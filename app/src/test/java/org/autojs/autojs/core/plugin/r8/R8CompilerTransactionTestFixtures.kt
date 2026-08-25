package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8ArtifactBundleCodec
import org.autojs.plugin.r8compiler.api.R8ArtifactIdentity
import org.autojs.plugin.r8compiler.api.R8ArtifactRole
import org.autojs.plugin.r8compiler.api.R8ArtifactSource
import org.autojs.plugin.r8compiler.api.R8Cancellation
import org.autojs.plugin.r8compiler.api.R8CancellationReason
import org.autojs.plugin.r8compiler.api.R8CompileRequest
import org.autojs.plugin.r8compiler.api.R8CompilerCapabilities
import org.autojs.plugin.r8compiler.api.R8CompilerCodec
import org.autojs.plugin.r8compiler.api.R8CompilerFamily
import org.autojs.plugin.r8compiler.api.R8Error
import org.autojs.plugin.r8compiler.api.R8ErrorCode
import org.autojs.plugin.r8compiler.api.R8FailurePhase
import org.autojs.plugin.r8compiler.api.R8Progress
import org.autojs.plugin.r8compiler.api.R8ProgressStage
import org.autojs.plugin.r8compiler.api.R8Result
import org.autojs.plugin.r8compiler.api.R8RetraceMetadata
import org.autojs.plugin.r8compiler.api.R8Sha256
import org.autojs.plugin.r8compiler.api.R8Started
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.Adler32
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object R8CompilerTransactionTestFixtures {
    fun spec(timeoutMillis: Long = 1_000L, terminalGraceMillis: Long = 0L) =
        R8CompilerHostCompileSpec(
            inputs = R8CompilerHostInputs(
                program = source("program-jar".toByteArray()),
                keepRules = listOf(source("-keep class example.Program { *; }\n".toByteArray())),
            ),
            minApi = 24,
            timeoutMillis = timeoutMillis,
            terminalGraceMillis = terminalGraceMillis,
        )

    fun negotiation(capabilities: R8CompilerCapabilities = R8CompilerHostFixtures.capabilities()) =
        R8CompilerNegotiation(
            R8CompilerHostFixtures.identity(),
            R8CompilerHostFixtures.info(),
            capabilities,
        )

    fun source(bytes: ByteArray) = R8CompilerHostSource { ByteArrayInputStream(bytes) }

    fun descriptorFactory(failOnWriteClose: Boolean = false) = object : R8CompilerDescriptorFactory {
        override fun openReadOnly(file: File): R8CompilerOwnedDescriptor = TestDescriptor(file, false)
        override fun openWriteOnly(file: File): R8CompilerOwnedDescriptor =
            TestDescriptor(file, true, failOnWriteClose)
    }

    fun requestIds(): () -> org.autojs.plugin.r8compiler.api.R8RequestId {
        val counter = AtomicInteger()
        return {
            org.autojs.plugin.r8compiler.api.R8RequestId.fromUuid(
                UUID(0x123e4567e89b12d3L, counter.incrementAndGet().toLong()),
            )
        }
    }

    fun validDexZip(): ByteArray = dexZip(validDex())

    fun invalidDexZip(): ByteArray = dexZip(ByteArray(112))

    fun validDex(): ByteArray {
        val bytes = ByteArray(112)
        "dex\n035\u0000".toByteArray(Charsets.US_ASCII).copyInto(bytes, 0)
        val little = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        little.putInt(32, bytes.size)
        little.putInt(36, 112)
        little.putInt(40, 0x12345678)
        val signature = MessageDigest.getInstance("SHA-1").digest(bytes.copyOfRange(32, bytes.size))
        signature.copyInto(bytes, 12)
        val checksum = Adler32().apply { update(bytes, 12, bytes.size - 12) }.value
        little.putInt(8, checksum.toInt())
        return bytes
    }

    fun dexZip(dex: ByteArray, entryName: String = "classes.dex"): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            val crc = CRC32().apply { update(dex) }
            val entry = ZipEntry(entryName).apply {
                method = ZipEntry.STORED
                size = dex.size.toLong()
                compressedSize = dex.size.toLong()
                this.crc = crc.value
            }
            zip.putNextEntry(entry)
            zip.write(dex)
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    internal class TestDescriptor(
        val file: File,
        val writeOnly: Boolean,
        private val failOnClose: Boolean = false,
    ) : R8CompilerOwnedDescriptor {
        var closed = false
        override fun close() {
            if (closed) return
            closed = true
            if (failOnClose) error("synthetic descriptor close failure")
        }
    }

    internal enum class ConnectionBehavior {
        SUCCESS,
        REMOTE_ERROR,
        CANCELLED,
        MALFORMED_COMPLETION,
        CORRUPT_BUNDLE,
        INVALID_DEX,
        DUPLICATE_TERMINAL,
        SILENT,
        THROW_ON_OPEN,
        THROW_ON_SESSION_CLOSE,
    }

    internal class FakeConnection(
        private val capabilities: R8CompilerCapabilities = R8CompilerHostFixtures.capabilities(),
        var behavior: ConnectionBehavior = ConnectionBehavior.SUCCESS,
        private val onOpen: (() -> Unit)? = null,
    ) : R8CompilerRemoteConnection {
        var openCount = 0
        var cancelCount = 0
        var closeCount = 0
        private val cancelled = AtomicBoolean(false)
        private val closed = AtomicBoolean(false)

        override fun getCompilerInfo(): ByteArray = R8CompilerCodec.encodeInfo(R8CompilerHostFixtures.info())

        override fun getCapabilities(): ByteArray = R8CompilerCodec.encodeCapabilities(capabilities)

        override fun openSession(
            encodedRequest: ByteArray,
            inputBundleDescriptor: R8CompilerOwnedDescriptor,
            outputBundleDescriptor: R8CompilerOwnedDescriptor,
            callback: R8CompilerRemoteCallback,
        ): R8CompilerRemoteSession {
            openCount++
            if (behavior == ConnectionBehavior.THROW_ON_OPEN) error("synthetic open failure")
            val input = inputBundleDescriptor as TestDescriptor
            val output = outputBundleDescriptor as TestDescriptor
            check(!input.writeOnly && output.writeOnly)
            check(input.file.isFile)
            val request = R8CompilerCodec.decodeRequest(encodedRequest)
            onOpen?.invoke()
            if (behavior != ConnectionBehavior.SILENT) {
                callback.onStarted(
                    R8CompilerCodec.encodeStarted(
                        R8Started(
                            request.requestId,
                            0,
                            request.protocolVersion,
                            R8CompilerFamily.R8,
                            capabilities.compilerVersion,
                            capabilities.capabilityFingerprint,
                            capabilities.runtimeLibraryFingerprint,
                            request.profile,
                            0,
                        ),
                    ),
                )
                callback.onProgress(
                    R8CompilerCodec.encodeProgress(
                        R8Progress(request.requestId, 1, R8ProgressStage.COMPILING),
                    ),
                )
            }
            when (behavior) {
                ConnectionBehavior.SUCCESS,
                ConnectionBehavior.THROW_ON_SESSION_CLOSE,
                -> complete(request, output.file, callback, validDexZip())
                ConnectionBehavior.REMOTE_ERROR -> callback.onFailed(
                    R8CompilerCodec.encodeError(
                        R8Error(
                            request.requestId,
                            R8ErrorCode.COMPILATION_FAILED,
                            R8FailurePhase.COMPILATION,
                            "synthetic R8 failure",
                            1,
                        ),
                    ),
                )
                ConnectionBehavior.CANCELLED -> callback.onCancelled(
                    R8CompilerCodec.encodeCancellation(
                        R8Cancellation(
                            request.requestId,
                            R8CancellationReason.REQUESTED,
                            R8FailurePhase.CLEANUP,
                            1,
                        ),
                    ),
                )
                ConnectionBehavior.MALFORMED_COMPLETION -> callback.onCompleted(byteArrayOf(1, 2, 3))
                ConnectionBehavior.CORRUPT_BUNDLE -> {
                    complete(request, output.file, callback, validDexZip(), beforeCallback = { file ->
                        RandomAccessFile(file, "rw").use { random ->
                            random.seek(file.length() - 1)
                            val original = random.read()
                            random.seek(file.length() - 1)
                            random.write(original.xor(0xff))
                        }
                    })
                }
                ConnectionBehavior.INVALID_DEX -> complete(request, output.file, callback, invalidDexZip())
                ConnectionBehavior.DUPLICATE_TERMINAL -> {
                    complete(request, output.file, callback, validDexZip())
                    callback.onFailed(
                        R8CompilerCodec.encodeError(
                            R8Error(
                                request.requestId,
                                R8ErrorCode.INTERNAL,
                                R8FailurePhase.CLEANUP,
                                "duplicate",
                                2,
                            ),
                        ),
                    )
                }
                ConnectionBehavior.SILENT,
                ConnectionBehavior.THROW_ON_OPEN,
                -> Unit
            }
            return object : R8CompilerRemoteSession {
                override fun cancel() {
                    if (cancelled.compareAndSet(false, true)) cancelCount++
                }

                override fun close() {
                    if (closed.compareAndSet(false, true)) {
                        closeCount++
                        if (behavior == ConnectionBehavior.THROW_ON_SESSION_CLOSE) {
                            error("synthetic session close failure")
                        }
                    }
                }
            }
        }

        private fun complete(
            request: R8CompileRequest,
            outputFile: File,
            callback: R8CompilerRemoteCallback,
            dexZip: ByteArray,
            beforeCallback: ((File) -> Unit)? = null,
        ) {
            val mapping = "# compiler: R8\nexample.Program -> a:\n".toByteArray()
            val seeds = ByteArray(0)
            val usage = ByteArray(0)
            val retrace = R8CompilerCodec.encodeRetraceMetadata(
                R8RetraceMetadata(
                    mappingSha256 = sha(mapping),
                    formatId = org.autojs.plugin.r8compiler.api.R8CompilerContract.MAPPING_FORMAT_ID,
                    formatVersion = "2.2",
                    compilerVersion = capabilities.compilerVersion,
                    capabilityFingerprint = capabilities.capabilityFingerprint,
                    runtimeLibraryFingerprint = capabilities.runtimeLibraryFingerprint,
                    inputSetFingerprint = request.inputSetFingerprint,
                    minApi = request.minApi,
                    profile = request.profile,
                ),
            )
            val payloads = linkedMapOf(
                R8ArtifactRole.DEX_ZIP to dexZip,
                R8ArtifactRole.MAPPING_TEXT to mapping,
                R8ArtifactRole.SEEDS_TEXT to seeds,
                R8ArtifactRole.USAGE_TEXT to usage,
                R8ArtifactRole.RETRACE_METADATA to retrace,
            )
            val sources = payloads.map { (role, bytes) ->
                R8ArtifactSource(
                    R8ArtifactIdentity(role, 0, bytes.size.toLong(), sha(bytes)),
                ) { ByteArrayInputStream(bytes) }
            }
            val summary = FileOutputStream(outputFile, false).use { output ->
                R8ArtifactBundleCodec.write(output, sources, request, capabilities).also { output.fd.sync() }
            }
            val result = R8Result(
                requestId = request.requestId,
                compilerFamily = request.compilerFamily,
                compilerVersion = capabilities.compilerVersion,
                capabilityFingerprint = capabilities.capabilityFingerprint,
                runtimeLibraryFingerprint = capabilities.runtimeLibraryFingerprint,
                inputSetFingerprint = request.inputSetFingerprint,
                profile = request.profile,
                minApi = request.minApi,
                outputLayout = request.outputLayout,
                outputBundleSizeBytes = summary.sizeBytes,
                outputBundleSha256 = summary.contentSha256,
                artifactIdentities = summary.identities,
                determinismClaim = capabilities.determinismClaim,
                elapsedMillis = 2,
            )
            beforeCallback?.invoke(outputFile)
            callback.onCompleted(R8CompilerCodec.encodeResult(result))
        }
    }

    internal class FailingStore(
        private val delegate: R8CompilerArtifactStore,
        private val failLookup: Boolean = false,
        private val failPublish: Boolean = false,
    ) : R8CompilerArtifactStore {
        override fun lookup(
            cacheKeySha256: String,
            request: R8CompileRequest,
            capabilities: R8CompilerCapabilities,
        ): R8CompilerStoredArtifact? {
            if (failLookup) error("synthetic lookup failure")
            return delegate.lookup(cacheKeySha256, request, capabilities)
        }

        override fun publishVerified(
            cacheKeySha256: String,
            verified: R8CompilerVerifiedArtifacts,
            request: R8CompileRequest,
            capabilities: R8CompilerCapabilities,
        ): R8CompilerStoredArtifact {
            if (failPublish) error("synthetic publication failure")
            return delegate.publishVerified(cacheKeySha256, verified, request, capabilities)
        }
    }

    private fun sha(bytes: ByteArray): R8Sha256 = R8Sha256.fromBytes(
        MessageDigest.getInstance("SHA-256").digest(bytes),
    )
}
