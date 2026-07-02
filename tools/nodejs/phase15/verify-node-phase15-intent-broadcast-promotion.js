#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-intent-broadcast-promotion";
const REQUIRED_OPERATIONS = Object.freeze([
  "register",
  "list",
  "get",
  "enable",
  "disable",
  "remove",
  "validateManifestPatch",
]);
const REQUIRED_TRIGGER_TYPES = Object.freeze([
  "custom_local",
  "custom_package_private",
  "boot_completed",
  "user_unlocked",
  "package_event",
  "startup",
  "exact_alarm",
]);
const REQUIRED_DESCRIPTOR_FIELDS = Object.freeze([
  "schema",
  "descriptorVersion",
  "id",
  "owner",
  "entry",
  "backend",
  "trigger",
  "manifest",
  "payloadPolicy",
  "enabled",
  "createdAtEpochMs",
  "updatedAtEpochMs",
]);
const REQUIRED_READY_EVIDENCE = Object.freeze([
  "phase14_intent_broadcast_contract",
  "phase15_packaged_task_smoke_dependency_gate",
  "phase15_promotion_contracts",
  "phase15_disclosure_rollback_contract",
  "phase15_surface_manifest_mapping",
]);
const REQUIRED_BLOCKERS = Object.freeze([
  "intent_broadcast_live_provider_archive",
  "intent_broadcast_manifest_synthesis_archive",
  "intent_broadcast_exported_permission_policy_archive",
  "intent_broadcast_trigger_payload_schema_archive",
  "intent_broadcast_remove_cleanup_archive",
  "intent_broadcast_packaged_trigger_remove_smoke_archive",
  "intent_broadcast_cross_package_denial_archive",
  "intent_broadcast_safe_downgrade_archive",
  "phase15_task_device_media_doc_sync",
]);

