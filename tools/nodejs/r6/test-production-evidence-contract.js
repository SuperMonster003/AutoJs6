#!/usr/bin/env node
"use strict";

const assert = require("assert");
const childProcess = require("child_process");
const crypto = require("crypto");
const fs = require("fs");
const os = require("os");
const path = require("path");

const {
  DEVICE_REQUIREMENTS,
  DEVICE_SCHEMA,
  LIFECYCLE_MODEL,
  REQUIRED_ARTIFACT_HASHES,
  REQUIRED_DEVICE_ARTIFACT_HASHES,
  REQUIRED_DEVICE_SELECTORS,
  REQUIRED_REGRESSIONS,
  REQUIRED_SOAK_METRICS,
  SOAK_REQUIREMENTS,
  SOAK_SCHEMA,
  evaluateDeviceReport,
  evaluateSoakReport,
} = require("./verify-production-evidence");
const {
  buildEvidenceGradleArgs,
  buildPackagedTemplateGradleArgs,
  packagedTemplatePathForRunToken,
  parseApkSignerOutput,
  selectAbiApk,
  selectFreshJUnit,
  snapshotJUnitFiles: snapshotDeviceJUnitFiles,
} = require("./run-device-evidence");
const {
  assertFreshArtifact,
  buildGradleArgs: buildSoakGradleArgs,
  selectFreshSoakJUnit,
  snapshotJUnitFiles: snapshotSoakJUnitFiles,
} = require("./run-production-soak");

const identity = {
  host: { commit: "1".repeat(40) },
  plugin: { commit: "2".repeat(40) },
  version: { name: "6.8.0 Alpha7", build: "5223" },
};
let contractRoot = null;
let contractAndroidTools = null;
let contractKeystore = null;
const contractApkCache = new Map();
const RUNTIME_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.nodejs";
const RUNTIME_PLUGIN_SERVICE = `${RUNTIME_PLUGIN_PACKAGE}.NodeJsRuntimePluginService`;
const R5_PLUGIN_PREFLIGHT_SELECTOR =
  "org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_01_livePluginPreflightNegotiatesContractAndReportsTrustIdentity";
const R6_PLUGIN_SOAK_SELECTOR =
  "org.autojs.autojs.engine.NodeR6PersistentPluginSoakInstrumentationTest#persistentRequiredPluginSoakProducesR6Evidence";

