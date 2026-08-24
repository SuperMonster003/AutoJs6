package org.autojs.autojs.core.plugin.python

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PythonHostOcrPolicyTest {
    @Test
    fun validatesStrictUtf8LinesAndReturnsADefensiveList() {
        val source = mutableListOf<Any>("AutoJs6", "中文 OCR", "")

        val validated = PythonHostOcrPolicy.validate(source)

        assertEquals(listOf("AutoJs6", "中文 OCR", ""), validated)
        assertNotSame(source, validated)
        source[0] = "changed"
        assertEquals("AutoJs6", validated[0])
    }

    @Test
    fun rejectsMalformedOrNonTextEngineResults() {
        assertThrows(PythonHostCapabilityOcrFailedException::class.java) {
            PythonHostOcrPolicy.validate(listOf(7))
        }
        assertThrows(PythonHostCapabilityOcrFailedException::class.java) {
            PythonHostOcrPolicy.validate(listOf("\uD800"))
        }
    }

    @Test
    fun enforcesLineCountPerLineAndAggregateBounds() {
        assertThrows(PythonHostCapabilityResultLimitException::class.java) {
            PythonHostOcrPolicy.validate(List(PythonHostOcrPolicy.MAX_LINES + 1) { "x" })
        }
        assertThrows(PythonHostCapabilityResultLimitException::class.java) {
            PythonHostOcrPolicy.validate(listOf("x".repeat(PythonHostOcrPolicy.MAX_LINE_BYTES + 1)))
        }
        assertThrows(PythonHostCapabilityResultLimitException::class.java) {
            PythonHostOcrPolicy.validate(
                List(
                    PythonHostOcrPolicy.MAX_TOTAL_TEXT_BYTES / PythonHostOcrPolicy.MAX_LINE_BYTES + 1,
                ) { "x".repeat(PythonHostOcrPolicy.MAX_LINE_BYTES) },
            )
        }
    }
}
