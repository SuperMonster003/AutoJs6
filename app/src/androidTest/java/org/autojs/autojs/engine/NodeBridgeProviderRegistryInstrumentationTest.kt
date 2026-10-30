package org.autojs.autojs.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeBridgeProviderRegistryInstrumentationTest {

    private val context = NodePluginTestHarness.targetContext

    @Test
    fun providerRegistryDefinesPhase7LiveProviderSurface() {
        val definitions = NodeBridgeProviderRegistry.definitionsById

        assertEquals(29, definitions.size)
        assertProvider(
            id = "agent",
            modules = listOf(NodeBridgeModules.AGENT),
            capabilities = listOf(NodeBridgePermissionManifest.AGENT, NodeBridgePermissionManifest.AGENT_EXEC),
        )
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
            id = "barcode",
            modules = listOf(NodeBridgeModules.BARCODE),
            capabilities = listOf(NodeBridgePermissionManifest.BARCODE, NodeBridgePermissionManifest.IMAGE),
        )
        assertProvider(
            id = "database",
            modules = listOf(NodeBridgeModules.DATABASE, NodeBridgeModules.SQLITE),
            capabilities = listOf(NodeBridgePermissionManifest.DATABASE),
        )
        assertProvider(
            id = "media",
            modules = listOf(NodeBridgeModules.MEDIA, NodeBridgeModules.MEDIAINFO, NodeBridgeModules.MEDIA_STORE, NodeBridgeModules.RECORDER),
            capabilities = listOf(
                NodeBridgePermissionManifest.MEDIA,
                NodeBridgePermissionManifest.MEDIA_AUDIO,
                NodeBridgePermissionManifest.MEDIA_METADATA,
                NodeBridgePermissionManifest.MEDIA_RECORDING,
                NodeBridgePermissionManifest.MEDIA_PLAYBACK,
                NodeBridgePermissionManifest.MEDIA_LIBRARY,
                NodeBridgePermissionManifest.MEDIA_LIBRARY_MUTATE,
            ),
        )
        assertProvider(
            id = "pinyin",
            modules = listOf(NodeBridgeModules.PINYIN),
            capabilities = listOf(NodeBridgePermissionManifest.PINYIN),
        )
        assertProvider(
            id = "pinyin4j",
            modules = listOf(NodeBridgeModules.PINYIN4J),
            capabilities = listOf(NodeBridgePermissionManifest.PINYIN4J),
        )
        assertProvider(
            id = "opencc",
            modules = listOf(NodeBridgeModules.OPENCC),
            capabilities = listOf(NodeBridgePermissionManifest.OPENCC),
        )
        assertProvider(
            id = "notifications",
            modules = listOf(NodeBridgeModules.NOTIFICATIONS),
            capabilities = listOf(NodeBridgePermissionManifest.NOTIFICATIONS, NodeBridgePermissionManifest.NOTIFICATIONS_SETTINGS),
        )
        assertProvider(
            id = "device",
            modules = listOf(NodeBridgeModules.DEVICE, NodeBridgeModules.SCREEN_METRICS),
            capabilities = listOf(NodeBridgePermissionManifest.DEVICE, NodeBridgePermissionManifest.DEVICE_POWER),
        )
        assertProvider(
            id = "work_manager",
            modules = listOf(NodeBridgeModules.WORK_MANAGER),
            capabilities = listOf(NodeBridgePermissionManifest.WORK_MANAGER),
        )
        assertProvider(
            id = "package_manager",
            modules = listOf(NodeBridgeModules.PACKAGE_MANAGER),
            capabilities = listOf(NodeBridgePermissionManifest.PACKAGE_MANAGER),
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
    fun pinyinHealthReflectsPluginAvailability() {
        val health = NodeBridgeProviderRegistry.androidHealth(context)
            .associateBy { it.definition.id }
            .getValue("pinyin")

        assertTrue(
            health.status in setOf(
                NodeBridgeProviderStatus.AVAILABLE,
                NodeBridgeProviderStatus.UNAVAILABLE,
            ),
        )
        assertTrue(health.details.getValue("liveMethods").contains("convert"))
    }

    @Test
    fun pinyin4jHealthReflectsPluginAvailability() {
        val health = NodeBridgeProviderRegistry.androidHealth(context)
            .associateBy { it.definition.id }
            .getValue("pinyin4j")

        assertTrue(
            health.status in setOf(
                NodeBridgeProviderStatus.AVAILABLE,
                NodeBridgeProviderStatus.UNAVAILABLE,
            ),
        )
        assertTrue(health.details.getValue("liveMethods").contains("readings"))
    }

    @Test
    fun androidHealthReflectsCurrentProviderAvailabilityWithoutPromotingFutureProviders() {
        val healthById = NodeBridgeProviderRegistry.androidHealth(context).associateBy { it.definition.id }

        assertEquals(
            NodeBridgeProviderStatus.AVAILABLE,
            healthById.getValue("fetch").status,
        )
        assertEquals(NodeBridgeProviderStatus.AVAILABLE, healthById.getValue("ocr").status)
        assertEquals(
            if (org.autojs.autojs.core.plugin.barcode.BarcodePluginHost.queryServiceCount(context) > 0) NodeBridgeProviderStatus.AVAILABLE else NodeBridgeProviderStatus.UNAVAILABLE,
            healthById.getValue("barcode").status,
        )
        assertEquals(NodeBridgeProviderStatus.AVAILABLE, healthById.getValue("media_projection").status)
        assertEquals(
            if (org.autojs.autojs.core.plugin.opencv.OpenCvPluginHost.isAvailable(context)) NodeBridgeProviderStatus.AVAILABLE else NodeBridgeProviderStatus.DEGRADED,
            healthById.getValue("image").status,
        )
        assertEquals(NodeBridgeProviderStatus.AVAILABLE, healthById.getValue("ui").status)
        assertTrue(
            healthById.getValue("input_observer").status in setOf(
                NodeBridgeProviderStatus.AVAILABLE,
                NodeBridgeProviderStatus.DEGRADED,
            ),
        )
        assertEquals("auto", healthById.getValue("ocr").details.getValue("defaultEngine"))
        assertEquals("mlkit-ocr,paddle-ocr,rapid-ocr", healthById.getValue("ocr").details.getValue("automaticEngineOrder"))
        assertEquals("mlkit-barcode", healthById.getValue("barcode").details.getValue("defaultEngine"))
        assertTrue(healthById.getValue("barcode").details.getValue("liveMethods").contains("detectAll"))
        assertTrue(healthById.getValue("image").details.getValue("liveMethods").contains("readImage"))
        assertTrue(healthById.getValue("ui").details.getValue("liveMethods").contains("showLayout"))
        assertEquals("fake", healthById.getValue("input_observer").details.getValue("availableSource"))
        assertEquals("accessibility", healthById.getValue("input_observer").details.getValue("liveSource"))
        assertEquals(NodeBridgeProviderStatus.AVAILABLE, healthById.getValue("storage").status)
        assertEquals(NodeBridgeProviderStatus.AVAILABLE, healthById.getValue("database").status)
        assertEquals(NodeBridgeProviderStatus.AVAILABLE, healthById.getValue("media").status)
        assertTrue(healthById.getValue("media").details.getValue("liveMethods").contains("mediainfo.read"))
        assertTrue(healthById.getValue("media").details.getValue("liveMethods").contains("media.play"))
        assertTrue(healthById.getValue("media").details.getValue("liveMethods").contains("media_store.query"))
        assertEquals(NodeBridgePermissionManifest.MEDIA_PLAYBACK, healthById.getValue("media").details.getValue("playbackCapability"))
        assertEquals(NodeBridgePermissionManifest.MEDIA_LIBRARY_MUTATE, healthById.getValue("media").details.getValue("libraryMutateCapability"))
        assertEquals(NodeBridgePermissionManifest.MEDIA_RECORDING, healthById.getValue("media").details.getValue("recordingCapability"))
        assertTrue(
            healthById.getValue("pinyin").status in setOf(
                NodeBridgeProviderStatus.AVAILABLE,
                NodeBridgeProviderStatus.UNAVAILABLE,
            ),
        )
        assertTrue(healthById.getValue("pinyin").details.getValue("liveMethods").contains("convert"))
        assertTrue(
            healthById.getValue("pinyin4j").status in setOf(
                NodeBridgeProviderStatus.AVAILABLE,
                NodeBridgeProviderStatus.UNAVAILABLE,
            ),
        )
        assertTrue(healthById.getValue("pinyin4j").details.getValue("liveMethods").contains("readings"))
        assertEquals(NodeBridgeProviderStatus.AVAILABLE, healthById.getValue("device").status)
        assertTrue(healthById.getValue("notifications").details.getValue("liveMethods").contains("openSettings"))
        assertEquals(NodeBridgePermissionManifest.NOTIFICATIONS_SETTINGS, healthById.getValue("notifications").details.getValue("settingsCapability"))
        assertTrue(healthById.getValue("device").details.getValue("liveMethods").contains("openBatteryOptimizationSettings"))
        assertEquals(NodeBridgePermissionManifest.DEVICE_POWER, healthById.getValue("device").details.getValue("powerCapability"))
        assertEquals(NodeBridgeProviderStatus.AVAILABLE, healthById.getValue("work_manager").status)
        assertEquals(NodeWorkManagerBridgeScheduler.SCHEDULER_NAME, healthById.getValue("work_manager").details.getValue("scheduler"))
        assertEquals(NodeBridgeProviderStatus.AVAILABLE, healthById.getValue("package_manager").status)
        assertEquals("guarded_mutation_partial", healthById.getValue("package_manager").details.getValue("jsBridge"))
        assertEquals("local_unpacked_host_managed_only", healthById.getValue("package_manager").details.getValue("sourceAuthority"))
        assertEquals(NodeAndroidPackageInstallManager.INSTALL_AUTHORITY, healthById.getValue("package_manager").details.getValue("installAuthority"))
        assertEquals(NodeBridgePermissionManifest.PACKAGE_MANAGER_MUTATE, healthById.getValue("package_manager").details.getValue("mutationCapability"))
        assertTrue(healthById.getValue("package_manager").details.getValue("liveMethods").contains("verify"))
        assertTrue(healthById.getValue("package_manager").details.getValue("liveMethods").contains("planInstall"))
        assertTrue(healthById.getValue("package_manager").details.getValue("liveMethods").contains("planRegistryInstall"))
        assertTrue(healthById.getValue("package_manager").details.getValue("liveMethods").contains("install"))
        assertEquals("planInstall,planUpdate,planRemove,planRegistryInstall,planTarballInstall", healthById.getValue("package_manager").details.getValue("planOperations"))
        assertEquals("install,update,remove", healthById.getValue("package_manager").details.getValue("mutationOperations"))
        assertEquals("guarded_package_manager_alias_no_cli", healthById.getValue("package_manager").details.getValue("npmFacade"))
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
        assertEquals("degraded", healthById.getValue("package_manager").definition.packagedSupport.status.wireName)
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
