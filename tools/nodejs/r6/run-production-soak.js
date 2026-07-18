#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const {
  SOAK_REQUIREMENTS,
  currentIdentity,
  evaluateSoakReport,
} = require("./verify-production-evidence.js");

const REPORT_SCHEMA = "autojs6-node-r6-persistent-plugin-soak-v1";
const RUNNER_SCHEMA = "autojs6-node-r6-persistent-plugin-soak-runner-v1";
const DEVICE_REPORT_DIR = "/sdcard/Download/AutoJs6/nodejs/r6-soak";
const HOST_PACKAGE = "org.autojs.autojs6";
const ANDROID_TEST_PACKAGE = "org.autojs.autojs6.test";
const TEST_SELECTOR = "org.autojs.autojs.engine.NodeR6PersistentPluginSoakInstrumentationTest#persistentRequiredPluginSoakProducesR6Evidence";
const MILLIS_PER_HOUR = 3_600_000;
const MAX_SAMPLE_INTERVAL_MS = 300_000;
const MIN_REQUIRED_SAMPLES = 4;
const CLOCK_BOUND_TOLERANCE_MS = 10_000;
const FRESHNESS_TOLERANCE_MS = 10_000;
const PRODUCTION_DURATION_MS = { quick: 8 * MILLIS_PER_HOUR, final: 24 * MILLIS_PER_HOUR };
const CANONICAL_REPORTS = {
  quick: "soak/quick-soak-8h.json",
  final: "soak/final-soak-24h.json",
};
const REQUIRED_SAMPLE_METRICS = [
  "servicePid",
  "processRuntimeGeneration",
  "executionSequence",
  "rssKb",
  "javaHeapKb",
  "nativeHeapKb",
  "threadCount",
  "fdCount",
  "nodeHeapUsedKb",
  "libuvHandleCount",
  "activeBridgeResourceCount",
];
const REQUIRED_GROWTH_METRICS = [
  "rssKb",
  "javaHeapKb",
  "nativeHeapKb",
  "threadCount",
  "fdCount",
  "nodeHeapUsedKb",
  "libuvHandleCount",
  "activeBridgeResourceCount",
];

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

  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const pluginRoot = resolvePluginRoot(repoRoot, options.pluginRoot);
  const smoke = Boolean(options.smoke);
  if (smoke && options.kind) throw new Error("--smoke cannot be combined with --kind.");
  const kind = smoke ? "smoke" : normalizeKind(options.kind || "quick");
  const durationMs = smoke
    ? boundedNumber(options.smokeDurationMs, 30_000, 5_000, 600_000, "smoke-duration-ms")
    : PRODUCTION_DURATION_MS[kind];
  const maximumSampleIntervalMs = smoke
    ? Math.max(1_000, Math.min(MAX_SAMPLE_INTERVAL_MS, Math.floor(durationMs / MIN_REQUIRED_SAMPLES)))
    : MAX_SAMPLE_INTERVAL_MS;
  const sampleIntervalMs = boundedNumber(
    options.sampleIntervalMs,
    Math.min(smoke ? 2_000 : MAX_SAMPLE_INTERVAL_MS, maximumSampleIntervalMs),
    1_000,
    maximumSampleIntervalMs,
    "sample-interval-ms",
  );
  const cycleDelayMs = boundedNumber(
    options.cycleDelayMs,
    smoke ? 500 : 30_000,
    0,
    Math.min(30_000, Math.floor(sampleIntervalMs / 2)),
    "cycle-delay-ms",
  );
  const livenessRecoverabilityEveryCycles = boundedNumber(
    options.livenessRecoverabilityEveryCycles,
    smoke ? 2 : 20,
    1,
    1_000_000,
    "liveness-recoverability-every-cycles",
  );
  const requiredSamples = minimumRequiredSamples(kind, durationMs, sampleIntervalMs);
  const serial = String(options.serial || process.env.ANDROID_SERIAL || "").trim();
  if (!serial) throw new Error("An explicit --serial or ANDROID_SERIAL is required.");
  const initialDeviceProps = adbDeviceProperties(serial, repoRoot);
  const pluginRuntimeAbi = initialDeviceProps.supportedAbis.find((abi) =>
    abi === "arm64-v8a" || abi === "x86_64",
  );
  if (!pluginRuntimeAbi) {
    throw new Error(
      `The R6 persistent-plugin soak requires arm64-v8a or x86_64, got ${initialDeviceProps.supportedAbis.join(",") || "none"}.`,
    );
  }

  const hostGit = gitIdentity(repoRoot);
  const pluginGit = gitIdentity(pluginRoot);
  if (!smoke) {
    if (!hostGit.clean) throw new Error("Production soak requires a clean host worktree.");
    if (!pluginGit.clean) throw new Error("Production soak requires a clean plugin worktree.");
  }

  const startedAtEpochMs = Date.now();
  const deviceStartedAtEpochMs = deviceEpochMs(serial, repoRoot);
  if (deviceStartedAtEpochMs <= 0) throw new Error("The device wall clock could not be captured before the R6 soak.");
  const runToken = crypto.randomBytes(32).toString("hex");
  const runId = `r6-${kind}-${timestampForPath(new Date(startedAtEpochMs))}`;
  const runRoot = path.resolve(
    options.runDir || path.join(repoRoot, "build/reports/nodejs/r6-soak-runs", runId),
  );
  if (!isInsideOrSame(repoRoot, runRoot)) {
    throw new Error(`--run-dir must stay inside the repository: ${runRoot}`);
  }
  prepareRunRoot(runRoot);
  const pullDir = path.join(runRoot, "device");
  const artifactDir = path.join(runRoot, "artifacts");
  fs.mkdirSync(pullDir, { recursive: true });
  fs.mkdirSync(artifactDir, { recursive: true });

  const summary = {
    schema: RUNNER_SCHEMA,
    generatedAt: new Date().toISOString(),
    mode: smoke ? "smoke_non_archivable" : "production",
    kind,
    runToken,
    serial,
    deviceStartedAtEpochMs,
    testSelector: TEST_SELECTOR,
    runDir: relativePath(repoRoot, runRoot),
    requested: {
      durationMs,
      durationHours: durationMs / MILLIS_PER_HOUR,
      sampleIntervalMs,
      minimumRequiredSamples: requiredSamples,
      cycleDelayMs,
      livenessRecoverabilityEveryCycles,
      releaseFlavor: "nodePlugin",
      backend: "plugin",
      pluginRequired: true,
      persistentRuntime: true,
      archive: !smoke && !options.noArchive,
    },
    sourceIdentity: { host: hostGit, plugin: pluginGit },
    pluginBuild: null,
    pluginInstall: null,
    gradle: null,
    pull: null,
    artifactCollection: [],
    candidate: null,
    archive: null,
    decision: { status: "running", archived: false, failures: [] },
  };
  writeSummary(runRoot, summary);

  console.log(`R6 persistent-plugin soak (${summary.mode})`);
  console.log(`- kind: ${kind}`);
  console.log(`- serial: ${serial}`);
  console.log(`- duration: ${durationMs / MILLIS_PER_HOUR}h`);
  console.log(`- run dir: ${runRoot}`);

  const env = { ...process.env, ANDROID_SERIAL: serial };
  const pluginBuildInvocation = commandInvocation(gradleCommand(pluginRoot), [
    "--console=plain",
    "--rerun-tasks",
    ":app:packageDebug",
  ]);
  const pluginBuild = await runCommand(pluginBuildInvocation.command, pluginBuildInvocation.args, {
    cwd: pluginRoot,
    env,
    timeoutMs: 7_200_000,
    logFile: path.join(runRoot, "plugin-build-output.txt"),
  });
  summary.pluginBuild = commandSummary(pluginBuild, repoRoot);
  if (pluginBuild.exitCode !== 0 || pluginBuild.timedOut) {
    failRunner(summary, runRoot, "The fresh runtime plugin build did not complete cleanly.");
    return;
  }
  const pluginBuildApk = selectPluginApk(pluginRoot, pluginRuntimeAbi);
  if (!pluginBuildApk || fs.statSync(pluginBuildApk).mtimeMs < pluginBuild.startedAt - 2_000) {
    failRunner(summary, runRoot, `No fresh ${pluginRuntimeAbi} runtime plugin APK was produced by this run.`);
    return;
  }
  const pluginGitAfterBuild = gitIdentity(pluginRoot);
  if (!smoke && (!pluginGitAfterBuild.clean || pluginGitAfterBuild.commit !== pluginGit.commit)) {
    failRunner(summary, runRoot, "Plugin source identity changed or became dirty during the fresh plugin build.");
    return;
  }
  summary.artifactCollection.push({
    kind: "fresh_plugin_build_apk",
    file: relativePath(repoRoot, pluginBuildApk),
    runtimeProcessAbi: pluginRuntimeAbi,
    sha256: sha256(pluginBuildApk),
    bytes: fs.statSync(pluginBuildApk).size,
    buildStartedAtEpochMs: pluginBuild.startedAt,
  });
  const pluginInstall = await runCommand("adb", ["-s", serial, "install", "-r", pluginBuildApk], {
    cwd: repoRoot,
    env,
    timeoutMs: 600_000,
    logFile: path.join(runRoot, "plugin-install-output.txt"),
  });
  summary.pluginInstall = commandSummary(pluginInstall, repoRoot);
  if (pluginInstall.exitCode !== 0 || pluginInstall.timedOut) {
    failRunner(summary, runRoot, "The fresh runtime plugin APK installation did not complete cleanly.");
    return;
  }
  writeSummary(runRoot, summary);

  const gradleTimeoutMs = boundedNumber(
    options.gradleTimeoutMs,
    durationMs + 3_600_000,
    durationMs + 300_000,
    durationMs + 7_200_000,
    "gradle-timeout-ms",
  );
  const gradleArgs = buildGradleArgs({
    kind,
    durationMs,
    runToken,
    smoke,
    pluginRoot,
    deviceProfile: options.deviceProfile || (smoke ? "smoke" : "sony"),
    sampleIntervalMs,
    cycleDelayMs,
    livenessRecoverabilityEveryCycles,
  });
  const gradleInvocation = commandInvocation(gradleCommand(repoRoot), gradleArgs);
  const junitResultRoot = path.join(repoRoot, "app/build/outputs/androidTest-results");
  const junitBefore = snapshotJUnitFiles(junitResultRoot);
  const gradle = await runCommand(gradleInvocation.command, gradleInvocation.args, {
    cwd: repoRoot,
    env,
    timeoutMs: gradleTimeoutMs,
    logFile: path.join(runRoot, "gradle-output.txt"),
  });
  summary.gradle = commandSummary(gradle, repoRoot);
  writeSummary(runRoot, summary);

  const pull = await runCommand("adb", ["-s", serial, "pull", `${DEVICE_REPORT_DIR}/.`, pullDir], {
    cwd: repoRoot,
    env,
    timeoutMs: boundedNumber(options.pullTimeoutMs, 300_000, 30_000, 900_000, "pull-timeout-ms"),
    logFile: path.join(runRoot, "adb-pull-output.txt"),
  });
  summary.pull = commandSummary(pull, repoRoot);
  const deviceFinishedAtEpochMs = deviceEpochMs(serial, repoRoot);
  summary.deviceFinishedAtEpochMs = deviceFinishedAtEpochMs;
  writeSummary(runRoot, summary);

  if (gradle.exitCode !== 0 || gradle.timedOut) {
    summary.decision.failures.push("Connected instrumentation did not complete cleanly.");
  }
  if (pull.exitCode !== 0 || pull.timedOut) {
    summary.decision.failures.push("R6 device report pull did not complete cleanly.");
  }
  if (deviceFinishedAtEpochMs < deviceStartedAtEpochMs) {
    summary.decision.failures.push("The device wall-clock bounds could not be captured consistently.");
  }

  const candidate = findCandidateReport(
    pullDir,
    kind,
    runToken,
    deviceStartedAtEpochMs,
    deviceFinishedAtEpochMs,
  );
  if (!candidate) {
    summary.decision.failures.push(`No fresh ${kind} R6 soak report was pulled from the device.`);
  } else {
    const raw = readJson(candidate.file);
    const rawCopy = path.join(runRoot, "raw-device-report.json");
    fs.copyFileSync(candidate.file, rawCopy);
    const enrichment = await enrichReport({
      repoRoot,
      pluginRoot,
      runRoot,
      artifactDir,
      serial,
      raw,
      env,
      startedAtEpochMs,
      hostGit,
      pluginGit,
      pluginBuildApk,
      initialDeviceProps,
      summary,
      expectedKind: kind,
      expectedDurationMs: durationMs,
      expectedRunToken: runToken,
      deviceStartedAtEpochMs,
      deviceFinishedAtEpochMs,
      minimumRequiredSamples: requiredSamples,
      junitBefore,
      gradleStartedAtEpochMs: gradle.startedAt,
    });
    if (!smoke) {
      const hostGitAfter = gitIdentity(repoRoot);
      const pluginGitAfter = gitIdentity(pluginRoot);
      summary.sourceIdentity.after = { host: hostGitAfter, plugin: pluginGitAfter };
      if (!hostGitAfter.clean || hostGitAfter.commit !== hostGit.commit) {
        enrichment.failures.push("Host source identity changed or became dirty during the production soak.");
      }
      if (!pluginGitAfter.clean || pluginGitAfter.commit !== pluginGit.commit) {
        enrichment.failures.push("Plugin source identity changed or became dirty during the production soak.");
      }
    }
    const enrichedFile = path.join(runRoot, "evidence-candidate.json");
    fs.writeFileSync(enrichedFile, JSON.stringify(enrichment.report, null, 2) + "\n");
    summary.candidate = {
      raw: relativePath(repoRoot, rawCopy),
      enriched: relativePath(repoRoot, enrichedFile),
      reportSha256: sha256(enrichedFile),
      status: enrichment.report.status,
      durationMs: enrichment.report.durationMs,
      durationHours: enrichment.report.durationHours,
      cycles: enrichment.report.cycles,
      samples: Array.isArray(enrichment.report.samples) ? enrichment.report.samples.length : 0,
      junit: enrichment.junit,
      enrichmentFailures: enrichment.failures,
    };
    summary.decision.failures.push(...enrichment.failures);

    if (
      !smoke &&
      !options.noArchive &&
      enrichment.junit.clean &&
      summary.decision.failures.length === 0
    ) {
      const archive = archiveProductionEvidence({
        repoRoot,
        pluginRoot,
        runRoot,
        kind,
        durationMs,
        runToken,
        minimumRequiredSamples: requiredSamples,
        startedAtEpochMs,
        enrichedFile,
      });
      summary.archive = archive;
      summary.decision.archived = archive.archived;
      summary.decision.failures.push(...archive.blockers);
    }
  }

  summary.decision.failures = [...new Set(summary.decision.failures)];
  summary.decision.status = summary.decision.failures.length === 0 ? "completed" : "failed";
  summary.generatedAt = new Date().toISOString();
  writeSummary(runRoot, summary);

  console.log(`R6 persistent-plugin soak runner: ${summary.decision.status}`);
  console.log(`Run report: ${path.join(runRoot, "run.json")}`);
  if (summary.decision.failures.length > 0) {
    summary.decision.failures.forEach((failure) => console.error(`- ${failure}`));
    process.exitCode = 1;
  }
}

