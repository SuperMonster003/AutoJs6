package org.autojs.autojs.event

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.DialogInterface
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.lifecycle.LifecycleOwner
import org.autojs.autojs6.R

/**
 * Provides a unified back handling layer for:
 * - Activity/Fragment: OnBackPressedDispatcher (AndroidX)
 * - Dialog: OnBackInvokedDispatcher (API 33+) + KEYCODE_BACK fallback
 * - Overlay View: View#findOnBackInvokedDispatcher (API 33+) + dispatchKeyEvent fallback
 *
 * zh-CN:
 *
 * 提供统一的返回键/返回手势处理层, 覆盖:
 * - Activity/Fragment: AndroidX OnBackPressedDispatcher
 * - Dialog: API 33+ OnBackInvokedDispatcher + KEYCODE_BACK fallback
 * - Overlay View: API 33+ View.findOnBackInvokedDispatcher + dispatchKeyEvent fallback
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Jan 21, 2026.
 * Modified by SuperMonster003 as of Apr 2, 2024.
 */
object BackCompat {

    enum class Priority { DEFAULT, OVERLAY }

    /**
     * Fallback policy when back is NOT consumed in API 33+ callback path.
     *
     * Note:
     * - Platform OnBackInvokedCallback has no return value, so we cannot "let system handle it".
     *   We provide a best-effort fallback strategy.
     *
     * zh-CN:
     * 
     * 当 API 33+ 回调路径中返回键未被消费时的备用策略.
     * 
     * 注:
     * - OnBackInvokedCallback 没有返回值, 无法像 KeyListener 那样把 back 继续交给系统.
     *   因此提供一个尽力而为的 fallback 策略.
     */
    enum class Fallback {
        NOOP,
        DISMISS_IF_CANCELABLE,
        ALWAYS_DISMISS,
    }

    /** Disposable handle. zh-CN: 可释放句柄. */
    fun interface Handle { fun dispose() }

    private val NOOP_HANDLE = Handle { /* No-op. */ }

    // Use View tag to prevent duplicate installation on the same decorView/rootView.
    // zh-CN: 用 View tag 防止重复安装.
    val TAG_KEY_INSTALLED: Int = R.id.tag_backcompat_dialog_back_callback

    /**
     * Install back handler for Activity/Fragment scope via AndroidX dispatcher.
     *
     * zh-CN: 通过 AndroidX dispatcher 为 Activity/Fragment 安装 back handler.
     */
    @JvmStatic
    @JvmOverloads
    fun install(
        activity: ComponentActivity,
        owner: LifecycleOwner = activity,
        enabled: Boolean = true,
        onBack: () -> Unit,
    ): Handle {
        val cb = object : OnBackPressedCallback(enabled) {
            override fun handleOnBackPressed() = onBack()
        }
        activity.onBackPressedDispatcher.addCallback(owner, cb)
        return Handle { cb.remove() }
    }

