#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const ERROR_MARKER = "__AUTOJS6_DIFFERENTIAL_ERROR__=";
const AUTOJS_RESULT_PREFIX = "realnpm.differential.result=";
const EXPECTED_CLASSIFICATIONS = new Set([
  "expected AutoJs6 restriction",
  "flaky",
]);

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

  const autoJsImport = readAutoJsResults(options);
  validateAutoJsFixtureSet(fixtures, autoJsImport);
  const autoJsResults = autoJsImport.results;
  const rows = fixtures.map((fixture) => {
    validateFixture(fixture);
    const fixtureDir = path.join(fixtureRoot, fixture.name);
    const expectedOutput = readText(path.join(fixtureDir, "expected.txt")).trim();
    const desktopFixture = resolveDesktopFixture(fixture, fixtureDir, expectedOutput);
    const desktop = runDesktopFixture(
      nodeBinary,
      fixtureDir,
      fixture.name,
      desktopFixture.testPath,
      desktopFixture.expectedOutput,
    );
    const autojs = autoJsResults.get(fixture.name) || null;
    return {
      fixture: fixture.name,
      patterns: fixture.patterns || [],
      expectedClassification: fixture.expectedClassification || "",
      reason: fixture.reason || "",
      expectedOutput,
      desktopExpectedOutput: desktopFixture.expectedOutput,
      desktop,
      autojs,
      comparison: compareResults(fixture, expectedOutput, desktop, autojs),
    };
  });

  const report = {
    generatedAt: new Date().toISOString(),
    fixtureRoot,
    desktopNode: {
      binary: nodeBinary,
      version: runNodeVersion(nodeBinary),
    },
    autoJsSource: autoJsImport.sources.length > 0 ? autoJsImport.sources.join("; ") : null,
    autoJsSources: autoJsImport.sources,
    autoJsImportedRecords: autoJsImport.recordCount,
    autoJsImportedFixtures: autoJsImport.fixtureCount,
    rows,
    summary: summarize(rows),
    phase13Compatibility: summarizePhase13CompatibilityCategories(rows, manifest.phase13Compatibility),
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
    "  --autojs-connected-results <dir|file>  Scan Gradle connected test results/logcat output for realnpm.differential.result= JSON lines.",
    "  --compat-report <file>  Output v1.1 npm compatibility Markdown report.",
    "  --v11-differential-report <file>  Output v1.1 differential Markdown report.",
  ].join("\n"));
}

function validateFixture(fixture) {
  if (!fixture || typeof fixture.name !== "string" || !fixture.name) {
    throw new Error("Differential fixture is missing name");
  }
  if (fixture.expectedClassification && !EXPECTED_CLASSIFICATIONS.has(fixture.expectedClassification)) {
    throw new Error(
      "Unsupported expected classification for " + fixture.name + ": " + fixture.expectedClassification,
    );
  }
  if (fixture.desktop != null) {
    if (!fixture.desktop || typeof fixture.desktop !== "object" || Array.isArray(fixture.desktop)) {
      throw new Error("Desktop fixture configuration must be an object: " + fixture.name);
    }
    if (!fixture.desktop.test || !fixture.desktop.expected) {
      throw new Error("Desktop fixture configuration must provide test and expected files: " + fixture.name);
    }
  }
  if (fixture.expectedClassification === "expected AutoJs6 restriction" && !fixture.desktop) {
    throw new Error("Expected AutoJs6 restriction requires a desktop baseline fixture: " + fixture.name);
  }
  if (fixture.expectedClassification === "expected AutoJs6 restriction" && !fixture.autoJsRestrictionPattern) {
    throw new Error("Expected AutoJs6 restriction requires an AutoJs6 marker: " + fixture.name);
  }
  if (fixture.expectedClassification === "flaky") {
    if (!fixture.allowedOutputPattern) {
      throw new Error("Flaky fixture requires an allowed output pattern: " + fixture.name);
    }
    try {
      new RegExp(fixture.allowedOutputPattern);
    } catch (error) {
      throw new Error("Invalid allowed output pattern for " + fixture.name + ": " + error.message);
    }
  }
}

