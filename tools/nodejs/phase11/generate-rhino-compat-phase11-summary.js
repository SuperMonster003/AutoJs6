#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase11a-summary";
const STATIC_SUMMARY = "docs/nodejs/reports/phase11/PHASE11A_SUMMARY.md";
const TASK_PLAN = "docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md";
const MATRIX_DOC = "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md";

const REQUIRED_REPORTS = Object.freeze([
  ["inventory", "rhino-compat-inventory-verification.json"],
  ["loader", "rhino-compat-loader-verification.json"],
  ["basic_facade", "rhino-compat-basic-facade-verification.json"],
  ["files_facade", "rhino-compat-files-facade-verification.json"],
  ["pure_utilities", "rhino-compat-pure-utilities-verification.json"],
  ["text_utilities", "rhino-compat-text-utilities-verification.json"],
  ["storage_sqlite", "rhino-compat-storage-sqlite-verification.json"],
  ["console_timers_error", "rhino-compat-console-timers-error-verification.json"],
  ["selector_dsl_design", "rhino-selector-dsl-handle-design-verification.json"],
  ["selector_accessibility", "rhino-compat-selector-accessibility-verification.json"],
  ["app_device_shell", "rhino-compat-app-device-shell-verification.json"],
  ["images_facade", "rhino-compat-images-facade-verification.json"],
  ["screenshot_permission", "rhino-compat-screenshot-permission-verification.json"],
  ["ocr_barcode_strategy", "rhino-compat-ocr-barcode-strategy-verification.json"],
  ["events_input_observer_design", "rhino-compat-events-input-observer-design-verification.json"],
  ["tasks_work_manager_design", "rhino-compat-tasks-work-manager-design-verification.json"],
  ["ui_design", "rhino-compat-ui-design-verification.json"],
  ["floaty_design", "rhino-compat-floaty-design-verification.json"],
  ["threads_policy", "rhino-compat-threads-policy-verification.json"],
  ["json_only_shim_design", "rhino-json-only-shim-design-verification.json"],
  ["examples", "rhino-compat-examples-verification.json"],
  ["dual_engine_tests", "rhino-dual-engine-compat-tests-verification.json"],
  ["packaged_smoke", "rhino-compat-packaged-smoke-verification.json"],
  ["resource_cleanup_security", "rhino-compat-resource-cleanup-security-verification.json"],
]);

const SUPPORTED_AREAS = Object.freeze([
  {
    id: "low_risk_compat_facades",
    status: "host_verified",
    surfaces: ["toast", "sleep", "clipboard", "files", "base64", "colors", "formatter", "converter", "mime", "nanoid", "storage", "sqlite"],
    migration: "Use require(\"autojs6:compat\") or explicit compat.install({ globals: true }); Promise-returning APIs replace Rhino blocking calls.",
  },
  {
    id: "automation_core_facades",
    status: "host_verified",
    surfaces: ["selector/accessibility", "app", "device", "shell", "images", "screenshot", "ocr"],
    migration: "Use JSON-safe descriptors and opaque handles; recycle images and stop screen capture explicitly.",
  },
  {
    id: "release_evidence",
    status: "host_verified",
    surfaces: ["examples", "dual-engine corpus", "packaged APK smoke", "resource cleanup/security"],
    migration: "Keep adding migration examples before promoting more Rhino globals.",
  },
]);

