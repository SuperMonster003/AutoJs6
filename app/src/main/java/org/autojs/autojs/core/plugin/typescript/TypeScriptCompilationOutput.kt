package org.autojs.autojs.core.plugin.typescript

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptPluginIds
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

internal data class TypeScriptTerminalResult(
    val requestId: String,
    val outcome: String,
    val succeeded: Boolean,
    val errorCode: String?,
    val errorMessage: String?,
    val diagnosticCount: Int,
    val emittedModuleCount: Int,
    val outputManifestSha256: String?,
)

internal data class TypeScriptDiagnosticRange(
    val start: Int,
    val length: Int,
    val line: Int,
    val column: Int,
)

internal data class TypeScriptRelatedInformation(
    val uri: String?,
    val range: TypeScriptDiagnosticRange?,
    val category: Int,
    val code: Int,
    val message: String,
)

internal data class TypeScriptDiagnostic(
    val phase: String,
    val uri: String?,
    val range: TypeScriptDiagnosticRange?,
    val category: Int,
    val code: Int,
    val message: String,
    val relatedInformation: List<TypeScriptRelatedInformation>,
) {
    val isError: Boolean
        get() = category == TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_ERROR
}

internal sealed interface TypeScriptCompilationOutput {
    val diagnostics: List<TypeScriptDiagnostic>

    data class Emitted(
        val javaScript: String,
        val sourceMap: String,
        val entryModulePath: String,
        override val diagnostics: List<TypeScriptDiagnostic>,
    ) : TypeScriptCompilationOutput

    data class Diagnostics(
        override val diagnostics: List<TypeScriptDiagnostic>,
    ) : TypeScriptCompilationOutput
}

internal object TypeScriptCompilationOutputReader {

    fun read(
        outputFile: File,
        prepared: TypeScriptPreparedCompilation,
        terminal: TypeScriptTerminalResult,
    ): TypeScriptCompilationOutput {
        require(terminal.requestId == prepared.requestId) {
            "TypeScript terminal result belongs to another request."
        }
        require(
            terminal.outcome == TypeScriptCompilerContract.OUTCOME_EMITTED ||
                terminal.outcome == TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS,
        ) {
            "TypeScript terminal outcome does not carry an output archive."
        }
        require(
            terminal.succeeded ==
                (terminal.outcome == TypeScriptCompilerContract.OUTCOME_EMITTED),
        ) {
            "TypeScript terminal success flag conflicts with its outcome."
        }
        require(
            outputFile.isFile &&
                outputFile.length() in 1..TypeScriptCompilerContract.MAX_OUTPUT_ARCHIVE_BYTES,
        ) {
            "TypeScript output archive is missing or exceeds its transport limit."
        }
        val expectedManifestSha256 = requireNotNull(terminal.outputManifestSha256) {
            "TypeScript terminal result did not bind the output manifest."
        }
        requireLowerSha256(expectedManifestSha256, "output manifest fingerprint")

        return ZipFile(outputFile).use { archive ->
            val entries = archive.entries().asSequence().toList()
            require(entries.isNotEmpty() && entries.size <= TypeScriptCompilerContract.MAX_OUTPUT_FILES)
            require(entries.none(ZipEntry::isDirectory))
            val entriesByName = LinkedHashMap<String, ZipEntry>(entries.size)
            entries.forEach { entry ->
                require(isCanonicalArchivePath(entry.name)) {
                    "TypeScript output archive contains a non-canonical path."
                }
                require(entriesByName.put(entry.name, entry) == null) {
                    "TypeScript output archive contains a duplicate path."
                }
            }
            require(
                entriesByName.keys.map(::asciiCaseFold).distinct().size == entriesByName.size,
            ) {
                "TypeScript output archive contains an ASCII case-fold collision."
            }

            val manifestEntry = requireNotNull(
                entriesByName[TypeScriptR2Contract.OUTPUT_ARCHIVE_MANIFEST_PATH],
            ) {
                "TypeScript output archive has no compilation manifest."
            }
            val manifestBytes = archive.readBounded(
                manifestEntry,
                TypeScriptCompilerContract.MAX_MANIFEST_BYTES,
            )
            require(sha256(manifestBytes) == expectedManifestSha256) {
                "TypeScript output manifest fingerprint does not match the terminal result."
            }
            val manifest = TypeScriptStrictJson.parseObject(manifestBytes)
            readValidatedOutput(
                archive = archive,
                entriesByName = entriesByName,
                manifest = manifest,
                prepared = prepared,
                terminal = terminal,
            )
        }
    }

