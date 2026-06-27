#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-surface-manifest-verification";
const VALID_PROFILES = Object.freeze([
  "safe_default",
  "pro_compat_opt_in",
  "desktop_compat_opt_in",
  "debug_unsafe_lab",
  "permanently_unsupported",
]);
const VALID_STATUSES = Object.freeze([
  "planned",
  "design_locked",
  "debug_poc",
  "provider_poc",
  "opt_in_promoted",
  "default_promoted",
  "rejected",
]);
const EVIDENCE_REQUIRED_STATUSES = Object.freeze(["debug_poc", "provider_poc", "opt_in_promoted", "default_promoted", "rejected"]);
const REQUIRED_TASKS = Object.freeze(Array.from({ length: 58 }, (_, index) => `P13-${String(index).padStart(2, "0")}`));
const REQUIRED_SURFACES = Object.freeze([
  "phase13.baseline",
  "phase13.surface_manifest",
  "profile.promotion_model",
  "runtime.graduation_blockers",
  "pro.entry_java_rhino",
  "pro.ui_automation_media",
  "pro.tasks_app_shell_media_utils",
  "desktop.network_fs",
  "desktop.execution_runtime",
  "desktop.modules_npm_process",
  "packaging.plugins_distribution",
  "security.rollback_privacy",
  "docs.examples.final_gate",
]);

const FILES = {
  manifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  manifestDocs: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  schema: "docs/nodejs/schemas/phase13_surface_manifest.schema.json",
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  gradle: "app/node-phase13-gate.gradle.kts",
  integrationPlan: "docs/nodejs/INTEGRATION_PLAN.md",
  rubric: "docs/nodejs/security/CAPABILITY_RELAXATION_RUBRIC.md",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const manifest = JSON.parse(files.manifest.text);
  const statusCounts = countBy(manifest.surfaces || [], (surface) => surface.status);
  const profileCounts = countProfiles(manifest.surfaces || []);
  const report = {
    schema: "autojs6-node-phase13-surface-manifest-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13SurfaceManifest",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    statusCounts,
    profileCounts,
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      manifestComplete: checkById(checks, "manifest-covers-required-surfaces"),
      taskCoverage: checkById(checks, "manifest-covers-phase13-tasks"),
      rollbackCoverage: checkById(checks, "risk-surfaces-have-rollback"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 13 surface manifest verification: " + report.decision.status);
  console.log("Phase 13 surface manifest policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const file = path.join(repoRoot, relative);
      return [key, { relative, text: read(file) }];
    }),
  );
}

