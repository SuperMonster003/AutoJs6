#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-promotion-contracts";
const REQUIRED_PROMOTION_STATES = Object.freeze([
  "implemented",
  "packaged_ready",
  "device_matrix_ready",
  "policy_ready",
  "stable_denied",
  "future_only",
]);
const REQUIRED_BLOCKER_CLASSIFICATIONS = Object.freeze([
  "release_blocking",
  "future_promotion",
  "external",
  "intentional_non_goal",
]);
const REQUIRED_HIGH_PRIVILEGE_SURFACES = Object.freeze([
  "live_ui",
  "overlay",
  "accessibility",
  "media_projection",
  "recorder",
  "task_database",
  "intent_broadcast",
  "notifications",
  "power_settings",
  "shell_root",
  "profile_switching",
]);
const REQUIRED_DISCLOSURE_FIELDS = Object.freeze([
  "manifestMetadata",
  "installReviewCopy",
  "runReviewCopy",
  "foregroundNotificationOwnership",
  "stopAction",
  "permissionDeniedDiagnostic",
  "undeclaredCapabilityDiagnostic",
  "taskRemovalCleanup",
  "relaunchBehavior",
  "crashRecoveryBehavior",
  "privacyDisclosure",
  "rollbackBehavior",
]);
const REQUIRED_ROLLBACK_FIELDS = Object.freeze([
  "disableFlag",
  "diagnosticCode",
  "userVisibleMessage",
  "safeFallback",
  "dataRetention",
  "packagedDowngradeBehavior",
  "reEnablePath",
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

const FILES = {
  contractsJson: "docs/nodejs/phase14/phase14-promotion-contracts.json",
  contractsDocs: "docs/nodejs/phase14/PHASE14_PROMOTION_CONTRACTS.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  gradle: "app/node-phase14-gate.gradle.kts",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const contracts = JSON.parse(files.contractsJson.text);
  const report = {
    schema: "autojs6-node-phase14-promotion-contracts-report-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase14PromotionContracts",
    task: "P14-02..P14-06",
    status: policyViolations.length === 0 ? "ready" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    contractCounts: {
      capabilityPromotionStates: countArray(contracts.capabilityPromotionStates),
      blockerClassifications: countArray(contracts.reportConventions && contracts.reportConventions.blockerClassifications),
      highPrivilegeSurfaces: countArray(contracts.packagedHighPrivilegeDisclosure && contracts.packagedHighPrivilegeDisclosure.requiredForSurfaces),
      disclosureFields: countArray(contracts.packagedHighPrivilegeDisclosure && contracts.packagedHighPrivilegeDisclosure.requiredFields),
      rollbackFields: countArray(contracts.rollbackAndCircuitBreaker && contracts.rollbackAndCircuitBreaker.rollbackRequiredFields),
      deviceFields: countArray(contracts.deviceEvidenceMatrix && contracts.deviceEvidenceMatrix.requiredFields),
    },
    checks,
    policyViolations,
    blockers: [],
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      promotionVocabularyReady: checkById(checks, "promotion-state-vocabulary"),
      reportConventionsReady: checkById(checks, "report-conventions"),
      disclosureContractReady: checkById(checks, "packaged-disclosure-contract"),
      rollbackContractReady: checkById(checks, "rollback-and-circuit-breaker"),
      deviceMatrixReady: checkById(checks, "device-evidence-matrix"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 14 promotion contracts: " + report.decision.status);
  console.log("Phase 14 promotion contract policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const file = path.join(repoRoot, relative);
      return [key, { relative, text: read(file) }];
    }),
  );
}

function buildChecks(files) {
  const contracts = JSON.parse(files.contractsJson.text);
  const manifest = JSON.parse(files.surfaceManifest.text);
  const promotionSurface = (manifest.surfaces || []).find((surface) => surface.id === "phase14.promotion_contracts");
  const promotionStateIds = (contracts.capabilityPromotionStates || []).map((state) => state.id);
  const blockerClassificationIds = ((contracts.reportConventions || {}).blockerClassifications || []).map((classification) => classification.id);
  const reportFields = (contracts.reportConventions || {}).requiredReportFields || [];
  const statusValues = (contracts.reportConventions || {}).statusValues || [];
  const disclosure = contracts.packagedHighPrivilegeDisclosure || {};
  const rollback = contracts.rollbackAndCircuitBreaker || {};
  const deviceMatrix = contracts.deviceEvidenceMatrix || {};
  return [
    check(
      "schema",
      contracts.schema === "autojs6-node-phase14-promotion-contracts-v1" &&
        contracts.phase === "Phase 14: Node v1.2 Provider Promotion",
      "Promotion contracts must use the Phase 14 contract schema and phase label.",
    ),
    check(
      "promotion-state-vocabulary",
      sameSet(promotionStateIds, REQUIRED_PROMOTION_STATES) &&
        REQUIRED_PROMOTION_STATES.every((state) => files.contractsDocs.text.includes("`" + state + "`")),
      "Promotion contracts must define implemented, packaged_ready, device_matrix_ready, policy_ready, stable_denied, and future_only.",
    ),
    check(
      "report-conventions",
      contracts.reportConventions &&
        contracts.reportConventions.reportNamePattern === "phase14-<surface-or-task>.json" &&
        contracts.reportConventions.markdownMirrorRequired === true &&
        contracts.reportConventions.schemaPattern === "autojs6-node-phase14-<surface-or-task>-v1" &&
        includesAll(reportFields, ["schema", "generatedAt", "gate", "task", "status", "checks", "policyViolations", "blockers", "decision"]) &&
        includesAll(statusValues, ["passed", "ready", "partial", "failed", "blocked", "archived"]) &&
        sameSet(blockerClassificationIds, REQUIRED_BLOCKER_CLASSIFICATIONS),
      "Report conventions must define deterministic names, markdown mirrors, required fields, statuses, and blocker classifications.",
    ),
    check(
      "packaged-disclosure-contract",
      sameSet(disclosure.requiredForSurfaces || [], REQUIRED_HIGH_PRIVILEGE_SURFACES) &&
        includesAll(disclosure.requiredFields || [], REQUIRED_DISCLOSURE_FIELDS) &&
        includesAll(disclosure.rawObjectDenials || [], ["Context", "Activity", "View", "Bitmap", "MediaProjection", "MediaRecorder", "Intent", "BroadcastReceiver", "Notification", "WakeLock", "TimedTask", "database cursor"]) &&
        REQUIRED_DISCLOSURE_FIELDS.every((field) => files.contractsDocs.text.includes("`" + field + "`")),
      "Packaged high-privilege disclosure contract must cover target surfaces, disclosure fields, and raw object denials.",
    ),
    check(
      "rollback-and-circuit-breaker",
      rollback.safeFallbackRequired === true &&
        rollback.nodeDoctorDiagnosticRequired === true &&
        rollback.crashCircuitBreaker &&
        String(rollback.crashCircuitBreaker.defaultThreshold || "").includes("3 provider crashes within 24h") &&
        includesAll(rollback.rollbackRequiredFields || [], REQUIRED_ROLLBACK_FIELDS) &&
        REQUIRED_ROLLBACK_FIELDS.every((field) => files.contractsDocs.text.includes("`" + field + "`")),
      "Rollback contract must define Safe fallback, Node Doctor diagnostics, crash threshold, and rollback fields.",
    ),
    check(
      "device-evidence-matrix",
      sameSet(deviceMatrix.requiredDeviceClasses || [], REQUIRED_DEVICE_CLASSES) &&
        includesAll(deviceMatrix.requiredFields || [], REQUIRED_DEVICE_FIELDS) &&
        String(deviceMatrix.minimumMatrixPolicy || "").includes("Android 12") &&
        String(deviceMatrix.minimumMatrixPolicy || "").includes("Android 15+") &&
        REQUIRED_DEVICE_FIELDS.every((field) => files.contractsDocs.text.includes("`" + field + "`")),
      "Device evidence matrix must require Android 12 and Android 15+ classes plus permission/lifecycle/OEM fields.",
    ),
    check(
      "surface-manifest-wiring",
      promotionSurface &&
        promotionSurface.status === "design_locked" &&
        includesAll(promotionSurface.tasks || [], ["P14-02", "P14-03", "P14-04", "P14-05", "P14-06"]) &&
        includesAll(promotionSurface.evidence || [], [
          "docs/nodejs/phase14/phase14-promotion-contracts.json",
          "docs/nodejs/phase14/PHASE14_PROMOTION_CONTRACTS.md",
          "tools/nodejs/phase14/verify-node-phase14-promotion-contracts.js",
          ":app:verifyNodePhase14PromotionContracts",
        ]),
      "Surface manifest must wire P14-02 through P14-06 to the design_locked promotion contract surface.",
    ),
    check(
      "task-plan-wiring",
      includesAll(files.phase14Tasks.text, [
        "- [x] P14-02:",
        "- [x] P14-03:",
        "- [x] P14-04:",
        "- [x] P14-05:",
        "- [x] P14-06:",
        ":app:verifyNodePhase14PromotionContracts",
      ]),
      "Phase 14 task checklist must mark P14-02 through P14-06 complete and point to the promotion contract gate.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.gradle.text.includes("verifyNodePhase14PromotionContracts") &&
        files.gradle.text.includes("verify-node-phase14-promotion-contracts.js") &&
        files.phase14Readme.text.includes(":app:verifyNodePhase14PromotionContracts") &&
        files.testing.text.includes(":app:verifyNodePhase14PromotionContracts") &&
        files.ciGates.text.includes(":app:verifyNodePhase14PromotionContracts"),
      "Gradle, README, TESTING, and CI_GATES must reference the Phase 14 promotion contract gate.",
    ),
  ];
}

function includesAll(values, requiredValues) {
  if (Array.isArray(values)) {
    return requiredValues.every((value) => values.includes(value));
  }
  return requiredValues.every((value) => String(values).includes(value));
}

function sameSet(actual, expected) {
  return Array.isArray(actual) &&
    actual.length === expected.length &&
    expected.every((value) => actual.includes(value));
}

function countArray(value) {
  return Array.isArray(value) ? value.length : 0;
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((checkItem) => checkItem.id === id);
  return item ? item.passed : false;
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Promotion Contracts",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Contract Counts",
    "",
  ];
  Object.keys(report.contractCounts).sort().forEach((key) => {
    lines.push(`- \`${key}\`: ${report.contractCounts[key]}`);
  });
  lines.push("", "## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
  report.checks.forEach((checkItem) => {
    lines.push(`| \`${checkItem.id}\` | ${checkItem.passed ? "passed" : "failed"} | ${escapeTable(checkItem.message)} |`);
  });
  return lines.join("\n") + "\n";
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

function read(file) {
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-node-phase14-promotion-contracts.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const eq = arg.indexOf("=");
    const rawKey = eq >= 0 ? arg.slice(2, eq) : arg.slice(2);
    const key = rawKey.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
    const value = eq >= 0 ? arg.slice(eq + 1) : argv[index + 1];
    if (eq < 0) {
      index += 1;
    }
    if (!value || value.startsWith("--")) {
      throw new Error("Missing value for " + arg);
    }
    options[key] = value;
  }
  return options;
}

main();
