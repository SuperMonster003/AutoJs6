package org.autojs.plugin.apkbuilder.template;

import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateInfo;
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateRequest;
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateResult;
import org.autojs.plugin.apkbuilder.template.ApkBuildRequest;
import org.autojs.plugin.apkbuilder.template.IApkBuildCallback;
import org.autojs.plugin.apkbuilder.template.IApkBuildSession;

parcelable org.autojs.plugin.common.api.PluginInfo;

interface IApkBuilderTemplatePlugin {

    org.autojs.plugin.common.api.PluginInfo getInfo();

    ApkBuilderTemplateInfo getTemplateInfo();

    ApkBuilderTemplateResult openTemplate(in ApkBuilderTemplateRequest request);

    IApkBuildSession openBuildSession(in ApkBuildRequest request, IApkBuildCallback callback);
}
