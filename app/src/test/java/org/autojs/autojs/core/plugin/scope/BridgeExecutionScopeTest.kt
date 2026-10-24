package org.autojs.autojs.core.plugin.scope

import org.autojs.autojs.core.plugin.scope.BridgeExecutionScope.Stage
import org.autojs.autojs.runtime.memory.BridgeExecutionObserver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * JVM tests of the execution scope of the plugin engines other than Node.js (docs/memory-phase2/ROADMAP.md T6): the release order
 * (the stages of a Node.js execution end, reverse registration within a stage), isolation of failing releases, one release from
 * repeated and concurrent closes, registrations made late or dropped early, weak tracking, and the report of the tracked objects
 * still reachable when the scope closes (P4.4).
 * zh-CN: Node.js 以外的插件引擎执行作用域的 JVM 测试 (docs/memory-phase2/ROADMAP.md T6): 释放顺序 (Node.js 执行结束的阶段, 阶段内按登记逆序),
 * 释放失败的隔离, 重复与并发关闭只释放一次, 迟到的登记与提前撤销的登记, 弱引用跟踪, 以及关闭时报告仍可达的跟踪对象 (P4.4).
 */
class BridgeExecutionScopeTest {

    private val warnings = mutableListOf<String>()

    private fun scope() = BridgeExecutionScope("bun", "bun-engine-1-test") { message, _ -> synchronized(warnings) { warnings += message } }

    @After
    fun resetObserver() {
        BridgeExecutionObserver.listener = null
        BridgeExecutionObserver.endListener = null
    }

    @Test
    fun stagesFollowTheEndOfANodeExecution() {
        // NodeJsRuntimePluginBridge.finishExecution: the broker first, then what the plugin was handed, then the host side.
        // zh-CN: NodeJsRuntimePluginBridge.finishExecution: 先 broker, 再交给插件的对象, 最后是宿主侧.
        assertEquals(listOf("BROKER", "CALLBACKS", "CONSOLE", "FILES"), Stage.entries.map { it.name })
    }

    @Test
    fun releasesFollowTheStagesWhateverTheRegistrationOrder() {
        val scope = scope()
        val released = mutableListOf<Stage>()
        Stage.entries.shuffled(java.util.Random(42)).forEach { stage -> scope.own(stage, stage.name) { released += stage } }

        val failures = scope.close()

        assertTrue(failures.isEmpty())
        assertEquals(Stage.entries.toList(), released)
        assertTrue(scope.isClosed)
    }

    @Test
    fun aStageReleasesInTheReverseOrderOfRegistration() {
        val scope = scope()
        val released = mutableListOf<String>()
        scope.own(Stage.CALLBACKS, "runtime callback") { released += "runtime callback" }
        scope.own(Stage.FILES, "payload") { released += "payload" }
        scope.own(Stage.CALLBACKS, "capability callback") { released += "capability callback" }
        scope.own(Stage.BROKER, "broker") { released += "broker" }

        scope.close()

        assertEquals(listOf("broker", "capability callback", "runtime callback", "payload"), released)
    }

    @Test
    fun aFailingReleaseDoesNotStopTheOthers() {
        val scope = scope()
        val released = mutableListOf<String>()
        val brokerFailure = IllegalStateException("broker")
        scope.own(Stage.BROKER, "broker") { throw brokerFailure }
        scope.own(Stage.CALLBACKS, "callback") { released += "callback" }
        scope.own(Stage.CONSOLE, "console") { throw StackOverflowError("console") }
        scope.own(Stage.FILES, "payload") { released += "payload" }

        val failures = scope.close()

        assertEquals(listOf("callback", "payload"), released)
        assertEquals(listOf("broker", "console"), failures.map { it.name })
        assertEquals(listOf(Stage.BROKER, Stage.CONSOLE), failures.map { it.stage })
        assertSame(brokerFailure, failures.first().cause)
        assertEquals(2, warnings.size)
        assertTrue(warnings.all { it.startsWith("bun.execution:bun-engine-1-test: releasing ") })
    }

    @Test
    fun closeReleasesOnlyOnce() {
        val scope = scope()
        var count = 0
        scope.own(Stage.BROKER, "broker") { count++ }

        scope.close()
        assertTrue(scope.close().isEmpty())

        assertEquals(1, count)
    }

