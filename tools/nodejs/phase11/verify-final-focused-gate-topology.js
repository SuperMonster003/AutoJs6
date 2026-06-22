#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REQUIRED_STRICT_TASKS = [
  "verifyNodeFinalAdapterGate",
  "verifyNodeFinalCrashGate",
  "verifyNodeFinalPackageManagerGate",
  "verifyNodeFinalLongRunningGate",
  "verifyNodeFinalMultiInstanceGate",
  "verifyNodeFinalWorkerDecisionGate",
  "verifyNodeFinalProviderGate",
  "verifyNodeFinalAbiGate",
  "verifyNodeFinalSecurityGate",
  "verifyNodeFinalSoakGate",
  "verifyNodeFinalCapabilityTruth",
  "verifyNodeFinalIntegrationGate",
];

const REQUIRED_STATUS_TASKS = [
  "generateNodeFinalCapabilityTruth",
  "generateNodeFinalAdapterStatus",
  "generateNodeFinalCrashStatus",
  "generateNodeFinalPackageManagerStatus",
  "generateNodeFinalLongRunningStatus",
  "generateNodeFinalMultiInstanceStatus",
  "generateNodeFinalWorkerDecisionStatus",
  "generateNodeFinalProviderStatus",
  "generateNodeFinalAbiStatus",
  "generateNodeFinalSecurityStatus",
  "generateNodeFinalSoakStatus",
  "generateNodeFinalIntegrationSummary",
];

const REQUIRED_SUPPORT_TASKS = [
  "collectNodeCrashBundle",
  "symbolizeNodeCrash",
  "verifyNodeCrashBundle",
];

const REQUIRED_TASKS = [
  ...REQUIRED_STRICT_TASKS.map((task) => ({ task, role: "strict" })),
  ...REQUIRED_STATUS_TASKS.map((task) => ({ task, role: "status" })),
  ...REQUIRED_SUPPORT_TASKS.map((task) => ({ task, role: "support" })),
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = [
    "app/build.gradle.kts",
    "app/node-phase11-gate.gradle.kts",
    "app/node-phase10-gate.gradle.kts",
    "app/node-native-modernization.gradle.kts",
  ].map((relative) => ({
    relative,
    text: read(path.join(repoRoot, relative)),
  }));
  const tasks = REQUIRED_TASKS.map(({ task, role }) => {
    const registration = files.find((file) => file.text.includes(`"${task}"`));
    return {
      task,
      role,
      registeredIn: registration ? registration.relative : null,
      category: category(task),
    };
  });
  const policyViolations = tasks
    .filter((entry) => !entry.registeredIn)
    .map((entry) => "missing final focused gate task: " + entry.task);
  const phase11Applied = files.find((file) => file.relative === "app/build.gradle.kts").text.includes("node-phase11-gate.gradle.kts");
  if (!phase11Applied) {
    policyViolations.push("app/build.gradle.kts must apply node-phase11-gate.gradle.kts");
  }
  const report = {
    schema: "autojs6-node-final-focused-gate-topology-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeFinalFocusedGateTopology",
    tasks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      finalIntegrationGateIsStrict: true,
      strictTasksRegistered: tasks.filter((entry) => entry.role === "strict").every((entry) => entry.registeredIn),
      statusTasksRegistered: tasks.filter((entry) => entry.role === "status").every((entry) => entry.registeredIn),
      supportTasksRegistered: tasks.filter((entry) => entry.role === "support").every((entry) => entry.registeredIn),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "final-focused-gate-topology", report, renderMarkdown);
  console.log("Final focused gate topology decision: " + report.decision.status);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function category(task) {
  if (task.includes("Adapter")) return "adapter";
  if (task.includes("Crash")) return "crash";
  if (task.includes("PackageManager")) return "package_manager";
  if (task.includes("LongRunning")) return "long_running";
  if (task.includes("MultiInstance")) return "multi_instance";
  if (task.includes("Worker")) return "worker_wasi";
  if (task.includes("Provider")) return "provider";
  if (task.includes("Abi")) return "abi";
  if (task.includes("Security")) return "security";
  if (task.includes("Soak")) return "soak";
  if (task.includes("CapabilityTruth")) return "capability_truth";
  if (task.includes("IntegrationSummary")) return "aggregate";
  return "aggregate";
}

function renderMarkdown(report) {
  const lines = [
    "# Final Focused Gate Topology",
    "",
    `- Decision: ${report.decision.status}`,
    `- Required tasks: ${report.tasks.length}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "| Task | Role | Category | Registered In |",
    "| --- | --- | --- | --- |",
  ];
  report.tasks.forEach((task) => {
    lines.push(`| ${task.task} | ${task.role} | ${task.category} | ${task.registeredIn || "<missing>"} |`);
  });
  lines.push("", "## Policy Violations", "");
  if (report.policyViolations.length === 0) {
    lines.push("None.");
  } else {
    report.policyViolations.forEach((violation) => lines.push(`- ${violation}`));
  }
  return lines.join("\n") + "\n";
}

function read(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "";
}

function writeReport(reportDir, basename, json, markdownRenderer) {
  fs.writeFileSync(path.join(reportDir, `${basename}.json`), JSON.stringify(json, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${basename}.md`), markdownRenderer(json));
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-final-focused-gate-topology.js [--repo-root dir] [--report-dir dir]");
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
