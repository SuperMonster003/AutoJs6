package com.kevinluo.autoglm.input

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.util.Base64
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import com.kevinluo.autoglm.R
import com.kevinluo.autoglm.util.Logger

/**
 * Built-in Input Method Service for AutoGLM text input.
 *
 * This IME provides a minimal keyboard interface that receives text input commands
 * via broadcasts. It provides text input functionality within the AutoGLM application.
 *
 * Features:
 * - Receives text via Base64-encoded broadcasts
 * - Supports all Unicode characters including Chinese, emoji, etc.
 * - Provides clear text functionality
 * - Minimal UI footprint - shows only a status indicator
 *
 * Broadcast Actions:
 * - [ACTION_INPUT_TEXT]: Input text (Base64 encoded in "msg" extra)
 * - [ACTION_INPUT_B64]: Input text (Base64 encoded)
 * - [ACTION_CLEAR_TEXT]: Clear current input field
 * - [ACTION_INPUT_CHARS]: Input text directly without encoding
 *
 * Usage:
 * 1. Enable the keyboard in system settings
 * 2. Switch to AutoGLM Keyboard when text input is needed
 * 3. Send broadcasts to input text programmatically
 *
 */
class AutoGLMKeyboardService : InputMethodService() {
    /**
     * BroadcastReceiver for handling text input commands.
     *
     * Listens for input and clear text broadcasts and processes them
     * through the InputConnection.
     */
    private val inputReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Logger.d(TAG, "Received broadcast: ${intent.action}")

