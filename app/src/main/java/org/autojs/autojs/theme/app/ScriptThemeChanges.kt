package org.autojs.autojs.theme.app

import android.content.Context
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.theme.ScriptUndoRules
import org.autojs.autojs.theme.ScriptUndoSnapshot
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorCoordinator
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.ThemeColorSource
import org.autojs.autojs.theme.app.ColorEntities.HistorySource
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_PALETTE
import org.autojs.autojs.theme.intelligent.IntelligentColorEngine
import org.autojs.autojs.theme.library.ColorLibraryIds

/**
 * Selection, history and undo of theme color changes made by scripts (D11, roadmap P6.3). A merged script color
 * becomes a palette selection recorded in the history as "Script: name"; the state before the first of a run of
 * script changes is kept until an explicit choice, so "Undo script change" can restore it, including the
 * intelligent library that was active.
 * zh-CN: 脚本修改主题色时的选中状态, 历史记录与撤销 (D11, Roadmap P6.3). 合并后的脚本颜色记为调色板选中项,
 * 并以 "脚本: 名称" 写入历史; 一连串脚本修改中第一次之前的状态会保留到下一次显式选择, 使 "撤销脚本修改" 可以恢复它,
 * 包括当时激活的智能库.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object ScriptThemeChanges : ThemeColorCoordinator.CommitObserver {

    private lateinit var appContext: Context

    /** Called once from `App.onCreate`. zh-CN: 在 `App.onCreate` 中调用一次. */
    fun init(context: Context) {
        appContext = context.applicationContext
        ThemeColorCoordinator.commitObserver = this
    }

    /** Whether the latest explicit change came from a script and can be undone. zh-CN: 最近一次显式修改是否来自脚本且可以撤销. */
    val canUndo: Boolean
        get() = MiscStateKeys.THEME_COLOR_SCRIPT_UNDO.storedText() != null

    override fun beforeCommit(themeColor: ThemeColor, source: ThemeColorSource) {
        if (ScriptUndoRules.capturesBefore(source, canUndo)) MiscStateKeys.THEME_COLOR_SCRIPT_UNDO.setStoredText(capture().encode())
    }

    override fun afterCommit(themeColor: ThemeColor, source: ThemeColorSource, scriptName: String?) {
        if (source == ThemeColorSource.SCRIPT) record(themeColor.colorPrimary, scriptName)
        if (ScriptUndoRules.dropsAfter(source)) MiscStateKeys.THEME_COLOR_SCRIPT_UNDO.remove()
    }

    /**
     * Leaves an intelligent library deactivated by a script with its current color as a palette selection.
     * zh-CN: 脚本停用智能库后, 以当前颜色作为调色板选中项.
     */
    fun onDeactivatedByScript() {
        val color = ThemeColorManager.colorPrimary
        ColorSelectBaseActivity.savePrefsForLibraries(COLOR_LIBRARY_ID_PALETTE, 0)
        ColorSelectBaseActivity.savePrefsForLegacyPalette(color)
        ColorSelectBaseActivity.savePrefsForSelectionNames(null, null)
        ThemeChangeNotifier.notifyThemeChanged()
    }

    /**
     * Restores the state before the script changes; call on the main thread. False when there is nothing to undo.
     * zh-CN: 恢复脚本修改之前的状态; 在主线程调用. 没有可撤销的内容时返回 false.
     */
    fun undo(): Boolean {
        val snapshot = ScriptUndoSnapshot.decode(MiscStateKeys.THEME_COLOR_SCRIPT_UNDO.storedText())
        MiscStateKeys.THEME_COLOR_SCRIPT_UNDO.remove()
        snapshot ?: return false
        val intelligent = snapshot.intelligentSnapshot
        if (intelligent != null && IntelligentColorEngine.restoreState(intelligent, snapshot.intelligentRuntime)) return true
        MiscStateKeys.COLOR_SELECT_LIBRARY_ID.set(snapshot.libraryId)
        MiscStateKeys.COLOR_SELECT_ITEM_ID.set(snapshot.itemId)
        MiscStateKeys.COLOR_SELECT_LEGACY_SELECTED_INDEX.set(snapshot.legacyIndex)
        snapshot.customColor?.let { MiscStateKeys.COLOR_SELECT_CUSTOM_COLOR.set(it) }
        ColorSelectBaseActivity.savePrefsForSelectionNames(snapshot.libraryName, snapshot.itemName)
        ThemeColorCoordinator.apply(ThemeColor(snapshot.primary, snapshot.dark, snapshot.accent), ThemeColorSource.RESTORE)
        return true
    }

    private fun capture(): ScriptUndoSnapshot {
        val current = ThemeColorManager.currentThemeColor
        val (intelligentSnapshot, intelligentRuntime) = IntelligentColorEngine.exportState()
        return ScriptUndoSnapshot(
            libraryId = MiscStateKeys.COLOR_SELECT_LIBRARY_ID.get(),
            itemId = MiscStateKeys.COLOR_SELECT_ITEM_ID.get(),
            legacyIndex = (MiscStateKeys.COLOR_SELECT_LEGACY_SELECTED_INDEX.storedOrNull() ?: ColorLibraryIds.SELECT_NONE),
            customColor = MiscStateKeys.COLOR_SELECT_CUSTOM_COLOR.storedOrNull(),
            libraryName = MiscStateKeys.COLOR_SELECT_LIBRARY_NAME.storedOrNull(),
            itemName = MiscStateKeys.COLOR_SELECT_ITEM_NAME.storedOrNull(),
            primary = current.colorPrimary,
            dark = current.colorPrimaryDark,
            accent = current.colorAccent,
            intelligentSnapshot = intelligentSnapshot,
            intelligentRuntime = intelligentRuntime,
        )
    }

    /** The merged script color as a palette selection, recorded as a script use. zh-CN: 合并后的脚本颜色记为调色板选中项, 并作为脚本使用记录. */
    private fun record(color: Int, scriptName: String?) {
        ColorSelectBaseActivity.savePrefsForLibraries(COLOR_LIBRARY_ID_PALETTE, 0)
        ColorSelectBaseActivity.savePrefsForLegacyPalette(color)
        ColorSelectBaseActivity.savePrefsForSelectionNames(null, null)
        ColorSelectBaseActivity.saveDatabaseForPaletteHistory(
            applicationContext = appContext, color = color, source = HistorySource.SCRIPT, sourceDetail = scriptName,
        )
    }

}
