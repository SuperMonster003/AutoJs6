package org.autojs.autojs.theme.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.app.ColorEntities.ColorLibrary
import org.autojs.autojs.theme.app.ColorEntities.LibraryColor
import org.autojs.autojs.theme.library.ColorLibraryRepository
import org.autojs.autojs.theme.library.ColorSelection
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MtActivityUserColorLibraryBinding

/**
 * Colors of one user (plain) color library: apply, add, edit, reorder and delete (roadmap P1.1).
 * zh-CN: 单个用户 (普通) 颜色库中的颜色: 应用, 添加, 编辑, 调整顺序与删除 (Roadmap P1.1).
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
class UserColorLibraryActivity : ColorSelectBaseActivity() {

    private lateinit var binding: MtActivityUserColorLibraryBinding

    private val repository by lazy { ColorLibraryRepository.get(this) }

    private val adapter = UserColorItemAdapter(::onColorClick, ::onColorLongClick)

    private val libraryRowId by lazy { intent.getLongExtra(EXTRA_LIBRARY_ROW_ID, -1L) }

    private var library: ColorLibrary? = null
    private var allColors: List<LibraryColor> = emptyList()
    private var query: String? = null
    private var searchMenuItem: MenuItem? = null
    private var reorderMenuItem: MenuItem? = null

    private val selectedColorIdInThisLibrary: Long?
        get() = (currentColorSelection() as? ColorSelection.UserColor)?.takeIf { it.libraryRowId == libraryRowId }?.colorRowId

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (libraryRowId <= 0L) {
            finish()
            return
        }

        ThemeChangeNotifier.themeChanged.observe(this) { refreshSelection() }

        MtActivityUserColorLibraryBinding.inflate(layoutInflater).let {
            binding = it
            setContentView(it.root)
            setUpAppBar(it.appBar, it.appBarContainer)
            setUpToolbar(it.toolbar)
        }

        binding.colorsRecyclerView.let {
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = adapter
            it.addItemDecoration(DividerItemDecoration(this, VERTICAL))
            it.excludePaddingClippableViewFromBottomNavigationBar()
            ItemTouchHelper(DragCallback()).attachToRecyclerView(it)
        }

        load {
            if (intent.hasExtra(EXTRA_SCROLL_TO_COLOR_ID)) scrollToColor(intent.getLongExtra(EXTRA_SCROLL_TO_COLOR_ID, -1L))
        }
    }

    override fun getSubtitle(withHexSuffix: Boolean): String? = when (selectedColorIdInThisLibrary) {
        null -> null
        else -> getCurrentColorSummary(this, withIdentifierPrefix = false, withHexSuffix = withHexSuffix)
    }

    private fun load(then: () -> Unit = {}) {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { repository.findLibrary(libraryRowId) }
            if (loaded == null || loaded.isIntelligent) {
                finish()
                return@launch
            }
            library = loaded
            allColors = withContext(Dispatchers.IO) { repository.listColors(libraryRowId) }
            binding.toolbar.title = loaded.name
            updateSubtitle(binding.toolbar)
            applyFilter()
            then()
        }
    }

    private fun applyFilter() {
        val text = query?.trim()
        val shown = when {
            text.isNullOrEmpty() -> allColors
            else -> allColors.filter { matches(it, text) }
        }
        adapter.selectedColorId = selectedColorIdInThisLibrary
        adapter.submit(shown)
        binding.emptyView.isVisible = allColors.isEmpty()
    }

    private fun matches(color: LibraryColor, text: String): Boolean {
        val hex = String.format("#%06X", 0xFFFFFF and color.color)
        val name = color.name.orEmpty()
        return when {
            text.startsWith("#") -> searchMatch(text.drop(1), hex) { hex.contains(text, ignoreCase = true) }
            else -> searchMatch(text, name) { name.contains(text, ignoreCase = true) || hex.contains(text, ignoreCase = true) }
        }
    }

    private fun refreshSelection() {
        adapter.selectedColorId = selectedColorIdInThisLibrary
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
    }

    private fun onColorClick(color: LibraryColor) {
        if (adapter.isReordering) return
        val library = library ?: return
        applyUserLibraryColor(library.name, color)
    }

    private fun onColorLongClick(color: LibraryColor) {
        val title = color.name ?: String.format("#%06X", 0xFFFFFF and color.color)
        showColorActions(
            color.color, title, extra = listOf(
                getString(R.string.text_rename) to { renameColor(color) },
                getString(R.string.text_change_color_value) to { changeColorValue(color) },
                getString(R.string.text_delete) to {
                    ColorLibraryDialogs.showConfirm(this, getString(R.string.text_delete_color_confirm, title)) {
                        deleteColors(listOf(color.id))
                    }
                },
            )
        )
    }

    private fun renameColor(color: LibraryColor) {
        ColorLibraryDialogs.showNameInput(this, R.string.text_rename, color.name, R.string.text_color_name_optional, allowEmpty = true) { name ->
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { repository.updateColor(color, name, color.color) }
                if (color.id == selectedColorIdInThisLibrary) {
                    savePrefsForSelectionNames(library?.name, name)
                    updateSubtitle(binding.toolbar)
                }
                load()
            }
        }
    }

    private fun changeColorValue(color: LibraryColor) {
        showColorPickerFor(color.color) { value ->
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { repository.updateColor(color, color.name, value) }
                load {
                    // The changed color is the theme color: apply the new value. zh-CN: 修改的正是当前主题色: 应用新值.
                    if (color.id == selectedColorIdInThisLibrary) {
                        allColors.find { it.id == color.id }?.let { onColorClick(it) }
                    }
                }
            }
        }
    }

    private fun deleteColors(ids: List<Long>) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { repository.deleteColors(libraryRowId, ids) }
            if (selectedColorIdInThisLibrary in ids) fallBackToPaletteSelection(this@UserColorLibraryActivity)
            load()
        }
    }

    private fun addColors(colors: List<ColorLibraryRepository.NewColor>) {
        if (colors.isEmpty()) return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { repository.addColors(libraryRowId, colors) }
            load { binding.colorsRecyclerView.scrollToPosition(adapter.itemCount - 1) }
        }
    }

    private fun addColorsFromLibrary() {
        lifecycleScope.launch {
            val sources = listColorSources(excludeUserLibraryId = libraryRowId)
            ColorLibraryDialogs.showSingleChoice(this@UserColorLibraryActivity, R.string.text_choose_color_library, sources.map { it.name }) { which ->
                lifecycleScope.launch {
                    val colors = sources[which].loadColors()
                    chooseColorsToAdd(colors)
                }
            }
        }
    }

    private fun addColorsFromHistory() {
        lifecycleScope.launch {
            val colors = loadColorHistoryItems(null)
                .distinctBy { it.colorInt and 0xFFFFFF }
                .map { ColorLibraryRepository.NewColor(it.colorInt, it.colorName) }
            chooseColorsToAdd(colors)
        }
    }

    private fun chooseColorsToAdd(colors: List<ColorLibraryRepository.NewColor>) {
        if (colors.isEmpty()) {
            ViewUtils.showToast(this, R.string.text_no_colors_available)
            return
        }
        val labels = colors.map { color ->
            val hex = String.format("#%06X", 0xFFFFFF and color.color)
            color.name?.let { "$it  $hex" } ?: hex
        }
        ColorLibraryDialogs.showMultiChoice(this, R.string.text_choose_colors_to_add, labels) { indices ->
            addColors(indices.map { colors[it] })
        }
    }

    private fun setReordering(reordering: Boolean) {
        if (reordering) {
            searchMenuItem?.collapseActionView()
            ViewUtils.showToast(this, R.string.text_reorder_colors_hint)
        } else {
            val order = adapter.items.map { it.id }
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { repository.reorderColors(libraryRowId, order) }
                load()
            }
        }
        adapter.isReordering = reordering
        reorderMenuItem?.setTitle(if (reordering) R.string.text_finish_reordering else R.string.text_reorder_colors)
    }

    private fun locateCurrentThemeColor() {
        val colorId = selectedColorIdInThisLibrary ?: run {
            checkAndGetTargetInfoForThemeColorLocate()
            return
        }
        searchMenuItem?.collapseActionView()
        scrollToColor(colorId)
    }

    private fun scrollToColor(colorId: Long) {
        val position = adapter.items.indexOfFirst { it.id == colorId }
        if (position >= 0) {
            (binding.colorsRecyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_user_color_library, menu)
        searchMenuItem = menu.findItem(R.id.action_search_color)
        reorderMenuItem = menu.findItem(R.id.action_reorder_colors)
        setUpSearchMenu(
            menu,
            onQueryTextSimpleListener = { text ->
                query = text
                applyFilter()
            },
            onMenuItemActionExpand = {
                if (adapter.isReordering) setReordering(false)
                menu.forEach { if (it.itemId != R.id.action_search_color) it.isVisible = false }
            },
            onMenuItemActionCollapse = { menu.forEach { it.isVisible = true } },
        )
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_add_colors_from_palette -> true.also {
            showColorPickerFor(ThemeColorManager.colorPrimary) { addColors(listOf(ColorLibraryRepository.NewColor(it))) }
        }
        R.id.action_add_colors_from_library -> true.also { addColorsFromLibrary() }
        R.id.action_add_colors_from_history -> true.also { addColorsFromHistory() }
        R.id.action_locate_current_theme_color -> true.also { locateCurrentThemeColor() }
        R.id.action_reorder_colors -> true.also { setReordering(!adapter.isReordering) }
        R.id.action_delete_colors -> true.also { chooseColorsToDelete() }
        R.id.action_rename_color_library -> true.also { renameLibrary() }
        R.id.action_delete_color_library -> true.also { deleteLibrary() }
        else -> super.onOptionsItemSelected(item)
    }

    private fun chooseColorsToDelete() {
        if (allColors.isEmpty()) {
            ViewUtils.showToast(this, R.string.text_no_colors_available)
            return
        }
        val labels = allColors.map { color ->
            val hex = String.format("#%06X", 0xFFFFFF and color.color)
            color.name?.let { "$it  $hex" } ?: hex
        }
        ColorLibraryDialogs.showMultiChoice(this, R.string.text_delete_colors, labels) { indices ->
            ColorLibraryDialogs.showConfirm(this, getString(R.string.text_delete_colors_confirm, indices.size)) {
                deleteColors(indices.map { allColors[it].id })
            }
        }
    }

    private fun renameLibrary() {
        val current = library ?: return
        ColorLibraryDialogs.showNameInput(this, R.string.text_rename_color_library, current.name) { name ->
            name ?: return@showNameInput
            lifecycleScope.launch {
                val stored = withContext(Dispatchers.IO) { repository.renameLibrary(current.id, name) }
                if (selectedColorIdInThisLibrary != null) {
                    savePrefsForSelectionNames(stored, MiscStateKeys.COLOR_SELECT_ITEM_NAME.storedOrNull())
                }
                load()
            }
        }
    }

    private fun deleteLibrary() {
        val current = library ?: return
        ColorLibraryDialogs.showConfirm(this, getString(R.string.text_delete_color_library_confirm, current.name)) {
            lifecycleScope.launch {
                val wasSelected = selectedColorIdInThisLibrary != null
                withContext(Dispatchers.IO) { repository.deleteLibrary(current) }
                if (wasSelected) fallBackToPaletteSelection(this@UserColorLibraryActivity)
                finish()
            }
        }
    }

    private inner class DragCallback : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

        override fun isLongPressDragEnabled() = adapter.isReordering

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

    }

    companion object {

        private const val EXTRA_LIBRARY_ROW_ID = "LIBRARY_ROW_ID"
        private const val EXTRA_SCROLL_TO_COLOR_ID = "SCROLL_TO_COLOR_ID"

        fun launch(context: Context, libraryRowId: Long, scrollToColorId: Long? = null) {
            Intent(context, UserColorLibraryActivity::class.java)
                .putExtra(EXTRA_LIBRARY_ROW_ID, libraryRowId)
                .apply { scrollToColorId?.let { putExtra(EXTRA_SCROLL_TO_COLOR_ID, it) } }
                .startSafely(context)
        }

    }

}
