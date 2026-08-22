package org.autojs.plugin.jvmsource.api;

import android.os.ParcelFileDescriptor;
import org.autojs.plugin.jvmsource.api.IJvmHostBridge;
import org.autojs.plugin.jvmsource.api.IJvmSourceCallback;
import org.autojs.plugin.jvmsource.api.IJvmSourceSession;

interface IJvmSourceProvider {

    byte[] getProviderInfo();

    byte[] getCapabilities();

    IJvmSourceSession openSession(
        in byte[] request,
        in ParcelFileDescriptor sourceFd,
        in ParcelFileDescriptor stdoutFd,
        in ParcelFileDescriptor stderrFd,
        IJvmHostBridge hostBridge,
        IJvmSourceCallback callback
    );
}