function resolveDesktopFixture(fixture, fixtureDir, defaultExpectedOutput) {
  if (!fixture.desktop) {
    return {
      testPath: path.join(fixtureDir, "test.cjs"),
      expectedOutput: defaultExpectedOutput,
    };
  }
  const testPath = resolveFixtureFile(fixtureDir, fixture.desktop.test, fixture.name + " desktop test");
  const expectedPath = resolveFixtureFile(
    fixtureDir,
    fixture.desktop.expected,
    fixture.name + " desktop expected output",
  );
  return {
    testPath,
    expectedOutput: readText(expectedPath).trim(),
  };
}

function resolveFixtureFile(fixtureDir, relativePath, label) {
  const root = path.resolve(fixtureDir);
  const resolved = path.resolve(root, String(relativePath || ""));
  const relative = path.relative(root, resolved);
  if (!relative || relative === ".." || relative.startsWith(".." + path.sep) || path.isAbsolute(relative)) {
    throw new Error("Invalid " + label + " path: " + relativePath);
  }
  if (!fs.statSync(resolved).isFile()) {
    throw new Error("Missing " + label + " file: " + resolved);
  }
  return resolved;
}

function runDesktopFixture(nodeBinary, fixtureDir, fixtureName, testPath, expectedOutput) {
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
  const sources = [];
  let recordCount = 0;
  function addRecords(source, records) {
    if (!sources.includes(source)) {
      sources.push(source);
    }
    recordCount += records.length;
    records.forEach((record) => {
      validateAutoJsRecord(record, source);
      const fixture = record.fixture;
      const normalized = normalizeRuntimeRecord(record);
      const existing = results.get(fixture);
      if (existing && JSON.stringify(existing) !== JSON.stringify(normalized)) {
        throw new Error("Conflicting AutoJs6 differential records for fixture " + fixture + " in " + source);
      }
      results.set(fixture, normalized);
    });
  }
  [options.autojsJson, options.autojsLog].filter(Boolean).forEach((file) => {
    const resolved = path.resolve(file);
    const text = fs.readFileSync(resolved, "utf8");
    const records = file === options.autojsJson ? parseAutoJsJson(text) : parseAutoJsLog(text);
    addRecords(resolved, records);
  });
  if (options.autojsConnectedResults) {
    const connectedRoot = path.resolve(options.autojsConnectedResults);
    const connected = readAutoJsConnectedResults(connectedRoot);
    if (connected.files.length === 0) {
      sources.push(connectedRoot);
    }
    connected.files.forEach((file) => {
      addRecords(file, connected.recordsByFile.get(file) || []);
    });
  }
  return {
    results,
    sources,
    recordCount,
    fixtureCount: results.size,
    comparisonRequested: Boolean(options.autojsJson || options.autojsLog || options.autojsConnectedResults),
  };
}

function validateAutoJsRecord(record, source) {
  if (!record || typeof record !== "object" || Array.isArray(record)) {
    throw new Error("Invalid AutoJs6 differential record in " + source);
  }
  const requiredStrings = [
    "runtime",
    "fixture",
    "stdout",
    "stderr",
    "errorCode",
    "errorMessage",
    "errorMessagePattern",
    "expectedOutput",
  ];
  requiredStrings.forEach((field) => {
    if (typeof record[field] !== "string") {
      throw new Error("AutoJs6 differential record field " + field + " must be a string in " + source);
    }
  });
  if (record.runtime !== "autojs6") {
    throw new Error("Unexpected differential runtime for " + record.fixture + " in " + source + ": " + record.runtime);
  }
  if (!record.fixture) {
    throw new Error("AutoJs6 differential record fixture must not be blank in " + source);
  }
  if (!Number.isInteger(record.exitCode) || ![0, 1].includes(record.exitCode)) {
    throw new Error("AutoJs6 differential record exitCode must be 0 or 1 for " + record.fixture + " in " + source);
  }
  if (typeof record.expectedOutputFound !== "boolean") {
    throw new Error(
      "AutoJs6 differential record expectedOutputFound must be boolean for " + record.fixture + " in " + source,
    );
  }
}

