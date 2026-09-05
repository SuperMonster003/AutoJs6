package org.autojs.autojs.ui.accessibility

import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.core.accessibility.diagnostics.A11yServiceDiagnostics
import org.autojs.autojs.core.accessibility.diagnostics.A11yServiceDiagnostics.Field
import org.autojs.autojs.core.accessibility.diagnostics.A11yServiceDiagnostics.Probes
import org.autojs.autojs.core.accessibility.diagnostics.A11yServiceDiagnostics.Report
import org.autojs.autojs.core.accessibility.diagnostics.A11yServiceSelfCheck
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry.StateListener
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.DisplayUtils
import org.autojs.autojs.util.RootUtils
import org.autojs.autojs.util.ThreadUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.A11yServiceManagerEntryItemBinding
import org.autojs.autojs6.databinding.A11yServiceManagerSwitchItemBinding
import org.autojs.autojs6.databinding.DialogA11yServiceManagerBinding
import org.autojs.autojs6.databinding.OperationDialogItemBinding
import java.util.EnumMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Accessibility service manager, opened from the drawer entry's "Manager" button or by long-pressing
 * the entry. A dialog (not an Activity) with three sections: live status rows (each copyable), control
 * actions and the auto-enable preferences, plus a copy of the whole diagnostics text. It doubles as the
 * on-device debugging panel of the accessibility automation roadmap.
 *
 * zh-CN: 无障碍服务管理器, 由抽屉条目的 "管理器" 按钮或长按条目打开. 对话框形式 (非 Activity), 含三个区块:
 * 实时状态行 (逐行可复制), 控制动作, 自动启用偏好, 并可复制完整诊断文本. 兼作无障碍自动化 Roadmap 的真机调试面板.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
