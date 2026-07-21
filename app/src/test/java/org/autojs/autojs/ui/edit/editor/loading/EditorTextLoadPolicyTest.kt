package org.autojs.autojs.ui.edit.editor.loading

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorTextLoadPolicyTest {

    @Test
    fun aceUnsafeLineUsesStrictBoundary() {
        assertFalse(
            EditorTextLoadPolicy.hasAceUnsafeLine(
                "x".repeat(EditorTextLoadPolicy.ACE_UNSAFE_LINE_LENGTH),
            ),
        )
        assertTrue(
            EditorTextLoadPolicy.hasAceUnsafeLine(
                "x".repeat(EditorTextLoadPolicy.ACE_UNSAFE_LINE_LENGTH + 1),
            ),
        )
    }

    @Test
    fun largeMultilineDocumentRemainsSafe() {
        val text = buildString {
            repeat(180) {
                append("x".repeat(1_000))
                append(if (it % 2 == 0) "\r\n" else "\n")
            }
        }

        assertFalse(EditorTextLoadPolicy.hasAceUnsafeLine(text))
    }

    @Test
    fun utf16MisdecodeShapedSingleLineIsUnsafe() {
        assertTrue(EditorTextLoadPolicy.hasAceUnsafeLine("\u0a20".repeat(177_772)))
    }

    @Test
    fun sizeableAceDocumentStartsInLightweightMode() {
        assertFalse(
            EditorTextLoadPolicy.requiresAceLightweightMode(
                "x\n".repeat(EditorTextLoadPolicy.ACE_LIGHTWEIGHT_DOCUMENT_LENGTH / 2 - 1),
            ),
        )
        assertTrue(
            EditorTextLoadPolicy.requiresAceLightweightMode(
                "x\n".repeat(EditorTextLoadPolicy.ACE_LIGHTWEIGHT_DOCUMENT_LENGTH / 2),
            ),
        )
    }
}
