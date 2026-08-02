#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const {
  SOAK_REQUIREMENTS,
  currentIdentity,
  evaluateSoakReport,
} = require("../r6/verify-production-evidence.js");

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
  r6Verifier: "tools/nodejs/r6/verify-production-evidence.js",
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
    canonicalId: "quick-soak-8h",
  },
  {
    id: "final_soak_24h",
    canonicalId: "final-soak-24h",
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
  const pluginRoot = resolvePluginRoot(repoRoot, options.pluginRoot);
  const evidenceRoot = path.join(repoRoot, "build/reports/nodejs/r6-evidence");
  const identity = currentIdentity(repoRoot, pluginRoot);
  const anchorChecks = STRESS_ANCHOR_CHECKS.map(([id, fileId, token]) => ({
    id,
    file: FILES[fileId],
    token,
    present: files[fileId].includes(token),
  }));
  const soakReports = REQUIRED_SOAK_REPORTS.map((entry) => readSoakReport(
    repoRoot,
    evidenceRoot,
    identity,
    entry,
  ));
  const registryBlocker = registryBlockerEntry(registry, "full_heavy_resource_stress");
  const blockers = [];
  anchorChecks
    .filter((entry) => !entry.present)
    .forEach((entry) => blockers.push("missing soak/stress anchor: " + entry.id));
  identity.blockers.forEach((blocker) => blockers.push("R6 source identity: " + blocker));
  soakReports.forEach((entry) => {
    entry.blockers.forEach((blocker) => blockers.push(`${entry.id}: ${blocker}`));
  });
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
    canonicalContract: {
      verifier: FILES.r6Verifier,
      evidenceRoot: relativePath(repoRoot, evidenceRoot),
      pluginRoot: relativePath(repoRoot, pluginRoot),
      identity,
      identityReady: identity.blockers.length === 0,
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

function readSoakReport(repoRoot, evidenceRoot, identity, requirement) {
  const canonicalRequirement = SOAK_REQUIREMENTS.find((entry) => entry.id === requirement.canonicalId);
  if (!canonicalRequirement) {
    return {
      id: requirement.id,
      canonicalId: requirement.canonicalId,
      path: "missing",
      minDurationHours: 0,
      available: false,
      durationHours: 0,
      durationReady: false,
      metricsClean: false,
      blockers: [`canonical R6 soak requirement is missing: ${requirement.canonicalId}`],
      ready: false,
    };
  }
  const canonical = evaluateSoakReport(evidenceRoot, canonicalRequirement, identity, repoRoot);
  return {
    id: requirement.id,
    canonicalId: canonicalRequirement.id,
    kind: canonicalRequirement.kind,
    path: canonical.path,
    minDurationHours: canonicalRequirement.minDurationHours,
    available: canonical.available,
    durationHours: canonical.durationHours,
    durationReady: canonical.durationHours >= canonicalRequirement.minDurationHours,
    samples: canonical.samples,
    metricsClean: canonical.ready,
    sha256: canonical.sha256 || null,
    blockers: canonical.blockers,
    ready: identity.blockers.length === 0 && canonical.ready,
  };
}

function resolvePluginRoot(repoRoot, explicit) {
  if (explicit) return path.resolve(explicit);
  const workspace = path.dirname(repoRoot);
  const candidates = [
    path.join(workspace, "AutoJs6-Plugin-NodeJs-Runtime"),
    path.join(workspace, "autojs6-plugin-nodejs"),
  ];
  return candidates.find((candidate) => fs.existsSync(candidate)) || candidates[0];
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
      console.log("Usage: node verify-final-soak-gate.js [--repo-root dir] [--plugin-root dir] [--report-dir dir] [--fail-on-blocked]");
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
