package org.autojs.autojs.apkbuilder.template

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.apkbuilder.ApkBuildLibrary
import org.autojs.autojs.project.ProjectConfig
import org.autojs.plugin.apkbuilder.template.ApkBuildResult
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile

/**
 * Opt-in producer for roadmap P7.1 (`tools/augment/device_packaged_libraries_check.py` drives it): builds packaged apps
 * whose script uses dayjs, lodash, pangu and fs (and os through a computed name) with the real APK Builder plugin, keeps
 * them in the external files directory and logs, per app, the files under `assets/modules` and the notices of the
 * success dialog. Which libraries the build may copy depends on the Bundled Libraries plugin on the device; the driver
 * installs or removes it between runs.
 *
 * zh-CN: ROADMAP P7.1 的按需生产者 (由 `tools/augment/device_packaged_libraries_check.py` 驱动): 用真实的 APK Builder 插件打包应用,
 * 脚本用到 dayjs, lodash, pangu, fs (以及拼接名称的 os), 产物留在外部文件目录, 每个应用记录 `assets/modules` 下的文件与成功对话框的提示.
 * 构建能复制哪些库取决于设备上的 Bundled Libraries 插件, 由驱动脚本在两次运行之间安装或卸载.
 */
@RunWith(AndroidJUnit4::class)
class PackagedScriptModulesProducerDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun producesPackagedApps() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Opt-in; pass -e $ARG_APPS a,c", !arguments.getString(ARG_APPS).isNullOrBlank())
        val apps = arguments.getString(ARG_APPS)!!.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val externalFiles = requireNotNull(context.getExternalFilesDir(null))
        val root = File(context.cacheDir, "p71-sources").apply { deleteRecursively() }
        try {
            apps.forEach { app ->
                val includeAll = app == APP_ALL
                val source = File(root, app).apply { check(mkdirs()) }
                val output = File(externalFiles, "p71-$app.apk").apply { delete() }
                val config = ProjectConfig()
                    .setName("P71 ${app.uppercase()}")
                    .setPackageName("$PACKAGE_PREFIX$app")
                    .setVersionName("1.0.0")
                    .setVersionCode(1)
                    .setMainScriptFileName("main.js")
                    .setProjectType("rhino")
                    .setSourcePath(source.absolutePath)
                    .setAbis(listOf(Build.SUPPORTED_ABIS.first()))
                    .setLibs(if (includeAll) listOf(ApkBuildLibrary.BUNDLED_LIBRARIES.label) else emptyList())
                    .setPermissions(emptyList())
                File(source, "project.json").writeText(config.toJson(false), Charsets.UTF_8)
                File(source, "main.js").writeText(SCRIPT, Charsets.UTF_8)
                val outcome = RemoteApkBuildClient.buildBlocking(
                    context = context,
                    projectConfig = config,
                    outApk = output,
                    cancelSignal = AtomicBoolean(false),
                    preferredPluginPackageName = ApkBuilderTemplatePluginIds.PACKAGE_NAME,
                )
                assertEquals("$app: ${outcome.errors}", ApkBuildResult.STATUS_OK, outcome.status)
                val modules = ZipFile(output).use { zip ->
                    zip.entries().asSequence().map { it.name }.filter { it.startsWith(MODULES) && !it.endsWith("/") }.map { it.removePrefix(MODULES) }.sorted().toList()
                }
                Log.i(TAG, "$MARK app=$app bytes=${output.length()} modules=${JSONArray(modules)} notices=${JSONArray(outcome.notices)}")
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private companion object {
        const val TAG = "PackagedScriptModulesProducer"
        const val MARK = "P71_PRODUCER"
        const val ARG_APPS = "autojs.p71.apps"
        const val APP_ALL = "c"
        const val PACKAGE_PREFIX = "org.autojs.autojs6.p71"
        const val MODULES = "assets/modules/"

        // Static names (require and the global getters), and os through a computed name the scan cannot see.
        // zh-CN: 静态名称 (require 与全局 getter), 以及扫描看不到的拼接名称 os.
        val SCRIPT = """
            var results = {};
            function probe(name, fn) {
                try {
                    results[name] = 'ok ' + fn();
                } catch (e) {
                    results[name] = ['throws', e && e.code, e && e.library, e && e.reason, e && e.message].join(' | ');
                }
            }
            probe('require-dayjs', function () { return require('dayjs')('2026-10-29').format('YYYY/MM/DD'); });
            probe('getter-dayjs', function () { return dayjs('2026-10-29').year(); });
            probe('require-lodash', function () { return require('lodash').map([1, 2], function (x) { return x * 2; }).join(); });
            probe('getter-pangu', function () { return pangu.spaceText('a' + String.fromCharCode(0x4e2d)) === 'a ' + String.fromCharCode(0x4e2d); });
            probe('require-fs', function () { return typeof require('fs').readFileSync; });
            probe('require-os', function () { return typeof require(['o', 's'].join('')).platform; });
            console.log('P71_RESULT ' + JSON.stringify(results));
        """.trimIndent() + "\n"
    }

}
