package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeProfileSelectionDiagnosticsPhase13DecisionInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext
    private lateinit var root: File

    @Before
    fun setUp() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        root = File(context.cacheDir, "node-profile-selection-p13-49-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create P13-49 profile selection root: $absolutePath" }
        }
        writeProjectMetadata()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun diagnosticsReportProjectPackageProfileSelectionAndFailureHints() {
        val report = NodeProfileSelectionDiagnostics.fromWorkingDirectory(root.absolutePath).toJsonObject()

        assertEquals(NodeProfileSelectionDiagnostics.SCHEMA, report.getString("schema"))
        assertEquals(NodeProfileSelectionDiagnostics.STATUS, report.getString("status"))
        assertEquals(NodeProfileSelectionDiagnostics.SAFE_DEFAULT, report.getString("effectiveProfile"))
        assertEquals("working_directory", report.getString("source"))
        assertFalse(report.getBoolean("profilePromotion"))
        assertFalse(report.getBoolean("userSelectable"))
        assertTrue(report.getBoolean("projectSettingsSupported"))
        assertTrue(report.getBoolean("packagedSettingsSupported"))
        assertTrue(report.getJSONArray("requestedProfiles").toString().contains("pro_compat_opt_in"))
        assertTrue(report.getJSONArray("requestedProfiles").toString().contains("desktop_compat_opt_in"))
        assertTrue(report.getJSONArray("requestedProfiles").toString().contains("debug_unsafe_lab"))
        assertTrue(report.getJSONArray("declaredNodePermissions").toString().contains("network"))
        assertTrue(report.getJSONArray("declaredNodePermissions").toString().contains("shell.root"))
        assertTrue(report.getJSONArray("declaredAndroidPermissions").toString().contains("android.permission.INTERNET"))
        assertTrue(report.getJSONArray("highRiskCapabilities").toString().contains("network"))
        assertTrue(report.getJSONArray("highRiskCapabilities").toString().contains("shell.root"))
        assertTrue(report.getJSONArray("blockedProfilePromotions").length() >= 3)

        val hint = NodeProfileSelectionDiagnostics.failureHintForMissingCapabilities(
            moduleName = "fetch",
            methodName = "get",
            missingCapabilities = listOf(NodeBridgePermissionManifest.NETWORK),
        )
        assertEquals("autojs6-node-profile-selection-failure-hint-v1", hint.getString("schema"))
        assertTrue(hint.getJSONArray("requiredProfiles").toString().contains("desktop_compat_opt_in"))
        assertTrue(hint.getJSONArray("requiredNodePermissions").toString().contains("network"))
        assertTrue(hint.getJSONArray("requiredAndroidPermissions").toString().contains("android.permission.INTERNET"))
        assertTrue(hint.getString("message").contains("required profile: desktop_compat_opt_in"))
        assertTrue(hint.getJSONObject("declarationExamples").getJSONArray("nodePermissions").toString().contains("network"))
        assertTrue(
            hint.getJSONObject("declarationExamples")
                .getJSONObject("projectJson")
                .getJSONObject("node")
                .getJSONArray("permissions")
                .toString()
                .contains("network"),
        )
    }

    @Test
    fun runtimeProfileSelectionAndCapabilityErrorsExposeActionableHints() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-49 profile selection diagnostics")

        val manifest = NodeBridgePermissionManifest.fromWorkingDirectory(root.absolutePath)
        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                (async function main() {
                  const profile = require("autojs6:profile");
                  const selection = profile.profileSelection;
                  console.log("p13profile.selection=" + [
                    selection.schema,
                    selection.status,
                    selection.effectiveProfile,
                    selection.requestedProfiles.join(","),
                    selection.declaredNodePermissions.join(","),
                    selection.blockedProfilePromotions.join(","),
                    selection.profilePromotion,
                    selection.userSelectable,
                    Object.isFrozen(selection),
                    selection.highRiskCapabilities.map((item) => item.id).sort().join(",")
                  ].join("|"));

                  try {
                    await require("accessibility").isEnabled({ timeoutMs: 1000 });
                  } catch (error) {
                    console.log("p13profile.error=" + [
                      String(error && (error.autojs6Code || error.code || "")),
                      Array.isArray(error && error.requiredProfiles) ? error.requiredProfiles.join(",") : "",
                      Array.isArray(error && error.requiredNodePermissions) ? error.requiredNodePermissions.join(",") : "",
                      Array.isArray(error && error.requiredAndroidPermissions) ? error.requiredAndroidPermissions.join(",") : "",
                      error && error.profileSelectionHint ? error.profileSelectionHint.effectiveProfile : "",
                      error && error.profileSelectionHint ? error.profileSelectionHint.profileSelectionRequired : "",
                      String(error && error.message || "").indexOf("required profile: pro_compat_opt_in") >= 0,
                      error && error.profileSelectionHint && error.profileSelectionHint.declarationExamples
                        ? error.profileSelectionHint.declarationExamples.projectJson.node.permissions.join(",")
                        : "",
                      Object.isFrozen(error && error.profileSelectionHint),
                      Object.isFrozen(error && error.profileSelectionHint && error.profileSelectionHint.declarationExamples),
                      Object.isFrozen(error && error.profileSelectionHint && error.profileSelectionHint.declarationExamples.nodePermissions),
                      Object.isFrozen(error && error.profileSelectionHint && error.profileSelectionHint.declarationExamples.projectJson.node.permissions),
                      Object.isFrozen(error && error.profileSelectionHint && error.profileSelectionHint.declarationExamples.packageJson.autojs6.node.permissions)
                    ].join("|"));
                  }
                })();
            """.trimIndent(),
            sourceName = "<node-profile-selection-phase13-p13-49.js>",
            workingDirectory = root,
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("P13-49 profile selection diagnostics", result)
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "p13profile.selection=${NodeProfileSelectionDiagnostics.SCHEMA}|" +
                    "${NodeProfileSelectionDiagnostics.STATUS}|safe_default|" +
                    "pro_compat_opt_in,desktop_compat_opt_in,debug_unsafe_lab|network,shell.root|" +
                    "pro_compat_opt_in,desktop_compat_opt_in,debug_unsafe_lab|false|false|true|",
            ),
        )
        assertTrue(result.stdout, result.stdout.contains("network"))
        assertTrue(result.stdout, result.stdout.contains("shell.root"))
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "p13profile.error=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|" +
                    "pro_compat_opt_in|accessibility||safe_default|true|true|accessibility|true|true|true|true|true",
            ),
        )
    }

    private fun writeProjectMetadata() {
        File(root, "project.json").writeText(
            """
            {
              "type": "node",
              "node": {
                "profile": "desktop_compat_opt_in",
                "profiles": ["pro_compat_opt_in", "debug_unsafe_lab"],
                "permissions": ["network", "shell.root"],
                "network": true
              }
            }
            """.trimIndent(),
        )
        File(root, "package.json").writeText(
            """
            {
              "name": "p13-profile-selection",
              "autojs6": {
                "node": {
                  "profiles": ["desktop_compat_opt_in"],
                  "permissions": ["network"]
                }
              }
            }
            """.trimIndent(),
        )
    }
}
