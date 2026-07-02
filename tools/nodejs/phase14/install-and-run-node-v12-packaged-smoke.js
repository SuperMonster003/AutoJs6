#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-packaged-aggregate-smoke";
const SOURCE_REPORTS = Object.freeze([
  ["P14-07", "phase14-live-ui-provider-packaged.json"],
  ["P14-10", "phase14-packaged-overlay-positive-smoke.json"],
  ["P14-11", "phase14-accessibility-lifecycle.json"],
  ["P14-15", "phase14-packaged-image-ocr-evidence.json"],
  ["P14-19", "phase14-packaged-task-scheduling-smoke.json"],
  ["P14-21", "phase14-packaged-broadcast-trigger-remove-smoke.json"],
  ["P14-22", "phase14-notification-ownership.json"],
  ["P14-23", "phase14-settings-power-oem-background-matrix.json"],
  ["P14-25", "phase14-packaged-recorder-media-stress.json"],
  ["P14-29", "phase14-worker-runnable-packaged-smoke.json"],
  ["P14-33", "phase14-security-regression-corpus.json"],
]);
const REQUIRED_SUITES = Object.freeze([
  "packaged_live_ui",
  "packaged_overlay",
  "packaged_accessibility",
  "packaged_image_ocr",
  "packaged_task_scheduling",
  "packaged_broadcast",
  "packaged_notifications_power",
  "packaged_recorder_media",
  "packaged_worker_threads",
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_34_NODE_V1_2_PACKAGED_AGGREGATE_SMOKE.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/node-v1-2-packaged-aggregate-smoke-plan.json",
  planDoc: "docs/nodejs/phase14/PHASE14_NODE_V1_2_PACKAGED_AGGREGATE_SMOKE.md",
  securityCorpus: "docs/nodejs/security/NODE_PROFILE_V1_2_SECURITY_REGRESSION_CORPUS.json",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
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
  console.log("Phase 14 packaged aggregate smoke: " + report.decision.status);
  console.log("Phase 14 packaged aggregate smoke blockers: " + report.blockers.length);
  console.log("Phase 14 packaged aggregate smoke policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot, reportDir) {
  const entries = Object.entries(FILES).map(([key, relative]) => {
    const file = path.join(repoRoot, relative);
    return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
  });
  SOURCE_REPORTS.forEach(([task, basename]) => {
    const file = path.join(reportDir, basename);
    entries.push([
      task,
      {
        relative: path.relative(repoRoot, file).replace(/\\/g, "/"),
        exists: fs.existsSync(file),
        text: read(file),
      },
    ]);
  });
  return Object.fromEntries(entries);
}

function buildReport(files) {
  const plan = parseJson(files.planJson.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const securitySurface = (manifest.surfaces || []).find((entry) => entry.id === "security.packaged_release_gates") || null;
  const suites = Array.isArray(plan.smokeSuites) ? plan.smokeSuites : [];
  const suitesById = Object.fromEntries(suites.map((entry) => [entry.id, entry]));
  const generatedReports = SOURCE_REPORTS.map(([task]) => {
    const parsed = parseJson(files[task].text);
    return {
      task,
      exists: files[task].exists,
      status: statusOf(parsed),
    };
  });
  const scenarios = [
    scenario("aggregate_plan", "P14-34 defines a deterministic aggregate packaged smoke entry", [
      value("schema", plan.schema === "autojs6-node-phase14-packaged-aggregate-smoke-plan-v1"),
      value("task", plan.task === "P14-34"),
      value("status", plan.status === "ready"),
      value("decision", plan.decision === "packaged_aggregate_smoke_contract_ready"),
      value("gradleTask", plan.gradleTask === ":app:installAndRunNodeV12PackagedSmoke"),
      value("runMode", plan.runMode === "host_contract_ready"),
      value("runtimeAuthority", plan.runtimeAuthorityChanged === false),
      value("sourceGates", sameSet(plan.sourceGates || [], SOURCE_REPORTS.map(([task]) => task))),
      value("reportArtifacts", includesAll(plan.reportArtifacts || [], [
        "build/reports/nodejs/phase14-packaged-aggregate-smoke.json",
        "build/reports/nodejs/phase14-packaged-aggregate-smoke.md",
      ])),
    ]),
    scenario("source_reports", "Packaged-capable source reports and P14-33 security corpus are available", [
      ...generatedReports.map((entry) => check(
        `generated:${entry.task}`,
        entry.exists && ["partial", "ready", "passed"].includes(entry.status),
        `${entry.task} generated report must exist with partial/ready/passed status`,
      )),
    ]),
    scenario("suite_contracts", "Every aggregate suite defines positive or conditional path plus denial, downgrade, and cleanup coverage", [
      value("suiteIds", sameSet(suites.map((entry) => entry.id), REQUIRED_SUITES)),
      ...REQUIRED_SUITES.map((id) => check(`suite:${id}`, suiteReady(suitesById[id]), `${id} must define aggregate packaged smoke contract fields`)),
      token(files.securityCorpus, "packaged_metadata_safe_downgrade"),
      token(files.securityCorpus, "packaged aggregate smoke"),
      token(files.securityCorpus, "Safe downgrade"),
    ]),
    scenario("docs_and_gradle_wiring", "Docs, manifest, and Gradle expose installAndRunNodeV12PackagedSmoke", [
      value("surfaceEvidence", securitySurface &&
        securitySurface.status === "design_locked" &&
        (securitySurface.tasks || []).includes("P14-34") &&
        (securitySurface.evidence || []).includes("docs/nodejs/phase14/node-v1-2-packaged-aggregate-smoke-plan.json") &&
        (securitySurface.evidence || []).includes(":app:installAndRunNodeV12PackagedSmoke")),
      token(files.surfaceManifestDoc, "P14-34 adds the deterministic packaged aggregate smoke entry"),
      token(files.phase14Tasks, "- [x] P14-34: Add v1.2 packaged aggregate smoke."),
      token(files.phase14Tasks, "Packaged aggregate smoke gate deployed:"),
      token(files.phase14Report, "P14-34 decision: `packaged_aggregate_smoke_contract_ready`"),
      token(files.phase14Readme, ":app:installAndRunNodeV12PackagedSmoke"),
      token(files.testing, ":app:installAndRunNodeV12PackagedSmoke"),
      token(files.ciGates, ":app:installAndRunNodeV12PackagedSmoke"),
      token(files.gradle14, "installAndRunNodeV12PackagedSmoke"),
      token(files.gradle14, "install-and-run-node-v12-packaged-smoke.js"),
    ]),
  ];
  const checks = [
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase14-packaged-aggregate-smoke-plan-v1", "P14-34 packaged aggregate smoke plan JSON must exist."),
    check("all-source-reports-present", generatedReports.every((entry) => entry.exists), "P14-34 source reports must exist."),
    check("all-suites-ready", REQUIRED_SUITES.every((id) => suiteReady(suitesById[id])), "Every P14-34 aggregate suite must be ready."),
    check("all-scenarios-ready", scenarios.every((entry) => entry.status === "ready"), "All P14-34 scenarios must be ready."),
  ];
  const policyViolations = checks.filter((entry) => !entry.ok).map((entry) => ({ id: entry.id, message: entry.message }));
  return {
    schema: "autojs6-node-phase14-packaged-aggregate-smoke-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "ready" : "failed",
    task: "P14-34",
    generatedAt: new Date().toISOString(),
    inputs: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, { path: file.relative, exists: file.exists }])),
    checks,
    scenarios,
    suiteCount: suites.length,
    runMode: plan.runMode || "missing",
    blockers: [],
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      packagedDecision: "packaged_aggregate_smoke_contract_ready",
      p14TaskComplete: policyViolations.length === 0,
      deterministicGradleEntryReady: true,
      sourceReportsImported: generatedReports.every((entry) => entry.exists),
      suiteContractsReady: REQUIRED_SUITES.every((id) => suiteReady(suitesById[id])),
      realDeviceInstallRunArchiveAttached: false,
      runtimeAuthorityChanged: false,
      reportArtifactsWritten: policyViolations.length === 0,
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Packaged Aggregate Smoke",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Packaged decision: \`${report.decision.packagedDecision}\``,
    `- Deterministic Gradle entry ready: \`${report.decision.deterministicGradleEntryReady}\``,
    `- Source reports imported: \`${report.decision.sourceReportsImported}\``,
    `- Suite contracts ready: \`${report.decision.suiteContractsReady}\``,
    `- Real device install-run archive attached: \`${report.decision.realDeviceInstallRunArchiveAttached}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    "",
    "## Scenarios",
    "",
    "| Scenario | Status | Missing evidence |",
    "| --- | --- | --- |",
  ];
  report.scenarios.forEach((entry) => {
    const missing = entry.missing.length > 0 ? entry.missing.join("<br>") : "None.";
    lines.push(`| \`${entry.id}\` | \`${entry.status}\` | ${missing} |`);
  });
  if (report.policyViolations.length > 0) {
    lines.push("", "## Policy Violations", "");
    report.policyViolations.forEach((entry) => {
      lines.push(`- \`${entry.id}\`: ${entry.message}`);
    });
  }
  lines.push("");
  return lines.join("\n");
}

