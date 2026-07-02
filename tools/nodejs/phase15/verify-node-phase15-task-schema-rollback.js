#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-task-schema-rollback";
const REQUIRED_FIXTURES = Object.freeze([
  "current_descriptor_v1",
  "older_descriptor_v0",
  "legacy_timed_task_snapshot_v1",
  "corrupt_descriptor",
  "unknown_future_schema",
  "packaged_owner_mismatch",
]);
const REQUIRED_READY_EVIDENCE = Object.freeze([
  "phase14_schema_rollback_contract",
  "phase15_task_database_dependency_gate",
  "phase15_promotion_contracts",
  "phase15_disclosure_rollback_contract",
  "phase15_surface_manifest_mapping",
]);
const REQUIRED_BLOCKERS = Object.freeze([
  "task_schema_live_migration_tests_archive",
  "task_schema_corruption_recovery_archive",
  "task_schema_downgrade_legacy_visibility_archive",
  "task_schema_packaged_rollback_archive",
  "task_schema_backup_restore_archive",
  "task_schema_safe_downgrade_archive",
  "task_schema_cross_package_rollback_denial_archive",
  "phase15_task_device_media_doc_sync",
]);

const FILES = {
  plan: "docs/nodejs/phase15/task-schema-rollback-plan.json",
  planDoc: "docs/nodejs/phase15/PHASE15_TASK_SCHEMA_ROLLBACK.md",
  taskReport: "docs/nodejs/reports/phase15/P15_13_TASK_SCHEMA_ROLLBACK.md",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  promotionContracts: "docs/nodejs/phase15/node-v1-3-promotion-contracts.json",
  promotionReport: "build/reports/nodejs/phase15-promotion-contracts.json",
  disclosureAudit: "docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json",
  disclosureReport: "build/reports/nodejs/phase15-disclosure-rollback-audit.json",
  p15TaskDatabaseReport: "build/reports/nodejs/phase15-task-database-promotion.json",
  phase14Plan: "docs/nodejs/phase14/task-schema-migration-rollback-plan.json",
  phase14PlanDoc: "docs/nodejs/phase14/PHASE14_TASK_SCHEMA_MIGRATION_ROLLBACK.md",
  phase14TaskReport: "docs/nodejs/reports/phase14/P14_18_TASK_SCHEMA_MIGRATION_ROLLBACK.md",
  phase14Generated: "build/reports/nodejs/phase14-task-schema-migration-rollback.json",
  p14TaskDatabaseGenerated: "build/reports/nodejs/phase14-task-database-provider.json",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  timedTaskSnapshots: "app/src/main/java/org/autojs/autojs/engine/NodeTimedTaskSnapshots.kt",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  errorCatalog: "docs/nodejs/ERROR_CATALOG.md",
  phase15Gradle: "app/node-phase15-gate.gradle.kts",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
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
  console.log("Phase 15 task schema rollback: " + report.decision.status);
  console.log("Phase 15 task schema blockers: " + report.blockers.length);
  console.log("Phase 15 task schema policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0) {
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
  const plan = parseJson(files.plan.text);
  const phase14Plan = parseJson(files.phase14Plan.text);
  const phase14Report = parseJson(files.phase14Generated.text);
  const p15TaskDatabaseReport = parseJson(files.p15TaskDatabaseReport.text);
  const p14TaskDatabaseReport = parseJson(files.p14TaskDatabaseGenerated.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContracts.text);
  const promotionReport = parseJson(files.promotionReport.text);
  const disclosureAudit = parseJson(files.disclosureAudit.text);
  const disclosureReport = parseJson(files.disclosureReport.text);
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-13") || {};
  const taskDatabaseContract = (contracts.providerPromotionBars || []).find((entry) => entry.id === "task_database") || {};
  const taskDatabaseAudit = (disclosureAudit.providerAudits || []).find((entry) => entry.providerId === "task_database") || {};
  const schemaContract = plan.schemaContract || {};
  const rollbackContract = plan.rollbackContract || {};
  const fixtures = Array.isArray(plan.fixtureMatrix) ? plan.fixtureMatrix : [];
  const fixtureIds = fixtures.map((entry) => entry.id);
  const p14Decision = phase14Report.decision || {};
  const p15TaskDatabaseDecision = p15TaskDatabaseReport.decision || {};
  const p14TaskDatabaseDecision = p14TaskDatabaseReport.decision || {};
  const scenarios = [
    scenario("phase14_schema_rollback_imported", "P14-18 schema fixture matrix and rollback contract are imported", [
      value("phase14_plan_schema", phase14Plan.schema === "autojs6-node-phase14-task-schema-migration-rollback-plan-v1"),
      value("phase14_report_status", statusOf(phase14Report) === "partial"),
      value("phase14_fixture_matrix_ready", p14Decision.schemaFixtureMatrixReady === true),
      value("phase14_fixture_set_ready", p14Decision.currentOlderCorruptUnknownFixturesReady === true),
      value("phase14_not_promoted", p14Decision.proSurfacePromoted === false),
      value("phase14_future_blockers", p14Decision.futurePromotionBlockers === 4),
      value("p14_task_database_imported", statusOf(p14TaskDatabaseReport) === "partial" && p14TaskDatabaseDecision.jsonSafeContractReady === true),
      token(files.phase14TaskReport, "P14-18 deploys the task schema migration and rollback evidence contract gate."),
      token(files.phase14PlanDoc, "`unknown_future_schema`"),
      token(files.phase14PlanDoc, "`packaged_owner_mismatch`"),
      token(files.timedTaskSnapshots, "autojs6-node-timed-task-snapshot-v1"),
    ]),
    scenario("phase15_task_database_dependency", "P15-12 task database dependency gate remains partial and unpromoted", [
      value("p15_task_database_status", statusOf(p15TaskDatabaseReport) === "partial"),
      value("p15_task_database_not_promoted", p15TaskDatabaseDecision.taskDatabasePromoted === false),
      value("p15_task_database_runtime_authority", p15TaskDatabaseDecision.runtimeAuthorityChanged === false),
      value("p15_task_database_future_blockers", p15TaskDatabaseDecision.futurePromotionBlockers === 9),
      token(files.p15TaskDatabaseReport, "\"taskDatabasePromoted\": false"),
      token(files.p15TaskDatabaseReport, "\"task_database_schema_migration_rollback_archive\""),
    ]),
    scenario("schema_contract_boundary", "P15-13 keeps schema migration fixture-only with fail-closed rollback policy", [
      value("descriptor_schema", schemaContract.descriptorSchema === "autojs6-node-task-database-descriptor-v1"),
      value("current_version", schemaContract.currentVersion === 1),
      value("fixtures", sameSet(fixtureIds, REQUIRED_FIXTURES)),
      value("fixtures_no_mutation", fixtures.every((entry) => entry.mutationAllowedNow === false)),
      value("unknown_future_policy", schemaContract.unknownFutureVersionPolicy === "fail_closed_preserve_row_without_mutation"),
      value("corruption_policy", schemaContract.corruptionPolicy === "quarantine_descriptor_preserve_legacy_visibility_no_reschedule"),
      value("downgrade_policy", schemaContract.downgradePolicy === "disable_or_ignore_node_descriptors_keep_legacy_rhino_rows_visible"),
      value("packaged_policy", schemaContract.packagedRowPolicy === "package_identity_scoped_rows_cannot_alias_main_app_or_legacy_rows"),
      value("safe_downgrade", rollbackContract.safeProfileDowngrade === "ignore Node task descriptors and keep legacy Rhino rows visible"),
      token(files.providerRegistry, "\"taskDatabaseSchemaMigrationPolicy\" to \"versioned_json_descriptor_current_older_corrupt_unknown_fail_closed\""),
      token(files.providerRegistry, "\"taskDatabaseRollbackPolicy\" to \"safe_downgrade_preserves_legacy_visibility_packaged_rows_disabled_or_removed_by_owner_policy\""),
      value("tasks_module_not_exposed", !files.protocol.text.includes('const val TASKS = "tasks"') && !files.nodeBridgeSources.text.includes('if (name === "tasks")')),
      value("future_tasks_capability_not_accepted", !files.permissionManifest.text.includes('const val TASKS = "tasks"') && !files.permissionManifest.text.includes("TASKS_INTENT")),
    ]),
    scenario("phase15_contracts_and_rollback", "Phase 15 contracts keep task schema rollback behind disclosure, rollback, type, security, and packaged bars", [
      value("promotion_report_ready", statusOf(promotionReport) === "ready"),
      value("disclosure_report_ready", statusOf(disclosureReport) === "ready"),
      value("task_contract_provider_poc", taskDatabaseContract.currentState === "provider_poc"),
      value("task_contract_p15_task", includesAll(taskDatabaseContract.p15Tasks || [], ["P15-13"])),
      value("task_contract_evidence_bars", includesAll(taskDatabaseContract.requiredEvidence || [], [
        "implementation_gate",
        "packaged_apk_evidence",
        "user_visible_disclosure",
        "rollback_disable_path",
        "safe_downgrade_behavior",
        "raw_object_denial_regression",
      ])),
      value("task_audit_provider_poc", taskDatabaseAudit.currentState === "provider_poc"),
      value("task_audit_disable_flag", taskDatabaseAudit.disableFlag === "node.provider.task_database.enabled"),
      value("task_audit_diagnostic", taskDatabaseAudit.diagnosticCode === "NODE_PROVIDER_TASK_DATABASE_DISABLED"),
      value("task_audit_safe_downgrade", taskDatabaseAudit.safeDowngrade === "preserve rows and deny package-owned task mutations"),
    ]),
    scenario("promotion_blockers_open", "Live migration, corruption, downgrade, packaged rollback, backup, Safe downgrade, cross-package denial, and docs sync remain future work", [
      value("open_blockers", sameSet((plan.openBlockers || []).map((entry) => entry.id), REQUIRED_BLOCKERS)),
      value("open_blockers_non_release", (plan.openBlockers || []).every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false)),
      ...REQUIRED_BLOCKERS.map((id) => token(files.planDoc, id)),
      ...REQUIRED_BLOCKERS.map((id) => token(files.taskReport, id)),
    ]),
    scenario("docs_surface_and_gradle_wiring", "Phase 15 docs, surface manifest, and Gradle expose the P15-13 schema rollback gate", [
      value("surface_mapping_owner", p15Mapping.ownerSurface === "pro.tasks_device_media"),
      value("surface_mapping_backlog", sameSet(p15Mapping.sourceBacklogTasks || [], ["P14-18"])),
      value("surface_mapping_evidence_class", p15Mapping.evidenceClass === "migration_rollback_fixture"),
      value("surface_mapping_gate", p15Mapping.gate === ":app:verifyNodePhase15TaskSchemaRollback"),
      value("surface_mapping_planned", p15Mapping.status === "planned"),
      token(files.phase15Gradle, "verifyNodePhase15TaskSchemaRollback"),
      token(files.phase15Gradle, "verify-node-phase15-task-schema-rollback.js"),
      token(files.phase15Readme, ":app:verifyNodePhase15TaskSchemaRollback"),
      token(files.testing, ":app:verifyNodePhase15TaskSchemaRollback"),
      token(files.ciGates, ":app:verifyNodePhase15TaskSchemaRollback"),
      token(files.phase15Tasks, "phase15-task-schema-rollback.json"),
      token(files.phase15Tasks, "task_schema_backup_restore_archive"),
    ]),
  ];
  const checks = [
    check(
      "plan-schema",
      plan.schema === "autojs6-node-phase15-task-schema-rollback-plan-v1" &&
        plan.task === "P15-13" &&
        sameSet(plan.sourceBacklogTasks || [], ["P14-18"]) &&
        plan.providerId === "task_database" &&
        plan.status === "partial" &&
        plan.promotionState === "provider_poc_not_promoted" &&
        plan.runtimeAuthorityChanged === false,
      "P15-13 plan must stay partial and must not change runtime authority.",
    ),
    check(
      "ready-evidence-declared",
      sameSet((plan.readyEvidence || []).map((entry) => entry.id), REQUIRED_READY_EVIDENCE) &&
        REQUIRED_READY_EVIDENCE.every((id) => files.planDoc.text.includes(id)),
      "P15-13 plan and docs must declare the imported ready evidence set.",
    ),
    check(
      "fixture-matrix-covers-required-shapes",
      sameSet(fixtureIds, REQUIRED_FIXTURES) &&
        includesAll(fixtures.map((entry) => entry.inputKind), ["current", "older", "legacy_snapshot", "corrupt", "unknown", "owner_mismatch"]),
      "P15-13 fixture matrix must cover current, older, legacy, corrupt, unknown, and packaged owner-mismatch shapes.",
    ),
    check(
      "promotion-blockers-declared",
      sameSet((plan.openBlockers || []).map((entry) => entry.id), REQUIRED_BLOCKERS) &&
        plan.openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false) &&
        REQUIRED_BLOCKERS.every((id) => files.taskReport.text.includes(id)),
      "P15-13 must keep future-promotion blockers visible and non-release-blocking.",
    ),
    check(
      "phase14-schema-rollback-partial",
      statusOf(phase14Report) === "partial" &&
        p14Decision.schemaFixtureMatrixReady === true &&
        p14Decision.currentOlderCorruptUnknownFixturesReady === true &&
        p14Decision.proSurfacePromoted === false,
      "P15-13 requires P14-18 schema rollback report to be partial with live archives still open.",
    ),
    check(
      "phase15-task-database-partial",
      statusOf(p15TaskDatabaseReport) === "partial" &&
        p15TaskDatabaseDecision.taskDatabasePromoted === false &&
        p15TaskDatabaseDecision.runtimeAuthorityChanged === false,
      "P15-13 depends on the P15-12 task database gate remaining partial and unpromoted.",
    ),
    check(
      "runtime-boundary-still-closed",
      !files.protocol.text.includes('const val TASKS = "tasks"') &&
        !files.permissionManifest.text.includes('const val TASKS = "tasks"') &&
        !files.nodeBridgeSources.text.includes('if (name === "tasks")'),
      "P15-13 must not expose require(\"tasks\") or accept the future tasks capability yet.",
    ),
    check(
      "scenario-evidence-present",
      scenarios.every((entry) => entry.status === "ready"),
      "All P15-13 evidence scenarios must be ready while rollback archives remain explicit blockers.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase15Gradle.text.includes("verifyNodePhase15TaskSchemaRollback") &&
        files.phase15Gradle.text.includes("verify-node-phase15-task-schema-rollback.js") &&
        files.phase15Readme.text.includes(":app:verifyNodePhase15TaskSchemaRollback") &&
        files.testing.text.includes(":app:verifyNodePhase15TaskSchemaRollback") &&
        files.ciGates.text.includes(":app:verifyNodePhase15TaskSchemaRollback"),
      "Gradle, README, TESTING, and CI_GATES must reference the P15-13 task schema rollback gate.",
    ),
    check(
      "task-plan-partial",
      files.phase15Tasks.text.includes("- [ ] P15-13: Close task schema migration and rollback evidence.") &&
        files.phase15Tasks.text.includes("phase15-task-schema-rollback.json") &&
        files.phase15Tasks.text.includes("task_schema_live_migration_tests_archive"),
      "P15-13 must remain unchecked while documenting the partial gate and output report.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-task-schema-rollback-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase15TaskSchemaRollback",
    task: "P15-13",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    schemaContract,
    rollbackContract,
    scenarios,
    checks,
    policyViolations,
    blockers: (plan.openBlockers || []).map((entry) => ({ ...entry, status: "open" })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc_not_promoted",
      p15TaskComplete: false,
      phase14SchemaContractReady: scenarioById(scenarios, "phase14_schema_rollback_imported").status === "ready",
      taskDatabaseDependencyReady: scenarioById(scenarios, "phase15_task_database_dependency").status === "ready",
      fixtureMatrixReady: scenarioById(scenarios, "schema_contract_boundary").status === "ready",
      requiredArchiveEvidenceReady: false,
      runtimeAuthorityChanged: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: Array.isArray(plan.openBlockers) ? plan.openBlockers.length : 0,
      taskSchemaRollbackPromoted: false,
    },
  };
}

