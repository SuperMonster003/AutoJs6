package org.autojs.autojs.runtime.api.augment.mail

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.plugin.mail.FakeImapServer
import org.autojs.autojs.core.plugin.mail.MailPluginHost
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The `mail` script API end to end (mail roadmap P3.1): a script run by the real engine calls
 * `mail.connect` / `test` / `testAsync` / `setDefault` / `close`, the metadata objects and the
 * refusal paths against the installed plugin and a loopback IMAP server owned by this process.
 * Skipped when no mail plugin is installed.
 *
 * ```
 * .\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.runtime.api.augment.mail.MailScriptSmokeDeviceTest"
 * ```
 *
 * [realProviderConnectAndTest] runs only with `mail.smoke.provider`, `mail.smoke.address` and
 * `mail.smoke.password` instrumentation arguments (never stored in the repository) and logs
 * the provider, the address domain and durations only.
 */
class MailScriptSmokeDeviceTest {

    @Test
    fun scriptConnectsTestsAndClosesAgainstTheFakeServer() {
        assumePluginInstalled()
        FakeImapServer().use { server ->
            server.start()
            val closedPort = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
            val prelude = "var MAIL_SMOKE = { port: ${server.port}, closedPort: $closedPort, password: '$SECRET' };\n"
            val report = runScript("connect-smoke", prelude, timeoutMs = 90_000L)
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
        val arguments = InstrumentationRegistry.getArguments()
        val provider = arguments.getString("mail.smoke.provider")
        val address = arguments.getString("mail.smoke.address")
        val password = arguments.getString("mail.smoke.password")
        assumeTrue("mail.smoke.provider / mail.smoke.address / mail.smoke.password not given", !provider.isNullOrBlank() && !address.isNullOrBlank() && !password.isNullOrBlank())
        val prelude = "var MAIL_SMOKE = { provider: ${quote(provider!!)}, address: ${quote(address!!)}, password: ${quote(password!!)} };\n"
        val report = runScript("provider-smoke", prelude, timeoutMs = 120_000L)
        Log.i(TAG, "provider-smoke: provider=$provider domain=${address.substringAfterLast('@')} connect ${report.optLong("connectMs")} ms, test ${report.optLong("testMs")} ms, imap=${report.optBoolean("imapOk")}, smtp=${report.optBoolean("smtpOk")}")
        assertFalse("the secret never enters a report", report.toString().contains(password))
        assertTrue("provider-smoke failed: ${report.optString("error")}", report.optBoolean("ok"))
    }

    private fun assumePluginInstalled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("the mail plugin is not installed", MailPluginHost.queryServiceCount(context) > 0)
    }

    private fun runScript(name: String, prelude: String, timeoutMs: Long): JSONObject {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val source = instrumentation.context.assets.open("mail/$name.js").bufferedReader().use { it.readText() }
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
        val execution = AutoJs.instance.scriptEngineService.execute(StringScriptSource("mail-$name", prelude + source), listener, ExecutionConfig())
        try {
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

    private fun quote(value: String): String = JSONObject.quote(value)

    private companion object {
        const val TAG = "MailScriptSmokeTest"
        const val SECRET = "alice-secret"
    }
}
