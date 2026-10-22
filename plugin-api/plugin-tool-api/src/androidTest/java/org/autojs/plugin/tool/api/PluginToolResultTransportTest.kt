package org.autojs.plugin.tool.api

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Results cross a real process boundary here (MCP roadmap D65): the providers run in the process `:provider` of the test
 * APK, so every report is a oneway transaction into this process's Binder buffer, as it is into the host's. Strings travel
 * as UTF-16, and a oneway transaction gets at most half of the receiver's buffer, shared with its other oneway calls.
 */
@RunWith(AndroidJUnit4::class)
class PluginToolResultTransportTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val connections = ArrayList<ServiceConnection>()

    @After
    fun tearDown() {
        connections.forEach { runCatching { context.unbindService(it) } }
    }

    @Test
    fun theOnewayTransportRefusesWhatUtf8BytesAloneAllowed() {
        val provider = bind(RawTransportProviderService::class.java)
        fun send(units: Int): Bundle {
            val recorder = Recorder()
            val answer = provider.invoke(request(units), emptyArray(), recorder)
            // The report must be taken before the next one, so that the buffer is free again.
            if (answer.getString(TransportResults.TRANSPORT) == "ok") assertEquals(units, recorder.result().resultChars())
            return answer
        }
        val ceiling = send(PluginToolLimits.MAX_RESULT_CHARS)
        assertEquals("ok", ceiling.getString(TransportResults.TRANSPORT))
        val old = send(PluginToolLimits.MAX_RESULT_BYTES)
        assertNotEquals("ok", old.getString(TransportResults.TRANSPORT))
        var fits = ceiling
        var fails = old
        var low = PluginToolLimits.MAX_RESULT_CHARS
        var high = PluginToolLimits.MAX_RESULT_BYTES
        while (high - low > 1) {
            val middle = (low + high) ushr 1
            val answer = send(middle)
            if (answer.getString(TransportResults.TRANSPORT) == "ok") {
                low = middle
                fits = answer
            } else {
                high = middle
                fails = answer
            }
        }
        Log.i(TAG, "largest result that crossed: $low UTF-16 units, Parcel ${fits.getInt(TransportResults.PARCEL_BYTES)} bytes; " +
            "smallest refused: $high units, Parcel ${fails.getInt(TransportResults.PARCEL_BYTES)} bytes (${fails.getString(TransportResults.TRANSPORT)}); " +
            "ceiling ${PluginToolLimits.MAX_RESULT_CHARS} units, Parcel ${ceiling.getInt(TransportResults.PARCEL_BYTES)} bytes")
    }

    @Test
    fun resultsUpToTheCeilingArrive() {
        val provider = bind(TransportProviderService::class.java)
        val result = call(provider, Recorder(), PluginToolLimits.MAX_RESULT_CHARS, textUnits = 1024).result()
        assertEquals(PluginToolLimits.MAX_RESULT_CHARS, result.resultChars())
        assertEquals(1024, result.getString(PluginToolResultKeys.TEXT)!!.length)
    }

    @Test
    fun resultsTheOldCeilingAllowedFailAtOnce() {
        val provider = bind(TransportProviderService::class.java)
        val startedAt = SystemClock.elapsedRealtime()
        val error = call(provider, Recorder(), 300 * 1024).error()
        Log.i(TAG, "300 Ki units: ${error.getString(PluginToolErrorKeys.CODE)} after ${SystemClock.elapsedRealtime() - startedAt} ms")
        assertEquals(PluginToolErrorCodes.LIMIT_EXCEEDED, error.getString(PluginToolErrorKeys.CODE))
    }

    @Test
    fun theCeilingCountsUtf16Units() {
        val provider = bind(TransportProviderService::class.java)
        val error = call(provider, Recorder(), PluginToolLimits.MAX_RESULT_CHARS + 1, textUnits = 1024).error()
        assertEquals(PluginToolErrorCodes.LIMIT_EXCEEDED, error.getString(PluginToolErrorKeys.CODE))
        assertTrue(error.getString(PluginToolErrorKeys.MESSAGE), error.getString(PluginToolErrorKeys.MESSAGE)!!.contains("${PluginToolLimits.MAX_RESULT_CHARS}"))
    }

    @Test
    fun reportsTheHostCannotTakeBecomeErrors() {
        val provider = bind(TransportProviderService::class.java)
        // The held report keeps its transaction, and so most of the oneway space, until it returns.
        val held = Recorder(hold = true)
        call(provider, held, PluginToolLimits.MAX_RESULT_CHARS)
        assertTrue("the held report did not arrive", held.entered.await(10, TimeUnit.SECONDS))
        try {
            val startedAt = SystemClock.elapsedRealtime()
            val error = call(provider, Recorder(), PluginToolLimits.MAX_RESULT_CHARS).error()
            Log.i(TAG, "second result while the first is held: ${error.getString(PluginToolErrorKeys.CODE)} after ${SystemClock.elapsedRealtime() - startedAt} ms: " +
                error.getString(PluginToolErrorKeys.MESSAGE))
            assertEquals(PluginToolErrorCodes.LIMIT_EXCEEDED, error.getString(PluginToolErrorKeys.CODE))
        } finally {
            held.release.countDown()
        }
        assertEquals(PluginToolLimits.MAX_RESULT_CHARS, held.result().resultChars())
    }

    private fun bind(service: Class<*>): IPluginToolProvider {
        val bound = LinkedBlockingQueue<IBinder>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
                bound.put(binder)
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        assertTrue(context.bindService(Intent(context, service), connection, Context.BIND_AUTO_CREATE))
        connections += connection
        val binder = bound.poll(10, TimeUnit.SECONDS) ?: throw AssertionError("${service.simpleName} did not bind")
        assertNull("the provider must run in another process", binder.queryLocalInterface(IPluginToolProvider.DESCRIPTOR))
        return IPluginToolProvider.Stub.asInterface(binder)
    }

    private fun call(provider: IPluginToolProvider, recorder: Recorder, units: Int, textUnits: Int = 0): Recorder {
        val answer = provider.invoke(request(units, textUnits), emptyArray(), recorder)
        assertTrue(answer.getString(PluginToolAcceptKeys.ERROR_MESSAGE), answer.getBoolean(PluginToolAcceptKeys.ACCEPTED))
        return recorder
    }

    private fun request(units: Int, textUnits: Int = 0) = Bundle().apply {
        putInt(PluginToolInvokeKeys.CONTRACT_VERSION, PluginToolContract.CONTRACT_VERSION)
        putString(PluginToolInvokeKeys.INVOCATION_ID, UUID.randomUUID().toString())
        putString(PluginToolInvokeKeys.TOOL, TransportResults.TOOL)
        putString(PluginToolInvokeKeys.ARGUMENTS_JSON, JSONObject().put("units", units).put("textUnits", textUnits).toString())
        putString(PluginToolInvokeKeys.INPUTS_JSON, "[]")
        putString(PluginToolInvokeKeys.OUTPUTS_JSON, "[]")
        putLong(PluginToolInvokeKeys.TIMEOUT_MS, 30_000L)
        putString(PluginToolInvokeKeys.CONSUMER, PluginToolValues.CONSUMER_SCRIPT)
    }

    private fun Bundle.resultChars(): Int = getString(PluginToolResultKeys.RESULT_JSON)!!.length + (getString(PluginToolResultKeys.TEXT)?.length ?: 0)

    /** Records the one report of a call; with [hold] it keeps the Binder thread of onResult until [release]. */
    private class Recorder(private val hold: Boolean = false) : IPluginToolCallback.Stub() {
        private val done = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)

        @Volatile
        private var result: Bundle? = null

        @Volatile
        private var error: Bundle? = null

        override fun onProgress(update: Bundle?) = Unit

        override fun onResult(result: Bundle?, outputs: Array<out ParcelFileDescriptor>?) {
            outputs?.forEach { runCatching { it.close() } }
            this.result = result
            entered.countDown()
            if (hold) release.await(30, TimeUnit.SECONDS)
            done.countDown()
        }

        override fun onError(error: Bundle?) {
            this.error = error
            done.countDown()
        }

        fun result(): Bundle {
            assertTrue("no report within $WAIT_SECONDS s", done.await(WAIT_SECONDS, TimeUnit.SECONDS))
            assertNull("unexpected error ${error?.getString(PluginToolErrorKeys.CODE)}: ${error?.getString(PluginToolErrorKeys.MESSAGE)}", error)
            return result!!
        }

        fun error(): Bundle {
            assertTrue("no report within $WAIT_SECONDS s", done.await(WAIT_SECONDS, TimeUnit.SECONDS))
            assertNull("unexpected result of ${result?.resultCharsOrNull()} UTF-16 units", result)
            return error!!
        }

        private fun Bundle.resultCharsOrNull(): Int = (getString(PluginToolResultKeys.RESULT_JSON)?.length ?: 0) + (getString(PluginToolResultKeys.TEXT)?.length ?: 0)
    }

    private companion object {
        const val TAG = "ToolResultTransport"
        const val WAIT_SECONDS = 10L
    }
}
