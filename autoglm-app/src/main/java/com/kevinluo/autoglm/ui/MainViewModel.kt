package com.kevinluo.autoglm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kevinluo.autoglm.task.TaskExecutionManager
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Data class representing the permission states across the app.
 *
 * Used for cross-Fragment state sharing to synchronize permission UI.
 *
 * @property shizuku Whether Shizuku permission is granted and connected
 * @property overlay Whether overlay (draw over other apps) permission is granted
 * @property keyboard Whether the custom keyboard is enabled
 * @property battery Whether battery optimization is disabled for the app
 */
data class PermissionStates(
    val shizuku: Boolean = false,
    val overlay: Boolean = false,
    val keyboard: Boolean = false,
    val battery: Boolean = false,
)

/**
 * Enum representing the types of permissions the app requires.
 */
enum class PermissionType {
    /** Shizuku permission for system-level operations. */
    SHIZUKU,

    /** Overlay permission for floating window. */
    OVERLAY,

    /** Keyboard permission for custom input method. */
    KEYBOARD,

    /** Battery optimization exemption. */
    BATTERY,
}

/**
 * Data class representing the UI state for the main screen.
 *
 * @property shizukuStatus Current Shizuku connection status
 * @property hasOverlayPermission Whether the app has overlay permission
 * @property taskStatus Current task execution status
 * @property stepNumber Current step number in task execution
 * @property thinking Current thinking text from the model
 * @property currentAction Current action being executed
 * @property isTaskRunning Whether a task is currently running
 * @property canStartTask Whether a new task can be started
 *
 */
data class MainUiState(
    val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_RUNNING,
    val hasOverlayPermission: Boolean = false,
    val taskStatus: TaskStatus = TaskStatus.IDLE,
    val stepNumber: Int = 0,
    val thinking: String = "",
    val currentAction: String = "",
    val isTaskRunning: Boolean = false,
    val canStartTask: Boolean = false,
)

/**
 * Enum representing the Shizuku connection status.
 *
 */
enum class ShizukuStatus {
    /** Shizuku service is not running. */
    NOT_RUNNING,

    /** Shizuku is running but permission not granted. */
    NO_PERMISSION,

    /** Currently connecting to Shizuku. */
    CONNECTING,

    /** Successfully connected to Shizuku. */
    CONNECTED,
}

/**
 * Sealed class representing one-time UI events.
 *
 * These events are consumed once and not persisted in the UI state.
 *
 */
sealed class MainUiEvent {
    /**
     * Event to show a toast message from a string resource.
     *
     * @property messageResId The string resource ID for the message
     */
    data class ShowToast(val messageResId: Int) : MainUiEvent()

    /**
     * Event to show a toast message with a text string.
     *
     * @property message The message text to display
     */
    data class ShowToastText(val message: String) : MainUiEvent()

    /**
     * Event indicating task completion.
     *
     * @property message The completion message
     */
    data class TaskCompleted(val message: String) : MainUiEvent()

    /**
     * Event indicating task failure.
     *
     * @property error The error message
     */
    data class TaskFailed(val error: String) : MainUiEvent()

    /** Event to minimize the app. */
    object MinimizeApp : MainUiEvent()
}

