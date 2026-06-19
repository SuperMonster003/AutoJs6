#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const ERROR_MARKER = "__AUTOJS6_MODERN_CORE_DIFFERENTIAL_ERROR__=";
const AUTOJS_RESULT_PREFIX = "moderncore.differential.result=";
const CLASSIFICATIONS = new Set([
  "exact match",
  "intentional AutoJs6 restriction",
  "partial",
  "bug",
]);

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(__dirname, "../../..");
  const fixtureRoot = path.resolve(
    options.fixtures || path.join(repoRoot, "app/src/androidTest/assets/node_modern_core_differential"),
  );
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs/differential"));
  const nodeBinary = options.node || process.execPath;

  const manifest = readJson(path.join(fixtureRoot, "manifest.json"));
  const fixtures = manifest.fixtures || [];
  if (!Array.isArray(fixtures) || fixtures.length === 0) {
    throw new Error("No modern core differential fixtures found in " + path.join(fixtureRoot, "manifest.json"));
  }

  const autoJsResults = readAutoJsResults(options);
  const rows = fixtures.map((fixture) => {
    validateFixture(fixture);
    const fixtureDir = path.join(fixtureRoot, fixture.name);
    const expectedOutput = readText(path.join(fixtureDir, "expected.txt")).trim();
    const desktop = runDesktopFixture(nodeBinary, fixtureDir, fixture.name, expectedOutput);
    const autojs = autoJsResults.get(fixture.name) || null;
    return {
      fixture: fixture.name,
      area: fixture.area,
      expectedClassification: fixture.expectedClassification,
      reason: fixture.reason || "",
      expectedOutput,
      desktop,
      autojs,
      comparison: compareFixture(fixture, expectedOutput, desktop, autojs),
    };
  });

  const report = {
    generatedAt: new Date().toISOString(),
    corpusVersion: String(manifest.version || ""),
    fixtureRoot,
    desktopNode: {
      binary: nodeBinary,
      version: runNodeVersion(nodeBinary),
    },
    autoJsSource: options.autojsJson || options.autojsLog || null,
    rows,
    summary: summarize(rows),
  };

  fs.mkdirSync(reportDir, { recursive: true });
  const jsonPath = path.join(reportDir, "modern-core-differential.json");
  const markdownPath = path.join(reportDir, "modern-core-differential.md");
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(markdownPath, renderMarkdown(report));

  console.log("Modern core differential JSON: " + jsonPath);
  console.log("Modern core differential Markdown: " + markdownPath);
  console.log("Modern core differential summary: " + JSON.stringify(report.summary));

  if (report.summary.desktopFailures > 0 || report.summary.bugs > 0) {
    process.exitCode = 1;
  }
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      printHelp();
      process.exit(0);
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const eq = arg.indexOf("=");
    const key = (eq >= 0 ? arg.slice(2, eq) : arg.slice(2)).replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
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

function printHelp() {
  console.log([
    "Usage: node tools/nodejs/differential/run-modern-core-differential.js [options]",
    "",
    "Options:",
    "  --fixtures <dir>      Fixture root. Defaults to app/src/androidTest/assets/node_modern_core_differential.",
    "  --report-dir <dir>    Output directory. Defaults to build/reports/nodejs/differential.",
    "  --node <path>         Desktop Node binary. Defaults to process.execPath.",
    "  --autojs-log <file>   Gradle/logcat text containing moderncore.differential.result= JSON lines.",
    "  --autojs-json <file>  JSON file containing an array, {results:[]}, or {rows:[{autojs}]} shape.",
  ].join("\n"));
}

function validateFixture(fixture) {
  if (!fixture || typeof fixture.name !== "string" || !fixture.name) {
    throw new Error("Modern core fixture is missing name");
  }
  if (!fixture.area) {
    throw new Error("Modern core fixture is missing area: " + fixture.name);
  }
  if (!CLASSIFICATIONS.has(fixture.expectedClassification)) {
    throw new Error("Unsupported classification for " + fixture.name + ": " + fixture.expectedClassification);
  }
}

