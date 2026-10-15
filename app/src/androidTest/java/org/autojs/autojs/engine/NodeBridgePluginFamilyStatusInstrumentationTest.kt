package org.autojs.autojs.engine

import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.barcode.BarcodePluginHost
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.docs.OfflineDocsPluginHost
import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityBrokerCore
import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityGrant
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import org.autojs.autojs.core.plugin.opencv.OpenCvPluginHost
import org.autojs.autojs.core.plugin.text.PinyinPluginHost
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.host.capability.api.HostCapabilityContract
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** `app.pluginCapabilities` and the family status behind it (MCP roadmap P11 D27). */
class NodeBridgePluginFamilyStatusInstrumentationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val statuses = setOf(
        NodeBridgePluginFamilyStatus.STATUS_AVAILABLE,
        NodeBridgePluginFamilyStatus.STATUS_NOT_INSTALLED,
        NodeBridgePluginFamilyStatus.STATUS_DISABLED,
        NodeBridgePluginFamilyStatus.STATUS_INCOMPATIBLE,
    )

    @Test
    fun registryDeclaresTheMethod() {
        assertTrue(NodeBridgeModules.supports("app", "pluginCapabilities"))
        assertEquals(listOf(NodeBridgePermissionManifest.APP_QUERY), NodeBridgePermissionManifest.requiredCapabilitiesFor("app", "pluginCapabilities"))
    }

    @Test
    fun theReportDescribesEveryFamilyConsistentlyWithTheHosts() {
        val report = NodeBridgePluginFamilyStatus.report(context)
        assertEquals(NodeBridgePluginFamilyStatus.SCHEMA, report.getString("schema"))
        assertEquals(BuildConfig.VERSION_CODE.toLong(), report.getLong("hostVersionCode"))
        assertTrue(report.getJSONObject("pluginCenter").getString("component").startsWith(context.packageName + "/"))
        val families = familiesById(report)
        assertEquals(listOf("ocr", "barcode", "pinyin", "pinyin4j", "mediainfo", "opencv", "docs"), families.keys.toList())
        families.values.forEach { family ->
            assertTrue(family.toString(), family.getString("status") in statuses)
            assertTrue(family.toString(), family.getJSONArray("methods").length() > 0)
            if (family.getString("status") == NodeBridgePluginFamilyStatus.STATUS_NOT_INSTALLED) {
                assertEquals(family.toString(), 0, family.getJSONArray("plugins").length())
            }
        }
        assertEquals(OcrPluginHost.availableEngines(context).isNotEmpty(), families.getValue("ocr").getString("status") == NodeBridgePluginFamilyStatus.STATUS_AVAILABLE)
        if (BarcodePluginHost.queryServiceCount(context) == 0) {
            assertEquals(NodeBridgePluginFamilyStatus.STATUS_NOT_INSTALLED, families.getValue("barcode").getString("status"))
        }
        assertEquals(OpenCvPluginHost.isAvailable(context), families.getValue("opencv").getString("status") == NodeBridgePluginFamilyStatus.STATUS_AVAILABLE)
        assertEquals(OfflineDocsPluginHost.isAvailable(context), families.getValue("docs").getString("status") == NodeBridgePluginFamilyStatus.STATUS_AVAILABLE)
        assertTrue("ocr family must list accessibility.readScreenText", "accessibility.readScreenText" in methodsOf(families.getValue("ocr")))
    }

    @Test
    fun availableMethodsFollowTheReport() {
        val report = familiesById(NodeBridgePluginFamilyStatus.report(context))
        val expected = report.values.filter { it.getString("status") == NodeBridgePluginFamilyStatus.STATUS_AVAILABLE }.flatMap { methodsOf(it) }.toSet()
        assertEquals(expected, NodeBridgePluginFamilyStatus.availableMethods(context))
    }

    @Test
    fun disablingAPluginInThePluginCenterTurnsItsFamilyOff() {
        @Suppress("DEPRECATION")
        val packages = context.packageManager.queryIntentServices(android.content.Intent(PinyinPluginHost.ACTION_PINYIN), 0)
            .mapNotNull { it.serviceInfo?.packageName }.distinct()
        assumeTrue("the Pinyin plugin must be installed for the enable-store gate", packages.isNotEmpty())
        val preferences = context.getSharedPreferences("plugin_center_enable_state", 0)
        val original = packages.associateWith { packageName ->
            val key = "key_\$_enabled_plugin_\$_$packageName"
            if (preferences.contains(key)) preferences.getBoolean(key, true) else null
        }
        try {
            packages.forEach { PluginEnableStore.setEnabled(context, it, false) }
            val disabled = familiesById(NodeBridgePluginFamilyStatus.report(context)).getValue("pinyin")
            assertEquals(disabled.toString(), NodeBridgePluginFamilyStatus.STATUS_DISABLED, disabled.getString("status"))
            assertTrue(disabled.toString(), (0 until disabled.getJSONArray("plugins").length()).none { disabled.getJSONArray("plugins").getJSONObject(it).getBoolean("enabled") })
            assertTrue("pinyin.convert" !in NodeBridgePluginFamilyStatus.availableMethods(context))
            packages.forEach { PluginEnableStore.setEnabled(context, it, true) }
            val enabled = familiesById(NodeBridgePluginFamilyStatus.report(context)).getValue("pinyin")
            assertTrue(enabled.toString(), enabled.getString("status") != NodeBridgePluginFamilyStatus.STATUS_DISABLED)
        } finally {
            val edit = preferences.edit()
            original.forEach { (packageName, enabled) ->
                val key = "key_\$_enabled_plugin_\$_$packageName"
                if (enabled == null) edit.remove(key) else edit.putBoolean(key, enabled)
            }
            edit.commit()
        }
    }

    @Test
    fun brokerInfoAdvertisesGrantedMethodsOfAvailableFamilies() {
        val grant = HostCapabilityGrant(
            allowedMethods = setOf("ocr.recognizeText", "pinyin.convert", "image.findImage", "device.info"),
            permissions = setOf("ocr", "image", "pinyin", "device"),
        )
        val core = HostCapabilityBrokerCore(context, "family-methods", grant)
        try {
            val optional = core.getBrokerInfo().getStringArray(HostCapabilityContract.KEY_AVAILABLE_OPTIONAL_METHODS)!!.toSet()
            val available = NodeBridgePluginFamilyStatus.availableMethods(context)
            assertEquals("ocr.recognizeText" in available, "ocr.recognizeText" in optional)
            assertEquals("pinyin.convert" in available, "pinyin.convert" in optional)
            assertEquals("image.findImage" in available, "image.findImage" in optional)
            // Not granted, so never advertised even when the plugin is present.
            assertTrue("pinyin.simple" !in optional)
            assertTrue("device.info" !in optional)
        } finally {
            core.destroy(null)
        }
    }

    @Test
    fun theBridgeMethodReturnsTheReportOffTheMainThread() {
        val provider = AndroidNodeBridgeCapabilityProvider(context, NodeBridgeEngineInfo.fromContext(context, id = "plugin-capabilities", sourceName = "status.cjs", cwd = context.cacheDir.absolutePath))
        try {
            val latch = CountDownLatch(1)
            var response: NodeBridgeResponse? = null
            provider.dispatch(
                NodeBridgeRequest(
                    id = "plugin-capabilities", module = "app", method = "pluginCapabilities", argsJson = "[]", timeoutMs = 15_000L,
                    permissions = listOf(NodeBridgePermissionManifest.APP_QUERY),
                ),
            ) { response = it; latch.countDown() }
            assertTrue(latch.await(20, TimeUnit.SECONDS))
            val result = checkNotNull(response)
            assertTrue(result.toString(), result.ok)
            assertEquals(NodeBridgePluginFamilyStatus.SCHEMA, JSONObject(result.resultJson.orEmpty()).getString("schema"))
        } finally {
            provider.destroy(NodeBridgeError.processDead())
        }
    }

    private fun familiesById(report: JSONObject): Map<String, JSONObject> {
        val families = report.getJSONArray("families")
        return LinkedHashMap<String, JSONObject>().also { map -> (0 until families.length()).forEach { val f = families.getJSONObject(it); map[f.getString("id")] = f } }
    }

    private fun methodsOf(family: JSONObject): List<String> {
        val methods = family.getJSONArray("methods")
        return (0 until methods.length()).map { methods.getString(it) }
    }
}
