package org.autojs.plugin.typescript.api;

import android.os.Bundle;

oneway interface ITypeScriptCompilationCallback {

    void onProgress(in Bundle progress);

    void onCompleted(in Bundle result);
}
