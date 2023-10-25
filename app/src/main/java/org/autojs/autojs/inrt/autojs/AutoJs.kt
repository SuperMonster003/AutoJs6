package org.autojs.autojs.inrt.autojs

import android.app.Application
import android.content.Context
import org.autojs.autojs.inrt.LogActivity
import org.autojs.autojs.inrt.SettingsActivity
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.AutoJs as AutoJsApp

/**
 * Created by Stardust on 2017/4/2.
 */
class AutoJs(application: Application) : AutoJsApp(application) {

    override fun createAppUtils(context: Context) = AppUtils(context, context.packageName + ".fileprovider")

    override fun createRuntime() = super.createRuntime().apply {
        putProperty("class.settings", SettingsActivity::class.java)
        putProperty("class.console", LogActivity::class.java)
    }

    companion object {

        val instance
            get() = AutoJsApp.instance

    }
}
