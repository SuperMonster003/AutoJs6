package org.autojs.autojs.core.plugin.typescript

import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.LinkedHashMap
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptPluginIds
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

/** Builds one bounded, immutable snapshot of a local TypeScript project directory. */
internal object TypeScriptProjectArchive {

    fun write(
        projectRoot: File,
        entryFile: File,
        requestId: String,
        projectId: String,
        authority: TypeScriptCompilerAuthority,
        destination: File,
    ): TypeScriptPreparedCompilation {
        require(requestId.isNotBlank())
        require(requestId.length <= TypeScriptCompilerContract.MAX_REQUEST_ID_CHARS)
        require(projectId.isNotBlank())
        require(projectId.length <= TypeScriptCompilerContract.MAX_PROJECT_ID_CHARS)

        val root = projectRoot.normalizedCanonicalFile()
        val entry = entryFile.normalizedCanonicalFile()
        require(root.isDirectory) { "TypeScript project root is not a directory." }
        require(entry.isFile && entry.isInside(root)) {
            "TypeScript project entry is outside its project root."
        }

        val sources = collectSources(root, authority.targetProfile)
        val entryRelativePath = entry.relativePathInside(root)
        val entryPath = "sources/$entryRelativePath"
        require(sources.any { source -> source.archivePath == entryPath }) {
            "TypeScript project entry is not an admitted source file."
        }
        val rootPaths = sources.asSequence()
            .filter { source -> isExecutableSource(source.projectRelativePath, authority.targetProfile) }
            .map(ProjectSource::archivePath)
            .sorted()
            .toList()
        require(entryPath in rootPaths) {
            "TypeScript project entry is not executable for ${authority.targetProfile}."
        }

        val configBytes = when (authority.configKind) {
            TypeScriptR2Contract.CONFIG_KIND_NONE -> null
            TypeScriptR2Contract.CONFIG_KIND_TSCONFIG -> {
                val config = File(root, CONTROLLED_CONFIG_PATH)
                if (config.isFile) {
                    readBounded(config, TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_BYTES.toLong())
                } else {
                    EMPTY_TSCONFIG.copyOf()
                }
            }
            else -> error("Unsupported TypeScript config kind: ${authority.configKind}")
        }
        val packageBytes = if (authority.targetProfile == TypeScriptCompilerContract.PROFILE_NODE) {
            File(root, PACKAGE_METADATA_PATH).takeIf(File::isFile)?.let { packageFile ->
                readBounded(packageFile, MAX_PACKAGE_METADATA_FILE_BYTES)
            }
        } else {
            null
        }

        try {
            val packageMetadata = packageBytes?.let { bytes ->
                packageMetadata(bytes, authority)
            }
            if (
                authority.targetProfile == TypeScriptCompilerContract.PROFILE_NODE &&
                sources.any { source ->
                    source.projectRelativePath.endsWith(".ts") &&
                        !source.projectRelativePath.endsWith(".d.ts")
                }
            ) {
                requireNotNull(packageMetadata) {
                    "Node .ts project compilation requires a root package.json with an explicit module boundary."
                }
            }

            val configSha256 = configBytes?.let(::sha256)
            val callerFileCount = sources.size +
                (if (configBytes == null) 0 else 1) +
                (if (packageBytes == null) 0 else 1)
            require(callerFileCount <= TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES) {
                "TypeScript project exceeds the ordinary input file-count limit."
            }
            val callerBytes = sources.fold(0L) { total, source ->
                Math.addExact(total, source.bytes.size.toLong())
            }.let { total -> Math.addExact(total, configBytes?.size?.toLong() ?: 0L) }
                .let { total -> Math.addExact(total, packageBytes?.size?.toLong() ?: 0L) }
            require(callerBytes <= TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES) {
                "TypeScript project exceeds the ordinary input byte limit."
            }

            val inventoryFingerprint = callerInventoryFingerprint(
                sources = sources,
                configBytes = configBytes,
                configSha256 = configSha256,
                packageMetadata = packageMetadata,
            )
            val entrySourceUri = sourceUri(projectId, entryPath)
            val manifest = manifestBytes(
                requestId = requestId,
                projectId = projectId,
                entrySourceUri = entrySourceUri,
                entryPath = entryPath,
                rootPaths = rootPaths,
                sources = sources,
                configBytes = configBytes,
                configSha256 = configSha256,
                packageMetadata = packageMetadata,
                callerInventoryFingerprint = inventoryFingerprint,
                authority = authority,
            )
            require(manifest.size.toLong() <= TypeScriptCompilerContract.MAX_MANIFEST_BYTES) {
                "TypeScript project request manifest exceeds its byte limit."
            }

            val entries = LinkedHashMap<String, ByteArray>(sources.size + 3)
            entries[TypeScriptR2Contract.INPUT_ARCHIVE_MANIFEST_PATH] = manifest
            configBytes?.let { bytes -> entries[CONTROLLED_CONFIG_PATH] = bytes }
            packageBytes?.let { bytes -> entries[PACKAGE_METADATA_PATH] = bytes }
            sources.forEach { source -> entries[source.archivePath] = source.bytes }
            StoredZipV1Writer.write(destination, entries)
            require(destination.length() <= TypeScriptCompilerContract.MAX_INPUT_ARCHIVE_BYTES) {
                "TypeScript project input archive exceeds its transport byte limit."
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
                rootPaths = rootPaths,
                sourceFiles = sources.map { source ->
                    TypeScriptPreparedSource(
                        archivePath = source.archivePath,
                        projectRelativePath = source.projectRelativePath,
                        originalFile = source.file,
                        sha256 = source.sha256,
                    )
                },
                packageMetadata = packageMetadata,
            )
        } finally {
            sources.forEach { source -> source.bytes.fill(0) }
            configBytes?.fill(0)
            packageBytes?.fill(0)
        }
    }