                when (intent.action) {
                    ACTION_INPUT_TEXT, ACTION_INPUT_B64 -> {
                        handleInputText(intent)
                    }

                    ACTION_CLEAR_TEXT -> {
                        handleClearText()
                    }

                    ACTION_INPUT_CHARS -> {
                        handleInputChars(intent)
                    }
                }
            }
        }

    /**
     * Flag indicating whether the receiver is currently registered.
     */
    private var isReceiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "AutoGLMKeyboardService created")
        // Register receiver immediately when service is created
        registerInputReceiver()
    }

    override fun onDestroy() {
        Logger.i(TAG, "AutoGLMKeyboardService destroyed")
        unregisterInputReceiver()
        super.onDestroy()
    }

    private var switchButton: ImageButton? = null

    /**
     * Called when the keyboard view is created.
     *
     * Creates a minimal view showing the keyboard status. The actual text input
     * is handled via broadcasts, so no interactive keyboard UI is needed.
     *
     * @return The keyboard view to display
     */
    override fun onCreateInputView(): View {
        Logger.d(TAG, "onCreateInputView called")

        // Register receiver when input view is created
        registerInputReceiver()

        // Create a minimal status view
        val view = layoutInflater.inflate(R.layout.keyboard_autoglm, null)

        // Setup switch keyboard button
        switchButton =
            view.findViewById<ImageButton>(R.id.btn_switch_keyboard)?.apply {
                setOnClickListener { switchToNextKeyboard() }
            }

        // Update button visibility based on system UI
        updateSwitchButtonVisibility()

        return view
    }

    /**
     * Updates the visibility of the switch keyboard button.
     *
     * Only shows the button if:
     * 1. System doesn't show its own switch button (shouldOfferSwitchingToNextInputMethod)
     * 2. There are multiple input methods enabled
     *
     * Note: shouldOfferSwitchingToNextInputMethod requires API 28+.
     * On older devices, we always show the switch button.
     */
    private fun updateSwitchButtonVisibility() {
        val shouldShow =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                !shouldOfferSwitchingToNextInputMethod()
            } else {
                // On API < 28, always show the switch button
                true
            }
        Logger.d(TAG, "Switch button visibility: shouldShow=$shouldShow")
        switchButton?.visibility = if (shouldShow) View.VISIBLE else View.GONE
    }

    /**
     * Switches to the next input method (keyboard).
     *
     * This allows users to quickly switch to another keyboard without
     * going through system settings.
     */
    private fun switchToNextKeyboard() {
        Logger.d(TAG, "Switching to next keyboard")
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // For Android 9+, use switchToNextInputMethod
                switchToNextInputMethod(false)
            } else {
                // For older versions, show input method picker
                @Suppress("DEPRECATION")
                imm.switchToNextInputMethod(window?.window?.attributes?.token, false)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to switch keyboard", e)
            // Fallback: show input method picker dialog
            try {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            } catch (e2: Exception) {
                Logger.e(TAG, "Failed to show input method picker", e2)
            }
        }
    }

    /**
     * Called when the keyboard starts input on a new editor.
     *
     * @param editorInfo Information about the text field
     * @param restarting Whether this is a restart of input on the same editor
     */
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Logger.d(TAG, "onStartInput: restarting=$restarting, editorType=${attribute?.inputType}")
    }

    /**
     * Called when the keyboard view is shown.
     *
     * Ensures the broadcast receiver is registered when the keyboard becomes visible.
     *
     * @param info Information about the text field
     * @param restarting Whether this is a restart
     */
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Logger.d(TAG, "onStartInputView: restarting=$restarting")

        // Ensure receiver is registered
        registerInputReceiver()

        // Update switch button visibility (system state may have changed)
        updateSwitchButtonVisibility()
    }

    /**
     * Called when the keyboard view is hidden.
     *
     * @param finishingInput Whether input is finishing
     */
    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        Logger.d(TAG, "onFinishInputView: finishingInput=$finishingInput")
    }

    /**
     * Called when input is finished.
     */
    override fun onFinishInput() {
        super.onFinishInput()
        Logger.d(TAG, "onFinishInput")
    }

    /**
     * Registers the broadcast receiver for input commands.
     *
     * Uses appropriate registration method based on Android version.
     */
    private fun registerInputReceiver() {
        if (isReceiverRegistered) {
            Logger.d(TAG, "Receiver already registered")
            return
        }

        val filter =
            IntentFilter().apply {
                addAction(ACTION_INPUT_TEXT)
                addAction(ACTION_INPUT_B64)
                addAction(ACTION_CLEAR_TEXT)
                addAction(ACTION_INPUT_CHARS)
            }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(inputReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(inputReceiver, filter)
            }
            isReceiverRegistered = true
            Logger.i(TAG, "Input receiver registered")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to register input receiver", e)
        }
    }

    /**
     * Unregisters the broadcast receiver.
     */
    private fun unregisterInputReceiver() {
        if (!isReceiverRegistered) {
            return
        }

        try {
            unregisterReceiver(inputReceiver)
            isReceiverRegistered = false
            Logger.i(TAG, "Input receiver unregistered")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to unregister input receiver", e)
        }
    }

    /**
     * Handles Base64-encoded text input broadcast.
     *
     * Decodes the Base64 text and commits it to the current input connection.
     *
     * @param intent The broadcast intent containing the encoded text
     */
    private fun handleInputText(intent: Intent) {
        val encodedText =
            intent.getStringExtra(EXTRA_MSG) ?: run {
                Logger.w(TAG, "No text in input broadcast")
                return
            }

        try {
            val decodedBytes = Base64.decode(encodedText, Base64.DEFAULT)
            val text = String(decodedBytes, Charsets.UTF_8)
            Logger.d(TAG, "Decoded text: '${text.take(50)}${if (text.length > 50) "..." else ""}'")

            commitText(text)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to decode Base64 text", e)
        }
    }

    /**
     * Handles direct text input broadcast (without encoding).
     *
     * @param intent The broadcast intent containing the text
     */
    private fun handleInputChars(intent: Intent) {
        val text =
            intent.getStringExtra(EXTRA_MSG) ?: run {
                Logger.w(TAG, "No text in input chars broadcast")
                return
            }

        Logger.d(TAG, "Input chars: '${text.take(50)}${if (text.length > 50) "..." else ""}'")
        commitText(text)
    }

    /**
     * Handles clear text broadcast.
     *
     * Selects all text in the current input field and deletes it.
     */
    private fun handleClearText() {
        Logger.d(TAG, "Clearing text")

        val ic =
            currentInputConnection ?: run {
                Logger.w(TAG, "No input connection for clear text")
                return
            }

        try {
            // Perform select all
            ic.performContextMenuAction(android.R.id.selectAll)

            // Delete selected text by committing empty string
            ic.commitText("", 0)

            Logger.d(TAG, "Text cleared successfully")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to clear text", e)
        }
    }

    /**
     * Commits text to the current input connection.
     *
     * @param text The text to commit
     */
    private fun commitText(text: String) {
        val ic =
            currentInputConnection ?: run {
                Logger.w(TAG, "No input connection for commit text")
                return
            }

        try {
            ic.commitText(text, 1)
            Logger.d(TAG, "Text committed successfully")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to commit text", e)
        }
    }

    companion object {
        private const val TAG = "AutoGLMKeyboard"

        /**
         * Broadcast action for Base64-encoded text input.
         */
        const val ACTION_INPUT_B64 = "ADB_INPUT_B64"

        /**
         * Broadcast action for Base64-encoded text input (AutoGLM specific).
         */
        const val ACTION_INPUT_TEXT = "AUTOGLM_INPUT_TEXT"

        /**
         * Broadcast action for clearing text.
         */
        const val ACTION_CLEAR_TEXT = "ADB_CLEAR_TEXT"

        /**
         * Broadcast action for direct text input without encoding.
         */
        const val ACTION_INPUT_CHARS = "AUTOGLM_INPUT_CHARS"

        /**
         * Extra key for the text message in broadcasts.
         */
        const val EXTRA_MSG = "msg"
    }
}
