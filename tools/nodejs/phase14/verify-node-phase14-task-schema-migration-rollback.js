#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-task-schema-migration-rollback";
const REQUIRED_FIXTURES = Object.freeze([
  "current_descriptor_v1",
  "older_descriptor_v0",
  "legacy_timed_task_snapshot_v1",
  "corrupt_descriptor",
  "unknown_future_schema",
  "packaged_owner_mismatch",
]);
const OPEN_BLOCKERS = Object.freeze([
  {
    id: "task_schema_live_migration_tests_archive",
    classification: "future_promotion",
    requiredEvidence: "Runtime migration tests for current, older, corrupt, and unknown descriptor versions with stable pass/fail diagnostics.",
  },
  {
    id: "task_schema_corruption_recovery_archive",
    classification: "future_promotion",
    requiredEvidence: "Corrupt descriptor recovery evidence proving quarantine, no scheduler reschedule, and no legacy row deletion.",
  },
  {
    id: "task_schema_downgrade_legacy_visibility_archive",
    classification: "future_promotion",
    requiredEvidence: "Safe downgrade and Pro disable evidence proving legacy Rhino TimedTask rows remain visible and Node descriptors fail closed.",
  },
  {
    id: "task_schema_packaged_rollback_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged rollback evidence proving packaged task rows are removed or disabled only for the owning package identity.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_18_TASK_SCHEMA_MIGRATION_ROLLBACK.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/task-schema-migration-rollback-plan.json",
  planDoc: "docs/nodejs/phase14/PHASE14_TASK_SCHEMA_MIGRATION_ROLLBACK.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  p14TaskDatabasePlan: "docs/nodejs/phase14/task-database-provider-plan.json",
  p14TaskDatabaseDoc: "docs/nodejs/phase14/PHASE14_TASK_DATABASE_PROVIDER.md",
  p14TaskDatabaseReport: "docs/nodejs/reports/phase14/P14_17_TASK_DATABASE_PROVIDER.md",
  p13Report: "docs/nodejs/reports/phase13/P13_21_TASK_DATABASE_PROVIDER.md",
  taskDecisionDoc: "docs/nodejs/design/TASKS_DAILY_WEEKLY_USER_DATABASE_DECISION.md",
  taskMigrationDoc: "docs/nodejs/design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  timedTaskSnapshots: "app/src/main/java/org/autojs/autojs/engine/NodeTimedTaskSnapshots.kt",
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
  console.log("Phase 14 task schema migration rollback: " + report.decision.status);
  console.log("Phase 14 task schema migration blockers: " + report.blockers.length);
  console.log("Phase 14 task schema migration policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot, reportDir) {
  const entries = Object.entries(FILES).map(([key, relative]) => {
    const file = path.join(repoRoot, relative);
    return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
  });
  const p14TaskDatabaseGenerated = path.join(reportDir, "phase14-task-database-provider.json");
  entries.push([
    "p14TaskDatabaseGeneratedReport",
    {
      relative: path.relative(repoRoot, p14TaskDatabaseGenerated).replace(/\\/g, "/"),
      exists: fs.existsSync(p14TaskDatabaseGenerated),
      text: read(p14TaskDatabaseGenerated),
    },
  ]);
  return Object.fromEntries(entries);
}

function buildReport(files) {
  const manifest = parseJson(files.surfaceManifest.text);
  const plan = parseJson(files.planJson.text);
  const contract = plan.schemaContract || {};
  const rollback = plan.rollbackContract || {};
  const fixtures = Array.isArray(plan.fixtureMatrix) ? plan.fixtureMatrix : [];
  const fixtureIds = fixtures.map((entry) => entry.id);
  const p14TaskDatabaseGenerated = parseJson(files.p14TaskDatabaseGeneratedReport.text);
  const p14TaskDatabaseDecision = p14TaskDatabaseGenerated.decision || {};
  const proSurface = (manifest.surfaces || []).find((entry) => entry.id === "pro.tasks_device_media") || null;
  const scenarios = [
    scenario("schema_fixture_matrix", "Current, older, legacy, corrupt, unknown, and packaged-owner fixtures are reserved", [
      checkTokenValue("schema", plan.schema === "autojs6-node-phase14-task-schema-migration-rollback-plan-v1"),
      checkTokenValue("task", plan.task === "P14-18"),
      checkTokenValue("status", plan.status === "partial"),
      checkTokenValue("promotionState", plan.promotionState === "provider_poc"),
      checkTokenValue("sourceGates", sameSet(plan.sourceGates || [], ["P13-21", "P14-17"])),
      checkTokenValue("descriptorSchema", contract.descriptorSchema === "autojs6-node-task-database-descriptor-v1"),
      checkTokenValue("currentVersion", contract.currentVersion === 1),
      checkTokenValue("fixtures", sameSet(fixtureIds, REQUIRED_FIXTURES)),
      checkTokenValue("fixturesNoMutation", fixtures.every((entry) => entry.mutationAllowedNow === false)),
      token(files.planDoc, "`current_descriptor_v1`"),
      token(files.planDoc, "`older_descriptor_v0`"),
      token(files.planDoc, "`corrupt_descriptor`"),
      token(files.planDoc, "`unknown_future_schema`"),
    ]),
    scenario("rollback_contract", "Downgrade, corruption recovery, unknown schema, legacy visibility, and packaged owner policy are documented", [
      checkTokenValue("unknownFutureVersionPolicy", contract.unknownFutureVersionPolicy === "fail_closed_preserve_row_without_mutation"),
      checkTokenValue("corruptionPolicy", contract.corruptionPolicy === "quarantine_descriptor_preserve_legacy_visibility_no_reschedule"),
      checkTokenValue("downgradePolicy", contract.downgradePolicy === "disable_or_ignore_node_descriptors_keep_legacy_rhino_rows_visible"),
      checkTokenValue("packagedRowPolicy", contract.packagedRowPolicy === "package_identity_scoped_rows_cannot_alias_main_app_or_legacy_rows"),
      checkTokenValue("legacyVisibilityPolicy", contract.legacyVisibilityPolicy === "legacy TimedTask rows remain visible to Rhino and can be represented only as immutable NodeTimedTaskSnapshots"),
      checkTokenValue("safeProfileDowngrade", rollback.safeProfileDowngrade === "ignore Node task descriptors and keep legacy Rhino rows visible"),
      checkTokenValue("packagedDowngrade", rollback.packagedDowngrade === "remove or disable only rows owned by the packaged app identity"),
      token(files.phase14Report, "Safe-profile downgrade ignores Node descriptors"),
      token(files.phase14Report, "unknown future schema descriptors are preserved without mutation"),
      token(files.compatibilityProfile, "P14-18 records the task schema migration and rollback contract"),
      token(files.securityModel, "P14-18 records task schema migration and rollback as partial"),
      token(files.errorCatalog, "P14-18 task schema migration rollback denial sync"),
    ]),
    scenario("p14_17_import", "P14-17 generated task database provider report is imported as the upstream partial gate", [
      checkTokenValue("p14TaskDatabaseGenerated", files.p14TaskDatabaseGeneratedReport.exists),
      checkTokenValue("p14TaskDatabaseStatus", p14TaskDatabaseGenerated.status === "partial" || p14TaskDatabaseDecision.status === "partial"),
      checkTokenValue("p14TaskDatabaseMigrationNotReady", p14TaskDatabaseDecision.schemaMigrationRollbackReady === false),
      checkTokenValue("p14TaskDatabaseNotComplete", p14TaskDatabaseDecision.p14TaskComplete === false),
      token(files.p14TaskDatabasePlan, "task_database_schema_migration_rollback_archive"),
      token(files.p14TaskDatabaseReport, "task_database_schema_migration_rollback_archive"),
      token(files.p13Report, "Migration and downgrade policy: legacy `TimedTask` rows"),
      token(files.taskDecisionDoc, "Downgrades must leave legacy Rhino rows visible"),
      token(files.taskMigrationDoc, "P13-21 adds `NodeTimedTaskSnapshots` as read-only legacy `TimedTask` query/get"),
      token(files.timedTaskSnapshots, "autojs6-node-timed-task-snapshot-v1"),
    ]),
    scenario("current_runtime_boundary", "Schema migration contract does not expose tasks authority yet", [
      token(files.providerRegistry, '"phase14TaskSchemaMigrationPromotion" to "P14-18 task schema migration and rollback evidence contract gate"'),
      token(files.providerRegistry, '"taskDatabaseSchemaMigrationPolicy" to "versioned_json_descriptor_current_older_corrupt_unknown_fail_closed"'),
      token(files.providerRegistry, '"taskDatabaseRollbackPolicy" to "safe_downgrade_preserves_legacy_visibility_packaged_rows_disabled_or_removed_by_owner_policy"'),
      token(files.providerRegistry, '"requireTasksPolicy" to "require(\\"tasks\\"), global tasks, and raw Rhino task objects remain unavailable until a dedicated Pro profile provider lands"'),
      checkTokenValue("tasksModuleNotExposed", !files.protocol.text.includes('const val TASKS = "tasks"') && !files.nodeBridgeSources.text.includes('if (name === "tasks")')),
      checkTokenValue("futureCapabilitiesNotAccepted", !files.permissionManifest.text.includes('const val TASKS = "tasks"') && !files.permissionManifest.text.includes("TASKS_INTENT")),
    ]),
    scenario("surface_docs_and_gradle_wiring", "Phase 14 manifest, reports, docs, and Gradle expose the P14-18 gate", [
      checkTokenValue("surfaceEvidence", proSurface &&
        proSurface.status === "provider_poc" &&
        (proSurface.tasks || []).includes("P14-18") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/task-schema-migration-rollback-plan.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14TaskSchemaMigrationRollback")),
      token(files.surfaceManifestDoc, "P14-18 adds the task schema migration and rollback contract"),
      token(files.phase14Tasks, "Task schema migration and rollback gate deployed:"),
      token(files.phase14Readme, ":app:verifyNodePhase14TaskSchemaMigrationRollback"),
      token(files.testing, ":app:verifyNodePhase14TaskSchemaMigrationRollback"),
      token(files.ciGates, ":app:verifyNodePhase14TaskSchemaMigrationRollback"),
      token(files.gradle14, "verifyNodePhase14TaskSchemaMigrationRollback"),
      token(files.gradle14, "verify-node-phase14-task-schema-migration-rollback.js"),
    ]),
    scenario("promotion_archives_open", "Live migration, corruption, downgrade, and packaged rollback archives remain future work", [
      ...OPEN_BLOCKERS.flatMap((entry) => [
        token(files.planDoc, entry.id),
        token(files.phase14Report, entry.id),
      ]),
    ], OPEN_BLOCKERS.map((entry) => entry.id)),
  ];
  const checks = [
    check("phase14-task-database-generated-report-present", files.p14TaskDatabaseGeneratedReport.exists, "The P14-17 task database provider report must be generated before the P14-18 gate."),
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase14-task-schema-migration-rollback-plan-v1", "P14-18 task schema migration rollback plan JSON must exist."),
    check("plan-doc-present", files.planDoc.exists && files.planDoc.text.includes("Phase 14 Task Schema Migration Rollback"), "P14-18 task schema migration rollback documentation must exist."),
    check(
      "fixture-matrix-covers-required-shapes",
      sameSet(fixtureIds, REQUIRED_FIXTURES) &&
        includesAll(fixtures.map((entry) => entry.inputKind), ["current", "older", "legacy_snapshot", "corrupt", "unknown", "owner_mismatch"]),
      "P14-18 fixture matrix must cover current, older, legacy, corrupt, unknown, and packaged owner-mismatch shapes.",
    ),
    check(
      "surface-manifest-evidence",
      proSurface &&
        proSurface.status === "provider_poc" &&
        (proSurface.tasks || []).includes("P14-18") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/task-schema-migration-rollback-plan.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14TaskSchemaMigrationRollback"),
      "The pro.tasks_device_media surface must include the P14-18 evidence and Gradle gate.",
    ),
    check(
      "tasks-runtime-still-closed",
      !files.protocol.text.includes('const val TASKS = "tasks"') &&
        !files.permissionManifest.text.includes('const val TASKS = "tasks"') &&
        !files.nodeBridgeSources.text.includes('if (name === "tasks")'),
      "P14-18 must not expose require(\"tasks\") or accept the future tasks capability yet.",
    ),
  ];
  const policyViolations = checks
    .filter((entry) => !entry.ok)
    .map((entry) => ({ id: entry.id, message: entry.message }));
  const blockers = OPEN_BLOCKERS.map((entry) => ({ ...entry }));
  return {
    schema: "autojs6-node-phase14-task-schema-migration-rollback-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "partial" : "failed",
    task: "P14-18",
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
      p14TaskDatabaseProviderImported: scenarioById(scenarios, "p14_17_import").status === "ready",
      schemaFixtureMatrixReady: scenarioById(scenarios, "schema_fixture_matrix").status === "ready",
      currentOlderCorruptUnknownFixturesReady: sameSet(fixtureIds, REQUIRED_FIXTURES),
      rollbackContractReady: scenarioById(scenarios, "rollback_contract").status === "ready",
      currentRuntimeBoundaryClosed: scenarioById(scenarios, "current_runtime_boundary").status === "ready",
      liveMigrationTestsReady: false,
      corruptionRecoveryArchiveReady: false,
      downgradeLegacyVisibilityArchiveReady: false,
      packagedRollbackArchiveReady: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: blockers.length,
      proSurfacePromoted: false,
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Task Schema Migration Rollback",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- P14 task complete: \`${report.decision.p14TaskComplete}\``,
    `- P14 task database provider imported: \`${report.decision.p14TaskDatabaseProviderImported}\``,
    `- Schema fixture matrix ready: \`${report.decision.schemaFixtureMatrixReady}\``,
    `- Rollback contract ready: \`${report.decision.rollbackContractReady}\``,
    `- Live migration tests ready: \`${report.decision.liveMigrationTestsReady}\``,
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
