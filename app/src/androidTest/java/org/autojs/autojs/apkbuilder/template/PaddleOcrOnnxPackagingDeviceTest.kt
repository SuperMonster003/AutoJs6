package org.autojs.autojs.apkbuilder.template

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.pref.keys.PluginCenterKeys
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs6.R
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Produces a packaged APK for the Paddle OCR PP-OCRv3 on ONNX Runtime roadmap (plugin repository
 * AutoJs6-Plugin-Paddle-OCR-PP-OCRv3, ROADMAP.md P3.5).
 *
 * The test only records the build outcome and the plugins the packager selected, so expected
 * packaging failures (a Conflict, a missing OpenCV plugin) stay observable.
 * tools/paddle-ocr-v3/device_check.py inspects, installs and runs the produced APK.
 * The APK Builder template is enabled in the Plugin Center store for the build and restored afterwards.
 *
 * Arguments (all prefixed with `autojs.paddleOcrPackaging.`):
 * - `produceApk`: must be `true`, otherwise the test is skipped.
 * - `name`: output base name.
 * - `source`: device directory whose files (main.js and its samples) become the project.
 * - `libs`: comma-separated library labels, defaults to `Paddle OCR`.
 * - `enable`: comma-separated OCR plugin packages enabled during the build. Every other installed
 *   Paddle OCR / Rapid OCR package is disabled for the build and restored afterwards.
 * - `disable`: comma-separated packages also disabled during the build (for example the OpenCV plugin).
 * - `abi`: packaged ABI, defaults to the primary device ABI.
 */
class PaddleOcrOnnxPackagingDeviceTest {

    @Test
    fun producePackagedApk() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("$PREFIX.produceApk").toBoolean())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = arguments.getString("$PREFIX.name", "paddle-ocr-packaging").replace(Regex("[^A-Za-z0-9._-]"), "_")
        val sourceInput = File(requireNotNull(arguments.getString("$PREFIX.source")) { "$PREFIX.source is required" })
        val libs = arguments.argumentList("libs", "Paddle OCR")
        val enabled = arguments.argumentList("enable", "").toSet() + ApkBuilderTemplatePluginIds.PACKAGE_NAME
        val disabled = arguments.argumentList("disable", "").toSet()
        val abi = arguments.getString("$PREFIX.abi", Build.SUPPORTED_ABIS.first())

        val outputDir = File(context.getExternalFilesDir(null), "paddle-ocr-packaging").apply { mkdirs() }
        val output = File(outputDir, "$name.apk").apply { delete() }
        val report = File(outputDir, "$name.json").apply { delete() }
        val source = File(context.cacheDir, "paddle-ocr-packaging-project").apply {
            deleteRecursively()
            mkdirs()
        }

        @Suppress("DEPRECATION")
        val ocrPackages = context.packageManager.getInstalledPackages(0)
            .map { it.packageName }
            .filter { packageName -> OCR_PACKAGE_PREFIXES.any(packageName::startsWith) }
        val toggled = (ocrPackages + enabled + disabled).distinct()
        // The stored values (absent ones included) are put back as they were, not what isEnabled derives from them.
        // zh-CN: 按存储原值 (含缺失的键) 还原, 而不是 isEnabled 据此推出的结果.
        val enableState = context.getSharedPreferences(ENABLE_STATE_PREFERENCES, Context.MODE_PRIVATE)
        val previousState = toggled
            .flatMap { listOf(PluginCenterKeys.PLUGIN_ENABLED.key(it).name, PluginCenterKeys.PLUGIN_SUSPENDED.key(it).name) }
            .associateWith { key -> if (enableState.contains(key)) enableState.getBoolean(key, false) else null }
        val selected = JSONArray()
        val result = JSONObject()
            .put("name", name)
            .put("abi", abi)
            .put("libs", JSONArray(libs))
            .put("enabledPackages", JSONArray(enabled.sorted()))
            .put("disabledPackages", JSONArray((toggled - enabled).sorted()))
            .put("selected", selected)
            // The message prefix lets the caller recognize this error in any locale.
            .put("openCvRequiredPrefix", context.getString(R.string.error_apk_paddle_ocr_onnx_runtime_requires_opencv_plugin, MARKER).substringBefore(MARKER))
        try {
            val sourceFiles = sourceInput.listFiles()?.filter { it.isFile }.orEmpty()
            check(sourceFiles.any { it.name == "main.js" }) { "main.js not found in $sourceInput" }
            sourceFiles.forEach { it.copyTo(File(source, it.name), overwrite = true) }
            val config = ProjectConfig().setName("Paddle OCR packaging smoke")
                .setPackageName(PACKAGED_APP_PACKAGE_NAME).setVersionName("1.0").setVersionCode(1)
                .setMainScriptFileName("main.js").setProjectType("rhino").setSourcePath(source.absolutePath)
                .setAbis(listOf(abi))
                .setLibs(libs)
                .setPermissions(emptyList())
            File(source, "project.json").writeText(config.toJson(false))
            toggled.forEach { packageName ->
                PluginEnableStore.setEnabled(context, packageName, packageName in enabled)
            }
            val outcome = RemoteApkBuildClient.buildBlocking(
                context, config, output, cancelSignal = null,
                progressListener = { progress ->
                    if (progress.title == "Selected plugin") {
                        selected.put(progress.detail)
                        Log.i(TAG, "Selected plugin ${progress.detail}")
                    }
                },
                preferredPluginPackageName = ApkBuilderTemplatePluginIds.PACKAGE_NAME,
            )
            result.put("status", outcome.status)
                .put("success", outcome.isSuccess && output.isFile)
                .put("errors", JSONArray(outcome.errors))
                .put("warnings", JSONArray(outcome.warnings))
                .put("apkPath", output.absolutePath.takeIf { output.isFile })
            Log.i(TAG, "$name: status ${outcome.status}, errors ${outcome.errors.size}")
        } catch (error: Throwable) {
            result.put("success", false).put("exception", error.stackTraceToString())
        } finally {
            // PluginEnableStore writes asynchronously and the instrumentation kills the process right after the test,
            // so the previous values are committed here (an asynchronous write got lost on 2026-10-30).
            // zh-CN: PluginEnableStore 为异步写入, 测试结束后插测进程即被结束, 因此在此提交原值 (2026-10-30 曾丢失异步写入).
            enableState.edit().apply {
                previousState.forEach { (key, value) -> if (value == null) remove(key) else putBoolean(key, value) }
            }.commit()
            source.deleteRecursively()
            report.writeText(result.toString(2))
        }
    }

    private fun Bundle.argumentList(key: String, default: String): List<String> =
        getString("$PREFIX.$key", default).split(',').map(String::trim).filter(String::isNotEmpty)

    private companion object {

        const val PREFIX = "autojs.paddleOcrPackaging"
        const val TAG = "PaddleOcrPackaging"
        const val PACKAGED_APP_PACKAGE_NAME = "org.autojs.autojs6.paddleocrpackagingsmoke"
        const val MARKER = "@MARKER@"
        const val ENABLE_STATE_PREFERENCES = "plugin_center_enable_state"

        val OCR_PACKAGE_PREFIXES = listOf(
            "io.github.supermonster003.autojs6.plugin.paddleocr.",
            "io.github.supermonster003.autojs6.plugin.rapidocr.",
        )
    }
}
