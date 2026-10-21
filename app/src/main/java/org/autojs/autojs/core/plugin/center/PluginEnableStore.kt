package org.autojs.autojs.core.plugin.center

import android.content.Context
import org.autojs.autojs.core.pref.keys.NamedStores
import org.autojs.autojs.core.pref.keys.PluginCenterKeys
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.set

/**
 * Enable and suspension state per plugin in the named store `plugin_center_enable_state`, the key families
 * [PluginCenterKeys.PLUGIN_ENABLED] and [PluginCenterKeys.PLUGIN_SUSPENDED] of the category `plugin_enable_state`
 * (docs/settings/ROADMAP.md P3.1, names unchanged); the store is opened by the settings layer with the application
 * context, as before.
 */
object PluginEnableStore {

    fun isEnabled(context: Context, packageName: String, defaultEnabled: Boolean = true): Boolean {
        val enabled = key(packageName).storedOrNull() ?: defaultEnabled
        if (enabled && !PluginLocalNetworkPermission.isGranted(context, packageName)) {
            setEnabled(context, packageName, false)
            return false
        }
        return enabled
    }

    /** The user's switch or a policy decision; it also clears a suspension. zh-CN: 用户开关或策略决定; 同时清除挂起. */
    fun setEnabled(context: Context, packageName: String, enabled: Boolean) {
        val key = key(packageName)
        val allowed = enabled && PluginLocalNetworkPermission.isGranted(context, packageName)
        val wasSuspended = suspendedKey(packageName).get()
        if (key.isStored() && key.get() == allowed && !wasSuspended) return
        write(packageName, PluginEnableSuspension.onDecision(allowed))
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
        write(packageName, next)
        PluginStateChangeRegistry.notifyChanged(packageName)
    }

    /**
     * Switches a suspended plugin back on after a discovery reached it; true when it is enabled again.
     * The caller has already checked authorization and the host version.
     * zh-CN: 发现重新连上插件后恢复被挂起的插件; 重新启用时返回 true. 调用方已检查授权与宿主版本.
     */
    fun restoreIfSuspended(context: Context, packageName: String): Boolean {
        val current = PluginEnableSuspension.State(key(packageName).storedOrNull() ?: false, isSuspended(context, packageName))
        val next = PluginEnableSuspension.onReachable(current, usable = PluginLocalNetworkPermission.isGranted(context, packageName))
        if (next == current) return false
        write(packageName, next)
        PluginStateChangeRegistry.notifyChanged(packageName)
        return next.enabled
    }

    @Suppress("UNUSED_PARAMETER")
    fun isSuspended(context: Context, packageName: String): Boolean = suspendedKey(packageName).get()

    /** One write of both keys, as the single SharedPreferences edit before. */
    private fun write(packageName: String, state: PluginEnableSuspension.State) = SettingsStores[NamedStores.PLUGIN_CENTER_ENABLE_STATE].edit {
        this[key(packageName)] = state.enabled
        if (state.suspended) this[suspendedKey(packageName)] = true else remove(suspendedKey(packageName).name)
    }

    /** `key_$_enabled_plugin_$_<package name>`. */
    private fun key(packageName: String) = PluginCenterKeys.PLUGIN_ENABLED.key(packageName)

    /** `key_$_suspended_plugin_$_<package name>`. */
    private fun suspendedKey(packageName: String) = PluginCenterKeys.PLUGIN_SUSPENDED.key(packageName)

}
