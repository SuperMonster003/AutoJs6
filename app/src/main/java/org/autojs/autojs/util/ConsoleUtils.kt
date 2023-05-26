package org.autojs.autojs.util

import android.content.Context
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.ui.log.LogActivity

object ConsoleUtils {

    @JvmStatic
    @JvmOverloads
    fun launch(context: Context? = GlobalAppContext.get()) = try {
        context?.let { LogActivity.launch(it) }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

}