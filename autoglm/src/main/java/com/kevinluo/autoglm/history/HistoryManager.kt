package com.kevinluo.autoglm.history

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import com.kevinluo.autoglm.action.AgentAction
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Manages task execution history storage and retrieval.
 *
 * This singleton class handles all operations related to task history including:
 * - Recording task execution steps with screenshots
 * - Persisting history to local storage
 * - Loading and querying historical tasks
 * - Managing history lifecycle (creation, deletion, cleanup)
 *
 * Usage example:
 * ```kotlin
 * val historyManager = HistoryManager.getInstance(context)
 * val task = historyManager.startTask("Open Settings app")
 * historyManager.recordStep(1, "Thinking...", action, "Tap on Settings", true)
 * historyManager.completeTask(true, "Task completed successfully")
 * ```
 *
 */
class HistoryManager private constructor(private val context: Context) {
    /** Directory for storing task history files. */
    private val historyDir: File by lazy {
        File(context.filesDir, HISTORY_DIR).also { it.mkdirs() }
    }

    /** Currently recording task, null if no task is being recorded. */
    private var currentTask: TaskHistory? = null

    /** Base64-encoded screenshot data for the current step. */
    private var currentScreenshotBase64: String? = null

    /** Width of the current screenshot in pixels. */
    private var currentScreenshotWidth: Int = 0

    /** Height of the current screenshot in pixels. */
    private var currentScreenshotHeight: Int = 0

    private val _historyList = MutableStateFlow<List<TaskHistory>>(emptyList())

    /** Observable list of all task histories, sorted by most recent first. */
    val historyList: StateFlow<List<TaskHistory>> = _historyList.asStateFlow()

    init {
        loadHistoryIndex()
    }

    /**
     * Starts recording a new task.
     *
     * Creates a new [TaskHistory] instance and sets it as the current task being recorded.
     * All subsequent calls to [recordStep] will add steps to this task until [completeTask] is called.
     *
     * @param taskDescription Human-readable description of the task being executed
     * @return The newly created [TaskHistory] instance
     *
     */
    fun startTask(taskDescription: String): TaskHistory {
        val task = TaskHistory(taskDescription = taskDescription)
        currentTask = task
        Logger.d(TAG, "Started recording task: ${task.id}")
        return task
    }

    /**
     * Sets the current screenshot for the next step.
     *
     * The screenshot data will be used when [recordStep] is called to save
     * both the original and annotated versions of the screenshot.
     *
     * @param base64Data Base64-encoded screenshot image data (WebP format)
     * @param width Screenshot width in pixels
     * @param height Screenshot height in pixels
     *
     */
    fun setCurrentScreenshot(base64Data: String, width: Int, height: Int) {
        currentScreenshotBase64 = base64Data
        currentScreenshotWidth = width
        currentScreenshotHeight = height
    }

