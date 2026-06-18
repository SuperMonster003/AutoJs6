package org.autojs.autojs.engine

import org.autojs.autojs6.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeBridgeProviderRegistryInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext

    @Test
    fun providerRegistryDefinesPhase7LiveProviderSurface() {
        val definitions = NodeBridgeProviderRegistry.definitionsById

        assertEquals(12, definitions.size)
        assertProvider(
            id = "fetch",
            modules = listOf(NodeBridgeModules.FETCH),
            capabilities = listOf(NodeBridgePermissionManifest.NETWORK),
        )
        assertProvider(
            id = "websocket",
            modules = listOf(NodeBridgeModules.WEBSOCKET),
            capabilities = listOf(NodeBridgePermissionManifest.NETWORK),
        )
        assertProvider(
            id = "ocr",
            modules = listOf(NodeBridgeModules.OCR),
            capabilities = listOf(NodeBridgePermissionManifest.OCR, NodeBridgePermissionManifest.IMAGE),
        )
        assertProvider(
            id = "work_manager",
            modules = listOf(NodeBridgeModules.WORK_MANAGER),
            capabilities = listOf(NodeBridgePermissionManifest.WORK_MANAGER),
        )
        assertProvider(
            id = "shell",
            modules = listOf(NodeBridgeModules.SHELL),
            capabilities = listOf(NodeBridgePermissionManifest.SHELL),
        )
    }

    @Test
    fun androidHealthReflectsCurrentProviderAvailabilityWithoutPromotingFutureProviders() {
        val healthById = NodeBridgeProviderRegistry.androidHealth(context).associateBy { it.definition.id }

        assertEquals(
            if (BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED) {
                NodeBridgeProviderStatus.AVAILABLE
            } else {
                NodeBridgeProviderStatus.UNAVAILABLE
            },
            healthById.getValue("fetch").status,
        )
        assertEquals(NodeBridgeProviderStatus.UNAVAILABLE, healthById.getValue("ocr").status)
        assertEquals(NodeBridgeProviderStatus.UNAVAILABLE, healthById.getValue("media_projection").status)
        assertEquals(NodeBridgeProviderStatus.UNAVAILABLE, healthById.getValue("image").status)
        assertEquals(NodeBridgeProviderStatus.UNAVAILABLE, healthById.getValue("ui").status)
        assertEquals(NodeBridgeProviderStatus.AVAILABLE, healthById.getValue("storage").status)
        assertEquals(NodeBridgeProviderStatus.FAKE, healthById.getValue("work_manager").status)
        assertEquals(NodeBridgeProviderStatus.AVAILABLE, healthById.getValue("shell").status)
        assertTrue(
            healthById.getValue("accessibility").status in setOf(
                NodeBridgeProviderStatus.AVAILABLE,
                NodeBridgeProviderStatus.PERMISSION_REQUIRED,
            ),
        )
        healthById.values.forEach { health ->
            assertEquals(NodeBridgeProviderRegistry.PROVIDER_VERSION, health.definition.version)
            assertTrue(health.activeResourceCount >= 0)
        }
    }

    @Test
    fun bridgeErrorsExposeStableCategoryWithoutBreakingLegacyCodes() {
        val unavailable = NodeBridgeError.bridgeUnavailable("missing", "method")
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, unavailable.code)
        assertEquals(NodeBridgeErrorCategory.UNAVAILABLE, unavailable.category)

        val capabilityDenied = NodeBridgeError.capabilityNotDeclared("storage", "get", listOf("storage"))
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED, capabilityDenied.code)
        assertEquals(NodeBridgeErrorCategory.CAPABILITY_DENIED, capabilityDenied.category)

        val permissionDenied = NodeBridgeError.permissionDenied("android permission denied", "notifications", "notify")
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, permissionDenied.code)
        assertEquals(NodeBridgeErrorCategory.PERMISSION_DENIED, permissionDenied.category)

        val providerFailed = NodeBridgeError.providerFailed("provider boom", "toast", "showToast")
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PROCESS_DEAD, providerFailed.code)
        assertEquals(NodeBridgeErrorCategory.PROVIDER_FAILED, providerFailed.category)
        assertEquals(
            NodeBridgeErrorCategory.PROVIDER_FAILED,
            NodeBridgeError.fromJsonObject(providerFailed.toJsonObject()).category,
        )
    }

    private fun assertProvider(
        id: String,
        modules: List<String>,
        capabilities: List<String>,
    ) {
        val provider = NodeBridgeProviderRegistry.definitionsById[id]
        assertNotNull("$id provider must be registered", provider)
        provider ?: return
        assertEquals(modules, provider.modules)
        assertEquals(capabilities.map(NodeBridgePermissionManifest::normalizeCapability), provider.requiredCapabilities)
        modules.forEach { module ->
            assertTrue(
                "$id must expose methods for $module",
                provider.toJsonObject().getJSONObject("methods").getJSONArray(module).length() > 0,
            )
        }
    }
}
