package org.autojs.autojs.app.tool

import android.annotation.SuppressLint
import android.content.Context
import org.autojs.autojs.core.pref.keys.ConnectionKeys
import org.autojs.autojs.permission.LocalNetworkPermission
import org.autojs.autojs.util.Observers
import org.autojs.autojs.util.ViewUtils

class JsonSocketServerTool(context: Context) : AbstractJsonSocketTool(context) {

    override val isConnected
        get() = devPlugin.isServerSocketConnected

    override var isNormallyClosed
        get() = devPlugin.isServerSocketNormallyClosed
        set(state) {
            devPlugin.isServerSocketNormallyClosed = state
        }

    override fun connect() {
        LocalNetworkPermission(context).request { granted ->
            if (granted) startServer() else onConnectionDialogDismissed.onDismiss(null)
        }
    }

    @SuppressLint("CheckResult")
    private fun startServer() {
        devPlugin
            .enableLocalServer()
            .subscribe(Observers.emptyConsumer()) {
                disconnect()
                ViewUtils.showToast(context, it.message)
                onConnectionException.accept(it)
            }
        isNormallyClosed = false
    }

    override fun connectIfNotNormallyClosed() {
        if (!isNormallyClosed && ConnectionKeys.SERVER_MODE_RESTORE_ON_LAUNCH.get() && LocalNetworkPermission.isGranted(context)) startServer()
    }

    override fun disconnect() {
        devPlugin.disconnectJsonSocketServer()
        isNormallyClosed = true
    }

    override fun dispose() {
        stateDisposable?.dispose()
    }

}
