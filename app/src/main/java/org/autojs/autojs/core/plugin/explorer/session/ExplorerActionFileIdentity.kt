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

/**
 * A launch target paired with the identity captured when its capability was created. A protocol v25 rename of the
 * target itself moves [file] to the new path; [directory] keeps the kind seen at launch.
 * zh-CN: 启动目标及创建能力时捕获的身份. 协议 v25 对目标本身改名时 [file] 随之更新为新路径; [directory] 保留启动时的类型.
 */
internal data class ExplorerActionHostTargetState(
    @Volatile var file: File,
    @Volatile var identity: ExplorerActionFileIdentity,
    val directory: Boolean = file.isDirectory,
)

/** Local transaction vocabulary retained inside the session state machine. */
internal typealias TargetIdentity = ExplorerActionFileIdentity
internal typealias TargetState = ExplorerActionHostTargetState
