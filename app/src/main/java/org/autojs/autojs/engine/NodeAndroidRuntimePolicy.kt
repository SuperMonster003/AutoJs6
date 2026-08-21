package org.autojs.autojs.engine

import android.os.Build
import org.json.JSONObject

object NodeAndroidRuntimePolicy {

    const val QUOTA_STATE_NOT_ENFORCED = "not_enforced_for_api_level"
    const val QUOTA_STATE_SHARED = "jobscheduler_quota_shared"
    const val QUOTA_STATE_EXHAUSTED = "quota_exhausted"

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

    private const val ANDROID_16_API_LEVEL = 36
}
