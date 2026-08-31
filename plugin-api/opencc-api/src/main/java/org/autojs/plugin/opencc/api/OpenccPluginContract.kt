package org.autojs.plugin.opencc.api

object OpenccPluginContract {
    const val VERSION_LEGACY = 1
    const val VERSION_EXTENDED_CONVERSIONS = 2
    const val VERSION_CURRENT = VERSION_EXTENDED_CONVERSIONS

    const val MAX_BATCH_SIZE = 1024
    const val MAX_CHAIN_LENGTH = 32
}

object OpenccPluginCapabilityKeys {
    const val CONTRACT_VERSION = "org.autojs.plugin.opencc.contractVersion"
    const val SUPPORTED_CONVERSION_TYPES = "org.autojs.plugin.opencc.supportedConversionTypes"
}
