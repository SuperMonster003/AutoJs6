#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const ERROR_MARKER = "__AUTOJS6_DIFFERENTIAL_ERROR__=";
const AUTOJS_RESULT_PREFIX = "realnpm.differential.result=";

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(__dirname, "../../..");
  const fixtureRoot = path.resolve(options.fixtures || path.join(repoRoot, "app/src/androidTest/assets/npm_compat_real"));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs/differential"));
  const nodeBinary = options.node || process.execPath;

  const manifest = readJson(path.join(fixtureRoot, "manifest.json"));
  const fixtures = manifest.fixtures || [];
  if (!Array.isArray(fixtures) || fixtures.length === 0) {
    throw new Error("No differential fixtures found in " + path.join(fixtureRoot, "manifest.json"));
  }

  const autoJsResults = readAutoJsResults(options);
  const rows = fixtures.map((fixture) => {
    const fixtureDir = path.join(fixtureRoot, fixture.name);
    const expectedOutput = readText(path.join(fixtureDir, "expected.txt")).trim();
    const desktop = runDesktopFixture(nodeBinary, fixtureDir, fixture.name, expectedOutput);
    const autojs = autoJsResults.get(fixture.name) || null;
    return {
      fixture: fixture.name,
      patterns: fixture.patterns || [],
      expectedOutput,
      desktop,
      autojs,
      comparison: compareResults(desktop, autojs),
    };
  });

  const report = {
    generatedAt: new Date().toISOString(),
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
  const jsonPath = path.join(reportDir, "desktop-node-differential.json");
  const markdownPath = path.join(reportDir, "desktop-node-differential.md");
  const v11ReportPath = path.resolve(options.compatReport || path.join(path.dirname(reportDir), "npm-compat-v1.1.md"));
  const v11DifferentialPath = path.resolve(
    options.v11DifferentialReport || path.join(path.dirname(reportDir), "v1.1-differential.md"),
  );
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(markdownPath, renderMarkdown(report));
  fs.mkdirSync(path.dirname(v11ReportPath), { recursive: true });
  fs.writeFileSync(v11ReportPath, renderV11CompatMarkdown(report));
  fs.mkdirSync(path.dirname(v11DifferentialPath), { recursive: true });
  fs.writeFileSync(v11DifferentialPath, renderV11DifferentialMarkdown(report));

  console.log("Differential JSON: " + jsonPath);
  console.log("Differential Markdown: " + markdownPath);
  console.log("NPM compatibility v1.1 Markdown: " + v11ReportPath);
  console.log("Node Profile v1.1 differential Markdown: " + v11DifferentialPath);
  console.log("Differential summary: " + JSON.stringify(report.summary));

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
    "Usage: node tools/nodejs/differential/run-differential-tests.js [options]",
    "",
    "Options:",
    "  --fixtures <dir>      Fixture root. Defaults to app/src/androidTest/assets/npm_compat_real.",
    "  --report-dir <dir>    Output directory. Defaults to build/reports/nodejs/differential.",
    "  --node <path>         Desktop Node binary. Defaults to process.execPath.",
    "  --autojs-log <file>   Gradle/logcat text containing realnpm.differential.result= JSON lines.",
    "  --autojs-json <file>  JSON file containing an array, {results:[]}, or {rows:[{autojs}]} shape.",
    "  --compat-report <file>  Output v1.1 npm compatibility Markdown report.",
    "  --v11-differential-report <file>  Output v1.1 differential Markdown report.",
  ].join("\n"));
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
  throw new Error("Unsupported AutoJs differential JSON shape");
}

