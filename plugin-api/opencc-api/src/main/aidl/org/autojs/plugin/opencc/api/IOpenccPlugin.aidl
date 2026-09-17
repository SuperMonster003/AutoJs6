package org.autojs.plugin.opencc.api;

parcelable org.autojs.plugin.common.api.PluginInfo;

interface IOpenccPlugin {

    org.autojs.plugin.common.api.PluginInfo getInfo();

    String convert(String text, String conversionType);

    List<String> getSupportedConversionTypes();

    List<String> convertBatch(in List<String> texts, String conversionType);

    String convertChain(String text, in List<String> conversionTypes);
}
