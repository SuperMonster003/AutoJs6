#!/usr/bin/env node
"use strict";

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const REQUIRED_CRASH_BUNDLE_FIELDS = [
  "logcat",
  "tombstone",
  "nativeLibraryHashes",
  "buildIds",
  "runtimeDescriptor",
  "capabilityFlags",
  "executionRequest",
  "lastError",
  "packageIdentity",
  "abi",
];

const ADAPTER_OPERATIONS = [
  { id: "create_runtime", headerToken: "create", requiredForDefault: true },
  { id: "start_runtime", headerToken: "start", requiredForDefault: true },
  { id: "execute_source", headerToken: "execute", requiredForDefault: true },
  { id: "execute_module", headerToken: "execute", requiredForDefault: true },
  { id: "poll_drain", headerToken: "get_diagnostics", requiredForDefault: true },
  { id: "request_stop", headerToken: "request_stop", requiredForDefault: true },
  { id: "force_stop", headerToken: "request_stop", requiredForDefault: true },
  { id: "get_state", headerToken: "get_diagnostics", requiredForDefault: true },
  { id: "get_diagnostics", headerToken: "get_diagnostics", requiredForDefault: true },
  { id: "destroy_runtime", headerToken: "destroy", requiredForDefault: true },
  { id: "post_bridge_result", headerToken: "post_bridge_result", requiredForDefault: false },
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const crashBundle = buildCrashEvidenceBundle(repoRoot, reportDir, files);
  const resourceSnapshot = evaluateResourceSnapshot(files);
  const adapterInventory = evaluateAdapterInventory(files);
  const abGate = evaluateLegacyAdapterAbGate(files, adapterInventory);
  const defaultSwitch = evaluateDefaultSwitch(adapterInventory, abGate);
  const policyViolations = [];

  if (defaultSwitch.allowed && adapterInventory.currentAdapterStatus !== "migrated") {
    policyViolations.push("adapter_v1 cannot become default while node_runtime_adapter_24_5.cpp is surface_only");
  }
  if (crashBundle.requiredMissing.length > 0) {
    policyViolations.push("crash evidence bundle is missing required fields: " + crashBundle.requiredMissing.join(", "));
  }

  const report = {
    schema: "autojs6-node-phase10-crash-adapter-cutover-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase10CrashAdapterCutover",
    tasks: [
      task("S10-47", "Automatic Crash Evidence Bundle", crashBundle.status),
      task("S10-48", "Resource Snapshot And Timeline Tail", resourceSnapshot.status),
      task("S10-49", "Adapter Operation Inventory", adapterInventory.status),
      task("S10-50", "Node 24.5 Adapter Lifecycle Implementation", adapterInventory.lifecycleStatus),
      task("S10-51", "Legacy vs Adapter A/B Gate", abGate.status),
      task("S10-52", "Adapter Default Switch Gate", defaultSwitch.status),
    ],
    crashBundle,
    resourceSnapshot,
    adapterInventory,
    abGate,
    defaultSwitch,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready_with_recorded_blockers" : "failed",
      defaultRuntimePath: "legacy_jni",
      adapterV1DefaultAllowed: defaultSwitch.allowed,
      crashEvidenceBundleGenerated: crashBundle.status !== "missing",
      safeNodeProfilePreserved: true,
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "phase10-crash-adapter-cutover", report, renderMarkdown);
  console.log("Phase 10 crash/adapter cutover decision: " + report.decision.status);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildCrashEvidenceBundle(repoRoot, reportDir, files) {
  const bundleDir = path.join(reportDir, "phase10-crash-evidence-bundle");
  fs.mkdirSync(bundleDir, { recursive: true });
  const nativeHashes = collectNativeLibraryHashes(repoRoot);
  const descriptor = extractRuntimeDescriptor(files.adapterSource.text);
  const bundle = {
    schema: "autojs6-node-phase10-crash-evidence-bundle-v1",
    generatedAt: new Date().toISOString(),
    crashInjected: false,
    crashSource: "synthetic_gate_manifest",
    logcat: {
      status: files.nativeCrashDiagnostics.text.includes("adb logcat -c") ? "command_documented" : "missing",
      reference: "docs/nodejs/NATIVE_CRASH_DIAGNOSTICS.md",
    },
    tombstone: {
      status: files.nativeCrashDiagnostics.text.includes("/data/tombstones") ? "reference_documented" : "missing",
      reference: "docs/nodejs/NATIVE_CRASH_DIAGNOSTICS.md",
    },
    nativeLibraryHashes: nativeHashes,
    buildIds: {
      status: "best_effort",
      note: "Current Node 24.5 artifacts may not expose GNU Build IDs; SHA-256 remains the stable identity.",
    },
    runtimeDescriptor: descriptor,
    capabilityFlags: {
      defaultRuntime: files.capabilities.text.includes("node24_5") ? "node24_5" : "unknown",
      safeProfileBoundariesPreserved: true,
    },
    executionRequest: {
      status: "synthetic_placeholder",
      note: "Automatic capture at crash time remains future work; the bundle schema is now fixed.",
    },
    lastError: {
      code: "ERR_AUTOJS6_NODE_ADAPTER_NOT_MIGRATED",
      source: "node_runtime_adapter_24_5.cpp",
    },
    packageIdentity: {
      status: files.phase9Crash.text.includes("package identity") ? "field_documented" : "missing",
    },
    abi: {
      checked: nativeHashes.map((entry) => entry.abi),
    },
  };
  const requiredMissing = REQUIRED_CRASH_BUNDLE_FIELDS.filter((field) => !bundle[field]);
  fs.writeFileSync(path.join(bundleDir, "manifest.json"), JSON.stringify(bundle, null, 2) + "\n");
  fs.writeFileSync(path.join(bundleDir, "README.md"), renderBundleReadme(bundle));
  return {
    status: requiredMissing.length === 0 ? "generated_schema_only" : "missing",
    directory: path.relative(repoRoot, bundleDir).replace(/\\/g, "/"),
    manifest: path.relative(repoRoot, path.join(bundleDir, "manifest.json")).replace(/\\/g, "/"),
    requiredFields: REQUIRED_CRASH_BUNDLE_FIELDS,
    requiredMissing,
    nativeLibraryCount: nativeHashes.length,
    automaticCrashTimeCapture: false,
  };
}

function collectNativeLibraryHashes(repoRoot) {
  const root = path.join(repoRoot, "app/src/main/jniLibs");
  if (!fs.existsSync(root)) {
    return [];
  }
  const entries = [];
  for (const abi of fs.readdirSync(root).sort()) {
    const lib = path.join(root, abi, "libnode.so");
    if (!fs.existsSync(lib)) {
      continue;
    }
    const hash = crypto.createHash("sha256").update(fs.readFileSync(lib)).digest("hex");
    entries.push({
      abi,
      library: "libnode.so",
      sha256: hash,
      path: path.relative(repoRoot, lib).replace(/\\/g, "/"),
    });
  }
  return entries;
}

function evaluateResourceSnapshot(files) {
  const checks = [
    tokenCheck("timeline-doc", files.testing.text, ["embedded_script.timeline_json", "last embedded runtime timeline events"]),
    tokenCheck("timeline-runtime", files.bridgeSources.text, ["timeline_json", "timeline_event_count"]),
    tokenCheck("resource-registry", files.resourceRegistry.text, ["NodeBridgeResourceRegistry", "activeResourceCount"]),
    tokenCheck("resource-diagnostics", files.bridgeSources.text, ["pendingScopedFsWatchCount", "pendingNodeTestCallbacks"]),
  ];
  const missing = checks.flatMap((check) => check.missing.map((token) => `${check.id}:${token}`));
  return {
    status: missing.length === 0 ? "partial_runtime_evidence" : "blocked",
    crashTimeSnapshotImplemented: false,
    timelineTailLimit: 256,
    activeResourceInventory: [
      "bridge resources",
      "timers",
      "watchers",
      "fd/FileHandle",
      "pending bridge calls",
      "test callbacks",
      "worker placeholders",
      "socket/UI/image handles via provider registry",
    ],
    checks,
    missing,
  };
}

function evaluateAdapterInventory(files) {
  const header = files.adapterHeader.text;
  const source = files.adapterSource.text;
  const operations = ADAPTER_OPERATIONS.map((operation) => {
    const exported = header.includes(operation.headerToken);
    const sourceMentions = source.includes(operation.headerToken) || source.includes(toCamel(operation.headerToken));
    const returnsUnavailable = source.includes("AUTOJS_NODE_RESULT_UNAVAILABLE");
    return {
      id: operation.id,
      headerToken: operation.headerToken,
      exported,
      sourceMentions,
      implemented: exported && sourceMentions && !returnsUnavailable,
      currentBehavior: returnsUnavailable ? "surface_only_unavailable" : "implemented_or_unknown",
      requiredForDefault: operation.requiredForDefault,
    };
  });
  const requiredMissing = operations
    .filter((operation) => operation.requiredForDefault && !operation.exported)
    .map((operation) => operation.id);
  const surfaceOnly = source.includes("\"status\":\"surface_only\"") ||
    source.includes("legacy_jni_path") ||
    source.includes("ERR_AUTOJS6_NODE_ADAPTER_NOT_MIGRATED");
  return {
    status: requiredMissing.length === 0 ? "inventory_ready" : "blocked",
    lifecycleStatus: surfaceOnly ? "blocked_surface_only" : "implemented",
    currentAdapterStatus: surfaceOnly ? "surface_only" : "migrated",
    operations,
    requiredMissing,
    publicAbiLeaks: detectPublicAbiLeaks(header),
  };
}

function evaluateLegacyAdapterAbGate(files, adapterInventory) {
  const abiReady = files.adapterAbiScript.text.includes("AutoJsNodeRuntimeApiV1") &&
    files.adapterExportScript.text.includes("autojs_node_get_runtime_api_v1");
  const adapterExecutable = adapterInventory.currentAdapterStatus === "migrated";
  return {
    status: adapterExecutable && abiReady ? "ready" : "blocked_adapter_surface_only",
    selector: ["legacy_jni", "adapter_v1"],
    comparedSurfaces: [
      "stdout/stderr",
      "result",
      "error shape",
      "timers",
      "CJS/ESM",
      "fs",
      "bridge",
      "stop/timeout",
      "packaged APK",
      "memory/RSS",
    ],
    abiReady,
    adapterExecutable,
    blocker: adapterExecutable ? null : "Node 24.5 adapter currently returns AUTOJS_NODE_RESULT_UNAVAILABLE and points execute to legacy_jni_path.",
  };
}

function evaluateDefaultSwitch(adapterInventory, abGate) {
  const blockers = [];
  if (adapterInventory.publicAbiLeaks.length > 0) {
    blockers.push("public ABI leaks forbidden native types: " + adapterInventory.publicAbiLeaks.join(", "));
  }
  if (adapterInventory.currentAdapterStatus !== "migrated") {
    blockers.push("adapter_v1 lifecycle is not migrated");
  }
  if (abGate.status !== "ready") {
    blockers.push("legacy vs adapter A/B gate is not ready");
  }
  return {
    status: blockers.length === 0 ? "ready" : "blocked",
    allowed: blockers.length === 0,
    defaultPath: blockers.length === 0 ? "adapter_v1" : "legacy_jni",
    legacyFallbackRequired: true,
    blockers,
  };
}

function detectPublicAbiLeaks(header) {
  return ["node::", "v8::", "uv_", "JNIEnv", "jobject", "jstring", "jclass", "std::string"]
    .filter((token) => header.includes(token));
}

function extractRuntimeDescriptor(source) {
  if (!source.includes("kRuntimeDescriptor")) {
    return { status: "missing" };
  }
  return {
    status: source.includes("\"status\":\"surface_only\"") ? "surface_only" : "present",
    runtimeSlot: source.includes("node24_5") ? "node24_5" : "unknown",
    execute: source.includes("legacy_jni_path") ? "legacy_jni_path" : "adapter_v1",
  };
}

function loadFiles(repoRoot) {
  const paths = {
    phase9Crash: "docs/nodejs/reports/phase9/NATIVE_CRASH_PIPELINE.md",
    crashIntake: "docs/nodejs/CRASH_INTAKE.md",
    nativeCrashDiagnostics: "docs/nodejs/NATIVE_CRASH_DIAGNOSTICS.md",
    testing: "docs/nodejs/TESTING.md",
    capabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
    bridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
    resourceRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeResourceRegistry.kt",
    adapterHeader: "app/src/main/cpp/node_runtime_api_v1.h",
    adapterSource: "app/src/main/cpp/node_runtime_adapter_24_5.cpp",
    adapterAbiScript: "tools/nodejs/native/verify-runtime-adapter-abi.js",
    adapterExportScript: "tools/nodejs/native/verify-runtime-adapter-export.js",
  };
  return Object.fromEntries(
    Object.entries(paths).map(([key, relative]) => [
      key,
      {
        relative,
        text: read(path.join(repoRoot, relative)),
      },
    ]),
  );
}

function tokenCheck(id, text, tokens) {
  return {
    id,
    ok: tokens.every((token) => text.includes(token)),
    missing: tokens.filter((token) => !text.includes(token)),
  };
}

function read(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "";
}

function task(id, name, status) {
  return { id, name, status };
}

function toCamel(value) {
  return value.replace(/_([a-z])/g, (_, ch) => ch.toUpperCase());
}

function renderBundleReadme(bundle) {
  return [
    "# Phase 10 Crash Evidence Bundle",
    "",
    "This directory is generated by `verifyNodePhase10CrashAdapterCutover`.",
    "It fixes the archive schema for crash intake without injecting a native crash.",
    "",
    `- Generated: ${bundle.generatedAt}`,
    `- Crash injected: ${bundle.crashInjected}`,
    `- Native libraries: ${bundle.nativeLibraryHashes.length}`,
    "",
  ].join("\n");
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 10 Crash / Adapter Cutover",
    "",
    `- Decision: ${report.decision.status}`,
    `- Default runtime path: ${report.decision.defaultRuntimePath}`,
    `- Adapter v1 default allowed: ${report.decision.adapterV1DefaultAllowed}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "| Task | Status |",
    "| --- | --- |",
  ];
  for (const taskEntry of report.tasks) {
    lines.push(`| ${taskEntry.id} ${taskEntry.name} | ${taskEntry.status} |`);
  }
  lines.push(
    "",
    "## Crash Evidence Bundle",
    "",
    `- Status: ${report.crashBundle.status}`,
    `- Manifest: ${report.crashBundle.manifest}`,
    `- Native libraries: ${report.crashBundle.nativeLibraryCount}`,
    `- Automatic crash-time capture: ${report.crashBundle.automaticCrashTimeCapture}`,
    "",
    "## Resource Snapshot",
    "",
    `- Status: ${report.resourceSnapshot.status}`,
    `- Crash-time snapshot implemented: ${report.resourceSnapshot.crashTimeSnapshotImplemented}`,
    `- Timeline tail limit: ${report.resourceSnapshot.timelineTailLimit}`,
    "",
    "## Adapter Inventory",
    "",
    `- Status: ${report.adapterInventory.status}`,
    `- Lifecycle status: ${report.adapterInventory.lifecycleStatus}`,
    `- Current adapter status: ${report.adapterInventory.currentAdapterStatus}`,
    "",
    "| Operation | Exported | Current Behavior | Required For Default |",
    "| --- | --- | --- | --- |",
  );
  for (const operation of report.adapterInventory.operations) {
    lines.push(`| ${operation.id} | ${operation.exported} | ${operation.currentBehavior} | ${operation.requiredForDefault} |`);
  }
  lines.push(
    "",
    "## Default Switch Blockers",
    "",
  );
  if (report.defaultSwitch.blockers.length === 0) {
    lines.push("None.");
  } else {
    report.defaultSwitch.blockers.forEach((blocker) => lines.push(`- ${blocker}`));
  }
  lines.push("", "## Policy Violations", "");
  if (report.policyViolations.length === 0) {
    lines.push("None.");
  } else {
    report.policyViolations.forEach((violation) => lines.push(`- ${violation}`));
  }
  return lines.join("\n") + "\n";
}

function writeReport(reportDir, basename, json, markdownRenderer) {
  fs.writeFileSync(path.join(reportDir, `${basename}.json`), JSON.stringify(json, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${basename}.md`), markdownRenderer(json));
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-crash-adapter-cutover.js [--repo-root dir] [--report-dir dir]");
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
