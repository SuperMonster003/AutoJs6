package org.autojs.autojs.app.tool

import android.app.Activity
import android.content.Context
import org.autojs.autojs.service.AccessibilityService
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.ui.main.drawer.RunnableItemHelper

/**
 * Created by SuperMonster003 on Jun 24, 2022.
 */
open class FloatingButtonTool(final override val context: Context) : RunnableItemHelper {

    private val mAccessibilityService: AccessibilityService = AccessibilityService(context)

    override val isRunning
        get() = FloatyWindowManger.isCircularMenuShowing()

    override val isInMainThread = true

    override fun launch() {
        FloatyWindowManger.showCircularMenu(context)
        startAccessibilityServiceIfNeeded()
    }

    private fun startAccessibilityServiceIfNeeded() {
        if (isRunning && !mAccessibilityService.isRunning) {
            mAccessibilityService.start()
        }
    }

    override fun launchIfNeeded() {
        if (!isRunning) {
            FloatyWindowManger.showCircularMenuIfNeeded(context)
        }
    }

    override fun close() {
        val isSaveState = !(context as Activity).isFinishing
        FloatyWindowManger.hideCircularMenu(isSaveState)
    }

}