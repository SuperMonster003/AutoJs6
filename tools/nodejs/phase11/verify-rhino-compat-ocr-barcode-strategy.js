#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const vm = require("vm");

const REPORT_BASENAME = "rhino-compat-ocr-barcode-strategy-verification";
const RHINO_GLOBALS = Object.freeze(["ocr"]);
const COVERED_OCR_APIS = Object.freeze(["ocr.__module", "ocr.detect", "ocr.recognizeText"]);
const BARCODE_MODULES = Object.freeze(["barcode", "barcode.qr_code"]);

const FILES = {
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  ocrTypes: "docs/nodejs/types/autojs6-node/ocr.d.ts",
  compatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  typeReadme: "docs/nodejs/types/autojs6-node/README.md",
  typeSmokeSource: "sample/nodejs/typescript-smoke/src/main.ts",
  loaderDesign: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  taskPlan: "docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  gradle: "app/node-phase11-gate.gradle.kts",
};

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => [key, {
      relative,
      text: read(path.join(repoRoot, relative)),
    }]),
  );
  const compatBlock = extractCompatBlock(files.runtime.text);
  const checks = [
    ...buildStaticChecks(files, compatBlock),
    ...(await buildDynamicChecks(compatBlock)),
  ];
  const policyViolations = checks.filter((check) => !check.passed).map((check) => check.message);
  const report = {
    schema: "autojs6-node-rhino-compat-ocr-barcode-strategy-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatOcrBarcodeStrategy",
    globals: RHINO_GLOBALS,
    coveredOcrApis: COVERED_OCR_APIS,
    barcodeModules: BARCODE_MODULES,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      ocrFacade: checkById(checks, "dynamic-ocr-handle-input"),
      pathInput: checkById(checks, "dynamic-ocr-path-input"),
      captureInput: checkById(checks, "dynamic-ocr-capture-input"),
      barcodeGap: checkById(checks, "dynamic-barcode-gap"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat OCR/barcode strategy verification: " + report.decision.status);
  console.log("Rhino compat OCR globals: " + RHINO_GLOBALS.join(", "));
  console.log("Rhino compat barcode strategy modules: " + BARCODE_MODULES.join(", "));
  console.log("Rhino compat OCR/barcode policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildStaticChecks(files, compatBlock) {
  const manifest = JSON.parse(files.statusManifest.text);
  const moduleById = new Map((manifest.modules || []).map((module) => [module.id, module]));
  const globalModule = moduleById.get("global");
  const ocrModule = moduleById.get("ocr");
  const ocrEntries = new Map((ocrModule ? ocrModule.apiEntries : []).map((entry) => [entry.id, entry]));
  const barcodeModules = BARCODE_MODULES.map((id) => moduleById.get(id));
  const matrixOcrRow = files.matrix.text.split(/\r?\n/).find((line) => /\| \d+ \| ocr \| ocr \|/.test(line)) || "";
  const matrixOcrSurface = matrixOcrRow.split("|")[5] || "";
  return [
    check("runtime-ocr-functions", compatBlock.includes("function __autojs6_rhino_compat_ocr()") && compatBlock.includes("function __autojs6_rhino_compat_ocr_with_image") && compatBlock.includes("function __autojs6_rhino_compat_ocr_options"), "runtime must define the Rhino OCR compatibility facade and argument normalizers"),
    check("runtime-ocr-global", compatBlock.includes('"ocr"') && compatBlock.includes("ocr: ocr") && compatBlock.includes("const ocr = __autojs6_rhino_compat_ocr()"), "autojs6:compat must expose ocr and make it an installable global"),
    check("runtime-status-s11-20", /implementedTasks:\s*Object\.freeze\(\[[^\]]*"S11-20"/.test(compatBlock) && /pendingTasks:\s*Object\.freeze\(\[\]\)/.test(compatBlock), "compat status must mark S11-20 implemented with no pending low-risk tasks"),
    check("types-existing-ocr", files.ocrTypes.text.includes("recognize(") && files.ocrTypes.text.includes("detectTextBounds("), "base OCR declarations must expose the bridge APIs used by S11-20"),
    check("types-compat-ocr", files.compatTypes.text.includes('import ocr = require("ocr")') && files.compatTypes.text.includes("interface RhinoOcrCompatFunction") && files.compatTypes.text.includes("readonly ocr: RhinoOcrCompatFunction"), "rhino_compat.d.ts must declare the S11-20 OCR compatibility facade"),
    check("type-smoke-ocr", files.typeSmokeSource.text.includes("rhinoCompat.ocr.recognizeText") && files.typeSmokeSource.text.includes("rhinoCompat.ocr.detectTextBounds") && files.typeSmokeSource.text.includes("rhinoCompat.ocr.tap"), "typescript-smoke sample must exercise S11-20 compat OCR APIs"),
    check("status-global-s11-20", globalModule && globalModule.notes.join("\n").includes("S11-20") && globalModule.bridgeModules.includes("ocr"), "global status manifest must mention S11-20 OCR global coverage"),
    check("status-ocr-s11-20", ocrModule && ocrModule.notes.join("\n").includes("S11-20") && ocrModule.nodeModules.includes("autojs6:compat") && ocrModule.bridgeModules.includes("ocr") && ocrModule.bridgeModules.includes("images"), "OCR status manifest must document S11-20 compat facade coverage"),
    check("status-ocr-apis", COVERED_OCR_APIS.every((id) => {
      const entry = ocrEntries.get(id);
      return entry && entry.status === "supported_async" && entry.decision === "s11_20_ocr_compat_facade";
    }), "status manifest must mark covered S11-20 OCR APIs as supported_async through the compat facade"),
    check("status-barcode-gap", barcodeModules.every((module) => module && module.decision === "s11_20_barcode_bridge_gap_pending" && module.notes.join("\n").includes("bridge gap") && (module.apiEntries || []).every((entry) => entry.status !== "supported_async")), "barcode/qrcode modules must remain bridge-gap strategy items, not supported facades"),
    check("type-readme-s11-20", files.typeReadme.text.includes("S11-20") && files.typeReadme.text.includes("compat.ocr") && files.typeReadme.text.includes("barcode/qrcode"), "type README must document S11-20 OCR declarations and barcode gap"),
    check("loader-design-s11-20", files.loaderDesign.text.includes("S11-20") && files.loaderDesign.text.includes("compat.ocr.recognizeText") && files.loaderDesign.text.includes("Barcode and qrcode"), "loader design must document S11-20 OCR facade behavior and barcode boundary"),
    check("migration-s11-20", files.migration.text.includes("Node with the S11-20 compatibility facade") && files.migration.text.includes("compat.ocr.recognizeText") && files.migration.text.includes("barcode") && files.migration.text.includes("qrcode"), "migration guide must show S11-20 compat OCR usage and barcode gap"),
    check("security-ocr-boundary", files.securityModel.text.includes("S11-20") && files.securityModel.text.includes("raw OCR engines") && files.securityModel.text.includes("barcode/qrcode"), "security model must keep OCR and barcode boundaries clear for S11-20"),
    check("matrix-ocr-s11-20", ["autojs6:compat", "images", "ocr"].every((name) => matrixOcrSurface.includes(name)) && /supported_async:\d+/.test(matrixOcrRow), "compat matrix must retain OCR compat/images/ocr surfaces after S11-20"),
    check("task-plan-s11-20-complete", /\[x\]\s+S11-20/.test(files.taskPlan.text) && files.taskPlan.text.includes("verifyNodeRhinoCompatOcrBarcodeStrategy"), "Phase 11 task plan must mark S11-20 complete and name the verification gate"),
    check("gradle-task", files.gradle.text.includes("verifyNodeRhinoCompatOcrBarcodeStrategy") && files.gradle.text.includes("verify-rhino-compat-ocr-barcode-strategy.js"), "Gradle must register verifyNodeRhinoCompatOcrBarcodeStrategy"),
  ];
}

async function buildDynamicChecks(compatBlock) {
  if (!compatBlock) {
    return [check("dynamic-compat-block", false, "runtime compat block could not be extracted")];
  }
  const harness = createCompatHarness(compatBlock);
  const compat = harness.compat;
  const checks = [];
  checks.push(check("dynamic-no-default-ocr", RHINO_GLOBALS.every((name) => !(name in harness.globalObject)), "require autojs6:compat must not install OCR globals by default"));
  const status = compat.status();
  checks.push(check("dynamic-status", status.implementedTasks.includes("S11-20") && status.availableGlobalNames.includes("ocr"), "compat status must expose S11-20 and OCR"));
  checks.push(check("dynamic-ocr-shape", typeof compat.ocr === "function" && typeof compat.ocr.recognizeText === "function" && typeof compat.ocr.detectTextBounds === "function" && compat.ocr.mode === "mlkit", "compat module must expose callable ML Kit-only OCR facade"));

  const handle = harness.makeImageHandle("manual", 320, 240);
  const texts = await compat.ocr.recognizeText(handle, { region: [1, 2, 30, 40], timeoutMs: 1000 });
  const handleCall = harness.calls.find((call) => call.module === "ocr" && call.method === "recognize" && call.image && call.image.id === handle.id);
  checks.push(check("dynamic-ocr-handle-input", Array.isArray(texts) && texts.join("|") === "hello|world" && handleCall && handleCall.options.region.x === 1 && handleCall.options.region.width === 30, "compat.ocr.recognizeText must accept image handles, normalize regions, and return Rhino-style string arrays"));

  const pathTexts = await compat.ocr("fixtures/ocr.png", { region: { left: 3, top: 4, right: 13, bottom: 24 }, timeoutMs: 1000 });
  const readCall = harness.calls.find((call) => call.module === "images" && call.method === "readImage" && call.path === "fixtures/ocr.png");
  const pathOcrCall = harness.calls.find((call) => call.module === "ocr" && call.method === "recognize" && call.image && call.image.id.startsWith("read-"));
  checks.push(check("dynamic-ocr-path-input", Array.isArray(pathTexts) && pathTexts.length === 2 && readCall && pathOcrCall && pathOcrCall.options.region.width === 10 && harness.pathReadRecycled(), "compat.ocr callable must read path inputs, normalize object regions, and recycle temporary image handles"));

  const bounds = await compat.ocr.detectTextBounds(handle, { region: { left: 0, top: 0, right: 10, bottom: 20 }, timeoutMs: 1000 });
  checks.push(check("dynamic-ocr-detect-json", Array.isArray(bounds) && bounds.length === 1 && bounds[0].bounds.left === 10 && JSON.parse(JSON.stringify(bounds))[0].text === "boxed", "compat.ocr.detectTextBounds must return JSON-safe OCR result snapshots"));

  const capturedTexts = await compat.ocr.recognizeText({ timeoutMs: 1000 });
  const captureCall = harness.calls.find((call) => call.module === "images" && call.method === "captureScreen");
  checks.push(check("dynamic-ocr-capture-input", capturedTexts.length === 2 && captureCall && captureCall.options.requireExistingPermission === true && harness.capturedImageRecycled(), "compat.ocr must capture with existing permission and recycle temporary screen handles when no image input is passed"));

  const modeOk = compat.ocr.tap("mlkit");
  const blockedCode = await rejectedCode(() => compat.ocr.tap("rapid"));
  checks.push(check("dynamic-ocr-mode-boundary", modeOk === "mlkit" && blockedCode === "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN" && compat.ocr.summary().includes("Available modes: [ mlkit ]"), "compat.ocr must accept only ML Kit mode and block Rhino Paddle/Rapid modes"));

  checks.push(check("dynamic-barcode-gap", !("barcode" in compat) && !("qrcode" in compat), "S11-20 must not expose barcode/qrcode as supported compat facades before a controlled barcode bridge exists"));
  const install = compat.install({ globals: true });
  checks.push(check("dynamic-explicit-global-ocr", install.globals.includes("ocr") && harness.globalObject.ocr === compat.ocr && !install.globals.includes("barcode") && !install.globals.includes("qrcode"), "compat.install({ globals: true }) must explicitly install OCR but not barcode/qrcode"));
  return checks;
}

function createCompatHarness(compatBlock) {
  const source = `
    (function() {
      let __autojs6_limited_rhino_compat_cache = null;
      let __autojs6_rhino_compat_facade_cache = null;
      const globalObject = {};
      const calls = [];
      const activeImageHandles = new Set();
      const recycledImageHandles = [];
      let nextImageId = 1;
      function makeImageHandle(label, width, height) {
        const id = label + "-" + nextImageId++;
        activeImageHandles.add(id);
        return Object.freeze({ __autojs6ImageHandle: id, id, width, height });
      }
      const imagesModule = Object.freeze({
        captureScreen(options) {
          calls.push({ module: "images", method: "captureScreen", options: options || {} });
          return Promise.resolve(makeImageHandle("capture", 1080, 2400));
        },
        readImage(path, options) {
          calls.push({ module: "images", method: "readImage", path, options: options || {} });
          return Promise.resolve(makeImageHandle("read", 100, 60));
        },
        saveImage(image, path, options) {
          calls.push({ module: "images", method: "saveImage", image, path, options: options || {} });
          return Promise.resolve(undefined);
        },
        clip() { return Promise.resolve(makeImageHandle("clip", 1, 1)); },
        resize() { return Promise.resolve(makeImageHandle("resize", 1, 1)); },
        grayscale() { return Promise.resolve(makeImageHandle("gray", 1, 1)); },
        threshold() { return Promise.resolve(makeImageHandle("threshold", 1, 1)); },
        findImage() { return Promise.resolve(null); },
        matchTemplate() { return Promise.resolve([]); },
        findColor() { return Promise.resolve(null); },
        findMultiColors() { return Promise.resolve(null); },
        getSize(image) { return Object.freeze({ width: image.width, height: image.height }); },
        recycle(image, options) {
          calls.push({ module: "images", method: "recycle", image, options: options || {} });
          if (image && image.id) {
            activeImageHandles.delete(image.id);
            recycledImageHandles.push(image.id);
          }
          return Promise.resolve(undefined);
        }
      });
      const ocrModule = Object.freeze({
        recognize(image, options) {
          calls.push({ module: "ocr", method: "recognize", image, options: options || {} });
          return Promise.resolve(Object.freeze([
            Object.freeze({ text: "hello", confidence: 0.9, bounds: Object.freeze({ left: 1, top: 2, right: 3, bottom: 4 }) }),
            Object.freeze({ text: "world", confidence: 0.8 })
          ]));
        },
        recognizeText(image, options) {
          calls.push({ module: "ocr", method: "recognizeText", image, options: options || {} });
          return Promise.resolve("hello\\nworld");
        },
        detectTextBounds(image, options) {
          calls.push({ module: "ocr", method: "detectTextBounds", image, options: options || {} });
          return Promise.resolve(Object.freeze([
            Object.freeze({ text: "boxed", confidence: 0.7, bounds: Object.freeze({ left: 10, top: 20, right: 30, bottom: 40 }) })
          ]));
        }
      });
      const app = Object.freeze({
        packageName: "org.autojs.autojs6",
        versionName: "6.7.1",
        versionCode: 3924,
        launchPackage() { return Promise.resolve(true); },
        launchApp() { return Promise.resolve(true); },
        openAppSetting() { return Promise.resolve(undefined); },
        startActivity() { return Promise.resolve(undefined); }
      });
      const device = Object.freeze({
        sdkInt: 36,
        width: 1080,
        height: 2400,
        density: 3,
        isScreenOn() { return Promise.resolve(true); },
        wakeUp() { return Promise.resolve(undefined); },
        vibrate() { return Promise.resolve(undefined); }
      });
      const shellModule = Object.freeze({
        exec(command) { return Promise.resolve(Object.freeze({ code: 0, stdout: String(command), stderr: "" })); },
        execFile(path) { return Promise.resolve(Object.freeze({ code: 0, stdout: String(path), stderr: "" })); },
        checkAccess(options) { return Object.freeze({ shell: true, root: !!(options && options.root === true), shizuku: false, missingCapabilities: Object.freeze([]) }); },
        setDefaultTimeout(ms) { return Number(ms); }
      });
      const mediaProjection = Object.freeze({
        requestScreenCapture() { return Promise.resolve(Object.freeze({ stop() { return Promise.resolve(undefined); } })); }
      });
      const moduleStubs = {
        files: Object.freeze({ path() { return null; } }),
        base64: Object.freeze({ encode() { return ""; }, decode() { return ""; } }),
        colors: Object.freeze({ rgb() { return 0; } }),
        formatter: Object.freeze({ bytes() { return ""; } }),
        converter: Object.freeze({ bytes() { return 0; } }),
        mime: Object.freeze(function mime() { return Object.freeze({ type: "", subtype: "", parameters: Object.freeze({}) }); }),
        nanoid: Object.freeze(function nanoid() { return ""; }),
        util: Object.freeze({ format(value) { return String(value); }, inspect(value) { return String(value); } }),
        opencc: Object.freeze(function opencc() { return ""; }),
        pinyin: Object.freeze(function pinyin() { return []; }),
        pinyin4j: Object.freeze(function pinyin4j() { return ""; }),
        storage: Object.freeze({ create() { return {}; }, open() { return {}; } }),
        storages: Object.freeze({ create() { return {}; }, open() { return {}; } }),
        database: Object.freeze({ open() { return Promise.resolve({ close() { return Promise.resolve(true); } }); } }),
        sqlite: Object.freeze(function sqlite() { return Promise.resolve({ close() { return Promise.resolve(true); } }); }),
        console: Object.freeze({ log() {}, info() {}, warn() {}, error() {}, verbose() {}, print() {}, show() { return this; } }),
        timers: Object.freeze({
          setTimeout() { return { type: "timeout" }; },
          clearTimeout() {},
          setInterval() { return { type: "interval" }; },
          clearInterval() {},
          setImmediate() { return { type: "immediate" }; },
          clearImmediate() {}
        })
      };
      function __autojs6_define_error_property(error, name, value) {
        Object.defineProperty(error, name, { value, configurable: true, enumerable: true, writable: true });
      }
      function __autojs6_error(message, code) {
        const error = new Error(message);
        __autojs6_define_error_property(error, "code", code);
        __autojs6_define_error_property(error, "autojs6Code", code);
        return error;
      }
      function __autojs6_define_global(name, value) {
        globalObject[name] = value;
      }
      function __autojs6_normalize_image_handle(value) {
        if (!value || typeof value !== "object") {
          return null;
        }
        const id = value.__autojs6ImageHandle || value.id;
        if (!id) {
          return null;
        }
        return Object.freeze({ __autojs6ImageHandle: id, id, width: value.width, height: value.height });
      }
      const globalThis = globalObject;
      function __autojs6_limited_files() { return moduleStubs.files; }
      function __autojs6_limited_base64() { return moduleStubs.base64; }
      function __autojs6_limited_colors() { return moduleStubs.colors; }
      function __autojs6_limited_formatter() { return moduleStubs.formatter; }
      function __autojs6_limited_converter() { return moduleStubs.converter; }
      function __autojs6_limited_mime() { return moduleStubs.mime; }
      function __autojs6_limited_nanoid() { return moduleStubs.nanoid; }
      function __autojs6_limited_util() { return moduleStubs.util; }
      function __autojs6_limited_opencc() { return moduleStubs.opencc; }
      function __autojs6_limited_pinyin() { return moduleStubs.pinyin; }
      function __autojs6_limited_pinyin4j() { return moduleStubs.pinyin4j; }
      function __autojs6_limited_storage(moduleName) { return moduleName === "storages" ? moduleStubs.storages : moduleStubs.storage; }
      function __autojs6_limited_database(moduleName) { return moduleName === "sqlite" ? moduleStubs.sqlite : moduleStubs.database; }
      function __autojs6_limited_console() { return moduleStubs.console; }
      function __autojs6_limited_timers() { return moduleStubs.timers; }
      function __autojs6_limited_accessibility() { return Object.freeze({}); }
      function __autojs6_limited_app() { return app; }
      function __autojs6_limited_device() { return device; }
      function __autojs6_limited_shell() { return shellModule; }
      function __autojs6_limited_image() { return imagesModule; }
      function __autojs6_limited_media_projection() { return mediaProjection; }
      function __autojs6_limited_ocr() { return ocrModule; }
      function __autojs6_limited_ui() { return Object.freeze({}); }
      ${compatBlock}
      const compat = __autojs6_limited_rhino_compat();
      return {
        compat,
        globalObject,
        calls,
        makeImageHandle,
        activeImageHandleCount() { return activeImageHandles.size; },
        pathReadRecycled() {
          const readCall = calls.find((call) => call.module === "images" && call.method === "readImage" && call.path === "fixtures/ocr.png");
          return !!(readCall && recycledImageHandles.some((id) => id.startsWith("read-")));
        },
        capturedImageRecycled() {
          const captureCall = calls.find((call) => call.module === "images" && call.method === "captureScreen");
          return !!(captureCall && recycledImageHandles.some((id) => id.startsWith("capture-")));
        }
      };
    })()
  `;
  return vm.runInNewContext(source, {
    Array,
    Error,
    JSON,
    Math,
    Number,
    Object,
    Promise,
    RegExp,
    Set,
    String,
  });
}

async function rejectedCode(fn) {
  try {
    await fn();
    return "";
  } catch (error) {
    return String(error && (error.autojs6Code || error.code || ""));
  }
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((check) => check.id === id);
  return item ? item.passed : false;
}

function read(file) {
  return fs.readFileSync(file, "utf8");
}

function extractCompatBlock(runtime) {
  const start = runtime.indexOf("const __autojs6_rhino_compat_error_codes");
  const end = runtime.indexOf("  function __autojs6_limited_work_manager", start);
  if (start < 0 || end < 0 || end <= start) {
    return "";
  }
  return runtime.slice(start, end);
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat OCR/Barcode Strategy Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Globals: ${report.globals.map((name) => `\`${name}\``).join(", ")}`,
    `- Covered OCR APIs: ${report.coveredOcrApis.map((name) => `\`${name}\``).join(", ")}`,
    `- Barcode strategy modules: ${report.barcodeModules.map((name) => `\`${name}\``).join(", ")}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Checks",
    "",
    "| Check | Result | Message |",
    "| --- | --- | --- |",
  ];
  report.checks.forEach((check) => {
    lines.push(`| \`${check.id}\` | ${check.passed ? "passed" : "failed"} | ${escapeTable(check.message)} |`);
  });
  return lines.join("\n") + "\n";
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-rhino-compat-ocr-barcode-strategy.js [--repo-root dir] [--report-dir dir]");
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

main().catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exitCode = 1;
});
