package org.autojs.autojs.ui.media

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityAudioPlayerBinding

class AudioPlayerActivity : BaseActivity() {

    private lateinit var binding: ActivityAudioPlayerBinding
    private lateinit var request: MediaViewerRequest
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            binding.errorText.isVisible = true
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            updateTitle(mediaMetadata.title)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        request = MediaViewerLauncher.requestFromIntent(intent, MediaViewerKind.AUDIO) ?: run {
            finish()
            return
        }

        binding = ActivityAudioPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setToolbarAsBack(request.displayName.ifBlank { getString(R.string.app_name) })

        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_START_PLAYBACK, false)) {
            if (!AudioPlaybackService.startPlayback(this, request)) {
                ViewUtils.showToast(this, R.string.text_cannot_read_file)
                finish()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val updatedRequest =
            MediaViewerLauncher.requestFromIntent(intent, MediaViewerKind.AUDIO) ?: return
        setIntent(intent)
        request = updatedRequest
        binding.toolbar.title = request.displayName.ifBlank { getString(R.string.app_name) }
        binding.errorText.isVisible = false
    }

    override fun onStart() {
        super.onStart()
        connectController()
    }

    override fun onStop() {
        disconnectController()
        super.onStop()
    }

    private fun connectController() {
        if (controllerFuture != null) return
        val token = SessionToken(this, ComponentName(this, AudioPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                if (controllerFuture !== future || isFinishing || isDestroyed) return@addListener
                runCatching { future.get() }
                    .onSuccess {
                        controller = it
                        it.addListener(playerListener)
                        binding.playerView.player = it
                        binding.errorText.isVisible = it.playerError != null
                        updateTitle(it.mediaMetadata.title)
                    }
                    .onFailure {
                        ViewUtils.showToast(this, R.string.text_cannot_read_file)
                        finish()
                    }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun disconnectController() {
        binding.playerView.player = null
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
    }

    private fun updateTitle(title: CharSequence?) {
        binding.toolbar.title = title
            ?.toString()
            ?.let(MediaViewerLauncher::sanitizeDisplayName)
            ?.takeIf(String::isNotBlank)
            ?: request.displayName.ifBlank { getString(R.string.app_name) }
    }

    companion object {
        internal const val EXTRA_START_PLAYBACK = "org.autojs.autojs.extra.START_MEDIA_PLAYBACK"
    }
}
