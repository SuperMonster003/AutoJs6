package org.autojs.plugin.jvmsource.api;

// Synchronous identity barrier: the host validates the provider process PID for every callback.
interface IJvmSourceCallback {

    void onStarted(in byte[] metadata);

    void onDiagnostic(in byte[] diagnostic);

    void onCompleted(in byte[] result);

    void onFailed(in byte[] error);

    void onCancelled(in byte[] cancellation);
}
