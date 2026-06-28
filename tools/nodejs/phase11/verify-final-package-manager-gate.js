#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const FILES = {
  phase10Report: "build/reports/nodejs/phase10-package-manager-boundary.json",
  phase10Tool: "tools/nodejs/phase10/autojs6-pm.js",
  phase10Gate: "app/node-phase10-gate.gradle.kts",
  finalGate: "app/node-phase11-gate.gradle.kts",
  packageManagerDoc: "docs/nodejs/design/PACKAGE_MANAGER.md",
  packageManagerReport: "docs/nodejs/reports/phase10/PACKAGE_MANAGER_BOUNDARY.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
  blockerRegistry: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
  phase13AndroidPackageInstallReport: "build/reports/nodejs/phase13-android-package-install-manager.json",
};

const HOST_FIXTURE_CHECKS = [
  ["phase10_tool", "phase10Tool", "class AutoJs6PackageManager"],
  ["install_operation", "phase10Tool", "async installPackage"],
  ["update_operation", "phase10Tool", "async updatePackage"],
  ["tarball_operation", "phase10Tool", "async installFromTarball"],
  ["safe_extract", "phase10Tool", "safeExtractTgz"],
  ["lifecycle_disabled", "phase10Tool", "DISABLED_LIFECYCLE_SCRIPTS"],
  ["native_addon_negative", "phase10Tool", "native addon disabled"],
  ["phase10_gate", "phase10Gate", "\"verifyNodePackageManagerGate\""],
  ["autojs6_pm_fixture_task", "phase10Gate", "\"verifyNodePhase10Autojs6PmFixture\""],
  ["dependency_manager_gate", "phase10Gate", "\"verifyNodeDependencyManagerFixture\""],
  ["dependency_bundler_gate", "phase10Gate", "\"verifyNodeDependencyBundlerFixtures\""],
  ["final_gate_task", "finalGate", "\"verifyNodeFinalPackageManagerGate\""],
  ["design_doc", "packageManagerDoc", "host-managed installer prototype"],
  ["phase10_report_doc", "packageManagerReport", "Android runtime package installation"],
  ["capability_matrix_doc", "capabilityMatrix", "Android local store is transactional"],
];

const REQUIRED_OPERATIONS = ["install", "installFromTarball", "update", "remove", "list", "verify", "prune"];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = Object.fromEntries(
    Object.entries(FILES).map(([id, relative]) => [id, read(path.join(repoRoot, relative))]),
  );
  const phase10Report = readJsonIfExists(path.join(repoRoot, FILES.phase10Report));
  const phase13AndroidReport = readJsonIfExists(path.join(repoRoot, FILES.phase13AndroidPackageInstallReport));
  const registry = readJsonIfExists(path.join(repoRoot, FILES.blockerRegistry)) || { blockers: [] };
  const hostFixtureChecks = HOST_FIXTURE_CHECKS.map(([id, fileId, token]) => ({
    id,
    file: FILES[fileId],
    token,
    present: files[fileId].includes(token),
  }));
  const reportStatus = decisionStatus(phase10Report);
  const reportAvailable = Boolean(phase10Report);
  const reportPolicyViolations = Array.isArray(phase10Report && phase10Report.policyViolations)
    ? phase10Report.policyViolations
    : [];
  const packageManager = phase10Report && phase10Report.packageManager ? phase10Report.packageManager : {};
  const operations = REQUIRED_OPERATIONS.map((operation) => ({
    operation,
    present: operationPresent(phase10Report, operation),
  }));
  const phase13AndroidReady = Boolean(
    phase13AndroidReport &&
      phase13AndroidReport.decision &&
      phase13AndroidReport.decision.status === "ready" &&
      phase13AndroidReport.decision.finalBlockerClosable === true &&
      phase13AndroidReport.decision.managerReady === true,
  );
  const androidInstallImplemented = phase13AndroidReady ||
    packageManager.androidRuntimeInstall === true ||
    files.phase10Tool.includes("androidRuntimeInstall: true") ||
    files.packageManagerDoc.includes("Android runtime package installation is enabled");
  const registryBlocker = registryBlockerEntry(registry, "android_side_package_installation");
  const blockers = [];
  hostFixtureChecks
    .filter((entry) => !entry.present)
    .forEach((entry) => blockers.push("missing package-manager fixture evidence: " + entry.id));
  if (!reportAvailable) {
    blockers.push("Phase 10 package-manager boundary report is missing");
  } else if (!["ready", "completed"].includes(reportStatus)) {
    blockers.push("Phase 10 package-manager boundary report is not ready: " + reportStatus);
  }
  reportPolicyViolations.forEach((violation) => blockers.push("Phase 10 package-manager policy violation: " + violation));
  operations
    .filter((entry) => !entry.present)
    .forEach((entry) => blockers.push("missing package-manager operation evidence: " + entry.operation));
  if (!androidInstallImplemented) {
    blockers.push("P13-09 Android local package installation report is not ready");
  }
  if (registryBlocker.open) {
    blockers.push(`${registryBlocker.id}: ${registryBlocker.currentEvidence}`);
  }
  const report = {
    schema: "autojs6-node-final-package-manager-gate-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeFinalPackageManagerGate",
    tasks: ["S11-22", "S11-23", "S11-24"],
    sources: Object.fromEntries(
      Object.entries(FILES).map(([id, relative]) => [id, relativePath(repoRoot, path.join(repoRoot, relative))]),
    ),
    phase10: {
      reportAvailable,
      reportStatus,
      packageManagerMode: packageManager.mode || "unknown",
      androidRuntimeInstall: packageManager.androidRuntimeInstall === true,
      lifecycleScriptsExecuted: packageManager.lifecycleScriptsExecuted === true,
      nativeAddonsEnabled: packageManager.nativeAddonsEnabled === true,
      binExecutionEnabled: packageManager.binExecutionEnabled === true,
      operations,
      policyViolations: reportPolicyViolations,
    },
    phase13AndroidPackageInstall: {
      reportAvailable: Boolean(phase13AndroidReport),
      reportStatus: phase13AndroidReport && phase13AndroidReport.decision
        ? phase13AndroidReport.decision.status
        : "missing",
      finalBlockerClosable: Boolean(
        phase13AndroidReport &&
          phase13AndroidReport.decision &&
          phase13AndroidReport.decision.finalBlockerClosable,
      ),
      managerReady: Boolean(
        phase13AndroidReport &&
          phase13AndroidReport.decision &&
          phase13AndroidReport.decision.managerReady,
      ),
    },
    hostFixture: {
      status: hostFixtureChecks.every((entry) => entry.present) ? "ready" : "missing_evidence",
      checks: hostFixtureChecks,
    },
    androidRuntimeInstaller: {
      status: androidInstallImplemented ? "implemented" : "not_implemented",
      requiredProperties: [
        "app-private local install/update/remove/list/verify/prune",
        "transactional staging and restore",
        "cancellable operations",
        "store locking",
        "sha512 integrity verification",
        "lifecycle-script/bin/native/path-escape denial",
        "guarded package_manager facade with explicit mutation confirmation",
      ],
    },
    registryBlockers: [registryBlocker],
    blockers,
    decision: {
      status: blockers.length === 0 ? "ready" : "blocked",
      hostFixtureReady: hostFixtureChecks.every((entry) => entry.present) &&
        reportAvailable &&
        ["ready", "completed"].includes(reportStatus) &&
        reportPolicyViolations.length === 0,
      androidRuntimeInstallAllowed: androidInstallImplemented && !registryBlocker.open,
      safeNodeProfilePreserved: true,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "final-package-manager-gate", report, renderMarkdown);
  console.log("Final package-manager gate decision: " + report.decision.status);
  if (options.failOnBlocked && report.decision.status !== "ready") {
    process.exitCode = 1;
  }
}

