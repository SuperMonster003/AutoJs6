package org.autojs.plugin.yolo.fake.service

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import org.autojs.plugin.yolo.api.IYoloCallback
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Relays one terminal callback from an Android isolated UID owned only by this test APK. */
class IsolatedWrongUidYoloCallbackService : Service() {
    override fun onBind(intent: Intent?): IBinder {
        val payload = requireNotNull(intent?.getBundleExtra(EXTRA_PAYLOAD)) {
            "Fake YOLO wrong-UID relay payload is missing"
        }
        val callback = requireNotNull(
            IYoloCallback.Stub.asInterface(payload.getBinder(EXTRA_CALLBACK_BINDER)),
        ) { "Fake YOLO wrong-UID callback Binder is missing" }
        val terminal = requireNotNull(payload.getByteArray(EXTRA_TERMINAL)) {
            "Fake YOLO wrong-UID terminal is missing"
        }
        callback.onDetected(terminal.copyOf())
        return acknowledgement
    }

    private val acknowledgement = Binder()

    internal companion object {
        const val EXTRA_PAYLOAD = "org.autojs.plugin.yolo.fake.WRONG_UID_PAYLOAD"
        const val EXTRA_CALLBACK_BINDER = "callback_binder"
        const val EXTRA_TERMINAL = "terminal"
    }
}

/** Exact private binding whose connection callback acknowledges relay dispatch. */
internal class IsolatedWrongUidYoloCallbackBinding(
    context: Context,
    callback: IYoloCallback,
    terminal: ByteArray,
) : Closeable {
    private val applicationContext = context.applicationContext
    private val acknowledged = CountDownLatch(1)
    private val connected = AtomicBoolean(false)
    private val registered = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val expectedComponent = ComponentName(
        applicationContext,
        IsolatedWrongUidYoloCallbackService::class.java,
    )
    private val intent = Intent().setComponent(expectedComponent).putExtra(
        IsolatedWrongUidYoloCallbackService.EXTRA_PAYLOAD,
        Bundle().apply {
            putBinder(
                IsolatedWrongUidYoloCallbackService.EXTRA_CALLBACK_BINDER,
                callback.asBinder(),
            )
            putByteArray(IsolatedWrongUidYoloCallbackService.EXTRA_TERMINAL, terminal.copyOf())
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
        if (!applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            return false
        }
        registered.set(true)
        return acknowledged.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS) && connected.get()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        acknowledged.countDown()
        if (registered.get()) {
            runCatching { applicationContext.unbindService(connection) }
        }
    }

    private companion object {
        const val BIND_TIMEOUT_SECONDS = 10L
    }
}
