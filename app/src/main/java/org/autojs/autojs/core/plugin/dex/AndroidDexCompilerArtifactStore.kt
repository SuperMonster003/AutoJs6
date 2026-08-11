package org.autojs.autojs.core.plugin.dex

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import org.autojs.plugin.dexcompiler.api.DexCompilerContract
import org.autojs.plugin.dexcompiler.api.DexCompilerInputBundleCodec
import org.autojs.plugin.dexcompiler.api.DexCompilerInputBundleSource
import org.autojs.plugin.dexcompiler.api.DexCompilerInputIdentity
import org.autojs.plugin.dexcompiler.api.DexCompilerInputRole
import org.autojs.plugin.dexcompiler.api.DexCompilerInputSetFingerprint
import org.autojs.plugin.dexcompiler.api.DexSha256
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.ArrayList
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Host-private snapshot and verified-copy cache publication used only by the experimental harness.
 *
 * Construct this once per process when the explicitly selected runtime backend is first needed,
 * before the harness can start its single live session. The persistent index recovers committed
 * generations and removes transaction-shaped or unindexed crash leftovers. A remote-writable
 * staging inode is never used as a published cache inode.
 * [drainExecutor] must be a dedicated bounded asynchronous executor; inline execution would block
 * before the write descriptor can be dispatched.
 */
