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
    compileCacheSection(files),
    startupSnapshotSection(files),
    prewarmSection(files),
    memoryPressureSection(files),
    performanceGateSection(files),
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
    schema: "autojs6-node-phase9-performance-policy-v1",
    generatedAt: new Date().toISOString(),
    task: "S9-15..S9-20 Performance Productionization Policy",
    defaultRuntimeSlot: extractRuntimeSlot(files.runtimeBuildLock),
    sections,
    decision,
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "phase9-performance-policy", report, renderMarkdown);
  console.log("Phase 9 performance policy decision: " + decision.status);
  if (decision.status === "blocked" || (options.failOnBootstrapOnly && decision.status !== "ready")) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  const paths = {
    runtimeBridge: "app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt",
    embeddedService: "app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt",
    bridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
    bridgeLifecycle: "app/src/main/cpp/node_bridge_lifecycle.cpp",
    compileCacheTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeModuleCompileCacheInstrumentationTest.kt",
    prewarmTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeRuntimePrewarmPoolInstrumentationTest.kt",
    performanceMatrixTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodePerformanceMatrixInstrumentationTest.kt",
    doctorTest: "app/src/androidTest/java/org/autojs/autojs/ui/settings/NodeDoctorReportGeneratorInstrumentationTest.kt",
    compileCacheDoc: "docs/nodejs/reports/phase8/MODULE_COMPILE_CACHE.md",
    prewarmDoc: "docs/nodejs/reports/phase8/RUNTIME_PREWARM_POOL.md",
    performanceMatrixDoc: "docs/nodejs/reports/phase8/PERFORMANCE_MATRIX.md",
    startupSnapshotDoc: "docs/nodejs/design/BOOTSTRAP_STARTUP_SNAPSHOT.md",
    phase8SnapshotDoc: "docs/nodejs/reports/phase8/BOOTSTRAP_STARTUP_SNAPSHOT.md",
    performanceDoc: "docs/nodejs/PERFORMANCE.md",
    compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
    runtimeBuildLock: "tools/nodejs/runtime-build/runtime-build.lock.json",
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

function compileCacheSection(files) {
  const checks = [
    checkTokens("kotlin-env-contract", files.runtimeBridge, [
      "COMPILE_CACHE_SCHEMA_VERSION",
      "COMPILE_CACHE_ENV_ENABLED",
      "COMPILE_CACHE_ENV_DIR",
      "COMPILE_CACHE_ENV_KEY",
      "COMPILE_CACHE_ENV_RUNTIME_KEY",
      "COMPILE_CACHE_ENV_PROJECT_KEY",
      "COMPILE_CACHE_ENV_LOADER",
      "COMPILE_CACHE_ENV_PREPARE_ERROR",
    ]),
    checkTokens("cache-key-runtime-scope", files.runtimeBridge, [
      "compileCacheRuntimeKey",
      "NodeRuntimeCapabilities.NODE_HEADER_VERSION",
      "BuildConfig.NODEJS_RUNTIME_SLOT",
      "BuildConfig.NODEJS_RUNTIME_SLOT_TARGET_VERSION",
      "Build.SUPPORTED_ABIS",
      "Build.VERSION.SDK_INT",
      "compileCacheLoaderVersion",
    ]),
    checkTokens("cache-key-source-scope", files.runtimeBridge, [
      "compileCacheProjectKey",
      "compileCacheContentKey",
      "request.moduleSources.keys.sorted()",
      "request.runtimeModuleSources.keys.sorted()",
      "NodeProjectRuntimeCompatibilityDescriptor.ESM_LOADER_VERSION",
      "NodeProjectRuntimeCompatibilityDescriptor.DYNAMIC_IMPORT_VERSION",
      "NodeProjectRuntimeCompatibilityDescriptor.MODULE_SOURCES_SCHEMA_VERSION",
    ]),
    checkTokens("app-private-quota-and-prune", files.runtimeBridge, [
      "context.applicationContext",
      "codeCacheDir",
      "COMPILE_CACHE_DIR_NAME",
      "COMPILE_CACHE_MAX_BYTES",
      "COMPILE_CACHE_MAX_PROJECT_DIRS",
      "pruneCompileCacheDirectory",
      "safeDeleteRecursivelyUnder",
      "canonicalRoot",
    ]),
    checkTokens("user-env-filter", files.runtimeBridge, [
      "NODE_COMPILE_CACHE",
      "NODE_DISABLE_COMPILE_CACHE",
      "NODE_STARTUP_ENV_NAMES",
    ]),
    checkTokens("native-internal-enable", files.bridgeSources, [
      "__autojs6_install_compile_cache",
      "enableCompileCache",
      "flushCompileCache",
      "AUTOJS6_NODE_COMPILE_CACHE_",
      "NODE_COMPILE_CACHE",
      "NODE_DISABLE_COMPILE_CACHE",
      "unavailable_custom_loader",
    ]),
    checkTokens("focused-tests", files.compileCacheTest, [
      "moduleBuiltin.enableCompileCache === undefined",
      "moduleBuiltin.flushCompileCache === undefined",
      "process.env.AUTOJS6_NODE_COMPILE_CACHE_DIR === undefined",
      "process.env.NODE_COMPILE_CACHE === undefined",
      "process.env.NODE_DISABLE_COMPILE_CACHE === undefined",
    ]),
    checkTokens("doctor-test", files.doctorTest, [
      "compileCache",
      "appPrivateOnly",
      "userControlExposed",
      "COMPILE_CACHE_MAX_BYTES",
      "COMPILE_CACHE_MAX_PROJECT_DIRS",
    ]),
    checkTokens("phase8-doc-evidence", files.compileCacheDoc, [
      "Status: complete",
      "CJS",
      "ESM",
      "TypeScript",
      "app-private",
      "max total compile-cache bytes",
      "user-supplied env values cannot redirect or disable",
    ]),
  ];
  return makeSection({
    id: "compile-cache",
    task: "S9-15/S9-16 Compile Cache Production Policy",
    statusWhenPassing: "ready",
    checks,
    warnings: [
      "Node owns the on-disk V8 code-cache entry format; AutoJs6 verifies namespace, quota, pruning, diagnostics, and fail-soft policy rather than individual entry atomics.",
    ],
  });
}

function startupSnapshotSection(files) {
  const checks = [
    checkTokens("design-gate-doc", files.startupSnapshotDoc, [
      "Status: design gate only",
      "does not enable a bootstrap startup snapshot",
      "snapshot_data=nullptr",
      "There is no snapshot generator",
      "Future startup snapshot support needs these steps",
      "normal bootstrap fallback",
      "snapshot only: unavailable",
    ]),
    checkTokens("phase8-decision-doc", files.phase8SnapshotDoc, [
      "Do not enable startup snapshots in v1.1.",
      "no snapshot blob is generated",
      "normal bootstrap fallback",
      "Scripts must not select a snapshot blob",
    ]),
    checkTokens("native-null-snapshot-path", files.bridgeLifecycle, [
      "snapshot=null",
      "newIsolate(allocator.get(), eventLoop.get(), platform.get(), nullptr",
      "CreateIsolateData",
    ]),
  ];
  return makeSection({
    id: "startup-snapshot",
    task: "S9-17 Startup Snapshot",
    statusWhenPassing: "deferred",
    checks,
    warnings: [
      "Startup snapshots remain disabled until immutable bootstrap prelude generation, manifest validation, packaged loading, and isolation tests exist.",
    ],
  });
}

function prewarmSection(files) {
  const checks = [
    checkTokens("service-api", files.embeddedService, [
      "ACTION_PREWARM_RUNTIME",
      "prewarmRuntime",
      "EXTRA_PREWARM_PROCESS_ONLY",
      "consumePrewarmForExecution",
      "single-use-process-only",
    ]),
    checkTokens("safe-diagnostics", files.embeddedService, [
      "embedded_script.prewarm_pool.process_only",
      "embedded_script.prewarm_pool.runtime_loaded_before_execution",
      "embedded_script.prewarm_pool.capabilities_granted",
      "embedded_script.prewarm_pool.user_modules_loaded",
      "\"false\"",
    ]),
    checkTokens("low-memory-expiry", files.embeddedService, [
      "onTrimMemory",
      "TRIM_MEMORY_RUNNING_LOW",
      "expirePrewarm",
    ]),
    checkTokens("focused-tests", files.prewarmTest, [
      "NodeRuntimePrewarmPoolInstrumentationTest",
      "prewarmRuntime",
      "embedded_script.prewarm_pool.status",
      "embedded_script.prewarm_pool.process_only",
      "embedded_script.prewarm_pool.runtime_loaded_before_execution",
      "embedded_script.prewarm_pool.capabilities_granted",
      "embedded_script.prewarm_pool.user_modules_loaded",
    ]),
    checkTokens("phase8-doc-evidence", files.prewarmDoc, [
      "Status: process-only MVP complete",
      "no Node/V8 environment is loaded",
      "no user source, path, env, module graph",
      "no permissions are granted",
      "Reusable V8/Node",
      "environment pooling remains deferred",
    ]),
  ];
  return makeSection({
    id: "process-prewarm",
    task: "S9-18 Prewarm Runtime Pool",
    statusWhenPassing: "partial",
    checks,
    warnings: [
      "Only the single-use process prewarm is active; reusable clean Node/V8 runtime pooling remains deferred.",
    ],
  });
}

function memoryPressureSection(files) {
  const checks = [
    checkTokens("prewarm-memory-pressure", files.embeddedService, [
      "onTrimMemory",
      "TRIM_MEMORY_RUNNING_LOW",
      "expirePrewarm",
      "low_memory_",
    ]),
    checkTokens("prewarm-doc-memory-pressure", files.prewarmDoc, [
      "Android low-memory callbacks discard unused prewarm state",
      "no resources are opened on behalf of a script",
    ]),
  ];
  return makeSection({
    id: "memory-pressure",
    task: "S9-19 Android Memory Pressure Integration",
    statusWhenPassing: "partial",
    checks,
    warnings: [
      "Current memory-pressure integration covers unused prewarm expiry; V8 low-memory notification, idle provider closure, and per-runtime RSS hard quota remain later native lifecycle work.",
    ],
  });
}

function performanceGateSection(files) {
  const checks = [
    checkTokens("instrumentation-matrix", files.performanceMatrixTest, [
      "NodePerformanceMatrixInstrumentationTest",
      "compile-cache-only",
      "compile-cache-plus-process-prewarm",
      "not_measured_in_embedded_instrumentation",
      "embedded_script.compile_cache.schema",
      "embedded_script.prewarm_pool.status",
    ]),
    checkTokens("matrix-doc", files.performanceMatrixDoc, [
      "compile-cache-only",
      "snapshot-only",
      "compile-cache-plus-snapshot",
      "compile-cache-plus-process-prewarm",
      "measured separate axis",
    ]),
    checkTokens("performance-doc", files.performanceDoc, [
      "compile cache",
      "startup snapshots disabled",
      "process-only prewarm",
      "performance matrix",
    ]),
  ];
  return makeSection({
    id: "performance-slo-gate",
    task: "S9-20 Performance SLO Gate",
    statusWhenPassing: "partial",
    checks,
    warnings: [
      "The current matrix records compile-cache and process-prewarm axes, but no numeric Phase 9 SLO threshold or packaged launch metric is enforced yet.",
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

function extractRuntimeSlot(file) {
  if (!file.text) {
    return "unknown";
  }
  try {
    const parsed = JSON.parse(file.text);
    return parsed.promotionDecision && parsed.promotionDecision.defaultRuntimeSlot || "unknown";
  } catch (_) {
    return "unknown";
  }
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
  lines.push("# AutoJs6 Node Phase 9 Performance Policy Gate");
  lines.push("");
  lines.push(`Generated: ${report.generatedAt}`);
  lines.push("");
  lines.push(`Decision: \`${report.decision.status}\``);
  lines.push("");
  lines.push(`Default runtime slot: \`${report.defaultRuntimeSlot}\``);
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
      console.log("Usage: node tools/nodejs/performance/verify-phase9-performance-policy.js [--repo-root dir] [--report-dir dir]");
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
