#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REQUIRED_TASKS = [
  "verifyNodePackagedParityGate",
  "verifyNodeLiveProviderGate",
  "verifyNodePackageManagerGate",
  "verifyNodeLongRunningGate",
  "verifyNodeMultiInstanceGate",
  "verifyNodeWorkerWasiGate",
  "verifyNodeAdapterV1Gate",
  "verifyNodePhase10Gate",
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = [
    "app/build.gradle.kts",
    "app/embedded-node-packaging-preflight.gradle.kts",
    "app/node-native-modernization.gradle.kts",
    "app/node-phase10-gate.gradle.kts",
  ].map((relative) => ({
    relative,
    text: fs.existsSync(path.join(repoRoot, relative)) ? fs.readFileSync(path.join(repoRoot, relative), "utf8") : "",
  }));
  const violations = [];
  const tasks = REQUIRED_TASKS.map((task) => {
    const registration = files.find((file) => file.text.includes(`"${task}"`) || file.text.includes(`(${task}`));
    const selector = files.find((file) => file.relative === "app/build.gradle.kts" && file.text.includes(`"${task}" to`));
    const present = Boolean(registration);
    if (!present) {
      violations.push(`missing focused gate task: ${task}`);
    }
    if (requiresInstrumentationSelector(task) && !selector) {
      violations.push(`missing focused instrumentation selector for ${task}`);
    }
    return {
      task,
      registeredIn: registration ? registration.relative : null,
      hasInstrumentationSelector: Boolean(selector),
      category: category(task),
    };
  });
  const report = {
    schema: "autojs6-node-phase10-focused-gate-matrix-v1",
    generatedAt: new Date().toISOString(),
    task: "S10-03 Focused Gate Matrix",
    gate: "verifyNodePhase10FocusedGateMatrix",
    tasks,
    policyViolations: violations,
    decision: {
      status: violations.length === 0 ? "ready" : "failed",
      fullReleaseGateRequiredForEachTask: false,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "phase10-focused-gate-matrix", report, renderMarkdown);
  console.log("Phase 10 focused gate matrix decision: " + report.decision.status);
  if (violations.length > 0) {
    process.exitCode = 1;
  }
}

function requiresInstrumentationSelector(task) {
  return [
    "verifyNodePackagedParityGate",
    "verifyNodeLongRunningGate",
    "verifyNodeWorkerWasiGate",
    "verifyNodePhase10Gate",
  ].includes(task);
}

function category(task) {
  if (task.includes("Packaged")) return "packaged";
  if (task.includes("LiveProvider")) return "live_provider";
  if (task.includes("PackageManager")) return "package_manager";
  if (task.includes("LongRunning")) return "long_running";
  if (task.includes("MultiInstance")) return "multi_instance";
  if (task.includes("WorkerWasi")) return "worker_wasi";
  if (task.includes("Adapter")) return "adapter";
  return "aggregate";
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 10 Focused Gate Matrix",
    "",
    `- Decision: ${report.decision.status}`,
    `- Required tasks: ${report.tasks.length}`,
    "",
    "| Task | Category | Registered In | Focused Selector |",
    "| --- | --- | --- | --- |",
  ];
  for (const task of report.tasks) {
    lines.push(`| ${task.task} | ${task.category} | ${task.registeredIn || "<missing>"} | ${task.hasInstrumentationSelector ? "yes" : "no"} |`);
  }
  lines.push("", "## Policy Violations", "");
  if (report.policyViolations.length === 0) {
    lines.push("None.");
  } else {
    report.policyViolations.forEach((violation) => lines.push(`- ${violation}`));
  }
  return lines.join("\n") + "\n";
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-focused-gate-matrix.js [--repo-root dir] [--report-dir dir]");
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

function writeReport(reportDir, basename, json, markdownRenderer) {
  fs.writeFileSync(path.join(reportDir, `${basename}.json`), JSON.stringify(json, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${basename}.md`), markdownRenderer(json));
}

main();
