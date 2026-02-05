package com.kevinluo.autoglm.history

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.kevinluo.autoglm.R
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter for history detail with header and steps.
 *
 * Uses view type to handle header and step items differently.
 * Efficiently loads screenshots asynchronously and caches them for smooth scrolling.
 *
 * @param historyManager Manager for loading screenshots
 * @param coroutineScope Scope for launching async operations
 *
 */
class HistoryDetailAdapter(private val historyManager: HistoryManager, private val coroutineScope: CoroutineScope) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var task: TaskHistory? = null
    private val loadedBitmaps = mutableMapOf<String, Bitmap>()
    private val loadingJobs = mutableMapOf<Int, Job>()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * Sets the task to display.
     *
     * @param task Task history to display
     */
    fun setTask(task: TaskHistory) {
        this.task = task
        Logger.d(TAG, "Set task with ${task.stepCount} steps")
        notifyDataSetChanged()
    }

    /**
     * Cleans up resources including cached bitmaps and pending jobs.
     *
     * Should be called when the adapter is no longer needed.
     */
    fun cleanup() {
        loadingJobs.values.forEach { it.cancel() }
        loadingJobs.clear()
        loadedBitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
        loadedBitmaps.clear()
        Logger.d(TAG, "Cleaned up adapter resources")
    }

    override fun getItemViewType(position: Int): Int = if (position == 0) TYPE_HEADER else TYPE_STEP

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.item_history_header, parent, false)
                HeaderViewHolder(view)
            }

            else -> {
                val view = inflater.inflate(R.layout.item_history_step, parent, false)
                StepViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val currentTask = task ?: return
        when (holder) {
            is HeaderViewHolder -> {
                holder.bind(currentTask)
            }

            is StepViewHolder -> {
                val stepIndex = position - 1 // Subtract 1 for header
                if (stepIndex < currentTask.steps.size) {
                    holder.bind(currentTask.steps[stepIndex])
                }
            }
        }
    }

    override fun getItemCount(): Int {
        val currentTask = task ?: return 0
        return 1 + currentTask.steps.size // 1 header + steps
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is StepViewHolder) {
            loadingJobs[holder.adapterPosition]?.cancel()
            loadingJobs.remove(holder.adapterPosition)
            holder.clearImage()
        }
    }

    /**
     * ViewHolder for the header section displaying task overview.
     */
    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val taskDescription: TextView = itemView.findViewById(R.id.taskDescription)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)
        private val infoText: TextView = itemView.findViewById(R.id.infoText)

        /**
         * Binds task data to the header view.
         *
         * @param task Task history to display
         */
        fun bind(task: TaskHistory) {
            taskDescription.text = task.taskDescription

            val context = itemView.context
            if (task.success) {
                statusText.text = context.getString(R.string.history_success)
                statusText.setTextColor(ContextCompat.getColor(context, R.color.status_success))
            } else {
                statusText.text = context.getString(R.string.history_failed)
                statusText.setTextColor(ContextCompat.getColor(context, R.color.status_error))
            }

            val duration = formatDuration(task.duration)
            infoText.text = "${dateFormat.format(Date(task.startTime))} · ${task.stepCount}步 · $duration"
        }

        /**
         * Formats duration in milliseconds to a human-readable string.
         *
         * @param ms Duration in milliseconds
         * @return Formatted duration string
         */
        private fun formatDuration(ms: Long): String {
            val seconds = ms / 1000
            return when {
                seconds < 60 -> "${seconds}秒"
                seconds < 3600 -> "${seconds / 60}分${seconds % 60}秒"
                else -> "${seconds / 3600}时${(seconds % 3600) / 60}分"
            }
        }
    }

    /**
     * ViewHolder for individual step items.
     *
     * Displays step number, action description, thinking, screenshot, and status.
     */
    inner class StepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val stepNumber: TextView = itemView.findViewById(R.id.stepNumber)
        private val actionDescription: TextView = itemView.findViewById(R.id.actionDescription)
        private val statusIcon: ImageView = itemView.findViewById(R.id.statusIcon)
        private val thinkingSection: LinearLayout = itemView.findViewById(R.id.thinkingSection)
        private val thinkingText: TextView = itemView.findViewById(R.id.thinkingText)
        private val screenshotSection: LinearLayout = itemView.findViewById(R.id.screenshotSection)
        private val screenshotImage: ImageView = itemView.findViewById(R.id.screenshotImage)
        private val btnOriginal: MaterialButton = itemView.findViewById(R.id.btnOriginal)
        private val btnAnnotated: MaterialButton = itemView.findViewById(R.id.btnAnnotated)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)

        private var currentStep: HistoryStep? = null

        /**
         * Binds step data to the view.
         *
         * @param step History step to display
         */
        fun bind(step: HistoryStep) {
            currentStep = step

            stepNumber.text = step.stepNumber.toString()
            actionDescription.text = step.actionDescription

            val context = itemView.context
            if (step.success) {
                statusIcon.setImageResource(R.drawable.ic_check_circle)
                statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.status_success))
            } else {
                statusIcon.setImageResource(R.drawable.ic_error)
                statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.status_error))
            }

            if (step.thinking.isNotBlank()) {
                thinkingSection.visibility = View.VISIBLE
                thinkingText.text = step.thinking
            } else {
                thinkingSection.visibility = View.GONE
            }

            if (step.screenshotPath != null || step.annotatedScreenshotPath != null) {
                screenshotSection.visibility = View.VISIBLE
                screenshotImage.setImageDrawable(null)

                val defaultPath = step.annotatedScreenshotPath ?: step.screenshotPath
                loadScreenshot(defaultPath, screenshotImage)

                val hasAnnotated = step.annotatedScreenshotPath != null
                btnAnnotated.visibility = if (hasAnnotated) View.VISIBLE else View.GONE

                btnOriginal.setOnClickListener {
                    loadScreenshot(step.screenshotPath, screenshotImage)
                    btnOriginal.alpha = 1f
                    btnAnnotated.alpha = 0.5f
                }

                btnAnnotated.setOnClickListener {
                    loadScreenshot(step.annotatedScreenshotPath, screenshotImage)
                    btnOriginal.alpha = 0.5f
                    btnAnnotated.alpha = 1f
                }

                if (hasAnnotated) {
                    btnOriginal.alpha = 0.5f
                    btnAnnotated.alpha = 1f
                } else {
                    btnOriginal.alpha = 1f
                }
            } else {
                screenshotSection.visibility = View.GONE
            }

            if (!step.message.isNullOrBlank()) {
                messageText.visibility = View.VISIBLE
                messageText.text = step.message
            } else {
                messageText.visibility = View.GONE
            }
        }

        /**
         * Clears the screenshot image to free memory.
         */
        fun clearImage() {
            screenshotImage.setImageDrawable(null)
        }

        /**
         * Loads a screenshot asynchronously and displays it.
         *
         * Uses caching to avoid reloading already-loaded images.
         *
         * @param path File path to the screenshot
         * @param imageView ImageView to display the screenshot in
         */
        private fun loadScreenshot(path: String?, imageView: ImageView) {
            if (path == null) return

            loadedBitmaps[path]?.let {
                if (!it.isRecycled) {
                    imageView.setImageBitmap(it)
                    return
                }
            }

            loadingJobs[adapterPosition]?.cancel()

            loadingJobs[adapterPosition] =
                coroutineScope.launch {
                    val bitmap =
                        withContext(Dispatchers.IO) {
                            historyManager.getScreenshotBitmap(path)
                        }
                    bitmap?.let {
                        loadedBitmaps[path] = it
                        if (currentStep?.screenshotPath == path || currentStep?.annotatedScreenshotPath == path) {
                            imageView.setImageBitmap(it)
                        }
                    }
                }
        }
    }

    companion object {
        private const val TAG = "HistoryDetailAdapter"
        private const val TYPE_HEADER = 0
        private const val TYPE_STEP = 1
    }
}
