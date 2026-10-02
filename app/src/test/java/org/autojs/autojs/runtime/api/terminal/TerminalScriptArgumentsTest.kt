package org.autojs.autojs.runtime.api.terminal

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.terminal.TerminalPluginException
import org.autojs.autojs.core.plugin.terminal.TerminalPluginState
import org.autojs.plugin.terminal.api.TerminalContract
import org.autojs.plugin.terminal.api.TerminalErrorCodes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The script-to-request argument mapping of the `terminal` API (plugin roadmap P4.1, appendix A.2), without Rhino. */
class TerminalScriptArgumentsTest {

    @Test
    fun openAcceptsNothingADirectoryOrOptionsAndRefusesUris() {
        val none = TerminalScriptArguments.open(null)
        assertNull(none.cwd)
        assertTrue(none.show)
        assertTrue(none.env.isEmpty())
        assertNull(none.title)

        val directory = TerminalScriptArguments.open(json("\"projects/demo\""))
        assertEquals("projects/demo", directory.cwd)
        assertTrue(directory.show)

        val options = TerminalScriptArguments.open(json("""{"cwd":"/sdcard/Scripts/demo","env":{"NODE_ENV":"production"},"show":false,"title":"Build"}"""))
        assertEquals("/sdcard/Scripts/demo", options.cwd)
        assertEquals(mapOf("NODE_ENV" to "production"), options.env)
        assertFalse(options.show)
        assertEquals("Build", options.title)

        assertTrue(invalidMessage { TerminalScriptArguments.open(json("""{"cwd":"x","name":"y"}""")) }.let { it.contains("unknown option 'options.name'") && it.contains("known options are cwd, env, show, title") })
        assertTrue(invalidMessage { TerminalScriptArguments.open(json("42")) }.contains("'dir' must be a string"))
        assertTrue(invalidMessage { TerminalScriptArguments.open(json("\" \"")) }.contains("'dir' must not be blank"))
        assertTrue(invalidMessage { TerminalScriptArguments.open(json("""{"title":"  "}""")) }.contains("'options.title' must not be blank"))
        assertTrue(invalidMessage { TerminalScriptArguments.open(json("""{"show":"yes"}""")) }.contains("'options.show' must be a boolean"))
        assertTrue(invalidMessage { TerminalScriptArguments.open(json("[]")) }.contains("'options' must be an object"))
        listOf("content://com.android.externalstorage.documents/tree/primary", "file:///sdcard/Scripts", "https://example.com/x", "android.resource://pkg/raw/a").forEach { uri ->
            assertTrue(uri, invalidMessage { TerminalScriptArguments.open(json("\"$uri\"")) }.contains("URIs such as content:// are not accepted"))
            assertTrue(uri, invalidMessage { TerminalScriptArguments.open(json("""{"cwd":"$uri"}""")) }.contains("'options.cwd' must be a directory path"))
        }
        assertEquals("notes 12:30", TerminalScriptArguments.open(json("\"notes 12:30\"")).cwd)
        assertEquals("./a/../b", TerminalScriptArguments.open(json("\"./a/../b\"")).cwd)
    }

    @Test
    fun environmentIsBoundedAndKeepsThePluginVariables() {
        TerminalScriptArguments.PROTECTED_ENV_KEYS.forEach { key ->
            assertTrue(key, invalidMessage { TerminalScriptArguments.open(json("""{"env":{"$key":"/x"}}""")) }.contains("'options.env.$key' cannot be overridden"))
        }
        val many = (1..TerminalContract.MAX_ENV_ENTRIES + 1).joinToString(",") { "\"V$it\":\"$it\"" }
        assertTrue(invalidMessage { TerminalScriptArguments.open(json("""{"env":{$many}}""")) }.contains("more than ${TerminalContract.MAX_ENV_ENTRIES} entries"))
        val exact = (1..TerminalContract.MAX_ENV_ENTRIES).joinToString(",") { "\"V$it\":\"$it\"" }
        assertEquals(TerminalContract.MAX_ENV_ENTRIES, TerminalScriptArguments.open(json("""{"env":{$exact}}""")).env.size)
        val longValue = "x".repeat(TerminalContract.MAX_ENV_ENTRY_BYTES)
        assertTrue(invalidMessage { TerminalScriptArguments.open(json("""{"env":{"A":"$longValue"}}""")) }.contains("'options.env.A' is longer than ${TerminalContract.MAX_ENV_ENTRY_BYTES} bytes"))
        assertEquals(TerminalContract.MAX_ENV_ENTRY_BYTES - 2, TerminalScriptArguments.open(json("""{"env":{"A":"${longValue.dropLast(2)}"}}""")).env.getValue("A").length)
        assertTrue(invalidMessage { TerminalScriptArguments.open(json("""{"env":{"A":1}}""")) }.contains("'options.env.A' must be a string"))
        assertTrue(invalidMessage { TerminalScriptArguments.open(json("""{"env":{"A=B":"1"}}""")) }.contains("invalid variable name"))
        assertTrue(invalidMessage { TerminalScriptArguments.open(json("""{"env":["A"]}""")) }.contains("'options.env' must be an object"))
    }

