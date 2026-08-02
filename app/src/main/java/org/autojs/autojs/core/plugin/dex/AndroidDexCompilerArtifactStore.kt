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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Host-private snapshot and verified-copy cache publication used only by the experimental harness.
 *
 * Construct this once during process initialization, before the harness can start its single live
 * session. Startup recovery deletes only transaction-shaped leftovers; published `.dex.zip` cache
 * entries are retained. A remote-writable staging inode is never used as a published cache inode.
 */
internal class AndroidDexCompilerArtifactStore(context: Context) {

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
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        try {
            FileOutputStream(file).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    total = Math.addExact(total, count.toLong())
                    if (total > maximumBytes) throw IllegalArgumentException("Program snapshot exceeds quota")
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
            if (total <= 0L) throw IllegalArgumentException("Program snapshot is empty")
            Os.chmod(file.absolutePath, READ_ONLY_MODE)
            return InputSnapshot(file, total, digest.digest().toHex())
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    fun outputTransaction(): DexCompilerOutputTransaction {
        val staging = newPrivateFile("output", ".partial")
        var verified: File? = null
        return try {
            val verifiedFile = newPrivateFile("verified", ".partial")
            verified = verifiedFile
            OutputTransaction(staging, verifiedFile, root)
        } catch (error: Throwable) {
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
    ) : DexCompilerOutputTransaction {
        private val descriptorClaimed = AtomicBoolean(false)
        private var state = State.OPEN

        override fun claimWriteDescriptor(): DexCompilerOwnedDescriptor = synchronized(this) {
            check(state == State.OPEN && descriptorClaimed.compareAndSet(false, true)) {
                "Output descriptor may be claimed exactly once"
            }
            AndroidDexCompilerDescriptor(
                ParcelFileDescriptor.open(
                    staging,
                    ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE,
                ),
            )
        }

        override fun readBytes(maxBytes: Long): ByteArray = synchronized(this) {
            check(state == State.OPEN && descriptorClaimed.get())
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
                staging.delete()
                verified.delete()
                state = State.ROLLED_BACK
            }
        }

        override fun close() = synchronized(this) {
            staging.delete()
            verified.delete()
            state = State.CLOSED
        }

        private enum class State {
            OPEN,
            PUBLISHED,
            ROLLED_BACK,
            CLOSED,
        }
    }

    private companion object {
        const val DIRECTORY_NAME = "dex-compiler-experimental"
        const val READ_ONLY_MODE = 0x100
        const val OWNER_READ_WRITE_MODE = 0x180
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
