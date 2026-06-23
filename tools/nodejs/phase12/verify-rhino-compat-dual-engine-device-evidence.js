#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-dual-engine-device-evidence-verification";
const REQUIRED_FIELDS = Object.freeze(["engine", "compatStatus", "providerMode", "resourceCleanupStatus", "skipReason"]);
const SELECTED_CASES = Object.freeze([
  "events-keys",
  "ui-compat-show-update-close",
  "tasks-disposable",
  "barcode-qrcode",
  "rhino-shim-disabled",
]);
const REQUIRED_STATUSES = Object.freeze(["supported", "supported_async", "partial", "blocked_by_design"]);

const FILES = {
  manifest: "docs/nodejs/dual-engine-compat/manifest.json",
  fixture: "docs/nodejs/dual-engine-compat/device-evidence/p12-33-fake-device-results.json",
  report: "docs/nodejs/reports/phase12/RHINO_DUAL_ENGINE_DEVICE_EVIDENCE.md",
  phase11Report: "docs/nodejs/reports/phase11/RHINO_DUAL_ENGINE_COMPAT_TESTS.md",
  instrumentationTest: "app/src/androidTest/java/org/autojs/autojs/engine/RhinoNodeDualEngineCompatInstrumentationTest.kt",
  buildGradle: "app/build.gradle.kts",
  phase11Verifier: "tools/nodejs/phase11/verify-rhino-dual-engine-compat-tests.js",
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
  const checks = buildChecks(files, repoRoot);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const fixture = JSON.parse(files.fixture.text);
  const report = {
    schema: "autojs6-node-rhino-compat-dual-engine-device-evidence-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatDualEngineDeviceEvidence",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    selectedCases: SELECTED_CASES,
    statusCounts: fixture.statusCounts || {},
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      manifestPromoted: checkById(checks, "manifest-device-evidence"),
      fixtureShape: checkById(checks, "fixture-result-shape"),
      instrumentationHook: checkById(checks, "instrumentation-hook"),
      surfaceManifest: checkById(checks, "surface-manifest"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat dual-engine device evidence verification: " + report.decision.status);
  console.log("Rhino compat dual-engine device evidence policy violations: " + policyViolations.length);
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
  const manifest = JSON.parse(files.manifest.text);
  const fixture = JSON.parse(files.fixture.text);
  const surfaceManifest = JSON.parse(files.surfaceManifest.text);
  const surface = (surfaceManifest.surfaces || []).find((entry) => entry.id === "dual_engine.device_evidence");
  const results = Array.isArray(fixture.results) ? fixture.results : [];
  const resultCounts = countStatuses(results);
  const syntax = checkSyntax(path.join(repoRoot, "tools/nodejs/phase12/verify-rhino-compat-dual-engine-device-evidence.js"));

  return [
    check(
      "manifest-device-evidence",
      manifest.deviceInstrumentation &&
        manifest.deviceInstrumentation.status === "fake_device_evidence" &&
        manifest.deviceInstrumentation.task === "P12-33" &&
        manifest.deviceInstrumentation.testClass === "org.autojs.autojs.engine.RhinoNodeDualEngineCompatInstrumentationTest" &&
        manifest.deviceInstrumentation.runnerMode === "fake_device_transport" &&
        manifest.deviceInstrumentation.resultFixture === "device-evidence/p12-33-fake-device-results.json" &&
        includesAll(manifest.deviceInstrumentation.requiredResultFields || [], REQUIRED_FIELDS) &&
        includesAll(manifest.deviceInstrumentation.requiredStatuses || [], ["supported_async", "blocked_by_design"]) &&
        includesAll(manifest.deviceInstrumentation.selectedDeviceCaseIds || [], SELECTED_CASES),
      "Dual-engine manifest must promote P12-33 fake device evidence with required fields, selected cases, fixture, and test class.",
    ),
    check(
      "fixture-result-shape",
      fixture.schema === "autojs6-rhino-dual-engine-device-evidence-v1" &&
        fixture.task === "P12-33" &&
        fixture.runnerMode === "fake_device_transport" &&
        includesAll(fixture.requiredResultFields || [], REQUIRED_FIELDS) &&
        results.length === SELECTED_CASES.length * 2 &&
        results.every((entry) =>
          SELECTED_CASES.includes(entry.caseId) &&
            ["rhino", "node"].includes(entry.engine) &&
            REQUIRED_STATUSES.includes(entry.compatStatus) &&
            typeof entry.providerMode === "string" &&
            ["closed", "not_applicable"].includes(entry.resourceCleanupStatus) &&
            Object.prototype.hasOwnProperty.call(entry, "skipReason") &&
            entry.skipReason === null),
      "P12-33 fixture must contain Rhino and Node result records with engine, compatStatus, providerMode, resourceCleanupStatus, and skipReason fields.",
    ),
    check(
      "fixture-case-coverage",
      SELECTED_CASES.every((caseId) => {
        const engines = results.filter((entry) => entry.caseId === caseId).map((entry) => entry.engine).sort();
        return engines.join(",") === "node,rhino";
      }) &&
        resultCounts.supported_async > 0 &&
        resultCounts.blocked_by_design > 0 &&
        JSON.stringify(fixture.statusCounts || {}) === JSON.stringify(resultCounts),
      "P12-33 fixture must cover each selected case for both engines and keep supported_async/blocked_by_design status buckets visible.",
    ),
    check(
      "report",
      includesAll(files.report.text, [
        "Status: P12-33 fake device instrumentation evidence landed",
        "`engine`",
        "`compatStatus`",
        "`providerMode`",
        "`resourceCleanupStatus`",
        "`skipReason`",
        "`events-keys`",
        "`barcode-qrcode`",
        "RhinoNodeDualEngineCompatInstrumentationTest",
        "verifyNodeRhinoCompatDualEngineDeviceEvidence",
      ]),
      "P12-33 report must document the result shape, selected cases, instrumentation hook, and Gradle gate.",
    ),
    check(
      "instrumentation-hook",
      includesAll(files.instrumentationTest.text, [
        "class RhinoNodeDualEngineCompatInstrumentationTest",
        "fakeDeviceTransportResultsUseP12RequiredShape",
        "fake_device_transport",
        "resourceCleanupStatus",
        "skipReason",
        "supported_async",
        "blocked_by_design",
        "rhino.dual_engine.device_evidence=",
      ]) &&
        SELECTED_CASES.every((caseId) => files.instrumentationTest.text.includes(caseId)),
      "Instrumentation hook must validate fake device result records and print a JSON evidence line.",
    ),
    check(
      "focused-gate-selector",
      includesAll(files.buildGradle.text, [
        "nodeJsRhinoDualEngineDeviceEvidenceInstrumentationClasses",
        "RhinoNodeDualEngineCompatInstrumentationTest",
        "\"verifyNodeRhinoCompatDualEngineDeviceEvidence\" to nodeJsRhinoDualEngineDeviceEvidenceInstrumentationClasses",
      ]),
      "app build.gradle must register the focused instrumentation selector for the P12-33 gate.",
    ),
    check(
      "phase11-docs-and-verifier-current",
      includesAll(files.phase11Report.text, [
        "P12-33 adds the",
        "p12-33-fake-device-results.json",
        "verifyNodeRhinoCompatDualEngineDeviceEvidence",
      ]) &&
        includesAll(files.phase11Verifier.text, [
          "fake_device_evidence",
          "allowing the later P12-33 fake device evidence state",
        ]),
      "Phase 11 dual-engine docs and verifier must accept the later P12-33 fake device evidence state.",
    ),
    check(
      "surface-manifest",
      surface &&
        ["provider_poc", "promoted"].includes(surface.status) &&
        (surface.tasks || []).includes("P12-33") &&
        (surface.tasks || []).includes("P12-34") &&
        includesAll(JSON.stringify(surface), [
          "RHINO_DUAL_ENGINE_DEVICE_EVIDENCE.md",
          "p12-33-fake-device-results.json",
          "RhinoNodeDualEngineCompatInstrumentationTest.kt",
          "verify-rhino-compat-dual-engine-device-evidence.js",
          "verifyNodeRhinoCompatDualEngineDeviceEvidence",
        ]) &&
        includesAll(files.surfaceDocs.text, [
          "`dual_engine.device_evidence`",
          "P12-33 adds fake device instrumentation",
          "evidence for high-value dual-engine",
          "`engine`",
          "`compatStatus`",
        ]),
      "Phase 12 surface manifest must keep dual_engine.device_evidence at provider_poc or promoted with P12-33 evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-33/.test(files.phase12Tasks.text) &&
        includesAll(files.phase12Tasks.text, [
          "RHINO_DUAL_ENGINE_DEVICE_EVIDENCE.md",
          "p12-33-fake-device-results.json",
          "RhinoNodeDualEngineCompatInstrumentationTest.kt",
          "verify-rhino-compat-dual-engine-device-evidence.js",
          "verifyNodeRhinoCompatDualEngineDeviceEvidence",
          "`resourceCleanupStatus`",
        ]),
      "Phase 12 task plan must mark P12-33 complete with report, fixture, instrumentation hook, verifier, and gate evidence.",
    ),
    check(
      "gradle-gate",
      includesAll(files.gradle.text, [
        "verifyNodeRhinoCompatDualEngineDeviceEvidence",
        "verify-rhino-compat-dual-engine-device-evidence.js",
        "verifyNodeRhinoCompatS13nBytesPromotion",
        "RHINO_DUAL_ENGINE_DEVICE_EVIDENCE.md",
        "rhino-compat-dual-engine-device-evidence-verification.json",
      ]),
      "Gradle must register verifyNodeRhinoCompatDualEngineDeviceEvidence after the P12-32 gate.",
    ),
    check(
      "syntax-verifier",
      syntax.ok,
      `P12-33 verifier must pass node --check${syntax.ok ? "" : ": " + syntax.message}`,
    ),
  ];
}

function countStatuses(results) {
  const counts = { supported: 0, supported_async: 0, partial: 0, blocked_by_design: 0 };
  results.forEach((entry) => {
    if (Object.prototype.hasOwnProperty.call(counts, entry.compatStatus)) {
      counts[entry.compatStatus] += 1;
    }
  });
  return counts;
}

function checkSyntax(file) {
  if (!fs.existsSync(file)) {
    return { ok: false, message: "missing file" };
  }
  const result = childProcess.spawnSync(process.execPath, ["--check", file], {
    encoding: "utf8",
    windowsHide: true,
  });
  return {
    ok: result.status === 0,
    message: String(result.stderr || result.stdout || "").trim(),
  };
}

function includesAll(textOrArray, values) {
  if (Array.isArray(textOrArray)) {
    return values.every((value) => textOrArray.includes(value));
  }
  return values.every((value) => String(textOrArray).includes(value));
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((checkItem) => checkItem.id === id);
  return Boolean(item && item.passed);
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat Dual-Engine Device Evidence Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Selected Cases",
    "",
  ];
  report.selectedCases.forEach((caseId) => lines.push(`- \`${caseId}\``));
  lines.push("", "## Status Counts", "");
  Object.keys(report.statusCounts).sort().forEach((status) => {
    lines.push(`- \`${status}\`: ${report.statusCounts[status]}`);
  });
  lines.push("", "## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
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
      console.log("Usage: node verify-rhino-compat-dual-engine-device-evidence.js [--repo-root dir] [--report-dir dir]");
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
