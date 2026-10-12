package org.autojs.autojs.core.plugin.center

/**
 * Decides whether Plugin Center needs discovery when its Fragment resumes.
 *
 * ViewPager tab changes move non-current pages between STARTED and RESUMED without
 * stopping them. Package broadcasts remain registered in that state, so another
 * discovery pass would be redundant and would compete with the page animation.
 * A real host departure reaches STOPPED and may miss broadcasts, so the next resume
 * performs one catch-up refresh. Explicit plugin settings also request one refresh:
 * a quick return or multi-window navigation may resume without ever reaching STOPPED.
 *
 * zh-CN: 决定插件中心 Fragment 恢复时是否需要重新发现插件.
 * ViewPager 标签切换只会在 STARTED 与 RESUMED 之间变化, 包广播仍保持注册,
 * 因此无需在页面动画期间重复发现. 真正离开宿主会进入 STOPPED 并可能错过广播,
 * 所以下次恢复时执行一次补偿刷新. 主动打开插件设置也会登记一次返回刷新,
 * 以覆盖快速返回或多窗口切换未进入 STOPPED 的情况.
 */
internal class PluginCenterResumeRefreshPolicy {

    private var stoppedSinceLastResume = false
    private var pluginSettingsOpened = false

    fun onStopped() {
        stoppedSinceLastResume = true
    }

    fun onPluginSettingsOpened() {
        pluginSettingsOpened = true
    }

    fun consumeRefreshOnResume(nightModeChanged: Boolean): Boolean {
        val shouldRefresh = stoppedSinceLastResume || pluginSettingsOpened || nightModeChanged
        stoppedSinceLastResume = false
        pluginSettingsOpened = false
        return shouldRefresh
    }
}
