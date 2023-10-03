@file:Suppress("unused")

package org.autojs.autojs.util

import android.app.AppOpsManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.pref.Language
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader


/**
 * Created by SuperMonster003 on Aug 17, 2023.
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
        val uri = Uri.parse("content://com.vivo.permissionmanager.provider.permission/start_bg_activity")
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

    fun isMiui() = getSystemProperty("ro.miui.ui.version.name").isNotEmpty()

    fun isEmui() = getSystemProperty("ro.build.version.emui").isNotEmpty()

    fun isOppo() = getSystemProperty("ro.build.version.opporom").isNotEmpty()

    fun isSmartisan() = getSystemProperty("ro.smartisan.version").isNotEmpty()

    fun isVivo() = getSystemProperty("ro.vivo.os.version").isNotEmpty()

    /** 金立. */
    fun isGionee() = getSystemProperty("ro.gn.sv.version").isNotEmpty()

    fun isLenovo() = getSystemProperty("ro.lenovo.lvp.version").isNotEmpty()

    fun isFlyme() = Build.DISPLAY.lowercase(Language.getPrefLanguage().locale).contains("flyme")

    private fun getSystemProperty(propName: String): String = try {
        BufferedReader(
            InputStreamReader(
                Runtime.getRuntime().exec("getprop $propName").inputStream
            ), 1024
        ).use { it.readLine() }
    } catch (ex: IOException) {
        ""
    }

    /* -=-=- --------------- -=-=- */
    /* -=-=- Brand / Product -=-=- */
    /* -=-=- --------------- -=-=- */

    object Brand {

        /** Sony (索尼) Xperia. */
        fun isXperia() = getSystemProperty("ro.semc.product.name").contains("xperia", true)

        /** Redmi (红米). */
        fun isRedmi() = getSystemProperty("ro.product.model").contains("redmi", true)

        /** Mi Mix. */
        fun isMiMix() = Build.MODEL.contains("mi mix", true)

    }

}
