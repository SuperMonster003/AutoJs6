package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import org.autojs.autojs.core.image.ColorItems
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByColorLuminance
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MtActivityColorLibrariesBinding

@SuppressLint("NotifyDataSetChanged")
class ColorLibrariesActivity : ColorSelectBaseActivity() {

    private lateinit var binding: MtActivityColorLibrariesBinding

    private lateinit var mAdapter: ColorLibraryAdapter

    private val customColor: Int
        get() = Pref.getInt(KEY_CUSTOM_COLOR, getColor(R.color.custom_color_default))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeChangeNotifier.themeChanged.observe(this) {
            setColor(ThemeColorManager.colorPrimary)
            mAdapter.notifyDataSetChanged()
        }

        MtActivityColorLibrariesBinding.inflate(layoutInflater).let {
            binding = it
            setContentView(it.root)
            setUpToolbar(it.toolbar)
            setUpAppBar(it.appBar, it.appBarContainer)
        }

        binding.colorLibrariesRecyclerView.let {
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = ColorLibraryAdapter(colorLibraries) { library ->
                val intent = Intent(this, ColorLibraryActivity::class.java)
                intent.putExtra(INTENT_IDENTIFIER_LIBRARY_ID, library.id)
                startActivity(intent)
            }.also { mAdapter = it }
            it.addItemDecoration(DividerItemDecoration(this, VERTICAL))
            ViewUtils.excludePaddingClippableViewFromNavigationBar(it)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_color_libraries, menu)

        binding.toolbar.setMenuIconsColorByColorLuminance(this, currentColor)

