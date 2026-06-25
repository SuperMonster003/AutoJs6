#!/usr/bin/env node
"use strict";

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-crash-evidence-automation";
const BUNDLE_DIRNAME = "phase13-crash-evidence-bundle";

const FILES = {
  blockerRegistry: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  phase11Collect: "tools/nodejs/phase11/collect-node-crash-bundle.js",
  phase11Verify: "tools/nodejs/phase11/verify-node-crash-bundle.js",
  phase11Symbolize: "tools/nodejs/phase11/symbolize-node-crash.js",
  crashStore: "app/src/main/java/org/autojs/autojs/engine/NodeCrashEvidenceStore.kt",
  app: "app/src/main/java/org/autojs/autojs/App.kt",
  embeddedService: "app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt",
  nativeProcessService: "app/src/main/java/org/autojs/autojs/engine/NativeNodeProcessService.kt",
  embeddedBridge: "app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt",
  crashEvidenceStoreTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeCrashEvidenceStoreInstrumentationTest.kt",
  androidRuntimePolicyTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeAndroidRuntimePolicyInstrumentationTest.kt",
  embeddedMvpTest: "app/src/androidTest/java/org/autojs/autojs/engine/EmbeddedScriptMvpInstrumentationTest.kt",
  capabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
};

const REQUIRED_MARKER_FIELDS = [
  "executionId",
  "runtimeSlot",
  "runtimeSlotId",
  "abi",
  "profile",
  "featureFlags",
  "resourceSnapshot",
  "timelineTail",
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const bundleDir = path.join(reportDir, BUNDLE_DIRNAME);
  const files = loadFiles(repoRoot);
  const report = buildReport(repoRoot, files, bundleDir);
  fs.mkdirSync(bundleDir, { recursive: true });
  fs.writeFileSync(path.join(bundleDir, "manifest.json"), JSON.stringify(report.bundleManifest, null, 2) + "\n");
  fs.writeFileSync(path.join(bundleDir, "README.md"), renderBundleReadme(report));
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report.output, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report.output));
  console.log("Phase 13 crash evidence automation: " + report.output.decision.status);
  console.log("Phase 13 crash evidence blockers: " + report.output.blockers.length);
  console.log("Phase 13 crash evidence policy violations: " + report.output.policyViolations.length);
  if (report.output.policyViolations.length > 0 || (options.failOnBlocked && !report.output.decision.finalBlockerClosable)) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const file = path.join(repoRoot, relative);
      return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
    }),
  );
}

function buildReport(repoRoot, files, bundleDir) {
  const markerAutomation = markerAutomationStatus(files);
  const scenarios = scenarioStatus(files);
  const nativeLibraryHashes = collectNativeLibraryHashes(repoRoot);
  const featureFlags = parseFeatureFlags(files.capabilities.text);
  const symbolization = {
    implemented: files.phase11Collect.exists && files.phase11Verify.exists && files.phase11Symbolize.exists,
    tools: [
      files.phase11Collect.relative,
      files.phase11Verify.relative,
      files.phase11Symbolize.relative,
    ],
  };
  const blockerEntry = blocker(files.blockerRegistry.text, "crash_evidence_automation");
  const blockers = [
    ...markerAutomation.blockers,
    ...scenarios.flatMap((scenario) => scenario.remaining.map((item) => `${scenario.id}: ${item}`)),
  ];
  const checks = buildChecks(markerAutomation, scenarios, nativeLibraryHashes, symbolization, files);
  const policyViolations = checks.filter((check) => !check.passed).map((check) => check.message);
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  const output = {
    schema: "autojs6-node-phase13-crash-evidence-automation-v1",
    generatedAt: new Date().toISOString(),
    gate: "collectNodePhase13CrashEvidence",
    task: "P13-05",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    blockerRegistry: blockerEntry,
    markerAutomation,
    scenarios,
    requiredMarkerFields: REQUIRED_MARKER_FIELDS,
    featureFlags,
    nativeLibraryHashes,
    symbolization,
    bundle: {
      directory: relativePath(repoRoot, bundleDir),
      manifest: relativePath(repoRoot, path.join(bundleDir, "manifest.json")),
    },
    checks,
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : finalBlockerClosable ? "ready" : "partial",
      phase13TaskComplete: finalBlockerClosable,
      finalBlockerClosable,
      automaticMarkerReady: markerAutomation.automaticMarkerReady,
      nextLaunchCollectorReady: markerAutomation.nextLaunchCollectorReady,
      safeNodeProfilePreserved: true,
    },
  };
  return {
    output,
    bundleManifest: {
      schema: "autojs6-node-phase13-crash-evidence-bundle-v1",
      generatedAt: output.generatedAt,
      generatedBy: output.gate,
      decision: output.decision,
      runtimeDescriptor: {
        runtimeSlot: "node24_5",
        profile: "AutoJs6 Safe Node Profile",
        nodeHeaderVersion: "24.5.0",
      },
      crashMarker: markerAutomation.crashMarker,
      nextLaunchCollection: markerAutomation.nextLaunchCollection,
      requiredMarkerFields: REQUIRED_MARKER_FIELDS,
      nativeLibraryHashes,
      featureFlags,
      symbolization,
      scenarios,
      blockers,
    },
  };
}

