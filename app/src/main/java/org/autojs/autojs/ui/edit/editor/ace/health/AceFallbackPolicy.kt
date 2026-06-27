package org.autojs.autojs.ui.edit.editor.ace.health

class AceFallbackPolicy {

    fun shouldFallback(failure: AceFailure): Boolean {
        if (!failure.fatal) {
            return false
        }
        return when (failure.type) {
            AceFailureType.LSP_ERROR,
            AceFailureType.COMPLETION_ERROR,
                -> false
            else -> true
        }
    }
}
