package com.kevinluo.autoglm.task

import android.content.Context
import com.kevinluo.autoglm.ComponentManager
import com.kevinluo.autoglm.action.AgentAction
import com.kevinluo.autoglm.agent.AgentState
import com.kevinluo.autoglm.agent.PhoneAgentListener
import com.kevinluo.autoglm.ui.FloatingWindowStateManager
import com.kevinluo.autoglm.ui.TaskStatus
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Singleton manager for task execution state.
 *
 * This is the single source of truth for task execution state in the application.
 * Both [MainViewModel] and [FloatingWindowService] observe this manager's StateFlows
 * to keep their UIs synchronized.
 *
 * Implements [PhoneAgentListener] to receive callbacks from [PhoneAgent] and
 * update the state accordingly.
 */
object TaskExecutionManager : PhoneAgentListener {
    private const val TAG = "TaskExecutionManager"
    private const val PHONE_AGENT_POLL_INTERVAL_MS = 500L

    private var applicationContext: Context? = null
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Primary state flow for task execution state
    private val _taskState = MutableStateFlow(TaskExecutionState())

    /**
     * Observable task execution state.
     *
     * UI components should observe this StateFlow to receive task state updates.
     */
    val taskState: StateFlow<TaskExecutionState> = _taskState.asStateFlow()

    // Steps list for waterfall display
    private val _steps = MutableStateFlow<List<TaskStep>>(emptyList())

    /**
     * Observable list of task steps for waterfall display.
     *
     * Each step contains the step number, thinking text, and action.
     */
    val steps: StateFlow<List<TaskStep>> = _steps.asStateFlow()

