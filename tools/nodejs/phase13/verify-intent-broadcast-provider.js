#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-intent-broadcast-provider";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_22_INTENT_BROADCAST_PROVIDER.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  workManagerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt",
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  tasksRhino: "app/src/main/java/org/autojs/autojs/runtime/api/augment/tasks/Tasks.kt",
  intentTask: "app/src/main/java/org/autojs/autojs/timing/IntentTask.java",
  intentTaskDatabase: "app/src/main/java/org/autojs/autojs/storage/database/IntentTaskDatabase.java",
  timedTaskManager: "app/src/main/java/org/autojs/autojs/timing/TimedTaskManager.kt",
  baseBroadcastReceiver: "app/src/main/java/org/autojs/autojs/external/receiver/BaseBroadcastReceiver.java",
  staticBroadcastReceiver: "app/src/main/java/org/autojs/autojs/external/receiver/StaticBroadcastReceiver.java",
  dynamicBroadcastReceivers: "app/src/main/java/org/autojs/autojs/external/receiver/DynamicBroadcastReceivers.kt",
  broadcastTasksDoc: "docs/nodejs/design/BROADCAST_TASKS.md",
  taskIntentDecisionDoc: "docs/nodejs/design/TASKS_INTENT_BROADCAST_COMPATIBILITY_DECISION.md",
  taskMigrationDoc: "docs/nodejs/design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md",
  workManagerDoc: "docs/nodejs/design/WORK_MANAGER.md",
  phase11Tasks: "docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  rhinoCompatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typeSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
  releaseChecklist: "docs/nodejs/RELEASE_CHECKLIST.md",
  gradleGate: "app/node-phase13-gate.gradle.kts",
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
  console.log("Phase 13 intent/broadcast provider: " + report.decision.status);
  console.log("Phase 13 intent/broadcast provider blockers: " + report.blockers.length);
  console.log("Phase 13 intent/broadcast provider policy violations: " + report.policyViolations.length);
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
  const proSurface = surface(files.surfaceManifest.text, "pro.tasks_app_shell_media_utils");
  const scenarios = [
    scenario("compat_intent_rejection_boundary", "compat.tasks keeps IntentTask methods rejected before bridge dispatch", [
      token(files.nodeBridgeSources, "function __autojs6_rhino_compat_tasks_reject_intent(apiName)"),
      token(files.nodeBridgeSources, "P12-18 keeps IntentTask and broadcast-triggered task compatibility unpromoted"),
      token(files.nodeBridgeSources, "addIntentTask: function(options)"),
      token(files.nodeBridgeSources, "queryIntentTasks: function(options)"),
      token(files.nodeBridgeSources, "getIntentTask: function(taskId, options)"),
      token(files.nodeBridgeSources, "removeIntentTask: function(taskId, options)"),
      token(files.workManagerTest, "compatAddIntentCode"),
      token(files.workManagerTest, "compat.tasks.rejected="),
      token(files.conformanceTest, "compat.tasks rejected stubs must not dispatch bridge requests"),
      token(files.conformanceTest, "compat.tasks.addIntentTask"),
      token(files.rhinoCompatTypes, "addIntentTask(options?: unknown): Promise<never>;"),
      token(files.typeSmoke, "const compatIntentAddDenied: Promise<never>"),
      token(files.phase11Tasks, "IntentTask"),
    ], [
      "Replace P12-18 stable rejection stubs only after a dedicated JSON-safe broadcast provider exists.",
      "Decide whether Rhino compat names remain Promise-only wrappers or a Pro-profile `tasks` provider owns them.",
    ]),
    scenario("android_receiver_substrate", "Android/Rhino IntentTask database and receiver stack exist but stay outside Node", [
      token(files.tasksRhino, "fun addIntentTask"),
      token(files.tasksRhino, "IntentTask().apply"),
      token(files.tasksRhino, "fun queryIntentTasks"),
      token(files.intentTask, "public class IntentTask extends BaseModel"),
      token(files.intentTask, "public IntentFilter getIntentFilter()"),
      token(files.intentTaskDatabase, "CREATE TABLE `\" + IntentTask.TABLE + \"`"),
      token(files.intentTaskDatabase, "`local` INT"),
      token(files.timedTaskManager, "val allIntentTasksAsList: List<IntentTask>"),
      token(files.timedTaskManager, "fun getIntentTaskOfAction(action: String?)"),
      token(files.timedTaskManager, "fun addTaskSync(intentTask: IntentTask)"),
      token(files.timedTaskManager, "app.dynamicBroadcastReceivers.registerIntent(intentTask)"),
      token(files.baseBroadcastReceiver, "ScriptIntents.prepareLaunch"),
      token(files.baseBroadcastReceiver, "Scripts.executeLaunch"),
      token(files.staticBroadcastReceiver, "android.intent.action.BOOT_COMPLETED"),
      token(files.staticBroadcastReceiver, "PACKAGE_ACTIONS"),
      token(files.dynamicBroadcastReceivers, "fun registerIntent(task: IntentTask)"),
      token(files.dynamicBroadcastReceivers, "context.registerReceiver(mDefaultActionReceiver"),
      token(files.dynamicBroadcastReceivers, "InAppEventBus.streamOf(action)"),
      token(files.dynamicBroadcastReceivers, "fun unregisterAll()"),
    ], [
      "Define ownership and migration between legacy Rhino IntentTask rows, Node descriptors, and packaged app task records.",
      "Add JSON snapshots for query/get without exposing raw IntentTask or receiver handles.",
    ]),
    scenario("security_packaged_manifest_boundary", "Receiver authority, manifest mutation, and raw broadcast objects remain blocked", [
      token(files.providerRegistry, '"phase13IntentBroadcastPromotion" to "P13-22 partial IntentTask/broadcast task provider evidence gate"'),
      token(files.providerRegistry, '"blockedBroadcastTriggers" to "boot,user-unlocked,package-event,startup,exact-alarm,custom-broadcast,exported-receiver"'),
      token(files.providerRegistry, '"broadcastTaskPromotion" to "blocked_until_capability_split_receiver_authority_packaged_manifest_trigger_payload_install_trigger_remove_evidence"'),
      token(files.providerRegistry, '"rawBroadcastObjectPolicy" to "no raw BroadcastReceiver, Intent, IntentFilter, PendingIntent, Bundle, Uri, PackageManager, signature, icon, or package-list object crosses the bridge"'),
      token(files.permissionManifest, 'const val WORK_MANAGER = "work_manager"'),
      token(files.taskMigrationDoc, "No new canonical capability is added."),
      token(files.taskIntentDecisionDoc, "P13-22 reopens this area only as a partial Pro-profile evidence gate"),
      token(files.broadcastTasksDoc, "No JavaScript API is implemented yet."),
      token(files.broadcastTasksDoc, "No packaged APK should gain boot or package receivers merely because it bundles `libnode.so`."),
      token(files.workManagerDoc, "Detailed boot, package-event, and custom broadcast policy is tracked in `docs/nodejs/design/BROADCAST_TASKS.md`."),
      token(files.releaseChecklist, "packaged scheduled/background task receivers and permissions are absent unless the project explicitly opts in"),
      token(files.securityModel, "P13-22 records IntentTask/broadcast task provider promotion as partial"),
    ], [
      "Add capability split, manifest synthesis, exported/non-exported receiver policy, and user-visible consent before promotion.",
      "Add install/trigger/remove packaged APK smoke before accepting packaged receiver metadata.",
    ]),
    scenario("docs_gate", "Phase 13 docs and Gradle gate record P13-22 as partial", [
      token(files.p13Report, "Status: partial"),
      token(files.phase13Tasks, "P13-22 partial IntentTask/broadcast provider gate"),
      token(files.gradleGate, "verifyNodePhase13IntentBroadcastProvider"),
      token(files.testingDoc, "verifyNodePhase13IntentBroadcastProvider"),
      token(files.ciGatesDoc, "verifyNodePhase13IntentBroadcastProvider"),
      token(files.compatibilityProfile, "P13-22 records the current IntentTask/broadcast task provider state"),
      token(files.proComparison, "P13-22 records IntentTask/broadcast provider promotion as partial"),
      token(files.proAutoxNotes, "P13-22 records IntentTask/broadcast provider promotion as partial"),
      token(files.capabilityMatrix, "P13-22 IntentTask/broadcast provider"),
      token(files.typesReadme, "P13-22 records the partial IntentTask/broadcast provider gate"),
      token(files.surfaceManifestDoc, "phase13-intent-broadcast-provider"),
    ], [
      "Keep P13-22 checklist open until controlled receiver registration, trigger payloads, and packaged trigger/remove evidence land.",
    ]),
  ];
  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-22"), "P13-22 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-22 report must exist."),
    check("surface-present", Boolean(proSurface), "`pro.tasks_app_shell_media_utils` surface must exist."),
    check(
      "surface-partial",
      proSurface && ["debug_poc", "provider_poc", "design_locked"].includes(proSurface.status),
      "`pro.tasks_app_shell_media_utils` must remain partial/poc after P13-22 evidence lands.",
    ),
    check(
      "surface-not-promoted",
      proSurface && !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.tasks_app_shell_media_utils` must not be promoted by the P13-22 partial gate.",
    ),
    check(
      "surface-evidence",
      proSurface && (proSurface.evidence || []).some((entry) => entry.includes("phase13-intent-broadcast-provider")),
      "`pro.tasks_app_shell_media_utils` surface must reference P13-22 evidence.",
    ),
    check(
      "tasks-broadcast-module-not-exposed",
      !files.protocol.text.includes("registerBroadcastTask") &&
        !files.nodeBridgeSources.text.includes('if (name === "tasks.broadcast")') &&
        !files.nodeBridgeSources.text.includes("__autojs6_limited_tasks_broadcast"),
      "P13-22 must not expose tasks.broadcast or registerBroadcastTask before a dedicated provider lands.",
    ),
    check(
      "future-broadcast-capabilities-not-accepted",
      !files.permissionManifest.text.includes("TASKS_BROADCAST") &&
        !files.permissionManifest.text.includes('const val TASKS = "tasks"') &&
        !files.permissionManifest.text.includes('"tasks.broadcast"'),
      "P13-22 must not accept future task/broadcast capabilities before provider policy exists.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-intent-broadcast-provider-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13IntentBroadcastProvider",
    task: "P13-22",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: proSurface || {
      id: "pro.tasks_app_shell_media_utils",
      status: "missing",
      evidence: [],
    },
    scenarios,
    checks,
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : finalBlockerClosable ? "ready" : "partial",
      phase13TaskComplete: finalBlockerClosable,
      finalBlockerClosable,
      compatIntentRejectionReady: scenarioById(scenarios, "compat_intent_rejection_boundary").status !== "missing_evidence",
      androidReceiverSubstrateReady: scenarioById(scenarios, "android_receiver_substrate").status !== "missing_evidence",
      receiverProviderReady: false,
      packagedIntentBroadcastReady: false,
      rawBroadcastObjectProfileReady: false,
      proSurfacePromoted: false,
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

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing" };
}

function surface(text, id) {
  try {
    const manifest = JSON.parse(text);
    return (manifest.surfaces || []).find((entry) => entry.id === id) || null;
  } catch (error) {
    return null;
  }
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8");
  } catch (error) {
    return "";
  }
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    } else if (arg === "--fail-on-blocked") {
      options.failOnBlocked = true;
    } else if (arg === "--help" || arg === "-h") {
      console.log("Usage: verify-intent-broadcast-provider.js [--repo-root DIR] [--report-dir DIR] [--fail-on-blocked]");
      process.exit(0);
    } else {
      throw new Error("Unknown argument: " + arg);
    }
  }
  return options;
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Intent Broadcast Provider",
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
  for (const scenario of report.scenarios) {
    lines.push(`| \`${scenario.id}\` | \`${scenario.status}\` | ${scenario.remaining.length === 0 ? "none" : scenario.remaining.join("<br>")} |`);
  }
  lines.push("", "## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
  for (const check of report.checks) {
    lines.push(`| \`${check.id}\` | ${check.passed ? "passed" : "failed"} | ${check.message} |`);
  }
  if (report.policyViolations.length > 0) {
    lines.push("", "## Policy Violations", "");
    for (const violation of report.policyViolations) {
      lines.push(`- ${violation}`);
    }
  }
  lines.push("", "## Blockers", "");
  for (const blocker of report.blockers) {
    lines.push(`- ${blocker}`);
  }
  lines.push("");
  return lines.join("\n");
}

main();
