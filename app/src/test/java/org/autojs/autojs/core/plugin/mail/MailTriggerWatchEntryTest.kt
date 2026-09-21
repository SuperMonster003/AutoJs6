package org.autojs.autojs.core.plugin.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailTriggerWatchEntryTest {

    @Test
    fun theTriggersDocumentBecomesLabelledEntries() {
        val entries = MailTriggerWatchEntry.parseList(
            """{"triggers":[
                {"triggerId":"alerts","alias":"work","address":"me@example.org","enabled":true,"folder":"INBOX","records":3,"status":{"triggerId":"alerts","state":"connected","since":1}},
                {"triggerId":"paused","alias":"work","enabled":false,"status":{"state":"stopped","reason":"disabled"}},
                {"triggerId":"warming","alias":"home","address":"","enabled":true,"status":{"state":"connecting"}},
                {"alias":"nameless"},
                7
            ]}""",
        )
        assertEquals(listOf("alerts", "paused", "warming"), entries.map { it.triggerId })
        assertEquals("alerts (me@example.org)", entries[0].label)
        assertEquals("paused (work) [off]", entries[1].label)
        assertEquals("warming (home) [connecting]", entries[2].label)
        assertTrue(entries[0].enabled)
        assertFalse(entries[1].enabled)
        assertEquals("connected", entries[0].state)
    }

    @Test
    fun damagedDocumentsYieldNothing() {
        assertTrue(MailTriggerWatchEntry.parseList(null).isEmpty())
        assertTrue(MailTriggerWatchEntry.parseList("").isEmpty())
        assertTrue(MailTriggerWatchEntry.parseList("not json").isEmpty())
        assertTrue(MailTriggerWatchEntry.parseList("[]").isEmpty())
        assertTrue(MailTriggerWatchEntry.parseList("""{"triggers":{}}""").isEmpty())
        assertTrue(MailTriggerWatchEntry.parseList("""{"triggers":[]}""").isEmpty())
    }
}
