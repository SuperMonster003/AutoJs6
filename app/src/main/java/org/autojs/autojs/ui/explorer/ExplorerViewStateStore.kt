package org.autojs.autojs.ui.explorer

import org.autojs.autojs.core.pref.SettingsJson
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
            history = MiscStateKeys.EXPLORER_VIEW_HISTORY.get().mapNotNull(::decodePageState),
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

    /**
     * A page as a JSON object (docs/settings/ROADMAP.md appendix C Q40); [decodePageState] also reads the comma
     * separated text ("path,scroll,directories collapsed,files collapsed") that older versions wrote.
     * zh-CN: 以 JSON 对象表示的页面 (附录 C Q40); [decodePageState] 也读取旧版本写入的逗号拼接文本.
     */
    internal fun encodePageState(state: ExplorerStoredPageState): Map<String, Any?> = linkedMapOf(
        "path" to state.path,
        "scrollPosition" to state.scrollPosition,
        "directoriesCollapsed" to state.directoriesCollapsed,
        "filesCollapsed" to state.filesCollapsed,
    )

    internal fun decodePageState(value: Any?): ExplorerStoredPageState? = when (value) {
        is Map<*, *> -> {
            val path = value["path"] as? String
            val scrollPosition = SettingsJson.toInt(value["scrollPosition"])
            val directoriesCollapsed = value["directoriesCollapsed"] as? Boolean
            val filesCollapsed = value["filesCollapsed"] as? Boolean
            if (path.isNullOrEmpty() || scrollPosition == null || directoriesCollapsed == null || filesCollapsed == null) null
            else ExplorerStoredPageState(path, scrollPosition, directoriesCollapsed, filesCollapsed)
        }
        is String -> decodeLegacyPageState(value)
        else -> null
    }

    private fun decodeLegacyPageState(value: String): ExplorerStoredPageState? {
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
