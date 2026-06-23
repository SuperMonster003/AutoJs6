#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-rhino-json-only-shim-poc-gate-verification";

const FILES = {
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
  phase11Verifier: "tools/nodejs/phase11/verify-rhino-json-only-shim-design.js",
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
    schema: "autojs6-node-rhino-compat-rhino-json-only-shim-poc-gate-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatRhinoJsonOnlyShimPocGate",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      pocGateEstablished: checkById(checks, "poc-gate-report"),
      defaultStillDisabled: checkById(checks, "disabled-runtime-facade"),
      surfaceManifestUpdated: checkById(checks, "surface-manifest-p12-23"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino JSON-only shim POC gate verification: " + report.decision.status);
  console.log("Rhino JSON-only shim POC gate policy violations: " + policyViolations.length);
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
  const verifierSyntax = checkSyntax(path.join(repoRoot, "tools/nodejs/phase12/verify-rhino-compat-rhino-json-only-shim-poc-gate.js"));

  return [
    check(
      "poc-gate-report",
      includesAll(files.pocGate.text, [
        "Status: P12-23 POC gate established",
        "It does not enable Rhino execution",
        "P12-24 follows by reserving",
        "disabled facade",
        "no executable `rhino.run()`",
        "no supported",
        "no `compat.rhino`",
        "no Rhino globals installed into V8",
        "const rhino = require(\"rhino\")",
        "rhino.run({",
        "source:",
        "path:",
        "P12-24 owns the default-disabled",
        "P12-25 now owns the execution adapter POC",
        "P12-26 owns",
      ]),
      "P12-23 report must establish the POC gate, preserve default non-exposure, and route follow-up tasks.",
    ),
    check(
      "poc-default-and-install-denial",
      includesAll(files.pocGate.text, [
        "Release/default Safe Node Profile must reject the shim",
        "explicit experimental flag or test metadata",
        "require(\"node:rhino\")",
        "node_modules/rhino",
        "cannot shadow",
        "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED",
        "`rhino.install()` is never part of the JSON-only POC",
      ]),
      "P12-23 report must lock default denial, experimental/test metadata only, node:rhino denial, shadowing decision, and install rejection.",
    ),
    check(
      "poc-json-validator",
      includesAll(files.pocGate.text, [
        "`undefined`",
        "function",
        "symbol",
        "`bigint`",
        "cycle",
        "prototype object or class instance",
        "Java object marker",
        "Rhino `Scriptable` marker",
        "Android object marker",
        "image/UI/accessibility handle marker",
        "thread/lock/process/native pointer/file descriptor marker",
        "oversized payload",
        "Returned values from Rhino must pass the same JSON-safe validator",
      ]),
      "P12-23 report must define the JSON validator denial corpus for input and output.",
    ),
    check(
      "poc-no-globals-and-boundary",
      includesAll(files.pocGate.text, [
        "The POC must not install or expose these V8 globals",
        "`importClass`",
        "`importPackage`",
        "`Packages`",
        "`java`",
        "`android`",
        "`JavaAdapter`",
        "old AutoJs globals",
        "timeoutMs",
        "exactly one of `source` or `path`",
        "script manager or a dedicated compatibility process",
        "stdout/stderr capture is bounded",
        "concurrent runs are bounded",
        "diagnostics record mixed-engine run count",
      ]),
      "P12-23 report must keep Rhino globals out of V8 and define execution-boundary gates.",
    ),
    check(
      "poc-packaged-gate",
      includesAll(files.pocGate.text, [
        "Packaged support remains fail-closed until P12-26",
        "APKs without explicit shim metadata cannot execute the shim",
        "Metadata may authorize only JSON-only `run`, not `install()` or globals",
        "Packaged timeout, crash, cancellation, and destroy behavior require evidence",
      ]),
      "P12-23 report must leave packaged support fail-closed until P12-26 metadata evidence.",
    ),
    check(
      "s11-design-updated",
      includesAll(files.jsonDesign.text, [
        "P12-23 establishes the Phase 12 POC",
        "P12-23 POC Gate",
        "RHINO_JSON_ONLY_SHIM_POC_GATE.md",
        "explicit experimental flag or test metadata",
        "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED",
        "no Rhino globals are installed into V8",
      ]) &&
        includesAll(files.shimDesign.text, [
          "P12-23 records the",
          "default-disabled Phase 12 POC gate",
          "RHINO_JSON_ONLY_SHIM_POC_GATE.md",
        ]) &&
        includesAll(files.phase11Verifier.text, [
          "verifyNodeRhinoJsonOnlyShimDesign",
          "runtime-rhino-disabled-facade",
          "type-rhino-disabled-facade",
        ]),
      "S11-26 design docs and existing design verifier must remain linked to the P12-23 POC gate.",
    ),
    check(
      "migration-security-loader-docs",
        includesAll(files.migration.text, [
          "P12-23 establishes the default-disabled POC gate",
          "RHINO_JSON_ONLY_SHIM_POC_GATE.md",
          "experimental: true",
          "metadata",
          "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED",
          "JSON-safe values",
        ]) &&
        includesAll(files.securityModel.text, [
          "P12-23 establishes a default-disabled POC gate",
          "explicit experimental flag or test metadata",
          "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED",
          "no Rhino globals may be installed into V8",
          "RHINO_JSON_ONLY_SHIM_POC_GATE.md",
        ]) &&
        includesAll(files.loaderDesign.text, [
          "P12-23 establishes the default-disabled POC gate",
          "P12-24 reserves `require(\"rhino\")` as a frozen disabled facade",
          "explicit experimental flag or test metadata",
          "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED",
          "S11-26/P12-23/P12-24",
          "local `node_modules/rhino` cannot shadow it",
        ]),
      "Migration, security, and loader docs must record the P12-23 default-disabled POC gate.",
    ),
    check(
      "disabled-runtime-facade",
      includesAll(files.runtime.text, [
        "function __autojs6_limited_rhino()",
        "ERR_AUTOJS6_RHINO_SHIM_DISABLED",
        "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED",
        "function __autojs6_rhino_run_request(request)",
        "if (input.experimental !== true)",
        "throw __autojs6_rhino_shim_disabled_error(\"run\");",
        "return Promise.reject(error);",
        "throw __autojs6_rhino_install_unsupported_error();",
        "name === \"rhino\"",
        "return __autojs6_limited_rhino();",
      ]) &&
        includesAll(files.rhinoTypes.text, [
          "declare module \"rhino\"",
          "mode: \"disabled\"",
          "enabled: false",
          "run<T extends JsonValue = JsonValue>(request: RunRequest): Promise<RunResult<T>>",
          "run(request?: unknown): Promise<never>",
          "install(options?: unknown): never",
        ]) &&
        !includesAny(files.compatTypes.text, [
          "readonly rhino",
          "compat.rhino",
        ]) &&
        includesAll(files.indexTypes.text, [
          "/// <reference path=\"./rhino.d.ts\" />",
        ]) &&
        !includesAny(files.runtime.text, [
          "name === \"node:rhino\"",
          "compat.rhino",
          "__autojs6_rhino_json_only_shim_adapter",
        ]),
      "P12-23/P12-24 must preserve the disabled default facade; P12-25 may add only the explicit rhino.run provider while node:rhino and compat.rhino remain absent.",
    ),
    check(
      "surface-manifest-p12-23",
      rhinoSurface &&
        (rhinoSurface.status === "design_locked" || rhinoSurface.status === "provider_poc") &&
        (rhinoSurface.tasks || []).includes("P12-23") &&
        includesAll(JSON.stringify(rhinoSurface), [
          "P12-23 establishes the default-disabled POC gate",
          "require(\\\"rhino\\\")",
          "rhino.run()",
          "rhino.install()",
          "compat.rhino",
          "P12-24 reserves require(\\\"rhino\\\") as a disabled facade",
          "ERR_AUTOJS6_RHINO_SHIM_DISABLED",
          "explicit experimental Rhino",
          "P12-25 adds",
          "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED",
          "RHINO_JSON_ONLY_SHIM_POC_GATE.md",
          "RHINO_SHIM_DISABLED_PATH.md",
          "RHINO_JSON_ONLY_EXECUTION_ADAPTER_POC.md",
          "verify-rhino-compat-rhino-json-only-shim-poc-gate.js",
          "verifyNodeRhinoCompatRhinoJsonOnlyShimPocGate",
        ]) &&
        includesAll(files.surfaceDocs.text, [
          "rhino.json_only_shim",
          "P12-23 establishes",
          "default-disabled POC gate",
          "P12-24 reserves",
          "P12-25 adds",
          "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED",
        ]),
      "Phase 12 surface manifest must record rhino.json_only_shim with P12-23 gate evidence and any later P12-25 provider evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-23/.test(files.phase12Tasks.text) &&
        includesAll(files.phase12Tasks.text, [
          "RHINO_JSON_ONLY_SHIM_POC_GATE.md",
          "release/default Safe Node Profile",
          "explicit experimental flag or test metadata",
          "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED",
          "function",
          "symbol",
          "bigint",
          "undefined",
          "cycle",
          "prototype object",
          "Java object marker",
          "No-globals gate",
          "verify-rhino-compat-rhino-json-only-shim-poc-gate.js",
          "verifyNodeRhinoCompatRhinoJsonOnlyShimPocGate",
        ]),
      "Phase 12 task plan must mark P12-23 complete with POC gate evidence.",
    ),
    check(
      "syntax-verifier",
      verifierSyntax.ok,
      `P12-23 verifier must pass node --check${verifierSyntax.ok ? "" : ": " + verifierSyntax.message}`,
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatRhinoJsonOnlyShimPocGate") &&
        files.gradle.text.includes("verify-rhino-compat-rhino-json-only-shim-poc-gate.js") &&
        files.gradle.text.includes("RHINO_JSON_ONLY_SHIM_POC_GATE.md") &&
        files.gradle.text.includes("verifyNodeRhinoCompatComputeProcessWorkerDecision"),
      "Gradle must register verifyNodeRhinoCompatRhinoJsonOnlyShimPocGate with P12-23 inputs.",
    ),
  ];
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
    "# Rhino JSON-Only Shim POC Gate Verification",
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
      console.log("Usage: node verify-rhino-compat-rhino-json-only-shim-poc-gate.js [--repo-root dir] [--report-dir dir]");
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
