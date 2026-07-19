#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const {
  DEVICE_REQUIREMENTS,
  DEVICE_SCHEMA,
  LIFECYCLE_MODEL,
  REQUIRED_DEVICE_SELECTORS,
  REQUIRED_REGRESSIONS,
  apkSignerCertificateSha256Digests,
  currentIdentity,
  evaluateDeviceReport,
} = require("./verify-production-evidence.js");

const RUNNER_SCHEMA = "autojs6-node-r6-device-evidence-runner-v1";
const PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.nodejs";
const HOST_PACKAGE = "org.autojs.autojs6";
const ANDROID_TEST_PACKAGE = "org.autojs.autojs6.test";
const RELEASE_FLAVOR = "nodePlugin";
const BACKEND = "plugin";
const RUNTIME_SLOT = "node24_5";
const NODE_VERSION = "24.5.0";
const R5_WARM_SAMPLES = 30;
const SOAK_KIND = "smoke";
const SOAK_SMOKE_DURATION_MS = 30_000;
const SOAK_SMOKE_SAMPLE_INTERVAL_MS = 2_000;
const SOAK_SMOKE_CYCLE_DELAY_MS = 500;
const SOAK_SMOKE_RECOVERY_EVERY_CYCLES = 2;
const GRADLE_TASK = ":app:verifyNodeR6DeviceEvidenceGate";
const XML_RESULT_ROOT = "app/build/outputs/androidTest-results";
const FRESHNESS_TOLERANCE_MS = 10_000;
const DEVICE_SOAK_REPORT_DIR = "/sdcard/Download/AutoJs6/nodejs/r6-soak";
const SOAK_REPORT_SCHEMA = "autojs6-node-r6-persistent-plugin-soak-v1";
const PACKAGED_TEMPLATE_TASK = ":app:packageInrtDebug";
const PACKAGED_TEMPLATE_PACKAGE = "org.autojs.autojs6.inrt";
const PACKAGED_TEMPLATE_DEVICE_PATH_PREFIX = "/data/local/tmp/autojs6-r6-packaged-template-";
const PACKAGED_TEMPLATE_OVERRIDE_ARGUMENT = "autojs.embeddedNode.packagedSmokeTemplateOverride";
const PACKAGED_TEMPLATE_EXACT_PATH_ARGUMENT = "autojs.embeddedNode.packagedSmokeTemplateExactPath";
const PACKAGED_TEMPLATE_TARGET_ABI_ARGUMENT = "autojs.embeddedNode.packagedSmokeTargetAbi";
const R5_PLUGIN_PREFLIGHT_SELECTOR =
  "org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_01_livePluginPreflightNegotiatesContractAndReportsTrustIdentity";
const R6_PLUGIN_SOAK_SELECTOR =
  "org.autojs.autojs.engine.NodeR6PersistentPluginSoakInstrumentationTest#persistentRequiredPluginSoakProducesR6Evidence";
const AUTHORIZED_PLUGIN_STATES = new Set(["official", "trusted", "user_granted"]);
const SHA256_PATTERN = /^[0-9a-f]{64}$/i;

const TARGET_PROFILES = Object.freeze({
  "sony-arm64": "sony",
  "sony-armeabi-v7a": "sony",
  "second-oem-arm64": "full",
  "emulator-x86_64": "full",
});

const SELECTOR_DERIVATION = Object.freeze({
  surfaces: {
    mainApp: REQUIRED_DEVICE_SELECTORS.filter((selector) =>
      !selector.startsWith("org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#")),
    packagedApk: REQUIRED_DEVICE_SELECTORS.filter((selector) =>
      selector.startsWith("org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#")),
  },
  regressions: {
    rhinoNodeAlternating: [
      "org.autojs.autojs.engine.NodeR6EngineSwitchInstrumentationTest#r6_01_requiredPluginNodeAndRhinoAlternateAcrossMultipleRounds",
    ],
    backendSwitch: [
      "org.autojs.autojs.engine.NodeR6EngineSwitchInstrumentationTest#r6_02_pluginRhinoCompatibilityPluginSwitchExecutesAndRecovers",
    ],
    persistentRuntimeIsolation: [
      "org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_02_candidateAndBinderCachesReusePersistentRuntimeWithFreshExecutionState",
      "org.autojs.autojs.engine.NodeR6PersistentPluginSoakInstrumentationTest#persistentRequiredPluginSoakProducesR6Evidence",
    ],
    pluginBinderDeathRecovery: [
      "org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_07_binderDeathReconnectsAtomicallyAndPreservesCrashAttribution",
    ],
    packagedLifecycle: [
      "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunFullCompatibilityEmbeddedNodePackagedApk",
    ],
    crashRecovery: [
      "org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_07_binderDeathReconnectsAtomicallyAndPreservesCrashAttribution",
      "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunCrashEvidenceEmbeddedNodePackagedApk",
    ],
    memoryGrowth: [
      "org.autojs.autojs.engine.NodeR6PersistentPluginSoakInstrumentationTest#persistentRequiredPluginSoakProducesR6Evidence",
    ],
    permanentDenials: [
      "org.autojs.autojs.engine.NodeR6RequiredPluginSecurityInstrumentationTest#requiredPluginPreservesPermanentSafeNodeDenials",
    ],
  },
  lifecycle: {
    persistentProcessRuntime: [
      "org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_02_candidateAndBinderCachesReusePersistentRuntimeWithFreshExecutionState",
      "org.autojs.autojs.engine.NodeR6PersistentPluginSoakInstrumentationTest#persistentRequiredPluginSoakProducesR6Evidence",
    ],
    processRuntimeInitCount: [
      "org.autojs.autojs.engine.NodeR6PersistentPluginSoakInstrumentationTest#persistentRequiredPluginSoakProducesR6Evidence",
    ],
    runtimeGenerationStable: [
      "org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_02_candidateAndBinderCachesReusePersistentRuntimeWithFreshExecutionState",
      "org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_08_runtimeWarmSamplesMeetR2SloAndSeparateDiscoveryAndBindOverhead",
      "org.autojs.autojs.engine.NodeR6PersistentPluginSoakInstrumentationTest#persistentRequiredPluginSoakProducesR6Evidence",
    ],
    executionSequenceMonotonic: [
      "org.autojs.autojs.engine.NodeR6PersistentPluginSoakInstrumentationTest#persistentRequiredPluginSoakProducesR6Evidence",
    ],
    freshExecutionState: [
      "org.autojs.autojs.engine.NodeR6EngineSwitchInstrumentationTest#r6_01_requiredPluginNodeAndRhinoAlternateAcrossMultipleRounds",
      "org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_02_candidateAndBinderCachesReusePersistentRuntimeWithFreshExecutionState",
      "org.autojs.autojs.engine.NodeR6PersistentPluginSoakInstrumentationTest#persistentRequiredPluginSoakProducesR6Evidence",
    ],
    recoveryAfterProcessDeath: [
      "org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_07_binderDeathReconnectsAtomicallyAndPreservesCrashAttribution",
      "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunCrashEvidenceEmbeddedNodePackagedApk",
    ],
  },
  resources: {
    activeBridgeResources: [
      "org.autojs.autojs.engine.NodeR6PersistentPluginSoakInstrumentationTest#persistentRequiredPluginSoakProducesR6Evidence",
    ],
    orphanProcesses: [
      "org.autojs.autojs.engine.NodeR6PersistentPluginSoakInstrumentationTest#persistentRequiredPluginSoakProducesR6Evidence",
    ],
    unrecoverableSlots: [
      "org.autojs.autojs.engine.NodeR6PersistentPluginSoakInstrumentationTest#persistentRequiredPluginSoakProducesR6Evidence",
    ],
  },
});

