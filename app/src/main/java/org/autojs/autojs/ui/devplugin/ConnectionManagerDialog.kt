package org.autojs.autojs.ui.devplugin

import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.disposables.CompositeDisposable
import org.autojs.autojs.core.accessibility.diagnostics.A11yServiceInfoFormat
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.DisplayUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.A11yServiceManagerEntryItemBinding
import org.autojs.autojs6.databinding.A11yServiceManagerSwitchItemBinding
import org.autojs.autojs6.databinding.DialogConnectionManagerBinding
import org.autojs.autojs6.databinding.OperationDialogItemBinding
import kotlin.math.roundToInt

/**
 * Shared shell of the VSCode connection managers (client mode / server mode), modelled on the
 * accessibility service manager: a dialog with a live status section (rows refreshed every second, each
 * copyable, the whole section copyable through the neutral button), control actions and preference switches.
 * zh-CN: VSCode 连接管理器 (客户端模式 / 服务端模式) 的公共骨架, 仿照无障碍服务管理器: 对话框含实时状态区
 * (每秒刷新, 逐行可复制, 中性按钮复制整个区块), 控制动作与偏好开关.
 *
 * Created by SuperMonster003 on Sep 16, 2026.
 */
abstract class ConnectionManagerDialog protected constructor(
    protected val context: Context,
    @StringRes titleRes: Int,
) {

    protected class Action(
        @StringRes val titleRes: Int,
        @DrawableRes val iconRes: Int,
        @StringRes val summaryRes: Int? = null,
        val onClick: () -> Unit,
    )

    protected val inflater: LayoutInflater = LayoutInflater.from(context)
    protected val binding = DialogConnectionManagerBinding.inflate(inflater)
    protected val handler = Handler(Looper.getMainLooper())
    protected val disposables = CompositeDisposable()

    private val statusRows = ArrayList<A11yServiceManagerEntryItemBinding>()
    private var statusEntries: List<Pair<String, String>> = emptyList()
    private val actionRows = LinkedHashMap<Action, OperationDialogItemBinding>()

    val dialog: MaterialDialog

    private val refreshTask = object : Runnable {
        override fun run() {
            if (!dialog.isShowing) return
            refresh()
            handler.postDelayed(this, REFRESH_INTERVAL_MILLIS)
        }
    }

    init {
        val sectionTitleColor = ColorUtils.adjustColorForContrast(context.getColor(R.color.window_background), ThemeColorManager.colorPrimary, 3.2)
        listOf(binding.statusTitle, binding.controlTitle, binding.settingsTitle).forEach { it.setTextColor(sectionTitleColor) }
        binding.statusRefresh.imageTintList = ColorStateList.valueOf(sectionTitleColor)
        binding.statusRefresh.setOnClickListener { refresh() }

        dialog = MaterialDialog.Builder(context)
            .title(titleRes)
            // Not wrapInScrollView: the library pads the inner view horizontally, which would cut the row ripples short.
            // zh-CN: 不使用 wrapInScrollView: 库会给内部视图加水平内边距, 使整行 ripple 被截断.
            .customView(binding.root, false)
            .autoDismiss(false)
            .negativeText(R.string.dialog_button_dismiss)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { d, _ -> d.dismiss() }
            .neutralText(R.string.dialog_button_copy)
            .neutralColorRes(R.color.dialog_button_hint)
            .onNeutral { _, _ -> copyStatus() }
            .showListener { onShown() }
            .dismissListener { onDismissed() }
            .build()
    }

    /** Recompute the status rows and the visibility of actions. Runs on the main thread. zh-CN: 重新计算状态行与动作可见性, 主线程执行. */
    protected abstract fun refresh()

    @CallSuper
    protected open fun onShown() {
        handler.removeCallbacks(refreshTask)
        handler.post(refreshTask)
    }

    @CallSuper
    protected open fun onDismissed() {
        handler.removeCallbacksAndMessages(null)
        disposables.clear()
    }

    fun show(): MaterialDialog = dialog.showAdaptive()

    protected fun getString(@StringRes res: Int): String = context.getString(res)

    protected fun getString(@StringRes res: Int, vararg args: Any): String = context.getString(res, *args)

    /** Elapsed text like `2.5 s` or `3 min 12 s`. zh-CN: 时长文本, 如 `2.5 s` 或 `3 min 12 s`. */
    protected fun formatElapsed(millis: Long): String = A11yServiceInfoFormat.formatElapsed(millis)

    /**
     * Replace the status rows; existing row views are reused so the dialog does not flicker on refresh.
     * zh-CN: 替换状态行; 复用已有行视图, 刷新时不闪烁.
     */
    protected fun setStatus(entries: List<Pair<String, String>>) {
        statusEntries = entries
        while (statusRows.size < entries.size) statusRows += createStatusRow(statusRows.size)
        statusRows.forEachIndexed { index, row ->
            val entry = entries.getOrNull(index)
            if (entry == null) {
                row.root.visibility = View.GONE
            } else {
                row.root.visibility = View.VISIBLE
                if (row.name.text != entry.first) row.name.text = entry.first
                if (row.value.text != entry.second) row.value.text = entry.second
            }
        }
    }

    private fun createStatusRow(index: Int): A11yServiceManagerEntryItemBinding {
        return A11yServiceManagerEntryItemBinding.inflate(inflater, binding.statusContainer, true).apply {
            root.setOnClickListener {
                val entry = statusEntries.getOrNull(index) ?: return@setOnClickListener
                copyText("${entry.first}: ${entry.second}")
            }
        }
    }

    protected fun addAction(action: Action): Action {
        actionRows[action] = OperationDialogItemBinding.inflate(inflater, binding.controlContainer, true).apply {
            val verticalPadding = DisplayUtils.dpToPx(10f).roundToInt()
            root.setPadding(root.paddingLeft, verticalPadding, root.paddingRight, verticalPadding)
            icon.setImageResource(action.iconRes)
            icon.imageTintList = ColorStateList.valueOf(context.getColor(R.color.day_night))
            title.setText(action.titleRes)
            action.summaryRes?.let { summaryRes ->
                subtitle.setText(summaryRes)
                subtitle.visibility = View.VISIBLE
            }
            root.setOnClickListener {
                runCatching { action.onClick() }.onFailure { e ->
                    snack(e.message ?: getString(R.string.error_an_error_occurred))
                }
            }
        }
        return action
    }

    protected fun setActionVisible(action: Action, visible: Boolean) {
        actionRows[action]?.root?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    protected fun addSwitch(
        @StringRes titleRes: Int,
        @StringRes summaryRes: Int,
        @StringRes keyRes: Int,
        defaultRes: Int,
        onChanged: ((Boolean) -> Unit)? = null,
    ) {
        A11yServiceManagerSwitchItemBinding.inflate(inflater, binding.settingsContainer, true).apply {
            title.setText(titleRes)
            subtitle.setText(summaryRes)
            // PrefSwitch persists through setChecked(), so a checked-change listener does not interfere.
            // zh-CN: PrefSwitch 通过 setChecked() 持久化, 因此选中变化监听器不会与之冲突.
            sw.setPrefKey(context.getString(keyRes), context.resources.getBoolean(defaultRes))
            onChanged?.let { listener -> sw.setOnCheckedChangeListener { _, isChecked -> listener(isChecked) } }
            root.setOnClickListener { sw.toggle() }
        }
    }

    protected fun snack(text: CharSequence?) = ViewUtils.showSnack(dialog.view, text)

    protected fun snack(@StringRes res: Int) = ViewUtils.showSnack(dialog.view, res)

    protected fun copyText(text: String?) {
        if (text.isNullOrEmpty()) return
        ClipboardUtils.setClip(context, text)
        snack(R.string.text_already_copied_to_clip)
    }

    private fun copyStatus() {
        copyText(statusEntries.joinToString("\n") { "${it.first}: ${it.second}" })
    }

    companion object {

        private const val REFRESH_INTERVAL_MILLIS = 1_000L

    }

}
