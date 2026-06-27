#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-rhino-json-only-execution-adapter-poc-verification";

const FILES = {
  pocReport: "docs/nodejs/reports/phase12/RHINO_JSON_ONLY_EXECUTION_ADAPTER_POC.md",
  packagedReport: "docs/nodejs/reports/phase12/RHINO_SHIM_PACKAGED_METADATA_GATE.md",
  pocGate: "docs/nodejs/reports/phase12/RHINO_JSON_ONLY_SHIM_POC_GATE.md",
  disabledReport: "docs/nodejs/reports/phase12/RHINO_SHIM_DISABLED_PATH.md",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  surfaceDocs: "docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  jsonDesign: "docs/nodejs/design/RHINO_JSON_ONLY_SHIM.md",
  shimDesign: "docs/nodejs/design/RHINO_SHIM.md",
  loaderDesign: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  rhinoService: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeRhinoExecutionService.kt",
  bridgeProtocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  errorCodes: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeErrorCodes.kt",
  androidManifest: "app/src/main/AndroidManifest.xml",
  instrumentationTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeRhinoShimDesignGateInstrumentationTest.kt",
  rhinoTypes: "docs/nodejs/types/autojs6-node/rhino.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typeSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
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
    schema: "autojs6-node-rhino-compat-rhino-json-only-execution-adapter-poc-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatRhinoJsonOnlyExecutionAdapterPoc",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      adapterImplemented: checkById(checks, "android-rhino-provider"),
      defaultStillDisabled: checkById(checks, "default-disabled-gate"),
      jsonBoundary: checkById(checks, "json-only-boundary"),
      packagedGate: checkById(checks, "packaged-boundary"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino JSON-only execution adapter POC verification: " + report.decision.status);
  console.log("Rhino JSON-only execution adapter POC policy violations: " + policyViolations.length);
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
  const verifierSyntax = checkSyntax(
    path.join(repoRoot, "tools/nodejs/phase12/verify-rhino-compat-rhino-json-only-execution-adapter-poc.js"),
  );

  return [
    check(
      "p12-25-report",
      includesAll(files.pocReport.text, [
        "Status: P12-25 execution adapter POC implemented behind an explicit gate",
        "experimental: true",
        "Calls without",
        "ERR_AUTOJS6_RHINO_SHIM_DISABLED",
        "NodeBridgeRhinoExecutionService",
        "RhinoJavaScriptEngine",
        "`JSON.stringify()` inside the",
        "ERR_AUTOJS6_RHINO_EXECUTION_TIMEOUT",
        "ERR_AUTOJS6_RHINO_JSON_UNSAFE_RESULT",
        "maximum 32 lines",
        "maximum 8192 UTF-8 bytes",
        "P12-26 records packaged metadata behavior as fail-closed",
        "packaged APK execution remains rejected",
        "verifyNodeRhinoCompatRhinoJsonOnlyExecutionAdapterPoc",
      ]),
      "P12-25 report must document the explicit gate, provider path, timeout, cleanup, console, JSON-only result, and packaged boundary.",
    ),
    check(
      "default-disabled-gate",
      includesAll(files.runtime.text, [
        "function __autojs6_limited_rhino()",
        "function __autojs6_rhino_run_request(request)",
        "if (input.experimental !== true)",
        "throw __autojs6_rhino_shim_disabled_error(\"run\");",
        "return Promise.reject(error);",
        "mode: \"disabled\"",
        "enabled: false",
        "throw __autojs6_rhino_install_unsupported_error();",
      ]) &&
        includesAll(files.instrumentationTest.text, [
          "rhinoShimFacadeIsDisabledByDefault",
          "rhino.run({ source: \"1 + 1\", timeoutMs: 1000, args: [] })",
          "ERR_AUTOJS6_RHINO_SHIM_DISABLED",
          "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED",
          "require(\"node:rhino\")",
        ]),
      "Runtime and instrumentation must preserve the P12-24 default-disabled denial path.",
    ),
    check(
      "runtime-node-facade",
      includesAll(files.runtime.text, [
        "__autojs6_rhino_run_timeout_ms",
        "__autojs6_rhino_run_args",
        "__autojs6_rhino_relative_text",
        "__autojs6_rhino_deep_freeze_json",
        "__autojs6_rhino_run_result",
        "__autojs6_call_autojs(",
        "\"rhino\"",
        "\"run\"",
        "permissions: [\"rhino\"]",
        "signal: __autojs6_bridge_signal_from_options(request)",
        "provider: \"queued-host\"",
        "provider-unavailable",
      ]),
      "Node facade must validate the POC request, dispatch through rhino.run with the rhino permission, normalize/freeze results, and keep queued-host execution unavailable.",
    ),
    check(
      "android-rhino-provider",
      includesAll(files.rhinoService.text, [
        "class NodeBridgeRhinoExecutionService : Service()",
        "NodeBridgeModules.RHINO",
        "RhinoJavaScriptEngine(applicationContext)",
        "future.get(descriptor.timeoutMs, TimeUnit.MILLISECONDS)",
        "TimeoutException",
        "engine?.forceStop()",
        "engine.destroy()",
        "RhinoConsoleCapture",
        "MAX_CONSOLE_LINES = 32",
        "MAX_CONSOLE_BYTES = 8 * 1024",
        "jsonSafeResult",
        "JSON.stringify($RESULT_PROPERTY)",
        "Wrapper",
        "Function",
        "NodeRuntimeErrorCodes.RHINO_EXECUTION_ERROR",
        "NodeRuntimeErrorCodes.RHINO_EXECUTION_TIMEOUT",
        "NodeRuntimeErrorCodes.RHINO_JSON_UNSAFE_RESULT",
      ]),
      "Android service must create/destroy a Rhino engine, enforce timeout, capture bounded console, and return stable JSON errors.",
    ),
    check(
      "json-only-boundary",
      includesAll(files.rhinoService.text, [
        "MAX_SOURCE_BYTES = 64 * 1024",
        "MAX_ARGS_BYTES = 16 * 1024",
        "MAX_RESULT_BYTES = 64 * 1024",
        "parseJsonValue(valueJson)",
        "result cannot be represented as JSON",
        "result cannot be a function, Java object, Android object, or Rhino wrapper",
        "resolveScopedCwd",
        "resolveScopedFile",
        "hasParentSegment",
        "isInsideOrSame",
      ]) &&
        includesAll(files.runtime.text, [
          "rhino.run requires exactly one of source or path",
          "rhino.run args must be a JSON array",
          "rhino.run source exceeds 65536 bytes",
          "rhino.run args exceed 16384 bytes",
        ]),
      "Provider and Node facade must enforce scoped source/path and JSON-only input/output limits.",
    ),
    check(
      "bridge-and-permission",
      includesAll(files.bridgeProtocol.text, [
        "const val RHINO = \"rhino\"",
        "val rhinoMethods = setOf(\"run\")",
        "RHINO to rhinoMethods",
        "dispatchRhino(request, auditedCallback)",
        "NodeBridgeRhinoExecutionService.dispatch(appContext, request, callback)",
      ]) &&
        includesAll(files.permissionManifest.text, [
          "const val RHINO = \"rhino\"",
          "\"rhino.run\" to RHINO",
          "NodeBridgeModules.RHINO -> listOf(RHINO)",
        ]) &&
        includesAll(files.errorCodes.text, [
          "RHINO_INVALID_REQUEST",
          "RHINO_EXECUTION_ERROR",
          "RHINO_EXECUTION_TIMEOUT",
          "RHINO_JSON_UNSAFE_RESULT",
        ]) &&
        includesAll(files.androidManifest.text, [
          "NodeBridgeRhinoExecutionService",
          "android:exported=\"false\"",
        ]),
      "Bridge protocol, permissions, errors, and Android service registration must expose only rhino.run behind the controlled rhino capability.",
    ),
    check(
      "instrumentation-poc",
      includesAll(files.instrumentationTest.text, [
        "rhinoJsonOnlyPocRunsBehindExplicitExperimentalRequest",
        "experimental: true",
        "legacy.rhino.js",
        "rhino.poc.inline=5",
        "rhino.poc.path=ok",
        "ERR_AUTOJS6_RHINO_EXECUTION_ERROR",
        "ERR_AUTOJS6_RHINO_JSON_UNSAFE_RESULT",
        "ERR_AUTOJS6_RHINO_EXECUTION_TIMEOUT",
        "lifecycle.destroyed",
        "forceStopRequested",
      ]),
      "Instrumentation evidence must cover source, path, exception, unsafe result, timeout, and cleanup lifecycle.",
    ),
    check(
      "types-and-smoke",
      includesAll(files.rhinoTypes.text, [
        "export interface RunBaseRequest",
        "readonly experimental: true",
        "export interface RunSourceRequest",
        "export interface RunPathRequest",
        "export type RunResult",
        "autojs6-rhino-json-only-run-result-v1",
        "run<T extends JsonValue = JsonValue>(request: RunRequest): Promise<RunResult<T>>",
        "run(request?: unknown): Promise<never>",
        "install(options?: unknown): never",
        "ERR_AUTOJS6_RHINO_JSON_UNSAFE_RESULT",
      ]) &&
        includesAll(files.typesReadme.text, [
          "P12-25 adds an explicit",
          "experimental: true",
          "RunResult",
          "never installs Rhino globals",
        ]) &&
        includesAll(files.typeSmoke.text, [
          "const rhinoRunDenied: Promise<never> = rhino.run",
          "const rhinoRunPoc: Promise<rhino.RunResult",
          "experimental: true",
          "void rhinoRunPoc",
        ]),
      "Type declarations and smoke sample must expose the typed POC run while preserving the disabled overload.",
    ),
    check(
      "docs-updated",
      includesAll(files.jsonDesign.text, [
        "P12-25 implements",
        "experimental: true",
        "RHINO_JSON_ONLY_EXECUTION_ADAPTER_POC.md",
      ]) &&
        includesAll(files.shimDesign.text, [
          "P12-25",
          "experimental: true",
          "JSON-only execution adapter POC",
        ]) &&
        includesAll(files.loaderDesign.text, [
          "P12-25",
          "explicit `experimental: true`",
          "no `compat.rhino`",
        ]) &&
        includesAll(files.migration.text, [
          "P12-25 implements",
          "experimental: true",
          "args",
          "RHINO_JSON_ONLY_EXECUTION_ADAPTER_POC.md",
        ]) &&
        includesAll(files.securityModel.text, [
          "P12-25 implements",
          "experimental: true",
          "NodeBridgeRhinoExecutionService",
          "P12-26",
        ]),
      "Design, loader, migration, and security docs must record the P12-25 explicit POC without promoting packaged support.",
    ),
    check(
      "surface-manifest",
      rhinoSurface &&
        rhinoSurface.status === "provider_poc" &&
        (rhinoSurface.tasks || []).includes("P12-25") &&
        includesAll(JSON.stringify(rhinoSurface), [
          "P12-25 adds the explicit experimental Rhino JSON-only execution adapter POC",
          "rhino.run({ experimental: true, source, timeoutMs, args })",
          "P12-26 records packaged metadata behavior as fail-closed",
          "RHINO_JSON_ONLY_EXECUTION_ADAPTER_POC.md",
          "RHINO_SHIM_PACKAGED_METADATA_GATE.md",
          "NodeBridgeRhinoExecutionService.kt",
          "verify-rhino-compat-rhino-json-only-execution-adapter-poc.js",
          "verify-rhino-compat-rhino-shim-packaged-metadata-gate.js",
          "verifyNodeRhinoCompatRhinoJsonOnlyExecutionAdapterPoc",
        ]) &&
        includesAll(files.surfaceDocs.text, [
          "provider POC",
          "P12-25 adds",
          "experimental: true",
          "P12-26 records packaged metadata behavior as fail-closed",
        ]),
      "Phase 12 surface manifest must advance rhino.json_only_shim to provider_poc with P12-25 evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-25/.test(files.phase12Tasks.text) &&
        includesAll(files.phase12Tasks.text, [
          "RHINO_JSON_ONLY_EXECUTION_ADAPTER_POC.md",
          "NodeBridgeRhinoExecutionService.kt",
          "ERR_AUTOJS6_RHINO_EXECUTION_TIMEOUT",
          "ERR_AUTOJS6_RHINO_JSON_UNSAFE_RESULT",
          "verify-rhino-compat-rhino-json-only-execution-adapter-poc.js",
          "verifyNodeRhinoCompatRhinoJsonOnlyExecutionAdapterPoc",
        ]),
      "Phase 12 task plan must mark P12-25 complete with runtime, provider, test, report, and gate evidence.",
    ),
    check(
      "packaged-boundary",
      includesAll(files.pocReport.text, [
        "P12-26 records packaged metadata behavior as fail-closed",
        "No packaged metadata shape is accepted in Phase 12",
        "packaged APK execution remains rejected",
      ]) &&
        includesAll(files.packagedReport.text, [
          "Status: P12-26 packaged metadata gate recorded as fail-closed",
          "packagedSupport.status = unsupported",
          "metadata may authorize only JSON-only run",
          "metadata never authorizes install or globals",
          "therefore packaged execution remains rejected",
      ]) &&
        /\[x\]\s+P12-26/.test(files.phase12Tasks.text),
      "P12-25 must not claim packaged metadata support; P12-26 must record fail-closed packaged rejection.",
    ),
    check(
      "syntax-verifier",
      verifierSyntax.ok,
      `P12-25 verifier must pass node --check${verifierSyntax.ok ? "" : ": " + verifierSyntax.message}`,
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatRhinoJsonOnlyExecutionAdapterPoc") &&
        files.gradle.text.includes("verify-rhino-compat-rhino-json-only-execution-adapter-poc.js") &&
        files.gradle.text.includes("RHINO_JSON_ONLY_EXECUTION_ADAPTER_POC.md") &&
        files.gradle.text.includes("NodeBridgeRhinoExecutionService.kt") &&
        files.gradle.text.includes("verifyNodeRhinoCompatRhinoShimDisabledPath"),
      "Gradle must register verifyNodeRhinoCompatRhinoJsonOnlyExecutionAdapterPoc after the P12-24 disabled path gate.",
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

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((checkItem) => checkItem.id === id);
  return item ? item.passed : false;
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino JSON-Only Execution Adapter POC Verification",
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

function read(file) {
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-rhino-compat-rhino-json-only-execution-adapter-poc.js [--repo-root dir] [--report-dir dir]");
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
