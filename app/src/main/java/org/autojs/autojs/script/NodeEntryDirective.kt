package org.autojs.autojs.script

object NodeEntryDirective {

    const val DIRECTIVE_NODEJS = "nodejs"
    const val DIRECTIVE_NODEJS_UI = "nodejs ui"
    const val DIRECTIVE_NODEJS_UI_THREAD = "nodejs ui-thread"
    const val DIRECTIVE_NODEJS_UI_AND_UI_THREAD = "nodejs ui ui-thread"

    @JvmStatic
    fun normalize(value: String?): String? {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val tokens = raw
            .split(Regex("\\s*[,;|]\\s*|\\s+"))
            .mapNotNull { token -> token.trim().lowercase().takeIf { it.isNotBlank() } }
        if (tokens.isEmpty()) {
            return null
        }
        var hasNode = false
        var hasUi = false
        var hasUiThread = false
        tokens.forEach { token ->
            when (token.replace('_', '-')) {
                "node", "nodejs" -> hasNode = true
                "ui" -> hasUi = true
                "ui-thread", "uithread" -> hasUiThread = true
                else -> throw IllegalArgumentException("Unsupported Node entry directive token: $token")
            }
        }
        if (!hasNode && !hasUi && !hasUiThread) {
            return null
        }
        return buildString {
            append(DIRECTIVE_NODEJS)
            if (hasUi) {
                append(" ui")
            }
            if (hasUiThread) {
                append(" ui-thread")
            }
        }
    }

    @JvmStatic
    fun fromExecutionMode(executionMode: Int): String? {
        if ((executionMode and JavaScriptSource.EXECUTION_MODE_NODEJS) == 0) {
            return null
        }
        return buildString {
            append(DIRECTIVE_NODEJS)
            if ((executionMode and JavaScriptSource.EXECUTION_MODE_UI) != 0) {
                append(" ui")
            }
            if ((executionMode and JavaScriptSource.EXECUTION_MODE_UI_THREAD) != 0) {
                append(" ui-thread")
            }
        }
    }

    @JvmStatic
    fun requiresUiActivity(value: String?): Boolean {
        val normalized = normalize(value) ?: return false
        return normalized.split(' ').contains("ui")
    }

    @JvmStatic
    fun requiresUiThread(value: String?): Boolean {
        val normalized = normalize(value) ?: return false
        return normalized.split(' ').contains("ui-thread")
    }
}
