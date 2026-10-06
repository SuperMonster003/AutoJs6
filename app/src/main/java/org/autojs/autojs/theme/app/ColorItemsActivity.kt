package org.autojs.autojs.theme.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.OnClickListener
import androidx.appcompat.widget.Toolbar
import androidx.core.view.forEach
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import androidx.recyclerview.widget.ThemeColorRecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.ITEM_ID_WINDOW_BACKGROUND_LIGHT
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.ITEM_ID_WINDOW_BACKGROUND_NIGHT
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.PresetColorItem
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.displayColorsOf
import org.autojs.autojs.theme.intelligent.IntelligentColorEngine
import org.autojs.autojs.theme.intelligent.IntelligentPresets
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.PresetColorLibrary
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.presetColorLibraries
import org.autojs.autojs.theme.library.ColorSelection
import org.autojs.autojs.theme.library.ColorSelectionCodec
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs.util.ViewUtils.onceGlobalLayout
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByColorLuminance
import org.autojs.autojs.util.ViewUtils.setOnSubtitleViewClickListener
import org.autojs.autojs.util.ViewUtils.setOnTitleViewClickListener
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MtActivityColorItemsBinding
import kotlin.properties.Delegates
import androidx.core.graphics.ColorUtils as AndroidColorUtils

@SuppressLint("NotifyDataSetChanged")
class ColorItemsActivity : ColorSelectBaseActivity() {

    private lateinit var binding: MtActivityColorItemsBinding

    private lateinit var mAdapter: ColorItemAdapter
    private lateinit var mLibrary: PresetColorLibrary

    private var mInitiallyItemIdScrollTo by Delegates.notNull<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeChangeNotifier.themeChanged.observe(this) {
            mAdapter.apply {
                val positionBeforeSelection = items.indexOfFirst {
                    it.libraryId == selectedLibraryId && it.itemId == selectedItemId
                }
                setUpSelectedPosition(this)
                if (positionBeforeSelection in items.indices) {
                    notifyItemChanged(positionBeforeSelection)
                }
            }
            updateClickListener(binding.toolbar)
        }

        val libraryId = intent.getIntExtra(INTENT_IDENTIFIER_LIBRARY_ID, SELECT_NONE)
        val library = presetColorLibraries.find { it.id == libraryId }?.also { lib ->
            mLibrary = lib
        } ?: throw RuntimeException("Unknown library id: $libraryId")

        mInitiallyItemIdScrollTo = intent.getIntExtra(INTENT_IDENTIFIER_COLOR_ITEM_ID_SCROLL_TO, SELECT_NONE)

        MtActivityColorItemsBinding.inflate(layoutInflater).let {
            binding = it
            setContentView(it.root)
            setUpAppBar(it.appBar, it.appBarContainer)
            it.toolbar.let { toolbar ->
                setUpToolbar(toolbar)
                toolbar.title = getString(library.titleRes)
                updateSubtitle(toolbar)
                updateClickListener(toolbar)
            }
        }

