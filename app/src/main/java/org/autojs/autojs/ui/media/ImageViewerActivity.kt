package org.autojs.autojs.ui.media

import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityImageViewerBinding

class ImageViewerActivity : BaseActivity() {

    private lateinit var binding: ActivityImageViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = MediaViewerLauncher.requestFromIntent(intent, MediaViewerKind.IMAGE) ?: run {
            finish()
            return
        }

        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setToolbarAsBack(request.displayName.ifBlank { getString(R.string.app_name) })

        Glide.with(this)
            .load(request.uri)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean,
                ): Boolean {
                    showLoadFailure()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean,
                ): Boolean {
                    binding.loadingIndicator.isVisible = false
                    binding.errorText.isVisible = false
                    return false
                }
            })
            .into(binding.image)
    }

    override fun onDestroy() {
        if (::binding.isInitialized) {
            Glide.with(this).clear(binding.image)
        }
        super.onDestroy()
    }

    private fun showLoadFailure() {
        binding.loadingIndicator.isVisible = false
        binding.errorText.isVisible = true
    }
}
