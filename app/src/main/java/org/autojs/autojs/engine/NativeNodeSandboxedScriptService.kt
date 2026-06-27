package org.autojs.autojs.engine

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.util.Log

class NativeNodeSandboxedScriptService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.w(
            TAG,
            "android.policy.sandboxed_node_service " +
                "status=design_gated pid=${Process.myPid()} action=${intent?.action.orEmpty()}",
        )
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        const val PROCESS_SUFFIX = ":nodejs_sandboxed"
        private const val TAG = "NativeNodeSandboxed"
    }
}