    private fun readValidatedOutput(
        archive: ZipFile,
        entriesByName: Map<String, ZipEntry>,
        manifest: Map<String, Any?>,
        prepared: TypeScriptPreparedCompilation,
        terminal: TypeScriptTerminalResult,
    ): TypeScriptCompilationOutput {
        val expectedManifestKeys = if (
            prepared.authority.targetProfile == TypeScriptCompilerContract.PROFILE_NODE
        ) {
            NODE_OUTPUT_MANIFEST_KEYS
        } else {
            OUTPUT_MANIFEST_KEYS
        }
        manifest.requireExactKeys(expectedManifestKeys, "output manifest")
        manifest.requireString("schema").requireEqual(TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA)
        manifest.requireInt("revision").requireEqual(
            TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA_REVISION,
        )
        manifest.requireInt("outputQuotaRevision").requireEqual(
            TypeScriptR2Contract.ORDINARY_COMPILATION_OUTPUT_QUOTA_REVISION,
        )
        manifest.requireInt("contractVersion").requireEqual(TypeScriptCompilerContract.VERSION)
        manifest.requireString("contractHash").requireEqual(TypeScriptCompilerContract.HASH)
        manifest.requireInt("outputArchiveTransportVersion").requireEqual(
            TypeScriptCompilerContract.OUTPUT_ARCHIVE_TRANSPORT_VERSION,
        )
        manifest.requireString("requestId").requireEqual(prepared.requestId)
        manifest.requireString("projectId").requireEqual(prepared.projectId)
        manifest.requireString("entrySourceUri").requireEqual(prepared.entrySourceUri)
        manifest.requireString("targetProfile").requireEqual(prepared.authority.targetProfile)
        manifest.requireInt("profileSchemaRevision").requireEqual(
            prepared.authority.profileSchemaRevision,
        )
        manifest.requireInt("profileRevision").requireEqual(prepared.authority.profileRevision)
        manifest.requireInt("configSchemaRevision").requireEqual(
            prepared.authority.configSchemaRevision,
        )
        manifest.requireString("configKind").requireEqual(prepared.authority.configKind)
        manifest.requireInt("runtimeCapabilitySchemaRevision").requireEqual(
            TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION,
        )
        manifest.requireString("compilationMode").requireEqual(
            TypeScriptCompilerContract.MODE_DEVELOPMENT,
        )
        manifest.requireString("compilerApi").requireEqual(COMPILER_API)
        manifest.requireString("compilerPackage").requireEqual(TypeScriptPluginIds.ID)
        manifest.requireString("compilerVersion").requireEqual(
            prepared.authority.compilerVersion,
        )
        manifest.requireString("compilerDistributionFingerprint").requireEqual(
            prepared.authority.compilerDistributionFingerprint,
        )
        manifest.requireString("declarationsFingerprint").requireEqual(
            prepared.authority.declarationsFingerprint,
        )
        manifest.requireString("runtimeCapabilitiesFingerprint").requireEqual(
            prepared.authority.runtimeCapabilitiesFingerprint,
        )
        manifest.requireString("inputArchiveSha256").requireEqual(
            prepared.inputArchiveSha256,
        )
        manifest.requireString("requestManifestSha256").requireEqual(
            prepared.requestManifestSha256,
        )
        manifest.requireInt("inputArchiveSchemaRevision").requireEqual(
            TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION,
        )
        manifest.requireInt("inputQuotaRevision").requireEqual(
            TypeScriptR2Contract.ORDINARY_COMPILATION_INPUT_QUOTA_REVISION,
        )
        manifest.requireString("declarationProfileFingerprint").requireEqual(
            prepared.authority.declarationProfileFingerprint,
        )
        manifest.requireString("outcome").requireEqual(terminal.outcome)
        manifest.requireInt("diagnosticCount").requireEqual(terminal.diagnosticCount)
        manifest.requireInt("emittedModuleCount").requireEqual(terminal.emittedModuleCount)
        manifest.requireString("diagnosticsPath").requireEqual(
            TypeScriptR2Contract.OUTPUT_ARCHIVE_DIAGNOSTICS_PATH,
        )
        manifest.requireLowerSha256("callerInventoryFingerprint")
        manifest.requireLowerSha256("retainedInventoryFingerprint")
        manifest.requireLowerSha256("declarationInventoryFingerprint")
        val effectiveOptionsFingerprint =
            manifest.requireLowerSha256("effectiveOptionsFingerprint")
        manifest.requireLowerSha256("moduleInventoryFingerprint")
        manifest.requireLowerSha256("artifactInventoryFingerprint")
        manifest.requireNonNegativeInt("declarationFileCount")
        manifest.requireNonNegativeLong("declarationByteLength")
        manifest.requireNonNegativeInt("retainedFileCount")
        manifest.requireNonNegativeLong("retainedByteLength")
        manifest.requireNonNegativeLong("largestRetainedFileBytes")
        require(manifest["effectiveOptions"] is Map<*, *>) {
            "TypeScript output manifest effectiveOptions is not an object."
        }
        if (prepared.authority.targetProfile == TypeScriptCompilerContract.PROFILE_NODE) {
            validateNodeResultAuthority(
                manifest = manifest,
                prepared = prepared,
                terminal = terminal,
                effectiveOptionsFingerprint = effectiveOptionsFingerprint,
            )
        }

        val descriptors = manifest.requireObjectList("files").map(::artifactDescriptor)
        require(
            descriptors.size <= TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_FILES + 1,
        )
        require(descriptors.map(ArtifactDescriptor::path) == descriptors.map(ArtifactDescriptor::path).sorted())
        require(descriptors.map(ArtifactDescriptor::path).distinct().size == descriptors.size)
        require(
            entriesByName.keys ==
                (descriptors.mapTo(linkedSetOf(), ArtifactDescriptor::path) +
                    TypeScriptR2Contract.OUTPUT_ARCHIVE_MANIFEST_PATH),
        ) {
            "TypeScript output artifact inventory does not cover the exact ZIP contents."
        }

        var ordinaryArtifactBytes = 0L
        val payloads = HashMap<String, ByteArray>(descriptors.size)
        descriptors.forEach { descriptor ->
            val entry = requireNotNull(entriesByName[descriptor.path])
            val limit = when (descriptor.kind) {
                KIND_DIAGNOSTICS -> TypeScriptR2Contract.MAX_DIAGNOSTICS_DOCUMENT_BYTES
                KIND_JAVASCRIPT ->
                    TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES
                KIND_SOURCE_MAP ->
                    TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_SOURCE_MAP_BYTES
                else -> error("Unsupported TypeScript output artifact kind.")
            }
            val bytes = archive.readBounded(entry, limit)
            require(bytes.size.toLong() == descriptor.size)
            require(sha256(bytes) == descriptor.sha256)
            if (descriptor.kind != KIND_DIAGNOSTICS) {
                ordinaryArtifactBytes = Math.addExact(ordinaryArtifactBytes, bytes.size.toLong())
            }
            payloads[descriptor.path] = bytes
        }
        require(
            ordinaryArtifactBytes <= TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_BYTES,
        )

        val diagnosticsDescriptor = descriptors.singleOrNull { it.kind == KIND_DIAGNOSTICS }
            ?: error("TypeScript output archive must contain one diagnostics document.")
        require(diagnosticsDescriptor.path == TypeScriptR2Contract.OUTPUT_ARCHIVE_DIAGNOSTICS_PATH)
        val diagnostics = parseDiagnostics(
            requireNotNull(payloads[diagnosticsDescriptor.path]),
            prepared,
            terminal.diagnosticCount,
        )

        return when (terminal.outcome) {
            TypeScriptCompilerContract.OUTCOME_EMITTED -> {
                require(manifest.requireBoolean("emitSkipped").not())
                require(terminal.emittedModuleCount == 1)
                val javaScript = descriptors.singleOrNull { it.kind == KIND_JAVASCRIPT }
                    ?: error("Single-file TypeScript output has no unique JavaScript module.")
                val sourceMap = descriptors.singleOrNull { it.kind == KIND_SOURCE_MAP }
                    ?: error("Single-file TypeScript output has no unique Source Map.")
                val entryModulePath = manifest.requireNullableString("entryModulePath")
                    ?: error("Emitted TypeScript output has no entry module.")
                require(entryModulePath == javaScript.path)
                require(
                    sourceMap.path ==
                        "maps/" + javaScript.path.removePrefix("modules/") + ".map",
                )
                require(
                    javaScript.sourcePaths == listOf(prepared.entryPath) &&
                        sourceMap.sourcePaths == listOf(prepared.entryPath),
                )
                TypeScriptCompilationOutput.Emitted(
                    javaScript = TypeScriptUtf8.decode(
                        requireNotNull(payloads[javaScript.path]),
                        "emitted JavaScript",
                    ),
                    sourceMap = TypeScriptUtf8.decode(
                        requireNotNull(payloads[sourceMap.path]),
                        "Source Map",
                    ),
                    entryModulePath = entryModulePath,
                    diagnostics = diagnostics,
                )
            }

            TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS -> {
                require(manifest.requireBoolean("emitSkipped"))
                require(manifest.requireNullableString("entryModulePath") == null)
                require(terminal.diagnosticCount > 0 && terminal.emittedModuleCount == 0)
                require(descriptors.size == 1)
                TypeScriptCompilationOutput.Diagnostics(diagnostics)
            }

            else -> error("Unsupported TypeScript output outcome.")
        }
    }

