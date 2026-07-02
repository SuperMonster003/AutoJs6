#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-overlay-provider-promotion";
const REQUIRED_READY_EVIDENCE = Object.freeze([
  "phase14_runtime_overlay_provider_poc",
  "permission_denied_and_raw_boundary",
  "conditional_permission_granted_fixture",
  "packaged_overlay_boundary",
  "phase15_disclosure_rollback_contract",
  "phase15_live_ui_foundation_gate",
]);
const REQUIRED_BLOCKERS = Object.freeze([
  "overlay_permission_granted_android12_archive",
  "overlay_permission_granted_android15_archive",
  "overlay_foreground_disclosure_stop_action_archive",
  "overlay_owner_cleanup_leak_stress_archive",
  "packaged_overlay_positive_smoke_archive",
  "overlay_oem_matrix_archive",
  "phase15_security_type_doc_sync",
]);

const FILES = {
  plan: "docs/nodejs/phase15/overlay-provider-promotion-plan.json",
  planDoc: "docs/nodejs/phase15/PHASE15_OVERLAY_PROVIDER_PROMOTION.md",
  taskReport: "docs/nodejs/reports/phase15/P15_05_OVERLAY_PROVIDER_PROMOTION.md",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  promotionContracts: "docs/nodejs/phase15/node-v1-3-promotion-contracts.json",
  promotionReport: "build/reports/nodejs/phase15-promotion-contracts.json",
  disclosureAudit: "docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json",
  disclosureReport: "build/reports/nodejs/phase15-disclosure-rollback-audit.json",
  liveUiReport: "build/reports/nodejs/phase15-packaged-live-ui-promotion.json",
  phase14Report: "docs/nodejs/reports/phase14/P14_08_OVERLAY_PROVIDER_V1.md",
  phase14GeneratedReport: "build/reports/nodejs/phase14-overlay-provider.json",
  phase13Report: "docs/nodejs/reports/phase13/P13_18_OVERLAY_PROVIDER_PROMOTION.md",
  phase13GeneratedReport: "build/reports/nodejs/phase13-overlay-provider-promotion.json",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  overlayTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeOverlayBridgeInstrumentationTest.kt",
  packagedTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  overlayTypes: "docs/nodejs/types/autojs6-node/ui_overlay.d.ts",
  overlayDesign: "docs/nodejs/design/OVERLAY_PROVIDER_V1.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
  phase15Gradle: "app/node-phase15-gate.gradle.kts",
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
  console.log("Phase 15 overlay provider promotion: " + report.decision.status);
  console.log("Phase 15 overlay provider blockers: " + report.blockers.length);
  console.log("Phase 15 overlay provider policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0) {
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
  const plan = parseJson(files.plan.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContracts.text);
  const disclosureAudit = parseJson(files.disclosureAudit.text);
  const promotionReport = parseJson(files.promotionReport.text);
  const disclosureReport = parseJson(files.disclosureReport.text);
  const liveUiReport = parseJson(files.liveUiReport.text);
  const phase14Report = parseJson(files.phase14GeneratedReport.text);
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-05") || {};
  const overlayContract = (contracts.providerPromotionBars || []).find((entry) => entry.id === "overlay") || {};
  const overlayAudit = (disclosureAudit.providerAudits || []).find((entry) => entry.providerId === "overlay") || {};
  const scenarios = [
    scenario("phase14_runtime_overlay_provider_poc", "P14 controlled overlay provider POC imported", [
      token(files.phase14Report, "promotionState: provider_poc"),
      token(files.phase14GeneratedReport, "\"runtimeProviderPocReady\": true"),
      token(files.protocol, "dispatchOverlayShow"),
      token(files.protocol, "ControlledOverlaySession"),
      token(files.protocol, "NodeBridgeResourceType.OVERLAY_SESSION"),
      token(files.protocol, "closeOverlaySession"),
      token(files.providerRegistry, "\"p14ProviderState\" to \"provider_poc\""),
    ]),
    scenario("permission_denied_and_raw_boundary", "Permission denied and raw-object boundaries imported", [
      token(files.phase14GeneratedReport, "\"permissionDeniedBoundaryReady\": true"),
      token(files.protocol, "Settings.canDrawOverlays(appContext)"),
      token(files.protocol, "NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED"),
      token(files.providerRegistry, "WindowManager, LayoutParams, FloatyWindow, WindowBridge, View, and NativeView are not exposed"),
      token(files.permissionManifest, "UI_OVERLAY_PERMISSION"),
      token(files.overlayTypes, 'declare module "ui.overlay"'),
    ]),
    scenario("conditional_permission_granted_fixture", "Permission-granted positive fixture exists but is not archived as promotion evidence", [
      token(files.phase14GeneratedReport, "\"conditionalDeviceTestReady\": true"),
      token(files.overlayTest, "kotlinOverlayRegistryManifestAndProviderPocAreStable"),
      token(files.overlayTest, "show/update/drain/close"),
      token(files.overlayTest, 'method = "update"'),
      token(files.overlayTest, 'method = "drainEvents"'),
      token(files.overlayTest, 'method = "close"'),
      token(files.overlayTest, "Settings.canDrawOverlays(context)"),
    ]),
    scenario("packaged_overlay_boundary", "P13 packaged overlay missing-capability boundary imported", [
      token(files.phase13Report, "Packaged boundary:"),
      token(files.phase13GeneratedReport, "\"packagedOverlayBoundaryReady\": true"),
      token(files.packagedTest, "installAndRunOverlayCapabilityBoundaryEmbeddedNodePackagedApk"),
      token(files.packagedTest, "packaged.overlay_boundary.raw_globals_absent=true"),
      token(files.packagedTest, "packaged.overlay_boundary.pending=0"),
    ]),
    scenario("phase15_disclosure_rollback_contract", "P15 disclosure and rollback audit imported", [
      token(files.disclosureAudit, "\"providerId\": \"overlay\""),
      token(files.disclosureAudit, "\"disableFlag\": \"node.provider.overlay.enabled\""),
      token(files.disclosureAudit, "\"diagnosticCode\": \"NODE_PROVIDER_OVERLAY_DISABLED\""),
      token(files.disclosureAudit, "\"crashCircuitBreakerRequired\": true"),
      token(files.disclosureReport, "\"status\": \"ready\""),
    ]),
    scenario("phase15_live_ui_foundation_gate", "P15-04 live UI foundation gate exists", [
      token(files.liveUiReport, "\"task\": \"P15-04\""),
      token(files.liveUiReport, "\"status\": \"partial\""),
      token(files.liveUiReport, "\"runtimeAuthorityChanged\": false"),
    ]),
  ];
  const checks = [
    check(
      "plan-schema",
      plan.schema === "autojs6-node-phase15-overlay-provider-promotion-plan-v1" &&
        plan.task === "P15-05" &&
        plan.sourceBacklogTask === "P14-08" &&
        plan.providerId === "overlay" &&
        plan.status === "partial" &&
        plan.promotionState === "provider_poc_not_promoted" &&
        plan.runtimeAuthorityChanged === false,
      "P15-05 plan must stay partial and must not change runtime authority.",
    ),
    check(
      "ready-evidence-declared",
      sameSet((plan.readyEvidence || []).map((entry) => entry.id), REQUIRED_READY_EVIDENCE) &&
        REQUIRED_READY_EVIDENCE.every((id) => files.planDoc.text.includes(id)),
      "P15-05 plan and docs must declare the imported ready evidence set.",
    ),
    check(
      "promotion-blockers-declared",
      sameSet((plan.openBlockers || []).map((entry) => entry.id), REQUIRED_BLOCKERS) &&
        plan.openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false) &&
        REQUIRED_BLOCKERS.every((id) => files.taskReport.text.includes(id)),
      "P15-05 must keep future-promotion blockers visible and non-release-blocking.",
    ),
    check(
      "phase15-contracts-ready",
      statusOf(promotionReport) === "ready" &&
        overlayContract.currentState === "provider_poc" &&
        Array.isArray(overlayContract.requiredEvidence) &&
        overlayContract.requiredEvidence.includes("connected_device_evidence") &&
        overlayContract.requiredEvidence.includes("user_visible_disclosure") &&
        overlayContract.requiredEvidence.includes("rollback_disable_path"),
      "P15-05 requires ready promotion contracts with overlay still provider_poc.",
    ),
    check(
      "phase15-disclosure-audit-ready",
      statusOf(disclosureReport) === "ready" &&
        overlayAudit.currentState === "provider_poc" &&
        overlayAudit.disableFlag === "node.provider.overlay.enabled" &&
        overlayAudit.diagnosticCode === "NODE_PROVIDER_OVERLAY_DISABLED" &&
        overlayAudit.foregroundNotificationRequired === true &&
        overlayAudit.stopActionRequired === true &&
        overlayAudit.crashCircuitBreakerRequired === true,
      "P15-05 requires ready disclosure rollback audit for overlay.",
    ),
    check(
      "phase14-source-partial",
      statusOf(phase14Report) === "partial" &&
        phase14Report.decision &&
        phase14Report.decision.promotionState === "provider_poc" &&
        phase14Report.decision.proSurfacePromoted === false,
      "P14-08 source report must remain partial provider_poc, not promoted.",
    ),
    check(
      "live-ui-foundation-present",
      statusOf(liveUiReport) === "partial" &&
        liveUiReport.decision &&
        liveUiReport.decision.runtimeAuthorityChanged === false,
      "P15-05 requires P15-04 live UI foundation gate report to be present.",
    ),
    check(
      "scenario-evidence-present",
      scenarios.every((entry) => entry.status === "ready"),
      "All imported P15-05 ready-evidence scenarios must have source tokens present.",
    ),
    check(
      "surface-manifest-boundary",
      p15Mapping.ownerSurface === "pro.ui_overlay_automation_media" &&
        p15Mapping.sourceBacklogTasks &&
        p15Mapping.sourceBacklogTasks.includes("P14-08") &&
        p15Mapping.status === "planned" &&
        p15Mapping.gate === ":app:verifyNodePhase15OverlayProviderPromotion",
      "Surface manifest must keep P15-05 planned until promotion evidence closes.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase15Gradle.text.includes("verifyNodePhase15OverlayProviderPromotion") &&
        files.phase15Gradle.text.includes("verify-node-phase15-overlay-provider-promotion.js") &&
        files.phase15Readme.text.includes(":app:verifyNodePhase15OverlayProviderPromotion") &&
        files.testing.text.includes(":app:verifyNodePhase15OverlayProviderPromotion") &&
        files.ciGates.text.includes(":app:verifyNodePhase15OverlayProviderPromotion"),
      "Gradle, README, TESTING, and CI_GATES must reference the P15-05 overlay provider promotion gate.",
    ),
    check(
      "task-plan-partial",
      files.phase15Tasks.text.includes("- [ ] P15-05: Promote Android overlay provider permission-granted path.") &&
        files.phase15Tasks.text.includes("phase15-overlay-provider-promotion.json") &&
        files.phase15Tasks.text.includes("overlay_permission_granted_android12_archive"),
      "P15-05 must remain unchecked while documenting the partial gate and output report.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-overlay-provider-promotion-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase15OverlayProviderPromotion",
    task: "P15-05",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    scenarios,
    checks,
    policyViolations,
    blockers: (plan.openBlockers || []).map((entry) => ({ ...entry, status: "open" })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc_not_promoted",
      p15TaskComplete: false,
      runtimeAuthorityChanged: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: Array.isArray(plan.openBlockers) ? plan.openBlockers.length : 0,
      overlayPromoted: false,
    },
  };
}

function scenario(id, title, checks) {
  const missing = checks.filter((entry) => !entry.present).map((entry) => `missing evidence token: ${entry.token}`);
  return {
    id,
    title,
    status: missing.length === 0 ? "ready" : "missing_evidence",
    checks,
    remaining: missing,
  };
}

function token(file, value) {
  return {
    file: file.relative,
    token: value,
    present: file.exists && file.text.includes(value),
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 15 Overlay Provider Promotion",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    `- Future promotion blockers: ${report.decision.futurePromotionBlockers}`,
    `- Policy violations: ${report.policyViolations.length}`,
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

function sameSet(actual, expected) {
  if (!Array.isArray(actual) || actual.length !== expected.length) {
    return false;
  }
  const actualSorted = actual.slice().sort();
  const expectedSorted = expected.slice().sort();
  return expectedSorted.every((value, index) => actualSorted[index] === value);
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function statusOf(report) {
  return report.status || (report.decision && report.decision.status) || "missing";
}

function parseJson(text) {
  if (!text || !text.trim()) {
    return {};
  }
  try {
    return JSON.parse(text);
  } catch {
    return {};
  }
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
  } catch {
    return "";
  }
}

function escapeTable(value) {
  return String(value || "").replace(/\|/g, "\\|").replace(/\r?\n/g, "<br>");
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    } else if (arg === "-h" || arg === "--help") {
      console.log("Usage: node verify-node-phase15-overlay-provider-promotion.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