    @Test
    fun aConcurrentCloseReturnsAtOnceWhileTheFirstOneReleases() {
        // A close from another thread returns at once while the first one still releases.
        // zh-CN: 另一线程的关闭在第一次关闭仍在释放时立即返回.
        val scope = scope()
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val released = java.util.concurrent.CopyOnWriteArrayList<String>()
        scope.own(Stage.BROKER, "broker") {
            entered.countDown()
            proceed.await(5, TimeUnit.SECONDS)
            released += "broker"
        }
        scope.own(Stage.CALLBACKS, "callback") { released += "callback" }
        val first = Thread { scope.close() }.apply { start() }
        assertTrue(entered.await(5, TimeUnit.SECONDS))

        assertTrue(scope.close().isEmpty())
        assertFalse(scope.isClosed)
        proceed.countDown()
        first.join(5_000L)

        assertEquals(listOf("broker", "callback"), released)
        assertTrue(scope.isClosed)
    }

    @Test
    fun disownedRegistrationsAreNotReleased() {
        val scope = scope()
        val released = mutableListOf<String>()
        val payload = scope.own(Stage.FILES, "payload") { released += "payload" }
        scope.own(Stage.CALLBACKS, "callback") { released += "callback" }

        assertTrue(payload.disown())
        assertFalse(payload.disown())
        assertTrue(payload.isDone)
        scope.close()

        assertEquals(listOf("callback"), released)
    }

    @Test
    fun lateRegistrationsAreReleasedAtOnceOrInTheirStage() {
        val scope = scope()
        val released = mutableListOf<String>()
        scope.own(Stage.CALLBACKS, "callback") {
            released += "callback"
            // A stage already passed: released at once. A stage still ahead: released in its turn.
            // zh-CN: 已经过去的阶段: 立即释放. 尚未到达的阶段: 轮到它时释放.
            scope.own(Stage.BROKER, "late broker") { released += "late broker" }
            scope.own(Stage.FILES, "late payload") { released += "late payload" }
            released += "callback done"
        }

        scope.close()
        val afterClose = scope.own(Stage.FILES, "after close") { released += "after close" }

        assertEquals(listOf("callback", "late broker", "callback done", "late payload", "after close"), released)
        assertTrue(afterClose.isDone)
        assertEquals(2, warnings.size)
        assertTrue(warnings.all { it.contains("registered after its release stage") })
    }

    @Test
    fun trackedObjectsAreReportedAndHeldWeakly() {
        val scope = scope()
        val reported = mutableListOf<String>()
        BridgeExecutionObserver.listener = BridgeExecutionObserver.Listener { engine, kind, executionId, _ ->
            reported += "$engine/$kind/$executionId"
        }
        val kept = Any()
        scope.track(kept, "broker")
        scope.track(Any(), "callback")

        val deadline = System.currentTimeMillis() + 10_000L
        while (scope.trackedReferences().size > 1 && System.currentTimeMillis() < deadline) {
            System.gc()
            Thread.sleep(20)
        }

        assertEquals(listOf("bun/broker/bun-engine-1-test", "bun/callback/bun-engine-1-test"), reported)
        assertEquals(listOf("broker"), scope.trackedReferences().map { it.name })
        assertSame(kept, scope.trackedReferences().single().reference.get())
    }

    @Test
    fun closeReportsTheTrackedObjectsStillReachableOnceAfterTheReleases() {
        val scope = scope()
        val events = mutableListOf<String>()
        val ended = mutableListOf<List<Any>>()
        BridgeExecutionObserver.endListener = BridgeExecutionObserver.EndListener { engine, executionId, objects ->
            events += "ended $engine/$executionId"
            ended += objects
        }
        val broker = Any()
        val callback = Any()
        scope.track(broker, "broker")
        scope.track(callback, "callback")
        scope.own(Stage.BROKER, "broker") { events += "broker released" }

        scope.close()
        scope.close()

        assertEquals(listOf("broker released", "ended bun/bun-engine-1-test"), events)
        assertEquals(1, ended.size)
        assertEquals(2, ended.single().size)
        assertSame(broker, ended.single()[0])
        assertSame(callback, ended.single()[1])
    }
}
