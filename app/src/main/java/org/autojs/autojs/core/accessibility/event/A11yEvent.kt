package org.autojs.autojs.core.accessibility.event

import android.os.Build
import android.view.accessibility.AccessibilityEvent
import org.autojs.autojs.core.accessibility.A11yEventRecord
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.automator.AccessibilityEventWrapper

/**
 * What travels on the accessibility event bus: the raw framework event plus the two views the
 * subscribers want, each built at most once per event and only when asked for. Framework delegates
 * take [raw] and [service], script callbacks take [wrapper], observers take [record].
 *
 * [raw] belongs to the framework and is recycled once `onAccessibilityEvent` returns on API < 33;
 * [seal] marks that moment, and [copied] produces an instance that is safe to keep (used for
 * debounced deliveries).
 *
 * zh-CN: 无障碍事件总线上传递的对象: 框架的原始事件加上订阅方需要的两种视图, 每个事件各视图至多构建一次且按需构建.
 * 框架内部的 delegate 使用 [raw] 与 [service], 脚本回调使用 [wrapper], 观察者使用 [record].
 * [raw] 属于框架, API < 33 上 `onAccessibilityEvent` 返回后即被回收; [seal] 标记这一时刻, [copied] 生成可安全保留的实例 (用于去抖投递).
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
class A11yEvent private constructor(
    val service: AccessibilityService,
    val raw: AccessibilityEvent,
    /** Whether [raw] is a copy owned by this object rather than the framework's pooled event. zh-CN: [raw] 是否为本对象持有的副本而非框架的池化事件. */
    val isCopy: Boolean,
) {

    constructor(service: AccessibilityService, raw: AccessibilityEvent) : this(service, raw, false)

    val type: Int = raw.eventType

    val record: A11yEventRecord by lazy { A11yEventRecord.of(raw) }

    private val wrapperDelegate = lazy { AccessibilityEventWrapper(raw) }

    val wrapper: AccessibilityEventWrapper by wrapperDelegate

    /**
     * To be called when the framework callback returns: the wrapper, if one was handed out, stops
     * resolving its source from the possibly recycled event (see [AccessibilityEventWrapper.seal]).
     * zh-CN: 框架回调返回时调用: 已交出的 wrapper 不再从可能已回收的事件解析 source (见 [AccessibilityEventWrapper.seal]).
     */
    fun seal() {
        if (!isCopy && wrapperDelegate.isInitialized()) wrapper.seal()
    }

    /** An event that may be kept past the framework callback, backed by a copy of [raw]. zh-CN: 可在框架回调之后保留的事件, 以 [raw] 的副本为底. */
    fun copied(): A11yEvent = if (isCopy) this else A11yEvent(service, copyOf(raw), true)

    override fun toString() = "A11yEvent(type=${A11yEventRecord.typeNameOf(type)}, package=${raw.packageName}, class=${raw.className}, copy=$isCopy)"

    companion object {

        // The copy constructor exists since API 30; obtain(event) is deprecated since API 33 but does the same before that.
        // zh-CN: 拷贝构造函数自 API 30 起可用; obtain(event) 自 API 33 起弃用, 此前作用相同.
        @Suppress("DEPRECATION")
        private fun copyOf(event: AccessibilityEvent): AccessibilityEvent = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> AccessibilityEvent(event)
            else -> AccessibilityEvent.obtain(event)
        }

    }

}
