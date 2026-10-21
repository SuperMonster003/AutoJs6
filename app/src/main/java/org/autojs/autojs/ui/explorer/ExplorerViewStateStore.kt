package org.autojs.autojs.ui.explorer

import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.set

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

/**
 * Typed persistence boundary for Explorer navigation and scroll state.
 * The keys are declared in [MiscStateKeys] (docs/settings/ROADMAP.md P4.2, names unchanged by R1).
 */
internal object ExplorerViewStateStore {

    fun savePageScroll(scrollPosition: Int) {
        MiscStateKeys.EXPLORER_VIEW_PAGE_STATE.set(scrollPosition)
    }

    fun restorePageScroll(): Int? {
        return MiscStateKeys.EXPLORER_VIEW_PAGE_STATE.get().takeIf { it > 0 }
    }

    fun saveExplorer(
        currentPath: String,
        rootPath: String,
        history: List<ExplorerStoredPageState>,
    ) {
        SettingsStores.default.edit {
            this[MiscStateKeys.EXPLORER_VIEW_CURRENT] = currentPath
            this[MiscStateKeys.EXPLORER_VIEW_ROOT] = rootPath
            this[MiscStateKeys.EXPLORER_VIEW_HISTORY] = history.map(::encodePageState)
        }
    }

    fun restoreExplorer(): ExplorerStoredViewState {
        return ExplorerStoredViewState(
            currentPath = MiscStateKeys.EXPLORER_VIEW_CURRENT.storedOrNull(),
            rootPath = MiscStateKeys.EXPLORER_VIEW_ROOT.storedOrNull(),
            history = MiscStateKeys.stringList(MiscStateKeys.EXPLORER_VIEW_HISTORY).mapNotNull(::decodePageState),
        )
    }

    fun clear() {
        SettingsStores.default.edit {
            remove(MiscStateKeys.EXPLORER_VIEW_PAGE_STATE.name)
            remove(MiscStateKeys.EXPLORER_VIEW_CURRENT.name)
            remove(MiscStateKeys.EXPLORER_VIEW_ROOT.name)
            remove(MiscStateKeys.EXPLORER_VIEW_HISTORY.name)
        }
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

    private const val PAGE_STATE_FIELD_COUNT = 4
}