internal class AndroidDexCompilerArtifactStore(
    context: Context,
    private val drainExecutor: Executor,
) {

    private val ownerToken = Any()

    private val root = File(context.noBackupFilesDir, DIRECTORY_NAME).apply {
        if (!exists() && !mkdirs()) throw IllegalStateException("Unable to create DEX compiler transaction directory")
        require(isDirectory)
    }.canonicalFile.also { directory ->
        directory.listFiles().orEmpty()
            .filter {
                it.isFile && (DexCompilerArtifactRecoveryPolicy.isTransientArtifact(it.name) ||
                    UNINDEXED_PUBLICATION.matches(it.name))
            }
            .forEach(File::delete)
    }
    private val persistentCache = DexCompilerPersistentCache(
        rootDirectory = root,
        fileOps = AndroidPersistentCacheFileOps,
    )

    /**
     * Looks up an exact semantic key derived after the authenticated provider handshake.
     * Arbitrary keys are rejected and are never interpreted as paths.
     */
    fun lookupVerified(cacheKeySha256: String): DexCompilerVerifiedArtifact? =
        persistentCache.lookup(cacheKeySha256)?.toVerifiedArtifact()

    fun snapshot(
        input: InputStream,
        maximumBytes: Long,
        isCancelled: () -> Boolean = { false },
    ): DexCompilerInputSnapshot = InputSnapshot(
        copySnapshot(input, maximumBytes, "Program", isCancelled),
    )

    /**
     * Freezes one program and a non-empty ordered compile-time classpath, then builds the V1.1
     * descriptor bundle exclusively from those private read-only snapshots.
     *
     * Each source factory is opened exactly once and the returned stream is closed here. No live
     * caller path is retained or reopened after its snapshot identity has been established.
     */
    fun snapshotInputSet(
        programInput: () -> InputStream,
        classpathInputs: Collection<() -> InputStream>,
        isCancelled: () -> Boolean = { false },
    ): DexCompilerInputSnapshot {
        val orderedClasspath = classpathInputs.toList()
        require(orderedClasspath.size in 1..DexCompilerContract.MAX_CLASSPATH_JARS) {
            "Ordered classpath count is outside the protocol limit"
        }
        val frozen = ArrayList<SnapshotState>(orderedClasspath.size + 1)
        var bundle: SnapshotState? = null
        try {
            if (isCancelled()) throw DexCompilerStreamCancelledException()
            val program = openAndCopySnapshot(
                source = programInput,
                maximumBytes = DexCompilerContract.MAX_COMPRESSED_PROGRAM_BYTES,
                label = "Program",
                isCancelled = isCancelled,
            ).also { frozen += it }

            var classpathBytes = 0L
            orderedClasspath.forEachIndexed { index, source ->
                if (isCancelled()) throw DexCompilerStreamCancelledException()
                val state = openAndCopySnapshot(
                    source = source,
                    maximumBytes = DexCompilerContract.MAX_COMPRESSED_CLASSPATH_JAR_BYTES,
                    label = "Classpath JAR $index",
                    isCancelled = isCancelled,
                )
                frozen += state
                classpathBytes = Math.addExact(classpathBytes, state.sizeBytes)
                require(classpathBytes <= DexCompilerContract.MAX_TOTAL_COMPRESSED_CLASSPATH_BYTES) {
                    "Ordered classpath exceeds the aggregate protocol limit"
                }
            }

            val identities = frozen.mapIndexed { index, state ->
                DexCompilerInputIdentity(
                    role = if (index == 0) DexCompilerInputRole.PROGRAM else DexCompilerInputRole.CLASSPATH,
                    ordinal = if (index == 0) 0 else index - 1,
                    sizeBytes = state.sizeBytes,
                    contentSha256 = state.sha256.toDexSha256(),
                )
            }
            val bundleFile = newPrivateFile("input", ".bundle")
            try {
                val summary = FileOutputStream(bundleFile).use { output ->
                    if (isCancelled()) throw DexCompilerStreamCancelledException()
                    val value = DexCompilerInputBundleCodec.write(
                        output = output,
                        sources = identities.mapIndexed { index, identity ->
                            DexCompilerInputBundleSource(identity) {
                                CancellationCheckingInputStream(
                                    FileInputStream(frozen[index].file),
                                    isCancelled,
                                )
                            }
                        },
                    )
                    if (isCancelled()) throw DexCompilerStreamCancelledException()
                    output.fd.sync()
                    if (isCancelled()) throw DexCompilerStreamCancelledException()
                    value
                }
                check(summary.identities == identities) { "Canonical input bundle identities changed" }
                check(bundleFile.length() == summary.sizeBytes) { "Canonical input bundle size changed" }
                Os.chmod(bundleFile.absolutePath, READ_ONLY_MODE)
                bundle = SnapshotState(
                    file = bundleFile,
                    sizeBytes = summary.sizeBytes,
                    sha256 = summary.contentSha256.toHexString(),
                )
            } catch (error: Throwable) {
                bundleFile.delete()
                throw error
            }
            return InputSetSnapshot(
                program = program,
                classpath = frozen.drop(1),
                bundle = checkNotNull(bundle),
                identities = identities,
                inputSetSha256 = DexCompilerInputSetFingerprint.compute(identities).toHexString(),
            )
        } catch (error: Throwable) {
            bundle?.release()
            frozen.asReversed().forEach(SnapshotState::release)
            throw error
        }
    }

    private fun copySnapshot(
        input: InputStream,
        maximumBytes: Long,
        label: String,
        isCancelled: () -> Boolean,
    ): SnapshotState {
        require(maximumBytes > 0L)
        val file = newPrivateFile("input", ".jar")
        try {
            val summary = FileOutputStream(file).use { output ->
                val copied = DexCompilerBoundedStreamTransfer.copy(
                    input = input,
                    output = output,
                    maximumBytes = maximumBytes,
                    isCancelled = isCancelled,
                )
                output.fd.sync()
                copied
            }
            if (summary.sizeBytes <= 0L) throw IllegalArgumentException("$label snapshot is empty")
            Os.chmod(file.absolutePath, READ_ONLY_MODE)
            return SnapshotState(file, summary.sizeBytes, summary.sha256)
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    private fun openAndCopySnapshot(
        source: () -> InputStream,
        maximumBytes: Long,
        label: String,
        isCancelled: () -> Boolean,
    ): SnapshotState {
        var copied: SnapshotState? = null
        return try {
            source().use { input ->
                copySnapshot(input, maximumBytes, label, isCancelled).also { copied = it }
            }
        } catch (error: Throwable) {
            // `use` may fail while closing the source after copySnapshot has already committed its
            // private read-only file. Until this helper returns, that state has no outer owner.
            copied?.release()
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
                owner = this,
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
        private val state: SnapshotState,
    ) : DexCompilerInputSnapshot {
        private val descriptorClaimed = AtomicBoolean(false)
        private val closed = AtomicBoolean(false)

        override val sizeBytes: Long
            get() = state.sizeBytes

        override val sha256: String
            get() = state.sha256

        override fun claimReadDescriptor(): DexCompilerOwnedDescriptor {
            check(!closed.get() && descriptorClaimed.compareAndSet(false, true)) {
                "Program descriptor may be claimed exactly once"
            }
            return AndroidDexCompilerDescriptor(
                ParcelFileDescriptor.open(state.file, ParcelFileDescriptor.MODE_READ_ONLY),
            )
        }

        override fun retainForLocalFallback(): DexCompilerRetainedInput {
            check(!closed.get()) { "Program snapshot is already closed" }
            state.retain()
            return RetainedInput(state)
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) state.release()
        }
    }

    private class InputSetSnapshot(
        private val program: SnapshotState,
        private val classpath: List<SnapshotState>,
        private val bundle: SnapshotState,
        identities: Collection<DexCompilerInputIdentity>,
        private val inputSetSha256: String,
    ) : DexCompilerInputSnapshot {
        private val descriptorClaimed = AtomicBoolean(false)
        private val closed = AtomicBoolean(false)

        override val sizeBytes: Long
            get() = program.sizeBytes

        override val sha256: String
            get() = program.sha256

        override val wireSizeBytes: Long
            get() = bundle.sizeBytes

        override val wireSha256: String
            get() = bundle.sha256

        override val inputIdentities: List<DexCompilerInputIdentity> =
            Collections.unmodifiableList(ArrayList(identities))

        override fun claimReadDescriptor(): DexCompilerOwnedDescriptor {
            check(!closed.get() && descriptorClaimed.compareAndSet(false, true)) {
                "Input bundle descriptor may be claimed exactly once"
            }
            return AndroidDexCompilerDescriptor(
                ParcelFileDescriptor.open(bundle.file, ParcelFileDescriptor.MODE_READ_ONLY),
            )
        }

        override fun retainForLocalFallback(): DexCompilerRetainedInput {
            check(!closed.get()) { "Input-set snapshot is already closed" }
            val states = listOf(program) + classpath
            retainAll(states)
            return RetainedInput(program, classpath, inputSetSha256)
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                bundle.release()
                classpath.asReversed().forEach(SnapshotState::release)
                program.release()
            }
        }
    }

    private class RetainedInput(
        private val state: SnapshotState,
        private val classpath: List<SnapshotState> = emptyList(),
        override val inputSetSha256: String? = null,
    ) : DexCompilerRetainedInput {
        private val closed = AtomicBoolean(false)

        override val sizeBytes: Long
            get() = state.sizeBytes

        override val sha256: String
            get() = state.sha256

        override fun resolve(): File? = if (closed.get()) null else state.resolve()

        override fun resolveInputSet(): DexCompilerResolvedInputSet? {
            if (closed.get()) return null
            val program = state.resolve() ?: return null
            val resolvedClasspath = ArrayList<File>(classpath.size)
            classpath.forEach { item -> resolvedClasspath += item.resolve() ?: return null }
            return DexCompilerResolvedInputSet(program, resolvedClasspath)
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                classpath.asReversed().forEach(SnapshotState::release)
                state.release()
            }
        }
    }

    private class SnapshotState(
        val file: File,
        val sizeBytes: Long,
        val sha256: String,
    ) {
        private val references = AtomicInteger(1)

        fun retain() {
            while (true) {
                val current = references.get()
                check(current > 0) { "Program snapshot is already released" }
                check(current < Int.MAX_VALUE) { "Program snapshot reference count overflow" }
                if (references.compareAndSet(current, current + 1)) return
            }
        }

        fun resolve(): File? {
            if (references.get() <= 0 || !file.isFile || file.length() != sizeBytes) return null
            return file.takeIf { sha256File(it) == sha256 }
        }

        fun release() {
            val remaining = references.decrementAndGet()
            check(remaining >= 0) { "Program snapshot was released more than once" }
            if (remaining == 0) file.delete()
        }
    }

    private class CancellationCheckingInputStream(
        input: InputStream,
        private val isCancelled: () -> Boolean,
    ) : FilterInputStream(input) {
        override fun read(): Int {
            throwIfCancelled()
            return super.read()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            throwIfCancelled()
            return super.read(buffer, offset, length)
        }

        private fun throwIfCancelled() {
            if (isCancelled()) throw DexCompilerStreamCancelledException()
        }
    }

    private class OutputTransaction(
        private val staging: File,
        private val verified: File,
        private val owner: AndroidDexCompilerArtifactStore,
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

        override fun publishVerified(
            cacheKeySha256: String,
            bytes: ByteArray,
        ): DexCompilerVerifiedArtifact = synchronized(this) {
            check(state == State.OPEN && descriptorClaimed.get())
            require(SHA256.matches(cacheKeySha256)) { "Cache key must be lowercase SHA-256" }
            require(bytes.isNotEmpty())
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
            val artifact = owner.publishVerifiedCopy(
                cacheKeySha256 = cacheKeySha256,
                outputSha256 = expectedDigest.toHex(),
                sizeBytes = bytes.size.toLong(),
                verified = verified,
            )
            state = State.PUBLISHED
            staging.delete()
            artifact
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

    private fun publishVerifiedCopy(
        cacheKeySha256: String,
        outputSha256: String,
        sizeBytes: Long,
        verified: File,
    ): DexCompilerVerifiedArtifact {
        val entry = persistentCache.publishVerified(
            cacheKeySha256 = cacheKeySha256,
            outputSha256 = outputSha256,
            sizeBytes = sizeBytes,
            verified = verified,
        )
        return entry.toVerifiedArtifact()
    }

    private fun openRead(
        token: Any,
        entry: DexCompilerPersistentCache.Entry,
    ): InputStream? = if (token === ownerToken) persistentCache.openRead(entry) else null

    private fun invalidate(
        token: Any,
        entry: DexCompilerPersistentCache.Entry,
    ): Boolean = token === ownerToken && persistentCache.invalidate(entry)

    private fun DexCompilerPersistentCache.Entry.toVerifiedArtifact(): DexCompilerVerifiedArtifact =
        VerifiedArtifact(
            owner = this@AndroidDexCompilerArtifactStore,
            ownerToken = ownerToken,
            entry = this,
        )

    private class VerifiedArtifact(
        private val owner: AndroidDexCompilerArtifactStore,
        private val ownerToken: Any,
        private val entry: DexCompilerPersistentCache.Entry,
    ) : DexCompilerVerifiedArtifact {
        override val cacheKeySha256: String
            get() = entry.cacheKeySha256

        override val outputSha256: String
            get() = entry.outputSha256

        override val sizeBytes: Long
            get() = entry.sizeBytes

        override fun openRead(): InputStream? = owner.openRead(
            token = ownerToken,
            entry = entry,
        )

        override fun invalidate(): Boolean = owner.invalidate(ownerToken, entry)

        override fun toString(): String =
            "DexCompilerVerifiedArtifact(cacheKeySha256=$cacheKeySha256, outputSha256=$outputSha256, " +
                "sizeBytes=$sizeBytes)"
    }

    private companion object {
        const val DIRECTORY_NAME = "dex-compiler-experimental"
        const val READ_ONLY_MODE = 0x100
        const val OWNER_READ_WRITE_MODE = 0x180
        const val PIPE_REJECTION_MESSAGE = "Host rejected DEX compiler output"
        val SHA256 = Regex("[0-9a-f]{64}")
        val UNINDEXED_PUBLICATION = Regex("[0-9a-f]{64}\\.dex\\.zip")

        fun retainAll(states: List<SnapshotState>) {
            val retained = ArrayList<SnapshotState>(states.size)
            try {
                states.forEach { state ->
                    state.retain()
                    retained += state
                }
            } catch (error: Throwable) {
                retained.asReversed().forEach(SnapshotState::release)
                throw error
            }
        }
    }
}

private object AndroidPersistentCacheFileOps : DexCompilerPersistentCache.FileOps {
    override fun makeOwnerReadWrite(file: File) {
        Os.chmod(file.absolutePath, 0x180)
    }

    override fun makeOwnerReadOnly(file: File) {
        Os.chmod(file.absolutePath, 0x100)
    }

    override fun isOwnerReadOnlyRegularFile(file: File): Boolean = try {
        val stat = Os.lstat(file.absolutePath)
        OsConstants.S_ISREG(stat.st_mode) && (stat.st_mode and PERMISSION_MASK) == OWNER_READ_ONLY
    } catch (_: Exception) {
        false
    }

    override fun isOwnerReadOnlyRegularFile(file: File, opened: FileInputStream): Boolean = try {
        val stat = Os.fstat(opened.fd)
        OsConstants.S_ISREG(stat.st_mode) && (stat.st_mode and PERMISSION_MASK) == OWNER_READ_ONLY
    } catch (_: Exception) {
        false
    }

    override fun atomicReplace(source: File, destination: File) {
        Os.rename(source.absolutePath, destination.absolutePath)
    }

    override fun syncDirectory(directory: File) {
        val descriptor = Os.open(directory.absolutePath, OsConstants.O_RDONLY, 0)
        try {
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }

    private const val PERMISSION_MASK = 0x1ff
    private const val OWNER_READ_ONLY = 0x100
}

private fun sha256File(file: File): String = FileInputStream(file).use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count > 0) digest.update(buffer, 0, count)
    }
    digest.digest().toHex()
}

private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun String.toDexSha256(): DexSha256 {
    require(length == DexSha256.BYTE_COUNT * 2 && all { it in '0'..'9' || it in 'a'..'f' })
    return DexSha256.fromBytes(
        ByteArray(DexSha256.BYTE_COUNT) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        },
    )
}
