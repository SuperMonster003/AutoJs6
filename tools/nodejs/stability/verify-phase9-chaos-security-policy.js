#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const sections = [
    chaosArtifactFailuresSection(files),
    chaosProcessFailuresSection(files),
    chaosCacheSnapshotWorkerWasmSection(files),
    securityNativeArtifactBoundarySection(files),
    securityIsolationWorkerWasiPluginSection(files),
    securityInspectorReleaseBypassSection(files),
  ];
  const blockers = sections.flatMap((section) => section.blockers.map((blocker) => `${section.id}: ${blocker}`));
  const warnings = sections.flatMap((section) => section.warnings.map((warning) => `${section.id}: ${warning}`));
  const nonReady = sections.filter((section) => section.status !== "ready").map((section) => `${section.id}=${section.status}`);
  const decision = {
    status: blockers.length > 0 ? "blocked" : nonReady.length > 0 ? "bootstrap_only" : "ready",
    blockers,
    warnings: warnings.concat(nonReady.map((entry) => `section is not fully production-ready: ${entry}`)),
  };
  const report = {
    schema: "autojs6-node-phase9-chaos-security-policy-v1",
    generatedAt: new Date().toISOString(),
    task: "S9-45 And S9-47 Chaos And Security Corpus",
    sections,
    decision,
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "phase9-chaos-security-policy", report, renderMarkdown);
  console.log("Phase 9 chaos/security policy decision: " + decision.status);
  if (decision.status === "blocked" || (options.failOnBootstrapOnly && decision.status !== "ready")) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  const paths = {
    chaosDoc: "docs/nodejs/reports/phase9/CHAOS_SECURITY.md",
    nativeCrash: "docs/nodejs/reports/phase9/NATIVE_CRASH_PIPELINE.md",
    isolationPolicy: "docs/nodejs/reports/phase9/ISOLATION_POLICY.md",
    wasmPolicy: "docs/nodejs/reports/phase9/WASM_WASI.md",
    inspectorPolicy: "docs/nodejs/reports/phase9/INSPECTOR_POLICY.md",
    pluginPolicy: "docs/nodejs/reports/phase9/PLUGIN_POLICY.md",
    performancePolicy: "docs/nodejs/reports/phase9/PERFORMANCE_POLICY.md",
    androidCompat: "docs/nodejs/native/ANDROID_NATIVE_COMPAT.md",
    distributionPolicy: "docs/nodejs/native/RUNTIME_DISTRIBUTION.md",
    runtimeAbi: "docs/nodejs/native/RUNTIME_ADAPTER_ABI.md",
    securityModel: "docs/nodejs/SECURITY_MODEL.md",
    testingDoc: "docs/nodejs/TESTING.md",
    ciGatesDoc: "docs/nodejs/CI_GATES.md",
    runtimeArtifactScript: "tools/nodejs/native/verify-runtime-artifacts.js",
    runtimeDistributionScript: "tools/nodejs/native/verify-runtime-distribution-policy.js",
    compileCacheTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeModuleCompileCacheInstrumentationTest.kt",
    prewarmTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeRuntimePrewarmPoolInstrumentationTest.kt",
    wasmTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWasmWasiInstrumentationTest.kt",
    inspectorSecurityTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeInspectorSecurityGateInstrumentationTest.kt",
    workerDesignTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsDesignGateInstrumentationTest.kt",
    pluginTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodePluginSystemInstrumentationTest.kt",
  };
  const loaded = {};
  for (const [key, relativePath] of Object.entries(paths)) {
    loaded[key] = {
      relativePath,
      text: readIfExists(path.join(repoRoot, relativePath)),
    };
  }
  return loaded;
}

