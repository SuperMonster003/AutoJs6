package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.pref.keys.AppearanceKeys
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.app.ColorEntities.FavoriteColor
import org.autojs.autojs.theme.app.ColorHistoryItemViewHolder.Companion.ColorHistoryItem
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.presetColorItems
import org.autojs.autojs.theme.library.ColorLibraryIds
import org.autojs.autojs.theme.library.ColorLibraryRepository
import org.autojs.autojs.theme.library.ColorSelection
import org.autojs.autojs.theme.library.ColorSelectionCodec
import org.autojs.autojs.theme.usage.FavoriteSort
import org.autojs.autojs.theme.usage.Recommendation
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MtActivityUserColorLibraryBinding
import org.autojs.autojs6.databinding.MtColorLibraryRecyclerViewItemBinding

/**
 * The virtual libraries "Favorites" (roadmap P5.1) and "Frequent and recommended" (roadmap P5.4). Favorites can be
 * sorted, dragged, pinned and removed; recommendations apply the way they were chosen before.
 * zh-CN: 虚拟库 "收藏" (Roadmap P5.1) 与 "常用与推荐" (Roadmap P5.4). 收藏可排序, 拖动, 置顶与移除; 推荐条目按当初的选择方式应用.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
class VirtualColorLibraryActivity : ColorSelectBaseActivity() {

    private lateinit var binding: MtActivityUserColorLibraryBinding

    private val repository by lazy { ColorLibraryRepository.get(this) }

    private val libraryId by lazy { intent.getIntExtra(EXTRA_LIBRARY_ID, ColorLibraryIds.FAVORITES) }

    private val isFavorites get() = libraryId == ColorLibraryIds.FAVORITES

    private val adapter = VirtualColorItemAdapter(::onRowClick, ::onRowLongClick, ::isSelected)

    private val seedWizard by lazy {
        SeedLibraryWizard(this, { initial, onPicked -> showColorPickerFor(initial, onPicked) }) { IntelligentLibraryActivity.launch(this, it.libraryId) }
    }

    private var sort: FavoriteSort
        get() = FavoriteSort.of(AppearanceKeys.COLOR_FAVORITE_SORT.storedOrNull())
        set(value) = AppearanceKeys.COLOR_FAVORITE_SORT.set(value.key)

    private var reorderMenuItem: MenuItem? = null

    /** Names of preset colors by value, so favorites show a familiar name. zh-CN: 按色值索引的预设颜色名称, 使收藏显示熟悉的名称. */
    private val presetNames: Map<Int, String> by lazy {
        val names = LinkedHashMap<Int, String>()
        presetColorItems.forEach { item -> names.putIfAbsent(getColor(item.colorRes) or OPAQUE, getString(item.nameRes)) }
        names
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeChangeNotifier.themeChanged.observe(this) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
            updateSubtitle(binding.toolbar)
        }

        MtActivityUserColorLibraryBinding.inflate(layoutInflater).let {
            binding = it
            setContentView(it.root)
            setUpAppBar(it.appBar, it.appBarContainer)
            setUpToolbar(it.toolbar)
            it.toolbar.title = getString(if (isFavorites) R.string.color_library_favorites else R.string.color_library_recommended)
            it.emptyView.setText(if (isFavorites) R.string.text_no_favorites else R.string.text_no_recommendations)
        }

        binding.colorsRecyclerView.let {
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = adapter
            it.addItemDecoration(DividerItemDecoration(this, VERTICAL))
            it.excludePaddingClippableViewFromBottomNavigationBar()
            ItemTouchHelper(DragCallback()).attachToRecyclerView(it)
        }
    }

    override fun onResume() {
        super.onResume()
        load {
            val scrollTo = intent.getLongExtra(EXTRA_SCROLL_TO_FAVORITE_ID, -1L)
            if (scrollTo > 0) {
                intent.removeExtra(EXTRA_SCROLL_TO_FAVORITE_ID)
                scrollToFavorite(scrollTo)
            }
        }
    }

    override fun getSubtitle(withHexSuffix: Boolean): String? = when (currentColorSelection()) {
        is ColorSelection.Favorite -> getCurrentColorSummary(this, withIdentifierPrefix = !isFavorites, withHexSuffix = withHexSuffix)
        else -> if (isFavorites) null else getCurrentColorSummary(this, withHexSuffix = withHexSuffix)
    }

    override fun onFavoritesChanged() = load()

    private fun load(then: () -> Unit = {}) {
        lifecycleScope.launch {
            val rows = when {
                isFavorites -> {
                    val favorites = withContext(Dispatchers.IO) { repository.listFavorites() }
                    val byId = favorites.associateBy { it.id }
                    sort.sort(favorites.map(::favoriteEntryOf)).mapNotNull { byId[it.id] }.map(::favoriteRow)
                }
                else -> loadRecommendations().map { recommendation ->
                    when (recommendation) {
                        is Recommendation.Favorite -> withContext(Dispatchers.IO) { repository.findFavoriteById(recommendation.entry.id) }?.let(::favoriteRow)
                        is Recommendation.Used -> usedRow(recommendation.key)
                    }
                }.filterNotNull()
            }
            adapter.submit(rows)
            binding.emptyView.isVisible = rows.isEmpty()
            updateSubtitle(binding.toolbar)
            then()
        }
    }

    private fun favoriteRow(favorite: FavoriteColor) = VirtualColorRow(
        color = favorite.color,
        name = presetNames[favorite.color] ?: hex(favorite.color),
        identifier = when {
            favorite.pinned -> getString(R.string.text_favorite_pinned)
            isFavorites -> null
            else -> getString(R.string.color_library_identifier_favorites)
        },
        favorite = favorite,
    )

    private fun usedRow(item: ColorHistoryItem) = VirtualColorRow(
        color = item.colorInt,
        name = item.colorName ?: presetNames[item.colorInt or OPAQUE] ?: hex(item.colorInt),
        identifier = listOfNotNull(
            item.colorLibraryIdentifier,
            resources.getQuantityString(R.plurals.text_used_n_times, item.useCount, item.useCount),
        ).joinToString(" · "),
        history = item,
    )

    private fun isSelected(row: VirtualColorRow): Boolean {
        val selection = currentColorSelection()
        row.favorite?.let { return (selection as? ColorSelection.Favorite)?.favoriteId == it.id }
        val item = row.history ?: return false
        return when (selection) {
            ColorSelection.Palette -> item.libraryId == null && (item.colorInt or OPAQUE) == (ThemeColorManager.colorPrimary or OPAQUE)
            is ColorSelection.Intelligent -> false
            else -> ColorSelectionCodec.encode(selection) == (item.libraryId to item.itemId)
        }
    }

    private fun onRowClick(row: VirtualColorRow) {
        if (adapter.isReordering) return
        row.favorite?.let { return applyFavorite(it) }
        row.history?.let { applyHistoryItem(it) }
    }

    private fun onRowLongClick(row: VirtualColorRow) {
        showColorActions(
            row.color, row.name, extra = listOf(
                getString(R.string.text_apply_as_theme_color) to { onRowClick(row) },
                getString(R.string.text_add_to_color_library) to { addColorsToUserLibrary(listOf(row.color)) },
            )
        )
    }

    private fun scrollToFavorite(favoriteId: Long) {
        val position = adapter.items.indexOfFirst { it.favorite?.id == favoriteId }
        if (position >= 0) {
            (binding.colorsRecyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
        }
    }

    private fun chooseSort() {
        val sorts = FavoriteSort.entries
        MaterialDialog.Builder(this)
            .title(R.string.text_sort_by)
            .items(sorts.map { getString(sortLabelOf(it)) })
            .itemsCallbackSingleChoice(sorts.indexOf(sort)) { dialog, _, which, _ ->
                dialog.dismiss()
                if (adapter.isReordering) setReordering(false)
                sort = sorts[which]
                load()
                true
            }
            .choiceWidgetThemeColor()
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .show()
    }

    private fun setReordering(reordering: Boolean) {
        if (reordering) {
            if (adapter.items.size < 2) {
                ViewUtils.showToast(this, R.string.text_no_colors_available)
                return
            }
            // Dragging defines the manual order. zh-CN: 拖动即定义手动顺序.
            if (sort != FavoriteSort.MANUAL) {
                sort = FavoriteSort.MANUAL
                load()
            }
            ViewUtils.showToast(this, R.string.text_reorder_colors_hint)
        } else {
            val order = adapter.items.mapNotNull { it.favorite?.id }
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { repository.reorderFavorites(order) }
                load()
            }
        }
        adapter.isReordering = reordering
        reorderMenuItem?.setTitle(if (reordering) R.string.text_finish_reordering else R.string.text_reorder_colors)
    }

    private fun createIntelligentLibrary() {
        val rows = adapter.items
        seedWizard.startFromColors(
            rows.map { it.color },
            getString(if (isFavorites) R.string.color_library_favorites else R.string.color_library_recommended),
            names = rows.map { row -> row.name.takeIf { it != hex(row.color) } },
        )
    }

    private fun locateCurrentThemeColor() {
        val position = adapter.items.indexOfFirst(::isSelected)
        when {
            position >= 0 -> (binding.colorsRecyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
            else -> checkAndGetTargetInfoForThemeColorLocate()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_virtual_color_library, menu)
        reorderMenuItem = menu.findItem(R.id.action_reorder_favorites)
        listOf(R.id.action_add_favorite_from_palette, R.id.action_sort_favorites, R.id.action_reorder_favorites).forEach {
            menu.findItem(it)?.isVisible = isFavorites
        }
        menu.findItem(R.id.action_create_intelligent_library_from_colors)?.setTitle(
            if (isFavorites) R.string.text_create_intelligent_library_from_favorites else R.string.text_create_intelligent_library_from_recommendations
        )
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_add_favorite_from_palette -> true.also { showColorPickerFor(ThemeColorManager.colorPrimary) { setFavorite(it, true) } }
        R.id.action_sort_favorites -> true.also { chooseSort() }
        R.id.action_reorder_favorites -> true.also { setReordering(!adapter.isReordering) }
        R.id.action_create_intelligent_library_from_colors -> true.also { createIntelligentLibrary() }
        R.id.action_locate_current_theme_color -> true.also { locateCurrentThemeColor() }
        else -> super.onOptionsItemSelected(item)
    }

    private inner class DragCallback : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

        override fun isLongPressDragEnabled() = adapter.isReordering

        // Pinned favorites stay above the others, so a row moves only among rows of the same kind.
        // zh-CN: 置顶的收藏总在其他收藏之上, 因此条目只能在同类条目之间移动.
        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val from = adapter.items.getOrNull(viewHolder.bindingAdapterPosition)?.favorite ?: return false
            val to = adapter.items.getOrNull(target.bindingAdapterPosition)?.favorite ?: return false
            if (from.pinned != to.pinned) return false
            adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

    }

    data class VirtualColorRow(
        val color: Int,
        val name: String,
        val identifier: String?,
        val favorite: FavoriteColor? = null,
        val history: ColorHistoryItem? = null,
    )

    @SuppressLint("NotifyDataSetChanged")
    private class VirtualColorItemAdapter(
        private val onClick: (VirtualColorRow) -> Unit,
        private val onLongClick: (VirtualColorRow) -> Unit,
        private val isSelected: (VirtualColorRow) -> Boolean,
    ) : RecyclerView.Adapter<ColorItemViewHolder>() {

        val items: MutableList<VirtualColorRow> = mutableListOf()

        var isReordering = false
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorItemViewHolder {
            return ColorItemViewHolder(MtColorLibraryRecyclerViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ColorItemViewHolder, position: Int) {
            val row = items[position]
            holder.bind(color = row.color, name = row.name, identifier = row.identifier, checked = isSelected(row))
            holder.itemView.setOnClickListener { items.getOrNull(holder.bindingAdapterPosition)?.let(onClick) }
            when {
                isReordering -> holder.itemView.setOnLongClickListener(null)
                else -> holder.itemView.setOnLongClickListener { true.also { items.getOrNull(holder.bindingAdapterPosition)?.let(onLongClick) } }
            }
        }

        override fun getItemCount() = items.size

        fun submit(rows: List<VirtualColorRow>) {
            items.clear()
            items.addAll(rows)
            notifyDataSetChanged()
        }

        fun move(from: Int, to: Int) {
            if (from !in items.indices || to !in items.indices) return
            items.add(to, items.removeAt(from))
            notifyItemMoved(from, to)
        }

    }

    companion object {

        private const val EXTRA_LIBRARY_ID = "LIBRARY_ID"
        private const val EXTRA_SCROLL_TO_FAVORITE_ID = "SCROLL_TO_FAVORITE_ID"

        private const val OPAQUE = 0xFF shl 24

        private fun hex(color: Int) = String.format("#%06X", 0xFFFFFF and color)

        fun sortLabelOf(sort: FavoriteSort) = when (sort) {
            FavoriteSort.MANUAL -> R.string.text_sort_manual
            FavoriteSort.TIME -> R.string.text_sort_favorited_time
            FavoriteSort.HUE -> R.string.text_sort_hue
            FavoriteSort.LIGHTNESS -> R.string.text_sort_lightness
        }

        fun launch(context: Context, libraryId: Int, scrollToFavoriteId: Long? = null) {
            Intent(context, VirtualColorLibraryActivity::class.java)
                .putExtra(EXTRA_LIBRARY_ID, libraryId)
                .apply { scrollToFavoriteId?.let { putExtra(EXTRA_SCROLL_TO_FAVORITE_ID, it) } }
                .startSafely(context)
        }

    }

}
