#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const vm = require("vm");

const REPORT_BASENAME = "rhino-compat-app-device-shell-verification";
const RHINO_GLOBALS = Object.freeze(["app", "device", "shell"]);
const MODULES = Object.freeze(["app", "device", "shell"]);

const FILES = {
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  appTypes: "docs/nodejs/types/autojs6-node/app.d.ts",
  deviceTypes: "docs/nodejs/types/autojs6-node/device.d.ts",
  shellTypes: "docs/nodejs/types/autojs6-node/shell.d.ts",
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
    schema: "autojs6-node-rhino-compat-app-device-shell-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatAppDeviceShell",
    modules: MODULES,
    globals: RHINO_GLOBALS,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      appDeviceExposed: checkById(checks, "dynamic-app-device-properties"),
      shellCallable: checkById(checks, "dynamic-shell-callable"),
      shellRootExplicit: checkById(checks, "dynamic-shell-root-explicit"),
      noDefaultGlobals: checkById(checks, "dynamic-no-default-app-device-shell"),
      explicitGlobalInstall: checkById(checks, "dynamic-explicit-global-app-device-shell"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat app/device/shell verification: " + report.decision.status);
  console.log("Rhino compat app/device/shell globals: " + RHINO_GLOBALS.join(", "));
  console.log("Rhino compat app/device/shell policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildStaticChecks(files, compatBlock) {
  const manifest = JSON.parse(files.statusManifest.text);
  const moduleById = new Map((manifest.modules || []).map((module) => [module.id, module]));
  const globalModule = moduleById.get("global");
  const appModule = moduleById.get("app");
  const deviceModule = moduleById.get("device");
  const shellModule = moduleById.get("shell");
  const matrixSurfaceFor = (moduleId) => {
    const row = files.matrix.text.split(/\r?\n/).find((line) => new RegExp(`\\| \\d+ \\| ${moduleId} \\| ${moduleId} \\|`).test(line)) || "";
    return row.split("|")[5] || "";
  };
  return [
    check("runtime-compat-shell-function", compatBlock.includes("function __autojs6_rhino_compat_shell()") && compatBlock.includes("function __autojs6_rhino_compat_shell_options"), "runtime must define the callable Rhino shell compatibility wrapper"),
    check("runtime-compat-app-device-shell", RHINO_GLOBALS.every((name) => compatBlock.includes(`"${name}"`)) && compatBlock.includes("app: __autojs6_limited_app()") && compatBlock.includes("device: __autojs6_limited_device()") && compatBlock.includes("shell: shell"), "autojs6:compat must expose app/device/shell and installable globals"),
    check("runtime-status-s11-17", /implementedTasks:\s*Object\.freeze\(\[[^\]]*"S11-17"/.test(compatBlock) && /pendingTasks:\s*Object\.freeze\(\[\]\)/.test(compatBlock), "compat status must mark S11-17 implemented with no pending low-risk tasks"),
    check("types-existing-modules", files.appTypes.text.includes("launchPackage(") && files.deviceTypes.text.includes("isScreenOn(") && files.shellTypes.text.includes("checkAccess("), "base app/device/shell declarations must expose the bridge APIs used by S11-17"),
    check("types-compat-app-device-shell", files.compatTypes.text.includes('import app = require("app")') && files.compatTypes.text.includes('import device = require("device")') && files.compatTypes.text.includes('import shellModule = require("shell")') && files.compatTypes.text.includes("interface ShellCompatFunction") && files.compatTypes.text.includes("readonly app: app.AppModule") && files.compatTypes.text.includes("readonly device: device.DeviceModule") && files.compatTypes.text.includes("readonly shell: ShellCompatFunction"), "rhino_compat.d.ts must declare app/device/shell compat properties and callable shell"),
    check("type-smoke-app-device-shell", files.typeSmokeSource.text.includes("rhinoCompat.app.launchPackage") && files.typeSmokeSource.text.includes("rhinoCompat.device.isScreenOn") && files.typeSmokeSource.text.includes("rhinoCompat.shell(\"echo compat\"") && files.typeSmokeSource.text.includes("rhinoCompat.shell(\"id\", true"), "typescript-smoke sample must exercise S11-17 compat APIs"),
    check("status-global-s11-17", globalModule && globalModule.notes.join("\n").includes("S11-17") && globalModule.bridgeModules.includes("app") && globalModule.bridgeModules.includes("device") && globalModule.bridgeModules.includes("shell"), "global status manifest must mention S11-17 and bridge modules"),
    check("status-app-s11-17", appModule && appModule.notes.join("\n").includes("S11-17") && appModule.nodeModules.includes("app"), "app status manifest must document S11-17 app facade coverage"),
    check("status-device-s11-17", deviceModule && deviceModule.notes.join("\n").includes("S11-17") && deviceModule.nodeModules.includes("device"), "device status manifest must document S11-17 device facade coverage"),
    check("status-shell-s11-17", shellModule && shellModule.notes.join("\n").includes("S11-17") && shellModule.nodeModules.includes("shell"), "shell status manifest must document S11-17 shell facade coverage"),
    check("type-readme-s11-17", files.typeReadme.text.includes("S11-17") && files.typeReadme.text.includes("compat.shell"), "type README must document S11-17 app/device/shell declarations"),
    check("loader-design-s11-17", files.loaderDesign.text.includes("S11-17") && files.loaderDesign.text.includes("compat.app.launchPackage") && files.loaderDesign.text.includes("compat.shell(\"echo ok\""), "loader design must document S11-17 app/device/shell facade behavior"),
    check("migration-s11-17", files.migration.text.includes("compat.app.launchPackage") && files.migration.text.includes("compat.shell(\"echo ok\"") && files.migration.text.includes("root: true"), "migration guide must show S11-17 compat app/device/shell usage and explicit root shell"),
    check("security-shell-boundary", files.securityModel.text.includes("Root mode is explicit") && files.securityModel.text.includes("Shizuku execution remains unsupported") && files.securityModel.text.includes("compat.shell"), "security model must keep shell root/shizuku boundaries clear for S11-17"),
    check("matrix-app-device-shell", matrixSurfaceFor("app").includes("app") && matrixSurfaceFor("app").includes("autojs6:compat") && matrixSurfaceFor("device").includes("device") && matrixSurfaceFor("device").includes("autojs6:compat") && matrixSurfaceFor("shell").includes("shell") && matrixSurfaceFor("shell").includes("autojs6:compat"), "compat matrix must retain app/device/shell module surfaces"),
    check("task-plan-s11-17-complete", /\[x\]\s+S11-17/.test(files.taskPlan.text) && files.taskPlan.text.includes("verifyNodeRhinoCompatAppDeviceShell"), "Phase 11 task plan must mark S11-17 complete and name the verification gate"),
    check("gradle-task", files.gradle.text.includes("verifyNodeRhinoCompatAppDeviceShell") && files.gradle.text.includes("verify-rhino-compat-app-device-shell.js"), "Gradle must register verifyNodeRhinoCompatAppDeviceShell"),
  ];
}

async function buildDynamicChecks(compatBlock) {
  if (!compatBlock) {
    return [check("dynamic-compat-block", false, "runtime compat block could not be extracted")];
  }
  const harness = createCompatHarness(compatBlock);
  const compat = harness.compat;
  const checks = [];
  checks.push(check("dynamic-no-default-app-device-shell", RHINO_GLOBALS.every((name) => !(name in harness.globalObject)), "require autojs6:compat must not install app/device/shell globals by default"));
  checks.push(check("dynamic-status", compat.status().implementedTasks.includes("S11-17") && !compat.status().pendingTasks.includes("S11-17"), "compat status must expose S11-17 as implemented"));
  checks.push(check("dynamic-app-device-properties", compat.app === harness.app && compat.device === harness.device && compat.app.packageName === "org.autojs.autojs6" && compat.device.sdkInt === 36, "compat module must expose app/device bridge modules"));
  checks.push(check("dynamic-shell-shape", typeof compat.shell === "function" && compat.shell.exec === harness.shellModule.exec && compat.shell.execFile === harness.shellModule.execFile && compat.shell.checkAccess === harness.shellModule.checkAccess, "compat.shell must be callable and preserve shell module methods"));

  const launched = await compat.app.launchPackage("org.example.app", { timeoutMs: 101 });
  await compat.app.openAppSetting("org.example.app", { timeoutMs: 102 });
  await compat.app.startActivity({ action: "android.intent.action.VIEW", data: "https://example.invalid" }, { timeoutMs: 103 });
  const screenOn = await compat.device.isScreenOn({ timeoutMs: 104 });
  await compat.device.wakeUp({ timeoutMs: 105 });
  await compat.device.vibrate(33, { timeoutMs: 106 });
  checks.push(check("dynamic-app-device-calls", launched === true && screenOn === true && harness.calls.some((call) => call.module === "app" && call.method === "startActivity") && harness.calls.some((call) => call.module === "device" && call.method === "vibrate" && call.args[0] === 33), "compat app/device methods must route to existing bridge facades"));

  const normal = await compat.shell("echo ok", { timeoutMs: 107, cwd: "sub" });
  const rooted = await compat.shell("id", true);
  const method = await compat.shell.exec("echo method", { timeoutMs: 108 });
  const file = await compat.shell.execFile("scripts/run.sh", ["--dry-run"], { timeoutMs: 109 });
  const access = compat.shell.checkAccess({ root: true, shizuku: true });
  const invalidCode = await rejectedCode(() => compat.shell("echo bad", "root"));
  checks.push(check("dynamic-shell-callable", normal.stdout === "echo ok" && method.stdout === "echo method" && file.stdout === "file:scripts/run.sh:--dry-run" && access.shizuku === false, "compat.shell callable and methods must route through shell facade"));
  checks.push(check("dynamic-shell-root-explicit", rooted.stdout === "id" && harness.calls.some((call) => call.module === "shell" && call.command === "id" && call.options && call.options.root === true) && !harness.calls.some((call) => call.module === "shell" && call.command === "echo ok" && call.options && call.options.root === true), "compat.shell(command, true) must be the only shorthand root path"));
  checks.push(check("dynamic-shell-invalid-option", invalidCode === "ERR_AUTOJS6_RHINO_COMPAT_INVALID_OPTION", "compat.shell invalid option values must reject with the Rhino compat invalid option code"));

  const install = compat.install({ globals: true });
  checks.push(check("dynamic-explicit-global-app-device-shell", RHINO_GLOBALS.every((name) => install.globals.includes(name)) && harness.globalObject.app === compat.app && harness.globalObject.device === compat.device && harness.globalObject.shell === compat.shell, "compat.install({ globals: true }) must explicitly install app/device/shell globals"));
  return checks;
}

function createCompatHarness(compatBlock) {
  const source = `
    (function() {
      let __autojs6_limited_rhino_compat_cache = null;
      let __autojs6_rhino_compat_facade_cache = null;
      const globalObject = {};
      const calls = [];
      const app = Object.freeze({
        packageName: "org.autojs.autojs6",
        versionName: "6.7.1",
        versionCode: 3924,
        launchPackage(packageName, options) { calls.push({ module: "app", method: "launchPackage", args: [packageName], options }); return Promise.resolve(true); },
        launchApp(appName, options) { calls.push({ module: "app", method: "launchApp", args: [appName], options }); return Promise.resolve(true); },
        openAppSetting(packageName, options) { calls.push({ module: "app", method: "openAppSetting", args: [packageName], options }); return Promise.resolve(undefined); },
        startActivity(request, options) { calls.push({ module: "app", method: "startActivity", args: [request], options }); return Promise.resolve(undefined); }
      });
      const device = Object.freeze({
        sdkInt: 36,
        width: 1080,
        height: 2400,
        density: 3,
        isScreenOn(options) { calls.push({ module: "device", method: "isScreenOn", args: [], options }); return Promise.resolve(true); },
        wakeUp(options) { calls.push({ module: "device", method: "wakeUp", args: [], options }); return Promise.resolve(undefined); },
        vibrate(duration, options) { calls.push({ module: "device", method: "vibrate", args: [duration], options }); return Promise.resolve(undefined); }
      });
      const shellModule = Object.freeze({
        exec(command, options) {
          calls.push({ module: "shell", method: "exec", command, options });
          return Promise.resolve(Object.freeze({ code: 0, stdout: String(command), stderr: "" }));
        },
        execFile(file, args, options) {
          calls.push({ module: "shell", method: "execFile", file, args, options });
          return Promise.resolve(Object.freeze({ code: 0, stdout: "file:" + file + ":" + (args || []).join(","), stderr: "" }));
        },
        checkAccess(options) {
          calls.push({ module: "shell", method: "checkAccess", options });
          return Object.freeze({ shell: true, root: options && options.root === true, shizuku: false, missingCapabilities: Object.freeze(options && options.shizuku === true ? ["shell.shizuku"] : []) });
        },
        setDefaultTimeout(ms) {
          calls.push({ module: "shell", method: "setDefaultTimeout", ms });
          return Number(ms);
        }
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
        }),
        images: Object.freeze({
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
      function __autojs6_limited_image() { return moduleStubs.images; }
      function __autojs6_limited_ocr() { return Object.freeze({ recognize() { return Promise.resolve([]); }, recognizeText() { return Promise.resolve(""); }, detectTextBounds() { return Promise.resolve([]); } }); }
      ${compatBlock}
      return {
        compat: __autojs6_limited_rhino_compat(),
        globalObject,
        calls,
        app,
        device,
        shellModule
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
    "# Rhino Compat App Device Shell Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Globals: ${report.globals.map((name) => `\`${name}\``).join(", ")}`,
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
      console.log("Usage: node verify-rhino-compat-app-device-shell.js [--repo-root dir] [--report-dir dir]");
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
