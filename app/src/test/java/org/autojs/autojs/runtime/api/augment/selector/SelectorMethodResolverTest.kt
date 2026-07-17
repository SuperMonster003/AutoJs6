package org.autojs.autojs.runtime.api.augment.selector

import org.autojs.autojs.core.accessibility.UiSelector
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.TopLevel
import org.mozilla.javascript.regexp.NativeRegExp

class SelectorMethodResolverTest {

    private lateinit var context: Context
    private lateinit var scope: TopLevel

    @Before
    fun enterContext() {
        context = Context.enter()
        scope = context.initStandardObjects()
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    @Test
    fun nativeRegExpSelectsTheMatchingOverloadWithoutTryingStringFirst() {
        val stringMethod = UiSelector::class.java.getMethod("idMatches", String::class.java)
        val regexpMethod = UiSelector::class.java.getMethod("idMatches", NativeRegExp::class.java)
        val regexp = context.evaluateString(scope, "/target/i", "<test>", 1, null) as NativeRegExp

        val resolution = SelectorMethodResolver.resolve(listOf(stringMethod, regexpMethod), arrayOf(regexp))

        assertSame(regexpMethod, resolution?.method)
        assertSame(regexp, resolution?.arguments?.single())
    }

    @Test
    fun stringSelectsTheStringOverloadRegardlessOfCandidateOrder() {
        val stringMethod = UiSelector::class.java.getMethod("idMatches", String::class.java)
        val regexpMethod = UiSelector::class.java.getMethod("idMatches", NativeRegExp::class.java)

        val resolution = SelectorMethodResolver.resolve(listOf(regexpMethod, stringMethod), arrayOf("target"))

        assertSame(stringMethod, resolution?.method)
    }

    @Test
    fun resolvedRegexpOverloadIsReadyForDirectInvocation() {
        val stringMethod = Fixture::class.java.getMethod("pick", String::class.java)
        val regexpMethod = Fixture::class.java.getMethod("pick", NativeRegExp::class.java)
        val regexp = context.evaluateString(scope, "/target/", "<test>", 1, null) as NativeRegExp
        val fixture = Fixture()

        val resolution = requireNotNull(
            SelectorMethodResolver.resolve(listOf(stringMethod, regexpMethod), arrayOf(regexp)),
        )
        val result = resolution.method.invoke(fixture, *resolution.arguments)

        assertEquals("regexp", result)
        assertEquals(0, fixture.stringCalls)
        assertEquals(1, fixture.regexpCalls)
    }

    @Test
    fun extraArgumentsAreNotSilentlyDiscarded() {
        val stringMethod = UiSelector::class.java.getMethod("idMatches", String::class.java)

        val resolution = SelectorMethodResolver.resolve(listOf(stringMethod), arrayOf("target", "ignored"))

        assertNull(resolution)
    }

    @Test
    fun numericVarargsKeepTheExistingNumericConversions() {
        val method = Fixture::class.java.getMethod(
            "collect",
            String::class.java,
            IntArray::class.java,
        )

        val resolution = SelectorMethodResolver.resolve(listOf(method), arrayOf<Any?>("values", 1.2, 2.8))

        assertSame(method, resolution?.method)
        assertEquals("values", resolution?.arguments?.get(0))
        assertArrayEquals(intArrayOf(1, 3), resolution?.arguments?.get(1) as IntArray)
    }

    class Fixture {

        var stringCalls = 0
        var regexpCalls = 0

        @Suppress("unused")
        fun pick(value: String): String {
            stringCalls++
            return "string"
        }

        @Suppress("unused")
        fun pick(value: NativeRegExp): String {
            regexpCalls++
            return "regexp"
        }

        @Suppress("unused")
        fun collect(prefix: String, vararg values: Int) = prefix to values
    }
}
