#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const { spawn } = require("child_process");

const DEVICE_REPORT_DIR = "/sdcard/Download/AutoJs6/nodejs/final-soak";
const DEFAULT_PRIMARY_SERIAL = "QV710AF65F";
const TEST_CLASS = "org.autojs.autojs.engine.NodeFinalSoakInstrumentationTest#fullHeavyResourceSoakProducesArchiveReadyReport";
const REPORT_SCHEMA = "autojs6-node-phase13-full-heavy-resource-soak-runner-v1";

const INSTRUMENTATION_ARGS = {
  kind: "autojs.nodejs.finalSoak.kind",
  durationMs: "autojs.nodejs.finalSoak.durationMs",
  durationHours: "autojs.nodejs.finalSoak.durationHours",
  sampleIntervalMs: "autojs.nodejs.finalSoak.sampleIntervalMs",
  cycleDelayMs: "autojs.nodejs.finalSoak.cycleDelayMs",
  maxRssDeltaKb: "autojs.nodejs.finalSoak.maxRssDeltaKb",
  maxJavaHeapDeltaKb: "autojs.nodejs.finalSoak.maxJavaHeapDeltaKb",
  maxNativeHeapDeltaKb: "autojs.nodejs.finalSoak.maxNativeHeapDeltaKb",
  maxThreadDelta: "autojs.nodejs.finalSoak.maxThreadDelta",
  maxFdDelta: "autojs.nodejs.finalSoak.maxFdDelta",
};

