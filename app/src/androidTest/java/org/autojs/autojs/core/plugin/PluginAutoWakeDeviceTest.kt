package org.autojs.autojs.core.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.batchrename.BatchRenamePluginHost
import org.autojs.autojs.core.plugin.center.InstalledPluginRepository
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * [PluginAutoWake] against an installed Batch Rename plugin that the test force-stops first. On systems that refuse to
 * bind a plugin whose process is not running (ColorOS) the calls below only succeed through a wake, and logcat shows
 * `woke <package>` under the PluginAutoWake tag; elsewhere the bindings simply succeed. The assertions hold on both.
 * zh-CN: 针对已安装且被测试先强制停止的 Batch Rename 插件验证 [PluginAutoWake]. 在拒绝绑定未运行插件的系统 (ColorOS) 上, 下列调用
 * 只有经过唤醒才能成功, logcat 的 PluginAutoWake 标签下会出现 `woke <包名>`; 在其他系统上绑定直接成功. 断言在两种情况下都成立.
 */
@RunWith(AndroidJUnit4::class)
class PluginAutoWakeDeviceTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private var enabledBefore = true

    @Before
    fun setUp() {
        assumeTrue("No Batch Rename plugin is installed", BatchRenamePluginHost.queryServiceCount(context) > 0)
        enabledBefore = PluginEnableStore.isEnabled(context, PLUGIN, true)
        PluginEnableStore.setEnabled(context, PLUGIN, true)
    }

    @After
    fun restore() {
        PluginEnableStore.setEnabled(context, PLUGIN, enabledBefore)
        // The store writes asynchronously; the instrumentation kills the process right after the last test, so commit
        // now or the plugin may stay disabled for the next test run. zh-CN: 存储为异步写入, 最后一个测试结束后进程即被结束,
        // 因此立即提交, 否则插件可能在下一次测试时仍处于停用状态.
        context.getSharedPreferences(ENABLE_STATE_PREFERENCES, Context.MODE_PRIVATE).edit().commit()
    }

    @Test
    fun aProbeReachesAStoppedEnabledPlugin() = runBlocking {
        forceStop()
        val before = PluginAutoWake.wakesStartedFor(PLUGIN)
        val info = BatchRenamePluginHost.probe(context, PLUGIN)
        Log.i(TAG, "probe after force-stop: id=${info.id}, wakes=${PluginAutoWake.wakesStartedFor(PLUGIN) - before}")
        assertEquals("batch-rename", info.id)
    }

    @Test
    fun aUserRequestReachesAStoppedPluginThatIsNotEnabled() = runBlocking {
        PluginEnableStore.setEnabled(context, PLUGIN, false)
        forceStop()
        val info = withContext(PluginAutoWake.UserRequest) { BatchRenamePluginHost.probe(context, PLUGIN) }
        assertEquals("batch-rename", info.id)
    }

    @Test
    fun anOnDemandCallNeverWakesAPluginThatIsNotEnabled() = runBlocking {
        PluginEnableStore.setEnabled(context, PLUGIN, false)
        forceStop()
        val before = PluginAutoWake.wakesStartedFor(PLUGIN)
        runCatching { BatchRenamePluginHost.probe(context, PLUGIN) }
        assertEquals(before, PluginAutoWake.wakesStartedFor(PLUGIN))
    }

    @Test
    fun thePluginCenterListNeitherWakesNorSuspendsAKnownPlugin() = runBlocking {
        // The first listing caches the metadata of this build (a stopped plugin may be woken once for it).
        // zh-CN: 第一次列出时缓存该构建的元数据 (被停止的插件可能为此被唤醒一次).
        InstalledPluginRepository().discoverInstalled(context)
        forceStop()
        val before = PluginAutoWake.wakesStartedFor(PLUGIN)
        val listed = InstalledPluginRepository().discoverInstalled(context).first { it.packageName == PLUGIN }
        assertNull(listed.bindError)
        assertEquals(before, PluginAutoWake.wakesStartedFor(PLUGIN))
        assertFalse(PluginEnableStore.isSuspended(context, PLUGIN))
    }

    @Test
    fun aBlockingBindingReachesAStoppedPlugin() {
        forceStop()
        val connected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) = connected.countDown()
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        val bound = AtomicReference<Boolean>()
        // Worker thread: the blocking variant never wakes on the main thread. zh-CN: 工作线程: 阻塞版在主线程上从不唤醒.
        thread {
            bound.set(PluginAutoWake.bindServiceWaking(context, Intent().setComponent(SERVICE), connection, Context.BIND_AUTO_CREATE))
        }.join()
        try {
            assertTrue(bound.get())
            assertTrue("the plugin service did not connect", connected.await(10, TimeUnit.SECONDS))
        } finally {
            if (bound.get() == true) context.unbindService(connection)
        }
    }

    private fun forceStop() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("am force-stop $PLUGIN").close()
        Thread.sleep(500)
    }

    private companion object {
        const val TAG = "PluginAutoWakeTest"
        const val ENABLE_STATE_PREFERENCES = "plugin_center_enable_state"
        const val PLUGIN = "io.github.supermonster003.autojs6.plugin.batch.rename"
        val SERVICE = ComponentName(PLUGIN, "$PLUGIN.BatchRenamePluginService")
    }
}