const BLOCKED_DECISIONS = Object.freeze([
  {
    id: "events_input_observer",
    status: "design_gated",
    reason: "Raw Rhino events and input observers bind native callbacks and listener lifecycle to Rhino/Android threading.",
    nextStep: "Design and test a controlled input_observer provider with bounded queues, permissions, and zero-active-resource destroy checks.",
  },
  {
    id: "ui_raw_threading",
    status: "blocked_by_design",
    reason: "Rhino ui.run/ui.post and raw View/NativeView references cannot execute JavaScript on Android main looper inside V8.",
    nextStep: "Promote only JSON descriptor UiHandle patterns; keep raw view lookup and main-thread JS execution blocked.",
  },
  {
    id: "floaty_overlay",
    status: "blocked_by_design",
    reason: "Overlay windows need user-visible permission, foreground disclosure, provider-owned handles, and packaged manifest policy.",
    nextStep: "Create an overlay provider design and cleanup gate before exposing require(\"floaty\") or compat.floaty.",
  },
  {
    id: "tasks_user_database",
    status: "design_gated",
    reason: "Rhino TimedTask/IntentTask objects and user task database semantics do not map to one WorkManager bridge call.",
    nextStep: "Split WorkManager migration from user-task database and broadcast trigger ownership.",
  },
  {
    id: "threads",
    status: "blocked_by_design",
    reason: "Rhino Java threads share mutable runtime objects and synchronous lock patterns that conflict with Safe Node Profile.",
    nextStep: "Use async/AbortController/process slot/work_manager first; define compute worker policy separately.",
  },
  {
    id: "rhino_json_only_shim",
    status: "design_gated",
    reason: "Embedding Rhino inside V8 must not install Rhino globals or leak Java/Rhino objects.",
    nextStep: "Prototype only explicit JSON-safe rhino.run({ source, timeoutMs, args }) with strict lifecycle and security gates.",
  },
  {
    id: "barcode_qrcode",
    status: "provider_gap",
    reason: "Barcode/qrcode compatibility needs a JSON-safe provider contract instead of overloading image helpers.",
    nextStep: "Add a barcode provider, result schema, fixtures, and packaged smoke before marking these APIs supported.",
  },
]);

