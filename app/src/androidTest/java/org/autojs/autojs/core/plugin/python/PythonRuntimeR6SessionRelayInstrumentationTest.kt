package org.autojs.autojs.core.plugin.python

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.python.runtime.api.IPythonExecutionCallback
import org.autojs.plugin.python.runtime.api.IPythonExecutionSession
import org.autojs.plugin.python.runtime.api.IPythonSessionOpenCallback
import org.autojs.plugin.python.runtime.api.PythonExecutionRequest
import org.autojs.plugin.python.runtime.api.PythonExecutionResult
import org.autojs.plugin.python.runtime.api.PythonPayloadKind
import org.autojs.plugin.python.runtime.api.PythonPayloadReference
import org.autojs.plugin.python.runtime.api.PythonRequestId
import org.autojs.plugin.python.runtime.api.PythonRuntimeCodec
import org.autojs.plugin.python.runtime.api.PythonRuntimeNegotiation
import org.autojs.plugin.python.runtime.api.PythonRuntimeValidation
import org.autojs.plugin.python.runtime.api.PythonSha256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * R6 test-only proof that an admitted session Binder cannot be relayed to another application UID.
 *
 * The relay target sends every oneway session operation. Since oneway Binder calls cannot return a
 * provider-side SecurityException, rejection is proved by lack of callbacks followed by one normal
 * owner-UID start/result on the same still-CREATED session.
 */
