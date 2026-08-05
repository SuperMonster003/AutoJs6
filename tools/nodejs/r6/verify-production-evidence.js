#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const SCHEMA = "autojs6-node-r6-production-evidence-gate-v1";
const DEVICE_SCHEMA = "autojs6-node-r6-device-evidence-v2";
const SOAK_SCHEMA = "autojs6-node-r6-persistent-plugin-soak-v1";
const REPORT_BASENAME = "r6-production-evidence-gate";
const LIFECYCLE_MODEL = "persistent_process_global_runtime";
const DEFAULT_RELEASE_FLAVOR = "nodePlugin";
const DEFAULT_BACKEND = "plugin";
const DEFAULT_RUNTIME_SLOT = "node24_5";
const DEFAULT_NODE_VERSION = "24.5.0";
const RUNTIME_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.nodejs";
const RUNTIME_PLUGIN_SERVICE = `${RUNTIME_PLUGIN_PACKAGE}.NodeJsRuntimePluginService`;
const APK_BUILDER_PLUGIN_PACKAGE = "org.autojs.plugin.apkbuilder.template";
const APK_BUILDER_PLUGIN_TASK = ":plugins:apk-builder-template:packageRelease";
const APK_BUILDER_PLUGIN_ACTION = "org.autojs.plugin.APK_BUILDER";
const APK_BUILDER_PLUGIN_SERVICE =
  "org.autojs.plugin.apkbuilder.template.impl.ApkBuilderTemplatePluginService";
const APK_BUILDER_PLUGIN_COMPONENT =
  `${APK_BUILDER_PLUGIN_PACKAGE}/${APK_BUILDER_PLUGIN_SERVICE}`;
const PACKAGED_TEMPLATE_PACKAGE = "org.autojs.autojs6.inrt";
const PACKAGED_TEMPLATE_TASK = ":app:packageInrtDebug";
const PACKAGED_TEMPLATE_DEVICE_PATH_PREFIX = "/data/local/tmp/autojs6-r6-packaged-template-";
const PACKAGED_TEMPLATE_OVERRIDE_ARGUMENT = "autojs.embeddedNode.packagedSmokeTemplateOverride";
const PACKAGED_TEMPLATE_EXACT_PATH_ARGUMENT = "autojs.embeddedNode.packagedSmokeTemplateExactPath";
const PACKAGED_TEMPLATE_TARGET_ABI_ARGUMENT = "autojs.embeddedNode.packagedSmokeTargetAbi";
const R5_PLUGIN_PREFLIGHT_SELECTOR =
  "org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_01_livePluginPreflightNegotiatesContractAndReportsTrustIdentity";
const R6_PLUGIN_SOAK_SELECTOR =
  "org.autojs.autojs.engine.NodeR6PersistentPluginSoakInstrumentationTest#persistentRequiredPluginSoakProducesR6Evidence";
const SHA256_PATTERN = /^[0-9a-f]{64}$/i;
const RUN_TOKEN_PATTERN = /^[0-9a-f]{64}$/i;
const MILLIS_PER_HOUR = 3_600_000;
const MAX_PRODUCTION_SAMPLE_INTERVAL_MS = 300_000;
const MAX_SOAK_WAKE_LOCK_GRACE_MS = 15 * 60_000;
const MAX_SOAK_SUSPEND_MS = 1_000;
const REMOTE_PROC_FALLBACK_CLOCK_TOLERANCE_MS = 30_000;
const REQUIRED_SOAK_CYCLE_TIMEOUT_MS = 15_000;
const SOAK_CLOCK_TOLERANCE_MS = 120_000;
const MIN_GROWTH_SAMPLES = 4;
const GROWTH_METRICS = [
  "rssKb",
  "javaHeapKb",
  "nativeHeapKb",
  "threadCount",
  "fdCount",
  "nodeHeapUsedKb",
  "libuvHandleCount",
  "activeBridgeResourceCount",
];
const GROWTH_THRESHOLDS = {
  rssKb: 262_144,
  javaHeapKb: 131_072,
  nativeHeapKb: 131_072,
  threadCount: 24,
  fdCount: 64,
  nodeHeapUsedKb: 131_072,
  libuvHandleCount: 8,
  activeBridgeResourceCount: 0,
};

const DEVICE_REQUIREMENTS = [
  {
    id: "sony-arm64",
    role: "primary_oem",
    relative: "device/sony-arm64.json",
    runtimeProcessAbi: "arm64-v8a",
    emulator: false,
    manufacturer: "sony",
  },
  {
    id: "sony-armeabi-v7a",
    role: "required_32bit_abi",
    relative: "device/sony-armeabi-v7a.json",
    runtimeProcessAbi: "armeabi-v7a",
    emulator: false,
    manufacturer: "sony",
  },
  {
    id: "second-oem-arm64",
    role: "secondary_oem",
    relative: "device/second-oem-arm64.json",
    runtimeProcessAbi: "arm64-v8a",
    emulator: false,
    manufacturerNot: "sony",
  },
  {
    id: "emulator-x86_64",
    role: "emulator",
    relative: "device/emulator-x86_64.json",
    runtimeProcessAbi: "x86_64",
    emulator: true,
    avdName: "AVD_API_37",
    apiLevel: 37,
    pageSizeBytes: 16_384,
  },
];

const SOAK_REQUIREMENTS = [
  {
    id: "quick-soak-8h",
    kind: "quick",
    relative: "soak/quick-soak-8h.json",
    minDurationHours: 8,
  },
  {
    id: "final-soak-24h",
    kind: "final",
    relative: "soak/final-soak-24h.json",
    minDurationHours: 24,
  },
];

const REQUIRED_DEVICE_SELECTORS = [
  "org.autojs.autojs.engine.NodeR6EngineSwitchInstrumentationTest#r6_01_requiredPluginNodeAndRhinoAlternateAcrossMultipleRounds",
  "org.autojs.autojs.engine.NodeR6EngineSwitchInstrumentationTest#r6_02_pluginRhinoCompatibilityPluginSwitchExecutesAndRecovers",
  "org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_01_livePluginPreflightNegotiatesContractAndReportsTrustIdentity",
  "org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_02_candidateAndBinderCachesReusePersistentRuntimeWithFreshExecutionState",
  "org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_04_concurrentPluginCallsApplySingleActiveBackpressureWithoutQueueing",
  "org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_06_hostRejectsContractVersionAndMandatoryCapabilityMismatch",
  "org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_07_binderDeathReconnectsAtomicallyAndPreservesCrashAttribution",
  "org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_08_runtimeWarmSamplesMeetR2SloAndSeparateDiscoveryAndBindOverhead",
  "org.autojs.autojs.engine.NodeR6RequiredPluginSecurityInstrumentationTest#requiredPluginPreservesPermanentSafeNodeDenials",
  "org.autojs.autojs.engine.NodeR6PersistentPluginSoakInstrumentationTest#persistentRequiredPluginSoakProducesR6Evidence",
  "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunFullCompatibilityEmbeddedNodePackagedApk",
  "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunCrashEvidenceEmbeddedNodePackagedApk",
  "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunRuntimeCompatibilityDescriptorMismatchEmbeddedNodePackagedApk",
];

const REQUIRED_REGRESSIONS = [
  "rhinoNodeAlternating",
  "backendSwitch",
  "persistentRuntimeIsolation",
  "pluginBinderDeathRecovery",
  "packagedLifecycle",
  "crashRecovery",
  "memoryGrowth",
  "permanentDenials",
];

const REQUIRED_ARTIFACT_HASHES = [
  "hostApkSha256",
  "androidTestApkSha256",
  "pluginApkSha256",
  "junitXmlSha256",
];
const REQUIRED_DEVICE_ARTIFACT_HASHES = [
  ...REQUIRED_ARTIFACT_HASHES,
  "apkBuilderPluginApkSha256",
  "apkBuilderPluginApkBeforeTestSha256",
  "apkBuilderPluginBuildApkSha256",
  "packagedTemplateApkSha256",
];

