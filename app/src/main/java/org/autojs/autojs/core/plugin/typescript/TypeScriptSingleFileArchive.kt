package org.autojs.autojs.core.plugin.typescript

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.zip.CRC32
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptPluginIds
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

internal data class TypeScriptRhinoCompilerAuthority(
    val compilerVersion: String,
    val compilerDistributionFingerprint: String,
    val declarationsFingerprint: String,
    val declarationProfileFingerprint: String,
    val runtimeCapabilitiesFingerprint: String,
    val profileSchemaRevision: Int,
    val profileRevision: Int,
    val configSchemaRevision: Int,
    val configKind: String,
    val configPolicyRevision: Int,
    val configPolicyFingerprint: String,
) {
    init {
        require(compilerVersion.isNotBlank() && compilerVersion.length <= MAX_VERSION_CHARS)
        requireLowerSha256(compilerDistributionFingerprint, "compiler distribution fingerprint")
        requireLowerSha256(declarationsFingerprint, "declarations fingerprint")
        requireLowerSha256(declarationProfileFingerprint, "declaration profile fingerprint")
        requireLowerSha256(runtimeCapabilitiesFingerprint, "runtime capabilities fingerprint")
        require(profileSchemaRevision == TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION)
        require(profileRevision > 0)
        require(configSchemaRevision == TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION)
        require(configKind == TypeScriptR2Contract.CONFIG_KIND_TSCONFIG)
        require(configPolicyRevision == TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION)
        requireLowerSha256(configPolicyFingerprint, "config policy fingerprint")
    }

    private companion object {
        const val MAX_VERSION_CHARS = 128
    }
}

internal data class TypeScriptPreparedCompilation(
    val requestId: String,
    val projectId: String,
    val entrySourceUri: String,
    val entryPath: String,
    val archiveFile: File,
    val inputArchiveSha256: String,
    val requestManifestSha256: String,
    val configByteLength: Long,
    val configSha256: String,
    val authority: TypeScriptRhinoCompilerAuthority,
)

internal object TypeScriptSingleFileArchive {

    private val emptyTsConfig = "{}".toByteArray(StandardCharsets.UTF_8)

    fun write(
        sourceName: String,
        sourceBytes: ByteArray,
        requestId: String,
        projectId: String,
        entrySourceUri: String,
        authority: TypeScriptRhinoCompilerAuthority,
        destination: File,
    ): TypeScriptPreparedCompilation {
        require(requestId.isNotBlank())
        require(requestId.length <= TypeScriptCompilerContract.MAX_REQUEST_ID_CHARS)
        require(projectId.isNotBlank())
        require(projectId.length <= TypeScriptCompilerContract.MAX_PROJECT_ID_CHARS)
        require(entrySourceUri.startsWith("autojs://source/"))
        require(entrySourceUri.length <= TypeScriptCompilerContract.MAX_SOURCE_URI_CHARS)
        TypeScriptUtf8.decode(sourceBytes, "TypeScript source")

        val entryPath = "sources/${canonicalSourceName(sourceName)}"
        val sourceSha256 = sha256(sourceBytes)
        val configSha256 = sha256(emptyTsConfig)
        val callerBytes = Math.addExact(sourceBytes.size.toLong(), emptyTsConfig.size.toLong())
        require(callerBytes <= TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES) {
            "Single-file TypeScript input exceeds the ordinary compilation byte limit."
        }
        require(
            sourceBytes.size.toLong() <=
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
        ) {
            "Single-file TypeScript source exceeds the ordinary per-file byte limit."
        }

        val inventoryFingerprint = callerInventoryFingerprint(
            sourcePath = entryPath,
            sourceBytes = sourceBytes,
            sourceSha256 = sourceSha256,
            configSha256 = configSha256,
        )
        val manifest = manifestBytes(
            requestId = requestId,
            projectId = projectId,
            entrySourceUri = entrySourceUri,
            entryPath = entryPath,
            sourceBytes = sourceBytes,
            sourceSha256 = sourceSha256,
            configSha256 = configSha256,
            callerInventoryFingerprint = inventoryFingerprint,
            authority = authority,
        )
        require(manifest.size.toLong() <= TypeScriptCompilerContract.MAX_MANIFEST_BYTES) {
            "TypeScript request manifest exceeds its byte limit."
        }

        StoredZipV1Writer.write(
            destination,
            linkedMapOf(
                TypeScriptR2Contract.INPUT_ARCHIVE_MANIFEST_PATH to manifest,
                CONTROLLED_CONFIG_PATH to emptyTsConfig,
                entryPath to sourceBytes,
            ),
        )
        require(destination.length() <= TypeScriptCompilerContract.MAX_INPUT_ARCHIVE_BYTES) {
            "TypeScript input archive exceeds its transport byte limit."
        }
        return TypeScriptPreparedCompilation(
            requestId = requestId,
            projectId = projectId,
            entrySourceUri = entrySourceUri,
            entryPath = entryPath,
            archiveFile = destination,
            inputArchiveSha256 = sha256(destination),
            requestManifestSha256 = sha256(manifest),
            configByteLength = emptyTsConfig.size.toLong(),
            configSha256 = configSha256,
            authority = authority,
        )
    }

