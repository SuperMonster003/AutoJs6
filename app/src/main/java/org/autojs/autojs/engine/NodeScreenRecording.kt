package org.autojs.autojs.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import android.view.Surface
import org.autojs.autojs.core.image.capture.ScreenCapturer
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/** One execution-owned MP4. Audio/video share a monotonic clock and one streaming muxer. */
internal class NodeScreenRecording(
    private val context: Context,
    private val capturer: ScreenCapturer,
    private val output: File,
    options: JSONObject,
    defaultWidth: Int,
    defaultHeight: Int,
    private val onNotificationStop: () -> Unit,
) : AutoCloseable {
    private val id = "recording-${UUID.randomUUID()}"
    private val width = integer(options, "width", defaultWidth, 2..8192)
    private val height = integer(options, "height", defaultHeight, 2..8192)
    private val frameRate = integer(options, "frameRate", 30, 1..120)
    private val bitRate = integer(options, "bitRate", 8_000_000, 1..Int.MAX_VALUE)
    private val audioSource = audioSource(options)
    private var video: MediaCodec? = null
    private var videoSurface: Surface? = null
    private var videoThread: Thread? = null
    private val videoReady = CompletableFuture<Unit>()
    private val videoDone = CompletableFuture<Unit>()
    @Volatile private var draining = false
    private var frames: ScreenRecordingFrames? = null
    private var audio: ScreenRecordingAudio? = null
    private var muxer: ScreenRecordingMuxer? = null
    private var temporary: File? = null
    private var originNs = 0L
    @Volatile private var recordingError: Throwable? = null
    private var closed = false

    init {
        require(options.keys().asSequence().all { it in setOf("width", "height", "frameRate", "bitRate", "audio") }) { "Recording options are width, height, frameRate, bitRate and audio." }
        require(options.has("width") == options.has("height")) { "Recording width and height must be specified together." }
        require(width % 2 == 0 && height % 2 == 0) { "H.264 recording dimensions must be even." }
        require(output.extension.equals("mp4", true)) { "Screen recording output must be an .mp4 file." }
        require(!output.exists()) { "Screen recording output already exists: ${output.path}" }
    }

    fun start(): JSONObject {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            if (Build.VERSION.SDK_INT >= 29) setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
        }
        val codecName = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format)
        require(codecName != null) { "No H.264 encoder supports ${width}x$height at $frameRate fps and $bitRate bit/s." }
        val parent = requireNotNull(output.parentFile) { "Recording output requires a parent directory." }
        require(parent.isDirectory || parent.mkdirs()) { "Cannot create recording output directory." }
        try {
            val temp = File.createTempFile(".autojs6-recording-", ".partial", parent).also { temporary = it }
            originNs = System.nanoTime()
            val outputMuxer = ScreenRecordingMuxer(temp, if (audioSource == "none") 1 else 2).also { muxer = it }
            val encoder = MediaCodec.createByCodecName(codecName).also { video = it }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            videoSurface = encoder.createInputSurface()
            encoder.start()
            draining = true
            videoThread = Thread({ drainVideo(encoder, outputMuxer) }, "ScreenRecording-AVC").also { it.start() }
            if (audioSource != "none") {
                audio = ScreenRecordingAudio(context, capturer, audioSource, id, originNs, outputMuxer,
                    ::recordFailure, onNotificationStop).also { it.start() }
            }
            frames = ScreenRecordingFrames(context, capturer, checkNotNull(videoSurface), width, height, frameRate,
                originNs, ::recordFailure).also { it.start() }
            await(videoReady)
            return status()
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    fun status(): JSONObject = JSONObject().put("state", if (recordingError == null) "recording" else "failed")
        .put("id", id).put("path", output.absolutePath).put("width", width).put("height", height)
        .put("frameRate", frameRate).put("bitRate", bitRate)
        .put("elapsedMs", ((System.nanoTime() - originNs) / 1_000_000).coerceAtLeast(0))
        .put("audio", audioSource != "none").put("audioSource", audioSource)
        .put("error", recordingError?.message ?: JSONObject.NULL)

    fun capture(): Bitmap {
        recordingError?.let { throw it }
        return checkNotNull(frames) { "Recording frame source is unavailable." }.snapshot()
    }

    private fun recordFailure(error: Throwable) { if (recordingError == null) recordingError = error }

    private fun drainVideo(encoder: MediaCodec, output: ScreenRecordingMuxer) {
        val info = MediaCodec.BufferInfo()
        var track = -1
        try {
            while (draining) {
                val index = encoder.dequeueOutputBuffer(info, 10_000)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = output.addTrack(encoder.outputFormat); videoReady.complete(Unit) }
                    index >= 0 -> {
                        try { if (info.size > 0) { check(track >= 0); output.write(track, checkNotNull(encoder.getOutputBuffer(index)), info, true) } }
                        finally { encoder.releaseOutputBuffer(index, false) }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            videoDone.complete(Unit)
        } catch (error: Throwable) {recordFailure(error);videoReady.completeExceptionally(error);videoDone.completeExceptionally(error)}
        finally {draining = false}
    }

    fun finish(): JSONObject {
        try {
            recordingError?.let { throw it }
            frames?.close(); frames = null
            audio?.finish(); audio = null
            checkNotNull(video).signalEndOfInputStream()
            await(videoDone)
            recordingError?.let { throw it }
            checkNotNull(muxer).finish()
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
                .put("mimeType", "video/mp4").put("videoCodec", "h264").put("audio", audioSource != "none")
                .put("audioSource", audioSource).put("audioCodec", if (audioSource == "none") JSONObject.NULL else "aac")
                .put("audioSampleRate", if (audioSource == "none") JSONObject.NULL else ScreenRecordingAudio.SAMPLE_RATE)
                .put("audioChannels", if (audioSource == "none") JSONObject.NULL else 1)
        } finally { close() }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { frames?.close() }; frames = null
        runCatching { audio?.close() }; audio = null
        draining = false
        runCatching { videoThread?.join(1000) }
        runCatching { video?.stop() };runCatching { video?.release() };video = null
        runCatching { videoSurface?.release() };videoSurface = null
        runCatching { muxer?.close() };muxer = null
        runCatching { capturer.restoreCaptureSurface() }
        temporary?.delete()
        temporary = null
    }

    private fun integer(options: JSONObject, name: String, default: Int, range: IntRange): Int {
        if (!options.has(name)) return default
        val value = (options.opt(name) as? Number)?.toDouble()
        require(value != null && value.isFinite() && value % 1 == 0.0 && value >= range.first && value <= range.last) { "$name must be an integer in $range." }
        return value.toInt()
    }

    private fun await(future: CompletableFuture<Unit>) {
        try { future.get(3, TimeUnit.SECONDS) } catch (error: ExecutionException) { throw error.cause ?: error }
    }

    companion object {
        fun audioSource(options: JSONObject): String = when (val value = options.opt("audio")) {
            null, JSONObject.NULL, false, "none" -> "none"
            "microphone", "playback", "mixed" -> value as String
            else -> throw IllegalArgumentException("audio must be none, microphone, playback, mixed or false.")
        }
    }
}