    private fun validateNodeResultAuthority(
        manifest: Map<String, Any?>,
        prepared: TypeScriptPreparedCompilation,
        terminal: TypeScriptTerminalResult,
        effectiveOptionsFingerprint: String,
    ) {
        manifest.requireInt("nodeModuleOutputPolicySchemaRevision").requireEqual(1)
        manifest.requireInt("nodeModuleOutputPolicyRevision").requireEqual(1)
        val moduleOutputPolicyFingerprint =
            manifest.requireLowerSha256("nodeModuleOutputPolicyFingerprint")
        moduleOutputPolicyFingerprint.requireEqual(NODE_MODULE_OUTPUT_POLICY_FINGERPRINT)
        manifest.requireString("generatedMappingSchema").requireEqual(
            GENERATED_MAPPING_SCHEMA,
        )
        manifest.requireInt("generatedMappingSchemaRevision").requireEqual(1)
        manifest.requireInt("generatedMappingPolicyRevision").requireEqual(1)
        manifest.requireInt("resolverPolicyRevision").requireEqual(2)
        manifest.requireLowerSha256("snapshotTargetInventoryFingerprint")
        require(manifest.getValue("packageMetadataInventoryFingerprint") == null)
        require(manifest.getValue("packageFormatCatalogFingerprint") == null)
        require(manifest.getValue("packageTargetInventoryFingerprint") == null)
        manifest.requireLowerSha256("targetUniverseFingerprint")

        val sourcePath = "/${prepared.entryPath}"
        manifest.requireString("resultSnapshotEntryPath").requireEqual(sourcePath)
        require(manifest.requireStringList("resultSnapshotRootNames") == listOf(sourcePath))

        val mappings = manifest.requireObjectList("generatedModuleMappings")
        require(mappings.size == terminal.emittedModuleCount)
        if (terminal.outcome == TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS) {
            require(mappings.isEmpty())
            return
        }

        val mapping = mappings.single()
        mapping.requireExactKeys(GENERATED_MODULE_MAPPING_KEYS, "generated module mapping")
        val sourceExtension = when {
            sourcePath.endsWith(".mts") -> ".mts"
            sourcePath.endsWith(".cts") -> ".cts"
            else -> error("Single-file Node output has an unsupported source extension.")
        }
        val moduleFormat = if (sourceExtension == ".mts") "esm" else "commonjs"
        val outputExtension = if (sourceExtension == ".mts") ".mjs" else ".cjs"
        val generatedPath = sourcePath.removePrefix("/sources/")
            .removeSuffix(sourceExtension)
            .let { relativePath -> "/outputs/$relativePath$outputExtension" }
        val sourceMapPath = "$generatedPath.map"
        val projectNamespace = "p-" + sha256(
            TypeScriptUtf8.encode(prepared.projectId, "TypeScript project ID"),
        )
        val generatedUri = "autojs://generated/$projectNamespace/" +
            generatedPath.removePrefix("/").split('/').joinToString("/", transform = ::percentEncode)

        mapping.requireString("sourcePath").requireEqual(sourcePath)
        mapping.requireString("projectNamespace").requireEqual(projectNamespace)
        mapping.requireString("originalSourceUri").requireEqual(prepared.entrySourceUri)
        mapping.requireString("moduleFormat").requireEqual(moduleFormat)
        mapping.requireString("outputExtension").requireEqual(outputExtension)
        mapping.requireString("generatedPath").requireEqual(generatedPath)
        mapping.requireString("sourceMapPath").requireEqual(sourceMapPath)
        mapping.requireString("generatedUri").requireEqual(generatedUri)

        val expectedEvidence = buildString {
            append("schema=")
            append(GENERATED_MAPPING_SCHEMA)
            append("|schemaRevision=1")
            append("|mappingPolicyRevision=1")
            append("|moduleOutputPolicyRevision=1")
            append("|moduleOutputPolicyFingerprint=")
            append(moduleOutputPolicyFingerprint)
            append("|profile=node")
            append("|profileSchemaRevision=")
            append(prepared.authority.profileSchemaRevision)
            append("|profileRevision=")
            append(prepared.authority.profileRevision)
            append("|configSchemaRevision=")
            append(prepared.authority.configSchemaRevision)
            append("|configKind=")
            append(percentEncode(prepared.authority.configKind))
            append("|effectiveOptionsFingerprint=")
            append(effectiveOptionsFingerprint)
            append("|runtimeCapabilitiesFingerprint=")
            append(prepared.authority.runtimeCapabilitiesFingerprint)
            append("|projectNamespace=")
            append(percentEncode(projectNamespace))
            append("|sourcePath=")
            append(percentEncode(sourcePath))
            append("|originalSourceUri=")
            append(percentEncode(prepared.entrySourceUri))
            append("|moduleFormat=")
            append(percentEncode(moduleFormat))
            append("|packageTypeState=none")
            append("|packageMetadataInventoryFingerprint=none")
            append("|packageBoundary=none")
            append("|outputExtension=")
            append(percentEncode(outputExtension))
            append("|generatedPath=")
            append(percentEncode(generatedPath))
            append("|sourceMapPath=")
            append(percentEncode(sourceMapPath))
            append("|generatedUri=")
            append(percentEncode(generatedUri))
        }
        require(expectedEvidence.all { character -> character.code in 0x20..0x7e })
        mapping.requireString("canonicalEvidence").requireEqual(expectedEvidence)
        mapping.requireString("fingerprint").also { fingerprint ->
            requireLowerSha256(fingerprint, "generated module mapping fingerprint")
            fingerprint.requireEqual(
                sha256(TypeScriptUtf8.encode(expectedEvidence, "generated mapping evidence")),
            )
        }
    }

