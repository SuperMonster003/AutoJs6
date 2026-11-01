package org.autojs.autojs.runtime.resource

import org.autojs.autojs.runtime.resource.RuntimeResourceScope.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable

/**
 * JVM tests of the resource scope of a script runtime (docs/memory/ROADMAP.md T6 / P4.1): the release order (fixed stages, reverse
 * registration within a stage), isolation of failing releases, idempotent release, and registrations made late or dropped early.
 * zh-CN: 脚本运行时资源作用域的 JVM 测试 (docs/memory/ROADMAP.md T6 / P4.1): 释放顺序 (固定阶段, 阶段内按登记逆序), 释放失败的隔离,
 * 重复释放幂等, 以及迟到的登记与提前撤销的登记.
 */
class RuntimeResourceScopeTest {

    private val warnings = mutableListOf<String>()

    private fun scope() = RuntimeResourceScope("runtime@test") { message, _ -> warnings += message }

    @Test
    fun stagesFollowAppendixB3() {
        // The order of ScriptRuntime.onExit before P4.1 (docs/memory/ROADMAP.md appendix B.3), with the owner sweep of P4.2 after the
        // accessibility tap, and the WebViews (after the exit event) and class loaders (last) of P4.4; changing it changes what scripts see.
        // zh-CN: P4.1 之前 ScriptRuntime.onExit 的顺序 (docs/memory/ROADMAP.md 附录 B.3), 加上 P4.2 在 accessibility tap 之后的按 owner 清理,
        // 以及 P4.4 的 WebView (exit 事件之后) 与类加载器 (最后); 改变它就改变了脚本可见的行为.
        assertEquals(
            listOf(
                "IMAGE_QUANTIZATIONS", "CONSOLE", "WEBSOCKETS", "ACCESSIBILITY_EVENTS", "ACCESSIBILITY_PULSE", "ACCESSIBILITY_LISTENER",
                "ROOT_MODE", "COMPOSE", "INTERRUPT_STATUS", "FLOATY", "EXIT_EVENT", "WEB_VIEWS",
                "MAIL", "INSTALLER", "EPUB", "GALLERY", "TERMINAL", "SPEECH", "AI", "TTS",
                "THREADS", "IMAGES", "PLUGINS", "EVENTS", "MEDIA", "LOOPERS", "ACCESSIBILITY_TAP", "ACCESSIBILITY_OWNER", "SHELL",
                "SCREEN_CAPTURER", "SCREEN_CAPTURE_SERVICE", "SENSORS", "DEVICE", "TIMERS", "UI", "CLOSEABLES", "CLASS_LOADERS",
            ),
            Stage.entries.map { it.name },
        )
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
        scope.add(Closeable { released += "database" })
        scope.own(Stage.CLOSEABLES, "shell") { released += "shell" }
        scope.add(Closeable { released += "session" })
        scope.own(Stage.TIMERS, "timers") { released += "timers" }

        scope.close()

        assertEquals(listOf("timers", "session", "shell", "database"), released)
    }

    @Test
    fun aFailingReleaseDoesNotStopTheOthers() {
        val scope = scope()
        val released = mutableListOf<String>()
        val floatyFailure = IllegalStateException("floaty")
        val closeableFailure = IllegalArgumentException("closeable")
        scope.own(Stage.FLOATY, "floaty.closeAll") { throw floatyFailure }
        scope.own(Stage.EXIT_EVENT, "events.emit(exit)") { released += "exit" }
        scope.add(Closeable { released += "first" })
        scope.add(Closeable { throw closeableFailure })
        scope.own(Stage.THREADS, "threads") { throw StackOverflowError("threads") }
        scope.own(Stage.UI, "ui.recycle") { released += "ui" }

        val failures = scope.close()

        assertEquals(listOf("exit", "ui", "first"), released)
        assertEquals(listOf(Stage.FLOATY, Stage.THREADS, Stage.CLOSEABLES), failures.map { it.stage })
        assertSame(floatyFailure, failures[0].cause)
        assertTrue(failures[1].cause is StackOverflowError)
        assertSame(closeableFailure, failures[2].cause)
        assertEquals(3, warnings.size)
        assertTrue(warnings[0], warnings[0].contains("floaty.closeAll") && warnings[0].contains("runtime@test"))
    }

    @Test
    fun closeReleasesOnlyOnce() {
        val scope = scope()
        var timers = 0
        var closeables = 0
        scope.own(Stage.TIMERS, "timers") { timers++ }
        scope.add(Closeable { closeables++ })

        assertTrue(scope.close().isEmpty())
        assertTrue(scope.close().isEmpty())

        assertEquals(1, timers)
        assertEquals(1, closeables)
    }

    @Test
    fun disownedRegistrationsAreNotReleased() {
        val scope = scope()
        val released = mutableListOf<String>()
        val kept = scope.own(Stage.MEDIA, "kept") { released += "kept" }
        val dropped = scope.own(Stage.MEDIA, "dropped") { released += "dropped" }
        val closeable = Closeable { released += "closeable" }
        scope.add(closeable)

        assertTrue(dropped.disown())
        assertFalse(dropped.disown())
        scope.remove(closeable)
        scope.close()

        assertEquals(listOf("kept"), released)
        assertTrue(kept.isDone)
        assertFalse(kept.disown())
    }