function parseAutoJsLog(text) {
  return String(text).split(/\r?\n/).map((line) => {
    const index = line.indexOf(AUTOJS_RESULT_PREFIX);
    if (index < 0) {
      return null;
    }
    const payload = line.slice(index + AUTOJS_RESULT_PREFIX.length).trim();
    return JSON.parse(payload);
  }).filter(Boolean);
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

function compareResults(desktop, autojs) {
  if (!desktop.expectedOutputFound) {
    return {
      status: "desktop-failed",
      classification: "bug",
      reason: "Desktop Node did not emit the fixture expected output.",
    };
  }
  if (!autojs) {
    return {
      status: "desktop-only",
      classification: "unsupported",
      reason: "No AutoJs6 differential result was supplied; generated desktop baseline only.",
    };
  }

  const exitMatches = Number(desktop.exitCode) === Number(autojs.exitCode);
  const stdoutMatches = normalizeOutput(desktop.stdout) === normalizeOutput(autojs.stdout);
  const errorCodeMatches = String(desktop.errorCode || "") === String(autojs.errorCode || "");
  const errorMessageMatches = String(desktop.errorMessagePattern || "") === String(autojs.errorMessagePattern || "");
  if (exitMatches && stdoutMatches && errorCodeMatches && errorMessageMatches) {
    return {
      status: "matched",
      classification: "matched",
      reason: "Desktop Node and AutoJs6 normalized outputs agree.",
    };
  }
  if (String(autojs.errorCode || "").startsWith("ERR_AUTOJS6_")) {
    return {
      status: "different",
      classification: "expected AutoJs6 restriction",
      reason: "AutoJs6 failed with a policy error while desktop Node did not.",
    };
  }
  if (desktop.exitCode !== 0 && autojs.exitCode !== 0) {
    return {
      status: "different",
      classification: "unsupported",
      reason: "Both runtimes failed; inspect normalized error patterns.",
    };
  }
  return {
    status: "different",
    classification: "bug",
    reason: "Runtime results differ without a known AutoJs6 policy error.",
  };
}

function summarize(rows) {
  const summary = {
    fixtures: rows.length,
    desktopFailures: 0,
    matched: 0,
    expectedRestrictions: 0,
    unsupported: 0,
    flaky: 0,
    bugs: 0,
  };
  rows.forEach((row) => {
    if (!row.desktop.expectedOutputFound || row.desktop.exitCode !== 0) {
      summary.desktopFailures += 1;
    }
    switch (row.comparison.classification) {
      case "matched":
        summary.matched += 1;
        break;
      case "expected AutoJs6 restriction":
        summary.expectedRestrictions += 1;
        break;
      case "unsupported":
        summary.unsupported += 1;
        break;
      case "flaky":
        summary.flaky += 1;
        break;
      case "bug":
        summary.bugs += 1;
        break;
      default:
        break;
    }
  });
  return summary;
}

function renderMarkdown(report) {
  const lines = [];
  lines.push("# Desktop Node Differential Report");
  lines.push("");
  lines.push("- Generated: `" + report.generatedAt + "`");
  lines.push("- Desktop Node: `" + report.desktopNode.version + "`");
  lines.push("- Fixture root: `" + report.fixtureRoot + "`");
  lines.push("- AutoJs source: `" + (report.autoJsSource || "not supplied") + "`");
  lines.push("");
  lines.push("| Fixture | Desktop | AutoJs6 | Classification | Reason |");
  lines.push("| --- | --- | --- | --- | --- |");
  report.rows.forEach((row) => {
    lines.push([
      row.fixture,
      row.desktop.expectedOutputFound && row.desktop.exitCode === 0 ? "pass" : "fail",
      row.autojs ? (row.autojs.exitCode === 0 ? "pass" : "fail") : "not supplied",
      row.comparison.classification,
      row.comparison.reason,
    ].map(markdownCell).join(" | ").replace(/^/, "| ").replace(/$/, " |"));
  });
  lines.push("");
  lines.push("```json");
  lines.push(JSON.stringify(report.summary, null, 2));
  lines.push("```");
  return lines.join("\n") + "\n";
}

function renderV11CompatMarkdown(report) {
  const categories = new Map();
  report.rows.forEach((row) => {
    (row.patterns || []).forEach((pattern) => {
      const list = categories.get(pattern) || [];
      list.push(row.fixture);
      categories.set(pattern, list);
    });
  });

  const lines = [];
  lines.push("# Node Profile v1.1 NPM Compatibility Report");
  lines.push("");
  lines.push("- Generated: `" + report.generatedAt + "`");
  lines.push("- Desktop Node: `" + report.desktopNode.version + "`");
  lines.push("- Fixture root: `" + report.fixtureRoot + "`");
  lines.push("- Fixed fixture count: `" + report.summary.fixtures + "`");
  lines.push("- Desktop failures: `" + report.summary.desktopFailures + "`");
  lines.push("- AutoJs source: `" + (report.autoJsSource || "not supplied") + "`");
  lines.push("");
  lines.push("## Fixture Coverage");
  lines.push("");
  lines.push("| Fixture | Patterns | Desktop baseline | AutoJs6 result |");
  lines.push("| --- | --- | --- | --- |");
  report.rows.forEach((row) => {
    lines.push([
      row.fixture,
      (row.patterns || []).join(", "),
      row.desktop.expectedOutputFound && row.desktop.exitCode === 0 ? "pass" : "fail",
      row.autojs ? (row.autojs.exitCode === 0 ? "pass" : "fail") : "not supplied",
    ].map(markdownCell).join(" | ").replace(/^/, "| ").replace(/$/, " |"));
  });
  lines.push("");
  lines.push("## Pattern Index");
  lines.push("");
  Array.from(categories.keys()).sort().forEach((pattern) => {
    lines.push("- `" + pattern + "`: " + categories.get(pattern).join(", "));
  });
  lines.push("");
  lines.push("## Summary");
  lines.push("");
  lines.push("```json");
  lines.push(JSON.stringify(report.summary, null, 2));
  lines.push("```");
  return lines.join("\n") + "\n";
}

function renderV11DifferentialMarkdown(report) {
  const categories = summarizeV11CapabilityCategories(report.rows);
  const lines = [];
  lines.push("# Node Profile v1.1 Differential Report");
  lines.push("");
  lines.push("- Generated: `" + report.generatedAt + "`");
  lines.push("- Desktop Node: `" + report.desktopNode.version + "`");
  lines.push("- Fixture root: `" + report.fixtureRoot + "`");
  lines.push("- AutoJs source: `" + (report.autoJsSource || "not supplied") + "`");
  lines.push("- Measurement mode: `" + (report.autoJsSource ? "desktop-vs-autojs6" : "desktop-baseline-only") + "`");
  lines.push("");
  lines.push("## Classification Summary");
  lines.push("");
  lines.push("| Classification | Count |");
  lines.push("| --- | ---: |");
  lines.push("| matched | `" + report.summary.matched + "` |");
  lines.push("| expected AutoJs6 restriction | `" + report.summary.expectedRestrictions + "` |");
  lines.push("| unsupported | `" + report.summary.unsupported + "` |");
  lines.push("| flaky | `" + report.summary.flaky + "` |");
  lines.push("| bug | `" + report.summary.bugs + "` |");
  lines.push("| desktop failures | `" + report.summary.desktopFailures + "` |");
  lines.push("");
  lines.push("## Required v1.1 Coverage");
  lines.push("");
  lines.push("| Capability area | Fixtures | Classification counts |");
  lines.push("| --- | --- | --- |");
  categories.forEach((category) => {
    lines.push([
      category.name,
      category.fixtures.join(", ") || "none",
      renderClassificationCounts(category.counts),
    ].map(markdownCell).join(" | ").replace(/^/, "| ").replace(/$/, " |"));
  });
  lines.push("");
  lines.push("## Fixture Results");
  lines.push("");
  lines.push("| Fixture | Patterns | Desktop | AutoJs6 | Classification | Reason |");
  lines.push("| --- | --- | --- | --- | --- | --- |");
  report.rows.forEach((row) => {
    lines.push([
      row.fixture,
      (row.patterns || []).join(", "),
      row.desktop.expectedOutputFound && row.desktop.exitCode === 0 ? "pass" : "fail",
      row.autojs ? (row.autojs.exitCode === 0 ? "pass" : "fail") : "not supplied",
      row.comparison.classification,
      row.comparison.reason,
    ].map(markdownCell).join(" | ").replace(/^/, "| ").replace(/$/, " |"));
  });
  return lines.join("\n") + "\n";
}

function summarizeV11CapabilityCategories(rows) {
  return [
    {
      name: "ESM packages",
      matches: ["esm", "hybrid", "exports import", "exports"],
    },
    {
      name: "stream packages",
      matches: ["stream utility"],
    },
    {
      name: "zlib packages",
      matches: ["zlib"],
    },
    {
      name: "fetch packages",
      matches: ["fetch"],
    },
    {
      name: "buffer packages",
      matches: ["buffer"],
    },
  ].map((category) => {
    const matchedRows = rows.filter((row) => {
      const haystack = (row.patterns || []).join(" ").toLowerCase();
      return category.matches.some((needle) => haystack.includes(needle));
    });
    return {
      name: category.name,
      fixtures: matchedRows.map((row) => row.fixture),
      counts: countClassifications(matchedRows),
    };
  });
}

function countClassifications(rows) {
  return rows.reduce((counts, row) => {
    const classification = row.comparison.classification;
    counts[classification] = (counts[classification] || 0) + 1;
    return counts;
  }, {});
}

function renderClassificationCounts(counts) {
  return [
    "matched",
    "expected AutoJs6 restriction",
    "unsupported",
    "flaky",
    "bug",
  ].filter((classification) => counts[classification] > 0)
    .map((classification) => classification + "=" + counts[classification])
    .join(", ") || "none";
}

function markdownCell(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\r?\n/g, " ");
}

