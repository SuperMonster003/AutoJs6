package org.autojs.autojs.ui.edit.editor.service

import java.io.File
import org.autojs.autojs.core.plugin.typescript.TypeScriptEditorDefinitionTargetResolver

internal object EditorDefinitionNavigationDispatcher {

    enum class Outcome {
        JUMPED_CURRENT_DOCUMENT,
        OPENED_TARGET_DOCUMENT,
        FAILED_TO_OPEN_TARGET,
    }

    fun dispatch(
        target: TypeScriptEditorDefinitionTargetResolver.ResolvedTarget,
        jumpCurrentDocument: (line: Int, column: Int) -> Unit,
        openTargetDocument: (
            name: String,
            path: String,
            line: Int,
            column: Int,
        ) -> Boolean,
    ): Outcome {
        if (target.isCurrentDocument) {
            jumpCurrentDocument(target.line, target.column)
            return Outcome.JUMPED_CURRENT_DOCUMENT
        }
        val opened = openTargetDocument(
            File(target.targetPath).name,
            target.targetPath,
            target.line,
            target.column,
        )
        return if (opened) Outcome.OPENED_TARGET_DOCUMENT else Outcome.FAILED_TO_OPEN_TARGET
    }
}
