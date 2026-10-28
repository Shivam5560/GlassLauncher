package com.shivam.glasslauncher.widgets

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
import com.shivam.glasslauncher.badges.BadgeListenerService
import com.shivam.glasslauncher.badges.BadgeStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the music widget shows. */
data class NowPlaying(
    val packageName: String,
    val title: String,
    val artist: String?,
    val art: Bitmap?,
    val durationMs: Long,
    val playing: Boolean,
    /** Position at [positionUpdatedAt] (elapsedRealtime), advancing at [speed] while playing. */
    val positionMs: Long,
    val positionUpdatedAt: Long,
    val speed: Float,
    val canSeek: Boolean,
    val canSkipNext: Boolean,
    val canSkipPrevious: Boolean,
) {
    fun positionNow(nowElapsed: Long = SystemClock.elapsedRealtime()): Long {
        val p = if (playing) positionMs + ((nowElapsed - positionUpdatedAt) * speed).toLong() else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

sealed interface MusicState {
    /** docs/CONSTRAINTS.md: media sessions need notification access. */
    data object NoAccess : MusicState
    data object Idle : MusicState
    data class Playing(val now: NowPlaying) : MusicState
}

/**
 * Reads the active media sessions through `MediaSessionManager.getActiveSessions`, using the
 * existing notification listener's ComponentName as the access token (the listener itself is not
 * modified). Started while a music widget is on screen.
 */
class MediaRepository private constructor(private val context: Context) {

    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, BadgeListenerService::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow<MusicState>(MusicState.Idle)
    val state: StateFlow<MusicState> = _state.asStateFlow()

    private var users = 0
    private var registered = false
    private var controller: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = pickController(currentSessions())
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pickController(list ?: emptyList()) }

    fun start() {
        users++
        connect()
    }

    fun stop() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) disconnect()
    }

    /** Re-checks access, e.g. after returning from the notification access screen. */
    fun retry() {
        if (users > 0) {
            disconnect()
            connect()
        }
    }

    private fun connect() {
        if (registered) return
        if (!BadgeStore.isAccessGranted(context)) {
            _state.value = MusicState.NoAccess
            return
        }
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listener, main)
            registered = true
            pickController(currentSessions())
        } catch (e: SecurityException) {
            _state.value = MusicState.NoAccess
        }
    }

    private fun disconnect() {
        if (registered) {
            try { manager.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) { }
            registered = false
        }
        controller?.unregisterCallback(controllerCallback)
        controller = null
    }

    private fun currentSessions(): List<MediaController> = try {
        manager.getActiveSessions(listener)
    } catch (e: SecurityException) {
        _state.value = MusicState.NoAccess
        emptyList()
    }

    /** Prefers a session that is playing, else the most recent one (the list is ordered by priority). */
    private fun pickController(list: List<MediaController>) {
        val pick = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.metadata != null }
        if (pick?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = pick
            pick?.registerCallback(controllerCallback, main)
        }
        publish()
    }

    private fun publish() {
        if (_state.value == MusicState.NoAccess && registered.not()) return
        val c = controller
        val md = c?.metadata
        if (c == null || md == null) {
            _state.value = MusicState.Idle
            return
        }
        val ps = c.playbackState
        val actions = ps?.actions ?: 0L
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        _state.value = MusicState.Playing(
            NowPlaying(
                packageName = c.packageName,
                title = title,
                artist = artist,
                art = art,
                durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
                playing = ps?.state == PlaybackState.STATE_PLAYING || ps?.state == PlaybackState.STATE_BUFFERING,
                positionMs = ps?.position ?: 0L,
                positionUpdatedAt = ps?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
                speed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
                canSeek = actions and PlaybackState.ACTION_SEEK_TO != 0L,
                canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
                canSkipPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            ),
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }
    fun seekTo(ms: Long) { controller?.transportControls?.seekTo(ms) }

    /** Opens the player app's own screen. */
    fun openPlayer(): Boolean {
        val c = controller ?: return false
        return try {
            val pi = c.sessionActivity
            if (pi != null) {
                val options = android.app.ActivityOptions.makeBasic()
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    options.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }
                pi.send(context, 0, null, null, null, null, options.toBundle())
                true
            } else {
                val launch = context.packageManager.getLaunchIntentForPackage(c.packageName) ?: return false
                context.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        @Volatile private var instance: MediaRepository? = null

        fun get(context: Context): MediaRepository = instance ?: synchronized(this) {
            instance ?: MediaRepository(context.applicationContext).also { instance = it }
        }
    }
}
