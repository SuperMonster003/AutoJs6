package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object PluginEnableStore {

    private const val SP_NAME = "plugin_center_enable_state"

    fun isEnabled(context: Context, packageName: String, defaultEnabled: Boolean = true): Boolean {
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val enabled = sp.getBoolean(key(packageName), defaultEnabled)
        if (enabled && !PluginLocalNetworkPermission.isGranted(context, packageName)) {
            setEnabled(context, packageName, false)
            return false
        }
        return enabled
    }

    /** The user's switch or a policy decision; it also clears a suspension. zh-CN: 用户开关或策略决定; 同时清除挂起. */
    fun setEnabled(context: Context, packageName: String, enabled: Boolean) {
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val key = key(packageName)
        val allowed = enabled && PluginLocalNetworkPermission.isGranted(context, packageName)
        val wasSuspended = sp.getBoolean(suspendedKey(packageName), false)
        if (sp.contains(key) && sp.getBoolean(key, true) == allowed && !wasSuspended) return
        write(sp, packageName, PluginEnableSuspension.onDecision(allowed))
        PluginStateChangeRegistry.notifyChanged(packageName)
    }

    /**
     * Switches an enabled plugin off because it cannot be reached right now, remembering that the
     * user did not ask for it (see [PluginEnableSuspension]); a plugin that is already off is left alone.
     * zh-CN: 因插件当前无法连接而关闭已启用的插件, 并记住这不是用户的要求 (参见 [PluginEnableSuspension]); 已关闭的插件保持不变.
     */
    fun suspend(context: Context, packageName: String, defaultEnabled: Boolean) {
        val current = PluginEnableSuspension.State(isEnabled(context, packageName, defaultEnabled), isSuspended(context, packageName))
        val next = PluginEnableSuspension.onUnreachable(current)
        if (next == current) return
        write(context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE), packageName, next)
        PluginStateChangeRegistry.notifyChanged(packageName)
    }

    /**
     * Switches a suspended plugin back on after a discovery reached it; true when it is enabled again.
     * The caller has already checked authorization and the host version.
     * zh-CN: 发现重新连上插件后恢复被挂起的插件; 重新启用时返回 true. 调用方已检查授权与宿主版本.
     */
    fun restoreIfSuspended(context: Context, packageName: String): Boolean {
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val current = PluginEnableSuspension.State(sp.getBoolean(key(packageName), false), isSuspended(context, packageName))
        val next = PluginEnableSuspension.onReachable(current, usable = PluginLocalNetworkPermission.isGranted(context, packageName))
        if (next == current) return false
        write(sp, packageName, next)
        PluginStateChangeRegistry.notifyChanged(packageName)
        return next.enabled
    }

    fun isSuspended(context: Context, packageName: String): Boolean =
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).getBoolean(suspendedKey(packageName), false)

    private fun write(sp: SharedPreferences, packageName: String, state: PluginEnableSuspension.State) = sp.edit {
        putBoolean(key(packageName), state.enabled)
        if (state.suspended) putBoolean(suspendedKey(packageName), true) else remove(suspendedKey(packageName))
    }

    private fun key(packageName: String) = "key_\$_enabled_plugin_\$_$packageName"

    private fun suspendedKey(packageName: String) = "key_\$_suspended_plugin_\$_$packageName"

}
