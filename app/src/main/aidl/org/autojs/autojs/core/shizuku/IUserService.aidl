package org.autojs.autojs.core.shizuku;

import android.os.ParcelFileDescriptor;
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

}