if (require.main === module) {
  main().catch((error) => {
    console.error(error && error.stack ? error.stack : String(error));
    process.exitCode = 1;
  });
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }

  const requirement = DEVICE_REQUIREMENTS.find((entry) => entry.id === options.target);
  if (!requirement) {
    throw new Error(`--target must be one of: ${DEVICE_REQUIREMENTS.map((entry) => entry.id).join(", ")}.`);
  }
  if (!options.serial || !String(options.serial).trim()) {
    throw new Error("An explicit --serial is required; ANDROID_SERIAL alone is not accepted for production evidence.");
  }

  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const pluginRoot = resolvePluginRoot(repoRoot, options.pluginRoot);
  const serial = String(options.serial).trim();
  const startedAtEpochMs = Date.now();
  const runToken = crypto.randomBytes(32).toString("hex");
  const packagedTemplateDevicePath = packagedTemplatePathForRunToken(runToken);
  const runRoot = path.resolve(
    options.runDir || path.join(
      repoRoot,
      "build/reports/nodejs/r6-device-runs",
      `${requirement.id}-${timestampForPath(new Date(startedAtEpochMs))}-${runToken.slice(-8)}`,
    ),
  );
  if (!isInsideOrSame(repoRoot, runRoot)) {
    throw new Error(`--run-dir must stay inside the repository so verifier-bound artifact paths remain stable: ${runRoot}`);
  }
  prepareRunRoot(runRoot);
  const artifactDir = path.join(runRoot, "artifacts");
  fs.mkdirSync(artifactDir, { recursive: true });
  const logFile = path.join(runRoot, "run.log");
  const evidenceRoot = path.join(repoRoot, "build/reports/nodejs/r6-evidence");
  const profile = TARGET_PROFILES[requirement.id];
  const summary = {
    schema: RUNNER_SCHEMA,
    generatedAt: new Date().toISOString(),
    mode: "production",
    target: requirement.id,
    requirement,
    serial,
    runToken,
    profile,
    runDir: relativePath(repoRoot, runRoot),
    contract: {
      releaseFlavor: RELEASE_FLAVOR,
      backend: BACKEND,
      pluginRequired: true,
      runtimeSlot: RUNTIME_SLOT,
      nodeVersion: NODE_VERSION,
      lifecycleModel: LIFECYCLE_MODEL,
      r5WarmSamples: R5_WARM_SAMPLES,
      soakKind: SOAK_KIND,
      soakSmokeDurationMs: SOAK_SMOKE_DURATION_MS,
      soakSmokeSampleIntervalMs: SOAK_SMOKE_SAMPLE_INTERVAL_MS,
      soakSmokeCycleDelayMs: SOAK_SMOKE_CYCLE_DELAY_MS,
      soakSmokeRecoveryEveryCycles: SOAK_SMOKE_RECOVERY_EVERY_CYCLES,
      selectors: REQUIRED_DEVICE_SELECTORS,
      archiveRequested: !options.noArchive,
    },
    sourceIdentity: { before: null, after: null },
    device: null,
    plugin: null,
    packagedTemplate: null,
    host: null,
    commands: [],
    junit: null,
    artifacts: null,
    candidate: null,
    archive: null,
    decision: { status: "running", validated: false, archived: false, failures: [] },
  };
  writeSummary(runRoot, summary);

  const context = { repoRoot, pluginRoot, runRoot, artifactDir, logFile, summary };
  logEvent(logFile, `Starting R6 device evidence run ${runToken}`);
  console.log(`R6 production device evidence: ${requirement.id}`);
  console.log(`- serial: ${serial}`);
  console.log(`- runtime ABI: ${requirement.runtimeProcessAbi}`);
  console.log(`- run directory: ${runRoot}`);

  let packagedTemplateCleanupRequired = false;
  try {
    if (!fs.existsSync(pluginRoot)) throw new Error(`Runtime plugin project is missing: ${pluginRoot}`);
    const identityBefore = currentIdentity(repoRoot, pluginRoot);
    summary.sourceIdentity.before = identityBefore;
    assertCleanIdentity(identityBefore, "before the production run");
    writeSummary(runRoot, summary);

    const device = await readAndValidateDevice(serial, requirement, context);
    summary.device = device;
    writeSummary(runRoot, summary);

    let explicitPluginApk = null;
    if (options.pluginApk) {
      explicitPluginApk = path.resolve(options.pluginApk);
      if (!fs.existsSync(explicitPluginApk) || !fs.statSync(explicitPluginApk).isFile()) {
        throw new Error(`The explicit plugin APK does not exist: ${explicitPluginApk}`);
      }
    }
    const pluginBuild = await runGradle({
      root: pluginRoot,
      // The sibling version plugin mutates version.properties only when assembleDebug is in
      // the graph. packageDebug produces the signed split APKs without that post-assemble hook.
      args: ["--console=plain", "--rerun-tasks", ":app:packageDebug"],
      timeoutMs: boundedNumber(options.pluginBuildTimeoutMs, 7_200_000, 300_000, 14_400_000, "plugin-build-timeout-ms"),
      context,
      label: "plugin-build",
    });
    requireSuccessfulCommand(pluginBuild, "Runtime plugin build");
    assertSameCleanIdentity(identityBefore, currentIdentity(repoRoot, pluginRoot), "after the runtime plugin build");
    const pluginApk = selectAbiApk(
      path.join(pluginRoot, "app/build/outputs/apk/debug"),
      requirement.runtimeProcessAbi,
      "runtime plugin",
    );
    assertFreshArtifact(pluginApk, pluginBuild.startedAt, "Runtime plugin APK");
    if (explicitPluginApk && sha256(explicitPluginApk) !== sha256(pluginApk)) {
      throw new Error("The explicit plugin APK does not match the fresh APK built from the current clean plugin commit.");
    }

    const pluginMetadata = inspectApk(pluginApk, repoRoot, context);
    const requestedPluginSignature = inspectApkSigner(
      pluginApk,
      repoRoot,
      context,
      "requested-plugin-signature",
    );
    validatePluginApk(pluginMetadata, pluginApk, requirement.runtimeProcessAbi);
    const requestedPluginCopy = copyArtifact(pluginApk, artifactDir, "requested-runtime-plugin.apk");
    const pluginSourceArtifact = artifactRecord(requestedPluginCopy, repoRoot);
    const install = await runCommand("adb", ["-s", serial, "install", "-r", pluginApk], {
      cwd: repoRoot,
      env: { ...process.env, ANDROID_SERIAL: serial },
      timeoutMs: 600_000,
      context,
      label: "plugin-install",
    });
    requireSuccessfulCommand(install, "Runtime plugin installation");

    const installedBefore = await inspectInstalledPlugin(serial, pluginMetadata.packageName, context);
    if (installedBefore.primaryCpuAbi !== requirement.runtimeProcessAbi) {
      throw new Error(
        `Installed plugin primaryCpuAbi must be ${requirement.runtimeProcessAbi}, got ${installedBefore.primaryCpuAbi || "missing"}.`,
      );
    }
    summary.plugin = {
      packageName: pluginMetadata.packageName,
      signerSha256: requestedPluginSignature.signerSha256,
      requestedApk: pluginSourceArtifact,
      declaredNativeAbis: pluginMetadata.nativeAbis,
      requestedSignature: requestedPluginSignature,
      installedBefore,
    };
    writeSummary(runRoot, summary);

    const packagedTemplateBuild = await runGradle({
      root: repoRoot,
      args: buildPackagedTemplateGradleArgs(),
      timeoutMs: boundedNumber(options.gradleTimeoutMs, 14_400_000, 900_000, 43_200_000, "gradle-timeout-ms"),
      context,
      label: "packaged-template-build",
    });
    requireSuccessfulCommand(packagedTemplateBuild, "Packaged smoke template build");
    assertSameCleanIdentity(
      identityBefore,
      currentIdentity(repoRoot, pluginRoot),
      "after the packaged smoke template build",
    );
    const packagedTemplateApk = selectAbiApk(
      path.join(repoRoot, "app/build/outputs/apk/inrt/debug"),
      requirement.runtimeProcessAbi,
      "packaged smoke template",
    );
    assertFreshArtifact(packagedTemplateApk, packagedTemplateBuild.startedAt, "Packaged smoke template APK");
    const packagedTemplateMetadata = inspectApk(packagedTemplateApk, repoRoot, context);
    const packagedTemplateSignature = inspectApkSigner(
      packagedTemplateApk,
      repoRoot,
      context,
      "packaged-template-signature",
    );
    validatePackagedTemplateApk(
      packagedTemplateMetadata,
      packagedTemplateApk,
      requirement.runtimeProcessAbi,
    );
    const packagedTemplateCopy = copyArtifact(
      packagedTemplateApk,
      artifactDir,
      "packaged-smoke-template.apk",
    );
    const packagedTemplateArtifact = artifactRecord(packagedTemplateCopy, repoRoot);
    if (await deviceFileExists(
      serial,
      packagedTemplateDevicePath,
      context,
      "packaged-template-device-preexisting-probe",
    )) {
      throw new Error(`Refusing to overwrite the run-owned packaged template path: ${packagedTemplateDevicePath}`);
    }
    packagedTemplateCleanupRequired = true;
    const templatePush = await runCommand(
      "adb",
      ["-s", serial, "push", packagedTemplateCopy, packagedTemplateDevicePath],
      {
        cwd: repoRoot,
        env: { ...process.env, ANDROID_SERIAL: serial },
        timeoutMs: 600_000,
        context,
        label: "packaged-template-push",
      },
    );
    requireSuccessfulCommand(templatePush, "Packaged smoke template push");
    const packagedTemplateDeviceSha256 = await deviceFileSha256(
      serial,
      packagedTemplateDevicePath,
      context,
      "packaged-template-device-sha256",
    );
    if (packagedTemplateDeviceSha256 !== packagedTemplateArtifact.sha256) {
      throw new Error(
        "Packaged smoke template device hash does not match the fresh current-source build " +
          `(${packagedTemplateDeviceSha256} != ${packagedTemplateArtifact.sha256}).`,
      );
    }
    summary.packagedTemplate = {
      sourceTask: PACKAGED_TEMPLATE_TASK,
      source: relativePath(repoRoot, packagedTemplateApk),
      artifact: packagedTemplateArtifact,
      signature: packagedTemplateSignature,
      devicePath: packagedTemplateDevicePath,
      deviceSha256BeforeTest: packagedTemplateDeviceSha256,
      deviceSha256AfterTest: null,
      deviceMatchesFreshBuild: true,
      deviceRemovedAfterTest: false,
      targetAbi: requirement.runtimeProcessAbi,
      overrideArgument: `${PACKAGED_TEMPLATE_OVERRIDE_ARGUMENT}=true`,
      exactPathArgument: `${PACKAGED_TEMPLATE_EXACT_PATH_ARGUMENT}=${packagedTemplateDevicePath}`,
      targetAbiArgument: `${PACKAGED_TEMPLATE_TARGET_ABI_ARGUMENT}=${requirement.runtimeProcessAbi}`,
    };
    writeSummary(runRoot, summary);

    const resultRoot = path.join(repoRoot, XML_RESULT_ROOT);
    const junitBefore = snapshotJUnitFiles(resultRoot);
    const instrumentationStartedAtEpochMs = Date.now();
    let gradle;
    let packagedTemplateDeviceSha256AfterTest = "";
    let evidenceGateFailure = null;
    try {
      gradle = await runGradle({
        root: repoRoot,
        args: buildEvidenceGradleArgs(pluginRoot, profile, runToken, requirement.runtimeProcessAbi),
        timeoutMs: boundedNumber(options.gradleTimeoutMs, 14_400_000, 900_000, 43_200_000, "gradle-timeout-ms"),
        context,
        label: "device-evidence-gradle",
        env: { ...process.env, ANDROID_SERIAL: serial },
      });
      packagedTemplateDeviceSha256AfterTest = await deviceFileSha256(
        serial,
        packagedTemplateDevicePath,
        context,
        "packaged-template-device-sha256-after-test",
      );
      if (packagedTemplateDeviceSha256AfterTest !== packagedTemplateArtifact.sha256) {
        throw new Error(
          "Packaged smoke template device hash changed during the evidence gate " +
          `(${packagedTemplateDeviceSha256AfterTest} != ${packagedTemplateArtifact.sha256}).`,
        );
      }
      requireSuccessfulCommand(gradle, "R6 connected device evidence gate");
    } catch (error) {
      evidenceGateFailure = error;
    }
    summary.packagedTemplate.deviceSha256AfterTest = packagedTemplateDeviceSha256AfterTest || null;
    let packagedTemplateCleanupFailure = null;
    if (packagedTemplateCleanupRequired) {
      try {
        await removeDeviceFile(
          serial,
          packagedTemplateDevicePath,
          context,
          "packaged-template-device-cleanup",
        );
        packagedTemplateCleanupRequired = false;
        summary.packagedTemplate.deviceRemovedAfterTest = true;
      } catch (error) {
        packagedTemplateCleanupFailure = error;
      }
    }
    writeSummary(runRoot, summary);
    if (evidenceGateFailure && packagedTemplateCleanupFailure) {
      const gateMessage = evidenceGateFailure.message || String(evidenceGateFailure);
      const cleanupMessage = packagedTemplateCleanupFailure.message || String(packagedTemplateCleanupFailure);
      const combined = new Error(`${gateMessage} Packaged template cleanup also failed: ${cleanupMessage}`);
      combined.cause = evidenceGateFailure;
      throw combined;
    }
    if (evidenceGateFailure) throw evidenceGateFailure;
    if (packagedTemplateCleanupFailure) throw packagedTemplateCleanupFailure;

    const freshJUnit = selectFreshJUnit(
      resultRoot,
      junitBefore,
      instrumentationStartedAtEpochMs,
      REQUIRED_DEVICE_SELECTORS,
    );
    const junitCopy = copyArtifact(freshJUnit.file, artifactDir, "r6-device-junit.xml");
    const junit = parseRequiredJUnit(junitCopy, REQUIRED_DEVICE_SELECTORS);
    summary.junit = {
      source: relativePath(repoRoot, freshJUnit.file),
      artifact: artifactRecord(junitCopy, repoRoot),
      freshness: freshJUnit.freshness,
      aggregate: junit.aggregate,
      selectors: junit.selectors,
    };

    const installedAfter = await inspectInstalledPlugin(serial, pluginMetadata.packageName, context);
    if (installedAfter.primaryCpuAbi !== requirement.runtimeProcessAbi) {
      throw new Error(
        `Plugin primaryCpuAbi changed during the run (${installedAfter.primaryCpuAbi || "missing"} != ${requirement.runtimeProcessAbi}).`,
      );
    }
    const installedPluginCopy = path.join(artifactDir, "installed-runtime-plugin.apk");
    await pullInstalledApk(serial, installedAfter.baseApkPath, installedPluginCopy, context);
    const requestedHash = sha256(requestedPluginCopy);
    const installedHash = sha256(installedPluginCopy);
    if (installedHash !== requestedHash) {
      throw new Error(
        `Installed runtime plugin APK hash does not match the specified APK (${installedHash} != ${requestedHash}).`,
      );
    }
    const installedPluginSignature = inspectApkSigner(
      installedPluginCopy,
      repoRoot,
      context,
      "installed-plugin-signature",
    );
    if (installedPluginSignature.signerSha256 !== requestedPluginSignature.signerSha256) {
      throw new Error(
        "Installed runtime plugin APK signer does not match the fresh requested APK signer " +
          `(${installedPluginSignature.signerSha256} != ${requestedPluginSignature.signerSha256}).`,
      );
    }
    const instrumentationPreflight = await collectInstrumentationPluginPreflight({
      serial,
      runToken,
      artifactDir,
      context,
    });
    const pluginProvenance = derivePluginProvenance({
      report: instrumentationPreflight.report,
      reportArtifact: instrumentationPreflight.artifact,
      junitSelectors: junit.selectors,
      requestedSignature: requestedPluginSignature,
      installedSignature: installedPluginSignature,
      expectedPackageName: pluginMetadata.packageName,
      expectedRuntimeProcessAbi: requirement.runtimeProcessAbi,
    });
    summary.plugin.installedAfter = installedAfter;
    summary.plugin.installedApk = artifactRecord(installedPluginCopy, repoRoot);
    summary.plugin.installedMatchesRequested = true;
    summary.plugin.installedSignature = installedPluginSignature;
    Object.assign(summary.plugin, pluginProvenance);
    writeSummary(runRoot, summary);

    const installedHost = await inspectInstalledPackage(serial, HOST_PACKAGE, context, "host");
    const installedHostCopy = path.join(artifactDir, "installed-host-app.apk");
    await pullInstalledApk(serial, installedHost.baseApkPath, installedHostCopy, context, "host-apk-pull");
    const hostApkSource = selectFreshMatchingApk(
      path.join(repoRoot, "app/build/outputs/apk/app/debug"),
      installedHostCopy,
      instrumentationStartedAtEpochMs,
      "host build",
    );
    const androidTestApkSource = selectAndroidTestApk(repoRoot);
    assertFreshArtifact(androidTestApkSource, instrumentationStartedAtEpochMs, "Android test APK");
    const installedAndroidTest = await inspectInstalledPackage(
      serial,
      ANDROID_TEST_PACKAGE,
      context,
      "android-test",
      false,
    );
    const installedAndroidTestCopy = path.join(artifactDir, "installed-host-app-androidTest.apk");
    await pullInstalledApk(
      serial,
      installedAndroidTest.baseApkPath,
      installedAndroidTestCopy,
      context,
      "android-test-apk-pull",
    );
    if (sha256(installedAndroidTestCopy) !== sha256(androidTestApkSource)) {
      throw new Error("Installed Android test APK does not match the fresh Android test build artifact.");
    }
    const hostBuildApk = copyArtifact(hostApkSource, artifactDir, "host-build.apk");
    const androidTestBuildApk = copyArtifact(androidTestApkSource, artifactDir, "host-app-androidTest-build.apk");
    const artifacts = {
      hostApk: artifactRecord(installedHostCopy, repoRoot),
      hostBuildApk: artifactRecord(hostBuildApk, repoRoot),
      androidTestApk: artifactRecord(installedAndroidTestCopy, repoRoot),
      androidTestBuildApk: artifactRecord(androidTestBuildApk, repoRoot),
      pluginApk: artifactRecord(installedPluginCopy, repoRoot),
      junitXml: artifactRecord(junitCopy, repoRoot),
      requestedPluginApk: pluginSourceArtifact,
      packagedTemplateApk: packagedTemplateArtifact,
    };
    summary.artifacts = artifacts;
    summary.host = {
      packageName: HOST_PACKAGE,
      installed: installedHost,
      installedApk: artifacts.hostApk,
      freshBuildApk: artifacts.hostBuildApk,
      installedMatchesFreshBuild: artifacts.hostApk.sha256 === artifacts.hostBuildApk.sha256,
      androidTest: {
        packageName: ANDROID_TEST_PACKAGE,
        installed: installedAndroidTest,
        installedApk: artifacts.androidTestApk,
        freshBuildApk: artifacts.androidTestBuildApk,
        installedMatchesFreshBuild: artifacts.androidTestApk.sha256 === artifacts.androidTestBuildApk.sha256,
      },
    };

    const identityAfter = currentIdentity(repoRoot, pluginRoot);
    summary.sourceIdentity.after = identityAfter;
    assertSameCleanIdentity(identityBefore, identityAfter, "after the production run");

    const derived = deriveEvidence(junit.selectors);
    const report = {
      schema: DEVICE_SCHEMA,
      generatedAt: new Date().toISOString(),
      status: "passed",
      target: requirement.id,
      evidenceTarget: requirement.id,
      runToken,
      identity: capturedIdentity(identityAfter),
      device: {
        role: requirement.role,
        serial,
        manufacturer: device.manufacturer,
        model: device.model,
        apiLevel: device.apiLevel,
        supportedAbis: device.supportedAbis,
        runtimeProcessAbi: installedAfter.primaryCpuAbi,
        isEmulator: device.isEmulator,
      },
      tests: {
        total: junit.aggregate.tests,
        failures: junit.aggregate.failures,
        errors: junit.aggregate.errors,
        skipped: junit.aggregate.skipped,
        selectors: junit.selectors.map((entry) => ({
          selector: entry.selector,
          status: entry.status,
          durationSeconds: entry.durationSeconds,
        })),
      },
      artifacts: {
        hostApkSha256: artifacts.hostApk.sha256,
        androidTestApkSha256: artifacts.androidTestApk.sha256,
        pluginApkSha256: artifacts.pluginApk.sha256,
        junitXmlSha256: artifacts.junitXml.sha256,
        hostApkPath: artifacts.hostApk.path,
        hostApkBytes: artifacts.hostApk.sizeBytes,
        androidTestApkPath: artifacts.androidTestApk.path,
        androidTestApkBytes: artifacts.androidTestApk.sizeBytes,
        androidTestBuildApkPath: artifacts.androidTestBuildApk.path,
        androidTestBuildApkSha256: artifacts.androidTestBuildApk.sha256,
        androidTestBuildApkBytes: artifacts.androidTestBuildApk.sizeBytes,
        installedAndroidTestMatchesFreshBuild: true,
        pluginApkPath: artifacts.pluginApk.path,
        pluginApkBytes: artifacts.pluginApk.sizeBytes,
        junitXmlPath: artifacts.junitXml.path,
        junitXmlBytes: artifacts.junitXml.sizeBytes,
        hostBuildApkPath: artifacts.hostBuildApk.path,
        hostBuildApkSha256: artifacts.hostBuildApk.sha256,
        hostBuildApkBytes: artifacts.hostBuildApk.sizeBytes,
        installedHostMatchesFreshBuild: true,
        requestedPluginApkPath: artifacts.requestedPluginApk.path,
        requestedPluginApkSha256: artifacts.requestedPluginApk.sha256,
        requestedPluginApkBytes: artifacts.requestedPluginApk.sizeBytes,
        installedPluginMatchesRequested: true,
        packagedTemplateApkPath: artifacts.packagedTemplateApk.path,
        packagedTemplateApkSha256: artifacts.packagedTemplateApk.sha256,
        packagedTemplateApkBytes: artifacts.packagedTemplateApk.sizeBytes,
        packagedTemplateSourceTask: PACKAGED_TEMPLATE_TASK,
        packagedTemplateDevicePath,
        packagedTemplateDeviceSha256BeforeTest: packagedTemplateDeviceSha256,
        packagedTemplateDeviceSha256AfterTest,
        packagedTemplateDeviceMatchesFreshBuild: true,
        packagedTemplateDeviceRemovedAfterTest: true,
        packagedTemplateTargetAbi: requirement.runtimeProcessAbi,
        packagedTemplateOverrideArgument: `${PACKAGED_TEMPLATE_OVERRIDE_ARGUMENT}=true`,
        packagedTemplateExactPathArgument:
          `${PACKAGED_TEMPLATE_EXACT_PATH_ARGUMENT}=${packagedTemplateDevicePath}`,
        packagedTemplateTargetAbiArgument:
          `${PACKAGED_TEMPLATE_TARGET_ABI_ARGUMENT}=${requirement.runtimeProcessAbi}`,
      },
      plugin: pluginProvenance,
      surfaces: derived.surfaces,
      regressions: derived.regressions,
      lifecycle: derived.lifecycle,
      resources: derived.resources,
      derivation: {
        policy: "fixed_junit_selector_assertion_mapping_v1",
        operatorOverridesAccepted: false,
        runToken,
        profile,
        junitXmlSha256: artifacts.junitXml.sha256,
        mappings: derived.mappings,
      },
    };

    const stageRoot = path.join(runRoot, "stage");
    const stagedFile = path.join(stageRoot, requirement.relative);
    fs.mkdirSync(path.dirname(stagedFile), { recursive: true });
    fs.writeFileSync(stagedFile, JSON.stringify(report, null, 2) + "\n");
    const evaluation = evaluateDeviceReport(stageRoot, requirement, identityAfter, repoRoot);
    summary.candidate = {
      file: relativePath(repoRoot, stagedFile),
      sha256: sha256(stagedFile),
      evaluation,
    };
    if (!evaluation.ready) {
      throw new Error(`Staged evidence failed the production verifier: ${evaluation.blockers.join("; ")}`);
    }
    summary.decision.validated = true;

    if (!options.noArchive) {
      const archive = archiveCanonicalEvidence({
        repoRoot,
        evidenceRoot,
        requirement,
        stagedFile,
        runRoot,
        runToken,
        identity: identityAfter,
      });
      summary.archive = archive;
      summary.decision.archived = archive.archived;
    } else {
      summary.archive = {
        requested: false,
        archived: false,
        target: relativePath(repoRoot, path.join(evidenceRoot, requirement.relative)),
        reason: "--no-archive",
      };
    }

    summary.decision.status = "completed";
    summary.generatedAt = new Date().toISOString();
    writeSummary(runRoot, summary);
    logEvent(logFile, `Completed R6 device evidence run ${runToken}`);
    console.log("R6 device evidence runner: completed");
    console.log(`- validated: ${summary.decision.validated}`);
    console.log(`- archived: ${summary.decision.archived}`);
    console.log(`- summary: ${path.join(runRoot, "run.json")}`);
  } catch (error) {
    let failure = error;
    if (packagedTemplateCleanupRequired) {
      try {
        await removeDeviceFile(
          serial,
          packagedTemplateDevicePath,
          context,
          "packaged-template-device-cleanup-after-failure",
        );
        packagedTemplateCleanupRequired = false;
        if (summary.packagedTemplate) summary.packagedTemplate.deviceRemovedAfterTest = true;
      } catch (cleanupError) {
        const originalMessage = error && error.message ? error.message : String(error);
        const cleanupMessage = cleanupError && cleanupError.message ? cleanupError.message : String(cleanupError);
        failure = new Error(`${originalMessage} Packaged template cleanup also failed: ${cleanupMessage}`);
        failure.cause = error;
      }
    }
    const message = failure && failure.message ? failure.message : String(failure);
    summary.decision.status = "failed";
    summary.decision.failures = [...new Set([...summary.decision.failures, message])];
    summary.generatedAt = new Date().toISOString();
    writeSummary(runRoot, summary);
    logEvent(logFile, `Failed R6 device evidence run: ${message}`);
    throw failure;
  }
}

