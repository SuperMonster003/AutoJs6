package org.autojs.autojs.theme.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewAnimationUtils
import androidx.annotation.StringRes
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.appbar.AppBarLayout
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.codetail.widget.RevealFrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.annotation.ReservedForCompatibility
import org.autojs.autojs.core.image.ColorItems
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_PALETTE
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.PresetColorItem
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.PresetColorLibrary
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.presetColorLibraries
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.onceGlobalLayout
import org.autojs.autojs.util.ViewUtils.setColorsByColorLuminance
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByColorLuminance
import org.autojs.autojs.util.ViewUtils.setNavigationIconColorByColorLuminance
import org.autojs.autojs.util.ViewUtils.setTitlesTextColorByColorLuminance
import org.autojs.autojs6.R
import java.util.*
import kotlin.math.hypot
import kotlin.properties.Delegates

/**
 * Created by Stardust on Mar 5, 2017.
 * Modified by SuperMonster003 as of Sep 22, 2022.
 * Transformed by SuperMonster003 on Sep 22, 2022.
 */
abstract class ColorSelectBaseActivity : BaseActivity() {

    override val handleStatusBarThemeColorAutomatically = false

    private lateinit var mToolbar: Toolbar
    private lateinit var mAppBarLayout: AppBarLayout
    private lateinit var mAppBarContainer: RevealFrameLayout

    private lateinit var mTitle: String

    private var mSearchJob: Job? = null
    private var mSearchView: SearchView? = null

    private val customColor: Int
        get() = Pref.getInt(KEY_CUSTOM_COLOR, getColor(R.color.custom_color_default))

