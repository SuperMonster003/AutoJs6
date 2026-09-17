package org.autojs.autojs.core.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs6.R
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/** A runtime's lease on script music. All connection and player state belongs to the main looper. */
class ScriptMusicPlayer(context: Context) : AutoCloseable {
    private val context = context.applicationContext
    private val closed = AtomicBoolean()
    private var connection: Connection? = null

    fun play(path: String, volume: Float, looping: Boolean) {
        require(Looper.myLooper() != Looper.getMainLooper()) {
            context.getString(R.string.error_function_called_in_ui_thread, "media.playMusic")
        }
        require(volume.isFinite() && volume in 0f..1f) { "media.playMusic volume must be in 0..1." }
        val request = Connection(path, volume, looping)
        main.post {
            if (closed.get() || request.ready.isDone) {
                request.dispose(IllegalStateException("The script music request was cancelled."))
            } else {
                connection?.dispose(IllegalStateException("A newer music request replaced this request."))
                connection = request
                try {
                    request.bound = context.bindService(
                        Intent(context, ScriptMusicService::class.java).setAction(ScriptMusicService.LOCAL_BIND),
                        request, Context.BIND_AUTO_CREATE,
                    )
                    check(request.bound) { "Cannot connect to the script music service." }
                } catch (error: Exception) {
                    request.dispose(error)
                }
            }
        }
        try {
            await(request.ready)
        } catch (error: Exception) {
            // Cancel before posting cleanup, so a late service connection cannot begin playback.
            request.ready.completeExceptionally(error)
            main.post { request.dispose(error) }
            throw error
        }
    }

    fun pause() = onMain { connection?.let { it.service?.pause(it) }; Unit }
    fun resume() = onMain { connection?.let { it.service?.resume(it) }; Unit }
    fun seekTo(position: Int) = onMain { connection?.let { it.service?.seekTo(it, position) }; Unit }
    fun isPlaying(): Boolean = onMain { connection?.let { it.service?.isPlaying(it) } ?: false }
    fun hasEnded(): Boolean = onMain { connection?.let { it.service?.hasEnded(it) } ?: false }
    fun duration(): Int = onMain { connection?.let { it.service?.duration(it) } ?: 0 }
    fun position(): Int = onMain { connection?.let { it.service?.position(it) } ?: -1 }
    fun stop() = onMain { connection?.dispose(); Unit }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            // Runtime shutdown may already have interrupted the caller. Cleanup must not wait on it.
            if (Looper.myLooper() == Looper.getMainLooper()) connection?.dispose()
            else main.post { connection?.dispose() }
        }
    }

    internal inner class Connection(val path: String, val volume: Float, val looping: Boolean) : ServiceConnection {
        val ready = CompletableFuture<Unit>()
        var bound = false
        var service: ScriptMusicService? = null

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (closed.get() || ready.isDone || connection !== this) {
                dispose()
                return
            }
            try {
                service = (binder as ScriptMusicService.LocalBinder).service
                service!!.play(this)
            } catch (error: Exception) {
                dispose(error)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) = dispose(IllegalStateException("The script music service disconnected."))
        override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
        override fun onNullBinding(name: ComponentName) = onServiceDisconnected(name)

        fun dispose(error: Exception = IllegalStateException("Script music stopped.")) {
            if (connection === this) connection = null
            ready.completeExceptionally(error)
            val previous = service
            service = null
            previous?.release(this)
            if (bound) {
                bound = false
                context.unbindService(this)
            }
        }
    }

    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val result = CompletableFuture<T>()
        main.post {
            if (!result.isDone) runCatching(block).fold(result::complete, result::completeExceptionally)
        }
        return try { await(result) } catch (error: Exception) {
            result.completeExceptionally(error)
            throw error
        }
    }

    private fun <T> await(result: CompletableFuture<T>): T = try {
        result.get(30, TimeUnit.SECONDS)
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()
        throw ScriptInterruptedException(error)
    } catch (error: ExecutionException) {
        throw (error.cause as? RuntimeException ?: IllegalStateException("Script music failed.", error.cause))
    } catch (error: TimeoutException) {
        throw IllegalStateException("Script music did not become ready within 30 seconds.", error)
    }

    private companion object {
        val main = Handler(Looper.getMainLooper())
    }
}
