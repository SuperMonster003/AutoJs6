package org.autojs.autojs.core.plugin.python

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import org.autojs.plugin.python.runtime.api.PythonExecutionResult
import org.autojs.plugin.python.runtime.api.PythonOutputArtifact
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import org.autojs.plugin.python.runtime.api.PythonSha256
import java.io.InputStream
import java.security.MessageDigest
import java.util.ArrayList
import java.util.Collections

/** Host-owned terminal result. stdout/stderr are deliberately absent from this value surface. */
class PythonRuntimeExecutionResult internal constructor(
    val terminal: PythonExecutionResult,
    outputArtifacts: Collection<PythonRuntimeOutputArtifact>,
) {
    val structuredJson: String?
        get() = terminal.structuredJson

    val outputArtifacts: List<PythonRuntimeOutputArtifact> =
        Collections.unmodifiableList(ArrayList(outputArtifacts))
}

/** One bounded artifact copied from a verified result descriptor into Host-owned memory. */
class PythonRuntimeOutputArtifact internal constructor(
    val path: String,
    val sha256: PythonSha256,
    bytes: ByteArray,
) {
    private val value = bytes.copyOf()

    val sizeBytes: Long
        get() = value.size.toLong()

    fun toByteArray(): ByteArray = value.copyOf()
}

internal object PythonRuntimeResultReceiver {
    fun receive(
        result: PythonExecutionResult,
        descriptors: Array<ParcelFileDescriptor>,
    ): PythonRuntimeExecutionResult {
        require(descriptors.size == result.outputArtifacts.size) {
            "Python Runtime result descriptor count changed after policy validation"
        }
        val artifacts = result.outputArtifacts.map { artifact ->
            materialize(artifact, descriptors[artifact.descriptorIndex])
        }
        return PythonRuntimeExecutionResult(result, artifacts)
    }

    private fun materialize(
        artifact: PythonOutputArtifact,
        descriptor: ParcelFileDescriptor,
    ): PythonRuntimeOutputArtifact {
        require(artifact.declaredLengthBytes <= PythonRuntimeContract.MAX_OUTPUT_ARTIFACT_BYTES) {
            "Python Runtime output artifact exceeds the protocol limit"
        }
        require(artifact.declaredLengthBytes <= Int.MAX_VALUE.toLong()) {
            "Python Runtime output artifact cannot be materialized safely"
        }
        val stat = Os.fstat(descriptor.fileDescriptor)
        require(OsConstants.S_ISREG(stat.st_mode) && stat.st_size == artifact.declaredLengthBytes) {
            "Python Runtime output artifact descriptor is not an exact regular file"
        }
        var bytes: ByteArray? = null
        try {
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                bytes = PythonRuntimeResultPayloadVerifier.readExactly(artifact, input)
                if (descriptor.canDetectErrors()) descriptor.checkError()
            }
            return PythonRuntimeOutputArtifact(artifact.path, artifact.sha256, checkNotNull(bytes))
        } catch (error: Throwable) {
            bytes?.fill(0)
            throw error
        }
    }
}

internal object PythonRuntimeResultPayloadVerifier {
    fun readExactly(artifact: PythonOutputArtifact, input: InputStream): ByteArray {
        require(artifact.declaredLengthBytes in 0L..PythonRuntimeContract.MAX_OUTPUT_ARTIFACT_BYTES)
        require(artifact.declaredLengthBytes <= Int.MAX_VALUE.toLong())
        val bytes = ByteArray(artifact.declaredLengthBytes.toInt())
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            var offset = 0
            while (offset < bytes.size) {
                val count = input.read(bytes, offset, bytes.size - offset)
                require(count >= 0) {
                    "Python Runtime output artifact ended before its declared length"
                }
                if (count == 0) continue
                digest.update(bytes, offset, count)
                offset += count
            }
            require(input.read() == -1) {
                "Python Runtime output artifact exceeds its declared length"
            }
            require(digest.digest().contentEquals(artifact.sha256.toByteArray())) {
                "Python Runtime output artifact digest does not match"
            }
            return bytes
        } catch (error: Throwable) {
            bytes.fill(0)
            throw error
        }
    }
}
