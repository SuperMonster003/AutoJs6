package org.autojs.autojs.core.plugin.speech

import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.autojs.plugin.speech.api.SpeechResultKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The life cycle of one session channel (3-Macaw Speech roadmap D16, P1.4): unknown events are dropped, the first `end`
 * ends the channel, a host-side close delivers one synthetic `error` + `end`, and round events of a wake session never
 * end the channel.
 * zh-CN: 一个会话通道的生命周期 (3-Macaw Speech 路线图 D16, P1.4): 丢弃未知事件, 首个 `end` 结束通道, 宿主侧关闭投递一个合成的 `error` 与
 * `end`, 唤醒会话的轮次事件从不结束通道.
 */
class SpeechEventGateTest {

    private val received = ArrayList<SpeechEvent>()
    private val gate = SpeechEventGate { received += it }

    @Test
    fun eventsPassUntilTheFirstEnd() {
        assertTrue(gate.accept(SpeechEvent(SpeechContract.EVENT_READY, mapOf(SpeechResultKeys.SESSION_ID to "s1"))))
        assertTrue(gate.accept(SpeechEvent(SpeechContract.EVENT_PARTIAL, mapOf(SpeechResultKeys.TEXT to "ni"))))
        assertFalse("unknown types are malformed", gate.accept(SpeechEvent("bogus")))
        assertTrue(gate.accept(SpeechEvent(SpeechContract.EVENT_END, mapOf(SpeechResultKeys.REASON to SpeechContract.REASON_STOPPED))))
        assertTrue(gate.isEnded)
        assertFalse(gate.accept(SpeechEvent(SpeechContract.EVENT_FINAL, mapOf(SpeechResultKeys.TEXT to "late"))))
        assertFalse(gate.accept(SpeechEvent(SpeechContract.EVENT_END)))
        gate.closeLocally(SpeechContract.REASON_CANCELLED)
        assertEquals(listOf("ready", "partial", "end"), received.map { it.type })
        assertEquals(3, gate.droppedCount)
    }

    @Test
    fun aLocalCloseDeliversErrorThenEndOnce() {
        gate.accept(SpeechEvent(SpeechContract.EVENT_READY))
        val died = SpeechErrorMapper.normalize(SpeechErrorCodes.SESSION_CLOSED, "speech plugin process died")
        gate.closeLocally(SpeechContract.REASON_ERROR, died)
        gate.closeLocally(SpeechContract.REASON_CANCELLED)
        assertEquals(listOf("ready", "error", "end"), received.map { it.type })
        assertEquals(died, received[1].error)
        assertEquals(SpeechContract.REASON_ERROR, received[2].payload[SpeechResultKeys.REASON])
        assertFalse(gate.accept(SpeechEvent(SpeechContract.EVENT_END)))
    }

    @Test
    fun roundEventsDoNotEndTheChannel() {
        gate.accept(SpeechEvent(SpeechContract.EVENT_READY, round = 0))
        gate.accept(SpeechEvent(SpeechContract.EVENT_END, mapOf(SpeechResultKeys.REASON to SpeechContract.REASON_STOPPED), round = 0))
        assertFalse(gate.isEnded)
        assertTrue(gate.accept(SpeechEvent(SpeechContract.EVENT_READY, round = 1)))
        gate.closeLocally(SpeechContract.REASON_STOPPED)
        assertEquals(listOf(0, 0, 1, null), received.map { it.round })
    }
}
