package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodePrivacyDisclosurePolicyPhase13DecisionInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext
    private lateinit var root: File

    @Before
    fun setUp() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        root = File(context.cacheDir, "node-privacy-disclosure-p13-52-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create P13-52 privacy disclosure root: $absolutePath" }
        }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun kotlinPolicyRecordsPromptDisclosureAndPackagedDenialRequirements() {
        val policy = NodePrivacyDisclosurePolicy.toJsonObject()

        assertEquals(NodePrivacyDisclosurePolicy.SCHEMA, policy.getString("schema"))
        assertEquals(NodePrivacyDisclosurePolicy.STATUS, policy.getString("status"))
        assertEquals(NodePrivacyDisclosurePolicy.SAFE_DEFAULT, policy.getString("defaultProfile"))
        assertEquals(NodePrivacyDisclosurePolicy.SAFE_DEFAULT, policy.getString("effectiveProfile"))
        assertTrue(policy.getBoolean("metadataOnly"))
        assertFalse(policy.getBoolean("grantsAuthority"))
        assertTrue(policy.getBoolean("implicitHighPrivilegeDenied"))
        assertTrue(policy.getBoolean("packagedImplicitHighPrivilegeDenied"))
        assertTrue(policy.getBoolean("disclosureBlocksPromotion"))
        assertTrue(policy.getBoolean("foregroundDisclosureRequiredForPromotedSensitiveSurfaces"))
        assertTrue(policy.getBoolean("userPromptRequiredForPromotedSensitiveSurfaces"))

        val surfaces = policy.getJSONArray("surfaces").byId()
        listOf(
            "screen_capture",
            "media_recording",
            "ui_overlay",
            "accessibility",
            "network",
            "raw_network",
            "filesystem_relaxation",
            "java_interop",
            "child_process",
            "native_addon",
            "inspector",
            "wasi",
        ).forEach { id ->
            assertTrue("Missing disclosure surface: $id", surfaces.containsKey(id))
            assertTrue(surfaces.getValue(id).getBoolean("requiresUserPrompt"))
            assertFalse(surfaces.getValue(id).getBoolean("implicitGrantAllowed"))
            assertEquals("metadata_only_no_authority", surfaces.getValue(id).getString("authority"))
            assertEquals(
                NodePrivacyDisclosurePolicy.PACKAGED_BEHAVIOR_BLOCKED,
                surfaces.getValue(id).getString("packagedBehavior"),
            )
            assertEquals(
                NodePrivacyDisclosurePolicy.PROMOTION_BLOCKED_UNTIL,
                surfaces.getValue(id).getString("promotionBlockedUntil"),
            )
        }

        val screenCapture = surfaces.getValue("screen_capture")
        assertEquals(NodeBridgePermissionManifest.SCREEN_CAPTURE, screenCapture.getString("capability"))
        assertEquals(NodePrivacyDisclosurePolicy.PRO_COMPAT_OPT_IN, screenCapture.getString("profile"))
        assertTrue(screenCapture.getBoolean("requiresForegroundDisclosure"))
        assertTrue(screenCapture.getBoolean("requiresNotification"))
        assertTrue(
            screenCapture
                .getJSONArray("androidPermissions")
                .toString()
                .contains(NodeBridgePermissionManifest.ANDROID_PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION),
        )

        val mediaRecording = surfaces.getValue("media_recording")
        assertTrue(mediaRecording.getBoolean("requiresForegroundDisclosure"))
        assertTrue(
            mediaRecording
                .getJSONArray("androidPermissions")
                .toString()
                .contains(NodeBridgePermissionManifest.ANDROID_PERMISSION_RECORD_AUDIO),
        )

        val overlay = surfaces.getValue("ui_overlay")
        assertTrue(overlay.getBoolean("requiresSettingsIntent"))
        assertTrue(overlay.getJSONArray("androidPermissions").toString().contains("SYSTEM_ALERT_WINDOW"))

        val accessibility = surfaces.getValue("accessibility")
        assertTrue(accessibility.getBoolean("requiresSettingsIntent"))
        assertTrue(accessibility.getString("settingsIntent").contains("ACTION_ACCESSIBILITY_SETTINGS"))

        val network = surfaces.getValue("network")
        assertEquals(NodePrivacyDisclosurePolicy.DESKTOP_COMPAT_OPT_IN, network.getString("profile"))
        assertTrue(
            network
                .getJSONArray("androidPermissions")
                .toString()
                .contains(NodeBridgePermissionManifest.ANDROID_PERMISSION_INTERNET),
        )
        val rawNetwork = surfaces.getValue("raw_network")
        assertEquals("raw_network", rawNetwork.getString("capability"))
        assertEquals(NodePrivacyDisclosurePolicy.DESKTOP_COMPAT_OPT_IN, rawNetwork.getString("profile"))
        assertTrue(
            rawNetwork
                .getJSONArray("androidPermissions")
                .toString()
                .contains(NodeBridgePermissionManifest.ANDROID_PERMISSION_INTERNET),
        )

        val packagedPolicy = policy.getJSONObject("packagedPolicy")
        assertEquals(NodePrivacyDisclosurePolicy.PACKAGED_DISCLOSURE_SCHEMA, packagedPolicy.getString("schema"))
        assertFalse(packagedPolicy.getBoolean("grantsAuthority"))
        assertTrue(packagedPolicy.getBoolean("implicitHighPrivilegeDenied"))
        assertTrue(packagedPolicy.getBoolean("requiresDisclosureManifest"))
        assertTrue(packagedPolicy.getBoolean("reviewedManifestRequiredForPromotion"))
        assertTrue(packagedPolicy.getBoolean("denyUndeclaredHighRiskSurfaces"))
        assertEquals(NodePrivacyDisclosurePolicy.PACKAGED_BEHAVIOR_BLOCKED, packagedPolicy.getString("defaultAction"))

        val channels = policy.getJSONArray("channels").toString()
        assertTrue(channels.contains("foreground_service_notification"))
        assertTrue(channels.contains("packaged_metadata_review"))
        assertTrue(channels.contains("doctor_report"))

        val doctorPrivacy = policy.getJSONObject("doctorPrivacy")
        assertFalse(doctorPrivacy.getBoolean("scriptContentsIncluded"))
        assertFalse(doctorPrivacy.getBoolean("environmentValuesIncluded"))
        assertTrue(doctorPrivacy.getBoolean("pathsRedacted"))
        assertTrue(doctorPrivacy.getBoolean("surfaceMetadataOnly"))

        val automationImplications = policy.getJSONObject("automationImplications")
        assertEquals(
            "future_metadata_only_safe_profile_fallback",
            automationImplications.getString("automaticCrashCircuitBreaker"),
        )
        assertEquals(
            NodePrivacyDisclosurePolicy.CRASH_CIRCUIT_BREAKER_PRIVACY,
            automationImplications.getString("crashEvidencePrivacy"),
        )
        assertEquals(
            NodePrivacyDisclosurePolicy.REMOTE_PACKAGED_KILL_SWITCH_POLICY,
            automationImplications.getString("remotePackagedKillSwitch"),
        )
        assertTrue(automationImplications.getBoolean("requiresDisclosureBeforeReenable"))
        assertFalse(automationImplications.getBoolean("grantsAuthority"))
    }

    @Test
    fun runtimeProfileExposesFrozenPrivacyDisclosurePolicyToScripts() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-52 privacy disclosure policy")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                const assert = require("assert");
                const profile = require("autojs6:profile");
                const policy = profile.privacyDisclosurePolicy;
                assert.strictEqual(policy.schema, "${NodePrivacyDisclosurePolicy.SCHEMA}");
                assert.strictEqual(policy.status, "${NodePrivacyDisclosurePolicy.STATUS}");
                assert.strictEqual(policy.defaultProfile, "safe_default");
                assert.strictEqual(policy.effectiveProfile, "safe_default");
                assert.strictEqual(policy.metadataOnly, true);
                assert.strictEqual(policy.grantsAuthority, false);
                assert.strictEqual(policy.implicitHighPrivilegeDenied, true);
                assert.strictEqual(policy.packagedImplicitHighPrivilegeDenied, true);
                assert.strictEqual(policy.disclosureBlocksPromotion, true);
                assert.strictEqual(policy.packagedPolicy.requiresDisclosureManifest, true);
                assert.strictEqual(policy.packagedPolicy.grantsAuthority, false);
                assert.strictEqual(policy.packagedPolicy.defaultAction, "${NodePrivacyDisclosurePolicy.PACKAGED_BEHAVIOR_BLOCKED}");
                assert.strictEqual(policy.doctorPrivacy.scriptContentsIncluded, false);
                assert.strictEqual(policy.doctorPrivacy.pathsRedacted, true);
                assert.strictEqual(policy.automationImplications.crashEvidencePrivacy, "${NodePrivacyDisclosurePolicy.CRASH_CIRCUIT_BREAKER_PRIVACY}");
                assert.strictEqual(policy.automationImplications.remotePackagedKillSwitch, "${NodePrivacyDisclosurePolicy.REMOTE_PACKAGED_KILL_SWITCH_POLICY}");
                const byId = Object.fromEntries(policy.surfaces.map((surface) => [surface.id, surface]));
                assert.strictEqual(byId.screen_capture.capability, "screen_capture");
                assert.strictEqual(byId.screen_capture.requiresForegroundDisclosure, true);
                assert.strictEqual(byId.screen_capture.requiresNotification, true);
                assert.strictEqual(byId.screen_capture.implicitGrantAllowed, false);
                assert.strictEqual(byId.ui_overlay.requiresSettingsIntent, true);
                assert.strictEqual(byId.accessibility.requiresSettingsIntent, true);
                assert.strictEqual(byId.network.profile, "desktop_compat_opt_in");
                assert.ok(byId.network.androidPermissions.includes("android.permission.INTERNET"));
                assert.strictEqual(byId.raw_network.capability, "raw_network");
                assert.strictEqual(byId.raw_network.profile, "desktop_compat_opt_in");
                assert.ok(byId.raw_network.androidPermissions.includes("android.permission.INTERNET"));
                assert.ok(policy.channels.some((channel) => channel.id === "foreground_service_notification"));
                assert.ok(policy.channels.some((channel) => channel.id === "project_capability_review" && channel.requiredFor.includes("raw_network")));
                assert.ok(policy.channels.some((channel) => channel.id === "packaged_metadata_review" && channel.requiredFor.includes("raw_network")));
                console.log("p13privacy.policy=" + [
                  policy.schema,
                  policy.status,
                  policy.effectiveProfile,
                  policy.grantsAuthority,
                  policy.implicitHighPrivilegeDenied,
                  policy.packagedImplicitHighPrivilegeDenied,
                  Object.isFrozen(policy),
                  Object.isFrozen(policy.surfaces),
                  Object.isFrozen(byId.screen_capture),
                  byId.network.androidPermissions.includes("android.permission.INTERNET"),
                  policy.surfaces.length
                ].join("|"));
            """.trimIndent(),
            sourceName = "<node-privacy-disclosure-phase13-p13-52.js>",
            workingDirectory = root,
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("P13-52 privacy disclosure policy", result)
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "p13privacy.policy=${NodePrivacyDisclosurePolicy.SCHEMA}|" +
                    "${NodePrivacyDisclosurePolicy.STATUS}|safe_default|false|true|true|true|true|true|true|",
            ),
        )
        assertTrue(result.stdout, result.stdout.contains("|14"))
    }

    private fun JSONArray.byId(): Map<String, JSONObject> {
        return (0 until length())
            .map { index -> getJSONObject(index) }
            .associateBy { item -> item.getString("id") }
    }
}
