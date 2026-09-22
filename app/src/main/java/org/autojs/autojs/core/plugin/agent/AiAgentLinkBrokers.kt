package org.autojs.autojs.core.plugin.agent

import android.content.Context
import android.os.Bundle
import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityBrokerCore
import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityBrokerStub
import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityGrant
import org.autojs.plugin.ai.agent.api.IAiAgentModelBroker
import java.util.concurrent.atomic.AtomicBoolean

/** Owns the resources of one attached Agent link, including failure-before-attach cleanup. */
class AiAgentLinkBrokers(
    context: Context,
    linkId: String,
    pluginUid: Int,
    grant: HostCapabilityGrant = AiAgentGrant.default(),
) {
    private val destroyed = AtomicBoolean(false)
    private val capabilityCore = HostCapabilityBrokerCore(
        context, linkId, grant, sourceName = "ai-agent", engineName = "ai-agent", label = "Agent", grantSource = "agent-grant",
    )
    val capabilityBroker = HostCapabilityBrokerStub(capabilityCore, expectedCallerUid = pluginUid)
    private val modelCore = AiAgentModelBroker(context, expectedCallerUid = pluginUid, grant = grant)
    val modelBroker: IAiAgentModelBroker get() = modelCore

    fun destroy(reason: Bundle) {
        if (destroyed.compareAndSet(false, true)) {
            try {
                modelCore.close()
            } finally {
                // Host lifetime teardown bypasses the public Binder caller guard.
                capabilityCore.destroy(reason)
            }
        }
    }
}
