#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-unsupported-utility-triage-verification";

const FILES = {
  report: "docs/nodejs/reports/phase12/RHINO_UNSUPPORTED_UTILITY_TRIAGE.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  matrixGenerator: "tools/nodejs/phase11/generate-rhino-compat-matrix.js",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  surfaceDocs: "docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  gradle: "app/node-phase12-gate.gradle.kts",
};

const CANDIDATES = Object.freeze([
  { id: "s13n", decision: "promote", phrase: "P12-31 decision: promote s13n.bytes" },
  { id: "util", decision: "defer", phrase: "P12-31 decision: defer util" },
  { id: "jsox.mathx", decision: "defer", phrase: "P12-31 decision: defer jsox.mathx" },
  { id: "zip", decision: "defer", phrase: "P12-31 decision: defer zip" },
  { id: "canvas", decision: "defer", phrase: "P12-31 decision: defer canvas" },
  { id: "recorder", decision: "defer", phrase: "P12-31 decision: defer recorder" },
  { id: "media", decision: "defer", phrase: "P12-31 decision: defer media/audio helper" },
  { id: "mediainfo", decision: "defer", phrase: "P12-31 decision: defer mediainfo" },
  { id: "opencc", decision: "defer", phrase: "P12-31 decision: defer OpenCC" },
  { id: "pinyin", decision: "defer", phrase: "P12-31 decision: defer pinyin.fromPhrase" },
]);

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files, repoRoot);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const report = {
    schema: "autojs6-node-rhino-compat-unsupported-utility-triage-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatUnsupportedUtilityTriage",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      candidateCoverage: checkById(checks, "candidate-decision-coverage"),
      p12Pilot: checkById(checks, "p12-32-pilot-selection"),
      surfaceStateCurrent: checkById(checks, "surface-manifest"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat unsupported utility triage verification: " + report.decision.status);
  console.log("Rhino compat unsupported utility triage policy violations: " + policyViolations.length);
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

function buildChecks(files, repoRoot) {
  const statusManifest = JSON.parse(files.statusManifest.text);
  const moduleById = new Map((statusManifest.modules || []).map((module) => [module.id, module]));
  const surfaceManifest = JSON.parse(files.surfaceManifest.text);
  const triageSurface = (surfaceManifest.surfaces || []).find((surface) => surface.id === "unsupported.utility_triage");
  const syntax = checkSyntax(path.join(repoRoot, "tools/nodejs/phase12/verify-rhino-compat-unsupported-utility-triage.js"));

  return [
    check(
      "report-shape",
      includesAll(files.report.text, [
        "Status: P12-31 unsupported utility-heavy triage landed",
        "P12-31 is a decision gate only",
        "| Candidate | Current manifest status | Unsupported API count | Decision | Rationale | Next gate |",
        "## P12-32 Pilot",
        "P12-32 should implement only `s13n.bytes` first",
        "## Non-Goals",
        "raw Android or Rhino objects",
        "verifyNodeRhinoCompatUnsupportedUtilityTriage",
      ]),
      "P12-31 report must describe the decision-only scope, candidate table, pilot, non-goals, and gate.",
    ),
    check(
      "candidate-decision-coverage",
      CANDIDATES.every((candidate) =>
        files.report.text.includes(`| \`${candidate.id === "pinyin" ? "pinyin.fromPhrase" : candidate.id}\``) &&
          files.report.text.includes(`| \`${candidate.decision}\` |`)),
      "Every P12-31 candidate must have an explicit promote/defer/permanent_non_goal decision in the report.",
    ),
    check(
      "p12-32-pilot-selection",
      includesAll(files.report.text, [
        "| `s13n` | `unsupported` | 6 | `promote` |",
        "runtime facade for `require(\"s13n\")`",
        "`autojs6:compat.s13n.bytes`",
        "`s13n.color`",
        "`s13n.point`",
        "`s13n.throwable`",
        "`s13n.time`",
      ]) &&
        !/P12-32 should implement only `(zip|canvas|recorder|media|opencc|pinyin)/.test(files.report.text),
      "P12-31 must select only s13n.bytes as the P12-32 pilot and keep sibling s13n helpers deferred.",
    ),
    check(
      "status-manifest-notes",
      CANDIDATES.every((candidate) => moduleHasNote(moduleById.get(candidate.id), candidate.phrase)) &&
        ["p12_31_promote_s13n_bytes_pilot", "p12_32_s13n_bytes_pure_utility_pilot"].includes(moduleById.get("s13n").decision) &&
        moduleById.get("zip").decision === "p12_31_defer_zip_archive_provider" &&
        moduleById.get("canvas").decision === "p12_31_defer_canvas_provider_design" &&
        moduleById.get("recorder").decision === "p12_31_defer_recorder_provider_design" &&
        moduleById.get("mediainfo").decision === "p12_31_defer_mediainfo_provider_design",
      "Status manifest must carry P12-31 notes and updated decisions for the triaged utility candidates.",
    ),
    check(
      "status-manifest-no-premature-promotion",
      ["unsupported", "partial"].includes(moduleById.get("s13n").status) &&
        ["unsupported", "supported"].includes(entryStatus(moduleById.get("s13n"), "s13n.bytes")) &&
        ["s13n.color", "s13n.point", "s13n.throwable", "s13n.time"].every((id) => entryStatus(moduleById.get("s13n"), id) === "unsupported") &&
        moduleById.get("util").status === "unsupported" &&
        moduleById.get("jsox.mathx").status === "unsupported" &&
        moduleById.get("zip").status === "unsupported" &&
        moduleById.get("canvas").status === "unsupported" &&
        moduleById.get("recorder").status === "unsupported" &&
        moduleById.get("mediainfo").status === "unsupported" &&
        moduleById.get("opencc").status === "unsupported" &&
        moduleById.get("media").status === "partial" &&
        moduleById.get("pinyin").status === "partial" &&
        entryStatus(moduleById.get("pinyin"), "pinyin.fromPhrase") === "unsupported",
      "P12-31 must not promote triaged modules except the later P12-32 s13n.bytes pilot.",
    ),
    check(
      "status-manifest-no-extra-p12-31-notes",
      (statusManifest.modules || [])
        .filter((module) => (module.notes || []).some((note) => note.includes("P12-31 decision:")))
        .every((module) => CANDIDATES.some((candidate) => candidate.id === module.id)),
      "P12-31 decision notes must not appear on non-candidate modules.",
    ),
    check(
      "matrix-generator-next-steps",
      includesAll(files.matrixGenerator.text, [
        "P12-31 decision: promote s13n.bytes",
        "Implement P12-32 s13n.bytes pure utility pilot",
        "P12-32 promotes s13n.bytes",
        "Proceed to P12-33 dual-engine device evidence",
        "P12-31 decision: defer zip",
        "P12-31 decision: defer canvas",
        "P12-31 decision: defer recorder",
        "P12-31 decision: defer media/audio helper",
        "P12-31 decision: defer OpenCC",
        "P12-31 decision: defer pinyin.fromPhrase",
        "P12-31 decision: defer mediainfo",
      ]),
      "Matrix generator must map P12-31 notes to concrete next steps.",
    ),
    check(
      "matrix-next-steps",
      includesAll(findMatrixRow(files.matrix.text, "s13n"), [
        "| 38 | s13n | s13n | partial |",
        "partial:1, supported:1, unsupported:4",
        "Proceed to P12-33 dual-engine device evidence",
      ]) &&
        includesAll(findMatrixRow(files.matrix.text, "zip"), [
          "| 68 | zip | zip | unsupported |",
          "Defer zip until a scoped archive provider",
        ]) &&
        includesAll(findMatrixRow(files.matrix.text, "canvas"), [
          "| 20 | canvas | Canvas | unsupported |",
          "raw Canvas/Paint/View parity",
        ]) &&
        includesAll(findMatrixRow(files.matrix.text, "recorder"), [
          "| 56 | recorder | recorder | unsupported |",
          "privacy, permission, foreground disclosure",
        ]) &&
        includesAll(findMatrixRow(files.matrix.text, "media"), [
          "| 58 | media | media | partial |",
          "Defer media/audio helper parity beyond screenshot wrappers",
        ]) &&
        includesAll(findMatrixRow(files.matrix.text, "opencc"), [
          "| 64 | opencc | opencc | unsupported |",
          "Defer OpenCC promotion",
        ]) &&
        includesAll(findMatrixRow(files.matrix.text, "pinyin"), [
          "| 70 | pinyin | pinyin | partial |",
          "Keep the fixture-backed pinyin subset",
        ]) &&
        includesAll(findMatrixRow(files.matrix.text, "mediainfo"), [
          "| 72 | mediainfo | mediainfo | unsupported |",
          "Defer mediainfo",
        ]),
      "Generated matrix must expose the P12-31 next steps for the triaged candidates.",
    ),
    check(
      "migration-docs",
      includesAll(files.migration.text, [
        "## Unsupported Utility Modules",
        "RHINO_UNSUPPORTED_UTILITY_TRIAGE.md",
        "P12-32 promotes the first low-risk pilot, `s13n.bytes`",
        "require(\"s13n\")",
        "compat.s13n.bytes",
        "`zip`: deferred until a scoped archive provider",
        "`canvas`: deferred until a JSON-only drawing provider",
        "`recorder`, `media` audio helpers, and `mediainfo`",
        "`opencc` and `pinyin.fromPhrase`",
        "`util` and `jsox.mathx`",
      ]),
      "Migration guide must explain the P12-31 utility decisions to script authors.",
    ),
    check(
      "surface-manifest",
      triageSurface &&
        triageSurface.status === "promoted" &&
        (triageSurface.tasks || []).includes("P12-31") &&
        (triageSurface.tasks || []).includes("P12-32") &&
        includesAll(JSON.stringify(triageSurface), [
          "RHINO_UNSUPPORTED_UTILITY_TRIAGE.md",
          "RHINO_S13N_BYTES_PROMOTION.md",
          "require(\\\"s13n\\\")",
          "verify-rhino-compat-unsupported-utility-triage.js",
          "verify-rhino-compat-s13n-bytes-promotion.js",
          "verifyNodeRhinoCompatUnsupportedUtilityTriage",
          "verifyNodeRhinoCompatS13nBytesPromotion",
          "s13n.bytes",
          "zip/canvas/recorder/media/mediainfo/opencc/pinyin",
        ]) &&
        includesAll(files.surfaceDocs.text, [
          "`unsupported.utility_triage`",
          "P12-31 locks decisions",
          "P12-32 promotes only `s13n.bytes`",
          "stable unsupported",
        ]),
      "Phase 12 surface manifest must mark unsupported.utility_triage promoted once P12-32 s13n.bytes lands while preserving P12-31 evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-31/.test(files.phase12Tasks.text) &&
        includesAll(files.phase12Tasks.text, [
          "RHINO_UNSUPPORTED_UTILITY_TRIAGE.md",
          "s13n.bytes",
          "verify-rhino-compat-unsupported-utility-triage.js",
          "verifyNodeRhinoCompatUnsupportedUtilityTriage",
          "unsupported.utility_triage",
          "promoted",
        ]),
      "Phase 12 task plan must mark P12-31 complete with report, manifest, docs, and gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatUnsupportedUtilityTriage") &&
        files.gradle.text.includes("verify-rhino-compat-unsupported-utility-triage.js") &&
        files.gradle.text.includes("verifyNodeRhinoCompatBarcodeExamples") &&
        files.gradle.text.includes("RHINO_UNSUPPORTED_UTILITY_TRIAGE.md") &&
        files.gradle.text.includes("rhino-compat-unsupported-utility-triage-verification.json"),
      "Gradle must register verifyNodeRhinoCompatUnsupportedUtilityTriage after the P12-30 barcode examples gate.",
    ),
    check(
      "syntax-verifier",
      syntax.ok,
      `P12-31 verifier must pass node --check${syntax.ok ? "" : ": " + syntax.message}`,
    ),
  ];
}

function moduleHasNote(module, phrase) {
  return Boolean(module && (module.notes || []).some((note) => note.includes(phrase)));
}

function entryStatus(module, id) {
  const entry = module && (module.apiEntries || []).find((item) => item.id === id);
  return entry ? entry.status : null;
}

function checkSyntax(file) {
  if (!fs.existsSync(file)) {
    return { ok: false, message: "missing file" };
  }
  const result = childProcess.spawnSync(process.execPath, ["--check", file], {
    encoding: "utf8",
    windowsHide: true,
  });
  return {
    ok: result.status === 0,
    message: String(result.stderr || result.stdout || "").trim(),
  };
}

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function findMatrixRow(text, moduleId) {
  return text.split(/\r?\n/).find((line) => line.includes(`| ${moduleId} |`)) || "";
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((checkItem) => checkItem.id === id);
  return Boolean(item && item.passed);
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat Unsupported Utility Triage Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Checks",
    "",
    "| Check | Result | Message |",
    "| --- | --- | --- |",
  ];
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
      console.log("Usage: node verify-rhino-compat-unsupported-utility-triage.js [--repo-root dir] [--report-dir dir]");
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