function buildPackagedTemplateGradleArgs() {
  return [
    "--console=plain",
    "--rerun-tasks",
    PACKAGED_TEMPLATE_TASK,
    `-Pautojs.nodejs.runtimeSlot=${RUNTIME_SLOT}`,
    "-Pautojs.nodejs.embedded.script.enabled=true",
    "-Pautojs.gradle.build.number.auto.increment.enabled=false",
    "-Pautojs.gradle.build.time.update.enabled=false",
  ];
}

function buildEvidenceGradleArgs(pluginRoot, profile, runToken, runtimeProcessAbi) {
  const packagedTemplateDevicePath = packagedTemplatePathForRunToken(runToken);
  return [
    "--console=plain",
    "--rerun-tasks",
    GRADLE_TASK,
    `-Pautojs.nodejs.releaseFlavor=${RELEASE_FLAVOR}`,
    `-Pautojs.nodejs.backend=${BACKEND}`,
    `-Pautojs.nodejs.runtimeSlot=${RUNTIME_SLOT}`,
    "-Pautojs.nodejs.embedded.persistentRuntime=true",
    `-Pautojs.nodejs.pluginProjectDir=${pluginRoot}`,
    `-Pautojs.nodejs.test.deviceProfile=${profile}`,
    "-Pautojs.gradle.build.number.auto.increment.enabled=false",
    "-Pautojs.gradle.build.time.update.enabled=false",
    "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true",
    `-Pandroid.testInstrumentationRunnerArguments.${PACKAGED_TEMPLATE_OVERRIDE_ARGUMENT}=true`,
    `-Pandroid.testInstrumentationRunnerArguments.${PACKAGED_TEMPLATE_EXACT_PATH_ARGUMENT}=${packagedTemplateDevicePath}`,
    `-Pandroid.testInstrumentationRunnerArguments.${PACKAGED_TEMPLATE_TARGET_ABI_ARGUMENT}=${runtimeProcessAbi}`,
    `-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.r5.pluginWarmSamples=${R5_WARM_SAMPLES}`,
    `-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.r6.soak.kind=${SOAK_KIND}`,
    `-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.r6.soak.runToken=${runToken}`,
    `-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.r6.soak.durationMs=${SOAK_SMOKE_DURATION_MS}`,
    `-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.r6.soak.sampleIntervalMs=${SOAK_SMOKE_SAMPLE_INTERVAL_MS}`,
    `-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.r6.soak.cycleDelayMs=${SOAK_SMOKE_CYCLE_DELAY_MS}`,
    `-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.r6.soak.livenessRecoverabilityEveryCycles=${SOAK_SMOKE_RECOVERY_EVERY_CYCLES}`,
    `-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.r6.runToken=${runToken}`,
    `-Pautojs.nodejs.r6.runToken=${runToken}`,
  ];
}

