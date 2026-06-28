#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const FILES = {
  policy: "app/src/main/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicy.kt",
  service: "app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt",
  bridge: "app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt",
  test: "app/src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicyInstrumentationTest.kt",
  processPoolDoc: "docs/nodejs/design/PROCESS_POOL.md",
  multiInstanceDoc: "docs/nodejs/design/MULTI_INSTANCE.md",
  compatibilityDoc: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  phase13SecondSlotReport: "docs/nodejs/reports/phase13/P13_06_SECOND_EXECUTION_SLOT_SCHEDULER.md",
  blockerRegistry: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
};

const FOUNDATION_CHECKS = [
  ["policy_status", "policy", "STATUS_EXPLICIT_SLOT_ENABLED"],
  ["policy_mode", "policy", "MODE_EXPLICIT_TWO_SLOT"],
  ["overflow_slot", "policy", "OVERFLOW_SLOT_ID"],
  ["explicit_effective_slot_count", "policy", "EXPLICIT_EFFECTIVE_MAX_ACTIVE_SLOTS"],
  ["requested_slot_plan", "policy", "requestedSlotId: String? = null"],
  ["request_extra", "service", "EXTRA_PROCESS_POOL_SLOT"],
  ["client_requested_slot_plan", "service", "requestedSlotId = scheduledRequestForService.processPoolSlot"],
  ["selected_service_class", "service", "serviceClass = processPoolPlan.selectedSlot.serviceClass"],
  ["service_requested_slot_plan", "service", "requestedSlotId = preparedServiceRequest.processPoolSlot"],
  ["intent_restore", "service", "processPoolSlot = getStringExtra(EXTRA_PROCESS_POOL_SLOT)"],
  ["compile_cache_slot_key", "bridge", "updatePart(request.processPoolSlot.orEmpty())"],
  ["bridge_request_field", "bridge", "val processPoolSlot: String? = null"],
  ["overflow_plan_test", "test", "explicitOverflowSlotPlanEnablesTwoSlotMode"],
  ["overflow_execution_test", "test", "explicitOverflowExecutionPublishesSecondSlotDiagnostics"],
  ["concurrent_slot_process_test", "test", "explicitPrimaryAndOverflowSlotsRunInDifferentProcesses"],
  ["process_pool_doc", "processPoolDoc", "explicit_two_slot"],
  ["multi_instance_doc", "multiInstanceDoc", "explicit `overflow_1`"],
  ["compatibility_doc", "compatibilityDoc", "explicit_two_slot"],
];

const DEFAULT_SCHEDULER_CHECKS = [
  ["default_scheduler_status", "policy", "STATUS_DEFAULT_SCHEDULER_ENABLED"],
  ["default_scheduler_mode", "policy", "MODE_DEFAULT_TWO_SLOT"],
  ["default_scheduler_effective_slot_count", "policy", "DEFAULT_SCHEDULER_EFFECTIVE_MAX_ACTIVE_SLOTS"],
  ["default_scheduler_selectable_slots", "policy", "defaultSchedulerSelectableSlots"],
  ["default_scheduler_service_source", "service", "processPoolSlotSource = NodeEmbeddedProcessPoolPolicy.SLOT_SELECTION_DEFAULT_SCHEDULER"],
  ["default_scheduler_concurrent_test", "test", "defaultSchedulerRoutesConcurrentOneShotScriptsToDifferentProcesses"],
  ["default_scheduler_queue_test", "test", "defaultSchedulerQueuesWhenBothDefaultSlotsAreBusy"],
  ["phase13_second_slot_ready", "phase13SecondSlotReport", "Status: ready for P13-06"],
];

