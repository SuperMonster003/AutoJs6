package org.autojs.autojs.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.util.Linkify
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.TextView
import androidx.preference.PreferenceViewHolder
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.annotation.ReservedForCompatibility
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.event.BackCompat
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.preference.LongClickablePreferenceLike
import org.autojs.autojs.ui.explorer.ExplorerView
import org.autojs.autojs.util.ViewUtils.setLinesEllipsizedIndividually
import org.autojs.autojs6.R
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Created by Stardust on Aug 4, 2017.
 * Transformed by SuperMonster003 on Oct 19, 2022.
 * Modified by OpenAI ChatGPT (GPT-5.2 Thinking) as of Jan 20, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 1, 2026.
 * Modified by SuperMonster003 as of Feb 7, 2026.
 */
object DialogUtils {

    private const val TAG = "DialogUtils"

    // Minimum UI update interval for progress dialog, to reduce main-thread pressure.
    // zh-CN: 进度对话框 UI 更新的最小间隔, 用于降低主线程压力.
    private const val PROGRESS_UPDATE_MIN_INTERVAL_MS: Long = 50L

    // Delay before showing progress dialog to avoid flashing for fast operations.
    // zh-CN: 显示进度对话框前的延迟, 用于避免快速操作导致的闪现.
    private const val PROGRESS_DIALOG_SHOW_DELAY_MS: Long = 200L

    // Minimum duration to keep progress dialog visible once shown, to avoid flashing.
    // zh-CN: 进度对话框一旦显示后的最短展示时长, 用于避免闪现.
    private const val PROGRESS_DIALOG_MIN_SHOW_MS: Long = 300L

    // Progress max for large files (totalBytes > Integer.MAX_VALUE), higher means smoother progress.
    // zh-CN: 大文件 (totalBytes > Integer.MAX_VALUE) 的进度最大值, 值越大进度越细腻.
    const val PROGRESS_MAX_LARGE: Int = 10000

    @JvmStatic
    fun <T : MaterialDialog.Builder> T.showAdaptive(): MaterialDialog = build().showAdaptive()

    @JvmStatic
    fun <T : MaterialDialog.Builder> T.showAdaptiveOrNull(): MaterialDialog? = build()?.showAdaptive()

    @JvmStatic
    @Suppress("DEPRECATION")
    fun <T : MaterialDialog> T.showAdaptive(): T = showDialog(this)

