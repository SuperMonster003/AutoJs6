#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase16-surface-manifest-verification";
const EXPECTED_BRANCH = "feature/nodejs-runtime-v1.4";
const REQUIRED_P16_TASKS = Object.freeze(
  Array.from({ length: 28 }, (_, index) => `P16-${String(index).padStart(2, "0")}`),
);
const REQUIRED_BACKLOG_TASKS = Object.freeze([
  "P15-04",
  "P15-05",
  "P15-06",
  "P15-07",
  "P15-08",
  "P15-09",
  "P15-10",
  "P15-12",
  "P15-13",
  "P15-14",
  "P15-15",
  "P15-16",
  "P15-17",
  "P15-18",
  "P15-19",
  "P15-20",
  "P15-21",
]);
const REQUIRED_OWNER_SURFACES = Object.freeze([
  "phase16.baseline",
  "phase16.surface_manifest",
  "phase16.promotion_contracts",
  "phase16.disclosure_rollback_audit",
  "pro.ui_overlay_automation_media",
  "pro.tasks_device_media",
  "security.types.packaged_migration",
  "phase16.summary_handoff",
]);
const VALID_PROFILES = Object.freeze([
  "safe_default",
  "pro_compat_opt_in",
  "desktop_compat_opt_in",
  "debug_unsafe_lab",
  "permanently_unsupported",
]);
const VALID_RISKS = Object.freeze(["low", "medium", "high", "critical"]);

