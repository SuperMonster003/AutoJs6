package org.autojs.autojs.engine

object NodeExecutionModePolicy {

    const val MODE_ONE_SHOT = "one_shot"
    const val MODE_LONG_RUNNING = "long_running"
    const val MODE_SERVICE = "service"

    fun normalize(mode: String?): String {
        val normalized = mode
            ?.trim()
            ?.lowercase()
            ?.replace('-', '_')
            ?: MODE_ONE_SHOT
        return when (normalized) {
            "", "oneshot", "one_shot", "one.shot" -> MODE_ONE_SHOT
            "longrunning", "long_running", "long.running" -> MODE_LONG_RUNNING
            MODE_SERVICE -> MODE_SERVICE
            else -> normalized
        }
    }

    fun isSupported(mode: String?): Boolean = normalize(mode) == MODE_ONE_SHOT

    fun isLongRunningRequest(mode: String?): Boolean {
        return normalize(mode) in setOf(MODE_LONG_RUNNING, MODE_SERVICE)
    }
}