function buildGradleArgs(config) {
  const args = [
    "--console=plain",
    "--rerun-tasks",
    ":app:connectedAppDebugAndroidTest",
    "-Pautojs.nodejs.releaseFlavor=nodePlugin",
    "-Pautojs.nodejs.backend=plugin",
    "-Pautojs.nodejs.runtimeSlot=node24_5",
    "-Pautojs.nodejs.embedded.persistentRuntime=true",
    "-Pautojs.gradle.build.number.auto.increment.enabled=false",
    "-Pautojs.gradle.build.time.update.enabled=false",
    "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true",
    `-Pautojs.nodejs.pluginProjectDir=${config.pluginRoot}`,
    `-Pautojs.nodejs.test.deviceProfile=${config.deviceProfile}`,
    `-Pandroid.testInstrumentationRunnerArguments.class=${TEST_SELECTOR}`,
    `-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.r6.soak.kind=${config.kind}`,
    `-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.r6.soak.runToken=${config.runToken}`,
  ];
  if (config.smoke) {
    args.push(`-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.r6.soak.durationMs=${config.durationMs}`);
  }
  const optionalArgs = {
    sampleIntervalMs: "autojs.nodejs.r6.soak.sampleIntervalMs",
    cycleDelayMs: "autojs.nodejs.r6.soak.cycleDelayMs",
    livenessRecoverabilityEveryCycles: "autojs.nodejs.r6.soak.livenessRecoverabilityEveryCycles",
  };
  Object.entries(optionalArgs).forEach(([key, instrumentationKey]) => {
    if (config[key] !== undefined) {
      args.push(`-Pandroid.testInstrumentationRunnerArguments.${instrumentationKey}=${nonNegativeNumber(config[key], key)}`);
    }
  });
  return args;
}

