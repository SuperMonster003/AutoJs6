package org.autojs.autojs.app.tool

import android.content.Context
import android.content.ContextWrapper
import io.reactivex.subjects.PublishSubject
import org.autojs.autojs.pluginclient.JsonSocketServer
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.ref.WeakReference

/**
 * JVM test of the server mode tool (docs/memory/ROADMAP.md Q54): the accept loop keeps its observer while the server
 * runs, and that observer must not keep the tool or the Activity that built it.
 * zh-CN: 服务端模式工具的 JVM 测试 (docs/memory/ROADMAP.md Q54): 服务端运行期间 accept 循环一直持有观察者, 该观察者不得保留工具及构造它的 Activity.
 */
class JsonSocketServerToolRetentionTest {

    @Test
    fun theRunningServerHoldsTheToolOnlyWeakly() {
        val app: Context = ContextWrapper(null)
        val server = PublishSubject.create<JsonSocketServer>()
        val (tool, activity) = subscribe(server, app)

        val deadline = System.currentTimeMillis() + 10_000L
        while ((tool.get() != null || activity.get() != null) && System.currentTimeMillis() < deadline) {
            System.gc()
            Thread.sleep(20)
        }

        assertTrue(server.hasObservers())
        assertNull(tool.get())
        assertNull(activity.get())
    }

    private fun subscribe(server: PublishSubject<JsonSocketServer>, app: Context): Pair<WeakReference<Any>, WeakReference<Any>> {
        val activity = object : ContextWrapper(null) {
            override fun getApplicationContext() = app
        }
        val tool = JsonSocketServerTool(activity)
        tool.subscribeServer(server)
        return WeakReference<Any>(tool) to WeakReference<Any>(activity)
    }

}
