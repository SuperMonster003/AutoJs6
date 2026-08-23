package org.autojs.autojs.core.plugin.typescript

import java.io.File
import java.nio.charset.StandardCharsets
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptPluginIds
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeScriptCompilationOutputReaderTest {

    @Test
    fun readsDigestBoundSingleFileEmission() {
        val input = File.createTempFile("typescript-output-input-", ".zip")
        val output = File.createTempFile("typescript-output-test-", ".zip")
        try {
            val prepared = preparedInput(input)
            val javaScript = "toast(42);\n//# sourceMappingURL=main.js.map\n"
                .toByteArray(StandardCharsets.UTF_8)
            val sourceMap = (
                "{\"version\":3,\"file\":\"main.js\",\"sources\":[\"../sources/main.ts\"]," +
                    "\"names\":[],\"mappings\":\"AAAA\"}"
                ).toByteArray(StandardCharsets.UTF_8)
            val diagnostics = diagnosticsJson(prepared, emptyList())
            val artifacts = listOf(
                artifact("diagnostics.json", "diagnostics", diagnostics, emptyList()),
                artifact(
                    "maps/main.js.map",
                    "source_map",
                    sourceMap,
                    listOf(prepared.entryPath),
                ),
                artifact(
                    "modules/main.js",
                    "javascript",
                    javaScript,
                    listOf(prepared.entryPath),
                ),
            )
            val manifest = outputManifest(
                prepared = prepared,
                outcome = TypeScriptCompilerContract.OUTCOME_EMITTED,
                emitSkipped = false,
                diagnosticCount = 0,
                emittedModuleCount = 1,
                entryModulePath = "modules/main.js",
                artifacts = artifacts,
            )
            StoredZipV1Writer.write(
                output,
                linkedMapOf(
                    "compilation-manifest.json" to manifest,
                    "diagnostics.json" to diagnostics,
                    "maps/main.js.map" to sourceMap,
                    "modules/main.js" to javaScript,
                ),
            )
            val terminal = TypeScriptTerminalResult(
                requestId = prepared.requestId,
                outcome = TypeScriptCompilerContract.OUTCOME_EMITTED,
                succeeded = true,
                errorCode = null,
                errorMessage = null,
                diagnosticCount = 0,
                emittedModuleCount = 1,
                outputManifestSha256 = sha256(manifest),
            )

            val result = TypeScriptCompilationOutputReader.read(output, prepared, terminal)
            assertTrue(result is TypeScriptCompilationOutput.Emitted)
            result as TypeScriptCompilationOutput.Emitted
            assertEquals(javaScript.toString(StandardCharsets.UTF_8), result.javaScript)
            assertEquals(sourceMap.toString(StandardCharsets.UTF_8), result.sourceMap)
            assertEquals("modules/main.js", result.entryModulePath)
            assertTrue(result.diagnostics.isEmpty())

            val tamperedTerminal = terminal.copy(outputManifestSha256 = "0".repeat(64))
            assertTrue(
                runCatching {
                    TypeScriptCompilationOutputReader.read(output, prepared, tamperedTerminal)
                }.isFailure,
            )
        } finally {
            input.delete()
            output.delete()
        }
    }

    @Test
    fun readsDiagnosticsOnlyOutcomeWithoutExecutableArtifact() {
        val input = File.createTempFile("typescript-output-input-", ".zip")
        val output = File.createTempFile("typescript-output-test-", ".zip")
        try {
            val prepared = preparedInput(input)
            val diagnostic = objectJson(
                "phase" to quoted("semantic"),
                "uri" to quoted("autojs://source/test/sources/main.ts"),
                "range" to objectJson(
                    "start" to "0",
                    "length" to "5",
                    "line" to "1",
                    "column" to "1",
                ),
                "category" to TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_ERROR.toString(),
                "code" to "2322",
                "message" to quoted("Type 'string' is not assignable to type 'number'."),
                "relatedInformation" to "[]",
            )
            val diagnostics = diagnosticsJson(prepared, listOf(diagnostic))
            val artifacts = listOf(
                artifact(
                    "diagnostics.json",
                    "diagnostics",
                    diagnostics,
                    listOf(prepared.entryPath),
                ),
            )
            val manifest = outputManifest(
                prepared = prepared,
                outcome = TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS,
                emitSkipped = true,
                diagnosticCount = 1,
                emittedModuleCount = 0,
                entryModulePath = null,
                artifacts = artifacts,
            )
            StoredZipV1Writer.write(
                output,
                linkedMapOf(
                    "compilation-manifest.json" to manifest,
                    "diagnostics.json" to diagnostics,
                ),
            )
            val result = TypeScriptCompilationOutputReader.read(
                outputFile = output,
                prepared = prepared,
                terminal = TypeScriptTerminalResult(
                    requestId = prepared.requestId,
                    outcome = TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS,
                    succeeded = false,
                    errorCode = TypeScriptCompilerContract.ERROR_COMPILATION_DIAGNOSTICS,
                    errorMessage = "Compilation produced diagnostics.",
                    diagnosticCount = 1,
                    emittedModuleCount = 0,
                    outputManifestSha256 = sha256(manifest),
                ),
            )
            assertTrue(result is TypeScriptCompilationOutput.Diagnostics)
            assertEquals(2322, result.diagnostics.single().code)
            assertEquals(1, result.diagnostics.single().range?.line)
        } finally {
            input.delete()
            output.delete()
        }
    }

    private fun preparedInput(destination: File): TypeScriptPreparedCompilation =
        TypeScriptSingleFileArchive.write(
            sourceName = "main.ts",
            sourceBytes = "const value: number = 42;\n".toByteArray(StandardCharsets.UTF_8),
            requestId = "host-output-test",
            projectId = "single-file-output-test",
            entrySourceUri = "autojs://source/single-file-output-test/main.ts",
            authority = authority(),
            destination = destination,
        )

    private fun diagnosticsJson(
        prepared: TypeScriptPreparedCompilation,
        diagnostics: List<String>,
    ): ByteArray = objectJson(
        "schema" to quoted(TypeScriptR2Contract.DIAGNOSTICS_SCHEMA),
        "revision" to TypeScriptR2Contract.DIAGNOSTICS_SCHEMA_REVISION.toString(),
        "diagnosticsQuotaRevision" to
            TypeScriptR2Contract.DIAGNOSTICS_QUOTA_REVISION.toString(),
        "requestId" to quoted(prepared.requestId),
        "projectId" to quoted(prepared.projectId),
        "targetProfile" to quoted(TypeScriptCompilerContract.PROFILE_RHINO),
        "profileSchemaRevision" to prepared.authority.profileSchemaRevision.toString(),
        "profileRevision" to prepared.authority.profileRevision.toString(),
        "configSchemaRevision" to prepared.authority.configSchemaRevision.toString(),
        "configKind" to quoted(prepared.authority.configKind),
        "positionEncoding" to quoted(TypeScriptR2Contract.DIAGNOSTICS_POSITION_ENCODING),
        "lineBase" to TypeScriptR2Contract.DIAGNOSTICS_LINE_BASE.toString(),
        "columnBase" to TypeScriptR2Contract.DIAGNOSTICS_COLUMN_BASE.toString(),
        "diagnosticCount" to diagnostics.size.toString(),
        "relatedInformationCount" to "0",
        "diagnostics" to diagnostics.joinToString(prefix = "[", postfix = "]"),
    ).toByteArray(StandardCharsets.UTF_8)

    private fun outputManifest(
        prepared: TypeScriptPreparedCompilation,
        outcome: String,
        emitSkipped: Boolean,
        diagnosticCount: Int,
        emittedModuleCount: Int,
        entryModulePath: String?,
        artifacts: List<Artifact>,
    ): ByteArray = objectJson(
        "schema" to quoted(TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA),
        "revision" to TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA_REVISION.toString(),
        "outputQuotaRevision" to
            TypeScriptR2Contract.ORDINARY_COMPILATION_OUTPUT_QUOTA_REVISION.toString(),
        "contractVersion" to TypeScriptCompilerContract.VERSION.toString(),
        "contractHash" to quoted(TypeScriptCompilerContract.HASH),
        "outputArchiveTransportVersion" to
            TypeScriptCompilerContract.OUTPUT_ARCHIVE_TRANSPORT_VERSION.toString(),
        "requestId" to quoted(prepared.requestId),
        "projectId" to quoted(prepared.projectId),
        "entrySourceUri" to quoted(prepared.entrySourceUri),
        "entryModulePath" to (entryModulePath?.let(::quoted) ?: "null"),
        "targetProfile" to quoted(TypeScriptCompilerContract.PROFILE_RHINO),
        "profileSchemaRevision" to prepared.authority.profileSchemaRevision.toString(),
        "profileRevision" to prepared.authority.profileRevision.toString(),
        "configSchemaRevision" to prepared.authority.configSchemaRevision.toString(),
        "configKind" to quoted(prepared.authority.configKind),
        "runtimeCapabilitySchemaRevision" to
            TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION.toString(),
        "compilationMode" to quoted(TypeScriptCompilerContract.MODE_DEVELOPMENT),
        "compilerApi" to quoted("createIncrementalProgram"),
        "compilerPackage" to quoted(TypeScriptPluginIds.ID),
        "compilerVersion" to quoted(prepared.authority.compilerVersion),
        "compilerDistributionFingerprint" to
            quoted(prepared.authority.compilerDistributionFingerprint),
        "declarationsFingerprint" to quoted(prepared.authority.declarationsFingerprint),
        "runtimeCapabilitiesFingerprint" to
            quoted(prepared.authority.runtimeCapabilitiesFingerprint),
        "inputArchiveSha256" to quoted(prepared.inputArchiveSha256),
        "requestManifestSha256" to quoted(prepared.requestManifestSha256),
        "inputArchiveSchemaRevision" to
            TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION.toString(),
        "inputQuotaRevision" to
            TypeScriptR2Contract.ORDINARY_COMPILATION_INPUT_QUOTA_REVISION.toString(),
        "callerInventoryFingerprint" to quoted("6".repeat(64)),
        "retainedInventoryFingerprint" to quoted("7".repeat(64)),
        "declarationProfileFingerprint" to
            quoted(prepared.authority.declarationProfileFingerprint),
        "declarationInventoryFingerprint" to quoted("8".repeat(64)),
        "declarationFileCount" to "1",
        "declarationByteLength" to "1",
        "retainedFileCount" to "2",
        "retainedByteLength" to "2",
        "largestRetainedFileBytes" to "1",
        "outcome" to quoted(outcome),
        "emitSkipped" to emitSkipped.toString(),
        "diagnosticCount" to diagnosticCount.toString(),
        "emittedModuleCount" to emittedModuleCount.toString(),
        "diagnosticsPath" to quoted(TypeScriptR2Contract.OUTPUT_ARCHIVE_DIAGNOSTICS_PATH),
        "effectiveOptions" to "{}",
        "effectiveOptionsFingerprint" to quoted("9".repeat(64)),
        "moduleInventoryFingerprint" to quoted("a".repeat(64)),
        "artifactInventoryFingerprint" to quoted("b".repeat(64)),
        "files" to artifacts.joinToString(
            prefix = "[",
            postfix = "]",
            transform = ::artifactJson,
        ),
    ).toByteArray(StandardCharsets.UTF_8)

    private fun artifactJson(artifact: Artifact): String = objectJson(
        "path" to quoted(artifact.path),
        "kind" to quoted(artifact.kind),
        "size" to artifact.size.toString(),
        "sha256" to quoted(artifact.sha256),
        "sourcePaths" to artifact.sourcePaths.joinToString(
            prefix = "[",
            postfix = "]",
            transform = ::quoted,
        ),
    )

    private fun artifact(
        path: String,
        kind: String,
        bytes: ByteArray,
        sourcePaths: List<String>,
    ) = Artifact(path, kind, bytes.size.toLong(), sha256(bytes), sourcePaths)

    private fun authority() = TypeScriptRhinoCompilerAuthority(
        compilerVersion = "6.0.3",
        compilerDistributionFingerprint = "1".repeat(64),
        declarationsFingerprint = "2".repeat(64),
        declarationProfileFingerprint = "3".repeat(64),
        runtimeCapabilitiesFingerprint = "4".repeat(64),
        profileSchemaRevision = TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
        profileRevision = 1,
        configSchemaRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
        configKind = TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
        configPolicyRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION,
        configPolicyFingerprint = "5".repeat(64),
    )

    private fun objectJson(vararg fields: Pair<String, String>): String =
        fields.joinToString(prefix = "{", postfix = "}") { (name, value) ->
            quoted(name) + ":" + value
        }

    private fun quoted(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }

    private data class Artifact(
        val path: String,
        val kind: String,
        val size: Long,
        val sha256: String,
        val sourcePaths: List<String>,
    )
}
