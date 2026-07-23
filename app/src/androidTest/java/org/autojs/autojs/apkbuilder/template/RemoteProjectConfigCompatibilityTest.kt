package org.autojs.autojs.apkbuilder.template

import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.project.ProjectConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class RemoteProjectConfigCompatibilityTest {

    @Test
    fun canonicalRemotePayloadDoesNotRewriteLegacySourceSchema() {
        // Ensure the target Application (and GlobalAppContext used by LaunchConfig) is ready.
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

        val original = ProjectConfig()
            .setName("Legacy project")
            .setPackageName("org.example.legacy")
            .setVersionName("1.0.0")
            .setVersionCode(7)
            .setMainScriptFileName("main.js")
        original.recordOriginalJsonKey("name", "projectName")
        original.recordOriginalJsonKey("packageName", "package")
        original.recordOriginalJsonKey("build", "buildInfo")
        original.buildInfo.apply {
            recordOriginalJsonKey("id", "build_id")
            recordOriginalJsonKey("number", "build_number")
            recordOriginalJsonKey("time", "build_time")
        }

        val remoteJson = RemoteApkBuildRequestPreparer.serializeProjectConfigForRemoteRequest(original)
        val remoteObject = JSONObject(remoteJson)
        assertTrue(remoteObject.has("name"))
        assertTrue(remoteObject.has("packageName"))
        assertTrue(remoteObject.has("build"))
        assertFalse(remoteObject.has("projectName"))
        assertFalse(remoteObject.has("package"))
        assertFalse(remoteObject.has("buildInfo"))

        remoteObject.getJSONObject("build").apply {
            put("id", "updated-build-id")
            put("number", 8L)
            put("time", 123456789L)
        }
        val mergedObject = JSONObject(
            RemoteProjectConfigUpdater.mergeBuildInfoJson(original, remoteObject.toString())
        )
        assertTrue(mergedObject.has("projectName"))
        assertTrue(mergedObject.has("package"))
        assertTrue(mergedObject.has("buildInfo"))
        assertFalse(mergedObject.has("name"))
        assertFalse(mergedObject.has("packageName"))
        assertFalse(mergedObject.has("build"))

        val legacyBuild = mergedObject.getJSONObject("buildInfo")
        assertEquals("updated-build-id", legacyBuild.getString("build_id"))
        assertEquals(8L, legacyBuild.getLong("build_number"))
        assertEquals(123456789L, legacyBuild.getLong("build_time"))
        assertFalse(legacyBuild.has("id"))
        assertFalse(legacyBuild.has("number"))
        assertFalse(legacyBuild.has("time"))
    }
}
