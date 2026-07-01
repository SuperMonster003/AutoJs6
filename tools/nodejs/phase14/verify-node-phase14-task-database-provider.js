#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-task-database-provider";
const RAW_DENIALS = Object.freeze([
  "TimedTask",
  "IntentTask",
  "TaskScheduler",
  "TimedTaskScheduler",
  "AlarmManager",
  "JobScheduler",
  "WorkManager",
  "WorkRequest",
  "PendingIntent",
  "BroadcastReceiver",
  "Intent",
  "database cursor",
  "mutable row handle",
  "ExecutionConfig",
  "Android Context",
  "Java object",
]);
const OPEN_BLOCKERS = Object.freeze([
  {
    id: "task_database_live_mutation_provider_archive",
    classification: "future_promotion",
    requiredEvidence: "Live provider evidence for add, update, enable, disable, remove, list, and get over JSON descriptors and patches without raw task objects.",
  },
  {
    id: "task_database_ownership_id_mapping_archive",
    classification: "future_promotion",
    requiredEvidence: "Owner and id mapping evidence proving Node ids, legacy TimedTask ids, packaged task ids, project identity, and package identity cannot alias across owners.",
  },
  {
    id: "task_database_schema_migration_rollback_archive",
    classification: "future_promotion",
    requiredEvidence: "Schema version, downgrade, corruption recovery, unknown-version fail-closed, and rollback behavior for task descriptors and legacy row visibility.",
  },
  {
    id: "task_database_wall_clock_scheduler_archive",
    classification: "future_promotion",
    requiredEvidence: "Daily/weekly wall-clock scheduler evidence covering timezone and clock changes, reboot, Doze, exact-alarm permission, WorkManager/JobScheduler fallback, and quota diagnostics.",
  },
  {
    id: "packaged_task_database_mutation_smoke_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged APK add/list/enable-disable/remove smoke proving packaged row ownership, disclosure, process death/relaunch behavior, task removal, and Safe downgrade policy.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_17_TASK_DATABASE_PROVIDER.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/task-database-provider-plan.json",
  planDoc: "docs/nodejs/phase14/PHASE14_TASK_DATABASE_PROVIDER.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  p13Report: "docs/nodejs/reports/phase13/P13_21_TASK_DATABASE_PROVIDER.md",
  taskDecisionDoc: "docs/nodejs/design/TASKS_DAILY_WEEKLY_USER_DATABASE_DECISION.md",
  taskMigrationDoc: "docs/nodejs/design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md",
  workManagerDoc: "docs/nodejs/design/WORK_MANAGER.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  timedTaskSnapshots: "app/src/main/java/org/autojs/autojs/engine/NodeTimedTaskSnapshots.kt",
  workManagerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt",
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  workManagerTypes: "docs/nodejs/types/autojs6-node/work_manager.d.ts",
  rhinoCompatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  errorCatalog: "docs/nodejs/ERROR_CATALOG.md",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
  gradle14: "app/node-phase14-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot, reportDir);
  const report = buildReport(files);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 14 task database provider: " + report.decision.status);
  console.log("Phase 14 task database provider blockers: " + report.blockers.length);
  console.log("Phase 14 task database provider policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot, reportDir) {
  const entries = Object.entries(FILES).map(([key, relative]) => {
    const file = path.join(repoRoot, relative);
    return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
  });
  const p13Generated = path.join(reportDir, "phase13-task-database-provider.json");
  entries.push([
    "p13TaskDatabaseGeneratedReport",
    {
      relative: path.relative(repoRoot, p13Generated).replace(/\\/g, "/"),
      exists: fs.existsSync(p13Generated),
      text: read(p13Generated),
    },
  ]);
  return Object.fromEntries(entries);
}

function buildReport(files) {
  const manifest = parseJson(files.surfaceManifest.text);
  const plan = parseJson(files.planJson.text);
  const p13Generated = parseJson(files.p13TaskDatabaseGeneratedReport.text);
  const contract = plan.operationContract || {};
  const ownership = contract.ownershipMapping || {};
  const proSurface = (manifest.surfaces || []).find((entry) => entry.id === "pro.tasks_device_media") || null;
  const p13Decision = p13Generated.decision || {};
  const scenarios = [
    scenario("json_safe_contract", "The future task database provider is reserved as JSON-only Pro authority", [
      checkTokenValue("schema", plan.schema === "autojs6-node-phase14-task-database-provider-plan-v1"),
      checkTokenValue("task", plan.task === "P14-17"),
      checkTokenValue("status", plan.status === "partial"),
      checkTokenValue("promotionState", plan.promotionState === "provider_poc"),
      checkTokenValue("surface", plan.surface === "pro.tasks_device_media"),
      checkTokenValue("providerName", contract.providerName === "tasks"),
      checkTokenValue("currentRuntimeAuthority", contract.currentRuntimeAuthority === "not_exposed"),
      checkTokenValue("operations", sameSet(contract.operations || [], ["add", "update", "enable", "disable", "remove", "list", "get"])),
      checkTokenValue("stableErrors", includesAll(contract.stableErrors || [], [
        "ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED",
        "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED",
        "ERR_AUTOJS6_BRIDGE_INVALID_REQUEST",
        "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED",
        "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN",
      ])),
      checkTokenValue("rawDenials", includesAll(contract.rawObjectsDenied || [], RAW_DENIALS)),
    ]),
    scenario("phase13_evidence_imported", "P13-21 partial task database evidence is imported without promotion", [
      checkTokenValue("p13Generated", files.p13TaskDatabaseGeneratedReport.exists),
      checkTokenValue("p13Status", p13Generated.status === "partial" || p13Decision.status === "partial"),
      checkTokenValue("p13Snapshots", p13Decision.timedTaskSnapshotsReady === true),
      checkTokenValue("p13NoPromotion", p13Decision.proSurfacePromoted === false),
      token(files.p13Report, "Status: partial"),
      token(files.p13Report, "The future owner for daily/weekly/user task database parity is a dedicated"),
      token(files.taskDecisionDoc, "P13-21 descriptor and id mapping policy:"),
      token(files.taskMigrationDoc, "P13-21 adds `NodeTimedTaskSnapshots` as read-only legacy `TimedTask` query/get"),
      token(files.workManagerDoc, "Current Decision"),
      token(files.workManagerTypes, 'declare module "work_manager"'),
      token(files.timedTaskSnapshots, "autojs6-node-timed-task-snapshot-v1"),
      token(files.timedTaskSnapshots, '"databaseCursor"'),
      token(files.workManagerTest, "timedTaskSnapshotsStayJsonOnlyWithoutSchedulerHandles"),
    ]),
    scenario("current_runtime_boundary", "Runtime still exposes only work_manager and compat.tasks migration paths", [
      token(files.providerRegistry, '"phase14TaskDatabasePromotion" to "P14-17 JSON-safe task database mutation contract gate"'),
      token(files.providerRegistry, '"taskDatabaseMutationContract" to "add,update,enable,disable,remove,list,get,ownership_mapping,id_mapping,stable_errors"'),
      token(files.providerRegistry, '"taskDatabaseMutationPromotion" to "blocked_until_live_json_safe_database_mutation_provider_schema_migration_packaged_row_ownership_and_scheduler_reschedule_evidence"'),
      token(files.providerRegistry, '"taskDatabaseRawObjectPolicy" to "no raw TimedTask, scheduler, database cursor, mutable row handle, or IntentTask crosses the bridge"'),
      token(files.providerRegistry, '"requireTasksPolicy" to "require(\\"tasks\\"), global tasks, and raw Rhino task objects remain unavailable until a dedicated Pro profile provider lands"'),
      token(files.nodeBridgeSources, "P12-17 has no JSON patch/update provider for the AutoJs6 user task database"),
      token(files.workManagerTest, "compat.tasks.rejected=ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED|ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED|ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN"),
      token(files.conformanceTest, "compat.tasks rejected stubs must not dispatch bridge requests"),
      token(files.rhinoCompatTypes, "addDailyTask(options?: unknown): Promise<never>;"),
      token(files.rhinoCompatTypes, "updateTask(task?: unknown): Promise<never>;"),
      checkTokenValue("tasksModuleNotExposed", !files.protocol.text.includes('const val TASKS = "tasks"') && !files.nodeBridgeSources.text.includes('if (name === "tasks")')),
      checkTokenValue("futureCapabilitiesNotAccepted", !files.permissionManifest.text.includes('const val TASKS = "tasks"') && !files.permissionManifest.text.includes("TASKS_INTENT")),
    ]),
    scenario("ownership_id_and_error_policy", "Owner/id mapping and stable denial guidance are documented", [
      checkTokenValue("idNamespaces", sameSet(ownership.idNamespaces || [], ["nodeTaskId", "legacyTimedTaskId", "packagedTaskId"])),
      checkTokenValue("legacyIdsImmutable", ownership.legacyTimedTaskIdsMutable === false),
      checkTokenValue("packagedNoAlias", ownership.packagedRowsMainAppAliasAllowed === false),
      token(files.planDoc, "Node task ids, legacy `TimedTask` row ids, and packaged task ids are separate namespaces"),
      token(files.phase14Report, "Stable denial guidance covers missing capability, permission denial, invalid"),
      token(files.compatibilityProfile, "P14-17 records the v1.2 JSON-safe task database mutation contract"),
      token(files.securityModel, "P14-17 records the v1.2 JSON-safe task database mutation contract"),
      token(files.errorCatalog, "P14-17 task database mutation denial sync"),
      token(files.errorCatalog, "Raw `TimedTask`, scheduler, database cursor, mutable row handle, `IntentTask`, `PendingIntent`, `Intent`, and Java objects remain blocked"),
    ]),
    scenario("surface_docs_and_gradle_wiring", "Phase 14 manifest, reports, docs, and Gradle expose the P14-17 gate", [
      checkTokenValue("surfaceEvidence", proSurface &&
        proSurface.status === "provider_poc" &&
        (proSurface.tasks || []).includes("P14-17") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/task-database-provider-plan.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14TaskDatabaseProvider")),
      token(files.surfaceManifestDoc, "P14-17 adds the JSON-safe task database mutation contract"),
      token(files.phase14Tasks, "Task database provider contract gate deployed:"),
      token(files.phase14Readme, ":app:verifyNodePhase14TaskDatabaseProvider"),
      token(files.testing, ":app:verifyNodePhase14TaskDatabaseProvider"),
      token(files.ciGates, ":app:verifyNodePhase14TaskDatabaseProvider"),
      token(files.gradle14, "verifyNodePhase14TaskDatabaseProvider"),
      token(files.gradle14, "verify-node-phase14-task-database-provider.js"),
    ]),
    scenario("promotion_archives_open", "Live mutation, ownership/id mapping, migration, scheduler, and packaged archives remain future work", [
      ...OPEN_BLOCKERS.flatMap((entry) => [
        token(files.planDoc, entry.id),
        token(files.phase14Report, entry.id),
      ]),
    ], OPEN_BLOCKERS.map((entry) => entry.id)),
  ];
  const checks = [
    check("phase13-generated-report-present", files.p13TaskDatabaseGeneratedReport.exists, "The P13-21 task database report must be generated before the P14-17 gate."),
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase14-task-database-provider-plan-v1", "P14-17 task database provider plan JSON must exist."),
    check("plan-doc-present", files.planDoc.exists && files.planDoc.text.includes("Phase 14 Task Database Provider"), "P14-17 task database provider documentation must exist."),
    check(
      "contract-denies-raw-objects",
      includesAll(contract.rawObjectsDenied || [], RAW_DENIALS) && sameSet(contract.operations || [], ["add", "update", "enable", "disable", "remove", "list", "get"]),
      "P14-17 must reserve JSON-safe operations and deny raw task/scheduler/database handles.",
    ),
    check(
      "surface-manifest-evidence",
      proSurface &&
        proSurface.status === "provider_poc" &&
        (proSurface.tasks || []).includes("P14-17") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/task-database-provider-plan.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14TaskDatabaseProvider"),
      "The pro.tasks_device_media surface must include the P14-17 evidence and Gradle gate.",
    ),
    check(
      "tasks-runtime-still-closed",
      !files.protocol.text.includes('const val TASKS = "tasks"') &&
        !files.permissionManifest.text.includes('const val TASKS = "tasks"') &&
        !files.nodeBridgeSources.text.includes('if (name === "tasks")'),
      "P14-17 must not expose require(\"tasks\") or accept the future tasks capability yet.",
    ),
  ];
  const policyViolations = checks
    .filter((entry) => !entry.ok)
    .map((entry) => ({ id: entry.id, message: entry.message }));
  const blockers = OPEN_BLOCKERS.map((entry) => ({ ...entry }));
  return {
    schema: "autojs6-node-phase14-task-database-provider-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "partial" : "failed",
    task: "P14-17",
    generatedAt: new Date().toISOString(),
    inputs: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, { path: file.relative, exists: file.exists }])),
    checks,
    scenarios,
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc",
      p14TaskComplete: false,
      phase13EvidenceImported: scenarioById(scenarios, "phase13_evidence_imported").status === "ready",
      jsonSafeContractReady: scenarioById(scenarios, "json_safe_contract").status === "ready",
      currentRuntimeBoundaryClosed: scenarioById(scenarios, "current_runtime_boundary").status === "ready",
      ownershipIdMappingContractReady: scenarioById(scenarios, "ownership_id_and_error_policy").status === "ready",
      liveDatabaseMutationsReady: false,
      schemaMigrationRollbackReady: false,
      wallClockSchedulerArchiveReady: false,
      packagedTaskDatabaseReady: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: blockers.length,
      proSurfacePromoted: false,
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Task Database Provider",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- P14 task complete: \`${report.decision.p14TaskComplete}\``,
    `- Phase 13 evidence imported: \`${report.decision.phase13EvidenceImported}\``,
    `- JSON-safe contract ready: \`${report.decision.jsonSafeContractReady}\``,
    `- Current runtime boundary closed: \`${report.decision.currentRuntimeBoundaryClosed}\``,
    `- Future promotion blockers: \`${report.decision.futurePromotionBlockers}\``,
    "",
    "## Scenarios",
    "",
    "| Scenario | Status | Missing evidence |",
    "| --- | --- | --- |",
  ];
  report.scenarios.forEach((entry) => {
    const missing = entry.missing.length > 0 ? entry.missing.join("<br>") : "None.";
    lines.push(`| \`${entry.id}\` | \`${entry.status}\` | ${missing} |`);
  });
  lines.push("", "## Blockers", "", "| Blocker | Classification | Required evidence |", "| --- | --- | --- |");
  report.blockers.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | \`${entry.classification}\` | ${entry.requiredEvidence} |`);
  });
  if (report.policyViolations.length > 0) {
    lines.push("", "## Policy Violations", "");
    report.policyViolations.forEach((entry) => {
      lines.push(`- \`${entry.id}\`: ${entry.message}`);
    });
  }
  lines.push("");
  return lines.join("\n");
}