    @Test
    fun pathsFollowTheWorkingDirectoryRule() {
        val cwd = "/sdcard/Scripts"
        assertEquals("/data/local/tmp", TerminalScriptArguments.resolvePath("/data/local/tmp", cwd))
        assertEquals("/data/local/tmp", TerminalScriptArguments.resolvePath("/data/local/tmp", null))
        assertEquals("/sdcard/Scripts/sub/dir", TerminalScriptArguments.resolvePath("sub/dir", cwd))
        assertEquals("/sdcard/x", TerminalScriptArguments.resolvePath("../x", "$cwd/"))
        assertEquals("/sdcard/Scripts/a/b/", TerminalScriptArguments.resolvePath("./a//b/", cwd))
        assertEquals(cwd, TerminalScriptArguments.resolvePath(".", cwd))
        assertEquals("/", TerminalScriptArguments.resolvePath("..", "/sdcard"))
        assertEquals("/sdcard/y", TerminalScriptArguments.resolvePath("y", "/sdcard//"))
        assertTrue(invalidMessage { TerminalScriptArguments.resolvePath("x", null) }.contains("no working directory"))
        assertTrue(invalidMessage { TerminalScriptArguments.resolvePath("x", " ") }.contains("no working directory"))
        assertTrue(invalidMessage { TerminalScriptArguments.resolvePath("../../x", "/a") }.contains("leaves the root directory"))
    }

    @Test
    fun requestsDefaultToTheScriptDirectoryAndValidateTheContract() {
        val plain = TerminalScriptArguments.request(TerminalScriptArguments.open(null), "/sdcard/Scripts")
        assertEquals("/sdcard/Scripts", plain.cwd)
        assertNull(plain.command)
        assertTrue(plain.keepOpen)
        assertTrue(plain.env.isEmpty())
        assertNull(plain.title)

        assertEquals("/sdcard/Scripts/demo", TerminalScriptArguments.request(TerminalScriptArguments.open(json("\"demo\"")), "/sdcard/Scripts").cwd)
        assertNull("a script without a working directory leaves the directory to the plugin", TerminalScriptArguments.request(TerminalScriptArguments.open(null), null).cwd)
        assertEquals("/abs", TerminalScriptArguments.request(TerminalScriptArguments.open(json("\"/abs\"")), null).cwd)

        val withCommand = TerminalScriptArguments.request(TerminalScriptArguments.open(json("""{"title":"T","env":{"A":"1"}}""")), "/sdcard/Scripts", "ls", keepOpen = false)
        assertEquals("ls", withCommand.command)
        assertFalse(withCommand.keepOpen)
        assertEquals("T", withCommand.title)
        assertEquals(mapOf("A" to "1"), withCommand.env)

        val tooLong = "/" + "x".repeat(TerminalContract.MAX_PATH_BYTES)
        assertTrue(invalidMessage { TerminalScriptArguments.request(TerminalScriptArguments.open(json("\"$tooLong\"")), null) }.contains("cwd is blank or longer than ${TerminalContract.MAX_PATH_BYTES} bytes"))
    }