        val searchItem = menu.findItem(R.id.action_search_color)
        val searchView = searchItem.actionView as? SearchView
        searchView?.queryHint = "搜索颜色库"
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterLibraries(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterLibraries(newText)
                return true
            }
        })
        return true
    }

    private fun filterLibraries(query: String?) {
        val filteredLibraries = when {
            query.isNullOrBlank() -> colorLibraries
            else -> colorLibraries.filter {
                val name = it.nameString ?: getString(it.nameRes)
                name.contains(query, ignoreCase = true)
            }
        }
        mAdapter.updateData(filteredLibraries)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_color_palette -> {
            showColorPicker()
            true
        }
        R.id.action_search_color -> {
            ViewUtils.showToast(this, R.string.text_under_development_title)
            true
        }
        R.id.action_new_color_library -> {
            ViewUtils.showToast(this, R.string.text_under_development_title)
            true
        }
        R.id.action_locate_current_theme_color -> {
            locateCurrentThemeColor()
            true
        }
        R.id.action_toggle_color_select_layout -> {
            isLegacyLayout = true
            startActivity(this)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showColorPicker() {
        ColorPickerDialog.newBuilder()
            .setAllowCustom(true)
            .setAllowPresets(true)
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setShowAlphaSlider(false)
            .setDialogTitle(R.string.dialog_title_color_palette)
            .setColor(customColor)
            .create()
            .setColorPickerDialogListener { dialogId: Int, color: Int ->
                savePrefsForLibraries()
                savePrefsForLegacy(color)

                binding.toolbar.post { binding.toolbar.subtitle = getCurrentColorSummary(this, true) }

                setColorWithAnimation(color)

                ThemeColorManager.setThemeColor(color)
                ThemeChangeNotifier.notifyThemeChanged()
            }
            .show(supportFragmentManager, "ColorPickerTagForColorLibraries")
    }

    private fun savePrefsForLibraries() {
        Pref.putInt(KEY_SELECTED_COLOR_LIBRARY_ID, COLOR_LIBRARY_CUSTOM_COLOR_ID)
        Pref.putInt(KEY_SELECTED_COLOR_LIBRARY_ITEM_ID, 0)
    }

    private fun savePrefsForLegacy(color: Int) {
        Pref.putInt(KEY_CUSTOM_COLOR, color or -0x1000000)
        Pref.putInt(KEY_LEGACY_SELECTED_COLOR_INDEX, customColorPosition)
    }

    private fun locateCurrentThemeColor() {
        checkAndGetTargetInfoForThemeColorLocate()?.let { target ->
            Intent(this, ColorLibraryActivity::class.java).apply {
                putExtra(INTENT_IDENTIFIER_LIBRARY_ID, target.libraryId)
                putExtra(INTENT_IDENTIFIER_COLOR_ITEM_ID_SCROLL_TO, target.libraryItemId)
            }.let { startActivity(it) }
        }
    }

    override fun getSubtitle() = getCurrentColorSummary(this)

    inner class ColorLibraryAdapter(
        private var libraries: List<ColorLibrary>,
        private val onItemClick: (ColorLibrary) -> Unit,
    ) : RecyclerView.Adapter<LibraryViewHolder>() {

        // 当上层需要搜索过滤时调用该方法更新数据
        fun updateData(newLibraries: List<ColorLibrary>) {
            libraries = newLibraries
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.mt_color_libraries_recycler_view_item, parent, false)
            return LibraryViewHolder(view)
        }

        override fun onBindViewHolder(holder: LibraryViewHolder, position: Int) {
            val library = libraries[position]
            holder.bind(library)
            holder.itemView.setOnClickListener { onItemClick(library) }
        }

        override fun getItemCount() = libraries.size

    }

    inner class LibraryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val colorItemView: ImageView = itemView.findViewById(R.id.library_item)
        private val libraryName: TextView = itemView.findViewById(R.id.name)
        private val description: TextView = itemView.findViewById(R.id.description)

        fun bind(library: ColorLibrary) {
            libraryName.text = library.nameString ?: getString(library.nameRes)

            val size = library.colors.size
            description.text = resources.getQuantityString(R.plurals.text_items_total_sum, size, size)

            val adjustedContrastColor = ColorUtils.adjustColorForContrast(getColor(R.color.window_background), ThemeColorManager.colorPrimary, 2.3)
            when {
                library.id == COLOR_LIBRARY_DEFAULT_COLORS_ID -> {
                    colorItemView.setImageResource(R.drawable.ic_color_library_default)
                }
                !library.isUserDefined -> {
                    colorItemView.setImageResource(R.drawable.ic_color_library_preset)
                }
            }
            colorItemView.imageTintList = ColorStateList.valueOf(adjustedContrastColor)
        }

    }

    companion object {

        const val COLOR_LIBRARY_CUSTOM_COLOR_ID = 0x10001

        const val COLOR_LIBRARY_DEFAULT_COLORS_ID = 0x20001
        const val COLOR_LIBRARY_MATERIAL_COLORS_ID = 0x20002
        const val COLOR_LIBRARY_ANDROID_COLORS_ID = 0x20003
        const val COLOR_LIBRARY_CSS_COLORS_ID = 0x20004
        const val COLOR_LIBRARY_WEB_COLORS_ID = 0x20005

        val colorLibraries by lazy {
            listOf(
                ColorLibrary(
                    id = COLOR_LIBRARY_DEFAULT_COLORS_ID,
                    nameRes = R.string.color_library_default_colors,
                    titleRes = R.string.color_library_title_default_colors,
                    identifierRes = R.string.color_library_identifier_default_colors,
                    colors = listOf(
                        R.color.md_teal_800 to R.string.md_teal_800,
                        R.color.md_blue_gray_800 to R.string.md_blue_gray_800,
                        R.color.window_background_light to R.string.window_background_light,
                        R.color.window_background_night to R.string.window_background_night,
                    ).mapIndexed { index, (colorRes, nameRes) ->
                        ColorItem(index, colorRes, nameRes)
                    },
                ),
                ColorLibrary(
                    id = COLOR_LIBRARY_MATERIAL_COLORS_ID,
                    nameRes = R.string.color_library_material_design_colors,
                    titleRes = R.string.color_library_title_material_design_colors,
                    identifierRes = R.string.color_library_identifier_material_design_colors,
                    colors = ColorItems.MATERIAL_COLORS.mapIndexed { index, (colorRes, nameRes) ->
                        ColorItem(index, colorRes, nameRes)
                    },
                ),
                ColorLibrary(
                    id = COLOR_LIBRARY_ANDROID_COLORS_ID,
                    nameRes = R.string.color_library_android_colors,
                    titleRes = R.string.color_library_title_android_colors,
                    identifierRes = R.string.color_library_identifier_android_colors,
                    colors = ColorItems.ANDROID_COLORS.mapIndexed { index, (colorRes, nameRes) ->
                        ColorItem(index, colorRes, nameRes)
                    },
                ),
                ColorLibrary(
                    id = COLOR_LIBRARY_CSS_COLORS_ID,
                    nameRes = R.string.color_library_css_colors,
                    titleRes = R.string.color_library_title_css_colors,
                    identifierRes = R.string.color_library_identifier_css_colors,
                    colors = ColorItems.CSS_COLORS.mapIndexed { index, (colorRes, nameRes) ->
                        ColorItem(index, colorRes, nameRes)
                    },
                ),
                ColorLibrary(
                    id = COLOR_LIBRARY_WEB_COLORS_ID,
                    nameRes = R.string.color_library_web_colors,
                    titleRes = R.string.color_library_title_web_colors,
                    identifierRes = R.string.color_library_identifier_web_colors,
                    colors = ColorItems.WEB_COLORS.mapIndexed { index, (colorRes, nameRes) ->
                        ColorItem(index, colorRes, nameRes)
                    },
                ),
            )
        }

        data class ColorItem(
            val id: Int,
            val colorRes: Int = R.color.md_black_1000,
            val nameRes: Int = R.string.text_unknown,
            val colorInt: Int? = null,
            val nameString: String? = null,
        )

        data class ColorLibrary(
            val id: Int,
            val nameRes: Int = R.string.text_color_library,
            val titleRes: Int? = null,
            val identifierRes: Int? = null,
            val nameString: String? = null,
            val titleString: String? = null,
            val colors: List<ColorItem> = emptyList(),
            var isUserDefined: Boolean = false,
        )

    }

}