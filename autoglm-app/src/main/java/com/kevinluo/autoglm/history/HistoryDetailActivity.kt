package com.kevinluo.autoglm.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kevinluo.autoglm.BaseActivity
import com.kevinluo.autoglm.R
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity for displaying detailed task history with steps.
 *
 * Shows comprehensive information about a single task execution including:
 * - Task description and status
 * - All execution steps with thinking and actions
 * - Screenshots (original and annotated)
 * - Duration and timing information
 *
 * Features:
 * - Copy task prompt to clipboard
 * - Save task as image to gallery
 * - Share task as image
 * - Delete task
 *
 */
class HistoryDetailActivity : BaseActivity() {
    private lateinit var historyManager: HistoryManager
    private var taskId: String? = null
    private var task: TaskHistory? = null

    private lateinit var contentRecyclerView: RecyclerView
    private lateinit var detailAdapter: HistoryDetailAdapter

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history_detail)
        setupEdgeToEdgeInsets(R.id.rootLayout, applyTop = true, applyBottom = false)

        historyManager = HistoryManager.getInstance(this)
        taskId = intent.getStringExtra(EXTRA_TASK_ID)

        Logger.d(TAG, "HistoryDetailActivity created for task: $taskId")
        setupViews()
        loadTask()
    }

    /**
     * Sets up all view references and click listeners.
     */
    private fun setupViews() {
        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            finish()
        }

        findViewById<ImageButton>(R.id.copyPromptBtn).setOnClickListener {
            copyPromptToClipboard()
        }

        findViewById<ImageButton>(R.id.saveImageBtn).setOnClickListener {
            saveAsImage()
        }

        findViewById<ImageButton>(R.id.shareBtn).setOnClickListener {
            shareAsImage()
        }

        findViewById<ImageButton>(R.id.deleteBtn).setOnClickListener {
            showDeleteDialog()
        }

        // Setup RecyclerView with true recycling
        contentRecyclerView = findViewById(R.id.contentRecyclerView)
        setupEdgeToEdgeInsets(contentRecyclerView, applyTop = false, applyBottom = true)
        detailAdapter = HistoryDetailAdapter(historyManager, lifecycleScope)
        contentRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@HistoryDetailActivity)
            adapter = detailAdapter
            setHasFixedSize(false)
            // Enable view caching for smoother scrolling
            setItemViewCacheSize(3)
        }
    }

    /**
     * Loads the task from history manager.
     */
    private fun loadTask() {
        val id = taskId ?: return

        lifecycleScope.launch {
            task = historyManager.getTask(id)
            task?.let {
                Logger.d(TAG, "Loaded task with ${it.stepCount} steps")
                detailAdapter.setTask(it)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detailAdapter.cleanup()
    }

    /**
     * Shows a confirmation dialog for deleting the task.
     */
    private fun showDeleteDialog() {
        AlertDialog
            .Builder(this)
            .setMessage(R.string.history_delete_confirm)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                deleteTask()
            }.setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /**
     * Copies the task prompt/description to clipboard.
     *
     */
    private fun copyPromptToClipboard() {
        val currentTask = task ?: return

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AutoGLM Prompt", currentTask.taskDescription)
        clipboard.setPrimaryClip(clip)

        Logger.d(TAG, "Copied prompt to clipboard")
        Toast.makeText(this, R.string.history_prompt_copied, Toast.LENGTH_SHORT).show()
    }

    /**
     * Deletes the current task from history.
     */
    private fun deleteTask() {
        val id = taskId ?: return
        Logger.d(TAG, "Deleting task: $id")
        lifecycleScope.launch {
            historyManager.deleteTask(id)
            finish()
        }
    }

    /**
     * Formats duration in milliseconds to a human-readable string.
     *
     * @param ms Duration in milliseconds
     * @return Formatted duration string (e.g., "30秒", "2分15秒")
     */
    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        return when {
            seconds < 60 -> "${seconds}秒"
            seconds < 3600 -> "${seconds / 60}分${seconds % 60}秒"
            else -> "${seconds / 3600}时${(seconds % 3600) / 60}分"
        }
    }

    /**
     * Saves the task history as an image to gallery.
     *
     * Generates a visual representation of the task and saves it to the device's gallery.
     *
     */
    private fun saveAsImage() {
        val currentTask = task ?: return

        Logger.d(TAG, "Saving task as image")
        Toast.makeText(this, R.string.history_generating_image, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val bitmap =
                    withContext(Dispatchers.Default) {
                        generateShareImage(currentTask)
                    }

                val saved =
                    withContext(Dispatchers.IO) {
                        saveBitmapToGallery(bitmap)
                    }

                bitmap.recycle()

                if (saved) {
                    Logger.d(TAG, "Image saved to gallery")
                    Toast.makeText(this@HistoryDetailActivity, R.string.history_save_success, Toast.LENGTH_SHORT).show()
                } else {
                    Logger.e(TAG, "Failed to save image to gallery")
                    Toast.makeText(this@HistoryDetailActivity, R.string.history_save_failed, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Error saving image", e)
                Toast.makeText(this@HistoryDetailActivity, R.string.history_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Shares the task history as an image.
     *
     * Generates a visual representation of the task and opens the system share sheet.
     *
     */
    private fun shareAsImage() {
        val currentTask = task ?: return

        Logger.d(TAG, "Sharing task as image")
        Toast.makeText(this, R.string.history_generating_image, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val bitmap =
                    withContext(Dispatchers.Default) {
                        generateShareImage(currentTask)
                    }

                // Save bitmap to cache directory
                val file =
                    withContext(Dispatchers.IO) {
                        saveBitmapToCache(bitmap)
                    }

                // Share the image
                shareImageFile(file)

                // Recycle bitmap
                bitmap.recycle()
            } catch (e: Exception) {
                Logger.e(TAG, "Error sharing image", e)
                Toast.makeText(this@HistoryDetailActivity, R.string.history_share_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Generates a share image from task history.
     *
     * Creates a bitmap containing the task description, status, and all steps with screenshots.
     *
     * @param task Task history to render
     * @return Generated bitmap image
     */
    private suspend fun generateShareImage(task: TaskHistory): Bitmap {
        val width = 1080
        val padding = 40
        val contentWidth = width - padding * 2
        val stepSpacing = 40 // Spacing between steps
        val screenshotWidthRatio = 0.8f // Screenshot width = 80% of image width

        // Calculate heights
        val headerHeight = 200
        val stepBaseHeight = 120
        val thinkingLineHeight = 50

        // First pass: calculate total height (need to load screenshots to get their heights)
        var totalHeight = headerHeight + padding * 2
        val screenshotHeights = mutableMapOf<Int, Int>()

        for ((index, step) in task.steps.withIndex()) {
            totalHeight += stepBaseHeight
            if (step.thinking.isNotBlank()) {
                val lines = (step.thinking.length / 40) + 1
                totalHeight += lines * thinkingLineHeight
            }
            val screenshotPath = step.annotatedScreenshotPath ?: step.screenshotPath
            if (screenshotPath != null) {
                val bitmap = historyManager.getScreenshotBitmap(screenshotPath)
                if (bitmap != null) {
                    val targetWidth = (width * screenshotWidthRatio).toInt()
                    val scale = targetWidth.toFloat() / bitmap.width
                    val scaledHeight = (bitmap.height * scale).toInt()
                    screenshotHeights[index] = scaledHeight
                    totalHeight += scaledHeight + 40
                    bitmap.recycle()
                }
            }
            totalHeight += stepSpacing
        }
        totalHeight += 80 // footer

        // Create bitmap
        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(Color.parseColor("#1A1A1A"))

        // Paints
        val titlePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 48f
                typeface = Typeface.DEFAULT_BOLD
            }

        val subtitlePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#AAAAAA")
                textSize = 32f
            }

        val stepNumberPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4CAF50")
                textSize = 36f
                typeface = Typeface.DEFAULT_BOLD
            }

        val actionPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 34f
            }

        val thinkingPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#888888")
                textSize = 28f
            }

        val cardPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2A2A2A")
            }

        var y = padding.toFloat()

        // Draw header
        canvas.drawText("AutoGLM 任务记录", padding.toFloat(), y + 50, titlePaint)
        y += 70

        // Task description
        val descLines = wrapText(task.taskDescription, actionPaint, contentWidth.toFloat())
        for (line in descLines) {
            canvas.drawText(line, padding.toFloat(), y + 40, actionPaint)
            y += 45
        }
        y += 20

        // Status and info
        val statusColor = if (task.success) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
        val statusPaint = Paint(subtitlePaint).apply { color = statusColor }
        val statusText = if (task.success) "✓ 成功" else "✗ 失败"
        canvas.drawText(statusText, padding.toFloat(), y + 35, statusPaint)

        val duration = formatDuration(task.duration)
        val infoStr = "${dateFormat.format(Date(task.startTime))} · ${task.stepCount}步 · $duration"
        canvas.drawText(infoStr, padding + 150f, y + 35, subtitlePaint)
        y += 60

        // Draw steps
        for ((index, step) in task.steps.withIndex()) {
            // Add spacing before each step (except first one uses smaller spacing)
            if (index == 0) {
                y += 20
            } else {
                y += stepSpacing
            }

            // Step card background
            val cardTop = y
            var cardHeight = stepBaseHeight.toFloat()
            if (step.thinking.isNotBlank()) {
                val lines = (step.thinking.length / 40) + 1
                cardHeight += lines * thinkingLineHeight
            }
            screenshotHeights[index]?.let { h ->
                cardHeight += h + 40
            }

            canvas.drawRoundRect(
                padding.toFloat(),
                cardTop,
                (width - padding).toFloat(),
                cardTop + cardHeight,
                20f,
                20f,
                cardPaint,
            )

            y += 15

            // Step number
            canvas.drawText("步骤 ${step.stepNumber}", padding + 20f, y + 40, stepNumberPaint)

            // Status indicator
            val stepStatusPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (step.success) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
                }
            canvas.drawCircle(width - padding - 30f, y + 25, 10f, stepStatusPaint)
            y += 50

            // Action description
            val actionLines = wrapText(step.actionDescription, actionPaint, contentWidth - 40f)
            for (line in actionLines) {
                canvas.drawText(line, padding + 20f, y + 30, actionPaint)
                y += 40
            }

            // Thinking
            if (step.thinking.isNotBlank()) {
                y += 10
                val thinkLines = wrapText("💭 ${step.thinking}", thinkingPaint, contentWidth - 40f)
                for (line in thinkLines) {
                    canvas.drawText(line, padding + 20f, y + 25, thinkingPaint)
                    y += 35
                }
            }

            // Screenshot
            val screenshotPath = step.annotatedScreenshotPath ?: step.screenshotPath
            if (screenshotPath != null) {
                y += 20
                val screenshotBitmap = historyManager.getScreenshotBitmap(screenshotPath)
                if (screenshotBitmap != null) {
                    // Scale screenshot to 80% of image width
                    val targetWidth = (width * 0.8f).toInt()
                    val scale = targetWidth.toFloat() / screenshotBitmap.width
                    val scaledWidth = targetWidth
                    val scaledHeight = (screenshotBitmap.height * scale).toInt()

                    val scaledBitmap = Bitmap.createScaledBitmap(screenshotBitmap, scaledWidth, scaledHeight, true)
                    val left = (width - scaledWidth) / 2 // Center horizontally
                    canvas.drawBitmap(scaledBitmap, left.toFloat(), y, null)

                    y += scaledHeight
                    scaledBitmap.recycle()
                    screenshotBitmap.recycle()
                }
            }

            // Move y to end of card (cardTop + cardHeight)
            y = cardTop + cardHeight
        }

        // Footer
        y += 30
        val footerPaint = Paint(subtitlePaint).apply { textSize = 24f }
        canvas.drawText("由 AutoGLM For Android 生成", padding.toFloat(), y + 30, footerPaint)

        return bitmap
    }

    /**
     * Wraps text to fit within a given width.
     *
     * @param text Text to wrap
     * @param paint Paint used for measuring text width
     * @param maxWidth Maximum width in pixels
     * @return List of wrapped text lines
     */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            val count = paint.breakText(remaining, true, maxWidth, null)
            if (count == 0) break

            // Try to break at word boundary
            var breakAt = count
            if (count < remaining.length) {
                val lastSpace = remaining.substring(0, count).lastIndexOf(' ')
                if (lastSpace > count / 2) {
                    breakAt = lastSpace + 1
                }
            }

            lines.add(remaining.substring(0, breakAt).trim())
            remaining = remaining.substring(breakAt).trim()
        }

        return lines.ifEmpty { listOf("") }
    }

    /**
     * Saves bitmap to cache directory.
     *
     * @param bitmap Bitmap to save
     * @return File reference to the saved image
     */
    private fun saveBitmapToCache(bitmap: Bitmap): File {
        val cacheDir = File(cacheDir, "share")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val file = File(cacheDir, "autoglm_task_${System.currentTimeMillis()}.webp")
        FileOutputStream(file).use { out ->
            @Suppress("DEPRECATION")
            val format =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    Bitmap.CompressFormat.WEBP
                }
            bitmap.compress(format, 90, out)
        }
        return file
    }

    /**
     * Shares the image file using system share sheet.
     *
     * @param file Image file to share
     */
    private fun shareImageFile(file: File) {
        val uri =
            FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file,
            )

        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "image/webp"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        startActivity(Intent.createChooser(intent, getString(R.string.history_share_title)))
    }

    /**
     * Saves bitmap to device gallery.
     *
     * Uses MediaStore API on Android 10+ and legacy storage on older versions.
     *
     * @param bitmap Bitmap to save
     * @return True if save was successful, false otherwise
     */
    private fun saveBitmapToGallery(bitmap: Bitmap): Boolean {
        val filename = "AutoGLM_${System.currentTimeMillis()}.webp"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ use MediaStore
            val contentValues =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/webp")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AutoGLM")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                resolver.openOutputStream(it)?.use { out ->
                    @Suppress("DEPRECATION")
                    val format =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSY
                        } else {
                            Bitmap.CompressFormat.WEBP
                        }
                    bitmap.compress(format, 90, out)
                }

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(it, contentValues, null, null)
                true
            } ?: false
        } else {
            // Legacy storage
            @Suppress("DEPRECATION")
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val autoglmDir = File(picturesDir, "AutoGLM")
            if (!autoglmDir.exists()) autoglmDir.mkdirs()

            val file = File(autoglmDir, filename)
            FileOutputStream(file).use { out ->
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, 90, out)
            }

            // Notify gallery using MediaScannerConnection
            MediaScannerConnection.scanFile(
                this,
                arrayOf(file.absolutePath),
                arrayOf("image/webp"),
                null,
            )

            true
        }
    }

    companion object {
        private const val TAG = "HistoryDetailActivity"

        /** Intent extra key for task ID. */
        const val EXTRA_TASK_ID = "task_id"
    }
}
