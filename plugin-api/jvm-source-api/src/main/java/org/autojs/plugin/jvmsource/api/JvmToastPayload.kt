package org.autojs.plugin.jvmsource.api

/** Canonical one-field JSON payload used by the allowlisted `toast.show` bridge call. */
object JvmToastPayload {
    @JvmStatic
    fun encode(message: String): String = JvmCanonicalTextPayload.encode(
        fieldName = "message",
        text = message,
        minimumBytes = 1,
        maximumBytes = JvmSourceContract.MAX_TOAST_MESSAGE_BYTES,
        valueName = "Toast message",
    )

    @JvmStatic
    fun decode(payloadJson: String): String = JvmCanonicalTextPayload.decode(
        fieldName = "message",
        payloadJson = payloadJson,
        minimumBytes = 1,
        maximumBytes = JvmSourceContract.MAX_TOAST_MESSAGE_BYTES,
        valueName = "Toast message",
    )
}
