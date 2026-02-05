package com.kevinluo.autoglm.util

import com.kevinluo.autoglm.model.NetworkError

/**
 * Centralized error handling utility for the AutoGLM Phone Agent application.
 *
 * Provides consistent error categorization, logging, and user-friendly messages.
 * All error handling should go through this utility to ensure consistent
 * error formatting and logging across the application.
 *
 * Usage example:
 * ```kotlin
 * try {
 *     performOperation()
 * } catch (e: Exception) {
 *     val error = ErrorHandler.handleUnknownError("Operation failed", e)
 *     Logger.e(TAG, ErrorHandler.formatErrorForLog(error), e)
 *     return Result.Error(error.userMessage, e)
 * }
 * ```
 *
 */
object ErrorHandler {
    /**
     * Error categories for classification.
     *
     * Used to categorize errors for appropriate handling and display.
     */
    enum class ErrorCategory {
        /** Network-related errors (connection, timeout, server errors). */
        NETWORK,

        /** Permission-related errors (missing permissions, Shizuku issues). */
        PERMISSION,

        /** Action execution errors (tap, swipe, type failures). */
        ACTION,

        /** Screenshot capture errors. */
        SCREENSHOT,

        /** Parsing errors (JSON, model response parsing). */
        PARSING,

        /** Configuration errors (invalid settings). */
        CONFIGURATION,

        /** Unknown or unexpected errors. */
        UNKNOWN,
    }

    /**
     * Represents a handled error with user-friendly message.
     *
     * @property category The category of the error for classification
     * @property userMessage User-friendly message suitable for display
     * @property technicalMessage Technical message for logging and debugging
     * @property isRetryable Whether the operation can be retried
     * @property originalException The original exception that caused the error, if any
     */
    data class HandledError(
        val category: ErrorCategory,
        val userMessage: String,
        val technicalMessage: String,
        val isRetryable: Boolean,
        val originalException: Throwable? = null,
    )

    /**
     * Handles a network error and returns a user-friendly error.
     *
     * @param error The network error to handle
     * @return HandledError with appropriate user and technical messages
     *
     */
    fun handleNetworkError(error: NetworkError): HandledError {
        Logger.logNetworkError(error.message ?: "Unknown network error")

        return when (error) {
            is NetworkError.ConnectionFailed -> {
                HandledError(
                    category = ErrorCategory.NETWORK,
                    userMessage = "无法连接到服务器，请检查网络连接",
                    technicalMessage = error.message,
                    isRetryable = true,
                    originalException = error,
                )
            }

            is NetworkError.Timeout -> {
                HandledError(
                    category = ErrorCategory.NETWORK,
                    userMessage = "请求超时，请稍后重试",
                    technicalMessage = "Request timed out after ${error.timeoutMs}ms",
                    isRetryable = true,
                    originalException = error,
                )
            }

            is NetworkError.ServerError -> {
                HandledError(
                    category = ErrorCategory.NETWORK,
                    userMessage = "服务器错误 (${error.statusCode})，请稍后重试",
                    technicalMessage = "Server error ${error.statusCode}: ${error.message}",
                    isRetryable = error.statusCode >= 500,
                    originalException = error,
                )
            }

            is NetworkError.ParseError -> {
                HandledError(
                    category = ErrorCategory.PARSING,
                    userMessage = "无法解析服务器响应",
                    technicalMessage = "Parse error: ${error.rawResponse.take(MAX_RAW_RESPONSE_LENGTH)}",
                    isRetryable = false,
                    originalException = error,
                )
            }
        }
    }

    /**
     * Handles an action execution error.
     *
     * @param actionType Type of action that failed (e.g., "tap", "swipe", "type")
     * @param error Error message describing what went wrong
     * @param exception Optional exception that caused the error
     * @return HandledError with appropriate user and technical messages
     *
     */
    fun handleActionError(actionType: String, error: String, exception: Throwable? = null): HandledError {
        Logger.e(TAG, "Action error [$actionType]: $error", exception ?: Exception(error))

        return HandledError(
            category = ErrorCategory.ACTION,
            userMessage = "操作执行失败: $actionType",
            technicalMessage = error,
            isRetryable = true,
            originalException = exception,
        )
    }

    /**
     * Handles a screenshot capture error.
     *
     * @param error Error message describing what went wrong
     * @param isSensitive Whether the error is due to sensitive screen detection
     * @param exception Optional exception that caused the error
     * @return HandledError with appropriate user and technical messages
     *
     */
    fun handleScreenshotError(
        error: String,
        isSensitive: Boolean = false,
        exception: Throwable? = null,
    ): HandledError {
        Logger.e(TAG, "Screenshot error: $error", exception ?: Exception(error))

        return if (isSensitive) {
            HandledError(
                category = ErrorCategory.SCREENSHOT,
                userMessage = "当前屏幕受保护，无法截图",
                technicalMessage = "Sensitive screen detected",
                isRetryable = false,
                originalException = exception,
            )
        } else {
            HandledError(
                category = ErrorCategory.SCREENSHOT,
                userMessage = "截图失败，请重试",
                technicalMessage = error,
                isRetryable = true,
                originalException = exception,
            )
        }
    }

