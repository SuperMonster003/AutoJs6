package org.autojs.plugin.yolo.api;

oneway interface IYoloCallback {

    void onDetected(in byte[] result);

    void onFailed(in byte[] error);

    void onCancelled(in byte[] cancellation);
}
