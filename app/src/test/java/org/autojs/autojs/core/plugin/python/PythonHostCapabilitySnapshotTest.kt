package org.autojs.autojs.python.preview

import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.python.PythonHostCapabilitySnapshot
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonHostCapabilitySnapshotTest {

    @Test
    fun projectDocumentIsDeterministicAndContainsOnlyTheThreePreviewGrants() {
        val document = fixture(project = true)
        val first = PythonHostCapabilitySnapshot.encode(document)
        val second = PythonHostCapabilitySnapshot.encode(document)

        assertTrue(first.contentEquals(second))
        assertTrue(first.size.toLong() <= PythonRuntimeContract.MAX_HOST_CAPABILITY_SNAPSHOT_BYTES)
        val json = JsonParser.parseString(first.decodeToString()).asJsonObject
        assertEquals(1, json["schemaVersion"].asInt)
        assertEquals("request-1", json["execution"].asJsonObject["id"].asString)
        assertEquals("src/main.py", json["execution"].asJsonObject["entryPoint"].asString)
        assertTrue(json["execution"].asJsonObject["project"].asBoolean)
        assertEquals(
            listOf("app.snapshot.read", "device.snapshot.read", "project.files.read"),
            json["grants"].asJsonArray.map { it.asString },
        )
    }

    @Test
    fun singleFileDocumentEscapesTextAndDoesNotExposeDeviceIdentifiers() {
        val encoded = PythonHostCapabilitySnapshot.encode(
            fixture(project = false).copy(
                entryPoint = "quoted\"/line\n.py",
                device = fixture(project = false).device.copy(model = "model\\name"),
            ),
        )
        val text = encoded.decodeToString()
        val json = JsonParser.parseString(text).asJsonObject

        assertEquals("quoted\"/line\n.py", json["execution"].asJsonObject["entryPoint"].asString)
        assertEquals("model\\name", json["device"].asJsonObject["model"].asString)
        assertEquals(
            listOf("app.snapshot.read", "device.snapshot.read"),
            json["grants"].asJsonArray.map { it.asString },
        )
        assertFalse(text.contains("serial", ignoreCase = true))
        assertFalse(text.contains("androidId", ignoreCase = true))
        assertFalse(text.contains("fingerprint", ignoreCase = true))
    }

    private fun fixture(project: Boolean) = PythonHostCapabilitySnapshot.Document(
        executionId = "request-1",
        entryPoint = "src/main.py",
        projectFilesAvailable = project,
        app = PythonHostCapabilitySnapshot.AppSnapshot(
            packageName = "org.autojs.autojs6",
            versionName = "6.7.0",
            versionCode = 670,
            debuggable = true,
        ),
        device = PythonHostCapabilitySnapshot.DeviceSnapshot(
            sdkInt = 31,
            release = "12",
            manufacturer = "example",
            brand = "example",
            model = "example",
            supportedAbis = listOf("arm64-v8a"),
        ),
    )
}