function scenario(id, description, checks, blockerIds = []) {
  const missing = checks.filter((entry) => !entry.ok).map((entry) => entry.message);
  return {
    id,
    description,
    status: missing.length === 0 ? "ready" : "missing_evidence",
    missing,
    blockerIds,
  };
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing_evidence" };
}

function check(id, ok, message) {
  return { id, ok: Boolean(ok), message };
}

function checkTokenValue(id, ok) {
  return check(id, ok, `failed contract check: ${id}`);
}

function token(file, value) {
  return check(`token:${file.relative}:${value}`, file.exists && file.text.includes(value), `missing evidence token: ${value}`);
}

function parseJson(text) {
  if (!text) {
    return {};
  }
  try {
    return JSON.parse(text);
  } catch {
    return {};
  }
}

function sameSet(actual, expected) {
  if (!Array.isArray(actual) || actual.length !== expected.length) {
    return false;
  }
  return expected.every((entry) => actual.includes(entry));
}

function includesAll(actual, expected) {
  return Array.isArray(actual) && expected.every((entry) => actual.includes(entry));
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8");
  } catch {
    return "";
  }
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      options.repoRoot = args[index + 1];
      index += 1;
    } else if (arg === "--report-dir") {
      options.reportDir = args[index + 1];
      index += 1;
    }
  }
  return options;
}

main();
