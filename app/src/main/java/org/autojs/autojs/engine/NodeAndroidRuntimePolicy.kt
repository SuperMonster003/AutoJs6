package org.autojs.autojs.engine

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import org.autojs.autojs.util.ForegroundServiceUtils.FOREGROUND_SERVICE_TYPE_UNKNOWN
import org.json.JSONArray
import org.json.JSONObject

object NodeAndroidRuntimePolicy {

    const val START_FAILURE_BACKGROUND_RESTRICTED = "foreground_service_start_not_allowed"
    const val START_FAILURE_MISSING_TYPE = "missing_foreground_service_type"
    const val START_FAILURE_INVALID_TYPE = "invalid_foreground_service_type"
    const val START_FAILURE_FAILED = "foreground_service_start_failed"
    const val QUOTA_STATE_NOT_ENFORCED = "not_enforced_for_api_level"
    const val QUOTA_STATE_SHARED = "jobscheduler_quota_shared"
    const val QUOTA_STATE_EXHAUSTED = "quota_exhausted"

    fun embeddedServiceDiagnostics(
        context: Context,
        serviceClass: Class<*>,
        requestedForegroundServiceType: Int,
    ): JSONObject {
        val appContext = context.applicationContext
        val component = ComponentName(appContext, serviceClass)
        val serviceInfo = serviceInfo(appContext, component)
        val permissions = packageInfo(appContext)?.requestedPermissions.orEmpty().toSet()
        val manifestType = foregroundServiceType(serviceInfo)
        val validation = validateForegroundServiceType(
            sdkInt = Build.VERSION.SDK_INT,
            manifestType = manifestType,
            requestedType = requestedForegroundServiceType,
        )
        return JSONObject().apply {
            put("sdkInt", Build.VERSION.SDK_INT)
            put("serviceName", component.className)
            put("manifestServiceFound", serviceInfo != null)
            put("requestedForegroundServiceType", requestedForegroundServiceType)
            put("requestedForegroundServiceTypeNames", foregroundServiceTypeNames(requestedForegroundServiceType))
            put("manifestForegroundServiceType", manifestType)
            put("manifestForegroundServiceTypeNames", foregroundServiceTypeNames(manifestType))
            put("foregroundServiceTypeValid", validation.valid)
            put("foregroundServiceTypeIssue", validation.issue ?: JSONObject.NULL)
            put(
                "declaresForegroundServicePermission",
                Manifest.permission.FOREGROUND_SERVICE in permissions,
            )
            put(
                "declaresForegroundServiceSpecialUsePermission",
                ANDROID_PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE in permissions,
            )
            put("backgroundStartRestrictionApplies", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            put("foregroundServiceTypeRequired", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            put("foregroundServiceOnTimeoutSupported", Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
            put("workManagerLongRunningUsesJobQuota", Build.VERSION.SDK_INT >= ANDROID_16_API_LEVEL)
        }
    }

    fun validateForegroundServiceType(
        sdkInt: Int,
        manifestType: Int,
        requestedType: Int,
    ): ForegroundServiceTypeValidation {
        if (sdkInt < Build.VERSION_CODES.Q || requestedType == FOREGROUND_SERVICE_TYPE_UNKNOWN) {
            return ForegroundServiceTypeValidation(valid = true)
        }
        if (manifestType == ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE) {
            return ForegroundServiceTypeValidation(
                valid = false,
                issue = START_FAILURE_MISSING_TYPE,
            )
        }
        if ((manifestType and requestedType) != requestedType) {
            return ForegroundServiceTypeValidation(
                valid = false,
                issue = START_FAILURE_INVALID_TYPE,
            )
        }
        return ForegroundServiceTypeValidation(valid = true)
    }

    fun serviceStartFailureDiagnostics(
        throwable: Throwable,
        serviceClassName: String,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): JSONObject {
        return serviceStartFailureDiagnosticsForClass(
            className = throwable.javaClass.name,
            message = throwable.message.orEmpty(),
            serviceClassName = serviceClassName,
            sdkInt = sdkInt,
        )
    }

    fun serviceStartFailureDiagnosticsForClass(
        className: String,
        message: String,
        serviceClassName: String,
        sdkInt: Int,
    ): JSONObject {
        val failure = when {
            className.endsWith("ForegroundServiceStartNotAllowedException") -> ServiceStartFailure(
                reason = START_FAILURE_BACKGROUND_RESTRICTED,
                errorName = "EmbeddedNodeForegroundServiceStartNotAllowedError",
                errorCode = NodeRuntimeErrorCodes.FOREGROUND_SERVICE_START_NOT_ALLOWED,
                userMessage = "Android denied starting the embedded Node foreground service from the current app state.",
            )
            className.endsWith("MissingForegroundServiceTypeException") -> ServiceStartFailure(
                reason = START_FAILURE_MISSING_TYPE,
                errorName = "EmbeddedNodeForegroundServiceTypeError",
                errorCode = NodeRuntimeErrorCodes.FOREGROUND_SERVICE_TYPE_INVALID,
                userMessage = "Embedded Node foreground service is missing a required Android foregroundServiceType.",
            )
            className.endsWith("InvalidForegroundServiceTypeException") -> ServiceStartFailure(
                reason = START_FAILURE_INVALID_TYPE,
                errorName = "EmbeddedNodeForegroundServiceTypeError",
                errorCode = NodeRuntimeErrorCodes.FOREGROUND_SERVICE_TYPE_INVALID,
                userMessage = "Embedded Node foreground service requested a type not declared by the manifest.",
            )
            else -> ServiceStartFailure(
                reason = START_FAILURE_FAILED,
                errorName = "EmbeddedNodeForegroundServiceStartError",
                errorCode = NodeRuntimeErrorCodes.EMBEDDED_NODE_INVALID_STATE,
                userMessage = "Embedded Node foreground service failed to start.",
            )
        }
        return JSONObject().apply {
            put("sdkInt", sdkInt)
            put("serviceName", serviceClassName)
            put("reason", failure.reason)
            put("errorName", failure.errorName)
            put("errorCode", failure.errorCode)
            put("message", failure.userMessage)
            put("androidExceptionClass", className)
            put("androidExceptionMessage", message)
            put("backgroundStartRestrictionApplies", sdkInt >= Build.VERSION_CODES.S)
            put("foregroundServiceTypeRequired", sdkInt >= Build.VERSION_CODES.Q)
        }
    }

    fun foregroundServiceTimeoutDiagnostics(
        startId: Int,
        foregroundServiceType: Int,
        serviceClassName: String,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): JSONObject {
        return JSONObject().apply {
            put("sdkInt", sdkInt)
            put("serviceName", serviceClassName)
            put("startId", startId)
            put("foregroundServiceType", foregroundServiceType)
            put("foregroundServiceTypeNames", foregroundServiceTypeNames(foregroundServiceType))
            put("reason", "foreground_service_timeout")
            put("requiresImmediateStopSelf", true)
            put("timeoutCallbackSupported", sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
            put("errorCode", NodeRuntimeErrorCodes.SCRIPT_TIMEOUT)
        }
    }

    fun workManagerQuotaDiagnostics(
        sdkInt: Int = Build.VERSION.SDK_INT,
        quotaExhausted: Boolean = false,
    ): JSONObject {
        val state = when {
            quotaExhausted -> QUOTA_STATE_EXHAUSTED
            sdkInt >= ANDROID_16_API_LEVEL -> QUOTA_STATE_SHARED
            else -> QUOTA_STATE_NOT_ENFORCED
        }
        return JSONObject().apply {
            put("sdkInt", sdkInt)
            put("scheduler", NodeWorkManagerBridgeScheduler.SCHEDULER_NAME)
            put("longRunningWorkerUsesForegroundService", true)
            put("jobSchedulerQuotaApplies", sdkInt >= ANDROID_16_API_LEVEL)
            put("quotaState", state)
            put("errorCode", if (quotaExhausted) NodeRuntimeErrorCodes.WORK_MANAGER_QUOTA_EXHAUSTED else JSONObject.NULL)
            put(
                "recommendedAction",
                if (quotaExhausted) {
                    "defer work or use an explicit user-visible foreground runtime for user-initiated long work"
                } else {
                    "keep scheduled Node tasks bounded and avoid long-running periodic processes"
                },
            )
        }
    }

    fun apiLevelPolicyTable(): JSONArray {
        return JSONArray().apply {
            put(policyRow(31, "foreground_service_background_start_restricted", "Background foreground-service starts can be denied."))
            put(policyRow(34, "foreground_service_type_validation", "Foreground services must declare an appropriate type and permission."))
            put(policyRow(35, "foreground_service_on_timeout", "Timed-out foreground services must call stopSelf() within the grace window."))
            put(policyRow(36, "workmanager_long_running_job_quota", "Long-running WorkManager foreground workers share JobScheduler quota."))
        }
    }

    fun nativeValues(prefix: String, json: JSONObject): Map<String, String> {
        val values = linkedMapOf<String, String>()
        json.keys().forEach { key ->
            val value = json.opt(key)
            if (value !is JSONObject && value !is JSONArray) {
                values["$prefix.$key"] = when (value) {
                    null, JSONObject.NULL -> ""
                    else -> value.toString()
                }
            }
        }
        return values
    }

    fun foregroundServiceTypeNames(type: Int): String {
        if (type == FOREGROUND_SERVICE_TYPE_UNKNOWN) {
            return "unknown"
        }
        if (type == ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE) {
            return "none"
        }
        val names = mutableListOf<String>()
        fun addIfSet(flag: Int, name: String) {
            if ((type and flag) == flag) {
                names += name
            }
        }
        addIfSet(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, "dataSync")
        addIfSet(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, "mediaPlayback")
        addIfSet(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION, "mediaProjection")
        addIfSet(ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL, "phoneCall")
        addIfSet(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, "location")
        addIfSet(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE, "connectedDevice")
        addIfSet(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE, "microphone")
        addIfSet(ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA, "camera")
        addIfSet(ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH, "health")
        addIfSet(ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING, "remoteMessaging")
        addIfSet(ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE, "shortService")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            addIfSet(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, "specialUse")
            addIfSet(ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED, "systemExempted")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            addIfSet(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING, "mediaProcessing")
        }
        return names.ifEmpty { listOf("custom:$type") }.joinToString(",")
    }

    private fun serviceInfo(context: Context, component: ComponentName): ServiceInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getServiceInfo(
                    component,
                    PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)
            }
        }.getOrNull()
    }

    private fun packageInfo(context: Context): PackageInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            }
        }.getOrNull()
    }

    private fun foregroundServiceType(serviceInfo: ServiceInfo?): Int {
        if (serviceInfo == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return FOREGROUND_SERVICE_TYPE_UNKNOWN
        }
        return serviceInfo.foregroundServiceType
    }

    private fun policyRow(apiLevel: Int, id: String, note: String): JSONObject {
        return JSONObject().apply {
            put("apiLevel", apiLevel)
            put("id", id)
            put("applies", Build.VERSION.SDK_INT >= apiLevel)
            put("note", note)
        }
    }

    data class ForegroundServiceTypeValidation(
        val valid: Boolean,
        val issue: String? = null,
    )

    private data class ServiceStartFailure(
        val reason: String,
        val errorName: String,
        val errorCode: String,
        val userMessage: String,
    )

    private const val ANDROID_16_API_LEVEL = 36
    private const val ANDROID_PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE =
        "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
}
