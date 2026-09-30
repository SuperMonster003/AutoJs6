package org.autojs.plugin.installer.api;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import org.autojs.plugin.common.api.PluginInfo;
import org.autojs.plugin.installer.api.IInstallerCallback;
import org.autojs.plugin.installer.api.IInstallerSession;
import org.autojs.plugin.installer.api.IInstallerSessionCallback;

/**
 * Entry point of an installer plugin (action InstallerActions.SERVICE_ACTION, category
 * InstallerActions.SERVICE_CATEGORY). Transaction codes follow the declaration order, so methods
 * may only ever be appended, never inserted, removed or renamed. Contract version 1 declares the
 * ten methods below. Every request Bundle carries a JSON document under
 * InstallerContract.KEY_REQUEST_JSON plus KEY_CONTRACT_VERSION and KEY_HOST_VERSION_CODE; every
 * asynchronous answer arrives exactly once through the callback. Non-oneway methods answer within
 * the Binder budget without touching a privileged process.
 */
interface IInstallerPlugin {
    /** Identity, version and capabilities of the plugin; never null. */
    PluginInfo getInfo();

    /** Live capability bundle keyed by InstallerCapabilityKeys; never null for contract version 1. */
    Bundle getCapabilities();

    /**
     * Cached state of one authorizer (InstallerContract.AUTHORIZER_NONE / SHIZUKU / ROOT) as JSON
     * under InstallerContract.KEY_STATUS_JSON: `available`, `running`, `granted`, `reason`.
     * Cheap: no privileged process is started here.
     */
    Bundle getAuthorizerState(String authorizer);

    /**
     * Asks the user for the authorizer (Shizuku permission dialog, root prompt). The result JSON
     * under KEY_RESULT_JSON carries `granted`; AUTHORIZER_UNAVAILABLE or AUTHORIZER_DENIED arrive
     * through onError.
     */
    void requestAuthorizer(String authorizer, IInstallerCallback callback);

    /**
     * Reads one package archive without installing it. The descriptor is read-only and owned by
     * the host; the request JSON carries `displayName` and `size`. The result JSON is the
     * PackageArchiveInfo document (format, package, versions, splits, signatures, installed state).
     */
    void inspect(in ParcelFileDescriptor source, in Bundle request, IInstallerCallback callback);

    /**
     * Opens an installation session for one package (one APK, or the split set listed in the
     * request) or for a batch of independent packages, at most InstallerContract.MAX_BATCH_SOURCES
     * descriptors. Descriptors are read-only and owned by the host; the plugin duplicates them and
     * closes its copies before onCompleted / onFailed. Returns null when the request is refused
     * outright (the reason arrives through onFailed with the host session id).
     */
    IInstallerSession openSession(in ParcelFileDescriptor[] sources, in Bundle request, IInstallerSessionCallback callback);

    /**
     * Uninstalls one package. The request JSON carries `packageName`, `keepData`, `user`,
     * `authorizer` and `interaction`; the result JSON is the UninstallResult document.
     */
    void uninstall(in Bundle request, IInstallerCallback callback);

    /**
     * Which component currently answers APK views, as JSON under KEY_STATUS_JSON: `component`,
     * `isSelf`, `method`, `requiresClear`. Cheap: a package-manager query only.
     */
    Bundle getDefaultInstallerState();

    /**
     * Makes the plugin the preferred APK handler (enable) or removes that preference (disable)
     * through the authorizer named in the request JSON. The result JSON repeats the default state.
     */
    void setDefaultInstaller(boolean enable, in Bundle request, IInstallerCallback callback);

    /**
     * Device users visible to the authorizer named in the request JSON, as the DeviceUser list
     * under `users` of the result JSON; the none authorizer only reports the current user.
     */
    void getUsers(in Bundle request, IInstallerCallback callback);
}