function readJson(file) {
  return JSON.parse(readText(file));
}

function readText(file) {
  return fs.readFileSync(file, "utf8");
}

function runNodeVersion(nodeBinary) {
  const result = childProcess.spawnSync(nodeBinary, ["--version"], { encoding: "utf8" });
  return normalizeOutput(result.stdout || result.stderr || "unknown");
}

function parseMarkedError(stderr) {
  const line = String(stderr || "").split(/\r?\n/).find((candidate) => candidate.startsWith(ERROR_MARKER));
  if (!line) {
    return null;
  }
  return JSON.parse(line.slice(ERROR_MARKER.length));
}

function normalizeStderr(stderr) {
  return String(stderr || "")
    .split(/\r?\n/)
    .filter((line) => !line.startsWith(ERROR_MARKER))
    .join("\n")
    .trim();
}

function normalizeOutput(value) {
  return String(value || "")
    .replace(/\r\n/g, "\n")
    .split("\n")
    .map((line) => line.trimEnd())
    .filter((line) => line.length > 0 && !line.startsWith(AUTOJS_RESULT_PREFIX))
    .join("\n");
}

function errorMessagePattern(message) {
  return String(message || "")
    .split(/\r?\n/)[0]
    .replace(/[A-Za-z]:[\\/][^\s)]+/g, "<path>")
    .replace(/\/(?:data|storage|sdcard)\/[^\s)]+/g, "<path>")
    .replace(/\d+/g, "<n>");
}

try {
  main();
} catch (error) {
  console.error(error && error.stack || error);
  process.exitCode = 1;
}
