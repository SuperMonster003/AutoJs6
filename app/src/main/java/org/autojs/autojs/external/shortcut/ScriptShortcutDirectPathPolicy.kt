package org.autojs.autojs.external.shortcut

/** Android-free admission policy for the exported shortcut activity. */
internal object ScriptShortcutDirectPathPolicy {

    fun requireDirectPath(
        hasData: Boolean,
        hasJsonExtra: Boolean,
        hasPreExecuteExtra: Boolean,
        path: String?,
    ): String {
        if (hasData) {
            throw ScriptShortcutDirectPathException(ScriptShortcutDirectPathError.URI_UNSUPPORTED)
        }
        if (hasJsonExtra || hasPreExecuteExtra) {
            throw ScriptShortcutDirectPathException(ScriptShortcutDirectPathError.PAYLOAD_CONFLICT)
        }
        if (path.isNullOrBlank()) {
            throw ScriptShortcutDirectPathException(ScriptShortcutDirectPathError.PATH_REQUIRED)
        }
        return path
    }
}

internal enum class ScriptShortcutDirectPathError(
    val stableCode: String,
    val stableMessage: String,
) {
    URI_UNSUPPORTED(
        "SCRIPT_SHORTCUT_URI_UNSUPPORTED",
        "Script shortcuts only accept a direct path extra",
    ),
    PAYLOAD_CONFLICT(
        "SCRIPT_SHORTCUT_PAYLOAD_CONFLICT",
        "Script shortcuts do not accept JSON, inline-script, or pre-execute payloads",
    ),
    PATH_REQUIRED(
        "SCRIPT_SHORTCUT_PATH_REQUIRED",
        "Script shortcuts require a non-blank direct path extra",
    ),
    MALFORMED_PAYLOAD(
        "SCRIPT_SHORTCUT_PAYLOAD_MALFORMED",
        "Script shortcut extras could not be decoded",
    ),
}

internal class ScriptShortcutDirectPathException(
    val error: ScriptShortcutDirectPathError,
    cause: Throwable? = null,
) : IllegalArgumentException("${error.stableCode}: ${error.stableMessage}", cause)
