#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-overlay-live-gate-verification";
const P12_14_NOTE = "P12-14 overlay live gate landed";

const FILES = {
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  executionModePolicy: "app/src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  types: "docs/nodejs/types/autojs6-node/ui_overlay.d.ts",
  typeReadme: "docs/nodejs/types/autojs6-node/README.md",
  androidManifest: "app/src/main/AndroidManifest.xml",
  projectConfig: "app/src/main/java/org/autojs/autojs/project/ProjectConfig.java",
  instrumentation: "app/src/androidTest/java/org/autojs/autojs/engine/NodeOverlayBridgeInstrumentationTest.kt",
  contract: "docs/nodejs/design/OVERLAY_PROVIDER_V1.md",
  rhinoFloaty: "docs/nodejs/design/RHINO_FLOATY_COMPATIBILITY.md",
  floatyNode: "docs/nodejs/design/FLOATY_NODE.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  matrixGenerator: "tools/nodejs/phase11/generate-rhino-compat-matrix.js",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  surfaceDocs: "docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  gradle: "app/node-phase12-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const report = {
    schema: "autojs6-node-rhino-compat-overlay-live-gate-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatOverlayLiveGate",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      liveGate: checkById(checks, "android-live-gate"),
      noSilentBackgroundOverlay: checkById(checks, "no-background-overlay-provider"),
      packagedMetadata: checkById(checks, "packaged-metadata"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat overlay live gate verification: " + report.decision.status);
  console.log("Rhino compat overlay live gate policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const file = path.join(repoRoot, relative);
      return [key, { relative, text: read(file) }];
    }),
  );
}