        binding.colorItemsRecyclerView.let { it ->
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = ColorItemAdapter(displayColorsOf(library), onMergedItemClick = { showBackgroundColorDialog() }).also { mAdapter = it }

            it.addItemDecoration(DividerItemDecoration(this, VERTICAL))
            it.excludePaddingClippableViewFromBottomNavigationBar()

            setUpSelectedPosition(mAdapter)

            it.onceGlobalLayout {
                scrollToPositionOnceIfNeeded(it)
            }
        }
    }

    private val isLibraryOfCurrentSelection: Boolean
        get() = when (val selection = currentColorSelection()) {
            is ColorSelection.Preset -> selection.libraryId == mLibrary.id
            is ColorSelection.Intelligent -> mLibrary.isDefault && selection.libraryId == IntelligentPresets.ID_BACKGROUND
            else -> false
        }

    /**
     * The id shown for [itemId]: the night window background is part of the merged entry.
     * zh-CN: [itemId] 在列表中对应的 id: 夜间窗口背景色属于合并条目.
     */
    private fun displayItemIdOf(itemId: Int): Int = when {
        mLibrary.isDefault && itemId == ITEM_ID_WINDOW_BACKGROUND_NIGHT -> ITEM_ID_WINDOW_BACKGROUND_LIGHT
        else -> itemId
    }

    private fun showBackgroundColorDialog() {
        val light = getColor(R.color.window_background_light)
        val dark = getColor(R.color.window_background_night)
        fun hex(color: Int) = String.format("#%06X", 0xFFFFFF and color)
        val labels = listOf(
            "${getString(R.string.text_background_color_light)}  ${hex(light)}",
            "${getString(R.string.text_background_color_dark)}  ${hex(dark)}",
            "${getString(R.string.text_background_color_follow)}  ${hex(light)} / ${hex(dark)}",
            "${getString(R.string.text_background_color_inverse)}  ${hex(dark)} / ${hex(light)}",
        )
        val selectedIndex = when (val selection = currentColorSelection()) {
            is ColorSelection.Preset -> when {
                selection.libraryId != mLibrary.id -> -1
                selection.itemId == ITEM_ID_WINDOW_BACKGROUND_LIGHT -> 0
                selection.itemId == ITEM_ID_WINDOW_BACKGROUND_NIGHT -> 1
                else -> -1
            }
            is ColorSelection.Intelligent -> when (selection.libraryId) {
                IntelligentPresets.ID_BACKGROUND -> IntelligentColorEngine.shownGroupId?.toInt()
                    ?.takeIf { IntelligentColorEngine.activeSnapshot?.libraryId == IntelligentPresets.ID_BACKGROUND } ?: -1
                else -> -1
            }
            else -> -1
        }
        MaterialDialog.Builder(this)
            .title(R.string.text_background_color)
            .content(R.string.text_choose_window_background_as_theme_color)
            .items(labels)
            .itemsCallbackSingleChoice(selectedIndex) { _, _, which, _ ->
                when (which) {
                    0 -> mLibrary.colors.find { it.itemId == ITEM_ID_WINDOW_BACKGROUND_LIGHT }?.let { applyPresetItem(it) }
                    1 -> mLibrary.colors.find { it.itemId == ITEM_ID_WINDOW_BACKGROUND_NIGHT }?.let { applyPresetItem(it) }
                    2 -> activateIntelligentLibrary(IntelligentPresets.background(this), IntelligentPresets.BACKGROUND_GROUP_FOLLOW)
                    3 -> activateIntelligentLibrary(IntelligentPresets.background(this), IntelligentPresets.BACKGROUND_GROUP_INVERSE)
                }
                true
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .show()
    }

    private fun updateClickListener(toolbar: Toolbar) {
        when {
            isLibraryOfCurrentSelection -> OnClickListener {
                showColorDetails(ThemeColorManager.colorPrimary, getSubtitle(false))
            }
            else -> null
        }.let {
            toolbar.setOnTitleViewClickListener(it)
            toolbar.setOnSubtitleViewClickListener(it)
        }
    }

    override fun getSubtitle(withHexSuffix: Boolean): String? = when {
        isLibraryOfCurrentSelection -> getCurrentColorSummary(this, withIdentifierPrefix = false, withHexSuffix = withHexSuffix)
        else -> null
    }

    private fun setUpSelectedPosition(adapter: ColorItemAdapter) {
        when (val selection = currentColorSelection()) {
            ColorSelection.Palette -> Unit
            is ColorSelection.Preset -> {
                adapter.selectedLibraryId = selection.libraryId
                adapter.selectedItemId = when (selection.libraryId) {
                    // Without a stored item (e.g. a legacy default selection), match the current theme color.
                    // zh-CN: 没有保存的条目时 (例如 legacy 的默认色选择), 按当前主题色匹配.
                    mLibrary.id -> displayItemIdOf(
                        selection.itemId
                            ?: mLibrary.colors.firstOrNull { getColor(it.colorRes) == ThemeColorManager.colorPrimary }?.itemId
                            ?: SELECT_NONE
                    )
                    else -> SELECT_NONE
                }
            }
            else -> when {
                // The background preset shows as the merged entry. zh-CN: "背景色" 预设显示为合并条目.
                mLibrary.isDefault && (selection as? ColorSelection.Intelligent)?.libraryId == IntelligentPresets.ID_BACKGROUND -> {
                    adapter.selectedLibraryId = mLibrary.id
                    adapter.selectedItemId = ITEM_ID_WINDOW_BACKGROUND_LIGHT
                }
                else -> {
                    adapter.selectedLibraryId = ColorSelectionCodec.encode(selection).first
                    adapter.selectedItemId = SELECT_NONE
                }
            }
        }
    }

    private fun scrollToPositionOnceIfNeeded(recyclerView: ThemeColorRecyclerView) {
        if (mInitiallyItemIdScrollTo < 0) return
        val targetItemId = displayItemIdOf(mInitiallyItemIdScrollTo)
        val targetPosition = mAdapter.items.indexOfFirst { it.itemId == targetItemId }
        if (targetPosition < 0) {
            mInitiallyItemIdScrollTo = -1
            return
        }
        val targetItem = mAdapter.items[targetPosition]

        if (recyclerView.findViewHolderForAdapterPosition(targetPosition) != null) {
            highlightColorItem(recyclerView, targetPosition, targetItem)
        } else {
            // @Hint by JetBrains AI Assistant (GPT-5.2) on Feb 3, 2025.
            //  ! `scrollToPositionWithOffset/scrollToPosition` may not dispatch scroll state changes,
            //  ! so relying on `SCROLL_STATE_IDLE` can miss the timing.
            //  ! zh-CN:
            //  ! `scrollToPositionWithOffset/scrollToPosition` 可能不会触发滚动状态变化,
            //  ! 因此依赖 `SCROLL_STATE_IDLE` 会错过触发时机.
            //  !
            //  # recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            //  #     override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            //  #         if (newState == RecyclerView.SCROLL_STATE_IDLE) {
            //  #             recyclerView.removeOnScrollListener(this)
            //  #             highlightColorItem(recyclerView, targetPosition, targetItem)
            //  #         }
            //  #     }
            //  # })

            // Trigger highlight when the target view is actually attached to window.
            // zh-CN: 当目标 View 真正 attach 到窗口时再触发高亮.
            val attachListener = object : RecyclerView.OnChildAttachStateChangeListener {

                override fun onChildViewAttachedToWindow(view: View) {
                    val holder = recyclerView.getChildViewHolder(view)
                    if (holder.bindingAdapterPosition == targetPosition) {
                        recyclerView.removeOnChildAttachStateChangeListener(this)
                        highlightColorItem(recyclerView, targetPosition, targetItem)
                    }
                }

                override fun onChildViewDetachedFromWindow(view: View) = Unit
            }
            recyclerView.addOnChildAttachStateChangeListener(attachListener)
        }

        // val layoutManager = recyclerView.layoutManager
        //
        // val smoothScroller = object : LinearSmoothScroller(this) {
        //     override fun getVerticalSnapPreference() = SNAP_TO_START
        // }.apply { this.targetPosition = targetPosition }
        //
        // when (layoutManager) {
        //     null -> recyclerView.smoothScrollToPosition(targetPosition)
        //     is GridLayoutManager -> layoutManager.startSmoothScroll(smoothScroller)
        //     is LinearLayoutManager -> layoutManager.startSmoothScroll(smoothScroller)
        //     else -> layoutManager.startSmoothScroll(smoothScroller)
        // }

        when (val layoutManager = recyclerView.layoutManager) {
            null -> recyclerView.scrollToPosition(targetPosition)
            is GridLayoutManager -> layoutManager.scrollToPositionWithOffset(targetPosition, 0)
            is LinearLayoutManager -> layoutManager.scrollToPositionWithOffset(targetPosition, 0)
            else -> layoutManager.scrollToPosition(targetPosition)
        }

        mInitiallyItemIdScrollTo = -1
    }

    private fun highlightColorItem(recyclerView: RecyclerView, targetPosition: Int, targetItem: PresetColorItem) {
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(targetPosition)
        viewHolder?.itemView?.let { itemView ->
            val originalColor = (itemView.background as? ColorDrawable)?.color ?: getColor(R.color.window_background)

            val highlightColor = ColorUtils.adjustColorForContrast(
                originalColor, getColor(targetItem.colorRes), 1.6,
            )

            when (val tag = itemView.tag) {
                !is ValueAnimator -> {
                    startAnimateForSetBackgroundColor(itemView, originalColor, highlightColor, 300, true)
                }
                else -> tag.cancel()
            }
        }
    }

    private fun startAnimateForSetBackgroundColor(view: View, fromColor: Int, toColor: Int, duration: Long, reverse: Boolean = false) {
        val alpha = 0.3
        val animator = ValueAnimator.ofObject(
            ArgbEvaluator(),
            fromColor.let { AndroidColorUtils.setAlphaComponent(it, (alpha * 255).toInt()) },
            toColor.let { AndroidColorUtils.setAlphaComponent(it, (alpha * 255).toInt()) },
        )
        animator.duration = duration
        animator.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Int
            view.setBackgroundColor(animatedValue)
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (reverse) {
                    view.postDelayed({
                        if (view.tag == null) return@postDelayed
                        startAnimateForSetBackgroundColor(view, toColor, fromColor, 1300)
                    }, 1000)
                } else {
                    view.tag = null
                }
            }

            override fun onAnimationCancel(animation: Animator) {
                view.tag = null
                view.setBackgroundColor(getColor(R.color.window_background))
            }
        })
        view.tag = animator

        animator.start()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_color_items, menu)

        binding.toolbar.setMenuIconsColorByColorLuminance(this, currentColor)

        setUpSearchMenu(
            menu,
            onQueryTextSimpleListener = { query -> filterColorsFromColorItems(query, displayColorsOf(mLibrary), mAdapter) },
            onMenuItemActionExpand = { menu.forEach { it.isVisible = it.isVisible.not() } },
            onMenuItemActionCollapse = { menu.forEach { it.isVisible = it.isVisible.not() } },
        )

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_locate_current_theme_color -> {
            locateCurrentThemeColor()
            true
        }
        R.id.action_show_history -> {
            showColorHistory(mLibrary.id) { selectedHistoryItem ->
                val item = selectedHistoryItem.itemId?.let { id -> mLibrary.colors.find { it.itemId == id } }
                    ?: mLibrary.colors.find { getColor(it.colorRes) == selectedHistoryItem.colorInt }
                when (item) {
                    null -> ViewUtils.showToast(this, R.string.error_failed_to_apply_current_color_history, true)
                    else -> applyPresetItem(item)
                }
            }
            true
        }
        R.id.action_color_search_help -> {
            showColorSearchHelp()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun locateCurrentThemeColor() {
        val target = checkAndGetTargetInfoForThemeColorLocate() ?: return
        val targetLibraryId = target.libraryId
        val targetIndex = target.libraryItemId
        when (targetLibraryId) {
            mLibrary.id -> {
                val recyclerView = binding.colorItemsRecyclerView
                val targetPosition = displayItemIdOf(targetIndex).let { id -> mAdapter.items.indexOfFirst { it.itemId == id } }
                val viewHolder = recyclerView.findViewHolderForAdapterPosition(targetPosition)

                viewHolder?.itemView?.let { itemView ->
                    (itemView.tag as? ValueAnimator)?.cancel()
                    itemView.setBackgroundColor(getColor(R.color.window_background))
                }

                mInitiallyItemIdScrollTo = targetIndex
                scrollToPositionOnceIfNeeded(recyclerView)
            }
            else -> {
                val intent = Intent(this, ColorItemsActivity::class.java).apply {
                    putExtra(INTENT_IDENTIFIER_LIBRARY_ID, targetLibraryId)
                    putExtra(INTENT_IDENTIFIER_COLOR_ITEM_ID_SCROLL_TO, targetIndex)
                }
                startActivity(intent)
                this.finish()
            }
        }
    }

}