function validateAutoJsFixtureSet(fixtures, autoJsImport) {
  if (!autoJsImport.comparisonRequested) {
    return;
  }
  const expected = new Set(fixtures.map((fixture) => String(fixture.name)));
  const actual = new Set(autoJsImport.results.keys());
  const missing = Array.from(expected).filter((fixture) => !actual.has(fixture));
  const unknown = Array.from(actual).filter((fixture) => !expected.has(fixture));
  if (missing.length > 0 || unknown.length > 0) {
    throw new Error([
      "AutoJs6 differential fixture set is incomplete or unexpected.",
      "Missing: " + (missing.join(", ") || "none"),
      "Unknown: " + (unknown.join(", ") || "none"),
    ].join(" "));
  }
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
  const output = [];
  String(text).split(/\r?\n/).forEach((line) => {
    let searchFrom = 0;
    while (searchFrom < line.length) {
      const index = line.indexOf(AUTOJS_RESULT_PREFIX, searchFrom);
      if (index < 0) {
        break;
      }
      const payload = line.slice(index + AUTOJS_RESULT_PREFIX.length).trim();
      const jsonText = extractJsonObject(payload);
      if (!jsonText) {
        break;
      }
      output.push(parseJsonPayload(jsonText));
      searchFrom = index + AUTOJS_RESULT_PREFIX.length + jsonText.length;
    }
  });
  return output;
}

function readAutoJsConnectedResults(root) {
  const files = collectAutoJsResultFiles(root);
  const recordsByFile = new Map();
  files.forEach((file) => {
    const text = fs.readFileSync(file, "utf8");
    if (!text.includes(AUTOJS_RESULT_PREFIX)) {
      return;
    }
    const records = parseAutoJsLog(text);
    if (records.length > 0) {
      recordsByFile.set(file, records);
    }
  });
  return {
    files: Array.from(recordsByFile.keys()),
    recordsByFile,
  };
}

function collectAutoJsResultFiles(root) {
  const stat = fs.statSync(root);
  if (stat.isFile()) {
    return [root];
  }
  if (!stat.isDirectory()) {
    return [];
  }
  const output = [];
  const stack = [root];
  while (stack.length > 0) {
    const dir = stack.pop();
    fs.readdirSync(dir, { withFileTypes: true }).forEach((entry) => {
      const file = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        stack.push(file);
      } else if (entry.isFile() && isAutoJsResultTextFile(file)) {
        output.push(file);
      }
    });
  }
  output.sort();
  return output;
}

function isAutoJsResultTextFile(file) {
  const extension = path.extname(file).toLowerCase();
  return extension === ".txt" ||
    extension === ".log" ||
    extension === ".xml" ||
    extension === ".textproto";
}

function extractJsonObject(value) {
  const text = String(value || "");
  const start = text.indexOf("{");
  if (start < 0) {
    const trimmed = text.trim();
    return trimmed.length > 0 ? trimmed : "";
  }
  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let index = start; index < text.length; index += 1) {
    const ch = text[index];
    if (inString) {
      if (escaped) {
        escaped = false;
      } else if (ch === "\\") {
        escaped = true;
      } else if (ch === "\"") {
        inString = false;
      }
      continue;
    }
    if (ch === "\"") {
      inString = true;
    } else if (ch === "{") {
      depth += 1;
    } else if (ch === "}") {
      depth -= 1;
      if (depth === 0) {
        return text.slice(start, index + 1);
      }
    }
  }
  return text.slice(start).trim();
}

