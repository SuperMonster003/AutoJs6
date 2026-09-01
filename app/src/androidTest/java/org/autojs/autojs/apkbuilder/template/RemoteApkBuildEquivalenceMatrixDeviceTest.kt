package org.autojs.autojs.apkbuilder.template

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.apkbuilder.ApkBuilder
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.project.LaunchConfig
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateCapabilityKeys
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.json.JSONObject
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
import java.util.zip.ZipFile

/**
 * Opt-in producer for the remote half of the G3 install/upgrade equivalence matrix.
 *
 * The test deliberately uses the historical [ApkBuilder] fluent API so the compatibility adapter,
 * remote transaction, output validation, and project build-info commit all participate. Successful
 * APKs are retained in external files for an off-device static/runtime/upgrade verifier.
 */
@RunWith(AndroidJUnit4::class)
class RemoteApkBuildEquivalenceMatrixDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun producesRemoteUpgradeMatrixThroughCompatibilityFacade() {
        requireQualificationEnvironment()

        val previousGate = Pref.getBoolean(R.string.key_apk_builder_remote_build_enabled, false)
        assertFalse("G3 remote matrix must start with the host gate disabled", previousGate)
        val externalFiles = requireNotNull(context.getExternalFilesDir(null))
        val specs = listOf(
            BuildSpec(100, File(externalFiles, "m3-g3-ui-remote-v100.apk")),
            BuildSpec(101, File(externalFiles, "m3-g3-ui-remote-v101.apk")),
            BuildSpec(200, File(externalFiles, "m3-g3-ui-remote-v200.apk")),
            BuildSpec(202, File(externalFiles, "m3-g3-ui-remote-v202.apk")),
        )
        specs.forEach { spec ->
            if (spec.output.exists()) {
                check(spec.output.delete()) { "Unable to remove stale remote matrix output." }
            }
        }

        val root = File(context.cacheDir, PRIVATE_ROOT).apply {
            deleteRecursively()
            check(mkdirs())
        }
        var succeeded = false
        var primaryFailure: Throwable? = null
        try {
            Pref.putBooleanSync(R.string.key_apk_builder_remote_build_enabled, true)
            RemoteApkBuildDiagnostics.resetForTest()
            val observations = specs.map { spec -> buildOne(root, spec) }

            assertFalse(
                "Successful G3 production leaked host request staging",
                File(context.cacheDir, HOST_STAGING_DIRECTORY).listFiles().orEmpty().any(),
            )
            val diagnostics = RemoteApkBuildDiagnostics.snapshot()
            assertEquals("Expected one terminal diagnostic per G3 output", specs.size, diagnostics.size)
            diagnostics.forEach { event ->
                assertEquals(RemoteApkBuildDiagnosticStatus.SUCCEEDED, event.status)
                assertEquals(RemoteApkBuildDiagnosticPhase.COMPLETE, event.phase)
                assertEquals(RemoteApkBuildErrorCategory.NONE, event.errorCategory)
            }

            Log.i(
                TAG,
                "M3_G3_REMOTE_MATRIX_RESULT status=PASS builds=${observations.size} " +
                    observations.joinToString(" ") { observation ->
                        "v${observation.versionCode}_bytes=${observation.bytes} " +
                            "v${observation.versionCode}_sha256=${observation.sha256} " +
                            "v${observation.versionCode}_build=${observation.buildNumber}"
                    },
            )
            succeeded = true
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            var cleanupFailure: Throwable? = null
            runCatching { Pref.putBooleanSync(R.string.key_apk_builder_remote_build_enabled, previousGate) }
                .onFailure { cleanupFailure = it }
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
        assertFalse("G3 remote matrix private root survived cleanup", root.exists())
        assertEquals(previousGate, Pref.getBoolean(R.string.key_apk_builder_remote_build_enabled, false))
    }

