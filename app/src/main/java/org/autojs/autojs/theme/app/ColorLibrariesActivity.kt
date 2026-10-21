package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.forEach
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import androidx.recyclerview.widget.ThemeColorRecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.image.ColorItems
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.theme.app.ColorEntities.ColorLibrary
import org.autojs.autojs.theme.app.ColorHistoryItemViewHolder.Companion.ColorHistoryItem
import org.autojs.autojs.theme.intelligent.IntelligentColorEngine
import org.autojs.autojs.theme.intelligent.IntelligentPresets
import org.autojs.autojs.theme.library.ColorLibraryIds
import org.autojs.autojs.theme.library.ColorLibraryRepository
import org.autojs.autojs.theme.library.ColorSelection
import org.autojs.autojs.util.ViewUtils.setOnSubtitleViewClickListener
import org.autojs.autojs.util.ViewUtils.setOnTitleViewClickListener
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.ThemeColorCoordinator
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.ThemeColorSource
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByColorLuminance
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MtActivityColorLibrariesBinding

@SuppressLint("NotifyDataSetChanged")
class ColorLibrariesActivity : ColorSelectBaseActivity() {

    private lateinit var binding: MtActivityColorLibrariesBinding

    private lateinit var mRecyclerView: ThemeColorRecyclerView

    private val repository by lazy { ColorLibraryRepository.get(this) }

    private val exchange = ColorLibraryExchange(this) { refreshUserLibraries() }

    private val presetRows by lazy { presetColorLibraries.map { LibraryRow.Preset(it) } }

    private val libraryAdapter: ColorLibraryAdapter by lazy {
        ColorLibraryAdapter(presetRows, ::onLibraryClick, ::onLibraryLongClick)
    }

    private val searchAdapter: GlobalColorSearchAdapter by lazy {
        GlobalColorSearchAdapter(::isSearchItemChecked, ::onSearchItemClick)
    }

    /** User colors and intelligent groups loaded when the search opens. zh-CN: 打开搜索时加载的用户颜色与智能库组. */
    private var searchableUserItems: List<GlobalColorSearchItem> = emptyList()

    private var lastQuery: String? = null

    private var searchMenuItem: MenuItem? = null
    private var reorderMenuItem: MenuItem? = null
    private var undoMenuItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeChangeNotifier.themeChanged.observe(this) {
            libraryAdapter.notifyDataSetChanged()
            searchAdapter.notifyDataSetChanged()
            updateUndoMenuItem()
        }

        MtActivityColorLibrariesBinding.inflate(layoutInflater).let {
            binding = it
            setContentView(it.root)
            setUpAppBar(it.appBar, it.appBarContainer)
            it.toolbar.let { toolbar ->
                setUpToolbar(toolbar)
                toolbar.setOnTitleViewClickListener { showThemeColorDetails() }
                toolbar.setOnSubtitleViewClickListener { showThemeColorDetails() }
                toolbar.setOnLongClickListener { true.also { /* toggleFabVisibility() */ } }
            }
        }

