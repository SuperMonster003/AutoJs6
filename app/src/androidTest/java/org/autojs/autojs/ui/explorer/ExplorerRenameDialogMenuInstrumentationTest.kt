package org.autojs.autojs.ui.explorer

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.explorer.ExplorerActionCatalogPolicy
import org.autojs.autojs.core.plugin.explorer.ExplorerSurface
import org.autojs.autojs.core.plugin.explorer.ResolvedExplorerAction
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.ui.common.ScriptOperations
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.ui.storage.BatchRenameHistoryActivity
import org.autojs.autojs6.R
import org.autojs.plugin.batchrename.api.BatchRenamePluginIds
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionValues
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * The "More" menu of the Explorer rename dialog (Batch Rename plugin roadmap P1.4, D10c / D29) on a device: the title
 * button is labelled and reachable through accessibility, a plugin action closes the dialog before it launches, and
 * without any action the "Batch rename..." entry explains how to get the plugin while the dialog stays open.
 *
 * A probe option appended after the real options hands the test the dialog itself.
 *
 * zh-CN: 在设备上验证文件管理器重命名对话框的 "更多" 菜单 (Batch Rename 插件路线图 P1.4, D10c / D29): 标题按钮带标签且可经无障碍
 * 操作, 选择插件动作时先关闭对话框再启动, 没有动作时 "批量重命名..." 说明如何获取插件且对话框保持打开. 追加在真实选项之后的探针选项
 * 用于让测试拿到对话框本身.
 */
