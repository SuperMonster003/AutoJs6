package org.autojs.autojs.core.plugin.center

/**
 * Keeps the last successful legacy SDK discovery as the baseline for the next refresh.
 *
 * A resume refresh runs AIDL, legacy SDK, and index discovery concurrently. Replacing the
 * legacy result with an empty list until its slower discovery finishes would transiently
 * remove installed SDK plugins, reset their rendered switch state, and disturb RecyclerView's
 * scroll anchor. The previous complete result remains authoritative until a new successful
 * discovery, including a successful empty result, replaces it.
 *
 * zh-CN:
 *
 * 保留上一次成功发现的传统 SDK 插件列表, 作为下一次刷新的基线.
 *
 * 恢复刷新会并发执行 AIDL/传统 SDK/索引发现. 若在较慢的传统 SDK 发现完成前将其结果
 * 暂时替换为空列表, 已安装的 SDK 插件会被短暂移除, 开关渲染状态及 RecyclerView
 * 滚动锚点也会受到干扰. 因此在新的成功发现结果到达前继续使用上一轮完整结果;
 * 成功返回的空列表同样会替换旧结果.
 */
internal class PluginCenterLegacyRefreshState<T> {

    private var lastSuccessfulSnapshot: List<T> = emptyList()

    fun beginRefresh(): List<T> = lastSuccessfulSnapshot

    fun completeRefresh(discovered: List<T>): List<T> = discovered.toList().also {
        lastSuccessfulSnapshot = it
    }
}
