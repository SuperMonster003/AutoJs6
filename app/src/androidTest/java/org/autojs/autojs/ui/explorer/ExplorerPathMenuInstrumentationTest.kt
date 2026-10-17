package org.autojs.autojs.ui.explorer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.model.explorer.Explorer
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerFileProvider
import org.autojs.autojs6.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt

/** Exercises the actual path-row trigger and popup through its accessible controls. */
@RunWith(AndroidJUnit4::class)
class ExplorerPathMenuInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private lateinit var activity: ExplorerPathMenuProbeActivity
    private lateinit var explorer: ExplorerView
    private lateinit var directory: File
    private var previousHiddenPreference: String? = null
    private var previousAccessibilityFlags = 0

    @Before
    fun setUp() {
        previousHiddenPreference = Pref.getStringOrNull(R.string.key_hidden_files)
        directory = File(targetContext.cacheDir, "explorer-path-menu-${UUID.randomUUID()}").apply {
            check(mkdir())
        }
        instrumentation.uiAutomation.serviceInfo.let { info ->
            previousAccessibilityFlags = info.flags
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            instrumentation.uiAutomation.serviceInfo = info
        }
        activity = instrumentation.startActivitySync(
            Intent(targetContext, ExplorerPathMenuProbeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as ExplorerPathMenuProbeActivity
    }

    @After
    fun tearDown() {
        if (::explorer.isInitialized && onMain { explorer.findViewById<View>(R.id.explorer_path_more).isSelected }) {
            instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
        onMain {
            if (::activity.isInitialized) activity.finish()
            Pref.putString(R.string.key_hidden_files, previousHiddenPreference)
        }
        instrumentation.uiAutomation.serviceInfo.let { info ->
            info.flags = previousAccessibilityFlags
            instrumentation.uiAutomation.serviceInfo = info
        }
        if (::directory.isInitialized) directory.deleteRecursively()
    }

    @Test
    fun moreButtonOpensAFullWidthPanelBelowThePathRowAndBackDismissesIt() {
        installExplorer()
        openMenu()
        screenshot("default")
        assertPanelBounds()

        withNode(R.id.explorer_menu_back) { assertFalse(it.isEnabled) }
        withNode(R.id.explorer_menu_forward) { assertFalse(it.isEnabled) }
        assertNavigationControls()

        assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
        awaitMain("The panel should release the path-row trigger after Back") {
            !explorer.findViewById<View>(R.id.explorer_path_more).isSelected
        }
        assertEquals(directory.path, onMain { explorer.currentPage.path })
    }

    @Test
    fun menuBackForwardAndUpNavigateTheDisplayedDirectory() {
        val first = File(directory, "first").apply { check(mkdir()) }
        val second = File(first, "second").apply { check(mkdir()) }
        installExplorer()
        onMain { explorer.navigateToPath(first.path) }
        awaitDirectory(first)
        onMain { explorer.navigateToPath(second.path) }
        awaitDirectory(second)

        openMenu()
        click(R.id.explorer_menu_back)
        awaitDirectory(first)
        assertTrue(onMain { explorer.canGoForward() })

        openMenu()
        click(R.id.explorer_menu_forward)
        awaitDirectory(second)
        assertFalse(onMain { explorer.canGoForward() })

        openMenu()
        click(R.id.explorer_menu_up)
        awaitDirectory(first)
        assertFalse("A new Up navigation should discard stale forward history", onMain { explorer.canGoForward() })
    }

    @Test
    fun hiddenFilesSwitchPersistsThePreferenceAndRefreshesTheCurrentListing() {
        val visible = File(directory, "visible.txt").apply { writeText("visible") }
        val hidden = File(directory, ".hidden.txt").apply { writeText("hidden") }
        onMain { Pref.putString(R.string.key_hidden_files, targetContext.getString(R.string.key_hidden_files_not_show)) }
        installExplorer()
        awaitMain("The initial listing should omit dot files") { listedFilePaths() == setOf(visible.path) }

        openMenu()
        scrollTo(R.id.explorer_menu_hidden)
        withNode(R.id.explorer_menu_hidden) { assertFalse(it.isChecked) }
        click(R.id.explorer_menu_hidden)
        awaitMain("Showing hidden files should update both the setting and the loaded directory") {
            Pref.isHiddenFilesShown && !explorer.isRefreshing && listedFilePaths() == setOf(visible.path, hidden.path)
        }
        withNode(R.id.explorer_menu_hidden) { assertTrue(it.isChecked) }

        click(R.id.explorer_menu_hidden)
        awaitMain("Hiding dot files again should remove them from the current listing") {
            !Pref.isHiddenFilesShown && !explorer.isRefreshing && listedFilePaths() == setOf(visible.path)
        }
    }

    @Test
    fun largeTextKeepsNavigationAndSettingsReachableInDayAndNightConfigurations() {
        listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES).forEach { nightMode ->
            installExplorer(fontScale = 1.5f, nightMode = nightMode)
            openMenu()
            val mode = if (nightMode == Configuration.UI_MODE_NIGHT_YES) "night" else "day"
            screenshot("$mode-large-text")
            assertPanelBounds()
            assertNavigationControls()
            scrollTo(R.id.explorer_menu_hidden)
            withNode(R.id.explorer_menu_hidden) { node ->
                assertTrue(node.isVisibleToUser)
                assertTrue(node.isClickable)
                assertTrue(node.text?.isNotBlank() == true)
            }
            screenshot("$mode-large-text-settings")
            assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
            awaitMain("The large-text panel should dismiss cleanly") {
                !explorer.findViewById<View>(R.id.explorer_path_more).isSelected
            }
        }
    }

    private fun installExplorer(fontScale: Float = 1f, nightMode: Int = Configuration.UI_MODE_NIGHT_NO) {
        val configuration = Configuration(targetContext.resources.configuration).apply {
            this.fontScale = fontScale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            setLocale(Locale.ENGLISH)
            setLayoutDirection(Locale.ENGLISH)
        }
        onMain {
            val context = ContextThemeWrapper(activity, R.style.AppTheme).apply {
                applyOverrideConfiguration(configuration)
            }
            explorer = ExplorerView(context).apply {
                setExplorer(Explorer(ExplorerFileProvider(), 0), ExplorerDirPage.createRoot(directory))
            }
            activity.showExplorer(explorer)
        }
        awaitMain("The Explorer should attach and finish its first directory load") {
            explorer.isAttachedToWindow && explorer.width > 0 && !explorer.isRefreshing
        }
    }

    private fun openMenu() {
        click(R.id.explorer_path_more)
        awaitStableBounds(R.id.explorer_menu_panel)
        assertTrue(onMain { explorer.findViewById<View>(R.id.explorer_path_more).isSelected })
    }

    private fun assertPanelBounds() {
        val panelBounds = awaitStableBounds(R.id.explorer_menu_panel)
        val anchor = onMain {
            val row = explorer.findViewById<View>(R.id.explorer_path_row)
            val position = IntArray(2).also(row::getLocationOnScreen)
            Anchor(Rect(position[0], position[1], position[0] + row.width, position[1] + row.height), row.context)
        }
        val visibleFrame = onMain { Rect().also(explorer::getWindowVisibleDisplayFrame) }
        val expectedWidth = minOf(anchor.bounds.width(), visibleFrame.width()) - dp(anchor.context, 16)
        assertTrue("The panel should nearly span the path row: $panelBounds versus ${anchor.bounds}", abs(panelBounds.width() - expectedWidth) <= dp(anchor.context, 2))
        assertTrue("The panel should start just below its path row", abs(panelBounds.top - anchor.bounds.bottom - dp(anchor.context, 2)) <= dp(anchor.context, 3))
        assertTrue("The panel should stay inside the visible window", panelBounds.bottom <= visibleFrame.bottom)
    }

    private fun assertNavigationControls() {
        val bounds = listOf(R.id.explorer_menu_back, R.id.explorer_menu_forward, R.id.explorer_menu_up, R.id.explorer_menu_refresh).map { id ->
            withNode(id) { node ->
                assertEquals(Button::class.java.name, node.className.toString())
                assertTrue(node.contentDescription?.isNotBlank() == true)
                Rect().also(node::getBoundsInScreen).also { rectangle ->
                    assertTrue("Navigation targets need at least 48 dp height", rectangle.height() >= dp(targetContext, 48))
                    assertTrue("Navigation targets need at least 48 dp width", rectangle.width() >= dp(targetContext, 48))
                }
            }
        }
        assertTrue("The four navigation controls must not overlap", bounds.zipWithNext().all { (left, right) -> left.right <= right.left })
    }

    private fun awaitDirectory(expected: File) = awaitMain("Directory navigation did not reach ${expected.path}") {
        explorer.currentPage.path == expected.path && !explorer.isRefreshing
    }

    /** Enable with -e explorerPathMenu.screenshots true; files remain in the target app cache for review. */
    private fun screenshot(name: String) {
        if (InstrumentationRegistry.getArguments().getString("explorerPathMenu.screenshots") != "true") return
        SystemClock.sleep(300)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Could not capture $name" }
        try {
            val screenshots = File(targetContext.cacheDir, "explorer-path-menu-screenshots").apply { mkdirs() }
            File(screenshots, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }

    /** Called only on the main thread, after the visible item manager has received the refresh. */
    private fun listedFilePaths(): Set<String> = explorer.explorerItemManager.let { manager ->
        (0 until manager.itemCount()).map { manager.getFileItem(it).path }.toSet()
    }

    private fun click(id: Int) = withNode(id) { node ->
        assertTrue("${resourceName(id)} should be visible", node.isVisibleToUser)
        assertTrue("${resourceName(id)} should be enabled", node.isEnabled)
        assertTrue("${resourceName(id)} should accept clicks", node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun scrollTo(id: Int) {
        repeat(12) {
            val alreadyVisible = findNode(id)?.let { node ->
                try {
                    if (node.isVisibleToUser) true else {
                        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                        false
                    }
                } finally {
                    release(node)
                }
            } == true
            if (alreadyVisible) return
            findNode(R.id.explorer_menu_panel)?.let { panel ->
                try { panel.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) } finally { release(panel) }
            }
            SystemClock.sleep(100)
        }
        throw AssertionError("Could not scroll to ${resourceName(id)}")
    }

    private fun awaitStableBounds(id: Int): Rect {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        var previous: Rect? = null
        var stableSince = 0L
        while (SystemClock.elapsedRealtime() < deadline) {
            val bounds = findNode(id)?.let { node ->
                try { Rect().also(node::getBoundsInScreen).takeUnless(Rect::isEmpty) } finally { release(node) }
            }
            val now = SystemClock.elapsedRealtime()
            if (bounds != null && bounds == previous && now - stableSince >= 250) return bounds
            if (bounds != previous) {
                previous = bounds
                stableSince = now
            }
            SystemClock.sleep(40)
        }
        throw AssertionError("${resourceName(id)} did not settle into a visible position")
    }

    private fun <T> withNode(id: Int, block: (AccessibilityNodeInfo) -> T): T {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            findNode(id)?.let { node ->
                return try { block(node) } finally { release(node) }
            }
            SystemClock.sleep(40)
        }
        throw AssertionError("Missing accessible view ${resourceName(id)}")
    }

    private fun findNode(id: Int): AccessibilityNodeInfo? {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        return try {
            val nodes = root.findAccessibilityNodeInfosByViewId(resourceName(id))
            nodes.drop(1).forEach(::release)
            nodes.firstOrNull()
        } finally {
            release(root)
        }
    }

    @Suppress("DEPRECATION")
    private fun release(node: AccessibilityNodeInfo) = node.recycle()

    private fun awaitMain(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (onMain(condition)) return
            SystemClock.sleep(40)
        }
        throw AssertionError(message)
    }

    private fun <T> onMain(block: () -> T): T {
        val outcome = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { outcome.set(runCatching(block)) }
        return requireNotNull(outcome.get()).getOrThrow()
    }

    private fun resourceName(id: Int) = targetContext.resources.getResourceName(id)
    private fun dp(context: Context, value: Int) = (context.resources.displayMetrics.density * value).roundToInt()
    private data class Anchor(val bounds: Rect, val context: Context)

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
