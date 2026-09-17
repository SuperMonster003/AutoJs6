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
    fun nodeProjectEntryLogsRetainTheRealPathAndExtension() {
        val project = File(System.getProperty("java.io.tmpdir"), "node-project-log").absoluteFile
        listOf("main.cjs", "index.mjs", "nested/entry.js").forEach { relative ->
            val entry = File(project, relative)
            val source = NodeProjectJavaScriptFileSource(project, entry, "Project title")
            assertEquals("Project title", source.name)
            assertEquals(entry.absolutePath, source.fullPath)
            assertEquals(entry.absolutePath, source.toString())
            source.overriddenFullPath = File(project, "original/$relative").absolutePath
            assertEquals(source.overriddenFullPath, source.fullPath)
        }
    }

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

        assertTrue(JavaScriptSource.isQuickJsFileName("main.quickjs.js"))
        assertTrue(JavaScriptSource.isQuickJsFileName("module.QUICKJS.MJS"))
        assertFalse(JavaScriptSource.isQuickJsFileName("main.quickjs.ts"))
        assertFalse(JavaScriptSource.isQuickJsFileName("main.qjs"))
        assertFalse(JavaScriptSource.isQuickJsFileName(null))
    }

    @Test
    fun quickJsDirectiveAndMarkersSelectTheIndependentEngineWithoutChangingLegacyRoutes() {
        val directed = StringScriptSource("\"QuIcKjS\";\nconsole.log('quickjs')")

        assertTrue(
            directed.executionMode and JavaScriptSource.EXECUTION_MODE_QUICKJS != 0,
        )
        assertEquals(JavaScriptSource.ENGINE_QUICKJS, directed.engineName)
        assertEquals(
            JavaScriptSource.ENGINE_QUICKJS,
            JavaScriptSource.resolveEngineName(JavaScriptSource.EXECUTION_MODE_QUICKJS),
        )
        assertEquals(
            JavaScriptSource.ENGINE_QUICKJS,
            StringScriptSource("module.mjs", "\"quickjs\"; export default 42").engineName,
        )
        assertEquals(
            JavaScriptSource.ENGINE_QUICKJS,
            StringScriptSource("programmatic.quickjs.mjs", "export default 42").engineName,
        )
        assertEquals(
            JavaScriptSource.ENGINE_QUICKJS,
            StringScriptSource("shared.quickjs.mjs", "export default 42").apply {
                overriddenFullPath = File(System.getProperty("java.io.tmpdir"), "provider-cache.mjs").path
            }.engineName,
        )

        listOf("entry.quickjs.js", "entry.quickjs.mjs").forEach { fileName ->
            val sourceFile = File.createTempFile(
                "autojs6-quickjs-route-",
                fileName.removePrefix("entry"),
            )
            try {
                sourceFile.writeText("console.log('quickjs')")
                val launch = ScriptLaunchSourceFactory.createFileLaunch(
                    file = sourceFile,
                    defaultWorkingDirectory = sourceFile.parent.orEmpty(),
                )

                assertEquals(JavaScriptSource.ENGINE_QUICKJS, launch.engineName)
            } finally {
                sourceFile.delete()
            }
        }
    }

    @Test
    fun conflictingQuickJsEngineSelectorsFailClosed() {
        listOf(
            JavaScriptSource.EXECUTION_MODE_QUICKJS or JavaScriptSource.EXECUTION_MODE_NODEJS,
            JavaScriptSource.EXECUTION_MODE_QUICKJS or JavaScriptSource.EXECUTION_MODE_RHINO,
            JavaScriptSource.EXECUTION_MODE_QUICKJS or JavaScriptSource.EXECUTION_MODE_BUN,
        ).forEach { mode ->
            assertThrows(IllegalArgumentException::class.java) {
                JavaScriptSource.resolveEngineName(mode)
            }
        }

        assertThrows(IllegalArgumentException::class.java) {
            StringScriptSource("\"quickjs,bun\";\n42").engineName
        }

        val quickJsMarkerWithNodeDirective = File.createTempFile(
            "autojs6-quickjs-conflict-",
            ".quickjs.mjs",
        )
        val nodeMarkerWithQuickJsDirective = File.createTempFile(
            "autojs6-quickjs-conflict-",
            ".node.js",
        )
        try {
            quickJsMarkerWithNodeDirective.writeText("\"nodejs\";\n42")
            nodeMarkerWithQuickJsDirective.writeText("\"quickjs\";\n42")

            listOf(quickJsMarkerWithNodeDirective, nodeMarkerWithQuickJsDirective).forEach { file ->
                assertThrows(IllegalArgumentException::class.java) {
                    JavaScriptFileSource(file).engineName
                }
            }
        } finally {
            quickJsMarkerWithNodeDirective.delete()
            nodeMarkerWithQuickJsDirective.delete()
        }
    }

    @Test
    fun encryptedHeadersMergeSemanticQuickJsSuffixesAndRejectEngineConflicts() {
        val quickJsFile = File.createTempFile("autojs6-quickjs-encrypted-", ".quickjs.js")
        val conflictFile = File.createTempFile("autojs6-quickjs-encrypted-conflict-", ".quickjs.mjs")
        try {
            FileOutputStream(quickJsFile).use { output ->
                EncryptedScriptFileHeader.writeHeader(output)
                output.write(byteArrayOf(1, 2, 3))
            }
            FileOutputStream(conflictFile).use { output ->
                EncryptedScriptFileHeader.writeHeader(
                    output,
                    EncryptedScriptFileHeader.FLAG_EXECUTION_MODE_BUN,
                )
                output.write(byteArrayOf(1, 2, 3))
            }

            assertEquals(JavaScriptSource.ENGINE_QUICKJS, JavaScriptFileSource(quickJsFile).engineName)
            assertThrows(IllegalArgumentException::class.java) {
                JavaScriptFileSource(conflictFile).engineName
            }
        } finally {
            quickJsFile.delete()
            conflictFile.delete()
        }
    }

    @Test
    fun nodeProjectLaunchRejectsQuickJsEntriesInsteadOfForcingNode() {
        val root = Files.createTempDirectory("autojs6-quickjs-node-project-").toFile()
        val quickJsEntry = File(root, "main.quickjs.mjs").apply {
            writeText("console.log('quickjs')")
        }
        try {
            val source = NodeProjectJavaScriptFileSource(root, quickJsEntry)
            val error = assertThrows(IllegalArgumentException::class.java) { source.engineName }
            assertTrue(error.message.orEmpty().contains("QuickJS"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun quickJsLaunchRejectsJavaScriptPreExecuteInsteadOfChangingEngines() {
        val sourceFile = File.createTempFile("autojs6-quickjs-prefix-", ".quickjs.js")
        try {
            sourceFile.writeText("console.log('quickjs')")

            val error = assertThrows(IllegalArgumentException::class.java) {
                ScriptLaunchSourceFactory.createPathLaunch(
                    path = sourceFile.path,
                    preExecuteScript = "globalThis.beforeQuickJs = true;",
                    defaultWorkingDirectory = sourceFile.parent.orEmpty(),
                )
            }

            assertTrue(error.message.orEmpty().contains("QuickJS"))
            assertTrue(error.message.orEmpty().contains("preExecuteScript"))
        } finally {
            sourceFile.delete()
        }
    }

    @Test
    fun quickJsDirectiveInsidePreExecuteScriptCannotRerouteAnOrdinaryEntry() {
        val sourceFile = File.createTempFile("autojs6-quickjs-prefix-bypass-", ".js")
        try {
            sourceFile.writeText("console.log('ordinary')")

            val error = assertThrows(IllegalArgumentException::class.java) {
                ScriptLaunchSourceFactory.createPathLaunch(
                    path = sourceFile.path,
                    preExecuteScript = "\"quickjs\";",
                    defaultWorkingDirectory = sourceFile.parent.orEmpty(),
                ).engineName
            }

            assertTrue(error.message.orEmpty().contains("QuickJS"))
            assertTrue(error.message.orEmpty().contains("preExecuteScript"))
        } finally {
            sourceFile.delete()
        }
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
            assertEquals(sourceFile.canonicalPath, launch.source.toString())
            assertEquals(sourceFile.canonicalPath, launch.source.fullPath)
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
            assertEquals(sourceFile.canonicalPath, launch.source.toString())
            assertEquals(sourceFile.canonicalPath, launch.source.fullPath)
        } finally {
            sourceFile.delete()
        }
    }

    @Test
    fun namedTypeScriptFilesPreserveTheirExtensionPrefixAndOriginHints() {
        val sourceFile = File.createTempFile("autojs6-typescript-name-", ".tsx")
        try {
            val source = NodeProjectScriptSourceFactory.create("Component", sourceFile)

            assertEquals("Component.tsx", source.toString())
            source.prefix = "\$remote/"
            assertEquals("\$remote/Component.tsx", source.toString())
            source.originUri = "content://scripts/original.tsx"
            assertEquals(source.originUri, source.toString())
            source.overriddenFullPath = File(sourceFile.parentFile, "original.tsx").path
            assertEquals(source.originPath, source.toString())
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