const NEXT_PHASE_TASKS = Object.freeze([
  {
    id: "P12-01",
    title: "Promote events.keys constants or input_observer provider POC",
    priority: "P0",
    dependsOn: ["S11-21", "S11-30"],
    exitGate: "input observer design/runtime gate with zero active observers after destroy",
  },
  {
    id: "P12-02",
    title: "Split UI compat runtime facade from raw Rhino UI non-goals",
    priority: "P0",
    dependsOn: ["S11-23", "S11-30"],
    exitGate: "UiHandle-only migration examples, no ui.run/ui.post/raw View leakage",
  },
  {
    id: "P12-03",
    title: "Define floaty/overlay provider or keep permanent non-goal",
    priority: "P0",
    dependsOn: ["S11-24", "S11-30"],
    exitGate: "overlay permission, foreground disclosure, packaged manifest, closeAll/destroy cleanup",
  },
  {
    id: "P12-04",
    title: "Decompose tasks into WorkManager, user-task DB, and broadcast trigger tracks",
    priority: "P1",
    dependsOn: ["S11-22"],
    exitGate: "no raw TimedTask/IntentTask objects; explicit ownership and migration table per subtrack",
  },
  {
    id: "P12-05",
    title: "Decide threads replacement policy after worker/process-slot maturity",
    priority: "P1",
    dependsOn: ["S11-25"],
    exitGate: "documented worker/process policy and examples for common thread patterns",
  },
  {
    id: "P12-06",
    title: "Prototype explicit Rhino JSON-only shim",
    priority: "P1",
    dependsOn: ["S11-26"],
    exitGate: "rhino.run JSON-only POC with no globals, no raw objects, timeout/destroy cleanup",
  },
  {
    id: "P12-07",
    title: "Implement barcode/qrcode JSON-safe provider",
    priority: "P1",
    dependsOn: ["S11-20"],
    exitGate: "barcode result schema, path/handle/capture fixtures, types, packaged smoke",
  },
  {
    id: "P12-08",
    title: "Triage unsupported utility-heavy modules",
    priority: "P2",
    dependsOn: ["S11-07", "S11-31"],
    exitGate: "per-module decision: supported, partial, blocked_by_design, or permanent non-goal",
  },
  {
    id: "P12-09",
    title: "Promote dual-engine corpus from host plan to device instrumentation where useful",
    priority: "P2",
    dependsOn: ["S11-28", "S11-29"],
    exitGate: "device result fields populated for selected supported_async cases",
  },
]);

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));

  const inventory = readJson(path.join(reportDir, "rhino-augment-api-inventory.json"));
  const gap = readJson(path.join(reportDir, "rhino-node-compat-gap.json"));
  const inventoryGate = readJson(path.join(reportDir, "rhino-compat-inventory-verification.json"));
  const reports = REQUIRED_REPORTS.map(([id, file]) => readRequiredReport(reportDir, id, file));
  const staticSummaryText = read(path.join(repoRoot, STATIC_SUMMARY));
  const taskPlanText = read(path.join(repoRoot, TASK_PLAN));
  const matrixText = read(path.join(repoRoot, MATRIX_DOC));
  const taskStatus = phaseTaskStatus(taskPlanText);
  const policyViolations = buildPolicyViolations({
    reports,
    staticSummaryText,
    taskStatus,
    matrixText,
    inventory,
    gap,
  });

  const summary = {
    schema: "autojs6-node-rhino-compat-phase11a-summary-v1",
    generatedAt: new Date().toISOString(),
    phase: "Phase 11A: Rhino Augment Compatibility",
    gate: "generateNodeRhinoCompatPhase11Summary",
    sourceDocs: {
      staticSummary: STATIC_SUMMARY,
      taskPlan: TASK_PLAN,
      matrix: MATRIX_DOC,
    },
    inventory: {
      modules: inventory.totals.moduleCount,
      apiEntries: inventory.totals.apiEntryCount,
      statusRecords: inventory.totals.byStatus,
      verificationStatusRecords: inventoryGate.summary.byStatus,
      unresolvedReceivers: inventoryGate.summary.unresolvedReceiverCount,
    },
    nodeEvidence: {
      bridgeModules: gap.summary.bridgeModuleCount,
      typeModules: gap.summary.typeModuleCount,
      runtimeEvidenceModules: gap.summary.runtimeEvidenceModuleCount,
      rhinoModulesWithBridgeEvidence: gap.summary.rhinoModulesWithBridgeEvidence,
      rhinoModulesWithTypeEvidence: gap.summary.rhinoModulesWithTypeEvidence,
      rhinoModulesWithoutNodeSurface: gap.summary.rhinoModulesWithoutNodeSurface,
    },
    requiredReports: reports.map(({ json, ...entry }) => entry),
    supportedAreas: SUPPORTED_AREAS,
    blockedDecisions: BLOCKED_DECISIONS,
    nextPhaseTasks: NEXT_PHASE_TASKS,
    migrationRules: [
      "No default Rhino globals in Node; use explicit autojs6:compat install when globals are required.",
      "Promise-returning async facades replace Rhino blocking APIs.",
      "All Android/Rhino/Java resources cross the Node boundary as JSON snapshots or opaque handles.",
      "Raw Android View, AccessibilityNodeInfo, Bitmap/Mat, WindowManager, Thread, and Rhino ScriptableObject leakage remains forbidden.",
      "New lifecycle-heavy providers must ship with stop/timeout/close/destroy cleanup evidence and packaged behavior.",
    ],
    taskStatus,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "completed" : "failed",
      phase11aCompleteForNextPlanning: policyViolations.length === 0,
      finalRuntimeParityClaimed: false,
      nextPhaseTaskCount: NEXT_PHASE_TASKS.length,
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(summary, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(summary));
  console.log("Phase 11A Rhino compat summary decision: " + summary.decision.status);
  console.log("Phase 11A Rhino compat required reports: " + reports.length);
  console.log("Phase 11A Rhino compat next-phase tasks: " + NEXT_PHASE_TASKS.length);
  console.log("Phase 11A Rhino compat policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function readRequiredReport(reportDir, id, file) {
  const absolute = path.join(reportDir, file);
  const available = fs.existsSync(absolute);
  const json = available ? readJson(absolute) : null;
  return {
    id,
    file,
    available,
    status: decisionStatus(json),
    policyViolationCount: Array.isArray(json && json.policyViolations) ? json.policyViolations.length : 0,
    json,
  };
}

function decisionStatus(json) {
  if (!json) return "missing";
  if (json.decision && json.decision.status) return json.decision.status;
  return json.status || "unknown";
}

function phaseTaskStatus(taskPlanText) {
  const taskMatches = Array.from(taskPlanText.matchAll(/- \[([ xX])\] S11-(\d{2}):/g));
  const tasks = taskMatches.map((match) => ({
    id: "S11-" + match[2],
    done: match[1].toLowerCase() === "x",
  }));
  const expected = Array.from({ length: 32 }, (_, index) => "S11-" + String(index).padStart(2, "0"));
  const present = new Set(tasks.map((task) => task.id));
  return {
    expected,
    tasks,
    missing: expected.filter((id) => !present.has(id)),
    incomplete: tasks.filter((task) => !task.done).map((task) => task.id),
  };
}

function buildPolicyViolations(input) {
  const violations = [];
  input.reports.forEach((report) => {
    if (!report.available) {
      violations.push("missing required Phase 11A report: " + report.file);
    } else if (report.status !== "passed") {
      violations.push(`${report.id} report status is ${report.status}, expected passed`);
    } else if (report.policyViolationCount > 0) {
      violations.push(`${report.id} report has policy violations`);
    }
  });
  if (input.taskStatus.missing.length > 0) {
    violations.push("task plan is missing tasks: " + input.taskStatus.missing.join(", "));
  }
  if (input.taskStatus.incomplete.length > 0) {
    violations.push("task plan has incomplete tasks: " + input.taskStatus.incomplete.join(", "));
  }
  if (!includesAll(input.staticSummaryText, [
    "Phase 11A",
    "S11-31",
    "73",
    "879",
    "supported_async",
    "blocked_by_design",
    "events",
    "floaty",
    "tasks",
    "threads",
    "Rhino JSON-only shim",
    "Next Phase",
  ])) {
    violations.push("static Phase 11A summary is missing required planning anchors");
  }
  if (!includesAll(input.matrixText, [
    "Rhino modules: 73",
    "Rhino API entries: 879",
    "Modules without Node surface evidence: 35",
  ])) {
    violations.push("generated Rhino compatibility matrix summary is missing expected totals");
  }
  if (input.inventory.totals.moduleCount !== 73 || input.inventory.totals.apiEntryCount !== 879) {
    violations.push("inventory totals changed; refresh PHASE11A_SUMMARY.md before publishing");
  }
  if (input.gap.summary.rhinoModulesWithoutNodeSurface < 1) {
    violations.push("gap report unexpectedly has no remaining Node surface gaps; summary backlog needs review");
  }
  return violations;
}

function renderMarkdown(summary) {
  const lines = [
    "# Phase 11A Rhino Compat Summary Report",
    "",
    `- Decision: \`${summary.decision.status}\``,
    `- Static summary: \`${summary.sourceDocs.staticSummary}\``,
    `- Required reports: ${summary.requiredReports.length}`,
    `- Policy violations: ${summary.policyViolations.length}`,
    `- Rhino modules: ${summary.inventory.modules}`,
    `- Rhino API entries: ${summary.inventory.apiEntries}`,
    `- Modules without Node surface evidence: ${summary.nodeEvidence.rhinoModulesWithoutNodeSurface}`,
    "",
    "## Required Reports",
    "",
    "| Report | Status | Available | Policy Violations |",
    "| --- | --- | --- | ---: |",
  ];
  summary.requiredReports.forEach((report) => {
    lines.push(`| \`${report.id}\` | \`${report.status}\` | ${report.available} | ${report.policyViolationCount} |`);
  });
  lines.push("", "## Supported Areas", "", "| Area | Status | Surfaces | Migration |", "| --- | --- | --- | --- |");
  summary.supportedAreas.forEach((area) => {
    lines.push(`| \`${area.id}\` | \`${area.status}\` | ${area.surfaces.map((item) => `\`${item}\``).join(", ")} | ${escapeTable(area.migration)} |`);
  });
  lines.push("", "## Blocked Decisions", "", "| Decision | Status | Reason | Next Step |", "| --- | --- | --- | --- |");
  summary.blockedDecisions.forEach((decision) => {
    lines.push(`| \`${decision.id}\` | \`${decision.status}\` | ${escapeTable(decision.reason)} | ${escapeTable(decision.nextStep)} |`);
  });
  lines.push("", "## Next Phase", "", "| Task | Priority | Title | Exit Gate |", "| --- | --- | --- | --- |");
  summary.nextPhaseTasks.forEach((task) => {
    lines.push(`| \`${task.id}\` | \`${task.priority}\` | ${escapeTable(task.title)} | ${escapeTable(task.exitGate)} |`);
  });
  lines.push("", "## Policy Violations", "");
  if (summary.policyViolations.length === 0) {
    lines.push("None.");
  } else {
    summary.policyViolations.forEach((violation) => lines.push(`- ${violation}`));
  }
  return lines.join("\n") + "\n";
}

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function read(file) {
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function readJson(file) {
  return JSON.parse(read(file));
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node generate-rhino-compat-phase11-summary.js [--repo-root dir] [--report-dir dir]");
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