async function enrichReport(config) {
  const failures = [];
  const report = JSON.parse(JSON.stringify(config.raw));
  if (report.schema !== REPORT_SCHEMA) failures.push(`Device report schema must be ${REPORT_SCHEMA}.`);
  if (report.kind !== config.expectedKind) failures.push(`Device report kind must be ${config.expectedKind}.`);
  if (report.runToken !== config.expectedRunToken) failures.push("Device report runToken does not match this runner invocation.");
  const reportStartedAtEpochMs = Number(report.startedAtEpochMs);
  const reportEndedAtEpochMs = Number(report.endedAtEpochMs);
  if (
    !Number.isFinite(reportStartedAtEpochMs) ||
    reportStartedAtEpochMs < config.deviceStartedAtEpochMs - CLOCK_BOUND_TOLERANCE_MS ||
    reportStartedAtEpochMs > config.deviceFinishedAtEpochMs + CLOCK_BOUND_TOLERANCE_MS
  ) {
    failures.push("Device report startedAtEpochMs falls outside this runner invocation's device-clock bounds.");
  }
  if (
    !Number.isFinite(reportEndedAtEpochMs) ||
    reportEndedAtEpochMs < reportStartedAtEpochMs + config.expectedDurationMs ||
    reportEndedAtEpochMs > config.deviceFinishedAtEpochMs + CLOCK_BOUND_TOLERANCE_MS
  ) {
    failures.push("Device report endedAtEpochMs does not prove the requested wall-clock duration within this run.");
  }
  if (Number(report.durationMs) < config.expectedDurationMs) {
    failures.push(`Device report durationMs is below the requested ${config.expectedDurationMs}ms.`);
  }
  if (report.status !== "passed") failures.push(`Device report status must be passed, got ${report.status || "missing"}.`);
  if (!Array.isArray(report.failures) || report.failures.length !== 0) failures.push("Device report failures must be an empty array.");
  if (!Array.isArray(report.samples) || report.samples.length < config.minimumRequiredSamples) {
    failures.push(`Device report must contain at least ${config.minimumRequiredSamples} samples.`);
  }
  const configuredSampleIntervalMs = Number(report.configuration && report.configuration.sampleIntervalMs);
  if (
    !Number.isFinite(configuredSampleIntervalMs) ||
    configuredSampleIntervalMs < 1_000 ||
    configuredSampleIntervalMs > MAX_SAMPLE_INTERVAL_MS
  ) {
    failures.push(`Device report sampleIntervalMs must be between 1000 and ${MAX_SAMPLE_INTERVAL_MS}.`);
  }
  if (Number(report.configuration && report.configuration.minimumRequiredSamples) !== config.minimumRequiredSamples) {
    failures.push("Device report minimumRequiredSamples does not match the runner requirement.");
  }
  (report.samples || []).forEach((sample, index) => {
    REQUIRED_SAMPLE_METRICS.forEach((metric) => {
      if (!sample || !Number.isFinite(sample[metric])) failures.push(`Sample ${index} lacks numeric ${metric}.`);
    });
  });
  const analysis = report.analysis || {};
  ["monotonicLeak", "orphanProcess", "unrecoverableRuntime", "unrecoverableSlot"].forEach((flag) => {
    if (analysis[flag] !== false) failures.push(`analysis.${flag} must be false.`);
  });
  if (analysis.processRuntimeReused !== true) failures.push("Device report did not prove process runtime reuse.");
  if (analysis.freshExecutionState !== true) failures.push("Device report did not prove fresh execution state.");
  if (
    analysis.livenessRecoverabilityChecksPassed !== true ||
    Number(analysis.livenessRecoverabilityChecks) <= 0
  ) {
    failures.push("Device report did not prove periodic liveness/recoverability checks.");
  }
  validateRuntimeContinuity(report, failures);
  validateGrowthAnalysis(analysis.growth, report.samples, failures);
  const cleanup = report.cleanup || {};
  if (
    cleanup.complete !== true ||
    cleanup.propertiesRestored !== true ||
    cleanup.pluginHostInvalidated !== true ||
    cleanup.workDirectoryDeleted !== true
  ) {
    failures.push("Device report did not prove complete post-soak cleanup.");
  }

  const version = readVersion(path.join(config.repoRoot, "version.properties"));
  report.generatedAt = new Date().toISOString();
  report.identity = {
    hostCommit: config.hostGit.commit,
    pluginCommit: config.pluginGit.commit,
    versionName: version.name,
    versionBuild: version.build,
    lifecycleModel: "persistent_process_global_runtime",
    releaseFlavor: "nodePlugin",
    backend: "plugin",
    pluginRequired: true,
    runtimeSlot: "node24_5",
    nodeVersion: "24.5.0",
    hostTreeClean: config.hostGit.clean,
    pluginTreeClean: config.pluginGit.clean,
  };

  const deviceProps = config.initialDeviceProps || adbDeviceProperties(config.serial, config.repoRoot);
  const plugin = report.plugin || {};
  if (String(plugin.nodeVersion || "").replace(/^v/, "") !== "24.5.0") {
    failures.push(`Plugin Node version must be 24.5.0, got ${plugin.nodeVersion || "missing"}.`);
  }
  if (String(plugin.runtimeSlot || "") !== "node24_5") {
    failures.push(`Plugin runtime slot must be node24_5, got ${plugin.runtimeSlot || "missing"}.`);
  }
  if (deviceProps.isEmulator) failures.push("Production soak evidence requires a physical device.");
  if (!deviceProps.supportedAbis.includes(String(plugin.runtimeProcessAbi || ""))) {
    failures.push(`Plugin runtime ABI ${plugin.runtimeProcessAbi || "missing"} is not supported by the device.`);
  }
  report.device = {
    ...(report.device || {}),
    serial: config.serial,
    manufacturer: deviceProps.manufacturer || (report.device && report.device.manufacturer) || "",
    model: deviceProps.model || (report.device && report.device.model) || "",
    apiLevel: Number(deviceProps.apiLevel || (report.device && report.device.apiLevel) || 0),
    supportedAbis: deviceProps.supportedAbis,
    runtimeProcessAbi: plugin.runtimeProcessAbi || "",
    isEmulator: deviceProps.isEmulator,
  };

  const pluginPackage = String(plugin.packageName || "");
  const pluginApk = await pullInstalledApk(config, pluginPackage, "installed-plugin.apk");
  const installedHostApk = await pullInstalledApk(config, HOST_PACKAGE, "installed-host.apk");
  const installedAndroidTestApk = await pullInstalledApk(
    config,
    ANDROID_TEST_PACKAGE,
    "installed-host-app-androidTest.apk",
  );
  const hostApk = selectHostApk(config.repoRoot, plugin.runtimeProcessAbi);
  const pluginBuildApk = config.pluginBuildApk || null;
  config.summary.artifactCollection.push(
    {
      kind: "host_build_apk",
      file: hostApk ? relativePath(config.repoRoot, hostApk) : "",
      runtimeProcessAbi: plugin.runtimeProcessAbi || "",
    },
    pluginApk.summary,
    installedHostApk.summary,
    installedAndroidTestApk.summary,
    {
      kind: "plugin_build_apk",
      file: pluginBuildApk ? relativePath(config.repoRoot, pluginBuildApk) : "",
      runtimeProcessAbi: plugin.runtimeProcessAbi || "",
    },
  );

  const androidTestApk = newestFile(
    path.join(config.repoRoot, "app/build/outputs/apk/androidTest"),
    (file) => file.endsWith("-androidTest.apk"),
  );
  if (hostApk) assertFreshArtifact(hostApk, config.gradleStartedAtEpochMs, "Host APK");
  if (androidTestApk) assertFreshArtifact(androidTestApk, config.gradleStartedAtEpochMs, "Android test APK");
  const junitXml = selectFreshSoakJUnit(
    path.join(config.repoRoot, "app/build/outputs/androidTest-results"),
    config.junitBefore,
    config.gradleStartedAtEpochMs,
  );
  const junit = evaluateJunitXml(junitXml);
  if (!androidTestApk) failures.push("The connected run Android test APK could not be located.");
  if (!junitXml) failures.push("The R6 soak JUnit XML could not be located.");
  if (junitXml && !junit.clean) {
    failures.push(...junit.blockers.map((blocker) => `R6 soak JUnit validation failed: ${blocker}`));
  }
  if (!hostApk) failures.push(`The host APK matching runtime ABI ${plugin.runtimeProcessAbi || "missing"} could not be located.`);
  if (!installedHostApk.file) failures.push(`Installed host APK could not be captured for ${HOST_PACKAGE}.`);
  if (!installedAndroidTestApk.file) {
    failures.push(`Installed Android test APK could not be captured for ${ANDROID_TEST_PACKAGE}.`);
  }
  if (!pluginApk.file) failures.push(`Installed plugin APK could not be captured for ${pluginPackage || "missing package"}.`);
  if (!pluginBuildApk) failures.push(`The plugin build APK matching runtime ABI ${plugin.runtimeProcessAbi || "missing"} could not be located.`);
  if (hostApk && fs.statSync(hostApk).mtimeMs < Date.parse(config.hostGit.commitTime || 0)) {
    failures.push("The host APK predates the captured host commit.");
  }
  if (androidTestApk && fs.statSync(androidTestApk).mtimeMs < Date.parse(config.hostGit.commitTime || 0)) {
    failures.push("The Android test APK predates the captured host commit.");
  }
  if (pluginBuildApk && fs.statSync(pluginBuildApk).mtimeMs < Date.parse(config.pluginGit.commitTime || 0)) {
    failures.push("The plugin build APK predates the captured plugin commit.");
  }
  if (pluginApk.file && pluginBuildApk && sha256(pluginApk.file) !== sha256(pluginBuildApk)) {
    failures.push("The installed runtime plugin APK does not match the current plugin build artifact.");
  }
  if (installedHostApk.file && hostApk && sha256(installedHostApk.file) !== sha256(hostApk)) {
    failures.push("The installed host APK does not match the current host build artifact.");
  }
  if (installedAndroidTestApk.file && androidTestApk &&
      sha256(installedAndroidTestApk.file) !== sha256(androidTestApk)) {
    failures.push("The installed Android test APK does not match the current Android test build artifact.");
  }

  const hostBuildCopy = hostApk ? copyArtifact(hostApk, config.artifactDir, "host-build.apk") : null;
  const androidTestCopy = androidTestApk
    ? copyArtifact(androidTestApk, config.artifactDir, "host-app-androidTest.apk")
    : null;
  const pluginBuildCopy = pluginBuildApk
    ? copyArtifact(pluginBuildApk, config.artifactDir, "requested-runtime-plugin.apk")
    : null;
  const junitCopy = junitXml ? copyArtifact(junitXml, config.artifactDir, "r6-soak-junit.xml") : null;

  report.artifacts = {
    hostApkSha256: installedHostApk.file ? sha256(installedHostApk.file) : "",
    androidTestApkSha256: installedAndroidTestApk.file ? sha256(installedAndroidTestApk.file) : "",
    pluginApkSha256: pluginApk.file ? sha256(pluginApk.file) : "",
    junitXmlSha256: junitCopy ? sha256(junitCopy) : "",
    hostApkBytes: installedHostApk.file ? fs.statSync(installedHostApk.file).size : 0,
    androidTestApkBytes: installedAndroidTestApk.file ? fs.statSync(installedAndroidTestApk.file).size : 0,
    pluginApkBytes: pluginApk.file ? fs.statSync(pluginApk.file).size : 0,
    junitXmlBytes: junitCopy ? fs.statSync(junitCopy).size : 0,
    hostApkPath: installedHostApk.file ? relativePath(config.repoRoot, installedHostApk.file) : "",
    androidTestApkPath: installedAndroidTestApk.file
      ? relativePath(config.repoRoot, installedAndroidTestApk.file)
      : "",
    pluginApkPath: pluginApk.file ? relativePath(config.repoRoot, pluginApk.file) : "",
    junitXmlPath: junitCopy ? relativePath(config.repoRoot, junitCopy) : "",
    hostBuildApkSha256: hostBuildCopy ? sha256(hostBuildCopy) : "",
    hostBuildApkBytes: hostBuildCopy ? fs.statSync(hostBuildCopy).size : 0,
    hostBuildApkPath: hostBuildCopy ? relativePath(config.repoRoot, hostBuildCopy) : "",
    installedHostMatchesFreshBuild: Boolean(
      installedHostApk.file && hostBuildCopy && sha256(installedHostApk.file) === sha256(hostBuildCopy)
    ),
    androidTestBuildApkSha256: androidTestCopy ? sha256(androidTestCopy) : "",
    androidTestBuildApkBytes: androidTestCopy ? fs.statSync(androidTestCopy).size : 0,
    androidTestBuildApkPath: androidTestCopy ? relativePath(config.repoRoot, androidTestCopy) : "",
    installedAndroidTestMatchesFreshBuild: Boolean(
      installedAndroidTestApk.file && androidTestCopy &&
      sha256(installedAndroidTestApk.file) === sha256(androidTestCopy)
    ),
    requestedPluginApkSha256: pluginBuildCopy ? sha256(pluginBuildCopy) : "",
    requestedPluginApkBytes: pluginBuildCopy ? fs.statSync(pluginBuildCopy).size : 0,
    requestedPluginApkPath: pluginBuildCopy ? relativePath(config.repoRoot, pluginBuildCopy) : "",
    installedPluginMatchesRequested: Boolean(
      pluginApk.file && pluginBuildCopy && sha256(pluginApk.file) === sha256(pluginBuildCopy)
    ),
    junit,
  };
  return { report, failures, junit };
}

