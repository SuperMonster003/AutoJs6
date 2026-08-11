package org.autojs.plugin.ai.text.fake.service

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import org.autojs.plugin.ai.text.api.IAiTextCallback
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Invokes one host callback from an Android isolated UID held only by this conformance APK. */
class IsolatedWrongUidAiCallbackService : Service() {
    override fun onBind(intent: Intent?): IBinder {
        val payload = requireNotNull(intent?.getBundleExtra(EXTRA_PAYLOAD)) {
            "Fake isolated callback payload is missing"
        }
        val callbackBinder = requireNotNull(payload.getBinder(EXTRA_CALLBACK_BINDER)) {
            "Fake isolated callback Binder is missing"
        }
        val startedEnvelope = requireNotNull(payload.getByteArray(EXTRA_STARTED_ENVELOPE)) {
            "Fake isolated Started envelope is missing"
        }
        val callback = requireNotNull(IAiTextCallback.Stub.asInterface(callbackBinder)) {
            "Fake isolated callback interface is missing"
        }
        callback.onStarted(startedEnvelope.copyOf())
        return acknowledgement
    }

    private val acknowledgement = Binder()

    internal companion object {
        const val EXTRA_PAYLOAD = "org.autojs.plugin.ai.text.fake.WRONG_UID_PAYLOAD"
        const val EXTRA_CALLBACK_BINDER = "callback_binder"
        const val EXTRA_STARTED_ENVELOPE = "started_envelope"
    }
}

/** Exact explicit binding whose connection callback acknowledges relay dispatch without sleeps. */
internal class IsolatedWrongUidAiCallbackBinding(
    context: Context,
    callback: IAiTextCallback,
    startedEnvelope: ByteArray,
) : Closeable {
    private val applicationContext = context.applicationContext
    private val acknowledged = CountDownLatch(1)
    private val connected = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val registered = AtomicBoolean(false)
    private val unbound = AtomicBoolean(false)
    private val expectedComponent = ComponentName(
        applicationContext,
        IsolatedWrongUidAiCallbackService::class.java,
    )
    private val intent = Intent().setComponent(
        expectedComponent,
    ).putExtra(
        IsolatedWrongUidAiCallbackService.EXTRA_PAYLOAD,
        Bundle().apply {
            putBinder(IsolatedWrongUidAiCallbackService.EXTRA_CALLBACK_BINDER, callback.asBinder())
            putByteArray(
                IsolatedWrongUidAiCallbackService.EXTRA_STARTED_ENVELOPE,
                startedEnvelope.copyOf(),
            )
        },
    )
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            connected.set(name == expectedComponent)
            acknowledged.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            acknowledged.countDown()
        }

        override fun onBindingDied(name: ComponentName) {
            acknowledged.countDown()
        }

        override fun onNullBinding(name: ComponentName) {
            acknowledged.countDown()
        }
    }

    fun bindAndAwaitAcknowledgement(): Boolean {
        if (closed.get()) return false
        val didBind = applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        if (!didBind) return false
        registered.set(true)
        unbindIfReady()
        if (!acknowledged.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) return false
        return connected.get() && !closed.get()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        acknowledged.countDown()
        unbindIfReady()
    }

    private fun unbindIfReady() {
        if (closed.get() && registered.get() && unbound.compareAndSet(false, true)) {
            runCatching { applicationContext.unbindService(connection) }
        }
    }

    private companion object {
        const val BIND_TIMEOUT_SECONDS = 10L
    }
}