function main() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "autojs6-r6-evidence-contract-"));
  contractRoot = root;
  try {
    prepareContractApkTools();
    assertApkSignerOutputContracts();
    assertDeviceRunnerGradleContracts();
    assertPackagedTemplateAbiSelectionContract(root);
    assertPackagedTemplateSourceContract();
    for (const requirement of DEVICE_REQUIREMENTS) {
      writeJson(root, requirement.relative, validDeviceReport(requirement));
      const result = evaluateDeviceReport(root, requirement, identity, root);
      assert.strictEqual(result.ready, true, `${requirement.id}: ${result.blockers.join("; ")}`);
    }

    const skippedRequirement = DEVICE_REQUIREMENTS[0];
    const skipped = validDeviceReport(skippedRequirement);
    skipped.tests.skipped = 1;
    writeJson(root, skippedRequirement.relative, skipped);
    const skippedResult = evaluateDeviceReport(root, skippedRequirement, identity, root);
    assert.strictEqual(skippedResult.ready, false);
    assert(skippedResult.blockers.some((message) => message.includes("skipped tests must be zero")));

    for (const requirement of SOAK_REQUIREMENTS) {
      writeJson(root, requirement.relative, validSoakReport(requirement));
      const result = evaluateSoakReport(root, requirement, identity, root);
      assert.strictEqual(result.ready, true, `${requirement.id}: ${result.blockers.join("; ")}`);
    }

    const invalidDeviceTokenRequirement = DEVICE_REQUIREMENTS[0];
    const invalidDeviceToken = validDeviceReport(invalidDeviceTokenRequirement);
    invalidDeviceToken.runToken = "G".repeat(64);
    writeJson(root, invalidDeviceTokenRequirement.relative, invalidDeviceToken);
    const invalidDeviceTokenResult = evaluateDeviceReport(root, invalidDeviceTokenRequirement, identity, root);
    assert.strictEqual(invalidDeviceTokenResult.ready, false);
    assert(invalidDeviceTokenResult.blockers.some((message) => message.includes("64 hexadecimal characters")));

    const invalidSoakTokenRequirement = SOAK_REQUIREMENTS[0];
    const invalidSoakToken = validSoakReport(invalidSoakTokenRequirement);
    invalidSoakToken.runToken = "a".repeat(63);
    writeJson(root, invalidSoakTokenRequirement.relative, invalidSoakToken);
    const invalidSoakTokenResult = evaluateSoakReport(root, invalidSoakTokenRequirement, identity, root);
    assert.strictEqual(invalidSoakTokenResult.ready, false);
    assert(invalidSoakTokenResult.blockers.some((message) => message.includes("64 hexadecimal characters")));

    const fakeSignerRequirement = SOAK_REQUIREMENTS[0];
    const fakeSigner = validSoakReport(fakeSignerRequirement);
    fakeSigner.plugin.signerSha256 = fakeSigner.plugin.signerSha256 === "f".repeat(64)
      ? "e".repeat(64)
      : "f".repeat(64);
    writeJson(root, fakeSignerRequirement.relative, fakeSigner);
    const fakeSignerResult = evaluateSoakReport(root, fakeSignerRequirement, identity, root);
    assert.strictEqual(fakeSignerResult.ready, false);
    assert(fakeSignerResult.blockers.some((message) => message.includes("does not match the runtime plugin APK signer")));

    const fakeDeviceSignerRequirement = DEVICE_REQUIREMENTS[0];
    const fakeDeviceSigner = validDeviceReport(fakeDeviceSignerRequirement);
    fakeDeviceSigner.plugin.signerSha256 = fakeDeviceSigner.plugin.signerSha256 === "f".repeat(64)
      ? "e".repeat(64)
      : "f".repeat(64);
    writeJson(root, fakeDeviceSignerRequirement.relative, fakeDeviceSigner);
    const fakeDeviceSignerResult = evaluateDeviceReport(root, fakeDeviceSignerRequirement, identity, root);
    assert.strictEqual(fakeDeviceSignerResult.ready, false);
    assert(fakeDeviceSignerResult.blockers.some((message) => message.includes("does not match the runtime plugin APK signer")));

    const untrustedDevicePluginRequirement = DEVICE_REQUIREMENTS[0];
    const untrustedDevicePlugin = validDeviceReport(untrustedDevicePluginRequirement);
    untrustedDevicePlugin.plugin.trusted = false;
    writeJson(root, untrustedDevicePluginRequirement.relative, untrustedDevicePlugin);
    const untrustedDevicePluginResult = evaluateDeviceReport(root, untrustedDevicePluginRequirement, identity, root);
    assert.strictEqual(untrustedDevicePluginResult.ready, false);
    assert(untrustedDevicePluginResult.blockers.some((message) => message.includes("device plugin trusted must be true")));

    const underDurationRequirement = SOAK_REQUIREMENTS[1];
    const underDuration = validSoakReport(underDurationRequirement);
    underDuration.durationHours = 1;
    writeJson(root, underDurationRequirement.relative, underDuration);
    const underDurationResult = evaluateSoakReport(root, underDurationRequirement, identity, root);
    assert.strictEqual(underDurationResult.ready, false);
    assert(underDurationResult.blockers.some((message) => message.includes("below 24h")));

    const missingTimelineRequirement = SOAK_REQUIREMENTS[0];
    const missingTimeline = validSoakReport(missingTimelineRequirement);
    delete missingTimeline.startedAtEpochMs;
    delete missingTimeline.endedAtEpochMs;
    missingTimeline.samples.forEach((sample) => {
      delete sample.capturedAtEpochMs;
      delete sample.elapsedMs;
    });
    writeJson(root, missingTimelineRequirement.relative, missingTimeline);
    const missingTimelineResult = evaluateSoakReport(root, missingTimelineRequirement, identity, root);
    assert.strictEqual(missingTimelineResult.ready, false);
    assert(missingTimelineResult.blockers.some((message) => message.includes("wall-clock interval")));
    assert(missingTimelineResult.blockers.some((message) => message.includes("capturedAtEpochMs")));

    const missingCountRequirement = DEVICE_REQUIREMENTS[0];
    const missingCount = validDeviceReport(missingCountRequirement);
    delete missingCount.tests.errors;
    writeJson(root, missingCountRequirement.relative, missingCount);
    const missingCountResult = evaluateDeviceReport(root, missingCountRequirement, identity, root);
    assert.strictEqual(missingCountResult.ready, false);
    assert(missingCountResult.blockers.some((message) => message.includes("tests.errors must be an explicit")));

    const fakeHashRequirement = DEVICE_REQUIREMENTS[0];
    const fakeHash = validDeviceReport(fakeHashRequirement);
    fakeHash.artifacts.hostApkSha256 = "f".repeat(64);
    writeJson(root, fakeHashRequirement.relative, fakeHash);
    const fakeHashResult = evaluateDeviceReport(root, fakeHashRequirement, identity, root);
    assert.strictEqual(fakeHashResult.ready, false);
    assert(fakeHashResult.blockers.some((message) => message.includes("hostApkSha256 does not match")));

    const fakeTemplateRequirement = DEVICE_REQUIREMENTS[0];
    const fakeTemplate = validDeviceReport(fakeTemplateRequirement);
    fakeTemplate.artifacts.packagedTemplateDeviceSha256AfterTest = "f".repeat(64);
    writeJson(root, fakeTemplateRequirement.relative, fakeTemplate);
    const fakeTemplateResult = evaluateDeviceReport(root, fakeTemplateRequirement, identity, root);
    assert.strictEqual(fakeTemplateResult.ready, false);
    assert(fakeTemplateResult.blockers.some((message) => message.includes("device packaged template must match")));

    const templateBindingMutations = [
      ["packagedTemplateDevicePath", "/data/local/tmp/autojs6-r6-packaged-template-" + "b".repeat(64) + ".apk", "report run token"],
      ["packagedTemplateExactPathArgument", "autojs.embeddedNode.packagedSmokeTemplateExactPath=/data/local/tmp/wrong.apk", "exact-path"],
      ["packagedTemplateOverrideArgument", "autojs.embeddedNode.packagedSmokeTemplateOverride=false", "override instrumentation"],
      ["packagedTemplateTargetAbi", "x86", "target ABI"],
      ["packagedTemplateTargetAbiArgument", "autojs.embeddedNode.packagedSmokeTargetAbi=x86", "target-ABI instrumentation"],
      ["packagedTemplateDeviceSha256BeforeTest", "e".repeat(64), "before and after testing"],
      ["packagedTemplateDeviceRemovedAfterTest", false, "must be removed"],
    ];
    templateBindingMutations.forEach(([field, value, blockerText]) => {
      const report = validDeviceReport(fakeTemplateRequirement);
      report.artifacts[field] = value;
      writeJson(root, fakeTemplateRequirement.relative, report);
      const result = evaluateDeviceReport(root, fakeTemplateRequirement, identity, root);
      assert.strictEqual(result.ready, false, `${field} mutation must fail`);
      assert(result.blockers.some((message) => message.includes(blockerText)), `${field}: ${result.blockers.join("; ")}`);
    });

    const invalidMetricsRequirement = SOAK_REQUIREMENTS[0];
    const invalidMetrics = validSoakReport(invalidMetricsRequirement);
    invalidMetrics.samples[1].fdCount = -1;
    invalidMetrics.samples[1].servicePid = 101;
    writeJson(root, invalidMetricsRequirement.relative, invalidMetrics);
    const invalidMetricsResult = evaluateSoakReport(root, invalidMetricsRequirement, identity, root);
    assert.strictEqual(invalidMetricsResult.ready, false);
    assert(invalidMetricsResult.blockers.some((message) => message.includes("fdCount must be positive")));
    assert(invalidMetricsResult.blockers.some((message) => message.includes("changed servicePid")));

    const insufficientSamplesRequirement = SOAK_REQUIREMENTS[1];
    const insufficientSamples = validSoakReport(insufficientSamplesRequirement);
    insufficientSamples.samples = insufficientSamples.samples.slice(0, 2);
    writeJson(root, insufficientSamplesRequirement.relative, insufficientSamples);
    const insufficientSamplesResult = evaluateSoakReport(root, insufficientSamplesRequirement, identity, root);
    assert.strictEqual(insufficientSamplesResult.ready, false);
    assert(insufficientSamplesResult.blockers.some((message) => message.includes("at least 289 comparable")));

    const unboundSamplesRequirement = SOAK_REQUIREMENTS[0];
    const unboundSamples = validSoakReport(unboundSamplesRequirement);
    unboundSamples.cycles = 1;
    unboundSamples.runs = unboundSamples.runs.slice(0, 1);
    writeJson(root, unboundSamplesRequirement.relative, unboundSamples);
    const unboundSamplesResult = evaluateSoakReport(root, unboundSamplesRequirement, identity, root);
    assert.strictEqual(unboundSamplesResult.ready, false);
    assert(unboundSamplesResult.blockers.some((message) => message.includes("cycle must be strictly increasing within the run")));

    const missingCleanupRequirement = SOAK_REQUIREMENTS[0];
    const missingCleanup = validSoakReport(missingCleanupRequirement);
    delete missingCleanup.cleanup;
    writeJson(root, missingCleanupRequirement.relative, missingCleanup);
    const missingCleanupResult = evaluateSoakReport(root, missingCleanupRequirement, identity, root);
    assert.strictEqual(missingCleanupResult.ready, false);
    assert(missingCleanupResult.blockers.some((message) => message.includes("cleanup.complete must be true")));

    const fakeLivenessRequirement = SOAK_REQUIREMENTS[0];
    const fakeLiveness = validSoakReport(fakeLivenessRequirement);
    fakeLiveness.livenessRecoverabilityCheckDetails = [];
    writeJson(root, fakeLivenessRequirement.relative, fakeLiveness);
    const fakeLivenessResult = evaluateSoakReport(root, fakeLivenessRequirement, identity, root);
    assert.strictEqual(fakeLivenessResult.ready, false);
    assert(fakeLivenessResult.blockers.some((message) => message.includes("detail count")));

    const sparseTimelineRequirement = SOAK_REQUIREMENTS[0];
    const sparseTimeline = validSoakReport(sparseTimelineRequirement);
    sparseTimeline.samples[1].elapsedMs = 1_000_000;
    sparseTimeline.samples[1].capturedAtEpochMs = sparseTimeline.startedAtEpochMs + 1_000_000;
    writeJson(root, sparseTimelineRequirement.relative, sparseTimeline);
    const sparseTimelineResult = evaluateSoakReport(root, sparseTimelineRequirement, identity, root);
    assert.strictEqual(sparseTimelineResult.ready, false);
    assert(sparseTimelineResult.blockers.some((message) => message.includes("configured sampling gap")));

    const sawtoothGrowthRequirement = SOAK_REQUIREMENTS[0];
    const sawtoothGrowth = validSoakReport(sawtoothGrowthRequirement);
    sawtoothGrowth.samples.forEach((sample, index) => {
      sample.rssKb = 64_000 + index * 7_000 - (index % 2 === 0 ? 0 : 1_000);
    });
    sawtoothGrowth.analysis.growth = growthAnalysis(sawtoothGrowth.samples);
    const rssGrowth = sawtoothGrowth.analysis.growth.metrics.find((metric) => metric.name === "rssKb");
    rssGrowth.monotonic = false;
    rssGrowth.growthDetected = true;
    sawtoothGrowth.analysis.growth.monotonicLeak = true;
    writeJson(root, sawtoothGrowthRequirement.relative, sawtoothGrowth);
    const sawtoothGrowthResult = evaluateSoakReport(root, sawtoothGrowthRequirement, identity, root);
    assert.strictEqual(sawtoothGrowthResult.ready, false);
    assert(sawtoothGrowthResult.blockers.some((message) => message.includes("exceeded its growth threshold")));

    const wrongSlotRequirement = SOAK_REQUIREMENTS[0];
    const wrongSlot = validSoakReport(wrongSlotRequirement);
    wrongSlot.plugin.runtimeSlot = "node24_current";
    writeJson(root, wrongSlotRequirement.relative, wrongSlot);
    const wrongSlotResult = evaluateSoakReport(root, wrongSlotRequirement, identity, root);
    assert.strictEqual(wrongSlotResult.ready, false);
    assert(wrongSlotResult.blockers.some((message) => message.includes("plugin runtimeSlot")));

    const staleRequirement = DEVICE_REQUIREMENTS[0];
    const stale = validDeviceReport(staleRequirement);
    stale.identity.pluginCommit = "3".repeat(40);
    writeJson(root, staleRequirement.relative, stale);
    const staleResult = evaluateDeviceReport(root, staleRequirement, identity, root);
    assert.strictEqual(staleResult.ready, false);
    assert(staleResult.blockers.some((message) => message.includes("identity pluginCommit")));

    const inconsistentSuiteRequirement = DEVICE_REQUIREMENTS[0];
    const inconsistentSuite = validDeviceReport(inconsistentSuiteRequirement);
    bindFixtureArtifact(
      inconsistentSuite,
      "junitXml",
      contractJUnit("device").replace(
        `tests="${REQUIRED_DEVICE_SELECTORS.length}" failures="0" errors="0" skipped="0" time="1.0">`,
        `tests="${REQUIRED_DEVICE_SELECTORS.length}" failures="1" errors="0" skipped="0" time="1.0">`,
      ),
      "inconsistent-nested-suite",
    );
    writeJson(root, inconsistentSuiteRequirement.relative, inconsistentSuite);
    const inconsistentSuiteResult = evaluateDeviceReport(root, inconsistentSuiteRequirement, identity, root);
    assert.strictEqual(inconsistentSuiteResult.ready, false);
    assert(inconsistentSuiteResult.blockers.some((message) => message.includes("JUnit artifact is not a parseable")));

    const cdataPseudoRequirement = DEVICE_REQUIREMENTS[0];
    const cdataPseudo = validDeviceReport(cdataPseudoRequirement);
    bindFixtureArtifact(cdataPseudo, "junitXml", cdataPseudoJUnit(), "cdata-pseudo-testcases");
    writeJson(root, cdataPseudoRequirement.relative, cdataPseudo);
    const cdataPseudoResult = evaluateDeviceReport(root, cdataPseudoRequirement, identity, root);
    assert.strictEqual(cdataPseudoResult.ready, false);
    assert(cdataPseudoResult.blockers.some((message) => message.includes("JUnit artifact is not a parseable")));

    assertJUnitFreshnessContracts(root);
    assertSoakFreshBuildContracts(root);

    const invalidApkRequirement = DEVICE_REQUIREMENTS[0];
    const invalidApk = validDeviceReport(invalidApkRequirement);
    for (const prefix of ["hostApk", "hostBuildApk"]) {
      const file = path.join(root, invalidApk.artifacts[`${prefix}Path`]);
      fs.writeFileSync(file, "not-an-apk\n");
      const content = fs.readFileSync(file);
      invalidApk.artifacts[`${prefix}Sha256`] = crypto.createHash("sha256").update(content).digest("hex");
      invalidApk.artifacts[`${prefix}Bytes`] = content.length;
    }
    writeJson(root, invalidApkRequirement.relative, invalidApk);
    const invalidApkResult = evaluateDeviceReport(root, invalidApkRequirement, identity, root);
    assert.strictEqual(invalidApkResult.ready, false);
    assert(invalidApkResult.blockers.some((message) => message.includes("is not an APK ZIP")));

    const incompleteHostRequirement = DEVICE_REQUIREMENTS[0];
    const incompleteHost = validDeviceReport(incompleteHostRequirement);
    const incompleteHostApk = contractApk("host-incomplete", incompleteHostRequirement.runtimeProcessAbi);
    for (const prefix of ["hostApk", "hostBuildApk"]) {
      const relative = `artifacts/incomplete-host-${prefix}.apk`;
      const target = path.join(root, relative);
      fs.copyFileSync(incompleteHostApk, target);
      const content = fs.readFileSync(target);
      incompleteHost.artifacts[`${prefix}Path`] = relative;
      incompleteHost.artifacts[`${prefix}Sha256`] = crypto.createHash("sha256").update(content).digest("hex");
      incompleteHost.artifacts[`${prefix}Bytes`] = content.length;
    }
    incompleteHost.artifacts.installedHostMatchesFreshBuild = true;
    writeJson(root, incompleteHostRequirement.relative, incompleteHost);
    const incompleteHostResult = evaluateDeviceReport(root, incompleteHostRequirement, identity, root);
    assert.strictEqual(incompleteHostResult.ready, false);
    assert(incompleteHostResult.blockers.some((message) => message.includes("baseline host JNI dependency")));
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
  console.log("R6 production evidence contract fixtures: passed");
}

