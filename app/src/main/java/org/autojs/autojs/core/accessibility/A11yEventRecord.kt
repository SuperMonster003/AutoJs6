package org.autojs.autojs.core.accessibility

import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Plain copy of the fields of an [AccessibilityEvent] that are worth showing in a live monitor.
 * Event objects themselves are pooled by the framework (and recycled after the callback on API < 33),
 * so only field values leave [AccessibilityService.onAccessibilityEvent].
 *
 * zh-CN: [AccessibilityEvent] 中值得在实时监视器中展示的字段的普通拷贝. 事件对象由框架池化
 * (API < 33 时回调结束后会被回收), 因此只有字段值会离开 [AccessibilityService.onAccessibilityEvent].
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
data class A11yEventRecord(
    val type: Int,
    val eventTime: Long,
    val receivedAtElapsedRealtime: Long,
    val packageName: String?,
    val className: String?,
    val windowId: Int,
    val contentChangeTypes: Int,
) {

    /** Event type name without the `TYPE_` prefix, e.g. `WINDOW_STATE_CHANGED`. zh-CN: 去掉 `TYPE_` 前缀的事件类型名. */
    val typeName: String get() = typeNameOf(type)

    companion object {

        fun of(event: AccessibilityEvent): A11yEventRecord = A11yEventRecord(
            type = event.eventType,
            eventTime = event.eventTime,
            receivedAtElapsedRealtime = SystemClock.elapsedRealtime(),
            packageName = event.packageName?.toString(),
            className = event.className?.toString(),
            windowId = event.windowId,
            contentChangeTypes = event.contentChangeTypes,
        )

        fun typeNameOf(type: Int): String = AccessibilityEvent.eventTypeToString(type).removePrefix("TYPE_")

    }

}
