package org.autojs.autojs.core.accessibility.window

import org.autojs.autojs.core.accessibility.window.WindowRootPolicy.Fallback
import org.autojs.autojs.core.accessibility.window.WindowRootPolicy.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowRootResolverTest {

    private class Window(
        val name: String,
        val root: String? = "root:$name",
        val application: Boolean = true,
        val focused: Boolean = false,
        val active: Boolean = false,
        val rootThrows: Boolean = false,
    ) {
        override fun toString() = name
    }

    /** A service whose accessors can be scripted to return, be absent, or throw. zh-CN: 访问器可按需返回, 缺失或抛出的服务. */
    private class FakeSource : WindowRootResolver.Source<Window, String> {
        var windows: List<Window>? = emptyList()
        var windowsThrow = false
        var activeRoot: String? = "active"
        var activeRootThrows = false
        var fastRoot: String? = "fast"
        val rootQueries = mutableListOf<String>()

        override fun windows(): List<Window>? = if (windowsThrow) throw IllegalStateException("gone") else windows

        override fun rootOf(window: Window): String? {
            rootQueries += window.name
            if (window.rootThrows) throw IllegalStateException("stale window")
            return window.root
        }

        override fun activeRoot(): String? = if (activeRootThrows) throw IllegalStateException("gone") else activeRoot

        override fun fastRoot(): String? = fastRoot

        override fun isApplication(window: Window) = window.application

        override fun isFocused(window: Window) = window.focused

        override fun isActive(window: Window) = window.active
    }

    private val source = FakeSource()
    private val logs = mutableListOf<String>()
    private val resolver = WindowRootResolver(source) { message, _ -> logs += message }

    private fun policy(
        mode: Mode = Mode.NORMAL,
        filter: ((Window) -> Boolean)? = null,
        fallback: Fallback = Fallback.NONE,
    ) = WindowRootPolicy(mode, filter, fallback)

    /* Without a filter: the active window root of the mode. */

    @Test
    fun withoutFilterTheModeDecidesBetweenActiveAndFastRoot() {
        source.windows = listOf(Window("app", focused = true))
        assertEquals(listOf("active"), resolver.roots(policy(Mode.NORMAL)))
        assertEquals(listOf("fast"), resolver.roots(policy(Mode.FAST)))
        assertEquals("active", resolver.currentRoot(policy(Mode.NORMAL)))
        assertEquals("fast", resolver.currentRoot(policy(Mode.FAST)))
        assertEquals("active", resolver.activeRoot(policy(Mode.NORMAL)))
        assertEquals("fast", resolver.activeRoot(policy(Mode.FAST)))
        assertTrue("the window list is not consulted", source.rootQueries.isEmpty())
    }

    @Test
    fun unknownActiveWindowYieldsNothingByDefault() {
        source.activeRoot = null
        source.fastRoot = null
        source.windows = listOf(Window("app", focused = true, active = true))
        assertEquals(emptyList<String>(), resolver.roots(policy(Mode.NORMAL)))
        assertEquals(emptyList<String>(), resolver.roots(policy(Mode.FAST)))
        assertNull(resolver.currentRoot(policy()))
        assertNull(resolver.activeRoot(policy()))
        assertTrue(source.rootQueries.isEmpty())
    }

    @Test
    fun throwingActiveRootCountsAsUnavailable() {
        source.activeRootThrows = true
        assertEquals(emptyList<String>(), resolver.roots(policy()))
        assertNull(resolver.activeRoot(policy()))
        assertEquals(2, logs.size)
    }

    /* Fallback to the application windows. */

    @Test
    fun fallbackUsesApplicationWindowsFocusedFirstThenActiveThenListOrder() {
        source.activeRoot = null
        source.windows = listOf(
            Window("ime", application = false, focused = true),
            Window("other"),
            Window("active", active = true),
            Window("focused", focused = true),
            Window("rootless", root = null, focused = true),
            Window("another"),
        )
        val fallback = policy(fallback = Fallback.APPLICATION_WINDOWS)
        assertEquals(listOf("root:focused", "root:active", "root:other", "root:another"), resolver.roots(fallback))
        assertEquals("root:focused", resolver.currentRoot(fallback))
        assertEquals("root:focused", resolver.activeRoot(fallback))
    }

    @Test
    fun fallbackAppliesToFastModeAsWell() {
        source.fastRoot = null
        source.windows = listOf(Window("app"))
        assertEquals(listOf("root:app"), resolver.roots(policy(Mode.FAST, fallback = Fallback.APPLICATION_WINDOWS)))
    }

    @Test
    fun fallbackIsNotUsedWhileTheActiveRootIsAvailable() {
        source.windows = listOf(Window("app", focused = true))
        assertEquals(listOf("active"), resolver.roots(policy(fallback = Fallback.APPLICATION_WINDOWS)))
        assertTrue(source.rootQueries.isEmpty())
    }

    @Test
    fun fallbackWithoutWindowsYieldsNothing() {
        source.activeRoot = null
        source.windows = null
        assertEquals(emptyList<String>(), resolver.roots(policy(fallback = Fallback.APPLICATION_WINDOWS)))
        source.windowsThrow = true
        assertEquals(emptyList<String>(), resolver.roots(policy(fallback = Fallback.APPLICATION_WINDOWS)))
        assertEquals(listOf("Unable to get accessibility windows"), logs)
    }

    /* With a filter: the roots of the passing windows, whatever the mode. */

    @Test
    fun filterSelectsWindowRootsRegardlessOfMode() {
        source.windows = listOf(Window("a"), Window("b"), Window("c", root = null), Window("d"))
        val filter = policy(Mode.FAST, filter = { it.name != "b" })
        assertEquals(listOf("root:a", "root:d"), resolver.roots(filter))
        assertEquals(listOf("a", "c", "d"), source.rootQueries)
    }

    @Test
    fun currentRootWithFilterIsTheFirstPassingWindowThatHasARootResolvedLazily() {
        source.windows = listOf(Window("skipped"), Window("rootless", root = null), Window("hit"), Window("later"))
        val filter = policy(filter = { it.name != "skipped" })
        assertEquals("root:hit", resolver.currentRoot(filter))
        assertEquals("later windows are not queried", listOf("rootless", "hit"), source.rootQueries)
    }

    @Test
    fun activeRootIgnoresTheFilter() {
        source.windows = listOf(Window("a"))
        assertEquals("active", resolver.activeRoot(policy(filter = { false })))
        assertEquals(emptyList<String>(), resolver.roots(policy(filter = { false })))
        assertNull(resolver.currentRoot(policy(filter = { false })))
    }

    @Test
    fun filterAndRootFailuresAreIsolatedAndLogged() {
        source.windows = listOf(Window("boom"), Window("stale", rootThrows = true), Window("ok"))
        val filter = policy(filter = { if (it.name == "boom") throw IllegalStateException("filter failed") else true })
        assertEquals(listOf("root:ok"), resolver.roots(filter))
        assertEquals(listOf("Unable to filter accessibility window", "Unable to get accessibility window root"), logs)
    }

    @Test
    fun filterWithoutWindowsYieldsNothing() {
        source.windows = null
        assertEquals(emptyList<String>(), resolver.roots(policy(filter = { true })))
        assertNull(resolver.currentRoot(policy(filter = { true })))
        source.windowsThrow = true
        assertEquals(emptyList<String>(), resolver.roots(policy(filter = { true })))
        assertEquals(listOf("Unable to get accessibility windows"), logs)
    }

    /* Policy helpers. */

    @Test
    fun legacyModeValuesMapOntoTheEnum() {
        assertEquals(Mode.NORMAL, WindowRootPolicy.modeOf(0))
        assertEquals(Mode.FAST, WindowRootPolicy.modeOf(1))
        assertEquals("only the fast bit matters", Mode.FAST, WindowRootPolicy.modeOf(3))
        assertEquals(Mode.NORMAL, WindowRootPolicy.modeOf(2))
    }

}
