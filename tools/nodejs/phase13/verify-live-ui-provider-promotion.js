#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-live-ui-provider-promotion";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_17_LIVE_UI_PROVIDER_PROMOTION.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  appManifest: "app/src/main/AndroidManifest.xml",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  uiProvider: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeUiProvider.kt",
  uiTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeUiBridgeInstrumentationTest.kt",
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  resourceLeakTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeResourceLeakStressInstrumentationTest.kt",
  packagedTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  uiTypes: "docs/nodejs/types/autojs6-node/ui.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typeSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  uiBridgeDesign: "docs/nodejs/design/UI_BRIDGE.md",
  rhinoUiDesign: "docs/nodejs/design/RHINO_UI_COMPATIBILITY.md",
  rhinoUiSubset: "docs/nodejs/design/RHINO_UI_JSON_LAYOUT_SUBSET.md",
  phase12UiReport: "docs/nodejs/reports/phase12/RHINO_UI_COMPAT_SMOKE.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
  gradleGate: "app/node-phase13-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const report = buildReport(files);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 13 live UI provider promotion: " + report.decision.status);
  console.log("Phase 13 live UI provider promotion blockers: " + report.blockers.length);
  console.log("Phase 13 live UI provider promotion policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0 || (options.failOnBlocked && !report.decision.finalBlockerClosable)) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const file = path.join(repoRoot, relative);
      return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
    }),
  );
}