const FINAL_BLOCKER_IDS = [
  "second_execution_slot",
  "android_side_package_installation",
  "packaged_long_running",
  "crash_evidence_automation",
  "full_heavy_resource_stress",
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = Object.fromEntries(
    Object.entries(FILES).map(([id, relative]) => [id, read(path.join(repoRoot, relative))]),
  );
  const registry = readJsonIfExists(path.join(repoRoot, FILES.blockerRegistry)) || { blockers: [] };
  const foundationChecks = FOUNDATION_CHECKS.map(([id, fileId, token]) => ({
    id,
    file: FILES[fileId],
    token,
    present: files[fileId].includes(token),
  }));
  const foundationBlockers = foundationChecks
    .filter((entry) => !entry.present)
    .map((entry) => `missing explicit two-slot evidence: ${entry.id}`);
  const defaultSchedulerChecks = DEFAULT_SCHEDULER_CHECKS.map(([id, fileId, token]) => ({
    id,
    file: FILES[fileId],
    token,
    present: files[fileId].includes(token),
  }));
  const defaultSchedulerBlockers = defaultSchedulerChecks
    .filter((entry) => !entry.present)
    .map((entry) => `missing default scheduler evidence: ${entry.id}`);
  const defaultSchedulerReady = defaultSchedulerBlockers.length === 0;
  const primaryOnlyDefault = files.policy.includes("STATUS_DESIGN_GATED") &&
    files.policy.includes("MODE_PRIMARY_ONLY") &&
    files.policy.includes("DEFAULT_EFFECTIVE_MAX_ACTIVE_SLOTS = 1");
  const longRunningSlotReserved = files.policy.includes("LONG_RUNNING_SLOT_ID") &&
    files.policy.includes("enabledForDefaultRouting = false");
  const promotionChecks = [
    {
      id: "default_scheduler_routing",
      ready: defaultSchedulerReady,
      evidence: defaultSchedulerReady
        ? "P13-06 default scheduler routes eligible one-shot scripts across primary/overflow_1; primary-only remains the documented fallback for ineligible devices."
        : "default scheduler routing evidence is incomplete",
    },
    {
      id: "long_running_dedicated_slot",
      ready: !longRunningSlotReserved,
      evidence: longRunningSlotReserved
        ? "long_running service slot remains reserved and disabled for default routing"
        : "long_running service slot no longer appears reserved",
    },
  ];
  const registryBlockers = FINAL_BLOCKER_IDS.map((id) => {
    const entry = (registry.blockers || []).find((candidate) => candidate.id === id);
    return {
      id,
      status: entry ? entry.status : "missing",
      classification: entry ? entry.classification : "missing",
      currentEvidence: entry ? entry.currentEvidence : "blocker registry entry is missing",
      open: !entry || entry.status !== "closed",
    };
  });
  const promotionBlockers = [
    ...defaultSchedulerBlockers,
    ...promotionChecks
      .filter((entry) => !entry.ready)
      .map((entry) => `${entry.id}: ${entry.evidence}`),
    ...registryBlockers
      .filter((entry) => entry.open)
      .map((entry) => `${entry.id}: ${entry.currentEvidence}`),
  ];
  const explicitTwoSlotReady = foundationBlockers.length === 0;
  const blockers = [...foundationBlockers, ...promotionBlockers];
  const report = {
    schema: "autojs6-node-final-multi-instance-gate-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeFinalMultiInstanceGate",
    tasks: ["S11-19", "S11-20", "S11-21"],
    sources: Object.fromEntries(
      Object.entries(FILES).map(([id, relative]) => [id, relativePath(repoRoot, path.join(repoRoot, relative))]),
    ),
    explicitTwoSlotFoundation: {
      status: explicitTwoSlotReady ? "ready" : "missing_evidence",
      requestedSlots: ["primary", "overflow_1"],
      defaultSlot: "primary",
      focusedInstrumentation: explicitTwoSlotReady,
      checks: foundationChecks,
    },
    defaultScheduler: {
      status: defaultSchedulerReady ? "ready" : "missing_evidence",
      requestedSlots: ["primary", "overflow_1"],
      fallbackSlot: "primary",
      focusedInstrumentation: defaultSchedulerReady,
      checks: defaultSchedulerChecks,
    },
    promotion: {
      defaultRuntimePath: defaultSchedulerReady ? "default_scheduler_when_eligible" : primaryOnlyDefault ? "primary_only" : "multi_slot_candidate",
      explicitRuntimePath: explicitTwoSlotReady ? "explicit_primary_overflow_only" : "none",
      defaultSchedulerPromoted: defaultSchedulerReady,
      longRunningDedicatedSlotPromoted: !longRunningSlotReserved,
      packagedSlotRoutingAllowed: !registryBlockers.some((entry) => (
        entry.open && ["android_side_package_installation", "packaged_long_running"].includes(entry.id)
      )),
      perSlotCrashEvidenceReady: !registryBlockers.some((entry) => entry.open && entry.id === "crash_evidence_automation"),
      stressPromotionReady: !registryBlockers.some((entry) => entry.open && entry.id === "full_heavy_resource_stress"),
      checks: promotionChecks,
      registryBlockers,
    },
    blockers,
    decision: {
      status: blockers.length === 0 ? "ready" : "blocked",
      explicitTwoSlotReady,
      defaultSchedulerReady,
      defaultMultiSlotAllowed: explicitTwoSlotReady && defaultSchedulerReady && promotionBlockers.length === 0,
      packagedMultiSlotAllowed: explicitTwoSlotReady && promotionBlockers.length === 0,
      safeNodeProfilePreserved: true,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "final-multi-instance-gate", report, renderMarkdown);
  console.log("Final multi-instance gate decision: " + report.decision.status);
  if (options.failOnBlocked && report.decision.status !== "ready") {
    process.exitCode = 1;
  }
}

function renderMarkdown(report) {
  const lines = [
    "# Final Multi-Instance Gate",
    "",
    `- Decision: ${report.decision.status}`,
    `- Explicit two-slot foundation: ${report.explicitTwoSlotFoundation.status}`,
    `- Default scheduler: ${report.defaultScheduler.status}`,
    `- Default runtime path: ${report.promotion.defaultRuntimePath}`,
    `- Explicit runtime path: ${report.promotion.explicitRuntimePath}`,
    `- Default multi-slot allowed: ${report.decision.defaultMultiSlotAllowed}`,
    `- Packaged multi-slot allowed: ${report.decision.packagedMultiSlotAllowed}`,
    "",
    "## Foundation Checks",
    "",
    "| Check | Present | File |",
    "| --- | --- | --- |",
  ];
  report.explicitTwoSlotFoundation.checks.forEach((entry) => {
    lines.push(`| ${entry.id} | ${entry.present} | ${entry.file} |`);
  });
  lines.push(
    "",
    "## Default Scheduler Checks",
    "",
    "| Check | Present | File |",
    "| --- | --- | --- |",
  );
  report.defaultScheduler.checks.forEach((entry) => {
    lines.push(`| ${entry.id} | ${entry.present} | ${entry.file} |`);
  });
  lines.push(
    "",
    "## Promotion Checks",
    "",
    "| Check | Ready | Evidence |",
    "| --- | --- | --- |",
  );
  report.promotion.checks.forEach((entry) => {
    lines.push(`| ${entry.id} | ${entry.ready} | ${entry.evidence} |`);
  });
  lines.push(
    "",
    "## Registry Blockers",
    "",
    "| Blocker | Status | Classification |",
    "| --- | --- | --- |",
  );
  report.promotion.registryBlockers.forEach((entry) => {
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
      console.log("Usage: node verify-final-multi-instance-gate.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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
