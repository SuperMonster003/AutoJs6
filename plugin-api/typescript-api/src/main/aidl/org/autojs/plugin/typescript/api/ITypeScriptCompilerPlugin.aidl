package org.autojs.plugin.typescript.api;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import org.autojs.plugin.typescript.api.ITypeScriptCompilationCallback;

interface ITypeScriptCompilerPlugin {

    // The existing transaction prefix is frozen; new methods are append-only.
    int getContractVersion();

    String getContractHash();

    Bundle getCompilerInfo();

    Bundle prewarm(in Bundle options);

    Bundle startCompilation(
        in Bundle request,
        in ParcelFileDescriptor inputArchive,
        in ParcelFileDescriptor outputArchive,
        ITypeScriptCompilationCallback callback
    );

    boolean cancelCompilation(in Bundle cancellation);
}
