package org.autojs.autojs.ui.media

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import androidx.recyclerview.widget.ThemeColorRecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.theme.app.ColorHistoryDatabase
import org.autojs.autojs.theme.app.ColorSelectBaseActivity
import org.autojs.autojs.theme.app.PaletteHistoryItemAdapter
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.common.ScriptOperations
import org.autojs.autojs.storage.history.TrashRepository
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.FileUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityImageViewerBinding
import java.io.File
import java.text.Collator

class ImageViewerActivity : BaseActivity() {

    override val handleStatusBarThemeColorAutomatically = false

    private lateinit var binding: ActivityImageViewerBinding
    private lateinit var initialRequest: MediaViewerRequest

    private var imageFiles: List<File> = emptyList()
    private var currentIndex = 0
    private var currentFile: File? = null
    private var currentUri: Uri? = null
    private var currentMimeType = Mime.IMAGE_WILDCARD
    private var isFullscreen = false
    private var loadGeneration = 0
    private var enumerationGeneration = 0
    private val operationDisposables = CompositeDisposable()

    private val editorLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(ImageEditorActivity.EXTRA_PATH)?.let(::File)?.let(::adoptLocalFile)
        }
    }

    private val backgroundColor: Int
        get() = Pref.getInt(PREF_BACKGROUND_COLOR, DEFAULT_BACKGROUND_COLOR)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialRequest = MediaViewerLauncher.requestFromIntent(intent, MediaViewerKind.IMAGE) ?: run {
            finish()
            return
        }

        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setToolbarAsBack(initialRequest.displayName.ifBlank { getString(R.string.app_name) })
        binding.image.onPageSwipe = { direction -> showRelative(direction) }
        val restoredFile = savedInstanceState?.getString(STATE_CURRENT_PATH)
            ?.let(::File)
            ?.let { runCatching { it.canonicalFile }.getOrNull() }
            ?.takeIf { it.isFile && it.canRead() }
        currentUri = restoredFile?.let { MediaViewerLauncher.uriForLocalFile(this, it) } ?: initialRequest.uri
        currentMimeType = initialRequest.mimeType
        currentFile = restoredFile ?: initialRequest.localPath?.let(::File)
        currentFile?.let { currentMimeType = Mime.fromFile(it.path) ?: Mime.IMAGE_WILDCARD }

        onBackPressedDispatcher.addCallback(this) {
            if (isFullscreen) {
                setFullscreen(false)
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        applyViewerChrome()
        loadCurrentImage()
        when (currentFile) {
            null -> binding.toolbar.subtitle = "1/1"
            else -> {
                binding.toolbar.subtitle = ".../..."
                enumerateImages(currentFile!!)
            }
        }
        val initialFullscreen = savedInstanceState?.getBoolean(STATE_FULLSCREEN)
            ?: Pref.getBoolean(PREF_START_FULLSCREEN, false)
        if (initialFullscreen) {
            binding.root.post { setFullscreen(true) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_FULLSCREEN, isFullscreen)
        currentFile?.path?.let { outState.putString(STATE_CURRENT_PATH, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isFullscreen) binding.root.post { applyFullscreenSystemBars() }
    }

    override fun onStart() {
        super.onStart()
        applyViewerChrome()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_image_viewer, menu)
        tintMenu(menu, foregroundColor())
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val localAvailable = currentFile?.let { it.isFile && it.canRead() } == true
        listOf(
            R.id.action_edit,
            R.id.action_delete_toolbar,
            R.id.action_move_to,
            R.id.action_copy_to,
            R.id.action_rename,
            R.id.action_delete,
            R.id.action_convert,
        ).forEach { id -> menu.findItem(id)?.isEnabled = localAvailable }
        menu.findItem(R.id.action_fullscreen_mode)?.isChecked = isFullscreen
        menu.findItem(R.id.action_start_in_fullscreen_mode)?.isChecked =
            Pref.getBoolean(PREF_START_FULLSCREEN, false)
        tintMenu(menu, foregroundColor())
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_edit -> true.also { editCurrent() }
        R.id.action_delete_toolbar,
        R.id.action_delete,
        -> true.also { deleteCurrent() }
        R.id.action_move_to -> true.also { moveCurrent() }
        R.id.action_copy_to -> true.also { copyCurrent() }
        R.id.action_rename -> true.also { renameCurrent() }
        R.id.action_convert -> true.also { convertCurrent() }
        R.id.action_fullscreen_mode -> true.also { setFullscreen(!isFullscreen) }
        R.id.action_send -> true.also { sendCurrent() }
        R.id.action_open_by_other_apps -> true.also { openCurrentExternally() }
        R.id.action_start_in_fullscreen_mode -> true.also {
            Pref.putBoolean(PREF_START_FULLSCREEN, !Pref.getBoolean(PREF_START_FULLSCREEN, false))
            invalidateOptionsMenu()
        }
        R.id.action_background_color -> true.also { showBackgroundColorPicker() }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        operationDisposables.dispose()
        loadGeneration++
        enumerationGeneration++
        // Glide observes this Activity lifecycle and clears the request itself.
        // Calling Glide.with(this) from onDestroy is invalid because the Activity
        // is already destroyed at that point.
        if (::binding.isInitialized) {
            binding.image.onPageSwipe = null
            binding.image.setImageDrawable(null)
        }
        super.onDestroy()
    }

    private fun enumerateImages(selected: File) {
        val generation = ++enumerationGeneration
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                val parent = selected.parentFile
                val collator = Collator.getInstance()
                parent?.listFiles()
                    ?.asSequence()
                    ?.filter { file ->
                        file.isFile && file.canRead() && runCatching {
                            FileUtils.TYPE.determineBy(file).isImageViewable()
                        }.getOrDefault(false)
                    }
                    ?.sortedWith { first, second -> collator.compare(first.name, second.name) }
                    ?.toList()
                    .orEmpty()
            }.ifEmpty { listOf(selected) }
            if (generation != enumerationGeneration || currentFile?.let { !sameFile(it, selected) } != false) {
                return@launch
            }
            imageFiles = files
            currentIndex = files.indexOfFirst { sameFile(it, selected) }.takeIf { it >= 0 } ?: 0
            currentFile = files[currentIndex]
            updateTitleAndSubtitle()
            invalidateOptionsMenu()
        }
    }

    private fun showRelative(direction: Int) {
        if (imageFiles.isEmpty()) return
        val target = currentIndex + direction
        if (target !in imageFiles.indices) return
        currentIndex = target
        currentFile = imageFiles[target]
        currentUri = MediaViewerLauncher.uriForLocalFile(this, imageFiles[target]) ?: return
        currentMimeType = Mime.fromFile(imageFiles[target].path) ?: Mime.IMAGE_WILDCARD
        updateTitleAndSubtitle()
        loadCurrentImage()
        invalidateOptionsMenu()
    }

    private fun adoptLocalFile(file: File) {
        if (isFinishing || isDestroyed || !::binding.isInitialized) return
        val canonical = runCatching { file.canonicalFile }.getOrNull()
            ?.takeIf { it.isFile && it.canRead() }
            ?: return
        currentFile = canonical
        currentUri = MediaViewerLauncher.uriForLocalFile(this, canonical) ?: return
        currentMimeType = Mime.fromFile(canonical.path) ?: Mime.IMAGE_WILDCARD
        enumerationGeneration++
        imageFiles = emptyList()
        currentIndex = 0
        binding.toolbar.subtitle = ".../..."
        loadCurrentImage()
        enumerateImages(canonical)
    }

    private fun updateTitleAndSubtitle() {
        val file = currentFile
        if (file != null) binding.toolbar.title = file.name
        binding.toolbar.subtitle = if (imageFiles.isEmpty()) "1/1" else "${currentIndex + 1}/${imageFiles.size}"
    }

    private fun loadCurrentImage() {
        if (isFinishing || isDestroyed || !::binding.isInitialized) return
        val uri = currentUri ?: return showLoadFailure()
        val generation = ++loadGeneration
        binding.image.resetZoom()
        binding.loadingIndicator.isVisible = true
        binding.errorText.isVisible = false
        Glide.with(this)
            .load(uri)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean,
                ): Boolean {
                    if (generation == loadGeneration) showLoadFailure()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean,
                ): Boolean {
                    if (generation == loadGeneration) {
                        binding.loadingIndicator.isVisible = false
                        binding.errorText.isVisible = false
                    }
                    return false
                }
            })
            .into(binding.image)
    }

    private fun showLoadFailure() {
        binding.loadingIndicator.isVisible = false
        binding.errorText.isVisible = true
    }

    private fun editCurrent() {
        currentFile?.let { editorLauncher.launch(ImageEditorActivity.createIntent(this, it)) }
    }

    private fun convertCurrent() {
        currentFile?.let { file -> ImageConverter.show(this, file, ::adoptLocalFile) }
    }

    private fun moveCurrent() {
        val file = currentFile ?: return
        operationDisposables.add(
            operationsFor(file).move(ScriptFile(file)).subscribe(
                { moved -> adoptLocalFile(File(moved.path)) },
                { ViewUtils.showToast(this, R.string.error_failed_to_move, true) },
            ),
        )
    }

    private fun copyCurrent() {
        val file = currentFile ?: return
        operationDisposables.add(
            operationsFor(file).copy(ScriptFile(file)).subscribe(
                { copied -> ViewUtils.showToast(this, getString(R.string.text_path_colon_value, copied.path), true) },
                { ViewUtils.showToast(this, R.string.error_failed_to_copy, true) },
            ),
        )
    }

    private fun renameCurrent() {
        val file = currentFile ?: return
        val parent = ExplorerDirPage(file.parentFile, null)
        operationDisposables.add(
            operationsFor(file).rename(ExplorerFileItem(file, parent)).subscribe(
                { renamed -> adoptLocalFile(File(renamed.path)) },
                { ViewUtils.showToast(this, R.string.error_failed_to_rename, true) },
            ),
        )
    }

    private fun deleteCurrent() {
        val file = currentFile ?: return
        var strategy = 0
        MaterialDialog.Builder(this)
            .title(R.string.text_choose_delete_strategy)
            .content(file.name)
            .items(
                getString(R.string.item_move_to_trash),
                getString(R.string.item_delete_permanently),
            )
            .itemsCallbackSingleChoice(0) { _, _, which, _ ->
                strategy = which
                true
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_caution)
            .onPositive { _, _ ->
                if (strategy == 0) moveCurrentToTrash(file) else permanentlyDeleteCurrent(file)
            }
            .build()
            .showAdaptive()
    }

    private fun moveCurrentToTrash(file: File) {
        val progress = MaterialDialog.Builder(this)
            .title(R.string.text_move_file_to_trash)
            .content(R.string.text_moving_to_trash)
            .progress(true, 0)
            .cancelable(false)
            .build()
            .showAdaptive()
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    TrashRepository(applicationContext).moveToTrashWithProgress(
                        file.path,
                        null,
                        null,
                        org.autojs.autojs.util.DialogUtils.OperationController(),
                    )
                }
            }
            progress.dismiss()
            result.onSuccess {
                notifyImageRemoved(file)
                ViewUtils.showToast(this@ImageViewerActivity, R.string.text_moved_to_trash, true)
                removeCurrentFromViewer(file)
            }.onFailure {
                ViewUtils.showToast(this@ImageViewerActivity, R.string.text_failed_to_delete, true)
            }
        }
    }

    private fun permanentlyDeleteCurrent(file: File) {
        MaterialDialog.Builder(this)
            .title(R.string.item_delete_permanently)
            .content(getString(R.string.text_confirm_to_delete) + "\n\n" + file.name)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.text_delete)
            .positiveColorRes(R.color.dialog_button_caution)
            .onPositive { _, _ ->
                lifecycleScope.launch {
                    val deleted = withContext(Dispatchers.IO) { file.delete() }
                    if (!deleted) {
                        ViewUtils.showToast(this@ImageViewerActivity, R.string.text_failed_to_delete, true)
                        return@launch
                    }
                    notifyImageRemoved(file)
                    removeCurrentFromViewer(file)
                }
            }
            .build()
            .showAdaptive()
    }

    private fun notifyImageRemoved(file: File) {
        Explorers.workspace().notifyItemRemoved(
            ExplorerFileItem(file, ExplorerDirPage(file.parentFile, null)),
        )
    }

    private fun removeCurrentFromViewer(file: File) {
        val remaining = imageFiles.filterNot { sameFile(it, file) }
        if (remaining.isEmpty()) {
            finish()
        } else {
            val targetIndex = currentIndex.coerceAtMost(remaining.lastIndex)
            adoptLocalFile(remaining[targetIndex])
        }
    }

    private fun operationsFor(file: File): ScriptOperations = ScriptOperations(
        this,
        binding.root,
        ScriptFile(file.parentFile),
    )

    private fun sendCurrent() {
        val uri = currentUri ?: return
        Intent(Intent.ACTION_SEND).apply {
            type = currentMimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let { Intent.createChooser(it, getString(R.string.text_send)).startSafely(this) }
    }

    private fun openCurrentExternally() {
        val uri = currentUri ?: return
        val target = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, currentMimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Intent.createChooser(target, getString(R.string.text_open_by_other_apps)).apply {
            putExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf(ComponentName(this@ImageViewerActivity, MediaViewerEntryActivity::class.java)),
            )
        }.startSafely(this)
    }

    private fun setFullscreen(enabled: Boolean) {
        isFullscreen = enabled
        binding.appBar.isVisible = !enabled
        applyFullscreenSystemBars()
        invalidateOptionsMenu()
    }

    private fun applyFullscreenSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            if (isFullscreen) {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    private fun showBackgroundColorPicker() {
        lateinit var picker: ColorPickerDialog
        picker = ColorPickerDialog.newBuilder()
            .setUseLegacyMode(false)
            .setAllowCustom(true)
            .setAllowPresets(true)
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setShowAlphaSlider(false)
            .setDialogTitle(R.string.dialog_title_color_palette)
            .setColor(backgroundColor)
            .setColorHistoryHandler { showColorHistory(it) }
            .create()
            .setColorPickerDialogListener { _, color ->
                Pref.putInt(PREF_BACKGROUND_COLOR, color)
                ColorSelectBaseActivity.saveDatabaseForPaletteHistory(
                    lifecycleScope,
                    applicationContext,
                    color,
                )
                applyViewerChrome()
            }
        picker.show(supportFragmentManager, COLOR_PICKER_TAG)
    }

    private fun showColorHistory(colorPickerDialog: ColorPickerDialog) {
        lifecycleScope.launch {
            val dao = withContext(Dispatchers.IO) {
                ColorHistoryDatabase.getInstance(applicationContext).paletteHistoryDao()
            }
            val history = withContext(Dispatchers.IO) { dao.getAll() }
            if (history.isEmpty()) {
                ViewUtils.showToast(this@ImageViewerActivity, R.string.text_no_history, true)
                return@launch
            }
            var historyDialog: MaterialDialog? = null
            val adapter = PaletteHistoryItemAdapter(history) { selectedColor ->
                historyDialog?.dismiss()
                colorPickerDialog.onHistorySelected(selectedColor)
            }
            val recyclerView = ThemeColorRecyclerView(this@ImageViewerActivity).also {
                it.adapter = adapter
                it.layoutManager = LinearLayoutManager(this@ImageViewerActivity)
                it.addItemDecoration(DividerItemDecoration(this@ImageViewerActivity, VERTICAL))
            }
            historyDialog = MaterialDialog.Builder(this@ImageViewerActivity)
                .title(R.string.text_history)
                .customView(recyclerView, false)
                .neutralText(R.string.dialog_button_clear_items)
                .neutralColorRes(R.color.dialog_button_warn)
                .onNeutral { dialog, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { dao.deleteAll() }
                        adapter.updateData(emptyList())
                        dialog.dismiss()
                    }
                }
                .positiveText(R.string.dialog_button_dismiss)
                .positiveColorRes(R.color.dialog_button_default)
                .build()
                .showAdaptive()
        }
    }

    private fun applyViewerChrome() {
        val background = backgroundColor
        val foreground = foregroundColor()
        binding.root.setBackgroundColor(background)
        binding.appBar.setBackgroundColor(background)
        binding.toolbar.setBackgroundColor(background)
        binding.image.setBackgroundColor(background)
        binding.toolbar.setTitleTextColor(foreground)
        binding.toolbar.setSubtitleTextColor(ColorUtils.applyAlpha(foreground, 0.7))
        binding.toolbar.navigationIcon?.let { drawable ->
            DrawableCompat.setTint(drawable.mutate(), foreground)
        }
        binding.loadingIndicator.indeterminateTintList = ColorStateList.valueOf(foreground)
        binding.errorText.setTextColor(ColorUtils.applyAlpha(foreground, 0.7))
        ViewUtils.setStatusBarBackgroundColor(this, background)
        ViewUtils.setStatusBarIconLight(this, ColorUtils.isLuminanceDark(background))
        @Suppress("DEPRECATION")
        window.navigationBarColor = background
        ViewUtils.setNavigationBarIconLight(this, ColorUtils.isLuminanceDark(background))
        invalidateOptionsMenu()
    }

    private fun foregroundColor(): Int = if (ColorUtils.isLuminanceDark(backgroundColor)) Color.WHITE else Color.BLACK

    private fun tintMenu(menu: Menu, color: Int) {
        for (index in 0 until menu.size()) {
            val item = menu.getItem(index)
            item.icon?.let { DrawableCompat.setTint(it.mutate(), color) }
            item.subMenu?.let { tintMenu(it, color) }
        }
    }

    private fun sameFile(first: File, second: File): Boolean = runCatching {
        first.canonicalPath == second.canonicalPath
    }.getOrDefault(first.absolutePath == second.absolutePath)

    companion object {
        private const val PREF_START_FULLSCREEN = "image_viewer_start_fullscreen"
        private const val PREF_BACKGROUND_COLOR = "image_viewer_background_color"
        private const val COLOR_PICKER_TAG = "ImageViewerBackgroundColorPicker"
        private const val STATE_FULLSCREEN = "image_viewer_fullscreen"
        private const val STATE_CURRENT_PATH = "image_viewer_current_path"
        private val DEFAULT_BACKGROUND_COLOR = Color.parseColor("#212121")
    }
}