function deriveEvidence(selectorResults) {
  const passed = new Map(selectorResults.map((entry) => [entry.selector, entry.status === "passed"]));
  const mappings = { surfaces: {}, regressions: {}, lifecycle: {}, resources: {} };
  const derive = (group, key, selectors, value) => {
    if (!Array.isArray(selectors) || selectors.length === 0) {
      throw new Error(`Fixed derivation ${group}.${key} has no selectors.`);
    }
    const missing = selectors.filter((selector) => passed.get(selector) !== true);
    if (missing.length > 0) {
      throw new Error(`Cannot derive ${group}.${key}; selector evidence is not passed: ${missing.join(", ")}`);
    }
    mappings[group][key] = { selectors: [...selectors], value };
    return value;
  };

  const surfaces = {};
  Object.entries(SELECTOR_DERIVATION.surfaces).forEach(([key, selectors]) => {
    surfaces[key] = derive("surfaces", key, selectors, "passed");
  });
  const regressions = {};
  Object.entries(SELECTOR_DERIVATION.regressions).forEach(([key, selectors]) => {
    regressions[key] = derive("regressions", key, selectors, "passed");
  });
  const lifecycleValues = {
    persistentProcessRuntime: true,
    processRuntimeInitCount: 1,
    runtimeGenerationStable: true,
    executionSequenceMonotonic: true,
    freshExecutionState: true,
    recoveryAfterProcessDeath: true,
  };
  const lifecycle = {};
  Object.entries(SELECTOR_DERIVATION.lifecycle).forEach(([key, selectors]) => {
    lifecycle[key] = derive("lifecycle", key, selectors, lifecycleValues[key]);
  });
  const resourceValues = { activeBridgeResources: 0, orphanProcesses: 0, unrecoverableSlots: 0 };
  const resources = {};
  Object.entries(SELECTOR_DERIVATION.resources).forEach(([key, selectors]) => {
    resources[key] = derive("resources", key, selectors, resourceValues[key]);
  });
  if (Object.keys(regressions).sort().join("\n") !== [...REQUIRED_REGRESSIONS].sort().join("\n")) {
    throw new Error("Fixed regression derivation does not match REQUIRED_REGRESSIONS.");
  }
  return { surfaces, regressions, lifecycle, resources, mappings };
}

async function readAndValidateDevice(serial, requirement, context) {
  const state = await adbText(serial, ["get-state"], context, "adb-device-state");
  if (state.trim() !== "device") throw new Error(`adb device ${serial} is not ready: ${state.trim() || "missing state"}`);
  const propertyNames = [
    "ro.product.manufacturer",
    "ro.product.model",
    "ro.build.version.sdk",
    "ro.product.cpu.abilist",
    "ro.kernel.qemu",
    "ro.boot.qemu",
    "ro.build.fingerprint",
    "ro.product.name",
    "ro.product.device",
    "ro.hardware",
  ];
  const properties = {};
  for (const name of propertyNames) {
    properties[name] = (await adbText(serial, ["shell", "getprop", name], context, `getprop-${name.replace(/[^a-z0-9]+/gi, "-")}`)).trim();
  }
  const manufacturer = properties["ro.product.manufacturer"];
  const model = properties["ro.product.model"];
  const apiLevel = Number(properties["ro.build.version.sdk"]);
  const supportedAbis = properties["ro.product.cpu.abilist"].split(",").map((value) => value.trim()).filter(Boolean);
  const emulatorText = [
    properties["ro.build.fingerprint"],
    properties["ro.product.name"],
    properties["ro.product.device"],
    properties["ro.hardware"],
    model,
  ].join(" ");
  const isEmulator = properties["ro.kernel.qemu"] === "1" ||
    properties["ro.boot.qemu"] === "1" ||
    /(?:^|[\s/])(generic|sdk_gphone|emulator)(?:[\s/_-]|$)|goldfish|ranchu|android sdk built for/i.test(emulatorText);
  if (!manufacturer || !model || !Number.isInteger(apiLevel) || apiLevel <= 0) {
    throw new Error("Device manufacturer, model, or API level is unavailable.");
  }
  if (apiLevel < 24) throw new Error(`Device API level ${apiLevel} is below 24.`);
  if (!supportedAbis.includes(requirement.runtimeProcessAbi)) {
    throw new Error(`Device ABI list does not contain required ${requirement.runtimeProcessAbi}: ${supportedAbis.join(", ")}`);
  }
  if (isEmulator !== requirement.emulator) {
    throw new Error(`Target ${requirement.id} requires isEmulator=${requirement.emulator}, detected ${isEmulator}.`);
  }
  const normalizedManufacturer = manufacturer.toLowerCase();
  if (requirement.manufacturer && normalizedManufacturer !== requirement.manufacturer) {
    throw new Error(`Target ${requirement.id} requires manufacturer ${requirement.manufacturer}, detected ${manufacturer}.`);
  }
  if (requirement.manufacturerNot && normalizedManufacturer === requirement.manufacturerNot) {
    throw new Error(`Target ${requirement.id} requires an OEM other than ${requirement.manufacturerNot}, detected ${manufacturer}.`);
  }
  return { serial, manufacturer, model, apiLevel, supportedAbis, isEmulator, properties };
}

async function inspectInstalledPlugin(serial, packageName, context) {
  return inspectInstalledPackage(serial, packageName, context, "plugin");
}

