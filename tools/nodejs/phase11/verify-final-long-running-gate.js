#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const FILES = {
  phase10Report: "build/reports/nodejs/phase10-long-running-activation.json",
  phase10Tool: "tools/nodejs/phase10/verify-long-running-activation.js",
  buildGradle: "app/build.gradle.kts",
  packagingPreflight: "app/embedded-node-packaging-preflight.gradle.kts",
  phase10Gate: "app/node-phase10-gate.gradle.kts",
  finalGate: "app/node-phase11-gate.gradle.kts",
  executionModePolicy: "app/src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt",
  runtimeBudgetPolicy: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeBudgetPolicy.kt",
  checkpointStore: "app/src/main/java/org/autojs/autojs/engine/NodeLifecycleCheckpointStore.kt",
  statusStore: "app/src/main/java/org/autojs/autojs/engine/NodeInteractiveLongRunningStatusStore.kt",
  scriptService: "app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt",
  interactiveTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeInteractiveLongRunningInstrumentationTest.kt",
  stressTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeLongRunningStressInstrumentationTest.kt",
  lifecycleStressTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedLifecycleStressInstrumentationTest.kt",
  designDoc: "docs/nodejs/design/LONG_RUNNING_SERVICE.md",
  phase10Doc: "docs/nodejs/reports/phase10/LONG_RUNNING_ACTIVATION.md",
  phase13PackagedReport: "docs/nodejs/reports/phase13/P13_07_PACKAGED_LONG_RUNNING_SERVICE.md",
  blockerRegistry: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
};

const EVIDENCE_CHECKS = [
  ["phase10_tool", "phase10Tool", "s10_30_interactive_long_running_runtime"],
  ["phase10_gate", "phase10Gate", "\"verifyNodePhase10LongRunningActivation\""],
  ["long_running_gate_mapping", "buildGradle", "\"verifyNodeLongRunningGate\" to nodeJsLongRunningGateInstrumentationClasses"],
  ["long_running_gate_registration", "packagingPreflight", "val verifyNodeLongRunningGate = tasks.register"],
  ["final_gate_task", "finalGate", "\"verifyNodeFinalLongRunningGate\""],
  ["mode_policy", "executionModePolicy", "MODE_INTERACTIVE_LONG_RUNNING"],
  ["scheduled_policy", "executionModePolicy", "LAUNCH_SURFACE_SCHEDULED_RUNNER"],
  ["interactive_start", "scriptService", "startInteractiveLongRunning"],
  ["interactive_stop", "scriptService", "stopInteractiveLongRunning"],
  ["stop_action", "statusStore", "STOP_ACTION_NAME"],
  ["restart_policy_never", "statusStore", "restartPolicy"],
  ["automatic_restart_false", "statusStore", "automaticRestart"],
  ["heartbeat_snapshot", "runtimeBudgetPolicy", "heartbeatSnapshot"],
  ["checkpoint_api", "checkpointStore", "METHOD_CHECKPOINT"],
  ["interactive_test", "interactiveTest", "NodeInteractiveLongRunningInstrumentationTest"],
  ["stress_test", "stressTest", "NodeLongRunningStressInstrumentationTest"],
  ["lifecycle_queue_stress", "lifecycleStressTest", "queuedExecutionRunsAfterActiveExecution"],
  ["packaged_doc_boundary", "designDoc", "packaged long-running launch surface"],
  ["phase10_doc_boundary", "phase10Doc", "Packaged long-running service mode"],
  ["phase13_packaged_partial", "phase13PackagedReport", "Status: partial packaged dispatch, metadata/disclosure policy, and Stop/crash"],
  ["phase13_packaged_stop_task_removal", "phase13PackagedReport", "The same focused smoke removes the packaged Activity task"],
];

const REQUIRED_PHASE10_ITEMS = [
  "s10_29_execution_mode_state_machine",
  "s10_30_interactive_long_running_runtime",
  "s10_31_quota_heartbeat_watchdog",
  "s10_33_execution_queue",
  "s10_34_scheduled_node_task_runner",
  "s10_35_checkpoint_restore",
  "s10_36_long_running_multi_instance_stress",
];

