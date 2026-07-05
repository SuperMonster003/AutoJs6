package org.autojs.plugin.opencc.api;

parcelable org.autojs.plugin.common.api.PluginInfo;

interface IOpenccPlugin {

    org.autojs.plugin.common.api.PluginInfo getInfo();

    String convert(String text, String conversionType);
}
