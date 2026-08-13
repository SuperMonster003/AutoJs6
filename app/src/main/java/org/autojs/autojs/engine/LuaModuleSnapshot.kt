package org.autojs.autojs.engine

import org.autojs.autojs.script.LuaFileSource
import org.autojs.autojs.script.LuaScriptSource
import org.autojs.plugin.lua.runtime.api.LuaContractException
import org.autojs.plugin.lua.runtime.api.LuaContractViolation
import org.autojs.plugin.lua.runtime.api.LuaSha256
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Session-private, immutable snapshot of flat Lua modules in `<entry>.modules/`.
 * Paths never leave the host process; the provider can only request an exact module name.
 */
internal class LuaModuleSnapshot private constructor(
    modules: Map<String, ByteArray>,
) {
    internal class FrozenModule private constructor(
        source: ByteArray,
        val sha256: LuaSha256,
    ) {
        private val source = source.copyOf()

        fun sourceBytes(): ByteArray = source.copyOf()

        companion object {
            fun create(source: ByteArray) = FrozenModule(source, LuaSha256.digest(source))
        }
    }

    private val modules: Map<String, FrozenModule> = Collections.unmodifiableMap(
        LinkedHashMap<String, FrozenModule>().apply {
            modules.forEach { (name, source) -> put(name, FrozenModule.create(source)) }
        },
    )

    val moduleCount: Int
        get() = modules.size

    val totalBytes: Int
        get() = modules.values.sumOf { it.sourceBytes().size }

    fun sourceFor(name: String): ByteArray? = moduleFor(name)?.sourceBytes()

    fun moduleFor(name: String): FrozenModule? = modules[name]

    companion object {
        const val MAX_MODULES = 16
        const val MAX_DIRECTORY_ENTRIES = 64
        const val MAX_MODULE_BYTES = 64 * 1024
        const val MAX_TOTAL_BYTES = 256 * 1024

        private val MODULE_FILE_PATTERN = Regex("([A-Za-z_][A-Za-z0-9_]{0,63})\\.lua")
        private val MODULE_NAME_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")

        val EMPTY = LuaModuleSnapshot(emptyMap())

        fun isValidModuleName(name: String): Boolean =
            name != RESERVED_MODULE_NAME && MODULE_NAME_PATTERN.matches(name)

        fun create(
            source: LuaScriptSource,
            fileReader: LuaModuleFileReader = LuaModuleFileReaders.runtime(),
        ): LuaModuleSnapshot {
            val entry = trustedEntry(source) ?: return EMPTY
            val root = entry.parentFile?.resolve("${entry.nameWithoutExtension}.modules") ?: return EMPTY
            if (!root.exists()) return EMPTY
            rejectLinkOrEscape(root, "Lua module root")
            val canonicalRoot = root.canonicalFile
            val canonicalEntryParent = entry.parentFile?.canonicalFile ?: return EMPTY
            if (!canonicalRoot.isDirectory || !samePath(canonicalRoot.parentFile, canonicalEntryParent)) {
                invalid("Lua module root escapes its entry directory")
            }

            val candidates = canonicalRoot.listFiles()
                ?: invalid("Cannot enumerate the Lua module root")
            if (candidates.size > MAX_DIRECTORY_ENTRIES) {
                invalid("Lua module root exceeds $MAX_DIRECTORY_ENTRIES directory entries")
            }
            val admitted = candidates.mapNotNull { candidate ->
                val match = MODULE_FILE_PATTERN.matchEntire(candidate.name) ?: return@mapNotNull null
                val name = match.groupValues[1]
                if (name == RESERVED_MODULE_NAME) return@mapNotNull null
                rejectLinkOrEscape(candidate, "Lua module '$name'")
                val canonicalCandidate = candidate.canonicalFile
                if (!samePath(canonicalCandidate.parentFile, canonicalRoot)) {
                    invalid("Lua module '$name' escapes its snapshot root")
                }
                if (!canonicalCandidate.isFile) {
                    return@mapNotNull null
                }
                name to canonicalCandidate
            }.sortedBy { it.first }

            if (admitted.size > MAX_MODULES) {
                invalid("Lua module snapshot exceeds $MAX_MODULES modules")
            }

            var totalBytes = 0
            val frozen = LinkedHashMap<String, ByteArray>(admitted.size)
            admitted.forEach { (name, file) ->
                val bytes = fileReader.read(file, name, MAX_MODULE_BYTES)
                validateUtf8(bytes, name)
                if (totalBytes > MAX_TOTAL_BYTES - bytes.size) {
                    invalid("Lua module snapshot exceeds $MAX_TOTAL_BYTES bytes")
                }
                totalBytes += bytes.size
                frozen[name] = bytes
            }
            return if (frozen.isEmpty()) EMPTY else LuaModuleSnapshot(frozen)
        }

        private fun trustedEntry(source: LuaScriptSource): File? {
            val originPath = source.originPath
            val candidate = when {
                originPath != null -> File(originPath)
                source is LuaFileSource -> source.file
                else -> return null
            }
            if (!candidate.isAbsolute || !LuaScriptSource.isLuaFileName(candidate.name)) return null
            return candidate.absoluteFile.normalize()
        }

        private fun validateUtf8(bytes: ByteArray, name: String) {
            try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
            } catch (error: Exception) {
                throw LuaContractException(
                    LuaContractViolation.INVALID_VALUE,
                    "Lua module '$name' is not valid UTF-8 text",
                    error,
                )
            }
        }

        private fun rejectLinkOrEscape(file: File, label: String) {
            val canonicalParent = file.parentFile?.canonicalFile
                ?: invalid("$label has no parent directory")
            val expectedFinalComponent = canonicalParent.resolve(file.name).absoluteFile.normalize()
            if (!samePath(expectedFinalComponent, file.canonicalFile)) {
                invalid("$label must not be a symbolic link or escape its root")
            }
        }

        private fun samePath(first: File?, second: File?): Boolean =
            first != null && second != null && first.path.equals(
                second.path,
                ignoreCase = File.separatorChar == '\\',
            )

        private fun invalid(message: String): Nothing =
            throw LuaContractException(LuaContractViolation.INVALID_VALUE, message)

        private const val RESERVED_MODULE_NAME = "autojs"
    }
}
