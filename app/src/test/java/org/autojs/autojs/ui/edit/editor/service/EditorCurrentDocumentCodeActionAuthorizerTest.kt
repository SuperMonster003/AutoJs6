package org.autojs.autojs.ui.edit.editor.service

import org.autojs.plugin.editor.api.EditorPluginCurrentDocumentCodeAction
import org.autojs.plugin.editor.api.EditorPluginCurrentDocumentCodeActionKind
import org.autojs.plugin.editor.api.EditorPluginTextEdit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class EditorCurrentDocumentCodeActionAuthorizerTest {

    @Test
    fun authorizesExactCurrentDocumentReplay() {
        val base = "const value = sharedAnswer;\n"
        val importText = "import { sharedAnswer } from './shared';\n\n"
        val action = action(
            base = base,
            result = importText + base,
            edits = listOf(EditorPluginTextEdit(0, 0, importText)),
        )

        assertTrue(
            EditorCurrentDocumentCodeActionAuthorizer.isAuthorized(
                action = action,
                currentText = base,
                readOnly = false,
            ),
        )
    }

    @Test
    fun rejectsReadOnlyStaleAndTamperedRequests() {
        val base = "const value = shraedAnswer;\n"
        val result = "const value = sharedAnswer;\n"
        val action = action(
            base = base,
            result = result,
            edits = listOf(EditorPluginTextEdit(14, 26, "sharedAnswer")),
            kind = EditorPluginCurrentDocumentCodeActionKind.SPELLING_CORRECTION,
        )

        assertFalse(EditorCurrentDocumentCodeActionAuthorizer.isAuthorized(action, base, true))
        assertFalse(
            EditorCurrentDocumentCodeActionAuthorizer.isAuthorized(
                action,
                "// changed\n$base",
                false,
            ),
        )
        assertFalse(
            EditorCurrentDocumentCodeActionAuthorizer.isAuthorized(
                action.copy(resultContentSha256 = hash("different")),
                base,
                false,
            ),
        )
        assertFalse(
            EditorCurrentDocumentCodeActionAuthorizer.isAuthorized(
                action.copy(edits = listOf(EditorPluginTextEdit(14, base.length + 1, "sharedAnswer"))),
                base,
                false,
            ),
        )
    }

    @Test
    fun rejectsEditsThatSplitUtf16SurrogatePairs() {
        val base = "const face = '😀';\n"
        val highSurrogateOffset = base.indexOf("😀")
        val action = action(
            base = base,
            result = base.removeRange(highSurrogateOffset, highSurrogateOffset + 1),
            edits = listOf(EditorPluginTextEdit(highSurrogateOffset, highSurrogateOffset + 1, "")),
        )

        assertFalse(EditorCurrentDocumentCodeActionAuthorizer.isAuthorized(action, base, false))
    }

    private fun action(
        base: String,
        result: String,
        edits: List<EditorPluginTextEdit>,
        kind: EditorPluginCurrentDocumentCodeActionKind =
            EditorPluginCurrentDocumentCodeActionKind.AUTO_IMPORT,
    ) = EditorPluginCurrentDocumentCodeAction(
        kind = kind,
        title = "Apply TypeScript code action",
        diagnosticCode = if (kind == EditorPluginCurrentDocumentCodeActionKind.AUTO_IMPORT) 2304 else 2552,
        baseContentSha256 = hash(base),
        resultContentSha256 = hash(result),
        edits = edits,
    )

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
