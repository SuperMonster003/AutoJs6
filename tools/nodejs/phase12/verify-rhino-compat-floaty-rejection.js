#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-floaty-rejection-verification";

const FILES = {
  report: "docs/nodejs/reports/phase12/RHINO_FLOATY_REJECTION.md",
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  dualManifest: "docs/nodejs/dual-engine-compat/manifest.json",
  dualNode: "docs/nodejs/dual-engine-compat/cases/floaty-window.node.cjs",
  dualRhino: "docs/nodejs/dual-engine-compat/cases/floaty-window.rhino.js",
  rhinoFloaty: "docs/nodejs/design/RHINO_FLOATY_COMPATIBILITY.md",
  floatyNode: "docs/nodejs/design/FLOATY_NODE.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  surfaceDocs: "docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  gradle: "app/node-phase12-gate.gradle.kts",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  resourceRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeResourceRegistry.kt",
  compatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const report = {
    schema: "autojs6-node-rhino-compat-floaty-rejection-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatFloatyRejection",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      runtimeAbsence: checkById(checks, "instrumentation-runtime-absence"),
      blockedMarker: checkById(checks, "dual-engine-blocked-markers"),
      surfaceRejected: checkById(checks, "surface-manifest-rejected"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat floaty rejection verification: " + report.decision.status);
  console.log("Rhino compat floaty rejection policy violations: " + policyViolations.length);
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
  const entries = new Map((floatyModule && floatyModule.apiEntries || []).map((entry) => [entry.id, entry]));
  const surfaceManifest = JSON.parse(files.surfaceManifest.text);
  const floatySurface = (surfaceManifest.surfaces || []).find((surface) => surface.id === "floaty.overlay");
  const dualManifest = JSON.parse(files.dualManifest.text);
  const floatyCase = (dualManifest.cases || []).find((entry) => entry.id === "floaty-window");
  return [
    check(
      "instrumentation-runtime-absence",
      includesAll(files.conformanceTest.text, [
        "expectRequireUnavailable(name)",
        'expectRequireUnavailable("floaty")',
        'expectRequireUnavailable("ui.overlay")',
        "typeof compat.floaty",
        "globalThis.floaty",
        'availableGlobalNames.indexOf("floaty")',
        "compat.install({ globals: true })",
      ]),
      "Instrumentation must assert require(\"floaty\"), require(\"ui.overlay\"), compat.floaty, and global floaty remain absent.",
    ),
    check(
      "dual-engine-blocked-markers",
      floatyCase &&
        (floatyCase.statusManifestIds || []).includes("floaty.window") &&
        (floatyCase.statusManifestIds || []).includes("floaty.rawWindow") &&
        includesAll(files.dualNode.text, [
          "compat.blockedByDesign(",
          "compat.errorCodes.blockedByDesign",
          '"floaty.window"',
          '"floaty.rawWindow"',
          "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN",
        ]) &&
        includesAll(files.dualRhino.text, [
          "floaty.window(",
          "floaty.rawWindow(",
          ".close()",
        ]),
      "Dual-engine policy fixture must cover floaty.window and floaty.rawWindow blocked-by-design markers.",
    ),
    check(
      "stable-error-code-docs",
      includesAll(files.report.text, [
        "P12-12B",
        "require(\"floaty\")",
        "require(\"ui.overlay\")",
        "compat.floaty",
        "global `floaty`",
        "compat.blockedByDesign",
        "compat.errorCodes.blockedByDesign",
        "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN",
        "clipboard",
        "compat.getClip",
        "require(\"ui\").showLayout",
      ]) &&
        includesAll(files.rhinoFloaty.text, ["P12-12B", "compat.blockedByDesign", "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN"]) &&
        includesAll(files.floatyNode.text, ["P12-12B", "require(\"floaty\")", "compat.floaty", "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN"]) &&
        includesAll(files.migration.text, ["P12-12B", "compat.blockedByDesign", "compat.getClip()", "require(\"ui\").showLayout"]) &&
        includesAll(files.securityModel.text, ["P12-12B", "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN", "SYSTEM_ALERT_WINDOW"]),
      "Report and docs must document stable blocked-by-design markers and migration paths.",
    ),
    check(
      "status-manifest-rejection",
      floatyModule &&
        floatyModule.status === "unsupported" &&
        includesAll(JSON.stringify(floatyModule), [
          "P12-12B stabilizes the negative runtime gate",
          "require(\\\"floaty\\\")",
          "require(\\\"ui.overlay\\\")",
          "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN",
        ]) &&
        entryIs(entries, "floaty.window", "blocked_by_design", "s11_24_xml_window_wrapper_blocked") &&
        entryIs(entries, "floaty.rawWindow", "blocked_by_design", "s11_24_raw_overlay_window_blocked") &&
        entryIs(entries, "floaty.getClip", "renamed", "s11_24_clipboard_replacement_no_overlay") &&
        entryIs(entries, "floaty.closeAll", "unsupported", "s11_24_overlay_session_provider_deferred"),
      "Status manifest must preserve exact floaty rejection, clipboard migration, and no provider/closeAll support.",
    ),
    check(
      "surface-manifest-rejected",
      floatySurface &&
        floatySurface.status === "rejected" &&
        (floatySurface.tasks || []).includes("P12-12B") &&
        includesAll(JSON.stringify(floatySurface), [
          "RHINO_FLOATY_REJECTION.md",
          "NodeBridgeConformanceInstrumentationTest.kt",
          "floaty-window.node.cjs",
          "verify-rhino-compat-floaty-rejection.js",
          "verifyNodeRhinoCompatFloatyRejection",
        ]) &&
        includesAll(files.surfaceDocs.text, ["Current Rejected Surfaces", "P12-12B", "compat.floaty", "global `floaty`"]),
      "Phase 12 surface manifest must mark the exact Rhino floaty surface rejected with P12-12B evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-12B/.test(files.phase12Tasks.text) &&
        files.phase12Tasks.text.includes("RHINO_FLOATY_REJECTION.md") &&
        files.phase12Tasks.text.includes("verifyNodeRhinoCompatFloatyRejection") &&
        files.phase12Tasks.text.includes("ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN"),
      "Phase 12 task plan must mark P12-12B complete with report, error code, and gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatFloatyRejection") &&
        files.gradle.text.includes("verify-rhino-compat-floaty-rejection.js") &&
        files.gradle.text.includes("RHINO_FLOATY_REJECTION.md") &&
        files.gradle.text.includes("NodeBridgeConformanceInstrumentationTest.kt"),
      "Gradle must register verifyNodeRhinoCompatFloatyRejection.",
    ),
    check(
      "no-runtime-provider-surface",
      !files.runtime.text.includes("function __autojs6_limited_floaty") &&
        !files.runtime.text.includes("function __autojs6_rhino_compat_floaty") &&
        !files.runtime.text.includes("compat.floaty") &&
        !files.runtime.text.includes('name === "floaty"') &&
        !files.runtime.text.includes('name === "ui.overlay"') &&
        !files.compatTypes.text.includes("floaty") &&
        !files.compatTypes.text.includes("ui.overlay") &&
        !files.permissionManifest.text.includes("ui.overlay") &&
        !files.permissionManifest.text.includes('"floaty"') &&
        !/\bFLOATY\b/.test(files.permissionManifest.text) &&
        !/\bFLOATY\b/.test(files.resourceRegistry.text) &&
        !/\bOVERLAY_WINDOW\b/.test(files.resourceRegistry.text),
      "P12-12B must not add floaty/ui.overlay runtime modules, compat types, capabilities, or resources.",
    ),
  ];
}

function entryIs(entries, id, status, decision) {
  const entry = entries.get(id);
  return Boolean(entry) && entry.status === status && entry.decision === decision;
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  return checks.find((checkItem) => checkItem.id === id) || null;
}

function includesAll(text, needles) {
  return needles.every((needle) => String(text).includes(needle));
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-rhino-compat-floaty-rejection.js [--repo-root dir] [--report-dir dir]");
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
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat Floaty Rejection Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Checks",
    "",
  ];
  for (const checkItem of report.checks) {
    lines.push(`- ${checkItem.passed ? "[x]" : "[ ]"} \`${checkItem.id}\`: ${checkItem.message}`);
  }
  if (report.policyViolations.length > 0) {
    lines.push("", "## Violations", "");
    for (const violation of report.policyViolations) {
      lines.push(`- ${violation}`);
    }
  }
  return lines.join("\n") + "\n";
}

main();
