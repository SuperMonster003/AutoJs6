package org.autojs.autojs.core.looper

import android.os.Handler
import android.os.Looper
import android.os.Message
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * A thread which prepared a looper and never looped it (the script thread of the Node.js engine) leaves its queued
 * messages behind when it ends: [LooperHelper.discardForThread] must recycle them, due ones included, so a pooled
 * Message instance referenced from elsewhere no longer reaches the handler, the looper or the thread
 * (docs/memory-phase2/ROADMAP.md C34).
 * zh-CN: 准备了 Looper 却从不循环它的线程 (Node.js 引擎的脚本线程) 结束时会留下排队中的消息: [LooperHelper.discardForThread]
 * 必须回收它们 (包括已到期的), 使其他位置引用的消息池实例不再到达 Handler, Looper 与线程 (docs/memory-phase2/ROADMAP.md C34).
 */
class LooperHelperDiscardDeviceTest {

    @Test
    fun discardRecyclesTheMessagesOfALooperWhichNeverLoops() {
        val looperRef = AtomicReference<Looper>()
        val due = AtomicReference<Message>()
        val delayed = AtomicReference<Message>()
        val thread = Thread {
            LooperHelper.prepare()
            val handler = Handler(Looper.myLooper()!!)
            due.set(handler.obtainMessage(1).also(handler::sendMessage))
            delayed.set(handler.obtainMessage(2).also { handler.sendMessageDelayed(it, 60_000L) })
            looperRef.set(Looper.myLooper())
        }
        thread.start()
        thread.join(5_000L)
        assertFalse(thread.isAlive)
        assertTrue(LooperHelper.contains(thread))
        assertSame(looperRef.get().thread, thread)
        assertNotNull(due.get().target)
        assertNotNull(delayed.get().target)

        LooperHelper.discardForThread(thread)

        assertFalse(LooperHelper.contains(thread))
        assertNull(due.get().target)
        assertNull(delayed.get().target)
    }

}