const FILES = {
  plan: "docs/nodejs/phase15/intent-broadcast-promotion-plan.json",
  planDoc: "docs/nodejs/phase15/PHASE15_INTENT_BROADCAST_PROMOTION.md",
  taskReport: "docs/nodejs/reports/phase15/P15_15_INTENT_BROADCAST_PROMOTION.md",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  promotionContracts: "docs/nodejs/phase15/node-v1-3-promotion-contracts.json",
  promotionReport: "build/reports/nodejs/phase15-promotion-contracts.json",
  disclosureAudit: "docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json",
  disclosureReport: "build/reports/nodejs/phase15-disclosure-rollback-audit.json",
  p15PackagedTaskReport: "build/reports/nodejs/phase15-packaged-task-smoke.json",
  phase14Plan: "docs/nodejs/phase14/intent-broadcast-provider-plan.json",
  phase14PlanDoc: "docs/nodejs/phase14/PHASE14_INTENT_BROADCAST_PROVIDER.md",
  phase14TaskReport: "docs/nodejs/reports/phase14/P14_20_INTENT_BROADCAST_PROVIDER.md",
  phase14Generated: "build/reports/nodejs/phase14-intent-broadcast-provider.json",
  p13Generated: "build/reports/nodejs/phase13-intent-broadcast-provider.json",
  p13Report: "docs/nodejs/reports/phase13/P13_22_INTENT_BROADCAST_PROVIDER.md",
  p13AppIntentReport: "docs/nodejs/reports/phase13/P13_23_APP_INTENT_BROADCAST_PARITY.md",
  broadcastTasksDoc: "docs/nodejs/design/BROADCAST_TASKS.md",
  taskIntentDecisionDoc: "docs/nodejs/design/TASKS_INTENT_BROADCAST_COMPATIBILITY_DECISION.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  intentTaskSnapshots: "app/src/main/java/org/autojs/autojs/engine/NodeIntentTaskSnapshots.kt",
  tasksRhino: "app/src/main/java/org/autojs/autojs/runtime/api/augment/tasks/Tasks.kt",
  intentTask: "app/src/main/java/org/autojs/autojs/timing/IntentTask.java",
  intentTaskDatabase: "app/src/main/java/org/autojs/autojs/storage/database/IntentTaskDatabase.java",
  timedTaskManager: "app/src/main/java/org/autojs/autojs/timing/TimedTaskManager.kt",
  baseBroadcastReceiver: "app/src/main/java/org/autojs/autojs/external/receiver/BaseBroadcastReceiver.java",
  staticBroadcastReceiver: "app/src/main/java/org/autojs/autojs/external/receiver/StaticBroadcastReceiver.java",
  dynamicBroadcastReceivers: "app/src/main/java/org/autojs/autojs/external/receiver/DynamicBroadcastReceivers.kt",
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
  console.log("Phase 15 intent broadcast promotion: " + report.decision.status);
  console.log("Phase 15 intent broadcast blockers: " + report.blockers.length);
  console.log("Phase 15 intent broadcast policy violations: " + report.policyViolations.length);
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
  const p13Report = parseJson(files.p13Generated.text);
  const p15PackagedTaskReport = parseJson(files.p15PackagedTaskReport.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContracts.text);
  const promotionReport = parseJson(files.promotionReport.text);
  const disclosureAudit = parseJson(files.disclosureAudit.text);
  const disclosureReport = parseJson(files.disclosureReport.text);
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-15") || {};
  const intentBroadcastContract = (contracts.providerPromotionBars || []).find((entry) => entry.id === "intent_broadcast") || {};
  const intentBroadcastAudit = (disclosureAudit.providerAudits || []).find((entry) => entry.providerId === "intent_broadcast") || {};
  const provider = plan.providerContract || {};
  const descriptor = plan.descriptorContract || {};
  const manifestContract = plan.manifestSynthesisContract || {};
  const payload = plan.triggerPayloadContract || {};
  const removeBehavior = plan.removeBehavior || {};
  const phase14Decision = phase14Report.decision || {};
  const p15PackagedTaskDecision = p15PackagedTaskReport.decision || {};
  const scenarios = [
    scenario("phase14_intent_broadcast_contract_imported", "P14-20 JSON-safe IntentTask/broadcast provider contract is imported", [
      value("phase14_plan_schema", phase14Plan.schema === "autojs6-node-phase14-intent-broadcast-provider-plan-v1"),
      value("phase14_report_status", statusOf(phase14Report) === "partial"),
      value("phase14_descriptor_contract", phase14Decision.descriptorContractReady === true),
      value("phase14_manifest_policy", phase14Decision.manifestExportPolicyReady === true),
      value("phase14_payload_remove_policy", phase14Decision.triggerPayloadRemovePolicyReady === true),
      value("phase14_runtime_not_promoted", phase14Decision.runtimeTasksBroadcastPromoted === false),
      value("phase14_future_blockers", phase14Decision.futurePromotionBlockers === 5),
      token(files.phase14TaskReport, "P14-20 deploys the JSON-safe IntentTask/broadcast provider contract gate"),
      token(files.phase14TaskReport, "`intent_broadcast_live_provider_archive`"),
      token(files.phase14TaskReport, "`intent_broadcast_manifest_synthesis_archive`"),
      token(files.phase14PlanDoc, "`autojs6-node-intent-broadcast-descriptor-v1`"),
      token(files.phase14PlanDoc, "`autojs6-node-broadcast-trigger-payload-v1`"),
    ]),
    scenario("phase15_packaged_task_dependency", "P15-14 packaged task smoke dependency remains partial and unpromoted", [
      value("p15_packaged_task_status", statusOf(p15PackagedTaskReport) === "partial"),
      value("p15_packaged_task_not_promoted", p15PackagedTaskDecision.packagedTaskSmokePromoted === false),
      value("p15_packaged_task_runtime_authority", p15PackagedTaskDecision.runtimeAuthorityChanged === false),
      value("p15_packaged_task_ownership_ready", p15PackagedTaskDecision.phase14PackagedTaskSmokeReady === true),
      token(files.p15PackagedTaskReport, "\"packagedTaskSmokePromoted\": false"),
      token(files.p15PackagedTaskReport, "\"packaged_task_safe_downgrade_archive\""),
    ]),
    scenario("provider_descriptor_boundary", "P15-15 reserves JSON-safe provider descriptors without exposing tasks.broadcast", [
      value("future_provider", provider.futureProvider === "tasks.broadcast"),
      value("current_runtime_authority", provider.currentRuntimeAuthority === "not_exposed"),
      value("current_migration_facade", provider.currentMigrationFacade === "autojs6:compat.tasks"),
      value("descriptor_schema", provider.descriptorSchema === "autojs6-node-intent-broadcast-descriptor-v1"),
      value("operations", sameSet(provider.operations || [], REQUIRED_OPERATIONS)),
      value("trigger_types", sameSet(provider.triggerTypes || [], REQUIRED_TRIGGER_TYPES)),
      value("descriptor_fields", sameSet(descriptor.requiredFields || [], REQUIRED_DESCRIPTOR_FIELDS)),
      value("legacy_rows_mutable_false", descriptor.legacyRowsMutable === false),
      value("packaged_alias_denied", descriptor.packagedRowsMainAppAliasAllowed === false),
      value("stable_denials", includesAll(provider.stableDeniedCurrentMethods || [], [
        "require(\"tasks\")",
        "require(\"tasks.broadcast\")",
        "global tasks",
        "compat.tasks.addIntentTask",
        "compat.tasks.removeIntentTask",
      ])),
      token(files.intentTaskSnapshots, "autojs6-node-intent-task-snapshot-v1"),
      token(files.intentTaskSnapshots, "\"rawIntentTask\""),
      token(files.tasksRhino, "fun addIntentTask"),
      token(files.intentTask, "public IntentFilter getIntentFilter()"),
      token(files.intentTaskDatabase, "CREATE TABLE `\" + IntentTask.TABLE + \"`"),
      token(files.providerRegistry, "\"intentBroadcastDescriptorPolicy\" to \"provider_owned_json_descriptors_no_legacy_row_aliasing\""),
      value("tasks_broadcast_not_exposed", !files.protocol.text.includes('const val TASKS_BROADCAST = "tasks.broadcast"') && !files.nodeBridgeSources.text.includes('if (name === "tasks.broadcast")')),
      value("future_broadcast_capability_not_accepted", !files.permissionManifest.text.includes("TASKS_BROADCAST") && !files.permissionManifest.text.includes('"tasks.broadcast"')),
    ]),
    scenario("manifest_payload_remove_policy", "Manifest synthesis, exported receiver policy, trigger payloads, and remove cleanup stay explicit and fail closed", [
      value("dynamic_manifest_mutation_denied", manifestContract.dynamicManifestMutationFromNodeAllowed === false),
      value("packaged_patch_required", manifestContract.packagedPatchRequired === true),
      value("boot_permission_opt_in", manifestContract.bootPermissionOptInOnly === true),
      value("receive_boot_completed", manifestContract.receiveBootCompletedPermission === "android.permission.RECEIVE_BOOT_COMPLETED"),
      value("package_data_scheme", manifestContract.packageDataSchemeRequired === true && manifestContract.packageDataScheme === "package"),
      value("custom_receiver_non_exported", manifestContract.customReceiversDefaultExported === false),
      value("exported_default_denied", manifestContract.exportedReceiversDefaultAllowed === false),
      value("payload_schema", payload.schema === "autojs6-node-broadcast-trigger-payload-v1"),
      value("raw_intent_denied", payload.rawIntentAllowed === false),
      value("max_payload_bytes", payload.maxPayloadBytes === 8192),
      value("remove_owned_descriptor", removeBehavior.removeOwnedDescriptor === true),
      value("unregister_dynamic_receiver", removeBehavior.unregisterDynamicReceiver === true),
      value("cancel_pending_executions", removeBehavior.cancelPendingExecutions === true),
      value("preserve_legacy_rows", removeBehavior.preserveLegacyRhinoRows === true),
      value("deny_cross_package_remove", removeBehavior.denyCrossPackageRemove === true),
      token(files.broadcastTasksDoc, "No packaged APK should gain boot or package receivers merely because it bundles `libnode.so`."),
      token(files.broadcastTasksDoc, "keep custom broadcast receivers non-exported by default"),
      token(files.providerRegistry, "\"intentBroadcastManifestPolicy\" to \"packaged_manifest_synthesis_opt_in_non_exported_by_default_exported_denied\""),
      token(files.providerRegistry, "\"intentBroadcastPayloadPolicy\" to \"bounded_json_snapshots_no_raw_intent_bundle_uri_receiver_handles\""),
      token(files.packagedSmokeTest, "\"android.permission.RECEIVE_BOOT_COMPLETED\""),
    ]),
    scenario("p13_runtime_substrate_imported", "P13 receiver substrate, compat rejection, and packaged boundary evidence remain imported", [
      value("p13_generated_status", statusOf(p13Report) === "partial"),
      token(files.p13Report, "Android/Rhino runtime already has `IntentTask`"),
      token(files.p13Report, "compat.tasks.addIntentTask()"),
      token(files.p13Report, "JSON snapshot boundary: `NodeIntentTaskSnapshots`"),
      token(files.p13Report, "packaged `compat.tasks` IntentTask methods still reject before bridge"),
      token(files.packagedSmokeTest, "installAndRunIntentBroadcastBoundaryEmbeddedNodePackagedApk"),
      token(files.packagedSmokeTest, "packaged.intent_broadcast.future_modules_denied=true"),
      token(files.baseBroadcastReceiver, "ScriptIntents.prepareLaunch"),
      token(files.staticBroadcastReceiver, "android.intent.action.BOOT_COMPLETED"),
      token(files.dynamicBroadcastReceivers, "fun registerIntent(task: IntentTask)"),
    ]),
    scenario("phase15_contracts_and_rollback", "Phase 15 contracts keep intent_broadcast behind disclosure, rollback, denial, type, security, packaged, and docs bars", [
      value("promotion_report_ready", statusOf(promotionReport) === "ready"),
      value("disclosure_report_ready", statusOf(disclosureReport) === "ready"),
      value("intent_contract_provider_poc", intentBroadcastContract.currentState === "provider_poc"),
      value("intent_contract_p15_task", includesAll(intentBroadcastContract.p15Tasks || [], ["P15-15"])),
      value("intent_contract_backlog", includesAll(intentBroadcastContract.sourceBacklogTasks || [], ["P14-20"])),
      value("intent_contract_evidence_bars", includesAll(intentBroadcastContract.requiredEvidence || [], [
        "implementation_gate",
        "connected_device_evidence",
        "packaged_apk_evidence",
        "user_visible_disclosure",
        "rollback_disable_path",
        "safe_downgrade_behavior",
        "raw_object_denial_regression",
      ])),
      value("intent_audit_provider_poc", intentBroadcastAudit.currentState === "provider_poc"),
      value("intent_audit_disable_flag", intentBroadcastAudit.disableFlag === "node.provider.intent_broadcast.enabled"),
      value("intent_audit_diagnostic", intentBroadcastAudit.diagnosticCode === "NODE_PROVIDER_INTENT_BROADCAST_DISABLED"),
      value("intent_audit_safe_downgrade", intentBroadcastAudit.safeDowngrade === "remove package-owned receivers and deny broadcast trigger authority"),
      value("intent_audit_disclosure_fields", includesAll(intentBroadcastAudit.requiredDisclosureFields || [], [
        "manifestMetadata",
        "foregroundNotificationOwnership",
        "stopAction",
        "taskRemovalCleanup",
        "safeDowngradeDisclosure",
      ])),
    ]),
    scenario("promotion_blockers_open", "Live provider, manifest, exported policy, payload, remove cleanup, packaged smoke, denial, Safe downgrade, and docs sync remain future work", [
      value("open_blockers", sameSet((plan.openBlockers || []).map((entry) => entry.id), REQUIRED_BLOCKERS)),
      value("open_blockers_non_release", (plan.openBlockers || []).every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false)),
      ...REQUIRED_BLOCKERS.map((id) => token(files.planDoc, id)),
      ...REQUIRED_BLOCKERS.map((id) => token(files.taskReport, id)),
    ]),
    scenario("docs_surface_and_gradle_wiring", "Phase 15 docs, surface manifest, and Gradle expose the P15-15 intent broadcast promotion gate", [
      value("surface_mapping_owner", p15Mapping.ownerSurface === "pro.tasks_device_media"),
      value("surface_mapping_backlog", sameSet(p15Mapping.sourceBacklogTasks || [], ["P14-20"])),
      value("surface_mapping_evidence_class", p15Mapping.evidenceClass === "provider_manifest_archive"),
      value("surface_mapping_gate", p15Mapping.gate === ":app:verifyNodePhase15IntentBroadcastPromotion"),
      value("surface_mapping_planned", p15Mapping.status === "planned"),
      token(files.phase15Gradle, "verifyNodePhase15IntentBroadcastPromotion"),
      token(files.phase15Gradle, "verify-node-phase15-intent-broadcast-promotion.js"),
      token(files.phase15Readme, ":app:verifyNodePhase15IntentBroadcastPromotion"),
      token(files.testing, ":app:verifyNodePhase15IntentBroadcastPromotion"),
      token(files.ciGates, ":app:verifyNodePhase15IntentBroadcastPromotion"),
      token(files.phase15Tasks, "phase15-intent-broadcast-promotion.json"),
      token(files.phase15Tasks, "intent_broadcast_safe_downgrade_archive"),
    ]),
  ];
  const checks = [
    check(
      "plan-schema",
      plan.schema === "autojs6-node-phase15-intent-broadcast-promotion-plan-v1" &&
        plan.task === "P15-15" &&
        sameSet(plan.sourceBacklogTasks || [], ["P14-20"]) &&
        plan.providerId === "intent_broadcast" &&
        plan.status === "partial" &&
        plan.promotionState === "provider_poc_not_promoted" &&
        plan.runtimeAuthorityChanged === false,
      "P15-15 plan must stay partial and must not change runtime authority.",
    ),
    check(
      "ready-evidence-declared",
      sameSet((plan.readyEvidence || []).map((entry) => entry.id), REQUIRED_READY_EVIDENCE) &&
        REQUIRED_READY_EVIDENCE.every((id) => files.planDoc.text.includes(id)),
      "P15-15 plan and docs must declare the imported ready evidence set.",
    ),
    check(
      "provider-descriptor-contract",
      provider.futureProvider === "tasks.broadcast" &&
        provider.currentRuntimeAuthority === "not_exposed" &&
        sameSet(provider.operations || [], REQUIRED_OPERATIONS) &&
        sameSet(provider.triggerTypes || [], REQUIRED_TRIGGER_TYPES) &&
        sameSet(descriptor.requiredFields || [], REQUIRED_DESCRIPTOR_FIELDS),
      "P15-15 provider descriptor contract must reserve JSON-safe operations and trigger types only.",
    ),
    check(
      "promotion-blockers-declared",
      sameSet((plan.openBlockers || []).map((entry) => entry.id), REQUIRED_BLOCKERS) &&
        plan.openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false) &&
        REQUIRED_BLOCKERS.every((id) => files.taskReport.text.includes(id)),
      "P15-15 must keep future-promotion blockers visible and non-release-blocking.",
    ),
    check(
      "phase14-intent-broadcast-partial",
      statusOf(phase14Report) === "partial" &&
        phase14Decision.descriptorContractReady === true &&
        phase14Decision.runtimeTasksBroadcastPromoted === false,
      "P15-15 requires P14-20 intent broadcast provider report to be partial with runtime authority closed.",
    ),
    check(
      "phase15-packaged-task-dependency-partial",
      statusOf(p15PackagedTaskReport) === "partial" &&
        p15PackagedTaskDecision.packagedTaskSmokePromoted === false &&
        p15PackagedTaskDecision.runtimeAuthorityChanged === false,
      "P15-15 depends on P15-14 packaged task smoke remaining partial and unpromoted.",
    ),
    check(
      "runtime-boundary-still-closed",
      !files.protocol.text.includes('const val TASKS_BROADCAST = "tasks.broadcast"') &&
        !files.permissionManifest.text.includes("TASKS_BROADCAST") &&
        !files.nodeBridgeSources.text.includes('if (name === "tasks.broadcast")') &&
        !files.nodeBridgeSources.text.includes("__autojs6_limited_tasks_broadcast"),
      "P15-15 must not expose require(\"tasks.broadcast\") or accept future broadcast capabilities yet.",
    ),
    check(
      "scenario-evidence-present",
      scenarios.every((entry) => entry.status === "ready"),
      "All P15-15 evidence scenarios must be ready while intent/broadcast archives remain explicit blockers.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase15Gradle.text.includes("verifyNodePhase15IntentBroadcastPromotion") &&
        files.phase15Gradle.text.includes("verify-node-phase15-intent-broadcast-promotion.js") &&
        files.phase15Readme.text.includes(":app:verifyNodePhase15IntentBroadcastPromotion") &&
        files.testing.text.includes(":app:verifyNodePhase15IntentBroadcastPromotion") &&
        files.ciGates.text.includes(":app:verifyNodePhase15IntentBroadcastPromotion"),
      "Gradle, README, TESTING, and CI_GATES must reference the P15-15 intent broadcast promotion gate.",
    ),
    check(
      "task-plan-partial",
      files.phase15Tasks.text.includes("- [ ] P15-15: Promote JSON-safe IntentTask and broadcast provider.") &&
        files.phase15Tasks.text.includes("phase15-intent-broadcast-promotion.json") &&
        files.phase15Tasks.text.includes("intent_broadcast_live_provider_archive"),
      "P15-15 must remain unchecked while documenting the partial gate and output report.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-intent-broadcast-promotion-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase15IntentBroadcastPromotion",
    task: "P15-15",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    providerContract: provider,
    descriptorContract: descriptor,
    manifestSynthesisContract: manifestContract,
    triggerPayloadContract: payload,
    removeBehavior,
    scenarios,
    checks,
    policyViolations,
    blockers: (plan.openBlockers || []).map((entry) => ({ ...entry, status: "open" })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc_not_promoted",
      p15TaskComplete: false,
      phase14IntentBroadcastContractReady: scenarioById(scenarios, "phase14_intent_broadcast_contract_imported").status === "ready",
      packagedTaskDependencyReady: statusOf(p15PackagedTaskReport) === "partial" && p15PackagedTaskDecision.packagedTaskSmokePromoted === false,
      descriptorContractReady: scenarioById(scenarios, "provider_descriptor_boundary").status === "ready",
      manifestPayloadRemovePolicyReady: scenarioById(scenarios, "manifest_payload_remove_policy").status === "ready",
      p13RuntimeSubstrateImported: scenarioById(scenarios, "p13_runtime_substrate_imported").status === "ready",
      liveProviderReady: false,
      packagedTriggerRemoveSmokeReady: false,
      requiredArchiveEvidenceReady: false,
      runtimeAuthorityChanged: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: Array.isArray(plan.openBlockers) ? plan.openBlockers.length : 0,
      intentBroadcastPromoted: false,
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
    "# Phase 15 Intent Broadcast Promotion",
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
      console.log("Usage: node verify-node-phase15-intent-broadcast-promotion.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