    private fun manifestBytes(
        requestId: String,
        projectId: String,
        entrySourceUri: String,
        entryPath: String,
        sourceBytes: ByteArray,
        sourceSha256: String,
        configSha256: String,
        callerInventoryFingerprint: String,
        authority: TypeScriptRhinoCompilerAuthority,
    ): ByteArray {
        val json = buildString(2_048) {
            append('{')
            appendJsonField("schema", TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA)
            append(',')
            appendJsonField("revision", TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION)
            append(',')
            appendJsonField(
                "inputQuotaRevision",
                TypeScriptR2Contract.ORDINARY_COMPILATION_INPUT_QUOTA_REVISION,
            )
            append(',')
            appendJsonField("contractVersion", TypeScriptCompilerContract.VERSION)
            append(',')
            appendJsonField("contractHash", TypeScriptCompilerContract.HASH)
            append(',')
            appendJsonField(
                "inputArchiveTransportVersion",
                TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION,
            )
            append(',')
            appendJsonField("requestId", requestId)
            append(',')
            appendJsonField("projectId", projectId)
            append(',')
            appendJsonField("entrySourceUri", entrySourceUri)
            append(',')
            appendJsonField("entryPath", entryPath)
            append(',')
            appendJsonField("targetProfile", TypeScriptCompilerContract.PROFILE_RHINO)
            append(',')
            appendJsonField("compilationMode", TypeScriptCompilerContract.MODE_DEVELOPMENT)
            append(',')
            appendJsonField("profileSchemaRevision", authority.profileSchemaRevision)
            append(',')
            appendJsonField("profileRevision", authority.profileRevision)
            append(',')
            appendJsonField(
                "runtimeCapabilitySchemaRevision",
                TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION,
            )
            append(',')
            appendJsonField("configSchemaRevision", authority.configSchemaRevision)
            append(',')
            appendJsonField(
                "runtimeCapabilitiesFingerprint",
                authority.runtimeCapabilitiesFingerprint,
            )
            append(',')
            appendJsonField("compilerPackage", TypeScriptPluginIds.ID)
            append(',')
            appendJsonField("compilerVersion", authority.compilerVersion)
            append(',')
            appendJsonField(
                "compilerDistributionFingerprint",
                authority.compilerDistributionFingerprint,
            )
            append(',')
            appendJsonField("declarationsFingerprint", authority.declarationsFingerprint)
            append(',')
            appendJsonField(
                "declarationProfileFingerprint",
                authority.declarationProfileFingerprint,
            )
            append(',')
            appendJsonField("configKind", authority.configKind)
            append(',')
            appendJsonString("config")
            append(':')
            append('{')
            appendJsonField("path", CONTROLLED_CONFIG_PATH)
            append(',')
            appendJsonField("size", emptyTsConfig.size.toLong())
            append(',')
            appendJsonField("sha256", configSha256)
            append(',')
            appendJsonField("policyRevision", authority.configPolicyRevision)
            append(',')
            appendJsonField("policyFingerprint", authority.configPolicyFingerprint)
            append('}')
            append(',')
            appendJsonField("callerInventoryFingerprint", callerInventoryFingerprint)
            append(',')
            appendJsonString("rootPaths")
            append(':')
            append('[')
            appendJsonString(entryPath)
            append(']')
            append(',')
            appendJsonString("files")
            append(':')
            append('[')
            append('{')
            appendJsonField("path", entryPath)
            append(',')
            appendJsonField("sha256", sourceSha256)
            append(',')
            appendJsonField("size", sourceBytes.size.toLong())
            append('}')
            append(']')
            append('}')
        }
        return TypeScriptUtf8.encode(json, "TypeScript request manifest")
    }

    private fun callerInventoryFingerprint(
        sourcePath: String,
        sourceBytes: ByteArray,
        sourceSha256: String,
        configSha256: String,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(INPUT_INVENTORY_PREFIX.toByteArray(StandardCharsets.US_ASCII))
        digest.updateInventoryRecord(
            domain = "source",
            path = "/$sourcePath",
            size = sourceBytes.size.toLong(),
            sha256 = sourceSha256,
        )
        digest.updateInventoryRecord(
            domain = "config",
            path = CONTROLLED_CONFIG_PATH,
            size = emptyTsConfig.size.toLong(),
            sha256 = configSha256,
        )
        return digest.digest().toLowerHex()
    }

    private fun MessageDigest.updateInventoryRecord(
        domain: String,
        path: String,
        size: Long,
        sha256: String,
    ) {
        update(domain.toByteArray(StandardCharsets.US_ASCII))
        update(0)
        update(path.toByteArray(StandardCharsets.UTF_8))
        update(0)
        update(size.toString().toByteArray(StandardCharsets.US_ASCII))
        update(0)
        update(sha256.toByteArray(StandardCharsets.US_ASCII))
        update('\n'.code.toByte())
    }

