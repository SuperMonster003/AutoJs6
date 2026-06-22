#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const vm = require("vm");

const REPORT_BASENAME = "rhino-compat-images-facade-verification";
const RHINO_GLOBALS = Object.freeze(["images"]);
const COVERED_APIS = Object.freeze([
  "images.read",
  "images.save",
  "images.clip",
  "images.resize",
  "images.grayscale",
  "images.threshold",
  "images.findImage",
  "images.matchTemplate",
  "images.findColor",
  "images.findMultiColors",
  "images.recycle",
]);

const FILES = {
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  imageTypes: "docs/nodejs/types/autojs6-node/image.d.ts",
  imagesTypes: "docs/nodejs/types/autojs6-node/images.d.ts",
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
    schema: "autojs6-node-rhino-compat-images-facade-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatImagesFacade",
    globals: RHINO_GLOBALS,
    coveredApis: COVERED_APIS,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      imagesFacadeExposed: checkById(checks, "dynamic-images-shape"),
      handleLifecycleCovered: checkById(checks, "dynamic-handle-lifecycle"),
      noDefaultGlobals: checkById(checks, "dynamic-no-default-images"),
      explicitGlobalInstall: checkById(checks, "dynamic-explicit-global-images"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat images facade verification: " + report.decision.status);
  console.log("Rhino compat images APIs: " + COVERED_APIS.join(", "));
  console.log("Rhino compat images policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildStaticChecks(files, compatBlock) {
  const manifest = JSON.parse(files.statusManifest.text);
  const moduleById = new Map((manifest.modules || []).map((module) => [module.id, module]));
  const globalModule = moduleById.get("global");
  const imagesModule = moduleById.get("images");
  const entryById = new Map((imagesModule ? imagesModule.apiEntries : []).map((entry) => [entry.id, entry]));
  const matrixImagesRow = files.matrix.text.split(/\r?\n/).find((line) => /\| \d+ \| images \| images \|/.test(line)) || "";
  const matrixImagesSurface = matrixImagesRow.split("|")[5] || "";
  return [
    check("runtime-compat-images-function", compatBlock.includes("function __autojs6_rhino_compat_images()") && compatBlock.includes("function __autojs6_rhino_compat_image_region") && compatBlock.includes("function __autojs6_rhino_compat_image_save_options"), "runtime must define the Rhino images compatibility wrapper and argument normalizers"),
    check("runtime-compat-images-global", compatBlock.includes('"images"') && compatBlock.includes("images: images") && compatBlock.includes('const imageModule = __autojs6_limited_image("images")'), "autojs6:compat must expose images and make it an installable global"),
    check("runtime-status-s11-18", /implementedTasks:\s*Object\.freeze\(\[[^\]]*"S11-18"/.test(compatBlock) && /pendingTasks:\s*Object\.freeze\(\[\]\)/.test(compatBlock), "compat status must mark S11-18 implemented with no pending low-risk tasks"),
    check("types-existing-images", files.imageTypes.text.includes("readImage(") && files.imageTypes.text.includes("findMultiColors(") && files.imagesTypes.text.includes('declare module "images"'), "base image/images declarations must expose the bridge APIs used by S11-18"),
    check("types-compat-images", files.compatTypes.text.includes('import image = require("image")') && files.compatTypes.text.includes("interface RhinoImagesCompatModule") && files.compatTypes.text.includes("readonly images: RhinoImagesCompatModule"), "rhino_compat.d.ts must declare the S11-18 images facade"),
    check("type-smoke-images", files.typeSmokeSource.text.includes("rhinoCompat.images.read") && files.typeSmokeSource.text.includes("rhinoCompat.images.threshold") && files.typeSmokeSource.text.includes("rhinoCompat.images.recycle"), "typescript-smoke sample must exercise S11-18 compat images APIs"),
    check("status-global-s11-18", globalModule && globalModule.notes.join("\n").includes("S11-18") && globalModule.bridgeModules.includes("images"), "global status manifest must mention S11-18 images global coverage"),
    check("status-images-s11-18", imagesModule && imagesModule.notes.join("\n").includes("S11-18") && imagesModule.nodeModules.includes("autojs6:compat"), "images status manifest must document S11-18 compat facade coverage"),
    check("status-images-apis", COVERED_APIS.every((id) => {
      const entry = entryById.get(id);
      return entry && entry.status === "supported_async" && entry.decision === "s11_18_images_compat_facade";
    }), "status manifest must mark every S11-18 images API as supported_async through the compat facade"),
    check("type-readme-s11-18", files.typeReadme.text.includes("S11-18") && files.typeReadme.text.includes("compat.images"), "type README must document S11-18 images declarations"),
    check("loader-design-s11-18", files.loaderDesign.text.includes("S11-18") && files.loaderDesign.text.includes("compat.images.read") && files.loaderDesign.text.includes("requestScreenCapture"), "loader design must document S11-18 images facade behavior and S11-19 screenshot boundary"),
    check("migration-s11-18", files.migration.text.includes("compat.images.findImage") && files.migration.text.includes("S11-19"), "migration guide must show S11-18 compat images usage and screenshot boundary"),
    check("security-images-boundary", files.securityModel.text.includes("S11-18") && files.securityModel.text.includes("Bitmap") && files.securityModel.text.includes("ImageWrapper") && files.securityModel.text.includes("recycle()"), "security model must keep image handle and raw object boundaries clear for S11-18"),
    check("matrix-images-s11-18", ["autojs6:compat", "image", "images", "media_projection"].every((name) => matrixImagesSurface.includes(name)) && /supported_async:\d+/.test(matrixImagesRow), "compat matrix must retain the images module surface after S11-18"),
    check("task-plan-s11-18-complete", /\[x\]\s+S11-18/.test(files.taskPlan.text) && files.taskPlan.text.includes("verifyNodeRhinoCompatImagesFacade"), "Phase 11 task plan must mark S11-18 complete and name the verification gate"),
    check("gradle-task", files.gradle.text.includes("verifyNodeRhinoCompatImagesFacade") && files.gradle.text.includes("verify-rhino-compat-images-facade.js"), "Gradle must register verifyNodeRhinoCompatImagesFacade"),
  ];
}

async function buildDynamicChecks(compatBlock) {
  if (!compatBlock) {
    return [check("dynamic-compat-block", false, "runtime compat block could not be extracted")];
  }
  const harness = createCompatHarness(compatBlock);
  const compat = harness.compat;
  const checks = [];
  checks.push(check("dynamic-no-default-images", !("images" in harness.globalObject), "require autojs6:compat must not install images globally by default"));
  checks.push(check("dynamic-status", compat.status().implementedTasks.includes("S11-18") && compat.status().availableGlobalNames.includes("images"), "compat status must expose S11-18 and images"));
  checks.push(check("dynamic-images-shape", compat.images === harness.images && typeof compat.images.read === "function" && typeof compat.images.save === "function", "compat module must expose the images facade"));

  const template = await compat.images.read("template.png", { timeoutMs: 101 });
  const clipped = await compat.images.clip(template, [1, 2, 20, 10], { timeoutMs: 102 });
  const resized = await compat.images.resize(clipped, { width: 7, height: 5 }, { timeoutMs: 103 });
  const gray = await compat.images.grayscale(resized, { timeoutMs: 104 });
  const binary = await compat.images.threshold(gray, 128, 255, "BINARY_INV");
  const point = await compat.images.findImage(binary, template, 0, 1, 7, 5, 0.8);
  const matches = await compat.images.matchTemplate(binary, template, { max: 2, timeoutMs: 105 });
  const colorPoint = await compat.images.findColor(binary, "#ff0000", 0, 0, 7, 5, 4);
  const multiPoint = await compat.images.findMultiColors(binary, 0xff0000, [[1, 0, "#00ff00"]], { threshold: 4, timeoutMs: 106 });
  const saved = await compat.images.save(binary, "out/result.png", "png", 90);
  const size = compat.images.getSize(binary);

  checks.push(check("dynamic-read-save-route", saved === true && harness.calls.some((call) => call.method === "readImage" && call.path === "template.png" && call.options.timeoutMs === 101) && harness.calls.some((call) => call.method === "saveImage" && call.path === "out/result.png" && call.options.format === "png" && call.options.quality === 90), "compat.images.read/save must route through readImage/saveImage with normalized options"));
  checks.push(check("dynamic-transform-route", size.width === 7 && size.height === 5 && harness.calls.some((call) => call.method === "clip" && call.region.width === 20 && call.options.timeoutMs === 102) && harness.calls.some((call) => call.method === "resize" && call.size.width === 7 && call.size.height === 5) && harness.calls.some((call) => call.method === "threshold" && call.options.type === "binary_inv"), "compat.images transform methods must normalize Rhino region, size, and threshold arguments"));
  checks.push(check("dynamic-search-route", point && point.x === 7 && matches.length === 1 && colorPoint && colorPoint.x === 3 && multiPoint && multiPoint.y === 6 && harness.calls.some((call) => call.method === "findImage" && call.options.region.join(",") === "0,1,7,5" && call.options.threshold === 0.8) && harness.calls.some((call) => call.method === "findColor" && call.options.threshold === 4), "compat.images search methods must normalize region overloads and return JSON snapshots"));

  await compat.images.recycle(template, { timeoutMs: 107 });
  await compat.images.recycle(clipped, { timeoutMs: 108 });
  await compat.images.recycle(resized, { timeoutMs: 109 });
  await compat.images.recycle(gray, { timeoutMs: 110 });
  await compat.images.recycle(binary, { timeoutMs: 111 });
  checks.push(check("dynamic-handle-lifecycle", harness.createdHandles.length === 5 && harness.recycledHandles.length === 5 && harness.activeHandleCount() === 0, "compat.images must leave every created image handle explicitly recyclable"));

  const invalidCode = await rejectedCode(() => compat.images.matchTemplate(binary, template, "bad"));
  checks.push(check("dynamic-invalid-options", invalidCode === "ERR_AUTOJS6_RHINO_COMPAT_INVALID_OPTION", "compat.images invalid option values must reject with the Rhino compat invalid option code"));

  const install = compat.install({ globals: true });
  checks.push(check("dynamic-explicit-global-images", install.globals.includes("images") && harness.globalObject.images === compat.images, "compat.install({ globals: true }) must explicitly install the images global"));
  return checks;
}

function createCompatHarness(compatBlock) {
  const source = `
    (function() {
      let __autojs6_limited_rhino_compat_cache = null;
      let __autojs6_rhino_compat_facade_cache = null;
      const globalObject = {};
      const calls = [];
      const createdHandles = [];
      const recycledHandles = [];
      const activeHandles = new Set();
      let nextImageId = 1;
      function makeHandle(label, width, height) {
        const id = label + "-" + nextImageId++;
        activeHandles.add(id);
        const handle = Object.freeze({ __autojs6ImageHandle: id, id, width, height });
        createdHandles.push(handle);
        return handle;
      }
      const imagesModule = Object.freeze({
        captureScreen(options) {
          calls.push({ module: "images", method: "captureScreen", options });
          return Promise.resolve(makeHandle("capture", 1080, 2400));
        },
        readImage(path, options) {
          calls.push({ module: "images", method: "readImage", path, options: options || {} });
          return Promise.resolve(makeHandle("read", 100, 60));
        },
        saveImage(image, path, options) {
          calls.push({ module: "images", method: "saveImage", image, path, options: options || {} });
          return Promise.resolve(undefined);
        },
        clip(image, x, y, width, height, options) {
          calls.push({ module: "images", method: "clip", image, region: { x, y, width, height }, options: options || {} });
          return Promise.resolve(makeHandle("clip", width, height));
        },
        resize(image, width, height, options) {
          calls.push({ module: "images", method: "resize", image, size: { width, height }, options: options || {} });
          return Promise.resolve(makeHandle("resize", width, height));
        },
        grayscale(image, options) {
          calls.push({ module: "images", method: "grayscale", image, options: options || {} });
          return Promise.resolve(makeHandle("gray", image.width, image.height));
        },
        threshold(image, options) {
          calls.push({ module: "images", method: "threshold", image, options: options || {} });
          return Promise.resolve(makeHandle("threshold", image.width, image.height));
        },
        findImage(image, template, options) {
          calls.push({ module: "images", method: "findImage", image, template, options: options || {} });
          return Promise.resolve(Object.freeze({ x: 7, y: 9 }));
        },
        matchTemplate(image, template, options) {
          calls.push({ module: "images", method: "matchTemplate", image, template, options: options || {} });
          return Promise.resolve(Object.freeze([Object.freeze({ x: 1, y: 2, score: 0.95 })]));
        },
        findColor(image, color, options) {
          calls.push({ module: "images", method: "findColor", image, color, options: options || {} });
          return Promise.resolve(Object.freeze({ x: 3, y: 4 }));
        },
        findMultiColors(image, color, points, options) {
          calls.push({ module: "images", method: "findMultiColors", image, color, points, options: options || {} });
          return Promise.resolve(Object.freeze({ x: 5, y: 6 }));
        },
        getSize(image) {
          calls.push({ module: "images", method: "getSize", image });
          return Object.freeze({ width: image.width, height: image.height });
        },
        recycle(image, options) {
          calls.push({ module: "images", method: "recycle", image, options: options || {} });
          if (image && image.id) {
            activeHandles.delete(image.id);
            recycledHandles.push(image.id);
          }
          return Promise.resolve(undefined);
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
      ${compatBlock}
      const compat = __autojs6_limited_rhino_compat();
      return {
        compat,
        images: compat.images,
        globalObject,
        calls,
        createdHandles,
        recycledHandles,
        activeHandleCount() { return activeHandles.size; }
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
    "# Rhino Compat Images Facade Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Globals: ${report.globals.map((name) => `\`${name}\``).join(", ")}`,
    `- Covered APIs: ${report.coveredApis.map((name) => `\`${name}\``).join(", ")}`,
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
      console.log("Usage: node verify-rhino-compat-images-facade.js [--repo-root dir] [--report-dir dir]");
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
