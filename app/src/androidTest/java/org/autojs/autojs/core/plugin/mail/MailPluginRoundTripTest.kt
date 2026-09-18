package org.autojs.autojs.core.plugin.mail

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.autojs.plugin.mail.api.MailContract
import org.autojs.plugin.mail.api.MailErrorCodes
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Real cross-process Binder round trip with the installed mail plugin (mail roadmap P1.3):
 * [MailPluginHost.openSession] -> `call(session.test)` -> `onResult(ok)` against a loopback
 * IMAP server owned by this process; then the plugin process is force-stopped while a call is
 * blocked on that server, so the call fails with `SESSION_CLOSED`, the client marks the session
 * for reopening and the next call opens a fresh session on its own (decision D4). A death while
 * the session is idle is noticed the same way.
 *
 * Skipped when no mail plugin is installed. The plugin's caller guard needs this host to be
 * signed by the plugin's certificate and to be at least the plugin's `requiresHostVersion`.
 *
 * ```
 * .\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.core.plugin.mail.MailPluginRoundTripTest"
 * ```
 */
class MailPluginRoundTripTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sessionTestRoundTripSurvivesAPluginProcessDeath(): Unit = runBlocking {
        assumeTrue("the mail plugin is not installed", MailPluginHost.queryServiceCount(context) > 0)
        val discovered = MailPluginHost.discover(context)
        val target = discovered.firstOrNull { it.pluginInfo != null }
            ?: throw AssertionError("no usable mail plugin: ${discovered.map { "${it.serviceInfo.packageName}: ${it.error?.message}" }}")
        val packageName = target.serviceInfo.packageName
        val info = requireNotNull(target.pluginInfo)
        Log.i(TAG, "plugin $packageName ${info.versionName} (${info.versionCode}) capabilities: ${info.capabilities}")

        FakeImapServer().use { server ->
            server.start()
            val account = MailAccountSpec.inline(
                """{"address":"alice@localhost","user":"alice",""" +
                    """"imap":{"host":"127.0.0.1","port":${server.port},"tls":"none"},""" +
                    """"timeout":{"connect":5000,"read":20000}}""",
                password = SECRET,
            )
            val client = MailPluginHost.newClient(context, account)
            try {
                // Phase 1: open -> session.test -> ok over the real Binder.
                val started = System.currentTimeMillis()
                val first = JSONObject(client.call(MailContract.OP_SESSION_TEST))
                Log.i(TAG, "session.test #1 in ${System.currentTimeMillis() - started} ms: $first")
                assertTrue(first.toString(), first.getBoolean("ok"))
                val imap = first.getJSONObject("imap")
                assertTrue(imap.toString(), imap.getBoolean("ok"))
                assertEquals(server.port, imap.getInt("port"))
                assertFalse("the secret never enters a result", first.toString().contains(SECRET))
                assertEquals(1, client.openCount)
                assertTrue(client.isOpen)
                val status = requireNotNull(client.status()) { "an open session answers getStatus" }
                assertEquals(MailContract.STATE_OPEN, status.state)
                // The probe logs in and keeps the connection for the session (no LOGOUT until close).
                val commands = server.sessions.single().commands
                assertTrue(commands.toString(), "CAPABILITY" in commands && "LOGIN" in commands)

                // Phase 2: the plugin process dies while a call is blocked on the server.
                server.holdConnections = true
                val inflight = async(Dispatchers.IO) { runCatching { client.call(MailContract.OP_SESSION_TEST) } }
                assertTrue("the plugin must block on the server", server.awaitBlocked(15_000L))
                val killed = System.currentTimeMillis()
                forceStop(packageName)
                val outcome = withTimeout(30_000L) { inflight.await() }
                val failure = outcome.exceptionOrNull()
                Log.i(TAG, "in-flight call ${System.currentTimeMillis() - killed} ms after force-stop: ${failure ?: outcome.getOrNull()}")
                assertTrue("the in-flight call must fail with a mail error: $outcome", failure is MailPluginException)
                assertEquals(MailErrorCodes.SESSION_CLOSED, (failure as MailPluginException).code)
                assertTrue(failure.retryable)
                assertFalse(client.isOpen)
                assertEquals(MailErrorCodes.SESSION_CLOSED, client.lastCloseReason?.code)
                assertNull("a dead session has no status", client.status())

                // Phase 3: the next call reopens the session without any caller involvement.
                server.holdConnections = false
                server.releaseHeld()
                val reopening = System.currentTimeMillis()
                val second = JSONObject(client.call(MailContract.OP_SESSION_TEST))
                Log.i(TAG, "session.test #2 after the reopen in ${System.currentTimeMillis() - reopening} ms: $second")
                assertTrue(second.toString(), second.getBoolean("ok"))
                assertEquals("the second call must reopen the session", 2, client.openCount)
                assertTrue(client.isOpen)

                // Phase 4: a death while idle is reported through the death recipient as well.
                forceStop(packageName)
                assertTrue("the death recipient must mark the session", awaitCondition(10_000L) { !client.isOpen })
                assertEquals(MailErrorCodes.SESSION_CLOSED, client.lastCloseReason?.code)
                val third = JSONObject(client.call(MailContract.OP_SESSION_TEST))
                assertTrue(third.toString(), third.getBoolean("ok"))
                assertEquals(3, client.openCount)
                assertEquals("one connection per opened session", 3, server.sessions.size)
                assertTrue(server.sessions.all { "LOGIN" in it.commands })
                Log.i(TAG, "round trip complete: ${client.openCount} sessions opened, ${server.sessions.size} IMAP connections served")
            } finally {
                client.close()
            }
        }
    }

    private fun forceStop(packageName: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("am force-stop $packageName")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        Log.i(TAG, "force-stopped $packageName")
    }

    private fun awaitCondition(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) return false
            Thread.sleep(50L)
        }
        return true
    }

    private companion object {
        const val TAG = "MailRoundTripTest"
        const val SECRET = "alice-secret"
    }
}
