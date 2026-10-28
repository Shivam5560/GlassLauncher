package com.shivam.glasslauncher.overlay.notifications

/**
 * Plain snapshot of one active notification (no Android types, so grouping, stacking and the
 * island classifier are unit-testable). Built by [NotificationHub] from a StatusBarNotification.
 */
data class NotifEntry(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val subText: String = "",
    /** StatusBarNotification.groupKey; entries sharing it belong to one app-defined group. */
    val groupKey: String? = null,
    val isGroupSummary: Boolean = false,
    val isOngoing: Boolean = false,
    val isClearable: Boolean = true,
    val category: String? = null,
    val progress: Int = 0,
    val progressMax: Int = 0,
    val progressIndeterminate: Boolean = false,
    val usesChronometer: Boolean = false,
    val chronometerCountDown: Boolean = false,
    /** Notification.when: the chronometer base for timers, stopwatches and calls. */
    val whenTime: Long = 0L,
    /** Simple name of the template style, e.g. "CallStyle", "MediaStyle", "MessagingStyle". */
    val template: String? = null,
    /** Notification.EXTRA_CALL_TYPE: 1 incoming, 2 ongoing, 3 screening, 0 unknown. */
    val callType: Int = 0,
    val hasFullScreenIntent: Boolean = false,
    /** Carries a media session (music players); shown as the pinned live card instead. */
    val isMedia: Boolean = false,
    /** NotificationManager importance from the ranking (3 = default). */
    val importance: Int = 3,
    val channelId: String? = null,
    val actions: List<NotifAction> = emptyList(),
)

data class NotifAction(val index: Int, val title: String, val hasRemoteInput: Boolean)

/** One app's notifications, shown as an iOS-style pile (collapsed) or a list (expanded). */
data class NotifStack(
    val packageName: String,
    val appLabel: String,
    /** Newest first. */
    val items: List<NotifEntry>,
    val expanded: Boolean,
) {
    val latest: NotifEntry get() = items.first()
    val count: Int get() = items.size
    /** Cards drawn: every card when expanded, otherwise the front card plus up to two slivers. */
    val visibleLayers: Int get() = if (expanded) items.size else minOf(items.size, NotificationGrouping.MAX_PILE_LAYERS)
    /** Notifications hidden behind the front card while collapsed. */
    val hiddenCount: Int get() = if (expanded) 0 else items.size - 1
    val clearableKeys: List<String> get() = items.filter { it.isClearable }.map { it.key }
}

object NotificationGrouping {

    const val MAX_PILE_LAYERS = 3

    /**
     * What the Notification Center lists: media notifications are dropped (the session is pinned
     * as a live card), and a group summary is dropped whenever any child of its group is present
     * (a lone summary is kept, since some apps post only the summary).
     */
    fun visible(entries: List<NotifEntry>): List<NotifEntry> {
        val groupsWithChildren = entries.filter { !it.isGroupSummary && it.groupKey != null }.mapTo(HashSet()) { it.groupKey }
        return entries.filter { e ->
            !e.isMedia && !(e.isGroupSummary && e.groupKey in groupsWithChildren)
        }
    }

    /**
     * Groups [entries] into one stack per app. Items inside a stack are newest first, and stacks are
     * ordered by their newest notification. Stacks whose package is in [expanded] are expanded.
     */
    fun stacks(entries: List<NotifEntry>, expanded: Set<String> = emptySet()): List<NotifStack> =
        visible(entries)
            .groupBy { it.packageName }
            .map { (pkg, items) ->
                val sorted = items.sortedWith(compareByDescending<NotifEntry> { it.postTime }.thenBy { it.key })
                NotifStack(pkg, sorted.first().appLabel, sorted, expanded = pkg in expanded && sorted.size > 1)
            }
            .sortedWith(compareByDescending<NotifStack> { it.latest.postTime }.thenBy { it.packageName })

    /** Keys removed by "Clear All": every clearable notification that is listed. */
    fun clearAllKeys(entries: List<NotifEntry>): List<String> = visible(entries).filter { it.isClearable }.map { it.key }
}
