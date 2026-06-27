#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");
const vm = require("vm");

const REPORT_BASENAME = "rhino-compat-s13n-bytes-promotion-verification";
const STABLE_UNSUPPORTED = Object.freeze(["color", "point", "throwable", "time"]);

const FILES = {
  report: "docs/nodejs/reports/phase12/RHINO_S13N_BYTES_PROMOTION.md",
  triageReport: "docs/nodejs/reports/phase12/RHINO_UNSUPPORTED_UTILITY_TRIAGE.md",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  s13nTypes: "docs/nodejs/types/autojs6-node/s13n.d.ts",
  compatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  indexTypes: "docs/nodejs/types/autojs6-node/index.d.ts",
  typeReadme: "docs/nodejs/types/autojs6-node/README.md",
  typeSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  typeGate: "app/node-examples.gradle.kts",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  matrixGenerator: "tools/nodejs/phase11/generate-rhino-compat-matrix.js",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  surfaceDocs: "docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  triageVerifier: "tools/nodejs/phase12/verify-rhino-compat-unsupported-utility-triage.js",
  gradle: "app/node-phase12-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const pureBlock = extractPureUtilityBlock(files.runtime.text);
  const compatBlock = extractCompatBlock(files.runtime.text);
  const checks = [
    ...buildStaticChecks(files, repoRoot, pureBlock, compatBlock),
    ...buildDynamicChecks(pureBlock, compatBlock),
  ];
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const report = {
    schema: "autojs6-node-rhino-compat-s13n-bytes-promotion-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatS13nBytesPromotion",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      runtimeFacade: checkById(checks, "runtime-facade"),
      dynamicBytes: checkById(checks, "dynamic-s13n-bytes"),
      stableUnsupported: checkById(checks, "dynamic-stable-unsupported"),
      statusPromoted: checkById(checks, "status-manifest"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat s13n.bytes promotion verification: " + report.decision.status);
  console.log("Rhino compat s13n.bytes promotion policy violations: " + policyViolations.length);
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

function buildStaticChecks(files, repoRoot, pureBlock, compatBlock) {
  const statusManifest = JSON.parse(files.statusManifest.text);
  const statusModule = (statusManifest.modules || []).find((module) => module.id === "s13n");
  const surfaceManifest = JSON.parse(files.surfaceManifest.text);
  const triageSurface = (surfaceManifest.surfaces || []).find((surface) => surface.id === "unsupported.utility_triage");
  const totals = countManifestStatuses(statusManifest);
  const syntax = checkSyntax(path.join(repoRoot, "tools/nodejs/phase12/verify-rhino-compat-s13n-bytes-promotion.js"));
  const moduleEntry = (id) => statusModule && (statusModule.apiEntries || []).find((entry) => entry.id === id);

  return [
    check(
      "report",
      includesAll(files.report.text, [
        "Status: P12-32 `s13n.bytes` pure utility pilot landed",
        "require(\"s13n\").bytes(source)",
        "require(\"autojs6:compat\").s13n.bytes",
        "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED",
        "No whole-module `s13n` parity claim",
        ":app:verifyNodeRhinoCompatS13nBytesPromotion",
      ]),
      "P12-32 report must record runtime surface, stable unsupported siblings, non-goals, and gate evidence.",
    ),
    check(
      "runtime-facade",
      includesAll(files.runtime.text, [
        "let __autojs6_limited_s13n_cache = null",
        "function __autojs6_s13n_bytes()",
        "function __autojs6_limited_s13n()",
        "bytes: __autojs6_s13n_bytes",
        "s13n: __autojs6_limited_s13n()",
        "\"P12-32\"",
        "\"s13n\"",
        "name === \"s13n\"",
        "return __autojs6_limited_s13n();",
      ]) &&
        STABLE_UNSUPPORTED.every((name) => files.runtime.text.includes(`__autojs6_s13n_unsupported("${name}")`)),
      "Runtime must expose require(\"s13n\"), compat.s13n, bytes, stable unsupported siblings, and P12-32 status.",
    ),
    check(
      "runtime-blocks",
      pureBlock.includes("function __autojs6_limited_s13n()") &&
        pureBlock.includes("function __autojs6_s13n_bytes()") &&
        compatBlock.includes("s13n: __autojs6_limited_s13n()"),
      "Verifier must be able to extract the pure utility and compat runtime blocks containing s13n.",
    ),
    check(
      "types",
      includesAll(files.s13nTypes.text, [
        "declare module \"s13n\"",
        "bytes(source: number | string, fromUnit?: string): number",
        "color(value?: unknown): never",
        "point(value?: unknown, other?: unknown): never",
        "throwable(value?: unknown): never",
        "time(value?: unknown, fromUnit?: unknown, toUnit?: unknown): never",
      ]) &&
        includesAll(files.compatTypes.text, [
          "import s13n = require(\"s13n\")",
          "readonly s13n: s13n.S13nModule",
        ]) &&
        files.indexTypes.text.includes("./s13n.d.ts"),
      "Type declarations must expose direct s13n and compat.s13n surfaces.",
    ),
    check(
      "type-smoke",
      includesAll(files.typeSmoke.text, [
        "import s13n = require(\"s13n\")",
        "const standardizedBytes: number = s13n.bytes(\"1 KB\")",
        "const compatStandardizedBytes: number = rhinoCompat.s13n.bytes(\"2\", \"MB\")",
        "void standardizedBytes",
        "void compatStandardizedBytes",
      ]) &&
        includesAll(files.typeGate.text, [
          "\"s13n\"",
          "nodeTypeDeclarationModules",
        ]) &&
        includesAll(files.typeReadme.text, [
          "`s13n`",
          "P12-32 adds the low-risk pure JS `s13n.bytes` pilot",
        ]),
      "Type smoke, type gate, and type README must include s13n.",
    ),
    check(
      "status-manifest",
      statusModule &&
        statusModule.status === "partial" &&
        statusModule.asyncMode === "sync" &&
        includesAll(JSON.stringify(statusModule.nodeModules || []), ["s13n", "autojs6:compat"]) &&
        statusModule.decision === "p12_32_s13n_bytes_pure_utility_pilot" &&
        moduleEntry("s13n.__module") &&
        moduleEntry("s13n.__module").status === "partial" &&
        moduleEntry("s13n.bytes") &&
        moduleEntry("s13n.bytes").status === "supported" &&
        moduleEntry("s13n.bytes").asyncMode === "sync" &&
        STABLE_UNSUPPORTED.every((name) => {
          const entry = moduleEntry(`s13n.${name}`);
          return entry && entry.status === "unsupported" && entry.asyncMode === "sync" && entry.nodeModule === "s13n";
        }) &&
        JSON.stringify(statusManifest.totals) === JSON.stringify(totals),
      "Status manifest must promote only s13n.bytes, keep sibling helpers stable unsupported, and expose matching totals.",
    ),
    check(
      "matrix",
      includesAll(files.matrixGenerator.text, [
        "P12-32 promotes s13n.bytes",
        "Proceed to P12-33 dual-engine device evidence",
      ]) &&
        includesAll(findMatrixRow(files.matrix.text, "s13n"), [
          "| 38 | s13n | s13n | partial |",
          "partial:1, supported:1, unsupported:4",
          "Proceed to P12-33 dual-engine device evidence",
        ]),
      "Matrix generator and generated matrix must advance s13n to the P12-33 handoff after bytes promotion.",
    ),
    check(
      "migration-docs",
      includesAll(files.migration.text, [
        "P12-32 promotes the first low-risk pilot, `s13n.bytes`",
        "const s13n = require(\"s13n\")",
        "compat.s13n.bytes(\"1\", \"MB\")",
        "s13n.color`, `s13n.point`, `s13n.throwable`, and `s13n.time`",
      ]),
      "Migration guide must show direct and compat s13n.bytes usage and document unsupported siblings.",
    ),
    check(
      "surface-manifest",
      triageSurface &&
        triageSurface.status === "promoted" &&
        (triageSurface.runtimeSurface || []).includes("require(\"s13n\")") &&
        includesAll(JSON.stringify(triageSurface), [
          "RHINO_UNSUPPORTED_UTILITY_TRIAGE.md",
          "RHINO_S13N_BYTES_PROMOTION.md",
          "s13n.bytes(source, fromUnit?)",
          "verify-rhino-compat-s13n-bytes-promotion.js",
          "verifyNodeRhinoCompatS13nBytesPromotion",
        ]) &&
        includesAll(files.surfaceDocs.text, [
          "`unsupported.utility_triage`",
          "P12-32 promotes only `s13n.bytes`",
          "`autojs6:compat.s13n.bytes`",
          "stable unsupported",
        ]),
      "Phase 12 surface manifest must record P12-32 as promoted with runtime and gate evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-32/.test(files.phase12Tasks.text) &&
        includesAll(files.phase12Tasks.text, [
          "RHINO_S13N_BYTES_PROMOTION.md",
          "verify-rhino-compat-s13n-bytes-promotion.js",
          "verifyNodeRhinoCompatS13nBytesPromotion",
          "s13n.bytes",
          "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED",
        ]),
      "Phase 12 task plan must mark P12-32 complete with report, runtime, type, manifest, verifier, and gate evidence.",
    ),
    check(
      "p12-31-verifier-current",
      includesAll(files.triageVerifier.text, [
        "surfaceStateCurrent",
        "P12-32 s13n.bytes pilot",
        "Proceed to P12-33 dual-engine device evidence",
        "verifyNodeRhinoCompatS13nBytesPromotion",
      ]),
      "P12-31 verifier must remain compatible with the later P12-32 promotion state.",
    ),
    check(
      "gradle-gate",
      includesAll(files.gradle.text, [
        "verifyNodeRhinoCompatS13nBytesPromotion",
        "verify-rhino-compat-s13n-bytes-promotion.js",
        "verifyNodeRhinoCompatUnsupportedUtilityTriage",
        "RHINO_S13N_BYTES_PROMOTION.md",
        "rhino-compat-s13n-bytes-promotion-verification.json",
      ]),
      "Gradle must register verifyNodeRhinoCompatS13nBytesPromotion after the P12-31 utility triage gate.",
    ),
    check(
      "syntax-verifier",
      syntax.ok,
      `P12-32 verifier must pass node --check${syntax.ok ? "" : ": " + syntax.message}`,
    ),
  ];
}

function buildDynamicChecks(pureBlock, compatBlock) {
  if (!pureBlock || !compatBlock) {
    return [check("dynamic-blocks", false, "runtime pure utility or compat block could not be extracted")];
  }
  let harness;
  try {
    harness = createHarness(pureBlock, compatBlock);
  } catch (error) {
    return [check("dynamic-harness", false, "runtime harness failed: " + (error && error.stack ? error.stack : error))];
  }
  const checks = [];
  checks.push(check("dynamic-no-default-global", !("s13n" in harness.globalObject), "require autojs6:compat must not install s13n globally by default"));
  checks.push(check(
    "dynamic-status",
    harness.compat.status().implementedTasks.includes("P12-32") &&
      harness.compat.status().availableGlobalNames.includes("s13n") &&
      harness.compat.status().moduleNames.includes("s13n"),
    "compat status must expose P12-32 and the s13n global/module name.",
  ));
  checks.push(check(
    "dynamic-s13n-bytes",
    harness.s13n.bytes("1 KB") === 1024 &&
      harness.s13n.bytes(2, "MB") === 2097152 &&
      harness.compat.s13n.bytes("1.5 KB") === 1536,
    "s13n.bytes must normalize string and numeric byte inputs to B.",
  ));
  checks.push(check(
    "dynamic-invalid-arg",
    throwsCode(() => harness.s13n.bytes("1", {}), "ERR_INVALID_ARG_TYPE"),
    "s13n.bytes must reject non-string fromUnit values with ERR_INVALID_ARG_TYPE.",
  ));
  checks.push(check(
    "dynamic-stable-unsupported",
    STABLE_UNSUPPORTED.every((name) => throwsCode(() => harness.s13n[name]("value"), "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED")),
    "s13n sibling helpers must throw stable ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED errors.",
  ));
  const install = harness.compat.install({ globals: true });
  checks.push(check(
    "dynamic-explicit-global",
    install.globals.includes("s13n") &&
      harness.globalObject.s13n === harness.s13n &&
      harness.globalObject.s13n.bytes("3 KB") === 3072,
    "compat.install({ globals: true }) must explicitly install the s13n facade.",
  ));
  return checks;
}

function createHarness(pureBlock, compatBlock) {
  const source = `
    (function() {
      const nodeCrypto = require("crypto");
      const nodeUtil = require("util");
      let __autojs6_limited_base64_cache = null;
      let __autojs6_limited_colors_cache = null;
      let __autojs6_limited_formatter_cache = null;
      let __autojs6_limited_converter_cache = null;
      let __autojs6_limited_s13n_cache = null;
      let __autojs6_limited_mime_cache = null;
      let __autojs6_limited_nanoid_cache = null;
      let __autojs6_limited_rhino_compat_cache = null;
      let __autojs6_rhino_compat_facade_cache = null;
      const __textUtilityStubs = {
        opencc: Object.freeze(function opencc() { return ""; }),
        pinyin: Object.freeze(function pinyin() { return []; }),
        pinyin4j: Object.freeze(function pinyin4j() { return ""; })
      };
      const __storageSqliteStubs = {
        storage: Object.freeze({ create() { return {}; }, open() { return {}; } }),
        storages: Object.freeze({ create() { return {}; }, open() { return {}; } }),
        database: Object.freeze({ open() { return Promise.resolve({ close() { return Promise.resolve(true); } }); } }),
        sqlite: Object.freeze(function sqlite() { return Promise.resolve({ close() { return Promise.resolve(true); } }); })
      };
      const filesStub = Object.freeze({ path() { return null; } });
      const consoleStub = Object.freeze({ log() {}, info() {}, warn() {}, error() {}, verbose() {}, print() {}, show() { return this; } });
      const timersStub = Object.freeze({
        setTimeout() { return { type: "timeout" }; },
        clearTimeout() {},
        setInterval() { return { type: "interval" }; },
        clearInterval() {},
        setImmediate() { return { type: "immediate" }; },
        clearImmediate() {}
      });
      const appStub = Object.freeze({
        packageName: "org.autojs.autojs6",
        versionName: "6.7.1",
        versionCode: 3924,
        launchPackage() { return Promise.resolve(true); },
        launchApp() { return Promise.resolve(true); },
        openAppSetting() { return Promise.resolve(undefined); },
        startActivity() { return Promise.resolve(undefined); }
      });
      const deviceStub = Object.freeze({
        sdkInt: 36,
        width: 1080,
        height: 2400,
        density: 3,
        isScreenOn() { return Promise.resolve(true); },
        wakeUp() { return Promise.resolve(undefined); },
        vibrate() { return Promise.resolve(undefined); }
      });
      const shellStub = Object.freeze({
        exec(command) { return Promise.resolve(Object.freeze({ code: 0, stdout: String(command), stderr: "" })); },
        execFile(file) { return Promise.resolve(Object.freeze({ code: 0, stdout: String(file), stderr: "" })); },
        checkAccess(options) { return Object.freeze({ shell: true, root: !!(options && options.root === true), shizuku: false, missingCapabilities: Object.freeze([]) }); },
        setDefaultTimeout(ms) { return Number(ms); }
      });
      const imageStub = Object.freeze({
        captureScreen() { return Promise.resolve(Object.freeze({ id: "image", width: 1, height: 1 })); },
        readImage() { return Promise.resolve(Object.freeze({ id: "image", width: 1, height: 1 })); },
        saveImage() { return Promise.resolve(undefined); },
        clip() { return Promise.resolve(Object.freeze({ id: "image", width: 1, height: 1 })); },
        resize() { return Promise.resolve(Object.freeze({ id: "image", width: 1, height: 1 })); },
        grayscale() { return Promise.resolve(Object.freeze({ id: "image", width: 1, height: 1 })); },
        threshold() { return Promise.resolve(Object.freeze({ id: "image", width: 1, height: 1 })); },
        findImage() { return Promise.resolve(null); },
        matchTemplate() { return Promise.resolve([]); },
        findColor() { return Promise.resolve(null); },
        findMultiColors() { return Promise.resolve(null); },
        getSize() { return Object.freeze({ width: 1, height: 1 }); },
        recycle() { return Promise.resolve(undefined); }
      });
      const mediaProjectionStub = Object.freeze({
        requestScreenCapture() {
          return Promise.resolve(Object.freeze({
            capture() { return Promise.resolve(Object.freeze({ id: "image", width: 1, height: 1 })); },
            stop() { return Promise.resolve(true); }
          }));
        }
      });
      const ocrStub = Object.freeze({
        recognize() { return Promise.resolve([]); },
        recognizeText() { return Promise.resolve(""); },
        detectTextBounds() { return Promise.resolve([]); }
      });
      const barcodeStub = Object.freeze({
        detect() { return Promise.resolve(null); },
        detectAll() { return Promise.resolve([]); },
        recognizeText() { return Promise.resolve(""); },
        recognizeTexts() { return Promise.resolve([]); }
      });
      const uiStub = Object.freeze({
        showLayout() { return Promise.resolve(Object.freeze({ close() { return Promise.resolve(true); } })); }
      });
      const workManagerStub = Object.freeze({
        scheduleOnce() { return Promise.resolve(Object.freeze({ id: "task-1" })); },
        cancel() { return Promise.resolve(true); },
        list() { return Promise.resolve([]); }
      });
      function __autojs6_define_error_property(error, name, value) {
        Object.defineProperty(error, name, { value, configurable: true, enumerable: true, writable: true });
      }
      function __autojs6_error(message, code, autojs6Code) {
        const error = new Error(message);
        __autojs6_define_error_property(error, "code", code);
        if (autojs6Code || (typeof code === "string" && code.indexOf("ERR_AUTOJS6_") === 0)) {
          __autojs6_define_error_property(error, "autojs6Code", autojs6Code || code);
        }
        return error;
      }
      function __autojs6_invalid_arg_type(message) {
        return __autojs6_error(message, "ERR_INVALID_ARG_TYPE");
      }
      function __autojs6_buffer_constructor() {
        return Buffer;
      }
      function __autojs6_crypto_module() {
        return nodeCrypto;
      }
      function __autojs6_limited_util() {
        return nodeUtil;
      }
      function __autojs6_limited_files() {
        return filesStub;
      }
      function __autojs6_limited_opencc() {
        return __textUtilityStubs.opencc;
      }
      function __autojs6_limited_pinyin() {
        return __textUtilityStubs.pinyin;
      }
      function __autojs6_limited_pinyin4j() {
        return __textUtilityStubs.pinyin4j;
      }
      function __autojs6_limited_storage(moduleName) {
        return moduleName === "storages" ? __storageSqliteStubs.storages : __storageSqliteStubs.storage;
      }
      function __autojs6_limited_database(moduleName) {
        return moduleName === "sqlite" ? __storageSqliteStubs.sqlite : __storageSqliteStubs.database;
      }
      function __autojs6_limited_console() {
        return consoleStub;
      }
      function __autojs6_limited_timers() {
        return timersStub;
      }
      function __autojs6_limited_app() {
        return appStub;
      }
      function __autojs6_limited_device() {
        return deviceStub;
      }
      function __autojs6_limited_shell() {
        return shellStub;
      }
      function __autojs6_limited_image() {
        return imageStub;
      }
      function __autojs6_limited_media_projection() {
        return mediaProjectionStub;
      }
      function __autojs6_limited_ocr() {
        return ocrStub;
      }
      function __autojs6_limited_barcode() {
        return barcodeStub;
      }
      function __autojs6_limited_ui() {
        return uiStub;
      }
      function __autojs6_limited_work_manager() {
        return workManagerStub;
      }
      function __autojs6_limited_toast() {
        return { showToast() { return Promise.resolve(); } };
      }
      function __autojs6_limited_clipboard() {
        return { setText() { return Promise.resolve(); }, getText() { return Promise.resolve(""); } };
      }
      function __autojs6_define_global(name, value) {
        Object.defineProperty(globalThis, name, { value, configurable: true, enumerable: false, writable: false });
      }
      ${pureBlock}
      ${compatBlock}
      return {
        s13n: __autojs6_limited_s13n(),
        compat: __autojs6_limited_rhino_compat(),
        globalObject: globalThis
      };
    })()
  `;
  return vm.runInNewContext(source, {
    Array,
    ArrayBuffer,
    BigInt,
    Buffer,
    Error,
    Math,
    Number,
    Object,
    Promise,
    RegExp,
    String,
    Uint8Array,
    console: { log() {} },
    globalThis: {},
    require,
    setTimeout,
  });
}

function extractPureUtilityBlock(runtime) {
  const start = runtime.indexOf("function __autojs6_buffer_constructor_required");
  const end = runtime.indexOf("  const __autojs6_opencc_methods", start);
  if (start < 0 || end <= start) {
    return "";
  }
  return runtime.slice(start, end);
}

function extractCompatBlock(runtime) {
  const start = runtime.indexOf("const __autojs6_rhino_compat_error_codes");
  const end = runtime.indexOf("  function __autojs6_limited_work_manager()", start);
  if (start < 0 || end <= start) {
    return "";
  }
  return runtime.slice(start, end);
}

function countManifestStatuses(manifest) {
  const counts = {};
  (manifest.modules || []).forEach((module) => {
    counts[module.status] = (counts[module.status] || 0) + 1;
    (module.apiEntries || []).forEach((entry) => {
      counts[entry.status] = (counts[entry.status] || 0) + 1;
    });
  });
  return counts;
}

function throwsCode(action, expectedCode) {
  try {
    action();
    return false;
  } catch (error) {
    return error && (error.code === expectedCode || error.autojs6Code === expectedCode);
  }
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

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat s13n.bytes Promotion Verification",
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
      console.log("Usage: node verify-rhino-compat-s13n-bytes-promotion.js [--repo-root dir] [--report-dir dir]");
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
