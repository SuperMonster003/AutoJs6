package org.autojs.autojs.core.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import org.autojs.autojs6.R
import java.io.File

/**
 * One music session per application, shared by Rhino runtimes in both app and inrt.
 *
 * Platform `MediaPlayer` + `android.media.session.MediaSession` + `Notification.MediaStyle`, no media3 (APK size
 * roadmap P6.1, D11). Every player, session, focus and ownership operation runs on the main looper: the binder
 * calls come from [ScriptMusicPlayer] (which posts to the main looper), `MediaPlayer` callbacks are delivered on
 * the looper that created the player, the session callback and the focus listener are bound to the main handler.
 *
 * zh-CN: 每个应用只有一个音乐会话, 由 app 与 inrt 的 Rhino 运行时共用. 使用平台 `MediaPlayer` + `MediaSession` +
 * `Notification.MediaStyle`, 不依赖 media3 (APK 体积 roadmap P6.1, D11). 播放器, 会话, 焦点与所有权的每个操作都在主
 * looper 上执行: binder 调用来自 [ScriptMusicPlayer] (它把操作投递到主 looper), `MediaPlayer` 回调投递到创建它的 looper,
 * 会话回调与焦点监听绑定到主 handler.
 */
class ScriptMusicService : Service() {
    private lateinit var player: MediaPlayer
    private lateinit var session: MediaSession
    private lateinit var audioManager: AudioManager
    private var owner: ScriptMusicPlayer.Connection? = null
    private var title: String? = null

    /** The current owner's data source has been prepared (duration known, transport controls valid). */
    private var prepared = false

    /** Non-looping playback reached the end; the next resume restarts from the beginning. */
    private var ended = false
    private var hasFocus = false
    private var pausedByFocusLoss = false
    private var ducked = false
    private var focusRequest: AudioFocusRequest? = null
    private var noisyReceiverRegistered = false

    internal inner class LocalBinder : Binder() {
        val service: ScriptMusicService get() = this@ScriptMusicService
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change -> onAudioFocusChange(change) }

