#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-packaged-overlay-positive-smoke";
const REQUIRED_NODE_PERMISSIONS = Object.freeze([
  "ui.overlay",
  "ui.overlay.permission",
  "ui.overlay.foreground",
  "ui.overlay.packaged",
]);
const REQUIRED_ANDROID_PERMISSIONS = Object.freeze(["android.permission.SYSTEM_ALERT_WINDOW"]);
const REQUIRED_RUNTIME_MARKERS = Object.freeze([
  "packaged.overlay_positive.ready=true",
  "packaged.overlay_positive.metadata=true",
  "packaged.overlay_positive.permission_snapshot=true",
  "packaged.overlay_positive.permission_granted=true",
  "packaged.overlay_positive.disclosure=true",
  "packaged.overlay_positive.show=true",
  "packaged.overlay_positive.update=true",
  "packaged.overlay_positive.event_drain=true",
  "packaged.overlay_positive.close=true",
  "packaged.overlay_positive.close_all=true",
  "packaged.overlay_positive.stress_sessions=3",
  "packaged.overlay_positive.leak_summary=true",
  "packaged.overlay_positive.raw_globals_absent=true",
  "packaged.overlay_positive.pending=0",
]);
const OPEN_BLOCKERS = Object.freeze([
  {
    id: "packaged_overlay_positive_device_archive",
    classification: "future_promotion",
    requiredEvidence: "Archived install-run logs for the packaged overlay positive APK on the Android 12 and Android 15+ matrix.",
  },
  {
    id: "packaged_overlay_foreground_disclosure_stop_action",
    classification: "future_promotion",
    requiredEvidence: "Foreground disclosure ownership and stop-action behavior reviewed while packaged overlay is visible.",
  },
  {
    id: "packaged_overlay_task_removal_relaunch_crash",
    classification: "future_promotion",
    requiredEvidence: "Task removal cleanup, package relaunch behavior, and crash recovery evidence for packaged overlay sessions.",
  },
  {
    id: "packaged_overlay_event_drain_leak_stress",
    classification: "future_promotion",
    requiredEvidence: "Event drain and leak/stress archive proving three overlay sessions close with zero pending dispatch and zero remaining overlays.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_10_PACKAGED_OVERLAY_POSITIVE_SMOKE.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/packaged-overlay-positive-smoke-plan.json",
  planDoc: "docs/nodejs/phase14/PHASE14_PACKAGED_OVERLAY_POSITIVE_SMOKE.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  promotionContractsJson: "docs/nodejs/phase14/phase14-promotion-contracts.json",
  p14OverlayReport: "docs/nodejs/reports/phase14/P14_08_OVERLAY_PROVIDER_V1.md",
  p14MatrixReport: "docs/nodejs/reports/phase14/P14_09_OVERLAY_PERMISSION_OEM_MATRIX.md",
  overlayMatrixJson: "docs/nodejs/phase14/overlay-permission-oem-matrix.json",
  packagedTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  overlayDesign: "docs/nodejs/design/OVERLAY_PROVIDER_V1.md",
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
  console.log("Phase 14 packaged overlay positive smoke: " + report.decision.status);
  console.log("Phase 14 packaged overlay positive smoke blockers: " + report.blockers.length);
  console.log("Phase 14 packaged overlay positive smoke policy violations: " + report.policyViolations.length);
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
    p14OverlayGeneratedReport: "phase14-overlay-provider.json",
    p14MatrixGeneratedReport: "phase14-overlay-permission-oem-matrix.json",
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
  const matrix = parseJson(files.overlayMatrixJson.text);
  const p14OverlayGenerated = parseJson(files.p14OverlayGeneratedReport.text);
  const p14MatrixGenerated = parseJson(files.p14MatrixGeneratedReport.text);
  const proSurface = (manifest.surfaces || []).find((entry) => entry.id === "pro.ui_automation_media") || null;
  const fixture = plan.fixture || {};
  const manual = plan.manualArchiveRequirements || {};
  const scenarios = [
    scenario("packaged_fixture_contract", "Packaged overlay positive fixture declares capabilities, Android permission, and runtime markers", [
      checkTokenValue("schema", plan.schema === "autojs6-node-phase14-packaged-overlay-positive-smoke-v1"),
      checkTokenValue("task", plan.task === "P14-10"),
      checkTokenValue("status", plan.status === "partial"),
      checkTokenValue("promotionState", plan.promotionState === "provider_poc"),
      checkTokenValue("node_permissions", includesAll(fixture.requiredNodePermissions || [], REQUIRED_NODE_PERMISSIONS)),
      checkTokenValue("android_permissions", includesAll(fixture.requiredAndroidPermissions || [], REQUIRED_ANDROID_PERMISSIONS)),
      checkTokenValue("runtime_markers", includesAll(fixture.runtimeMarkers || [], REQUIRED_RUNTIME_MARKERS)),
      token(files.packagedTest, "buildOverlayPositiveEmbeddedNodePackagedApk"),
      token(files.packagedTest, "buildPackagedOverlayPositiveSmokeApk"),
      token(files.packagedTest, "OVERLAY_POSITIVE_NODE_PERMISSIONS"),
      token(files.packagedTest, "OVERLAY_POSITIVE_RUNTIME_MARKERS"),
      token(files.packagedTest, "PackagedNodeOverlayPositiveSmoke_v1.0.0.apk"),
      token(files.packagedTest, 'extraPermissions = listOf("android.permission.SYSTEM_ALERT_WINDOW")'),
      token(files.packagedTest, "packaged.overlay_positive.ready=true"),
      token(files.packagedTest, "packaged.overlay_positive.stress_sessions=3"),
    ]),
    scenario("runtime_positive_path_contract", "Fixture script exercises permission snapshot, show/update/drain/close/closeAll, stress, and leak cleanup", [
      token(files.packagedTest, "const permission = await overlay.hasPermission"),
      token(files.packagedTest, "const first = await overlay.show"),
      token(files.packagedTest, "await first.update"),
      token(files.packagedTest, "await first.drainEvents"),
      token(files.packagedTest, "await first.close"),
      token(files.packagedTest, "await overlay.closeAll"),
      token(files.packagedTest, "for (let index = 0; index < 3; index += 1)"),
      token(files.packagedTest, "p14_10_positive_final_cleanup"),
      token(files.packagedTest, "WindowManager"),
      token(files.packagedTest, "bridge.__test.pendingCount()"),
      token(files.protocol, "foregroundDisclosure"),
      token(files.protocol, "activeOverlayCount"),
    ]),
    scenario("device_archives_open", "Install-run, foreground disclosure, task-removal, relaunch/crash, and stress archives remain future work", [
      checkTokenValue("manual_device_classes", includesAll(manual.requiredDeviceClasses || [], ["android_12_class", "android_15_plus_class"])),
      checkTokenValue("requires_user_grant", manual.requiresUserGrantedOverlayPermission === true),
      checkTokenValue("requires_disclosure_review", manual.requiresForegroundDisclosureReview === true),
      token(files.p14OverlayReport, "packaged positive smoke"),
      token(files.p14MatrixReport, "Android 12 class and Android 15+ class archive"),
      token(files.planDoc, "requiresUserGrantedOverlayPermission"),
      token(files.phase14Report, "packaged_overlay_positive_device_archive"),
      token(files.phase14Report, "packaged_overlay_foreground_disclosure_stop_action"),
      token(files.phase14Report, "packaged_overlay_task_removal_relaunch_crash"),
      token(files.phase14Report, "packaged_overlay_event_drain_leak_stress"),
    ], OPEN_BLOCKERS.map((entry) => entry.id)),
    scenario("phase14_prerequisites_imported", "P14-08 provider and P14-09 permission/OEM matrix remain imported as prerequisites", [
      token(files.p14OverlayGeneratedReport, '"permissionDeniedBoundaryReady": true'),
      token(files.p14OverlayGeneratedReport, '"promotionState": "provider_poc"'),
      token(files.p14MatrixGeneratedReport, '"permissionDeniedBoundaryImported": true'),
      token(files.p14MatrixGeneratedReport, '"requiredDeviceArchivesReady": false'),
      checkTokenValue("overlay_provider_partial", p14OverlayGenerated.status === "partial"),
      checkTokenValue("overlay_matrix_partial", p14MatrixGenerated.status === "partial"),
      checkTokenValue("matrix_has_android12", includesAll(matrix.requiredDeviceClasses || [], ["android_12_class"])),
      checkTokenValue("matrix_has_android15", includesAll(matrix.requiredDeviceClasses || [], ["android_15_plus_class"])),
    ]),
    scenario("docs_surface_and_gradle_wiring", "Phase 14 docs, surface manifest, and Gradle expose the P14-10 gate", [
      token(files.gradle14, "verifyNodePhase14PackagedOverlayPositiveSmoke"),
      token(files.gradle14, "verify-node-phase14-packaged-overlay-positive-smoke.js"),
      token(files.phase14Tasks, "Packaged positive fixture deployed:"),
      token(files.phase14Readme, ":app:verifyNodePhase14PackagedOverlayPositiveSmoke"),
      token(files.testing, ":app:verifyNodePhase14PackagedOverlayPositiveSmoke"),
      token(files.ciGates, ":app:verifyNodePhase14PackagedOverlayPositiveSmoke"),
      token(files.surfaceManifestDoc, "P14-10 adds the packaged overlay positive smoke/stress fixture"),
      token(files.overlayDesign, "P14-10 adds a packaged overlay positive smoke/stress fixture"),
    ]),
  ];
  const disclosure = contracts.packagedHighPrivilegeDisclosure || {};
  const checks = [
    check("phase14-overlay-provider-generated-report-present", files.p14OverlayGeneratedReport.exists, "The P14-08 overlay provider report must be generated before the P14-10 gate."),
    check("phase14-overlay-matrix-generated-report-present", files.p14MatrixGeneratedReport.exists, "The P14-09 overlay permission/OEM matrix report must be generated before the P14-10 gate."),
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase14-packaged-overlay-positive-smoke-v1", "P14-10 packaged overlay positive smoke plan JSON must exist."),
    check("plan-doc-present", files.planDoc.exists && files.planDoc.text.includes("Phase 14 Packaged Overlay Positive Smoke"), "P14-10 plan documentation must exist."),
    check(
      "fixture-contract",
      includesAll(fixture.requiredNodePermissions || [], REQUIRED_NODE_PERMISSIONS) &&
        includesAll(fixture.requiredAndroidPermissions || [], REQUIRED_ANDROID_PERMISSIONS) &&
        includesAll(fixture.runtimeMarkers || [], REQUIRED_RUNTIME_MARKERS) &&
        files.packagedTest.text.includes("buildPackagedOverlayPositiveSmokeApk"),
      "P14-10 fixture must declare overlay node permissions, SYSTEM_ALERT_WINDOW, runtime markers, and a packaged builder.",
    ),
    check(
      "surface-provider-poc-not-promoted",
      proSurface &&
        proSurface.status === "provider_poc" &&
        !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.ui_automation_media` must stay provider_poc, not opt-in or default promoted.",
    ),
    check(
      "surface-evidence",
      proSurface &&
        includesAll(proSurface.evidence || [], [
          "docs/nodejs/phase14/packaged-overlay-positive-smoke-plan.json",
          "docs/nodejs/phase14/PHASE14_PACKAGED_OVERLAY_POSITIVE_SMOKE.md",
          "docs/nodejs/reports/phase14/P14_10_PACKAGED_OVERLAY_POSITIVE_SMOKE.md",
          "tools/nodejs/phase14/verify-node-phase14-packaged-overlay-positive-smoke.js",
          ":app:verifyNodePhase14PackagedOverlayPositiveSmoke",
          "build/reports/nodejs/phase14-packaged-overlay-positive-smoke.json",
          "build/reports/nodejs/phase14-packaged-overlay-positive-smoke.md",
        ]),
      "`pro.ui_automation_media` evidence must include the P14-10 fixture, docs, verifier, Gradle gate, and output reports.",
    ),
    check(
      "contracts-cover-overlay-packaged-fields",
      includesAll(disclosure.requiredForSurfaces || [], ["overlay"]) &&
        includesAll(disclosure.requiredFields || [], [
          "foregroundNotificationOwnership",
          "stopAction",
          "taskRemovalCleanup",
          "relaunchBehavior",
          "crashRecoveryBehavior",
          "privacyDisclosure",
          "rollbackBehavior",
        ]),
      "Phase 14 promotion contracts must cover packaged overlay disclosure, stop action, cleanup, relaunch, crash, privacy, and rollback fields.",
    ),
    check(
      "task-plan-keeps-p14-10-open",
      files.phase14Tasks.text.includes("- [ ] P14-10:") &&
        files.phase14Tasks.text.includes("Packaged positive fixture deployed:") &&
        OPEN_BLOCKERS.every((entry) => files.phase14Tasks.text.includes(entry.id)),
      "P14-10 must remain unchecked while the fixture records open packaged/device promotion blockers.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.gradle14.text.includes("verifyNodePhase14PackagedOverlayPositiveSmoke") &&
        files.phase14Readme.text.includes(":app:verifyNodePhase14PackagedOverlayPositiveSmoke") &&
        files.testing.text.includes(":app:verifyNodePhase14PackagedOverlayPositiveSmoke") &&
        files.ciGates.text.includes(":app:verifyNodePhase14PackagedOverlayPositiveSmoke"),
      "Gradle, README, TESTING, and CI_GATES must reference the P14-10 packaged overlay positive smoke gate.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase14-packaged-overlay-positive-smoke-report-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase14PackagedOverlayPositiveSmoke",
    task: "P14-10",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    fixture: {
      projectName: fixture.projectName || "missing",
      packageName: fixture.packageName || "missing",
      apkName: fixture.apkName || "missing",
      requiredNodePermissions: fixture.requiredNodePermissions || [],
      requiredAndroidPermissions: fixture.requiredAndroidPermissions || [],
      runtimeMarkers: fixture.runtimeMarkers || [],
    },
    scenarios,
    checks,
    policyViolations,
    blockers: OPEN_BLOCKERS.map((entry) => ({ ...entry, status: "open" })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc",
      p14TaskComplete: false,
      packagedFixtureReady: scenarioById(scenarios, "packaged_fixture_contract").status === "ready",
      runtimePositivePathReady: scenarioById(scenarios, "runtime_positive_path_contract").status === "ready",
      requiredDeviceArchivesReady: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: OPEN_BLOCKERS.length,
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

function checkTokenValue(tokenName, present) {
  return {
    file: "parsed-json",
    token: tokenName,
    present: Boolean(present),
  };
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing" };
}

function includesAll(values, requiredValues) {
  if (Array.isArray(values)) {
    return requiredValues.every((value) => values.includes(value));
  }
  return requiredValues.every((value) => String(values).includes(value));
}

function parseJson(text) {
  try {
    return JSON.parse(stripBom(text || "{}"));
  } catch (_) {
    return {};
  }
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Packaged Overlay Positive Smoke",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    `- Future promotion blockers: ${report.decision.futurePromotionBlockers}`,
    "",
    "## Fixture",
    "",
    `- Project: \`${report.fixture.projectName}\``,
    `- Package: \`${report.fixture.packageName}\``,
    `- APK: \`${report.fixture.apkName}\``,
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

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-node-phase14-packaged-overlay-positive-smoke.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    }
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    } else {
      throw new Error("Unexpected argument: " + arg);
    }
  }
  return options;
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
  } catch (_) {
    return "";
  }
}

function stripBom(text) {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

main();
