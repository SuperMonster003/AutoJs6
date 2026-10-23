package org.autojs.autojs.core.plugin.python

import android.os.ParcelFileDescriptor
import org.autojs.plugin.python.runtime.api.IPythonExecutionCallback
import org.autojs.plugin.python.runtime.api.IPythonHostCapabilityBroker
import org.autojs.plugin.python.runtime.api.IPythonRuntimeProvider
import org.autojs.plugin.python.runtime.api.IPythonSessionOpenCallback
import org.autojs.plugin.python.runtime.api.PythonExecutionRequest
import org.autojs.plugin.python.runtime.api.PythonProtocolVersion
import org.autojs.plugin.python.runtime.api.PythonRuntimeCodec

/**
 * Fails closed on every host capability call. A provider admits a protocol 1.5 or newer session only together with
 * exactly one broker; the real-plugin diagnostics run scripts that never call a host capability, so they pass this one.
 */
internal class InertPythonHostCapabilityBroker : IPythonHostCapabilityBroker.Stub() {

    override fun dispatch(request: ByteArray?): ByteArray =
        throw UnsupportedOperationException("Python Runtime diagnostics do not serve host capabilities")
}

/** Opens [request] through the entry point its negotiated protocol requires, as `PythonRuntimeClient` does. */
internal fun IPythonRuntimeProvider.openSessionForNegotiatedProtocol(
    request: PythonExecutionRequest,
    descriptors: Array<ParcelFileDescriptor>,
    admissionCallback: IPythonSessionOpenCallback,
    executionCallback: IPythonExecutionCallback,
) {
    val encodedRequest = PythonRuntimeCodec.encodeExecutionRequest(request)
    if (request.protocolVersion >= HOST_CAPABILITY_BROKER_PROTOCOL) {
        openSessionWithHostCapabilities(
            encodedRequest,
            descriptors,
            InertPythonHostCapabilityBroker(),
            admissionCallback,
            executionCallback,
        )
    } else {
        openSession(encodedRequest, descriptors, admissionCallback, executionCallback)
    }
}

private val HOST_CAPABILITY_BROKER_PROTOCOL = PythonProtocolVersion(1, 5)
