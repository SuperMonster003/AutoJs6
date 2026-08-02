package org.autojs.autojs.core.plugin.explorer.output

import android.system.Os
import java.io.File
import java.io.FileDescriptor

internal data class ExplorerActionFileIdentity(
    val device: Long,
    val inode: Long,
)

internal interface ExplorerActionFileIdentityReader {
    fun stat(file: File): ExplorerActionFileIdentity
    fun fstat(descriptor: FileDescriptor): ExplorerActionFileIdentity
}

internal object ExplorerActionAndroidFileIdentityReader : ExplorerActionFileIdentityReader {

    override fun stat(file: File): ExplorerActionFileIdentity = Os.stat(file.path).toIdentity()

    override fun fstat(descriptor: FileDescriptor): ExplorerActionFileIdentity =
        Os.fstat(descriptor).toIdentity()

    private fun android.system.StructStat.toIdentity() = ExplorerActionFileIdentity(
        device = st_dev,
        inode = st_ino,
    )
}
