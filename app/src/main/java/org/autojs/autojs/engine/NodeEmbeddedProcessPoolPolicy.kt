package org.autojs.autojs.engine

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context

object NodeEmbeddedProcessPoolPolicy {

    const val SCHEMA = "autojs6-node-embedded-process-pool-v1"
    const val VERSION = 1
    const val STATUS_DESIGN_GATED = "design_gated"
    const val STATUS_EXPLICIT_SLOT_ENABLED = "explicit_slot_enabled"
    const val MODE_PRIMARY_ONLY = "primary_only"
    const val MODE_EXPLICIT_TWO_SLOT = "explicit_two_slot"
    const val PRIMARY_SLOT_ID = "primary"
    const val LONG_RUNNING_SLOT_ID = "long_running"
    const val OVERFLOW_SLOT_ID = "overflow_1"
    const val DEFAULT_EFFECTIVE_MAX_ACTIVE_SLOTS = 1
    const val EXPLICIT_EFFECTIVE_MAX_ACTIVE_SLOTS = 2

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
        val effectiveMaxActiveSlots: Int,
        val recommendedMaxActiveSlots: Int,
        val longRunningDedicatedSlotId: String,
        val longRunningDedicatedSlotEnabled: Boolean,
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
            "embedded_script.process_pool.selected_service_class" to selectedSlot.serviceClassName,
            "embedded_script.process_pool.selected_process_suffix" to selectedSlot.processSuffix,
            "embedded_script.process_pool.effective_max_active_slots" to effectiveMaxActiveSlots.toString(),
            "embedded_script.process_pool.recommended_max_active_slots" to recommendedMaxActiveSlots.toString(),
            "embedded_script.process_pool.long_running_dedicated_slot_id" to longRunningDedicatedSlotId,
            "embedded_script.process_pool.long_running_dedicated_slot_enabled" to longRunningDedicatedSlotEnabled.toString(),
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
    ): ProcessPoolPlan {
        val memoryProfile = deviceMemoryProfile(context)
        val normalizedMode = NodeExecutionModePolicy.normalize(executionMode)
        val currentSlot = slotForServiceClassName(currentServiceClassName)
        val normalizedRequestedSlotId = requestedSlotId?.trim().orEmpty()
        val requestedSlot = explicitSelectableSlot(normalizedRequestedSlotId)
        val explicitSlotRequested = normalizedRequestedSlotId.isNotBlank()
        val explicitSlotEnabled = requestedSlot != null || currentSlot?.id == OVERFLOW_SLOT_ID
        val selectedSlot = currentSlot ?: requestedSlot ?: primarySlot
        val reason = when {
            currentSlot != null && requestedSlot != null && currentSlot.id != requestedSlot.id ->
                "current_service_slot_overrides_requested:${currentSlot.id}"
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
            status = if (explicitSlotEnabled) STATUS_EXPLICIT_SLOT_ENABLED else STATUS_DESIGN_GATED,
            mode = if (explicitSlotEnabled) MODE_EXPLICIT_TWO_SLOT else MODE_PRIMARY_ONLY,
            multiProcessEnabled = explicitSlotEnabled,
            declaredSlots = declaredSlots,
            requestedSlotId = normalizedRequestedSlotId,
            selectedSlot = selectedSlot,
            effectiveMaxActiveSlots = if (explicitSlotEnabled) {
                EXPLICIT_EFFECTIVE_MAX_ACTIVE_SLOTS
            } else {
                DEFAULT_EFFECTIVE_MAX_ACTIVE_SLOTS
            },
            recommendedMaxActiveSlots = recommendedMaxActiveSlots(memoryProfile),
            longRunningDedicatedSlotId = longRunningSlot.id,
            longRunningDedicatedSlotEnabled = false,
            queueWhenPoolFullEnabled = false,
            queuePolicy = "caller_side_queue_only_until_process_pool_enabled",
            oneActiveEnvironmentPerSlot = true,
            sharedV8EnvironmentAllowed = false,
            crashIsolationPolicy = "per_process_slot_when_enabled",
            memoryProfile = memoryProfile,
            reason = reason,
        )
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
