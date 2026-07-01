#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-full-heavy-resource-stress";

const FILES = {
  blockerRegistry: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_08_FULL_HEAVY_RESOURCE_STRESS.md",
  soakRunner: "tools/nodejs/phase13/run-full-heavy-resource-soak.js",
  archiveScript: "tools/nodejs/phase13/archive-full-heavy-resource-soak.js",
  phase11SoakScript: "tools/nodejs/phase11/verify-final-soak-gate.js",
  phase11Gate: "app/node-phase11-gate.gradle.kts",
  phase13Gate: "app/node-phase13-gate.gradle.kts",
  buildGradle: "app/build.gradle.kts",
  memoryLeakStressTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedMemoryLeakStressInstrumentationTest.kt",
  phase7ResourceStressTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodePhase7ResourceStressInstrumentationTest.kt",
  bridgeResourceStressTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeResourceLeakStressInstrumentationTest.kt",
  processPoolPolicyTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicyInstrumentationTest.kt",
  longRunningStressTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeLongRunningStressInstrumentationTest.kt",
  workerStressTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt",
  finalSoakTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeFinalSoakInstrumentationTest.kt",
  resourceSnapshot: "app/src/androidTest/java/org/autojs/autojs/engine/NodeResourceStressSnapshot.kt",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  performanceDoc: "docs/nodejs/PERFORMANCE.md",
  finalDefinition: "docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md",
};

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

const REQUIRED_METRICS = [
  "rssKb",
  "javaHeapKb",
  "nativeHeapKb",
  "threadCount",
  "fdCount",
  "bridgeDiagnostics",
];

