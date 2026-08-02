package org.autojs.autojs.core.plugin.explorer.output

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream

/** Opens the same-directory publication temporary once with CREATE_NEW semantics. */
internal object ExplorerActionAndroidOutputFileOpener : ExplorerActionOutputFileOpener {

    override fun createNew(destination: File): FileOutputStream {
        val descriptor = try {
            Os.open(
                destination.path,
                OsConstants.O_WRONLY or
                    OsConstants.O_CREAT or
                    OsConstants.O_EXCL or
                    OsConstants.O_NOFOLLOW,
                OsConstants.S_IRUSR or OsConstants.S_IWUSR,
            )
        } catch (error: ErrnoException) {
            if (error.errno == OsConstants.EEXIST || error.errno == OsConstants.ELOOP) {
                throw ExplorerActionOutputPublisher.DestinationAlreadyExistsException(destination)
            }
            throw error
        }
        return try {
            FileOutputStream(descriptor)
        } catch (error: Throwable) {
            runCatching { Os.close(descriptor) }
            throw error
        }
    }
}

/** Publishes with link(2), whose destination creation is atomic and never replaces a file. */
internal object ExplorerActionAndroidOutputNoReplaceCommitter : ExplorerActionOutputNoReplaceCommitter {

    override fun commit(
        temporary: File,
        destination: File,
    ): ExplorerActionOutputCommitMode {
        try {
            Os.link(temporary.path, destination.path)
            return ExplorerActionOutputCommitMode.HARD_LINK
        } catch (error: ErrnoException) {
            if (error.errno == OsConstants.EEXIST || error.errno == OsConstants.ELOOP) {
                throw ExplorerActionOutputPublisher.DestinationAlreadyExistsException(destination)
            }
            if (!isHardLinkUnsupported(error.errno)) {
                throw error
            }
        }

        // Shared/external-storage file systems commonly reject link(2). The completed and
        // fsynced temporary is still a sibling, so rename remains atomic. The explicit absence
        // check preserves create-sibling behavior, while post-rename inode/hash checks in the
        // publisher detect a destination race instead of accepting an unverified result.
        try {
            Os.lstat(destination.path)
            throw ExplorerActionOutputPublisher.DestinationAlreadyExistsException(destination)
        } catch (error: ErrnoException) {
            if (error.errno != OsConstants.ENOENT) throw error
        }
        try {
            Os.rename(temporary.path, destination.path)
        } catch (error: ErrnoException) {
            throw ExplorerActionOutputPublisher.AtomicRenameFallbackException(
                temporary,
                destination,
                error,
            )
        }
        return ExplorerActionOutputCommitMode.ATOMIC_RENAME_FALLBACK
    }

    override fun unlink(file: File) {
        Os.remove(file.path)
    }

    override fun syncDirectory(directory: File): ExplorerActionOutputDirectorySync {
        val descriptor = try {
            Os.open(
                directory.path,
                OsConstants.O_RDONLY,
                0,
            )
        } catch (error: ErrnoException) {
            if (isDirectorySyncUnsupported(error.errno)) {
                return ExplorerActionOutputDirectorySync.UNSUPPORTED
            }
            throw error
        }
        try {
            return try {
                Os.fsync(descriptor)
                ExplorerActionOutputDirectorySync.SYNCED
            } catch (error: ErrnoException) {
                if (isDirectorySyncUnsupported(error.errno)) {
                    ExplorerActionOutputDirectorySync.UNSUPPORTED
                } else {
                    throw error
                }
            }
        } finally {
            Os.close(descriptor)
        }
    }

    private fun isHardLinkUnsupported(errno: Int): Boolean =
        errno == OsConstants.EPERM ||
            errno == OsConstants.EXDEV ||
            errno == OsConstants.EOPNOTSUPP

    private fun isDirectorySyncUnsupported(errno: Int): Boolean =
        errno == OsConstants.EINVAL || errno == OsConstants.EOPNOTSUPP
}