function validDeviceReport(requirement) {
  const manufacturer = requirement.manufacturerNot ? "google" : (requirement.manufacturer || "sony");
  const runToken = "a".repeat(64);
  const artifacts = artifactHashes(`device-${requirement.id}`, requirement.runtimeProcessAbi, "device", runToken);
  const signerSha256 = apkSignerSha256(path.join(contractRoot, artifacts.pluginApkPath));
  const preflightArtifact = writeFixtureArtifact(
    `artifacts/device-${requirement.id}-plugin-preflight.json`,
    {
      schema: SOAK_SCHEMA,
      kind: "smoke",
      status: "passed",
      runToken,
      failures: [],
      plugin: {
        packageName: RUNTIME_PLUGIN_PACKAGE,
        serviceName: RUNTIME_PLUGIN_SERVICE,
        signerSha256,
        authorizationState: "official",
      },
    },
  );
  return {
    schema: DEVICE_SCHEMA,
    evidenceTarget: requirement.id,
    generatedAt: new Date().toISOString(),
    runToken,
    status: "passed",
    identity: capturedIdentity(),
    device: {
      role: requirement.role,
      serial: `contract-${requirement.id}`,
      manufacturer,
      model: `contract-${requirement.id}`,
      apiLevel: 36,
      isEmulator: requirement.emulator,
      runtimeProcessAbi: requirement.runtimeProcessAbi,
      supportedAbis: [requirement.runtimeProcessAbi],
    },
    artifacts,
    plugin: {
      packageName: RUNTIME_PLUGIN_PACKAGE,
      serviceClass: RUNTIME_PLUGIN_SERVICE,
      signerSha256,
      authorizationState: "official",
      required: true,
      authenticated: true,
      trusted: true,
      runtimeSlot: "node24_5",
      nodeVersion: "24.5.0",
      runtimeProcessAbi: requirement.runtimeProcessAbi,
      evidence: {
        instrumentationReport: preflightArtifact,
        requiredModeSelector: R6_PLUGIN_SOAK_SELECTOR,
        trustIdentitySelector: R5_PLUGIN_PREFLIGHT_SELECTOR,
        requestedApkSignerSha256: signerSha256,
        installedApkSignerSha256: signerSha256,
      },
    },
    tests: {
      total: REQUIRED_DEVICE_SELECTORS.length,
      failures: 0,
      errors: 0,
      skipped: 0,
      selectors: REQUIRED_DEVICE_SELECTORS.map((selector) => ({ selector, status: "passed" })),
    },
    surfaces: { mainApp: "passed", packagedApk: "passed" },
    regressions: Object.fromEntries(REQUIRED_REGRESSIONS.map((id) => [id, "passed"])),
    lifecycle: {
      persistentProcessRuntime: true,
      processRuntimeInitCount: 1,
      runtimeGenerationStable: true,
      executionSequenceMonotonic: true,
      freshExecutionState: true,
      recoveryAfterProcessDeath: true,
    },
    resources: { activeBridgeResources: 0, orphanProcesses: 0, unrecoverableSlots: 0 },
  };
}

