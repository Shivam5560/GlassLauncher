package com.shivam.glasslauncher.overlay.notifications

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Live view of the active notifications for the Notification Center and the island, fed by the
 * launcher's NotificationListenerService (BadgeListenerService). Also performs the actions the
 * listener is allowed to: dismiss, open, run action buttons and inline replies via RemoteInput.
 */
object NotificationHub {

    private const val TAG = "GlassNotifications"

    private val _entries = MutableStateFlow<List<NotifEntry>>(emptyList())
    val entries: StateFlow<List<NotifEntry>> = _entries.asStateFlow()

    private val _connected = MutableStateFlow(false)
    /** True while notification access is granted and the listener is bound. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _posted = MutableSharedFlow<NotifEntry>(extraBufferCapacity = 16)
    /** Notifications that just arrived (not updates of an existing key), for the island peek. */
    val posted: SharedFlow<NotifEntry> = _posted.asSharedFlow()

    private val _interruptionFilter = MutableStateFlow(NotificationListenerService.INTERRUPTION_FILTER_UNKNOWN)
    val interruptionFilter: StateFlow<Int> = _interruptionFilter.asStateFlow()

    @Volatile private var listener: NotificationListenerService? = null
    private val raw = ConcurrentHashMap<String, StatusBarNotification>()
    private val labels = ConcurrentHashMap<String, String>()

    // --- fed by BadgeListenerService ---

    fun onConnected(service: NotificationListenerService) {
        listener = service
        _connected.value = true
        _interruptionFilter.value = try { service.currentInterruptionFilter } catch (e: Exception) { NotificationListenerService.INTERRUPTION_FILTER_UNKNOWN }
        refreshAll(service)
    }

    fun onDisconnected(service: NotificationListenerService) {
        if (listener !== service) return
        listener = null
        _connected.value = false
        raw.clear()
        _entries.value = emptyList()
    }

    fun onPosted(service: NotificationListenerService, sbn: StatusBarNotification, ranking: NotificationListenerService.RankingMap?) {
        val isNew = !raw.containsKey(sbn.key)
        raw[sbn.key] = sbn
        val entry = parse(service, sbn, ranking)
        _entries.value = _entries.value.filterNot { it.key == sbn.key } + listOfNotNull(entry.takeUnless { it.isSystemStatus })
        if (isNew && !entry.isSystemStatus) _posted.tryEmit(entry)
    }

    fun onRemoved(sbn: StatusBarNotification) {
        raw.remove(sbn.key)
        _entries.value = _entries.value.filterNot { it.key == sbn.key }
    }

    /** Channel/privacy changes can arrive without a new notification being posted. */
    fun onRankingUpdated(service: NotificationListenerService, ranking: NotificationListenerService.RankingMap) {
        if (listener !== service) return
        _entries.value = raw.values.toList().map { parse(service, it, ranking) }.filterNot { it.isSystemStatus }
    }

    fun onInterruptionFilterChanged(filter: Int) { _interruptionFilter.value = filter }

    private fun refreshAll(service: NotificationListenerService) {
        val active = try { service.activeNotifications } catch (e: Exception) { null } ?: return
        val ranking = try { service.currentRanking } catch (e: Exception) { null }
        raw.clear()
        active.forEach { raw[it.key] = it }
        _entries.value = active.map { parse(service, it, ranking) }.filterNot { it.isSystemStatus }
    }

    private val systemApps = ConcurrentHashMap<String, Boolean>()

    private fun isSystemApp(context: Context, pkg: String): Boolean = systemApps.getOrPut(pkg) {
        try {
            context.packageManager.getApplicationInfo(pkg, 0).flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
        } catch (e: Exception) { pkg == "android" }
    }

    fun label(context: Context, pkg: String): String = labels.getOrPut(pkg) {
        try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { pkg }
    }

