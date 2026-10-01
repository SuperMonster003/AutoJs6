package org.autojs.autojs.core.plugin.terminal

import org.autojs.plugin.terminal.api.TerminalContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalOpenRequestTest {

    @Test
    fun `the five launcher entry points produce the contract extras and nothing else`() {
        assertEquals(emptyMap<String, Any>(), TerminalOpenRequest.open().extras())
        assertEquals(mapOf(TerminalContract.EXTRA_DIRECTORY to "/sdcard/Scripts"), TerminalOpenRequest.open("/sdcard/Scripts").extras())
        assertEquals(mapOf(TerminalContract.EXTRA_NEW_SESSION to true), TerminalOpenRequest.openNew().extras())
        assertEquals(
            mapOf(TerminalContract.EXTRA_DIRECTORY to "/sdcard/Scripts", TerminalContract.EXTRA_NEW_SESSION to true),
            TerminalOpenRequest.openNew("/sdcard/Scripts").extras(),
        )
        assertEquals(mapOf(TerminalContract.EXTRA_SESSION_ID to "s-1"), TerminalOpenRequest.session("s-1").extras())
        assertEquals(mapOf(TerminalContract.EXTRA_MANAGER to true), TerminalOpenRequest.manager().extras())
        assertEquals(
            listOf("directory", "sessionId", "newSession", "command", "manager"),
            TerminalOpenRequest("/d", "s", true, "npm install", true).extras().keys.toList(),
        )
    }

    @Test
    fun `blank and default values are omitted from the extras`() {
        assertEquals(emptyMap<String, Any>(), TerminalOpenRequest(directory = "  ", sessionId = "", command = "", newSession = false, manager = false).extras())
        assertEquals(mapOf(TerminalContract.EXTRA_COMMAND to " "), TerminalOpenRequest(command = " ").extras())
    }

    @Test
    fun `validation enforces the ceilings and rejects contradictory combinations`() {
        assertNull(TerminalOpenRequest.open("/sdcard").validate())
        assertNull(TerminalOpenRequest.openNew().validate())
        assertNull(TerminalOpenRequest.session("s").validate())
        assertNull(TerminalOpenRequest.manager().validate())
        assertNull(TerminalOpenRequest(directory = "/d", newSession = true, command = "ls").validate())
        assertNotNull(TerminalOpenRequest(directory = " ").validate())
        assertNotNull(TerminalOpenRequest(directory = "x".repeat(TerminalContract.MAX_PATH_BYTES + 1)).validate())
        assertNull(TerminalOpenRequest(directory = "x".repeat(TerminalContract.MAX_PATH_BYTES)).validate())
        assertNotNull(TerminalOpenRequest(command = "y".repeat(TerminalContract.MAX_COMMAND_BYTES + 1)).validate())
        assertNotNull(TerminalOpenRequest(sessionId = " ").validate())
        assertNotNull(TerminalOpenRequest(sessionId = "s", newSession = true).validate())
        assertNotNull(TerminalOpenRequest(sessionId = "s", command = "ls").validate())
        assertNotNull(TerminalOpenRequest(manager = true, sessionId = "s").validate())
        assertNotNull(TerminalOpenRequest(manager = true, newSession = true).validate())
        assertNotNull(TerminalOpenRequest(manager = true, command = "ls").validate())
        assertNull(TerminalOpenRequest(manager = true, directory = "/d").validate())
    }

    @Test
    fun `multi byte directories are measured in bytes`() {
        val cjk = "终端".repeat(TerminalContract.MAX_PATH_BYTES / 6 + 1)
        assertTrue(cjk.length < TerminalContract.MAX_PATH_BYTES)
        assertNotNull(TerminalOpenRequest(directory = cjk).validate())
    }
}
