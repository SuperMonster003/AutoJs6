package org.autojs.plugin.three.stove.agent.api

import org.autojs.plugin.common.api.PluginActions

/** Frozen discovery and attach intent names. Exported entries require PLUGIN_PERMISSION. */
object ThreeStoveAgentActions {
    const val INFO = PluginActions.INFO
    const val SERVICE_ACTION = "org.autojs.plugin.THREE_STOVE_AGENT"
    const val SERVICE_CATEGORY = "three-stove-agent"
    const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"
    const val ACTION_ATTACH_REQUEST = "org.autojs.autojs6.action.THREE_STOVE_AGENT_ATTACH"
    /** Boolean extra on the host's existing launcher intent; requests the same attach flow. */
    const val EXTRA_THREE_STOVE_AGENT_ATTACH = "org.autojs.autojs6.extra.THREE_STOVE_AGENT_ATTACH"
    /**
     * Required immutable PendingIntent proof in attach broadcasts. The host verifies its
     * creatorPackage/creatorUid against the trusted plugin identity and never sends this token.
     * This authenticates the sender on Android versions without reliable broadcast sender UIDs.
     */
    const val EXTRA_ATTACH_IDENTITY = "org.autojs.autojs6.extra.THREE_STOVE_AGENT_ATTACH_IDENTITY"
}