    protected var currentColor by Delegates.notNull<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mTitle = intent.getStringExtra("title") ?: getString(R.string.text_theme_color)
        currentColor = intent.getIntExtra("currentColor", ThemeColorManager.colorPrimary)
    }

    protected fun setUpToolbar(toolbar: Toolbar) {
        toolbar.apply {
            title = mTitle
            subtitle = this@ColorSelectBaseActivity.getSubtitle()
            setSupportActionBar(this)
            setNavigationOnClickListener { finish() }
            setTitleTextAppearance(this@ColorSelectBaseActivity, R.style.TextAppearanceMainTitle)
            setSubtitleTextAppearance(this@ColorSelectBaseActivity, R.style.TextAppearanceMainSubtitle)
            mToolbar = this
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    protected fun setUpSearchMenu(menu: Menu, onQueryTextSimpleListener: (text: String?) -> Unit, onMenuItemActionExpand: (item: MenuItem) -> Unit = {}, onMenuItemActionCollapse: (item: MenuItem) -> Unit = {}): SearchView? {
        val menuItem = menu.findItem(R.id.action_search_color) ?: return null
        menuItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem) = true.also {
                mToolbar.onceGlobalLayout { updateToolbarColors() }
                onMenuItemActionExpand(item)
            }

            override fun onMenuItemActionCollapse(item: MenuItem) = true.also {
                onQueryTextSimpleListener(null)
                onMenuItemActionCollapse(item)
            }
        })
        return setUpSearchMenu(menuItem.actionView, onQueryTextSimpleListener)
    }

    private fun setUpSearchMenu(searchView: View?, onQueryTextSimpleListener: (text: String?) -> Unit): SearchView? {
        return setUpSearchMenu(searchView, object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true.also { onQueryTextSimpleListener(query) }
            override fun onQueryTextChange(newText: String?) = true.also { onQueryTextSimpleListener(newText) }
        })
    }

    protected fun setUpSearchMenu(menu: Menu, onQueryTextListener: SearchView.OnQueryTextListener? = null, onMenuItemActionExpand: (item: MenuItem) -> Unit = {}, onMenuItemActionCollapse: (item: MenuItem) -> Unit = {}): SearchView? {
        val menuItem = menu.findItem(R.id.action_search_color) ?: return null
        menuItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem) = true.also { onMenuItemActionExpand(item) }
            override fun onMenuItemActionCollapse(item: MenuItem) = true.also { onMenuItemActionCollapse(item) }
        })
        return setUpSearchMenu(menuItem.actionView, onQueryTextListener)
    }

    private fun setUpSearchMenu(searchView: View?, onQueryTextListener: SearchView.OnQueryTextListener? = null): SearchView? {
        if (searchView !is SearchView) return null
        return searchView.apply {
            mSearchView = this
            queryHint = context.getString(R.string.text_search_color)
            setOnQueryTextListener(onQueryTextListener)
        }
    }

    protected open fun getSubtitle(): String? = null

    override fun initThemeColors() {
        super.initThemeColors()
        updateToolbarColors()
        updateSearchViewColors()
        ViewUtils.setStatusBarAppearanceLightByColorLuminance(this, currentColor)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateToolbarColors()
        updateSearchViewColors()
        return super.onCreateOptionsMenu(menu)
    }

    private fun updateToolbarColors() {
        mToolbar.setMenuIconsColorByColorLuminance(this, currentColor)
        mToolbar.setNavigationIconColorByColorLuminance(this, currentColor)
        mToolbar.setTitlesTextColorByColorLuminance(this, currentColor)
    }

    private fun updateSearchViewColors() {
        mSearchView?.setColorsByColorLuminance(this, currentColor)
    }

    protected fun updateAppBarColorContent(colorTo: Int) {
        setColors(colorTo)
        updateSubtitle()
        if (hasWindowFocus()) ViewAnimationUtils.createCircularReveal(
            /* view = */ mAppBarLayout,
            /* centerX = */ mAppBarLayout.left,
            /* centerY = */ mAppBarLayout.bottom,
            /* startRadius = */ 0f,
            /* endRadius = */ hypot(mAppBarLayout.width.toDouble(), mAppBarLayout.height.toDouble()).toFloat(),
        ).apply {
            duration = 500L
            start()
        }
    }

    protected fun updateSubtitle() {
        mToolbar.post { mToolbar.subtitle = getSubtitle() }
    }

    private fun setColors(colorTo: Int) {
        mAppBarContainer.setBackgroundColor(currentColor)
        mAppBarLayout.setBackgroundColor(colorTo)
        currentColor = colorTo
        initThemeColors()
    }

    protected fun setUpAppBar(appBar: AppBarLayout, appBarContainer: RevealFrameLayout) {
        mAppBarLayout = appBar.apply { setBackgroundColor(currentColor) }
        mAppBarContainer = appBarContainer
    }

    protected fun checkAndGetTargetInfoForThemeColorLocate(): TargetInfoForLocate? {
        val targetLibraryId = Pref.getInt(KEY_SELECTED_COLOR_LIBRARY_ID, -1)
        if (targetLibraryId == COLOR_LIBRARY_ID_PALETTE) {
            MaterialDialog.Builder(this)
                .title(R.string.text_prompt)
                .content(R.string.content_current_theme_color_configured_by_palette, ColorUtils.toHex(currentColor, 6))
                .neutralText(R.string.dialog_button_open_color_palette)
                .neutralColorRes(R.color.dialog_button_hint)
                .onNeutral { _, _ -> showColorPicker() }
                .negativeText(R.string.dialog_button_dismiss)
                .negativeColorRes(R.color.dialog_button_default)
                .show()
            return null
        }
        val targetLibrary = presetColorLibraries.find { it.id == targetLibraryId }
        if (targetLibrary == null) {
            MaterialDialog.Builder(this)
                .title(R.string.text_failed_to_locate)
                .content(
                    getString(R.string.content_failed_to_locate_library_for_theme_color) + "\n" +
                            "\n" +
                            "Target library ID: 0x${targetLibraryId.toString(16)}" + "\n" +
                            "Color library IDs: [ ${presetColorLibraries.sortedBy { it.id }.joinToString(", ") { "0x${it.id.toString(16)}" }} ]"
                )
                .negativeText(R.string.dialog_button_dismiss)
                .negativeColorRes(R.color.dialog_button_default)
                .show()
            return null
        }
        val targetItemIndex = Pref.getInt(KEY_SELECTED_COLOR_LIBRARY_ITEM_ID, -1)
        if (targetItemIndex !in targetLibrary.colors.indices) {
            MaterialDialog.Builder(this)
                .title(R.string.text_failed_to_locate)
                .content(
                    getString(R.string.content_failed_to_determine_index_of_theme_color_item) + "\n" +
                            "\n" +
                            "Target item index: $targetItemIndex" + "\n" +
                            "Item indicies: [ 0..${targetLibrary.colors.size - 1} ]"
                )
                .negativeText(R.string.dialog_button_dismiss)
                .negativeColorRes(R.color.dialog_button_default)
                .show()
            return null
        }
        return TargetInfoForLocate(targetLibraryId, targetItemIndex)
    }

    protected fun showColorPicker() {
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

                updateAppBarColorContent(color)

                ThemeColorManager.setThemeColor(color)
                ThemeChangeNotifier.notifyThemeChanged()
            }
            .show(supportFragmentManager, "ColorPickerTagForColorLibraries")
    }

    private fun savePrefsForLibraries() {
        Pref.putInt(KEY_SELECTED_COLOR_LIBRARY_ID, COLOR_LIBRARY_ID_PALETTE)
        Pref.putInt(KEY_SELECTED_COLOR_LIBRARY_ITEM_ID, 0)
    }

    private fun savePrefsForLegacy(color: Int) {
        Pref.putInt(KEY_CUSTOM_COLOR, color or -0x1000000)
        Pref.putInt(KEY_LEGACY_SELECTED_COLOR_INDEX, customColorPosition)
    }

    protected fun filterColorsFromColorItems(query: String?, colorItems: List<PresetColorItem>, colorItemAdapter: ColorItemAdapter) {
        mSearchJob?.cancel()
        mSearchJob = lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                if (query.isNullOrBlank()) return@withContext colorItems
                val normalizedQuery = normalizeForMatching(query)
                colorItems.filter { colorItem ->
                    val colorHex = String.format("#%06X", 0xFFFFFF and getColor(colorItem.colorRes))
                    if (query.startsWith("#")) {
                        if (isRegexSearch(query.drop(1))) {
                            val regex = compileRegexOrNull(extractPatternFromQuery(query.drop(1))) ?: return@filter false
                            return@filter regex.containsMatchIn(colorHex)
                        }
                        return@filter colorHex.contains(query, ignoreCase = true)
                    }
                    val localName = getString(colorItem.nameRes)
                    val enName = getLocalizedString(this@ColorSelectBaseActivity, colorItem.nameRes, Locale("en"))
                    if (isRegexSearch(query)) {
                        val regex = compileRegexOrNull(extractPatternFromQuery(query)) ?: return@filter false
                        return@filter regex.containsMatchIn(localName) || regex.containsMatchIn(enName)
                    }
                    return@filter normalizeForMatching(localName).contains(normalizedQuery, ignoreCase = true)
                            || normalizeForMatching(enName).contains(normalizedQuery, ignoreCase = true)
                }
            }.let { filteredResult -> colorItemAdapter.updateData(filteredResult) }
        }
    }

    private fun normalizeForMatching(input: String): String {
        // 去掉所有非字母或数字字符 (例如空格/括号/斜线等).
        // \p{L} 表示任何语言的字母字符, \p{N} 表示数字.
        return input.replace("[^\\p{L}\\p{N}]+".toRegex(), "")
    }

    private fun getLocalizedString(context: Context, @StringRes resId: Int, locale: Locale): String {
        val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
        val localizedContext = context.createConfigurationContext(config)
        return localizedContext.getString(resId)
    }

    private fun isRegexSearch(query: String?): Boolean {
        if (query.isNullOrBlank()) return false
        return query.length > 2 && query.startsWith("/") && query.endsWith("/")
    }

    private fun extractPatternFromQuery(query: String): String {
        // 假设已检查过 isRegexSearch() == true
        // 去掉最前和最后的斜杠
        return query.substring(1, query.length - 1)
    }

    private fun compileRegexOrNull(pattern: String): Regex? {
        return runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()
    }

    interface OnItemClickListener {
        fun onItemClick(v: View?, position: Int)
    }

    data class TargetInfoForLocate(val libraryId: Int, val libraryItemId: Int)

    companion object {

        const val SELECT_NONE = -1

        @ReservedForCompatibility
        const val KEY_CUSTOM_COLOR = "org.autojs.autojs.theme.app.ColorSettingRecyclerView.COLOR_SETTING_CUSTOM_COLOR"

        @ReservedForCompatibility
        const val KEY_LEGACY_SELECTED_COLOR_INDEX = "org.autojs.autojs.theme.app.ColorSettingRecyclerView.SELECTED_COLOR_INDEX"

        const val KEY_SELECTED_COLOR_LIBRARY_ID = "SELECTED_COLOR_LIBRARY_ID"
        const val KEY_SELECTED_COLOR_LIBRARY_ITEM_ID = "SELECTED_COLOR_LIBRARY_ITEM_ID"

        const val INTENT_IDENTIFIER_LIBRARY_ID = "LIBRARY_ID"
        const val INTENT_IDENTIFIER_COLOR_ITEM_ID_SCROLL_TO = "INTENT_IDENTIFIER_COLOR_ITEM_ID_SCROLL_TO"

        val customColorPosition: Int
            get() = /* return mColors.size() - 1; */ 0

        val defaultColorPosition: Int
            get() = customColorPosition + 1

        var isLegacyLayout: Boolean
            get() = Pref.getBoolean(R.string.key_color_select_activity_legacy_layout, false)
            set(value) = Pref.putBoolean(R.string.key_color_select_activity_legacy_layout, value)

        val colorItemsLegacy by lazy {
            mutableListOf<Pair</* colorRes */ Int, /* nameRes */ Int>>().apply {
                add(customColorPosition, R.color.custom_color_default to R.string.mt_custom)
                add(defaultColorPosition, R.color.theme_color_default to R.string.theme_color_default)
                addAll(ColorItems.MATERIAL_COLORS)
            }
        }

        @JvmStatic
        fun startActivity(context: Context) {
            val cls = when (isLegacyLayout) {
                true -> ColorSelectActivity::class.java
                else -> ColorLibrariesActivity::class.java
            }
            val titleRes = when (isLegacyLayout) {
                true -> R.string.mt_color_picker_title
                else -> R.string.text_theme_color
            }
            val intent = Intent(context, cls).apply {
                putExtra("title", context.getString(titleRes))
            }
            if (context is ColorSelectBaseActivity) {
                intent.putExtra("currentColor", context.currentColor)
                context.finish()
            }
            context.startActivity(intent)
        }

        @JvmStatic
        fun getCurrentColorSummary(context: Context, isBrief: Boolean = false): String = when {
            isLegacyLayout -> when (val index = Pref.getInt(KEY_LEGACY_SELECTED_COLOR_INDEX, defaultColorPosition)) {
                SELECT_NONE -> {
                    val colorInt = ThemeColorManager.colorPrimary
                    ColorUtils.toString(colorInt).uppercase()
                }
                else -> {
                    val colorItem = colorItemsLegacy[index]
                    val (colorRes, nameRes) = colorItem
                    val name = context.getString(nameRes)
                    val colorInt = when (index) {
                        customColorPosition -> Pref.getInt(KEY_CUSTOM_COLOR, context.getColor(R.color.custom_color_default))
                        else -> context.getColor(colorRes)
                    }
                    "$name [${ColorUtils.toString(colorInt).uppercase()}]"
                }
            }
            else -> {
                var identifier: String? = null
                var colorName: String? = null
                val colorInt = ThemeColorManager.colorPrimary
                val libraryId = Pref.getInt(KEY_SELECTED_COLOR_LIBRARY_ID, SELECT_NONE)
                val libraryItemId = Pref.getInt(KEY_SELECTED_COLOR_LIBRARY_ITEM_ID, SELECT_NONE)
                when (libraryId) {
                    COLOR_LIBRARY_ID_PALETTE -> {
                        identifier = context.getString(R.string.color_library_identifier_palette)
                    }
                    SELECT_NONE -> {
                        val legacyIndex = Pref.getInt(KEY_LEGACY_SELECTED_COLOR_INDEX, SELECT_NONE)
                        when (legacyIndex) {
                            customColorPosition -> identifier = context.getString(R.string.color_library_identifier_palette)
                            SELECT_NONE, defaultColorPosition -> identifier = context.getString(R.string.color_library_identifier_default_colors)
                            else -> {
                                val calculatedIndex = legacyIndex - maxOf(customColorPosition, defaultColorPosition) - 1
                                if (calculatedIndex in ColorItems.MATERIAL_COLORS.indices) {
                                    val (_, nameRes) = ColorItems.MATERIAL_COLORS[calculatedIndex]
                                    identifier = context.getString(R.string.color_library_identifier_material_design_colors)
                                    colorName = context.getString(nameRes)
                                }
                            }
                        }
                    }
                    else -> presetColorLibraries.find { it.id == libraryId }?.let { lib: PresetColorLibrary ->
                        identifier = when {
                            lib.isCreated -> {
                                // lib.identifierString?.let { idStr ->
                                //     when {
                                //         idStr.estimateVisualWidth <= 10 -> idStr
                                //         else -> {
                                //             var newStr = idStr
                                //             while (newStr.estimateVisualWidth > 8) {
                                //                 newStr = newStr.dropLast(1)
                                //             }
                                //             "$newStr..."
                                //         }
                                //     }
                                // } ?: lib.titleString?.let { titleStr ->
                                //     when {
                                //         titleStr.estimateVisualWidth <= 10 -> titleStr
                                //         else -> {
                                //             var newStr = titleStr
                                //             while (newStr.estimateVisualWidth > 8) {
                                //                 newStr = newStr.dropLast(1)
                                //             }
                                //             "$newStr..."
                                //         }
                                //     }
                                // } ?: context.getString(R.string.color_library_identifier_created)
                                context.getString(R.string.color_library_identifier_created)
                            }
                            else -> context.getString(lib.identifierRes)
                        }
                        if (libraryItemId != -1) {
                            lib.colors.find { it.itemId == libraryItemId }?.let { item ->
                                // colorName = item.nameString
                                //     ?: item.nameRes.takeUnless {
                                //         it == R.string.text_unknown
                                //     }?.let { context.getString(it) }
                                colorName = context.getString(item.nameRes)
                            }
                        }
                    }
                }
                val colorString = ColorUtils.toString(colorInt).uppercase()
                when {
                    identifier != null && colorName != null -> when {
                        isBrief -> "$colorName [$colorString]"
                        else -> "$identifier | $colorName [$colorString]"
                    }
                    identifier != null -> "$identifier | $colorString"
                    colorName != null -> "$colorName [$colorString]"
                    else -> colorString
                }
            }
        }

    }

}