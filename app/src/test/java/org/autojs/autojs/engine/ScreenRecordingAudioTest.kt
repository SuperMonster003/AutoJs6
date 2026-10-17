package org.autojs.autojs.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenRecordingAudioTest {
    @Test fun equalGainMixingDoesNotOverflowOrClipFullScaleSources() {
        for ((a,b,expected) in listOf(Triple(32767,32767,32767),Triple(-32768,-32768,-32768),
            Triple(32767,-32768,0),Triple(12000,0,6000),Triple(-12000,0,-6000),Triple(30000,10000,20000))) {
            assertEquals(expected, ScreenRecordingAudio.mixPcm16(a.toShort(),b.toShort()).toInt())
        }
    }
}
