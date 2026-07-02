#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase16-overlay-provider-promotion";
const REQUIRED_READY_EVIDENCE = Object.freeze([
  "phase14_runtime_overlay_provider_poc",
  "permission_denied_and_raw_boundary",
  "conditional_permission_granted_fixture",
  "packaged_overlay_boundary",
  "phase15_overlay_provider_partial_gate",
  "phase16_promotion_contract",
  "phase16_disclosure_rollback_contract",
  "phase16_live_ui_foundation_gate",
]);
const REQUIRED_BLOCKERS = Object.freeze([
  "overlay_permission_granted_android12_archive",
  "overlay_permission_granted_android15_archive",
  "overlay_foreground_disclosure_stop_action_archive",
  "overlay_owner_cleanup_leak_stress_archive",
  "packaged_overlay_positive_smoke_archive",
  "overlay_oem_matrix_archive",
  "phase16_security_type_doc_sync",
]);

const FILES = {
  plan: "docs/nodejs/phase16/overlay-provider-promotion-plan.json",
  planDoc: "docs/nodejs/phase16/PHASE16_OVERLAY_PROVIDER_PROMOTION.md",
  taskReport: "docs/nodejs/reports/phase16/P16_05_OVERLAY_PROVIDER_PROMOTION.md",
  phase16Tasks: "docs/nodejs/reports/phase16/PHASE16_NODE_V1_4_PROVIDER_PROMOTION_TASKS.md",
  phase16Readme: "docs/nodejs/reports/phase16/README.md",
  surfaceManifest: "docs/nodejs/phase16/phase16-surface-manifest.json",
  promotionContracts: "docs/nodejs/phase16/node-v1-4-promotion-contracts.json",
  promotionReport: "build/reports/nodejs/phase16-promotion-contracts.json",
  disclosureAudit: "docs/nodejs/phase16/node-v1-4-disclosure-rollback-audit.json",
  disclosureReport: "build/reports/nodejs/phase16-disclosure-rollback-audit.json",
  liveUiReport: "build/reports/nodejs/phase16-packaged-live-ui-promotion.json",
  phase15Plan: "docs/nodejs/phase15/overlay-provider-promotion-plan.json",
  phase15PlanDoc: "docs/nodejs/phase15/PHASE15_OVERLAY_PROVIDER_PROMOTION.md",
  phase15TaskReport: "docs/nodejs/reports/phase15/P15_05_OVERLAY_PROVIDER_PROMOTION.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_08_OVERLAY_PROVIDER_V1.md",
  phase13Report: "docs/nodejs/reports/phase13/P13_18_OVERLAY_PROVIDER_PROMOTION.md",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  overlayTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeOverlayBridgeInstrumentationTest.kt",
  packagedTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  overlayTypes: "docs/nodejs/types/autojs6-node/ui_overlay.d.ts",
  overlayDesign: "docs/nodejs/design/OVERLAY_PROVIDER_V1.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  errorCatalog: "docs/nodejs/ERROR_CATALOG.md",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
  phase16Gradle: "app/node-phase16-gate.gradle.kts",
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
  console.log("Phase 16 overlay provider promotion: " + report.decision.status);
  console.log("Phase 16 overlay provider blockers: " + report.blockers.length);
  console.log("Phase 16 overlay provider policy violations: " + report.policyViolations.length);
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
  const phase15Plan = parseJson(files.phase15Plan.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContracts.text);
  const disclosureAudit = parseJson(files.disclosureAudit.text);
  const promotionReport = parseJson(files.promotionReport.text);
  const disclosureReport = parseJson(files.disclosureReport.text);
  const liveUiReport = parseJson(files.liveUiReport.text);
  const p16Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P16-05") || {};
  const overlayContract = (contracts.providerPromotionBars || []).find((entry) => entry.providerId === "overlay") || {};
  const overlayAudit = (disclosureAudit.providerAudits || []).find((entry) => entry.providerId === "overlay") || {};
  const scenarios = [
    scenario("phase14_runtime_overlay_provider_poc", "P14 controlled overlay provider POC imported", [
      token(files.phase14Report, "promotionState: provider_poc"),
      token(files.phase14Report, "ControlledOverlaySession"),
      token(files.protocol, "dispatchOverlayShow"),
      token(files.protocol, "ControlledOverlaySession"),
      token(files.protocol, "NodeBridgeResourceType.OVERLAY_SESSION"),
      token(files.protocol, "closeOverlaySession"),
      token(files.providerRegistry, "\"p14ProviderState\" to \"provider_poc\""),
    ]),
    scenario("permission_denied_and_raw_boundary", "Permission denied and raw-object boundaries imported", [
      token(files.phase14Report, "Raw boundary:"),
      token(files.protocol, "Settings.canDrawOverlays(appContext)"),
      token(files.protocol, "NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED"),
      token(files.providerRegistry, "WindowManager, LayoutParams, FloatyWindow, WindowBridge, View, and NativeView are not exposed"),
      token(files.permissionManifest, "UI_OVERLAY_PERMISSION"),
      token(files.overlayTypes, 'declare module "ui.overlay"'),
    ]),
    scenario("conditional_permission_granted_fixture", "Permission-granted positive fixture exists but is not archived as promotion evidence", [
      token(files.overlayTest, "P14-08 conditional positive path: show/update/drain/close."),
      token(files.overlayTest, 'method = "update"'),
      token(files.overlayTest, 'method = "drainEvents"'),
      token(files.overlayTest, 'method = "close"'),
      token(files.overlayTest, "Settings.canDrawOverlays(context)"),
      token(files.overlayTest, "embedded_script.overlay_active_session_count"),
    ]),
    scenario("packaged_overlay_boundary", "P13 packaged overlay missing-capability boundary imported", [
      token(files.phase13Report, "Packaged boundary:"),
      token(files.packagedTest, "installAndRunOverlayCapabilityBoundaryEmbeddedNodePackagedApk"),
      token(files.packagedTest, "packaged.overlay_boundary.raw_globals_absent=true"),
      token(files.packagedTest, "packaged.overlay_boundary.pending=0"),
    ]),
    scenario("phase15_overlay_provider_partial_gate", "P15 partial overlay provider gate imported", [
      token(files.phase15TaskReport, "Status: partial"),
      token(files.phase15TaskReport, "Promotion state: `provider_poc_not_promoted`"),
      token(files.phase15TaskReport, "verifyNodePhase15OverlayProviderPromotion"),
      token(files.phase15PlanDoc, "phase15_security_type_doc_sync"),
    ]),
    scenario("phase16_live_ui_foundation_gate", "P16-04 live UI foundation gate exists", [
      token(files.liveUiReport, "\"task\": \"P16-04\""),
      token(files.liveUiReport, "\"status\": \"partial\""),
      token(files.liveUiReport, "\"runtimeAuthorityChanged\": false"),
    ]),
  ];
  const checks = [
    check(
      "plan-schema",
      plan.schema === "autojs6-node-phase16-overlay-provider-promotion-plan-v1" &&
        plan.task === "P16-05" &&
        plan.sourceBacklogTask === "P15-05" &&
        plan.providerId === "overlay" &&
        plan.status === "partial" &&
        plan.promotionState === "provider_poc_not_promoted" &&
        plan.runtimeAuthorityChanged === false,
      "P16-05 plan must stay partial and must not change runtime authority.",
    ),
    check(
      "source-backlog-partial",
      phase15Plan.schema === "autojs6-node-phase15-overlay-provider-promotion-plan-v1" &&
        phase15Plan.task === "P15-05" &&
        phase15Plan.providerId === "overlay" &&
        phase15Plan.status === "partial" &&
        files.phase15TaskReport.text.includes("provider_poc_not_promoted"),
      "P16-05 must import the P15-05 partial overlay source backlog without treating it as promoted.",
    ),
    check(
      "ready-evidence-declared",
      sameSet((plan.readyEvidence || []).map((entry) => entry.id), REQUIRED_READY_EVIDENCE) &&
        REQUIRED_READY_EVIDENCE.every((id) => files.planDoc.text.includes(id)),
      "P16-05 plan and docs must declare the imported ready evidence set.",
    ),
    check(
      "promotion-blockers-declared",
      sameSet((plan.openBlockers || []).map((entry) => entry.id), REQUIRED_BLOCKERS) &&
        plan.openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false) &&
        REQUIRED_BLOCKERS.every((id) => files.taskReport.text.includes(id)),
      "P16-05 must keep future-promotion blockers visible and non-release-blocking.",
    ),
    check(
      "phase16-contracts-passed",
      statusOf(promotionReport) === "passed" &&
        overlayContract.currentState === "provider_poc" &&
        Array.isArray(overlayContract.p16Tasks) &&
        ["P16-05", "P16-06", "P16-07"].every((task) => overlayContract.p16Tasks.includes(task)) &&
        Array.isArray(overlayContract.sourceBacklogTasks) &&
        ["P15-05", "P15-06", "P15-07"].every((task) => overlayContract.sourceBacklogTasks.includes(task)) &&
        Array.isArray(overlayContract.requiredEvidence) &&
        overlayContract.requiredEvidence.includes("connected_device_evidence") &&
        overlayContract.requiredEvidence.includes("user_visible_disclosure") &&
        overlayContract.requiredEvidence.includes("rollback_disable_path"),
      "P16-05 requires passed promotion contracts with overlay still provider_poc.",
    ),
    check(
      "phase16-disclosure-audit-passed",
      statusOf(disclosureReport) === "passed" &&
        overlayAudit.currentState === "provider_poc" &&
        Array.isArray(overlayAudit.p16Tasks) &&
        ["P16-05", "P16-06", "P16-07"].every((task) => overlayAudit.p16Tasks.includes(task)) &&
        overlayAudit.disableFlag === "node.provider.overlay.enabled" &&
        overlayAudit.diagnosticCode === "NODE_PROVIDER_OVERLAY_DISABLED" &&
        overlayAudit.foregroundNotificationRequired === true &&
        overlayAudit.stopActionRequired === true &&
        overlayAudit.crashCircuitBreakerRequired === true,
      "P16-05 requires passed disclosure rollback audit for overlay.",
    ),
    check(
      "live-ui-foundation-present",
      statusOf(liveUiReport) === "partial" &&
        liveUiReport.decision &&
        liveUiReport.decision.runtimeAuthorityChanged === false,
      "P16-05 requires P16-04 live UI foundation gate report to be present.",
    ),
    check(
      "scenario-evidence-present",
      scenarios.every((entry) => entry.status === "ready"),
      "All imported P16-05 ready-evidence scenarios must have source tokens present.",
    ),
    check(
      "surface-manifest-boundary",
      p16Mapping.ownerSurface === "pro.ui_overlay_automation_media" &&
        p16Mapping.sourceBacklogTasks &&
        p16Mapping.sourceBacklogTasks.includes("P15-05") &&
        p16Mapping.status === "planned" &&
        p16Mapping.gate === ":app:verifyNodePhase16OverlayProviderPromotion",
      "Surface manifest must keep P16-05 planned until overlay promotion evidence closes.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase16Gradle.text.includes("verifyNodePhase16OverlayProviderPromotion") &&
        files.phase16Gradle.text.includes("verify-node-phase16-overlay-provider-promotion.js") &&
        files.phase16Readme.text.includes(":app:verifyNodePhase16OverlayProviderPromotion") &&
        files.testing.text.includes(":app:verifyNodePhase16OverlayProviderPromotion") &&
        files.ciGates.text.includes(":app:verifyNodePhase16OverlayProviderPromotion") &&
        files.securityModel.text.includes("P16-05") &&
        files.errorCatalog.text.includes("P16-05"),
      "Gradle, README, TESTING, CI_GATES, security model, and error catalog must reference the P16-05 overlay provider promotion gate.",
    ),
    check(
      "task-plan-partial",
      files.phase16Tasks.text.includes("- [ ] P16-05: Promote Android overlay provider permission-granted path.") &&
        files.phase16Tasks.text.includes("phase16-overlay-provider-promotion.json") &&
        files.phase16Tasks.text.includes("overlay_permission_granted_android12_archive"),
      "P16-05 must remain unchecked while documenting the partial gate and output report.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase16-overlay-provider-promotion-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase16OverlayProviderPromotion",
    task: "P16-05",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    scenarios,
    checks,
    policyViolations,
    blockers: (plan.openBlockers || []).map((entry) => ({ ...entry, status: "open" })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc_not_promoted",
      p16TaskComplete: false,
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
    "# Phase 16 Overlay Provider Promotion",
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
      console.log("Usage: node verify-node-phase16-overlay-provider-promotion.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