    /**
     * Show this [MaterialDialog] in a context-safe way.
     *
     * Behavior:
     * 1) Always performs window operations and `show()` on the main thread.
     * 2) If the dialog uses an [android.app.Activity] context, it is shown normally.
     * 3) Otherwise, it tries to show as an overlay window:
     *    - `TYPE_APPLICATION_OVERLAY` on Android O+.
     *    - `TYPE_PHONE` on pre-O devices.
     *    Overlay requires "Draw over other apps" permission (SYSTEM_ALERT_WINDOW).
     *    If permission is missing, the dialog will NOT be shown and no exception will be thrown.
     * 4) Avoids overriding `OnShowListener` to prevent breaking caller/library logic.
     *
     * zh-CN: 以 "上下文安全" 的方式显示 [MaterialDialog].
     *
     * 行为说明:
     * 1) 所有 Window 参数操作和 `show()` 都保证在主线程执行.
     * 2) 若对话框基于 [android.app.Activity] Context, 则按常规方式显示.
     * 3) 否则尝试以 overlay 窗口显示:
     *    - Android O+ 使用 `TYPE_APPLICATION_OVERLAY`.
     *    - Android O 以下使用 `TYPE_PHONE`.
     *    overlay 依赖 "在其他应用上层显示" (SYSTEM_ALERT_WINDOW) 权限.
     *    若检测到权限缺失, 将直接放弃显示且不会抛异常.
     * 4) 不覆盖 `OnShowListener`, 避免破坏调用方或库内部逻辑.
     *
     * @param dialog The dialog instance to show.
     *        zh-CN: 需要显示的对话框实例.
     * @param focusable Only affects overlay dialogs. If false, `FLAG_NOT_FOCUSABLE` is added,
     *        which makes the dialog less likely to be blocked when app is in background,
     *        but also prevents it from receiving some key/IME inputs.
     *        zh-CN: 仅影响 overlay 对话框. 为 false 时会添加 `FLAG_NOT_FOCUSABLE`,
     *        更可能在后台可见, 但会影响按键/输入法等焦点相关能力.
     *
     * @return The same dialog instance.
     *         zh-CN: 返回同一个对话框实例.
     */
    @JvmStatic
    @JvmOverloads
    @Deprecated("Use showAdaptive instead.", ReplaceWith("showAdaptive(dialog, focusable)"))
    @ReservedForCompatibility
    fun <T : MaterialDialog> showDialog(dialog: T, focusable: Boolean = true): T {
        ThreadUtils.runOnMain {
            // Prevent duplicated show.
            // zh-CN: 防止重复 show().
            if (dialog.isShowing) return@runOnMain

            val context = dialog.context

            // Activity lifecycle guard.
            // zh-CN: Activity 生命周期保护.
            unwrapActivity(context)?.let { act ->
                if (act.isFinishing || act.isDestroyed) {
                    Log.w(TAG, "Skip showing dialog: Activity is finishing/destroyed.")
                    return@runOnMain
                }
            }

            val needsOverlay = !isActivityContext(context)

            if (needsOverlay) {
                // Permission check.
                // zh-CN: 检查 overlay 权限.
                if (!Settings.canDrawOverlays(context)) {
                    Log.w(TAG, "Skip showing overlay dialog: missing SYSTEM_ALERT_WINDOW permission.")
                    return@runOnMain
                }

                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                fun applyOverlayWindowParams() {
                    // Ensure window exists; `create()` usually creates the underlying window for Dialog.
                    // zh-CN: 确保 window 存在; `create()` 通常会创建底层 window.
                    if (dialog.window == null) {
                        try {
                            dialog.create()
                        } catch (t: Throwable) {
                            Log.w(TAG, "dialog.create() failed (ignored).", t)
                        }
                    }

                    dialog.window?.let { w ->
                        try {
                            @Suppress("DEPRECATION")
                            w.setType(type)
                        } catch (t: Throwable) {
                            // Some ROMs may throw here; ignore and continue.
                            // zh-CN: 某些 ROM 可能在此抛异常; 忽略并继续.
                            Log.w(TAG, "Failed to set window type (ignored).", t)
                        }

                        if (focusable) {
                            // Keep default behavior for key/input handling.
                            // zh-CN: 保持默认行为以处理按键/输入.
                            w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                        } else {
                            // Non-focusable overlay dialogs are more likely to show while app is in background.
                            // zh-CN: 不可聚焦的 overlay 对话框更可能在应用后台时正常显示.
                            w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                        }
                    }
                }

                // Apply before show.
                // zh-CN: show() 前先尝试设置.
                applyOverlayWindowParams()

                try {
                    dialog.show()
                } catch (t: Throwable) {
                    // BadTokenException / SecurityException etc.
                    // zh-CN: 可能出现 BadTokenException / SecurityException 等.
                    Log.w(TAG, "Failed to show overlay dialog.", t)
                    return@runOnMain
                }

                // Re-apply after show (some dialog libs may reset attributes during show).
                // zh-CN: show() 后再补一次 (库内部可能在 show 过程中重置参数).
                applyOverlayWindowParams()
            } else {
                // Normal Activity dialog.
                // zh-CN: Activity Context 正常对话框.
                try {
                    dialog.show()
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to show dialog.", t)
                }
            }
        }

        return dialog
    }

    /**
     * Build dialog on the main thread by a callable factory.
     *
     * zh-CN: 通过 callable 工厂在主线程 build 对话框.
     */
    @JvmStatic
    fun <T : MaterialDialog> buildAdaptive(factory: Callable<T>): T {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            return factory.call()
        }

        val ref = AtomicReference<T>()
        val err = AtomicReference<Throwable?>()
        val latch = CountDownLatch(1)

        GlobalAppContext.post {
            try {
                ref.set(factory.call())
            } catch (t: Throwable) {
                err.set(t)
                Log.w(TAG, "buildAdaptive: failed", t)
            } finally {
                latch.countDown()
            }
        }

        // Wait a bit to avoid infinite blocking in background threads.
        // zh-CN: 设置等待超时以避免后台线程无限阻塞.
        latch.await(5, TimeUnit.SECONDS)

