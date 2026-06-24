#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-second-execution-slot-scheduler";

const FILES = {
  blockerRegistry: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  policy: "app/src/main/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicy.kt",
  service: "app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt",
  bridge: "app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt",
  crashStore: "app/src/main/java/org/autojs/autojs/engine/NodeCrashEvidenceStore.kt",
  processService: "app/src/main/java/org/autojs/autojs/engine/NativeNodeProcessService.kt",
  policyTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicyInstrumentationTest.kt",
  testHarness: "app/src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedTestHarness.kt",
  processPoolDoc: "docs/nodejs/design/PROCESS_POOL.md",
  multiInstanceDoc: "docs/nodejs/design/MULTI_INSTANCE.md",
  p13Report: "docs/nodejs/reports/phase13/P13_06_SECOND_EXECUTION_SLOT_SCHEDULER.md",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const report = buildReport(files);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 13 second execution slot scheduler: " + report.decision.status);
  console.log("Phase 13 second execution slot blockers: " + report.blockers.length);
  console.log("Phase 13 second execution slot policy violations: " + report.policyViolations.length);
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

function buildReport(files) {
  const blockerEntry = blocker(files.blockerRegistry.text, "second_execution_slot");
  const scenarios = [
    scenario("explicit_primary_overflow", "Explicit primary/overflow one-shot execution", [
      token(files.policy, "MODE_EXPLICIT_TWO_SLOT"),
      token(files.policy, "OVERFLOW_SLOT_ID"),
      token(files.service, "requestedSlotId = scheduledRequestForService.processPoolSlot"),
      token(files.service, "serviceClass = processPoolPlan.selectedSlot.serviceClass"),
      token(files.policyTest, "explicitPrimaryAndOverflowSlotsRunInDifferentProcesses"),
    ]),
    scenario("default_scheduler", "Default one-shot scheduler can select primary/overflow", [
      token(files.policy, "STATUS_DEFAULT_SCHEDULER_ENABLED"),
      token(files.policy, "MODE_DEFAULT_TWO_SLOT"),
      token(files.policy, "SLOT_SELECTION_DEFAULT_SCHEDULER"),
      token(files.policy, "defaultSchedulerSelectableSlots"),
      token(files.service, "acquireDefaultProcessPoolSlot"),
      token(files.service, "DEFAULT_PROCESS_POOL_BUSY_SLOT_IDS"),
      token(files.service, "processPoolSlotSource = NodeEmbeddedProcessPoolPolicy.SLOT_SELECTION_DEFAULT_SCHEDULER"),
      token(files.policyTest, "defaultSchedulerRoutesConcurrentOneShotScriptsToDifferentProcesses"),
    ]),
    scenario("slot_busy", "Default scheduler reports pool busy when all default slots are occupied", [
      token(files.service, "defaultProcessPoolBusyResult"),
      token(files.service, "embedded_script.process_pool.scheduler_rejected"),
      token(files.service, "embedded_script.process_pool.active_slot_ids"),
      token(files.service, "EmbeddedNodeProcessPoolBusyError"),
    ]),
    scenario("resource_isolation", "Per-execution bridge/provider/output/crash metadata remains slot scoped", [
      token(files.service, "File(cacheDir, \"embedded-node-bridge/$executionId\")"),
      token(files.service, "AndroidNodeBridgeCapabilityProvider("),
      token(files.bridge, "updatePart(request.processPoolSlot.orEmpty())"),
      token(files.service, "runtimeSlotId = processPoolPlan.selectedSlot.id"),
      token(files.crashStore, "runtimeSlotId"),
      token(files.testHarness, "NodeEmbeddedProcessPoolPolicy.declaredSlots.forEach"),
    ]),
    scenario("slot_crash_fallback", "Per-slot crash fallback is visible but not promoted", [
      token(files.policy, "defaultSchedulerFallbackEnabled = false"),
      token(files.crashStore, "NEXT_LAUNCH_BUNDLE_SCHEMA"),
      token(files.service, "NodeCrashEvidenceStore.begin"),
    ], [
      "Focused fatal native slot-crash injection still does not prove automatic replacement/fallback.",
      "Scheduler fallback remains explicitly false until slot quarantine/replacement semantics are implemented.",
    ]),
    scenario("packaged_scheduler", "Packaged APK default scheduler evidence", [
      token(files.multiInstanceDoc, "Packaged APK Behavior"),
    ], [
      "Packaged APK primary/overflow scheduler smoke remains open.",
      "Package metadata still cannot request or describe multi-slot routing.",
    ]),
  ];
  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-06"), "P13-06 must remain listed in the Phase 13 checklist."),
    check("blocker-open", blockerEntry && blockerEntry.status === "open", "`second_execution_slot` must remain open until packaged/crash/stress evidence lands."),
    check("docs-present", files.p13Report.exists && files.p13Report.text.includes("P13-06"), "P13-06 report must document current progress."),
    check("policy-present", files.policy.exists && files.service.exists && files.bridge.exists, "Runtime policy/service/bridge files must exist."),
    check("ready-scenarios-have-evidence", scenarios.filter((entry) => entry.status === "ready").length >= 4, "At least explicit, default scheduler, busy, and isolation evidence must be ready."),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-second-execution-slot-scheduler-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13SecondExecutionSlotScheduler",
    task: "P13-06",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    blockerRegistry: blockerEntry || {
      id: "second_execution_slot",
      status: "missing",
      currentEvidence: "blocker registry entry is missing",
    },
    scenarios,
    checks,
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : finalBlockerClosable ? "ready" : "partial",
      phase13TaskComplete: finalBlockerClosable,
      finalBlockerClosable,
      defaultSchedulerReady: scenarioById(scenarios, "default_scheduler").status === "ready",
      explicitTwoSlotReady: scenarioById(scenarios, "explicit_primary_overflow").status === "ready",
      slotBusyReady: scenarioById(scenarios, "slot_busy").status === "ready",
      resourceIsolationReady: scenarioById(scenarios, "resource_isolation").status === "ready",
      slotCrashFallbackReady: scenarioById(scenarios, "slot_crash_fallback").status === "ready",
      packagedSchedulerReady: scenarioById(scenarios, "packaged_scheduler").status === "ready",
      safeNodeProfilePreserved: true,
    },
  };
}

