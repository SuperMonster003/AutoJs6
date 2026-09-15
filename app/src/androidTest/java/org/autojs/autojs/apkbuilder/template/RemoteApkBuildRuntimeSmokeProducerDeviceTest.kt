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
import org.autojs.plugin.apkbuilder.template.ApkBuildResult
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateCapabilityKeys
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Opt-in producer for the G1 installation/launch/runtime smoke qualification.
 *
 * This test intentionally retains two successful output APKs in the host external-files directory.
 * The qualification driver pulls, installs, launches, and inspects them, then removes all fixtures.
 */
@RunWith(AndroidJUnit4::class)
class RemoteApkBuildRuntimeSmokeProducerDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun producesDirectoryAndSingleFileRuntimeSmokeApks() {
        requireQualificationEnvironment()

        val root = File(context.cacheDir, PRIVATE_PATH_SENTINEL).apply {
            deleteRecursively()
            check(mkdirs())
        }
        val externalFiles = requireNotNull(context.getExternalFilesDir(null))
        val directorySource = File(root, "directory-source").apply { check(mkdirs()) }
        val singleFileSource = File(root, "single-file-source.js")
        val specs = listOf(
            BuildSpec(
                kind = "directory",
                displayName = "M3 G1 directory runtime smoke",
                packageName = DIRECTORY_PACKAGE,
                marker = DIRECTORY_MARKER,
                scriptLogMarker = DIRECTORY_SCRIPT_LOG_MARKER,
                source = directorySource,
                output = File(externalFiles, DIRECTORY_OUTPUT_APK),
            ),
            BuildSpec(
                kind = "single_file",
                displayName = "M3 G1 single-file runtime smoke",
                packageName = SINGLE_FILE_PACKAGE,
                marker = SINGLE_FILE_MARKER,
                scriptLogMarker = SINGLE_FILE_SCRIPT_LOG_MARKER,
                source = singleFileSource,
                output = File(externalFiles, SINGLE_FILE_OUTPUT_APK),
            ),
        )
        specs.forEach { spec ->
            if (spec.output.exists()) {
                check(spec.output.delete()) { "Unable to remove stale qualification output: ${spec.output.name}" }
            }
        }