class A11yServiceManagerDialog private constructor(
    private val context: Context,
    private val tool: AccessibilityTool,
    private val onServiceChanged: () -> Unit,
) {

    private enum class Action(@StringRes val titleRes: Int, @StringRes val summaryRes: Int?, @DrawableRes val iconRes: Int) {
        START(R.string.text_start, null, R.drawable.ic_play_arrow_white_48dp),
        STOP(R.string.text_stop, null, R.drawable.ic_stop_button_fill),
        RESTART(R.string.text_restart, null, R.drawable.ic_refresh_white_24dp),
        CLEAR_NODE_CACHE(R.string.text_clear_node_cache, null, R.drawable.ic_delete_all),
        SELF_CHECK(R.string.text_self_check, R.string.summary_a11y_service_self_check, R.drawable.ic_check_mark),
        EVENT_MONITOR(R.string.text_a11y_event_monitor, R.string.summary_a11y_event_monitor, R.drawable.ic_list_thicker),
    }

    /** Result of a control action; [launchSettings] asks the user to finish the job in system settings. */
    private class Outcome(val message: String? = null, val launchSettings: Boolean = false, val selfCheck: String? = null)

    private val inflater = LayoutInflater.from(context)
    private val binding = DialogA11yServiceManagerBinding.inflate(inflater)
    private val diagnostics = A11yServiceDiagnostics(context, tool)
    private val handler = Handler(Looper.getMainLooper())
    private val disposables = CompositeDisposable()
    private val statusRows = EnumMap<Field, A11yServiceManagerEntryItemBinding>(Field::class.java)
    private val actionRows = EnumMap<Action, OperationDialogItemBinding>(Action::class.java)
    private val isCollecting = AtomicBoolean(false)
    private var isActionRunning = false
    private var latestReport: Report? = null

    // Probed once on a worker thread (may spawn a root shell); never read from the 1 s timer.
    // zh-CN: 只在 worker 线程探测一次 (可能会起 root shell); 不在 1 s 定时器中读取.
    @Volatile
    private var rootAvailable: Boolean? = null

    @Volatile
    private var selfCheckResult: String? = null

    private val dialog: MaterialDialog

    private val refreshTask = object : Runnable {
        override fun run() {
            if (!dialog.isShowing) return
            refresh()
            handler.postDelayed(this, REFRESH_INTERVAL_MILLIS)
        }
    }

    // Registry callbacks may arrive on any thread.
    // zh-CN: 注册表回调可能来自任意线程.
    private val stateListener = StateListener<AccessibilityService> { _, _ ->
        ThreadUtils.runOnMain { if (dialog.isShowing) refresh() }
    }

    init {
        val sectionTitleColor = ColorUtils.adjustColorForContrast(context.getColor(R.color.window_background), ThemeColorManager.colorPrimary, 3.2)
        listOf(binding.statusTitle, binding.controlTitle, binding.settingsTitle).forEach { it.setTextColor(sectionTitleColor) }

        Field.entries.forEach { field -> statusRows[field] = createStatusRow(field) }
        Action.entries.forEach { action -> actionRows[action] = createActionRow(action) }
        createSwitchRow(
            R.string.text_enable_a11y_service_with_root_access_automatically,
            R.string.summary_enable_a11y_service_with_root_access,
            R.string.key_enable_a11y_service_with_root_access,
            R.bool.pref_enable_a11y_service_with_root_access,
        )
        createSwitchRow(
            R.string.text_enable_a11y_service_with_secure_settings_automatically,
            R.string.summary_enable_a11y_service_with_secure_settings,
            R.string.key_enable_a11y_service_with_secure_settings,
            R.bool.pref_enable_a11y_service_with_secure_settings,
        )

        dialog = MaterialDialog.Builder(context)
            .title(R.string.text_a11y_service_manager)
            .options(
                listOf(
                    MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_refresh)) { refresh() },
                ),
            )
            // Not wrapInScrollView: the library pads the inner view horizontally, which would cut the row ripples short.
            // zh-CN: 不使用 wrapInScrollView: 库会给内部视图加水平内边距, 使整行 ripple 被截断.
            .customView(binding.root, false)
            .autoDismiss(false)
            .negativeText(R.string.dialog_button_dismiss)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { d, _ -> d.dismiss() }
            .neutralText(R.string.dialog_button_copy)
            .neutralColorRes(R.color.dialog_button_hint)
            .onNeutral { _, _ -> copyReport() }
            .positiveText(R.string.dialog_button_system_settings_simplified)
            .positiveColorRes(R.color.dialog_button_advanced_settings)
            .onPositive { _, _ -> tool.launchSettings(false) }
            .showListener { onShown() }
            .dismissListener { onDismissed() }
            .build()
    }

    private fun onShown() {
        AccessibilityService.registry.addListener(this, stateListener)
        handler.removeCallbacks(refreshTask)
        handler.post(refreshTask)
        disposables.add(
            Single.fromCallable { RootUtils.isRootAvailable() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ available ->
                    rootAvailable = available
                    if (dialog.isShowing) refresh()
                }, { e ->
                    Log.w(TAG, "Unable to probe root availability", e)
                    rootAvailable = false
                }),
        )
    }

    private fun onDismissed() {
        handler.removeCallbacksAndMessages(null)
        AccessibilityService.registry.removeListener(this)
        disposables.clear()
    }

    // Collection reads windows / root node / service info over binder; keep it off the main thread and never overlap.
    // zh-CN: 收集过程通过 binder 读取窗口 / 根节点 / 服务信息; 放在主线程之外并避免重叠.
    private fun refresh() {
        if (!isCollecting.compareAndSet(false, true)) return
        val probes = Probes(rootAvailable, selfCheckResult)
        disposables.add(
            Single.fromCallable { diagnostics.collect(probes) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally { isCollecting.set(false) }
                .subscribe({ report ->
                    if (dialog.isShowing) applyReport(report)
                }, { e ->
                    Log.w(TAG, "Unable to collect accessibility service diagnostics", e)
                }),
        )
    }

    private fun applyReport(report: Report) {
        latestReport = report
        val present = report.entries.associateBy { it.field }
        statusRows.forEach { (field, row) ->
            val entry = present[field]
            row.root.visibility = if (entry != null) View.VISIBLE else View.GONE
            if (entry != null) row.value.text = entry.value
        }
    }

    private fun createStatusRow(field: Field): A11yServiceManagerEntryItemBinding {
        return A11yServiceManagerEntryItemBinding.inflate(inflater, binding.statusContainer, true).apply {
            name.setText(field.labelRes)
            root.visibility = View.GONE
            root.setOnClickListener {
                val entry = latestReport?.entries?.firstOrNull { it.field == field } ?: return@setOnClickListener
                ClipboardUtils.setClip(context, "${entry.label}: ${entry.value}")
                ViewUtils.showSnack(dialog.view, R.string.text_already_copied_to_clip)
            }
        }
    }

    private fun createActionRow(action: Action): OperationDialogItemBinding {
        return OperationDialogItemBinding.inflate(inflater, binding.controlContainer, true).apply {
            val verticalPadding = DisplayUtils.dpToPx(10f).roundToInt()
            root.setPadding(root.paddingLeft, verticalPadding, root.paddingRight, verticalPadding)
            icon.setImageResource(action.iconRes)
            icon.imageTintList = ColorStateList.valueOf(context.getColor(R.color.day_night))
            title.setText(action.titleRes)
            restoreSubtitle(this, action)
            root.setOnClickListener { runAction(action) }
        }
    }

    private fun restoreSubtitle(row: OperationDialogItemBinding, action: Action) {
        val summaryRes = action.summaryRes
        if (summaryRes != null) {
            row.subtitle.setText(summaryRes)
            row.subtitle.visibility = View.VISIBLE
        } else {
            row.subtitle.visibility = View.GONE
        }
    }

    private fun createSwitchRow(@StringRes titleRes: Int, @StringRes summaryRes: Int, @StringRes keyRes: Int, defaultRes: Int) {
        A11yServiceManagerSwitchItemBinding.inflate(inflater, binding.settingsContainer, true).apply {
            title.setText(titleRes)
            subtitle.setText(summaryRes)
            sw.setPrefKey(context.getString(keyRes), context.resources.getBoolean(defaultRes))
            root.setOnClickListener { sw.toggle() }
        }
    }

    private fun runAction(action: Action) {
        val work: () -> Outcome = when (action) {
            Action.START -> ::start
            Action.STOP -> ::stop
            Action.RESTART -> ::restart
            Action.CLEAR_NODE_CACHE -> ::clearNodeCache
            Action.SELF_CHECK -> ::selfCheck
            Action.EVENT_MONITOR -> {
                A11yEventMonitorDialog.show(context)
                return
            }
        }
        if (isActionRunning) return
        setActionsEnabled(false, running = action)

        // Service control shares the drawer toggle's serial scheduler so that the two never interleave;
        // the read-only self-check (up to ~3.5 s) must not hold that scheduler.
        // zh-CN: 服务启停与抽屉开关共用同一串行调度器, 避免交错; 只读的自检 (最长约 3.5 s) 不应占用该调度器.
        val scheduler = if (action == Action.SELF_CHECK) Schedulers.io() else Schedulers.single()

        disposables.add(
            Single.fromCallable { work() }
                .subscribeOn(scheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ outcome ->
                    finishAction(action)
                    outcome.selfCheck?.let { selfCheckResult = it }
                    outcome.message?.let { ViewUtils.showSnack(dialog.view, it, outcome.selfCheck != null) }
                    if (outcome.launchSettings) tool.launchSettings(true)
                }, { e ->
                    finishAction(action)
                    Log.w(TAG, "Accessibility service manager action failed: $action", e)
                    ViewUtils.showSnack(dialog.view, e.message ?: context.getString(R.string.error_an_error_occurred), true)
                }),
        )
    }

    private fun finishAction(action: Action) {
        setActionsEnabled(true, running = action)
        if (action != Action.SELF_CHECK && action != Action.CLEAR_NODE_CACHE) {
            onServiceChanged()
        }
        refresh()
    }

    private fun setActionsEnabled(enabled: Boolean, running: Action) {
        isActionRunning = !enabled
        actionRows.forEach { (action, row) ->
            row.root.isEnabled = enabled
            row.root.alpha = if (enabled) 1f else DISABLED_ALPHA
            if (action == running) {
                if (enabled) {
                    restoreSubtitle(row, action)
                } else {
                    row.subtitle.setText(R.string.text_in_progress)
                    row.subtitle.visibility = View.VISIBLE
                }
            }
        }
    }

    // Convenient ways (root / secure settings) first; when the service is simply not enabled the user
    // finishes in system settings, an enabled-but-unbound service is reported as a failure (restart helps).
    // zh-CN: 优先走便捷方式 (root / 安全设置); 服务只是未启用时交给用户在系统设置中完成, 已启用但未绑定则报告失败 (可用重启处理).
    private fun start(): Outcome = when {
        tool.startService(false) -> Outcome(context.getString(R.string.text_a11y_service_started))
        !tool.hasService() -> Outcome(launchSettings = true)
        else -> Outcome(context.getString(R.string.text_failed_to_enable_a11y_service))
    }

    private fun stop(): Outcome = when {
        tool.stopService(false) -> Outcome(context.getString(R.string.text_a11y_service_stopped))
        tool.hasService() -> Outcome(launchSettings = true)
        else -> Outcome(context.getString(R.string.text_a11y_service_stopped))
    }

    private fun restart(): Outcome = when {
        tool.restartService(false) -> Outcome(context.getString(R.string.text_a11y_service_restarted))
        !tool.hasService() -> Outcome(launchSettings = true)
        else -> Outcome(context.getString(R.string.text_failed_to_restart_a11y_service))
    }

    private fun clearNodeCache(): Outcome = when {
        !AccessibilityService.hasInstance() -> Outcome(serviceStatusMessage())
        tool.clearCache() -> Outcome(context.getString(R.string.text_node_cache_cleared))
        else -> Outcome(context.getString(R.string.text_failed_to_clear_node_cache))
    }

    private fun selfCheck(): Outcome {
        val service = AccessibilityService.instance ?: return Outcome(serviceStatusMessage())
        val result = A11yServiceSelfCheck.run(service).toText()
        return Outcome(message = result, selfCheck = result)
    }

    private fun serviceStatusMessage(): String {
        return "${context.getString(R.string.text_a11y_service)}: ${context.getString(diagnostics.status.textRes)}"
    }

    private fun copyReport() {
        val report = latestReport ?: return
        ClipboardUtils.setClip(context, report.toText())
        ViewUtils.showSnack(dialog.view, R.string.text_already_copied_to_clip)
    }

    companion object {

        private const val TAG = "A11yServiceManagerDialog"

        private const val REFRESH_INTERVAL_MILLIS = 1_000L
        private const val DISABLED_ALPHA = 0.58f

        /**
         * Shows the manager. [context] must be an Activity context; the caller keeps the returned dialog
         * (weakly) and dismisses it when that Activity goes away.
         * zh-CN: 显示管理器. [context] 须为 Activity 上下文; 调用方 (弱) 持有返回的对话框, 并在 Activity 销毁时关闭它.
         */
        fun show(context: Context, tool: AccessibilityTool, onServiceChanged: () -> Unit = {}): MaterialDialog {
            return A11yServiceManagerDialog(context, tool, onServiceChanged).dialog.showAdaptive()
        }

    }

}
