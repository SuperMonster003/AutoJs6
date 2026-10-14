package org.autojs.autojs.ui.explorer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.DisplayUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ExplorerViewBinding
import kotlin.math.roundToInt

/** Everything needed to render the current multi-selection actions. */
internal data class ExplorerSelectionToolbarState(
    val selectedCount: Int,
    val archiveSelection: Boolean,
    val archiveExtraction: Boolean,
    val copyEnabled: Boolean,
    val moveEnabled: Boolean,
    val deleteEnabled: Boolean,
    val pluginLabel: String?,
    val pluginEnabled: Boolean,
    /** More than one plugin action matches: the plugin button runs the first, "More" lists the others. */
    val morePluginActions: Boolean = false,
)

/**
 * Owns selection-toolbar presentation, animation, insets and long-press touch suppression.
 *
 * Selection semantics remain in [ExplorerSelectionController], while [ExplorerView] resolves the
 * actions. This class is deliberately limited to UI state and window integration.
 */
internal class ExplorerSelectionToolbarController(
    private val context: Context,
    private val binding: ExplorerViewBinding,
    private val recyclerView: RecyclerView,
    private val isSelectionActive: () -> Boolean,
    private val setRefreshEnabled: (Boolean) -> Unit,
    private val onClose: () -> Unit,
    private val onCopy: () -> Unit,
    private val onMove: () -> Unit,
    private val onPluginAction: () -> Unit,
    private val onMorePluginActions: () -> Unit,
    private val onDelete: () -> Unit,
    private val onShownChanged: (Boolean) -> Unit,
) {

    private var shown = false
    private var navigationBarColorApplied = false
    private var blockListScrollUntilTouchRelease = false
    private var navigationBarInsetBottom = 0
    private var extraListPaddingBottom = 0
    private var listClipToPadding = false

    init {
        setupActions()
        setupTouchBlocker()
        applyTheme()
    }

    fun render(state: ExplorerSelectionToolbarState) {
        binding.selectionCount.text = context.getString(R.string.text_selected_items, state.selectedCount)
        binding.selectionCopy.isVisible = !state.archiveSelection
        binding.selectionMove.isVisible = !state.archiveSelection
        binding.selectionDelete.isVisible = !state.archiveSelection || state.deleteEnabled
        updateActionState(binding.selectionCopy, state.copyEnabled)
        updateActionState(binding.selectionMove, state.moveEnabled)
        updateActionState(binding.selectionDelete, state.deleteEnabled)
        binding.selectionPlugin.isVisible = state.archiveExtraction || state.pluginLabel != null
        if (state.archiveExtraction) {
            val label = context.getString(R.string.text_extract)
            binding.selectionPluginText.text = label
            binding.selectionPlugin.contentDescription = label
            binding.selectionPluginIcon.setImageResource(R.drawable.ic_unarchive_black_48dp)
        } else {
            binding.selectionPluginIcon.setImageResource(R.drawable.ic_archive_black_48dp)
            state.pluginLabel?.let { label ->
                binding.selectionPluginText.text = label
                binding.selectionPlugin.contentDescription = label
            }
        }
        updateActionState(binding.selectionPlugin, state.pluginEnabled)
        binding.selectionPluginMore.isVisible = !state.archiveSelection && state.morePluginActions
        setShown(state.selectedCount > 0)
    }

    fun configureListBottomPadding(extraPaddingBottomDp: Float, clipToPadding: Boolean) {
        extraListPaddingBottom = DisplayUtils.dpToPx(extraPaddingBottomDp).roundToInt()
        listClipToPadding = clipToPadding
        recyclerView.clipToPadding = clipToPadding
        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { _, insets ->
            navigationBarInsetBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            recyclerView.clipToPadding = listClipToPadding
            updateToolbarBottomInset()
            updateListBottomPadding()
            insets
        }
        ViewCompat.requestApplyInsets(recyclerView)
        recyclerView.post { ViewCompat.requestApplyInsets(recyclerView) }
    }

    fun blockListScroll() {
        blockListScrollUntilTouchRelease = true
        setRefreshEnabled(false)
        recyclerView.stopScroll()
        recyclerView.parent?.requestDisallowInterceptTouchEvent(true)
    }

    fun unblockListScroll() {
        if (!blockListScrollUntilTouchRelease) return
        blockListScrollUntilTouchRelease = false
        setRefreshEnabled(true)
        recyclerView.parent?.requestDisallowInterceptTouchEvent(false)
    }

    fun applyTheme(themeColor: ThemeColor = ThemeColorManager.currentThemeColor) {
        val backgroundColor = themeColor.colorPrimary
        val foregroundColor = ViewUtils.getDayOrNightColorByLuminance(context, backgroundColor)
        val foregroundTint = ColorStateList.valueOf(foregroundColor)
        binding.selectionToolbar.setBackgroundColor(backgroundColor)
        listOf(
            binding.selectionCount,
            binding.selectionCloseText,
            binding.selectionCopyText,
            binding.selectionMoveText,
            binding.selectionPluginText,
            binding.selectionPluginMoreText,
            binding.selectionDeleteText,
        ).forEach { it.setTextColor(foregroundColor) }
        listOf<ImageView>(
            binding.selectionCloseIcon,
            binding.selectionCopyIcon,
            binding.selectionMoveIcon,
            binding.selectionPluginIcon,
            binding.selectionPluginMoreIcon,
            binding.selectionDeleteIcon,
        ).forEach { ImageViewCompat.setImageTintList(it, foregroundTint) }
        if (shown) applyNavigationBarColor(themeColor)
    }

    fun detach() {
        unblockListScroll()
        restoreNavigationBarColor()
    }

    private fun setupActions() {
        listOf(
            binding.selectionClose,
            binding.selectionCopy,
            binding.selectionMove,
            binding.selectionPlugin,
            binding.selectionPluginMore,
            binding.selectionDelete,
        ).forEach(::applyButtonAccessibilityRole)
        ViewCompat.setAccessibilityHeading(binding.selectionCount, true)
        binding.selectionClose.setOnClickListener { onClose() }
        binding.selectionCopy.setOnClickListener { onCopy() }
        binding.selectionMove.setOnClickListener { onMove() }
        binding.selectionPlugin.setOnClickListener { onPluginAction() }
        binding.selectionPluginMore.setOnClickListener { onMorePluginActions() }
        binding.selectionDelete.setOnClickListener { onDelete() }
    }

    private fun setupTouchBlocker() {
        recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (!blockListScrollUntilTouchRelease) return false
                if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                    unblockListScroll()
                }
                return true
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                    unblockListScroll()
                }
            }
        })
    }

    private fun applyButtonAccessibilityRole(view: View) {
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }
        })
    }

    private fun setShown(shouldShow: Boolean) {
        val toolbar = binding.selectionToolbar
        toolbar.animate().cancel()
        if (shouldShow) {
            applyTheme()
            applyNavigationBarColor()
            if (shown && toolbar.isVisible) {
                toolbar.translationY = 0F
                updateListBottomPadding()
                onShownChanged(true)
                return
            }
            shown = true
            toolbar.isVisible = true
            toolbar.translationY = toolbarHeight().toFloat()
            onShownChanged(true)
            toolbar.animate()
                .translationY(0F)
                .setDuration(SHOW_DURATION)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction(::updateListBottomPadding)
                .start()
            updateListBottomPadding()
            toolbar.post {
                updateListBottomPadding()
                onShownChanged(true)
            }
            return
        }
        if (!shown && !toolbar.isVisible) {
            restoreNavigationBarColor()
            updateListBottomPadding()
            return
        }
        shown = false
        toolbar.animate()
            .translationY(toolbarHeight().toFloat())
            .setDuration(HIDE_DURATION)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                if (!isSelectionActive()) {
                    toolbar.isVisible = false
                    toolbar.translationY = 0F
                    restoreNavigationBarColor()
                    updateListBottomPadding()
                    onShownChanged(false)
                }
            }
            .start()
    }

    private fun updateToolbarBottomInset() {
        binding.selectionToolbar.updatePadding(bottom = navigationBarInsetBottom)
        if (shown) onShownChanged(true)
    }

    private fun updateListBottomPadding() {
        val bottomPadding = if (isSelectionActive() || shown) toolbarHeight() else navigationBarInsetBottom
        recyclerView.updatePadding(bottom = extraListPaddingBottom + bottomPadding)
    }

    private fun toolbarHeight(): Int {
        val fallbackHeight = DisplayUtils.dpToPx(CONTENT_HEIGHT_DP).roundToInt() + navigationBarInsetBottom
        return binding.selectionToolbar.height.takeIf { it > 0 } ?: fallbackHeight
    }

    private fun updateActionState(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1.0f else 0.38f
    }

    private fun applyNavigationBarColor(themeColor: ThemeColor = ThemeColorManager.currentThemeColor) {
        val activity = context.findActivity() ?: return
        ViewUtils.setNavigationBarBackgroundColor(activity, themeColor.colorPrimary)
        ViewUtils.setNavigationBarIconLightByColorLuminance(activity, themeColor.colorPrimary)
        navigationBarColorApplied = true
    }

    private fun restoreNavigationBarColor() {
        if (!navigationBarColorApplied) return
        context.findActivity()?.let { activity ->
            ViewUtils.setNavigationBarBackgroundColor(activity, Color.TRANSPARENT)
            ViewUtils.setNavigationBarIconLight(activity, ViewUtils.isNightModeEnabled)
        }
        navigationBarColorApplied = false
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private companion object {
        private const val CONTENT_HEIGHT_DP = 81F
        private const val SHOW_DURATION = 120L
        private const val HIDE_DURATION = 90L
    }
}