async function inspectInstalledPackage(serial, packageName, context, label, requirePrimaryCpuAbi = true) {
  const dumpsys = await adbText(serial, ["shell", "dumpsys", "package", packageName], context, `${label}-dumpsys`);
  const primaryCpuAbi = matchLineValue(dumpsys, "primaryCpuAbi");
  const versionName = matchLineValue(dumpsys, "versionName");
  const pmPath = await adbText(serial, ["shell", "pm", "path", packageName], context, `${label}-pm-path`);
  const apkPaths = pmPath.split(/\r?\n/)
    .map((line) => line.trim().replace(/^package:/, ""))
    .filter((line) => line.startsWith("/"));
  const baseApkPath = apkPaths.find((entry) => entry.endsWith("/base.apk")) || apkPaths[0] || "";
  if ((requirePrimaryCpuAbi && !primaryCpuAbi) || !baseApkPath) {
    throw new Error(`Installed package ${packageName} has incomplete ABI or base APK metadata.`);
  }
  return { primaryCpuAbi, versionName, baseApkPath, apkPaths };
}

async function pullInstalledApk(serial, remote, local, context, label = "plugin-apk-pull") {
  if (!remote.startsWith("/")) throw new Error(`Invalid installed plugin APK path: ${remote}`);
  const pull = await runCommand("adb", ["-s", serial, "pull", remote, local], {
    cwd: context.repoRoot,
    env: { ...process.env, ANDROID_SERIAL: serial },
    timeoutMs: 600_000,
    context,
    label,
  });
  requireSuccessfulCommand(pull, "Installed runtime plugin APK pull");
  if (!fs.existsSync(local) || fs.statSync(local).size <= 0) {
    throw new Error("The installed runtime plugin APK pull produced no file.");
  }
}

async function collectInstrumentationPluginPreflight(config) {
  const tokenPrefix = config.runToken.slice(0, 16);
  const filePattern = `r6-persistent-plugin-soak-${SOAK_KIND}-${tokenPrefix}-*.json`;
  const listing = await adbText(
    config.serial,
    ["shell", "ls", "-1", `${DEVICE_SOAK_REPORT_DIR}/${filePattern}`],
    config.context,
    "plugin-preflight-report-list",
  );
  const expectedName = new RegExp(
    `^r6-persistent-plugin-soak-${SOAK_KIND}-${tokenPrefix}-[0-9]+\\.json$`,
  );
  const candidates = [...new Set(listing.split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => ({ line, name: path.posix.basename(line.replace(/\\/g, "/")) }))
    .filter((entry) => expectedName.test(entry.name))
    .map((entry) => entry.line.startsWith("/")
      ? entry.line.replace(/\\/g, "/")
      : `${DEVICE_SOAK_REPORT_DIR}/${entry.name}`))];
  if (candidates.length !== 1) {
    throw new Error(
      `Exactly one instrumentation plugin preflight report is required for run ${config.runToken}; found ${candidates.length}.`,
    );
  }
  const local = path.join(config.artifactDir, "r6-smoke-plugin-preflight.json");
  const pull = await runCommand("adb", ["-s", config.serial, "pull", candidates[0], local], {
    cwd: config.context.repoRoot,
    env: { ...process.env, ANDROID_SERIAL: config.serial },
    timeoutMs: 300_000,
    context: config.context,
    label: "plugin-preflight-report-pull",
  });
  requireSuccessfulCommand(pull, "Instrumentation plugin preflight report pull");
  if (!fs.existsSync(local) || fs.statSync(local).size <= 0) {
    throw new Error("The instrumentation plugin preflight report pull produced no file.");
  }
  let report;
  try {
    report = JSON.parse(fs.readFileSync(local, "utf8"));
  } catch (error) {
    throw new Error(`Instrumentation plugin preflight report is not valid JSON: ${error.message}`);
  }
  if (report.schema !== SOAK_REPORT_SCHEMA || report.kind !== SOAK_KIND || report.runToken !== config.runToken) {
    throw new Error("Instrumentation plugin preflight report schema, kind, or runToken does not match this run.");
  }
  if (report.status !== "passed" || !Array.isArray(report.failures) || report.failures.length !== 0) {
    throw new Error("Instrumentation plugin preflight report did not finish passed with an empty failures array.");
  }
  const reportStartedAtEpochMs = Number(report.startedAtEpochMs);
  if (!Number.isFinite(reportStartedAtEpochMs) || reportStartedAtEpochMs <= 0) {
    throw new Error("Instrumentation plugin preflight report has no valid device start time.");
  }
  return {
    report,
    artifact: artifactRecord(local, config.context.repoRoot),
  };
}

function derivePluginProvenance(config) {
  const plugin = config.report && config.report.plugin ? config.report.plugin : {};
  const packageName = String(plugin.packageName || "").trim();
  const serviceClass = String(plugin.serviceClass || plugin.serviceName || "").trim();
  const signerSha256 = String(plugin.signerSha256 || "").trim().toLowerCase();
  const authorizationState = String(plugin.authorizationState || "").trim().toLowerCase();
  const runtimeSlot = String(plugin.runtimeSlot || "").trim();
  const nodeVersion = String(plugin.nodeVersion || "").trim();
  const runtimeProcessAbi = String(plugin.runtimeProcessAbi || "").trim();
  if (packageName !== config.expectedPackageName || packageName !== PLUGIN_PACKAGE) {
    throw new Error(`Instrumentation preflight plugin package is invalid: ${packageName || "missing"}.`);
  }
  if (!serviceClass || /\s/.test(serviceClass)) {
    throw new Error(`Instrumentation preflight plugin service class is invalid: ${serviceClass || "missing"}.`);
  }
  if (!SHA256_PATTERN.test(signerSha256)) {
    throw new Error("Instrumentation preflight plugin signer SHA-256 is missing or malformed.");
  }
  if (signerSha256 !== config.requestedSignature.signerSha256 ||
      signerSha256 !== config.installedSignature.signerSha256) {
    throw new Error("Instrumentation preflight signer does not match both apksigner-verified plugin APKs.");
  }
  if (runtimeSlot !== RUNTIME_SLOT || nodeVersion.replace(/^v/, "") !== NODE_VERSION) {
    throw new Error(
      `Instrumentation preflight runtime identity drifted (${runtimeSlot || "missing"}/${nodeVersion || "missing"}).`,
    );
  }
  if (runtimeProcessAbi !== config.expectedRuntimeProcessAbi) {
    throw new Error(
      `Instrumentation preflight runtime ABI must be ${config.expectedRuntimeProcessAbi}, got ${runtimeProcessAbi || "missing"}.`,
    );
  }
  const r5PreflightPassed = selectorPassed(config.junitSelectors, R5_PLUGIN_PREFLIGHT_SELECTOR);
  const r6SoakPassed = selectorPassed(config.junitSelectors, R6_PLUGIN_SOAK_SELECTOR);
  const required = config.report.status === "passed" && r6SoakPassed;
  const authenticated = r5PreflightPassed && r6SoakPassed &&
    config.requestedSignature.signerCount === 1 &&
    config.installedSignature.signerCount === 1 &&
    signerSha256 === config.requestedSignature.signerSha256 &&
    signerSha256 === config.installedSignature.signerSha256;
  const trusted = r5PreflightPassed && r6SoakPassed && AUTHORIZED_PLUGIN_STATES.has(authorizationState);
  if (!required || !authenticated || !trusted) {
    throw new Error(
      "Plugin provenance was not proven by the required-mode instrumentation, trust preflight, and APK signer bindings.",
    );
  }
  return {
    packageName,
    serviceClass,
    signerSha256,
    authorizationState,
    required,
    authenticated,
    trusted,
    runtimeSlot,
    nodeVersion,
    runtimeProcessAbi,
    evidence: {
      instrumentationReport: config.reportArtifact,
      requiredModeSelector: R6_PLUGIN_SOAK_SELECTOR,
      trustIdentitySelector: R5_PLUGIN_PREFLIGHT_SELECTOR,
      requestedApkSignerSha256: config.requestedSignature.signerSha256,
      installedApkSignerSha256: config.installedSignature.signerSha256,
    },
  };
}

function selectorPassed(selectors, selector) {
  const matches = Array.isArray(selectors)
    ? selectors.filter((entry) => entry && entry.selector === selector)
    : [];
  return matches.length === 1 && matches[0].status === "passed";
}

function selectFreshMatchingApk(root, installedApk, startedAtEpochMs, label) {
  const installedHash = sha256(installedApk);
  const candidates = listFiles(root)
    .filter((file) => file.toLowerCase().endsWith(".apk"))
    .filter((file) => fs.statSync(file).mtimeMs >= startedAtEpochMs - FRESHNESS_TOLERANCE_MS)
    .filter((file) => sha256(file) === installedHash)
    .sort((left, right) => fs.statSync(right).mtimeMs - fs.statSync(left).mtimeMs);
  if (candidates.length === 0) {
    throw new Error(
      `The installed ${label} APK (${installedHash}) does not match any APK freshly built by this run under ${root}.`,
    );
  }
  return candidates[0];
}

function inspectApk(apk, cwd, context) {
  const aapt = findAapt(cwd);
  const result = childProcess.spawnSync(aapt, ["dump", "badging", apk], {
    cwd,
    encoding: "utf8",
    windowsHide: true,
    timeout: 120_000,
    maxBuffer: 16 * 1024 * 1024,
  });
  logSyncResult(context.logFile, aapt, ["dump", "badging", apk], result, "apk-badging");
  if (result.status !== 0 || result.error) {
    throw new Error(`aapt could not inspect ${apk}: ${String(result.stderr || "").trim()}`);
  }
  const output = String(result.stdout || "");
  const packageMatch = output.match(/^package:\s+name='([^']+)'/m);
  const nativeMatch = output.match(/^native-code:\s+(.+)$/m);
  const nativeAbis = nativeMatch
    ? [...nativeMatch[1].matchAll(/'([^']+)'/g)].map((match) => match[1])
    : [];
  return { packageName: packageMatch ? packageMatch[1] : "", nativeAbis, aapt };
}

function inspectApkSigner(apk, cwd, context, label) {
  const apksigner = findApksigner(cwd);
  const args = ["verify", "--verbose", "--print-certs", apk];
  const invocation = commandInvocation(apksigner, args);
  const result = childProcess.spawnSync(invocation.command, invocation.args, {
    cwd,
    encoding: "utf8",
    windowsHide: true,
    timeout: 120_000,
    maxBuffer: 16 * 1024 * 1024,
  });
  logSyncResult(context.logFile, invocation.command, invocation.args, result, label);
  if (result.status !== 0 || result.error) {
    const detail = [result.stderr, result.stdout, result.error && result.error.message]
      .map((value) => String(value || "").trim())
      .filter(Boolean)
      .join("; ");
    throw new Error(`apksigner could not verify ${apk}: ${detail || "no diagnostics"}`);
  }
  return parseApkSignerOutput(String(result.stdout || ""), apk);
}

