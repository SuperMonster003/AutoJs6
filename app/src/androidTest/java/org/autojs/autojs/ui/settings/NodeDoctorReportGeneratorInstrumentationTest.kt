package org.autojs.autojs.ui.settings

import org.autojs.autojs.engine.NodeEmbeddedTestHarness
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
        assertFalse(text.contains(context.filesDir.absolutePath))
        assertFalse(jsonText.contains(context.filesDir.absolutePath))
    }
}
