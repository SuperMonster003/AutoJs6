#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const FIELD_PATTERN = /([A-Za-z0-9_]+)=("(?:\\.|[^"])*"|[^\s]+)/g;

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(__dirname, "../../..");
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "app/build/reports/nodejs/resolver"));
  const traceFile = options.traceFile ? path.resolve(options.traceFile) : null;
  const traceText = traceFile && fs.existsSync(traceFile) ? fs.readFileSync(traceFile, "utf8") : null;
  const report = buildReport({
    request: options.request || "",
    from: options.from || "",
    traceFile,
    traceText,
  });

  fs.mkdirSync(reportDir, { recursive: true });
  const jsonPath = path.join(reportDir, "resolve-explanation.json");
  const textPath = path.join(reportDir, "resolve-explanation.txt");
  fs.writeFileSync(jsonPath, JSON.stringify(report.json, null, 2) + "\n");
  fs.writeFileSync(textPath, report.text);

  console.log("Resolver explanation JSON: " + jsonPath);
  console.log("Resolver explanation text: " + textPath);
  if (!traceText) {
    console.log("No trace file was available. Capture stderr/logcat with AUTOJS6_NODE_RESOLVE_TRACE=1 and pass --trace-file <file>.");
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
    if (value === undefined) {
      throw new Error("Missing value for " + arg);
    }
    options[key] = value;
  }
  return options;
}

function printHelp() {
  console.log([
    "Usage:",
    "  node tools/nodejs/resolver/explain-node-resolve.js --request <specifier> --from <file> --trace-file <stderr-log>",
    "",
    "Options:",
    "  --request <specifier>   Module specifier to explain.",
    "  --from <file>           Parent module filename from the failing require/import.",
    "  --trace-file <file>     File containing AUTOJS6_NODE_RESOLVE_TRACE=1 output.",
    "  --report-dir <dir>      Output directory. Defaults to build/reports/nodejs/resolver.",
  ].join("\n"));
}

function parseTraceLine(line) {
  const prefix = "[resolve] ";
  const index = line.indexOf(prefix);
  if (index < 0) {
    return null;
  }
  const raw = line.slice(index);
  const payload = raw.slice(prefix.length).trim();
  const firstField = /\s[A-Za-z0-9_]+=/.exec(payload);
  const event = firstField ? payload.slice(0, firstField.index).trim() : payload;
  const fieldsText = firstField ? payload.slice(firstField.index + 1) : "";
  const fields = {};
  FIELD_PATTERN.lastIndex = 0;
  let match;
  while ((match = FIELD_PATTERN.exec(fieldsText)) !== null) {
    fields[match[1]] = unquoteField(match[2]);
  }
  return { event, fields, raw };
}

function unquoteField(value) {
  const trimmed = String(value || "").trim();
  if (!trimmed.startsWith("\"")) {
    return trimmed;
  }
  try {
    return String(JSON.parse(trimmed));
  } catch (_) {
    return trimmed.slice(1, -1);
  }
}

function parseTrace(text) {
  const groups = [];
  for (const line of String(text || "").split(/\r?\n/)) {
    const event = parseTraceLine(line);
    if (!event) {
      continue;
    }
    if (event.event === "request" || groups.length === 0) {
      groups.push([event]);
    } else {
      groups[groups.length - 1].push(event);
    }
  }
  return groups.map((events) => {
    const request = (events.find((event) => event.event === "request") || { fields: {} }).fields.request || "";
    const from = (events.find((event) => event.event === "request") || { fields: {} }).fields.from || "";
    return { request, from, events };
  });
}

function normalizePath(value) {
  return String(value || "").replace(/\\/g, "/").replace(/\/+$/, "");
}

function pathMatches(actual, expected) {
  if (!expected) {
    return true;
  }
  const normalizedActual = normalizePath(actual);
  const normalizedExpected = normalizePath(expected);
  return normalizedActual === normalizedExpected || normalizedActual.endsWith("/" + normalizedExpected);
}

