package org.autojs.autojs.core.plugin.center

/**
 * Who switched an installed plugin off, decided without Android so that it can be unit tested.
 *
 * Plugin Center switches an enabled plugin off when it cannot be reached right now, for example
 * when binding fails while the package is being replaced, or a vendor system refuses the binding
 * until the plugin is activated: automatic consumers that select plugins by their enable state must
 * not pick such a plugin. That switch is a suspension rather than the user's choice, so the next
 * discovery that reaches the plugin again with nothing else in the way switches it back on. The
 * user's own switch and policy refusals (authorization, host version, local network permission)
 * replace the stored state and clear any suspension.
 *
 * zh-CN: 判定已安装插件由谁关闭, 不依赖 Android 以便单元测试.
 * 插件当前无法连接时 (例如包被替换期间绑定失败, 或厂商系统在激活前拒绝绑定), 插件中心会关闭已启用的插件,
 * 以免按启用状态选择插件的自动使用方选中它. 这次关闭只是挂起而不是用户的选择, 因此下一次发现重新连上插件且没有其他阻碍时,
 * 插件会自动恢复启用. 用户自己的开关操作及策略性拒绝 (授权, 宿主版本, 本地网络权限) 会覆盖存储的状态并清除挂起.
 */
internal object PluginEnableSuspension {

    /** The stored enable flag, and whether Plugin Center rather than the user cleared it. zh-CN: 存储的启用标志, 以及它是否由插件中心而非用户清除. */
    data class State(val enabled: Boolean, val suspended: Boolean)

    /**
     * A discovery or bind failure: an enabled plugin is switched off and remembered as suspended;
     * a plugin that is already off keeps its state, so a user's own choice is never turned into a suspension.
     * zh-CN: 发现或绑定失败: 已启用的插件被关闭并记为挂起; 已关闭的插件保持原状, 因此用户自己的选择不会变成挂起.
     */
    fun onUnreachable(state: State): State = if (state.enabled) State(enabled = false, suspended = true) else state

    /**
     * A discovery that reached the plugin; [usable] is false while a policy still refuses it. Only a
     * suspended plugin is switched back on.
     * zh-CN: 发现已连上插件; 仍有策略拒绝时 [usable] 为 false. 只有被挂起的插件会恢复启用.
     */
    fun onReachable(state: State, usable: Boolean): State =
        if (!state.enabled && state.suspended && usable) State(enabled = true, suspended = false) else state

    /** The user's switch or a policy decision: the stored flag follows it and no suspension remains. zh-CN: 用户开关或策略决定: 存储的标志随之改变, 不再保留挂起. */
    fun onDecision(enabled: Boolean): State = State(enabled = enabled, suspended = false)
}
