#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const {
  REQUIRED_SOAK_REPORTS,
  evaluateSoakReportJson,
  readJsonIfExists,
  round,
} = require("./verify-full-heavy-resource-stress.js");

const REPORT_BASENAME = "phase13-full-heavy-resource-stress-archive";

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const source = options.source ? path.resolve(options.source) : "";
  const requirement = requirementForKind(options.kind);
  const target = path.join(repoRoot, requirement.relative);
  const json = source ? readJsonIfExists(source) : null;
  const evaluation = evaluateSoakReportJson(json, {
    ...requirement,
    relative: source ? relativePath(repoRoot, source) : "",
  });
  const blockers = [];
  if (!source) {
    blockers.push("missing --source report path");
  } else if (!fs.existsSync(source)) {
    blockers.push(`source report does not exist: ${source}`);
  }
  if (!evaluation.ready) {
    blockers.push(...evaluation.blockers);
  }
  const raw = source && fs.existsSync(source) ? fs.readFileSync(source, "utf8") : "";
  if (blockers.length === 0 && fs.existsSync(target) && fs.readFileSync(target, "utf8") !== raw && !options.force) {
    blockers.push(`target already exists; pass --force to replace ${requirement.relative}`);
  }
  let copied = false;
  if (blockers.length === 0 && !options.dryRun) {
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.copyFileSync(source, target);
    copied = true;
  }
  const report = {
    schema: "autojs6-node-phase13-full-heavy-resource-stress-archive-v1",
    generatedAt: new Date().toISOString(),
    kind: requirement.id,
    source: source ? relativePath(repoRoot, source) : "",
    target: requirement.relative,
    dryRun: Boolean(options.dryRun),
    force: Boolean(options.force),
    copied,
    evaluation,
    blockers,
    decision: {
      status: blockers.length > 0 ? "blocked" : options.dryRun ? "validated" : "archived",
      readyForFinalSoakPath: blockers.length === 0,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 13 full heavy resource stress archive: " + report.decision.status);
  console.log("Phase 13 full heavy resource stress archive blockers: " + blockers.length);
  if (blockers.length > 0) {
    process.exitCode = 1;
  }
}

function requirementForKind(kindValue) {
  const normalized = String(kindValue || "").toLowerCase().replace(/_/g, "-");
  if (["quick", "quick-soak", "quick-soak-8h", "quick-soak-8"].includes(normalized)) {
    return REQUIRED_SOAK_REPORTS.find((entry) => entry.id === "quick_soak_8h");
  }
  if (["final", "final-soak", "final-soak-24h", "final-soak-24"].includes(normalized)) {
    return REQUIRED_SOAK_REPORTS.find((entry) => entry.id === "final_soak_24h");
  }
  throw new Error("Missing or unsupported --kind. Use quick or final.");
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Full Heavy Resource Stress Archive",
    "",
    `- Kind: \`${report.kind}\``,
    `- Status: \`${report.decision.status}\``,
    `- Source: \`${report.source || "missing"}\``,
    `- Target: \`${report.target}\``,
    `- Dry run: \`${report.dryRun}\``,
    `- Copied: \`${report.copied}\``,
    `- Duration hours: \`${round(report.evaluation.durationHours)}\``,
    `- Metrics ready: \`${report.evaluation.requiredMetricsReady}\``,
    `- Metrics clean: \`${report.evaluation.metricsClean}\``,
    "",
    "## Blockers",
    "",
  ];
  if (report.blockers.length === 0) {
    lines.push("None.");
  } else {
    report.blockers.forEach((entry) => lines.push(`- ${entry}`));
  }
  return lines.join("\n") + "\n";
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["dry-run", "force"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node archive-full-heavy-resource-soak.js --kind quick|final --source report.json [--repo-root dir] [--report-dir dir] [--dry-run] [--force]");
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

function relativePath(root, file) {
  return path.relative(root, file).replace(/\\/g, "/");
}

main();