    @Test
    fun execParsesTheCommandAndItsOptions() {
        val defaults = TerminalScriptArguments.exec(json("\"npm install\""), null)
        assertEquals("npm install", defaults.command)
        assertTrue(defaults.keepOpen)
        assertFalse(defaults.wait)
        assertEquals(TerminalScriptArguments.DEFAULT_WAIT_TIMEOUT_MILLIS, defaults.timeoutMillis)
        assertTrue(defaults.stripAnsi)
        assertTrue(defaults.open.show)
        assertNull(defaults.open.cwd)

        val full = TerminalScriptArguments.exec(json("\"ls\""), json("""{"cwd":"demo","show":false,"keepOpen":false,"wait":true,"timeout":0,"stripAnsi":false,"title":"T"}"""))
        assertEquals("demo", full.open.cwd)
        assertFalse(full.open.show)
        assertFalse(full.keepOpen)
        assertTrue(full.wait)
        assertEquals(0L, full.timeoutMillis)
        assertFalse(full.stripAnsi)
        assertEquals("T", full.open.title)
        assertEquals(1500L, TerminalScriptArguments.exec(json("\"ls\""), json("""{"timeout":1500}""")).timeoutMillis)

        assertTrue(invalidMessage { TerminalScriptArguments.exec(null, null) }.contains("'command' is required"))
        assertTrue(invalidMessage { TerminalScriptArguments.exec(json("\" \""), null) }.contains("'command' must not be blank"))
        assertTrue(invalidMessage { TerminalScriptArguments.exec(json("1"), null) }.contains("'command' must be a string"))
        val oversized = "x".repeat(TerminalContract.MAX_COMMAND_BYTES + 1)
        assertTrue(invalidMessage { TerminalScriptArguments.exec(json("\"$oversized\""), null) }.contains("'command' is longer than ${TerminalContract.MAX_COMMAND_BYTES} bytes"))
        assertEquals(TerminalContract.MAX_COMMAND_BYTES, TerminalScriptArguments.exec(json("\"${oversized.dropLast(1)}\""), null).command.length)
        assertTrue(invalidMessage { TerminalScriptArguments.exec(json("\"ls\""), json("""{"timeout":-1}""")) }.contains("'options.timeout' must be a non-negative integer"))
        assertTrue(invalidMessage { TerminalScriptArguments.exec(json("\"ls\""), json("""{"timeout":1.5}""")) }.contains("'options.timeout' must be a non-negative integer"))
        assertTrue(invalidMessage { TerminalScriptArguments.exec(json("\"ls\""), json("""{"timeout":"soon"}""")) }.contains("'options.timeout' must be a non-negative number"))
        assertTrue(invalidMessage { TerminalScriptArguments.exec(json("\"ls\""), json("""{"wait":1}""")) }.contains("'options.wait' must be a boolean"))
        assertTrue(invalidMessage { TerminalScriptArguments.exec(json("\"ls\""), json("""{"closeOnExit":true}""")) }.let { it.contains("unknown option 'options.closeOnExit'") && it.contains("keepOpen") })
        assertTrue(invalidMessage { TerminalScriptArguments.exec(json("\"ls\""), json("\"demo\"")) }.contains("'options' must be an object"))
    }

    @Test
    fun npmRunBuildsTheCommandAndTakesTheDirectory() {
        val plain = TerminalScriptArguments.npmRun(json("\"build\""), null, null)
        assertEquals("npm run build", plain.command)
        assertNull(plain.open.cwd)

        val directory = TerminalScriptArguments.npmRun(json("\"test:unit\""), json("\"demo\""), json("""{"wait":true}"""))
        assertEquals("npm run test:unit", directory.command)
        assertEquals("demo", directory.open.cwd)
        assertTrue(directory.wait)
        assertEquals("demo", TerminalScriptArguments.npmRun(json("\"build\""), null, json("""{"cwd":"demo"}""")).open.cwd)
        assertEquals("demo", TerminalScriptArguments.npmRun(json("\"build\""), json("\"demo\""), json("""{"cwd":"demo"}""")).open.cwd)
        assertEquals("npm run @scope/pkg", TerminalScriptArguments.npmRun(json("\"@scope/pkg\""), null, null).command)

        assertTrue(invalidMessage { TerminalScriptArguments.npmRun(json("\"build\""), json("\"a\""), json("""{"cwd":"b"}""")) }.contains("'dir' and 'options.cwd' disagree"))
        assertTrue(invalidMessage { TerminalScriptArguments.npmRun(json("\"build; rm -rf /\""), null, null) }.contains("'script' must be an npm script name"))
        assertTrue(invalidMessage { TerminalScriptArguments.npmRun(json("\"build x\""), null, null) }.contains("'script' must be an npm script name"))
        assertTrue(invalidMessage { TerminalScriptArguments.npmRun(json("\"\""), null, null) }.contains("'script' must not be blank"))
        assertTrue(invalidMessage { TerminalScriptArguments.npmRun(null, null, null) }.contains("'script' is required"))
        assertTrue(invalidMessage { TerminalScriptArguments.npmRun(json("\"build\""), json("\"content://x/y\""), null) }.contains("'dir' must be a directory path"))
    }

