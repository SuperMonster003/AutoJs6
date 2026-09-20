package org.autojs.plugin.epub.api

/**
 * Error codes of Bundle answers ([EpubContract.KEY_ERROR_CODE]), of the exceptions thrown by
 * calls that do not answer with a Bundle, of [EpubContract.EVENT_ERROR] events and of the
 * host-side `EpubError`. The set is closed for contract version 1; a code the host does not
 * know is reported as [INTERNAL].
 */
object EpubErrorCodes {

    const val PLUGIN_UNAVAILABLE = "PLUGIN_UNAVAILABLE"
    const val PLUGIN_DISABLED = "PLUGIN_DISABLED"
    const val PLUGIN_INCOMPATIBLE = "PLUGIN_INCOMPATIBLE"
    const val FILE_NOT_FOUND = "FILE_NOT_FOUND"
    const val FILE_UNREADABLE = "FILE_UNREADABLE"
    const val NOT_EPUB = "NOT_EPUB"
    const val PARSE_FAILED = "PARSE_FAILED"
    const val ENCRYPTED = "ENCRYPTED"
    const val RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND"
    const val LIMIT_EXCEEDED = "LIMIT_EXCEEDED"
    const val INVALID_ARGUMENT = "INVALID_ARGUMENT"
    const val SESSION_CLOSED = "SESSION_CLOSED"
    const val SESSION_REPLACED = "SESSION_REPLACED"
    const val READER_NOT_VISIBLE = "READER_NOT_VISIBLE"
    const val UNSUPPORTED_PREFERENCE = "UNSUPPORTED_PREFERENCE"
    const val CANCELLED = "CANCELLED"
    const val TIMEOUT = "TIMEOUT"
    const val IO = "IO"
    const val INTERNAL = "INTERNAL"

    /** Every code of contract version 1, in the order of roadmap appendix B.4. */
    val ALL: List<String> = listOf(
        PLUGIN_UNAVAILABLE,
        PLUGIN_DISABLED,
        PLUGIN_INCOMPATIBLE,
        FILE_NOT_FOUND,
        FILE_UNREADABLE,
        NOT_EPUB,
        PARSE_FAILED,
        ENCRYPTED,
        RESOURCE_NOT_FOUND,
        LIMIT_EXCEEDED,
        INVALID_ARGUMENT,
        SESSION_CLOSED,
        SESSION_REPLACED,
        READER_NOT_VISIBLE,
        UNSUPPORTED_PREFERENCE,
        CANCELLED,
        TIMEOUT,
        IO,
        INTERNAL,
    )

    /** Codes the host produces itself before any Binder call reaches the plugin. */
    val HOST_ONLY: Set<String> = setOf(PLUGIN_UNAVAILABLE, PLUGIN_DISABLED, PLUGIN_INCOMPATIBLE)

    private const val SEPARATOR = ": "

    fun isKnown(code: String?): Boolean = code != null && code in ALL

    /**
     * Message of an exception thrown across Binder by a call that cannot answer with a Bundle:
     * the code, [SEPARATOR] and the detail (empty detail yields the bare code).
     */
    fun encode(code: String, detail: String? = null): String {
        require(isKnown(code)) { "Unknown EPUB error code: $code" }
        val text = detail?.trim().orEmpty()
        return if (text.isEmpty()) code else code + SEPARATOR + text
    }

    /**
     * Splits an [encode]d message into its code and detail; null when the message does not start
     * with a known code, in which case the caller reports [INTERNAL] with the whole message.
     */
    fun decode(message: String?): Pair<String, String>? {
        val text = message?.trim() ?: return null
        val code = text.substringBefore(SEPARATOR)
        if (!isKnown(code)) return null
        val detail = if (code.length == text.length) "" else text.substring(code.length + SEPARATOR.length)
        return code to detail
    }
}
