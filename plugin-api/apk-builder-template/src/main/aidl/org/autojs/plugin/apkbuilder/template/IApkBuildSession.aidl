package org.autojs.plugin.apkbuilder.template;

import org.autojs.plugin.apkbuilder.template.ApkBuildProgress;

interface IApkBuildSession {

    void start();

    void cancel();

    void close();

    ApkBuildProgress getProgress();
}
