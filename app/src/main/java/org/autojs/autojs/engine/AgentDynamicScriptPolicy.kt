package org.autojs.autojs.engine

/** Bound inline source before crossing the main-process boundary; this is not a JavaScript sandbox. */
internal object AgentDynamicScriptPolicy {
    const val MAX_SOURCE_BYTES = 8 * 1024
    const val MAX_TIMEOUT_MS = 300_000L
    private val options = setOf("agentInvocationId", "agentRunId", "presetName", "timeoutMs", "captureConsole")

    fun validateOptions(keys: Set<String>) {
        require(keys.all { it in options }) { "Unsupported dynamic script options" }
        require("agentInvocationId" in keys && "timeoutMs" in keys) { "Dynamic script ownership and timeout are required" }
    }

    fun validateSource(name: String, source: String) {
        require(name.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,119}(?:\\.js)?"))) { "Invalid dynamic script name" }
        require(source.isNotBlank() && '\u0000' !in source && source.toByteArray(Charsets.UTF_8).size <= MAX_SOURCE_BYTES) {
            "Dynamic script source must be non-empty and at most 8192 UTF-8 bytes"
        }
        var index = 0
        while (index < source.length) {
            val character = source[index++]
            if (character.isHighSurrogate()) {
                require(index < source.length && source[index++].isLowSurrogate()) { "Invalid source Unicode" }
            } else require(!character.isLowSurrogate()) { "Invalid source Unicode" }
        }
    }
}
