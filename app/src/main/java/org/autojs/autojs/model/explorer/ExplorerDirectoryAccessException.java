package org.autojs.autojs.model.explorer;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.IOException;

/** A failed directory listing must never be presented as a successfully loaded empty directory. */
public final class ExplorerDirectoryAccessException extends IOException {

    public enum Reason {
        NOT_FOUND,
        NOT_DIRECTORY,
        ACCESS_DENIED,
        READ_FAILED
    }

    private final String mPath;
    private final Reason mReason;

    public ExplorerDirectoryAccessException(String path, Reason reason, Throwable cause) {
        super("Cannot read directory " + path + " (" + reason + ")", cause);
        mPath = path;
        mReason = reason;
    }

    public String getPath() {
        return mPath;
    }

    public Reason getReason() {
        return mReason;
    }

    /** Stat preserves ENOENT versus EACCES, unlike File.exists(), which returns false for both. */
    static ExplorerDirectoryAccessException forFailedListing(String path) {
        try {
            if (!OsConstants.S_ISDIR(Os.stat(path).st_mode)) {
                return new ExplorerDirectoryAccessException(path, Reason.NOT_DIRECTORY, null);
            }
            if (!Os.access(path, OsConstants.R_OK | OsConstants.X_OK)) {
                return new ExplorerDirectoryAccessException(path, Reason.ACCESS_DENIED, null);
            }
        } catch (ErrnoException error) {
            Reason reason;
            if (error.errno == OsConstants.ENOENT) {
                reason = Reason.NOT_FOUND;
            } else if (error.errno == OsConstants.ENOTDIR) {
                reason = Reason.NOT_DIRECTORY;
            } else if (error.errno == OsConstants.EACCES || error.errno == OsConstants.EPERM) {
                reason = Reason.ACCESS_DENIED;
            } else {
                reason = Reason.READ_FAILED;
            }
            return new ExplorerDirectoryAccessException(path, reason, error);
        } catch (SecurityException error) {
            return new ExplorerDirectoryAccessException(path, Reason.ACCESS_DENIED, error);
        }
        // The mount or directory may have changed between list() and stat(). Keep the failure.
        return new ExplorerDirectoryAccessException(path, Reason.READ_FAILED, null);
    }
}
