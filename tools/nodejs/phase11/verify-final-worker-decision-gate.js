#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const FILES = {
  phase10Report: "build/reports/nodejs/phase10-worker-wasi-boundary.json",
  phase10Tool: "tools/nodejs/phase10/verify-worker-wasi-boundary.js",
  buildGradle: "app/build.gradle.kts",
  phase10Gate: "app/node-phase10-gate.gradle.kts",
  finalGate: "app/node-phase11-gate.gradle.kts",
  workerSupport: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt",
  workerBootstrapTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsBootstrapInstrumentationTest.kt",
  workerDesignTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsDesignGateInstrumentationTest.kt",
  workerExperimentalTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsExperimentalInstrumentationTest.kt",
  workerStressTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt",
  wasmWasiTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWasmWasiInstrumentationTest.kt",
  workerDoc: "docs/nodejs/design/WORKER_THREADS.md",
  phase10Doc: "docs/nodejs/reports/phase10/WORKER_WASI_BOUNDARY.md",
  phase9WasmWasiDoc: "docs/nodejs/reports/phase9/WASM_WASI.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  blockerRegistry: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
};

const EVIDENCE_CHECKS = [
  ["phase10_tool", "phase10Tool", "s10_37_worker_capability_probe"],
  ["phase10_gate", "phase10Gate", "\"verifyNodePhase10WorkerWasiBoundary\""],
  ["worker_wasi_gate", "phase10Gate", "\"verifyNodeWorkerWasiGate\""],
  ["final_gate_task", "finalGate", "\"verifyNodeFinalWorkerDecisionGate\""],
  ["worker_flag", "buildGradle", "autojs.nodejs.worker_threads.experimental"],
  ["worker_native_preflight", "workerSupport", "probeNativeWorkerThreads"],
  ["worker_default_denial", "workerBootstrapTest", "defaultRequestDeniesWorkerThreadsBeforeShadowing"],
  ["worker_design_gate", "workerDesignTest", "workerThreadsRemainDesignGatedAndDisabledByDefault"],
  ["worker_message_channel_scope", "workerExperimentalTest", "MessageChannel"],
  ["worker_stress_scope", "workerStressTest", "WorkerPool"],
  ["raw_wasi_denial", "wasmWasiTest", "rawWasiBuiltinsRemainDisabledAndCannotBeShadowed"],
  ["wasm_basic", "wasmWasiTest", "webAssemblyCompileInstantiateAndFileAssetWork"],
  ["worker_doc_decision", "workerDoc", "blocked for Node Profile v1.1 runnable execution"],
  ["phase10_doc_boundary", "phase10Doc", "ready_with_recorded_blockers"],
  ["wasi_doc_boundary", "phase9WasmWasiDoc", "Raw `node:wasi` remains disabled"],
  ["compatibility_worker_boundary", "compatibilityProfile", "`worker_threads` remains disabled by default"],
];

const REQUIRED_PHASE10_ITEMS = [
  "s10_37_worker_capability_probe",
  "s10_38_controlled_worker_mvp",
  "s10_39_message_channel_transfer",
  "s10_40_worker_pool_resource_limits",
  "s10_41_controlled_wasi_profile",
  "s10_42_wasm_compile_cache_packaged_support",
];