        err.get()?.let { throw RuntimeException(it) }

        return ref.get() ?: throw RuntimeException("buildAdaptive: dialog is null (timeout or build failed)")
    }

    @JvmStatic
    fun <T : MaterialDialog> buildAdaptiveOrNull(factory: Callable<T?>): T? {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            return factory.call()
        }

        val ref = AtomicReference<T?>()
        val err = AtomicReference<Throwable?>()
        val latch = CountDownLatch(1)

        GlobalAppContext.post {
            try {
                ref.set(factory.call())
            } catch (t: Throwable) {
                err.set(t)
                Log.w(TAG, "buildAdaptiveOrNull: failed", t)
            } finally {
                latch.countDown()
            }
        }

        // Wait a bit to avoid infinite blocking in background threads.
        // zh-CN: 设置等待超时以避免后台线程无限阻塞.
        latch.await(5, TimeUnit.SECONDS)

        err.get()?.let { throw RuntimeException(it) }

        return ref.get()
    }

    /**
     * Build and show dialog on the main thread with a callable factory, then return the instance.
     *
     * zh-CN: 使用 callable 工厂在主线程 build 并 show 对话框, 然后返回实例.
     */
    @JvmStatic
    @Suppress("DEPRECATION")
    fun <T : MaterialDialog> buildAndShowAdaptive(factory: Callable<T>): T {
        return buildAndShowAdaptive(factory, true)
    }

    /**
     * Build and show dialog on the main thread with a callable factory, then return the instance.
     *
     * zh-CN: 使用 callable 工厂在主线程 build 并 show 对话框, 然后返回实例.
     */
    @JvmStatic
    @Suppress("DEPRECATION")
    fun <T : MaterialDialog> buildAndShowAdaptive(factory: Callable<T>, focusable: Boolean): T {
        val dialog = buildAdaptive(factory)
        return showDialog(dialog, focusable)
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun <T : MaterialDialog> buildAndShowAdaptiveOrNull(factory: Callable<T?>): T? =
        buildAndShowAdaptiveOrNull(factory, true)

    @JvmStatic
    @Suppress("DEPRECATION")
    fun <T : MaterialDialog> buildAndShowAdaptiveOrNull(factory: Callable<T?>, focusable: Boolean): T? {
        val dialog = buildAdaptiveOrNull(factory) ?: return null
        return showDialog(dialog, focusable)
    }

    private fun unwrapActivity(context: Context?): Activity? {
        return when (context) {
            is Activity -> context
            is ContextWrapper -> unwrapActivity(context.baseContext)
            else -> null
        }
    }

    fun MaterialDialog.makeSettingsLaunchable(viewGetter: (dialog: MaterialDialog) -> View?, packageName: String?) {
        viewGetter(this)?.setOnClickListener {
            packageName?.let { pkg ->
                IntentUtils.launchAppDetailsSettings(this.context, pkg)
            } ?: ViewUtils.showSnack(this.view, R.string.error_app_not_installed)
        }
    }

    @JvmStatic
    fun MaterialDialog.makeTextCopyable(textViewGetter: (dialog: MaterialDialog) -> TextView?) {
        makeTextCopyable(textViewGetter(this))
    }

    @JvmStatic
    @JvmOverloads
    fun MaterialDialog.makeTextCopyable(textView: TextView?, textValue: String? = textView?.text?.toString()) {
        if (!textValue.isNullOrBlank()) {
            val context = this.context
            textView?.setOnClickListener {
                ClipboardUtils.setClip(context, textValue)
                ViewUtils.showSnack(this.view, "${context.getString(R.string.text_already_copied_to_clip)}: $textValue")
            }
        }
    }

    fun MaterialDialog.setCopyableTextIfAbsent(textView: TextView, textValuePair: Pair<String?, String?>) {
        setCopyableTextIfAbsent(textView, textValuePair.first, textValuePair.second)
    }

    fun MaterialDialog.setCopyableTextIfAbsent(textView: TextView, textValue: String?, suffix: String? = null) {
        when {
            textValue.isNullOrBlank() -> {
                if (textView.isNullOrBlank() || textView.isEllipsisSix()) {
                    textView.setUnknown()
                }
            }
            textView.isNullOrBlank() || textView.isEllipsisSix() || textView.isUnknown() -> {
                textView.text = textValue + (suffix ?: "")
                this.makeTextCopyable(textView, textValue)
            }
        }
    }

    fun MaterialDialog.setCopyableTextIfAbsent(textView: TextView, scope: CoroutineScope, f: () -> String?) {
        setCopyableTextIfAbsent(textView, scope, f, suffix = null)
    }

    fun MaterialDialog.setCopyableTextIfAbsent(textView: TextView, scope: CoroutineScope, f: () -> String?, suffix: String?) {
        scope.launch(Dispatchers.IO) {
            val textValue = f.invoke()
            withContext(Dispatchers.Main) {
                setCopyableTextIfAbsent(textView, textValue, suffix)
            }
        }
    }

    fun MaterialDialog.setCopyableText(textView: TextView, textValue: String?) {
        when {
            textValue.isNullOrBlank() -> {
                textView.setUnknown()
            }
            else -> {
                textView.text = textValue
                this.makeTextCopyable(textView, textValue)
            }
        }
    }

    @JvmStatic
    fun MaterialDialog.Builder.choiceWidgetThemeColor() = also {
        it.choiceWidgetColor(ThemeColorHelper.getThemeColorStateList(context))
    }

    @JvmStatic
    fun MaterialDialog.Builder.widgetThemeColor() = also {
        it.widgetColor(ThemeColorHelper.getThemeColorStateList(context))
    }

    @JvmStatic
    fun fixCheckBoxGravity(dialog: MaterialDialog): MaterialDialog = dialog.also {
        it.view.findViewById<CheckBox>(com.afollestad.materialdialogs.R.id.md_promptCheckbox)?.gravity = Gravity.CENTER_VERTICAL
    }

    private fun isActivityContext(context: Context?): Boolean {
        return context is Activity || (context is ContextWrapper && isActivityContext(context.baseContext))
    }

    fun toggleContentViewByItems(dialog: MaterialDialog) {
        dialog.contentView?.visibility = when {
            dialog.items.isNullOrEmpty() -> View.VISIBLE
            else -> View.GONE
        }
    }

    fun toggleActionButtonAbilityByItems(dialog: MaterialDialog, actionButton: DialogAction) {
        dialog.getActionButton(actionButton)?.isEnabled = !dialog.items.isNullOrEmpty()
    }

    @JvmStatic
    fun adaptToExplorer(dialog: MaterialDialog, explorerView: ExplorerView): MaterialDialog {
        val time = object {
            var lastPressed = 0L
            val minPressInterval = 1000L
        }

        return BackCompat.installDialogBackHandler(
            dialog = dialog,
            // Overlay dialog priority is inferred automatically by context.
            // zh-CN: Overlay/普通对话框优先级由 context 自动推断.
            priority = BackCompat.inferDialogPriority(dialog),
            // Always consumes the event, so the fallback will never be reached.
            // zh-CN: 事件总是会消费, fallback 实际永不可达.
            fallback = BackCompat.Fallback.NOOP,
            legacyKeyListener = true,
        ) { di ->
            if (explorerView.canGoBack()) {
                explorerView.goBack()
                return@installDialogBackHandler true
            }
            val now = System.currentTimeMillis()
            if (now - time.lastPressed >= time.minPressInterval) {
                time.lastPressed = now
                ViewUtils.showSnack(explorerView, R.string.text_press_again_to_dismiss_dialog)
                return@installDialogBackHandler true
            }
            di.dismiss()
            return@installDialogBackHandler true
        }
    }

    fun applyLongClickability(preference: LongClickablePreferenceLike, holder: PreferenceViewHolder) {
        holder.itemView.setOnLongClickListener {
            preference.longClickPrompt?.let { content ->
                MaterialDialog.Builder(preference.prefContext)
                    .title(preference.prefTitle ?: preference.prefContext.getString(R.string.text_prompt))
                    .content(content)
                    .apply {
                        preference.longClickPromptMore?.let { contentMore ->
                            neutralText(R.string.dialog_button_more)
                            neutralColorRes(R.color.dialog_button_hint)
                            onNeutral { _, _ ->
                                MaterialDialog.Builder(context)
                                    .title(context.getString(R.string.dialog_button_more))
                                    .content(contentMore)
                                    .positiveText(R.string.dialog_button_dismiss)
                                    .positiveColorRes(R.color.dialog_button_default)
                                    .build()
                                    .also {
                                        preference.longClickPromptMoreDialogHandler(it)
                                        it.contentView?.apply {
                                            autoLinkMask = Linkify.WEB_URLS
                                            text = text
                                        }
                                    }
                                    .showAdaptive()
                            }
                        }
                    }
                    .positiveText(R.string.dialog_button_dismiss)
                    .positiveColorRes(R.color.dialog_button_default)
                    .onPositive { dialog, _ -> dialog.dismiss() }
                    .autoDismiss(false)
                    .build()
                    .also {
                        preference.longClickPromptDialogHandler(it)
                        it.contentView?.apply {
                            autoLinkMask = Linkify.WEB_URLS
                            text = text
                        }
                    }
                    .showAdaptive()
                true
            } ?: false
        }
    }

    @JvmStatic
    fun MaterialDialog.installBackHandler(onBack: (DialogInterface) -> Boolean): MaterialDialog =
        BackCompat.installDialogBackHandler(this, onBack = onBack)

    @JvmStatic
    @JvmOverloads
    fun MaterialDialog.setProgressNumberFormatByBytes(readBytes: Long, totalBytes: Long, invalidBytesHint: String = ""): MaterialDialog = also {
        setProgressNumberFormat(getProgressBytesFormat(readBytes, totalBytes, invalidBytesHint))
    }

    @JvmStatic
    fun MaterialDialog.setProgressNumberFormatByBytes(readBytes: Long, totalBytes: Long, showPendingHint: Boolean): MaterialDialog = also {
        setProgressNumberFormat(getProgressBytesFormat(readBytes, totalBytes, if (showPendingHint) "..." else ""))
    }

    @Suppress("LocalVariableName")
    private fun getProgressBytesFormat(
        readBytes: Long,
        totalBytes: Long,
        invalidBytesHint: String = "",
    ): String {

        if (totalBytes <= 0 || readBytes <= 0) {
            return invalidBytesHint
        }

        val locale = Locale.getDefault()

        val r = readBytes.toDouble()
        val t = totalBytes.toDouble()

        val KiB = 1024.0
        val MiB = KiB * 1024.0
        val GiB = MiB * 1024.0
        val TiB = GiB * 1024.0

        return when {
            totalBytes < 1000L ->
                String.format(locale, "%.0f B / %.0f B", r, t)

            totalBytes < 1000L * 1024L -> when {
                readBytes < 1000L ->
                    String.format(locale, "%.0f B / %.1f KiB", r, t / KiB)
                else ->
                    String.format(locale, "%.1f KiB / %.1f KiB", r / KiB, t / KiB)
            }

            totalBytes < 1000L * 1024L * 1024L -> when {
                readBytes < 1000L ->
                    String.format(locale, "%.0f B / %.2f MiB", r, t / MiB)
                readBytes < 1000L * 1024L ->
                    String.format(locale, "%.1f KiB / %.2f MiB", r / KiB, t / MiB)
                else ->
                    String.format(locale, "%.2f MiB / %.2f MiB", r / MiB, t / MiB)
            }

            totalBytes < 1000L * 1024L * 1024L * 1024L -> when {
                readBytes < 1000L ->
                    String.format(locale, "%.0f B / %.2f GiB", r, t / GiB)
                readBytes < 1000L * 1024L ->
                    String.format(locale, "%.1f KiB / %.2f GiB", r / KiB, t / GiB)
                readBytes < 1000L * 1024L * 1024L ->
                    String.format(locale, "%.2f MiB / %.2f GiB", r / MiB, t / GiB)
                else ->
                    String.format(locale, "%.2f GiB / %.2f GiB", r / GiB, t / GiB)
            }

            else -> when {
                readBytes < 1000L ->
                    String.format(locale, "%.0f B / %.2f TiB", r, t / TiB)
                readBytes < 1000L * 1024L ->
                    String.format(locale, "%.1f KiB / %.2f TiB", r / KiB, t / TiB)
                readBytes < 1000L * 1024L * 1024L ->
                    String.format(locale, "%.2f MiB / %.2f TiB", r / MiB, t / TiB)
                readBytes < 1000L * 1024L * 1024L * 1024L ->
                    String.format(locale, "%.2f GiB / %.2f TiB", r / GiB, t / TiB)
                else ->
                    String.format(locale, "%.2f TiB / %.2f TiB", r / TiB, t / TiB)
            }
        }
    }

    @JvmStatic
    fun MaterialDialog.applyProgressThemeColorTintLists(): MaterialDialog = also {
        val progressBar = progressBar ?: return@also

        val fgColor = ColorUtils.adjustColorForContrast(context.getColor(R.color.dialog_progress_gray_background_tint), ThemeColorManager.colorPrimary, 2.3)
        val bgColor = ColorUtils.applyAlpha(fgColor, 0.2)

        progressBar.setProgressTintList(ColorStateList.valueOf(fgColor))
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(bgColor))
    }

    @JvmStatic
    fun MaterialDialog.setActionButtonText(actionButton: DialogAction, string: String) {
        getActionButton(actionButton).text = string
    }

    class ProgressDialogSession internal constructor(private val controller: OperationController) {
        private val dialogRef = AtomicReference<MaterialDialog?>()

        // Whether the session has been closed (operation finished/aborted), used to prevent late dialog showing.
        // zh-CN: 会话是否已关闭 (操作已完成/已中止), 用于防止延迟任务在结束后仍弹出对话框.
        private val closed = AtomicBoolean(false)

        private val showScheduled = AtomicBoolean(false)

        @Volatile
        private var shown = false

        private var shownAtUptimeMs = 0L

        // Pending UI state before dialog is actually shown.
        // zh-CN: 对话框真正 show 之前的挂起 UI 状态, 用于避免延迟显示导致的首次更新丢失.
        private val pendingContentRef: AtomicReference<List<String>?> = AtomicReference(null)
        private val pendingProcessedRef: AtomicLong = AtomicLong(-1L)
        private val pendingTotalRef: AtomicLong = AtomicLong(0L)

        // Timestamp/progress/content state for throttling UI updates.
        // zh-CN: 用于节流 UI 更新的时间戳/进度/文本状态.
        private val lastProgressUpdateTime: LongArray = longArrayOf(0L)
        private val lastProgressValue: LongArray = longArrayOf(-1L)
        private val lastContentUpdateTime: LongArray = longArrayOf(0L)

        fun scheduleShow(builder: MaterialDialog.Builder) {
            if (!showScheduled.compareAndSet(false, true)) {
                return
            }

            GlobalAppContext.postDelayed(Runnable {
                // Skip showing if operation already finished/aborted.
                // zh-CN: 若操作已结束/已中止, 则不再显示对话框.
                if (closed.get() || controller.cancelled.get()) {
                    return@Runnable
                }

                val dialog: MaterialDialog = buildAndShowAdaptive { builder.build() }
                dialog.setProgressNumberFormat("...")
                dialog.applyProgressThemeColorTintLists()

                // If closed becomes true right after building, dismiss immediately to avoid dangling dialog.
                // zh-CN: 若 build 后立刻变为 closed, 则立即关闭, 避免对话框悬挂.
                if (closed.get()) {
                    try {
                        if (dialog.isShowing) {
                            dialog.dismiss()
                        }
                    } catch (_: Throwable) {
                        /* Ignored. */
                    }
                    return@Runnable
                }

                dialogRef.set(dialog)
                shown = true
                shownAtUptimeMs = SystemClock.uptimeMillis()

                // Apply pending content/progress immediately after dialog is ready.
                // zh-CN: 对话框就绪后立即应用挂起的 content/progress, 避免首次更新被延迟显示吞掉.
                pendingContentRef.getAndSet(null)?.let { pending ->
                    GlobalAppContext.post {
                        try {
                            dialog.contentView?.setLinesEllipsizedIndividually(pending, 1.5f)
                        } catch (_: Throwable) {
                            /* Ignored. */
                        }
                    }
                }

                val pendingProcessed = pendingProcessedRef.getAndSet(-1L)
                if (pendingProcessed >= 0L) {
                    val pendingTotal = pendingTotalRef.get()
                    setProgressThrottled(pendingProcessed, pendingTotal)
                }
            }, PROGRESS_DIALOG_SHOW_DELAY_MS)
        }

        fun setContentThrottled(contentList: MutableList<String>) {
            val dialog = dialogRef.get()
            if (dialog == null) {
                // Cache the latest content if dialog is not shown yet.
                // zh-CN: 若对话框尚未显示, 缓存最新 content, 等显示后回放.
                pendingContentRef.set(contentList.toList())
                return
            }

            val now = SystemClock.uptimeMillis()

            if (now - lastContentUpdateTime[0] < PROGRESS_UPDATE_MIN_INTERVAL_MS) {
                return
            }

            lastContentUpdateTime[0] = now

            GlobalAppContext.post {
                try {
                    dialog.contentView?.setLinesEllipsizedIndividually(contentList.toList(), 1.5f)
                } catch (_: Throwable) {
                    /* Ignored. */
                }
            }
        }

        fun setProgressThrottled(processed: Long, total: Long) {
            val dialog = dialogRef.get()
            if (dialog == null) {
                // Cache the latest progress if dialog is not shown yet.
                // zh-CN: 若对话框尚未显示, 缓存最新进度, 等显示后回放.
                pendingProcessedRef.set(processed)
                pendingTotalRef.set(total)
                return
            }

            val now = SystemClock.uptimeMillis()

            if (processed == lastProgressValue[0]) {
                return
            }
            if (now - lastProgressUpdateTime[0] < PROGRESS_UPDATE_MIN_INTERVAL_MS) {
                return
            }

            lastProgressUpdateTime[0] = now
            lastProgressValue[0] = processed

            GlobalAppContext.post(Runnable {
                try {
                    dialog.setProgressNumberFormatByBytes(processed, total, true)

                    var v: Int
                    val max: Int

                    if (total > 0 && total <= Int.MAX_VALUE) {
                        max = total.toInt()
                        v = max(0L, min(processed, max.toLong())).toInt()
                    } else if (total > 0) {
                        max = PROGRESS_MAX_LARGE
                        val ratio = processed.toDouble() / total.toDouble()
                        v = (ratio * max.toDouble()).roundToInt()
                        v = max(0, min(max, v))
                    } else {
                        // No total bytes, keep indeterminate behavior.
                        // zh-CN: 无总字节数, 保持不确定进度行为.
                        return@Runnable
                    }

                    dialog.setMaxProgress(max)
                    dialog.setProgress(v)
                } catch (_: Throwable) {
                    /* Ignored. */
                }
            })
        }

        fun dismissSafely() {
            // Mark closed first so delayed show won't create a dialog after completion.
            // zh-CN: 先标记 closed, 防止延迟 show 在完成后创建对话框.
            closed.set(true)

            val dialog = dialogRef.get() ?: return

            val now = SystemClock.uptimeMillis()
            var delay = 0L

            if (shown) {
                val shownDuration = now - shownAtUptimeMs
                if (shownDuration < PROGRESS_DIALOG_MIN_SHOW_MS) {
                    delay = PROGRESS_DIALOG_MIN_SHOW_MS - shownDuration
                }
            }

            GlobalAppContext.postDelayed({
                try {
                    if (dialog.isShowing) {
                        dialog.dismiss()
                    }
                } catch (_: Throwable) {
                    /* Ignored. */
                }
            }, delay)
        }
    }

    // Controller for cancellable operations (copy/move/delete).
    // zh-CN: 用于可取消操作 (复制/移动/删除) 的控制器.
    class OperationController {
        val cancelled: AtomicBoolean = AtomicBoolean(false)

        fun cancel() {
            cancelled.set(true)
        }

        fun throwIfCancelled() {
            if (cancelled.get()) {
                throw OperationAbortedException()
            }
        }
    }

    class OperationAbortedException internal constructor() : RuntimeException("Operation aborted")

    private fun TextView.setUnknown() = setText(R.string.text_unknown)
    private fun TextView.isUnknown() = text == context.getString(R.string.text_unknown)
    private fun TextView.isEllipsisSix() = text == context.getString(R.string.ellipsis_six)
    private fun TextView.isNullOrBlank() = text.isNullOrBlank()
}
