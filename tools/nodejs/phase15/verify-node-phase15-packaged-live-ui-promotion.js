#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-packaged-live-ui-promotion";
const REQUIRED_READY_EVIDENCE = Object.freeze([
  "phase13_packaged_capability_boundary",
  "phase13_packaged_entry_update_close",
  "phase13_android12_live_activity_lifecycle",
  "phase14_provider_poc_contract",
  "phase15_disclosure_rollback_contract",
  "general_packaged_crash_lifecycle_anchor",
]);
const REQUIRED_BLOCKERS = Object.freeze([
  "packaged_live_ui_task_relaunch_archive",
  "packaged_live_ui_task_removal_cleanup_archive",
  "packaged_live_ui_crash_recovery_archive",
  "packaged_live_ui_user_visible_disclosure_archive",
  "android_15_plus_packaged_live_ui_archive",
  "phase15_security_type_doc_sync",
]);

const FILES = {
  plan: "docs/nodejs/phase15/packaged-live-ui-promotion-plan.json",
  planDoc: "docs/nodejs/phase15/PHASE15_PACKAGED_LIVE_UI_PROMOTION.md",
  taskReport: "docs/nodejs/reports/phase15/P15_04_PACKAGED_LIVE_UI_PROMOTION.md",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  promotionContracts: "docs/nodejs/phase15/node-v1-3-promotion-contracts.json",
  promotionReport: "build/reports/nodejs/phase15-promotion-contracts.json",
  disclosureAudit: "docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json",
  disclosureReport: "build/reports/nodejs/phase15-disclosure-rollback-audit.json",
  phase14Report: "docs/nodejs/reports/phase14/P14_07_LIVE_UI_PROVIDER_PACKAGED.md",
  phase14GeneratedReport: "build/reports/nodejs/phase14-live-ui-provider-packaged.json",
  phase13Report: "docs/nodejs/reports/phase13/P13_17_LIVE_UI_PROVIDER_PROMOTION.md",
  phase13GeneratedReport: "build/reports/nodejs/phase13-live-ui-provider-promotion.json",
  packagedTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  privacyPolicy: "docs/nodejs/security/PRIVACY_DISCLOSURE_POLICY.md",
  rollbackPlaybook: "docs/nodejs/security/PROFILE_ROLLBACK_PLAYBOOK.md",
  uiDesign: "docs/nodejs/design/UI_BRIDGE.md",
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
  console.log("Phase 15 packaged live UI promotion: " + report.decision.status);
  console.log("Phase 15 packaged live UI blockers: " + report.blockers.length);
  console.log("Phase 15 packaged live UI policy violations: " + report.policyViolations.length);
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
  const phase14Report = parseJson(files.phase14GeneratedReport.text);
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-04") || {};
  const liveUiContract = (contracts.providerPromotionBars || []).find((entry) => entry.id === "live_ui") || {};
  const liveUiAudit = (disclosureAudit.providerAudits || []).find((entry) => entry.providerId === "live_ui") || {};
  const scenarios = [
    scenario("phase13_packaged_capability_boundary", "P13 packaged capability boundary imported", [
      token(files.phase13Report, "Packaged capability boundary:"),
      token(files.phase13GeneratedReport, "\"packagedCapabilityBoundaryReady\": true"),
      token(files.packagedTest, "installAndRunUiCapabilityBoundaryEmbeddedNodePackagedApk"),
      token(files.packagedTest, "packaged.ui_boundary.capability_denied=true"),
      token(files.packagedTest, "packaged.ui_boundary.raw_globals_absent=true"),
      token(files.packagedTest, "packaged.ui_boundary.pending=0"),
    ]),
    scenario("phase13_packaged_entry_update_close", "P13 packaged entry/update/close imported", [
      token(files.phase13Report, "Packaged entry/close smoke:"),
      token(files.phase13GeneratedReport, "\"packagedEntryCloseReady\": true"),
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
      token(files.phase14GeneratedReport, "\"promotionState\": \"provider_poc\""),
      token(files.phase14GeneratedReport, "\"futurePromotionBlockers\": 3"),
    ]),
    scenario("phase15_disclosure_rollback_contract", "P15 disclosure and rollback audit imported", [
      token(files.disclosureAudit, "\"providerId\": \"live_ui\""),
      token(files.disclosureAudit, "\"disableFlag\": \"node.provider.live_ui.enabled\""),
      token(files.disclosureAudit, "\"diagnosticCode\": \"NODE_PROVIDER_LIVE_UI_DISABLED\""),
      token(files.disclosureAudit, "\"crashCircuitBreakerRequired\": true"),
      token(files.disclosureReport, "\"status\": \"ready\""),
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
      plan.schema === "autojs6-node-phase15-packaged-live-ui-promotion-plan-v1" &&
        plan.task === "P15-04" &&
        plan.sourceBacklogTask === "P14-07" &&
        plan.providerId === "live_ui" &&
        plan.status === "partial" &&
        plan.promotionState === "provider_poc_not_promoted" &&
        plan.runtimeAuthorityChanged === false,
      "P15-04 plan must stay partial and must not change runtime authority.",
    ),
    check(
      "ready-evidence-declared",
      sameSet((plan.readyEvidence || []).map((entry) => entry.id), REQUIRED_READY_EVIDENCE) &&
        REQUIRED_READY_EVIDENCE.every((id) => files.planDoc.text.includes(id)),
      "P15-04 plan and docs must declare the imported ready evidence set.",
    ),
    check(
      "promotion-blockers-declared",
      sameSet((plan.openBlockers || []).map((entry) => entry.id), REQUIRED_BLOCKERS) &&
        plan.openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false) &&
        REQUIRED_BLOCKERS.every((id) => files.taskReport.text.includes(id)),
      "P15-04 must keep future-promotion blockers visible and non-release-blocking.",
    ),
    check(
      "phase15-contracts-ready",
      statusOf(promotionReport) === "ready" &&
        liveUiContract.currentState === "provider_poc" &&
        Array.isArray(liveUiContract.requiredEvidence) &&
        liveUiContract.requiredEvidence.includes("packaged_apk_evidence") &&
        liveUiContract.requiredEvidence.includes("user_visible_disclosure") &&
        liveUiContract.requiredEvidence.includes("rollback_disable_path"),
      "P15-04 requires ready promotion contracts with live_ui still provider_poc.",
    ),
    check(
      "phase15-disclosure-audit-ready",
      statusOf(disclosureReport) === "ready" &&
        liveUiAudit.currentState === "provider_poc" &&
        liveUiAudit.disableFlag === "node.provider.live_ui.enabled" &&
        liveUiAudit.diagnosticCode === "NODE_PROVIDER_LIVE_UI_DISABLED" &&
        liveUiAudit.foregroundNotificationRequired === true &&
        liveUiAudit.stopActionRequired === true &&
        liveUiAudit.crashCircuitBreakerRequired === true,
      "P15-04 requires ready disclosure rollback audit for live_ui.",
    ),
    check(
      "phase14-source-partial",
      statusOf(phase14Report) === "partial" &&
        phase14Report.decision &&
        phase14Report.decision.promotionState === "provider_poc" &&
        phase14Report.decision.proSurfacePromoted === false,
      "P14-07 source report must remain partial provider_poc, not promoted.",
    ),
    check(
      "scenario-evidence-present",
      scenarios.every((entry) => entry.status === "ready"),
      "All imported P15-04 ready-evidence scenarios must have source tokens present.",
    ),
    check(
      "surface-manifest-boundary",
      p15Mapping.ownerSurface === "pro.ui_overlay_automation_media" &&
        p15Mapping.sourceBacklogTasks &&
        p15Mapping.sourceBacklogTasks.includes("P14-07") &&
        p15Mapping.status === "planned" &&
        p15Mapping.gate === ":app:verifyNodePhase15PackagedLiveUiPromotion",
      "Surface manifest must keep P15-04 planned until promotion evidence closes.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase15Gradle.text.includes("verifyNodePhase15PackagedLiveUiPromotion") &&
        files.phase15Gradle.text.includes("verify-node-phase15-packaged-live-ui-promotion.js") &&
        files.phase15Readme.text.includes(":app:verifyNodePhase15PackagedLiveUiPromotion") &&
        files.testing.text.includes(":app:verifyNodePhase15PackagedLiveUiPromotion") &&
        files.ciGates.text.includes(":app:verifyNodePhase15PackagedLiveUiPromotion"),
      "Gradle, README, TESTING, and CI_GATES must reference the P15-04 live UI promotion gate.",
    ),
    check(
      "task-plan-partial",
      files.phase15Tasks.text.includes("- [ ] P15-04: Close packaged live UI relaunch and crash recovery.") &&
        files.phase15Tasks.text.includes("Partial gate deployed:") &&
        files.phase15Tasks.text.includes("phase15-packaged-live-ui-promotion.json"),
      "P15-04 must remain unchecked while documenting the partial gate and output report.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-packaged-live-ui-promotion-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase15PackagedLiveUiPromotion",
    task: "P15-04",
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
    "# Phase 15 Packaged Live UI Promotion",
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
      console.log("Usage: node verify-node-phase15-packaged-live-ui-promotion.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
