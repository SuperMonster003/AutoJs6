package com.kevinluo.autoglm.action

import com.kevinluo.autoglm.app.AppResolver
import com.kevinluo.autoglm.device.DeviceExecutor
import com.kevinluo.autoglm.input.TextInputManager
import com.kevinluo.autoglm.screenshot.FloatingWindowController
import com.kevinluo.autoglm.util.CoordinateConverter
import com.kevinluo.autoglm.util.ErrorHandler
import com.kevinluo.autoglm.util.HumanizedSwipeGenerator
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.delay

/**
 * Handles execution of agent actions by coordinating with DeviceExecutor,
 * AppResolver, HumanizedSwipeGenerator, and TextInputManager.
 *
 * This class is responsible for translating high-level [AgentAction] commands
 * into device-level operations. It manages floating window visibility during
 * touch operations to prevent interference.
 *
 * @param deviceExecutor Executor for device-level operations (tap, swipe, etc.)
 * @param appResolver Resolver for app name to package name mapping
 * @param swipeGenerator Generator for humanized swipe paths
 * @param textInputManager Manager for text input operations
 * @param floatingWindowProvider Optional provider for floating window controller
 *
 */
class ActionHandler(
    private val deviceExecutor: DeviceExecutor,
    private val appResolver: AppResolver,
    private val swipeGenerator: HumanizedSwipeGenerator,
    private val textInputManager: TextInputManager,
    private val floatingWindowProvider: (() -> FloatingWindowController?)? = null,
) {
    /**
     * Callback interface for sensitive operation confirmation.
     *
     * Implementations handle user confirmation dialogs, takeover requests,
     * and interactive option selection.
     */
    interface ConfirmationCallback {
        /**
         * Called when a sensitive operation requires user confirmation.
         *
         * @param message The confirmation message to display
         * @return true if the user confirms, false otherwise
         */
        suspend fun onConfirmationRequired(message: String): Boolean

        /**
         * Called when the agent requests manual user takeover.
         *
         * @param message The takeover request message
         */
        suspend fun onTakeOverRequested(message: String)

        /**
         * Called when user interaction is required to select from options.
         *
         * @param options List of options for the user to choose from
         * @return The index of the selected option, or -1 if cancelled
         */
        suspend fun onInteractionRequired(options: List<String>?): Int
    }

    private var confirmationCallback: ConfirmationCallback? = null

    /**
     * Sets the callback for handling confirmation dialogs and user interactions.
     *
     * @param callback The callback implementation, or null to remove
     */
    fun setConfirmationCallback(callback: ConfirmationCallback?) {
        confirmationCallback = callback
    }

    /**
     * Hides the floating window before touch operations to prevent it from intercepting touches.
     *
     * This method should be called before any touch-based operation (tap, swipe, etc.)
     * to ensure the floating window doesn't intercept the touch events.
     */
    private suspend fun hideFloatingWindow() {
        floatingWindowProvider?.invoke()?.hide()
        delay(WINDOW_HIDE_DELAY_MS)
    }

    /**
     * Shows the floating window after touch operations complete.
     *
     * This method should be called after touch-based operations complete
     * to restore the floating window visibility.
     */
    private suspend fun showFloatingWindow() {
        delay(WINDOW_SHOW_DELAY_MS)
        floatingWindowProvider?.invoke()?.show()
    }

    /**
     * Executes an agent action on the device.
     *
     * This is the main entry point for action execution. It dispatches the action
     * to the appropriate handler method based on the action type.
     *
     * @param action The action to execute
     * @param screenWidth Current screen width in pixels
     * @param screenHeight Current screen height in pixels
     * @return The result of the action execution
     *
     */
    suspend fun execute(action: AgentAction, screenWidth: Int, screenHeight: Int): ActionResult {
        Logger.logAction(action::class.simpleName ?: "Unknown", "Executing on ${screenWidth}x$screenHeight")

        return try {
            val result =
                when (action) {
                    is AgentAction.Tap -> executeTap(action, screenWidth, screenHeight)
                    is AgentAction.Swipe -> executeSwipe(action, screenWidth, screenHeight)
                    is AgentAction.Type -> executeType(action)
                    is AgentAction.TypeName -> executeTypeName(action)
                    is AgentAction.Launch -> executeLaunch(action)
                    is AgentAction.ListApps -> executeListApps()
                    is AgentAction.Back -> executeBack()
                    is AgentAction.Home -> executeHome()
                    is AgentAction.VolumeUp -> executeVolumeUp()
                    is AgentAction.VolumeDown -> executeVolumeDown()
                    is AgentAction.Power -> executePower()
                    is AgentAction.LongPress -> executeLongPress(action, screenWidth, screenHeight)
                    is AgentAction.DoubleTap -> executeDoubleTap(action, screenWidth, screenHeight)
                    is AgentAction.Wait -> executeWait(action)
                    is AgentAction.TakeOver -> executeTakeOver(action)
                    is AgentAction.Interact -> executeInteract(action)
                    is AgentAction.Note -> executeNote(action)
                    is AgentAction.CallApi -> executeCallApi(action)
                    is AgentAction.Finish -> executeFinish(action)
                    is AgentAction.Batch -> executeBatch(action, screenWidth, screenHeight)
                }

            Logger.d(TAG, "Action result: success=${result.success}, message=${result.message}")
            result
        } catch (e: Exception) {
            val handledError =
                ErrorHandler.handleActionError(
                    action::class.simpleName ?: "Unknown",
                    e.message ?: "Unknown error",
                    e,
                )
            Logger.e(TAG, ErrorHandler.formatErrorForLog(handledError), e)
            ActionResult(false, false, handledError.userMessage)
        }
    }

    /**
     * Executes a Tap action.
     * Hides floating window before tap to prevent touch interception.
     * Uses try-finally to ensure floating window is restored even if tap fails.
     */
    private suspend fun executeTap(action: AgentAction.Tap, screenWidth: Int, screenHeight: Int): ActionResult {
        // Handle sensitive operation confirmation
        if (action.message != null) {
            val confirmed = confirmationCallback?.onConfirmationRequired(action.message) ?: true
            if (!confirmed) {
                return ActionResult(true, false, "点击已被用户取消")
            }
        }

        val (absX, absY) =
            CoordinateConverter.toAbsolute(
                action.x,
                action.y,
                screenWidth,
                screenHeight,
            )

        // Hide floating window to prevent touch interception
        hideFloatingWindow()

        return try {
            val result = deviceExecutor.tap(absX, absY)
            if (isDeviceExecutorError(result)) {
                Logger.w(TAG, "Tap command failed: $result")
                ActionResult(false, false, "点击失败: $result")
            } else {
                ActionResult(true, false, "点击 ($absX, $absY)")
            }
        } finally {
            // Always show floating window after tap, even if tap fails
            showFloatingWindow()
        }
    }

    /**
     * Executes a Swipe action.
     * Hides floating window before swipe to prevent touch interception.
     * Uses try-finally to ensure floating window is restored even if swipe fails.
     */
    private suspend fun executeSwipe(action: AgentAction.Swipe, screenWidth: Int, screenHeight: Int): ActionResult {
        val (startAbsX, startAbsY) =
            CoordinateConverter.toAbsolute(
                action.startX,
                action.startY,
                screenWidth,
                screenHeight,
            )
        val (endAbsX, endAbsY) =
            CoordinateConverter.toAbsolute(
                action.endX,
                action.endY,
                screenWidth,
                screenHeight,
            )

        val swipePath =
            if (action.humanized) {
                swipeGenerator.generatePath(
                    startAbsX,
                    startAbsY,
                    endAbsX,
                    endAbsY,
                    screenWidth,
                    screenHeight,
                )
            } else {
                swipeGenerator.generateLinearPath(
                    startAbsX,
                    startAbsY,
                    endAbsX,
                    endAbsY,
                    screenWidth,
                    screenHeight,
                )
            }

        // Hide floating window to prevent touch interception
        hideFloatingWindow()

        return try {
            val result = deviceExecutor.swipe(swipePath.points, swipePath.durationMs)

            // Wait for swipe animation to complete before showing floating window
            // The swipe command returns immediately, but the gesture takes time
            delay(swipePath.durationMs.toLong() + 100)

            if (isDeviceExecutorError(result)) {
                Logger.w(TAG, "Swipe command failed: $result")
                ActionResult(false, false, "滑动失败: $result")
            } else {
                ActionResult(true, false, "滑动 从($startAbsX, $startAbsY) 到($endAbsX, $endAbsY)")
            }
        } finally {
            // Always show floating window after swipe, even if swipe fails
            showFloatingWindow()
        }
    }

    /**
     * Executes a Type action.
     *
     * IMPORTANT: We must hide the floating window before typing to ensure:
     * 1. The target app's input field has focus
     * 2. AutoGLM Keyboard's onCreateInputView() is called
     * 3. The BroadcastReceiver is registered
     *
     * Uses try-finally to ensure floating window is restored even if typing fails.
     */
    private suspend fun executeType(action: AgentAction.Type): ActionResult {
        // Hide floating window to ensure target app has focus
        hideFloatingWindow()

        return try {
            // Small delay to let the system settle focus
            delay(200)

            val result = textInputManager.typeText(action.text)
            ActionResult(result.success, false, result.message)
        } finally {
            // Always show floating window after typing, even if typing fails
            showFloatingWindow()
        }
    }

    /**
     * Executes a TypeName action (same as Type).
     * Uses try-finally to ensure floating window is restored even if typing fails.
     */
    private suspend fun executeTypeName(action: AgentAction.TypeName): ActionResult {
        // Hide floating window to ensure target app has focus
        hideFloatingWindow()

        return try {
            // Small delay to let the system settle focus
            delay(200)

            val result = textInputManager.typeText(action.text)
            ActionResult(result.success, false, "输入名称: ${action.text}")
        } finally {
            // Always show floating window after typing, even if typing fails
            showFloatingWindow()
        }
    }

    /**
     * Executes a Launch action.
     * If package name is found, launches directly and checks for errors.
     * If not found, returns success with a message instructing the model
     * to find the app icon on screen (home screen or app drawer).
     *
     */
    private suspend fun executeLaunch(action: AgentAction.Launch): ActionResult {
        Logger.d(TAG, "Launching app: ${action.app}")

        val packageName =
            if (action.app.contains('.')) {
                // Looks like a package name, use directly
                Logger.d(TAG, "Using package name directly: ${action.app}")
                action.app
            } else {
                // Resolve app name to package name
                Logger.d(TAG, "Resolving app name: ${action.app}")
                appResolver.resolvePackageName(action.app)
            }

        return if (packageName != null) {
            Logger.i(TAG, "Launching package: $packageName")
            val launchResult = deviceExecutor.launchApp(packageName)

            if (isDeviceExecutorError(launchResult)) {
                Logger.w(TAG, "Launch failed for $packageName: $launchResult")
                // Launch failed - instruct model to find app icon on screen
                deviceExecutor.pressKey(DeviceExecutor.KEYCODE_HOME)
                ActionResult(
                    // Operation itself succeeded, just app not found
                    success = true,
                    shouldFinish = false,
                    message = "启动应用'$packageName'失败，已返回主屏幕。请在主屏幕或应用列表中查找并点击'${action.app}'应用图标来启动它。",
                )
            } else {
                ActionResult(true, false, "启动应用: $packageName")
            }
        } else {
            // Package not found - instruct model to find app icon on screen
            Logger.i(TAG, "Package not found for '${action.app}', instructing model to find app icon on screen")
            // Press Home first to go to home screen
            deviceExecutor.pressKey(DeviceExecutor.KEYCODE_HOME)
            ActionResult(
                success = true,
                shouldFinish = false,
                message = "找不到应用包名'${action.app}'，已返回主屏幕。请在主屏幕或应用列表中查找并点击'${action.app}'应用图标来启动它。如果主屏幕没有，请上滑打开应用列表查找。",
            )
        }
    }

    /**
     * Executes a ListApps action.
     * Returns a list of all installed launchable apps with their names and package names.
     */
    private suspend fun executeListApps(): ActionResult {
        Logger.d(TAG, "Listing all installed apps")

        val apps = appResolver.getAllLaunchableApps()

        if (apps.isEmpty()) {
            return ActionResult(true, false, "未找到已安装的应用")
        }

        // Format app list for display
        val appListStr =
            buildString {
                appendLine("已安装的应用列表 (共${apps.size}个):")
                appendLine()
                apps.sortedBy { it.displayName.lowercase() }.forEach { app ->
                    appendLine("• ${app.displayName}")
                    appendLine("  包名: ${app.packageName}")
                }
            }

        Logger.i(TAG, "Found ${apps.size} installed apps")
        return ActionResult(true, false, appListStr)
    }

    /**
     * Executes a Back action.
     * First dismisses the soft keyboard (if shown) to ensure the back action
     * actually navigates back instead of just closing the keyboard.
     */
    private suspend fun executeBack(): ActionResult {
        // First, dismiss keyboard with ESCAPE key to ensure Back actually navigates
        // If keyboard is shown, the first Back would just close it
        deviceExecutor.pressKey(KEYCODE_ESCAPE)
        delay(100)

        // Now press Back to navigate
        val result = deviceExecutor.pressKey(DeviceExecutor.KEYCODE_BACK)
        return if (isDeviceExecutorError(result)) {
            Logger.w(TAG, "Back key press failed: $result")
            ActionResult(false, false, "返回键失败: $result")
        } else {
            ActionResult(true, false, "返回")
        }
    }

    /**
     * Executes a Home action.
     */
    private suspend fun executeHome(): ActionResult {
        val result = deviceExecutor.pressKey(DeviceExecutor.KEYCODE_HOME)
        return if (isDeviceExecutorError(result)) {
            Logger.w(TAG, "Home key press failed: $result")
            ActionResult(false, false, "主页键失败: $result")
        } else {
            ActionResult(true, false, "主页")
        }
    }

    /**
     * Executes a VolumeUp action.
     */
    private suspend fun executeVolumeUp(): ActionResult {
        val result = deviceExecutor.pressKey(DeviceExecutor.KEYCODE_VOLUME_UP)
        return if (isDeviceExecutorError(result)) {
            Logger.w(TAG, "Volume up key press failed: $result")
            ActionResult(false, false, "音量+键失败: $result")
        } else {
            ActionResult(true, false, "音量+")
        }
    }

    /**
     * Executes a VolumeDown action.
     */
    private suspend fun executeVolumeDown(): ActionResult {
        val result = deviceExecutor.pressKey(DeviceExecutor.KEYCODE_VOLUME_DOWN)
        return if (isDeviceExecutorError(result)) {
            Logger.w(TAG, "Volume down key press failed: $result")
            ActionResult(false, false, "音量-键失败: $result")
        } else {
            ActionResult(true, false, "音量-")
        }
    }

    /**
     * Executes a Power action.
     */
    private suspend fun executePower(): ActionResult {
        val result = deviceExecutor.pressKey(DeviceExecutor.KEYCODE_POWER)
        return if (isDeviceExecutorError(result)) {
            Logger.w(TAG, "Power key press failed: $result")
            ActionResult(false, false, "电源键失败: $result")
        } else {
            ActionResult(true, false, "电源键")
        }
    }

    /**
     * Executes a LongPress action.
     * Hides floating window before long press to prevent touch interception.
     * Uses try-finally to ensure floating window is restored even if long press fails.
     */
    private suspend fun executeLongPress(
        action: AgentAction.LongPress,
        screenWidth: Int,
        screenHeight: Int,
    ): ActionResult {
        val (absX, absY) =
            CoordinateConverter.toAbsolute(
                action.x,
                action.y,
                screenWidth,
                screenHeight,
            )

        // Hide floating window to prevent touch interception
        hideFloatingWindow()

        return try {
            val result = deviceExecutor.longPress(absX, absY, action.durationMs)

            // Wait for long press to complete before showing floating window
            // The command returns immediately, but the gesture takes time
            delay(action.durationMs.toLong() + 100)

            if (isDeviceExecutorError(result)) {
                Logger.w(TAG, "Long press command failed: $result")
                ActionResult(false, false, "长按失败: $result")
            } else {
                ActionResult(true, false, "长按 ($absX, $absY) ${action.durationMs}毫秒")
            }
        } finally {
            // Always show floating window after long press, even if it fails
            showFloatingWindow()
        }
    }

    /**
     * Executes a DoubleTap action.
     * Hides floating window before double tap to prevent touch interception.
     * Uses try-finally to ensure floating window is restored even if double tap fails.
     */
    private suspend fun executeDoubleTap(
        action: AgentAction.DoubleTap,
        screenWidth: Int,
        screenHeight: Int,
    ): ActionResult {
        val (absX, absY) =
            CoordinateConverter.toAbsolute(
                action.x,
                action.y,
                screenWidth,
                screenHeight,
            )

        // Hide floating window to prevent touch interception
        hideFloatingWindow()

        return try {
            val result = deviceExecutor.doubleTap(absX, absY)
            if (isDeviceExecutorError(result)) {
                Logger.w(TAG, "Double tap command failed: $result")
                ActionResult(false, false, "双击失败: $result")
            } else {
                ActionResult(true, false, "双击 ($absX, $absY)")
            }
        } finally {
            // Always show floating window after double tap, even if it fails
            showFloatingWindow()
        }
    }

    /**
     * Executes a Wait action.
     */
    private suspend fun executeWait(action: AgentAction.Wait): ActionResult {
        val durationMs = (action.durationSeconds * 1000).toLong()
        delay(durationMs)
        return ActionResult(true, false, "等待 ${action.durationSeconds}秒")
    }

    /**
     * Executes a TakeOver action.
     */
    private suspend fun executeTakeOver(action: AgentAction.TakeOver): ActionResult {
        confirmationCallback?.onTakeOverRequested(action.message)
        return ActionResult(true, false, "请求手动接管: ${action.message}")
    }

    /**
     * Executes an Interact action.
     */
    private suspend fun executeInteract(action: AgentAction.Interact): ActionResult {
        val selectedIndex = confirmationCallback?.onInteractionRequired(action.options) ?: -1
        return if (selectedIndex >= 0) {
            val selectedOption = action.options?.getOrNull(selectedIndex) ?: "选项 $selectedIndex"
            ActionResult(true, false, "用户选择: $selectedOption")
        } else {
            ActionResult(true, false, "交互已取消")
        }
    }

    /**
     * Executes a Note action.
     */
    private suspend fun executeNote(action: AgentAction.Note): ActionResult {
        // Note action just records the message, no device operation needed
        return ActionResult(true, false, "备注: ${action.message}")
    }

    /**
     * Executes a CallApi action.
     */
    private suspend fun executeCallApi(action: AgentAction.CallApi): ActionResult {
        // CallApi action is handled by the agent layer, not device operations
        return ActionResult(true, false, "API调用: ${action.instruction}")
    }

    /**
     * Executes a Finish action.
     */
    private suspend fun executeFinish(action: AgentAction.Finish): ActionResult =
        ActionResult(true, true, action.message)

    /**
     * Executes a Batch action - multiple actions in sequence with delay between each.
     * Useful for multi-step operations like typing digits on a custom numeric keypad.
     */
    private suspend fun executeBatch(action: AgentAction.Batch, screenWidth: Int, screenHeight: Int): ActionResult {
        val results = mutableListOf<String>()
        var allSuccess = true

        Logger.d(TAG, "Executing batch with ${action.steps.size} steps, ${action.delayMs}ms delay")

        for ((index, step) in action.steps.withIndex()) {
            // Don't allow nested Batch actions to prevent infinite recursion
            if (step is AgentAction.Batch) {
                Logger.w(TAG, "Skipping nested Batch action at step $index")
                continue
            }

            // Don't allow Finish in batch - it should be a separate action
            if (step is AgentAction.Finish) {
                Logger.w(TAG, "Skipping Finish action in batch at step $index")
                continue
            }

            Logger.d(TAG, "Batch step ${index + 1}/${action.steps.size}: ${step.formatForDisplay()}")

            val result = execute(step, screenWidth, screenHeight)
            results.add("Step ${index + 1}: ${result.message ?: "OK"}")

            if (!result.success) {
                allSuccess = false
                Logger.w(TAG, "Batch step ${index + 1} failed: ${result.message}")
            }

            // Delay between steps (except after the last step)
            if (index < action.steps.size - 1) {
                delay(action.delayMs.toLong())
            }
        }

        val allSucceededMsg = if (allSuccess) "all succeeded" else "some failed"
        val summary = "Batch completed: ${action.steps.size} steps, $allSucceededMsg"
        Logger.d(TAG, summary)

        return ActionResult(
            success = allSuccess,
            shouldFinish = false,
            message = summary,
        )
    }

    /**
     * Generates the shell commands that would be executed for a Type action.
     * Useful for testing and verification.
     *
     * @param text The text to type
     * @return List of shell commands (clear + input)
     */
    fun generateTypeCommands(text: String): List<String> = listOf(
        // Placeholder for clear operation
        "clear_text",
        "input text '$text'",
    )

    companion object {
        private const val TAG = "ActionHandler"
        private const val WINDOW_HIDE_DELAY_MS = 100L
        private const val WINDOW_SHOW_DELAY_MS = 50L
        private const val KEYCODE_ESCAPE = 111

        /**
         * Checks if a DeviceExecutor result indicates an error.
         *
         * @param result The result string from DeviceExecutor
         * @return true if the result indicates an error, false otherwise
         */
        fun isDeviceExecutorError(result: String): Boolean = result.contains("Error", ignoreCase = true) ||
            result.contains("Exception", ignoreCase = true) ||
            result.contains("failed", ignoreCase = true) ||
            result.contains("permission denied", ignoreCase = true) ||
            result.contains("not found", ignoreCase = true) ||
            result.contains("does not exist", ignoreCase = true)
    }
}
