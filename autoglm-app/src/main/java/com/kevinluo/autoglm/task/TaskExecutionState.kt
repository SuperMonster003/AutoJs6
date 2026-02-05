package com.kevinluo.autoglm.task

import com.kevinluo.autoglm.ui.TaskStatus

/**
 * Data class representing the current state of task execution.
 *
 * This is the primary state object exposed by [TaskExecutionManager] via StateFlow.
 * All UI components observe this state to display task progress.
 *
 * @property status Current task execution status
 * @property stepNumber Current step number in task execution (0 when idle)
 * @property thinking Current thinking text from the model
 * @property currentAction Current action being executed
 * @property resultMessage Result or error message when task completes/fails
 * @property taskDescription The description of the task being executed
 */
data class TaskExecutionState(
    val status: TaskStatus = TaskStatus.IDLE,
    val stepNumber: Int = 0,
    val thinking: String = "",
    val currentAction: String = "",
    val resultMessage: String = "",
    val taskDescription: String = "",
)
