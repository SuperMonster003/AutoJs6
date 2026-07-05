package org.autojs.plugin.pinyin4j.api;

import android.os.Bundle;

parcelable org.autojs.plugin.common.api.PluginInfo;

interface IPinyin4jPlugin {

    org.autojs.plugin.common.api.PluginInfo getInfo();

    String of(String text, in Bundle options);

    String asTone(String text);
}
