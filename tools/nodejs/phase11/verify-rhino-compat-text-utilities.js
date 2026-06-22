#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const vm = require("vm");

const REPORT_BASENAME = "rhino-compat-text-utilities-verification";
const TEXT_MODULES = ["opencc", "pinyin", "pinyin4j"];
const RHINO_TYPE_GATE_MODULES = [
  "files",
  "base64",
  "colors",
  "formatter",
  "converter",
  "mime",
  "nanoid",
  "util",
  ...TEXT_MODULES,
];

const FILES = {
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  nodeExamplesGradle: "app/node-examples.gradle.kts",
  typeSmokeSource: "sample/nodejs/typescript-smoke/src/main.ts",
  openccTypes: "docs/nodejs/types/autojs6-node/opencc.d.ts",
  pinyinTypes: "docs/nodejs/types/autojs6-node/pinyin.d.ts",
  pinyin4jTypes: "docs/nodejs/types/autojs6-node/pinyin4j.d.ts",
  compatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  indexTypes: "docs/nodejs/types/autojs6-node/index.d.ts",
  typeReadme: "docs/nodejs/types/autojs6-node/README.md",
  design: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
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
  const textBlock = extractTextUtilityBlock(files.runtime.text);
  const compatBlock = extractCompatBlock(files.runtime.text);
  const checks = [
    ...buildStaticChecks(files, textBlock, compatBlock),
    ...buildDynamicChecks(textBlock, compatBlock),
  ];
  const policyViolations = checks.filter((check) => !check.passed).map((check) => check.message);
  const report = {
    schema: "autojs6-node-rhino-compat-text-utilities-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatTextUtilities",
    modules: TEXT_MODULES,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      runtimeFacadePresent: checkById(checks, "runtime-text-facades"),
      pinyinFixturesWork: checkById(checks, "dynamic-pinyin-fixtures"),
      pinyin4jFixturesWork: checkById(checks, "dynamic-pinyin4j-fixtures"),
      openccUnsupportedStable: checkById(checks, "dynamic-opencc-unsupported"),
      explicitGlobalInstallWorks: checkById(checks, "dynamic-explicit-global-text-utils"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat text utility verification: " + report.decision.status);
  console.log("Rhino compat text utility modules: " + TEXT_MODULES.join(", "));
  console.log("Rhino compat text utility policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildStaticChecks(files, textBlock, compatBlock) {
  const manifest = JSON.parse(files.statusManifest.text);
  const moduleById = new Map((manifest.modules || []).map((module) => [module.id, module]));
  const statusEntry = (moduleId, entryId) => {
    const module = moduleById.get(moduleId);
    const entry = module && (module.apiEntries || []).find((item) => item.id === entryId);
    return entry ? entry.status : "";
  };
  return [
    check("runtime-text-facades", TEXT_MODULES.every((name) => textBlock.includes(`__autojs6_limited_${name}`)), "runtime must define S11-12 text utility facade functions"),
    check("runtime-require-text-modules", TEXT_MODULES.every((name) => files.runtime.text.includes(`name === "${name}"`)), "runtime must resolve and require S11-12 text utility modules"),
    check("runtime-compat-text-modules", TEXT_MODULES.every((name) => compatBlock.includes(`"${name}"`)) && compatBlock.includes("opencc: __autojs6_limited_opencc()"), "autojs6:compat must expose and install S11-12 text utility globals"),
    check("runtime-status-s11-12", /implementedTasks:\s*Object\.freeze\(\[[^\]]*"S11-12"/.test(compatBlock) && !/pendingTasks:\s*Object\.freeze\(\[[^\]]*"S11-12"/.test(compatBlock), "compat status must mark S11-12 implemented and remove it from pending tasks"),
    check("types-text-utils", files.openccTypes.text.includes('declare module "opencc"') && files.pinyinTypes.text.includes('declare module "pinyin"') && files.pinyin4jTypes.text.includes('declare module "pinyin4j"'), "text utility type declarations must exist"),
    check("types-index-text-utils", TEXT_MODULES.every((name) => files.indexTypes.text.includes(`./${name}.d.ts`)), "index.d.ts must reference S11-12 type declarations"),
    check("types-compat-text-utils", TEXT_MODULES.every((name) => files.compatTypes.text.includes(`readonly ${name}:`)), "rhino_compat.d.ts must expose S11-12 text utility properties"),
    check("type-gate-rhino-facades", RHINO_TYPE_GATE_MODULES.every((name) => files.nodeExamplesGradle.text.includes(`"${name}"`)), "verifyAutoJs6NodeTypeDeclarations must list Rhino compat facade declarations through S11-12"),
    check("type-smoke-rhino-facades", RHINO_TYPE_GATE_MODULES.every((name) => files.typeSmokeSource.text.includes(`require("${name}")`)), "typescript-smoke sample must import Rhino compat facade modules through S11-12"),
    check("status-opencc", moduleById.get("opencc") && moduleById.get("opencc").status === "unsupported" && statusEntry("opencc", "opencc.s2t") === "unsupported", "status manifest must keep OpenCC resource-backed APIs unsupported with a Node facade"),
    check("status-pinyin", moduleById.get("pinyin") && moduleById.get("pinyin").status === "partial" && statusEntry("pinyin", "pinyin.simple") === "supported" && statusEntry("pinyin", "pinyin.fromPhrase") === "unsupported", "status manifest must mark pinyin as partial with resource-backed APIs unsupported"),
    check("status-pinyin4j", moduleById.get("pinyin4j") && moduleById.get("pinyin4j").status === "supported" && statusEntry("pinyin4j", "pinyin4j.of") === "supported", "status manifest must mark pinyin4j.of supported"),
    check("type-readme-text-utils", TEXT_MODULES.every((name) => files.typeReadme.text.includes(`\`${name}\``)), "type README must list S11-12 text utility modules"),
    check("design-doc-s11-12", files.design.text.includes("S11-12") && files.design.text.includes("pinyin4j") && files.design.text.includes("ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED"), "design doc must document S11-12 text utility facade and stable unsupported degradation"),
    check("matrix-s11-12", /\| \d+ \| opencc \| opencc \| unsupported \|/.test(files.matrix.text) && /\| \d+ \| pinyin \| pinyin \| partial \|/.test(files.matrix.text) && /\| \d+ \| pinyin4j \| pinyin4j \| supported \|/.test(files.matrix.text), "compat matrix must reflect S11-12 text utility modules"),
    check("task-plan-s11-12-complete", /\[x\]\s+S11-12/.test(files.taskPlan.text), "Phase 11 task plan must mark S11-12 complete"),
    check("gradle-task", files.gradle.text.includes("verifyNodeRhinoCompatTextUtilities") && files.gradle.text.includes("verify-rhino-compat-text-utilities.js"), "Gradle must register verifyNodeRhinoCompatTextUtilities"),
  ];
}

function buildDynamicChecks(textBlock, compatBlock) {
  if (!textBlock || !compatBlock) {
    return [check("dynamic-blocks", false, "runtime text utility or compat block could not be extracted")];
  }
  const harness = createHarness(textBlock, compatBlock);
  const checks = [];
  checks.push(check("dynamic-no-default-text-utils", !("pinyin" in harness.globalObject) && !("opencc" in harness.globalObject), "require autojs6:compat must not install S11-12 globals by default"));
  checks.push(check("dynamic-status", harness.compat.status().implementedTasks.includes("S11-12") && harness.compat.status().availableGlobalNames.includes("pinyin"), "compat status must expose S11-12 and text utility globals"));
  const converted = harness.pinyin.convert("\u4f60\u597d", { style: harness.pinyin.STYLE_TONE2 });
  checks.push(check("dynamic-pinyin-fixtures", harness.pinyin.simple("\u4f60\u597d") === "nihao" && converted[0][0] === "ni3" && converted[1][0] === "hao3" && converted.compact() === "ni3hao3", "pinyin facade must convert fixture Chinese text and compact results"));
  checks.push(check("dynamic-pinyin-resource-unsupported", throwsCode(() => harness.pinyin.convert("\u4f60\u597d", { segment: true }), "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED") && throwsCode(() => harness.pinyin.fromPhrase("\u672a\u77e5\u8bcd"), "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED"), "pinyin resource-backed paths must return stable unsupported errors"));
  checks.push(check("dynamic-pinyin4j-fixtures", harness.pinyin4j.of("\u4e2d\u56fd", { separator: " ", tone: "WITH_TONE_NUMBER" }) === "zhong1 guo2" && harness.pinyin4j.as("zhong1 guo2") === "zh\u014dng gu\u00f3", "pinyin4j facade must format fixture Chinese text and convert numeric tones"));
  checks.push(check("dynamic-opencc-unsupported", throwsCode(() => harness.opencc.s2t("\u6c49\u5b57"), "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED"), "opencc dictionary conversion must return stable unsupported errors without resources"));
  const install = harness.compat.install({ globals: true });
  checks.push(check("dynamic-explicit-global-text-utils", TEXT_MODULES.every((name) => install.globals.includes(name)) && harness.globalObject.pinyin === harness.pinyin && harness.globalObject.opencc === harness.opencc, "compat.install({ globals: true }) must explicitly install S11-12 text utility globals"));
  return checks;
}

function createHarness(textBlock, compatBlock) {
  const source = `
    (function() {
      let __autojs6_limited_opencc_cache = null;
      let __autojs6_limited_pinyin_cache = null;
      let __autojs6_limited_pinyin4j_cache = null;
      let __autojs6_limited_rhino_compat_cache = null;
      let __autojs6_rhino_compat_facade_cache = null;
      const __moduleStubs = {
        files: Object.freeze({ path() { return null; } }),
        base64: Object.freeze({ encode() { return ""; }, decode() { return ""; } }),
        colors: Object.freeze({ rgb() { return 0; } }),
        formatter: Object.freeze({ bytes() { return ""; } }),
        converter: Object.freeze({ bytes() { return 0; } }),
        mime: Object.freeze(function mime() { return Object.freeze({ type: "", subtype: "", parameters: Object.freeze({}) }); }),
        nanoid: Object.freeze(function nanoid() { return ""; }),
        util: Object.freeze({ format(value) { return String(value); }, inspect(value) { return String(value); } }),
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
        })
      };
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
      function __autojs6_limited_files() { return __moduleStubs.files; }
      function __autojs6_limited_base64() { return __moduleStubs.base64; }
      function __autojs6_limited_colors() { return __moduleStubs.colors; }
      function __autojs6_limited_formatter() { return __moduleStubs.formatter; }
      function __autojs6_limited_converter() { return __moduleStubs.converter; }
      function __autojs6_limited_mime() { return __moduleStubs.mime; }
      function __autojs6_limited_nanoid() { return __moduleStubs.nanoid; }
      function __autojs6_limited_util() { return __moduleStubs.util; }
      function __autojs6_limited_storage(moduleName) { return moduleName === "storages" ? __moduleStubs.storages : __moduleStubs.storage; }
      function __autojs6_limited_database(moduleName) { return moduleName === "sqlite" ? __moduleStubs.sqlite : __moduleStubs.database; }
      function __autojs6_limited_console() { return __moduleStubs.console; }
      function __autojs6_limited_timers() { return __moduleStubs.timers; }
      function __autojs6_limited_app() { return __moduleStubs.app; }
      function __autojs6_limited_device() { return __moduleStubs.device; }
      function __autojs6_limited_shell() { return __moduleStubs.shell; }
      function __autojs6_limited_toast() { return { showToast() { return Promise.resolve(); } }; }
      function __autojs6_limited_clipboard() { return { setText() { return Promise.resolve(); }, getText() { return Promise.resolve(""); } }; }
      function __autojs6_define_global(name, value) {
        Object.defineProperty(globalThis, name, { value, configurable: true, enumerable: false, writable: false });
      }
      ${textBlock}
      ${compatBlock}
      return {
        opencc: __autojs6_limited_opencc(),
        pinyin: __autojs6_limited_pinyin(),
        pinyin4j: __autojs6_limited_pinyin4j(),
        compat: __autojs6_limited_rhino_compat(),
        globalObject: globalThis
      };
    })()
  `;
  return vm.runInNewContext(source, {
    Array,
    Error,
    Math,
    Number,
    Object,
    Promise,
    RegExp,
    Set,
    String,
    console: { log() {} },
    globalThis: {},
    setTimeout,
  });
}

function extractTextUtilityBlock(runtime) {
  const start = runtime.indexOf("const __autojs6_opencc_methods");
  const end = runtime.indexOf("  const __autojs6_rhino_compat_error_codes", start);
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
    "# Rhino Compat Text Utilities Verification",
    "",
    `- Decision: ${report.decision.status}`,
    `- Modules: ${report.modules.map((name) => `\`${name}\``).join(", ")}`,
    `- Checks: ${report.checks.length}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Decisions",
    "",
    `- Runtime facade present: ${report.decision.runtimeFacadePresent}`,
    `- Pinyin fixtures work: ${report.decision.pinyinFixturesWork}`,
    `- Pinyin4j fixtures work: ${report.decision.pinyin4jFixturesWork}`,
    `- OpenCC unsupported stable: ${report.decision.openccUnsupportedStable}`,
    `- Explicit global install works: ${report.decision.explicitGlobalInstallWorks}`,
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
      console.log("Usage: node verify-rhino-compat-text-utilities.js [--repo-root dir] [--report-dir dir]");
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
