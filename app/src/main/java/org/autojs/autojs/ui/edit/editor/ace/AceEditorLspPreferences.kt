package org.autojs.autojs.ui.edit.editor.ace

import org.autojs.autojs.core.pref.Pref

object AceEditorLspPreferences {
    const val KEY_ACE_LSP_ENABLED = "key_\$_ace_lsp_enabled"
    const val KEY_ACE_LSP_FILE_TYPES = "key_\$_ace_lsp_file_types"
    private val TYPE_SCRIPT_DECLARATION_EXTENSIONS = listOf(".d.ts", ".d.mts", ".d.cts")

    val DEFAULT_FILE_TYPES = listOf(
        ".js",
        ".mjs",
        ".cjs",
        ".jsx",
        ".ts",
        ".tsx",
        ".d.ts",
        ".json",
        ".auto.js",
        ".node.js",
    )

    @JvmStatic
    fun isEnabled(): Boolean {
        return Pref.getBoolean(KEY_ACE_LSP_ENABLED, false)
    }

    @JvmStatic
    fun setEnabled(enabled: Boolean) {
        Pref.putBoolean(KEY_ACE_LSP_ENABLED, enabled)
    }

    @JvmStatic
    fun getFileTypes(): List<String> {
        if (!Pref.containsKey(KEY_ACE_LSP_FILE_TYPES)) {
            return DEFAULT_FILE_TYPES
        }
        return normalizeFileTypes(Pref.getString(KEY_ACE_LSP_FILE_TYPES, ""))
    }

    @JvmStatic
    fun setFileTypes(fileTypes: Collection<String>) {
        Pref.putString(KEY_ACE_LSP_FILE_TYPES, normalizeFileTypes(fileTypes).joinToString(","))
    }

    @JvmStatic
    fun resetFileTypes() {
        Pref.remove(KEY_ACE_LSP_FILE_TYPES)
    }

    @JvmStatic
    fun isEnabledForDocument(documentPathOrName: String?): Boolean {
        if (!isEnabled()) {
            return false
        }
        return isDocumentAllowed(documentPathOrName)
    }

    @JvmStatic
    fun isDocumentAllowed(documentPathOrName: String?): Boolean {
        val normalized = documentPathOrName
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?.replace('\\', '/')
            ?.substringAfterLast('/')
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: return false
        return matchesFileType(normalized, getFileTypes())
    }

    fun matchesFileType(fileName: String, fileTypes: Collection<String>): Boolean {
        val normalizedFileName = fileName.trim().replace('\\', '/').substringAfterLast('/').lowercase()
        val normalizedFileTypes = normalizeFileTypes(fileTypes)
        val declarationExtension = TYPE_SCRIPT_DECLARATION_EXTENSIONS.firstOrNull { normalizedFileName.endsWith(it) }
        if (declarationExtension != null) {
            return declarationExtension in normalizedFileTypes
        }
        return normalizedFileTypes.any { normalizedFileName.endsWith(it) }
    }

    fun normalizeFileTypes(raw: String?): List<String> {
        return normalizeFileTypes(
            raw.orEmpty()
                .split(',', ';', '\n', '\r', '\t', ' ')
                .filter { it.isNotBlank() },
        )
    }

    fun normalizeFileTypes(values: Collection<String>): List<String> {
        return values
            .asSequence()
            .map { it.trim().lowercase().removePrefix("*") }
            .filter { it.isNotBlank() }
            .map { if (it.startsWith(".")) it else ".$it" }
            .distinct()
            .toList()
    }
}
