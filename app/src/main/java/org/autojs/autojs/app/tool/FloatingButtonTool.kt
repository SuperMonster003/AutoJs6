package org.autojs.autojs.app.tool

import android.app.Activity
import android.content.Context
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.ui.main.drawer.ShowableItemHelper

/**
 * Created by SuperMonster003 on Jun 24, 2022.
 */
open class FloatingButtonTool(final override val context: Context) : ShowableItemHelper {

    override val isShowing
        get() = FloatyWindowManger.isCircularMenuShowing()

    override val isInMainThread = true

    override fun show(): Boolean {
        FloatyWindowManger.showCircularMenu(context)
        // @Comment by SuperMonster003 on Nov 16, 2023.
        //  ! Seems irrational to force a11y service to start along with showing floating button.
        //  ! zh-CN: 看起来强制无妨碍服务伴随浮动窗口显示而启动并非合理.
        // return when (isShowing) {
        //     true -> true.also { mAccessibilityService.startIfNeeded() }
        //     else -> false
        // }
        return isShowing
    }

    override fun showIfNeeded() {
        if (!isShowing) {
            FloatyWindowManger.showCircularMenuIfNeeded(context)
        }
    }

    override fun hide() = runCatching {
        val isSaveState = !(context as Activity).isFinishing
        FloatyWindowManger.hideCircularMenu(isSaveState)
    }.isSuccess

}