class PythonRuntimeR6SessionRelayInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @Test
    fun alternateUidCannotMutateRelayedSessionAndOwnerStillCompletesExactlyOnce() {
        assumeTrue(
            "R6 session-relay probe is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED).toBoolean(),
        )
        assertEquals(HOST_PACKAGE, context.packageName)
        assertEquals(
            "Relay fixture must share the candidate signer so the host can enter its protected service",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, RELAY_PACKAGE),
        )
        assertEquals(
            "Runtime plugin must share the candidate signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, PLUGIN_PACKAGE),
        )
        assertTrue(
            "Relay fixture must own a UID distinct from the host",
            packageUid(RELAY_PACKAGE) != context.applicationInfo.uid,
        )

        val transportFailure = AtomicReference<Throwable?>()
        val transport = PythonRuntimeOfficialTransport(context)
        val installed = requireNotNull(transport.inspect()) { "Exact Python Runtime provider is missing" }
        assertTrue(
            "Exact Python Runtime provider is disabled",
            installed.pluginEnabled && installed.applicationEnabled && installed.serviceEnabled,
        )
        assertTrue("Exact Python Runtime provider signer differs from host", installed.sameSignerAsHost)
        val binding = transport.bind(
            timeoutMillis = BIND_TIMEOUT_MILLIS,
            cancelled = { false },
            onConnectionLost = { error -> transportFailure.compareAndSet(null, error) },
        )

        val sourceFile = File.createTempFile("python-r6-session-relay-", ".py", context.cacheDir)
        var relayBound = false
        var relayConnection: RelayConnection? = null
        var session: IPythonExecutionSession? = null
        try {
            val provider = binding.provider()
            val runtimeInfo = PythonRuntimeCodec.decodeRuntimeInfo(provider.runtimeInfo)
            val capabilities = PythonRuntimeCodec.decodeCapabilities(provider.capabilities)
            val protocol = PythonRuntimeNegotiation.requireNegotiated(runtimeInfo)
            val source = SOURCE.toByteArray(Charsets.UTF_8)
            sourceFile.writeBytes(source)
            val request = PythonExecutionRequest(
                requestId = PythonRequestId.fromUuid(UUID.randomUUID()),
                protocolVersion = protocol,
                entryPoint = ENTRY_POINT,
                source = PythonPayloadReference(
                    kind = PythonPayloadKind.SOURCE,
                    descriptorIndex = 0,
                    declaredLengthBytes = source.size.toLong(),
                    sha256 = PythonSha256.digest(source),
                ),
                timeoutMillis = SESSION_TIMEOUT_MILLIS,
                maxOutputBytes = MAX_OUTPUT_BYTES,
                maxOutputChunkBytes = MAX_OUTPUT_CHUNK_BYTES,
                maxOutputChunks = MAX_OUTPUT_CHUNKS,
            )
            PythonRuntimeValidation.validateRequestAgainst(
                request = request,
                capabilities = capabilities,
                negotiatedVersion = protocol,
                descriptorCount = 1,
            )

            val opened = CountDownLatch(1)
            val terminal = CountDownLatch(1)
            val openedSession = AtomicReference<IPythonExecutionSession?>()
            val admissionFailure = AtomicReference<String?>()
            val executionFailure = AtomicReference<String?>()
            val startedCount = AtomicInteger(0)
            val resultCount = AtomicInteger(0)
            val otherCallbackCount = AtomicInteger(0)
            val result = AtomicReference<PythonExecutionResult?>()
            val callbackUids = mutableListOf<Int>()

            val admissionCallback = object : IPythonSessionOpenCallback.Stub() {
                override fun onOpened(value: IPythonExecutionSession?) {
                    synchronized(callbackUids) { callbackUids += Binder.getCallingUid() }
                    openedSession.set(value)
                    opened.countDown()
                }

                override fun onRejected(error: ByteArray?) {
                    synchronized(callbackUids) { callbackUids += Binder.getCallingUid() }
                    admissionFailure.set("provider rejected session: ${error?.size ?: -1} bytes")
                    opened.countDown()
                }
            }
            val executionCallback = object : NonInteractivePythonExecutionCallback() {
                override fun onStarted(metadata: ByteArray?) {
                    synchronized(callbackUids) { callbackUids += Binder.getCallingUid() }
                    requireNotNull(metadata) { "Session started metadata is null" }
                    startedCount.incrementAndGet()
                }

                override fun onStdout(chunk: ByteArray?) {
                    synchronized(callbackUids) { callbackUids += Binder.getCallingUid() }
                    otherCallbackCount.incrementAndGet()
                }

                override fun onStderr(chunk: ByteArray?) {
                    synchronized(callbackUids) { callbackUids += Binder.getCallingUid() }
                    otherCallbackCount.incrementAndGet()
                }

                override fun onResult(
                    encoded: ByteArray?,
                    descriptors: Array<out ParcelFileDescriptor?>?,
                ) {
                    synchronized(callbackUids) { callbackUids += Binder.getCallingUid() }
                    val received = descriptors?.filterNotNull().orEmpty()
                    try {
                        result.set(
                            PythonRuntimeCodec.decodeExecutionResult(
                                requireNotNull(encoded) { "Execution result is null" },
                            ),
                        )
                        resultCount.incrementAndGet()
                    } catch (error: Throwable) {
                        executionFailure.compareAndSet(null, error.toString())
                    } finally {
                        received.forEach { descriptor -> runCatching { descriptor.close() } }
                        terminal.countDown()
                    }
                }

                override fun onFailed(error: ByteArray?) {
                    synchronized(callbackUids) { callbackUids += Binder.getCallingUid() }
                    executionFailure.compareAndSet(null, "provider failed execution: ${error?.size ?: -1} bytes")
                    terminal.countDown()
                }

                override fun onCancelled(cancellation: ByteArray?) {
                    synchronized(callbackUids) { callbackUids += Binder.getCallingUid() }
                    executionFailure.compareAndSet(
                        null,
                        "provider cancelled execution: ${cancellation?.size ?: -1} bytes",
                    )
                    terminal.countDown()
                }
            }

            ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                provider.openSession(
                    PythonRuntimeCodec.encodeExecutionRequest(request),
                    arrayOf(descriptor),
                    admissionCallback,
                    executionCallback,
                )
            }
            assertTrue("Timed out waiting for session admission", opened.await(OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertNull(admissionFailure.get(), admissionFailure.get())
            session = requireNotNull(openedSession.get()) { "Provider admitted a null session" }

            val exactRelayConnection = RelayConnection()
            relayConnection = exactRelayConnection
            relayBound = context.bindService(
                Intent().setComponent(ComponentName(RELAY_PACKAGE, RELAY_SERVICE)),
                exactRelayConnection,
                Context.BIND_AUTO_CREATE,
            )
            assertTrue("Unable to bind the exact distinct-UID relay fixture", relayBound)
            assertTrue(
                "Timed out waiting for the exact distinct-UID relay fixture",
                exactRelayConnection.connected.await(BIND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
            )
            exactRelayConnection.failure.get()?.let { error ->
                throw AssertionError("Relay binding failed").apply { initCause(error) }
            }
            val relayedOperations = relaySession(
                requireNotNull(exactRelayConnection.binder.get()) { "Relay fixture returned no Binder" },
                requireNotNull(session).asBinder(),
            )
            assertEquals(EXPECTED_RELAY_OPERATION_COUNT, relayedOperations)

            assertFalse(
                "Alternate UID changed the session or caused a terminal callback",
                terminal.await(RELAY_SETTLE_MILLIS, TimeUnit.MILLISECONDS),
            )
            assertEquals("Alternate UID started the session", 0, startedCount.get())
            assertEquals("Alternate UID completed the session", 0, resultCount.get())
            assertEquals("Alternate UID caused output", 0, otherCallbackCount.get())
            assertNull(executionFailure.get(), executionFailure.get())

            // The owner call is sent only after every relayed oneway operation has had a bounded
            // settling window. Success proves start/invalid-credit/cancel/close had no effect.
            requireNotNull(session).start()
            assertTrue("Owner UID did not receive a terminal callback", terminal.await(SESSION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertNull(executionFailure.get(), executionFailure.get())
            assertEquals("Owner UID did not receive exactly one started callback", 1, startedCount.get())
            assertEquals("Owner UID did not receive exactly one result callback", 1, resultCount.get())
            assertEquals("No-output source unexpectedly emitted output", 0, otherCallbackCount.get())
            val completedResult = result.get()
            assertNotNull("Owner execution returned no result", completedResult)
            assertEquals("Owner execution returned non-zero", 0, requireNotNull(completedResult).exitCode)
            assertTrue(
                "Callback UID drifted from the pinned provider UID: $callbackUids",
                synchronized(callbackUids) { callbackUids.isNotEmpty() && callbackUids.all { it == installed.packageUid } },
            )
            assertNull(transportFailure.get()?.toString(), transportFailure.get())
        } finally {
            runCatching { session?.close() }
            if (relayBound) {
                relayConnection?.let { connection ->
                    runCatching { context.unbindService(connection) }
                }
            }
            binding.close()
            check(!sourceFile.exists() || sourceFile.delete()) {
                "Unable to remove R6 relay source snapshot: $sourceFile"
            }
        }
    }

    private fun relaySession(relay: IBinder, session: IBinder): Int {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(RELAY_DESCRIPTOR)
            data.writeStrongBinder(session)
            assertTrue(
                "Relay fixture rejected the exact probe transaction",
                relay.transact(RELAY_TRANSACTION, data, reply, 0),
            )
            reply.readException()
            reply.readInt()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun packageUid(packageName: String): Int =
        context.packageManager.getApplicationInfo(packageName, 0).uid

    private class RelayConnection : ServiceConnection {
        val connected = CountDownLatch(1)
        val binder = AtomicReference<IBinder?>()
        val failure = AtomicReference<Throwable?>()

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder.set(service)
            connected.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            failure.compareAndSet(null, AssertionError("Relay service disconnected: $name"))
            connected.countDown()
        }

        override fun onNullBinding(name: ComponentName?) {
            failure.compareAndSet(null, AssertionError("Relay service returned null binding: $name"))
            connected.countDown()
        }

        override fun onBindingDied(name: ComponentName?) {
            failure.compareAndSet(null, AssertionError("Relay service binding died: $name"))
            connected.countDown()
        }
    }

    private companion object {
        const val ARG_ENABLED = "autojs.python.r6.sessionRelay.enabled"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"
        const val RELAY_PACKAGE = "org.autojs.plugin.python.runtime.consumer"
        const val RELAY_SERVICE = "$RELAY_PACKAGE.SessionRelayProbeService"
        const val RELAY_DESCRIPTOR =
            "org.autojs.plugin.python.runtime.consumer.SessionRelayProbeService"
        const val RELAY_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION
        const val EXPECTED_RELAY_OPERATION_COUNT = 4
        const val ENTRY_POINT = "main.py"
        const val SOURCE = "value = 40 + 2\n"
        const val BIND_TIMEOUT_MILLIS = 10_000L
        const val OPEN_TIMEOUT_SECONDS = 10L
        const val SESSION_TIMEOUT_MILLIS = 10_000L
        const val SESSION_TIMEOUT_SECONDS = 12L
        const val RELAY_SETTLE_MILLIS = 1_000L
        const val MAX_OUTPUT_BYTES = 1_024L
        const val MAX_OUTPUT_CHUNK_BYTES = 256
        const val MAX_OUTPUT_CHUNKS = 4L
    }
}
