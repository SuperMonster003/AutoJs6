package org.autojs.autojs.ui.media

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.pio.PFile
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.theme.app.ColorHistoryDatabase
import org.autojs.autojs.theme.app.ColorSelectBaseActivity
import org.autojs.autojs.theme.app.PaletteHistoryItemAdapter
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.common.ScriptOperations
import org.autojs.autojs.ui.main.scripts.DisplayExifInfoActivity
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
import java.util.Locale

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
    private var isEnumerating = false
    private var loadGeneration = 0
    private var enumerationGeneration = 0
    private var metadataGeneration = 0
    private var metadataJob: Job? = null
    private var currentMetadata: ViewerImageMetadata? = null
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
        setToolbarAsBack("")
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.title = null
        binding.toolbar.subtitle = null
        binding.image.onPageSwipe = { direction -> showRelative(direction) }
        binding.imageExifInfo.setOnClickListener { showCurrentExifInfo() }
        val restoredFile = savedInstanceState?.getString(STATE_CURRENT_PATH)
            ?.let(::File)
            ?.let { runCatching { it.canonicalFile }.getOrNull() }
            ?.takeIf { it.isFile && it.canRead() }
        currentUri = restoredFile?.let { MediaViewerLauncher.uriForLocalFile(this, it) } ?: initialRequest.uri
        currentMimeType = initialRequest.mimeType
        currentFile = restoredFile ?: initialRequest.localPath?.let(::File)
        currentFile?.let { currentMimeType = Mime.fromFile(it.path) ?: Mime.IMAGE_WILDCARD }
        isEnumerating = currentFile != null

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
            null -> updateViewerInformation()
            else -> enumerateImages(currentFile!!)
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
        menu.findItem(R.id.action_preview_previous)?.isEnabled =
            !isEnumerating && imageFiles.isNotEmpty() && currentIndex > 0
        menu.findItem(R.id.action_preview_next)?.isEnabled =
            !isEnumerating && imageFiles.isNotEmpty() && currentIndex < imageFiles.lastIndex
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
        R.id.action_preview_previous -> true.also { showRelative(ZoomableImageView.DIRECTION_PREVIOUS) }
        R.id.action_preview_next -> true.also { showRelative(ZoomableImageView.DIRECTION_NEXT) }
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
        metadataJob?.cancel()
        loadGeneration++
        enumerationGeneration++
        metadataGeneration++
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
        isEnumerating = true
        updateViewerInformation()
        invalidateOptionsMenu()
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
                    .let { discovered ->
                        if (discovered.any { sameFile(it, selected) }) {
                            discovered
                        } else {
                            (discovered + selected).sortedWith { first, second ->
                                collator.compare(first.name, second.name)
                            }
                        }
                    }
            }
            if (generation != enumerationGeneration || currentFile?.let { !sameFile(it, selected) } != false) {
                return@launch
            }
            isEnumerating = false
            imageFiles = files
            currentIndex = files.indexOfFirst { sameFile(it, selected) }
            if (currentIndex < 0) {
                imageFiles = listOf(selected)
                currentIndex = 0
            }
            currentFile = imageFiles[currentIndex]
            updateViewerInformation()
            invalidateOptionsMenu()
        }
    }

    private fun showRelative(direction: Int) {
        if (imageFiles.isEmpty()) return
        val target = currentIndex + direction
        if (target !in imageFiles.indices) return
        val targetFile = imageFiles[target]
        val targetUri = MediaViewerLauncher.uriForLocalFile(this, targetFile) ?: return
        currentIndex = target
        currentFile = targetFile
        currentUri = targetUri
        currentMimeType = Mime.fromFile(targetFile.path) ?: Mime.IMAGE_WILDCARD
        updateViewerInformation(resetMetadata = true)
        loadCurrentImage()
        invalidateOptionsMenu()
    }

    private fun adoptLocalFile(file: File) {
        if (isFinishing || isDestroyed || !::binding.isInitialized) return
        val canonical = runCatching { file.canonicalFile }.getOrNull()
            ?.takeIf { it.isFile && it.canRead() }
            ?: return
        val canonicalUri = MediaViewerLauncher.uriForLocalFile(this, canonical) ?: return
        currentFile = canonical
        currentUri = canonicalUri
        currentMimeType = Mime.fromFile(canonical.path) ?: Mime.IMAGE_WILDCARD
        enumerationGeneration++
        imageFiles = emptyList()
        currentIndex = 0
        isEnumerating = true
        updateViewerInformation(resetMetadata = true)
        loadCurrentImage()
        enumerateImages(canonical)
    }

    private fun updateViewerInformation(resetMetadata: Boolean = false) {
        binding.imageNameInfo.text = currentFile?.name
            ?: initialRequest.displayName.ifBlank {
                currentUri?.lastPathSegment ?: getString(R.string.app_name)
            }
        binding.imagePositionInfo.text = when {
            currentFile == null -> "1/1"
            isEnumerating -> ".../..."
            imageFiles.isEmpty() -> "1/1"
            else -> "${currentIndex + 1}/${imageFiles.size}"
        }
        if (resetMetadata) {
            currentMetadata = null
            binding.imagePropertiesInfo.text = "..."
            binding.imageFormatInfo.text = "..."
            binding.imageModifiedInfo.text = "..."
            binding.imageExifInfo.text = "..."
            binding.imageExifInfo.isEnabled = false
        }
    }

    private fun loadCurrentImage() {
        if (isFinishing || isDestroyed || !::binding.isInitialized) return
        val uri = currentUri ?: return showLoadFailure()
        val generation = ++loadGeneration
        binding.image.resetZoom()
        updateViewerInformation(resetMetadata = true)
        loadCurrentMetadata()
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

    private fun loadCurrentMetadata() {
        metadataJob?.cancel()
        val generation = ++metadataGeneration
        val file = currentFile
        val uri = currentUri
        val displayName = currentFile?.name
            ?: initialRequest.displayName.ifBlank { uri?.lastPathSegment.orEmpty() }
        val mimeType = currentMimeType
        metadataJob = lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    when {
                        file != null -> readLocalMetadata(file)
                        uri != null -> readContentMetadata(uri, displayName, mimeType)
                        else -> error("Missing image source")
                    }
                }
            }
            if (generation != metadataGeneration || isFinishing || isDestroyed) return@launch
            result.onSuccess(::applyMetadata).onFailure { error ->
                if (error is CancellationException) throw error
                applyMetadata(ViewerImageMetadata.unknown(getString(R.string.text_unknown)))
            }
        }
    }

    private fun readLocalMetadata(file: File): ViewerImageMetadata {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val exif = runCatching { ExifInterface(file.path) }.getOrNull()
        val exifEntries = readExifEntries(exif)
        val format = imageFormat(bounds.outMimeType, file.extension)
        val resolution = imageResolution(bounds.outWidth, bounds.outHeight)
        val fileSize = PFiles.formatSizeWithUnit(file.length())
        val modified = PFile.getFullDateString(file.lastModified()).toString()
        return ViewerImageMetadata(
            resolution = resolution,
            fileSize = fileSize,
            format = format,
            modified = modified,
            exifInfo = buildExifInfoIfPresent(
                completeName = file.path,
                format = format,
                fileSize = fileSize,
                resolution = resolution,
                modified = modified,
                exifEntries = exifEntries,
            ),
        )
    }

    private fun readContentMetadata(
        uri: Uri,
        displayName: String,
        fallbackMimeType: String,
    ): ViewerImageMetadata {
        val resolver = contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }
        val exif = runCatching {
            resolver.openInputStream(uri)?.use { input -> ExifInterface(input) }
        }.getOrNull()
        val contentInfo = queryContentInfo(uri)
        val format = imageFormat(
            bounds.outMimeType ?: resolver.getType(uri) ?: fallbackMimeType,
            displayName.substringAfterLast('.', ""),
        )
        val resolution = imageResolution(bounds.outWidth, bounds.outHeight)
        val fileSize = contentInfo.size
            ?.takeIf { it >= 0L }
            ?.let { PFiles.formatSizeWithUnit(it) }
            ?: getString(R.string.text_unknown)
        val modified = contentInfo.modified
            ?.takeIf { it > 0L }
            ?.let { PFile.getFullDateString(it).toString() }
            ?: getString(R.string.text_unknown)
        return ViewerImageMetadata(
            resolution = resolution,
            fileSize = fileSize,
            format = format,
            modified = modified,
            exifInfo = buildExifInfoIfPresent(
                completeName = uri.toString(),
                format = format,
                fileSize = fileSize,
                resolution = resolution,
                modified = modified,
                exifEntries = readExifEntries(exif),
            ),
        )
    }

    private fun queryContentInfo(uri: Uri): ContentInfo {
        var size: Long? = null
        var modified: Long? = null
        runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use
                cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { index ->
                    if (!cursor.isNull(index)) size = cursor.getLong(index)
                }
                cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    .takeIf { it >= 0 }
                    ?.let { index ->
                        if (!cursor.isNull(index)) modified = cursor.getLong(index)
                    }
                if (modified == null) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                        .takeIf { it >= 0 }
                        ?.let { index ->
                            if (!cursor.isNull(index)) {
                                modified = cursor.getLong(index).let { value ->
                                    if (value in 1 until TIMESTAMP_MILLISECONDS_THRESHOLD) value * 1000L else value
                                }
                            }
                        }
                }
            }
        }
        return ContentInfo(size, modified)
    }

    private fun readExifEntries(exif: ExifInterface?): List<Pair<String, String>> = EXIF_TAGS.mapNotNull { tag ->
        exif?.getAttribute(tag)?.takeIf(String::isNotBlank)?.let { tag to it }
    }

    private fun buildExifInfoIfPresent(
        completeName: String,
        format: String,
        fileSize: String,
        resolution: String,
        modified: String,
        exifEntries: List<Pair<String, String>>,
    ): String? {
        // Width and height can be synthesized from the image container by ExifInterface. Do not
        // report those alone as embedded EXIF metadata.
        if (exifEntries.none { (tag, _) -> tag !in EXIF_STRUCTURAL_TAGS }) return null
        return buildString {
            appendLine("# EXIF")
            appendLine()
            appendLine("General")
            appendLine("Complete name : $completeName")
            appendLine("Format : $format")
            appendLine("File size : $fileSize")
            appendLine("Resolution : $resolution")
            appendLine("Modified : $modified")
            appendLine()
            appendLine("EXIF")
            val labelWidth = exifEntries.maxOf { (tag, _) -> tag.length }
            exifEntries.forEach { (tag, value) ->
                appendLine("${tag.padEnd(labelWidth)} : $value")
            }
        }.trimEnd()
    }

    private fun imageFormat(mimeType: String?, fallbackExtension: String): String {
        val mimeFormat = mimeType
            ?.substringAfter('/', "")
            ?.substringBefore(';')
            ?.takeIf { it.isNotBlank() && it != "*" }
        val rawFormat = mimeFormat ?: fallbackExtension.takeIf(String::isNotBlank)
        val normalizedFormat = rawFormat?.lowercase(Locale.ROOT)
        return when (normalizedFormat) {
            "jpg", "jpeg" -> "JPEG"
            "svg+xml" -> "SVG"
            null -> getString(R.string.text_unknown)
            else -> normalizedFormat.uppercase(Locale.ROOT)
        }
    }

    private fun imageResolution(width: Int, height: Int): String = when {
        width > 0 && height > 0 -> "$width x $height"
        else -> getString(R.string.text_unknown)
    }

    private fun applyMetadata(metadata: ViewerImageMetadata) {
        currentMetadata = metadata
        binding.imagePropertiesInfo.text = "${metadata.resolution}  |  ${metadata.fileSize}"
        binding.imageFormatInfo.text = metadata.format
        binding.imageModifiedInfo.text = metadata.modified
        binding.imageExifInfo.text = if (metadata.exifInfo != null) EXIF_LABEL else NO_EXIF_LABEL
        binding.imageExifInfo.isEnabled = true
    }

    private fun showCurrentExifInfo() {
        currentMetadata?.exifInfo?.let { info ->
            DisplayExifInfoActivity.launch(this, info)
            return
        }
        ViewUtils.showToast(this, R.string.text_no_exif_information)
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
        binding.toolbar.setEditorThemeColors(background, foreground)
        binding.imageInfoBar.setBackgroundColor(background)
        binding.image.setBackgroundColor(background)
        listOf(
            binding.imageNameInfo,
            binding.imagePositionInfo,
            binding.imageExifInfo,
        ).forEach { it.setTextColor(foreground) }
        listOf(
            binding.imagePropertiesInfo,
            binding.imageFormatInfo,
            binding.imageModifiedInfo,
        ).forEach {
            it.setTextColor(ColorUtils.applyAlpha(foreground, 0.78))
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

    private data class ContentInfo(
        val size: Long?,
        val modified: Long?,
    )

    private data class ViewerImageMetadata(
        val resolution: String,
        val fileSize: String,
        val format: String,
        val modified: String,
        val exifInfo: String?,
    ) {
        companion object {
            fun unknown(value: String) = ViewerImageMetadata(
                resolution = value,
                fileSize = value,
                format = value,
                modified = value,
                exifInfo = null,
            )
        }
    }

    companion object {
        private const val PREF_START_FULLSCREEN = "image_viewer_start_fullscreen"
        private const val PREF_BACKGROUND_COLOR = "image_viewer_background_color"
        private const val COLOR_PICKER_TAG = "ImageViewerBackgroundColorPicker"
        private const val STATE_FULLSCREEN = "image_viewer_fullscreen"
        private const val STATE_CURRENT_PATH = "image_viewer_current_path"
        private const val EXIF_LABEL = "EXIF"
        private const val NO_EXIF_LABEL = "No EXIF"
        private const val TIMESTAMP_MILLISECONDS_THRESHOLD = 10_000_000_000L
        private val DEFAULT_BACKGROUND_COLOR = Color.parseColor("#212121")
        private val EXIF_STRUCTURAL_TAGS = setOf(
            ExifInterface.TAG_IMAGE_WIDTH,
            ExifInterface.TAG_IMAGE_LENGTH,
        )
        private val EXIF_TAGS = listOf(
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_IMAGE_WIDTH,
            ExifInterface.TAG_IMAGE_LENGTH,
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_ISO_SPEED_RATINGS,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
        )
    }
}
