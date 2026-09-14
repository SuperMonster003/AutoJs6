package org.autojs.autojs.core.terminal

import org.autojs.plugin.nodejs.api.NodeJsPluginCapabilityKeys
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import java.io.File

/**
 * Parses the terminal-launcher contract a Node.js Runtime plugin declares as manifest
 * `<meta-data>` on its runtime service. The key names come from the mirrored plugin contract
 * ([NodeJsPluginCapabilityKeys]); they are read from `ServiceInfo.metaData`, so no Binder call
 * is needed to decide availability.
 *
 * zh-CN: 解析 Node.js 运行时插件在其运行时服务上以 manifest `<meta-data>` 声明的终端启动器契约.
 * 键名来自镜像的插件契约 ([NodeJsPluginCapabilityKeys]); 直接从 `ServiceInfo.metaData` 读取, 判定可用性无需 Binder 调用.
 */
object NodeCliMetadata {

    const val KEY_SCHEMA = NodeJsPluginCapabilityKeys.NODE_CLI_SCHEMA
    const val KEY_EXECUTABLE = NodeJsPluginCapabilityKeys.NODE_CLI_EXECUTABLE
    const val KEY_COMMANDS = NodeJsPluginCapabilityKeys.NODE_CLI_COMMANDS
    const val KEY_ARCHIVE = NodeJsPluginCapabilityKeys.NODE_CLI_ARCHIVE
    const val KEY_ARCHIVE_SHA256 = NodeJsPluginCapabilityKeys.NODE_CLI_ARCHIVE_SHA256
    const val KEY_ARCHIVE_ROOT = NodeJsPluginCapabilityKeys.NODE_CLI_ARCHIVE_ROOT
    const val KEY_ARCHIVE_ENTRY_COUNT = NodeJsPluginCapabilityKeys.NODE_CLI_ARCHIVE_ENTRY_COUNT
    const val KEY_ARCHIVE_BYTES = NodeJsPluginCapabilityKeys.NODE_CLI_ARCHIVE_BYTES
    const val KEY_NPM_VERSION = NodeJsPluginCapabilityKeys.NODE_CLI_NPM_VERSION
    const val KEY_COREPACK_VERSION = NodeJsPluginCapabilityKeys.NODE_CLI_COREPACK_VERSION

    const val SUPPORTED_SCHEMA = NodeJsRuntimeContract.NODE_CLI_SCHEMA_VERSION
    const val DEFAULT_ARCHIVE_ROOT = "lib/node_modules"

    private val SHA256_HEX = Regex("[0-9a-fA-F]{64}")
    private val LIBRARY_NAME = Regex("lib[A-Za-z0-9_.-]+\\.so")
    private val COMMAND_NAME = Regex("[A-Za-z0-9_-]+")
    private val ELF_MAGIC = byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())

    /**
     * @param executableName    file name of the multi-call launcher inside the plugin's native library dir
     * @param commands          names the launcher dispatches on (`argv[0]`), e.g. `node`, `npm`
     * @param archivePath       asset path of the npm / corepack archive inside the plugin APK
     * @param archiveRoot       directory inside the archive that holds `npm/` and `corepack/`
     * @param archiveEntryCount declared entry count, used as the extraction limit when present
     * @param archiveBytes      declared uncompressed size, used as the extraction limit when present
     */
    data class Descriptor(
        val executableName: String,
        val commands: List<String>,
        val archivePath: String,
        val archiveSha256: String,
        val archiveRoot: String,
        val archiveEntryCount: Int?,
        val archiveBytes: Long?,
        val npmVersion: String?,
        val corepackVersion: String?,
    )

    sealed class ParseResult {
        data class Valid(val descriptor: Descriptor) : ParseResult()

        /** The plugin predates the contract or declares it incorrectly. zh-CN: 插件早于契约或声明有误. */
        data class Missing(val reason: String) : ParseResult()
    }

    /**
     * Parses a meta-data map. Values are coerced through [Any.toString] because aapt stores
     * numeric-looking `android:value` attributes as integers.
     * zh-CN: 解析 meta-data. 值经 toString 归一化, 因为 aapt 会把形似数字的 `android:value` 存为整数.
     */
    @JvmStatic
    fun parse(meta: Map<String, Any?>?): ParseResult {
        if (meta == null) return ParseResult.Missing("no meta-data")
        val schema = meta.string(KEY_SCHEMA) ?: return ParseResult.Missing("schema not declared")
        if (schema != SUPPORTED_SCHEMA) return ParseResult.Missing("unsupported schema $schema")
        val executable = meta.string(KEY_EXECUTABLE) ?: return ParseResult.Missing("executable not declared")
        if (!LIBRARY_NAME.matches(executable)) return ParseResult.Missing("invalid executable name $executable")
        val commands = meta.string(KEY_COMMANDS)
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()
        if (commands.isEmpty() || commands.any { !COMMAND_NAME.matches(it) }) {
            return ParseResult.Missing("invalid command list")
        }
        val archive = meta.string(KEY_ARCHIVE) ?: return ParseResult.Missing("archive not declared")
        if (archive.startsWith("/") || archive.split('/').any { it == ".." || it.isEmpty() }) {
            return ParseResult.Missing("invalid archive path $archive")
        }
        val sha256 = meta.string(KEY_ARCHIVE_SHA256) ?: return ParseResult.Missing("archive digest not declared")
        if (!SHA256_HEX.matches(sha256)) return ParseResult.Missing("invalid archive digest")
        val archiveRoot = (meta.string(KEY_ARCHIVE_ROOT) ?: DEFAULT_ARCHIVE_ROOT).trimEnd('/')
        if (archiveRoot.isEmpty() || archiveRoot.startsWith("/") || archiveRoot.split('/').any { it == ".." || it.isEmpty() }) {
            return ParseResult.Missing("invalid archive root $archiveRoot")
        }
        val entryCount = meta.string(KEY_ARCHIVE_ENTRY_COUNT)?.toIntOrNull()?.takeIf { it > 0 }
        val bytes = meta.string(KEY_ARCHIVE_BYTES)?.toLongOrNull()?.takeIf { it > 0 }
        return ParseResult.Valid(
            Descriptor(
                executableName = executable,
                commands = commands,
                archivePath = archive,
                archiveSha256 = sha256.lowercase(),
                archiveRoot = archiveRoot,
                archiveEntryCount = entryCount,
                archiveBytes = bytes,
                npmVersion = meta.string(KEY_NPM_VERSION),
                corepackVersion = meta.string(KEY_COREPACK_VERSION),
            ),
        )
    }

    /**
     * True when [file] exists and starts with the ELF magic, i.e. is a real launcher rather than
     * a script that the platform would refuse to execute from an app's library directory.
     * zh-CN: 文件存在且以 ELF 魔数开头时为 true.
     */
    @JvmStatic
    fun isElf(file: File): Boolean {
        if (!file.isFile || file.length() < ELF_MAGIC.size) return false
        return runCatching {
            file.inputStream().use { input ->
                val head = ByteArray(ELF_MAGIC.size)
                var read = 0
                while (read < head.size) {
                    val n = input.read(head, read, head.size - read)
                    if (n < 0) break
                    read += n
                }
                read == head.size && head.contentEquals(ELF_MAGIC)
            }
        }.getOrDefault(false)
    }

    private fun Map<String, Any?>.string(key: String): String? = this[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() }

}