    private fun collectSources(
        root: File,
        targetProfile: String,
    ): List<ProjectSource> {
        val sources = root.walkTopDown()
            .onEnter { directory ->
                directory == root || directory.name !in EXCLUDED_DIRECTORY_NAMES
            }
            .filter(File::isFile)
            .mapNotNull { candidate ->
                val file = candidate.normalizedCanonicalFile()
                require(file.isInside(root)) {
                    "TypeScript project source escapes its project root."
                }
                require(candidate.absoluteFile.normalize().path == file.path) {
                    "TypeScript project sources may not traverse symbolic links."
                }
                val relativePath = file.relativePathInside(root)
                if (relativePath.split('/').any { segment -> segment.equals("node_modules", true) }) {
                    return@mapNotNull null
                }
                if (
                    targetProfile == TypeScriptCompilerContract.PROFILE_NODE &&
                    file.name.equals(PACKAGE_METADATA_PATH, ignoreCase = true) &&
                    !relativePath.equals(PACKAGE_METADATA_PATH, ignoreCase = true)
                ) {
                    error("Nested TypeScript package boundaries are not supported: $relativePath")
                }
                if (!isSnapshotSource(relativePath, targetProfile)) return@mapNotNull null
                requireCanonicalRelativePath(relativePath)
                val bytes = readBounded(
                    file,
                    TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
                )
                TypeScriptUtf8.decode(bytes, "TypeScript project source $relativePath")
                ProjectSource(
                    archivePath = "sources/$relativePath",
                    projectRelativePath = relativePath,
                    file = file,
                    bytes = bytes,
                    sha256 = sha256(bytes),
                )
            }
            .sortedBy(ProjectSource::archivePath)
            .toList()
        require(sources.isNotEmpty()) { "TypeScript project contains no admitted source files." }
        require(sources.map(ProjectSource::archivePath).distinct().size == sources.size)
        require(
            sources.map { source -> source.archivePath.asciiCaseFold() }.distinct().size ==
                sources.size,
        ) {
            "TypeScript project sources contain an ASCII case-fold collision."
        }
        return sources
    }

    private fun isSnapshotSource(relativePath: String, targetProfile: String): Boolean {
        val name = relativePath.substringAfterLast('/')
        if (name in RESERVED_ROOT_FILES || name.equals(PACKAGE_METADATA_PATH, ignoreCase = true)) {
            return false
        }
        if (relativePath.split('/').any { segment -> segment.equals("node_modules", true) }) {
            return false
        }
        if (name.endsWith(".tsx") || name.endsWith(".d.mts") || name.endsWith(".d.cts")) {
            return false
        }
        return when {
            name.endsWith(".ts") -> true
            targetProfile == TypeScriptCompilerContract.PROFILE_NODE &&
                (name.endsWith(".mts") || name.endsWith(".cts")) -> true
            name.endsWith(".json") -> true
            name.endsWith(".js") || name.endsWith(".mjs") || name.endsWith(".cjs") -> true
            else -> false
        }
    }

    private fun isExecutableSource(relativePath: String, targetProfile: String): Boolean {
        if (relativePath.endsWith(".d.ts")) return false
        return relativePath.endsWith(".ts") ||
            targetProfile == TypeScriptCompilerContract.PROFILE_NODE &&
            (relativePath.endsWith(".mts") || relativePath.endsWith(".cts"))
    }

