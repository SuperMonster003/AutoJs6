package org.autojs.plugin.jvmsource.api;

import org.autojs.plugin.jvmsource.api.IJvmHostBridgeCallback;

// Synchronous identity barrier: each hop validates the Binder caller PID before forwarding.
interface IJvmHostBridge {

    void dispatch(in byte[] request, IJvmHostBridgeCallback callback);

    void destroy(in byte[] reason);
}
