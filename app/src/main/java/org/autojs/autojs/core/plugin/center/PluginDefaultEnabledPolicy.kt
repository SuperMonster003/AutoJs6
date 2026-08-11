package org.autojs.autojs.core.plugin.center

import org.autojs.plugin.yolo.api.YoloContract

object PluginDefaultEnabledPolicy {

    fun isEnabledByDefault(engine: String?): Boolean =
        engine?.trim()?.equals(YoloContract.ENGINE_ID, ignoreCase = true) != true
}
