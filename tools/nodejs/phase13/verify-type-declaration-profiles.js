#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-type-declaration-profiles";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_55_TYPE_DECLARATION_PROFILES.md",
  typesIndex: "docs/nodejs/types/autojs6-node/index.d.ts",
  typesProfileCapabilities: "docs/nodejs/types/autojs6-node/profile_capabilities.d.ts",
  typesAutojsGlobal: "docs/nodejs/types/autojs6-node/autojs_global.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typesPackage: "docs/nodejs/types/autojs6-node/package.json",
  typescriptSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  typescriptSmokeTsconfig: "sample/nodejs/typescript-smoke/tsconfig.json",
  nodeExamplesGradle: "app/node-examples.gradle.kts",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  examplesDoc: "docs/nodejs/EXAMPLES.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  proParityManifest: "sample/nodejs/pro-parity-suite/examples.json",
  desktopParityManifest: "sample/nodejs/desktop-parity-suite/examples.json",
};

const REQUIRED_PROFILE_TOKENS = [
  "RuntimeProfileId",
  "ProfileModuleCatalog",
  "ProfileModuleSpec",
  "ProfileModuleModeFor",
  "ProfileRequiredFor",
  "SafeDefaultModeFor",
  "PackagedBehaviorFor",
  "ProfileModuleRequiresDeclaration",
  "ProfileDeclarationMatrix",
  "safe_default",
  "pro_compat_opt_in",
  "desktop_compat_opt_in",
  "debug_unsafe_lab",
  "$autojs.java",
  "rhino.install",
  "ui.overlay",
  "worker_threads",
  "raw_network",
  "fs.advanced",
  "wasi",
  "inspector",
  "package_manager",
];

