package org.autojs.autojs.core.plugin.agent

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.ai.AiProviderComponent
import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityGrant
import org.autojs.plugin.ai.agent.api.AiAgentContract
import org.autojs.plugin.ai.agent.api.IAiAgentModelCallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.UUID

/** Opt-in: installed same-signature test-apps:ai-provider-conformance, no real model or network. */
class AiAgentModelBrokerAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun catalogContainsOnlyPublicFieldsAndStructuredGenerateRoundTrips() {
        requireFake()
        broker().use { broker ->
            val catalog = Events()
            broker.listTargets(request(JsonObject().apply { addProperty("requestId", "catalog") }), catalog)
            val completedCatalog = catalog.awaitTerminal()
            assertEquals("completed", completedCatalog["type"].asString)
            val targets = completedCatalog["targets"].asJsonArray
            assertTrue(targets.size() >= 7)
            targets.forEach { target ->
                assertEquals(setOf("targetId", "displayName", "locality", "capabilityIds", "configured",
                    "available", "maximumContextBytes", "supportedControls"), target.asJsonObject.keySet())
            }
            val events = Events()
            broker.generate(request(generate("local:echo", "hello agent", structured = true)), events)
            val terminal = events.awaitTerminal()
            assertEquals("completed", terminal["type"].asString)
            assertTrue(JsonParser.parseString(terminal["text"].asString).isJsonObject)
            assertEquals(listOf("started", "usage", "completed"), events.types())
            events.assertSequence()
        }
    }

    @Test fun cancellationAfterProviderChunkTerminatesOnce() {
        requireFake()
        broker().use { broker ->
            val input = generate("local:stream", "agent-conformance:stall", stream = true)
            val events = Events()
            broker.generate(request(input), events)
            assertTrue("Provider never acknowledged streaming request", events.ready.await(15, TimeUnit.SECONDS))
            broker.cancel(Bundle().apply {
                putInt(AiAgentContract.KEY_CONTRACT_VERSION, 1)
                putString(AiAgentContract.KEY_MODEL_REF_JSON, JsonObject().apply {
                    addProperty("requestId", input["requestId"].asString)
                }.toString())
            })
            assertEquals("cancelled", events.awaitTerminal()["type"].asString)
            assertEquals(listOf("started", "chunk", "cancelled"), events.types())
            events.assertSequence()
        }
    }

    @Test fun fileAndPipePayloadsCompleteAndPreserveTheFileOffset() {
        requireFake()
        broker().use { broker ->
            for (pipePayload in listOf(false, true)) {
                val value = generate("local:echo", "Payload fixture " + "x".repeat(40_000), structured = true)
                val bytes = value.toString().toByteArray(Charsets.UTF_8)
                val file = java.io.File.createTempFile("agent-payload-", ".json", context.cacheDir)
                var writer: Thread? = null
                val pipe = if (pipePayload) ParcelFileDescriptor.createPipe() else null
                try {
                    val descriptor = if (pipe != null) pipe[0] else {
                        file.outputStream().use { it.write(byteArrayOf(0, 1, 2)); it.write(bytes) }
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).also {
                            android.system.Os.lseek(it.fileDescriptor, 3, android.system.OsConstants.SEEK_SET)
                        }
                    }
                    val events = Events()
                    broker.generate(Bundle().apply {
                        putInt(AiAgentContract.KEY_CONTRACT_VERSION, 1)
                        putParcelable(AiAgentContract.KEY_PAYLOAD_FD, descriptor)
                        putString(AiAgentContract.KEY_MODEL_REF_JSON, """{"requestId":"${value["requestId"].asString}"}""")
                    }, events)
                    if (pipe != null) writer = Thread {
                        runCatching { ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(bytes) } }
                    }.apply { isDaemon = true; start() }
                    val terminal = events.awaitTerminal()
                    assertEquals("completed", terminal["type"].asString)
                    assertTrue(JsonParser.parseString(terminal["text"].asString).isJsonObject)
                    assertTrue(runCatching { descriptor.fd }.isFailure)
                    events.assertSequence()
                } finally {
                    pipe?.forEach { runCatching { it.close() } }
                    writer?.join(2_000)
                    file.delete()
                }
            }
        }
    }

    @Test fun stalledProviderTimesOutWithoutLeakingDiagnostics() {
        requireFake()
        broker().use { broker ->
            val events = Events()
            broker.generate(request(generate("local:stream", "agent-conformance:stall", stream = true).apply {
                addProperty("timeoutMs", 5_000)
            }), events)
            assertTrue("Provider never acknowledged the timed request", events.ready.await(4, TimeUnit.SECONDS))
            val terminal = events.awaitTerminal()
            assertEquals("failed", terminal["type"].asString)
            assertEquals(AiAgentContract.ERROR_MODEL_TIMEOUT, terminal["code"].asString)
            events.assertSequence()
        }
    }

    @Test fun anUnreadPayloadPipeCannotDelayCancellationOfAnActiveRequest() {
        requireFake()
        broker().use { broker ->
            val input = generate("local:stream", "agent-conformance:stall", stream = true)
            val events = Events()
            broker.generate(request(input), events)
            assertTrue(events.ready.await(15, TimeUnit.SECONDS))
            val pipe = ParcelFileDescriptor.createPipe()
            try {
                // Keep the write end open without sending a JSON body. A second request now owns
                // the worker's payload-reader lane, while the first already owns a Provider call.
                broker.generate(Bundle().apply {
                    putInt(AiAgentContract.KEY_CONTRACT_VERSION, 1)
                    putParcelable(AiAgentContract.KEY_PAYLOAD_FD, pipe[0])
                    putString(AiAgentContract.KEY_MODEL_REF_JSON, """{"requestId":"blocked-pipe"}""")
                }, Events())
                // More than a Linux pipe's capacity: completion of this write proves the worker
                // consumed bytes and is now awaiting the rest of the body, without timing sleeps.
                android.system.Os.write(pipe[1].fileDescriptor, ByteArray(128 * 1024) { 32 }, 0, 128 * 1024)
                val before = SystemClock.elapsedRealtime()
                broker.cancel(Bundle().apply {
                    putInt(AiAgentContract.KEY_CONTRACT_VERSION, 1)
                    putString(AiAgentContract.KEY_MODEL_REF_JSON,
                        """{"requestId":"${input["requestId"].asString}"}""")
                })
                assertEquals("cancelled", events.awaitTerminal()["type"].asString)
                assertTrue("Cancellation waited for the unrelated pipe deadline", SystemClock.elapsedRealtime() - before < 2_000)
            } finally { pipe.forEach { runCatching { it.close() } } }
        }
    }

    @Test fun payloadReferenceAllowsCancellationBeforeAnyBodyBytesAndRejectsMismatches() {
        broker().use { broker ->
            val pipe = ParcelFileDescriptor.createPipe()
            try {
                val events = Events()
                broker.generate(Bundle().apply {
                    putInt(AiAgentContract.KEY_CONTRACT_VERSION, 1)
                    putParcelable(AiAgentContract.KEY_PAYLOAD_FD, pipe[0])
                    putString(AiAgentContract.KEY_MODEL_REF_JSON, """{"requestId":"cancel-before-body"}""")
                }, events)
                broker.cancel(Bundle().apply {
                    putInt(AiAgentContract.KEY_CONTRACT_VERSION, 1)
                    putString(AiAgentContract.KEY_MODEL_REF_JSON, """{"requestId":"cancel-before-body"}""")
                })
                assertEquals("cancelled", events.awaitTerminal()["type"].asString)
                assertEquals("cancel-before-body", events.values.last()["requestId"].asString)
                // Closing the receiver's descriptor on cancellation prevents a delayed sender
                // from supplying a valid body and reviving the cancelled Provider operation.
                assertThrows(android.system.ErrnoException::class.java) {
                    android.system.Os.write(pipe[1].fileDescriptor, byteArrayOf(123), 0, 1)
                }
            } finally { pipe.forEach { runCatching { it.close() } } }
            val file = java.io.File.createTempFile("agent-request-", ".json", context.cacheDir)
            try {
                file.writeText(generate("local:echo", "private").toString())
                val events = Events()
                broker.generate(Bundle().apply {
                    putInt(AiAgentContract.KEY_CONTRACT_VERSION, 1)
                    putParcelable(AiAgentContract.KEY_PAYLOAD_FD,
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
                    putString(AiAgentContract.KEY_MODEL_REF_JSON, """{"requestId":"mismatched-header"}""")
                }, events)
                assertEquals(AiAgentContract.ERROR_INVALID_REQUEST, events.awaitTerminal()["code"].asString)
                assertEquals("mismatched-header", events.values.last()["requestId"].asString)
            } finally { file.delete() }
        }
    }

    @Test fun providerFailureIsSanitizedAndUnsupportedTargetIsRejected() {
        requireFake()
        broker().use { broker ->
            val failed = Events()
            broker.generate(request(generate("local:echo", "agent-conformance:fail")), failed)
            val terminal = failed.awaitTerminal()
            assertEquals(AiAgentContract.ERROR_MODEL_FAILED, terminal["code"].asString)
            assertEquals("PROVIDER_FAILED", terminal["reason"].asString)
            assertFalse(failed.values.joinToString().contains("Private conformance"))
            val unsupported = Events()
            broker.generate(request(generate("local:reasoning", "hello", structured = true)), unsupported)
            assertEquals(AiAgentContract.ERROR_TARGET_UNSUPPORTED, unsupported.awaitTerminal()["code"].asString)
        }
    }

    @Test fun foreignUidAndDestroyedLinkCannotReachProvider() {
        AiAgentModelBroker(context, Process.myUid() + 1, grant()).use { broker ->
            assertThrows(SecurityException::class.java) { broker.getBrokerInfo() }
            assertThrows(SecurityException::class.java) { broker.generate(request(generate("local:echo", "x")), Events()) }
        }
        val broker = broker()
        broker.close()
        val events = Events()
        broker.generate(request(generate("local:echo", "x")), events)
        assertEquals(AiAgentContract.ERROR_LINK_DETACHED, events.awaitTerminal()["code"].asString)
    }

    @Test fun unexpectedDescriptorsAreClosedOnRejectionNullCallbackAndForeignUid() {
        fun incoming(key: String): Pair<Bundle, ParcelFileDescriptor> {
            val descriptor = ParcelFileDescriptor.open(java.io.File("/dev/null"), ParcelFileDescriptor.MODE_READ_ONLY)
            return request(generate("local:echo", "x")).apply { putParcelable(key, descriptor) } to descriptor
        }
        fun assertClosed(descriptor: ParcelFileDescriptor) {
            assertTrue("The receiver must close transferred descriptors", runCatching { descriptor.fd }.isFailure)
        }
        broker().use { broker ->
            for (key in listOf("unexpectedFd", AiAgentContract.KEY_MODEL_REQUEST_JSON)) {
                val (request, descriptor) = incoming(key)
                val events = Events()
                broker.generate(request, events)
                assertEquals(AiAgentContract.ERROR_INVALID_REQUEST, events.awaitTerminal()["code"].asString)
                assertClosed(descriptor)
            }
            val (withoutCallback, descriptor) = incoming("unexpectedFd")
            broker.generate(withoutCallback, null)
            assertClosed(descriptor)
            val (reason, reasonDescriptor) = incoming("unexpectedFd")
            broker.destroy(reason)
            assertClosed(reasonDescriptor)
        }
        AiAgentModelBroker(context, Process.myUid() + 1, grant()).use { broker ->
            val (request, descriptor) = incoming("unexpectedFd")
            assertThrows(SecurityException::class.java) { broker.generate(request, Events()) }
            assertClosed(descriptor)
        }
        // A correctly placed descriptor remains open until the accepted request is cancelled.
        broker().use { broker ->
            val pipe = ParcelFileDescriptor.createPipe()
            try {
                val events = Events()
                broker.generate(Bundle().apply {
                    putInt(AiAgentContract.KEY_CONTRACT_VERSION, 1)
                    putParcelable(AiAgentContract.KEY_PAYLOAD_FD, pipe[0])
                    putString(AiAgentContract.KEY_MODEL_REF_JSON, """{"requestId":"accepted-fd"}""")
                }, events)
                assertTrue(pipe[0].fd >= 0)
                broker.cancel(Bundle().apply {
                    putInt(AiAgentContract.KEY_CONTRACT_VERSION, 1)
                    putString(AiAgentContract.KEY_MODEL_REF_JSON, """{"requestId":"accepted-fd"}""")
                })
                assertEquals("cancelled", events.awaitTerminal()["type"].asString)
                assertClosed(pipe[0])
            } finally { pipe.forEach { runCatching { it.close() } } }
        }
    }

    private fun broker() = AiAgentModelBroker(context, Process.myUid(), grant(),
        AiProviderComponent(FAKE_PACKAGE, "$FAKE_PACKAGE.service.FakeAiProviderService"), "fake.local")
    private fun grant() = HostCapabilityGrant(emptySet(), emptySet())

    private fun generate(target: String, prompt: String, structured: Boolean = false, stream: Boolean = false) =
        JsonParser.parseString("""{"messages":[{"role":"user","content":""}]}""").asJsonObject.apply {
            addProperty("requestId", UUID.randomUUID().toString())
            addProperty("targetId", target)
            getAsJsonArray("messages")[0].asJsonObject.addProperty("content", prompt)
            addProperty("structuredJson", structured)
            addProperty("stream", stream)
            addProperty("timeoutMs", 15_000)
        }

    private fun request(value: JsonObject) = Bundle().apply {
        putInt(AiAgentContract.KEY_CONTRACT_VERSION, 1)
        putString(AiAgentContract.KEY_MODEL_REQUEST_JSON, value.toString())
    }

    private fun requireFake() {
        assumeTrue("Enable autojs.aiAgent.model.conformance.enabled for fake Provider device checks",
            InstrumentationRegistry.getArguments().getString("autojs.aiAgent.model.conformance.enabled") == "true")
        assertTrue("Install test-apps:ai-provider-conformance first", runCatching {
            context.packageManager.getPackageInfo(FAKE_PACKAGE, 0)
        }.isSuccess)
        assertEquals(android.content.pm.PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, FAKE_PACKAGE))
    }

    private class Events : IAiAgentModelCallback.Stub() {
        val values = CopyOnWriteArrayList<JsonObject>()
        val ready = CountDownLatch(1)
        private val terminal = CountDownLatch(1)
        override fun onEvent(event: Bundle?) {
            requireNotNull(event)
            @Suppress("DEPRECATION")
            val descriptor = event.getParcelable<ParcelFileDescriptor>(AiAgentContract.KEY_PAYLOAD_FD)
            val text = descriptor?.let { ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { it.readText() } }
                ?: requireNotNull(event.getString(AiAgentContract.KEY_MODEL_EVENT_JSON))
            val value = JsonParser.parseString(text).asJsonObject
            values += value
            if (value["type"].asString == "chunk") ready.countDown()
            if (value["type"].asString in TERMINAL) terminal.countDown()
        }
        fun awaitTerminal(): JsonObject {
            assertTrue("Model broker did not terminate", terminal.await(20, TimeUnit.SECONDS))
            // Allow late Provider callbacks/cancellation to expose an accidental second terminal.
            Thread.sleep(150)
            val terminals = values.filter { it["type"].asString in TERMINAL }
            assertEquals(1, terminals.size)
            return terminals.single()
        }
        fun types() = values.map { it["type"].asString }
        fun assertSequence() = assertEquals((1..values.size).map(Int::toLong), values.map { it["sequence"].asLong })
    }

    companion object {
        private const val FAKE_PACKAGE = "org.autojs.plugin.ai.provider.fake"
        private val TERMINAL = setOf("completed", "failed", "cancelled")
    }
}
