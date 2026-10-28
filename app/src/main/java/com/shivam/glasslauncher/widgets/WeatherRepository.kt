package com.shivam.glasslauncher.widgets

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.util.AtomicFile
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shivam.glasslauncher.GlassApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class WeatherState(
    val data: WeatherData? = null,
    val place: String? = null,
    val fetchedAt: Long = 0L,
    val loading: Boolean = false,
    val error: String? = null,
    /** No coordinates yet: the widget asks for coarse location or a city (docs/CONSTRAINTS.md fallback). */
    val needsLocation: Boolean = false,
)

/**
 * Weather from Open-Meteo: coarse location (or a manually picked city as the fallback), cached in
 * `files/weather.json`, refreshed on demand when stale (30 min) and every 45 min by WorkManager.
 * The only network access in Glass besides Google search intents.
 */
class WeatherRepository private constructor(private val context: Context, private val scope: CoroutineScope) {

    val prefs = WidgetPrefs(context, scope)
    private val cacheFile = AtomicFile(File(context.filesDir, "weather.json"))
    private val lock = Mutex()
    private val _state = MutableStateFlow(WeatherState())
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) { loadCache() }
        scope.launch {
            val p = prefs.weather.filterNotNull().first()
            if (p.latitude != null) schedule()
        }
    }

    private fun loadCache() {
        try {
            if (!cacheFile.baseFile.exists()) return
            val o = JSONObject(String(cacheFile.readFully()))
            val data = OpenMeteo.parseForecast(o.getString("raw"))
            _state.update { it.copy(data = data, place = o.optString("place").ifBlank { null }, fetchedAt = o.optLong("fetchedAt")) }
        } catch (e: Exception) {
            Log.w(TAG, "Weather cache unreadable", e)
        }
    }

    private fun saveCache(raw: String, place: String?, fetchedAt: Long) {
        val out = cacheFile.startWrite()
        try {
            out.write(JSONObject().put("raw", raw).put("place", place ?: "").put("fetchedAt", fetchedAt).toString().toByteArray())
            cacheFile.finishWrite(out)
        } catch (e: Exception) {
            cacheFile.failWrite(out)
        }
    }

    fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Refreshes in the background if the data is older than [maxAgeMs]. Safe to call often. */
    fun refreshIfStale(maxAgeMs: Long = STALE_MS) {
        val s = _state.value
        if (s.loading) return
        if (s.data != null && System.currentTimeMillis() - s.fetchedAt < maxAgeMs) return
        scope.launch { refresh(allowLocation = true) }
    }

    /**
     * Fetches the forecast. With [allowLocation] (foreground only) DEVICE mode asks for a fresh
     * coarse fix; the background worker uses the last saved coordinates.
     */
    suspend fun refresh(allowLocation: Boolean): Boolean = lock.withLock {
        _state.update { it.copy(loading = true, error = null) }
        try {
            val p = prefs.weather.filterNotNull().first()
            var lat = p.latitude
            var lon = p.longitude
            var place = p.placeName
            if (p.mode == WeatherLocationMode.DEVICE && allowLocation && hasLocationPermission()) {
                val loc = currentLocation()
                if (loc != null) {
                    lat = loc.latitude
                    lon = loc.longitude
                    place = placeName(loc) ?: place ?: "Current Location"
                    prefs.setLocation(WeatherLocationMode.DEVICE, lat, lon, place)
                    schedule()
                }
            }
            if (lat == null || lon == null) {
                _state.update { it.copy(loading = false, needsLocation = true) }
                return@withLock false
            }
            val raw = withContext(Dispatchers.IO) { httpGet(OpenMeteo.forecastUrl(lat, lon)) }
            val data = OpenMeteo.parseForecast(raw)
            val now = System.currentTimeMillis()
            withContext(Dispatchers.IO) { saveCache(raw, place, now) }
            _state.update { WeatherState(data = data, place = place, fetchedAt = now) }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Weather refresh failed", e)
            _state.update { it.copy(loading = false, error = if (e is IOException) "Offline — showing saved forecast" else "Weather unavailable") }
            false
        }
    }

    /** Switches to the phone's coarse location (permission must already be granted to get a fix). */
    fun useDeviceLocation() = scope.launch {
        val p = prefs.weather.filterNotNull().first()
        prefs.setLocation(WeatherLocationMode.DEVICE, p.latitude, p.longitude, p.placeName)
        refresh(allowLocation = true)
    }

    fun useCity(place: GeoPlace) = scope.launch {
        prefs.setLocation(WeatherLocationMode.CITY, place.latitude, place.longitude, place.name)
        _state.update { it.copy(needsLocation = false) }
        schedule()
        refresh(allowLocation = false)
    }

    suspend fun searchCities(query: String): Result<List<GeoPlace>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.success(emptyList())
        runCatching { OpenMeteo.parseGeocoding(httpGet(OpenMeteo.geocodingUrl(query))) }
    }

    fun schedule() {
        try {
            val request = PeriodicWorkRequestBuilder<WeatherWorker>(45, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't schedule weather refresh", e)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Location? {
        if (!hasLocationPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { p -> try { lm.isProviderEnabled(p) } catch (e: Exception) { false } }
        for (p in providers) {
            val loc = withTimeoutOrNull(8_000L) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    try {
                        lm.getCurrentLocation(p, signal, context.mainExecutor) { l -> if (cont.isActive) cont.resume(l) }
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
            if (loc != null) return loc
        }
        return listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .firstNotNullOfOrNull { p -> try { lm.getLastKnownLocation(p) } catch (e: Exception) { null } }
    }

    private suspend fun placeName(loc: Location): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        try {
            @Suppress("DEPRECATION")
            val a = Geocoder(context).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
            a?.locality ?: a?.subAdminArea ?: a?.adminArea
        } catch (e: Exception) {
            null
        }
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "GlassLauncher/0.2 (Android)")
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                // Open-Meteo explains bad requests in JSON; surface it as a parse error, not "offline".
                if (body.startsWith("{")) return body
                throw IOException("HTTP $code")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "GlassWeather"
        private const val WORK_NAME = "glass-weather-refresh"
        const val STALE_MS = 30 * 60 * 1000L

        @Volatile private var instance: WeatherRepository? = null

        fun get(context: Context): WeatherRepository = instance ?: synchronized(this) {
            instance ?: WeatherRepository(context.applicationContext, GlassApp.from(context).scope).also { instance = it }
        }
    }
}

/** Periodic background refresh (45 min, network required) using the last saved coordinates. */
class WeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (WeatherRepository.get(applicationContext).refresh(allowLocation = false)) Result.success() else Result.retry()
}