const FILES = {
  baselineReport: "build/reports/nodejs/phase16-baseline-verification.json",
  manifest: "docs/nodejs/phase16/phase16-surface-manifest.json",
  manifestDocs: "docs/nodejs/phase16/PHASE16_SURFACE_MANIFEST.md",
  schema: "docs/nodejs/schemas/phase16_surface_manifest.schema.json",
  phase16Tasks: "docs/nodejs/reports/phase16/PHASE16_NODE_V1_4_PROVIDER_PROMOTION_TASKS.md",
  phase16Readme: "docs/nodejs/reports/phase16/README.md",
  phase15Backlog: "docs/nodejs/reports/phase15/PHASE15_FUTURE_BACKLOG.json",
  phase16Gradle: "app/node-phase16-gate.gradle.kts",
  appGradle: "app/build.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files);
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const manifest = parseJson(files.manifest.text);
  const report = {
    schema: "autojs6-node-phase16-surface-manifest-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase16SurfaceManifest",
    task: "P16-01",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    ownerSurfaceCount: Array.isArray(manifest.ownerSurfaces) ? manifest.ownerSurfaces.length : 0,
    taskMappingCount: Array.isArray(manifest.taskMappings) ? manifest.taskMappings.length : 0,
    importedBacklog: manifest.importedBacklog || {},
    statusCounts: countBy(manifest.taskMappings || [], (entry) => entry.status),
    riskCounts: countBy(manifest.taskMappings || [], (entry) => entry.risk),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      manifestComplete: checkById(checks, "manifest-covers-phase16-tasks"),
      backlogOwned: checkById(checks, "imported-backlog-owned"),
      gradleWired: checkById(checks, "gradle-gate"),
      runtimeAuthorityChanged: false,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 16 surface manifest verification: " + report.decision.status);
  console.log("Phase 16 surface manifest policy violations: " + policyViolations.length);
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
  const baselineReport = parseJson(files.baselineReport.text);
  const manifest = parseJson(files.manifest.text);
  const schema = parseJson(files.schema.text);
  const phase15Backlog = parseJson(files.phase15Backlog.text);
  const ownerSurfaces = manifest.ownerSurfaces || [];
  const taskMappings = manifest.taskMappings || [];
  const ownerIds = ownerSurfaces.map((surface) => surface.id);
  const taskIds = taskMappings.map((entry) => entry.task);
  const backlogFromManifest = manifest.importedBacklog && Array.isArray(manifest.importedBacklog.tasks)
    ? manifest.importedBacklog.tasks
    : [];
  const backlogFromTasks = taskMappings.flatMap((entry) => entry.sourceBacklogTasks || []);
  const declaredEvidenceClasses = manifest.evidenceClassVocabulary || [];
  const taskChecklistIds = parseTaskIds(files.phase16Tasks.text, "P16");
  const highRiskTasks = taskMappings.filter((entry) => ["high", "critical"].includes(entry.risk));
  return [
    check(
      "baseline-passed",
      statusOf(baselineReport) === "passed" &&
        baselineReport.decision &&
        baselineReport.decision.branchReady === true &&
        baselineReport.branch === EXPECTED_BRANCH,
      "Phase 16 surface manifest requires the P16-00 baseline gate to pass on the v1.4 branch.",
    ),
    check(
      "schema-current",
      manifest.schema === "autojs6-node-phase16-surface-manifest-v1" &&
        manifest.phase === "Phase 16: Node v1.4 Provider Promotion" &&
        manifest.branch === EXPECTED_BRANCH,
      "Manifest must use the Phase 16 schema, phase label, and v1.4 branch.",
    ),
    check(
      "json-schema-current",
      schema.properties &&
        schema.properties.schema &&
        schema.properties.schema.const === "autojs6-node-phase16-surface-manifest-v1" &&
        files.schema.text.includes("^P16-\\\\d{2}$") &&
        files.schema.text.includes("^P15-\\\\d{2}$"),
      "JSON schema must describe Phase 16 task mappings and imported Phase 15 backlog references.",
    ),
    check(
      "valid-vocabularies",
      sameSet(manifest.profileVocabulary || [], VALID_PROFILES) &&
        sameSet(manifest.riskVocabulary || [], VALID_RISKS) &&
        declaredEvidenceClasses.length >= 12,
      "Manifest must declare valid profile, risk, and evidence-class vocabularies.",
    ),
    check(
      "owner-surfaces",
      sameSet(ownerIds, REQUIRED_OWNER_SURFACES) &&
        ownerSurfaces.every((surface) =>
          Array.isArray(surface.profiles) &&
          surface.profiles.length > 0 &&
          surface.profiles.every((profile) => VALID_PROFILES.includes(profile)) &&
          VALID_RISKS.includes(surface.risk) &&
          typeof surface.rollbackHook === "string" &&
          surface.rollbackHook.length >= 8),
      "Manifest must declare every required owner surface with profile, risk, and rollback hook.",
    ),
    check(
      "manifest-covers-phase16-tasks",
      sameSet(taskIds, REQUIRED_P16_TASKS) &&
        duplicateValues(taskIds).length === 0 &&
        sameSet(taskChecklistIds, REQUIRED_P16_TASKS),
      "Manifest and checklist must map every P16-00 through P16-27 task exactly once.",
    ),
    check(
      "task-fields-complete",
      taskMappings.every((entry) =>
        REQUIRED_P16_TASKS.includes(entry.task) &&
        ownerIds.includes(entry.ownerSurface) &&
        Array.isArray(entry.profiles) &&
        entry.profiles.length > 0 &&
        entry.profiles.every((profile) => VALID_PROFILES.includes(profile)) &&
        VALID_RISKS.includes(entry.risk) &&
        declaredEvidenceClasses.includes(entry.evidenceClass) &&
        typeof entry.rollbackHook === "string" &&
        entry.rollbackHook.length >= 8 &&
        String(entry.gate || "").startsWith(":app:") &&
        ["ready", "planned"].includes(entry.status)),
      "Every P16 task mapping must declare owner surface, profile, risk, evidence class, rollback hook, gate, and status.",
    ),
    check(
      "imported-backlog-owned",
      phase15Backlog.schema === "autojs6-node-phase15-future-backlog-v1" &&
        phase15Backlog.classification === "v1.4 future promotion backlog" &&
        sameSet(backlogFromManifest, REQUIRED_BACKLOG_TASKS) &&
        sameSet(backlogFromTasks, REQUIRED_BACKLOG_TASKS),
      "Every imported Phase 15 future backlog task must be owned by a Phase 16 task mapping.",
    ),
    check(
      "risk-rollback-coverage",
      highRiskTasks.length >= 20 &&
        highRiskTasks.every((entry) => typeof entry.rollbackHook === "string" && entry.rollbackHook.length >= 8) &&
        taskMappings
          .filter((entry) => entry.risk === "critical")
          .every((entry) => entry.status === "planned" && entry.ownerSurface === "security.types.packaged_migration"),
      "High and critical risk Phase 16 tasks must keep explicit rollback hooks and critical work must remain planned.",
    ),
    check(
      "docs-current",
      includesAll(files.manifestDocs.text, [
        "# Phase 16 Surface Manifest",
        "`P16-00` through `P16-27`",
        "`P15-04`",
        "`P15-21`",
        "`phase16.promotion_contracts`",
        "`security.types.packaged_migration`",
        ":app:verifyNodePhase16SurfaceManifest",
      ]),
      "Manifest docs must describe owner surfaces, imported backlog ownership, rules, and verifier.",
    ),
    check(
      "task-plan-and-readme-wiring",
      includesAll(files.phase16Tasks.text, [
        "- [x] P16-01: Create Phase 16 surface manifest.",
        ":app:verifyNodePhase16SurfaceManifest",
        "phase16-surface-manifest.json",
        "phase16-surface-manifest-verification.json",
      ]) &&
        files.phase16Readme.text.includes(":app:verifyNodePhase16SurfaceManifest"),
      "Phase 16 task checklist and README must reference the surface manifest gate and artifacts.",
    ),
    check(
      "gradle-gate",
      files.phase16Gradle.text.includes("verifyNodePhase16SurfaceManifest") &&
        files.phase16Gradle.text.includes("verify-node-phase16-surface-manifest.js") &&
        files.phase16Gradle.text.includes("verifyNodePhase16Baseline") &&
        files.appGradle.text.includes("node-phase16-gate.gradle.kts"),
      "Gradle must register the Phase 16 surface manifest verifier after the baseline gate.",
    ),
  ];
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 16 Surface Manifest Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    `- Owner surfaces: ${report.ownerSurfaceCount}`,
    `- Task mappings: ${report.taskMappingCount}`,
    "",
    "## Status Counts",
    "",
  ];
  Object.keys(report.statusCounts).sort().forEach((status) => {
    lines.push(`- \`${status}\`: ${report.statusCounts[status]}`);
  });
  lines.push("", "## Checks", "", "| Check | Passed | Message |", "| --- | --- | --- |");
  report.checks.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | \`${entry.passed}\` | ${escapeTable(entry.message)} |`);
  });
  if (report.policyViolations.length > 0) {
    lines.push("", "## Policy Violations", "");
    report.policyViolations.forEach((entry) => {
      lines.push(`- ${entry}`);
    });
  }
  lines.push("");
  return lines.join("\n");
}

