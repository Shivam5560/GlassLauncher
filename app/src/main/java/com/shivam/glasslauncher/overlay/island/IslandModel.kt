package com.shivam.glasslauncher.overlay.island

/**
 * Everything the Dynamic Island can show. Higher [priority] wins the main slot. [transient]
 * activities (short feedback) take over the main slot for a moment but never sit in the split slot.
 */
enum class IslandKind(val priority: Int, val transient: Boolean, val title: String, val autoExpand: Boolean = false) {
    IncomingCall(100, false, "Incoming calls", autoExpand = true),
    Ringer(95, true, "Ringer & silent feedback"),
    Peek(85, true, "Notification peek", autoExpand = true),
    OngoingCall(80, false, "Ongoing calls"),
    Charging(75, true, "Charging"),
    Navigation(70, false, "Navigation (Google Maps)"),
    Timer(60, false, "Timer"),
    Stopwatch(55, false, "Stopwatch"),
    Progress(50, false, "Downloads & uploads"),
    Music(40, false, "Music"),
}

enum class RingerMode { Normal, Vibrate, Silent }

sealed interface IslandContent {
    data class Music(val title: String, val artist: String, val playing: Boolean) : IslandContent
    data class Charging(val level: Int, val watts: Float?) : IslandContent
    data class Call(val name: String, val incoming: Boolean, val since: Long, val notificationKey: String) : IslandContent
    /** [base] is the chronometer base (Notification.when); [text] is the notification text as a fallback. */
    data class Clock(val label: String, val base: Long, val countDown: Boolean, val hasChronometer: Boolean, val text: String) : IslandContent {
        /** No running clock: a finished timer ("Time's up!" over the original "10 seconds") or a paused one. */
        val stoppedLabel: String get() = if (label.length <= 14) label else text
    }
    data class Navigation(val primary: String, val secondary: String) : IslandContent
    data class Progress(val title: String, val progress: Int, val max: Int, val indeterminate: Boolean, val upload: Boolean) : IslandContent {
        val fraction: Float get() = if (indeterminate || max <= 0) 0f else (progress.toFloat() / max).coerceIn(0f, 1f)
    }
    data class Peek(val appLabel: String, val title: String, val text: String) : IslandContent
    data class Ringer(val mode: RingerMode) : IslandContent
}

/**
 * One live activity. [id] is stable for the activity's life ("music:pkg", "progress:<key>") so a
 * swipe-dismissal sticks until the activity ends. [notificationKey] / [sourcePackage] are what a
 * long-press opens.
 */
data class IslandActivity(
    val id: String,
    val kind: IslandKind,
    val since: Long,
    val content: IslandContent,
    val sourcePackage: String? = null,
    val notificationKey: String? = null,
)

data class IslandSlots(val primary: IslandActivity?, val secondary: IslandActivity?) {
    val isSplit: Boolean get() = primary != null && secondary != null
    companion object { val EMPTY = IslandSlots(null, null) }
}

object IslandArbiter {

    private val order = compareByDescending<IslandActivity> { it.kind.priority }
        .thenByDescending { it.since }
        .thenBy { it.id }

    /**
     * Picks what the island shows:
     *  - the main slot is the highest-priority enabled, non-dismissed activity (newest first on ties);
     *  - an incoming call takes the whole island (no split);
     *  - the split slot is the best remaining non-transient activity of a different kind, so a
     *    short alert (ringer, peek, charging) pushes the running activity aside instead of hiding it.
     */
    fun resolve(
        activities: List<IslandActivity>,
        enabled: (IslandKind) -> Boolean = { true },
        dismissed: Set<String> = emptySet(),
    ): IslandSlots {
        val candidates = activities.filter { enabled(it.kind) && it.id !in dismissed }.sortedWith(order)
        val primary = candidates.firstOrNull() ?: return IslandSlots.EMPTY
        if (primary.kind == IslandKind.IncomingCall) return IslandSlots(primary, null)
        val secondary = candidates.drop(1).firstOrNull { !it.kind.transient && it.kind != primary.kind }
        return IslandSlots(primary, secondary)
    }

    /** Dismissals only last while their activity exists; drop ids that are gone. */
    fun pruneDismissed(dismissed: Set<String>, activities: List<IslandActivity>): Set<String> {
        if (dismissed.isEmpty()) return dismissed
        val live = activities.mapTo(HashSet()) { it.id }
        return dismissed.filterTo(HashSet()) { it in live }
    }
}

/** What the engine knows between recomputes: the live activities and the ids swiped away. */
data class IslandState(val activities: List<IslandActivity> = emptyList(), val dismissed: Set<String> = emptySet())

sealed interface IslandEvent {
    /** The sources (notifications, media, charging, ringer, peeks, demo) report the activities alive now. */
    data class Sync(val activities: List<IslandActivity>) : IslandEvent
    /** The user swiped an activity away. */
    data class Dismiss(val id: String) : IslandEvent
}

object IslandReducer {
    /** Pure state transition. A dismissal only lasts while its activity is still live (see pruneDismissed). */
    fun reduce(state: IslandState, event: IslandEvent): IslandState = when (event) {
        is IslandEvent.Sync -> IslandState(
            activities = event.activities,
            dismissed = IslandArbiter.pruneDismissed(state.dismissed, event.activities),
        )
        is IslandEvent.Dismiss -> state.copy(dismissed = state.dismissed + event.id)
    }
}