function operationPresent(report, operation) {
  if (!report || !report.operations) {
    return false;
  }
  if (operation === "list") {
    return Array.isArray(report.operations.list) && report.operations.list.length > 0;
  }
  const value = report.operations[operation];
  return value === true || Boolean(value && typeof value === "object" && value.ok === true);
}

function registryBlockerEntry(registry, id) {
  const entry = (registry.blockers || []).find((candidate) => candidate.id === id);
  return {
    id,
    status: entry ? entry.status : "missing",
    classification: entry ? entry.classification : "missing",
    currentEvidence: entry ? entry.currentEvidence : "blocker registry entry is missing",
    open: !entry || entry.status !== "closed",
  };
}

function decisionStatus(json) {
  if (!json) return "missing";
  if (json.decision && json.decision.status) return json.decision.status;
  return json.status || "unknown";
}

function renderMarkdown(report) {
  const lines = [
    "# Final Package Manager Gate",
    "",
    `- Decision: ${report.decision.status}`,
    `- Host fixture ready: ${report.decision.hostFixtureReady}`,
    `- Phase 10 report: ${report.phase10.reportStatus}`,
    `- Package manager mode: ${report.phase10.packageManagerMode}`,
    `- P13-09 Android package install report: ${report.phase13AndroidPackageInstall.reportStatus}`,
    `- Android runtime install allowed: ${report.decision.androidRuntimeInstallAllowed}`,
    "",
    "## Host Fixture Checks",
    "",
    "| Check | Present | File |",
    "| --- | --- | --- |",
  ];
  report.hostFixture.checks.forEach((entry) => {
    lines.push(`| ${entry.id} | ${entry.present} | ${entry.file} |`);
  });
  lines.push(
    "",
    "## Operation Evidence",
    "",
    "| Operation | Present |",
    "| --- | --- |",
  );
  report.phase10.operations.forEach((entry) => {
    lines.push(`| ${entry.operation} | ${entry.present} |`);
  });
  lines.push(
    "",
    "## Android Runtime Installer",
    "",
    `- Status: ${report.androidRuntimeInstaller.status}`,
  );
  report.androidRuntimeInstaller.requiredProperties.forEach((entry) => lines.push(`- ${entry}`));
  lines.push("", "## Blockers", "");
  if (report.blockers.length === 0) {
    lines.push("None.");
  } else {
    report.blockers.forEach((blocker) => lines.push(`- ${blocker}`));
  }
  return lines.join("\n") + "\n";
}

function read(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "";
}

function readJsonIfExists(file) {
  if (!fs.existsSync(file)) {
    return null;
  }
  try {
    return JSON.parse(stripBom(fs.readFileSync(file, "utf8")));
  } catch (_) {
    return null;
  }
}

function stripBom(value) {
  return value.replace(/^\uFEFF/, "");
}

function relativePath(root, file) {
  return path.relative(root, file).replace(/\\/g, "/");
}

function writeReport(reportDir, basename, json, markdownRenderer) {
  fs.writeFileSync(path.join(reportDir, `${basename}.json`), JSON.stringify(json, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${basename}.md`), markdownRenderer(json));
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-blocked"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-final-package-manager-gate.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
      process.exit(0);
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const eq = arg.indexOf("=");
    const rawKey = eq >= 0 ? arg.slice(2, eq) : arg.slice(2);
    const key = rawKey.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
    if (booleanArgs.has(rawKey)) {
      options[key] = eq >= 0 ? parseBoolean(arg.slice(eq + 1)) : true;
      continue;
    }
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

function parseBoolean(value) {
  return ["1", "true", "yes", "on"].includes(String(value).toLowerCase());
}

main();
