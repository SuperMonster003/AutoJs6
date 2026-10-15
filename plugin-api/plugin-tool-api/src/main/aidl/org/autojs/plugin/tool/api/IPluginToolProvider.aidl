package org.autojs.plugin.tool.api;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import org.autojs.plugin.tool.api.IPluginToolCallback;

parcelable org.autojs.plugin.common.api.PluginInfo;

/**
 * Binder contract of a plugin that describes its own tools (docs/dev/plugin-tool-protocol-v1.md,
 * MCP Server roadmap P12 D29 / D30). A plugin joins by exporting one service for
 * PluginToolContract.SERVICE_ACTION that returns this interface; the host lists, validates and
 * forwards every call, so consumers (MCP, Agent, scripts) never bind the plugin themselves.
 *
 * Every method answers quickly on a Binder thread: invoke() only accepts or refuses a call, the work
 * runs elsewhere and ends with exactly one IPluginToolCallback.onResult or onError. Transactions may
 * only ever be appended (PluginToolAidlOrderTest).
 */
interface IPluginToolProvider {

    /** Identity and capabilities, including PluginToolCapabilityKeys.CONTRACT_VERSION; never null. */
    org.autojs.plugin.common.api.PluginInfo getInfo();

    /**
     * The tool catalog for the request Bundle described by PluginToolCatalogRequestKeys, answered with
     * PluginToolCatalogKeys (the catalog JSON inline, at most PluginToolLimits.MAX_CATALOG_BYTES). The
     * host caches it per (package, versionCode); a plugin may adapt it to the device, for example while
     * a model is missing. Never throws for invalid input: the answer carries an error code. Never null.
     */
    Bundle getCatalog(in Bundle request);

    /**
     * Accepts or refuses one call described by PluginToolInvokeKeys. inputs holds the descriptors of
     * the image and file arguments the host resolved, in the order PluginToolInvokeKeys.INPUTS_JSON
     * names them; the plugin owns and closes them. The answer (PluginToolAcceptKeys) comes back at
     * once; an accepted call later reports to callback exactly once. Never null.
     */
    Bundle invoke(in Bundle request, in ParcelFileDescriptor[] inputs, IPluginToolCallback callback);

    /** Asks a running call (PluginToolCancelKeys.INVOCATION_ID) to stop; it then ends with PluginToolErrorCodes.CANCELLED. */
    oneway void cancel(in Bundle request);
}