    @Test
    fun aRegistrationReleasedEarlyIsSkippedByClose() {
        // One Node.js execution of a script (docs/memory-phase2/ROADMAP.md T6): the bridge releases it when the execution
        // returns, and the runtime exit releases only the executions whose release never ran, in the PLUGINS stage.
        // zh-CN: 脚本的一次 Node.js 执行 (docs/memory-phase2/ROADMAP.md T6): 执行返回时由桥接释放, 运行时结束时只释放从未释放过的执行, 在 PLUGINS 阶段.
        val scope = scope()
        val released = mutableListOf<String>()
        scope.own(Stage.LOOPERS, "loopers") { released += "loopers" }
        val returned = scope.own(Stage.PLUGINS, "node.execution:returned") { released += "returned" }
        scope.own(Stage.PLUGINS, "node.execution:interrupted") { released += "interrupted" }
        scope.own(Stage.THREADS, "threads") { released += "threads" }

        assertTrue(returned.releaseNow())
        assertFalse(returned.releaseNow())
        assertFalse(returned.disown())
        assertEquals(listOf("returned"), released)

        assertTrue(scope.close().isEmpty())
        assertEquals(listOf("returned", "threads", "interrupted", "loopers"), released)
    }

    @Test
    fun anEarlyReleaseThrowsToItsCallerOnce() {
        val scope = scope()
        var runs = 0
        val failure = IllegalStateException("broker")
        val registration = scope.own(Stage.PLUGINS, "node.execution:failing") { runs++; throw failure }

        val thrown = runCatching { registration.releaseNow() }.exceptionOrNull()

        assertSame(failure, thrown)
        assertTrue(registration.isDone)
        assertTrue(scope.close().isEmpty())
        assertEquals(1, runs)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun anExecutionStartedAfterThePluginsStageIsReleasedAtOnce() {
        val scope = scope()
        val released = mutableListOf<String>()
        scope.own(Stage.TIMERS, "timers") {
            // A thread of the script starting one more execution while the runtime exits.
            // zh-CN: 运行时结束期间脚本的某个线程又开始一次执行.
            val late = scope.own(Stage.PLUGINS, "node.execution:late") { released += "late" }
            released += if (late.isDone) "late done" else "late pending"
        }

        scope.close()

        assertEquals(listOf("late", "late done"), released)
    }

    @Test
    fun closeablesMayRemoveThemselvesWhileReleased() {
        val scope = scope()
        val closed = mutableListOf<Int>()
        lateinit var first: Closeable
        lateinit var second: Closeable
        first = Closeable {
            closed += 1
            scope.remove(first)
        }
        second = Closeable {
            closed += 2
            scope.remove(second)
        }
        scope.add(first)
        scope.add(second)

        scope.close()

        assertEquals(listOf(2, 1), closed)
    }

    @Test
    fun addingTheSameCloseableTwiceKeepsOneRegistration() {
        val scope = scope()
        var closed = 0
        val closeable = Closeable { closed++ }
        scope.add(closeable)
        scope.add(closeable)

        scope.close()

        assertEquals(1, closed)
    }

    @Test
    fun ownOnceKeepsTheFirstPendingRegistration() {
        val scope = scope()
        var released = 0
        val first = scope.ownOnce(Stage.WEBSOCKETS, "WebSocket.onExit") { released++ }
        val second = scope.ownOnce(Stage.WEBSOCKETS, "WebSocket.onExit") { released += 10 }

        scope.close()

        assertSame(first, second)
        assertEquals(1, released)
    }

    @Test
    fun lateRegistrationsAreReleasedAtOnceOrInTheirStage() {
        val scope = scope()
        val released = mutableListOf<String>()
        scope.own(Stage.THREADS, "threads") {
            released += "threads"
            // A stage already passed: released at once. A stage still ahead: released in its turn.
            // zh-CN: 已经过去的阶段: 立即释放. 尚未到达的阶段: 轮到它时释放.
            scope.own(Stage.FLOATY, "late window") { released += "late window" }
            scope.add(Closeable { released += "late closeable" })
            released += "threads done"
        }
        scope.own(Stage.UI, "ui") { released += "ui" }

        scope.close()
        scope.own(Stage.SHELL, "after close") { released += "after close" }

        assertEquals(listOf("threads", "late window", "threads done", "ui", "late closeable", "after close"), released)
        assertEquals(2, warnings.size)
        assertTrue(warnings.all { it.contains("registered after its release stage") })
    }

    @Test
    fun survivingThreadsReachTheLaterStages() {
        val scope = scope()
        val survivor = Thread {}
        var seen: List<Thread>? = null
        scope.own(Stage.IMAGES, "images") { seen = scope.survivingThreads }
        scope.own(Stage.THREADS, "threads") { scope.survivingThreads = listOf(survivor) }

        scope.close()

        assertEquals(listOf(survivor), seen)
    }

    @Test
    fun trackedObjectsAreHeldWeakly() {
        val scope = scope()
        val kept = Any()
        scope.track(kept, "kept window")
        scope.track(Any(), "dropped thread")

        val deadline = System.currentTimeMillis() + 10_000L
        while (scope.trackedReferences().size > 1 && System.currentTimeMillis() < deadline) {
            System.gc()
            Thread.sleep(20)
        }

        assertEquals(listOf("kept window"), scope.trackedReferences().map { it.name })
        assertSame(kept, scope.trackedReferences().single().reference.get())
    }
}
