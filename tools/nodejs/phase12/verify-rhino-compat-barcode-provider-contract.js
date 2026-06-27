#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-barcode-provider-contract-verification";
const CAPABILITIES = Object.freeze(["barcode", "image", "screen_capture"]);
const METHODS = Object.freeze(["detect", "detectAll", "recognizeText", "recognizeTexts"]);
const IMAGE_INPUT_TYPES = Object.freeze(["path", "imageHandle", "capture"]);
const SNAPSHOT_FIELDS = Object.freeze([
  "rawValue",
  "displayValue",
  "format",
  "formatName",
  "valueType",
  "valueTypeName",
]);
const STRUCTURED_PAYLOADS = Object.freeze([
  "email",
  "phone",
  "sms",
  "url",
  "wifi",
  "geoPoint",
  "contactInfo",
  "calendarEvent",
  "driverLicense",
]);
const LIMITS = Object.freeze([
  "maxResults",
  "maxFormats",
  "maxImageInputPathBytes",
  "maxRegionPixels",
  "maxStructuredPayloadDepth",
  "maxRawValueBytes",
]);

const FILES = {
  report: "docs/nodejs/reports/phase12/RHINO_BARCODE_PROVIDER_CONTRACT.md",
  contract: "docs/nodejs/design/BARCODE_PROVIDER_V1.md",
  schema: "docs/nodejs/schemas/barcode_provider_v1.schema.json",
  fixtures: "docs/nodejs/fixtures/barcode-provider-v1-fixtures.json",
  loaderDesign: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  surfaceDocs: "docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
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
    schema: "autojs6-node-rhino-compat-barcode-provider-contract-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatBarcodeProviderContract",
    capabilities: CAPABILITIES,
    methods: METHODS,
    imageInputTypes: IMAGE_INPUT_TYPES,
    snapshotFields: SNAPSHOT_FIELDS,
    structuredPayloads: STRUCTURED_PAYLOADS,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      schemaLocked: checkById(checks, "schema-contract"),
      fixturesLocked: checkById(checks, "fixtures"),
      compatStillClosed: checkById(checks, "compat-still-closed"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat barcode provider contract verification: " + report.decision.status);
  console.log("Rhino compat barcode provider policy violations: " + policyViolations.length);
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
  const schema = JSON.parse(files.schema.text);
  const fixtures = JSON.parse(files.fixtures.text);
  const statusManifest = JSON.parse(files.statusManifest.text);
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
  const surfaceManifest = JSON.parse(files.surfaceManifest.text);
  const barcodeSurface = (surfaceManifest.surfaces || []).find((surface) => surface.id === "barcode.qrcode");
  const schemaText = JSON.stringify(schema);
  const fixtureText = JSON.stringify(fixtures);
  const methodProperties = schema.properties && schema.properties.methods && schema.properties.methods.properties
    ? Object.keys(schema.properties.methods.properties)
    : [];
  const capabilityEnums = schema.properties && schema.properties.capabilities && schema.properties.capabilities.items
    ? schema.properties.capabilities.items.enum || []
    : [];
  const limitProperties = schema.$defs && schema.$defs.BarcodeLimits && schema.$defs.BarcodeLimits.properties
    ? schema.$defs.BarcodeLimits.properties
    : {};
  const snapshotRequired = schema.$defs && schema.$defs.BarcodeSnapshot
    ? schema.$defs.BarcodeSnapshot.required || []
    : [];
  const structuredProperties = schema.$defs && schema.$defs.StructuredPayload && schema.$defs.StructuredPayload.properties
    ? Object.keys(schema.$defs.StructuredPayload.properties)
    : [];
  const verifierSyntax = checkSyntax(
    path.join(repoRoot, "tools/nodejs/phase12/verify-rhino-compat-barcode-provider-contract.js"),
  );

  return [
    check(
      "report",
      includesAll(files.report.text, [
        "Status: P12-27 barcode/qrcode bridge schema design-locked",
        "BARCODE_PROVIDER_V1.md",
        "barcode_provider_v1.schema.json",
        "barcode-provider-v1-fixtures.json",
        "verifyNodeRhinoCompatBarcodeProviderContract",
        "Barcode/qrcode was a bridge gap after P12-27",
      ]),
      "P12-27 report must record design-locked schema evidence and later P12-28/P12-29 handoff state.",
    ),
    check(
      "contract-api",
      includesAll(files.contract.text, [
        "P12-27",
        "const barcode = require(\"barcode\")",
        "detect(image, options?)",
        "detectAll(image, options?)",
        "recognizeText(image, options?)",
        "recognizeTexts(image, options?)",
        "compat.barcode",
        "compat.qrcode",
        "does not expose a runtime module",
      ]),
      "Contract doc must lock the future barcode module, methods, and non-runtime P12-27 state.",
    ),
    check(
      "contract-image-input-options",
      IMAGE_INPUT_TYPES.every((inputType) => files.contract.text.includes(`type: "${inputType}"`)) &&
        includesAll(files.contract.text, [
          "project-relative",
          "opaque image handle",
          "existing screen-capture permission",
          "requireExistingPermission: true",
          "region",
          "formats",
          "format filter",
          "qrOnly: true",
          "formats: [\"QR_CODE\"]",
          "rejects any non-QR format filter",
        ]),
      "Contract doc must define path/handle/capture inputs, region, format filter, and QR-only behavior.",
    ),
    check(
      "contract-result-boundary",
      SNAPSHOT_FIELDS.every((field) => files.contract.text.includes(field)) &&
        STRUCTURED_PAYLOADS.every((field) => files.contract.text.includes(field)) &&
        includesAll(files.contract.text, [
          "boundingBox",
          "cornerPoints",
          "selected structured payloads as JSON",
          "ML Kit `Barcode`",
          "WrappedBarcode",
          "ImageWrapper",
          "Bitmap",
          "InputImage",
          "Java/Android object",
        ]),
      "Contract doc must define JSON-safe result fields, structured payloads, and raw object denial.",
    ),
    check(
      "contract-limits-handoff",
      LIMITS.every((limit) => files.contract.text.includes(limit)) &&
        includesAll(files.contract.text, [
          "NodeBridgeModules.BARCODE",
          "NodeBridgePermissionManifest.BARCODE",
          "fake-provider shape tests",
          "live ML Kit provider gate",
          "Packaged APK support remains provider-dependent until P12-30",
        ]),
      "Contract doc must lock limits and P12-28/P12-29/P12-30 handoff requirements.",
    ),
    check(
      "schema-contract",
      schema.properties &&
        schema.properties.schema &&
        schema.properties.schema.const === "autojs6-node-barcode-provider-v1" &&
        schema.properties.module &&
        schema.properties.module.const === "barcode" &&
        schema.properties.qrcodeFacade &&
        schema.properties.qrcodeFacade.const === "qr-only-format-filter",
      "Schema must identify the barcode provider v1 module and qrcode QR-only facade contract.",
    ),
    check(
      "schema-methods-capabilities",
      METHODS.every((method) => methodProperties.includes(method)) &&
        CAPABILITIES.every((capability) => capabilityEnums.includes(capability)),
      "Schema must lock barcode methods and barcode/image/screen_capture capabilities.",
    ),
    check(
      "schema-image-input-options",
      includesAll(schemaText, [
        "\"PathImageInput\"",
        "\"ImageHandleInput\"",
        "\"CaptureImageInput\"",
        "\"const\":\"path\"",
        "\"const\":\"imageHandle\"",
        "\"const\":\"capture\"",
        "\"requireExistingPermission\"",
        "\"Region\"",
        "\"formats\"",
        "\"qrOnly\"",
        "\"QR_CODE\"",
        "\"EAN_13\"",
      ]),
      "Schema must define path/handle/capture inputs, region, format filters, and QR-only format names.",
    ),
    check(
      "schema-snapshot-payloads",
      SNAPSHOT_FIELDS.every((field) => snapshotRequired.includes(field)) &&
        STRUCTURED_PAYLOADS.every((field) => structuredProperties.includes(field)) &&
        includesAll(schemaText, [
          "\"autojs6-node-barcode-snapshot-v1\"",
          "\"boundingBox\"",
          "\"cornerPoints\"",
          "\"StructuredPayload\"",
          "\"BarcodeValueTypeName\"",
        ]),
      "Schema must require core snapshot fields and define JSON structured payloads.",
    ),
    check(
      "schema-limits",
      LIMITS.every((limit) => Object.prototype.hasOwnProperty.call(limitProperties, limit)) &&
        limitProperties.maxResults.const === 128 &&
        limitProperties.maxFormats.const === 16 &&
        limitProperties.maxImageInputPathBytes.const === 4096 &&
        limitProperties.maxRegionPixels.const === 16777216 &&
        limitProperties.maxStructuredPayloadDepth.const === 8 &&
        limitProperties.maxRawValueBytes.const === 8192,
      "Schema must lock barcode provider limits.",
    ),
    check(
      "fixtures",
      fixtures.schema === "autojs6-node-barcode-provider-v1-fixtures" &&
        Array.isArray(fixtures.accepted) &&
        fixtures.accepted.length >= 3 &&
        Array.isArray(fixtures.rejected) &&
        fixtures.rejected.length >= 8 &&
        includesAll(fixtureText, [
          "path-detect-all-mixed-formats",
          "image-handle-qrcode-detect",
          "capture-recognize-text-existing-permission",
          "raw-mlkit-barcode",
          "wrapped-barcode",
          "imagewrapper-input",
          "bitmap-input",
          "qrcode-non-qr-format",
          "temporaryImageRecycled",
          "doesNotPromptForPermission",
        ]),
      "Fixtures must cover path, image handle, capture, QR-only, cleanup, and raw-object rejection.",
    ),
    check(
      "docs-updated",
      includesAll(files.loaderDesign.text, [
        "P12-27 locks",
        "BARCODE_PROVIDER_V1.md",
        "barcode_provider_v1.schema.json",
        "P12-28 exposes the direct `require(\"barcode\")` provider",
        "compat.barcode",
        "compat.qrcode",
      ]) &&
        includesAll(files.migration.text, [
        "P12-27 locks",
        "barcode provider v1 schema",
          "P12-28 exposes the direct `require(\"barcode\")` provider",
          "P12-29",
        ]) &&
        includesAll(files.securityModel.text, [
          "P12-28 exposes the direct `barcode` bridge provider",
          "compat.barcode",
          "compat.qrcode",
          "raw ML Kit `Barcode`",
        ]),
      "Loader, migration, and security docs must record the P12-27 schema lock, P12-28 direct provider, and P12-29 compat handoff.",
    ),
    check(
      "status-manifest-gap-preserved",
      barcodeCompatPending || barcodeCompatPromoted,
      "Status manifest must either keep barcode/qrcode as bridge gaps before P12-29 or promote them through the P12-29 compat facade.",
    ),
    check(
      "compat-still-closed",
      (barcodeCompatPromoted || !includesAny(files.runtime.text, [
          "__autojs6_rhino_compat_barcode",
          "__autojs6_rhino_compat_qrcode",
          "compat.barcode",
          "compat.qrcode",
        ])) &&
        (
          !files.runtime.text.includes("__autojs6_limited_barcode") ||
          includesAll(files.runtime.text, [
            "function __autojs6_limited_barcode()",
            "return __autojs6_limited_barcode()",
            "permissions: [\"barcode\", \"image\"]",
          ])
        ),
      "Barcode compat facades must remain closed before P12-29; after P12-29 they may exist only over the controlled barcode bridge.",
    ),
    check(
      "surface-manifest",
      barcodeSurface &&
        ["design_locked", "provider_poc", "promoted"].includes(barcodeSurface.status) &&
        (barcodeSurface.tasks || []).includes("P12-27") &&
        includesAll(JSON.stringify(barcodeSurface), [
          "P12-27 locks the barcode provider v1 JSON-safe schema",
          "BARCODE_PROVIDER_V1.md",
          "barcode_provider_v1.schema.json",
          "barcode-provider-v1-fixtures.json",
          "verify-rhino-compat-barcode-provider-contract.js",
          "verifyNodeRhinoCompatBarcodeProviderContract",
        ]) &&
        includesAll(files.surfaceDocs.text, [
          "barcode.qrcode",
          "P12-27 locks",
          "barcode provider v1",
        ]),
      "Phase 12 surface manifest must keep barcode.qrcode tied to P12-27 schema evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-27/.test(files.phase12Tasks.text) &&
        includesAll(files.phase12Tasks.text, [
          "BARCODE_PROVIDER_V1.md",
          "barcode_provider_v1.schema.json",
          "barcode-provider-v1-fixtures.json",
          "verify-rhino-compat-barcode-provider-contract.js",
          "verifyNodeRhinoCompatBarcodeProviderContract",
        ]),
      "Phase 12 task plan must mark P12-27 complete with contract, schema, fixtures, report, and gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatBarcodeProviderContract") &&
        files.gradle.text.includes("verify-rhino-compat-barcode-provider-contract.js") &&
        files.gradle.text.includes("BARCODE_PROVIDER_V1.md") &&
        files.gradle.text.includes("barcode_provider_v1.schema.json") &&
        files.gradle.text.includes("barcode-provider-v1-fixtures.json") &&
        files.gradle.text.includes("verifyNodeRhinoCompatRhinoShimPackagedMetadataGate"),
      "Gradle must register verifyNodeRhinoCompatBarcodeProviderContract after the P12-26 gate.",
    ),
    check(
      "syntax-verifier",
      verifierSyntax.ok,
      `P12-27 verifier must pass node --check${verifierSyntax.ok ? "" : ": " + verifierSyntax.message}`,
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
  return item ? item.passed : false;
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat Barcode Provider Contract Verification",
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
      console.log("Usage: node verify-rhino-compat-barcode-provider-contract.js [--repo-root dir] [--report-dir dir]");
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
