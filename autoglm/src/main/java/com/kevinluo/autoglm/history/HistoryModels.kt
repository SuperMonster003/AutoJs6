package com.kevinluo.autoglm.history

import com.kevinluo.autoglm.action.AgentAction
import java.util.UUID

/**
 * Represents a single step in task execution history.
 *
 * Each step captures the model's thinking, the action taken, and the result.
 * Screenshots are stored as file paths to avoid memory issues.
 *
 * @property stepNumber Sequential step number within the task (1-based)
 * @property timestamp Unix timestamp when the step was recorded
 * @property thinking Model's reasoning/thinking for this step
 * @property action The agent action executed, or null if no action
 * @property actionDescription Human-readable description of the action
 * @property screenshotPath File path to the original screenshot, or null
 * @property annotatedScreenshotPath File path to the annotated screenshot, or null
 * @property success Whether the step executed successfully
 * @property message Optional additional message or error details
 *
 */
data class HistoryStep(
    val stepNumber: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val thinking: String,
    val action: AgentAction?,
    val actionDescription: String,
    val screenshotPath: String?,
    val annotatedScreenshotPath: String?,
    val success: Boolean,
    val message: String? = null,
)

/**
 * Represents a complete task execution history.
 *
 * Contains all information about a task execution including metadata,
 * status, and all recorded steps.
 *
 * @property id Unique identifier for the task (UUID)
 * @property taskDescription Human-readable description of the task
 * @property startTime Unix timestamp when the task started
 * @property endTime Unix timestamp when the task ended, or null if still running
 * @property success Whether the task completed successfully
 * @property completionMessage Optional message describing the completion result
 * @property steps List of all recorded steps in execution order
 *
 */
data class TaskHistory(
    val id: String = UUID.randomUUID().toString(),
    val taskDescription: String,
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    var success: Boolean = false,
    var completionMessage: String? = null,
    val steps: MutableList<HistoryStep> = mutableListOf(),
) {
    /** Duration of the task in milliseconds. */
    val duration: Long
        get() = (endTime ?: System.currentTimeMillis()) - startTime

    /** Number of steps recorded in this task. */
    val stepCount: Int
        get() = steps.size
}

/**
 * Action annotation info for drawing on screenshots.
 *
 * Sealed class hierarchy representing different types of visual annotations
 * that can be drawn on screenshots to indicate user actions.
 *
 */
sealed class ActionAnnotation {
    /**
     * Circle annotation for tap actions.
     *
     * @property x X coordinate in relative units (0-1000)
     * @property y Y coordinate in relative units (0-1000)
     * @property screenWidth Actual screen width in pixels
     * @property screenHeight Actual screen height in pixels
     */
    data class TapCircle(val x: Int, val y: Int, val screenWidth: Int, val screenHeight: Int) : ActionAnnotation()

    /**
     * Arrow annotation for swipe actions.
     *
     * @property startX Start X coordinate in relative units (0-1000)
     * @property startY Start Y coordinate in relative units (0-1000)
     * @property endX End X coordinate in relative units (0-1000)
     * @property endY End Y coordinate in relative units (0-1000)
     * @property screenWidth Actual screen width in pixels
     * @property screenHeight Actual screen height in pixels
     */
    data class SwipeArrow(
        val startX: Int,
        val startY: Int,
        val endX: Int,
        val endY: Int,
        val screenWidth: Int,
        val screenHeight: Int,
    ) : ActionAnnotation()

    /**
     * Long press annotation (circle with duration indicator).
     *
     * @property x X coordinate in relative units (0-1000)
     * @property y Y coordinate in relative units (0-1000)
     * @property screenWidth Actual screen width in pixels
     * @property screenHeight Actual screen height in pixels
     * @property durationMs Duration of the long press in milliseconds
     */
    data class LongPressCircle(
        val x: Int,
        val y: Int,
        val screenWidth: Int,
        val screenHeight: Int,
        val durationMs: Int,
    ) : ActionAnnotation()

    /**
     * Double tap annotation (two concentric circles).
     *
     * @property x X coordinate in relative units (0-1000)
     * @property y Y coordinate in relative units (0-1000)
     * @property screenWidth Actual screen width in pixels
     * @property screenHeight Actual screen height in pixels
     */
    data class DoubleTapCircle(val x: Int, val y: Int, val screenWidth: Int, val screenHeight: Int) :
        ActionAnnotation()

    /**
     * Text annotation for type actions.
     *
     * @property text The text that was typed
     */
    data class TypeText(val text: String) : ActionAnnotation()

    /**
     * Batch annotation for multiple sequential actions.
     *
     * Shows numbered circles/arrows for each step in the batch.
     *
     * @property steps List of individual action annotations
     * @property screenWidth Actual screen width in pixels
     * @property screenHeight Actual screen height in pixels
     */
    data class BatchSteps(val steps: List<ActionAnnotation>, val screenWidth: Int, val screenHeight: Int) :
        ActionAnnotation()

    /**
     * No visual annotation needed.
     */
    data object None : ActionAnnotation()
}
