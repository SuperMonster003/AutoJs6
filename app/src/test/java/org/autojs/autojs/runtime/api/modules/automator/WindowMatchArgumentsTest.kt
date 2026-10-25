package org.autojs.autojs.runtime.api.modules.automator

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
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope

/**
 * [WindowMatchArguments] under a bare Rhino context: the window filter object of
 * `auto.setWindowFilter` / `auto.findWindows` / `auto.findWindowRoots` and its errors.
 * zh-CN: 裸 Rhino 上下文下的 [WindowMatchArguments]: `auto.setWindowFilter` / `auto.findWindows` / `auto.findWindowRoots` 的窗口过滤器对象及其错误.
 */
class WindowMatchArgumentsTest {

    private lateinit var cx: Context
    private lateinit var scope: VarScope

    @Before
    fun setUp() {
        cx = Context.enter()
        scope = cx.initStandardObjects()
    }

    @After
    fun tearDown() = Context.exit()

    private fun js(source: String): Any? = cx.evaluateString(scope, source, "test", 1, null)

    private fun error(source: String): WrappedIllegalArgumentException =
        assertThrows(WrappedIllegalArgumentException::class.java) { WindowMatchArguments.parse(js(source), "auto.findWindows") }

    @Test
    fun nothingMeansEveryWindow() {
        assertTrue(WindowMatchArguments.parse(null).isEmpty)
        assertTrue(WindowMatchArguments.parse(Undefined.instance).isEmpty)
        assertTrue(WindowMatchArguments.parse(js("({})")).isEmpty)
    }

    @Test
    fun everyKeyIsParsed() {
        val match = WindowMatchArguments.parse(js("({ type: ['application', 'system'], packageName: /^com\\./, title: 'Settings', displayId: 0, active: true, focused: false, id: 7, layer: 2 })"))
        assertEquals(setOf(1, 3), match.types)
        assertTrue(match.packageName!!.test("com.android.settings"))
        assertFalse(match.packageName.test("org.autojs.autojs6"))
        assertFalse(match.packageName.test(null))
        assertTrue(match.title!!.test("Settings"))
        assertFalse(match.title.test("settings"))
        assertEquals(0, match.displayId)
        assertEquals(true, match.active)
        assertEquals(false, match.focused)
        assertEquals(7, match.id)
        assertEquals(2, match.layer)
        assertEquals("{ type: application|system, packageName: /^com\\./, title: \"Settings\", displayId: 0, active: true, focused: false, id: 7, layer: 2 }", match.toString())
    }

    @Test
    fun aSingleTypeNameAndOtherSpellingsWork() {
        assertEquals(setOf(2), WindowMatchArguments.parse(js("({ type: 'inputMethod' })")).types)
        assertEquals(setOf(4), WindowMatchArguments.parse(js("({ type: 'TYPE_ACCESSIBILITY_OVERLAY' })")).types)
        assertEquals(setOf(5, 6), WindowMatchArguments.parse(js("({ type: ['split_screen_divider', 'magnificationOverlay'] })")).types)
        assertNull(WindowMatchArguments.parse(js("({ type: undefined, title: null })")).types)
    }

    @Test
    fun aRegexTitleIsASearch() {
        val match = WindowMatchArguments.parse(js("({ title: /^Sett/i })"))
        assertTrue(match.title!!.test("settings"))
        assertFalse(match.title.test("My settings"))
    }

    @Test
    fun unknownKeysAndWrongTypesAreRefusedWithTheFunctionName() {
        assertTrue(error("({ foo: 1 })").message!!.let { it.contains("auto.findWindows") && it.contains("unknown key \"foo\"") })
        assertTrue(error("({ type: 'bogus' })").message!!.contains("unknown window type \"bogus\""))
        assertTrue(error("({ type: 3 })").message!!.contains("a window type name expected"))
        assertTrue(error("({ displayId: 1.5 })").message!!.contains("auto.findWindows.displayId: an integer expected"))
        assertTrue(error("({ id: 'x' })").message!!.contains("an integer expected"))
        assertTrue(error("({ active: 'yes' })").message!!.contains("auto.findWindows.active: a boolean expected"))
        assertTrue(error("({ packageName: 1 })").message!!.contains("a string or a regular expression expected"))
        assertTrue(error("'com.x'").message!!.contains("an object with type / packageName"))
        assertTrue(error("[1]").message!!.contains("an object with"))
    }

}
