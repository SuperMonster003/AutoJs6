package org.autojs.plugin.three.stove.agent.api

import org.autojs.plugin.common.api.PluginCapabilityKeys

/** Keys in PluginInfo.capabilities and getCapabilities, readable before creating a link. */
object ThreeStoveAgentCapabilityKeys {
    /** Required Long: minimum host versionCode (host reads numbers type-agnostically). */
    const val REQUIRES_HOST_VERSION = PluginCapabilityKeys.REQUIRES_HOST_VERSION
    /** Required Int: implemented ThreeStoveAgentContract version. */
    const val CONTRACT_VERSION = "threeStoveAgentContractVersion"
    /** Required String array: supported tool groups, not a grant to enable every group. */
    const val TOOL_GROUPS = "threeStoveAgentToolGroups"
    /** Required String array: implemented features only; reserved features are never advertised. */
    const val FEATURES = "threeStoveAgentFeatures"

    const val FEATURE_STRUCTURED_JSON_LOOP = "structured-json-loop"
    /** Reserved for 1.1; no native tool calling in contract version 1. */
    const val FEATURE_NATIVE_TOOLS = "native-tools"
    /** Reserved for 1.1; no image inputs in contract version 1. */
    const val FEATURE_VISION = "vision"
    /** Reserved for 1.2; no MCP client adapter in contract version 1. */
    const val FEATURE_MCP_TOOLS = "mcp-tools"
}
