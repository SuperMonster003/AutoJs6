package org.autojs.autojs.runtime.api.modules.automator

import org.autojs.autojs.runtime.api.modules.automator.EventWaitArguments.Filter
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.VarScope

/**
 * [EventWaitArguments] under a bare Rhino context: the forms of `waitForIdle` / `waitForEvent` /
 * `waitForToast` / `waitForNotification`, the filters, the timeouts and the errors.
 * zh-CN: 裸 Rhino 上下文下的 [EventWaitArguments]: `waitForIdle` / `waitForEvent` / `waitForToast` / `waitForNotification` 的各种形式,
 * 过滤器, 超时与错误.
 */
class EventWaitArgumentsTest {

    private lateinit var cx: Context
    private lateinit var scope: VarScope

    private val types = mapOf("WINDOW_STATE_CHANGED" to 32, "WINDOW_CONTENT_CHANGED" to 2048, "WINDOWS_CHANGED" to 4194304, "VIEW_CLICKED" to 1)

    private fun resolve(name: String): Int = types[name] ?: throw IllegalArgumentException("Unknown event: $name")

    @Before
    fun setUp() {
        cx = Context.enter()
        scope = cx.initStandardObjects()
    }

    @After
    fun tearDown() = Context.exit()

    private fun js(source: String): Any? = cx.evaluateString(scope, source, "test", 1, null)

    private fun property(vararg pairs: Pair<String, String?>): (String) -> String? = { key -> pairs.toMap()[key] }

    @Test
    fun idleDefaultsToHalfASecondOfWindowEvents() {
        val idle = EventWaitArguments.idle(emptyArray(), 10_000, ::resolve)
        assertEquals(500L, idle.quietFor)
        assertEquals(10_000L, idle.timeout)
        assertEquals(setOf(32, 2048, 4194304), idle.types)
        assertEquals(listOf("window_state_changed", "window_content_changed", "windows_changed"), idle.typeNames)
        assertFalse(idle.explicitTypes)
        assertEquals("quiet 500 ms", idle.detail)
        assertEquals(250L, EventWaitArguments.idle(arrayOf<Any?>(250.0), 10_000, ::resolve).quietFor)
        assertEquals(500L, EventWaitArguments.idle(arrayOf<Any?>(null), 10_000, ::resolve).quietFor)
    }

    @Test
    fun idleOptionsSetQuietForTimeoutAndTypes() {
        val idle = EventWaitArguments.idle(arrayOf<Any?>(js("({ quietFor: 300, timeout: 2000, eventTypes: ['viewClicked', 'TYPE_WINDOW_STATE_CHANGED'] })")), 10_000, ::resolve)
        assertEquals(300L, idle.quietFor)
        assertEquals(2_000L, idle.timeout)
        assertEquals(setOf(1, 32), idle.types)
        assertTrue(idle.explicitTypes)
        assertEquals("quiet 300 ms, events view_clicked|window_state_changed", idle.detail)

        val any = EventWaitArguments.idle(arrayOf<Any?>(js("({ eventTypes: '*' })")), 10_000, ::resolve)
        assertNull(any.types)
        assertEquals("quiet 500 ms, events *", any.detail)

        assertEquals(Long.MAX_VALUE, EventWaitArguments.idle(arrayOf<Any?>(js("({ timeout: Infinity })")), 10_000, ::resolve).timeout)
    }

    @Test
    fun idleRejectsUnknownOptionsBadNumbersAndUnknownTypes() {
        assertThrows(WrappedIllegalArgumentException::class.java) { EventWaitArguments.idle(arrayOf<Any?>(js("({ quiet: 300 })")), 10_000, ::resolve) }
        assertThrows(WrappedIllegalArgumentException::class.java) { EventWaitArguments.idle(arrayOf<Any?>(-1.0), 10_000, ::resolve) }
        assertThrows(WrappedIllegalArgumentException::class.java) { EventWaitArguments.idle(arrayOf<Any?>(js("NaN")), 10_000, ::resolve) }
        assertThrows(WrappedIllegalArgumentException::class.java) { EventWaitArguments.idle(arrayOf<Any?>(js("({ eventTypes: 'no_such_event' })")), 10_000, ::resolve) }
        assertThrows(WrappedIllegalArgumentException::class.java) { EventWaitArguments.idle(arrayOf<Any?>("500"), 10_000, ::resolve) }
        assertThrows(WrappedIllegalArgumentException::class.java) { EventWaitArguments.idle(arrayOf<Any?>(1.0, 2.0), 10_000, ::resolve) }
    }

