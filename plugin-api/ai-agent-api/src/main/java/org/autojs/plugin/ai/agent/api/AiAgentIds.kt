package org.autojs.plugin.ai.agent.api

/** Identity reported by PluginInfo; the host checks package and trusted signer before binding. */
object AiAgentIds {
    const val PLUGIN_ID = "ai-agent"
    const val ENGINE = "ai-agent"
    const val VARIANT_DEFAULT = "default"
    const val DEFAULT_PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.ai.agent"
    /** Minimum host build shipping the P1 foundation and P3.1 script-directory settings. */
    const val REQUIRED_HOST_VERSION_CODE = 5286L
}