function markerAutomationStatus(files) {
  const store = files.crashStore.text;
  const app = files.app.text;
  const embeddedService = files.embeddedService.text;
  const nativeProcessService = files.nativeProcessService.text;
  const markerFieldsPresent = REQUIRED_MARKER_FIELDS.every((field) => store.includes(`"${field}"`));
  const beginCount = countOccurrences(embeddedService + nativeProcessService, "NodeCrashEvidenceStore.begin(");
  const serviceDestroyCount = countOccurrences(embeddedService + nativeProcessService, "NodeCrashEvidenceStore.markServiceDestroy(");
  const blockers = [];
  if (!files.crashStore.exists) blockers.push("runtime crash evidence store source is missing");
  if (!markerFieldsPresent) blockers.push("runtime crash marker schema does not include all P13 metadata fields");
  if (!app.includes("NodeCrashEvidenceStore.collectPending(")) blockers.push("next-launch crash evidence collector is not wired in App startup");
  if (!embeddedService.includes("NodeCrashEvidenceStore.markEmbeddedResult(")) blockers.push("embedded script service does not write terminal crash evidence");
  if (!nativeProcessService.includes("NodeCrashEvidenceStore.markNativeProcessExit(")) blockers.push("native process service does not write terminal crash evidence");
  return {
    automaticMarkerReady: files.crashStore.exists && markerFieldsPresent && beginCount >= 2,
    nextLaunchCollectorReady: app.includes("NodeCrashEvidenceStore.collectPending("),
    serviceLifecycleReady: serviceDestroyCount >= 2,
    crashMarker: {
      implemented: files.crashStore.exists && markerFieldsPresent,
      schema: "autojs6-node-phase13-crash-marker-v1",
      runtimeJsonMarker: true,
      signalSafeNativeMarker: store.includes("safeSignalMarker") && store.includes("native signal-safe writer remains separate") ? false : null,
      source: files.crashStore.relative,
      requiredFieldsPresent: markerFieldsPresent,
      beginCallSites: beginCount,
    },
    nextLaunchCollection: {
      implemented: app.includes("NodeCrashEvidenceStore.collectPending(") && store.includes("NEXT_LAUNCH_BUNDLE_SCHEMA"),
      source: files.app.relative,
      bundleSchema: "autojs6-node-phase13-next-launch-crash-bundle-v1",
    },
    terminalWriters: {
      embeddedScriptService: embeddedService.includes("NodeCrashEvidenceStore.markEmbeddedResult("),
      nativeProcessService: nativeProcessService.includes("NodeCrashEvidenceStore.markNativeProcessExit("),
      serviceDestroyCallSites: serviceDestroyCount,
      timeoutWriter: embeddedService.includes("NodeCrashEvidenceStore.markTimeout("),
    },
    blockers,
  };
}

