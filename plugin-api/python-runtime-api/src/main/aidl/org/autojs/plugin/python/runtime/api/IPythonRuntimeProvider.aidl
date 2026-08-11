package org.autojs.plugin.python.runtime.api;

import android.os.ParcelFileDescriptor;
import org.autojs.plugin.python.runtime.api.IPythonExecutionCallback;
import org.autojs.plugin.python.runtime.api.IPythonExecutionSession;
import org.autojs.plugin.python.runtime.api.IPythonSessionOpenCallback;

interface IPythonRuntimeProvider {

    byte[] getRuntimeInfo();

    byte[] getCapabilities();

    void openSession(
        in byte[] request,
        in ParcelFileDescriptor[] descriptors,
        IPythonSessionOpenCallback admissionCallback,
        IPythonExecutionCallback executionCallback
    );
}
