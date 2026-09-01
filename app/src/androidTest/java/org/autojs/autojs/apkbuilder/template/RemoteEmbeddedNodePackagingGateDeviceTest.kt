package org.autojs.autojs.apkbuilder.template

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import org.autojs.autojs.apkbuilder.ApkBuildLibrary
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs6.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Opt-in qualification proving that legacy Embedded Node selections fail before the host creates
 * request staging or emits preparation progress. Remote sessions can only open after preparation.
 */
@RunWith(AndroidJUnit4::class)
class RemoteEmbeddedNodePackagingGateDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun legacyNodeAliasAndScriptDirectiveStopBeforeRequestStaging() {
        requireQualificationEnvironment()

        val sourceRoot = File(context.cacheDir, SOURCE_DIRECTORY).apply {
            deleteRecursively()
            check(mkdirs())
        }
        val requestStaging = File(context.cacheDir, REQUEST_STAGING_DIRECTORY)
        requestStaging.deleteRecursively()
        val progressCount = AtomicInteger(0)
        var primaryFailure: Throwable? = null
        try {
            File(sourceRoot, "main.js").writeText("\"nodejs\";\nconsole.log('node gate');\n", Charsets.UTF_8)
            val baseConfig = ProjectConfig()
                .setName("M3 G3 Embedded Node gate")
                .setPackageName("org.autojs.autojs6.m3g3nodegate")
                .setVersionName("1.0.0")
                .setVersionCode(1)
                .setMainScriptFileName("main.js")
                .setProjectType("rhino")
                .setSourcePath(sourceRoot.absolutePath)
                .setPermissions(emptyList())

            listOf(
                baseConfig.setLibs(listOf("  EMBEDDED-NODEJS  ")),
                ProjectConfig.fromJson(baseConfig.toJson(false))
                    ?.setSourcePath(sourceRoot.absolutePath)
                    ?.setLibs(emptyList())
                    ?: error("Failed to clone directive-only Node fixture"),
            ).forEach { config ->
                val error = assertIOException {
                    RemoteApkBuildRequestPreparer.prepare(
                        context = context,
                        projectConfig = config,
                        outputFileName = "must-not-exist.apk",
                        progressListener = { progressCount.incrementAndGet() },
                    )
                }
                assertTrue(
                    "Node migration failure is not actionable: ${error.message}",
                    error.message.orEmpty().contains("external plugin"),
                )
                assertFalse("Node rejection created request staging", requestStaging.exists())
            }
            assertEquals("Node rejection emitted preparation progress", 0, progressCount.get())

            Log.i(
                TAG,
                "M3_G3_NODE_GATE_RESULT rejected=2 progress_count=0 staging_created=false",
            )
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            var cleanupFailure: Throwable? = null
            runCatching { sourceRoot.deleteRecursively() }
                .onFailure { cleanupFailure = it }
            runCatching { requestStaging.deleteRecursively() }
                .onFailure { error ->
                    val previousCleanupFailure = cleanupFailure
                    if (previousCleanupFailure == null) {
                        cleanupFailure = error
                    } else {
                        previousCleanupFailure.addSuppressed(error)
                    }
                }
            val finalCleanupFailure = cleanupFailure
            if (primaryFailure == null && finalCleanupFailure != null) {
                throw finalCleanupFailure
            }
            if (primaryFailure != null && finalCleanupFailure != null) {
                primaryFailure.addSuppressed(finalCleanupFailure)
            }
        }
    }

    private fun requireQualificationEnvironment() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Embedded Node packaging qualification is opt-in; pass -e $ARG_ENABLED true",
            arguments.getString(ARG_ENABLED) == "true",
        )
        assertTrue("Embedded Node packaging qualification requires a debug host build", BuildConfig.DEBUG)
        assertEquals("Qualification must target the canonical host", HOST_PACKAGE, context.packageName)

        val expectedSerial = arguments.getString(ARG_DEVICE_SERIAL).orEmpty().trim()
        assertTrue("Pass the exact device ro.serialno through -e $ARG_DEVICE_SERIAL", expectedSerial.isNotEmpty())
        val actualSerial = instrumentation.uiAutomation.executeShellCommand("getprop ro.serialno").use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText().trim() }
        }
        assertEquals("Instrumentation is running on a different device", expectedSerial, actualSerial)
    }

    private fun assertIOException(block: () -> Unit): IOException {
        try {
            block()
        } catch (error: IOException) {
            return error
        }
        fail("Expected Embedded Node packaging to fail closed")
        throw AssertionError("unreachable")
    }

    private companion object {
        const val TAG = "RemoteEmbeddedNodeGate"
        const val ARG_ENABLED = "autojs.apkBuilder.g3NodeGate.enabled"
        const val ARG_DEVICE_SERIAL = "autojs.apkBuilder.g3NodeGate.serial"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val SOURCE_DIRECTORY = "m3-g3-node-gate-source"
        const val REQUEST_STAGING_DIRECTORY = "apk-remote-build-input"
    }
}