    private fun packageMetadata(
        bytes: ByteArray,
        authority: TypeScriptCompilerAuthority,
    ): TypeScriptPreparedPackageMetadata {
        require(bytes.size.toLong() <= MAX_PACKAGE_METADATA_FILE_BYTES)
        val root = TypeScriptStrictJson.parseObject(
            bytes = bytes,
            maximumDepth = MAX_PACKAGE_METADATA_JSON_DEPTH,
            maximumNodes = MAX_PACKAGE_METADATA_JSON_NODES,
        )
        require("scripts" !in root) {
            "TypeScript package metadata may not carry lifecycle scripts."
        }
        val typeState = when {
            "type" !in root -> PACKAGE_TYPE_MISSING
            root["type"] == PACKAGE_TYPE_MODULE -> PACKAGE_TYPE_MODULE
            root["type"] == PACKAGE_TYPE_COMMONJS -> PACKAGE_TYPE_COMMONJS
            else -> error("TypeScript package metadata has an unsupported top-level type value.")
        }
        val digest = sha256(bytes)
        val canonicalInventory = PACKAGE_METADATA_INVENTORY_DOMAIN +
            "|file=$PACKAGE_METADATA_PATH,${bytes.size},$digest"
        require(canonicalInventory.all { character -> character.code in 0x20..0x7e })
        return TypeScriptPreparedPackageMetadata(
            archivePath = PACKAGE_METADATA_PATH,
            size = bytes.size.toLong(),
            sha256 = digest,
            inventoryFingerprint = sha256(canonicalInventory.toByteArray(StandardCharsets.US_ASCII)),
            typeState = typeState,
        ).also {
            require(requireNotNull(authority.packageMetadataSchemaRevision) > 0)
            require(requireNotNull(authority.packageMetadataPolicyRevision) > 0)
            requireLowerSha256(
                requireNotNull(authority.packageMetadataPolicyFingerprint),
                "package metadata policy fingerprint",
            )
        }
    }