function buildReport(files) {
  const proSurface = surface(files.surfaceManifest.text, "pro.ui_automation_media");
  const scenarios = [
    scenario("live_activity_provider", "require(\"ui\") has a live Activity-owned Android provider", [
      token(files.providerRegistry, '"phase13Promotion" to "P13-17 partial live provider evidence gate"'),
      token(files.providerRegistry, '"liveMethods" to "showLayout,update,batchUpdate,drainEvents,close"'),
      token(files.providerRegistry, '"components" to "Column,Row,Text,Button,Input,Image,ScrollView,List,Switch,Checkbox,Progress,Tabs"'),
      token(files.uiTest, "jsUiModuleUsesAndroidLiveProviderForButtonAndInputEvents"),
      token(files.uiTest, "NodeBridgeUiActivity"),
      token(files.uiTest, "ui.live.clicked="),
      token(files.uiTest, "ui.live.input="),
      token(files.uiTest, "androidUiProviderOpensActivityDeliversEventsAndCleansOwnedResource"),
      token(files.uiTest, 'collected.hasEvent("click", "ok")'),
      token(files.uiTest, 'collected.hasEvent("tab", "tabs")'),
      token(files.uiTest, "androidUiProviderRecordsBackActivityDestroyLifecycleEvent"),
      token(files.uiTest, "ui.lifecycle.back.reason=activity_destroyed"),
      token(files.uiTest, "androidUiProviderRecordsRecentsTaskRemovalLifecycleEvent"),
      token(files.uiTest, "ui.lifecycle.recents_task_removed.reason=activity_destroyed"),
    ]),
    scenario("json_handle_lifecycle", "JSON layout, updates, event drain, close, and provider cleanup are covered", [
      token(files.uiTypes, "showLayout(layoutJson"),
      token(files.uiTypes, "batchUpdate(patches"),
      token(files.uiTypes, "close(options?"),
      token(files.protocol, "type = NodeBridgeResourceType.UI_SESSION"),
      token(files.protocol, 'resourceRegistry.release(sessionId, "ui.close")'),
      token(files.protocol, "resourceRegistry.closeAll(destroyReason)"),
      token(files.uiProvider, "fun closeAll(reason: String = \"close_all\")"),
      token(files.resourceLeakTest, "NodeBridgeResourceType.UI_SESSION"),
      token(files.uiTest, "provider.destroy()"),
      token(files.uiTest, 'provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"]'),
    ]),
    scenario("security_and_boundaries", "UI stays JSON-only and does not leak raw Android or Rhino view objects", [
      token(files.permissionManifest, 'const val UI = "ui"'),
      token(files.uiTest, 'assertFalse(NodeBridgeModules.supports("ui", "rawView"))'),
      token(files.uiTest, 'androidPermissionsForCapabilities(listOf("ui"))'),
      token(files.uiTest, "RawView"),
      token(files.uiTest, "WebView"),
      token(files.uiTest, "owned by this execution"),
      token(files.uiTest, "BRIDGE_RESOURCE_LIMIT"),
      token(files.uiBridgeDesign, "no raw `Context`, `Activity`, `View`"),
      token(files.providerRegistry, '"rawObjectPolicy" to "no raw View, Activity, Context, NativeView, or Java object crosses the bridge"'),
    ]),
    scenario("rhino_compat_and_types", "Rhino compat.ui wrapper and type declarations stay aligned with the UI provider", [
      token(files.conformanceTest, "autojs6:compat.ui"),
      token(files.conformanceTest, "compat.ui.showLayout"),
      token(files.phase12UiReport, "packaged.rhino_compat.compat_ui=true"),
      token(files.uiTypes, "export interface UiModule"),
      token(files.typeSmoke, "const compatUi: rhinoCompat.RhinoUiCompatModule"),
      token(files.rhinoUiDesign, "P13-17"),
      token(files.rhinoUiSubset, "P13-17"),
      token(files.typesReadme, "P13-17"),
    ]),
    scenario("configuration_change_policy", "Node UI Activity uses explicit destroy/recreate configuration-change policy without a survival claim", [
      token(files.appManifest, 'android:name="org.autojs.autojs.engine.NodeBridgeUiActivity"'),
      token(files.appManifest, 'android:excludeFromRecents="true"'),
      token(files.uiTest, "androidUiActivityKeepsConfigurationChangesAsDestroyPolicy"),
      token(files.uiTest, "ui.configuration_change_policy=destroy_recreate_no_survival_claim"),
      token(files.providerRegistry, '"configurationChangePolicy" to "destroy_recreate_no_survival_claim"'),
      token(files.p13Report, "Configuration changes are explicitly not a P13-17 survivability contract"),
    ]),
    scenario("packaged_capability_boundary", "Packaged UI capability metadata denial is covered without claiming live packaged UI promotion", [
      token(files.packagedTest, "installAndRunUiCapabilityBoundaryEmbeddedNodePackagedApk"),
      token(files.packagedTest, 'const projectConfig = JSON.parse(fs.readFileSync("project.json", "utf8"));'),
      token(files.packagedTest, 'nodePermissions.indexOf("ui") < 0'),
      token(files.packagedTest, 'denied.code === "ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED"'),
      token(files.packagedTest, "packaged.ui_boundary.capability_denied=true"),
      token(files.packagedTest, "packaged.ui_boundary.metadata_denied=true"),
      token(files.packagedTest, "packaged.ui_boundary.raw_globals_absent=true"),
    ]),
    scenario("packaged_entry_close_smoke", "Packaged UI entry, update, close, and raw-global boundary smoke is covered", [
      token(files.packagedTest, "installAndRunUiBridgeEmbeddedNodePackagedApk"),
      token(files.packagedTest, "buildPackagedUiBridgeApk"),
      token(files.packagedTest, "UI_BRIDGE_NODE_PERMISSIONS"),
      token(files.packagedTest, "packaged.ui_bridge.ready=true"),
      token(files.packagedTest, "packaged.ui_bridge.shown=true"),
      token(files.packagedTest, "packaged.ui_bridge.update=true"),
      token(files.packagedTest, "packaged.ui_bridge.close=true"),
      token(files.packagedTest, "packaged.ui_bridge.raw_globals_absent=true"),
      token(files.packagedTest, "packaged.ui_bridge.pending=0"),
    ]),
    scenario("docs_gate", "Phase 13 docs and Gradle gate record the P13-17 promotion state", [
      token(files.p13Report, "Status: partial"),
      token(files.phase13Tasks, "P13-17"),
      token(files.phase13Tasks, "verifyNodePhase13LiveUiProviderPromotion"),
      token(files.gradleGate, "verifyNodePhase13LiveUiProviderPromotion"),
      token(files.testingDoc, "verifyNodePhase13LiveUiProviderPromotion"),
      token(files.ciGatesDoc, "verifyNodePhase13LiveUiProviderPromotion"),
      token(files.compatibilityProfile, "P13-17"),
      token(files.securityModel, "P13-17"),
      token(files.proComparison, "P13-17"),
      token(files.proAutoxNotes, "P13-17"),
      token(files.capabilityMatrix, "P13-17 live UI provider promotion"),
      token(files.surfaceManifestDoc, "phase13-live-ui-provider-promotion"),
    ], [
      "Add packaged UI task relaunch, crash recovery, and user-visible disclosure evidence.",
    ]),
  ];
  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-17"), "P13-17 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-17 report must exist."),
    check("surface-present", Boolean(proSurface), "`pro.ui_automation_media` surface must exist."),
    check(
      "surface-partial",
      proSurface && ["debug_poc", "provider_poc", "design_locked"].includes(proSurface.status),
      "`pro.ui_automation_media` must show partial/poc state, not planned or promoted.",
    ),
    check(
      "surface-not-promoted",
      proSurface && !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.ui_automation_media` must not be promoted by the P13-17 partial gate.",
    ),
    check(
      "surface-evidence",
      proSurface && (proSurface.evidence || []).some((entry) => entry.includes("phase13-live-ui-provider-promotion")),
      "`pro.ui_automation_media` surface must reference P13-17 evidence.",
    ),
    check(
      "packaged-support-not-overclaimed",
      files.providerRegistry.text.includes("packagedProviderDependent") &&
        files.providerRegistry.text.includes("blocked_until_live_packaged_task_relaunch_crash_disclosure_evidence"),
      "`ui` provider packaged support must remain provider-dependent until live packaged UI evidence lands.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-live-ui-provider-promotion-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13LiveUiProviderPromotion",
    task: "P13-17",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: proSurface || {
      id: "pro.ui_automation_media",
      status: "missing",
      evidence: [],
    },
    scenarios,
    checks,
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : finalBlockerClosable ? "ready" : "partial",
      phase13TaskComplete: finalBlockerClosable,
      finalBlockerClosable,
      liveActivityProviderReady: scenarioById(scenarios, "live_activity_provider").status === "ready",
      jsonLifecycleReady: scenarioById(scenarios, "json_handle_lifecycle").status === "ready",
      securityBoundaryReady: scenarioById(scenarios, "security_and_boundaries").status === "ready",
      rhinoCompatReady: scenarioById(scenarios, "rhino_compat_and_types").status === "ready",
      configurationChangePolicyReady: scenarioById(scenarios, "configuration_change_policy").status === "ready",
      packagedCapabilityBoundaryReady: scenarioById(scenarios, "packaged_capability_boundary").status === "ready",
      packagedEntryCloseReady: scenarioById(scenarios, "packaged_entry_close_smoke").status === "ready",
      proSurfacePromoted: false,
    },
  };
}

function scenario(id, title, checks, plannedRemaining = []) {
  const missing = checks.filter((entry) => !entry.present).map((entry) => `missing evidence token: ${entry.token}`);
  const remaining = [...missing, ...plannedRemaining];
  return {
    id,
    title,
    status: remaining.length === 0 ? "ready" : missing.length > 0 ? "missing_evidence" : "partial",
    checks,
    remaining,
  };
}

function token(file, value) {
  return {
    file: file.relative,
    token: value,
    present: file.exists && file.text.includes(value),
  };
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing" };
}

function surface(text, id) {
  try {
    const manifest = JSON.parse(stripBom(text));
    return (manifest.surfaces || []).find((entry) => entry.id === id) || null;
  } catch (_) {
    return null;
  }
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Live UI Provider Promotion",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Final blocker closable: \`${report.decision.finalBlockerClosable}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    `- Blockers: ${report.blockers.length}`,
    "",
    "## Scenarios",
    "",
    "| Scenario | Status | Remaining |",
    "| --- | --- | --- |",
  ];
  report.scenarios.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | \`${entry.status}\` | ${escapeTable(entry.remaining.join("; ") || "None.")} |`);
  });
  lines.push("", "## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
  report.checks.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | ${entry.passed ? "passed" : "failed"} | ${escapeTable(entry.message)} |`);
  });
  lines.push("", "## Blockers", "");
  if (report.blockers.length === 0) {
    lines.push("None.");
  } else {
    report.blockers.forEach((entry) => lines.push(`- ${entry}`));
  }
  return lines.join("\n") + "\n";
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    } else if (arg === "--fail-on-blocked") {
      options.failOnBlocked = true;
    }
  }
  return options;
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8");
  } catch (_) {
    return "";
  }
}

function stripBom(text) {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|");
}

main();