const CONTRACT_FIXTURE_REPORTS = [
  {
    id: "fixture_accept_quick_soak",
    relative: "tools/nodejs/phase13/fixtures/full-heavy-resource-stress/accepted-quick-soak-8h.json",
    minDurationHours: 8,
    expectedReady: true,
  },
  {
    id: "fixture_accept_final_soak_timestamps",
    relative: "tools/nodejs/phase13/fixtures/full-heavy-resource-stress/accepted-final-soak-24h-timestamps.json",
    minDurationHours: 24,
    expectedReady: true,
  },
  {
    id: "fixture_reject_under_duration",
    relative: "tools/nodejs/phase13/fixtures/full-heavy-resource-stress/rejected-under-duration.json",
    minDurationHours: 8,
    expectedReady: false,
  },
  {
    id: "fixture_reject_missing_metric",
    relative: "tools/nodejs/phase13/fixtures/full-heavy-resource-stress/rejected-missing-bridge-diagnostics.json",
    minDurationHours: 8,
    expectedReady: false,
  },
  {
    id: "fixture_reject_dirty_runtime",
    relative: "tools/nodejs/phase13/fixtures/full-heavy-resource-stress/rejected-leak-orphan-runtime.json",
    minDurationHours: 24,
    expectedReady: false,
  },
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const soakReports = REQUIRED_SOAK_REPORTS.map((entry) => readSoakReport(repoRoot, entry));
  const contractFixtures = CONTRACT_FIXTURE_REPORTS.map((entry) => readContractFixture(repoRoot, entry));
  const report = buildReport(files, soakReports, contractFixtures);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 13 full heavy resource stress: " + report.decision.status);
  console.log("Phase 13 full heavy resource stress blockers: " + report.blockers.length);
  console.log("Phase 13 full heavy resource stress policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0 || (options.failOnBlocked && !report.decision.finalBlockerClosable)) {
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

function buildReport(files, soakReports, contractFixtures) {
  const blockerEntry = blocker(files.blockerRegistry.text, "full_heavy_resource_stress");
  const anchorChecks = [
    token(files.phase13Tasks, "P13-08"),
    token(files.p13Report, "Status: ready"),
    token(files.soakRunner, "autojs6-node-phase13-full-heavy-resource-soak-runner-v1"),
    token(files.soakRunner, "NodeFinalSoakInstrumentationTest#fullHeavyResourceSoakProducesArchiveReadyReport"),
    token(files.archiveScript, "autojs6-node-phase13-full-heavy-resource-stress-archive-v1"),
    token(files.phase11SoakScript, "autojs6-node-final-soak-gate-v1"),
    token(files.phase11Gate, "generateNodeFinalSoakStatus"),
    token(files.phase11Gate, "verifyNodeFinalSoakGate"),
    token(files.buildGradle, "nodeJsPhase7ResourceStressGateInstrumentationClasses"),
    token(files.memoryLeakStressTest, "autojs6-node-memory-leak-stress-v1"),
    token(files.phase7ResourceStressTest, "autojs6-node-phase7-resource-stress-v1"),
    token(files.bridgeResourceStressTest, "bridge resource stress"),
    token(files.processPoolPolicyTest, "concurrentSlotsStressSharedResourcesRemainIsolated"),
    token(files.processPoolPolicyTest, "p13.pool.resource.summary"),
    token(files.processPoolPolicyTest, "embedded_script.crash_evidence.marker_path"),
    token(files.processPoolPolicyTest, "processPoolSlot = NodeEmbeddedProcessPoolPolicy.OVERFLOW_SLOT_ID"),
    token(files.longRunningStressTest, "NodeLongRunningStressInstrumentationTest"),
    token(files.workerStressTest, "NodeWorkerThreadsResourceStressInstrumentationTest"),
    token(files.finalSoakTest, "autojs6-node-final-soak-run-v1"),
    token(files.finalSoakTest, "node.final_soak.public"),
    token(files.resourceSnapshot, "rssKb"),
    token(files.resourceSnapshot, "bridgeDiagnostics"),
    token(files.testingDoc, "verifyNodePhase13FullHeavyResourceStress"),
    token(files.ciGatesDoc, "verifyNodePhase13FullHeavyResourceStress"),
    token(files.performanceDoc, "autojs.nodejs.leak.maxRssDeltaKb"),
    token(files.finalDefinition, "Final soak records 24-hour RSS"),
  ];
  const missingAnchors = anchorChecks
    .filter((entry) => !entry.present)
    .map((entry) => `missing stress anchor: ${entry.file} :: ${entry.token}`);
  const soakBlockers = soakReports.flatMap((entry) => entry.blockers.map((blockerText) => `${entry.id}: ${blockerText}`));
  const contractFixtureViolations = contractFixtures
    .filter((entry) => !entry.passed)
    .map((entry) => `${entry.id}: expected ready=${entry.expectedReady}, actual ready=${entry.ready}`);
  const quickSoakReady = reportById(soakReports, "quick_soak_8h").ready;
  const finalSoakReady = reportById(soakReports, "final_soak_24h").ready;
  const comparableReportReady = soakReports.every((entry) => entry.available && entry.requiredMetricsReady);
  const registryBlocker = (() => {
    if (!blockerEntry) {
      return ["full_heavy_resource_stress: blocker registry entry is missing"];
    }
    if (blockerEntry.status === "closed") {
      return [];
    }
    if (quickSoakReady && finalSoakReady && comparableReportReady) {
      return ["full_heavy_resource_stress: close FINAL_BLOCKER_REGISTRY after quick/final soak reports are archived and ready."];
    }
    return [];
  })();
  const policyViolations = [
    ...missingAnchors,
    ...contractFixtureViolations,
    ...(!blockerEntry || !["open", "closed"].includes(blockerEntry.status)
      ? ["full_heavy_resource_stress registry entry must exist with open or closed status."]
      : []),
  ];
  const blockers = [
    ...soakBlockers,
    ...registryBlocker,
  ];
  const finalBlockerClosable = (
    policyViolations.length === 0 &&
    blockers.length === 0 &&
    quickSoakReady &&
    finalSoakReady &&
    comparableReportReady
  );
  return {
    schema: "autojs6-node-phase13-full-heavy-resource-stress-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13FullHeavyResourceStress",
    task: "P13-08",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    expectedSoakReports: REQUIRED_SOAK_REPORTS,
    requiredMetrics: REQUIRED_METRICS,
    contractFixtureRequirements: CONTRACT_FIXTURE_REPORTS,
    anchors: {
      status: missingAnchors.length === 0 ? "ready" : "missing_evidence",
      checks: anchorChecks,
    },
    soakReports,
    contractFixtures,
    blockerRegistry: blockerEntry || {
      id: "full_heavy_resource_stress",
      status: "missing",
      currentEvidence: "blocker registry entry is missing",
    },
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : finalBlockerClosable ? "ready" : "partial",
      phase13TaskComplete: finalBlockerClosable,
      finalBlockerClosable,
      quickSoakReady,
      finalSoakReady,
      comparableReportReady,
      contractFixturesReady: contractFixtures.every((entry) => entry.passed),
      stressAnchorsReady: missingAnchors.length === 0,
      safeNodeProfilePreserved: true,
    },
    runbook: runbook(),
  };
}

function readSoakReport(repoRoot, requirement) {
  const file = path.join(repoRoot, requirement.relative);
  const json = readJsonIfExists(file);
  return evaluateSoakReportJson(json, requirement);
}

function evaluateSoakReportJson(json, requirement) {
  const durationHours = durationHoursFrom(json);
  const requiredMetricPresence = Object.fromEntries(REQUIRED_METRICS.map((key) => [key, hasKeyDeep(json, key)]));
  const requiredMetricsReady = Object.values(requiredMetricPresence).every(Boolean);
  const failureCount = failureCountFrom(json);
  const statusClean = statusIsClean(json);
  const monotonicLeakFree = !truthyKeyDeep(json, ["monotonicLeaks", "monotonicLeak", "leakDetected", "runawayDetected"]);
  const orphanFree = !truthyKeyDeep(json, ["orphanProcess", "orphanProcesses", "orphanedProcess", "orphanedResources"]);
  const runtimeRecoverable = !truthyKeyDeep(json, ["unrecoverableRuntime", "runtimeUnrecoverable", "nativeCrash"]);
  const metricsClean = Boolean(json) && statusClean && failureCount === 0 && monotonicLeakFree && orphanFree && runtimeRecoverable;
  const durationReady = durationHours >= requirement.minDurationHours;
  const blockers = [];
  if (!json) {
    blockers.push(`missing required soak evidence at ${requirement.relative}`);
  } else {
    if (!durationReady) {
      blockers.push(`duration ${round(durationHours)}h is below ${requirement.minDurationHours}h`);
    }
    if (!requiredMetricsReady) {
      blockers.push("report does not expose all required comparable metrics");
    }
    if (!metricsClean) {
      blockers.push("report indicates failures, leaks, orphan resources, or unrecoverable runtime");
    }
  }
  return {
    id: requirement.id,
    path: requirement.relative,
    minDurationHours: requirement.minDurationHours,
    available: Boolean(json),
    schema: json && typeof json.schema === "string" ? json.schema : "",
    status: statusFrom(json),
    durationHours,
    durationReady,
    requiredMetricPresence,
    requiredMetricsReady,
    failureCount,
    metricsClean,
    ready: Boolean(json) && durationReady && requiredMetricsReady && metricsClean,
    blockers,
  };
}

function readContractFixture(repoRoot, fixture) {
  const evaluated = readSoakReport(repoRoot, fixture);
  const passed = evaluated.available && evaluated.ready === fixture.expectedReady;
  return {
    ...evaluated,
    expectedReady: fixture.expectedReady,
    passed,
  };
}

function durationHoursFrom(json) {
  if (!json) return 0;
  if (typeof json.durationHours === "number") return json.durationHours;
  if (typeof json.durationMs === "number") return json.durationMs / 3600000;
  if (typeof json.elapsedMs === "number") return json.elapsedMs / 3600000;
  const start = Date.parse(json.startedAt || json.startTime || json.startedAtIso || "");
  const end = Date.parse(json.completedAt || json.endTime || json.completedAtIso || "");
  if (Number.isFinite(start) && Number.isFinite(end) && end >= start) {
    return (end - start) / 3600000;
  }
  return 0;
}

function failureCountFrom(json) {
  if (!json) return 0;
  let count = 0;
  walk(json, (value, key) => {
    if (key === "failures" && Array.isArray(value)) {
      count += value.length;
    }
    if (key === "failureCount" && typeof value === "number") {
      count += value;
    }
  });
  return count;
}

function statusFrom(json) {
  if (!json) return "missing";
  const candidates = [
    json.status,
    json.result,
    json.decision && json.decision.status,
  ].filter((value) => typeof value === "string");
  return candidates[0] || "unknown";
}

function statusIsClean(json) {
  const status = statusFrom(json);
  return status === "missing" ? false : !["failed", "blocked", "error", "crashed", "timeout"].includes(status);
}

function hasKeyDeep(value, needle) {
  let found = false;
  walk(value, (_, key) => {
    if (key === needle) {
      found = true;
    }
  });
  return found;
}

function truthyKeyDeep(value, keys) {
  let found = false;
  walk(value, (entry, key) => {
    if (keys.includes(key) && entry === true) {
      found = true;
    }
  });
  return found;
}

function walk(value, visitor) {
  if (!value || typeof value !== "object") return;
  if (Array.isArray(value)) {
    value.forEach((entry) => walk(entry, visitor));
    return;
  }
  Object.entries(value).forEach(([key, entry]) => {
    visitor(entry, key);
    walk(entry, visitor);
  });
}

function token(file, value) {
  return {
    file: file.relative,
    token: value,
    present: file.exists && file.text.includes(value),
  };
}

function blocker(text, id) {
  try {
    const registry = JSON.parse(stripBom(text));
    return (registry.blockers || []).find((entry) => entry.id === id) || null;
  } catch (_) {
    return null;
  }
}

function reportById(reports, id) {
  return reports.find((entry) => entry.id === id) || { ready: false };
}

function runbook() {
  return [
    {
      id: "status_gate",
      command: ".\\gradlew.bat --console=plain :app:verifyNodePhase13FullHeavyResourceStress",
      output: `build/reports/nodejs/${REPORT_BASENAME}.json`,
    },
    {
      id: "quick_soak_8h",
      command: "node tools/nodejs/phase13/run-full-heavy-resource-soak.js --kind quick --serial QV710AF65F --device-profile sony --archive",
      output: "build/reports/nodejs/final-soak/quick-soak-8h.json",
    },
    {
      id: "final_soak_24h",
      command: "node tools/nodejs/phase13/run-full-heavy-resource-soak.js --kind final --serial QV710AF65F --device-profile sony --archive",
      output: "build/reports/nodejs/final-soak/final-soak-24h.json",
    },
  ];
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Full Heavy Resource Stress",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Final blocker closable: \`${report.decision.finalBlockerClosable}\``,
    `- Quick soak ready: \`${report.decision.quickSoakReady}\``,
    `- Final soak ready: \`${report.decision.finalSoakReady}\``,
    `- Comparable report ready: \`${report.decision.comparableReportReady}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    `- Blockers: ${report.blockers.length}`,
    "",
    "## Soak Reports",
    "",
    "| Report | Available | Duration Hours | Metrics | Clean | Ready |",
    "| --- | --- | --- | --- | --- | --- |",
  ];
  report.soakReports.forEach((entry) => {
    lines.push(
      `| \`${entry.id}\` | ${entry.available} | ${round(entry.durationHours)} | ${entry.requiredMetricsReady} | ${entry.metricsClean} | ${entry.ready} |`,
    );
  });
  lines.push("", "## Anchor Checks", "", "| File | Token | Present |", "| --- | --- | --- |");
  report.anchors.checks.forEach((entry) => {
    lines.push(`| \`${entry.file}\` | \`${escapeTable(entry.token)}\` | ${entry.present} |`);
  });
  lines.push("", "## Contract Fixtures", "", "| Fixture | Expected Ready | Actual Ready | Passed |", "| --- | --- | --- | --- |");
  report.contractFixtures.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | ${entry.expectedReady} | ${entry.ready} | ${entry.passed} |`);
  });
  lines.push("", "## Blockers", "");
  if (report.blockers.length === 0) {
    lines.push("None.");
  } else {
    report.blockers.forEach((entry) => lines.push(`- ${entry}`));
  }
  lines.push("", "## Runbook", "");
  report.runbook.forEach((entry) => {
    lines.push(`- \`${entry.id}\`: \`${entry.command}\``);
  });
  return lines.join("\n") + "\n";
}

function read(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "") : "";
}

function readJsonIfExists(file) {
  if (!fs.existsSync(file)) return null;
  try {
    return JSON.parse(stripBom(fs.readFileSync(file, "utf8")));
  } catch (_) {
    return null;
  }
}

function stripBom(value) {
  return String(value || "").replace(/^\uFEFF/, "");
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

function round(value) {
  return Math.round(Number(value || 0) * 1000) / 1000;
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-blocked"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-full-heavy-resource-stress.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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

if (require.main === module) {
  main();
}

module.exports = {
  REQUIRED_SOAK_REPORTS,
  REQUIRED_METRICS,
  evaluateSoakReportJson,
  readJsonIfExists,
  round,
};
