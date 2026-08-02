#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const childProcess = require("child_process");
const LIVE_PROVIDER_SNAPSHOT_ROOT = "app/build/reports/nodejs/live-provider-evidence/";

const FILES = {
  phase10RegistryReport: "build/reports/nodejs/phase10-provider-registry-v2.json",
  appLiveProviderSummary: "app/build/reports/nodejs/live-provider-summary.json",
  phase10Tool: "tools/nodejs/phase10/verify-provider-registry-v2.js",
  instrumentationGates: "app/node-instrumentation-gates.gradle.kts",
  packagingPreflight: "app/embedded-node-packaging-preflight.gradle.kts",
  phase10Gate: "app/node-phase10-gate.gradle.kts",
  finalGate: "app/node-phase11-gate.gradle.kts",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  doctor: "app/src/main/java/org/autojs/autojs/ui/settings/NodeDoctorReportGenerator.kt",
  registryTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeProviderRegistryInstrumentationTest.kt",
  doctorTest: "app/src/androidTest/java/org/autojs/autojs/ui/settings/NodeDoctorReportGeneratorInstrumentationTest.kt",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  finalDefinition: "docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md",
};

const REGISTRY_CHECKS = [
  ["phase10_tool", "phase10Tool", "autojs6-node-phase10-provider-registry-v2-gate"],
  ["phase10_task", "phase10Gate", "\"verifyNodePhase10ProviderRegistryV2\""],
  ["live_provider_gate", "packagingPreflight", "verifyNodeLiveProviderGate"],
  ["live_provider_summary_task", "instrumentationGates", "val generateNodeLiveProviderSummary = tasks.register"],
  ["final_provider_task", "finalGate", "\"verifyNodeFinalProviderGate\""],
  ["provider_schema", "providerRegistry", "autojs6-node-bridge-provider-v2"],
  ["provider_health", "providerRegistry", "healthCheck"],
  ["provider_packaged_support", "providerRegistry", "packagedSupport"],
  ["provider_active_resources", "providerRegistry", "activeResources"],
  ["provider_last_error", "providerRegistry", "lastError"],
  ["doctor_packaged_support", "doctor", "packagedSupport"],
  ["registry_test", "registryTest", "providerRegistryDefinesPhase7LiveProviderSurface"],
  ["doctor_test", "doctorTest", "generatesPrivacyRedactedTextAndJsonReports"],
  ["testing_doc", "testingDoc", "verifyNodeLiveProviderGate"],
  ["ci_doc", "ciGatesDoc", "verifyNodeLiveProviderGate"],
  ["final_definition", "finalDefinition", "Stable providers are not fake-provider-only"],
];

const EXPECTED_LIVE_PROVIDERS = [
  "registry",
  "doctor",
  "fetch",
  "websocket",
  "ocr",
  "storage",
  "notifications",
  "sensors",
  "accessibility",
  "image",
  "ui",
  "work_manager",
];

