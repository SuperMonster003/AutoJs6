package org.autojs.plugin.lua.runtime.fake.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.autojs.plugin.common.api.IPluginInfoProvider
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.lua.runtime.fake.FakeLuaConformanceCounterContract
import org.autojs.plugin.lua.runtime.fake.FakeLuaConformanceState
import org.autojs.plugin.lua.runtime.fake.FakeLuaRuntimeFixture

abstract class BaseFakeLuaPluginInfoService : Service() {
    protected abstract val stall: Boolean

    private lateinit var callerVerifier: HostCallerVerifier

    override fun onCreate() {
        super.onCreate()
        callerVerifier = HostCallerVerifier(this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private val binder = object : IPluginInfoProvider.Stub() {
        override fun getInfo(): PluginInfo {
            callerVerifier.enforceAllowedCaller()
            val generation = FakeLuaConformanceState.capture()
            generation.record(FakeLuaConformanceCounterContract.PLUGIN_INFO_CALLS)
            return try {
                if (stall) generation.awaitPluginInfoRelease()
                FakeLuaRuntimeFixture.pluginInfo()
            } finally {
                generation.record(FakeLuaConformanceCounterContract.PLUGIN_INFO_EXITS)
            }
        }
    }
}

class FakeLuaPluginInfoService : BaseFakeLuaPluginInfoService() {
    override val stall = false
}

class StallLuaPluginInfoService : BaseFakeLuaPluginInfoService() {
    override val stall = true
}
