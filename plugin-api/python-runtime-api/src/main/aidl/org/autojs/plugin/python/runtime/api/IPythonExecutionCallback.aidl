package org.autojs.plugin.python.runtime.api;

import android.os.ParcelFileDescriptor;

oneway interface IPythonExecutionCallback {

    void onStarted(in byte[] metadata);

    void onStdout(in byte[] chunk);

    void onStderr(in byte[] chunk);

    void onResult(in byte[] result, in ParcelFileDescriptor[] descriptors);

    void onFailed(in byte[] error);

    void onCancelled(in byte[] cancellation);

    void onInputPrompt(in byte[] prompt);
}
