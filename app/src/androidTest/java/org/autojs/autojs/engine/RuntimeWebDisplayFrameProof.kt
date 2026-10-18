package org.autojs.autojs.engine

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File

internal object RuntimeWebDisplayFrameProof {
    fun decode(file:File):JSONObject {
        val extractor=MediaExtractor();var decoder:MediaCodec?=null
        try {
            extractor.setDataSource(file.path);extractor.selectTrack(0);val format=extractor.getTrackFormat(0)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            val codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!).also{decoder=it;it.configure(format,null,null,0);it.start()}
            val info=MediaCodec.BufferInfo();var inputDone=false;var done=false;var red=0;var green=0;var frames=0;var last=0L
            val deadline=SystemClock.elapsedRealtime()+20000
            while(!done){check(SystemClock.elapsedRealtime()<deadline){"Display frame decode timeout"}
                if(!inputDone){val i=codec.dequeueInputBuffer(10000);if(i>=0){val size=extractor.readSampleData(requireNotNull(codec.getInputBuffer(i)),0)
                    if(size<0){inputDone=true;codec.queueInputBuffer(i,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM)}else{codec.queueInputBuffer(i,0,size,extractor.sampleTime,0);extractor.advance()}}}
                val i=codec.dequeueOutputBuffer(info,10000)
                if(i>=0){if(info.size>0){frames++;last=info.presentationTimeUs
                    codec.getOutputImage(i)?.use { image->
                        val x=image.cropRect.left+image.cropRect.width()/4;val y=image.cropRect.top+image.cropRect.height()/4
                        val plane=image.planes[2];val v=plane.buffer.get((y/2)*plane.rowStride+(x/2)*plane.pixelStride).toInt() and 255
                        if(v>155)red++;if(v<105)green++
                    }
                };done=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0;codec.releaseOutputBuffer(i,false)}
            }
            assertTrue("Changing red fixture frames: $red/$frames",red>=2);assertTrue("Changing green fixture frames: $green/$frames",green>=2);assertTrue(last>4_000_000)
            return JSONObject().put("frames",frames).put("redFrames",red).put("greenFrames",green).put("lastUs",last)
        }finally{runCatching{decoder?.stop()};decoder?.release();extractor.release()}
    }
}
