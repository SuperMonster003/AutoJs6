#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-overlay-oem-matrix";
const REQUIRED_READY_EVIDENCE = Object.freeze([
  "phase14_oem_matrix_contract",
  "phase14_denied_boundary_import",
  "phase15_overlay_provider_promotion_gate",
  "phase15_promotion_contracts",
  "phase15_disclosure_rollback_contract",
  "phase15_surface_manifest_mapping",
]);
const REQUIRED_DEVICE_CLASSES = Object.freeze(["android_12_class", "android_15_plus_class"]);
const REQUIRED_DEVICE_FIELDS = Object.freeze([
  "deviceSerial",
  "deviceModel",
  "androidVersion",
  "apiLevel",
  "packageIdentity",
  "profile",
  "capabilities",
  "androidPermissionsRequested",
  "androidPermissionsGranted",
  "notificationPermissionState",
  "backgroundStartBehavior",
  "settingsRoundTripBehavior",
  "taskRemovalBehavior",
  "relaunchBehavior",
  "crashRecoveryBehavior",
  "foregroundDisclosureBehavior",
  "stopActionBehavior",
  "leakOrResourceSummary",
  "oemQuirks",
  "reportPath",
]);
const REQUIRED_OVERLAY_SCENARIOS = Object.freeze([
  "SYSTEM_ALERT_WINDOW denied fail-closed copy",
  "SYSTEM_ALERT_WINDOW granted show/update/drainEvents/close",
  "openPermissionSettings settings round-trip",
  "task removal cleanup",
  "package relaunch behavior",
  "OEM permission/settings behavior notes",
]);
const REQUIRED_BLOCKERS = Object.freeze([
  "overlay_android12_granted_device_archive",
  "overlay_android15_plus_granted_device_archive",
  "overlay_settings_round_trip_oem_notes",
  "overlay_task_removal_relaunch_evidence",
  "overlay_foreground_disclosure_stop_action_archive",
  "phase15_security_type_doc_sync",
]);

