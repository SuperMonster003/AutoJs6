package org.autojs.autojs.app.tool

import android.annotation.SuppressLint
import android.content.Context
import org.autojs.autojs.util.Observers
import org.autojs.autojs.util.ViewUtils

class JsonSocketServerTool(context: Context) : AbstractJsonSocketTool(context) {

    override val isConnected
        get() = devPlugin.isServerSocketConnected

    private var isNormallyClosed
        get() = devPlugin.isServerSocketNormallyClosed
        set(state) {
            devPlugin.isServerSocketNormallyClosed = state
        }

    @SuppressLint("CheckResult")
    override fun connect() {
        devPlugin
            .enableLocalServer()
            .subscribe(Observers.emptyConsumer()) {
                disconnect()
                ViewUtils.showToast(context, it.message)
                onConnectionException.accept(it)
            }
        isNormallyClosed = false
    }

    internal fun connectIfNotNormallyClosed() {
        if (!isNormallyClosed) connect()
    }

    override fun disconnect() {
        devPlugin.disconnectJsonSocketServer()
        isNormallyClosed = true
    }

    override fun dispose() {
        stateDisposable?.dispose()
    }

}