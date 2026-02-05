package com.kevinluo.autoglm.util

import android.util.Log

/**
 * Centralized logging utility for the AutoGLM Phone Agent application.
 *
 * Provides consistent logging with tag prefixes and log level control.
 * All components should use this Logger instead of direct `android.util.Log` calls
 * to ensure consistent formatting and centralized log management.
 *
 * Usage example:
 * ```kotlin
 * class MyComponent {
 *     fun doSomething() {
 *         Logger.d(TAG, "Starting operation")
 *     }
 *
 *     companion object {
 *         private const val TAG = "MyComponent"
 *     }
 * }
 * ```
 *
 */
object Logger {
    /**
     * Log levels for filtering output.
     *
     * Messages with a level below [minLevel] will be ignored.
     */
    enum class Level {
        /** Most verbose level, for detailed debugging information. */
        VERBOSE,

        /** Debug level, for development-time debugging. */
        DEBUG,

        /** Info level, for general informational messages. */
        INFO,

        /** Warning level, for potentially problematic situations. */
        WARN,

        /** Error level, for error conditions. */
        ERROR,
    }

    /**
     * Current minimum log level. Messages below this level will be ignored.
     */
    var minLevel: Level = Level.DEBUG

    /**
     * Whether to include timestamps in log messages.
     */
    var includeTimestamp: Boolean = false

    /**
     * Whether to write logs to file in addition to Logcat.
     */
    var writeToFile: Boolean = true

    /**
     * Logs a verbose message.
     *
     * @param tag Component tag identifying the source of the log message
     * @param message Log message to be recorded
     *
     */
    fun v(tag: String, message: String) {
        if (minLevel <= Level.VERBOSE) {
            Log.v(formatTag(tag), formatMessage(message))
            writeToFileIfEnabled("VERBOSE", tag, message)
        }
    }

    /**
     * Logs a debug message.
     *
     * @param tag Component tag identifying the source of the log message
     * @param message Log message to be recorded
     *
     */
    fun d(tag: String, message: String) {
        if (minLevel <= Level.DEBUG) {
            Log.d(formatTag(tag), formatMessage(message))
            writeToFileIfEnabled("DEBUG", tag, message)
        }
    }

    /**
     * Logs an info message.
     *
     * @param tag Component tag identifying the source of the log message
     * @param message Log message to be recorded
     *
     */
    fun i(tag: String, message: String) {
        if (minLevel <= Level.INFO) {
            Log.i(formatTag(tag), formatMessage(message))
            writeToFileIfEnabled("INFO", tag, message)
        }
    }

    /**
     * Logs a warning message.
     *
     * @param tag Component tag identifying the source of the log message
     * @param message Log message to be recorded
     *
     */
    fun w(tag: String, message: String) {
        if (minLevel <= Level.WARN) {
            Log.w(formatTag(tag), formatMessage(message))
            writeToFileIfEnabled("WARN", tag, message)
        }
    }

    /**
     * Logs a warning message with exception.
     *
     * @param tag Component tag identifying the source of the log message
     * @param message Log message to be recorded
     * @param throwable Exception to log along with the message
     *
     */
    fun w(tag: String, message: String, throwable: Throwable) {
        if (minLevel <= Level.WARN) {
            Log.w(formatTag(tag), formatMessage(message), throwable)
            writeToFileIfEnabled("WARN", tag, message, throwable)
        }
    }

    /**
     * Logs an error message.
     *
     * @param tag Component tag identifying the source of the log message
     * @param message Log message to be recorded
     *
     */
    fun e(tag: String, message: String) {
        if (minLevel <= Level.ERROR) {
            Log.e(formatTag(tag), formatMessage(message))
            writeToFileIfEnabled("ERROR", tag, message)
        }
    }

    /**
     * Logs an error message with exception.
     *
     * @param tag Component tag identifying the source of the log message
     * @param message Log message to be recorded
     * @param throwable Exception to log along with the message
     *
     */
    fun e(tag: String, message: String, throwable: Throwable) {
        if (minLevel <= Level.ERROR) {
            Log.e(formatTag(tag), formatMessage(message), throwable)
            writeToFileIfEnabled("ERROR", tag, message, throwable)
        }
    }

    /**
     * Writes log to file if file logging is enabled.
     */
    private fun writeToFileIfEnabled(level: String, tag: String, message: String, throwable: Throwable? = null) {
        if (writeToFile) {
            LogFileManager.writeLog(level, "$APP_TAG/$tag", message, throwable)
        }
    }