    @Test
    fun sessionIdsAndInputAreChecked() {
        assertEquals("abc", TerminalScriptArguments.sessionId(json("\"abc\"")))
        assertNull(TerminalScriptArguments.optionalSessionId(null))
        assertEquals("abc", TerminalScriptArguments.optionalSessionId(json("\"abc\"")))
        assertTrue(invalidMessage { TerminalScriptArguments.sessionId(json("\" \"")) }.contains("'id' must not be blank"))
        assertTrue(invalidMessage { TerminalScriptArguments.sessionId(json("42")) }.contains("'id' must be a string"))
        assertTrue(invalidMessage { TerminalScriptArguments.sessionId(null) }.contains("'id' is required"))
        assertTrue(invalidMessage { TerminalScriptArguments.sessionId(json("\"${"x".repeat(300)}\"")) }.contains("is not a session id"))

        assertArrayEquals("ls\n".toByteArray(), TerminalScriptArguments.input(json("\"ls\\n\"")))
        assertArrayEquals(byteArrayOf(3), TerminalScriptArguments.input(json("[3]")))
        assertArrayEquals(byteArrayOf(0x1b, 0xff.toByte()), TerminalScriptArguments.input(json("[27, 255]")))
        assertTrue(invalidMessage { TerminalScriptArguments.input(json("[256]")) }.contains("'data[0]' must be a byte value 0..255"))
        assertTrue(invalidMessage { TerminalScriptArguments.input(json("[1.5]")) }.contains("'data[0]' must be a byte value 0..255"))
        assertTrue(invalidMessage { TerminalScriptArguments.input(json("[\"a\"]")) }.contains("'data[0]' must be a byte value 0..255"))
        assertTrue(invalidMessage { TerminalScriptArguments.input(json("\"\"")) }.contains("'data' must not be empty"))
        assertTrue(invalidMessage { TerminalScriptArguments.input(json("true")) }.contains("'data' must be a string or an array"))
        assertTrue(invalidMessage { TerminalScriptArguments.input(null) }.contains("'data' is required"))
        val oversized = "x".repeat(TerminalContract.MAX_INPUT_BYTES + 1)
        assertTrue(invalidMessage { TerminalScriptArguments.input(json("\"$oversized\"")) }.contains("'data' is longer than ${TerminalContract.MAX_INPUT_BYTES} bytes"))
        assertEquals(TerminalContract.MAX_INPUT_BYTES, TerminalScriptArguments.input(json("\"${oversized.dropLast(1)}\"")).size)
    }

    @Test
    fun stateNamesAreTheFiveLowercaseStates() {
        assertEquals(
            setOf("available", "not_installed", "disabled", "incompatible", "unavailable"),
            TerminalPluginState.entries.map(TerminalScriptArguments::stateName).toSet(),
        )
        assertEquals("not_installed", TerminalScriptArguments.stateName(TerminalPluginState.NOT_INSTALLED))
    }

    private fun json(text: String): JsonElement = JsonParser.parseString(text)

    private fun invalidMessage(block: () -> Unit): String {
        try {
            block()
        } catch (e: TerminalPluginException) {
            assertEquals(e.error.message, TerminalErrorCodes.INVALID_ARGUMENT, e.code)
            return e.error.message
        }
        fail("INVALID_ARGUMENT expected")
        throw AssertionError()
    }
}
