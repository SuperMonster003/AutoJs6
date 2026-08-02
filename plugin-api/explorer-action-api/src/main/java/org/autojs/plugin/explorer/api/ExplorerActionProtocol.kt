package org.autojs.plugin.explorer.api

object ExplorerActionProtocol {
    const val VERSION = 2
    const val MIN_SUPPORTED_VERSION = 1
    const val MAX_SUPPORTED_VERSION = VERSION

    const val MAX_ACTIONS_PER_PLUGIN = 32
    const val MAX_ACTION_ID_LENGTH = 128
    const val MAX_LABEL_LENGTH = 160
    const val MAX_ACTIVITY_CLASS_NAME_LENGTH = 512
    const val MAX_MATCHERS_PER_KIND = 128
}