function scenarioStatus(files) {
  const embeddedService = files.embeddedService.text;
  const nativeProcessService = files.nativeProcessService.text;
  const embeddedBridge = files.embeddedBridge.text;
  const crashEvidenceStoreTest = files.crashEvidenceStoreTest.text;
  const androidRuntimePolicyTest = files.androidRuntimePolicyTest.text;
  const embeddedMvpTest = files.embeddedMvpTest.text;
  const nativeFatalRunningMarkerReady = crashEvidenceStoreTest.includes("collectsRunningMarkerAfterSimulatedNativeFatalProcessDeath") &&
    crashEvidenceStoreTest.includes("native_fatal_running_marker") &&
    crashEvidenceStoreTest.includes("STATE_COLLECTED_AFTER_RESTART");
  const packagedMarkerContractReady = crashEvidenceStoreTest.includes("markerRecordsPackagedLaunchSurfaceForFutureCrashSmoke") &&
    crashEvidenceStoreTest.includes("LAUNCH_SURFACE_PACKAGED_LONG_RUNNING") &&
    crashEvidenceStoreTest.includes("packaged_apk_crash_smoke_contract");
  const signalSafeBoundaryReady = files.crashStore.text.includes("safeSignalMarker") &&
    crashEvidenceStoreTest.includes("markerRecordsSignalSafeNativeMarkerBoundary") &&
    crashEvidenceStoreTest.includes("native signal-safe writer remains separate");
  const bridgeProviderCrashReady = embeddedBridge.includes("PROBE_LEVEL_PLUGIN_CRASH") &&
    embeddedBridge.includes("plugin_crash.has_crash_event") &&
    crashEvidenceStoreTest.includes("markerRecordsBridgeProviderCrashBundleFields") &&
    crashEvidenceStoreTest.includes("plugin_crash.has_crash_event") &&
    crashEvidenceStoreTest.includes("NodeCrashEvidenceStore.collectPending(");
  return [
    scenario(
      "native_crash",
      nativeProcessService.includes("NodeCrashEvidenceStore.begin(") &&
        embeddedService.includes("NodeCrashEvidenceStore.begin(") &&
        nativeFatalRunningMarkerReady,
      [
        files.nativeProcessService.relative,
        files.embeddedService.relative,
        files.crashStore.relative,
        files.crashEvidenceStoreTest.relative,
      ],
      ["real fatal native chaos injection evidence is still required"],
    ),
    scenario(
      "bridge_provider_crash",
      bridgeProviderCrashReady,
      [files.embeddedBridge.relative, files.crashEvidenceStoreTest.relative],
      [],
    ),
    scenario(
      "timeout_kill",
      embeddedService.includes("NodeCrashEvidenceStore.markTimeout(") &&
        androidRuntimePolicyTest.includes("foreground service timeout") &&
        embeddedMvpTest.includes("Embedded Node.js script timed out"),
      [
        files.embeddedService.relative,
        files.androidRuntimePolicyTest.relative,
        files.embeddedMvpTest.relative,
      ],
      [],
    ),
    scenario(
      "service_destroy",
      embeddedService.includes("embedded_script_service.onDestroy") &&
        nativeProcessService.includes("native_process_service.onDestroy"),
      [files.embeddedService.relative, files.nativeProcessService.relative],
      [],
    ),
    scenario(
      "packaged_apk",
      packagedMarkerContractReady,
      [files.crashEvidenceStoreTest.relative],
      ["real packaged APK crash evidence smoke is still required"],
    ),
    scenario(
      "signal_safe_marker",
      signalSafeBoundaryReady,
      [files.crashStore.relative, files.crashEvidenceStoreTest.relative, files.phase11Collect.relative],
      ["native signal-safe fixed-size marker writer proof is still required"],
    ),
  ];
}

function scenario(id, implemented, evidence, remaining) {
  return {
    id,
    status: implemented ? (remaining.length === 0 ? "ready" : "partial") : "missing",
    evidence,
    skipReason: "",
    remaining: implemented ? remaining : [`${id} evidence is not wired`],
  };
}

function buildChecks(markerAutomation, scenarios, nativeLibraryHashes, symbolization, files) {
  return [
    check(
      "runtime-marker-store",
      markerAutomation.crashMarker.implemented,
      "P13-05 requires a runtime crash marker writer/reader schema.",
    ),
    check(
      "next-launch-collector",
      markerAutomation.nextLaunchCollectorReady,
      "P13-05 requires next-launch crash evidence collection.",
    ),
    check(
      "embedded-and-native-writers",
      markerAutomation.terminalWriters.embeddedScriptService && markerAutomation.terminalWriters.nativeProcessService,
      "Embedded script and native process services must both write terminal crash evidence.",
    ),
    check(
      "metadata-fields",
      markerAutomation.crashMarker.requiredFieldsPresent,
      "Crash evidence markers must include execution id, runtime slot, ABI, profile, feature flags, resource snapshot, and timeline tail.",
    ),
    check(
      "native-hashes",
      nativeLibraryHashes.length >= 3,
      "Crash evidence bundle should include libnode.so hashes for the required ABIs.",
    ),
    check(
      "symbolization-tooling",
      symbolization.implemented,
      "Phase 11 crash bundle collection, verification, and symbolization tooling must remain available.",
    ),
    check(
      "packaged-path-visible",
      scenarios.some((item) => item.id === "packaged_apk" && (item.status !== "missing" || item.skipReason)),
      "Packaged APK path must have equivalent crash evidence, a marker contract, or an explicit skip reason.",
    ),
    check(
      "signal-safe-boundary-visible",
      scenarios.some((item) => item.id === "signal_safe_marker" && item.status !== "missing"),
      "P13-05 must record the current signal-safe marker boundary until a native fixed-size writer lands.",
    ),
    check(
      "task-checklist-visible",
      files.phase13Tasks.text.includes("P13-05") && files.phase13Tasks.text.includes("collect-crash-evidence.js"),
      "Phase 13 checklist must reference the P13-05 crash evidence collector.",
    ),
  ];
}

