package com.kevinluo.autoglm.input

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import com.kevinluo.autoglm.util.Logger

/**
 * Helper class for keyboard-related operations.
 *
 * Provides utilities for checking AutoGLM Keyboard availability,
 * enabling the keyboard, and navigating to keyboard settings.
 *
 */
object KeyboardHelper {
    private const val TAG = "KeyboardHelper"

    /** AutoGLM package name. */
    private const val PACKAGE_NAME = "com.kevinluo.autoglm"

    /** AutoGLM Keyboard IME ID (Android system format). */
    const val IME_ID = "$PACKAGE_NAME/.input.AutoGLMKeyboardService"

    /**
     * Checks if the given IME ID belongs to AutoGLM Keyboard.
     */
    fun isAutoGLMKeyboard(imeId: String): Boolean = imeId.startsWith("$PACKAGE_NAME/")

    /**
     * Keyboard status enumeration.
     */
    enum class KeyboardStatus {
        /** Keyboard is enabled and ready to use. */
        ENABLED,

        /** Keyboard is installed but not enabled in system settings. */
        NOT_ENABLED,
    }

    /**
     * Checks the status of AutoGLM Keyboard.
     *
     * @param context Application context
     * @return [KeyboardStatus] indicating the keyboard's current state
     */
    fun getAutoGLMKeyboardStatus(context: Context): KeyboardStatus {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledInputMethods = imm.enabledInputMethodList

        Logger.d(TAG, "Looking for keyboard: package=$PACKAGE_NAME")

        for (ime in enabledInputMethods) {
            Logger.d(TAG, "Found IME: package=${ime.packageName}, service=${ime.serviceName}")
            if (ime.packageName == PACKAGE_NAME &&
                ime.serviceName.endsWith(".AutoGLMKeyboardService")
            ) {
                Logger.d(TAG, "AutoGLM Keyboard is enabled")
                return KeyboardStatus.ENABLED
            }
        }

        Logger.d(TAG, "AutoGLM Keyboard is not enabled")
        return KeyboardStatus.NOT_ENABLED
    }

    /**
     * Checks if AutoGLM Keyboard is available for use.
     *
     * @param context Application context
     * @return true if AutoGLM Keyboard is enabled
     */
    fun isKeyboardAvailable(context: Context): Boolean = getAutoGLMKeyboardStatus(context) == KeyboardStatus.ENABLED

    /**
     * Gets a human-readable status message for keyboard availability.
     *
     * @param context Application context
     * @return Status message describing keyboard availability
     */
    fun getKeyboardStatusMessage(context: Context): String = when (getAutoGLMKeyboardStatus(context)) {
        KeyboardStatus.ENABLED -> "AutoGLM Keyboard 已启用"
        KeyboardStatus.NOT_ENABLED -> "请启用 AutoGLM Keyboard"
    }

    /**
     * Opens the system input method settings.
     *
     * @param context Application context
     */
    fun openInputMethodSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Logger.d(TAG, "Opened input method settings")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to open input method settings", e)
        }
    }

    /**
     * Opens the input method picker dialog.
     *
     * @param context Application context
     */
    fun showInputMethodPicker(context: Context) {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
            Logger.d(TAG, "Showed input method picker")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to show input method picker", e)
        }
    }
}