    private fun canonicalSourceName(sourceName: String): String {
        val candidate = sourceName.takeIf { name ->
            name.isNotBlank() &&
                name.endsWith(".ts") &&
                name != "." &&
                name != ".." &&
                name.none { character ->
                    character == '/' ||
                        character == '\\' ||
                        character.code in 0x00..0x1f ||
                        character.code in 0x7f..0x9f
                } &&
                runCatching {
                    TypeScriptUtf8.encode("sources/$name", "TypeScript archive path").size <=
                        TypeScriptCompilerContract.MAX_ARCHIVE_ENTRY_NAME_BYTES
                }.getOrDefault(false)
        }
        return candidate ?: DEFAULT_SOURCE_NAME
    }

    private fun StringBuilder.appendJsonField(name: String, value: String) {
        appendJsonString(name)
        append(':')
        appendJsonString(value)
    }

    private fun StringBuilder.appendJsonField(name: String, value: Int) {
        appendJsonString(name)
        append(':')
        append(value)
    }

    private fun StringBuilder.appendJsonField(name: String, value: Long) {
        appendJsonString(name)
        append(':')
        append(value)
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (character.code < 0x20) {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
        }
        append('"')
    }

    private const val CONTROLLED_CONFIG_PATH = "tsconfig.json"
    private const val DEFAULT_SOURCE_NAME = "main.ts"
    private const val INPUT_INVENTORY_PREFIX = "autojs6.typescript.input-inventory.v1\n"
}

internal object StoredZipV1Writer {

    fun write(destination: File, entries: LinkedHashMap<String, ByteArray>) {
        require(entries.size <= 0xffff)
        FileOutputStream(destination, false).use { output ->
            val records = ArrayList<Record>(entries.size)
            var position = 0L
            entries.forEach { (path, bytes) ->
                val name = TypeScriptUtf8.encode(path, "ZIP entry name")
                require(name.isNotEmpty())
                require(name.size <= TypeScriptCompilerContract.MAX_ARCHIVE_ENTRY_NAME_BYTES)
                val flags = if (name.any { byte -> byte.toInt() and 0x80 != 0 }) FLAG_UTF8 else 0
                val crc = CRC32().apply { update(bytes) }.value
                records += Record(name, bytes, flags, crc, position)
                output.putU32(LOCAL_FILE_HEADER_SIGNATURE)
                output.putU16(VERSION_STORED)
                output.putU16(flags)
                output.putU16(METHOD_STORED)
                output.putU16(0)
                output.putU16(0)
                output.putU32(crc)
                output.putU32(bytes.size.toLong())
                output.putU32(bytes.size.toLong())
                output.putU16(name.size)
                output.putU16(0)
                output.write(name)
                output.write(bytes)
                position += LOCAL_HEADER_BYTES + name.size + bytes.size
            }

            val centralOffset = position
            records.forEach { record ->
                output.putU32(CENTRAL_DIRECTORY_SIGNATURE)
                output.putU16(VERSION_STORED)
                output.putU16(VERSION_STORED)
                output.putU16(record.flags)
                output.putU16(METHOD_STORED)
                output.putU16(0)
                output.putU16(0)
                output.putU32(record.crc)
                output.putU32(record.bytes.size.toLong())
                output.putU32(record.bytes.size.toLong())
                output.putU16(record.name.size)
                output.putU16(0)
                output.putU16(0)
                output.putU16(0)
                output.putU16(0)
                output.putU32(0)
                output.putU32(record.localOffset)
                output.write(record.name)
                position += CENTRAL_HEADER_BYTES + record.name.size
            }

            output.putU32(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
            output.putU16(0)
            output.putU16(0)
            output.putU16(records.size)
            output.putU16(records.size)
            output.putU32(position - centralOffset)
            output.putU32(centralOffset)
            output.putU16(0)
            output.fd.sync()
        }
    }

    private fun FileOutputStream.putU16(value: Int) {
        write(value and 0xff)
        write(value ushr 8 and 0xff)
    }

    private fun FileOutputStream.putU32(value: Long) {
        write((value and 0xff).toInt())
        write((value ushr 8 and 0xff).toInt())
        write((value ushr 16 and 0xff).toInt())
        write((value ushr 24 and 0xff).toInt())
    }

    private data class Record(
        val name: ByteArray,
        val bytes: ByteArray,
        val flags: Int,
        val crc: Long,
        val localOffset: Long,
    )

    private const val VERSION_STORED = 10
    private const val METHOD_STORED = 0
    private const val FLAG_UTF8 = 1 shl 11
    private const val LOCAL_HEADER_BYTES = 30L
    private const val CENTRAL_HEADER_BYTES = 46L
    private const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50L
    private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50L
    private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50L
}

internal fun requireLowerSha256(value: String, label: String) {
    require(value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }) {
        "$label is not a lowercase SHA-256 value."
    }
}

internal fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).toLowerHex()

internal fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().toLowerHex()
}

internal fun ByteArray.toLowerHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