/**
 * ViewModel for MainActivity.
 *
 * Manages UI state using StateFlow for reactive updates and observes
 * [TaskExecutionManager] to receive task execution state updates.
 *
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MainUiState())

    /** Observable UI state for the main screen. */
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MainUiEvent>()

    /** Observable stream of one-time UI events. */
    val events = _events.asSharedFlow()

    private val _outputLog = MutableStateFlow("")

    /** Observable output log for displaying task execution details. */
    val outputLog: StateFlow<String> = _outputLog.asStateFlow()

    private val logBuilder = StringBuilder()

    // region Cross-Fragment State Sharing

    private val _permissionStates = MutableStateFlow(PermissionStates())

    /**
     * Observable permission states for cross-Fragment synchronization.
     *
     * When permissions change in SettingsFragment, other Fragments can observe
     * this StateFlow to update their UI accordingly.
     */
    val permissionStates: StateFlow<PermissionStates> = _permissionStates.asStateFlow()

    private val _isServiceConnected = MutableStateFlow(false)

    /**
     * Observable Shizuku service connection state.
     *
     * Indicates whether the UserService is bound and ready for use.
     */
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    // endregion

    init {
        observeTaskState()
    }

    /**
     * Observes TaskExecutionManager.taskState and updates _uiState accordingly.
     *
     * This replaces the previous PhoneAgentListener implementation.
     */
    private fun observeTaskState() {
        viewModelScope.launch {
            TaskExecutionManager.taskState.collect { taskState ->
                Logger.d(TAG, "TaskState updated: status=${taskState.status}, step=${taskState.stepNumber}")

                val isRunning =
                    taskState.status == TaskStatus.RUNNING ||
                        taskState.status == TaskStatus.PAUSED

                _uiState.value =
                    _uiState.value.copy(
                        taskStatus = taskState.status,
                        stepNumber = taskState.stepNumber,
                        thinking = taskState.thinking,
                        currentAction = taskState.currentAction,
                        isTaskRunning = isRunning,
                        canStartTask = calculateCanStartTask(isRunning = isRunning),
                    )

                // Emit events for task completion/failure
                when (taskState.status) {
                    TaskStatus.COMPLETED -> {
                        if (taskState.resultMessage.isNotEmpty()) {
                            appendLog("Task completed: ${taskState.resultMessage}")
                            _events.emit(MainUiEvent.TaskCompleted(taskState.resultMessage))
                        }
                    }

                    TaskStatus.FAILED -> {
                        if (taskState.resultMessage.isNotEmpty()) {
                            appendLog("Task failed: ${taskState.resultMessage}")
                            _events.emit(MainUiEvent.TaskFailed(taskState.resultMessage))
                        }
                    }

                    else -> { /* No event needed */ }
                }
            }
        }
    }

    /**
     * Updates the Shizuku connection status.
     *
     * @param status The new Shizuku status
     *
     */
    fun updateShizukuStatus(status: ShizukuStatus) {
        Logger.d(TAG, "updateShizukuStatus: $status")
        _uiState.value =
            _uiState.value.copy(
                shizukuStatus = status,
                canStartTask = calculateCanStartTask(status),
            )
        // Sync with permission states for cross-Fragment sharing
        val isConnected = status == ShizukuStatus.CONNECTED
        updatePermissionState(PermissionType.SHIZUKU, isConnected)
        _isServiceConnected.value = isConnected
    }

    /**
     * Updates the overlay permission status.
     *
     * @param hasPermission Whether the app has overlay permission
     *
     */
    fun updateOverlayPermission(hasPermission: Boolean) {
        Logger.d(TAG, "updateOverlayPermission: $hasPermission")
        _uiState.value =
            _uiState.value.copy(
                hasOverlayPermission = hasPermission,
                canStartTask = calculateCanStartTask(hasOverlayPermission = hasPermission),
            )
        // Also update permission states for cross-Fragment synchronization
        updatePermissionState(PermissionType.OVERLAY, hasPermission)
    }

    // region Cross-Fragment State Update Methods

    /**
     * Updates a specific permission state.
     *
     * This method updates the shared permission states that can be observed
     * by all Fragments. Use this when a permission is granted or revoked.
     *
     * @param type The type of permission being updated
     * @param granted Whether the permission is granted
     */
    fun updatePermissionState(type: PermissionType, granted: Boolean) {
        Logger.d(TAG, "updatePermissionState: $type = $granted")
        _permissionStates.value =
            when (type) {
                PermissionType.SHIZUKU -> _permissionStates.value.copy(shizuku = granted)
                PermissionType.OVERLAY -> _permissionStates.value.copy(overlay = granted)
                PermissionType.KEYBOARD -> _permissionStates.value.copy(keyboard = granted)
                PermissionType.BATTERY -> _permissionStates.value.copy(battery = granted)
            }
    }

    /**
     * Updates all permission states at once.
     *
     * Use this method when initializing or refreshing all permission states.
     *
     * @param states The new permission states
     */
    fun updateAllPermissionStates(states: PermissionStates) {
        Logger.d(TAG, "updateAllPermissionStates: $states")
        _permissionStates.value = states
    }

    /**
     * Sets the Shizuku service connection state.
     *
     * @param connected Whether the service is connected
     */
    fun setServiceConnected(connected: Boolean) {
        Logger.d(TAG, "setServiceConnected: $connected")
        _isServiceConnected.value = connected
        // Also update Shizuku permission state
        updatePermissionState(PermissionType.SHIZUKU, connected)
    }

    // endregion

    /**
     * Updates the task input availability based on whether text is entered.
     *
     * @param hasText Whether the task input field has text
     *
     */
    fun updateTaskInput(hasText: Boolean) {
        _uiState.value =
            _uiState.value.copy(
                canStartTask = calculateCanStartTask(hasTaskText = hasText),
            )
    }

    /**
     * Calculates whether a new task can be started based on current state.
     *
     * @param status Current Shizuku status
     * @param hasOverlayPermission Whether overlay permission is granted
     * @param hasTaskText Whether task input has text
     * @param isRunning Whether a task is currently running
     * @return true if a new task can be started, false otherwise
     */
    private fun calculateCanStartTask(
        status: ShizukuStatus = _uiState.value.shizukuStatus,
        hasOverlayPermission: Boolean = _uiState.value.hasOverlayPermission,
        hasTaskText: Boolean = true,
        isRunning: Boolean = _uiState.value.isTaskRunning,
    ): Boolean = status == ShizukuStatus.CONNECTED &&
        hasOverlayPermission &&
        hasTaskText &&
        !isRunning &&
        TaskExecutionManager.canStartTask()

    /**
     * Starts a new task with the given description.
     *
     * @param taskDescription The description of the task to execute
     *
     */
    fun startTask(taskDescription: String) {
        if (!TaskExecutionManager.canStartTask()) {
            appendLog("Error: Cannot start task - preconditions not met")
            Logger.w(TAG, "Cannot start task: preconditions not met")
            return
        }

        // Clear previous output
        logBuilder.clear()
        _outputLog.value = ""

        Logger.d(TAG, "Starting task: ${taskDescription.take(50)}...")
        appendLog("Starting task: $taskDescription")

        // Notify state manager that task is starting
        FloatingWindowStateManager.onTaskStarted(getApplication())

        viewModelScope.launch {
            // Minimize app
            _events.emit(MainUiEvent.MinimizeApp)
        }

        // Start task via TaskExecutionManager
        TaskExecutionManager.startTask(taskDescription)
    }

    /**
     * Cancels the currently running task.
     *
     */
    fun cancelTask() {
        Logger.d(TAG, "Cancelling task")
        TaskExecutionManager.cancelTask()
        appendLog("Task cancelled by user")
        // Notify state manager that task completed
        FloatingWindowStateManager.onTaskCompleted()
    }

    /**
     * Pauses the currently running task.
     */
    fun pauseTask() {
        Logger.d(TAG, "Pausing task")
        val paused = TaskExecutionManager.pauseTask()
        if (paused) {
            appendLog("Task paused by user")
        } else {
            Logger.w(TAG, "Failed to pause task - not running")
        }
    }

    /**
     * Resumes the paused task.
     */
    fun resumeTask() {
        Logger.d(TAG, "Resuming task")
        val resumed = TaskExecutionManager.resumeTask()
        if (resumed) {
            appendLog("Task resumed by user")
        } else {
            Logger.w(TAG, "Failed to resume task - not paused")
        }
    }

    /**
     * Resets the agent state before starting a new task.
     */
    fun resetAgent() {
        Logger.d(TAG, "Resetting agent")
        TaskExecutionManager.resetTask()
        logBuilder.clear()
        _outputLog.value = ""
    }

    /**
     * Appends a timestamped message to the output log.
     *
     * @param message The message to append
     */
    private fun appendLog(message: String) {
        val timestamp =
            java.text
                .SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
        logBuilder.append("[$timestamp] $message\n")
        _outputLog.value = logBuilder.toString()
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}
