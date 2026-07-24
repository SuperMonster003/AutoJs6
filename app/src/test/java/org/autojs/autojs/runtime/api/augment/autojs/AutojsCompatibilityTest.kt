package org.autojs.autojs.runtime.api.augment.autojs

import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.junit.Assert.assertThrows
import org.junit.Test

class AutojsCompatibilityTest {

    @Test
    fun unsupportedNumericRootModesThrow() {
        assertThrows(WrappedIllegalArgumentException::class.java) {
            Autojs.resolveRootMode(2)
        }
        assertThrows(WrappedIllegalArgumentException::class.java) {
            Autojs.resolveRootMode(1.2)
        }
    }

}