const EXPECTED_LIVE_CASES = [
  "org.autojs.autojs.engine.NodeBridgeProviderRegistryInstrumentationTest#providerRegistryDefinesPhase7LiveProviderSurface",
  "org.autojs.autojs.engine.NodeBridgeProviderRegistryInstrumentationTest#androidHealthReflectsCurrentProviderAvailabilityWithoutPromotingFutureProviders",
  "org.autojs.autojs.ui.settings.NodeDoctorReportGeneratorInstrumentationTest#generatesPrivacyRedactedTextAndJsonReports",
  "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest#jsFetchAndAxiosUseAndroidLiveProviderWhenNetworkExperimentEnabled",
  "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest#androidFetchProviderCancelsActiveLiveRequestOnDestroy",
  "org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest#jsWebSocketUsesAndroidLiveProviderWhenNetworkExperimentEnabled",
  "org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest#androidWebSocketProviderClosesActiveLiveConnectionOnDestroy",
  "org.autojs.autojs.engine.NodeOcrBridgeInstrumentationTest#kotlinOcrLiveProviderRecognizesMlKitImageHandle",
  "org.autojs.autojs.engine.NodeOcrBridgeInstrumentationTest#androidOcrRejectsUnknownImageHandleWithoutLeakingResources",
  "org.autojs.autojs.engine.NodeStorageBridgeInstrumentationTest#androidStorageProviderPersistsAndScopesByWorkingDirectory",
  "org.autojs.autojs.engine.NodeStorageBridgeInstrumentationTest#androidStorageProviderEnforcesQuotaAndPackageIdentityIsolation",
  "org.autojs.autojs.engine.NodeNotificationsBridgeInstrumentationTest#androidProviderPostsOrReportsNotificationPermissionAndTracksOwnedResource",
  "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest#androidProviderListsSensorsAndCleansUpLiveSubscriptionWhenAvailable",
  "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest#androidProviderOnceObservesLiveSensorEventWhenAvailable",
  "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest#androidProviderSubscribeDrainsEventsAndUnsubscribesWhenAvailable",
  "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest#androidProviderEnforcesLiveSubscriptionLimitWhenAvailable",
  "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest#androidProviderReportsUnavailableWhenSensorManagerMissing",
  "org.autojs.autojs.engine.NodeAccessibilityRealDeviceSmokeInstrumentationTest#providerFindsAndClicksControlledUiWhenAccessibilityServiceIsEnabled",
  "org.autojs.autojs.engine.NodeAccessibilitySelectorBridgeInstrumentationTest#androidAccessibilitySelectorV2RejectsUntilProviderIsConnected",
  "org.autojs.autojs.engine.NodeImageBridgeExpansionInstrumentationTest#androidImageExpansionRejectsUntilProviderIsConnected",
  "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest#jsUiModuleUsesAndroidLiveProviderForButtonAndInputEvents",
  "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest#androidUiProviderOpensActivityDeliversEventsAndCleansOwnedResource",
  "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest#androidUiProviderRejectsUnsupportedDescriptorsAndForeignHandles",
  "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest#androidWorkManagerProviderSchedulesLiveMetadataAndEnforcesPathPolicy",
  "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest#androidWorkManagerLiveProviderExecutesPackagedProjectEntryAndPersistsResult",
  "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest#androidWorkManagerLiveProviderCancelPreventsDelayedExecution",
  "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest#androidWorkManagerProviderRequiresExplicitCapability",
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = Object.fromEntries(
    Object.entries(FILES).map(([id, relative]) => [id, read(path.join(repoRoot, relative))]),
  );
  const registryReport = readJsonIfExists(path.join(repoRoot, FILES.phase10RegistryReport));
  const requestedLiveSummary = options.liveSummary || FILES.appLiveProviderSummary;
  const liveSummaryPath = path.resolve(repoRoot, requestedLiveSummary);
  const liveSummaryRelative = path.relative(repoRoot, liveSummaryPath);
  if (path.isAbsolute(liveSummaryRelative) || liveSummaryRelative === ".." ||
      liveSummaryRelative.startsWith(`..${path.sep}`)) {
    throw new Error("--live-summary must resolve inside the repository");
  }
  const liveSummarySource = fs.existsSync(liveSummaryPath)
    ? relativePath(repoRoot, liveSummaryPath)
    : null;
  const liveSummary = liveSummarySource ? readJsonIfExists(liveSummaryPath) : null;
  const registryChecks = REGISTRY_CHECKS.map(([id, fileId, token]) => ({
    id,
    file: FILES[fileId],
    token,
    present: files[fileId].includes(token),
  }));
  const registryStatus = decisionStatus(registryReport);
  const liveStatus = evaluateLiveSummary(liveSummary, repoRoot);
  liveStatus.source = liveSummarySource;
  const blockers = [];
  registryChecks
    .filter((entry) => !entry.present)
    .forEach((entry) => blockers.push("missing provider registry evidence: " + entry.id));
  if (!registryReport) {
    blockers.push("Phase 10 provider registry v2 report is missing");
  } else if (registryStatus !== "ready") {
    blockers.push("Phase 10 provider registry v2 report is not ready: " + registryStatus);
  }
  if (!liveSummary) {
    blockers.push("live-provider summary is missing");
  }
  liveStatus.integrityViolations.forEach((violation) => blockers.push("live-provider evidence violation: " + violation));
  liveStatus.policyViolations.forEach((violation) => blockers.push("live-provider policy violation: " + violation));
  liveStatus.missingProviders.forEach((provider) => blockers.push("live-provider summary missing provider: " + provider));
  if (liveSummary && liveSummary.fakeProvidersAllowed !== false) {
    blockers.push("live-provider gate must not allow fake providers");
  }
  if (liveSummary && liveSummary.networkExperimentalEnabled !== true) {
    blockers.push("live-provider gate must run with network experimental provider coverage enabled");
  }
  const report = {
    schema: "autojs6-node-final-provider-gate-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeFinalProviderGate",
    tasks: ["S11-34", "S11-35"],
    sources: Object.fromEntries(
      Object.entries(FILES).map(([id, relative]) => [id, relativePath(repoRoot, path.join(repoRoot, relative))]),
    ),
    registry: {
      reportAvailable: Boolean(registryReport),
      reportStatus: registryStatus,
      status: registryChecks.every((entry) => entry.present) && registryStatus === "ready" ? "ready" : "missing_evidence",
      checks: registryChecks,
    },
    liveProvider: liveStatus,
    blockers,
    decision: {
      status: blockers.length === 0 ? "ready" : "blocked",
      registryReady: registryChecks.every((entry) => entry.present) && registryStatus === "ready",
      liveProviderEvidenceReady: liveStatus.ready,
      fakeProviderOnlyPromoted: false,
      hostOnlyPromoted: false,
      safeNodeProfilePreserved: true,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "final-provider-gate", report, renderMarkdown);
  console.log("Final provider gate decision: " + report.decision.status);
  if (options.failOnBlocked && report.decision.status !== "ready") {
    process.exitCode = 1;
  }
}

function evaluateLiveSummary(summary, repoRoot) {
  const currentSourceIdentity = readCurrentSourceIdentity(repoRoot);
  if (!summary) {
    return {
      reportAvailable: false,
      ready: false,
      deviceProfile: "missing",
      networkExperimentalEnabled: false,
      fakeProvidersAllowed: null,
      expectedCases: 0,
      coveredProviders: [],
      missingProviders: EXPECTED_LIVE_PROVIDERS,
      policyViolations: [],
      integrityViolations: currentSourceIdentity.error ? [currentSourceIdentity.error] : [],
      currentSourceIdentity,
    };
  }
  const integrityViolations = [];
  const policyViolations = Array.isArray(summary.policyViolations) ? summary.policyViolations : [];
  if (!Array.isArray(summary.policyViolations)) {
    integrityViolations.push("live-provider summary policyViolations must be an array");
  }
  const totals = summary.totals || {};
  const coveredProviders = Array.isArray(totals.coveredProviders) ? totals.coveredProviders : [];
  const covered = new Set(coveredProviders);
  const missingProviders = EXPECTED_LIVE_PROVIDERS.filter((provider) => !covered.has(provider));
  const expectedCases = Number(totals.expectedCases || 0);
  const cases = Array.isArray(summary.cases) ? summary.cases : [];
  const caseIds = cases.map((entry) => `${entry.className || ""}#${entry.methodName || ""}`);
  const uniqueCaseIds = new Set(caseIds);
  const missingCases = EXPECTED_LIVE_CASES.filter((id) => !uniqueCaseIds.has(id));
  const unexpectedCases = [...uniqueCaseIds].filter((id) => !EXPECTED_LIVE_CASES.includes(id));

  if (summary.schemaVersion !== 3) {
    integrityViolations.push(`live-provider summary schemaVersion must be 3, got ${summary.schemaVersion ?? "missing"}`);
  }
  if (summary.task !== "S7-16 Live Provider Gate" || summary.gate !== "verifyNodeLiveProviderGate") {
    integrityViolations.push("live-provider summary task/gate identity is invalid");
  }
  if (!Number.isFinite(Date.parse(summary.generatedAt || ""))) {
    integrityViolations.push("live-provider summary generatedAt is missing or invalid");
  }
  if (!new Set(["sony", "full"]).has(summary.deviceProfile)) {
    integrityViolations.push(`live-provider summary requires sony/full deviceProfile, got ${summary.deviceProfile || "missing"}`);
  }
  if (!Array.isArray(summary.devices) || summary.devices.length !== 1 ||
      summary.devices.some((device) => typeof device !== "string" || !device.trim())) {
    integrityViolations.push("live-provider summary must name exactly one connected device");
  } else if (!isSafeDeviceIdentity(summary.devices[0])) {
    integrityViolations.push("live-provider summary contains an unsafe connected device identity");
  }
  if (summary.networkExperimentalRequired !== true) {
    integrityViolations.push("live-provider summary must record networkExperimentalRequired=true");
  }
  if (summary.skipReasonRequired !== true) {
    integrityViolations.push("live-provider summary must record skipReasonRequired=true");
  }
  if (uniqueCaseIds.size !== caseIds.length) {
    integrityViolations.push("live-provider summary contains duplicate expected test cases");
  }
  missingCases.forEach((id) => integrityViolations.push(`live-provider summary missing expected test case: ${id}`));
  unexpectedCases.forEach((id) => integrityViolations.push(`live-provider summary contains unexpected test case: ${id}`));
  cases.forEach((entry) => {
    const id = `${entry.className || ""}#${entry.methodName || ""}`;
    if (entry.status !== "passed" && entry.status !== "skipped") {
      integrityViolations.push(`live-provider case did not pass or take an allowed skip: ${id} (${entry.status || "missing"})`);
    }
    if (entry.status === "skipped" && (entry.skipAllowed !== true || !String(entry.message || "").trim())) {
      integrityViolations.push(`live-provider case skipped without an allowed, recorded reason: ${id}`);
    }
    if (entry.fakeProviderAllowed !== false) {
      integrityViolations.push(`live-provider case permits a fake provider: ${id}`);
    }
  });
  const skippedCases = cases.filter((entry) => entry.status === "skipped").length;
  if (!Number.isInteger(totals.tests) || totals.tests !== EXPECTED_LIVE_CASES.length) {
    integrityViolations.push(`live-provider totals.tests must equal ${EXPECTED_LIVE_CASES.length}, got ${totals.tests ?? "missing"}`);
  }
  if (!Number.isInteger(totals.failures) || totals.failures !== 0 || !Number.isInteger(totals.errors) || totals.errors !== 0) {
    integrityViolations.push(`live-provider totals must record zero failures/errors, got ${totals.failures ?? "missing"}/${totals.errors ?? "missing"}`);
  }
  if (!Number.isInteger(totals.expectedCases) || expectedCases !== EXPECTED_LIVE_CASES.length || cases.length !== EXPECTED_LIVE_CASES.length) {
    integrityViolations.push(
      `live-provider expected/case counts must equal ${EXPECTED_LIVE_CASES.length}, got ${expectedCases}/${cases.length}`,
    );
  }
  if (!Number.isInteger(totals.skipped) || totals.skipped !== skippedCases) {
    integrityViolations.push(`live-provider totals.skipped does not match case results: ${totals.skipped ?? "missing"}/${skippedCases}`);
  }

  const sourceIdentity = summary.sourceIdentity || {};
  const sourceBefore = sourceIdentity.before || {};
  const sourceAfter = sourceIdentity.after || {};
  if (sourceIdentity.stable !== true) {
    integrityViolations.push("live-provider source identity changed while evidence was collected");
  }
  for (const [label, identity] of [["before", sourceBefore], ["after", sourceAfter]]) {
    if (!/^(?:[0-9a-f]{40}|[0-9a-f]{64})$/i.test(String(identity.hostCommit || ""))) {
      integrityViolations.push(`live-provider ${label} hostCommit is missing or invalid`);
    }
    if (identity.hostTreeClean !== true) {
      integrityViolations.push(`live-provider ${label} host worktree was not clean`);
    }
    if (!Number.isFinite(Date.parse(identity.capturedAt || ""))) {
      integrityViolations.push(`live-provider ${label} capturedAt is missing or invalid`);
    }
  }
  if (sourceBefore.hostCommit !== sourceAfter.hostCommit) {
    integrityViolations.push("live-provider before/after host commits do not match");
  }
  const sourceBeforeAtMs = Date.parse(sourceBefore.capturedAt || "");
  const generatedAtMs = Date.parse(summary.generatedAt || "");
  const sourceAfterAtMs = Date.parse(sourceAfter.capturedAt || "");
  if (!Number.isFinite(sourceBeforeAtMs) || !Number.isFinite(generatedAtMs) || !Number.isFinite(sourceAfterAtMs) ||
      sourceBeforeAtMs > generatedAtMs || generatedAtMs > sourceAfterAtMs) {
    integrityViolations.push("live-provider evidence timestamps are not bracketed by source identity capture");
  }
  if (currentSourceIdentity.error) {
    integrityViolations.push(currentSourceIdentity.error);
  } else {
    if (!currentSourceIdentity.hostTreeClean) {
      integrityViolations.push("current host worktree is not clean for live-provider evidence verification");
    }
    if (sourceAfter.hostCommit !== currentSourceIdentity.hostCommit) {
      integrityViolations.push("live-provider host commit does not match current HEAD");
    }
  }

  const evidenceSnapshot = summary.evidenceSnapshot || {};
  const snapshotPath = String(evidenceSnapshot.path || "").replace(/\\/g, "/").replace(/\/$/, "");
  const snapshotRunKey = String(evidenceSnapshot.runKey || "");
  const snapshotInvocationId = String(evidenceSnapshot.invocationId || "");
  const sourceInvocationId = String(sourceIdentity.invocationId || "");
  if (evidenceSnapshot.immutable !== true) {
    integrityViolations.push("live-provider evidence snapshot must record immutable=true");
  }
  const expectedSnapshotRunKey = `${String(sourceBefore.hostCommit || "").slice(0, 12)}-${generatedAtMs}`;
  const snapshotMetadataValid = /^[0-9a-f]{12}-\d{13}$/i.test(snapshotRunKey) &&
    snapshotRunKey === expectedSnapshotRunKey &&
    snapshotPath === `${LIVE_PROVIDER_SNAPSHOT_ROOT}${snapshotRunKey}` &&
    /^\d{13}-[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(snapshotInvocationId) &&
    snapshotInvocationId === sourceInvocationId &&
    Number(snapshotInvocationId.slice(0, 13)) <= sourceBeforeAtMs;
  if (!snapshotMetadataValid) {
    integrityViolations.push("live-provider evidence snapshot path/runKey is invalid");
  }
  const snapshotRootAbsolute = snapshotMetadataValid
    ? path.resolve(repoRoot, snapshotPath)
    : path.resolve(repoRoot, ".invalid-live-provider-snapshot");
  const snapshotRoot = validateSnapshotRoot(repoRoot, snapshotRootAbsolute);
  integrityViolations.push(...snapshotRoot.violations);
  const safeSnapshotRootRealPath = snapshotRoot.valid
    ? snapshotRoot.realPath
    : path.resolve(repoRoot, ".invalid-live-provider-snapshot");
  if (snapshotRoot.valid) {
    integrityViolations.push(...validateSnapshotManifest(summary, repoRoot, safeSnapshotRootRealPath, snapshotPath));
  }
  const artifactTimeRange = { notBeforeMs: sourceBeforeAtMs, notAfterMs: generatedAtMs };

  const resultArtifacts = validateArtifactList(
    summary.resultXmlArtifacts,
    repoRoot,
    "result XML",
    safeSnapshotRootRealPath,
    artifactTimeRange,
  );
  integrityViolations.push(...resultArtifacts.violations);
  if (!Array.isArray(summary.resultXmlArtifacts) || summary.resultXmlArtifacts.length !== 1) {
    integrityViolations.push("live-provider resultXmlArtifacts must contain exactly one hashed JUnit XML artifact");
  }
  const legacyResultPaths = Array.isArray(summary.resultXml)
    ? summary.resultXml.map((entry) => String(entry).replace(/\\/g, "/")).sort()
    : [];
  const artifactResultPaths = resultArtifacts.paths.slice().sort();
  if (legacyResultPaths.length === 0 || JSON.stringify(legacyResultPaths) !== JSON.stringify(artifactResultPaths)) {
    integrityViolations.push("live-provider resultXml paths do not match hashed resultXmlArtifacts");
  }
  validateLiveProviderJUnit(summary, repoRoot, artifactResultPaths, integrityViolations);
  const apkArtifacts = validateArtifactList(
    summary.apkArtifacts,
    repoRoot,
    "APK",
    safeSnapshotRootRealPath,
    artifactTimeRange,
  );
  integrityViolations.push(...apkArtifacts.violations);
  const rawApkArtifacts = Array.isArray(summary.apkArtifacts) ? summary.apkArtifacts : [];
  const appApkArtifacts = rawApkArtifacts.filter((entry) => entry.kind === "app");
  const androidTestApkArtifacts = rawApkArtifacts.filter((entry) => entry.kind === "androidTest");
  if (appApkArtifacts.length === 0 || androidTestApkArtifacts.length === 0) {
    integrityViolations.push("live-provider APK evidence must include tested app and androidTest artifacts");
  }

  const utpSelectionArtifacts = validateArtifactList(
    summary.utpApkSelectionArtifacts,
    repoRoot,
    "UTP APK selection",
    safeSnapshotRootRealPath,
    artifactTimeRange,
  );
  integrityViolations.push(...utpSelectionArtifacts.violations);
  validateTestedApkBindings(
    summary,
    repoRoot,
    apkArtifacts.paths,
    utpSelectionArtifacts.paths,
    integrityViolations,
  );

  const snapshotRelativeFromRepo = path.relative(repoRoot, snapshotRootAbsolute);
  if (!snapshotMetadataValid || path.isAbsolute(snapshotRelativeFromRepo) || snapshotRelativeFromRepo.startsWith("..")) {
    integrityViolations.push("live-provider evidence snapshot resolves outside the repository");
  }

  return {
    reportAvailable: true,
    ready: policyViolations.length === 0 &&
      integrityViolations.length === 0 &&
      missingProviders.length === 0 &&
      summary.fakeProvidersAllowed === false &&
      summary.networkExperimentalEnabled === true,
    deviceProfile: summary.deviceProfile || "unknown",
    devices: Array.isArray(summary.devices) ? summary.devices : [],
    networkExperimentalEnabled: summary.networkExperimentalEnabled === true,
    fakeProvidersAllowed: summary.fakeProvidersAllowed,
    expectedCases,
    coveredProviders,
    missingProviders,
    policyViolations,
    integrityViolations,
    missingCases,
    unexpectedCases,
    sourceIdentity,
    currentSourceIdentity,
    evidenceSnapshot,
    resultXmlArtifactCount: resultArtifacts.paths.length,
    apkArtifactCount: apkArtifacts.paths.length,
    utpApkSelectionArtifactCount: utpSelectionArtifacts.paths.length,
  };
}

function validateSnapshotRoot(repoRoot, snapshotRootAbsolute) {
  const violations = [];
  let realPath = snapshotRootAbsolute;
  let valid = false;
  try {
    const rootStat = fs.lstatSync(snapshotRootAbsolute);
    if (!rootStat.isDirectory() || rootStat.isSymbolicLink()) {
      violations.push("live-provider evidence snapshot root must be a real directory");
    }
    realPath = fs.realpathSync(snapshotRootAbsolute);
    const repoRealPath = fs.realpathSync(repoRoot);
    const relative = path.relative(repoRealPath, realPath);
    if (!relative || path.isAbsolute(relative) || relative === ".." || relative.startsWith(`..${path.sep}`)) {
      violations.push("live-provider evidence snapshot real path escapes the repository");
    }
    valid = violations.length === 0;
  } catch (error) {
    violations.push(`live-provider evidence snapshot root is missing or unreadable: ${error.message}`);
  }
  return { realPath, valid, violations };
}

function validateSnapshotManifest(summary, repoRoot, snapshotRootRealPath, snapshotPath) {
  const violations = [];
  const evidenceSnapshot = summary && summary.evidenceSnapshot || {};
  const manifestPath = String(evidenceSnapshot.manifestPath || "").replace(/\\/g, "/");
  const archivedSummaryPath = String(evidenceSnapshot.summaryPath || "").replace(/\\/g, "/");
  const expectedManifestPath = `${snapshotPath}/manifest.json`;
  const expectedSummaryPath = `${snapshotPath}/summary.json`;
  const expectedManifestSha256 = String(evidenceSnapshot.manifestSha256 || "").toLowerCase();
  if (manifestPath !== expectedManifestPath) {
    violations.push("live-provider evidence manifest path does not match its snapshot root");
  }
  if (archivedSummaryPath !== expectedSummaryPath) {
    violations.push("live-provider archived summary path does not match its snapshot root");
  }
  if (!/^[0-9a-f]{64}$/.test(expectedManifestSha256)) {
    violations.push("live-provider evidence manifest sha256 is missing or invalid");
  }

  function readSnapshotJson(relative, label) {
    const absolute = path.resolve(repoRoot, relative);
    try {
      const fileLstat = fs.lstatSync(absolute);
      if (!fileLstat.isFile() || fileLstat.isSymbolicLink()) {
        violations.push(`live-provider ${label} must be a real file`);
        return null;
      }
      const realPath = fs.realpathSync(absolute);
      const snapshotRelative = path.relative(snapshotRootRealPath, realPath);
      if (!snapshotRelative || path.isAbsolute(snapshotRelative) || snapshotRelative === ".." ||
          snapshotRelative.startsWith(`..${path.sep}`)) {
        violations.push(`live-provider ${label} real path escapes its immutable evidence snapshot`);
        return null;
      }
      return {
        bytes: fs.readFileSync(realPath),
        json: JSON.parse(stripBom(fs.readFileSync(realPath, "utf8"))),
      };
    } catch (error) {
      violations.push(`live-provider ${label} is missing, unreadable, or invalid: ${error.message}`);
      return null;
    }
  }

  if (manifestPath !== expectedManifestPath || archivedSummaryPath !== expectedSummaryPath) {
    return violations;
  }
  const manifest = readSnapshotJson(manifestPath, "evidence manifest");
  const archivedSummary = readSnapshotJson(archivedSummaryPath, "archived summary");
  if (manifest) {
    const actualManifestSha256 = crypto.createHash("sha256").update(manifest.bytes).digest("hex");
    if (actualManifestSha256 !== expectedManifestSha256) {
      violations.push("live-provider evidence manifest hash mismatch");
    }
    const canonicalWithoutManifestHash = JSON.parse(JSON.stringify(summary));
    if (canonicalWithoutManifestHash.evidenceSnapshot) {
      delete canonicalWithoutManifestHash.evidenceSnapshot.manifestSha256;
    }
    if (stableJson(manifest.json) !== stableJson(canonicalWithoutManifestHash)) {
      violations.push("live-provider evidence manifest does not match the selected summary");
    }
  }
  if (archivedSummary && stableJson(archivedSummary.json) !== stableJson(summary)) {
    violations.push("live-provider archived summary does not match the selected summary");
  }
  return violations;
}

function stableJson(value) {
  if (Array.isArray(value)) {
    return `[${value.map(stableJson).join(",")}]`;
  }
  if (value && typeof value === "object") {
    return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${stableJson(value[key])}`).join(",")}}`;
  }
  return JSON.stringify(value);
}

function validateArtifactList(rawArtifacts, repoRoot, label, snapshotRootRealPath, timeRange) {
  const artifacts = Array.isArray(rawArtifacts) ? rawArtifacts : [];
  const violations = [];
  const paths = [];
  if (artifacts.length === 0) {
    violations.push(`live-provider ${label} artifact list is missing or empty`);
  }
  artifacts.forEach((artifact, index) => {
    const relative = String(artifact && artifact.path || "").replace(/\\/g, "/");
    const expectedSha256 = String(artifact && artifact.sha256 || "").toLowerCase();
    const absolute = path.resolve(repoRoot, relative);
    const repoRelative = path.relative(repoRoot, absolute);
    const normalizedRelative = path.posix.normalize(relative);
    const unsafeSegments = relative.split("/").some((segment) => segment === "." || segment === "..");
    const outsideRepo = !relative || path.isAbsolute(relative) || path.isAbsolute(repoRelative) ||
      repoRelative === ".." || repoRelative.startsWith(`..${path.sep}`);
    if (outsideRepo || unsafeSegments || normalizedRelative !== relative) {
      violations.push(`live-provider ${label} artifact ${index} has an unsafe path`);
      return;
    }
    const snapshotRelative = path.relative(snapshotRootRealPath, absolute);
    const outsideSnapshot = !snapshotRelative || path.isAbsolute(snapshotRelative) ||
      snapshotRelative === ".." || snapshotRelative.startsWith(`..${path.sep}`);
    if (outsideSnapshot) {
      violations.push(`live-provider ${label} artifact is outside its immutable evidence snapshot: ${relative}`);
      return;
    }
    const sourceLastModifiedAtMs = Date.parse(String(artifact && artifact.sourceLastModifiedAt || ""));
    if (!Number.isFinite(sourceLastModifiedAtMs) || !Number.isFinite(timeRange.notBeforeMs) ||
        !Number.isFinite(timeRange.notAfterMs) || sourceLastModifiedAtMs < timeRange.notBeforeMs ||
        sourceLastModifiedAtMs > timeRange.notAfterMs) {
      violations.push(`live-provider ${label} producer timestamp is outside the source-identity run bracket: ${relative}`);
      return;
    }
    if (!/^[0-9a-f]{64}$/.test(expectedSha256)) {
      violations.push(`live-provider ${label} artifact has an invalid sha256: ${relative}`);
      return;
    }
    if (!fs.existsSync(absolute)) {
      violations.push(`live-provider ${label} artifact is missing: ${relative}`);
      return;
    }
    const artifactLstat = fs.lstatSync(absolute);
    if (!artifactLstat.isFile() || artifactLstat.isSymbolicLink()) {
      violations.push(`live-provider ${label} artifact must be a real file: ${relative}`);
      return;
    }
    const artifactRealPath = fs.realpathSync(absolute);
    const artifactSnapshotRelative = path.relative(snapshotRootRealPath, artifactRealPath);
    if (!artifactSnapshotRelative || path.isAbsolute(artifactSnapshotRelative) ||
        artifactSnapshotRelative === ".." || artifactSnapshotRelative.startsWith(`..${path.sep}`)) {
      violations.push(`live-provider ${label} artifact real path escapes its immutable evidence snapshot: ${relative}`);
      return;
    }
    const actualSha256 = crypto.createHash("sha256").update(fs.readFileSync(absolute)).digest("hex");
    if (actualSha256 !== expectedSha256) {
      violations.push(`live-provider ${label} artifact hash mismatch: ${relative}`);
      return;
    }
    paths.push(relative);
  });
  if (new Set(paths).size !== paths.length) {
    violations.push(`live-provider ${label} artifact paths are duplicated`);
  }
  return { paths, violations };
}

function validateLiveProviderJUnit(summary, repoRoot, artifactResultPaths, violations) {
  if (artifactResultPaths.length !== 1) {
    violations.push(
      `live-provider JUnit validation requires exactly one hashed snapshot XML, got ${artifactResultPaths.length}`,
    );
    return;
  }

  const relative = artifactResultPaths[0];
  const absolute = path.resolve(repoRoot, relative);
  let xml;
  try {
    xml = stripBom(fs.readFileSync(absolute, "utf8"));
  } catch (error) {
    violations.push(`live-provider hashed JUnit XML is unreadable: ${relative}: ${error.message}`);
    return;
  }
  if (/<!DOCTYPE\b/i.test(xml) || /<!ENTITY\b/i.test(xml)) {
    violations.push(`live-provider hashed JUnit XML must not contain a DOCTYPE or entity declaration: ${relative}`);
  }

  const junitDevices = [...xml.matchAll(/<property\b([^>]*?)(?:\/?>)/gi)]
    .map((match) => match[1] || "")
    .filter((attributes) => xmlAttribute(attributes, "name") === "device")
    .map((attributes) => xmlAttribute(attributes, "value"));
  const expectedDevice = Array.isArray(summary.devices) && summary.devices.length === 1
    ? String(summary.devices[0])
    : "";
  if (junitDevices.length === 0 ||
      junitDevices.some((device) => device !== expectedDevice || !isSafeDeviceIdentity(device))) {
    violations.push(
      `live-provider JUnit device properties must all match the single summary device: ${[...new Set(junitDevices)].join(",") || "missing"}/${expectedDevice || "missing"}`,
    );
  }

  const rootMatch = xml.match(
    /^\s*(?:<\?xml\b[\s\S]*?\?>\s*)?(?:(?:<!--[\s\S]*?-->)\s*)*<(testsuites|testsuite)\b([^>]*)>/i,
  );
  if (!rootMatch) {
    violations.push(`live-provider hashed JUnit XML has no testsuites/testsuite document root: ${relative}`);
    return;
  }

  const totalNames = ["tests", "failures", "errors", "skipped"];
  const rootTotals = {};
  totalNames.forEach((name) => {
    const raw = xmlAttribute(rootMatch[2], name);
    if (!/^\d+$/.test(raw)) {
      violations.push(`live-provider JUnit root ${name} must be an explicit non-negative integer: ${raw || "missing"}`);
      rootTotals[name] = null;
    } else {
      rootTotals[name] = Number(raw);
    }
  });

  const junitCases = [];
  const testcasePattern = /<testcase\b([^>]*?)\/>|<testcase\b([^>]*)>([\s\S]*?)<\/testcase\s*>/gi;
  for (const match of xml.matchAll(testcasePattern)) {
    const attributes = match[1] || match[2] || "";
    const body = match[3] || "";
    const className = xmlAttribute(attributes, "classname");
    const methodName = xmlAttribute(attributes, "name");
    const statusMarkers = [
      ["failed", /<failure\b/i.test(body)],
      ["error", /<error\b/i.test(body)],
      ["skipped", /<skipped\b/i.test(body)],
    ].filter((entry) => entry[1]);
    if (statusMarkers.length > 1) {
      violations.push(
        `live-provider JUnit testcase has conflicting result elements: ${className || "missing"}#${methodName || "missing"}`,
      );
    }
    const status = statusMarkers.length === 0 ? "passed" : statusMarkers[0][0];
    junitCases.push({
      id: `${className}#${methodName}`,
      className,
      methodName,
      status,
    });
  }

  const parsedTotals = {
    tests: junitCases.length,
    failures: junitCases.filter((entry) => entry.status === "failed").length,
    errors: junitCases.filter((entry) => entry.status === "error").length,
    skipped: junitCases.filter((entry) => entry.status === "skipped").length,
  };
  const summaryTotals = summary && summary.totals && typeof summary.totals === "object"
    ? summary.totals
    : {};
  totalNames.forEach((name) => {
    if (rootTotals[name] !== null && rootTotals[name] !== parsedTotals[name]) {
      violations.push(
        `live-provider JUnit root ${name} does not match parsed testcases: ${rootTotals[name]}/${parsedTotals[name]}`,
      );
    }
    if (!Number.isInteger(summaryTotals[name]) || rootTotals[name] !== summaryTotals[name]) {
      violations.push(
        `live-provider JUnit root ${name} does not match summary totals: ${rootTotals[name] ?? "invalid"}/${summaryTotals[name] ?? "missing"}`,
      );
    }
  });

  const summaryCases = Array.isArray(summary.cases) ? summary.cases : [];
  const summaryCaseEntries = summaryCases.map((entry) => ({
    id: `${entry && entry.className || ""}#${entry && entry.methodName || ""}`,
    status: String(entry && entry.status || ""),
  }));
  const expectedSet = new Set(EXPECTED_LIVE_CASES);
  const junitById = groupCasesById(junitCases);
  const summaryById = groupCasesById(summaryCaseEntries);

  if (junitCases.length !== EXPECTED_LIVE_CASES.length) {
    violations.push(
      `live-provider JUnit testcase count must equal ${EXPECTED_LIVE_CASES.length}, got ${junitCases.length}`,
    );
  }
  if (summaryCaseEntries.length !== EXPECTED_LIVE_CASES.length) {
    violations.push(
      `live-provider summary case count must equal ${EXPECTED_LIVE_CASES.length} for JUnit binding, got ${summaryCaseEntries.length}`,
    );
  }
  [...junitById.keys()]
    .filter((id) => !expectedSet.has(id))
    .forEach((id) => violations.push(`live-provider JUnit contains unexpected testcase: ${id}`));
  [...summaryById.keys()]
    .filter((id) => !expectedSet.has(id))
    .forEach((id) => violations.push(`live-provider summary contains testcase outside JUnit contract: ${id}`));

  EXPECTED_LIVE_CASES.forEach((id) => {
    const junitMatches = junitById.get(id) || [];
    const summaryMatches = summaryById.get(id) || [];
    if (junitMatches.length !== 1) {
      violations.push(`live-provider JUnit testcase must occur exactly once: ${id} (${junitMatches.length})`);
    }
    if (summaryMatches.length !== 1) {
      violations.push(`live-provider summary testcase must occur exactly once for JUnit binding: ${id} (${summaryMatches.length})`);
    }
    if (junitMatches.length === 1 && summaryMatches.length === 1 &&
        junitMatches[0].status !== summaryMatches[0].status) {
      violations.push(
        `live-provider JUnit testcase status does not match summary: ${id} (${junitMatches[0].status}/${summaryMatches[0].status})`,
      );
    }
  });
}

function groupCasesById(cases) {
  const grouped = new Map();
  cases.forEach((entry) => {
    const matches = grouped.get(entry.id) || [];
    matches.push(entry);
    grouped.set(entry.id, matches);
  });
  return grouped;
}

function xmlAttribute(attributes, name) {
  const match = String(attributes).match(
    new RegExp(`\\b${escapeRegex(name)}\\s*=\\s*(["'])([\\s\\S]*?)\\1`, "i"),
  );
  return match ? decodeXml(match[2]) : "";
}

function decodeXml(value) {
  return String(value)
    .replace(/&#x([0-9a-f]+);/gi, (_, digits) => decodeXmlCodePoint(digits, 16))
    .replace(/&#(\d+);/g, (_, digits) => decodeXmlCodePoint(digits, 10))
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&amp;/g, "&");
}

function decodeXmlCodePoint(digits, radix) {
  const value = Number.parseInt(digits, radix);
  try {
    return Number.isInteger(value) ? String.fromCodePoint(value) : "";
  } catch (_) {
    return "";
  }
}

function escapeRegex(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function isSafeDeviceIdentity(value) {
  const device = String(value || "");
  return Boolean(device.trim()) && device !== "." && device !== ".." &&
    !device.includes("/") && !device.includes("\\") && !path.isAbsolute(device);
}

function validateTestedApkBindings(summary, repoRoot, apkArtifactPaths, selectionArtifactPaths, violations) {
  const devices = Array.isArray(summary.devices) ? summary.devices.map(String) : [];
  const rawApkArtifacts = Array.isArray(summary.apkArtifacts) ? summary.apkArtifacts : [];
  const rawSelections = Array.isArray(summary.utpApkSelectionArtifacts) ? summary.utpApkSelectionArtifacts : [];
  const rawBindings = Array.isArray(summary.deviceApkBindings) ? summary.deviceApkBindings : [];
  const apkPathSet = new Set(apkArtifactPaths);
  const selectionPathSet = new Set(selectionArtifactPaths);
  const apkByPath = new Map();
  rawApkArtifacts.forEach((artifact) => {
    const artifactPath = String(artifact && artifact.path || "").replace(/\\/g, "/");
    const sourcePath = String(artifact && artifact.sourcePath || "").replace(/\\/g, "/");
    if (!new Set(["app", "androidTest"]).has(artifact && artifact.kind)) {
      violations.push(`live-provider APK artifact has invalid kind: ${artifactPath || "missing"}`);
    }
    const expectedSourcePrefix = artifact && artifact.kind === "app"
      ? "app/build/outputs/apk/app/debug/"
      : "app/build/outputs/apk/androidTest/app/debug/";
    if (!sourcePath.startsWith(expectedSourcePrefix) || sourcePath.includes("..")) {
      violations.push(`live-provider APK artifact has invalid producer sourcePath: ${sourcePath || "missing"}`);
    }
    apkByPath.set(artifactPath, artifact);
  });

  if (new Set(devices).size !== devices.length) {
    violations.push("live-provider summary contains duplicate device identities");
  }
  if (rawSelections.length !== devices.length * 2) {
    violations.push(`live-provider UTP APK selection count must equal two per device, got ${rawSelections.length}/${devices.length * 2}`);
  }
  const selectionByDeviceKind = new Map();
  const referencedApkPaths = new Set();
  rawSelections.forEach((selection) => {
    const device = String(selection && selection.device || "");
    const kind = String(selection && selection.kind || "");
    const selectionPath = String(selection && selection.path || "").replace(/\\/g, "/");
    const sourcePath = String(selection && selection.sourcePath || "").replace(/\\/g, "/");
    const inspectedApkSourcePath = String(selection && selection.inspectedApkSourcePath || "").replace(/\\/g, "/");
    const apkArtifactPath = String(selection && selection.apkArtifactPath || "").replace(/\\/g, "/");
    const key = `${device}:${kind}`;
    if (!devices.includes(device) || !new Set(["app", "androidTest"]).has(kind)) {
      violations.push(`live-provider UTP APK selection has invalid device/kind: ${key}`);
    }
    if (selectionByDeviceKind.has(key)) {
      violations.push(`live-provider UTP APK selection is duplicated: ${key}`);
    }
    selectionByDeviceKind.set(key, selection);
    if (!selectionPathSet.has(selectionPath)) {
      violations.push(`live-provider UTP APK selection path is not a hashed snapshot artifact: ${selectionPath}`);
    }
    const expectedSelectionSourcePrefix =
      `app/build/outputs/androidTest-results/connected/debug/flavors/app/${device}/`;
    if (!isSafeDeviceIdentity(device) || !sourcePath.startsWith(expectedSelectionSourcePrefix) || sourcePath.includes("..")) {
      violations.push(`live-provider UTP APK selection has invalid producer sourcePath: ${sourcePath || "missing"}`);
    }
    const apkArtifact = apkByPath.get(apkArtifactPath);
    if (!apkPathSet.has(apkArtifactPath) || !apkArtifact || apkArtifact.kind !== kind) {
      violations.push(`live-provider UTP APK selection does not reference a matching hashed APK: ${key}`);
    } else if (String(apkArtifact.sourcePath || "").replace(/\\/g, "/") !== inspectedApkSourcePath) {
      violations.push(`live-provider UTP APK selection source does not match its hashed APK: ${key}`);
    }
    referencedApkPaths.add(apkArtifactPath);
    if (selectionPathSet.has(selectionPath)) {
      const inspectedPath = readUtpInspectedApkPath(path.resolve(repoRoot, selectionPath));
      const normalizedInspectedPath = inspectedPath.replace(/\\/g, "/");
      if (!normalizedInspectedPath ||
          (normalizedInspectedPath !== inspectedApkSourcePath && !normalizedInspectedPath.endsWith(`/${inspectedApkSourcePath}`))) {
        violations.push(`live-provider UTP aapt trace does not bind the recorded APK source: ${key}`);
      }
    }
  });
  if (referencedApkPaths.size !== apkPathSet.size || [...apkPathSet].some((entry) => !referencedApkPaths.has(entry))) {
    violations.push("live-provider APK snapshot contains artifacts not bound by UTP device selection evidence");
  }

  const bindingByDevice = new Map();
  rawBindings.forEach((binding) => {
    const device = String(binding && binding.device || "");
    if (bindingByDevice.has(device)) {
      violations.push(`live-provider device APK binding is duplicated: ${device}`);
    }
    bindingByDevice.set(device, binding);
  });
  if (rawBindings.length !== devices.length || devices.some((device) => !bindingByDevice.has(device))) {
    violations.push("live-provider device APK bindings do not match the connected device set");
  }
  devices.forEach((device) => {
    const binding = bindingByDevice.get(device) || {};
    for (const [kind, artifactField, inspectionField] of [
      ["app", "appArtifactPath", "appInspectionArtifactPath"],
      ["androidTest", "androidTestArtifactPath", "androidTestInspectionArtifactPath"],
    ]) {
      const selection = selectionByDeviceKind.get(`${device}:${kind}`) || {};
      if (String(binding[artifactField] || "").replace(/\\/g, "/") !==
          String(selection.apkArtifactPath || "").replace(/\\/g, "/") ||
          String(binding[inspectionField] || "").replace(/\\/g, "/") !==
          String(selection.path || "").replace(/\\/g, "/")) {
        violations.push(`live-provider device APK binding does not match UTP selection: ${device}:${kind}`);
      }
    }
  });
}

function readUtpInspectedApkPath(logPath) {
  if (!fs.existsSync(logPath) || !fs.statSync(logPath).isFile()) return "";
  const marker = " dump badging ";
  const command = fs.readFileSync(logPath, "utf8")
    .split(/\r?\n/)
    .find((line) => line.startsWith("EXECUTING:") && line.includes(marker));
  if (!command) return "";
  const raw = command.slice(command.indexOf(marker) + marker.length).trim();
  if (raw.length >= 2 && ((raw.startsWith('"') && raw.endsWith('"')) || (raw.startsWith("'") && raw.endsWith("'")))) {
    return raw.slice(1, -1);
  }
  return raw;
}

function readCurrentSourceIdentity(repoRoot) {
  try {
    const hostCommit = childProcess.execFileSync(
      "git",
      ["-C", repoRoot, "rev-parse", "HEAD"],
      { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] },
    ).trim();
    const worktreeStatus = childProcess.execFileSync(
      "git",
      ["-C", repoRoot, "status", "--porcelain=v1", "--untracked-files=all"],
      { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] },
    ).trim();
    return {
      hostCommit,
      hostTreeClean: worktreeStatus.length === 0,
      error: null,
    };
  } catch (error) {
    return {
      hostCommit: null,
      hostTreeClean: false,
      error: `cannot resolve current host source identity: ${error.message}`,
    };
  }
}

function decisionStatus(json) {
  if (!json) return "missing";
  if (json.decision && json.decision.status) return json.decision.status;
  return json.status || "unknown";
}

function renderMarkdown(report) {
  const lines = [
    "# Final Provider Gate",
    "",
    `- Decision: ${report.decision.status}`,
    `- Registry ready: ${report.decision.registryReady}`,
    `- Live provider evidence ready: ${report.decision.liveProviderEvidenceReady}`,
    `- Live provider summary: ${report.liveProvider.reportAvailable}`,
    `- Fake-provider-only promoted: ${report.decision.fakeProviderOnlyPromoted}`,
    "",
    "## Registry Checks",
    "",
    "| Check | Present | File |",
    "| --- | --- | --- |",
  ];
  report.registry.checks.forEach((entry) => {
    lines.push(`| ${entry.id} | ${entry.present} | ${entry.file} |`);
  });
  lines.push(
    "",
    "## Live Provider Summary",
    "",
    `- Source: ${report.liveProvider.source || "missing"}`,
    `- Device profile: ${report.liveProvider.deviceProfile}`,
    `- Network experimental enabled: ${report.liveProvider.networkExperimentalEnabled}`,
    `- Fake providers allowed: ${report.liveProvider.fakeProvidersAllowed}`,
    `- Expected cases: ${report.liveProvider.expectedCases}`,
    `- Current host commit: ${report.liveProvider.currentSourceIdentity?.hostCommit || "missing"}`,
    `- Current host tree clean: ${report.liveProvider.currentSourceIdentity?.hostTreeClean === true}`,
    `- Result XML artifacts: ${report.liveProvider.resultXmlArtifactCount || 0}`,
    `- APK artifacts: ${report.liveProvider.apkArtifactCount || 0}`,
    `- Covered providers: ${report.liveProvider.coveredProviders.join(", ") || "None"}`,
    `- Missing providers: ${report.liveProvider.missingProviders.join(", ") || "None"}`,
    "",
    "## Blockers",
    "",
  );
  if (report.blockers.length === 0) {
    lines.push("None.");
  } else {
    report.blockers.forEach((blocker) => lines.push(`- ${blocker}`));
  }
  return lines.join("\n") + "\n";
}

function read(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "";
}

function readJsonIfExists(file) {
  if (!fs.existsSync(file)) {
    return null;
  }
  try {
    return JSON.parse(stripBom(fs.readFileSync(file, "utf8")));
  } catch (_) {
    return null;
  }
}

function stripBom(value) {
  return value.replace(/^\uFEFF/, "");
}

function relativePath(root, file) {
  return path.relative(root, file).replace(/\\/g, "/");
}

function writeReport(reportDir, basename, json, markdownRenderer) {
  fs.writeFileSync(path.join(reportDir, `${basename}.json`), JSON.stringify(json, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${basename}.md`), markdownRenderer(json));
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-blocked"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-final-provider-gate.js [--repo-root dir] [--report-dir dir] [--live-summary file] [--fail-on-blocked]");
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