    @Suppress("DEPRECATION")
    private fun buildOne(root: File, spec: BuildSpec): Observation {
        val project = File(root, "project-${spec.versionCode}").apply { check(mkdirs()) }
        val workspace = File(root, "compat-workspace-${spec.versionCode}")
        val config = createConfig(project, spec.versionCode)
        File(project, ProjectConfig.CONFIG_FILE_NAME).writeText(config.toJson(false), Charsets.UTF_8)
        File(project, MAIN_SCRIPT).writeText(runtimeScript(), Charsets.UTF_8)

        ApkBuilder(null, spec.output, workspace.absolutePath)
            .setCancelSignal(AtomicBoolean(false))
            .prepare(context)
            .withConfig(context, config)
            .build(context)
            .sign(context)
            .commitProjectConfigIfNeeded(context)
            .cleanWorkspace(context)

        assertTrue("Remote v${spec.versionCode} output is absent", spec.output.isFile)
        assertTrue("Remote v${spec.versionCode} output is unexpectedly small", spec.output.length() > MIN_OUTPUT_BYTES)
        assertFalse("Compatibility facade unexpectedly created its legacy workspace", workspace.exists())

        val expectedBuildNumber = spec.versionCode.toLong()
        val persisted = requireNotNull(ProjectConfig.fromFile(File(project, ProjectConfig.CONFIG_FILE_NAME)))
        assertEquals(expectedBuildNumber, persisted.buildInfo.buildNumber)
        assertTrue(persisted.buildInfo.buildTime > INPUT_BUILD_TIME)
        assertTrue(persisted.buildInfo.buildId.endsWith("-$expectedBuildNumber"))

        ZipFile(spec.output).use { zip ->
            val projectEntry = requireNotNull(zip.getEntry(PROJECT_CONFIG_ENTRY))
            val bundled = JSONObject(zip.getInputStream(projectEntry).bufferedReader().use { it.readText() })
            assertEquals(APP_NAME, bundled.getString("name"))
            assertEquals(PACKAGE_NAME, bundled.getString("packageName"))
            assertEquals(versionName(spec.versionCode), bundled.getString("versionName"))
            assertEquals(spec.versionCode, bundled.getInt("versionCode"))
            assertEquals(MAIN_SCRIPT, bundled.getString("main"))
            assertEquals(expectedBuildNumber, bundled.getJSONObject("build").getLong("number"))
            assertEquals(SIGNATURE_SCHEME, bundled.getString("signatureScheme"))
            assertNotNull(zip.getEntry("assets/project/$MAIN_SCRIPT"))
        }

        val archiveInfo = context.packageManager.getPackageArchiveInfo(
            spec.output.absolutePath,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES,
        )
        assertNotNull("PackageManager could not parse remote v${spec.versionCode}", archiveInfo)
        assertEquals(PACKAGE_NAME, requireNotNull(archiveInfo).packageName)
        assertEquals(versionName(spec.versionCode), archiveInfo.versionName)
        assertEquals(spec.versionCode, archiveInfo.versionCode)

        return Observation(
            versionCode = spec.versionCode,
            bytes = spec.output.length(),
            sha256 = sha256(spec.output),
            buildNumber = persisted.buildInfo.buildNumber,
        )
    }

    private fun createConfig(project: File, versionCode: Int): ProjectConfig {
        val launch = LaunchConfig().apply {
            isLogsVisible = false
            isSplashVisible = true
            isLauncherVisible = true
            slug = SPLASH_SLUG
        }
        return ProjectConfig()
            .setName(APP_NAME)
            .setPackageName(PACKAGE_NAME)
            .setVersionName(versionName(versionCode))
            .setVersionCode(versionCode)
            .setMainScriptFileName(MAIN_SCRIPT)
            .setProjectType("rhino")
            .setSourcePath(project.absolutePath)
            .setAbis(listOf(Build.SUPPORTED_ABIS.first()))
            .setLibs(emptyList())
            .setPermissions(PERMISSIONS)
            .setSignatureScheme(SIGNATURE_SCHEME)
            .setIconGetter { qualificationIcon() }
            .also { config ->
                config.setLaunchConfig(launch)
                config.buildInfo.apply {
                    buildId = "M3-G3-REMOTE-INPUT-${versionCode - 1}"
                    buildNumber = versionCode.toLong() - 1L
                    buildTime = INPUT_BUILD_TIME
                }
            }
    }

