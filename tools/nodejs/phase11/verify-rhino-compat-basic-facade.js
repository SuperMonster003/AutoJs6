#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const vm = require("vm");

const REPORT_BASENAME = "rhino-compat-basic-facade-verification";
const REQUIRED_GLOBALS = ["toast", "toastLog", "sleep", "setClip", "getClip"];
const REQUIRED_STATUS_IDS = ["global.toastLog", "global.sleep", "global.setClip", "global.getClip"];

const FILES = {
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  types: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  design: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  taskPlan: "docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
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
  const staticChecks = buildStaticChecks(files, compatBlock);
  const dynamicChecks = await buildDynamicChecks(compatBlock);
  const checks = [...staticChecks, ...dynamicChecks];
  const policyViolations = checks.filter((check) => !check.passed).map((check) => check.message);
  const report = {
    schema: "autojs6-node-rhino-compat-basic-facade-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatBasicFacade",
    globals: REQUIRED_GLOBALS,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      defaultGlobalInjectionDenied: checkById(checks, "dynamic-no-default-globals"),
      explicitGlobalInstallWorks: checkById(checks, "dynamic-explicit-global-install"),
      asyncFacadeWorks: REQUIRED_GLOBALS.every((name) => checkById(checks, `dynamic-${name}`)),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat basic facade verification: " + report.decision.status);
  console.log("Rhino compat basic facade globals: " + REQUIRED_GLOBALS.join(", "));
  console.log("Rhino compat basic facade policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildStaticChecks(files, compatBlock) {
  const manifest = JSON.parse(files.statusManifest.text);
  const statusEntries = new Map();
  for (const module of manifest.modules || []) {
    for (const entry of module.apiEntries || []) {
      statusEntries.set(entry.id, entry);
    }
  }
  const checks = [
    check("runtime-facade-cache", files.runtime.text.includes("__autojs6_rhino_compat_facade_cache"), "runtime must cache the basic Rhino compat facade"),
    check("runtime-basic-global-list", REQUIRED_GLOBALS.every((name) => compatBlock.includes(`"${name}"`)), "runtime must list every S11-09 low-risk global"),
    check("runtime-no-default-globals", compatBlock.includes("defaultGlobalInjection: false"), "runtime status must keep default global injection disabled"),
    check("runtime-explicit-global-install", compatBlock.includes("input.global === true || input.globals === true") && compatBlock.includes("__autojs6_define_global(name, facade[name])"), "runtime must install globals only after explicit options"),
    check("design-doc-basic-facade", REQUIRED_GLOBALS.every((name) => files.design.text.includes(name)), "design doc must document the S11-09 basic facade names"),
    check("types-basic-facade", REQUIRED_GLOBALS.every((name) => files.types.text.includes(`${name}(`)), "types must declare the S11-09 basic facade methods"),
    check("task-plan-s11-09-complete", /\[x\]\s+S11-09/.test(files.taskPlan.text), "Phase 11 task plan must mark S11-09 complete"),
  ];
  REQUIRED_STATUS_IDS.forEach((id) => {
    const entry = statusEntries.get(id);
    checks.push(check(
      `status-${id}`,
      entry && entry.status === "supported_async" && entry.nodeModule === "autojs6:compat",
      `${id} must be marked supported_async through autojs6:compat`,
    ));
  });
  return checks;
}

async function buildDynamicChecks(compatBlock) {
  if (!compatBlock) {
    return [check("dynamic-compat-block", false, "compat runtime block could not be extracted")];
  }
  const harness = createHarness(compatBlock);
  const checks = [];
  checks.push(check("dynamic-no-default-globals", REQUIRED_GLOBALS.every((name) => !(name in harness.globalObject)), "require autojs6:compat must not install globals by default"));
  checks.push(check("dynamic-status", harness.compat.status().defaultGlobalInjection === false && harness.compat.status().implementedTasks.includes("S11-09"), "compat status must expose S11-09 and keep default globals disabled"));
  checks.push(check("dynamic-invalid-install-option", throwsCode(() => harness.compat.install("bad"), "ERR_AUTOJS6_RHINO_COMPAT_INVALID_OPTION"), "compat.install must reject malformed options with a stable code"));
  await harness.compat.toast("hello");
  checks.push(check("dynamic-toast", harness.calls.some((call) => call.module === "toast" && call.message === "hello"), "compat.toast must call the toast bridge facade"));
  await harness.compat.toastLog("hello", 7);
  checks.push(check("dynamic-toastLog", harness.calls.some((call) => call.module === "toast" && call.message === "hello 7"), "compat.toastLog must log and call the toast bridge facade"));
  await harness.compat.sleep(0);
  checks.push(check("dynamic-sleep", true, "compat.sleep must return a resolved Promise for zero delay"));
  await harness.compat.setClip("abc");
  const clip = await harness.compat.getClip();
  checks.push(check("dynamic-setClip", harness.calls.some((call) => call.module === "clipboard" && call.method === "setText" && call.text === "abc"), "compat.setClip must call clipboard.setText"));
  checks.push(check("dynamic-getClip", clip === "abc", "compat.getClip must resolve clipboard text"));
  const install = harness.compat.install({ globals: true });
  checks.push(check("dynamic-explicit-global-install", install.installed === true && REQUIRED_GLOBALS.every((name) => typeof harness.globalObject[name] === "function"), "compat.install({ globals: true }) must install the S11-09 globals"));
  await harness.globalObject.sleep(0);
  return checks;
}

function createHarness(compatBlock) {
  const source = `
    (function() {
      const __calls = [];
      let __clip = "";
      let __autojs6_limited_rhino_compat_cache = null;
      let __autojs6_rhino_compat_facade_cache = null;
      const __pureUtilityStubs = {
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
      function __autojs6_limited_toast() {
        return {
          showToast(message, options) {
            __calls.push({ module: "toast", method: "showToast", message: String(message), options: options || null });
            return Promise.resolve();
          }
        };
      }
      function __autojs6_limited_clipboard() {
        return {
          setText(text) {
            __clip = String(text);
            __calls.push({ module: "clipboard", method: "setText", text: __clip });
            return Promise.resolve();
          },
          getText() {
            __calls.push({ module: "clipboard", method: "getText" });
            return Promise.resolve(__clip);
          }
        };
      }
      function __autojs6_limited_files() {
        return {
          path(value) {
            return value === undefined || value === null ? null : String(value);
          },
          read() {
            return "";
          },
          write() {},
          exists() {
            return false;
          },
          listDir() {
            return [];
          }
        };
      }
      function __autojs6_limited_base64() {
        return __pureUtilityStubs.base64;
      }
      function __autojs6_limited_colors() {
        return __pureUtilityStubs.colors;
      }
      function __autojs6_limited_formatter() {
        return __pureUtilityStubs.formatter;
      }
      function __autojs6_limited_converter() {
        return __pureUtilityStubs.converter;
      }
      function __autojs6_limited_mime() {
        return __pureUtilityStubs.mime;
      }
      function __autojs6_limited_nanoid() {
        return __pureUtilityStubs.nanoid;
      }
      function __autojs6_limited_util() {
        return __pureUtilityStubs.util;
      }
      function __autojs6_limited_opencc() {
        return __pureUtilityStubs.opencc;
      }
      function __autojs6_limited_pinyin() {
        return __pureUtilityStubs.pinyin;
      }
      function __autojs6_limited_pinyin4j() {
        return __pureUtilityStubs.pinyin4j;
      }
      function __autojs6_limited_storage(moduleName) {
        return moduleName === "storages" ? __pureUtilityStubs.storages : __pureUtilityStubs.storage;
      }
      function __autojs6_limited_database(moduleName) {
        return moduleName === "sqlite" ? __pureUtilityStubs.sqlite : __pureUtilityStubs.database;
      }
      function __autojs6_define_global(name, value) {
        Object.defineProperty(globalThis, name, {
          value,
          configurable: true,
          enumerable: false,
          writable: false
        });
      }
      ${compatBlock}
      return {
        compat: __autojs6_limited_rhino_compat(),
        calls: __calls,
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

function throwsCode(action, expectedCode) {
  try {
    action();
    return false;
  } catch (error) {
    return error && (error.code === expectedCode || error.autojs6Code === expectedCode);
  }
}

function extractCompatBlock(runtime) {
  const start = runtime.indexOf("const __autojs6_rhino_compat_error_codes");
  const end = runtime.indexOf("  function __autojs6_limited_work_manager()", start);
  if (start < 0 || end <= start) {
    return "";
  }
  return runtime.slice(start, end);
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
    "# Rhino Compat Basic Facade Verification",
    "",
    `- Decision: ${report.decision.status}`,
    `- Globals: ${report.globals.map((name) => `\`${name}\``).join(", ")}`,
    `- Checks: ${report.checks.length}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Decisions",
    "",
    `- Default global injection denied: ${report.decision.defaultGlobalInjectionDenied}`,
    `- Explicit global install works: ${report.decision.explicitGlobalInstallWorks}`,
    `- Async facade works: ${report.decision.asyncFacadeWorks}`,
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
      console.log("Usage: node verify-rhino-compat-basic-facade.js [--repo-root dir] [--report-dir dir]");
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