    /**
     * Handles a permission error.
     *
     * @param permission Permission that is missing or denied
     * @param exception Optional exception that caused the error
     * @return HandledError with appropriate user and technical messages
     *
     */
    fun handlePermissionError(permission: String, exception: Throwable? = null): HandledError {
        Logger.e(TAG, "Permission error: $permission", exception ?: Exception("Missing permission: $permission"))

        return HandledError(
            category = ErrorCategory.PERMISSION,
            userMessage = "缺少必要权限: $permission",
            technicalMessage = "Missing permission: $permission",
            isRetryable = false,
            originalException = exception,
        )
    }

    /**
     * Handles a Shizuku-related error.
     *
     * @param error Error message describing the Shizuku issue
     * @param exception Optional exception that caused the error
     * @return HandledError with appropriate user and technical messages
     *
     */
    fun handleShizukuError(error: String, exception: Throwable? = null): HandledError {
        Logger.e(TAG, "Shizuku error: $error", exception ?: Exception(error))

        return HandledError(
            category = ErrorCategory.PERMISSION,
            userMessage = "Shizuku 服务不可用，请确保 Shizuku 已启动并授权",
            technicalMessage = error,
            isRetryable = true,
            originalException = exception,
        )
    }

    /**
     * Handles a parsing error.
     *
     * @param input Input that failed to parse (will be truncated in logs)
     * @param error Error message describing the parsing failure
     * @param exception Optional exception that caused the error
     * @return HandledError with appropriate user and technical messages
     *
     */
    fun handleParsingError(input: String, error: String, exception: Throwable? = null): HandledError {
        Logger.e(
            TAG,
            "Parsing error: $error, input: ${input.take(MAX_INPUT_LOG_LENGTH)}",
            exception ?: Exception(error),
        )

        return HandledError(
            category = ErrorCategory.PARSING,
            userMessage = "无法解析模型响应",
            technicalMessage = "Parse error: $error",
            isRetryable = false,
            originalException = exception,
        )
    }

    /**
     * Handles a configuration error.
     *
     * @param setting Setting name that is invalid or missing
     * @param error Error message describing the configuration issue
     * @return HandledError with appropriate user and technical messages
     *
     */
    fun handleConfigurationError(setting: String, error: String): HandledError {
        Logger.e(TAG, "Configuration error [$setting]: $error")

        return HandledError(
            category = ErrorCategory.CONFIGURATION,
            userMessage = "配置错误: $setting",
            technicalMessage = error,
            isRetryable = false,
        )
    }

    /**
     * Handles an unknown/unexpected error.
     *
     * @param error Error message describing what went wrong
     * @param exception Optional exception that caused the error
     * @return HandledError with appropriate user and technical messages
     *
     */
    fun handleUnknownError(error: String, exception: Throwable? = null): HandledError {
        Logger.e(TAG, "Unknown error: $error", exception ?: Exception(error))

        return HandledError(
            category = ErrorCategory.UNKNOWN,
            userMessage = "发生未知错误，请重试",
            technicalMessage = error,
            isRetryable = true,
            originalException = exception,
        )
    }

    /**
     * Handles an app not found error.
     *
     * @param appName Name of the app that wasn't found
     * @return HandledError with appropriate user and technical messages
     *
     */
    fun handleAppNotFoundError(appName: String): HandledError {
        Logger.w(TAG, "App not found: $appName")

        return HandledError(
            category = ErrorCategory.ACTION,
            userMessage = "找不到应用: $appName",
            technicalMessage = "App not found: $appName",
            isRetryable = false,
        )
    }

    /**
     * Formats an error for user display.
     *
     * @param error The handled error to format
     * @return Formatted error message suitable for UI display
     *
     */
    fun formatErrorForDisplay(error: HandledError): String = buildString {
        append(error.userMessage)
        if (error.isRetryable) {
            append(" (可重试)")
        }
    }

    /**
     * Formats an error for logging.
     *
     * @param error The handled error to format
     * @return Formatted error message suitable for log output
     *
     */
    fun formatErrorForLog(error: HandledError): String = buildString {
        append("[${error.category}] ")
        append(error.technicalMessage)
        error.originalException?.let {
            append("\nStack trace: ${it.stackTraceToString().take(MAX_STACK_TRACE_LENGTH)}")
        }
    }

    // Constants - placed at the bottom following code style guidelines
    private const val TAG = "ErrorHandler"
    private const val MAX_RAW_RESPONSE_LENGTH = 200
    private const val MAX_INPUT_LOG_LENGTH = 100
    private const val MAX_STACK_TRACE_LENGTH = 500
}