const FILES = {
  plan: "docs/nodejs/phase15/overlay-oem-matrix-plan.json",
  planDoc: "docs/nodejs/phase15/PHASE15_OVERLAY_OEM_MATRIX.md",
  taskReport: "docs/nodejs/reports/phase15/P15_06_OVERLAY_OEM_MATRIX.md",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  promotionContracts: "docs/nodejs/phase15/node-v1-3-promotion-contracts.json",
  promotionReport: "build/reports/nodejs/phase15-promotion-contracts.json",
  disclosureAudit: "docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json",
  disclosureReport: "build/reports/nodejs/phase15-disclosure-rollback-audit.json",
  overlayProviderPlan: "docs/nodejs/phase15/overlay-provider-promotion-plan.json",
  overlayProviderReport: "build/reports/nodejs/phase15-overlay-provider-promotion.json",
  phase14Matrix: "docs/nodejs/phase14/overlay-permission-oem-matrix.json",
  phase14MatrixDoc: "docs/nodejs/phase14/PHASE14_OVERLAY_PERMISSION_OEM_MATRIX.md",
  phase14TaskReport: "docs/nodejs/reports/phase14/P14_09_OVERLAY_PERMISSION_OEM_MATRIX.md",
  phase14GeneratedReport: "build/reports/nodejs/phase14-overlay-permission-oem-matrix.json",
  phase14OverlayReport: "build/reports/nodejs/phase14-overlay-provider.json",
  phase15Gradle: "app/node-phase15-gate.gradle.kts",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
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
  console.log("Phase 15 overlay OEM matrix: " + report.decision.status);
  console.log("Phase 15 overlay OEM matrix blockers: " + report.blockers.length);
  console.log("Phase 15 overlay OEM matrix policy violations: " + report.policyViolations.length);
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
  const overlayProviderPlan = parseJson(files.overlayProviderPlan.text);
  const overlayProviderReport = parseJson(files.overlayProviderReport.text);
  const phase14Matrix = parseJson(files.phase14Matrix.text);
  const phase14GeneratedReport = parseJson(files.phase14GeneratedReport.text);
  const phase14OverlayReport = parseJson(files.phase14OverlayReport.text);
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-06") || {};
  const overlayContract = (contracts.providerPromotionBars || []).find((entry) => entry.id === "overlay") || {};
  const overlayAudit = (disclosureAudit.providerAudits || []).find((entry) => entry.providerId === "overlay") || {};
  const phase14Rows = Array.isArray(phase14Matrix.evidenceRows) ? phase14Matrix.evidenceRows : [];
  const p14Blockers = (phase14GeneratedReport.blockers || []).map((entry) => entry.id);
  const scenarios = [
    scenario("phase14_matrix_imported", "P14-09 matrix contract is imported without promotion claims", [
      checkTokenValue("phase14_matrix_schema", phase14Matrix.schema === "autojs6-node-phase14-overlay-permission-oem-matrix-v1"),
      checkTokenValue("phase14_matrix_task", phase14Matrix.task === "P14-09"),
      checkTokenValue("phase14_matrix_status", phase14Matrix.status === "partial"),
      checkTokenValue("phase14_matrix_poc", phase14Matrix.promotionState === "provider_poc"),
      checkTokenValue("phase14_generated_status", statusOf(phase14GeneratedReport) === "partial"),
      checkTokenValue("phase14_generated_gate_ready", phase14GeneratedReport.decision && phase14GeneratedReport.decision.matrixGateReady === true),
      checkTokenValue("phase14_generated_not_promoted", phase14GeneratedReport.decision && phase14GeneratedReport.decision.proSurfacePromoted === false),
      token(files.phase14TaskReport, "The matrix is a promotion contract and report gate, not a"),
    ]),
    scenario("required_device_slots_reserved", "Required Android 12 and Android 15+ device rows remain reserved", [
      checkTokenValue("plan_required_device_classes", sameSet(plan.requiredDeviceClasses || [], REQUIRED_DEVICE_CLASSES)),
      checkTokenValue("phase14_required_device_classes", sameSet(phase14Matrix.requiredDeviceClasses || [], REQUIRED_DEVICE_CLASSES)),
      checkTokenValue("plan_required_fields", includesAll(plan.requiredFields || [], REQUIRED_DEVICE_FIELDS)),
      checkTokenValue("phase14_required_fields", includesAll(phase14Matrix.requiredFields || [], REQUIRED_DEVICE_FIELDS)),
      checkTokenValue("plan_required_scenarios", includesAll(plan.requiredOverlayScenarios || [], REQUIRED_OVERLAY_SCENARIOS)),
      checkTokenValue("phase14_required_scenarios", includesAll(phase14Matrix.requiredOverlayScenarios || [], REQUIRED_OVERLAY_SCENARIOS)),
      checkTokenValue("android_12_row_reserved", hasRow(phase14Rows, "android_12_class", "missing_device_archive")),
      checkTokenValue("android_15_plus_row_reserved", hasRow(phase14Rows, "android_15_plus_class", "missing_device_archive")),
      checkTokenValue("phase14_rows_have_required_fields", phase14Rows.every((row) => REQUIRED_DEVICE_FIELDS.every((field) => Object.prototype.hasOwnProperty.call(row, field)))),
    ], REQUIRED_BLOCKERS.slice(0, 4)),
    scenario("phase15_overlay_provider_dependency", "P15-05 overlay provider promotion gate remains partial and points to this matrix", [
      checkTokenValue("overlay_provider_status", statusOf(overlayProviderReport) === "partial"),
      checkTokenValue("overlay_provider_not_promoted", overlayProviderReport.decision && overlayProviderReport.decision.overlayPromoted === false),
      checkTokenValue("overlay_provider_future_blocker", (overlayProviderPlan.requiredPromotionEvidence || []).includes("overlay_oem_matrix_archive")),
      token(files.overlayProviderReport, "\"runtimeAuthorityChanged\": false"),
      token(files.overlayProviderReport, "\"overlayPromoted\": false"),
    ]),
    scenario("phase15_contracts_and_rollback", "Phase 15 contracts keep overlay promotion behind evidence, disclosure, and rollback bars", [
      checkTokenValue("promotion_report_ready", statusOf(promotionReport) === "ready"),
      checkTokenValue("disclosure_report_ready", statusOf(disclosureReport) === "ready"),
      checkTokenValue("overlay_contract_provider_poc", overlayContract.currentState === "provider_poc"),
      checkTokenValue("overlay_contract_p15_task", includesAll(overlayContract.p15Tasks || [], ["P15-05", "P15-06", "P15-07"])),
      checkTokenValue("overlay_contract_evidence_bars", includesAll(overlayContract.requiredEvidence || [], [
        "connected_device_evidence",
        "packaged_apk_evidence",
        "user_visible_disclosure",
        "rollback_disable_path",
        "raw_object_denial_regression",
      ])),
      checkTokenValue("overlay_audit_provider_poc", overlayAudit.currentState === "provider_poc"),
      checkTokenValue("overlay_audit_disable_flag", overlayAudit.disableFlag === "node.provider.overlay.enabled"),
      checkTokenValue("overlay_audit_disclosure_stop", overlayAudit.foregroundNotificationRequired === true && overlayAudit.stopActionRequired === true),
      checkTokenValue("overlay_audit_crash_breaker", overlayAudit.crashCircuitBreakerRequired === true),
    ]),
    scenario("docs_surface_and_gradle_wiring", "Phase 15 docs, surface manifest, and Gradle expose the P15-06 gate", [
      checkTokenValue("surface_mapping_owner", p15Mapping.ownerSurface === "pro.ui_overlay_automation_media"),
      checkTokenValue("surface_mapping_backlog", includesAll(p15Mapping.sourceBacklogTasks || [], ["P14-09"])),
      checkTokenValue("surface_mapping_evidence_class", p15Mapping.evidenceClass === "device_oem_matrix"),
      checkTokenValue("surface_mapping_gate", p15Mapping.gate === ":app:verifyNodePhase15OverlayOemMatrix"),
      checkTokenValue("surface_mapping_planned", p15Mapping.status === "planned"),
      token(files.phase15Gradle, "verifyNodePhase15OverlayOemMatrix"),
      token(files.phase15Gradle, "verify-node-phase15-overlay-oem-matrix.js"),
      token(files.phase15Readme, ":app:verifyNodePhase15OverlayOemMatrix"),
      token(files.testing, ":app:verifyNodePhase15OverlayOemMatrix"),
      token(files.ciGates, ":app:verifyNodePhase15OverlayOemMatrix"),
      token(files.phase15Tasks, "phase15-overlay-oem-matrix.json"),
      token(files.phase15Tasks, "overlay_android12_granted_device_archive"),
    ]),
  ];
  const checks = [
    check(
      "plan-schema",
      plan.schema === "autojs6-node-phase15-overlay-oem-matrix-plan-v1" &&
        plan.task === "P15-06" &&
        plan.sourceBacklogTask === "P14-09" &&
        plan.providerId === "overlay" &&
        plan.status === "partial" &&
        plan.promotionState === "provider_poc_not_promoted" &&
        plan.runtimeAuthorityChanged === false,
      "P15-06 plan must stay partial and must not change runtime authority.",
    ),
    check(
      "ready-evidence-declared",
      sameSet((plan.readyEvidence || []).map((entry) => entry.id), REQUIRED_READY_EVIDENCE) &&
        REQUIRED_READY_EVIDENCE.every((id) => files.planDoc.text.includes(id)),
      "P15-06 plan and docs must declare the imported ready evidence set.",
    ),
    check(
      "promotion-blockers-declared",
      sameSet((plan.openBlockers || []).map((entry) => entry.id), REQUIRED_BLOCKERS) &&
        sameSet(plan.requiredPromotionEvidence || [], REQUIRED_BLOCKERS) &&
        plan.openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false) &&
        REQUIRED_BLOCKERS.every((id) => files.taskReport.text.includes(id)),
      "P15-06 must keep future-promotion blockers visible and non-release-blocking.",
    ),
    check(
      "phase14-matrix-ready",
      statusOf(phase14GeneratedReport) === "partial" &&
        phase14GeneratedReport.decision &&
        phase14GeneratedReport.decision.matrixGateReady === true &&
        phase14GeneratedReport.decision.requiredDeviceArchivesReady === false &&
        REQUIRED_BLOCKERS.slice(0, 4).every((id) => p14Blockers.includes(id)),
      "P15-06 requires the P14-09 matrix report to be present and partial with device archives still open.",
    ),
    check(
      "phase14-overlay-provider-imported",
      statusOf(phase14OverlayReport) === "partial" &&
        phase14OverlayReport.decision &&
        phase14OverlayReport.decision.proSurfacePromoted === false,
      "P15-06 imports the P14 overlay provider boundary without claiming promotion.",
    ),
    check(
      "phase15-overlay-provider-partial",
      statusOf(overlayProviderReport) === "partial" &&
        overlayProviderReport.decision &&
        overlayProviderReport.decision.overlayPromoted === false &&
        overlayProviderReport.decision.runtimeAuthorityChanged === false,
      "P15-06 depends on the P15-05 overlay provider gate remaining partial and unpromoted.",
    ),
    check(
      "phase15-contracts-ready",
      statusOf(promotionReport) === "ready" &&
        overlayContract.currentState === "provider_poc" &&
        includesAll(overlayContract.requiredEvidence || [], ["connected_device_evidence", "user_visible_disclosure", "rollback_disable_path"]),
      "P15-06 requires ready promotion contracts with overlay still provider_poc.",
    ),
    check(
      "phase15-disclosure-audit-ready",
      statusOf(disclosureReport) === "ready" &&
        overlayAudit.currentState === "provider_poc" &&
        overlayAudit.disableFlag === "node.provider.overlay.enabled" &&
        overlayAudit.diagnosticCode === "NODE_PROVIDER_OVERLAY_DISABLED" &&
        overlayAudit.foregroundNotificationRequired === true &&
        overlayAudit.stopActionRequired === true,
      "P15-06 requires ready disclosure rollback audit for overlay.",
    ),
    check(
      "scenario-evidence-present",
      scenarios.every((entry) => entry.status === "ready" || entry.status === "partial"),
      "All P15-06 evidence scenarios must be either ready or explicitly partial due to planned device archives.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase15Gradle.text.includes("verifyNodePhase15OverlayOemMatrix") &&
        files.phase15Gradle.text.includes("verify-node-phase15-overlay-oem-matrix.js") &&
        files.phase15Readme.text.includes(":app:verifyNodePhase15OverlayOemMatrix") &&
        files.testing.text.includes(":app:verifyNodePhase15OverlayOemMatrix") &&
        files.ciGates.text.includes(":app:verifyNodePhase15OverlayOemMatrix"),
      "Gradle, README, TESTING, and CI_GATES must reference the P15-06 overlay OEM matrix gate.",
    ),
    check(
      "task-plan-partial",
      files.phase15Tasks.text.includes("- [ ] P15-06: Close overlay permission and OEM matrix.") &&
        files.phase15Tasks.text.includes("phase15-overlay-oem-matrix.json") &&
        files.phase15Tasks.text.includes("overlay_android15_plus_granted_device_archive"),
      "P15-06 must remain unchecked while documenting the partial gate and output report.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-overlay-oem-matrix-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase15OverlayOemMatrix",
    task: "P15-06",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    matrix: {
      sourceStatus: phase14Matrix.status || "missing",
      sourcePromotionState: phase14Matrix.promotionState || "missing",
      requiredDeviceClasses: phase14Matrix.requiredDeviceClasses || [],
      evidenceRows: phase14Rows.map((row) => ({
        id: row.id,
        deviceClass: row.deviceClass,
        status: row.status,
        reportPath: row.reportPath,
      })),
    },
    scenarios,
    checks,
    policyViolations,
    blockers: (plan.openBlockers || []).map((entry) => ({ ...entry, status: "open" })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc_not_promoted",
      p15TaskComplete: false,
      matrixGateReady: scenarioById(scenarios, "phase14_matrix_imported").status === "ready",
      requiredDeviceArchivesReady: false,
      runtimeAuthorityChanged: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: Array.isArray(plan.openBlockers) ? plan.openBlockers.length : 0,
      overlayPromoted: false,
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

function checkTokenValue(tokenName, present) {
  return {
    file: "parsed-json",
    token: tokenName,
    present: Boolean(present),
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 15 Overlay OEM Matrix",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    `- Future promotion blockers: ${report.decision.futurePromotionBlockers}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Matrix Rows",
    "",
    "| Row | Device class | Status | Report path |",
    "| --- | --- | --- | --- |",
  ];
  report.matrix.evidenceRows.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | \`${entry.deviceClass}\` | \`${entry.status}\` | ${escapeTable(entry.reportPath || "")} |`);
  });
  lines.push("", "## Scenarios", "", "| Scenario | Status | Remaining |", "| --- | --- | --- |");
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

function includesAll(values, requiredValues) {
  if (Array.isArray(values)) {
    return requiredValues.every((value) => values.includes(value));
  }
  return requiredValues.every((value) => String(values).includes(value));
}

function hasRow(rows, deviceClass, status) {
  return rows.some((row) => row.deviceClass === deviceClass && row.status === status);
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing" };
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
      console.log("Usage: node verify-node-phase15-overlay-oem-matrix.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
