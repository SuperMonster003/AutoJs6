package org.autojs.autojs.core.plugin.explorer.session

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.IOException

/** Stable filesystem identity used to reject replaced targets and recovery paths. */
internal data class ExplorerActionFileIdentity(
    val device: Long,
    val inode: Long,
    val size: Long,
    val lastModifiedMillis: Long,
) {

    companion object {
        fun capture(file: File): ExplorerActionFileIdentity {
            val stat = try {
                Os.stat(file.path)
            } catch (error: ErrnoException) {
                throw IOException("Explorer Action target identity is unavailable", error)
            }
            return ExplorerActionFileIdentity(
                device = stat.st_dev,
                inode = stat.st_ino,
                size = stat.st_size,
                lastModifiedMillis = file.lastModified(),
            )
        }

        fun captureRegularFile(file: File): ExplorerActionFileIdentity {
            val stat = try {
                Os.lstat(file.path)
            } catch (error: ErrnoException) {
                throw IOException("Explorer Action output identity is unavailable", error)
            }
            require(OsConstants.S_ISREG(stat.st_mode) && !OsConstants.S_ISLNK(stat.st_mode)) {
                "Explorer Action output is not a regular file"
            }
            return ExplorerActionFileIdentity(
                device = stat.st_dev,
                inode = stat.st_ino,
                size = stat.st_size,
                lastModifiedMillis = file.lastModified(),
            )
        }
    }
}

/** A launch target paired with the identity captured when its capability was created. */
internal data class ExplorerActionHostTargetState(
    val file: File,
    var identity: ExplorerActionFileIdentity,
)

/** Local transaction vocabulary retained inside the session state machine. */
internal typealias TargetIdentity = ExplorerActionFileIdentity
internal typealias TargetState = ExplorerActionHostTargetState
