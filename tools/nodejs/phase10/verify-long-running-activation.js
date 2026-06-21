#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const CHECKS = [
  {
    id: "s10_29_execution_mode_state_machine",
    status: "implemented",
    required: [
      ["executionModePolicy", "MODE_ONE_SHOT"],
      ["executionModePolicy", "MODE_INTERACTIVE_LONG_RUNNING"],
      ["executionModePolicy", "MODE_SCHEDULED"],
      ["executionModePolicy", "MODE_WORKER_COMPUTATION"],
      ["executionModePolicy", "MODE_NODE_SANDBOXED"],
      ["executionModePolicyTest", "phase7StateMachineDefinesAllModePolicies"],
      ["executionModePolicyTest", "unsupportedModesReturnStablePolicyMetadata"],
    ],
  },
  {
    id: "s10_30_interactive_long_running_runtime",
    status: "live_provider",
    required: [
      ["scriptService", "startInteractiveLongRunning"],
      ["scriptService", "stopInteractiveLongRunning"],
      ["statusStore", "STOP_ACTION_NAME"],
      ["statusStore", "restartPolicy"],
      ["statusStore", "automaticRestart"],
      ["interactiveTest", "startInteractiveLongRunning"],
      ["interactiveTest", "STATE_RUNNING"],
      ["interactiveTest", "STATE_STOPPED"],
    ],
  },
  {
    id: "s10_31_quota_heartbeat_watchdog",
    status: "implemented",
    required: [
      ["runtimeBudgetPolicy", "heartbeatSnapshot"],
      ["runtimeBudgetPolicy", "maxTimers"],
      ["runtimeBudgetPolicy", "maxPendingBridgeCalls"],
      ["runtimeBudgetPolicy", "maxImageHandles"],
      ["runtimeBudgetPolicy", "maxWebSocketConnections"],
      ["runtimeBudgetPolicy", "maxNetworkRequests"],
      ["runtimeBudgetPolicy", "HEARTBEAT_STALE"],
      ["budgetTest", "HEARTBEAT_STALE"],
      ["budgetTest", "providerResourceQuotaRejectsOffendingResourceWithoutLeaking"],
    ],
  },
  {
    id: "s10_32_fixed_process_slot_pool",
    status: "blocked_primary_only",
    required: [
      ["processPoolPolicy", "PRIMARY_SLOT_ID"],
      ["processPoolPolicy", "LONG_RUNNING_SLOT_ID"],
      ["processPoolPolicy", "OVERFLOW_SLOT_ID"],
      ["processPoolPolicy", "DEFAULT_EFFECTIVE_MAX_ACTIVE_SLOTS = 1"],
      ["processPoolPolicy", "MODE_PRIMARY_ONLY"],
      ["processPoolTest", "processPoolPolicyIsDeviceAwareButFailClosedByDefault"],
      ["processPoolTest", "longRunningSlotIsReservedUntilPromotion"],
    ],
    blocker: "reserved slots are declared, but effectiveMaxActiveSlots remains 1 and multiProcessEnabled remains false",
  },
  {
    id: "s10_33_execution_queue",
    status: "implemented",
    required: [
      ["queuePolicy", "MODE_REJECT_WHEN_BUSY"],
      ["queuePolicy", "MODE_ENQUEUE_WHEN_BUSY"],
      ["queuePolicy", "DEFAULT_MAX_QUEUED_EXECUTIONS = 1"],
      ["scriptService", "NodeRuntimeErrorCodes.NODE_QUEUE_FULL"],
      ["scriptService", "NodeRuntimeErrorCodes.NODE_QUEUE_TIMEOUT"],
      ["lifecycleStressTest", "queuedExecutionTimeoutReturnsStableError"],
      ["lifecycleStressTest", "queue full"],
    ],
  },
  {
    id: "s10_34_scheduled_node_task_runner",
    status: "live_provider",
    required: [
      ["executionModePolicy", "LAUNCH_SURFACE_SCHEDULED_RUNNER"],
      ["executionModePolicy", "never_no_implicit_retry"],
      ["workManagerTest", "androidWorkManagerLiveProviderExecutesPackagedProjectEntryAndPersistsResult"],
      ["workManagerTest", "androidWorkManagerLiveProviderCancelPreventsDelayedExecution"],
    ],
  },
  {
    id: "s10_35_checkpoint_restore",
    status: "implemented",
    required: [
      ["checkpointStore", "METHOD_CHECKPOINT"],
      ["checkpointStore", "METHOD_READ_CHECKPOINT"],
      ["checkpointStore", "METHOD_CLEAR_CHECKPOINT"],
      ["checkpointStore", "MAX_CHECKPOINT_BYTES = 64 * 1024"],
      ["checkpointStore", "automaticRestart"],
      ["checkpointStore", "restartPolicy"],
      ["interactiveTest", "onRestore"],
      ["interactiveTest", "checkpoint"],
    ],
  },
  {
    id: "s10_36_long_running_multi_instance_stress",
    status: "partial_device_gate",
    required: [
      ["buildGradle", "nodeJsLongRunningGateInstrumentationClasses"],
      ["buildGradle", "NodeLongRunningStressInstrumentationTest"],
      ["buildGradle", "NodeBridgeResourceLeakStressInstrumentationTest"],
      ["buildGradle", "NodeEmbeddedMemoryLeakStressInstrumentationTest"],
      ["buildGradle", "\"verifyNodeLongRunningGate\" to nodeJsLongRunningGateInstrumentationClasses"],
      ["phase10Gate", "verifyNodePhase10LongRunningActivation"],
    ],
    blocker: "packaged long-running service mode and two active process slots are still blocked",
  },
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const policyViolations = [];
  const items = CHECKS.map((check) => evaluateCheck(check, files, policyViolations));
  const report = {
    schema: "autojs6-node-phase10-long-running-activation-v1",
    generatedAt: new Date().toISOString(),
    task: "S10-29..S10-36 Long-running, Scheduled, Queue, Checkpoint, And Multi-instance Boundary",
    gate: "verifyNodePhase10LongRunningActivation",
    items,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready_with_recorded_blockers" : "failed",
      interactiveLongRunningPromoted: true,
      scheduledRunnerPromoted: true,
      multiInstancePromoted: false,
      packagedLongRunningPromoted: false,
      automaticRestartEnabled: false,
      safeNodeProfilePreserved: true,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "phase10-long-running-activation", report, renderMarkdown);
  console.log("Phase 10 long-running activation decision: " + report.decision.status);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function evaluateCheck(check, files, policyViolations) {
  const evidence = check.required.map(([source, token]) => {
    const file = files[source];
    const matched = Boolean(file && file.text.includes(token));
    if (!matched) {
      policyViolations.push(`${check.id} missing token ${token} in ${source}`);
    }
    return {
      source,
      path: file ? file.path : null,
      token,
      matched,
    };
  });
  return {
    id: check.id,
    status: check.status,
    evidence,
    blocker: check.blocker || null,
    ready: evidence.every((entry) => entry.matched),
  };
}

