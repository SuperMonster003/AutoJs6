package org.autojs.autojs.core.plugin

/**
 * `bindService` returned false for a plugin service: the system refused the binding. Some systems (ColorOS) refuse to
 * bind a plugin whose process is not running, so [PluginAutoWake] wakes the plugin and binds again. The message keeps
 * the "bindService failed" wording that [org.autojs.autojs.core.plugin.center.PluginErrorMapper] maps to BIND_FAILED.
 * zh-CN: 插件服务的 `bindService` 返回 false, 即系统拒绝了绑定. 部分系统 (ColorOS) 拒绝绑定进程未在运行的插件, 因此
 * [PluginAutoWake] 会唤醒插件并重新绑定. 消息保留 "bindService failed" 的措辞, 由 PluginErrorMapper 映射为 BIND_FAILED.
 */
class PluginBindRefusedException(val packageName: String, message: String) : IllegalStateException(message)

/**
 * Whether this error, or one of its causes, is a refused plugin binding.
 * zh-CN: 该错误或其原因链中是否有被拒绝的插件绑定.
 */
fun Throwable.isPluginBindRefused(): Boolean = generateSequence(this) { it.cause }.take(8).any { it is PluginBindRefusedException }
