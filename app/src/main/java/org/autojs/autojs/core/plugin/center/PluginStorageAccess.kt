package org.autojs.autojs.core.plugin.center

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared storage access of the runtime plugins (api-coverage Q228 (b)). A plugin process has its own UID, so its file I/O
 * does not use the grants of AutoJs6, and the plugin has no screen to ask for its own. Each execution watches the error
 * output of the script ([Prompt]): the first refused access to shared storage, while the plugin holds no grant, opens one prompt
 * that leads to the plugin's page in system settings. The error itself reaches the script unchanged.
 * zh-CN: 运行时插件的共享存储授权 (api-coverage Q228 (b)). 插件进程有自己的 UID, 其文件读写用不上 AutoJs6 的授权, 插件也没有界面可以申请. 每次执行观察脚本的
 * 错误输出 ([Prompt]): 插件未授权时, 共享存储上第一次被拒绝的访问打开一次提示, 通向系统设置里该插件的页面. 错误本身照常到达脚本.
 */
object PluginStorageAccess {

    const val NODE_PACKAGE = "io.github.supermonster003.autojs6.plugin.nodejs"
    const val BUN_PACKAGE = "io.github.supermonster003.autojs6.plugin.bun.runtime"

    // The platform keeps AppOpsManager.OPSTR_MANAGE_EXTERNAL_STORAGE hidden. zh-CN: 平台未公开 AppOpsManager.OPSTR_MANAGE_EXTERNAL_STORAGE.
    private const val OP_MANAGE_EXTERNAL_STORAGE = "android:manage_external_storage"

    private val refusal = Regex("""\b(EACCES|EPERM)\b""")
    private val sharedStoragePath = Regex("""(^|[^\w/.-])(/sdcard|/mnt/sdcard|/storage/)""")

    /**
     * Whether the output [text] reports a refused access to shared storage in the form of a Node.js or Bun error: EACCES, or
     * EPERM, which Android 11 and later answer to a new file at the top of shared storage. `open failed:` is a Java error of the
     * host process, which a grant to the plugin does not change.
     * zh-CN: 输出 [text] 是否以 Node.js 或 Bun 错误的形式报告了共享存储访问被拒绝: EACCES, 或 EPERM (Android 11 起在共享存储根目录新建文件时的错误).
     * `open failed:` 是宿主进程的 Java 错误, 给插件授权改变不了它.
     */
    fun isDenied(text: CharSequence): Boolean = "open failed:" !in text && refusal.containsMatchIn(text) && sharedStoragePath.containsMatchIn(text)

    /**
     * All files access of the package from Android 11 on, the storage permission before. The system may keep the appop of
     * another package from AutoJs6: that counts as no grant, so the worst case is a prompt for a granted plugin.
     * zh-CN: Android 11 起是该包的所有文件访问权限, 之前是存储权限. 系统可能不让 AutoJs6 查询其他包的 appop: 此时按未授权处理, 最坏情况是对已授权的插件提示一次.
     */
    fun isGranted(context: Context, packageName: String): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> runCatching {
            val uid = context.packageManager.getApplicationInfo(packageName, 0).uid
            context.getSystemService(AppOpsManager::class.java)
                .unsafeCheckOpNoThrow(OP_MANAGE_EXTERNAL_STORAGE, uid, packageName) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
        else -> context.packageManager.checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, packageName) == PackageManager.PERMISSION_GRANTED
    }

    /** Opens the system page that grants the access of [packageName]. zh-CN: 打开为 [packageName] 授权的系统页面. */
    fun openSettings(context: Context, packageName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            IntentUtils.launchAppDetailsSettings(context, packageName)
            return
        }
        val opened = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", packageName, null)).startSafely(context)
        if (!opened) Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).startSafely(context)
    }

    /** The prompt of one execution, shown at most once. zh-CN: 一次执行的提示, 最多显示一次. */
    class Prompt(context: Context, private val packageName: String) {

        private val appContext = context.applicationContext
        private val shown = AtomicBoolean(false)

        /** To be called with the script's error output. zh-CN: 以脚本的错误输出调用. */
        fun onErrorOutput(text: String) {
            if (shown.get() || !isDenied(text) || isGranted(appContext, packageName) || !shown.compareAndSet(false, true)) return
            // An unanswered prompt may wait behind other apps: it gives way, or the system would bring it back with its old plugin.
            // zh-CN: 未作答的提示可能留在其他应用之后: 让它让位, 否则系统会把它连同旧的插件一起带回前台.
            Intent(appContext, PluginStorageAccessActivity::class.java)
                .putExtra(PluginStorageAccessActivity.EXTRA_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .startSafely(appContext)
        }
    }
}
