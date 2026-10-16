package org.autojs.autojs.core.plugin.center

/**
 * Why Plugin Center temporarily lists a plugin first. zh-CN: 插件中心临时将插件排在最前的原因.
 */
enum class PluginCenterAttention {
    NEW_INSTALL,
    ENABLE_FAILED,
}

/**
 * Bookkeeping behind the temporary attention pins of Plugin Center (maintainer decisions of 2026-10-16): a fresh
 * installation pins the plugin until the user leaves Plugin Center once after seeing it, and an automatic enable that
 * failed is shown on the same pin.
 *
 * Fresh installations are recognized by the first install time of each plugin package, so updates never count and a
 * reinstall does; packages installed while Plugin Center was closed are found by the next comparison. The first
 * comparison of a source only records what is installed. A pin seen in this process is consumed when the session ends;
 * a pin seen in an earlier process (the host was killed meanwhile) is dropped when loaded.
 *
 * zh-CN:
 *
 * 插件中心临时置顶的记录 (维护者 2026-10-16 的决定): 全新安装的插件被置顶, 直到用户看到后离开插件中心一次;
 * 自动启用失败也显示在同一置顶条目上.
 *
 * 以各插件包的首次安装时间识别全新安装, 因此更新不算, 重装算; 插件中心关闭期间装入的包由下一次比对发现.
 * 每个来源的首次比对只记录已安装的包. 本进程中已展示的置顶在会话结束时消耗; 在更早的进程中已展示的置顶
 * (期间宿主进程已结束) 在载入时丢弃.
 */
internal class PluginCenterAttentionLedger(
    private val processToken: String,
    known: Map<String, Long> = emptyMap(),
    initializedSources: Set<String> = emptySet(),
    pins: Collection<Pin> = emptyList(),
) {

    data class Pin(
        val packageName: String,
        val createdAt: Long,
        val enableFailed: Boolean = false,
        // The process token of the session that showed the pin, null while unseen. zh-CN: 展示过该置顶的会话所在进程的标识, 未展示时为 null.
        val seenIn: String? = null,
    )

    private val known = known.toMutableMap()
    private val initializedSources = initializedSources.toMutableSet()
    private val pins = linkedMapOf<String, Pin>()

    init {
        pins.filter { it.seenIn == null || it.seenIn == processToken }
            .sortedBy { it.createdAt }
            .forEach { this.pins[it.packageName] = it }
    }

    fun knownFirstInstallTimes(): Map<String, Long> = known.toMap()

    fun initializedSources(): Set<String> = initializedSources.toSet()

    fun pins(): Map<String, Pin> = pins.toMap()

    /**
     * Compares the installed packages of one discovery source with what was seen before and pins the fresh
     * installations. [observed] maps package names to their first install time (null when unknown).
     * zh-CN: 将某一发现来源的已安装包与此前所见比对, 并置顶全新安装的包. [observed] 为包名到首次安装时间 (未知为 null) 的映射.
     *
     * @return the packages pinned by this comparison. zh-CN: 本次比对新置顶的包.
     */
    fun reconcile(source: String, observed: Map<String, Long?>, now: Long): List<String> {
        if (initializedSources.add(source)) {
            observed.forEach { (name, time) -> known[name] = time ?: known[name] ?: UNKNOWN_TIME }
            return emptyList()
        }
        val fresh = observed.filter { (name, time) ->
            val previous = known[name]
            previous == null || (time != null && previous != UNKNOWN_TIME && previous != time)
        }.keys.toList()
        observed.forEach { (name, time) -> known[name] = time ?: known[name] ?: UNKNOWN_TIME }
        fresh.forEach { pin(it, now) }
        return fresh
    }

    /**
     * A fresh installation reported by the package broadcast while Plugin Center is open.
     * zh-CN: 插件中心打开期间由包广播报告的全新安装.
     */
    fun recordFreshInstall(packageName: String, firstInstallTime: Long?, now: Long) {
        known[packageName] = firstInstallTime ?: UNKNOWN_TIME
        pin(packageName, now)
    }

    /** Returns true when a pin changed. zh-CN: 置顶有变化时返回 true. */
    fun markEnableFailed(packageName: String): Boolean {
        val pin = pins[packageName]?.takeUnless { it.enableFailed } ?: return false
        pins[packageName] = pin.copy(enableFailed = true)
        return true
    }

    /** Returns true when a pin changed. zh-CN: 置顶有变化时返回 true. */
    fun markSeen(packageNames: Collection<String>): Boolean {
        var changed = false
        packageNames.forEach { name ->
            val pin = pins[name] ?: return@forEach
            if (pin.seenIn != processToken) {
                pins[name] = pin.copy(seenIn = processToken)
                changed = true
            }
        }
        return changed
    }

    /** Consumes the pins this process has shown. Returns true when a pin was removed. zh-CN: 消耗本进程已展示的置顶. */
    fun endSession(): Boolean = pins.values.removeAll { it.seenIn == processToken }

    /** Returns true when a pin was removed. zh-CN: 有置顶被移除时返回 true. */
    fun forget(packageName: String): Boolean = pins.remove(packageName) != null

    private fun pin(packageName: String, now: Long) {
        pins.remove(packageName)
        pins[packageName] = Pin(packageName, now)
        while (pins.size > MAX_PINS) {
            pins.remove(pins.keys.first())
        }
    }

    companion object {
        const val SOURCE_AIDL = "aidl"
        const val SOURCE_LEGACY = "legacy"

        // Recorded when the package manager reported no first install time. zh-CN: PackageManager 未报告首次安装时间时记录的值.
        const val UNKNOWN_TIME = 0L

        const val MAX_PINS = 32
    }
}