const FINAL_BLOCKER_IDS = [
  "packaged_long_running",
  "full_heavy_resource_stress",
];

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
  const registryBlockers = FINAL_BLOCKER_IDS.map((id) => registryBlockerEntry(registry, id));
  const packagedLongRunningRegistryOpen = registryBlockers.some((entry) => (
    entry.id === "packaged_long_running" && entry.open
  ));
  const decision = phase10Report && phase10Report.decision ? phase10Report.decision : {};
  const phase10Status = decisionStatus(phase10Report);
  const blockers = [];
  evidenceChecks
    .filter((entry) => !entry.present)
    .forEach((entry) => blockers.push("missing long-running evidence: " + entry.id));
  phase10Items
    .filter((entry) => !entry.ready)
    .forEach((entry) => blockers.push("Phase 10 long-running item is not ready: " + entry.id));
  if (!phase10Report) {
    blockers.push("Phase 10 long-running activation report is missing");
  } else if (phase10Status !== "ready_with_recorded_blockers") {
    blockers.push("Phase 10 long-running activation report has unexpected status: " + phase10Status);
  }
  if (decision.interactiveLongRunningPromoted !== true) {
    blockers.push("interactive long-running runtime is not promoted");
  }
  if (decision.scheduledRunnerPromoted !== true) {
    blockers.push("scheduled runner is not promoted");
  }
  if (decision.automaticRestartEnabled !== false) {
    blockers.push("automatic restart must remain disabled");
  }
  if (decision.packagedLongRunningPromoted !== true && !packagedLongRunningRegistryOpen) {
    blockers.push("packaged long-running service mode is not promoted");
  }
  registryBlockers
    .filter((entry) => entry.open)
    .forEach((entry) => blockers.push(`${entry.id}: ${entry.currentEvidence}`));
  const report = {
    schema: "autojs6-node-final-long-running-gate-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeFinalLongRunningGate",
    tasks: ["S11-25", "S11-26", "S11-27", "S11-28"],
    sources: Object.fromEntries(
      Object.entries(FILES).map(([id, relative]) => [id, relativePath(repoRoot, path.join(repoRoot, relative))]),
    ),
    phase10: {
      reportAvailable: Boolean(phase10Report),
      reportStatus: phase10Status,
      interactiveLongRunningPromoted: decision.interactiveLongRunningPromoted === true,
      scheduledRunnerPromoted: decision.scheduledRunnerPromoted === true,
      packagedLongRunningPromoted: decision.packagedLongRunningPromoted === true,
      automaticRestartEnabled: decision.automaticRestartEnabled === true,
      policyViolations: Array.isArray(phase10Report && phase10Report.policyViolations)
        ? phase10Report.policyViolations
        : [],
      items: phase10Items,
    },
    evidence: {
      status: evidenceChecks.every((entry) => entry.present) ? "ready" : "missing_evidence",
      checks: evidenceChecks,
    },
    finalPromotion: {
      packagedServiceMode: decision.packagedLongRunningPromoted === true ? "promoted" : "blocked",
      fullHeavyStress: registryBlockers.some((entry) => entry.id === "full_heavy_resource_stress" && entry.open)
        ? "blocked"
        : "ready",
      restartPolicy: "never",
      automaticRestartAllowed: false,
      registryBlockers,
    },
    blockers,
    decision: {
      status: blockers.length === 0 ? "ready" : "blocked",
      interactiveLongRunningReady: decision.interactiveLongRunningPromoted === true,
      packagedLongRunningAllowed: decision.packagedLongRunningPromoted === true &&
        !registryBlockers.some((entry) => entry.open && entry.id === "packaged_long_running"),
      fullHeavyStressReady: !registryBlockers.some((entry) => entry.open && entry.id === "full_heavy_resource_stress"),
      safeNodeProfilePreserved: true,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "final-long-running-gate", report, renderMarkdown);
  console.log("Final long-running gate decision: " + report.decision.status);
  if (options.failOnBlocked && report.decision.status !== "ready") {
    process.exitCode = 1;
  }
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
    "# Final Long-running Gate",
    "",
    `- Decision: ${report.decision.status}`,
    `- Phase 10 status: ${report.phase10.reportStatus}`,
    `- Interactive long-running ready: ${report.decision.interactiveLongRunningReady}`,
    `- Packaged long-running allowed: ${report.decision.packagedLongRunningAllowed}`,
    `- Full heavy stress ready: ${report.decision.fullHeavyStressReady}`,
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
    "## Evidence Checks",
    "",
    "| Check | Present | File |",
    "| --- | --- | --- |",
  );
  report.evidence.checks.forEach((entry) => {
    lines.push(`| ${entry.id} | ${entry.present} | ${entry.file} |`);
  });
  lines.push(
    "",
    "## Registry Blockers",
    "",
    "| Blocker | Status | Classification |",
    "| --- | --- | --- |",
  );
  report.finalPromotion.registryBlockers.forEach((entry) => {
    lines.push(`| ${entry.id} | ${entry.status} | ${entry.classification} |`);
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
      console.log("Usage: node verify-final-long-running-gate.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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
