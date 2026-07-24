package org.autojs.autojs.runtime.api.tts

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal fun interface TtsScheduledTask {
    fun cancel()
}

/**
 * Serial command lane for TextToSpeech.
 *
 * [post] must never run inline. That guarantee also makes a synchronous OnInit failure safe:
 * the callback is handled only after the backend constructor has returned and the service has
 * stored the newly created backend.
 */
internal interface TtsCommandScheduler {

    fun post(action: () -> Unit)

    fun postDelayed(delayMillis: Long, action: () -> Unit): TtsScheduledTask

    fun shutdown() = Unit
}

internal class AndroidTtsCommandScheduler private constructor(
    looper: Looper?,
) : TtsCommandScheduler {

    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private var ownedThread: HandlerThread? = null
    private var handler: Handler? = looper?.let(::Handler)

    constructor() : this(null)

    override fun post(action: () -> Unit) {
        val runnable = Runnable(action)
        check(handler().post(runnable)) { "Unable to schedule a TTS command" }
    }

    override fun postDelayed(delayMillis: Long, action: () -> Unit): TtsScheduledTask {
        val runnable = Runnable(action)
        val target = handler()
        check(target.postDelayed(runnable, delayMillis)) {
            "Unable to schedule a delayed TTS command"
        }
        return TtsScheduledTask {
            target.removeCallbacks(runnable)
        }
    }

    override fun shutdown() {
        if (!closed.compareAndSet(false, true)) return
        val thread = synchronized(lock) {
            handler?.removeCallbacksAndMessages(null)
            handler = null
            ownedThread.also { ownedThread = null }
        }
        thread?.quitSafely()
    }

    private fun handler(): Handler {
        check(!closed.get()) { "TTS command scheduler is closed" }
        return synchronized(lock) {
            check(!closed.get()) { "TTS command scheduler is closed" }
            handler ?: HandlerThread(
                "AutoJs6-TTS-${nextThreadId.incrementAndGet()}",
            ).also { thread ->
                thread.start()
                ownedThread = thread
            }.looper.let(::Handler).also {
                handler = it
            }
        }
    }

    private companion object {

        val nextThreadId = AtomicInteger()
    }
}