function validateGrowthAnalysis(growth, samples, failures) {
  const metrics = growth && Array.isArray(growth.metrics) ? growth.metrics : [];
  const names = metrics.map((metric) => String(metric && metric.name || ""));
  if (metrics.length !== REQUIRED_GROWTH_METRICS.length || new Set(names).size !== REQUIRED_GROWTH_METRICS.length) {
    failures.push(`analysis.growth must contain exactly ${REQUIRED_GROWTH_METRICS.length} unique metrics.`);
  }
  REQUIRED_GROWTH_METRICS.forEach((name) => {
    const metric = metrics.find((entry) => entry && entry.name === name);
    if (!metric) {
      failures.push(`analysis.growth is missing ${name}.`);
      return;
    }
    if (Number(metric.sampleCount) !== (Array.isArray(samples) ? samples.length : 0)) {
      failures.push(`analysis.growth ${name} sampleCount does not match samples.`);
    }
    ["baseline", "final", "delta", "threshold"].forEach((field) => {
      if (!Number.isFinite(metric[field])) failures.push(`analysis.growth ${name} lacks numeric ${field}.`);
    });
    if (typeof metric.monotonic !== "boolean" || typeof metric.growthDetected !== "boolean") {
      failures.push(`analysis.growth ${name} lacks boolean detection fields.`);
    }
    const sampleValues = Array.isArray(samples) ? samples.map((sample) => sample && sample[name]) : [];
    if (
      !Array.isArray(metric.values) ||
      metric.values.length !== sampleValues.length ||
      metric.values.some((value, index) => !Number.isFinite(value) || value !== sampleValues[index])
    ) {
      failures.push(`analysis.growth ${name} values do not match samples.`);
    }
  });
}

