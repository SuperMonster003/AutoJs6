package org.autojs.autojs.core.plugin.terminal

import com.google.gson.JsonParser
import org.autojs.plugin.terminal.api.TerminalContract
import org.autojs.plugin.terminal.api.TerminalErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalJsonTest {

    private val session = """{"id":"s-1","cwd":"/sdcard/Scripts","title":"Scripts","createdAt":1700000000000,"alive":true,"pid":4242,"state":"running"}"""
    private val exited = """{"id":"s-2","cwd":"/data/home","title":"home","createdAt":1700000001000,"alive":false,"exitCode":130,"pid":4300,"state":"exited"}"""

    @Test
    fun `open request encodes only the given fields and keeps the contract names`() {
        val json = TerminalJson.encodeOpenRequest(TerminalJson.OpenRequest(cwd = "/sdcard/Scripts", command = "npm install", env = mapOf("CI" to "1"), title = "install", keepOpen = false))
        val root = JsonParser.parseString(json).asJsonObject
        assertEquals(setOf("cwd", "command", "env", "title", "keepOpen"), root.keySet())
        assertEquals("/sdcard/Scripts", root[TerminalContract.FIELD_CWD].asString)
        assertEquals("npm install", root[TerminalContract.FIELD_COMMAND].asString)
        assertEquals("1", root[TerminalContract.FIELD_ENV].asJsonObject["CI"].asString)
        assertEquals("install", root[TerminalContract.FIELD_TITLE].asString)
        assertFalse(root[TerminalContract.FIELD_KEEP_OPEN].asBoolean)
        assertEquals("{}", TerminalJson.encodeOpenRequest(TerminalJson.OpenRequest()))
        assertEquals("{}", TerminalJson.encodeSubscribeOptions(fromStart = false))
        assertEquals("""{"fromStart":true}""", TerminalJson.encodeSubscribeOptions(fromStart = true))
    }

    @Test
    fun `open request validation follows the D22 ceilings`() {
        assertNull(TerminalJson.OpenRequest().validate())
        assertNull(TerminalJson.OpenRequest(env = (1..TerminalContract.MAX_ENV_ENTRIES).associate { "K$it" to "v" }).validate())
        assertTrue(TerminalJson.OpenRequest(env = (1..TerminalContract.MAX_ENV_ENTRIES + 1).associate { "K$it" to "v" }).validate()!!.contains("env"))
        assertTrue(TerminalJson.OpenRequest(env = mapOf("A=B" to "v")).validate()!!.contains("A=B"))
        assertTrue(TerminalJson.OpenRequest(env = mapOf("" to "v")).validate()!!.contains("blank"))
        assertTrue(TerminalJson.OpenRequest(env = mapOf("K" to "v".repeat(TerminalContract.MAX_ENV_ENTRY_BYTES))).validate()!!.contains("K"))
        assertNull(TerminalJson.OpenRequest(env = mapOf("K" to "v".repeat(TerminalContract.MAX_ENV_ENTRY_BYTES - 2))).validate())
        assertTrue(TerminalJson.OpenRequest(cwd = "").validate()!!.contains("cwd"))
        assertTrue(TerminalJson.OpenRequest(command = "x".repeat(TerminalContract.MAX_COMMAND_BYTES + 1)).validate()!!.contains("command"))
        assertTrue(TerminalJson.OpenRequest(title = " ").validate()!!.contains("title"))
        val rejected = assertThrows(TerminalPluginException::class.java) { TerminalJson.encodeOpenRequest(TerminalJson.OpenRequest(cwd = " ")) }
        assertEquals(TerminalErrorCodes.INVALID_ARGUMENT, rejected.code)
        val oversized = assertThrows(TerminalPluginException::class.java) {
            TerminalJson.encodeOpenRequest(TerminalJson.OpenRequest(env = (1..32).associate { "K$it" to "v".repeat(TerminalContract.MAX_ENV_ENTRY_BYTES - 8) }))
        }
        assertEquals(TerminalErrorCodes.INVALID_ARGUMENT, oversized.code)
        assertTrue(oversized.message!!.contains("${TerminalContract.MAX_JSON_BYTES}"))
    }

    @Test
    fun `session documents decode with defaults and keep their source text`() {
        val running = TerminalJson.decodeSession(session)
        assertEquals("s-1", running.id)
        assertEquals("/sdcard/Scripts", running.cwd)
        assertEquals("Scripts", running.title)
        assertEquals(1700000000000L, running.createdAt)
        assertTrue(running.alive)
        assertNull(running.exitCode)
        assertEquals(4242, running.pid)
        assertEquals(TerminalContract.STATE_RUNNING, running.state)
        assertFalse(running.isExited)
        assertFalse(running.isPending)
        assertEquals(session, running.json)

        val done = TerminalJson.decodeSession(exited)
        assertFalse(done.alive)
        assertEquals(130, done.exitCode)
        assertTrue(done.isExited)

        val minimal = TerminalJson.decodeSession("""{"id":"s-3","state":"pending"}""")
        assertEquals("", minimal.cwd)
        assertEquals(-1, minimal.pid)
        assertTrue(minimal.alive)
        assertTrue(minimal.isPending)
        assertFalse(TerminalJson.decodeSession("""{"id":"s-4","state":"exited"}""").alive)
    }

    @Test
    fun `session lists round trip and reject objects and unknown states`() {
        val list = TerminalJson.decodeSessions("[$session,$exited]")
        assertEquals(listOf("s-1", "s-2"), list.map { it.id })
        assertEquals(emptyList<TerminalJson.Session>(), TerminalJson.decodeSessions("[]"))
        val encoded = TerminalJson.encodeSessions(list)
        assertEquals(list.map { it.id }, TerminalJson.decodeSessions(encoded).map { it.id })

        listOf(null, "", "   ", "not json", "[1]", """[{"id":"x","state":"paused"}]""", """{"id":"x","state":"running"}""", """{"state":"running"}""").forEach { bad ->
            val error = assertThrows(bad ?: "null", TerminalPluginException::class.java) { TerminalJson.decodeSessions(bad) }
            assertEquals(bad ?: "null", TerminalErrorCodes.INTERNAL, error.code)
            assertTrue(error.message!!.contains("malformed"))
        }
        val missingId = assertThrows(TerminalPluginException::class.java) { TerminalJson.decodeSession("""{"state":"running"}""") }
        assertTrue(missingId.message!!.contains("id"))
        val huge = assertThrows(TerminalPluginException::class.java) { TerminalJson.decodeSessions("[" + "\"x\"," .repeat(TerminalContract.MAX_JSON_BYTES / 4) + "1]") }
        assertTrue(huge.message!!.contains("${TerminalContract.MAX_JSON_BYTES}"))
    }

    @Test
    fun `error documents answered in place of sessions are thrown with their code`() {
        val answer = """{"code":"SESSION_LIMIT","message":"16 sessions already run","sessionId":null}"""
        val fromSession = assertThrows(TerminalPluginException::class.java) { TerminalJson.decodeSession(answer) }
        assertEquals(TerminalErrorCodes.SESSION_LIMIT, fromSession.code)
        assertEquals("16 sessions already run", fromSession.error.message)
        assertNull(fromSession.error.sessionId)
        val fromList = assertThrows(TerminalPluginException::class.java) { TerminalJson.decodeSessions(answer) }
        assertEquals(TerminalErrorCodes.SESSION_LIMIT, fromList.code)
        val fromEnvironment = assertThrows(TerminalPluginException::class.java) { TerminalJson.decodeEnvironment(answer) }
        assertEquals(TerminalErrorCodes.SESSION_LIMIT, fromEnvironment.code)

        val scoped = TerminalJson.decodeError("""{"code":"SESSION_CLOSED","message":"gone","sessionId":"s-9"}""")!!
        assertEquals(TerminalError(TerminalErrorCodes.SESSION_CLOSED, "gone", "s-9"), scoped)
        assertNull(TerminalJson.decodeError(null))
        assertNull(TerminalJson.decodeError(""))
        assertNull(TerminalJson.decodeError("""{"message":"no code"}"""))
        assertNull(TerminalJson.decodeError("[]"))
        assertNull(TerminalJson.decodeError("garbage"))
        // Host-only codes cannot be raised by the plugin.
        assertEquals(TerminalErrorCodes.INTERNAL, TerminalJson.decodeError("""{"code":"PLUGIN_UNAVAILABLE","message":"x"}""")!!.code)
        assertEquals(TerminalErrorCodes.INTERNAL, TerminalJson.decodeError("""{"code":"NOPE"}""")!!.code)
    }

    @Test
    fun `environment documents decode the node object and tolerate missing optionals`() {
        val json = """{"home":"/data/home","prefix":"/data/usr","shell":"/system/bin/sh","node":{"available":true,"version":"v24.21.0","packageName":"io.github.supermonster003.autojs6.plugin.nodejs","pluginVersion":"1.5.1","reason":"available"},"npmRegistry":"https://registry.npmmirror.com","storageAccess":"granted","pluginVersion":"1.0.0","contractVersion":1}"""
        val environment = TerminalJson.decodeEnvironment(json)
        assertEquals("/data/home", environment.home)
        assertEquals("/data/usr", environment.prefix)
        assertEquals("/system/bin/sh", environment.shell)
        assertTrue(environment.node.available)
        assertEquals("v24.21.0", environment.node.version)
        assertEquals("1.5.1", environment.node.pluginVersion)
        assertEquals(TerminalContract.NODE_CLI_AVAILABLE, environment.node.reason)
        assertEquals("https://registry.npmmirror.com", environment.npmRegistry)
        assertEquals(TerminalContract.STORAGE_ACCESS_GRANTED, environment.storageAccess)
        assertEquals("1.0.0", environment.pluginVersion)
        assertEquals(1, environment.contractVersion)
        assertEquals(json, environment.json)

        val bare = TerminalJson.decodeEnvironment("""{"contractVersion":1}""")
        assertNull(bare.home)
        assertFalse(bare.node.available)
        assertNull(bare.node.reason)
        val noVersion = assertThrows(TerminalPluginException::class.java) { TerminalJson.decodeEnvironment("""{"home":"/x"}""") }
        assertTrue(noVersion.message!!.contains("contractVersion"))
    }
}
