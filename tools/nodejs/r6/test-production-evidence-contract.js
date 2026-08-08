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
  buildApkBuilderPluginGradleArgs,
  buildEvidenceGradleArgs,
  buildIsolatedGradleArgs,
  buildPackagedTemplateGradleArgs,
  buildPluginGradleArgs,
  gradleDaemonRegistryBaseForRun,
  normalizeAndroidComponentName,
  packagedTemplatePathForRunToken,
  parseApkSignerOutput,
  parseEmulatorAvdName,
  parsePageSizeBytes,
  selectAbiApk,
  selectFreshJUnit,
  snapshotJUnitFiles: snapshotDeviceJUnitFiles,
} = require("./run-device-evidence");
const {
  assertFreshArtifact,
  buildGradleArgs: buildSoakGradleArgs,
  buildIsolatedGradleArgs: buildSoakIsolatedGradleArgs,
  buildPluginGradleArgs: buildSoakPluginGradleArgs,
  gradleDaemonRegistryBaseForRun: soakGradleDaemonRegistryBaseForRun,
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
let contractAlternateKeystore = null;
const contractApkCache = new Map();
const RUNTIME_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.nodejs";
const RUNTIME_PLUGIN_SERVICE = `${RUNTIME_PLUGIN_PACKAGE}.NodeJsRuntimePluginService`;
const APK_BUILDER_PLUGIN_PACKAGE = "org.autojs.plugin.apkbuilder.template";
const APK_BUILDER_PLUGIN_ACTION = "org.autojs.plugin.APK_BUILDER";
const APK_BUILDER_PLUGIN_SERVICE =
  "org.autojs.plugin.apkbuilder.template.impl.ApkBuilderTemplatePluginService";
const APK_BUILDER_PLUGIN_COMPONENT =
  `${APK_BUILDER_PLUGIN_PACKAGE}/${APK_BUILDER_PLUGIN_SERVICE}`;
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
    assertAndroidComponentNameContracts();
    assertEmulatorDeviceContractParsers();
    assertDeviceRunnerGradleContracts();
    assertPackagedTemplateAbiSelectionContract(root);
    assertPackagedTemplateSourceContract();
    assertPackagedHostVerifierSourceContract();
    assertReleaseInstrumentationScopeContract();
    assertNodeErrorCatalogSourceContract();
    assertPackagedInteractiveShellContract();
    assertRuntimeKitNativeBaselineContract();
    for (const requirement of DEVICE_REQUIREMENTS) {
      writeJson(root, requirement.relative, validDeviceReport(requirement));
      const result = evaluateDeviceReport(root, requirement, identity, root);
      assert.strictEqual(result.ready, true, `${requirement.id}: ${result.blockers.join("; ")}`);
    }

    const emulatorRequirement = DEVICE_REQUIREMENTS.find((entry) => entry.id === "emulator-x86_64");
    assert(emulatorRequirement, "emulator-x86_64 requirement is missing");
    const emulatorMutations = [
      ["wrong AVD", (report) => { report.device.avdName = "AVD_API_36.1"; }, "emulator AVD name"],
      ["missing AVD", (report) => { delete report.device.avdName; }, "emulator AVD name"],
      ["wrong API", (report) => { report.device.apiLevel = 36; }, "device API level must be exactly"],
      ["4 KB page size", (report) => { report.device.pageSizeBytes = 4096; }, "device page size must be exactly"],
      ["missing page size", (report) => { delete report.device.pageSizeBytes; }, "device page size must be exactly"],
    ];
    emulatorMutations.forEach(([label, mutate, blockerText]) => {
      const report = validDeviceReport(emulatorRequirement);
      mutate(report);
      writeJson(root, emulatorRequirement.relative, report);
      const result = evaluateDeviceReport(root, emulatorRequirement, identity, root);
      assert.strictEqual(result.ready, false, `${label} must fail closed`);
      assert(
        result.blockers.some((message) => message.includes(blockerText)),
        `${label}: ${result.blockers.join("; ")}`,
      );
    });

    const skippedRequirement = DEVICE_REQUIREMENTS[0];
    const skipped = validDeviceReport(skippedRequirement);
    skipped.tests.skipped = 1;
    writeJson(root, skippedRequirement.relative, skipped);
    const skippedResult = evaluateDeviceReport(root, skippedRequirement, identity, root);
    assert.strictEqual(skippedResult.ready, false);
    assert(skippedResult.blockers.some((message) => message.includes("skipped tests must be zero")));

    const missingHostProcessAbiRequirement = DEVICE_REQUIREMENTS[0];
    const missingHostProcessAbi = validDeviceReport(missingHostProcessAbiRequirement);
    delete missingHostProcessAbi.device.hostProcessAbi;
    writeJson(root, missingHostProcessAbiRequirement.relative, missingHostProcessAbi);
    const missingHostProcessAbiResult = evaluateDeviceReport(
      root,
      missingHostProcessAbiRequirement,
      identity,
      root,
    );
    assert.strictEqual(missingHostProcessAbiResult.ready, false);
    assert(missingHostProcessAbiResult.blockers.some((message) => message.includes("host process ABI is missing")));

    for (const requirement of SOAK_REQUIREMENTS) {
      writeJson(root, requirement.relative, validSoakReport(requirement));
      const result = evaluateSoakReport(root, requirement, identity, root);
      assert.strictEqual(result.ready, true, `${requirement.id}: ${result.blockers.join("; ")}`);
    }
    assert(REQUIRED_SOAK_METRICS.includes("nativeHeapCommittedKb"));
    assert(REQUIRED_SOAK_METRICS.includes("nativePssKb"));

    const zeroAllocatedNativeHeapRequirement = SOAK_REQUIREMENTS[0];
    const zeroAllocatedNativeHeap = validSoakReport(zeroAllocatedNativeHeapRequirement);
    zeroAllocatedNativeHeap.samples.forEach((sample) => {
      sample.nativeHeapKb = 0;
    });
    zeroAllocatedNativeHeap.analysis.growth = growthAnalysis(zeroAllocatedNativeHeap.samples);
    writeJson(root, zeroAllocatedNativeHeapRequirement.relative, zeroAllocatedNativeHeap);
    const zeroAllocatedNativeHeapResult = evaluateSoakReport(
      root,
      zeroAllocatedNativeHeapRequirement,
      identity,
      root,
    );
    assert.strictEqual(
      zeroAllocatedNativeHeapResult.ready,
      true,
      `zero allocated native heap must remain valid: ${zeroAllocatedNativeHeapResult.blockers.join("; ")}`,
    );

    const missingNativeMemoryProofRequirement = SOAK_REQUIREMENTS[0];
    const missingNativeMemoryProof = validSoakReport(missingNativeMemoryProofRequirement);
    missingNativeMemoryProof.samples[1].nativeHeapCommittedKb = 0;
    missingNativeMemoryProof.samples[1].nativePssKb = 0;
    writeJson(root, missingNativeMemoryProofRequirement.relative, missingNativeMemoryProof);
    const missingNativeMemoryProofResult = evaluateSoakReport(
      root,
      missingNativeMemoryProofRequirement,
      identity,
      root,
    );
    assert.strictEqual(missingNativeMemoryProofResult.ready, false);
    assert(
      missingNativeMemoryProofResult.blockers.some((message) =>
        message.includes("nativeHeapCommittedKb must be positive")),
    );
    assert(
      missingNativeMemoryProofResult.blockers.some((message) => message.includes("nativePssKb must be positive")),
    );

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

    const malformedSoakNumberTypes = validSoakReport(SOAK_REQUIREMENTS[0]);
    malformedSoakNumberTypes.durationHours = String(malformedSoakNumberTypes.durationHours);
    malformedSoakNumberTypes.durationMs = [malformedSoakNumberTypes.durationMs];
    malformedSoakNumberTypes.startedAtEpochMs = String(malformedSoakNumberTypes.startedAtEpochMs);
    malformedSoakNumberTypes.endedAtEpochMs = [malformedSoakNumberTypes.endedAtEpochMs];
    writeJson(root, SOAK_REQUIREMENTS[0].relative, malformedSoakNumberTypes);
    const malformedSoakNumberTypesResult =
      evaluateSoakReport(root, SOAK_REQUIREMENTS[0], identity, root);
    assert.strictEqual(malformedSoakNumberTypesResult.ready, false);
    assert(malformedSoakNumberTypesResult.blockers.some((message) =>
      message.includes("durationHours must be a finite number")));
    assert(malformedSoakNumberTypesResult.blockers.some((message) =>
      message.includes("durationMs must be a finite number")));
    assert(malformedSoakNumberTypesResult.blockers.some((message) =>
      message.includes("wall-clock interval")));

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

    const missingApkBuilderRequirement = DEVICE_REQUIREMENTS[0];
    const missingApkBuilder = validDeviceReport(missingApkBuilderRequirement);
    delete missingApkBuilder.artifacts.apkBuilderPluginApkPath;
    writeJson(root, missingApkBuilderRequirement.relative, missingApkBuilder);
    const missingApkBuilderResult =
      evaluateDeviceReport(root, missingApkBuilderRequirement, identity, root);
    assert.strictEqual(missingApkBuilderResult.ready, false);
    assert(missingApkBuilderResult.blockers.some((message) =>
      message.includes("artifact apkBuilderPluginApkPath is missing")));

    const fakeApkBuilderHashRequirement = DEVICE_REQUIREMENTS[0];
    const fakeApkBuilderHash = validDeviceReport(fakeApkBuilderHashRequirement);
    fakeApkBuilderHash.artifacts.apkBuilderPluginBuildApkSha256 = "f".repeat(64);
    writeJson(root, fakeApkBuilderHashRequirement.relative, fakeApkBuilderHash);
    const fakeApkBuilderHashResult =
      evaluateDeviceReport(root, fakeApkBuilderHashRequirement, identity, root);
    assert.strictEqual(fakeApkBuilderHashResult.ready, false);
    assert(fakeApkBuilderHashResult.blockers.some((message) =>
      message.includes("apkBuilderPluginBuildApkSha256 does not match")));
    assert(fakeApkBuilderHashResult.blockers.some((message) =>
      message.includes("installed APK Builder plugin APK must match")));

    const changedApkBuilderDuringTestRequirement = DEVICE_REQUIREMENTS[0];
    const changedApkBuilderDuringTest =
      validDeviceReport(changedApkBuilderDuringTestRequirement);
    bindFixtureApk(
      changedApkBuilderDuringTest,
      "apkBuilderPluginApkBeforeTest",
      contractApk("apk-builder-plugin-alt", ""),
      "apk-builder-changed-during-test",
    );
    writeJson(
      root,
      changedApkBuilderDuringTestRequirement.relative,
      changedApkBuilderDuringTest,
    );
    const changedApkBuilderDuringTestResult = evaluateDeviceReport(
      root,
      changedApkBuilderDuringTestRequirement,
      identity,
      root,
    );
    assert.strictEqual(changedApkBuilderDuringTestResult.ready, false);
    assert(changedApkBuilderDuringTestResult.blockers.some((message) =>
      message.includes("must remain identical to the fresh build before and after testing")));

    const mismatchedApkBuilderBuildRequirement = DEVICE_REQUIREMENTS[0];
    const mismatchedApkBuilderBuild = validDeviceReport(mismatchedApkBuilderBuildRequirement);
    bindFixtureApk(
      mismatchedApkBuilderBuild,
      "apkBuilderPluginBuildApk",
      contractApk("apk-builder-plugin-alt", ""),
      "apk-builder-alternate-current-source-build",
    );
    writeJson(root, mismatchedApkBuilderBuildRequirement.relative, mismatchedApkBuilderBuild);
    const mismatchedApkBuilderBuildResult =
      evaluateDeviceReport(root, mismatchedApkBuilderBuildRequirement, identity, root);
    assert.strictEqual(mismatchedApkBuilderBuildResult.ready, false);
    assert(mismatchedApkBuilderBuildResult.blockers.some((message) =>
      message.includes("installed APK Builder plugin APK must match")));

    const wrongApkBuilderPackageRequirement = DEVICE_REQUIREMENTS[0];
    const wrongApkBuilderPackage = validDeviceReport(wrongApkBuilderPackageRequirement);
    bindFixtureApk(
      wrongApkBuilderPackage,
      "apkBuilderPluginApk",
      contractApk("test", ""),
      "apk-builder-wrong-installed-package",
    );
    bindFixtureApk(
      wrongApkBuilderPackage,
      "apkBuilderPluginBuildApk",
      contractApk("test", ""),
      "apk-builder-wrong-build-package",
    );
    writeJson(root, wrongApkBuilderPackageRequirement.relative, wrongApkBuilderPackage);
    const wrongApkBuilderPackageResult =
      evaluateDeviceReport(root, wrongApkBuilderPackageRequirement, identity, root);
    assert.strictEqual(wrongApkBuilderPackageResult.ready, false);
    assert(wrongApkBuilderPackageResult.blockers.some((message) =>
      message.includes("APK Builder plugin APK package must be org.autojs.plugin.apkbuilder.template")));

    const fakeApkBuilderSignerRequirement = DEVICE_REQUIREMENTS[0];
    const fakeApkBuilderSigner = validDeviceReport(fakeApkBuilderSignerRequirement);
    fakeApkBuilderSigner.apkBuilderPlugin.signerSha256 =
      fakeApkBuilderSigner.apkBuilderPlugin.signerSha256 === "f".repeat(64)
        ? "e".repeat(64)
        : "f".repeat(64);
    writeJson(root, fakeApkBuilderSignerRequirement.relative, fakeApkBuilderSigner);
    const fakeApkBuilderSignerResult =
      evaluateDeviceReport(root, fakeApkBuilderSignerRequirement, identity, root);
    assert.strictEqual(fakeApkBuilderSignerResult.ready, false);
    assert(fakeApkBuilderSignerResult.blockers.some((message) =>
      message.includes("APK Builder plugin signerSha256 does not match")));

    const hostBuilderSignerMismatchRequirement = DEVICE_REQUIREMENTS[0];
    const hostBuilderSignerMismatch = validDeviceReport(hostBuilderSignerMismatchRequirement);
    const alternateSignerBuilderApk = contractApk("apk-builder-plugin-other-signer", "");
    for (const prefix of [
      "apkBuilderPluginApk",
      "apkBuilderPluginApkBeforeTest",
      "apkBuilderPluginBuildApk",
    ]) {
      bindFixtureApk(
        hostBuilderSignerMismatch,
        prefix,
        alternateSignerBuilderApk,
        `apk-builder-other-signer-${prefix}`,
      );
    }
    hostBuilderSignerMismatch.apkBuilderPlugin.signerSha256 =
      apkSignerSha256(alternateSignerBuilderApk);
    writeJson(
      root,
      hostBuilderSignerMismatchRequirement.relative,
      hostBuilderSignerMismatch,
    );
    const hostBuilderSignerMismatchResult = evaluateDeviceReport(
      root,
      hostBuilderSignerMismatchRequirement,
      identity,
      root,
    );
    assert.strictEqual(hostBuilderSignerMismatchResult.ready, false);
    assert(hostBuilderSignerMismatchResult.blockers.some((message) =>
      message.includes("host APK and installed APK Builder plugin APK must have the same signer")));

    const invalidApkBuilderProvenanceRequirement = DEVICE_REQUIREMENTS[0];
    const invalidApkBuilderProvenance = validDeviceReport(invalidApkBuilderProvenanceRequirement);
    invalidApkBuilderProvenance.apkBuilderPlugin.buildTask =
      ":plugins:apk-builder-template:packageDebug";
    invalidApkBuilderProvenance.apkBuilderPlugin.remoteBuildEnabled = false;
    invalidApkBuilderProvenance.apkBuilderPlugin.hostCommit = "3".repeat(40);
    invalidApkBuilderProvenance.apkBuilderPlugin.installedMatchesFreshBuild = false;
    invalidApkBuilderProvenance.apkBuilderPlugin.installedUnchangedDuringTest = false;
    invalidApkBuilderProvenance.apkBuilderPlugin.servicePreflight.matched = false;
    writeJson(root, invalidApkBuilderProvenanceRequirement.relative, invalidApkBuilderProvenance);
    const invalidApkBuilderProvenanceResult =
      evaluateDeviceReport(root, invalidApkBuilderProvenanceRequirement, identity, root);
    assert.strictEqual(invalidApkBuilderProvenanceResult.ready, false);
    assert(invalidApkBuilderProvenanceResult.blockers.some((message) =>
      message.includes("buildTask must be :plugins:apk-builder-template:packageRelease")));
    assert(invalidApkBuilderProvenanceResult.blockers.some((message) =>
      message.includes("remoteBuildEnabled must be true")));
    assert(invalidApkBuilderProvenanceResult.blockers.some((message) =>
      message.includes("hostCommit must match")));
    assert(invalidApkBuilderProvenanceResult.blockers.some((message) =>
      message.includes("installedMatchesFreshBuild must be true")));
    assert(invalidApkBuilderProvenanceResult.blockers.some((message) =>
      message.includes("installedUnchangedDuringTest must be true")));
    assert(invalidApkBuilderProvenanceResult.blockers.some((message) =>
      message.includes("servicePreflight must prove")));

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
    invalidMetrics.samples[1].nativeHeapKb = -1;
    invalidMetrics.samples[1].servicePid = 101;
    writeJson(root, invalidMetricsRequirement.relative, invalidMetrics);
    const invalidMetricsResult = evaluateSoakReport(root, invalidMetricsRequirement, identity, root);
    assert.strictEqual(invalidMetricsResult.ready, false);
    assert(invalidMetricsResult.blockers.some((message) => message.includes("fdCount must be positive")));
    assert(invalidMetricsResult.blockers.some((message) => message.includes("nativeHeapKb must be non-negative")));
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

    const invalidWakeLockRequirement = SOAK_REQUIREMENTS[0];
    const invalidWakeLock = validSoakReport(invalidWakeLockRequirement);
    invalidWakeLock.configuration.wakeLockPolicy = "none";
    invalidWakeLock.configuration.wakeLockTimeoutMs = invalidWakeLock.durationMs - 1;
    invalidWakeLock.configuration.cycleTimeoutMs = 60_000;
    invalidWakeLock.cleanup.wakeLockAcquired = false;
    invalidWakeLock.cleanup.wakeLockHeldUntilCompletion = false;
    invalidWakeLock.cleanup.wakeLockReleased = false;
    invalidWakeLock.powerManagement.suspendDurationMs = 5_000;
    writeJson(root, invalidWakeLockRequirement.relative, invalidWakeLock);
    const invalidWakeLockResult = evaluateSoakReport(root, invalidWakeLockRequirement, identity, root);
    assert.strictEqual(invalidWakeLockResult.ready, false);
    assert(invalidWakeLockResult.blockers.some((message) => message.includes("wakeLockPolicy must be bounded_partial")));
    assert(invalidWakeLockResult.blockers.some((message) => message.includes("wakeLockTimeoutMs must equal")));
    assert(invalidWakeLockResult.blockers.some((message) => message.includes("cycleTimeoutMs must be 15000")));
    assert(invalidWakeLockResult.blockers.some((message) => message.includes("wakeLockAcquired must be true")));
    assert(invalidWakeLockResult.blockers.some((message) => message.includes("wakeLockHeldUntilCompletion must be true")));
    assert(invalidWakeLockResult.blockers.some((message) => message.includes("wakeLockReleased must be true")));
    assert(invalidWakeLockResult.blockers.some((message) => message.includes("suspendDurationMs must be an exact value")));

    const malformedWakeLockEnums = validSoakReport(invalidWakeLockRequirement);
    malformedWakeLockEnums.configuration.wakeLockPolicy = ["bounded_partial"];
    malformedWakeLockEnums.powerManagement.wakeLockType = ["partial"];
    writeJson(root, invalidWakeLockRequirement.relative, malformedWakeLockEnums);
    const malformedWakeLockEnumsResult =
      evaluateSoakReport(root, invalidWakeLockRequirement, identity, root);
    assert.strictEqual(malformedWakeLockEnumsResult.ready, false);
    assert(malformedWakeLockEnumsResult.blockers.some((message) =>
      message.includes("wakeLockPolicy must be bounded_partial")));
    assert(malformedWakeLockEnumsResult.blockers.some((message) =>
      message.includes("wakeLockType must be partial")));

    const optionalProcRequirement = SOAK_REQUIREMENTS[0];
    const optionalProc = validSoakReport(optionalProcRequirement);
    const optionalProcSample = optionalProc.samples[1];
    optionalProcSample.remoteProcCaptureStatus = "unavailable_but_plugin_alive";
    optionalProcSample.procMetricsAvailable = false;
    optionalProcSample.procRssKb = null;
    optionalProcSample.procThreadCount = null;
    optionalProcSample.remoteProcCaptureAttempts = 5;
    optionalProcSample.remoteProcCaptureFailure = "transient cross-UID proc visibility failure";
    optionalProcSample.remoteProcFallbackLiveness = {
      cycle: optionalProcSample.cycle,
      checkedAtEpochMs: optionalProcSample.capturedAtEpochMs,
      mode: "non_destructive_binder_session_health_and_recoverability",
      passed: true,
      runtimeReady: true,
      persistentProcessRuntime: true,
      trigger: "remote_proc_probe_unavailable",
      probeFailure: optionalProcSample.remoteProcCaptureFailure,
      servicePid: optionalProcSample.servicePid,
      hostSessionGeneration: optionalProcSample.pluginSessionGeneration,
    };
    writeJson(root, optionalProcRequirement.relative, optionalProc);
    const optionalProcResult = evaluateSoakReport(root, optionalProcRequirement, identity, root);
    assert.strictEqual(optionalProcResult.ready, true, optionalProcResult.blockers.join("; "));

    const invalidProcFallback = JSON.parse(JSON.stringify(optionalProc));
    invalidProcFallback.samples[1].remoteProcFallbackLiveness.servicePid += 1;
    writeJson(root, optionalProcRequirement.relative, invalidProcFallback);
    const invalidProcFallbackResult = evaluateSoakReport(root, optionalProcRequirement, identity, root);
    assert.strictEqual(invalidProcFallbackResult.ready, false);
    assert(invalidProcFallbackResult.blockers.some((message) => message.includes("matching live Binder identity")));

    const malformedProcEnums = JSON.parse(JSON.stringify(optionalProc));
    malformedProcEnums.samples[0].remoteProcCaptureStatus = ["captured"];
    malformedProcEnums.samples[1].remoteProcFallbackLiveness.mode = [
      "non_destructive_binder_session_health_and_recoverability",
    ];
    malformedProcEnums.samples[1].remoteProcFallbackLiveness.trigger = [
      "remote_proc_probe_unavailable",
    ];
    writeJson(root, optionalProcRequirement.relative, malformedProcEnums);
    const malformedProcEnumsResult =
      evaluateSoakReport(root, optionalProcRequirement, identity, root);
    assert.strictEqual(malformedProcEnumsResult.ready, false);
    assert(malformedProcEnumsResult.blockers.some((message) =>
      message.includes("remoteProcCaptureStatus is invalid")));
    assert(malformedProcEnumsResult.blockers.some((message) =>
      message.includes("matching live Binder identity")));

    const invalidCapturedProc = validSoakReport(optionalProcRequirement);
    invalidCapturedProc.samples[1].procRssKb = null;
    writeJson(root, optionalProcRequirement.relative, invalidCapturedProc);
    const invalidCapturedProcResult = evaluateSoakReport(root, optionalProcRequirement, identity, root);
    assert.strictEqual(invalidCapturedProcResult.ready, false);
    assert(invalidCapturedProcResult.blockers.some((message) => message.includes("procRssKb must be a non-negative")));

    const nullSuspend = validSoakReport(optionalProcRequirement);
    nullSuspend.powerManagement.suspendDurationMs = null;
    writeJson(root, optionalProcRequirement.relative, nullSuspend);
    const nullSuspendResult = evaluateSoakReport(root, optionalProcRequirement, identity, root);
    assert.strictEqual(nullSuspendResult.ready, false);
    assert(nullSuspendResult.blockers.some((message) => message.includes("suspendDurationMs must be an exact value")));

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
    const incompleteHostApk = contractApk("host-incomplete", incompleteHost.device.hostProcessAbi);
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
  const hostProcessAbi = requirement.runtimeProcessAbi === "armeabi-v7a"
    ? "arm64-v8a"
    : requirement.runtimeProcessAbi;
  const artifacts = artifactHashes(
    `device-${requirement.id}`,
    requirement.runtimeProcessAbi,
    "device",
    runToken,
    hostProcessAbi,
  );
  const signerSha256 = apkSignerSha256(path.join(contractRoot, artifacts.pluginApkPath));
  const apkBuilderPluginSignerSha256 =
    apkSignerSha256(path.join(contractRoot, artifacts.apkBuilderPluginApkPath));
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
      apiLevel: requirement.apiLevel || 36,
      ...(requirement.avdName ? { avdName: requirement.avdName } : {}),
      ...(Number.isInteger(requirement.pageSizeBytes)
        ? { pageSizeBytes: requirement.pageSizeBytes }
        : {}),
      isEmulator: requirement.emulator,
      hostProcessAbi,
      runtimeProcessAbi: requirement.runtimeProcessAbi,
      supportedAbis: [...new Set([hostProcessAbi, requirement.runtimeProcessAbi])],
    },
    artifacts,
    apkBuilderPlugin: {
      packageName: APK_BUILDER_PLUGIN_PACKAGE,
      buildTask: ":plugins:apk-builder-template:packageRelease",
      remoteBuildEnabled: true,
      hostCommit: identity.host.commit,
      signerSha256: apkBuilderPluginSignerSha256,
      installedMatchesFreshBuild: true,
      installedUnchangedDuringTest: true,
      servicePreflight: {
        action: APK_BUILDER_PLUGIN_ACTION,
        expectedComponent: APK_BUILDER_PLUGIN_COMPONENT,
        components: [APK_BUILDER_PLUGIN_COMPONENT],
        matched: true,
      },
    },
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
      durationMs,
      sampleIntervalMs: 300_000,
      minimumRequiredSamples: sampleCount,
      livenessRecoverabilityEveryCycles: sampleCount,
      cycleTimeoutMs: 15_000,
      wakeLockPolicy: "bounded_partial",
      wakeLockTimeoutMs: durationMs + 15 * 60_000,
      wakeLockGraceMs: 15 * 60_000,
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
      wakeLockAcquired: true,
      wakeLockHeldUntilCompletion: true,
      wakeLockReleased: true,
    },
    powerManagement: {
      wakeLockType: "partial",
      wakeLockHeldElapsedMs: durationMs,
      wakeLockHeldUptimeMs: durationMs,
      suspendDurationMs: 0,
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

function artifactHashes(prefix, runtimeProcessAbi, junitKind, runToken = "", hostProcessAbi = runtimeProcessAbi) {
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
    bindings.push(
      [
        "apkBuilderPluginApkSha256",
        "apkBuilderPluginApkPath",
        "apkBuilderPluginApkBytes",
      ],
      [
        "apkBuilderPluginApkBeforeTestSha256",
        "apkBuilderPluginApkBeforeTestPath",
        "apkBuilderPluginApkBeforeTestBytes",
      ],
      [
        "apkBuilderPluginBuildApkSha256",
        "apkBuilderPluginBuildApkPath",
        "apkBuilderPluginBuildApkBytes",
      ],
    );
    bindings.push([
      "packagedTemplateApkSha256",
      "packagedTemplateApkPath",
      "packagedTemplateApkBytes",
    ]);
  }
  const result = {};
  bindings.forEach(([hashKey, pathKey, bytesKey]) => {
    const extension = hashKey === "junitXmlSha256" ? "xml" : "bin";
    const normalizedHashKey = hashKey.toLowerCase();
    const apkRole = normalizedHashKey.includes("apkbuilderplugin")
      ? "apk-builder-plugin"
      : normalizedHashKey.includes("plugin")
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
        const artifactAbi = apkRole === "host" ? hostProcessAbi : runtimeProcessAbi;
        fs.copyFileSync(contractApk(apkRole, artifactAbi), file);
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
    result.installedApkBuilderPluginMatchesFreshBuild = true;
    result.installedApkBuilderPluginUnchangedDuringTest = true;
  }
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
  if (junitKind === "device") {
    assert.strictEqual(
      result.apkBuilderPluginApkSha256,
      result.apkBuilderPluginBuildApkSha256,
    );
    assert.strictEqual(
      result.apkBuilderPluginApkBeforeTestSha256,
      result.apkBuilderPluginBuildApkSha256,
    );
  }
  return result;
}

function contractApk(role, runtimeProcessAbi) {
  const cacheKey = role === "test" || role.startsWith("apk-builder-plugin")
    ? role
    : `${role}-${runtimeProcessAbi}`;
  if (contractApkCache.has(cacheKey)) return contractApkCache.get(cacheKey);
  assert(
    contractRoot && contractAndroidTools && contractKeystore && contractAlternateKeystore,
    "contract APK tools are unavailable",
  );
  const directory = path.join(contractRoot, "generated-apks", cacheKey);
  fs.mkdirSync(directory, { recursive: true });
  const packageName = role === "plugin"
    ? "io.github.supermonster003.autojs6.plugin.nodejs"
    : role === "test"
      ? "org.autojs.autojs6.test"
      : role === "template"
        ? "org.autojs.autojs6.inrt"
        : role.startsWith("apk-builder-plugin")
          ? "org.autojs.plugin.apkbuilder.template"
          : "org.autojs.autojs6";
  const applicationAttributes = role === "apk-builder-plugin-alt"
    ? ' android:hasCode="false" android:label="alternate"'
    : ' android:hasCode="false"';
  const manifest = path.join(directory, "AndroidManifest.xml");
  fs.writeFileSync(
    manifest,
    `<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="${packageName}">` +
      `<uses-sdk android:minSdkVersion="24" android:targetSdkVersion="36"/>` +
      `<application${applicationAttributes}/></manifest>\n`,
  );
  const unsigned = path.join(directory, "unsigned.apk");
  runContractTool(contractAndroidTools.aapt, [
    "package", "-f", "-M", manifest, "-I", contractAndroidTools.androidJar, "-F", unsigned,
  ], directory);
  const nativeLibraries = role === "plugin"
    ? ["libnode.so", "libautojs6-node.so", "libc++_shared.so"]
    : role === "host"
      ? ["libhost-contract.so", "libjackpal-termexec2.so", "libc++_shared.so"]
      : role === "template"
        ? ["libjackpal-termexec2.so", "libc++_shared.so"]
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
  const useAlternateSigner = role === "apk-builder-plugin-other-signer";
  runContractTool(contractAndroidTools.apksigner, [
    "sign",
    "--ks", useAlternateSigner ? contractAlternateKeystore : contractKeystore,
    "--ks-key-alias", useAlternateSigner ? "alternate" : "contract",
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
  contractAlternateKeystore = path.join(contractRoot, "contract-alternate-signing.jks");
  runContractTool("keytool", [
    "-genkeypair", "-noprompt",
    "-keystore", contractAlternateKeystore,
    "-storepass", "changeit",
    "-keypass", "changeit",
    "-alias", "alternate",
    "-dname", "CN=AutoJs6 R6 Alternate Contract",
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

function assertEmulatorDeviceContractParsers() {
  const requirement = DEVICE_REQUIREMENTS.find((entry) => entry.id === "emulator-x86_64");
  assert(requirement, "emulator-x86_64 requirement is missing");
  assert.strictEqual(DEVICE_SCHEMA, "autojs6-node-r6-device-evidence-v2");
  assert.deepStrictEqual(
    {
      avdName: requirement.avdName,
      apiLevel: requirement.apiLevel,
      pageSizeBytes: requirement.pageSizeBytes,
    },
    { avdName: "AVD_API_37", apiLevel: 37, pageSizeBytes: 16_384 },
  );
  assert.strictEqual(parseEmulatorAvdName("AVD_API_37\r\nOK\r\n"), "AVD_API_37");
  assert.throws(() => parseEmulatorAvdName("OK\n"), /exactly one AVD name/);
  assert.throws(() => parseEmulatorAvdName("AVD_API_37\nOTHER_AVD\nOK\n"), /exactly one AVD name/);
  assert.strictEqual(parsePageSizeBytes("16384\n"), 16_384);
  assert.throws(() => parsePageSizeBytes(""), /positive decimal integer/);
  assert.throws(() => parsePageSizeBytes("16 KiB"), /positive decimal integer/);
}

function assertDeviceRunnerGradleContracts() {
  const collectorSource = fs.readFileSync(
    path.resolve(__dirname, "run-device-evidence.js"),
    "utf8",
  );
  assert(collectorSource.includes(
    "buildIsolatedGradleArgs(config.context.runRoot, config.args)",
  ));
  assert(collectorSource.includes('["emu", "avd", "name"]'));
  assert(collectorSource.includes('["shell", "getconf", "PAGE_SIZE"]'));
  const assertIsolatedGradleArgs = (args, label) => {
    assert.strictEqual(
      args.filter((value) => value === "--no-daemon").length,
      1,
      `${label} must use exactly one single-use Gradle daemon argument`,
    );
    assert(!args.includes("--daemon"), `${label} must not enable the reusable Gradle daemon`);
    assert.deepStrictEqual(
      args.filter((value) => value.startsWith("-Pkotlin.compiler.execution.strategy=")),
      ["-Pkotlin.compiler.execution.strategy=in-process"],
      `${label} must use only in-process Kotlin compilation`,
    );
  };
  const pluginArgs = buildPluginGradleArgs();
  assertIsolatedGradleArgs(pluginArgs, "runtime plugin build");
  assert.strictEqual(pluginArgs.filter((value) => value === ":app:packageDebug").length, 1);
  assertPerRunDaemonRegistryArgs(pluginArgs, "runtime plugin build");
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
    assertIsolatedGradleArgs(evidenceArgs, `${requirement.id} evidence gate`);
    assertPerRunDaemonRegistryArgs(evidenceArgs, `${requirement.id} evidence gate`);
    assert.strictEqual(evidenceArgs.filter((value) => value ===
      ":app:verifyNodeR6DeviceEvidenceGate").length, 1);
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
  const templateArgs = buildPackagedTemplateGradleArgs(identity.host.commit);
  assertIsolatedGradleArgs(templateArgs, "packaged template build");
  assertPerRunDaemonRegistryArgs(templateArgs, "packaged template build");
  assert.strictEqual(templateArgs.filter((value) => value === ":app:packageInrtDebug").length, 1);
  assert(!templateArgs.includes(":plugins:apk-builder-template:packageRelease"));
  assert(!templateArgs.some((value) =>
    value.startsWith("-Pautojs.apkBuilder.templatePlugin.enableRemoteBuild=")));
  const apkBuilderArgs = buildApkBuilderPluginGradleArgs(identity.host.commit);
  assertIsolatedGradleArgs(apkBuilderArgs, "APK Builder plugin build");
  assertPerRunDaemonRegistryArgs(apkBuilderArgs, "APK Builder plugin build");
  assert(!apkBuilderArgs.includes(":app:packageInrtDebug"));
  assert.strictEqual(
    apkBuilderArgs.filter((value) =>
      value === ":plugins:apk-builder-template:packageRelease").length,
    1,
  );
  assert(!apkBuilderArgs.includes(":plugins:apk-builder-template:packageDebug"));
  assert.deepStrictEqual(
    apkBuilderArgs.filter((value) =>
      value.startsWith("-Pautojs.apkBuilder.templatePlugin.enableRemoteBuild=")),
    ["-Pautojs.apkBuilder.templatePlugin.enableRemoteBuild=true"],
  );
  [templateArgs, apkBuilderArgs].forEach((args) => {
    assert.deepStrictEqual(
      args.filter((value) => value.startsWith("-Pautojs.git.sha=")),
      [`-Pautojs.git.sha=${identity.host.commit}`],
    );
    assert(args.includes("-Pautojs.nodejs.embedded.script.enabled=true"));
    assert(!args.some((value) => value.startsWith("-Pautojs.nodejs.releaseFlavor=")));
  });
  const packagedTemplateInvocation = collectorSource.indexOf(
    "args: buildPackagedTemplateGradleArgs(identityBefore.host.commit)",
  );
  const apkBuilderInvocation = collectorSource.indexOf(
    "args: buildApkBuilderPluginGradleArgs(identityBefore.host.commit)",
  );
  assert(packagedTemplateInvocation >= 0);
  assert(apkBuilderInvocation > packagedTemplateInvocation);
  assert(collectorSource.includes('label: "packaged-template-build"'));
  assert(collectorSource.includes('label: "apk-builder-plugin-build"'));
  assert(REQUIRED_DEVICE_ARTIFACT_HASHES.includes("packagedTemplateApkSha256"));
  assert(REQUIRED_DEVICE_ARTIFACT_HASHES.includes("apkBuilderPluginApkSha256"));
  assert(REQUIRED_DEVICE_ARTIFACT_HASHES.includes("apkBuilderPluginApkBeforeTestSha256"));
  assert(REQUIRED_DEVICE_ARTIFACT_HASHES.includes("apkBuilderPluginBuildApkSha256"));
  assert(!REQUIRED_ARTIFACT_HASHES.includes("packagedTemplateApkSha256"));
  assert(!REQUIRED_ARTIFACT_HASHES.includes("apkBuilderPluginApkSha256"));
  assert(!REQUIRED_ARTIFACT_HASHES.includes("apkBuilderPluginApkBeforeTestSha256"));
  assert(!REQUIRED_ARTIFACT_HASHES.includes("apkBuilderPluginBuildApkSha256"));
}

function assertPerRunDaemonRegistryArgs(baseArgs, label) {
  const runRoot = path.join(contractRoot, "runner path with spaces", label);
  const expectedRegistry = path.join(path.resolve(runRoot), "gradle-daemon-registry");
  assert.strictEqual(gradleDaemonRegistryBaseForRun(runRoot), expectedRegistry);
  const isolatedArgs = buildIsolatedGradleArgs(runRoot, baseArgs);
  assert.deepStrictEqual(
    isolatedArgs.filter((value) =>
      value.startsWith("-Dorg.gradle.daemon.registry.base=")),
    [`-Dorg.gradle.daemon.registry.base=${expectedRegistry}`],
    `${label} must use one per-run daemon registry while retaining the shared Gradle cache`,
  );
  assert.throws(
    () => buildIsolatedGradleArgs(runRoot, isolatedArgs),
    /must be injected exactly once/,
  );
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

function assertPackagedHostVerifierSourceContract() {
  const source = fs.readFileSync(
    path.resolve(__dirname, "../../../app/embedded-node-packaging-preflight.gradle.kts"),
    "utf8",
  );
  const apkBuilderBuildSource = fs.readFileSync(
    path.resolve(__dirname, "../../../plugins/apk-builder-template/build.gradle.kts"),
    "utf8",
  );
  assert(source.includes("embeddedNodePackagingJsonMap(json)?.get(key) as? String"));
  assert(source.includes("embeddedNodePackagingModuleSourceMaxBytes = 16_777_216"));
  assert(source.includes("embeddedNodePackagingModuleSourcesMaxBytes = 67_108_864"));
  assert(source.includes('"packaged.readlink.symlink=true"'));
  assert(source.includes('"packaged.readlink.rejects=true"'));
  assert(source.includes('embeddedNodePackagingFullSmokeTailMarker = "packaged.tail=true"'));
  assert(source.includes(
    "embeddedNodePackagingRealNpmCorpusReadyMarker = embeddedNodePackagingFullSmokeTailMarker",
  ));
  assert(source.includes(
    "embeddedNodePackagingCjsResolverReadyMarker = embeddedNodePackagingFullSmokeTailMarker",
  ));
  assert(source.includes(
    "embeddedNodePackagingDependencyResolutionReadyMarker = embeddedNodePackagingFullSmokeTailMarker",
  ));
  assert(!source.includes('"packaged.readlink.symlink_disabled=true"'));
  assert(source.includes('"packaged.diagnostics.esm.type_module.value=true"'));
  assert(source.includes('"packaged.diagnostics.esm.async_code=ERR_REQUIRE_ASYNC_MODULE"'));
  assert(source.includes('"packaged.diagnostics.esm.code=ok"'));
  assert(source.includes('"assets/project/node_modules/diagnostic-async-esm/index.mjs"'));
  assert(!source.includes('"packaged.diagnostics.esm.type_module.message=true"'));
  assert(!source.includes('"packaged.diagnostics.esm.code=ERR_AUTOJS6_EMBEDDED_NODE_ESM_UNSUPPORTED"'));
  assert(source.includes('releaseFlavor == "nodePlugin" && expectedAbis.isEmpty()'));
  assert(source.includes('runtimeBackend in setOf("embedded", "plugin", "pluginOptional")'));
  assert(source.includes(
    "EmbeddedNodePackagedSmokeRole.FULL -> fullCompatibilitySmokeReady",
  ));
  assert(source.includes(
    "EmbeddedNodePackagedSmokeRole.DIAGNOSTICS -> diagnosticsRequested && diagnosticsReady",
  ));
  assert(source.includes("packagedSmokeEvidenceReady &&"));
  assert(source.includes("(!moduleSourcesBudgetRequested || fullCompatibilitySmokeReady)"));
  assert(source.includes("process.waitFor(timeoutSeconds, TimeUnit.SECONDS)"));
  assert(source.includes("process.destroyForcibly()"));
  assert(source.includes("catch (error: InterruptedException)"));
  assert(source.includes("Thread.currentThread().interrupt()"));
  assert(source.includes("mergeError = false"));
  assert(apkBuilderBuildSource.includes(
    'currentSourceProvenanceSchema = "autojs6-apk-builder-template-current-source-v1"',
  ));
  assert(apkBuilderBuildSource.includes("rootProject.apkBuilderTemplateRepositoryState()"));
  assert(apkBuilderBuildSource.includes("currentSourceStateBeforeReleaseBuild"));
  assert(apkBuilderBuildSource.includes("repositoryStateBeforeBuild == repositoryState"));
  assert(apkBuilderBuildSource.includes('providers.gradleProperty("autojs.git.sha")'));
  assert(apkBuilderBuildSource.includes("configuredHostGitSha == repositoryState.commit"));
  assert(apkBuilderBuildSource.includes('zip.getEntry("assets/runtime-kit/runtime-kit.json")'));
  assert(apkBuilderBuildSource.includes("runtimeKitGitSha == repositoryState.commit"));
  assert(apkBuilderBuildSource.includes('setProperty("build.rerun_tasks", gradle.startParameter.isRerunTasks.toString())'));
  assert(apkBuilderBuildSource.includes('setProperty("runtime_kit.git_sha", runtimeKitGitSha)'));
  assert(apkBuilderBuildSource.includes('setProperty("source.fingerprint", repositoryState.fingerprint)'));
  assert(apkBuilderBuildSource.includes('setProperty("apk.sha256", releaseApk.sha256String())'));
  assert(apkBuilderBuildSource.includes("outputs.file(currentSourceProvenanceFile)"));

  const pullTaskStart = source.indexOf("val embeddedNodePullPackagedSmokeApks = tasks.register(");
  const pullTaskEnd = source.indexOf(
    "\nval embeddedNodePackagingPreflightAppDebug = tasks.register(",
    pullTaskStart,
  );
  assert(pullTaskStart >= 0 && pullTaskEnd > pullTaskStart);
  const pullTaskSource = source.slice(pullTaskStart, pullTaskEnd);
  assert.match(
    pullTaskSource,
    /mustRunAfter\([\s\S]*?"connectedAppDebugAndroidTest",[\s\S]*?embeddedNodeVerifyPackagedSmokeInstrumentationResults,[\s\S]*?\)/,
  );
  assert(pullTaskSource.includes("dependsOn(embeddedNodeVerifyPackagedSmokeInstrumentationResults)"));
  assert(pullTaskSource.includes("embeddedNodePackagingSmokeVerifierReadyApkNames.forEach"));
  assert(!source.includes("embeddedNodePackagingSmokeApkNames"));

  const precleanTaskStart = source.indexOf(
    "val embeddedNodePrecleanPackagedSmokeInstrumentation = tasks.register(",
  );
  const resultTaskStart = source.indexOf(
    "val embeddedNodeVerifyPackagedSmokeInstrumentationResults = tasks.register(",
  );
  assert(precleanTaskStart >= 0 && resultTaskStart > precleanTaskStart);
  const precleanTaskSource = source.slice(precleanTaskStart, resultTaskStart);
  assert(precleanTaskSource.includes("resultDir.deleteRecursively()"));
  assert(precleanTaskSource.includes("embeddedNodePackagingSmokeVerifierReadyApkNames.forEach"));
  assert(precleanTaskSource.includes('"shell", "rm", "-f", remotePath'));
  assert(precleanTaskSource.includes('"shell", "test", "!", "-e", remotePath'));
  assert(source.includes("embeddedNodePackagingApkBuilderPluginPackage"));
  assert(precleanTaskSource.includes("embeddedNodePackagingInspectApkBuilder("));
  assert(precleanTaskSource.includes("source.fingerprint"));
  assert(precleanTaskSource.includes("installed.apk.sha256"));
  assert(precleanTaskSource.includes("runtime_kit.git_sha"));
  assert(precleanTaskSource.includes("apkBuilderSnapshotFile.exists() && !apkBuilderSnapshotFile.delete()"));
  assert(precleanTaskSource.includes("usesConnectedTestUnsupportedAliasOnly"));
  assert(precleanTaskSource.includes("!adbSerialSelection.hasConnectedTestBinding"));
  assert(precleanTaskSource.includes("RC/release connected testing requires ANDROID_SERIAL"));
  assert(precleanTaskSource.includes("apk_builder_snapshot_ready=$snapshotReady"));
  assert(precleanTaskSource.includes("apk_builder_plugin_ready=$apkBuilderPluginReady"));

  const resultTaskSource = source.slice(resultTaskStart, pullTaskStart);
  assert(resultTaskSource.includes('dependsOn(\n        embeddedNodePrecleanPackagedSmokeInstrumentation,\n        "connectedAppDebugAndroidTest",'));
  assert(resultTaskSource.includes("xmlFiles.size != 1"));
  assert(resultTaskSource.includes("totalTests == expectedSelectors.size"));
  assert(resultTaskSource.includes("totalSkipped == 0"));
  assert(resultTaskSource.includes("missingSelectors.isEmpty()"));
  assert(resultTaskSource.includes("unexpectedSelectors.isEmpty()"));
  assert(resultTaskSource.includes("nonPassingSelectors.isEmpty()"));
  assert(resultTaskSource.includes("snapshotFingerprint != apkBuilderPostInspection.repositoryState?.fingerprint"));
  assert(resultTaskSource.includes("snapshotApkSha256 != apkBuilderPostInspection.installedSha256"));
  assert(resultTaskSource.includes("snapshotApkSha256 != snapshotInstalledSha256"));
  assert(resultTaskSource.includes("snapshotRuntimeKitGitSha != apkBuilderPostInspection.runtimeKitGitSha"));
  assert(resultTaskSource.includes("apk_builder_unchanged=$apkBuilderUnchanged"));
  assert(resultTaskSource.includes("nonPassingSelectors.isEmpty() &&\n                apkBuilderUnchanged"));
  assert(pullTaskSource.includes("!hostDir.deleteRecursively()"));
  assert(pullTaskSource.includes("!hostDir.mkdirs() && !hostDir.isDirectory"));
  assert.match(
    source,
    /tasks\.matching \{ task -> task\.name == "connectedAppDebugAndroidTest" \}\.configureEach \{[\s\S]*?mustRunAfter\(embeddedNodePrecleanPackagedSmokeInstrumentation\)/,
  );

  const hostVerifyTaskStart = source.indexOf(
    "val embeddedNodePackagedSmokeHostVerify = tasks.register(",
  );
  const hostVerifyTaskEnd = source.indexOf(
    "\nval verifyEmbeddedNodeDebug = tasks.register(",
    hostVerifyTaskStart,
  );
  assert(hostVerifyTaskStart >= 0 && hostVerifyTaskEnd > hostVerifyTaskStart);
  const hostVerifyTaskSource = source.slice(hostVerifyTaskStart, hostVerifyTaskEnd);
  assert.match(
    hostVerifyTaskSource,
    /packagedApkProperty = successApk\.absolutePath,[\s\S]*?expectedSmokeRole = EmbeddedNodePackagedSmokeRole\.FULL,/,
  );
  assert.match(
    hostVerifyTaskSource,
    /packagedApkProperty = diagnosticsApk\.absolutePath,[\s\S]*?expectedSmokeRole = EmbeddedNodePackagedSmokeRole\.DIAGNOSTICS,/,
  );

  const releaseCandidateTaskStart = source.indexOf(
    'tasks.register("verifyEmbeddedNodeReleaseCandidate")',
  );
  assert(releaseCandidateTaskStart >= 0);
  const releaseCandidateTaskSource = source.slice(releaseCandidateTaskStart);
  assert(releaseCandidateTaskSource.includes("embeddedNodeVerifyPackagedSmokeInstrumentationResults"));
  assert(!releaseCandidateTaskSource.includes('"connectedAppDebugAndroidTest"'));
}

function assertNodeErrorCatalogSourceContract() {
  const source = fs.readFileSync(
    path.resolve(__dirname, "../../../app/node-error-catalog.gradle.kts"),
    "utf8",
  );
  assert(source.includes('nodeErrorCatalogConfiguredFile("nodeJsPluginProjectDir")'));
  assert(source.includes('nodeErrorCatalogConfiguredFile("nodeJsPluginCppDir")'));
  assert(source.includes('nodeErrorCatalogPluginProjectDir?.resolve("app/src/main/java")'));
  assert(source.includes("inputs.file(nodeErrorCatalogRegistryFile)"));
  assert(source.includes("inputs.file(nodeErrorCatalogCatalogFile)"));
  assert(source.includes(
    "nodeErrorCatalogRuntimeSourceRoots.filter(File::isDirectory).forEach { inputs.dir(it) }",
  ));
  assert(source.includes("missingConfiguredPluginRoots.isNotEmpty()"));
  assert(source.includes('"runtimeSourceRoots" to nodeErrorCatalogRuntimeSourceRoots'));
}

function assertReleaseInstrumentationScopeContract() {
  const source = fs.readFileSync(
    path.resolve(__dirname, "../../../app/node-instrumentation-gates.gradle.kts"),
    "utf8",
  );
  assert(source.includes("val nodeJsPackagedReleaseCandidateInstrumentationClasses = listOf("));
  assert(source.includes("val nodeJsR6ReleaseGateInstrumentationClasses = ("));
  assert(source.includes("val nodeJsR6PersistentPluginSoakInstrumentationSelector ="));
  assert(source.includes(
    ".filterNot { selector -> selector == nodeJsR6PersistentPluginSoakInstrumentationSelector }",
  ));
  assert(source.includes(
    '"org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunDiagnosticsEmbeddedNodePackagedApk"',
  ));
  assert(source.includes(
    '"verifyEmbeddedNodeReleaseCandidate" to nodeJsPackagedReleaseCandidateInstrumentationClasses',
  ));
  assert(source.includes(
    '"verifyNodeReleaseGate" to nodeJsR6ReleaseGateInstrumentationClasses',
  ));
  assert(source.includes("nodeJsRequestedFocusedGates.size > 1"));
  assert(source.includes("Only one focused Node instrumentation gate may be requested"));
  assert(source.includes("nodeJsRequestedFocusedGates.singleOrNull()"));
  assert(source.includes("nodeJsR6DeviceEvidenceGateInstrumentationClasses.size == 13"));
  assert(source.includes("nodeJsR6ReleaseGateInstrumentationClasses.size == 13"));

  const deviceStart = source.indexOf(
    "val nodeJsR6DeviceEvidenceGateInstrumentationClasses = listOf(",
  );
  const candidateStart = source.indexOf(
    "val nodeJsPackagedReleaseCandidateInstrumentationClasses = listOf(",
    deviceStart,
  );
  const releaseStart = source.indexOf(
    "val nodeJsR6ReleaseGateInstrumentationClasses = (",
    candidateStart,
  );
  const releaseEnd = source.indexOf(
    "val nodeJsPluginUiGateInstrumentationClasses = (",
    releaseStart,
  );
  assert(deviceStart >= 0 && candidateStart > deviceStart);
  assert(releaseStart > candidateStart && releaseEnd > releaseStart);
  const deviceBlock = source.slice(deviceStart, candidateStart);
  const candidateBlock = source.slice(candidateStart, releaseStart);
  const releaseBlock = source.slice(releaseStart, releaseEnd);
  const quotedSelectors = (block) =>
    [...block.matchAll(/"(org\.autojs[^"\r\n]+)"/g)].map((match) => match[1]);
  const fullSelector =
    "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunFullCompatibilityEmbeddedNodePackagedApk";
  const diagnosticsSelector =
    "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunDiagnosticsEmbeddedNodePackagedApk";
  const deviceSelectors = [...quotedSelectors(deviceBlock), R6_PLUGIN_SOAK_SELECTOR];
  assert.deepStrictEqual(
    [...deviceSelectors].sort(),
    [...REQUIRED_DEVICE_SELECTORS].sort(),
  );
  assert(deviceBlock.includes("nodeJsR6PersistentPluginSoakInstrumentationSelector"));
  assert.deepStrictEqual(quotedSelectors(candidateBlock), [fullSelector, diagnosticsSelector]);
  assert.deepStrictEqual(quotedSelectors(releaseBlock), [diagnosticsSelector]);
  assert(releaseBlock.includes(
    ".filterNot { selector -> selector == nodeJsR6PersistentPluginSoakInstrumentationSelector }",
  ));
  assert.deepStrictEqual(
    [...REQUIRED_DEVICE_SELECTORS.filter((selector) => selector !== R6_PLUGIN_SOAK_SELECTOR), diagnosticsSelector].sort(),
    [...new Set([...REQUIRED_DEVICE_SELECTORS.filter((selector) => selector !== R6_PLUGIN_SOAK_SELECTOR), diagnosticsSelector])].sort(),
  );
}

function assertPackagedInteractiveShellContract() {
  const source = fs.readFileSync(
    path.resolve(__dirname, "../../../app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"),
    "utf8",
  );
  const manifest = fs.readFileSync(
    path.resolve(__dirname, "../../../app/src/main/AndroidManifest.xml"),
    "utf8",
  );
  const service = fs.readFileSync(
    path.resolve(__dirname, "../../../app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt"),
    "utf8",
  );
  const statusStore = fs.readFileSync(
    path.resolve(__dirname, "../../../app/src/main/java/org/autojs/autojs/engine/NodeInteractiveLongRunningStatusStore.kt"),
    "utf8",
  );
  const receiverName = "org.autojs.autojs.engine.NativeNodeEmbeddedInteractiveShellReceiver";
  const receiverStart = manifest.indexOf(`android:name="${receiverName}"`);
  const receiverEnd = receiverStart < 0 ? -1 : manifest.indexOf("/>", receiverStart);
  assert(receiverStart >= 0 && receiverEnd > receiverStart);
  const receiver = manifest.slice(receiverStart, receiverEnd);
  assert(receiver.includes('android:exported="true"'));
  assert(receiver.includes('android:permission="android.permission.DUMP"'));
  assert(receiver.includes('android:process=":nodejs_embedded"'));
  assert(service.includes("class NativeNodeEmbeddedInteractiveShellReceiver : BroadcastReceiver()"));
  assert(service.includes("if (!isOrderedBroadcast)"));
  assert(service.includes("Base64.encodeToString(it, Base64.NO_WRAP)"));
  assert(statusStore.includes("const val SHELL_QUERY_ACTION_NAME"));
  assert(statusStore.includes("const val SHELL_STOP_ACTION_NAME"));
  assert(statusStore.includes('const val SHELL_STATUS_RESULT_PREFIX = "autojs6-node-interactive-status-v1:"'));
  assert(source.includes(`-n $packageName/${receiverName}`));
  assert(source.includes("NodeInteractiveLongRunningStatusStore.SHELL_QUERY_ACTION_NAME"));
  assert(source.includes("NodeInteractiveLongRunningStatusStore.SHELL_STOP_ACTION_NAME"));
  assert(source.includes("NodeInteractiveLongRunningStatusStore.SHELL_STATUS_RESULT_PREFIX"));
  assert(!source.includes("run-as $packageName"));
}

function assertRuntimeKitNativeBaselineContract() {
  const runtimeKitBuild = fs.readFileSync(
    path.resolve(__dirname, "../../../app/build.gradle.kts"),
    "utf8",
  );
  const apkBuilderPluginBuild = fs.readFileSync(
    path.resolve(__dirname, "../../../plugins/apk-builder-template/build.gradle.kts"),
    "utf8",
  );
  const remoteBuilder = fs.readFileSync(
    path.resolve(
      __dirname,
      "../../../plugins/apk-builder-template/src/main/java/org/autojs/plugin/apkbuilder/template/impl/RemoteApkLightweightBuilder.kt",
    ),
    "utf8",
  );
  assert(runtimeKitBuild.includes("writeRuntimeKitTemplateWithBaselineNativeLibraries("));
  assert(runtimeKitBuild.includes("libs/jackpal-androidterm-libtermexec-1_0/libtermexec-release.aar"));
  for (const abi of ["arm64-v8a", "armeabi-v7a", "x86_64", "x86"]) {
    assert(runtimeKitBuild.includes(`"${abi}"`));
    assert(apkBuilderPluginBuild.includes(`"${abi}"`));
  }
  for (const library of ["libjackpal-androidterm5.so", "libjackpal-termexec2.so"]) {
    assert(runtimeKitBuild.includes(`"${library}"`));
    assert(apkBuilderPluginBuild.includes(`"${library}"`));
  }
  assert(runtimeKitBuild.includes('"jni/$abi/$libraryName"'));
  assert(runtimeKitBuild.includes('"lib/$abi/$libraryName"'));
  assert(apkBuilderPluginBuild.includes("Runtime Kit template APK is missing baseline native library"));
  assert(remoteBuilder.includes("required native libraries in the Runtime Kit template"));
  assert(remoteBuilder.includes("or host build inputs"));
  assert(!remoteBuilder.includes("requires native libraries, but the host did not provide a build input archive"));
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

function assertAndroidComponentNameContracts() {
  assert.strictEqual(
    normalizeAndroidComponentName(
      "org.autojs.plugin.apkbuilder.template/.impl.ApkBuilderTemplatePluginService",
    ),
    APK_BUILDER_PLUGIN_COMPONENT,
  );
  assert.strictEqual(
    normalizeAndroidComponentName(APK_BUILDER_PLUGIN_COMPONENT),
    APK_BUILDER_PLUGIN_COMPONENT,
  );
  assert.strictEqual(normalizeAndroidComponentName("unexpected output"), "unexpected output");
}

function bindFixtureApk(report, prefix, source, suffix) {
  assert(contractRoot, "contract fixture root is unavailable");
  const relative = `artifacts/negative-${suffix}.apk`;
  const file = path.join(contractRoot, relative);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.copyFileSync(source, file);
  const bytes = fs.readFileSync(file);
  report.artifacts[`${prefix}Path`] = relative;
  report.artifacts[`${prefix}Sha256`] =
    crypto.createHash("sha256").update(bytes).digest("hex");
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

  const skippedRoot = path.join(root, "freshness-device-skipped");
  fs.mkdirSync(skippedRoot, { recursive: true });
  const skippedFile = path.join(skippedRoot, "TEST-skipped.xml");
  fs.writeFileSync(skippedFile, contractJUnit("device"));
  const skippedBefore = snapshotDeviceJUnitFiles(skippedRoot);
  fs.writeFileSync(
    skippedFile,
    contractJUnit("device").replace(/skipped="0"/g, 'skipped="1"'),
  );
  assert.throws(
    () => selectFreshJUnit(
      skippedRoot,
      skippedBefore,
      Date.now() - 1_000,
      REQUIRED_DEVICE_SELECTORS,
    ),
    /skipped must be explicitly zero, got 1/,
    "a changed R6 JUnit report with skipped tests must surface its exact contract failure",
  );

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
  const collectorSource = fs.readFileSync(
    path.resolve(__dirname, "run-production-soak.js"),
    "utf8",
  );
  assert(collectorSource.includes(
    "buildIsolatedGradleArgs(runRoot, buildPluginGradleArgs())",
  ));
  assert(collectorSource.includes("buildIsolatedGradleArgs(runRoot, gradleArgs)"));
  const assertIsolatedSoakGradleArgs = (args, label) => {
    assert.strictEqual(
      args.filter((value) => value === "--no-daemon").length,
      1,
      `${label} must use exactly one single-use Gradle daemon argument`,
    );
    assert(!args.includes("--daemon"), `${label} must not enable a reusable Gradle daemon`);
    assert.deepStrictEqual(
      args.filter((value) => value.startsWith("-Pkotlin.compiler.execution.strategy=")),
      ["-Pkotlin.compiler.execution.strategy=in-process"],
      `${label} must use only in-process Kotlin compilation`,
    );
    const runRoot = path.join(root, "soak runner path with spaces");
    const registryBase = soakGradleDaemonRegistryBaseForRun(runRoot);
    const isolatedArgs = buildSoakIsolatedGradleArgs(runRoot, args);
    assert.deepStrictEqual(
      isolatedArgs.filter((value) =>
        value.startsWith("-Dorg.gradle.daemon.registry.base=")),
      [`-Dorg.gradle.daemon.registry.base=${registryBase}`],
      `${label} must use one per-run Gradle daemon registry`,
    );
    assert.throws(
      () => buildSoakIsolatedGradleArgs(runRoot, isolatedArgs),
      /must be injected exactly once/,
    );
  };
  const pluginArgs = buildSoakPluginGradleArgs();
  assertIsolatedSoakGradleArgs(pluginArgs, "production soak runtime plugin build");
  assert.strictEqual(pluginArgs.filter((value) => value === ":app:packageDebug").length, 1);
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
  assertIsolatedSoakGradleArgs(args, "production soak connected build");
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
    nativeHeapCommittedKb: 32_000 + sequence % 2,
    nativePssKb: 24_000 + sequence % 2,
    threadCount: 20,
    fdCount: 80,
    fdCountSource: "plugin_uid_proc_self_fd",
    nodeHeapUsedKb: 7_000 + sequence % 2,
    libuvHandleCount: 2,
    activeBridgeResourceCount: 0,
    freshExecutionState: true,
    remoteProcCaptureStatus: "captured",
    procMetricsAvailable: true,
    procRssKb: 64_000,
    procThreadCount: 12,
    remoteProcCaptureAttempts: 1,
    remoteProcCaptureFailure: null,
    remoteProcFallbackLiveness: null,
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
