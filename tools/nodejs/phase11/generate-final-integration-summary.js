#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORTS = [
  ["capability_truth", "final-capability-truth.json", true],
  ["focused_gate_topology", "final-focused-gate-topology.json", true],
  ["adapter_gate", "final-adapter-gate.json", false],
  ["crash_gate", "final-crash-gate.json", false],
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const registry = readJson(path.join(repoRoot, "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"));
  const reports = REPORTS.map(([id, file, required]) => readReport(reportDir, id, file, required, repoRoot));
  const unresolvedRequiredInternalBlockers = registry.blockers
    .filter((entry) => entry.classification === "required_internal" && entry.status !== "closed");
  const policyViolations = [];
  for (const report of reports) {
    if (report.required && !report.available) {
      policyViolations.push("missing required final report: " + report.id);
    }
    if (report.status === "failed") {
      policyViolations.push(`${report.id} status is failed`);
    }
  }
  const blockers = [
    ...unresolvedRequiredInternalBlockers.map((entry) => ({
      id: entry.id,
      reason: entry.currentEvidence,
    })),
    ...reports
      .filter((entry) => entry.status === "blocked")
      .map((entry) => ({
        id: entry.id,
        reason: "report status is blocked",
      })),
  ];
  const status = policyViolations.length > 0 ? "failed" : blockers.length > 0 ? "blocked" : "completed";
  const summary = {
    schema: "autojs6-node-final-integration-summary-v1",
    generatedAt: new Date().toISOString(),
    phase: "Phase 11: Node Platform Completion & Graduation",
    gate: options.failOnBlocked ? "verifyNodeFinalIntegrationGate" : "generateNodeFinalIntegrationSummary",
    runtime: {
      defaultSlot: registry.defaultRuntime,
      node2417: registry.node2417Lane,
      libnodeModifiedByPhase11: false,
    },
    reports: reports.map(({ json, ...entry }) => entry),
    unresolvedRequiredInternalBlockers,
    intentionalNonGoals: registry.blockers.filter((entry) => entry.classification === "intentional_non_goal"),
    externalRuntimeMaintenance: registry.blockers.filter((entry) => entry.classification === "external_runtime_maintenance"),
    blockers,
    policyViolations,
    decision: {
      status,
      completedWithRecordedBlockersAllowed: false,
      safeNodeProfilePreserved: true,
      finalIntegrationComplete: status === "completed",
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "final-integration-summary", summary, renderMarkdown);
  console.log("Final integration summary decision: " + summary.decision.status);
  if (options.failOnBlocked && summary.decision.status !== "completed") {
    process.exitCode = 1;
  } else if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function readReport(reportDir, id, file, required, repoRoot) {
  const absolute = path.join(reportDir, file);
  const available = fs.existsSync(absolute);
  const json = available ? readJson(absolute) : null;
  return {
    id,
    path: relativePath(repoRoot, absolute),
    required,
    available,
    status: decisionStatus(json),
    policyViolationCount: Array.isArray(json && json.policyViolations) ? json.policyViolations.length : 0,
    json,
  };
}

function decisionStatus(json) {
  if (!json) return "missing";
  if (json.decision && json.decision.status) return json.decision.status;
  return json.status || "unknown";
}

function renderMarkdown(report) {
  const lines = [
    "# Final Integration Summary",
    "",
    `- Decision: ${report.decision.status}`,
    `- Default runtime: ${report.runtime.defaultSlot}`,
    `- Node 24.17 lane: ${report.runtime.node2417}`,
    `- Required internal blockers: ${report.unresolvedRequiredInternalBlockers.length}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Reports",
    "",
    "| Report | Status | Available | Required |",
    "| --- | --- | --- | --- |",
  ];
  report.reports.forEach((entry) => {
    lines.push(`| ${entry.id} | ${entry.status} | ${entry.available} | ${entry.required} |`);
  });
  lines.push("", "## Blockers", "");
  if (report.blockers.length === 0) {
    lines.push("None.");
  } else {
    report.blockers.forEach((blocker) => lines.push(`- ${blocker.id}: ${blocker.reason}`));
  }
  lines.push("", "## Policy Violations", "");
  if (report.policyViolations.length === 0) {
    lines.push("None.");
  } else {
    report.policyViolations.forEach((violation) => lines.push(`- ${violation}`));
  }
  return lines.join("\n") + "\n";
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
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
      console.log("Usage: node generate-final-integration-summary.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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