const REQUIRED_SMOKE_TOKENS = [
  'ProfileModuleModeFor<"java", "safe_default">',
  'ProfileModuleModeFor<"java", "pro_compat_opt_in">',
  'ProfileModuleModeFor<"ui.overlay", "pro_compat_opt_in">',
  'ProfileModuleModeFor<"worker_threads", "safe_default">',
  'ProfileModuleModeFor<"worker_threads", "desktop_compat_opt_in">',
  'ProfileModuleModeFor<"http", "desktop_compat_opt_in">',
  'ProfileModuleModeFor<"raw_network", "safe_default">',
  'ProfileModuleModeFor<"raw_network", "desktop_compat_opt_in">',
  'ProfileModuleModeFor<"wasi", "desktop_compat_opt_in">',
  'ProfileModuleModeFor<"inspector", "debug_unsafe_lab">',
  'ProfileRequiredFor<"package_manager">',
  'PackagedBehaviorFor<"$autojs.java">',
  'SafeDefaultModeFor<"npm.pure_js">',
  'ProfileDeclarationMatrix',
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
  console.log("Phase 13 type declaration profiles: " + report.decision.status);
  console.log("Phase 13 type declaration profile blockers: " + report.blockers.length);
  console.log("Phase 13 type declaration profile policy violations: " + report.policyViolations.length);
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
    scenario("profile_capability_matrix", "Profile-aware declaration matrix covers Safe/Pro/Desktop/Debug module status", [
      token(files.typesProfileCapabilities, 'schema: "autojs6-node-profile-declaration-matrix-v1"'),
      ...REQUIRED_PROFILE_TOKENS.map((value) => token(files.typesProfileCapabilities, value)),
      token(files.typesIndex, "profile_capabilities.d.ts"),
      token(files.typesPackage, "Safe/Pro/Desktop"),
      token(files.typesReadme, "P13-55 adds `profile_capabilities.d.ts`"),
      token(files.typesReadme, "does not declare denied desktop modules as callable modules"),
    ]),
    scenario("typescript_smoke", "TypeScript smoke covers Safe, Pro, Desktop, and Debug profile differences", [
      ...REQUIRED_SMOKE_TOKENS.map((value) => token(files.typescriptSmoke, value)),
      token(files.typescriptSmoke, '"safe_default"'),
      token(files.typescriptSmoke, '"pro_compat_opt_in"'),
      token(files.typescriptSmoke, '"desktop_compat_opt_in"'),
      token(files.typescriptSmoke, '"debug_unsafe_lab"'),
      token(files.nodeExamplesGradle, "profile_capabilities.d.ts"),
      token(files.nodeExamplesGradle, "typescript-smoke sample must cover Safe/Pro/Desktop profile declaration differences"),
    ]),
    scenario("example_surface_coverage", "Profile declarations align with P13-53 and P13-54 example surfaces", [
      token(files.proParityManifest, "java-interop"),
      token(files.proParityManifest, "rhino-install"),
      token(files.proParityManifest, "overlay-floaty"),
      token(files.desktopParityManifest, "worker-threads"),
      token(files.desktopParityManifest, "http-https"),
      token(files.desktopParityManifest, "raw-network"),
      token(files.desktopParityManifest, "npm-dependency"),
      token(files.typesProfileCapabilities, "$autojs.java"),
      token(files.typesProfileCapabilities, "worker_threads"),
      token(files.typesProfileCapabilities, "raw_network"),
      token(files.typesProfileCapabilities, "package_manager"),
    ]),
    scenario("security_rollback_privacy_alignment", "Profile declarations stay aligned with P13-49 to P13-52 security, rollback, and privacy metadata", [
      token(files.typesAutojsGlobal, "ProfileSelectionDiagnostics"),
      token(files.typesAutojsGlobal, "ProfileSelectionFailureHint"),
      token(files.typesAutojsGlobal, "ProfileRollbackPolicy"),
      token(files.typesAutojsGlobal, "PrivacyDisclosurePolicy"),
      token(files.typesAutojsGlobal, "metadata_only_no_authority"),
      token(files.typesAutojsGlobal, "implicitHighPrivilegeDenied"),
      token(files.typescriptSmoke, "autojsProfile.profileSelection"),
      token(files.typescriptSmoke, "autojsProfile.profileRollbackPolicy"),
      token(files.typescriptSmoke, "autojsProfile.privacyDisclosurePolicy"),
      token(files.typescriptSmoke, "ProfileSelectionHighRiskCapability"),
      token(files.typescriptSmoke, "ProfileRollbackPackagedDowngrade"),
      token(files.typescriptSmoke, "PrivacyDisclosurePackagedPolicy"),
      token(files.typesReadme, "P13-49 extends the `autojs6:profile` declaration with `profileSelection`"),
      token(files.typesReadme, "P13-51 extends the `autojs6:profile` declaration with `profileRollbackPolicy`"),
      token(files.typesReadme, "P13-52 extends the `autojs6:profile` declaration with"),
    ]),
    scenario("docs_gate_and_surface", "Docs, release matrix, surface manifest, and Gradle gate record P13-55", [
      token(files.phase13Tasks, "P13-55 partial landing"),
      token(files.p13Report, "P13-55 records TypeScript profile declarations as partial"),
      token(files.phase13GradleGate, "verifyNodePhase13TypeDeclarationProfiles"),
      token(files.phase13GradleGate, "verify-type-declaration-profiles.js"),
      token(files.phase13GradleGate, "typeCheckNodeTypescriptSmoke"),
      token(files.testingDoc, "verifyNodePhase13TypeDeclarationProfiles"),
      token(files.ciGatesDoc, "verifyNodePhase13TypeDeclarationProfiles"),
      token(files.proComparison, "P13-55 records TypeScript profile declarations as partial"),
      token(files.proAutoxNotes, "P13-55 records TypeScript profile declarations as partial"),
      token(files.capabilityMatrix, "P13-55 TypeScript profile declarations"),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
    ], [
      "Generated declaration package release checks remain gated until profile promotion and release packaging.",
    ]),
  ];

  const checks = [
    check("required-files", Object.values(files).every((file) => file.exists), "All P13-55 source, doc, and gate files must exist."),
    check(
      "profile-types-do-not-declare-denied-modules",
      !files.typesProfileCapabilities.text.includes('declare module "worker_threads"') &&
        !files.typesProfileCapabilities.text.includes('declare module "wasi"') &&
        !files.typesProfileCapabilities.text.includes('declare module "inspector"'),
      "P13-55 profile matrix must not make denied desktop modules callable by declaring their modules.",
    ),
    check(
      "runtime-profile-stays-diagnostic",
      files.typesProfileCapabilities.text.includes("readonly grantsAuthority: false") &&
        files.typesProfileCapabilities.text.includes("readonly metadataOnly: true") &&
        files.typescriptSmoke.text.includes("grantsAuthority: false"),
      "P13-55 declarations must remain metadata-only and must not grant authority.",
    ),
    check(
      "smoke-safe-pro-desktop",
      REQUIRED_SMOKE_TOKENS.every((value) => files.typescriptSmoke.text.includes(value)),
      "TypeScript smoke must cover Safe, Pro, Desktop, and Debug declaration differences.",
    ),
    check(
      "surface-entry",
      Boolean(surfaceEntry) &&
        Array.isArray(surfaceEntry.tasks) &&
        surfaceEntry.tasks.includes("P13-55") &&
        Array.isArray(surfaceEntry.evidence) &&
        surfaceEntry.evidence.some((entry) => entry.includes(REPORT_BASENAME)) &&
        surfaceEntry.evidence.some((entry) => entry.includes("profile_capabilities.d.ts")),
      "`docs.examples.final_gate` must keep P13-55 mapped and reference type declaration evidence.",
    ),
  ];

  const policyViolations = [
    ...checks.filter((entry) => !entry.passed).map((entry) => entry.message),
    ...scenarios.flatMap((entry) => entry.remaining.filter((item) => item.startsWith("missing evidence"))),
  ];
  const blockers = [
    ...scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`)),
    "runtime profile promotion is still diagnostic-only and does not grant Pro/Desktop authority",
  ];
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-type-declaration-profiles-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13TypeDeclarationProfiles",
    task: "P13-55",
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
      declarationMatrixReady: scenarioById(scenarios, "profile_capability_matrix").status === "ready",
      typeSmokeCoversProfiles: scenarioById(scenarios, "typescript_smoke").status === "ready",
      securityRollbackPrivacyAligned: scenarioById(scenarios, "security_rollback_privacy_alignment").status === "ready",
      deniedDesktopModulesCallable: false,
      metadataOnly: true,
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
    "# Phase 13 Type Declaration Profiles",
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
      console.log("Usage: node verify-type-declaration-profiles.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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
