package org.autojs.autojs.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

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
        assertTrue(JavaScriptSource.isCompilerBackedTypeScriptFileName("component.tsx"))
        assertTrue(JavaScriptSource.isRhinoTypeScriptFileName("component.tsx"))
        assertFalse(JavaScriptSource.isNodeJsFileName("component.tsx"))
        assertFalse(JavaScriptSource.isCompilerBackedTypeScriptFileName("types.d.mts"))
        assertFalse(JavaScriptSource.isUnsupportedSingleFileTypeScriptFileName("component.tsx"))
        assertTrue(JavaScriptSource.isUnsupportedSingleFileTypeScriptFileName("types.d.ts"))
        assertFalse(JavaScriptSource.isUnsupportedSingleFileTypeScriptFileName("main.ts"))

        assertTrue(JavaScriptSource.isBunFileName("main.bun.js"))
        assertTrue(JavaScriptSource.isBunFileName("main.BUN.TS"))
        assertTrue(JavaScriptSource.isBunFileName("component.bun.tsx"))
        assertFalse(JavaScriptSource.isBunFileName("types.bun.d.ts"))
        assertFalse(JavaScriptSource.isBunFileName("main.bun.jsx"))
        assertFalse(JavaScriptSource.isBunFileName(null))
    }

    @Test
    fun bunDirectiveAndMarkersSelectTheIndependentEngineWithoutChangingLegacyRoutes() {
        val directed = StringScriptSource("\"BuN\";\nconsole.log('bun')")

        assertTrue(
            directed.executionMode and JavaScriptSource.EXECUTION_MODE_BUN != 0,
        )
        assertEquals(JavaScriptSource.ENGINE_BUN, directed.engineName)
        assertEquals(
            JavaScriptSource.ENGINE_BUN,
            JavaScriptSource.resolveEngineName(JavaScriptSource.EXECUTION_MODE_BUN),
        )
        assertEquals(
            JavaScriptSource.ENGINE_NODEJS,
            JavaScriptSource.resolveEngineName(JavaScriptSource.EXECUTION_MODE_NODEJS),
        )
        assertEquals(
            JavaScriptSource.ENGINE,
            JavaScriptSource.resolveEngineName(JavaScriptSource.EXECUTION_MODE_RHINO),
        )
        assertEquals(JavaScriptSource.ENGINE_NODEJS, StringScriptSource("\"nodejs\";\n42").engineName)
        assertEquals(JavaScriptSource.ENGINE, StringScriptSource("\"rhino\";\n42").engineName)
        assertEquals(
            JavaScriptSource.ENGINE_BUN,
            StringScriptSource("programmatic.bun.js", "console.log('bun')").engineName,
        )
        assertEquals(
            JavaScriptSource.ENGINE_BUN,
            StringScriptSource("shared.bun.js", "console.log('bun')").apply {
                overriddenFullPath = File(System.getProperty("java.io.tmpdir"), "provider-cache.js").path
            }.engineName,
        )

        listOf("entry.bun.js", "entry.bun.ts", "entry.bun.tsx").forEach { fileName ->
            val sourceFile = File.createTempFile(
                "autojs6-bun-route-",
                fileName.removePrefix("entry"),
            )
            try {
                sourceFile.writeText("console.log('bun')")
                val launch = ScriptLaunchSourceFactory.createFileLaunch(
                    file = sourceFile,
                    defaultWorkingDirectory = sourceFile.parent.orEmpty(),
                )

                assertEquals(JavaScriptSource.ENGINE_BUN, launch.engineName)
            } finally {
                sourceFile.delete()
            }
        }
    }

    @Test
    fun conflictingBunEngineSelectorsFailClosed() {
        listOf(
            JavaScriptSource.EXECUTION_MODE_BUN or JavaScriptSource.EXECUTION_MODE_NODEJS,
            JavaScriptSource.EXECUTION_MODE_BUN or JavaScriptSource.EXECUTION_MODE_RHINO,
        ).forEach { mode ->
            assertThrows(IllegalArgumentException::class.java) {
                JavaScriptSource.resolveEngineName(mode)
            }
        }

        val conflictingDirective = StringScriptSource("\"bun,nodejs\";\n42")
        assertThrows(IllegalArgumentException::class.java) {
            conflictingDirective.engineName
        }

        val bunMarkerWithNodeDirective = File.createTempFile(
            "autojs6-bun-conflict-",
            ".bun.js",
        )
        val nodeMarkerWithBunDirective = File.createTempFile(
            "autojs6-bun-conflict-",
            ".node.js",
        )
        try {
            bunMarkerWithNodeDirective.writeText("\"nodejs\";\n42")
            nodeMarkerWithBunDirective.writeText("\"bun\";\n42")

            listOf(bunMarkerWithNodeDirective, nodeMarkerWithBunDirective).forEach { file ->
                val source = JavaScriptFileSource(file)
                assertThrows(IllegalArgumentException::class.java) {
                    source.engineName
                }
            }
        } finally {
            bunMarkerWithNodeDirective.delete()
            nodeMarkerWithBunDirective.delete()
        }
    }

    @Test
    fun encryptedHeadersStillMergeSemanticBunSuffixesAndRejectEngineConflicts() {
        val bunFile = File.createTempFile("autojs6-bun-encrypted-", ".bun.js")
        val conflictFile = File.createTempFile("autojs6-bun-encrypted-conflict-", ".bun.ts")
        try {
            FileOutputStream(bunFile).use { output ->
                EncryptedScriptFileHeader.writeHeader(output)
                output.write(byteArrayOf(1, 2, 3))
            }
            FileOutputStream(conflictFile).use { output ->
                EncryptedScriptFileHeader.writeHeader(
                    output,
                    EncryptedScriptFileHeader.FLAG_EXECUTION_MODE_NODEJS,
                )
                output.write(byteArrayOf(1, 2, 3))
            }

            assertEquals(JavaScriptSource.ENGINE_BUN, JavaScriptFileSource(bunFile).engineName)
            assertThrows(IllegalArgumentException::class.java) {
                JavaScriptFileSource(conflictFile).engineName
            }
        } finally {
            bunFile.delete()
            conflictFile.delete()
        }
    }

    @Test
    fun nodeProjectLaunchRejectsBunEntriesInsteadOfForcingNode() {
        val root = Files.createTempDirectory("autojs6-bun-node-project-").toFile()
        val bunEntry = File(root, "main.bun.js").apply { writeText("console.log('bun')") }
        try {
            val source = NodeProjectJavaScriptFileSource(root, bunEntry)
            val error = assertThrows(IllegalArgumentException::class.java) { source.engineName }
            assertTrue(error.message.orEmpty().contains("Bun"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun bunLaunchRejectsJavaScriptPreExecuteInsteadOfChangingEngines() {
        val sourceFile = File.createTempFile("autojs6-bun-prefix-", ".bun.ts")
        try {
            sourceFile.writeText("const value: number = 42")

            val error = assertThrows(IllegalArgumentException::class.java) {
                ScriptLaunchSourceFactory.createPathLaunch(
                    path = sourceFile.path,
                    preExecuteScript = "globalThis.beforeBun = true;",
                    defaultWorkingDirectory = sourceFile.parent.orEmpty(),
                )
            }

            assertTrue(error.message.orEmpty().contains("Bun"))
            assertTrue(error.message.orEmpty().contains("preExecuteScript"))
        } finally {
            sourceFile.delete()
        }
    }

    @Test
    fun bunDirectiveInsidePreExecuteScriptCannotRerouteAnOrdinaryEntry() {
        val sourceFile = File.createTempFile("autojs6-bun-prefix-bypass-", ".js")
        try {
            sourceFile.writeText("console.log('ordinary')")

            val error = assertThrows(IllegalArgumentException::class.java) {
                ScriptLaunchSourceFactory.createPathLaunch(
                    path = sourceFile.path,
                    preExecuteScript = "\"bun\";",
                    defaultWorkingDirectory = sourceFile.parent.orEmpty(),
                ).engineName
            }

            assertTrue(error.message.orEmpty().contains("Bun"))
            assertTrue(error.message.orEmpty().contains("preExecuteScript"))
        } finally {
            sourceFile.delete()
        }
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
    fun tsxFileUsesCompilerBackedRhinoSource() {
        val sourceFile = File.createTempFile("autojs6-typescript-route-", ".tsx")
        try {
            sourceFile.writeText("\"ui\"; ui.layout(<text text=\"TSX\" />)")

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
