#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-disclosure-rollback-audit";
const REQUIRED_DISCLOSURE_FIELDS = Object.freeze([
  "highPrivilegePrompt",
  "manifestMetadata",
  "installReviewCopy",
  "runReviewCopy",
  "foregroundNotificationOwnership",
  "stopAction",
  "permissionDeniedDiagnostic",
  "undeclaredCapabilityDiagnostic",
  "privacyDisclosure",
  "taskRemovalCleanup",
  "relaunchBehavior",
  "crashRecoveryBehavior",
  "safeDowngradeDisclosure",
]);
const REQUIRED_ROLLBACK_FIELDS = Object.freeze([
  "disableFlag",
  "disableScope",
  "diagnosticCode",
  "userVisibleMessage",
  "safeFallback",
  "packagedDowngradeBehavior",
  "dataRetention",
  "reEnablePath",
  "nodeDoctorSignal",
  "crashCircuitBreakerMarker",
]);
const REQUIRED_MARKER_FIELDS = Object.freeze([
  "providerId",
  "profileId",
  "capabilityId",
  "packageIdentity",
  "crashCount",
  "firstCrashAt",
  "lastCrashAt",
  "disableReason",
]);
const REQUIRED_PROVIDER_IDS = Object.freeze([
  "live_ui",
  "overlay",
  "accessibility",
  "media_projection",
  "image_ocr",
  "task_database",
  "intent_broadcast",
  "notifications",
  "settings_power",
  "recorder",
  "mediastore_playback",
]);
const REQUIRED_BACKLOG_TASKS = Object.freeze([
  "P14-07",
  "P14-08",
  "P14-09",
  "P14-10",
  "P14-11",
  "P14-12",
  "P14-13",
  "P14-14",
  "P14-15",
  "P14-17",
  "P14-18",
  "P14-19",
  "P14-20",
  "P14-21",
  "P14-22",
  "P14-23",
  "P14-24",
  "P14-25",
  "P14-26",
]);

