package org.autojs.autojs.core.plugin.epub

import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.FakeRemote
import org.autojs.autojs.core.plugin.openAfterTheCallerWasCancelled
import org.autojs.autojs.core.plugin.registeredLease
import org.autojs.plugin.epub.api.EpubContract
import org.autojs.plugin.epub.api.IEpubBook
import org.autojs.plugin.epub.api.IEpubReaderSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList

/**
 * EPUB books and reader sessions that open after their script stopped (docs/memory/ROADMAP.md appendix C Q59): `EpubPluginHost`
 * closes the late client as the script exit does, so a book is closed in the plugin and a reader session ends while the reader
 * stays open; either way the dedicated binding is released. The close request of a reader session needs a Bundle, which the
 * JVM cannot build, so that test observes the release of the binding and the synthetic `close(host)`.
 * zh-CN: 脚本停止之后才打开的 EPUB 书籍与阅读器会话 (docs/memory/ROADMAP.md 附录 C Q59): `EpubPluginHost` 像脚本退出时那样关闭迟到的客户端,
 * 书籍在插件中关闭, 阅读器会话结束而阅读器保持打开; 两种情况下专用绑定都会释放. 阅读器会话的关闭请求需要 Bundle, JVM 无法构造,
 * 因此该测试观察绑定的释放与合成的 `close(host)`.
 */
class EpubLateOpeningTest {

    @Test
    fun aBookOpenedAfterTheScriptStoppedIsClosedInThePluginAndReleasesTheBinding() {
        val remote = FakeRemote()
        val lease = registeredLease()

        openAfterTheCallerWasCancelled(Closeable::close) {
            EpubBookClient(AidlPluginHost.LeasedCall(remote.remote(IEpubBook::class.java), lease))
        }

        assertEquals(listOf("binder.linkToDeath", "binder.unlinkToDeath", "close"), remote.calls)
        assertTrue(lease.isClosed)
    }

    @Test
    fun aReaderSessionOpenedAfterTheScriptStoppedEndsAndReleasesTheBinding() {
        val remote = FakeRemote()
        val lease = registeredLease()
        val events = CopyOnWriteArrayList<EpubReaderEvent>()
        var client: EpubReaderClient? = null

        openAfterTheCallerWasCancelled(Closeable::close) {
            EpubReaderClient(
                AidlPluginHost.LeasedCall(remote.remote(IEpubReaderSession::class.java), lease),
                EpubReaderBridge(sink(events)),
                TOKEN,
            ).also { client = it }
        }

        assertTrue(client!!.isClosed)
        assertTrue(lease.isClosed)
        assertEquals(listOf(EpubReaderEvent(EpubContract.EVENT_CLOSE, reason = EpubContract.REASON_HOST)), events)
    }

    private fun sink(events: MutableList<EpubReaderEvent>) = object : EpubReaderSink {
        override fun onEvent(event: EpubReaderEvent) {
            events += event
        }

        override fun onDropped(reason: String, generation: Long, seq: Long) = Unit
    }

    private companion object {
        const val TOKEN = "token"
    }
}
