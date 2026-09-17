package org.autojs.plugin.jvmsource.api

/** Canonical payloads used by the allowlisted `clipboard.get` and `clipboard.set` calls. */
object JvmClipboardPayload {
    const val GET_REQUEST_JSON = "{}"

    @JvmStatic
    fun validateGetRequest(payloadJson: String) {
        require(payloadJson == GET_REQUEST_JSON) { "Clipboard get payload must be the canonical empty object" }
    }

    /** Encodes either a `clipboard.set` request or a successful `clipboard.get` response. */
    @JvmStatic
    fun encodeText(text: String): String = JvmCanonicalTextPayload.encode(
        fieldName = "text",
        text = text,
        minimumBytes = 0,
        maximumBytes = JvmSourceContract.MAX_CLIPBOARD_TEXT_BYTES,
        valueName = "Clipboard text",
    )

    /** Decodes either a `clipboard.set` request or a successful `clipboard.get` response. */
    @JvmStatic
    fun decodeText(payloadJson: String): String = JvmCanonicalTextPayload.decode(
        fieldName = "text",
        payloadJson = payloadJson,
        minimumBytes = 0,
        maximumBytes = JvmSourceContract.MAX_CLIPBOARD_TEXT_BYTES,
        valueName = "Clipboard text",
    )
}
