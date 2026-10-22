package org.autojs.autojs.pluginclient

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.lang.ref.WeakReference

/**
 * JVM tests of the context of a client connection (docs/memory/ROADMAP.md Q54): the process-level client keeps the
 * application context, and the Activity that started the connection only weakly, for the handshake problem dialog.
 * zh-CN: 客户端连接上下文的 JVM 测试 (docs/memory/ROADMAP.md Q54): 进程级客户端保留 application context, 发起连接的 Activity
 * 只以弱引用保留, 供握手问题对话框使用.
 */
class ConnectionContextTest {

    private val app: Context = ContextWrapper(null)

    @Test
    fun aLivingActivityIsKeptForDialogs() {
        val activity = activity()
        val connection = ConnectionContext(activity)

        assertSame(app, connection.app)
        assertSame(activity, connection.activity)
    }

    @Test
    fun theConnectionDoesNotKeepTheActivity() {
        val (connection, activity) = connect()

        val deadline = System.currentTimeMillis() + 10_000L
        while (activity.get() != null && System.currentTimeMillis() < deadline) {
            System.gc()
            Thread.sleep(20)
        }

        assertNull(activity.get())
        assertNull(connection.activity)
        assertSame(app, connection.app)
    }

    @Test
    fun aContextThatIsNotAnActivityHasNoDialogHost() {
        val service = object : ContextWrapper(null) {
            override fun getApplicationContext() = app
        }
        val connection = ConnectionContext(service)

        assertSame(app, connection.app)
        assertNull(connection.activity)
    }

    @Test
    fun theClientHasNoContextField() {
        val fields = JsonSocketClient::class.java.declaredFields
            .filter { Context::class.java.isAssignableFrom(it.type) }
            .map { it.name }

        assertEquals(emptyList<String>(), fields)
    }

    private fun connect(): Pair<ConnectionContext, WeakReference<Activity>> {
        val activity = activity()
        return ConnectionContext(activity) to WeakReference(activity)
    }

    private fun activity() = object : Activity() {
        override fun getApplicationContext() = app
    }

}
