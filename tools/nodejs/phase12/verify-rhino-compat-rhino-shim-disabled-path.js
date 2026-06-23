#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-rhino-shim-disabled-path-verification";

const FILES = {
  disabledReport: "docs/nodejs/reports/phase12/RHINO_SHIM_DISABLED_PATH.md",
  pocGate: "docs/nodejs/reports/phase12/RHINO_JSON_ONLY_SHIM_POC_GATE.md",
  jsonDesign: "docs/nodejs/design/RHINO_JSON_ONLY_SHIM.md",
  shimDesign: "docs/nodejs/design/RHINO_SHIM.md",
  loaderDesign: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  surfaceDocs: "docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  rhinoTypes: "docs/nodejs/types/autojs6-node/rhino.d.ts",
  compatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  indexTypes: "docs/nodejs/types/autojs6-node/index.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typeSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  nodeExamplesGradle: "app/node-examples.gradle.kts",
  instrumentationTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeRhinoShimDesignGateInstrumentationTest.kt",
  phase11Verifier: "tools/nodejs/phase11/verify-rhino-json-only-shim-design.js",
  phase12PocVerifier: "tools/nodejs/phase12/verify-rhino-compat-rhino-json-only-shim-poc-gate.js",
  gradle: "app/node-phase12-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files, repoRoot);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const report = {
    schema: "autojs6-node-rhino-compat-rhino-shim-disabled-path-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatRhinoShimDisabledPath",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      runtimeDisabledFacade: checkById(checks, "runtime-disabled-facade"),
      docsAndTypesConsistent: checkById(checks, "types-and-docs"),
      noExecutionAdapter: checkById(checks, "no-execution-or-globals"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino shim disabled path verification: " + report.decision.status);
  console.log("Rhino shim disabled path policy violations: " + policyViolations.length);
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

function buildChecks(files, repoRoot) {
  const surfaceManifest = JSON.parse(files.surfaceManifest.text);
  const rhinoSurface = (surfaceManifest.surfaces || []).find((surface) => surface.id === "rhino.json_only_shim");
  const runtimeFacade = extractBetween(
    files.runtime.text,
    "function __autojs6_limited_rhino()",
    "  const __autojs6_rhino_compat_error_codes",
  );
  const verifierSyntax = checkSyntax(path.join(repoRoot, "tools/nodejs/phase12/verify-rhino-compat-rhino-shim-disabled-path.js"));

  return [
    check(
      "disabled-report",
      includesAll(files.disabledReport.text, [
        "Status: P12-24 disabled/default denial path implemented",
        "`require(\"rhino\")` returns a frozen facade",
        "`require.resolve(\"rhino\")` returns `rhino`",
        "local `node_modules/rhino` package cannot shadow",
        "`require(\"node:rhino\")` remains unavailable",
        "`rhino.run()` returns a rejected Promise",
        "ERR_AUTOJS6_RHINO_SHIM_DISABLED",
        "`rhino.install()` throws",
        "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED",
        "does not add `compat.rhino`",
        "P12-25 remains responsible",
        "P12-26 remains responsible",
      ]),
      "P12-24 report must document the disabled facade, stable denial, shadowing, no globals, and follow-up boundaries.",
    ),
    check(
      "runtime-disabled-facade",
      includesAll(files.runtime.text, [
        "let __autojs6_limited_rhino_cache = null",
        "const __autojs6_rhino_shim_error_codes = Object.freeze",
        "shimDisabled: \"ERR_AUTOJS6_RHINO_SHIM_DISABLED\"",
        "installUnsupported: \"ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED\"",
        "function __autojs6_rhino_shim_disabled_error(apiName)",
        "function __autojs6_rhino_install_unsupported_error()",
        "function __autojs6_rhino_disabled_status()",
        "function __autojs6_limited_rhino()",
        "schema: \"autojs6-rhino-json-only-shim-disabled-v1\"",
        "mode: \"disabled\"",
        "enabled: false",
        "globalsInstalled: false",
        "nodeAlias: false",
        "shadowing: \"reserved\"",
        "return Promise.reject(__autojs6_rhino_shim_disabled_error(\"run\"));",
        "throw __autojs6_rhino_install_unsupported_error();",
        "Object.freeze({",
      ]) &&
        includesAll(runtimeFacade, [
          "name: \"rhino\"",
          "version: 1",
          "isEnabled: function()",
          "status: __autojs6_rhino_disabled_status",
          "run,",
          "install",
        ]),
      "Runtime must expose only a frozen disabled rhino facade with stable run/install denial.",
    ),
    check(
      "runtime-resolution",
      countOccurrences(files.runtime.text, "if (name === \"rhino\")") >= 3 &&
        includesAll(files.runtime.text, [
          "return __autojs6_limited_rhino();",
          "name === \"rhino\" ||",
          "function __autojs6_import_meta_builtin_url(name)",
          "require.resolve",
        ]) &&
        !includesAny(files.runtime.text, [
          "name === \"node:rhino\"",
          "\"node:rhino\":",
          "return __autojs6_limited_rhino(\"node:rhino\")",
        ]),
      "CommonJS and partial ESM must reserve bare rhino while keeping node:rhino unavailable.",
    ),
    check(
      "no-execution-or-globals",
      !includesAny(runtimeFacade, [
        "__autojs6_call_autojs",
        "NodeBridge",
        "ScriptRuntime",
        "RhinoEngine",
        "importClass",
        "importPackage",
        "Packages",
        "JavaAdapter",
        "__autojs6_define_global",
      ]) &&
        !includesAny(files.runtime.text, [
          "compat.rhino",
          "__autojs6_rhino_json_only_shim_adapter",
          "__autojs6_rhino_run_provider",
          "function __autojs6_rhino_run",
        ]) &&
        !includesAny(files.compatTypes.text, [
          "readonly rhino",
          "compat.rhino",
        ]),
      "P12-24 must not add Rhino execution, bridge dispatch, globals, or compat.rhino.",
    ),
    check(
      "types-and-docs",
      includesAll(files.rhinoTypes.text, [
        "declare module \"rhino\"",
        "ERR_AUTOJS6_RHINO_SHIM_DISABLED",
        "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED",
        "schema: \"autojs6-rhino-json-only-shim-disabled-v1\"",
        "mode: \"disabled\"",
        "enabled: false",
        "run(request?: unknown): Promise<never>",
        "install(options?: unknown): never",
        "status(): Status",
        "isEnabled(): false",
      ]) &&
        files.indexTypes.text.includes("/// <reference path=\"./rhino.d.ts\" />") &&
        includesAll(files.typesReadme.text, [
          "- `rhino`",
          "P12-24 reserves `require(\"rhino\")` as a disabled JSON-only shim facade",
          "ERR_AUTOJS6_RHINO_SHIM_DISABLED",
          "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED",
          "no Rhino globals or `compat.rhino`",
        ]) &&
        includesAll(files.typeSmoke.text, [
          "import rhino = require(\"rhino\")",
          "const rhinoEnabled: false = rhino.isEnabled()",
          "const rhinoStatus: rhino.Status = rhino.status()",
          "const rhinoRunDenied: Promise<never> = rhino.run",
          "const rhinoInstallDenied: (options?: unknown) => never = rhino.install",
        ]) &&
        files.nodeExamplesGradle.text.includes("\"rhino\"") &&
        files.nodeExamplesGradle.text.includes("typescript-smoke sample must import '$moduleName'") &&
        !files.compatTypes.text.includes("declare module \"rhino\"") &&
        !files.compatTypes.text.includes("compat.rhino"),
      "Type declarations and README must expose only the disabled rhino facade and no compat.rhino.",
    ),
    check(
      "design-migration-security-docs",
      includesAll(files.jsonDesign.text, [
        "P12-24 reserves a disabled runtime facade",
        "`require(\"rhino\")` resolves to a disabled facade",
        "`rhino.run()` rejects with `ERR_AUTOJS6_RHINO_SHIM_DISABLED`",
        "`rhino.install()` throws `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`",
        "local `node_modules/rhino`",
        "## P12-24 Disabled Facade",
      ]) &&
        includesAll(files.shimDesign.text, [
          "P12-24 disabled facade",
          "ERR_AUTOJS6_RHINO_SHIM_DISABLED",
          "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED",
          "`require(\"node:rhino\")`",
        ]) &&
        includesAll(files.loaderDesign.text, [
          "P12-24 reserves `require(\"rhino\")` as a disabled default-denial facade",
          "local `node_modules/rhino` cannot shadow it",
          "no `compat.rhino` or Rhino globals",
        ]) &&
        includesAll(files.migration.text, [
          "P12-24 reserves `require(\"rhino\")`",
          "`rhino.run()` rejects with `ERR_AUTOJS6_RHINO_SHIM_DISABLED`",
          "`rhino.install()` throws `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`",
        ]) &&
        includesAll(files.securityModel.text, [
          "P12-24 reserves `require(\"rhino\")` only as a disabled facade",
          "`rhino.run()` rejects with `ERR_AUTOJS6_RHINO_SHIM_DISABLED`",
          "local `node_modules/rhino` cannot shadow",
        ]),
      "Design, loader, migration, and security docs must agree on the P12-24 disabled/default denial path.",
    ),
    check(
      "p12-23-gate-updated",
      includesAll(files.pocGate.text, [
        "P12-24 follows by reserving",
        "runtime `require(\"rhino\")` returns only a frozen disabled facade",
        "runtime `rhino.run()` rejects with `ERR_AUTOJS6_RHINO_SHIM_DISABLED`",
        "runtime `rhino.install()` throws `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`",
        "TypeScript declares only the disabled `rhino` facade",
      ]) &&
        includesAll(files.phase12PocVerifier.text, [
          "disabled-runtime-facade",
          "ERR_AUTOJS6_RHINO_SHIM_DISABLED",
          "rhino.d.ts",
        ]) &&
        includesAll(files.phase11Verifier.text, [
          "runtime-rhino-disabled-facade",
          "type-rhino-disabled-facade",
          "ERR_AUTOJS6_RHINO_SHIM_DISABLED",
        ]),
      "P12-23 and S11 design verifiers must be updated to allow the P12-24 disabled facade while still rejecting execution.",
    ),
    check(
      "instrumentation-evidence",
      includesAll(files.instrumentationTest.text, [
        "class NodeRhinoShimDesignGateInstrumentationTest",
        "rhinoShimFacadeIsDisabledByDefault",
        "node_modules/rhino",
        "require(\"rhino\")",
        "require.resolve(\"rhino\")",
        "require(\"node:rhino\")",
        "ERR_AUTOJS6_RHINO_SHIM_DISABLED",
        "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED",
        "globalThis.importClass",
        "globalThis.Packages",
      ]),
      "Instrumentation evidence must cover facade denial, node:rhino denial, no globals, and node_modules shadowing.",
    ),
    check(
      "surface-manifest-p12-24",
      rhinoSurface &&
        rhinoSurface.status === "design_locked" &&
        (rhinoSurface.tasks || []).includes("P12-24") &&
        includesAll(JSON.stringify(rhinoSurface), [
          "P12-24 reserves require(\\\"rhino\\\") as a disabled facade",
          "ERR_AUTOJS6_RHINO_SHIM_DISABLED",
          "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED",
          "RHINO_SHIM_DISABLED_PATH.md",
          "docs/nodejs/types/autojs6-node/rhino.d.ts",
          "NodeRhinoShimDesignGateInstrumentationTest.kt",
          "verify-rhino-compat-rhino-shim-disabled-path.js",
          "verifyNodeRhinoCompatRhinoShimDisabledPath",
        ]) &&
        includesAll(files.surfaceDocs.text, [
          "P12-24 reserves `require(\"rhino\")` as a disabled facade",
          "ERR_AUTOJS6_RHINO_SHIM_DISABLED",
          "local `node_modules/rhino`",
        ]),
      "Phase 12 surface manifest must include P12-24 disabled facade evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-24/.test(files.phase12Tasks.text) &&
        includesAll(files.phase12Tasks.text, [
          "RHINO_SHIM_DISABLED_PATH.md",
          "ERR_AUTOJS6_RHINO_SHIM_DISABLED",
          "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED",
          "NodeRhinoShimDesignGateInstrumentationTest.kt",
          "verify-rhino-compat-rhino-shim-disabled-path.js",
          "verifyNodeRhinoCompatRhinoShimDisabledPath",
        ]),
      "Phase 12 task plan must mark P12-24 complete with runtime, type, test, report, and gate evidence.",
    ),
    check(
      "syntax-verifier",
      verifierSyntax.ok,
      `P12-24 verifier must pass node --check${verifierSyntax.ok ? "" : ": " + verifierSyntax.message}`,
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatRhinoShimDisabledPath") &&
        files.gradle.text.includes("verify-rhino-compat-rhino-shim-disabled-path.js") &&
        files.gradle.text.includes("RHINO_SHIM_DISABLED_PATH.md") &&
        files.gradle.text.includes("NodeRhinoShimDesignGateInstrumentationTest.kt") &&
        files.gradle.text.includes("verifyNodeRhinoCompatRhinoJsonOnlyShimPocGate"),
      "Gradle must register verifyNodeRhinoCompatRhinoShimDisabledPath with P12-24 inputs.",
    ),
  ];
}

function extractBetween(text, start, end) {
  const startIndex = text.indexOf(start);
  if (startIndex < 0) return "";
  const endIndex = text.indexOf(end, startIndex + start.length);
  return text.slice(startIndex, endIndex < 0 ? text.length : endIndex);
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

function countOccurrences(text, value) {
  let count = 0;
  let index = 0;
  while (index < text.length) {
    const next = text.indexOf(value, index);
    if (next < 0) break;
    count += 1;
    index = next + value.length;
  }
  return count;
}

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function includesAny(text, values) {
  return values.some((value) => text.includes(value));
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((checkItem) => checkItem.id === id);
  return item ? item.passed : false;
}

function read(file) {
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Shim Disabled Path Verification",
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

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-rhino-compat-rhino-shim-disabled-path.js [--repo-root dir] [--report-dir dir]");
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
