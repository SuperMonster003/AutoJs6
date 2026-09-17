package org.autojs.autojs.engine

/**
 * Host-side execution mode tokens for Node.js plugin launches.
 *
 * The host only decides which mode / launch-surface pairs it can actually start. The semantics of
 * a mode (status, restart policy, resource quotas, allowed providers, stop semantics) are owned by
 * the installed plugin and are reported through its capability catalog and `autojs6:lifecycle`;
 * the host does not keep a second copy of that metadata.
 */
object NodeExecutionModePolicy {

    const val MODE_ONE_SHOT = "one_shot"
    const val MODE_INTERACTIVE_LONG_RUNNING = "interactive_long_running"
    const val MODE_SCHEDULED = "scheduled"

    const val LAUNCH_SURFACE_SCRIPT = "script"
    const val LAUNCH_SURFACE_INTERACTIVE_SESSION = "interactive_session"
    const val LAUNCH_SURFACE_SCHEDULED_RUNNER = "scheduled_runner"

    // Legacy request aliases retained for stable rejection diagnostics.
    const val MODE_LONG_RUNNING = "long_running"
    const val MODE_SERVICE = "service"

    /** The execution modes the host can launch, in plugin catalog order. */
    val runnableModes: List<String> = listOf(
        MODE_ONE_SHOT,
        MODE_INTERACTIVE_LONG_RUNNING,
        MODE_SCHEDULED,
    )

    fun normalize(mode: String?): String {
        val normalized = mode
            ?.trim()
            ?.lowercase()
            ?.replace('-', '_')
            ?: MODE_ONE_SHOT
        return when (normalized) {
            "", "oneshot", "one_shot", "one.shot" -> MODE_ONE_SHOT
            "interactivelongrunning",
            "interactive_long_running",
            "interactive.long.running",
            "longrunning",
            MODE_LONG_RUNNING,
            "long.running",
            MODE_SERVICE,
            -> MODE_INTERACTIVE_LONG_RUNNING
            "scheduled", "schedule", "workmanager", "work_manager" -> MODE_SCHEDULED
            else -> normalized
        }
    }

    fun isRunnable(mode: String?): Boolean = normalize(mode) in runnableModes

    /**
     * The launch surface through which the host starts the given mode, or null when the host has
     * no launcher for it. Direct script launches are always one-shot.
     */
    fun launchSurfaceFor(mode: String?): String? = when (normalize(mode)) {
        MODE_ONE_SHOT -> LAUNCH_SURFACE_SCRIPT
        MODE_INTERACTIVE_LONG_RUNNING -> LAUNCH_SURFACE_INTERACTIVE_SESSION
        MODE_SCHEDULED -> LAUNCH_SURFACE_SCHEDULED_RUNNER
        else -> null
    }

    fun isSupported(
        mode: String?,
        launchSurface: String = LAUNCH_SURFACE_SCRIPT,
    ): Boolean = launchSurfaceFor(mode) == launchSurface

    fun isLongRunningRequest(mode: String?): Boolean {
        return normalize(mode) == MODE_INTERACTIVE_LONG_RUNNING
    }

    fun isInteractiveLongRunningLaunchSurface(launchSurface: String?): Boolean {
        return launchSurface == LAUNCH_SURFACE_INTERACTIVE_SESSION
    }

    fun nativeValues(
        mode: String?,
        launchSurface: String = LAUNCH_SURFACE_SCRIPT,
    ): Map<String, String> {
        val normalized = normalize(mode)
        val supported = isSupported(normalized, launchSurface)
        // `embedded_script.*` is a published diagnostic wire namespace. It is retained for
        // plugin compatibility and does not mean that the host embeds a Node.js runtime.
        // Only host launch facts are reported here; mode semantics come from the plugin.
        return linkedMapOf(
            "embedded_script.execution_mode" to normalized,
            "embedded_script.execution_mode_launch_surface" to launchSurface,
            "embedded_script.execution_mode_supported" to supported.toString(),
            "embedded_script.execution_mode_supported_launch_surfaces" to (launchSurfaceFor(normalized) ?: "none"),
            "embedded_script.long_running_requested" to isLongRunningRequest(normalized).toString(),
            "embedded_script.long_running_supported" to (normalized == MODE_INTERACTIVE_LONG_RUNNING && supported).toString(),
        )
    }
}