function validateRuntimeContinuity(report, failures) {
  const cycles = report && report.cycles;
  const runs = report && Array.isArray(report.runs) ? report.runs : [];
  const samples = report && Array.isArray(report.samples) ? report.samples : [];
  if (!Number.isInteger(cycles) || cycles <= 0) failures.push("Device report cycles must be a positive integer.");
  if (runs.length !== cycles) failures.push(`Device report runs length ${runs.length} must equal cycles ${cycles}.`);

  let stablePid = null;
  let stableSessionGeneration = null;
  let stableRuntimeGeneration = null;
  let previousSequence = null;
  runs.forEach((run, index) => {
    if (!run || run.cycle !== index + 1) failures.push(`Run ${index} cycle is not contiguous.`);
    for (const field of ["servicePid", "pluginSessionGeneration", "processRuntimeGeneration", "executionSequence"]) {
      if (!run || !Number.isInteger(run[field]) || run[field] <= 0) failures.push(`Run ${index} ${field} must be a positive integer.`);
    }
    if (!run || run.processRuntimeInitCount !== 1) failures.push(`Run ${index} processRuntimeInitCount must be 1.`);
    if (!run || run.activeBridgeResourceCount !== 0) failures.push(`Run ${index} retained bridge resources.`);
    if (!run || run.freshExecutionState !== true) failures.push(`Run ${index} did not prove fresh execution state.`);
    if (index > 0 && (!run || run.runtimeReused !== true)) failures.push(`Run ${index} did not reuse the process runtime.`);
    if (index === 0 && run) {
      stablePid = run.servicePid;
      stableSessionGeneration = run.pluginSessionGeneration;
      stableRuntimeGeneration = run.processRuntimeGeneration;
    } else if (run) {
      if (run.servicePid !== stablePid) failures.push(`Run ${index} changed servicePid.`);
      if (run.pluginSessionGeneration !== stableSessionGeneration) failures.push(`Run ${index} changed pluginSessionGeneration.`);
      if (run.processRuntimeGeneration !== stableRuntimeGeneration) failures.push(`Run ${index} changed processRuntimeGeneration.`);
      if (run.executionSequence !== previousSequence + 1) failures.push(`Run ${index} executionSequence did not advance exactly once.`);
    }
    previousSequence = run && run.executionSequence;
  });

  let previousSampleCycle = 0;
  samples.forEach((sample, index) => {
    if (!sample || !Number.isInteger(sample.cycle) || sample.cycle <= previousSampleCycle || sample.cycle > cycles) {
      failures.push(`Sample ${index} cycle is not strictly increasing within the run.`);
      return;
    }
    const matchingRun = runs[sample.cycle - 1];
    for (const field of [
      "servicePid",
      "pluginSessionGeneration",
      "processRuntimeGeneration",
      "processRuntimeInitCount",
      "executionSequence",
    ]) {
      if (!matchingRun || sample[field] !== matchingRun[field]) {
        failures.push(`Sample ${index} ${field} does not match its cycle record.`);
      }
    }
    if (sample.freshExecutionState !== true) failures.push(`Sample ${index} did not prove fresh execution state.`);
    if (sample.activeBridgeResourceCount !== 0) failures.push(`Sample ${index} retained bridge resources.`);
    previousSampleCycle = sample.cycle;
  });
}

function evaluateJunitXml(file) {
  const blockers = [];
  if (!file || !fs.existsSync(file)) {
    return {
      clean: false,
      targetClass: TEST_SELECTOR.split("#")[0],
      targetMethod: TEST_SELECTOR.split("#")[1],
      targetTestCases: 0,
      suite: null,
      blockers: ["JUnit XML is missing."],
    };
  }

  const xml = safeRead(file);
  const targetClass = TEST_SELECTOR.split("#")[0];
  const targetMethod = TEST_SELECTOR.split("#")[1];
  const testCases = [];
  const testCasePattern = /<testcase\b([^>]*?)(?:\/\s*>|>([\s\S]*?)<\/testcase\s*>)/gi;
  let match;
  while ((match = testCasePattern.exec(xml)) !== null) {
    const attributes = match[1] || "";
    const body = match[2] || "";
    testCases.push({
      className: xmlAttribute(attributes, "classname"),
      name: xmlAttribute(attributes, "name"),
      failure: /<failure\b/i.test(body),
      error: /<error\b/i.test(body),
      skipped: /<skipped\b/i.test(body),
    });
  }
  const targetCases = testCases.filter(
    (testCase) => testCase.className === targetClass && testCase.name === targetMethod,
  );
  const rootMatch = xml.match(/<testsuites\b([^>]*)>/i);
  const suite = rootMatch
    ? {
        tests: numericXmlAttribute(rootMatch[1], "tests"),
        failures: numericXmlAttribute(rootMatch[1], "failures"),
        errors: numericXmlAttribute(rootMatch[1], "errors"),
        skipped: numericXmlAttribute(rootMatch[1], "skipped"),
      }
    : null;

  if (targetCases.length !== 1) {
    blockers.push(`Expected exactly one target testcase, found ${targetCases.length}.`);
  }
  if (targetCases.some((testCase) => testCase.failure)) blockers.push("Target testcase contains a failure.");
  if (targetCases.some((testCase) => testCase.error)) blockers.push("Target testcase contains an error.");
  if (targetCases.some((testCase) => testCase.skipped)) blockers.push("Target testcase was skipped.");
  if (!suite) {
    blockers.push("JUnit XML lacks the testsuites summary.");
  } else {
    if (suite.tests !== 1) blockers.push(`JUnit suite must contain exactly one test, got ${suite.tests}.`);
    if (suite.failures !== 0) blockers.push(`JUnit suite reports ${suite.failures} failure(s).`);
    if (suite.errors !== 0) blockers.push(`JUnit suite reports ${suite.errors} error(s).`);
    if (suite.skipped !== 0) blockers.push(`JUnit suite reports ${suite.skipped} skipped test(s).`);
  }
  if (/Test run failed to complete|Instrumentation run failed|Process crashed/i.test(xml)) {
    blockers.push("JUnit XML reports an incomplete or crashed instrumentation run.");
  }

  return {
    clean: blockers.length === 0,
    targetClass,
    targetMethod,
    targetTestCases: targetCases.length,
    suite,
    blockers,
  };
}