    @Test
    fun eventTakesATypeAFilterAndATimeoutInEitherOrder() {
        val event = EventWaitArguments.event(arrayOf<Any?>("window_state_changed", js("({ packageName: 'com.android.settings', text: /Wi.?Fi/ })"), 3_000.0), 10_000, ::resolve)
        assertEquals(setOf(32), event.types)
        assertEquals(3_000L, event.timeout)
        val filter = event.filter as Filter.Properties
        assertTrue(filter.matches(property("packageName" to "com.android.settings", "text" to "Settings WiFi")))
        assertTrue(filter.matches(property("packageName" to "com.android.settings", "text" to "Wi-Fi")))
        assertFalse(filter.matches(property("packageName" to "com.android.settings", "text" to "Bluetooth")))
        assertFalse(filter.matches(property("packageName" to "com.android.settings.suffix", "text" to "WiFi")))
        assertFalse(filter.matches(property("packageName" to null, "text" to "WiFi")))
        assertEquals("window_state_changed { packageName: \"com.android.settings\", text: /Wi.?Fi/ }", event.detail)

        val timeoutFirst = EventWaitArguments.event(arrayOf<Any?>(js("['viewClicked', 'windowsChanged']"), 500.0, js("(function (e) { return true })")), 10_000, ::resolve)
        assertEquals(setOf(1, 4194304), timeoutFirst.types)
        assertEquals(500L, timeoutFirst.timeout)
        assertTrue(timeoutFirst.filter is Filter.Script)
        assertEquals("view_clicked|windows_changed fn", timeoutFirst.detail)

        val any = EventWaitArguments.event(arrayOf<Any?>("*"), 10_000, ::resolve)
        assertNull(any.types)
        assertNull(any.filter)
        assertEquals(10_000L, any.timeout)
        assertEquals("any", any.detail)
        assertNull(EventWaitArguments.event(emptyArray(), 10_000, ::resolve).types)
        assertEquals(700L, EventWaitArguments.event(arrayOf<Any?>(null, null, 700.0), 10_000, ::resolve).timeout)
    }

    @Test
    fun eventRejectsUnknownTypesDuplicatesAndOddFilters() {
        assertThrows(WrappedIllegalArgumentException::class.java) { EventWaitArguments.event(arrayOf<Any?>("no_such_event"), 10_000, ::resolve) }
        assertThrows(WrappedIllegalArgumentException::class.java) { EventWaitArguments.event(arrayOf<Any?>(js("[1]")), 10_000, ::resolve) }
        assertThrows(WrappedIllegalArgumentException::class.java) { EventWaitArguments.event(arrayOf<Any?>("*", 1.0, 2.0), 10_000, ::resolve) }
        assertThrows(WrappedIllegalArgumentException::class.java) { EventWaitArguments.event(arrayOf<Any?>("*", js("({})"), js("({})")), 10_000, ::resolve) }
        assertThrows(WrappedIllegalArgumentException::class.java) { EventWaitArguments.event(arrayOf<Any?>("*", "text"), 10_000, ::resolve) }
        assertThrows(WrappedIllegalArgumentException::class.java) { EventWaitArguments.event(arrayOf<Any?>("*", js("({ title: 'x' })")), 10_000, ::resolve) }
        assertThrows(WrappedIllegalArgumentException::class.java) { EventWaitArguments.event(arrayOf<Any?>("*", js("({ text: 1 })")), 10_000, ::resolve) }
        assertThrows(WrappedIllegalArgumentException::class.java) { EventWaitArguments.event(arrayOf<Any?>("*", null, 1.0, 2.0), 10_000, ::resolve) }
    }

