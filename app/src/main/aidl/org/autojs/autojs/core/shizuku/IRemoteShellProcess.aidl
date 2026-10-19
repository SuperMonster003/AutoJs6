package org.autojs.autojs.core.shizuku;

import android.os.ParcelFileDescriptor;

// A process the Shizuku user service started for the host (shell.spawn with adb, shizuku.spawn, new Shell({ adb: true })).
// Each stream getter hands out its pipe end once.
interface IRemoteShellProcess {

    ParcelFileDescriptor getOutputStream() = 1;
    ParcelFileDescriptor getInputStream() = 2;
    ParcelFileDescriptor getErrorStream() = 3;

    boolean waitForTimeout(long timeoutMillis) = 4;
    int exitValue() = 5;
    boolean alive() = 6;
    void destroy() = 7;
    int pid() = 8;

}