    private fun percentEncode(value: String): String {
        val bytes = TypeScriptUtf8.encode(value, "TypeScript Node output identity")
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

    private fun artifactDescriptor(value: Map<String, Any?>): ArtifactDescriptor {
        value.requireExactKeys(ARTIFACT_DESCRIPTOR_KEYS, "artifact descriptor")
        val path = value.requireString("path")
        require(isCanonicalArchivePath(path))
        val kind = value.requireString("kind")
        val size = value.requireNonNegativeLong("size")
        val digest = value.requireString("sha256")
        requireLowerSha256(digest, "artifact fingerprint")
        val sourcePaths = value.requireStringList("sourcePaths")
        require(sourcePaths == sourcePaths.sorted() && sourcePaths.distinct() == sourcePaths)
        return ArtifactDescriptor(path, kind, size, digest, sourcePaths)
    }

    private fun parseDiagnostics(
        bytes: ByteArray,
        prepared: TypeScriptPreparedCompilation,
        expectedCount: Int,
    ): List<TypeScriptDiagnostic> {
        require(bytes.size.toLong() <= TypeScriptR2Contract.MAX_DIAGNOSTICS_DOCUMENT_BYTES)
        val root = TypeScriptStrictJson.parseObject(bytes)
        root.requireExactKeys(DIAGNOSTICS_DOCUMENT_KEYS, "diagnostics document")
        root.requireString("schema").requireEqual(TypeScriptR2Contract.DIAGNOSTICS_SCHEMA)
        root.requireInt("revision").requireEqual(TypeScriptR2Contract.DIAGNOSTICS_SCHEMA_REVISION)
        root.requireInt("diagnosticsQuotaRevision").requireEqual(
            TypeScriptR2Contract.DIAGNOSTICS_QUOTA_REVISION,
        )
        root.requireString("requestId").requireEqual(prepared.requestId)
        root.requireString("projectId").requireEqual(prepared.projectId)
        root.requireString("targetProfile").requireEqual(prepared.authority.targetProfile)
        root.requireInt("profileSchemaRevision").requireEqual(
            prepared.authority.profileSchemaRevision,
        )
        root.requireInt("profileRevision").requireEqual(prepared.authority.profileRevision)
        root.requireInt("configSchemaRevision").requireEqual(
            prepared.authority.configSchemaRevision,
        )
        root.requireString("configKind").requireEqual(prepared.authority.configKind)
        root.requireString("positionEncoding").requireEqual(
            TypeScriptR2Contract.DIAGNOSTICS_POSITION_ENCODING,
        )
        root.requireInt("lineBase").requireEqual(TypeScriptR2Contract.DIAGNOSTICS_LINE_BASE)
        root.requireInt("columnBase").requireEqual(TypeScriptR2Contract.DIAGNOSTICS_COLUMN_BASE)
        root.requireInt("diagnosticCount").requireEqual(expectedCount)
        val expectedRelatedCount = root.requireNonNegativeInt("relatedInformationCount")

        val diagnostics = root.requireObjectList("diagnostics").map { value ->
            value.requireExactKeys(DIAGNOSTIC_KEYS, "diagnostic")
            val related = value.requireObjectList("relatedInformation").map { item ->
                item.requireExactKeys(RELATED_INFORMATION_KEYS, "related information")
                TypeScriptRelatedInformation(
                    uri = item.requireNullableString("uri"),
                    range = item.requireRange("range"),
                    category = item.requireDiagnosticCategory("category"),
                    code = item.requirePositiveInt("code"),
                    message = item.requireDiagnosticMessage("message"),
                ).also(::requireLocationShape)
            }
            require(
                related.size <= TypeScriptR2Contract.MAX_RELATED_INFORMATION_PER_DIAGNOSTIC,
            )
            TypeScriptDiagnostic(
                phase = value.requireString("phase"),
                uri = value.requireNullableString("uri"),
                range = value.requireRange("range"),
                category = value.requireDiagnosticCategory("category"),
                code = value.requirePositiveInt("code"),
                message = value.requireDiagnosticMessage("message"),
                relatedInformation = related,
            ).also(::requireLocationShape)
        }
        require(diagnostics.size == expectedCount)
        require(diagnostics.size <= TypeScriptR2Contract.MAX_DIAGNOSTICS_COUNT)
        require(diagnostics.sumOf { it.relatedInformation.size } == expectedRelatedCount)
        require(expectedRelatedCount <= TypeScriptR2Contract.MAX_RELATED_INFORMATION_TOTAL)
        return diagnostics
    }

    private fun requireLocationShape(diagnostic: TypeScriptDiagnostic) {
        require(diagnostic.uri != null || diagnostic.range == null)
    }

    private fun requireLocationShape(related: TypeScriptRelatedInformation) {
        require(related.uri != null || related.range == null)
    }

    private fun Map<String, Any?>.requireRange(key: String): TypeScriptDiagnosticRange? {
        val value = getValue(key) ?: return null
        require(value is Map<*, *>)
        @Suppress("UNCHECKED_CAST")
        val range = value as Map<String, Any?>
        range.requireExactKeys(RANGE_KEYS, "diagnostic range")
        return TypeScriptDiagnosticRange(
            start = range.requireNonNegativeInt("start"),
            length = range.requireNonNegativeInt("length"),
            line = range.requirePositiveInt("line"),
            column = range.requirePositiveInt("column"),
        )
    }

    private fun Map<String, Any?>.requireDiagnosticCategory(key: String): Int =
        requireInt(key).also { category ->
            require(
                category in
                    TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_WARNING..
                    TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_MESSAGE,
            )
        }

    private fun Map<String, Any?>.requireDiagnosticMessage(key: String): String =
        requireString(key).also { message ->
            require(message.length <= TypeScriptR2Contract.MAX_DIAGNOSTIC_MESSAGE_CHARS)
        }

    private fun ZipFile.readBounded(entry: ZipEntry, maximumBytes: Long): ByteArray {
        require(entry.size in 0..maximumBytes)
        getInputStream(entry).use { input ->
            val output = ByteArrayOutputStream(entry.size.toInt().coerceAtLeast(32))
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total = Math.addExact(total, count.toLong())
                require(total <= maximumBytes)
                output.write(buffer, 0, count)
            }
            require(total == entry.size)
            return output.toByteArray()
        }
    }

