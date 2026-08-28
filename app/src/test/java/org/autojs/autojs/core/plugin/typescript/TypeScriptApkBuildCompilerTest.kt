package org.autojs.autojs.core.plugin.typescript

import java.nio.charset.StandardCharsets
import com.google.gson.JsonParser
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeScriptApkBuildCompilerTest {

    @Test
    fun createsReleaseOverlayWithoutRawSourcesOrInlineSourceContent() {
        val output = emitted(
            modules = listOf(module("main.ts", "main.js")),
            entryModulePath = "modules/main.js",
        )

        val plan = TypeScriptApkBuildCompiler.createPlan(
            output = output,
            mainScriptPath = "main.js",
            forceDirectorySource = false,
            singleFileTarget = null,
        )
        try {
            assertEquals(TypeScriptApkBuildCompiler.RHINO_LAUNCHER_PATH, plan.mainScriptPath)
            assertFalse(plan.requiresEncryptedStaging)
            assertEquals(
                listOf(
                    ".autojs6/typescript-compilation.json",
                    ".autojs6/typescript-launcher.js",
                    "main.js",
                    "main.js.map",
                ),
                plan.entries.map(TypeScriptApkBuildEntry::relativePath),
            )
            assertEquals(
                "\"use strict\";\nrequire(files.path(\"./main.js\"));\n",
                plan.text(TypeScriptApkBuildCompiler.RHINO_LAUNCHER_PATH),
            )
            val javaScript = plan.text("main.js")
            assertTrue(javaScript.contains("const answer = 42;"))
            assertTrue(javaScript.contains("//# sourceMappingURL=main.js.map"))
            assertFalse(javaScript.contains("old-name.js.map"))

            val sourceMap = JsonParser.parseString(plan.text("main.js.map")).asJsonObject
            assertEquals(3, sourceMap.get("version").asInt)
            assertEquals("main.js", sourceMap.get("file").asString)
            assertEquals("main.ts", sourceMap.getAsJsonArray("sources")[0].asString)
            assertFalse(sourceMap.has("sourcesContent"))

            val manifest = JsonParser.parseString(
                plan.text(".autojs6/typescript-compilation.json"),
            ).asJsonObject
            assertEquals("autojs6.typescript.apk-compilation", manifest.get("schema").asString)
            assertEquals("main.js", manifest.get("entryJavaScriptPath").asString)
            assertEquals(
                TypeScriptApkBuildCompiler.RHINO_LAUNCHER_PATH,
                manifest.get("launchScriptPath").asString,
            )
            assertEquals(COMPILER_VERSION, manifest.get("compilerVersion").asString)
            assertFalse(manifest.toString().contains("autojs://private-project/main.ts"))
            assertFalse(manifest.toString().contains("main.ts"))
        } finally {
            val retainedBytes = plan.entries.map { entry -> entry.bytes }
            plan.close()
            assertTrue(retainedBytes.all { bytes -> bytes.all { it == 0.toByte() } })
        }
    }

    @Test
    fun singleFileOverlayRenamesJavaScriptAndSourceMapTogether() {
        val output = emitted(
            modules = listOf(module("原始.tsx", "原始.js")),
            entryModulePath = "modules/原始.js",
        )

        val plan = TypeScriptApkBuildCompiler.createPlan(
            output = output,
            mainScriptPath = "main.js",
            forceDirectorySource = true,
            singleFileTarget = "main.js",
        )
        try {
            assertTrue(plan.forceDirectorySource)
            assertEquals(TypeScriptApkBuildCompiler.RHINO_LAUNCHER_PATH, plan.mainScriptPath)
            assertTrue(TypeScriptApkBuildCompiler.RHINO_LAUNCHER_PATH in plan.generatedPaths)
            assertTrue("main.js" in plan.generatedPaths)
            assertTrue("main.js.map" in plan.generatedPaths)
            assertFalse("原始.js" in plan.generatedPaths)
            assertEquals(
                "main.js",
                JsonParser.parseString(plan.text("main.js.map")).asJsonObject
                    .get("file").asString,
            )
        } finally {
            plan.close()
        }
    }

    @Test
    fun carriesEncryptedSourcePolicyIntoTheInMemoryOverlay() {
        val plan = TypeScriptApkBuildCompiler.createPlan(
            output = emitted(
                modules = listOf(module("main.ts", "main.js")),
                entryModulePath = "modules/main.js",
            ),
            mainScriptPath = "main.js",
            forceDirectorySource = false,
            singleFileTarget = null,
            requiresEncryptedStaging = true,
        )
        try {
            assertTrue(plan.requiresEncryptedStaging)
            assertTrue("main.js" in plan.generatedPaths)
        } finally {
            plan.close()
        }
    }

    @Test
    fun omitsOnlyCompilerInputsAndOriginalProjectAuthority() {
        val plan = TypeScriptApkBuildPlan(
            mainScriptPath = "main.js",
            forceDirectorySource = false,
            entries = listOf(TypeScriptApkBuildEntry("main.js", byteArrayOf(1))),
        )
        try {
            listOf(
                "main.ts",
                "ui/main.tsx",
                "types/api.d.ts",
                "types/api.d.mts",
                "module.mts",
                "module.cts",
                "tsconfig.json",
                "project.json",
            ).forEach { path -> assertTrue(path, plan.omitOriginal(path)) }
            listOf(
                "main.js",
                "module.cjs",
                "data.json",
                "nested/project.json",
                "README.md",
            ).forEach { path -> assertFalse(path, plan.omitOriginal(path)) }
            assertTrue(TypeScriptApkBuildCompiler.isTypeScriptPath("MAIN.TSX"))
            assertTrue(TypeScriptApkBuildCompiler.isTypeScriptPath("module.mts"))
            assertFalse(TypeScriptApkBuildCompiler.isTypeScriptPath("main.js"))
        } finally {
            plan.close()
        }
    }

    private fun emitted(
        modules: List<TypeScriptEmittedModule>,
        entryModulePath: String,
    ): TypeScriptCompilationOutput.Emitted = TypeScriptCompilationOutput.Emitted(
        modules = modules,
        entryModulePath = entryModulePath,
        projectSources = modules.map(TypeScriptEmittedModule::source),
        packageMetadata = null,
        dependencyLayer = null,
        projectId = "apk-build-test",
        authority = authority(),
        configByteLength = 2L,
        configSha256 = sha256("{}".toByteArray(StandardCharsets.UTF_8)),
        diagnostics = emptyList(),
    )

    private fun module(
        sourcePath: String,
        generatedPath: String,
    ): TypeScriptEmittedModule {
        val source = TypeScriptPreparedSource(
            archivePath = "sources/$sourcePath",
            projectRelativePath = sourcePath,
            originalFile = null,
            sha256 = "11".repeat(32),
        )
        return TypeScriptEmittedModule(
            sourcePaths = listOf(source.archivePath),
            source = source,
            javaScriptPath = "modules/$generatedPath",
            javaScript = "const answer = 42;\n//# sourceMappingURL=old-name.js.map\n",
            sourceMapPath = "maps/$generatedPath.map",
            sourceMap = """{"version":3,"file":"${generatedPath.substringAfterLast('/')}","sourceRoot":"","sources":["autojs://private-project/$sourcePath"],"sourcesContent":["const secret = 42;"],"names":[],"mappings":"AAAA"}""",
            generatedMapping = TypeScriptGeneratedModuleMapping(
                sourcePath = source.archivePath,
                originalSourceUri = "autojs://private-project/$sourcePath",
                moduleFormat = "commonjs",
                outputExtension = ".js",
                generatedPath = "/outputs/$generatedPath",
                sourceMapPath = "/outputs/$generatedPath.map",
                generatedUri = "autojs://private-project/outputs/$generatedPath",
            ),
        )
    }

    private fun TypeScriptApkBuildPlan.text(path: String): String = entries
        .single { entry -> entry.relativePath == path }
        .bytes
        .toString(StandardCharsets.UTF_8)

    private fun authority() = TypeScriptCompilerAuthority(
        targetProfile = TypeScriptCompilerContract.PROFILE_RHINO,
        compilerVersion = COMPILER_VERSION,
        compilerDistributionFingerprint = "22".repeat(32),
        declarationsFingerprint = "33".repeat(32),
        declarationProfileFingerprint = "44".repeat(32),
        runtimeCapabilitiesFingerprint = "55".repeat(32),
        profileSchemaRevision = TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
        profileRevision = 2,
        configSchemaRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
        configKind = TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
        configPolicyRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION,
        configPolicyFingerprint = "66".repeat(32),
    )

    private companion object {
        const val COMPILER_VERSION = "6.0.3"
    }
}
