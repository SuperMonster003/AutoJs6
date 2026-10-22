package org.autojs.autojs.core.plugin.autojs4

import android.os.Bundle
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.FakeRemote
import org.autojs.autojs.core.plugin.allocate
import org.autojs.autojs.core.plugin.registeredLease
import org.autojs.plugin.autojs4.runtime.api.IAutoJs4RuntimePlugin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An AutoJs4 admission that answers after the admission deadline (docs/memory/ROADMAP.md appendix C Q59): the engine waits on
 * the script thread in a plain `runBlocking`, gives up with a timeout, and the late admission is cancelled in the runtime over
 * the Binder of its own dedicated binding before that binding is released.
 * zh-CN: 受理截止时间之后才应答的 AutoJs4 受理 (docs/memory/ROADMAP.md 附录 C Q59): 引擎在脚本线程上以普通 `runBlocking` 等待, 超时后放弃,
 * 迟到的受理经其专用绑定的 Binder 在运行时中取消, 随后释放该绑定.
 */
class AutoJs4LateAdmissionTest {

    @Test
    fun anAdmissionAnsweringAfterTheDeadlineIsCancelledInTheRuntimeAndReleasesTheBinding() {
        val runtime = FakeRemote()
        val plugin = runtime.remote(IAutoJs4RuntimePlugin::class.java)
        val lease = registeredLease()
        val admission = AutoJs4RuntimePluginClient.Admission(allocate(Bundle::class.java), FakeRemote(localInterface = { plugin }).binder)

        val failure = runCatching {
            runBlocking {
                AidlPluginHost.withTimeoutAbandoningLate(
                    DEADLINE_MS,
                    { late: AutoJs4RuntimePluginClient.Admission -> late.abandon(EXECUTION_ID) },
                ) {
                    delay(1)
                    Thread.sleep(LATE_MS)
                    AidlPluginHost.LeasedCall(admission, lease)
                }
            }
        }.exceptionOrNull()

        assertTrue(failure is TimeoutCancellationException)
        assertEquals(listOf("cancelScript($EXECUTION_ID)"), runtime.calls)
        assertTrue(lease.isClosed)
    }

    private companion object {
        const val EXECUTION_ID = "execution-1"
        const val DEADLINE_MS = 100L
        const val LATE_MS = 400L
    }
}
