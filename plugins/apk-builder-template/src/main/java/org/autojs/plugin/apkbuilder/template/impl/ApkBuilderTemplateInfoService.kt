package org.autojs.plugin.apkbuilder.template.impl

import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.autojs.plugin.common.api.IPluginInfoProvider

class ApkBuilderTemplateInfoService : Service() {

    override fun onBind(intent: Intent?): IBinder = binder

    private val binder = object : IPluginInfoProvider.Stub() {
        override fun getInfo() = ApkBuilderTemplateMetadata.pluginInfo(this@ApkBuilderTemplateInfoService)
    }
}