    private fun isCanonicalArchivePath(path: String): Boolean {
        val encoded = runCatching { TypeScriptUtf8.encode(path, "archive path") }.getOrNull()
            ?: return false
        if (
            encoded.isEmpty() ||
            encoded.size > TypeScriptCompilerContract.MAX_ARCHIVE_ENTRY_NAME_BYTES ||
            path.startsWith('/') ||
            path.endsWith('/') ||
            '\\' in path
        ) {
            return false
        }
        val segments = path.split('/')
        return segments.size <= TypeScriptCompilerContract.MAX_ARCHIVE_PATH_DEPTH &&
            segments.all { segment ->
                segment.isNotEmpty() &&
                    segment != "." &&
                    segment != ".." &&
                    segment.none { character ->
                        character.code in 0x00..0x1f || character.code in 0x7f..0x9f
                    }
            }
    }

    private fun asciiCaseFold(value: String): String = buildString(value.length) {
        value.forEach { character ->
            append(if (character in 'A'..'Z') character + 32 else character)
        }
    }

    private fun Map<String, Any?>.requireExactKeys(expected: Set<String>, label: String) {
        require(keys == expected) { "$label has missing or unsupported fields." }
    }

    private fun Map<String, Any?>.requireString(key: String): String =
        getValue(key) as? String ?: error("TypeScript JSON field $key is not a string.")