function runDesktopFixture(nodeBinary, fixtureDir, fixtureName, expectedOutput) {
  const testPath = path.join(fixtureDir, "test.cjs");
  const script = `
    "use strict";
    const testPath = process.argv[1];
    Promise.resolve()
      .then(() => {
        const run = require(testPath);
        if (typeof run !== "function") {
          throw new Error("fixture does not export a runner");
        }
        return run();
      })
      .then((result) => {
        if (result !== "PASS") {
          throw new Error("fixture returned " + result);
        }
      })
      .catch((error) => {
        const payload = {
          name: String(error && error.name || "Error"),
          code: String(error && error.code || ""),
          message: String(error && error.message || error),
        };
        console.error(${JSON.stringify(ERROR_MARKER)} + JSON.stringify(payload));
        process.exitCode = 1;
      });
  `;
  const result = childProcess.spawnSync(nodeBinary, ["-e", script, testPath], {
    cwd: fixtureDir,
    encoding: "utf8",
    maxBuffer: 10 * 1024 * 1024,
  });
  if (result.error) {
    throw result.error;
  }

  const error = parseMarkedError(result.stderr);
  const stdout = normalizeOutput(result.stdout);
  const stderr = normalizeStderr(result.stderr);
  const errorMessage = error ? error.message : "";
  return {
    runtime: "desktop-node",
    fixture: fixtureName,
    exitCode: result.status == null ? 1 : result.status,
    stdout,
    stderr,
    errorCode: error ? error.code : "",
    errorMessage,
    errorMessagePattern: errorMessagePattern(errorMessage),
    expectedOutput,
    expectedOutputFound: stdout.includes(expectedOutput),
  };
}

function readAutoJsResults(options) {
  const results = new Map();
  [options.autojsJson, options.autojsLog].filter(Boolean).forEach((file) => {
    const resolved = path.resolve(file);
    const text = fs.readFileSync(resolved, "utf8");
    const records = file === options.autojsJson ? parseAutoJsJson(text) : parseAutoJsLog(text);
    records.forEach((record) => {
      if (record && record.fixture) {
        results.set(record.fixture, normalizeRuntimeRecord(record));
      }
    });
  });
  return results;
}

function parseAutoJsJson(text) {
  const parsed = JSON.parse(text);
  if (Array.isArray(parsed)) {
    return parsed;
  }
  if (Array.isArray(parsed.results)) {
    return parsed.results;
  }
  if (Array.isArray(parsed.autojs)) {
    return parsed.autojs;
  }
  if (Array.isArray(parsed.rows)) {
    return parsed.rows.map((row) => row && row.autojs).filter(Boolean);
  }
  throw new Error("Unsupported AutoJs modern core differential JSON shape");
}

function parseAutoJsLog(text) {
  const source = String(text);
  const records = [];
  let offset = 0;
  while (offset < source.length) {
    const markerIndex = source.indexOf(AUTOJS_RESULT_PREFIX, offset);
    if (markerIndex < 0) {
      break;
    }
    const payloadStart = markerIndex + AUTOJS_RESULT_PREFIX.length;
    const payload = source.slice(payloadStart);
    const jsonText = extractJsonObjectPrefix(payload);
    records.push(JSON.parse(jsonText));
    offset = payloadStart + jsonText.length;
  }
  return records;
}

function extractJsonObjectPrefix(value) {
  const text = String(value).trimStart();
  let depth = 0;
  let inString = false;
  let escaped = false;
  let started = false;
  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    if (inString) {
      if (escaped) {
        escaped = false;
      } else if (char === "\\") {
        escaped = true;
      } else if (char === '"') {
        inString = false;
      }
      continue;
    }
    if (char === '"') {
      inString = true;
      continue;
    }
    if (char === "{") {
      depth += 1;
      started = true;
      continue;
    }
    if (char === "}") {
      depth -= 1;
      if (started && depth === 0) {
        return text.slice(0, index + 1);
      }
    }
  }
  throw new Error("Could not find complete AutoJs modern core differential JSON payload");
}