const FINAL_DECISION_IDS = ["worker_threads", "controlled_wasi"];
const RESOLVED_DECISION_STATUSES = new Set([
  "closed",
  "implemented",
  "permanently_unsupported",
  "intentionally_unsupported",
  "unsupported_by_policy",
]);

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = Object.fromEntries(
    Object.entries(FILES).map(([id, relative]) => [id, read(path.join(repoRoot, relative))]),
  );
  const phase10Report = readJsonIfExists(path.join(repoRoot, FILES.phase10Report));
  const registry = readJsonIfExists(path.join(repoRoot, FILES.blockerRegistry)) || { blockers: [] };
  const evidenceChecks = EVIDENCE_CHECKS.map(([id, fileId, token]) => ({
    id,
    file: FILES[fileId],
    token,
    present: files[fileId].includes(token),
  }));
  const phase10Items = REQUIRED_PHASE10_ITEMS.map((id) => {
    const item = phase10Report && Array.isArray(phase10Report.items)
      ? phase10Report.items.find((candidate) => candidate.id === id)
      : null;
    return {
      id,
      status: item ? item.status : "missing",
      ready: Boolean(item && item.ready === true),
      blocker: item ? item.blocker : "missing Phase 10 item",
    };
  });
  const registryDecisions = FINAL_DECISION_IDS.map((id) => registryDecision(registry, id));
  const decision = phase10Report && phase10Report.decision ? phase10Report.decision : {};
  const phase10Status = decisionStatus(phase10Report);
  const blockers = [];
  evidenceChecks
    .filter((entry) => !entry.present)
    .forEach((entry) => blockers.push("missing Worker/WASI evidence: " + entry.id));
  phase10Items
    .filter((entry) => !entry.ready)
    .forEach((entry) => blockers.push("Phase 10 Worker/WASI item is not ready: " + entry.id));
  if (!phase10Report) {
    blockers.push("Phase 10 Worker/WASI boundary report is missing");
  } else if (phase10Status !== "ready_with_recorded_blockers") {
    blockers.push("Phase 10 Worker/WASI boundary report has unexpected status: " + phase10Status);
  }
  if (decision.wasmBasicPromoted !== true) {
    blockers.push("basic WebAssembly is not promoted");
  }
  if (decision.rawWasiPromoted !== false) {
    blockers.push("raw WASI must remain disabled unless a controlled WASI decision replaces it");
  }
  registryDecisions
    .filter((entry) => entry.open)
    .forEach((entry) => blockers.push(`${entry.id}: ${entry.currentEvidence}`));
  const report = {
    schema: "autojs6-node-final-worker-decision-gate-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeFinalWorkerDecisionGate",
    tasks: ["S11-29", "S11-30", "S11-31"],
    sources: Object.fromEntries(
      Object.entries(FILES).map(([id, relative]) => [id, relativePath(repoRoot, path.join(repoRoot, relative))]),
    ),
    phase10: {
      reportAvailable: Boolean(phase10Report),
      reportStatus: phase10Status,
      workerThreadsPromoted: decision.workerThreadsPromoted === true,
      rawWasiPromoted: decision.rawWasiPromoted === true,
      wasmBasicPromoted: decision.wasmBasicPromoted === true,
      controlledWasiPromoted: decision.controlledWasiPromoted === true,
      packagedWasmPromoted: decision.packagedWasmPromoted === true,
      policyViolations: Array.isArray(phase10Report && phase10Report.policyViolations)
        ? phase10Report.policyViolations
        : [],
      items: phase10Items,
    },
    evidence: {
      status: evidenceChecks.every((entry) => entry.present) ? "ready" : "missing_evidence",
      checks: evidenceChecks,
    },
    finalDecisions: registryDecisions,
    blockers,
    decision: {
      status: blockers.length === 0 ? "ready" : "blocked",
      workerThreadsDecisionResolved: !registryDecisions.some((entry) => entry.open && entry.id === "worker_threads"),
      controlledWasiDecisionResolved: !registryDecisions.some((entry) => entry.open && entry.id === "controlled_wasi"),
      workerThreadsDefaultAllowed: decision.workerThreadsPromoted === true,
      rawWasiAllowed: false,
      safeNodeProfilePreserved: true,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "final-worker-decision-gate", report, renderMarkdown);
  console.log("Final Worker/WASI decision gate decision: " + report.decision.status);
  if (options.failOnBlocked && report.decision.status !== "ready") {
    process.exitCode = 1;
  }
}

function registryDecision(registry, id) {
  const entry = (registry.blockers || []).find((candidate) => candidate.id === id);
  const status = entry ? entry.status : "missing";
  return {
    id,
    status,
    classification: entry ? entry.classification : "missing",
    currentEvidence: entry ? entry.currentEvidence : "blocker registry entry is missing",
    requiredResolution: entry ? entry.requiredResolution : "missing registry entry",
    open: !RESOLVED_DECISION_STATUSES.has(status),
  };
}

function decisionStatus(json) {
  if (!json) return "missing";
  if (json.decision && json.decision.status) return json.decision.status;
  return json.status || "unknown";
}

function renderMarkdown(report) {
  const lines = [
    "# Final Worker/WASI Decision Gate",
    "",
    `- Decision: ${report.decision.status}`,
    `- Phase 10 status: ${report.phase10.reportStatus}`,
    `- worker_threads decision resolved: ${report.decision.workerThreadsDecisionResolved}`,
    `- controlled WASI decision resolved: ${report.decision.controlledWasiDecisionResolved}`,
    `- Basic WebAssembly promoted: ${report.phase10.wasmBasicPromoted}`,
    `- Raw WASI allowed: ${report.decision.rawWasiAllowed}`,
    "",
    "## Phase 10 Items",
    "",
    "| Item | Status | Ready | Blocker |",
    "| --- | --- | --- | --- |",
  ];
  report.phase10.items.forEach((entry) => {
    lines.push(`| ${entry.id} | ${entry.status} | ${entry.ready} | ${entry.blocker || ""} |`);
  });
  lines.push(
    "",
    "## Final Decisions",
    "",
    "| Decision | Status | Classification |",
    "| --- | --- | --- |",
  );
  report.finalDecisions.forEach((entry) => {
    lines.push(`| ${entry.id} | ${entry.status} | ${entry.classification} |`);
  });
  lines.push(
    "",
    "## Evidence Checks",
    "",
    "| Check | Present | File |",
    "| --- | --- | --- |",
  );
  report.evidence.checks.forEach((entry) => {
    lines.push(`| ${entry.id} | ${entry.present} | ${entry.file} |`);
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
      console.log("Usage: node verify-final-worker-decision-gate.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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
