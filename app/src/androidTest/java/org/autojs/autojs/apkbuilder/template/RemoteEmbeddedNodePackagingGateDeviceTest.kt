package org.autojs.autojs.apkbuilder.template

import android.util.Log
import android.os.Build
import org.autojs.plugin.apkbuilder.template.ApkBuildResult
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.autojs.plugin.nodejs.api.NodeJsPluginActions
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    fun legacyEmbeddedLibraryStopsBeforeRequestStaging() {
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

            listOf(baseConfig.setLibs(listOf("  EMBEDDED-NODEJS  "))).forEach { config ->
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
                "M3_G3_NODE_GATE_RESULT rejected=1 progress_count=0 staging_created=false",
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

    @Test
    fun nodeProjectBuildsAnIndependentPluginClientApk() {
        requireQualificationEnvironment()
        val arguments = InstrumentationRegistry.getArguments()
        val port = arguments.getString("node.packaged.resultPort")?.toInt() ?: 58424
        val targetAbi = arguments.getString("node.packaged.targetAbi") ?: Build.SUPPORTED_ABIS.first()
        val singleFile = arguments.getString("node.packaged.sourceKind") == "file"
        val root = File(context.cacheDir, "m24-node-source").apply { mkdirs() }
        val output = File(context.getExternalFilesDir(null), "m24-node-packaged.apk")
        try {
            val entry = File(root, if (singleFile) "main.js" else "main.cjs")
            entry.writeText(
                """
                "nodejs";
                const http = require('node:http');
                const fs = require('node:fs');
                function report(value) {
                  console.log('M24_PACKAGED_RESULT ' + JSON.stringify(value));
                  fs.writeFileSync('m24-result.json', JSON.stringify(value));
                  const req = http.request({host:'127.0.0.1', port:$port, path:'/m24', method:'POST'}, r => r.resume());
                  req.on('error', e => console.error(e.message));
                  req.end(JSON.stringify(value));
                }
                (async () => {
                  const info = await require('device').info();
                  const packaged = require('autojs6:profile').packageManagerProfile.packagedBehavior;
                  report({ok:true, marker:'M24_PACKAGED_NODE', answer:40+2, node:process.version, packaged, device:info});
                })().catch(error => report({ok:false, message:String(error), stack:error.stack}));
                """.trimIndent() + "\n",
            )
            val config = ProjectConfig()
                .setName("Node packaged smoke")
                .setPackageName(if (singleFile) "org.autojs.autojs6.m24nodefile" else "org.autojs.autojs6.m24nodepackaged")
                .setVersionName("1.0.0")
                .setVersionCode(1)
                .setMainScriptFileName(entry.name)
                .setProjectType(if (singleFile) "rhino" else "node")
                .setSourcePath(if (singleFile) entry.absolutePath else root.absolutePath)
                .setAbis(listOf(targetAbi))
                .setLibs(emptyList())
                .setPermissions(emptyList())
                .setNodeConfig(ProjectConfig.NodeConfig().setPermissions(listOf("device")))
            config.launchConfig.requiresSharedStorage = false
            config.buildInfo.apply {
                buildId = "M24-NODE-${System.currentTimeMillis()}"
                buildNumber = 1L
                buildTime = 1L
            }
            if (!singleFile) File(root, "project.json").writeText(config.toJson(false))
            if (!singleFile) File(root, "package.json").writeText("""{"name":"m24-node-packaged","version":"1.0.0","type":"commonjs","main":"main.cjs"}""")
            PluginEnableStore.setEnabled(context, ApkBuilderTemplatePluginIds.PACKAGE_NAME, true)
            org.autojs.autojs.core.plugin.center.PluginWakeManager.tryAutoWakeIfNeeded(
                context, ApkBuilderTemplatePluginIds.PACKAGE_NAME)
            Thread.sleep(500L)
            val result = RemoteApkBuildClient.buildBlocking(
                context = context,
                projectConfig = config,
                outApk = output,
                cancelSignal = AtomicBoolean(false),
                preferredPluginPackageName = ApkBuilderTemplatePluginIds.PACKAGE_NAME,
            )
            assertEquals("Remote build failed: errors=${result.errors}, warnings=${result.warnings}", ApkBuildResult.STATUS_OK, result.status)
            assertTrue(output.isFile)
            @Suppress("DEPRECATION")
            val info = requireNotNull(context.packageManager.getPackageArchiveInfo(output.path,
                android.content.pm.PackageManager.GET_PERMISSIONS or android.content.pm.PackageManager.GET_SIGNATURES))
            assertTrue(info.requestedPermissions.orEmpty().contains(NodeJsPluginActions.PACKAGED_RUNTIME_PERMISSION))
            @Suppress("DEPRECATION")
            val hostInfo = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            assertFalse("Generated app must have an independent signer", info.signatures.contentEquals(hostInfo.signatures))
            ZipFile(output).use { apk ->
                assertFalse("External runtime must not be embedded", apk.entries().asSequence().any { it.name.endsWith("/libnode.so") })
            }
            Log.i(TAG, "M24_NODE_APK ${output.path} bytes=${output.length()}")
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun nodeProjectKeepsItsRuntimeLibrarySelectedInTheBuildScreen() {
        requireQualificationEnvironment()
        val root = File(context.cacheDir, "m24-node-build-ui").apply { mkdirs() }
        try {
            File(root, "main.cjs").writeText("console.log(42);\n")
            val config = ProjectConfig().setName("Node UI smoke")
                .setPackageName("org.autojs.autojs6.m24nodeui")
                .setVersionName("1.0.0").setVersionCode(1)
                .setProjectType("node").setMainScriptFileName("main.cjs")
                .setSourcePath(root.path).setLibs(emptyList())
            File(root, "project.json").writeText(config.toJson(false))
            val intent = android.content.Intent(context, org.autojs.autojs.ui.project.BuildActivity::class.java)
                .putExtra(org.autojs.autojs.ui.project.BuildActivity.EXTRA_SOURCE, root.path)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            androidx.test.core.app.ActivityScenario.launch<org.autojs.autojs.ui.project.BuildActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    fun find(view: android.view.View): org.autojs.autojs.ui.widget.RoundCheckboxWithText? {
                        if (view is org.autojs.autojs.ui.widget.RoundCheckboxWithText &&
                            view.getText()?.toString() == ApkBuildLibrary.NODEJS_RUNTIME.label) return view
                        if (view is android.view.ViewGroup) {
                            for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                        }
                        return null
                    }
                    val runtime = requireNotNull(find(activity.window.decorView))
                    assertTrue("Node projects must select their runtime", runtime.isChecked())
                    assertFalse("A required runtime must not be deselected", runtime.isEnabled)
                }
            }
        } finally {
            root.deleteRecursively()
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