function parseJsonPayload(payload) {
  try {
    return JSON.parse(payload);
  } catch (error) {
    return JSON.parse(unescapeXml(payload));
  }
}

function unescapeXml(value) {
  return String(value)
    .replace(/&quot;/g, "\"")
    .replace(/&apos;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&amp;/g, "&");
}

function normalizeRuntimeRecord(record) {
  return {
    runtime: record.runtime,
    fixture: record.fixture,
    exitCode: record.exitCode,
    stdout: normalizeOutput(record.stdout),
    stderr: normalizeOutput(record.stderr),
    errorCode: record.errorCode,
    errorMessage: record.errorMessage,
    errorMessagePattern: record.errorMessagePattern,
    expectedOutput: record.expectedOutput,
    expectedOutputFound: record.expectedOutputFound,
  };
}

function compareResults(fixture, expectedOutput, desktop, autojs) {
  if (!runtimePassed(desktop, desktop.expectedOutput, { allowStderr: true })) {
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
      comparisonAvailable: false,
      reason: "No AutoJs6 differential result was supplied; generated desktop baseline only.",
    };
  }

  if (!runtimePassed(autojs, expectedOutput)) {
    return {
      status: "autojs-failed",
      classification: "bug",
      comparisonAvailable: true,
      reason: "AutoJs6 did not pass the fixture or emit the expected marker.",
    };
  }

  if (fixture.expectedClassification === "expected AutoJs6 restriction") {
    const restrictionPattern = String(fixture.autoJsRestrictionPattern || "");
    const restrictionMarker = normalizeOutput(autojs.stdout) + "\n" + String(autojs.errorCode || "");
    if (!restrictionMarker.includes(restrictionPattern)) {
      return {
        status: "restriction-missing",
        classification: "bug",
        comparisonAvailable: true,
        reason: "AutoJs6 did not emit the expected policy result for the runtime-specific fixture.",
      };
    }
    return {
      status: "restricted",
      classification: "expected AutoJs6 restriction",
      comparisonAvailable: true,
      reason: fixture.reason || "AutoJs6 intentionally restricts this desktop Node capability.",
    };
  }

  if (fixture.expectedClassification === "flaky") {
    const allowedOutput = new RegExp(fixture.allowedOutputPattern);
    if (!allowedOutput.test(normalizeOutput(desktop.stdout)) || !allowedOutput.test(normalizeOutput(autojs.stdout))) {
      return {
        status: "unexpected-output",
        classification: "bug",
        comparisonAvailable: true,
        reason: "A flaky fixture emitted output outside its manifest allowlist.",
      };
    }
    const recordsMatch = Number(desktop.exitCode) === Number(autojs.exitCode) &&
      normalizeOutput(desktop.stdout) === normalizeOutput(autojs.stdout) &&
      String(desktop.errorCode || "") === String(autojs.errorCode || "") &&
      String(desktop.errorMessagePattern || "") === String(autojs.errorMessagePattern || "");
    return {
      status: recordsMatch ? "matched" : "different-allowed",
      classification: recordsMatch ? "matched" : "flaky",
      comparisonAvailable: true,
      reason: fixture.reason || "The fixture permits a documented nondeterministic runtime result.",
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
  return {
    status: "different",
    classification: "bug",
    reason: "Runtime results differ without a known AutoJs6 policy error.",
  };
}

function runtimePassed(record, expectedOutput, options = {}) {
  const marker = String(expectedOutput || "");
  return !!record && marker.length > 0 && Number(record.exitCode) === 0 &&
    record.expectedOutputFound === true && normalizeOutput(record.stdout).includes(marker) &&
    String(record.expectedOutput || "") === marker &&
    String(record.errorCode || "") === "" && String(record.errorMessage || "") === "" &&
    String(record.errorMessagePattern || "") === "" &&
    (options.allowStderr === true || normalizeOutput(record.stderr) === "");
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
    if (!runtimePassed(row.desktop, row.desktopExpectedOutput, { allowStderr: true })) {
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
      runtimePassed(row.desktop, row.desktopExpectedOutput, { allowStderr: true }) ? "pass" : "fail",
      row.autojs ? (runtimePassed(row.autojs, row.expectedOutput) ? "pass" : "fail") : "not supplied",
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
      runtimePassed(row.desktop, row.desktopExpectedOutput, { allowStderr: true }) ? "pass" : "fail",
      row.autojs ? (runtimePassed(row.autojs, row.expectedOutput) ? "pass" : "fail") : "not supplied",
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
  const phase13Categories = report.phase13Compatibility && report.phase13Compatibility.categories || [];
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
  if (phase13Categories.length > 0) {
    lines.push("## Phase 13 Dependency Compatibility Categories");
    lines.push("");
    lines.push("| Category | Reason kind | Fixtures | Desktop pass-rate | AutoJs6 pass-rate | Package reasons |");
    lines.push("| --- | --- | --- | ---: | ---: | --- |");
    phase13Categories.forEach((category) => {
      lines.push([
        category.title || category.id,
        category.reasonKind,
        category.fixtures.join(", ") || "none",
        renderPassRate(category.passRate.desktop),
        renderPassRate(category.passRate.autojs),
        category.packages.map((row) => row.name + ": " + row.reason).join("; "),
      ].map(markdownCell).join(" | ").replace(/^/, "| ").replace(/$/, " |"));
    });
    lines.push("");
  }
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
      runtimePassed(row.desktop, row.desktopExpectedOutput, { allowStderr: true }) ? "pass" : "fail",
      row.autojs ? (runtimePassed(row.autojs, row.expectedOutput) ? "pass" : "fail") : "not supplied",
      row.comparison.classification,
      row.comparison.reason,
    ].map(markdownCell).join(" | ").replace(/^/, "| ").replace(/$/, " |"));
  });
  return lines.join("\n") + "\n";
}