function buildChecks(files) {
  const manifest = JSON.parse(files.manifest.text);
  const schema = JSON.parse(files.schema.text);
  const surfaces = manifest.surfaces || [];
  const surfaceById = new Map(surfaces.map((surface) => [surface.id, surface]));
  const taskCoverage = new Map();
  surfaces.forEach((surface) => {
    (surface.tasks || []).forEach((task) => {
      const existing = taskCoverage.get(task);
      taskCoverage.set(task, existing ? `${existing},${surface.id}` : surface.id);
    });
  });
  const duplicateTasks = Array.from(taskCoverage.entries()).filter(([, owner]) => owner.includes(",")).map(([task]) => task);
  const riskySurfaces = surfaces.filter((surface) => ["high", "critical"].includes(surface.risk));
  const evidenceRequiredSurfaces = surfaces.filter((surface) => EVIDENCE_REQUIRED_STATUSES.includes(surface.status));
  return [
    check(
      "schema",
      manifest.schema === "autojs6-node-phase13-surface-manifest-v1" &&
        manifest.phase === "Phase 13: Auto.js Pro And Desktop Node Parity Expansion",
      "Manifest must use the Phase 13 surface schema and phase label.",
    ),
    check(
      "schema-doc-current",
      schema.properties &&
        schema.properties.schema &&
        schema.properties.schema.const === "autojs6-node-phase13-surface-manifest-v1" &&
        files.schema.text.includes("pro_compat_opt_in") &&
        files.schema.text.includes("desktop_compat_opt_in"),
      "JSON schema must describe Phase 13 profile and status vocabularies.",
    ),
    check(
      "valid-statuses",
      surfaces.length > 0 && surfaces.every((surface) => VALID_STATUSES.includes(surface.status)),
      "Every surface must use a valid Phase 13 status.",
    ),
    check(
      "valid-profiles",
      surfaces.every((surface) =>
        Array.isArray(surface.profiles) &&
        surface.profiles.length > 0 &&
        surface.profiles.every((profile) => VALID_PROFILES.includes(profile))),
      "Every surface must declare at least one valid Phase 13 profile.",
    ),
    check(
      "manifest-covers-required-surfaces",
      REQUIRED_SURFACES.every((id) => surfaceById.has(id)) && surfaces.length === REQUIRED_SURFACES.length,
      "Manifest must cover all required Phase 13 surface groups exactly once.",
    ),
    check(
      "manifest-covers-phase13-tasks",
      REQUIRED_TASKS.every((task) => taskCoverage.has(task)) && duplicateTasks.length === 0,
      "Manifest must map every P13 task exactly once.",
    ),
    check(
      "evidence-required-statuses",
      evidenceRequiredSurfaces.every((surface) => Array.isArray(surface.evidence) && surface.evidence.length > 0),
      "Promoted, POC, and rejected surfaces must include evidence paths or gates.",
    ),
    check(
      "risk-surfaces-have-rollback",
      riskySurfaces.every((surface) => typeof surface.rollback === "string" && surface.rollback.length >= 16),
      "High and critical risk surfaces must include rollback policy text.",
    ),
    check(
      "critical-not-default-promoted",
      surfaces.every((surface) => surface.risk !== "critical" || surface.status !== "default_promoted"),
      "Critical-risk surfaces must not be default_promoted in the Phase 13 initial manifest.",
    ),
    check(
      "surface-docs",
      includesAll(files.manifestDocs.text, [
        "# Phase 13 Surface Manifest",
        "`safe_default`",
        "`pro_compat_opt_in`",
        "`desktop_compat_opt_in`",
        "`debug_unsafe_lab`",
        "`opt_in_promoted`",
        "`default_promoted`",
      ]),
      "Manifest documentation must define Phase 13 profile and status vocabulary.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodePhase13SurfaceManifest") &&
        files.gradle.text.includes("verify-node-phase13-surface-manifest.js"),
      "Gradle must register the Phase 13 surface manifest verifier.",
    ),
    check(
      "integration-plan-and-rubric",
      files.integrationPlan.text.includes("PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md") &&
        includesAll(files.rubric.text, [
          "# Capability Relaxation Rubric",
          "safe_default",
          "pro_compat_opt_in",
          "desktop_compat_opt_in",
          "debug_unsafe_lab",
        ]),
      "Integration plan and capability relaxation rubric must describe the Phase 13 profile model.",
    ),
  ];
}

function countBy(values, selector) {
  const output = {};
  values.forEach((value) => {
    const key = selector(value);
    output[key] = (output[key] || 0) + 1;
  });
  return output;
}

function countProfiles(surfaces) {
  const output = {};
  surfaces.forEach((surface) => {
    (surface.profiles || []).forEach((profile) => {
      output[profile] = (output[profile] || 0) + 1;
    });
  });
  return output;
}

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((checkItem) => checkItem.id === id);
  return item ? item.passed : false;
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Surface Manifest Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Status Counts",
    "",
  ];
  Object.keys(report.statusCounts).sort().forEach((status) => {
    lines.push(`- \`${status}\`: ${report.statusCounts[status]}`);
  });
  lines.push("", "## Profile Counts", "");
  Object.keys(report.profileCounts).sort().forEach((profile) => {
    lines.push(`- \`${profile}\`: ${report.profileCounts[profile]}`);
  });
  lines.push("", "## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
  report.checks.forEach((checkItem) => {
    lines.push(`| \`${checkItem.id}\` | ${checkItem.passed ? "passed" : "failed"} | ${escapeTable(checkItem.message)} |`);
  });
  return lines.join("\n") + "\n";
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

function read(file) {
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-node-phase13-surface-manifest.js [--repo-root dir] [--report-dir dir]");
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

main();
