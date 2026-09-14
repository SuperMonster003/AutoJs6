package org.autojs.plugin.nodejs.api

object NodeJsPluginCapabilityKeys {
    const val NODE_VERSION = "nodeVersion"
    const val RUNTIME_SLOT = "runtimeSlot"
    const val NATIVE_LIBRARY_NAME = "nativeLibraryName"
    const val RUNTIME_CONTRACT_VERSION = "runtimeContractVersion"
    const val RUNTIME_SERVICE_ACTION = "runtimeServiceAction"

    /**
     * Terminal launcher contract declared as `<meta-data>` on the RUNTIME service (schema 1).
     * The host reads it from `ServiceInfo.metaData`, so the values never require a Binder call.
     * zh-CN: 终端启动器契约, 以 RUNTIME 服务的 `<meta-data>` 声明 (schema 1); 宿主直接从
     * `ServiceInfo.metaData` 读取, 无需 Binder 调用.
     */
    const val NODE_CLI_META_DATA_PREFIX = "org.autojs.plugin.nodejs."
    const val NODE_CLI_SCHEMA = NODE_CLI_META_DATA_PREFIX + "NODE_CLI_SCHEMA"
    const val NODE_CLI_EXECUTABLE = NODE_CLI_META_DATA_PREFIX + "NODE_CLI_EXECUTABLE"
    const val NODE_CLI_COMMANDS = NODE_CLI_META_DATA_PREFIX + "NODE_CLI_COMMANDS"
    const val NODE_CLI_ARCHIVE = NODE_CLI_META_DATA_PREFIX + "NODE_CLI_ARCHIVE"
    const val NODE_CLI_ARCHIVE_SHA256 = NODE_CLI_META_DATA_PREFIX + "NODE_CLI_ARCHIVE_SHA256"
    const val NODE_CLI_ARCHIVE_ROOT = NODE_CLI_META_DATA_PREFIX + "NODE_CLI_ARCHIVE_ROOT"
    const val NODE_CLI_ARCHIVE_ENTRY_COUNT = NODE_CLI_META_DATA_PREFIX + "NODE_CLI_ARCHIVE_ENTRY_COUNT"
    const val NODE_CLI_ARCHIVE_BYTES = NODE_CLI_META_DATA_PREFIX + "NODE_CLI_ARCHIVE_BYTES"
    const val NODE_CLI_NPM_VERSION = NODE_CLI_META_DATA_PREFIX + "NODE_CLI_NPM_VERSION"
    const val NODE_CLI_COREPACK_VERSION = NODE_CLI_META_DATA_PREFIX + "NODE_CLI_COREPACK_VERSION"
}
