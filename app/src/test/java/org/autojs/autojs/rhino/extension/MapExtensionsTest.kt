package org.autojs.autojs.rhino.extension

import org.autojs.autojs.rhino.extension.MapExtensions.toNativeObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable

class MapExtensionsTest {

    @Before
    fun enterContext() {
        Context.enter()
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    @Test
    fun pairValueIsWrittenToNestedObjectWithoutPollutingOuterObject() {
        val outer = mapOf("nested" to ("inner" to 42)).toNativeObject()
        val nested = outer.get("nested", outer) as NativeObject

        assertEquals(42, nested.get("inner", nested))
        assertSame(Scriptable.NOT_FOUND, outer.get("inner", outer))
    }

}
