package org.autojs.plugin.explorer.api;

import android.os.Bundle;

parcelable org.autojs.plugin.common.api.PluginInfo;

interface IExplorerActionPlugin {

    org.autojs.plugin.common.api.PluginInfo getInfo();

    Bundle getActionCatalog();
}
