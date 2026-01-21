package org.autojs.autojs.permission

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
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
class XiaomiBackgroundPopupPermission(
    override val context: Context,
) : PermissionItemHelper {

    override fun has(): Boolean {
        if (!RomUtils.isMiui()) return true
        return isMiuiBgStartPermissionGranted(context)
    }

    override fun request(): Boolean = config()

    override fun revoke(): Boolean = config()

    /**
     * Open OEM settings page for this permission.
     * zh-CN: 打开此权限对应的厂商设置页面.
     */
    fun config(): Boolean {
        if (!RomUtils.isMiui()) {
            return openAppDetails()
        }

        val pkg = context.packageName

        val intents = listOf(
            // MIUI 8+ commonly uses this activity.
            // zh-CN: MIUI 8+ 常见使用此 Activity.
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity",
                )
                .putExtra("extra_pkgname", pkg),

            // Some MIUI versions use AppPermissionsEditorActivity.
            // zh-CN: 部分 MIUI 版本使用 AppPermissionsEditorActivity.
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.AppPermissionsEditorActivity",
                )
                .putExtra("extra_pkgname", pkg),
        )

        return tryStartActivities(intents) || openAppDetails()
    }

    private fun openAppDetails(): Boolean = Intent()
        .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData("package:${context.packageName}".toUri())
        .let { tryStartActivity(it) }

    /**
     * Check MIUI "background start activity" app-op (commonly op=10021).
     * zh-CN: 检查 MIUI "后台启动界面" 的 AppOps 状态 (常见 op=10021).
     */
    private fun isMiuiBgStartPermissionGranted(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return true

        return runCatching {
            val op = OP_BACKGROUND_START_ACTIVITY
            val method = ops.javaClass.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
            )
            val mode = method.invoke(ops, op, Process.myUid(), context.packageName) as? Int
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrElse {
            it.printStackTrace()
            // Fail-open to avoid blocking non-MIUI or changed ROM implementations.
            // zh-CN: 发生异常时放行, 避免 ROM 变更导致误判并阻塞功能.
            true
        }
    }

    // /**
    //  * Check whether this looks like a Xiaomi/Redmi/POCO device.
    //  * zh-CN: 判断是否为 Xiaomi/Redmi/POCO 设备 (经验判断).
    //  */
    // private fun isXiaomiLikeDevice(): Boolean {
    //     val brand = (Build.BRAND ?: "").lowercase()
    //     val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
    //     return manufacturer == "xiaomi" || brand == "xiaomi" || brand == "redmi" || brand == "poco"
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

        // MIUI app-op code for background start activity (commonly 10021).
        // zh-CN: MIUI 后台启动界面常见的 AppOps 编号 (通常为 10021).
        private const val OP_BACKGROUND_START_ACTIVITY = 10021

    }

}
