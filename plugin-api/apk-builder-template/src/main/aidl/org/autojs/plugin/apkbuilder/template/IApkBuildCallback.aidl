package org.autojs.plugin.apkbuilder.template;

import org.autojs.plugin.apkbuilder.template.ApkBuildProgress;
import org.autojs.plugin.apkbuilder.template.ApkBuildResult;

oneway interface IApkBuildCallback {

    void onStarted(in ApkBuildProgress progress);

    void onProgress(in ApkBuildProgress progress);

    void onCompleted(in ApkBuildResult result);

    void onFailed(in ApkBuildResult result);

    void onCancelled(in ApkBuildResult result);
}
