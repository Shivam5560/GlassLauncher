package com.shivam.glasslauncher.badges

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Notification counts per package, published by [BadgeListenerService]. */
object BadgeStore {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    internal fun publish(map: Map<String, Int>) { _counts.value = map }

    fun isAccessGranted(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(context, BadgeListenerService::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}

/**
 * Counts active, clearable, non-summary notifications per package for iOS-style red badges.
 * Uses the notification's `number` when an app sets it (e.g. "5 new messages").
 */
class BadgeListenerService : NotificationListenerService() {

    override fun onListenerConnected() = recount()
    override fun onListenerDisconnected() = BadgeStore.publish(emptyMap())
    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()

    private fun recount() {
        val active = try { activeNotifications } catch (e: Exception) { null } ?: return
        val counts = HashMap<String, Int>()
        for (sbn in active) {
            val n = sbn.notification
            if (!sbn.isClearable || sbn.isOngoing) continue
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            if (n.category == Notification.CATEGORY_TRANSPORT || n.category == Notification.CATEGORY_PROGRESS) continue
            val add = if (n.number > 0) n.number else 1
            counts[sbn.packageName] = (counts[sbn.packageName] ?: 0) + add
        }
        BadgeStore.publish(counts)
    }
}
