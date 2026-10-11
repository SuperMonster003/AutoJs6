package org.autojs.autojs.core.plugin.mcp

import android.os.Bundle
import android.os.IBinder
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.ServiceBindingLease
import org.autojs.plugin.mcp.server.api.IMcpServerSession
import org.autojs.plugin.mcp.server.api.McpServerContract
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The rebind schedule of [McpServerSessionController] (roadmap P1.2 / P9) against a fake plugin:
 * the session binder's death is reported through the captured death recipient, the 1 / 3 / 9 s
 * backoff is recorded instead of waited for, and the plugin's availability is switched by the test.
 * zh-CN: 以假插件验证 [McpServerSessionController] 的重绑计划 (路线图 P1.2 / P9): 会话 binder 的死亡经捕获的
 * 死亡通知报告, 1 / 3 / 9 s 退避只记录不等待, 插件可用性由测试切换.
 */
class McpServerSessionControllerRebindTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val statuses = CopyOnWriteArrayList<McpServerStatus>()
    private val opens = AtomicInteger()
    private val failOpens = AtomicInteger()
    private val leases = CopyOnWriteArrayList<ServiceBindingLease>()
    private val sessions = CopyOnWriteArrayList<FakeSession>()
    private val deathRecipients = CopyOnWriteArrayList<IBinder.DeathRecipient>()
    private val sleeps = CopyOnWriteArrayList<Long>()
    private val sleepGate = Channel<Unit>(Channel.UNLIMITED)
    private val available = AtomicBoolean(true)

    private class FakeSession : IMcpServerSession.Stub() {
        val stops = AtomicInteger()
        val closes = AtomicInteger()

        override fun getStatus(): Bundle = Bundle().apply {
            putString(McpServerContract.KEY_STATUS_STATE, McpServerContract.STATE_RUNNING)
            putInt(McpServerContract.KEY_STATUS_PORT, PORT)
        }

        override fun updateConfig(config: Bundle?) = Unit

        override fun stop(reason: Bundle?) {
            stops.incrementAndGet()
        }

        override fun close() {
            closes.incrementAndGet()
        }
    }

    private val controller = McpServerSessionController(
        context,
        object : McpServerSessionController.Listener {
            override fun onStatus(status: McpServerStatus) {
                statuses += status
            }

            override fun onEvent(event: McpServerEvent) = Unit
        },
        scope = scope,
        pluginAvailable = { available.get() },
        openServer = { _, _, _, _ ->
            opens.incrementAndGet()
            if (failOpens.getAndUpdate { if (it > 0) it - 1 else 0 } > 0) {
                throw IllegalStateException("fake plugin refused to open")
            }
            val session = FakeSession().also { sessions += it }
            AidlPluginHost.LeasedCall(session as IMcpServerSession, ServiceBindingLease {}.also { leases += it })
        },
        linkDeath = { _, recipient -> deathRecipients += recipient },
        unlinkDeath = { _, recipient -> deathRecipients.remove(recipient) },
        sleep = { delayMs ->
            sleeps += delayMs
            sleepGate.receive()
        },
    )

    @After
    fun tearDown() {
        runBlocking { controller.close() }
        scope.cancel()
    }

    private fun openRunning() = runBlocking {
        controller.open(McpServerConfig(port = PORT))
        assertTrue(controller.isOpen)
        assertEquals(1, deathRecipients.size)
    }

    /** Reports the death of the current session binder. zh-CN: 报告当前会话 binder 的死亡. */
    private fun killPlugin() {
        deathRecipients.last().binderDied()
    }

    private fun awaitStatus(description: String, predicate: (McpServerStatus) -> Boolean): McpServerStatus {
        awaitCondition(description) { statuses.lastOrNull()?.let(predicate) == true }
        return statuses.last()
    }

    private fun awaitCondition(description: String, condition: () -> Boolean) = runBlocking {
        try {
            withTimeout(10_000L) {
                while (!condition()) delay(10)
            }
        } catch (_: TimeoutCancellationException) {
            throw AssertionError("timed out waiting: $description (statuses: $statuses)")
        }
    }

    private fun releaseSleep() {
        sleepGate.trySend(Unit)
    }

    @Test
    fun aDeadPluginIsRebound() {
        openRunning()
        killPlugin()
        val rebinding = awaitStatus("starting with the first rebind attempt") { it.state == McpServerContract.STATE_STARTING && it.lastError != null }
        assertEquals(McpServerContract.REASON_PLUGIN_DIED, rebinding.lastErrorCode)
        assertTrue(rebinding.lastError!!, rebinding.lastError!!.contains("attempt 1 of 3") && rebinding.lastError!!.contains("1000 ms"))
        assertTrue("the dead session's binding is released", leases[0].isClosed)
        releaseSleep()
        awaitCondition("reopened") { opens.get() == 2 && controller.isOpen }
        assertEquals(listOf(1_000L), sleeps)
        awaitStatus("running again") { it.isRunning }

        // A later death starts the schedule from the first attempt again.
        killPlugin()
        awaitCondition("second schedule started") { sleeps.size == 2 }
        assertEquals(listOf(1_000L, 1_000L), sleeps)
        releaseSleep()
        awaitCondition("reopened again") { opens.get() == 3 && controller.isOpen }
    }

    @Test
    fun theScheduleIsOneThreeNineSecondsThenStops() {
        openRunning()
        failOpens.set(3)
        killPlugin()
        repeat(3) { attempt ->
            awaitCondition("backoff ${attempt + 1} requested") { sleeps.size == attempt + 1 }
            releaseSleep()
        }
        val stopped = awaitStatus("stopped after the last attempt") { it.state == McpServerContract.STATE_STOPPED }
        assertEquals(listOf(1_000L, 3_000L, 9_000L), sleeps)
        assertEquals("the first open and three rebind attempts", 4, opens.get())
        assertEquals(McpServerContract.REASON_PLUGIN_DIED, stopped.lastErrorCode)
        assertTrue(stopped.lastError!!, stopped.lastError!!.contains("did not come back after 3 rebind attempts"))
        assertTrue(!controller.isOpen)
    }

    @Test
    fun aPluginStoppedOutsideAutoJs6IsNotRebound() {
        openRunning()
        available.set(false)
        killPlugin()
        val stopped = awaitStatus("stopped without rebinding") { it.state == McpServerContract.STATE_STOPPED }
        assertTrue(stopped.lastError!!, stopped.lastError!!.contains("stopped outside AutoJs6"))
        assertEquals(emptyList<Long>(), sleeps)
        assertEquals(1, opens.get())
    }

    @Test
    fun aPluginThatBecomesUnavailableDuringTheBackoffIsNotRebound() {
        openRunning()
        killPlugin()
        awaitCondition("backoff requested") { sleeps.size == 1 }
        available.set(false)
        releaseSleep()
        val stopped = awaitStatus("stopped without rebinding") { it.state == McpServerContract.STATE_STOPPED }
        assertTrue(stopped.lastError!!, stopped.lastError!!.contains("stopped outside AutoJs6"))
        assertEquals(1, opens.get())
    }

    @Test
    fun closingDuringTheBackoffCancelsTheRebind() {
        openRunning()
        killPlugin()
        awaitCondition("backoff requested") { sleeps.size == 1 }
        runBlocking { controller.close() }
        releaseSleep()
        runBlocking { delay(200) }
        assertEquals(1, opens.get())
        assertEquals(McpServerContract.STATE_STOPPED, statuses.last().state)
        assertEquals(McpServerContract.REASON_USER_REQUEST, statuses.last().lastErrorCode)
    }

    @Test
    fun aDeathAfterCloseIsNotRebound() {
        openRunning()
        val recipient = deathRecipients.last()
        runBlocking { controller.close() }
        assertEquals(1, sessions[0].closes.get())
        assertEquals("close unlinks the death recipient", 0, deathRecipients.size)
        recipient.binderDied()
        runBlocking { delay(200) }
        assertEquals(emptyList<Long>(), sleeps)
        assertEquals(1, opens.get())
    }

    private companion object {
        const val PORT = 19637
    }
}
