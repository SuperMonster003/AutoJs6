#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-packaged-broadcast-trigger-remove-smoke";
const CURRENT_BOUNDARY_MARKERS = Object.freeze([
  "packaged.intent_broadcast.ready=true",
  "packaged.intent_broadcast.compat_rejected=true",
  "packaged.intent_broadcast.future_modules_denied=true",
  "packaged.intent_broadcast.raw_globals_absent=true",
  "packaged.intent_broadcast.broadcast_channel_js_only=true",
  "packaged.intent_broadcast.surface=true",
  "packaged.intent_broadcast.metadata_denied=true",
]);
const REQUIRED_POSITIVE_MARKERS = Object.freeze([
  "packaged.broadcast_trigger.install=true",
  "packaged.broadcast_trigger.register=true",
  "packaged.broadcast_trigger.payload_valid=true",
  "packaged.broadcast_trigger.delivered=true",
  "packaged.broadcast_trigger.remove_cleanup=true",
  "packaged.broadcast_trigger.relaunch=true",
  "packaged.broadcast_trigger.crash_recovery=true",
  "packaged.broadcast_trigger.undeclared_capability_denied=true",
]);
const REQUIRED_MATRIX_ROWS = Object.freeze([
  "current_packaged_boundary_denial_smoke",
  "future_packaged_local_custom_trigger",
  "future_packaged_remove_cleanup",
  "future_packaged_relaunch_crash_recovery",
  "future_undeclared_capability_and_exported_denial",
]);
const OPEN_BLOCKERS = Object.freeze([
  {
    id: "packaged_broadcast_trigger_positive_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged APK positive trigger archive for app-local or package-private broadcast descriptor registration, payload delivery, and JSON-only payload validation.",
  },
  {
    id: "packaged_broadcast_remove_cleanup_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged remove cleanup archive proving owned descriptor cleanup, receiver unregister or manifest patch cleanup, pending execution cancellation, and no post-remove delivery.",
  },
  {
    id: "packaged_broadcast_relaunch_crash_recovery_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged relaunch and crash recovery archive proving no duplicate receivers, orphan pending executions, or lost visible descriptors.",
  },
  {
    id: "packaged_broadcast_undeclared_capability_denial_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged denial archive for missing tasks.broadcast capabilities, unsupported triggers, metadata mismatch, and exported receiver requests.",
  },
  {
    id: "packaged_broadcast_exported_receiver_policy_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged exported receiver policy archive with explicit metadata, allowlist, permission model, abuse review, and Android 12/15+ evidence before any exported receiver is promoted.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_21_PACKAGED_BROADCAST_TRIGGER_REMOVE_SMOKE.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/packaged-broadcast-trigger-remove-smoke-plan.json",
  planDoc: "docs/nodejs/phase14/PHASE14_PACKAGED_BROADCAST_TRIGGER_REMOVE_SMOKE.md",
  p14IntentPlan: "docs/nodejs/phase14/intent-broadcast-provider-plan.json",
  p14IntentReport: "docs/nodejs/reports/phase14/P14_20_INTENT_BROADCAST_PROVIDER.md",
  p14IntentGeneratedReport: "phase14-intent-broadcast-provider.json",
  p13Report: "docs/nodejs/reports/phase13/P13_22_INTENT_BROADCAST_PROVIDER.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
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
  console.log("Phase 14 packaged broadcast trigger/remove smoke: " + report.decision.status);
  console.log("Phase 14 packaged broadcast blockers: " + report.blockers.length);
  console.log("Phase 14 packaged broadcast policy violations: " + report.policyViolations.length);
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
  const plan = parseJson(files.planJson.text);
  const smoke = plan.smokeContract || {};
  const metadata = plan.packagedMetadataContract || {};
  const cleanup = plan.cleanupContract || {};
  const matrixRows = Array.isArray(plan.triggerRemoveMatrix) ? plan.triggerRemoveMatrix : [];
  const matrixIds = matrixRows.map((entry) => entry.id);
  const p14IntentPlan = parseJson(files.p14IntentPlan.text);
  const p14IntentGenerated = parseJson(files.p14IntentGeneratedReport.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const proSurface = (manifest.surfaces || []).find((entry) => entry.id === "pro.tasks_device_media") || null;
  const currentMarkers = Array.isArray(smoke.currentBoundaryMarkers) ? smoke.currentBoundaryMarkers : [];
  const positiveMarkers = Array.isArray(smoke.requiredPositiveMarkers) ? smoke.requiredPositiveMarkers : [];
  const scenarios = [
    scenario("packaged_smoke_contract", "P14-21 reserves packaged positive broadcast trigger/remove smoke without promoting runtime authority", [
      value("schema", plan.schema === "autojs6-node-phase14-packaged-broadcast-trigger-remove-smoke-plan-v1"),
      value("task", plan.task === "P14-21"),
      value("status", plan.status === "partial"),
      value("promotionState", plan.promotionState === "provider_poc"),
      value("sourceGates", sameSet(plan.sourceGates || [], ["P13-22", "P14-20"])),
      value("futureProvider", smoke.futureProvider === "tasks.broadcast"),
      value("currentBoundaryImported", smoke.currentBoundaryEvidenceImported === true),
      value("positiveNotPromoted", smoke.positiveTriggerRemovePromoted === false),
      value("currentBoundaryMarkers", sameSet(currentMarkers, CURRENT_BOUNDARY_MARKERS)),
      value("positiveMarkers", sameSet(positiveMarkers, REQUIRED_POSITIVE_MARKERS)),
      value("matrixRows", sameSet(matrixIds, REQUIRED_MATRIX_ROWS)),
      token(files.planDoc, "`packaged.intent_broadcast.ready=true`"),
      token(files.planDoc, "`packaged.broadcast_trigger.remove_cleanup=true`"),
    ]),
    scenario("current_packaged_boundary_imported", "P13-22 packaged IntentTask/broadcast boundary smoke remains the current packaged evidence", [
      token(files.packagedSmokeTest, "buildIntentBroadcastBoundaryEmbeddedNodePackagedApk"),
      token(files.packagedSmokeTest, "installAndRunIntentBroadcastBoundaryEmbeddedNodePackagedApk"),
      token(files.packagedSmokeTest, "buildPackagedIntentBroadcastBoundaryApk"),
      token(files.packagedSmokeTest, "assertInstallAndRunPackagedIntentBroadcastBoundarySmoke"),
      token(files.packagedSmokeTest, "PackagedNodeIntentBroadcastBoundarySmoke"),
      token(files.packagedSmokeTest, "\"tasks.broadcast\""),
      token(files.packagedSmokeTest, "android.permission.RECEIVE_BOOT_COMPLETED"),
      ...CURRENT_BOUNDARY_MARKERS.map((marker) => token(files.packagedSmokeTest, marker)),
      token(files.p13Report, "Packaged boundary: `EmbeddedNodePackagedApkSmokeTest#installAndRunIntentBroadcastBoundaryEmbeddedNodePackagedApk`"),
    ]),
    scenario("p14_20_provider_contract_imported", "P14-20 descriptor, manifest/export, and payload/remove policies are imported", [
      value("p14IntentGeneratedPresent", files.p14IntentGeneratedReport.exists),
      value("p14IntentGeneratedPartial", statusOf(p14IntentGenerated) === "partial"),
      value("p14IntentDescriptorReady", (p14IntentGenerated.decision || {}).descriptorContractReady === true),
      value("p14IntentManifestReady", (p14IntentGenerated.decision || {}).manifestExportPolicyReady === true),
      value("p14IntentPayloadReady", (p14IntentGenerated.decision || {}).triggerPayloadRemovePolicyReady === true),
      value("p14IntentRuntimeNotPromoted", (p14IntentGenerated.decision || {}).runtimeTasksBroadcastPromoted === false),
      value("p14IntentFutureProvider", (p14IntentPlan.providerContract || {}).futureProvider === "tasks.broadcast"),
      token(files.p14IntentReport, "`intent_broadcast_live_provider_archive`"),
      token(files.p14IntentReport, "`intent_broadcast_trigger_payload_schema_archive`"),
    ]),
    scenario("packaged_metadata_export_and_cleanup_policy", "Packaged metadata, exported receiver, and cleanup policy remain explicit and owner-scoped", [
      value("metadataOptInRequired", metadata.metadataOptInRequired === true),
      value("currentFutureCapabilitiesDenied", sameSet(metadata.capabilitiesDeniedNow || [], ["tasks", "tasks.intent", "tasks.broadcast", "tasks.boot", "tasks.package_events"])),
      value("bootPermissionDeniedNow", metadata.bootPermissionDeniedNow === "android.permission.RECEIVE_BOOT_COMPLETED"),
      value("patchReportRequired", metadata.receiverPatchReportRequired === true),
      value("defaultExportedFalse", metadata.defaultExported === false),
      value("exportedReceiverAllowedNowFalse", metadata.exportedReceiverAllowedNow === false),
      value("removeOwnedDescriptor", cleanup.removeOwnedDescriptor === true),
      value("unregisterDynamicReceiver", cleanup.unregisterDynamicReceiver === true),
      value("removeOwnedManifestPatch", cleanup.removeOwnedManifestPatch === true),
      value("cancelPendingExecutions", cleanup.cancelPendingExecutions === true),
      value("preventPostRemoveDelivery", cleanup.preventPostRemoveDelivery === true),
      value("preserveLegacyRhinoRows", cleanup.preserveLegacyRhinoRows === true),
      value("denyCrossPackageRemove", cleanup.denyCrossPackageRemove === true),
      token(files.planDoc, "Exported receivers remain denied"),
      token(files.securityModel, "P14-21 records the packaged broadcast trigger/remove smoke contract"),
      token(files.errorCatalog, "P14-21 packaged broadcast trigger/remove denial sync"),
      token(files.compatibilityProfile, "P14-21 records the packaged broadcast trigger/remove smoke contract"),
    ]),
    scenario("current_runtime_boundary", "P14-21 keeps tasks.broadcast runtime authority and future capabilities unavailable", [
      token(files.providerRegistry, '"phase14PackagedBroadcastTriggerRemovePromotion" to "P14-21 packaged broadcast trigger/remove smoke contract gate"'),
      token(files.providerRegistry, '"packagedBroadcastSmokePolicy" to "imports_packaged_intent_broadcast_boundary_positive_trigger_remove_not_promoted"'),
      token(files.providerRegistry, '"packagedBroadcastCleanupPolicy" to "owned_descriptor_receiver_patch_pending_execution_cleanup_required_before_promotion"'),
      value("tasksBroadcastNotExposed", !files.protocol.text.includes('const val TASKS = "tasks"') && !files.protocol.text.includes('const val TASKS_BROADCAST = "tasks.broadcast"') && !files.nodeBridgeSources.text.includes('if (name === "tasks.broadcast")') && !files.nodeBridgeSources.text.includes("__autojs6_limited_tasks_broadcast")),
      value("futureCapabilitiesNotAccepted", !files.permissionManifest.text.includes('const val TASKS = "tasks"') && !files.permissionManifest.text.includes("TASKS_BROADCAST") && !files.permissionManifest.text.includes('"tasks.broadcast"')),
    ]),
    scenario("surface_docs_and_gradle_wiring", "Phase 14 docs, manifest, and Gradle expose the P14-21 gate", [
      value("surfaceEvidence", proSurface &&
        proSurface.status === "provider_poc" &&
        (proSurface.tasks || []).includes("P14-21") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/packaged-broadcast-trigger-remove-smoke-plan.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14PackagedBroadcastTriggerRemoveSmoke")),
      token(files.surfaceManifestDoc, "P14-21 adds the packaged broadcast trigger/remove smoke contract"),
      token(files.phase14Tasks, "Packaged broadcast trigger/remove smoke gate deployed:"),
      token(files.phase14Readme, ":app:verifyNodePhase14PackagedBroadcastTriggerRemoveSmoke"),
      token(files.testing, ":app:verifyNodePhase14PackagedBroadcastTriggerRemoveSmoke"),
      token(files.ciGates, ":app:verifyNodePhase14PackagedBroadcastTriggerRemoveSmoke"),
      token(files.gradle14, "verifyNodePhase14PackagedBroadcastTriggerRemoveSmoke"),
      token(files.gradle14, "verify-node-phase14-packaged-broadcast-trigger-remove-smoke.js"),
    ]),
    scenario("promotion_archives_open", "Positive trigger, cleanup, relaunch/crash, undeclared capability, and exported receiver archives remain future work", [
      ...OPEN_BLOCKERS.flatMap((entry) => [
        token(files.planDoc, entry.id),
        token(files.phase14Report, entry.id),
      ]),
    ], OPEN_BLOCKERS.map((entry) => entry.id)),
  ];
  const checks = [
    check("p14-intent-generated-report-present", files.p14IntentGeneratedReport.exists, "The P14-20 generated report must exist before P14-21 runs."),
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase14-packaged-broadcast-trigger-remove-smoke-plan-v1", "P14-21 packaged broadcast trigger/remove plan JSON must exist."),
    check("plan-doc-present", files.planDoc.exists && files.planDoc.text.includes("Phase 14 Packaged Broadcast Trigger Remove Smoke"), "P14-21 packaged broadcast trigger/remove documentation must exist."),
    check(
      "surface-manifest-evidence",
      proSurface &&
        proSurface.status === "provider_poc" &&
        (proSurface.tasks || []).includes("P14-21") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/packaged-broadcast-trigger-remove-smoke-plan.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14PackagedBroadcastTriggerRemoveSmoke"),
      "The pro.tasks_device_media surface must include the P14-21 evidence and Gradle gate.",
    ),
    check(
      "runtime-still-closed",
      !files.protocol.text.includes('const val TASKS = "tasks"') &&
        !files.nodeBridgeSources.text.includes('if (name === "tasks.broadcast")') &&
        !files.nodeBridgeSources.text.includes("__autojs6_limited_tasks_broadcast") &&
        !files.permissionManifest.text.includes("TASKS_BROADCAST"),
      "P14-21 must not expose tasks.broadcast runtime authority or accept future broadcast capabilities yet.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.ok).map((entry) => ({ id: entry.id, message: entry.message }));
  const blockers = OPEN_BLOCKERS.map((entry) => ({ ...entry }));
  return {
    schema: "autojs6-node-phase14-packaged-broadcast-trigger-remove-smoke-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "partial" : "failed",
    task: "P14-21",
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
      currentPackagedBoundaryImported: scenarioById(scenarios, "current_packaged_boundary_imported").status === "ready",
      p14IntentProviderContractImported: scenarioById(scenarios, "p14_20_provider_contract_imported").status === "ready",
      packagedMetadataExportCleanupPolicyReady: scenarioById(scenarios, "packaged_metadata_export_and_cleanup_policy").status === "ready",
      positiveTriggerRemoveSmokeReady: false,
      relaunchCrashRecoveryReady: false,
      undeclaredCapabilityDenialReady: false,
      exportedReceiverPolicyReady: false,
      runtimeTasksBroadcastPromoted: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: blockers.length,
      proSurfacePromoted: false,
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Packaged Broadcast Trigger Remove Smoke",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- P14 task complete: \`${report.decision.p14TaskComplete}\``,
    `- Current packaged boundary imported: \`${report.decision.currentPackagedBoundaryImported}\``,
    `- P14 Intent provider contract imported: \`${report.decision.p14IntentProviderContractImported}\``,
    `- Metadata/export/cleanup policy ready: \`${report.decision.packagedMetadataExportCleanupPolicyReady}\``,
    `- Positive trigger/remove smoke ready: \`${report.decision.positiveTriggerRemoveSmokeReady}\``,
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
