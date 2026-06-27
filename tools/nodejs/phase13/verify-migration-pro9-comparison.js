#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-migration-pro9-comparison";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_56_MIGRATION_PRO9_COMPARISON.md",
  migrationDoc: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  typesProfileCapabilities: "docs/nodejs/types/autojs6-node/profile_capabilities.d.ts",
  proParityManifest: "sample/nodejs/pro-parity-suite/examples.json",
  desktopParityManifest: "sample/nodejs/desktop-parity-suite/examples.json",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  phase13Summary: "docs/nodejs/reports/phase13/PHASE13_SUMMARY.md",
};

const REQUIRED_DOC_TOKENS = [
  "P13-56 records migration and Pro9 comparison docs as partial",
  "Pro-compatible",
  "Desktop-compatible",
  "gated/diagnostic-only",
  "debug-only/provider POC is not formal availability",
  "sample/nodejs/pro-parity-suite",
  "sample/nodejs/desktop-parity-suite",
  "profile_capabilities.d.ts",
  "raw_network",
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const report = buildReport(files);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 13 migration and Pro9 comparison docs: " + report.decision.status);
  console.log("Phase 13 migration and Pro9 comparison blockers: " + report.blockers.length);
  console.log("Phase 13 migration and Pro9 comparison policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0 || (options.failOnBlocked && !report.decision.finalBlockerClosable)) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const file = path.join(repoRoot, relative);
      return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
    }),
  );
}