function loadFiles(repoRoot) {
  return {
    buildGradle: read(repoRoot, "app/build.gradle.kts"),
    phase10Gate: read(repoRoot, "app/node-phase10-gate.gradle.kts"),
    executionModePolicy: read(repoRoot, "app/src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt"),
    runtimeBudgetPolicy: read(repoRoot, "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeBudgetPolicy.kt"),
    checkpointStore: read(repoRoot, "app/src/main/java/org/autojs/autojs/engine/NodeLifecycleCheckpointStore.kt"),
    statusStore: read(repoRoot, "app/src/main/java/org/autojs/autojs/engine/NodeInteractiveLongRunningStatusStore.kt"),
    queuePolicy: read(repoRoot, "app/src/main/java/org/autojs/autojs/engine/NodeExecutionQueuePolicy.kt"),
    processPoolPolicy: read(repoRoot, "app/src/main/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicy.kt"),
    scriptService: read(repoRoot, "app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt"),
    executionModePolicyTest: read(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeExecutionModePolicyInstrumentationTest.kt"),
    interactiveTest: read(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeInteractiveLongRunningInstrumentationTest.kt"),
    longRunningStressTest: read(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeLongRunningStressInstrumentationTest.kt"),
    budgetTest: read(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeRuntimeBudgetPolicyInstrumentationTest.kt"),
    lifecycleStressTest: read(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedLifecycleStressInstrumentationTest.kt"),
    processPoolTest: read(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicyInstrumentationTest.kt"),
    workManagerTest: read(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt"),
  };
}

function read(repoRoot, relative) {
  const file = path.join(repoRoot, relative);
  return {
    path: relative,
    text: fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "",
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 10 Long-running Activation",
    "",
    `- Decision: ${report.decision.status}`,
    `- Interactive long-running promoted: ${report.decision.interactiveLongRunningPromoted}`,
    `- Scheduled runner promoted: ${report.decision.scheduledRunnerPromoted}`,
    `- Multi-instance promoted: ${report.decision.multiInstancePromoted}`,
    `- Packaged long-running promoted: ${report.decision.packagedLongRunningPromoted}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "| Item | Status | Ready | Blocker |",
    "| --- | --- | --- | --- |",
  ];
  report.items.forEach((item) => {
    lines.push(`| ${item.id} | ${item.status} | ${item.ready ? "yes" : "no"} | ${item.blocker || ""} |`);
  });
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
      console.log("Usage: node verify-long-running-activation.js [--repo-root dir] [--report-dir dir]");
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