function writeFixtureArtifact(relative, value) {
  assert(contractRoot, "contract fixture root is unavailable");
  writeJson(contractRoot, relative, value);
  const file = path.join(contractRoot, relative);
  const content = fs.readFileSync(file);
  return {
    path: relative,
    sizeBytes: content.length,
    sha256: crypto.createHash("sha256").update(content).digest("hex"),
    modifiedAt: new Date(fs.statSync(file).mtimeMs).toISOString(),
  };
}

function validSoakReport(requirement) {
  const durationMs = requirement.minDurationHours * 3_600_000;
  const startedAtEpochMs = Date.now() - durationMs;
  const sampleCount = Math.floor(durationMs / 300_000) + 1;
  const artifacts = artifactHashes(`soak-${requirement.kind}`, "arm64-v8a", "soak");
  const samples = Array.from({ length: sampleCount }, (_, index) => {
    const elapsedMs = Math.round(durationMs * index / (sampleCount - 1));
    return soakSample(index + 1, startedAtEpochMs + elapsedMs, elapsedMs);
  });
  return {
    schema: SOAK_SCHEMA,
    generatedAt: new Date().toISOString(),
    kind: requirement.kind,
    status: "passed",
    identity: capturedIdentity(),
    artifacts,
    durationHours: requirement.minDurationHours,
    durationMs,
    startedAtEpochMs,
    endedAtEpochMs: startedAtEpochMs + durationMs,
    runToken: "B".repeat(64),
    configuration: {
      sampleIntervalMs: 300_000,
      minimumRequiredSamples: sampleCount,
      livenessRecoverabilityEveryCycles: sampleCount,
    },
    plugin: {
      packageName: "io.github.supermonster003.autojs6.plugin.nodejs",
      signerSha256: apkSignerSha256(path.join(contractRoot, artifacts.pluginApkPath)),
      authorizationState: "official",
      runtimeSlot: "node24_5",
      nodeVersion: "24.5.0",
      runtimeProcessAbi: "arm64-v8a",
    },
    cycles: sampleCount,
    runs: Array.from({ length: sampleCount }, (_, index) => soakRun(index + 1)),
    samples,
    failures: [],
    cleanup: {
      complete: true,
      propertiesRestored: true,
      pluginHostInvalidated: true,
      workDirectoryDeleted: true,
    },
    analysis: {
      monotonicLeak: false,
      orphanProcess: false,
      unrecoverableRuntime: false,
      unrecoverableSlot: false,
      processRuntimeReused: true,
      freshExecutionState: true,
      livenessRecoverabilityChecksPassed: true,
      livenessRecoverabilityChecks: 1,
      livenessRecoverabilityMode: "non_destructive_binder_session_health_and_recoverability",
      growth: growthAnalysis(samples),
    },
    livenessRecoverabilityCheckDetails: [{
      cycle: sampleCount,
      checkedAtEpochMs: startedAtEpochMs + durationMs,
      mode: "non_destructive_binder_session_health_and_recoverability",
      passed: true,
      servicePid: 100,
      hostSessionGeneration: 1,
      runtimeReady: true,
      persistentProcessRuntime: true,
    }],
    device: {
      serial: "contract-physical",
      manufacturer: "sony",
      model: "contract-device",
      apiLevel: 36,
      supportedAbis: ["arm64-v8a"],
      isEmulator: false,
    },
  };
}

