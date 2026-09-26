package org.autojs.autojs.core.plugin.ai

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.ai.common.api.AiErrorCode
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiRetryDisposition
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.autojs.plugin.ai.provider.api.AiToolDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Exercises production Attempt dispatch without discovery, binding, network or model calls. */
class AiPluginAskFailureDispatchAndroidTest {
    @Test fun ordinaryStreamingAndNativeToolAttemptsRetainTheSafeProviderReason() {
        for (route in Route.entries) {
            val failed = dispatch(route, AiProviderSessionEvent.ProviderFailed(AiProviderSessionErrorSummary(
                AiErrorCode.PROVIDER_FAILED, AiRetryDisposition.NEVER, true,
                AiPluginProviderFailureReason.ONLINE_NETWORK_UNAVAILABLE)))
            assertEquals(AiPluginAskFailure.PROVIDER_FAILED, failed.first)
            assertEquals(AiPluginProviderFailureReason.ONLINE_NETWORK_UNAVAILABLE, failed.second)
        }
    }

    @Test fun attemptProviderTimeoutKeepsItsOriginalMappingAndLocalTerminalsStayDistinct() {
        for (route in Route.entries) {
            val providerTimeout = dispatch(route, AiProviderSessionEvent.ProviderFailed(AiProviderSessionErrorSummary(
                AiErrorCode.TIMEOUT, AiRetryDisposition.NEVER, true)))
            assertEquals(AiPluginAskFailure.PROVIDER_FAILED, providerTimeout.first)
            assertNull(providerTimeout.second)
            assertEquals(AiPluginAskFailure.TIMED_OUT, dispatch(route, AiProviderSessionEvent.TimedOut).first)
            assertEquals(AiPluginAskFailure.CANCELLED, dispatch(route, AiProviderSessionEvent.Cancelled).first)
        }
    }

    private fun dispatch(route: Route, event: AiProviderSessionEvent): Pair<AiPluginAskFailure, AiPluginProviderFailureReason?> {
        // Reflection keeps the production process owner and its Attempt private. This fixture feeds
        // the real callback dispatch boundary without invoking start() or altering singleton state.
        val ownerClass = Class.forName("org.autojs.autojs.core.plugin.ai.ProcessAiPluginAskRunner")
        val owner = ownerClass.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
            .newInstance(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)
        val admission = ownerClass.getDeclaredField("admission").apply { isAccessible = true }.get(owner) as AtomicBoolean
        admission.set(true)
        val attempts = Class.forName("org.autojs.autojs.core.plugin.ai.ProcessAiPluginAskRunner\$Attempt")
        val outcomes = mutableListOf<AiPluginAskOutcome>()
        val events = mutableListOf<AiPluginStreamEvent>()
        val onOutcome: ((AiPluginAskOutcome) -> Unit)? = if (route == Route.ASK) ({ outcomes += it }) else null
        val onEvent: ((AiPluginStreamEvent) -> Unit)? = if (route == Route.ASK) null else ({ events += it })
        val constructor = attempts.declaredConstructors.single { it.parameterTypes.size == 7 }.apply { isAccessible = true }
        val attempt = constructor.newInstance(owner, request(route), route.streaming, onOutcome, onEvent, null, null)
        val stageClosed = AtomicInteger()
        val deadlineClosed = AtomicInteger()
        attempts.getDeclaredField("stageHandle").apply { isAccessible = true }
            .set(attempt, Closeable { stageClosed.incrementAndGet() })
        attempts.getDeclaredField("deadlineHandle").apply { isAccessible = true }
            .set(attempt, Closeable { deadlineClosed.incrementAndGet() })
        try {
            val accept = attempts.getDeclaredMethod("acceptSessionEvent", AiProviderSessionRunEvent::class.java)
                .apply { isAccessible = true }
            val transcript = AiProviderSessionRunEvent.Transcript(event)
            accept.invoke(attempt, transcript)
            accept.invoke(attempt, transcript)
            assertEquals(1, stageClosed.get())
            assertEquals(1, deadlineClosed.get())
            assertFalse(admission.get())
            return if (route == Route.ASK) {
                assertEquals(1, outcomes.size)
                assertEquals(0, events.size)
                val failed = outcomes.single() as AiPluginAskOutcome.Failed
                failed.failure to failed.providerReason
            } else {
                assertEquals(0, outcomes.size)
                assertEquals(1, events.size)
                val failed = events.single() as AiPluginStreamEvent.Failed
                failed.failure to failed.providerReason
            }
        } finally {
            (attempt as AiPluginAskHandle).close()
            ownerClass.declaredFields.filter { ExecutorService::class.java.isAssignableFrom(it.type) }.forEach { field ->
                field.isAccessible = true
                (field.get(owner) as ExecutorService).shutdownNow()
            }
        }
    }

    private fun request(route: Route) = AiPluginAskRequest(
        component = AiProviderComponent("org.autojs.fixture", "org.autojs.fixture.Provider"),
        providerId = "fixture.provider", targetId = "fixture:target",
        messages = listOf(AiPluginMessage(AiPluginMessageRole.USER, "Synthetic request")), timeoutMillis = 10_000,
        tools = if (route.tools) listOf(AiToolDefinition("observe", "Synthetic tool",
            AiPayloadReference(AiProviderMimeType.JSON, 2, inlineBytes = "{}".toByteArray(), charset = "utf-8"))) else emptyList(),
        maximumToolRounds = if (route.tools) 1 else 0,
    )

    private enum class Route(val streaming: Boolean, val tools: Boolean) {
        ASK(false, false), STREAM(true, false), TOOLS(false, true), STREAM_TOOLS(true, true),
    }
}
