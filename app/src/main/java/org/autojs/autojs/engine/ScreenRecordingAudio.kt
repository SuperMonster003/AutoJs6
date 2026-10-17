package org.autojs.autojs.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Build
import org.autojs.autojs.core.image.capture.ScreenCapturer
import java.nio.ByteOrder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/** PCM capture and AAC encoding. Both modes use Android RECORD_AUDIO; playback obeys capture policy. */
internal class ScreenRecordingAudio(
    private val context: Context,
    private val capturer: ScreenCapturer,
    private val source: String,
    private val id: String,
    private val originNs: Long,
    private val muxer: ScreenRecordingMuxer,
    private val onFailure: (Throwable) -> Unit,
    private val onNotificationStop: () -> Unit,
) : AutoCloseable {
    private val records = ArrayList<AudioRecord>()
    private var codec: MediaCodec? = null
    private var thread: Thread? = null
    private val ready = CompletableFuture<Unit>()
    @Volatile private var running = false
    @Volatile private var failure: Throwable? = null
    private val microphone = source == "microphone" || source == "mixed"
    private val playback = source == "playback" || source == "mixed"
    private var foregroundLease = false

    fun start() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Grant AutoJs6 microphone (RECORD_AUDIO) permission in Android settings before recording audio, including playback capture.")
        }
        if (playback && Build.VERSION.SDK_INT < 29) throw IllegalArgumentException("Playback audio capture requires Android 10 or later.")
        try {
            if (microphone) {
                val service = CompletableFuture<Unit>()
                foregroundLease = true
                NodeAudioRecordingService.acquire(context, id,
                    onReady = { it.fold({ service.complete(Unit) }, { error -> service.completeExceptionally(error) }) },
                    onStop = { onNotificationStop() })
                await(service)
            }
            val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()
            val bufferBytes = maxOf(AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 8192)
            if (playback) {
                check(Build.VERSION.SDK_INT >= 29)
                val capture = AudioPlaybackCaptureConfiguration.Builder(capturer.audioProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build()
                records.add(AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(bufferBytes)
                    .setAudioPlaybackCaptureConfig(capture).build())
            }
            if (microphone) records.add(AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(bufferBytes)
                .setAudioSource(MediaRecorder.AudioSource.MIC).build())
            records.forEach { check(it.state == AudioRecord.STATE_INITIALIZED) { "Android could not initialize the requested audio source." } }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,SAMPLE_RATE,1).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE,BIT_RATE);setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,8192)
                },null,null,MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            records.forEach { it.startRecording();check(it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {"Android did not start audio capture."} }
            running=true
            thread=Thread(::encode,"ScreenRecording-AAC").also {it.start()}
            await(ready)
        } catch(error:Throwable) {close();throw error}
    }

    private fun encode() {
        val encoder=checkNotNull(codec);val info=MediaCodec.BufferInfo();var track=-1;var frames=0L;var lastPts=-1L
        val first=ShortArray(1024);val second=ShortArray(1024);val timestamp=AudioTimestamp()
        fun drain(waitForEos:Boolean):Boolean {
            val deadline=System.nanoTime()+2_000_000_000L
            while(true) {
                val index=encoder.dequeueOutputBuffer(info,if(waitForEos)10_000 else 0)
                when {
                    index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {track=muxer.addTrack(encoder.outputFormat);ready.complete(Unit)}
                    index>=0 -> {
                        try {if(info.size>0){check(track>=0);muxer.write(track,checkNotNull(encoder.getOutputBuffer(index)),info,false)}}
                        finally {encoder.releaseOutputBuffer(index,false)}
                        if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0)return true
                    }
                    !waitForEos -> return false
                }
                if(waitForEos)check(System.nanoTime()<deadline) {"AAC encoder did not finish."}
            }
        }
        try {
            while(running) {
                val count=read(records[0],first)
                if(count==0)break
                val now=System.nanoTime()
                val clockNs=if(records[0].getTimestamp(timestamp,AudioTimestamp.TIMEBASE_MONOTONIC)==AudioRecord.SUCCESS && timestamp.nanoTime>0)
                    timestamp.nanoTime-(timestamp.framePosition-frames)*1_000_000_000L/SAMPLE_RATE
                    else now-count*1_000_000_000L/SAMPLE_RATE
                if(records.size==2) {
                    val other=read(records[1],second)
                    if(other==0)break
                    for(i in 0 until count) first[i]=mixPcm16(first[i],second[i])
                }
                var input=encoder.dequeueInputBuffer(10_000)
                while(input<0 && running){drain(false);input=encoder.dequeueInputBuffer(10_000)}
                if(input<0)break
                val pts=maxOf(0,(clockNs-originNs)/1000,lastPts+1)
                checkNotNull(encoder.getInputBuffer(input)).apply{clear();order(ByteOrder.LITTLE_ENDIAN);asShortBuffer().put(first,0,count)}
                encoder.queueInputBuffer(input,0,count*2,pts,0)
                lastPts=pts;frames+=count;drain(false)
            }
            val input=encoder.dequeueInputBuffer(1_000_000)
            check(input>=0) {"AAC encoder cannot accept end of stream."}
            encoder.queueInputBuffer(input,0,0,maxOf(0,lastPts+1024_000_000L/SAMPLE_RATE),MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(true)
        } catch(error:Throwable) {failure=error;ready.completeExceptionally(error);onFailure(error)}
        finally {
            running=false
            records.forEach{runCatching{it.stop()};runCatching{it.release()}}
            runCatching{encoder.stop()};runCatching{encoder.release()};codec=null
            if(foregroundLease){NodeAudioRecordingService.release(id);foregroundLease=false}
        }
    }

    private fun read(record:AudioRecord,buffer:ShortArray):Int {
        var offset=0
        while(offset<buffer.size && running) {
            val count=record.read(buffer,offset,buffer.size-offset,AudioRecord.READ_BLOCKING)
            if(!running)return 0
            check(count>0) {"Android audio capture read failed: $count"}
            offset+=count
        }
        return if(running)offset else 0
    }

    fun finish() {close();failure?.let{throw it}}
    override fun close() {
        running=false
        records.forEach{runCatching{it.stop()}}
        thread?.let {it.join(3000);check(!it.isAlive) {"Audio recording cleanup timed out."}}
        if(thread==null) {
            records.forEach{runCatching{it.release()}}
            runCatching{codec?.stop()};runCatching{codec?.release()};codec=null
        }
        if(foregroundLease){NodeAudioRecordingService.release(id);foregroundLease=false}
    }
    private fun await(future:CompletableFuture<Unit>) {
        try {future.get(3,TimeUnit.SECONDS)}catch(error:ExecutionException){throw error.cause?:error}
    }

    companion object {
        const val SAMPLE_RATE=48_000
        const val BIT_RATE=128_000
        /** Equal gain for each source avoids clipping when both are full scale. */
        internal fun mixPcm16(a:Short,b:Short):Short=((a.toInt()+b.toInt())/2).toShort()
    }
}
