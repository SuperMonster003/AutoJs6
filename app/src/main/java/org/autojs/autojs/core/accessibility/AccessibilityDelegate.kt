package org.autojs.autojs.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Created by Stardust on Feb 14, 2017.
 */
interface AccessibilityDelegate {

    /**
     * 返回调用 onAccessibilityEvent 时的 EventType 的集合. 如果需要对所有 EventType 都有效, 返回 null.
     */
    val eventTypes: Set<Int>?

    fun onAccessibilityEvent(service: AccessibilityService, event: AccessibilityEvent): Boolean

    companion object {

        val ALL_EVENT_TYPES: Set<Int>? = null

    }

}
