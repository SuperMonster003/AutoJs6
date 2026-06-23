#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-rhino-shim-packaged-metadata-gate-verification";

const FILES = {
  packagedReport: "docs/nodejs/reports/phase12/RHINO_SHIM_PACKAGED_METADATA_GATE.md",
  pocReport: "docs/nodejs/reports/phase12/RHINO_JSON_ONLY_EXECUTION_ADAPTER_POC.md",
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
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  executionModePolicy: "app/src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  rhinoService: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeRhinoExecutionService.kt",
  bridgeProtocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  instrumentationTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeRhinoShimDesignGateInstrumentationTest.kt",
  rhinoTypes: "docs/nodejs/types/autojs6-node/rhino.d.ts",
  p12_25Verifier: "tools/nodejs/phase12/verify-rhino-compat-rhino-json-only-execution-adapter-poc.js",
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
    schema: "autojs6-node-rhino-compat-rhino-shim-packaged-metadata-gate-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatRhinoShimPackagedMetadataGate",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      packagedRejection: checkById(checks, "packaged-rejection-report"),
      providerMetadata: checkById(checks, "provider-registry-metadata"),
      noInstallOrGlobals: checkById(checks, "metadata-does-not-authorize-install-or-globals"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino shim packaged metadata gate verification: " + report.decision.status);
  console.log("Rhino shim packaged metadata gate policy violations: " + policyViolations.length);
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
  const scheduledPolicy = extractBetween(
    files.executionModePolicy.text,
    "mode = MODE_SCHEDULED",
    "mode = MODE_WORKER_COMPUTATION",
  );
  const verifierSyntax = checkSyntax(
    path.join(repoRoot, "tools/nodejs/phase12/verify-rhino-compat-rhino-shim-packaged-metadata-gate.js"),
  );

  return [
    check(
      "packaged-rejection-report",
      includesAll(files.packagedReport.text, [
        "Status: P12-26 packaged metadata gate recorded as fail-closed",
        "packagedSupport.status = unsupported",
        "No packaged metadata shape is accepted in Phase 12",
        "APKs without explicit shim metadata cannot execute the shim",
        "metadata may authorize only JSON-only run",
        "metadata never authorizes install or globals",
        "packaged timeout, crash, and destroy evidence is intentionally absent",
        "therefore packaged execution remains rejected",
        "queued-host",
        "ERR_AUTOJS6_RHINO_SHIM_DISABLED",
        "provider-unavailable",
        "verifyNodeRhinoCompatRhinoShimPackagedMetadataGate",
      ]),
      "P12-26 report must record packaged rejection, metadata scope, queued-host denial, and evidence boundaries.",
    ),
    check(
      "provider-registry-metadata",
      includesAll(files.providerRegistry.text, [
        "private const val RHINO_PROVIDER = \"rhino\"",
        "displayName = \"rhino JSON-only shim\"",
        "modules = listOf(NodeBridgeModules.RHINO)",
        "capabilities = listOf(NodeBridgePermissionManifest.RHINO)",
        "packagedUnsupported(",
        "P12-26 rejects packaged Rhino JSON-only execution",
        "explicit shim metadata authorizes only JSON-only run",
        "RHINO_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE",
        "\"packagedSupport\" to \"unsupported\"",
        "\"packagedPolicy\"",
        "future metadata may authorize only JSON-only run, never install or globals",
        "\"installPolicy\"",
        "\"globalsPolicy\"",
        "\"timeoutPolicy\"",
        "\"statusReason\"",
      ]),
      "Provider registry must publish rhino provider metadata and mark packaged support unsupported.",
    ),
    check(
      "one-shot-provider-policy",
      includesAll(files.executionModePolicy.text, [
        "private val ONE_SHOT_PROVIDERS = listOf(",
        "\"rhino\"",
        "MODE_ONE_SHOT",
        "supportedLaunchSurfaces = setOf(LAUNCH_SURFACE_SCRIPT)",
        "timeoutPolicy = \"required_positive_timeout\"",
      ]) &&
        !scheduledPolicy.includes("\"rhino\""),
      "Execution mode diagnostics must allow rhino only for explicit one-shot script provider policy, not scheduled packaged promotion.",
    ),
    check(
      "default-and-queued-host-denial",
      includesAll(files.runtime.text, [
        "if (input.experimental !== true)",
        "throw __autojs6_rhino_shim_disabled_error(\"run\");",
        "provider: \"queued-host\"",
        "provider-unavailable",
        "ERR_AUTOJS6_RHINO_SHIM_DISABLED",
        "engineCreated: false",
        "destroyed: true",
      ]) &&
        includesAll(files.disabledReport.text, [
          "P12-26 records packaged metadata behavior as fail-closed",
          "Packaged APKs without explicit shim metadata cannot execute Rhino through Node",
        ]),
      "Default calls and queued-host/package-like execution must fail closed without creating Rhino engines.",
    ),
    check(
      "metadata-does-not-authorize-install-or-globals",
      includesAll(files.runtime.text, [
        "throw __autojs6_rhino_install_unsupported_error();",
        "install: \"unsupported\"",
        "globalsInstalled: false",
      ]) &&
        includesAll(files.rhinoTypes.text, [
          "install(options?: unknown): never",
          "globalsInstalled: false",
          "run<T extends JsonValue = JsonValue>(request: RunRequest): Promise<RunResult<T>>",
        ]) &&
        includesAll(files.pocGate.text, [
          "Metadata may authorize only JSON-only `run`, not `install()` or globals",
          "Packaged support remains fail-closed after P12-26",
        ]) &&
        !includesAny(files.runtime.text, [
          "compat.rhino",
          "__autojs6_define_global(\"rhino\"",
          "__autojs6_define_global('rhino'",
        ]),
      "No metadata or experimental path may authorize rhino.install(), compat.rhino, or Rhino globals.",
    ),
    check(
      "timeout-crash-destroy-boundary",
      includesAll(files.rhinoService.text, [
        "future.get(descriptor.timeoutMs, TimeUnit.MILLISECONDS)",
        "TimeoutException",
        "engine?.forceStop()",
        "engine.destroy()",
        "destroyed",
        "forceStopRequested",
      ]) &&
        includesAll(files.instrumentationTest.text, [
          "ERR_AUTOJS6_RHINO_EXECUTION_TIMEOUT",
          "lifecycle.destroyed",
          "forceStopRequested",
        ]) &&
        includesAll(files.packagedReport.text, [
          "live embedded POC has timeout and destroy evidence",
          "packaged crash containment evidence does not exist",
          "packaged execution remains rejected",
        ]),
      "P12-26 must distinguish live timeout/destroy evidence from missing packaged crash/destroy evidence.",
    ),
    check(
      "bridge-and-permission-still-run-only",
      includesAll(files.bridgeProtocol.text, [
        "val rhinoMethods = setOf(\"run\")",
        "dispatchRhino(request, auditedCallback)",
        "NodeBridgeRhinoExecutionService.dispatch(appContext, request, callback)",
      ]) &&
        includesAll(files.permissionManifest.text, [
          "const val RHINO = \"rhino\"",
          "\"rhino.run\" to RHINO",
          "NodeBridgeModules.RHINO -> listOf(RHINO)",
        ]) &&
        !files.permissionManifest.text.includes("RHINO_PACKAGED"),
      "Bridge and permission manifest must expose only rhino.run and avoid a packaged capability until metadata is designed.",
    ),
    check(
      "p12-25-updated-for-p12-26",
      includesAll(files.pocReport.text, [
        "P12-26 records packaged metadata behavior as fail-closed",
        "No packaged metadata shape is accepted in Phase 12",
        "packaged APK execution remains rejected",
      ]) &&
        includesAll(files.p12_25Verifier.text, [
          "P12-26 records packaged metadata behavior as fail-closed",
          "RHINO_SHIM_PACKAGED_METADATA_GATE.md",
          "/\\[x\\]\\s+P12-26/",
        ]),
      "P12-25 report and verifier must no longer require P12-26 to remain open.",
    ),
    check(
      "docs-updated",
      includesAll(files.jsonDesign.text, [
        "P12-26 records packaged metadata behavior as fail-closed",
        "No packaged metadata shape is accepted in Phase 12",
        "metadata may authorize only JSON-only run",
      ]) &&
        includesAll(files.shimDesign.text, [
          "P12-26",
          "packaged metadata gate",
          "fail-closed",
        ]) &&
        includesAll(files.loaderDesign.text, [
          "P12-26 records packaged metadata behavior as fail-closed",
          "packaged APK execution remains rejected",
        ]) &&
        includesAll(files.migration.text, [
          "P12-26 records packaged metadata behavior as fail-closed",
          "Do not ship packaged APK code that depends on the Rhino JSON-only POC",
        ]) &&
        includesAll(files.securityModel.text, [
          "P12-26 records packaged metadata behavior as fail-closed",
          "No packaged metadata shape is accepted in Phase 12",
        ]),
      "Design, loader, migration, and security docs must record the P12-26 fail-closed packaged gate.",
    ),
    check(
      "surface-manifest",
      rhinoSurface &&
        rhinoSurface.status === "provider_poc" &&
        (rhinoSurface.tasks || []).includes("P12-26") &&
        includesAll(JSON.stringify(rhinoSurface), [
          "P12-26 records packaged metadata behavior as fail-closed",
          "RHINO_SHIM_PACKAGED_METADATA_GATE.md",
          "NodeBridgeProviderRegistry.kt",
          "NodeExecutionModePolicy.kt",
          "verify-rhino-compat-rhino-shim-packaged-metadata-gate.js",
          "verifyNodeRhinoCompatRhinoShimPackagedMetadataGate",
        ]) &&
        includesAll(files.surfaceDocs.text, [
          "P12-26 records packaged metadata behavior as fail-closed",
          "packaged APK execution remains rejected",
        ]),
      "Phase 12 surface manifest must keep rhino.json_only_shim as provider_poc with P12-26 evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-26/.test(files.phase12Tasks.text) &&
        includesAll(files.phase12Tasks.text, [
          "RHINO_SHIM_PACKAGED_METADATA_GATE.md",
          "NodeBridgeProviderRegistry.kt",
          "packagedSupport.status = unsupported",
          "verify-rhino-compat-rhino-shim-packaged-metadata-gate.js",
          "verifyNodeRhinoCompatRhinoShimPackagedMetadataGate",
        ]),
      "Phase 12 task plan must mark P12-26 complete with report, provider metadata, and gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatRhinoShimPackagedMetadataGate") &&
        files.gradle.text.includes("verify-rhino-compat-rhino-shim-packaged-metadata-gate.js") &&
        files.gradle.text.includes("RHINO_SHIM_PACKAGED_METADATA_GATE.md") &&
        files.gradle.text.includes("NodeBridgeProviderRegistry.kt") &&
        files.gradle.text.includes("verifyNodeRhinoCompatRhinoJsonOnlyExecutionAdapterPoc"),
      "Gradle must register verifyNodeRhinoCompatRhinoShimPackagedMetadataGate after the P12-25 adapter gate.",
    ),
    check(
      "syntax-verifier",
      verifierSyntax.ok,
      `P12-26 verifier must pass node --check${verifierSyntax.ok ? "" : ": " + verifierSyntax.message}`,
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

function extractBetween(text, startMarker, endMarker) {
  const startIndex = text.indexOf(startMarker);
  if (startIndex < 0) {
    return "";
  }
  const endIndex = text.indexOf(endMarker, startIndex + startMarker.length);
  if (endIndex < 0) {
    return text.slice(startIndex);
  }
  return text.slice(startIndex, endIndex);
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
    "# Rhino Shim Packaged Metadata Gate Verification",
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
      console.log("Usage: node verify-rhino-compat-rhino-shim-packaged-metadata-gate.js [--repo-root dir] [--report-dir dir]");
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
