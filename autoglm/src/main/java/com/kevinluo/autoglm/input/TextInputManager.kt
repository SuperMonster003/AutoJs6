package com.kevinluo.autoglm.input

import android.util.Base64
import com.kevinluo.autoglm.IUserService
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Manages text input operations using AutoGLM Keyboard.
 *
 * This class handles text input by switching to AutoGLM Keyboard,
 * sending text via Base64-encoded broadcasts, and restoring the original
 * keyboard afterwards. It supports all Unicode characters including Chinese,
 * emoji, etc.
 *
 * IMPORTANT: The keyboard's BroadcastReceiver is only registered when the
 * keyboard view is created (in onCreateInputView). This happens when:
 * 1. The keyboard is set as default IME
 * 2. An input field has focus
 * 3. The soft keyboard is requested to show
 *
 * NOTE: The broadcast result check (result=-1 vs result=0) is NOT reliable because:
 * - The keyboard may process the broadcast even when result=0 is returned
 * - The receiver registration timing is unpredictable
 * - User testing confirmed text IS being input even when result=0
 *
 * Therefore, we send the broadcast and assume success. The agent will see the result
 * in the next screenshot and can retry if needed.
 *
 * @param userService The Shizuku UserService for executing shell commands
 *
 */
class TextInputManager(private val userService: IUserService) {
    /** Cached original IME for restoration after text input. */
    private var originalIme: String? = null

    /**
     * Types text into the currently focused input field.
     *
     * This method performs the following steps:
     * 1. Detects and switches to the best available keyboard
     * 2. Clears any existing text in the input field
     * 3. Inputs the new text via Base64-encoded broadcast
     * 4. Restores the original keyboard
     *
     * @param text The text to type (supports all Unicode including Chinese, emoji, etc.)
     * @return [InputResult] indicating success or failure with details
     *
     */
    suspend fun typeText(text: String): InputResult = withContext(Dispatchers.IO) {
        Logger.i(TAG, "typeText: '$text'")

        try {
            // Step 1: Switch to the best available keyboard
            val switchResult = switchToInputKeyboard()
            if (!switchResult.success) {
                return@withContext switchResult
            }

            // Step 2: Clear existing text
            clearText()
            delay(TEXT_CLEAR_DELAY_MS)

            // Step 3: Input new text
            // We don't check broadcast result because it's unreliable
            // The text may be input successfully even when result=0
            val result = inputTextViaB64(text)
            Logger.d(TAG, "Input broadcast result: $result")

            delay(TEXT_INPUT_DELAY_MS)

            // Step 4: Restore original keyboard
            restoreKeyboard()
            delay(KEYBOARD_RESTORE_DELAY_MS)

            // Always return success - the agent will verify via screenshot
            InputResult.success("输入: $text")
        } catch (e: Exception) {
            Logger.e(TAG, "typeText failed", e)
            // Try to restore keyboard even on failure
            try {
                restoreKeyboard()
            } catch (_: Exception) {
                // Ignore restoration errors during cleanup
            }
            InputResult.failure("输入文本失败: ${e.message}")
        }
    }

    /**
     * Switches to AutoGLM Keyboard for text input.
     *
     * Saves the current IME for later restoration.
     *
     * @return [InputResult] indicating success or failure of the switch operation
     *
     */
    private suspend fun switchToInputKeyboard(): InputResult {
        // Get current IME
        val currentIme = getCurrentIme()
        Logger.d(TAG, "Current IME: $currentIme")

        // Check if already using AutoGLM Keyboard
        if (KeyboardHelper.isAutoGLMKeyboard(currentIme)) {
            Logger.d(TAG, "Already using AutoGLM Keyboard")
            return InputResult.success("Using AutoGLM Keyboard")
        }

        // Save original IME
        originalIme = currentIme
        Logger.d(TAG, "Saved original IME: $originalIme")

        // List all enabled IMEs to debug
        val enabledImes = shell("ime list -s")
        Logger.d(TAG, "Enabled IMEs:\n$enabledImes")

        // Get the IME ID
        val imeId = KeyboardHelper.IME_ID
        Logger.d(TAG, "AutoGLM Keyboard IME ID: $imeId")

        // Enable and switch to AutoGLM Keyboard
        if (tryEnableKeyboard(imeId)) {
            Logger.i(TAG, "Switched to AutoGLM Keyboard")
            return InputResult.success("Switched to AutoGLM Keyboard")
        }

        // AutoGLM Keyboard not available
        Logger.e(TAG, "AutoGLM Keyboard not available")
        return InputResult.failure("AutoGLM Keyboard 未启用。请在系统设置中启用 AutoGLM Keyboard。")
    }

