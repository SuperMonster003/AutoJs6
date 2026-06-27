#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-overlay-provider-promotion";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_18_OVERLAY_PROVIDER_PROMOTION.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  executionModePolicy: "app/src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt",
  androidManifest: "app/src/main/AndroidManifest.xml",
  projectConfig: "app/src/main/java/org/autojs/autojs/project/ProjectConfig.java",
  apkBuilder: "app/src/main/java/org/autojs/autojs/apkbuilder/ApkBuilder.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  overlayTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeOverlayBridgeInstrumentationTest.kt",
  uiOverlayTypes: "docs/nodejs/types/autojs6-node/ui_overlay.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typeSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  floatyNode: "docs/nodejs/design/FLOATY_NODE.md",
  overlayProviderDesign: "docs/nodejs/design/OVERLAY_PROVIDER_V1.md",
  rhinoFloatyDesign: "docs/nodejs/design/RHINO_FLOATY_COMPATIBILITY.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
  phase12Verifier: "tools/nodejs/phase12/verify-rhino-compat-overlay-live-gate.js",
  phase12Summary: "docs/nodejs/reports/phase12/PHASE12_SUMMARY.md",
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
  console.log("Phase 13 overlay provider promotion: " + report.decision.status);
  console.log("Phase 13 overlay provider promotion blockers: " + report.blockers.length);
  console.log("Phase 13 overlay provider promotion policy violations: " + report.policyViolations.length);
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
    scenario("permission_settings_cleanup_gate", "Android live gate exposes permission, settings, and cleanup evidence", [
      token(files.providerRegistry, '"phase13Promotion" to "P13-18 partial overlay promotion evidence gate"'),
      token(files.providerRegistry, '"liveGateMethods" to "hasPermission,openPermissionSettings,closeAll"'),
      token(files.providerRegistry, '"permissionGranted" to runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false).toString()'),
      token(files.protocol, "overlayPermissionSnapshot"),
      token(files.protocol, "dispatchOverlayOpenPermissionSettings"),
      token(files.protocol, "Settings.ACTION_MANAGE_OVERLAY_PERMISSION"),
      token(files.protocol, "embedded_script.overlay_permission_settings_launch_count"),
      token(files.protocol, 'overlayCloseAllSummary("p12_14_live_gate_no_active_provider")'),
      token(files.overlayTest, "settings-intent-dry-run"),
      token(files.overlayTest, "rawWindowLeaked"),
    ]),
    scenario("fail_closed_live_creation", "Live show/update/drainEvents/close remains blocked until a real Android overlay provider lands", [
      token(files.providerRegistry, '"liveCreationPromotion" to "blocked_until_promoted_show_update_drainEvents_close_provider"'),
      token(files.providerRegistry, '"blockedLiveMethods" to "show,update,drainEvents,close"'),
      token(files.providerRegistry, "Android show/update/drainEvents/close creation path fails closed"),
      token(files.protocol, "overlayLiveProviderMissing"),
      token(files.protocol, "P12-14 exposes only permission/settings/cleanup live-gate evidence"),
      token(files.overlayTest, "ui.overlay live creation must remain fail-closed after the P12-14 live gate"),
      token(files.p13Report, "Live overlay creation remains fail-closed."),
    ], [
      "Implement actual Android live overlay show/update/drainEvents/close provider.",
      "Record granted and denied overlay permission device matrix, including OEM settings behavior.",
      "Connect foreground notification/disclosure ownership and stop action before live overlay creation is promoted.",
    ]),
    scenario("capability_packaged_policy", "Overlay authority requires explicit capability and packaged metadata", [
      token(files.permissionManifest, 'const val UI_OVERLAY = "ui.overlay"'),
      token(files.permissionManifest, 'const val UI_OVERLAY_PERMISSION = "ui.overlay.permission"'),
      token(files.permissionManifest, 'const val UI_OVERLAY_FOREGROUND = "ui.overlay.foreground"'),
      token(files.permissionManifest, 'const val UI_OVERLAY_PACKAGED = "ui.overlay.packaged"'),
      token(files.androidManifest, "android.permission.SYSTEM_ALERT_WINDOW"),
      token(files.projectConfig, "android.permission.SYSTEM_ALERT_WINDOW"),
      token(files.apkBuilder, "mergedPermissions"),
      token(files.overlayTest, 'NodeBridgePermissionManifest.requiredCapabilitiesFor("ui.overlay", "show")'),
      token(files.overlayTest, "ui.overlay manifest capability regression"),
      token(files.executionModePolicy, "embedded_script.execution_mode_allowed_providers"),
      token(files.providerRegistry, '"packagedPromotion" to "blocked_until_live_packaged_overlay_permission_foreground_disclosure_cleanup_and_leak_evidence"'),
    ], [
      "Add packaged install-run overlay smoke with explicit SYSTEM_ALERT_WINDOW metadata.",
      "Add packaged denial evidence for missing overlay capability, missing packaged overlay metadata, and missing disclosure.",
    ]),
    scenario("fake_provider_types_and_raw_boundary", "JS facade, type declarations, and raw Window/View denial remain aligned", [
      token(files.nodeBridgeSources, "function __autojs6_limited_ui_overlay()"),
      token(files.nodeBridgeSources, 'return "ui.overlay";'),
      token(files.nodeBridgeSources, "rejects raw WindowManager.LayoutParams"),
      token(files.nodeBridgeSources, "does not allow hidden overlays"),
      token(files.nodeBridgeSources, "does not allow fully transparent overlays"),
      token(files.overlayTest, "jsOverlayUsesFakeTransportAndBlocksShadowing"),
      token(files.overlayTest, "overlay.requests=ui.overlay.show|ui.overlay.update|ui.overlay.drainEvents|ui.overlay.close|ui.overlay.closeAll|ui.overlay.hasPermission|ui.overlay.openPermissionSettings"),
      token(files.overlayTest, 'assertFalse(NodeBridgeModules.supports("ui.overlay", "rawWindow"))'),
      token(files.providerRegistry, "WindowManager, LayoutParams, FloatyWindow, WindowBridge, View, and NativeView are not exposed"),
      token(files.permissionManifest, "const val FLOATY_RAW"),
      token(files.permissionManifest, "const val FLOATY_TOUCH"),
      token(files.uiOverlayTypes, 'declare module "ui.overlay"'),
      token(files.typeSmoke, 'import overlay = require("ui.overlay")'),
      token(files.typeSmoke, "overlay.OverlayPermissionSnapshot"),
      token(files.typeSmoke, "Promise<overlay.OverlayHandle>"),
    ]),
    scenario("docs_gate", "Phase 13 docs and Gradle gate record P13-18 as partial", [
      token(files.p13Report, "Status: partial"),
      token(files.phase13Tasks, "P13-18"),
      token(files.phase13Tasks, "verifyNodePhase13OverlayProviderPromotion"),
      token(files.gradleGate, "verifyNodePhase13OverlayProviderPromotion"),
      token(files.testingDoc, "verifyNodePhase13OverlayProviderPromotion"),
      token(files.ciGatesDoc, "verifyNodePhase13OverlayProviderPromotion"),
      token(files.compatibilityProfile, "P13-18"),
      token(files.securityModel, "P13-18"),
      token(files.proComparison, "P13-18"),
      token(files.proAutoxNotes, "P13-18"),
      token(files.floatyNode, "P13-18"),
      token(files.overlayProviderDesign, "P13-18"),
      token(files.rhinoFloatyDesign, "P13-18"),
      token(files.typesReadme, "P13-18"),
      token(files.capabilityMatrix, "P13-18 overlay provider promotion"),
      token(files.phase12Verifier, "verifyNodeRhinoCompatOverlayLiveGate"),
      token(files.phase12Summary, "P12-14"),
      token(files.surfaceManifestDoc, "phase13-overlay-provider-promotion"),
    ], []),
  ];
  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-18"), "P13-18 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-18 report must exist."),
    check("surface-present", Boolean(proSurface), "`pro.ui_automation_media` surface must exist."),
    check(
      "surface-partial",
      proSurface && ["debug_poc", "provider_poc", "design_locked"].includes(proSurface.status),
      "`pro.ui_automation_media` must show partial/poc state, not planned or promoted.",
    ),
    check(
      "surface-not-promoted",
      proSurface && !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.ui_automation_media` must not be promoted by the P13-18 partial gate.",
    ),
    check(
      "surface-evidence",
      proSurface && (proSurface.evidence || []).some((entry) => entry.includes("phase13-overlay-provider-promotion")),
      "`pro.ui_automation_media` surface must reference P13-18 evidence.",
    ),
    check(
      "provider-not-overclaimed",
      files.providerRegistry.text.includes("UI_OVERLAY_PROVIDER -> NodeBridgeProviderStatus.DEGRADED") &&
        files.providerRegistry.text.includes('"blockedLiveMethods" to "show,update,drainEvents,close"') &&
        files.providerRegistry.text.includes("blocked_until_promoted_show_update_drainEvents_close_provider"),
      "`ui.overlay` provider must remain degraded and fail-closed for live creation methods.",
    ),
    check(
      "execution-mode-not-overclaimed",
      !files.executionModePolicy.text.includes('"ui.overlay"'),
      "Execution mode policy must not list ui.overlay as an allowed foreground/background provider yet.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-overlay-provider-promotion-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13OverlayProviderPromotion",
    task: "P13-18",
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
      permissionSettingsCleanupGateReady: scenarioById(scenarios, "permission_settings_cleanup_gate").status === "ready",
      liveCreationProviderReady: false,
      capabilityPackagedPolicyReady: scenarioById(scenarios, "capability_packaged_policy").status === "ready",
      rawBoundaryReady: scenarioById(scenarios, "fake_provider_types_and_raw_boundary").status === "ready",
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
    "# Phase 13 Overlay Provider Promotion",
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