    @Test
    fun toastTakesATextARegexAFunctionAnObjectOrJustATimeout() {
        val text = EventWaitArguments.toast(arrayOf<Any?>("hello"), 10_000)
        val textFilter = text.filter as Filter.Text
        assertTrue(textFilter.matches(property("text" to "say hello there")))
        assertFalse(textFilter.matches(property("text" to "bye")))
        assertFalse(textFilter.matches(property("text" to null)))
        assertEquals("text ~ \"hello\"", text.detail)
        assertEquals(10_000L, text.timeout)

        val regex = EventWaitArguments.toast(arrayOf<Any?>(js("/^ok$/i"), 2_000.0), 10_000)
        assertTrue((regex.filter as Filter.Text).matches(property("text" to "OK")))
        assertFalse(regex.filter.matches(property("text" to "not ok")))
        assertEquals(2_000L, regex.timeout)
        assertEquals("text ~ /^ok$/i", regex.detail)

        assertTrue(EventWaitArguments.toast(arrayOf<Any?>(js("(function (t) { return true })")), 10_000).filter is Filter.Script)

        val obj = EventWaitArguments.toast(arrayOf<Any?>(js("({ packageName: 'com.example', text: 'done' })")), 10_000)
        val objFilter = obj.filter as Filter.Properties
        assertTrue(objFilter.matches(property("packageName" to "com.example", "text" to "all done")))
        assertFalse(objFilter.matches(property("packageName" to "com.example.other", "text" to "all done")))

        val timeoutOnly = EventWaitArguments.toast(arrayOf<Any?>(1_500.0), 10_000)
        assertNull(timeoutOnly.filter)
        assertEquals(1_500L, timeoutOnly.timeout)
        assertEquals("any", timeoutOnly.detail)

        assertNull(EventWaitArguments.toast(emptyArray(), 10_000).filter)
        assertEquals(Long.MAX_VALUE, EventWaitArguments.toast(arrayOf<Any?>(null, js("Infinity")), 10_000).timeout)
    }

    @Test
    fun toastRejectsForeignKeysBadTimeoutsAndExtraArguments() {
        assertThrows(WrappedIllegalArgumentException::class.java) { EventWaitArguments.toast(arrayOf<Any?>(js("({ title: 'x' })")), 10_000) }
        assertThrows(WrappedIllegalArgumentException::class.java) { EventWaitArguments.toast(arrayOf<Any?>("x", "abc"), 10_000) }
        assertThrows(WrappedIllegalArgumentException::class.java) { EventWaitArguments.toast(arrayOf<Any?>(1_000.0, 2_000.0), 10_000) }
        assertThrows(WrappedIllegalArgumentException::class.java) { EventWaitArguments.toast(arrayOf<Any?>("x", 1.0, 2.0), 10_000) }
        assertThrows(WrappedIllegalArgumentException::class.java) { EventWaitArguments.toast(arrayOf<Any?>(true), 10_000) }
    }

    @Test
    fun notificationTextMatchesTheTitleOrTheText() {
        val text = EventWaitArguments.notification(arrayOf<Any?>("Download"), 10_000)
        val filter = text.filter as Filter.Text
        assertTrue(filter.matches(property("title" to "Download complete", "text" to "file.zip")))
        assertTrue(filter.matches(property("title" to "Files", "text" to "Download finished")))
        assertFalse(filter.matches(property("title" to "Files", "text" to "Upload finished")))
        assertEquals("title|text ~ \"Download\"", text.detail)

        val obj = EventWaitArguments.notification(arrayOf<Any?>(js("({ packageName: 'com.example', title: /^Down/, text: 'zip' })"), 5.0), 10_000)
        val objFilter = obj.filter as Filter.Properties
        assertTrue(objFilter.matches(property("packageName" to "com.example", "title" to "Download", "text" to "a.zip")))
        assertFalse(objFilter.matches(property("packageName" to "com.example", "title" to "Upload", "text" to "a.zip")))
        assertEquals(5L, obj.timeout)
        assertEquals("{ packageName: \"com.example\", title: /^Down/, text: \"zip\" }", obj.detail)

        assertTrue(EventWaitArguments.notification(arrayOf<Any?>(js("({})")), 10_000).filter.let { it is Filter.Properties && it.matchers.isEmpty() && it.matches(property()) })
        assertThrows(WrappedIllegalArgumentException::class.java) { EventWaitArguments.notification(arrayOf<Any?>(js("({ className: 'x' })")), 10_000) }
    }

    @Test
    fun typeNamesAreNormalisedFromAnySpelling() {
        assertEquals("WINDOW_STATE_CHANGED", EventWaitArguments.normalizeTypeName("windowStateChanged"))
        assertEquals("WINDOW_STATE_CHANGED", EventWaitArguments.normalizeTypeName(" window_state_changed "))
        assertEquals("WINDOW_STATE_CHANGED", EventWaitArguments.normalizeTypeName("TYPE_WINDOW_STATE_CHANGED"))
        assertEquals("VIEW_TEXT_SELECTION_CHANGED", EventWaitArguments.normalizeTypeName("viewTextSelectionChanged"))
        assertEquals("ANNOUNCEMENT", EventWaitArguments.normalizeTypeName("announcement"))
    }

}