    /**
     * Records a step in the current task.
     *
     * Saves the step information including thinking, action, and screenshot to the current task.
     * If a screenshot is available (set via [setCurrentScreenshot]), it will be saved to disk
     * and optionally annotated with action visualization.
     *
     * @param stepNumber Sequential step number within the task
     * @param thinking Model's reasoning/thinking for this step
     * @param action The agent action executed, or null if no action
     * @param actionDescription Human-readable description of the action
     * @param success Whether the step executed successfully
     * @param message Optional additional message or error details
     *
     */
    suspend fun recordStep(
        stepNumber: Int,
        thinking: String,
        action: AgentAction?,
        actionDescription: String,
        success: Boolean,
        message: String? = null,
    ) = withContext(Dispatchers.IO) {
        val task = currentTask ?: return@withContext

        var screenshotPath: String? = null
        var annotatedPath: String? = null

        // Save screenshot if available
        currentScreenshotBase64?.let { base64 ->
            try {
                // Decode base64 to raw bytes (already WebP format)
                val webpBytes = Base64.decode(base64, Base64.DEFAULT)

                // Save original screenshot directly without re-compression
                screenshotPath = saveScreenshotBytes(task.id, stepNumber, webpBytes, false)

                // Create and save annotated screenshot if action has visual annotation
                if (action != null) {
                    val annotation =
                        ScreenshotAnnotator.createAnnotation(
                            action,
                            currentScreenshotWidth,
                            currentScreenshotHeight,
                        )
                    if (annotation !is ActionAnnotation.None) {
                        // Only decode bitmap when we need to annotate
                        val bitmap = BitmapFactory.decodeByteArray(webpBytes, 0, webpBytes.size)
                        if (bitmap != null) {
                            // Calculate scaled density based on screenshot size vs typical screen size
                            // This ensures annotations look proportional on scaled screenshots
                            val baseDensity = context.resources.displayMetrics.density
                            val scaleFactor = bitmap.width.toFloat() / context.resources.displayMetrics.widthPixels
                            val scaledDensity = baseDensity * scaleFactor
                            val annotatedBitmap = ScreenshotAnnotator.annotate(bitmap, annotation, scaledDensity)
                            annotatedPath = saveScreenshotBitmap(task.id, stepNumber, annotatedBitmap, true)
                            annotatedBitmap.recycle()
                            bitmap.recycle()
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to save screenshot for step $stepNumber", e)
            }
        }

        val step =
            HistoryStep(
                stepNumber = stepNumber,
                thinking = thinking,
                action = action,
                actionDescription = actionDescription,
                screenshotPath = screenshotPath,
                annotatedScreenshotPath = annotatedPath,
                success = success,
                message = message,
            )

        task.steps.add(step)
        Logger.d(TAG, "Recorded step $stepNumber for task ${task.id}")

        // Clear current screenshot
        currentScreenshotBase64 = null
    }

    /**
     * Completes the current task recording.
     *
     * Finalizes the task with success/failure status and saves it to persistent storage.
     * Empty tasks (no steps recorded) are discarded. The history list is updated and
     * old entries are trimmed if the maximum count is exceeded.
     *
     * @param success Whether the task completed successfully
     * @param message Optional completion message or error description
     *
     */
    suspend fun completeTask(success: Boolean, message: String?) = withContext(Dispatchers.IO) {
        val task = currentTask ?: return@withContext

        // Don't save empty tasks (no steps recorded)
        if (task.steps.isEmpty()) {
            Logger.d(TAG, "Skipping empty task ${task.id}")
            currentTask = null
            return@withContext
        }

        task.endTime = System.currentTimeMillis()
        task.success = success
        task.completionMessage = message

        // Save task to disk
        saveTask(task)

        // Update history list
        val updatedList = _historyList.value.toMutableList()
        updatedList.add(0, task)

        // Trim old history if needed
        while (updatedList.size > MAX_HISTORY_COUNT) {
            val removed = updatedList.removeAt(updatedList.size - 1)
            deleteTaskFiles(removed.id)
        }

        _historyList.value = updatedList
        saveHistoryIndex()

        Logger.d(TAG, "Completed task ${task.id}, success=$success")
        currentTask = null
    }

    /**
     * Gets a task history by ID.
     *
     * Loads the complete task history including all steps from persistent storage.
     *
     * @param taskId Unique identifier of the task to retrieve
     * @return The [TaskHistory] if found, null otherwise
     *
     */
    suspend fun getTask(taskId: String): TaskHistory? = withContext(Dispatchers.IO) {
        loadTask(taskId)
    }

    /**
     * Deletes a task history.
     *
     * Removes the task and all associated files (screenshots) from storage.
     *
     * @param taskId Unique identifier of the task to delete
     *
     */
    suspend fun deleteTask(taskId: String) = withContext(Dispatchers.IO) {
        deleteTaskFiles(taskId)
        _historyList.value = _historyList.value.filter { it.id != taskId }
        saveHistoryIndex()
    }

    /**
     * Deletes multiple task histories.
     *
     * Batch deletion of tasks and their associated files.
     *
     * @param taskIds Set of unique identifiers of tasks to delete
     *
     */
    suspend fun deleteTasks(taskIds: Set<String>) = withContext(Dispatchers.IO) {
        taskIds.forEach { taskId ->
            deleteTaskFiles(taskId)
        }
        _historyList.value = _historyList.value.filter { it.id !in taskIds }
        saveHistoryIndex()
    }

    /**
     * Clears all history.
     *
     * Removes all task histories and their associated files from storage.
     *
     */
    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        historyDir.listFiles()?.forEach { it.deleteRecursively() }
        _historyList.value = emptyList()
        saveHistoryIndex()
    }

    /**
     * Gets the screenshot bitmap for a step.
     *
     * Loads and decodes a screenshot image from the given file path.
     *
     * @param path Absolute file path to the screenshot, or null
     * @return Decoded [Bitmap] if the file exists and is valid, null otherwise
     *
     */
    fun getScreenshotBitmap(path: String?): Bitmap? {
        if (path == null) return null
        val file = File(path)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(path)
    }

    // Private helper methods

    /**
     * Saves raw WebP bytes directly to file (no re-compression).
     *
     * @param taskId Task identifier for directory organization
     * @param stepNumber Step number for filename
     * @param webpBytes Raw WebP image bytes
     * @param annotated Whether this is an annotated screenshot
     * @return Absolute file path of the saved screenshot
     */
    private fun saveScreenshotBytes(
        taskId: String,
        stepNumber: Int,
        webpBytes: ByteArray,
        annotated: Boolean,
    ): String {
        val taskDir = File(historyDir, taskId).also { it.mkdirs() }
        val suffix = if (annotated) "_annotated" else ""
        val file = File(taskDir, "step_${stepNumber}$suffix.webp")

        FileOutputStream(file).use { out ->
            out.write(webpBytes)
        }

        return file.absolutePath
    }

    /**
     * Saves bitmap as WebP (used for annotated screenshots).
     *
     * @param taskId Task identifier for directory organization
     * @param stepNumber Step number for filename
     * @param bitmap Bitmap to save
     * @param annotated Whether this is an annotated screenshot
     * @return Absolute file path of the saved screenshot
     */
    private fun saveScreenshotBitmap(taskId: String, stepNumber: Int, bitmap: Bitmap, annotated: Boolean): String {
        val taskDir = File(historyDir, taskId).also { it.mkdirs() }
        val suffix = if (annotated) "_annotated" else ""
        val file = File(taskDir, "step_${stepNumber}$suffix.webp")

        FileOutputStream(file).use { out ->
            @Suppress("DEPRECATION")
            val format =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    Bitmap.CompressFormat.WEBP
                }
            bitmap.compress(format, 85, out)
        }

        return file.absolutePath
    }

    /**
     * Saves a task's metadata to JSON file.
     *
     * @param task Task history to save
     */
    private fun saveTask(task: TaskHistory) {
        val taskDir = File(historyDir, task.id).also { it.mkdirs() }
        val metaFile = File(taskDir, "meta.json")

        val json =
            JSONObject().apply {
                put("id", task.id)
                put("taskDescription", task.taskDescription)
                put("startTime", task.startTime)
                put("endTime", task.endTime)
                put("success", task.success)
                put("completionMessage", task.completionMessage)

                val stepsArray = JSONArray()
                task.steps.forEach { step ->
                    stepsArray.put(
                        JSONObject().apply {
                            put("stepNumber", step.stepNumber)
                            put("timestamp", step.timestamp)
                            put("thinking", step.thinking)
                            put("actionDescription", step.actionDescription)
                            put("screenshotPath", step.screenshotPath)
                            put("annotatedScreenshotPath", step.annotatedScreenshotPath)
                            put("success", step.success)
                            put("message", step.message)
                        },
                    )
                }
                put("steps", stepsArray)
            }

        metaFile.writeText(json.toString(2))
    }

    /**
     * Loads a task from its JSON metadata file.
     *
     * @param taskId Task identifier to load
     * @return Loaded TaskHistory, or null if not found or invalid
     */
    private fun loadTask(taskId: String): TaskHistory? {
        val metaFile = File(historyDir, "$taskId/meta.json")
        if (!metaFile.exists()) return null

        return try {
            val json = JSONObject(metaFile.readText())
            val steps = mutableListOf<HistoryStep>()

            val stepsArray = json.optJSONArray("steps")
            if (stepsArray != null) {
                for (i in 0 until stepsArray.length()) {
                    val stepJson = stepsArray.getJSONObject(i)
                    steps.add(
                        HistoryStep(
                            stepNumber = stepJson.getInt("stepNumber"),
                            timestamp = stepJson.getLong("timestamp"),
                            thinking = stepJson.getString("thinking"),
                            // Action is not serialized
                            action = null,
                            actionDescription = stepJson.getString("actionDescription"),
                            screenshotPath = stepJson.optString("screenshotPath").takeIf { it.isNotEmpty() },
                            annotatedScreenshotPath =
                            stepJson
                                .optString("annotatedScreenshotPath")
                                .takeIf { it.isNotEmpty() },
                            success = stepJson.getBoolean("success"),
                            message = stepJson.optString("message").takeIf { it.isNotEmpty() },
                        ),
                    )
                }
            }

            TaskHistory(
                id = json.getString("id"),
                taskDescription = json.getString("taskDescription"),
                startTime = json.getLong("startTime"),
                endTime = json.optLong("endTime"),
                success = json.getBoolean("success"),
                completionMessage = json.optString("completionMessage").takeIf { it.isNotEmpty() },
                steps = steps,
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load task $taskId", e)
            null
        }
    }

    /**
     * Deletes all files associated with a task.
     *
     * @param taskId Task identifier whose files should be deleted
     */
    private fun deleteTaskFiles(taskId: String) {
        File(historyDir, taskId).deleteRecursively()
    }

    /**
     * Loads the history index from persistent storage.
     *
     * Populates [_historyList] with all saved task histories.
     */
    private fun loadHistoryIndex() {
        val indexFile = File(historyDir, INDEX_FILE)
        if (!indexFile.exists()) return

        try {
            val json = JSONArray(indexFile.readText())
            val list = mutableListOf<TaskHistory>()

            for (i in 0 until json.length()) {
                val taskId = json.getString(i)
                loadTask(taskId)?.let { list.add(it) }
            }

            _historyList.value = list
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load history index", e)
        }
    }

    /**
     * Saves the history index to persistent storage.
     *
     * Writes the list of task IDs to the index file for quick loading on startup.
     */
    private fun saveHistoryIndex() {
        val indexFile = File(historyDir, INDEX_FILE)
        val json = JSONArray()
        _historyList.value.forEach { json.put(it.id) }
        indexFile.writeText(json.toString())
    }

    companion object {
        private const val TAG = "HistoryManager"
        private const val HISTORY_DIR = "task_history"
        private const val INDEX_FILE = "history_index.json"
        private const val MAX_HISTORY_COUNT = 50

        @Volatile
        private var instance: HistoryManager? = null

        /**
         * Gets the singleton instance of HistoryManager.
         *
         * @param context Android context, application context will be used
         * @return The singleton HistoryManager instance
         */
        fun getInstance(context: Context): HistoryManager = instance ?: synchronized(this) {
            instance ?: HistoryManager(context.applicationContext).also { instance = it }
        }
    }
}
