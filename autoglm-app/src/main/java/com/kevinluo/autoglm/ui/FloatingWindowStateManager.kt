package com.kevinluo.autoglm.ui

import android.content.Context
import android.content.Intent
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Enum representing the floating window visibility state.
 */
enum class FloatingWindowState {
    /** Window is hidden and should not be shown. */
    HIDDEN,

    /** Window should be visible when app is in background. */
    VISIBLE_WHEN_BACKGROUND,

    /** Window is forced visible (e.g., during task execution). */
    FORCED_VISIBLE,
}

/**
 * Singleton state machine for managing floating window visibility.
 *
 * Centralizes all floating window show/hide logic to avoid state conflicts
 * from multiple components trying to control the window simultaneously.
 *
 * State transitions:
 * - HIDDEN -> VISIBLE_WHEN_BACKGROUND: User enables floating window
 * - HIDDEN -> FORCED_VISIBLE: Task starts executing
 * - VISIBLE_WHEN_BACKGROUND -> HIDDEN: User disables floating window
 * - VISIBLE_WHEN_BACKGROUND -> FORCED_VISIBLE: Task starts executing
 * - FORCED_VISIBLE -> VISIBLE_WHEN_BACKGROUND: Task completes
 * - FORCED_VISIBLE -> HIDDEN: Task completes and user had disabled window
 */
object FloatingWindowStateManager {
    private const val TAG = "FloatingWindowState"

    private val _state = MutableStateFlow(FloatingWindowState.HIDDEN)

    /** Observable state of the floating window. */
    val state: StateFlow<FloatingWindowState> = _state.asStateFlow()

    private val _isAppInForeground = MutableStateFlow(true)

    /** Whether the app is currently in foreground. */
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    // Track if user explicitly enabled floating window (persists across task executions)
    private var userEnabledWindow = false

    /**
     * Called when a task starts executing.
     *
     * Forces the floating window to be visible regardless of user preference.
     *
     * @param context Application context for starting the service
     */
    fun onTaskStarted(context: Context) {
        Logger.d(TAG, "onTaskStarted: current state = ${_state.value}")
        _state.value = FloatingWindowState.FORCED_VISIBLE
        ensureServiceStarted(context)
        showWindowIfNeeded(context)
    }

    /**
     * Called when a task completes (success or failure).
     *
     * Returns to user preference state.
     */
    fun onTaskCompleted() {
        Logger.d(TAG, "onTaskCompleted: userEnabledWindow = $userEnabledWindow")
        _state.value =
            if (userEnabledWindow) {
                FloatingWindowState.VISIBLE_WHEN_BACKGROUND
            } else {
                FloatingWindowState.HIDDEN
            }
    }

    /**
     * Called when user explicitly enables the floating window.
     *
     * @param context Application context for starting the service
     */
    fun enableByUser(context: Context) {
        Logger.d(TAG, "enableByUser")
        userEnabledWindow = true
        if (_state.value != FloatingWindowState.FORCED_VISIBLE) {
            _state.value = FloatingWindowState.VISIBLE_WHEN_BACKGROUND
        }
        ensureServiceStarted(context)
    }

    /**
     * Called when user explicitly disables the floating window.
     */
    fun disableByUser() {
        Logger.d(TAG, "disableByUser")
        userEnabledWindow = false
        if (_state.value != FloatingWindowState.FORCED_VISIBLE) {
            _state.value = FloatingWindowState.HIDDEN
            hideWindow()
        }
    }

    /**
     * Toggles the floating window state by user action.
     *
     * @param context Application context
     */
    fun toggleByUser(context: Context) {
        if (userEnabledWindow || _state.value == FloatingWindowState.FORCED_VISIBLE) {
            disableByUser()
        } else {
            enableByUser(context)
        }
    }

    /**
     * Called when app enters foreground.
     *
     * Hides the floating window unless forced visible.
     */
    fun onAppForeground() {
        Logger.d(TAG, "onAppForeground: state = ${_state.value}")
        _isAppInForeground.value = true

        // Only hide if not forced visible (task running)
        if (_state.value != FloatingWindowState.FORCED_VISIBLE) {
            hideWindow()
        }
    }

    /**
     * Called when app enters background.
     *
     * Shows the floating window if state allows.
     *
     * @param context Application context
     */
    fun onAppBackground(context: Context) {
        Logger.d(TAG, "onAppBackground: state = ${_state.value}")
        _isAppInForeground.value = false

        when (_state.value) {
            FloatingWindowState.FORCED_VISIBLE,
            FloatingWindowState.VISIBLE_WHEN_BACKGROUND,
            -> {
                showWindowIfNeeded(context)
            }

            FloatingWindowState.HIDDEN -> {
                // Don't show
            }
        }
    }

    /**
     * Checks if the floating window should currently be visible.
     *
     * @return true if window should be visible
     */
    fun shouldBeVisible(): Boolean {
        val state = _state.value
        val inForeground = _isAppInForeground.value

        return when (state) {
            FloatingWindowState.FORCED_VISIBLE -> true
            FloatingWindowState.VISIBLE_WHEN_BACKGROUND -> !inForeground
            FloatingWindowState.HIDDEN -> false
        }
    }

    /**
     * Checks if the floating window is enabled by user or task.
     *
     * @return true if enabled
     */
    fun isEnabled(): Boolean = _state.value != FloatingWindowState.HIDDEN

    /**
     * Checks if user has explicitly enabled the floating window.
     *
     * @return true if user enabled
     */
    fun isUserEnabled(): Boolean = userEnabledWindow

    private fun ensureServiceStarted(context: Context) {
        val existingService = FloatingWindowService.getInstance()
        if (existingService == null) {
            Logger.d(TAG, "Starting FloatingWindowService")
            val intent = Intent(context, FloatingWindowService::class.java)
            context.startService(intent)
        }
    }

    private fun showWindowIfNeeded(context: Context) {
        if (shouldBeVisible()) {
            Logger.d(TAG, "Showing floating window")
            // Use post to ensure service is ready
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                FloatingWindowService.getInstance()?.show()
            }, 100)
        }
    }

    private fun hideWindow() {
        Logger.d(TAG, "Hiding floating window")
        FloatingWindowService.getInstance()?.hide()
    }

    /**
     * Resets the state manager (for testing or app restart).
     */
    fun reset() {
        _state.value = FloatingWindowState.HIDDEN
        _isAppInForeground.value = true
        userEnabledWindow = false
    }
}
