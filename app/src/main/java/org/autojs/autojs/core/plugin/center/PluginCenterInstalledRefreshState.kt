package org.autojs.autojs.core.plugin.center

/**
 * Keeps the last complete AIDL discovery as the baseline that a refresh renders before its own complete discovery
 * finishes.
 *
 * A refresh first reads a package-manager-only snapshot, then runs AIDL, legacy SDK and index discovery concurrently,
 * and every stage that finishes publishes the merged list. The snapshot never binds a plugin, so a package without
 * cached metadata (or with an older cached state) looks different there than in the complete discovery already on
 * screen: no icon or description, another enabled state, another sort position. Publishing it would show such a row
 * briefly and move it away again once the complete discovery arrives. The baseline therefore keeps the previous
 * complete entry of every package whose build did not change (same version code and update time) and takes the
 * snapshot only for added, updated or reinstalled packages; packages missing from the snapshot are gone.
 *
 * zh-CN:
 *
 * 保留上一次完整的 AIDL 发现结果, 作为刷新在自身完整发现完成前所渲染的基线.
 *
 * 刷新先读取仅依赖 PackageManager 的快照, 再并发执行 AIDL / 传统 SDK / 索引发现, 任一阶段完成都会发布合并后的列表.
 * 快照不绑定插件, 没有缓存元数据 (或缓存状态较旧) 的包在快照中与屏幕上已有的完整发现结果不同: 没有图标与描述,
 * 启用状态和排序位置也可能不同. 直接发布会让这一行短暂出现, 待完整发现到达后又移走. 因此基线对构建未变化
 * (版本号与更新时间相同) 的包沿用上一次的完整条目, 只对新增, 更新或重装的包采用快照; 快照中缺失的包视为已不存在.
 */
internal class PluginCenterInstalledRefreshState<T>(
    private val packageOf: (T) -> String,
    private val buildOf: (T) -> Any?,
) {

    private var lastComplete: Map<String, T>? = null

    /** The list a refresh renders until its complete discovery finishes. zh-CN: 刷新在完整发现完成前渲染的列表. */
    fun baseline(snapshot: List<T>): List<T> {
        val previous = lastComplete ?: return snapshot
        return snapshot.map { fresh ->
            previous[packageOf(fresh)]?.takeIf { buildOf(it) == buildOf(fresh) } ?: fresh
        }
    }

    /** Records a successful complete discovery, including an empty one. zh-CN: 记录一次成功的完整发现 (包括空结果). */
    fun complete(discovered: List<T>): List<T> = discovered.toList().also { list ->
        lastComplete = list.associateBy(packageOf)
    }
}
