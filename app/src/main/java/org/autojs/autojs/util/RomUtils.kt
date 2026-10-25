@file:Suppress("unused")

package org.autojs.autojs.util

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.net.toUri
import ezy.assist.compat.RomUtil
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.runtime.api.modules.sysprops.SystemProperties

/**
 * Created by SuperMonster003 on Aug 17, 2023.
 * Modified by SuperMonster003 as of Mar 30, 2026.
 */
// @Reference to https://blog.csdn.net/liao_fu_yun/article/details/114971424
object RomUtils {

    @JvmStatic
    @JvmOverloads
    fun isBackgroundStartGranted(context: Context = GlobalAppContext.get()) = when {
        isMiui() -> isMiuiBgStartPermissionGranted(context)
        isVivo() -> isVivoBgStartPermissionGranted(context)
        isOppo() -> Settings.canDrawOverlays(context)
        else -> true
    }

    private fun isMiuiBgStartPermissionGranted(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        try {
            val op = 10021
            val method = ops.javaClass.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
            )
            val result = method.invoke(ops, op, Process.myUid(), context.packageName) as Int
            return result == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun isVivoBgStartPermissionGranted(context: Context): Boolean {
        return getVivoBgStartPermissionStatus(context) == 0
    }

    /**
     * 判断 Vivo 后台弹出界面状态. 1: 无权限; 0: 有权限.
     */
    private fun getVivoBgStartPermissionStatus(context: Context): Int {
        val uri = "content://com.vivo.permissionmanager.provider.permission/start_bg_activity".toUri()
        val selection = "pkgname = ?"
        val selectionArgs = arrayOf(context.packageName)
        var state = 1
        try {
            context.contentResolver.query(uri, null, selection, selectionArgs, null)?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndex("currentstate")
                    if (columnIndex >= 0) {
                        state = it.getInt(columnIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return state
    }

    /* -=-=- --------------------------------------- -=-=- */
    /* -=-=- Android-based OS, including custom ROMs -=-=- */
    /* -=-=- --------------------------------------- -=-=- */

    fun isMiui() = !SystemProperties.get("ro.miui.ui.version.name").isNullOrEmpty()

    fun isEmui() = !SystemProperties.get("ro.build.version.emui").isNullOrEmpty()

    fun isOppo() = !SystemProperties.get("ro.build.version.opporom").isNullOrEmpty()

    fun isSmartisan() = !SystemProperties.get("ro.smartisan.version").isNullOrEmpty()

    fun isVivo() = !SystemProperties.get("ro.vivo.os.version").isNullOrEmpty()

    /** 金立. */
    fun isGionee() = !SystemProperties.get("ro.gn.sv.version").isNullOrEmpty()

    fun isLenovo() = !SystemProperties.get("ro.lenovo.lvp.version").isNullOrEmpty()

    fun isFlyme() = Build.DISPLAY.lowercase(Language.getPrefLanguage().locale).contains("flyme")

    fun isQiku() = RomUtil.isQiku()

    /* -=-=- --------------- -=-=- */
    /* -=-=- Brand / Product -=-=- */
    /* -=-=- --------------- -=-=- */

    object Brand {

        /** Sony (索尼) Xperia. */
        fun isXperia() = SystemProperties.get("ro.semc.product.name")?.contains("xperia", true) == true

        /** Redmi (红米). */
        fun isRedmi() = SystemProperties.get("ro.product.model")?.contains("redmi", true) == true

        /** Mi Mix. */
        fun isMiMix() = Build.MODEL.contains("mi mix", true)
    }
}
