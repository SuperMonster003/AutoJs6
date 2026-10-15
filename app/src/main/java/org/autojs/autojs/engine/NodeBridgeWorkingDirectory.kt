package org.autojs.autojs.engine

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import org.autojs.autojs.app.GlobalAppContext
import java.io.File

/**
 * Says why the AutoJs6 working directory cannot serve a bridge call that works inside it (`files.*`,
 * `engines.execScript` / `execScriptFile`, `rhino.run`), instead of a generic "not available" or a
 * policy message that lists every possible cause. A freshly installed AutoJs6 that was never opened, or
 * one whose working directory was deleted, has the path configured but no directory behind it.
 * zh-CN: 说明 AutoJs6 工作目录为何无法服务于在其中工作的桥接调用 (`files.*`, `engines.execScript` / `execScriptFile`, `rhino.run`),
 * 而不是笼统的 "不可用" 或罗列所有可能原因的策略消息. 刚安装且从未打开过的 AutoJs6, 或工作目录已被删除时, 路径已配置但目录并不存在.
 */
object NodeBridgeWorkingDirectory {

    /**
     * Null when [path] is an existing directory; otherwise one sentence naming the path, the cause and
     * what the user can do. [storageAccess] tells whether AutoJs6 may see shared storage at all, so a
     * directory hidden by a missing permission is not reported as missing.
     * zh-CN: [path] 为已存在的目录时返回 null; 否则返回一句话, 说明路径, 原因与用户可以怎么做. [storageAccess] 表示 AutoJs6 能否访问共享存储,
     * 以免把因缺少权限而看不到的目录报告为不存在.
     */
    @JvmStatic
    fun problem(path: String, storageAccess: () -> Boolean = ::hasStorageAccess): String? {
        if (path.isBlank()) {
            return "AutoJs6 has no working directory set; choose one in AutoJs6 > Settings > File explorer > Working directory path, then retry."
        }
        val directory = File(path)
        if (directory.isDirectory) {
            return null
        }
        if (directory.exists()) {
            return "the AutoJs6 working directory $path is a file, not a directory; " +
                    "choose another one in AutoJs6 > Settings > File explorer > Working directory path, then retry."
        }
        if (!storageAccess()) {
            return "AutoJs6 cannot reach its working directory $path because it has no storage access; " +
                    "grant AutoJs6 All files access (Android 11 or later) or the storage permission, then retry."
        }
        return "the AutoJs6 working directory $path does not exist (AutoJs6 has not created it yet, or it was deleted); " +
                "create that folder on the phone, or choose an existing one in AutoJs6 > Settings > File explorer > Working directory path, then retry."
    }

    /** [problem] as a bridge error with its own code, or null when the working directory is usable. */
    @JvmStatic
    fun error(path: String, module: String?, method: String?, storageAccess: () -> Boolean = ::hasStorageAccess): NodeBridgeError? {
        val message = problem(path, storageAccess) ?: return null
        return NodeBridgeError.providerFailed(
            message = if (module != null && method != null) "$module.$method: $message" else message,
            module = module,
            method = method,
            code = NodeRuntimeErrorCodes.WORKING_DIRECTORY_UNAVAILABLE,
        )
    }

    private fun hasStorageAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        else -> runCatching {
            GlobalAppContext.get().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(true)
    }
}