function parseApkSignerOutput(output, label = "APK") {
  if (!/Verified using v2 scheme \(APK Signature Scheme v2\): true/i.test(output)) {
    throw new Error(`${label} did not verify with APK Signature Scheme v2.`);
  }
  const countMatch = output.match(/Number of signers:\s*([0-9]+)/i);
  const signerCount = countMatch ? Number(countMatch[1]) : 0;
  const signerDigestMatches = apkSignerCertificateSha256Digests(output);
  const signerDigests = [...new Set(signerDigestMatches)];
  if (signerCount !== 1 || signerDigestMatches.length !== 1 || signerDigests.length !== 1) {
    throw new Error(
      `${label} must expose exactly one SHA-256 signer (reported=${signerCount}, digests=${signerDigestMatches.length}).`,
    );
  }
  return {
    signerSha256: signerDigests[0],
    signerCount,
    verifiedV2: true,
  };
}

function validatePluginApk(metadata, apk, expectedAbi) {
  if (metadata.packageName !== PLUGIN_PACKAGE) {
    throw new Error(`Plugin APK package must be ${PLUGIN_PACKAGE}, got ${metadata.packageName || "missing"}: ${apk}`);
  }
  if (metadata.nativeAbis.length !== 1 || metadata.nativeAbis[0] !== expectedAbi) {
    throw new Error(
      `Plugin APK must contain exactly the target ABI ${expectedAbi}; declared native-code is ${metadata.nativeAbis.join(", ") || "missing"}.`,
    );
  }
}

function validatePackagedTemplateApk(metadata, apk, expectedAbi) {
  if (metadata.packageName !== PACKAGED_TEMPLATE_PACKAGE) {
    throw new Error(
      `Packaged smoke template APK package must be ${PACKAGED_TEMPLATE_PACKAGE}, ` +
        `got ${metadata.packageName || "missing"}: ${apk}`,
    );
  }
  if (metadata.nativeAbis.length !== 1 || metadata.nativeAbis[0] !== expectedAbi) {
    throw new Error(
      `Packaged smoke template must contain exactly the target ABI ${expectedAbi}; ` +
        `declared native-code is ${metadata.nativeAbis.join(", ") || "missing"}.`,
    );
  }
}

function selectAbiApk(root, abi, label) {
  const suffix = `-${abi}.apk`.toLowerCase();
  const matches = listFiles(root)
    .filter((file) => path.basename(file).toLowerCase().endsWith(suffix))
    .sort((left, right) => fs.statSync(right).mtimeMs - fs.statSync(left).mtimeMs);
  if (matches.length === 0) throw new Error(`No ${label} APK for ABI ${abi} was found under ${root}.`);
  if (matches.length > 1 && fs.statSync(matches[0]).mtimeMs === fs.statSync(matches[1]).mtimeMs) {
    throw new Error(`Multiple equally recent ${label} APKs for ABI ${abi} were found under ${root}.`);
  }
  return matches[0];
}

function selectAndroidTestApk(repoRoot) {
  const root = path.join(repoRoot, "app/build/outputs/apk/androidTest/app/debug");
  const matches = listFiles(root)
    .filter((file) => path.basename(file).endsWith("-androidTest.apk"))
    .sort((left, right) => fs.statSync(right).mtimeMs - fs.statSync(left).mtimeMs);
  if (matches.length === 0) throw new Error(`No Android test APK was found under ${root}.`);
  return matches[0];
}

function snapshotJUnitFiles(root) {
  const snapshot = new Map();
  listFiles(root)
    .filter((file) => path.basename(file).startsWith("TEST-") && file.endsWith(".xml"))
    .forEach((file) => {
      const stat = fs.statSync(file);
      snapshot.set(path.resolve(file), { mtimeMs: stat.mtimeMs, size: stat.size, sha256: sha256(file) });
    });
  return snapshot;
}

function selectFreshJUnit(root, before, startedAtEpochMs, requiredSelectors) {
  const candidates = [];
  for (const file of listFiles(root).filter((entry) => path.basename(entry).startsWith("TEST-") && entry.endsWith(".xml"))) {
    const stat = fs.statSync(file);
    const digest = sha256(file);
    const previous = before.get(path.resolve(file));
    const changed = !previous || previous.sha256 !== digest || previous.size !== stat.size;
    if (!changed || stat.mtimeMs < startedAtEpochMs - FRESHNESS_TOLERANCE_MS) continue;
    try {
      const parsed = parseRequiredJUnit(file, requiredSelectors);
      candidates.push({
        file,
        parsed,
        mtimeMs: stat.mtimeMs,
        freshness: {
          changedSinceSnapshot: true,
          mtimeMs: stat.mtimeMs,
          instrumentationStartedAtEpochMs: startedAtEpochMs,
          previousSha256: previous ? previous.sha256 : null,
          sha256: digest,
        },
      });
    } catch (_) {
      // Changed XML from unrelated Gradle work is not accepted as this run's evidence.
    }
  }
  candidates.sort((left, right) => right.mtimeMs - left.mtimeMs);
  if (candidates.length === 0) {
    throw new Error("No fresh JUnit XML contains exactly the required R6 device selectors with explicit zero failures/errors/skips.");
  }
  if (candidates.length > 1) {
    throw new Error(`Multiple fresh R6 JUnit XML files are ambiguous: ${candidates.map((entry) => entry.file).join(", ")}`);
  }
  return candidates[0];
}

function parseRequiredJUnit(file, requiredSelectors) {
  const xml = fs.readFileSync(file, "utf8");
  return parseRequiredJUnitXml(xml, requiredSelectors, file);
}

function parseRequiredJUnitXml(xml, requiredSelectors, label = "JUnit XML") {
  const rootMatch = xml.match(/<(testsuites|testsuite)\b([^>]*)>/i);
  if (!rootMatch) throw new Error(`JUnit XML has no testsuites/testsuite root: ${label}`);
  const aggregate = explicitZeroAggregate(rootMatch[2], `JUnit root ${rootMatch[1]}`);
  const suiteMatches = [...xml.matchAll(/<testsuite\b([^>]*)>/gi)];
  if (suiteMatches.length === 0) throw new Error(`JUnit XML has no testsuite: ${label}`);
  suiteMatches.forEach((match, index) => explicitZeroAggregate(match[1], `JUnit testsuite ${index}`));

  const testcases = [];
  const testcasePattern = /<testcase\b([^>]*?)\/>|<testcase\b([^>]*)>([\s\S]*?)<\/testcase\s*>/gi;
  for (const match of xml.matchAll(testcasePattern)) {
    const attributes = match[1] || match[2] || "";
    const body = match[3] || "";
    const className = xmlAttribute(attributes, "classname");
    const name = xmlAttribute(attributes, "name");
    const selector = `${className}#${name}`;
    const failure = /<failure\b/i.test(body);
    const error = /<error\b/i.test(body);
    const skipped = /<skipped\b/i.test(body);
    const durationSeconds = numberXmlAttribute(attributes, "time", 0);
    testcases.push({ selector, failure, error, skipped, durationSeconds });
  }
  if (testcases.length !== requiredSelectors.length) {
    throw new Error(`JUnit testcase count must be exactly ${requiredSelectors.length}, got ${testcases.length}.`);
  }
  if (aggregate.tests !== testcases.length) {
    throw new Error(`JUnit root tests=${aggregate.tests} does not equal parsed testcase count ${testcases.length}.`);
  }
  const requiredSet = new Set(requiredSelectors);
  const unexpected = testcases.filter((entry) => !requiredSet.has(entry.selector)).map((entry) => entry.selector);
  if (unexpected.length > 0) throw new Error(`JUnit contains unexpected selectors: ${unexpected.join(", ")}`);
  const selectors = requiredSelectors.map((selector) => {
    const matches = testcases.filter((entry) => entry.selector === selector);
    if (matches.length !== 1) throw new Error(`JUnit selector ${selector} must occur exactly once, got ${matches.length}.`);
    const testcase = matches[0];
    if (testcase.failure || testcase.error || testcase.skipped) {
      throw new Error(`JUnit selector did not pass without failure/error/skip: ${selector}`);
    }
    return { selector, status: "passed", durationSeconds: testcase.durationSeconds };
  });
  return { aggregate, selectors };
}

function explicitZeroAggregate(attributes, label) {
  const values = {};
  for (const name of ["tests", "failures", "errors", "skipped"]) {
    const raw = xmlAttribute(attributes, name);
    if (raw === "") throw new Error(`${label} must explicitly declare ${name}.`);
    const value = Number(raw);
    if (!Number.isInteger(value) || value < 0) throw new Error(`${label} ${name} must be a non-negative integer.`);
    values[name] = value;
  }
  for (const name of ["failures", "errors", "skipped"]) {
    if (values[name] !== 0) throw new Error(`${label} ${name} must be explicitly zero, got ${values[name]}.`);
  }
  return values;
}

function capturedIdentity(identity) {
  return {
    hostCommit: identity.host.commit,
    pluginCommit: identity.plugin.commit,
    versionName: identity.version.name,
    versionBuild: identity.version.build,
    lifecycleModel: LIFECYCLE_MODEL,
    releaseFlavor: RELEASE_FLAVOR,
    backend: BACKEND,
    pluginRequired: true,
    runtimeSlot: RUNTIME_SLOT,
    nodeVersion: NODE_VERSION,
    hostTreeClean: identity.host.clean,
    pluginTreeClean: identity.plugin.clean,
  };
}

function assertCleanIdentity(identity, label) {
  if (!identity || identity.blockers.length > 0 || !identity.host.clean || !identity.plugin.clean) {
    const blockers = identity && identity.blockers ? identity.blockers : ["source identity unavailable"];
    throw new Error(`R6 production device evidence requires clean host and plugin worktrees ${label}: ${blockers.join("; ")}`);
  }
}

function assertSameCleanIdentity(before, after, label) {
  assertCleanIdentity(after, label);
  const comparisons = [
    ["host commit", before.host.commit, after.host.commit],
    ["plugin commit", before.plugin.commit, after.plugin.commit],
    ["version name", before.version.name, after.version.name],
    ["version build", before.version.build, after.version.build],
  ];
  const changed = comparisons.filter((entry) => entry[1] !== entry[2]);
  if (changed.length > 0) {
    throw new Error(`Source identity changed ${label}: ${changed.map(([name, first, second]) => `${name} ${first} -> ${second}`).join("; ")}`);
  }
}