const FILES = {
  auditJson: "docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json",
  auditDocs: "docs/nodejs/phase15/PHASE15_NODE_V1_3_DISCLOSURE_ROLLBACK_AUDIT.md",
  contractsJson: "docs/nodejs/phase15/node-v1-3-promotion-contracts.json",
  contractsReport: "build/reports/nodejs/phase15-promotion-contracts.json",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
  phase15Gradle: "app/node-phase15-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files);
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const audit = parseJson(files.auditJson.text);
  const report = {
    schema: "autojs6-node-phase15-disclosure-rollback-audit-report-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase15DisclosureRollbackAudit",
    task: "P15-03",
    status: policyViolations.length === 0 ? "ready" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    auditCounts: {
      providers: countArray(audit.providerAudits),
      disclosureFields: countArray(audit.requiredDisclosureFields),
      rollbackFields: countArray(audit.requiredRollbackFields),
      crashMarkerFields: countArray(audit.crashCircuitBreaker && audit.crashCircuitBreaker.markerFields),
    },
    providerStates: countBy(audit.providerAudits || [], (entry) => entry.currentState),
    checks,
    policyViolations,
    blockers: [],
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      disclosureCoverageReady: checkById(checks, "disclosure-field-coverage"),
      rollbackCoverageReady: checkById(checks, "rollback-field-coverage"),
      providerAuditReady: checkById(checks, "provider-audit-coverage"),
      runtimeAuthorityChanged: false,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 15 disclosure rollback audit: " + report.decision.status);
  console.log("Phase 15 disclosure rollback policy violations: " + policyViolations.length);
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
  const audit = parseJson(files.auditJson.text);
  const contracts = parseJson(files.contractsJson.text);
  const contractsReport = parseJson(files.contractsReport.text);
  const surfaceManifest = parseJson(files.surfaceManifest.text);
  const audits = audit.providerAudits || [];
  const auditIds = audits.map((entry) => entry.providerId);
  const auditBacklog = audits.flatMap((entry) => entry.sourceBacklogTasks || []);
  const contractIds = (contracts.providerPromotionBars || []).map((entry) => entry.id);
  const promotedContracts = (contracts.providerPromotionBars || []).filter((entry) => entry.currentState === "promoted");
  const p15Mapping = (surfaceManifest.taskMappings || []).find((entry) => entry.task === "P15-03");
  return [
    check(
      "promotion-contracts-ready",
      statusOf(contractsReport) === "ready" &&
        contractsReport.decision &&
        contractsReport.decision.optInBarsReady === true &&
        promotedContracts.length === 0,
      "Disclosure rollback audit requires ready P15-02 promotion contracts and no promoted providers.",
    ),
    check(
      "schema",
      audit.schema === "autojs6-node-phase15-disclosure-rollback-audit-v1" &&
        audit.phase === "Phase 15: Node v1.3 Provider Promotion" &&
        audit.task === "P15-03" &&
        audit.branch === "feature/nodejs-runtime-v1.3" &&
        audit.runtimeAuthorityChanged === false,
      "Audit must use the Phase 15 disclosure/rollback schema without changing runtime authority.",
    ),
    check(
      "disclosure-field-coverage",
      sameSet(audit.requiredDisclosureFields || [], REQUIRED_DISCLOSURE_FIELDS) &&
        REQUIRED_DISCLOSURE_FIELDS.every((field) => files.auditDocs.text.includes("`" + field + "`")),
      "Audit must define all required high-privilege disclosure fields.",
    ),
    check(
      "rollback-field-coverage",
      sameSet(audit.requiredRollbackFields || [], REQUIRED_ROLLBACK_FIELDS) &&
        REQUIRED_ROLLBACK_FIELDS.every((field) => files.auditDocs.text.includes("`" + field + "`")),
      "Audit must define all required rollback fields.",
    ),
    check(
      "crash-circuit-breaker",
      audit.crashCircuitBreaker &&
        String(audit.crashCircuitBreaker.threshold || "").includes("3 provider crashes within 24h") &&
        String(audit.crashCircuitBreaker.action || "").includes("fall back") &&
        sameSet(audit.crashCircuitBreaker.markerFields || [], REQUIRED_MARKER_FIELDS) &&
        REQUIRED_MARKER_FIELDS.every((field) => files.auditDocs.text.includes("`" + field + "`")),
      "Audit must define crash circuit breaker threshold, fallback action, and marker fields.",
    ),
    check(
      "provider-audit-coverage",
      sameSet(auditIds, REQUIRED_PROVIDER_IDS) &&
        sameSet(auditIds, contractIds) &&
        sameSet(auditBacklog, REQUIRED_BACKLOG_TASKS) &&
        audits.every((entry) =>
          entry.currentState !== "promoted" &&
          sameSet(entry.requiredDisclosureFields || [], REQUIRED_DISCLOSURE_FIELDS) &&
          sameSet(entry.requiredRollbackFields || [], REQUIRED_ROLLBACK_FIELDS) &&
          Array.isArray(entry.disclosureProofPaths) &&
          entry.disclosureProofPaths.length >= 2 &&
          Array.isArray(entry.rollbackProofPaths) &&
          entry.rollbackProofPaths.length >= 2 &&
          String(entry.disableFlag || "").startsWith("node.provider.") &&
          entry.disableScope === "per-provider and per-packaged-project" &&
          String(entry.diagnosticCode || "").startsWith("NODE_PROVIDER_") &&
          entry.foregroundNotificationRequired === true &&
          entry.stopActionRequired === true &&
          entry.crashCircuitBreakerRequired === true),
      "Every promotable provider must declare disclosure proof paths, rollback proof paths, disable flag, diagnostics, Safe fallback, stop action, and crash breaker requirements.",
    ),
    check(
      "surface-manifest-wiring",
      p15Mapping &&
        p15Mapping.ownerSurface === "phase15.disclosure_rollback_audit" &&
        p15Mapping.evidenceClass === "disclosure_rollback_audit" &&
        p15Mapping.status === "ready" &&
        p15Mapping.gate === ":app:verifyNodePhase15DisclosureRollbackAudit",
      "Surface manifest must mark P15-03 ready under the disclosure rollback audit owner surface.",
    ),
    check(
      "task-plan-wiring",
      includesAll(files.phase15Tasks.text, [
        "- [x] P15-03: Add v1.3 disclosure and rollback audit gate.",
        ":app:verifyNodePhase15DisclosureRollbackAudit",
        "node-v1-3-disclosure-rollback-audit.json",
        "phase15-disclosure-rollback-audit.json",
      ]),
      "Phase 15 task checklist must mark P15-03 complete and list the disclosure rollback audit artifacts.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase15Readme.text.includes(":app:verifyNodePhase15DisclosureRollbackAudit") &&
        files.testing.text.includes(":app:verifyNodePhase15DisclosureRollbackAudit") &&
        files.ciGates.text.includes(":app:verifyNodePhase15DisclosureRollbackAudit") &&
        files.phase15Gradle.text.includes("verifyNodePhase15DisclosureRollbackAudit") &&
        files.phase15Gradle.text.includes("verify-node-phase15-disclosure-rollback-audit.js") &&
        files.phase15Gradle.text.includes("verifyNodePhase15PromotionContracts"),
      "README, TESTING, CI_GATES, and Gradle must reference the Phase 15 disclosure rollback audit gate.",
    ),
  ];
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 15 Disclosure Rollback Audit",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Audit Counts",
    "",
  ];
  Object.keys(report.auditCounts).sort().forEach((key) => {
    lines.push(`- \`${key}\`: ${report.auditCounts[key]}`);
  });
  lines.push("", "## Provider States", "");
  Object.keys(report.providerStates).sort().forEach((state) => {
    lines.push(`- \`${state}\`: ${report.providerStates[state]}`);
  });
  lines.push("", "## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
  report.checks.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | ${entry.passed ? "passed" : "failed"} | ${escapeTable(entry.message)} |`);
  });
  if (report.policyViolations.length > 0) {
    lines.push("", "## Policy Violations", "");
    report.policyViolations.forEach((entry) => {
      lines.push(`- ${entry}`);
    });
  }
  return lines.join("\n") + "\n";
}

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function sameSet(actual, expected) {
  if (!Array.isArray(actual) || actual.length !== expected.length) {
    return false;
  }
  const actualSorted = actual.slice().sort();
  const expectedSorted = expected.slice().sort();
  return expectedSorted.every((value, index) => actualSorted[index] === value);
}

function countArray(value) {
  return Array.isArray(value) ? value.length : 0;
}

function countBy(values, selector) {
  const output = {};
  values.forEach((value) => {
    const key = selector(value);
    output[key] = (output[key] || 0) + 1;
  });
  return output;
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const entry = checks.find((checkItem) => checkItem.id === id);
  return entry ? entry.passed : false;
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
      console.log("Usage: node verify-node-phase15-disclosure-rollback-audit.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
