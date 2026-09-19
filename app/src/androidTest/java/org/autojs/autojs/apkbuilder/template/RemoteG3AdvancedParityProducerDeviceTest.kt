package org.autojs.autojs.apkbuilder.template

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.apkbuilder.ApkBuilder
import org.autojs.autojs.core.plugin.typescript.TypeScriptApkBuildCompiler
import org.autojs.autojs.project.LaunchConfig
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs6.BuildConfig
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

/**
 * Opt-in remote producer for the TypeScript semantic and ImageQuant native G3 gates.
 *
 * The TypeScript artifact is compiled from real TSX through the official compiler Binder before
 * entering the real APK Builder Binder transaction. ImageQuant uses the same JavaScript fixture as
 * the frozen local builder and requests the official native packaging variant.
 */
@RunWith(AndroidJUnit4::class)
class RemoteG3AdvancedParityProducerDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun producesCompiledTsxAndSelfContainedImageQuantApks() {
        requireQualificationEnvironment()

        val outputDirectory = requireNotNull(context.getExternalFilesDir(null))
        val specs = listOf(
            BuildSpec(
                kind = Kind.TYPESCRIPT,
                versionCode = TYPESCRIPT_REMOTE_VERSION,
                output = File(outputDirectory, TYPESCRIPT_OUTPUT_NAME),
            ),
            BuildSpec(
                kind = Kind.IMAGE_QUANT,
                versionCode = IMAGE_QUANT_REMOTE_VERSION,
                output = File(outputDirectory, IMAGE_QUANT_OUTPUT_NAME),
            ),
        )
        specs.forEach { spec ->
            if (spec.output.exists()) {
                check(spec.output.delete()) { "Unable to remove stale advanced-parity output." }
            }
        }