    private fun runtimeScript(): String = """
        "ui";

        const parityChecksum = [2, 3, 5, 7].reduce((sum, value) => sum + value, 0);
        if (parityChecksum !== 17) {
            throw new Error("G3 JavaScript parity computation failed");
        }
        ui.layout(
            <vertical gravity="center" padding="24">
                <text
                    id="result"
                    contentDescription="$RUNTIME_MARKER"
                    gravity="center"
                    text="$RUNTIME_MARKER"
                    textSize="22sp"
                />
            </vertical>
        );
        console.log("$LOG_MARKER");
    """.trimIndent() + "\n"

    private fun qualificationIcon(): Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.rgb(24, 48, 96))
        for (index in 0 until 64) {
            setPixel(index, index, Color.rgb(255, 196, 0))
            setPixel(63 - index, index, Color.rgb(0, 220, 180))
        }
    }

    private fun versionName(versionCode: Int): String = "3.0.$versionCode"

    private fun requireQualificationEnvironment() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "G3 remote matrix is opt-in; pass -e $ARG_ENABLED true",
            args.getString(ARG_ENABLED) == "true",
        )
        assertTrue("G3 remote matrix requires a debug host", BuildConfig.DEBUG)
        assertEquals(HOST_PACKAGE, context.packageName)
        assertEquals(PLUGIN_PACKAGE, args.getString(ARG_PLUGIN_PACKAGE))

        val expectedSerial = args.getString(ARG_DEVICE_SERIAL).orEmpty().trim()
        assertTrue("Pass the exact device ro.serialno", expectedSerial.isNotEmpty())
        val actualSerial = executeShellCommand("getprop ro.serialno").trim().ifEmpty {
            executeShellCommand("getprop ro.boot.serialno").trim()
        }
        assertEquals("Instrumentation is running on a different device", expectedSerial, actualSerial)

        try {
            context.packageManager.getApplicationInfo(PLUGIN_PACKAGE, PackageManager.MATCH_DISABLED_COMPONENTS)
        } catch (error: PackageManager.NameNotFoundException) {
            throw AssertionError("The real APK Builder plugin is not installed.").also { it.initCause(error) }
        }
        assertEquals(
            "The debug host and APK Builder plugin must have the same signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(HOST_PACKAGE, PLUGIN_PACKAGE),
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

    private fun executeShellCommand(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
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
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun appendFailure(existing: Throwable?, next: Throwable): Throwable {
        if (existing == null) return next
        existing.addSuppressed(next)
        return existing
    }

    private data class BuildSpec(val versionCode: Int, val output: File)

    private data class Observation(
        val versionCode: Int,
        val bytes: Long,
        val sha256: String,
        val buildNumber: Long,
    )

    private companion object {
        const val TAG = "RemoteApkBuildEquivalenceMatrix"
        const val ARG_ENABLED = "autojs.apkBuilder.g3.remoteMatrix.enabled"
        const val ARG_DEVICE_SERIAL = "autojs.apkBuilder.g3.remoteMatrix.serial"
        const val ARG_PLUGIN_PACKAGE = "autojs.apkBuilder.g3.remoteMatrix.pluginPackage"

        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val PLUGIN_PACKAGE = ApkBuilderTemplatePluginIds.PACKAGE_NAME
        const val PRIVATE_ROOT = "m3-g3-remote-matrix"
        const val HOST_STAGING_DIRECTORY = "apk-remote-build-input"
        const val PROJECT_CONFIG_ENTRY = "assets/project/project.json"
        const val PACKAGE_NAME = "org.autojs.autojs6.m3g3equivalence"
        const val APP_NAME = "M3 G3 builder equivalence"
        const val MAIN_SCRIPT = "main.js"
        const val SIGNATURE_SCHEME = "V2"
        const val SPLASH_SLUG = "G3 builder equivalence"
        const val RUNTIME_MARKER = "M3 G3 UI JavaScript parity OK"
        const val LOG_MARKER = "M3_G3_UI_JAVASCRIPT_PARITY_EXECUTED"
        const val INPUT_BUILD_TIME = 1L
        const val MIN_OUTPUT_BYTES = 1_000_000L
        val PERMISSIONS = listOf("android.permission.INTERNET", "android.permission.VIBRATE")
    }
}
