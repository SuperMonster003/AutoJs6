package org.autojs.plugin.three.stove.agent.api

/** Identity reported by PluginInfo; the host checks package and trusted signer before binding. */
object ThreeStoveAgentIds {
    const val PLUGIN_ID = "three-stove-agent"
    const val ENGINE = "three-stove-agent"
    const val VARIANT_DEFAULT = "default"
    const val DEFAULT_PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.three.stove.agent"
    /** Minimum host build shipping the 3-Stove Agent rename and contract version 2. */
    const val REQUIRED_HOST_VERSION_CODE = 5298L
}
