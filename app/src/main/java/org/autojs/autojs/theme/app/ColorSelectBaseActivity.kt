package org.autojs.autojs.theme.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewAnimationUtils
import android.widget.LinearLayout
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
import org.autojs.autojs.core.image.ColorItems
import org.autojs.autojs.core.pref.keys.AppearanceKeys
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.ThemeColorCoordinator
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.ThemeColorSource
import org.autojs.autojs.theme.app.ColorEntities.ColorHistory
import org.autojs.autojs.theme.app.ColorEntities.ColorInfo
import org.autojs.autojs.theme.app.ColorEntities.HistorySource
import org.autojs.autojs.theme.app.ColorEntities.LibraryColor
import org.autojs.autojs.theme.app.ColorEntities.PaletteHistory
import org.autojs.autojs.theme.app.ColorHistoryItemViewHolder.Companion.ColorHistoryItem
import org.autojs.autojs.theme.app.ColorInfoDialogManager.CustomColorInfoDialogNeutral.NeutralButtonCallback
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_DEFAULT
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_MATERIAL
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_PALETTE
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.PresetColorItem
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.PresetColorLibrary
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.presetColorLibraries
import org.autojs.autojs.theme.intelligent.IntelligentColorEngine
import org.autojs.autojs.theme.intelligent.IntelligentSnapshot
import org.autojs.autojs.theme.library.ColorLibraryIds
import org.autojs.autojs.theme.app.ColorEntities.ColorLibrary
import org.autojs.autojs.theme.library.ColorLibraryRepository
import org.autojs.autojs.theme.library.ColorSelection
import org.autojs.autojs.theme.library.ColorSelectionCodec
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.main.drawer.DrawerFragment.Companion.Event.ThemeColorLayoutSwitchedEvent
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.StringUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.onceGlobalLayout
import org.autojs.autojs.util.ViewUtils.setColorsByColorLuminance
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByColorLuminance
import org.autojs.autojs.util.ViewUtils.setNavigationIconColorByColorLuminance
import org.autojs.autojs.util.ViewUtils.setTitlesTextColorByColorLuminance
import org.autojs.autojs6.R
import org.greenrobot.eventbus.EventBus
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.hypot
import kotlin.properties.Delegates
import org.autojs.autojs.theme.app.ColorEntities.FavoriteColor
import org.autojs.autojs.theme.usage.ColorClusterer
import org.autojs.autojs.theme.usage.ColorRecommender
import org.autojs.autojs.theme.usage.FavoriteEntry
import org.autojs.autojs.theme.usage.HistoryEntry
import org.autojs.autojs.theme.usage.Recommendation
import org.autojs.autojs.theme.widget.ThemeColorCheckBox
import org.autojs.autojs.ui.dialog.InfoDialog

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
        get() = (MiscStateKeys.COLOR_SELECT_CUSTOM_COLOR.storedOrNull() ?: getColor(R.color.custom_color_default))

    protected var currentColor by Delegates.notNull<Int>()
    protected var isForciblyEnableAppBarColorTransition = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Intelligent changes wait while any theme color page is open (roadmap D6).
        // zh-CN: 任一主题色页面打开期间, 智能修改暂缓 (Roadmap D6).
        ThemeColorCoordinator.pause(this)

        ThemeChangeNotifier.themeChanged.observe(this) {
            updateAppBarColorContent(ThemeColorManager.colorPrimary)
        }

        mTitle = intent.getStringExtra("title") ?: getString(R.string.text_theme_color)
        currentColor = intent.getIntExtra("currentColor", ThemeColorManager.colorPrimary)

        if (intent.getBooleanExtra("isToggled", false)) {
            EventBus.getDefault().post(object : ThemeColorLayoutSwitchedEvent {})
        }
    }

    override fun onDestroy() {
        releasePauseToken(this)
        super.onDestroy()
    }

    protected fun setUpToolbar(toolbar: Toolbar) {
        toolbar.apply {
            title = mTitle
            subtitle = this@ColorSelectBaseActivity.getSubtitle()
            setSupportActionBar(this)
            setNavigationOnClickListener { finish() }
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
            it.queryHint = it.context.getString(R.string.text_search_color)
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
        ViewUtils.setStatusBarIconLightByColorLuminance(this, currentColor)
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
        val selection = currentColorSelection()
        // User libraries and intelligent libraries have their own pages (roadmap P1.7).
        // zh-CN: 用户库与智能库有各自的页面 (Roadmap P1.7).
        when (selection) {
            is ColorSelection.UserColor -> {
                UserColorLibraryActivity.launch(this, selection.libraryRowId, scrollToColorId = selection.colorRowId)
                return null
            }
            is ColorSelection.Intelligent -> {
                IntelligentLibraryActivity.launch(this, selection.libraryId)
                return null
            }
            is ColorSelection.Favorite -> {
                VirtualColorLibraryActivity.launch(this, ColorLibraryIds.FAVORITES, scrollToFavoriteId = selection.favoriteId)
                return null
            }
            else -> Unit
        }
        if (selection is ColorSelection.Palette) {
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
        val preset = selection as? ColorSelection.Preset
        val targetLibrary = preset?.let { presetColorLibraries.find { library -> library.id == it.libraryId } }
        if (preset == null || targetLibrary == null) {
            val targetLibraryId = ColorSelectionCodec.encode(selection).first
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
        // Without a stored item (e.g. a legacy default selection), match the current theme color.
        // zh-CN: 没有保存的条目时 (例如 legacy 的默认色选择), 按当前主题色匹配.
        val targetItemIndex = preset.itemId
            ?: targetLibrary.colors.find { getColor(it.colorRes) == ThemeColorManager.colorPrimary }?.itemId
            ?: SELECT_NONE
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
        return TargetInfoForLocate(targetLibrary.id, targetItemIndex)
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
            .setColorHistoryHandler { showColorPickerHistory(it) }
            .create()
            .setColorPickerDialogListener { _, color: Int ->
                onColorPickerConfirmed(color)
            }
            .show(supportFragmentManager, "ColorPickerTagForColorLibraries")
    }

    /**
     * Shows the palette and reports the picked color without applying it as the theme color.
     * zh-CN: 显示调色盘并回传所选颜色, 不将其应用为主题色.
     */
    protected fun showColorPickerFor(initialColor: Int, onPicked: (Int) -> Unit) {
        ColorPickerDialog.newBuilder()
            .setUseLegacyMode(isLegacyLayout)
            .setAllowCustom(true)
            .setAllowPresets(!isLegacyLayout)
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setShowAlphaSlider(false)
            .setDialogTitle(R.string.dialog_title_color_palette)
            .setColor(initialColor)
            .setColorHistoryHandler { showColorPickerHistory(it) }
            .create()
            .setColorPickerDialogListener { _, color: Int -> onPicked(color or -0x1000000) }
            .show(supportFragmentManager, "ColorPickerTagForLibraryColor")
    }

    /**
     * A library whose colors can be cloned or copied: presets first, then user plain libraries.
     * zh-CN: 可被克隆或复制颜色的颜色库: 先是预设库, 然后是用户普通库.
     */
    protected class ColorSource(val name: String, val isPreset: Boolean, val loadColors: suspend () -> List<ColorLibraryRepository.NewColor>)

    protected suspend fun listColorSources(excludeUserLibraryId: Long? = null): List<ColorSource> {
        val repository = ColorLibraryRepository.get(this)
        val presets = presetColorLibraries.filterNot { it.isIntelligent }.map { library ->
            ColorSource(getString(library.nameRes), isPreset = true) {
                library.colors.map { ColorLibraryRepository.NewColor(getColor(it.colorRes), getString(it.nameRes)) }
            }
        }
        val users = withContext(Dispatchers.IO) { repository.listPlainLibraries() }
            .filter { it.library.id != excludeUserLibraryId }
            .map { summary ->
                ColorSource(summary.library.name, isPreset = false) {
                    withContext(Dispatchers.IO) { repository.listColors(summary.library.id) }.map { ColorLibraryRepository.NewColor(it.color, it.name) }
                }
            }
        return presets + users
    }

    /**
     * Applies a color of a user library as the theme color (roadmap P1.1, D13).
     * zh-CN: 将用户库中的颜色应用为主题色 (Roadmap P1.1, D13).
     */
    protected fun applyUserLibraryColor(libraryName: String, color: LibraryColor) {
        val libraryId = ColorLibraryIds.ofUserLibrary(color.libraryId, intelligent = false)
        savePrefsForLibraries(libraryId, color.id.toInt())
        savePrefsForLegacyPalette(color.color)
        savePrefsForSelectionNames(libraryName, color.name)
        saveDatabaseForColorHistory(lifecycleScope, applicationContext, libraryId, color.id.toInt())
        isForciblyEnableAppBarColorTransition = true
        ThemeColorCoordinator.apply(color.color, ThemeColorSource.USER)
    }

    protected fun applyPresetItem(item: PresetColorItem) {
        savePrefsForLibraries(item)
        savePrefsForLegacy(this, item)
        saveDatabaseForColorHistory(applicationContext = applicationContext, libraryId = item.libraryId, itemId = item.itemId)
        isForciblyEnableAppBarColorTransition = true
        ThemeColorCoordinator.apply(getColor(item.colorRes), ThemeColorSource.USER)
    }

    /**
     * Activates an intelligent library starting from [groupId]; its color applies at once (roadmap P1.2).
     * zh-CN: 激活智能库并从 [groupId] 开始; 其颜色立即生效 (Roadmap P1.2).
     */
    protected fun activateIntelligentLibrary(snapshot: IntelligentSnapshot, groupId: Long?) {
        isForciblyEnableAppBarColorTransition = true
        IntelligentColorEngine.activate(snapshot, groupId)
    }

    protected fun onColorPickerConfirmed(color: Int) {
        savePrefsForLegacyPalette(color)
        savePrefsForLibraries(COLOR_LIBRARY_ID_PALETTE, 0)

        saveDatabaseForPaletteHistory(lifecycleScope, applicationContext, color)

        isForciblyEnableAppBarColorTransition = true

        ThemeColorCoordinator.apply(color, ThemeColorSource.USER)
    }

    private fun showColorPickerHistory(colorPickerDialog: ColorPickerDialog) {
        lifecycleScope.launch {
            var historyDialog: MaterialDialog? = null
            val dao = withContext(Dispatchers.IO) {
                ColorHistoryDatabase.getInstance(applicationContext).paletteHistoryDao()
            }
            when {
                withContext(Dispatchers.IO) { dao.hasData() } -> {
                    val adapter = PaletteHistoryItemAdapter(emptyList()) { selectedColor ->
                        historyDialog?.dismiss()
                        colorPickerDialog.onHistorySelected(selectedColor)
                    }
                    launch(Dispatchers.Main) {
                        val context = this@ColorSelectBaseActivity
                        val recyclerView = ThemeColorRecyclerView(context).also {
                            it.adapter = adapter
                            it.layoutManager = LinearLayoutManager(context)
                            it.addItemDecoration(DividerItemDecoration(context, VERTICAL))
                        }
                        historyDialog = MaterialDialog.Builder(context)
                            .title(R.string.text_history)
                            .customView(recyclerView, false)
                            .neutralText(R.string.dialog_button_clear_items)
                            .neutralColorRes(R.color.dialog_button_warn)
                            .onNeutral { parentDialog, _ ->
                                MaterialDialog.Builder(context)
                                    .title(R.string.text_prompt)
                                    .content(getString(R.string.text_confirm_to_clear_the_history_of, getString(R.string.text_color_palette)))
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
                                                ViewUtils.showToast(context, R.string.text_history_has_been_cleared, true)
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
                    val history = withContext(Dispatchers.IO) {
                        dao.getAll().sortedByDescending { it.lastUsedTime }
                    }
                    launch(Dispatchers.Main) {
                        adapter.updateData(history)
                    }
                }
                else -> withContext(Dispatchers.Main) {
                    MaterialDialog.Builder(this@ColorSelectBaseActivity)
                        .title(R.string.text_history)
                        .content(R.string.text_no_history)
                        .positiveText(R.string.dialog_button_dismiss)
                        .positiveColorRes(R.color.dialog_button_default)
                        .show()
                }
            }
        }
    }

    protected fun showColorHistory(libraryId: Int? = null, onHistorySelected: (selectedHistoryItem: ColorHistoryItem) -> Unit) {

        val isShowAllHistoryEntries = libraryId == null

        lifecycleScope.launch {
            var historyDialog: MaterialDialog? = null
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
                        .title(R.string.text_history)
                        .content(R.string.text_no_history)
                        .positiveText(R.string.dialog_button_dismiss)
                        .positiveColorRes(R.color.dialog_button_default)
                        .show()
                }
                return@launch
            }
            val adapter = ColorHistoryItemAdapter(emptyList()) { selectedHistoryItem ->
                historyDialog?.dismiss()
                onHistorySelected(selectedHistoryItem)
            }
            var allHistoryItems: List<ColorHistoryItem> = emptyList()
            // "Merge similar colors" keeps the newest of similar entries (roadmap P5.3). zh-CN: "合并相似颜色" 保留相似条目中最新的一条 (Roadmap P5.3).
            fun showHistoryItems() = adapter.updateData(
                when {
                    AppearanceKeys.COLOR_HISTORY_MERGE_SIMILAR.get() -> ColorClusterer.cluster(allHistoryItems, { it.colorInt })
                        .map { it.representative.copy(mergedCount = it.merged) }
                    else -> allHistoryItems
                }
            )
            launch(Dispatchers.Main) {
                val context = this@ColorSelectBaseActivity
                val recyclerView = ThemeColorRecyclerView(context).also {
                    it.adapter = adapter
                    it.layoutManager = LinearLayoutManager(context)
                    it.addItemDecoration(DividerItemDecoration(context, VERTICAL))
                }
                val mergeCheck = ThemeColorCheckBox(context).apply {
                    text = getString(R.string.text_merge_similar_colors)
                    isChecked = AppearanceKeys.COLOR_HISTORY_MERGE_SIMILAR.get()
                    val padding = (16 * resources.displayMetrics.density).toInt()
                    setPadding(paddingLeft, padding / 2, paddingRight, padding / 2)
                    setOnCheckedChangeListener { _, checked ->
                        AppearanceKeys.COLOR_HISTORY_MERGE_SIMILAR.set(checked)
                        showHistoryItems()
                    }
                }
                val content = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    val padding = (20 * resources.displayMetrics.density).toInt()
                    addView(mergeCheck, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = padding })
                    addView(recyclerView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                }
                historyDialog = MaterialDialog.Builder(context)
                    .title(R.string.text_history)
                    .customView(content, false)
                    .neutralText(R.string.dialog_button_clear_items)
                    .neutralColorRes(R.color.dialog_button_warn)
                    .onNeutral { parentDialog, _ ->
                        MaterialDialog.Builder(context)
                            .title(R.string.text_prompt)
                            .apply {
                                when {
                                    isShowAllHistoryEntries -> {
                                        content(getString(R.string.text_confirm_to_clear_the_history_of_all_color_libraries))
                                    }
                                    else -> presetColorLibraries.find { it.id == libraryId }?.let {
                                        content(getString(R.string.text_confirm_to_clear_the_history_of, getString(it.nameRes)))
                                    } ?: content(getString(R.string.text_confirm_to_clear_the_history))
                                }
                            }
                            .negativeText(R.string.dialog_button_cancel)
                            .negativeColorRes(R.color.dialog_button_default)
                            .positiveText(R.string.dialog_button_confirm)
                            .positiveColorRes(R.color.dialog_button_caution)
                            .onPositive { _, _ ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    if (isShowAllHistoryEntries) {
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
                                        ViewUtils.showToast(context, R.string.text_history_has_been_cleared, true)
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

            allHistoryItems = loadColorHistoryItems(libraryId)

            launch(Dispatchers.Main) {
                showHistoryItems()
            }
        }
    }

    /**
     * History entries of [libraryId], or of every library plus the palette when it is null, newest first.
     * Entries whose library item no longer exists are skipped.
     * zh-CN: [libraryId] 的历史条目; 为 null 时包含全部颜色库及调色盘, 按时间倒序. 对应条目已不存在的记录会被跳过.
     */
    protected suspend fun loadColorHistoryItems(libraryId: Int?): List<ColorHistoryItem> {
        val isShowAllHistoryEntries = libraryId == null
        val database = ColorHistoryDatabase.getInstance(applicationContext)
        val colorHistory = withContext(Dispatchers.IO) {
            when {
                isShowAllHistoryEntries -> database.colorHistoryDao().getAll()
                else -> database.colorHistoryDao().getAllByLibraryId(libraryId.toLong())
            }
        }

        // Colors of user libraries, keyed by row id. zh-CN: 用户库中的颜色, 按行 id 索引.
        val userColors = withContext(Dispatchers.IO) {
            when {
                colorHistory.none { ColorLibraryIds.isUserPlain(it.libraryId) } -> emptyMap()
                else -> database.colorLibraryDao().let { dao ->
                    val names = dao.listLibraries().associate { it.id to it.name }
                    dao.listAllColors().associate { it.id to (it to names[it.libraryId]) }
                }
            }
        }

        // Favorites, keyed by row id (roadmap P5.1). zh-CN: 收藏, 按行 id 索引 (Roadmap P5.1).
        val favorites = withContext(Dispatchers.IO) {
            when {
                colorHistory.none { it.libraryId == ColorLibraryIds.FAVORITES } -> emptyMap()
                else -> database.colorLibraryDao().listFavorites().associateBy { it.id }
            }
        }

        val historyItems = colorHistory.mapNotNull { history ->
            var colorInt: Int? = null
            var colorName: String? = null
            var colorLibraryIdentifier: String? = null

            val presetColorLibrary = presetColorLibraries.find { it.id == history.libraryId }

            if (presetColorLibrary != null) {
                presetColorLibrary.colors.find { it.itemId == history.itemId }?.let { item ->
                    colorInt = getColor(item.colorRes)
                    colorName = getString(item.nameRes)
                    if (isShowAllHistoryEntries) {
                        colorLibraryIdentifier = getString(presetColorLibrary.identifierRes)
                    }
                }
            } else if (ColorLibraryIds.isUserPlain(history.libraryId)) {
                userColors[history.itemId.toLong()]?.let { (color, libraryName) ->
                    colorInt = color.color
                    colorName = color.name
                    if (isShowAllHistoryEntries) {
                        colorLibraryIdentifier = libraryName ?: getString(R.string.color_library_identifier_created)
                    }
                }
            } else if (history.libraryId == ColorLibraryIds.FAVORITES) {
                favorites[history.itemId.toLong()]?.let { favorite ->
                    colorInt = favorite.color
                    if (isShowAllHistoryEntries) {
                        colorLibraryIdentifier = getString(R.string.color_library_identifier_favorites)
                    }
                }
            }

            if (colorInt != null) {
                return@mapNotNull ColorHistoryItem(history.lastUsedTime, colorInt!!, colorName, withHistorySource(colorLibraryIdentifier, history.source, history.sourceDetail), history.libraryId, history.itemId, history.useCount)
            }
            // TODO by SuperMonster003 on Apr 8 ,2025.
            //  ! Obsolete data should be removed from database.
            //  ! Data will not be removed so far as color lib creation has not been completed yet.
            //  ! zh-CN: 孤立数据应从数据库中移除, 但目前暂未完成颜色库建立相关功能, 此处暂时不做数据移除处理.
            return@mapNotNull null
        }.toMutableList()

        if (isShowAllHistoryEntries) {
            val paletteIdentifier = getString(R.string.color_library_identifier_palette)
            historyItems += database.paletteHistoryDao().getAll().map { history ->
                ColorHistoryItem(
                    lastUsedTime = history.lastUsedTime,
                    colorInt = history.colorInfo.colorInt,
                    colorName = null,
                    colorLibraryIdentifier = withHistorySource(paletteIdentifier, history.source, history.sourceDetail),
                    useCount = history.useCount,
                )
            }
        }

        return historyItems.sortedByDescending { it.lastUsedTime }
    }

    /**
     * Appends "Script: name" to the identifier of entries chosen by scripts (D11, roadmap P6.3).
     * zh-CN: 为脚本选择的条目在标识后追加 "脚本: 名称" (D11, Roadmap P6.3).
     */
    private fun withHistorySource(identifier: String?, source: String, detail: String?): String? = when (source) {
        HistorySource.SCRIPT -> {
            val script = getString(R.string.text_history_source_script, detail ?: "-")
            identifier?.let { "$it · $script" } ?: script
        }
        else -> identifier
    }

    /**
     * "Frequent and recommended" (roadmap P5.4): favorites first, then the history of every library and the palette
     * ranked by use, with similar colors merged; at most [ColorRecommender.MAX_ITEMS].
     * zh-CN: "常用与推荐" (Roadmap P5.4): 收藏在前, 然后是按使用情况排序的全部颜色库与调色板历史, 相似颜色已合并; 至多 [ColorRecommender.MAX_ITEMS] 项.
     */
    protected suspend fun loadRecommendations(): List<Recommendation<ColorHistoryItem>> {
        val favorites = withContext(Dispatchers.IO) { ColorLibraryRepository.get(this@ColorSelectBaseActivity).listFavorites() }.map(::favoriteEntryOf)
        val history = loadColorHistoryItems(null).map { HistoryEntry(it, it.colorInt, it.useCount, it.lastUsedTime) }
        return withContext(Dispatchers.Default) { ColorRecommender.recommend(favorites, history, System.currentTimeMillis()) }
    }

    /**
     * Applies a history entry the way it was chosen: a library item, a favorite or a palette color.
     * zh-CN: 按历史条目当初的选择方式应用: 颜色库条目, 收藏或调色板颜色.
     */
    protected fun applyHistoryItem(item: ColorHistoryItem) {
        val libraryId = item.libraryId
        val itemId = item.itemId
        val repository = ColorLibraryRepository.get(this)
        when {
            libraryId != null && itemId != null && ColorLibraryIds.isUserPlain(libraryId) -> lifecycleScope.launch {
                val color = withContext(Dispatchers.IO) { repository.findColor(itemId.toLong()) }
                val library = color?.let { withContext(Dispatchers.IO) { repository.findLibrary(it.libraryId) } }
                when {
                    color == null || library == null -> ViewUtils.showToast(this@ColorSelectBaseActivity, R.string.error_failed_to_apply_current_color_history, true)
                    else -> applyUserLibraryColor(library.name, color)
                }
            }
            libraryId == ColorLibraryIds.FAVORITES && itemId != null -> lifecycleScope.launch {
                // A favorite removed meanwhile still applies its color as a palette color. zh-CN: 期间已被取消的收藏仍以调色板颜色应用.
                when (val favorite = withContext(Dispatchers.IO) { repository.findFavoriteById(itemId.toLong()) }) {
                    null -> onColorPickerConfirmed(item.colorInt)
                    else -> applyFavorite(favorite)
                }
            }
            libraryId != null && itemId != null -> {
                presetColorLibraries.find { it.id == libraryId }?.colors?.find { it.itemId == itemId }?.let { applyPresetItem(it) }
                    ?: ViewUtils.showToast(this, R.string.error_failed_to_apply_current_color_history, true)
            }
            else -> onColorPickerConfirmed(item.colorInt)
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
        val colorTools = ColorInfoDialogManager.CustomColorInfoDialogNeutral(
            textRes = R.string.text_color_tools,
            colorRes = R.color.dialog_button_attraction,
            onNeutralCallback = object : NeutralButtonCallback {
                override fun onClick(dialog: MaterialDialog, which: DialogAction, color: Int) {
                    ColorDerivationDialog.show(
                        this@ColorSelectBaseActivity, color,
                        onApply = { onColorPickerConfirmed(it) },
                        onAddToLibrary = { addColorsToUserLibrary(it) },
                        onFavorite = { setFavorite(it, true) },
                    )
                }
            }
        )
        val infoDialog = ColorInfoDialogManager.showColorInfoDialog(this, color, title, customNeutral, colorTools)
        if (infoDialog != null && color != null) addFavoriteControls(infoDialog, color)
    }

    /**
     * "Favorite" and "Pinned" checkboxes under the values of the color details (roadmap P5.1).
     * zh-CN: 颜色详情数值下方的 "收藏" 与 "置顶" 复选框 (Roadmap P5.1).
     */
    private fun addFavoriteControls(infoDialog: InfoDialog, color: Int) {
        val favoriteCheck = ThemeColorCheckBox(this).apply { text = getString(R.string.text_favorite) }
        val pinnedCheck = ThemeColorCheckBox(this).apply { text = getString(R.string.text_favorite_pinned) }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(favoriteCheck)
            addView(pinnedCheck, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = (16 * resources.displayMetrics.density).toInt()
            })
        }
        infoDialog.items.addSeparator()
        infoDialog.items.addCustom(row)
        infoDialog.scope.launch {
            val favorite = withContext(Dispatchers.IO) { ColorLibraryRepository.get(this@ColorSelectBaseActivity).findFavorite(color) }
            favoriteCheck.isChecked = favorite != null
            pinnedCheck.isChecked = favorite?.pinned == true
            pinnedCheck.isEnabled = favorite != null
            favoriteCheck.setOnCheckedChangeListener { _, checked ->
                pinnedCheck.isEnabled = checked
                if (!checked) pinnedCheck.isChecked = false
                setFavorite(color, checked)
            }
            pinnedCheck.setOnCheckedChangeListener { _, checked ->
                if (favoriteCheck.isChecked) setFavoritePinned(color, checked)
            }
        }
    }

    /**
     * Applies a favorite as the theme color; the selection and the history name the favorites library (roadmap P5.1).
     * zh-CN: 将收藏应用为主题色; 选中状态与历史记录均归于收藏库 (Roadmap P5.1).
     */
    protected fun applyFavorite(favorite: FavoriteColor) {
        savePrefsForLibraries(ColorLibraryIds.FAVORITES, favorite.id.toInt())
        savePrefsForLegacyPalette(favorite.color)
        savePrefsForSelectionNames(getString(R.string.color_library_favorites), null)
        saveDatabaseForColorHistory(lifecycleScope, applicationContext, ColorLibraryIds.FAVORITES, favorite.id.toInt())
        isForciblyEnableAppBarColorTransition = true
        ThemeColorCoordinator.apply(favorite.color, ThemeColorSource.USER)
    }

    /**
     * Adds [color] to the favorites or removes it. Removing the favorite in use keeps the color as a palette color.
     * zh-CN: 收藏或取消收藏 [color]. 取消收藏正在使用的收藏时, 该颜色保留为调色板颜色.
     */
    protected fun setFavorite(color: Int, favorite: Boolean, then: () -> Unit = {}) {
        val repository = ColorLibraryRepository.get(this)
        lifecycleScope.launch {
            when {
                favorite -> withContext(Dispatchers.IO) { repository.addFavorite(color) }
                else -> {
                    val removed = withContext(Dispatchers.IO) { repository.findFavorite(color)?.also { repository.removeFavorite(color) } }
                    if (removed != null && (currentColorSelection() as? ColorSelection.Favorite)?.favoriteId == removed.id) {
                        fallBackToPaletteSelection(this@ColorSelectBaseActivity)
                    }
                }
            }
            ViewUtils.showToast(this@ColorSelectBaseActivity, if (favorite) R.string.text_added_to_favorites else R.string.text_removed_from_favorites)
            onFavoritesChanged()
            then()
        }
    }

    protected fun setFavoritePinned(color: Int, pinned: Boolean, then: () -> Unit = {}) {
        val repository = ColorLibraryRepository.get(this)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { repository.setFavoritePinned(color, pinned) }
            onFavoritesChanged()
            then()
        }
    }

    /** Called after any favorite changed. zh-CN: 任一收藏变化后调用. */
    protected open fun onFavoritesChanged() = Unit

    /**
     * Long press actions of a color: [extra] first, then favorite / pin actions and the details (roadmap P5.1).
     * zh-CN: 颜色的长按操作: 先是 [extra], 然后是收藏 / 置顶操作与颜色详情 (Roadmap P5.1).
     */
    protected fun showColorActions(color: Int, title: String, extra: List<Pair<String, () -> Unit>> = emptyList()) {
        lifecycleScope.launch {
            val favorite = withContext(Dispatchers.IO) { ColorLibraryRepository.get(this@ColorSelectBaseActivity).findFavorite(color) }
            val actions = extra + favoriteActions(color, favorite) + (getString(R.string.dialog_title_color_details) to { showColorDetails(color, title) })
            ColorLibraryDialogs.showActions(this@ColorSelectBaseActivity, title, actions)
        }
    }

    protected fun favoriteActions(color: Int, favorite: FavoriteColor?): List<Pair<String, () -> Unit>> = when (favorite) {
        null -> listOf(getString(R.string.text_add_to_favorites) to { setFavorite(color, true) })
        else -> listOf(
            getString(if (favorite.pinned) R.string.text_unpin_favorite else R.string.text_pin_to_top) to { setFavoritePinned(color, !favorite.pinned) },
            getString(R.string.text_remove_from_favorites) to { setFavorite(color, false) },
        )
    }

    /**
     * Adds [colors] to a user color library chosen by the user, or to a new one (roadmap P4.3).
     * zh-CN: 将 [colors] 加入用户选择的颜色库, 或新建的颜色库 (Roadmap P4.3).
     */
    protected fun addColorsToUserLibrary(colors: List<Int>) {
        if (colors.isEmpty()) return
        val repository = ColorLibraryRepository.get(this)
        val newColors = colors.map { ColorLibraryRepository.NewColor(it) }
        fun added() = ViewUtils.showToast(this, resources.getQuantityString(R.plurals.text_n_colors_added, colors.size, colors.size))
        lifecycleScope.launch {
            val libraries = withContext(Dispatchers.IO) { repository.listPlainLibraries() }.map { it.library }
            val labels = listOf(getString(R.string.text_new_color_library)) + libraries.map { it.name }
            ColorLibraryDialogs.showSingleChoice(this@ColorSelectBaseActivity, R.string.text_add_to_color_library, labels) { which ->
                when (which) {
                    0 -> ColorLibraryDialogs.showNameInput(this@ColorSelectBaseActivity, R.string.text_new_color_library, null) { name ->
                        name ?: return@showNameInput
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) { repository.createPlainLibrary(name, ColorLibrary.Kind.CREATED, colors = newColors) }
                            added()
                        }
                    }
                    else -> lifecycleScope.launch {
                        withContext(Dispatchers.IO) { repository.addColors(libraries[which - 1].id, newColors) }
                        added()
                    }
                }
            }
        }
    }

    protected fun showThemeColorDetails() {
        showColorDetails(ThemeColorManager.colorPrimary, getSubtitle(false))
    }

    protected fun showColorSearchHelp() {
        Intent(this, ColorSearchHelpActivity::class.java)
            .startSafely(this)
    }

    protected fun filterColorsFromColorItems(query: String?, colorItems: List<PresetColorItem>, colorItemAdapter: ColorItemAdapter) {
        mSearchJob?.cancel()
        mSearchJob = lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                val matcher = colorQueryMatcher(query) ?: return@withContext colorItems
                colorItems.filter { matcher.matches(it) }
            }.let { filteredResult -> colorItemAdapter.updateData(filteredResult) }
        }
    }

    /**
     * Runs [filter] for [query] off the main thread, cancelling any search still running, and delivers the result on the main thread.
     * zh-CN: 在主线程之外针对 [query] 执行 [filter], 并取消仍在进行的搜索, 结果在主线程交付.
     */
    protected fun <T> searchColors(query: String?, filter: (ColorQueryMatcher?) -> List<T>, onResult: (List<T>) -> Unit) {
        mSearchJob?.cancel()
        mSearchJob = lifecycleScope.launch {
            onResult(withContext(Dispatchers.Default) { filter(colorQueryMatcher(query)) })
        }
    }

    /**
     * Matches colors against a search query: `#...` matches color values, `$...` English names, anything else
     * localized names; each form may be a regular expression (see the search help page).
     * zh-CN: 按搜索词匹配颜色: `#...` 匹配色值, `$...` 匹配英文名称, 其他匹配本地化名称; 各形式均可为正则表达式 (见搜索帮助页).
     */
    inner class ColorQueryMatcher(query: String) {

        private val searchTerm = query.trimStart()
        private val cleanedSearchTerm = searchTerm.drop(1)
        private val normalizedQuery = normalizeForMatching(searchTerm)

        /** [hexes] are `#RRGGBB` values; the names are computed only when needed. zh-CN: [hexes] 为 `#RRGGBB` 色值; 名称仅在需要时计算. */
        fun matches(hexes: List<String>, englishName: () -> String, localizedName: () -> String): Boolean = when {
            searchTerm.startsWith("#") -> hexes.any { hex ->
                searchMatch(cleanedSearchTerm, hex) { hex.contains(searchTerm, ignoreCase = true) }
            }
            searchTerm.startsWith("$") -> englishName().let { name ->
                searchMatch(cleanedSearchTerm, name) { normalizeForMatching(name).contains(normalizedQuery, ignoreCase = true) }
            }
            else -> localizedName().let { name ->
                searchMatch(searchTerm, name) { normalizeForMatching(name).contains(normalizedQuery, ignoreCase = true) }
            }
        }

        fun matches(item: PresetColorItem): Boolean = matches(
            hexes = listOf(String.format("#%06X", 0xFFFFFF and getColor(item.colorRes))),
            englishName = { StringUtils.getStringForLocale(this@ColorSelectBaseActivity, Locale.forLanguageTag("en"), item.nameRes) },
            localizedName = { getString(item.nameRes) },
        )

    }

    /** Null for a blank query, which matches everything. zh-CN: 搜索词为空白时返回 null, 表示匹配全部. */
    protected fun colorQueryMatcher(query: String?): ColorQueryMatcher? = query?.takeIf { it.isNotBlank() }?.let { ColorQueryMatcher(it) }

    fun searchMatch(search: String, text: String, nonRegexMatch: () -> Boolean) = when {
        isRegexSearch(search) -> {
            compileRegexOrNull(extractPatternFromQuery(search))?.containsMatchIn(text) == true
        }
        else -> nonRegexMatch()
    }

    private fun normalizeForMatching(input: String): String {
        // Remove all non-letter and non-digit characters (e.g. spaces/brackets/slashes etc.).
        // \p{L} matches any letter character in any language, \p{N} matches any digit.
        // zh-CN:
        // 去掉所有非字母或数字字符 (例如空格/括号/斜线等).
        // \p{L} 表示任何语言的字母字符, \p{N} 表示数字.
        return input.replace("[^\\p{L}\\p{N}]+".toRegex(), "")
    }

    private fun isRegexSearch(query: String?): Boolean {
        if (query.isNullOrBlank()) return false
        return query.length > 2 && query.startsWith("/") && query.endsWith("/")
    }

    private fun extractPatternFromQuery(query: String): String {
        // Assuming isRegexSearch() == true has been checked.
        // Remove the leading and trailing slashes.
        // zh-CN:
        // 假设已检查过 isRegexSearch() == true.
        // 去掉最前和最后的斜杠.
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

        // The selection is remembered in MiscStateKeys.COLOR_SELECT_* (docs/settings/ROADMAP.md P4.2, appendix C Q15);
        // the display names of a user or intelligent selection are cached so summaries need no database access.
        // zh-CN: 选择记忆在 MiscStateKeys.COLOR_SELECT_* 中 (ROADMAP P4.2, 附录 C Q15); 用户库或智能库选中项的显示名称会缓存,
        // 使摘要无需访问数据库.

        fun favoriteEntryOf(favorite: FavoriteColor) = FavoriteEntry(favorite.id, favorite.color, favorite.pinned, favorite.position, favorite.createdTime)

        private const val LIBRARY_NAME_SUMMARY_LENGTH = 12

        const val INTENT_IDENTIFIER_LIBRARY_ID = "LIBRARY_ID"
        const val INTENT_IDENTIFIER_COLOR_ITEM_ID_SCROLL_TO = "INTENT_IDENTIFIER_COLOR_ITEM_ID_SCROLL_TO"

        val customColorPosition: Int
            get() = /* return mColors.size() - 1; */ 0

        val defaultColorPosition: Int
            get() = customColorPosition + 1

        var isLegacyLayout: Boolean
            get() = MiscStateKeys.COLOR_SELECT_ACTIVITY_LEGACY_LAYOUT.get()
            set(value) = MiscStateKeys.COLOR_SELECT_ACTIVITY_LEGACY_LAYOUT.set(value)

        val colorItemsLegacy by lazy {
            mutableListOf<Pair</* colorRes */ Int, /* nameRes */ Int>>().apply {
                add(customColorPosition, R.color.custom_color_default to R.string.color_library_identifier_custom)
                add(defaultColorPosition, R.color.theme_color_default to R.string.color_library_identifier_default_colors)
                addAll(ColorItems.MATERIAL_COLORS)
            }
        }

        @JvmStatic
        fun launch(context: Context, isToggled: Boolean = false) {
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
            intent.startSafely(context)
        }

        /**
         * The stored theme color selection, normalized by [ColorSelectionCodec] (legacy index included).
         * zh-CN: 经 [ColorSelectionCodec] 归一化 (含 legacy 索引) 的已保存主题色选中状态.
         */
        @JvmStatic
        fun currentColorSelection(): ColorSelection = ColorSelectionCodec.decode(
            libraryId = MiscStateKeys.COLOR_SELECT_LIBRARY_ID.get(),
            itemId = MiscStateKeys.COLOR_SELECT_ITEM_ID.get(),
            legacyIndex = (MiscStateKeys.COLOR_SELECT_LEGACY_SELECTED_INDEX.storedOrNull() ?: SELECT_NONE),
            materialColorCount = ColorItems.MATERIAL_COLORS.size,
        )

        @JvmStatic
        fun getCurrentColorSummary(context: Context, withIdentifierPrefix: Boolean = true, withHexSuffix: Boolean = true): String = when {
            // The legacy layout cannot show intelligent libraries; name the active one instead (D13).
            // zh-CN: 旧布局无法显示智能库; 改为显示激活中的智能库名称 (D13).
            isLegacyLayout && currentColorSelection() is ColorSelection.Intelligent -> {
                val name = MiscStateKeys.COLOR_SELECT_LIBRARY_NAME.storedOrNull() ?: context.getString(R.string.color_library_title_intelligent_colors)
                val summary = "${context.getString(R.string.color_library_identifier_intelligent_colors)}: $name"
                if (withHexSuffix) "$summary [${ColorUtils.toString(ThemeColorManager.colorPrimary)}]" else summary
            }
            isLegacyLayout -> when (val index = (MiscStateKeys.COLOR_SELECT_LEGACY_SELECTED_INDEX.storedOrNull() ?: defaultColorPosition)) {
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
                                customColorPosition -> (MiscStateKeys.COLOR_SELECT_CUSTOM_COLOR.storedOrNull() ?: context.getColor(R.color.custom_color_default))
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
                when (val selection = currentColorSelection()) {
                    ColorSelection.Palette -> {
                        identifier = context.getString(R.string.color_library_identifier_palette)
                    }
                    is ColorSelection.UserColor -> {
                        identifier = selectionLibraryNameForSummary() ?: context.getString(R.string.color_library_identifier_created)
                        colorName = MiscStateKeys.COLOR_SELECT_ITEM_NAME.storedOrNull()
                    }
                    is ColorSelection.Favorite -> {
                        identifier = context.getString(R.string.color_library_identifier_favorites)
                    }
                    is ColorSelection.Intelligent -> {
                        identifier = selectionLibraryNameForSummary() ?: context.getString(R.string.color_library_identifier_intelligent_colors)
                        colorName = MiscStateKeys.COLOR_SELECT_ITEM_NAME.storedOrNull()
                    }
                    is ColorSelection.Unknown -> Unit
                    is ColorSelection.Preset -> presetColorLibraries.find { it.id == selection.libraryId }?.let { lib: PresetColorLibrary ->
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
                        selection.itemId?.let { itemId ->
                            lib.colors.find { it.itemId == itemId }?.let { item ->
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

        /**
         * Records one use of a palette color; [source] is [HistorySource.USER] or [HistorySource.SCRIPT] (roadmap P5.2).
         * zh-CN: 记录调色板颜色的一次使用; [source] 为 [HistorySource.USER] 或 [HistorySource.SCRIPT] (Roadmap P5.2).
         */
        @JvmStatic
        fun saveDatabaseForPaletteHistory(dispatcherScope: CoroutineScope? = null, applicationContext: Context, color: Int, source: String = HistorySource.USER, sourceDetail: String? = null) {
            saveHistory(dispatcherScope) {
                var itemId by Delegates.notNull<Int>()
                savePaletteHistory(applicationContext, color, source, sourceDetail).also { itemId = it }
                saveColorHistory(applicationContext, COLOR_LIBRARY_ID_PALETTE, itemId, source, sourceDetail)
            }
        }

        @JvmStatic
        fun saveDatabaseForColorHistory(dispatcherScope: CoroutineScope? = null, applicationContext: Context, libraryId: Int, itemId: Int, source: String = HistorySource.USER) {
            saveHistory(dispatcherScope) {
                saveColorHistory(applicationContext, libraryId, itemId, source)
            }
        }

        private fun saveHistory(
            dispatcherScope: CoroutineScope? = null,
            saveJob: suspend CoroutineScope.() -> Unit,
        ) {
            when (dispatcherScope) {
                null -> CoroutineScope(Dispatchers.IO).launch(block = saveJob)
                else -> dispatcherScope.launch(context = Dispatchers.IO, block = saveJob)
            }
        }

        private suspend fun savePaletteHistory(applicationContext: Context, color: Int, source: String, sourceDetail: String? = null): Int {
            val dao = ColorHistoryDatabase.getInstance(applicationContext).paletteHistoryDao()
            val colorInfo = ColorInfo(ColorUtils.toHex(color, 6))
            return dao.upsert(PaletteHistory(colorInfo = colorInfo, source = source, sourceDetail = sourceDetail).apply { setLastUsedTimeCurrent() }).toInt()
        }

        private suspend fun saveColorHistory(applicationContext: Context, libraryId: Int, itemId: Int, source: String, sourceDetail: String? = null) {
            val dao = ColorHistoryDatabase.getInstance(applicationContext).colorHistoryDao()
            dao.upsert(ColorHistory(libraryId = libraryId, itemId = itemId, source = source, sourceDetail = sourceDetail).apply { setLastUsedTimeCurrent() })
        }

        @JvmStatic
        fun savePrefsForLegacyPalette(color: Int) {
            MiscStateKeys.COLOR_SELECT_CUSTOM_COLOR.set(color or -0x1000000)
            MiscStateKeys.COLOR_SELECT_LEGACY_SELECTED_INDEX.set(customColorPosition)
        }

        fun savePrefsForLegacy(context: Context, colorItem: PresetColorItem) {
            when (colorItem.libraryId) {
                COLOR_LIBRARY_ID_DEFAULT -> {
                    if (context.getColor(colorItem.colorRes) == context.getColor(R.color.theme_color_default)) {
                        MiscStateKeys.COLOR_SELECT_LEGACY_SELECTED_INDEX.set(defaultColorPosition)
                    } else {
                        MiscStateKeys.COLOR_SELECT_LEGACY_SELECTED_INDEX.set(SELECT_NONE)
                    }
                }
                COLOR_LIBRARY_ID_MATERIAL -> {
                    val index = colorItem.itemId
                    if (index !in ColorItems.MATERIAL_COLORS.indices) {
                        MiscStateKeys.COLOR_SELECT_LEGACY_SELECTED_INDEX.set(SELECT_NONE)
                    } else {
                        val fixedIndex = maxOf(
                            defaultColorPosition,
                            customColorPosition,
                        ) + 1 + index
                        MiscStateKeys.COLOR_SELECT_LEGACY_SELECTED_INDEX.set(fixedIndex)
                    }
                }
                else -> {
                    MiscStateKeys.COLOR_SELECT_LEGACY_SELECTED_INDEX.set(SELECT_NONE)
                }
            }
        }

        fun savePrefsForLibraries(colorItem: PresetColorItem) {
            savePrefsForLibraries(colorItem.libraryId, colorItem.itemId)
        }

        fun savePrefsForLibraries(libraryId: Int, itemId: Int) {
            MiscStateKeys.COLOR_SELECT_LIBRARY_ID.set(libraryId)
            MiscStateKeys.COLOR_SELECT_ITEM_ID.set(itemId)
        }

        /**
         * The selected library or color is gone: keep the theme color, but record it as a palette selection.
         * zh-CN: 选中的颜色库或颜色已不存在: 保留主题色, 但改记为调色盘选择.
         */
        @JvmStatic
        fun fallBackToPaletteSelection(context: Context) {
            val color = ThemeColorManager.colorPrimary
            savePrefsForLibraries(COLOR_LIBRARY_ID_PALETTE, 0)
            savePrefsForLegacyPalette(color)
            saveDatabaseForPaletteHistory(applicationContext = context.applicationContext, color = color)
            ThemeChangeNotifier.notifyThemeChanged()
        }

        /**
         * Records an active intelligent library as the selection; the item id holds the starting group (D9).
         * zh-CN: 将激活的智能库记为选中状态; 条目 id 保存起始组 (D9).
         */
        @JvmStatic
        fun savePrefsForIntelligent(libraryId: Int, startGroupId: Long?) {
            ColorSelectionCodec.encode(ColorSelection.Intelligent(libraryId, startGroupId)).let { (library, item) ->
                savePrefsForLibraries(library, item)
            }
            MiscStateKeys.COLOR_SELECT_LEGACY_SELECTED_INDEX.set(SELECT_NONE)
        }

        /**
         * Releases the pause token of a theme color page. When the page is only being recreated (rotation, night
         * mode), the release waits one main loop turn so the new instance takes its token first.
         * zh-CN: 释放主题色页面的暂停令牌. 页面只是在重建 (旋转, 夜间模式) 时, 延后一轮主线程循环释放, 让新实例先取得令牌.
         */
        @JvmStatic
        fun releasePauseToken(activity: android.app.Activity) {
            when {
                activity.isChangingConfigurations -> Handler(Looper.getMainLooper()).post { ThemeColorCoordinator.resume(activity) }
                else -> ThemeColorCoordinator.resume(activity)
            }
        }

        fun savePrefsForSelectionNames(libraryName: String?, itemName: String?) {
            MiscStateKeys.COLOR_SELECT_LIBRARY_NAME.set(libraryName)
            MiscStateKeys.COLOR_SELECT_ITEM_NAME.set(itemName)
        }

        private fun selectionLibraryNameForSummary(): String? = MiscStateKeys.COLOR_SELECT_LIBRARY_NAME.storedOrNull()?.let {
            if (it.length <= LIBRARY_NAME_SUMMARY_LENGTH) it else it.take(LIBRARY_NAME_SUMMARY_LENGTH - 2).trimEnd() + "..."
        }

    }

}