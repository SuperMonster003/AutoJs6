package org.autojs.autojs.core.pref.fleet

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for FleetProfileApplier running on an Android device.
 *
 * Uses a dedicated test SharedPreferences instance to avoid modifying the app's
 * real preferences. Each test cleans up after itself.
 */
class FleetProfileApplierTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs by lazy {
        context.getSharedPreferences("test_fleet_profile", Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
    }

    @Test
    fun applyJson_appliesBoolean() {
        val result = FleetProfileApplier.applyJson(context, """{"foreground_service": true}""", prefs)
        assertTrue("expected success", result.success)
        assertEquals(1, result.appliedCount)
        assertEquals(0, result.skippedCount)
        assertEquals(listOf("foreground_service"), result.appliedKeys)
    }

    @Test
    fun applyJson_appliesString() {
        val result = FleetProfileApplier.applyJson(context, """{"restart_strategy": "quick"}""", prefs)
        assertTrue(result.success)
        assertEquals(1, result.appliedCount)
        assertEquals(listOf("restart_strategy"), result.appliedKeys)
    }

    @Test
    fun applyJson_appliesMultipleKeys() {
        val json = """{
            "foreground_service": true,
            "floating_menu_shown": false,
            "stable_mode": true,
            "guard_mode": true
        }"""
        val result = FleetProfileApplier.applyJson(context, json, prefs)
        assertTrue(result.success)
        assertEquals(4, result.appliedCount)
        assertEquals(4, result.appliedKeys.size)
    }

    @Test
    fun applyJson_acceptsRawKey() {
        val d = "${'$'}"
        val result = FleetProfileApplier.applyJson(context, """{"key_${d}_foreground_service": true}""", prefs)
        assertTrue(result.success)
        assertEquals(1, result.appliedCount)
    }

    @Test
    fun applyJson_resolvesValueAlias() {
        val result = FleetProfileApplier.applyJson(context, """{"timed_task_backend": "alarm"}""", prefs)
        assertTrue("expected success", result.success)
        assertEquals(1, result.appliedCount)
    }

    @Test
    fun applyJson_unknownKey_skipped() {
        val result = FleetProfileApplier.applyJson(context, """{"nonexistent_key": true}""", prefs)
        assertFalse("expected failure", result.success)
        assertEquals(0, result.appliedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(listOf("nonexistent_key"), result.failedKeys)
    }

    @Test
    fun applyJson_invalidJson_returnsError() {
        val result = FleetProfileApplier.applyJson(context, """{invalid""", prefs)
        assertFalse(result.success)
        assertEquals(0, result.appliedCount)
        assertTrue(result.errors.first().contains("Invalid JSON"))
    }

    @Test
    fun applyJson_skipsMetaKeys() {
        val json = """{
            "_meta": {"name": "test"},
            "foreground_service": true
        }"""
        val result = FleetProfileApplier.applyJson(context, json, prefs)
        assertTrue(result.success)
        assertEquals(1, result.appliedCount)
    }

    @Test
    fun applyJson_clearExisting_clearsBeforeApply() {
        FleetProfileApplier.applyJson(context, """{"foreground_service": true}""", prefs)
        val result = FleetProfileApplier.applyJson(context, """{
            "_meta": {"clear_existing": true},
            "stable_mode": true
        }""", prefs)
        assertTrue(result.success)
        assertEquals(1, result.appliedCount)
    }

    @Test
    fun applyJson_appliesInt() {
        val result = FleetProfileApplier.applyJson(context, """{"editor_text_size": 18}""", prefs)
        assertTrue(result.success)
        assertEquals(1, result.appliedCount)
    }

    @Test
    fun applyJson_appliesFloat() {
        val result = FleetProfileApplier.applyJson(context, """{"screen_capture_request_delay": 0.5}""", prefs)
        assertTrue(result.success)
        assertEquals(1, result.appliedCount)
    }

    @Test
    fun applyJson_appliesStringArray() {
        val json = """{"file_extensions": ["js", "jsx"]}"""
        val result = FleetProfileApplier.applyJson(context, json, prefs)
        assertTrue(result.success)
        assertEquals(1, result.appliedCount)
    }

    @Test
    fun result_toJson_includesAllFields() {
        val result = FleetProfileApplier.Result(
            success = true, appliedCount = 2, skippedCount = 0,
            appliedKeys = listOf("a", "b"), failedKeys = emptyList(),
            errors = emptyList(), message = "ok"
        )
        val json = result.toJson()
        assertTrue(json.getBoolean("success"))
        assertEquals(2, json.getInt("applied_count"))
        assertEquals(0, json.getInt("skipped_count"))
        assertEquals(2, json.getJSONArray("applied_keys").length())
        assertEquals("ok", json.getString("message"))
    }

    @Test
    fun result_toLogLine_includesTimestamp() {
        val result = FleetProfileApplier.Result(
            success = true, appliedCount = 1, skippedCount = 0,
            appliedKeys = listOf("x"), failedKeys = emptyList(),
            errors = emptyList(), message = "ok"
        )
        val line = result.toLogLine()
        assertTrue("expected timestamp prefix", line.matches(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z .*""")))
        assertTrue(line.contains("INFO "))
        assertTrue(line.contains("applied=1"))
        assertTrue(line.contains("message=\"ok\""))
    }

    @Test
    fun result_toLogLine_levelIsErrorOnFail() {
        val result = FleetProfileApplier.Result(
            success = false, appliedCount = 0, skippedCount = 1,
            appliedKeys = emptyList(), failedKeys = listOf("bad"),
            errors = listOf("Unknown key: bad"), message = "fail"
        )
        val line = result.toLogLine()
        assertTrue("expected ERROR level", line.contains("ERROR"))
        assertTrue(line.contains("failed=bad"))
    }

    @Test
    fun result_toLogLine_levelIsWarnOnPartial() {
        val result = FleetProfileApplier.Result(
            success = false, appliedCount = 2, skippedCount = 1,
            appliedKeys = listOf("a", "b"), failedKeys = listOf("c"),
            errors = listOf("Unknown key: c"), message = "partial"
        )
        val line = result.toLogLine()
        assertTrue("expected WARN level on partial", line.contains("WARN "))
    }

    @Test
    fun applyFromPath_parsesFile() {
        val file = java.io.File(context.cacheDir, "test-fleet-profile.json")
        file.writeText("""{"foreground_service": true}""", Charsets.UTF_8)
        val result = FleetProfileApplier.applyFromPath(context, file.absolutePath)
        assertTrue(result.success)
        assertEquals(1, result.appliedCount)
        file.delete()
    }

    @Test
    fun applyFromPath_missingFile_returnsError() {
        val result = FleetProfileApplier.applyFromPath(context, "/nonexistent/path.json")
        assertFalse(result.success)
        assertTrue(result.message.contains("Failed to read"))
    }
}
