#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-overlay-permission-oem-matrix";
const REQUIRED_DEVICE_CLASSES = Object.freeze(["android_12_class", "android_15_plus_class"]);
const REQUIRED_DEVICE_FIELDS = Object.freeze([
  "deviceSerial",
  "deviceModel",
  "androidVersion",
  "apiLevel",
  "packageIdentity",
  "profile",
  "capabilities",
  "androidPermissionsRequested",
  "androidPermissionsGranted",
  "notificationPermissionState",
  "backgroundStartBehavior",
  "settingsRoundTripBehavior",
  "taskRemovalBehavior",
  "relaunchBehavior",
  "crashRecoveryBehavior",
  "foregroundDisclosureBehavior",
  "stopActionBehavior",
  "leakOrResourceSummary",
  "oemQuirks",
  "reportPath",
]);
const REQUIRED_OVERLAY_SCENARIOS = Object.freeze([
  "SYSTEM_ALERT_WINDOW denied fail-closed copy",
  "SYSTEM_ALERT_WINDOW granted show/update/drainEvents/close",
  "openPermissionSettings settings round-trip",
  "task removal cleanup",
  "package relaunch behavior",
  "OEM permission/settings behavior notes",
]);
const OPEN_BLOCKERS = Object.freeze([
  {
    id: "overlay_android12_granted_device_archive",
    classification: "future_promotion",
    requiredEvidence: "Archived Android 12 class device run with SYSTEM_ALERT_WINDOW granted and show/update/drainEvents/close plus denial copy recorded.",
  },
  {
    id: "overlay_android15_plus_granted_device_archive",
    classification: "future_promotion",
    requiredEvidence: "Archived Android 15+ class device run with SYSTEM_ALERT_WINDOW granted and show/update/drainEvents/close plus denial copy recorded.",
  },
  {
    id: "overlay_settings_round_trip_oem_notes",
    classification: "future_promotion",
    requiredEvidence: "OEM-specific overlay settings round-trip notes for permission grant, revoke, and returned permission snapshot.",
  },
  {
    id: "overlay_task_removal_relaunch_evidence",
    classification: "future_promotion",
    requiredEvidence: "Task removal cleanup and package relaunch behavior over visible overlay sessions on the required device matrix.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_09_OVERLAY_PERMISSION_OEM_MATRIX.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  matrixJson: "docs/nodejs/phase14/overlay-permission-oem-matrix.json",
  matrixDoc: "docs/nodejs/phase14/PHASE14_OVERLAY_PERMISSION_OEM_MATRIX.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  promotionContractsJson: "docs/nodejs/phase14/phase14-promotion-contracts.json",
  promotionContractsDoc: "docs/nodejs/phase14/PHASE14_PROMOTION_CONTRACTS.md",
  p14OverlayReport: "docs/nodejs/reports/phase14/P14_08_OVERLAY_PROVIDER_V1.md",
  phase13Report: "docs/nodejs/reports/phase13/P13_18_OVERLAY_PROVIDER_PROMOTION.md",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  overlayTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeOverlayBridgeInstrumentationTest.kt",
  packagedTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
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
  console.log("Phase 14 overlay permission OEM matrix: " + report.decision.status);
  console.log("Phase 14 overlay permission OEM matrix blockers: " + report.blockers.length);
  console.log("Phase 14 overlay permission OEM matrix policy violations: " + report.policyViolations.length);
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
    phase13GeneratedReport: "phase13-overlay-provider-promotion.json",
    p14OverlayGeneratedReport: "phase14-overlay-provider.json",
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
  const matrix = parseJson(files.matrixJson.text);
  const proSurface = (manifest.surfaces || []).find((entry) => entry.id === "pro.ui_automation_media") || null;
  const contractMatrix = contracts.deviceEvidenceMatrix || {};
  const rows = Array.isArray(matrix.evidenceRows) ? matrix.evidenceRows : [];
  const p14OverlayGenerated = parseJson(files.p14OverlayGeneratedReport.text);
  const phase13Generated = parseJson(files.phase13GeneratedReport.text);
  const scenarios = [
    scenario("matrix_schema_and_required_slots", "Matrix declares required device classes, fields, and reserved archive rows", [
      checkTokenValue("schema", matrix.schema === "autojs6-node-phase14-overlay-permission-oem-matrix-v1"),
      checkTokenValue("task", matrix.task === "P14-09"),
      checkTokenValue("status", matrix.status === "partial"),
      checkTokenValue("promotionState", matrix.promotionState === "provider_poc"),
      checkTokenValue("requiredDeviceClasses", sameSet(matrix.requiredDeviceClasses || [], REQUIRED_DEVICE_CLASSES)),
      checkTokenValue("requiredFields", includesAll(matrix.requiredFields || [], REQUIRED_DEVICE_FIELDS)),
      checkTokenValue("requiredOverlayScenarios", includesAll(matrix.requiredOverlayScenarios || [], REQUIRED_OVERLAY_SCENARIOS)),
      checkTokenValue("android_12_row", hasRow(rows, "android_12_class", "missing_device_archive")),
      checkTokenValue("android_15_plus_row", hasRow(rows, "android_15_plus_class", "missing_device_archive")),
      checkTokenValue("rows_have_required_fields", rows.every((row) => REQUIRED_DEVICE_FIELDS.every((field) => Object.prototype.hasOwnProperty.call(row, field)))),
    ]),
    scenario("denied_boundary_imported", "Permission-denied overlay boundary is imported without claiming granted device coverage", [
      token(files.p14OverlayReport, "without `SYSTEM_ALERT_WINDOW`"),
      token(files.p14OverlayGeneratedReport, '"permissionDeniedBoundaryReady": true'),
      token(files.protocol, "Settings.canDrawOverlays(appContext)"),
      token(files.protocol, "AutoJs6 ui.overlay.show requires android.permission.SYSTEM_ALERT_WINDOW"),
      token(files.overlayTest, "ui.overlay live creation must fail closed without SYSTEM_ALERT_WINDOW"),
      token(files.overlayTest, "NodeBridgeErrorCategory.PERMISSION_DENIED"),
      token(files.overlayTest, 'embedded_script.overlay_active_session_count'),
      checkTokenValue("phase14_overlay_report_partial", p14OverlayGenerated.status === "partial"),
      checkTokenValue("phase13_overlay_generated_imported", phase13Generated.decision && phase13Generated.decision.packagedOverlayBoundaryReady === true),
    ]),
    scenario("packaged_and_oem_boundaries_open", "Packaged overlay and OEM/device behavior remain future-promotion blockers", [
      token(files.packagedTest, "installAndRunOverlayCapabilityBoundaryEmbeddedNodePackagedApk"),
      token(files.packagedTest, 'androidPermissions.indexOf("android.permission.SYSTEM_ALERT_WINDOW") < 0'),
      token(files.phase13Report, "Record overlay permission granted and denied device evidence, including OEM"),
      token(files.matrixDoc, "missing_device_archive"),
      token(files.phase14Report, "overlay_android12_granted_device_archive"),
      token(files.phase14Report, "overlay_android15_plus_granted_device_archive"),
      token(files.phase14Report, "overlay_settings_round_trip_oem_notes"),
      token(files.phase14Report, "overlay_task_removal_relaunch_evidence"),
    ], OPEN_BLOCKERS.map((entry) => entry.id)),
    scenario("docs_surface_and_gradle_wiring", "Phase 14 docs, surface manifest, and Gradle expose the P14-09 gate", [
      token(files.gradle14, "verifyNodePhase14OverlayPermissionOemMatrix"),
      token(files.gradle14, "verify-node-phase14-overlay-permission-oem-matrix.js"),
      token(files.phase14Tasks, "Matrix gate deployed:"),
      token(files.phase14Readme, ":app:verifyNodePhase14OverlayPermissionOemMatrix"),
      token(files.testing, ":app:verifyNodePhase14OverlayPermissionOemMatrix"),
      token(files.ciGates, ":app:verifyNodePhase14OverlayPermissionOemMatrix"),
      token(files.surfaceManifestDoc, "P14-09 adds the overlay permission/OEM matrix"),
      token(files.overlayDesign, "P14-09 adds the overlay permission/OEM matrix"),
    ]),
  ];
  const checks = [
    check("phase13-generated-report-present", files.phase13GeneratedReport.exists, "The Phase 13 overlay provider report must be generated before the P14-09 gate."),
    check("phase14-overlay-provider-generated-report-present", files.p14OverlayGeneratedReport.exists, "The P14-08 overlay provider report must be generated before the P14-09 gate."),
    check("matrix-json-present", files.matrixJson.exists && matrix.schema === "autojs6-node-phase14-overlay-permission-oem-matrix-v1", "P14-09 matrix JSON must exist with the expected schema."),
    check("matrix-doc-present", files.matrixDoc.exists && files.matrixDoc.text.includes("Phase 14 Overlay Permission OEM Matrix"), "P14-09 matrix documentation must exist."),
    check(
      "contract-device-fields-imported",
      sameSet(contractMatrix.requiredDeviceClasses || [], REQUIRED_DEVICE_CLASSES) &&
        includesAll(contractMatrix.requiredFields || [], REQUIRED_DEVICE_FIELDS) &&
        includesAll(matrix.requiredFields || [], contractMatrix.requiredFields || []),
      "P14-09 matrix must import the shared Phase 14 device evidence classes and fields.",
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
          "docs/nodejs/phase14/overlay-permission-oem-matrix.json",
          "docs/nodejs/phase14/PHASE14_OVERLAY_PERMISSION_OEM_MATRIX.md",
          "docs/nodejs/reports/phase14/P14_09_OVERLAY_PERMISSION_OEM_MATRIX.md",
          "tools/nodejs/phase14/verify-node-phase14-overlay-permission-oem-matrix.js",
          ":app:verifyNodePhase14OverlayPermissionOemMatrix",
          "build/reports/nodejs/phase14-overlay-permission-oem-matrix.json",
          "build/reports/nodejs/phase14-overlay-permission-oem-matrix.md",
        ]),
      "`pro.ui_automation_media` evidence must include the P14-09 matrix, docs, verifier, Gradle gate, and output reports.",
    ),
    check(
      "task-plan-keeps-p14-09-open",
      files.phase14Tasks.text.includes("- [ ] P14-09:") &&
        files.phase14Tasks.text.includes("Matrix gate deployed:") &&
        OPEN_BLOCKERS.every((entry) => files.phase14Tasks.text.includes(entry.id)),
      "P14-09 must remain unchecked while the matrix records the open device/OEM promotion blockers.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.gradle14.text.includes("verifyNodePhase14OverlayPermissionOemMatrix") &&
        files.phase14Readme.text.includes(":app:verifyNodePhase14OverlayPermissionOemMatrix") &&
        files.testing.text.includes(":app:verifyNodePhase14OverlayPermissionOemMatrix") &&
        files.ciGates.text.includes(":app:verifyNodePhase14OverlayPermissionOemMatrix"),
      "Gradle, README, TESTING, and CI_GATES must reference the P14-09 overlay permission/OEM matrix gate.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase14-overlay-permission-oem-matrix-report-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase14OverlayPermissionOemMatrix",
    task: "P14-09",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    matrix: {
      status: matrix.status || "missing",
      promotionState: matrix.promotionState || "missing",
      requiredDeviceClasses: matrix.requiredDeviceClasses || [],
      evidenceRows: rows.map((row) => ({
        id: row.id,
        deviceClass: row.deviceClass,
        status: row.status,
        reportPath: row.reportPath,
      })),
    },
    scenarios,
    checks,
    policyViolations,
    blockers: OPEN_BLOCKERS.map((entry) => ({
      ...entry,
      status: "open",
    })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc",
      p14TaskComplete: false,
      matrixGateReady: scenarioById(scenarios, "matrix_schema_and_required_slots").status === "ready",
      permissionDeniedBoundaryImported: scenarioById(scenarios, "denied_boundary_imported").status === "ready",
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

function sameSet(values, requiredValues) {
  return Array.isArray(values) && values.length === requiredValues.length && requiredValues.every((value) => values.includes(value));
}

function hasRow(rows, deviceClass, status) {
  return rows.some((row) => row.deviceClass === deviceClass && row.status === status);
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
    "# Phase 14 Overlay Permission OEM Matrix",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    `- Future promotion blockers: ${report.decision.futurePromotionBlockers}`,
    "",
    "## Matrix Rows",
    "",
    "| Row | Device class | Status | Report path |",
    "| --- | --- | --- | --- |",
  ];
  report.matrix.evidenceRows.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | \`${entry.deviceClass}\` | \`${entry.status}\` | ${escapeTable(entry.reportPath || "")} |`);
  });
  lines.push("", "## Scenarios", "", "| Scenario | Status | Remaining |", "| --- | --- | --- |");
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
      console.log("Usage: node verify-node-phase14-overlay-permission-oem-matrix.js [--repo-root dir] [--report-dir dir]");
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