    private fun callerInventoryFingerprint(
        sources: List<ProjectSource>,
        configBytes: ByteArray?,
        configSha256: String?,
        packageMetadata: TypeScriptPreparedPackageMetadata?,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(INPUT_INVENTORY_PREFIX.toByteArray(StandardCharsets.US_ASCII))
        sources.forEach { source ->
            digest.updateInventoryRecord(
                domain = "source",
                path = "/${source.archivePath}",
                size = source.bytes.size.toLong(),
                sha256 = source.sha256,
            )
        }
        configBytes?.let { bytes ->
            digest.updateInventoryRecord(
                domain = "config",
                path = CONTROLLED_CONFIG_PATH,
                size = bytes.size.toLong(),
                sha256 = requireNotNull(configSha256),
            )
        }
        packageMetadata?.let { metadata ->
            digest.updateInventoryRecord(
                domain = "package-metadata",
                path = "/${metadata.archivePath}",
                size = metadata.size,
                sha256 = metadata.sha256,
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

    private fun manifestBytes(
        requestId: String,
        projectId: String,
        entrySourceUri: String,
        entryPath: String,
        rootPaths: List<String>,
        sources: List<ProjectSource>,
        configBytes: ByteArray?,
        configSha256: String?,
        packageMetadata: TypeScriptPreparedPackageMetadata?,
        callerInventoryFingerprint: String,
        authority: TypeScriptCompilerAuthority,
    ): ByteArray {
        val json = buildString(4_096 + sources.size * 160) {
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
            if (packageMetadata != null) {
                append(',')
                appendJsonString("packageMetadata")
                append(':')
                append('{')
                appendJsonField(
                    "schemaRevision",
                    requireNotNull(authority.packageMetadataSchemaRevision),
                )
                append(',')
                appendJsonField(
                    "policyRevision",
                    requireNotNull(authority.packageMetadataPolicyRevision),
                )
                append(',')
                appendJsonField(
                    "policyFingerprint",
                    requireNotNull(authority.packageMetadataPolicyFingerprint),
                )
                append(',')
                appendJsonField("inventoryFingerprint", packageMetadata.inventoryFingerprint)
                append(',')
                appendJsonString("dependencyRoots")
                append(':')
                append("[]")
                append(',')
                appendJsonString("files")
                append(':')
                append('[')
                append('{')
                appendJsonField("path", packageMetadata.archivePath)
                append(',')
                appendJsonField("size", packageMetadata.size)
                append(',')
                appendJsonField("sha256", packageMetadata.sha256)
                append('}')
                append(']')
                append('}')
            }
            append(',')
            appendJsonField("callerInventoryFingerprint", callerInventoryFingerprint)
            append(',')
            appendJsonString("rootPaths")
            append(':')
            appendJsonStringArray(rootPaths)
            append(',')
            appendJsonString("files")
            append(':')
            append('[')
            sources.forEachIndexed { index, source ->
                if (index > 0) append(',')
                append('{')
                appendJsonField("path", source.archivePath)
                append(',')
                appendJsonField("sha256", source.sha256)
                append(',')
                appendJsonField("size", source.bytes.size.toLong())
                append('}')
            }
            append(']')
            append('}')
        }
        return TypeScriptUtf8.encode(json, "TypeScript project request manifest")
    }

    private fun sourceUri(projectId: String, entryPath: String): String {
        val namespace = "p-" + sha256(TypeScriptUtf8.encode(projectId, "TypeScript project ID"))
        val encodedPath = entryPath.split('/').joinToString("/") { segment ->
            percentEncodeSegment(segment)
        }
        return "autojs://source/$namespace/$encodedPath".also { uri ->
            require(uri.length <= TypeScriptCompilerContract.MAX_SOURCE_URI_CHARS)
        }
    }

    private fun percentEncodeSegment(value: String): String {
        val bytes = TypeScriptUtf8.encode(value, "TypeScript project path segment")
        return buildString(bytes.size) {
            bytes.forEach { byte ->
                val unsigned = byte.toInt() and 0xff
                if (
                    unsigned in 'a'.code..'z'.code ||
                    unsigned in 'A'.code..'Z'.code ||
                    unsigned in '0'.code..'9'.code ||
                    unsigned == '-'.code || unsigned == '.'.code ||
                    unsigned == '_'.code || unsigned == '~'.code
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

    private fun readBounded(file: File, maximumBytes: Long): ByteArray {
        require(file.length() in 0..maximumBytes) {
            "TypeScript project file exceeds its byte limit: ${file.path}"
        }
        return file.readBytes().also { bytes ->
            require(bytes.size.toLong() <= maximumBytes)
        }
    }

    private fun requireCanonicalRelativePath(path: String) {
        val encoded = TypeScriptUtf8.encode("sources/$path", "TypeScript project archive path")
        require(
            path.isNotBlank() &&
                !path.startsWith('/') &&
                !path.endsWith('/') &&
                '\\' !in path &&
                encoded.size <= TypeScriptCompilerContract.MAX_ARCHIVE_ENTRY_NAME_BYTES,
        ) { "TypeScript project path is not canonical." }
        val segments = path.split('/')
        require(
            segments.size + 1 <= TypeScriptCompilerContract.MAX_ARCHIVE_PATH_DEPTH &&
                segments.all { segment ->
                    segment.isNotEmpty() && segment != "." && segment != ".." &&
                        segment.none { character ->
                            character.code in 0x00..0x1f || character.code in 0x7f..0x9f
                        }
                },
        ) { "TypeScript project path is not canonical." }
    }

    private fun File.relativePathInside(root: File): String {
        require(isInside(root))
        return path.removePrefix(root.path + File.separator)
            .replace(File.separatorChar, '/')
            .also(::requireCanonicalRelativePath)
    }

    private fun File.isInside(root: File): Boolean =
        path.startsWith(root.path + File.separator)

    private fun File.normalizedCanonicalFile(): File =
        runCatching { canonicalFile }.getOrElse { absoluteFile.normalize() }

    private fun String.asciiCaseFold(): String = buildString(length) {
        this@asciiCaseFold.forEach { character ->
            append(if (character in 'A'..'Z') character + 32 else character)
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

    private fun StringBuilder.appendJsonStringArray(values: List<String>) {
        append('[')
        values.forEachIndexed { index, value ->
            if (index > 0) append(',')
            appendJsonString(value)
        }
        append(']')
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
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private data class ProjectSource(
        val archivePath: String,
        val projectRelativePath: String,
        val file: File,
        val bytes: ByteArray,
        val sha256: String,
    )

    private val EMPTY_TSCONFIG = "{}".toByteArray(StandardCharsets.UTF_8)
    private val EXCLUDED_DIRECTORY_NAMES = setOf(
        ".git",
        ".gradle",
        ".idea",
        "build",
        "dist",
        "node_modules",
    )
    private val RESERVED_ROOT_FILES = setOf("project.json", "tsconfig.json")
    private const val CONTROLLED_CONFIG_PATH = "tsconfig.json"
    private const val PACKAGE_METADATA_PATH = "package.json"
    private const val PACKAGE_METADATA_INVENTORY_DOMAIN =
        "autojs6.typescript.package-metadata.inventory.v1"
    private const val PACKAGE_TYPE_MISSING = "missing"
    private const val PACKAGE_TYPE_MODULE = "module"
    private const val PACKAGE_TYPE_COMMONJS = "commonjs"
    private const val MAX_PACKAGE_METADATA_FILE_BYTES = 64L * 1024L
    private const val MAX_PACKAGE_METADATA_JSON_DEPTH = 16
    private const val MAX_PACKAGE_METADATA_JSON_NODES = 4 * 1024
    private const val INPUT_INVENTORY_PREFIX = "autojs6.typescript.input-inventory.v1\n"
    private const val HEX_DIGITS = "0123456789ABCDEF"
}
