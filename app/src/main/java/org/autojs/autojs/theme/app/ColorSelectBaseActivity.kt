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
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import androidx.recyclerview.widget.ThemeColorRecyclerView
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.appbar.AppBarLayout
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.codetail.widget.RevealFrameLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.annotation.ReservedForCompatibility
import org.autojs.autojs.core.image.ColorItems
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.app.ColorEntities.ColorHistory
import org.autojs.autojs.theme.app.ColorEntities.ColorInfo
import org.autojs.autojs.theme.app.ColorEntities.PaletteHistory
import org.autojs.autojs.theme.app.ColorHistoryItemViewHolder.Companion.ColorHistoryItem
import org.autojs.autojs.theme.app.ColorInfoDialogManager.CustomColorInfoDialogNeutral.NeutralButtonCallback
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_DEFAULT
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_MATERIAL
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_PALETTE
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.PresetColorItem
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.PresetColorLibrary
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.presetColorLibraries
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.main.drawer.DrawerFragment.Companion.Event.ThemeColorLayoutSwitchedEvent
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.onceGlobalLayout
import org.autojs.autojs.util.ViewUtils.setColorsByColorLuminance
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByColorLuminance
import org.autojs.autojs.util.ViewUtils.setNavigationIconColorByColorLuminance
import org.autojs.autojs.util.ViewUtils.setTitlesTextColorByColorLuminance
import org.autojs.autojs6.R
import org.autojs.autojs6.R.string.text_search_color
import org.greenrobot.eventbus.EventBus
import java.util.*
import kotlin.math.absoluteValue
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
    protected var isForciblyEnableAppBarColorTransition = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeChangeNotifier.themeChanged.observe(this) {
            updateAppBarColorContent(ThemeColorManager.colorPrimary)
        }

        mTitle = intent.getStringExtra("title") ?: getString(R.string.text_theme_color)
        currentColor = intent.getIntExtra("currentColor", ThemeColorManager.colorPrimary)

        if (intent.getBooleanExtra("isToggled", false)) {
            EventBus.getDefault().post(object : ThemeColorLayoutSwitchedEvent {})
        }
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

    private fun setUpSearchMenu(searchView: View?, onQueryTextSimpleListener: (text: String?) -> Unit): SearchView? = when (searchView) {
        !is SearchView -> null
        else -> searchView.also {
            mSearchView = it
            it.queryHint = it.context.getString(text_search_color)
            it.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?) = true.also { onQueryTextSimpleListener(query) }
                override fun onQueryTextChange(newText: String?) = true.also { onQueryTextSimpleListener(newText) }
            })
        }
    }

    protected open fun getSubtitle(withHexSuffix: Boolean = true): String? = null

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
        updateSubtitle(mToolbar)
        if (isForciblyEnableAppBarColorTransition || hasWindowFocus()) {
            isForciblyEnableAppBarColorTransition = false
            ViewAnimationUtils.createCircularReveal(
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
    }

    protected fun updateSubtitle(toolbar: Toolbar) {
        toolbar.post { toolbar.subtitle = getSubtitle() }
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
        val targetLibraryId = Pref.getInt(KEY_SELECTED_COLOR_LIBRARY_ID, COLOR_LIBRARY_ID_DEFAULT)
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
            val targetLibraryIdHexString = "0x${targetLibraryId.absoluteValue.toString(16)}".let {
                if (targetLibraryId < 0) "-$it" else it
            }
            MaterialDialog.Builder(this)
                .title(R.string.text_failed_to_locate)
                .content(
                    getString(R.string.content_failed_to_locate_library_for_theme_color) + "\n" +
                            "\n" +
                            "Target library ID: $targetLibraryIdHexString" + "\n" +
                            "Color library IDs: [ ${presetColorLibraries.sortedBy { it.id }.joinToString(", ") { "0x${it.id.toString(16)}" }} ]"
                )
                .negativeText(R.string.dialog_button_dismiss)
                .negativeColorRes(R.color.dialog_button_default)
                .show()
            return null
        }
        val defaultTargetItemIndex = when {
            targetLibraryId == COLOR_LIBRARY_ID_DEFAULT -> {
                targetLibrary.colors.find { getColor(it.colorRes) == ThemeColorManager.colorPrimary }?.itemId ?: SELECT_NONE
            }
            else -> SELECT_NONE
        }
        val targetItemIndex = Pref.getInt(KEY_SELECTED_COLOR_ITEM_ID, defaultTargetItemIndex)
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

    protected fun showColorPicker(initialColor: Int? = null) {
        ColorPickerDialog.newBuilder()
            .setUseLegacyMode(isLegacyLayout)
            .setAllowCustom(true)
            .setAllowPresets(!isLegacyLayout)
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setShowAlphaSlider(false)
            .setDialogTitle(R.string.dialog_title_color_palette)
            .setColor(initialColor ?: customColor)
            .setOldColorPanelOnClickListener { v, dialog -> showColorDetails(v.tag as? Int, parentDialog = dialog) }
            .setNewColorPanelOnClickListener { v, dialog -> showColorDetails(v.tag as? Int, parentDialog = dialog) }
            .setColorHistoriesHandler { showColorPickerHistories(it) }
            .create()
            .setColorPickerDialogListener { _, color: Int ->
                onColorPickerConfirmed(color)
            }
            .show(supportFragmentManager, "ColorPickerTagForColorLibraries")
    }

    protected fun onColorPickerConfirmed(color: Int) {
        savePrefsForLegacyPalette(color)
        savePrefsForLibraries(COLOR_LIBRARY_ID_PALETTE, 0)

        saveDatabaseForPaletteHistories(lifecycleScope, applicationContext, color)

        isForciblyEnableAppBarColorTransition = true

        ThemeColorManager.setThemeColor(color)
        ThemeChangeNotifier.notifyThemeChanged()
    }

    private fun showColorPickerHistories(colorPickerDialog: ColorPickerDialog) {
        lifecycleScope.launch {
            var historiesDialog: MaterialDialog? = null
            val dao = withContext(Dispatchers.IO) {
                ColorHistoryDatabase.getInstance(applicationContext).paletteHistoryDao()
            }
            when {
                withContext(Dispatchers.IO) { dao.hasData() } -> {
                    val adapter = PaletteHistoryItemAdapter(emptyList()) { selectedColor ->
                        historiesDialog?.dismiss()
                        colorPickerDialog.onHistorySelected(selectedColor)
                    }
                    launch(Dispatchers.Main) {
                        val context = this@ColorSelectBaseActivity
                        val recyclerView = ThemeColorRecyclerView(context).also {
                            it.adapter = adapter
                            it.layoutManager = LinearLayoutManager(context)
                            it.addItemDecoration(DividerItemDecoration(context, VERTICAL))
                        }
                        historiesDialog = MaterialDialog.Builder(context)
                            .title(R.string.text_histories)
                            .customView(recyclerView, false)
                            .neutralText(R.string.dialog_button_clear_items)
                            .neutralColorRes(R.color.dialog_button_warn)
                            .onNeutral { parentDialog, _ ->
                                MaterialDialog.Builder(context)
                                    .title(R.string.text_prompt)
                                    .content(getString(R.string.text_confirm_to_clear_histories_of, getString(R.string.text_color_palette)))
                                    .negativeText(R.string.dialog_button_cancel)
                                    .negativeColorRes(R.color.dialog_button_default)
                                    .positiveText(R.string.dialog_button_confirm)
                                    .positiveColorRes(R.color.dialog_button_caution)
                                    .onPositive { _, _ ->
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            dao.deleteAll()
                                            withContext(Dispatchers.Main) {
                                                adapter.updateData(emptyList())
                                                parentDialog.dismiss()
                                                ViewUtils.showToast(context, R.string.text_all_histories_cleared, true)
                                            }
                                        }
                                    }
                                    .show()
                            }
                            .positiveText(R.string.dialog_button_dismiss)
                            .positiveColorRes(R.color.dialog_button_default)
                            .onPositive { dialog, _ -> dialog.dismiss() }
                            .autoDismiss(false)
                            .show()
                    }
                    val histories = withContext(Dispatchers.IO) {
                        dao.getAll().sortedByDescending { it.lastUsedTime }
                    }
                    launch(Dispatchers.Main) {
                        adapter.updateData(histories)
                    }
                }
                else -> withContext(Dispatchers.Main) {
                    MaterialDialog.Builder(this@ColorSelectBaseActivity)
                        .title(R.string.text_histories)
                        .content(R.string.text_no_histories)
                        .positiveText(R.string.dialog_button_dismiss)
                        .positiveColorRes(R.color.dialog_button_default)
                        .show()
                }
            }
        }
    }

    protected fun showColorHistories(libraryId: Int? = null, onHistorySelected: (selectedHistoryItem: ColorHistoryItem) -> Unit) {

        val isShowAllHistories = libraryId == null

        lifecycleScope.launch {
            var historiesDialog: MaterialDialog? = null
            var paletteHistoryDao: PaletteHistoryDao? = null
            val colorHistoryDao = withContext(Dispatchers.IO) {
                ColorHistoryDatabase.getInstance(applicationContext).colorHistoryDao()
            }
            val hasData = withContext(Dispatchers.IO) {
                when {
                    libraryId != null -> colorHistoryDao.hasDataByLibraryId(libraryId.toLong())
                    else -> colorHistoryDao.hasData()
                }
            }
            if (!hasData) {
                withContext(Dispatchers.Main) {
                    MaterialDialog.Builder(this@ColorSelectBaseActivity)
                        .title(R.string.text_histories)
                        .content(R.string.text_no_histories)
                        .positiveText(R.string.dialog_button_dismiss)
                        .positiveColorRes(R.color.dialog_button_default)
                        .show()
                }
                return@launch
            }
            val adapter = ColorHistoryItemAdapter(emptyList()) { selectedHistoryItem ->
                historiesDialog?.dismiss()
                onHistorySelected(selectedHistoryItem)
            }
            launch(Dispatchers.Main) {
                val context = this@ColorSelectBaseActivity
                val recyclerView = ThemeColorRecyclerView(context).also {
                    it.adapter = adapter
                    it.layoutManager = LinearLayoutManager(context)
                    it.addItemDecoration(DividerItemDecoration(context, VERTICAL))
                }
                historiesDialog = MaterialDialog.Builder(context)
                    .title(R.string.text_histories)
                    .customView(recyclerView, false)
                    .neutralText(R.string.dialog_button_clear_items)
                    .neutralColorRes(R.color.dialog_button_warn)
                    .onNeutral { parentDialog, _ ->
                        MaterialDialog.Builder(context)
                            .title(R.string.text_prompt)
                            .apply {
                                when {
                                    isShowAllHistories -> {
                                        content(getString(R.string.text_confirm_to_clear_histories_of_all_color_libraries))
                                    }
                                    else -> presetColorLibraries.find { it.id == libraryId }?.let {
                                        content(getString(R.string.text_confirm_to_clear_histories_of, getString(it.nameRes)))
                                    } ?: content(getString(R.string.text_confirm_to_clear_all_histories))
                                }
                            }
                            .negativeText(R.string.dialog_button_cancel)
                            .negativeColorRes(R.color.dialog_button_default)
                            .positiveText(R.string.dialog_button_confirm)
                            .positiveColorRes(R.color.dialog_button_caution)
                            .onPositive { _, _ ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    if (isShowAllHistories) {
                                        if (paletteHistoryDao == null) {
                                            paletteHistoryDao = withContext(Dispatchers.IO) {
                                                ColorHistoryDatabase.getInstance(applicationContext).paletteHistoryDao()
                                            }
                                        }
                                        paletteHistoryDao!!.deleteAll()
                                        colorHistoryDao.deleteAll()
                                    } else {
                                        colorHistoryDao.deleteAllByLibraryId(libraryId.toLong())
                                    }
                                    withContext(Dispatchers.Main) {
                                        adapter.updateData(emptyList())
                                        parentDialog.dismiss()
                                        ViewUtils.showToast(context, R.string.text_all_histories_cleared, true)
                                    }
                                }
                            }
                            .show()
                    }
                    .positiveText(R.string.dialog_button_dismiss)
                    .positiveColorRes(R.color.dialog_button_default)
                    .onPositive { dialog, _ -> dialog.dismiss() }
                    .autoDismiss(false)
                    .show()
            }

            val colorHistories = withContext(Dispatchers.IO) {
                when {
                    isShowAllHistories -> colorHistoryDao.getAll()
                    else -> colorHistoryDao.getAllByLibraryId(libraryId.toLong())
                }
            }

            val historyItems = colorHistories.mapNotNull { history ->
                var colorInt: Int? = null
                var colorName: String? = null
                var colorLibraryIdentifier: String? = null

                val presetColorLibrary = presetColorLibraries.find { it.id == history.libraryId }

                if (presetColorLibrary != null) {
                    presetColorLibrary.colors.find { it.itemId == history.itemId }?.let { item ->
                        colorInt = getColor(item.colorRes)
                        colorName = getString(item.nameRes)
                        if (isShowAllHistories) {
                            colorLibraryIdentifier = getString(presetColorLibrary.identifierRes)
                        }
                    }
                } else {
                    // TODO by SuperMonster003 on Apr 8, 2025.
                    //  ! Check out new color lib data (imported/created/cloned).
                    //  ! zh-CN: 检查新建颜色库数据 (导入/创建/克隆).
                }

                if (colorInt != null) {
                    return@mapNotNull ColorHistoryItem(history.lastUsedTime, colorInt!!, colorName, colorLibraryIdentifier)
                }
                // TODO by SuperMonster003 on Apr 8 ,2025.
                //  ! Obsolete data should be removed from database.
                //  ! Data will not be removed so far as color lib creation has not been completed yet.
                //  ! zh-CN: 孤立数据应从数据库中移除, 但目前暂未完成颜色库建立相关功能, 此处暂时不做数据移除处理.
                return@mapNotNull null
            }.toMutableList()

            if (isShowAllHistories) {
                val paletteIdentifier = getString(R.string.color_library_identifier_palette)
                if (paletteHistoryDao == null) {
                    paletteHistoryDao = withContext(Dispatchers.IO) {
                        ColorHistoryDatabase.getInstance(applicationContext).paletteHistoryDao()
                    }
                }
                historyItems += paletteHistoryDao.getAll().map { history ->
                    ColorHistoryItem(
                        lastUsedTime = history.lastUsedTime,
                        colorInt = history.colorInfo.colorInt,
                        colorName = null,
                        colorLibraryIdentifier = paletteIdentifier
                    )
                }
            }

            launch(Dispatchers.Main) {
                adapter.updateData(historyItems.sortedByDescending { it.lastUsedTime })
            }
        }
    }

    protected fun showColorDetails(color: Int?, title: String? = null, parentDialog: DialogFragment? = null) {
        val customNeutral = ColorInfoDialogManager.CustomColorInfoDialogNeutral(
            textRes = R.string.dialog_button_use_palette,
            colorRes = R.color.dialog_button_hint,
            onNeutralCallback = object : NeutralButtonCallback {
                override fun onClick(dialog: MaterialDialog, which: DialogAction, color: Int) {
                    parentDialog?.dismiss()
                    dialog.dismiss()
                    showColorPicker(color)
                }
            }
        )
        ColorInfoDialogManager.showColorInfoDialog(this, color, title, customNeutral)
    }

    protected fun showThemeColorDetails() {
        showColorDetails(ThemeColorManager.colorPrimary, getSubtitle(false))
    }

    protected fun showColorSearchHelp() {
        Intent(this, ColorSearchHelpActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .let { startActivity(it) }
    }

    protected fun filterColorsFromColorItems(query: String?, colorItems: List<PresetColorItem>, colorItemAdapter: ColorItemAdapter) {
        mSearchJob?.cancel()
        mSearchJob = lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                if (query.isNullOrBlank()) return@withContext colorItems
                val searchTerm = query.trimStart()
                val cleanedSearchTerm = searchTerm.drop(1)
                val normalizedQuery = normalizeForMatching(searchTerm)
                colorItems.filter { colorItem ->
                    val colorHex = String.format("#%06X", 0xFFFFFF and getColor(colorItem.colorRes))
                    val enName by lazy { getLocalizedString(this@ColorSelectBaseActivity, colorItem.nameRes, Locale("en")) }
                    val localizedName by lazy { getString(colorItem.nameRes) }
                    when {
                        searchTerm.startsWith("#") -> {
                            searchMatch(cleanedSearchTerm, colorHex) {
                                colorHex.contains(searchTerm, ignoreCase = true)
                            }
                        }
                        searchTerm.startsWith("$") -> {
                            searchMatch(cleanedSearchTerm, enName) {
                                normalizeForMatching(enName).contains(normalizedQuery, ignoreCase = true)
                            }
                        }
                        else -> {
                            searchMatch(searchTerm, localizedName) {
                                normalizeForMatching(localizedName).contains(normalizedQuery, ignoreCase = true)
                            }
                        }
                    }
                }
            }.let { filteredResult -> colorItemAdapter.updateData(filteredResult) }
        }
    }

    fun searchMatch(search: String, text: String, nonRegexMatch: () -> Boolean) = when {
        isRegexSearch(search) -> {
            compileRegexOrNull(extractPatternFromQuery(search))?.containsMatchIn(text) == true
        }
        else -> nonRegexMatch()
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
        const val KEY_SELECTED_COLOR_ITEM_ID = "SELECTED_COLOR_LIBRARY_ITEM_ID"

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
                add(customColorPosition, R.color.custom_color_default to R.string.color_library_identifier_custom)
                add(defaultColorPosition, R.color.theme_color_default to R.string.color_library_identifier_default_colors)
                addAll(ColorItems.MATERIAL_COLORS)
            }
        }

        @JvmStatic
        fun startActivity(context: Context, isToggled: Boolean = false) {
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
                putExtra("isToggled", isToggled)
            }
            if (context is ColorSelectBaseActivity) {
                intent.putExtra("currentColor", context.currentColor)
                context.finish()
            }
            context.startActivity(intent)
        }

        @JvmStatic
        fun getCurrentColorSummary(context: Context, withIdentifierPrefix: Boolean = true, withHexSuffix: Boolean = true): String = when {
            isLegacyLayout -> when (val index = Pref.getInt(KEY_LEGACY_SELECTED_COLOR_INDEX, defaultColorPosition)) {
                SELECT_NONE -> {
                    val colorInt = ThemeColorManager.colorPrimary
                    ColorUtils.toString(colorInt)
                }
                else -> {
                    val colorItem = colorItemsLegacy[index]
                    val (colorRes, nameRes) = colorItem
                    val name = context.getString(nameRes)
                    when {
                        !withHexSuffix -> name
                        else -> {
                            val colorInt = when (index) {
                                customColorPosition -> Pref.getInt(KEY_CUSTOM_COLOR, context.getColor(R.color.custom_color_default))
                                else -> context.getColor(colorRes)
                            }
                            "$name [${ColorUtils.toString(colorInt)}]"
                        }
                    }
                }
            }
            else -> {
                var identifier: String? = null
                var colorName: String? = null
                val colorInt = ThemeColorManager.colorPrimary
                val libraryId = Pref.getInt(KEY_SELECTED_COLOR_LIBRARY_ID, SELECT_NONE)
                val libraryItemId = Pref.getInt(KEY_SELECTED_COLOR_ITEM_ID, SELECT_NONE)
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
                        // identifier = when {
                        //     lib.isCreated -> {
                        //         lib.identifierString?.let { idStr ->
                        //             when {
                        //                 idStr.estimateVisualWidth <= 10 -> idStr
                        //                 else -> {
                        //                     var newStr = idStr
                        //                     while (newStr.estimateVisualWidth > 8) {
                        //                         newStr = newStr.dropLast(1)
                        //                     }
                        //                     "$newStr..."
                        //                 }
                        //             }
                        //         } ?: lib.titleString?.let { titleStr ->
                        //             when {
                        //                 titleStr.estimateVisualWidth <= 10 -> titleStr
                        //                 else -> {
                        //                     var newStr = titleStr
                        //                     while (newStr.estimateVisualWidth > 8) {
                        //                         newStr = newStr.dropLast(1)
                        //                     }
                        //                     "$newStr..."
                        //                 }
                        //             }
                        //         } ?: context.getString(R.string.color_library_identifier_created)
                        //         context.getString(R.string.color_library_identifier_created)
                        //     }
                        //     else -> context.getString(lib.identifierRes)
                        // }
                        identifier = context.getString(lib.identifierRes)
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
                val colorString = ColorUtils.toString(colorInt)
                when {
                    identifier != null && colorName != null -> when {
                        withIdentifierPrefix -> when {
                            withHexSuffix -> "$identifier | $colorName [$colorString]"
                            else -> "$identifier | $colorName"
                        }
                        else -> when {
                            withHexSuffix -> "$colorName [$colorString]"
                            else -> colorName
                        }
                    }
                    identifier != null -> when {
                        withIdentifierPrefix -> "$identifier | $colorString"
                        else -> colorString
                    }
                    colorName != null -> when {
                        withHexSuffix -> "$colorName [$colorString]"
                        else -> colorName
                    }
                    else -> colorString
                }
            }
        }

        @JvmStatic
        fun saveDatabaseForPaletteHistories(dispatcherScope: CoroutineScope? = null, applicationContext: Context, color: Int) {
            saveHistories(dispatcherScope) {
                var itemId by Delegates.notNull<Int>()
                savePaletteHistories(applicationContext, color).also { itemId = it }
                saveColorHistories(applicationContext, COLOR_LIBRARY_ID_PALETTE, itemId)
            }
        }

        @JvmStatic
        fun saveDatabaseForColorHistories(dispatcherScope: CoroutineScope? = null, applicationContext: Context, libraryId: Int, itemId: Int) {
            saveHistories(dispatcherScope) {
                saveColorHistories(applicationContext, libraryId, itemId)
            }
        }

        private fun saveHistories(
            dispatcherScope: CoroutineScope? = null,
            saveJob: suspend CoroutineScope.() -> Unit,
        ) {
            when (dispatcherScope) {
                null -> CoroutineScope(Dispatchers.IO).launch(block = saveJob)
                else -> dispatcherScope.launch(context = Dispatchers.IO, block = saveJob)
            }
        }

        private suspend fun savePaletteHistories(applicationContext: Context, color: Int): Int {
            val dao = ColorHistoryDatabase.getInstance(applicationContext).paletteHistoryDao()
            val colorInfo = ColorInfo(ColorUtils.toHex(color, 6))
            return dao.upsert(PaletteHistory(colorInfo = colorInfo).apply { setLastUsedTimeCurrent() }).toInt()
        }

        private suspend fun saveColorHistories(applicationContext: Context, libraryId: Int, itemId: Int) {
            val dao = ColorHistoryDatabase.getInstance(applicationContext).colorHistoryDao()
            dao.upsert(ColorHistory(libraryId = libraryId, itemId = itemId).apply { setLastUsedTimeCurrent() })
        }

        protected fun savePrefsForLegacyPalette(color: Int) {
            Pref.putInt(KEY_CUSTOM_COLOR, color or -0x1000000)
            Pref.putInt(KEY_LEGACY_SELECTED_COLOR_INDEX, customColorPosition)
        }

        fun savePrefsForLegacy(context: Context, colorItem: PresetColorItem) {
            when (colorItem.libraryId) {
                COLOR_LIBRARY_ID_DEFAULT -> {
                    if (context.getColor(colorItem.colorRes) == context.getColor(R.color.theme_color_default)) {
                        Pref.putInt(KEY_LEGACY_SELECTED_COLOR_INDEX, defaultColorPosition)
                    } else {
                        Pref.putInt(KEY_LEGACY_SELECTED_COLOR_INDEX, SELECT_NONE)
                    }
                }
                COLOR_LIBRARY_ID_MATERIAL -> {
                    val index = colorItem.itemId
                    if (index !in ColorItems.MATERIAL_COLORS.indices) {
                        Pref.putInt(KEY_LEGACY_SELECTED_COLOR_INDEX, SELECT_NONE)
                    } else {
                        val fixedIndex = maxOf(
                            defaultColorPosition,
                            customColorPosition,
                        ) + 1 + index
                        Pref.putInt(KEY_LEGACY_SELECTED_COLOR_INDEX, fixedIndex)
                    }
                }
                else -> {
                    Pref.putInt(KEY_LEGACY_SELECTED_COLOR_INDEX, SELECT_NONE)
                }
            }
        }

        fun savePrefsForLibraries(colorItem: PresetColorItem) {
            savePrefsForLibraries(colorItem.libraryId, colorItem.itemId)
        }

        fun savePrefsForLibraries(libraryId: Int, itemId: Int) {
            Pref.putInt(KEY_SELECTED_COLOR_LIBRARY_ID, libraryId)
            Pref.putInt(KEY_SELECTED_COLOR_ITEM_ID, itemId)
        }

    }

}