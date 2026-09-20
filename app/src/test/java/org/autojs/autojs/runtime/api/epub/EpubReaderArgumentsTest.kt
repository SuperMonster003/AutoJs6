package org.autojs.autojs.runtime.api.epub

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.epub.EpubPluginException
import org.autojs.plugin.epub.api.EpubContract
import org.autojs.plugin.epub.api.EpubErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The reader-session argument shapes of the `epub` API (plugin roadmap P6.2, appendix A.4), without Rhino. */
class EpubReaderArgumentsTest {

    @Test
    fun readOptionsCarryOneStartTargetAndThePreferences() {
        val none = EpubScriptArguments.read(null)
        assertNull(none.locatorJson)
        assertNull(none.href)
        assertNull(none.progression)
        assertNull(none.preferencesJson)

        val href = EpubScriptArguments.read(json("""{"href":"OEBPS/c2.xhtml#s1"}"""))
        assertEquals("OEBPS/c2.xhtml#s1", href.href)
        assertEquals(0.5, EpubScriptArguments.read(json("""{"progression":0.5}""")).progression)
        assertEquals(1.0, EpubScriptArguments.read(json("""{"progression":1}""")).progression)
        val locator = EpubScriptArguments.read(json("""{"locator":{"href":"OEBPS/c1.xhtml","type":"application/xhtml+xml","locations":{"progression":0.2}},"preferences":{"fontSize":1.5,"theme":"dark"}}"""))
        assertEquals("""{"href":"OEBPS/c1.xhtml","type":"application/xhtml+xml","locations":{"progression":0.2}}""", locator.locatorJson)
        assertEquals("""{"fontSize":1.5,"theme":"dark"}""", locator.preferencesJson)
        assertNull(locator.href)
        assertNull(EpubScriptArguments.read(json("""{"href":null,"progression":null}""")).href)

        assertTrue(invalidMessage { EpubScriptArguments.read(json("""{"href":"a.xhtml","progression":0.2}""")) }.contains("at most one start target"))
        assertTrue(invalidMessage { EpubScriptArguments.read(json("""{"progression":1.5}""")) }.contains("'options.progression' must be a number between 0 and 1"))
        assertTrue(invalidMessage { EpubScriptArguments.read(json("""{"progression":"half"}""")) }.contains("'options.progression' must be a number between 0 and 1"))
        assertTrue(invalidMessage { EpubScriptArguments.read(json("""{"locator":{"type":"x"}}""")) }.contains("'options.locator' must carry a non-blank 'href'"))
        assertTrue(invalidMessage { EpubScriptArguments.read(json("""{"locator":"OEBPS/c1.xhtml"}""")) }.contains("'options.locator' must be a locator object"))
        assertTrue(invalidMessage { EpubScriptArguments.read(json("""{"preferences":[]}""")) }.contains("'options.preferences' must be an object"))
        assertTrue(invalidMessage { EpubScriptArguments.read(json("""{"href":""}""")) }.contains("'options.href' must not be blank"))
        assertTrue(invalidMessage { EpubScriptArguments.read(json("""{"displayName":"x"}""")) }.contains("unknown option 'options.displayName'"))
        assertTrue(invalidMessage { EpubScriptArguments.read(json("\"OEBPS/c1.xhtml\"")) }.contains("'options' must be an object"))
    }

    @Test
    fun goToTargetsAreHrefProgressionOrLocator() {
        val href = EpubScriptArguments.goTo(json("\"OEBPS/c2.xhtml\""))
        assertEquals("OEBPS/c2.xhtml", href.href)
        assertNull(href.locatorJson)
        assertNull(href.progression)
        assertEquals(0.0, EpubScriptArguments.goTo(json("0")).progression)
        assertEquals(0.25, EpubScriptArguments.goTo(json("0.25")).progression)
        assertEquals("""{"href":"OEBPS/c1.xhtml","locations":{"position":3}}""", EpubScriptArguments.goTo(json("""{"href":"OEBPS/c1.xhtml","locations":{"position":3}}""")).locatorJson)
        assertTrue(invalidMessage { EpubScriptArguments.goTo(null) }.contains("'target' is required"))
        assertTrue(invalidMessage { EpubScriptArguments.goTo(json("1.5")) }.contains("'target' must be a number between 0 and 1, got 1.5"))
        assertTrue(invalidMessage { EpubScriptArguments.goTo(json("-0.1")) }.contains("between 0 and 1"))
        assertTrue(invalidMessage { EpubScriptArguments.goTo(json("true")) }.contains("'target' must be an href string, a progression number or a locator object"))
        assertTrue(invalidMessage { EpubScriptArguments.goTo(json("[]")) }.contains("got an array"))
        assertTrue(invalidMessage { EpubScriptArguments.goTo(json("""{"href":" "}""")) }.contains("non-blank 'href'"))
        assertTrue(invalidMessage { EpubScriptArguments.goTo(json("\"\"")) }.contains("'target' must not be blank"))
    }

    @Test
    fun locatorsAndPreferencesAreBoundedByTheContractCeilings() {
        val big = "x".repeat(EpubContract.MAX_LOCATOR_BYTES)
        val locator = try {
            EpubScriptArguments.goTo(json("""{"href":"OEBPS/c1.xhtml","title":"$big"}"""))
            fail("expected LIMIT_EXCEEDED")
            throw AssertionError()
        } catch (e: EpubPluginException) {
            e
        }
        assertEquals(EpubErrorCodes.LIMIT_EXCEEDED, locator.code)
        assertTrue(locator.error.message.contains("'target' exceeds ${EpubContract.MAX_LOCATOR_BYTES} bytes"))

        assertEquals("""{"fontSize":1.2,"nope":true}""", EpubScriptArguments.preferences(json("""{"fontSize":1.2,"nope":true}""")))
        assertEquals("{}", EpubScriptArguments.preferences(json("{}")))
        assertTrue(invalidMessage { EpubScriptArguments.preferences(null) }.contains("'preferences' is required"))
        assertTrue(invalidMessage { EpubScriptArguments.preferences(json("\"dark\"")) }.contains("'preferences' must be an object"))
        val preferences = try {
            EpubScriptArguments.preferences(json("""{"fontFamily":"${"y".repeat(EpubContract.MAX_PREFERENCES_BYTES)}"}"""))
            fail("expected LIMIT_EXCEEDED")
            throw AssertionError()
        } catch (e: EpubPluginException) {
            e
        }
        assertEquals(EpubErrorCodes.LIMIT_EXCEEDED, preferences.code)
    }

    @Test
    fun closeOptionsCarryFinishOnly() {
        assertFalse(EpubScriptArguments.close(null).finish)
        assertFalse(EpubScriptArguments.close(json("{}")).finish)
        assertTrue(EpubScriptArguments.close(json("""{"finish":true}""")).finish)
        assertTrue(invalidMessage { EpubScriptArguments.close(json("""{"finish":1}""")) }.contains("'options.finish' must be a boolean"))
        assertTrue(invalidMessage { EpubScriptArguments.close(json("""{"reason":"x"}""")) }.contains("unknown option 'options.reason'"))
        assertTrue(invalidMessage { EpubScriptArguments.close(json("true")) }.contains("'options' must be an object"))
    }

    private fun json(text: String): JsonElement = JsonParser.parseString(text)

    private fun invalidMessage(block: () -> Unit): String {
        try {
            block()
        } catch (e: EpubPluginException) {
            assertEquals(EpubErrorCodes.INVALID_ARGUMENT, e.code)
            return e.error.message
        }
        fail("expected INVALID_ARGUMENT")
        throw AssertionError()
    }
}