main().catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exitCode = 1;
});

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const kind = normalizeKind(options.kind || "quick");
  const durationMs = resolveDurationMs(options, kind);
  const serial = options.serial || process.env.ANDROID_SERIAL || DEFAULT_PRIMARY_SERIAL;
  const deviceProfile = options.deviceProfile || (serial === DEFAULT_PRIMARY_SERIAL ? "sony" : "smoke");
  const runStartedAtEpochMs = Date.now();
  const runId = options.runId || `final-soak-${kind}-${timestampForPath(new Date(runStartedAtEpochMs))}`;
  const runRoot = path.resolve(options.runDir || path.join(repoRoot, "build/reports/nodejs/final-soak-runs", runId));
  const devicePullDir = path.join(runRoot, "device");
  const archive = Boolean(options.archive) && !Boolean(options.noArchive);
  const gradleTimeoutMs = numberOption(
    options.gradleTimeoutMs,
    Math.max(durationMs + 3_600_000, 1_800_000),
    "gradle-timeout-ms",
  );

  fs.mkdirSync(runRoot, { recursive: true });
  fs.mkdirSync(devicePullDir, { recursive: true });

  const summary = {
    schema: REPORT_SCHEMA,
    generatedAt: new Date().toISOString(),
    kind,
    serial,
    deviceProfile,
    testClass: TEST_CLASS,
    deviceReportDir: DEVICE_REPORT_DIR,
    runDir: relativePath(repoRoot, runRoot),
    requested: {
      durationMs,
      durationHours: durationMs / 3_600_000,
      sampleIntervalMs: numberOption(options.sampleIntervalMs, 300_000, "sample-interval-ms"),
      cycleDelayMs: numberOption(options.cycleDelayMs, 30_000, "cycle-delay-ms"),
      archive,
      forceArchive: Boolean(options.force),
      dryRunArchive: Boolean(options.dryRunArchive),
      gradleTimeoutMs,
    },
    gradle: null,
    pull: null,
    report: null,
    archive: null,
    decision: {
      status: "running",
      readyForArchive: false,
      archived: false,
    },
  };
  writeSummary(repoRoot, runRoot, summary);

  const gradleArgs = buildGradleArgs(kind, durationMs, deviceProfile, options);
  const env = { ...process.env, ANDROID_SERIAL: serial };
  console.log("Phase 13 full heavy resource soak runner");
  console.log(`- kind: ${kind}`);
  console.log(`- serial: ${serial}`);
  console.log(`- requested duration: ${formatHours(durationMs)}h`);
  console.log(`- run dir: ${runRoot}`);
  console.log(`- archive after run: ${archive}`);

  const gradleInvocation = commandInvocation(gradleCommand(repoRoot), gradleArgs);
  const gradle = await runCommand(
    gradleInvocation.command,
    gradleInvocation.args,
    {
      cwd: repoRoot,
      env,
      timeoutMs: gradleTimeoutMs,
      logFile: path.join(runRoot, "gradle-output.txt"),
    },
  );
  summary.gradle = commandSummary(gradle, repoRoot);
  writeSummary(repoRoot, runRoot, summary);

  if (!options.noPull) {
    const pull = await runCommand(
      "adb",
      ["-s", serial, "pull", `${DEVICE_REPORT_DIR}/.`, devicePullDir],
      {
        cwd: repoRoot,
        env,
        timeoutMs: numberOption(options.pullTimeoutMs, 300_000, "pull-timeout-ms"),
        logFile: path.join(runRoot, "adb-pull-output.txt"),
      },
    );
    summary.pull = commandSummary(pull, repoRoot);
    writeSummary(repoRoot, runRoot, summary);
  }

  const candidate = findCandidateReport(devicePullDir, kind, runStartedAtEpochMs);
  if (candidate) {
    summary.report = {
      path: relativePath(repoRoot, candidate.file),
      fileName: path.basename(candidate.file),
      fileTimestampEpochMs: candidate.timestampEpochMs,
      freshForRun: candidate.freshForRun,
      json: reportDigest(candidate.file),
    };
    summary.decision.readyForArchive = Boolean(summary.report.json && summary.report.json.status !== "missing");
  } else {
    summary.report = {
      path: "",
      fileName: "",
      freshForRun: false,
      json: { status: "missing" },
    };
  }
  writeSummary(repoRoot, runRoot, summary);

  if (archive && candidate) {
    const archiveArgs = [
      path.join(repoRoot, "tools/nodejs/phase13/archive-full-heavy-resource-soak.js"),
      "--kind",
      kind,
      "--source",
      candidate.file,
    ];
    if (options.force) {
      archiveArgs.push("--force");
    }
    if (options.dryRunArchive) {
      archiveArgs.push("--dry-run");
    }
    const archiveRun = await runCommand(
      process.execPath,
      archiveArgs,
      {
        cwd: repoRoot,
        env,
        timeoutMs: numberOption(options.archiveTimeoutMs, 300_000, "archive-timeout-ms"),
        logFile: path.join(runRoot, "archive-output.txt"),
      },
    );
    summary.archive = commandSummary(archiveRun, repoRoot);
    summary.decision.archived = archiveRun.exitCode === 0 && !options.dryRunArchive;
    writeSummary(repoRoot, runRoot, summary);
  }

  const failures = [];
  if (gradle.exitCode !== 0 || gradle.timedOut) {
    failures.push("gradle connected instrumentation did not complete cleanly");
  }
  if (!options.noPull && summary.pull && (summary.pull.exitCode !== 0 || summary.pull.timedOut)) {
    failures.push("adb pull did not complete cleanly");
  }
  if (!candidate) {
    failures.push("no final soak report was pulled from the device");
  }
  if (archive && (!summary.archive || summary.archive.exitCode !== 0 || summary.archive.timedOut)) {
    failures.push("archive helper did not complete cleanly");
  }
  summary.decision.status = failures.length === 0 ? "completed" : "failed";
  summary.decision.failures = failures;
  summary.generatedAt = new Date().toISOString();
  writeSummary(repoRoot, runRoot, summary);

  console.log(`Phase 13 full heavy resource soak runner: ${summary.decision.status}`);
  console.log(`Run report: ${path.join(runRoot, "run.json")}`);
  if (summary.report && summary.report.path) {
    console.log(`Device soak report: ${summary.report.path}`);
  }
  if (failures.length > 0) {
    failures.forEach((failure) => console.error("- " + failure));
    process.exitCode = 1;
  }
}

