package org.autojs.autojs.ui.edit.editor.service

import org.autojs.autojs.core.plugin.typescript.TypeScriptEditorDefinitionTargetResolver
import org.autojs.plugin.editor.api.EditorPluginDefinitionTargetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class EditorDefinitionNavigationDispatcherTest {

    @Test
    fun jumpsWithinTheCurrentDocumentAtTheValidatedPosition() {
        var jumped: Pair<Int, Int>? = null
        val outcome = EditorDefinitionNavigationDispatcher.dispatch(
            target = target(documentPath = "/project/main.ts", targetPath = "/project/main.ts"),
            jumpCurrentDocument = { line, column -> jumped = line to column },
            openTargetDocument = { _, _, _, _ ->
                fail("Current document must not be reopened")
                false
            },
        )

        assertEquals(EditorDefinitionNavigationDispatcher.Outcome.JUMPED_CURRENT_DOCUMENT, outcome)
        assertEquals(7 to 11, jumped)
    }

    @Test
    fun opensAnotherValidatedDocumentWithItsExactCursorPosition() {
        var opened: List<Any>? = null
        val outcome = EditorDefinitionNavigationDispatcher.dispatch(
            target = target(
                documentPath = "/project/main.ts",
                targetPath = "/project/src/shared.ts",
            ),
            jumpCurrentDocument = { _, _ -> fail("Another document must be opened") },
            openTargetDocument = { name, path, line, column ->
                opened = listOf(name, path, line, column)
                true
            },
        )

        assertEquals(EditorDefinitionNavigationDispatcher.Outcome.OPENED_TARGET_DOCUMENT, outcome)
        assertEquals(listOf("shared.ts", "/project/src/shared.ts", 7, 11), opened)
    }

    @Test
    fun reportsAnOpenFailureWithoutFallingBackToAnUnvalidatedJump() {
        val outcome = EditorDefinitionNavigationDispatcher.dispatch(
            target = target(
                documentPath = "/project/main.ts",
                targetPath = "/project/node_modules/pkg/index.d.ts",
            ),
            jumpCurrentDocument = { _, _ -> fail("Dependency target is another document") },
            openTargetDocument = { _, _, _, _ -> false },
        )

        assertEquals(EditorDefinitionNavigationDispatcher.Outcome.FAILED_TO_OPEN_TARGET, outcome)
    }

    private fun target(
        documentPath: String,
        targetPath: String,
    ) = TypeScriptEditorDefinitionTargetResolver.ResolvedTarget(
        documentPath = documentPath,
        targetPath = targetPath,
        line = 7,
        column = 11,
        endLine = 7,
        endColumn = 17,
        kind = EditorPluginDefinitionTargetKind.PROJECT_SOURCE,
    )
}
