package org.autojs.autojs.inrt.autojs

import android.app.Application
import android.content.Context
import org.autojs.autojs.inrt.LogActivity
import org.autojs.autojs.inrt.SettingsActivity
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.AutoJs as AutoJsApp

/**
 * Created by Stardust on Apr 2, 2017.
 */
class AutoJs(application: Application) : AutoJsApp(application) {

    override fun createAppUtils(context: Context): AppUtils {
        val authorityName = "${context.packageName}.fileprovider"
        return AppUtils(context, authorityName)
    }

    override fun createRuntime() = super.createRuntime().apply {
        putProperty("class.settings", SettingsActivity::class.java)
        putProperty("class.console", LogActivity::class.java)
    }

    companion object {

        val instance
            get() = AutoJsApp.instance

    }

}