function normalizeRuntimeRecord(record) {
  const errorMessage = String(record.errorMessage || "");
  return {
    runtime: record.runtime || "autojs6",
    fixture: String(record.fixture || ""),
    exitCode: Number(record.exitCode || 0),
    stdout: normalizeOutput(record.stdout || ""),
    stderr: normalizeOutput(record.stderr || ""),
    errorCode: String(record.errorCode || ""),
    errorMessage,
    errorMessagePattern: String(record.errorMessagePattern || errorMessagePattern(errorMessage)),
    expectedOutput: String(record.expectedOutput || ""),
    expectedOutputFound: !!record.expectedOutputFound,
  };
}

function compareFixture(fixture, expectedOutput, desktop, autojs) {
  const desktopOk = runtimeOk(desktop, expectedOutput);
  if (!desktopOk && !fixture.allowDesktopFailure) {
    return {
      status: "desktop-failed",
      classification: "bug",
      comparisonAvailable: !!autojs,
      reason: "Desktop Node did not pass the fixture or emit the expected marker.",
    };
  }

  if (!autojs) {
    return {
      status: "desktop-only",
      classification: fixture.expectedClassification,
      comparisonAvailable: false,
      reason: "Desktop Node baseline generated; supply --autojs-log or --autojs-json for an AutoJs6 comparison.",
    };
  }

  const autojsOk = runtimeOk(autojs, expectedOutput);
  if (!autojsOk) {
    return {
      status: "autojs-failed",
      classification: "bug",
      comparisonAvailable: true,
      reason: "AutoJs6 did not pass the fixture or emit the expected marker.",
    };
  }

  if (fixture.expectedClassification === "exact match") {
    if (recordsMatch(desktop, autojs)) {
      return {
        status: "matched",
        classification: "exact match",
        comparisonAvailable: true,
        reason: "Desktop Node and AutoJs6 normalized fixture outputs agree.",
      };
    }
    return {
      status: "different",
      classification: "bug",
      comparisonAvailable: true,
      reason: "Fixture is expected to match desktop Node exactly, but normalized results differ.",
    };
  }

  if (fixture.expectedClassification === "intentional AutoJs6 restriction") {
    const pattern = String(fixture.autoJsRestrictionPattern || "");
    if (pattern && !autojs.stdout.includes(pattern) && !String(autojs.errorCode || "").includes(pattern)) {
      return {
        status: "restriction-marker-missing",
        classification: "bug",
        comparisonAvailable: true,
        reason: "Expected AutoJs6 restriction marker was not observed: " + pattern,
      };
    }
    return {
      status: recordsMatch(desktop, autojs) ? "matched-restriction-probe" : "restricted",
      classification: "intentional AutoJs6 restriction",
      comparisonAvailable: true,
      reason: fixture.reason || "AutoJs6 intentionally exposes a restricted subset for this area.",
    };
  }

  if (fixture.expectedClassification === "partial") {
    if (!recordsMatch(desktop, autojs) && fixture.allowOutputDifference !== true) {
      return {
        status: "different",
        classification: "bug",
        comparisonAvailable: true,
        reason: "Fixture is partial, but output differences were not marked as expected.",
      };
    }
    return {
      status: recordsMatch(desktop, autojs) ? "matched-supported-subset" : "different-supported-subset",
      classification: "partial",
      comparisonAvailable: true,
      reason: fixture.reason || "AutoJs6 supports the probed subset while keeping the broader desktop surface limited.",
    };
  }

  return {
    status: "unknown",
    classification: "bug",
    comparisonAvailable: true,
    reason: "Unhandled classification: " + fixture.expectedClassification,
  };
}

function runtimeOk(record, expectedOutput) {
  return !!record &&
    Number(record.exitCode) === 0 &&
    record.expectedOutputFound === true &&
    normalizeOutput(record.stdout).includes(expectedOutput);
}

function recordsMatch(left, right) {
  return Number(left.exitCode) === Number(right.exitCode) &&
    normalizeOutput(left.stdout) === normalizeOutput(right.stdout) &&
    String(left.errorCode || "") === String(right.errorCode || "") &&
    String(left.errorMessagePattern || "") === String(right.errorMessagePattern || "");
}

