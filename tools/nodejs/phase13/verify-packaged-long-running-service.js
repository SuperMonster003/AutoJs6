#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-packaged-long-running-service";

const FILES = {
  blockerRegistry: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  executionModePolicy: "app/src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt",
  statusStore: "app/src/main/java/org/autojs/autojs/engine/NodeInteractiveLongRunningStatusStore.kt",
  embeddedService: "app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt",
  projectRunner: "app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedProjectRunner.kt",
  embeddedEngine: "app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedJavaScriptEngine.kt",
  lifecycleStore: "app/src/main/java/org/autojs/autojs/engine/NodeLifecycleCheckpointStore.kt",
  assetsLauncher: "app/src/main/java/org/autojs/autojs/inrt/launch/AssetsProjectLauncher.kt",
  projectConfig: "app/src/main/java/org/autojs/autojs/project/ProjectConfig.java",
  launchDescriptor: "app/src/main/java/org/autojs/autojs/script/NodeProjectLaunchDescriptor.kt",
  launchSource: "app/src/main/java/org/autojs/autojs/script/NodeProjectJavaScriptFileSource.kt",
  diagnostics: "app/src/main/java/org/autojs/autojs/engine/NodeProjectLaunchDiagnostics.kt",
  interactiveTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeInteractiveLongRunningInstrumentationTest.kt",
  executionModeTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeExecutionModePolicyInstrumentationTest.kt",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  longRunningDoc: "docs/nodejs/design/LONG_RUNNING_SERVICE.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  testingDoc: "docs/nodejs/TESTING.md",
  sampleProject: "sample/nodejs/long-running-service/project.json",
  p13Report: "docs/nodejs/reports/phase13/P13_07_PACKAGED_LONG_RUNNING_SERVICE.md",
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
  console.log("Phase 13 packaged long-running service: " + report.decision.status);
  console.log("Phase 13 packaged long-running blockers: " + report.blockers.length);
  console.log("Phase 13 packaged long-running policy violations: " + report.policyViolations.length);
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
  const blockerEntry = blocker(files.blockerRegistry.text, "packaged_long_running");
  const scenarios = [
    scenario("metadata_policy", "Project and descriptor metadata carry executionMode", [
      token(files.projectConfig, '@SerializedName("executionMode")'),
      token(files.launchDescriptor, 'executionMode = json.optStringOrNull("executionMode")'),
      token(files.launchSource, "nodeProjectExecutionMode"),
      token(files.projectRunner, "executionMode = launchSource.nodeProjectExecutionMode"),
      token(files.projectRunner, "embedded_project_runner.execution_mode"),
      token(files.diagnostics, "executionMode="),
      token(files.packagedSmokeTest, "executionMode: String? = null"),
    ]),
    scenario("packaged_dispatch", "Packaged inrt can route long-running projects to foreground service", [
      token(files.executionModePolicy, "LAUNCH_SURFACE_PACKAGED_LONG_RUNNING"),
      token(files.executionModePolicy, "isInteractiveLongRunningLaunchSurface"),
      token(files.embeddedService, "fun startPackagedLongRunning"),
      token(files.assetsLauncher, "startPackagedLongRunningIfRequested"),
      token(files.assetsLauncher, "withNodeConfigDefaults"),
      token(files.assetsLauncher, "prepareNodeProjectLaunchRequest"),
      token(files.assetsLauncher, "autojs6.inrt.node_project_packaged_long_running.v1"),
      token(files.embeddedEngine, "prepareNodeProjectLaunchRequest"),
      token(files.projectRunner, "buildResolvedProjectLaunchRequest"),
    ]),
    scenario("foreground_lifecycle", "Foreground notification, Stop action, status, and lifecycle checkpoint path remain shared", [
      token(files.embeddedService, "startScriptForeground()"),
      token(files.embeddedService, "ACTION_STOP_INTERACTIVE"),
      token(files.embeddedService, "updateInteractiveForeground"),
      token(files.statusStore, "STOP_ACTION_NAME"),
      token(files.statusStore, "launchSurface"),
      token(files.statusStore, "NodeLifecycleCheckpointStore.statusSnapshot"),
      token(files.interactiveTest, "interactiveRuntimeCheckpointsOnStopAndRestoresOnlyWhenExplicitlyRequested"),
    ]),
    scenario("packaged_lifecycle_smoke", "Packaged long-running mode can checkpoint, read, and clear lifecycle state", [
      token(files.lifecycleStore, "NodeExecutionModePolicy.LAUNCH_SURFACE_PACKAGED_LONG_RUNNING"),
      token(files.lifecycleStore, "explicit interactive or packaged long-running runtime"),
      token(files.lifecycleStore, "CHECKPOINT_MARKER_SCHEMA"),
      token(files.lifecycleStore, "checkpoint.write schema=$CHECKPOINT_MARKER_SCHEMA"),
      token(files.lifecycleStore, "checkpoint.read schema=$CHECKPOINT_MARKER_SCHEMA"),
      token(files.lifecycleStore, "checkpoint.clear schema=$CHECKPOINT_MARKER_SCHEMA"),
      token(files.packagedSmokeTest, "recordPackagedLifecycleEvidence"),
      token(files.packagedSmokeTest, "NodeLifecycleCheckpointStore.CHECKPOINT_MARKER_SCHEMA"),
      token(files.packagedSmokeTest, "packaged.crash_evidence.lifecycle_checkpoint=true"),
      token(files.packagedSmokeTest, "packaged.crash_evidence.lifecycle_launch_surface=${NodeExecutionModePolicy.LAUNCH_SURFACE_PACKAGED_LONG_RUNNING}"),
      token(files.packagedSmokeTest, "packaged.crash_evidence.lifecycle_clear_deleted=true"),
      token(files.p13Report, "Packaged lifecycle smoke"),
      token(files.longRunningDoc, "packaged long-running surfaces"),
      token(files.compatibilityProfile, "interactive or packaged long-running runtime"),
    ]),
    scenario("packaged_stop_smoke", "Packaged long-running Stop marks clean exit and preserves crash recovery", [
      token(files.embeddedService, "NodeCrashEvidenceStore.markNativeProcessExit"),
      token(files.embeddedService, "exitCode = 0"),
      token(files.packagedSmokeTest, "waitForPackagedForegroundNotification"),
      token(files.packagedSmokeTest, "stopPackagedLongRunningViaRunAs"),
      token(files.packagedSmokeTest, "run-as $packageName am startservice --user 0"),
      token(files.packagedSmokeTest, "CRASH_EVIDENCE_STOP_MARKERS"),
      token(files.packagedSmokeTest, "NodeCrashEvidenceStore.STATE_CLEAN_EXIT"),
      token(files.packagedSmokeTest, "Clean Stop action should not be collected as next-launch crash evidence"),
      token(files.packagedSmokeTest, "waitForPackagedForegroundNotificationRemoved"),
      token(files.p13Report, "verifies the clean Stop is not collected"),
    ]),
    scenario("fail_closed", "Non-packaged direct script launches still reject service mode", [
      token(files.executionModePolicy, "supportedLaunchSurfaces = setOf("),
      token(files.executionModePolicy, "LAUNCH_SURFACE_SCRIPT"),
      token(files.executionModePolicy, "direct script launches remain one-shot"),
      token(files.executionModeTest, "assertFalse(NodeExecutionModePolicy.isSupported(NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING))"),
      token(files.executionModeTest, "LAUNCH_SURFACE_PACKAGED_LONG_RUNNING"),
    ]),
    scenario("sample_docs", "Sample and docs advertise partial packaged opt-in without closing final evidence", [
      token(files.sampleProject, '"executionMode": "interactive_long_running"'),
      token(files.sampleProject, "partial opt-in"),
      token(files.longRunningDoc, "packaged long-running"),
      token(files.compatibilityProfile, "packaged long-running"),
      token(files.testingDoc, "Packaged service mode"),
      token(files.p13Report, "P13-07"),
    ]),
    scenario("device_evidence_remaining", "Final packaged device evidence remains open", [
      token(files.p13Report, "Status: partial"),
      token(files.p13Report, "Open Before Closure"),
      token(files.packagedSmokeTest, "installAndRunCrashEvidenceEmbeddedNodePackagedApk"),
      token(files.packagedSmokeTest, "LAUNCH_SURFACE_PACKAGED_LONG_RUNNING"),
      token(files.packagedSmokeTest, "marker.collect schema=autojs6-node-phase13-next-launch-crash-bundle-v1"),
    ], [
      "Final packaged foreground/device matrix evidence is still required: task-removal cleanup, Android API/permission/background-start denial, packaged metadata/disclosure denial paths, and quota/resource stress.",
    ]),
  ];
  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-07"), "P13-07 must remain listed in the Phase 13 checklist."),
    check("blocker-open", blockerEntry && blockerEntry.status === "open", "`packaged_long_running` must remain open until device evidence lands."),
    check("docs-present", files.p13Report.exists && files.p13Report.text.includes("P13-07"), "P13-07 report must document current progress."),
    check("packaged-surface-supported", files.executionModePolicy.text.includes("LAUNCH_SURFACE_PACKAGED_LONG_RUNNING"), "Packaged long-running launch surface must be defined."),
    check("inrt-dispatch-present", files.assetsLauncher.text.includes("startPackagedLongRunningIfRequested"), "Packaged inrt launcher must dispatch long-running requests."),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-packaged-long-running-service-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13PackagedLongRunningService",
    task: "P13-07",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    blockerRegistry: blockerEntry || {
      id: "packaged_long_running",
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
      metadataPolicyReady: scenarioById(scenarios, "metadata_policy").status === "ready",
      packagedDispatchReady: scenarioById(scenarios, "packaged_dispatch").status === "ready",
      foregroundLifecycleShared: scenarioById(scenarios, "foreground_lifecycle").status === "ready",
      packagedLifecycleSmokeReady: scenarioById(scenarios, "packaged_lifecycle_smoke").status === "ready",
      packagedStopSmokeReady: scenarioById(scenarios, "packaged_stop_smoke").status === "ready",
      failClosedReady: scenarioById(scenarios, "fail_closed").status === "ready",
      deviceEvidenceReady: scenarioById(scenarios, "device_evidence_remaining").status === "ready",
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
    "# Phase 13 Packaged Long-Running Service",
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
  lines.push("", "## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
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
      console.log("Usage: node verify-packaged-long-running-service.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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