function capturedIdentity() {
  return {
    hostCommit: identity.host.commit,
    pluginCommit: identity.plugin.commit,
    versionName: identity.version.name,
    versionBuild: identity.version.build,
    lifecycleModel: LIFECYCLE_MODEL,
    releaseFlavor: "nodePlugin",
    backend: "plugin",
    runtimeSlot: "node24_5",
    nodeVersion: "24.5.0",
    pluginRequired: true,
    hostTreeClean: true,
    pluginTreeClean: true,
  };
}

function artifactHashes(prefix, runtimeProcessAbi, junitKind, runToken = "") {
  assert(contractRoot, "contract fixture root is unavailable");
  const bindings = [
    ["hostApkSha256", "hostApkPath", "hostApkBytes"],
    ["androidTestApkSha256", "androidTestApkPath", "androidTestApkBytes"],
    ["pluginApkSha256", "pluginApkPath", "pluginApkBytes"],
    ["junitXmlSha256", "junitXmlPath", "junitXmlBytes"],
    ["hostBuildApkSha256", "hostBuildApkPath", "hostBuildApkBytes"],
    ["androidTestBuildApkSha256", "androidTestBuildApkPath", "androidTestBuildApkBytes"],
    ["requestedPluginApkSha256", "requestedPluginApkPath", "requestedPluginApkBytes"],
  ];
  if (junitKind === "device") {
    bindings.push([
      "packagedTemplateApkSha256",
      "packagedTemplateApkPath",
      "packagedTemplateApkBytes",
    ]);
  }
  const result = {};
  bindings.forEach(([hashKey, pathKey, bytesKey]) => {
    const extension = hashKey === "junitXmlSha256" ? "xml" : "bin";
    const apkRole = hashKey.includes("Plugin") || hashKey.includes("plugin")
      ? "plugin"
      : hashKey.toLowerCase().includes("androidtest")
        ? "test"
        : hashKey.toLowerCase().includes("packagedtemplate") ? "template" : "host";
    const relative = `artifacts/${prefix}-${hashKey}.${extension === "xml" ? "xml" : "apk"}`;
    const file = path.join(contractRoot, relative);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    if (!fs.existsSync(file)) {
      if (hashKey === "junitXmlSha256") {
        fs.writeFileSync(file, contractJUnit(junitKind));
      } else {
        fs.copyFileSync(contractApk(apkRole, runtimeProcessAbi), file);
      }
    }
    const content = fs.readFileSync(file);
    result[hashKey] = crypto.createHash("sha256").update(content).digest("hex");
    result[pathKey] = relative;
    result[bytesKey] = content.length;
  });
  result.installedHostMatchesFreshBuild = true;
  result.installedAndroidTestMatchesFreshBuild = true;
  result.installedPluginMatchesRequested = true;
  if (junitKind === "device") {
    const devicePath = packagedTemplatePathForRunToken(runToken);
    result.packagedTemplateSourceTask = ":app:packageInrtDebug";
    result.packagedTemplateDevicePath = devicePath;
    result.packagedTemplateDeviceSha256BeforeTest = result.packagedTemplateApkSha256;
    result.packagedTemplateDeviceSha256AfterTest = result.packagedTemplateApkSha256;
    result.packagedTemplateDeviceMatchesFreshBuild = true;
    result.packagedTemplateDeviceRemovedAfterTest = true;
    result.packagedTemplateTargetAbi = runtimeProcessAbi;
    result.packagedTemplateOverrideArgument = "autojs.embeddedNode.packagedSmokeTemplateOverride=true";
    result.packagedTemplateExactPathArgument =
      `autojs.embeddedNode.packagedSmokeTemplateExactPath=${devicePath}`;
    result.packagedTemplateTargetAbiArgument =
      `autojs.embeddedNode.packagedSmokeTargetAbi=${runtimeProcessAbi}`;
  }
  assert.strictEqual(result.hostApkSha256, result.hostBuildApkSha256);
  assert.strictEqual(result.androidTestApkSha256, result.androidTestBuildApkSha256);
  assert.strictEqual(result.pluginApkSha256, result.requestedPluginApkSha256);
  return result;
}

