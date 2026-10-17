package org.autojs.autojs.engine

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** Serializes the video/audio codec drains into one MP4, without a final file-sized remux. */
internal class ScreenRecordingMuxer(file: File, private val trackCount: Int) : AutoCloseable {
    private val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var tracks = 0
    private var started = false
    private var closed = false
    private data class Sample(val track: Int, val bytes: ByteArray, val timeUs: Long, val flags: Int)
    private val pending = ArrayList<Sample>()
    private var pendingBytes = 0
    var videoSamples = 0
        private set
    var audioSamples = 0
        private set

    @Synchronized fun addTrack(format: MediaFormat): Int {
        check(!closed && !started) { "Recording track changed after muxer startup." }
        val track = muxer.addTrack(format)
        if (++tracks == trackCount) {
            muxer.start()
            started = true
            pending.forEach { sample ->
                muxer.writeSampleData(sample.track, ByteBuffer.wrap(sample.bytes), MediaCodec.BufferInfo().apply {
                    set(0, sample.bytes.size, sample.timeUs, sample.flags)
                })
            }
            pending.clear()
            pendingBytes = 0
        }
        return track
    }

    @Synchronized fun write(track: Int, data: ByteBuffer, info: MediaCodec.BufferInfo, video: Boolean) {
        check(!closed)
        if (info.size == 0 || info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) return
        if (video) videoSamples++ else audioSamples++
        if (started) muxer.writeSampleData(track, data, info)
        else {
            // Only startup samples wait for the other encoder's format. Startup itself is bounded.
            check(pendingBytes + info.size <= 2 * 1024 * 1024) { "Recording encoder did not publish its track format." }
            val bytes = ByteArray(info.size)
            data.duplicate().apply { position(info.offset); limit(info.offset + info.size) }.get(bytes)
            pending.add(Sample(track, bytes, info.presentationTimeUs, info.flags))
            pendingBytes += bytes.size
        }
    }

    @Synchronized fun finish() {
        check(started && videoSamples > 0 && (trackCount == 1 || audioSamples > 0)) { "Recording has no complete audio/video tracks." }
        try { muxer.stop() } finally { closed = true; muxer.release(); pending.clear() }
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        if (started) runCatching { muxer.stop() }
        runCatching { muxer.release() }
        pending.clear()
    }
}