const REQUIRED_SOAK_METRICS = [
  "servicePid",
  "pluginSessionGeneration",
  "processRuntimeGeneration",
  "executionSequence",
  "rssKb",
  "javaHeapKb",
  "nativeHeapKb",
  "nativeHeapCommittedKb",
  "nativePssKb",
  "threadCount",
  "fdCount",
  "nodeHeapUsedKb",
  "libuvHandleCount",
  "activeBridgeResourceCount",
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const pluginRoot = resolvePluginRoot(repoRoot, options.pluginRoot);
  const evidenceRoot = path.resolve(
    options.evidenceRoot || path.join(repoRoot, "build/reports/nodejs/r6-evidence"),
  );
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const identity = currentIdentity(repoRoot, pluginRoot);
  const deviceReports = DEVICE_REQUIREMENTS.map((requirement) =>
    evaluateDeviceReport(evidenceRoot, requirement, identity, repoRoot));
  const soakReports = SOAK_REQUIREMENTS.map((requirement) =>
    evaluateSoakReport(evidenceRoot, requirement, identity, repoRoot));
  const matrixBlockers = evaluateMatrix(deviceReports);
  const capturedReports = [...deviceReports, ...soakReports];
  const currentSourceIdentityMatched = identity.blockers.length === 0 && capturedReports.every((entry) =>
    entry.available && !entry.blockers.some((message) =>
      message.startsWith("identity ") || message.includes("worktree was not clean"),
    ),
  );
  const blockers = [
    ...identity.blockers,
    ...deviceReports.flatMap((entry) => entry.blockers.map((message) => `${entry.id}: ${message}`)),
    ...soakReports.flatMap((entry) => entry.blockers.map((message) => `${entry.id}: ${message}`)),
    ...matrixBlockers,
  ];
  const policyViolations = validateContractAnchors(repoRoot);
  const status = policyViolations.length > 0 ? "failed" : blockers.length > 0 ? "blocked" : "completed";
  const report = {
    schema: SCHEMA,
    generatedAt: new Date().toISOString(),
    gate: options.failOnBlocked
      ? "verifyNodeR6ProductionEvidenceGate"
      : "generateNodeR6ProductionEvidenceStatus",
    lifecycleModel: LIFECYCLE_MODEL,
    distribution: {
      releaseFlavor: DEFAULT_RELEASE_FLAVOR,
      backend: DEFAULT_BACKEND,
      pluginRequired: true,
      runtimeSlot: DEFAULT_RUNTIME_SLOT,
      nodeVersion: DEFAULT_NODE_VERSION,
    },
    identity,
    evidenceRoot: relativePath(repoRoot, evidenceRoot),
    requirements: {
      devices: DEVICE_REQUIREMENTS,
      soaks: SOAK_REQUIREMENTS,
      deviceSelectors: REQUIRED_DEVICE_SELECTORS,
      regressions: REQUIRED_REGRESSIONS,
      artifactHashes: REQUIRED_DEVICE_ARTIFACT_HASHES,
      soakArtifactHashes: REQUIRED_ARTIFACT_HASHES,
      soakMetrics: REQUIRED_SOAK_METRICS,
    },
    devices: deviceReports,
    soaks: soakReports,
    blockers,
    policyViolations,
    decision: {
      status,
      completed: status === "completed",
      currentSourceIdentityMatched,
      deviceMatrixReady: deviceReports.every((entry) => entry.ready) && matrixBlockers.length === 0,
      soakReady: soakReports.every((entry) => entry.ready),
      zeroSkippedTests: deviceReports.every((entry) =>
        entry.available && entry.tests.total > 0 && entry.tests.skipped === 0,
      ),
      safeNodeProfilePreserved: deviceReports.every((entry) => entry.ready),
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log(`R6 production evidence decision: ${status}`);
  console.log(`R6 production evidence blockers: ${blockers.length}`);
  console.log(`R6 production evidence policy violations: ${policyViolations.length}`);
  if (policyViolations.length > 0 || (options.failOnBlocked && status !== "completed")) {
    process.exitCode = 1;
  }
}

function evaluateDeviceReport(evidenceRoot, requirement, identity, repoRoot = null) {
  const file = path.join(evidenceRoot, requirement.relative);
  const json = readJsonIfExists(file);
  const blockers = [];
  if (!json) {
    blockers.push(`missing current device evidence at ${requirement.relative}`);
    return emptyDeviceResult(requirement, file, blockers);
  }
  if (json.schema !== DEVICE_SCHEMA) blockers.push(`schema must be ${DEVICE_SCHEMA}`);
  if (json.evidenceTarget !== requirement.id) blockers.push(`evidenceTarget must be ${requirement.id}`);
  if (json.status !== "passed") blockers.push(`status must be passed, got ${stringValue(json.status)}`);
  if (!Number.isFinite(Date.parse(stringValue(json.generatedAt)))) blockers.push("generatedAt must be a valid ISO timestamp");
  if (!RUN_TOKEN_PATTERN.test(stringValue(json.runToken))) {
    blockers.push("runToken must be exactly 64 hexadecimal characters");
  }
  validateCapturedIdentity(json.identity, identity, blockers);
  const device = json.device || {};
  validateDevice(device, requirement, blockers);
  const plugin = json.plugin || {};
  const apkBuilderPlugin = json.apkBuilderPlugin || {};
  const artifactFiles = validateArtifacts(json.artifacts, repoRoot, blockers, {
    reportType: "device",
    runToken: stringValue(json.runToken),
    hostProcessAbi: stringValue(device.hostProcessAbi),
    runtimeProcessAbi: requirement.runtimeProcessAbi,
    reportedPluginSignerSha256: stringValue(plugin.signerSha256),
    reportedApkBuilderPluginSignerSha256: stringValue(apkBuilderPlugin.signerSha256),
  });
  const tests = normalizeTests(json.tests);
  validateTestCounts(json.tests, tests, blockers);
  validateDeviceJUnitArtifact(artifactFiles.junitXmlPath, tests, blockers);
  validateDevicePluginProvenance(plugin, requirement, tests, json.runToken, repoRoot, blockers);
  validateApkBuilderPluginProvenance(
    apkBuilderPlugin,
    json.artifacts,
    json.identity,
    blockers,
  );
  if (tests.total !== REQUIRED_DEVICE_SELECTORS.length) {
    blockers.push(`test total must equal the fixed R6 selector count ${REQUIRED_DEVICE_SELECTORS.length}`);
  }
  if (tests.total <= 0) blockers.push("test total must be positive");
  if (tests.failures !== 0) blockers.push(`test failures must be zero, got ${tests.failures}`);
  if (tests.errors !== 0) blockers.push(`test errors must be zero, got ${tests.errors}`);
  if (tests.skipped !== 0) blockers.push(`skipped tests must be zero, got ${tests.skipped}`);
  REQUIRED_DEVICE_SELECTORS.forEach((selector) => {
    const matches = tests.selectors.filter((entry) => entry.selector === selector);
    if (matches.length === 0) {
      blockers.push(`missing required selector evidence: ${selector}`);
    } else if (matches.some((entry) => entry.status !== "passed")) {
      blockers.push(`required selector did not pass: ${selector}`);
    }
  });
  for (const surface of ["mainApp", "packagedApk"]) {
    if (!json.surfaces || json.surfaces[surface] !== "passed") {
      blockers.push(`${surface} surface must be passed`);
    }
  }
  REQUIRED_REGRESSIONS.forEach((id) => {
    if (!json.regressions || json.regressions[id] !== "passed") {
      blockers.push(`regression ${id} must be passed`);
    }
  });
  const lifecycle = json.lifecycle || {};
  if (lifecycle.persistentProcessRuntime !== true) blockers.push("persistent process runtime was not proven");
  if (lifecycle.processRuntimeInitCount !== 1) blockers.push("process runtime init count must be 1");
  if (lifecycle.runtimeGenerationStable !== true) blockers.push("runtime generation stability was not proven");
  if (lifecycle.executionSequenceMonotonic !== true) blockers.push("execution sequence monotonicity was not proven");
  if (lifecycle.freshExecutionState !== true) blockers.push("fresh per-execution state was not proven");
  if (lifecycle.recoveryAfterProcessDeath !== true) blockers.push("process-death recovery was not proven");
  const resources = json.resources || {};
  if (resources.activeBridgeResources !== 0) blockers.push("active bridge resources must finish at zero");
  if (resources.orphanProcesses !== 0) blockers.push("orphan processes must finish at zero");
  if (resources.unrecoverableSlots !== 0) blockers.push("unrecoverable slots must finish at zero");
  return {
    id: requirement.id,
    role: requirement.role,
    path: normalizeEvidencePath(requirement.relative),
    available: true,
    sha256: sha256(file),
    generatedAt: stringValue(json.generatedAt),
    device: json.device || {},
    identity: json.identity || {},
    tests,
    blockers,
    ready: blockers.length === 0,
  };
}

function validateDevicePluginProvenance(plugin, requirement, tests, runToken, repoRoot, blockers) {
  const packageName = stringValue(plugin.packageName);
  const serviceClass = stringValue(plugin.serviceClass);
  const signerSha256 = stringValue(plugin.signerSha256).toLowerCase();
  const authorizationState = stringValue(plugin.authorizationState);
  if (packageName !== RUNTIME_PLUGIN_PACKAGE) {
    blockers.push(`device plugin packageName must be ${RUNTIME_PLUGIN_PACKAGE}`);
  }
  if (serviceClass !== RUNTIME_PLUGIN_SERVICE) {
    blockers.push(`device plugin serviceClass must be ${RUNTIME_PLUGIN_SERVICE}`);
  }
  if (!SHA256_PATTERN.test(signerSha256)) blockers.push("device plugin signerSha256 is invalid");
  if (!["official", "trusted", "user_granted"].includes(authorizationState)) {
    blockers.push("device plugin authorizationState is not trusted");
  }
  for (const flag of ["required", "authenticated", "trusted"]) {
    if (plugin[flag] !== true) blockers.push(`device plugin ${flag} must be true`);
  }
  if (stringValue(plugin.runtimeSlot) !== DEFAULT_RUNTIME_SLOT) {
    blockers.push(`device plugin runtimeSlot must be ${DEFAULT_RUNTIME_SLOT}`);
  }
  if (stringValue(plugin.nodeVersion).replace(/^v/, "") !== DEFAULT_NODE_VERSION) {
    blockers.push(`device plugin nodeVersion must be ${DEFAULT_NODE_VERSION}`);
  }
  if (stringValue(plugin.runtimeProcessAbi) !== requirement.runtimeProcessAbi) {
    blockers.push(`device plugin runtimeProcessAbi must be ${requirement.runtimeProcessAbi}`);
  }

  const evidence = plugin.evidence || {};
  if (stringValue(evidence.requiredModeSelector) !== R6_PLUGIN_SOAK_SELECTOR) {
    blockers.push("device plugin required-mode selector provenance is invalid");
  }
  if (stringValue(evidence.trustIdentitySelector) !== R5_PLUGIN_PREFLIGHT_SELECTOR) {
    blockers.push("device plugin trust-identity selector provenance is invalid");
  }
  for (const selector of [R5_PLUGIN_PREFLIGHT_SELECTOR, R6_PLUGIN_SOAK_SELECTOR]) {
    const matches = tests.selectors.filter((entry) => entry.selector === selector && entry.status === "passed");
    if (matches.length !== 1) blockers.push(`device plugin provenance selector must pass exactly once: ${selector}`);
  }
  if (stringValue(evidence.requestedApkSignerSha256).toLowerCase() !== signerSha256) {
    blockers.push("device plugin requested APK signer provenance does not match signerSha256");
  }
  if (stringValue(evidence.installedApkSignerSha256).toLowerCase() !== signerSha256) {
    blockers.push("device plugin installed APK signer provenance does not match signerSha256");
  }
  validateDevicePluginPreflightArtifact(
    evidence.instrumentationReport,
    repoRoot,
    runToken,
    { packageName, serviceClass, signerSha256, authorizationState },
    blockers,
  );
}

function validateDevicePluginPreflightArtifact(record, repoRoot, runToken, plugin, blockers) {
  const artifactPath = stringValue(record && record.path);
  const expectedSha256 = stringValue(record && record.sha256).toLowerCase();
  const expectedBytes = Number(record && record.sizeBytes);
  if (!artifactPath || !SHA256_PATTERN.test(expectedSha256) || !Number.isInteger(expectedBytes) || expectedBytes <= 0) {
    blockers.push("device plugin instrumentation preflight artifact record is invalid");
    return;
  }
  if (!repoRoot) return;
  const resolved = path.resolve(repoRoot, artifactPath);
  if (!isInsideOrSame(path.resolve(repoRoot), resolved)) {
    blockers.push("device plugin instrumentation preflight artifact escapes the repository root");
    return;
  }
  if (!fs.existsSync(resolved) || !fs.statSync(resolved).isFile()) {
    blockers.push("device plugin instrumentation preflight artifact does not exist");
    return;
  }
  if (fs.statSync(resolved).size !== expectedBytes || sha256(resolved) !== expectedSha256) {
    blockers.push("device plugin instrumentation preflight artifact hash or size does not match");
    return;
  }
  const report = readJsonIfExists(resolved);
  const reportPlugin = report && report.plugin ? report.plugin : {};
  if (!report || report.schema !== SOAK_SCHEMA || report.kind !== "smoke" || report.status !== "passed" ||
      report.runToken !== runToken || !Array.isArray(report.failures) || report.failures.length !== 0) {
    blockers.push("device plugin instrumentation preflight report contract is invalid");
    return;
  }
  const reportService = stringValue(reportPlugin.serviceClass || reportPlugin.serviceName);
  if (stringValue(reportPlugin.packageName) !== plugin.packageName || reportService !== plugin.serviceClass ||
      stringValue(reportPlugin.signerSha256).toLowerCase() !== plugin.signerSha256 ||
      stringValue(reportPlugin.authorizationState) !== plugin.authorizationState) {
    blockers.push("device plugin instrumentation preflight report identity does not match canonical provenance");
  }
}

function evaluateSoakReport(evidenceRoot, requirement, identity, repoRoot = null) {
  const file = path.join(evidenceRoot, requirement.relative);
  const json = readJsonIfExists(file);
  const blockers = [];
  if (!json) {
    blockers.push(`missing current persistent-plugin soak evidence at ${requirement.relative}`);
    return {
      id: requirement.id,
      kind: requirement.kind,
      path: normalizeEvidencePath(requirement.relative),
      available: false,
      ready: false,
      durationHours: 0,
      samples: 0,
      blockers,
    };
  }
  if (json.schema !== SOAK_SCHEMA) blockers.push(`schema must be ${SOAK_SCHEMA}`);
  if (json.kind !== requirement.kind) blockers.push(`kind must be ${requirement.kind}`);
  if (json.status !== "passed") blockers.push(`status must be passed, got ${stringValue(json.status)}`);
  validateCapturedIdentity(json.identity, identity, blockers);
  const plugin = json.plugin || {};
  const artifactFiles = validateArtifacts(json.artifacts, repoRoot, blockers, {
    reportType: "soak",
    runtimeProcessAbi: stringValue(plugin.runtimeProcessAbi),
    reportedPluginSignerSha256: stringValue(plugin.signerSha256),
  });
  validateSoakJUnitArtifact(artifactFiles.junitXmlPath, blockers);
  const minimumDurationMs = requirement.minDurationHours * MILLIS_PER_HOUR;
  const durationHours = json.durationHours;
  const durationMs = json.durationMs;
  if (!Number.isFinite(durationHours)) {
    blockers.push("durationHours must be a finite number");
  } else if (durationHours < requirement.minDurationHours) {
    blockers.push(`duration ${durationHours}h is below ${requirement.minDurationHours}h`);
  }
  if (!Number.isFinite(durationMs)) {
    blockers.push("durationMs must be a finite number");
  } else if (durationMs < minimumDurationMs) {
    blockers.push(`durationMs ${durationMs} is below ${minimumDurationMs}`);
  }
  if (Number.isFinite(durationHours) &&
      Number.isFinite(durationMs) &&
      Math.abs(durationHours - durationMs / MILLIS_PER_HOUR) > 1 / MILLIS_PER_HOUR) {
    blockers.push("durationHours does not match durationMs");
  }
  const startedAtEpochMs = json.startedAtEpochMs;
  const endedAtEpochMs = json.endedAtEpochMs;
  if (!Number.isFinite(startedAtEpochMs) ||
      !Number.isFinite(endedAtEpochMs) ||
      startedAtEpochMs <= 0 ||
      endedAtEpochMs <= 0 ||
      endedAtEpochMs < startedAtEpochMs) {
    blockers.push("startedAtEpochMs and endedAtEpochMs must define a valid wall-clock interval");
  } else if (endedAtEpochMs - startedAtEpochMs < minimumDurationMs) {
    blockers.push(`wall-clock interval ${endedAtEpochMs - startedAtEpochMs}ms is below ${minimumDurationMs}ms`);
  }
  const generatedAtEpochMs = Date.parse(stringValue(json.generatedAt));
  if (!Number.isFinite(generatedAtEpochMs)) {
    blockers.push("generatedAt must be a valid ISO timestamp");
  }
  if (!RUN_TOKEN_PATTERN.test(stringValue(json.runToken))) {
    blockers.push("runToken must be exactly 64 hexadecimal characters");
  }
  if (stringValue(plugin.runtimeSlot) !== DEFAULT_RUNTIME_SLOT) {
    blockers.push(`plugin runtimeSlot must be ${DEFAULT_RUNTIME_SLOT}`);
  }
  if (stringValue(plugin.nodeVersion).replace(/^v/, "") !== DEFAULT_NODE_VERSION) {
    blockers.push(`plugin Node version must be ${DEFAULT_NODE_VERSION}`);
  }
  if (stringValue(plugin.packageName) !== RUNTIME_PLUGIN_PACKAGE) {
    blockers.push(`plugin packageName must be ${RUNTIME_PLUGIN_PACKAGE}`);
  }
  if (!SHA256_PATTERN.test(stringValue(plugin.signerSha256))) blockers.push("plugin signerSha256 is invalid");
  if (!["official", "trusted", "user_granted"].includes(stringValue(plugin.authorizationState))) {
    blockers.push("plugin authorizationState is not trusted");
  }
  if (!stringValue(plugin.runtimeProcessAbi)) blockers.push("plugin runtimeProcessAbi is missing");
  const configuration = json.configuration || {};
  const sampleIntervalMs = Number(configuration.sampleIntervalMs);
  if (!Number.isInteger(sampleIntervalMs) || sampleIntervalMs < 1_000 || sampleIntervalMs > MAX_PRODUCTION_SAMPLE_INTERVAL_MS) {
    blockers.push(`configuration.sampleIntervalMs must be within 1000..${MAX_PRODUCTION_SAMPLE_INTERVAL_MS}`);
  }
  if (configuration.durationMs !== minimumDurationMs) {
    blockers.push(`configuration.durationMs must be ${minimumDurationMs}`);
  }
  if (configuration.cycleTimeoutMs !== REQUIRED_SOAK_CYCLE_TIMEOUT_MS) {
    blockers.push(`configuration.cycleTimeoutMs must be ${REQUIRED_SOAK_CYCLE_TIMEOUT_MS}`);
  }
  if (configuration.wakeLockPolicy !== "bounded_partial") {
    blockers.push("configuration.wakeLockPolicy must be bounded_partial");
  }
  const wakeLockGraceMs = configuration.wakeLockGraceMs;
  if (!Number.isInteger(wakeLockGraceMs) || wakeLockGraceMs <= 0 || wakeLockGraceMs > MAX_SOAK_WAKE_LOCK_GRACE_MS) {
    blockers.push(`configuration.wakeLockGraceMs must be within 1..${MAX_SOAK_WAKE_LOCK_GRACE_MS}`);
  }
  const wakeLockTimeoutMs = configuration.wakeLockTimeoutMs;
  if (!Number.isInteger(wakeLockTimeoutMs) ||
      wakeLockTimeoutMs !== configuration.durationMs + wakeLockGraceMs) {
    blockers.push("configuration.wakeLockTimeoutMs must equal configuration.durationMs plus wakeLockGraceMs");
  }
  const cycles = numberValue(json.cycles);
  if (!Number.isInteger(cycles) || cycles <= 0) blockers.push("cycles must be a positive integer");
  const runs = Array.isArray(json.runs) ? json.runs : [];
  if (runs.length !== cycles) blockers.push(`runs length ${runs.length} must equal cycles ${cycles}`);
  let stablePid = null;
  let stableSessionGeneration = null;
  let stableRuntimeGeneration = null;
  let previousRunSequence = 0;
  runs.forEach((run, index) => {
    requirePositiveInteger(run, "servicePid", `run ${index}`, blockers);
    requirePositiveInteger(run, "pluginSessionGeneration", `run ${index}`, blockers);
    requirePositiveInteger(run, "processRuntimeGeneration", `run ${index}`, blockers);
    requirePositiveInteger(run, "executionSequence", `run ${index}`, blockers);
    requireNonNegativeNumber(run, "nodeHeapUsedKb", `run ${index}`, blockers);
    requireNonNegativeNumber(run, "libuvHandleCount", `run ${index}`, blockers);
    requireNonNegativeNumber(run, "activeBridgeResourceCount", `run ${index}`, blockers);
    if (Number(run && run.processRuntimeInitCount) !== 1) blockers.push(`run ${index} processRuntimeInitCount must be 1`);
    if (Number(run && run.cycle) !== index + 1) blockers.push(`run ${index} cycle is not contiguous`);
    if (Number(run && run.activeBridgeResourceCount) !== 0) {
      blockers.push(`run ${index} retained bridge resources`);
    }
    if (!run || run.freshExecutionState !== true) {
      blockers.push(`run ${index} did not prove fresh execution state`);
    }
    if (index > 0 && run.runtimeReused !== true) blockers.push(`run ${index} did not reuse the process runtime`);
    const pid = Number(run && run.servicePid);
    const sessionGeneration = Number(run && run.pluginSessionGeneration);
    const runtimeGeneration = Number(run && run.processRuntimeGeneration);
    const sequence = Number(run && run.executionSequence);
    if (index === 0) {
      stablePid = pid;
      stableSessionGeneration = sessionGeneration;
      stableRuntimeGeneration = runtimeGeneration;
    } else {
      if (pid !== stablePid) blockers.push(`run ${index} changed servicePid`);
      if (sessionGeneration !== stableSessionGeneration) blockers.push(`run ${index} changed pluginSessionGeneration`);
      if (runtimeGeneration !== stableRuntimeGeneration) blockers.push(`run ${index} changed processRuntimeGeneration`);
      if (!(sequence > previousRunSequence)) blockers.push(`run ${index} executionSequence is not strictly monotonic`);
    }
    previousRunSequence = sequence;
  });
  const samples = Array.isArray(json.samples) ? json.samples : [];
  const effectiveSampleIntervalMs = Number.isInteger(sampleIntervalMs) && sampleIntervalMs > 0
    ? sampleIntervalMs
    : MAX_PRODUCTION_SAMPLE_INTERVAL_MS;
  const minimumSamples = Math.max(
    MIN_GROWTH_SAMPLES,
    Math.floor(minimumDurationMs / effectiveSampleIntervalMs) + 1,
  );
  if (Number(configuration.minimumRequiredSamples) !== minimumSamples) {
    blockers.push(`configuration.minimumRequiredSamples must be ${minimumSamples}`);
  }
  const livenessCadence = Number(configuration.livenessRecoverabilityEveryCycles);
  if (!Number.isInteger(livenessCadence) || livenessCadence <= 0) {
    blockers.push("configuration.livenessRecoverabilityEveryCycles must be a positive integer");
  }
  if (samples.length < minimumSamples) {
    blockers.push(`at least ${minimumSamples} comparable resource samples are required`);
  }
  let previousCapturedAt = -1;
  let previousElapsedMs = -1;
  let previousSampleSequence = 0;
  let previousSampleCycle = 0;
  samples.forEach((sample, index) => {
    requirePositiveInteger(sample, "servicePid", `sample ${index}`, blockers);
    requirePositiveInteger(sample, "pluginSessionGeneration", `sample ${index}`, blockers);
    requirePositiveInteger(sample, "processRuntimeGeneration", `sample ${index}`, blockers);
    requirePositiveInteger(sample, "executionSequence", `sample ${index}`, blockers);
    for (const metric of [
      "rssKb",
      "javaHeapKb",
      "nativeHeapCommittedKb",
      "nativePssKb",
      "threadCount",
      "fdCount",
      "nodeHeapUsedKb",
    ]) {
      requirePositiveNumber(sample, metric, `sample ${index}`, blockers);
    }
    for (const metric of ["nativeHeapKb", "libuvHandleCount", "activeBridgeResourceCount"]) {
      requireNonNegativeNumber(sample, metric, `sample ${index}`, blockers);
    }
    validateSoakSampleProcEvidence(sample, index, blockers);
    if (stringValue(sample && sample.fdCountSource) !== "plugin_uid_proc_self_fd") {
      blockers.push(`sample ${index} fdCountSource must be plugin_uid_proc_self_fd`);
    }
    if (Number(sample && sample.processRuntimeInitCount) !== 1) blockers.push(`sample ${index} processRuntimeInitCount must be 1`);
    if (Number(sample && sample.activeBridgeResourceCount) !== 0) {
      blockers.push(`sample ${index} retained bridge resources`);
    }
    if (sample && sample.freshExecutionState !== true) blockers.push(`sample ${index} did not prove fresh execution state`);
    if (stablePid != null && Number(sample && sample.servicePid) !== stablePid) blockers.push(`sample ${index} changed servicePid`);
    if (stableSessionGeneration != null && Number(sample && sample.pluginSessionGeneration) !== stableSessionGeneration) {
      blockers.push(`sample ${index} changed pluginSessionGeneration`);
    }
    if (stableRuntimeGeneration != null && Number(sample && sample.processRuntimeGeneration) !== stableRuntimeGeneration) {
      blockers.push(`sample ${index} changed processRuntimeGeneration`);
    }
    const sampleSequence = Number(sample && sample.executionSequence);
    if (index > 0 && !(sampleSequence > previousSampleSequence)) {
      blockers.push(`sample ${index} executionSequence is not strictly monotonic`);
    }
    previousSampleSequence = sampleSequence;
    const sampleCycle = Number(sample && sample.cycle);
    if (!Number.isInteger(sampleCycle) || sampleCycle <= previousSampleCycle || sampleCycle > cycles) {
      blockers.push(`sample ${index} cycle must be strictly increasing within the run`);
    } else {
      const matchingRun = runs[sampleCycle - 1];
      for (const field of [
        "servicePid",
        "pluginSessionGeneration",
        "processRuntimeGeneration",
        "processRuntimeInitCount",
        "executionSequence",
      ]) {
        if (!matchingRun || Number(sample && sample[field]) !== Number(matchingRun[field])) {
          blockers.push(`sample ${index} ${field} does not match cycle ${sampleCycle}`);
        }
      }
    }
    previousSampleCycle = sampleCycle;
    const capturedAtEpochMs = Number(sample && sample.capturedAtEpochMs);
    const elapsedMs = Number(sample && sample.elapsedMs);
    if (!Number.isFinite(capturedAtEpochMs) || capturedAtEpochMs <= 0) {
      blockers.push(`sample ${index} is missing capturedAtEpochMs`);
    } else if (capturedAtEpochMs < previousCapturedAt) {
      blockers.push(`sample ${index} capturedAtEpochMs is not monotonic`);
    } else if (
      capturedAtEpochMs < startedAtEpochMs - SOAK_CLOCK_TOLERANCE_MS ||
      capturedAtEpochMs > endedAtEpochMs + SOAK_CLOCK_TOLERANCE_MS
    ) {
      blockers.push(`sample ${index} capturedAtEpochMs falls outside the soak wall-clock interval`);
    }
    if (!Number.isFinite(elapsedMs) || elapsedMs < 0) {
      blockers.push(`sample ${index} is missing elapsedMs`);
    } else if (elapsedMs < previousElapsedMs) {
      blockers.push(`sample ${index} elapsedMs is not monotonic`);
    } else if (index === 0 && elapsedMs > effectiveSampleIntervalMs + SOAK_CLOCK_TOLERANCE_MS) {
      blockers.push("first resource sample was captured too late");
    } else if (index > 0 && elapsedMs - previousElapsedMs > effectiveSampleIntervalMs + SOAK_CLOCK_TOLERANCE_MS) {
      blockers.push(`sample ${index} exceeds the configured sampling gap`);
    }
    if (
      Number.isFinite(capturedAtEpochMs) && Number.isFinite(elapsedMs) &&
      Math.abs((capturedAtEpochMs - startedAtEpochMs) - elapsedMs) > SOAK_CLOCK_TOLERANCE_MS
    ) {
      blockers.push(`sample ${index} wall-clock and elapsed timestamps disagree`);
    }
    previousCapturedAt = capturedAtEpochMs;
    previousElapsedMs = elapsedMs;
  });
  const finalElapsedMs = samples.reduce(
    (maximum, sample) => Math.max(maximum, Number(sample && sample.elapsedMs) || 0),
    0,
  );
  if (finalElapsedMs < minimumDurationMs) {
    blockers.push(`final sample elapsedMs ${finalElapsedMs} is below ${minimumDurationMs}`);
  }
  const wallDurationMs = endedAtEpochMs - startedAtEpochMs;
  if (startedAtEpochMs > 0 && endedAtEpochMs >= startedAtEpochMs &&
      Math.abs(wallDurationMs - durationMs) > SOAK_CLOCK_TOLERANCE_MS) {
    blockers.push("wall-clock interval does not match durationMs");
  }
  if (Number.isFinite(generatedAtEpochMs) && endedAtEpochMs > 0 &&
      Math.abs(generatedAtEpochMs - endedAtEpochMs) > SOAK_CLOCK_TOLERANCE_MS) {
    blockers.push("generatedAt is not bound to the reported soak completion");
  }
  const failures = Array.isArray(json.failures) ? json.failures : null;
  if (!failures || failures.length !== 0) blockers.push("failures must be an empty array");
  const cleanup = json.cleanup || {};
  for (const flag of [
    "complete",
    "propertiesRestored",
    "pluginHostInvalidated",
    "workDirectoryDeleted",
    "wakeLockAcquired",
    "wakeLockHeldUntilCompletion",
    "wakeLockReleased",
  ]) {
    if (cleanup[flag] !== true) blockers.push(`cleanup.${flag} must be true`);
  }
  const powerManagement = json.powerManagement || {};
  if (powerManagement.wakeLockType !== "partial") {
    blockers.push("powerManagement.wakeLockType must be partial");
  }
  const wakeLockHeldElapsedMs = powerManagement.wakeLockHeldElapsedMs;
  const wakeLockHeldUptimeMs = powerManagement.wakeLockHeldUptimeMs;
  const suspendDurationMs = powerManagement.suspendDurationMs;
  if (!Number.isFinite(wakeLockHeldElapsedMs) ||
      Math.abs(wakeLockHeldElapsedMs - durationMs) > SOAK_CLOCK_TOLERANCE_MS) {
    blockers.push("powerManagement.wakeLockHeldElapsedMs must cover the reported soak duration");
  }
  if (!Number.isFinite(wakeLockHeldUptimeMs) ||
      Math.abs(wakeLockHeldUptimeMs - durationMs) > SOAK_CLOCK_TOLERANCE_MS) {
    blockers.push("powerManagement.wakeLockHeldUptimeMs must cover the reported soak duration");
  }
  if (!Number.isFinite(suspendDurationMs) ||
      suspendDurationMs < 0 ||
      suspendDurationMs > MAX_SOAK_SUSPEND_MS ||
      suspendDurationMs !== Math.max(wakeLockHeldElapsedMs - wakeLockHeldUptimeMs, 0)) {
    blockers.push(`powerManagement.suspendDurationMs must be an exact value within 0..${MAX_SOAK_SUSPEND_MS}`);
  }
  const analysis = json.analysis || {};
  for (const flag of ["monotonicLeak", "orphanProcess", "unrecoverableRuntime", "unrecoverableSlot"]) {
    if (analysis[flag] !== false) blockers.push(`analysis.${flag} must be false`);
  }
  if (analysis.processRuntimeReused !== true) blockers.push("process runtime reuse was not proven during soak");
  if (analysis.freshExecutionState !== true) blockers.push("fresh execution state was not proven during soak");
  if (analysis.livenessRecoverabilityChecksPassed !== true) {
    blockers.push("liveness/recoverability checks did not pass during soak");
  }
  if (numberValue(analysis.livenessRecoverabilityChecks) <= 0) {
    blockers.push("at least one liveness/recoverability check is required");
  }
  if (stringValue(analysis.livenessRecoverabilityMode) !== "non_destructive_binder_session_health_and_recoverability") {
    blockers.push("analysis.livenessRecoverabilityMode must describe the non-destructive Binder/session check");
  }
  const livenessDetails = Array.isArray(json.livenessRecoverabilityCheckDetails)
    ? json.livenessRecoverabilityCheckDetails
    : [];
  if (livenessDetails.length !== numberValue(analysis.livenessRecoverabilityChecks)) {
    blockers.push("liveness/recoverability detail count does not match analysis");
  }
  if (Number.isInteger(livenessCadence) && livenessCadence > 0 && Number.isInteger(cycles) && cycles > 0 &&
      livenessDetails.length !== Math.ceil(cycles / livenessCadence)) {
    blockers.push("liveness/recoverability detail count does not match the configured cycle cadence");
  }
  livenessDetails.forEach((detail, index) => {
    if (!detail || detail.passed !== true) blockers.push(`liveness/recoverability check ${index} did not pass`);
    if (stringValue(detail && detail.mode) !== "non_destructive_binder_session_health_and_recoverability") {
      blockers.push(`liveness/recoverability check ${index} has an invalid mode`);
    }
    if (stablePid != null && Number(detail && detail.servicePid) !== stablePid) {
      blockers.push(`liveness/recoverability check ${index} changed servicePid`);
    }
    if (stableSessionGeneration != null && Number(detail && detail.hostSessionGeneration) !== stableSessionGeneration) {
      blockers.push(`liveness/recoverability check ${index} changed hostSessionGeneration`);
    }
    const checkCycle = Number(detail && detail.cycle);
    if (!Number.isInteger(checkCycle) || checkCycle <= 0 || checkCycle > cycles) {
      blockers.push(`liveness/recoverability check ${index} has an invalid cycle`);
    }
    const expectedCycle = Math.min((index + 1) * livenessCadence, cycles);
    if (Number.isInteger(livenessCadence) && livenessCadence > 0 && Number.isInteger(cycles) && cycles > 0 &&
        checkCycle !== expectedCycle) {
      blockers.push(`liveness/recoverability check ${index} must cover cycle ${expectedCycle}`);
    }
    if (!detail || detail.runtimeReady !== true) blockers.push(`liveness/recoverability check ${index} runtimeReady must be true`);
    if (!detail || detail.persistentProcessRuntime !== true) {
      blockers.push(`liveness/recoverability check ${index} persistentProcessRuntime must be true`);
    }
    const checkedAtEpochMs = Number(detail && detail.checkedAtEpochMs);
    if (!Number.isFinite(checkedAtEpochMs) ||
        checkedAtEpochMs < startedAtEpochMs - SOAK_CLOCK_TOLERANCE_MS ||
        checkedAtEpochMs > endedAtEpochMs + SOAK_CLOCK_TOLERANCE_MS) {
      blockers.push(`liveness/recoverability check ${index} falls outside the soak interval`);
    }
  });
  validateGrowthAnalysis(analysis.growth, samples, blockers);
  const device = json.device || {};
  if (device.isEmulator !== false) blockers.push("final soak evidence must come from physical hardware");
  if (!stringValue(device.manufacturer) || !stringValue(device.model) || !stringValue(device.serial)) {
    blockers.push("physical soak device identity is incomplete");
  }
  if (numberValue(device.apiLevel) < 24) blockers.push("physical soak device API level must be at least 24");
  const supportedAbis = Array.isArray(device.supportedAbis) ? device.supportedAbis.map(stringValue) : [];
  if (!supportedAbis.includes(stringValue(plugin.runtimeProcessAbi))) {
    blockers.push("physical soak device ABI list does not include the plugin runtime ABI");
  }
  return {
    id: requirement.id,
    kind: requirement.kind,
    path: normalizeEvidencePath(requirement.relative),
    available: true,
    sha256: sha256(file),
    generatedAt: stringValue(json.generatedAt),
    durationHours,
    cycles,
    samples: samples.length,
    identity: json.identity || {},
    device,
    blockers,
    ready: blockers.length === 0,
  };
}

function requirePositiveInteger(value, key, label, blockers) {
  const number = Number(value && value[key]);
  if (!Number.isInteger(number) || number <= 0) blockers.push(`${label} ${key} must be a positive integer`);
}

function requirePositiveNumber(value, key, label, blockers) {
  const number = Number(value && value[key]);
  if (!Number.isFinite(number) || number <= 0) blockers.push(`${label} ${key} must be positive`);
}

function requireNonNegativeNumber(value, key, label, blockers) {
  const number = Number(value && value[key]);
  if (!Number.isFinite(number) || number < 0) blockers.push(`${label} ${key} must be non-negative`);
}

function validateGrowthAnalysis(growthValue, samples, blockers) {
  const growth = growthValue || {};
  if (growth.method !== "thresholded_terminal_or_monotonic_remote_plugin_growth") {
    blockers.push("analysis.growth.method is invalid");
  }
  if (Number(growth.minimumSamples) !== MIN_GROWTH_SAMPLES) {
    blockers.push(`analysis.growth.minimumSamples must be ${MIN_GROWTH_SAMPLES}`);
  }
  const metrics = Array.isArray(growth.metrics) ? growth.metrics : [];
  const names = metrics.map((entry) => stringValue(entry && entry.name));
  if (new Set(names).size !== names.length) blockers.push("analysis.growth metric names must be unique");
  let anyDetected = false;
  GROWTH_METRICS.forEach((name) => {
    const metric = metrics.find((entry) => entry && entry.name === name);
    if (!metric) {
      blockers.push(`analysis.growth is missing metric ${name}`);
      return;
    }
    const values = samples.map((sample) => Number(sample && sample[name]));
    const reportedValues = Array.isArray(metric.values) ? metric.values.map(Number) : [];
    if (reportedValues.length !== values.length || reportedValues.some((value, index) => value !== values[index])) {
      blockers.push(`analysis.growth metric ${name} values do not match samples`);
    }
    const baseline = values[0];
    const final = values[values.length - 1];
    const delta = final - baseline;
    const threshold = GROWTH_THRESHOLDS[name];
    const monotonic = looksMonotonicRunaway(values);
    const detected = delta > threshold;
    anyDetected = anyDetected || detected;
    if (Number(metric.sampleCount) !== values.length) blockers.push(`analysis.growth metric ${name} sampleCount is invalid`);
    if (Number(metric.baseline) !== baseline) blockers.push(`analysis.growth metric ${name} baseline is invalid`);
    if (Number(metric.final) !== final) blockers.push(`analysis.growth metric ${name} final is invalid`);
    if (Number(metric.delta) !== delta) blockers.push(`analysis.growth metric ${name} delta is invalid`);
    if (Number(metric.threshold) !== threshold) blockers.push(`analysis.growth metric ${name} threshold is invalid`);
    if (metric.monotonic !== monotonic) blockers.push(`analysis.growth metric ${name} monotonic decision is invalid`);
    if (metric.growthDetected !== detected) blockers.push(`analysis.growth metric ${name} growth decision is invalid`);
    if (detected) blockers.push(`analysis.growth metric ${name} exceeded its growth threshold`);
  });
  if (metrics.length !== GROWTH_METRICS.length) blockers.push("analysis.growth contains unexpected metrics");
  if (growth.monotonicLeak !== anyDetected) blockers.push("analysis.growth.monotonicLeak is inconsistent");
}

function looksMonotonicRunaway(values) {
  if (values.length < MIN_GROWTH_SAMPLES || values.some((value) => !Number.isFinite(value))) return false;
  let increases = 0;
  for (let index = 1; index < values.length; index += 1) {
    if (values[index] > values[index - 1]) increases += 1;
  }
  const recent = values.slice(-Math.min(values.length, 4));
  let recentNonDecreases = 0;
  for (let index = 1; index < recent.length; index += 1) {
    if (recent[index] >= recent[index - 1]) recentNonDecreases += 1;
  }
  return values[values.length - 1] === Math.max(...values) &&
    increases >= values.length - 2 &&
    recentNonDecreases === recent.length - 1;
}

function validateCapturedIdentity(captured, current, blockers) {
  const value = captured || {};
  const requiredStrings = {
    hostCommit: current.host.commit,
    pluginCommit: current.plugin.commit,
    versionName: current.version.name,
    versionBuild: current.version.build,
    lifecycleModel: LIFECYCLE_MODEL,
    releaseFlavor: DEFAULT_RELEASE_FLAVOR,
    backend: DEFAULT_BACKEND,
    runtimeSlot: DEFAULT_RUNTIME_SLOT,
    nodeVersion: DEFAULT_NODE_VERSION,
  };
  Object.entries(requiredStrings).forEach(([key, expected]) => {
    if (!expected || stringValue(value[key]) !== stringValue(expected)) {
      blockers.push(`identity ${key} does not match current source (${stringValue(value[key])} != ${stringValue(expected)})`);
    }
  });
  if (value.pluginRequired !== true) blockers.push("identity pluginRequired must be true");
  if (value.hostTreeClean !== true) blockers.push("captured host worktree was not clean");
  if (value.pluginTreeClean !== true) blockers.push("captured plugin worktree was not clean");
}

function validateDevice(deviceValue, requirement, blockers) {
  const device = deviceValue || {};
  if (device.role !== requirement.role) blockers.push(`device role must be ${requirement.role}`);
  if (device.isEmulator !== requirement.emulator) blockers.push(`device isEmulator must be ${requirement.emulator}`);
  if (!stringValue(device.serial)) blockers.push("device serial is missing");
  if (!stringValue(device.model)) blockers.push("device model is missing");
  if (!stringValue(device.manufacturer)) blockers.push("device manufacturer is missing");
  if (numberValue(device.apiLevel) < 24) blockers.push("device API level must be at least 24");
  if (Number.isInteger(requirement.apiLevel) && device.apiLevel !== requirement.apiLevel) {
    blockers.push(`device API level must be exactly ${requirement.apiLevel}`);
  }
  if (requirement.avdName && stringValue(device.avdName) !== requirement.avdName) {
    blockers.push(`emulator AVD name must be ${requirement.avdName}`);
  }
  if (Number.isInteger(requirement.pageSizeBytes) && device.pageSizeBytes !== requirement.pageSizeBytes) {
    blockers.push(`device page size must be exactly ${requirement.pageSizeBytes} bytes`);
  }
  if (stringValue(device.runtimeProcessAbi) !== requirement.runtimeProcessAbi) {
    blockers.push(`runtime process ABI must be ${requirement.runtimeProcessAbi}`);
  }
  const hostProcessAbi = stringValue(device.hostProcessAbi);
  if (!hostProcessAbi) blockers.push("host process ABI is missing");
  const supportedAbis = Array.isArray(device.supportedAbis) ? device.supportedAbis.map(stringValue) : [];
  if (!supportedAbis.includes(requirement.runtimeProcessAbi)) {
    blockers.push(`device ABI list does not include ${requirement.runtimeProcessAbi}`);
  }
  if (hostProcessAbi && !supportedAbis.includes(hostProcessAbi)) {
    blockers.push(`device ABI list does not include host process ABI ${hostProcessAbi}`);
  }
  const manufacturer = stringValue(device.manufacturer).toLowerCase();
  if (requirement.manufacturer && manufacturer !== requirement.manufacturer) {
    blockers.push(`device manufacturer must be ${requirement.manufacturer}`);
  }
  if (requirement.manufacturerNot && manufacturer === requirement.manufacturerNot) {
    blockers.push(`device manufacturer must differ from ${requirement.manufacturerNot}`);
  }
}

function validateTestCounts(rawValue, tests, blockers) {
  const raw = rawValue || {};
  for (const key of ["total", "failures", "errors", "skipped"]) {
    const value = Number(raw[key]);
    if (!Number.isInteger(value) || value < 0) {
      blockers.push(`tests.${key} must be an explicit non-negative integer`);
    }
  }
  if (tests.total !== tests.selectors.length) {
    blockers.push(`test total ${tests.total} must equal selector result count ${tests.selectors.length}`);
  }
  if (tests.failures + tests.errors + tests.skipped > tests.total) {
    blockers.push("test failure/error/skipped counts exceed total");
  }
  const selectorNames = tests.selectors.map((entry) => entry.selector);
  if (new Set(selectorNames).size !== selectorNames.length) {
    blockers.push("test selector results must be unique");
  }
  const acceptedStatuses = new Set(["passed", "failed", "error", "skipped"]);
  tests.selectors.forEach((entry, index) => {
    if (!entry.selector) blockers.push(`test selector ${index} is blank`);
    if (!acceptedStatuses.has(entry.status)) {
      blockers.push(`test selector ${entry.selector || index} has unsupported status ${entry.status}`);
    }
  });
  const statusCounts = {
    failed: tests.selectors.filter((entry) => entry.status === "failed").length,
    error: tests.selectors.filter((entry) => entry.status === "error").length,
    skipped: tests.selectors.filter((entry) => entry.status === "skipped").length,
  };
  if (statusCounts.failed !== tests.failures) blockers.push("tests.failures does not match selector results");
  if (statusCounts.error !== tests.errors) blockers.push("tests.errors does not match selector results");
  if (statusCounts.skipped !== tests.skipped) blockers.push("tests.skipped does not match selector results");
}

function validateArtifacts(artifactsValue, repoRoot, blockers, options = {}) {
  const artifacts = artifactsValue || {};
  const resolvedFiles = {};
  const requiredArtifactHashes = options.reportType === "device"
    ? REQUIRED_DEVICE_ARTIFACT_HASHES
    : REQUIRED_ARTIFACT_HASHES;
  requiredArtifactHashes.forEach((key) => {
    if (!SHA256_PATTERN.test(stringValue(artifacts[key]))) {
      blockers.push(`artifact ${key} must be a SHA-256 digest`);
    }
  });
  const fileBindings = [
    ["hostApkSha256", "hostApkPath", "hostApkBytes"],
    ["androidTestApkSha256", "androidTestApkPath", "androidTestApkBytes"],
    ["pluginApkSha256", "pluginApkPath", "pluginApkBytes"],
    ["junitXmlSha256", "junitXmlPath", "junitXmlBytes"],
  ];
  if (options.reportType === "device") {
    fileBindings.push([
      "apkBuilderPluginApkSha256",
      "apkBuilderPluginApkPath",
      "apkBuilderPluginApkBytes",
    ]);
    fileBindings.push([
      "apkBuilderPluginApkBeforeTestSha256",
      "apkBuilderPluginApkBeforeTestPath",
      "apkBuilderPluginApkBeforeTestBytes",
    ]);
    fileBindings.push([
      "packagedTemplateApkSha256",
      "packagedTemplateApkPath",
      "packagedTemplateApkBytes",
    ]);
  }
  fileBindings.forEach(([hashKey, pathKey, bytesKey]) => {
    const artifactPath = stringValue(artifacts[pathKey]);
    const expectedBytes = Number(artifacts[bytesKey]);
    if (!artifactPath) blockers.push(`artifact ${pathKey} is missing`);
    if (!Number.isInteger(expectedBytes) || expectedBytes <= 0) {
      blockers.push(`artifact ${bytesKey} must be a positive integer`);
    }
    if (!repoRoot || !artifactPath) return;
    const resolved = path.resolve(repoRoot, artifactPath);
    if (!isInsideOrSame(path.resolve(repoRoot), resolved)) {
      blockers.push(`artifact ${pathKey} escapes the repository root`);
      return;
    }
    if (!fs.existsSync(resolved) || !fs.statSync(resolved).isFile()) {
      blockers.push(`artifact ${pathKey} does not exist: ${artifactPath}`);
      return;
    }
    resolvedFiles[pathKey] = resolved;
    const actualBytes = fs.statSync(resolved).size;
    if (actualBytes !== expectedBytes) {
      blockers.push(`artifact ${bytesKey} does not match ${pathKey}`);
    }
    if (SHA256_PATTERN.test(stringValue(artifacts[hashKey])) && sha256(resolved) !== stringValue(artifacts[hashKey]).toLowerCase()) {
      blockers.push(`artifact ${hashKey} does not match ${pathKey}`);
    }
  });
  const attestationBindings = [
    ["hostBuildApkSha256", "hostBuildApkPath", "hostBuildApkBytes"],
    ["androidTestBuildApkSha256", "androidTestBuildApkPath", "androidTestBuildApkBytes"],
    ["requestedPluginApkSha256", "requestedPluginApkPath", "requestedPluginApkBytes"],
  ];
  if (options.reportType === "device") {
    attestationBindings.push([
      "apkBuilderPluginBuildApkSha256",
      "apkBuilderPluginBuildApkPath",
      "apkBuilderPluginBuildApkBytes",
    ]);
  }
  attestationBindings.forEach(([hashKey, pathKey, bytesKey]) => {
    if (!SHA256_PATTERN.test(stringValue(artifacts[hashKey]))) {
      blockers.push(`artifact ${hashKey} must be a SHA-256 digest`);
    }
    const artifactPath = stringValue(artifacts[pathKey]);
    const expectedBytes = Number(artifacts[bytesKey]);
    if (!artifactPath) blockers.push(`artifact ${pathKey} is missing`);
    if (!Number.isInteger(expectedBytes) || expectedBytes <= 0) {
      blockers.push(`artifact ${bytesKey} must be a positive integer`);
    }
    if (!repoRoot || !artifactPath) return;
    const resolved = path.resolve(repoRoot, artifactPath);
    if (!isInsideOrSame(path.resolve(repoRoot), resolved)) {
      blockers.push(`artifact ${pathKey} escapes the repository root`);
      return;
    }
    if (!fs.existsSync(resolved) || !fs.statSync(resolved).isFile()) {
      blockers.push(`artifact ${pathKey} does not exist: ${artifactPath}`);
      return;
    }
    resolvedFiles[pathKey] = resolved;
    if (fs.statSync(resolved).size !== expectedBytes) blockers.push(`artifact ${bytesKey} does not match ${pathKey}`);
    if (SHA256_PATTERN.test(stringValue(artifacts[hashKey])) &&
        sha256(resolved) !== stringValue(artifacts[hashKey]).toLowerCase()) {
      blockers.push(`artifact ${hashKey} does not match ${pathKey}`);
    }
  });
  if (artifacts.installedHostMatchesFreshBuild !== true ||
      stringValue(artifacts.hostApkSha256) !== stringValue(artifacts.hostBuildApkSha256)) {
    blockers.push("installed host APK must match the fresh host build APK");
  }
  if (artifacts.installedPluginMatchesRequested !== true ||
      stringValue(artifacts.pluginApkSha256) !== stringValue(artifacts.requestedPluginApkSha256)) {
    blockers.push("installed runtime plugin APK must match the requested plugin APK");
  }
  if (artifacts.installedAndroidTestMatchesFreshBuild !== true ||
      stringValue(artifacts.androidTestApkSha256) !== stringValue(artifacts.androidTestBuildApkSha256)) {
    blockers.push("installed Android test APK must match the fresh Android test build APK");
  }
  if (options.reportType === "device") {
    const installedApkBuilderPluginSha256 =
      stringValue(artifacts.apkBuilderPluginApkSha256).toLowerCase();
    const preTestApkBuilderPluginSha256 =
      stringValue(artifacts.apkBuilderPluginApkBeforeTestSha256).toLowerCase();
    const freshApkBuilderPluginSha256 =
      stringValue(artifacts.apkBuilderPluginBuildApkSha256).toLowerCase();
    if (artifacts.installedApkBuilderPluginMatchesFreshBuild !== true ||
        installedApkBuilderPluginSha256 !== freshApkBuilderPluginSha256) {
      blockers.push("installed APK Builder plugin APK must match the fresh current-source build APK");
    }
    if (artifacts.installedApkBuilderPluginUnchangedDuringTest !== true ||
        preTestApkBuilderPluginSha256 !== installedApkBuilderPluginSha256 ||
        preTestApkBuilderPluginSha256 !== freshApkBuilderPluginSha256) {
      blockers.push(
        "installed APK Builder plugin APK must remain identical to the fresh build before and after testing",
      );
    }
    if (stringValue(artifacts.packagedTemplateSourceTask) !== PACKAGED_TEMPLATE_TASK) {
      blockers.push(`packaged template source task must be ${PACKAGED_TEMPLATE_TASK}`);
    }
    const expectedDevicePath = packagedTemplatePathForRunToken(options.runToken);
    if (!expectedDevicePath) {
      blockers.push("packaged template device path cannot be bound without a valid run token");
    } else if (stringValue(artifacts.packagedTemplateDevicePath) !== expectedDevicePath) {
      blockers.push(`packaged template device path must be bound to the report run token: ${expectedDevicePath}`);
    }
    const templateApkSha256 = stringValue(artifacts.packagedTemplateApkSha256).toLowerCase();
    const deviceSha256BeforeTest = stringValue(artifacts.packagedTemplateDeviceSha256BeforeTest).toLowerCase();
    const deviceSha256AfterTest = stringValue(artifacts.packagedTemplateDeviceSha256AfterTest).toLowerCase();
    if (!SHA256_PATTERN.test(deviceSha256BeforeTest)) {
      blockers.push("packagedTemplateDeviceSha256BeforeTest must be a SHA-256 digest");
    }
    if (!SHA256_PATTERN.test(deviceSha256AfterTest)) {
      blockers.push("packagedTemplateDeviceSha256AfterTest must be a SHA-256 digest");
    }
    if (artifacts.packagedTemplateDeviceMatchesFreshBuild !== true ||
        deviceSha256BeforeTest !== templateApkSha256 ||
        deviceSha256AfterTest !== templateApkSha256) {
      blockers.push("device packaged template must match the fresh current-source template APK before and after testing");
    }
    if (artifacts.packagedTemplateDeviceRemovedAfterTest !== true) {
      blockers.push("the run-owned packaged template device file must be removed after testing");
    }
    if (stringValue(artifacts.packagedTemplateTargetAbi) !== stringValue(options.runtimeProcessAbi)) {
      blockers.push("packaged template target ABI must match the device runtime process ABI");
    }
    if (stringValue(artifacts.packagedTemplateOverrideArgument) !==
        `${PACKAGED_TEMPLATE_OVERRIDE_ARGUMENT}=true`) {
      blockers.push("packaged template override instrumentation argument is not bound to true");
    }
    if (expectedDevicePath && stringValue(artifacts.packagedTemplateExactPathArgument) !==
        `${PACKAGED_TEMPLATE_EXACT_PATH_ARGUMENT}=${expectedDevicePath}`) {
      blockers.push("packaged template exact-path instrumentation argument is not bound to the run-owned device path");
    }
    if (stringValue(artifacts.packagedTemplateTargetAbiArgument) !==
        `${PACKAGED_TEMPLATE_TARGET_ABI_ARGUMENT}=${stringValue(options.runtimeProcessAbi)}`) {
      blockers.push("packaged template target-ABI instrumentation argument is not bound to the device runtime process ABI");
    }
  }
  const hostProcessAbi = stringValue(options.hostProcessAbi) || stringValue(options.runtimeProcessAbi);
  const hostApk = validateApkArtifact(
    resolvedFiles.hostApkPath,
    "host APK",
    hostProcessAbi,
    "host",
    blockers,
  );
  const hostBuildApk = validateApkArtifact(
    resolvedFiles.hostBuildApkPath,
    "host build APK",
    hostProcessAbi,
    "host",
    blockers,
  );
  const androidTestApk = validateApkArtifact(
    resolvedFiles.androidTestApkPath,
    "Android test APK",
    "",
    "test",
    blockers,
  );
  const androidTestBuildApk = validateApkArtifact(
    resolvedFiles.androidTestBuildApkPath,
    "Android test build APK",
    "",
    "test",
    blockers,
  );
  const pluginApk = validateApkArtifact(
    resolvedFiles.pluginApkPath,
    "runtime plugin APK",
    options.runtimeProcessAbi,
    "plugin",
    blockers,
  );
  const requestedPluginApk = validateApkArtifact(
    resolvedFiles.requestedPluginApkPath,
    "requested runtime plugin APK",
    options.runtimeProcessAbi,
    "plugin",
    blockers,
  );
  let apkBuilderPluginApk = null;
  let apkBuilderPluginApkBeforeTest = null;
  let apkBuilderPluginBuildApk = null;
  if (options.reportType === "device") {
    apkBuilderPluginApk = validateApkArtifact(
      resolvedFiles.apkBuilderPluginApkPath,
      "installed APK Builder plugin APK",
      "",
      "apk-builder-plugin",
      blockers,
    );
    apkBuilderPluginApkBeforeTest = validateApkArtifact(
      resolvedFiles.apkBuilderPluginApkBeforeTestPath,
      "pre-test installed APK Builder plugin APK",
      "",
      "apk-builder-plugin",
      blockers,
    );
    apkBuilderPluginBuildApk = validateApkArtifact(
      resolvedFiles.apkBuilderPluginBuildApkPath,
      "fresh APK Builder plugin build APK",
      "",
      "apk-builder-plugin",
      blockers,
    );
    validateApkArtifact(
      resolvedFiles.packagedTemplateApkPath,
      "packaged smoke template APK",
      options.runtimeProcessAbi,
      "template",
      blockers,
    );
  }
  validateApkSignerPair(hostApk, hostBuildApk, "installed host APK and fresh host build APK", blockers);
  validateApkSignerPair(
    androidTestApk,
    androidTestBuildApk,
    "installed Android test APK and fresh Android test build APK",
    blockers,
  );
  validateApkSignerPair(
    pluginApk,
    requestedPluginApk,
    "installed runtime plugin APK and requested runtime plugin APK",
    blockers,
  );
  validateApkSignerPair(hostApk, androidTestApk, "host APK and Android test APK", blockers);
  if (options.reportType === "device") {
    validateApkSignerPair(
      apkBuilderPluginApk,
      apkBuilderPluginBuildApk,
      "installed APK Builder plugin APK and fresh build APK",
      blockers,
    );
    validateApkSignerPair(
      apkBuilderPluginApkBeforeTest,
      apkBuilderPluginBuildApk,
      "pre-test installed APK Builder plugin APK and fresh build APK",
      blockers,
    );
    validateApkSignerPair(
      hostApk,
      apkBuilderPluginApk,
      "host APK and installed APK Builder plugin APK",
      blockers,
    );
    if (SHA256_PATTERN.test(stringValue(options.reportedApkBuilderPluginSignerSha256))) {
      const reportedBuilderSigner =
        stringValue(options.reportedApkBuilderPluginSignerSha256).toLowerCase();
      validateReportedApkSigner(
        reportedBuilderSigner,
        apkBuilderPluginApk,
        "installed APK Builder plugin APK",
        blockers,
        "APK Builder plugin",
      );
      validateReportedApkSigner(
        reportedBuilderSigner,
        apkBuilderPluginApkBeforeTest,
        "pre-test installed APK Builder plugin APK",
        blockers,
        "APK Builder plugin",
      );
      validateReportedApkSigner(
        reportedBuilderSigner,
        apkBuilderPluginBuildApk,
        "fresh APK Builder plugin build APK",
        blockers,
        "APK Builder plugin",
      );
    }
  }
  if (SHA256_PATTERN.test(stringValue(options.reportedPluginSignerSha256))) {
    const reportedSigner = stringValue(options.reportedPluginSignerSha256).toLowerCase();
    validateReportedApkSigner(reportedSigner, pluginApk, "runtime plugin APK", blockers);
    validateReportedApkSigner(reportedSigner, requestedPluginApk, "requested runtime plugin APK", blockers);
  }
  return resolvedFiles;
}

function validateApkArtifact(file, label, runtimeProcessAbi, kind, blockers) {
  if (!file) return null;
  if (path.extname(file).toLowerCase() !== ".apk") {
    blockers.push(`${label} must use the .apk extension`);
    return null;
  }
  const entries = readZipEntries(file);
  if (!entries || !entries.includes("AndroidManifest.xml")) {
    blockers.push(`${label} is not an APK ZIP containing AndroidManifest.xml`);
    return null;
  }
  const inspection = inspectApkArtifact(file);
  const expectedPackage = kind === "plugin"
    ? RUNTIME_PLUGIN_PACKAGE
    : kind === "test"
      ? "org.autojs.autojs6.test"
      : kind === "template"
        ? PACKAGED_TEMPLATE_PACKAGE
        : kind === "apk-builder-plugin"
          ? APK_BUILDER_PLUGIN_PACKAGE
          : "org.autojs.autojs6";
  if (!inspection.ready) {
    blockers.push(`${label} failed Android package/signature inspection: ${inspection.blockers.join("; ")}`);
  } else if (inspection.packageName !== expectedPackage) {
    blockers.push(`${label} package must be ${expectedPackage}, got ${inspection.packageName}`);
  }
  const nativeEntries = entries.filter((entry) => /^lib\/[^/]+\/[^/]+\.so$/.test(entry));
  const nodeRuntimeEntries = nativeEntries.filter((entry) =>
    /\/(?:libnode|libautojs6-node)\.so$/.test(entry));
  if (kind === "host") {
    if (nodeRuntimeEntries.length !== 0) blockers.push(`${label} must not package Node runtime JNI libraries`);
    const abi = stringValue(runtimeProcessAbi);
    if (!abi) {
      blockers.push(`${label} runtime ABI is missing`);
      return inspection;
    }
    for (const library of ["libjackpal-termexec2.so", "libc++_shared.so"]) {
      if (!entries.includes(`lib/${abi}/${library}`)) {
        blockers.push(`${label} is missing baseline host JNI dependency lib/${abi}/${library}`);
      }
    }
    return inspection;
  }
  if (kind === "template") {
    if (nodeRuntimeEntries.length !== 0) {
      blockers.push(`${label} must be a slim template without prepackaged Node runtime JNI libraries`);
    }
    const abi = stringValue(runtimeProcessAbi);
    if (!abi) {
      blockers.push(`${label} target ABI is missing`);
      return inspection;
    }
    const packagedAbis = [...new Set(nativeEntries.map((entry) => entry.split("/")[1]))];
    if (packagedAbis.length !== 1 || packagedAbis[0] !== abi) {
      blockers.push(`${label} must contain exactly the ${abi} native ABI`);
    }
    for (const library of ["libjackpal-termexec2.so", "libc++_shared.so"]) {
      if (!entries.includes(`lib/${abi}/${library}`)) {
        blockers.push(`${label} is missing baseline template JNI dependency lib/${abi}/${library}`);
      }
    }
    return inspection;
  }
  if (kind !== "plugin") return inspection;
  const abi = stringValue(runtimeProcessAbi);
  if (!abi) {
    blockers.push(`${label} runtime ABI is missing`);
    return inspection;
  }
  const packagedAbis = [...new Set(nativeEntries.map((entry) => entry.split("/")[1]))];
  if (packagedAbis.length !== 1 || packagedAbis[0] !== abi) {
    blockers.push(`${label} must contain exactly the ${abi} native ABI`);
  }
  for (const library of ["libnode.so", "libautojs6-node.so", "libc++_shared.so"]) {
    if (!entries.includes(`lib/${abi}/${library}`)) blockers.push(`${label} is missing lib/${abi}/${library}`);
  }
  return inspection;
}

function validateApkSignerPair(left, right, label, blockers) {
  const leftSigner = apkInspectionSigner(left);
  const rightSigner = apkInspectionSigner(right);
  if (leftSigner && rightSigner && leftSigner !== rightSigner) {
    blockers.push(`${label} must have the same signer`);
  }
}

function validateReportedApkSigner(
  reportedSigner,
  inspection,
  label,
  blockers,
  provenanceLabel = "plugin",
) {
  const artifactSigner = apkInspectionSigner(inspection);
  if (artifactSigner && reportedSigner !== artifactSigner) {
    blockers.push(`${provenanceLabel} signerSha256 does not match the ${label} signer`);
  }
}

function apkInspectionSigner(inspection) {
  const signer = stringValue(inspection && inspection.signerSha256).toLowerCase();
  return SHA256_PATTERN.test(signer) ? signer : "";
}

function apkSignerCertificateSha256Digests(output) {
  return [...stringValue(output).matchAll(
    /^(?:Signer #\d+\s+|V\d+(?:\.\d+)? Signer:\s+)certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$/gim,
  )].map((match) => match[1].toLowerCase());
}

let cachedAndroidBuildTools;
const cachedApkInspections = new Map();

function inspectApkArtifact(file) {
  const digest = sha256(file);
  if (cachedApkInspections.has(digest)) return cachedApkInspections.get(digest);
  const tools = resolveAndroidBuildTools();
  const blockers = [];
  if (!tools) {
    const unavailable = {
      ready: false,
      packageName: "",
      signerSha256: "",
      blockers: ["Android SDK build-tools are unavailable"],
    };
    cachedApkInspections.set(digest, unavailable);
    return unavailable;
  }
  const badging = runExternal(tools.aapt, ["dump", "badging", file]);
  if (badging.status !== 0) blockers.push(`aapt dump badging failed: ${badging.stderr || badging.stdout}`.trim());
  const packageMatch = /^package:\s+name='([^']+)'/m.exec(badging.stdout);
  if (!packageMatch) blockers.push("aapt did not report an APK package name");
  const signature = runExternal(tools.apksigner, ["verify", "--verbose", "--print-certs", file]);
  if (signature.status !== 0) blockers.push(`apksigner verification failed: ${signature.stderr || signature.stdout}`.trim());
  if (!/Verified using v2 scheme \(APK Signature Scheme v2\): true/i.test(signature.stdout)) {
    blockers.push("APK Signature Scheme v2 verification is not true");
  }
  const signerCountMatch = /Number of signers:\s*([0-9]+)/i.exec(signature.stdout);
  const signerCount = signerCountMatch ? Number(signerCountMatch[1]) : 0;
  if (signerCount !== 1) blockers.push(`APK must have exactly one signer, got ${signerCount}`);
  const signerDigests = apkSignerCertificateSha256Digests(signature.stdout);
  const signerSha256 = signerDigests.length === 1 ? signerDigests[0] : "";
  if (!signerSha256) blockers.push("APK signer SHA-256 identity is missing or ambiguous");
  const inspection = {
    ready: blockers.length === 0,
    packageName: packageMatch ? packageMatch[1] : "",
    signerSha256,
    blockers,
  };
  cachedApkInspections.set(digest, inspection);
  return inspection;
}

function resolveAndroidBuildTools() {
  if (cachedAndroidBuildTools !== undefined) return cachedAndroidBuildTools;
  const repositoryRoot = path.resolve(__dirname, "../../..");
  const sdkRoots = [process.env.ANDROID_SDK_ROOT, process.env.ANDROID_HOME, readSdkDir(repositoryRoot)]
    .map((value) => stringValue(value))
    .filter(Boolean);
  for (const sdkRoot of [...new Set(sdkRoots)]) {
    const buildToolsRoot = path.join(sdkRoot, "build-tools");
    if (!fs.existsSync(buildToolsRoot)) continue;
    const versions = fs.readdirSync(buildToolsRoot)
      .map((name) => path.join(buildToolsRoot, name))
      .filter((entry) => fs.statSync(entry).isDirectory())
      .sort((left, right) => path.basename(right).localeCompare(path.basename(left), undefined, { numeric: true }));
    for (const directory of versions) {
      const aapt = path.join(directory, process.platform === "win32" ? "aapt.exe" : "aapt");
      const apksigner = path.join(directory, process.platform === "win32" ? "apksigner.bat" : "apksigner");
      if (fs.existsSync(aapt) && fs.existsSync(apksigner)) {
        cachedAndroidBuildTools = { aapt, apksigner };
        return cachedAndroidBuildTools;
      }
    }
  }
  cachedAndroidBuildTools = null;
  return null;
}

function readSdkDir(repoRoot) {
  const localProperties = path.join(repoRoot, "local.properties");
  if (!fs.existsSync(localProperties)) return "";
  const line = read(localProperties).split(/\r?\n/).find((entry) => entry.startsWith("sdk.dir="));
  return line ? line.slice("sdk.dir=".length).replace(/\\:/g, ":").replace(/\\\\/g, "\\") : "";
}

function runExternal(command, args) {
  if (process.platform === "win32" && command.toLowerCase().endsWith(".bat")) {
    const result = childProcess.spawnSync(process.env.ComSpec || "cmd.exe", ["/d", "/c", "call", command, ...args], {
      encoding: "utf8",
      windowsHide: true,
      timeout: 120_000,
    });
    return { status: result.status, stdout: stringValue(result.stdout), stderr: stringValue(result.stderr) };
  }
  const result = childProcess.spawnSync(command, args, { encoding: "utf8", windowsHide: true, timeout: 120_000 });
  return { status: result.status, stdout: stringValue(result.stdout), stderr: stringValue(result.stderr) };
}

function readZipEntries(file) {
  const buffer = fs.readFileSync(file);
  const eocdSignature = 0x06054b50;
  const centralSignature = 0x02014b50;
  const minimumOffset = Math.max(0, buffer.length - 65_557);
  let eocd = -1;
  for (let offset = buffer.length - 22; offset >= minimumOffset; offset -= 1) {
    if (buffer.readUInt32LE(offset) === eocdSignature) {
      eocd = offset;
      break;
    }
  }
  if (eocd < 0 || eocd + 22 > buffer.length) return null;
  const entryCount = buffer.readUInt16LE(eocd + 10);
  let offset = buffer.readUInt32LE(eocd + 16);
  const entries = [];
  for (let index = 0; index < entryCount; index += 1) {
    if (offset < 0 || offset + 46 > buffer.length || buffer.readUInt32LE(offset) !== centralSignature) return null;
    const nameLength = buffer.readUInt16LE(offset + 28);
    const extraLength = buffer.readUInt16LE(offset + 30);
    const commentLength = buffer.readUInt16LE(offset + 32);
    const nameStart = offset + 46;
    const nameEnd = nameStart + nameLength;
    if (nameEnd > buffer.length) return null;
    entries.push(buffer.toString("utf8", nameStart, nameEnd));
    offset = nameEnd + extraLength + commentLength;
  }
  return entries;
}

function validateDeviceJUnitArtifact(file, tests, blockers) {
  if (!file) return;
  const junit = parseJUnit(file);
  if (!junit) {
    blockers.push("JUnit artifact is not a parseable test report");
    return;
  }
  if (junit.total !== tests.total || junit.failures !== tests.failures ||
      junit.errors !== tests.errors || junit.skipped !== tests.skipped) {
    blockers.push("JUnit totals do not match device evidence tests");
  }
  const junitSelectorNames = junit.selectors.map((entry) => entry.selector);
  if (junit.selectors.length !== junit.total || new Set(junitSelectorNames).size !== junitSelectorNames.length) {
    blockers.push("JUnit testcase count and selectors must be exact and unique");
  }
  if (junitSelectorNames.length !== REQUIRED_DEVICE_SELECTORS.length ||
      REQUIRED_DEVICE_SELECTORS.some((selector) => !junitSelectorNames.includes(selector))) {
    blockers.push("JUnit selectors do not equal the fixed R6 device selector set");
  }
  const actual = new Map(junit.selectors.map((entry) => [entry.selector, entry.status]));
  tests.selectors.forEach((entry) => {
    if (actual.get(entry.selector) !== entry.status) {
      blockers.push(`JUnit result does not match selector ${entry.selector}`);
    }
  });
}

function validateSoakJUnitArtifact(file, blockers) {
  if (!file) return;
  const junit = parseJUnit(file);
  if (!junit) {
    blockers.push("JUnit artifact is not a parseable test report");
    return;
  }
  const selector = "org.autojs.autojs.engine.NodeR6PersistentPluginSoakInstrumentationTest#persistentRequiredPluginSoakProducesR6Evidence";
  const matches = junit.selectors.filter((entry) => entry.selector === selector);
  if (junit.failures !== 0 || junit.errors !== 0 || junit.skipped !== 0 ||
      junit.total !== 1 || junit.selectors.length !== 1 ||
      matches.length !== 1 || matches[0].status !== "passed") {
    blockers.push("JUnit artifact does not prove one clean R6 persistent-plugin soak test");
  }
}

function parseJUnit(file) {
  try {
    const root = parseXmlDocument(read(file));
    if (!root || (root.name !== "testsuites" && root.name !== "testsuite")) return null;
    const suites = xmlDescendants(root, "testsuite", root.name === "testsuite");
    if (suites.length === 0) return null;
    const testcaseNodes = xmlDescendants(root, "testcase", false);
    const selectors = testcaseNodes.map((testcase) => {
      if (xmlDescendants(testcase, "testcase", false).length > 0) {
        throw new Error("JUnit testcase elements must not be nested.");
      }
      let suite = testcase.parent;
      while (suite && suite.name !== "testsuite") suite = suite.parent;
      if (!suite) throw new Error("JUnit testcase is not contained by a testsuite.");
      const className = testcase.attributes.classname || "";
      const name = testcase.attributes.name || "";
      if (!className || !name) throw new Error("JUnit testcase identity is incomplete.");
      const markers = [
        ["failure", "failed"],
        ["error", "error"],
        ["skipped", "skipped"],
      ].filter(([element]) => xmlDescendants(testcase, element, false).length > 0);
      if (markers.length > 1) throw new Error("JUnit testcase contains conflicting result markers.");
      return {
        node: testcase,
        selector: `${className}#${name}`,
        status: markers.length === 0 ? "passed" : markers[0][1],
      };
    });
    const aggregateFor = (node) => {
      const descendants = selectors.filter((entry) => xmlIsDescendant(entry.node, node));
      return {
        tests: descendants.length,
        failures: descendants.filter((entry) => entry.status === "failed").length,
        errors: descendants.filter((entry) => entry.status === "error").length,
        skipped: descendants.filter((entry) => entry.status === "skipped").length,
      };
    };
    for (const suite of suites) {
      assertJUnitAggregate(suite.attributes, aggregateFor(suite));
    }
    const actual = aggregateFor(root);
    const aggregate = assertJUnitAggregate(root.attributes, actual);
    return {
      total: aggregate.tests,
      failures: aggregate.failures,
      errors: aggregate.errors,
      skipped: aggregate.skipped,
      selectors: selectors.map(({ selector, status }) => ({ selector, status })),
    };
  } catch (_) {
    return null;
  }
}

function parseXmlDocument(value) {
  const xml = String(value || "");
  const stack = [];
  let root = null;
  let offset = 0;
  while (offset < xml.length) {
    const tagStart = xml.indexOf("<", offset);
    if (tagStart < 0) {
      if (stack.length === 0 && xml.slice(offset).trim()) throw new Error("Text follows the XML root.");
      break;
    }
    if (stack.length === 0 && xml.slice(offset, tagStart).trim()) {
      throw new Error("Text appears outside the XML root.");
    }
    if (xml.startsWith("<!--", tagStart)) {
      const end = xml.indexOf("-->", tagStart + 4);
      if (end < 0) throw new Error("Unterminated XML comment.");
      offset = end + 3;
      continue;
    }
    if (xml.startsWith("<![CDATA[", tagStart)) {
      if (stack.length === 0) throw new Error("CDATA appears outside the XML root.");
      const end = xml.indexOf("]]>", tagStart + 9);
      if (end < 0) throw new Error("Unterminated XML CDATA section.");
      offset = end + 3;
      continue;
    }
    if (xml.startsWith("<?", tagStart)) {
      const end = xml.indexOf("?>", tagStart + 2);
      if (end < 0) throw new Error("Unterminated XML processing instruction.");
      offset = end + 2;
      continue;
    }
    if (xml.startsWith("<!", tagStart)) throw new Error("Unsupported XML declaration.");
    const tagEnd = findXmlTagEnd(xml, tagStart + 1);
    let tag = xml.slice(tagStart + 1, tagEnd).trim();
    if (!tag) throw new Error("Empty XML tag.");
    if (tag.startsWith("/")) {
      const closing = tag.slice(1).trim();
      if (!/^[A-Za-z_:][A-Za-z0-9_.:-]*$/.test(closing)) throw new Error("Invalid XML closing tag.");
      const current = stack.pop();
      if (!current || current.name !== closing) throw new Error("Mismatched XML closing tag.");
      offset = tagEnd + 1;
      continue;
    }
    const selfClosing = /\/\s*$/.test(tag);
    if (selfClosing) tag = tag.replace(/\/\s*$/, "").trimEnd();
    const nameMatch = /^([A-Za-z_:][A-Za-z0-9_.:-]*)(?:\s|$)/.exec(tag);
    if (!nameMatch) throw new Error("Invalid XML opening tag.");
    const node = {
      name: nameMatch[1],
      attributes: parseXmlAttributes(tag.slice(nameMatch[0].length)),
      children: [],
      parent: stack.length > 0 ? stack[stack.length - 1] : null,
    };
    if (node.parent) {
      node.parent.children.push(node);
    } else if (root) {
      throw new Error("XML contains multiple roots.");
    } else {
      root = node;
    }
    if (!selfClosing) stack.push(node);
    offset = tagEnd + 1;
  }
  if (stack.length !== 0) throw new Error("XML contains unclosed elements.");
  return root;
}

function findXmlTagEnd(xml, start) {
  let quote = "";
  for (let index = start; index < xml.length; index += 1) {
    const character = xml[index];
    if (quote) {
      if (character === quote) quote = "";
    } else if (character === "\"" || character === "'") {
      quote = character;
    } else if (character === ">") {
      return index;
    }
  }
  throw new Error("Unterminated XML tag.");
}

function parseXmlAttributes(value) {
  const attributes = {};
  let offset = 0;
  const text = String(value || "");
  while (offset < text.length) {
    while (offset < text.length && /\s/.test(text[offset])) offset += 1;
    if (offset >= text.length) break;
    const match = /^([A-Za-z_:][A-Za-z0-9_.:-]*)\s*=\s*(["'])/.exec(text.slice(offset));
    if (!match) throw new Error("Invalid XML attribute.");
    const name = match[1];
    if (Object.prototype.hasOwnProperty.call(attributes, name)) throw new Error("Duplicate XML attribute.");
    const quote = match[2];
    const valueStart = offset + match[0].length;
    const valueEnd = text.indexOf(quote, valueStart);
    if (valueEnd < 0) throw new Error("Unterminated XML attribute.");
    attributes[name] = xmlUnescape(text.slice(valueStart, valueEnd));
    offset = valueEnd + 1;
  }
  return attributes;
}

function xmlUnescape(value) {
  return String(value)
    .replace(/&#x([0-9a-f]+);/gi, (_, digits) => String.fromCodePoint(Number.parseInt(digits, 16)))
    .replace(/&#([0-9]+);/g, (_, digits) => String.fromCodePoint(Number.parseInt(digits, 10)))
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&amp;/g, "&");
}

function xmlDescendants(node, name, includeSelf) {
  const result = [];
  const visit = (current, include) => {
    if (include && current.name === name) result.push(current);
    current.children.forEach((child) => visit(child, true));
  };
  visit(node, includeSelf);
  return result;
}

function xmlIsDescendant(node, ancestor) {
  let current = node;
  while (current) {
    if (current === ancestor) return true;
    current = current.parent;
  }
  return false;
}

function assertJUnitAggregate(attributes, actual) {
  const declared = {};
  for (const key of ["tests", "failures", "errors", "skipped"]) {
    const raw = attributes[key];
    if (typeof raw !== "string" || !/^(?:0|[1-9][0-9]*)$/.test(raw)) {
      throw new Error(`JUnit aggregate ${key} must be an explicit non-negative integer.`);
    }
    const value = Number(raw);
    if (!Number.isSafeInteger(value)) throw new Error(`JUnit aggregate ${key} exceeds the safe integer range.`);
    declared[key] = value;
    if (value !== actual[key]) throw new Error(`JUnit aggregate ${key} does not match testcase results.`);
  }
  return declared;
}

function validateSoakSampleProcEvidence(sample, index, blockers) {
  const prefix = `sample ${index}`;
  const attempts = sample && sample.remoteProcCaptureAttempts;
  if (!Number.isInteger(attempts) || attempts < 1 || attempts > 5) {
    blockers.push(`${prefix} remoteProcCaptureAttempts must be within 1..5`);
  }
  const status = sample && sample.remoteProcCaptureStatus;
  if (status === "captured") {
    if (!sample || sample.procMetricsAvailable !== true) {
      blockers.push(`${prefix} captured proc metrics must be marked available`);
    }
    if (!sample || !Number.isFinite(sample.procRssKb) || sample.procRssKb < 0) {
      blockers.push(`${prefix} procRssKb must be a non-negative number`);
    }
    if (!sample || !Number.isFinite(sample.procThreadCount) || sample.procThreadCount <= 0) {
      blockers.push(`${prefix} procThreadCount must be positive`);
    }
    if (sample && sample.remoteProcCaptureFailure !== null) {
      blockers.push(`${prefix} captured proc metrics must not report a failure`);
    }
    if (sample && sample.remoteProcFallbackLiveness !== null) {
      blockers.push(`${prefix} captured proc metrics must not report fallback liveness`);
    }
    return;
  }
  if (status !== "unavailable_but_plugin_alive") {
    blockers.push(`${prefix} remoteProcCaptureStatus is invalid`);
    return;
  }
  if (!sample || sample.procMetricsAvailable !== false) {
    blockers.push(`${prefix} unavailable proc metrics must be marked unavailable`);
  }
  if (!sample || sample.procRssKb !== null || sample.procThreadCount !== null) {
    blockers.push(`${prefix} unavailable proc metrics must remain null`);
  }
  if (attempts !== 5) {
    blockers.push(`${prefix} unavailable proc metrics must exhaust all 5 capture attempts`);
  }
  const failure = sample && sample.remoteProcCaptureFailure;
  if (typeof failure !== "string" || !failure || failure.length > 256) {
    blockers.push(`${prefix} unavailable proc metrics require a bounded failure diagnostic`);
  }
  const fallback = sample && sample.remoteProcFallbackLiveness;
  const fallbackCheckedAtEpochMs = fallback && fallback.checkedAtEpochMs;
  const sampleCapturedAtEpochMs = sample && sample.capturedAtEpochMs;
  if (!fallback || fallback.passed !== true ||
      fallback.runtimeReady !== true ||
      fallback.persistentProcessRuntime !== true ||
      fallback.cycle !== (sample && sample.cycle) ||
      fallback.mode !== "non_destructive_binder_session_health_and_recoverability" ||
      fallback.trigger !== "remote_proc_probe_unavailable" ||
      fallback.probeFailure !== failure ||
      fallback.servicePid !== (sample && sample.servicePid) ||
      fallback.hostSessionGeneration !== (sample && sample.pluginSessionGeneration) ||
      !Number.isFinite(fallbackCheckedAtEpochMs) ||
      !Number.isFinite(sampleCapturedAtEpochMs) ||
      Math.abs(fallbackCheckedAtEpochMs - sampleCapturedAtEpochMs) >
        REMOTE_PROC_FALLBACK_CLOCK_TOLERANCE_MS) {
    blockers.push(`${prefix} unavailable proc metrics require matching live Binder identity evidence`);
  }
}

function validateApkBuilderPluginProvenance(
  apkBuilderPlugin,
  artifactsValue,
  capturedIdentityValue,
  blockers,
) {
  const artifacts = artifactsValue || {};
  const capturedIdentity = capturedIdentityValue || {};
  const signerSha256 = stringValue(apkBuilderPlugin.signerSha256).toLowerCase();
  if (stringValue(apkBuilderPlugin.packageName) !== APK_BUILDER_PLUGIN_PACKAGE) {
    blockers.push(`APK Builder plugin packageName must be ${APK_BUILDER_PLUGIN_PACKAGE}`);
  }
  if (stringValue(apkBuilderPlugin.buildTask) !== APK_BUILDER_PLUGIN_TASK) {
    blockers.push(`APK Builder plugin buildTask must be ${APK_BUILDER_PLUGIN_TASK}`);
  }
  if (apkBuilderPlugin.remoteBuildEnabled !== true) {
    blockers.push("APK Builder plugin remoteBuildEnabled must be true");
  }
  if (!/^(?:[0-9a-f]{40}|[0-9a-f]{64})$/i.test(stringValue(apkBuilderPlugin.hostCommit)) ||
      stringValue(apkBuilderPlugin.hostCommit) !== stringValue(capturedIdentity.hostCommit)) {
    blockers.push("APK Builder plugin hostCommit must match the captured clean host source identity");
  }
  if (!SHA256_PATTERN.test(signerSha256)) {
    blockers.push("APK Builder plugin signerSha256 is invalid");
  }
  if (apkBuilderPlugin.installedMatchesFreshBuild !== true) {
    blockers.push("APK Builder plugin installedMatchesFreshBuild must be true");
  }
  if (apkBuilderPlugin.installedMatchesFreshBuild !== artifacts.installedApkBuilderPluginMatchesFreshBuild) {
    blockers.push("APK Builder plugin provenance does not match the artifact installation attestation");
  }
  if (apkBuilderPlugin.installedUnchangedDuringTest !== true) {
    blockers.push("APK Builder plugin installedUnchangedDuringTest must be true");
  }
  if (apkBuilderPlugin.installedUnchangedDuringTest !==
      artifacts.installedApkBuilderPluginUnchangedDuringTest) {
    blockers.push("APK Builder plugin provenance does not match the artifact unchanged-during-test attestation");
  }
  const servicePreflight = apkBuilderPlugin.servicePreflight || {};
  const components = Array.isArray(servicePreflight.components)
    ? servicePreflight.components.map(stringValue)
    : [];
  if (stringValue(servicePreflight.action) !== APK_BUILDER_PLUGIN_ACTION ||
      stringValue(servicePreflight.expectedComponent) !== APK_BUILDER_PLUGIN_COMPONENT ||
      servicePreflight.matched !== true ||
      !components.includes(APK_BUILDER_PLUGIN_COMPONENT)) {
    blockers.push(
      `APK Builder plugin servicePreflight must prove ${APK_BUILDER_PLUGIN_COMPONENT} for ${APK_BUILDER_PLUGIN_ACTION}`,
    );
  }
}

function evaluateMatrix(deviceReports) {
  const blockers = [];
  const primary = deviceReports.find((entry) => entry.id === "sony-arm64");
  const secondary = deviceReports.find((entry) => entry.id === "second-oem-arm64");
  const emulator = deviceReports.find((entry) => entry.id === "emulator-x86_64");
  if (primary && secondary && primary.available && secondary.available) {
    const first = stringValue(primary.device.manufacturer).toLowerCase();
    const second = stringValue(secondary.device.manufacturer).toLowerCase();
    if (first && first === second) blockers.push("primary and secondary OEM evidence use the same manufacturer");
    if (stringValue(primary.device.serial) === stringValue(secondary.device.serial)) {
      blockers.push("primary and secondary OEM evidence use the same device serial");
    }
  }
  if (emulator && emulator.available && emulator.device.isEmulator !== true) {
    blockers.push("x86_64 matrix evidence is not marked as an emulator");
  }
  return blockers;
}

function validateContractAnchors(repoRoot) {
  const registry = readJsonIfExists(path.join(repoRoot, "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"));
  const contract = registry && registry.r6EvidenceRevalidation;
  const instrumentationGate = read(path.join(repoRoot, "app/node-instrumentation-gates.gradle.kts"));
  const releaseGate = read(path.join(repoRoot, "app/embedded-node-packaging-preflight.gradle.kts"));
  const deviceCollector = read(path.join(repoRoot, "tools/nodejs/r6/run-device-evidence.js"));
  const soakCollector = read(path.join(repoRoot, "tools/nodejs/r6/run-production-soak.js"));
  const soakInstrumentation = read(
    path.join(
      repoRoot,
      "app/src/androidTest/java/org/autojs/autojs/engine/NodeR6PersistentPluginSoakInstrumentationTest.kt",
    ),
  );
  const packagedSmokeInstrumentation = read(
    path.join(
      repoRoot,
      "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
    ),
  );
  const manifest = read(path.join(repoRoot, "app/src/main/AndroidManifest.xml"));
  const embeddedService = read(
    path.join(
      repoRoot,
      "app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt",
    ),
  );
  const interactiveStatusStore = read(
    path.join(
      repoRoot,
      "app/src/main/java/org/autojs/autojs/engine/NodeInteractiveLongRunningStatusStore.kt",
    ),
  );
  const runtimeKitBuild = read(path.join(repoRoot, "app/build.gradle.kts"));
  const apkBuilderPluginBuild = read(
    path.join(repoRoot, "plugins/apk-builder-template/build.gradle.kts"),
  );
  const remoteApkBuilder = read(
    path.join(
      repoRoot,
      "plugins/apk-builder-template/src/main/java/org/autojs/plugin/apkbuilder/template/impl/RemoteApkLightweightBuilder.kt",
    ),
  );
  const violations = [];
  if (!contract || contract.blocksCoreCompletion !== true) {
    violations.push("FINAL_BLOCKER_REGISTRY must declare blocking R6 evidence revalidation");
  }
  if (contract && contract.lifecycleModel !== LIFECYCLE_MODEL) {
    violations.push(`R6 registry lifecycle model must be ${LIFECYCLE_MODEL}`);
  }
  if (contract && contract.distributionPolicy !== "nodePlugin_required_plugin") {
    violations.push("R6 registry distribution policy must be nodePlugin_required_plugin");
  }
  REQUIRED_DEVICE_SELECTORS.forEach((selector) => {
    if (!instrumentationGate.includes(`"${selector}"`)) {
      violations.push(`R6 focused device gate is missing selector ${selector}`);
    }
  });
  if (!releaseGate.includes('dependsOn("verifyNodeFinalIntegrationGate")')) {
    violations.push("verifyNodeReleaseGate must depend on the strict final integration gate");
  }
  if (!deviceCollector.includes("autojs6-node-r6-device-evidence-runner-v1")) {
    violations.push("R6 production device evidence collector is missing");
  }
  if (!deviceCollector.includes(PACKAGED_TEMPLATE_TASK) ||
      !deviceCollector.includes("packagedSmokeTemplateOverride") ||
      !deviceCollector.includes("packagedSmokeTemplateExactPath")) {
    violations.push("R6 production device collector must prepare and enable a fresh packaged smoke template");
  }
  if (!deviceCollector.includes("packagedSmokeTargetAbi")) {
    violations.push("R6 production device collector must bind packaged smoke to the target runtime ABI");
  }
  if (!deviceCollector.includes("packaged-template-device-sha256-after-test") ||
      !deviceCollector.includes("packaged-template-device-cleanup")) {
    violations.push("R6 production device collector must rehash and clean its run-owned packaged smoke template");
  }
  if (!deviceCollector.includes(APK_BUILDER_PLUGIN_TASK) ||
      !deviceCollector.includes(APK_BUILDER_PLUGIN_PACKAGE) ||
      !deviceCollector.includes("autojs.apkBuilder.templatePlugin.enableRemoteBuild") ||
      !deviceCollector.includes("apkBuilderPluginApkSha256") ||
      !deviceCollector.includes("apkBuilderPluginApkBeforeTestSha256") ||
      !deviceCollector.includes("apkBuilderPluginBuildApkSha256") ||
      !deviceCollector.includes("installedApkBuilderPluginMatchesFreshBuild") ||
      !deviceCollector.includes("installedApkBuilderPluginUnchangedDuringTest") ||
      !deviceCollector.includes("apk-builder-plugin-service-preflight") ||
      !deviceCollector.includes('"query-services"') ||
      !deviceCollector.includes(APK_BUILDER_PLUGIN_ACTION) ||
      !deviceCollector.includes(APK_BUILDER_PLUGIN_SERVICE)) {
    violations.push(
      "R6 production device collector must build, install, and attest the current-source release APK Builder plugin",
    );
  }
  const packagedTemplateBuildIndex = deviceCollector.indexOf(
    "args: buildPackagedTemplateGradleArgs(identityBefore.host.commit)",
  );
  const apkBuilderPluginBuildIndex = deviceCollector.indexOf(
    "args: buildApkBuilderPluginGradleArgs(identityBefore.host.commit)",
  );
  if (packagedTemplateBuildIndex < 0 ||
      apkBuilderPluginBuildIndex <= packagedTemplateBuildIndex ||
      !deviceCollector.includes('label: "packaged-template-build"') ||
      !deviceCollector.includes('label: "apk-builder-plugin-build"')) {
    violations.push(
      "R6 production device collector must build the packaged template and APK Builder plugin in separate ordered Gradle invocations",
    );
  }
  if (!deviceCollector.includes('"--no-daemon"') ||
      !deviceCollector.includes('"-Pkotlin.compiler.execution.strategy=in-process"')) {
    violations.push("R6 production device collector must isolate all Gradle and Kotlin compiler invocations");
  }
  const shellReceiverName = "org.autojs.autojs.engine.NativeNodeEmbeddedInteractiveShellReceiver";
  const shellReceiverStart = manifest.indexOf(`android:name="${shellReceiverName}"`);
  const shellReceiverEnd = shellReceiverStart < 0 ? -1 : manifest.indexOf("/>", shellReceiverStart);
  const shellReceiver = shellReceiverStart >= 0 && shellReceiverEnd > shellReceiverStart
    ? manifest.slice(shellReceiverStart, shellReceiverEnd)
    : "";
  if (!shellReceiver.includes('android:exported="true"') ||
      !shellReceiver.includes('android:permission="android.permission.DUMP"') ||
      !shellReceiver.includes('android:process=":nodejs_embedded"') ||
      !embeddedService.includes("class NativeNodeEmbeddedInteractiveShellReceiver : BroadcastReceiver()") ||
      !embeddedService.includes("if (!isOrderedBroadcast)") ||
      !embeddedService.includes("Base64.encodeToString(it, Base64.NO_WRAP)") ||
      !interactiveStatusStore.includes("const val SHELL_QUERY_ACTION_NAME") ||
      !interactiveStatusStore.includes("const val SHELL_STOP_ACTION_NAME") ||
      !packagedSmokeInstrumentation.includes(`-n $packageName/${shellReceiverName}`) ||
      !packagedSmokeInstrumentation.includes(
        "NodeInteractiveLongRunningStatusStore.SHELL_STATUS_RESULT_PREFIX",
      ) ||
      packagedSmokeInstrumentation.includes("run-as $packageName")) {
    violations.push(
      "R6 packaged production lifecycle evidence must use the DUMP-protected shell receiver without run-as",
    );
  }
  const runtimeKitBaselineAbis = ["arm64-v8a", "armeabi-v7a", "x86_64", "x86"];
  const runtimeKitBaselineLibraries = ["libjackpal-androidterm5.so", "libjackpal-termexec2.so"];
  if (!runtimeKitBuild.includes("writeRuntimeKitTemplateWithBaselineNativeLibraries(") ||
      !runtimeKitBuild.includes("libs/jackpal-androidterm-libtermexec-1_0/libtermexec-release.aar") ||
      runtimeKitBaselineAbis.some((abi) =>
        !runtimeKitBuild.includes(`"${abi}"`) || !apkBuilderPluginBuild.includes(`"${abi}"`)) ||
      runtimeKitBaselineLibraries.some((library) =>
        !runtimeKitBuild.includes(`"${library}"`) || !apkBuilderPluginBuild.includes(`"${library}"`)) ||
      !runtimeKitBuild.includes('"jni/$abi/$libraryName"') ||
      !runtimeKitBuild.includes('"lib/$abi/$libraryName"') ||
      !apkBuilderPluginBuild.includes("Runtime Kit template APK is missing baseline native library") ||
      !remoteApkBuilder.includes("required native libraries in the Runtime Kit template") ||
      !remoteApkBuilder.includes("or host build inputs") ||
      remoteApkBuilder.includes("requires native libraries, but the host did not provide a build input archive")) {
    violations.push(
      "R6 remote APK Builder Runtime Kit must carry the cross-ABI terminal baseline and accept template-provided libraries",
    );
  }
  if (!deviceCollector.includes("org.gradle.daemon.registry.base") ||
      !deviceCollector.includes("gradle-daemon-registry") ||
      !deviceCollector.includes(
        "buildIsolatedGradleArgs(config.context.runRoot, config.args)",
      )) {
    violations.push("R6 production device collector must use a per-run Gradle daemon registry");
  }
  if (!soakCollector.includes("autojs6-node-r6-persistent-plugin-soak-runner-v1") ||
      !soakCollector.includes('"--no-daemon"') ||
      !soakCollector.includes('"-Pkotlin.compiler.execution.strategy=in-process"') ||
      !soakCollector.includes("org.gradle.daemon.registry.base") ||
      !soakCollector.includes("gradle-daemon-registry") ||
      !soakCollector.includes(
        "buildIsolatedGradleArgs(runRoot, buildPluginGradleArgs())",
      ) ||
      !soakCollector.includes("buildIsolatedGradleArgs(runRoot, gradleArgs)")) {
    violations.push(
      "R6 production soak collector must isolate plugin and connected builds with a per-run Gradle daemon registry",
    );
  }
  if (!soakInstrumentation.includes("PowerManager.PARTIAL_WAKE_LOCK") ||
      !soakInstrumentation.includes("acquire(durationMs + WAKE_LOCK_GRACE_MS)") ||
      !soakInstrumentation.includes('.put("wakeLockHeldUntilCompletion", wakeLockHeldUntilCompletion)')) {
    violations.push("R6 production soak must prove a bounded partial wake lock remained held until completion");
  }
  if (!soakInstrumentation.includes("private const val CYCLE_TIMEOUT_MS = 15_000L") ||
      !soakInstrumentation.includes('.put("cycleTimeoutMs", CYCLE_TIMEOUT_MS)')) {
    violations.push("R6 production soak must retain and report the 15-second per-cycle timeout");
  }
  if (!soakInstrumentation.includes("retryOptionalRemoteMetric") ||
      !soakInstrumentation.includes('.put("procMetricsAvailable", proc.value != null)') ||
      !soakInstrumentation.includes('"remote_proc_probe_unavailable"')) {
    violations.push("R6 production soak must treat cross-UID proc sampling as optional with live Binder revalidation");
  }
  return violations;
}

function packagedTemplatePathForRunToken(runToken) {
  const value = stringValue(runToken);
  return RUN_TOKEN_PATTERN.test(value)
    ? `${PACKAGED_TEMPLATE_DEVICE_PATH_PREFIX}${value}.apk`
    : "";
}

function currentIdentity(repoRoot, pluginRoot) {
  const host = gitIdentity(repoRoot);
  const plugin = pluginRoot && fs.existsSync(pluginRoot)
    ? gitIdentity(pluginRoot)
    : { root: pluginRoot || "", commit: "", clean: false, commitTime: "" };
  const version = readVersion(path.join(repoRoot, "version.properties"));
  const blockers = [];
  if (!host.commit) blockers.push("current host source identity is unavailable");
  if (!host.clean) blockers.push("current host worktree is not clean");
  if (!pluginRoot || !fs.existsSync(pluginRoot)) blockers.push("runtime plugin project is unavailable");
  if (!plugin.commit) blockers.push("current runtime plugin source identity is unavailable");
  if (!plugin.clean) blockers.push("current runtime plugin worktree is not clean");
  if (!version.name || !version.build) blockers.push("current host version identity is incomplete");
  return {
    host: {
      root: relativePath(repoRoot, repoRoot),
      commit: host.commit,
      clean: host.clean,
      commitTime: host.commitTime,
    },
    plugin: {
      root: pluginRoot ? relativePath(repoRoot, pluginRoot) : "missing",
      commit: plugin.commit,
      clean: plugin.clean,
      commitTime: plugin.commitTime,
    },
    version,
    blockers,
  };
}

function gitIdentity(root) {
  const commit = run(root, ["git", "rev-parse", "HEAD"]);
  const status = run(root, ["git", "status", "--porcelain", "--untracked-files=normal"]);
  const commitTime = run(root, ["git", "show", "-s", "--format=%cI", "HEAD"]);
  return {
    root,
    commit,
    clean: Boolean(commit) && status === "",
    commitTime,
  };
}

function readVersion(file) {
  const values = {};
  read(file).split(/\r?\n/).forEach((line) => {
    const index = line.indexOf("=");
    if (index > 0) values[line.slice(0, index).trim()] = line.slice(index + 1).trim();
  });
  return {
    name: values.VERSION_NAME || "",
    build: values.VERSION_BUILD || "",
  };
}

function resolvePluginRoot(repoRoot, explicit) {
  if (explicit) return path.resolve(explicit);
  const workspace = path.dirname(repoRoot);
  const candidates = [
    path.join(workspace, "AutoJs6-Plugin-NodeJs-Runtime"),
    path.join(workspace, "autojs6-plugin-nodejs"),
  ];
  return candidates.find((candidate) => fs.existsSync(candidate)) || candidates[0];
}

function normalizeTests(value) {
  const tests = value || {};
  const selectors = Array.isArray(tests.selectors)
    ? tests.selectors.map((entry) => ({
        selector: stringValue(entry && entry.selector),
        status: stringValue(entry && entry.status),
      }))
    : [];
  return {
    total: numberValue(tests.total),
    failures: numberValue(tests.failures),
    errors: numberValue(tests.errors),
    skipped: numberValue(tests.skipped),
    selectors,
  };
}

function emptyDeviceResult(requirement, file, blockers) {
  return {
    id: requirement.id,
    role: requirement.role,
    path: normalizeEvidencePath(requirement.relative),
    available: false,
    sha256: null,
    generatedAt: "",
    device: {},
    identity: {},
    tests: normalizeTests(null),
    blockers,
    ready: false,
  };
}

function renderMarkdown(report) {
  const lines = [
    "# R6 Production Evidence Gate",
    "",
    `- Decision: ${report.decision.status}`,
    `- Host commit: ${report.identity.host.commit || "missing"}`,
    `- Plugin commit: ${report.identity.plugin.commit || "missing"}`,
    `- Lifecycle: ${report.lifecycleModel}`,
    `- Distribution: ${report.distribution.releaseFlavor}/${report.distribution.backend}`,
    `- Device matrix ready: ${report.decision.deviceMatrixReady}`,
    `- Soak ready: ${report.decision.soakReady}`,
    `- Zero skipped tests: ${report.decision.zeroSkippedTests}`,
    "",
    "## Device Evidence",
    "",
    "| Evidence | Role | Available | Ready | Device | ABI | Tests | Skipped |",
    "| --- | --- | --- | --- | --- | --- | ---: | ---: |",
  ];
  report.devices.forEach((entry) => {
    lines.push(
      `| ${entry.id} | ${entry.role} | ${entry.available} | ${entry.ready} | ${stringValue(entry.device.manufacturer)} ${stringValue(entry.device.model)}` +
      ` | ${stringValue(entry.device.runtimeProcessAbi)} | ${entry.tests.total} | ${entry.tests.skipped} |`,
    );
  });
  lines.push("", "## Soak Evidence", "", "| Evidence | Available | Ready | Duration | Samples |", "| --- | --- | --- | ---: | ---: |");
  report.soaks.forEach((entry) => {
    lines.push(`| ${entry.id} | ${entry.available} | ${entry.ready} | ${entry.durationHours} h | ${entry.samples} |`);
  });
  lines.push("", "## Blockers", "");
  if (report.blockers.length === 0) lines.push("None.");
  else report.blockers.forEach((message) => lines.push(`- ${message}`));
  lines.push("", "## Policy Violations", "");
  if (report.policyViolations.length === 0) lines.push("None.");
  else report.policyViolations.forEach((message) => lines.push(`- ${message}`));
  return lines.join("\n") + "\n";
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-blocked"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log(
        "Usage: node verify-production-evidence.js [--repo-root dir] [--plugin-root dir] " +
        "[--evidence-root dir] [--report-dir dir] [--fail-on-blocked]",
      );
      process.exit(0);
    }
    if (!arg.startsWith("--")) throw new Error(`Unexpected positional argument: ${arg}`);
    const eq = arg.indexOf("=");
    const rawKey = eq >= 0 ? arg.slice(2, eq) : arg.slice(2);
    const key = rawKey.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
    if (booleanArgs.has(rawKey)) {
      options[key] = eq >= 0 ? parseBoolean(arg.slice(eq + 1)) : true;
      continue;
    }
    const value = eq >= 0 ? arg.slice(eq + 1) : argv[index + 1];
    if (eq < 0) index += 1;
    if (!value || value.startsWith("--")) throw new Error(`Missing value for ${arg}`);
    options[key] = value;
  }
  return options;
}

function parseBoolean(value) {
  return ["1", "true", "yes", "on"].includes(String(value).toLowerCase());
}

function readJsonIfExists(file) {
  if (!fs.existsSync(file)) return null;
  try {
    return JSON.parse(read(file).replace(/^\uFEFF/, ""));
  } catch (_) {
    return null;
  }
}

function run(cwd, command) {
  const result = childProcess.spawnSync(command[0], command.slice(1), {
    cwd,
    encoding: "utf8",
    windowsHide: true,
  });
  return result.status === 0 ? stringValue(result.stdout).trim() : "";
}

function sha256(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function read(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "";
}

function relativePath(root, file) {
  return path.relative(root, file).replace(/\\/g, "/") || ".";
}

function isInsideOrSame(root, candidate) {
  const relative = path.relative(root, candidate);
  return relative === "" || (!relative.startsWith(`..${path.sep}`) && relative !== ".." && !path.isAbsolute(relative));
}

function normalizeEvidencePath(value) {
  return `build/reports/nodejs/r6-evidence/${value}`.replace(/\\/g, "/");
}

function stringValue(value) {
  return value == null ? "" : String(value);
}

function numberValue(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

if (require.main === module) {
  main();
}

module.exports = {
  DEVICE_REQUIREMENTS,
  DEVICE_SCHEMA,
  LIFECYCLE_MODEL,
  REQUIRED_ARTIFACT_HASHES,
  REQUIRED_DEVICE_ARTIFACT_HASHES,
  REQUIRED_DEVICE_SELECTORS,
  REQUIRED_REGRESSIONS,
  REQUIRED_SOAK_METRICS,
  SOAK_CLOCK_TOLERANCE_MS,
  SOAK_REQUIREMENTS,
  SOAK_SCHEMA,
  apkSignerCertificateSha256Digests,
  currentIdentity,
  evaluateDeviceReport,
  evaluateSoakReport,
  validateSoakSampleProcEvidence,
};
