package org.autojs.autojs.runtime.api.ai

import org.autojs.autojs.core.plugin.ai.AiPluginAskFailure
import org.autojs.autojs.core.plugin.ai.AiPluginAskHandle
import org.autojs.autojs.core.plugin.ai.AiPluginCatalog
import org.autojs.autojs.core.plugin.ai.AiPluginCatalogOutcome
import org.autojs.autojs.core.plugin.ai.AiPluginCatalogRequest
import org.autojs.autojs.core.plugin.ai.AiPluginCatalogRunner
import org.autojs.autojs.core.plugin.ai.AiPluginTargetSummary
import org.autojs.autojs.core.plugin.ai.AiProviderComponent
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.modules.ai.AiAsyncDispatcher
import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.autojs.plugin.ai.provider.api.AiTargetAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class AiPluginCatalogRuntimeTest {

    @Test
    fun completeCatalogPublishesExactDefaultLocalAndRemoteTargetMetadata() {
        val dispatchers = TestDispatchers()
        val request = request()
        val local = testPluginTarget()
        val remote = AiPluginTargetSummary(
            targetId = "profile:work",
            providerId = "openai-compatible",
            profileId = "work",
            modelId = "claude-opus-4-8",
            displayName = "PoloAPI",
            capabilityIds = listOf(AiProviderCapabilityId.STREAMING, AiProviderCapabilityId.USAGE),
            locality = AiDataLocality.REMOTE,
            credentialMode = AiCredentialMode.PLUGIN_MANAGED,
            availability = AiTargetAvailability.AVAILABLE,
            configured = true,
            isDefault = false,
            maximumContextBytes = 262_144,
            maximumOutputBytes = 65_536,
            supportedControls = emptyList(),
            declaredHttpsOrigins = listOf("https://api.example.test"),
            backendProfiles = emptyList(),
        )
        val runner = FakeRunner(
            AiPluginCatalogOutcome.Completed(
                AiPluginCatalog(
                    component = request.component,
                    providerId = request.providerId,
                    generation = "catalog-42",
                    defaultTargetId = local.targetId,
                    targets = listOf(local, remote),
                ),
            ),
        )
        val runtime = AiPluginCatalogRuntime({ runner }, dispatchers::create)
        val resolved = AtomicReference<Any?>()

        runtime.list(request).onResolve(callback(resolved::set))
        dispatchers.drain()

        val catalog = resolved.get() as Map<*, *>
        assertEquals("catalog-42", catalog["generation"])
        assertEquals("local:example-model", catalog["defaultTarget"])
        val plugin = catalog["plugin"] as Map<*, *>
        assertEquals("example.provider", plugin["provider"])
        val targets = catalog["targets"] as List<*>
        assertEquals(2, targets.size)
        val localValue = targets[0] as Map<*, *>
        assertEquals("local", localValue["locality"])
        assertEquals("none", localValue["credentialMode"])
        assertEquals(true, localValue["isDefault"])
        val remoteValue = targets[1] as Map<*, *>
        assertEquals("profile:work", remoteValue["id"])
        assertEquals("remote", remoteValue["locality"])
        assertEquals("plugin-managed", remoteValue["credentialMode"])
        assertEquals("work", remoteValue["profile"])
        assertEquals(listOf("https://api.example.test"), remoteValue["origins"])
        assertEquals(1, runner.handle.closeCount.get())
        runtime.close()
    }

    @Test
    fun catalogFailurePublishesStablePluginCode() {
        val dispatchers = TestDispatchers()
        val runner = FakeRunner(AiPluginCatalogOutcome.Failed(AiPluginAskFailure.AI_PROVIDER_UNAVAILABLE))
        val runtime = AiPluginCatalogRuntime({ runner }, dispatchers::create)
        val rejected = AtomicReference<Any?>()

        runtime.list(request()).onReject(callback(rejected::set))
        dispatchers.drain()

        val error = rejected.get() as Map<*, *>
        assertEquals("AI_PROVIDER_UNAVAILABLE", error["code"])
        assertEquals("plugin", error["route"])
        assertNull(error["provider"])
        assertEquals(1, runner.handle.closeCount.get())
        runtime.close()
    }

    @Test
    fun catalogRejectsDuplicateTargetsAndInconsistentDefault() {
        val component = request().component
        val target = testPluginTarget()

        assertThrows(IllegalArgumentException::class.java) {
            AiPluginCatalog(
                component = component,
                providerId = "example.provider",
                generation = "catalog-duplicate",
                defaultTargetId = target.targetId,
                targets = listOf(target, target),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiPluginCatalog(
                component = component,
                providerId = "example.provider",
                generation = "catalog-wrong-default",
                defaultTargetId = "local:another-model",
                targets = listOf(target),
            )
        }
    }

    private fun request() = AiPluginCatalogRequest(
        component = AiProviderComponent("org.example.provider", "org.example.provider.AiProviderService"),
        providerId = "example.provider",
        timeoutMillis = 5_000,
    )

    private fun callback(action: (Any?) -> Unit) = object : ScriptPromiseAdapter.Callback {
        override fun call(arg: Any?) = action(arg)
    }

    private class TestDispatchers {
        private val scheduled = ArrayDeque<() -> Unit>()

        fun create() = AiAsyncDispatcher(
            valueMapper = { it },
            scheduler = scheduled::addLast,
            releaseWait = {},
        )

        fun drain() {
            while (scheduled.isNotEmpty()) scheduled.removeFirst().invoke()
        }
    }

    private class FakeRunner(
        private val outcome: AiPluginCatalogOutcome,
    ) : AiPluginCatalogRunner {
        val handle = RecordingHandle()

        override fun listCatalog(
            request: AiPluginCatalogRequest,
            onOutcome: (AiPluginCatalogOutcome) -> Unit,
        ): AiPluginAskHandle {
            onOutcome(outcome)
            return handle
        }
    }

    private class RecordingHandle : AiPluginAskHandle {
        val closeCount = AtomicInteger()
        override fun cancel(): Boolean = true
        override fun close() {
            closeCount.incrementAndGet()
        }
    }
}
