package org.autojs.autojs.app.tool

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.VisibleForTesting
import io.reactivex.Observable
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.pref.keys.ConnectionKeys
import org.autojs.autojs.permission.LocalNetworkPermission
import org.autojs.autojs.pluginclient.JsonSocketServer
import org.autojs.autojs.util.Observers
import org.autojs.autojs.util.ViewUtils
import java.lang.ref.WeakReference

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

    private fun startServer() {
        subscribeServer(devPlugin.enableLocalServer())
        isNormallyClosed = false
    }

    /**
     * The accept loop keeps its observer until the server stops, so the observer holds this tool (and through it the
     * Activity that built it) only weakly; once the tool is gone, an error still stops the server and shows the
     * message (docs/memory/ROADMAP.md Q54).
     * zh-CN: accept 循环在服务端停止前一直持有观察者, 因此观察者只弱引用本工具 (以及经它持有的构造它的 Activity);
     * 工具被回收后, 出错时仍会停止服务端并提示消息 (docs/memory/ROADMAP.md Q54).
     */
    @SuppressLint("CheckResult")
    @VisibleForTesting
    internal fun subscribeServer(server: Observable<JsonSocketServer>) {
        val appContext = context.applicationContext ?: context
        val toolRef = WeakReference(this)
        server.subscribe(Observers.emptyConsumer()) {
            val tool = toolRef.get()
            if (tool != null) {
                tool.disconnect()
                ViewUtils.showToast(tool.context, it.message)
                tool.onConnectionException.accept(it)
            } else {
                AutoJs.instance.devPluginService.apply {
                    disconnectJsonSocketServer()
                    isServerSocketNormallyClosed = true
                }
                ViewUtils.showToast(appContext, it.message)
            }
        }
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
