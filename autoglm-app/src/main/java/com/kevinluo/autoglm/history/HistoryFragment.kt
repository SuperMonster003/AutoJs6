package com.kevinluo.autoglm.history

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kevinluo.autoglm.R
import com.kevinluo.autoglm.util.Logger
import com.kevinluo.autoglm.util.showWithPrimaryButtons
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * History Fragment for displaying task execution history.
 *
 * Shows a list of all recorded task executions with their status, duration, and step count.
 * Supports multi-select mode for batch deletion of history entries.
 *
 * Features:
 * - View all task history in a scrollable list
 * - Pull-to-refresh to reload history
 * - Tap to view task details
 * - Long press to enter multi-select mode
 * - Batch delete selected tasks
 * - Clear all history
 */
class HistoryFragment : Fragment() {
    private lateinit var historyManager: HistoryManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var adapter: HistoryListAdapter

    // Toolbars
    private lateinit var normalToolbar: LinearLayout
    private lateinit var selectionToolbar: LinearLayout
    private lateinit var selectionCountText: TextView

    // Multi-select mode
    private var isSelectionMode = false

    // Back press callback for handling selection mode exit
    private lateinit var backPressedCallback: OnBackPressedCallback

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Logger.d(TAG, "HistoryFragment created")
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        historyManager = HistoryManager.getInstance(requireContext())
        setupViews(view)
        setupBackPressedCallback()
        observeHistory()
    }

    /**
     * Sets up the back pressed callback using the OnBackPressedDispatcher API.
     */
    private fun setupBackPressedCallback() {
        backPressedCallback =
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    if (isSelectionMode) {
                        exitSelectionMode()
                    }
                }
            }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
    }

    /**
     * Sets up all view references and click listeners.
     */
    private fun setupViews(view: View) {
        // Toolbars
        normalToolbar = view.findViewById(R.id.normalToolbar)
        selectionToolbar = view.findViewById(R.id.selectionToolbar)
        selectionCountText = view.findViewById(R.id.selectionCountText)

        // Normal toolbar buttons
        view.findViewById<ImageButton>(R.id.clearAllBtn).setOnClickListener {
            showClearAllDialog()
        }

        // Selection toolbar buttons
        view.findViewById<ImageButton>(R.id.cancelSelectionBtn).setOnClickListener {
            exitSelectionMode()
        }

        view.findViewById<ImageButton>(R.id.selectAllBtn).setOnClickListener {
            adapter.selectAll()
        }

        view.findViewById<ImageButton>(R.id.deleteSelectedBtn).setOnClickListener {
            showDeleteSelectedDialog()
        }

        // SwipeRefreshLayout
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.primary))
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(requireContext(), R.color.surface_variant),
        )
        swipeRefreshLayout.setOnRefreshListener {
            refreshHistory()
        }

        // RecyclerView and empty state
        recyclerView = view.findViewById(R.id.historyRecyclerView)
        emptyState = view.findViewById(R.id.emptyState)

        adapter =
            HistoryListAdapter(
                onItemClick = { task ->
                    if (isSelectionMode) {
                        adapter.toggleSelection(task.id)
                    } else {
                        openTaskDetail(task)
                    }
                },
                onItemLongClick = { task ->
                    if (!isSelectionMode) {
                        enterSelectionMode()
                        adapter.toggleSelection(task.id)
                    }
                },
                onSelectionChanged = { count ->
                    updateSelectionCount(count)
                    if (count == 0 && isSelectionMode) {
                        exitSelectionMode()
                    }
                },
            )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    /**
     * Observes the history list and updates the UI accordingly.
     */
    private fun observeHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            historyManager.historyList.collectLatest { list ->
                adapter.submitList(list)
                updateEmptyState(list.isEmpty())

                // Exit selection mode if list becomes empty
                if (list.isEmpty() && isSelectionMode) {
                    exitSelectionMode()
                }
            }
        }
    }

    /**
     * Refreshes the history list.
     */
    private fun refreshHistory() {
        // History is already observed via StateFlow, just stop the refresh indicator
        swipeRefreshLayout.isRefreshing = false
        Logger.d(TAG, "History refreshed")
    }

    /**
     * Updates the empty state visibility based on list state.
     *
     * @param isEmpty Whether the history list is empty
     */
    fun updateEmptyState(isEmpty: Boolean) {
        emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    /**
     * Enters multi-select mode for batch operations.
     */
    private fun enterSelectionMode() {
        isSelectionMode = true
        adapter.setSelectionMode(true)
        normalToolbar.visibility = View.GONE
        selectionToolbar.visibility = View.VISIBLE
        backPressedCallback.isEnabled = true
        Logger.d(TAG, "Entered selection mode")
    }

    /**
     * Exits multi-select mode and clears selection.
     */
    private fun exitSelectionMode() {
        isSelectionMode = false
        adapter.setSelectionMode(false)
        adapter.clearSelection()
        normalToolbar.visibility = View.VISIBLE
        selectionToolbar.visibility = View.GONE
        backPressedCallback.isEnabled = false
        Logger.d(TAG, "Exited selection mode")
    }

    /**
     * Updates the selection count display in the toolbar.
     *
     * @param count Number of currently selected items
     */
    private fun updateSelectionCount(count: Int) {
        selectionCountText.text = getString(R.string.history_selected_count, count)
    }

    /**
     * Opens the task detail activity for the given task.
     *
     * @param task Task history to display details for
     */
    private fun openTaskDetail(task: TaskHistory) {
        Logger.d(TAG, "Opening task detail: ${task.id}")
        val intent = Intent(requireContext(), HistoryDetailActivity::class.java)
        intent.putExtra(HistoryDetailActivity.EXTRA_TASK_ID, task.id)
        startActivity(intent)
    }

    /**
     * Shows a confirmation dialog for deleting selected tasks.
     */
    private fun showDeleteSelectedDialog() {
        val selectedIds = adapter.getSelectedIds()
        if (selectedIds.isEmpty()) return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.history_delete_selected)
            .setMessage(getString(R.string.history_delete_selected_confirm, selectedIds.size))
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    historyManager.deleteTasks(selectedIds)
                    exitSelectionMode()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .showWithPrimaryButtons()
    }

    /**
     * Shows a confirmation dialog for clearing all history.
     */
    private fun showClearAllDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.history_clear_all)
            .setMessage(R.string.history_clear_confirm)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                Logger.d(TAG, "Clearing all history")
                viewLifecycleOwner.lifecycleScope.launch {
                    historyManager.clearAllHistory()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .showWithPrimaryButtons()
    }

    companion object {
        private const val TAG = "HistoryFragment"
    }
}

