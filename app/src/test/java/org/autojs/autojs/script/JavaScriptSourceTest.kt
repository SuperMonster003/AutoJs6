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
}
