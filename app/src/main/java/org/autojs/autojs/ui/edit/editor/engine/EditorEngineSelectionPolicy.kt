package org.autojs.autojs.ui.edit.editor.engine

internal object EditorEngineSelectionPolicy {

    enum class Mode(val preferenceValue: String) {
        AUTO_ACE("auto_ace"),
        NATIVE_EXPLICIT("native_explicit");

        companion object {
            fun fromPreferenceValue(value: String?): Mode? =
                entries.firstOrNull { it.preferenceValue == value }
        }
    }

    data class Decision(
        val mode: Mode,
        val effectiveType: EditorEngineType,
        val needsAcePlugin: Boolean = false,
    )

    fun migrateMode(rawEngineValue: String?): Mode = when (rawEngineValue) {
        null, EditorEngineType.ACE.preferenceValue -> Mode.AUTO_ACE
        EditorEngineType.NATIVE.preferenceValue -> Mode.NATIVE_EXPLICIT
        else -> Mode.NATIVE_EXPLICIT
    }

    fun effectiveType(mode: Mode, aceAvailable: Boolean): EditorEngineType =
        if (mode == Mode.AUTO_ACE && aceAvailable) EditorEngineType.ACE else EditorEngineType.NATIVE

    fun reconcile(mode: Mode, aceAvailable: Boolean): Decision = Decision(
        mode = mode,
        effectiveType = effectiveType(mode, aceAvailable),
    )

    fun selectByUser(
        currentMode: Mode,
        selectedType: EditorEngineType,
        aceAvailable: Boolean,
    ): Decision = when (selectedType) {
        EditorEngineType.ACE -> if (aceAvailable) {
            Decision(mode = Mode.AUTO_ACE, effectiveType = EditorEngineType.ACE)
        } else {
            Decision(
                mode = currentMode,
                effectiveType = EditorEngineType.NATIVE,
                needsAcePlugin = true,
            )
        }
        EditorEngineType.NATIVE -> {
            val mode = if (aceAvailable) Mode.NATIVE_EXPLICIT else currentMode
            Decision(mode = mode, effectiveType = EditorEngineType.NATIVE)
        }
    }
}
