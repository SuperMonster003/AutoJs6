package org.autojs.autojs.core.accessibility.event

import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.event.A11yEventBus.DeferredScheduler
import org.autojs.autojs.core.accessibility.event.A11yEventBus.Options
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class A11yEventBusTest {

    /** A test event: [type] is what subscriptions filter on, [tag] tells events of one type apart. zh-CN: 测试事件. */
    private data class Event(val type: Int, val tag: String = "", val window: String = "w")

    /** Deferred tasks run when the test advances [now] past their due time. zh-CN: 测试推进时钟后运行到期任务. */
    private class FakeScheduler : DeferredScheduler {
        var now = 0L
        val tasks = mutableListOf<Pair<Long, Runnable>>()
        var cancelled = 0

        override fun schedule(delayMillis: Long, task: Runnable): DeferredScheduler.Cancellable {
            val entry = (now + delayMillis) to task
            tasks += entry
            return DeferredScheduler.Cancellable {
                if (tasks.remove(entry)) cancelled++
            }
        }

        fun advanceTo(time: Long) {
            now = time
            while (true) {
                val due = tasks.filter { it.first <= now }.minByOrNull { it.first } ?: return
                tasks.remove(due)
                due.second.run()
            }
        }
    }

    private val scheduler = FakeScheduler()
    private val errors = mutableListOf<String>()
    private val retained = mutableListOf<Event>()
    private val bus = A11yEventBus<Event>(
        typeOf = { it.type },
        clock = { scheduler.now },
        scheduler = scheduler,
        retain = { it.copy(tag = "${it.tag}-retained").also(retained::add) },
        log = { message, _ -> errors += message },
    )
    private val delivered = mutableListOf<String>()

    private fun record(name: String, consumed: Boolean = false) = A11yEventBus.Handler<Event> { event ->
        delivered += "$name:${event.type}${if (event.tag.isEmpty()) "" else "/" + event.tag}"
        consumed
    }

    @Test
    fun dispatchesInPriorityOrderThenInsertionOrder() {
        bus.subscribe(owner = "b", priority = 10, handler = record("b"))
        bus.subscribe(owner = "a", priority = 0, handler = record("a"))
        bus.subscribe(owner = "c", priority = 10, handler = record("c"))
        bus.subscribe(owner = "d", priority = -5, handler = record("d"))

        bus.dispatch(Event(1))

        assertEquals(listOf("d:1", "a:1", "b:1", "c:1"), delivered)
    }

    /**
     * Locks the order the service relies on: observers, then script callbacks, then framework
     * delegates by priority, where a consuming delegate ends the chain. The priority constants are
     * compile-time constants of [AccessibilityService], so no Android class is loaded here.
     * zh-CN: 锁定服务依赖的顺序: 观察者, 脚本回调, 再按优先级的框架 delegate (消费型 delegate 终止链).
     */
    @Test
    fun serviceDispatchOrderIsObserversThenScriptsThenDelegates() {
        bus.subscribe(owner = "recorder", priority = 300, consumable = true, handler = record("recorder"))
        bus.subscribe(owner = "notification", priority = 200, consumable = true, handler = record("notification", consumed = true))
        bus.subscribe(owner = "infoProvider", priority = 100, consumable = true, handler = record("infoProvider"))
        bus.subscribe(owner = "script-1", priority = AccessibilityService.PRIORITY_SCRIPT, handler = record("script"))
        bus.subscribe(owner = "monitor", priority = AccessibilityService.PRIORITY_OBSERVER, handler = record("monitor"))

        assertTrue(bus.dispatch(Event(1)))

        assertEquals(listOf("monitor:1", "script:1", "infoProvider:1", "notification:1"), delivered)
        assertTrue(AccessibilityService.PRIORITY_KEY_OBSERVER < AccessibilityService.PRIORITY_KEY_INTERCEPTOR)
    }

    @Test
    fun typesFilterAndNullMeansAll() {
        bus.subscribe(owner = "all", types = null, handler = record("all"))
        bus.subscribe(owner = "one", types = setOf(1), handler = record("one"))
        bus.subscribe(owner = "two", types = setOf(2, 3), handler = record("two"))

        bus.dispatch(Event(1))
        bus.dispatch(Event(3))

        assertEquals(listOf("all:1", "one:1", "all:3", "two:3"), delivered)
        assertTrue(bus.hasSubscribers(2))
        assertTrue(bus.hasSubscribers(99))
        bus.unsubscribeAll("all")
        assertFalse(bus.hasSubscribers(99))
    }

    @Test
    fun onlyConsumableSubscriptionsEndTheChain() {
        bus.subscribe(owner = "loud", priority = 0, consumable = false, handler = record("loud", consumed = true))
        bus.subscribe(owner = "eater", priority = 1, consumable = true, handler = record("eater", consumed = true))
        bus.subscribe(owner = "late", priority = 2, handler = record("late"))

        assertTrue(bus.dispatch(Event(1)))

        assertEquals("a non-consumable handler returning true does not consume", listOf("loud:1", "eater:1"), delivered)
    }

    @Test
    fun consumableSubscriptionReturningFalseLetsTheChainContinue() {
        bus.subscribe(owner = "picky", priority = 0, consumable = true, handler = record("picky", consumed = false))
        bus.subscribe(owner = "late", priority = 1, handler = record("late"))

        assertFalse(bus.dispatch(Event(1)))

        assertEquals(listOf("picky:1", "late:1"), delivered)
    }

    @Test
    fun unsubscribeByHandleAndByOwner() {
        val first = bus.subscribe(owner = "script", types = setOf(1), handler = record("first"))
        bus.subscribe(owner = "script", types = setOf(2), handler = record("second"))
        bus.subscribe(owner = "other", handler = record("other"))
        assertEquals(3, bus.subscriptionCount)
        assertEquals(2, bus.subscriptionsOf("script").size)

        assertTrue(bus.unsubscribe(first))
        assertFalse("already removed", bus.unsubscribe(first))
        assertFalse(first.isActive)
        assertEquals(1, bus.unsubscribeAll("script"))
        assertEquals(0, bus.unsubscribeAll("script"))

        bus.dispatch(Event(1))
        bus.dispatch(Event(2))
        assertEquals(listOf("other:1", "other:2"), delivered)
        assertEquals(1, bus.count { it.owner == "other" })
    }

    @Test
    fun unsubscribeIfRemovesEveryMatchingSubscription() {
        // The owner sweep of an exiting script runtime (docs/memory/ROADMAP.md P4.2): its id and its objects, nothing else.
        // zh-CN: 结束中的脚本运行时按 owner 清理 (docs/memory/ROADMAP.md P4.2): 只清它的 id 与它的对象.
        val owned = setOf("runtime@1", "events@1")
        val callback = bus.subscribe(owner = "runtime@1", types = setOf(1), handler = record("callback"))
        bus.subscribe(owner = "events@1", handler = record("keys"))
        bus.subscribe(owner = "runtime@2", handler = record("other"))

        assertEquals(2, bus.unsubscribeIf { it.owner in owned })
        assertEquals(0, bus.unsubscribeIf { it.owner in owned })
        assertFalse(callback.isActive)

        bus.dispatch(Event(1))
        assertEquals(listOf("other:1"), delivered)
        assertEquals(1, bus.subscriptionCount)
    }

    @Test
    fun handlerExceptionsAreLoggedAndDoNotStopTheChain() {
        bus.subscribe(owner = "bad", priority = 0, consumable = true) { throw IllegalStateException("boom") }
        bus.subscribe(owner = "good", priority = 1, handler = record("good"))

        assertFalse(bus.dispatch(Event(1)))

        assertEquals(listOf("good:1"), delivered)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("bad"))
    }

    @Test
    fun changesFromInsideAHandlerApplyFromTheNextEvent() {
        lateinit var self: A11yEventBus.Subscription<Event>
        self = bus.subscribe(owner = "once", priority = 0) { event ->
            delivered += "once:${event.type}"
            bus.unsubscribe(self)
            bus.subscribe(owner = "added", priority = -1, handler = record("added"))
            false
        }

        bus.dispatch(Event(1))
        bus.dispatch(Event(2))

        assertEquals(listOf("once:1", "added:2"), delivered)
    }

    @Test
    fun distinctDropsRepeatedKeysWithinTheWindow() {
        bus.subscribe(owner = "s", options = Options.distinct(withinMillis = 500) { it.window }, handler = record("s"))

        scheduler.now = 1000
        bus.dispatch(Event(1, "a", window = "w1"))
        scheduler.now = 1100
        bus.dispatch(Event(1, "b", window = "w1"))
        scheduler.now = 1200
        bus.dispatch(Event(1, "c", window = "w2"))
        scheduler.now = 1300
        bus.dispatch(Event(1, "d", window = "w1"))
        scheduler.now = 1900
        bus.dispatch(Event(1, "e", window = "w1"))

        assertEquals(
            "duplicate within the window dropped, key change passes, same key after the window passes",
            listOf("s:1/a", "s:1/c", "s:1/d", "s:1/e"), delivered,
        )
    }

    @Test
    fun distinctWithoutKeyIsAThrottleAndWithoutWindowDropsUntilTheKeyChanges() {
        bus.subscribe(owner = "throttle", options = Options.distinct(withinMillis = 100), handler = record("t"))
        bus.subscribe(owner = "keyed", options = Options.distinct { it.window }, handler = record("k"))

        scheduler.now = 0
        bus.dispatch(Event(1, "a", window = "w1"))
        scheduler.now = 50
        bus.dispatch(Event(1, "b", window = "w1"))
        scheduler.now = 150
        bus.dispatch(Event(1, "c", window = "w1"))
        scheduler.now = 160
        bus.dispatch(Event(1, "d", window = "w2"))
        scheduler.now = 5000
        bus.dispatch(Event(1, "e", window = "w2"))

        assertEquals(listOf("t:1/a", "k:1/a", "t:1/c", "k:1/d", "t:1/e"), delivered)
    }

    @Test
    fun distinctKeyExceptionLetsTheEventPass() {
        bus.subscribe(owner = "s", options = Options.distinct(withinMillis = 100) { throw IllegalStateException("key") }, handler = record("s"))

        bus.dispatch(Event(1, "a"))
        bus.dispatch(Event(1, "b"))

        assertEquals(listOf("s:1/a", "s:1/b"), delivered)
        assertEquals(2, errors.size)
    }

    @Test
    fun debounceDeliversOnlyTheLastEventOfABurstAfterTheQuietPeriod() {
        bus.subscribe(owner = "s", options = Options.debounce(100), handler = record("s"))

        scheduler.advanceTo(0)
        bus.dispatch(Event(1, "a"))
        scheduler.advanceTo(50)
        bus.dispatch(Event(1, "b"))
        scheduler.advanceTo(120)
        assertEquals("still within 100 ms of the last event", emptyList<String>(), delivered)
        scheduler.advanceTo(151)

        assertEquals(listOf("s:1/b-retained"), delivered)
        assertEquals("every deferred event was retained", listOf("a", "b"), retained.map { it.tag.removeSuffix("-retained") })
        assertEquals("the first task was cancelled by the second event", 1, scheduler.cancelled)
    }

    @Test
    fun debouncedDeliveryNeverConsumesAndUnsubscribeCancelsIt() {
        bus.subscribe(owner = "s", priority = 0, consumable = true, options = Options.debounce(100), handler = record("s", consumed = true))
        bus.subscribe(owner = "late", priority = 1, handler = record("late"))

        assertFalse(bus.dispatch(Event(1, "a")))
        assertEquals(listOf("late:1/a"), delivered)

        assertEquals(1, bus.unsubscribeAll("s"))
        scheduler.advanceTo(1000)
        assertEquals("cancelled delivery", listOf("late:1/a"), delivered)
    }

    @Test
    fun debounceRequiresAScheduler() {
        val plain = A11yEventBus<Event>(typeOf = { it.type })
        assertThrows(IllegalArgumentException::class.java) {
            plain.subscribe(owner = "s", options = Options.debounce(10), handler = record("s"))
        }
        plain.subscribe(owner = "s", options = Options.distinct(withinMillis = 10), handler = record("s"))
        plain.dispatch(Event(1))
        assertEquals(listOf("s:1"), delivered)
    }

    @Test
    fun timestampsRecordMatchesBeforeFiltering() {
        val filtered = bus.subscribe(owner = "s", types = setOf(1), options = Options.distinct(withinMillis = 1000), handler = record("s"))
        val other = bus.subscribe(owner = "o", types = setOf(2), handler = record("o"))
        assertEquals(0L, filtered.lastMatchedAtMillis)
        assertEquals(0L, bus.lastDispatchAtMillis)

        scheduler.now = 10
        bus.dispatch(Event(1))
        scheduler.now = 20
        bus.dispatch(Event(1))

        assertEquals(listOf("s:1"), delivered)
        assertEquals("a dropped duplicate still counts as activity", 20L, filtered.lastMatchedAtMillis)
        assertEquals(0L, other.lastMatchedAtMillis)
        assertEquals(20L, bus.lastDispatchAtMillis)
    }

    @Test
    fun retainIsIdentityByDefault() {
        val plainScheduler = FakeScheduler()
        val plain = A11yEventBus<Event>(typeOf = { it.type }, clock = { plainScheduler.now }, scheduler = plainScheduler)
        var seen: Event? = null
        plain.subscribe(owner = "s", options = Options.debounce(10)) { seen = it; false }
        val event = Event(1)
        plain.dispatch(event)
        plainScheduler.advanceTo(10)
        assertSame(event, seen)
    }

}
