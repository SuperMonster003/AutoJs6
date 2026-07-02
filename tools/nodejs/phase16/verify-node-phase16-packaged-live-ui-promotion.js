#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase16-packaged-live-ui-promotion";
const REQUIRED_READY_EVIDENCE = Object.freeze([
  "phase13_packaged_capability_boundary",
  "phase13_packaged_entry_update_close",
  "phase13_android12_live_activity_lifecycle",
  "phase14_provider_poc_contract",
  "phase15_packaged_live_ui_partial_gate",
  "phase16_promotion_contract",
  "phase16_disclosure_rollback_contract",
  "general_packaged_crash_lifecycle_anchor",
]);
const REQUIRED_BLOCKERS = Object.freeze([
  "packaged_live_ui_task_relaunch_archive",
  "packaged_live_ui_task_removal_cleanup_archive",
  "packaged_live_ui_crash_recovery_archive",
  "packaged_live_ui_user_visible_disclosure_archive",
  "android_15_plus_packaged_live_ui_archive",
  "phase16_security_type_doc_sync",
]);

const FILES = {
  plan: "docs/nodejs/phase16/packaged-live-ui-promotion-plan.json",
  planDoc: "docs/nodejs/phase16/PHASE16_PACKAGED_LIVE_UI_PROMOTION.md",
  taskReport: "docs/nodejs/reports/phase16/P16_04_PACKAGED_LIVE_UI_PROMOTION.md",
  phase16Tasks: "docs/nodejs/reports/phase16/PHASE16_NODE_V1_4_PROVIDER_PROMOTION_TASKS.md",
  phase16Readme: "docs/nodejs/reports/phase16/README.md",
  surfaceManifest: "docs/nodejs/phase16/phase16-surface-manifest.json",
  promotionContracts: "docs/nodejs/phase16/node-v1-4-promotion-contracts.json",
  promotionReport: "build/reports/nodejs/phase16-promotion-contracts.json",
  disclosureAudit: "docs/nodejs/phase16/node-v1-4-disclosure-rollback-audit.json",
  disclosureReport: "build/reports/nodejs/phase16-disclosure-rollback-audit.json",
  phase15Plan: "docs/nodejs/phase15/packaged-live-ui-promotion-plan.json",
  phase15PlanDoc: "docs/nodejs/phase15/PHASE15_PACKAGED_LIVE_UI_PROMOTION.md",
  phase15TaskReport: "docs/nodejs/reports/phase15/P15_04_PACKAGED_LIVE_UI_PROMOTION.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_07_LIVE_UI_PROVIDER_PACKAGED.md",
  phase13Report: "docs/nodejs/reports/phase13/P13_17_LIVE_UI_PROVIDER_PROMOTION.md",
  packagedTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  privacyPolicy: "docs/nodejs/security/PRIVACY_DISCLOSURE_POLICY.md",
  rollbackPlaybook: "docs/nodejs/security/PROFILE_ROLLBACK_PLAYBOOK.md",
  uiDesign: "docs/nodejs/design/UI_BRIDGE.md",
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
  console.log("Phase 16 packaged live UI promotion: " + report.decision.status);
  console.log("Phase 16 packaged live UI blockers: " + report.blockers.length);
  console.log("Phase 16 packaged live UI policy violations: " + report.policyViolations.length);
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
  const p16Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P16-04") || {};
  const liveUiContract = (contracts.providerPromotionBars || []).find((entry) => entry.providerId === "live_ui") || {};
  const liveUiAudit = (disclosureAudit.providerAudits || []).find((entry) => entry.providerId === "live_ui") || {};
  const scenarios = [
    scenario("phase13_packaged_capability_boundary", "P13 packaged capability boundary imported", [
      token(files.phase13Report, "Packaged capability boundary:"),
      token(files.packagedTest, "installAndRunUiCapabilityBoundaryEmbeddedNodePackagedApk"),
      token(files.packagedTest, "packaged.ui_boundary.capability_denied=true"),
      token(files.packagedTest, "packaged.ui_boundary.raw_globals_absent=true"),
      token(files.packagedTest, "packaged.ui_boundary.pending=0"),
    ]),
    scenario("phase13_packaged_entry_update_close", "P13 packaged entry/update/close imported", [
      token(files.phase13Report, "Packaged entry/close smoke:"),
      token(files.packagedTest, "installAndRunUiBridgeEmbeddedNodePackagedApk"),
      token(files.packagedTest, "packaged.ui_bridge.shown=true"),
      token(files.packagedTest, "packaged.ui_bridge.update=true"),
      token(files.packagedTest, "packaged.ui_bridge.close=true"),
      token(files.packagedTest, "packaged.ui_bridge.pending=0"),
    ]),
    scenario("phase13_android12_live_activity_lifecycle", "Android 12 live Activity lifecycle evidence imported", [
      token(files.phase13Report, "Android 12"),
      token(files.phase13Report, "androidUiProviderRecordsBackActivityDestroyLifecycleEvent"),
      token(files.phase13Report, "androidUiProviderRecordsRecentsTaskRemovalLifecycleEvent"),
      token(files.phase13Report, "QV710AF65F"),
    ]),
    scenario("phase14_provider_poc_contract", "P14 provider POC boundary imported", [
      token(files.phase14Report, "promotionState: provider_poc"),
      token(files.phase14Report, "packaged_task_relaunch"),
      token(files.phase14Report, "packaged_crash_recovery"),
      token(files.phase14Report, "packaged_user_visible_disclosure"),
    ]),
    scenario("phase15_packaged_live_ui_partial_gate", "P15 partial live UI gate imported", [
      token(files.phase15TaskReport, "Status: partial"),
      token(files.phase15TaskReport, "Promotion state: `provider_poc_not_promoted`"),
      token(files.phase15TaskReport, "verifyNodePhase15PackagedLiveUiPromotion"),
      token(files.phase15PlanDoc, "phase15_security_type_doc_sync"),
    ]),
    scenario("general_packaged_crash_lifecycle_anchor", "Generic packaged crash lifecycle anchor exists but is not live_ui-specific", [
      token(files.packagedTest, "installAndRunCrashEvidenceEmbeddedNodePackagedApk"),
      token(files.packagedTest, "CRASH_EVIDENCE_STOP_MARKERS"),
      token(files.packagedTest, "CRASH_EVIDENCE_TASK_REMOVED_MARKERS"),
      token(files.packagedTest, "CRASH_EVIDENCE_COLLECTION_MARKERS"),
      token(files.packagedTest, "foregroundRecordContainsStopCommand"),
    ]),
  ];
  const checks = [
    check(
      "plan-schema",
      plan.schema === "autojs6-node-phase16-packaged-live-ui-promotion-plan-v1" &&
        plan.task === "P16-04" &&
        plan.sourceBacklogTask === "P15-04" &&
        plan.providerId === "live_ui" &&
        plan.status === "partial" &&
        plan.promotionState === "provider_poc_not_promoted" &&
        plan.runtimeAuthorityChanged === false,
      "P16-04 plan must stay partial and must not change runtime authority.",
    ),
    check(
      "source-backlog-partial",
      phase15Plan.schema === "autojs6-node-phase15-packaged-live-ui-promotion-plan-v1" &&
        phase15Plan.task === "P15-04" &&
        phase15Plan.providerId === "live_ui" &&
        phase15Plan.status === "partial" &&
        files.phase15TaskReport.text.includes("provider_poc_not_promoted"),
      "P16-04 must import the P15-04 partial live UI source backlog without treating it as promoted.",
    ),
    check(
      "ready-evidence-declared",
      sameSet((plan.readyEvidence || []).map((entry) => entry.id), REQUIRED_READY_EVIDENCE) &&
        REQUIRED_READY_EVIDENCE.every((id) => files.planDoc.text.includes(id)),
      "P16-04 plan and docs must declare the imported ready evidence set.",
    ),
    check(
      "promotion-blockers-declared",
      sameSet((plan.openBlockers || []).map((entry) => entry.id), REQUIRED_BLOCKERS) &&
        plan.openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false) &&
        REQUIRED_BLOCKERS.every((id) => files.taskReport.text.includes(id)),
      "P16-04 must keep future-promotion blockers visible and non-release-blocking.",
    ),
    check(
      "phase16-contracts-passed",
      statusOf(promotionReport) === "passed" &&
        liveUiContract.currentState === "provider_poc" &&
        Array.isArray(liveUiContract.p16Tasks) &&
        liveUiContract.p16Tasks.includes("P16-04") &&
        Array.isArray(liveUiContract.sourceBacklogTasks) &&
        liveUiContract.sourceBacklogTasks.includes("P15-04") &&
        Array.isArray(liveUiContract.requiredEvidence) &&
        liveUiContract.requiredEvidence.includes("packaged_apk_evidence") &&
        liveUiContract.requiredEvidence.includes("user_visible_disclosure") &&
        liveUiContract.requiredEvidence.includes("rollback_disable_path"),
      "P16-04 requires passed promotion contracts with live_ui still provider_poc.",
    ),
    check(
      "phase16-disclosure-audit-passed",
      statusOf(disclosureReport) === "passed" &&
        liveUiAudit.currentState === "provider_poc" &&
        Array.isArray(liveUiAudit.p16Tasks) &&
        liveUiAudit.p16Tasks.includes("P16-04") &&
        liveUiAudit.disableFlag === "node.provider.live_ui.enabled" &&
        liveUiAudit.diagnosticCode === "NODE_PROVIDER_LIVE_UI_DISABLED" &&
        liveUiAudit.foregroundNotificationRequired === true &&
        liveUiAudit.stopActionRequired === true &&
        liveUiAudit.crashCircuitBreakerRequired === true,
      "P16-04 requires passed disclosure rollback audit for live_ui.",
    ),
    check(
      "scenario-evidence-present",
      scenarios.every((entry) => entry.status === "ready"),
      "All imported P16-04 ready-evidence scenarios must have source tokens present.",
    ),
    check(
      "surface-manifest-boundary",
      p16Mapping.ownerSurface === "pro.ui_overlay_automation_media" &&
        p16Mapping.sourceBacklogTasks &&
        p16Mapping.sourceBacklogTasks.includes("P15-04") &&
        p16Mapping.status === "planned" &&
        p16Mapping.gate === ":app:verifyNodePhase16PackagedLiveUiPromotion",
      "Surface manifest must keep P16-04 planned until live UI promotion evidence closes.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase16Gradle.text.includes("verifyNodePhase16PackagedLiveUiPromotion") &&
        files.phase16Gradle.text.includes("verify-node-phase16-packaged-live-ui-promotion.js") &&
        files.phase16Readme.text.includes(":app:verifyNodePhase16PackagedLiveUiPromotion") &&
        files.testing.text.includes(":app:verifyNodePhase16PackagedLiveUiPromotion") &&
        files.ciGates.text.includes(":app:verifyNodePhase16PackagedLiveUiPromotion") &&
        files.securityModel.text.includes("P16-04") &&
        files.errorCatalog.text.includes("P16-04"),
      "Gradle, README, TESTING, CI_GATES, security model, and error catalog must reference the P16-04 live UI promotion gate.",
    ),
    check(
      "task-plan-partial",
      files.phase16Tasks.text.includes("- [ ] P16-04: Close packaged live UI relaunch and crash recovery.") &&
        files.phase16Tasks.text.includes("Partial gate deployed:") &&
        files.phase16Tasks.text.includes("phase16-packaged-live-ui-promotion.json"),
      "P16-04 must remain unchecked while documenting the partial gate and output report.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase16-packaged-live-ui-promotion-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase16PackagedLiveUiPromotion",
    task: "P16-04",
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
      liveUiPromoted: false,
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
    "# Phase 16 Packaged Live UI Promotion",
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
      console.log("Usage: node verify-node-phase16-packaged-live-ui-promotion.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