        val root = File(context.cacheDir, PRIVATE_ROOT).apply {
            deleteRecursively()
            check(mkdirs())
        }
        var succeeded = false
        var primaryFailure: Throwable? = null
        try {
            RemoteApkBuildDiagnostics.resetForTest()
            val observations = specs.map { spec -> buildOne(root, spec) }

            assertFalse(
                "Remote advanced production leaked host request staging",
                File(context.cacheDir, HOST_STAGING_DIRECTORY).listFiles().orEmpty().any(),
            )
            assertFalse(
                "Remote advanced production leaked TypeScript transport archives",
                context.cacheDir.listFiles().orEmpty().any { file ->
                    file.name.startsWith("typescript-input-") ||
                        file.name.startsWith("typescript-output-")
                },
            )
            val diagnostics = RemoteApkBuildDiagnostics.snapshot()
            assertEquals("Expected one terminal diagnostic per advanced output", specs.size, diagnostics.size)
            diagnostics.forEach { event ->
                assertEquals(RemoteApkBuildDiagnosticStatus.SUCCEEDED, event.status)
                assertEquals(RemoteApkBuildDiagnosticPhase.COMPLETE, event.phase)
                assertEquals(RemoteApkBuildErrorCategory.NONE, event.errorCategory)
            }

            Log.i(
                TAG,
                "M3_G3_REMOTE_ADVANCED_RESULT status=PASS builds=${observations.size} " +
                    observations.joinToString(" ") { observation ->
                        "kind=${observation.kind.id} bytes=${observation.bytes} " +
                            "sha256=${observation.sha256} build=${observation.buildNumber}"
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
            if (primaryFailure == null && cleanupFailure != null) throw cleanupFailure
            if (primaryFailure != null && cleanupFailure != null) {
                primaryFailure.addSuppressed(cleanupFailure)
            }
        }
        assertFalse("Remote advanced-parity private root survived cleanup", root.exists())
    }

    @Suppress("DEPRECATION")
    private fun buildOne(root: File, spec: BuildSpec): Observation {
        val project = File(root, "project-${spec.kind.id}").apply { check(mkdirs()) }
        val workspace = File(root, "compat-workspace-${spec.kind.id}")
        val config = createConfig(project, spec)
        File(project, ProjectConfig.CONFIG_FILE_NAME).writeText(config.toJson(false), Charsets.UTF_8)
        File(project, spec.kind.sourceFileName).writeText(sourceFor(spec.kind), Charsets.UTF_8)

        ApkBuilder(null, spec.output, workspace.absolutePath)
            .setCancelSignal(AtomicBoolean(false))
            .prepare(context)
            .withConfig(context, config)
            .build(context)
            .sign(context)
            .commitProjectConfigIfNeeded(context)
            .cleanWorkspace(context)

        assertTrue("Remote ${spec.kind.id} output is absent", spec.output.isFile)
        assertTrue("Remote ${spec.kind.id} output is unexpectedly small", spec.output.length() > MIN_OUTPUT_BYTES)
        assertFalse("Compatibility facade unexpectedly created its legacy workspace", workspace.exists())

        val expectedBuildNumber = spec.versionCode.toLong()
        val persisted = requireNotNull(ProjectConfig.fromFile(File(project, ProjectConfig.CONFIG_FILE_NAME)))
        assertEquals(expectedBuildNumber, persisted.buildInfo.buildNumber)
        assertTrue(persisted.buildInfo.buildTime > INPUT_BUILD_TIME)
        assertTrue(persisted.buildInfo.buildId.endsWith("-$expectedBuildNumber"))

        assertPackagedOutput(spec)
        val archiveInfo = context.packageManager.getPackageArchiveInfo(
            spec.output.absolutePath,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES,
        )
        assertNotNull("PackageManager could not parse remote ${spec.kind.id}", archiveInfo)
        requireNotNull(archiveInfo)
        assertEquals(spec.kind.packageName, archiveInfo.packageName)
        assertEquals(versionName(spec.versionCode), archiveInfo.versionName)
        assertEquals(spec.versionCode, archiveInfo.versionCode)

        return Observation(
            kind = spec.kind,
            bytes = spec.output.length(),
            sha256 = sha256(spec.output),
            buildNumber = persisted.buildInfo.buildNumber,
        )
    }

    private fun createConfig(project: File, spec: BuildSpec): ProjectConfig {
        val launch = LaunchConfig().apply {
            isLogsVisible = false
            isSplashVisible = true
            isLauncherVisible = true
            slug = spec.kind.splashSlug
        }
        return ProjectConfig()
            .setName(spec.kind.appName)
            .setPackageName(spec.kind.packageName)
            .setVersionName(versionName(spec.versionCode))
            .setVersionCode(spec.versionCode)
            .setMainScriptFileName(spec.kind.sourceFileName)
            .setProjectType("rhino")
            .setSourcePath(project.absolutePath)
            .setAbis(listOf(Build.SUPPORTED_ABIS.first()))
            .setLibs(if (spec.kind == Kind.IMAGE_QUANT) listOf(IMAGE_QUANT_LABEL) else emptyList())
            .setPermissions(PERMISSIONS)
            .setSignatureScheme(SIGNATURE_SCHEME)
            .setIconGetter { qualificationIcon(spec.kind) }
            .also { config ->
                config.setLaunchConfig(launch)
                config.buildInfo.apply {
                    buildId = "M3-G3-REMOTE-${spec.kind.id}-${spec.versionCode - 1}"
                    buildNumber = spec.versionCode.toLong() - 1L
                    buildTime = INPUT_BUILD_TIME
                }
            }
    }

    private fun assertPackagedOutput(spec: BuildSpec) {
        ZipFile(spec.output).use { zip ->
            val names = Collections.list(zip.entries()).mapTo(linkedSetOf()) { entry -> entry.name }
            val bundled = JSONObject(
                zip.getInputStream(requireNotNull(zip.getEntry(PROJECT_CONFIG_ENTRY)))
                    .bufferedReader()
                    .use { it.readText() },
            )
            assertEquals(spec.kind.appName, bundled.getString("name"))
            assertEquals(spec.kind.packageName, bundled.getString("packageName"))
            assertEquals(versionName(spec.versionCode), bundled.getString("versionName"))
            assertEquals(spec.versionCode, bundled.getInt("versionCode"))
            assertEquals(spec.versionCode.toLong(), bundled.getJSONObject("build").getLong("number"))
            assertEquals(SIGNATURE_SCHEME, bundled.getString("signatureScheme"))

            when (spec.kind) {
                Kind.TYPESCRIPT -> {
                    assertEquals(0, bundled.getJSONArray("libs").length())
                    assertEquals(TypeScriptApkBuildCompiler.RHINO_LAUNCHER_PATH, bundled.getString("main"))
                    assertTrue("assets/project/main.js" in names)
                    assertTrue("assets/project/main.js.map" in names)
                    assertTrue(
                        "assets/project/${TypeScriptApkBuildCompiler.RHINO_LAUNCHER_PATH}" in names,
                    )
                    assertTrue(TYPESCRIPT_COMPILATION_MANIFEST_ENTRY in names)
                    assertFalse(
                        names.any { name ->
                            name.startsWith("assets/project/") &&
                                (name.endsWith(".ts") || name.endsWith(".tsx"))
                        },
                    )
                    assertFalse(IMAGE_QUANT_LIBRARY_ENTRY in names)

                    val sourceMap = JSONObject(
                        zip.getInputStream(requireNotNull(zip.getEntry("assets/project/main.js.map")))
                            .bufferedReader()
                            .use { it.readText() },
                    )
                    assertEquals(3, sourceMap.getInt("version"))
                    assertFalse(sourceMap.has("sourcesContent"))
                    assertTrue(
                        (0 until sourceMap.getJSONArray("sources").length()).any { index ->
                            sourceMap.getJSONArray("sources").getString(index).endsWith("main.tsx")
                        },
                    )

                    val compilationManifest = JSONObject(
                        zip.getInputStream(requireNotNull(zip.getEntry(TYPESCRIPT_COMPILATION_MANIFEST_ENTRY)))
                            .bufferedReader()
                            .use { it.readText() },
                    )
                    assertEquals("autojs6.typescript.apk-compilation", compilationManifest.getString("schema"))
                    assertEquals("emitted", compilationManifest.getString("outcome"))
                    assertEquals("main.js", compilationManifest.getString("entryJavaScriptPath"))
                    assertEquals(
                        TypeScriptApkBuildCompiler.RHINO_LAUNCHER_PATH,
                        compilationManifest.getString("launchScriptPath"),
                    )
                    assertEquals(1, compilationManifest.getJSONArray("modules").length())
                }

                Kind.IMAGE_QUANT -> {
                    assertEquals(MAIN_JAVASCRIPT, bundled.getString("main"))
                    assertEquals(IMAGE_QUANT_LABEL, bundled.getJSONArray("libs").getString(0))
                    assertTrue("assets/project/$MAIN_JAVASCRIPT" in names)
                    assertTrue(IMAGE_QUANT_LIBRARY_ENTRY in names)
                    REQUIRED_IMAGE_QUANT_LICENSES.forEach { name -> assertTrue(name, name in names) }
                    assertFalse(names.any { name -> FORBIDDEN_IMAGE_QUANT_LIBRARY_NAMES.any(name::endsWith) })
                    val nativeAbis = names.asSequence()
                        .filter { name -> name.startsWith("lib/") && name.endsWith(".so") }
                        .map { name -> name.split('/')[1] }
                        .toSet()
                    assertEquals(setOf("x86_64"), nativeAbis)
                }
            }
        }
    }

    private fun sourceFor(kind: Kind): String = when (kind) {
        Kind.TYPESCRIPT -> """
            "ui";

            type ParityValues = ReadonlyArray<number>;
            const values: ParityValues = [4, 8, 15, 16, 23, 42];
            const checksum: number = values.reduce((sum: number, value: number) => sum + value, 0);
            if (checksum !== 108) {
                throw new Error("G3 TypeScript semantic checksum failed");
            }
            const marker: string = "$TYPESCRIPT_RUNTIME_MARKER";
            ui.layout(
                <vertical gravity="center" padding={24}>
                    <text id="result" textSize="22sp">{marker}</text>
                </vertical>,
            );
            console.log("$TYPESCRIPT_LOG_MARKER checksum=" + checksum);
        """.trimIndent() + "\n"

        Kind.IMAGE_QUANT -> imageQuantSource()
    }

    private fun imageQuantSource(): String = """
        "ui";

        const source = images.fromBase64("$SOURCE_PNG_BASE64");
        const encoded = images.compressToBytes(source, "png", 80);
        const u8 = index => encoded[index] & 0xff;
        const signature = [137, 80, 78, 71, 13, 10, 26, 10];
        if (encoded.length <= 32 || signature.some((value, index) => u8(index) !== value)) {
            throw new Error("G3 ImageQuant PNG signature failed");
        }
        const pngWidth = (u8(16) << 24) | (u8(17) << 16) | (u8(18) << 8) | u8(19);
        const pngHeight = (u8(20) << 24) | (u8(21) << 16) | (u8(22) << 8) | u8(23);
        if (pngWidth !== 16 || pngHeight !== 16 || u8(25) !== 3) {
            throw new Error("G3 ImageQuant output is not a 16x16 indexed PNG");
        }
        let checksum = 0;
        for (let index = 0; index < encoded.length; index += 1) {
            checksum = (checksum + u8(index) * (index + 1)) % 1000000007;
        }
        const marker = "$IMAGE_QUANT_RUNTIME_MARKER";
        ui.layout(
            <vertical gravity="center" padding="24">
                <text id="result" gravity="center" text={marker} textSize="22sp" />
            </vertical>
        );
        console.log("$IMAGE_QUANT_LOG_MARKER bytes=" + encoded.length + " checksum=" + checksum);
    """.trimIndent() + "\n"

    private fun qualificationIcon(kind: Kind): Bitmap =
        Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(if (kind == Kind.IMAGE_QUANT) Color.rgb(60, 28, 92) else Color.rgb(20, 70, 100))
            for (index in 0 until 64) {
                setPixel(index, index, Color.rgb(255, 196, 0))
                setPixel(63 - index, index, Color.rgb(0, 220, 180))
            }
        }

