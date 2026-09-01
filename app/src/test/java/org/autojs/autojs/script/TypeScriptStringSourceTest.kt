package org.autojs.autojs.script

import org.autojs.autojs.runtime.api.Engines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TypeScriptStringSourceTest {

    @Test
    fun enginesFactoryRoutesCompilerBackedNamesWithoutChangingJavaScript() {
        val typeScript = Engines.createExecScriptSource(
            "snippet.ts",
            "const answer: number = 42; answer",
        )
        val tsx = Engines.createExecScriptSource("component.tsx", "<text />")
        val javaScript = Engines.createExecScriptSource("snippet", "40 + 2")
        val bunJavaScript = Engines.createExecScriptSource("snippet.bun.js", "40 + 2")

        assertTrue(typeScript is TypeScriptStringSource)
        assertTrue(tsx is TypeScriptStringSource)
        assertTrue(javaScript is StringScriptSource)
        assertEquals(JavaScriptSource.ENGINE, typeScript.engineName)
        assertEquals(JavaScriptSource.ENGINE_BUN, bunJavaScript.engineName)
    }

    @Test
    fun semanticNameAndPrefixSurviveInMemoryRouting() {
        val source = TypeScriptStringSource("console.ts", "1 as number").apply {
            prefix = "\$console/"
        }

        assertEquals("console.ts", source.compilationSourceName)
        assertEquals("autojs://string/console.ts", source.compilationSourceIdentity)
        assertEquals("\$console/console.ts", source.toString())
        assertEquals("1 as number", source.script)
    }

    @Test
    fun extensionsSelectAProfileWithoutNeedingProjectMetadata() {
        val module = TypeScriptStringSource("module.mts", "export const value = 42")
        val bun = TypeScriptStringSource("module.bun.ts", "export const value = 42")
        val directed = TypeScriptStringSource(
            "directed.ts",
            "\"nodejs\"; const value: number = 42",
        )
        val bunDirected = TypeScriptStringSource(
            "directed.ts",
            "\"bun\"; const value: number = 42",
        )

        assertEquals(JavaScriptSource.ENGINE_NODEJS, module.engineName)
        assertEquals(JavaScriptSource.ENGINE_BUN, bun.engineName)
        assertEquals(JavaScriptSource.ENGINE_NODEJS, directed.engineName)
        assertEquals(JavaScriptSource.ENGINE_BUN, bunDirected.engineName)
    }

    @Test
    fun enginesFactoryPreservesBunTypeScriptMarker() {
        val typeScript = Engines.createExecScriptSource(
            "snippet.bun.ts",
            "const answer: number = 42; answer",
        )
        val tsx = Engines.createExecScriptSource("component.bun.tsx", "<text />")

        assertTrue(typeScript is TypeScriptStringSource)
        assertTrue(tsx is TypeScriptStringSource)
        assertEquals(JavaScriptSource.ENGINE_BUN, typeScript.engineName)
        assertEquals(JavaScriptSource.ENGINE_BUN, tsx.engineName)
    }

    @Test
    fun bunTypeScriptMarkerCombinesWithDirectivesAndFailsClosedOnEngineConflicts() {
        val uiSource = TypeScriptStringSource(
            "component.bun.tsx",
            "\"ui\"; const value: number = 42",
        )
        assertEquals(JavaScriptSource.ENGINE_BUN, uiSource.engineName)
        assertTrue(uiSource.executionMode and JavaScriptSource.EXECUTION_MODE_UI != 0)

        listOf("nodejs", "rhino").forEach { conflictingEngine ->
            val source = TypeScriptStringSource(
                "component.bun.ts",
                "\"$conflictingEngine\"; const value: number = 42",
            )
            assertThrows(IllegalArgumentException::class.java) {
                source.engineName
            }
        }
    }

    @Test
    fun originHintsCannotLoseOrConflictWithTheBunTypeScriptRoute() {
        val originSelected = TypeScriptStringSource("shared.ts", "const value: number = 42").apply {
            overriddenFullPath = File(System.getProperty("java.io.tmpdir"), "actual.bun.ts").path
        }
        assertEquals(JavaScriptSource.ENGINE_BUN, originSelected.engineName)

        val conflict = TypeScriptStringSource("shared.mts", "const value: number = 42").apply {
            overriddenFullPath = File(System.getProperty("java.io.tmpdir"), "actual.bun.ts").path
        }
        assertThrows(IllegalArgumentException::class.java) { conflict.engineName }
    }

    @Test
    fun declarationAndPathNamesAreRejectedBeforeExecution() {
        assertThrows(IllegalArgumentException::class.java) {
            Engines.createExecScriptSource("types.d.ts", "declare const value: number")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TypeScriptStringSource("nested/snippet.ts", "42")
        }
    }

}
