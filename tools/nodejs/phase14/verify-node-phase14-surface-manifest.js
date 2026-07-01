#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-surface-manifest-verification";
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
const REQUIRED_TASKS = Object.freeze(Array.from({ length: 40 }, (_, index) => `P14-${String(index).padStart(2, "0")}`));
const REQUIRED_SURFACES = Object.freeze([
  "phase14.baseline",
  "phase14.surface_manifest",
  "phase14.promotion_contracts",
  "pro.ui_automation_media",
  "pro.tasks_device_media",
  "desktop.worker_runtime",
  "security.packaged_release_gates",
  "docs.examples.final_gate",
]);

const FILES = {
  manifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  manifestDocs: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  schema: "docs/nodejs/schemas/phase14_surface_manifest.schema.json",
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  gradle: "app/node-phase14-gate.gradle.kts",
  appBuild: "app/build.gradle.kts",
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
    schema: "autojs6-node-phase14-surface-manifest-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase14SurfaceManifest",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    statusCounts,
    profileCounts,
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      manifestComplete: checkById(checks, "manifest-covers-required-surfaces"),
      taskCoverage: checkById(checks, "manifest-covers-phase14-tasks"),
      rollbackCoverage: checkById(checks, "risk-surfaces-have-rollback"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 14 surface manifest verification: " + report.decision.status);
  console.log("Phase 14 surface manifest policy violations: " + policyViolations.length);
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
      manifest.schema === "autojs6-node-phase14-surface-manifest-v1" &&
        manifest.phase === "Phase 14: Node v1.2 Provider Promotion",
      "Manifest must use the Phase 14 surface schema and phase label.",
    ),
    check(
      "schema-doc-current",
      schema.properties &&
        schema.properties.schema &&
        schema.properties.schema.const === "autojs6-node-phase14-surface-manifest-v1" &&
        files.schema.text.includes("pro_compat_opt_in") &&
        files.schema.text.includes("desktop_compat_opt_in") &&
        files.schema.text.includes("^P14-\\\\d{2}$"),
      "JSON schema must describe Phase 14 profile, status, and task vocabularies.",
    ),
    check(
      "valid-statuses",
      surfaces.length > 0 && surfaces.every((surface) => VALID_STATUSES.includes(surface.status)),
      "Every surface must use a valid Phase 14 status.",
    ),
    check(
      "valid-profiles",
      surfaces.every((surface) =>
        Array.isArray(surface.profiles) &&
        surface.profiles.length > 0 &&
        surface.profiles.every((profile) => VALID_PROFILES.includes(profile))),
      "Every surface must declare at least one valid Phase 14 profile.",
    ),
    check(
      "manifest-covers-required-surfaces",
      REQUIRED_SURFACES.every((id) => surfaceById.has(id)) && surfaces.length === REQUIRED_SURFACES.length,
      "Manifest must cover all required Phase 14 surface groups exactly once.",
    ),
    check(
      "manifest-covers-phase14-tasks",
      REQUIRED_TASKS.every((task) => taskCoverage.has(task)) && duplicateTasks.length === 0,
      "Manifest must map every P14 task exactly once.",
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
      "Critical-risk surfaces must not be default_promoted in the Phase 14 initial manifest.",
    ),
    check(
      "surface-docs",
      includesAll(files.manifestDocs.text, [
        "# Phase 14 Surface Manifest",
        "`safe_default`",
        "`pro_compat_opt_in`",
        "`desktop_compat_opt_in`",
        "`debug_unsafe_lab`",
        "`opt_in_promoted`",
        "`default_promoted`",
        "`phase14.promotion_contracts`",
      ]),
      "Manifest documentation must define Phase 14 profile/status vocabulary and current surfaces.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodePhase14SurfaceManifest") &&
        files.gradle.text.includes("verify-node-phase14-surface-manifest.js") &&
        files.appBuild.text.includes("node-phase14-gate.gradle.kts"),
      "Gradle must register and apply the Phase 14 surface manifest verifier.",
    ),
    check(
      "plan-and-readme-wiring",
      files.phase14Tasks.text.includes("P14-01: Create Phase 14 surface manifest") &&
        files.phase14Tasks.text.includes(":app:verifyNodePhase14SurfaceManifest") &&
        files.phase14Readme.text.includes("PHASE14_NODE_V1_2_PROMOTION_TASKS.md"),
      "Phase 14 checklist and README must reference the surface manifest gate.",
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
    "# Phase 14 Surface Manifest Verification",
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
      console.log("Usage: node verify-node-phase14-surface-manifest.js [--repo-root dir] [--report-dir dir]");
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