function chaosArtifactFailuresSection(files) {
  const checks = [
    checkTokens("chaos-doc-runtime-artifacts", files.chaosDoc, [
      "adapter load failure",
      "wrong runtime hash",
      "16 KB-only environment",
      "runtime adapter export and ABI gates",
      "SHA-256 evidence",
    ]),
    checkTokens("runtime-artifact-script", files.runtimeArtifactScript, [
      "native-16kb-alignment",
      "runtime-artifact-manifest",
      "runtime-artifact-integrity",
      "SHA-256",
      "Build ID",
      "LOAD alignment",
    ]),
    checkTokens("native-compat-doc", files.androidCompat, [
      "16 KB alignment gate",
      "ELF `LOAD` segment alignment",
      "SHA-256",
      "Build ID",
      "current Node 24.5 `libnode.so` artifacts",
    ]),
  ];
  return makeSection({
    id: "chaos-runtime-artifact-failures",
    task: "S9-45 Adapter Load Failure, Wrong Runtime Hash, And 16 KB-only Environment",
    statusWhenPassing: "partial",
    checks,
    warnings: [
      "The current gate validates artifact evidence and policy shape; it does not mutate native artifacts on device.",
    ],
  });
}

function chaosProcessFailuresSection(files) {
  const checks = [
    checkTokens("chaos-doc-process-failures", files.chaosDoc, [
      "runtime process crash",
      "Binder death",
      "provider process death",
      "low-memory callback",
      "TRIM_MEMORY_RUNNING_LOW",
    ]),
    checkTokens("native-crash-pipeline", files.nativeCrash, [
      "runtime artifact manifest",
      "logcat window",
      "tombstone text or Android bugreport excerpt",
      "execution descriptor",
      "crash quarantine/retry integration",
    ]),
    checkTokens("isolation-policy", files.isolationPolicy, [
      "Crash Containment And Recovery",
      "Current state: deferred",
      "Binder capability broker",
      "quarantine the failed slot",
    ]),
    checkTokens("prewarm-low-memory", files.prewarmTest, [
      "prewarmRuntime",
      "embedded_script.prewarm_pool.status",
      "processOnlyPrewarmExpiresWithoutContaminatingNextExecution",
    ]),
  ];
  return makeSection({
    id: "chaos-process-provider-failures",
    task: "S9-45 Runtime Crash, Binder Death, Provider Death, And Low-memory Callback",
    statusWhenPassing: "partial",
    checks,
    warnings: [
      "Low-memory evidence exists, but runtime crash injection, Binder death injection, and provider death injection remain future process-pool work.",
    ],
  });
}

function chaosCacheSnapshotWorkerWasmSection(files) {
  const checks = [
    checkTokens("chaos-doc-cache-snapshot-worker-wasm", files.chaosDoc, [
      "compile cache corruption",
      "snapshot corruption",
      "worker crash",
      "WASM timeout",
      "entry fallback",
    ]),
    checkTokens("compile-cache-test", files.compileCacheTest, [
      "NodeModuleCompileCacheInstrumentationTest",
      "embedded_script.compile_cache",
      "COMPILE_CACHE_ENV_DIR",
      "NODE_COMPILE_CACHE",
    ]),
    checkTokens("performance-policy", files.performancePolicy, [
      "startup snapshots",
      "bootstrap_only",
      "startup snapshots remain disabled",
      "compile-cache",
      "process-only prewarm",
    ]),
    checkTokens("worker-policy", files.isolationPolicy, [
      "Worker Partial Promotion",
      "Current state: blocked",
      "`worker_threads` stays disabled by default",
      "native-availability skips",
    ]),
    checkTokens("wasm-policy", files.wasmPolicy, [
      "resource limits for module size, memory pages, stdout/stderr, timeout",
      "WASM/WASI security corpus",
    ]),
  ];
  return makeSection({
    id: "chaos-cache-snapshot-worker-wasm",
    task: "S9-45 Compile Cache, Snapshot, Worker, And WASM Chaos",
    statusWhenPassing: "deferred",
    checks,
    warnings: [
      "Compile-cache fallback evidence exists, but positive corruption injection, startup snapshot mutation, worker crash, and WASM timeout gates are not runnable yet.",
    ],
  });
}