    private fun Map<String, Any?>.requireNullableString(key: String): String? {
        val value = getValue(key) ?: return null
        return value as? String ?: error("TypeScript JSON field $key is not a nullable string.")
    }

    private fun Map<String, Any?>.requireBoolean(key: String): Boolean =
        getValue(key) as? Boolean ?: error("TypeScript JSON field $key is not a boolean.")

    private fun Map<String, Any?>.requireInt(key: String): Int =
        requireCanonicalLong(key).also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt()

    private fun Map<String, Any?>.requireNonNegativeInt(key: String): Int =
        requireInt(key).also { require(it >= 0) }

    private fun Map<String, Any?>.requirePositiveInt(key: String): Int =
        requireInt(key).also { require(it > 0) }

    private fun Map<String, Any?>.requireNonNegativeLong(key: String): Long =
        requireCanonicalLong(key).also { require(it >= 0L) }

    private fun Map<String, Any?>.requireCanonicalLong(key: String): Long {
        val token = getValue(key) as? TypeScriptStrictJson.NumberToken
            ?: error("TypeScript JSON field $key is not a number.")
        require(CANONICAL_INTEGER.matches(token.source))
        return token.source.toLong()
    }

    private fun Map<String, Any?>.requireLowerSha256(key: String): String =
        requireString(key).also { requireLowerSha256(it, key) }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.requireObjectList(key: String): List<Map<String, Any?>> {
        val values = getValue(key) as? List<*>
            ?: error("TypeScript JSON field $key is not an array.")
        return values.map { value ->
            require(value is Map<*, *>)
            value as Map<String, Any?>
        }
    }

