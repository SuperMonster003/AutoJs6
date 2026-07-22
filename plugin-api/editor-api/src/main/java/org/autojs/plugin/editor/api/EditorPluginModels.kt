package org.autojs.plugin.editor.api

import android.os.Bundle

data class EditorPluginSessionConfig(
    val hostPackageName: String,
    val hostVersionName: String,
    val hostVersionCode: Long,
    val storageDirectoryPath: String,
    val documentPath: String? = null,
    val extras: Bundle? = null,
)

data class EditorPluginCursor(
    val line: Int,
    val column: Int,
    val lineText: String,
) {
    companion object {
        val EMPTY = EditorPluginCursor(line = 0, column = 0, lineText = "")
    }
}

data class EditorPluginSelection(
    val startOffset: Int,
    val endOffset: Int,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
) {
    val isCollapsed: Boolean
        get() = startOffset == endOffset

    companion object {
        val EMPTY = EditorPluginSelection(
            startOffset = 0,
            endOffset = 0,
            startLine = 0,
            startColumn = 0,
            endLine = 0,
            endColumn = 0,
        )
    }
}

data class EditorPluginState(
    val text: String,
    val dirty: Boolean,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val lineCount: Int,
    val cursor: EditorPluginCursor,
    val selection: EditorPluginSelection,
    val selectedText: String,
    val completionPopupOpen: Boolean = false,
    val breakpoints: List<Int>,
    val firstVisibleLine: Int,
    val firstVisibleColumn: Int,
    val textIncluded: Boolean = true,
)

data class EditorPluginBreakpoint(
    val line: Int,
    val enabled: Boolean = true,
)

data class EditorPluginSearchOptions(
    val usingRegex: Boolean = false,
    val caseSensitive: Boolean = false,
    val wholeWord: Boolean = false,
)

data class EditorPluginSearchResult(
    val found: Boolean,
    val foundIndex: Int = -1,
    val query: String? = null,
)

class EditorPluginSearchSyntaxException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

data class EditorPluginTheme(
    val id: String,
    val aceTheme: String? = null,
    val isDark: Boolean,
    val backgroundColor: Int,
    val foregroundColor: Int? = null,
    val extras: Bundle? = null,
)

data class EditorPluginSnapshot(
    val text: String,
    val dirty: Boolean,
    val cursorLine: Int,
    val cursorColumn: Int,
    val selection: EditorPluginSelection?,
    val firstVisibleLine: Int?,
    val firstVisibleColumn: Int?,
    val breakpoints: List<EditorPluginBreakpoint>,
    val debuggingLine: Int,
    val readOnly: Boolean,
    val fontSizeSp: Float,
    val themeId: String?,
    val searchQuery: String?,
    val searchOptions: EditorPluginSearchOptions?,
)

enum class EditorPluginHealthState {
    CREATED,
    LOADING_PAGE,
    PAGE_LOADED,
    BRIDGE_READY,
    RUNNING,
    DEGRADED,
    FALLING_BACK,
    FAILED,
    DESTROYED,
}

enum class EditorPluginFailureType {
    ASSET_MISSING,
    PAGE_LOAD_ERROR,
    PAGE_LOAD_TIMEOUT,
    READY_TIMEOUT,
    BRIDGE_NOT_READY,
    JS_INIT_ERROR,
    JS_RUNTIME_ERROR,
    CONSOLE_FATAL,
    EVALUATE_JAVASCRIPT_ERROR,
    RENDER_PROCESS_GONE,
    HEARTBEAT_TIMEOUT,
    SECURITY_BLOCKED_URL,
    LSP_ERROR,
    COMPLETION_ERROR,
    UNKNOWN,
}

data class EditorPluginFailure(
    val type: EditorPluginFailureType,
    val message: String,
    val detail: String? = null,
    val state: EditorPluginHealthState? = null,
    val fatal: Boolean = true,
    val timestampUptimeMillis: Long = 0L,
    val extras: Bundle? = null,
)

/** Values must use Android platform Bundle types; never place plugin-defined classes here. */
data class EditorPluginDiagnostics(
    val values: Bundle,
)

enum class EditorPluginHistoryDirection {
    UNDO,
    REDO,
}

enum class EditorPluginSelectionAction {
    COPY,
    PASTE,
    SELECT_ALL,
    DELETE_LINE,
    COPY_LINE,
}