function buildGradleArgs(kind, durationMs, deviceProfile, options) {
  const args = [
    "--console=plain",
    ":app:connectedAppDebugAndroidTest",
    `-Pandroid.testInstrumentationRunnerArguments.class=${TEST_CLASS}`,
    `-Pandroid.testInstrumentationRunnerArguments.${INSTRUMENTATION_ARGS.kind}=${kind}`,
    `-Pandroid.testInstrumentationRunnerArguments.${INSTRUMENTATION_ARGS.durationMs}=${durationMs}`,
    `-Pandroid.testInstrumentationRunnerArguments.${INSTRUMENTATION_ARGS.sampleIntervalMs}=${numberOption(options.sampleIntervalMs, 300_000, "sample-interval-ms")}`,
    `-Pandroid.testInstrumentationRunnerArguments.${INSTRUMENTATION_ARGS.cycleDelayMs}=${numberOption(options.cycleDelayMs, 30_000, "cycle-delay-ms")}`,
    `-Pautojs.nodejs.test.deviceProfile=${deviceProfile}`,
  ];
  [
    "maxRssDeltaKb",
    "maxJavaHeapDeltaKb",
    "maxNativeHeapDeltaKb",
    "maxThreadDelta",
    "maxFdDelta",
  ].forEach((key) => {
    if (options[key] !== undefined) {
      args.push(`-Pandroid.testInstrumentationRunnerArguments.${INSTRUMENTATION_ARGS[key]}=${numberOption(options[key], 0, key)}`);
    }
  });
  return args;
}

