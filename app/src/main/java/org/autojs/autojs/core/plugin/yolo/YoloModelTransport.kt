package org.autojs.autojs.core.plugin.yolo

import android.os.ParcelFileDescriptor
import org.autojs.plugin.yolo.api.YoloContract
import org.autojs.plugin.yolo.api.YoloModelArtifact
import org.autojs.plugin.yolo.api.YoloSha256
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

internal object YoloModelTransport {

    data class Source(
        val role: String,
        val logicalName: String,
        val file: File,
    )

    fun prepare(sources: List<Source>): Prepared {
        require(sources.isNotEmpty()) { "YOLO model artifacts are empty" }
        require(sources.size <= YoloContract.MAX_MODEL_DESCRIPTORS) {
            "YOLO model artifact count exceeds the protocol limit"
        }

        val descriptors = ArrayList<ParcelFileDescriptor>(sources.size)
        try {
            val artifacts = sources.mapIndexed { index, source ->
                require(source.file.isFile) { "YOLO model artifact is not a regular file" }
                val length = source.file.length()
                require(length in 1L..YoloContract.MAX_MODEL_ARTIFACT_BYTES) {
                    "YOLO model artifact length is invalid"
                }
                if (source.role == YoloContract.REQUIRED_MANIFEST_ROLE) {
                    require(length <= YoloContract.MAX_MANIFEST_BYTES) {
                        "YOLO model manifest exceeds the protocol limit"
                    }
                }
                val digest = sha256(source.file)
                val descriptor = ParcelFileDescriptor.open(
                    source.file,
                    ParcelFileDescriptor.MODE_READ_ONLY,
                )
                check(descriptor.statSize == length) {
                    "YOLO model artifact changed while opening its descriptor"
                }
                descriptors += descriptor
                YoloModelArtifact(
                    role = source.role,
                    logicalName = source.logicalName,
                    sizeBytes = length,
                    sha256 = digest,
                    descriptorIndex = index,
                )
            }
            return Prepared(
                artifacts = artifacts,
                descriptors = descriptors.toTypedArray(),
            )
        } catch (error: Throwable) {
            descriptors.forEach { runCatching { it.close() } }
            throw error
        }
    }

    private fun sha256(file: File): YoloSha256 {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return YoloSha256.fromBytes(digest.digest())
    }

    internal class Prepared(
        val artifacts: List<YoloModelArtifact>,
        val descriptors: Array<ParcelFileDescriptor>,
    ) : Closeable {
        override fun close() {
            descriptors.forEach { runCatching { it.close() } }
        }
    }

    private const val BUFFER_BYTES = 64 * 1024
}
