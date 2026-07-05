package org.autojs.plugin.pinyin.api;

import android.os.Bundle;

parcelable org.autojs.plugin.common.api.PluginInfo;

interface IPinyinPlugin {

    org.autojs.plugin.common.api.PluginInfo getInfo();

    String convert(String text, in Bundle options);

    String simple(String text, boolean enableNumericTone, boolean enableSegment);

    String fromCodePoint(int codePoint);

    String fromPhrase(String phrase);
}
