package com.shivam.glasslauncher.widgets

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    /** ARGB colour of the event's calendar. */
    val color: Int,
    val location: String?,
)

/** "Up next" events from the system calendar provider (READ_CALENDAR, asked lazily by the widget). */
object CalendarRepository {

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Events that have not ended yet, within the next [days] days, soonest first. */
    suspend fun upcoming(context: Context, days: Int = 7, limit: Int = 12): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().let {
            ContentUris.appendId(it, now - 24 * 3600_000L) // all-day events are stored in UTC
            ContentUris.appendId(it, now + days * 24 * 3600_000L)
            it.build()
        }
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val out = ArrayList<CalendarEvent>()
        try {
            context.contentResolver.query(
                uri, projection,
                "${CalendarContract.Instances.VISIBLE} = 1",
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val allDay = c.getInt(4) == 1
                    var begin = c.getLong(2)
                    var end = c.getLong(3)
                    if (allDay) {
                        // All-day instances are UTC midnights; shift them to local midnights.
                        val tz = java.util.TimeZone.getDefault()
                        begin -= tz.getOffset(begin)
                        end -= tz.getOffset(end)
                    }
                    if (end <= now) continue
                    out += CalendarEvent(
                        eventId = c.getLong(0),
                        title = c.getString(1)?.ifBlank { null } ?: "(No title)",
                        begin = begin,
                        end = end,
                        allDay = allDay,
                        color = c.getInt(5),
                        location = c.getString(6)?.ifBlank { null },
                    )
                }
            }
        } catch (e: Exception) {
            return@withContext emptyList()
        }
        // Ongoing (and today's all-day) events first, then by start time.
        out.sortedBy { it.begin.coerceAtLeast(now) }.take(limit)
    }

    /** Emits whenever the calendar provider changes. */
    fun changes(context: Context): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        try {
            context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        } catch (_: Exception) { }
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    fun openEvent(context: Context, e: CalendarEvent): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(intent); true } catch (_: Exception) { openCalendar(context) }
    }

    fun openCalendar(context: Context): Boolean {
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").let {
            ContentUris.appendId(it, System.currentTimeMillis())
            it.build()
        }
        // Some calendar apps (including OEM variants) do not handle the provider's time URI.
        // The standard app-category selector still opens their calendar launcher activity.
        val intents = listOf(
            Intent(Intent.ACTION_VIEW, uri),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
        )
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) { }
        }
        return false
    }
}
