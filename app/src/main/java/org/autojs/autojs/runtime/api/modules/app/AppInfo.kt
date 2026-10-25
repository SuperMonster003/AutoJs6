package org.autojs.autojs.runtime.api.modules.app

import android.content.Context
import android.content.pm.ApplicationInfo

class AppInfo(context: Context, applicationInfo: ApplicationInfo) : ApplicationInfo(applicationInfo) {

    @JvmField
    val label: String = loadLabel(context.packageManager).toString()
}
