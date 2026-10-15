package org.autojs.plugin.gallery.api

/**
 * Error codes of Bundle answers ([GalleryContract.KEY_ERROR_CODE]), of the exceptions thrown by
 * calls that do not answer with a Bundle, of [GalleryContract.EVENT_ERROR] events and of the
 * host-side `GalleryError`. The set is closed for contract version 1; a code the host does not know
 * is reported as [INTERNAL].
 */
object GalleryErrorCodes {

    const val PLUGIN_UNAVAILABLE = "PLUGIN_UNAVAILABLE"
    const val PLUGIN_DISABLED = "PLUGIN_DISABLED"
    const val PLUGIN_INCOMPATIBLE = "PLUGIN_INCOMPATIBLE"

    /** A path the script passed does not exist or is not a regular file. */
    const val FILE_NOT_FOUND = "FILE_NOT_FOUND"

    /** A source exists but cannot be read (the host could not open it, or the plugin could not read the descriptor). */
    const val FILE_UNREADABLE = "FILE_UNREADABLE"

    /** The image format is not one the plugin opens, or not one it can write (`format`). */
    const val UNSUPPORTED_FORMAT = "UNSUPPORTED_FORMAT"

    /** The source has a supported type but its content does not decode. */
    const val DECODE_FAILED = "DECODE_FAILED"

    /** A count, size or pixel ceiling of [GalleryContract] was exceeded. */
    const val LIMIT_EXCEEDED = "LIMIT_EXCEEDED"

    const val INVALID_ARGUMENT = "INVALID_ARGUMENT"

    /** The plugin has no photo access and the user refused it in the picker. */
    const val PERMISSION_REQUIRED = "PERMISSION_REQUIRED"

    /** A path lies where the script may not read or write, or every picked image did. */
    const val ACCESS_DENIED = "ACCESS_DENIED"

    /** The requested output path already exists and `overwrite` is not set. */
    const val OUTPUT_EXISTS = "OUTPUT_EXISTS"

    const val SESSION_CLOSED = "SESSION_CLOSED"
    const val SESSION_REPLACED = "SESSION_REPLACED"
    const val CANCELLED = "CANCELLED"
    const val TIMEOUT = "TIMEOUT"
    const val IO = "IO"
    const val INTERNAL = "INTERNAL"

    /** Every code of contract version 1, in the order of `docs/dev/gallery-plugin-protocol-v1.md`. */
    val ALL: List<String> = listOf(
        PLUGIN_UNAVAILABLE,
        PLUGIN_DISABLED,
        PLUGIN_INCOMPATIBLE,
        FILE_NOT_FOUND,
        FILE_UNREADABLE,
        UNSUPPORTED_FORMAT,
        DECODE_FAILED,
        LIMIT_EXCEEDED,
        INVALID_ARGUMENT,
        PERMISSION_REQUIRED,
        ACCESS_DENIED,
        OUTPUT_EXISTS,
        SESSION_CLOSED,
        SESSION_REPLACED,
        CANCELLED,
        TIMEOUT,
        IO,
        INTERNAL,
    )

    /** Codes the host produces itself: discovery, and the checks of the script's paths and outputs. */
    val HOST_ONLY: Set<String> = setOf(PLUGIN_UNAVAILABLE, PLUGIN_DISABLED, PLUGIN_INCOMPATIBLE, FILE_NOT_FOUND, ACCESS_DENIED, OUTPUT_EXISTS)

    private const val SEPARATOR = ": "

    fun isKnown(code: String?): Boolean = code != null && code in ALL

    /**
     * Message of an exception thrown across Binder by a call that cannot answer with a Bundle: the
     * code, [SEPARATOR] and the detail (empty detail yields the bare code).
     */
    fun encode(code: String, detail: String? = null): String {
        require(isKnown(code)) { "Unknown gallery error code: $code" }
        val text = detail?.trim().orEmpty()
        return if (text.isEmpty()) code else code + SEPARATOR + text
    }

    /**
     * Splits an [encode]d message into its code and detail; null when the message does not start with
     * a known code, in which case the caller reports [INTERNAL] with the whole message.
     */
    fun decode(message: String?): Pair<String, String>? {
        val text = message?.trim() ?: return null
        val code = text.substringBefore(SEPARATOR)
        if (!isKnown(code)) return null
        val detail = if (code.length == text.length) "" else text.substring(code.length + SEPARATOR.length)
        return code to detail
    }
}