function contractApk(role, runtimeProcessAbi) {
  const cacheKey = role === "test" ? role : `${role}-${runtimeProcessAbi}`;
  if (contractApkCache.has(cacheKey)) return contractApkCache.get(cacheKey);
  assert(contractRoot && contractAndroidTools && contractKeystore, "contract APK tools are unavailable");
  const directory = path.join(contractRoot, "generated-apks", cacheKey);
  fs.mkdirSync(directory, { recursive: true });
  const packageName = role === "plugin"
    ? "io.github.supermonster003.autojs6.plugin.nodejs"
    : role === "test"
      ? "org.autojs.autojs6.test"
      : role === "template" ? "org.autojs.autojs6.inrt" : "org.autojs.autojs6";
  const manifest = path.join(directory, "AndroidManifest.xml");
  fs.writeFileSync(
    manifest,
    `<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="${packageName}">` +
      `<uses-sdk android:minSdkVersion="24" android:targetSdkVersion="36"/>` +
      `<application android:hasCode="false"/></manifest>\n`,
  );
  const unsigned = path.join(directory, "unsigned.apk");
  runContractTool(contractAndroidTools.aapt, [
    "package", "-f", "-M", manifest, "-I", contractAndroidTools.androidJar, "-F", unsigned,
  ], directory);
  const nativeLibraries = role === "plugin"
    ? ["libnode.so", "libautojs6-node.so", "libc++_shared.so"]
    : role === "host"
      ? ["libhost-contract.so", "libjackpal-termexec2.so", "libc++_shared.so"]
      : role === "host-incomplete" ? ["libhost-contract.so"] : [];
  const relativeLibraries = nativeLibraries.map((name) => `lib/${runtimeProcessAbi}/${name}`);
  relativeLibraries.forEach((relative) => {
    const file = path.join(directory, ...relative.split("/"));
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, `contract-${relative}\n`);
  });
  if (relativeLibraries.length > 0) {
    runContractTool(contractAndroidTools.aapt, ["add", unsigned, ...relativeLibraries], directory);
  }
  const signed = path.join(directory, "signed.apk");
  runContractTool(contractAndroidTools.apksigner, [
    "sign",
    "--ks", contractKeystore,
    "--ks-key-alias", "contract",
    "--ks-pass", "pass:changeit",
    "--key-pass", "pass:changeit",
    "--out", signed,
    unsigned,
  ], directory);
  contractApkCache.set(cacheKey, signed);
  return signed;
}

function prepareContractApkTools() {
  const repoRoot = path.resolve(__dirname, "../../..");
  const sdkRoot = process.env.ANDROID_SDK_ROOT || process.env.ANDROID_HOME || readSdkDir(repoRoot);
  assert(sdkRoot && fs.existsSync(sdkRoot), "Android SDK is required for the R6 APK contract fixtures");
  const buildToolsRoot = path.join(sdkRoot, "build-tools");
  const buildToolsDir = fs.readdirSync(buildToolsRoot)
    .map((name) => path.join(buildToolsRoot, name))
    .filter((entry) => fs.statSync(entry).isDirectory())
    .sort((left, right) => path.basename(right).localeCompare(path.basename(left), undefined, { numeric: true }))[0];
  assert(buildToolsDir, "Android SDK build-tools are required for the R6 APK contract fixtures");
  contractAndroidTools = {
    aapt: path.join(buildToolsDir, process.platform === "win32" ? "aapt.exe" : "aapt"),
    apksigner: path.join(buildToolsDir, process.platform === "win32" ? "apksigner.bat" : "apksigner"),
    androidJar: path.join(sdkRoot, "platforms", "android-36", "android.jar"),
  };
  Object.values(contractAndroidTools).forEach((file) => assert(fs.existsSync(file), `Missing Android tool: ${file}`));
  contractKeystore = path.join(contractRoot, "contract-signing.jks");
  runContractTool("keytool", [
    "-genkeypair", "-noprompt",
    "-keystore", contractKeystore,
    "-storepass", "changeit",
    "-keypass", "changeit",
    "-alias", "contract",
    "-dname", "CN=AutoJs6 R6 Contract",
    "-keyalg", "RSA",
    "-keysize", "2048",
    "-validity", "3650",
  ], contractRoot);
}

function runContractTool(command, args, cwd) {
  const invocation = process.platform === "win32" && command.toLowerCase().endsWith(".bat")
    ? { command: process.env.ComSpec || "cmd.exe", args: ["/d", "/c", "call", command, ...args] }
    : { command, args };
  const result = childProcess.spawnSync(invocation.command, invocation.args, {
    cwd,
    encoding: "utf8",
    windowsHide: true,
    timeout: 120_000,
  });
  assert.strictEqual(
    result.status,
    0,
    `${command} failed: ${String(result.stderr || result.stdout || result.error || "unknown error")}`,
  );
  return result;
}

function apkSignerSha256(file) {
  assert(contractAndroidTools, "contract APK signer tool is unavailable");
  const result = runContractTool(
    contractAndroidTools.apksigner,
    ["verify", "--verbose", "--print-certs", file],
    contractRoot,
  );
  return parseApkSignerOutput(result.stdout, file).signerSha256;
}

function assertApkSignerOutputContracts() {
  const digest = "a".repeat(64);
  const verified = [
    "Verified using v2 scheme (APK Signature Scheme v2): true",
    "Number of signers: 1",
  ];
  assert.strictEqual(
    parseApkSignerOutput([...verified, `Signer #1 certificate SHA-256 digest: ${digest}`].join("\n")).signerSha256,
    digest,
  );
  assert.strictEqual(
    parseApkSignerOutput([...verified, `V2 Signer: certificate SHA-256 digest: ${digest}`].join("\n")).signerSha256,
    digest,
  );
  assert.strictEqual(
    parseApkSignerOutput([...verified, `V3.1 Signer: certificate SHA-256 digest: ${digest}`].join("\n")).signerSha256,
    digest,
  );
  assert.throws(
    () => parseApkSignerOutput([...verified, `V2 Signer: public key SHA-256 digest: ${digest}`].join("\n")),
    /exactly one SHA-256 signer/,
  );
  assert.throws(
    () => parseApkSignerOutput([
      verified[0],
      "Number of signers: 2",
      `V2 Signer: certificate SHA-256 digest: ${digest}`,
    ].join("\n")),
    /exactly one SHA-256 signer/,
  );
  assert.throws(
    () => parseApkSignerOutput([
      ...verified,
      `V2 Signer: certificate SHA-256 digest: ${digest}`,
      `V3 Signer: certificate SHA-256 digest: ${digest}`,
    ].join("\n")),
    /exactly one SHA-256 signer/,
  );
  assert.throws(
    () => parseApkSignerOutput([
      "Verified using v2 scheme (APK Signature Scheme v2): false",
      verified[1],
      `V2 Signer: certificate SHA-256 digest: ${digest}`,
    ].join("\n")),
    /did not verify with APK Signature Scheme v2/,
  );
}

