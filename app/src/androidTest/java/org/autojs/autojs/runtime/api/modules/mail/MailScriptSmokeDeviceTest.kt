package org.autojs.autojs.runtime.api.modules.mail

import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.plugin.mail.FakeImapServer
import org.autojs.autojs.core.plugin.mail.MailPluginHost
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.StringScriptSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The `mail` script API end to end (mail roadmap P3.1 and P3.2): a script run by the real engine
 * calls `mail.connect` / `test` / `testAsync` / `setDefault` / `close`, the metadata objects and
 * the refusal paths against the installed plugin and a loopback IMAP server owned by this
 * process. Skipped when no mail plugin is installed.
 *
 * ```
 * .\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.runtime.api.modules.mail.MailScriptSmokeDeviceTest"
 * ```
 *
 * [realProviderConnectAndTest] and [realProviderScript] run only with `mail.smoke.provider`,
 * `mail.smoke.address` and `mail.smoke.password` (or `mail.smoke.accessToken`) instrumentation
 * arguments (never stored in the repository) and log the provider, the address domain and
 * durations only. [realProviderScript] additionally takes `mail.smoke.script`, the device path
 * of a script pushed by the plugin repository's `.python/run_host_script_smoke.py --script`
 * (the `docs/smoke` scripts there); a script starting with `"ui";` runs in ui mode and
 * `mail.smoke.timeoutMs` (default 300000) bounds the wait for its report (the watch matrix of
 * mail roadmap P5 sleeps through a Doze window).
 * [savedAccountScript] runs such a script with only `mail.smoke.alias` (an account saved on the
 * plugin's settings page) and `mail.smoke.script`; no credential reaches the host.
 */
class MailScriptSmokeDeviceTest {

    @Test
    fun scriptConnectsTestsAndClosesAgainstTheFakeServer() {
        assumePluginInstalled()
        FakeImapServer().use { server ->
            server.start()
            val closedPort = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
            val prelude = "var MAIL_SMOKE = { port: ${server.port}, closedPort: $closedPort, password: '$SECRET' };\n"
            val report = runScript("connect-smoke", assetSource("connect-smoke"), prelude, timeoutMs = 90_000L)
            val steps = report.optJSONArray("steps")
            Log.i(TAG, "connect-smoke: ${steps?.length() ?: 0} checks, connect ${report.optLong("connectMs")} ms, test ${report.optLong("testMs")} ms, testAsync ${report.optLong("testAsyncMs")} ms")
            assertTrue("connect-smoke failed after ${steps?.length() ?: 0} checks: ${report.optString("error")}", report.optBoolean("ok"))
            assertEquals("one IMAP connection per opened session", 2, server.sessions.size)
            assertTrue(server.sessions.all { "LOGIN" in it.commands })
            assertFalse("the secret never enters a report", report.toString().contains(SECRET))
        }
    }

    @Test
    fun realProviderConnectAndTest() {
        assumePluginInstalled()
        val account = realAccount()
        val report = runScript("provider-smoke", assetSource("provider-smoke"), account.prelude(), timeoutMs = 120_000L)
        Log.i(TAG, "provider-smoke: provider=${account.provider} domain=${account.domain} connect ${report.optLong("connectMs")} ms, test ${report.optLong("testMs")} ms, imap=${report.optBoolean("imapOk")}, smtp=${report.optBoolean("smtpOk")}")
        assertFalse("the secret never enters a report", report.toString().contains(account.secret))
        assertTrue("provider-smoke failed: ${report.optString("error")}", report.optBoolean("ok"))
    }

    /** A pushed smoke script of the plugin repository (`docs/smoke`) against a real account (mail roadmap P3.2). */
    @Test
    fun realProviderScript() {
        assumePluginInstalled()
        val account = realAccount()
        val scriptPath = InstrumentationRegistry.getArguments().getString("mail.smoke.script")
        assumeTrue("mail.smoke.script not given", !scriptPath.isNullOrBlank())
        val source = shellRead(scriptPath!!)
        assertTrue("$scriptPath is empty or unreadable", source.isNotBlank())
        val name = scriptPath.substringAfterLast('/').removeSuffix(".js")
        val workDir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null) ?: InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "mail-smoke").apply { mkdirs() }
        val reportFile = File(workDir, "$name-report.json").apply { delete() }
        val prelude = account.prelude(mapOf("workDir" to workDir.absolutePath, "reportPath" to reportFile.absolutePath))
        val timeoutMs = InstrumentationRegistry.getArguments().getString("mail.smoke.timeoutMs")?.toLongOrNull()?.takeIf { it > 0L } ?: 300_000L
        val started = System.currentTimeMillis()
        val report = runScript(name, source, prelude, timeoutMs = timeoutMs, reportFile = reportFile)
        val elapsed = System.currentTimeMillis() - started
        val steps = report.optJSONArray("steps")
        Log.i(TAG, "$name: provider=${account.provider} domain=${account.domain} ok=${report.optBoolean("ok")} steps=${steps?.length() ?: 0} total ${elapsed} ms timings=${report.optJSONObject("timings")} search=${report.optJSONObject("search")} send=${report.optJSONObject("send")} ticks=${report.optInt("ticks", -1)}/${report.optInt("expectedTicks", -1)}")
        assertFalse("the secret never enters a report", report.toString().contains(account.secret))
        report.toString().chunked(3000).forEachIndexed { index, chunk -> Log.i(TAG, "$name report[$index]: $chunk") }
        assertTrue("$name failed after ${steps?.length() ?: 0} steps: ${report.optString("errorCode")} ${report.optString("error")}", report.optBoolean("ok"))
        if (report.has("uiBlocked")) {
            assertFalse("the UI thread stalled: ${report.optInt("ticks")} ticks of ${report.optInt("expectedTicks")} expected", report.optBoolean("uiBlocked"))
        }
        assertTrue("the client closed at the end", report.optBoolean("closed"))
    }

    /**
     * A pushed `docs/smoke` script against an account the user saved on the plugin's settings
     * page (mail roadmap P4.7): the host receives only `mail.smoke.alias` and `mail.smoke.script`,
     * never a credential; the script connects with `mail.connect(alias)`.
     */
    @Test
    fun savedAccountScript() {
        assumePluginInstalled()
        val arguments = InstrumentationRegistry.getArguments()
        val alias = arguments.getString("mail.smoke.alias")
        val scriptPath = arguments.getString("mail.smoke.script")
        assumeTrue("mail.smoke.alias / mail.smoke.script not given", !alias.isNullOrBlank() && !scriptPath.isNullOrBlank())
        val source = shellRead(scriptPath!!)
        assertTrue("$scriptPath is empty or unreadable", source.isNotBlank())
        val name = scriptPath.substringAfterLast('/').removeSuffix(".js")
        val workDir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null) ?: InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "mail-smoke").apply { mkdirs() }
        val reportFile = File(workDir, "$name-report.json").apply { delete() }
        val prelude = "var MAIL_SMOKE = { alias: ${JSONObject.quote(alias)}, workDir: ${JSONObject.quote(workDir.absolutePath)}, reportPath: ${JSONObject.quote(reportFile.absolutePath)} };\n"
        val started = System.currentTimeMillis()
        val report = runScript(name, source, prelude, timeoutMs = 300_000L, reportFile = reportFile)
        val elapsed = System.currentTimeMillis() - started
        val steps = report.optJSONArray("steps")
        Log.i(TAG, "$name: alias=$alias ok=${report.optBoolean("ok")} steps=${steps?.length() ?: 0} total ${elapsed} ms")
        report.toString().chunked(3000).forEachIndexed { index, chunk -> Log.i(TAG, "$name report[$index]: $chunk") }
        assertTrue("$name failed after ${steps?.length() ?: 0} steps: ${report.optString("errorCode")} ${report.optString("error")}", report.optBoolean("ok"))
        assertTrue("the client closed at the end", report.optBoolean("closed"))
    }

    private fun assumePluginInstalled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("the mail plugin is not installed", MailPluginHost.queryServiceCount(context) > 0)
    }

    private class RealAccount(val provider: String, val address: String, val secret: String, val secretKey: String) {
        val domain: String get() = address.substringAfterLast('@')

        fun prelude(extra: Map<String, String> = emptyMap()): String {
            val fields = LinkedHashMap<String, String>()
            fields["provider"] = provider
            fields["address"] = address
            fields[secretKey] = secret
            fields.putAll(extra)
            return "var MAIL_SMOKE = { " + fields.entries.joinToString(", ") { (key, value) -> "$key: ${JSONObject.quote(value)}" } + " };\n"
        }
    }

    private fun realAccount(): RealAccount {
        val arguments = InstrumentationRegistry.getArguments()
        val provider = arguments.getString("mail.smoke.provider")
        val address = arguments.getString("mail.smoke.address")
        val password = arguments.getString("mail.smoke.password")
        val accessToken = arguments.getString("mail.smoke.accessToken")
        assumeTrue(
            "mail.smoke.provider / mail.smoke.address / mail.smoke.password (or mail.smoke.accessToken) not given",
            !provider.isNullOrBlank() && !address.isNullOrBlank() && (!password.isNullOrBlank() || !accessToken.isNullOrBlank()),
        )
        return if (!password.isNullOrBlank()) RealAccount(provider!!, address!!, password, "password") else RealAccount(provider!!, address!!, accessToken!!, "accessToken")
    }

    private fun assetSource(name: String): String =
        InstrumentationRegistry.getInstrumentation().context.assets.open("mail/$name.js").bufferedReader().use { it.readText() }

    /** Reads a file the shell user can see (a path pushed with `adb push`). */
    private fun shellRead(path: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cat $path")
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    /**
     * Runs [source] with [prelude] injected after a leading `"ui";` directive (or at the top) and
     * returns the report: from [reportFile] when given (written by the script, also in ui mode,
     * where the engine belongs to an Activity), else from runtime property `mail.smoke.report`.
     */
    private fun runScript(name: String, source: String, prelude: String, timeoutMs: Long, reportFile: File? = null): JSONObject {
        val script = if (source.startsWith(JavaScriptSource.EXECUTION_MODE_UI_PREFIX)) {
            val directive = source.substringBefore('\n')
            directive + "\n" + prelude + source.substring(directive.length)
        } else {
            prelude + source
        }
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }
        val execution = AutoJs.instance.scriptEngineService.execute(StringScriptSource("mail-$name", script), listener, ExecutionConfig())
        try {
            if (reportFile != null) {
                val deadline = System.currentTimeMillis() + timeoutMs
                while (!reportFile.isFile && System.currentTimeMillis() < deadline) {
                    failure.get()?.let { throw AssertionError("$name failed: ${it.message}", it) }
                    Thread.sleep(250)
                }
                assertTrue("$name left no report within $timeoutMs ms", reportFile.isFile)
                return JSONObject(reportFile.readText())
            }
            assertTrue("$name did not finish within $timeoutMs ms", completed.await(timeoutMs, TimeUnit.MILLISECONDS))
            val engine = execution.engine as RhinoJavaScriptEngine
            failure.get()?.let { throw AssertionError("$name failed: ${it.message}", it) }
            val json = engine.runtime.getProperty("mail.smoke.report") as? String
                ?: throw AssertionError("$name left no report")
            return JSONObject(json)
        } finally {
            (execution.engine as? RhinoJavaScriptEngine)?.let { if (it.thread.isAlive) it.forceStop() }
        }
    }

    private companion object {
        const val TAG = "MailScriptSmokeTest"
        const val SECRET = "alice-secret"
    }
}
