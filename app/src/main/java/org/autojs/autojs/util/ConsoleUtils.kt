package org.autojs.autojs.util

import android.content.Context
import android.content.Intent
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.ui.log.LogActivity_

object ConsoleUtils {

    @JvmStatic
    @JvmOverloads
    fun launch(context: Context? = GlobalAppContext.get()) = try {
        LogActivity_.intent(context)
            .flags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .start()
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

}