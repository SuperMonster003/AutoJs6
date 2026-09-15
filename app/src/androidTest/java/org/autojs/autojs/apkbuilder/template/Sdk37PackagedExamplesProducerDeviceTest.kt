package org.autojs.autojs.apkbuilder.template

import android.content.res.AssetManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.project.LaunchConfig
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs6.R
import org.autojs.plugin.apkbuilder.template.ApkBuildResult
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Opt-in producer for Roadmap P3.4: packages the three SDK 37 example projects under
 * `androidTest/assets/sdk37/packaged` through the installed APK Builder plugin, exactly as the
 * host's build screen would, and keeps the signed APKs in external files.
 * `tools/sdk37/run_packaged_examples.py` pulls, inspects, installs and drives them.
 *
 * zh-CN: Roadmap P3.4 的可选生产测试: 通过已安装的 APK Builder 插件, 按宿主打包页面的方式打包
 * `androidTest/assets/sdk37/packaged` 下的三个 SDK 37 示例项目, 签名后的 APK 保留在外部文件目录.
 * 由 `tools/sdk37/run_packaged_examples.py` 拉取、核对、安装并驱动.
 */
class Sdk37PackagedExamplesProducerDeviceTest {

    private class Example(val name: String, val logsVisible: Boolean)

    @Test
    fun producesPackagedExamples() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("autojs.sdk37.produceExamples").toBoolean())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val assets = instrumentation.context.assets
        val previousGate = Pref.getBoolean(R.string.key_apk_builder_remote_build_enabled, false)
        val root = File(context.cacheDir, "sdk37-packaged").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            Pref.putBooleanSync(R.string.key_apk_builder_remote_build_enabled, true)
            for (example in EXAMPLES) {
                val source = File(root, example.name).apply { mkdirs() }
                copyAssetDirectory(assets, "$ASSET_ROOT/${example.name}", source)
                for (shared in SHARED_FILES) {
                    copyAssetFile(assets, "$ASSET_ROOT/$shared", File(source, shared))
                }
                val config = ProjectConfig()
                    .setName("SDK37 ${example.name}")
                    .setPackageName("$PACKAGE_PREFIX.${example.name}")
                    .setVersionName("1.0")
                    .setVersionCode(1)
                    .setMainScriptFileName("main.js")
                    .setProjectType("rhino")
                    .setSourcePath(source.absolutePath)
                    .setAbis(listOf(Build.SUPPORTED_ABIS.first()))
                    .setLibs(emptyList())
                    .setPermissions(PERMISSIONS)
                config.setLaunchConfig(LaunchConfig().apply {
                    isSplashVisible = false
                    isLogsVisible = example.logsVisible
                    isLauncherVisible = true
                    isRunOnBoot = false
                })
                File(source, "project.json").writeText(config.toJson(false))
                val output = File(context.getExternalFilesDir(null), "sdk37-example-${example.name}.apk")
                output.delete()
                val outcome = RemoteApkBuildClient.buildBlocking(
                    context, config, output, cancelSignal = null,
                    preferredPluginPackageName = ApkBuilderTemplatePluginIds.PACKAGE_NAME,
                )
                assertEquals("${example.name}: errors=${outcome.errors} warnings=${outcome.warnings}", ApkBuildResult.STATUS_OK, outcome.status)
                assertTrue("${example.name}: ${outcome.errors}", outcome.errors.isEmpty())
                assertTrue("${example.name}: empty output", output.length() > 0)
            }
        } finally {
            Pref.putBooleanSync(R.string.key_apk_builder_remote_build_enabled, previousGate)
            root.deleteRecursively()
        }
    }

    private fun copyAssetDirectory(assets: AssetManager, path: String, target: File) {
        val names = assets.list(path) ?: emptyArray()
        assertTrue("No assets under $path", names.isNotEmpty())
        for (name in names) {
            copyAssetFile(assets, "$path/$name", File(target, name))
        }
    }

    private fun copyAssetFile(assets: AssetManager, path: String, target: File) {
        assets.open(path).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private companion object {
        const val ASSET_ROOT = "sdk37/packaged"
        const val PACKAGE_PREFIX = "org.autojs.autojs6.sdk37"
        val SHARED_FILES = listOf("common.js", "sdk37-probe.dex")
        val EXAMPLES = listOf(
            Example("a11y", logsVisible = true),
            Example("ui", logsVisible = false),
            Example("timer", logsVisible = true),
        )

        /** The build screen's defaults plus what the examples observe (local network, exact alarms, package visibility). */
        val PERMISSIONS = (ProjectConfig.DEFAULT_PERMISSIONS + listOf(
            "android.permission.ACCESS_LOCAL_NETWORK",
            "android.permission.USE_EXACT_ALARM",
            "android.permission.SCHEDULE_EXACT_ALARM",
            "android.permission.QUERY_ALL_PACKAGES",
        )).distinct()
    }
}
