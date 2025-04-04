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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import androidx.recyclerview.widget.ThemeColorRecyclerView
import org.autojs.autojs.core.image.ColorItems
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_DEFAULT
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_MATERIAL
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.PresetColorItem
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.PresetColorLibrary
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.presetColorLibraries
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.onceGlobalLayout
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByColorLuminance
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MtActivityColorLibraryBinding
import kotlin.properties.Delegates
import androidx.core.graphics.ColorUtils as AndroidColorUtils

@SuppressLint("NotifyDataSetChanged")
class ColorLibraryActivity : ColorSelectBaseActivity() {

    private lateinit var binding: MtActivityColorLibraryBinding

    private lateinit var mAdapter: ColorItemAdapter
    private lateinit var mLibrary: PresetColorLibrary

    private var mInitiallyItemIdScrollTo by Delegates.notNull<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeChangeNotifier.themeChanged.observe(this) {
            updateClickListener(binding.toolbar)
        }

        val libraryId = intent.getIntExtra(INTENT_IDENTIFIER_LIBRARY_ID, -1)
        val library = presetColorLibraries.find { it.id == libraryId }?.also { lib ->
            mLibrary = lib
        } ?: throw RuntimeException("Unknown library id: $libraryId")

        mInitiallyItemIdScrollTo = intent.getIntExtra(INTENT_IDENTIFIER_COLOR_ITEM_ID_SCROLL_TO, -1)

        MtActivityColorLibraryBinding.inflate(layoutInflater).let {
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

        binding.colorLibraryRecyclerView.let { it ->
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = ColorItemAdapter(library.colors) { colorItem, itemView ->
                updateAppBarColorContent(getColor(colorItem.colorRes))
            }.also { mAdapter = it }

            it.addItemDecoration(DividerItemDecoration(this, VERTICAL))
            ViewUtils.excludePaddingClippableViewFromNavigationBar(it)

            setUpSelectedPosition(mAdapter)

            it.onceGlobalLayout {
                scrollToPositionOnceIfNeeded(it)
            }
        }
    }

    private fun updateClickListener(toolbar: Toolbar) {
        when (mLibrary.id) {
            Pref.getInt(KEY_SELECTED_COLOR_LIBRARY_ID, SELECT_NONE) -> object : OnClickListener {
                override fun onClick(v: View?) {
                    showColorDetails(ThemeColorManager.colorPrimary, getSubtitle(false))
                }
            }
            else -> null
        }.let { toolbar.setOnClickListener(it) }
    }

    override fun getSubtitle(withHexSuffix: Boolean): String? = when (mLibrary.id) {
        Pref.getInt(KEY_SELECTED_COLOR_LIBRARY_ID, SELECT_NONE) -> {
            getCurrentColorSummary(this, withIdentifierPrefix = false, withHexSuffix = withHexSuffix)
        }
        else -> null
    }

    private fun setUpSelectedPosition(adapter: ColorItemAdapter) {
        when (Pref.getInt(KEY_SELECTED_COLOR_LIBRARY_ID, SELECT_NONE)) {
            SELECT_NONE -> when (val legacyIndex = Pref.getInt(KEY_LEGACY_SELECTED_COLOR_INDEX, SELECT_NONE)) {
                customColorPosition -> Unit
                SELECT_NONE, defaultColorPosition -> when (mLibrary.id) {
                    COLOR_LIBRARY_ID_DEFAULT -> {
                        adapter.selectedLibraryId = COLOR_LIBRARY_ID_DEFAULT
                        adapter.selectedItemId = mLibrary.colors.firstOrNull { getColor(it.colorRes) == getColor(R.color.theme_color_default) }?.itemId ?: SELECT_NONE
                    }
                }
                else -> when (val calculatedIndex = legacyIndex - maxOf(customColorPosition, defaultColorPosition) - 1) {
                    in ColorItems.MATERIAL_COLORS.indices -> {
                        val (colorRes, _) = ColorItems.MATERIAL_COLORS[calculatedIndex]
                        adapter.selectedLibraryId = COLOR_LIBRARY_ID_MATERIAL
                        adapter.selectedItemId = mLibrary.colors.firstOrNull { it.colorRes == colorRes }?.itemId ?: SELECT_NONE
                    }
                }
            }
            mLibrary.id -> {
                adapter.selectedLibraryId = mLibrary.id
                adapter.selectedItemId = Pref.getInt(KEY_SELECTED_COLOR_LIBRARY_ITEM_ID, SELECT_NONE)
            }
        }
    }

    private fun scrollToPositionOnceIfNeeded(recyclerView: ThemeColorRecyclerView) {
        if (mInitiallyItemIdScrollTo < 0) return
        val targetPosition = mLibrary.colors.indexOfFirst { it.itemId == mInitiallyItemIdScrollTo }
        if (targetPosition < 0) {
            mInitiallyItemIdScrollTo = -1
            return
        }
        val targetItem = mLibrary.colors[targetPosition]

        if (recyclerView.findViewHolderForAdapterPosition(targetPosition) != null) {
            highlightColorItem(recyclerView, targetPosition, targetItem)
        } else {
            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        recyclerView.removeOnScrollListener(this)
                        highlightColorItem(recyclerView, targetPosition, targetItem)
                    }
                }
            })
        }

        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
        layoutManager?.let { manager ->
            val smoothScroller = object : LinearSmoothScroller(this) {
                override fun getVerticalSnapPreference() = SNAP_TO_START
            }.apply { this.targetPosition = targetPosition }
            manager.startSmoothScroll(smoothScroller)
        } ?: recyclerView.smoothScrollToPosition(targetPosition)

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
        menuInflater.inflate(R.menu.menu_color_library, menu)

        binding.toolbar.setMenuIconsColorByColorLuminance(this, currentColor)

        setUpSearchMenu(
            menu,
            onQueryTextSimpleListener = { query -> filterColorsFromColorItems(query, mLibrary.colors, mAdapter) },
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
                val recyclerView = binding.colorLibraryRecyclerView
                val viewHolder = recyclerView.findViewHolderForAdapterPosition(targetIndex)

                viewHolder?.itemView?.let { itemView ->
                    (itemView.tag as? ValueAnimator)?.cancel()
                    itemView.setBackgroundColor(getColor(R.color.window_background))
                }

                mInitiallyItemIdScrollTo = targetIndex
                scrollToPositionOnceIfNeeded(recyclerView)
            }
            else -> {
                val intent = Intent(this, ColorLibraryActivity::class.java).apply {
                    putExtra(INTENT_IDENTIFIER_LIBRARY_ID, targetLibraryId)
                    putExtra(INTENT_IDENTIFIER_COLOR_ITEM_ID_SCROLL_TO, targetIndex)
                }
                startActivity(intent)
                this.finish()
            }
        }
    }

}