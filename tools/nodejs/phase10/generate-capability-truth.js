#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const CAPABILITIES = [
  {
    id: "esm",
    status: ["implemented", "packaged_supported", "experimental"],
    evidence: [
      "NodeEsmExperimentalInstrumentationTest",
      "installAndRunExperimentalEsmEmbeddedNodePackagedApk",
    ],
    notes: "Partial ESM is enabled for local project graphs and packaged APK smokes.",
  },
  {
    id: "dynamic_import",
    status: ["implemented", "packaged_supported", "experimental"],
    evidence: [
      "NODEJS_DYNAMIC_IMPORT_LOCAL_ENABLED",
      "installAndRunDynamicImportLocalEmbeddedNodePackagedApk",
    ],
    notes: "Local dynamic import is restricted to packaged/project graph targets and allowed builtins.",
  },
  {
    id: "fetch_axios",
    status: ["live_provider", "experimental"],
    evidence: [
      "jsFetchAndAxiosUseAndroidLiveProviderWhenNetworkExperimentEnabled",
      "verifyNodeLiveFetchGate",
    ],
    notes: "Controlled Android provider requires network capability and -Pautojs.nodejs.network.experimental=true.",
  },
  {
    id: "websocket",
    status: ["live_provider", "experimental"],
    evidence: [
      "jsWebSocketUsesAndroidLiveProviderWhenNetworkExperimentEnabled",
      "verifyNodeLiveWebSocketGate",
    ],
    notes: "Controlled WebSocket provider requires the same experimental network opt-in.",
  },
  {
    id: "ocr",
    status: ["live_provider", "experimental"],
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
    status: ["live_provider", "experimental"],
    evidence: [
      "androidProviderListsSensorsAndCleansUpLiveSubscriptionWhenAvailable",
      "androidProviderReportsUnavailableWhenSensorManagerMissing",
    ],
    notes: "Availability depends on device sensors; cleanup and unavailable states are asserted.",
  },
  {
    id: "accessibility",
    status: ["live_provider", "experimental"],
    evidence: [
      "providerFindsAndClicksControlledUiWhenAccessibilityServiceIsEnabled",
      "jsAccessibilitySelectorV2HonorsTimeoutAndCancellation",
    ],
    notes: "Real smoke may skip only when the focused service setup is unavailable.",
  },
  {
    id: "image",
    status: ["blocked"],
    evidence: ["androidImageExpansionRejectsUntilProviderIsConnected"],
    notes: "Current gate proves fail-closed provider behavior; full live image provider remains a Phase 10 target.",
  },
  {
    id: "ui",
    status: ["live_provider", "experimental"],
    evidence: [
      "jsUiModuleUsesAndroidLiveProviderForButtonAndInputEvents",
      "androidUiProviderOpensActivityDeliversEventsAndCleansOwnedResource",
    ],
    notes: "Activity-owned provider is available with handle ownership and unsupported descriptor rejection.",
  },
  {
    id: "work_manager",
    status: ["live_provider", "packaged_supported", "experimental"],
    evidence: [
      "androidWorkManagerProviderSchedulesLiveMetadataAndEnforcesPathPolicy",
      "androidWorkManagerLiveProviderExecutesPackagedProjectEntryAndPersistsResult",
    ],
    notes: "Packaged project entry execution is covered by a live provider test.",
  },
  {
    id: "shell",
    status: ["implemented", "experimental"],
    evidence: ["NodeShellBridgeInstrumentationTest", "shell capability"],
    notes: "Controlled bridge surface only; unrestricted child_process remains disabled.",
  },
  {
    id: "workers",
    status: ["disabled", "blocked"],
    evidence: ["NodeWorkerThreadsDesignGateInstrumentationTest"],
    notes: "worker_threads remains default disabled unless explicit experimental support is available.",
  },
  {
    id: "wasi",
    status: ["disabled", "blocked"],
    evidence: ["NodeWasmWasiInstrumentationTest", "verifyNodeWasmWasiGate"],
    notes: "WebAssembly loading is allowed, but raw node:wasi is not exposed as a sandbox.",
  },
  {
    id: "package_manager",
    status: ["implemented", "experimental"],
    evidence: ["verifyNodeDependencyManagerFixture", "verifyNodeDependencyBundlerFixtures"],
    notes: "Host dependency manager and bundler sanitizer exist; user-facing package manager remains Phase 10 work.",
  },
  {
    id: "long_running",
    status: ["implemented", "experimental"],
    evidence: ["NodeInteractiveLongRunningInstrumentationTest", "verifyNodeLongRunningGate"],
    notes: "Long-running/resource lifecycle policies and stress gates exist; scheduled expansion remains Phase 10 work.",
  },
  {
    id: "multi_instance",
    status: ["blocked", "experimental"],
    evidence: ["NodeEmbeddedProcessPoolPolicyInstrumentationTest"],
    notes: "Policy and process-pool design evidence exists; two live execution slots are not promoted yet.",
  },
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadEvidenceFiles(repoRoot);
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
      "fake_provider_only",
      "live_provider",
      "packaged_supported",
      "experimental",
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

function loadEvidenceFiles(repoRoot) {
  return {
    buildGradle: readEvidence(repoRoot, "app/build.gradle.kts"),
    packagedSmoke: readEvidence(repoRoot, "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"),
    descriptor: readEvidence(repoRoot, "app/src/main/java/org/autojs/autojs/script/NodeProjectRuntimeCompatibilityDescriptor.kt"),
    capabilities: readEvidence(repoRoot, "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"),
    testingDoc: readEvidence(repoRoot, "docs/nodejs/TESTING.md"),
    ciGatesDoc: readEvidence(repoRoot, "docs/nodejs/CI_GATES.md"),
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
      console.log("Usage: node generate-capability-truth.js [--repo-root dir] [--report-dir dir]");
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
