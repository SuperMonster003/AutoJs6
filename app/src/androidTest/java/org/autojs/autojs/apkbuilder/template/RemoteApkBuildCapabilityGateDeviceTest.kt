package org.autojs.autojs.apkbuilder.template

import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateCapabilityKeys
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Opt-in device qualification proving that an installed plugin which explicitly advertises
 * supportsRemoteBuild=false is rejected before request preparation or session opening.
 */
@RunWith(AndroidJUnit4::class)
class RemoteApkBuildCapabilityGateDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun declaredFalseCapabilityStopsBeforePreparation() {
        requireQualificationEnvironment()

        val root = File(context.cacheDir, SENSITIVE_PATH_SENTINEL).apply {
            deleteRecursively()
            check(mkdirs())
        }
        val output = File(requireNotNull(context.getExternalFilesDir(null)), OUTPUT_APK_NAME)
        val originalOutput = EXISTING_OUTPUT_SENTINEL.toByteArray(Charsets.UTF_8)
        output.writeBytes(originalOutput)
        var primaryFailure: Throwable? = null
        try {
            val config = ProjectConfig()
                .setName("M3 G6 capability gate")
                .setPackageName(OUTPUT_PACKAGE_NAME)
                .setVersionName("1.0.0")
                .setVersionCode(1)
                .setMainScriptFileName("main.js")
                .setProjectType("rhino")
                .setSourcePath(root.absolutePath)
                .setAbis(listOf(Build.SUPPORTED_ABIS.first()))
                .setLibs(emptyList())
                .setPermissions(emptyList())
            config.buildInfo.apply {
                buildId = "M3-G6-CAPABILITY-GATE"
                buildNumber = 1L
                buildTime = 1L
            }
            File(root, "project.json").writeText(config.toJson(false), Charsets.UTF_8)
            File(root, "main.js").writeText(
                "console.log(\"$SENSITIVE_SOURCE_SENTINEL\");\n",
                Charsets.UTF_8,
            )

            RemoteApkBuildDiagnostics.resetForTest()

            val discovered = runBlocking(Dispatchers.IO) {
                ApkBuilderTemplatePluginHost.discover(context)
            }.single { item -> item.serviceInfo.packageName == PLUGIN_PACKAGE }
            assertNull("Plugin metadata discovery failed", discovered.error)
            val capabilities = requireNotNull(requireNotNull(discovered.pluginInfo).capabilities) {
                "Qualification plugin metadata does not contain capabilities"
            }
            assertTrue(
                "Qualification plugin must explicitly declare the remote-build capability",
                capabilities.containsKey(ApkBuilderTemplateCapabilityKeys.SUPPORTS_REMOTE_BUILD),
            )
            assertFalse(
                "Qualification plugin unexpectedly advertises remote-build support",
                capabilities.getBoolean(ApkBuilderTemplateCapabilityKeys.SUPPORTS_REMOTE_BUILD),
            )

            val resolved = runBlocking(Dispatchers.IO) {
                ApkBuilderTemplatePluginHost.resolveRemoteBuilder(
                    context = context,
                    preferredPackageName = PLUGIN_PACKAGE,
                )
            }
            assertNull("A supportsRemoteBuild=false plugin passed host candidate resolution", resolved)

            val progressCount = AtomicInteger(0)
            val outcome = RemoteApkBuildClient.buildBlocking(
                context = context,
                projectConfig = config,
                outApk = output,
                cancelSignal = AtomicBoolean(false),
                progressListener = RemoteApkBuildClient.ProgressListener { progressCount.incrementAndGet() },
                preferredPluginPackageName = PLUGIN_PACKAGE,
            )

            assertEquals(RemoteApkBuildClient.STATUS_NOT_AVAILABLE, outcome.status)
            assertEquals(
                listOf(context.getString(R.string.error_apk_builder_remote_build_not_supported_by_plugin)),
                outcome.warnings,
            )
            assertTrue("Capability rejection unexpectedly returned errors: ${outcome.errors}", outcome.errors.isEmpty())
            assertEquals("Host prepared or opened a remote session after capability rejection", 0, progressCount.get())
            assertArrayEquals(
                "Capability rejection replaced or truncated the user's existing output",
                originalOutput,
                output.readBytes(),
            )
            assertFalse(
                "Capability rejection created private request staging",
                File(context.cacheDir, "apk-remote-build-input").listFiles().orEmpty().any(),
            )

            val event = RemoteApkBuildDiagnostics.snapshot().single()
            assertEquals(RemoteApkBuildDiagnosticStatus.NOT_AVAILABLE, event.status)
            assertEquals(RemoteApkBuildDiagnosticPhase.DISCOVERY, event.phase)
            assertEquals(RemoteApkBuildErrorCategory.PROVIDER_NOT_AVAILABLE, event.errorCategory)
            assertEquals(-1L, event.outputSizeBytes)
            assertSafeDiagnostic(event.safeLogLine())

            Log.i(
                TAG,
                "M3_G6_CAPABILITY_GATE_RESULT status=${event.status} phase=${event.phase} " +
                    "error=${event.errorCategory} progress_count=0 output_preserved=true",
            )
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            var cleanupFailure: Throwable? = null
            runCatching { root.deleteRecursively() }
                .onFailure { error -> cleanupFailure = appendFailure(cleanupFailure, error) }
            runCatching { output.delete() }
                .onFailure { error -> cleanupFailure = appendFailure(cleanupFailure, error) }
            if (primaryFailure == null && cleanupFailure != null) {
                throw cleanupFailure
            }
            if (primaryFailure != null && cleanupFailure != null) {
                primaryFailure.addSuppressed(cleanupFailure)
            }
        }
    }

    private fun requireQualificationEnvironment() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "APK Builder capability-gate qualification is opt-in; pass -e $ARG_ENABLED true",
            arguments.getString(ARG_ENABLED) == "true",
        )
        assertTrue("Capability-gate qualification requires a debug host build", BuildConfig.DEBUG)
        assertEquals("Qualification must target the canonical host", HOST_PACKAGE, context.packageName)
        assertEquals(
            "Qualification requires the exact canonical APK Builder package",
            PLUGIN_PACKAGE,
            arguments.getString(ARG_PLUGIN_PACKAGE),
        )

        val expectedSerial = arguments.getString(ARG_DEVICE_SERIAL).orEmpty().trim()
        assertTrue("Pass the exact device ro.serialno through -e $ARG_DEVICE_SERIAL", expectedSerial.isNotEmpty())
        val actualSerial = executeShellCommand("getprop ro.serialno").trim().ifEmpty {
            executeShellCommand("getprop ro.boot.serialno").trim()
        }
        assertEquals("Instrumentation is running on a different device", expectedSerial, actualSerial)

        val packageManager = context.packageManager
        try {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(PLUGIN_PACKAGE, PackageManager.MATCH_DISABLED_COMPONENTS)
        } catch (error: PackageManager.NameNotFoundException) {
            throw AssertionError("The real APK Builder plugin is not installed: $PLUGIN_PACKAGE").also {
                it.initCause(error)
            }
        }
        assertEquals(
            "The debug host and APK Builder plugin must have the same signer",
            PackageManager.SIGNATURE_MATCH,
            packageManager.checkSignatures(HOST_PACKAGE, PLUGIN_PACKAGE),
        )
    }

    private fun assertSafeDiagnostic(line: String) {
        assertTrue("Structured diagnostic is outside the whitelist: $line", SAFE_DIAGNOSTIC.matches(line))
        listOf(
            SENSITIVE_PATH_SENTINEL,
            SENSITIVE_SOURCE_SENTINEL,
            EXISTING_OUTPUT_SENTINEL,
            context.cacheDir.absolutePath,
        ).forEach { forbidden ->
            assertFalse("Structured diagnostic leaked sensitive text: $forbidden", line.contains(forbidden))
        }
    }

    private fun executeShellCommand(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return descriptor.use { pfd ->
            FileInputStream(pfd.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
        }
    }

    private fun appendFailure(existing: Throwable?, next: Throwable): Throwable {
        if (existing == null) return next
        existing.addSuppressed(next)
        return existing
    }

    private companion object {
        const val TAG = "RemoteApkBuildCapabilityGateTest"
        const val ARG_ENABLED = "autojs.apkBuilder.capabilityGate.enabled"
        const val ARG_DEVICE_SERIAL = "autojs.apkBuilder.capabilityGate.serial"
        const val ARG_PLUGIN_PACKAGE = "autojs.apkBuilder.capabilityGate.pluginPackage"

        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val PLUGIN_PACKAGE = ApkBuilderTemplatePluginIds.PACKAGE_NAME
        const val OUTPUT_APK_NAME = "m3-g6-capability-gate-existing-output.apk"
        const val OUTPUT_PACKAGE_NAME = "org.autojs.autojs6.m3g6capabilitygate"
        const val SENSITIVE_PATH_SENTINEL = "M3_G6_CAPABILITY_PRIVATE_PATH_DO_NOT_LOG"
        const val SENSITIVE_SOURCE_SENTINEL = "M3_G6_CAPABILITY_PRIVATE_SOURCE_DO_NOT_LOG"
        const val EXISTING_OUTPUT_SENTINEL = "M3_G6_CAPABILITY_EXISTING_OUTPUT_MUST_SURVIVE"

        val SAFE_DIAGNOSTIC = Regex(
            "^remote_apk_build seq=\\d+ protocol=\\d+ status=[A-Z_]+ phase=[A-Z_]+ " +
                "elapsed_ms=\\d+ output_bytes=-?\\d+ error=[A-Z_]+$",
        )
    }
}
