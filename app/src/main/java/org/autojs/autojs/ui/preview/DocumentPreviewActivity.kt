package org.autojs.autojs.ui.preview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.edit.editor.loading.EditorFileReadPipeline
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.FileUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.isNightModeYes
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityDocumentPreviewBinding
import org.autojs.autojs6.databinding.DialogDocumentPreviewSettingsBinding
import java.io.File
import java.io.IOException

class DocumentPreviewActivity : BaseActivity() {

    private lateinit var binding: ActivityDocumentPreviewBinding
    private lateinit var previewFile: File
    private lateinit var previewType: FileUtils.PreviewType
    private lateinit var preferences: DocumentPreviewPreferences
    private lateinit var webController: DocumentPreviewWebController

    private val renderer = DocumentPreviewRenderer()
    private var loadJob: Job? = null
    private var loadGeneration = 0
    private var fullscreenMode = false

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (fullscreenMode) {
                setFullscreenMode(false)
                return
            }
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    private val customCssPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importCustomCss)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val resolved = resolvePreviewFile() ?: run {
            ViewUtils.showToast(this, R.string.text_cannot_read_file)
            finish()
            return
        }
        previewFile = resolved.first
        previewType = resolved.second
        preferences = DocumentPreviewPreferences(this)
        fullscreenMode = savedInstanceState?.getBoolean(STATE_FULLSCREEN_MODE)
            ?: preferences.startInFullscreenMode

        binding = ActivityDocumentPreviewBinding.inflate(layoutInflater).also {
            setContentView(it.root)
            it.previewWebView.setBackgroundColor(getColor(R.color.window_background))
        }
        setToolbarAsBack(previewFile.name)
        setFullscreenMode(fullscreenMode, invalidateMenu = false)
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        webController = DocumentPreviewWebController(
            context = this,
            webView = binding.previewWebView,
            resourceRoot = requireNotNull(previewFile.parentFile ?: previewFile.absoluteFile.parentFile),
            onExternalLink = { uri ->
                Intent(Intent.ACTION_VIEW, uri).startSafely(this)
            },
            onPageFinished = {
                binding.loadingIndicator.isVisible = false
            },
        )
        loadPreview()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_document_preview, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val markdown = previewType == FileUtils.PreviewType.MARKDOWN
        menu.findItem(R.id.action_preview_theme)?.isVisible = markdown
        menu.findItem(R.id.action_import_custom_css)?.isVisible = markdown
        menu.findItem(R.id.action_clear_custom_css)?.isVisible = markdown && preferences.hasCustomCss
        menu.findItem(R.id.action_fullscreen_mode)?.isChecked = fullscreenMode
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_refresh -> true.also { loadPreview() }
        R.id.action_preview_theme -> true.also { showThemeDialog() }
        R.id.action_import_custom_css -> true.also { openCustomCssPicker() }
        R.id.action_clear_custom_css -> true.also { clearCustomCss() }
        R.id.action_fullscreen_mode -> true.also { setFullscreenMode(!fullscreenMode) }
        R.id.action_document_preview_settings -> true.also { showSettingsDialog() }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_FULLSCREEN_MODE, fullscreenMode)
        super.onSaveInstanceState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && fullscreenMode && ::binding.isInitialized) {
            applyFullscreenMode()
        }
    }

    override fun onDestroy() {
        loadGeneration++
        loadJob?.cancel()
        if (::webController.isInitialized) {
            webController.destroy()
        }
        super.onDestroy()
    }

    private fun resolvePreviewFile(): Pair<File, FileUtils.PreviewType>? {
        val path = intent.getStringExtra(EXTRA_PATH)?.takeIf(String::isNotBlank) ?: return null
        val file = runCatching { File(path).canonicalFile }.getOrNull()?.takeIf(File::isFile) ?: return null
        val type = FileUtils.TYPE.determineBy(file)
        return type.previewType?.takeIf { type.isPreviewable() }?.let { file to it }
    }

    private fun loadPreview() {
        val generation = ++loadGeneration
        loadJob?.cancel()
        binding.loadingIndicator.isVisible = true
        binding.errorText.isVisible = false

        val selectedTheme = preferences.theme
        val stylesheetName = selectedTheme.stylesheetName(isNightModeYes(this))
        loadJob = lifecycleScope.launch {
            try {
                val input = withContext(Dispatchers.IO) {
                    if (!previewFile.isFile) throw IOException("Preview file is unavailable")
                    if (previewFile.length() > MAX_DOCUMENT_BYTES) {
                        throw DocumentTooLargeException(MAX_DOCUMENT_BYTES)
                    }
                    RenderInput(
                        source = EditorFileReadPipeline(this@DocumentPreviewActivity)
                            .readText(Uri.fromFile(previewFile))
                            .text,
                        customCss = if (selectedTheme == DocumentPreviewTheme.CUSTOM) {
                            preferences.readCustomCss()
                        } else {
                            null
                        },
                    )
                }
                val html = withContext(Dispatchers.Default) {
                    synchronized(renderer) {
                        when (previewType) {
                            FileUtils.PreviewType.MARKDOWN -> renderer.renderMarkdown(
                                markdown = input.source,
                                stylesheetName = stylesheetName,
                                customCss = input.customCss,
                            )
                            FileUtils.PreviewType.HTML -> renderer.renderHtml(input.source)
                        }
                    }
                }
                if (generation != loadGeneration) return@launch
                binding.previewWebView.isVisible = true
                webController.show(html)
            } catch (_: CancellationException) {
                // A newer load request superseded this one.
            } catch (error: Throwable) {
                if (generation == loadGeneration) {
                    showLoadError(error)
                }
            }
        }
    }

    private fun showLoadError(error: Throwable) {
        binding.loadingIndicator.isVisible = false
        binding.previewWebView.isVisible = false
        binding.errorText.isVisible = true
        binding.errorText.text = when (error) {
            is DocumentTooLargeException -> getString(
                R.string.error_document_preview_file_too_large,
                PFiles.formatSizeWithUnit(error.limitBytes.toLong()),
            )
            else -> getString(R.string.text_cannot_read_file)
        }
    }

    private fun showThemeDialog() {
        val themes = buildList {
            addAll(DocumentPreviewTheme.BUILT_IN)
            if (preferences.hasCustomCss) add(DocumentPreviewTheme.CUSTOM)
        }
        val selectedIndex = themes.indexOf(preferences.theme).coerceAtLeast(0)
        MaterialDialog.Builder(this)
            .title(R.string.text_preview_theme)
            .items(themes.map { getString(it.labelRes) })
            .itemsCallbackSingleChoice(selectedIndex) { _, _, which, _ ->
                preferences.theme = themes[which]
                loadPreview()
                true
            }
            .choiceWidgetThemeColor()
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .showAdaptive()
    }

    private fun showSettingsDialog() {
        val settingsBinding = DialogDocumentPreviewSettingsBinding.inflate(layoutInflater).apply {
            startInFullscreenMode.isChecked = preferences.startInFullscreenMode
        }
        MaterialDialog.Builder(this)
            .title(R.string.text_settings)
            .customView(settingsBinding.root, false)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { _, _ ->
                preferences.startInFullscreenMode = settingsBinding.startInFullscreenMode.isChecked
            }
            .showAdaptive()
    }

    private fun setFullscreenMode(enabled: Boolean, invalidateMenu: Boolean = true) {
        val changed = fullscreenMode != enabled
        fullscreenMode = enabled
        binding.appBar.isVisible = !enabled
        applyFullscreenMode()
        if (changed && invalidateMenu) {
            invalidateOptionsMenu()
        }
    }

    private fun applyFullscreenMode() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            when {
                fullscreenMode -> {
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
                else -> show(WindowInsetsCompat.Type.systemBars())
            }
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun openCustomCssPicker() {
        customCssPicker.launch(arrayOf("text/css", "text/plain"))
    }

    private fun importCustomCss(uri: Uri) {
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val input = contentResolver.openInputStream(uri)
                        ?: throw IOException("Cannot open the selected CSS file")
                    input.use(preferences::importCustomCss)
                }
            }
            result.onSuccess {
                preferences.theme = DocumentPreviewTheme.CUSTOM
                ViewUtils.showToast(this@DocumentPreviewActivity, R.string.text_custom_css_imported)
                invalidateOptionsMenu()
                loadPreview()
            }.onFailure { error ->
                val message = when (error) {
                    is CustomCssTooLargeException -> getString(
                        R.string.error_custom_css_file_too_large,
                        PFiles.formatSizeWithUnit(error.limitBytes.toLong()),
                    )
                    else -> getString(R.string.text_failed_to_import)
                }
                ViewUtils.showToast(this@DocumentPreviewActivity, message)
            }
        }
    }

    private fun clearCustomCss() {
        preferences.clearCustomCss()
        ViewUtils.showToast(this, R.string.text_custom_css_cleared)
        invalidateOptionsMenu()
        loadPreview()
    }

    private data class RenderInput(
        val source: String,
        val customCss: String?,
    )

    private class DocumentTooLargeException(
        val limitBytes: Int,
    ) : IOException()

    companion object {
        const val MAX_DOCUMENT_BYTES = 8 * 1024 * 1024

        private const val EXTRA_PATH = "path"
        private const val STATE_FULLSCREEN_MODE = "fullscreen_mode"

        fun launch(context: Context, path: String) {
            Intent(context, DocumentPreviewActivity::class.java)
                .putExtra(EXTRA_PATH, path)
                .startSafely(context)
        }
    }
}
