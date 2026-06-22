#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const vm = require("vm");

const REPORT_BASENAME = "rhino-compat-pure-utilities-verification";
const PURE_MODULES = ["base64", "colors", "formatter", "converter", "mime", "nanoid"];
const GLOBAL_NAMES = ["base64", "colors", "fmt", "cvt", "mime", "nanoid", "util"];

const FILES = {
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  base64Types: "docs/nodejs/types/autojs6-node/base64.d.ts",
  colorsTypes: "docs/nodejs/types/autojs6-node/colors.d.ts",
  formatterTypes: "docs/nodejs/types/autojs6-node/formatter.d.ts",
  converterTypes: "docs/nodejs/types/autojs6-node/converter.d.ts",
  mimeTypes: "docs/nodejs/types/autojs6-node/mime.d.ts",
  nanoidTypes: "docs/nodejs/types/autojs6-node/nanoid.d.ts",
  utilTypes: "docs/nodejs/types/autojs6-node/util.d.ts",
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
  const pureBlock = extractPureUtilityBlock(files.runtime.text);
  const compatBlock = extractCompatBlock(files.runtime.text);
  const checks = [
    ...buildStaticChecks(files, pureBlock, compatBlock),
    ...buildDynamicChecks(pureBlock, compatBlock),
  ];
  const policyViolations = checks.filter((check) => !check.passed).map((check) => check.message);
  const report = {
    schema: "autojs6-node-rhino-compat-pure-utilities-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatPureUtilities",
    modules: PURE_MODULES,
    globals: GLOBAL_NAMES,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      runtimeFacadePresent: checkById(checks, "runtime-pure-facades"),
      dynamicBase64Works: checkById(checks, "dynamic-base64"),
      dynamicColorsWorks: checkById(checks, "dynamic-colors"),
      dynamicByteUtilitiesWork: checkById(checks, "dynamic-byte-utilities"),
      explicitGlobalInstallWorks: checkById(checks, "dynamic-explicit-global-pure-utils"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat pure utility verification: " + report.decision.status);
  console.log("Rhino compat pure utility modules: " + PURE_MODULES.join(", "));
  console.log("Rhino compat pure utility policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildStaticChecks(files, pureBlock, compatBlock) {
  const manifest = JSON.parse(files.statusManifest.text);
  const moduleById = new Map((manifest.modules || []).map((module) => [module.id, module]));
  const statusEntry = (moduleId, entryId) => {
    const module = moduleById.get(moduleId);
    const entry = module && (module.apiEntries || []).find((item) => item.id === entryId);
    return entry ? entry.status : "";
  };
  return [
    check("runtime-pure-facades", PURE_MODULES.every((name) => pureBlock.includes(`__autojs6_limited_${name === "formatter" ? "formatter" : name === "converter" ? "converter" : name}`)), "runtime must define S11-11 pure utility facade functions"),
    check("runtime-require-pure-modules", PURE_MODULES.every((name) => files.runtime.text.includes(`name === "${name}"`) || (name === "formatter" && files.runtime.text.includes('name === "fmt"')) || (name === "converter" && files.runtime.text.includes('name === "cvt"'))), "runtime must resolve and require S11-11 pure utility modules"),
    check("runtime-compat-pure-modules", GLOBAL_NAMES.every((name) => compatBlock.includes(`"${name}"`)) && compatBlock.includes("base64: __autojs6_limited_base64()") && compatBlock.includes("util: __autojs6_limited_util()"), "autojs6:compat must expose and install S11-11 pure utility globals"),
    check("runtime-status-s11-11", /implementedTasks:\s*Object\.freeze\(\[[^\]]*"S11-11"/.test(compatBlock) && !/pendingTasks:\s*Object\.freeze\(\[[^\]]*"S11-11"/.test(compatBlock), "compat status must mark S11-11 implemented and remove it from pending tasks"),
    check("types-base64", files.base64Types.text.includes('declare module "base64"') && files.base64Types.text.includes("encode(") && files.base64Types.text.includes("decode("), "base64.d.ts must declare encode/decode"),
    check("types-colors", files.colorsTypes.text.includes('declare module "colors"') && files.colorsTypes.text.includes("toHex(") && files.colorsTypes.text.includes("toColorStateList"), "colors.d.ts must declare pure and blocked colors APIs"),
    check("types-byte-utils", files.formatterTypes.text.includes('declare module "fmt"') && files.converterTypes.text.includes('declare module "cvt"') && files.formatterTypes.text.includes("strict(") && files.converterTypes.text.includes("loose("), "formatter/converter types must declare fmt/cvt aliases and byte helpers"),
    check("types-mime-nanoid-util", files.mimeTypes.text.includes('declare module "mime"') && files.nanoidTypes.text.includes('declare module "nanoid"') && files.utilTypes.text.includes('declare module "util"') && files.utilTypes.text.includes("inspect("), "mime/nanoid/util types must exist"),
    check("types-index-pure-utils", ["base64", "colors", "formatter", "converter", "mime", "nanoid", "util"].every((name) => files.indexTypes.text.includes(`./${name}.d.ts`)), "index.d.ts must reference S11-11 type declarations"),
    check("types-compat-pure-utils", GLOBAL_NAMES.every((name) => files.compatTypes.text.includes(`readonly ${name}:`) || name === "fmt" || name === "cvt") && files.compatTypes.text.includes("readonly fmt:") && files.compatTypes.text.includes("readonly cvt:"), "rhino_compat.d.ts must expose S11-11 pure utility properties"),
    check("status-base64", moduleById.get("base64") && moduleById.get("base64").status === "supported" && statusEntry("base64", "base64.encode") === "supported", "status manifest must mark base64 supported"),
    check("status-colors", moduleById.get("colors") && moduleById.get("colors").status === "partial" && statusEntry("colors", "colors.rgb") === "supported" && statusEntry("colors", "colors.toColorStateList") === "blocked_by_design", "status manifest must mark colors partial with Android object boundaries blocked"),
    check("status-byte-utils", moduleById.get("formatter.bytes") && moduleById.get("formatter.bytes").status === "supported" && moduleById.get("converter.bytes") && moduleById.get("converter.bytes").status === "supported", "status manifest must mark formatter.bytes and converter.bytes supported"),
    check("status-mime-nanoid-util", moduleById.get("mime") && moduleById.get("mime").status === "supported" && moduleById.get("nanoid") && moduleById.get("nanoid").status === "supported" && moduleById.get("util.inspect") && moduleById.get("util.inspect").status === "supported", "status manifest must mark mime, nanoid, and util.inspect supported"),
    check("type-readme-pure-utils", PURE_MODULES.every((name) => files.typeReadme.text.includes(`\`${name}\``)) && files.typeReadme.text.includes("`util`"), "type README must list S11-11 pure utility modules"),
    check("design-doc-s11-11", files.design.text.includes("S11-11") && files.design.text.includes("base64") && files.design.text.includes("util.inspect"), "design doc must document S11-11 pure utility facade and util.inspect migration"),
    check("matrix-s11-11", /\| \d+ \| base64 \| base64 \| supported \|/.test(files.matrix.text) && /\| \d+ \| formatter\.bytes \| bytes \| supported \|/.test(files.matrix.text) && /\| \d+ \| colors \| colors \| partial \|/.test(files.matrix.text), "compat matrix must reflect S11-11 supported pure utility modules"),
    check("task-plan-s11-11-complete", /\[x\]\s+S11-11/.test(files.taskPlan.text), "Phase 11 task plan must mark S11-11 complete"),
    check("gradle-task", files.gradle.text.includes("verifyNodeRhinoCompatPureUtilities") && files.gradle.text.includes("verify-rhino-compat-pure-utilities.js"), "Gradle must register verifyNodeRhinoCompatPureUtilities"),
  ];
}

function buildDynamicChecks(pureBlock, compatBlock) {
  if (!pureBlock || !compatBlock) {
    return [check("dynamic-blocks", false, "runtime pure utility or compat block could not be extracted")];
  }
  const harness = createHarness(pureBlock, compatBlock);
  const checks = [];
  checks.push(check("dynamic-no-default-pure-utils", !("base64" in harness.globalObject) && !("colors" in harness.globalObject), "require autojs6:compat must not install S11-11 globals by default"));
  checks.push(check("dynamic-status", harness.compat.status().implementedTasks.includes("S11-11") && harness.compat.status().availableGlobalNames.includes("base64"), "compat status must expose S11-11 and pure utility globals"));
  checks.push(check("dynamic-base64", harness.base64.encode("AutoJs6") === "QXV0b0pzNg==" && harness.base64.decode("QXV0b0pzNg==") === "AutoJs6", "base64 encode/decode must round-trip strings"));
  const color = harness.colors.rgb(1, 2, 3);
  checks.push(check("dynamic-colors", harness.colors.red(color) === 1 && harness.colors.green(color) === 2 && harness.colors.blue(color) === 3 && harness.colors.toHex(color) === "#010203", "colors facade must expose RGB components and hex conversion"));
  checks.push(check("dynamic-colors-blocked", throwsCode(() => harness.colors.toColorStateList(color), "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN"), "colors.toColorStateList must be blocked by design"));
  checks.push(check("dynamic-byte-utilities", harness.formatter.bytes(1536) === "1.50 KB" && harness.converter.bytes("1 KB", "B") === 1024 && harness.formatter.bytes.strict(1024, "B", "KiB") === "1.00 KiB", "formatter.bytes and converter.bytes must run in host gate"));
  const parsedMime = harness.mime("text/plain; charset=utf-8");
  checks.push(check("dynamic-mime", parsedMime.type === "text" && parsedMime.subtype === "plain" && parsedMime.parameters.charset === "utf-8", "mime facade must parse type, subtype, and parameters"));
  const id = harness.nanoid();
  checks.push(check("dynamic-nanoid", typeof id === "string" && id.length === 21 && /^[A-Za-z0-9_-]+$/.test(id), "nanoid facade must generate URL-safe IDs"));
  checks.push(check("dynamic-util-inspect", harness.compat.util.inspect({ a: 1 }).includes("a"), "compat.util.inspect must be available for Rhino util.inspect migration"));
  const install = harness.compat.install({ globals: true });
  checks.push(check("dynamic-explicit-global-pure-utils", GLOBAL_NAMES.every((name) => install.globals.includes(name)) && harness.globalObject.base64 === harness.base64 && harness.globalObject.fmt === harness.formatter, "compat.install({ globals: true }) must explicitly install S11-11 pure utility globals"));
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
      let __autojs6_limited_mime_cache = null;
      let __autojs6_limited_nanoid_cache = null;
      let __autojs6_limited_rhino_compat_cache = null;
      let __autojs6_rhino_compat_facade_cache = null;
      const filesStub = Object.freeze({ path() { return null; } });
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
        base64: __autojs6_limited_base64(),
        colors: __autojs6_limited_colors(),
        formatter: __autojs6_limited_formatter(),
        converter: __autojs6_limited_converter(),
        mime: __autojs6_limited_mime(),
        nanoid: __autojs6_limited_nanoid(),
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
    "# Rhino Compat Pure Utilities Verification",
    "",
    `- Decision: ${report.decision.status}`,
    `- Modules: ${report.modules.map((name) => `\`${name}\``).join(", ")}`,
    `- Globals: ${report.globals.map((name) => `\`${name}\``).join(", ")}`,
    `- Checks: ${report.checks.length}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Decisions",
    "",
    `- Runtime facade present: ${report.decision.runtimeFacadePresent}`,
    `- Dynamic base64 works: ${report.decision.dynamicBase64Works}`,
    `- Dynamic colors works: ${report.decision.dynamicColorsWorks}`,
    `- Dynamic byte utilities work: ${report.decision.dynamicByteUtilitiesWork}`,
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
      console.log("Usage: node verify-rhino-compat-pure-utilities.js [--repo-root dir] [--report-dir dir]");
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