function xmlAttribute(attributes, name) {
  const match = attributes.match(new RegExp(`\\b${name}\\s*=\\s*["']([^"']*)["']`, "i"));
  return match ? match[1] : "";
}

function numericXmlAttribute(attributes, name) {
  const raw = xmlAttribute(attributes, name);
  if (!raw.trim()) return null;
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
}

async function pullInstalledApk(config, packageName, fileName) {
  if (!/^[A-Za-z0-9_.]+$/.test(packageName)) {
    return { file: null, summary: { packageName, error: "invalid or missing package name" } };
  }
  const query = runSync(config.repoRoot, ["adb", "-s", config.serial, "shell", "pm", "path", packageName]);
  const paths = query.stdout
    .split(/\r?\n/)
    .map((line) => line.trim().replace(/^package:/, ""))
    .filter((line) => line.startsWith("/"));
  const remote = paths.find((value) => value.endsWith("/base.apk")) || paths[0];
  if (!remote) {
    return { file: null, summary: { packageName, query: query.stdout, error: query.stderr || "pm path returned no APK" } };
  }
  const local = path.join(config.artifactDir, fileName);
  const pull = await runCommand("adb", ["-s", config.serial, "pull", remote, local], {
    cwd: config.repoRoot,
    env: config.env,
    timeoutMs: 300_000,
    logFile: path.join(config.runRoot, `adb-pull-${fileName}.txt`),
  });
  return {
    file: pull.exitCode === 0 && fs.existsSync(local) ? local : null,
    summary: { packageName, remote, ...commandSummary(pull, config.repoRoot) },
  };
}

function archiveProductionEvidence(config) {
  const blockers = [];
  const json = readJson(config.enrichedFile);
  const minimum = PRODUCTION_DURATION_MS[config.kind];
  const runnerElapsedMs = Date.now() - config.startedAtEpochMs;
  if (config.durationMs !== minimum) blockers.push("Runner production duration did not equal the canonical requirement.");
  if (runnerElapsedMs < minimum) blockers.push(`Runner elapsed ${runnerElapsedMs}ms is below required ${minimum}ms.`);
  if (Number(json.durationMs) < minimum) blockers.push(`Device duration ${json.durationMs}ms is below required ${minimum}ms.`);
  if (json.kind !== config.kind) blockers.push(`Device kind ${json.kind} does not match ${config.kind}.`);
  if (json.runToken !== config.runToken) blockers.push("Device report runToken does not match this runner invocation.");
  if (json.schema !== REPORT_SCHEMA) blockers.push(`Schema must be ${REPORT_SCHEMA}.`);
  if (json.status !== "passed") blockers.push(`Device report status is ${json.status}.`);
  if (!Array.isArray(json.samples) || json.samples.length < config.minimumRequiredSamples) {
    blockers.push(`At least ${config.minimumRequiredSamples} samples are required.`);
  }
  (json.samples || []).forEach((sample, index) => {
    REQUIRED_SAMPLE_METRICS.forEach((metric) => {
      if (!Number.isFinite(Number(sample && sample[metric]))) blockers.push(`Sample ${index} lacks numeric ${metric}.`);
    });
  });
  const analysis = json.analysis || {};
  ["monotonicLeak", "orphanProcess", "unrecoverableRuntime", "unrecoverableSlot"].forEach((flag) => {
    if (analysis[flag] !== false) blockers.push(`analysis.${flag} must be false.`);
  });
  if (analysis.processRuntimeReused !== true) blockers.push("Process runtime reuse was not proven.");
  if (analysis.freshExecutionState !== true) blockers.push("Fresh execution state was not proven.");
  if (
    analysis.livenessRecoverabilityChecksPassed !== true ||
    Number(analysis.livenessRecoverabilityChecks) <= 0
  ) {
    blockers.push("Periodic liveness/recoverability checks were not proven.");
  }
  validateGrowthAnalysis(analysis.growth, json.samples, blockers);

  const requirement = SOAK_REQUIREMENTS.find((entry) => entry.kind === config.kind);
  const stageRoot = path.join(config.runRoot, "archive-validation");
  const staged = path.join(stageRoot, requirement.relative);
  fs.mkdirSync(path.dirname(staged), { recursive: true });
  fs.copyFileSync(config.enrichedFile, staged);
  const evaluation = evaluateSoakReport(
    stageRoot,
    requirement,
    currentIdentity(config.repoRoot, config.pluginRoot),
    config.repoRoot,
  );
  blockers.push(...evaluation.blockers);

  const target = path.join(config.repoRoot, "build/reports/nodejs/r6-evidence", CANONICAL_REPORTS[config.kind]);
  let archived = false;
  if (blockers.length === 0) {
    fs.mkdirSync(path.dirname(target), { recursive: true });
    const temporary = `${target}.tmp-${config.runToken.slice(0, 12)}`;
    const backup = path.join(config.runRoot, `previous-${path.basename(target)}`);
    fs.copyFileSync(config.enrichedFile, temporary);
    if (sha256(temporary) !== sha256(config.enrichedFile)) {
      blockers.push("Temporary canonical soak evidence copy failed its hash check.");
      fs.rmSync(temporary, { force: true });
    } else {
      let movedPrevious = false;
      try {
        if (fs.existsSync(target)) {
          fs.renameSync(target, backup);
          movedPrevious = true;
        }
        fs.renameSync(temporary, target);
        archived = sha256(target) === sha256(config.enrichedFile);
        if (!archived) blockers.push("Canonical soak evidence failed its post-write hash check.");
      } catch (error) {
        blockers.push(`Canonical soak evidence update failed: ${error && error.message ? error.message : error}.`);
        if (!fs.existsSync(target) && movedPrevious && fs.existsSync(backup)) {
          fs.renameSync(backup, target);
        }
        if (fs.existsSync(temporary)) fs.rmSync(temporary, { force: true });
      }
    }
  }
  return {
    target: relativePath(config.repoRoot, target),
    runnerElapsedMs,
    requiredDurationMs: minimum,
    evaluation,
    blockers: [...new Set(blockers)],
    archived,
  };
}

function findCandidateReport(root, kind, runToken, startedAtEpochMs, finishedAtEpochMs) {
  return listFiles(root)
    .filter((file) => path.basename(file).startsWith(`r6-persistent-plugin-soak-${kind}-`) && file.endsWith(".json"))
    .map((file) => {
      try {
        const json = readJson(file);
        return { file, startedAtEpochMs: Number(json.startedAtEpochMs || 0), json };
      } catch (_) {
        return null;
      }
    })
    .filter(Boolean)
    .filter((entry) =>
      entry.json.schema === REPORT_SCHEMA &&
      entry.json.kind === kind &&
      entry.json.runToken === runToken
    )
    .filter((entry) =>
      entry.startedAtEpochMs >= startedAtEpochMs - CLOCK_BOUND_TOLERANCE_MS &&
      entry.startedAtEpochMs <= finishedAtEpochMs + CLOCK_BOUND_TOLERANCE_MS
    )
    .sort((left, right) => right.startedAtEpochMs - left.startedAtEpochMs)[0] || null;
}

