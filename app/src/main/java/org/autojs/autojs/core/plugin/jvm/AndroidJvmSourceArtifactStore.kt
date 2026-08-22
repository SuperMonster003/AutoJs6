package org.autojs.autojs.core.plugin.jvm

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class AndroidJvmSourceArtifactStore(
    context: Context,
    private val drainExecutor: ExecutorService,
) : JvmSourceArtifactStore {
    private val closed = AtomicBoolean(false)
    private val allocation = allocateRoot(context)
    private val parent = allocation.parent
    private val root = allocation.root
    private val rootLease = allocation.lease

    override fun snapshotJavaSource(file: File, maximumBytes: Long): JvmSourceInputSnapshot {
        check(!closed.get()) { "JVM source artifact store is closed" }
        require(maximumBytes > 0L)
        val sourceKind = requireNotNull(org.autojs.autojs.script.JvmSourceKind.fromFileName(file.name)) {
            "JVM source snapshot requires a recognized source extension"
        }
        JvmSourceR1Policy.requireSupportedFileName(sourceKind, file.name)
        val descriptor = Os.open(
            file.absolutePath,
            OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
            0,
        )
        val inputStream = try {
            FileInputStream(descriptor)
        } catch (error: Throwable) {
            runCatching { Os.close(descriptor) }
            throw error
        }
        val bytes = inputStream.use { input ->
            val opened = Os.fstat(input.fd)
            require(OsConstants.S_ISREG(opened.st_mode)) { "JVM source must remain a regular file when opened" }
            JvmSourceBoundedTransfer.read(input, maximumBytes).bytes
        }
        val layout = JvmSourceR1Policy.inspect(sourceKind, bytes)
        val snapshot = newPrivateFile("source", ".${sourceKind.canonicalExtension}")
        try {
            FileOutputStream(snapshot).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            Os.chmod(snapshot.absolutePath, OWNER_READ_ONLY_MODE)
            return InputSnapshot(
                file = snapshot,
                sizeBytes = bytes.size.toLong(),
                sha256Hex = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(bytes)
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) },
                sourceFileName = layout.sourceFileName,
                entryClassName = layout.entryClassName,
                onClosed = ::cleanupClosedRoot,
            )
        } catch (error: Throwable) {
            snapshot.delete()
            throw error
        }
    }

    override fun outputCapture(
        maximumBytes: Long,
        observer: JvmSourceOutputObserver,
    ): JvmSourceOutputCapture {
        check(!closed.get()) { "JVM source artifact store is closed" }
        require(maximumBytes >= 0L)
        val pipe = ParcelFileDescriptor.createReliablePipe()
        return try {
            PipeCapture(pipe[0], pipe[1], maximumBytes, observer, drainExecutor)
        } catch (error: Throwable) {
            pipe.forEach { descriptor -> runCatching { descriptor.close() } }
            throw error
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(ROOT_ALLOCATION_LOCK) {
            ACTIVE_ROOTS.remove(root.path)
            rootLease.close()
            cleanupClosedRootLocked()
        }
    }

    private fun cleanupClosedRoot() {
        if (!closed.get()) return
        synchronized(ROOT_ALLOCATION_LOCK) {
            cleanupClosedRootLocked()
        }
    }

    private fun cleanupClosedRootLocked() {
        if (root.path in ACTIVE_ROOTS) return
        JvmSourceArtifactStaleCleanup.cleanup(parent, ANDROID_FILE_OPERATIONS) { candidate ->
            ANDROID_FILE_OPERATIONS.canonicalFile(candidate) == root
        }
    }

    private fun newPrivateFile(prefix: String, suffix: String): File {
        val candidate = File(root, "$prefix-${UUID.randomUUID()}$suffix").canonicalFile
        require(candidate.parentFile == root) { "JVM source snapshot escaped its private directory" }
        if (!candidate.createNewFile()) error("Unable to reserve JVM source snapshot")
        Os.chmod(candidate.absolutePath, OWNER_READ_WRITE_MODE)
        return candidate
    }

    private class InputSnapshot(
        private val file: File,
        override val sizeBytes: Long,
        override val sha256Hex: String,
        override val sourceFileName: String,
        override val entryClassName: String,
        private val onClosed: () -> Unit,
    ) : JvmSourceInputSnapshot {
        private val claimed = AtomicBoolean(false)
        private val closed = AtomicBoolean(false)

        override fun claimReadDescriptor(): JvmSourceOwnedDescriptor {
            check(!closed.get() && claimed.compareAndSet(false, true))
            return AndroidJvmSourceDescriptor(
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
            )
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                file.delete()
                onClosed()
            }
        }
    }

    private class PipeCapture(
        private val readSide: ParcelFileDescriptor,
        private var writeSide: ParcelFileDescriptor?,
        maximumBytes: Long,
        private val observer: JvmSourceOutputObserver,
        drainExecutor: ExecutorService,
    ) : JvmSourceOutputCapture {
        private val claimed = AtomicBoolean(false)
        private val closed = AtomicBoolean(false)
        private val future: Future<JvmSourceCapturedOutput> = drainExecutor.submit<JvmSourceCapturedOutput> {
            try {
                ParcelFileDescriptor.AutoCloseInputStream(readSide).use { input ->
                    val captured = JvmSourceBoundedTransfer.read(input, maximumBytes, observer)
                    readSide.checkError()
                    captured
                }
            } finally {
                runCatching { observer.onComplete() }
            }
        }

        override fun claimWriteDescriptor(): JvmSourceOwnedDescriptor = synchronized(this) {
            check(!closed.get() && claimed.compareAndSet(false, true))
            val descriptor = checkNotNull(writeSide)
            writeSide = null
            AndroidJvmSourceDescriptor(descriptor)
        }

        override fun await(timeoutMillis: Long): JvmSourceCapturedOutput {
            require(timeoutMillis > 0L)
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS)
        }

        override fun abort(cause: Throwable?) {
            if (!closed.compareAndSet(false, true)) return
            val message = cause?.message?.take(128) ?: "Host closed JVM source output"
            runCatching { readSide.closeWithError(message) }
            synchronized(this) {
                val writer = writeSide
                writeSide = null
                if (writer != null) runCatching { writer.closeWithError(message) }
            }
            future.cancel(true)
        }

        override fun close() = abort()
    }

    private companion object {
        const val DIRECTORY_NAME = "jvm-source-host"
        const val ROOT_LEASE_FILE = ".owner.lock"
        const val OWNER_READ_ONLY_MODE = 0x100
        const val OWNER_READ_WRITE_MODE = 0x180

        val ROOT_ALLOCATION_LOCK = Any()
        val ACTIVE_ROOTS = linkedSetOf<String>()

        val ANDROID_FILE_OPERATIONS = object : JvmSourceArtifactStaleCleanup.Operations {
            override fun typeNoFollow(file: File): JvmSourceArtifactStaleCleanup.NodeType = try {
                val mode = Os.lstat(file.absolutePath).st_mode
                when {
                    OsConstants.S_ISDIR(mode) -> JvmSourceArtifactStaleCleanup.NodeType.DIRECTORY
                    OsConstants.S_ISREG(mode) -> JvmSourceArtifactStaleCleanup.NodeType.REGULAR_FILE
                    OsConstants.S_ISLNK(mode) -> JvmSourceArtifactStaleCleanup.NodeType.SYMBOLIC_LINK
                    else -> JvmSourceArtifactStaleCleanup.NodeType.OTHER
                }
            } catch (error: ErrnoException) {
                if (error.errno == OsConstants.ENOENT) {
                    JvmSourceArtifactStaleCleanup.NodeType.MISSING
                } else {
                    JvmSourceArtifactStaleCleanup.NodeType.OTHER
                }
            }

            override fun canonicalFile(file: File): File? = runCatching { file.canonicalFile }.getOrNull()

            override fun children(directory: File): List<File>? = directory.listFiles()?.toList()

            override fun unlink(file: File): Boolean = runCatching {
                Os.remove(file.absolutePath)
                true
            }.getOrDefault(false)

            override fun deleteDirectory(directory: File): Boolean = runCatching {
                Os.remove(directory.absolutePath)
                true
            }.getOrDefault(false)
        }

        fun allocateRoot(context: Context): RootAllocation {
            val expectedParent = context.noBackupFilesDir.canonicalFile
            val requestedBase = File(expectedParent, DIRECTORY_NAME)
            when (ANDROID_FILE_OPERATIONS.typeNoFollow(requestedBase)) {
                JvmSourceArtifactStaleCleanup.NodeType.MISSING -> {
                    if (!requestedBase.mkdir()) error("Unable to create JVM source host directory")
                }
                JvmSourceArtifactStaleCleanup.NodeType.DIRECTORY -> Unit
                else -> error("JVM source host directory is not an ordinary private directory")
            }
            val base = JvmSourceArtifactStaleCleanup.requirePrivateBase(
                expectedParent,
                requestedBase,
                ANDROID_FILE_OPERATIONS,
            )
            synchronized(ROOT_ALLOCATION_LOCK) {
                JvmSourceArtifactStaleCleanup.cleanup(base, ANDROID_FILE_OPERATIONS) { candidate ->
                    val canonical = ANDROID_FILE_OPERATIONS.canonicalFile(candidate)
                        ?: return@cleanup false
                    canonical.path !in ACTIVE_ROOTS && canClaimStaleRoot(candidate)
                }
                val requestedRoot = File(base, "host-${UUID.randomUUID()}")
                if (!requestedRoot.mkdir()) error("Unable to create isolated JVM source host transaction directory")
                val root = requireNotNull(ANDROID_FILE_OPERATIONS.canonicalFile(requestedRoot))
                require(root.parentFile == base && root.name == requestedRoot.name) {
                    "JVM source host transaction directory escaped its private base"
                }
                val lease = try {
                    RootLease.acquire(root)
                } catch (error: Throwable) {
                    runCatching { ANDROID_FILE_OPERATIONS.deleteDirectory(root) }
                    throw error
                }
                check(ACTIVE_ROOTS.add(root.path)) { "JVM source host root was already active" }
                return RootAllocation(base, root, lease)
            }
        }

        fun canClaimStaleRoot(root: File): Boolean {
            val stream = openLeaseStream(
                File(root, ROOT_LEASE_FILE),
                OsConstants.O_RDWR or OsConstants.O_CREAT or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
            ) ?: return false
            return stream.use { output ->
                if (!OsConstants.S_ISREG(Os.fstat(output.fd).st_mode)) return@use false
                val lock = try {
                    output.channel.tryLock()
                } catch (_: OverlappingFileLockException) {
                    null
                } ?: return@use false
                runCatching { lock.release() }.isSuccess
            }
        }

        fun openLeaseStream(file: File, flags: Int): FileOutputStream? {
            val descriptor = try {
                Os.open(file.absolutePath, flags, OWNER_READ_WRITE_MODE)
            } catch (_: Throwable) {
                return null
            }
            return try {
                FileOutputStream(descriptor)
            } catch (error: Throwable) {
                runCatching { Os.close(descriptor) }
                throw error
            }
        }
    }

    private data class RootAllocation(
        val parent: File,
        val root: File,
        val lease: RootLease,
    )

    private class RootLease private constructor(
        private val file: File,
        private val stream: FileOutputStream,
        private val lock: FileLock,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            runCatching { lock.release() }
            runCatching { stream.close() }
            runCatching { Os.remove(file.absolutePath) }
        }

        companion object {
            fun acquire(root: File): RootLease {
                val file = File(root, ROOT_LEASE_FILE)
                val stream = requireNotNull(
                    openLeaseStream(
                        file,
                        OsConstants.O_RDWR or OsConstants.O_CREAT or OsConstants.O_EXCL or
                            OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
                    ),
                ) { "Unable to create the JVM source host root lease" }
                try {
                    require(OsConstants.S_ISREG(Os.fstat(stream.fd).st_mode)) {
                        "JVM source host root lease is not a regular file"
                    }
                    val lock = requireNotNull(stream.channel.tryLock()) {
                        "Unable to lock the JVM source host root lease"
                    }
                    return RootLease(file, stream, lock)
                } catch (error: Throwable) {
                    runCatching { stream.close() }
                    runCatching { Os.remove(file.absolutePath) }
                    throw error
                }
            }
        }
    }
}

internal class AndroidJvmSourceDescriptor(
    internal val descriptor: ParcelFileDescriptor,
) : JvmSourceOwnedDescriptor {
    override fun close() = descriptor.close()
}
