package org.autojs.autojs.core.plugin.typescript

import java.io.File
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.ScriptLaunchSourceFactory
import org.autojs.autojs.script.SequenceScriptSource
import org.autojs.autojs.script.TypeScriptFileSource
import org.autojs.autojs.script.TypeScriptStringSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TypeScriptBackgroundPreparationRouteTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun inMemoryTypeScriptUsesTheSameBackgroundPreparationRoute() {
        val source = TypeScriptStringSource(
            "inline.ts",
            "const answer: number = 42; answer",
        )

        assertTrue(TypeScriptExecutionCompiler.requiresPreparation(source))
        assertEquals(JavaScriptSource.ENGINE, source.engineName)
    }

    @Test
    fun bunTypeScriptSkipsHostPreparationForMemoryFileAndSequenceSources() {
        val inMemory = TypeScriptStringSource(
            "inline.bun.ts",
            "const answer: number = 42; answer",
        )
        val file = temporaryFolder.newFile("entry.bun.ts").apply {
            writeText("export const answer: number = 42;\n")
        }
        val fileSource = org.autojs.autojs.script.NodeProjectScriptSourceFactory.create(file)
        val sequence = SequenceScriptSource(
            fileSource.name,
            org.autojs.autojs.script.StringScriptSource("globalThis.beforeBun = true;\n"),
            fileSource,
        )

        assertEquals(JavaScriptSource.ENGINE_BUN, inMemory.engineName)
        assertEquals(JavaScriptSource.ENGINE_BUN, fileSource.engineName)
        assertTrue(fileSource is TypeScriptFileSource)
        assertEquals(file.canonicalPath, fileSource.toString())
        assertFalse(TypeScriptExecutionCompiler.requiresPreparation(inMemory))
        assertFalse(TypeScriptExecutionCompiler.requiresPreparation(fileSource))
        assertFalse(TypeScriptExecutionCompiler.requiresPreparation(sequence))
    }

    @Test
    fun bunPreExecutePrefixCannotBeCompiledAwayBeforeOrdinaryTypeScriptRouting() {
        val sequence = SequenceScriptSource(
            "entry.ts",
            org.autojs.autojs.script.StringScriptSource("\"bun\";"),
            TypeScriptStringSource("entry.ts", "const answer: number = 42"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            TypeScriptExecutionCompiler.requiresPreparation(sequence)
        }
    }

    @Test
    fun taskerPrefixRetainsTheCompilerBackedFileRoute() {
        val typeScript = temporaryFolder.newFile("tasker.ts").apply {
            writeText("const value: number = 42;\n")
        }
        val direct = ScriptLaunchSourceFactory.createPathLaunch(
            path = typeScript.path,
            workingDirectory = temporaryFolder.root.path,
            defaultWorkingDirectory = temporaryFolder.root.path,
        )
        val prefixed = ScriptLaunchSourceFactory.createPathLaunch(
            path = typeScript.path,
            preExecuteScript = "globalThis.beforeTypeScript = true;",
            workingDirectory = temporaryFolder.root.path,
            defaultWorkingDirectory = temporaryFolder.root.path,
        )

        assertTrue(direct.source is TypeScriptFileSource)
        assertEquals(typeScript.canonicalPath, direct.source.toString())
        assertEquals(typeScript.canonicalPath, prefixed.source.toString())
        assertTrue(TypeScriptExecutionCompiler.requiresPreparation(direct.source))
        assertTrue(prefixed.source is SequenceScriptSource)
        val sequence = prefixed.source as SequenceScriptSource
        assertEquals("globalThis.beforeTypeScript = true;", sequence.firstScriptSource.script)
        assertTrue(sequence.secondScriptSource is TypeScriptFileSource)
        assertTrue(TypeScriptExecutionCompiler.requiresPreparation(sequence))

        val javaScript = temporaryFolder.newFile("legacy.js")
        val legacy = ScriptLaunchSourceFactory.createPathLaunch(
            path = javaScript.path,
            preExecuteScript = "globalThis.beforeJavaScript = true;",
            workingDirectory = temporaryFolder.root.path,
            defaultWorkingDirectory = temporaryFolder.root.path,
        )
        assertTrue(legacy.source is SequenceScriptSource)
        assertFalse(TypeScriptExecutionCompiler.requiresPreparation(legacy.source))
    }

    @Test
    fun prefixedNodeTypeScriptKeepsItsNodeCompilerProfile() {
        val source = temporaryFolder.newFile("background.mts").apply {
            writeText("export const value: number = 42;\n")
        }
        val launch = ScriptLaunchSourceFactory.createPathLaunch(
            path = source.path,
            preExecuteScript = "globalThis.beforeNodeTypeScript = true;",
            workingDirectory = temporaryFolder.root.path,
            defaultWorkingDirectory = temporaryFolder.root.path,
        )
        val sequence = launch.source as SequenceScriptSource
        val fileSource = sequence.secondScriptSource as JavaScriptFileSource

        assertEquals(JavaScriptSource.ENGINE_NODEJS, fileSource.engineName)
        assertTrue(fileSource.isCompilerBackedTypeScriptSource)
        assertTrue(TypeScriptExecutionCompiler.requiresPreparation(sequence))
    }

    @Test
    fun preExecutePrefixHasAStableLineBoundaryForSourceMaps() {
        val originalFile = temporaryFolder.newFile("mapped.ts")
        val preparedSource = TypeScriptPreparedSource(
            archivePath = "sources/mapped.ts",
            projectRelativePath = "mapped.ts",
            originalFile = originalFile,
            sha256 = "1".repeat(64),
        )
        val sourceMap =
            """{"version":3,"file":"mapped.js","sourceRoot":"","sources":["mapped.ts"],"names":[],"mappings":"AAAA"}"""
        val generatedSourceName = "mapped.ts.generated.js"
        val module = TypeScriptEmittedModule(
            sourcePaths = listOf("sources/mapped.ts"),
            source = preparedSource,
            javaScriptPath = "modules/mapped.js",
            javaScript = "throw new Error('mapped');\n",
            sourceMapPath = "modules/mapped.js.map",
            sourceMap = sourceMap,
            generatedMapping = null,
        )
        val executable = TypeScriptExecutableSource(
            sourceName = "mapped.ts",
            targetProfile = org.autojs.plugin.typescript.api.TypeScriptCompilerContract.PROFILE_RHINO,
            javaScript = module.javaScript,
            sourceMapBinding = TypeScriptSourceMapBinding.parse(
                sourceMapText = sourceMap,
                generatedSourceName = generatedSourceName,
                expectedGeneratedFileName = "mapped.js",
                originalSourceName = "mapped.ts",
            ),
            generatedSourceName = generatedSourceName,
            diagnostics = emptyList(),
            modules = listOf(module),
            entryModulePath = module.javaScriptPath,
            projectRoot = null,
            projectSources = listOf(preparedSource),
            packageMetadata = null,
            dependencyLayer = null,
        )
        val compiled = CompiledTypeScriptSource(
            originalSource = TypeScriptFileSource(originalFile),
            executable = executable,
            preExecuteScript = "const first = 1;\nconst second = 2;",
        )

        assertEquals(2, compiled.entryGeneratedLineOffset)
        assertTrue(compiled.script.startsWith("const first = 1;\nconst second = 2;\nthrow"))
        val bindings = executable.sourceMapBindings(
            generatedSourceNames = mapOf(module.javaScriptPath to generatedSourceName),
            entryGeneratedLineOffset = compiled.entryGeneratedLineOffset,
        )
        val original = bindings.originalPositionFor(
            frameSourceName = generatedSourceName,
            generatedLineNumber = 3,
            generatedColumnIndex = 0,
        )
        assertEquals(originalFile.path, original?.sourceName)
        assertEquals(1, original?.lineNumber)
        assertEquals(1, original?.columnNumber)
    }
}
