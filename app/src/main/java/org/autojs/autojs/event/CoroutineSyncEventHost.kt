@file:Suppress("MemberVisibilityCanBePrivate", "ReplacePutWithAssignment", "unused")

package org.autojs.autojs.event

import androidx.annotation.Keep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by SuperMonster003 on May 23, 2025.
 */
// @Reference to com.stardust.event.CoroutineSyncEventHost from Auto.js Pro 9.3.11 by SuperMonster003 on May 23, 2025.
class CoroutineSyncEventHost(
    val scope: CoroutineScope,
    val consumer: (Event) -> Boolean,
) : IEventEmitter {

    var onError: ((Throwable) -> Unit)? = null
    val syncEventTable = ConcurrentHashMap<String, Boolean>()
    var alwaysSync: Boolean = false

    override fun emitEvent(event: String, vararg args: Any?): EventResult {
        require(event.isNotBlank()) { "event" }
        return emitInScope(event, scope, *args)
    }

    fun emitInScope(eventName: String, scope: CoroutineScope, vararg args: Any?): EventResult {
        val sync = alwaysSync || syncEventTable[eventName] ?: false
        val evt = Event(arguments = args, name = eventName, sync = sync, consumed = false, result = null)
        return when {
            sync -> {
                val handled = runCatching {
                    consumer(evt)
                }.onFailure {
                    onError?.invoke(it.apply { printStackTrace() })
                }.isSuccess
                when {
                    handled -> EventResult(evt.result, !evt.consumed)
                    else -> IGNORE_RESULT
                }
            }
            else -> {
                scope.launch(Dispatchers.Default) {
                    runCatching {
                        consumer(evt)
                    }.onFailure {
                        onError?.invoke(it.apply { printStackTrace() })
                    }
                }
                IGNORE_RESULT
            }
        }
    }


    companion object {

        val IGNORE_RESULT = EventResult(null, true)

        @Keep
        @Suppress("ArrayInDataClass")
        data class Event(val arguments: Array<out Any?>, val name: String, val sync: Boolean, var consumed: Boolean = false, var result: Any? = null)

    }

}