function archiveCanonicalEvidence(config) {
  const target = path.join(config.evidenceRoot, config.requirement.relative);
  const backup = path.join(config.runRoot, "canonical-before.json");
  const temporary = `${target}.tmp-${process.pid}-${config.runToken.slice(-8)}`;
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.copyFileSync(config.stagedFile, temporary);
  let previousMoved = false;
  try {
    if (fs.existsSync(target)) {
      fs.renameSync(target, backup);
      previousMoved = true;
    }
    fs.renameSync(temporary, target);
  } catch (error) {
    if (fs.existsSync(temporary)) fs.rmSync(temporary, { force: true });
    if (previousMoved && !fs.existsSync(target) && fs.existsSync(backup)) fs.renameSync(backup, target);
    throw error;
  }
  const evaluation = evaluateDeviceReport(config.evidenceRoot, config.requirement, config.identity, config.repoRoot);
  if (!evaluation.ready) {
    const invalid = path.join(config.runRoot, "canonical-invalid.json");
    fs.renameSync(target, invalid);
    if (previousMoved && fs.existsSync(backup)) fs.renameSync(backup, target);
    throw new Error(`Canonical evidence failed post-write verification: ${evaluation.blockers.join("; ")}`);
  }
  return {
    requested: true,
    archived: true,
    target: relativePath(config.repoRoot, target),
    sha256: sha256(target),
    previous: previousMoved ? relativePath(config.repoRoot, backup) : null,
    evaluation,
  };
}

function assertFreshArtifact(file, startedAtEpochMs, label) {
  const stat = fs.statSync(file);
  if (stat.mtimeMs < startedAtEpochMs - FRESHNESS_TOLERANCE_MS) {
    throw new Error(`${label} is stale for this run: ${file} (${new Date(stat.mtimeMs).toISOString()}).`);
  }
}

function copyArtifact(source, directory, name) {
  const target = path.join(directory, name);
  fs.copyFileSync(source, target);
  if (sha256(source) !== sha256(target)) throw new Error(`Artifact copy hash mismatch: ${source} -> ${target}`);
  return target;
}

function artifactRecord(file, repoRoot) {
  const stat = fs.statSync(file);
  if (!stat.isFile() || stat.size <= 0) throw new Error(`Evidence artifact must be a non-empty file: ${file}`);
  return {
    path: relativePath(repoRoot, file),
    sizeBytes: stat.size,
    sha256: sha256(file),
    modifiedAt: new Date(stat.mtimeMs).toISOString(),
  };
}

async function adbText(serial, args, context, label) {
  const result = await runCommand("adb", ["-s", serial, ...args], {
    cwd: context.repoRoot,
    env: { ...process.env, ANDROID_SERIAL: serial },
    timeoutMs: 60_000,
    context,
    label,
    echo: false,
  });
  requireSuccessfulCommand(result, `adb ${args.join(" ")}`);
  return result.stdout;
}

async function deviceFileSha256(serial, devicePath, context, label) {
  const output = await adbText(serial, ["shell", "sha256sum", devicePath], context, label);
  const match = output.match(/^([0-9a-f]{64})\s+/im);
  if (!match) throw new Error(`Device did not report a SHA-256 digest for ${devicePath}.`);
  return match[1].toLowerCase();
}

async function deviceFileExists(serial, devicePath, context, label) {
  const command = `if [ -e ${devicePath} ]; then echo present; else echo absent; fi`;
  const output = (await adbText(serial, ["shell", command], context, label)).trim();
  if (output === "present") return true;
  if (output === "absent") return false;
  throw new Error(`Device path probe returned an unexpected result for ${devicePath}: ${output || "<empty>"}`);
}

async function removeDeviceFile(serial, devicePath, context, label) {
  const result = await runCommand("adb", ["-s", serial, "shell", "rm", "-f", devicePath], {
    cwd: context.repoRoot,
    env: { ...process.env, ANDROID_SERIAL: serial },
    timeoutMs: 60_000,
    context,
    label,
    echo: false,
  });
  requireSuccessfulCommand(result, `adb shell rm -f ${devicePath}`);
  if (await deviceFileExists(serial, devicePath, context, `${label}-probe`)) {
    throw new Error(`Packaged template cleanup left the run-owned device file in place: ${devicePath}`);
  }
}

function packagedTemplatePathForRunToken(runToken) {
  const value = String(runToken || "");
  if (!SHA256_PATTERN.test(value)) {
    throw new Error("Packaged template device path requires a 64-character hexadecimal run token.");
  }
  return `${PACKAGED_TEMPLATE_DEVICE_PATH_PREFIX}${value}.apk`;
}

async function runGradle(config) {
  const command = gradleCommand(config.root);
  const invocation = commandInvocation(command, config.args);
  return runCommand(invocation.command, invocation.args, {
    cwd: config.root,
    env: config.env || process.env,
    timeoutMs: config.timeoutMs,
    context: config.context,
    label: config.label,
  });
}

function runCommand(command, args, options) {
  return new Promise((resolve) => {
    const startedAt = Date.now();
    const display = displayCommand(command, args);
    logEvent(options.context.logFile, `$ ${display}`);
    const child = childProcess.spawn(command, args, {
      cwd: options.cwd,
      env: options.env,
      windowsHide: true,
      detached: process.platform !== "win32",
    });
    let stdout = "";
    let stderr = "";
    let settled = false;
    let timedOut = false;
    let forceKillTimer = null;
    let finalSettleTimer = null;
    const maxCapture = 32 * 1024 * 1024;
    const append = (streamName, chunk) => {
      const text = chunk.toString();
      fs.appendFileSync(options.context.logFile, text);
      if (options.echo !== false) {
        if (streamName === "stdout") process.stdout.write(chunk);
        else process.stderr.write(chunk);
      }
      if (streamName === "stdout" && stdout.length < maxCapture) stdout += text.slice(0, maxCapture - stdout.length);
      if (streamName === "stderr" && stderr.length < maxCapture) stderr += text.slice(0, maxCapture - stderr.length);
    };
    const finish = (result) => {
      if (settled) return;
      settled = true;
      clearTimeout(timeout);
      if (forceKillTimer) clearTimeout(forceKillTimer);
      if (finalSettleTimer) clearTimeout(finalSettleTimer);
      const completed = {
        command,
        args,
        exitCode: result.exitCode,
        signal: result.signal || "",
        error: result.error || "",
        timedOut,
        startedAt,
        endedAt: Date.now(),
        stdout,
        stderr,
      };
      options.context.summary.commands.push(commandSummary(
        completed,
        options.context.repoRoot,
        options.context.logFile,
        options.label,
      ));
      writeSummary(options.context.runRoot, options.context.summary);
      logEvent(options.context.logFile, `[${options.label}] exit=${completed.exitCode} timeout=${timedOut}`);
      resolve(completed);
    };
    const timeout = setTimeout(() => {
      timedOut = true;
      terminateProcessTree(child);
      stopDeviceInstrumentation(options.env && options.env.ANDROID_SERIAL);
      forceKillTimer = setTimeout(() => {
        terminateProcessTree(child, true);
        finalSettleTimer = setTimeout(() => {
          child.stdout.removeAllListeners("data");
          child.stderr.removeAllListeners("data");
          child.stdout.destroy();
          child.stderr.destroy();
          child.unref();
          finish({
            exitCode: 1,
            signal: "SIGKILL",
            error: `Command did not close after timeout and forced process-tree termination: ${display}`,
          });
        }, 5_000);
      }, 5_000);
    }, options.timeoutMs);
    child.stdout.on("data", (chunk) => append("stdout", chunk));
    child.stderr.on("data", (chunk) => append("stderr", chunk));
    child.on("error", (error) => finish({ exitCode: 1, error: error.message }));
    child.on("close", (exitCode, signal) => finish({ exitCode: typeof exitCode === "number" ? exitCode : 1, signal }));
  });
}

function terminateProcessTree(child, force = false) {
  if (!child || !child.pid) return;
  try {
    if (process.platform === "win32") {
      const result = childProcess.spawnSync("taskkill.exe", ["/PID", String(child.pid), "/T", "/F"], {
        windowsHide: true,
        timeout: 30_000,
      });
      if (result.status !== 0 || result.error) child.kill("SIGKILL");
    } else {
      process.kill(-child.pid, force ? "SIGKILL" : "SIGTERM");
    }
  } catch (_) {
    try { child.kill(force ? "SIGKILL" : "SIGTERM"); } catch (_) { /* already exited */ }
  }
}

function stopDeviceInstrumentation(serial) {
  if (!serial) return;
  for (const packageName of [ANDROID_TEST_PACKAGE, HOST_PACKAGE]) {
    childProcess.spawnSync("adb", ["-s", serial, "shell", "am", "force-stop", packageName], {
      windowsHide: true,
      timeout: 30_000,
      encoding: "utf8",
    });
  }
}

function requireSuccessfulCommand(result, label) {
  if (result.exitCode !== 0 || result.timedOut) {
    throw new Error(`${label} failed (exit=${result.exitCode}, timedOut=${result.timedOut}).`);
  }
}

function commandSummary(result, repoRoot, logFile, label) {
  return {
    label,
    command: displayCommand(result.command, result.args),
    exitCode: result.exitCode,
    signal: result.signal,
    timedOut: result.timedOut,
    durationMs: result.endedAt - result.startedAt,
    error: result.error,
    logFile: relativePath(repoRoot, logFile),
  };
}

function logSyncResult(logFile, command, args, result, label = "apk-inspection") {
  logEvent(logFile, `$ ${displayCommand(command, args)}`);
  if (result.stdout) fs.appendFileSync(logFile, String(result.stdout));
  if (result.stderr) fs.appendFileSync(logFile, String(result.stderr));
  const timedOut = Boolean(result.error && result.error.code === "ETIMEDOUT");
  logEvent(logFile, `[${label}] exit=${result.status} timeout=${timedOut}`);
}

function findAapt(repoRoot) {
  return findAndroidBuildTool(
    repoRoot,
    "aapt",
    process.platform === "win32" ? "aapt.exe" : "aapt",
    "Android aapt is required to verify the runtime plugin APK package and native ABI.",
  );
}

function findApksigner(repoRoot) {
  return findAndroidBuildTool(
    repoRoot,
    "apksigner",
    process.platform === "win32" ? "apksigner.bat" : "apksigner",
    "Android apksigner is required to verify the runtime plugin APK signer provenance.",
  );
}

