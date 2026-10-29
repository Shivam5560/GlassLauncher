package com.shivam.glasslauncher.overlay.island

import android.content.Context
import android.os.SystemClock
import com.shivam.glasslauncher.overlay.OverlayPrefs
import com.shivam.glasslauncher.overlay.OverlaySettings
import com.shivam.glasslauncher.overlay.notifications.MediaHub
import com.shivam.glasslauncher.overlay.notifications.MediaInfo
import com.shivam.glasslauncher.overlay.notifications.NotifEntry
import com.shivam.glasslauncher.overlay.notifications.NotificationHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Everything the island window renders. */
data class IslandUi(
    val slots: IslandSlots = IslandSlots.EMPTY,
    val cameraInUse: Boolean = false,
    val micInUse: Boolean = false,
    val battery: BatteryInfo = BatteryInfo(0, false, null, false),
    val media: MediaInfo? = null,
)

/**
 * Builds the island's activity list from notifications ([IslandClassifier]), the media session,
 * charging and ringer changes, then lets [IslandArbiter] pick the main and split slots. One
 * process-wide instance, shared by whichever window host (accessibility or display overlay) is up.
 */
class IslandEngine private constructor(context: Context) {

    private val ctx = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val signals = IslandSignals(ctx)

    private val _ui = MutableStateFlow(IslandUi())
    val ui: StateFlow<IslandUi> = _ui.asStateFlow()

    /** Ids swiped away this session; kept only while their activity exists. */
    private var dismissed: Set<String> = emptySet()
    private val peeks = ArrayList<Pair<IslandActivity, Long>>()
    /** A demo activity shown from settings so the user can see and calibrate the island. */
    private var demoUntil = 0L

    private var users = 0
    private var jobs: List<Job> = emptyList()

    fun acquire() {
        users++
        if (users > 1) return
        signals.start()
        signals.refreshBattery()
        val settings = OverlayPrefs.state(ctx)
        jobs = listOf(
            scope.launch { NotificationHub.entries.collect { recompute() } },
            scope.launch { MediaHub.state.collect { recompute() } },
            scope.launch { settings.collect { recompute() } },
            scope.launch { signals.batteryInfo.collect { recompute() } },
            scope.launch { signals.pluggedAt.collect { recompute() } },
            scope.launch { signals.ringer.collect { recompute() } },
            scope.launch { signals.cameraInUse.collect { recompute() } },
            scope.launch { signals.micInUse.collect { recompute() } },
            scope.launch {
                NotificationHub.posted.collect { e ->
                    val s = settings.value.island
                    if (s.showsGlass && signals.interactive.value && !signals.locked.value && s.isOn(IslandKind.Peek) && IslandClassifier.isPeekCandidate(e, ctx.packageName)) {
                        val now = SystemClock.elapsedRealtime()
                        peeks.removeAll { it.second < now }
                        peeks.removeAll { it.first.notificationKey == e.key }
                        peeks.add(IslandClassifier.peek(e, System.currentTimeMillis()) to now + s.peekSeconds.coerceIn(4, 15) * 1000L)
                        while (peeks.size > 4) peeks.removeAt(0)
                        recompute()
                    }
                }
            },
            // Ticker: expires transient activities, paused music and refreshes the charging watts.
            scope.launch {
                combine(settings, signals.interactive, signals.locked) { prefs, interactive, locked ->
                    prefs.island.showsGlass && interactive && !locked
                }.distinctUntilChanged().collectLatest { enabled ->
                    recompute()
                    if (enabled) while (isActive) {
                        delay(1000)
                        val now = SystemClock.elapsedRealtime()
                        if (chargingVisible(now)) signals.refreshBattery()
                        recompute()
                    }
                }
            },
        )
    }

    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users > 0) return
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        signals.stop()
    }

    fun dismiss(id: String) {
        dismissed = IslandReducer.reduce(IslandState(dismissed = dismissed), IslandEvent.Dismiss(id)).dismissed
        peeks.removeAll { it.first.id == id }
        recompute()
    }

    fun showDemo() {
        demoUntil = SystemClock.elapsedRealtime() + 5000
        recompute()
    }

    fun showNotificationDemo() {
        val now = SystemClock.elapsedRealtime()
        peeks.removeAll { it.first.id == "demo-notification" }
        peeks.add(IslandActivity("demo-notification", IslandKind.Peek, System.currentTimeMillis(),
            IslandContent.Peek("Glass Launcher", "A little more space for what matters", "Your notification stays below the camera, with a clear title and room to read."),
            sourcePackage = ctx.packageName) to now + 10_000L)
        recompute()
    }

    private fun chargingVisible(now: Long): Boolean {
        val plugged = signals.pluggedAt.value
        return plugged > 0 && now - plugged < CHARGING_MS && signals.batteryInfo.value.charging
    }

    private fun recompute() {
        val now = SystemClock.elapsedRealtime()
        val settings: OverlaySettings = OverlayPrefs.state(ctx).value
        val island = settings.island
        if (!island.showsGlass || !signals.interactive.value || signals.locked.value) {
            peeks.clear()
            _ui.value = IslandUi(battery = signals.batteryInfo.value)
            return
        }
        val list = ArrayList<IslandActivity>()

        NotificationHub.entries.value.forEach { e: NotifEntry -> IslandClassifier.classify(e)?.let { list.add(it) } }

        val media = MediaHub.state.value
        if (media != null && (media.playing || (media.lastPlayingAt > 0 && now - media.lastPlayingAt < PAUSED_MUSIC_MS))) {
            list.add(
                IslandActivity(
                    id = "music:${media.packageName}",
                    kind = IslandKind.Music,
                    since = media.lastPlayingAt,
                    content = IslandContent.Music(media.title, media.artist, media.playing),
                    sourcePackage = media.packageName,
                ),
            )
        }

        val battery = signals.batteryInfo.value
        if (chargingVisible(now)) {
            list.add(IslandActivity("charging:${signals.pluggedAt.value}", IslandKind.Charging, signals.pluggedAt.value, IslandContent.Charging(battery.level, battery.watts)))
        }
        signals.ringer.value?.let { (mode, at) ->
            if (now - at < RINGER_MS) list.add(IslandActivity("ringer:$at", IslandKind.Ringer, at, IslandContent.Ringer(mode)))
        }
        peeks.removeAll { it.second < now }
        peeks.forEach { list.add(it.first) }
        if (demoUntil > now) {
            list.add(IslandActivity("demo", IslandKind.Charging, now, IslandContent.Charging(battery.level, battery.watts)))
        }

        val state = IslandReducer.reduce(IslandState(dismissed = dismissed), IslandEvent.Sync(list))
        dismissed = state.dismissed
        val slots = if (island.showsGlass) {
            IslandArbiter.resolve(state.activities, enabled = { island.isOn(it) || it == IslandKind.Charging && demoUntil > now }, dismissed = dismissed)
        } else IslandSlots.EMPTY
        _ui.value = IslandUi(
            slots = slots,
            cameraInUse = island.privacyDots && signals.cameraInUse.value,
            micInUse = island.privacyDots && signals.micInUse.value,
            battery = battery,
            media = media,
        )
    }

    companion object {
        private const val RINGER_MS = 2200L
        private const val CHARGING_MS = 6000L
        private const val PAUSED_MUSIC_MS = 60_000L

        @Volatile private var instance: IslandEngine? = null

        fun get(context: Context): IslandEngine = instance ?: synchronized(this) {
            instance ?: IslandEngine(context).also { instance = it }
        }
    }
}
