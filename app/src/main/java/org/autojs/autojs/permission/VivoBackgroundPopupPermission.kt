package org.autojs.autojs.permission

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import org.autojs.autojs.ui.main.drawer.PermissionItemHelper
import org.autojs.autojs.util.IntentUtils.start
import org.autojs.autojs.util.RomUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on Jan 16, 2026.
 */
class VivoBackgroundPopupPermission(
    override val context: Context,
) : PermissionItemHelper {

    override fun has(): Boolean {
        if (!RomUtils.isVivo()) return true
        return getVivoBgStartPermissionStatus(context) == STATE_ALLOWED
    }

    override fun request(): Boolean = config()

    override fun revoke(): Boolean = config()

    /**
     * Open OEM settings page for this permission.
     * zh-CN: 打开此权限对应的厂商设置页面.
     */
    fun config(): Boolean {
        if (!RomUtils.isVivo()) {
            return openAppDetails()
        }

        val pkg = context.packageName

        val intents = listOf(
            // Vivo permission detail page (commonly works on many models).
            // zh-CN: Vivo 权限详情页 (较多机型可用).
            Intent()
                .setClassName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity",
                )
                .setAction("secure.intent.action.softPermissionDetail")
                .putExtra("packagename", pkg),

            // Older models may use PurviewTabActivity.
            // zh-CN: 老机型可能使用 PurviewTabActivity.
            Intent()
                .setClassName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.PurviewTabActivity",
                )
                .putExtra("packagename", pkg)
                .putExtra("tabId", "1"),

            // Some ROMs expose a background start manager page.
            // zh-CN: 部分 ROM 暴露了后台启动管理页.
            Intent()
                .setClassName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                ),
        )

        return tryStartActivities(intents) || openAppDetails()
    }

    private fun openAppDetails(): Boolean = Intent()
        .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData("package:${context.packageName}".toUri())
        .let { tryStartActivity(it) }

    /**
     * Query vivo PermissionProvider "start_bg_activity" table.
     * zh-CN: 查询 vivo 权限 Provider 的 "start_bg_activity" 表.
     */
    @SuppressLint("Range")
    private fun getVivoBgStartPermissionStatus(context: Context): Int {
        val uri = "content://com.vivo.permissionmanager.provider.permission/start_bg_activity".toUri()
        val selection = "pkgname = ?"
        val selectionArgs = arrayOf(context.packageName)
        var state = STATE_DENIED

        runCatching {
            context.contentResolver.query(uri, null, selection, selectionArgs, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(COLUMN_CURRENT_STATE)
                    if (idx >= 0) {
                        state = c.getInt(idx)
                    }
                }
            }
        }.onFailure {
            it.printStackTrace()
            // Keep default denied on query failure.
            // zh-CN: 查询失败时保持默认无权限.
        }

        return state
    }

    // /**
    //  * Check whether this looks like a vivo/iQOO device.
    //  * zh-CN: 判断是否为 vivo/iQOO 设备 (经验判断).
    //  */
    // private fun isVivoLikeDevice(): Boolean {
    //     val brand = (Build.BRAND ?: "").lowercase()
    //     val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
    //     return manufacturer == "vivo" || brand == "vivo" || brand == "iqoo"
    // }

    private fun tryStartActivities(intents: List<Intent>): Boolean {
        for (i in intents) {
            if (tryStartActivity(i)) return true
        }
        return false
    }

    private fun tryStartActivity(i: Intent): Boolean = runCatching {
        val pm = context.packageManager
        i.resolveActivity(pm) ?: return@runCatching false
        i.start(context)
        true
    }.onFailure {
        it.printStackTrace()
        ViewUtils.showToast(context, R.string.text_failed)
    }.getOrDefault(false)

    private companion object {

        private const val STATE_ALLOWED = 0
        private const val STATE_DENIED = 1

        private const val COLUMN_CURRENT_STATE = "currentstate"

    }

}
