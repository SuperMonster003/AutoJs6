package org.autojs.autojs.ui.edit.editor.ace

import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.keys.EditorKeys
import org.autojs.autojs.core.pref.keys.MiscStateKeys

object AceEditorLspPreferences {
    const val KEY_ACE_LSP_ENABLED = "key_\$_ace_lsp_enabled"
    const val KEY_ACE_LSP_FILE_TYPES = "key_\$_ace_lsp_file_types"
    const val KEY_ACE_LSP_FILE_TYPES_REVISION = "key_\$_ace_lsp_file_types_revision"
    const val KEY_ACE_LSP_DECLARATION_GROUPS = "key_\$_ace_lsp_declaration_groups"
    const val KEY_ACE_SEMANTIC_TYPESCRIPT_ENABLED = "key_\$_ace_semantic_typescript_enabled"
    const val KEY_ACE_SEMANTIC_PYTHON_ENABLED = "key_\$_ace_semantic_python_enabled"
    const val KEY_ACE_SEMANTIC_LUA_ENABLED = "key_\$_ace_semantic_lua_enabled"
    const val KEY_ACE_SEMANTIC_JAVA_ENABLED = "key_\$_ace_semantic_java_enabled"
    const val KEY_ACE_SEMANTIC_KOTLIN_ENABLED = "key_\$_ace_semantic_kotlin_enabled"
    const val DEFAULT_ENABLED = true
    const val SEMANTIC_LANGUAGE_TYPESCRIPT = "typescript"
    const val SEMANTIC_LANGUAGE_PYTHON = "python"
    const val SEMANTIC_LANGUAGE_LUA = "lua"
    const val SEMANTIC_LANGUAGE_JAVA = "java"
    const val SEMANTIC_LANGUAGE_KOTLIN = "kotlin"
    const val DECLARATION_GROUP_ANDROID = "android"
    const val DECLARATION_GROUP_LIBRARIES = "libraries"
    const val DECLARATION_GROUP_RESOURCES = "resources"
    const val DECLARATION_GROUP_MAIN_APP = "main-app"
    private val TYPE_SCRIPT_DECLARATION_EXTENSIONS = listOf(".d.ts", ".d.mts", ".d.cts")

    enum class SyntaxSupport {
        FULL,
        PARTIAL,
    }

    enum class LocalCompletionSupport {
        SUPPORTED,
        P2_PLUS,
        NONE,
    }

    enum class SemanticSupport {
        FULL,
        SYNTAX_DIAGNOSTICS_ONLY,
        SINGLE_FILE_DIAGNOSTICS,
        NONE,
    }

    data class LanguageSupport(
        val id: String,
        val fileTypes: List<String>,
        val syntax: SyntaxSupport,
        val keywordCompletion: Boolean,
        val snippets: Boolean,
        val localCompletion: LocalCompletionSupport,
        val semantic: SemanticSupport,
        val semanticLanguage: String?,
    )

    val DEFAULT_FILE_TYPES = listOf(
        ".js",
        ".mjs",
        ".cjs",
        ".jsx",
        ".ts",
        ".tsx",
        ".mts",
        ".cts",
        ".d.ts",
        ".d.mts",
        ".d.cts",
        ".json",
        ".auto.js",
        ".node.js",
        ".py",
        ".lua",
        ".java",
        ".kt",
        ".kts",
    )

