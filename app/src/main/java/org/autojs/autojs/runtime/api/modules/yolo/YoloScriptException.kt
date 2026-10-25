package org.autojs.autojs.runtime.api.modules.yolo

/** Stable Preview error prefixes exposed to Rhino scripts. */
class YoloScriptException(
    @JvmField val code: String,
    detail: String,
    cause: Throwable? = null,
) : IllegalStateException("$code: $detail", cause) {

    companion object {
        const val COMPONENT_REQUIRED = "YOLO_COMPONENT_REQUIRED"
        const val INVALID_ARGUMENT = "YOLO_INVALID_ARGUMENT"
        const val MODEL_INVALID = "YOLO_MODEL_INVALID"
        const val MODEL_REJECTED = "YOLO_MODEL_REJECTED"
        const val UNSUPPORTED_CAPABILITY = "YOLO_UNSUPPORTED_CAPABILITY"
        const val PROVIDER_UNAVAILABLE = "YOLO_PROVIDER_UNAVAILABLE"
        const val SESSION_OPEN_FAILED = "YOLO_SESSION_OPEN_FAILED"
        const val DETECT_FAILED = "YOLO_DETECT_FAILED"
        const val SESSION_CLOSED = "YOLO_SESSION_CLOSED"
    }
}
