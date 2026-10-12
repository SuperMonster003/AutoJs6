package org.autojs.autojs.tool

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.autojs.autojs.util.ViewUtils

/**
 * Created by Stardust on May 2, 2017.
 * Transformed by SuperMonster003 on Oct 25, 2023.
 * Modified by SuperMonster003 as of Jan 30, 2026.
 */
class UiHandler(val applicationContext: Context) : Handler(Looper.getMainLooper()) {

    /**
     * The Auto.js 4 accessors of `com.stardust.util.UiHandler`; that class name resolves to this class through
     * `StardustClassAliases` (roadmap P7.3).
     * zh-CN: Auto.js 4 `com.stardust.util.UiHandler` 的访问器; 该类名经 `StardustClassAliases` 解析到本类 (roadmap P7.3).
     */
    val context: Context
        get() = applicationContext

    fun toast(message: String) {
        post { ViewUtils.showToast(applicationContext, message) }
    }

    fun toast(resId: Int) {
        post { ViewUtils.showToast(applicationContext, resId) }
    }

}
