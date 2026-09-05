package org.autojs.autojs.core.accessibility.control

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings.Secure
import org.autojs.autojs.runtime.api.ProcessShell
import org.autojs.autojs.runtime.api.WrappedShizuku

/**
 * The Android-side [EnabledServicesStore] implementations behind the start strategies.
 * zh-CN: 启动策略背后的 Android 侧 [EnabledServicesStore] 实现.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */

/** Reads and writes the setting through `settings get / put secure` in a root shell. zh-CN: 经 root shell 的 `settings get / put secure` 读写设置. */
class RootEnabledServicesStore(private val log: (String) -> Unit = {}) : EnabledServicesStore {

    override fun read(): String? {
        return ProcessShell.execCommand("settings get secure ${Secure.ENABLED_ACCESSIBILITY_SERVICES}", true).result
    }

    // The value is single-quoted so that an empty string (AutoJs6 was the only enabled service) is
    // still passed as an argument; an unquoted empty value used to be dropped by the shell,
    // "settings put" then rejected the command and the service silently stayed enabled.
    // zh-CN: 值以单引号包裹, 使空字符串 (AutoJs6 是唯一启用的服务) 仍能作为参数传递;
    // 未加引号的空值会被 shell 丢弃, "settings put" 因而拒绝执行, 服务静默地保持启用状态.
    override fun write(value: String) {
        val quoted = "'${value.replace("'", "'\\''")}'"
        val result = ProcessShell.execCommand("settings put secure ${Secure.ENABLED_ACCESSIBILITY_SERVICES} $quoted", true)
        val error = result.error?.trim().orEmpty()
        if (error.isNotEmpty()) {
            // Not fatal by itself (su wrappers print notices to stderr); the strategy verifies by reading back.
            // zh-CN: 本身不算失败 (su 封装可能向 stderr 打印提示); 策略以回读结果为准.
            log("settings put secure ${Secure.ENABLED_ACCESSIBILITY_SERVICES}: $error")
        }
    }

}

/**
 * Reads and writes the setting through `settings get / put secure` executed by the Shizuku user
 * service (shell or root identity). Binding the user service may take a few seconds on first use,
 * and any Shizuku problem surfaces as an exception, which the strategy counts as a failed attempt.
 * zh-CN: 经 Shizuku 用户服务 (shell 或 root 身份) 执行 `settings get / put secure` 读写设置. 首次使用时绑定用户服务
 * 可能需要几秒; Shizuku 的任何问题都以异常抛出, 策略把它视为一次失败的尝试.
 */
class ShizukuEnabledServicesStore(private val context: Context, private val log: (String) -> Unit = {}) : EnabledServicesStore {

    override fun read(): String? {
        val result = WrappedShizuku.execCommand(context, "settings get secure ${Secure.ENABLED_ACCESSIBILITY_SERVICES}")
        check(result.code == 0) { "settings get secure ${Secure.ENABLED_ACCESSIBILITY_SERVICES} failed: ${result.error}" }
        return result.result?.trim()
    }

    // Same quoting as the root store: an empty list must still reach "settings put" as an argument.
    // zh-CN: 与 root store 相同的引号处理: 空列表也必须作为参数传给 "settings put".
    override fun write(value: String) {
        val quoted = "'${value.replace("'", "'\\''")}'"
        val result = WrappedShizuku.execCommand(context, "settings put secure ${Secure.ENABLED_ACCESSIBILITY_SERVICES} $quoted")
        val error = result.error?.trim().orEmpty()
        if (error.isNotEmpty()) {
            log("settings put secure ${Secure.ENABLED_ACCESSIBILITY_SERVICES} (shizuku): $error")
        }
    }

}

/** Reads and writes the setting directly; writing requires `WRITE_SECURE_SETTINGS`. zh-CN: 直接读写设置; 写入需要 `WRITE_SECURE_SETTINGS` 权限. */
class SecureEnabledServicesStore(private val resolver: ContentResolver) : EnabledServicesStore {

    override fun read(): String? = Secure.getString(resolver, Secure.ENABLED_ACCESSIBILITY_SERVICES)

    override fun write(value: String) {
        Secure.putString(resolver, Secure.ENABLED_ACCESSIBILITY_SERVICES, value)
    }

}