function scenario(id, title, checks, plannedRemaining = []) {
  const missing = checks.filter((entry) => !entry.present).map((entry) => `missing evidence token: ${entry.token}`);
  const remaining = [...missing, ...plannedRemaining];
  return {
    id,
    title,
    status: remaining.length === 0 ? "ready" : missing.length > 0 ? "missing_evidence" : "partial",
    checks,
    remaining,
  };
}

function token(file, value) {
  return {
    file: file.relative,
    token: value,
    present: file.exists && file.text.includes(value),
  };
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing" };
}

function blocker(text, id) {
  try {
    const registry = JSON.parse(stripBom(text));
    return (registry.blockers || []).find((entry) => entry.id === id) || null;
  } catch (_) {
    return null;
  }
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Second Execution Slot Scheduler",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Final blocker closable: \`${report.decision.finalBlockerClosable}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    `- Blockers: ${report.blockers.length}`,
    "",
    "## Scenarios",
    "",
    "| Scenario | Status | Remaining |",
    "| --- | --- | --- |",
  ];
  report.scenarios.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | \`${entry.status}\` | ${escapeTable(entry.remaining.join("; ") || "None.")} |`);
  });
  lines.push(
    "",
    "## Checks",
    "",
    "| Check | Result | Message |",
    "| --- | --- | --- |",
  );
  report.checks.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | ${entry.passed ? "passed" : "failed"} | ${escapeTable(entry.message)} |`);
  });
  lines.push("", "## Blockers", "");
  if (report.blockers.length === 0) {
    lines.push("None.");
  } else {
    report.blockers.forEach((entry) => lines.push(`- ${entry}`));
  }
  return lines.join("\n") + "\n";
}

function read(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "") : "";
}

function stripBom(value) {
  return String(value || "").replace(/^\uFEFF/, "");
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-blocked"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-second-execution-slot-scheduler.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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
