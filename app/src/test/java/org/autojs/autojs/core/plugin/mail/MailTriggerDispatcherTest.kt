package org.autojs.autojs.core.plugin.mail

import org.autojs.plugin.mail.api.MailContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailTriggerDispatcherTest {

    private var now = 100_000L
    private val scheduled = ArrayList<Pair<Long, () -> Unit>>()
    private val dispatcher = MailTriggerDispatcher(clock = { now }, schedule = { delay, action -> scheduled += delay to action })
    private val launches = ArrayList<Pair<Long, String>>()

    private fun event(uid: Long, triggerId: String = "alerts", subject: String = "Invoice 42", from: String = "billing@bank.example", folder: String = "INBOX"): MailTriggerEvent =
        requireNotNull(
            MailTriggerEvent.parse(
                """{"type":"mail","triggerId":"$triggerId","alias":"work","address":"me@example.org","folder":"$folder",
                    "message":{"uid":$uid,"folder":"$folder","subject":"$subject","from":{"name":"Bank","address":"$from"},"size":10},"receivedAt":$now}""",
            ),
        )

    private fun target(id: Long, extras: MailTriggerTaskExtras = MailTriggerTaskExtras.ANY): MailTriggerDispatcher.Target =
        MailTriggerDispatcher.Target(id, extras) { launches += id to (it.uid ?: "") }

    @Test
    fun eventsAreParsedAndOtherTypesRefused() {
        val event = event(7)
        assertEquals("alerts", event.triggerId)
        assertEquals("work", event.alias)
        assertEquals("me@example.org", event.address)
        assertEquals("INBOX", event.folder)
        assertEquals("7", event.uid)
        assertEquals(now, event.receivedAt)
        assertTrue(event.json.contains("\"subject\":\"Invoice 42\""))
        assertNull(MailTriggerEvent.parse("""{"type":"message","triggerId":"a"}"""))
        assertNull(MailTriggerEvent.parse("""{"type":"mail"}"""))
        assertNull(MailTriggerEvent.parse("[]"))
        assertNull(MailTriggerEvent.parse(null))
    }

    @Test
    fun aRepeatedArrivalLaunchesNothing() {
        val targets = listOf(target(1))
        assertEquals(listOf(1L), dispatcher.dispatch(event(1), targets))
        now += 60_000
        assertEquals(emptyList<Long>(), dispatcher.dispatch(event(1), targets))
        assertEquals(1L, dispatcher.duplicates)
        assertEquals(listOf(1L), dispatcher.dispatch(event(1, folder = "Archive"), targets))
        assertEquals(2, launches.size)
    }

    @Test
    fun tasksListenToOneWatchOrEveryWatchAndMayFilter() {
        val targets = listOf(
            target(1, MailTriggerTaskExtras("alerts")),
            target(2, MailTriggerTaskExtras("other")),
            target(3),
            target(4, MailTriggerTaskExtras("", MailTriggerFilter(subject = listOf("invoice")))),
            target(5, MailTriggerTaskExtras("ALERTS", MailTriggerFilter(from = listOf("nobody")))),
        )
        assertEquals(listOf(1L, 3L, 4L), dispatcher.dispatch(event(1), targets))
        now += 10_000
        assertEquals("the subject filter of task 4 and the sender filter of task 5 keep them out", listOf(1L, 3L), dispatcher.dispatch(event(2, subject = "Hello"), targets))
        now += 10_000
        assertEquals(listOf(2L, 3L), dispatcher.dispatch(event(3, triggerId = "other", subject = "x"), targets))
    }

    @Test
    fun burstsCollapseToOneLaunchPerIntervalWithTheLatestEvent() {
        val targets = listOf(target(1))
        assertEquals(listOf(1L), dispatcher.dispatch(event(1), targets))
        now += 1_000
        assertEquals(emptyList<Long>(), dispatcher.dispatch(event(2), targets))
        now += 500
        assertEquals(emptyList<Long>(), dispatcher.dispatch(event(3), targets))
        assertEquals(2L, dispatcher.throttled)
        assertEquals("one flush scheduled for the rest of the interval", 1, scheduled.size)
        assertEquals(MailContract.MIN_TRIGGER_INTERVAL_MS - 1_000, scheduled.single().first)
        assertEquals(listOf(1L to "1"), launches)
        now += MailContract.MIN_TRIGGER_INTERVAL_MS
        scheduled.single().second()
        assertEquals(listOf(1L to "1", 1L to "3"), launches)
        now += MailContract.MIN_TRIGGER_INTERVAL_MS
        assertEquals(listOf(1L), dispatcher.dispatch(event(4), targets))
        assertEquals(3, launches.size)
    }

    @Test
    fun extrasRoundTripAndTolerateDamage() {
        val extras = MailTriggerTaskExtras("Alerts", MailTriggerFilter(from = listOf("bank"), subject = listOf("invoice", "statement")))
        val json = extras.toJson()
        assertEquals("""{"triggerId":"Alerts","filter":{"from":["bank"],"subject":["invoice","statement"]}}""", json)
        assertEquals(extras, MailTriggerTaskExtras.parse(json))
        assertEquals(MailTriggerTaskExtras.ANY, MailTriggerTaskExtras.parse(null))
        assertEquals(MailTriggerTaskExtras.ANY, MailTriggerTaskExtras.parse("not json"))
        assertEquals(MailTriggerTaskExtras.ANY, MailTriggerTaskExtras.parse("[1]"))
        assertTrue(MailTriggerTaskExtras.parse("""{"filter":5}""").anyWatch)
        assertTrue(extras.accepts("alerts"))
        assertFalse(extras.accepts("other"))
        assertEquals(listOf("a@x.org", "b"), MailTriggerFilter.split(" A@x.org, b;\n\n"))
        assertEquals(listOf("invoice"), MailTriggerFilter.parse(MailTriggerFilter(subject = listOf(" Invoice ", "invoice")).toJson()).subject)
    }
}
