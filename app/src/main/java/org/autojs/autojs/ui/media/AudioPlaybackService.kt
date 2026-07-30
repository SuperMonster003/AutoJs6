package org.autojs.autojs.ui.media

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class AudioPlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        player = ExoPlayer.Builder(this)
            .build()
            .apply {
                setAudioAttributes(audioAttributes, true)
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_LOCAL)
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                })
            }
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_PLAY_URI) {
            if (!handlePlayIntent(intent)) {
                stopSelfResult(startId)
            }
        }
        return result
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    private fun handlePlayIntent(intent: Intent): Boolean {
        val uri = intent.data ?: return false
        val mimeType = MediaViewerPolicy.normalizeMimeType(intent.type) ?: return false
        if (MediaViewerPolicy.kindForMimeType(mimeType) != MediaViewerKind.AUDIO) return false

        val title = sequenceOf(
            intent.getStringExtra(MediaViewerLauncher.EXTRA_DISPLAY_NAME),
            uri.lastPathSegment?.substringAfterLast('/'),
        )
            .filterNotNull()
            .map(MediaViewerLauncher::sanitizeDisplayName)
            .firstOrNull(String::isNotBlank)
            ?: applicationInfo.loadLabel(packageManager).toString()

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMimeType(mimeType)
            .setMediaId(uri.toString().hashCode().toUInt().toString(16))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()

        mediaSession.setSessionActivity(createSessionActivity(uri, mimeType, title))
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        return true
    }

    private fun createSessionActivity(
        uri: android.net.Uri,
        mimeType: String,
        title: String,
    ): PendingIntent {
        val activityIntent = Intent(this, AudioPlayerActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(uri, mimeType)
            putExtra(MediaViewerLauncher.EXTRA_DISPLAY_NAME, title)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return PendingIntent.getActivity(
            this,
            SESSION_ACTIVITY_REQUEST_CODE,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val ACTION_PLAY_URI = "org.autojs.autojs.action.PLAY_AUDIO_URI"
        private const val SESSION_ACTIVITY_REQUEST_CODE = 0xA61D

        internal fun startPlayback(context: Context, request: MediaViewerRequest): Boolean {
            if (request.kind != MediaViewerKind.AUDIO) return false
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_PLAY_URI
                setDataAndType(request.uri, request.mimeType)
                putExtra(
                    MediaViewerLauncher.EXTRA_DISPLAY_NAME,
                    MediaViewerLauncher.sanitizeDisplayName(request.displayName),
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            return runCatching {
                ContextCompat.startForegroundService(context, intent)
                true
            }.getOrDefault(false)
        }
    }
}