function assertDeviceRunnerGradleContracts() {
  const devicePaths = new Set();
  DEVICE_REQUIREMENTS.forEach((requirement, index) => {
    const runToken = (index + 1).toString(16).repeat(64);
    const devicePath = packagedTemplatePathForRunToken(runToken);
    const evidenceArgs = buildEvidenceGradleArgs(
      "runtime-plugin",
      requirement.manufacturer === "sony" ? "sony" : "full",
      runToken,
      requirement.runtimeProcessAbi,
    );
    assert(evidenceArgs.includes(
      "-Pandroid.testInstrumentationRunnerArguments.autojs.embeddedNode.packagedSmokeTemplateOverride=true",
    ));
    assert(evidenceArgs.includes(
      `-Pandroid.testInstrumentationRunnerArguments.autojs.embeddedNode.packagedSmokeTemplateExactPath=${devicePath}`,
    ));
    assert(evidenceArgs.includes(
      `-Pandroid.testInstrumentationRunnerArguments.autojs.embeddedNode.packagedSmokeTargetAbi=${requirement.runtimeProcessAbi}`,
    ));
    devicePaths.add(devicePath);
  });
  assert.strictEqual(devicePaths.size, DEVICE_REQUIREMENTS.length);
  const templateArgs = buildPackagedTemplateGradleArgs();
  assert(templateArgs.includes(":app:packageInrtDebug"));
  assert(templateArgs.includes("-Pautojs.nodejs.embedded.script.enabled=true"));
  assert(!templateArgs.some((value) => value.startsWith("-Pautojs.nodejs.releaseFlavor=")));
  assert(REQUIRED_DEVICE_ARTIFACT_HASHES.includes("packagedTemplateApkSha256"));
  assert(!REQUIRED_ARTIFACT_HASHES.includes("packagedTemplateApkSha256"));
}

function assertPackagedTemplateAbiSelectionContract(root) {
  const outputRoot = path.join(root, "packaged-template-selection");
  fs.mkdirSync(outputRoot, { recursive: true });
  fs.writeFileSync(path.join(outputRoot, "inrt-v6.8.0-alpha7-universal.apk"), "universal");
  DEVICE_REQUIREMENTS.forEach((requirement) => {
    fs.writeFileSync(
      path.join(outputRoot, `inrt-v6.8.0-alpha7-${requirement.runtimeProcessAbi}.apk`),
      requirement.runtimeProcessAbi,
    );
  });
  DEVICE_REQUIREMENTS.forEach((requirement) => {
    assert.strictEqual(
      path.basename(selectAbiApk(outputRoot, requirement.runtimeProcessAbi, "packaged smoke template")),
      `inrt-v6.8.0-alpha7-${requirement.runtimeProcessAbi}.apk`,
    );
  });
}

function assertPackagedTemplateSourceContract() {
  const source = fs.readFileSync(
    path.resolve(__dirname, "../../../app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"),
    "utf8",
  );
  const selectionStart = source.indexOf("private fun packagedSmokeTemplateSource(");
  const selectionEnd = source.indexOf("private fun isPackagedSmokeAssetsTemplateAvailable", selectionStart);
  assert(selectionStart >= 0 && selectionEnd > selectionStart);
  const selection = source.slice(selectionStart, selectionEnd);
  assert(selection.indexOf("packagedSmokeTemplateExactPath()") < selection.indexOf("packagedSmokeTemplateOverride()"));
  assert(selection.includes("check(isShellPackagedSmokeTemplateAvailable(exactPath))"));
  assert(source.includes("autojs.embeddedNode.packagedSmokeTemplateExactPath"));
  assert(source.includes("shell(\"stat -c %s $path\")"));
  assert(!source.includes("shell(\"if [ -s $path ]"));
  assert(source.includes("update(\"runtime.targetAbi\", packagedSmokeTargetAbi())"));
  assert(!source.includes("\"abis\": [\"arm64-v8a\"]"));
  assert(!source.includes("assertEquals(listOf(\"arm64-v8a\"), bundledConfig.abis)"));
  assert(source.includes("\"lib/$targetAbi/$libName\" in entries"));
}

function readSdkDir(repoRoot) {
  const file = path.join(repoRoot, "local.properties");
  if (!fs.existsSync(file)) return "";
  const line = fs.readFileSync(file, "utf8").split(/\r?\n/).find((entry) => entry.startsWith("sdk.dir="));
  return line ? line.slice("sdk.dir=".length).replace(/\\:/g, ":").replace(/\\\\/g, "\\") : "";
}

function contractJUnit(kind) {
  const selectors = kind === "device"
    ? REQUIRED_DEVICE_SELECTORS
    : ["org.autojs.autojs.engine.NodeR6PersistentPluginSoakInstrumentationTest#persistentRequiredPluginSoakProducesR6Evidence"];
  const cases = selectors.map((selector) => {
    const split = selector.lastIndexOf("#");
    const classname = selector.slice(0, split);
    const name = selector.slice(split + 1);
    return `    <testcase classname="${classname}" name="${name}" time="0.1"></testcase>`;
  }).join("\n");
  return `<?xml version="1.0" encoding="UTF-8"?>\n` +
    `<testsuites tests="${selectors.length}" failures="0" errors="0" skipped="0">\n` +
    `  <testsuite name="contract" tests="${selectors.length}" failures="0" errors="0" skipped="0" time="1.0">\n` +
    `${cases}\n  </testsuite>\n</testsuites>\n`;
}

function cdataPseudoJUnit() {
  const pseudoCases = REQUIRED_DEVICE_SELECTORS.map((selector) => {
    const split = selector.lastIndexOf("#");
    return `<testcase classname="${selector.slice(0, split)}" name="${selector.slice(split + 1)}"/>`;
  }).join("\n");
  return `<?xml version="1.0" encoding="UTF-8"?>\n` +
    `<testsuites tests="${REQUIRED_DEVICE_SELECTORS.length}" failures="0" errors="0" skipped="0">\n` +
    `  <testsuite name="forged" tests="${REQUIRED_DEVICE_SELECTORS.length}" failures="0" errors="0" skipped="0">\n` +
    `    <system-out><![CDATA[${pseudoCases}]]></system-out>\n` +
    `  </testsuite>\n</testsuites>\n`;
}

