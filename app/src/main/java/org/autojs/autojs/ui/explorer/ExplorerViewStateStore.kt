package org.autojs.autojs.ui.explorer

import org.autojs.autojs.core.pref.Pref.getInt
import org.autojs.autojs.core.pref.Pref.getLinkedList
import org.autojs.autojs.core.pref.Pref.getStringOrNull
import org.autojs.autojs.core.pref.Pref.putInt
import org.autojs.autojs.core.pref.Pref.putLinkedList
import org.autojs.autojs.core.pref.Pref.putString
import org.autojs.autojs.core.pref.Pref.remove
import java.util.LinkedList

internal data class ExplorerStoredPageState(
    val path: String,
    val scrollPosition: Int,
    val directoriesCollapsed: Boolean,
    val filesCollapsed: Boolean,
)

internal data class ExplorerStoredViewState(
    val currentPath: String?,
    val rootPath: String?,
    val history: List<ExplorerStoredPageState>,
)

/** Typed persistence boundary for Explorer navigation and scroll state. */
internal object ExplorerViewStateStore {

    fun savePageScroll(scrollPosition: Int) {
        putInt(prefKey(PAGE_STATE), scrollPosition)
    }

    fun restorePageScroll(): Int? {
        return getInt(prefKey(PAGE_STATE), -1).takeIf { it > 0 }
    }

    fun saveExplorer(
        currentPath: String,
        rootPath: String,
        history: List<ExplorerStoredPageState>,
    ) {
        putString(prefKey(EXPLORER_CURRENT), currentPath)
        putString(prefKey(EXPLORER_ROOT), rootPath)
        putLinkedList(
            prefKey(EXPLORER_HISTORY),
            LinkedList(history.map(::encodePageState)),
        )
    }

    fun restoreExplorer(): ExplorerStoredViewState {
        return ExplorerStoredViewState(
            currentPath = getStringOrNull(prefKey(EXPLORER_CURRENT)),
            rootPath = getStringOrNull(prefKey(EXPLORER_ROOT)),
            history = getLinkedList(prefKey(EXPLORER_HISTORY)).mapNotNull(::decodePageState),
        )
    }

    fun clear() {
        remove(prefKey(PAGE_STATE))
        remove(prefKey(EXPLORER_CURRENT))
        remove(prefKey(EXPLORER_ROOT))
        remove(prefKey(EXPLORER_HISTORY))
    }

    fun prefKey(key: String): String = ExplorerView::class.java.simpleName + '.' + key

    internal fun encodePageState(state: ExplorerStoredPageState): String {
        return listOf(
            state.path,
            state.scrollPosition,
            state.directoriesCollapsed,
            state.filesCollapsed,
        ).joinToString(",")
    }

    internal fun decodePageState(value: String): ExplorerStoredPageState? {
        val fields = value.split(',')
        if (fields.size < PAGE_STATE_FIELD_COUNT) return null
        val path = fields.dropLast(PAGE_STATE_FIELD_COUNT - 1).joinToString(",")
        val scrollPosition = fields[fields.lastIndex - 2].toIntOrNull() ?: return null
        val directoriesCollapsed = fields[fields.lastIndex - 1].toBooleanStrictOrNull() ?: return null
        val filesCollapsed = fields.last().toBooleanStrictOrNull() ?: return null
        if (path.isEmpty()) return null
        return ExplorerStoredPageState(path, scrollPosition, directoriesCollapsed, filesCollapsed)
    }

    private const val PAGE_STATE = "page_state"
    private const val EXPLORER_CURRENT = "explorer_current"
    private const val EXPLORER_ROOT = "explorer_root"
    private const val EXPLORER_HISTORY = "explorer_history"
    private const val PAGE_STATE_FIELD_COUNT = 4
}
