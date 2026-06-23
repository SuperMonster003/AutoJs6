package org.autojs.autojs.engine

object NodeExecutionModePolicy {

    const val MODE_ONE_SHOT = "one_shot"
    const val MODE_INTERACTIVE_LONG_RUNNING = "interactive_long_running"
    const val MODE_SCHEDULED = "scheduled"
    const val MODE_WORKER_COMPUTATION = "worker_computation"
    const val MODE_NODE_SANDBOXED = "node_sandboxed"

    const val LAUNCH_SURFACE_SCRIPT = "script"
    const val LAUNCH_SURFACE_INTERACTIVE_SESSION = "interactive_session"
    const val LAUNCH_SURFACE_SCHEDULED_RUNNER = "scheduled_runner"

    // Legacy request aliases retained for stable rejection diagnostics.
    const val MODE_LONG_RUNNING = "long_running"
    const val MODE_SERVICE = "service"

    data class Definition(
        val mode: String,
        val status: String,
        val supportedLaunchSurfaces: Set<String>,
        val timeoutPolicy: String,
        val foregroundRequirement: String,
        val notificationRequirement: String,
        val allowedProviders: List<String>,
        val restartPolicy: String,
        val resourceQuotaProfile: String,
        val stopSemantics: String,
        val unsupportedReason: String? = null,
    ) {
        val supported: Boolean
            get() = supportedLaunchSurfaces.isNotEmpty()

        fun supports(launchSurface: String): Boolean = launchSurface in supportedLaunchSurfaces
    }

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
            "long_running",
            "long.running",
            MODE_SERVICE,
            -> MODE_INTERACTIVE_LONG_RUNNING
            "scheduled", "schedule", "workmanager", "work_manager" -> MODE_SCHEDULED
            "workercomputation",
            "worker_computation",
            "worker.computation",
            "worker",
            -> MODE_WORKER_COMPUTATION
            "nodesandboxed",
            "node_sandboxed",
            "node.sandboxed",
            "sandboxed",
            "sandbox",
            -> MODE_NODE_SANDBOXED
            else -> normalized
        }
    }

    fun definition(mode: String?): Definition {
        val normalized = normalize(mode)
        return DEFINITIONS_BY_MODE[normalized] ?: unknownDefinition(normalized)
    }

    fun definitions(): List<Definition> = DEFINITIONS

    fun canonicalModes(): List<String> = DEFINITIONS.map { it.mode }

    fun isSupported(
        mode: String?,
        launchSurface: String = LAUNCH_SURFACE_SCRIPT,
    ): Boolean = definition(mode).supports(launchSurface)

    fun isLongRunningRequest(mode: String?): Boolean {
        return normalize(mode) == MODE_INTERACTIVE_LONG_RUNNING
    }

    fun unsupportedMessage(
        mode: String?,
        launchSurface: String = LAUNCH_SURFACE_SCRIPT,
    ): String {
        val definition = definition(mode)
        val reason = definition.unsupportedReason?.let { " $it" }.orEmpty()
        return "Embedded Node executionMode '${definition.mode}' is not supported for launchSurface '$launchSurface' by the Phase 7 runtime state machine.$reason " +
            "Use '$MODE_ONE_SHOT' with a positive timeout for normal scripts."
    }

    fun nativeValues(
        mode: String?,
        launchSurface: String = LAUNCH_SURFACE_SCRIPT,
    ): Map<String, String> {
        val definition = definition(mode)
        return linkedMapOf(
            "embedded_script.execution_mode" to definition.mode,
            "embedded_script.execution_mode_status" to definition.status,
            "embedded_script.execution_mode_launch_surface" to launchSurface,
            "embedded_script.execution_mode_supported" to definition.supports(launchSurface).toString(),
            "embedded_script.execution_mode_supported_launch_surfaces" to definition.supportedLaunchSurfaces.joinToString(",").ifBlank { "none" },
            "embedded_script.execution_mode_timeout_policy" to definition.timeoutPolicy,
            "embedded_script.execution_mode_foreground_requirement" to definition.foregroundRequirement,
            "embedded_script.execution_mode_notification_requirement" to definition.notificationRequirement,
            "embedded_script.execution_mode_allowed_providers" to definition.allowedProviders.joinToString(",").ifBlank { "none" },
            "embedded_script.execution_mode_restart_policy" to definition.restartPolicy,
            "embedded_script.execution_mode_resource_quota_profile" to definition.resourceQuotaProfile,
            "embedded_script.execution_mode_stop_semantics" to definition.stopSemantics,
            "embedded_script.long_running_requested" to isLongRunningRequest(definition.mode).toString(),
            "embedded_script.long_running_supported" to (
                definition.mode == MODE_INTERACTIVE_LONG_RUNNING && definition.supports(launchSurface)
                ).toString(),
            "embedded_script.sandboxed_runtime_requested" to (definition.mode == MODE_NODE_SANDBOXED).toString(),
            "embedded_script.sandboxed_runtime_supported" to (
                definition.mode == MODE_NODE_SANDBOXED && definition.supports(launchSurface)
                ).toString(),
        )
    }

    private fun unknownDefinition(mode: String): Definition {
        return Definition(
            mode = mode,
            status = "unknown",
            supportedLaunchSurfaces = emptySet(),
            timeoutPolicy = "none",
            foregroundRequirement = "none",
            notificationRequirement = "none",
            allowedProviders = emptyList(),
            restartPolicy = "never",
            resourceQuotaProfile = "none",
            stopSemantics = "reject_before_user_code",
            unsupportedReason = "The mode is not recognized by AutoJs6.",
        )
    }

    private val ONE_SHOT_PROVIDERS = listOf(
        "accessibility",
        "app",
        "clipboard",
        "device",
        "engines",
        "fetch",
        "image",
        "input_observer",
        "notifications",
        "ocr",
        "rhino",
        "sensors",
        "shell",
        "storage",
        "toast",
        "ui",
        "websocket",
        "work_manager",
    )

    private val DEFINITIONS = listOf(
        Definition(
            mode = MODE_ONE_SHOT,
            status = "available",
            supportedLaunchSurfaces = setOf(LAUNCH_SURFACE_SCRIPT),
            timeoutPolicy = "required_positive_timeout",
            foregroundRequirement = "transient_embedded_service",
            notificationRequirement = "transient_execution_notification",
            allowedProviders = ONE_SHOT_PROVIDERS,
            restartPolicy = "never",
            resourceQuotaProfile = "one_shot_default",
            stopSemantics = "timeout_or_user_stop_destroys_runtime",
        ),
        Definition(
            mode = MODE_INTERACTIVE_LONG_RUNNING,
            status = "partial",
            supportedLaunchSurfaces = setOf(LAUNCH_SURFACE_INTERACTIVE_SESSION),
            timeoutPolicy = "startup_timeout_stop_grace_and_watchdog",
            foregroundRequirement = "required",
            notificationRequirement = "persistent_stop_action_required",
            allowedProviders = listOf(
                "accessibility",
                "fetch",
                "image",
                "input_observer",
                "notifications",
                "ocr",
                "sensors",
                "shell",
                "storage",
                "ui",
                "websocket",
            ),
            restartPolicy = "never",
            resourceQuotaProfile = "interactive_long_running_strict",
            stopSemantics = "foreground_or_user_stop_then_graceful_destroy",
            unsupportedReason = "Interactive long-running mode is available only through the explicit interactive session launch surface; direct script launches remain one-shot.",
        ),
        Definition(
            mode = MODE_SCHEDULED,
            status = "partial",
            supportedLaunchSurfaces = setOf(LAUNCH_SURFACE_SCHEDULED_RUNNER),
            timeoutPolicy = "required_task_timeout",
            foregroundRequirement = "workmanager_policy_dependent",
            notificationRequirement = "task_visibility_required_before_promotion",
            allowedProviders = listOf(
                "fetch",
                "image",
                "notifications",
                "ocr",
                "storage",
                "work_manager",
            ),
            restartPolicy = "never_no_implicit_retry",
            resourceQuotaProfile = "scheduled_task_strict",
            stopSemantics = "cancel_work_then_destroy_runtime",
            unsupportedReason = "Scheduled Node execution is available only through the controlled WorkManager scheduled runner launch surface; direct script launches remain one-shot.",
        ),
        Definition(
            mode = MODE_WORKER_COMPUTATION,
            status = "reserved",
            supportedLaunchSurfaces = emptySet(),
            timeoutPolicy = "startup_and_task_timeout",
            foregroundRequirement = "not_applicable",
            notificationRequirement = "not_applicable",
            allowedProviders = emptyList(),
            restartPolicy = "never",
            resourceQuotaProfile = "worker_computation_restricted",
            stopSemantics = "terminate_worker_then_release_ports",
            unsupportedReason = "Worker computation mode remains behind the worker_threads capability isolation gates.",
        ),
        Definition(
            mode = MODE_NODE_SANDBOXED,
            status = "design_gated",
            supportedLaunchSurfaces = emptySet(),
            timeoutPolicy = "broker_startup_and_script_timeout",
            foregroundRequirement = "host_owned_visibility_required",
            notificationRequirement = "host_owned_stop_action_required",
            allowedProviders = emptyList(),
            restartPolicy = "never",
            resourceQuotaProfile = "hardened_sandbox_broker_only",
            stopSemantics = "broker_stop_then_isolated_process_destroy",
            unsupportedReason = "Hardened isolated runtime mode is a Phase 8 manifest POC and requires a Binder capability broker before execution.",
        ),
    )

    private val DEFINITIONS_BY_MODE = DEFINITIONS.associateBy { it.mode }
}