        var succeeded = false
        var primaryFailure: Throwable? = null
        try {
            val directoryConfig = createConfig(specs[0], buildNumber = 1L)
            File(directorySource, "project.json").writeText(directoryConfig.toJson(false), Charsets.UTF_8)
            File(directorySource, "main.js").writeText(
                runtimeSmokeScript(specs[0].marker, specs[0].scriptLogMarker),
                Charsets.UTF_8,
            )
            singleFileSource.writeText(
                runtimeSmokeScript(specs[1].marker, specs[1].scriptLogMarker),
                Charsets.UTF_8,
            )

            RemoteApkBuildDiagnostics.resetForTest()

            val observations = specs.mapIndexed { index, spec ->
                val progressCount = AtomicInteger(0)
                val config = if (index == 0) directoryConfig else createConfig(spec, buildNumber = 2L)
                val outcome = RemoteApkBuildClient.buildBlocking(
                    context = context,
                    projectConfig = config,
                    outApk = spec.output,
                    cancelSignal = AtomicBoolean(false),
                    progressListener = RemoteApkBuildClient.ProgressListener {
                        progressCount.incrementAndGet()
                    },
                    preferredPluginPackageName = PLUGIN_PACKAGE,
                )
                assertEquals("${spec.kind} remote build failed: ${outcome.errors}", ApkBuildResult.STATUS_OK, outcome.status)
                assertTrue("${spec.kind} remote build returned errors: ${outcome.errors}", outcome.errors.isEmpty())
                assertTrue("${spec.kind} remote build emitted no progress", progressCount.get() > 0)
                assertTrue("${spec.kind} output APK is missing", spec.output.isFile)
                assertTrue("${spec.kind} output APK is unexpectedly small", spec.output.length() > MIN_OUTPUT_BYTES)
                assertArchiveIdentity(spec)
                BuildObservation(
                    spec = spec,
                    sizeBytes = spec.output.length(),
                    sha256 = sha256(spec.output),
                    progressCount = progressCount.get(),
                )
            }

            assertFalse(
                "Successful runtime-smoke production leaked private request staging",
                File(context.cacheDir, "apk-remote-build-input").listFiles().orEmpty().any(),
            )
            val diagnostics = RemoteApkBuildDiagnostics.snapshot()
            assertEquals("Expected one structured diagnostic per produced APK", specs.size, diagnostics.size)
            diagnostics.zip(observations).forEach { (event, observation) ->
                assertEquals(RemoteApkBuildDiagnosticStatus.SUCCEEDED, event.status)
                assertEquals(RemoteApkBuildDiagnosticPhase.COMPLETE, event.phase)
                assertEquals(RemoteApkBuildErrorCategory.NONE, event.errorCategory)
                assertEquals(observation.sizeBytes, event.outputSizeBytes)
            }

            Log.i(
                TAG,
                "M3_G1_PRODUCER_RESULT builds=2 " + observations.joinToString(separator = " ") { observation ->
                    "${observation.spec.kind}_bytes=${observation.sizeBytes} " +
                        "${observation.spec.kind}_sha256=${observation.sha256} " +
                        "${observation.spec.kind}_progress=${observation.progressCount}"
                },
            )
            succeeded = true
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            var cleanupFailure: Throwable? = null
            runCatching { root.deleteRecursively() }
                .onFailure { error -> cleanupFailure = appendFailure(cleanupFailure, error) }
            if (!succeeded) {
                specs.forEach { spec ->
                    runCatching { spec.output.delete() }
                        .onFailure { error -> cleanupFailure = appendFailure(cleanupFailure, error) }
                }
            }
            if (primaryFailure == null && cleanupFailure != null) {
                throw cleanupFailure
            }
            if (primaryFailure != null && cleanupFailure != null) {
                primaryFailure.addSuppressed(cleanupFailure)
            }
        }
    }

    private fun createConfig(spec: BuildSpec, buildNumber: Long): ProjectConfig {
        return ProjectConfig()
            .setName(spec.displayName)
            .setPackageName(spec.packageName)
            .setVersionName("1.0.$buildNumber")
            .setVersionCode(buildNumber.toInt())
            .setMainScriptFileName(if (spec.source.isDirectory) "main.js" else spec.source.name)
            .setProjectType("rhino")
            .setSourcePath(spec.source.absolutePath)
            .setAbis(listOf(Build.SUPPORTED_ABIS.first()))
            .setLibs(emptyList())
            .setPermissions(emptyList())
            .also { config ->
                config.buildInfo.apply {
                    buildId = "M3-G1-${spec.kind.uppercase()}"
                    this.buildNumber = buildNumber
                    buildTime = buildNumber
                }
            }
    }

    private fun runtimeSmokeScript(marker: String, logMarker: String): String = """
        "ui";

        ui.layout(
            <vertical gravity="center" padding="24">
                <text
                    id="result"
                    contentDescription="$marker"
                    gravity="center"
                    text="$marker"
                    textSize="22sp"
                />
            </vertical>
        );
        console.log("$logMarker");
    """.trimIndent() + "\n"

    @Suppress("DEPRECATION")
    private fun assertArchiveIdentity(spec: BuildSpec) {
        val archiveInfo = context.packageManager.getPackageArchiveInfo(
            spec.output.absolutePath,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES,
        )
        assertNotNull("PackageManager could not parse ${spec.kind} output APK", archiveInfo)
        assertEquals(spec.packageName, requireNotNull(archiveInfo).packageName)
        assertEquals("1.0.${if (spec.kind == "directory") 1 else 2}", archiveInfo.versionName)
    }

    private fun requireQualificationEnvironment() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Runtime-smoke production is opt-in; pass -e $ARG_ENABLED true",
            arguments.getString(ARG_ENABLED) == "true",
        )
        assertTrue("Runtime-smoke qualification requires a debug host build", BuildConfig.DEBUG)
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
        val discovered = runBlocking(Dispatchers.IO) {
            ApkBuilderTemplatePluginHost.discover(context)
        }.single { item -> item.serviceInfo.packageName == PLUGIN_PACKAGE }
        assertEquals(null, discovered.error)
        val capabilities = requireNotNull(requireNotNull(discovered.pluginInfo).capabilities)
        assertTrue(
            "Qualification plugin does not advertise remote-build support",
            capabilities.getBoolean(ApkBuilderTemplateCapabilityKeys.SUPPORTS_REMOTE_BUILD, false),
        )
    }

    private fun executeShellCommand(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return descriptor.use { pfd ->
            FileInputStream(pfd.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun appendFailure(existing: Throwable?, next: Throwable): Throwable {
        if (existing == null) return next
        existing.addSuppressed(next)
        return existing
    }

    private data class BuildSpec(
        val kind: String,
        val displayName: String,
        val packageName: String,
        val marker: String,
        val scriptLogMarker: String,
        val source: File,
        val output: File,
    )

    private data class BuildObservation(
        val spec: BuildSpec,
        val sizeBytes: Long,
        val sha256: String,
        val progressCount: Int,
    )

    private companion object {
        const val TAG = "RemoteApkBuildRuntimeSmokeProducer"
        const val ARG_ENABLED = "autojs.apkBuilder.runtimeSmoke.enabled"
        const val ARG_DEVICE_SERIAL = "autojs.apkBuilder.runtimeSmoke.serial"
        const val ARG_PLUGIN_PACKAGE = "autojs.apkBuilder.runtimeSmoke.pluginPackage"

        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val PLUGIN_PACKAGE = ApkBuilderTemplatePluginIds.PACKAGE_NAME
        const val DIRECTORY_PACKAGE = "org.autojs.autojs6.m3g1directorysmoke"
        const val SINGLE_FILE_PACKAGE = "org.autojs.autojs6.m3g1singlefilesmoke"
        const val DIRECTORY_OUTPUT_APK = "m3-g1-directory-runtime-smoke.apk"
        const val SINGLE_FILE_OUTPUT_APK = "m3-g1-single-file-runtime-smoke.apk"
        const val DIRECTORY_MARKER = "M3 G1 directory runtime smoke OK"
        const val SINGLE_FILE_MARKER = "M3 G1 single-file runtime smoke OK"
        const val DIRECTORY_SCRIPT_LOG_MARKER = "M3_G1_DIRECTORY_SCRIPT_EXECUTED"
        const val SINGLE_FILE_SCRIPT_LOG_MARKER = "M3_G1_SINGLE_FILE_SCRIPT_EXECUTED"
        const val PRIVATE_PATH_SENTINEL = "M3_G1_PRIVATE_SOURCE_PATH_DO_NOT_LOG"
        const val MIN_OUTPUT_BYTES = 1_000_000L
    }
}
