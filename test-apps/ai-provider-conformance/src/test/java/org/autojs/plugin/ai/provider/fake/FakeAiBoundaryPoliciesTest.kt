package org.autojs.plugin.ai.provider.fake

import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.provider.api.AiContentPart
import org.autojs.plugin.ai.provider.api.AiMessage
import org.autojs.plugin.ai.provider.api.AiMessageRole
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class FakeAiBoundaryPoliciesTest {
    @Test
    fun identityRequiresFixedPackageUidAndSameSignature() {
        val allowed = FakeHostIdentityEvidence(
            callingUid = 1001,
            installedHostUid = 1001,
            packagesForCallingUid = setOf(FakeAiProviderFixture.HOST_PACKAGE_NAME),
            providerAndHostSignaturesMatch = true,
        )
        assertEquals(FakeHostIdentityDecision.ALLOW, FakeHostIdentityPolicy.evaluate(allowed))
        FakeHostIdentityPolicy.requireAllowed(allowed)

        assertEquals(
            FakeHostIdentityDecision.HOST_PACKAGE_NOT_OWNED,
            FakeHostIdentityPolicy.evaluate(allowed.copy(packagesForCallingUid = setOf("other.package"))),
        )
        assertEquals(
            FakeHostIdentityDecision.HOST_UID_MISMATCH,
            FakeHostIdentityPolicy.evaluate(allowed.copy(installedHostUid = 1002)),
        )
        assertEquals(
            FakeHostIdentityDecision.SIGNATURE_MISMATCH,
            FakeHostIdentityPolicy.evaluate(allowed.copy(providerAndHostSignaturesMatch = false)),
        )
    }

    @Test
    fun descriptorLedgerKeepsInFlightIndicesCancelCloseable() {
        val ledger = FakeDescriptorOwnershipLedger(3)
        ledger.take(1)

        assertThrows(IllegalStateException::class.java) { ledger.take(1) }
        assertArrayEquals(intArrayOf(0, 1, 2), ledger.closeOwned())
        ledger.release(1)
        assertArrayEquals(IntArray(0), ledger.closeOwned())
        assertThrows(IllegalStateException::class.java) { ledger.take(0) }
    }

    @Test
    fun declaredPayloadReaderAcceptsOnlyExactLengthAndEof() {
        val expected = byteArrayOf(1, 2, 3)
        val verified = AtomicInteger(0)

        assertArrayEquals(
            expected,
            FakeDeclaredPayloadReader.readExactly(ByteArrayInputStream(expected), 3L) {
                verified.incrementAndGet()
            },
        )
        assertEquals(1, verified.get())
        assertThrows(IllegalArgumentException::class.java) {
            FakeDeclaredPayloadReader.readExactly(ByteArrayInputStream(byteArrayOf(1, 2)), 3L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FakeDeclaredPayloadReader.readExactly(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), 3L)
        }
        assertThrows(IllegalStateException::class.java) {
            FakeDeclaredPayloadReader.readExactly(ByteArrayInputStream(expected), 3L) {
                throw IllegalStateException("Producer failed")
            }
        }
    }

    @Test
    fun declaredPayloadReaderRejectsProtocolAndNegotiatedOversize() {
        assertThrows(IllegalArgumentException::class.java) {
            FakeDeclaredPayloadReader.readExactly(
                ByteArrayInputStream(ByteArray(0)),
                AiCommonLimits.MAX_PAYLOAD_BYTES + 1L,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FakeDeclaredPayloadReader.readExactly(
                ByteArrayInputStream(ByteArray(0)),
                declaredLengthBytes = 5L,
                maximumLengthBytes = 4L,
            )
        }
    }

    @Test
    fun boundedExecutorRejectsFloodBeyondWorkersAndQueue() {
        val executor = FakeBoundedExecutorFactory.create(
            coreThreads = 1,
            maximumThreads = 1,
            queueCapacity = 1,
            keepAliveSeconds = 0L,
            threadName = "fake-capacity-test",
        )
        val running = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            executor.execute {
                running.countDown()
                release.await()
            }
            assertTrue(running.await(5, TimeUnit.SECONDS))
            executor.execute { Unit }
            assertThrows(RejectedExecutionException::class.java) {
                executor.execute { Unit }
            }
            assertEquals(1, executor.maximumPoolSize)
            assertEquals(1, executor.queue.remainingCapacity() + executor.queue.size)
        } finally {
            release.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun binderInputPreflightRejectsOversizeBeforeCopyOrDescriptorDuplication() {
        FakeAiBinderInputPolicy.requireEnvelopeSize(1)
        FakeAiBinderInputPolicy.requireEnvelopeSize(AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES)
        FakeAiBinderInputPolicy.requireRequestDescriptorCount(
            AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST,
            AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST,
        )
        FakeAiBinderInputPolicy.requireSessionDescriptorCount(
            AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION,
            AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION,
        )

        assertThrows(IllegalArgumentException::class.java) {
            FakeAiBinderInputPolicy.requireEnvelopeSize(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FakeAiBinderInputPolicy.requireEnvelopeSize(AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES + 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FakeAiBinderInputPolicy.requireRequestDescriptorCount(
                AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST + 1,
                AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FakeAiBinderInputPolicy.requireSessionDescriptorCount(
                AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION + 1,
                AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION,
            )
        }
    }

    @Test
    fun acceptedTerminalAlwaysRunsCleanupWhenPublicationFails() {
        val cleanups = AtomicInteger(0)
        assertThrows(IllegalStateException::class.java) {
            FakeTerminalFinalizer.run(cleanup = { cleanups.incrementAndGet() }) {
                throw IllegalStateException("Encoding failed")
            }
        }
        assertEquals(1, cleanups.get())
    }

    @Test
    fun requestMaterializerValidatesDescriptorLengthDigestAndUtf8() {
        val bytes = "hello".toByteArray()
        val digest = sha256(bytes)
        val payload = descriptorPayload(
            mimeType = AiProviderMimeType.PLAIN,
            length = bytes.size.toLong(),
            index = 0,
            sha256 = digest,
            charset = "utf-8",
        )
        val base = fakeRequest()
        val request = base.copy(
            messages = listOf(AiMessage(AiMessageRole.USER, listOf(AiContentPart(payload)))),
        )

        val materialized = FakeAiPayloadMaterializer.materializeRequest(request) { index, length ->
            assertEquals(0, index)
            assertEquals(5L, length)
            bytes
        }
        assertEquals("hello", materialized.prompt)

        assertThrows(IllegalArgumentException::class.java) {
            FakeAiPayloadMaterializer.materializeRequest(request) { _, _ -> byteArrayOf(1) }
        }
        val wrongDigest = request.copy(
            messages = listOf(
                AiMessage(
                    AiMessageRole.USER,
                    listOf(
                        AiContentPart(
                            descriptorPayload(
                                AiProviderMimeType.PLAIN,
                                5L,
                                0,
                                sha256 = "0".repeat(64),
                                charset = "utf-8",
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            FakeAiPayloadMaterializer.materializeRequest(wrongDigest) { _, _ -> bytes }
        }
    }

    @Test
    fun requestAndToolResultMaterializersRejectMalformedDescriptorJson() {
        val malformed = "{]".toByteArray()
        val json = descriptorPayload(
            mimeType = AiProviderMimeType.JSON,
            length = malformed.size.toLong(),
            index = 0,
        )
        val request = fakeRequest().copy(
            messages = listOf(AiMessage(AiMessageRole.USER, listOf(AiContentPart(json)))),
        )
        assertThrows(IllegalArgumentException::class.java) {
            FakeAiPayloadMaterializer.materializeRequest(request) { _, _ -> malformed }
        }
        assertThrows(IllegalArgumentException::class.java) {
            FakeAiPayloadMaterializer.materializeToolResults(
                org.autojs.plugin.ai.provider.api.AiToolResultBatch(
                    listOf(org.autojs.plugin.ai.provider.api.AiToolResult("call-1", json)),
                ),
            ) { _, _ -> malformed }
        }
    }

    @Test
    fun identityFailureRaisesOnlySanitizedDecision() {
        val error = assertThrows(SecurityException::class.java) {
            FakeHostIdentityPolicy.requireAllowed(
                FakeHostIdentityEvidence(
                    callingUid = 7,
                    installedHostUid = null,
                    packagesForCallingUid = setOf(FakeAiProviderFixture.HOST_PACKAGE_NAME),
                    providerAndHostSignaturesMatch = false,
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("HOST_NOT_INSTALLED"))
    }

    @Test
    fun ownedWorkRegistryClosesRejectedAndCancelledResourcesExactlyOnce() {
        val registry = FakeOwnedResourceRegistry<CountingCloseable>()
        val rejected = CountingCloseable()
        registry.register(rejected)
        registry.release(rejected)
        registry.release(rejected)
        assertEquals(1, rejected.closeCount.get())

        val cancelledBeforeRun = CountingCloseable()
        val stillPending = CountingCloseable()
        registry.register(cancelledBeforeRun)
        registry.register(stillPending)
        registry.close()
        registry.close()
        assertEquals(1, cancelledBeforeRun.closeCount.get())
        assertEquals(1, stillPending.closeCount.get())
        assertThrows(IllegalStateException::class.java) {
            registry.register(CountingCloseable())
        }
    }

    @Test
    fun ownedWorkReleaseAndCancellationRaceStillClosesExactlyOnce() {
        val registry = FakeOwnedResourceRegistry<CountingCloseable>()
        val resource = CountingCloseable()
        registry.register(resource)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val release = executor.submit {
                start.await()
                registry.release(resource)
            }
            val cancel = executor.submit {
                start.await()
                registry.close()
            }
            start.countDown()
            release.get(5, TimeUnit.SECONDS)
            cancel.get(5, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        assertEquals(1, resource.closeCount.get())
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    private class CountingCloseable : Closeable {
        val closeCount = AtomicInteger(0)

        override fun close() {
            closeCount.incrementAndGet()
        }
    }
}
