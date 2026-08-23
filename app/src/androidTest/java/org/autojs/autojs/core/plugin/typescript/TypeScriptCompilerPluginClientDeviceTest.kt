package org.autojs.autojs.core.plugin.typescript

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypeScriptCompilerPluginClientDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun compilesUtf8SingleFileThroughRealPluginBinder() {
        withSource(
            name = "你好.ts",
            source = """
                const message: string = "host-client-ok"
                toast(message)
            """.trimIndent(),
        ) { source ->
            val output = runBlocking {
                TypeScriptCompilerPluginClient(context).compileSingleFile(source)
            }

            assertTrue(output is TypeScriptCompilationOutput.Emitted)
            output as TypeScriptCompilationOutput.Emitted
            assertTrue(output.javaScript.contains("host-client-ok"))
            assertTrue(output.sourceMap.contains("\"version\":3"))
            assertTrue(output.entryModulePath.endsWith("你好.js"))
            assertFalse(output.diagnostics.any(TypeScriptDiagnostic::isError))
        }
    }

    @Test
    fun returnsBoundedDiagnosticsWithoutExecutableOutput() {
        withSource(
            name = "diagnostic.ts",
            source = "const count: number = \"not-a-number\"",
        ) { source ->
            val output = runBlocking {
                TypeScriptCompilerPluginClient(context).compileSingleFile(source)
            }

            assertTrue(output is TypeScriptCompilationOutput.Diagnostics)
            output as TypeScriptCompilationOutput.Diagnostics
            assertEquals(1, output.diagnostics.count(TypeScriptDiagnostic::isError))
            assertTrue(
                output.diagnostics.any { diagnostic ->
                    diagnostic.category == TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_ERROR &&
                        diagnostic.uri?.endsWith("/diagnostic.ts") == true &&
                        diagnostic.range != null
                },
            )
        }
    }

    private fun withSource(name: String, source: String, block: (File) -> Unit) {
        val root = File(context.cacheDir, "typescript-client-${System.nanoTime()}")
        check(root.mkdirs())
        try {
            val sourceFile = File(root, name)
            sourceFile.writeText(source, Charsets.UTF_8)
            block(sourceFile)
        } finally {
            root.deleteRecursively()
        }
    }
}
