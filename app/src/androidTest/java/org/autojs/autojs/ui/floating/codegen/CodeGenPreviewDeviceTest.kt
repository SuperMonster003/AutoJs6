package org.autojs.autojs.ui.floating.codegen

import android.app.UiAutomation
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.accessibility.Capture
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.core.accessibility.NodeInfoSnapshot
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.floating.LayoutFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.LayoutBoundsFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.LayoutHierarchyFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.search.InspectorMatchView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class CodeGenPreviewDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: CodeGenSmokeActivity
    private val windows = ArrayList<LayoutFloatyWindow>()

    @Before
    fun prepare() {
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val context = instrumentation.targetContext
        activity = instrumentation.startActivitySync(Intent(context, CodeGenSmokeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CodeGenSmokeActivity
        instrumentation.runOnMainSync { activity.startService(Intent(activity, FloatyService::class.java)) }
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (!FloatyService.isRunning() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("Floaty service ready", FloatyService.isRunning())
    }

    @After
    fun finish() {
        instrumentation.runOnMainSync {
            windows.forEach { it.close() }
            if (::activity.isInitialized) activity.finish()
        }
    }

    @Test
    fun boundsPreviewRestoresTheOriginalSearchAndConsumesTheReturnTap() {
        instrumentation.runOnMainSync {
            val root = layout()
            val selected = root.children[0]
            var match: InspectorMatchView? = null
            val window = object : LayoutBoundsFloatyWindow(Capture(emptyList(), root), activity) {
                fun preview(nodes: List<NodeInfo>, onReturn: () -> Unit) = previewGeneratedTargets(nodes, onReturn)
                fun active() = isCodeGenPreview
                override fun applyCodeGenPreview(value: InspectorMatchView?) { super.applyCodeGenPreview(value); match = value }
            }
            windows += window
            FloatyService.addWindow(window)
            val session = window.searchSession
            val returned = AtomicInteger()
            window.preview(listOf(selected)) { returned.incrementAndGet() }
            assertTrue(window.active())
            assertTrue(match!!.isMatched(selected))
            assertFalse(match!!.isMatched(root.children[1]))
            tap(window)
            assertFalse(window.active())
            assertNull(match)
            assertSame(session, window.searchSession)
            assertEquals(1, returned.get())
        }
    }

    @Test
    fun hierarchyPreviewKeepsAncestorsAndDoesNotReopenAfterClosing() {
        instrumentation.runOnMainSync {
            val root = layout()
            val selected = root.children[0]
            var match: InspectorMatchView? = null
            val window = object : LayoutHierarchyFloatyWindow(Capture(emptyList(), root), activity) {
                fun preview(nodes: List<NodeInfo>, onReturn: () -> Unit) = previewGeneratedTargets(nodes, onReturn)
                fun active() = isCodeGenPreview
                override fun applyCodeGenPreview(value: InspectorMatchView?) { super.applyCodeGenPreview(value); match = value }
            }
            windows += window
            FloatyService.addWindow(window)
            val session = window.searchSession
            val returned = AtomicInteger()
            window.preview(listOf(selected)) { returned.incrementAndGet() }
            assertTrue(match!!.isKept(root))
            assertTrue(match!!.isMatched(selected))
            assertFalse(match!!.isKept(root.children[1]))
            tap(window)
            assertFalse(window.active())
            assertSame(session, window.searchSession)
            assertEquals(1, returned.get())
            window.preview(listOf(selected)) { returned.incrementAndGet() }
            window.close()
            assertFalse(window.active())
            assertNull(match)
            assertEquals(1, returned.get())
        }
    }

    private fun tap(window: LayoutFloatyWindow) {
        val view = requireNotNull(window.windowView)
        val start = SystemClock.uptimeMillis()
        for ((action, time) in listOf(MotionEvent.ACTION_DOWN to start, MotionEvent.ACTION_UP to start + 20)) {
            val event = MotionEvent.obtain(start, time, action, 100f, 100f, 0)
            try { assertTrue(view.dispatchTouchEvent(event)) } finally { event.recycle() }
        }
    }

    private fun layout(): NodeInfo = NodeInfoSnapshot(
        className = "android.widget.LinearLayout", bounds = intArrayOf(0, 0, 600, 900),
        children = listOf(
            NodeInfoSnapshot(className = "android.widget.Button", text = "Selected", bounds = intArrayOf(30, 120, 550, 180), clickable = true, depth = 1, indexInParent = 0),
            NodeInfoSnapshot(className = "android.widget.Button", text = "Other", bounds = intArrayOf(30, 240, 550, 300), clickable = true, depth = 1, indexInParent = 1),
        ),
    ).toNodeInfo()
}
