package org.autojs.autojs.core.plugin.batchrename

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.plugin.explorer.ExplorerActionEntryPoint
import org.autojs.autojs.core.plugin.explorer.ExplorerActionRegistry
import org.autojs.autojs.core.plugin.explorer.ExplorerSurface
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.script.StringScriptSource
import org.autojs.autojs.ui.explorer.ExplorerBatchRenamePluginUi
import org.autojs.autojs.ui.explorer.ExplorerRenameDialogMenu
import org.autojs.autojs.ui.explorer.ExplorerRenameDialogMenuEntry
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.batchrename.api.BatchRenamePluginActions
import org.autojs.plugin.batchrename.api.BatchRenamePluginIds
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * A Batch Rename plugin that requires a newer host than the installed one (plugin roadmap P6.6, D40), installed as a
 * test build of the plugin with a raised `requiresHostVersion`: scripts get `PLUGIN_INCOMPATIBLE` with the upgrade
 * hint, the file manager offers none of its actions, and the "Batch rename..." guidance of the rename dialog asks for an
 * AutoJs6 upgrade and leads to the Plugin Center. Skipped unless such a plugin is installed. With the instrumentation
 * argument `batchRename.screenshots=true` the guidance and the Plugin Center are saved under the app's external files
 * directory (`screenshots/`).
 *
 * zh-CN: 要求比已安装宿主更新版本的 Batch Rename 插件 (插件路线图 P6.6, D40), 以调高 `requiresHostVersion` 的插件测试构建安装: 脚本得到带升级
 * 提示的 `PLUGIN_INCOMPATIBLE`, 文件管理器不提供它的任何动作, 重命名对话框的 "批量重命名..." 引导提示升级 AutoJs6 并通往插件中心. 未安装
 * 这样的插件时跳过. 传入 instrumentation 参数 `batchRename.screenshots=true` 时, 引导与插件中心会保存到应用外部文件目录 (`screenshots/`).
 */
class BatchRenameNewerHostPluginDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root = File(context.cacheDir, "batch-rename-newer-host-${System.nanoTime()}")
    private var required = 0L

    @Before
    fun setUp() {
        required = requiredHostVersion() ?: 0L
        assumeTrue("No Batch Rename plugin that needs a newer host is installed", required > BuildConfig.VERSION_CODE)
        root.mkdirs()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).forEach { activity ->
                if (activity is PluginCenterActivity || activity is MainActivity) activity.finish()
            }
        }
    }

    @Test
    fun scriptsAndTheFileManagerTreatThePluginAsIncompatible() {
        File(root, "a.txt").writeText("a")
        val result = runScript(
            """
            var result = { available: files.batchRename.isAvailable() };
            try { files.batchRename.plan("a.txt", { search: "a", replace: "b" }); } catch (e) { result.code = e.code; result.message = e.message; }
            files.write(MARKER, JSON.stringify(result));
            """,
        )
        assertFalse(result.getBoolean("available"))
        assertEquals(BatchRenameScriptCodes.PLUGIN_INCOMPATIBLE, result.getString("code"))
        assertEquals(upgradeHint(), result.getString("message"))
        assertTrue(File(root, "a.txt").exists())

        runBlocking { ExplorerActionRegistry.refresh(context) }
        val items = listOf<ExplorerItem>(ExplorerFileItem(File(root, "a.txt"), null))
        ExplorerActionEntryPoint.entries.forEach { entryPoint ->
            val offered = ExplorerActionRegistry.actionsFor(ExplorerSurface.MAIN_FILES, entryPoint, items)
                .filter { it.packageName == BatchRenamePluginIds.PACKAGE_NAME }
            assertTrue("the plugin offers $offered at $entryPoint", offered.isEmpty())
        }
        val dialogActions = ExplorerRenameDialogMenu.resolveActions(ExplorerActionRegistry, ExplorerSurface.MAIN_FILES, items.single())
        assertEquals(
            listOf(ExplorerRenameDialogMenuEntry.BatchRenameGuidance, ExplorerRenameDialogMenuEntry.RenameHistory),
            ExplorerRenameDialogMenu.entries(ExplorerSurface.MAIN_FILES, dialogActions.filter { it.packageName == BatchRenamePluginIds.PACKAGE_NAME }),
        )
    }

    @Test
    fun theGuidanceAsksForAnAutoJs6UpgradeAndLeadsToThePluginCenter() {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var main: MainActivity? = null
        awaitMain {
            main = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
            main != null
        }
        instrumentation.runOnMainSync { ExplorerBatchRenamePluginUi.showUnavailableDialog(checkNotNull(main)) }
        awaitText(upgradeHint())
        screenshot("newer-host-guidance")
        clickStableText(context.getString(R.string.text_plugin_center), ignoreCase = true)
        awaitMain {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is PluginCenterActivity }
        }
        SystemClock.sleep(3_000)
        screenshot("newer-host-plugin-center")
    }

    private fun upgradeHint() = context.getString(R.string.hint_plugin_requires_higher_host_version_with_required_and_current, required, BuildConfig.VERSION_CODE.toLong())

    @Suppress("DEPRECATION")
    private fun requiredHostVersion(): Long? = context.packageManager.queryIntentServices(
        Intent(BatchRenamePluginActions.BATCH_RENAME).addCategory(BatchRenamePluginActions.SERVICE_CATEGORY).setPackage(BatchRenamePluginIds.PACKAGE_NAME),
        PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS,
    ).mapNotNull { it.serviceInfo?.metaData?.get("requiresHostVersion")?.let(PluginCapabilityResolver::coerceLong) }.maxOrNull()

    private fun runScript(body: String): JSONObject {
        val marker = File(root, "marker.json")
        val source = "var MARKER = " + JSONObject.quote(marker.path) + ";\n" + body.trimIndent()
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }
        val execution = AutoJs.instance.scriptEngineService.execute(
            StringScriptSource("batch-rename-newer-host", source),
            listener,
            ExecutionConfig(workingDirectory = root.absolutePath),
        )
        try {
            assertTrue("the script did not finish", completed.await(60, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("the script failed", it) }
            return JSONObject(marker.readText()).also { marker.delete() }
        } finally {
            execution.engine?.forceStop()
        }
    }

    private fun screenshot(name: String) {
        if (InstrumentationRegistry.getArguments().getString("batchRename.screenshots") != "true") return
        SystemClock.sleep(500)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun awaitText(text: String): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText(text)
                ?.firstOrNull { it.text?.toString() == text }
                ?.let { return it }
            SystemClock.sleep(50)
        }
        throw AssertionError("Text did not appear: $text")
    }

    /** [ignoreCase] serves dialog buttons, which show their text in capitals. zh-CN: [ignoreCase] 用于以大写显示文字的对话框按钮. */
    private fun clickStableText(text: String, ignoreCase: Boolean = false) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        var previousBounds: Rect? = null
        var stableSince = 0L
        while (SystemClock.elapsedRealtime() < deadline) {
            val now = SystemClock.elapsedRealtime()
            val node = instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText(text)
                ?.firstOrNull { it.text?.toString().equals(text, ignoreCase) && it.isVisibleToUser && it.isEnabled }
            val bounds = node?.let { Rect().also(it::getBoundsInScreen) }
            if (node != null && bounds != null && !bounds.isEmpty) {
                if (bounds == previousBounds) {
                    if (now - stableSince >= 250L) {
                        var target: AccessibilityNodeInfo = node
                        while (!target.isClickable || target.actionList.none { it.id == AccessibilityNodeInfo.ACTION_CLICK }) {
                            target = target.parent ?: throw AssertionError("No accessible click action for: $text")
                        }
                        assertTrue("The click was rejected: $text", target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                        return
                    }
                } else {
                    previousBounds = bounds
                    stableSince = now
                }
            }
            SystemClock.sleep(50)
        }
        throw AssertionError("Text did not settle: $text")
    }

    private fun awaitMain(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Condition was not met in time")
    }
}