    /**
     * Tries to enable and switch to AutoGLM Keyboard.
     *
     * @param imeId The IME identifier to enable
     * @return true if successfully switched, false otherwise
     */
    private suspend fun tryEnableKeyboard(imeId: String): Boolean {
        // Enable the keyboard first (required before switching)
        Logger.d(TAG, "Enabling keyboard: $imeId")
        shell("ime enable $imeId")
        delay(IME_ENABLE_DELAY_MS)

        // Switch to the keyboard
        val result = shell("ime set $imeId")
        Logger.d(TAG, "Switch to $imeId result: $result")
        delay(KEYBOARD_SWITCH_DELAY_MS)

        // Verify switch
        val newIme = getCurrentIme()
        return KeyboardHelper.isAutoGLMKeyboard(newIme)
    }

    /**
     * Gets the current default input method.
     *
     * @return The current IME identifier string, or empty string if not found
     */
    private fun getCurrentIme(): String {
        val result = shell("settings get secure default_input_method")
        return result.lines().firstOrNull { it.isNotBlank() && !it.startsWith("[") } ?: ""
    }

    /**
     * Clears text in the currently focused input field.
     *
     * Sends a broadcast to the active keyboard to clear the current input field content.
     *
     * @return The broadcast result string
     */
    private fun clearText(): String {
        Logger.d(TAG, "Clearing text")
        return shell("am broadcast -a $ACTION_CLEAR_TEXT -p $PACKAGE_NAME")
    }

    /**
     * Inputs text using Base64-encoded broadcast.
     *
     * This method encodes the text in Base64 and sends it via broadcast to the
     * active keyboard. This approach supports all Unicode characters including
     * Chinese, emoji, etc.
     *
     * NOTE: On Android 14+, we must specify the package name for the broadcast
     * to be received by dynamically registered receivers.
     *
     * @param text The text to input
     * @return The broadcast result string
     */
    private fun inputTextViaB64(text: String): String {
        val encoded = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        Logger.d(TAG, "Input text via B64: '$text' -> '$encoded'")
        return shell("am broadcast -a $ACTION_INPUT_B64 -p $PACKAGE_NAME --es msg '$encoded'")
    }

    /**
     * Restores the original keyboard IME.
     *
     * If an original IME was saved during [switchToInputKeyboard], this method
     * restores it as the default input method.
     */
    private fun restoreKeyboard() {
        val ime = originalIme
        if (ime != null && ime.isNotBlank() && !ime.contains("null", ignoreCase = true)) {
            Logger.d(TAG, "Restoring keyboard: $ime")
            shell("ime set $ime")
        } else {
            Logger.d(TAG, "No original IME to restore")
        }
    }

    /**
     * Executes a shell command via Shizuku UserService.
     *
     * @param command The shell command to execute
     * @return The command output, or an error message if execution failed
     */
    private fun shell(command: String): String = try {
        userService.executeCommand(command)
    } catch (e: Exception) {
        Logger.e(TAG, "Shell command failed: $command", e)
        "Error: ${e.message}"
    }

    companion object {
        private const val TAG = "TextInputManager"

        // Broadcast actions
        private const val ACTION_INPUT_B64 = "ADB_INPUT_B64"
        private const val ACTION_CLEAR_TEXT = "ADB_CLEAR_TEXT"

        // Package name
        private const val PACKAGE_NAME = "com.kevinluo.autoglm"

        // Timing constants (increased for stability)
        // Wait after switching keyboard to ensure it's fully active
        private const val KEYBOARD_SWITCH_DELAY_MS = 1000L

        // Wait after enabling keyboard before switching
        private const val IME_ENABLE_DELAY_MS = 500L

        // Wait after clearing text to ensure field is empty
        private const val TEXT_CLEAR_DELAY_MS = 500L

        // Wait after inputting text to ensure it's committed
        private const val TEXT_INPUT_DELAY_MS = 500L

        // Wait after restoring keyboard before continuing
        private const val KEYBOARD_RESTORE_DELAY_MS = 500L
    }
}

/**
 * Result of a text input operation.
 *
 * This data class represents the outcome of a text input operation,
 * containing both a success flag and a descriptive message.
 *
 * @property success Whether the input operation succeeded
 * @property message A descriptive message about the operation result
 *
 */
data class InputResult(val success: Boolean, val message: String) {
    companion object {
        /**
         * Creates a successful input result.
         *
         * @param message A descriptive message about the successful operation
         * @return An [InputResult] with success=true
         */
        fun success(message: String) = InputResult(true, message)

        /**
         * Creates a failed input result.
         *
         * @param message A descriptive message about the failure
         * @return An [InputResult] with success=false
         */
        fun failure(message: String) = InputResult(false, message)
    }
}
