package org.autojs.autojs.runtime.api.modules.util

import org.autojs.autojs.runtime.api.augment.util.Java
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Wrapper

class JavaCompatibilityTest {

    @Before
    fun enterContext() {
        Context.enter()
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    @Test
    fun javaArrayDetectionUnwrapsRhinoWrappers() {
        assertTrue(Util.isJavaArray(arrayOf(intArrayOf(1))))
        assertTrue(Util.isJavaArray(arrayOf(TestWrapper(arrayOf("value")))))
        assertFalse(Util.isJavaArray(arrayOf(TestWrapper("value"))))
    }

    @Test
    fun instanceOfUnwrapsTheSubject() {
        assertTrue(Java.instanceof(arrayOf<Any?>(TestWrapper("value"), String::class.java)))
        assertFalse(Java.instanceof(arrayOf<Any?>(TestWrapper("value"), Number::class.java)))
    }

    @Test
    fun legacyInstanceOfAliasIsExported() {
        val target = newNativeObject()
        Java.assign(target)

        val function = target.get("instanceOf", target) as BaseFunction
        assertTrue(callFunction(function, target, target, arrayOf<Any?>(TestWrapper("value"), String::class.java)) as Boolean)
    }

    @Test
    fun legacyCheckStringParamIsExported() {
        val target = newNativeObject()
        Util.assign(target)

        val function = target.get("checkStringParam", target) as BaseFunction
        assertTrue(callFunction(function, target, target, arrayOf<Any?>("root", "root")) as Boolean)
    }

    private class TestWrapper(private val value: Any?) : Wrapper {
        override fun unwrap(): Any? = value
    }

}
