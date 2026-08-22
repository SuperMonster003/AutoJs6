package org.autojs.plugin.jvmsource.api;

// Synchronous identity barrier: the worker validates that the compiler proxy returned the response.
interface IJvmHostBridgeCallback {

    void onResponse(in byte[] response);
}