    private fun versionName(versionCode: Int): String = "3.1.$versionCode"

    private fun requireQualificationEnvironment() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "G3 remote advanced producer is opt-in; pass -e $ARG_ENABLED true",
            args.getString(ARG_ENABLED) == "true",
        )
        assertTrue("G3 remote advanced producer requires a debug host", BuildConfig.DEBUG)
        assertEquals(HOST_PACKAGE, context.packageName)
        assertEquals(APK_BUILDER_PLUGIN_PACKAGE, args.getString(ARG_APK_BUILDER_PLUGIN_PACKAGE))
        assertEquals(TYPESCRIPT_PLUGIN_PACKAGE, args.getString(ARG_TYPESCRIPT_PLUGIN_PACKAGE))
        assertEquals(IMAGE_QUANT_PLUGIN_PACKAGE, args.getString(ARG_IMAGE_QUANT_PLUGIN_PACKAGE))

        val expectedSerial = args.getString(ARG_DEVICE_SERIAL).orEmpty().trim()
        assertTrue("Pass the exact device ro.serialno", expectedSerial.isNotEmpty())
        val actualSerial = executeShellCommand("getprop ro.serialno").trim().ifEmpty {
            executeShellCommand("getprop ro.boot.serialno").trim()
        }
        assertEquals("Instrumentation is running on a different device", expectedSerial, actualSerial)

        listOf(
            APK_BUILDER_PLUGIN_PACKAGE,
            TYPESCRIPT_PLUGIN_PACKAGE,
            IMAGE_QUANT_PLUGIN_PACKAGE,
        ).forEach { packageName ->
            try {
                context.packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
            } catch (error: PackageManager.NameNotFoundException) {
                throw AssertionError("Required qualification plugin is not installed: $packageName").also {
                    it.initCause(error)
                }
            }
            assertEquals(
                "The debug host and $packageName must have the same signer",
                PackageManager.SIGNATURE_MATCH,
                context.packageManager.checkSignatures(HOST_PACKAGE, packageName),
            )
        }

        val discovered = runBlocking(Dispatchers.IO) {
            ApkBuilderTemplatePluginHost.discover(context)
        }.single { item -> item.serviceInfo.packageName == APK_BUILDER_PLUGIN_PACKAGE }
        assertEquals(null, discovered.error)
        val capabilities = requireNotNull(requireNotNull(discovered.pluginInfo).capabilities)
        assertTrue(
            "Qualification APK Builder plugin does not advertise remote-build support",
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

    private enum class Kind(
        val id: String,
        val sourceFileName: String,
        val packageName: String,
        val appName: String,
        val splashSlug: String,
    ) {
        TYPESCRIPT(
            id = "typescript",
            sourceFileName = "main.tsx",
            packageName = TYPESCRIPT_PACKAGE,
            appName = "M3 G3 TypeScript parity",
            splashSlug = "G3 TypeScript semantic parity",
        ),
        IMAGE_QUANT(
            id = "imagequant",
            sourceFileName = MAIN_JAVASCRIPT,
            packageName = IMAGE_QUANT_PACKAGE,
            appName = "M3 G3 ImageQuant parity",
            splashSlug = "G3 ImageQuant native parity",
        ),
    }

    private data class BuildSpec(
        val kind: Kind,
        val versionCode: Int,
        val output: File,
    )

    private data class Observation(
        val kind: Kind,
        val bytes: Long,
        val sha256: String,
        val buildNumber: Long,
    )

    private companion object {
        const val TAG = "RemoteG3AdvancedParity"
        const val ARG_ENABLED = "autojs.apkBuilder.g3.remoteAdvanced.enabled"
        const val ARG_DEVICE_SERIAL = "autojs.apkBuilder.g3.remoteAdvanced.serial"
        const val ARG_APK_BUILDER_PLUGIN_PACKAGE = "autojs.apkBuilder.g3.remoteAdvanced.apkBuilderPackage"
        const val ARG_TYPESCRIPT_PLUGIN_PACKAGE = "autojs.apkBuilder.g3.remoteAdvanced.typescriptPackage"
        const val ARG_IMAGE_QUANT_PLUGIN_PACKAGE = "autojs.apkBuilder.g3.remoteAdvanced.imageQuantPackage"

        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val APK_BUILDER_PLUGIN_PACKAGE = ApkBuilderTemplatePluginIds.PACKAGE_NAME
        const val TYPESCRIPT_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.typescript"
        const val IMAGE_QUANT_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.imagequant"
        const val PRIVATE_ROOT = "m3-g3-remote-advanced"
        const val HOST_STAGING_DIRECTORY = "apk-remote-build-input"
        const val TYPESCRIPT_PACKAGE = "org.autojs.autojs6.m3g3typescript"
        const val IMAGE_QUANT_PACKAGE = "org.autojs.autojs6.m3g3imagequant"
        const val TYPESCRIPT_OUTPUT_NAME = "m3-g3-typescript-remote-v301.apk"
        const val IMAGE_QUANT_OUTPUT_NAME = "m3-g3-imagequant-remote-v401.apk"
        const val TYPESCRIPT_REMOTE_VERSION = 301
        const val IMAGE_QUANT_REMOTE_VERSION = 401
        const val MAIN_JAVASCRIPT = "main.js"
        const val PROJECT_CONFIG_ENTRY = "assets/project/project.json"
        const val TYPESCRIPT_COMPILATION_MANIFEST_ENTRY = "assets/project/.autojs6/typescript-compilation.json"
        const val SIGNATURE_SCHEME = "V2"
        const val IMAGE_QUANT_LABEL = "Image Quantization"
        const val IMAGE_QUANT_LIBRARY_ENTRY = "lib/x86_64/libpngquant_bridge.so"
        const val TYPESCRIPT_RUNTIME_MARKER = "M3 G3 TypeScript semantic parity OK"
        const val TYPESCRIPT_LOG_MARKER = "M3_G3_TYPESCRIPT_SEMANTIC_PARITY_EXECUTED"
        const val IMAGE_QUANT_RUNTIME_MARKER = "M3 G3 ImageQuant native parity OK"
        const val IMAGE_QUANT_LOG_MARKER = "M3_G3_IMAGEQUANT_NATIVE_PARITY_EXECUTED"
        const val INPUT_BUILD_TIME = 1L
        const val MIN_OUTPUT_BYTES = 1_000_000L
        const val SOURCE_PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAIrSURBVDhPFZNRFQAhCMCoYAUqWMEKVLCCv3xSwQpUsIIVrECF3bsI29tERLzJQWXSpTHkYrJ8irLkERJs6aSUH9lcGTyBkgQxF2mL1hRtj96C0bpbK2bbrDaIBrulZzNOE247vDap1px2EdGi6UZ10BUfmpgaU4Wlh9DpWxupl6OLq8rT56UB2hHpRuuC9uO9T0ZvWL/MvlhdPfpj9yB75/Ti9u2vD6oDPREZlzaW61D6eIwR2OjMUb7GJsZgD8iRnGF+h/DGocaE0RCx7s0KtU23wTAwS59mLBPCDtsmac2PXa4tnillDyxcZCZtGjqFPg9jTrfZmPOy5iKmsufznMGZnTuLNzc1hzNBZDXauuha9KU+1sNWMFdnrSLW9r0GueCs5C7jLfFaB9ZEJIIWHY3yHpsRAwuYkawwjxB2HDImJxo3rr9YVCjEQ2RD2+m6jb6FsQ+2J3M3X/sSe7G3kvtxdvjdnbeL2pvfrkhOb9nQvPRcjFQsn88MVnYii52bzOEn4Wby0qgUyOMi59FOoKfTTzHOdjuD+SOeJI6xj3iewzmTexrvXOos5ygid/xFojfp13xcwe5h3sm6jbjX913kVc593Bu8271uwd2IvEN7E33N+7uMt7CnzPdYLzxeZ78i3+a8wX34e0k9479KpJRWz7WCXp1RhdVm1vBVEJXsMrKEU8dvTV41qi7UQoTtjYH+aZMMDEN8clhMgsbmkiw/KJfHIyj+Gco/1epj09OOaAYAAAAASUVORK5CYII="
        val PERMISSIONS = listOf("android.permission.INTERNET")
        val REQUIRED_IMAGE_QUANT_LICENSES = setOf(
            "assets/licenses/imagequant/BUILD-SOURCE.md",
            "assets/licenses/imagequant/GPL-3.0.txt",
            "assets/licenses/imagequant/SOURCE-OFFER.md",
            "assets/licenses/imagequant/android-ndk-r26b-llvm-NOTICE",
            "assets/licenses/imagequant/libimagequant-COPYRIGHT",
            "assets/licenses/imagequant/libpng-LICENSE",
        )
        val FORBIDDEN_IMAGE_QUANT_LIBRARY_NAMES = setOf(
            "libimagequant.so",
            "libpng.so",
            "libpng16.so",
        )
    }
}
