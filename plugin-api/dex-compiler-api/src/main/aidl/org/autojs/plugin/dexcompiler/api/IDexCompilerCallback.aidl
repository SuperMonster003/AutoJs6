package org.autojs.plugin.dexcompiler.api;

oneway interface IDexCompilerCallback {

    void onStarted(in byte[] metadata);

    void onProgress(in byte[] progress);

    void onCompleted(in byte[] result);

    void onFailed(in byte[] error);

    void onCancelled(in byte[] cancellation);
}
