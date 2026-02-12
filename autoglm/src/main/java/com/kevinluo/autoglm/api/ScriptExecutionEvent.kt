package com.kevinluo.autoglm.api

data class ScriptExecutionEvent(
    val type: Type,
    val id: Int,
    val scriptPath: String?,
    val message: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
) {
    enum class Type { START, SUCCESS, EXCEPTION }
}