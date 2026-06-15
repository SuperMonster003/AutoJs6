package org.autojs.autojs.ui.edit.editor.ace.health

import org.json.JSONObject

object AceJsErrorClassifier {

    fun classifyBridgeEvent(name: String, isReady: Boolean, payloadJson: String?): Classification {
        val message = messageFromPayload(payloadJson, fallbackFor(name))
        return when (name) {
            "lspError" -> Classification(AceFailureType.LSP_ERROR, message, fatal = false, category = "lsp")
            "completionError" -> Classification(AceFailureType.COMPLETION_ERROR, message, fatal = false, category = "completion")
            "recoverableError" -> Classification(AceFailureType.JS_RUNTIME_ERROR, message, fatal = false, category = "recoverable")
            "fatalError" -> classifyFatalError(isReady, payloadJson, message)
            else -> Classification(AceFailureType.UNKNOWN, message, fatal = false, category = "unknown")
        }
    }

    fun shouldTreatConsoleErrorAsFatal(isReady: Boolean, source: String?, message: String?): Boolean {
        if (isReady) {
            return false
        }
        val safeSource = source.orEmpty()
        val safeMessage = message.orEmpty()
        return safeSource.contains("autojs6_ace_bridge", ignoreCase = true) ||
            safeMessage.contains("Uncaught", ignoreCase = true) ||
            safeMessage.contains("ReferenceError", ignoreCase = true) ||
            safeMessage.contains("SyntaxError", ignoreCase = true)
    }

    private fun classifyFatalError(isReady: Boolean, payloadJson: String?, message: String): Classification {
        if (!isReady) {
            return Classification(AceFailureType.JS_INIT_ERROR, message, fatal = true, category = "init")
        }
        val source = payloadValue(payloadJson, "source")
        val stack = payloadValue(payloadJson, "stack")
        val reason = payloadValue(payloadJson, "reason")
        val combined = listOf(message, source, stack, reason).joinToString("\n")
        if (isFeatureLocal(combined)) {
            return Classification(AceFailureType.JS_RUNTIME_ERROR, message, fatal = false, category = "feature_local")
        }
        if (isRecoverableRuntimeError(combined)) {
            return Classification(AceFailureType.JS_RUNTIME_ERROR, message, fatal = false, category = "recoverable_runtime")
        }
        return Classification(AceFailureType.JS_RUNTIME_ERROR, message, fatal = true, category = "runtime")
    }

    private fun isFeatureLocal(value: String): Boolean {
        return listOf("completion", "lsp", "tooltip", "markdown").any {
            value.contains(it, ignoreCase = true)
        }
    }

    private fun isRecoverableRuntimeError(value: String): Boolean {
        return listOf(
            "scheduleResize",
            "scrollCursorIntoView",
            "resize:",
            "ResizeObserver loop",
            "focus",
            "blur",
        ).any {
            value.contains(it, ignoreCase = true)
        }
    }

    private fun fallbackFor(name: String): String {
        return when (name) {
            "fatalError" -> "ACE JavaScript fatal error"
            "lspError" -> "ACE LSP error"
            "completionError" -> "ACE completion error"
            "recoverableError" -> "ACE recoverable JavaScript error"
            else -> "ACE JavaScript error"
        }
    }

    private fun messageFromPayload(payloadJson: String?, fallback: String): String {
        if (payloadJson.isNullOrBlank()) {
            return fallback
        }
        return runCatching {
            val obj = JSONObject(payloadJson)
            obj.optString("message").ifBlank {
                obj.optString("reason").ifBlank { fallback }
            }
        }.getOrElse { fallback }
    }

    private fun payloadValue(payloadJson: String?, key: String): String {
        if (payloadJson.isNullOrBlank()) {
            return ""
        }
        return runCatching {
            JSONObject(payloadJson).optString(key)
        }.getOrDefault("")
    }

    data class Classification(
        val type: AceFailureType,
        val message: String,
        val fatal: Boolean,
        val category: String,
    )
}