    /**
     * Logs an action execution.
     *
     * @param actionType Type of action being executed (e.g., "tap", "swipe", "type")
     * @param details Additional details about the action parameters
     *
     */
    fun logAction(actionType: String, details: String) {
        i(TAG_ACTION, "$actionType: $details")
    }

    /**
     * Logs a network request.
     *
     * @param method HTTP method (e.g., "GET", "POST")
     * @param url Request URL
     *
     */
    fun logNetworkRequest(method: String, url: String) {
        d(TAG_NETWORK, "Request: $method $url")
    }

    /**
     * Logs a network response.
     *
     * @param statusCode HTTP status code
     * @param durationMs Request duration in milliseconds
     *
     */
    fun logNetworkResponse(statusCode: Int, durationMs: Long) {
        d(TAG_NETWORK, "Response: $statusCode (${durationMs}ms)")
    }

    /**
     * Logs a network error.
     *
     * @param error Error description
     * @param throwable Optional exception that caused the error
     *
     */
    fun logNetworkError(error: String, throwable: Throwable? = null) {
        if (throwable != null) {
            e(TAG_NETWORK, "Error: $error", throwable)
        } else {
            e(TAG_NETWORK, "Error: $error")
        }
    }

    /**
     * Logs a step in the agent execution.
     *
     * @param stepNumber Current step number in the task execution
     * @param action Action being performed in this step
     *
     */
    fun logStep(stepNumber: Int, action: String) {
        i(TAG_AGENT, "Step $stepNumber: $action")
    }

    /**
     * Logs task start.
     *
     * @param taskDescription Description of the task being started (truncated to 100 chars)
     *
     */
    fun logTaskStart(taskDescription: String) {
        val truncated =
            if (taskDescription.length > MAX_TASK_DESCRIPTION_LENGTH) {
                "${taskDescription.take(MAX_TASK_DESCRIPTION_LENGTH)}..."
            } else {
                taskDescription
            }
        i(TAG_AGENT, "Task started: $truncated")
    }

    /**
     * Logs task completion.
     *
     * @param success Whether the task succeeded
     * @param message Result message describing the outcome
     * @param stepCount Number of steps executed during the task
     *
     */
    fun logTaskComplete(success: Boolean, message: String, stepCount: Int) {
        val status = if (success) "completed" else "failed"
        i(TAG_AGENT, "Task $status after $stepCount steps: $message")
    }

    /**
     * Logs a screenshot capture.
     *
     * @param width Screenshot width in pixels
     * @param height Screenshot height in pixels
     * @param isSensitive Whether the screen was detected as sensitive content
     *
     */
    fun logScreenshot(width: Int, height: Int, isSensitive: Boolean) {
        d(TAG_SCREENSHOT, "Captured ${width}x$height, sensitive=$isSensitive")
    }

    /**
     * Logs model thinking.
     *
     * @param thinking Thinking text from the model (truncated to 200 chars)
     *
     */
    fun logThinking(thinking: String) {
        val truncated =
            if (thinking.length > MAX_THINKING_LENGTH) {
                "${thinking.take(MAX_THINKING_LENGTH)}..."
            } else {
                thinking
            }
        d(TAG_MODEL, "Thinking: $truncated")
    }

    /**
     * Logs model action.
     *
     * @param action Action string from model response
     *
     */
    fun logModelAction(action: String) {
        d(TAG_MODEL, "Action: $action")
    }

    /**
     * Formats the tag with the application prefix.
     *
     * @param tag Component tag to format
     * @return Formatted tag with APP_TAG prefix
     */
    private fun formatTag(tag: String): String = "$APP_TAG/$tag"

    /**
     * Formats the message with optional timestamp.
     *
     * @param message Message to format
     * @return Formatted message, optionally with timestamp prefix
     */
    private fun formatMessage(message: String): String = if (includeTimestamp) {
        val timestamp =
            java.text
                .SimpleDateFormat(
                    TIMESTAMP_FORMAT,
                    java.util.Locale.getDefault(),
                ).format(java.util.Date())
        "[$timestamp] $message"
    } else {
        message
    }

    // Constants - placed at the bottom following code style guidelines
    private const val APP_TAG = "AutoGLM"
    private const val TAG_ACTION = "Action"
    private const val TAG_NETWORK = "Network"
    private const val TAG_AGENT = "Agent"
    private const val TAG_SCREENSHOT = "Screenshot"
    private const val TAG_MODEL = "Model"
    private const val TIMESTAMP_FORMAT = "HH:mm:ss.SSS"
    private const val MAX_TASK_DESCRIPTION_LENGTH = 100
    private const val MAX_THINKING_LENGTH = 200
}
