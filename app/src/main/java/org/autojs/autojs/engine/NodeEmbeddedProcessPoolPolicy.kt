package org.autojs.autojs.engine

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context

object NodeEmbeddedProcessPoolPolicy {

    const val SCHEMA = "autojs6-node-embedded-process-pool-v1"
    const val VERSION = 1
    const val STATUS_DESIGN_GATED = "design_gated"
    const val STATUS_EXPLICIT_SLOT_ENABLED = "explicit_slot_enabled"
    const val STATUS_DEFAULT_SCHEDULER_ENABLED = "default_scheduler_enabled"
    const val MODE_PRIMARY_ONLY = "primary_only"
    const val MODE_EXPLICIT_TWO_SLOT = "explicit_two_slot"
    const val MODE_DEFAULT_TWO_SLOT = "default_two_slot"
    const val PRIMARY_SLOT_ID = "primary"
    const val LONG_RUNNING_SLOT_ID = "long_running"
    const val OVERFLOW_SLOT_ID = "overflow_1"
    const val SLOT_SELECTION_LEGACY_DEFAULT = "legacy_default"
    const val SLOT_SELECTION_EXPLICIT_REQUEST = "explicit_request"
    const val SLOT_SELECTION_DEFAULT_SCHEDULER = "default_scheduler"
    const val SLOT_SELECTION_CURRENT_SERVICE = "current_service"
    const val SLOT_CRASH_FALLBACK_MODE_NO_RESTART = "no_restart_collect_marker"
    const val SLOT_CRASH_QUARANTINE_POLICY_NONE = "no_quarantine_until_pool_coordinator"
    const val SLOT_CRASH_SURVIVOR_POLICY_ALLOW_OTHER_SLOTS = "allow_other_slots_after_marker_collection"
    const val DEFAULT_EFFECTIVE_MAX_ACTIVE_SLOTS = 1
    const val EXPLICIT_EFFECTIVE_MAX_ACTIVE_SLOTS = 2
    const val DEFAULT_SCHEDULER_EFFECTIVE_MAX_ACTIVE_SLOTS = 2

    private const val MB = 1024L * 1024L
    private const val STANDARD_DEVICE_MIN_TOTAL_MB = 3_072L
    private const val LARGE_DEVICE_MIN_TOTAL_MB = 6_144L
    private const val STANDARD_DEVICE_MIN_MEMORY_CLASS_MB = 256
    private const val LARGE_DEVICE_MIN_LARGE_MEMORY_CLASS_MB = 512

