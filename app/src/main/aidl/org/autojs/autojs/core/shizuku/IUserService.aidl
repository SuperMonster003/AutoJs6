package org.autojs.autojs.core.shizuku;

import android.os.ParcelFileDescriptor;
import android.view.Surface;
import org.autojs.autojs.core.shizuku.IRemoteShellProcess;

interface IUserService {

    void destroy() = 16777114; // Destroy method defined by Shizuku server

    void exit() = 1; // Exit method defined by user

    String execCommand(String command) = 2;

    String currentPackage() = 11;
    String currentActivity() = 12;
    String currentComponent() = 13;
    String currentComponentShort() = 14;

    String listExplorerDirectory(String path, String snapshotToken, int offset) = 21;
    oneway void closeExplorerDirectory(String snapshotToken) = 22;
    String executeExplorerFileOperation(String requestJson) = 23;
    ParcelFileDescriptor openExplorerFile(String requestJson) = 24;
    String snapshotExplorerFile(String requestJson) = 25;
    String saveExplorerFile(String requestJson, in ParcelFileDescriptor contents) = 26;
    String reconcileExplorerFileSave(String requestJson) = 27;
    String manageExplorerFileBackup(String requestJson) = 28;

    // A long-lived process with piped streams, or on a pseudo-terminal with pty; it ends when the host process (client) dies.
    IRemoteShellProcess newShellProcess(in String[] command, in String[] env, String dir, boolean pty, IBinder client) = 31;

    // The host process token: the service ends what it started and exits once every attached host died,
    // and exits 30 s after it started when no host attached (docs/shizuku-oom/ROADMAP.md D5).
    oneway void attachHost(IBinder host) = 41;

    // ---- Virtual screen (docs/virtual-screen/README.md) ----
    // The display is owned by this shell-uid process: it dies with the process, so it must not be
    // created by a one-shot execCommand. `surface` is an ImageReader surface in the app process.
    int createVirtualDisplay(in Surface surface, int width, int height, int dpi, int flags) = 51;
    boolean releaseVirtualDisplay(int displayId) = 52;
    String listVirtualDisplays() = 53;
    String virtualDisplayInfo(int displayId) = 54;
    int launchOnDisplay(int displayId, String packageName, String component, boolean clearTask) = 55;
    boolean tapOnDisplay(int displayId, int x, int y) = 56;
    boolean swipeOnDisplay(int displayId, int x1, int y1, int x2, int y2, int durationMs) = 57;
    boolean keyOnDisplay(int displayId, int keyCode) = 58;
    boolean textOnDisplay(int displayId, String text) = 59;
    boolean forceStopOnDisplay(int displayId, String packageName) = 60;
    // ACTION_VIEW app links (e.g. example://...) must also be launched on the virtual display;
    // app.openUrl() alone would resolve them on the default display.
    int launchUriOnDisplay(int displayId, String uri) = 61;

}