    private fun parse(context: Context, sbn: StatusBarNotification, rankingMap: NotificationListenerService.RankingMap?): NotifEntry {
        val n = sbn.notification
        val x = n.extras ?: Bundle.EMPTY
        val template = x.getString(Notification.EXTRA_TEMPLATE)?.substringAfterLast('$')?.substringAfterLast('.')
        val text = x.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }
            ?: x.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: lastMessage(x)
            ?: ""
        val ranking = rankingMap?.let { map ->
            val value = NotificationListenerService.Ranking()
            try { value.takeIf { map.getRanking(sbn.key, it) } } catch (_: RuntimeException) { null }
        }
        val importance = ranking?.importance ?: 3
        val visibilityOverride = try { ranking?.lockscreenVisibilityOverride } catch (_: RuntimeException) { null }
        val channelVisibility = try { ranking?.channel?.lockscreenVisibility } catch (_: RuntimeException) { null }
        return NotifEntry(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = label(context, sbn.packageName),
            title = (x.getCharSequence(Notification.EXTRA_TITLE_BIG) ?: x.getCharSequence(Notification.EXTRA_TITLE))?.toString().orEmpty(),
            text = text,
            postTime = sbn.postTime,
            subText = x.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty(),
            groupKey = sbn.groupKey,
            isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isOngoing = sbn.isOngoing,
            isClearable = sbn.isClearable,
            category = n.category,
            progress = x.getInt(Notification.EXTRA_PROGRESS, 0),
            progressMax = x.getInt(Notification.EXTRA_PROGRESS_MAX, 0),
            progressIndeterminate = x.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false),
            usesChronometer = x.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false),
            chronometerCountDown = x.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false),
            whenTime = n.`when`,
            template = template,
            callType = x.getInt(Notification.EXTRA_CALL_TYPE, 0),
            hasFullScreenIntent = n.fullScreenIntent != null,
            isMedia = x.containsKey(Notification.EXTRA_MEDIA_SESSION) || template == "MediaStyle" || template == "DecoratedMediaCustomViewStyle",
            importance = importance,
            channelId = n.channelId,
            visibility = effectiveLockScreenVisibility(n.visibility, visibilityOverride, channelVisibility),
            isSystem = isSystemApp(context, sbn.packageName),
            publicTitle = n.publicVersion?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            publicText = n.publicVersion?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            actions = n.actions.orEmpty().mapIndexed { i, a ->
                NotifAction(i, a.title?.toString().orEmpty(), a.remoteInputs?.any { it.allowFreeFormInput } == true)
            }.filter { it.title.isNotBlank() },
        )
    }

    @Suppress("DEPRECATION")
    private fun lastMessage(x: Bundle): String? {
        val msgs = x.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        val last = msgs.lastOrNull() as? Bundle ?: return null
        return last.getCharSequence("text")?.toString()
    }

    // --- actions ---

    fun notification(key: String): StatusBarNotification? = raw[key]

    fun dismiss(key: String) = dismissAll(listOf(key))

    /** Drops cancelled cards at once: waiting for onRemoved left an empty gap in the list for ~0.5 s. */
    private fun dropNow(keys: Collection<String>) {
        keys.forEach { raw.remove(it) }
        _entries.value = _entries.value.filterNot { it.key in keys }
    }

    /**
     * Ongoing notifications the user cleared (key -> postTime then). Android won't cancel them, so
     * Glass hides them until the app posts them again.
     */
    private val _hidden = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Long>>(emptyMap())
    val hidden: kotlinx.coroutines.flow.StateFlow<Map<String, Long>> = _hidden

    fun dismissAll(keys: List<String>) {
        if (keys.isEmpty()) return
        val (clearable, sticky) = keys.partition { raw[it]?.isClearable != false }
        if (clearable.isNotEmpty()) {
            try {
                val l = listener
                if (l != null) {
                    l.cancelNotifications(clearable.toTypedArray())
                    dropNow(clearable.toHashSet())
                }
            } catch (e: Exception) { Log.w(TAG, "clear failed", e) }
        }
        if (sticky.isNotEmpty()) _hidden.value = _hidden.value + sticky.associateWith { raw[it]?.postTime ?: Long.MAX_VALUE }
    }

    /** Asks the system for a Do Not Disturb filter through the listener (fallback without DND access). */
    fun requestInterruptionFilter(filter: Int): Boolean {
        val l = listener ?: return false
        return try { l.requestInterruptionFilter(filter); true } catch (e: Exception) { false }
    }

    /**
     * Options that let a PendingIntent we send start its activity: on Android 14+ the sender must
     * opt in, and our overlay is what the user is interacting with.
     */
    private fun sendOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            .toBundle()
    }

    /** Opens a notification's full-screen intent (the dialer's incoming-call screen). */
    fun openFullScreen(context: Context, key: String): Boolean =
        raw[key]?.notification?.fullScreenIntent?.let { send(context, it) } ?: false

    fun send(context: Context, pi: PendingIntent, fill: Intent? = null): Boolean = try {
        pi.send(context, 0, fill, null, null, null, sendOptions())
        true
    } catch (e: Exception) {
        Log.w(TAG, "PendingIntent failed", e)
        false
    }

    /** Opens the notification like a tap in the shade (falls back to launching the app). */
    fun open(context: Context, key: String): Boolean {
        val sbn = raw[key]
        val pi = sbn?.notification?.contentIntent
        val ok = if (pi != null) send(context, pi) else sbn?.let { launchApp(context, it.packageName) } ?: false
        if (ok && sbn != null && sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0) dismiss(key)
        return ok
    }

    fun launchApp(context: Context, pkg: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) { false }
    }

    fun runAction(context: Context, key: String, index: Int): Boolean {
        val action = raw[key]?.notification?.actions?.getOrNull(index) ?: return false
        val pi = action.actionIntent ?: return false
        return send(context, pi)
    }

    /** Inline reply: fills every free-form RemoteInput of the action with [text] and fires it. */
    fun reply(context: Context, key: String, index: Int, text: String): Boolean {
        val action = raw[key]?.notification?.actions?.getOrNull(index) ?: return false
        val inputs = action.remoteInputs?.takeIf { it.isNotEmpty() } ?: return false
        val pi = action.actionIntent ?: return false
        val results = Bundle()
        inputs.forEach { results.putCharSequence(it.resultKey, text) }
        val fill = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        RemoteInput.addResultsToIntent(inputs, fill, results)
        RemoteInput.setResultsSource(fill, RemoteInput.SOURCE_FREE_FORM_INPUT)
        return send(context, pi, fill)
    }
}
