package org.autojs.autojs.theme.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import androidx.recyclerview.widget.ThemeColorRecyclerView
import org.autojs.autojs.core.image.ColorItems
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_DEFAULT_COLORS_ID
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_MATERIAL_COLORS_ID
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.ColorItem
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.ColorLibrary
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.colorLibraries
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
    private lateinit var mLibrary: ColorLibrary

    private var mInitiallyIdScrollTo by Delegates.notNull<Int>()

    private var mSelectedPosition = SELECT_NONE
    private var mSelectedColor: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val libraryId = intent.getIntExtra(INTENT_IDENTIFIER_LIBRARY_ID, -1)
        val library = colorLibraries.find { it.id == libraryId }?.also { lib ->
            mLibrary = lib
        } ?: throw RuntimeException("Unknown library id: $libraryId")

        mInitiallyIdScrollTo = intent.getIntExtra(INTENT_IDENTIFIER_COLOR_ITEM_ID_SCROLL_TO, -1)

        MtActivityColorLibraryBinding.inflate(layoutInflater).let {
            binding = it
            setContentView(it.root)
            setUpToolbar(it.toolbar)
            setUpAppBar(it.appBar, it.appBarContainer)
            it.toolbar.title = library.let {
                it.titleString
                    ?: it.titleRes?.let { resId -> getString(resId) }
                    ?: it.nameString
                    ?: getString(it.nameRes)
            }
            if (library.id == Pref.getInt(KEY_SELECTED_COLOR_LIBRARY_ID, SELECT_NONE)) {
                it.toolbar.subtitle = getCurrentColorSummary(this, true)
            }
        }

        binding.colorLibraryRecyclerView.let { it ->
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = ColorItemAdapter(library.colors) { colorItem, itemView ->
                savePrefsForLibraries(library, colorItem)
                savePrefsForLegacy(library, colorItem)

                it.post { binding.toolbar.subtitle = getCurrentColorSummary(this, true) }

                val selectedColor = getColor(colorItem.colorRes).also { mSelectedColor = it }
                setColorWithAnimation(selectedColor)

                val adapter = it.adapter as ColorItemAdapter
                val currentPosition = it.getChildViewHolder(itemView).bindingAdapterPosition
                if (mSelectedPosition != SELECT_NONE) {
                    adapter.notifyItemChanged(mSelectedPosition)
                    mSelectedPosition = currentPosition
                    adapter.notifyItemChanged(currentPosition)
                } else {
                    mSelectedPosition = currentPosition
                    adapter.notifyDataSetChanged()
                }

                ThemeColorManager.setThemeColor(selectedColor)
                ThemeChangeNotifier.notifyThemeChanged()
            }.also { mAdapter = it }

            it.addItemDecoration(DividerItemDecoration(this, VERTICAL))
            ViewUtils.excludePaddingClippableViewFromNavigationBar(it)

            setUpSelectedPosition()

            it.onceGlobalLayout {
                scrollToPositionOnceIfNeeded(it)
            }
        }
    }

    private fun setUpSelectedPosition() {
        when (Pref.getInt(KEY_SELECTED_COLOR_LIBRARY_ID, SELECT_NONE)) {
            SELECT_NONE -> when (val legacyIndex = Pref.getInt(KEY_LEGACY_SELECTED_COLOR_INDEX, SELECT_NONE)) {
                customColorPosition -> Unit
                SELECT_NONE, defaultColorPosition -> when (mLibrary.id) {
                    COLOR_LIBRARY_DEFAULT_COLORS_ID -> {
                        val index = mLibrary.colors.indexOfFirst { getColor(it.colorRes) == getColor(R.color.theme_color_default) }
                        if (index >= 0) mSelectedPosition = index
                    }
                }
                else -> when (val calculatedIndex = legacyIndex - maxOf(customColorPosition, defaultColorPosition) - 1) {
                    in ColorItems.MATERIAL_COLORS.indices -> {
                        val (colorRes, _) = ColorItems.MATERIAL_COLORS[calculatedIndex]
                        val index = mLibrary.colors.indexOfFirst { it.colorRes == colorRes }
                        if (index >= 0) mSelectedPosition = index
                    }
                }
            }
            mLibrary.id -> {
                val libraryItemId = Pref.getInt(KEY_SELECTED_COLOR_LIBRARY_ITEM_ID, SELECT_NONE)
                mSelectedPosition = mLibrary.colors.indexOfFirst { it.id == libraryItemId }.takeIf { it != SELECT_NONE } ?: SELECT_NONE
            }
        }
    }

    private fun scrollToPositionOnceIfNeeded(recyclerView: ThemeColorRecyclerView) {
        if (mInitiallyIdScrollTo < 0) return
        val targetPosition = mLibrary.colors.indexOfFirst { it.id == mInitiallyIdScrollTo }
        if (targetPosition < 0) {
            mInitiallyIdScrollTo = -1
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

        mInitiallyIdScrollTo = -1
    }

    private fun highlightColorItem(recyclerView: RecyclerView, targetPosition: Int, targetItem: ColorItem) {
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

    private fun savePrefsForLibraries(library: ColorLibrary?, colorItem: ColorItem) {
        Pref.putInt(KEY_SELECTED_COLOR_LIBRARY_ID, library?.id ?: -1)
        Pref.putInt(KEY_SELECTED_COLOR_LIBRARY_ITEM_ID, colorItem.id)
    }

    private fun savePrefsForLegacy(library: ColorLibrary?, colorItem: ColorItem) {
        when (library?.id) {
            COLOR_LIBRARY_DEFAULT_COLORS_ID -> {
                if (getColor(colorItem.colorRes) == getColor(R.color.theme_color_default)) {
                    Pref.putInt(KEY_LEGACY_SELECTED_COLOR_INDEX, defaultColorPosition)
                } else {
                    Pref.putInt(KEY_LEGACY_SELECTED_COLOR_INDEX, SELECT_NONE)
                }
            }
            COLOR_LIBRARY_MATERIAL_COLORS_ID -> {
                val index = colorItem.id
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_color_library, menu)

        binding.toolbar.setMenuIconsColorByColorLuminance(this, currentColor)

        val searchItem = menu.findItem(R.id.action_search_color)
        val searchView = searchItem.actionView as? SearchView
        searchView?.queryHint = "搜索颜色"
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterColors(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterColors(newText)
                return true
            }
        })
        return true
    }

    private fun filterColors(@Suppress("unused") query: String?) {
        // if (library == null) return
        // val filteredColors = if (query.isNullOrBlank()) {
        //     library!!.colors
        // } else {
        //     library!!.colors.filter { colorItem ->
        //         // 假设可以通过资源 id 获取对应的字符串显示，再做匹配
        //         val name = getString(colorItem.nameRes)
        //         name.contains(query, ignoreCase = true)
        //     }
        // }
        // adapter.updateData(filteredColors)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_search_color -> {
            ViewUtils.showToast(this, R.string.text_under_development_title)
            true
        }
        R.id.action_locate_current_theme_color -> {
            locateCurrentThemeColor()
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

                mInitiallyIdScrollTo = targetIndex
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

    inner class ColorItemAdapter(
        private var items: List<ColorItem>,
        private val onItemClick: (ColorItem, View) -> Unit,
    ) : RecyclerView.Adapter<ColorViewHolder>() {

        fun updateData(newItems: List<ColorItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.mt_color_library_recycler_view_item, parent, false)
            return ColorViewHolder(view)
        }

        override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
            val item = items[position]

            when (val tag = holder.itemView.tag) {
                is ValueAnimator -> {
                    tag.cancel()
                    holder.itemView.tag = null
                    holder.itemView.setBackgroundColor(getColor(R.color.window_background))
                }
            }

            holder.bind(item, position)
            holder.itemView.setOnClickListener { onItemClick(item, holder.itemView) }
        }

        override fun getItemCount() = items.size

    }

    inner class ColorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val colorView: ImageView = itemView.findViewById(R.id.color)
        private val nameView: TextView = itemView.findViewById(R.id.name)
        private val descView: TextView = itemView.findViewById(R.id.description)

        fun bind(item: ColorItem, position: Int) {
            val color = getColor(item.colorRes)
            ThemeColorHelper.setBackgroundColor(colorView, color)
            nameView.text = item.nameString ?: getString(item.nameRes)
            descView.text = String.format("#%06X", 0xFFFFFF and color)
            setChecked(color, mSelectedPosition == position)
        }

        fun setChecked(@ColorInt color: Int, checked: Boolean) {
            if (checked) {
                colorView.setImageResource(R.drawable.mt_ic_check_white_36dp)
                val tintRes = if (ViewUtils.isLuminanceLight(color)) R.color.day else R.color.night
                colorView.imageTintList = ColorStateList.valueOf(getColor(tintRes))
            } else {
                colorView.setImageDrawable(null)
            }
        }

    }

}