    /**
     * Install a back handler that works on both legacy key events and Android 13+ predictive back.
     *
     * Notes:
     * - Legacy path: Dialog#setOnKeyListener for KEYCODE_BACK.
     * - Android 13+: OnBackInvokedDispatcher is the actual entry point when targetSdk >= 33.
     * - Cleanup: unregister callback on decorView detach to avoid leaks.
     *
     * zh-CN:
     * 
     * 安装适用于旧版按键事件和 Android 13+ 预测返回的 back handler.
     * 
     * 注:
     * - 旧链路: Dialog#setOnKeyListener 处理 KEYCODE_BACK.
     * - Android 13+: targetSdk >= 33 时, OnBackInvokedDispatcher 才是实际入口.
     * - 清理: decorView detach 时自动反注册, 避免泄漏.
     */
    @JvmStatic
    @JvmOverloads
    fun <T : Dialog> installDialogBackHandler(
        dialog: T,
        priority: Priority = inferDialogPriority(dialog),
        fallback: Fallback = Fallback.DISMISS_IF_CANCELABLE,
        legacyKeyListener: Boolean = true,
        onBack: (DialogInterface) -> Boolean,
    ): T = dialog.also { d ->

        // 1) Legacy fallback: key event path.
        // zh-CN: 旧系统 fallback: 按键事件链路.
        if (legacyKeyListener) {
            d.setOnKeyListener { di, keyCode, event ->
                keyCode == KeyEvent.KEYCODE_BACK &&
                        event.action == KeyEvent.ACTION_UP &&
                        onBack(di)
            }
        }

        // 2) Android 13+: predictive back path.
        // zh-CN: Android 13+: 预测返回链路.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Api33.installDialogBackHandler(d, priority, fallback, onBack)
        }
    }

    @JvmStatic
    fun inferDialogPriority(dialog: Dialog): Priority =
        when {
            isActivityContext(dialog.context) -> {
                Priority.DEFAULT
            }
            else -> Priority.OVERLAY
        }

    /**
     * Install back handler for an attached View via View#findOnBackInvokedDispatcher() (API 33+).
     *
     * Notes:
     * - Works best for overlay / WindowManager-added views that want to respond to system back.
     * - Automatically registers on attach, unregisters on detach.
     * - Does NOT require you to override dispatchKeyEvent (but you may keep it as legacy fallback).
     *
     * zh-CN:
     *
     * 通过 View#findOnBackInvokedDispatcher() 为已附加的 View 安装返回处理器 (API 33+).
     *
     * 注:
     * - 适用于 overlay / WindowManager.addView 的根视图.
     * - attach 时注册, detach 时反注册.
     * - 不强依赖 dispatchKeyEvent (你可以保留作为旧系统 fallback).
     */
    @JvmStatic
    @JvmOverloads
    fun installViewBackHandler(
        view: View,
        priority: Priority = Priority.OVERLAY,
        onBack: () -> Unit,
    ): Handle {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return NOOP_HANDLE
        return Api33.installViewBackHandler(view, priority, onBack)
    }

    private fun isActivityContext(context: Context?): Boolean =
        when (context) {
            null -> false
            is Activity -> true
            is ContextWrapper -> isActivityContext(context.baseContext)
            else -> false
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private object Api33 {

        private fun toPlatformPriority(p: Priority): Int = when (p) {
            Priority.DEFAULT -> OnBackInvokedDispatcher.PRIORITY_DEFAULT
            Priority.OVERLAY -> OnBackInvokedDispatcher.PRIORITY_OVERLAY
        }

        fun installDialogBackHandler(
            dialog: Dialog,
            priority: Priority,
            fallback: Fallback,
            onBack: (DialogInterface) -> Boolean,
        ) {
            // Ensure window/decorView exists (best effort).
            // zh-CN: 尽力确保 window/decorView 可用.
            runCatching { if (dialog.window == null) dialog.create() }

            val decor = dialog.window?.decorView ?: run {
                // Worst-case: no decorView, register directly (no auto cleanup).
                // zh-CN: 极端情况: 没拿到 decorView, 直接注册 (无法自动清理).
                val dispatcher = dialog.onBackInvokedDispatcher
                val cb = OnBackInvokedCallback {
                    val consumed = onBack(dialog)
                    if (!consumed) applyFallback(dialog, fallback)
                }
                dispatcher.registerOnBackInvokedCallback(toPlatformPriority(priority), cb)
                return
            }

            // Prevent duplicate installation on same decor view.
            // zh-CN: 防重复安装.
            if (decor.getTag(TAG_KEY_INSTALLED) == true) return
            decor.setTag(TAG_KEY_INSTALLED, true)

            var dispatcher: OnBackInvokedDispatcher? = null
            var callback: OnBackInvokedCallback? = null

            fun unregister() {
                val d = dispatcher
                val c = callback
                if (d != null && c != null) runCatching { d.unregisterOnBackInvokedCallback(c) }
                dispatcher = null
                callback = null
            }

            // Register on attach, unregister on detach.
            // zh-CN: attach 注册, detach 反注册.
            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    unregister()
                    val d = dialog.onBackInvokedDispatcher
                    val c = OnBackInvokedCallback {
                        val consumed = onBack(dialog)
                        if (!consumed) applyFallback(dialog, fallback)
                    }
                    d.registerOnBackInvokedCallback(toPlatformPriority(priority), c)
                    dispatcher = d
                    callback = c
                }

                override fun onViewDetachedFromWindow(v: View) {
                    unregister()
                    // Keep listener for potential re-show; decorView may re-attach.
                    // zh-CN: 保留 listener, 以支持可能的重复 show().
                }
            }

            decor.addOnAttachStateChangeListener(listener)
            if (decor.isAttachedToWindow) listener.onViewAttachedToWindow(decor)
        }

        fun installViewBackHandler(
            view: View,
            priority: Priority,
            onBack: () -> Unit,
        ): Handle {
            // Prevent duplicate installation on same view.
            // zh-CN: 防重复安装.
            if (view.getTag(TAG_KEY_INSTALLED) == true) return NOOP_HANDLE
            view.setTag(TAG_KEY_INSTALLED, true)

            var dispatcher: OnBackInvokedDispatcher? = null
            var callback: OnBackInvokedCallback? = null

            fun unregister() {
                val d = dispatcher
                val c = callback
                if (d != null && c != null) runCatching { d.unregisterOnBackInvokedCallback(c) }
                dispatcher = null
                callback = null
            }

            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    unregister()
                    val d = v.findOnBackInvokedDispatcher() ?: return
                    val c = OnBackInvokedCallback { onBack() }
                    d.registerOnBackInvokedCallback(toPlatformPriority(priority), c)
                    dispatcher = d
                    callback = c
                }

                override fun onViewDetachedFromWindow(v: View) {
                    unregister()
                }
            }

            view.addOnAttachStateChangeListener(listener)
            if (view.isAttachedToWindow) listener.onViewAttachedToWindow(view)

            return Handle {
                view.removeOnAttachStateChangeListener(listener)
                unregister()
                view.setTag(TAG_KEY_INSTALLED, null)
            }
        }

        private fun applyFallback(dialog: Dialog, fallback: Fallback) {
            when (fallback) {
                Fallback.NOOP -> Unit
                Fallback.ALWAYS_DISMISS -> runCatching { dialog.dismiss() }
                Fallback.DISMISS_IF_CANCELABLE -> {
                    if (dialog.isShowing) {
                        runCatching { dialog.cancel() }.onFailure { runCatching { dialog.dismiss() } }
                    }
                }
            }
        }
    }
}
