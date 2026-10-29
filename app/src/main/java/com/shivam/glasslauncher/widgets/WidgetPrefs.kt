package com.shivam.glasslauncher.widgets

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.widgetStore: DataStore<Preferences> by preferencesDataStore(name = "widget_prefs")

/** Where the weather widget gets its location: the phone's coarse location or a picked city. */
enum class WeatherLocationMode { DEVICE, CITY }

data class WeatherPrefs(
    val fahrenheit: Boolean = false,
    val mode: WeatherLocationMode = WeatherLocationMode.DEVICE,
    /** Last known coordinates: from coarse location (DEVICE) or the picked city (CITY). */
    val latitude: Double? = null,
    val longitude: Double? = null,
    val placeName: String? = null,
)

/** Phase 2 preferences, in their own DataStore so the phase 1 settings file is untouched. */
class WidgetPrefs(private val context: Context, private val scope: CoroutineScope) {

    private object K {
        val fahrenheit = booleanPreferencesKey("weather_fahrenheit")
        val mode = stringPreferencesKey("weather_mode")
        val lat = doublePreferencesKey("weather_lat")
        val lon = doublePreferencesKey("weather_lon")
        val place = stringPreferencesKey("weather_place")
    }

    val weather: StateFlow<WeatherPrefs?> = context.widgetStore.data.map { p ->
        WeatherPrefs(
            fahrenheit = p[K.fahrenheit] ?: false,
            mode = p[K.mode]?.let { m -> WeatherLocationMode.entries.firstOrNull { it.name == m } } ?: WeatherLocationMode.DEVICE,
            latitude = p[K.lat],
            longitude = p[K.lon],
            placeName = p[K.place],
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    fun setFahrenheit(v: Boolean) = scope.launch { context.widgetStore.edit { it[K.fahrenheit] = v } }

    suspend fun setLocation(mode: WeatherLocationMode, lat: Double?, lon: Double?, place: String?) {
        context.widgetStore.edit {
            it[K.mode] = mode.name
            if (lat != null && lon != null) {
                it[K.lat] = lat
                it[K.lon] = lon
            } else {
                it.remove(K.lat)
                it.remove(K.lon)
            }
            if (place != null) it[K.place] = place else it.remove(K.place)
        }
    }
}
