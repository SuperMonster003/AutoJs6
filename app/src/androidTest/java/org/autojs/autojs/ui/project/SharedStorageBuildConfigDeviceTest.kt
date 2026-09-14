package org.autojs.autojs.ui.project

import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.apkbuilder.template.RemoteApkBuildRequestPreparer
import org.autojs.autojs.project.ProjectConfig
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SharedStorageBuildConfigDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun project(launch: String? = null): ProjectConfig {
        val json = JSONObject().put("name", "Storage fixture").put("packageName", "org.example.storage")
            .put("versionName", "1.0.0").put("versionCode", 1).put("main", "main.js")
        if (launch != null) json.put("launchConfig", JSONObject(launch))
        return requireNotNull(ProjectConfig.fromJson(json.toString()))
    }

    @Test fun existingProjectsDefaultToNoStorageGate() {
        assertFalse(project().launchConfig.requiresSharedStorage)
        assertFalse(project("""{"hideLogs":true}""").launchConfig.requiresSharedStorage)
    }

    @Test fun projectAndRemoteSerializationPreserveExplicitChoice() {
        for (required in listOf(true, false)) {
            val config = project("""{"requiresSharedStorage":$required}""")
            assertEquals(required, config.launchConfig.requiresSharedStorage)
            val serialized = RemoteApkBuildRequestPreparer.serializeProjectConfigForRemoteRequest(config)
            assertEquals(required, JSONObject(serialized).getJSONObject("launchConfig").getBoolean("requiresSharedStorage"))
            assertEquals(required, ProjectConfig.fromJson(serialized).launchConfig.requiresSharedStorage)
        }
    }

    @Test fun savedBuildProfilePreservesStorageStateAndFieldPolicy() {
        val name = "sdk37-storage-" + java.util.UUID.randomUUID()
        // Exercise the public persistence path, including normalization of exported/imported fields.
        val state = JSONObject().put(BuildConfigStore.STATE_LAUNCH_REQUIRES_SHARED_STORAGE, true)
        val fields = JSONObject().put(BuildConfigStore.FIELD_FIXED_LAUNCH_REQUIRES_SHARED_STORAGE, true)
        try {
            BuildConfigStore.saveProfile(context, name, state, fields)
            val loaded = BuildConfigStore.loadProfilePayload(context, name)!!
            assertTrue(loaded.state.getBoolean(BuildConfigStore.STATE_LAUNCH_REQUIRES_SHARED_STORAGE))
            val imported = BuildConfigStore.importProfileFromJson(BuildConfigStore.exportProfileToJson(context, name))
            assertTrue(imported.state.getBoolean(BuildConfigStore.STATE_LAUNCH_REQUIRES_SHARED_STORAGE))
            assertTrue(imported.fields.getBoolean(BuildConfigStore.FIELD_FIXED_LAUNCH_REQUIRES_SHARED_STORAGE))
            val old = BuildConfigStore.importProfileFromJson(JSONObject().put("name", name))
            assertFalse(old.state.getBoolean(BuildConfigStore.STATE_LAUNCH_REQUIRES_SHARED_STORAGE))
        } finally {
            BuildConfigStore.deleteProfile(context, name)
        }
    }
}
