package org.autojs.plugin.screencolorpicker.api;

import android.app.PendingIntent;
parcelable org.autojs.plugin.common.api.PluginInfo;

/** Version 1 control plane for the Screen Color Picker plugin. */
interface IScreenColorPickerPlugin {

    org.autojs.plugin.common.api.PluginInfo getInfo();

    int getState();

    PendingIntent getStartPendingIntent();

    boolean stop();
}
