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

        assertEquals(14, definitions.size)
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
            id = "database",
            modules = listOf(NodeBridgeModules.DATABASE, NodeBridgeModules.SQLITE),
            capabilities = listOf(NodeBridgePermissionManifest.DATABASE),
        )
        assertProvider(
            id = "work_manager",
            modules = listOf(NodeBridgeModules.WORK_MANAGER),
            capabilities = listOf(NodeBridgePermissionManifest.WORK_MANAGER),
        )
        assertProvider(
            id = "input_observer",
            modules = listOf(NodeBridgeModules.INPUT_OBSERVER),
            capabilities = listOf(NodeBridgePermissionManifest.INPUT_OBSERVER, NodeBridgePermissionManifest.INPUT_OBSERVER_KEYS),
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
        assertEquals(NodeBridgeProviderStatus.AVAILABLE, healthById.getValue("ocr").status)
        assertEquals(NodeBridgeProviderStatus.UNAVAILABLE, healthById.getValue("media_projection").status)
        assertEquals(NodeBridgeProviderStatus.DEGRADED, healthById.getValue("image").status)
        assertEquals(NodeBridgeProviderStatus.AVAILABLE, healthById.getValue("ui").status)
        assertEquals(NodeBridgeProviderStatus.DEGRADED, healthById.getValue("input_observer").status)
        assertEquals("mlkit-chinese", healthById.getValue("ocr").details.getValue("defaultEngine"))
        assertTrue(healthById.getValue("image").details.getValue("liveMethods").contains("readImage"))
        assertTrue(healthById.getValue("ui").details.getValue("liveMethods").contains("showLayout"))
        assertEquals("fake", healthById.getValue("input_observer").details.getValue("availableSource"))
        assertEquals(NodeBridgeProviderStatus.AVAILABLE, healthById.getValue("storage").status)
        assertEquals(NodeBridgeProviderStatus.AVAILABLE, healthById.getValue("database").status)
        assertEquals(NodeBridgeProviderStatus.AVAILABLE, healthById.getValue("work_manager").status)
        assertEquals(NodeWorkManagerBridgeScheduler.SCHEDULER_NAME, healthById.getValue("work_manager").details.getValue("scheduler"))
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
            val definitionJson = health.definition.toJsonObject()
            assertEquals(NodeBridgeProviderRegistry.PROVIDER_SCHEMA, definitionJson.getString("schema"))
            assertEquals(NodeBridgeProviderRegistry.REGISTRY_VERSION, definitionJson.getString("registryVersion"))
            assertTrue(definitionJson.getJSONArray("statusVocabulary").length() >= 6)
            assertTrue(definitionJson.getJSONObject("packagedSupport").getString("status").isNotBlank())
            val healthJson = health.toJsonObject()
            assertTrue(NodeBridgeProviderRegistry.statusVocabulary.contains(healthJson.getString("statusCode")))
            assertEquals(healthJson.getString("statusCode"), healthJson.getJSONObject("healthCheck").getString("status"))
            assertEquals(health.activeResourceCount, healthJson.getJSONObject("activeResources").getInt("count"))
            assertTrue(healthJson.has("lastError"))
        }
        assertEquals("supported", healthById.getValue("storage").definition.packagedSupport.status.wireName)
        assertEquals("supported", healthById.getValue("work_manager").definition.packagedSupport.status.wireName)
        assertEquals("unsupported", healthById.getValue("media_projection").definition.packagedSupport.status.wireName)
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
