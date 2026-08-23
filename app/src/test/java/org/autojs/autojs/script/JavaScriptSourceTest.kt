package org.autojs.autojs.script

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JavaScriptSourceTest {

    @Test
    fun typeScriptDeclarationsAreNotExecutableScriptFiles() {
        assertTrue(JavaScriptSource.isTypeScriptDeclarationFileName("types.d.ts"))
        assertFalse(JavaScriptSource.isJavaScriptFileName("types.d.ts"))
        assertFalse(JavaScriptSource.isNodeJsFileName("types.d.ts"))

        assertTrue(JavaScriptSource.isJavaScriptFileName("main.ts"))
        assertTrue(JavaScriptSource.isRhinoTypeScriptFileName("main.ts"))
        assertFalse(JavaScriptSource.isNodeJsFileName("main.ts"))
        assertTrue(JavaScriptSource.isNodeJsFileName("main.mts"))
        assertFalse(JavaScriptSource.isRhinoTypeScriptFileName("main.mts"))
        assertTrue(JavaScriptSource.isCompilerBackedTypeScriptFileName("main.ts"))
        assertTrue(JavaScriptSource.isCompilerBackedTypeScriptFileName("main.mts"))
        assertTrue(JavaScriptSource.isCompilerBackedTypeScriptFileName("main.cts"))
        assertFalse(JavaScriptSource.isCompilerBackedTypeScriptFileName("component.tsx"))
        assertFalse(JavaScriptSource.isCompilerBackedTypeScriptFileName("types.d.mts"))
        assertTrue(JavaScriptSource.isUnsupportedSingleFileTypeScriptFileName("component.tsx"))
        assertTrue(JavaScriptSource.isUnsupportedSingleFileTypeScriptFileName("types.d.ts"))
        assertFalse(JavaScriptSource.isUnsupportedSingleFileTypeScriptFileName("main.ts"))
    }

    @Test
    fun ordinaryTypeScriptFileUsesCompilerBackedRhinoSource() {
        val sourceFile = File.createTempFile("autojs6-typescript-route-", ".ts")
        try {
            sourceFile.writeText("const value: number = 42")

            val launch = ScriptLaunchSourceFactory.createFileLaunch(
                file = sourceFile,
                defaultWorkingDirectory = sourceFile.parent.orEmpty(),
            )

            assertTrue(launch.source is TypeScriptFileSource)
            assertEquals(JavaScriptSource.ENGINE, launch.engineName)
            assertEquals(sourceFile.name, (launch.source as TypeScriptFileSource).compilationSourceName)
        } finally {
            sourceFile.delete()
        }
    }

    @Test
    fun semanticTypeScriptNameSurvivesEditorCacheRouting() {
        val sourceFile = File.createTempFile("autojs6-typescript-cache-", ".ts")
        try {
            sourceFile.writeText("toast('cache')")

            val launch = ScriptLaunchSourceFactory.createSemanticFileLaunch(
                sourceFile = sourceFile,
                semanticPath = File(sourceFile.parentFile, "你好.ts").path,
                semanticDisplayName = "你好.ts",
                defaultWorkingDirectory = sourceFile.parent.orEmpty(),
            )

            assertTrue(launch.source is TypeScriptFileSource)
            assertEquals("你好.ts", (launch.source as TypeScriptFileSource).compilationSourceName)
        } finally {
            sourceFile.delete()
        }
    }

    @Test
    fun nodeTypeScriptExtensionsAndDirectiveSelectNodeEngine() {
        val mts = File.createTempFile("autojs6-typescript-node-route-", ".mts")
        val directedTs = File.createTempFile("autojs6-typescript-node-directive-", ".ts")
        try {
            mts.writeText("const value: number = 42; export { value }")
            directedTs.writeText("\"nodejs\";\nconst value: number = 42")

            val mtsLaunch = ScriptLaunchSourceFactory.createFileLaunch(
                file = mts,
                defaultWorkingDirectory = mts.parent.orEmpty(),
            )
            val directedLaunch = ScriptLaunchSourceFactory.createFileLaunch(
                file = directedTs,
                defaultWorkingDirectory = directedTs.parent.orEmpty(),
            )

            assertEquals(JavaScriptSource.ENGINE_NODEJS, mtsLaunch.engineName)
            assertTrue((mtsLaunch.source as JavaScriptFileSource).isCompilerBackedTypeScriptSource)
            assertTrue(directedLaunch.source is TypeScriptFileSource)
        } finally {
            mts.delete()
            directedTs.delete()
        }
    }
}
