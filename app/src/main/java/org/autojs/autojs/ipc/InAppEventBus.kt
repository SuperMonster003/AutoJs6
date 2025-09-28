package org.autojs.autojs.ipc

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object InAppEventBus {

    // Map<action, eventStream>
    private val streams = mutableMapOf<String, MutableSharedFlow<Unit>>()

    @JvmStatic
    @Synchronized
    fun streamOf(action: String): SharedFlow<Unit> {
        return streams.getOrPut(action) {
            MutableSharedFlow(
                replay = 1,
                extraBufferCapacity = 64,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        }
    }

    @JvmStatic
    @Synchronized
    fun tryEmit(action: String): Boolean {
        return streams[action]?.tryEmit(Unit) == true
    }

    @JvmStatic
    @Synchronized
    fun clear() {
        streams.clear()
    }

}