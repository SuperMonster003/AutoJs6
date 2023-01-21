package org.autojs.autojs.app.tool

import android.annotation.SuppressLint
import android.content.Context
import org.autojs.autojs.util.Observers

class JsonSocketServerTool(context: Context) : AbstractJsonSocketTool(context) {

    override val isConnected
        get() = devPlugin.isServerSocketConnected

    @SuppressLint("CheckResult")
    override fun connect() {
        devPlugin
            .enableLocalServer()
            .subscribe(Observers.emptyConsumer(), onConnectionException)
    }

    override fun disconnect() = devPlugin.disconnectJsonSocketServer()

    override fun dispose() {
        stateDisposable?.dispose()
    }

}