function selectGroups(groups, request, from) {
  const byRequest = groups.filter((group) => !request || group.request === request);
  const byFrom = byRequest.filter((group) => pathMatches(group.from, from));
  if (byFrom.length > 0) {
    return byFrom;
  }
  if (byRequest.length > 0) {
    return byRequest;
  }
  return !request && groups.length > 0 ? groups : [];
}

function distinct(values) {
  return Array.from(new Set(values.map((value) => String(value || "").trim()).filter(Boolean)));
}

function buildReport({ request, from, traceFile, traceText }) {
  const groups = traceText ? parseTrace(traceText) : [];
  const selectedGroups = selectGroups(groups, request, from);
  const events = selectedGroups.flatMap((group) => group.events);
  const searchPaths = distinct(
    events
      .filter((event) => event.event === "node_modules search")
      .flatMap((event) => String(event.fields.paths || "").split("|"))
  );
  const packageJsonFieldsRead = distinct(
    events
      .filter((event) => event.event === "package.json found" || event.event === "package scope")
      .map((event) => event.fields.file || event.fields.packageDir)
  );
  const packageBranchesSelected = events
    .filter((event) =>
      event.event.startsWith("exports") ||
      event.event.startsWith("imports") ||
      event.event === "main matched" ||
      event.event === "index matched" ||
      event.event === "self-reference matched"
    )
    .map((event) => event.raw);
  const nullBlocking = events
    .filter((event) => event.event.includes("blocked") || event.fields.target === "null")
    .map((event) => event.raw);
  const finalErrors = events
    .filter((event) => event.event === "error")
    .map((event) => ({
      code: event.fields.code || "",
      autojs6Code: event.fields.autojs6Code || "",
      message: event.fields.message || "",
      raw: event.raw,
    }));
  const rawTrace = events.map((event) => event.raw);
  const json = {
    schema: "autojs6-node-resolver-visualizer-v1",
    generatedAt: new Date().toISOString(),
    request,
    from,
    traceFile,
    traceAvailable: traceText !== null,
    traceGroups: groups.length,
    selectedGroups: selectedGroups.length,
    searchPaths,
    packageJsonFieldsRead,
    packageBranchesSelected,
    nullBlocking,
    finalErrors,
    rawTrace,
  };
  return {
    json,
    text: renderText(json),
  };
}

function renderText(report) {
  const lines = [
    "AutoJs6 Node Resolver Explanation",
    "Generated at: " + report.generatedAt,
    "Request: " + (report.request || "not specified"),
    "From: " + (report.from || "not specified"),
    "Trace file: " + (report.traceFile || "not specified"),
    "Trace available: " + report.traceAvailable,
    "Trace groups: " + report.traceGroups,
    "Selected groups: " + report.selectedGroups,
    "",
  ];
  if (!report.traceAvailable) {
    lines.push(
      "No trace file was available.",
      "Run the failing script with AUTOJS6_NODE_RESOLVE_TRACE=1, capture stderr/logcat, then pass --trace-file <file>.",
      ""
    );
  }
  appendList(lines, "Search paths:", report.searchPaths);
  appendList(lines, "package.json fields read:", report.packageJsonFieldsRead);
  appendList(lines, "Exports/imports/main branch:", report.packageBranchesSelected);
  appendList(lines, "Null blocking:", report.nullBlocking);
  appendList(lines, "Final errors:", report.finalErrors.map((error) => "code=" + error.code + " autojs6Code=" + error.autojs6Code + " message=" + error.message));
  appendList(lines, "Raw trace:", report.rawTrace, "none selected");
  return lines.join("\n") + "\n";
}

function appendList(lines, title, values, emptyText = "none observed") {
  lines.push(title);
  if (!values || values.length === 0) {
    lines.push("  " + emptyText, "");
    return;
  }
  for (const value of values) {
    lines.push("  " + value);
  }
  lines.push("");
}

main();
