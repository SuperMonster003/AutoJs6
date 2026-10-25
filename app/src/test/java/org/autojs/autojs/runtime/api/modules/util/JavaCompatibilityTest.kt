package org.autojs.autojs.runtime.api.modules.util

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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

    private class TestWrapper(private val value: Any?) : Wrapper {
        override fun unwrap(): Any? = value
    }

}
