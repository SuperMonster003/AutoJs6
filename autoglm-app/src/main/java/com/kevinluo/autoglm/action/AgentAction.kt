package com.kevinluo.autoglm.action

/**
 * Sealed class hierarchy representing all possible agent actions.
 *
 * These actions are parsed from the AutoGLM model responses and executed
 * by [ActionHandler]. Each action type represents a specific device operation
 * or control flow command.
 *
 * The sealed class pattern ensures exhaustive handling of all action types
 * and provides type-safe action processing.
 *
 */
sealed class AgentAction {
    /**
     * Tap at specified coordinates.
     *
     * @property x Relative X coordinate (0-999)
     * @property y Relative Y coordinate (0-999)
     * @property message Optional message for sensitive operation confirmation
     */
    data class Tap(val x: Int, val y: Int, val message: String? = null) : AgentAction()

    /**
     * Swipe from start to end coordinates.
     *
     * @property startX Relative start X coordinate (0-999)
     * @property startY Relative start Y coordinate (0-999)
     * @property endX Relative end X coordinate (0-999)
     * @property endY Relative end Y coordinate (0-999)
     * @property humanized Whether to use humanized swipe with curved path
     */
    data class Swipe(val startX: Int, val startY: Int, val endX: Int, val endY: Int, val humanized: Boolean = true) :
        AgentAction()

    /**
     * Type text into the current input field.
     *
     * @property text The text to type
     */
    data class Type(val text: String) : AgentAction()

    /**
     * Type a person's name (same behavior as [Type]).
     *
     * @property text The name to type
     */
    data class TypeName(val text: String) : AgentAction()

    /**
     * Launch an app by name or package name.
     *
     * @property app App display name or package name
     */
    data class Launch(val app: String) : AgentAction()

    /**
     * List all installed apps on the device.
     *
     * Returns a list of app names and package names when executed.
     */
    data object ListApps : AgentAction()

    /**
     * Press the back button (KEYCODE_BACK).
     */
    data object Back : AgentAction()

    /**
     * Press the home button (KEYCODE_HOME).
     */
    data object Home : AgentAction()

    /**
     * Press the volume up button (KEYCODE_VOLUME_UP).
     */
    data object VolumeUp : AgentAction()

    /**
     * Press the volume down button (KEYCODE_VOLUME_DOWN).
     */
    data object VolumeDown : AgentAction()

    /**
     * Press the power button (KEYCODE_POWER).
     */
    data object Power : AgentAction()

    /**
     * Long press at specified coordinates.
     *
     * @property x Relative X coordinate (0-999)
     * @property y Relative Y coordinate (0-999)
     * @property durationMs Duration of the long press in milliseconds
     */
    data class LongPress(val x: Int, val y: Int, val durationMs: Int = 3000) : AgentAction()

    /**
     * Double tap at specified coordinates.
     *
     * @property x Relative X coordinate (0-999)
     * @property y Relative Y coordinate (0-999)
     */
    data class DoubleTap(val x: Int, val y: Int) : AgentAction()

    /**
     * Wait for a specified duration.
     *
     * @property durationSeconds Duration to wait in seconds
     */
    data class Wait(val durationSeconds: Float) : AgentAction()

    /**
     * Request user takeover for manual intervention.
     *
     * @property message Message to display to the user explaining why takeover is needed
     */
    data class TakeOver(val message: String) : AgentAction()

    /**
     * Request user interaction to choose from options.
     *
     * @property options List of options for the user to choose from, or null for free-form input
     */
    data class Interact(val options: List<String>? = null) : AgentAction()

    /**
     * Record a note about the current page content.
     *
     * @property message The note content to record
     */
    data class Note(val message: String) : AgentAction()

    /**
     * Call API for summarization or commenting.
     *
     * @property instruction The instruction for the API call
     */
    data class CallApi(val instruction: String) : AgentAction()

    /**
     * Finish the task with a message.
     *
     * @property message The completion message describing the result
     */
    data class Finish(val message: String) : AgentAction()

    /**
     * Batch execute multiple actions in sequence.
     *
     * Useful for multi-step operations like typing digits on a custom keypad.
     *
     * @property steps List of actions to execute in order
     * @property delayMs Delay between each action in milliseconds (default 500ms)
     */
    data class Batch(val steps: List<AgentAction>, val delayMs: Int = 500) : AgentAction()

    /**
     * Formats the action for display in UI.
     *
     * @return Human-readable description of the action
     */
    fun formatForDisplay(): String = when (this) {
        is Tap -> "点击 ($x, $y)"
        is Swipe -> "滑动 从($startX, $startY) 到($endX, $endY)"
        is Type -> "输入: \"${text.take(30)}${if (text.length > 30) "..." else ""}\""
        is TypeName -> "输入名称: \"$text\""
        is Launch -> "启动: $app"
        is ListApps -> "列出已安装应用"
        is Back -> "返回"
        is Home -> "主页"
        is VolumeUp -> "音量+"
        is VolumeDown -> "音量-"
        is Power -> "电源键"
        is LongPress -> "长按 ($x, $y)"
        is DoubleTap -> "双击 ($x, $y)"
        is Wait -> "等待 ${durationSeconds}秒"
        is TakeOver -> "手动接管"
        is Interact -> "用户交互"
        is Note -> "备注: ${message.take(30)}${if (message.length > 30) "..." else ""}"
        is CallApi -> "API调用"
        is Finish -> "完成: ${message.take(30)}${if (message.length > 30) "..." else ""}"
        is Batch -> "批量操作: ${steps.size}步 (间隔${delayMs}ms)"
    }
}

/**
 * Result of an action execution.
 *
 * @property success Whether the action executed successfully
 * @property shouldFinish Whether the task should finish after this action
 * @property message Optional message describing the result
 * @property refreshFloatingWindow Whether to refresh the floating window after this action
 */
data class ActionResult(
    val success: Boolean,
    val shouldFinish: Boolean,
    val message: String? = null,
    val refreshFloatingWindow: Boolean = false,
)
