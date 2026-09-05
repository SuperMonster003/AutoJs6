package org.autojs.autojs.core.accessibility.node

/**
 * Whether nodes obtained from the framework have to be recycled. Below API 33 an
 * `AccessibilityNodeInfo` is pooled: it must be recycled exactly once when no longer used and not
 * touched afterwards. From API 33 (Tiramisu) on, `recycle()` is a no-op and nodes are ordinary
 * garbage-collected objects, so releasing them would only cost time.
 *
 * zh-CN: 从框架取得的节点是否必须回收. API 33 以下 `AccessibilityNodeInfo` 是池化对象: 不再使用时必须恰好回收一次, 之后不得再触碰.
 * API 33 (Tiramisu) 起 `recycle()` 为空操作, 节点是普通的可垃圾回收对象, 释放它们只会白费时间.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
enum class NodeRecyclePolicy {

    /** Nodes are pooled: recycle them once they are no longer used. zh-CN: 节点是池化的: 不再使用时回收. */
    RECYCLE,

    /** Recycling is a no-op on this platform: never call it. zh-CN: 本平台上回收是空操作: 不调用. */
    NO_OP;

    val recycles: Boolean get() = this == RECYCLE

    companion object {

        /** `Build.VERSION_CODES.TIRAMISU`, the first API level without node recycling. zh-CN: `Build.VERSION_CODES.TIRAMISU`, 首个无需回收节点的 API 级别. */
        const val FIRST_SDK_WITHOUT_RECYCLING = 33

        fun forSdk(sdkInt: Int): NodeRecyclePolicy = if (sdkInt < FIRST_SDK_WITHOUT_RECYCLING) RECYCLE else NO_OP

    }

}
