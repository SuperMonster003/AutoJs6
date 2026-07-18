#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORTS = [
  ["capability_truth", "final-capability-truth.json", true],
  ["focused_gate_topology", "final-focused-gate-topology.json", true],
  ["adapter_gate", "final-adapter-gate.json", true],
  ["crash_gate", "final-crash-gate.json", true],
  ["package_manager_gate", "final-package-manager-gate.json", true],
  ["long_running_gate", "final-long-running-gate.json", true],
  ["multi_instance_gate", "final-multi-instance-gate.json", true],
  ["worker_decision_gate", "final-worker-decision-gate.json", true],
  ["soak_gate", "final-soak-gate.json", true],
  ["provider_gate", "final-provider-gate.json", true],
  ["abi_gate", "final-abi-gate.json", true],
  ["security_gate", "final-security-gate.json", true],
  ["r6_production_evidence", "r6-production-evidence-gate.json", true],
];

const ACCEPTED_STATUSES = new Set(["ready", "completed", "ready_with_deferred_runtime_artifacts"]);

const BLOCKER_ID_ALIASES = [
  [/^missing required soak evidence:/, "full_heavy_resource_stress"],
  [/^soak evidence duration is below .+:/, "full_heavy_resource_stress"],
  [/^soak evidence reports leaks, orphan process, or unrecoverable runtime:/, "full_heavy_resource_stress"],
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
    } else if (report.parseError) {
      policyViolations.push(`${report.id} is not valid JSON: ${report.parseError}`);
    } else if (report.status === "failed") {
      policyViolations.push(`${report.id} status is failed`);
    } else if (report.status === "missing" || report.status === "unknown") {
      policyViolations.push(`${report.id} status is ${report.status}`);
    } else if (report.status !== "blocked" && !ACCEPTED_STATUSES.has(report.status)) {
      policyViolations.push(`${report.id} has unsupported decision status: ${report.status}`);
    }
  }
  const blockers = [
    ...unresolvedRequiredInternalBlockers.map((entry) => ({
      id: entry.id,
      reason: entry.currentEvidence,
    })),
    ...reports
      .filter((entry) => entry.status === "blocked")
      .filter((entry) => !isBlockedOnlyByKnownRegistry(entry, unresolvedRequiredInternalBlockers))
      .map((entry) => ({
        id: entry.id,
        reason: blockedReportReason(entry),
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
  let json = null;
  let parseError = null;
  if (available) {
    try {
      json = readJson(absolute);
    } catch (error) {
      parseError = error instanceof Error ? error.message : String(error);
    }
  }
  return {
    id,
    path: relativePath(repoRoot, absolute),
    required,
    available,
    status: decisionStatus(json),
    accepted: ACCEPTED_STATUSES.has(decisionStatus(json)),
    parseError,
    policyViolationCount: Array.isArray(json && json.policyViolations) ? json.policyViolations.length : 0,
    blockerIds: blockerIds(json),
    json,
  };
}

function isBlockedOnlyByKnownRegistry(report, unresolvedRequiredInternalBlockers) {
  if (!report.available || report.status !== "blocked" || report.blockerIds.length === 0) {
    return false;
  }
  const known = new Set(unresolvedRequiredInternalBlockers.map((entry) => entry.id));
  return report.blockerIds.every((id) => known.has(id));
}

function blockedReportReason(report) {
  const messages = blockerMessages(report.json);
  if (messages.length === 0) {
    return "report status is blocked";
  }
  const visible = messages.slice(0, 3);
  const hidden = messages.length - visible.length;
  return visible.join("; ") + (hidden > 0 ? `; +${hidden} more` : "");
}

function blockerMessages(json) {
  if (!json || !Array.isArray(json.blockers)) {
    return [];
  }
  return json.blockers.map((entry) => {
    if (typeof entry === "string") {
      return entry;
    }
    if (entry && typeof entry === "object") {
      const id = typeof entry.id === "string" ? entry.id : "blocker";
      if (typeof entry.reason === "string") {
        return `${id}: ${entry.reason}`;
      }
      if (typeof entry.message === "string") {
        return `${id}: ${entry.message}`;
      }
      return id;
    }
    return "";
  }).filter(Boolean);
}

function blockerIds(json) {
  if (!json || !Array.isArray(json.blockers)) {
    return [];
  }
  return json.blockers.map((entry) => {
    if (typeof entry === "string") {
      return blockerIdFromString(entry);
    }
    if (entry && typeof entry === "object" && typeof entry.id === "string") {
      return entry.id;
    }
    return "";
  }).filter(Boolean);
}

function blockerIdFromString(value) {
  const alias = BLOCKER_ID_ALIASES.find(([pattern]) => pattern.test(value));
  if (alias) {
    return alias[1];
  }
  return value.split(":")[0].trim();
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
