#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-barcode-provider-implementation-verification";
const METHODS = Object.freeze(["detect", "detectAll", "recognizeText", "recognizeTexts"]);

const FILES = {
  report: "docs/nodejs/reports/phase12/RHINO_BARCODE_PROVIDER_IMPLEMENTATION.md",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  contractReport: "docs/nodejs/reports/phase12/RHINO_BARCODE_PROVIDER_CONTRACT.md",
  contract: "docs/nodejs/design/BARCODE_PROVIDER_V1.md",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  manifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  registry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  resources: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeResourceRegistry.kt",
  barcodeTypes: "docs/nodejs/types/autojs6-node/barcode.d.ts",
  typeIndex: "docs/nodejs/types/autojs6-node/index.d.ts",
  typeReadme: "docs/nodejs/types/autojs6-node/README.md",
  barcodeTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBarcodeBridgeInstrumentationTest.kt",
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  providerRegistryTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeProviderRegistryInstrumentationTest.kt",
  doctorTest: "app/src/androidTest/java/org/autojs/autojs/ui/settings/NodeDoctorReportGeneratorInstrumentationTest.kt",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  surfaceDocs: "docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md",
  projectTool: "tools/nodejs/project/autojs6-node-project.js",
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
    schema: "autojs6-node-rhino-compat-barcode-provider-implementation-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatBarcodeProviderImplementation",
    methods: METHODS,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      runtimeProvider: checkById(checks, "runtime-provider"),
      androidProvider: checkById(checks, "android-provider"),
      compatStillPending: checkById(checks, "compat-facades-still-pending"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat barcode provider implementation verification: " + report.decision.status);
  console.log("Rhino compat barcode provider implementation policy violations: " + policyViolations.length);
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
  const barcodeCompatPending = [barcodeModule, qrcodeModule].every((module) =>
    module &&
      module.status === "partial" &&
      module.decision === "s11_20_barcode_bridge_gap_pending" &&
      (module.apiEntries || []).every((entry) => entry.status !== "supported_async"),
  );
  const barcodeCompatPromoted = [barcodeModule, qrcodeModule].every((module) =>
    module &&
      module.status === "supported_async" &&
      String(module.decision || "").indexOf("p12_29_") === 0 &&
      (module.apiEntries || []).every((entry) =>
        entry.status === "supported_async" &&
        entry.nodeModule === "autojs6:compat" &&
        entry.bridgeModule === "barcode"),
  );
  const barcodeSurface = (surfaceManifest.surfaces || []).find((surface) => surface.id === "barcode.qrcode");
  const verifierSyntax = checkSyntax(
    path.join(repoRoot, "tools/nodejs/phase12/verify-rhino-compat-barcode-provider-implementation.js"),
  );
  const projectToolSyntax = checkSyntax(path.join(repoRoot, "tools/nodejs/project/autojs6-node-project.js"));

  return [
    check(
      "report",
      includesAll(files.report.text, [
        "Status: P12-28 direct `barcode` bridge provider POC implemented",
        "NodeBridgeModules.BARCODE",
        "NodeBridgePermissionManifest.BARCODE",
        "NodeBridgeProviderRegistry",
        "NodeBridgeResourceType.BARCODE_OPERATION",
        "embedded_script.barcode_active_operation_count",
        "compat.barcode",
        "compat.qrcode",
        "verifyNodeRhinoCompatBarcodeProviderImplementation",
      ]),
      "P12-28 report must record runtime provider, capability, registry, diagnostics, and P12-29 handoff.",
    ),
    check(
      "android-provider",
      includesAll(files.protocol.text, [
        "const val BARCODE = \"barcode\"",
        "val barcodeMethods = setOf(\"detect\", \"detectAll\", \"recognizeText\", \"recognizeTexts\")",
        "dispatchBarcode(request",
        "BarcodeScanning.getClient",
        "BarcodeScannerOptions.Builder",
        "NodeBridgeResourceType.BARCODE_OPERATION",
        "barcodeOperations",
        "barcodeDiagnostics",
        "embedded_script.barcode_active_operation_count",
        "autojs6-node-barcode-snapshot-v1",
        "structuredPayloadJson",
        "FORMAT_QR_CODE",
        "TYPE_DRIVER_LICENSE",
      ]),
      "Android bridge protocol must implement the barcode module, ML Kit scanner, cleanup diagnostics, and JSON snapshots.",
    ),
    check(
      "permission-provider-registry",
      includesAll(files.manifest.text, [
        "const val BARCODE = \"barcode\"",
        "BARCODE,",
        "NodeBridgeModules.BARCODE -> listOf(BARCODE, IMAGE)",
        "\"qrcode\" to BARCODE",
      ]) &&
        includesAll(files.registry.text, [
          "BARCODE_PROVIDER",
          "modules = listOf(NodeBridgeModules.BARCODE)",
          "NodeBridgePermissionManifest.BARCODE",
          "mlKitBarcodeAvailable",
          "defaultEngine\" to \"mlkit-barcode\"",
          "liveMethods\" to \"detect,detectAll,recognizeText,recognizeTexts\"",
        ]) &&
        files.resources.text.includes("BARCODE_OPERATION(\"barcode\")"),
      "Permission manifest, provider registry, and resource registry must expose barcode capability and health.",
    ),
    check(
      "runtime-provider",
      includesAll(files.runtime.text, [
        "let __autojs6_limited_barcode_cache = null",
        "function __autojs6_limited_barcode()",
        "function __autojs6_barcode_options",
        "function __autojs6_barcode_recycle_after",
        "if (moduleName === \"barcode\") return [\"barcode\", \"image\"]",
        "if (name === \"barcode\")",
        "return __autojs6_limited_barcode()",
        "requireExistingPermission: true",
        "qrOnly mode accepts only QR_CODE format",
      ]) &&
        METHODS.every((method) => files.runtime.text.includes(method)),
      "Embedded JS runtime must expose require(\"barcode\") with options, temp-handle cleanup, permission preflight, and all methods.",
    ),
    check(
      "compat-facades-still-pending",
      barcodeCompatPromoted || (
        !includesAny(files.runtime.text, [
          "__autojs6_rhino_compat_barcode",
          "__autojs6_rhino_compat_qrcode",
          "compat.barcode",
          "compat.qrcode",
        ]) &&
          barcodeCompatPending
      ),
      "P12-28 must expose the direct provider; Rhino compat.barcode/qrcode may remain pending before P12-29 or be promoted by the later P12-29 facade.",
    ),
    check(
      "types",
      includesAll(files.barcodeTypes.text, [
        "declare module \"barcode\"",
        "BarcodeFormatName",
        "BarcodeImageInput",
        "BarcodeSnapshot",
        "detect(image:",
        "detectAll(image:",
        "recognizeText(image:",
        "recognizeTexts(image:",
        "CaptureImageInput",
      ]) &&
        files.typeIndex.text.includes("barcode.d.ts") &&
        includesAll(files.typeReadme.text, [
          "`barcode`",
          "P12-28 adds the direct controlled `barcode` provider",
          "compat.barcode",
          "compat.qrcode",
        ]),
      "Type declarations must expose the direct barcode module and remain compatible with later P12-29 compat facade declarations.",
    ),
    check(
      "tests",
      includesAll(files.barcodeTest.text, [
        "kotlinBarcodeBridgeRegistryAndFakeProviderAreStable",
        "jsBarcodeBridgeUsesFakeTransportPathInputsAndBlocksShadowing",
        "jsBarcodeRequiresManifestCapabilityBeforeDispatch",
        "kotlinBarcodeLiveProviderScansMlKitImageHandleAndCleansUp",
        "barcode_live_dispatch_count",
        "require(\"barcode\")",
      ]) &&
        includesAll(files.conformanceTest.text, [
          "const barcode = require(\"barcode\")",
          "expectExports(\"barcode\"",
          "barcode.detect",
          "barcode.recognizeTexts",
          "NodeBridgeModules.BARCODE",
        ]) &&
        files.providerRegistryTest.text.includes("id = \"barcode\"") &&
        files.doctorTest.text.includes("barcodeProvider"),
      "Instrumentation and conformance tests must cover fake provider shape, direct JS module, manifest denial, live gate, and health/doctor wiring.",
    ),
    check(
      "surface-manifest",
      barcodeSurface &&
        barcodeSurface.status === "provider_poc" &&
        (barcodeSurface.tasks || []).includes("P12-28") &&
        includesAll(JSON.stringify(barcodeSurface), [
          "require(\\\"barcode\\\")",
          "RHINO_BARCODE_PROVIDER_IMPLEMENTATION.md",
          "verify-rhino-compat-barcode-provider-implementation.js",
          "verifyNodeRhinoCompatBarcodeProviderImplementation",
        ]) &&
        includesAll(files.surfaceDocs.text, [
          "`barcode.qrcode`",
          "P12-28 adds the direct",
          "provider POC",
        ]),
      "Phase 12 surface manifest must promote barcode.qrcode to provider_poc with P12-28 evidence.",
    ),
    check(
      "project-tool",
      includesAll(files.projectTool.text, [
        "\"barcode\"",
        "[\"barcode\", \"barcode\"]",
        "qrcode",
        "ocr, barcode, network",
      ]) &&
        projectToolSyntax.ok,
      `Project helper must recognize barcode capabilities and remain syntactically valid${projectToolSyntax.ok ? "" : ": " + projectToolSyntax.message}`,
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-28/.test(files.phase12Tasks.text) &&
        includesAll(files.phase12Tasks.text, [
          "RHINO_BARCODE_PROVIDER_IMPLEMENTATION.md",
          "NodeBarcodeBridgeInstrumentationTest.kt",
          "verify-rhino-compat-barcode-provider-implementation.js",
          "verifyNodeRhinoCompatBarcodeProviderImplementation",
        ]),
      "Phase 12 task plan must mark P12-28 complete with implementation, tests, report, and gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatBarcodeProviderImplementation") &&
        files.gradle.text.includes("verify-rhino-compat-barcode-provider-implementation.js") &&
        files.gradle.text.includes("verifyNodeRhinoCompatBarcodeProviderContract") &&
        files.gradle.text.includes("NodeBarcodeBridgeInstrumentationTest.kt"),
      "Gradle must register verifyNodeRhinoCompatBarcodeProviderImplementation after the barcode contract gate.",
    ),
    check(
      "syntax-verifier",
      verifierSyntax.ok,
      `P12-28 verifier must pass node --check${verifierSyntax.ok ? "" : ": " + verifierSyntax.message}`,
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

function includesAny(text, values) {
  return values.some((value) => text.includes(value));
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
    "# Rhino Compat Barcode Provider Implementation Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Methods: ${report.methods.map((method) => `\`${method}\``).join(", ")}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Checks",
    "",
  ];
  report.checks.forEach((checkItem) => {
    lines.push(`- ${checkItem.passed ? "[x]" : "[ ]"} \`${checkItem.id}\`: ${checkItem.message}`);
  });
  if (report.policyViolations.length > 0) {
    lines.push("", "## Violations", "");
    report.policyViolations.forEach((violation) => lines.push(`- ${violation}`));
  }
  lines.push("");
  return lines.join("\n");
}

function read(file) {
  if (!fs.existsSync(file)) {
    throw new Error("Missing required file: " + file);
  }
  return fs.readFileSync(file, "utf8");
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
      console.log("Usage: node verify-rhino-compat-barcode-provider-implementation.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error("Unknown argument: " + arg);
    }
  }
  return options;
}

main();
