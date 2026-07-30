package org.autojs.autojs.ui.media

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityVideoPlayerBinding

class VideoPlayerActivity : BaseActivity() {

    private lateinit var binding: ActivityVideoPlayerBinding
    private lateinit var request: MediaViewerRequest
    private var player: ExoPlayer? = null
    private var playbackPosition = 0L
    private var playWhenReady = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        request = MediaViewerLauncher.requestFromIntent(intent, MediaViewerKind.VIDEO) ?: run {
            finish()
            return
        }
        playbackPosition = savedInstanceState?.getLong(STATE_POSITION) ?: 0L
        playWhenReady = savedInstanceState?.getBoolean(STATE_PLAY_WHEN_READY) ?: true

        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setToolbarAsBack(request.displayName.ifBlank { getString(R.string.app_name) })
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    override fun onStop() {
        releasePlayer()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        player?.let {
            playbackPosition = it.currentPosition.coerceAtLeast(0L)
            playWhenReady = it.playWhenReady
        }
        outState.putLong(STATE_POSITION, playbackPosition)
        outState.putBoolean(STATE_PLAY_WHEN_READY, playWhenReady)
        super.onSaveInstanceState(outState)
    }

    private fun initializePlayer() {
        if (player != null) return
        binding.errorText.isVisible = false
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()
        player = ExoPlayer.Builder(this)
            .build()
            .also { exoPlayer ->
                exoPlayer.setAudioAttributes(audioAttributes, true)
                exoPlayer.setHandleAudioBecomingNoisy(true)
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        binding.errorText.isVisible = true
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        binding.playerView.keepScreenOn = isPlaying
                    }
                })
                binding.playerView.player = exoPlayer
                val mediaItem = MediaItem.Builder()
                    .setUri(request.uri)
                    .setMimeType(request.mimeType)
                    .build()
                exoPlayer.setMediaItem(mediaItem, playbackPosition)
                exoPlayer.playWhenReady = playWhenReady
                exoPlayer.prepare()
            }
    }

    private fun releasePlayer() {
        val currentPlayer = player ?: return
        playbackPosition = currentPlayer.currentPosition.coerceAtLeast(0L)
        playWhenReady = currentPlayer.playWhenReady
        binding.playerView.player = null
        binding.playerView.keepScreenOn = false
        currentPlayer.release()
        player = null
    }

    private companion object {
        const val STATE_POSITION = "media_video_position"
        const val STATE_PLAY_WHEN_READY = "media_video_play_when_ready"
    }
}
