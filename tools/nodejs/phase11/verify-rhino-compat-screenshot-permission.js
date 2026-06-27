#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const vm = require("vm");

const REPORT_BASENAME = "rhino-compat-screenshot-permission-verification";
const RHINO_GLOBALS = Object.freeze(["requestScreenCapture", "requestScreenCaptureAsync", "captureScreen"]);
const COVERED_APIS = Object.freeze([
  "images.captureScreen",
  "images.requestScreenCapture",
  "images.requestScreenCaptureAsync",
  "images.stopScreenCapture",
]);

const FILES = {
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  imageTypes: "docs/nodejs/types/autojs6-node/image.d.ts",
  mediaProjectionTypes: "docs/nodejs/types/autojs6-node/media_projection.d.ts",
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
    schema: "autojs6-node-rhino-compat-screenshot-permission-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatScreenshotPermission",
    globals: RHINO_GLOBALS,
    coveredApis: COVERED_APIS,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      requestWrapper: checkById(checks, "dynamic-request-screen-capture"),
      captureWrapper: checkById(checks, "dynamic-capture-screen"),
      denialStable: checkById(checks, "dynamic-denial-stable"),
      explicitGlobalInstall: checkById(checks, "dynamic-explicit-global-screenshot"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat screenshot permission verification: " + report.decision.status);
  console.log("Rhino compat screenshot globals: " + RHINO_GLOBALS.join(", "));
  console.log("Rhino compat screenshot policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildStaticChecks(files, compatBlock) {
  const manifest = JSON.parse(files.statusManifest.text);
  const moduleById = new Map((manifest.modules || []).map((module) => [module.id, module]));
  const globalModule = moduleById.get("global");
  const imagesModule = moduleById.get("images");
  const mediaModule = moduleById.get("media");
  const entryById = new Map((imagesModule ? imagesModule.apiEntries : []).map((entry) => [entry.id, entry]));
  const matrixImagesRow = files.matrix.text.split(/\r?\n/).find((line) => /\| \d+ \| images \| images \|/.test(line)) || "";
  const matrixImagesSurface = matrixImagesRow.split("|")[5] || "";
  return [
    check("runtime-screenshot-functions", compatBlock.includes("function __autojs6_rhino_compat_request_screen_capture") && compatBlock.includes("function __autojs6_rhino_compat_capture_screen") && compatBlock.includes("function __autojs6_rhino_compat_stop_screen_capture"), "runtime must define request/capture/stop screenshot compatibility wrappers"),
    check("runtime-screenshot-globals", RHINO_GLOBALS.every((name) => compatBlock.includes(`"${name}"`)) && compatBlock.includes("requestScreenCapture: __autojs6_rhino_compat_request_screen_capture") && compatBlock.includes("captureScreen: __autojs6_rhino_compat_capture_screen"), "autojs6:compat must expose screenshot wrappers and installable globals"),
    check("runtime-status-s11-19", /implementedTasks:\s*Object\.freeze\(\[[^\]]*"S11-19"/.test(compatBlock) && /pendingTasks:\s*Object\.freeze\(\[\]\)/.test(compatBlock), "compat status must mark S11-19 implemented with no pending low-risk tasks"),
    check("types-existing-screenshot", files.imageTypes.text.includes("captureScreen(") && files.mediaProjectionTypes.text.includes("requestScreenCapture("), "base image/media_projection declarations must expose the bridge APIs used by S11-19"),
    check("types-compat-screenshot", files.compatTypes.text.includes('import mediaProjection = require("media_projection")') && files.compatTypes.text.includes("type ScreenCaptureRequestOptions") && files.compatTypes.text.includes("requestScreenCapture(options?: ScreenCaptureRequestOptions)") && files.compatTypes.text.includes("captureScreen(path: string"), "rhino_compat.d.ts must declare S11-19 screenshot compatibility wrappers"),
    check("type-smoke-screenshot", files.typeSmokeSource.text.includes("rhinoCompat.requestScreenCapture") && files.typeSmokeSource.text.includes("rhinoCompat.images.stopScreenCapture"), "typescript-smoke sample must exercise S11-19 screenshot compat APIs"),
    check("status-global-s11-19", globalModule && globalModule.notes.join("\n").includes("S11-19") && globalModule.bridgeModules.includes("media_projection"), "global status manifest must mention S11-19 and media_projection bridge coverage"),
    check("status-images-s11-19", imagesModule && imagesModule.notes.join("\n").includes("S11-19") && imagesModule.nodeModules.includes("media_projection") && imagesModule.bridgeModules.includes("media_projection"), "images status manifest must document S11-19 screenshot facade coverage"),
    check("status-screenshot-apis", COVERED_APIS.every((id) => {
      const entry = entryById.get(id);
      return entry && entry.status === "supported_async" && entry.decision === "s11_19_screenshot_compat_facade";
    }), "status manifest must mark S11-19 screenshot APIs as supported_async through the compat facade"),
    check("status-media-note", mediaModule && mediaModule.notes.join("\n").includes("S11-19") && mediaModule.nodeModules.includes("autojs6:compat"), "media status manifest must record the S11-19 media_projection provider wrapper role"),
    check("type-readme-s11-19", files.typeReadme.text.includes("S11-19") && files.typeReadme.text.includes("requestScreenCapture"), "type README must document S11-19 screenshot declarations"),
    check("loader-design-s11-19", files.loaderDesign.text.includes("S11-19") && files.loaderDesign.text.includes("compat.requestScreenCapture") && files.loaderDesign.text.includes("packaged fallback"), "loader design must document S11-19 screenshot permission behavior"),
    check("migration-s11-19", files.migration.text.includes("Node with the S11-19 compatibility facade") && files.migration.text.includes("compat.requestScreenCapture"), "migration guide must show S11-19 compat screenshot usage"),
    check("security-screenshot-boundary", files.securityModel.text.includes("S11-19") && files.securityModel.text.includes("raw `MediaProjection`") && files.securityModel.text.includes("packaged fallback"), "security model must keep screenshot permission boundaries clear for S11-19"),
    check("matrix-screenshot", ["autojs6:compat", "image", "images", "media_projection"].every((name) => matrixImagesSurface.includes(name)) && /supported_async:\d+/.test(matrixImagesRow), "compat matrix must retain compat/images/media_projection screenshot surfaces"),
    check("task-plan-s11-19-complete", /\[x\]\s+S11-19/.test(files.taskPlan.text) && files.taskPlan.text.includes("verifyNodeRhinoCompatScreenshotPermission"), "Phase 11 task plan must mark S11-19 complete and name the verification gate"),
    check("gradle-task", files.gradle.text.includes("verifyNodeRhinoCompatScreenshotPermission") && files.gradle.text.includes("verify-rhino-compat-screenshot-permission.js"), "Gradle must register verifyNodeRhinoCompatScreenshotPermission"),
  ];
}

async function buildDynamicChecks(compatBlock) {
  if (!compatBlock) {
    return [check("dynamic-compat-block", false, "runtime compat block could not be extracted")];
  }
  const harness = createCompatHarness(compatBlock);
  const compat = harness.compat;
  const checks = [];
  checks.push(check("dynamic-no-default-screenshot", RHINO_GLOBALS.every((name) => !(name in harness.globalObject)), "require autojs6:compat must not install screenshot globals by default"));
  checks.push(check("dynamic-status", compat.status().implementedTasks.includes("S11-19") && RHINO_GLOBALS.every((name) => compat.status().availableGlobalNames.includes(name)), "compat status must expose S11-19 screenshot globals"));

  const requested = await compat.requestScreenCapture(true);
  const requestedAgain = await compat.images.requestScreenCapture({ orientation: "portrait", timeoutMs: 101 });
  checks.push(check("dynamic-request-screen-capture", requested === true && requestedAgain === true && harness.calls.some((call) => call.module === "media_projection" && call.method === "requestScreenCapture" && call.options.orientation === "landscape") && harness.calls.some((call) => call.module === "media_projection" && call.method === "stop"), "requestScreenCapture must normalize orientation, return true, and stop a previous cached capturer before replacing it"));

  const screen = await compat.captureScreen({ timeoutMs: 102 });
  const saved = await compat.images.captureScreen("out/screen.png", { format: "png", timeoutMs: 103 });
  const stopped = await compat.images.stopScreenCapture({ timeoutMs: 104 });
  checks.push(check("dynamic-capture-screen", screen && screen.id && saved === true && stopped === true && harness.calls.some((call) => call.module === "images" && call.method === "captureScreen" && call.options.requireExistingPermission === true && call.options.timeoutMs === 102) && harness.calls.some((call) => call.module === "images" && call.method === "saveImage" && call.path === "out/screen.png") && harness.savedCaptureRecycled(), "captureScreen must use existing-permission capture, save path captures must recycle temporary handles, and stopScreenCapture must close the cached capturer"));

  await compat.images.recycle(screen, { timeoutMs: 105 });
  checks.push(check("dynamic-handle-lifecycle", harness.activeImageHandleCount() === 0, "S11-19 captured image handles must remain explicitly recyclable"));

  const denialCode = await rejectedCode(() => compat.requestScreenCapture({ deny: true }));
  const invalidCode = await rejectedCode(() => compat.requestScreenCapture(123));
  checks.push(check("dynamic-denial-stable", denialCode === "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED", "requestScreenCapture provider denial must preserve the stable bridge error code"));
  checks.push(check("dynamic-invalid-options", invalidCode === "ERR_AUTOJS6_RHINO_COMPAT_INVALID_OPTION", "requestScreenCapture invalid options must reject with the Rhino compat invalid option code"));

  const install = compat.install({ globals: true });
  checks.push(check("dynamic-explicit-global-screenshot", RHINO_GLOBALS.every((name) => install.globals.includes(name) && harness.globalObject[name] === compat[name]), "compat.install({ globals: true }) must explicitly install screenshot globals"));
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
      let nextCapturerId = 1;
      function makeBridgeError(message, code, moduleName, methodName) {
        const error = __autojs6_error(message, code);
        __autojs6_define_error_property(error, "module", moduleName);
        __autojs6_define_error_property(error, "method", methodName);
        return error;
      }
      function makeImageHandle(label, width, height) {
        const id = label + "-" + nextImageId++;
        activeImageHandles.add(id);
        return Object.freeze({ __autojs6ImageHandle: id, id, width, height });
      }
      function makeCapturer(options) {
        const id = "capturer-" + nextCapturerId++;
        return Object.freeze({
          __autojs6ScreenCapturerHandle: id,
          id,
          width: 1080,
          height: 2400,
          nextImage(nextOptions) {
            calls.push({ module: "media_projection", method: "nextImage", id, options: nextOptions || {} });
            return Promise.resolve(makeImageHandle("frame", 1080, 2400));
          },
          stop(stopOptions) {
            calls.push({ module: "media_projection", method: "stop", id, options: stopOptions || {} });
            return Promise.resolve(undefined);
          },
          options: options || {}
        });
      }
      const mediaProjection = Object.freeze({
        requestScreenCapture(options) {
          calls.push({ module: "media_projection", method: "requestScreenCapture", options: options || {} });
          if (options && options.deny === true) {
            return Promise.reject(makeBridgeError("screen capture denied", "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED", "media_projection", "requestScreenCapture"));
          }
          return Promise.resolve(makeCapturer(options || {}));
        }
      });
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
      function __autojs6_limited_media_projection() { return mediaProjection; }
      function __autojs6_limited_ocr() { return Object.freeze({ recognize() { return Promise.resolve([]); }, recognizeText() { return Promise.resolve(""); }, detectTextBounds() { return Promise.resolve([]); } }); }
      ${compatBlock}
      const compat = __autojs6_limited_rhino_compat();
      return {
        compat,
        globalObject,
        calls,
        activeImageHandleCount() { return activeImageHandles.size; },
        savedCaptureRecycled() {
          const saveCall = calls.find((call) => call.module === "images" && call.method === "saveImage" && call.path === "out/screen.png");
          return !!(saveCall && saveCall.image && recycledImageHandles.includes(saveCall.image.id));
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
    "# Rhino Compat Screenshot Permission Verification",
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
      console.log("Usage: node verify-rhino-compat-screenshot-permission.js [--repo-root dir] [--report-dir dir]");
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
