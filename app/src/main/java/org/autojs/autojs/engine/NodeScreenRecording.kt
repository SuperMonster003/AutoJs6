package org.autojs.autojs.engine

import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.SystemClock
import org.autojs.autojs.core.image.capture.ScreenCapturer
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.util.UUID

/** A single silent MP4 recording. Only an explicit successful stop publishes the output file. */
internal class NodeScreenRecording(
    private val capturer: ScreenCapturer,
    private val output: File,
    options: JSONObject,
    defaultWidth: Int,
    defaultHeight: Int,
) : AutoCloseable {
    private val id = "recording-${UUID.randomUUID()}"
    private val width = integer(options, "width", defaultWidth, 2..8192)
    private val height = integer(options, "height", defaultHeight, 2..8192)
    private val frameRate = integer(options, "frameRate", 30, 1..120)
    private val bitRate = integer(options, "bitRate", 8_000_000, 1..Int.MAX_VALUE)
    private var recorder: MediaRecorder? = null
    private var temporary: File? = null
    private var startedAt = 0L
    @Volatile private var recordingError: String? = null

    init {
        require(options.keys().asSequence().all { it in setOf("width", "height", "frameRate", "bitRate") }) { "Recording options are width, height, frameRate and bitRate; audio is not supported." }
        require(options.has("width") == options.has("height")) { "Recording width and height must be specified together." }
        require(width % 2 == 0 && height % 2 == 0) { "H.264 recording dimensions must be even." }
        require(output.extension.equals("mp4", true)) { "Screen recording output must be an .mp4 file." }
        require(!output.exists()) { "Screen recording output already exists: ${output.path}" }
    }

    @Suppress("DEPRECATION")
    fun start(): JSONObject {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        }
        require(MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format) != null) { "No H.264 encoder supports the requested recording size, frame rate and bit rate." }
        val parent = requireNotNull(output.parentFile) { "Recording output requires a parent directory." }
        require(parent.isDirectory || parent.mkdirs()) { "Cannot create recording output directory." }
        try {
            val temp = File.createTempFile(".autojs6-recording-", ".partial", parent).also { temporary = it }
            val media = MediaRecorder().also { recorder = it }
            media.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            media.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            media.setVideoSize(width, height)
            media.setVideoFrameRate(frameRate)
            media.setVideoEncodingBitRate(bitRate)
            media.setOutputFile(temp.absolutePath)
            media.setOnErrorListener { _, what, extra -> recordingError = "MediaRecorder error $what/$extra" }
            media.prepare()
            capturer.setRecordingSurface(media.surface, width, height)
            media.start()
            startedAt = SystemClock.elapsedRealtime()
            return status()
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    fun status(): JSONObject = JSONObject().put("state", if (recordingError == null) "recording" else "failed")
        .put("id", id).put("path", output.absolutePath).put("width", width).put("height", height)
        .put("frameRate", frameRate).put("bitRate", bitRate)
        .put("elapsedMs", (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0))
        .put("error", recordingError ?: JSONObject.NULL)

    fun finish(): JSONObject {
        try {
            check(recordingError == null) { recordingError.orEmpty() }
            capturer.detachRecordingSurface()
            checkNotNull(recorder).stop() // Fails for recordings with no encoded frames; never publish them.
            recorder?.release()
            recorder = null
            capturer.restoreCaptureSurface()
            val temp = checkNotNull(temporary)
            val duration = MediaMetadataRetriever().let { metadata ->
                try {
                    metadata.setDataSource(temp.absolutePath)
                    check(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") { "Recording has no video track." }
                    metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                } finally { metadata.release() }
            }
            check(duration > 0 && temp.length() > 0) { "Recording contains no playable frames." }
            // No REPLACE_EXISTING: preserve files created by another caller while recording.
            Files.move(temp.toPath(), output.toPath())
            temporary = null
            return JSONObject().put("id", id).put("path", output.absolutePath).put("size", output.length())
                .put("durationMs", duration).put("width", width).put("height", height)
                .put("mimeType", "video/mp4").put("videoCodec", "h264").put("audio", false)
        } finally { close() }
    }

    override fun close() {
        runCatching { capturer.restoreCaptureSurface() }
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        temporary?.delete()
        temporary = null
    }

    private fun integer(options: JSONObject, name: String, default: Int, range: IntRange): Int {
        if (!options.has(name)) return default
        val value = (options.opt(name) as? Number)?.toDouble()
        require(value != null && value.isFinite() && value % 1 == 0.0 && value >= range.first && value <= range.last) { "$name must be an integer in $range." }
        return value.toInt()
    }
}
