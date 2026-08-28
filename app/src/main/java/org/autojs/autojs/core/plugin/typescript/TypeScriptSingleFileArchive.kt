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

internal data class TypeScriptCompilerAuthority(
    val targetProfile: String,
    val compilerVersion: String,
    val compilerDistributionFingerprint: String,
    val declarationsFingerprint: String,
    val declarationProfileFingerprint: String,
    val runtimeCapabilitiesFingerprint: String,
    val profileSchemaRevision: Int,
    val profileRevision: Int,
    val configSchemaRevision: Int,
    val configKind: String,
    val configPolicyRevision: Int? = null,
    val configPolicyFingerprint: String? = null,
    val packageMetadataSchemaRevision: Int? = null,
    val packageMetadataPolicyRevision: Int? = null,
    val packageMetadataPolicyFingerprint: String? = null,
    val dependencyLayerSchemaRevision: Int? = null,
    val dependencyLayerPolicyRevision: Int? = null,
    val dependencyLayerPolicyFingerprint: String? = null,
    val dependencyResolverPolicyRevision: Int? = null,
    val dependencyResolverPolicyFingerprint: String? = null,
) {
    init {
        require(targetProfile in TypeScriptCompilerContract.TARGET_PROFILES)
        require(compilerVersion.isNotBlank() && compilerVersion.length <= MAX_VERSION_CHARS)
        requireLowerSha256(compilerDistributionFingerprint, "compiler distribution fingerprint")
        requireLowerSha256(declarationsFingerprint, "declarations fingerprint")
        requireLowerSha256(declarationProfileFingerprint, "declaration profile fingerprint")
        requireLowerSha256(runtimeCapabilitiesFingerprint, "runtime capabilities fingerprint")
        require(profileSchemaRevision == TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION)
        require(profileRevision > 0)
        when (configKind) {
            TypeScriptR2Contract.CONFIG_KIND_NONE -> {
                require(configSchemaRevision == CONFIG_NONE_SCHEMA_REVISION)
                require(configPolicyRevision == null && configPolicyFingerprint == null)
            }
            TypeScriptR2Contract.CONFIG_KIND_TSCONFIG -> {
                require(
                    configSchemaRevision ==
                        TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
                )
                require(
                    configPolicyRevision ==
                        TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION,
                )
                requireLowerSha256(
                    requireNotNull(configPolicyFingerprint),
                    "config policy fingerprint",
                )
            }
            else -> error("Unsupported TypeScript config kind: $configKind")
        }
        require(
            targetProfile != TypeScriptCompilerContract.PROFILE_NODE ||
                configKind == TypeScriptR2Contract.CONFIG_KIND_NONE,
        )
        if (targetProfile == TypeScriptCompilerContract.PROFILE_NODE) {
            require(requireNotNull(packageMetadataSchemaRevision) > 0)
            require(requireNotNull(packageMetadataPolicyRevision) > 0)
            requireLowerSha256(
                requireNotNull(packageMetadataPolicyFingerprint),
                "package metadata policy fingerprint",
            )
        } else {
            require(
                packageMetadataSchemaRevision == null &&
                    packageMetadataPolicyRevision == null &&
                    packageMetadataPolicyFingerprint == null,
            )
        }
        val dependencyAuthorityPresence = listOf(
            dependencyLayerSchemaRevision != null,
            dependencyLayerPolicyRevision != null,
            dependencyLayerPolicyFingerprint != null,
        )
        val dependencyResolverAuthorityPresence = listOf(
            dependencyResolverPolicyRevision != null,
            dependencyResolverPolicyFingerprint != null,
        )
        require(
            dependencyAuthorityPresence.all { present -> present } ||
                dependencyAuthorityPresence.none { present -> present },
        ) { "Dependency-layer authority must be either complete or absent." }
        if (dependencyAuthorityPresence.all { present -> present }) {
            require(
                dependencyLayerSchemaRevision ==
                    TypeScriptR2Contract.DEPENDENCY_LAYER_SCHEMA_REVISION,
            )
            require(
                dependencyLayerPolicyRevision ==
                    TypeScriptR2Contract.DEPENDENCY_LAYER_POLICY_REVISION,
            )
            requireLowerSha256(
                requireNotNull(dependencyLayerPolicyFingerprint),
                "dependency layer policy fingerprint",
            )
        }
        require(
            dependencyResolverAuthorityPresence.all { present -> present } ||
                dependencyResolverAuthorityPresence.none { present -> present },
        ) { "Dependency resolver authority must be either complete or absent." }
        require(
            dependencyAuthorityPresence.all { present -> present } ==
                dependencyResolverAuthorityPresence.all { present -> present },
        ) { "Dependency-layer and resolver authorities must be present together." }
        if (dependencyResolverAuthorityPresence.all { present -> present }) {
            require(
                dependencyResolverPolicyRevision ==
                    TypeScriptR2Contract.DEPENDENCY_RESOLVER_POLICY_REVISION,
            )
            require(
                dependencyResolverPolicyFingerprint ==
                    TypeScriptR2Contract.DEPENDENCY_RESOLVER_POLICY_FINGERPRINT,
            )
        }
    }

    companion object {
        const val CONFIG_NONE_SCHEMA_REVISION = 1

        const val MAX_VERSION_CHARS = 128
    }
}

