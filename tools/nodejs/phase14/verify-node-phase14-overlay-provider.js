#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-overlay-provider";
const OPEN_BLOCKERS = Object.freeze([
  {
    id: "overlay_permission_granted_device_evidence",
    classification: "future_promotion",
    requiredEvidence: "Archived Android device run with SYSTEM_ALERT_WINDOW granted and show/update/drain/close passing.",
  },
  {
    id: "overlay_foreground_disclosure_stop_action",
    classification: "future_promotion",
    requiredEvidence: "Foreground notification/disclosure ownership and stop action behavior for visible overlays.",
  },
  {
    id: "packaged_overlay_positive_smoke",
    classification: "future_promotion",
    requiredEvidence: "Packaged APK install-run smoke with explicit overlay metadata, foreground disclosure, cleanup, task removal, and relaunch/crash evidence.",
  },
  {
    id: "overlay_leak_stress_evidence",
    classification: "future_promotion",
    requiredEvidence: "Real overlay leak/stress run proving zero remaining sessions and bounded event queues.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_08_OVERLAY_PROVIDER_V1.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  promotionContractsJson: "docs/nodejs/phase14/phase14-promotion-contracts.json",
  promotionContractsDoc: "docs/nodejs/phase14/PHASE14_PROMOTION_CONTRACTS.md",
  phase13Report: "docs/nodejs/reports/phase13/P13_18_OVERLAY_PROVIDER_PROMOTION.md",
  phase13Verifier: "tools/nodejs/phase13/verify-overlay-provider-promotion.js",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  overlayTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeOverlayBridgeInstrumentationTest.kt",
  packagedTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  overlayTypes: "docs/nodejs/types/autojs6-node/ui_overlay.d.ts",
  overlayDesign: "docs/nodejs/design/OVERLAY_PROVIDER_V1.md",
  floatyDesign: "docs/nodejs/design/FLOATY_NODE.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  privacyPolicy: "docs/nodejs/security/PRIVACY_DISCLOSURE_POLICY.md",
  rollbackPlaybook: "docs/nodejs/security/PROFILE_ROLLBACK_PLAYBOOK.md",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
  gradle14: "app/node-phase14-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot, reportDir);
  const report = buildReport(files);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 14 overlay provider: " + report.decision.status);
  console.log("Phase 14 overlay provider blockers: " + report.blockers.length);
  console.log("Phase 14 overlay provider policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot, reportDir) {
  const entries = Object.entries(FILES).map(([key, relative]) => {
    const file = path.join(repoRoot, relative);
    return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
  });
  const phase13Generated = path.join(reportDir, "phase13-overlay-provider-promotion.json");
  entries.push([
    "phase13GeneratedReport",
    {
      relative: path.relative(repoRoot, phase13Generated).replace(/\\/g, "/"),
      exists: fs.existsSync(phase13Generated),
      text: read(phase13Generated),
    },
  ]);
  return Object.fromEntries(entries);
}

function buildReport(files) {
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContractsJson.text);
  const proSurface = (manifest.surfaces || []).find((entry) => entry.id === "pro.ui_automation_media") || null;
  const disclosure = contracts.packagedHighPrivilegeDisclosure || {};
  const scenarios = [
    scenario("phase13_overlay_boundary_imported", "P13 overlay boundary and packaged denial evidence remain imported", [
      token(files.phase13Report, "Status: partial"),
      token(files.phase13GeneratedReport, '"packagedOverlayBoundaryReady": true'),
      token(files.packagedTest, "installAndRunOverlayCapabilityBoundaryEmbeddedNodePackagedApk"),
      token(files.packagedTest, "packaged.overlay_boundary.raw_globals_absent=true"),
      token(files.packagedTest, "packaged.overlay_boundary.pending=0"),
    ]),
    scenario("p14_runtime_provider_poc", "P14 runtime provider exposes controlled show/update/drainEvents/close POC", [
      token(files.protocol, "dispatchOverlayShow"),
      token(files.protocol, "ControlledOverlaySession"),
      token(files.protocol, "WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY"),
      token(files.protocol, "NodeBridgeResourceType.OVERLAY_SESSION"),
      token(files.protocol, "overlaySessions[id] = session"),
      token(files.protocol, "overlaySessionIds += id"),
      token(files.protocol, "closeOverlaySession"),
      token(files.protocol, "p14_08_provider_poc"),
      token(files.providerRegistry, '"p14LiveMethods" to "show,update,drainEvents,close"'),
      token(files.providerRegistry, '"p14ProviderState" to "provider_poc"'),
      token(files.providerRegistry, '"phase14Promotion" to "P14-08 live overlay provider POC gate"'),
    ]),
    scenario("permission_and_raw_boundary", "Overlay provider fails closed without permission and keeps raw Android objects out", [
      token(files.protocol, "Settings.canDrawOverlays(appContext)"),
      token(files.protocol, "AutoJs6 ui.overlay.show requires android.permission.SYSTEM_ALERT_WINDOW"),
      token(files.providerRegistry, "WindowManager, LayoutParams, FloatyWindow, WindowBridge, View, and NativeView are not exposed"),
      token(files.overlayTest, "ui.overlay live creation must fail closed without SYSTEM_ALERT_WINDOW"),
      token(files.overlayTest, "NodeBridgeErrorCategory.PERMISSION_DENIED"),
      token(files.overlayTest, "Settings.canDrawOverlays(context)"),
      token(files.permissionManifest, 'const val UI_OVERLAY = "ui.overlay"'),
      token(files.permissionManifest, 'const val UI_OVERLAY_PERMISSION = "ui.overlay.permission"'),
      token(files.overlayTypes, 'declare module "ui.overlay"'),
    ]),
    scenario("conditional_device_test", "Instrumentation exercises positive provider behavior when overlay permission is already granted", [
      token(files.overlayTest, "kotlinOverlayRegistryManifestAndProviderPocAreStable"),
      token(files.overlayTest, "show/update/drain/close"),
      token(files.overlayTest, 'method = "update"'),
      token(files.overlayTest, 'method = "drainEvents"'),
      token(files.overlayTest, 'method = "close"'),
      token(files.overlayTest, 'embedded_script.overlay_active_session_count'),
      token(files.overlayTest, 'assertTrue(events.resultJson.orEmpty().contains("shown"))'),
      token(files.overlayTest, 'assertTrue(events.resultJson.orEmpty().contains("update"))'),
    ], OPEN_BLOCKERS.map((entry) => entry.id)),
    scenario("phase14_docs_and_gradle_gate", "Phase 14 docs and Gradle wiring expose the P14-08 gate", [
      token(files.gradle14, "verifyNodePhase14OverlayProvider"),
      token(files.gradle14, "verify-node-phase14-overlay-provider.js"),
      token(files.phase14Tasks, "Provider POC deployed:"),
      token(files.phase14Readme, ":app:verifyNodePhase14OverlayProvider"),
      token(files.testing, ":app:verifyNodePhase14OverlayProvider"),
      token(files.ciGates, ":app:verifyNodePhase14OverlayProvider"),
      token(files.surfaceManifestDoc, "`pro.ui_automation_media` | `provider_poc`"),
      token(files.overlayDesign, "P14-08 adds a controlled Android overlay provider POC"),
      token(files.securityModel, "P14-08 adds a provider POC for `ui.overlay`"),
      token(files.privacyPolicy, "`ui_overlay`"),
      token(files.rollbackPlaybook, "`bridge_provider_crash`"),
    ]),
  ];
  const checks = [
    check("phase13-generated-report-present", files.phase13GeneratedReport.exists, "The Phase 13 overlay provider report must be generated before the P14-08 gate."),
    check("phase14-report-present", files.phase14Report.exists && files.phase14Report.text.includes("P14-08"), "P14-08 report must exist."),
    check(
      "surface-provider-poc-not-promoted",
      proSurface &&
        proSurface.status === "provider_poc" &&
        !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.ui_automation_media` must stay provider_poc, not opt-in or default promoted.",
    ),
    check(
      "surface-evidence",
      proSurface &&
        includesAll(proSurface.evidence || [], [
          "docs/nodejs/reports/phase14/P14_08_OVERLAY_PROVIDER_V1.md",
          "tools/nodejs/phase14/verify-node-phase14-overlay-provider.js",
          ":app:verifyNodePhase14OverlayProvider",
          "build/reports/nodejs/phase14-overlay-provider.json",
          "build/reports/nodejs/phase14-overlay-provider.md",
        ]),
      "`pro.ui_automation_media` evidence must include the P14-08 report, verifier, Gradle gate, and output reports.",
    ),
    check(
      "contracts-cover-overlay",
      includesAll(disclosure.requiredForSurfaces || [], ["overlay"]) &&
        includesAll(disclosure.requiredFields || [], [
          "foregroundNotificationOwnership",
          "stopAction",
          "taskRemovalCleanup",
          "relaunchBehavior",
          "crashRecoveryBehavior",
          "privacyDisclosure",
          "rollbackBehavior",
        ]),
      "Phase 14 promotion contracts must cover overlay disclosure, cleanup, relaunch, crash recovery, privacy, and rollback fields.",
    ),
    check(
      "task-plan-keeps-p14-08-open",
      files.phase14Tasks.text.includes("- [ ] P14-08:") &&
        files.phase14Tasks.text.includes("Provider POC deployed:") &&
        files.phase14Tasks.text.includes("overlay_permission_granted_device_evidence") &&
        files.phase14Tasks.text.includes("overlay_foreground_disclosure_stop_action") &&
        files.phase14Tasks.text.includes("packaged_overlay_positive_smoke") &&
        files.phase14Tasks.text.includes("overlay_leak_stress_evidence"),
      "P14-08 must remain unchecked while the provider POC records the open overlay promotion blockers.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.gradle14.text.includes("verifyNodePhase14OverlayProvider") &&
        files.phase14Readme.text.includes(":app:verifyNodePhase14OverlayProvider") &&
        files.testing.text.includes(":app:verifyNodePhase14OverlayProvider") &&
        files.ciGates.text.includes(":app:verifyNodePhase14OverlayProvider"),
      "Gradle, README, TESTING, and CI_GATES must reference the P14-08 overlay provider gate.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase14-overlay-provider-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase14OverlayProvider",
    task: "P14-08",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: proSurface || {
      id: "pro.ui_automation_media",
      status: "missing",
      evidence: [],
    },
    scenarios,
    checks,
    policyViolations,
    blockers: OPEN_BLOCKERS.map((entry) => ({
      ...entry,
      status: "open",
    })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc",
      p14TaskComplete: false,
      runtimeProviderPocReady: scenarioById(scenarios, "p14_runtime_provider_poc").status === "ready",
      permissionDeniedBoundaryReady: scenarioById(scenarios, "permission_and_raw_boundary").status === "ready",
      conditionalDeviceTestReady: scenarioById(scenarios, "conditional_device_test").status !== "missing_evidence",
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: OPEN_BLOCKERS.length,
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

function includesAll(values, requiredValues) {
  if (Array.isArray(values)) {
    return requiredValues.every((value) => values.includes(value));
  }
  return requiredValues.every((value) => String(values).includes(value));
}

function parseJson(text) {
  try {
    return JSON.parse(stripBom(text || "{}"));
  } catch (_) {
    return {};
  }
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Overlay Provider",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    `- Future promotion blockers: ${report.decision.futurePromotionBlockers}`,
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
  lines.push("", "## Open Blockers", "", "| Blocker | Classification | Required Evidence |", "| --- | --- | --- |");
  report.blockers.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | \`${entry.classification}\` | ${escapeTable(entry.requiredEvidence)} |`);
  });
  return lines.join("\n") + "\n";
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-node-phase14-overlay-provider.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    }
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    } else {
      throw new Error("Unexpected argument: " + arg);
    }
  }
  return options;
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
  } catch (_) {
    return "";
  }
}

function stripBom(text) {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

main();
