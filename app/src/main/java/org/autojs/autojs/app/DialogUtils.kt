@file:Suppress("unused")

package org.autojs.autojs.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.DialogInterface
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.text.util.Linkify
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import androidx.preference.PreferenceViewHolder
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.annotation.ReservedForCompatibility
import org.autojs.autojs.theme.preference.LongClickablePreferenceLike
import org.autojs.autojs.ui.explorer.ExplorerView
import org.autojs.autojs.util.ViewUtils.showSnack
import org.autojs.autojs6.R

/**
 * Created by Stardust on Aug 4, 2017.
 * Transformed by SuperMonster003 on Oct 19, 2022.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Jan 18, 2026.
 * Modified by OpenAI ChatGPT (GPT-5.2 Thinking) as of Jan 20, 2026.
 * Modified by SuperMonster003 as of Jan 20, 2026.
 */
object DialogUtils {

    private const val TAG = "DialogUtils"

    @JvmStatic
    fun MaterialDialog.Builder.showAdaptive() = build().showAdaptive()

    @JvmStatic
    fun MaterialDialog.showAdaptive() = showDialog(this)

    /**
     * Show this [MaterialDialog] in a context-safe way.
     *
     * Behavior:
     * 1) Always performs window operations and `show()` on the main thread.
     * 2) If the dialog uses an [Activity] context, it is shown normally.
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
     * 2) 若对话框基于 [Activity] Context, 则按常规方式显示.
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
    @ReservedForCompatibility
    fun <T : MaterialDialog> showDialog(dialog: T, focusable: Boolean = true): T {
        runOnMain {
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

    private fun unwrapActivity(context: Context?): Activity? {
        return when (context) {
            is Activity -> context
            is ContextWrapper -> unwrapActivity(context.baseContext)
            else -> null
        }
    }

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            block()
        } else {
            GlobalAppContext.post { block() }
        }
    }

    @JvmStatic
    fun <T : MaterialDialog> fixCheckBoxGravity(dialog: T): T = dialog.also {
        it.view.findViewById<CheckBox>(com.afollestad.materialdialogs.R.id.md_promptCheckbox)?.gravity = Gravity.CENTER_VERTICAL
    }

    @JvmStatic
    fun isActivityContext(context: Context?): Boolean {
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
        val time = object : Any() {
            var lastPressed: Long = 0
            val minPressInterval: Long = 1000
        }
        dialog.setOnKeyListener { dialogInterface: DialogInterface, keyCode: Int, event: KeyEvent ->
            if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                if (explorerView.canGoBack()) {
                    explorerView.goBack()
                    return@setOnKeyListener true
                }
                if (System.currentTimeMillis() - time.lastPressed < time.minPressInterval) {
                    dialogInterface.dismiss()
                } else {
                    time.lastPressed = System.currentTimeMillis()
                    showSnack(explorerView, R.string.text_press_again_to_dismiss_dialog)
                }
            }
            false
        }
        return dialog
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

}