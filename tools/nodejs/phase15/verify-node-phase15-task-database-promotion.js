#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-task-database-promotion";
const REQUIRED_READY_EVIDENCE = Object.freeze([
  "phase14_json_safe_task_database_contract",
  "phase13_work_manager_compat_tasks_import",
  "phase15_promotion_contracts",
  "phase15_disclosure_rollback_contract",
  "phase15_surface_manifest_mapping",
]);
const REQUIRED_BLOCKERS = Object.freeze([
  "task_database_live_mutation_provider_archive",
  "task_database_ownership_id_mapping_archive",
  "task_database_schema_migration_rollback_archive",
  "task_database_wall_clock_scheduler_archive",
  "packaged_task_database_mutation_smoke_archive",
  "task_database_cross_package_denial_archive",
  "task_database_disclosure_stop_action_archive",
  "task_database_safe_downgrade_archive",
  "phase15_task_device_media_doc_sync",
]);
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
const OPERATIONS = Object.freeze(["add", "update", "enable", "disable", "remove", "list", "get"]);

const FILES = {
  plan: "docs/nodejs/phase15/task-database-promotion-plan.json",
  planDoc: "docs/nodejs/phase15/PHASE15_TASK_DATABASE_PROMOTION.md",
  taskReport: "docs/nodejs/reports/phase15/P15_12_TASK_DATABASE_PROMOTION.md",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  promotionContracts: "docs/nodejs/phase15/node-v1-3-promotion-contracts.json",
  promotionReport: "build/reports/nodejs/phase15-promotion-contracts.json",
  disclosureAudit: "docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json",
  disclosureReport: "build/reports/nodejs/phase15-disclosure-rollback-audit.json",
  phase15DocSyncReport: "build/reports/nodejs/phase15-ui-automation-media-doc-sync.json",
  phase14Plan: "docs/nodejs/phase14/task-database-provider-plan.json",
  phase14PlanDoc: "docs/nodejs/phase14/PHASE14_TASK_DATABASE_PROVIDER.md",
  phase14TaskReport: "docs/nodejs/reports/phase14/P14_17_TASK_DATABASE_PROVIDER.md",
  phase14Generated: "build/reports/nodejs/phase14-task-database-provider.json",
  phase13Generated: "build/reports/nodejs/phase13-task-database-provider.json",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  timedTaskSnapshots: "app/src/main/java/org/autojs/autojs/engine/NodeTimedTaskSnapshots.kt",
  workManagerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt",
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  workManagerTypes: "docs/nodejs/types/autojs6-node/work_manager.d.ts",
  rhinoCompatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
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
  console.log("Phase 15 task database promotion: " + report.decision.status);
  console.log("Phase 15 task database blockers: " + report.blockers.length);
  console.log("Phase 15 task database policy violations: " + report.policyViolations.length);
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
  const phase13Report = parseJson(files.phase13Generated.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContracts.text);
  const promotionReport = parseJson(files.promotionReport.text);
  const disclosureAudit = parseJson(files.disclosureAudit.text);
  const disclosureReport = parseJson(files.disclosureReport.text);
  const p15DocSyncReport = parseJson(files.phase15DocSyncReport.text);
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-12") || {};
  const taskDatabaseContract = (contracts.providerPromotionBars || []).find((entry) => entry.id === "task_database") || {};
  const taskDatabaseAudit = (disclosureAudit.providerAudits || []).find((entry) => entry.providerId === "task_database") || {};
  const operationContract = plan.operationContract || {};
  const ownership = operationContract.ownershipMapping || {};
  const p14Decision = phase14Report.decision || {};
  const p13Decision = phase13Report.decision || {};
  const scenarios = [
    scenario("phase14_task_database_imported", "P14-17 JSON-safe task database contract and P13 work_manager evidence are imported", [
      value("phase14_plan_schema", phase14Plan.schema === "autojs6-node-phase14-task-database-provider-plan-v1"),
      value("phase14_report_status", statusOf(phase14Report) === "partial"),
      value("phase14_contract_ready", p14Decision.jsonSafeContractReady === true),
      value("phase14_not_promoted", p14Decision.proSurfacePromoted === false),
      value("phase14_future_blockers", p14Decision.futurePromotionBlockers === 5),
      value("phase13_status", statusOf(phase13Report) === "partial"),
      value("phase13_snapshots_ready", p13Decision.timedTaskSnapshotsReady === true),
      token(files.phase14TaskReport, "P14-17 deploys the JSON-safe task database mutation provider contract gate."),
      token(files.phase14PlanDoc, "Node task ids, legacy `TimedTask` row ids, and packaged task ids are separate namespaces"),
      token(files.timedTaskSnapshots, "autojs6-node-timed-task-snapshot-v1"),
      token(files.workManagerTest, "timedTaskSnapshotsStayJsonOnlyWithoutSchedulerHandles"),
    ]),
    scenario("operation_contract_boundary", "P15-12 keeps task database mutation JSON-safe and raw-object denied", [
      value("provider_id", plan.providerId === "task_database"),
      value("operation_contract", operationContract.providerName === "tasks" && operationContract.currentRuntimeAuthority === "not_exposed"),
      value("operations", sameSet(operationContract.operations || [], OPERATIONS)),
      value("raw_denials", includesAll(operationContract.rawObjectsDenied || [], RAW_DENIALS)),
      value("ownership_ids", sameSet(ownership.idNamespaces || [], ["nodeTaskId", "legacyTimedTaskId", "packagedTaskId"])),
      value("legacy_ids_immutable", ownership.legacyTimedTaskIdsMutable === false),
      value("packaged_alias_denied", ownership.packagedRowsMainAppAliasAllowed === false),
      token(files.providerRegistry, "\"taskDatabaseMutationPromotion\" to \"blocked_until_live_json_safe_database_mutation_provider_schema_migration_packaged_row_ownership_and_scheduler_reschedule_evidence\""),
      token(files.nodeBridgeSources, "P12-17 has no JSON patch/update provider for the AutoJs6 user task database"),
      token(files.workManagerTypes, "declare module \"work_manager\""),
      token(files.rhinoCompatTypes, "addDailyTask(options?: unknown): Promise<never>;"),
      value("tasks_module_not_exposed", !files.protocol.text.includes('const val TASKS = "tasks"') && !files.nodeBridgeSources.text.includes('if (name === "tasks")')),
      value("future_tasks_capability_not_accepted", !files.permissionManifest.text.includes('const val TASKS = "tasks"') && !files.permissionManifest.text.includes("TASKS_INTENT")),
    ]),
    scenario("phase15_contracts_and_rollback", "Phase 15 contracts keep task_database behind implementation, packaged, disclosure, rollback, type, and security bars", [
      value("promotion_report_ready", statusOf(promotionReport) === "ready"),
      value("disclosure_report_ready", statusOf(disclosureReport) === "ready"),
      value("task_contract_provider_poc", taskDatabaseContract.currentState === "provider_poc"),
      value("task_contract_p15_task", includesAll(taskDatabaseContract.p15Tasks || [], ["P15-12"])),
      value("task_contract_evidence_bars", includesAll(taskDatabaseContract.requiredEvidence || [], [
        "implementation_gate",
        "connected_device_evidence",
        "packaged_apk_evidence",
        "user_visible_disclosure",
        "rollback_disable_path",
        "safe_downgrade_behavior",
        "raw_object_denial_regression",
      ])),
      value("task_audit_provider_poc", taskDatabaseAudit.currentState === "provider_poc"),
      value("task_audit_disable_flag", taskDatabaseAudit.disableFlag === "node.provider.task_database.enabled"),
      value("task_audit_diagnostic", taskDatabaseAudit.diagnosticCode === "NODE_PROVIDER_TASK_DATABASE_DISABLED"),
      value("task_audit_disclosure_stop", taskDatabaseAudit.foregroundNotificationRequired === true && taskDatabaseAudit.stopActionRequired === true),
      value("task_audit_crash_breaker", taskDatabaseAudit.crashCircuitBreakerRequired === true),
      value("doc_sync_ready", statusOf(p15DocSyncReport) === "ready"),
    ]),
    scenario("promotion_blockers_open", "Live mutation, schema rollback, packaged smoke, disclosure, Safe downgrade, and docs sync remain future work", [
      value("open_blockers", sameSet((plan.openBlockers || []).map((entry) => entry.id), REQUIRED_BLOCKERS)),
      value("open_blockers_non_release", (plan.openBlockers || []).every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false)),
      ...REQUIRED_BLOCKERS.map((id) => token(files.planDoc, id)),
      ...REQUIRED_BLOCKERS.map((id) => token(files.taskReport, id)),
    ]),
    scenario("docs_surface_and_gradle_wiring", "Phase 15 docs, surface manifest, and Gradle expose the P15-12 task database gate", [
      value("surface_mapping_owner", p15Mapping.ownerSurface === "pro.tasks_device_media"),
      value("surface_mapping_backlog", sameSet(p15Mapping.sourceBacklogTasks || [], ["P14-17"])),
      value("surface_mapping_evidence_class", p15Mapping.evidenceClass === "provider_mutation_archive"),
      value("surface_mapping_gate", p15Mapping.gate === ":app:verifyNodePhase15TaskDatabasePromotion"),
      value("surface_mapping_planned", p15Mapping.status === "planned"),
      token(files.phase15Gradle, "verifyNodePhase15TaskDatabasePromotion"),
      token(files.phase15Gradle, "verify-node-phase15-task-database-promotion.js"),
      token(files.phase15Readme, ":app:verifyNodePhase15TaskDatabasePromotion"),
      token(files.testing, ":app:verifyNodePhase15TaskDatabasePromotion"),
      token(files.ciGates, ":app:verifyNodePhase15TaskDatabasePromotion"),
      token(files.phase15Tasks, "phase15-task-database-promotion.json"),
      token(files.phase15Tasks, "task_database_cross_package_denial_archive"),
    ]),
  ];
  const checks = [
    check(
      "plan-schema",
      plan.schema === "autojs6-node-phase15-task-database-promotion-plan-v1" &&
        plan.task === "P15-12" &&
        sameSet(plan.sourceBacklogTasks || [], ["P14-17"]) &&
        plan.providerId === "task_database" &&
        plan.status === "partial" &&
        plan.promotionState === "provider_poc_not_promoted" &&
        plan.runtimeAuthorityChanged === false,
      "P15-12 plan must stay partial and must not change runtime authority.",
    ),
    check(
      "ready-evidence-declared",
      sameSet((plan.readyEvidence || []).map((entry) => entry.id), REQUIRED_READY_EVIDENCE) &&
        REQUIRED_READY_EVIDENCE.every((id) => files.planDoc.text.includes(id)),
      "P15-12 plan and docs must declare the imported ready evidence set.",
    ),
    check(
      "promotion-blockers-declared",
      sameSet((plan.openBlockers || []).map((entry) => entry.id), REQUIRED_BLOCKERS) &&
        plan.openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false) &&
        REQUIRED_BLOCKERS.every((id) => files.taskReport.text.includes(id)),
      "P15-12 must keep future-promotion blockers visible and non-release-blocking.",
    ),
    check(
      "phase14-task-database-partial",
      statusOf(phase14Report) === "partial" &&
        p14Decision.jsonSafeContractReady === true &&
        p14Decision.proSurfacePromoted === false &&
        p14Decision.futurePromotionBlockers === 5,
      "P15-12 requires P14-17 task database report to be partial with live mutation archives still open.",
    ),
    check(
      "phase15-contracts-ready",
      statusOf(promotionReport) === "ready" &&
        taskDatabaseContract.currentState === "provider_poc" &&
        includesAll(taskDatabaseContract.requiredEvidence || [], ["implementation_gate", "packaged_apk_evidence", "connected_device_evidence", "user_visible_disclosure"]),
      "P15-12 requires ready promotion contracts with task_database still provider_poc.",
    ),
    check(
      "phase15-disclosure-audit-ready",
      statusOf(disclosureReport) === "ready" &&
        taskDatabaseAudit.currentState === "provider_poc" &&
        taskDatabaseAudit.disableFlag === "node.provider.task_database.enabled" &&
        taskDatabaseAudit.diagnosticCode === "NODE_PROVIDER_TASK_DATABASE_DISABLED" &&
        taskDatabaseAudit.foregroundNotificationRequired === true &&
        taskDatabaseAudit.stopActionRequired === true,
      "P15-12 requires ready disclosure rollback audit for task_database.",
    ),
    check(
      "runtime-boundary-still-closed",
      !files.protocol.text.includes('const val TASKS = "tasks"') &&
        !files.permissionManifest.text.includes('const val TASKS = "tasks"') &&
        !files.nodeBridgeSources.text.includes('if (name === "tasks")'),
      "P15-12 must not expose require(\"tasks\") or accept the future tasks capability yet.",
    ),
    check(
      "scenario-evidence-present",
      scenarios.every((entry) => entry.status === "ready"),
      "All P15-12 evidence scenarios must be ready while promotion archives remain explicit blockers.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase15Gradle.text.includes("verifyNodePhase15TaskDatabasePromotion") &&
        files.phase15Gradle.text.includes("verify-node-phase15-task-database-promotion.js") &&
        files.phase15Readme.text.includes(":app:verifyNodePhase15TaskDatabasePromotion") &&
        files.testing.text.includes(":app:verifyNodePhase15TaskDatabasePromotion") &&
        files.ciGates.text.includes(":app:verifyNodePhase15TaskDatabasePromotion"),
      "Gradle, README, TESTING, and CI_GATES must reference the P15-12 task database promotion gate.",
    ),
    check(
      "task-plan-partial",
      files.phase15Tasks.text.includes("- [ ] P15-12: Promote JSON-safe task database mutations.") &&
        files.phase15Tasks.text.includes("phase15-task-database-promotion.json") &&
        files.phase15Tasks.text.includes("task_database_live_mutation_provider_archive"),
      "P15-12 must remain unchecked while documenting the partial gate and output report.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-task-database-promotion-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase15TaskDatabasePromotion",
    task: "P15-12",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    operationContract,
    scenarios,
    checks,
    policyViolations,
    blockers: (plan.openBlockers || []).map((entry) => ({ ...entry, status: "open" })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc_not_promoted",
      p15TaskComplete: false,
      phase14ContractReady: scenarioById(scenarios, "phase14_task_database_imported").status === "ready",
      operationContractReady: scenarioById(scenarios, "operation_contract_boundary").status === "ready",
      requiredArchiveEvidenceReady: false,
      runtimeAuthorityChanged: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: Array.isArray(plan.openBlockers) ? plan.openBlockers.length : 0,
      taskDatabasePromoted: false,
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
    "# Phase 15 Task Database Promotion",
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
      console.log("Usage: node verify-node-phase15-task-database-promotion.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
