package org.autojs.autojs.core.plugin.dex

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Host-private snapshot and verified-copy cache publication used only by the experimental harness.
 *
 * Construct this once during process initialization, before the harness can start its single live
 * session. Startup recovery deletes only transaction-shaped leftovers; published `.dex.zip` cache
 * entries are retained. A remote-writable staging inode is never used as a published cache inode.
 * [drainExecutor] must be a dedicated bounded asynchronous executor; inline execution would block
 * before the write descriptor can be dispatched.
 */
internal class AndroidDexCompilerArtifactStore(
    context: Context,
    private val drainExecutor: Executor,
) {

    private val root = File(context.noBackupFilesDir, DIRECTORY_NAME).apply {
        if (!exists() && !mkdirs()) throw IllegalStateException("Unable to create DEX compiler transaction directory")
        require(isDirectory)
    }.canonicalFile.also { directory ->
        directory.listFiles().orEmpty()
            .filter { it.isFile && DexCompilerArtifactRecoveryPolicy.isTransientArtifact(it.name) }
            .forEach(File::delete)
    }

    fun snapshot(input: InputStream, maximumBytes: Long): DexCompilerInputSnapshot {
        require(maximumBytes > 0L)
        val file = newPrivateFile("input", ".jar")
        try {
            val summary = FileOutputStream(file).use { output ->
                val copied = DexCompilerBoundedStreamTransfer.copy(input, output, maximumBytes)
                output.fd.sync()
                copied
            }
            if (summary.sizeBytes <= 0L) throw IllegalArgumentException("Program snapshot is empty")
            Os.chmod(file.absolutePath, READ_ONLY_MODE)
            return InputSnapshot(file, summary.sizeBytes, summary.sha256)
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    fun outputTransaction(): DexCompilerOutputTransaction {
        val staging = newPrivateFile("output", ".partial")
        var verified: File? = null
        var pipe: Array<ParcelFileDescriptor>? = null
        return try {
            val verifiedFile = newPrivateFile("verified", ".partial")
            verified = verifiedFile
            val descriptors = ParcelFileDescriptor.createReliablePipe()
            pipe = descriptors
            OutputTransaction(
                staging = staging,
                verified = verifiedFile,
                root = root,
                readSide = descriptors[0],
                writeSide = descriptors[1],
                drainExecutor = drainExecutor,
            )
        } catch (error: Throwable) {
            pipe?.forEach { descriptor -> runCatching { descriptor.close() } }
            staging.delete()
            verified?.delete()
            throw error
        }
    }

    private fun newPrivateFile(prefix: String, suffix: String): File {
        val file = checkedChild(File(root, "$prefix-${UUID.randomUUID()}$suffix"))
        try {
            if (!file.createNewFile()) throw IllegalStateException("Unable to reserve DEX compiler transaction file")
            Os.chmod(file.absolutePath, OWNER_READ_WRITE_MODE)
            return file
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    private fun checkedChild(file: File): File = file.canonicalFile.also { child ->
        require(child.parentFile == root) { "DEX compiler artifact escaped its private directory" }
    }

    private class InputSnapshot(
        private val file: File,
        override val sizeBytes: Long,
        override val sha256: String,
    ) : DexCompilerInputSnapshot {
        private val descriptorClaimed = AtomicBoolean(false)
        private val closed = AtomicBoolean(false)

        override fun claimReadDescriptor(): DexCompilerOwnedDescriptor {
            check(!closed.get() && descriptorClaimed.compareAndSet(false, true)) {
                "Program descriptor may be claimed exactly once"
            }
            return AndroidDexCompilerDescriptor(
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
            )
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) file.delete()
        }
    }

    private class OutputTransaction(
        private val staging: File,
        private val verified: File,
        private val root: File,
        private val readSide: ParcelFileDescriptor,
        private var writeSide: ParcelFileDescriptor?,
        private val drainExecutor: Executor,
    ) : DexCompilerOutputTransaction {
        private val descriptorClaimed = AtomicBoolean(false)
        private var state = State.OPEN
        private var drainState = DrainState.NEW

        override fun beginDrain(
            maxBytes: Long,
            onTerminal: (DexCompilerOutputDrainTerminal) -> Unit,
        ) {
            require(maxBytes > 0L)
            synchronized(this) {
                check(state == State.OPEN && drainState == DrainState.NEW) {
                    "Output drain may be started exactly once"
                }
                drainState = DrainState.RUNNING
            }
            try {
                drainExecutor.execute { drain(maxBytes, onTerminal) }
            } catch (error: RuntimeException) {
                synchronized(this) {
                    if (drainState == DrainState.RUNNING) drainState = DrainState.FAILED
                }
                closePipe(withError = true)
                throw error
            }
        }

        override fun claimWriteDescriptor(): DexCompilerOwnedDescriptor = synchronized(this) {
            check(state == State.OPEN && drainState == DrainState.RUNNING &&
                descriptorClaimed.compareAndSet(false, true)
            ) {
                "Output descriptor may be claimed exactly once"
            }
            val descriptor = checkNotNull(writeSide) { "Output pipe writer is unavailable" }
            writeSide = null
            AndroidDexCompilerDescriptor(
                descriptor,
            )
        }

        override fun readBytes(maxBytes: Long): ByteArray = synchronized(this) {
            check(state == State.OPEN && descriptorClaimed.get() && drainState == DrainState.SUCCEEDED)
            val length = staging.length()
            require(length in 1L..maxBytes && length <= Int.MAX_VALUE) { "DEX output exceeds quota" }
            val bytes = ByteArray(length.toInt())
            FileInputStream(staging).use { input ->
                var offset = 0
                while (offset < bytes.size) {
                    val count = input.read(bytes, offset, bytes.size - offset)
                    if (count < 0) throw IllegalArgumentException("DEX output was truncated")
                    offset += count
                }
                if (input.read() != -1) throw IllegalArgumentException("DEX output grew while being read")
            }
            bytes
        }

        override fun publishVerified(cacheKeySha256: String, bytes: ByteArray) = synchronized(this) {
            check(state == State.OPEN && descriptorClaimed.get())
            require(SHA256.matches(cacheKeySha256)) { "Cache key must be lowercase SHA-256" }
            require(bytes.isNotEmpty())
            val published = File(root, "$cacheKeySha256.dex.zip").canonicalFile
            require(published.parentFile == root) { "DEX cache target escaped its private directory" }
            FileOutputStream(verified, false).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            require(verified.length() == bytes.size.toLong()) { "Verified publication copy has the wrong size" }
            val expectedDigest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val actualDigest = FileInputStream(verified).use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
                digest.digest()
            }
            require(MessageDigest.isEqual(expectedDigest, actualDigest)) {
                "Verified publication copy differs from validated output"
            }
            Os.chmod(verified.absolutePath, READ_ONLY_MODE)
            Os.rename(verified.absolutePath, published.absolutePath)
            state = State.PUBLISHED
            staging.delete()
            Unit
        }

        override fun rollback() = synchronized(this) {
            if (state == State.OPEN) {
                closePipe(withError = true)
                staging.delete()
                verified.delete()
                state = State.ROLLED_BACK
            }
        }

        override fun close() = synchronized(this) {
            closePipe(withError = state == State.OPEN || state == State.ROLLED_BACK)
            staging.delete()
            verified.delete()
            state = State.CLOSED
        }

        private fun drain(
            maxBytes: Long,
            onTerminal: (DexCompilerOutputDrainTerminal) -> Unit,
        ) {
            val terminal = try {
                // Opening the path and checking transaction ownership must share the same lock as
                // rollback/close. Otherwise a queued drain can recreate a staging pathname after
                // the transaction has already deleted it.
                val output = synchronized(this) {
                    if (state == State.OPEN && drainState == DrainState.RUNNING) {
                        FileOutputStream(staging, false)
                    } else {
                        null
                    }
                }
                if (output == null) {
                    DexCompilerOutputDrainTerminal.Failure(null)
                } else {
                    val summary = output.use { outputStream ->
                        val input = ParcelFileDescriptor.AutoCloseInputStream(readSide)
                        try {
                            val copied = DexCompilerBoundedStreamTransfer.copy(
                                input = input,
                                output = outputStream,
                                maximumBytes = maxBytes,
                                isCancelled = { synchronized(this) { state != State.OPEN } },
                            )
                            readSide.checkError()
                            outputStream.fd.sync()
                            copied
                        } catch (error: Throwable) {
                            runCatching { readSide.closeWithError(PIPE_REJECTION_MESSAGE) }
                            throw error
                        } finally {
                            runCatching { input.close() }
                        }
                    }
                    val accepted = synchronized(this) {
                        if (state == State.OPEN && drainState == DrainState.RUNNING) {
                            drainState = DrainState.SUCCEEDED
                            true
                        } else {
                            false
                        }
                    }
                    if (accepted) {
                        DexCompilerOutputDrainTerminal.Success(summary)
                    } else {
                        DexCompilerOutputDrainTerminal.Failure(null)
                    }
                }
            } catch (error: Throwable) {
                synchronized(this) {
                    if (drainState == DrainState.RUNNING) drainState = DrainState.FAILED
                }
                DexCompilerOutputDrainTerminal.Failure(error)
            }
            runCatching { onTerminal(terminal) }
        }

        private fun closePipe(withError: Boolean) {
            if (withError) {
                runCatching { readSide.closeWithError(PIPE_REJECTION_MESSAGE) }
            } else {
                runCatching { readSide.close() }
            }
            val writer = writeSide
            writeSide = null
            if (writer != null) runCatching { writer.close() }
        }

        private enum class State {
            OPEN,
            PUBLISHED,
            ROLLED_BACK,
            CLOSED,
        }

        private enum class DrainState {
            NEW,
            RUNNING,
            SUCCEEDED,
            FAILED,
        }
    }

    private companion object {
        const val DIRECTORY_NAME = "dex-compiler-experimental"
        const val READ_ONLY_MODE = 0x100
        const val OWNER_READ_WRITE_MODE = 0x180
        const val PIPE_REJECTION_MESSAGE = "Host rejected DEX compiler output"
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}
