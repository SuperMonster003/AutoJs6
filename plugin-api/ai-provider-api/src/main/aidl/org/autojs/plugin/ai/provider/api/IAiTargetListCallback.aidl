package org.autojs.plugin.ai.provider.api;

oneway interface IAiTargetListCallback {

    void onPage(in byte[] page);

    void onFailed(in byte[] error);
}
