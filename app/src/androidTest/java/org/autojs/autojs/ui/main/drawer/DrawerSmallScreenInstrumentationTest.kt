package org.autojs.autojs.ui.main.drawer

import android.graphics.Rect
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.ui.main.ResumedMainActivity
import org.autojs.autojs6.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class DrawerSmallScreenInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun contentSwitchesAndFooterFitInsideDrawer() = withDrawer { activity, drawerLayout ->
        assertDrawerFits(activity, drawerLayout)
    }

    @Test
    fun drawerAdaptsWhenItsWindowShrinksAndExpands() = withDrawer { activity, drawerLayout ->
        val originalWidth = onMain { drawerLayout.width }
        val originalDrawerWidth = onMain { activity.findViewById<View>(R.id.fragment_drawer).width }
        val narrowWidth = originalWidth * 4 / 5
        onMain {
            drawerLayout.layoutParams = drawerLayout.layoutParams.apply { width = narrowWidth }
        }
        awaitLayout(drawerLayout) { drawerLayout.width == narrowWidth }
        assertDrawerFits(activity, drawerLayout)
        onMain {
            assertTrue(
                "The drawer must leave visible space to dismiss it after the window shrinks",
                activity.findViewById<View>(R.id.fragment_drawer).width < narrowWidth,
            )
            drawerLayout.layoutParams = drawerLayout.layoutParams.apply { width = originalWidth }
        }
        awaitLayout(drawerLayout) { drawerLayout.width == originalWidth }
        assertDrawerFits(activity, drawerLayout)
        onMain {
            assertEquals(originalDrawerWidth, activity.findViewById<View>(R.id.fragment_drawer).width)
        }
    }

    @Test
    fun lastMenuEntryCanScrollFullyAboveFooter() = withDrawer { activity, drawerLayout ->
        val menu = onMain { activity.findViewById<RecyclerView>(R.id.drawer_menu) }
        val lastPosition = onMain { requireNotNull(menu.adapter).itemCount - 1 }
        onMain { menu.scrollToPosition(lastPosition) }
        awaitLayout(drawerLayout) { menu.findViewHolderForAdapterPosition(lastPosition) != null }
        onMain {
            val lastItem = requireNotNull(menu.findViewHolderForAdapterPosition(lastPosition)).itemView
            assertFullyVisible(lastItem, "Last menu entry")
            val lastBounds = Rect().also { lastItem.getGlobalVisibleRect(it) }
            val footerBounds = Rect().also { activity.findViewById<View>(R.id.settings).getGlobalVisibleRect(it) }
            assertTrue("The last menu entry must stay above the footer", lastBounds.bottom <= footerBounds.top)
        }
        assertDrawerFits(activity, drawerLayout)
    }

    private fun assertDrawerFits(activity: MainActivity, drawerLayout: DrawerLayout) = onMain {
        val drawer = activity.findViewById<View>(R.id.fragment_drawer)
        val content = activity.findViewById<View>(R.id.drawer_menu_container)
        val menu = activity.findViewById<RecyclerView>(R.id.drawer_menu)
        assertTrue("The drawer must leave space outside it", drawer.width < drawerLayout.width)
        assertEquals("Drawer content must fit its outer container", drawer.width, content.width)
        assertEquals(
            "Menu must fit inside the drawer's system insets",
            content.width - content.paddingLeft - content.paddingRight,
            menu.width,
        )
        assertFullyVisible(drawer, "Drawer")
        for (id in listOf(R.id.settings, R.id.plugin_center, R.id.restart, R.id.exit)) {
            assertFullyVisible(activity.findViewById(id), activity.resources.getResourceEntryName(id))
        }
        for (index in 0 until menu.childCount) {
            val switch = menu.getChildAt(index).findViewById<View>(R.id.sw) ?: continue
            if (switch.visibility != View.VISIBLE) continue
            val visible = Rect()
            if (switch.getGlobalVisibleRect(visible)) {
                assertEquals("Visible menu switches must not be clipped horizontally", switch.width, visible.width())
            }
        }
    }

    private fun assertFullyVisible(view: View, label: String) {
        val visible = Rect()
        assertTrue("$label must be visible", view.getGlobalVisibleRect(visible))
        assertTrue("$label must have a positive size", view.width > 0 && view.height > 0)
        assertEquals("$label must fit horizontally", view.width, visible.width())
        assertEquals("$label must fit vertically", view.height, visible.height())
    }

    private fun withDrawer(block: (MainActivity, DrawerLayout) -> Unit) {
        val activity = ResumedMainActivity.launch()
        try {
            val drawerLayout = onMain { activity.findViewById<DrawerLayout>(R.id.drawer_layout) }
            awaitLayout(drawerLayout) { drawerLayout.width > 0 }
            onMain { drawerLayout.openDrawer(Gravity.START, false) }
            awaitLayout(drawerLayout) { drawerLayout.isDrawerOpen(Gravity.START) }
            block(activity, drawerLayout)
        } finally {
            onMain { activity.finish() }
            instrumentation.waitForIdleSync()
        }
    }

    private fun awaitLayout(view: View, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (onMain { view.isLaidOut && !view.isLayoutRequested && condition() }) return
            SystemClock.sleep(25)
        }
        throw AssertionError("Drawer layout did not settle")
    }

    private fun <T> onMain(block: () -> T): T {
        val outcome = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { outcome.set(runCatching(block)) }
        return requireNotNull(outcome.get()).getOrThrow()
    }
}