function summarizePhase13CompatibilityCategories(rows, metadata) {
  if (!metadata || !Array.isArray(metadata.categories)) {
    return {
      schema: "",
      fixtureCount: rows.length,
      requiredReasonKinds: [],
      categories: [],
    };
  }
  const rowByFixture = new Map(rows.map((row) => [row.fixture, row]));
  return {
    schema: String(metadata.schema || ""),
    fixtureCount: Number(metadata.fixtureCount || rows.length),
    requiredReasonKinds: Array.isArray(metadata.requiredReasonKinds) ? metadata.requiredReasonKinds.map(String) : [],
    categories: metadata.categories.map((category) => {
      const fixtures = Array.isArray(category.fixtures) ? category.fixtures.map(String) : [];
      const matchedRows = fixtures.map((fixture) => rowByFixture.get(fixture)).filter(Boolean);
      return {
        id: String(category.id || ""),
        title: String(category.title || category.id || ""),
        reasonKind: String(category.reasonKind || ""),
        fixtures,
        packages: Array.isArray(category.packages)
          ? category.packages.map((row) => ({
            name: String(row && row.name || ""),
            reason: String(row && row.reason || ""),
          }))
          : [],
        counts: countClassifications(matchedRows),
        passRate: {
          desktop: passRate(
            matchedRows,
            (row) => runtimePassed(row.desktop, row.desktopExpectedOutput, { allowStderr: true }),
          ),
          autojs: passRate(
            matchedRows.filter((row) => row.autojs),
            (row) => runtimePassed(row.autojs, row.expectedOutput),
          ),
        },
      };
    }),
  };
}

function passRate(rows, predicate) {
  if (!rows.length) {
    return {
      passed: 0,
      total: 0,
      ratio: null,
    };
  }
  const passed = rows.filter(predicate).length;
  return {
    passed,
    total: rows.length,
    ratio: passed / rows.length,
  };
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

function renderPassRate(value) {
  if (!value || !value.total) {
    return "n/a";
  }
  return value.passed + "/" + value.total;
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
