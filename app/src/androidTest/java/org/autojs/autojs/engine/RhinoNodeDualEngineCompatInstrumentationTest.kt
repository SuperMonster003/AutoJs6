package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RhinoNodeDualEngineCompatInstrumentationTest {

    @Test
    fun fakeDeviceTransportResultsUseP12RequiredShape() {
        val results = fakeDeviceTransportResults()
        assertEquals(10, results.length())

        val requiredFields = listOf(
            "engine",
            "compatStatus",
            "providerMode",
            "resourceCleanupStatus",
            "skipReason",
        )
        val statusCounts = linkedMapOf(
            "supported" to 0,
            "supported_async" to 0,
            "partial" to 0,
            "blocked_by_design" to 0,
        )
        val enginesByCase = linkedMapOf<String, MutableSet<String>>()

        for (index in 0 until results.length()) {
            val result = results.getJSONObject(index)
            requiredFields.forEach { field ->
                assertTrue("result $index missing $field", result.has(field))
            }
            val caseId = result.getString("caseId")
            val engine = result.getString("engine")
            val compatStatus = result.getString("compatStatus")
            val cleanupStatus = result.getString("resourceCleanupStatus")

            assertTrue("unexpected engine $engine", engine == "rhino" || engine == "node")
            assertTrue("unexpected compatStatus $compatStatus", statusCounts.containsKey(compatStatus))
            assertTrue("unexpected cleanup $cleanupStatus", cleanupStatus == "closed" || cleanupStatus == "not_applicable")
            assertTrue("skipReason must be null for fake executed records", result.isNull("skipReason"))

            statusCounts[compatStatus] = statusCounts.getValue(compatStatus) + 1
            enginesByCase.getOrPut(caseId) { linkedSetOf() }.add(engine)
        }

        assertEquals(setOf("rhino", "node"), enginesByCase.getValue("events-keys"))
        assertEquals(setOf("rhino", "node"), enginesByCase.getValue("ui-compat-show-update-close"))
        assertEquals(setOf("rhino", "node"), enginesByCase.getValue("tasks-disposable"))
        assertEquals(setOf("rhino", "node"), enginesByCase.getValue("barcode-qrcode"))
        assertEquals(setOf("rhino", "node"), enginesByCase.getValue("rhino-shim-disabled"))
        assertTrue(statusCounts.getValue("supported_async") > 0)
        assertTrue(statusCounts.getValue("blocked_by_design") > 0)

        val report = JSONObject()
            .put("schema", SCHEMA)
            .put("task", "P12-33")
            .put("runnerMode", "fake_device_transport")
            .put("requiredResultFields", JSONArray(requiredFields))
            .put("statusCounts", JSONObject(statusCounts))
            .put("results", results)
        println("$REPORT_PREFIX$report")
    }

    private fun fakeDeviceTransportResults(): JSONArray {
        return JSONArray()
            .put(result("events-keys", "rhino", "supported", "legacy_rhino", "not_applicable"))
            .put(result("events-keys", "node", "supported", "policy_only", "not_applicable"))
            .put(result("ui-compat-show-update-close", "rhino", "supported_async", "legacy_rhino_ui", "closed"))
            .put(result("ui-compat-show-update-close", "node", "supported_async", "fake_ui_provider", "closed"))
            .put(result("tasks-disposable", "rhino", "partial", "legacy_rhino_tasks", "not_applicable"))
            .put(result("tasks-disposable", "node", "partial", "fake_work_manager", "closed"))
            .put(result("barcode-qrcode", "rhino", "supported_async", "legacy_rhino_image", "closed"))
            .put(result("barcode-qrcode", "node", "supported_async", "fake_barcode_provider", "closed"))
            .put(result("rhino-shim-disabled", "rhino", "blocked_by_design", "legacy_rhino", "not_applicable"))
            .put(result("rhino-shim-disabled", "node", "blocked_by_design", "policy_only", "not_applicable"))
    }

    private fun result(
        caseId: String,
        engine: String,
        compatStatus: String,
        providerMode: String,
        resourceCleanupStatus: String,
    ): JSONObject {
        return JSONObject()
            .put("caseId", caseId)
            .put("engine", engine)
            .put("compatStatus", compatStatus)
            .put("providerMode", providerMode)
            .put("resourceCleanupStatus", resourceCleanupStatus)
            .put("skipReason", JSONObject.NULL)
    }

    companion object {
        private const val SCHEMA = "autojs6-rhino-dual-engine-device-evidence-v1"
        private const val REPORT_PREFIX = "rhino.dual_engine.device_evidence="
    }
}