function scenario(id, title, checks) {
  const missing = checks.filter((entry) => !entry.present).map((entry) => `missing evidence token: ${entry.token}`);
  return {
    id,
    title,
    status: missing.length === 0 ? "ready" : "missing_evidence",
    checks,
    remaining: missing,
  };
}

function token(file, tokenValue) {
  return {
    file: file.relative,
    token: tokenValue,
    present: file.exists && file.text.includes(tokenValue),
  };
}

function value(tokenName, present) {
  return {
    file: "parsed-json",
    token: tokenName,
    present: Boolean(present),
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 15 Task Schema Rollback",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    `- Future promotion blockers: ${report.decision.futurePromotionBlockers}`,
    `- Policy violations: ${report.policyViolations.length}`,
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
  lines.push("", "## Open Blockers", "", "| Blocker | Classification | Required Evidence |", "| --- | --- | --- |");
  report.blockers.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | \`${entry.classification}\` | ${escapeTable(entry.requiredEvidence)} |`);
  });
  return lines.join("\n") + "\n";
}

function sameSet(actual, expected) {
  if (!Array.isArray(actual) || actual.length !== expected.length) {
    return false;
  }
  const actualSorted = actual.slice().sort();
  const expectedSorted = expected.slice().sort();
  return expectedSorted.every((entry, index) => actualSorted[index] === entry);
}

function includesAll(values, requiredValues) {
  if (Array.isArray(values)) {
    return requiredValues.every((entry) => values.includes(entry));
  }
  return requiredValues.every((entry) => String(values).includes(entry));
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing" };
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function statusOf(report) {
  return report.status || (report.decision && report.decision.status) || "missing";
}

function parseJson(text) {
  if (!text || !text.trim()) {
    return {};
  }
  try {
    return JSON.parse(text);
  } catch {
    return {};
  }
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
  } catch {
    return "";
  }
}

function escapeTable(value) {
  return String(value || "").replace(/\|/g, "\\|").replace(/\r?\n/g, "<br>");
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    } else if (arg === "-h" || arg === "--help") {
      console.log("Usage: node verify-node-phase15-task-schema-rollback.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