    // Headphones unplugged: pause, as ExoPlayer's setHandleAudioBecomingNoisy(true) did.
    // zh-CN: 耳机拔出: 暂停, 与 ExoPlayer 的 setHandleAudioBecomingNoisy(true) 一致.
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pauseByUser()
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        player = MediaPlayer()
        session = MediaSession(this, TAG).apply {
            @Suppress("DEPRECATION") // API 24 / 25 route media buttons and transport controls only with these flags.
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(SessionCallback(), main)
            packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                setSessionActivity(PendingIntent.getActivity(this@ScriptMusicService, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
        }
        publishState()
    }

    override fun onBind(intent: Intent?): IBinder? = if (intent?.action == LOCAL_BIND) LocalBinder() else null

    /** Notification actions; the service is already in the foreground whenever its notification exists. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> owner?.let { request -> runCatching { resume(request) }.onFailure { Log.w(TAG, "Resume from the notification failed", it) } }
            ACTION_PAUSE -> pauseByUser()
            ACTION_STOP -> owner?.dispose()
        }
        return START_NOT_STICKY
    }

    internal fun play(request: ScriptMusicPlayer.Connection) {
        // Validate before taking ownership, so an unreadable path cannot stop another script.
        val file = File(request.path)
        require(file.isFile && file.canRead()) { "Cannot read the requested audio file: ${file.name}" }
        val previous = owner
        owner = request
        previous?.dispose(IllegalStateException("Another script took over music playback."))
        resetPlayer()
        title = file.name
        session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, file.name).build())
        session.isActive = true
        // Promote before prepare / play requests audio focus. The notification is refreshed once prepared.
        promote()
        try {
            // The script API accepts file paths; do not turn arbitrary strings into network requests.
            player.setDataSource(file.path)
            player.setAudioAttributes(AUDIO_ATTRIBUTES)
            player.isLooping = request.looping
            player.setVolume(request.volume, request.volume)
            player.setOnPreparedListener { if (owner === request) onPrepared(request) }
            player.setOnCompletionListener { if (owner === request) onCompletion() }
            player.setOnErrorListener { _, what, extra ->
                if (owner === request) onPlayerError(request, what, extra)
                true
            }
            player.prepareAsync()
        } catch (error: Exception) {
            throw IllegalStateException("Cannot play the requested audio file.", error)
        }
    }

    private fun onPrepared(request: ScriptMusicPlayer.Connection) {
        prepared = true
        ended = false
        if (!requestFocus()) {
            request.dispose(IllegalStateException(RECOVERY_MESSAGE))
            return
        }
        player.start()
        registerNoisyReceiver()
        publishState()
        updateNotification()
        request.ready.complete(Unit)
    }

    private fun onCompletion() {
        ended = true
        unregisterNoisyReceiver()
        publishState()
        updateNotification()
    }

    private fun onPlayerError(request: ScriptMusicPlayer.Connection, what: Int, extra: Int) {
        Log.w(TAG, "Script music playback failed: what=$what extra=$extra")
        request.dispose(IllegalStateException("Cannot play the requested audio file.", IllegalStateException("MediaPlayer error what=$what extra=$extra")))
    }

    internal fun pause(request: ScriptMusicPlayer.Connection) {
        if (owner === request) pauseByUser()
    }

    internal fun resume(request: ScriptMusicPlayer.Connection) {
        if (owner !== request) return
        try {
            promote()
            if (!prepared) return
            if (!requestFocus()) throw IllegalStateException(RECOVERY_MESSAGE)
            if (ended) {
                ended = false
                seekToMillis(0)
            }
            pausedByFocusLoss = false
            player.start()
            registerNoisyReceiver()
            publishState()
            updateNotification()
        } catch (error: Exception) {
            request.dispose(error)
            throw error
        }
    }

    internal fun seekTo(request: ScriptMusicPlayer.Connection, position: Int) {
        if (owner !== request || !prepared) return
        // A seek after natural completion behaves like ExoPlayer's STATE_ENDED -> STATE_READY transition.
        ended = false
        seekToMillis(position.coerceAtLeast(0))
        publishState()
    }

    internal fun isPlaying(request: ScriptMusicPlayer.Connection) = owner === request && prepared && player.isPlaying
    internal fun hasEnded(request: ScriptMusicPlayer.Connection) = owner === request && ended
    internal fun duration(request: ScriptMusicPlayer.Connection): Int =
        if (owner === request && prepared) player.duration.coerceAtLeast(0) else 0

    internal fun position(request: ScriptMusicPlayer.Connection): Int = when {
        owner !== request -> -1
        !prepared -> 0
        // Some MediaPlayer implementations report 0 after completion; the script API keeps the end position.
        ended -> player.duration.coerceAtLeast(0)
        else -> player.currentPosition.coerceAtLeast(0)
    }

    internal fun release(request: ScriptMusicPlayer.Connection) {
        if (owner !== request) return
        owner = null
        resetPlayer()
        session.isActive = false
        publishState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        stopSelf()
    }

    override fun onDestroy() {
        val previous = owner
        owner = null
        previous?.dispose(IllegalStateException("The script music service stopped."))
        resetPlayer()
        session.release()
        player.release()
        super.onDestroy()
    }

    private fun pauseByUser() {
        if (owner == null || !prepared) return
        if (player.isPlaying) player.pause()
        pausedByFocusLoss = false
        unregisterNoisyReceiver()
        publishState()
        updateNotification()
    }

    private fun resetPlayer() {
        unregisterNoisyReceiver()
        abandonFocus()
        prepared = false
        ended = false
        title = null
        player.reset()
    }

    private fun seekToMillis(position: Int) {
        if (Build.VERSION.SDK_INT >= 26) player.seekTo(position.toLong(), MediaPlayer.SEEK_CLOSEST) else player.seekTo(position)
    }

    // Audio focus: AUDIOFOCUS_GAIN is held from the first start until stop / release, like ExoPlayer's automatic
    // focus handling; transient loss pauses and resumes on gain, ducking lowers the volume, permanent loss pauses.
    // zh-CN: 音频焦点: 从首次开始播放到停止 / 释放一直持有 AUDIOFOCUS_GAIN, 与 ExoPlayer 的自动焦点处理一致;
    // 暂时丢失时暂停并在重新获得时恢复, 可降低音量时压低音量, 永久丢失时暂停.
    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        val result = if (Build.VERSION.SDK_INT >= 26) {
            val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AUDIO_ATTRIBUTES)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(focusListener, main)
                .build()
                .also { focusRequest = it }
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    private fun abandonFocus() {
        if (hasFocus) {
            if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            else @Suppress("DEPRECATION") audioManager.abandonAudioFocus(focusListener)
        }
        hasFocus = false
        pausedByFocusLoss = false
        ducked = false
    }

    private fun onAudioFocusChange(change: Int) {
        val request = owner ?: return
        if (!prepared) return
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (player.isPlaying) {
                player.pause()
                pausedByFocusLoss = true
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                ducked = true
                player.setVolume(request.volume * DUCK_FACTOR, request.volume * DUCK_FACTOR)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                pausedByFocusLoss = false
                if (player.isPlaying) player.pause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                if (ducked) {
                    ducked = false
                    player.setVolume(request.volume, request.volume)
                }
                if (pausedByFocusLoss && !ended) {
                    pausedByFocusLoss = false
                    player.start()
                }
            }
            else -> return
        }
        publishState()
        updateNotification()
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        ContextCompat.registerReceiver(this, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        noisyReceiverRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        noisyReceiverRegistered = false
        runCatching { unregisterReceiver(noisyReceiver) }
    }

    private fun publishState() {
        val playing = owner != null && prepared && player.isPlaying
        val state = when {
            owner == null -> PlaybackState.STATE_NONE
            !prepared -> PlaybackState.STATE_BUFFERING
            playing -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        val position = if (owner != null && prepared) (if (ended) player.duration else player.currentPosition).coerceAtLeast(0).toLong() else 0L
        session.setPlaybackState(
            PlaybackState.Builder()
                .setState(state, position, if (playing) 1f else 0f)
                .setActions(SESSION_ACTIONS)
                .build(),
        )
    }

    private fun updateNotification() {
        if (owner == null) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val playing = prepared && player.isPlaying
        @Suppress("DEPRECATION") // API 24-25 have no notification channels.
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setSmallIcon(R.drawable.autojs6_status_bar_icon)
            .setContentTitle(title ?: getString(R.string.text_script_music))
            .addAction(
                if (playing) action(R.drawable.ic_pause_black_48dp, R.string.text_pause, ACTION_PAUSE, 1)
                else action(R.drawable.ic_play_arrow_24dp, R.string.text_play, ACTION_PLAY, 2),
            )
            .addAction(action(R.drawable.ic_close_black_24dp, R.string.text_stop, ACTION_STOP, 3))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1))
            .setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PUBLIC).build()
    }

    private fun action(icon: Int, title: Int, action: String, requestCode: Int): Notification.Action {
        val intent = PendingIntent.getService(
            this, requestCode, Intent(this, ScriptMusicService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), getString(title), intent).build()
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
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(NOTIFICATION_ID, notification)
        } catch (error: RuntimeException) {
            throw IllegalStateException(RECOVERY_MESSAGE, error)
        }
    }

    /** System media controls, media buttons and the notification actions drive the owned player only. */
    private inner class SessionCallback : MediaSession.Callback() {
        override fun onPlay() {
            owner?.let { request -> runCatching { resume(request) }.onFailure { Log.w(TAG, "Resume from the session failed", it) } }
        }

        override fun onPause() = pauseByUser()

        override fun onSeekTo(pos: Long) {
            owner?.let { request -> seekTo(request, pos.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()) }
        }

        override fun onStop() {
            owner?.dispose()
        }
    }

    companion object {
        internal const val LOCAL_BIND = "org.autojs.autojs.action.BIND_SCRIPT_MUSIC"
        internal const val NOTIFICATION_ID = 0x534D
        internal const val CHANNEL_ID = "script_music"
        private const val ACTION_PLAY = "org.autojs.autojs.action.SCRIPT_MUSIC_PLAY"
        private const val ACTION_PAUSE = "org.autojs.autojs.action.SCRIPT_MUSIC_PAUSE"
        private const val ACTION_STOP = "org.autojs.autojs.action.SCRIPT_MUSIC_STOP"
        private const val TAG = "ScriptMusicService"
        private const val DUCK_FACTOR = 0.2f
        private const val SESSION_ACTIONS = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP
        private const val RECOVERY_MESSAGE = "Android did not allow script music playback. Open the app and retry media.playMusic from a script thread. Background schedules and boot do not guarantee audio eligibility."
        private val AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        private val main = Handler(Looper.getMainLooper())
    }
}