function findAndroidBuildTool(repoRoot, toolName, executableName, missingMessage) {
  const candidates = [];
  const sdkRoots = [
    process.env.ANDROID_SDK_ROOT,
    process.env.ANDROID_HOME,
    sdkRootFromLocalProperties(repoRoot),
    sdkRootFromAdb(),
  ]
    .filter(Boolean)
    .map((entry) => path.resolve(entry));
  for (const sdk of [...new Set(sdkRoots)]) {
    const buildTools = path.join(sdk, "build-tools");
    if (!fs.existsSync(buildTools)) continue;
    const versions = fs.readdirSync(buildTools, { withFileTypes: true })
      .filter((entry) => entry.isDirectory())
      .map((entry) => entry.name)
      .sort(compareVersionStrings)
      .reverse();
    versions.forEach((version) => candidates.push(path.join(buildTools, version, executableName)));
  }
  const found = candidates.find((entry) => fs.existsSync(entry));
  if (found) return found;
  const fallback = process.platform === "win32" && executableName.endsWith(".bat")
    ? `${toolName}.bat`
    : toolName;
  const invocation = commandInvocation(fallback, ["version"]);
  const probe = childProcess.spawnSync(invocation.command, invocation.args, {
    cwd: repoRoot,
    encoding: "utf8",
    windowsHide: true,
    timeout: 30_000,
    maxBuffer: 1024 * 1024,
  });
  if (probe.status === 0 && !probe.error) return fallback;
  throw new Error(missingMessage);
}

function sdkRootFromLocalProperties(repoRoot) {
  const file = path.join(repoRoot, "local.properties");
  if (!fs.existsSync(file)) return "";
  const line = fs.readFileSync(file, "utf8").split(/\r?\n/)
    .find((entry) => entry.trim().startsWith("sdk.dir="));
  return line
    ? line.trim().slice("sdk.dir=".length).replace(/\\:/g, ":").replace(/\\\\/g, "\\")
    : "";
}

function sdkRootFromAdb() {
  const probe = childProcess.spawnSync(
    process.platform === "win32" ? "where.exe" : "which",
    ["adb"],
    { encoding: "utf8", windowsHide: true, timeout: 30_000, maxBuffer: 1024 * 1024 },
  );
  if (probe.status !== 0 || probe.error) return "";
  const adb = String(probe.stdout || "").split(/\r?\n/).map((entry) => entry.trim()).find(Boolean);
  return adb ? path.dirname(path.dirname(adb)) : "";
}

function compareVersionStrings(left, right) {
  const a = left.split(/[^0-9]+/).map(Number);
  const b = right.split(/[^0-9]+/).map(Number);
  for (let index = 0; index < Math.max(a.length, b.length); index += 1) {
    const difference = (a[index] || 0) - (b[index] || 0);
    if (difference !== 0) return difference;
  }
  return left.localeCompare(right);
}

function matchLineValue(text, key) {
  const match = text.match(new RegExp(`^\\s*${escapeRegex(key)}=([^\\r\\n]*)`, "m"));
  return match ? match[1].trim() : "";
}

function xmlAttribute(attributes, name) {
  const match = attributes.match(new RegExp(`\\b${escapeRegex(name)}\\s*=\\s*(["'])([\\s\\S]*?)\\1`, "i"));
  return match ? decodeXml(match[2]) : "";
}

function numberXmlAttribute(attributes, name, fallback) {
  const raw = xmlAttribute(attributes, name);
  const value = Number(raw);
  return Number.isFinite(value) && value >= 0 ? value : fallback;
}

function decodeXml(value) {
  return String(value)
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&amp;/g, "&");
}

function escapeRegex(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function listFiles(root) {
  if (!fs.existsSync(root)) return [];
  const output = [];
  for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
    const file = path.join(root, entry.name);
    if (entry.isDirectory()) output.push(...listFiles(file));
    else if (entry.isFile()) output.push(file);
  }
  return output;
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

function gradleCommand(root) {
  return process.platform === "win32" ? path.join(root, "gradlew.bat") : path.join(root, "gradlew");
}

function commandInvocation(command, args) {
  if (process.platform !== "win32" || !command.toLowerCase().endsWith(".bat")) return { command, args };
  return { command: process.env.ComSpec || "cmd.exe", args: ["/d", "/c", "call", command, ...args] };
}

function displayCommand(command, args) {
  return [command, ...args]
    .map((value) => /\s/.test(String(value)) ? `"${String(value).replace(/"/g, '\\"')}"` : String(value))
    .join(" ");
}

function prepareRunRoot(runRoot) {
  if (fs.existsSync(runRoot) && fs.readdirSync(runRoot).length > 0) {
    throw new Error(`Run directory must be absent or empty: ${runRoot}`);
  }
  fs.mkdirSync(runRoot, { recursive: true });
}

function isInsideOrSame(root, candidate) {
  const relative = path.relative(path.resolve(root), path.resolve(candidate));
  return relative === "" || (!relative.startsWith(`..${path.sep}`) && relative !== ".." && !path.isAbsolute(relative));
}

function logEvent(logFile, message) {
  fs.mkdirSync(path.dirname(logFile), { recursive: true });
  fs.appendFileSync(logFile, `[${new Date().toISOString()}] ${message}\n`);
}

function writeSummary(runRoot, summary) {
  fs.writeFileSync(path.join(runRoot, "run.json"), JSON.stringify(summary, null, 2) + "\n");
  fs.writeFileSync(path.join(runRoot, "run.md"), renderSummary(summary));
}

function renderSummary(summary) {
  const lines = [
    "# R6 Device Evidence Runner",
    "",
    `- Status: \`${summary.decision.status}\``,
    `- Target: \`${summary.target}\``,
    `- Serial: \`${summary.serial}\``,
    `- Runtime ABI: \`${summary.requirement.runtimeProcessAbi}\``,
    `- Run token: \`${summary.runToken}\``,
    `- Validated: \`${summary.decision.validated}\``,
    `- Archived: \`${summary.decision.archived}\``,
    `- Run directory: \`${summary.runDir}\``,
    `- Plugin package: \`${summary.plugin && summary.plugin.packageName || "pending"}\``,
    `- Plugin service: \`${summary.plugin && summary.plugin.serviceClass || "pending"}\``,
    `- Plugin signer SHA-256: \`${summary.plugin && summary.plugin.signerSha256 || "pending"}\``,
    `- Plugin authorization: \`${summary.plugin && summary.plugin.authorizationState || "pending"}\``,
    `- Plugin required/authenticated/trusted: \`${summary.plugin
      ? [summary.plugin.required, summary.plugin.authenticated, summary.plugin.trusted].join("/")
      : "pending"}\``,
    "",
    "## Failures",
    "",
  ];
  if (summary.decision.failures.length === 0) lines.push("None.");
  else summary.decision.failures.forEach((failure) => lines.push(`- ${failure}`));
  return lines.join("\n") + "\n";
}

function artifactPathFromRoot(root, file) {
  return path.relative(root, file).replace(/\\/g, "/") || ".";
}

function relativePath(root, file) {
  return artifactPathFromRoot(root, file);
}

function sha256(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function timestampForPath(date) {
  return date.toISOString().replace(/[:.]/g, "-");
}

function boundedNumber(value, fallback, minimum, maximum, name) {
  const parsed = value === undefined ? fallback : Number(value);
  if (!Number.isFinite(parsed) || parsed < minimum || parsed > maximum) {
    throw new Error(`--${name} must be between ${minimum} and ${maximum}; got ${value}.`);
  }
  return Math.round(parsed);
}

function parseArgs(argv) {
  const options = {};
  const allowedValues = new Set([
    "target",
    "serial",
    "repo-root",
    "plugin-root",
    "plugin-apk",
    "run-dir",
    "gradle-timeout-ms",
    "plugin-build-timeout-ms",
  ]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      options.help = true;
      continue;
    }
    if (arg === "--no-archive") {
      options.noArchive = true;
      continue;
    }
    if (!arg.startsWith("--")) throw new Error(`Unexpected positional argument: ${arg}`);
    const equals = arg.indexOf("=");
    const rawKey = equals >= 0 ? arg.slice(2, equals) : arg.slice(2);
    if (!allowedValues.has(rawKey)) {
      throw new Error(`Unsupported option --${rawKey}. Status, force, selector, profile, duration, and run-token overrides are intentionally unavailable.`);
    }
    const value = equals >= 0 ? arg.slice(equals + 1) : argv[index + 1];
    if (equals < 0) index += 1;
    if (value === undefined || value === "" || value.startsWith("--")) throw new Error(`Missing value for --${rawKey}.`);
    const key = rawKey.replace(/-([a-z])/g, (_, letter) => letter.toUpperCase());
    if (options[key] !== undefined) throw new Error(`Duplicate option --${rawKey}.`);
    options[key] = value;
  }
  return options;
}

function printHelp() {
  console.log(`Usage: node tools/nodejs/r6/run-device-evidence.js --target TARGET --serial SERIAL [options]

Required production identity:
  --target TARGET                sony-arm64, sony-armeabi-v7a, second-oem-arm64, or emulator-x86_64.
  --serial SERIAL                Explicit adb serial; ANDROID_SERIAL alone is not accepted.

Artifact/source options:
  --plugin-root DIR              Runtime plugin source root (defaults to the sibling project).
  --plugin-apk FILE              Exact target-ABI plugin APK; otherwise a clean plugin build is produced.
  --repo-root DIR                AutoJs6 repository root.
  --run-dir DIR                  Empty directory for logs, staged evidence, and captured artifacts.
  --no-archive                  Validate the staged report without replacing device/<target>.json.

Operational timeouts:
  --gradle-timeout-ms MS         Main connected gate timeout (15 minutes..12 hours).
  --plugin-build-timeout-ms MS   Plugin build timeout (5 minutes..4 hours).

The runner fixes nodePlugin/plugin/node24_5, the target profile and packaged-smoke ABI,
builds and pushes a fresh target-ABI slim Inrt template to a run-token-derived exact path, verifies
its device hash before and after testing, cleans it up, runs 30 R5 warm samples and a
30-second smoke lifecycle with 2-second samples, and fixes the exact verifier selectors
plus a generated 64-character hexadecimal run token.
It accepts no status, force, selector, profile, duration, or run-token override.
`);
}

module.exports = {
  SELECTOR_DERIVATION,
  buildEvidenceGradleArgs,
  buildPackagedTemplateGradleArgs,
  derivePluginProvenance,
  deriveEvidence,
  parseArgs,
  parseApkSignerOutput,
  parseRequiredJUnit,
  parseRequiredJUnitXml,
  packagedTemplatePathForRunToken,
  selectAbiApk,
  selectFreshJUnit,
  snapshotJUnitFiles,
};
