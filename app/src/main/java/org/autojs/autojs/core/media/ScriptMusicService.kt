package org.autojs.autojs.core.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import org.autojs.autojs6.R
import java.io.File

/** One music session per application, shared by Rhino runtimes in both app and inrt. */
@androidx.annotation.OptIn(UnstableApi::class)
class ScriptMusicService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private var owner: ScriptMusicPlayer.Connection? = null

    internal inner class LocalBinder : Binder() {
        val service: ScriptMusicService get() = this@ScriptMusicService
    }

    override fun onCreate() {
        super.onCreate()
        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setNotificationId(NOTIFICATION_ID)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.text_script_music)
            .build()
        provider.setSmallIcon(R.drawable.autojs6_status_bar_icon)
        setMediaNotificationProvider(provider)
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER)
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        session = MediaSession.Builder(this, player).setCallback(SessionCallback()).apply {
            packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                setSessionActivity(PendingIntent.getActivity(this@ScriptMusicService, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
        }.build()
        addSession(session)
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (player.isPlaying) owner?.ready?.complete(Unit)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "Script music playback failed", error)
                owner?.dispose(IllegalStateException("Cannot play the requested audio file.", error))
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                    owner?.takeUnless { it.ready.isDone }?.dispose(IllegalStateException(RECOVERY_MESSAGE))
                }
            }
        })
        setListener(object : Listener {
            override fun onForegroundServiceStartNotAllowedException() {
                owner?.dispose(IllegalStateException(RECOVERY_MESSAGE))
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder? {
        val binder = super.onBind(intent)
        return if (intent?.action == LOCAL_BIND) LocalBinder() else binder
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        if (owner != null && isAllowed(controllerInfo)) session else null

    internal fun play(request: ScriptMusicPlayer.Connection) {
        // Validate before taking ownership, so an unreadable path cannot stop another script.
        val file = File(request.path)
        require(file.isFile && file.canRead()) { "Cannot read the requested audio file: ${file.name}" }
        val previous = owner
        owner = request
        previous?.dispose(IllegalStateException("Another script took over music playback."))
        player.stop()
        player.clearMediaItems()
        // The script API accepts file paths; do not turn arbitrary strings into network requests.
        player.volume = request.volume
        player.repeatMode = if (request.looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.setMediaItem(MediaItem.Builder().setUri(Uri.fromFile(file))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(file.name).build()).build())
        // Promote before prepare/play requests audio focus. Automatic notification updates occur later.
        promote()
        player.prepare()
        player.play()
    }

    internal fun pause(request: ScriptMusicPlayer.Connection) { if (owner === request) player.pause() }
    internal fun resume(request: ScriptMusicPlayer.Connection) {
        if (owner !== request) return
        try {
            promote()
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        } catch (error: Exception) {
            request.dispose(error)
            throw error
        }
    }
    internal fun seekTo(request: ScriptMusicPlayer.Connection, position: Int) {
        if (owner === request) player.seekTo(position.toLong().coerceAtLeast(0))
    }
    internal fun isPlaying(request: ScriptMusicPlayer.Connection) = owner === request && player.isPlaying
    internal fun hasEnded(request: ScriptMusicPlayer.Connection) = owner === request && player.playbackState == Player.STATE_ENDED
    internal fun duration(request: ScriptMusicPlayer.Connection): Int =
        if (owner === request && player.duration != C.TIME_UNSET) player.duration.coerceIn(0, Int.MAX_VALUE.toLong()).toInt() else 0
    internal fun position(request: ScriptMusicPlayer.Connection): Int =
        if (owner === request) player.currentPosition.coerceIn(0, Int.MAX_VALUE.toLong()).toInt() else -1

    internal fun release(request: ScriptMusicPlayer.Connection) {
        if (owner !== request) return
        owner = null
        player.stop()
        player.clearMediaItems()
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        stopSelf()
    }

    override fun onDestroy() {
        val previous = owner
        owner = null
        previous?.dispose(IllegalStateException("The script music service stopped."))
        session.release()
        player.release()
        super.onDestroy()
    }

    private fun promote() {
        if (Build.VERSION.SDK_INT >= 34 && applicationInfo.targetSdkVersion >= 34) {
            check(checkSelfPermission("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK") == PackageManager.PERMISSION_GRANTED) {
                "The packed app needs android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK in its project permissions. Add it and rebuild the app."
            }
        }
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.text_script_music), NotificationManager.IMPORTANCE_LOW),
        )
        @Suppress("DEPRECATION") // API 24-25 have no notification channels.
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        val notification = builder.setSmallIcon(R.drawable.autojs6_status_bar_icon)
            .setContentTitle(player.mediaMetadata.title ?: getString(R.string.text_script_music))
            .setStyle(Notification.MediaStyle().setMediaSession(session.platformToken))
            .setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PUBLIC).build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(NOTIFICATION_ID, notification)
        } catch (error: RuntimeException) {
            throw IllegalStateException(RECOVERY_MESSAGE, error)
        }
    }

    private fun isAllowed(controller: MediaSession.ControllerInfo) = controller.uid == Process.myUid() || controller.isTrusted

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            if (!isAllowed(controller)) return MediaSession.ConnectionResult.reject()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    .remove(Player.COMMAND_SET_MEDIA_ITEM).remove(Player.COMMAND_CHANGE_MEDIA_ITEMS).build())
                .build()
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION") // Check promotion before a remote play command requests focus.
        override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int {
            if (owner == null) return SessionResult.RESULT_ERROR_INVALID_STATE
            if (playerCommand == Player.COMMAND_PLAY_PAUSE && !player.playWhenReady) {
                try { promote() } catch (error: Exception) {
                    owner?.dispose(error)
                    return SessionResult.RESULT_ERROR_INVALID_STATE
                }
            }
            return SessionResult.RESULT_SUCCESS
        }

        override fun onPlayerInteractionFinished(session: MediaSession, controllerInfo: MediaSession.ControllerInfo, playerCommands: Player.Commands) {
            if (playerCommands.contains(Player.COMMAND_STOP)) owner?.dispose()
        }
    }

    companion object {
        internal const val LOCAL_BIND = "org.autojs.autojs.action.BIND_SCRIPT_MUSIC"
        internal const val NOTIFICATION_ID = 0x534D
        internal const val CHANNEL_ID = "script_music"
        private const val TAG = "ScriptMusicService"
        private const val RECOVERY_MESSAGE = "Android did not allow script music playback. Open the app and retry media.playMusic from a script thread. Background schedules and boot do not guarantee audio eligibility."
    }
}