function summarize(rows) {
  const byClassification = {};
  rows.forEach((row) => {
    const key = row.comparison.classification;
    byClassification[key] = (byClassification[key] || 0) + 1;
  });
  return {
    fixtures: rows.length,
    byClassification,
    exactMatches: byClassification["exact match"] || 0,
    intentionalRestrictions: byClassification["intentional AutoJs6 restriction"] || 0,
    partial: byClassification.partial || 0,
    bugs: byClassification.bug || 0,
    desktopFailures: rows.filter((row) => row.comparison.status === "desktop-failed").length,
    autoJsMissing: rows.filter((row) => !row.autojs).length,
    comparisonAvailable: rows.some((row) => row.autojs),
  };
}

function renderMarkdown(report) {
  const lines = [];
  lines.push("# Modern Core Differential Report");
  lines.push("");
  lines.push("- generatedAt: `" + report.generatedAt + "`");
  lines.push("- corpusVersion: `" + escapeMd(report.corpusVersion || "unknown") + "`");
  lines.push("- desktopNode: `" + escapeMd(report.desktopNode.version) + "` via `" + escapeMd(report.desktopNode.binary) + "`");
  lines.push("- autoJsSource: `" + escapeMd(report.autoJsSource || "not supplied") + "`");
  lines.push("");
  lines.push("## Summary");
  lines.push("");
  lines.push("| Classification | Count |");
  lines.push("| --- | ---: |");
  CLASSIFICATIONS.forEach((classification) => {
    lines.push("| " + classification + " | " + (report.summary.byClassification[classification] || 0) + " |");
  });
  lines.push("| AutoJs6 results missing | " + report.summary.autoJsMissing + " |");
  lines.push("");
  lines.push("## Fixtures");
  lines.push("");
  lines.push("| Fixture | Area | Classification | Status | Reason |");
  lines.push("| --- | --- | --- | --- | --- |");
  report.rows.forEach((row) => {
    lines.push([
      "| " + escapeMd(row.fixture),
      escapeMd(row.area),
      escapeMd(row.comparison.classification),
      escapeMd(row.comparison.status),
      escapeMd(row.comparison.reason),
    ].join(" | ") + " |");
  });
  lines.push("");
  lines.push("## Notes");
  lines.push("");
  lines.push("- `exact match` requires identical normalized stdout, exit code, error code, and error message pattern.");
  lines.push("- `partial` marks a supported AutoJs6 subset that intentionally does not claim the full desktop Node surface.");
  lines.push("- `intentional AutoJs6 restriction` marks an explicit Safe Node Profile policy boundary.");
  lines.push("- Supply Android logcat output with `--autojs-log` to compare against `moderncore.differential.result=` records emitted by the instrumentation corpus.");
  lines.push("");
  return lines.join("\n");
}

function parseMarkedError(stderr) {
  const line = String(stderr || "").split(/\r?\n/).find((entry) => entry.startsWith(ERROR_MARKER));
  if (!line) {
    return null;
  }
  return JSON.parse(line.slice(ERROR_MARKER.length));
}

function normalizeStderr(value) {
  return normalizeOutput(
    String(value || "").split(/\r?\n/)
      .filter((line) => !line.startsWith(ERROR_MARKER))
      .join("\n"),
  );
}

function normalizeOutput(value) {
  return String(value || "")
    .replace(/\r\n/g, "\n")
    .replace(/\r/g, "\n")
    .split("\n")
    .map((line) => line.trimEnd())
    .join("\n")
    .trim();
}

function errorMessagePattern(value) {
  return String(value || "")
    .replace(/[A-Za-z]:[\\/][^'")\s]+/g, "<path>")
    .replace(/\d+/g, "<n>");
}

function runNodeVersion(nodeBinary) {
  const result = childProcess.spawnSync(nodeBinary, ["--version"], { encoding: "utf8" });
  if (result.error) {
    throw result.error;
  }
  return normalizeOutput(result.stdout || result.stderr || "unknown");
}

function readJson(file) {
  return JSON.parse(readText(file));
}

function readText(file) {
  return fs.readFileSync(file, "utf8");
}

function escapeMd(value) {
  return String(value == null ? "" : value).replace(/\|/g, "\\|").replace(/\n/g, "<br>");
}

main();
