package com.kevinluo.autoglm.task

/**
 * Data class representing a single step in task execution.
 *
 * Used for the waterfall display in the floating window to show
 * the history of steps executed during a task.
 *
 * @property stepNumber The sequential number of this step in the task execution
 * @property thinking The model's reasoning/thinking text for this step
 * @property action The action being performed in this step
 */
data class TaskStep(val stepNumber: Int, val thinking: String, val action: String)
