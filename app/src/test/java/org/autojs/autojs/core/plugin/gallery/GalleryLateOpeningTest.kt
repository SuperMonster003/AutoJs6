package org.autojs.autojs.core.plugin.gallery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.FakeRemote
import org.autojs.autojs.core.plugin.openAfterTheCallerWasCancelled
import org.autojs.autojs.core.plugin.registeredLease
import org.autojs.plugin.gallery.api.GalleryContract
import org.autojs.plugin.gallery.api.IGallerySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Gallery sessions that open after their script stopped or their opening call timed out (docs/memory/ROADMAP.md appendix C Q59):
 * `GalleryPluginHost` abandons such a session the way the script exit ends it, so its dedicated binding is released and the
 * script side sees the synthetic `close(host)`. The close request itself needs a Bundle, which the JVM cannot build, so the
 * release of the binding is what these tests observe.
 * zh-CN: 脚本停止或打开调用超时之后才打开的图库会话 (docs/memory/ROADMAP.md 附录 C Q59): `GalleryPluginHost` 以脚本退出时结束会话的方式放弃它,
 * 专用绑定随之释放, 脚本侧收到合成的 `close(host)`. 关闭请求本身需要 Bundle, JVM 无法构造, 因此这些测试观察的是绑定的释放.
 */
class GalleryLateOpeningTest {

    @Test
    fun aViewerOpenedAfterTheScriptStoppedEndsItsSessionAndReleasesTheBinding() {
        val remote = FakeRemote()
        val lease = registeredLease()
        val events = CopyOnWriteArrayList<GalleryEvent>()
        var client: GallerySessionClient? = null

        openAfterTheCallerWasCancelled({ late: GallerySessionClient -> late.close(finish = false) }) {
            GallerySessionClient(
                AidlPluginHost.LeasedCall(remote.remote(IGallerySession::class.java), lease),
                GallerySessionBridge(sink(events)),
                TOKEN,
            ).also { client = it }
        }

        assertTrue(client!!.isClosed)
        assertTrue(lease.isClosed)
        assertTrue("binder.unlinkToDeath" in remote.calls)
        assertEquals(listOf(GalleryEvent(GalleryContract.EVENT_CLOSE, reason = GalleryContract.REASON_HOST)), events)
    }

    @Test
    fun aSessionOpenedAfterTheTimeoutReleasesItsBindingEvenThoughItsCloseCannotBeSent() {
        val remote = FakeRemote()
        val lease = registeredLease()

        val failure = runCatching {
            runBlocking(Dispatchers.Default) {
                AidlPluginHost.withTimeoutAbandoningLate(
                    TIMEOUT_MS,
                    { session: IGallerySession -> session.close(GalleryBundles.closeOptions(GalleryPeer.LEGACY.contractVersion, finish = true)) },
                ) {
                    delay(1)
                    Thread.sleep(LATE_MS)
                    AidlPluginHost.LeasedCall(remote.remote(IGallerySession::class.java), lease)
                }
            }
        }.exceptionOrNull()

        assertTrue(failure is TimeoutCancellationException)
        assertTrue(lease.isClosed)
    }

    private fun sink(events: MutableList<GalleryEvent>) = object : GallerySessionSink {
        override fun onEvent(event: GalleryEvent) {
            events += event
        }
    }

    private companion object {
        const val TOKEN = "token"
        const val TIMEOUT_MS = 100L
        const val LATE_MS = 400L
    }
}
