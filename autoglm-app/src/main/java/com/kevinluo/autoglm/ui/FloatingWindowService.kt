package com.kevinluo.autoglm.ui

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.kevinluo.autoglm.MainActivity
import com.kevinluo.autoglm.R
import com.kevinluo.autoglm.action.AgentAction
import com.kevinluo.autoglm.screenshot.FloatingWindowController
import com.kevinluo.autoglm.task.TaskExecutionManager
import com.kevinluo.autoglm.task.TaskStep
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Enum representing the current status of a task in the floating window.
 *
 * Used to track and display the execution state of agent tasks.
 *
 */
enum class TaskStatus {
    /** No task is currently running. */
    IDLE,

    /** A task is actively being executed. */
    RUNNING,

    /** Task execution has been paused by the user. */
    PAUSED,

    /** Task has completed successfully. */
    COMPLETED,

    /** Task has failed due to an error. */
    FAILED,

    /** Waiting for user confirmation to proceed. */
    WAITING_CONFIRMATION,

    /** Waiting for user to take over control. */
    WAITING_TAKEOVER,
}

/**
 * Data class representing a single step in the floating window waterfall display.
 *
 * @property stepNumber The sequential number of this step in the task execution
 * @property thinking The model's reasoning/thinking text for this step
 * @property action The action being performed in this step
 *
 */
data class FloatingStep(val stepNumber: Int, val thinking: String, val action: String)

/**
 * Foreground service that manages the floating window overlay for task execution.
 *
 * This service provides a floating window interface that allows users to:
 * - Input and start new tasks
 * - View real-time task execution progress in a waterfall-style display
 * - Control task execution (pause, resume, stop)
 * - See task completion status and results
 *
 * The floating window can be dragged, minimized, and hidden/shown as needed.
 * It implements [FloatingWindowController] to allow other components to control
 * the window visibility.
 *
 */
