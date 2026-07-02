#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-packaged-task-smoke";
const REQUIRED_READY_EVIDENCE = Object.freeze([
  "phase14_packaged_task_scheduling_contract",
  "phase15_task_database_dependency_gate",
  "phase15_task_schema_dependency_gate",
  "phase15_promotion_contracts",
  "phase15_disclosure_rollback_contract",
  "phase15_surface_manifest_mapping",
]);
const REQUIRED_SMOKE_ROWS = Object.freeze([
  "packaged_work_manager_fake_schedule_list_cancel",
  "live_work_manager_packaged_project_entry",
  "future_tasks_database_add_list_enable_disable_remove",
  "future_process_death_relaunch_task_removal",
  "future_packaged_task_disclosure_stop_action_safe_downgrade",
  "future_packaged_task_denial_matrix",
]);
const REQUIRED_BLOCKERS = Object.freeze([
  "packaged_task_database_positive_smoke_archive",
  "packaged_task_process_death_relaunch_archive",
  "packaged_task_removal_cleanup_archive",
  "packaged_task_disclosure_safe_downgrade_archive",
  "packaged_task_cross_package_denial_archive",
  "packaged_task_missing_capability_denial_archive",
  "packaged_task_safe_downgrade_archive",
  "phase15_task_device_media_doc_sync",
]);