function securityNativeArtifactBoundarySection(files) {
  const checks = [
    checkTokens("chaos-doc-native-boundary", files.chaosDoc, [
      "runtime artifact substitution",
      "C ABI version mismatch",
      "16 KB alignment rejection",
      "dynamic native file not read-only",
      "AutoJsNodeRuntimeApiV1",
    ]),
    checkTokens("runtime-abi-doc", files.runtimeAbi, [
      "AutoJsNodeRuntimeApiV1",
      "struct_size",
      "runtime-specific embedder handles",
      "host/adapter boundary",
    ]),
    checkTokens("runtime-distribution-doc", files.distributionPolicy, [
      "localDeveloperRuntime",
      "read-only runtime artifacts",
      "remote native runtime downloads",
      "remoteNativeRuntimeDownload",
    ]),
    checkTokens("runtime-distribution-script", files.runtimeDistributionScript, [
      "localDeveloperRuntime",
      "read-only artifacts",
      "remoteNativeRuntimeDownload",
      "Unrestricted remote native runtime download is not supported.",
    ]),
  ];
  return makeSection({
    id: "security-native-artifact-boundary",
    task: "S9-47 Runtime Substitution, C ABI Mismatch, 16 KB Rejection, And Read-only Native Files",
    statusWhenPassing: "partial",
    checks,
    warnings: [
      "Host-side artifact and distribution evidence exists; on-device hostile native artifact substitution remains a future injection test.",
    ],
  });
}

function securityIsolationWorkerWasiPluginSection(files) {
  const checks = [
    checkTokens("chaos-doc-isolation-worker-wasi-plugin", files.chaosDoc, [
      "isolated broker capability bypass",
      "worker capability inheritance",
      "WASI preopen escape",
      "plugin signature mismatch",
      "controlled `autojs6:wasi` facade is not implemented",
    ]),
    checkTokens("isolation-policy", files.isolationPolicy, [
      "Capability Broker v3 remains future work",
      "worker_threads",
      "does not expose usable native worker execution",
    ]),
    checkTokens("wasm-policy", files.wasmPolicy, [
      "Raw `node:wasi` remains disabled",
      "Future `autojs6:wasi` must map preopens through the scoped-fs broker",
      "deny arbitrary host paths",
    ]),
    checkTokens("wasi-test", files.wasmTest, [
      "rawWasiBuiltinsRemainDisabledAndCannotBeShadowed",
      "require(\"node:wasi\")",
      "wasi.shadowLoaded=false",
    ]),
    checkTokens("worker-test", files.workerDesignTest, [
      "worker_threads design gate",
      "ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED",
    ]),
    checkTokens("plugin-policy", files.pluginPolicy, [
      "native service plugins",
      "does not enable native plugin archives",
      ".node` addons",
      "direct `process.dlopen` authority",
    ]),
    checkTokens("plugin-test", files.pluginTest, [
      "pluginNativeAddonFileIsRejected",
      "NodeRuntimeErrorCodes.NATIVE_ADDON_DISABLED",
    ]),
  ];
  return makeSection({
    id: "security-isolation-worker-wasi-plugin",
    task: "S9-47 Broker Bypass, Worker Inheritance, WASI Preopen, And Plugin Signature Mismatch",
    statusWhenPassing: "deferred",
    checks,
    warnings: [
      "Denial gates are present, but positive hostile-input coverage must wait for Broker v3, native worker availability, autojs6:wasi, and signed plugin archives.",
    ],
  });
}

