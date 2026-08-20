#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const CAPABILITIES = [
  {
    id: "esm",
    status: ["stable", "implemented", "packaged_supported"],
    evidence: [
      "NodeEsmStableInstrumentationTest",
      "installAndRunStableEsmEmbeddedNodePackagedApk",
    ],
    notes: "ESM is enabled by default for local project graphs and packaged APK smokes.",
  },
  {
    id: "dynamic_import",
    status: ["stable", "implemented", "packaged_supported"],
    evidence: [
      "NODEJS_DYNAMIC_IMPORT_LOCAL_ENABLED",
      "installAndRunDynamicImportLocalEmbeddedNodePackagedApk",
    ],
    notes: "Local dynamic import is restricted to packaged/project graph targets and allowed builtins.",
  },
  {
    id: "fetch_axios",
    status: ["stable", "live_provider"],
    evidence: [
      "jsFetchAndAxiosUseAndroidLiveProviderWhenNetworkEnabled",
      "verifyNodeLiveFetchGate",
    ],
    notes: "The controlled Android provider is enabled by default and dispatch still requires the project network capability.",
  },
  {
    id: "websocket",
    status: ["stable", "live_provider"],
    evidence: [
      "jsWebSocketUsesAndroidLiveProviderWhenNetworkEnabled",
      "verifyNodeLiveWebSocketGate",
    ],
    notes: "The controlled WebSocket provider is enabled by default and retains capability and resource checks.",
  },
  {
    id: "ocr",
    status: ["live_provider", "partial"],
    evidence: [
      "kotlinOcrLiveProviderRecognizesMlKitImageHandle",
      "androidOcrRejectsUnknownImageHandleWithoutLeakingResources",
    ],
    notes: "Live path is device/provider dependent; unknown image handles fail closed.",
  },
  {
    id: "storage",
    status: ["live_provider", "implemented"],
    evidence: [
      "androidStorageProviderPersistsAndScopesByWorkingDirectory",
      "androidStorageProviderEnforcesQuotaAndPackageIdentityIsolation",
    ],
    notes: "Project-scoped persistence and package identity isolation are covered.",
  },
  {
    id: "notifications",
    status: ["live_provider", "implemented"],
    evidence: ["androidProviderPostsOrReportsNotificationPermissionAndTracksOwnedResource"],
    notes: "Permission-denied path is explicit and resource ownership is tracked.",
  },
  {
    id: "sensors",
    status: ["live_provider", "partial"],
    evidence: [
      "androidProviderListsSensorsAndCleansUpLiveSubscriptionWhenAvailable",
      "androidProviderReportsUnavailableWhenSensorManagerMissing",
    ],
    notes: "Availability depends on device sensors; cleanup and unavailable states are asserted.",
  },
  {
    id: "accessibility",
    status: ["live_provider", "partial"],
    evidence: [
      "providerFindsAndClicksControlledUiWhenAccessibilityServiceIsEnabled",
      "jsAccessibilitySelectorV2HonorsTimeoutAndCancellation",
    ],
    notes: "Real smoke may skip only when the focused service setup is unavailable.",
  },
  {
    id: "image",
    status: ["planned", "disabled"],
    evidence: ["androidImageExpansionRejectsUntilProviderIsConnected"],
    notes: "Current gate proves fail-closed provider behavior; full live image provider remains a Phase 10 target.",
  },
  {
    id: "ui",
    status: ["live_provider", "partial"],
    evidence: [
      "jsUiModuleUsesAndroidLiveProviderForButtonAndInputEvents",
      "androidUiProviderOpensActivityDeliversEventsAndCleansOwnedResource",
    ],
    notes: "Activity-owned provider is available with handle ownership and unsupported descriptor rejection.",
  },
  {
    id: "work_manager",
    status: ["live_provider", "packaged_supported", "partial"],
    evidence: [
      "androidWorkManagerProviderSchedulesLiveMetadataAndEnforcesPathPolicy",
      "androidWorkManagerLiveProviderExecutesPackagedProjectEntryAndPersistsResult",
    ],
    notes: "Packaged project entry execution is covered by a live provider test.",
  },
  {
    id: "shell",
    status: ["stable", "implemented"],
    evidence: ["NodeShellBridgeInstrumentationTest", "shell capability"],
    notes: "The controlled bridge surface remains capability-gated and separate from the stable native child_process builtin.",
  },
  {
    id: "workers",
    status: ["stable", "implemented", "packaged_supported"],
    evidence: [
      "NodeWorkerThreadsStableInstrumentationTest",
      "installAndRunWorkerThreadsEsmEmbeddedNodePackagedApk",
    ],
    notes: "worker_threads is enabled by default with native and packaged coverage.",
  },
  {
    id: "wasi",
    status: ["disabled", "blocked"],
    evidence: [
      "verifyNodePhase10WorkerWasiBoundary",
      "NodeWasmWasiInstrumentationTest",
      "verifyNodeWasmWasiGate",
    ],
    notes: "WebAssembly loading is allowed, but raw node:wasi and controlled autojs6:wasi facade promotion remain disabled/deferred.",
  },
  {
    id: "package_manager",
    status: ["implemented", "partial"],
    evidence: [
      "verifyNodePhase10Autojs6PmFixture",
      "verifyNodeDependencyManagerFixture",
      "verifyNodeDependencyBundlerFixtures",
    ],
    notes: "Host-managed autojs6-pm MVP covers offline registry metadata, semver, tarball integrity, lockfile, safe extraction, and compatibility analysis; Android runtime install remains disabled.",
  },
  {
    id: "typescript_build",
    status: ["implemented", "packaged_supported", "partial"],
    evidence: [
      "verifyNodePhase10DeveloperExperience",
      "NodeTypeScriptInstrumentationTest",
      "phase10-developer-experience",
    ],
    notes: "Runtime lightweight TypeScript remains .ts/.mts/.cts erasable syntax only; Phase 10 adds a host/build-stage transform and incremental cache report.",
  },
  {
    id: "source_maps",
    status: ["implemented", "packaged_supported", "partial"],
    evidence: [
      "NodeSourceMapStackTraceInstrumentationTest",
      "embedded_script.source_map_loaded_count",
      "verifyNodeDeveloperExperienceGate",
    ],
    notes: "Inline and scoped external source maps are device-tested; Phase 10 developer experience also records TypeScript build-stage map outputs.",
  },
  {
    id: "embedded_test_runner",
    status: ["implemented", "partial"],
    evidence: [
      "nodeTestEmbeddedSubsetRunsSingleRuntimeTap",
      "nodeTestEmbeddedSubsetReportsFailuresTimeoutsAndLateAsyncActivity",
      "partialEsmImportsNodeTestEmbeddedSubset",
    ],
    notes: "Single-runtime node:test subset supports TAP, hooks, skip/todo, timeouts, failures, and late async diagnostics without child_process isolation.",
  },
  {
    id: "watch_hot_reload",
    status: ["implemented", "partial", "disabled"],
    evidence: [
      "verifyNodePhase10DeveloperExperience",
      "pluginLifecycleResourcesAndDebugReload",
      "plugins.reload is debug-only",
    ],
    notes: "Debug-only host workflow and plugin reload evidence exist; packaged/release hot reload remains disabled by policy.",
  },
  {
    id: "crash_evidence_bundle",
    status: ["implemented", "partial"],
    evidence: [
      "verifyNodePhase10CrashAdapterCutover",
      "phase10-crash-evidence-bundle",
      "nativeLibraryHashes",
    ],
    notes: "Phase 10 fixes the crash evidence bundle schema and generates a synthetic host manifest; automatic crash-time capture remains future work.",
  },
  {
    id: "adapter_v1_cutover",
    status: ["blocked", "disabled"],
    evidence: [
      "verifyNodeCrashAdapterCutoverGate",
      "ERR_AUTOJS6_NODE_ADAPTER_NOT_MIGRATED",
      "legacy_jni",
    ],
    notes: "Adapter ABI/export gates are ready, but Node 24.5 adapter lifecycle is still surface-only and default execution remains legacy_jni.",
  },
  {
    id: "long_running",
    status: ["implemented", "partial"],
    evidence: [
      "verifyNodePhase10LongRunningActivation",
      "NodeInteractiveLongRunningInstrumentationTest",
      "verifyNodeLongRunningGate",
    ],
    notes: "Interactive foreground long-running, scheduled runner, queue, heartbeat, checkpoint, and stress evidence exist; packaged long-running service mode remains blocked.",
  },
  {
    id: "multi_instance",
    status: ["planned", "disabled"],
    evidence: [
      "verifyNodePhase10LongRunningActivation",
      "NodeEmbeddedProcessPoolPolicyInstrumentationTest",
    ],
    notes: "Reserved process slots and policy evidence exist, but effective active slots remain primary-only and two live execution slots are not promoted.",
  },
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const nativeCppRoot = resolveNativeCppRoot(repoRoot, options.nativeCppRoot);
  const files = loadEvidenceFiles(repoRoot, nativeCppRoot);
  const capabilities = CAPABILITIES.map((capability) => evaluateCapability(capability, files));
  const policyViolations = [];

  for (const capability of capabilities) {
    if (capability.evidenceMatches.length === 0) {
      policyViolations.push(`${capability.id} has no matching code/test evidence`);
    }
    if (capability.status.includes("live_provider") && capability.status.includes("fake_provider_only")) {
      policyViolations.push(`${capability.id} cannot be both live_provider and fake_provider_only`);
    }
  }
  const packagedCapabilities = capabilities.filter((entry) => entry.status.includes("packaged_supported"));
  if (!packagedCapabilities.some((entry) => entry.id === "esm") || !packagedCapabilities.some((entry) => entry.id === "dynamic_import")) {
    policyViolations.push("packaged ESM and dynamic import must be represented as packaged_supported capabilities");
  }

  const report = {
    schema: "autojs6-node-phase10-capability-truth-v1",
    generatedAt: new Date().toISOString(),
    task: "S10-02 Runtime Capability Truth Report",
    gate: "generateNodePhase10CapabilityTruth",
    statusVocabulary: [
      "implemented",
      "stable",
      "partial",
      "planned",
      "fake_provider_only",
      "live_provider",
      "packaged_supported",
      "blocked",
      "disabled",
    ],
    evidenceSources: Object.fromEntries(Object.entries(files).map(([key, value]) => [key, value.path])),
    capabilities,
    totals: summarize(capabilities),
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      fakeProviderResultsPromoted: false,
      safeNodeProfilePreserved: true,
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "phase10-capability-truth", report, renderMarkdown);
  console.log("Phase 10 capability truth decision: " + report.decision.status);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadEvidenceFiles(repoRoot, nativeCppRoot) {
  return {
    buildGradle: readEvidence(repoRoot, "app/build.gradle.kts"),
    packagedSmoke: readEvidence(repoRoot, "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"),
    descriptor: readEvidence(repoRoot, "app/src/main/java/org/autojs/autojs/script/NodeProjectRuntimeCompatibilityDescriptor.kt"),
    capabilities: readEvidence(repoRoot, "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"),
    providerRegistry: readEvidence(repoRoot, "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"),
    fetchProviderTests: readEvidence(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeFetchBridgeInstrumentationTest.kt"),
    webSocketProviderTests: readEvidence(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeWebSocketBridgeInstrumentationTest.kt"),
    ocrProviderTests: readEvidence(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeOcrBridgeInstrumentationTest.kt"),
    storageProviderTests: readEvidence(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeStorageBridgeInstrumentationTest.kt"),
    notificationsProviderTests: readEvidence(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeNotificationsBridgeInstrumentationTest.kt"),
    sensorsProviderTests: readEvidence(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeSensorsBridgeInstrumentationTest.kt"),
    accessibilityProviderTests: readEvidence(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilityRealDeviceSmokeInstrumentationTest.kt"),
    imageProviderTests: readEvidence(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeImageBridgeExpansionInstrumentationTest.kt"),
    uiProviderTests: readEvidence(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeUiBridgeInstrumentationTest.kt"),
    workManagerProviderTests: readEvidence(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt"),
    phase10Gate: readEvidence(repoRoot, "app/node-phase10-gate.gradle.kts"),
    autojs6Pm: readEvidence(repoRoot, "tools/nodejs/phase10/autojs6-pm.js"),
    longRunningActivation: readEvidence(repoRoot, "tools/nodejs/phase10/verify-long-running-activation.js"),
    workerWasiBoundary: readEvidence(repoRoot, "tools/nodejs/phase10/verify-worker-wasi-boundary.js"),
    developerExperience: readEvidence(repoRoot, "tools/nodejs/phase10/verify-developer-experience.js"),
    crashAdapterCutover: readEvidence(repoRoot, "tools/nodejs/phase10/verify-crash-adapter-cutover.js"),
    runtimeAdapter245: readEvidenceFile(repoRoot, path.join(nativeCppRoot, "node_runtime_adapter_24_5.cpp")),
    testingDoc: readEvidence(repoRoot, "docs/nodejs/TESTING.md"),
    ciGatesDoc: readEvidence(repoRoot, "docs/nodejs/CI_GATES.md"),
  };
}

function resolveNativeCppRoot(repoRoot, requested) {
  const candidates = [
    requested && path.resolve(requested),
    path.resolve(repoRoot, "../AutoJs6-Plugin-NodeJs-Runtime/app/src/main/cpp"),
    path.join(repoRoot, "app/src/main/cpp"),
  ].filter(Boolean);
  return candidates.find((candidate) => fs.existsSync(candidate)) || candidates[candidates.length - 1];
}

function readEvidenceFile(repoRoot, file) {
  return {
    path: path.relative(repoRoot, file).replace(/\\/g, "/"),
    text: fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "",
  };
}

function readEvidence(repoRoot, relative) {
  const file = path.join(repoRoot, relative);
  return {
    path: relative,
    text: fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "",
  };
}

function evaluateCapability(capability, files) {
  const evidenceMatches = [];
  for (const token of capability.evidence) {
    const locations = Object.values(files)
      .filter((entry) => entry.text.includes(token))
      .map((entry) => entry.path);
    if (locations.length > 0) {
      evidenceMatches.push({ token, locations });
    }
  }
  return {
    id: capability.id,
    status: capability.status,
    evidence: capability.evidence,
    evidenceMatches,
    confidence: evidenceMatches.length === capability.evidence.length ? "high" : "partial",
    notes: capability.notes,
  };
}

function summarize(capabilities) {
  const totals = {};
  for (const capability of capabilities) {
    for (const status of capability.status) {
      totals[status] = (totals[status] || 0) + 1;
    }
  }
  return totals;
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 10 Capability Truth",
    "",
    `- Decision: ${report.decision.status}`,
    `- Capabilities: ${report.capabilities.length}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "| Capability | Status | Confidence | Notes |",
    "| --- | --- | --- | --- |",
  ];
  for (const capability of report.capabilities) {
    lines.push(`| ${capability.id} | ${capability.status.join(", ")} | ${capability.confidence} | ${capability.notes} |`);
  }
  lines.push("", "## Policy Violations", "");
  if (report.policyViolations.length === 0) {
    lines.push("None.");
  } else {
    report.policyViolations.forEach((violation) => lines.push(`- ${violation}`));
  }
  return lines.join("\n") + "\n";
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node generate-capability-truth.js [--repo-root dir] [--native-cpp-root dir] [--report-dir dir]");
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

function writeReport(reportDir, basename, json, markdownRenderer) {
  fs.writeFileSync(path.join(reportDir, `${basename}.json`), JSON.stringify(json, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${basename}.md`), markdownRenderer(json));
}

main();
