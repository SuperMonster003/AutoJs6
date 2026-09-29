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
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentModelCallback
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract
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
class ThreeStoveAgentModelBrokerAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun visionInitialPngAndJpegReachTheProviderThroughOwnedDescriptors() {
        requireFake()
        for (jpeg in listOf(false, true)) broker().use { broker ->
            val info = JsonParser.parseString(broker.brokerInfo.getString(ThreeStoveAgentContract.KEY_MODEL_BROKER_INFO_JSON)).asJsonObject
            assertEquals(1, info[ThreeStoveAgentContract.MODEL_INFO_VISION_VERSION].asInt)
            val bytes = imageBytes(jpeg)
            val descriptor = imageDescriptor(bytes)
            val input = generate("local:echo", "Inspect the synthetic image").apply {
                addProperty("maximumOutputTokens", 1024)
                add("imageRefs", com.google.gson.JsonArray().apply { add(imageRef(bytes, initial = true, jpeg = jpeg)) })
            }
            val events = Events()
            broker.generate(request(input).apply { putParcelableArray(ThreeStoveAgentContract.KEY_MODEL_IMAGE_FDS, arrayOf(descriptor)) }, events)
            val terminal = events.awaitTerminal()
            assertEquals(terminal.toString(), "completed", terminal["type"].asString)
            assertEquals("vision:images=1", terminal["text"].asString)
            assertTrue(runCatching { descriptor.fd }.isFailure)
            events.assertSequence()
        }
    }

    @Test fun visionNativeToolsResumeTheSameSessionWithImageResults() {
        requireFake()
        for (stream in listOf(false, true)) broker().use { broker ->
            val input = nativeRequest(stream).apply { addProperty("vision", true) }
            val events = Events()
            broker.generate(request(input), events)
            val result = resultsFor(events.awaitTools())
            val bytes = imageBytes(false)
            result["results"].asJsonArray.single().asJsonObject.add("imageRefs", com.google.gson.JsonArray().apply { add(imageRef(bytes)) })
            val descriptor = imageDescriptor(bytes)
            broker.submitToolResults(request(result).apply { putParcelableArray(ThreeStoveAgentContract.KEY_MODEL_IMAGE_FDS, arrayOf(descriptor)) })
            val terminal = events.awaitTerminal()
            assertEquals(terminal.toString(), "completed", terminal["type"].asString)
            assertEquals("tool:observed:images=1", terminal["text"].asString)
            assertTrue(runCatching { descriptor.fd }.isFailure)
            events.assertSequence()
        }
    }

    @Test fun visionInvalidBytesDimensionsDigestAndDescriptorCountsFailClosed() {
        requireFake()
        for (scenario in listOf("digest", "dimension", "invalid-image", "length", "missing", "extra")) broker().use { broker ->
            val bytes = if (scenario == "invalid-image") "not an image".toByteArray() else imageBytes(false)
            val metadata = imageRef(bytes, initial = true).apply {
                if (scenario == "digest") addProperty("sha256", "a".repeat(64))
                if (scenario == "dimension") addProperty("width", 31)
                if (scenario == "length") addProperty("byteLength", bytes.size + 1)
            }
            val descriptors = when (scenario) {
                "missing" -> emptyArray()
                "extra" -> arrayOf(imageDescriptor(bytes), imageDescriptor(bytes))
                else -> arrayOf(imageDescriptor(bytes))
            }
            val input = generate("local:echo", "private-image-prompt").apply {
                addProperty("maximumOutputTokens", 1024)
                add("imageRefs", com.google.gson.JsonArray().apply { add(metadata) })
            }
            val events = Events()
            broker.generate(request(input).apply { putParcelableArray(ThreeStoveAgentContract.KEY_MODEL_IMAGE_FDS, descriptors) }, events)
            val terminal = events.awaitTerminal()
            assertEquals(scenario + terminal, ThreeStoveAgentContract.ERROR_INVALID_REQUEST, terminal["code"].asString)
            broker.close()
            assertTrue(descriptors.all { runCatching { it.fd }.isFailure })
            assertFalse(events.values.joinToString().contains("private-image-prompt"))
            events.assertSequence()
        }
    }

    @Test fun visionUnsupportedTargetRejectsBeforeReadingAnOpenImagePipe() {
        requireFake()
        broker().use { broker ->
            val pipe = ParcelFileDescriptor.createPipe()
            try {
                val input = generate("local:reasoning", "look").apply {
                    add("imageRefs", com.google.gson.JsonArray().apply { add(imageRef(imageBytes(false), initial = true)) })
                }
                val events = Events()
                broker.generate(request(input).apply { putParcelableArray(ThreeStoveAgentContract.KEY_MODEL_IMAGE_FDS, arrayOf(pipe[0])) }, events)
                assertEquals(ThreeStoveAgentContract.ERROR_TARGET_UNSUPPORTED, events.awaitTerminal()["code"].asString)
                broker.close()
                assertTrue(runCatching { pipe[0].fd }.isFailure)
            } finally { pipe.forEach { runCatching { it.close() } } }
        }
    }

    @Test fun visionImagePipeReadsRemainCancellableAndKeepTheOriginalDeadline() {
        requireFake()
        for (cancel in listOf(true, false)) broker().use { broker ->
            val pipe = ParcelFileDescriptor.createPipe()
            try {
                val input = generate("local:echo", "look").apply {
                    addProperty("maximumOutputTokens", 1024)
                    addProperty("timeoutMs", if (cancel) 15000 else 4000)
                    add("imageRefs", com.google.gson.JsonArray().apply { add(imageRef(imageBytes(false), initial = true).apply {
                        addProperty("byteLength", 256 * 1024)
                    }) })
                }
                val events = Events()
                broker.generate(request(input).apply { putParcelableArray(ThreeStoveAgentContract.KEY_MODEL_IMAGE_FDS, arrayOf(pipe[0])) }, events)
                // A write larger than pipe capacity proves the reader is active and awaiting EOF.
                android.system.Os.write(pipe[1].fileDescriptor, ByteArray(128 * 1024), 0, 128 * 1024)
                val before = SystemClock.elapsedRealtime()
                if (cancel) broker.cancel(Bundle().apply {
                    putInt(ThreeStoveAgentContract.KEY_CONTRACT_VERSION, 1)
                    putString(ThreeStoveAgentContract.KEY_MODEL_REF_JSON, """{"requestId":"${input["requestId"].asString}"}""")
                })
                val terminal = events.awaitTerminal()
                assertEquals(if (cancel) ThreeStoveAgentContract.ERROR_CANCELLED else ThreeStoveAgentContract.ERROR_MODEL_TIMEOUT, terminal["code"].asString)
                if (cancel) assertTrue(SystemClock.elapsedRealtime() - before < 2000)
                broker.close()
                assertTrue(runCatching { pipe[0].fd }.isFailure)
                events.assertSequence()
            } finally { pipe.forEach { runCatching { it.close() } } }
        }
    }

    @Test fun visionDescriptorArraysAreClosedOnBadTypesNullCallbacksAndForeignUid() {
        fun descriptor() = ParcelFileDescriptor.open(java.io.File("/dev/null"), ParcelFileDescriptor.MODE_READ_ONLY)
        broker().use { broker ->
            for (mode in listOf("wrong-type", "nested", "deep-nested", "no-callback")) {
                val fd = descriptor()
                val bundle = request(generate("local:echo", "look")).apply {
                    when (mode) {
                        "wrong-type" -> putParcelableArray(ThreeStoveAgentContract.KEY_MODEL_IMAGE_FDS, arrayOf(fd, Bundle()))
                        "nested" -> putBundle("unknown", Bundle().apply { putParcelableArray("array", arrayOf(fd)) })
                        "deep-nested" -> {
                            var nested = Bundle().apply { putParcelable("fd", fd) }
                            repeat(32) { nested = Bundle().apply { putBundle("child", nested) } }
                            putBundle("unknown", nested)
                        }
                        else -> putParcelableArray(ThreeStoveAgentContract.KEY_MODEL_IMAGE_FDS, arrayOf(fd))
                    }
                }
                val events = Events()
                broker.generate(bundle, if (mode == "no-callback") null else events)
                if (mode != "no-callback") assertEquals(ThreeStoveAgentContract.ERROR_INVALID_REQUEST, events.awaitTerminal()["code"].asString)
                assertTrue(runCatching { fd.fd }.isFailure)
            }
        }
        ThreeStoveAgentModelBroker(context, Process.myUid() + 1, grant()).use { broker ->
            val fd = descriptor()
            assertThrows(SecurityException::class.java) { broker.generate(request(generate("local:echo", "look")).apply {
                putParcelableArray(ThreeStoveAgentContract.KEY_MODEL_IMAGE_FDS, arrayOf(fd))
            }, Events()) }
            assertTrue(runCatching { fd.fd }.isFailure)
        }
    }

    private fun imageBytes(jpeg: Boolean): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(32, 24, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.GREEN)
            return java.io.ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(if (jpeg) android.graphics.Bitmap.CompressFormat.JPEG else android.graphics.Bitmap.CompressFormat.PNG, 70, output))
                output.toByteArray()
            }
        } finally { bitmap.recycle() }
    }

    private fun imageDescriptor(bytes: ByteArray): ParcelFileDescriptor {
        val file = java.io.File.createTempFile("agent-vision-test-", ".bin", context.cacheDir)
        try {
            file.writeBytes(bytes)
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally { file.delete() }
    }

    private fun imageRef(bytes: ByteArray, initial: Boolean = false, jpeg: Boolean = false) = JsonObject().apply {
        addProperty("descriptorIndex", 0)
        addProperty("mimeType", if (jpeg) "image/jpeg" else "image/png")
        addProperty("byteLength", bytes.size)
        addProperty("width", 32); addProperty("height", 24)
        addProperty("sha256", java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) })
        if (initial) addProperty("messageIndex", 0)
    }

    @Test fun nativeToolsRoundTripOnTheOriginalRequestWithAndWithoutStreaming() {
        requireFake()
        for (stream in listOf(false, true)) broker().use { broker ->
            assertEquals(1, JsonParser.parseString(broker.brokerInfo.getString(ThreeStoveAgentContract.KEY_MODEL_BROKER_INFO_JSON))
                .asJsonObject[ThreeStoveAgentContract.MODEL_INFO_TOOL_CALLING_VERSION].asInt)
            val input = nativeRequest(stream)
            val events = Events()
            broker.generate(request(input), events)
            val calls = events.awaitTools()
            assertEquals(input["requestId"], calls["requestId"])
            assertEquals(1, calls["round"].asInt)
            assertTrue(calls["calls"].asJsonArray.single().asJsonObject["arguments"].asJsonObject["fixture"].asBoolean)
            broker.submitToolResults(request(resultsFor(calls)))
            val terminal = events.awaitTerminal()
            assertEquals(terminal.toString(), "completed", terminal["type"].asString)
            assertEquals("tool:observed", terminal["text"].asString)
            assertEquals(1, events.types().count { it == "started" })
            events.assertSequence()
        }
    }

    @Test fun pendingNativeToolsCanBeCancelledAndRejectStaleRoundOrWrongCallIds() {
        requireFake()
        for (scenario in listOf("cancel", "wrong-round", "wrong-id")) broker().use { broker ->
            val input = nativeRequest(false)
            val events = Events()
            broker.generate(request(input), events)
            val calls = events.awaitTools()
            if (scenario == "cancel") broker.cancel(Bundle().apply {
                putInt(ThreeStoveAgentContract.KEY_CONTRACT_VERSION, 1)
                putString(ThreeStoveAgentContract.KEY_MODEL_REF_JSON, """{"requestId":"${input["requestId"].asString}"}""")
            }) else broker.submitToolResults(request(resultsFor(calls).apply {
                if (scenario == "wrong-round") addProperty("round", 2)
                else getAsJsonArray("results")[0].asJsonObject.addProperty("callId", "wrong")
            }))
            val terminal = events.awaitTerminal()
            assertEquals(if (scenario == "cancel") "cancelled" else "failed", terminal["type"].asString)
            assertEquals(if (scenario == "cancel") ThreeStoveAgentContract.ERROR_CANCELLED else ThreeStoveAgentContract.ERROR_INVALID_REQUEST,
                terminal["code"].asString)
            events.assertSequence()
        }
    }

    @Test fun nativeContinuationCannotBypassTheLinkModelCallQuota() {
        requireFake()
        broker(grant().copy(modelCallsPerMinute = 1)).use { broker ->
            val events = Events()
            broker.generate(request(nativeRequest(false)), events)
            broker.submitToolResults(request(resultsFor(events.awaitTools())))
            val terminal = events.awaitTerminal()
            assertEquals("failed", terminal["type"].asString)
            assertEquals(ThreeStoveAgentContract.ERROR_QUOTA_EXCEEDED, terminal["code"].asString)
        }
    }

    @Test fun acceptedToolResultsRestartTheGenerationDeadlineForTheNextRound() {
        requireFake()
        broker().use { broker ->
            val events = Events()
            broker.generate(request(nativeRequest(false).apply { addProperty("timeoutMs", 5000) }), events)
            val calls = events.awaitTools()
            // Submit right before the first request's deadline: the round that follows gets the full timeout again.
            SystemClock.sleep(4_000)
            broker.submitToolResults(request(resultsFor(calls)))
            val terminal = events.awaitTerminal()
            assertEquals(terminal.toString(), "completed", terminal["type"].asString)
            assertEquals("tool:observed", terminal["text"].asString)
        }
    }

    @Test fun nativeToolsKeepTheGenerationDeadlineWhileWaitingForResults() {
        requireFake()
        broker().use { broker ->
            val events = Events()
            broker.generate(request(nativeRequest(false).apply { addProperty("timeoutMs", 5000) }), events)
            events.awaitTools()
            val terminal = events.awaitTerminal()
            assertEquals("failed", terminal["type"].asString)
            assertEquals(ThreeStoveAgentContract.ERROR_MODEL_TIMEOUT, terminal["code"].asString)
        }
    }

    @Test fun nativeResultDescriptorsRetainCorrelationAndCanBeCancelledWhileUnread() {
        requireFake()
        for (mode in listOf("valid", "mismatch", "stalled")) broker().use { broker ->
            val input = nativeRequest(false)
            val events = Events()
            broker.generate(request(input), events)
            val calls = events.awaitTools()
            val value = resultsFor(calls)
            if (mode == "mismatch") value.addProperty("requestId", "another-request")
            val pipe = ParcelFileDescriptor.createPipe()
            try {
                broker.submitToolResults(Bundle().apply {
                    putInt(ThreeStoveAgentContract.KEY_CONTRACT_VERSION, 1)
                    putString(ThreeStoveAgentContract.KEY_MODEL_REF_JSON, """{"requestId":"${input["requestId"].asString}"}""")
                    putParcelable(ThreeStoveAgentContract.KEY_PAYLOAD_FD, pipe[0])
                })
                if (mode == "stalled") {
                    android.system.Os.write(pipe[1].fileDescriptor, ByteArray(128 * 1024) { 32 }, 0, 128 * 1024)
                    val before = SystemClock.elapsedRealtime()
                    broker.cancel(Bundle().apply {
                        putInt(ThreeStoveAgentContract.KEY_CONTRACT_VERSION, 1)
                        putString(ThreeStoveAgentContract.KEY_MODEL_REF_JSON, """{"requestId":"${input["requestId"].asString}"}""")
                    })
                    assertEquals("cancelled", events.awaitTerminal()["type"].asString)
                    assertTrue(SystemClock.elapsedRealtime() - before < 2000)
                } else {
                    ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(value.toString().toByteArray(Charsets.UTF_8)) }
                    val terminal = events.awaitTerminal()
                    assertEquals(if (mode == "valid") "completed" else "failed", terminal["type"].asString)
                    if (mode == "mismatch") assertEquals(ThreeStoveAgentContract.ERROR_INVALID_REQUEST, terminal["code"].asString)
                }
                assertTrue(runCatching { pipe[0].fd }.isFailure)
                events.assertSequence()
            } finally { pipe.forEach { runCatching { it.close() } } }
        }
    }

    private fun nativeRequest(stream: Boolean) = generate("local:tools", "synthetic tool fixture", stream = stream).apply {
        addProperty("maximumToolRounds", 2)
        addProperty("maximumOutputTokens", 1024)
        add("tools", JsonParser.parseString("""[{"name":"ui_read","description":"Read synthetic state","inputSchema":{"type":"object"}}]"""))
    }

    private fun resultsFor(calls: JsonObject) = JsonObject().apply {
        add("requestId", calls["requestId"]); add("round", calls["round"])
        add("results", com.google.gson.JsonArray().apply { calls["calls"].asJsonArray.forEach { call -> add(JsonObject().apply {
            add("callId", call.asJsonObject["callId"]); addProperty("output", "observed")
        }) } })
    }

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
                putInt(ThreeStoveAgentContract.KEY_CONTRACT_VERSION, 1)
                putString(ThreeStoveAgentContract.KEY_MODEL_REF_JSON, JsonObject().apply {
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
                        putInt(ThreeStoveAgentContract.KEY_CONTRACT_VERSION, 1)
                        putParcelable(ThreeStoveAgentContract.KEY_PAYLOAD_FD, descriptor)
                        putString(ThreeStoveAgentContract.KEY_MODEL_REF_JSON, """{"requestId":"${value["requestId"].asString}"}""")
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
            assertEquals(ThreeStoveAgentContract.ERROR_MODEL_TIMEOUT, terminal["code"].asString)
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
                    putInt(ThreeStoveAgentContract.KEY_CONTRACT_VERSION, 1)
                    putParcelable(ThreeStoveAgentContract.KEY_PAYLOAD_FD, pipe[0])
                    putString(ThreeStoveAgentContract.KEY_MODEL_REF_JSON, """{"requestId":"blocked-pipe"}""")
                }, Events())
                // More than a Linux pipe's capacity: completion of this write proves the worker
                // consumed bytes and is now awaiting the rest of the body, without timing sleeps.
                android.system.Os.write(pipe[1].fileDescriptor, ByteArray(128 * 1024) { 32 }, 0, 128 * 1024)
                val before = SystemClock.elapsedRealtime()
                broker.cancel(Bundle().apply {
                    putInt(ThreeStoveAgentContract.KEY_CONTRACT_VERSION, 1)
                    putString(ThreeStoveAgentContract.KEY_MODEL_REF_JSON,
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
                    putInt(ThreeStoveAgentContract.KEY_CONTRACT_VERSION, 1)
                    putParcelable(ThreeStoveAgentContract.KEY_PAYLOAD_FD, pipe[0])
                    putString(ThreeStoveAgentContract.KEY_MODEL_REF_JSON, """{"requestId":"cancel-before-body"}""")
                }, events)
                broker.cancel(Bundle().apply {
                    putInt(ThreeStoveAgentContract.KEY_CONTRACT_VERSION, 1)
                    putString(ThreeStoveAgentContract.KEY_MODEL_REF_JSON, """{"requestId":"cancel-before-body"}""")
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
                    putInt(ThreeStoveAgentContract.KEY_CONTRACT_VERSION, 1)
                    putParcelable(ThreeStoveAgentContract.KEY_PAYLOAD_FD,
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
                    putString(ThreeStoveAgentContract.KEY_MODEL_REF_JSON, """{"requestId":"mismatched-header"}""")
                }, events)
                assertEquals(ThreeStoveAgentContract.ERROR_INVALID_REQUEST, events.awaitTerminal()["code"].asString)
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
            assertEquals(ThreeStoveAgentContract.ERROR_MODEL_FAILED, terminal["code"].asString)
            assertEquals("PROVIDER_FAILED", terminal["reason"].asString)
            assertFalse(failed.values.joinToString().contains("Private conformance"))
            val unsupported = Events()
            broker.generate(request(generate("local:reasoning", "hello", structured = true)), unsupported)
            assertEquals(ThreeStoveAgentContract.ERROR_TARGET_UNSUPPORTED, unsupported.awaitTerminal()["code"].asString)
        }
    }

    @Test fun foreignUidAndDestroyedLinkCannotReachProvider() {
        ThreeStoveAgentModelBroker(context, Process.myUid() + 1, grant()).use { broker ->
            assertThrows(SecurityException::class.java) { broker.getBrokerInfo() }
            assertThrows(SecurityException::class.java) { broker.generate(request(generate("local:echo", "x")), Events()) }
        }
        val broker = broker()
        broker.close()
        val events = Events()
        broker.generate(request(generate("local:echo", "x")), events)
        assertEquals(ThreeStoveAgentContract.ERROR_LINK_DETACHED, events.awaitTerminal()["code"].asString)
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
            for (key in listOf("unexpectedFd", ThreeStoveAgentContract.KEY_MODEL_REQUEST_JSON)) {
                val (request, descriptor) = incoming(key)
                val events = Events()
                broker.generate(request, events)
                assertEquals(ThreeStoveAgentContract.ERROR_INVALID_REQUEST, events.awaitTerminal()["code"].asString)
                assertClosed(descriptor)
            }
            val (withoutCallback, descriptor) = incoming("unexpectedFd")
            broker.generate(withoutCallback, null)
            assertClosed(descriptor)
            val (reason, reasonDescriptor) = incoming("unexpectedFd")
            broker.destroy(reason)
            assertClosed(reasonDescriptor)
        }
        ThreeStoveAgentModelBroker(context, Process.myUid() + 1, grant()).use { broker ->
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
                    putInt(ThreeStoveAgentContract.KEY_CONTRACT_VERSION, 1)
                    putParcelable(ThreeStoveAgentContract.KEY_PAYLOAD_FD, pipe[0])
                    putString(ThreeStoveAgentContract.KEY_MODEL_REF_JSON, """{"requestId":"accepted-fd"}""")
                }, events)
                assertTrue(pipe[0].fd >= 0)
                broker.cancel(Bundle().apply {
                    putInt(ThreeStoveAgentContract.KEY_CONTRACT_VERSION, 1)
                    putString(ThreeStoveAgentContract.KEY_MODEL_REF_JSON, """{"requestId":"accepted-fd"}""")
                })
                assertEquals("cancelled", events.awaitTerminal()["type"].asString)
                assertClosed(pipe[0])
            } finally { pipe.forEach { runCatching { it.close() } } }
        }
    }

    private fun broker(grant: HostCapabilityGrant = grant()) = ThreeStoveAgentModelBroker(context, Process.myUid(), grant,
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
        putInt(ThreeStoveAgentContract.KEY_CONTRACT_VERSION, 1)
        putString(ThreeStoveAgentContract.KEY_MODEL_REQUEST_JSON, value.toString())
    }

    private fun requireFake() {
        assumeTrue("Enable autojs.threeStoveAgent.model.conformance.enabled for fake Provider device checks",
            InstrumentationRegistry.getArguments().getString("autojs.threeStoveAgent.model.conformance.enabled") == "true")
        assertTrue("Install test-apps:ai-provider-conformance first", runCatching {
            context.packageManager.getPackageInfo(FAKE_PACKAGE, 0)
        }.isSuccess)
        assertEquals(android.content.pm.PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, FAKE_PACKAGE))
    }

    private class Events : IThreeStoveAgentModelCallback.Stub() {
        val values = CopyOnWriteArrayList<JsonObject>()
        val ready = CountDownLatch(1)
        private val tools = CountDownLatch(1)
        private val terminal = CountDownLatch(1)
        override fun onEvent(event: Bundle?) {
            requireNotNull(event)
            @Suppress("DEPRECATION")
            val descriptor = event.getParcelable<ParcelFileDescriptor>(ThreeStoveAgentContract.KEY_PAYLOAD_FD)
            val text = descriptor?.let { ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { it.readText() } }
                ?: requireNotNull(event.getString(ThreeStoveAgentContract.KEY_MODEL_EVENT_JSON))
            val value = JsonParser.parseString(text).asJsonObject
            values += value
            if (value["type"].asString == "chunk") ready.countDown()
            if (value["type"].asString == "tool_calls") tools.countDown()
            if (value["type"].asString in TERMINAL) terminal.countDown()
        }
        fun awaitTools(): JsonObject {
            val received = tools.await(15, TimeUnit.SECONDS)
            assertTrue("Model broker did not request tools: $values", received)
            return values.last { it["type"].asString == "tool_calls" }
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
