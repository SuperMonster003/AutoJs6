package org.autojs.autojs.ui.media

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.MaterialDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityImageEditorBinding
import org.autojs.autojs6.databinding.DialogImageEditorTextBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class ImageEditorActivity : BaseActivity() {

    private enum class ToolState { NORMAL, ADJUSTMENT, CROP, BRUSH, TEXT }

    private lateinit var binding: ActivityImageEditorBinding
    private lateinit var history: EditorHistoryStore
    private val editorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = requestExit()
    }
    private var sourceFile: File? = null
    private var bitmap: Bitmap? = null
    private var toolState = ToolState.NORMAL
    private var activeAdjustment: ImageEditingEngine.Adjustment? = null
    private var brushColor = Color.RED
    private var busy = false
    private var destroyed = false
    private var currentStateId = INITIAL_STATE_ID
    private var savedStateId = INITIAL_STATE_ID
    private var stateIdCounter = INITIAL_STATE_ID
    private var colorPickerCallback: ((Int) -> Unit)? = null

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
        history = EditorHistoryStore(cacheDir)
        binding = ActivityImageEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setToolbarAsBack(sourceFile!!.name)
        binding.toolbar.setNavigationOnClickListener { requestExit() }
        binding.toolbar.subtitle = getString(R.string.text_edit)
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
        bindControls()
        loadBitmap()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_image_editor, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val idle = bitmap != null && !busy && toolState == ToolState.NORMAL
        menu.findItem(R.id.action_save)?.isEnabled = idle && currentStateId != savedStateId
        menu.findItem(R.id.action_undo)?.isEnabled = idle && history.canUndo
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> true.also { requestExit() }
        R.id.action_save -> true.also { save() }
        R.id.action_undo -> true.also { undo() }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        destroyed = true
        colorPickerCallback = null
        if (::binding.isInitialized) binding.editor.setBitmap(null)
        if (!busy) {
            bitmap?.recycle()
            bitmap = null
            if (::history.isInitialized) history.clear()
            editorScope.cancel()
        }
        super.onDestroy()
    }

    private fun bindControls() = with(binding) {
        toolCrop.setOnClickListener { beginCrop() }
        toolRotateLeft.setOnClickListener {
            commitOperation { ImageEditingEngine.transform(it, rotation = -90f) }
        }
        toolRotateRight.setOnClickListener {
            commitOperation { ImageEditingEngine.transform(it, rotation = 90f) }
        }
        toolFlipHorizontal.setOnClickListener {
            commitOperation { ImageEditingEngine.transform(it, flipX = true) }
        }
        toolFlipVertical.setOnClickListener {
            commitOperation { ImageEditingEngine.transform(it, flipY = true) }
        }
        toolBrightness.setOnClickListener {
            beginAdjustment(ImageEditingEngine.Adjustment.BRIGHTNESS, R.string.image_editor_brightness)
        }
        toolContrast.setOnClickListener {
            beginAdjustment(ImageEditingEngine.Adjustment.CONTRAST, R.string.image_editor_contrast)
        }
        toolSaturation.setOnClickListener {
            beginAdjustment(ImageEditingEngine.Adjustment.SATURATION, R.string.image_editor_saturation)
        }
        toolTemperature.setOnClickListener {
            beginAdjustment(ImageEditingEngine.Adjustment.TEMPERATURE, R.string.image_editor_temperature)
        }
        toolBrush.setOnClickListener { beginBrush() }
        toolText.setOnClickListener { showTextDialog() }

        adjustmentSeekBar.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (toolState != ToolState.ADJUSTMENT) return
                val value = progress - ADJUSTMENT_CENTER
                adjustmentValue.text = if (value > 0) "+$value" else value.toString()
                activeAdjustment?.let { editor.setPreviewColorMatrix(ImageEditingEngine.colorMatrix(it, value)) }
            }
        })
        adjustmentButtons.buttonCancel.setOnClickListener { cancelActiveTool() }
        adjustmentButtons.buttonConfirm.setOnClickListener { applyAdjustment() }
        interactionButtons.buttonCancel.setOnClickListener { cancelActiveTool() }
        interactionButtons.buttonConfirm.setOnClickListener { applyInteractiveTool() }
        brushColor.setOnClickListener {
            showColorPicker(this@ImageEditorActivity.brushColor) { selected ->
                this@ImageEditorActivity.brushColor = selected
                updateColorButton(binding.brushColor, selected)
                binding.editor.updateBrush(color = selected)
            }
        }
        brushWidth.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                binding.editor.updateBrush(widthOnScreen = dp(progress + MIN_BRUSH_WIDTH_DP))
            }
        })
        brushButtons.buttonCancel.setOnClickListener { cancelActiveTool() }
        brushButtons.buttonConfirm.setOnClickListener { applyBrush() }
        updateColorButton(brushColor, this@ImageEditorActivity.brushColor)
    }

    private fun loadBitmap() {
        setBusy(true)
        editorScope.launch {
            val loaded = runCatching {
                withContext(Dispatchers.IO) { ImageBitmapIO.decodeForEditing(sourceFile!!) }
            }
            if (destroyed) {
                loaded.getOrNull()?.recycle()
                finishDestroyedWork()
                return@launch
            }
            setBusy(false)
            loaded.onSuccess { decoded ->
                bitmap = decoded
                binding.editor.setBitmap(decoded)
                binding.errorText.isVisible = false
            }.onFailure {
                binding.errorText.isVisible = true
            }
        }
    }

    private fun beginAdjustment(adjustment: ImageEditingEngine.Adjustment, titleRes: Int) {
        if (!canStartTool()) return
        toolState = ToolState.ADJUSTMENT
        activeAdjustment = adjustment
        binding.adjustmentName.setText(titleRes)
        binding.adjustmentSeekBar.progress = ADJUSTMENT_CENTER
        binding.adjustmentValue.text = "0"
        showOnlyPanel(ToolState.ADJUSTMENT)
        invalidateOptionsMenu()
    }

    private fun applyAdjustment() {
        val adjustment = activeAdjustment ?: return
        val value = binding.adjustmentSeekBar.progress - ADJUSTMENT_CENTER
        if (value == 0) {
            cancelActiveTool()
            return
        }
        finishActiveToolUi()
        commitOperation { ImageEditingEngine.applyAdjustment(it, adjustment, value) }
    }

    private fun beginCrop() {
        if (!canStartTool()) return
        toolState = ToolState.CROP
        binding.editor.beginCrop()
        binding.interactionHelp.setText(R.string.image_editor_crop_help)
        showOnlyPanel(ToolState.CROP)
        invalidateOptionsMenu()
    }

    private fun beginBrush() {
        if (!canStartTool()) return
        toolState = ToolState.BRUSH
        val width = binding.brushWidth.progress + MIN_BRUSH_WIDTH_DP
        binding.editor.beginBrush(brushColor, dp(width))
        showOnlyPanel(ToolState.BRUSH)
        invalidateOptionsMenu()
    }

    private fun applyBrush() {
        val strokes = binding.editor.brushStrokes()
        if (strokes.isEmpty()) {
            cancelActiveTool()
            return
        }
        finishActiveToolUi()
        commitOperation { ImageEditingEngine.applyBrush(it, strokes) }
    }

    private fun showTextDialog() {
        val current = bitmap ?: return
        if (!canStartTool()) return
        val dialogBinding = DialogImageEditorTextBinding.inflate(layoutInflater)
        var selectedColor = Color.WHITE
        fun updateSizeLabel(progress: Int) {
            dialogBinding.textSizeLabel.text = getString(
                R.string.image_editor_text_size_percent,
                progress + MIN_TEXT_PERCENT,
            )
        }
        updateSizeLabel(dialogBinding.textSize.progress)
        updateColorButton(dialogBinding.textColor, selectedColor)
        dialogBinding.textSize.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateSizeLabel(progress)
            }
        })
        dialogBinding.textColor.setOnClickListener {
            showColorPicker(selectedColor) { color ->
                selectedColor = color
                updateColorButton(dialogBinding.textColor, color)
            }
        }
        MaterialDialog.Builder(this)
            .title(R.string.image_editor_text)
            .customView(dialogBinding.root, false)
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.dialog_button_confirm)
            .autoDismiss(false)
            .onNegative { prompt, _ -> prompt.dismiss() }
            .onPositive { prompt, _ ->
                val text = dialogBinding.textContent.text?.toString().orEmpty().trim()
                if (text.isEmpty()) {
                    ViewUtils.showToast(this, R.string.text_should_not_be_empty, true)
                    return@onPositive
                }
                val percent = dialogBinding.textSize.progress + MIN_TEXT_PERCENT
                prompt.dismiss()
                toolState = ToolState.TEXT
                binding.editor.beginText(
                    ImageEditingEngine.TextSpec(
                        text = text,
                        color = selectedColor,
                        size = min(current.width, current.height) * percent / 100f,
                        centerX = current.width / 2f,
                        centerY = current.height / 2f,
                    ),
                )
                binding.interactionHelp.setText(R.string.image_editor_text_position_help)
                showOnlyPanel(ToolState.TEXT)
                invalidateOptionsMenu()
            }
            .show()
    }

    private fun applyInteractiveTool() {
        when (toolState) {
            ToolState.CROP -> {
                val selection = binding.editor.cropSelection() ?: return
                finishActiveToolUi()
                commitOperation { ImageEditingEngine.crop(it, selection) }
            }
            ToolState.TEXT -> {
                val spec = binding.editor.textSpec() ?: return
                finishActiveToolUi()
                commitOperation { ImageEditingEngine.addText(it, spec) }
            }
            else -> Unit
        }
    }

    private fun cancelActiveTool() {
        if (busy || toolState == ToolState.NORMAL) return
        finishActiveToolUi()
        invalidateOptionsMenu()
    }

    private fun finishActiveToolUi() {
        binding.editor.clearToolState()
        activeAdjustment = null
        toolState = ToolState.NORMAL
        showOnlyPanel(ToolState.NORMAL)
    }

    private fun showOnlyPanel(state: ToolState) = with(binding) {
        toolBar.isVisible = state == ToolState.NORMAL
        adjustmentPanel.isVisible = state == ToolState.ADJUSTMENT
        interactionPanel.isVisible = state == ToolState.CROP || state == ToolState.TEXT
        brushPanel.isVisible = state == ToolState.BRUSH
    }

    private fun commitOperation(operation: (Bitmap) -> Bitmap) {
        val source = bitmap ?: return
        if (busy || toolState != ToolState.NORMAL) return
        setBusy(true)
        editorScope.launch {
            var transformed: Bitmap? = null
            var snapshotPushed = false
            val result = runCatching {
                withContext(Dispatchers.IO) { history.push(source, currentStateId) }
                snapshotPushed = true
                withContext(Dispatchers.Default) { operation(source) }.also { transformed = it }
            }
            if (destroyed) {
                recycleDistinct(source, transformed)
                bitmap = null
                finishDestroyedWork()
                return@launch
            }
            setBusy(false)
            result.onSuccess { edited ->
                if (edited !== source) source.recycle()
                bitmap = edited
                currentStateId = ++stateIdCounter
                binding.editor.setBitmap(edited)
            }.onFailure {
                transformed?.takeIf { it !== source }?.recycle()
                if (snapshotPushed) history.discardLast()
                ViewUtils.showToast(this@ImageEditorActivity, R.string.text_failed, true)
            }
            invalidateOptionsMenu()
        }
    }

    private fun undo() {
        val current = bitmap ?: return
        if (busy || toolState != ToolState.NORMAL || !history.canUndo) return
        setBusy(true)
        editorScope.launch {
            val restored = runCatching { withContext(Dispatchers.IO) { history.pop() } }
            if (destroyed) {
                recycleDistinct(current, restored.getOrNull()?.bitmap)
                bitmap = null
                finishDestroyedWork()
                return@launch
            }
            setBusy(false)
            restored.onSuccess { entry ->
                current.recycle()
                bitmap = entry.bitmap
                currentStateId = entry.stateId
                binding.editor.setBitmap(entry.bitmap)
            }.onFailure {
                ViewUtils.showToast(this@ImageEditorActivity, R.string.text_failed, true)
            }
            invalidateOptionsMenu()
        }
    }

    private fun save() {
        val currentBitmap = bitmap ?: return
        val currentFile = sourceFile ?: return
        if (busy || toolState != ToolState.NORMAL) return
        setBusy(true)
        editorScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { ImageBitmapIO.saveEdited(currentBitmap, currentFile) }
            }
            if (destroyed) {
                currentBitmap.recycle()
                bitmap = null
                finishDestroyedWork()
                return@launch
            }
            setBusy(false)
            result.onSuccess { saved ->
                savedStateId = currentStateId
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

    private fun requestExit() {
        when {
            busy -> Unit
            toolState != ToolState.NORMAL -> cancelActiveTool()
            currentStateId != savedStateId -> MaterialDialog.Builder(this)
                .title(R.string.text_unsaved_changes)
                .content(R.string.edit_exit_without_save_warn)
                .negativeText(R.string.dialog_button_cancel)
                .positiveText(R.string.dialog_button_discard_changes)
                .onPositive { _, _ -> finishWithoutCallback() }
                .show()
            else -> finishWithoutCallback()
        }
    }

    private fun finishWithoutCallback() {
        backPressedCallback.isEnabled = false
        onBackPressedDispatcher.onBackPressed()
    }

    private fun canStartTool(): Boolean = bitmap != null && !busy && toolState == ToolState.NORMAL

    private fun setBusy(value: Boolean) {
        busy = value
        if (::binding.isInitialized) {
            binding.loadingIndicator.isVisible = value
            binding.toolBar.isEnabled = !value
        }
        invalidateOptionsMenu()
    }

    private fun showColorPicker(initialColor: Int, onSelected: (Int) -> Unit) {
        colorPickerCallback = onSelected
        ColorPickerDialog.newBuilder()
            .setUseLegacyMode(false)
            .setAllowCustom(true)
            .setAllowPresets(true)
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setShowAlphaSlider(true)
            .setDialogTitle(R.string.dialog_title_color_palette)
            .setColor(initialColor)
            .create()
            .setColorPickerDialogListener(object : ColorPickerDialogListener {
                override fun onColorSelected(dialogId: Int, color: Int) {
                    colorPickerCallback?.invoke(color)
                    colorPickerCallback = null
                }

                override fun onDialogDismissed(dialogId: Int) {
                    colorPickerCallback = null
                }
            })
            .show(supportFragmentManager, COLOR_PICKER_TAG)
    }

    private fun updateColorButton(button: android.widget.Button, color: Int) {
        ViewCompat.setBackgroundTintList(button, ColorStateList.valueOf(color))
        button.setTextColor(if (ColorUtils.calculateLuminance(color) > 0.5) Color.BLACK else Color.WHITE)
    }

    private fun finishDestroyedWork() {
        if (::history.isInitialized) history.clear()
        editorScope.cancel()
    }

    private fun recycleDistinct(vararg bitmaps: Bitmap?) {
        val recycled = mutableListOf<Bitmap>()
        bitmaps.filterNotNull().forEach { candidate ->
            if (recycled.none { it === candidate } && !candidate.isRecycled) {
                candidate.recycle()
                recycled += candidate
            }
        }
    }

    private fun dp(value: Int): Float = value * resources.displayMetrics.density

    private abstract class SimpleSeekBarListener : SeekBar.OnSeekBarChangeListener {
        override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
    }

    private class EditorHistoryStore(cacheDirectory: File) {

        data class Restored(val bitmap: Bitmap, val stateId: Long)

        private data class Snapshot(val file: File, val stateId: Long)

        private val snapshots = ArrayDeque<Snapshot>()
        private val directory = File(
            cacheDirectory,
            "image-editor-history-${SESSION_COUNTER.incrementAndGet()}",
        )

        val canUndo: Boolean
            get() = snapshots.isNotEmpty()

        fun push(bitmap: Bitmap, stateId: Long) {
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("Unable to create image editor history")
            }
            val file = File(directory, "${System.nanoTime()}.png")
            try {
                FileOutputStream(file).use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        throw IOException("Unable to encode image editor history")
                    }
                    output.fd.sync()
                }
                snapshots.addLast(Snapshot(file, stateId))
                trimHistory()
            } catch (error: Throwable) {
                file.delete()
                throw error
            }
        }

        fun pop(): Restored {
            val snapshot = snapshots.peekLast() ?: throw IOException("Image editor history is empty")
            val restored = BitmapFactory.decodeFile(snapshot.file.path)
                ?: throw IOException("Unable to decode image editor history")
            snapshots.removeLast()
            snapshot.file.delete()
            return Restored(restored, snapshot.stateId)
        }

        fun discardLast() {
            snapshots.pollLast()?.file?.delete()
        }

        fun clear() {
            snapshots.forEach { it.file.delete() }
            snapshots.clear()
            directory.delete()
        }

        private fun trimHistory() {
            var totalBytes = snapshots.sumOf { it.file.length() }
            while (snapshots.size > 1 && (snapshots.size > MAX_HISTORY_SIZE || totalBytes > MAX_HISTORY_BYTES)) {
                val removed = snapshots.removeFirst()
                totalBytes -= removed.file.length()
                removed.file.delete()
            }
        }

        private companion object {
            const val MAX_HISTORY_SIZE = 8
            const val MAX_HISTORY_BYTES = 192L * 1024L * 1024L
            val SESSION_COUNTER = AtomicLong()
        }
    }

    companion object {
        internal const val EXTRA_PATH = "org.autojs.autojs.extra.IMAGE_EDIT_PATH"

        private const val INITIAL_STATE_ID = 0L
        private const val ADJUSTMENT_CENTER = 100
        private const val MIN_BRUSH_WIDTH_DP = 2
        private const val MIN_TEXT_PERCENT = 2
        private const val COLOR_PICKER_TAG = "image_editor_color_picker"

        internal fun createIntent(context: Context, file: File): Intent =
            Intent(context, ImageEditorActivity::class.java).putExtra(EXTRA_PATH, file.path)

        @JvmStatic
        fun launch(context: Context, file: File): Boolean = createIntent(context, file).startSafely(context)
    }
}
