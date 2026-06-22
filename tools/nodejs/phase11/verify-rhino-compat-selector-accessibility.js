#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const vm = require("vm");

const REPORT_BASENAME = "rhino-compat-selector-accessibility-verification";
const BUILDER_NAMES = Object.freeze(["text", "desc", "id", "className", "textContains", "descContains", "textMatches", "descMatches", "idMatches", "classNameMatches", "clickable", "enabled", "depth", "boundsInside", "boundsContains"]);
const ACTION_NAMES = Object.freeze(["click", "back", "home", "recentApps", "recents"]);
const RHINO_GLOBALS = Object.freeze(["selector", ...BUILDER_NAMES, ...ACTION_NAMES]);

const FILES = {
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  compatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  accessibilityTypes: "docs/nodejs/types/autojs6-node/accessibility.d.ts",
  typeReadme: "docs/nodejs/types/autojs6-node/README.md",
  typeSmokeSource: "sample/nodejs/typescript-smoke/src/main.ts",
  instrumentationTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilitySelectorBridgeInstrumentationTest.kt",
  selectorDesign: "docs/nodejs/design/RHINO_SELECTOR_DSL_HANDLE.md",
  loaderDesign: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
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
  const compatBlock = extractCompatBlock(files.runtime.text);
  const checks = [
    ...buildStaticChecks(files, compatBlock),
    ...(await buildDynamicChecks(compatBlock)),
  ];
  const policyViolations = checks.filter((check) => !check.passed).map((check) => check.message);
  const report = {
    schema: "autojs6-node-rhino-compat-selector-accessibility-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatSelectorAccessibility",
    globals: RHINO_GLOBALS,
    builders: BUILDER_NAMES,
    actions: ACTION_NAMES,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      immutableHandle: checkById(checks, "dynamic-immutable-chain"),
      timeoutNormalized: checkById(checks, "dynamic-find-one-timeout"),
      promiseActions: checkById(checks, "dynamic-promise-actions"),
      explicitGlobalInstall: checkById(checks, "dynamic-explicit-global-selector"),
      noDefaultGlobals: checkById(checks, "dynamic-no-default-selector-globals"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat selector/accessibility verification: " + report.decision.status);
  console.log("Rhino compat selector/accessibility globals: " + RHINO_GLOBALS.join(", "));
  console.log("Rhino compat selector/accessibility policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildStaticChecks(files, compatBlock) {
  const manifest = JSON.parse(files.statusManifest.text);
  const moduleById = new Map((manifest.modules || []).map((module) => [module.id, module]));
  const selectorModule = moduleById.get("selector");
  const automatorModule = moduleById.get("automator");
  return [
    check("runtime-compat-block", compatBlock.includes("function __autojs6_rhino_selector_handle") && compatBlock.includes("const __autojs6_rhino_selector_handle_type = \"autojs6.rhino.selector.handle\""), "runtime must define Rhino selector handle factory"),
    check("runtime-builder-list", BUILDER_NAMES.every((name) => compatBlock.includes(`"${name}"`)) && compatBlock.includes("const __autojs6_rhino_selector_builder_names"), "runtime must list all S11-16 selector builders"),
    check("runtime-action-list", ACTION_NAMES.every((name) => compatBlock.includes(`"${name}"`)) && compatBlock.includes("const __autojs6_rhino_selector_action_names"), "runtime must list S11-16 accessibility action wrappers"),
    check("runtime-compat-properties", BUILDER_NAMES.every((name) => compatBlock.includes(`${name}: selector.${name}`)) && ["selector: selector", "click: selector.click", "back: selector.back", "home: selector.home"].every((snippet) => compatBlock.includes(snippet)), "autojs6:compat must expose selector object, builder functions, and core actions"),
    check("runtime-timeout-normalization", compatBlock.includes("function __autojs6_rhino_selector_find_one_options") && compatBlock.includes("return Object.freeze({ timeoutMs: options });"), "findOne(number) must normalize to { timeoutMs }"),
    check("runtime-find-alias", compatBlock.includes("find: function(options)") && compatBlock.includes("findAll(frozenDescriptor, findOptions)"), "Rhino find() must route to accessibility.findAll"),
    check("runtime-status-s11-16", /implementedTasks:\s*Object\.freeze\(\[[^\]]*"S11-16"/.test(compatBlock) && /pendingTasks:\s*Object\.freeze\(\[\]\)/.test(compatBlock), "compat status must mark S11-16 implemented with no pending low-risk tasks"),
    check("types-selector-handle", files.compatTypes.text.includes("interface RhinoSelectorHandle") && files.compatTypes.text.includes('readonly type: "autojs6.rhino.selector.handle"') && files.compatTypes.text.includes("readonly descriptor: accessibility.SelectorDescriptor"), "rhino_compat.d.ts must declare RhinoSelectorHandle"),
    check("types-selector-module", files.compatTypes.text.includes("interface SelectorDslModule") && files.compatTypes.text.includes("readonly selector: SelectorDslModule") && BUILDER_NAMES.every((name) => files.compatTypes.text.includes(`${name}(`)), "rhino_compat.d.ts must declare selector DSL module and builders"),
    check("types-promise-boundary", ["findOne", "find(", "findAll", "click", "back", "home", "exists"].every((name) => files.compatTypes.text.includes(name)) && files.compatTypes.text.includes("Promise<"), "rhino_compat.d.ts must keep selector query/action APIs Promise-returning"),
    check("type-smoke-selector", files.typeSmokeSource.text.includes('require("autojs6:compat")') && files.typeSmokeSource.text.includes("rhinoCompat.text(\"OK\")") && files.typeSmokeSource.text.includes("findOne(1000)") && files.typeSmokeSource.text.includes("rhinoCompat.click(1, 2"), "typescript-smoke sample must import and exercise S11-16 selector compatibility"),
    check("instrumentation-selector-facade", files.instrumentationTest.text.includes("jsRhinoCompatSelectorFacadeUsesFakeTransport") && files.instrumentationTest.text.includes('require("autojs6:compat")') && files.instrumentationTest.text.includes("rhino.compat.selector.requests=findOne|findAll|click|click|back|home|recentApps"), "instrumentation tests must cover the S11-16 compat selector facade through fake transport"),
    check("status-selector-s11-16", selectorModule && selectorModule.notes.join("\n").includes("S11-16") && selectorModule.nodeModules.includes("accessibility"), "status manifest must document S11-16 selector facade coverage"),
    check("status-automator-core", automatorModule && automatorModule.notes.join("\n").includes("S11-16") && automatorModule.apiEntries.some((entry) => entry.id === "automator.click" && entry.status === "supported_async"), "status manifest must document S11-16 automator core action wrappers"),
    check("type-readme-s11-16", files.typeReadme.text.includes("S11-16") && files.typeReadme.text.includes("selector") && files.typeReadme.text.includes("findOne"), "type README must mention S11-16 selector compatibility"),
    check("loader-design-s11-16", files.loaderDesign.text.includes("S11-16") && files.loaderDesign.text.includes("compat.text(\"OK\")") && files.loaderDesign.text.includes("selector DSL"), "loader design must document S11-16 selector facade behavior"),
    check("migration-s11-16", files.migration.text.includes("compat.text(\"OK\")") && files.migration.text.includes("findOne(3000)") && files.migration.text.includes("No raw `AccessibilityNodeInfo`"), "migration guide must show the S11-16 compat selector path and no raw node leakage"),
    check("matrix-selector-accessibility", /\| \d+ \| selector \| selector \| partial \| accessibility \|/.test(files.matrix.text), "compat matrix must retain selector accessibility surface"),
    check("task-plan-s11-16-complete", /\[x\]\s+S11-16/.test(files.taskPlan.text) && files.taskPlan.text.includes("verifyNodeRhinoCompatSelectorAccessibility"), "Phase 11 task plan must mark S11-16 complete and name the verification gate"),
    check("gradle-task", files.gradle.text.includes("verifyNodeRhinoCompatSelectorAccessibility") && files.gradle.text.includes("verify-rhino-compat-selector-accessibility.js"), "Gradle must register verifyNodeRhinoCompatSelectorAccessibility"),
  ];
}

async function buildDynamicChecks(compatBlock) {
  if (!compatBlock) {
    return [check("dynamic-compat-block", false, "runtime compat block could not be extracted")];
  }
  const harness = createCompatHarness(compatBlock);
  const checks = [];
  const compat = harness.compat;

  checks.push(check("dynamic-no-default-selector-globals", RHINO_GLOBALS.every((name) => !(name in harness.globalObject)), "require autojs6:compat must not install selector globals by default"));
  checks.push(check("dynamic-status", compat.status().implementedTasks.includes("S11-16") && !compat.status().pendingTasks.includes("S11-16"), "compat status must expose S11-16 as implemented"));
  checks.push(check("dynamic-compat-selector-properties", compat.selector.text === compat.text && compat.selector.click === compat.click && compat.selector.back === compat.back, "compat module must expose selector object and direct builder/action aliases"));

  const base = compat.text("OK");
  const chained = base.descContains("Confirm").clickable();
  checks.push(check("dynamic-handle-shape", base.type === "autojs6.rhino.selector.handle" && base.descriptor.type === "autojs6.accessibility.selector" && typeof base.findOne === "function" && typeof base.click === "function", "selector builders must return Rhino selector handles over accessibility descriptors"));
  checks.push(check("dynamic-immutable-chain", Object.isFrozen(base) && Object.isFrozen(base.descriptor) && Object.isFrozen(chained) && Object.isFrozen(chained.descriptor) && base !== chained && base.descriptor !== chained.descriptor && base.descriptor.descContains === undefined && chained.descriptor.descContains === "Confirm" && chained.descriptor.clickable === true, "selector chains must return new frozen handles without mutating earlier descriptors"));
  checks.push(check("dynamic-builder-validation", throws(() => compat.text("")) && !harness.calls.some((call) => call.method === "findOne" || call.method === "findAll" || call.method === "click"), "malformed selector builder arguments must fail before bridge dispatch"));

  const one = await chained.findOne(3000);
  const all = await chained.find({ timeoutMs: 2500 });
  const foundAll = await chained.findAll({ timeoutMs: 1500 });
  const exists = await chained.exists(500);
  checks.push(check("dynamic-find-one-timeout", harness.calls.some((call) => call.method === "findOne" && call.options && call.options.timeoutMs === 3000) && one && one.text === "OK", "findOne(number) must map to accessibility.findOne(selector, { timeoutMs })"));
  checks.push(check("dynamic-find-alias", harness.calls.some((call) => call.method === "findAll" && call.options && call.options.timeoutMs === 2500) && Array.isArray(all) && Array.isArray(foundAll) && exists === true, "find() and findAll() must route through accessibility.findAll and exists() must stay async"));

  const clicked = await chained.click({ timeoutMs: 1000 });
  const longClicked = await chained.longClick({ timeoutMs: 1001 });
  const typed = await chained.setText("value", { timeoutMs: 1002 });
  const scrolledForward = await chained.scrollForward({ timeoutMs: 1003 });
  const scrolledBackward = await chained.scrollBackward({ timeoutMs: 1004 });
  const pointClickedPromise = compat.click(1, 2, { timeoutMs: 1005 });
  checks.push(check("dynamic-promise-actions", typeof pointClickedPromise.then === "function" && clicked && longClicked && typed && scrolledForward && scrolledBackward && await pointClickedPromise, "selector and coordinate actions must return Promises resolving bridge booleans"));
  await compat.back({ timeoutMs: 1006 });
  await compat.home({ timeoutMs: 1007 });
  await compat.recentApps({ timeoutMs: 1008 });
  await compat.recents({ timeoutMs: 1009 });
  checks.push(check("dynamic-global-actions", ["back", "home", "recentApps"].every((method) => harness.calls.some((call) => call.method === method)) && harness.calls.filter((call) => call.method === "recentApps").length >= 2, "back/home/recentApps/recents wrappers must route to accessibility simple actions"));

  const install = compat.install({ globals: true });
  checks.push(check("dynamic-explicit-global-selector", RHINO_GLOBALS.every((name) => install.globals.includes(name)) && harness.globalObject.selector === compat.selector && harness.globalObject.text === compat.text && harness.globalObject.click === compat.click && harness.globalObject.back === compat.back, "compat.install({ globals: true }) must explicitly install selector globals"));
  checks.push(check("dynamic-no-raw-node-leak", !("node" in base) && !("raw" in base) && !("accessibilityNodeInfo" in base) && one && !("accessibilityNodeInfo" in one), "selector handles and results must not expose raw node handles"));
  return checks;
}

function createCompatHarness(compatBlock) {
  const source = `
    (function() {
      let __autojs6_limited_rhino_compat_cache = null;
      let __autojs6_rhino_compat_facade_cache = null;
      const calls = [];
      const globalObject = {};
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
        app: Object.freeze({
          packageName: "org.autojs.autojs6",
          versionName: "6.7.1",
          versionCode: 3924,
          launchPackage() { return Promise.resolve(true); },
          launchApp() { return Promise.resolve(true); },
          openAppSetting() { return Promise.resolve(undefined); },
          startActivity() { return Promise.resolve(undefined); }
        }),
        device: Object.freeze({
          sdkInt: 36,
          width: 1080,
          height: 2400,
          density: 3,
          isScreenOn() { return Promise.resolve(true); },
          wakeUp() { return Promise.resolve(undefined); },
          vibrate() { return Promise.resolve(undefined); }
        }),
        shell: Object.freeze({
          exec(command) { return Promise.resolve(Object.freeze({ code: 0, stdout: String(command), stderr: "" })); },
          execFile(path) { return Promise.resolve(Object.freeze({ code: 0, stdout: String(path), stderr: "" })); },
          checkAccess(options) { return Object.freeze({ shell: true, root: !!(options && options.root === true), shizuku: false, missingCapabilities: Object.freeze([]) }); },
          setDefaultTimeout(ms) { return Number(ms); }
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
      function selectorError(methodName) {
        return __autojs6_error("accessibility." + methodName + " invalid selector input", "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED");
      }
      function patternInput(value, methodName) {
        if (value instanceof RegExp) return Object.freeze({ pattern: value.source, flags: value.ignoreCase ? "i" : "" });
        const text = value === undefined || value === null ? "" : String(value);
        if (!text) throw selectorError(methodName);
        return text;
      }
      function stringInput(value, methodName) {
        const text = value === undefined || value === null ? "" : String(value);
        if (!text) throw selectorError(methodName);
        return text;
      }
      function boundsInput(args, methodName) {
        const input = args.length === 1 && args[0] && typeof args[0] === "object"
          ? args[0]
          : { left: args[0], top: args[1], right: args[2], bottom: args[3] };
        const bounds = Object.freeze({
          left: Number(input.left),
          top: Number(input.top),
          right: Number(input.right),
          bottom: Number(input.bottom)
        });
        if (![bounds.left, bounds.top, bounds.right, bounds.bottom].every(Number.isFinite)) throw selectorError(methodName);
        return bounds;
      }
      function descriptor(field, value) {
        const result = { type: "autojs6.accessibility.selector", version: 1 };
        result[field] = value;
        return Object.freeze(result);
      }
      function selectorBuilder(field) {
        return function(value) {
          if (field === "clickable" || field === "enabled") return descriptor(field, value !== false);
          if (field === "depth") return descriptor(field, Math.max(0, Math.trunc(Number(value))));
          if (field === "boundsInside" || field === "boundsContains") return descriptor(field, boundsInput(arguments, field));
          if (field.endsWith("Matches")) return descriptor(field, patternInput(value, field));
          return descriptor(field, stringInput(value, field));
        };
      }
      const accessibilityStub = {
        text: selectorBuilder("text"),
        desc: selectorBuilder("desc"),
        id: selectorBuilder("id"),
        className: selectorBuilder("className"),
        textContains: selectorBuilder("textContains"),
        descContains: selectorBuilder("descContains"),
        textMatches: selectorBuilder("textMatches"),
        descMatches: selectorBuilder("descMatches"),
        idMatches: selectorBuilder("idMatches"),
        classNameMatches: selectorBuilder("classNameMatches"),
        clickable: selectorBuilder("clickable"),
        enabled: selectorBuilder("enabled"),
        depth: selectorBuilder("depth"),
        boundsInside: selectorBuilder("boundsInside"),
        boundsContains: selectorBuilder("boundsContains"),
        findOne(selector, options) {
          calls.push({ method: "findOne", selector, options });
          return Promise.resolve(Object.freeze({ text: "OK", bounds: Object.freeze({ left: 0, top: 0, right: 1, bottom: 1 }) }));
        },
        findAll(selector, options) {
          calls.push({ method: "findAll", selector, options });
          return Promise.resolve(Object.freeze([Object.freeze({ text: "OK", bounds: Object.freeze({ left: 0, top: 0, right: 1, bottom: 1 }) })]));
        },
        click(x, y, options) {
          if (x && typeof x === "object") calls.push({ method: "click", selector: x, options: y });
          else calls.push({ method: "click", x, y, options });
          return Promise.resolve(true);
        },
        longClick(selector, options) {
          calls.push({ method: "longClick", selector, options });
          return Promise.resolve(true);
        },
        setText(selector, text, options) {
          calls.push({ method: "setText", selector, text, options });
          return Promise.resolve(true);
        },
        scrollForward(selector, options) {
          calls.push({ method: "scrollForward", selector, options });
          return Promise.resolve(true);
        },
        scrollBackward(selector, options) {
          calls.push({ method: "scrollBackward", selector, options });
          return Promise.resolve(true);
        },
        back(options) {
          calls.push({ method: "back", options });
          return Promise.resolve(true);
        },
        home(options) {
          calls.push({ method: "home", options });
          return Promise.resolve(true);
        },
        recentApps(options) {
          calls.push({ method: "recentApps", options });
          return Promise.resolve(true);
        }
      };
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
      function __autojs6_limited_app() { return moduleStubs.app; }
      function __autojs6_limited_device() { return moduleStubs.device; }
      function __autojs6_limited_shell() { return moduleStubs.shell; }
      function __autojs6_limited_image() { return moduleStubs.images; }
      function __autojs6_limited_accessibility() { return accessibilityStub; }
      ${compatBlock}
      return {
        compat: __autojs6_limited_rhino_compat(),
        globalObject,
        calls,
        accessibilityStub
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

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((check) => check.id === id);
  return item ? item.passed : false;
}

function throws(fn) {
  try {
    fn();
    return false;
  } catch (_) {
    return true;
  }
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
    "# Rhino Compat Selector Accessibility Verification",
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
      console.log("Usage: node verify-rhino-compat-selector-accessibility.js [--repo-root dir] [--report-dir dir]");
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
