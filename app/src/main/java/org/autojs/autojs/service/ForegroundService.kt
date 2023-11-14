package org.autojs.autojs.service

import android.content.Context
import org.autojs.autojs.external.foreground.MainActivityForegroundService
import org.autojs.autojs.ui.main.drawer.ServiceItemHelper
import org.autojs.autojs.util.ForegroundServiceUtils

class ForegroundService(override val context: Context) : ServiceItemHelper {

    private val mClassName = MainActivityForegroundService::class.java

    override val isRunning
        get() = ForegroundServiceUtils.isRunning(context, mClassName)

    override fun start(): Boolean {
        return ForegroundServiceUtils.startService(context, mClassName)
    }

    override fun stop(): Boolean {
        return ForegroundServiceUtils.stopServiceIfNeeded(context, mClassName)
    }

}