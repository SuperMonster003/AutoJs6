package org.autojs.autojs.ipc

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object InAppEventBus {

    // Map<action, eventStream>
    private val streams = mutableMapOf<String, MutableSharedFlow<Unit>>()
    private val payloadStreams = mutableMapOf<String, MutableSharedFlow<Any?>>()

    @JvmStatic
    @Synchronized
    fun streamPayloadsOf(action: String): SharedFlow<Any?> = payloadStreams.getOrPut(action) {
        MutableSharedFlow(replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }

    @JvmStatic
    @Synchronized
    fun streamOf(action: String): SharedFlow<Unit> {
        return streams.getOrPut(action) {
            MutableSharedFlow(
                replay = 0,
                extraBufferCapacity = 64,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        }
    }

    @JvmStatic
    @Synchronized
    fun tryEmit(action: String): Boolean {
        return tryEmitPayload(action, null)
    }

    @JvmStatic
    @Synchronized
    fun tryEmitPayload(action: String, payload: Any?): Boolean {
        val emitted = streams[action]?.tryEmit(Unit) == true
        return (payloadStreams[action]?.tryEmit(payload) == true) || emitted
    }

    @JvmStatic
    @Synchronized
    fun clear() {
        streams.clear()
        payloadStreams.clear()
    }

}
