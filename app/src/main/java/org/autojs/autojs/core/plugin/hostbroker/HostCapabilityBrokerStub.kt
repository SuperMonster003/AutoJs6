package org.autojs.autojs.core.plugin.hostbroker

import android.os.Binder
import android.os.Bundle
import org.autojs.plugin.host.capability.api.IHostCapabilityBroker
import org.autojs.plugin.host.capability.api.IHostCapabilityCallback

/** The sole shared capability Binder implementation; Agent links pin it to the selected UID. */
class HostCapabilityBrokerStub(
    internal val core: HostCapabilityBrokerCore,
    private val expectedCallerUid: Int? = null,
) : IHostCapabilityBroker.Stub() {

    override fun getBrokerInfo(): Bundle {
        enforceCaller()
        return core.getBrokerInfo()
    }

    override fun dispatch(request: Bundle?, callback: IHostCapabilityCallback?) {
        enforceCaller(request)
        core.dispatch(request, callback?.let { target -> { response -> target.onResponse(response) } })
    }

    override fun destroy(reason: Bundle?) {
        enforceCaller(reason)
        core.destroy(reason)
    }

    private fun enforceCaller(incoming: Bundle? = null) {
        if (expectedCallerUid != null && Binder.getCallingUid() != expectedCallerUid) {
            HostCapabilityBrokerCore.closeUnexpectedPayload(incoming)
            throw SecurityException("Host capability broker caller does not own this link")
        }
    }
}
