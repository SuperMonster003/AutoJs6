package org.autojs.autojs.ui.explorer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.annotation.VisibleForTesting
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.MenuCompat
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
import org.autojs.autojs6.databinding.ExplorerSelectionActionBinding
import org.autojs.autojs6.databinding.ExplorerViewBinding
import kotlin.math.roundToInt

/** Everything needed to render the current multi-selection actions. */
internal data class ExplorerSelectionToolbarState(
    val selectedCount: Int,
    /** In display order; [ExplorerSelectionAction.level] decides between a button and "More". */
    val actions: List<ExplorerSelectionAction>,
)

/**
 * Owns selection-toolbar presentation, animation, insets and long-press touch suppression.
 *
 * Selection semantics remain in [ExplorerSelectionController], while [ExplorerView] resolves the
 * actions. This class is deliberately limited to UI state and window integration.
 *
 * The toolbar is "Exit", up to [MAX_ACTION_BUTTONS] primary actions as labeled buttons, and "More"
 * whenever secondary actions (or primary actions without a free slot) remain, so new batch actions
 * only need a level instead of a new layout slot.
 * zh-CN: 工具栏依次为 "退出", 最多 [MAX_ACTION_BUTTONS] 个带文字的主要操作按钮, 以及在仍有次要操作
 * (或没有空余按钮位的主要操作) 时出现的 "更多". 因此新增批量操作只需指定级别, 无需新增布局按钮位.
 */
internal class ExplorerSelectionToolbarController(
    private val context: Context,
    private val binding: ExplorerViewBinding,
    private val recyclerView: RecyclerView,
    private val isSelectionActive: () -> Boolean,
    private val setRefreshEnabled: (Boolean) -> Unit,
    private val onClose: () -> Unit,
    private val onShownChanged: (Boolean) -> Unit,
) {

    private var shown = false
    private var navigationBarColorApplied = false
    private var blockListScrollUntilTouchRelease = false
    private var navigationBarInsetBottom = 0
    private var extraListPaddingBottom = 0
    private var listClipToPadding = false
    private var foregroundColor = Color.BLACK

    private val actionButtons = LinkedHashMap<String, ExplorerSelectionActionBinding>()
    private var overflowActions: List<ExplorerSelectionAction> = emptyList()

    init {
        setupActions()
        setupTouchBlocker()
        applyTheme()
    }

    fun render(state: ExplorerSelectionToolbarState) {
        // An empty selection keeps the last buttons, so the row does not reflow while the toolbar slides away.
        // zh-CN: 选择为空时保留上一次的按钮, 避免工具栏滑出期间按钮行重新排布.
        if (state.selectedCount > 0) {
            binding.selectionCount.text = context.getString(R.string.text_selected_items, state.selectedCount)
            val layout = ExplorerSelectionActionLayout.of(state.actions, MAX_ACTION_BUTTONS)
            renderButtons(layout.buttons)
            overflowActions = layout.overflow
            binding.selectionMore.isVisible = overflowActions.isNotEmpty()
        }
        setShown(state.selectedCount > 0)
    }

    /** Buttons that currently show primary actions, in display order. */
    @VisibleForTesting
    internal val actionButtonViews: List<View>
        get() = actionButtons.values.map { it.root }

    private fun renderButtons(actions: List<ExplorerSelectionAction>) {
        val container = binding.selectionActions
        val keys = actions.map { it.key }
        if (keys != actionButtons.keys.toList()) {
            // Reattach in the new order; buttons whose keys remain keep their views (and accessibility focus).
            // zh-CN: 按新顺序重新挂载; 仍然存在的按键沿用原视图 (及无障碍焦点).
            val previous = LinkedHashMap(actionButtons)
            actionButtons.clear()
            previous.values.forEach { container.removeView(it.root) }
            val insertAt = container.indexOfChild(binding.selectionClose) + 1
            keys.forEachIndexed { index, key ->
                val button = previous[key] ?: createActionButton(container)
                actionButtons[key] = button
                container.addView(button.root, insertAt + index)
            }
        }
        actions.forEach { action -> bindActionButton(actionButtons.getValue(action.key), action) }
    }

    private fun createActionButton(container: ViewGroup): ExplorerSelectionActionBinding {
        return ExplorerSelectionActionBinding.inflate(LayoutInflater.from(context), container, false).also { button ->
            applyButtonAccessibilityRole(button.root)
            button.selectionActionText.setTextColor(foregroundColor)
            ImageViewCompat.setImageTintList(button.selectionActionIcon, ColorStateList.valueOf(foregroundColor))
        }
    }

    private fun bindActionButton(button: ExplorerSelectionActionBinding, action: ExplorerSelectionAction) {
        button.selectionActionIcon.setImageResource(checkNotNull(action.iconRes))
        val iconPadding = DisplayUtils.dpToPx(action.iconPaddingDp).roundToInt()
        button.selectionActionIcon.setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
        button.selectionActionText.text = action.label
        button.root.contentDescription = action.label
        button.root.setOnClickListener { action.onClick() }
        val onLongClick = action.onLongClick
        if (onLongClick != null) {
            button.root.setOnLongClickListener { onLongClick() }
        } else {
            button.root.setOnLongClickListener(null)
            button.root.isLongClickable = false
        }
        updateActionState(button.root, action.enabled)
    }

    private fun showOverflowActions() {
        val actions = overflowActions
        if (actions.isEmpty()) return
        val popupMenu = PopupMenu(context, binding.selectionMore)
        actions.forEachIndexed { index, action ->
            popupMenu.menu.add(action.group.ordinal, index, index, action.label).isEnabled = action.enabled
        }
        MenuCompat.setGroupDividerEnabled(popupMenu.menu, true)
        popupMenu.setOnMenuItemClickListener { item ->
            actions.getOrNull(item.itemId)?.takeIf { it.enabled }?.onClick?.invoke()
            true
        }
        popupMenu.show()
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
        this.foregroundColor = foregroundColor
        binding.selectionToolbar.setBackgroundColor(backgroundColor)
        listOf(
            binding.selectionCount,
            binding.selectionCloseText,
            binding.selectionMoreText,
        ).forEach { it.setTextColor(foregroundColor) }
        listOf<ImageView>(
            binding.selectionCloseIcon,
            binding.selectionMoreIcon,
        ).forEach { ImageViewCompat.setImageTintList(it, foregroundTint) }
        actionButtons.values.forEach { button ->
            button.selectionActionText.setTextColor(foregroundColor)
            ImageViewCompat.setImageTintList(button.selectionActionIcon, foregroundTint)
        }
        if (shown) applyNavigationBarColor(themeColor)
    }

    fun detach() {
        unblockListScroll()
        restoreNavigationBarColor()
    }

    private fun setupActions() {
        listOf(
            binding.selectionClose,
            binding.selectionMore,
        ).forEach(::applyButtonAccessibilityRole)
        ViewCompat.setAccessibilityHeading(binding.selectionCount, true)
        binding.selectionClose.setOnClickListener { onClose() }
        binding.selectionMore.setOnClickListener { showOverflowActions() }
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

    internal companion object {
        /**
         * Primary buttons between "Exit" and "More": six slots of at least 53dp fit a 320dp screen.
         * zh-CN: "退出" 与 "更多" 之间的主要操作按钮数: 六个不小于 53dp 的按钮位可放入 320dp 宽的屏幕.
         */
        const val MAX_ACTION_BUTTONS = 4

        private const val CONTENT_HEIGHT_DP = 81F
        private const val SHOW_DURATION = 120L
        private const val HIDE_DURATION = 90L
    }
}
