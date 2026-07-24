package org.autojs.autojs.runtime.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ScaleCompatibilityTest {

    @Test
    fun rejectedSecondAssignmentDoesNotMutateTheBase() {
        val scale = Scale()
        scale.baseX = 1080

        assertThrows(IllegalArgumentException::class.java) {
            scale.baseX = 1440
        }
        assertEquals(1080, scale.baseX)
    }

    @Test
    fun reusableBaseValidationRejectsNonPositiveValues() {
        assertEquals(1, Scale.ensureBase(1))
        assertThrows(IllegalArgumentException::class.java) { Scale.ensureBase(0) }
        assertThrows(IllegalArgumentException::class.java) { Scale.ensureBase(-1) }
    }

}
