package org.autojs.autojs.app.tool

import android.app.Activity
import android.content.Context
import org.autojs.autojs.service.AccessibilityService
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.ui.main.drawer.ShowableItemHelper

/**
 * Created by SuperMonster003 on Jun 24, 2022.
 */
open class FloatingButtonTool(final override val context: Context) : ShowableItemHelper {

    private val mAccessibilityService: AccessibilityService = AccessibilityService(context)

    override val isShowing
        get() = FloatyWindowManger.isCircularMenuShowing()

    override val isInMainThread = true

    override fun show(): Boolean {
        FloatyWindowManger.showCircularMenu(context)
        return when (isShowing) {
            true -> true.also { mAccessibilityService.startIfNeeded() }
            else -> false
        }
    }

    override fun showIfNeeded() {
        if (!isShowing) {
            FloatyWindowManger.showCircularMenuIfNeeded(context)
        }
    }

    override fun hide() = try {
        val isSaveState = !(context as Activity).isFinishing
        FloatyWindowManger.hideCircularMenu(isSaveState)
        true
    } catch (_: Exception) {
        false
    }

}