internal data class TypeScriptPreparedSource(
    val archivePath: String,
    val projectRelativePath: String,
    val originalFile: File?,
    val sha256: String,
)

internal data class TypeScriptPreparedPackageMetadataFile(
    val archivePath: String,
    val size: Long,
    val sha256: String,
    val typeState: String,
) {

    val boundaryPath: String
        get() = if (archivePath == "package.json") {
            "/"
        } else {
            "/${archivePath.removeSuffix("/package.json")}"
        }
}

internal data class TypeScriptPreparedPackageMetadata(
    val dependencyRoots: List<String>,
    val files: List<TypeScriptPreparedPackageMetadataFile>,
    val inventoryFingerprint: String,
) {

    init {
        require(dependencyRoots == dependencyRoots.sorted() && dependencyRoots.distinct() == dependencyRoots)
        require(files.isNotEmpty() && files.first().archivePath == "package.json")
        require(files == files.sortedBy(TypeScriptPreparedPackageMetadataFile::archivePath))
        require(files.map(TypeScriptPreparedPackageMetadataFile::archivePath).distinct().size == files.size)
        requireLowerSha256(inventoryFingerprint, "package metadata inventory fingerprint")
        files.forEach { file ->
            require(file.size >= 0L)
            requireLowerSha256(file.sha256, "package metadata file fingerprint")
            require(file.typeState in PACKAGE_TYPE_STATES)
            require(
                file.archivePath == "package.json" ||
                    dependencyRoots.any { root -> file.archivePath.startsWith("$root/") },
            )
        }
    }

    val archivePath: String
        get() = files.first().archivePath

    val size: Long
        get() = files.first().size

    val sha256: String
        get() = files.first().sha256

    val typeState: String
        get() = files.first().typeState

    fun nearestFileForSource(sourcePath: String): TypeScriptPreparedPackageMetadataFile {
        val canonicalSourcePath = sourcePath.removePrefix("/")
        require(canonicalSourcePath.startsWith("sources/"))
        val projectPath = "/${canonicalSourcePath.removePrefix("sources/")}"
        return requireNotNull(
            files.asSequence()
                .filter { file ->
                    file.boundaryPath == "/" || projectPath.startsWith("${file.boundaryPath}/")
                }
                .maxByOrNull { file -> file.boundaryPath.length },
        ) { "TypeScript source has no accepted package boundary." }
    }

    private companion object {
        val PACKAGE_TYPE_STATES = setOf("missing", "module", "commonjs")
    }
}

internal data class TypeScriptPreparedDependencyLayerFile(
    val archivePath: String,
    val size: Long,
    val sha256: String,
) {
    init {
        require(archivePath.startsWith("dependencies/"))
        require(size in 0L..TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_SINGLE_FILE_BYTES)
        requireLowerSha256(sha256, "dependency layer file fingerprint")
    }
}

