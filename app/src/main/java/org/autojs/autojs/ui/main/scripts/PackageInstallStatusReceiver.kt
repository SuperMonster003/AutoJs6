package org.autojs.autojs.ui.main.scripts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** The explicit, mutable callback token is held by PackageInstaller, never by a notification. */
class PackageInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { PackageInstallStatusCoordinator.receive(context, intent) }
            .onFailure { Log.e("PackageInstallStatus", "Unable to record installation callback", it) }
    }
}
