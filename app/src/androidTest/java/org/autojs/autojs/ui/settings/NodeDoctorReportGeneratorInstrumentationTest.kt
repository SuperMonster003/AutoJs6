package org.autojs.autojs.ui.settings

import org.autojs.autojs.engine.NodeBridgePermissionManifest
import org.autojs.autojs.engine.NodeBridgeProviderRegistry
import org.autojs.autojs.engine.NodeBridgeProviderStatus
import org.autojs.autojs.engine.NodeEmbeddedTestHarness
import org.autojs.autojs.engine.NativeNodeEmbeddedRuntimeBridge
import org.autojs.autojs.engine.NodeWorkManagerBridgeScheduler
import org.autojs.autojs.script.NodeProjectRuntimeCompatibilityDescriptor
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeDoctorReportGeneratorInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext

    @Test
    fun generatesPrivacyRedactedTextAndJsonReports() {
        val reports = NodeDoctorReportGenerator.generate(context, includeNativeProbe = false)

        assertTrue(reports.textFile.isFile)
        assertTrue(reports.jsonFile.isFile)

        val text = reports.textFile.readText()
        val jsonText = reports.jsonFile.readText()
        val json = JSONObject(jsonText)

        assertTrue(text.contains("AutoJs6 Node Doctor Report"))
        assertEquals("autojs6-node-doctor-device-v1", json.getString("schema"))
        assertEquals("skipped", json.getJSONObject("nativeProbe").getString("status"))
        assertFalse(json.getJSONObject("privacy").getBoolean("scriptContentsIncluded"))
        assertFalse(json.getJSONObject("privacy").getBoolean("environmentValuesIncluded"))
        assertTrue(json.getJSONObject("privacy").getBoolean("pathsRedacted"))
        val runtime = json.getJSONObject("runtime")
        val compatibilityDescriptor = runtime.getJSONObject("compatibilityDescriptor")
        val descriptorVersions = compatibilityDescriptor.getJSONObject("versions")
        assertEquals(NodeProjectRuntimeCompatibilityDescriptor.SCHEMA, compatibilityDescriptor.getString("schema"))
        assertEquals(
            NodeProjectRuntimeCompatibilityDescriptor.NODE_PROFILE_V1_1,
            compatibilityDescriptor.getString("nodeProfile"),
        )
        assertEquals(
            NodeProjectRuntimeCompatibilityDescriptor.BOOTSTRAP_VERSION,
            descriptorVersions.getString("bootstrap"),
        )
        assertEquals(
            NodeProjectRuntimeCompatibilityDescriptor.MODULE_SOURCES_SCHEMA_VERSION,
            descriptorVersions.getString("moduleSourcesSchema"),
        )
        val compileCache = json.getJSONObject("compileCache")
        assertEquals(NativeNodeEmbeddedRuntimeBridge.COMPILE_CACHE_SCHEMA_VERSION, compileCache.getString("schema"))
        assertTrue(compileCache.getBoolean("appPrivateOnly"))
        assertFalse(compileCache.getBoolean("userControlExposed"))
        assertEquals(NativeNodeEmbeddedRuntimeBridge.COMPILE_CACHE_MAX_BYTES, compileCache.getLong("maxBytes"))
        assertEquals(NativeNodeEmbeddedRuntimeBridge.COMPILE_CACHE_MAX_PROJECT_DIRS, compileCache.getInt("maxProjectDirs"))
        assertTrue(compileCache.getString("baseDir").contains(NativeNodeEmbeddedRuntimeBridge.COMPILE_CACHE_DIR_NAME))
        assertTrue(compileCache.getString("diagnosticPrefix").contains("embedded_script.compile_cache"))
        val bridgeCapabilities = json.getJSONObject("bridgeCapabilities")
        val networkCapability = bridgeCapabilities.getJSONObject("network")
        assertTrue(bridgeCapabilities.getBoolean("projectManifestSupported"))
        assertTrue(bridgeCapabilities.getBoolean("enforcedWhenDeclared"))
        assertEquals(NodeBridgePermissionManifest.NETWORK, networkCapability.getString("capability"))
        assertTrue(networkCapability.getBoolean("requiresDeclaration"))
        assertTrue(networkCapability.getBoolean("requiresAndroidInternet"))
        assertFalse(networkCapability.getBoolean("rawNodeNetworkModulesEnabled"))
        assertTrue(
            networkCapability
                .getJSONArray("requiredAndroidPermissions")
                .toString()
                .contains(NodeBridgePermissionManifest.ANDROID_PERMISSION_INTERNET),
        )
        val bridgeProviders = json.getJSONArray("bridgeProviders")
        assertEquals(NodeBridgeProviderRegistry.definitions.size, bridgeProviders.length())
        val providersById = bridgeProviders.byId()
        val fetchProvider = providersById.getValue("fetch")
        val ocrProvider = providersById.getValue("ocr")
        val barcodeProvider = providersById.getValue("barcode")
        val shellProvider = providersById.getValue("shell")
        val workManagerProvider = providersById.getValue("work_manager")
        assertEquals(NodeBridgeProviderRegistry.PROVIDER_SCHEMA, fetchProvider.getString("schema"))
        assertEquals(NodeBridgeProviderRegistry.REGISTRY_VERSION, fetchProvider.getString("registryVersion"))
        assertEquals("fetch/axios", fetchProvider.getString("displayName"))
        assertTrue(fetchProvider.getJSONArray("requiredCapabilities").toString().contains("network"))
        assertTrue(fetchProvider.getJSONObject("packagedSupport").getString("status").isNotBlank())
        assertTrue(NodeBridgeProviderRegistry.statusVocabulary.contains(fetchProvider.getString("statusCode")))
        assertEquals(NodeBridgeProviderStatus.AVAILABLE.wireName, ocrProvider.getString("status"))
        assertEquals(NodeBridgeProviderStatus.AVAILABLE.phase10WireName, ocrProvider.getString("statusCode"))
        assertEquals("mlkit-chinese", ocrProvider.getJSONObject("details").getString("defaultEngine"))
        assertTrue(text.contains("defaultEngine=mlkit-chinese"))
        assertEquals(NodeBridgeProviderStatus.AVAILABLE.wireName, barcodeProvider.getString("status"))
        assertEquals("mlkit-barcode", barcodeProvider.getJSONObject("details").getString("defaultEngine"))
        assertTrue(text.contains("defaultEngine=mlkit-barcode"))
        assertEquals(NodeBridgeProviderStatus.AVAILABLE.wireName, workManagerProvider.getString("status"))
        assertEquals(
            NodeWorkManagerBridgeScheduler.SCHEDULER_NAME,
            workManagerProvider.getJSONObject("details").getString("scheduler"),
        )
        assertEquals("androidHealth", workManagerProvider.getJSONObject("healthCheck").getString("name"))
        assertEquals(0, shellProvider.getInt("activeResourceCount"))
        assertEquals(0, shellProvider.getJSONObject("activeResources").getInt("count"))
        assertTrue(shellProvider.has("lastError"))
        assertTrue(text.contains("Bridge Capabilities:"))
        assertTrue(text.contains("Bridge Providers:"))
        assertTrue(text.contains("fetch/axios: status="))
        assertTrue(text.contains("Runtime Compatibility:"))
        assertTrue(text.contains("Compile Cache:"))
        assertTrue(text.contains("app-private only: true"))
        assertTrue(text.contains("custom-loader hit/miss counters are unavailable"))
        assertTrue(text.contains(NodeProjectRuntimeCompatibilityDescriptor.ESM_LOADER_VERSION))
        assertTrue(text.contains("network capability: requires declaration=true"))
        assertFalse(text.contains(context.filesDir.absolutePath))
        assertFalse(jsonText.contains(context.filesDir.absolutePath))
    }

    private fun JSONArray.byId(): Map<String, JSONObject> {
        return (0 until length())
            .map { index -> getJSONObject(index) }
            .associateBy { provider -> provider.getString("id") }
    }
}
