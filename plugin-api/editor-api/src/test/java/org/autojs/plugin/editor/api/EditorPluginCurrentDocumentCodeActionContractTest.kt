package org.autojs.plugin.editor.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPluginCurrentDocumentCodeActionContractTest {

    @Test
    fun contractFourAcceptsBoundedAutoImportAndSpellingActions() {
        assertTrue(EditorPluginContract.MAX_SUPPORTED_VERSION >= 4)
        assertTrue(EditorPluginCurrentDocumentCodeActionContract.isSupported(autoImport()))
        assertTrue(
            EditorPluginCurrentDocumentCodeActionContract.isSupported(
                autoImport().copy(
                    kind = EditorPluginCurrentDocumentCodeActionKind.SPELLING_CORRECTION,
                    title = "Change spelling to 'sharedAnswer'",
                    diagnosticCode = 2552,
                    edits = listOf(EditorPluginTextEdit(24, 36, "sharedAnswer")),
                ),
            ),
        )
    }

    @Test
    fun rejectsUnboundNoOpAndMalformedMetadata() {
        val action = autoImport()
        assertFalse(
            EditorPluginCurrentDocumentCodeActionContract.isSupported(
                action.copy(baseContentSha256 = action.resultContentSha256),
            ),
        )
        assertFalse(
            EditorPluginCurrentDocumentCodeActionContract.isSupported(
                action.copy(title = "Add import\nwithout review"),
            ),
        )
        assertFalse(
            EditorPluginCurrentDocumentCodeActionContract.isSupported(
                action.copy(diagnosticCode = 0),
            ),
        )
        assertFalse(
            EditorPluginCurrentDocumentCodeActionContract.isSupported(
                action.copy(edits = listOf(EditorPluginTextEdit(4, 4, ""))),
            ),
        )
        assertFalse(
            EditorPluginCurrentDocumentCodeActionContract.isSupported(
                action.copy(edits = listOf(EditorPluginTextEdit(4, 4, "\uD800"))),
            ),
        )
    }

    @Test
    fun rejectsOverlappingUnsortedAndOverBudgetEdits() {
        val action = autoImport()
        assertFalse(
            EditorPluginCurrentDocumentCodeActionContract.isSupported(
                action.copy(
                    edits = listOf(
                        EditorPluginTextEdit(0, 8, "import"),
                        EditorPluginTextEdit(7, 9, "x"),
                    ),
                ),
            ),
        )
        assertFalse(
            EditorPluginCurrentDocumentCodeActionContract.isSupported(
                action.copy(
                    edits = listOf(
                        EditorPluginTextEdit(10, 11, "x"),
                        EditorPluginTextEdit(2, 3, "y"),
                    ),
                ),
            ),
        )
        assertFalse(
            EditorPluginCurrentDocumentCodeActionContract.isSupported(
                action.copy(
                    edits = listOf(
                        EditorPluginTextEdit(
                            0,
                            0,
                            "x".repeat(
                                EditorPluginCurrentDocumentCodeActionContract
                                    .MAX_REPLACEMENT_TEXT_BYTES + 1,
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun autoImport(): EditorPluginCurrentDocumentCodeAction =
        EditorPluginCurrentDocumentCodeAction(
            kind = EditorPluginCurrentDocumentCodeActionKind.AUTO_IMPORT,
            title = "Add import from './shared'",
            diagnosticCode = 2304,
            baseContentSha256 = "1".repeat(64),
            resultContentSha256 = "2".repeat(64),
            edits = listOf(
                EditorPluginTextEdit(
                    startOffset = 0,
                    endOffset = 0,
                    newText = "import { sharedAnswer } from './shared';\n\n",
                ),
            ),
        )
}
