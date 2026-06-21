#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const FILES = {
  finalGate: "app/node-phase11-gate.gradle.kts",
  buildGradle: "app/build.gradle.kts",
  memoryLeakStressTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedMemoryLeakStressInstrumentationTest.kt",
  resourceSnapshot: "app/src/androidTest/java/org/autojs/autojs/engine/NodeResourceStressSnapshot.kt",
  longRunningStressTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeLongRunningStressInstrumentationTest.kt",
  workerStressTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  performanceDoc: "docs/nodejs/PERFORMANCE.md",
  finalDefinition: "docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md",
  blockerRegistry: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
};

const STRESS_ANCHOR_CHECKS = [
  ["final_soak_task", "finalGate", "\"verifyNodeFinalSoakGate\""],
  ["phase7_resource_gate", "ciGatesDoc", "verifyNodePhase7ResourceStressGate"],
  ["memory_leak_stress", "memoryLeakStressTest", "autojs6-node-memory-leak-stress-v1"],
  ["resource_snapshot", "resourceSnapshot", "rssKb"],
  ["long_running_stress", "longRunningStressTest", "NodeLongRunningStressInstrumentationTest"],
  ["worker_stress", "workerStressTest", "NodeWorkerThreadsResourceStressInstrumentationTest"],
  ["testing_resource_stress", "testingDoc", "NodePhase7ResourceStressInstrumentationTest"],
  ["performance_thresholds", "performanceDoc", "autojs.nodejs.leak.maxRssDeltaKb"],
  ["final_definition_soak", "finalDefinition", "Final soak records 24-hour RSS"],
];

const REQUIRED_SOAK_REPORTS = [
  {
    id: "quick_soak_8h",
    relative: "build/reports/nodejs/final-soak/quick-soak-8h.json",
    minDurationHours: 8,
  },
  {
    id: "final_soak_24h",
    relative: "build/reports/nodejs/final-soak/final-soak-24h.json",
    minDurationHours: 24,
  },
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = Object.fromEntries(
    Object.entries(FILES).map(([id, relative]) => [id, read(path.join(repoRoot, relative))]),
  );
  const registry = readJsonIfExists(path.join(repoRoot, FILES.blockerRegistry)) || { blockers: [] };
  const anchorChecks = STRESS_ANCHOR_CHECKS.map(([id, fileId, token]) => ({
    id,
    file: FILES[fileId],
    token,
    present: files[fileId].includes(token),
  }));
  const soakReports = REQUIRED_SOAK_REPORTS.map((entry) => readSoakReport(repoRoot, entry));
  const registryBlocker = registryBlockerEntry(registry, "full_heavy_resource_stress");
  const blockers = [];
  anchorChecks
    .filter((entry) => !entry.present)
    .forEach((entry) => blockers.push("missing soak/stress anchor: " + entry.id));
  soakReports
    .filter((entry) => !entry.available)
    .forEach((entry) => blockers.push("missing required soak evidence: " + entry.id));
  soakReports
    .filter((entry) => entry.available && !entry.durationReady)
    .forEach((entry) => blockers.push(`soak evidence duration is below ${entry.minDurationHours}h: ${entry.id}`));
  soakReports
    .filter((entry) => entry.available && !entry.metricsClean)
    .forEach((entry) => blockers.push("soak evidence reports leaks, orphan process, or unrecoverable runtime: " + entry.id));
  if (registryBlocker.open) {
    blockers.push(`${registryBlocker.id}: ${registryBlocker.currentEvidence}`);
  }
  const report = {
    schema: "autojs6-node-final-soak-gate-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeFinalSoakGate",
    tasks: ["S11-32", "S11-33"],
    sources: Object.fromEntries(
      Object.entries(FILES).map(([id, relative]) => [id, relativePath(repoRoot, path.join(repoRoot, relative))]),
    ),
    anchors: {
      status: anchorChecks.every((entry) => entry.present) ? "ready" : "missing_evidence",
      checks: anchorChecks,
    },
    soakReports,
    registryBlockers: [registryBlocker],
    blockers,
    decision: {
      status: blockers.length === 0 ? "ready" : "blocked",
      quickSoakReady: soakReports.some((entry) => entry.id === "quick_soak_8h" && entry.ready),
      finalSoakReady: soakReports.some((entry) => entry.id === "final_soak_24h" && entry.ready),
      fullHeavyResourceStressReady: !registryBlocker.open && soakReports.every((entry) => entry.ready),
      safeNodeProfilePreserved: true,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "final-soak-gate", report, renderMarkdown);
  console.log("Final soak gate decision: " + report.decision.status);
  if (options.failOnBlocked && report.decision.status !== "ready") {
    process.exitCode = 1;
  }
}

function readSoakReport(repoRoot, requirement) {
  const file = path.join(repoRoot, requirement.relative);
  const json = readJsonIfExists(file);
  const durationHours = durationHoursFrom(json);
  const metricsClean = json ? metricsAreClean(json) : false;
  return {
    id: requirement.id,
    path: requirement.relative,
    minDurationHours: requirement.minDurationHours,
    available: Boolean(json),
    durationHours,
    durationReady: durationHours >= requirement.minDurationHours,
    metricsClean,
    ready: Boolean(json) && durationHours >= requirement.minDurationHours && metricsClean,
  };
}

function durationHoursFrom(json) {
  if (!json) return 0;
  if (typeof json.durationHours === "number") return json.durationHours;
  if (typeof json.durationMs === "number") return json.durationMs / 3600000;
  const start = Date.parse(json.startedAt || json.startTime || "");
  const end = Date.parse(json.completedAt || json.endTime || "");
  if (Number.isFinite(start) && Number.isFinite(end) && end >= start) {
    return (end - start) / 3600000;
  }
  return 0;
}

function metricsAreClean(json) {
  if (json.decision && json.decision.status && !["ready", "passed", "completed"].includes(json.decision.status)) {
    return false;
  }
  if (json.status && !["ready", "passed", "completed"].includes(json.status)) {
    return false;
  }
  const flags = [
    json.monotonicLeaks,
    json.orphanProcess,
    json.unrecoverableRuntime,
    json.leakDetected,
  ];
  return flags.every((value) => value !== true);
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

function renderMarkdown(report) {
  const lines = [
    "# Final Soak Gate",
    "",
    `- Decision: ${report.decision.status}`,
    `- Stress anchors: ${report.anchors.status}`,
    `- Quick soak ready: ${report.decision.quickSoakReady}`,
    `- Final soak ready: ${report.decision.finalSoakReady}`,
    `- Full heavy resource stress ready: ${report.decision.fullHeavyResourceStressReady}`,
    "",
    "## Stress Anchors",
    "",
    "| Check | Present | File |",
    "| --- | --- | --- |",
  ];
  report.anchors.checks.forEach((entry) => {
    lines.push(`| ${entry.id} | ${entry.present} | ${entry.file} |`);
  });
  lines.push(
    "",
    "## Soak Evidence",
    "",
    "| Report | Available | Duration Hours | Metrics Clean | Ready |",
    "| --- | --- | --- | --- | --- |",
  );
  report.soakReports.forEach((entry) => {
    lines.push(`| ${entry.id} | ${entry.available} | ${entry.durationHours} | ${entry.metricsClean} | ${entry.ready} |`);
  });
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
      console.log("Usage: node verify-final-soak-gate.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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