@RunWith(AndroidJUnit4::class)
class ExplorerRenameDialogMenuInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val dialogs = mutableListOf<MaterialDialog>()
    private var resumedActivity: MainActivity? = null
    private val activity: MainActivity
        get() = checkNotNull(resumedActivity) { "MainActivity is not resumed" }
    private lateinit var directory: File

    @Before
    fun setUp() {
        // Wait for RESUMED instead of using startActivitySync / ActivityScenario: both also wait for an idle main thread,
        // which a fresh API 24 emulator never reports while MainActivity is shown.
        // zh-CN: 等待 RESUMED, 而不使用 startActivitySync / ActivityScenario: 两者都要等主线程空闲, 而全新 API 24 模拟器在
        // MainActivity 显示期间从不报告空闲.
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        awaitMain {
            resumedActivity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().firstOrNull()
            resumedActivity != null
        }
        directory = File(context.cacheDir, "rename-dialog-menu-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync {
            dialogs.forEach(MaterialDialog::dismiss)
            resumedActivity?.finish()
        }
        directory.deleteRecursively()
    }

    @Test
    fun withoutAnyActionTheGuidanceExplainsThePluginAndKeepsTheDialogOpen() {
        val dialog = showRenameDialog { unexpectedLaunch ->
            ExplorerRenameDialogMenu.options(activity, ExplorerRenameDialogMenu.entries(ExplorerSurface.MAIN_FILES, emptyList()), unexpectedLaunch)
        }
        assertDecoratedOptionsButton(dialog)

        clickOptionsButton()
        clickStableText(context.getString(R.string.text_batch_rename))
        // The guidance names the plugin and leads to the Plugin Center; the rename dialog stays behind it.
        awaitText(context.getString(R.string.plugin_batch_rename_name))
        val pluginCenter = awaitText(context.getString(R.string.text_plugin_center), ignoreCase = true)
        var renameDialogShowing = false
        instrumentation.runOnMainSync { renameDialogShowing = dialog.isShowing }
        assertTrue("the rename dialog stays open behind the guidance", renameDialogShowing)
        assertTrue(pluginCenter.refresh())
        clickStableText(context.getString(R.string.dialog_button_cancel), ignoreCase = true)
    }

    @Test
    fun aPluginActionClosesTheDialogBeforeItLaunches() {
        val action = renameDialogAction("Batch rename (test)")
        val launched = AtomicReference<ResolvedExplorerAction>()
        val showingAtLaunch = AtomicReference<Boolean>()
        val dialog = showRenameDialog { _ ->
            ExplorerRenameDialogMenu.options(activity, ExplorerRenameDialogMenu.entries(ExplorerSurface.MAIN_FILES, listOf(action))) { chosen ->
                showingAtLaunch.set(dialogs.single().isShowing)
                launched.set(chosen)
            }
        }
        assertDecoratedOptionsButton(dialog)

        clickOptionsButton()
        clickStableText(action.menuTitle)
        awaitMain { launched.get() != null }
        assertSame(action, launched.get())
        // The menu dismisses the dialog first, so the plugin screen never opens above a stale name field.
        assertEquals("the rename dialog closes before the action launches", false, showingAtLaunch.get())
        var showing = true
        instrumentation.runOnMainSync { showing = dialog.isShowing }
        assertFalse(showing)
    }

    /**
     * "Rename history" is the last entry, with or without plugin actions, and opens the history page after closing the
     * dialog (D51).
     * zh-CN: "重命名历史" 是最后一项 (无论是否有插件动作), 关闭对话框后打开历史页面 (D51).
     */
    @Test
    fun renameHistoryClosesTheDialogAndOpensTheHistoryPage() {
        val dialog = showRenameDialog { unexpectedLaunch ->
            ExplorerRenameDialogMenu.options(activity, ExplorerRenameDialogMenu.entries(ExplorerSurface.MAIN_FILES, emptyList()), unexpectedLaunch)
        }
        clickOptionsButton()
        awaitText(context.getString(R.string.text_rename_history))
        screenshot("rename-dialog-menu")
        clickStableText(context.getString(R.string.text_rename_history))
        var page: BatchRenameHistoryActivity? = null
        awaitMain {
            page = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<BatchRenameHistoryActivity>().firstOrNull()
            page != null
        }
        var showing = true
        instrumentation.runOnMainSync {
            showing = dialog.isShowing
            page?.finish()
        }
        assertFalse("the rename dialog closes before the history page opens", showing)
    }

    /** Opens the rename dialog with [buildOptions] plus a probe option and returns the dialog after one probe click. */
    private fun showRenameDialog(
        buildOptions: ((ResolvedExplorerAction) -> Unit) -> List<MaterialDialog.OptionMenuItemSpec>,
    ): MaterialDialog {
        val file = File(directory, "sample.txt").apply { writeText("sample") }
        val probed = AtomicReference<MaterialDialog>()
        instrumentation.runOnMainSync {
            val options = buildOptions { error("no plugin action is expected") } +
                MaterialDialog.OptionMenuItemSpec(PROBE_TITLE) { dialog -> probed.set(dialog) }
            ScriptOperations(activity, null, ScriptFile(directory.path)).rename(ExplorerFileItem(file, null), options)
        }
        awaitText(file.name)
        clickOptionsButton()
        clickStableText(PROBE_TITLE)
        awaitMain { probed.get() != null }
        return probed.get().also { dialogs += it }
    }

    private fun assertDecoratedOptionsButton(dialog: MaterialDialog) {
        awaitMain {
            val options = dialog.optionsView
            options != null && options.isShown && (options.parent as? View)?.touchDelegate != null
        }
        instrumentation.runOnMainSync {
            val options = checkNotNull(dialog.optionsView)
            assertEquals(context.getString(R.string.text_more), options.contentDescription)
            // The 27 dp button gets a touch target of at least 48 dp through its parent's TouchDelegate.
            val target = (48 * options.resources.displayMetrics.density).toInt()
            val bounds = Rect().also(options::getHitRect)
            assertTrue("the button itself is narrower than the target", bounds.width() < target)
            assertNotNull((options.parent as View).touchDelegate)
        }
    }

    /** Clicks the title button through accessibility, as TalkBack would. zh-CN: 像 TalkBack 一样经无障碍操作点击标题按钮. */
    private fun clickOptionsButton() {
        val label = context.getString(R.string.text_more)
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val button = instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText(label)
                ?.firstOrNull { it.contentDescription?.toString() == label && it.isVisibleToUser }
            if (button != null) {
                assertTrue("the More button is clickable", button.isClickable)
                assertTrue("the More click was rejected", button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                return
            }
            SystemClock.sleep(50)
        }
        throw AssertionError("The labelled More button did not appear")
    }

    /** [ignoreCase] serves dialog buttons, which show their text in capitals. zh-CN: [ignoreCase] 用于以大写显示文字的对话框按钮. */
    private fun awaitText(text: String, ignoreCase: Boolean = false): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText(text)
                ?.firstOrNull { it.text?.toString().equals(text, ignoreCase) }
                ?.let { return it }
            SystemClock.sleep(50)
        }
        throw AssertionError("Text did not appear: $text")
    }

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
                    // Popup rows exist before their entrance animation settles.
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

    /**
     * With the instrumentation argument `batchRename.screenshots=true`, saves the screen under the app's external files
     * directory (`screenshots/`) for the manual check of the layout.
     * zh-CN: 传入 instrumentation 参数 `batchRename.screenshots=true` 时, 把屏幕保存到应用外部文件目录 (`screenshots/`), 供人工检查布局.
     */
    private fun screenshot(name: String) {
        if (InstrumentationRegistry.getArguments().getString("batchRename.screenshots") != "true") return
        SystemClock.sleep(500)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
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

    private fun renameDialogAction(menuTitle: String): ResolvedExplorerAction {
        val packageName = BatchRenamePluginIds.PACKAGE_NAME
        val descriptor = requireNotNull(
            ExplorerActionCatalogPolicy.normalizeDescriptor(
                protocolVersion = ExplorerActionProtocol.MAX_SUPPORTED_VERSION,
                packageName = packageName,
                id = "batch-rename-single",
                labelResourceName = null,
                labelFallback = menuTitle,
                activityClassName = "$packageName.BatchRenameActivity",
                priority = 0,
                targetKind = ExplorerActionValues.TARGET_MIXED,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                accessMode = ExplorerActionValues.ACCESS_READ_ONLY,
                placement = ExplorerActionValues.PLACEMENT_RENAME_DIALOG,
                mimeTypes = listOf("*/*"),
                extensions = emptyList(),
                targetRename = true,
            ),
        ) { "the Batch Rename single-item shape is a valid protocol 25 descriptor" }
        return ResolvedExplorerAction(
            packageName, ComponentName(packageName, descriptor.activityClassName), menuTitle, menuTitle, descriptor,
            AidlPluginHost.Discovered(ServiceInfo(), null, null),
        )
    }

    private companion object {
        const val PROBE_TITLE = "Rename dialog probe"
    }
}
