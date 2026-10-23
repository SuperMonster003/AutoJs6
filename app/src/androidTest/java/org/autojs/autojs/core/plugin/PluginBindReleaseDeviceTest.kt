package org.autojs.autojs.core.plugin

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.dex.AndroidDexCompilerExperimentalTransport
import org.autojs.autojs.core.plugin.dex.DexCompilerBindingCallback
import org.autojs.autojs.core.plugin.dex.DexCompilerComponent
import org.autojs.autojs.core.plugin.dex.DexCompilerPinnedIdentity
import org.autojs.autojs.core.plugin.dex.DexCompilerRemoteConnection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A binding the system refused keeps its ServiceConnection registered in the process (`bindService` registers it before
 * the system answers) until it is unbound; [PluginAutoWake.bindServiceWaking], and so every transport built on it,
 * unbinds it (docs/memory-phase2/ROADMAP.md C43). The test disables the installed DEX compiler plugin
 * (`pm disable-user`, enabled again afterwards) so that the system refuses every binding, and tells whether a connection
 * is still registered by unbinding it: `unbindService` throws IllegalArgumentException for a connection that is not.
 * zh-CN: 被系统拒绝的绑定会让其 ServiceConnection 一直登记在进程中 (`bindService` 在系统应答之前就登记了它), 直到解绑;
 * [PluginAutoWake.bindServiceWaking] 以及基于它的全部传输会解绑它 (docs/memory-phase2/ROADMAP.md C43). 测试停用已安装的 DEX 编译器插件
 * (`pm disable-user`, 结束后重新启用), 使系统拒绝每次绑定, 并通过解绑判断连接是否仍登记: 对未登记的连接 `unbindService` 抛出
 * IllegalArgumentException.
 */
@RunWith(AndroidJUnit4::class)
class PluginBindReleaseDeviceTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private var identity: DexCompilerPinnedIdentity? = null

    @Before
    fun disablePlugin() {
        // Pinned while the plugin is enabled, as the host does before it binds. zh-CN: 在插件启用时固定身份, 与宿主绑定前一样.
        identity = AndroidDexCompilerExperimentalTransport(context).inspectExact(COMPONENT)?.pin()
        assumeTrue("No DEX compiler plugin is installed", identity != null)
        shell("pm disable-user --user 0 $PLUGIN")
    }

    @After
    fun enablePlugin() {
        if (identity != null) shell("pm enable --user 0 $PLUGIN")
    }

    @Test
    fun theSystemKeepsARefusedConnectionRegistered() {
        val connection = IgnoringConnection()
        assertFalse(context.bindService(Intent().setComponent(SERVICE), connection, Context.BIND_AUTO_CREATE))
        // Does not throw: the refused connection was registered. zh-CN: 不抛出: 被拒绝的连接已登记.
        context.unbindService(connection)
    }

    @Test
    fun bindServiceWakingUnbindsARefusedConnection() {
        val connection = IgnoringConnection()
        assertFalse(PluginAutoWake.bindServiceWaking(context, Intent().setComponent(SERVICE), connection, Context.BIND_AUTO_CREATE))
        assertNotRegistered(connection)
    }

    @Test
    fun aTransportUnbindsItsRefusedConnection() {
        val recording = RecordingContext(context)
        val failure = assertThrows(IllegalStateException::class.java) {
            AndroidDexCompilerExperimentalTransport(recording).bindExact(checkNotNull(identity), IgnoringCallback)
        }
        Log.i(TAG, "refused DEX compiler binding: ${failure.message}, bind attempts=${recording.connections.size}")
        assertEquals(1, recording.connections.toSet().size)
        recording.connections.forEach(::assertNotRegistered)
        val services = shell("dumpsys activity services $PLUGIN")
        assertFalse("the system still records a connection to $PLUGIN", services.contains("ConnectionRecord"))
    }

    private fun assertNotRegistered(connection: ServiceConnection) {
        assertThrows(IllegalArgumentException::class.java) { context.unbindService(connection) }
    }

    private fun shell(command: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }

    /** Binds through the target context and records the connections. zh-CN: 经目标 Context 绑定并记录连接. */
    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val connections = CopyOnWriteArrayList<ServiceConnection>()

        override fun getApplicationContext(): Context = this

        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
            connections += conn
            return super.bindService(service, conn, flags)
        }
    }

    private class IgnoringConnection : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) = Unit
        override fun onServiceDisconnected(name: ComponentName) = Unit
    }

    private object IgnoringCallback : DexCompilerBindingCallback {
        override fun onConnected(connection: DexCompilerRemoteConnection) = Unit
        override fun onBindingFailure(cause: Throwable?) = Unit
        override fun onBinderDeath() = Unit
    }

    private companion object {
        const val TAG = "PluginBindReleaseTest"
        const val PLUGIN = "io.github.supermonster003.autojs6.plugin.dexcompiler"
        const val SERVICE_CLASS = "$PLUGIN.DexCompilerService"
        val COMPONENT = DexCompilerComponent(PLUGIN, SERVICE_CLASS)
        val SERVICE = ComponentName(PLUGIN, SERVICE_CLASS)
    }
}