function suiteReady(entry) {
  return Boolean(entry) &&
    typeof entry.positivePath === "string" &&
    entry.positivePath.length > 0 &&
    entry.undeclaredCapabilityDenial === true &&
    entry.packagedMetadataMismatchDenial === true &&
    entry.safeDowngradeRequired === true &&
    entry.cleanupRequired === true &&
    typeof entry.currentExecutionState === "string" &&
    entry.currentExecutionState.length > 0;
}

function scenario(id, description, checks) {
  const missing = checks.filter((entry) => !entry.ok).map((entry) => entry.message);
  return { id, description, status: missing.length === 0 ? "ready" : "missing_evidence", checks, missing };
}

function value(id, ok) {
  return { id, ok: Boolean(ok), message: `missing value check: ${id}` };
}

function token(file, expected) {
  return {
    id: `${file.relative}:${expected}`,
    ok: file.exists && file.text.includes(expected),
    message: `missing evidence token: ${expected} in ${file.relative}`,
  };
}

function check(id, ok, message) {
  return { id, ok: Boolean(ok), message };
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

function statusOf(report) {
  return report.status || (report.decision || {}).status || "missing";
}

function sameSet(actual, expected) {
  if (!Array.isArray(actual) || actual.length !== expected.length) {
    return false;
  }
  const actualSorted = actual.slice().sort();
  const expectedSorted = expected.slice().sort();
  return expectedSorted.every((value, index) => actualSorted[index] === value);
}

function includesAll(actual, expected) {
  return Array.isArray(actual) && expected.every((value) => actual.includes(value));
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8");
  } catch {
    return "";
  }
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
      console.log("Usage: node install-and-run-node-v12-packaged-smoke.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
