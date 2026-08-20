package org.autojs.plugin.ondeviceai.api;

oneway interface IAiModelListCallback {

    void onPage(in byte[] page);

    void onFailed(in byte[] error);
}
