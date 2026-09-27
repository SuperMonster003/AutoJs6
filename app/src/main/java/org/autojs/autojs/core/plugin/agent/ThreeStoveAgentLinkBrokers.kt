package org.autojs.autojs.core.plugin.agent

import android.content.Context
import android.os.Bundle
import com.google.gson.JsonParser
import com.google.gson.JsonArray
import java.io.File
import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityBrokerCore
import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityBrokerStub
import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityGrant
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentModelBroker
import java.util.concurrent.atomic.AtomicBoolean

/** Owns the resources of one attached Agent link, including failure-before-attach cleanup. */
class ThreeStoveAgentLinkBrokers(
    context: Context,
    linkId: String,
    pluginUid: Int,
    grant: HostCapabilityGrant = ThreeStoveAgentGrant.default(),
) {
    private val destroyed = AtomicBoolean(false)
    private val capabilityCore = HostCapabilityBrokerCore(
        context, linkId, grant, sourceName = "three-stove-agent", engineName = "three-stove-agent", label = "Agent", grantSource = "agent-grant",
    )
    val capabilityBroker = HostCapabilityBrokerStub(capabilityCore, expectedCallerUid = pluginUid)
    private val modelCore = ThreeStoveAgentModelBroker(context, expectedCallerUid = pluginUid, grant = grant)
    val modelBroker: IThreeStoveAgentModelBroker get() = modelCore

    internal fun prepareConfig(configJson: String): () -> Unit {
        val config = JsonParser.parseString(configJson).asJsonObject
        val roots = config.get("scriptRoots") ?: JsonArray()
        require(roots.isJsonArray && roots.asJsonArray.size() <= 32) { "Invalid script roots." }
        val files = roots.asJsonArray.map { value ->
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Invalid script root." }
            File(value.asString).also { require(it.isAbsolute) { "Script roots must be absolute." } }
        }
        return capabilityCore.prepareAgentScriptRoots(files)
    }

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
