package org.autojs.plugin.tool.api

/**
 * Bundle keys of the provider transactions (docs/dev/plugin-tool-protocol-v1.md). JSON travels as UTF-8 strings inside
 * the Bundles, so later versions can append keys without touching the AIDL.
 *
 * zh-CN: 提供者各事务的 Bundle 键. JSON 以 UTF-8 字符串放在 Bundle 中, 以便后续版本追加键而不改 AIDL.
 */
object PluginToolCatalogRequestKeys {

    /** Int: the host's contract version. */
    const val CONTRACT_VERSION = "contractVersion"

    /** Long: the host's versionCode, for catalogs that depend on host features. */
    const val HOST_VERSION_CODE = "hostVersionCode"
}

/** Answer of `getCatalog`: [CATALOG_JSON] on success, [ERROR_CODE] / [ERROR_MESSAGE] otherwise. */
object PluginToolCatalogKeys {

    /** Int: the contract version the catalog follows. */
    const val CONTRACT_VERSION = "contractVersion"

    /** String: the catalog document (PluginToolCatalogJson), at most PluginToolLimits.MAX_CATALOG_BYTES of UTF-8. */
    const val CATALOG_JSON = "catalogJson"

    /** String: one of PluginToolErrorCodes when no catalog could be produced. */
    const val ERROR_CODE = "errorCode"

    const val ERROR_MESSAGE = "errorMessage"
}

/** Request of `invoke`. */
object PluginToolInvokeKeys {

    /** Int: the host's contract version. */
    const val CONTRACT_VERSION = "contractVersion"

    /** String: unique per call; `cancel` and every callback refer to it. */
    const val INVOCATION_ID = "invocationId"

    /** String: the tool name as the catalog declares it, without the short name. */
    const val TOOL = "tool"

    /** String: JSON object of the arguments the host validated, without the image and file arguments. */
    const val ARGUMENTS_JSON = "argumentsJson"

    /**
     * String: JSON array describing the input descriptors, entry i for descriptor i: `{name, source, mimeType?,
     * displayName?, byteCount?, width?, height?}` (PluginToolInputJson). Empty array when the tool takes none.
     */
    const val INPUTS_JSON = "inputsJson"

    /** String: JSON array of the declared output names the caller wants written; the plugin may skip the others. */
    const val OUTPUTS_JSON = "outputsJson"

    /** Long: milliseconds the host waits for the callback before it cancels the call. */
    const val TIMEOUT_MS = "timeoutMs"

    /** String: who asked (PluginToolValues.CONSUMER_*), for logs and statistics only. */
    const val CONSUMER = "consumer"

    /**
     * Bundle, present when the caller wants directory outputs (MCP roadmap D53): output name to the IBinder of an
     * IPluginToolOutputTree. The names also appear in [OUTPUTS_JSON]; hosts before directory outputs never send it.
     */
    const val OUTPUT_TREES = "outputTrees"
}

/** Answers of IPluginToolOutputTree: the result of `createDirectory`, the `answer` of `openFile`. */
object PluginToolOutputTreeKeys {

    /** Boolean: true when the directory exists or the file is open. */
    const val OK = "ok"

    /** String: one of PluginToolOutputTreeErrors when OK is false. */
    const val ERROR_CODE = "errorCode"

    /** String: what went wrong, for logs and for the plugin's own result. */
    const val ERROR_MESSAGE = "errorMessage"
}

/** Immediate answer of `invoke`. */
object PluginToolAcceptKeys {

    /** Boolean: true when the call runs and will report to the callback exactly once. */
    const val ACCEPTED = "accepted"

    /** String: one of PluginToolErrorCodes when the call was refused. */
    const val ERROR_CODE = "errorCode"

    const val ERROR_MESSAGE = "errorMessage"
}

/** `IPluginToolCallback.onResult`. */
object PluginToolResultKeys {

    const val INVOCATION_ID = "invocationId"

    /** String: the result as JSON (an object when the tool declares an output schema), at most PluginToolLimits.MAX_RESULT_BYTES. */
    const val RESULT_JSON = "resultJson"

    /** String: optional short plain-text summary that consumers may show instead of the JSON. */
    const val TEXT = "text"

    /** String: JSON array describing the output descriptors, entry i for descriptor i: `{name, mimeType}`. */
    const val OUTPUTS_JSON = "outputsJson"
}

/** `IPluginToolCallback.onError` and refused catalogs or calls. */
object PluginToolErrorKeys {

    const val INVOCATION_ID = "invocationId"

    /** String: one of PluginToolErrorCodes. */
    const val CODE = "code"

    /** String: English, for people and models; at most PluginToolLimits.MAX_ERROR_MESSAGE_CHARS. */
    const val MESSAGE = "message"
}

/** `IPluginToolCallback.onProgress`, reserved: the host bridge of contract version 1 drops it. */
object PluginToolProgressKeys {

    const val INVOCATION_ID = "invocationId"

    /** Double in 0..1, or absent when unknown. */
    const val FRACTION = "fraction"

    const val MESSAGE = "message"
}

/** Request of `cancel`. */
object PluginToolCancelKeys {

    const val INVOCATION_ID = "invocationId"

    /** String: PluginToolValues.CANCEL_REASON_*. */
    const val REASON = "reason"
}