function buildChecks(files) {
  const statusManifest = JSON.parse(files.statusManifest.text);
  const floatyModule = (statusManifest.modules || []).find((module) => module.id === "floaty");
  const surfaceManifest = JSON.parse(files.surfaceManifest.text);
  const floatySurface = (surfaceManifest.surfaces || []).find((surface) => surface.id === "floaty.overlay");
  const matrixFloatyRow = findMatrixRow(files.matrix.text, "floaty");
  return [
    check(
      "android-live-gate",
      includesAll(files.protocol.text, [
        "dispatchOverlayOpenPermissionSettings",
        "overlayPermissionSnapshot",
        "overlayCloseAllSummary",
        "overlayLiveProviderMissing",
        "Settings.ACTION_MANAGE_OVERLAY_PERMISSION",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "embedded_script.overlay_active_session_count",
        "embedded_script.overlay_permission_settings_launch_count",
        "P12-14 exposes only permission/settings/cleanup live-gate evidence",
      ]),
      "Android bridge must expose permission/settings/closeAll live-gate evidence while keeping overlay creation fail-closed.",
    ),
    check(
      "provider-registry-live-gate",
      includesAll(files.providerRegistry.text, [
        "UI_OVERLAY_PROVIDER -> NodeBridgeProviderStatus.DEGRADED",
        "liveGateMethods",
        "blockedLiveMethods",
        "packagedManifestPermission",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "foregroundDisclosure",
        "cleanupPolicy",
        "skipPolicy",
        "rawWindowLeakPolicy",
        "permission_settings_cleanup_gate_only",
      ]),
      "Provider registry must mark ui.overlay as a degraded live gate with packaged, foreground, cleanup, and raw-object denial metadata.",
    ),
    check(
      "no-background-overlay-provider",
      !files.executionModePolicy.text.includes('"ui.overlay"') &&
        includesAll(files.executionModePolicy.text, [
          "MODE_SCHEDULED",
          "allowedProviders = listOf(",
          '"work_manager"',
          "scheduled_task_strict",
        ]),
      "Execution mode policy must not allow ui.overlay for one-shot, interactive, scheduled, worker, or sandboxed providers.",
    ),
    check(
      "packaged-metadata",
      files.androidManifest.text.includes("android.permission.SYSTEM_ALERT_WINDOW") &&
        files.projectConfig.text.includes("android.permission.SYSTEM_ALERT_WINDOW"),
      "App and packaged project metadata must record SYSTEM_ALERT_WINDOW explicitly.",
    ),
    check(
      "runtime-types-dry-run",
      includesAll(files.runtime.text, [
        "request.dryRun = Boolean(opts.dryRun)",
        "canOpenSettings: Boolean(source.canOpenSettings)",
        "dryRun: Boolean(source.dryRun)",
      ]) &&
        includesAll(files.types.text, ["readonly dryRun?: boolean", "readonly canOpenSettings?: boolean"]) &&
        files.typeReadme.text.includes("P12-14"),
      "Runtime and types must expose dry-run settings evidence for ui.overlay.openPermissionSettings().",
    ),
    check(
      "instrumentation-live-gate",
      includesAll(files.instrumentation.text, [
        "NodeBridgeProviderStatus.DEGRADED",
        "settings-intent-dry-run",
        "rawWindowLeaked",
        "ui.overlay live creation must remain fail-closed after the P12-14 live gate",
        "embedded_script.execution_mode_allowed_providers",
        "P12-14",
      ]),
      "Instrumentation must cover degraded health, permission/settings dry-run, cleanup no-op, fail-closed show, and no background provider policy.",
    ),
    check(
      "docs-updated",
      includesAll(files.contract.text, ["P12-14", "live gate", "fail-closed"]) &&
        includesAll(files.rhinoFloaty.text, ["P12-14", "live gate", "permission/settings/cleanup"]) &&
        includesAll(files.floatyNode.text, ["P12-14", "live gate", "fail-closed"]) &&
        includesAll(files.migration.text, ["P12-14", "live gate", "production scripts"]) &&
        includesAll(files.securityModel.text, ["P12-14", "live gate", "scheduled/background silent overlays remain rejected"]),
      "Contract, design, migration, and security docs must record the P12-14 overlay live gate.",
    ),
    check(
      "status-manifest-p12-14",
      floatyModule &&
        floatyModule.status === "unsupported" &&
        includesAll(JSON.stringify(floatyModule), [
          P12_14_NOTE,
          "permission/settings/closeAll cleanup evidence",
          "SYSTEM_ALERT_WINDOW packaged metadata",
          "foreground disclosure requirements",
          "show/update/drainEvents/close live creation fail-closed",
          "scheduled/background silent overlays rejected",
          "raw-wrapper semantics remain blocked by design",
        ]),
      "Status manifest must record the P12-14 live gate without promoting exact Rhino raw-window parity.",
    ),
    check(
      "matrix-updated",
      includesAll(files.matrixGenerator.text, [
        P12_14_NOTE,
        "Proceed to P12-15 tasks disposable migration",
      ]) &&
        includesAll(matrixFloatyRow, [
          "| 53 | floaty | floaty | unsupported |",
          "ui.overlay",
          "Proceed to P12-15 tasks disposable migration",
        ]),
      "Generated matrix must advance floaty past P12-14 and point Phase 12 execution to P12-15.",
    ),
    check(
      "surface-manifest-live-gate",
      floatySurface &&
        floatySurface.status === "provider_poc" &&
        (floatySurface.tasks || []).includes("P12-14") &&
        includesAll(JSON.stringify(floatySurface), [
          "P12-14 overlay live gate",
          "verify-rhino-compat-overlay-live-gate.js",
          "verifyNodeRhinoCompatOverlayLiveGate",
          "SYSTEM_ALERT_WINDOW",
        ]) &&
        includesAll(files.surfaceDocs.text, ["floaty.overlay", "P12-14", "live gate", "fail-closed"]),
      "Surface manifest must keep floaty.overlay as provider_poc with P12-14 live-gate evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-14/.test(files.phase12Tasks.text) &&
        files.phase12Tasks.text.includes("verifyNodeRhinoCompatOverlayLiveGate") &&
        files.phase12Tasks.text.includes("SYSTEM_ALERT_WINDOW") &&
        files.phase12Tasks.text.includes("settings-intent-dry-run"),
      "Phase 12 task plan must mark P12-14 complete with live-gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatOverlayLiveGate") &&
        files.gradle.text.includes("verify-rhino-compat-overlay-live-gate.js") &&
        files.gradle.text.includes("NodeExecutionModePolicy.kt"),
      "Gradle must register verifyNodeRhinoCompatOverlayLiveGate.",
    ),
  ];
}

function findMatrixRow(markdown, moduleId) {
  return markdown.split(/\r?\n/).find((line) => line.includes(`| ${moduleId} |`)) || "";
}

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((checkItem) => checkItem.id === id);
  return item ? item.passed : false;
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat Overlay Live Gate Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Checks",
    "",
    "| Check | Result | Message |",
    "| --- | --- | --- |",
  ];
  report.checks.forEach((checkItem) => {
    lines.push(`| \`${checkItem.id}\` | ${checkItem.passed ? "passed" : "failed"} | ${escapeTable(checkItem.message || "")} |`);
  });
  return lines.join("\n") + "\n";
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

function read(file) {
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-rhino-compat-overlay-live-gate.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const eq = arg.indexOf("=");
    const rawKey = eq >= 0 ? arg.slice(2, eq) : arg.slice(2);
    const key = rawKey.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
    const value = eq >= 0 ? arg.slice(eq + 1) : argv[index + 1];
    if (eq < 0) {
      index += 1;
    }
    if (!value || value.startsWith("--")) {
      throw new Error("Missing value for " + arg);
    }
    options[key] = value;
  }
  return options;
}

main();
