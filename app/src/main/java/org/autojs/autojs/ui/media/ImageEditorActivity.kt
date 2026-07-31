package org.autojs.autojs.ui.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityImageViewerBinding
import java.io.File

class ImageEditorActivity : BaseActivity() {

    private lateinit var binding: ActivityImageViewerBinding
    private var sourceFile: File? = null
    private var bitmap: Bitmap? = null
    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceFile = intent.getStringExtra(EXTRA_PATH)
            ?.let(::File)
            ?.let { runCatching { it.canonicalFile }.getOrNull() }
            ?.takeIf { it.isFile && it.canRead() }
        if (sourceFile == null) {
            finish()
            return
        }
        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setToolbarAsBack(sourceFile!!.name)
        binding.toolbar.subtitle = getString(R.string.text_edit)
        loadBitmap()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_image_editor, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val enabled = bitmap != null && !saving
        for (index in 0 until menu.size()) menu.getItem(index).isEnabled = enabled
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_rotate_left -> true.also { transform(rotation = -90f) }
        R.id.action_rotate_right -> true.also { transform(rotation = 90f) }
        R.id.action_flip_horizontal -> true.also { transform(flipX = true) }
        R.id.action_flip_vertical -> true.also { transform(flipY = true) }
        R.id.action_save -> true.also { save() }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        if (::binding.isInitialized) binding.image.setImageDrawable(null)
        // Compression runs on Dispatchers.IO and may finish just after lifecycle
        // cancellation. Do not recycle its source out from under the encoder.
        if (!saving) bitmap?.recycle()
        bitmap = null
        super.onDestroy()
    }

    private fun loadBitmap() {
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { ImageBitmapIO.decodeForEditing(sourceFile!!) }
            }
            result.onSuccess { loaded ->
                bitmap = loaded
                binding.image.setImageBitmap(loaded)
                binding.loadingIndicator.isVisible = false
                binding.errorText.isVisible = false
                invalidateOptionsMenu()
            }.onFailure {
                binding.loadingIndicator.isVisible = false
                binding.errorText.isVisible = true
            }
        }
    }

    private fun transform(rotation: Float = 0f, flipX: Boolean = false, flipY: Boolean = false) {
        val previous = bitmap ?: return
        val transformed = runCatching {
            ImageBitmapIO.transform(previous, rotation, flipX, flipY)
        }.getOrElse {
            ViewUtils.showToast(this, R.string.text_failed, true)
            return
        }
        if (transformed !== previous) previous.recycle()
        bitmap = transformed
        binding.image.resetZoom()
        binding.image.setImageBitmap(transformed)
    }

    private fun save() {
        val currentBitmap = bitmap ?: return
        val currentFile = sourceFile ?: return
        saving = true
        binding.loadingIndicator.isVisible = true
        invalidateOptionsMenu()
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { ImageBitmapIO.saveEdited(currentBitmap, currentFile) }
            }
            saving = false
            binding.loadingIndicator.isVisible = false
            invalidateOptionsMenu()
            result.onSuccess { saved ->
                sourceFile = saved
                binding.toolbar.title = saved.name
                Explorers.workspace().notifyItemCreated(
                    ExplorerFileItem(saved, ExplorerDirPage(saved.parentFile, null)),
                )
                ViewUtils.showToast(this@ImageEditorActivity, R.string.text_done, true)
                setResult(RESULT_OK, Intent().putExtra(EXTRA_PATH, saved.path))
            }.onFailure {
                ViewUtils.showToast(this@ImageEditorActivity, R.string.error_failed_to_save, true)
            }
        }
    }

    companion object {
        internal const val EXTRA_PATH = "org.autojs.autojs.extra.IMAGE_EDIT_PATH"

        internal fun createIntent(context: Context, file: File): Intent =
            Intent(context, ImageEditorActivity::class.java).putExtra(EXTRA_PATH, file.path)

        @JvmStatic
        fun launch(context: Context, file: File): Boolean = createIntent(context, file).startSafely(context)
    }
}
