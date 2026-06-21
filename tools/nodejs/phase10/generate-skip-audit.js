#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const TARGET_ROOTS = [
  "app/src/androidTest/java",
  "app/src/test/java",
];

const PATTERNS = [
  { id: "assume", regex: /\bAssume\.(assumeTrue|assumeFalse|assumeNoException)|\bassumeTrue\s*\(|\bassumeFalse\s*\(/ },
  { id: "ignore", regex: /@Ignore\b/ },
  { id: "skip_allowed", regex: /skipAllowed|skipPolicy|may skip/i },
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const entries = [];
  for (const root of TARGET_ROOTS) {
    const dir = path.join(repoRoot, root);
    if (!fs.existsSync(dir)) {
      continue;
    }
    for (const file of walk(dir)) {
      if (!/\.(kt|java)$/.test(file)) {
        continue;
      }
      scanFile(repoRoot, file, entries);
    }
  }
  entries.sort((left, right) => left.path.localeCompare(right.path) || left.line - right.line);
  const totals = {};
  for (const entry of entries) {
    totals[entry.category] = (totals[entry.category] || 0) + 1;
  }
  const report = {
    schema: "autojs6-node-phase10-skip-audit-v1",
    generatedAt: new Date().toISOString(),
    task: "S10-05 Unresolved Skip Audit",
    gate: "generateNodePhase10SkipAudit",
    scannedRoots: TARGET_ROOTS,
    totalEntries: entries.length,
    totals,
    entries,
    policyViolations: [],
    decision: {
      status: "recorded",
      skipRemovalWithoutCoverageAllowed: false,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "phase10-skip-audit", report, renderMarkdown);
  console.log(`Phase 10 skip audit entries: ${entries.length}`);
}

function scanFile(repoRoot, file, entries) {
  const relative = path.relative(repoRoot, file).replace(/\\/g, "/");
  const lines = fs.readFileSync(file, "utf8").split(/\r?\n/);
  lines.forEach((line, index) => {
    const matches = PATTERNS.filter((pattern) => pattern.regex.test(line));
    if (matches.length === 0) {
      return;
    }
    entries.push({
      path: relative,
      line: index + 1,
      patterns: matches.map((entry) => entry.id),
      category: categorize(relative, line),
      text: line.trim().slice(0, 240),
    });
  });
}

function categorize(file, line) {
  const value = `${file}\n${line}`.toLowerCase();
  if (value.includes("packaged") || value.includes("apkbuilder")) {
    return "packaged_runtime_gate";
  }
  if (value.includes("worker")) {
    return "worker_policy";
  }
  if (value.includes("wasi") || value.includes("wasm")) {
    return "wasi_policy";
  }
  if (value.includes("inspector")) {
    return "inspector_policy";
  }
  if (value.includes("live") || value.includes("provider") || value.includes("ocr") || value.includes("sensor") || value.includes("accessibility")) {
    return "live_provider";
  }
  if (value.includes("longrunning") || value.includes("long-running") || value.includes("schedule")) {
    return "long_running";
  }
  if (value.includes("device") || value.includes("sony") || value.includes("profile")) {
    return "device_requirement";
  }
  return "general";
}

function walk(dir) {
  const files = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const child = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...walk(child));
    } else {
      files.push(child);
    }
  }
  return files;
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 10 Skip Audit",
    "",
    `- Decision: ${report.decision.status}`,
    `- Entries: ${report.totalEntries}`,
    "",
    "| Category | Count |",
    "| --- | --- |",
  ];
  for (const [category, count] of Object.entries(report.totals).sort()) {
    lines.push(`| ${category} | ${count} |`);
  }
  lines.push("", "## Entries", "");
  for (const entry of report.entries.slice(0, 120)) {
    lines.push(`- ${entry.path}:${entry.line} [${entry.category}] ${entry.text}`);
  }
  if (report.entries.length > 120) {
    lines.push(`- ... ${report.entries.length - 120} more entries in JSON report`);
  }
  return lines.join("\n") + "\n";
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node generate-skip-audit.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const eq = arg.indexOf("=");
    const rawKey = eq >= 0 ? arg.slice(2, eq) : arg.slice(2);
    const key = rawKey.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
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

function writeReport(reportDir, basename, json, markdownRenderer) {
  fs.writeFileSync(path.join(reportDir, `${basename}.json`), JSON.stringify(json, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${basename}.md`), markdownRenderer(json));
}

main();