function runCommand(command, args, options) {
  return new Promise((resolve) => {
    const startedAt = Date.now();
    fs.mkdirSync(path.dirname(options.logFile), { recursive: true });
    const log = fs.createWriteStream(options.logFile, { flags: "a" });
    log.write(`$ ${displayCommand(command, args)}\n`);
    const child = spawn(command, args, {
      cwd: options.cwd,
      env: options.env,
      windowsHide: true,
    });
    let timedOut = false;
    const timeout = setTimeout(() => {
      timedOut = true;
      child.kill("SIGTERM");
    }, options.timeoutMs);
    child.stdout.on("data", (chunk) => {
      process.stdout.write(chunk);
      log.write(chunk);
    });
    child.stderr.on("data", (chunk) => {
      process.stderr.write(chunk);
      log.write(chunk);
    });
    child.on("error", (error) => {
      clearTimeout(timeout);
      log.write(`\nERROR: ${error.message}\n`);
      log.end();
      resolve({
        command,
        args,
        exitCode: 1,
        signal: "",
        timedOut,
        startedAt,
        endedAt: Date.now(),
        logFile: options.logFile,
        error: error.message,
      });
    });
    child.on("close", (exitCode, signal) => {
      clearTimeout(timeout);
      log.write(`\nexitCode=${exitCode} signal=${signal || ""} timedOut=${timedOut}\n`);
      log.end();
      resolve({
        command,
        args,
        exitCode: typeof exitCode === "number" ? exitCode : 1,
        signal: signal || "",
        timedOut,
        startedAt,
        endedAt: Date.now(),
        logFile: options.logFile,
      });
    });
  });
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

function findCandidateReport(devicePullDir, kind, runStartedAtEpochMs) {
  const files = listFiles(devicePullDir)
    .filter((file) => path.basename(file).startsWith(`final-soak-${kind}-`) && file.endsWith(".json"))
    .map((file) => ({
      file,
      timestampEpochMs: Number(path.basename(file).replace(`final-soak-${kind}-`, "").replace(/\.json$/, "")),
    }))
    .filter((entry) => Number.isFinite(entry.timestampEpochMs))
    .sort((left, right) => right.timestampEpochMs - left.timestampEpochMs);
  if (files.length === 0) {
    return null;
  }
  const fresh = files.find((entry) => entry.timestampEpochMs >= runStartedAtEpochMs - 300_000);
  const selected = fresh || files[0];
  return {
    ...selected,
    freshForRun: Boolean(fresh),
  };
}

function listFiles(root) {
  if (!fs.existsSync(root)) {
    return [];
  }
  const entries = [];
  for (const name of fs.readdirSync(root)) {
    const file = path.join(root, name);
    const stat = fs.statSync(file);
    if (stat.isDirectory()) {
      entries.push(...listFiles(file));
    } else {
      entries.push(file);
    }
  }
  return entries;
}

function reportDigest(file) {
  try {
    const json = JSON.parse(fs.readFileSync(file, "utf8").replace(/^\uFEFF/, ""));
    return {
      schema: json.schema || "",
      status: json.status || "",
      kind: json.kind || "",
      durationMs: typeof json.durationMs === "number" ? json.durationMs : null,
      durationHours: typeof json.durationHours === "number" ? json.durationHours : null,
      cycles: typeof json.cycles === "number" ? json.cycles : null,
      samples: Array.isArray(json.samples) ? json.samples.length : null,
      failures: Array.isArray(json.failures) ? json.failures.length : null,
      monotonicLeaks: Boolean(json.monotonicLeaks),
      orphanProcess: Boolean(json.orphanProcess),
      unrecoverableRuntime: Boolean(json.unrecoverableRuntime),
    };
  } catch (error) {
    return {
      status: "unreadable",
      error: error.message,
    };
  }
}

function writeSummary(repoRoot, runRoot, summary) {
  fs.writeFileSync(path.join(runRoot, "run.json"), JSON.stringify(summary, null, 2) + "\n");
  fs.writeFileSync(path.join(runRoot, "run.md"), renderMarkdown(repoRoot, summary));
}

function renderMarkdown(repoRoot, summary) {
  const lines = [
    "# Phase 13 Full Heavy Resource Soak Runner",
    "",
    `- Status: \`${summary.decision.status}\``,
    `- Kind: \`${summary.kind}\``,
    `- Serial: \`${summary.serial}\``,
    `- Device profile: \`${summary.deviceProfile}\``,
    `- Requested duration hours: \`${formatHours(summary.requested.durationMs)}\``,
    `- Archive requested: \`${summary.requested.archive}\``,
    `- Run dir: \`${summary.runDir}\``,
    "",
    "## Commands",
    "",
  ];
  if (summary.gradle) {
    lines.push(`- Gradle: \`${summary.gradle.exitCode}\`, log \`${summary.gradle.logFile}\``);
  }
  if (summary.pull) {
    lines.push(`- Pull: \`${summary.pull.exitCode}\`, log \`${summary.pull.logFile}\``);
  }
  if (summary.archive) {
    lines.push(`- Archive: \`${summary.archive.exitCode}\`, log \`${summary.archive.logFile}\``);
  }
  lines.push("", "## Report", "");
  if (summary.report && summary.report.path) {
    lines.push(`- Device report: \`${summary.report.path}\``);
    lines.push(`- Status: \`${summary.report.json.status}\``);
    lines.push(`- Duration hours: \`${summary.report.json.durationHours}\``);
    lines.push(`- Fresh for run: \`${summary.report.freshForRun}\``);
  } else {
    lines.push("No pulled report selected yet.");
  }
  if (summary.decision.failures && summary.decision.failures.length > 0) {
    lines.push("", "## Failures", "");
    summary.decision.failures.forEach((failure) => lines.push(`- ${failure}`));
  }
  return lines.join("\n") + "\n";
}

function resolveDurationMs(options, kind) {
  if (options.durationMs !== undefined) {
    return numberOption(options.durationMs, 0, "duration-ms");
  }
  if (options.durationHours !== undefined) {
    return Math.round(numberOption(options.durationHours, 0, "duration-hours") * 3_600_000);
  }
  return kind === "final" ? 24 * 3_600_000 : 8 * 3_600_000;
}

function normalizeKind(value) {
  const normalized = String(value || "").toLowerCase().replace(/_/g, "-");
  if (["quick", "quick-soak", "quick-soak-8h", "quick-soak-8"].includes(normalized)) {
    return "quick";
  }
  if (["final", "final-soak", "final-soak-24h", "final-soak-24"].includes(normalized)) {
    return "final";
  }
  throw new Error("Unsupported --kind. Use quick or final.");
}

function gradleCommand(repoRoot) {
  return process.platform === "win32" ? path.join(repoRoot, "gradlew.bat") : path.join(repoRoot, "gradlew");
}

function commandInvocation(command, args) {
  if (process.platform !== "win32" || !command.toLowerCase().endsWith(".bat")) {
    return { command, args };
  }
  return {
    command: process.env.ComSpec || "cmd.exe",
    args: ["/d", "/c", "call", command, ...args],
  };
}

function numberOption(value, defaultValue, name) {
  if (value === undefined || value === null || value === "") {
    return defaultValue;
  }
  const number = Number(value);
  if (!Number.isFinite(number) || number < 0) {
    throw new Error(`Invalid --${name}: ${value}`);
  }
  return number;
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set([
    "archive",
    "no-archive",
    "dry-run-archive",
    "force",
    "no-pull",
    "help",
  ]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "-h" || arg === "--help") {
      options.help = true;
      continue;
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const eq = arg.indexOf("=");
    const rawKey = eq >= 0 ? arg.slice(2, eq) : arg.slice(2);
    const key = camelCase(rawKey);
    if (booleanArgs.has(rawKey)) {
      options[key] = eq >= 0 ? parseBoolean(arg.slice(eq + 1)) : true;
      continue;
    }
    const value = eq >= 0 ? arg.slice(eq + 1) : argv[index + 1];
    if (eq < 0) {
      index += 1;
    }
    if (value === undefined || value.startsWith("--")) {
      throw new Error("Missing value for " + arg);
    }
    options[key] = value;
  }
  return options;
}

function camelCase(value) {
  return value.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
}

function parseBoolean(value) {
  return ["1", "true", "yes", "on"].includes(String(value).toLowerCase());
}

function timestampForPath(date) {
  return date.toISOString().replace(/[:.]/g, "-");
}

function displayCommand(command, args) {
  return [command, ...args].map(quoteArg).join(" ");
}

function quoteArg(value) {
  const text = String(value);
  return /\s/.test(text) ? `"${text.replace(/"/g, '\\"')}"` : text;
}

function formatHours(durationMs) {
  return Math.round((durationMs / 3_600_000) * 1000) / 1000;
}

function relativePath(root, file) {
  return path.relative(root, file).replace(/\\/g, "/");
}

function printHelp() {
  console.log(`Usage: node tools/nodejs/phase13/run-full-heavy-resource-soak.js [options]

Runs NodeFinalSoakInstrumentationTest on one explicit Android device, pulls the
device JSON report, and optionally archives a completed 8h/24h report.

Options:
  --kind quick|final                 Soak type; defaults to quick.
  --serial SERIAL                    ADB serial; defaults to ANDROID_SERIAL or ${DEFAULT_PRIMARY_SERIAL}.
  --device-profile PROFILE           Gradle device profile; defaults to sony for ${DEFAULT_PRIMARY_SERIAL}, smoke otherwise.
  --duration-hours HOURS             Override duration; defaults to 8 for quick and 24 for final.
  --duration-ms MS                   Millisecond duration override for smoke testing.
  --sample-interval-ms MS            Snapshot interval; defaults to 300000.
  --cycle-delay-ms MS                Delay between workload cycles; defaults to 30000.
  --gradle-timeout-ms MS             Gradle timeout; defaults to duration plus one hour.
  --archive                          Validate and copy the pulled report into build/reports/nodejs/final-soak.
  --dry-run-archive                  Validate archive only.
  --force                            Replace an existing canonical final-soak report.
  --run-dir DIR                      Output directory for logs and run report.
`);
}
