#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-barcode-examples-verification";

const FILES = {
  nodeExample: "docs/nodejs/examples/rhino-compat/10-barcode.node.cjs",
  rhinoExample: "docs/nodejs/examples/rhino-compat/10-barcode.rhino.js",
  examplesManifest: "docs/nodejs/examples/rhino-compat/manifest.json",
  examplesReadme: "docs/nodejs/examples/rhino-compat/README.md",
  examplesDoc: "docs/nodejs/EXAMPLES.md",
  examplesVerifier: "tools/nodejs/phase11/verify-rhino-compat-examples.js",
  report: "docs/nodejs/reports/phase12/RHINO_BARCODE_QRCODE_EXAMPLES.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  surfaceDocs: "docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  matrixGenerator: "tools/nodejs/phase11/generate-rhino-compat-matrix.js",
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
  const report = {
    schema: "autojs6-node-rhino-compat-barcode-examples-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatBarcodeExamples",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      examplesCompile: checkById(checks, "syntax-examples"),
      packagedPolicy: checkById(checks, "packaged-policy"),
      surfacePromoted: checkById(checks, "surface-manifest"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat barcode examples verification: " + report.decision.status);
  console.log("Rhino compat barcode examples policy violations: " + policyViolations.length);
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
  const examplesManifest = JSON.parse(files.examplesManifest.text);
  const barcodeExample = (examplesManifest.examples || []).find((entry) => entry.id === "barcode-qrcode");
  const statusManifest = JSON.parse(files.statusManifest.text);
  const barcodeModule = (statusManifest.modules || []).find((module) => module.id === "barcode");
  const qrcodeModule = (statusManifest.modules || []).find((module) => module.id === "barcode.qr_code");
  const surfaceManifest = JSON.parse(files.surfaceManifest.text);
  const barcodeSurface = (surfaceManifest.surfaces || []).find((surface) => surface.id === "barcode.qrcode");
  const nodeSyntax = checkSyntax(path.join(repoRoot, files.nodeExample.relative));
  const rhinoSyntax = checkSyntax(path.join(repoRoot, files.rhinoExample.relative));
  const verifierSyntax = checkSyntax(path.join(repoRoot, "tools/nodejs/phase12/verify-rhino-compat-barcode-examples.js"));
  const examplesVerifierSyntax = checkSyntax(path.join(repoRoot, "tools/nodejs/phase11/verify-rhino-compat-examples.js"));
  const barcodeMatrixRow = findMatrixRow(files.matrix.text, "barcode");
  const qrcodeMatrixRow = findMatrixRow(files.matrix.text, "barcode.qr_code");

  return [
    check(
      "node-example-shape",
      files.nodeExample.text.startsWith("\"nodejs\";") &&
        includesAll(files.nodeExample.text, [
          "require(\"autojs6:compat\")",
          "async function main()",
          "AUTOJS6_RUN_BARCODE_EXAMPLE",
          "compat.barcode.detectAll(",
          "compat.barcode.recognizeText(",
          "compat.qrcode.detectAll(",
          "compat.images.read(",
          "compat.images.recycle(",
          "fixtures/barcodes.png",
          "fixtures/qrcode.png",
          "formats: [\"QR_CODE\", \"EAN_13\"]",
          "region: [0, 0, 500, 500]",
          "main().catch",
        ]),
      "Node barcode example must cover path input, image handle input, QR-only facade, region, recycle, and gated runtime execution.",
    ),
    check(
      "rhino-example-shape",
      !files.rhinoExample.text.includes("\"nodejs\"") &&
        includesAll(files.rhinoExample.text, [
          "barcode.detectAll(",
          "barcode.recognizeText(",
          "qrcode.detectAll(",
          "images.read(",
          "images.recycle(",
          "fixtures/barcodes.png",
          "fixtures/qrcode.png",
        ]),
      "Rhino barcode example must show the legacy barcode/qrcode calls that the Node example migrates.",
    ),
    check(
      "syntax-examples",
      nodeSyntax.ok && rhinoSyntax.ok,
      `P12-30 barcode examples must pass node --check${nodeSyntax.ok && rhinoSyntax.ok ? "" : ": " + [nodeSyntax.message, rhinoSyntax.message].filter(Boolean).join(" | ")}`,
    ),
    check(
      "example-manifest-docs",
      barcodeExample &&
        barcodeExample.rhino === "10-barcode.rhino.js" &&
        barcodeExample.node === "10-barcode.node.cjs" &&
        barcodeExample.status === "static_verified" &&
        Array.isArray(barcodeExample.coverage) &&
        barcodeExample.coverage.includes("barcode") &&
        barcodeExample.coverage.includes("qrcode") &&
        barcodeExample.coverage.includes("image") &&
        includesAll(files.examplesReadme.text, ["P12-30", "`barcode-qrcode`", "compat.qrcode.detectAll()"]) &&
        includesAll(files.examplesDoc.text, ["P12-30 adds barcode/qrcode examples", "QR-only filtering"]) &&
        includesAll(files.migration.text, ["P12-30 adds `docs/nodejs/examples/rhino-compat/10-barcode.*`", "verifyNodeRhinoCompatBarcodeExamples"]) &&
        includesAll(files.examplesVerifier.text, ["\"barcode-qrcode\"", "\"barcode\"", "\"qrcode\"", "compat.qrcode.detectAll("]) &&
        examplesVerifierSyntax.ok,
      `Example manifest, README, EXAMPLES, migration guide, and static example gate must include barcode/qrcode${examplesVerifierSyntax.ok ? "" : ": " + examplesVerifierSyntax.message}`,
    ),
    check(
      "packaged-policy",
      includesAll(files.report.text, [
        "Status: P12-30 barcode/qrcode examples and packaged policy evidence landed",
        "project-relative image path input",
        "provider-owned image handle input",
        "QR-only filtering",
        "explicit temporary image cleanup",
        "Packaged barcode/qrcode support is provider-dependent",
        "stable unavailable/degraded bridge error",
        "must not receive raw ML Kit",
        "verifyNodeRhinoCompatBarcodeExamples",
      ]),
      "P12-30 report must document examples, packaged provider-dependent stable rejection, and raw object denial.",
    ),
    check(
      "status-manifest-notes",
      [barcodeModule, qrcodeModule].every((module) =>
        module &&
          module.status === "supported_async" &&
          includesAll(JSON.stringify(module), [
            "P12-30 adds barcode/qrcode migration examples",
            "RHINO_BARCODE_QRCODE_EXAMPLES.md",
          ])),
      "Status manifest must record P12-30 examples and packaged policy evidence for barcode and qrcode.",
    ),
    check(
      "matrix-next-step",
      includesAll(files.matrixGenerator.text, [
        "P12-30 adds barcode/qrcode migration examples",
        "Proceed to P12-31 unsupported utility-heavy module triage",
      ]) &&
        includesAll(barcodeMatrixRow, [
          "| 26 | barcode | barcode | supported_async |",
          "supported_async:5",
          "Proceed to P12-31 unsupported utility-heavy module triage",
        ]) &&
        includesAll(qrcodeMatrixRow, [
          "| 27 | barcode.qr_code | qrcode | supported_async |",
          "supported_async:5",
          "Proceed to P12-31 unsupported utility-heavy module triage",
        ]),
      "Generated matrix must advance barcode/qrcode from P12-30 to P12-31.",
    ),
    check(
      "surface-manifest",
      barcodeSurface &&
        barcodeSurface.status === "promoted" &&
        (barcodeSurface.tasks || []).includes("P12-30") &&
        includesAll(JSON.stringify(barcodeSurface), [
          "RHINO_BARCODE_QRCODE_EXAMPLES.md",
          "10-barcode.node.cjs",
          "10-barcode.rhino.js",
          "verify-rhino-compat-barcode-examples.js",
          "verifyNodeRhinoCompatBarcodeExamples",
        ]) &&
        includesAll(files.surfaceDocs.text, [
          "barcode.qrcode",
          "P12-30 adds migration examples",
          "provider-dependent smoke policy evidence",
        ]),
      "Phase 12 surface manifest must promote barcode.qrcode with P12-30 example and packaged policy evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-30/.test(files.phase12Tasks.text) &&
        includesAll(files.phase12Tasks.text, [
          "10-barcode.node.cjs",
          "10-barcode.rhino.js",
          "RHINO_BARCODE_QRCODE_EXAMPLES.md",
          "verify-rhino-compat-barcode-examples.js",
          "verifyNodeRhinoCompatBarcodeExamples",
        ]),
      "Phase 12 task plan must mark P12-30 complete with examples, report, and gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatBarcodeExamples") &&
        files.gradle.text.includes("verify-rhino-compat-barcode-examples.js") &&
        files.gradle.text.includes("verifyNodeRhinoCompatBarcodeFacade") &&
        files.gradle.text.includes("10-barcode.node.cjs") &&
        files.gradle.text.includes("RHINO_BARCODE_QRCODE_EXAMPLES.md"),
      "Gradle must register verifyNodeRhinoCompatBarcodeExamples after the barcode facade gate.",
    ),
    check(
      "syntax-verifier",
      verifierSyntax.ok,
      `P12-30 verifier must pass node --check${verifierSyntax.ok ? "" : ": " + verifierSyntax.message}`,
    ),
  ];
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

function findMatrixRow(text, moduleId) {
  return text.split(/\r?\n/).find((line) => line.includes(`| ${moduleId} |`)) || "";
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
    "# Rhino Compat Barcode Examples Verification",
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
      console.log("Usage: verify-rhino-compat-barcode-examples.js [--repo-root PATH] [--report-dir PATH]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