function collectNativeLibraryHashes(repoRoot) {
  const root = path.join(repoRoot, "app/src/main/jniLibs");
  if (!fs.existsSync(root)) return [];
  const entries = [];
  for (const abi of fs.readdirSync(root).sort()) {
    const lib = path.join(root, abi, "libnode.so");
    if (!fs.existsSync(lib)) continue;
    entries.push({
      abi,
      library: "libnode.so",
      sha256: sha256(lib),
      path: relativePath(repoRoot, lib),
    });
  }
  return entries;
}

function parseFeatureFlags(text) {
  const flags = [];
  const pattern = /NodeRuntimeFeatureFlag\(\s*id\s*=\s*"([^"]+)",\s*diagnosticName\s*=\s*"([^"]+)",\s*gradleProperty\s*=\s*([^,\n]+),\s*defaultEnabled\s*=\s*([^,\n]+),\s*buildTimeOverrideAllowed\s*=\s*(true|false),\s*projectJsonOverrideAllowed\s*=\s*(true|false),\s*packagedApkOverrideAllowed\s*=\s*(true|false),\s*securityLevel\s*=\s*"([^"]+)",\s*status\s*=\s*"([^"]+)"/g;
  let match;
  while ((match = pattern.exec(text)) !== null) {
    flags.push({
      id: match[1],
      diagnosticName: match[2],
      defaultEnabled: parseBooleanExpression(match[4].trim(), text),
      securityLevel: match[8],
      status: match[9],
    });
  }
  return flags;
}

function blocker(text, id) {
  try {
    const registry = JSON.parse(text);
    const entry = (registry.blockers || []).find((item) => item.id === id);
    return entry ? {
      id: entry.id,
      status: entry.status,
      classification: entry.classification,
      currentEvidence: entry.currentEvidence,
      requiredResolution: entry.requiredResolution,
    } : null;
  } catch (_) {
    return null;
  }
}

function parseBooleanExpression(value, text) {
  if (value === "true") return true;
  if (value === "false") return false;
  const constName = value.match(/^([A-Z0-9_]+)$/);
  if (constName) {
    const match = text.match(new RegExp(`const val ${constName[1]}\\s*=\\s*(true|false)`));
    if (match) return match[1] === "true";
  }
  return null;
}

function renderBundleReadme(report) {
  return [
    "# Phase 13 Crash Evidence Bundle",
    "",
    "Generated by `collectNodePhase13CrashEvidence`.",
    "",
    `- Decision: ${report.output.decision.status}`,
    `- Final blocker closable: ${report.output.decision.finalBlockerClosable}`,
    `- Blockers: ${report.output.blockers.length}`,
    `- Policy violations: ${report.output.policyViolations.length}`,
    "",
  ].join("\n");
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Crash Evidence Automation",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Final blocker closable: ${report.decision.finalBlockerClosable}`,
    `- Runtime marker ready: ${report.decision.automaticMarkerReady}`,
    `- Next-launch collector ready: ${report.decision.nextLaunchCollectorReady}`,
    `- Native library hashes: ${report.nativeLibraryHashes.length}`,
    `- Blockers: ${report.blockers.length}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Scenarios",
    "",
    "| Scenario | Status | Evidence | Remaining / Skip |",
    "| --- | --- | --- | --- |",
  ];
  report.scenarios.forEach((scenario) => {
    const evidence = scenario.evidence.length > 0
      ? scenario.evidence.map((item) => `\`${item}\``).join("<br>")
      : "-";
    const remaining = scenario.skipReason ||
      (scenario.remaining.length > 0 ? scenario.remaining.join("<br>") : "-");
    lines.push(`| \`${scenario.id}\` | \`${scenario.status}\` | ${evidence} | ${escapeTable(remaining)} |`);
  });
  lines.push("", "## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
  report.checks.forEach((item) => {
    lines.push(`| \`${item.id}\` | ${item.passed ? "passed" : "failed"} | ${escapeTable(item.message)} |`);
  });
  lines.push("", "## Blockers", "");
  if (report.blockers.length === 0) {
    lines.push("None.");
  } else {
    report.blockers.forEach((item) => lines.push(`- ${item}`));
  }
  return lines.join("\n") + "\n";
}

function countOccurrences(text, needle) {
  return text.split(needle).length - 1;
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function sha256(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function relativePath(root, file) {
  return path.relative(root, file).replace(/\\/g, "/");
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

function read(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "") : "";
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-blocked"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node collect-crash-evidence.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
      process.exit(0);
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const eq = arg.indexOf("=");
    const rawKey = eq >= 0 ? arg.slice(2, eq) : arg.slice(2);
    const key = rawKey.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
    if (booleanArgs.has(rawKey)) {
      options[key] = eq >= 0 ? parseBoolean(arg.slice(eq + 1)) : true;
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

function parseBoolean(value) {
  return ["1", "true", "yes", "on"].includes(String(value).toLowerCase());
}

main();
