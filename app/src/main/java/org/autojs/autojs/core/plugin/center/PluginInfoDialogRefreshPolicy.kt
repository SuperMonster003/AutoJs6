package org.autojs.autojs.core.plugin.center

/**
 * Decides how an open plugin information dialog follows a new plugin list.
 *
 * The plugin center publishes several lists after a cold start (cached snapshot, complete
 * discovery, index refresh, forced icon renders). Replacing the dialog window for each of
 * them made the dialog flash, so only a change of its action buttons rebuilds it; any other
 * visible change updates the rows in place, and an unchanged item leaves the dialog alone.
 *
 * zh-CN: 决定已打开的插件信息对话框如何跟随新的插件列表.
 * 冷启动后插件中心会连续发布多份列表 (缓存快照, 完整发现, 索引刷新, 强制图标渲染).
 * 每次都替换对话框窗口会造成闪烁, 因此只有操作按钮变化才重建; 其它可见变化原地更新行,
 * 条目未变化时对话框保持不动.
 */
object PluginInfoDialogRefreshPolicy {

    enum class Refresh { NONE, IN_PLACE, REBUILD }

    /** The neutral action of the dialog, the only part that cannot be updated in place. zh-CN: 对话框的中性按钮, 唯一不能原地更新的部分. */
    enum class NeutralAction { INSTALL, VIEW_UPDATE, VIEW_MATCHED_VERSION, NONE }

    fun neutralActionOf(item: PluginCenterItem): NeutralAction = when {
        !item.isInstalled -> NeutralAction.INSTALL
        !item.isUpdatable -> NeutralAction.NONE
        item.requiresDowngradeReinstall -> NeutralAction.VIEW_MATCHED_VERSION
        else -> NeutralAction.VIEW_UPDATE
    }

    fun decide(shown: PluginCenterItem, next: PluginCenterItem): Refresh = when {
        PluginCenterItemSemantics.areContentsTheSame(shown, next) -> Refresh.NONE
        neutralActionOf(shown) != neutralActionOf(next) -> Refresh.REBUILD
        else -> Refresh.IN_PLACE
    }

    /** Whether the dialog icon must be bound again rather than only re-tinted. zh-CN: 对话框图标是否需要重新绑定, 而不只是重新着色. */
    fun iconChanged(shown: PluginCenterItem, next: PluginCenterItem): Boolean =
        shown.iconSignature != next.iconSignature ||
            shown.remoteIconUrl != next.remoteIconUrl ||
            shown.remoteNightIconUrl != next.remoteNightIconUrl ||
            shown.forceIgnoreLocalIcon != next.forceIgnoreLocalIcon ||
            shown.iconBackgroundColor != next.iconBackgroundColor ||
            shown.nightIconBackgroundColor != next.nightIconBackgroundColor ||
            (shown.icon == null) != (next.icon == null)
}