/**
 * RecyclerView adapter for history list with multi-select support.
 *
 * Displays task history items with status indicators, timestamps, and step counts.
 * Supports both single-click navigation and multi-select mode for batch operations.
 *
 * @param onItemClick Callback invoked when an item is clicked
 * @param onItemLongClick Callback invoked when an item is long-pressed
 * @param onSelectionChanged Callback invoked when selection count changes
 */
class HistoryListAdapter(
    private val onItemClick: (TaskHistory) -> Unit,
    private val onItemLongClick: (TaskHistory) -> Unit,
    private val onSelectionChanged: (Int) -> Unit,
) : RecyclerView.Adapter<HistoryListAdapter.ViewHolder>() {
    private var items: List<TaskHistory> = emptyList()
    private val selectedIds = mutableSetOf<String>()
    private var isSelectionMode = false
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    /**
     * Submits a new list of items to display.
     *
     * @param list New list of task histories
     */
    fun submitList(list: List<TaskHistory>) {
        items = list
        // Remove selected IDs that no longer exist
        selectedIds.retainAll(list.map { it.id }.toSet())
        notifyDataSetChanged()
    }

    /**
     * Enables or disables selection mode.
     *
     * @param enabled True to enable selection mode, false to disable
     */
    fun setSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        notifyDataSetChanged()
    }

    /**
     * Toggles the selection state of a task.
     *
     * @param taskId ID of the task to toggle
     */
    fun toggleSelection(taskId: String) {
        if (selectedIds.contains(taskId)) {
            selectedIds.remove(taskId)
        } else {
            selectedIds.add(taskId)
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    /**
     * Selects all items in the list.
     */
    fun selectAll() {
        selectedIds.clear()
        selectedIds.addAll(items.map { it.id })
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    /**
     * Clears all selections.
     */
    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    /**
     * Gets the set of currently selected task IDs.
     *
     * @return Immutable copy of selected task IDs
     */
    fun getSelectedIds(): Set<String> = selectedIds.toSet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.item_history_task, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    /**
     * ViewHolder for history list items.
     *
     * Displays task information including description, status, time, steps, and duration.
     */
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkBox)
        private val statusIcon: ImageView = itemView.findViewById(R.id.statusIcon)
        private val taskDescription: TextView = itemView.findViewById(R.id.taskDescription)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val stepsText: TextView = itemView.findViewById(R.id.stepsText)
        private val durationText: TextView = itemView.findViewById(R.id.durationText)

        /**
         * Binds task data to the view.
         *
         * @param task Task history to display
         */
        fun bind(task: TaskHistory) {
            taskDescription.text = task.taskDescription
            timeText.text = dateFormat.format(Date(task.startTime))
            stepsText.text = itemView.context.getString(R.string.history_steps_format, task.stepCount)
            durationText.text =
                itemView.context.getString(
                    R.string.history_duration_format,
                    formatDuration(task.duration),
                )

            if (task.success) {
                statusIcon.setImageResource(R.drawable.ic_check_circle)
                statusIcon.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.status_success),
                )
            } else {
                statusIcon.setImageResource(R.drawable.ic_error)
                statusIcon.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.status_error),
                )
            }

            // Handle selection mode
            checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            checkBox.isChecked = selectedIds.contains(task.id)

            checkBox.setOnClickListener {
                toggleSelection(task.id)
            }

            itemView.setOnClickListener { onItemClick(task) }
            itemView.setOnLongClickListener {
                onItemLongClick(task)
                true
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
    }
}
