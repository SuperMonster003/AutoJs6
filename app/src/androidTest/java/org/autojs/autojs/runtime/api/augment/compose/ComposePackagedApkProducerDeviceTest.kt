package org.autojs.autojs.runtime.api.augment.compose

import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.apkbuilder.template.ApkBuilderPluginTrustPolicy
import org.autojs.autojs.apkbuilder.template.ApkBuilderTemplatePluginHost
import org.autojs.autojs.apkbuilder.template.RemoteApkBuildClient
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile

/**
 * Builds a real, separately signed script application through the production Binder builder.
 * The retained output belongs to this opt-in fixture; the device driver pulls and installs it.
 * No alternate runtime, rewritten Gradle applicationId, or synthetic APK replaces the builder.
 */
@RunWith(AndroidJUnit4::class)
class ComposePackagedApkProducerDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun producesVersionOneComposeApplicationFromThePairedRuntimeKit() {
        assumeTrue("Pass -e composePackagedBuild true to produce the disposable APK",
            InstrumentationRegistry.getArguments().getString("composePackagedBuild") == "true")
        assertTrue("Use the isolated debug host", BuildConfig.DEBUG && context.packageName.endsWith(".compose.spike"))
        val builderPackage = ApkBuilderTemplatePluginIds.PACKAGE_NAME
        context.packageManager.getApplicationInfo(builderPackage, 0)
        assertTrue("The actual APK Builder plugin must pass the normal certificate policy",
            ApkBuilderPluginTrustPolicy.isAccepted(context, builderPackage))
        val abi = InstrumentationRegistry.getArguments().getString("composePackagedAbi")
        assertTrue("Pass the intended runtime ABI explicitly through -e composePackagedAbi",
            abi in setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86"))
        val selectedAbi = requireNotNull(abi)
        assertTrue("The device must support the selected runtime ABI", selectedAbi in Build.SUPPORTED_ABIS)
        assertEquals("The runtime ABI must match the instrumented process bitness", Process.is64Bit(),
            selectedAbi == "arm64-v8a" || selectedAbi == "x86_64")
        val builder = runBlocking(Dispatchers.IO) {
            ApkBuilderTemplatePluginHost.discover(context)
        }.single { it.serviceInfo.packageName == builderPackage }
        assertNull("The real Builder discovery must succeed", builder.error)
        assertTrue("The real Runtime Kit must contain the explicitly selected ABI",
            selectedAbi in requireNotNull(builder.pluginInfo).supportedAbis.orEmpty())
        val root = File(context.cacheDir, "compose-packaged-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val output = File(requireNotNull(context.getExternalFilesDir(null)), OUTPUT_APK)
        val receiptFile = File(output.parentFile, "$OUTPUT_APK.json")
        if (output.exists()) check(output.delete()) { "Cannot replace the previous owned fixture output" }
        if (receiptFile.exists()) check(receiptFile.delete()) { "Cannot replace the previous owned fixture receipt" }
        var succeeded = false
        try {
            val config = ProjectConfig()
                .setName("Compose packaged P5")
                .setPackageName(OUTPUT_PACKAGE)
                .setVersionName("1.0.0")
                .setVersionCode(1)
                .setMainScriptFileName("main.js")
                .setProjectType("rhino")
                .setSourcePath(root.absolutePath)
                .setAbis(listOf(selectedAbi))
                .setLibs(emptyList())
                .setPermissions(emptyList())
            config.launchConfig.apply {
                isSplashVisible = false
                isLogsVisible = false
                isRunOnBoot = false
                requiresSharedStorage = false
            }
            File(root, "project.json").writeText(config.toJson(false), Charsets.UTF_8)
            instrumentation.context.assets.open("compose-packaged/main.js").use { input ->
                File(root, "main.js").outputStream().use { outputStream -> input.copyTo(outputStream) }
            }
            val progressCount = AtomicInteger()
            val outcome = RemoteApkBuildClient.buildBlocking(context, config, output, AtomicBoolean(false),
                RemoteApkBuildClient.ProgressListener { progressCount.incrementAndGet() }, builderPackage)
            assertTrue("Production APK Builder failed: errors=${outcome.errors}; warnings=${outcome.warnings}", outcome.isSuccess)
            assertTrue("The production builder emits progress", progressCount.get() > 0)
            assertTrue("The generated runtime APK exists", output.isFile && output.length() > 1_000_000)
            @Suppress("DEPRECATION")
            val archive = requireNotNull(context.packageManager.getPackageArchiveInfo(output.path,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES))
            assertEquals(OUTPUT_PACKAGE, archive.packageName)
            assertEquals(1L, PackageInfoCompat.getLongVersionCode(archive))
            assertEquals("1.0.0", archive.versionName)
            assertFalse("A user application must not redeclare the host's signature permission",
                archive.permissions.orEmpty().any { it.name == "org.autojs.permission.PLUGIN" })
            assertFalse("The app does not borrow the host's signature permission",
                archive.requestedPermissions.orEmpty().contains("org.autojs.permission.PLUGIN"))
            ZipFile(output).use { zip ->
                assertNotNull(zip.getEntry("classes.dex"))
                assertNotNull(zip.getEntry("assets/project/main.js"))
                val nativeAbis = zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith("lib/") }
                    .map { it.name.substringAfter("lib/").substringBefore('/') }.toSet()
                assertEquals("The generated APK keeps only the explicitly selected runtime ABI", setOf(selectedAbi), nativeAbis)
                val project = JSONObject(zip.getInputStream(requireNotNull(zip.getEntry("assets/project/project.json")))
                    .bufferedReader(Charsets.UTF_8).use { it.readText() })
                assertEquals(OUTPUT_PACKAGE, project.getString("packageName"))
                assertEquals(1, project.getInt("versionCode"))
            }
            val receipt = JSONObject().put("packageName", OUTPUT_PACKAGE).put("versionCode", 1)
                .put("abi", selectedAbi)
                .put("runtimeVersionCode", BuildConfig.VERSION_CODE).put("bytes", output.length())
                .put("sha256", sha256(output)).put("progressEvents", progressCount.get())
                .put("warnings", outcome.warnings)
            receiptFile.writeText(receipt.toString(2) + "\n", Charsets.UTF_8)
            println("compose.p5.packagedApk=$receipt")
            succeeded = true
        } finally {
            check(root.canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator))
            root.deleteRecursively()
            if (!succeeded) { output.delete(); receiptFile.delete() }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    companion object {
        const val OUTPUT_PACKAGE = "org.autojs.autojs6.compose.packaged.p5"
        const val OUTPUT_APK = "compose-p5-packaged.apk"
    }
}
