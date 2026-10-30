package com.shivam.glasslauncher.overlay.island

import com.shivam.glasslauncher.overlay.notifications.NotifEntry

/**
 * Turns notifications into island activities (docs/CONSTRAINTS.md: island content only exists if
 * the app posts an ongoing or progress notification). Pure, so it is unit-tested.
 */
object IslandClassifier {

    val CLOCK_PACKAGES = setOf(
        "com.android.deskclock", // AOSP and HyperOS / MIUI Clock
        "com.google.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.oneplus.deskclock",
        "com.coloros.alarmclock",
    )
    val MAPS_PACKAGES = setOf("com.google.android.apps.maps")

    private const val CATEGORY_CALL = "call"
    private const val CATEGORY_NAVIGATION = "navigation"
    private const val CATEGORY_STOPWATCH = "stopwatch"
    private const val CATEGORY_PROGRESS = "progress"
    private const val CATEGORY_TRANSPORT = "transport"
    private const val CATEGORY_SERVICE = "service"
    private const val CATEGORY_SYSTEM = "sys"
    private val UPLOAD_WORDS = listOf("upload", "sending", "backing up", "backup", "syncing")

    /** The ongoing activity a notification represents, or null if it is just a message. */
    fun classify(e: NotifEntry): IslandActivity? {
        if (e.isGroupSummary || e.isMedia) return null
        val isCall = e.category == CATEGORY_CALL || e.template == "CallStyle"
        if (isCall) {
            val incoming = e.callType == 1 || e.callType == 3 ||
                (e.callType == 0 && e.hasFullScreenIntent && !e.usesChronometer)
            val ongoing = !incoming && (e.callType == 2 || e.usesChronometer || e.isOngoing)
            if (!incoming && !ongoing) return null
            val name = e.title.ifBlank { e.text }.ifBlank { "Call" }
            return IslandActivity(
                id = "call:${e.key}",
                kind = if (incoming) IslandKind.IncomingCall else IslandKind.OngoingCall,
                since = e.postTime,
                // Dialers repost with a fresh `when` once answered and only then turn on the chronometer; 0 = still ringing.
                content = IslandContent.Call(name, incoming, if (e.usesChronometer && e.whenTime > 0) e.whenTime else 0L, e.key),
                sourcePackage = e.packageName,
                notificationKey = e.key,
            )
        }
        if ((e.packageName in MAPS_PACKAGES && e.isOngoing) || e.category == CATEGORY_NAVIGATION) {
            return IslandActivity(
                id = "nav:${e.key}",
                kind = IslandKind.Navigation,
                since = e.postTime,
                content = IslandContent.Navigation(e.title.ifBlank { e.text }, if (e.title.isBlank()) "" else e.text),
                sourcePackage = e.packageName,
                notificationKey = e.key,
            )
        }
        if ((e.packageName in CLOCK_PACKAGES || e.category == CATEGORY_STOPWATCH) && e.isOngoing) {
            val kind = clockKind(e)
            if (kind != null) {
                return IslandActivity(
                    id = "${kind.name.lowercase()}:${e.key}",
                    kind = kind,
                    since = e.postTime,
                    content = IslandContent.Clock(
                        label = e.title.ifBlank { if (kind == IslandKind.Timer) "Timer" else "Stopwatch" },
                        base = e.whenTime,
                        countDown = kind == IslandKind.Timer,
                        hasChronometer = e.usesChronometer && e.whenTime > 0,
                        text = e.text,
                    ),
                    sourcePackage = e.packageName,
                    notificationKey = e.key,
                )
            }
        }
        if (e.progressMax > 0 || e.progressIndeterminate) {
            val haystack = (e.title + " " + e.text + " " + (e.channelId ?: "")).lowercase()
            return IslandActivity(
                id = "progress:${e.key}",
                kind = IslandKind.Progress,
                since = e.postTime,
                content = IslandContent.Progress(
                    title = e.title.ifBlank { e.appLabel },
                    progress = e.progress,
                    max = e.progressMax,
                    indeterminate = e.progressIndeterminate || e.progressMax <= 0,
                    upload = UPLOAD_WORDS.any { it in haystack },
                ),
                sourcePackage = e.packageName,
                notificationKey = e.key,
            )
        }
        return null
    }

    private fun clockKind(e: NotifEntry): IslandKind? {
        if (e.category == CATEGORY_STOPWATCH) return IslandKind.Stopwatch
        val words = (e.title + " " + e.text + " " + (e.channelId ?: "")).lowercase()
        return when {
            e.usesChronometer && e.chronometerCountDown -> IslandKind.Timer
            "stopwatch" in words -> IslandKind.Stopwatch
            "timer" in words -> IslandKind.Timer
            e.usesChronometer -> IslandKind.Stopwatch
            else -> null // alarms, bedtime and other clock notifications are not live activities
        }
    }

    /** Whether a newly posted notification gets a short island peek. */
    fun isPeekCandidate(e: NotifEntry, ownPackage: String): Boolean =
        !e.isOngoing && e.isClearable && !e.isGroupSummary && !e.isMedia &&
            e.packageName != ownPackage &&
            e.importance >= 3 &&
            e.category !in setOf(CATEGORY_PROGRESS, CATEGORY_TRANSPORT, CATEGORY_CALL, CATEGORY_SERVICE, CATEGORY_SYSTEM) &&
            (e.title.isNotBlank() || e.text.isNotBlank()) &&
            classify(e) == null

    fun peek(e: NotifEntry, now: Long): IslandActivity = IslandActivity(
        id = "peek:${e.key}:${e.postTime}",
        kind = IslandKind.Peek,
        since = now,
        content = IslandContent.Peek(e.appLabel, e.title.ifBlank { e.appLabel }, e.text),
        sourcePackage = e.packageName,
        notificationKey = e.key,
    )
}