internal data class TypeScriptPreparedDependencyLayer(
    val identityKind: String,
    val identityPath: String,
    val identityFingerprint: String,
    val inventoryFingerprint: String,
    val fingerprint: String,
    val files: List<TypeScriptPreparedDependencyLayerFile>,
    val totalBytes: Long,
    val totalPathBytes: Long,
) {
    init {
        require(identityKind in setOf("package-lock", "pnpm-lock", "content"))
        require(identityPath.isEmpty() || identityPath.startsWith("dependencies/lockfiles/"))
        requireLowerSha256(identityFingerprint, "dependency layer identity fingerprint")
        requireLowerSha256(inventoryFingerprint, "dependency layer inventory fingerprint")
        requireLowerSha256(fingerprint, "dependency layer fingerprint")
        require(files.isNotEmpty())
        require(files == files.sortedBy(TypeScriptPreparedDependencyLayerFile::archivePath))
        require(files.map(TypeScriptPreparedDependencyLayerFile::archivePath).distinct().size == files.size)
        require(files.size <= TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_FILES)
        require(totalBytes == files.sumOf(TypeScriptPreparedDependencyLayerFile::size))
        require(totalBytes <= TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_BYTES)
        require(
            totalPathBytes == files.sumOf { file ->
                TypeScriptUtf8.encode(file.archivePath, "dependency layer path").size.toLong()
            },
        )
        require(totalPathBytes <= TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_PATH_BYTES)
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
    val configByteLength: Long?,
    val configSha256: String?,
    val authority: TypeScriptCompilerAuthority,
    val rootPaths: List<String> = listOf(entryPath),
    val sourceFiles: List<TypeScriptPreparedSource> = emptyList(),
    val packageMetadata: TypeScriptPreparedPackageMetadata? = null,
    val dependencyLayer: TypeScriptPreparedDependencyLayer? = null,
)

internal object TypeScriptSingleFileArchive {

    private val emptyTsConfig = "{}".toByteArray(StandardCharsets.UTF_8)

    fun write(
        sourceName: String,
        sourceBytes: ByteArray,
        requestId: String,
        projectId: String,
        authority: TypeScriptCompilerAuthority,
        destination: File,
    ): TypeScriptPreparedCompilation {
        require(requestId.isNotBlank())
        require(requestId.length <= TypeScriptCompilerContract.MAX_REQUEST_ID_CHARS)
        require(projectId.isNotBlank())
        require(projectId.length <= TypeScriptCompilerContract.MAX_PROJECT_ID_CHARS)
        TypeScriptUtf8.decode(sourceBytes, "TypeScript source")

        val canonicalSourceName = canonicalSourceName(sourceName, authority.targetProfile)
        val entryPath = "sources/$canonicalSourceName"
        val entrySourceUri = entrySourceUri(
            projectId = projectId,
            sourceName = canonicalSourceName,
            targetProfile = authority.targetProfile,
        )
        require(entrySourceUri.length <= TypeScriptCompilerContract.MAX_SOURCE_URI_CHARS)
        val configBytes = when (authority.configKind) {
            TypeScriptR2Contract.CONFIG_KIND_NONE -> null
            TypeScriptR2Contract.CONFIG_KIND_TSCONFIG -> emptyTsConfig
            else -> error("Unsupported TypeScript config kind: ${authority.configKind}")
        }
        val sourceSha256 = sha256(sourceBytes)
        val configSha256 = configBytes?.let(::sha256)
        val callerBytes = Math.addExact(
            sourceBytes.size.toLong(),
            configBytes?.size?.toLong() ?: 0L,
        )
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
            configBytes = configBytes,
            configSha256 = configSha256,
        )
        val manifest = manifestBytes(
            requestId = requestId,
            projectId = projectId,
            entrySourceUri = entrySourceUri,
            entryPath = entryPath,
            sourceBytes = sourceBytes,
            sourceSha256 = sourceSha256,
            configBytes = configBytes,
            configSha256 = configSha256,
            callerInventoryFingerprint = inventoryFingerprint,
            authority = authority,
        )
        require(manifest.size.toLong() <= TypeScriptCompilerContract.MAX_MANIFEST_BYTES) {
            "TypeScript request manifest exceeds its byte limit."
        }

        val entries = linkedMapOf(
            TypeScriptR2Contract.INPUT_ARCHIVE_MANIFEST_PATH to manifest,
        )
        configBytes?.let { bytes -> entries[CONTROLLED_CONFIG_PATH] = bytes }
        entries[entryPath] = sourceBytes
        StoredZipV1Writer.write(destination, entries)
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
            configByteLength = configBytes?.size?.toLong(),
            configSha256 = configSha256,
            authority = authority,
            rootPaths = listOf(entryPath),
            sourceFiles = listOf(
                TypeScriptPreparedSource(
                    archivePath = entryPath,
                    projectRelativePath = canonicalSourceName,
                    originalFile = null,
                    sha256 = sourceSha256,
                ),
            ),
        )
    }

    private fun manifestBytes(
        requestId: String,
        projectId: String,
        entrySourceUri: String,
        entryPath: String,
        sourceBytes: ByteArray,
        sourceSha256: String,
        configBytes: ByteArray?,
        configSha256: String?,
        callerInventoryFingerprint: String,
        authority: TypeScriptCompilerAuthority,
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
            appendJsonField("targetProfile", authority.targetProfile)
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
            if (configBytes != null) {
                append(',')
                appendJsonString("config")
                append(':')
                append('{')
                appendJsonField("path", CONTROLLED_CONFIG_PATH)
                append(',')
                appendJsonField("size", configBytes.size.toLong())
                append(',')
                appendJsonField("sha256", requireNotNull(configSha256))
                append(',')
                appendJsonField("policyRevision", requireNotNull(authority.configPolicyRevision))
                append(',')
                appendJsonField(
                    "policyFingerprint",
                    requireNotNull(authority.configPolicyFingerprint),
                )
                append('}')
            }
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
        configBytes: ByteArray?,
        configSha256: String?,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(INPUT_INVENTORY_PREFIX.toByteArray(StandardCharsets.US_ASCII))
        digest.updateInventoryRecord(
            domain = "source",
            path = "/$sourcePath",
            size = sourceBytes.size.toLong(),
            sha256 = sourceSha256,
        )
        configBytes?.let { bytes ->
            digest.updateInventoryRecord(
                domain = "config",
                path = CONTROLLED_CONFIG_PATH,
                size = bytes.size.toLong(),
                sha256 = requireNotNull(configSha256),
            )
        }
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

    private fun canonicalSourceName(sourceName: String, targetProfile: String): String {
        val candidate = sourceName.takeIf { name ->
            name.isNotBlank() &&
                TypeScriptSourceExtensions.forProfile(targetProfile)
                    .any(name::endsWith) &&
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
        return requireNotNull(candidate) {
            "The TypeScript source name is not canonical for the $targetProfile profile."
        }
    }

    private fun entrySourceUri(
        projectId: String,
        sourceName: String,
        targetProfile: String,
    ): String {
        val encodedSourceName = percentEncodeSegment(sourceName)
        return when (targetProfile) {
            TypeScriptCompilerContract.PROFILE_RHINO ->
                "autojs://source/$projectId/$encodedSourceName"
            TypeScriptCompilerContract.PROFILE_NODE -> {
                val projectNamespace = "p-" + sha256(
                    TypeScriptUtf8.encode(projectId, "TypeScript project ID"),
                )
                "autojs://source/$projectNamespace/sources/$encodedSourceName"
            }
            else -> error("Unsupported TypeScript target profile: $targetProfile")
        }
    }

    private fun percentEncodeSegment(value: String): String {
        val bytes = TypeScriptUtf8.encode(value, "TypeScript source name")
        return buildString(bytes.size) {
            bytes.forEach { byte ->
                val unsigned = byte.toInt() and 0xff
                if (
                    unsigned in 'a'.code..'z'.code ||
                    unsigned in 'A'.code..'Z'.code ||
                    unsigned in '0'.code..'9'.code ||
                    unsigned == '-'.code ||
                    unsigned == '.'.code ||
                    unsigned == '_'.code ||
                    unsigned == '~'.code
                ) {
                    append(unsigned.toChar())
                } else {
                    append('%')
                    append(HEX_DIGITS[unsigned ushr 4])
                    append(HEX_DIGITS[unsigned and 0x0f])
                }
            }
        }
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
    private const val INPUT_INVENTORY_PREFIX = "autojs6.typescript.input-inventory.v1\n"
    private const val HEX_DIGITS = "0123456789ABCDEF"
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
