package org.autojs.autojs.core.plugin.center

/**
 * Decides whether Plugin Center needs discovery when its Fragment resumes.
 *
 * ViewPager tab changes move non-current pages between STARTED and RESUMED without
 * stopping them. Package broadcasts remain registered in that state, so another
 * discovery pass would be redundant and would compete with the page animation.
 * A real host departure reaches STOPPED and may miss broadcasts, so the next resume
 * performs one catch-up refresh.
 *
 * zh-CN: 决定插件中心 Fragment 恢复时是否需要重新发现插件.
 * ViewPager 标签切换只会在 STARTED 与 RESUMED 之间变化, 包广播仍保持注册,
 * 因此无需在页面动画期间重复发现. 真正离开宿主会进入 STOPPED 并可能错过广播,
 * 所以下次恢复时执行一次补偿刷新.
 */
internal class PluginCenterResumeRefreshPolicy {

    private var stoppedSinceLastResume = false

    fun onStopped() {
        stoppedSinceLastResume = true
    }

    fun consumeRefreshOnResume(nightModeChanged: Boolean): Boolean {
        val shouldRefresh = stoppedSinceLastResume || nightModeChanged
        stoppedSinceLastResume = false
        return shouldRefresh
    }
}
