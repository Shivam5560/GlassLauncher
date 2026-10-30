package com.shivam.glasslauncher.overlay.notifications

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.graphics.scale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/** The media session the island, Control Center and Notification Center show. */
data class MediaInfo(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    /** elapsedRealtime of [positionMs]. */
    val positionAt: Long,
    /** elapsedRealtime when this session was last seen playing. */
    val lastPlayingAt: Long,
    val canSkip: Boolean,
) {
    fun livePosition(now: Long = SystemClock.elapsedRealtime()): Long =
        if (playing) (positionMs + (now - positionAt)).coerceIn(0L, if (durationMs > 0) durationMs else Long.MAX_VALUE) else positionMs
}

/**
 * Media session controller shared by Phase 3 (docs/CONSTRAINTS.md: getActiveSessions needs
 * notification access; art and seek only if the player exposes them). Attached by the
 * notification listener so it uses that component's access.
 */
object MediaHub {

    private const val TAG = "GlassMedia"
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<MediaInfo?>(null)
    val state: StateFlow<MediaInfo?> = _state.asStateFlow()

    private var manager: MediaSessionManager? = null
    private var appContext: Context? = null
    private var controllers: List<MediaController> = emptyList()
    private var current: MediaController? = null
    private val lastPlaying = HashMap<String, Long>()
    private var artCache: Pair<String, Bitmap?>? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> setControllers(list.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = publish()
    }

    fun attach(service: NotificationListenerService) {
        val msm = service.getSystemService(MediaSessionManager::class.java) ?: return
        val component = ComponentName(service, service.javaClass)
        appContext = service.applicationContext
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, component, main)
            manager = msm
            setControllers(msm.getActiveSessions(component))
        } catch (e: SecurityException) {
            Log.w(TAG, "No notification access for media sessions", e)
        }
    }

    fun detach() {
        try { manager?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) { }
        manager = null
        setControllers(emptyList())
    }

    private fun setControllers(list: List<MediaController>) {
        controllers.forEach { try { it.unregisterCallback(callback) } catch (_: Exception) { } }
        controllers = list
        list.forEach { try { it.registerCallback(callback, main) } catch (_: Exception) { } }
        publish()
    }

    private fun isPlaying(c: MediaController) = c.playbackState?.state.let {
        it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING || it == PlaybackState.STATE_FAST_FORWARDING || it == PlaybackState.STATE_REWINDING
    }

    private fun publish() {
        val now = SystemClock.elapsedRealtime()
        controllers.filter { isPlaying(it) }.forEach { lastPlaying[it.packageName] = now }
        // A playing session wins; otherwise the one that played most recently (sessions are ordered by priority).
        val pick = controllers.firstOrNull { isPlaying(it) }
            ?: controllers.filter { it.metadata != null }.maxByOrNull { lastPlaying[it.packageName] ?: 0L }
        current = pick
        if (pick == null) {
            _state.value = null
            return
        }
        val md = pick.metadata
        val ps = pick.playbackState
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty()
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
        val artKey = pick.packageName + "|" + title + "|" + artist
        val art = artCache?.takeIf { it.first == artKey }?.second ?: run {
            val bmp = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            val small = bmp?.let { downscale(it) }
            artCache = artKey to small
            small
        }
        val actions = ps?.actions ?: 0L
        val ctx = appContext
        _state.value = MediaInfo(
            packageName = pick.packageName,
            appLabel = if (ctx != null) NotificationHub.label(ctx, pick.packageName) else pick.packageName,
            title = title,
            artist = artist,
            art = art,
            playing = isPlaying(pick),
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            positionMs = ps?.position ?: 0L,
            positionAt = ps?.lastPositionUpdateTime?.takeIf { it > 0 } ?: now,
            lastPlayingAt = lastPlaying[pick.packageName] ?: 0L,
            canSkip = actions and (PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L || actions == 0L,
        )
    }

    private fun downscale(b: Bitmap): Bitmap? = try {
        val side = max(b.width, b.height)
        if (side <= 256) b else b.scale((b.width * 256f / side).toInt().coerceAtLeast(1), (b.height * 256f / side).toInt().coerceAtLeast(1))
    } catch (e: Exception) { null }

    // --- transport ---

    fun toggle() {
        val c = current ?: return
        if (isPlaying(c)) c.transportControls.pause() else c.transportControls.play()
    }
    fun next() { current?.transportControls?.skipToNext() }
    fun previous() { current?.transportControls?.skipToPrevious() }
    /** Jumps to [ms] when the player supports seeking. */
    fun seekTo(ms: Long) {
        val c = current ?: return
        val actions = c.playbackState?.actions ?: 0L
        if (actions and android.media.session.PlaybackState.ACTION_SEEK_TO != 0L) c.transportControls.seekTo(ms)
    }

    /** Opens the player: its session activity if it has one, else its launcher activity. */
    fun openPlayer(context: Context): Boolean {
        val c = current ?: return false
        val pi = c.sessionActivity
        if (pi != null && NotificationHub.send(context, pi)) return true
        return NotificationHub.launchApp(context, c.packageName)
    }
}