        binding.colorLibrariesRecyclerView.let {
            mRecyclerView = it
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = libraryAdapter
            it.addItemDecoration(DividerItemDecoration(this, VERTICAL))
            it.excludePaddingClippableViewFromBottomNavigationBar()
            ItemTouchHelper(LibraryDragCallback()).attachToRecyclerView(it)
        }
    }

    private fun setReordering(reordering: Boolean) {
        if (reordering) {
            if (libraryAdapter.userRows.size < 2) {
                ViewUtils.showToast(this, R.string.text_not_enough_color_libraries_to_reorder)
                return
            }
            searchMenuItem?.collapseActionView()
            ViewUtils.showToast(this, R.string.text_reorder_color_libraries_hint)
        }
        libraryAdapter.isReordering = reordering
        reorderMenuItem?.setTitle(if (reordering) R.string.text_finish_reordering else R.string.text_reorder_color_libraries)
    }

    /** Saves the order after every drag, so leaving the page never loses it. zh-CN: 每次拖动结束即保存顺序, 离开页面不会丢失. */
    private fun saveLibraryOrder() {
        val order = libraryAdapter.userRows.map { it.summary.library.id }
        lifecycleScope.launch { withContext(Dispatchers.IO) { repository.reorderLibraries(order) } }
    }

    private fun chooseLibrariesToDelete() {
        val libraries = libraryAdapter.userRows.map { it.summary.library }
        if (libraries.isEmpty()) {
            ViewUtils.showToast(this, R.string.text_no_user_color_libraries)
            return
        }
        ColorLibraryDialogs.showMultiChoice(this, R.string.text_choose_color_libraries_to_delete, libraries.map { it.name }) { indices ->
            val chosen = indices.map { libraries[it] }
            ColorLibraryDialogs.showConfirm(this, getString(R.string.text_delete_color_libraries_confirm, chosen.size)) {
                lifecycleScope.launch {
                    val selectedRowId = (currentColorSelection() as? ColorSelection.UserColor)?.libraryRowId
                    withContext(Dispatchers.IO) { chosen.forEach { repository.deleteLibrary(it) } }
                    if (chosen.any { it.id == selectedRowId }) fallBackToPaletteSelection(this@ColorLibrariesActivity)
                    refreshUserLibraries()
                }
            }
        }
    }

    private inner class LibraryDragCallback : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

        override fun isLongPressDragEnabled() = libraryAdapter.isReordering

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int = when {
            libraryAdapter.isUserRow(viewHolder.bindingAdapterPosition) -> super.getMovementFlags(recyclerView, viewHolder)
            else -> 0
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            return libraryAdapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            saveLibraryOrder()
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

    }

    override fun onResume() {
        super.onResume()
        refreshUserLibraries()
        updateUndoMenuItem()
    }

    /**
     * "Undo script change" shows while the latest explicit change came from a script (D11, roadmap P6.3).
     * zh-CN: 最近一次显式修改来自脚本时显示 "撤销脚本修改" (D11, Roadmap P6.3).
     */
    private fun updateUndoMenuItem() {
        undoMenuItem?.isVisible = ScriptThemeChanges.canUndo && searchMenuItem?.isActionViewExpanded != true
    }

    private fun undoScriptChange() {
        if (ScriptThemeChanges.undo()) {
            isForciblyEnableAppBarColorTransition = true
            ViewUtils.showToast(this, R.string.text_script_change_undone)
        }
        updateUndoMenuItem()
        refreshUserLibraries()
    }

    private fun refreshUserLibraries() {
        lifecycleScope.launch {
            val summaries = withContext(Dispatchers.IO) { repository.listPlainLibraries() }
            val intelligentCount = IntelligentPresets.all(this@ColorLibrariesActivity).size + withContext(Dispatchers.IO) { repository.listIntelligentLibraries().size }
            val intelligentDescription = buildString {
                append(resources.getQuantityString(R.plurals.text_items_total_sum_with_colon, intelligentCount, intelligentCount))
                IntelligentColorEngine.activeSnapshot?.let { append("\n${getString(R.string.text_in_use)}: ${it.name}") }
            }
            val rows: MutableList<LibraryRow> = presetRows.map { row ->
                if (row.library.isIntelligent) row.copy(description = intelligentDescription) else row
            }.toMutableList()
            // Favorites always show; recommendations hide while there is nothing to recommend (roadmap P5.1, P5.4).
            // zh-CN: 收藏总是显示; 没有可推荐的颜色时隐藏推荐 (Roadmap P5.1, P5.4).
            val favoriteCount = withContext(Dispatchers.IO) { repository.listFavorites().size }
            val recommendationCount = loadRecommendations().size
            val virtualRows = listOfNotNull(
                LibraryRow.Virtual(ColorLibraryIds.FAVORITES, favoriteCount),
                LibraryRow.Virtual(ColorLibraryIds.RECOMMENDED, recommendationCount).takeIf { recommendationCount > 0 },
            )
            rows.addAll(rows.indexOfFirst { it is LibraryRow.Preset && it.library.isIntelligent } + 1, virtualRows)
            libraryAdapter.updateData(rows + summaries.map { LibraryRow.User(it) })
        }
    }

    private fun onLibraryClick(row: LibraryRow) {
        when (row) {
            is LibraryRow.Preset -> when {
                row.library.isIntelligent -> IntelligentLibrariesActivity.launch(this)
                else -> startActivity(Intent(this, ColorItemsActivity::class.java).putExtra(INTENT_IDENTIFIER_LIBRARY_ID, row.library.id))
            }
            is LibraryRow.Virtual -> VirtualColorLibraryActivity.launch(this, row.libraryId)
            is LibraryRow.User -> UserColorLibraryActivity.launch(this, row.summary.library.id)
        }
    }

    private fun onLibraryLongClick(row: LibraryRow) {
        when (row) {
            is LibraryRow.Preset -> if (!row.library.isIntelligent) {
                ColorLibraryDialogs.showActions(
                    this, getString(row.library.nameRes), listOf(
                        getString(R.string.text_open) to { onLibraryClick(row) },
                        getString(R.string.text_clone) to { cloneFrom(getString(row.library.nameRes), presetColorsOf(row)) },
                        getString(R.string.text_export) to { exchange.export(listOf(ColorLibraryExchange.Source.PresetPlain(row.library.id))) },
                    )
                )
            }
            is LibraryRow.Virtual -> onLibraryClick(row)
            is LibraryRow.User -> {
                val library = row.summary.library
                ColorLibraryDialogs.showActions(
                    this, library.name, listOf(
                        getString(R.string.text_open) to { onLibraryClick(row) },
                        getString(R.string.text_rename) to { renameLibrary(library) },
                        getString(R.string.text_clone) to { cloneFrom(library.name) { withContext(Dispatchers.IO) { repository.listColors(library.id) }.map { ColorLibraryRepository.NewColor(it.color, it.name) } } },
                        getString(R.string.text_export) to { exchange.export(listOf(ColorLibraryExchange.Source.User(library.id))) },
                        getString(R.string.text_delete) to { deleteLibrary(library) },
                    )
                )
            }
        }
    }

    private fun presetColorsOf(row: LibraryRow.Preset): suspend () -> List<ColorLibraryRepository.NewColor> = {
        row.library.colors.map { ColorLibraryRepository.NewColor(getColor(it.colorRes), getString(it.nameRes)) }
    }

    private fun createLibrary() {
        ColorLibraryDialogs.showNameInput(this, R.string.text_new_color_library, null) { name ->
            name ?: return@showNameInput
            lifecycleScope.launch {
                val created = withContext(Dispatchers.IO) { repository.createPlainLibrary(name, ColorLibrary.Kind.CREATED) }
                UserColorLibraryActivity.launch(this@ColorLibrariesActivity, created.id)
            }
        }
    }

    private fun chooseLibraryToClone() {
        lifecycleScope.launch {
            val sources = listColorSources()
            ColorLibraryDialogs.showSingleChoice(this@ColorLibrariesActivity, R.string.text_choose_color_library_to_clone, sources.map { it.name }) { which ->
                cloneFrom(sources[which].name, sources[which].loadColors)
            }
        }
    }

    private fun cloneFrom(sourceName: String, loadColors: suspend () -> List<ColorLibraryRepository.NewColor>) {
        lifecycleScope.launch {
            val colors = loadColors()
            val name = getString(R.string.text_color_library_copy_name, sourceName)
            val created = withContext(Dispatchers.IO) {
                repository.createPlainLibrary(name, ColorLibrary.Kind.CLONED, origin = sourceName, colors = colors)
            }
            UserColorLibraryActivity.launch(this@ColorLibrariesActivity, created.id)
        }
    }

    private fun renameLibrary(library: ColorLibrary) {
        ColorLibraryDialogs.showNameInput(this, R.string.text_rename_color_library, library.name) { name ->
            name ?: return@showNameInput
            lifecycleScope.launch {
                val stored = withContext(Dispatchers.IO) { repository.renameLibrary(library.id, name) }
                if ((currentColorSelection() as? ColorSelection.UserColor)?.libraryRowId == library.id) {
                    savePrefsForSelectionNames(stored, MiscStateKeys.COLOR_SELECT_ITEM_NAME.storedOrNull())
                    updateSubtitle(binding.toolbar)
                }
                refreshUserLibraries()
            }
        }
    }

    private fun deleteLibrary(library: ColorLibrary) {
        ColorLibraryDialogs.showConfirm(this, getString(R.string.text_delete_color_library_confirm, library.name)) {
            lifecycleScope.launch {
                val wasSelected = (currentColorSelection() as? ColorSelection.UserColor)?.libraryRowId == library.id
                withContext(Dispatchers.IO) { repository.deleteLibrary(library) }
                if (wasSelected) fallBackToPaletteSelection(this@ColorLibrariesActivity)
                refreshUserLibraries()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_color_libraries, menu)
        searchMenuItem = menu.findItem(R.id.action_search_color)
        reorderMenuItem = menu.findItem(R.id.action_reorder_color_libraries)
        undoMenuItem = menu.findItem(R.id.action_undo_script_change)
        updateUndoMenuItem()

        binding.toolbar.setMenuIconsColorByColorLuminance(this, currentColor)

        setUpSearchMenu(
            menu,
            onQueryTextSimpleListener = { query -> search(query) },
            onMenuItemActionExpand = {
                if (libraryAdapter.isReordering) setReordering(false)
                mRecyclerView.adapter = searchAdapter
                loadSearchableUserItems()
                menu.forEach { it.isVisible = it.isVisible.not() }
                undoMenuItem?.isVisible = false
            },
            onMenuItemActionCollapse = {
                mRecyclerView.adapter = libraryAdapter
                menu.forEach { it.isVisible = it.isVisible.not() }
                updateUndoMenuItem()
            }
        )?.apply { queryHint = getString(R.string.text_search_all_colors) }

        return super.onCreateOptionsMenu(menu)
    }

    /**
     * Searches preset colors, user library colors and intelligent groups (roadmap P2.4).
     * zh-CN: 搜索预设颜色, 用户库颜色与智能库的组 (Roadmap P2.4).
     */
    private fun search(query: String?) {
        lastQuery = query
        val userItems = searchableUserItems
        searchColors(query, { matcher ->
            val presets = presetColorItems.filter { matcher?.matches(it) ?: true }.map { GlobalColorSearchItem.Preset(it) }
            presets + userItems.filter { item -> matcher?.let { matches(it, item) } ?: true }
        }) { searchAdapter.updateData(it) }
    }

    private fun matches(matcher: ColorQueryMatcher, item: GlobalColorSearchItem): Boolean = when (item) {
        is GlobalColorSearchItem.Preset -> matcher.matches(item.item)
        is GlobalColorSearchItem.UserColor -> {
            val hex = String.format("#%06X", 0xFFFFFF and item.color.color)
            val name = item.color.name ?: hex
            matcher.matches(listOf(hex), { name }, { name })
        }
        is GlobalColorSearchItem.Group -> {
            val colors = item.group.states.values.mapNotNull { it.primary }
            val name = IntelligentSummaryFormatter.groupName(item.snapshot.config, item.group)
            matcher.matches(colors.map { String.format("#%06X", 0xFFFFFF and it) }, { name }, { name })
        }
    }

    private fun loadSearchableUserItems() {
        lifecycleScope.launch {
            val (colors, intelligent) = withContext(Dispatchers.IO) { repository.listAllPlainColors() to repository.listIntelligentLibraries() }
            val snapshots = IntelligentPresets.all(this@ColorLibrariesActivity) + intelligent
            searchableUserItems = colors.map { (library, color) -> GlobalColorSearchItem.UserColor(library.name, color) } +
                    snapshots.flatMap { snapshot -> snapshot.groups.filter { it.hasColor }.map { GlobalColorSearchItem.Group(snapshot, it) } }
            search(lastQuery)
        }
    }

    private fun isSearchItemChecked(item: GlobalColorSearchItem): Boolean = when (item) {
        is GlobalColorSearchItem.Preset -> (currentColorSelection() as? ColorSelection.Preset)?.let { selection ->
            // Without a stored item (e.g. a legacy default selection), match the current theme color.
            // zh-CN: 没有保存的条目时 (例如 legacy 的默认色选择), 按当前主题色匹配.
            selection.libraryId == item.item.libraryId && when (val itemId = selection.itemId) {
                null -> getColor(item.item.colorRes) == ThemeColorManager.colorPrimary
                else -> itemId == item.item.itemId
            }
        } == true
        is GlobalColorSearchItem.UserColor -> (currentColorSelection() as? ColorSelection.UserColor)?.let {
            it.libraryRowId == item.color.libraryId && it.colorRowId == item.color.id
        } == true
        is GlobalColorSearchItem.Group -> IntelligentColorEngine.activeSnapshot?.libraryId == item.snapshot.libraryId &&
                IntelligentColorEngine.shownGroupId == item.group.id
    }

    private fun onSearchItemClick(item: GlobalColorSearchItem) {
        when (item) {
            is GlobalColorSearchItem.Preset -> applyPresetItem(item.item)
            is GlobalColorSearchItem.UserColor -> applyUserLibraryColor(item.libraryName, item.color)
            is GlobalColorSearchItem.Group -> activateIntelligentLibrary(item.snapshot, item.group.id)
        }
        searchAdapter.notifyDataSetChanged()
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_color_palette -> {
            showColorPicker()
            true
        }
        R.id.action_show_all_history_entries -> {
            showColorHistory { selectedHistoryItem -> applyHistoryItem(selectedHistoryItem) }
            true
        }
        R.id.action_new_color_library -> {
            createLibrary()
            true
        }
        R.id.action_import_color_library -> {
            exchange.import()
            true
        }
        R.id.action_export_color_libraries -> {
            lifecycleScope.launch { exchange.chooseAndExport(exchange.allChoices()) }
            true
        }
        R.id.action_derived_colors -> {
            DerivedColorsSetting.show(this)
            true
        }
        R.id.action_undo_script_change -> {
            undoScriptChange()
            true
        }
        R.id.action_reorder_color_libraries -> {
            setReordering(!libraryAdapter.isReordering)
            true
        }
        R.id.action_delete_color_libraries -> {
            chooseLibrariesToDelete()
            true
        }
        R.id.action_clone_color_library -> {
            chooseLibraryToClone()
            true
        }
        R.id.action_locate_current_theme_color -> {
            locateCurrentThemeColor()
            true
        }
        R.id.action_toggle_color_select_layout -> {
            isLegacyLayout = true
            launch(this, isToggled = true)
            true
        }
        R.id.action_color_search_help -> {
            showColorSearchHelp()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun locateCurrentThemeColor() {
        checkAndGetTargetInfoForThemeColorLocate()?.let { target ->
            Intent(this, ColorItemsActivity::class.java).apply {
                putExtra(INTENT_IDENTIFIER_LIBRARY_ID, target.libraryId)
                putExtra(INTENT_IDENTIFIER_COLOR_ITEM_ID_SCROLL_TO, target.libraryItemId)
            }.let { startActivity(it) }
        }
    }

    override fun getSubtitle(withHexSuffix: Boolean) = getCurrentColorSummary(this, withHexSuffix = withHexSuffix)

    companion object {

        const val COLOR_LIBRARY_ID_DEFAULT = 0x01
        const val COLOR_LIBRARY_ID_MATERIAL = 0x02
        const val COLOR_LIBRARY_ID_ANDROID = 0x03
        const val COLOR_LIBRARY_ID_CSS = 0x04
        const val COLOR_LIBRARY_ID_WEB = 0x05

        const val COLOR_LIBRARY_ID_PALETTE = 0x21
        const val COLOR_LIBRARY_ID_INTELLIGENT = 0x22

        const val COLOR_LIBRARY_FROM_IMPORTED = 0x01
        const val COLOR_LIBRARY_FROM_CLONED = 0x02
        const val COLOR_LIBRARY_FROM_CREATED = 0x03

        val presetColorLibraries by lazy {
            listOf(
                PresetColorLibrary(
                    id = COLOR_LIBRARY_ID_DEFAULT,
                    nameRes = R.string.color_library_default_colors,
                    titleRes = R.string.color_library_title_default_colors,
                    identifierRes = R.string.color_library_identifier_default_colors,
                    colors = listOf(
                        R.color.web_navajo_white to R.string.web_navajo_white,
                        R.color.md_teal_800 to R.string.md_teal_800,
                        R.color.md_blue_gray_800 to R.string.md_blue_gray_800,
                        R.color.window_background_light to R.string.color_name_window_background_light,
                        R.color.window_background_night to R.string.color_name_window_background_night,
                    ).mapIndexed { index, (colorRes, nameRes) ->
                        PresetColorItem(index, COLOR_LIBRARY_ID_DEFAULT, colorRes, nameRes)
                    },
                ),
                PresetColorLibrary(
                    id = COLOR_LIBRARY_ID_INTELLIGENT,
                    nameRes = R.string.color_library_intelligent_colors,
                    titleRes = R.string.color_library_title_intelligent_colors,
                    identifierRes = R.string.color_library_identifier_intelligent_colors,
                    colors = emptyList<PresetColorItem>(),
                ),
                PresetColorLibrary(
                    id = COLOR_LIBRARY_ID_MATERIAL,
                    nameRes = R.string.color_library_material_design_colors,
                    titleRes = R.string.color_library_title_material_design_colors,
                    identifierRes = R.string.color_library_identifier_material_design_colors,
                    colors = ColorItems.MATERIAL_COLORS.mapIndexed { index, (colorRes, nameRes) ->
                        PresetColorItem(index, COLOR_LIBRARY_ID_MATERIAL, colorRes, nameRes)
                    },
                ),
                PresetColorLibrary(
                    id = COLOR_LIBRARY_ID_ANDROID,
                    nameRes = R.string.color_library_android_colors,
                    titleRes = R.string.color_library_title_android_colors,
                    identifierRes = R.string.color_library_identifier_android_colors,
                    colors = ColorItems.ANDROID_COLORS.mapIndexed { index, (colorRes, nameRes) ->
                        PresetColorItem(index, COLOR_LIBRARY_ID_ANDROID, colorRes, nameRes)
                    },
                ),
                PresetColorLibrary(
                    id = COLOR_LIBRARY_ID_CSS,
                    nameRes = R.string.color_library_css_colors,
                    titleRes = R.string.color_library_title_css_colors,
                    identifierRes = R.string.color_library_identifier_css_colors,
                    colors = ColorItems.CSS_COLORS.mapIndexed { index, (colorRes, nameRes) ->
                        PresetColorItem(index, COLOR_LIBRARY_ID_CSS, colorRes, nameRes)
                    },
                ),
                PresetColorLibrary(
                    id = COLOR_LIBRARY_ID_WEB,
                    nameRes = R.string.color_library_web_colors,
                    titleRes = R.string.color_library_title_web_colors,
                    identifierRes = R.string.color_library_identifier_web_colors,
                    colors = ColorItems.WEB_COLORS.mapIndexed { index, (colorRes, nameRes) ->
                        PresetColorItem(index, COLOR_LIBRARY_ID_WEB, colorRes, nameRes)
                    },
                ),
            )
        }

        val presetColorItems by lazy {
            presetColorLibraries.flatMap { it.colors }
        }

        /** Item ids of the two window backgrounds in the default library. zh-CN: 默认颜色库中两个窗口背景色的条目 id. */
        const val ITEM_ID_WINDOW_BACKGROUND_LIGHT = 3
        const val ITEM_ID_WINDOW_BACKGROUND_NIGHT = 4

        /**
         * The items shown on a library page. In the default library the two window backgrounds are merged into one
         * "background color" entry (roadmap P1.6), which keeps the light item id so stored selections still match.
         * zh-CN: 颜色库页面显示的条目. 默认颜色库中的两个窗口背景色合并为一个 "背景色" 条目 (Roadmap P1.6),
         * 该条目沿用亮色的条目 id, 使已保存的选中状态仍能对应.
         */
        fun displayColorsOf(library: PresetColorLibrary): List<PresetColorItem> = when {
            !library.isDefault -> library.colors
            else -> library.colors.mapNotNull { item ->
                when (item.itemId) {
                    ITEM_ID_WINDOW_BACKGROUND_LIGHT -> item.copy(nameRes = R.string.text_background_color, mergedColorRes = R.color.window_background_night)
                    ITEM_ID_WINDOW_BACKGROUND_NIGHT -> null
                    else -> item
                }
            }
        }

        data class PresetColorItem(
            val itemId: Int,
            val libraryId: Int,
            val colorRes: Int,
            val nameRes: Int,
            /** A second color shown with [colorRes] for a merged entry. zh-CN: 合并条目中与 [colorRes] 一同显示的第二种颜色. */
            val mergedColorRes: Int? = null,
        ) {
            val isMerged: Boolean
                get() = mergedColorRes != null
        }

        data class PresetColorLibrary(
            val id: Int,
            val nameRes: Int,
            val titleRes: Int,
            val identifierRes: Int,
            val colors: List<PresetColorItem> = emptyList(),
        ) {
            val isIntelligent get() = id == COLOR_LIBRARY_ID_INTELLIGENT
            val isDefault get() = id == COLOR_LIBRARY_ID_DEFAULT
            val isMaterial get() = id == COLOR_LIBRARY_ID_MATERIAL
        }

    }

}
