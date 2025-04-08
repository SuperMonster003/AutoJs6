package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.forEach
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import androidx.recyclerview.widget.ThemeColorRecyclerView
import org.autojs.autojs.core.image.ColorItems
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByColorLuminance
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MtActivityColorLibrariesBinding

@SuppressLint("NotifyDataSetChanged")
class ColorLibrariesActivity : ColorSelectBaseActivity() {

    private lateinit var binding: MtActivityColorLibrariesBinding

    private lateinit var mRecyclerView: ThemeColorRecyclerView

    private val libraryAdapter: ColorLibraryAdapter by lazy {
        ColorLibraryAdapter(presetColorLibraries) { library ->
            val intent = Intent(this, ColorItemsActivity::class.java)
            intent.putExtra(INTENT_IDENTIFIER_LIBRARY_ID, library.id)
            startActivity(intent)
        }
    }

    private val colorItemAdapter: ColorItemAdapter by lazy {
        ColorItemAdapter(presetColorItems, isShowLibraryIdentifier = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeChangeNotifier.themeChanged.observe(this) {
            updateAppBarColorContent(ThemeColorManager.colorPrimary)
            libraryAdapter.notifyDataSetChanged()
            colorItemAdapter.notifyDataSetChanged()
        }

        MtActivityColorLibrariesBinding.inflate(layoutInflater).let {
            binding = it
            setContentView(it.root)
            setUpAppBar(it.appBar, it.appBarContainer)
            it.toolbar.let { toolbar ->
                setUpToolbar(toolbar)
                toolbar.setOnClickListener { true.also { showThemeColorDetails() } }
                toolbar.setOnLongClickListener { true.also { /* toggleFabVisibility() */ } }
            }
        }

        binding.colorLibrariesRecyclerView.let {
            mRecyclerView = it
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = libraryAdapter
            it.addItemDecoration(DividerItemDecoration(this, VERTICAL))
            ViewUtils.excludePaddingClippableViewFromNavigationBar(it)
        }
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
        when (Pref.getInt(KEY_SELECTED_COLOR_LIBRARY_ID, SELECT_NONE)) {
            SELECT_NONE -> when (val legacyIndex = Pref.getInt(KEY_LEGACY_SELECTED_COLOR_INDEX, SELECT_NONE)) {
                customColorPosition -> Unit
                SELECT_NONE, defaultColorPosition -> {
                    adapter.selectedLibraryId = COLOR_LIBRARY_ID_DEFAULT
                    adapter.selectedItemId = adapter.items().firstOrNull {
                        it.libraryId == COLOR_LIBRARY_ID_DEFAULT && getColor(it.colorRes) == getColor(R.color.theme_color_default)
                    }?.itemId ?: SELECT_NONE
                }
                else -> when (val calculatedIndex = legacyIndex - maxOf(customColorPosition, defaultColorPosition) - 1) {
                    in ColorItems.MATERIAL_COLORS.indices -> {
                        val (colorRes, _) = ColorItems.MATERIAL_COLORS[calculatedIndex]
                        adapter.selectedLibraryId = COLOR_LIBRARY_ID_MATERIAL
                        adapter.selectedItemId = adapter.items().firstOrNull {
                            it.libraryId == COLOR_LIBRARY_ID_MATERIAL && it.colorRes == colorRes
                        }?.itemId ?: SELECT_NONE
                    }
                }
            }
            else -> {
                adapter.selectedLibraryId = Pref.getInt(KEY_SELECTED_COLOR_LIBRARY_ID, SELECT_NONE)
                adapter.selectedItemId = Pref.getInt(KEY_SELECTED_COLOR_ITEM_ID, SELECT_NONE)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_color_palette -> {
            showColorPicker()
            true
        }
        R.id.action_show_all_histories -> {
            showColorHistories { selectedHistoryItem ->
                val identifier = selectedHistoryItem.colorLibraryIdentifier ?: run {
                    ViewUtils.showToast(this@ColorLibrariesActivity, R.string.error_failed_to_apply_current_color_history, true)
                    return@showColorHistories
                }
                if (identifier == getString(R.string.color_library_identifier_palette)) {
                    onColorPickerConfirmed(selectedHistoryItem.colorInt)
                    return@showColorHistories
                }
                presetColorLibraries.find { getString(it.identifierRes) == identifier }?.let { library ->
                    library.colors.firstOrNull { getColor(it.colorRes) == selectedHistoryItem.colorInt }?.let { item ->
                        savePrefsForLibraries(item)
                        savePrefsForLegacy(this, item)

                        saveDatabaseForColorHistories(
                            applicationContext = applicationContext,
                            libraryId = item.libraryId,
                            itemId = item.itemId,
                        )

                        isForciblyEnableAppBarColorTransition = true

                        ThemeColorManager.setThemeColor(getColor(item.colorRes))
                        ThemeChangeNotifier.notifyThemeChanged()
                    }
                } ?: ViewUtils.showToast(this@ColorLibrariesActivity, R.string.error_failed_to_apply_current_color_history, true)
            }
            true
        }
        R.id.action_new_color_library -> {
            ViewUtils.showToast(this, R.string.text_under_development)
            true
        }
        R.id.action_import_color_library -> {
            ViewUtils.showToast(this, R.string.text_under_development)
            true
        }
        R.id.action_clone_color_library -> {
            ViewUtils.showToast(this, R.string.text_under_development)
            true
        }
        R.id.action_locate_current_theme_color -> {
            locateCurrentThemeColor()
            true
        }
        R.id.action_toggle_color_select_layout -> {
            isLegacyLayout = true
            startActivity(this, isToggled = true)
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