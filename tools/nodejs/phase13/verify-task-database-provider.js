#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-task-database-provider";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_21_TASK_DATABASE_PROVIDER.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  workManagerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt",
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  resourceRegistryTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeResourceRegistryInstrumentationTest.kt",
  timedTaskRhino: "app/src/main/java/org/autojs/autojs/runtime/api/augment/tasks/Tasks.kt",
  timedTaskDatabase: "app/src/main/java/org/autojs/autojs/storage/database/TimedTaskDatabase.java",
  intentTaskDatabase: "app/src/main/java/org/autojs/autojs/storage/database/IntentTaskDatabase.java",
  timedTaskManager: "app/src/main/java/org/autojs/autojs/timing/TimedTaskManager.kt",
  workManagerTypes: "docs/nodejs/types/autojs6-node/work_manager.d.ts",
  rhinoCompatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typeSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  taskDecisionDoc: "docs/nodejs/design/TASKS_DAILY_WEEKLY_USER_DATABASE_DECISION.md",
  taskMigrationDoc: "docs/nodejs/design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md",
  intentDecisionDoc: "docs/nodejs/design/TASKS_INTENT_BROADCAST_COMPATIBILITY_DECISION.md",
  phase11Tasks: "docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
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
  console.log("Phase 13 task database provider: " + report.decision.status);
  console.log("Phase 13 task database provider blockers: " + report.blockers.length);
  console.log("Phase 13 task database provider policy violations: " + report.policyViolations.length);
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
    scenario("work_manager_node_owned_baseline", "Node-owned scheduled work remains live through work_manager", [
      token(files.protocol, 'val workManagerMethods = setOf("scheduleOnce", "schedulePeriodic", "cancel", "list")'),
      token(files.protocol, "private fun dispatchWorkManager"),
      token(files.protocol, "NodeWorkManagerBridgeScheduler.schedule"),
      token(files.providerRegistry, '"phase13TaskDatabasePromotion" to "P13-21 partial daily/weekly/user task database provider evidence gate"'),
      token(files.providerRegistry, '"liveMethods" to "scheduleOnce,schedulePeriodic,cancel,list"'),
      token(files.providerRegistry, '"dailyWeeklyPromotion" to "blocked_until_user_task_database_schema_migration_packaged_policy_and_wall_clock_scheduler_evidence"'),
      token(files.workManagerTypes, 'declare module "work_manager"'),
      token(files.typeSmoke, "const onceId: string = await workManager.scheduleOnce"),
      token(files.workManagerTest, "jsWorkManagerModuleUsesFakeTransportAndSerializesPackagedMetadata"),
      token(files.workManagerTest, "androidWorkManagerLiveProviderExecutesPackagedProjectEntryAndPersistsResult"),
      token(files.resourceRegistryTest, "androidProviderTracksWorkManagerTasksAndCancellation"),
    ], [
      "Define whether daily/weekly wall-clock tasks are a new `tasks` provider, a work_manager extension, or a separate Pro-only module.",
      "Add database-row ownership and id mapping between Node-owned task ids, Rhino timed task ids, and packaged app ids.",
    ]),
    scenario("rhino_compat_tasks_boundary", "compat.tasks keeps one-shot migration live and daily/weekly rejected before dispatch", [
      token(files.nodeBridgeSources, "function __autojs6_rhino_compat_tasks()"),
      token(files.nodeBridgeSources, "P12-17 keeps daily/weekly wall-clock user task database compatibility unpromoted"),
      token(files.nodeBridgeSources, "P12-17 has no JSON patch/update provider for the AutoJs6 user task database"),
      token(files.nodeBridgeSources, "P12-18 keeps IntentTask and broadcast-triggered task compatibility unpromoted"),
      token(files.workManagerTest, "compat.tasks.rejected=ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED|ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED|ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN"),
      token(files.workManagerTest, "requests.length === beforeRejected"),
      token(files.conformanceTest, "compat.tasks rejected stubs must not dispatch bridge requests"),
      token(files.rhinoCompatTypes, "addDailyTask(options?: unknown): Promise<never>;"),
      token(files.rhinoCompatTypes, "queryTimedTasks(options?: RhinoTasksQueryOptions): Promise<readonly workManager.WorkTask[]>;"),
      token(files.typesReadme, "P12-17 adds typed stable rejection stubs for `addDailyTask()`, `addWeeklyTask()`, and `updateTask()`"),
      token(files.phase11Tasks, "addDailyTask"),
      token(files.phase11Tasks, "addWeeklyTask"),
    ], [
      "Promote daily/weekly only after a JSON-safe provider replaces the rejection stubs.",
      "Decide whether compat.tasks should stay Promise-only or gain a Pro-only compatibility mode for task database snapshots.",
    ]),
    scenario("android_task_database_substrate", "Rhino/Android task database exists but raw rows and objects do not cross the Node bridge", [
      token(files.timedTaskRhino, "TimedTask.dailyTask("),
      token(files.timedTaskRhino, "TimedTask.weeklyTask("),
      token(files.timedTaskRhino, "TimedTaskManager.addTaskSync(task)"),
      token(files.timedTaskRhino, "TimedTaskManager.updateTaskSync(task)"),
      token(files.timedTaskDatabase, "CREATE TABLE `\" + TimedTask.TABLE + \"`"),
      token(files.intentTaskDatabase, "CREATE TABLE `\" + IntentTask.TABLE + \"`"),
      token(files.timedTaskManager, "val allTasksAsList: List<TimedTask>"),
      token(files.taskDecisionDoc, "Task ownership: main app vs packaged app ownership"),
      token(files.taskMigrationDoc, "Mapping `addDailyTask()` or `addWeeklyTask()` to `schedulePeriodic()` would silently"),
    ], [
      "Define descriptor versioning, migration snapshots, downgrade behavior, and legacy row visibility.",
      "Add query/update/remove provider operations over frozen JSON task snapshots.",
    ]),
    scenario("security_packaged_raw_boundary", "Task provider promotion remains explicit and raw Android scheduler objects stay blocked", [
      token(files.providerRegistry, '"rawTaskObjectPolicy" to "no raw TimedTask, IntentTask, WorkRequest, PendingIntent, AlarmManager, JobScheduler, WorkManager, BroadcastReceiver, Intent, or database cursor crosses the bridge"'),
      token(files.providerRegistry, '"requireTasksPolicy" to "require(\\"tasks\\"), global tasks, and raw Rhino task objects remain unavailable until a dedicated Pro profile provider lands"'),
      token(files.permissionManifest, 'const val WORK_MANAGER = "work_manager"'),
      token(files.taskMigrationDoc, "No new canonical capability is added."),
      token(files.intentDecisionDoc, "Boot, package-event, exact-alarm, startup, and custom broadcast triggers must"),
      token(files.securityModel, "P13-21 records daily/weekly/user task database promotion as partial"),
      token(files.securityModel, "Raw WorkManager, AlarmManager, JobScheduler, BroadcastReceiver, PendingIntent, Intent, Bundle, Uri, IntentFilter, exact-alarm, boot, package-event, custom broadcast authority, raw `TimedTask`, and raw `IntentTask` remain denied."),
    ], [
      "Design task capabilities, packaged metadata, manifest permission synthesis, user disclosure, and rollback policy.",
      "Create a separate gate before accepting `tasks`, `tasks.intent`, `tasks.boot`, or package-event capabilities.",
    ]),
    scenario("docs_gate", "Phase 13 docs and Gradle gate record P13-21 as partial", [
      token(files.p13Report, "Status: partial"),
      token(files.phase13Tasks, "P13-21 partial task database provider gate"),
      token(files.gradleGate, "verifyNodePhase13TaskDatabaseProvider"),
      token(files.testingDoc, "verifyNodePhase13TaskDatabaseProvider"),
      token(files.ciGatesDoc, "verifyNodePhase13TaskDatabaseProvider"),
      token(files.compatibilityProfile, "P13-21 records the current daily/weekly/user task database provider state"),
      token(files.proComparison, "P13-21 records task database provider promotion as partial"),
      token(files.proAutoxNotes, "P13-21 records task database provider promotion as partial"),
      token(files.capabilityMatrix, "P13-21 task database provider"),
      token(files.typesReadme, "P13-21 records the partial task database provider gate"),
      token(files.surfaceManifestDoc, "phase13-task-database-provider"),
    ], []),
  ];
  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-21"), "P13-21 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-21 report must exist."),
    check("surface-present", Boolean(proSurface), "`pro.tasks_app_shell_media_utils` surface must exist."),
    check(
      "surface-partial",
      proSurface && ["debug_poc", "provider_poc", "design_locked"].includes(proSurface.status),
      "`pro.tasks_app_shell_media_utils` must show partial/poc state after P13-21 evidence lands.",
    ),
    check(
      "surface-not-promoted",
      proSurface && !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.tasks_app_shell_media_utils` must not be promoted by the P13-21 partial gate.",
    ),
    check(
      "surface-evidence",
      proSurface && (proSurface.evidence || []).some((entry) => entry.includes("phase13-task-database-provider")),
      "`pro.tasks_app_shell_media_utils` surface must reference P13-21 evidence.",
    ),
    check(
      "tasks-module-not-exposed",
      !files.protocol.text.includes('const val TASKS = "tasks"') &&
        !files.nodeBridgeSources.text.includes('if (name === "tasks")') &&
        !files.nodeBridgeSources.text.includes('__autojs6_limited_tasks'),
      "P13-21 must not expose require(\"tasks\") before a dedicated provider lands.",
    ),
    check(
      "future-capabilities-not-accepted",
      !files.permissionManifest.text.includes('const val TASKS = "tasks"') &&
        !files.permissionManifest.text.includes('const val TASKS_INTENT'),
      "P13-21 must not accept future task capabilities before provider policy exists.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-task-database-provider-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13TaskDatabaseProvider",
    task: "P13-21",
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
      workManagerNodeOwnedReady: scenarioById(scenarios, "work_manager_node_owned_baseline").status !== "missing_evidence",
      compatTasksBoundaryReady: scenarioById(scenarios, "rhino_compat_tasks_boundary").status !== "missing_evidence",
      androidTaskDatabaseSubstrateReady: scenarioById(scenarios, "android_task_database_substrate").status !== "missing_evidence",
      dailyWeeklyProviderReady: false,
      packagedTaskDatabaseReady: false,
      rawTaskObjectProfileReady: false,
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
      console.log("Usage: node verify-task-database-provider.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Task Database Provider",
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
    lines.push(`| \`${entry.id}\` | \`${entry.status}\` | ${escapeTable(entry.remaining.join("; ") || "none")} |`);
  });
  lines.push("", "## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
  report.checks.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | ${entry.passed ? "passed" : "failed"} | ${escapeTable(entry.message)} |`);
  });
  if (report.policyViolations.length > 0) {
    lines.push("", "## Policy Violations", "");
    report.policyViolations.forEach((entry) => lines.push(`- ${entry}`));
  }
  if (report.blockers.length > 0) {
    lines.push("", "## Blockers", "");
    report.blockers.forEach((entry) => lines.push(`- ${entry}`));
  }
  return lines.join("\n") + "\n";
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

main();
