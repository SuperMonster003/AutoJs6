package org.autojs.plugin.tool.fake.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.autojs.plugin.common.api.IPluginInfoProvider
import org.autojs.plugin.common.api.PluginInfo

/**
 * The common `org.autojs.plugin.INFO` service, so that the AutoJs6 plugin center lists this test-only provider like any
 * third-party plugin and the user can authorize it there (a provider without it never appears, so it stays unauthorized).
 */
class FakePluginInfoService : Service() {

    private val binder = object : IPluginInfoProvider.Stub() {
        override fun getInfo(): PluginInfo = FakePluginToolProviderService.pluginInfo()
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