    private fun Map<String, Any?>.requireStringList(key: String): List<String> {
        val values = getValue(key) as? List<*>
            ?: error("TypeScript JSON field $key is not an array.")
        return values.map { value ->
            value as? String ?: error("TypeScript JSON field $key has a non-string item.")
        }
    }

    private fun String.requireEqual(expected: String) {
        require(this == expected)
    }

    private fun Int.requireEqual(expected: Int) {
        require(this == expected)
    }

    private data class ArtifactDescriptor(
        val path: String,
        val kind: String,
        val size: Long,
        val sha256: String,
        val sourcePaths: List<String>,
    )

    private const val KIND_DIAGNOSTICS = "diagnostics"
    private const val KIND_JAVASCRIPT = "javascript"
    private const val KIND_SOURCE_MAP = "source_map"
    private const val COMPILER_API = "createIncrementalProgram"
    private const val GENERATED_MAPPING_SCHEMA =
        "autojs6.typescript.generated-module-mapping"
    private const val NODE_MODULE_OUTPUT_POLICY_FINGERPRINT =
        "5c2294f0ee9d6d25bd2132b4ea24866d0a5f9caaa521a63bf81ffe1d897f6ce7"
    private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()
    private val CANONICAL_INTEGER = Regex("""-?(0|[1-9][0-9]*)""")
    private val ARTIFACT_DESCRIPTOR_KEYS = setOf("path", "kind", "size", "sha256", "sourcePaths")
    private val DIAGNOSTIC_KEYS =
        setOf("phase", "uri", "range", "category", "code", "message", "relatedInformation")
    private val RELATED_INFORMATION_KEYS =
        setOf("uri", "range", "category", "code", "message")
    private val RANGE_KEYS = setOf("start", "length", "line", "column")
    private val GENERATED_MODULE_MAPPING_KEYS = setOf(
        "sourcePath",
        "projectNamespace",
        "originalSourceUri",
        "moduleFormat",
        "outputExtension",
        "generatedPath",
        "sourceMapPath",
        "generatedUri",
        "canonicalEvidence",
        "fingerprint",
    )
    private val DIAGNOSTICS_DOCUMENT_KEYS = setOf(
        "schema",
        "revision",
        "diagnosticsQuotaRevision",
        "requestId",
        "projectId",
        "targetProfile",
        "profileSchemaRevision",
        "profileRevision",
        "configSchemaRevision",
        "configKind",
        "positionEncoding",
        "lineBase",
        "columnBase",
        "diagnosticCount",
        "relatedInformationCount",
        "diagnostics",
    )
    private val OUTPUT_MANIFEST_KEYS = setOf(
        "schema",
        "revision",
        "outputQuotaRevision",
        "contractVersion",
        "contractHash",
        "outputArchiveTransportVersion",
        "requestId",
        "projectId",
        "entrySourceUri",
        "entryModulePath",
        "targetProfile",
        "profileSchemaRevision",
        "profileRevision",
        "configSchemaRevision",
        "configKind",
        "runtimeCapabilitySchemaRevision",
        "compilationMode",
        "compilerApi",
        "compilerPackage",
        "compilerVersion",
        "compilerDistributionFingerprint",
        "declarationsFingerprint",
        "runtimeCapabilitiesFingerprint",
        "inputArchiveSha256",
        "requestManifestSha256",
        "inputArchiveSchemaRevision",
        "inputQuotaRevision",
        "callerInventoryFingerprint",
        "retainedInventoryFingerprint",
        "declarationProfileFingerprint",
        "declarationInventoryFingerprint",
        "declarationFileCount",
        "declarationByteLength",
        "retainedFileCount",
        "retainedByteLength",
        "largestRetainedFileBytes",
        "outcome",
        "emitSkipped",
        "diagnosticCount",
        "emittedModuleCount",
        "diagnosticsPath",
        "effectiveOptions",
        "effectiveOptionsFingerprint",
        "moduleInventoryFingerprint",
        "artifactInventoryFingerprint",
        "files",
    )
    private val NODE_OUTPUT_MANIFEST_KEYS = OUTPUT_MANIFEST_KEYS + setOf(
        "nodeModuleOutputPolicySchemaRevision",
        "nodeModuleOutputPolicyRevision",
        "nodeModuleOutputPolicyFingerprint",
        "generatedMappingSchema",
        "generatedMappingSchemaRevision",
        "generatedMappingPolicyRevision",
        "resolverPolicyRevision",
        "snapshotTargetInventoryFingerprint",
        "packageMetadataInventoryFingerprint",
        "packageFormatCatalogFingerprint",
        "packageTargetInventoryFingerprint",
        "targetUniverseFingerprint",
        "resultSnapshotEntryPath",
        "resultSnapshotRootNames",
        "generatedModuleMappings",
    )
}
