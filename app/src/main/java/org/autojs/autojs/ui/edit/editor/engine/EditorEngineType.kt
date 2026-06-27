package org.autojs.autojs.ui.edit.editor.engine

enum class EditorEngineType(val preferenceValue: String) {
    NATIVE("native"),
    ACE("ace");

    companion object {
        fun fromPreferenceValue(value: String?): EditorEngineType {
            return entries.firstOrNull { it.preferenceValue == value } ?: ACE
        }
    }
}
