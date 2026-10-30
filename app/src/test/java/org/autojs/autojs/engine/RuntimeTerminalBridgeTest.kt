package org.autojs.autojs.engine

import org.autojs.autojs.core.plugin.terminal.TerminalErrorMapper
import org.autojs.autojs.core.plugin.terminal.TerminalJson
import org.autojs.autojs.core.plugin.terminal.TerminalPluginException
import org.autojs.autojs.runtime.api.terminal.TerminalScriptArguments
import org.autojs.plugin.terminal.api.TerminalErrorCodes
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The `terminal` bridge module of the JS runtimes (api-coverage P3.3, Q167 (a)): argument mapping, documents, errors, capability. */
class RuntimeTerminalBridgeTest {

    @Test fun everyMethodIsSupportedAndNeedsTheTerminalCapabilityOnly() {
        val expected = setOf("state", "isAvailable", "open", "sessions", "session", "close", "closeAll", "show", "exec", "env", "npmRun",
            "write", "waitFor", "transcript", "isAlive")
        assertEquals(expected, RuntimeTerminalBridge.METHODS)
        for (method in expected) {
            assertTrue(method, NodeBridgeModules.supports("terminal", method))
            assertEquals(method, listOf("terminal"), NodeBridgePermissionManifest.requiredCapabilitiesFor("terminal", method))
        }
        assertTrue(NodeBridgePermissionManifest.definedCapabilities.contains("terminal"))
        // Q167 (a): neither shell nor shell.root implies terminal.
        assertEquals(listOf("terminal"), NodeBridgePermissionManifest.missingCapabilities(listOf("shell", "shell.root", "shell.shizuku"), listOf("terminal")))
        assertEquals(emptyList<String>(), NodeBridgePermissionManifest.missingCapabilities(listOf("terminal"), listOf("terminal")))
    }

    @Test fun arityIsCheckedAsAnInvalidArgument() {
        RuntimeTerminalBridge.checkArity("open", JSONArray())
        RuntimeTerminalBridge.checkArity("waitFor", JSONArray().put("s1").put("ready").put(1000).put(false))
        val error = assertThrows(TerminalPluginException::class.java) { RuntimeTerminalBridge.checkArity("write", JSONArray().put("s1")) }
        assertEquals(TerminalErrorCodes.INVALID_ARGUMENT, error.code)
        assertEquals(TerminalErrorCodes.INVALID_ARGUMENT, assertThrows(TerminalPluginException::class.java) { RuntimeTerminalBridge.checkArity("spawn", JSONArray()) }.code)
    }

    @Test fun argumentsReachTheRhinoArgumentLayerAsGsonTrees() {
        val args = JSONArray().put("npm test").put(JSONObject().put("cwd", "projects/demo").put("wait", true).put("timeout", 5000)).put(JSONObject.NULL)
        val exec = TerminalScriptArguments.exec(RuntimeTerminalBridge.element(args, 0), RuntimeTerminalBridge.element(args, 1))
        assertEquals("npm test", exec.command)
        assertEquals("projects/demo", exec.open.cwd)
        assertTrue(exec.wait)
        assertEquals(5000L, exec.timeoutMillis)
        assertTrue(RuntimeTerminalBridge.element(args, 2)!!.isJsonNull)
        assertNull(RuntimeTerminalBridge.element(args, 3))
        val request = TerminalScriptArguments.request(exec.open, "/sdcard/Scripts", exec.command, exec.keepOpen)
        assertEquals("/sdcard/Scripts/projects/demo", request.cwd)
        val npm = TerminalScriptArguments.npmRun(RuntimeTerminalBridge.element(JSONArray().put("build"), 0), null, null)
        assertEquals("npm run build", npm.command)
    }

    @Test fun waitForPatternsAreSubstringsOrRegExpSources() {
        assertEquals(null to "ready", RuntimeTerminalBridge.pattern("ready"))
        assertEquals("/ready \\d+/i" to null, RuntimeTerminalBridge.pattern(JSONObject().put("regex", "ready \\d+").put("flags", "i")))
        assertEquals("/a\\/b/" to null, RuntimeTerminalBridge.pattern(JSONObject().put("regex", "a\\/b")))
        val (regex, text) = RuntimeTerminalBridge.pattern(JSONObject().put("regex", "ready \\d+").put("flags", "i"))
        val matcher = TerminalScriptArguments.lineMatcher(regex, text)
        assertTrue(matcher("Server READY 42"))
        assertFalse(matcher("server ready"))
        assertThrows(TerminalPluginException::class.java) { RuntimeTerminalBridge.pattern(42) }
        assertThrows(TerminalPluginException::class.java) { RuntimeTerminalBridge.pattern(JSONObject().put("flags", "g")) }
        assertTrue(RuntimeTerminalBridge.stripAnsi(null))
        assertFalse(RuntimeTerminalBridge.stripAnsi(false))
        assertThrows(TerminalPluginException::class.java) { RuntimeTerminalBridge.stripAnsi("no") }
    }

    @Test fun sessionDocumentsPreferTheReportedExitCode() {
        val running = TerminalJson.Session("s1", "/home", "Build", 1_700_000_000_000L, true, null, 4242, "running", "{}")
        val live = RuntimeTerminalBridge.sessionJson(running, null)
        assertEquals("s1", live.getString("id"))
        assertEquals("running", live.getString("state"))
        assertTrue(live.getBoolean("alive"))
        assertTrue(live.isNull("exitCode"))
        assertEquals(1_700_000_000_000L, live.getLong("createdAt"))
        val exited = RuntimeTerminalBridge.sessionJson(running, 3)
        assertEquals("exited", exited.getString("state"))
        assertFalse(exited.getBoolean("alive"))
        assertEquals(3, exited.getInt("exitCode"))
    }

    @Test fun failuresCarryTheTerminalCodeAndCategory() {
        val unavailable = RuntimeTerminalBridge.failure("open", TerminalErrorMapper.normalize(TerminalErrorCodes.PLUGIN_UNAVAILABLE, "install 3-Shell Terminal"))
        assertEquals("PLUGIN_UNAVAILABLE", unavailable.code)
        assertEquals("TerminalError", unavailable.name)
        assertEquals("terminal", unavailable.module)
        assertEquals(NodeBridgeErrorCategory.UNAVAILABLE, unavailable.category)
        assertEquals(NodeBridgeErrorCategory.INVALID_REQUEST, RuntimeTerminalBridge.failure("write", RuntimeTerminalBridge.terminalError(IllegalArgumentException("bad"))).category)
        assertEquals(TerminalErrorCodes.INVALID_ARGUMENT, RuntimeTerminalBridge.terminalError(org.json.JSONException("bad")).code)
        assertEquals(NodeBridgeErrorCategory.TIMEOUT, RuntimeTerminalBridge.failure("waitFor", TerminalErrorMapper.normalize(TerminalErrorCodes.TIMEOUT, "late")).category)
        assertEquals(NodeBridgeErrorCategory.PROVIDER_FAILED, RuntimeTerminalBridge.failure("show", TerminalErrorMapper.normalize(TerminalErrorCodes.SESSION_NOT_FOUND, "gone")).category)
    }
}
