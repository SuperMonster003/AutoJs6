package org.autojs.autojs.ui.floating.controller

import android.content.Context
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.permission.DisplayOverOtherAppsPermission
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * Keeps at most one floating controller per script path and opens new ones on request.
 * zh-CN: 每个脚本路径最多保留一个悬浮控制器, 并按需打开新的控制器.
 *
 * Created by SuperMonster003 on Sep 14, 2026.
 */
object FloatingControllerManager {

    private val mWindows = mutableMapOf<String, FloatingControllerWindow>()

    /**
     * Shows the controller for [file] (reusing an open one) and runs the script right away
     * when [runImmediately] is set. Returns false when the overlay permission is missing.
     *
     * zh-CN: 显示 [file] 的控制器 (复用已打开的), [runImmediately] 为 true 时立即运行脚本. 缺少悬浮窗权限时返回 false.
     */
    @JvmStatic
    @JvmOverloads
    fun start(context: Context, file: ScriptFile, runImmediately: Boolean = true): Boolean {
        val permission = DisplayOverOtherAppsPermission(context)
        if (!permission.has()) {
            ViewUtils.showToast(context, R.string.error_no_display_over_other_apps_permission)
            permission.config()
            return false
        }
        val key = file.path
        val existing = mWindows[key]?.takeUnless { it.isClosed }
        if (existing != null) {
            if (runImmediately) existing.controller.run(context, interactive = true)
            return true
        }
        val controller = ScriptExecutionController(file)
        val window = FloatingControllerWindow(controller) { closed -> mWindows.remove(key, closed) }
        mWindows[key] = window
        if (!FloatyWindowManger.addWindow(context, window)) {
            mWindows.remove(key, window)
            controller.release()
            return false
        }
        if (runImmediately) controller.run(context, interactive = true)
        return true
    }

    @JvmStatic
    fun closeAll() {
        mWindows.values.toList().forEach { it.close() }
        mWindows.clear()
    }

}
