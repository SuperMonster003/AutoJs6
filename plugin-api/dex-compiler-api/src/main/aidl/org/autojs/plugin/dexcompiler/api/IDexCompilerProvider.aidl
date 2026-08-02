package org.autojs.plugin.dexcompiler.api;

import android.os.ParcelFileDescriptor;
import org.autojs.plugin.dexcompiler.api.IDexCompilerCallback;
import org.autojs.plugin.dexcompiler.api.IDexCompilerSession;

interface IDexCompilerProvider {

    byte[] getCompilerInfo();

    byte[] getCapabilities();

    IDexCompilerSession openSession(
        in byte[] request,
        in ParcelFileDescriptor programFd,
        in ParcelFileDescriptor outputFd,
        IDexCompilerCallback callback
    );
}
