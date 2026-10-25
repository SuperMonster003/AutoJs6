package org.autojs.autojs.runtime.api.modules.barcode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.NativeObject

class BarcodeCompatibilityTest {

    @Test
    fun allDispatchUsesTheLastArgument() {
        assertFalse(Barcode.shouldTakeAsAll(emptyArray()))
        assertTrue(Barcode.shouldTakeAsAll(arrayOf<Any?>("image", true)))
        assertFalse(Barcode.shouldTakeAsAll(arrayOf<Any?>(true, false)))
    }

    @Test
    fun allDispatchReadsIsAllFromOptions() {
        val enabled = NativeObject().also { it.put("isAll", it, true) }
        val disabled = NativeObject().also { it.put("isAll", it, false) }

        assertTrue(Barcode.shouldTakeAsAll(arrayOf<Any?>("image", enabled)))
        assertFalse(Barcode.shouldTakeAsAll(arrayOf<Any?>("image", disabled)))
    }

}