function minimumRequiredSamples(kind, durationMs, sampleIntervalMs) {
  return kind === "smoke"
    ? MIN_REQUIRED_SAMPLES
    : Math.max(MIN_REQUIRED_SAMPLES, Math.floor(durationMs / sampleIntervalMs) + 1);
}

function adbDeviceProperties(serial, cwd) {
  const getprop = (name) => {
    const result = runSync(cwd, ["adb", "-s", serial, "shell", "getprop", name]);
    if (result.status !== 0) throw new Error(`adb getprop ${name} failed: ${result.stderr || result.stdout}`);
    return result.stdout.trim();
  };
  const manufacturer = getprop("ro.product.manufacturer");
  const model = getprop("ro.product.model");
  const apiLevel = getprop("ro.build.version.sdk");
  const abiList = getprop("ro.product.cpu.abilist");
  const qemu = getprop("ro.kernel.qemu");
  const bootQemu = getprop("ro.boot.qemu");
  const product = getprop("ro.product.name");
  const hardware = getprop("ro.hardware");
  const fingerprint = getprop("ro.build.fingerprint");
  if (!manufacturer || !model || !apiLevel || !abiList || !fingerprint) {
    throw new Error("Device identity getprop values are incomplete.");
  }
  return {
    manufacturer,
    model,
    apiLevel,
    supportedAbis: abiList.split(",").map((value) => value.trim()).filter(Boolean),
    isEmulator: qemu === "1" || bootQemu === "1" ||
      /^(sdk|generic)|emulator/i.test(product) ||
      /goldfish|ranchu|qemu/i.test(hardware) ||
      /generic|emulator|sdk_gphone/i.test(fingerprint),
    fingerprint,
    hardware,
  };
}

function deviceEpochMs(serial, cwd) {
  const result = runSync(cwd, ["adb", "-s", serial, "shell", "date", "+%s"]);
  if (result.status !== 0) return 0;
  const seconds = Number(result.stdout.trim());
  return Number.isFinite(seconds) && seconds > 0 ? seconds * 1000 : 0;
}

function runCommand(command, args, options) {
  return new Promise((resolve) => {
    const startedAt = Date.now();
    fs.mkdirSync(path.dirname(options.logFile), { recursive: true });
    const log = fs.createWriteStream(options.logFile, { flags: "a" });
    log.write(`$ ${displayCommand(command, args)}\n`);
    const child = childProcess.spawn(command, args, {
      cwd: options.cwd,
      env: options.env,
      windowsHide: true,
      detached: process.platform !== "win32",
    });
    let settled = false;
    let timedOut = false;
    let forceKillTimer = null;
    let finalSettleTimer = null;
    const finish = (result) => {
      if (settled) return;
      settled = true;
      clearTimeout(timeout);
      if (forceKillTimer) clearTimeout(forceKillTimer);
      if (finalSettleTimer) clearTimeout(finalSettleTimer);
      log.end();
      resolve({ command, args, startedAt, endedAt: Date.now(), timedOut, logFile: options.logFile, ...result });
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
            error: `Command did not close after timeout and forced process-tree termination: ${displayCommand(command, args)}`,
          });
        }, 5_000);
      }, 5_000);
    }, options.timeoutMs);
    child.stdout.on("data", (chunk) => { process.stdout.write(chunk); log.write(chunk); });
    child.stderr.on("data", (chunk) => { process.stderr.write(chunk); log.write(chunk); });
    child.on("error", (error) => finish({ exitCode: 1, signal: "", error: error.message }));
    child.on("close", (exitCode, signal) => finish({ exitCode: typeof exitCode === "number" ? exitCode : 1, signal: signal || "" }));
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
  for (const packageName of ["org.autojs.autojs6.test", HOST_PACKAGE]) {
    childProcess.spawnSync("adb", ["-s", serial, "shell", "am", "force-stop", packageName], {
      windowsHide: true,
      timeout: 30_000,
      encoding: "utf8",
    });
  }
}

function runSync(cwd, command) {
  const result = childProcess.spawnSync(command[0], command.slice(1), {
    cwd,
    encoding: "utf8",
    windowsHide: true,
    timeout: 60_000,
    maxBuffer: 8 * 1024 * 1024,
  });
  const error = result.error && result.error.message ? result.error.message : "";
  return {
    status: typeof result.status === "number" ? result.status : 1,
    stdout: String(result.stdout || ""),
    stderr: [String(result.stderr || ""), error].filter(Boolean).join("\n"),
  };
}

function commandSummary(result, repoRoot) {
  return {
    command: displayCommand(result.command, result.args),
    exitCode: result.exitCode,
    signal: result.signal,
    timedOut: result.timedOut,
    durationMs: result.endedAt - result.startedAt,
    logFile: relativePath(repoRoot, result.logFile),
    error: result.error || "",
  };
}

function gitIdentity(root) {
  if (!root || !fs.existsSync(root)) return { root: root || "", commit: "", clean: false, commitTime: "" };
  const commit = runSync(root, ["git", "rev-parse", "HEAD"]).stdout.trim();
  const status = runSync(root, ["git", "status", "--porcelain", "--untracked-files=normal"]).stdout.trim();
  const commitTime = runSync(root, ["git", "show", "-s", "--format=%cI", "HEAD"]).stdout.trim();
  return { root, commit, clean: Boolean(commit) && status === "", commitTime };
}

function readVersion(file) {
  const values = {};
  safeRead(file).split(/\r?\n/).forEach((line) => {
    const index = line.indexOf("=");
    if (index > 0) values[line.slice(0, index).trim()] = line.slice(index + 1).trim();
  });
  return { name: values.VERSION_NAME || "", build: values.VERSION_BUILD || "" };
}

function newestFile(root, predicate) {
  return listFiles(root)
    .filter(predicate)
    .map((file) => ({ file, mtime: fs.statSync(file).mtimeMs }))
    .sort((left, right) => right.mtime - left.mtime)[0]?.file || null;
}

function snapshotJUnitFiles(root) {
  const snapshot = new Map();
  listFiles(root).filter((file) => file.endsWith(".xml")).forEach((file) => {
    const stat = fs.statSync(file);
    snapshot.set(path.resolve(file), { size: stat.size, mtimeMs: stat.mtimeMs, sha256: sha256(file) });
  });
  return snapshot;
}

function selectFreshSoakJUnit(root, before, startedAtEpochMs) {
  const candidates = listFiles(root)
    .filter((file) => file.endsWith(".xml"))
    .filter((file) => safeRead(file).includes("NodeR6PersistentPluginSoakInstrumentationTest"))
    .map((file) => {
      const stat = fs.statSync(file);
      const prior = before instanceof Map ? before.get(path.resolve(file)) : null;
      const digest = sha256(file);
      const changed = !prior || prior.sha256 !== digest || prior.size !== stat.size;
      return { file, stat, changed };
    })
    .filter((entry) => entry.changed && entry.stat.mtimeMs >= startedAtEpochMs - 10_000)
    .sort((left, right) => right.stat.mtimeMs - left.stat.mtimeMs);
  if (candidates.length !== 1) {
    throw new Error(`Expected exactly one fresh R6 soak JUnit XML, found ${candidates.length}.`);
  }
  return candidates[0].file;
}

function selectHostApk(repoRoot, runtimeAbi) {
  const root = path.join(repoRoot, "app/build/outputs/apk/app/debug");
  const apks = listFiles(root).filter((file) => file.endsWith(".apk"));
  const abi = String(runtimeAbi || "").trim();
  const matching = abi ? apks.filter((file) => path.basename(file).includes(`-${abi}.apk`)) : [];
  const candidates = matching.length > 0
    ? matching
    : apks.filter((file) => path.basename(file).includes("-universal.apk"));
  return candidates
    .map((file) => ({ file, mtime: fs.statSync(file).mtimeMs }))
    .sort((left, right) => right.mtime - left.mtime)[0]?.file || null;
}

