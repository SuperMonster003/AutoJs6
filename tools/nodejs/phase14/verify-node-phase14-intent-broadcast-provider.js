#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-intent-broadcast-provider";
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
const OPEN_BLOCKERS = Object.freeze([
  {
    id: "intent_broadcast_live_provider_archive",
    classification: "future_promotion",
    requiredEvidence: "Live JSON-safe provider evidence for register/list/get/enable/disable/remove/validateManifestPatch without raw broadcast objects.",
  },
  {
    id: "intent_broadcast_manifest_synthesis_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged manifest synthesis evidence for opt-in receivers, boot permission, package data scheme, non-exported defaults, and deterministic patch reports.",
  },
  {
    id: "intent_broadcast_exported_permission_policy_archive",
    classification: "future_promotion",
    requiredEvidence: "Exported receiver denial/default evidence plus allowlist, permission, abuse-review, and Android 12/15+ matrix proof for any exported receiver promotion.",
  },
  {
    id: "intent_broadcast_trigger_payload_schema_archive",
    classification: "future_promotion",
    requiredEvidence: "Bounded JSON trigger payload snapshots, privacy filtering, raw Intent/Bundle/Uri denial, and receiver-thread non-execution evidence.",
  },
  {
    id: "intent_broadcast_remove_cleanup_archive",
    classification: "future_promotion",
    requiredEvidence: "Owned descriptor cleanup, dynamic receiver unregister, pending execution cancellation, packaged patch cleanup, and legacy Rhino row preservation evidence.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_20_INTENT_BROADCAST_PROVIDER.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/intent-broadcast-provider-plan.json",
  planDoc: "docs/nodejs/phase14/PHASE14_INTENT_BROADCAST_PROVIDER.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  promotionContractsJson: "docs/nodejs/phase14/phase14-promotion-contracts.json",
  p14TaskDatabaseGeneratedReport: "phase14-task-database-provider.json",
  p14SchemaGeneratedReport: "phase14-task-schema-migration-rollback.json",
  p14PackagedSchedulingGeneratedReport: "phase14-packaged-task-scheduling-smoke.json",
  p13GeneratedReport: "phase13-intent-broadcast-provider.json",
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
  console.log("Phase 14 intent broadcast provider: " + report.decision.status);
  console.log("Phase 14 intent broadcast blockers: " + report.blockers.length);
  console.log("Phase 14 intent broadcast policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot, reportDir) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const isGenerated = relative.endsWith(".json") && !relative.includes("/");
      const file = isGenerated ? path.join(reportDir, relative) : path.join(repoRoot, relative);
      return [
        key,
        {
          relative: path.relative(repoRoot, file).replace(/\\/g, "/"),
          exists: fs.existsSync(file),
          text: read(file),
        },
      ];
    }),
  );
}