function bindFixtureArtifact(report, prefix, content, suffix) {
  assert(contractRoot, "contract fixture root is unavailable");
  const relative = `artifacts/negative-${suffix}.xml`;
  const file = path.join(contractRoot, relative);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, content);
  const bytes = fs.readFileSync(file);
  report.artifacts[`${prefix}Path`] = relative;
  report.artifacts[`${prefix}Sha256`] = crypto.createHash("sha256").update(bytes).digest("hex");
  report.artifacts[`${prefix}Bytes`] = bytes.length;
}

function assertJUnitFreshnessContracts(root) {
  const deviceRoot = path.join(root, "freshness-device");
  fs.mkdirSync(deviceRoot, { recursive: true });
  const deviceFile = path.join(deviceRoot, "TEST-device.xml");
  fs.writeFileSync(deviceFile, contractJUnit("device"));
  const deviceBefore = snapshotDeviceJUnitFiles(deviceRoot);
  const touchedAt = new Date(Date.now() + 20_000);
  fs.utimesSync(deviceFile, touchedAt, touchedAt);
  assert.throws(
    () => selectFreshJUnit(deviceRoot, deviceBefore, Date.now() - 1_000, REQUIRED_DEVICE_SELECTORS),
    /No fresh JUnit XML/,
    "mtime-only device JUnit changes must remain stale",
  );
  fs.appendFileSync(deviceFile, "\n");
  const freshDevice = selectFreshJUnit(
    deviceRoot,
    deviceBefore,
    Date.now() - 1_000,
    REQUIRED_DEVICE_SELECTORS,
  );
  assert.strictEqual(path.resolve(freshDevice.file), path.resolve(deviceFile));

  const ambiguousRoot = path.join(root, "freshness-device-ambiguous");
  fs.mkdirSync(ambiguousRoot, { recursive: true });
  fs.writeFileSync(path.join(ambiguousRoot, "TEST-first.xml"), contractJUnit("device"));
  fs.writeFileSync(path.join(ambiguousRoot, "TEST-second.xml"), contractJUnit("device") + "\n");
  assert.throws(
    () => selectFreshJUnit(ambiguousRoot, new Map(), Date.now() - 10_000, REQUIRED_DEVICE_SELECTORS),
    /Multiple fresh R6 JUnit XML files are ambiguous/,
    "multiple exact device JUnit candidates must fail closed",
  );

  const soakRoot = path.join(root, "freshness-soak");
  fs.mkdirSync(soakRoot, { recursive: true });
  const soakFile = path.join(soakRoot, "TEST-soak.xml");
  fs.writeFileSync(soakFile, contractJUnit("soak"));
  const soakBefore = snapshotSoakJUnitFiles(soakRoot);
  fs.utimesSync(soakFile, touchedAt, touchedAt);
  assert.throws(
    () => selectFreshSoakJUnit(soakRoot, soakBefore, Date.now() - 1_000),
    /Expected exactly one fresh R6 soak JUnit XML, found 0/,
    "mtime-only soak JUnit changes must remain stale",
  );
}

function assertSoakFreshBuildContracts(root) {
  const args = buildSoakGradleArgs({
    kind: "quick",
    durationMs: 8 * 3_600_000,
    runToken: "c".repeat(64),
    smoke: false,
    pluginRoot: root,
    deviceProfile: "sony",
    sampleIntervalMs: 300_000,
    cycleDelayMs: 30_000,
    livenessRecoverabilityEveryCycles: 20,
  });
  assert(args.includes("--rerun-tasks"), "production soak connected builds must force task reruns");
  const artifact = path.join(root, "stale-connected-build.apk");
  fs.writeFileSync(artifact, "stale");
  const staleAt = new Date(Date.now() - 60_000);
  fs.utimesSync(artifact, staleAt, staleAt);
  assert.throws(
    () => assertFreshArtifact(artifact, Date.now(), "Contract APK"),
    /is stale for this connected build/,
    "an APK predating the connected build must not be called fresh",
  );
}

function soakRun(sequence) {
  return {
    servicePid: 100,
    pluginSessionGeneration: 1,
    processRuntimeGeneration: 1,
    processRuntimeInitCount: 1,
    executionSequence: sequence,
    cycle: sequence,
    runtimeReused: sequence > 1,
    nodeHeapUsedKb: 1000 + sequence,
    libuvHandleCount: 2,
    activeBridgeResourceCount: 0,
    freshExecutionState: true,
  };
}

function soakSample(sequence, capturedAtEpochMs, elapsedMs) {
  return {
    cycle: sequence,
    servicePid: 100,
    pluginSessionGeneration: 1,
    processRuntimeGeneration: 1,
    processRuntimeInitCount: 1,
    executionSequence: sequence,
    rssKb: 64_000 + sequence % 2,
    javaHeapKb: 8_000 + sequence % 2,
    nativeHeapKb: 16_000 + sequence % 2,
    threadCount: 20,
    fdCount: 80,
    fdCountSource: "plugin_uid_proc_self_fd",
    nodeHeapUsedKb: 7_000 + sequence % 2,
    libuvHandleCount: 2,
    activeBridgeResourceCount: 0,
    freshExecutionState: true,
    capturedAtEpochMs,
    elapsedMs,
  };
}

function growthAnalysis(samples) {
  const thresholds = {
    rssKb: 262_144,
    javaHeapKb: 131_072,
    nativeHeapKb: 131_072,
    threadCount: 24,
    fdCount: 64,
    nodeHeapUsedKb: 131_072,
    libuvHandleCount: 8,
    activeBridgeResourceCount: 0,
  };
  return {
    method: "thresholded_terminal_or_monotonic_remote_plugin_growth",
    minimumSamples: 4,
    monotonicLeak: false,
    metrics: Object.entries(thresholds).map(([name, threshold]) => {
      const values = samples.map((sample) => sample[name]);
      return {
        name,
        sampleCount: values.length,
        baseline: values[0],
        final: values[values.length - 1],
        delta: values[values.length - 1] - values[0],
        threshold,
        monotonic: false,
        growthDetected: false,
        values,
      };
    }),
  };
}

function writeJson(root, relative, value) {
  const file = path.join(root, relative);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(value, null, 2) + "\n");
}

main();
