package org.autojs.autojs.apkbuilder.template

import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.apkbuilder.template.ApkBuildResult
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Opt-in hostile lifecycle qualification against the separately installed real APK Builder plugin.
 *
 * The test force-stops the exact package only after the remote session reports its first callback.
 * It therefore exercises a real cross-process Binder death instead of a fake binder or unit-level
 * wait-policy branch. Ordinary connected tests skip this class unless the producer explicitly
 * names the device and consents to force-stopping the canonical plugin package.
 */
@RunWith(AndroidJUnit4::class)
class RemoteApkBuildBinderDeathDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun realPluginDeathIsOneRedactedTerminalAndPreservesExistingOutput() {
        requireQualificationEnvironment()

        val root = File(context.cacheDir, SENSITIVE_PATH_SENTINEL).apply {
            deleteRecursively()
            check(mkdirs())
        }
        val output = File(requireNotNull(context.getExternalFilesDir(null)), OUTPUT_APK_NAME)
        val originalOutput = EXISTING_OUTPUT_SENTINEL.toByteArray(Charsets.UTF_8)
        output.writeBytes(originalOutput)

        val config = ProjectConfig()
            .setName("M3 G6 Binder death")
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
            buildId = "M3-G6-BINDER-DEATH"
            buildNumber = 1L
            buildTime = 1L
        }

        val killIssued = AtomicBoolean(false)
        val killFinished = CountDownLatch(1)
        val killFailure = AtomicReference<Throwable?>()
        val processBeforeKill = AtomicReference("")
        val processAfterKill = AtomicReference("")
        var primaryFailure: Throwable? = null
        try {
            File(root, "project.json").writeText(config.toJson(false), Charsets.UTF_8)
            File(root, "main.js").writeText(
                "console.log(\"$SENSITIVE_SOURCE_SENTINEL\");\n",
                Charsets.UTF_8,
            )
            writeLargeFixture(File(root, "payload.bin"), LARGE_FIXTURE_BYTES)

            RemoteApkBuildDiagnostics.resetForTest()

            val outcome = RemoteApkBuildClient.buildBlocking(
                context = context,
                projectConfig = config,
                outApk = output,
                cancelSignal = AtomicBoolean(false),
                progressListener = RemoteApkBuildClient.ProgressListener { progress ->
                    if (
                        progress.title == REMOTE_SESSION_ACTIVE_TITLE &&
                        killIssued.compareAndSet(false, true)
                    ) {
                        Thread({
                            try {
                                processBeforeKill.set(executeShellCommand("pidof $PLUGIN_PACKAGE").trim())
                                check(processBeforeKill.get().isNotEmpty()) {
                                    "The real APK Builder process was absent before force-stop"
                                }
                                executeShellCommand("am force-stop --user current $PLUGIN_PACKAGE")
                                processAfterKill.set(executeShellCommand("pidof $PLUGIN_PACKAGE").trim())
                                check(processAfterKill.get().isEmpty()) {
                                    "The APK Builder process survived force-stop: ${processAfterKill.get()}"
                                }
                            } catch (error: Throwable) {
                                killFailure.compareAndSet(null, error)
                            } finally {
                                killFinished.countDown()
                            }
                        }, "m3-g6-apk-builder-force-stop").start()
                    }
                },
                preferredPluginPackageName = PLUGIN_PACKAGE,
            )

            assertTrue(
                "The remote session never reached its first plugin callback: " +
                    "status=${outcome.status}, warnings=${outcome.warnings}, errors=${outcome.errors}",
                killIssued.get(),
            )
            assertTrue(
                "The explicitly consented plugin force-stop did not finish",
                killFinished.await(FORCE_STOP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
            )
            killFailure.get()?.let { throw AssertionError("Plugin force-stop failed", it) }
            assertEquals(ApkBuildResult.STATUS_FAILED, outcome.status)
            assertTrue(
                "Binder death did not reach an actionable host error: ${outcome.errors}",
                outcome.errors.singleOrNull()?.contains("lost", ignoreCase = true) == true,
            )
            assertArrayEquals(
                "Binder death replaced or truncated the user's existing output",
                originalOutput,
                output.readBytes(),
            )
            assertFalse(
                "Private remote-build request staging leaked after Binder death",
                File(context.cacheDir, "apk-remote-build-input").listFiles().orEmpty().any(),
            )

            val event = RemoteApkBuildDiagnostics.snapshot().single()
            assertEquals(RemoteApkBuildDiagnosticStatus.FAILED, event.status)
            assertEquals(RemoteApkBuildDiagnosticPhase.WAIT, event.phase)
            assertEquals(RemoteApkBuildErrorCategory.BINDER_LOST, event.errorCategory)
            assertEquals(-1L, event.outputSizeBytes)
            assertSafeDiagnostic(event.safeLogLine())

            val recovered = awaitPluginRecovery()
            assertNotNull("The force-stopped APK Builder plugin did not recover", recovered)
            val recoveredPid = executeShellCommand("pidof $PLUGIN_PACKAGE").trim()
            assertTrue("Recovered APK Builder process is absent", recoveredPid.isNotEmpty())

            Log.i(
                TAG,
                "M3_G6_RESULT status=${event.status} phase=${event.phase} " +
                    "error=${event.errorCategory} output_preserved=true recovered=true",
            )
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            var cleanupFailure: Throwable? = null
            if (killIssued.get() && !killFinished.await(FORCE_STOP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                cleanupFailure = AssertionError("Plugin force-stop thread remained active during cleanup")
            }
            if (killIssued.get()) {
                runCatching {
                    checkNotNull(awaitPluginRecovery()) {
                        "The force-stopped APK Builder plugin did not recover during cleanup"
                    }
                }.onFailure { cleanupFailure = appendFailure(cleanupFailure, it) }
            }
            runCatching { root.deleteRecursively() }
                .onFailure { cleanupFailure = appendFailure(cleanupFailure, it) }
            runCatching { output.delete() }
                .onFailure { cleanupFailure = appendFailure(cleanupFailure, it) }
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
            "APK Builder Binder-death qualification is opt-in; pass -e $ARG_ENABLED true",
            arguments.getString(ARG_ENABLED) == "true",
        )
        assertTrue("Binder-death qualification requires a debug host build", BuildConfig.DEBUG)
        assertEquals("Qualification must target the canonical host", HOST_PACKAGE, context.packageName)
        assertEquals(
            "Force-stop requires explicit consent for the exact APK Builder package",
            PLUGIN_PACKAGE,
            arguments.getString(ARG_FORCE_STOP_PACKAGE),
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
            "The debug host and real APK Builder plugin must have the same signer",
            PackageManager.SIGNATURE_MATCH,
            packageManager.checkSignatures(HOST_PACKAGE, PLUGIN_PACKAGE),
        )
    }

    private fun awaitPluginRecovery(): ApkBuilderTemplatePluginHost.ResolvedRemoteBuilder? {
        return runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(RECOVERY_TIMEOUT_MILLIS) {
                var resolved: ApkBuilderTemplatePluginHost.ResolvedRemoteBuilder? = null
                while (resolved == null) {
                    resolved = runCatching {
                        ApkBuilderTemplatePluginHost.resolveRemoteBuilder(
                            context = context,
                            preferredPackageName = PLUGIN_PACKAGE,
                        )
                    }.getOrNull()
                    if (resolved == null) {
                        delay(RECOVERY_POLL_MILLIS)
                    }
                }
                resolved
            }
        }
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

    private fun writeLargeFixture(file: File, byteCount: Int) {
        val block = ByteArray(1024 * 1024) { index -> (index and 0xff).toByte() }
        FileOutputStream(file).use { output ->
            repeat(byteCount / block.size) { output.write(block) }
            output.flush()
        }
        block.fill(0)
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
        const val TAG = "RemoteApkBuildBinderDeathTest"
        const val ARG_ENABLED = "autojs.apkBuilder.binderDeath.enabled"
        const val ARG_DEVICE_SERIAL = "autojs.apkBuilder.binderDeath.serial"
        const val ARG_FORCE_STOP_PACKAGE = "autojs.apkBuilder.binderDeath.forceStopPackage"

        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val PLUGIN_PACKAGE = ApkBuilderTemplatePluginIds.PACKAGE_NAME
        const val OUTPUT_APK_NAME = "m3-g6-existing-output.apk"
        const val OUTPUT_PACKAGE_NAME = "org.autojs.autojs6.m3g6binderdeath"
        const val REMOTE_SESSION_ACTIVE_TITLE = "Reading remote build request"
        const val SENSITIVE_PATH_SENTINEL = "M3_G6_PRIVATE_PATH_DO_NOT_LOG"
        const val SENSITIVE_SOURCE_SENTINEL = "M3_G6_PRIVATE_SOURCE_DO_NOT_LOG"
        const val EXISTING_OUTPUT_SENTINEL = "M3_G6_EXISTING_OUTPUT_MUST_SURVIVE"

        const val LARGE_FIXTURE_BYTES = 32 * 1024 * 1024
        const val FORCE_STOP_TIMEOUT_MILLIS = 10_000L
        const val RECOVERY_TIMEOUT_MILLIS = 20_000L
        const val RECOVERY_POLL_MILLIS = 250L

        val SAFE_DIAGNOSTIC = Regex(
            "^remote_apk_build seq=\\d+ protocol=\\d+ status=[A-Z_]+ phase=[A-Z_]+ " +
                "elapsed_ms=\\d+ output_bytes=-?\\d+ error=[A-Z_]+$",
        )
    }
}