function buildReport(files) {
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContractsJson.text);
  const plan = parseJson(files.planJson.text);
  const provider = plan.providerContract || {};
  const descriptor = plan.descriptorContract || {};
  const manifestContract = plan.manifestSynthesisContract || {};
  const payload = plan.triggerPayloadContract || {};
  const removeBehavior = plan.removeBehavior || {};
  const p13Generated = parseJson(files.p13GeneratedReport.text);
  const p14TaskDatabaseGenerated = parseJson(files.p14TaskDatabaseGeneratedReport.text);
  const p14SchemaGenerated = parseJson(files.p14SchemaGeneratedReport.text);
  const p14PackagedSchedulingGenerated = parseJson(files.p14PackagedSchedulingGeneratedReport.text);
  const proSurface = (manifest.surfaces || []).find((entry) => entry.id === "pro.tasks_device_media") || null;
  const requiredDisclosureFields = ((contracts.packagedHighPrivilegeDisclosure || {}).requiredFields || []);
  const scenarios = [
    scenario("provider_descriptor_contract", "P14-20 reserves JSON-safe broadcast descriptors without exposing runtime authority", [
      value("schema", plan.schema === "autojs6-node-phase14-intent-broadcast-provider-plan-v1"),
      value("task", plan.task === "P14-20"),
      value("status", plan.status === "partial"),
      value("promotionState", plan.promotionState === "provider_poc"),
      value("sourceGates", sameSet(plan.sourceGates || [], ["P13-22", "P13-23", "P14-17", "P14-18", "P14-19"])),
      value("futureProvider", provider.futureProvider === "tasks.broadcast"),
      value("currentRuntimeAuthority", provider.currentRuntimeAuthority === "not_exposed"),
      value("operations", sameSet(provider.operations || [], REQUIRED_OPERATIONS)),
      value("triggerTypes", sameSet(provider.triggerTypes || [], REQUIRED_TRIGGER_TYPES)),
      value("descriptorFields", sameSet(descriptor.requiredFields || [], REQUIRED_DESCRIPTOR_FIELDS)),
      value("legacyRowsMutableFalse", descriptor.legacyRowsMutable === false),
      value("mainAppAliasDenied", descriptor.packagedRowsMainAppAliasAllowed === false),
      token(files.planDoc, "`autojs6-node-intent-broadcast-descriptor-v1`"),
      token(files.phase14Report, "`validateManifestPatch`"),
    ]),
    scenario("p13_intent_broadcast_evidence_imported", "P13-22 receiver substrate, compat rejection, JSON snapshots, and packaged boundary remain imported", [
      value("p13GeneratedPresent", files.p13GeneratedReport.exists),
      value("p13GeneratedPartial", p13Generated.decision && p13Generated.decision.status === "partial"),
      token(files.p13Report, "Android/Rhino runtime already has `IntentTask`"),
      token(files.p13Report, "compat.tasks.addIntentTask()"),
      token(files.p13Report, "JSON snapshot boundary: `NodeIntentTaskSnapshots`"),
      token(files.p13Report, "packaged `compat.tasks` IntentTask methods still reject before bridge"),
      token(files.intentTaskSnapshots, "autojs6-node-intent-task-snapshot-v1"),
      token(files.intentTaskSnapshots, '"rawIntentTask"'),
      token(files.intentTaskSnapshots, '"rawReceiverHandle"'),
      token(files.tasksRhino, "fun addIntentTask"),
      token(files.intentTask, "public IntentFilter getIntentFilter()"),
      token(files.intentTaskDatabase, "CREATE TABLE `\" + IntentTask.TABLE + \"`"),
      token(files.timedTaskManager, "app.dynamicBroadcastReceivers.registerIntent(intentTask)"),
      token(files.baseBroadcastReceiver, "ScriptIntents.prepareLaunch"),
      token(files.staticBroadcastReceiver, "android.intent.action.BOOT_COMPLETED"),
      token(files.dynamicBroadcastReceivers, "fun registerIntent(task: IntentTask)"),
      token(files.packagedSmokeTest, "installAndRunIntentBroadcastBoundaryEmbeddedNodePackagedApk"),
      token(files.packagedSmokeTest, "packaged.intent_broadcast.future_modules_denied=true"),
    ]),
    scenario("upstream_phase14_task_contracts_imported", "P14-17, P14-18, and P14-19 remain upstream partial contracts for ownership and rollback", [
      value("p14TaskDatabaseGeneratedPresent", files.p14TaskDatabaseGeneratedReport.exists),
      value("p14SchemaGeneratedPresent", files.p14SchemaGeneratedReport.exists),
      value("p14PackagedSchedulingGeneratedPresent", files.p14PackagedSchedulingGeneratedReport.exists),
      value("p14TaskDatabasePartial", statusOf(p14TaskDatabaseGenerated) === "partial"),
      value("p14SchemaPartial", statusOf(p14SchemaGenerated) === "partial"),
      value("p14PackagedSchedulingPartial", statusOf(p14PackagedSchedulingGenerated) === "partial"),
      value("packagedSchedulingNotPromoted", (p14PackagedSchedulingGenerated.decision || {}).packagedTaskDatabasePositiveSmokeReady === false),
      token(files.planDoc, "P14-17 through P14-19"),
    ]),
    scenario("manifest_export_permission_policy", "Manifest synthesis and exported receiver policy stay explicit and fail closed", [
      value("dynamicMutationDenied", manifestContract.dynamicManifestMutationFromNodeAllowed === false),
      value("packagedPatchRequired", manifestContract.packagedPatchRequired === true),
      value("bootPermissionOptInOnly", manifestContract.bootPermissionOptInOnly === true),
      value("receiveBootCompletedPermission", manifestContract.receiveBootCompletedPermission === "android.permission.RECEIVE_BOOT_COMPLETED"),
      value("packageDataSchemeRequired", manifestContract.packageDataSchemeRequired === true),
      value("customReceiversDefaultExportedFalse", manifestContract.customReceiversDefaultExported === false),
      value("exportedReceiversDefaultDenied", manifestContract.exportedReceiversDefaultAllowed === false),
      value("disclosureFields", includesAll(requiredDisclosureFields, ["manifestMetadata", "taskRemovalCleanup", "rollbackBehavior"])),
      token(files.broadcastTasksDoc, "No packaged APK should gain boot or package receivers merely because it bundles `libnode.so`."),
      token(files.broadcastTasksDoc, "keep custom broadcast receivers non-exported by default"),
      token(files.packagedSmokeTest, '"android.permission.RECEIVE_BOOT_COMPLETED"'),
      token(files.phase14Report, "`intent_broadcast_manifest_synthesis_archive`"),
      token(files.phase14Report, "`intent_broadcast_exported_permission_policy_archive`"),
    ]),
    scenario("trigger_payload_and_remove_policy", "Trigger payloads and remove cleanup are JSON-only, bounded, and owner-scoped", [
      value("payloadSchema", payload.schema === "autojs6-node-broadcast-trigger-payload-v1"),
      value("rawIntentDenied", payload.rawIntentAllowed === false),
      value("maxPayloadBytes", payload.maxPayloadBytes === 8192),
      value("removeOwnedDescriptor", removeBehavior.removeOwnedDescriptor === true),
      value("unregisterDynamicReceiver", removeBehavior.unregisterDynamicReceiver === true),
      value("cancelPendingExecutions", removeBehavior.cancelPendingExecutions === true),
      value("preserveLegacyRhinoRows", removeBehavior.preserveLegacyRhinoRows === true),
      value("denyCrossPackageRemove", removeBehavior.denyCrossPackageRemove === true),
      token(files.planDoc, "return without running Node on the"),
      token(files.planDoc, "`autojs6-node-broadcast-trigger-payload-v1`"),
      token(files.phase14Report, "`intent_broadcast_trigger_payload_schema_archive`"),
      token(files.phase14Report, "`intent_broadcast_remove_cleanup_archive`"),
    ]),
    scenario("current_runtime_boundary", "P14-20 does not expose tasks.broadcast or accept future broadcast capabilities", [
      token(files.providerRegistry, '"phase14IntentBroadcastProviderPromotion" to "P14-20 JSON-safe IntentTask/broadcast provider contract gate"'),
      token(files.providerRegistry, '"intentBroadcastDescriptorPolicy" to "provider_owned_json_descriptors_no_legacy_row_aliasing"'),
      token(files.providerRegistry, '"intentBroadcastManifestPolicy" to "packaged_manifest_synthesis_opt_in_non_exported_by_default_exported_denied"'),
      token(files.providerRegistry, '"intentBroadcastPayloadPolicy" to "bounded_json_snapshots_no_raw_intent_bundle_uri_receiver_handles"'),
      value("tasksBroadcastNotExposed", !files.protocol.text.includes('const val TASKS = "tasks"') && !files.protocol.text.includes('const val TASKS_BROADCAST = "tasks.broadcast"') && !files.nodeBridgeSources.text.includes('if (name === "tasks.broadcast")') && !files.nodeBridgeSources.text.includes("__autojs6_limited_tasks_broadcast")),
      value("registerBroadcastTaskNotRuntime", !files.protocol.text.includes("registerBroadcastTask") && !files.nodeBridgeSources.text.includes("registerBroadcastTask")),
      value("futureCapabilitiesNotAccepted", !files.permissionManifest.text.includes('const val TASKS = "tasks"') && !files.permissionManifest.text.includes("TASKS_BROADCAST") && !files.permissionManifest.text.includes('"tasks.broadcast"')),
      token(files.securityModel, "P14-20 records the JSON-safe IntentTask/broadcast provider contract"),
      token(files.errorCatalog, "P14-20 IntentTask/broadcast denial sync"),
      token(files.compatibilityProfile, "P14-20 records the JSON-safe IntentTask/broadcast provider contract"),
    ]),
    scenario("surface_docs_and_gradle_wiring", "Phase 14 docs, manifest, and Gradle expose the P14-20 gate", [
      value("surfaceEvidence", proSurface &&
        proSurface.status === "provider_poc" &&
        (proSurface.tasks || []).includes("P14-20") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/intent-broadcast-provider-plan.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14IntentBroadcastProvider")),
      token(files.surfaceManifestDoc, "P14-20 adds the JSON-safe IntentTask/broadcast provider contract"),
      token(files.phase14Tasks, "IntentTask/broadcast provider contract gate deployed:"),
      token(files.phase14Readme, ":app:verifyNodePhase14IntentBroadcastProvider"),
      token(files.testing, ":app:verifyNodePhase14IntentBroadcastProvider"),
      token(files.ciGates, ":app:verifyNodePhase14IntentBroadcastProvider"),
      token(files.gradle14, "verifyNodePhase14IntentBroadcastProvider"),
      token(files.gradle14, "verify-node-phase14-intent-broadcast-provider.js"),
    ]),
    scenario("promotion_archives_open", "Intent/broadcast live provider, manifest, exported policy, payload, and remove archives remain future work", [
      ...OPEN_BLOCKERS.flatMap((entry) => [
        token(files.planDoc, entry.id),
        token(files.phase14Report, entry.id),
      ]),
    ], OPEN_BLOCKERS.map((entry) => entry.id)),
  ];
  const checks = [
    check("p13-generated-report-present", files.p13GeneratedReport.exists, "The P13-22 generated report must exist before P14-20 runs."),
    check("phase14-task-database-report-present", files.p14TaskDatabaseGeneratedReport.exists, "The P14-17 generated report must exist before P14-20 runs."),
    check("phase14-schema-report-present", files.p14SchemaGeneratedReport.exists, "The P14-18 generated report must exist before P14-20 runs."),
    check("phase14-packaged-scheduling-report-present", files.p14PackagedSchedulingGeneratedReport.exists, "The P14-19 generated report must exist before P14-20 runs."),
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase14-intent-broadcast-provider-plan-v1", "P14-20 plan JSON must exist."),
    check("plan-doc-present", files.planDoc.exists && files.planDoc.text.includes("Phase 14 Intent Broadcast Provider"), "P14-20 plan documentation must exist."),
    check(
      "surface-manifest-evidence",
      proSurface &&
        proSurface.status === "provider_poc" &&
        (proSurface.tasks || []).includes("P14-20") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/intent-broadcast-provider-plan.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14IntentBroadcastProvider"),
      "The pro.tasks_device_media surface must include the P14-20 evidence and Gradle gate.",
    ),
    check(
      "runtime-still-closed",
      !files.protocol.text.includes('const val TASKS = "tasks"') &&
        !files.nodeBridgeSources.text.includes('if (name === "tasks.broadcast")') &&
        !files.nodeBridgeSources.text.includes("__autojs6_limited_tasks_broadcast") &&
        !files.permissionManifest.text.includes("TASKS_BROADCAST"),
      "P14-20 must not expose tasks.broadcast runtime authority or accept future broadcast capabilities yet.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.ok).map((entry) => ({ id: entry.id, message: entry.message }));
  const blockers = OPEN_BLOCKERS.map((entry) => ({ ...entry }));
  return {
    schema: "autojs6-node-phase14-intent-broadcast-provider-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "partial" : "failed",
    task: "P14-20",
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
      p13IntentBroadcastEvidenceImported: scenarioById(scenarios, "p13_intent_broadcast_evidence_imported").status === "ready",
      upstreamTaskContractsImported: scenarioById(scenarios, "upstream_phase14_task_contracts_imported").status === "ready",
      descriptorContractReady: scenarioById(scenarios, "provider_descriptor_contract").status === "ready",
      manifestExportPolicyReady: scenarioById(scenarios, "manifest_export_permission_policy").status === "ready",
      triggerPayloadRemovePolicyReady: scenarioById(scenarios, "trigger_payload_and_remove_policy").status === "ready",
      liveProviderReady: false,
      packagedTriggerRemoveSmokeReady: false,
      runtimeTasksBroadcastPromoted: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: blockers.length,
      proSurfacePromoted: false,
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Intent Broadcast Provider",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- P14 task complete: \`${report.decision.p14TaskComplete}\``,
    `- P13 IntentTask/broadcast evidence imported: \`${report.decision.p13IntentBroadcastEvidenceImported}\``,
    `- Descriptor contract ready: \`${report.decision.descriptorContractReady}\``,
    `- Manifest/export policy ready: \`${report.decision.manifestExportPolicyReady}\``,
    `- Trigger payload/remove policy ready: \`${report.decision.triggerPayloadRemovePolicyReady}\``,
    `- Live provider ready: \`${report.decision.liveProviderReady}\``,
    `- Runtime tasks.broadcast promoted: \`${report.decision.runtimeTasksBroadcastPromoted}\``,
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

function value(id, ok) {
  return check(id, ok, `failed contract check: ${id}`);
}

function token(file, expected) {
  return check(`token:${file.relative}:${expected}`, file.exists && file.text.includes(expected), `missing evidence token: ${expected}`);
}

function statusOf(report) {
  return report.status || (report.decision || {}).status || "missing";
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
  return Array.isArray(actual) && actual.length === expected.length && expected.every((entry) => actual.includes(entry));
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