    private val REVISION_1_DEFAULT_FILE_TYPES = listOf(
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

    private val REVISION_2_DEFAULT_FILE_TYPES = listOf(
        ".js",
        ".mjs",
        ".cjs",
        ".jsx",
        ".ts",
        ".tsx",
        ".mts",
        ".cts",
        ".d.ts",
        ".d.mts",
        ".d.cts",
        ".json",
        ".auto.js",
        ".node.js",
    )

    val SUPPORTED_SEMANTIC_LANGUAGES = listOf(
        SEMANTIC_LANGUAGE_TYPESCRIPT,
        SEMANTIC_LANGUAGE_PYTHON,
        SEMANTIC_LANGUAGE_LUA,
        SEMANTIC_LANGUAGE_JAVA,
        SEMANTIC_LANGUAGE_KOTLIN,
    )

    val AVAILABLE_SEMANTIC_LANGUAGES = listOf(
        SEMANTIC_LANGUAGE_TYPESCRIPT,
        SEMANTIC_LANGUAGE_PYTHON,
        SEMANTIC_LANGUAGE_LUA,
        SEMANTIC_LANGUAGE_JAVA,
    )

    val DEFAULT_SEMANTIC_LANGUAGES = linkedMapOf(
        SEMANTIC_LANGUAGE_TYPESCRIPT to true,
        SEMANTIC_LANGUAGE_PYTHON to true,
        SEMANTIC_LANGUAGE_LUA to true,
        SEMANTIC_LANGUAGE_JAVA to true,
        SEMANTIC_LANGUAGE_KOTLIN to false,
    )

    val LANGUAGE_SUPPORT_MATRIX = listOf(
        LanguageSupport(
            id = "javascript",
            fileTypes = listOf(".js", ".mjs", ".cjs", ".auto.js", ".node.js"),
            syntax = SyntaxSupport.FULL,
            keywordCompletion = true,
            snippets = true,
            localCompletion = LocalCompletionSupport.SUPPORTED,
            semantic = SemanticSupport.FULL,
            semanticLanguage = SEMANTIC_LANGUAGE_TYPESCRIPT,
        ),
        LanguageSupport(
            id = "jsx",
            fileTypes = listOf(".jsx"),
            syntax = SyntaxSupport.FULL,
            keywordCompletion = false,
            snippets = false,
            localCompletion = LocalCompletionSupport.SUPPORTED,
            semantic = SemanticSupport.FULL,
            semanticLanguage = SEMANTIC_LANGUAGE_TYPESCRIPT,
        ),
        LanguageSupport(
            id = "typescript",
            fileTypes = listOf(".ts", ".mts", ".cts", ".d.ts", ".d.mts", ".d.cts"),
            syntax = SyntaxSupport.FULL,
            keywordCompletion = true,
            snippets = true,
            localCompletion = LocalCompletionSupport.SUPPORTED,
            semantic = SemanticSupport.FULL,
            semanticLanguage = SEMANTIC_LANGUAGE_TYPESCRIPT,
        ),
        LanguageSupport(
            id = "tsx",
            fileTypes = listOf(".tsx"),
            // Ace Editor plugin 2.0.0 (Ace 1.44.0) highlights TSX with Ace's official tsx mode.
            // zh-CN: Ace Editor 插件 2.0.0 (Ace 1.44.0) 使用 Ace 官方的 tsx mode 高亮 TSX.
            syntax = SyntaxSupport.FULL,
            keywordCompletion = true,
            snippets = true,
            localCompletion = LocalCompletionSupport.SUPPORTED,
            semantic = SemanticSupport.FULL,
            semanticLanguage = SEMANTIC_LANGUAGE_TYPESCRIPT,
        ),
        LanguageSupport(
            id = "json",
            fileTypes = listOf(".json"),
            syntax = SyntaxSupport.FULL,
            keywordCompletion = false,
            snippets = false,
            localCompletion = LocalCompletionSupport.NONE,
            semantic = SemanticSupport.SYNTAX_DIAGNOSTICS_ONLY,
            semanticLanguage = null,
        ),
        LanguageSupport(
            id = SEMANTIC_LANGUAGE_PYTHON,
            fileTypes = listOf(".py"),
            syntax = SyntaxSupport.FULL,
            keywordCompletion = true,
            snippets = true,
            localCompletion = LocalCompletionSupport.SUPPORTED,
            semantic = SemanticSupport.FULL,
            semanticLanguage = SEMANTIC_LANGUAGE_PYTHON,
        ),
        LanguageSupport(
            id = SEMANTIC_LANGUAGE_LUA,
            fileTypes = listOf(".lua"),
            syntax = SyntaxSupport.FULL,
            keywordCompletion = true,
            snippets = true,
            localCompletion = LocalCompletionSupport.SUPPORTED,
            semantic = SemanticSupport.FULL,
            semanticLanguage = SEMANTIC_LANGUAGE_LUA,
        ),
        LanguageSupport(
            id = SEMANTIC_LANGUAGE_JAVA,
            fileTypes = listOf(".java"),
            syntax = SyntaxSupport.FULL,
            keywordCompletion = true,
            snippets = true,
            localCompletion = LocalCompletionSupport.SUPPORTED,
            semantic = SemanticSupport.SINGLE_FILE_DIAGNOSTICS,
            semanticLanguage = SEMANTIC_LANGUAGE_JAVA,
        ),
        LanguageSupport(
            id = SEMANTIC_LANGUAGE_KOTLIN,
            fileTypes = listOf(".kt", ".kts"),
            syntax = SyntaxSupport.FULL,
            keywordCompletion = true,
            snippets = true,
            localCompletion = LocalCompletionSupport.P2_PLUS,
            semantic = SemanticSupport.NONE,
            semanticLanguage = SEMANTIC_LANGUAGE_KOTLIN,
        ),
    )

    val SUPPORTED_DECLARATION_GROUPS = listOf(
        DECLARATION_GROUP_ANDROID,
        DECLARATION_GROUP_LIBRARIES,
        DECLARATION_GROUP_RESOURCES,
        DECLARATION_GROUP_MAIN_APP,
    )

    val DEFAULT_DECLARATION_GROUPS = emptyList<String>()

    @JvmStatic
    fun isEnabled(): Boolean {
        return EditorKeys.ACE_LSP_ENABLED.get()
    }

    @JvmStatic
    fun setEnabled(enabled: Boolean) {
        EditorKeys.ACE_LSP_ENABLED.set(enabled)
    }

    @JvmStatic
    fun resetEnabled() {
        EditorKeys.ACE_LSP_ENABLED.remove()
    }

    @JvmStatic
    fun getFileTypes(): List<String> {
        if (!EditorKeys.ACE_LSP_FILE_TYPES.isStored()) {
            return DEFAULT_FILE_TYPES
        }
        val revision = MiscStateKeys.ACE_LSP_FILE_TYPES_REVISION.get()
        return resolveStoredFileTypes(EditorKeys.ACE_LSP_FILE_TYPES.storedOrNull() ?: "", revision)
    }

    internal fun resolveStoredFileTypes(raw: String?, revision: Int): List<String> {
        val fileTypes = normalizeFileTypes(raw)
        val matchesHistoricalDefault = when (revision) {
            1 -> fileTypes.toSet() == REVISION_1_DEFAULT_FILE_TYPES.toSet()
            2 -> fileTypes.toSet() == REVISION_2_DEFAULT_FILE_TYPES.toSet()
            else -> false
        }
        return if (revision < FILE_TYPES_REVISION && matchesHistoricalDefault) {
            DEFAULT_FILE_TYPES
        } else {
            fileTypes
        }
    }

    @JvmStatic
    fun setFileTypes(fileTypes: Collection<String>) {
        EditorKeys.ACE_LSP_FILE_TYPES.set(normalizeFileTypes(fileTypes).joinToString(","))
        MiscStateKeys.ACE_LSP_FILE_TYPES_REVISION.set(FILE_TYPES_REVISION)
    }

    @JvmStatic
    fun resetFileTypes() {
        EditorKeys.ACE_LSP_FILE_TYPES.remove()
        MiscStateKeys.ACE_LSP_FILE_TYPES_REVISION.remove()
    }

    @JvmStatic
    fun getDeclarationGroups(): List<String> {
        if (!EditorKeys.ACE_LSP_DECLARATION_GROUPS.isStored()) {
            return DEFAULT_DECLARATION_GROUPS
        }
        return normalizeDeclarationGroups(EditorKeys.ACE_LSP_DECLARATION_GROUPS.storedOrNull() ?: "")
    }

    @JvmStatic
    fun setDeclarationGroups(groups: Collection<String>) {
        EditorKeys.ACE_LSP_DECLARATION_GROUPS.set(normalizeDeclarationGroups(groups).joinToString(","))
    }

    @JvmStatic
    fun resetDeclarationGroups() {
        EditorKeys.ACE_LSP_DECLARATION_GROUPS.remove()
    }

    @JvmStatic
    fun isSemanticEnabled(language: String): Boolean {
        if (!isEnabled()) return false
        val normalized = normalizeSemanticLanguage(language) ?: return false
        if (!isSemanticAvailable(normalized)) return false
        return semanticKey(normalized).get()
    }

    @JvmStatic
    fun setSemanticEnabled(language: String, enabled: Boolean) {
        val normalized = requireNotNull(normalizeSemanticLanguage(language)) {
            "Unsupported semantic language: $language"
        }
        require(!enabled || isSemanticAvailable(normalized)) {
            "Semantic provider is unavailable for language: $language"
        }
        semanticKey(normalized).set(enabled)
    }

    @JvmStatic
    fun resetSemanticEnabled(language: String) {
        val normalized = requireNotNull(normalizeSemanticLanguage(language)) {
            "Unsupported semantic language: $language"
        }
        semanticKey(normalized).remove()
    }

    @JvmStatic
    fun getSemanticLanguages(): Map<String, Boolean> {
        return SUPPORTED_SEMANTIC_LANGUAGES.associateWith(::isSemanticEnabled)
    }

    fun defaultSemanticEnabled(language: String): Boolean {
        val normalized = normalizeSemanticLanguage(language) ?: return false
        return isSemanticAvailable(normalized) && DEFAULT_SEMANTIC_LANGUAGES.getValue(normalized)
    }

    fun isSemanticAvailable(language: String): Boolean {
        val normalized = normalizeSemanticLanguage(language) ?: return false
        return normalized in AVAILABLE_SEMANTIC_LANGUAGES
    }

    fun semanticLanguageForDocument(documentPathOrName: String?): String? {
        val fileName = documentPathOrName
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?.replace('\\', '/')
            ?.substringAfterLast('/')
            ?.lowercase()
            ?.takeIf(String::isNotBlank)
            ?: return null
        return when {
            listOf(
                ".d.ts", ".d.mts", ".d.cts", ".auto.js", ".node.js",
                ".js", ".mjs", ".cjs", ".jsx", ".ts", ".tsx", ".mts", ".cts",
            ).any(fileName::endsWith) -> SEMANTIC_LANGUAGE_TYPESCRIPT
            fileName.endsWith(".py") -> SEMANTIC_LANGUAGE_PYTHON
            fileName.endsWith(".lua") -> SEMANTIC_LANGUAGE_LUA
            fileName.endsWith(".java") -> SEMANTIC_LANGUAGE_JAVA
            fileName.endsWith(".kt") || fileName.endsWith(".kts") -> SEMANTIC_LANGUAGE_KOTLIN
            else -> null
        }
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

    @JvmStatic
    fun isSemanticEnabledForDocument(documentPathOrName: String?): Boolean {
        if (!isEnabledForDocument(documentPathOrName)) return false
        val language = semanticLanguageForDocument(documentPathOrName) ?: return false
        return isSemanticEnabled(language)
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

    fun normalizeDeclarationGroups(raw: String?): List<String> {
        return normalizeDeclarationGroups(
            raw.orEmpty()
                .split(',', ';', '\n', '\r', '\t', ' ')
                .filter { it.isNotBlank() },
        )
    }

    fun normalizeDeclarationGroups(values: Collection<String>): List<String> {
        val selected = values
            .asSequence()
            .map { it.trim().lowercase() }
            .filter { it in SUPPORTED_DECLARATION_GROUPS }
            .toSet()
        return SUPPORTED_DECLARATION_GROUPS.filter { it in selected }
    }

    fun resolveDeclarationGroups(values: Collection<String>): List<String> {
        val effective = normalizeDeclarationGroups(values).toMutableSet()
        if (DECLARATION_GROUP_MAIN_APP in effective) {
            effective += DECLARATION_GROUP_ANDROID
            effective += DECLARATION_GROUP_LIBRARIES
            effective += DECLARATION_GROUP_RESOURCES
        }
        if (DECLARATION_GROUP_LIBRARIES in effective) {
            effective += DECLARATION_GROUP_ANDROID
        }
        return SUPPORTED_DECLARATION_GROUPS.filter { it in effective }
    }

    private fun normalizeSemanticLanguage(language: String): String? {
        return language.trim().lowercase().takeIf { it in SUPPORTED_SEMANTIC_LANGUAGES }
    }

    private fun semanticKey(language: String): SettingKey.Bool = when (language) {
        SEMANTIC_LANGUAGE_TYPESCRIPT -> EditorKeys.ACE_SEMANTIC_TYPESCRIPT_ENABLED
        SEMANTIC_LANGUAGE_PYTHON -> EditorKeys.ACE_SEMANTIC_PYTHON_ENABLED
        SEMANTIC_LANGUAGE_LUA -> EditorKeys.ACE_SEMANTIC_LUA_ENABLED
        SEMANTIC_LANGUAGE_JAVA -> EditorKeys.ACE_SEMANTIC_JAVA_ENABLED
        SEMANTIC_LANGUAGE_KOTLIN -> EditorKeys.ACE_SEMANTIC_KOTLIN_ENABLED
        else -> error("Unsupported semantic language: $language")
    }

    internal const val FILE_TYPES_REVISION = 3
}
