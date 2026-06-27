#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REQUIRED_REPORTS = [
  { id: "adapter-export", path: "runtime-adapter-export.json" },
  { id: "build-plan", path: "runtime-build-plan.json" },
  { id: "alignment", path: "native-16kb-alignment.json" },
  { id: "artifact-manifest", path: "runtime-artifact-manifest.json" },
  { id: "artifact-integrity", path: "runtime-artifact-integrity.json" },
  { id: "distribution-policy", path: "runtime-distribution-policy.json" },
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const entries = REQUIRED_REPORTS.map((entry) => readEntry(reportDir, entry));
  const missing = entries.filter((entry) => !entry.available).map((entry) => entry.id);
  const blocked = entries.filter((entry) => entry.status === "blocked").map((entry) => entry.id);
  const bootstrap = entries.filter((entry) => entry.status === "bootstrap_only").map((entry) => entry.id);
  const decision = {
    status: missing.length > 0 || blocked.length > 0 ? "blocked" : bootstrap.length > 0 ? "bootstrap_only" : "ready",
    blockers: []
      .concat(missing.map((id) => `missing required report: ${id}`))
      .concat(blocked.map((id) => `required report is blocked: ${id}`)),
    warnings: bootstrap.map((id) => `required report is bootstrap_only: ${id}`),
  };
  const report = {
    schema: "autojs6-node-runtime-artifact-gate-v1",
    generatedAt: new Date().toISOString(),
    task: "S9-14 Runtime Artifact Gate",
    reports: entries,
    decision,
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "runtime-artifact-gate", report, renderMarkdown);
  console.log("Runtime artifact gate decision: " + decision.status);
  if (decision.status === "blocked" || (options.failOnBootstrapOnly && decision.status !== "ready")) {
    process.exitCode = 1;
  }
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-bootstrap-only"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node tools/nodejs/native/generate-runtime-artifact-gate.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const eq = arg.indexOf("=");
    const rawKey = eq >= 0 ? arg.slice(2, eq) : arg.slice(2);
    const key = rawKey.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
    if (booleanArgs.has(rawKey)) {
      options[key] = eq >= 0 ? ["1", "true", "yes", "on"].includes(arg.slice(eq + 1).toLowerCase()) : true;
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

function readEntry(reportDir, entry) {
  const file = path.join(reportDir, entry.path);
  if (!fs.existsSync(file)) {
    return {
      id: entry.id,
      path: entry.path,
      available: false,
      status: "missing",
    };
  }
  const report = JSON.parse(fs.readFileSync(file, "utf8"));
  return {
    id: entry.id,
    path: entry.path,
    available: true,
    status: report.decision && report.decision.status || "unknown",
    blockers: report.decision && report.decision.blockers || [],
    warnings: report.decision && report.decision.warnings || [],
  };
}

function writeReport(reportDir, baseName, report, renderer) {
  const jsonPath = path.join(reportDir, `${baseName}.json`);
  const markdownPath = path.join(reportDir, `${baseName}.md`);
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(markdownPath, renderer(report));
  console.log(`${baseName} JSON: ${jsonPath}`);
  console.log(`${baseName} Markdown: ${markdownPath}`);
}

function renderMarkdown(report) {
  const lines = [];
  lines.push("# AutoJs6 Node Runtime Artifact Gate");
  lines.push("");
  lines.push(`Generated: ${report.generatedAt}`);
  lines.push("");
  lines.push(`Decision: \`${report.decision.status}\``);
  lines.push("");
  lines.push("| Report | Status | Path |");
  lines.push("| --- | --- | --- |");
  for (const entry of report.reports) {
    lines.push(`| ${entry.id} | ${entry.status} | ${entry.path} |`);
  }
  if (report.decision.blockers.length > 0) {
    lines.push("");
    lines.push("## Blockers");
    lines.push("");
    report.decision.blockers.forEach((issue) => lines.push(`- ${issue}`));
  }
  if (report.decision.warnings.length > 0) {
    lines.push("");
    lines.push("## Warnings");
    lines.push("");
    report.decision.warnings.forEach((warning) => lines.push(`- ${warning}`));
  }
  lines.push("");
  return lines.join("\n");
}

main();