function securityInspectorReleaseBypassSection(files) {
  const checks = [
    checkTokens("chaos-doc-inspector", files.chaosDoc, [
      "inspector release bypass",
      "disabled-state inspector security gate",
      "production Inspector",
    ]),
    checkTokens("inspector-policy", files.inspectorPolicy, [
      "release-forbidden",
      "no Gradle, project, or packaged APK override path",
      "non-loopback listeners",
      "deterministic close on stop",
      "packaged release denial",
    ]),
    checkTokens("inspector-security-test", files.inspectorSecurityTest, [
      "releaseAndPackagedMetadataCannotEnableInspector",
      "inspectorPortIsNotLeftListeningAfterScriptStopOrTimeout",
      "NODE_OPTIONS",
      "NODE_INSPECT_RESUME_ON_START",
    ]),
    checkTokens("ci-testing-docs", files.ciGatesDoc, [
      "verifyNodePhase9InspectorPolicy",
    ]),
  ];
  return makeSection({
    id: "security-inspector-release-bypass",
    task: "S9-47 Inspector Release Bypass",
    statusWhenPassing: "ready",
    checks,
    warnings: [
      "Ready here means release bypass denial is covered; positive debug inspector promotion would require a separate security gate.",
    ],
  });
}

function makeSection({ id, task, statusWhenPassing, checks, warnings }) {
  const blockers = checks
    .filter((check) => !check.ok)
    .map((check) => `${check.id} missing ${check.missing.join(", ")}`);
  return {
    id,
    task,
    status: blockers.length > 0 ? "blocked" : statusWhenPassing,
    checks,
    blockers,
    warnings: blockers.length > 0 ? warnings.concat("Required source, test, or documentation evidence is missing.") : warnings,
  };
}

function checkTokens(id, file, tokens) {
  const missing = [];
  if (!file.text) {
    missing.push(`file:${file.relativePath}`);
  } else {
    for (const token of tokens) {
      if (!file.text.includes(token)) {
        missing.push(token);
      }
    }
  }
  return {
    id,
    path: file.relativePath,
    ok: missing.length === 0,
    missing,
  };
}

function readIfExists(file) {
  if (!fs.existsSync(file)) {
    return "";
  }
  return fs.readFileSync(file, "utf8");
}

function writeReport(reportDir, baseName, report, renderer) {
  const jsonPath = path.join(reportDir, `${baseName}.json`);
  const markdownPath = path.join(reportDir, `${baseName}.md`);
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(markdownPath, renderer(report));
  console.log(`${baseName} JSON: ${jsonPath}`);
  console.log(`${baseName} Markdown: ${markdownPath}`);
}

function renderMarkdown(report) {
  const lines = [];
  lines.push("# AutoJs6 Node Phase 9 Chaos And Security Policy Gate");
  lines.push("");
  lines.push(`Generated: ${report.generatedAt}`);
  lines.push("");
  lines.push(`Decision: \`${report.decision.status}\``);
  lines.push("");
  lines.push("| Section | Task | Status |");
  lines.push("| --- | --- | --- |");
  for (const section of report.sections) {
    lines.push(`| ${section.id} | ${section.task} | ${section.status} |`);
  }
  for (const section of report.sections) {
    lines.push("");
    lines.push(`## ${section.id}`);
    lines.push("");
    lines.push("| Check | OK | Path |");
    lines.push("| --- | --- | --- |");
    for (const check of section.checks) {
      lines.push(`| ${check.id} | ${check.ok} | ${check.path} |`);
    }
    appendIssues(lines, "Blockers", section.blockers);
    appendIssues(lines, "Warnings", section.warnings);
  }
  appendIssues(lines, "Decision Blockers", report.decision.blockers);
  appendIssues(lines, "Decision Warnings", report.decision.warnings);
  lines.push("");
  return lines.join("\n");
}

function appendIssues(lines, title, issues) {
  if (!issues || issues.length === 0) {
    return;
  }
  lines.push("");
  lines.push(`### ${title}`);
  lines.push("");
  issues.forEach((issue) => lines.push(`- ${issue}`));
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-bootstrap-only"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node tools/nodejs/stability/verify-phase9-chaos-security-policy.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const eq = arg.indexOf("=");
    const rawKey = eq >= 0 ? arg.slice(2, eq) : arg.slice(2);
    const key = rawKey.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
    if (booleanArgs.has(rawKey)) {
      options[key] = eq >= 0 ? ["1", "true", "yes", "on"].includes(arg.slice(eq + 1).toLowerCase()) : true;
      continue;
    }
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
