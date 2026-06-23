#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-barcode-facade-verification";
const METHODS = Object.freeze(["detect", "detectAll", "recognizeText", "recognizeTexts"]);

const FILES = {
  report: "docs/nodejs/reports/phase12/RHINO_BARCODE_COMPAT_FACADE.md",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  barcodeTypes: "docs/nodejs/types/autojs6-node/barcode.d.ts",
  compatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  typeReadme: "docs/nodejs/types/autojs6-node/README.md",
  barcodeTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBarcodeBridgeInstrumentationTest.kt",
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  surfaceDocs: "docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md",
  contract: "docs/nodejs/design/BARCODE_PROVIDER_V1.md",
  loaderDesign: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  providerImplementationReport: "docs/nodejs/reports/phase12/RHINO_BARCODE_PROVIDER_IMPLEMENTATION.md",
  gradle: "app/node-phase12-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files, repoRoot);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const report = {
    schema: "autojs6-node-rhino-compat-barcode-facade-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatBarcodeFacade",
    methods: METHODS,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      runtimeFacade: checkById(checks, "runtime-facade"),
      statusPromoted: checkById(checks, "status-manifest"),
      tests: checkById(checks, "tests"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat barcode facade verification: " + report.decision.status);
  console.log("Rhino compat barcode facade policy violations: " + policyViolations.length);
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
  const statusManifest = JSON.parse(files.statusManifest.text);
  const surfaceManifest = JSON.parse(files.surfaceManifest.text);
  const barcodeModule = (statusManifest.modules || []).find((module) => module.id === "barcode");
  const qrcodeModule = (statusManifest.modules || []).find((module) => module.id === "barcode.qr_code");
  const barcodeSurface = (surfaceManifest.surfaces || []).find((surface) => surface.id === "barcode.qrcode");
  const verifierSyntax = checkSyntax(path.join(repoRoot, "tools/nodejs/phase12/verify-rhino-compat-barcode-facade.js"));

  return [
    check(
      "report",
      includesAll(files.report.text, [
        "Status: P12-29 `compat.barcode` and `compat.qrcode` facades implemented",
        "compat.barcode.detect",
        "compat.qrcode.detect",
        "qrOnly: true",
        "compat.install({ globals: true })",
        "verifyNodeRhinoCompatBarcodeFacade",
      ]),
      "P12-29 report must record runtime surface, QR-only behavior, explicit globals, and gate evidence.",
    ),
    check(
      "runtime-facade",
      includesAll(files.runtime.text, [
        "let __autojs6_rhino_compat_barcode_cache = null",
        "let __autojs6_rhino_compat_qrcode_cache = null",
        "function __autojs6_rhino_compat_barcode_facade",
        "function __autojs6_rhino_compat_barcode()",
        "function __autojs6_rhino_compat_qrcode()",
        "output.qrOnly = true",
        "barcode: barcode",
        "qrcode: qrcode",
        "\"P12-29\"",
        "\"barcode\", \"qrcode\"",
      ]) &&
        METHODS.every((method) => files.runtime.text.includes(`${method}: { value: ${method}, enumerable: true }`)),
      "Runtime must expose frozen callable compat.barcode/qrcode facades, QR-only qrcode options, globals, and P12-29 status.",
    ),
    check(
      "types",
      includesAll(files.compatTypes.text, [
        "import barcodeModule = require(\"barcode\")",
        "RhinoBarcodeCompatFunction",
        "BarcodeImageInput",
        "BarcodeInputOptions",
        "readonly barcode: RhinoBarcodeCompatFunction",
        "readonly qrcode: RhinoBarcodeCompatFunction",
        "Promise<barcodeModule.BarcodeSnapshot | null>",
        "Promise<readonly string[]>",
      ]) &&
        includesAll(files.typeReadme.text, [
          "P12-29 adds Rhino-style `compat.barcode`",
          "`compat.qrcode`",
        ]) &&
        includesAll(files.barcodeTypes.text, [
          "BarcodeSnapshot",
          "recognizeText(image:",
          "recognizeTexts(image:",
        ]),
      "Type declarations must expose compat.barcode/qrcode over the direct barcode provider shapes.",
    ),
    check(
      "tests",
      includesAll(files.barcodeTest.text, [
        "const compat = require(\"autojs6:compat\")",
        "compat.barcode.detect",
        "compat.qrcode.recognizeTexts",
        "compat.qrcode.detect",
        "compat.barcode.global=true|true|true|true|true|true",
        "qrcode.request=barcode|detect|image-1|true|QR_CODE",
      ]) &&
        includesAll(files.conformanceTest.text, [
          "[\"autojs6:compat.barcode\", compat.barcode]",
          "[\"autojs6:compat.qrcode\", compat.qrcode]",
          "expectExports(\"autojs6:compat.barcode\"",
          "compat.status().implementedTasks.indexOf(\"P12-29\")",
          "compat.qrcode.detectAll",
        ]),
      "Instrumentation and conformance tests must cover facade exports, global install, QR-only dispatch, and provider routing.",
    ),
    check(
      "status-manifest",
      promotedBarcodeModule(barcodeModule, "p12_29_barcode_compat_facade", "barcode") &&
        promotedBarcodeModule(qrcodeModule, "p12_29_qrcode_compat_facade", "qrcode") &&
        statusManifest.totals &&
        statusManifest.totals.supported_async >= 64 &&
        statusManifest.totals.partial <= 320,
      "Status manifest must promote barcode and qrcode API entries to supported_async over autojs6:compat.",
    ),
    check(
      "surface-manifest",
      barcodeSurface &&
        barcodeSurface.status === "provider_poc" &&
        (barcodeSurface.tasks || []).includes("P12-29") &&
        includesAll(JSON.stringify(barcodeSurface), [
          "autojs6:compat.barcode",
          "autojs6:compat.qrcode",
          "RHINO_BARCODE_COMPAT_FACADE.md",
          "verify-rhino-compat-barcode-facade.js",
          "verifyNodeRhinoCompatBarcodeFacade",
        ]) &&
        includesAll(files.surfaceDocs.text, [
          "P12-29 adds Rhino-style",
          "`autojs6:compat.barcode`",
          "`autojs6:compat.qrcode`",
        ]),
      "Phase 12 surface manifest must record the P12-29 compat facade evidence.",
    ),
    check(
      "docs",
      includesAll(files.contract.text, [
        "P12-29 exposes `compat.barcode`",
        "`compat.qrcode`",
        "P12-30 still owns examples",
      ]) &&
        includesAll(files.loaderDesign.text, [
          "P12-29 adds `compat.barcode`",
          "QR-only `compat.qrcode`",
        ]) &&
        includesAll(files.migration.text, [
          "P12-29 exposes Rhino-style `compat.barcode`",
          "`compat.qrcode`",
        ]) &&
        includesAll(files.securityModel.text, [
          "P12-29 `compat.barcode`",
          "`compat.qrcode` forces QR_CODE filtering",
        ]) &&
        includesAll(files.providerImplementationReport.text, [
          "P12-29 now",
          "P12-30",
        ]),
      "Design, migration, security, and P12-28 implementation docs must record the P12-29 facade and P12-30 handoff.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-29/.test(files.phase12Tasks.text) &&
        includesAll(files.phase12Tasks.text, [
          "RHINO_BARCODE_COMPAT_FACADE.md",
          "verify-rhino-compat-barcode-facade.js",
          "verifyNodeRhinoCompatBarcodeFacade",
        ]),
      "Phase 12 task plan must mark P12-29 complete with report, tests, manifest, and gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatBarcodeFacade") &&
        files.gradle.text.includes("verify-rhino-compat-barcode-facade.js") &&
        files.gradle.text.includes("verifyNodeRhinoCompatBarcodeProviderImplementation"),
      "Gradle must register verifyNodeRhinoCompatBarcodeFacade after the barcode provider implementation gate.",
    ),
    check(
      "syntax-verifier",
      verifierSyntax.ok,
      `P12-29 verifier must pass node --check${verifierSyntax.ok ? "" : ": " + verifierSyntax.message}`,
    ),
  ];
}

