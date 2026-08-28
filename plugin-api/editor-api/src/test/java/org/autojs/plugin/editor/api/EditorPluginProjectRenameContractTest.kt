package org.autojs.plugin.editor.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPluginProjectRenameContractTest {

    @Test
    fun acceptsCanonicalBoundedThreeFileRename() {
        assertTrue(EditorPluginContract.MAX_SUPPORTED_VERSION >= 5)
        assertTrue(EditorPluginProjectRenameContract.isSupported(request()))
    }

    @Test
    fun rejectsPathAndInventoryAuthorityEscapes() {
        val request = request()
        assertFalse(
            EditorPluginProjectRenameContract.isSupported(
                request.copy(projectSourceInventoryFingerprint = hash('G')),
            ),
        )
        assertFalse(
            EditorPluginProjectRenameContract.isSupported(
                request.copy(documentRelativePath = "../src/main.ts"),
            ),
        )
        assertFalse(
            EditorPluginProjectRenameContract.isSupported(
                request.copy(files = request.files.reversed()),
            ),
        )
        assertFalse(
            EditorPluginProjectRenameContract.isSupported(
                request.copy(
                    files = request.files + request.files.last().copy(
                        relativePath = "SRC/SHARED.TS",
                    ),
                ),
            ),
        )
    }

    @Test
    fun rejectsNonRenameEditsAndMissingActiveDocument() {
        val request = request()
        val first = request.files.first()
        assertFalse(
            EditorPluginProjectRenameContract.isSupported(
                request.copy(
                    files = listOf(
                        first.copy(
                            edits = listOf(EditorPluginTextEdit(16, 22, "injected();")),
                        ),
                    ) + request.files.drop(1),
                ),
            ),
        )
        assertFalse(
            EditorPluginProjectRenameContract.isSupported(
                request.copy(
                    files = request.files.map { file ->
                        file.copy(edits = file.edits.map { it.copy(endOffset = it.endOffset - 1) })
                    },
                ),
            ),
        )
        assertFalse(
            EditorPluginProjectRenameContract.isSupported(
                request.copy(documentRelativePath = "src/missing.ts"),
            ),
        )
    }

    @Test
    fun rejectsInvalidOrUnchangedIdentifier() {
        val request = request()
        assertFalse(EditorPluginProjectRenameContract.isSupported(request.copy(newName = "")))
        assertFalse(EditorPluginProjectRenameContract.isSupported(request.copy(newName = "bad-name")))
        assertFalse(
            EditorPluginProjectRenameContract.isSupported(
                request.copy(newName = request.symbolName),
            ),
        )
    }

    private fun request(): EditorPluginProjectRenameRequest =
        EditorPluginProjectRenameRequest(
            projectRootPath = "/project",
            projectSourceInventoryFingerprint = hash('a'),
            documentRelativePath = "src/main.ts",
            symbolName = "answer",
            newName = "projectAnswer",
            files = listOf(
                file("src/consumer.ts", 16, 'b', 'c'),
                file("src/main.ts", 9, 'd', 'e'),
                file("src/shared.ts", 16, 'f', '0'),
            ),
        )

    private fun file(
        relativePath: String,
        startOffset: Int,
        baseHash: Char,
        resultHash: Char,
    ) = EditorPluginProjectRenameFile(
        relativePath = relativePath,
        baseContentSha256 = hash(baseHash),
        resultContentSha256 = hash(resultHash),
        edits = listOf(
            EditorPluginTextEdit(startOffset, startOffset + "answer".length, "projectAnswer"),
        ),
    )

    private fun hash(character: Char): String = character.toString().repeat(64)
}