const FILES = {
  plan: "docs/nodejs/phase15/packaged-task-smoke-plan.json",
  planDoc: "docs/nodejs/phase15/PHASE15_PACKAGED_TASK_SMOKE.md",
  taskReport: "docs/nodejs/reports/phase15/P15_14_PACKAGED_TASK_SMOKE.md",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  promotionContracts: "docs/nodejs/phase15/node-v1-3-promotion-contracts.json",
  promotionReport: "build/reports/nodejs/phase15-promotion-contracts.json",
  disclosureAudit: "docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json",
  disclosureReport: "build/reports/nodejs/phase15-disclosure-rollback-audit.json",
  p15TaskDatabaseReport: "build/reports/nodejs/phase15-task-database-promotion.json",
  p15TaskSchemaReport: "build/reports/nodejs/phase15-task-schema-rollback.json",
  phase14Plan: "docs/nodejs/phase14/packaged-task-scheduling-smoke-plan.json",
  phase14PlanDoc: "docs/nodejs/phase14/PHASE14_PACKAGED_TASK_SCHEDULING_SMOKE.md",
  phase14TaskReport: "docs/nodejs/reports/phase14/P14_19_PACKAGED_TASK_SCHEDULING_SMOKE.md",
  phase14Generated: "build/reports/nodejs/phase14-packaged-task-scheduling-smoke.json",
  p14TaskDatabaseGenerated: "build/reports/nodejs/phase14-task-database-provider.json",
  p14SchemaGenerated: "build/reports/nodejs/phase14-task-schema-migration-rollback.json",
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
  console.log("Phase 15 packaged task smoke: " + report.decision.status);
  console.log("Phase 15 packaged task blockers: " + report.blockers.length);
  console.log("Phase 15 packaged task policy violations: " + report.policyViolations.length);
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
  const p15TaskSchemaReport = parseJson(files.p15TaskSchemaReport.text);
  const p14TaskDatabaseReport = parseJson(files.p14TaskDatabaseGenerated.text);
  const p14SchemaReport = parseJson(files.p14SchemaGenerated.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContracts.text);
  const promotionReport = parseJson(files.promotionReport.text);
  const disclosureAudit = parseJson(files.disclosureAudit.text);
  const disclosureReport = parseJson(files.disclosureReport.text);
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-14") || {};
  const taskDatabaseContract = (contracts.providerPromotionBars || []).find((entry) => entry.id === "task_database") || {};
  const taskDatabaseAudit = (disclosureAudit.providerAudits || []).find((entry) => entry.providerId === "task_database") || {};
  const smokeContract = plan.smokeContract || {};
  const ownershipContract = plan.ownershipContract || {};
  const smokeMatrix = Array.isArray(plan.smokeMatrix) ? plan.smokeMatrix : [];
  const smokeRowIds = smokeMatrix.map((entry) => entry.id);
  const p14Decision = phase14Report.decision || {};
  const p15TaskDatabaseDecision = p15TaskDatabaseReport.decision || {};
  const p15TaskSchemaDecision = p15TaskSchemaReport.decision || {};
  const p14TaskDatabaseDecision = p14TaskDatabaseReport.decision || {};
  const p14SchemaDecision = p14SchemaReport.decision || {};
  const scenarios = [
    scenario("phase14_packaged_task_smoke_imported", "P14-19 packaged work_manager smoke and live project-entry anchor are imported", [
      value("phase14_plan_schema", phase14Plan.schema === "autojs6-node-phase14-packaged-task-scheduling-smoke-plan-v1"),
      value("phase14_report_status", statusOf(phase14Report) === "partial"),
      value("phase14_fake_smoke_imported", p14Decision.packagedWorkManagerFakeSmokeImported === true),
      value("phase14_live_project_entry_imported", p14Decision.livePackagedProjectEntryImported === true),
      value("phase14_upstream_reports_imported", p14Decision.upstreamTaskDatabaseReportsImported === true),
      value("phase14_future_blockers", p14Decision.futurePromotionBlockers === 4),
      value("phase14_not_promoted", p14Decision.proSurfacePromoted === false),
      token(files.phase14TaskReport, "P14-19 deploys the packaged task scheduling smoke evidence gate."),
      token(files.phase14PlanDoc, "`packaged_work_manager_fake_schedule_list_cancel`"),
      token(files.phase14PlanDoc, "`future_process_death_relaunch_task_removal`"),
    ]),
    scenario("phase15_dependencies_partial", "P15-12 task database and P15-13 schema rollback dependencies remain partial and unpromoted", [
      value("p15_task_database_status", statusOf(p15TaskDatabaseReport) === "partial"),
      value("p15_task_database_not_promoted", p15TaskDatabaseDecision.taskDatabasePromoted === false),
      value("p15_task_database_runtime_authority", p15TaskDatabaseDecision.runtimeAuthorityChanged === false),
      value("p15_task_schema_status", statusOf(p15TaskSchemaReport) === "partial"),
      value("p15_task_schema_not_promoted", p15TaskSchemaDecision.taskSchemaRollbackPromoted === false),
      value("p15_task_schema_runtime_authority", p15TaskSchemaDecision.runtimeAuthorityChanged === false),
      value("p14_task_database_imported", statusOf(p14TaskDatabaseReport) === "partial" && p14TaskDatabaseDecision.jsonSafeContractReady === true),
      value("p14_schema_imported", statusOf(p14SchemaReport) === "partial" && p14SchemaDecision.schemaFixtureMatrixReady === true),
      token(files.p15TaskDatabaseReport, "\"taskDatabasePromoted\": false"),
      token(files.p15TaskSchemaReport, "\"taskSchemaRollbackPromoted\": false"),
    ]),
    scenario("packaged_smoke_boundary", "P15-14 imports current schedule/list/cancel evidence without exposing the future tasks provider", [
      value("current_module", smokeContract.currentModule === "work_manager"),
      value("future_provider", smokeContract.futureProvider === "tasks"),
      value("work_manager_operations", sameSet(smokeContract.workManagerOperationsImported || [], ["scheduleOnce", "list", "cancel"])),
      value("packaged_rows_not_promoted", smokeContract.packagedTaskRowsPromoted === false),
      value("relaunch_not_promoted", smokeContract.processDeathRelaunchPromoted === false),
      value("task_removal_not_promoted", smokeContract.taskRemovalCleanupPromoted === false),
      value("safe_downgrade_not_promoted", smokeContract.safeDowngradePromoted === false),
      value("ownership_requires_package_identity", ownershipContract.packageIdentityRequired === true),
      value("ownership_denies_alias", ownershipContract.packagedRowsMainAppAliasAllowed === false),
      token(files.packagedSmokeTest, 'const work = require("work_manager");'),
      token(files.packagedSmokeTest, "const workId = await work.scheduleOnce({"),
      token(files.packagedSmokeTest, "const workBefore = await work.list({ timeoutMs: 1000 });"),
      token(files.packagedSmokeTest, "await work.cancel(workId, { timeoutMs: 1000 });"),
      token(files.packagedSmokeTest, 'workId === "packaged-work-1"'),
      token(files.workManagerTest, "androidWorkManagerLiveProviderExecutesPackagedProjectEntryAndPersistsResult"),
      token(files.workManagerTest, '"entry":"main.cjs","packaged":true'),
      token(files.workManagerTest, "scheduled packaged Node task should execute and persist result"),
      token(files.workManagerTest, 'assertEquals("projectEntry", task.getString("entryKind"))'),
      token(files.providerRegistry, "\"packagedTaskSchedulingSmokePolicy\" to \"packaged_work_manager_fake_smoke_and_live_project_entry_anchor_task_database_rows_not_promoted\""),
      value("tasks_module_not_exposed", !files.protocol.text.includes('const val TASKS = "tasks"') && !files.nodeBridgeSources.text.includes('if (name === "tasks")')),
      value("future_tasks_capability_not_accepted", !files.permissionManifest.text.includes('const val TASKS = "tasks"') && !files.permissionManifest.text.includes("TASKS_INTENT")),
    ]),
    scenario("phase15_contracts_and_rollback", "Phase 15 contracts keep packaged task smoke behind disclosure, rollback, denial, type, security, and packaged bars", [
      value("promotion_report_ready", statusOf(promotionReport) === "ready"),
      value("disclosure_report_ready", statusOf(disclosureReport) === "ready"),
      value("task_contract_provider_poc", taskDatabaseContract.currentState === "provider_poc"),
      value("task_contract_p15_task", includesAll(taskDatabaseContract.p15Tasks || [], ["P15-14"])),
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
      value("task_audit_disclosure_fields", includesAll(taskDatabaseAudit.requiredDisclosureFields || [], [
        "foregroundNotificationOwnership",
        "stopAction",
        "taskRemovalCleanup",
        "relaunchBehavior",
        "crashRecoveryBehavior",
        "safeDowngradeDisclosure",
      ])),
    ]),
    scenario("promotion_blockers_open", "Packaged positive smoke, relaunch, removal cleanup, denial, Safe downgrade, and docs sync remain future work", [
      value("open_blockers", sameSet((plan.openBlockers || []).map((entry) => entry.id), REQUIRED_BLOCKERS)),
      value("open_blockers_non_release", (plan.openBlockers || []).every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false)),
      ...REQUIRED_BLOCKERS.map((id) => token(files.planDoc, id)),
      ...REQUIRED_BLOCKERS.map((id) => token(files.taskReport, id)),
    ]),
    scenario("docs_surface_and_gradle_wiring", "Phase 15 docs, surface manifest, and Gradle expose the P15-14 packaged task smoke gate", [
      value("surface_mapping_owner", p15Mapping.ownerSurface === "pro.tasks_device_media"),
      value("surface_mapping_backlog", sameSet(p15Mapping.sourceBacklogTasks || [], ["P14-19"])),
      value("surface_mapping_evidence_class", p15Mapping.evidenceClass === "packaged_smoke_archive"),
      value("surface_mapping_gate", p15Mapping.gate === ":app:installAndRunNodeV13PackagedTaskSmoke"),
      value("surface_mapping_planned", p15Mapping.status === "planned"),
      token(files.phase15Gradle, "installAndRunNodeV13PackagedTaskSmoke"),
      token(files.phase15Gradle, "verify-node-phase15-packaged-task-smoke.js"),
      token(files.phase15Readme, ":app:installAndRunNodeV13PackagedTaskSmoke"),
      token(files.testing, ":app:installAndRunNodeV13PackagedTaskSmoke"),
      token(files.ciGates, ":app:installAndRunNodeV13PackagedTaskSmoke"),
      token(files.phase15Tasks, "phase15-packaged-task-smoke.json"),
      token(files.phase15Tasks, "packaged_task_missing_capability_denial_archive"),
    ]),
  ];
  const checks = [
    check(
      "plan-schema",
      plan.schema === "autojs6-node-phase15-packaged-task-smoke-plan-v1" &&
        plan.task === "P15-14" &&
        sameSet(plan.sourceBacklogTasks || [], ["P14-19"]) &&
        plan.providerId === "task_database" &&
        plan.status === "partial" &&
        plan.promotionState === "provider_poc_not_promoted" &&
        plan.runtimeAuthorityChanged === false,
      "P15-14 plan must stay partial and must not change runtime authority.",
    ),
    check(
      "ready-evidence-declared",
      sameSet((plan.readyEvidence || []).map((entry) => entry.id), REQUIRED_READY_EVIDENCE) &&
        REQUIRED_READY_EVIDENCE.every((id) => files.planDoc.text.includes(id)),
      "P15-14 plan and docs must declare the imported ready evidence set.",
    ),
    check(
      "smoke-matrix-covers-required-rows",
      sameSet(smokeRowIds, REQUIRED_SMOKE_ROWS) &&
        includesAll(smokeMatrix.map((entry) => entry.status), ["imported", "future_promotion"]),
      "P15-14 smoke matrix must cover current packaged work_manager import, live packaged entry, future task DB rows, relaunch/removal, disclosure/downgrade, and denial.",
    ),
    check(
      "promotion-blockers-declared",
      sameSet((plan.openBlockers || []).map((entry) => entry.id), REQUIRED_BLOCKERS) &&
        plan.openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false) &&
        REQUIRED_BLOCKERS.every((id) => files.taskReport.text.includes(id)),
      "P15-14 must keep future-promotion blockers visible and non-release-blocking.",
    ),
    check(
      "phase14-packaged-task-smoke-partial",
      statusOf(phase14Report) === "partial" &&
        p14Decision.packagedWorkManagerFakeSmokeImported === true &&
        p14Decision.livePackagedProjectEntryImported === true &&
        p14Decision.proSurfacePromoted === false,
      "P15-14 requires P14-19 packaged task scheduling smoke to be partial with packaged task DB archives still open.",
    ),
    check(
      "phase15-task-dependencies-partial",
      statusOf(p15TaskDatabaseReport) === "partial" &&
        p15TaskDatabaseDecision.taskDatabasePromoted === false &&
        p15TaskDatabaseDecision.runtimeAuthorityChanged === false &&
        statusOf(p15TaskSchemaReport) === "partial" &&
        p15TaskSchemaDecision.taskSchemaRollbackPromoted === false &&
        p15TaskSchemaDecision.runtimeAuthorityChanged === false,
      "P15-14 depends on P15-12 and P15-13 remaining partial and unpromoted.",
    ),
    check(
      "runtime-boundary-still-closed",
      !files.protocol.text.includes('const val TASKS = "tasks"') &&
        !files.permissionManifest.text.includes('const val TASKS = "tasks"') &&
        !files.nodeBridgeSources.text.includes('if (name === "tasks")'),
      "P15-14 must not expose require(\"tasks\") or accept the future tasks capability yet.",
    ),
    check(
      "scenario-evidence-present",
      scenarios.every((entry) => entry.status === "ready"),
      "All P15-14 evidence scenarios must be ready while packaged task archives remain explicit blockers.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase15Gradle.text.includes("installAndRunNodeV13PackagedTaskSmoke") &&
        files.phase15Gradle.text.includes("verify-node-phase15-packaged-task-smoke.js") &&
        files.phase15Readme.text.includes(":app:installAndRunNodeV13PackagedTaskSmoke") &&
        files.testing.text.includes(":app:installAndRunNodeV13PackagedTaskSmoke") &&
        files.ciGates.text.includes(":app:installAndRunNodeV13PackagedTaskSmoke"),
      "Gradle, README, TESTING, and CI_GATES must reference the P15-14 packaged task smoke gate.",
    ),
    check(
      "task-plan-partial",
      files.phase15Tasks.text.includes("- [ ] P15-14: Add packaged task scheduling smoke.") &&
        files.phase15Tasks.text.includes("phase15-packaged-task-smoke.json") &&
        files.phase15Tasks.text.includes("packaged_task_database_positive_smoke_archive"),
      "P15-14 must remain unchecked while documenting the partial gate and output report.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-packaged-task-smoke-v1",
    generatedAt: new Date().toISOString(),
    gate: "installAndRunNodeV13PackagedTaskSmoke",
    task: "P15-14",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    smokeContract,
    ownershipContract,
    scenarios,
    checks,
    policyViolations,
    blockers: (plan.openBlockers || []).map((entry) => ({ ...entry, status: "open" })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc_not_promoted",
      p15TaskComplete: false,
      phase14PackagedTaskSmokeReady: scenarioById(scenarios, "phase14_packaged_task_smoke_imported").status === "ready",
      taskDatabaseDependencyReady: statusOf(p15TaskDatabaseReport) === "partial" && p15TaskDatabaseDecision.taskDatabasePromoted === false,
      taskSchemaDependencyReady: statusOf(p15TaskSchemaReport) === "partial" && p15TaskSchemaDecision.taskSchemaRollbackPromoted === false,
      packagedWorkManagerSmokeImported: true,
      livePackagedProjectEntryImported: true,
      requiredArchiveEvidenceReady: false,
      runtimeAuthorityChanged: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: Array.isArray(plan.openBlockers) ? plan.openBlockers.length : 0,
      packagedTaskSmokePromoted: false,
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
    "# Phase 15 Packaged Task Smoke",
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
      console.log("Usage: node verify-node-phase15-packaged-task-smoke.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