class FloatingWindowService :
    Service(),
    FloatingWindowController {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var isAttached = AtomicBoolean(false)
    private var isMinimized = false
    private var currentStepNumber = 0
    private var currentStatus = TaskStatus.IDLE

    // Coroutine scope for UI operations - uses SupervisorJob so child failures don't cancel siblings
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Steps list for waterfall display
    private val stepsList = mutableListOf<FloatingStep>()
    private var stepsAdapter: StepsAdapter? = null

    companion object {
        private const val TAG = "FloatingWindow"

        // Window size as percentage of screen
        private const val WIDTH_PERCENT = 0.80f
        private const val HEIGHT_PERCENT = 0.50f

        @Volatile
        private var instance: FloatingWindowService? = null

        fun getInstance(): FloatingWindowService? = instance

        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun requestOverlayPermission(context: Context) {
            val intent =
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}"),
                )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        Logger.d(TAG, "Service created")
        observeTaskState()
    }

    /**
     * Observes TaskExecutionManager state flows and updates UI accordingly.
     *
     * Starts coroutines to observe taskState and steps StateFlows.
     * These coroutines are cancelled in onDestroy when serviceScope is cancelled.
     */
    private fun observeTaskState() {
        // Observe task state changes
        serviceScope.launch {
            TaskExecutionManager.taskState.collect { state ->
                Logger.d(TAG, "TaskState changed: status=${state.status}, step=${state.stepNumber}")
                updateUIFromTaskState(state)
            }
        }

        // Observe steps changes
        serviceScope.launch {
            TaskExecutionManager.steps.collect { steps ->
                Logger.d(TAG, "Steps changed: count=${steps.size}")
                updateStepsFromTaskManager(steps)
            }
        }
    }

    /**
     * Updates the UI based on TaskExecutionState from TaskExecutionManager.
     *
     * @param state The current task execution state
     */
    private fun updateUIFromTaskState(state: com.kevinluo.autoglm.task.TaskExecutionState) {
        // Update status
        if (currentStatus != state.status) {
            currentStatus = state.status
            updateStatusIndicator(state.status)
            updateUIForStatus(state.status)
        }

        // Update step number
        if (currentStepNumber != state.stepNumber) {
            currentStepNumber = state.stepNumber
            floatingView?.findViewById<TextView>(R.id.tv_step_counter)?.text =
                getString(R.string.step_counter_format, state.stepNumber)
        }

        // Update result message for completed/failed states
        if (state.status == TaskStatus.COMPLETED || state.status == TaskStatus.FAILED) {
            showResult(state.resultMessage, state.status == TaskStatus.COMPLETED)
        }
    }

    /**
     * Updates the status indicator UI elements.
     *
     * @param status The current task status
     */
    private fun updateStatusIndicator(status: TaskStatus) {
        floatingView?.let { view ->
            val statusText = view.findViewById<TextView>(R.id.tv_status)
            val indicator = view.findViewById<View>(R.id.status_indicator)

            val (textRes, colorRes) =
                when (status) {
                    TaskStatus.IDLE -> R.string.task_status_idle to R.color.status_idle
                    TaskStatus.RUNNING -> R.string.task_status_running to R.color.status_running
                    TaskStatus.PAUSED -> R.string.task_status_paused to R.color.status_paused
                    TaskStatus.COMPLETED -> R.string.task_status_completed to R.color.status_completed
                    TaskStatus.FAILED -> R.string.task_status_failed to R.color.status_failed
                    TaskStatus.WAITING_CONFIRMATION -> R.string.floating_waiting_confirm to R.color.status_waiting
                    TaskStatus.WAITING_TAKEOVER -> R.string.takeover_title to R.color.status_waiting
                }

            statusText?.text = getString(textRes)
            indicator?.let {
                val drawable =
                    (it.background as? GradientDrawable)
                        ?: GradientDrawable().also { d -> it.background = d }
                drawable.shape = GradientDrawable.OVAL
                drawable.setColor(ContextCompat.getColor(this@FloatingWindowService, colorRes))
            }
        }
    }

    /**
     * Updates the steps list from TaskExecutionManager.
     *
     * @param steps The list of task steps from TaskExecutionManager
     */
    private fun updateStepsFromTaskManager(steps: List<TaskStep>) {
        // Clear and rebuild the local steps list
        stepsList.clear()
        steps.forEach { step ->
            stepsList.add(
                FloatingStep(
                    stepNumber = step.stepNumber,
                    thinking = step.thinking,
                    action = step.action,
                ),
            )
        }
        stepsAdapter?.notifyDataSetChanged()

        // Scroll to bottom if there are steps
        if (stepsList.isNotEmpty()) {
            floatingView?.findViewById<RecyclerView>(R.id.steps_recycler_view)?.let { rv ->
                rv.scrollToPosition(stepsList.size - 1)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 不再使用前台服务，悬浮窗本身就能保持显示
        // 如果需要保活，依赖 ContinuousListeningService 的前台通知

        // Only create the window view, don't show it automatically
        // Window will be shown when show() is called explicitly
        if (floatingView == null && canDrawOverlays(this)) {
            createWindowView()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Logger.d(TAG, "Service destroying")
        instance = null
        // Cancel all coroutines
        serviceScope.cancel()
        removeWindow()
        super.onDestroy()
    }

    // ==================== FloatingWindowController ====================

    /**
     * Hides the floating window from the screen.
     *
     * Clears input focus and hides the keyboard before removing the window.
     *
     */
    override fun hide() {
        serviceScope.launch {
            Logger.d(TAG, "hide() called, isAttached=${isAttached.get()}")
            if (isAttached.get()) {
                // Clear focus and hide keyboard before hiding window
                clearInputFocus()
                removeWindowInternal()
            }
        }
    }

    /**
     * Clears input focus and hides keyboard.
     *
     * After clearing focus, adds FLAG_NOT_FOCUSABLE so back key works in other apps.
     */
    private fun clearInputFocus() {
        val taskInput = floatingView?.findViewById<EditText>(R.id.task_input) ?: return

        if (!taskInput.hasFocus()) {
            return
        }

        Logger.d(TAG, "clearInputFocus: clearing focus and hiding keyboard")

        // Hide keyboard first
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(taskInput.windowToken, 0)

        // Clear focus
        taskInput.clearFocus()

        // Add FLAG_NOT_FOCUSABLE so back key works in other apps
        layoutParams?.let { params ->
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            if (isAttached.get()) {
                try {
                    windowManager?.updateViewLayout(floatingView, params)
                    Logger.d(TAG, "clearInputFocus: added FLAG_NOT_FOCUSABLE")
                } catch (e: Exception) {
                    Logger.e(TAG, "Error updating layout after clearing focus", e)
                }
            }
        }
    }

    /**
     * Shows the floating window on the screen.
     *
     * Creates the window view if not already created, then adds it to the window manager.
     *
     */
    override fun show() {
        serviceScope.launch {
            Logger.d(TAG, "show() called, isAttached=${isAttached.get()}, floatingView=${floatingView != null}")

            // Create window view if not created yet
            if (floatingView == null) {
                createWindowView()
            }

            if (!isAttached.get() && floatingView != null) {
                addWindowInternal()
            }
        }
    }

    /**
     * Shows the floating window and brings it to the front.
     *
     * If the window is already attached, removes and re-adds it to ensure it's on top.
     *
     */
    override fun showAndBringToFront() {
        serviceScope.launch {
            val attached = isAttached.get()
            val hasView = floatingView != null
            Logger.d(TAG, "showAndBringToFront() called, isAttached=$attached, floatingView=$hasView")

            // Create window view if not created yet
            if (floatingView == null) {
                createWindowView()
            }

            if (floatingView != null) {
                if (isAttached.get()) {
                    removeWindowInternal()
                }
                addWindowInternal()
            }
        }
    }

    /**
     * Checks if the floating window is currently visible.
     *
     * @return true if the window is attached and visible, false otherwise
     *
     */
    override fun isVisible(): Boolean = isAttached.get()

    // ==================== Public Methods ====================

    /**
     * Adds a new step to the waterfall display.
     *
     * @param stepNumber The sequential number of this step
     * @param thinking The model's reasoning text for this step
     * @param action The action being performed, or null if no action
     *
     */
    fun addStep(stepNumber: Int, thinking: String, action: AgentAction?) {
        serviceScope.launch {
            val step =
                FloatingStep(
                    stepNumber = stepNumber,
                    thinking = thinking,
                    action = action?.formatForDisplay() ?: "无",
                )
            stepsList.add(step)
            stepsAdapter?.notifyItemInserted(stepsList.size - 1)

            // Scroll to bottom
            floatingView?.findViewById<RecyclerView>(R.id.steps_recycler_view)?.let { rv ->
                rv.scrollToPosition(stepsList.size - 1)
            }

            // Update step counter
            currentStepNumber = stepNumber
            floatingView?.findViewById<TextView>(R.id.tv_step_counter)?.text =
                getString(R.string.step_counter_format, stepNumber)
        }
    }

    /**
     * Updates the thinking text for the current (last) step.
     *
     * @param thinking The new thinking text to display
     *
     */
    fun updateThinking(thinking: String) {
        serviceScope.launch {
            if (stepsList.isNotEmpty()) {
                val lastIndex = stepsList.size - 1
                stepsList[lastIndex] = stepsList[lastIndex].copy(thinking = thinking)
                stepsAdapter?.notifyItemChanged(lastIndex)
            }
        }
    }

    /**
     * Updates the action for the current (last) step.
     *
     * @param action The new action to display
     *
     */
    fun updateAction(action: AgentAction) {
        serviceScope.launch {
            if (stepsList.isNotEmpty()) {
                val lastIndex = stepsList.size - 1
                stepsList[lastIndex] = stepsList[lastIndex].copy(action = action.formatForDisplay())
                stepsAdapter?.notifyItemChanged(lastIndex)
            }
        }
    }

    /**
     * Updates the task status and refreshes the UI accordingly.
     *
     * This method is private to ensure all status changes go through StateFlow.
     * Only used internally by showConfirmation/showTakeOver/showInteract for temporary states.
     *
     * @param status The new task status to display
     */
    private fun updateStatus(status: TaskStatus) {
        Logger.d(TAG, "updateStatus called with status: $status")
        serviceScope.launch {
            Logger.d(TAG, "updateStatus serviceScope.launch executing, status: $status, floatingView: $floatingView")
            currentStatus = status

            // If switching to RUNNING, clear previous steps
            if (status == TaskStatus.RUNNING) {
                stepsList.clear()
                stepsAdapter?.notifyDataSetChanged()
                currentStepNumber = 0
                floatingView?.let { view ->
                    view.findViewById<TextView>(R.id.tv_result)?.visibility = View.GONE
                    view.findViewById<TextView>(R.id.tv_step_counter)?.text =
                        getString(R.string.step_counter_default)
                }
            }

            floatingView?.let { view ->
                val statusText = view.findViewById<TextView>(R.id.tv_status)
                val indicator = view.findViewById<View>(R.id.status_indicator)

                val (textRes, colorRes) =
                    when (status) {
                        TaskStatus.IDLE -> R.string.task_status_idle to R.color.status_idle
                        TaskStatus.RUNNING -> R.string.task_status_running to R.color.status_running
                        TaskStatus.PAUSED -> R.string.task_status_paused to R.color.status_paused
                        TaskStatus.COMPLETED -> R.string.task_status_completed to R.color.status_completed
                        TaskStatus.FAILED -> R.string.task_status_failed to R.color.status_failed
                        TaskStatus.WAITING_CONFIRMATION -> R.string.floating_waiting_confirm to R.color.status_waiting
                        TaskStatus.WAITING_TAKEOVER -> R.string.takeover_title to R.color.status_waiting
                    }

                statusText?.text = getString(textRes)
                indicator?.let {
                    val drawable =
                        (it.background as? GradientDrawable)
                            ?: GradientDrawable().also { d -> it.background = d }
                    drawable.shape = GradientDrawable.OVAL
                    drawable.setColor(ContextCompat.getColor(this@FloatingWindowService, colorRes))
                }

                // Update UI based on status
                Logger.d(TAG, "updateStatus calling updateUIForStatus with status: $status")
                updateUIForStatus(status)
            } ?: Logger.w(TAG, "updateStatus: floatingView is null!")
        }
    }

    /**
     * Updates the step counter display.
     *
     * @param step The current step number to display
     *
     */
    fun updateStepNumber(step: Int) {
        currentStepNumber = step
        serviceScope.launch {
            floatingView?.findViewById<TextView>(R.id.tv_step_counter)?.text =
                getString(R.string.step_counter_format, step)
        }
    }

    /**
     * Shows a result message in the floating window.
     *
     * @param message The result message to display
     * @param isSuccess Whether the result represents success or failure
     *
     */
    fun showResult(message: String, isSuccess: Boolean) {
        serviceScope.launch {
            floatingView?.let { view ->
                val resultView = view.findViewById<TextView>(R.id.tv_result)
                resultView?.visibility = View.VISIBLE
                resultView?.text = message
                resultView?.setTextColor(
                    ContextCompat.getColor(
                        this@FloatingWindowService,
                        if (isSuccess) R.color.status_completed else R.color.status_failed,
                    ),
                )
            }
        }
    }

    /**
     * Resets the floating window to its initial state.
     *
     * Delegates to TaskExecutionManager.resetTask() and the state will be
     * synchronized through StateFlow. Only UI-specific cleanup (clearing input)
     * is done here.
     */
    fun reset() {
        serviceScope.launch {
            Logger.d(TAG, "reset() called - delegating to TaskExecutionManager")

            // Clear task input field (UI-only operation)
            floatingView?.let { view ->
                view.findViewById<EditText>(R.id.task_input)?.text?.clear()
            }

            // Reset task state through TaskExecutionManager
            // The UI will be updated automatically via StateFlow observation
            TaskExecutionManager.resetTask()

            Logger.d(TAG, "reset() complete")
        }
    }

    /**
     * Brings the floating window to the front of other windows.
     *
     */
    fun bringToFront() {
        showAndBringToFront()
    }

    /**
     * Shows a confirmation dialog and waits for user response.
     *
     * @param message The confirmation message to display
     * @param callback Function to be called with the user's response (true for confirm, false for cancel)
     *
     */
    fun showConfirmation(message: String, callback: (Boolean) -> Unit) {
        serviceScope.launch {
            updateStatus(TaskStatus.WAITING_CONFIRMATION)
            Logger.d(TAG, "Confirmation requested: $message")
            delay(100)
            callback(true)
            updateStatus(TaskStatus.RUNNING)
        }
    }

    /**
     * Shows a takeover request and waits for user to take control.
     *
     * @param message The takeover message to display
     * @param callback Function to be called when user acknowledges the takeover
     *
     */
    fun showTakeOver(message: String, callback: () -> Unit) {
        serviceScope.launch {
            updateStatus(TaskStatus.WAITING_TAKEOVER)
            Logger.d(TAG, "Takeover requested: $message")
            delay(100)
            callback()
            updateStatus(TaskStatus.RUNNING)
        }
    }

    /**
     * Shows an interaction dialog with multiple options.
     *
     * @param options List of option strings to display
     * @param callback Function to be called with the selected option index (-1 if no options)
     *
     */
    fun showInteract(options: List<String>, callback: (Int) -> Unit) {
        serviceScope.launch {
            updateStatus(TaskStatus.WAITING_CONFIRMATION)
            Logger.d(TAG, "Interact requested with options: $options")
            delay(100)
            callback(if (options.isNotEmpty()) 0 else -1)
            updateStatus(TaskStatus.RUNNING)
        }
    }

    // ==================== Private Methods ====================

    /**
     * Updates the UI elements based on the current task status.
     *
     * @param status The current task status
     */
    private fun updateUIForStatus(status: TaskStatus) {
        Logger.d(TAG, "updateUIForStatus called with status: $status")
        floatingView?.let { view ->
            val inputArea = view.findViewById<LinearLayout>(R.id.input_area)
            val stepsRecycler = view.findViewById<RecyclerView>(R.id.steps_recycler_view)
            val controlButtonsContainer = view.findViewById<LinearLayout>(R.id.control_buttons_container)
            val stopBtn = view.findViewById<MaterialButton>(R.id.btn_stop)
            val pauseBtn = view.findViewById<MaterialButton>(R.id.btn_pause)
            val resumeBtn = view.findViewById<MaterialButton>(R.id.btn_resume)
            val newTaskBtn = view.findViewById<MaterialButton>(R.id.btn_new_task)

            Logger.d(TAG, "updateUIForStatus: inputArea=$inputArea, stepsRecycler=$stepsRecycler, stopBtn=$stopBtn")

            when (status) {
                TaskStatus.IDLE -> {
                    // Show input area, hide steps and control buttons
                    Logger.d(TAG, "updateUIForStatus: Setting IDLE state - show input, hide steps")
                    inputArea?.visibility = View.VISIBLE
                    stepsRecycler?.visibility = View.GONE
                    controlButtonsContainer?.visibility = View.GONE
                    newTaskBtn?.visibility = View.GONE
                }

                TaskStatus.RUNNING, TaskStatus.WAITING_CONFIRMATION, TaskStatus.WAITING_TAKEOVER -> {
                    // Show steps and control buttons with pause visible
                    Logger.d(TAG, "updateUIForStatus: Setting RUNNING state - hide input, show steps")
                    inputArea?.visibility = View.GONE
                    stepsRecycler?.visibility = View.VISIBLE
                    controlButtonsContainer?.visibility = View.VISIBLE
                    pauseBtn?.visibility = View.VISIBLE
                    resumeBtn?.visibility = View.GONE
                    stopBtn?.visibility = View.VISIBLE
                    newTaskBtn?.visibility = View.GONE
                }

                TaskStatus.PAUSED -> {
                    // Show steps and control buttons with resume visible
                    Logger.d(TAG, "updateUIForStatus: Setting PAUSED state - show steps and resume button")
                    inputArea?.visibility = View.GONE
                    stepsRecycler?.visibility = View.VISIBLE
                    controlButtonsContainer?.visibility = View.VISIBLE
                    pauseBtn?.visibility = View.GONE
                    resumeBtn?.visibility = View.VISIBLE
                    stopBtn?.visibility = View.VISIBLE
                    newTaskBtn?.visibility = View.GONE
                }

                TaskStatus.COMPLETED, TaskStatus.FAILED -> {
                    // Show steps with a "New Task" button to allow starting new task
                    Logger.d(TAG, "updateUIForStatus: Setting COMPLETED/FAILED state - show steps and new task button")
                    inputArea?.visibility = View.GONE
                    stepsRecycler?.visibility = View.VISIBLE
                    controlButtonsContainer?.visibility = View.GONE
                    newTaskBtn?.visibility = View.VISIBLE
                }
            }
            val inputVis = inputArea?.visibility
            val stepsVis = stepsRecycler?.visibility
            Logger.d(TAG, "updateUIForStatus: After update - inputArea.vis=$inputVis, stepsRecycler.vis=$stepsVis")
        } ?: Logger.w(TAG, "updateUIForStatus: floatingView is null!")
    }

    /**
     * Creates and shows the floating window.
     */
    private fun createAndShowWindow() {
        Logger.d(TAG, "Creating and showing floating window")
        createWindowView()
        addWindowInternal()
        // Note: updateStatus is now handled in createWindowView() based on current TaskExecutionManager state
        Logger.d(TAG, "Floating window created and shown")
    }

    /**
     * Creates the window view without showing it.
     */
    private fun createWindowView() {
        if (floatingView != null) {
            Logger.d(TAG, "Window view already created")
            return
        }

        Logger.d(TAG, "Creating floating window view")

        val themedContext = android.view.ContextThemeWrapper(this, R.style.Theme_AutoGLM)
        floatingView =
            LayoutInflater.from(themedContext).inflate(R.layout.layout_floating_window, null)

        layoutParams = createLayoutParams()

        setupRecyclerView()
        setupDragBehavior()
        setupButtons()
        setupTaskInput()

        // Initialize UI with current state from TaskExecutionManager instead of assuming IDLE
        // This fixes the bug where IDLE would overwrite the correct RUNNING status
        // when the floating window is created after a task has already started
        val currentState = TaskExecutionManager.taskState.value
        currentStatus = currentState.status
        updateUIForStatus(currentState.status)
        updateStatusIndicator(currentState.status)
        if (currentState.stepNumber > 0) {
            currentStepNumber = currentState.stepNumber
            floatingView?.findViewById<TextView>(R.id.tv_step_counter)?.text =
                getString(R.string.step_counter_format, currentState.stepNumber)
        }

        // Setup touch listener to clear focus when tapping outside input
        setupTouchToClearFocus()

        Logger.d(TAG, "Floating window view created")
    }

    /**
     * Sets up touch listener on the floating view to clear input focus
     * when user taps outside the input field or outside the window.
     */
    private fun setupTouchToClearFocus() {
        floatingView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_OUTSIDE) {
                val taskInput = floatingView?.findViewById<EditText>(R.id.task_input)
                if (taskInput?.hasFocus() == true) {
                    if (event.action == MotionEvent.ACTION_OUTSIDE) {
                        // Touch outside window, clear focus
                        Logger.d(TAG, "Touch outside window, clearing focus")
                        clearInputFocus()
                    } else {
                        // Check if touch is outside the input field
                        val inputLocation = IntArray(2)
                        taskInput.getLocationOnScreen(inputLocation)
                        val inputRect =
                            android.graphics.Rect(
                                inputLocation[0],
                                inputLocation[1],
                                inputLocation[0] + taskInput.width,
                                inputLocation[1] + taskInput.height,
                            )

                        // Get touch position relative to screen
                        val touchX = event.rawX.toInt()
                        val touchY = event.rawY.toInt()

                        if (!inputRect.contains(touchX, touchY)) {
                            // Touch is outside input, clear focus
                            Logger.d(TAG, "Touch outside input, clearing focus")
                            clearInputFocus()
                        }
                    }
                }
            }
            false // Don't consume the event, let it propagate
        }
    }

    /**
     * Creates the window layout parameters.
     *
     * @return WindowManager.LayoutParams configured for the floating window
     */
    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val widthPx = (screenWidth * WIDTH_PERCENT).toInt()
        val heightPx = (screenHeight * HEIGHT_PERCENT).toInt()

        return WindowManager
            .LayoutParams(
                widthPx,
                heightPx,
                type,
                // FLAG_NOT_TOUCH_MODAL: allow touches outside window to pass through
                // FLAG_WATCH_OUTSIDE_TOUCH: receive ACTION_OUTSIDE events to clear focus
                // FLAG_NOT_FOCUSABLE: initially not focusable so back key works in other apps
                // When user clicks input, FLAG_NOT_FOCUSABLE will be removed to allow keyboard
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
                // Allow keyboard input
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
    }

    /**
     * Sets up the RecyclerView for displaying steps.
     */
    private fun setupRecyclerView() {
        val recyclerView = floatingView?.findViewById<RecyclerView>(R.id.steps_recycler_view)
        recyclerView?.let { rv ->
            rv.layoutManager = LinearLayoutManager(this)
            stepsAdapter = StepsAdapter(stepsList)
            rv.adapter = stepsAdapter
        }
    }

    /**
     * Sets up the task input field and start button.
     */
    private fun setupTaskInput() {
        val taskInput = floatingView?.findViewById<EditText>(R.id.task_input)
        val startBtn = floatingView?.findViewById<MaterialButton>(R.id.btn_start)
        val selectTemplateBtn = floatingView?.findViewById<ImageButton>(R.id.btn_select_template)

        // Use OnTouchListener to handle focus BEFORE the click event
        // This ensures FLAG_NOT_FOCUSABLE is removed before system tries to show keyboard
        taskInput?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                Logger.d(TAG, "taskInput touched (ACTION_DOWN)")
                layoutParams?.let { params ->
                    val wasNotFocusable = (params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0
                    if (wasNotFocusable) {
                        // Remove FLAG_NOT_FOCUSABLE to allow focus BEFORE the touch is processed
                        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                        if (isAttached.get()) {
                            try {
                                windowManager?.updateViewLayout(floatingView, params)
                                Logger.d(TAG, "setupTaskInput: removed FLAG_NOT_FOCUSABLE on touch")

                                // Use ViewTreeObserver to wait for window focus, then show keyboard
                                floatingView?.viewTreeObserver?.addOnWindowFocusChangeListener(
                                    object : android.view.ViewTreeObserver.OnWindowFocusChangeListener {
                                        override fun onWindowFocusChanged(hasFocus: Boolean) {
                                            Logger.d(TAG, "Window focus changed: hasFocus=$hasFocus")
                                            if (hasFocus) {
                                                floatingView?.viewTreeObserver?.removeOnWindowFocusChangeListener(this)
                                                taskInput.requestFocus()
                                                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                                                imm.showSoftInput(taskInput, InputMethodManager.SHOW_IMPLICIT)
                                            }
                                        }
                                    },
                                )
                            } catch (e: Exception) {
                                Logger.e(TAG, "Error updating layout for focus", e)
                            }
                        }
                    }
                }
            }
            false // Don't consume the event, let it propagate for normal EditText behavior
        }

        // Template selection button
        selectTemplateBtn?.setOnClickListener {
            showTemplateSelectionPopup(it, taskInput)
        }

        startBtn?.setOnClickListener {
            val task = taskInput?.text?.toString()?.trim() ?: ""
            if (task.isNotBlank()) {
                // Check if we can start a task and get specific reason if not
                val blockReason = TaskExecutionManager.getStartTaskBlockReason()
                if (blockReason != TaskExecutionManager.StartTaskBlockReason.NONE) {
                    Logger.w(TAG, "Cannot start task: $blockReason")
                    val messageRes =
                        when (blockReason) {
                            TaskExecutionManager.StartTaskBlockReason.SERVICE_NOT_CONNECTED -> {
                                R.string.toast_shizuku_not_running
                            }

                            TaskExecutionManager.StartTaskBlockReason.PHONE_AGENT_NULL -> {
                                R.string.toast_phone_agent_not_ready
                            }

                            TaskExecutionManager.StartTaskBlockReason.TASK_ALREADY_RUNNING -> {
                                R.string.toast_task_already_running
                            }

                            TaskExecutionManager.StartTaskBlockReason.NONE -> {
                                R.string.toast_shizuku_not_running
                            } // Should not happen
                        }
                    Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Clear focus and hide keyboard
                clearInputFocus()

                Logger.d(TAG, "Starting task via TaskExecutionManager: $task")
                // Start task through TaskExecutionManager
                // UI updates will happen automatically through state observation
                TaskExecutionManager.startTask(task)
            }
        }
    }

    /**
     * Shows a popup menu for template selection.
     *
     * @param anchor The view to anchor the popup to
     * @param taskInput The EditText to populate with the selected template
     */
    private fun showTemplateSelectionPopup(anchor: View, taskInput: EditText?) {
        val settingsManager =
            com.kevinluo.autoglm.settings
                .SettingsManager.getInstance(this)
        val templates = settingsManager.getTaskTemplates()

        if (templates.isEmpty()) {
            android.widget.Toast
                .makeText(
                    this,
                    R.string.settings_no_templates,
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            return
        }

        val popup = android.widget.PopupMenu(this, anchor)
        templates.forEachIndexed { index, template ->
            popup.menu.add(0, index, index, template.name)
        }

        popup.setOnMenuItemClickListener { item ->
            val selectedTemplate = templates[item.itemId]
            taskInput?.setText(selectedTemplate.description)
            true
        }

        popup.show()
    }

    /**
     * Adds the floating window to the window manager.
     */
    private fun addWindowInternal() {
        if (isAttached.get() || floatingView == null || layoutParams == null) {
            val attached = isAttached.get()
            val hasView = floatingView != null
            val hasParams = layoutParams != null
            Logger.w(TAG, "Cannot add window: isAttached=$attached, view=$hasView, params=$hasParams")
            return
        }

        try {
            Logger.d(TAG, "Adding window with params: x=${layoutParams?.x}, y=${layoutParams?.y}")
            windowManager?.addView(floatingView, layoutParams)
            isAttached.set(true)
            Logger.d(TAG, "Window added successfully")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to add window", e)
        }
    }

    /**
     * Removes the floating window from the window manager.
     */
    private fun removeWindowInternal() {
        if (!isAttached.get() || floatingView == null) {
            Logger.w(TAG, "Cannot remove window: isAttached=${isAttached.get()}, view=${floatingView != null}")
            return
        }

        try {
            windowManager?.removeView(floatingView)
            isAttached.set(false)
            Logger.d(TAG, "Window removed successfully")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to remove window", e)
        }
    }

    /**
     * Removes the window and cleans up resources.
     */
    private fun removeWindow() {
        if (isAttached.get() && floatingView != null) {
            try {
                windowManager?.removeView(floatingView)
            } catch (e: Exception) {
                Logger.e(TAG, "Error removing window", e)
            }
        }
        floatingView = null
        layoutParams = null
        isAttached.set(false)
    }

    /**
     * Sets up drag behavior for the floating window header.
     */
    private fun setupDragBehavior() {
        val header = floatingView?.findViewById<View>(R.id.header) ?: return
        val params = layoutParams ?: return

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isAttached.get()) {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        try {
                            windowManager?.updateViewLayout(floatingView, params)
                        } catch (e: Exception) {
                            Logger.e(TAG, "Error updating layout", e)
                        }
                    }
                    true
                }

                else -> {
                    false
                }
            }
        }
    }

    /**
     * Sets up click listeners for the floating window buttons.
     */
    private fun setupButtons() {
        floatingView?.findViewById<ImageButton>(R.id.btn_open_app)?.setOnClickListener {
            // Open MainActivity
            val intent =
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            startActivity(intent)
        }

        floatingView?.findViewById<ImageButton>(R.id.btn_minimize)?.setOnClickListener {
            toggleMinimize()
        }

        floatingView?.findViewById<ImageButton>(R.id.btn_close)?.setOnClickListener {
            // Stop service and close window
            stopSelf()
        }

        floatingView?.findViewById<MaterialButton>(R.id.btn_stop)?.setOnClickListener {
            Logger.d(TAG, "Stop button clicked")
            TaskExecutionManager.cancelTask()
        }

        floatingView?.findViewById<MaterialButton>(R.id.btn_pause)?.setOnClickListener {
            Logger.d(TAG, "Pause button clicked")
            TaskExecutionManager.pauseTask()
        }

        floatingView?.findViewById<MaterialButton>(R.id.btn_resume)?.setOnClickListener {
            Logger.d(TAG, "Resume button clicked")
            TaskExecutionManager.resumeTask()
        }

        floatingView?.findViewById<MaterialButton>(R.id.btn_new_task)?.setOnClickListener {
            Logger.d(TAG, "New task button clicked")
            // Reset TaskExecutionManager and local UI state
            TaskExecutionManager.resetTask()
            reset()
        }
    }

    /**
     * Toggles the minimized state of the floating window.
     */
    private fun toggleMinimize() {
        val inputArea = floatingView?.findViewById<LinearLayout>(R.id.input_area)
        val recyclerView = floatingView?.findViewById<RecyclerView>(R.id.steps_recycler_view)
        val resultView = floatingView?.findViewById<TextView>(R.id.tv_result)
        val stopBtn = floatingView?.findViewById<MaterialButton>(R.id.btn_stop)
        val pauseBtn = floatingView?.findViewById<MaterialButton>(R.id.btn_pause)
        val resumeBtn = floatingView?.findViewById<MaterialButton>(R.id.btn_resume)
        val newTaskBtn = floatingView?.findViewById<MaterialButton>(R.id.btn_new_task)
        val minimizeBtn = floatingView?.findViewById<ImageButton>(R.id.btn_minimize)
        val container = floatingView?.findViewById<View>(R.id.floating_window_container)

        isMinimized = !isMinimized

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        if (isMinimized) {
            // Hide all content except header
            inputArea?.visibility = View.GONE
            recyclerView?.visibility = View.GONE
            resultView?.visibility = View.GONE
            stopBtn?.visibility = View.GONE
            pauseBtn?.visibility = View.GONE
            resumeBtn?.visibility = View.GONE
            newTaskBtn?.visibility = View.GONE
            // Change icon to + (expand)
            minimizeBtn?.setImageResource(R.drawable.ic_plus)

            // Shrink window to just header - use fixed height based on header size
            layoutParams?.width = (screenWidth * WIDTH_PERCENT * 0.8).toInt()
            // Use a fixed height for minimized state (header height ~48dp)
            val headerHeight = (48 * displayMetrics.density).toInt()
            layoutParams?.height = headerHeight
        } else {
            // Restore based on current status
            updateUIForStatus(currentStatus)
            if (resultView?.text?.isNotEmpty() == true) {
                resultView.visibility = View.VISIBLE
            }
            // Change icon to - (minimize)
            minimizeBtn?.setImageResource(R.drawable.ic_minus)

            // Restore full window size
            layoutParams?.width = (screenWidth * WIDTH_PERCENT).toInt()
            layoutParams?.height = (screenHeight * HEIGHT_PERCENT).toInt()
        }

        if (isAttached.get()) {
            try {
                windowManager?.updateViewLayout(floatingView, layoutParams)
            } catch (e: Exception) {
                Logger.e(TAG, "Error updating layout after minimize", e)
            }
        }
    }

    // ==================== Steps Adapter ====================

    /**
     * RecyclerView adapter for displaying steps in the waterfall view.
     *
     * @property steps The list of steps to display
     *
     */
    private inner class StepsAdapter(private val steps: List<FloatingStep>) :
        RecyclerView.Adapter<StepsAdapter.ViewHolder>() {
        /**
         * ViewHolder for step items.
         */
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val stepNumber: TextView = itemView.findViewById(R.id.step_number)
            val thinkingText: TextView = itemView.findViewById(R.id.thinking_text)
            val actionText: TextView = itemView.findViewById(R.id.action_text)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view =
                LayoutInflater
                    .from(parent.context)
                    .inflate(R.layout.item_floating_step, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val step = steps[position]
            holder.stepNumber.text = step.stepNumber.toString()

            // Check if both thinking and action are empty (loading state)
            val isLoading = step.thinking.isBlank() && step.action.isBlank()

            if (isLoading) {
                // Show "thinking..." placeholder
                holder.thinkingText.visibility = View.VISIBLE
                holder.thinkingText.text = getString(R.string.floating_thinking)
            } else if (step.thinking.isBlank()) {
                holder.thinkingText.visibility = View.GONE
            } else {
                holder.thinkingText.visibility = View.VISIBLE
                holder.thinkingText.text = step.thinking
            }

            // Hide action area (including dot separator) if action is empty
            val actionContainer = holder.actionText.parent as? View
            if (step.action.isBlank()) {
                actionContainer?.visibility = View.GONE
            } else {
                actionContainer?.visibility = View.VISIBLE
                holder.actionText.text = step.action
            }
        }

        override fun getItemCount(): Int = steps.size
    }
}
