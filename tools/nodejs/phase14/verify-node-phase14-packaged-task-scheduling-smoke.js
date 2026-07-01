#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-packaged-task-scheduling-smoke";
const REQUIRED_SMOKE_ROWS = Object.freeze([
  "packaged_work_manager_fake_schedule_list_cancel",
  "live_work_manager_packaged_project_entry",
  "future_tasks_database_add_list_enable_disable_remove",
  "future_process_death_relaunch_task_removal",
  "future_disclosure_safe_downgrade",
]);
const OPEN_BLOCKERS = Object.freeze([
  {
    id: "packaged_task_database_positive_smoke_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged APK add/list/enable-disable/remove smoke over future tasks database rows with package identity ownership.",
  },
  {
    id: "packaged_task_process_death_relaunch_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged task process-death, relaunch, crash recovery, and last-run diagnostic archive.",
  },
  {
    id: "packaged_task_removal_cleanup_archive",
    classification: "future_promotion",
    requiredEvidence: "Task removal cleanup evidence proving owned scheduled work is cancelled and package-scoped rows are disabled or removed.",
  },
  {
    id: "packaged_task_disclosure_safe_downgrade_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged disclosure, stop action, Safe downgrade, and rollback evidence for task database promotion.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_19_PACKAGED_TASK_SCHEDULING_SMOKE.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/packaged-task-scheduling-smoke-plan.json",
  planDoc: "docs/nodejs/phase14/PHASE14_PACKAGED_TASK_SCHEDULING_SMOKE.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  promotionContractsJson: "docs/nodejs/phase14/phase14-promotion-contracts.json",
  promotionContractsDoc: "docs/nodejs/phase14/PHASE14_PROMOTION_CONTRACTS.md",
  p14TaskDatabasePlan: "docs/nodejs/phase14/task-database-provider-plan.json",
  p14TaskDatabaseReport: "docs/nodejs/reports/phase14/P14_17_TASK_DATABASE_PROVIDER.md",
  p14SchemaPlan: "docs/nodejs/phase14/task-schema-migration-rollback-plan.json",
  p14SchemaReport: "docs/nodejs/reports/phase14/P14_18_TASK_SCHEMA_MIGRATION_ROLLBACK.md",
  p13Report: "docs/nodejs/reports/phase13/P13_21_TASK_DATABASE_PROVIDER.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  workManagerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  rollbackPlaybook: "docs/nodejs/security/PROFILE_ROLLBACK_PLAYBOOK.md",
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
  console.log("Phase 14 packaged task scheduling smoke: " + report.decision.status);
  console.log("Phase 14 packaged task scheduling blockers: " + report.blockers.length);
  console.log("Phase 14 packaged task scheduling policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot, reportDir) {
  const entries = Object.entries(FILES).map(([key, relative]) => {
    const file = path.join(repoRoot, relative);
    return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
  });
  const generatedReports = {
    p14TaskDatabaseGeneratedReport: "phase14-task-database-provider.json",
    p14SchemaGeneratedReport: "phase14-task-schema-migration-rollback.json",
  };
  Object.entries(generatedReports).forEach(([key, basename]) => {
    const file = path.join(reportDir, basename);
    entries.push([
      key,
      {
        relative: path.relative(repoRoot, file).replace(/\\/g, "/"),
        exists: fs.existsSync(file),
        text: read(file),
      },
    ]);
  });
  return Object.fromEntries(entries);
}

function buildReport(files) {
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContractsJson.text);
  const plan = parseJson(files.planJson.text);
  const smokeContract = plan.smokeContract || {};
  const ownership = plan.ownershipContract || {};
  const smokeRows = Array.isArray(plan.smokeMatrix) ? plan.smokeMatrix : [];
  const smokeIds = smokeRows.map((entry) => entry.id);
  const p14TaskDatabaseGenerated = parseJson(files.p14TaskDatabaseGeneratedReport.text);
  const p14SchemaGenerated = parseJson(files.p14SchemaGeneratedReport.text);
  const p14TaskDatabaseDecision = p14TaskDatabaseGenerated.decision || {};
  const p14SchemaDecision = p14SchemaGenerated.decision || {};
  const proSurface = (manifest.surfaces || []).find((entry) => entry.id === "pro.tasks_device_media") || null;
  const requiredDisclosureFields = ((contracts.packagedHighPrivilegeDisclosure || {}).requiredFields || []);
  const scenarios = [
    scenario("packaged_smoke_contract", "P14-19 reserves packaged task scheduling smoke rows without promoting tasks DB authority", [
      checkTokenValue("schema", plan.schema === "autojs6-node-phase14-packaged-task-scheduling-smoke-plan-v1"),
      checkTokenValue("task", plan.task === "P14-19"),
      checkTokenValue("status", plan.status === "partial"),
      checkTokenValue("promotionState", plan.promotionState === "provider_poc"),
      checkTokenValue("sourceGates", sameSet(plan.sourceGates || [], ["P13-21", "P14-17", "P14-18"])),
      checkTokenValue("currentModule", smokeContract.currentModule === "work_manager"),
      checkTokenValue("futureProvider", smokeContract.futureProvider === "tasks"),
      checkTokenValue("packagedRowsNotPromoted", smokeContract.packagedTaskRowsPromoted === false),
      checkTokenValue("smokeRows", sameSet(smokeIds, REQUIRED_SMOKE_ROWS)),
      checkTokenValue("workManagerOps", sameSet(smokeContract.workManagerOperationsImported || [], ["scheduleOnce", "list", "cancel"])),
      token(files.planDoc, "`packaged_work_manager_fake_schedule_list_cancel`"),
      token(files.planDoc, "`live_work_manager_packaged_project_entry`"),
      token(files.planDoc, "`future_tasks_database_add_list_enable_disable_remove`"),
    ]),
    scenario("packaged_work_manager_fake_provider_imported", "Packaged v1.1 fake-provider work_manager schedule/list/cancel smoke is imported", [
      token(files.packagedSmokeTest, 'const work = require("work_manager");'),
      token(files.packagedSmokeTest, 'const workId = await work.scheduleOnce({'),
      token(files.packagedSmokeTest, 'const workBefore = await work.list({ timeoutMs: 1000 });'),
      token(files.packagedSmokeTest, 'await work.cancel(workId, { timeoutMs: 1000 });'),
      token(files.packagedSmokeTest, 'const workAfter = await work.list({ timeoutMs: 1000 });'),
      token(files.packagedSmokeTest, 'workId === "packaged-work-1"'),
      token(files.packagedSmokeTest, "workBefore.length === 1"),
      token(files.packagedSmokeTest, "workAfter.length === 0"),
      token(files.packagedSmokeTest, 'request.permissions.indexOf("work_manager") >= 0'),
      token(files.packagedSmokeTest, "packaged.v1_1_bridge.work_manager=true"),
      token(files.packagedSmokeTest, "packaged.v1_1_bridge.pending=0"),
    ]),
    scenario("live_packaged_project_entry_anchor_imported", "Live Android work_manager packaged project-entry scheduled-runner anchor is imported", [
      token(files.workManagerTest, "androidWorkManagerLiveProviderExecutesPackagedProjectEntryAndPersistsResult"),
      token(files.workManagerTest, '"scheduleOnce"'),
      token(files.workManagerTest, '"entry":"main.cjs","packaged":true'),
      token(files.workManagerTest, "scheduled packaged Node task should execute and persist result"),
      token(files.workManagerTest, 'assertEquals("projectEntry", task.getString("entryKind"))'),
      token(files.workManagerTest, 'assertTrue(task.getBoolean("packaged"))'),
      token(files.workManagerTest, "NodeExecutionModePolicy.LAUNCH_SURFACE_SCHEDULED_RUNNER"),
      token(files.workManagerTest, 'assertTrue(dispatch(provider, "cancel", JSONArray().put(taskId).toString()).ok)'),
      token(files.p13Report, "Direct Node module: `require(\"work_manager\")` exposes `scheduleOnce`"),
    ]),
    scenario("upstream_task_database_and_schema_imported", "P14-17 and P14-18 generated task reports remain partial upstream gates", [
      checkTokenValue("p14TaskDatabaseGenerated", files.p14TaskDatabaseGeneratedReport.exists),
      checkTokenValue("p14SchemaGenerated", files.p14SchemaGeneratedReport.exists),
      checkTokenValue("p14TaskDatabasePartial", p14TaskDatabaseGenerated.status === "partial" || p14TaskDatabaseDecision.status === "partial"),
      checkTokenValue("p14SchemaPartial", p14SchemaGenerated.status === "partial" || p14SchemaDecision.status === "partial"),
      checkTokenValue("p14TaskDatabasePackagedNotReady", p14TaskDatabaseDecision.packagedTaskDatabaseReady === false),
      checkTokenValue("p14SchemaPackagedRollbackNotReady", p14SchemaDecision.packagedRollbackArchiveReady === false),
      token(files.p14TaskDatabasePlan, "packaged_task_database_mutation_smoke_archive"),
      token(files.p14TaskDatabaseReport, "packaged_task_database_mutation_smoke_archive"),
      token(files.p14SchemaPlan, "task_schema_packaged_rollback_archive"),
      token(files.p14SchemaReport, "task_schema_packaged_rollback_archive"),
    ]),
    scenario("packaged_ownership_disclosure_and_rollback_policy", "Package identity ownership, disclosure fields, and Safe downgrade policy are documented", [
      checkTokenValue("packageIdentityRequired", ownership.packageIdentityRequired === true),
      checkTokenValue("mainAppAliasDenied", ownership.packagedRowsMainAppAliasAllowed === false),
      checkTokenValue("legacyRowsPreserved", ownership.legacyRowsPreserved === true),
      checkTokenValue("safeDowngradeAction", ownership.safeDowngradeAction === "disable_or_remove_owned_packaged_rows"),
      checkTokenValue("taskRemovalAction", ownership.taskRemovalAction === "disable_or_remove_owned_packaged_rows_and_cancel_owned_scheduled_work"),
      checkTokenValue("disclosureFields", includesAll(requiredDisclosureFields, ["manifestMetadata", "taskRemovalCleanup", "relaunchBehavior", "crashRecoveryBehavior", "rollbackBehavior"])),
      token(files.promotionContractsDoc, "`packaged_ready`"),
      token(files.promotionContractsDoc, "`task_database`"),
      token(files.rollbackPlaybook, "publish_safe_profile_patch_or_remove_promoted_metadata"),
      token(files.compatibilityProfile, "P14-19 records the packaged task scheduling smoke baseline"),
      token(files.securityModel, "P14-19 records packaged task scheduling smoke as partial"),
      token(files.errorCatalog, "P14-19 packaged task scheduling denial sync"),
    ]),
    scenario("current_runtime_boundary", "Packaged smoke evidence does not expose tasks provider authority", [
      token(files.providerRegistry, '"phase14PackagedTaskSchedulingPromotion" to "P14-19 packaged task scheduling smoke evidence contract gate"'),
      token(files.providerRegistry, '"packagedTaskSchedulingSmokePolicy" to "packaged_work_manager_fake_smoke_and_live_project_entry_anchor_task_database_rows_not_promoted"'),
      token(files.providerRegistry, '"packagedTaskRowOwnershipPolicy" to "package_identity_scoped_no_main_app_alias_disable_or_remove_owned_rows_on_downgrade"'),
      token(files.providerRegistry, '"requireTasksPolicy" to "require(\\"tasks\\"), global tasks, and raw Rhino task objects remain unavailable until a dedicated Pro profile provider lands"'),
      checkTokenValue("tasksModuleNotExposed", !files.protocol.text.includes('const val TASKS = "tasks"') && !files.nodeBridgeSources.text.includes('if (name === "tasks")')),
      checkTokenValue("futureCapabilitiesNotAccepted", !files.permissionManifest.text.includes('const val TASKS = "tasks"') && !files.permissionManifest.text.includes("TASKS_INTENT")),
    ]),
    scenario("surface_docs_and_gradle_wiring", "Phase 14 manifest, reports, docs, and Gradle expose the P14-19 gate", [
      checkTokenValue("surfaceEvidence", proSurface &&
        proSurface.status === "provider_poc" &&
        (proSurface.tasks || []).includes("P14-19") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/packaged-task-scheduling-smoke-plan.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14PackagedTaskSchedulingSmoke")),
      token(files.surfaceManifestDoc, "P14-19 adds the packaged task scheduling smoke baseline"),
      token(files.phase14Tasks, "Packaged task scheduling smoke gate deployed:"),
      token(files.phase14Readme, ":app:verifyNodePhase14PackagedTaskSchedulingSmoke"),
      token(files.testing, ":app:verifyNodePhase14PackagedTaskSchedulingSmoke"),
      token(files.ciGates, ":app:verifyNodePhase14PackagedTaskSchedulingSmoke"),
      token(files.gradle14, "verifyNodePhase14PackagedTaskSchedulingSmoke"),
      token(files.gradle14, "verify-node-phase14-packaged-task-scheduling-smoke.js"),
    ]),
    scenario("promotion_archives_open", "Positive tasks DB packaged smoke, relaunch, removal cleanup, and disclosure/downgrade archives remain future work", [
      ...OPEN_BLOCKERS.flatMap((entry) => [
        token(files.planDoc, entry.id),
        token(files.phase14Report, entry.id),
      ]),
    ], OPEN_BLOCKERS.map((entry) => entry.id)),
  ];
  const checks = [
    check("phase14-task-database-generated-report-present", files.p14TaskDatabaseGeneratedReport.exists, "The P14-17 task database provider report must be generated before the P14-19 gate."),
    check("phase14-schema-generated-report-present", files.p14SchemaGeneratedReport.exists, "The P14-18 task schema migration report must be generated before the P14-19 gate."),
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase14-packaged-task-scheduling-smoke-plan-v1", "P14-19 packaged task scheduling smoke plan JSON must exist."),
    check("plan-doc-present", files.planDoc.exists && files.planDoc.text.includes("Phase 14 Packaged Task Scheduling Smoke"), "P14-19 packaged task scheduling smoke documentation must exist."),
    check(
      "smoke-matrix-covers-required-rows",
      sameSet(smokeIds, REQUIRED_SMOKE_ROWS),
      "P14-19 smoke matrix must cover packaged work_manager import, live packaged entry, future task DB operations, process death/relaunch, and disclosure/downgrade rows.",
    ),
    check(
      "surface-manifest-evidence",
      proSurface &&
        proSurface.status === "provider_poc" &&
        (proSurface.tasks || []).includes("P14-19") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/packaged-task-scheduling-smoke-plan.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14PackagedTaskSchedulingSmoke"),
      "The pro.tasks_device_media surface must include the P14-19 evidence and Gradle gate.",
    ),
    check(
      "tasks-runtime-still-closed",
      !files.protocol.text.includes('const val TASKS = "tasks"') &&
        !files.permissionManifest.text.includes('const val TASKS = "tasks"') &&
        !files.nodeBridgeSources.text.includes('if (name === "tasks")'),
      "P14-19 must not expose require(\"tasks\") or accept the future tasks capability yet.",
    ),
  ];
  const policyViolations = checks
    .filter((entry) => !entry.ok)
    .map((entry) => ({ id: entry.id, message: entry.message }));
  const blockers = OPEN_BLOCKERS.map((entry) => ({ ...entry }));
  return {
    schema: "autojs6-node-phase14-packaged-task-scheduling-smoke-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "partial" : "failed",
    task: "P14-19",
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
      packagedWorkManagerFakeSmokeImported: scenarioById(scenarios, "packaged_work_manager_fake_provider_imported").status === "ready",
      livePackagedProjectEntryImported: scenarioById(scenarios, "live_packaged_project_entry_anchor_imported").status === "ready",
      upstreamTaskDatabaseReportsImported: scenarioById(scenarios, "upstream_task_database_and_schema_imported").status === "ready",
      ownershipDisclosureRollbackContractReady: scenarioById(scenarios, "packaged_ownership_disclosure_and_rollback_policy").status === "ready",
      packagedTaskDatabasePositiveSmokeReady: false,
      processDeathRelaunchArchiveReady: false,
      taskRemovalCleanupArchiveReady: false,
      disclosureSafeDowngradeArchiveReady: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: blockers.length,
      proSurfacePromoted: false,
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Packaged Task Scheduling Smoke",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- P14 task complete: \`${report.decision.p14TaskComplete}\``,
    `- Packaged WorkManager fake smoke imported: \`${report.decision.packagedWorkManagerFakeSmokeImported}\``,
    `- Live packaged project-entry imported: \`${report.decision.livePackagedProjectEntryImported}\``,
    `- Packaged task database positive smoke ready: \`${report.decision.packagedTaskDatabasePositiveSmokeReady}\``,
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