    /**
     * Initializes the TaskExecutionManager with application context.
     *
     * Should be called in [AutoGLMApplication.onCreate] after [ComponentManager] is initialized.
     *
     * @param context Application context
     */
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        Logger.i(TAG, "TaskExecutionManager initialized")
        observePhoneAgentAvailability()
    }

    /**
     * Observes ComponentManager.phoneAgent availability and registers/unregisters as listener.
     *
     * When phoneAgent becomes available, registers this manager as PhoneAgentListener.
     * When phoneAgent becomes null, unregisters.
     */
    private fun observePhoneAgentAvailability() {
        managerScope.launch {
            var lastPhoneAgent: com.kevinluo.autoglm.agent.PhoneAgent? = null

            // Poll for phoneAgent availability changes
            // This is a simple approach since ComponentManager doesn't expose a StateFlow for phoneAgent
            while (true) {
                val componentManager = getComponentManager()
                val currentPhoneAgent = componentManager?.phoneAgent

                if (currentPhoneAgent != lastPhoneAgent) {
                    if (lastPhoneAgent != null) {
                        // Unregister from previous phoneAgent
                        lastPhoneAgent.setListener(null)
                        Logger.i(TAG, "Unregistered as PhoneAgentListener")
                    }

                    if (currentPhoneAgent != null) {
                        // Register with new phoneAgent
                        currentPhoneAgent.setListener(this@TaskExecutionManager)
                        Logger.i(TAG, "Registered as PhoneAgentListener")
                    }

                    lastPhoneAgent = currentPhoneAgent
                }

                kotlinx.coroutines.delay(PHONE_AGENT_POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Gets the ComponentManager instance.
     *
     * @return ComponentManager instance or null if not initialized
     */
    private fun getComponentManager(): ComponentManager? {
        val ctx = applicationContext ?: return null
        return ComponentManager.getInstance(ctx)
    }

    // region Task Control Methods

    /**
     * Starts a new task with the given description.
     *
     * @param description The task description to execute
     * @return true if task was started successfully, false otherwise
     */
    fun startTask(description: String): Boolean {
        if (!canStartTask()) {
            Logger.w(TAG, "Cannot start task: preconditions not met")
            return false
        }

        val componentManager = getComponentManager() ?: return false
        val agent = componentManager.phoneAgent ?: return false

        // Update state to running
        _taskState.value =
            TaskExecutionState(
                status = TaskStatus.RUNNING,
                taskDescription = description,
            )

        // Clear previous steps
        _steps.value = emptyList()

        Logger.i(TAG, "Starting task: ${description.take(50)}...")

        // Launch task execution in coroutine
        managerScope.launch {
            try {
                val result = agent.run(description)

                if (result.success) {
                    Logger.i(TAG, "Task completed successfully: ${result.message}")
                    _taskState.value =
                        _taskState.value.copy(
                            status = TaskStatus.COMPLETED,
                            resultMessage = result.message,
                        )
                } else {
                    Logger.w(TAG, "Task failed: ${result.message}")
                    _taskState.value =
                        _taskState.value.copy(
                            status = TaskStatus.FAILED,
                            resultMessage = result.message,
                        )
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Task error: ${e.message}", e)
                _taskState.value =
                    _taskState.value.copy(
                        status = TaskStatus.FAILED,
                        resultMessage = e.message ?: "Unknown error",
                    )
            }
        }

        return true
    }

    /**
     * Pauses the currently running task.
     *
     * @return true if task was paused successfully, false otherwise
     */
    fun pauseTask(): Boolean {
        val componentManager = getComponentManager() ?: return false
        val agent = componentManager.phoneAgent ?: return false

        val paused = agent.pause()
        if (paused) {
            Logger.i(TAG, "Task paused")
            _taskState.value = _taskState.value.copy(status = TaskStatus.PAUSED)
        } else {
            Logger.w(TAG, "Failed to pause task")
        }
        return paused
    }

    /**
     * Resumes the paused task.
     *
     * @return true if task was resumed successfully, false otherwise
     */
    fun resumeTask(): Boolean {
        val componentManager = getComponentManager() ?: return false
        val agent = componentManager.phoneAgent ?: return false

        val resumed = agent.resume()
        if (resumed) {
            Logger.i(TAG, "Task resumed")
            _taskState.value = _taskState.value.copy(status = TaskStatus.RUNNING)
        } else {
            Logger.w(TAG, "Failed to resume task")
        }
        return resumed
    }

    /**
     * Cancels the currently running task.
     */
    fun cancelTask() {
        val componentManager = getComponentManager() ?: return
        val agent = componentManager.phoneAgent ?: return

        Logger.i(TAG, "Cancelling task")
        agent.cancel()
        _taskState.value =
            _taskState.value.copy(
                status = TaskStatus.FAILED,
                resultMessage = "任务已取消",
            )
    }

    /**
     * Resets the task state to idle.
     *
     * Should be called when user wants to start a new task after completion/failure.
     */
    fun resetTask() {
        Logger.i(TAG, "Resetting task state")
        _taskState.value = TaskExecutionState()
        _steps.value = emptyList()
    }

    // endregion

    // region Query Methods

    /**
     * Enum representing reasons why a task cannot be started.
     */
    enum class StartTaskBlockReason {
        /** No blocking reason, task can be started. */
        NONE,

        /** Shizuku service is not connected. */
        SERVICE_NOT_CONNECTED,

        /** PhoneAgent is not available. */
        PHONE_AGENT_NULL,

        /** A task is already running or paused. */
        TASK_ALREADY_RUNNING,
    }

    /**
     * Checks if a new task can be started.
     *
     * @return true if all preconditions are met to start a task
     */
    fun canStartTask(): Boolean = getStartTaskBlockReason() == StartTaskBlockReason.NONE

    /**
     * Gets the specific reason why a task cannot be started.
     *
     * This method provides more detailed information than [canStartTask] for
     * displaying appropriate error messages to the user.
     *
     * @return The blocking reason, or [StartTaskBlockReason.NONE] if task can be started
     */
    fun getStartTaskBlockReason(): StartTaskBlockReason {
        val componentManager =
            getComponentManager()
                ?: return StartTaskBlockReason.SERVICE_NOT_CONNECTED

        // Check if service is connected
        if (!componentManager.isServiceConnected) {
            Logger.d(TAG, "getStartTaskBlockReason: service not connected")
            return StartTaskBlockReason.SERVICE_NOT_CONNECTED
        }

        // Check if phone agent is available
        val agent = componentManager.phoneAgent
        if (agent == null) {
            Logger.d(TAG, "getStartTaskBlockReason: phoneAgent is null")
            return StartTaskBlockReason.PHONE_AGENT_NULL
        }

        // Check if a task is already running
        if (agent.isRunning() || agent.isPaused()) {
            Logger.d(TAG, "getStartTaskBlockReason: task already running or paused")
            return StartTaskBlockReason.TASK_ALREADY_RUNNING
        }

        return StartTaskBlockReason.NONE
    }

    /**
     * Checks if a task is currently running.
     *
     * @return true if a task is running or paused
     */
    fun isTaskRunning(): Boolean {
        val status = _taskState.value.status
        return status == TaskStatus.RUNNING || status == TaskStatus.PAUSED
    }

    // endregion

    // region PhoneAgentListener Implementation

    /**
     * Called when a new step starts in the task execution.
     *
     * @param stepNumber The step number that is starting
     */
    override fun onStepStarted(stepNumber: Int) {
        Logger.d(TAG, "Step $stepNumber started")
        _taskState.value =
            _taskState.value.copy(
                stepNumber = stepNumber,
                thinking = "",
                currentAction = "",
            )

        // Add new step to the list
        val newStep =
            TaskStep(
                stepNumber = stepNumber,
                thinking = "",
                action = "",
            )
        _steps.value = _steps.value + newStep
    }

    /**
     * Called when the model's thinking text is updated.
     *
     * @param thinking The current thinking text from the model
     */
    override fun onThinkingUpdate(thinking: String) {
        Logger.d(TAG, "Thinking update: ${thinking.take(50)}...")
        _taskState.value = _taskState.value.copy(thinking = thinking)

        // Update the last step's thinking
        val currentSteps = _steps.value.toMutableList()
        if (currentSteps.isNotEmpty()) {
            val lastIndex = currentSteps.lastIndex
            currentSteps[lastIndex] = currentSteps[lastIndex].copy(thinking = thinking)
            _steps.value = currentSteps
        }
    }

    /**
     * Called when an action is executed.
     *
     * @param action The action that was executed
     */
    override fun onActionExecuted(action: AgentAction) {
        val actionText = action.formatForDisplay()
        Logger.d(TAG, "Action executed: $actionText")
        _taskState.value = _taskState.value.copy(currentAction = actionText)

        // Update the last step's action
        val currentSteps = _steps.value.toMutableList()
        if (currentSteps.isNotEmpty()) {
            val lastIndex = currentSteps.lastIndex
            currentSteps[lastIndex] = currentSteps[lastIndex].copy(action = actionText)
            _steps.value = currentSteps
        }
    }

    /**
     * Called when the task completes successfully.
     *
     * @param message The completion message
     */
    override fun onTaskCompleted(message: String) {
        Logger.i(TAG, "Task completed: $message")
        _taskState.value =
            _taskState.value.copy(
                status = TaskStatus.COMPLETED,
                resultMessage = message,
            )
        // Notify FloatingWindowStateManager that task has ended
        FloatingWindowStateManager.onTaskCompleted()
    }

    /**
     * Called when the task fails.
     *
     * @param error The error message
     */
    override fun onTaskFailed(error: String) {
        Logger.e(TAG, "Task failed: $error")
        _taskState.value =
            _taskState.value.copy(
                status = TaskStatus.FAILED,
                resultMessage = error,
            )
        // Notify FloatingWindowStateManager that task has ended
        FloatingWindowStateManager.onTaskCompleted()
    }

    /**
     * Called when screenshot capture starts.
     */
    override fun onScreenshotStarted() {
        // Can be used to show loading indicator if needed
        Logger.d(TAG, "Screenshot started")
    }

    /**
     * Called when screenshot capture completes.
     */
    override fun onScreenshotCompleted() {
        Logger.d(TAG, "Screenshot completed")
    }

    /**
     * Called when the floating window needs to be refreshed.
     */
    override fun onFloatingWindowRefreshNeeded() {
        Logger.d(TAG, "Floating window refresh needed")
        // This will be handled by FloatingWindowService observing the state
    }

    /**
     * Called when task is paused.
     *
     * @param stepNumber The step number when paused
     */
    override fun onTaskPaused(stepNumber: Int) {
        Logger.i(TAG, "Task paused at step $stepNumber")
        _taskState.value = _taskState.value.copy(status = TaskStatus.PAUSED)
    }

    /**
     * Called when task is resumed.
     *
     * @param stepNumber The step number when resumed
     */
    override fun onTaskResumed(stepNumber: Int) {
        Logger.i(TAG, "Task resumed at step $stepNumber")
        _taskState.value = _taskState.value.copy(status = TaskStatus.RUNNING)
    }

    // endregion

    // region State Mapping Utilities

    /**
     * Maps AgentState to TaskStatus.
     *
     * Used for verifying state consistency between PhoneAgent and TaskExecutionManager.
     *
     * @param agentState The agent state to map
     * @return Corresponding TaskStatus
     */
    fun mapAgentStateToTaskStatus(agentState: AgentState): TaskStatus = when (agentState) {
        AgentState.IDLE -> TaskStatus.IDLE
        AgentState.RUNNING -> TaskStatus.RUNNING
        AgentState.PAUSED -> TaskStatus.PAUSED
        AgentState.CANCELLED -> TaskStatus.FAILED
    }

    // endregion
}
