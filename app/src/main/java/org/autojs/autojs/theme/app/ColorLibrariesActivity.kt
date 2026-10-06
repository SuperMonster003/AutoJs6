package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.forEach
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import androidx.recyclerview.widget.ThemeColorRecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.image.ColorItems
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.theme.app.ColorEntities.ColorLibrary
import org.autojs.autojs.theme.app.ColorHistoryItemViewHolder.Companion.ColorHistoryItem
import org.autojs.autojs.theme.library.ColorLibraryIds
import org.autojs.autojs.theme.library.ColorLibraryRepository
import org.autojs.autojs.theme.library.ColorSelection
import org.autojs.autojs.theme.library.ColorSelectionCodec
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

    private val presetRows by lazy { presetColorLibraries.map { LibraryRow.Preset(it) } }

    private val libraryAdapter: ColorLibraryAdapter by lazy {
        ColorLibraryAdapter(presetRows, ::onLibraryClick, ::onLibraryLongClick)
    }

    private val colorItemAdapter: ColorItemAdapter by lazy {
        ColorItemAdapter(presetColorItems, isShowLibraryIdentifier = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeChangeNotifier.themeChanged.observe(this) {
            libraryAdapter.notifyDataSetChanged()
            colorItemAdapter.notifyDataSetChanged()
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
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUserLibraries()
    }

    private fun refreshUserLibraries() {
        lifecycleScope.launch {
            val summaries = withContext(Dispatchers.IO) { repository.listPlainLibraries() }
            libraryAdapter.updateData(presetRows + summaries.map { LibraryRow.User(it) })
        }
    }

    private fun onLibraryClick(row: LibraryRow) {
        when (row) {
            is LibraryRow.Preset -> when {
                row.library.isIntelligent -> MaterialDialog.Builder(this)
                    .title(R.string.text_under_development_title)
                    .content(R.string.text_under_development_content)
                    .positiveText(R.string.dialog_button_dismiss)
                    .positiveColorRes(R.color.dialog_button_default)
                    .show()
                else -> startActivity(Intent(this, ColorItemsActivity::class.java).putExtra(INTENT_IDENTIFIER_LIBRARY_ID, row.library.id))
            }
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
                    )
                )
            }
            is LibraryRow.User -> {
                val library = row.summary.library
                ColorLibraryDialogs.showActions(
                    this, library.name, listOf(
                        getString(R.string.text_open) to { onLibraryClick(row) },
                        getString(R.string.text_rename) to { renameLibrary(library) },
                        getString(R.string.text_clone) to { cloneFrom(library.name) { withContext(Dispatchers.IO) { repository.listColors(library.id) }.map { ColorLibraryRepository.NewColor(it.color, it.name) } } },
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
                    savePrefsForSelectionNames(stored, Pref.getStringOrNull(KEY_SELECTED_COLOR_ITEM_NAME))
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
                if (wasSelected) {
                    // Keep the theme color, but record it as a palette selection. zh-CN: 保留主题色, 但改记为调色盘选择.
                    savePrefsForLibraries(COLOR_LIBRARY_ID_PALETTE, 0)
                    savePrefsForLegacyPalette(ThemeColorManager.colorPrimary)
                    saveDatabaseForPaletteHistory(lifecycleScope, applicationContext, ThemeColorManager.colorPrimary)
                    ThemeChangeNotifier.notifyThemeChanged()
                }
                refreshUserLibraries()
            }
        }
    }

    private fun applyHistoryItem(item: ColorHistoryItem) {
        val libraryId = item.libraryId
        val itemId = item.itemId
        when {
            libraryId != null && itemId != null && ColorLibraryIds.isUserPlain(libraryId) -> lifecycleScope.launch {
                val color = withContext(Dispatchers.IO) { repository.findColor(itemId.toLong()) }
                val library = color?.let { withContext(Dispatchers.IO) { repository.findLibrary(it.libraryId) } }
                when {
                    color == null || library == null -> ViewUtils.showToast(this@ColorLibrariesActivity, R.string.error_failed_to_apply_current_color_history, true)
                    else -> applyUserLibraryColor(library.name, color)
                }
            }
            libraryId != null && itemId != null -> {
                presetColorLibraries.find { it.id == libraryId }?.colors?.find { it.itemId == itemId }?.let { applyPresetItem(it) }
                    ?: ViewUtils.showToast(this, R.string.error_failed_to_apply_current_color_history, true)
            }
            else -> onColorPickerConfirmed(item.colorInt)
        }
    }

    private fun applyPresetItem(item: PresetColorItem) {
        savePrefsForLibraries(item)
        savePrefsForLegacy(this, item)
        saveDatabaseForColorHistory(applicationContext = applicationContext, libraryId = item.libraryId, itemId = item.itemId)
        isForciblyEnableAppBarColorTransition = true
        ThemeColorCoordinator.apply(getColor(item.colorRes), ThemeColorSource.USER)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_color_libraries, menu)

        binding.toolbar.setMenuIconsColorByColorLuminance(this, currentColor)

        setUpSearchMenu(
            menu,
            onQueryTextSimpleListener = { query ->
                filterColorsFromColorItems(query, presetColorItems, colorItemAdapter)
            },
            onMenuItemActionExpand = {
                mRecyclerView.adapter = colorItemAdapter
                setUpSelectedPosition(colorItemAdapter)
                menu.forEach { it.isVisible = it.isVisible.not() }
            },
            onMenuItemActionCollapse = {
                mRecyclerView.adapter = libraryAdapter
                menu.forEach { it.isVisible = it.isVisible.not() }
            }
        )?.apply { queryHint = getString(R.string.text_search_all_colors) }

        return super.onCreateOptionsMenu(menu)
    }

    private fun setUpSelectedPosition(adapter: ColorItemAdapter) {
        when (val selection = currentColorSelection()) {
            ColorSelection.Palette -> Unit
            is ColorSelection.Preset -> {
                adapter.selectedLibraryId = selection.libraryId
                // Without a stored item (e.g. a legacy default selection), match the current theme color.
                // zh-CN: 没有保存的条目时 (例如 legacy 的默认色选择), 按当前主题色匹配.
                adapter.selectedItemId = selection.itemId ?: adapter.items().firstOrNull {
                    it.libraryId == selection.libraryId && getColor(it.colorRes) == ThemeColorManager.colorPrimary
                }?.itemId ?: SELECT_NONE
            }
            else -> ColorSelectionCodec.encode(selection).let { (libraryId, itemId) ->
                adapter.selectedLibraryId = libraryId
                adapter.selectedItemId = itemId
            }
        }
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
            ViewUtils.showToast(this, R.string.text_under_development)
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

        data class PresetColorItem(
            val itemId: Int,
            val libraryId: Int,
            val colorRes: Int,
            val nameRes: Int,
        )

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
