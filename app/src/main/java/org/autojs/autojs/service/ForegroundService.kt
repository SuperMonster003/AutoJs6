package org.autojs.autojs.service

import android.content.Context
import android.content.Intent
import android.os.Build
import org.autojs.autojs.external.foreground.MainActivityForegroundService
import org.autojs.autojs.ui.main.drawer.ServiceItemHelper
import org.autojs.autojs.util.ForegroundServiceUtils

class ForegroundService(override val context: Context) : ServiceItemHelper {

    private val mClassName = MainActivityForegroundService::class.java

    override val isRunning
        get() = ForegroundServiceUtils.isRunning(context, mClassName)

    override fun start() = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
            context.startForegroundService(Intent(context, mClassName)) != null
        }
        else -> context.startService(Intent(context, mClassName)) != null
    }

    override fun stop() = context.stopService(Intent(context, mClassName))

}