function buildReport(files) {
  const surfaceEntry = surface(files.surfaceManifest.text, "docs.examples.final_gate");
  const scenarios = [
    scenario("migration_doc_taxonomy", "Rhino migration guide records Pro/Desktop/gated labels", [
      ...REQUIRED_DOC_TOKENS.map((value) => token(files.migrationDoc, value)),
      token(files.migrationDoc, "covered `autojs6:compat`"),
      token(files.migrationDoc, "$autojs.java"),
      token(files.migrationDoc, "raw network sockets"),
      token(files.migrationDoc, "raw network modules opt-in"),
      token(files.migrationDoc, "Android npm CLI"),
    ]),
    scenario("pro9_comparison_taxonomy", "Auto.js Pro 9 comparison records the same partial taxonomy", [
      ...REQUIRED_DOC_TOKENS.map((value) => token(files.proComparison, value)),
      token(files.proComparison, "Pro/Desktop authority"),
      token(files.proComparison, "runtime promotion gate"),
      token(files.proComparison, "lifecycle scripts"),
    ]),
    scenario("pro_autox_notes_taxonomy", "Pro/AutoX compatibility notes record the same partial taxonomy", [
      ...REQUIRED_DOC_TOKENS.map((value) => token(files.proAutoxNotes, value)),
      token(files.proAutoxNotes, "AutoJs/Rhino bridge modules"),
      token(files.proAutoxNotes, "runtime promotion gate"),
      token(files.proAutoxNotes, "native payloads"),
    ]),
    scenario("evidence_chain", "P13-56 is wired into docs, gates, examples, types, surface manifest, and summary state", [
      token(files.phase13Tasks, "P13-56 partial landing"),
      token(files.p13Report, "P13-56 records migration and Pro9 comparison docs as partial"),
      token(files.phase13GradleGate, "verifyNodePhase13MigrationPro9Comparison"),
      token(files.phase13GradleGate, "verify-migration-pro9-comparison.js"),
      token(files.testingDoc, "verifyNodePhase13MigrationPro9Comparison"),
      token(files.ciGatesDoc, "verifyNodePhase13MigrationPro9Comparison"),
      token(files.capabilityMatrix, "P13-56 migration and Pro9 comparison docs"),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.typesProfileCapabilities, "ProfileDeclarationMatrix"),
      token(files.typesProfileCapabilities, "raw_network"),
      token(files.proParityManifest, "java-interop"),
      token(files.desktopParityManifest, "worker-threads"),
      token(files.desktopParityManifest, "raw-network"),
      token(files.phase13Summary, "Status: P13-57 summary generator published as a partial final gate."),
      token(files.phase13Summary, "- Completed blockers:"),
      token(files.phase13Summary, "- Open blockers:"),
      token(files.phase13Summary, "- Deferred blockers:"),
      token(files.phase13Summary, "- Rejected blockers:"),
      token(files.phase13Summary, "| `P13-56` | `open` | `docs.examples.final_gate`"),
      token(files.phase13Summary, "| `phase13-migration-pro9-comparison.json` | `verifyNodePhase13MigrationPro9Comparison` | `P13-56`"),
    ]),
  ];

  const checks = [
    check("required-files", Object.values(files).every((file) => file.exists), "All P13-56 docs, manifests, type, example, and gate files must exist."),
    check(
      "docs-say-provider-poc-is-not-availability",
      [files.migrationDoc, files.proComparison, files.proAutoxNotes].every((file) =>
        file.text.includes("debug-only/provider POC is not formal availability"),
      ),
      "Each P13-56 target doc must state that debug-only/provider POC is not formal availability.",
    ),
    check(
      "no-provider-poc-overclaim",
      ![files.migrationDoc, files.proComparison, files.proAutoxNotes].some((file) =>
        file.text.includes("debug-only/provider POC is formal availability"),
      ),
      "P13-56 docs must not overclaim debug-only/provider POC evidence as formal availability.",
    ),
    check(
      "examples-and-types-are-evidence-only",
      [files.migrationDoc, files.proComparison, files.proAutoxNotes].every((file) =>
        file.text.includes("sample/nodejs/pro-parity-suite") &&
          file.text.includes("sample/nodejs/desktop-parity-suite") &&
          file.text.includes("profile_capabilities.d.ts") &&
          file.text.includes("raw_network"),
      ),
      "P13-56 docs must link the Pro/Desktop examples, raw_network marker, and profile declarations as evidence, not authority grants.",
    ),
    check(
      "runtime-parity-claims-stay-partial",
      files.p13Report.text.includes("Runtime parity claims remain partial until promoted paths are executable") &&
        [files.migrationDoc, files.proComparison, files.proAutoxNotes].every((file) =>
          file.text.includes("debug-only/provider POC is not formal availability") &&
            file.text.includes("runtime promotion gate")) &&
        files.phase13Summary.text.includes("| `P13-56` | `open` | `docs.examples.final_gate`"),
      "P13-56 runtime parity claims must remain partial until promoted paths are executable and summary state stays open.",
    ),
    check(
      "surface-entry",
      Boolean(surfaceEntry) &&
        Array.isArray(surfaceEntry.tasks) &&
        surfaceEntry.tasks.includes("P13-56") &&
        Array.isArray(surfaceEntry.evidence) &&
        surfaceEntry.evidence.some((entry) => entry.includes(REPORT_BASENAME)) &&
        surfaceEntry.evidence.some((entry) => entry.includes("MIGRATION_FROM_RHINO.md")) &&
        surfaceEntry.evidence.some((entry) => entry.includes("INTEGRATION_STATUS_PRO9_COMPARISON.md")) &&
        surfaceEntry.evidence.some((entry) => entry.includes("COMPATIBILITY_NOTES_PRO_AUTOX.md")),
      "`docs.examples.final_gate` must keep P13-56 mapped and reference migration/Pro9 comparison evidence.",
    ),
  ];

  const policyViolations = [
    ...checks.filter((entry) => !entry.passed).map((entry) => entry.message),
    ...scenarios.flatMap((entry) => entry.remaining.filter((item) => item.startsWith("missing evidence"))),
  ];
  const blockers = [
    ...scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`)),
    "P13-56 is documentation classification only; runtime Pro/Desktop profile promotion remains gated",
    "packaged Pro/Desktop migration cookbook, prompt/disclosure UX, and device permission matrix remain incomplete",
  ];
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-migration-pro9-comparison-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13MigrationPro9Comparison",
    task: "P13-56",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: surfaceEntry || {
      id: "docs.examples.final_gate",
      status: "missing",
      evidence: [],
    },
    scenarios,
    checks,
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : finalBlockerClosable ? "ready" : "partial",
      phase13TaskComplete: false,
      finalBlockerClosable,
      migrationDocsClassified: scenarioById(scenarios, "migration_doc_taxonomy").status === "ready",
      pro9ComparisonClassified: scenarioById(scenarios, "pro9_comparison_taxonomy").status === "ready",
      proAutoxNotesClassified: scenarioById(scenarios, "pro_autox_notes_taxonomy").status === "ready",
      phase13SummaryStateLinked: scenarioById(scenarios, "evidence_chain").status === "ready",
      runtimeParityClaimsPartial: checkById(checks, "runtime-parity-claims-stay-partial").passed,
      grantsAuthority: false,
    },
  };
}

function scenario(id, title, checks, plannedRemaining = []) {
  const missing = checks.filter((entry) => !entry.present).map((entry) => `missing evidence token: ${entry.token}`);
  const remaining = [...missing, ...plannedRemaining];
  return {
    id,
    title,
    status: remaining.length === 0 ? "ready" : missing.length > 0 ? "missing_evidence" : "partial",
    checks,
    remaining,
  };
}

function token(file, value) {
  return {
    file: file.relative,
    token: value,
    present: file.exists && file.text.includes(value),
  };
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  return checks.find((entry) => entry.id === id) || { passed: false };
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id);
}

function surface(text, id) {
  try {
    const manifest = JSON.parse(text);
    return (manifest.surfaces || []).find((entry) => entry.id === id);
  } catch (_) {
    return null;
  }
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Migration And Pro9 Comparison Docs",
    "",
    `- Task: ${report.task}`,
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Blockers: ${report.blockers.length}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Scenarios",
    "",
  ];
  for (const scenarioItem of report.scenarios) {
    lines.push(`- \`${scenarioItem.id}\`: \`${scenarioItem.status}\` - ${scenarioItem.title}`);
    for (const remaining of scenarioItem.remaining) {
      lines.push(`  - ${remaining}`);
    }
  }
  lines.push("", "## Checks", "");
  for (const checkItem of report.checks) {
    lines.push(`- ${checkItem.passed ? "[x]" : "[ ]"} \`${checkItem.id}\`: ${checkItem.message}`);
  }
  if (report.policyViolations.length > 0) {
    lines.push("", "## Policy Violations", "");
    for (const violation of report.policyViolations) {
      lines.push(`- ${violation}`);
    }
  }
  lines.push("", "## Remaining Blockers", "");
  if (report.blockers.length === 0) {
    lines.push("None.");
  } else {
    for (const blocker of report.blockers) {
      lines.push(`- ${blocker}`);
    }
  }
  return lines.join("\n") + "\n";
}

function read(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "") : "";
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-blocked"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-migration-pro9-comparison.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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

main();
