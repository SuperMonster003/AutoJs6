package org.autojs.plugin.screencolorpicker.api;

import org.autojs.plugin.common.api.PluginCapabilityKeys;

/** Stable identity, discovery and compatibility contract shared by the host and plugin. */
public final class ScreenColorPickerContract {

    public static final int VERSION = 1;
    public static final int MIN_SUPPORTED_VERSION = VERSION;
    public static final int MAX_SUPPORTED_VERSION = VERSION;

    public static final String SERVICE_ACTION = "org.autojs.plugin.SCREEN_COLOR_PICKER";
    public static final String SERVICE_CATEGORY = "screen-color-picker";
    public static final String PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN";

    public static final String PLUGIN_ID = "three-color-picker";
    public static final String ENGINE = "screen-color-picker";
    public static final String VARIANT_DEFAULT = "default";

    public static final String DEFAULT_PACKAGE_NAME =
            "io.github.supermonster003.autojs6.plugin.three.color.picker";
    public static final long REQUIRED_HOST_VERSION_CODE = 5316L;

    public static final String CAPABILITY_CONTRACT_VERSION =
            "screenColorPickerContractVersion";
    public static final String CAPABILITY_REQUIRES_HOST_VERSION =
            PluginCapabilityKeys.REQUIRES_HOST_VERSION;

    private ScreenColorPickerContract() {
        throw new AssertionError("No instances");
    }
}
