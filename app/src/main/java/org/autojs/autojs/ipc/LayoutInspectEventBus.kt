package org.autojs.autojs.ipc

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

sealed interface LayoutInspectEvent {
    object ShowLayoutBounds : LayoutInspectEvent
    object ShowLayoutHierarchy : LayoutInspectEvent
}

object LayoutInspectEventBus {
    // One-time event: replay=0, provide buffering
    // and discard oldest on overflow to avoid emit suspension.
    // zh-CN: 单次事件: replay=0, 提供一定缓冲并在溢出时丢弃最旧, 避免 emit 挂起.
    private val _events = MutableSharedFlow<LayoutInspectEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<LayoutInspectEvent> get() = _events

    suspend fun showLayoutBounds() = _events.emit(LayoutInspectEvent.ShowLayoutBounds)
    suspend fun showLayoutHierarchy() = _events.emit(LayoutInspectEvent.ShowLayoutHierarchy)
}