function selectPluginApk(pluginRoot, runtimeAbi) {
  const root = path.join(pluginRoot, "app/build/outputs/apk/debug");
  const abi = String(runtimeAbi || "").trim();
  const apks = listFiles(root).filter((file) => file.endsWith(".apk"));
  const matching = abi ? apks.filter((file) => path.basename(file).includes(`-${abi}.apk`)) : [];
  const candidates = matching.length > 0
    ? matching
    : apks.filter((file) => path.basename(file).includes("-universal.apk"));
  return candidates
    .map((file) => ({ file, mtime: fs.statSync(file).mtimeMs }))
    .sort((left, right) => right.mtime - left.mtime)[0]?.file || null;
}

function copyArtifact(source, directory, name) {
  fs.mkdirSync(directory, { recursive: true });
  const target = path.join(directory, name);
  fs.copyFileSync(source, target);
  if (fs.statSync(target).size <= 0 || sha256(source) !== sha256(target)) {
    throw new Error(`Artifact copy did not preserve bytes: ${source} -> ${target}`);
  }
  return target;
}

function assertFreshArtifact(file, startedAtEpochMs, label) {
  const stat = fs.statSync(file);
  if (!Number.isFinite(startedAtEpochMs) ||
      stat.mtimeMs < startedAtEpochMs - FRESHNESS_TOLERANCE_MS) {
    throw new Error(`${label} is stale for this connected build: ${file} (${new Date(stat.mtimeMs).toISOString()}).`);
  }
}

function listFiles(root) {
  if (!fs.existsSync(root)) return [];
  const output = [];
  for (const name of fs.readdirSync(root)) {
    const file = path.join(root, name);
    const stat = fs.statSync(file);
    if (stat.isDirectory()) output.push(...listFiles(file));
    else output.push(file);
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

function normalizeKind(value) {
  const kind = String(value || "").trim().toLowerCase();
  if (kind === "quick") return kind;
  if (kind === "final") return kind;
  throw new Error("--kind must be quick or final; use --smoke for a non-archivable short run.");
}

function parseArgs(argv) {
  const options = {};
  const booleans = new Set(["smoke", "no-archive", "help"]);
  const values = new Set([
    "kind", "serial", "repo-root", "plugin-root", "run-dir", "device-profile",
    "smoke-duration-ms", "sample-interval-ms", "cycle-delay-ms", "liveness-recoverability-every-cycles",
    "gradle-timeout-ms", "pull-timeout-ms",
  ]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "-h" || arg === "--help") { options.help = true; continue; }
    if (!arg.startsWith("--")) throw new Error(`Unexpected positional argument: ${arg}`);
    const eq = arg.indexOf("=");
    const rawKey = eq >= 0 ? arg.slice(2, eq) : arg.slice(2);
    if (!booleans.has(rawKey) && !values.has(rawKey)) {
      throw new Error(`Unsupported option --${rawKey}. Production duration overrides and force archive are intentionally forbidden.`);
    }
    const key = rawKey.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
    if (booleans.has(rawKey)) {
      options[key] = eq >= 0 ? parseBoolean(arg.slice(eq + 1)) : true;
      continue;
    }
    const value = eq >= 0 ? arg.slice(eq + 1) : argv[++index];
    if (value === undefined || value.startsWith("--")) throw new Error(`Missing value for --${rawKey}.`);
    options[key] = value;
  }
  return options;
}

function boundedNumber(value, fallback, minimum, maximum, name) {
  const parsed = value === undefined ? fallback : Number(value);
  if (!Number.isFinite(parsed) || parsed < minimum || parsed > maximum) {
    throw new Error(`--${name} must be between ${minimum} and ${maximum}; got ${value}.`);
  }
  return Math.round(parsed);
}

function nonNegativeNumber(value, name) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 0) throw new Error(`--${name} must be non-negative; got ${value}.`);
  return Math.round(parsed);
}

function parseBoolean(value) {
  return ["1", "true", "yes", "on"].includes(String(value).toLowerCase());
}

function gradleCommand(repoRoot) {
  return process.platform === "win32" ? path.join(repoRoot, "gradlew.bat") : path.join(repoRoot, "gradlew");
}

function commandInvocation(command, args) {
  if (process.platform !== "win32" || !command.toLowerCase().endsWith(".bat")) return { command, args };
  return { command: process.env.ComSpec || "cmd.exe", args: ["/d", "/c", "call", command, ...args] };
}

function writeSummary(runRoot, summary) {
  fs.writeFileSync(path.join(runRoot, "run.json"), JSON.stringify(summary, null, 2) + "\n");
  fs.writeFileSync(path.join(runRoot, "run.md"), renderMarkdown(summary));
}

function failRunner(summary, runRoot, failure) {
  summary.decision.failures = [...new Set([...summary.decision.failures, failure])];
  summary.decision.status = "failed";
  summary.generatedAt = new Date().toISOString();
  writeSummary(runRoot, summary);
  console.error(`R6 persistent-plugin soak runner: failed\n- ${failure}`);
  process.exitCode = 1;
}

function renderMarkdown(summary) {
  const lines = [
    "# R6 Persistent Plugin Soak Runner", "",
    `- Status: \`${summary.decision.status}\``,
    `- Mode: \`${summary.mode}\``,
    `- Kind: \`${summary.kind}\``,
    `- Serial: \`${summary.serial}\``,
    `- Requested duration: \`${summary.requested.durationHours} h\``,
    `- Archived: \`${summary.decision.archived}\``,
    `- Run directory: \`${summary.runDir}\``, "", "## Failures", "",
  ];
  if (summary.decision.failures.length === 0) lines.push("None.");
  else summary.decision.failures.forEach((failure) => lines.push(`- ${failure}`));
  return lines.join("\n") + "\n";
}

function displayCommand(command, args) {
  return [command, ...args].map((value) => /\s/.test(String(value)) ? `"${String(value).replace(/"/g, '\\"')}"` : String(value)).join(" ");
}

function timestampForPath(date) {
  return date.toISOString().replace(/[:.]/g, "-");
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8").replace(/^\uFEFF/, ""));
}

function safeRead(file) {
  try { return fs.readFileSync(file, "utf8"); } catch (_) { return ""; }
}

function sha256(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function relativePath(root, file) {
  return path.relative(root, file).replace(/\\/g, "/") || ".";
}

function printHelp() {
  console.log(`Usage: node tools/nodejs/r6/run-production-soak.js [options]

Production (fixed duration, archives only after complete validation):
  --kind quick|final             quick is exactly 8h; final is exactly 24h.

Development smoke (always non-archivable and report kind=smoke):
  --smoke                       Run a short non-production smoke.
  --smoke-duration-ms MS        5000..600000; defaults to 30000.

Common:
  --serial SERIAL               Required unless ANDROID_SERIAL is set.
  --plugin-root DIR             Runtime plugin source root.
  --device-profile PROFILE      Gradle device profile.
  --sample-interval-ms MS       Snapshot interval (1000..300000; smoke may cap lower).
  --cycle-delay-ms MS           Execution delay (capped at 30000 and half the sample interval).
  --liveness-recoverability-every-cycles N
                                Non-destructive liveness/recoverability check cadence.
  --run-dir DIR                 Empty runner directory inside the repository.
  --no-archive                  Validate a production run without canonical copy.

There is intentionally no production duration override and no force-archive option.
`);
}

module.exports = {
  assertFreshArtifact,
  buildGradleArgs,
  selectFreshSoakJUnit,
  snapshotJUnitFiles,
};