function parseTaskIds(text, prefix) {
  const pattern = new RegExp(`- \\[[ x]\\]\\s+(${prefix}-\\d{2})`, "g");
  return Array.from(text.matchAll(pattern)).map((match) => match[1]);
}

function duplicateValues(values) {
  const counts = countBy(values, (value) => value);
  return Object.keys(counts).filter((value) => counts[value] > 1);
}

function countBy(values, selector) {
  const output = {};
  values.forEach((value) => {
    const key = selector(value);
    output[key] = (output[key] || 0) + 1;
  });
  return output;
}

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function sameSet(actual, expected) {
  if (!Array.isArray(actual) || actual.length !== expected.length) {
    return false;
  }
  const actualSorted = actual.slice().sort();
  const expectedSorted = expected.slice().sort();
  return expectedSorted.every((value, index) => actualSorted[index] === value);
}

function statusOf(report) {
  return report.status || (report.decision && report.decision.status) || "missing";
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const entry = checks.find((checkItem) => checkItem.id === id);
  return entry ? entry.passed : false;
}

function parseJson(text) {
  if (!text || !text.trim()) {
    return {};
  }
  try {
    return JSON.parse(text);
  } catch {
    return {};
  }
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
  } catch {
    return "";
  }
}

function escapeTable(value) {
  return String(value || "").replace(/\|/g, "\\|").replace(/\r?\n/g, "<br>");
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    } else if (arg === "-h" || arg === "--help") {
      console.log("Usage: node verify-node-phase16-surface-manifest.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
