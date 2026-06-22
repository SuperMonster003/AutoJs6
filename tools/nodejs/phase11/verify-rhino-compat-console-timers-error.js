#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const vm = require("vm");

const REPORT_BASENAME = "rhino-compat-console-timers-error-verification";
const RHINO_GLOBALS = ["console", "setTimeout", "clearTimeout", "setInterval", "clearInterval"];
const MODULES = ["console", "timers"];

const FILES = {
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  consoleTypes: "docs/nodejs/types/autojs6-node/console.d.ts",
  timersTypes: "docs/nodejs/types/autojs6-node/timers.d.ts",
  compatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  indexTypes: "docs/nodejs/types/autojs6-node/index.d.ts",
  typeReadme: "docs/nodejs/types/autojs6-node/README.md",
  nodeExamplesGradle: "app/node-examples.gradle.kts",
  typeSmokeSource: "sample/nodejs/typescript-smoke/src/main.ts",
  design: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
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
  const consoleBlock = extractConsoleBlock(files.runtime.text);
  const timersBlock = extractTimersBlock(files.runtime.text);
  const compatBlock = extractCompatBlock(files.runtime.text);
  const checks = [
    ...buildStaticChecks(files, consoleBlock, timersBlock, compatBlock),
    ...buildDynamicChecks(consoleBlock, timersBlock, compatBlock),
  ];
  const policyViolations = checks.filter((check) => !check.passed).map((check) => check.message);
  const report = {
    schema: "autojs6-node-rhino-compat-console-timers-error-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatConsoleTimersError",
    modules: MODULES,
    globals: RHINO_GLOBALS,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      consoleAliasesWork: checkById(checks, "dynamic-console-alias-output"),
      consoleUnsupportedStable: checkById(checks, "dynamic-console-unsupported"),
      timersWork: checkById(checks, "dynamic-timers"),
      compatInstallWorks: checkById(checks, "dynamic-explicit-global-console-timers"),
      errorStackSourceMapsPresent: checkById(checks, "runtime-error-source-map-stack"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat console/timers/error verification: " + report.decision.status);
  console.log("Rhino compat console/timers/error modules: " + MODULES.join(", "));
  console.log("Rhino compat console/timers/error policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildStaticChecks(files, consoleBlock, timersBlock, compatBlock) {
  const manifest = JSON.parse(files.statusManifest.text);
  const moduleById = new Map((manifest.modules || []).map((module) => [module.id, module]));
  const statusEntry = (moduleId, entryId) => {
    const module = moduleById.get(moduleId);
    return module && (module.apiEntries || []).find((item) => item.id === entryId);
  };
  const supportedConsole = ["console.log", "console.print", "console.verbose", "console.err", "console.time", "console.timeEnd", "console.printAllStackTrace"];
  const partialConsole = ["console.show", "console.setSize", "console.setTitle", "console.launchConsole"];
  const supportedTimers = ["timers.setTimeout", "timers.clearTimeout", "timers.setInterval", "timers.clearInterval", "timers.setImmediate", "timers.clearImmediate"];
  const unsupportedTimers = ["timers.keepAlive", "timers.setIntervalExt", "timers.loop"];
  return [
    check("runtime-console-block", consoleBlock.includes("function __autojs6_limited_console()") && consoleBlock.includes("print: { value: defaultMethod(\"log\")") && consoleBlock.includes("verbose: { value: defaultMethod(\"debug\")") && consoleBlock.includes("err: { value: defaultMethod(\"error\")"), "runtime must expose S11-14 console output aliases"),
    check("runtime-console-ui-noop", ["show", "hide", "setSize", "setTitle", "launchConsole"].every((name) => consoleBlock.includes(`${name}: { value: consoleNoopReturnSelf`)), "runtime must keep common floating-console controls as no-op output-capture shims"),
    check("runtime-console-unsupported", consoleBlock.includes("console.input") && consoleBlock.includes("console.rawInput") && consoleBlock.includes("console.build") && consoleBlock.includes("function consoleUnsupported"), "runtime must expose stable unsupported paths for interactive console input/build APIs"),
    check("runtime-timers-block", timersBlock.includes("function __autojs6_limited_timers()") && supportedTimers.every((id) => timersBlock.includes(id.slice("timers.".length))), "runtime must expose standard Node timer functions"),
    check("runtime-require-console-timers", ["console", "node:console", "timers", "node:timers"].every((name) => files.runtime.text.includes(`name === "${name}"`)), "runtime must resolve and require console/timers aliases"),
    check("runtime-compat-console-timers", RHINO_GLOBALS.every((name) => compatBlock.includes(`"${name}"`)) && compatBlock.includes("console: __autojs6_limited_console()") && compatBlock.includes("timers: __autojs6_limited_timers()") && compatBlock.includes("setTimeout: __autojs6_limited_timers().setTimeout"), "autojs6:compat must expose S11-14 console/timers facades and installable globals"),
    check("runtime-status-s11-14", /implementedTasks:\s*Object\.freeze\(\[[^\]]*"S11-14"/.test(compatBlock) && /pendingTasks:\s*Object\.freeze\(\[\]\)/.test(compatBlock), "compat status must mark S11-14 implemented with no pending low-risk tasks"),
    check("runtime-error-source-map-stack", files.runtime.text.includes("errorStack: __autojs6_error_stack(error)") && files.runtime.text.includes("function __autojs6_apply_source_maps_to_stack") && files.runtime.text.includes("fields.sourceMapMappedFrameCount"), "runtime must keep source-map-aware errorStack diagnostics"),
    check("types-console", files.consoleTypes.text.includes('declare module "console"') && files.consoleTypes.text.includes("print(") && files.consoleTypes.text.includes("show(") && files.consoleTypes.text.includes("input("), "console.d.ts must declare Node console plus Rhino debug aliases"),
    check("types-timers", files.timersTypes.text.includes('declare module "timers"') && files.timersTypes.text.includes("declare function setTimeout") && files.timersTypes.text.includes('declare module "timers/promises"'), "timers.d.ts must declare timer module and global functions"),
    check("types-compat-console-timers", files.compatTypes.text.includes('import consoleModule = require("console")') && files.compatTypes.text.includes('import timers = require("timers")') && files.compatTypes.text.includes("readonly console:") && files.compatTypes.text.includes("readonly setTimeout:"), "rhino_compat.d.ts must expose S11-14 console/timers properties"),
    check("types-index-console-timers", MODULES.every((name) => files.indexTypes.text.includes(`./${name}.d.ts`)), "index.d.ts must reference console/timers declarations"),
    check("type-gate-console-timers", MODULES.every((name) => files.nodeExamplesGradle.text.includes(`"${name}"`)), "verifyAutoJs6NodeTypeDeclarations must list console/timers declarations"),
    check("type-smoke-console-timers", MODULES.every((name) => files.typeSmokeSource.text.includes(`require("${name}")`)) && files.typeSmokeSource.text.includes("consoleModule.show()") && files.typeSmokeSource.text.includes("setInterval("), "typescript-smoke sample must import and exercise console/timers declarations"),
    check("status-console-module", moduleById.get("console") && moduleById.get("console").status === "partial" && moduleById.get("console").nodeModules.includes("console"), "status manifest must mark console partial through the controlled console module"),
    check("status-console-supported", supportedConsole.every((id) => statusEntry("console", id) && statusEntry("console", id).status === "supported"), "status manifest must mark common console output APIs supported"),
    check("status-console-partial", partialConsole.every((id) => statusEntry("console", id) && statusEntry("console", id).status === "partial"), "status manifest must mark floating-console controls partial no-op"),
    check("status-console-unsupported", ["console.input", "console.rawInput", "console.build"].every((id) => statusEntry("console", id) && statusEntry("console", id).status === "unsupported"), "status manifest must keep interactive console APIs unsupported"),
    check("status-timers-module", moduleById.get("timers") && moduleById.get("timers").status === "partial" && moduleById.get("timers").nodeModules.includes("timers"), "status manifest must mark timers partial with standard Node timer coverage"),
    check("status-timers-supported", supportedTimers.every((id) => statusEntry("timers", id) && statusEntry("timers", id).status === "supported"), "status manifest must mark standard timers supported"),
    check("status-timers-unsupported", unsupportedTimers.every((id) => statusEntry("timers", id) && statusEntry("timers", id).status === "unsupported"), "status manifest must keep Auto.js timer extensions unsupported"),
    check("type-readme-s11-14", files.typeReadme.text.includes("S11-14") && files.typeReadme.text.includes("console") && files.typeReadme.text.includes("timers"), "type README must document S11-14 console/timers declarations"),
    check("design-doc-s11-14", files.design.text.includes("S11-14") && files.design.text.includes("console.show()") && files.design.text.includes("source-map-aware"), "design doc must document S11-14 console/timers/error behavior"),
    check("migration-doc-s11-14", files.migration.text.includes("autojs6:compat") && files.migration.text.includes("console.show()") && files.migration.text.includes("errorStack"), "migration doc must explain S11-14 debug migration behavior"),
    check("matrix-s11-14", /\| \d+ \| timers \| timers \| partial \|/.test(files.matrix.text) && /\| \d+ \| console \| console \| partial \|/.test(files.matrix.text), "compat matrix must reflect S11-14 console/timers partial status"),
    check("task-plan-s11-14-complete", /\[x\]\s+S11-14/.test(files.taskPlan.text), "Phase 11 task plan must mark S11-14 complete"),
    check("gradle-task", files.gradle.text.includes("verifyNodeRhinoCompatConsoleTimersError") && files.gradle.text.includes("verify-rhino-compat-console-timers-error.js"), "Gradle must register verifyNodeRhinoCompatConsoleTimersError"),
  ];
}

function buildDynamicChecks(consoleBlock, timersBlock, compatBlock) {
  if (!consoleBlock || !timersBlock || !compatBlock) {
    return [check("dynamic-blocks", false, "runtime console, timers, or compat block could not be extracted")];
  }
  const runtimeHarness = createRuntimeHarness(consoleBlock, timersBlock);
  const checks = [];

  runtimeHarness.console.log("hello", 7);
  runtimeHarness.console.print("print");
  runtimeHarness.console.verbose("verbose");
  runtimeHarness.console.err("err");
  runtimeHarness.console.assert(false, "assertion");
  runtimeHarness.console.time("phase");
  runtimeHarness.console.timeEnd("phase");
  const chained = runtimeHarness.console.show().setTitle("Debug").setSize(320, 200).hide();
  runtimeHarness.console.printAllStackTrace(new Error("boom"));
  checks.push(check("dynamic-console-alias-output", runtimeHarness.stdout.some((line) => line.includes("hello") && line.includes("7")) && runtimeHarness.stdout.some((line) => line.includes("print")) && runtimeHarness.stdout.some((line) => line.includes("verbose")) && runtimeHarness.stderr.some((line) => line.includes("err")) && runtimeHarness.stderr.some((line) => line.includes("Assertion failed")) && runtimeHarness.stderr.some((line) => line.includes("boom")), "console aliases must write readable stdout/stderr output"));
  checks.push(check("dynamic-console-ui-noop", chained === runtimeHarness.console, "floating-console no-op shims must return the console module for chaining"));
  checks.push(check("dynamic-console-unsupported", throwsCode(() => runtimeHarness.console.input("name"), "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED") && throwsCode(() => runtimeHarness.console.rawInput("name"), "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED") && throwsCode(() => runtimeHarness.console.build({}), "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED"), "interactive console APIs must fail with stable unsupported codes"));

  const timeout = runtimeHarness.timers.setTimeout(function noop() {}, 1);
  runtimeHarness.timers.clearTimeout(timeout);
  const interval = runtimeHarness.timers.setInterval(function noop() {}, 2);
  runtimeHarness.timers.clearInterval(interval);
  const immediate = runtimeHarness.timers.setImmediate(function noop() {});
  runtimeHarness.timers.clearImmediate(immediate);
  checks.push(check("dynamic-timers", ["setTimeout", "clearTimeout", "setInterval", "clearInterval", "setImmediate", "clearImmediate"].every((name) => runtimeHarness.timerCalls.some((call) => call.name === name)), "timers facade must call every standard timer operation"));

  const compatHarness = createCompatHarness(compatBlock);
  checks.push(check("dynamic-no-default-console-timers", RHINO_GLOBALS.every((name) => !(name in compatHarness.globalObject)), "require autojs6:compat must not install S11-14 globals by default"));
  checks.push(check("dynamic-status", compatHarness.compat.status().implementedTasks.includes("S11-14") && !compatHarness.compat.status().pendingTasks.includes("S11-14"), "compat status must expose S11-14 as implemented"));
  checks.push(check("dynamic-compat-properties", compatHarness.compat.console === compatHarness.console && compatHarness.compat.timers === compatHarness.timers && compatHarness.compat.setTimeout === compatHarness.timers.setTimeout, "compat module must expose console/timers properties"));
  const install = compatHarness.compat.install({ globals: true });
  checks.push(check("dynamic-explicit-global-console-timers", RHINO_GLOBALS.every((name) => install.globals.includes(name)) && compatHarness.globalObject.console === compatHarness.console && compatHarness.globalObject.setTimeout === compatHarness.timers.setTimeout, "compat.install({ globals: true }) must explicitly install S11-14 globals"));
  return checks;
}

function createRuntimeHarness(consoleBlock, timersBlock) {
  const source = `
    (function() {
      const stdout = [];
      const stderr = [];
      const timerCalls = [];
      let __autojs6_limited_console_cache = null;
      let __autojs6_limited_timers_cache = null;
      const process = {
        stdout: Object.freeze({ write(value) { stdout.push(String(value)); } }),
        stderr: Object.freeze({ write(value) { stderr.push(String(value)); } })
      };
      const __timerModule = Object.freeze({
        setTimeout(callback, delay) { timerCalls.push({ name: "setTimeout", delay }); return { type: "timeout" }; },
        clearTimeout(handle) { timerCalls.push({ name: "clearTimeout", handle }); },
        setInterval(callback, delay) { timerCalls.push({ name: "setInterval", delay }); return { type: "interval" }; },
        clearInterval(handle) { timerCalls.push({ name: "clearInterval", handle }); },
        setImmediate(callback) { timerCalls.push({ name: "setImmediate" }); return { type: "immediate" }; },
        clearImmediate(handle) { timerCalls.push({ name: "clearImmediate", handle }); }
      });
      function __autojs6_timers_module() { return __timerModule; }
      function __autojs6_has_own(object, name) { return Object.prototype.hasOwnProperty.call(object, name); }
      function __autojs6_define_error_property(error, name, value) {
        Object.defineProperty(error, name, { value, configurable: true, enumerable: true, writable: true });
      }
      function __autojs6_error(message, code) {
        const error = new Error(message);
        __autojs6_define_error_property(error, "code", code);
        __autojs6_define_error_property(error, "autojs6Code", code);
        return error;
      }
      function __autojs6_invalid_arg_type(message) {
        return __autojs6_error(message, "ERR_INVALID_ARG_TYPE");
      }
      function __autojs6_rhino_compat_unsupported(apiName, reason) {
        const error = __autojs6_error(
          "autojs6:compat " + apiName + " is unsupported: " + reason,
          "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED"
        );
        __autojs6_define_error_property(error, "module", "autojs6:compat");
        __autojs6_define_error_property(error, "api", apiName);
        return error;
      }
      function __autojs6_console_text(value) {
        if (value && value.stack !== undefined && value.stack !== null) return String(value.stack);
        if (typeof value === "string") return value;
        try { return JSON.stringify(value); } catch (_) { return String(value); }
      }
      function __autojs6_console_write(stream, values) {
        stream.write(Array.prototype.map.call(values, __autojs6_console_text).join(" ") + "\\n");
      }
      ${timersBlock}
      ${consoleBlock}
      return {
        console: __autojs6_limited_console(),
        timers: __autojs6_limited_timers(),
        stdout,
        stderr,
        timerCalls
      };
    })()
  `;
  return vm.runInNewContext(source, {
    Error,
    JSON,
    Math,
    Number,
    Object,
    String,
    TypeError,
    globalThis: {},
  });
}

function createCompatHarness(compatBlock) {
  const source = `
    (function() {
      let __autojs6_limited_rhino_compat_cache = null;
      let __autojs6_rhino_compat_facade_cache = null;
      const consoleStub = Object.freeze({ log() {}, info() {}, warn() {}, error() {}, verbose() {}, print() {}, show() { return this; } });
      const timersStub = Object.freeze({
        setTimeout() { return { type: "timeout" }; },
        clearTimeout() {},
        setInterval() { return { type: "interval" }; },
        clearInterval() {},
        setImmediate() { return { type: "immediate" }; },
        clearImmediate() {}
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
        sqlite: Object.freeze(function sqlite() { return Promise.resolve({ close() { return Promise.resolve(true); } }); })
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
      function __autojs6_limited_toast() { return { showToast() { return Promise.resolve(); } }; }
      function __autojs6_limited_clipboard() { return { setText() { return Promise.resolve(); }, getText() { return Promise.resolve(""); } }; }
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
      function __autojs6_limited_console() { return consoleStub; }
      function __autojs6_limited_timers() { return timersStub; }
      function __autojs6_define_global(name, value) {
        Object.defineProperty(globalThis, name, { value, configurable: true, enumerable: false, writable: false });
      }
      ${compatBlock}
      return {
        compat: __autojs6_limited_rhino_compat(),
        console: consoleStub,
        timers: timersStub,
        globalObject: globalThis
      };
    })()
  `;
  return vm.runInNewContext(source, {
    Error,
    Math,
    Number,
    Object,
    Promise,
    RegExp,
    String,
    console: { log() {} },
    globalThis: {},
    setTimeout,
  });
}

function extractConsoleBlock(runtime) {
  const start = runtime.indexOf("function __autojs6_limited_console()");
  const end = runtime.indexOf("  const __autojs6_limited_v8_serialized_size_limit", start);
  if (start < 0 || end <= start) {
    return "";
  }
  return runtime.slice(start, end);
}

function extractTimersBlock(runtime) {
  const start = runtime.indexOf("function __autojs6_timer_function");
  const end = runtime.indexOf("  function __autojs6_timers_promises_options", start);
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

function throwsCode(action, expectedCode) {
  try {
    action();
    return false;
  } catch (error) {
    return error && (error.code === expectedCode || error.autojs6Code === expectedCode);
  }
}

function check(id, passed, message) {
  return { id, passed: passed === true, message };
}

function checkById(checks, id) {
  const item = checks.find((checkItem) => checkItem.id === id);
  return item ? item.passed : false;
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat Console/Timers/Error Verification",
    "",
    `- Decision: ${report.decision.status}`,
    `- Modules: ${report.modules.map((name) => `\`${name}\``).join(", ")}`,
    `- Globals: ${report.globals.map((name) => `\`${name}\``).join(", ")}`,
    `- Checks: ${report.checks.length}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Decisions",
    "",
    `- Console aliases work: ${report.decision.consoleAliasesWork}`,
    `- Console unsupported paths stable: ${report.decision.consoleUnsupportedStable}`,
    `- Timers work: ${report.decision.timersWork}`,
    `- Explicit global install works: ${report.decision.compatInstallWorks}`,
    `- Error stack source maps present: ${report.decision.errorStackSourceMapsPresent}`,
    "",
    "## Checks",
    "",
    "| Check | Result |",
    "| --- | --- |",
  ];
  report.checks.forEach((checkItem) => {
    lines.push(`| ${checkItem.id} | ${checkItem.passed ? "pass" : "fail"} |`);
  });
  lines.push("", "## Policy Violations", "");
  if (report.policyViolations.length === 0) {
    lines.push("None.");
  } else {
    report.policyViolations.forEach((violation) => lines.push(`- ${violation}`));
  }
  return lines.join("\n") + "\n";
}

function read(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "";
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-rhino-compat-console-timers-error.js [--repo-root dir] [--report-dir dir]");
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
  process.exit(1);
});