    data class ProcessSlot(
        val id: String,
        val index: Int,
        val serviceClass: Class<out NativeNodeEmbeddedScriptService>,
        val processSuffix: String,
        val role: String,
        val launchEnabledByDefault: Boolean,
        val longRunningDedicated: Boolean,
    ) {
        val serviceClassName: String
            get() = serviceClass.name

        fun componentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, serviceClass)
    }

    data class DeviceMemoryProfile(
        val lowRamDevice: Boolean,
        val memoryClassMb: Int,
        val largeMemoryClassMb: Int,
        val totalMemoryMb: Long,
    )

    data class ProcessPoolPlan(
        val schema: String,
        val version: Int,
        val status: String,
        val mode: String,
        val multiProcessEnabled: Boolean,
        val declaredSlots: List<ProcessSlot>,
        val requestedSlotId: String,
        val selectedSlot: ProcessSlot,
        val slotSelectionSource: String,
        val effectiveMaxActiveSlots: Int,
        val recommendedMaxActiveSlots: Int,
        val longRunningDedicatedSlotId: String,
        val longRunningDedicatedSlotEnabled: Boolean,
        val defaultSchedulerEnabled: Boolean,
        val defaultSchedulerSlotIds: List<String>,
        val defaultSchedulerFallbackEnabled: Boolean,
        val slotCrashFallbackMode: String,
        val failedSlotReplacementEnabled: Boolean,
        val failedSlotQuarantinePolicy: String,
        val survivingSlotExecutionPolicy: String,
        val queueWhenPoolFullEnabled: Boolean,
        val queuePolicy: String,
        val oneActiveEnvironmentPerSlot: Boolean,
        val sharedV8EnvironmentAllowed: Boolean,
        val crashIsolationPolicy: String,
        val memoryProfile: DeviceMemoryProfile,
        val reason: String,
    ) {
        fun nativeValues(): Map<String, String> = linkedMapOf(
            "embedded_script.process_pool.schema" to schema,
            "embedded_script.process_pool.version" to version.toString(),
            "embedded_script.process_pool.status" to status,
            "embedded_script.process_pool.mode" to mode,
            "embedded_script.process_pool.enabled" to multiProcessEnabled.toString(),
            "embedded_script.process_pool.declared_slot_count" to declaredSlots.size.toString(),
            "embedded_script.process_pool.declared_slot_ids" to declaredSlots.joinToString(",") { it.id },
            "embedded_script.process_pool.requested_slot_id" to requestedSlotId,
            "embedded_script.process_pool.selected_slot_id" to selectedSlot.id,
            "embedded_script.process_pool.slot_selection_source" to slotSelectionSource,
            "embedded_script.process_pool.selected_service_class" to selectedSlot.serviceClassName,
            "embedded_script.process_pool.selected_process_suffix" to selectedSlot.processSuffix,
            "embedded_script.process_pool.effective_max_active_slots" to effectiveMaxActiveSlots.toString(),
            "embedded_script.process_pool.recommended_max_active_slots" to recommendedMaxActiveSlots.toString(),
            "embedded_script.process_pool.long_running_dedicated_slot_id" to longRunningDedicatedSlotId,
            "embedded_script.process_pool.long_running_dedicated_slot_enabled" to longRunningDedicatedSlotEnabled.toString(),
            "embedded_script.process_pool.default_scheduler_enabled" to defaultSchedulerEnabled.toString(),
            "embedded_script.process_pool.default_scheduler_slot_ids" to defaultSchedulerSlotIds.joinToString(","),
            "embedded_script.process_pool.default_scheduler_fallback_enabled" to defaultSchedulerFallbackEnabled.toString(),
            "embedded_script.process_pool.slot_crash_fallback_mode" to slotCrashFallbackMode,
            "embedded_script.process_pool.failed_slot_replacement_enabled" to failedSlotReplacementEnabled.toString(),
            "embedded_script.process_pool.failed_slot_quarantine_policy" to failedSlotQuarantinePolicy,
            "embedded_script.process_pool.surviving_slot_execution_policy" to survivingSlotExecutionPolicy,
            "embedded_script.process_pool.queue_when_full_enabled" to queueWhenPoolFullEnabled.toString(),
            "embedded_script.process_pool.queue_policy" to queuePolicy,
            "embedded_script.process_pool.one_active_environment_per_slot" to oneActiveEnvironmentPerSlot.toString(),
            "embedded_script.process_pool.shared_v8_environment_allowed" to sharedV8EnvironmentAllowed.toString(),
            "embedded_script.process_pool.crash_isolation_policy" to crashIsolationPolicy,
            "embedded_script.process_pool.low_ram_device" to memoryProfile.lowRamDevice.toString(),
            "embedded_script.process_pool.memory_class_mb" to memoryProfile.memoryClassMb.toString(),
            "embedded_script.process_pool.large_memory_class_mb" to memoryProfile.largeMemoryClassMb.toString(),
            "embedded_script.process_pool.total_memory_mb" to memoryProfile.totalMemoryMb.toString(),
            "embedded_script.process_pool.reason" to reason,
        )
    }

    val declaredSlots: List<ProcessSlot> = listOf(
        ProcessSlot(
            id = PRIMARY_SLOT_ID,
            index = 0,
            serviceClass = NativeNodeEmbeddedScriptService::class.java,
            processSuffix = ":nodejs_embedded",
            role = "primary_one_shot_and_current_interactive",
            launchEnabledByDefault = true,
            longRunningDedicated = false,
        ),
        ProcessSlot(
            id = LONG_RUNNING_SLOT_ID,
            index = 1,
            serviceClass = NativeNodeEmbeddedScriptServiceSlot1::class.java,
            processSuffix = ":nodejs_embedded_1",
            role = "dedicated_interactive_long_running_reserved",
            launchEnabledByDefault = false,
            longRunningDedicated = true,
        ),
        ProcessSlot(
            id = OVERFLOW_SLOT_ID,
            index = 2,
            serviceClass = NativeNodeEmbeddedScriptServiceSlot2::class.java,
            processSuffix = ":nodejs_embedded_2",
            role = "one_shot_overflow_reserved",
            launchEnabledByDefault = false,
            longRunningDedicated = false,
        ),
    )

    val primarySlot: ProcessSlot = declaredSlots.first { it.id == PRIMARY_SLOT_ID }

    val longRunningSlot: ProcessSlot = declaredSlots.first { it.id == LONG_RUNNING_SLOT_ID }

    val overflowSlot: ProcessSlot = declaredSlots.first { it.id == OVERFLOW_SLOT_ID }

    fun slotForServiceClassName(className: String?): ProcessSlot? {
        if (className.isNullOrBlank()) {
            return null
        }
        return declaredSlots.firstOrNull { it.serviceClassName == className }
    }

    fun plan(
        context: Context,
        executionMode: String? = NodeExecutionModePolicy.MODE_ONE_SHOT,
        launchSurface: String = NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT,
        currentServiceClassName: String? = null,
        requestedSlotId: String? = null,
        slotSelectionSource: String? = null,
        defaultSchedulerEnabled: Boolean = false,
    ): ProcessPoolPlan {
        val memoryProfile = deviceMemoryProfile(context)
        val normalizedMode = NodeExecutionModePolicy.normalize(executionMode)
        val currentSlot = slotForServiceClassName(currentServiceClassName)
        val normalizedRequestedSlotId = requestedSlotId?.trim().orEmpty()
        val normalizedSlotSelectionSource = slotSelectionSource?.trim().orEmpty()
        val defaultSchedulerSlot = explicitSelectableSlot(normalizedRequestedSlotId)
            ?.takeIf { normalizedSlotSelectionSource == SLOT_SELECTION_DEFAULT_SCHEDULER }
        val requestedSlot = explicitSelectableSlot(normalizedRequestedSlotId)
            ?.takeIf { defaultSchedulerSlot == null }
        val explicitSlotRequested = normalizedRequestedSlotId.isNotBlank() && defaultSchedulerSlot == null
        val selectedSlot = currentSlot ?: defaultSchedulerSlot ?: requestedSlot ?: primarySlot
        val selectedByDefaultScheduler = defaultSchedulerEnabled ||
                normalizedSlotSelectionSource == SLOT_SELECTION_DEFAULT_SCHEDULER ||
                defaultSchedulerSlot != null
        val explicitSlotEnabled = requestedSlot != null || currentSlot?.id == OVERFLOW_SLOT_ID
        val multiProcessEnabled = explicitSlotEnabled || selectedByDefaultScheduler
        val effectiveMaxActiveSlots = when {
            selectedByDefaultScheduler -> DEFAULT_SCHEDULER_EFFECTIVE_MAX_ACTIVE_SLOTS
            explicitSlotEnabled -> EXPLICIT_EFFECTIVE_MAX_ACTIVE_SLOTS
            else -> DEFAULT_EFFECTIVE_MAX_ACTIVE_SLOTS
        }
        val resolvedSelectionSource = when {
            selectedByDefaultScheduler -> SLOT_SELECTION_DEFAULT_SCHEDULER
            requestedSlot != null -> SLOT_SELECTION_EXPLICIT_REQUEST
            currentSlot != null && currentSlot.id != PRIMARY_SLOT_ID -> SLOT_SELECTION_CURRENT_SERVICE
            else -> SLOT_SELECTION_LEGACY_DEFAULT
        }
        val reason = when {
            currentSlot != null && requestedSlot != null && currentSlot.id != requestedSlot.id ->
                "current_service_slot_overrides_requested:${currentSlot.id}"
            selectedByDefaultScheduler && currentSlot != null ->
                "default_scheduler_selected:${currentSlot.id}"
            selectedByDefaultScheduler && defaultSchedulerSlot != null ->
                "default_scheduler_selected:${defaultSchedulerSlot.id}"
            currentSlot != null && requestedSlot != null ->
                "explicit_process_slot_selected:${requestedSlot.id}"
            currentSlot != null && currentSlot.id != PRIMARY_SLOT_ID ->
                "reserved_slot_direct_start_detected:current_service_slot:${currentSlot.id}"
            requestedSlot != null ->
                "explicit_process_slot_selected:${requestedSlot.id}"
            explicitSlotRequested ->
                "explicit_process_slot_rejected:$normalizedRequestedSlotId"
            normalizedMode == NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING ||
                launchSurface == NodeExecutionModePolicy.LAUNCH_SURFACE_INTERACTIVE_SESSION ->
                "dedicated_long_running_slot_reserved_until_pool_promotion"
            else -> "multi_process_pool_design_gated_primary_only"
        }
        return ProcessPoolPlan(
            schema = SCHEMA,
            version = VERSION,
            status = when {
                selectedByDefaultScheduler -> STATUS_DEFAULT_SCHEDULER_ENABLED
                explicitSlotEnabled -> STATUS_EXPLICIT_SLOT_ENABLED
                else -> STATUS_DESIGN_GATED
            },
            mode = when {
                selectedByDefaultScheduler -> MODE_DEFAULT_TWO_SLOT
                explicitSlotEnabled -> MODE_EXPLICIT_TWO_SLOT
                else -> MODE_PRIMARY_ONLY
            },
            multiProcessEnabled = multiProcessEnabled,
            declaredSlots = declaredSlots,
            requestedSlotId = normalizedRequestedSlotId,
            selectedSlot = selectedSlot,
            slotSelectionSource = resolvedSelectionSource,
            effectiveMaxActiveSlots = effectiveMaxActiveSlots,
            recommendedMaxActiveSlots = recommendedMaxActiveSlots(memoryProfile),
            longRunningDedicatedSlotId = longRunningSlot.id,
            longRunningDedicatedSlotEnabled = false,
            defaultSchedulerEnabled = selectedByDefaultScheduler,
            defaultSchedulerSlotIds = defaultSchedulerSlotIds(),
            defaultSchedulerFallbackEnabled = false,
            slotCrashFallbackMode = SLOT_CRASH_FALLBACK_MODE_NO_RESTART,
            failedSlotReplacementEnabled = false,
            failedSlotQuarantinePolicy = SLOT_CRASH_QUARANTINE_POLICY_NONE,
            survivingSlotExecutionPolicy = SLOT_CRASH_SURVIVOR_POLICY_ALLOW_OTHER_SLOTS,
            queueWhenPoolFullEnabled = selectedByDefaultScheduler,
            queuePolicy = if (selectedByDefaultScheduler) {
                "bounded_wait_for_available_process_slot"
            } else {
                "caller_side_queue_only_until_process_pool_enabled"
            },
            oneActiveEnvironmentPerSlot = true,
            sharedV8EnvironmentAllowed = false,
            crashIsolationPolicy = "per_process_slot_when_enabled",
            memoryProfile = memoryProfile,
            reason = reason,
        )
    }

    fun defaultSchedulerSlotIds(): List<String> = listOf(PRIMARY_SLOT_ID, OVERFLOW_SLOT_ID)

    fun defaultSchedulerEligible(
        context: Context,
        executionMode: String? = NodeExecutionModePolicy.MODE_ONE_SHOT,
        launchSurface: String = NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT,
    ): Boolean {
        return defaultSchedulerEligible(
            memoryProfile = deviceMemoryProfile(context),
            executionMode = executionMode,
            launchSurface = launchSurface,
        )
    }

    fun defaultSchedulerEligible(
        memoryProfile: DeviceMemoryProfile,
        executionMode: String? = NodeExecutionModePolicy.MODE_ONE_SHOT,
        launchSurface: String = NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT,
    ): Boolean {
        return NodeExecutionModePolicy.normalize(executionMode) == NodeExecutionModePolicy.MODE_ONE_SHOT &&
                launchSurface == NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT &&
                recommendedMaxActiveSlots(memoryProfile) >= DEFAULT_SCHEDULER_EFFECTIVE_MAX_ACTIVE_SLOTS
    }

    fun defaultSchedulerSelectableSlots(
        context: Context,
        executionMode: String? = NodeExecutionModePolicy.MODE_ONE_SHOT,
        launchSurface: String = NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT,
    ): List<ProcessSlot> {
        return if (defaultSchedulerEligible(context, executionMode, launchSurface)) {
            listOf(primarySlot, overflowSlot)
        } else {
            listOf(primarySlot)
        }
    }

    private fun explicitSelectableSlot(requestedSlotId: String): ProcessSlot? {
        return when (requestedSlotId) {
            PRIMARY_SLOT_ID -> primarySlot
            OVERFLOW_SLOT_ID -> overflowSlot
            else -> null
        }
    }

    fun deviceMemoryProfile(context: Context): DeviceMemoryProfile {
        val manager = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        runCatching { manager?.getMemoryInfo(memoryInfo) }
        return DeviceMemoryProfile(
            lowRamDevice = manager?.isLowRamDevice ?: false,
            memoryClassMb = manager?.memoryClass ?: 0,
            largeMemoryClassMb = manager?.largeMemoryClass ?: 0,
            totalMemoryMb = memoryInfo.totalMem.takeIf { it > 0L }?.div(MB) ?: 0L,
        )
    }

    fun recommendedMaxActiveSlots(profile: DeviceMemoryProfile): Int {
        val recommended = when {
            profile.lowRamDevice -> 1
            profile.totalMemoryMb >= LARGE_DEVICE_MIN_TOTAL_MB &&
                profile.largeMemoryClassMb >= LARGE_DEVICE_MIN_LARGE_MEMORY_CLASS_MB -> 3
            profile.totalMemoryMb >= STANDARD_DEVICE_MIN_TOTAL_MB &&
                profile.memoryClassMb >= STANDARD_DEVICE_MIN_MEMORY_CLASS_MB -> 2
            else -> 1
        }
        return recommended.coerceIn(1, declaredSlots.size)
    }
}
