#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-developer-docs-sync-verification";

const FILES = {
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  pro9: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  security: "docs/nodejs/SECURITY_MODEL.md",
  testing: "docs/nodejs/TESTING.md",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  report: "docs/nodejs/reports/phase12/RHINO_DEVELOPER_DOCS_SYNC.md",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  surfaceDocs: "docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  matrixGenerator: "tools/nodejs/phase11/generate-rhino-compat-matrix.js",
  p12DeviceVerifier: "tools/nodejs/phase12/verify-rhino-compat-dual-engine-device-evidence.js",
  gradle: "app/node-phase12-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files, repoRoot);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const surfaceManifest = JSON.parse(files.surfaceManifest.text);
  const surface = (surfaceManifest.surfaces || []).find((entry) => entry.id === "dual_engine.device_evidence") || {};
  const report = {
    schema: "autojs6-node-rhino-compat-developer-docs-sync-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatDeveloperDocsSync",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: {
      id: surface.id || null,
      status: surface.status || null,
      tasks: surface.tasks || [],
    },
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      promotedExamples: checkById(checks, "migration-promoted-examples"),
      stableRejections: checkById(checks, "stable-rejection-guidance"),
      surfacePromoted: checkById(checks, "surface-manifest-promoted"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat developer docs sync verification: " + report.decision.status);
  console.log("Rhino compat developer docs sync policy violations: " + policyViolations.length);
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

function buildChecks(files, repoRoot) {
  const surfaceManifest = JSON.parse(files.surfaceManifest.text);
  const surface = (surfaceManifest.surfaces || []).find((entry) => entry.id === "dual_engine.device_evidence");
  const surfaceJson = JSON.stringify(surface || {});
  const promotedSection = sectionBetween(files.surfaceDocs.text, "## Current Promoted Surfaces", "## Current Provider POC Surfaces");
  const providerPocSection = sectionBetween(files.surfaceDocs.text, "## Current Provider POC Surfaces", "## Current Design-Locked Surfaces");
  const syntax = checkSyntax(repoRoot, FILES.p12DeviceVerifier, FILES.matrixGenerator, "tools/nodejs/phase12/verify-rhino-compat-developer-docs-sync.js");

  return [
    check(
      "migration-promoted-examples",
      includesAll(files.migration.text, [
        "P12-34 keeps the promoted Phase 12 compatibility surfaces visible",
        "compat.keys.HOME",
        "compat.events.keys.BACK",
        "compat.tasks.addDisposableTask",
        "barcode.detectAll",
        "compat.qrcode.recognizeTexts",
        "const s13n = require(\"s13n\")",
        "compat.s13n.bytes",
        "RhinoNodeDualEngineCompatInstrumentationTest",
      ]),
      "Migration guide must include P12-34 promoted examples for events.keys, compat.tasks, barcode/qrcode, s13n.bytes, and the P12-33 device hook.",
    ),
    check(
      "stable-rejection-guidance",
      includesAll(files.migration.text, [
        "ERR_AUTOJS6_RHINO_SHIM_DISABLED",
        "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED",
        "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED",
        "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN",
      ]) &&
        includesAll(files.pro9.text, [
          "ERR_AUTOJS6_RHINO_SHIM_DISABLED",
          "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED",
          "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED",
          "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN",
        ]) &&
        includesAll(files.report.text, [
          "ERR_AUTOJS6_RHINO_SHIM_DISABLED",
          "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED",
          "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED",
          "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN",
        ]),
      "Migration, Pro 9 comparison, and P12-34 report must keep stable rejection error codes visible.",
    ),
    check(
      "pro9-comparison-current",
      includesAll(files.pro9.text, [
        "### P12-34",
        "`events.keys`",
        "`compat.tasks.addDisposableTask()`",
        "`barcode` / `qrcode` 已完成 P12-27 到 P12-30",
        "`s13n.bytes` 已通过",
        "RhinoNodeDualEngineCompatInstrumentationTest",
        "| `barcode` / `qrcode` |",
        "`supported_async`",
        "| `s13n` / `jsox` / `util` |",
      ]) &&
        !files.pro9.text.includes("| `barcode` / `qrcode` | 条码/二维码识别或生成 | 暂无 JSON-safe barcode provider | provider gap |"),
      "Pro 9 comparison must include P12-34 current status and must not keep the old barcode/qrcode provider-gap row.",
    ),
    check(
      "matrix-current",
      includesAll(files.matrix.text, [
        "P12-34 keeps promoted Phase 12 examples",
        "| 19 | events.keys | keys | supported |",
        "| 26 | barcode | barcode | supported_async |",
        "| 27 | barcode.qr_code | qrcode | supported_async |",
        "| 32 | tasks | tasks | partial |",
        "| 38 | s13n | s13n | partial |",
      ]) &&
        files.matrixGenerator.text.includes("P12-34 keeps promoted Phase 12 examples"),
      "Compatibility matrix and generator must carry the P12-34 documentation-sync note and current promoted rows.",
    ),
    check(
      "security-testing-types-current",
      includesAll(files.security.text, [
        "P12-32 promotes `s13n.bytes`",
        "P12-33 dual-engine device evidence is a verification artifact",
        "P12-34 keeps that guidance",
        "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED",
      ]) &&
        includesAll(files.testing.text, [
          "verifyNodeRhinoCompatDualEngineDeviceEvidence",
          "verifyNodeRhinoCompatDeveloperDocsSync",
          "RhinoNodeDualEngineCompatInstrumentationTest",
          "P12-34 verifies",
        ]) &&
        includesAll(files.typesReadme.text, [
          "P12-34 is a documentation synchronization gate",
          "stable unsupported calls keep",
          "error codes",
          "fake dual-engine device evidence remains testing",
          "evidence only",
        ]),
      "Security model, testing guide, and type README must describe the P12-32/P12-33/P12-34 boundaries.",
    ),
    check(
      "surface-manifest-promoted",
      surface &&
        surface.status === "promoted" &&
        includesAll(surface.tasks || [], ["P12-33", "P12-34"]) &&
        includesAll(surface.evidence || [], [
          "docs/nodejs/MIGRATION_FROM_RHINO.md",
          "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
          "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
          "docs/nodejs/SECURITY_MODEL.md",
          "docs/nodejs/TESTING.md",
          "docs/nodejs/types/autojs6-node/README.md",
          "docs/nodejs/reports/phase12/RHINO_DEVELOPER_DOCS_SYNC.md",
          "tools/nodejs/phase12/verify-rhino-compat-developer-docs-sync.js",
          ":app:verifyNodeRhinoCompatDeveloperDocsSync",
        ]) &&
        includesAll(surfaceJson, [
          "p12-33-fake-device-results.json",
          "RhinoNodeDualEngineCompatInstrumentationTest.kt",
        ]),
      "Phase 12 surface manifest must promote dual_engine.device_evidence and list P12-34 docs/gate evidence.",
    ),
    check(
      "surface-docs-promoted",
      promotedSection.includes("`dual_engine.device_evidence`") &&
        promotedSection.includes("verifyNodeRhinoCompatDeveloperDocsSync") &&
        !providerPocSection.includes("`dual_engine.device_evidence`"),
      "Phase 12 surface docs must list dual_engine.device_evidence under promoted surfaces, not provider POC surfaces.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-34/.test(files.phase12Tasks.text) &&
        includesAll(files.phase12Tasks.text, [
          "RHINO_DEVELOPER_DOCS_SYNC.md",
          "verify-rhino-compat-developer-docs-sync.js",
          "verifyNodeRhinoCompatDeveloperDocsSync",
          "dual_engine.device_evidence",
          "promoted",
        ]),
      "Phase 12 task checklist must mark P12-34 complete with report, verifier, gate, and surface promotion evidence.",
    ),
    check(
      "gradle-gate",
      includesAll(files.gradle.text, [
        "verifyNodeRhinoCompatDeveloperDocsSync",
        "verify-rhino-compat-developer-docs-sync.js",
        "RHINO_DEVELOPER_DOCS_SYNC.md",
        "rhino-compat-developer-docs-sync-verification.json",
        "rhino-compat-developer-docs-sync-verification.md",
      ]),
      "Gradle must register the P12-34 developer docs sync verification gate and outputs.",
    ),
    check(
      "p12-33-verifier-forward-compatible",
      files.p12DeviceVerifier.text.includes("[\"provider_poc\", \"promoted\"].includes(surface.status)") &&
        files.p12DeviceVerifier.text.includes("provider_poc or promoted"),
      "P12-33 device evidence verifier must remain forward-compatible with the P12-34 promoted surface state.",
    ),
    check(
      "report",
      includesAll(files.report.text, [
        "Status: P12-34 formal migration and Pro 9 comparison documentation sync landed.",
        "Promoted API Examples",
        "Stable Rejections",
        "Surface Status",
        "verifyNodeRhinoCompatDeveloperDocsSync",
      ]),
      "P12-34 report must summarize promoted examples, stable rejections, surface status, and gate evidence.",
    ),
    check(
      "syntax",
      syntax.passed,
      syntax.message,
    ),
  ];
}

function checkSyntax(repoRoot, ...relatives) {
  const failures = relatives.filter((relative) => {
    const result = childProcess.spawnSync(process.execPath, ["--check", path.join(repoRoot, relative)], {
      cwd: repoRoot,
      encoding: "utf8",
    });
    return result.status !== 0;
  });
  return {
    passed: failures.length === 0,
    message: failures.length === 0
      ? "Verifier and generator JavaScript syntax checks must pass."
      : "JavaScript syntax checks failed for: " + failures.join(", "),
  };
}

function sectionBetween(text, start, end) {
  const startIndex = text.indexOf(start);
  if (startIndex < 0) {
    return "";
  }
  const endIndex = text.indexOf(end, startIndex + start.length);
  return text.slice(startIndex, endIndex < 0 ? text.length : endIndex);
}

function includesAll(textOrArray, values) {
  return values.every((value) => textOrArray.includes(value));
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
    "# Rhino Compat Developer Docs Sync Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Surface: \`${report.surface.id || "missing"}\``,
    `- Surface status: \`${report.surface.status || "missing"}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Checks",
    "",
    "| Check | Result | Message |",
    "| --- | --- | --- |",
  ];
  report.checks.forEach((checkItem) => {
    lines.push(`| \`${checkItem.id}\` | ${checkItem.passed ? "passed" : "failed"} | ${escapeTable(checkItem.message)} |`);
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
      console.log("Usage: node verify-rhino-compat-developer-docs-sync.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const key = arg.slice(2).replace(/-([a-z])/g, (_, letter) => letter.toUpperCase());
    const value = argv[index + 1];
    if (!value || value.startsWith("--")) {
      throw new Error("Missing value for " + arg);
    }
    options[key] = value;
    index += 1;
  }
  return options;
}

main();
