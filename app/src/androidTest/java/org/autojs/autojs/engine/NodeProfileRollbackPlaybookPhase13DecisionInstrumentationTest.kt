package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeProfileRollbackPlaybookPhase13DecisionInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext
    private lateinit var root: File

    @Before
    fun setUp() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        root = File(context.cacheDir, "node-profile-rollback-p13-51-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create P13-51 rollback root: $absolutePath" }
        }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun kotlinPolicyRecordsPerProfileAndCapabilityRollbackPlaybook() {
        val policy = NodeProfileRollbackPolicy.toJsonObject()

        assertEquals(NodeProfileRollbackPolicy.SCHEMA, policy.getString("schema"))
        assertEquals(NodeProfileRollbackPolicy.STATUS, policy.getString("status"))
        assertEquals(NodeProfileRollbackPolicy.SAFE_DEFAULT, policy.getString("defaultProfile"))
        assertEquals(NodeProfileRollbackPolicy.SAFE_DEFAULT, policy.getString("effectiveProfile"))
        assertEquals(NodeProfileRollbackPolicy.SAFE_DEFAULT, policy.getString("rollbackTargetProfile"))
        assertEquals(NodeProfileRollbackPolicy.SAFE_DEFAULT, policy.getString("crashFallbackProfile"))
        assertEquals(NodeProfileRollbackPolicy.SAFE_DEFAULT, policy.getString("stressFallbackProfile"))
        assertFalse(policy.getBoolean("profilePromotion"))
        assertFalse(policy.getBoolean("userSelectable"))
        assertFalse(policy.getBoolean("grantsAuthority"))
        assertTrue(policy.getBoolean("metadataOnly"))
        assertTrue(policy.getBoolean("safeDefaultAlwaysAvailable"))
        assertTrue(policy.getBoolean("independentProfileDisable"))

        val profileSwitches = policy.getJSONObject("profileKillSwitches")
        assertEquals(NodeProfileRollbackPolicy.PRO_PROFILE_PROPERTY, profileSwitches.getString(NodeProfileRollbackPolicy.PRO_COMPAT_OPT_IN))
        assertEquals(NodeProfileRollbackPolicy.DESKTOP_PROFILE_PROPERTY, profileSwitches.getString(NodeProfileRollbackPolicy.DESKTOP_COMPAT_OPT_IN))
        assertEquals(NodeProfileRollbackPolicy.DEBUG_UNSAFE_LAB_PROPERTY, profileSwitches.getString(NodeProfileRollbackPolicy.DEBUG_UNSAFE_LAB))

        val capabilitySwitches = policy.getJSONObject("capabilityKillSwitches")
        assertEquals(NodeRuntimeCapabilities.NETWORK_EXPERIMENTAL_PROPERTY, capabilitySwitches.getString("network"))
        assertEquals(
            NodeRuntimeCapabilities.RAW_NETWORK_MODULES_EXPERIMENTAL_PROPERTY,
            capabilitySwitches.getString("raw_network"),
        )
        assertEquals(
            NodeRuntimeCapabilities.RAW_NETWORK_MODULES_EXPERIMENTAL_PROPERTY,
            capabilitySwitches.getString("raw_node_network_modules"),
        )
        assertEquals(NodeRuntimeCapabilities.WORKER_THREADS_EXPERIMENTAL_PROPERTY, capabilitySwitches.getString("worker_threads"))
        assertEquals(NodeRuntimeCapabilities.JAVA_INTEROP_EXPERIMENTAL_PROPERTY, capabilitySwitches.getString("java_interop"))
        assertEquals(NodeProfileRollbackPolicy.RELAXED_CAPABILITIES_PROPERTY, capabilitySwitches.getString("all_relaxed_capabilities"))
        assertEquals(NodeRuntimeCapabilities.CHILD_PROCESS_EXPERIMENTAL_PROPERTY, capabilitySwitches.getString("child_process"))

        val profileStates = policy.getJSONArray("profileStates").toString()
        assertTrue(profileStates.contains(NodeProfileRollbackPolicy.SAFE_DEFAULT))
        assertTrue(profileStates.contains(NodeProfileRollbackPolicy.PRO_COMPAT_OPT_IN))
        assertTrue(profileStates.contains(NodeProfileRollbackPolicy.DESKTOP_COMPAT_OPT_IN))
        assertTrue(profileStates.contains(NodeProfileRollbackPolicy.DEBUG_UNSAFE_LAB))
        assertTrue(profileStates.contains("publish_safe_profile_patch_or_remove_desktop_metadata"))

        val capabilityStates = policy.getJSONArray("capabilityStates").toString()
        assertTrue(capabilityStates.contains("network"))
        assertTrue(capabilityStates.contains("raw_network"))
        assertTrue(capabilityStates.contains("raw_node_network_modules"))
        assertTrue(capabilityStates.contains("java_interop"))
        assertTrue(capabilityStates.contains("filesystem_relaxation"))
        assertTrue(capabilityStates.contains("keep_raw_wasi_denied"))

        val triggers = policy.getJSONArray("rollbackTriggers").toString()
        assertTrue(triggers.contains("native_crash"))
        assertTrue(triggers.contains("stress_regression"))
        assertTrue(triggers.contains("packaged_apk_regression"))
        assertTrue(triggers.contains(NodeProfileRollbackPolicy.SAFE_DEFAULT))

        val packagedDowngrade = policy.getJSONObject("packagedDowngrade")
        assertEquals("publish_safe_profile_patch_or_remove_promoted_metadata", packagedDowngrade.getString("strategy"))
        assertEquals("metadata_only_no_authority", packagedDowngrade.getString("authority"))
        assertTrue(packagedDowngrade.getBoolean("publishSafeProfilePatch"))
        assertFalse(packagedDowngrade.getBoolean("remoteKillSwitchAvailable"))
        assertEquals(
            "rejected_v1_1_publish_safe_profile_patch",
            packagedDowngrade.getString("remoteKillSwitchPolicy"),
        )
        assertTrue(packagedDowngrade.getBoolean("requiresUserDisclosure"))
    }

    @Test
    fun runtimeProfileExposesFrozenRollbackPolicyToScripts() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-51 profile rollback playbook")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                const assert = require("assert");
                const profile = require("autojs6:profile");
                const rollback = profile.profileRollbackPolicy;
                assert.strictEqual(rollback.schema, "${NodeProfileRollbackPolicy.SCHEMA}");
                assert.strictEqual(rollback.status, "${NodeProfileRollbackPolicy.STATUS}");
                assert.strictEqual(rollback.effectiveProfile, "safe_default");
                assert.strictEqual(rollback.rollbackTargetProfile, "safe_default");
                assert.strictEqual(rollback.crashFallbackProfile, "safe_default");
                assert.strictEqual(rollback.stressFallbackProfile, "safe_default");
                assert.strictEqual(rollback.profilePromotion, false);
                assert.strictEqual(rollback.userSelectable, false);
                assert.strictEqual(rollback.grantsAuthority, false);
                assert.strictEqual(rollback.safeDefaultAlwaysAvailable, true);
                assert.strictEqual(rollback.independentProfileDisable, true);
                assert.strictEqual(rollback.profileKillSwitches.pro_compat_opt_in, "${NodeProfileRollbackPolicy.PRO_PROFILE_PROPERTY}");
                assert.strictEqual(rollback.profileKillSwitches.desktop_compat_opt_in, "${NodeProfileRollbackPolicy.DESKTOP_PROFILE_PROPERTY}");
                assert.strictEqual(rollback.capabilityKillSwitches.network, "${NodeRuntimeCapabilities.NETWORK_EXPERIMENTAL_PROPERTY}");
                assert.strictEqual(rollback.capabilityKillSwitches.raw_network, "${NodeRuntimeCapabilities.RAW_NETWORK_MODULES_EXPERIMENTAL_PROPERTY}");
                assert.strictEqual(rollback.capabilityKillSwitches.raw_node_network_modules, "${NodeRuntimeCapabilities.RAW_NETWORK_MODULES_EXPERIMENTAL_PROPERTY}");
                assert.strictEqual(rollback.capabilityKillSwitches.child_process, "${NodeRuntimeCapabilities.CHILD_PROCESS_EXPERIMENTAL_PROPERTY}");
                assert.strictEqual(rollback.capabilityKillSwitches.all_relaxed_capabilities, "${NodeProfileRollbackPolicy.RELAXED_CAPABILITIES_PROPERTY}");
                assert.ok(rollback.profileStates.some((item) => item.id === "safe_default" && item.enabled === true));
                assert.ok(rollback.profileStates.some((item) => item.id === "desktop_compat_opt_in" && item.fallbackProfile === "safe_default"));
                assert.ok(rollback.capabilityStates.some((item) => item.id === "raw_network" && item.enabled === false));
                assert.ok(rollback.capabilityStates.some((item) => item.id === "raw_node_network_modules" && item.enabled === false));
                assert.ok(rollback.capabilityStates.some((item) => item.id === "child_process" && item.enabled === false));
                assert.ok(rollback.rollbackTriggers.some((item) => item.id === "stress_regression" && item.fallbackProfile === "safe_default"));
                assert.strictEqual(rollback.packagedDowngrade.strategy, "publish_safe_profile_patch_or_remove_promoted_metadata");
                assert.strictEqual(rollback.packagedDowngrade.remoteKillSwitchPolicy, "rejected_v1_1_publish_safe_profile_patch");
                console.log("p13rollback.policy=" + [
                  rollback.schema,
                  rollback.status,
                  rollback.effectiveProfile,
                  rollback.rollbackTargetProfile,
                  rollback.profilePromotion,
                  rollback.userSelectable,
                  rollback.safeDefaultAlwaysAvailable,
                  rollback.independentProfileDisable,
                  Object.isFrozen(rollback),
                  Object.isFrozen(rollback.profileKillSwitches),
                  rollback.profileStates.map((item) => item.id + ":" + item.enabled + ":" + item.fallbackProfile).join(","),
                  rollback.rollbackTriggers.map((item) => item.id + ":" + item.fallbackProfile).join(",")
                ].join("|"));
            """.trimIndent(),
            sourceName = "<node-profile-rollback-phase13-p13-51.js>",
            workingDirectory = root,
            timeoutMs = 10_000L,
            childProcessExperimentalEnabled = false,
        )

        NodeEmbeddedTestHarness.assertSucceeded("P13-51 profile rollback playbook", result)
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "p13rollback.policy=${NodeProfileRollbackPolicy.SCHEMA}|" +
                    "${NodeProfileRollbackPolicy.STATUS}|safe_default|safe_default|false|false|true|true|true|true|",
            ),
        )
        assertTrue(result.stdout, result.stdout.contains("safe_default:true:safe_default"))
        assertTrue(result.stdout, result.stdout.contains("desktop_compat_opt_in:false:safe_default"))
        assertTrue(result.stdout, result.stdout.contains("stress_regression:safe_default"))
    }
}
