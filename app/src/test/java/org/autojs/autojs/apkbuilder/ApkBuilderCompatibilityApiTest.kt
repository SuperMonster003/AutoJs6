package org.autojs.autojs.apkbuilder

import android.content.Context
import org.autojs.autojs.project.ProjectConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Suppress("DEPRECATION")
class ApkBuilderCompatibilityApiTest {

    @Test
    fun historicBuilderSurfaceRemainsAvailable() {
        val type = ApkBuilder::class.java

        type.getConstructor(InputStream::class.java, File::class.java, String::class.java)
        assertReturns<ApkBuilder>("setProgressCallback", ApkBuilder.ProgressCallback::class.java)
        assertReturns<ApkBuilder>("setCancelSignal", AtomicBoolean::class.java)
        assertReturns<ApkBuilder>("prepare", Context::class.java)
        assertReturns<ApkBuilder>("setScriptFile", Context::class.java, String::class.java)
        assertReturns<Void>("copyDir", Context::class.java, File::class.java, String::class.java)
        assertReturns<ApkBuilder>(
            "replaceFile",
            Context::class.java,
            String::class.java,
            String::class.java,
        )
        assertReturns<ApkBuilder>(
            "replaceFile",
            Context::class.java,
            File::class.java,
            String::class.java,
        )
        assertReturns<ApkBuilder>("withConfig", Context::class.java, ProjectConfig::class.java)
        assertReturns<ManifestEditor>("editManifest")
        assertReturns<ApkBuilder>("commitProjectConfigIfNeeded", Context::class.java)
        assertReturns<ApkBuilder>("build", Context::class.java)
        assertReturns<ApkBuilder>("sign", Context::class.java)
        assertReturns<ApkBuilder>("cleanWorkspace", Context::class.java)
        assertReturns<ApkBuilder>("finish")
        assertReturns<ApkBuilder>("setArscPackageName", String::class.java)

        assertArrayEquals(
            arrayOf("PREPARE", "BUILD", "SIGN", "CLEAN"),
            ApkBuilder.ProgressStep.entries.map { it.name }.toTypedArray(),
        )
    }

    @Test
    fun unsupportedIncrementalMutationFailsWithCentralMigrationGuidance() {
        val builder = ApkBuilder(null, File("unused-output.apk"), "unused-workspace")

        val error = runCatching { builder.setArscPackageName("org.example.app") }
            .exceptionOrNull()
        assertTrue(error is UnsupportedOperationException)
        assertTrue(error?.message.orEmpty().contains("incremental in-process APK template mutation API"))
        assertTrue(error?.message.orEmpty().contains("ProjectConfig"))
        assertTrue(error?.message.orEmpty().contains("official APK Builder plugin"))
    }

    @Test
    fun unusedHistoricTemplateStreamIsClosedExactlyOnce() {
        val closeCount = AtomicInteger()
        val input = object : InputStream() {
            override fun read(): Int = -1

            override fun close() {
                closeCount.incrementAndGet()
            }
        }

        ApkBuilder(input, File("unused-output.apk"), "unused-workspace")

        assertEquals(1, closeCount.get())
    }

    @Test
    fun pxbSpecificManifestEditorHookIsIntentionallyNotRetained() {
        // ManifestEditor.onAttr(AxmlWriter.Attr) would reintroduce the pxb AXML type into the host
        // public API. Incremental binary-manifest subclassing must migrate into the builder plugin.
        val type = ManifestEditor::class.java
        type.getConstructor(InputStream::class.java)
        assertEquals(
            ManifestEditor::class.java,
            type.getMethod("setVersionCode", Integer.TYPE).returnType,
        )
        assertEquals(
            ManifestEditor::class.java,
            type.getMethod("setVersionName", String::class.java).returnType,
        )
        assertEquals(
            ManifestEditor::class.java,
            type.getMethod("setAppName", CharSequence::class.java).returnType,
        )
        assertEquals(
            ManifestEditor::class.java,
            type.getMethod("setPackageName", String::class.java).returnType,
        )
        assertEquals(
            ManifestEditor::class.java,
            type.getMethod("addEmbeddedNodeScriptServiceIfMissing").returnType,
        )
        assertEquals(
            ManifestEditor::class.java,
            type.getMethod("addEmbeddedNodeForegroundServicePermissionsIfMissing").returnType,
        )
        assertEquals(ManifestEditor::class.java, type.getMethod("commit").returnType)
        assertEquals(Void.TYPE, type.getMethod("writeTo", OutputStream::class.java).returnType)
        assertEquals(
            Boolean::class.javaPrimitiveType,
            type.getMethod("isPermissionRequired", String::class.java).returnType,
        )
        assertEquals(
            Boolean::class.javaPrimitiveType,
            type.getMethod(
                "shouldIgnoreComponentNode",
                String::class.java,
                String::class.java,
            ).returnType,
        )
        assertEquals(
            Boolean::class.javaPrimitiveType,
            type.getDeclaredMethod(
                "isEmbeddedNodeForegroundServicePermission",
                String::class.java,
            ).returnType,
        )
        assertFalse(
            type.methods.any { method ->
                method.name == "onAttr" ||
                        method.parameterTypes.any { it.name.startsWith("pxb.android.") }
            }
        )
    }

    private inline fun <reified T> assertReturns(
        name: String,
        vararg parameterTypes: Class<*>,
    ) {
        val expected = if (T::class.java == Void::class.java) {
            Void.TYPE
        } else {
            T::class.java
        }
        assertEquals(expected, ApkBuilder::class.java.getMethod(name, *parameterTypes).returnType)
    }
}
