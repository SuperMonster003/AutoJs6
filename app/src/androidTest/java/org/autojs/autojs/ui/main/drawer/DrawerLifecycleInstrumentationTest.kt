package org.autojs.autojs.ui.main.drawer

import android.view.Gravity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs6.R
import org.greenrobot.eventbus.EventBus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrawerLifecycleInstrumentationTest {

    @Test
    fun closingMainActivityDestroysDrawer() {
        lateinit var drawer: DrawerFragment
        lateinit var lifecycle: Lifecycle
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                drawer = drawerOf(activity)
                lifecycle = drawer.lifecycle
                openDrawerAndAssertMenu(activity)
            }
        }
        // FragmentManager resets a detached Fragment's lifecycle for potential reuse.
        assertEquals(Lifecycle.State.DESTROYED, lifecycle.currentState)
        assertNull(drawer.view)
        assertFalse(EventBus.getDefault().isRegistered(drawer))
    }

    @Test
    fun recreatingMainActivityKeepsDrawerUsable() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var oldDrawer: DrawerFragment
            lateinit var oldLifecycle: Lifecycle
            scenario.onActivity { activity ->
                oldDrawer = drawerOf(activity)
                oldLifecycle = oldDrawer.lifecycle
                openDrawerAndAssertMenu(activity)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertNotSame(oldDrawer, drawerOf(activity))
                assertEquals(Lifecycle.State.DESTROYED, oldLifecycle.currentState)
                assertFalse(EventBus.getDefault().isRegistered(oldDrawer))
                openDrawerAndAssertMenu(activity)
                activity.findViewById<DrawerLayout>(R.id.drawer_layout).closeDrawer(Gravity.START, false)
            }
        }
    }

    @Test
    fun drawerHandlesThemeChangesWithoutAViewAndRecreatesItsMenu() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawer = drawerOf(activity)
                val oldView = drawer.requireView()
                val manager = activity.supportFragmentManager
                manager.beginTransaction().setMaxLifecycle(drawer, Lifecycle.State.CREATED).commitNow()
                assertNull(drawer.view)

                // A fragment-scoped notification may arrive between two view lifetimes.
                drawer.onThemeColorLayoutSwitched(object : DrawerFragment.Companion.Event.ThemeColorLayoutSwitchedEvent {})

                manager.beginTransaction().setMaxLifecycle(drawer, Lifecycle.State.RESUMED).commitNow()
                assertNotSame(oldView, drawer.requireView())
                openDrawerAndAssertMenu(activity)
            }
        }
    }

    private fun drawerOf(activity: MainActivity): DrawerFragment =
        activity.supportFragmentManager.findFragmentById(R.id.fragment_drawer) as DrawerFragment

    private fun openDrawerAndAssertMenu(activity: MainActivity) {
        val layout = activity.findViewById<DrawerLayout>(R.id.drawer_layout)
        layout.openDrawer(Gravity.START, false)
        assertTrue(layout.isDrawerOpen(Gravity.START))
        val menu = activity.findViewById<RecyclerView>(R.id.drawer_menu)
        assertTrue(requireNotNull(menu.adapter).itemCount > 0)
        assertEquals(Lifecycle.State.RESUMED, drawerOf(activity).lifecycle.currentState)
    }
}
