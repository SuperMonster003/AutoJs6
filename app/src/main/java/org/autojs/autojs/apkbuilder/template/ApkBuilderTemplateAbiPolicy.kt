package org.autojs.autojs.apkbuilder.template

import org.autojs.autojs.core.plugin.center.PluginAbiUtils

internal object ApkBuilderTemplateAbiPolicy {

    fun isCompatible(
        supportedAbis: Collection<String>?,
        deviceAbis: Collection<String>,
    ): Boolean {
        return !PluginAbiUtils.hasMismatchWithDevice(supportedAbis, deviceAbis)
    }
}
