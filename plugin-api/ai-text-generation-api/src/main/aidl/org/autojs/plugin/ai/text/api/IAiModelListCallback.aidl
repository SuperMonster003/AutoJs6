package org.autojs.plugin.ai.text.api;

oneway interface IAiModelListCallback {

    void onPage(in byte[] page);

    void onFailed(in byte[] error);
}
