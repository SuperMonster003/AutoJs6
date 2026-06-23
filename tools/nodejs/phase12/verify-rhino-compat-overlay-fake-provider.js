#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-overlay-fake-provider-verification";
const METHODS = Object.freeze(["show", "update", "drainEvents", "close", "closeAll", "hasPermission", "openPermissionSettings"]);
const CAPABILITY_CONSTANTS = Object.freeze([
  "UI_OVERLAY",
  "UI_OVERLAY_PERMISSION",
  "UI_OVERLAY_FOREGROUND",
  "UI_OVERLAY_PACKAGED",
  "FLOATY_RAW",
  "FLOATY_TOUCH",
]);

const FILES = {
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  resourceRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeResourceRegistry.kt",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  types: "docs/nodejs/types/autojs6-node/ui_overlay.d.ts",
  typeIndex: "docs/nodejs/types/autojs6-node/index.d.ts",
  typeReadme: "docs/nodejs/types/autojs6-node/README.md",
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
  instrumentation: "app/src/androidTest/java/org/autojs/autojs/engine/NodeOverlayBridgeInstrumentationTest.kt",
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
    schema: "autojs6-node-rhino-compat-overlay-fake-provider-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatOverlayFakeProvider",
    methods: METHODS,
    capabilityConstants: CAPABILITY_CONSTANTS,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      runtimeSurface: checkById(checks, "runtime-module"),
      bridgeRegistry: checkById(checks, "kotlin-bridge-registry"),
      fakeTransportTest: checkById(checks, "instrumentation-fake-provider"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat overlay fake provider verification: " + report.decision.status);
  console.log("Rhino compat overlay fake provider policy violations: " + policyViolations.length);
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
      "runtime-module",
      includesAll(files.runtime.text, [
        "__autojs6_limited_ui_overlay_cache",
        "__autojs6_limited_ui_overlay",
        "__autojs6_ui_overlay_policy",
        'if (name === "ui.overlay")',
        'return __autojs6_limited_ui_overlay();',
        '"ui.overlay"',
        '"show"',
        '"drainEvents"',
        '"closeAll"',
        '"openPermissionSettings"',
      ]),
      'Runtime must expose require("ui.overlay") with show/update/drainEvents/close/closeAll/permission methods.',
    ),
    check(
      "runtime-permissions-validation",
      includesAll(files.runtime.text, [
        '__autojs6_bridge_default_permissions(moduleName, methodName)',
        'methodName === "hasPermission" || methodName === "openPermissionSettings"',
        'return ["ui.overlay.permission"]',
        'return ["ui.overlay"]',
        "__autojs6_ui_overlay_descriptor",
        "does not allow hidden overlays",
        "WindowManager.LayoutParams",
        "Object.freeze",
      ]),
      "Runtime wrapper must preflight capabilities, validate descriptors, reject hidden/raw overlay forms, and freeze JSON snapshots.",
    ),
    check(
      "kotlin-bridge-registry",
      includesAll(files.protocol.text, [
        'const val UI_OVERLAY = "ui.overlay"',
        'val uiOverlayMethods = setOf("show", "update", "drainEvents", "close", "closeAll", "hasPermission", "openPermissionSettings")',
        "dispatchUiOverlay",
      ]) &&
        (files.protocol.text.includes("P12-13 exposes only fake-transport POC coverage") ||
          files.protocol.text.includes("P12-14 exposes only permission/settings/cleanup live-gate evidence")),
      "Kotlin bridge registry must register ui.overlay methods and keep Android live dispatch fail-closed.",
    ),
    check(
      "permission-manifest",
      CAPABILITY_CONSTANTS.every((name) => files.permissionManifest.text.includes(`const val ${name}`)) &&
        includesAll(files.permissionManifest.text, [
          "NodeBridgeModules.UI_OVERLAY",
          "hasPermission",
          "openPermissionSettings",
          "listOf(UI_OVERLAY_PERMISSION)",
          "listOf(UI_OVERLAY)",
        ]),
      "Permission manifest must define overlay capabilities and method-specific capability requirements.",
    ),
    check(
      "resource-provider-registry",
      files.resourceRegistry.text.includes("OVERLAY_SESSION") &&
        includesAll(files.providerRegistry.text, [
          "UI_OVERLAY_PROVIDER",
          "NodeBridgeModules.UI_OVERLAY",
          "NodeBridgePermissionManifest.UI_OVERLAY",
          "liveProvider",
          "OVERLAY_SESSION",
        ]) &&
        (files.providerRegistry.text.includes("P12-13 fake provider POC") ||
          files.providerRegistry.text.includes("P12-14 live gate")) &&
        (files.providerRegistry.text.includes("not_promoted") ||
          files.providerRegistry.text.includes("permission_settings_cleanup_gate_only")),
      "Resource and provider registries must record overlay sessions and provider promotion status.",
    ),
    check(
      "types",
      includesAll(files.types.text, [
        'declare module "ui.overlay"',
        "OverlayDescriptor",
        "OverlayHandle",
        "OverlayPermissionSnapshot",
        "OverlayCloseSummary",
        "show(descriptor",
        "openPermissionSettings",
      ]) &&
        files.typeIndex.text.includes("./ui_overlay.d.ts") &&
        files.typeReadme.text.includes("`ui.overlay` is a P12-13 fake-provider POC"),
      "Type declarations and README must expose the ui.overlay fake-provider POC shape.",
    ),
    check(
      "instrumentation-fake-provider",
      includesAll(files.instrumentation.text, [
        "NodeOverlayBridgeInstrumentationTest",
        "kotlinOverlayRegistryManifestAndFailClosedProviderAreStable",
        "jsOverlayUsesFakeTransportAndBlocksShadowing",
        "jsOverlayRequiresManifestCapabilityBeforeDispatch",
        "bridge.__test.setTransport",
        "overlay.requests=ui.overlay.show|ui.overlay.update|ui.overlay.drainEvents|ui.overlay.close|ui.overlay.closeAll|ui.overlay.hasPermission|ui.overlay.openPermissionSettings",
        "overlay.permissions=ui.overlay|ui.overlay|ui.overlay|ui.overlay|ui.overlay|ui.overlay.permission|ui.overlay.permission",
        "overlay.pending=0",
        "BRIDGE_CAPABILITY_NOT_DECLARED",
      ]),
      "Instrumentation must cover fake transport, shadowing, handle close lifecycle, live fail-closed path, and manifest denial.",
    ),
    check(
      "docs-updated",
      includesAll(files.contract.text, ["P12-13", "fake-provider runtime POC", 'require("ui.overlay")']) &&
        includesAll(files.rhinoFloaty.text, ["P12-13", "ui.overlay", "fake provider POC"]) &&
        includesAll(files.floatyNode.text, ["P12-13", "ui.overlay", "fake-provider facade POC"]) &&
        includesAll(files.migration.text, ["P12-13", "ui.overlay", "fake-provider POC"]) &&
        includesAll(files.securityModel.text, ["P12-13", "ui.overlay", "fake transport"]),
      "Contract, design, migration, and security docs must record the P12-13 fake-provider POC.",
    ),
    check(
      "status-manifest-p12-13",
      floatyModule &&
        floatyModule.status === "unsupported" &&
        includesAll(JSON.stringify(floatyModule), [
          "ui.overlay",
          "P12-13 overlay fake provider POC landed",
          "fake transport",
          "P12-14 owns live overlay packaged and cleanup",
          "raw-wrapper semantics remain blocked by design",
        ]),
      "Status manifest must record the P12-13 ui.overlay POC without changing exact Rhino raw-window denial.",
    ),
    check(
      "matrix-updated",
      includesAll(files.matrixGenerator.text, [
        "P12-13 overlay fake provider POC landed",
        "Complete P12-14 live overlay packaged and cleanup gate",
        "Proceed to P12-15 tasks disposable migration",
      ]) &&
        includesAll(matrixFloatyRow, [
          "| 53 | floaty | floaty | unsupported |",
          "ui.overlay",
        ]) &&
        (matrixFloatyRow.includes("Complete P12-14 live overlay packaged and cleanup gate") ||
          matrixFloatyRow.includes("Proceed to P12-15 tasks disposable migration")),
      "Generated matrix must point floaty to P12-14 or later after P12-13 lands the fake provider.",
    ),
    check(
      "surface-manifest-provider-poc",
      floatySurface &&
        floatySurface.status === "provider_poc" &&
        (floatySurface.tasks || []).includes("P12-13") &&
        includesAll(JSON.stringify(floatySurface), [
          "ui.overlay",
          "ui_overlay.d.ts",
          "NodeOverlayBridgeInstrumentationTest.kt",
          "verify-rhino-compat-overlay-fake-provider.js",
          "verifyNodeRhinoCompatOverlayFakeProvider",
        ]) &&
        includesAll(files.surfaceDocs.text, ["floaty.overlay", "P12-13", "provider POC", "P12-14"]),
      "Phase 12 surface manifest must mark floaty.overlay as provider_poc with P12-13 evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-13/.test(files.phase12Tasks.text) &&
        files.phase12Tasks.text.includes("NodeOverlayBridgeInstrumentationTest.kt") &&
        files.phase12Tasks.text.includes("ui_overlay.d.ts") &&
        files.phase12Tasks.text.includes("verifyNodeRhinoCompatOverlayFakeProvider"),
      "Phase 12 task plan must mark P12-13 complete with fake-provider evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatOverlayFakeProvider") &&
        files.gradle.text.includes("verify-rhino-compat-overlay-fake-provider.js") &&
        files.gradle.text.includes("NodeOverlayBridgeInstrumentationTest.kt") &&
        files.gradle.text.includes("ui_overlay.d.ts"),
      "Gradle must register verifyNodeRhinoCompatOverlayFakeProvider.",
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
    "# Rhino Compat Overlay Fake Provider Verification",
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
      console.log("Usage: node verify-rhino-compat-overlay-fake-provider.js [--repo-root dir] [--report-dir dir]");
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