function promotedBarcodeModule(module, decision, facadeName) {
  return Boolean(
    module &&
      module.status === "supported_async" &&
      module.asyncMode === "promise" &&
      module.decision === decision &&
      (module.nodeModules || []).includes("autojs6:compat") &&
      (module.bridgeModules || []).includes("barcode") &&
      (module.apiEntries || []).every((entry) =>
        entry.status === "supported_async" &&
        entry.asyncMode === "promise" &&
        entry.nodeModule === "autojs6:compat" &&
        entry.bridgeModule === "barcode" &&
        typeof entry.nodeName === "string" &&
        entry.nodeName.indexOf(facadeName + ".") === 0,
      ),
  );
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

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((checkItem) => checkItem.id === id);
  return Boolean(item && item.passed);
}

function read(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`Missing required file: ${file}`);
  }
  return fs.readFileSync(file, "utf8");
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat Barcode Facade Verification",
    "",
    `- Schema: \`${report.schema}\``,
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Checks",
    "",
  ];
  for (const checkItem of report.checks) {
    lines.push(`- ${checkItem.passed ? "[x]" : "[ ]"} \`${checkItem.id}\` - ${checkItem.message}`);
  }
  if (report.policyViolations.length > 0) {
    lines.push("", "## Policy Violations", "");
    for (const violation of report.policyViolations) {
      lines.push(`- ${violation}`);
    }
  }
  lines.push("");
  return lines.join("\n");
}

function parseArgs(args) {
  const options = {};
  for (let i = 0; i < args.length; i += 1) {
    const arg = args[i];
    if (arg === "--repo-root") {
      options.repoRoot = args[++i];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++i];
    } else if (arg === "--help" || arg === "-h") {
      console.log("Usage: verify-rhino-compat-barcode-facade.js [--repo-root PATH] [--report-dir PATH]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
