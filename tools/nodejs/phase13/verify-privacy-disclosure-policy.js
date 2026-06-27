#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-privacy-disclosure-policy";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_52_PRIVACY_DISCLOSURE_POLICY.md",
  privacyDisclosurePolicy: "docs/nodejs/security/PRIVACY_DISCLOSURE_POLICY.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  kotlinPolicy: "app/src/main/java/org/autojs/autojs/engine/NodePrivacyDisclosurePolicy.kt",
  doctorGenerator: "app/src/main/java/org/autojs/autojs/ui/settings/NodeDoctorReportGenerator.kt",
  doctorTest: "app/src/androidTest/java/org/autojs/autojs/ui/settings/NodeDoctorReportGeneratorInstrumentationTest.kt",
  p13PrivacyTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodePrivacyDisclosurePolicyPhase13DecisionInstrumentationTest.kt",
  typesAutojsGlobal: "docs/nodejs/types/autojs6-node/autojs_global.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typescriptSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
};

const REQUIRED_SURFACES = [
  "screen_capture",
  "media_recording",
  "ui_overlay",
  "accessibility",
  "network",
  "raw_network",
  "filesystem_relaxation",
  "java_interop",
  "child_process",
  "native_addon",
  "inspector",
  "wasi",
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
  console.log("Phase 13 privacy disclosure policy: " + report.decision.status);
  console.log("Phase 13 privacy disclosure blockers: " + report.blockers.length);
  console.log("Phase 13 privacy disclosure policy violations: " + report.policyViolations.length);
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
  const surfaceEntry = surface(files.surfaceManifest.text, "security.rollback_privacy");
  const kotlinBlock = files.kotlinPolicy.text;
  const runtimeBlock = block(
    files.nodeBridgeSources.text,
    "function __autojs6_privacy_disclosure_policy_report",
    "  function __autojs6_bridge_normalize_capability",
  );
  const scenarios = [
    scenario("kotlin_policy", "NodePrivacyDisclosurePolicy records prompt, disclosure, and packaged denial requirements without authority", [
      token(files.kotlinPolicy, "object NodePrivacyDisclosurePolicy"),
      token(files.kotlinPolicy, 'SCHEMA = "autojs6-node-privacy-disclosure-policy-v1"'),
      token(files.kotlinPolicy, 'STATUS = "policy_partial"'),
      token(files.kotlinPolicy, 'PACKAGED_DISCLOSURE_SCHEMA = "autojs6-node-packaged-disclosure-manifest-v1"'),
      token(files.kotlinPolicy, 'put("metadataOnly", true)'),
      token(files.kotlinPolicy, 'put("grantsAuthority", false)'),
      token(files.kotlinPolicy, 'put("implicitHighPrivilegeDenied", true)'),
      token(files.kotlinPolicy, 'put("packagedImplicitHighPrivilegeDenied", true)'),
      token(files.kotlinPolicy, 'put("requiresDisclosureManifest", true)'),
      token(files.kotlinPolicy, 'put("denyUndeclaredHighRiskSurfaces", true)'),
      ...REQUIRED_SURFACES.map((id) => token(files.kotlinPolicy, `id = "${id}"`)),
      token(files.kotlinPolicy, "foreground_service_notification"),
      token(files.kotlinPolicy, "project.json:node.disclosures"),
    ], [
      "Settings UI prompt copy, real foreground disclosure ownership, and packaged disclosure manifest review remain future work.",
    ]),
    scenario("runtime_and_doctor_diagnostics", "autojs6:profile and Node Doctor expose privacy disclosure diagnostics to scripts and device reports", [
      token(files.nodeBridgeSources, "const privacyDisclosurePolicy = __autojs6_privacy_disclosure_policy_report();"),
      token(files.nodeBridgeSources, "privacyDisclosurePolicy,"),
      token(files.nodeBridgeSources, 'schema: "autojs6-node-privacy-disclosure-policy-v1"'),
      token(files.nodeBridgeSources, 'status: "policy_partial"'),
      token(files.nodeBridgeSources, "implicitHighPrivilegeDenied: true"),
      token(files.nodeBridgeSources, "packagedImplicitHighPrivilegeDenied: true"),
      token(files.nodeBridgeSources, "surfaces: Object.freeze(["),
      token(files.doctorGenerator, 'put("privacyDisclosurePolicy", NodePrivacyDisclosurePolicy.toJsonObject())'),
      token(files.doctorGenerator, 'appendLine("Privacy Disclosure Policy:")'),
      token(files.doctorTest, "NodePrivacyDisclosurePolicy.SCHEMA"),
      token(files.p13PrivacyTest, "runtimeProfileExposesFrozenPrivacyDisclosurePolicyToScripts"),
      token(files.p13PrivacyTest, "kotlinPolicyRecordsPromptDisclosureAndPackagedDenialRequirements"),
      token(files.p13PrivacyTest, "Object.isFrozen(policy)"),
    ]),
    scenario("typescript_surface", "TypeScript declarations and smoke cover privacy disclosure policy shapes", [
      token(files.typesAutojsGlobal, "export interface PrivacyDisclosurePolicy"),
      token(files.typesAutojsGlobal, "export interface PrivacyDisclosureSurface"),
      token(files.typesAutojsGlobal, "export interface PrivacyDisclosurePackagedPolicy"),
      token(files.typesAutojsGlobal, "readonly privacyDisclosurePolicy: PrivacyDisclosurePolicy"),
      token(files.typesReadme, "P13-52 extends the `autojs6:profile` declaration with"),
      token(files.typesReadme, "`privacyDisclosurePolicy`. The field reports sensitive surface prompt"),
      token(files.typescriptSmoke, "autojsProfile.privacyDisclosurePolicy.status"),
      token(files.typescriptSmoke, "AutoJs6Node.PrivacyDisclosureSurface"),
    ]),
    scenario("docs_gate_and_surface", "Docs, release matrix, surface manifest, and Gradle gate record P13-52", [
      token(files.phase13Tasks, "P13-52 partial landing"),
      token(files.p13Report, "P13-52 records privacy and disclosure policy as partial"),
      token(files.privacyDisclosurePolicy, "autojs6-node-privacy-disclosure-policy-v1"),
      token(files.phase13GradleGate, "verifyNodePhase13PrivacyDisclosurePolicy"),
      token(files.testingDoc, "verifyNodePhase13PrivacyDisclosurePolicy"),
      token(files.ciGatesDoc, "verifyNodePhase13PrivacyDisclosurePolicy"),
      token(files.compatibilityProfile, "P13-52 records privacy and disclosure policy as partial"),
      token(files.securityModel, "P13-52 records privacy and disclosure policy as partial"),
      token(files.proComparison, "P13-52 records privacy and disclosure policy as partial"),
      token(files.proAutoxNotes, "P13-52 records privacy and disclosure policy as partial"),
      token(files.capabilityMatrix, "P13-52 privacy disclosure policy"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
    ], [
      "P13-52 is a policy/diagnostic gate; actual prompts, notifications, and packaged review UI are not yet promoted.",
    ]),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-52"), "P13-52 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-52 report must exist."),
    check("policy-doc-present", files.privacyDisclosurePolicy.exists, "P13-52 privacy disclosure policy document must exist."),
    check("surface-present", Boolean(surfaceEntry), "`security.rollback_privacy` surface must exist."),
    check(
      "surface-evidence",
      surfaceEntry &&
        (surfaceEntry.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)) &&
        (surfaceEntry.evidence || []).some((entry) => entry.includes("PRIVACY_DISCLOSURE_POLICY.md")),
      "`security.rollback_privacy` surface must reference P13-52 privacy disclosure evidence.",
    ),
    check(
      "all-sensitive-surfaces-listed",
      REQUIRED_SURFACES.every((id) => kotlinBlock.includes(`id = "${id}"`) && runtimeBlock.includes(`"${id}"`)),
      "Kotlin and runtime policy must list all required sensitive surfaces.",
    ),
    check(
      "no-policy-authority",
      !kotlinBlock.includes('put("grantsAuthority", true)') &&
        !kotlinBlock.includes('put("implicitGrantAllowed", true)') &&
        !runtimeBlock.includes("grantsAuthority: true") &&
        !runtimeBlock.includes("implicitGrantAllowed: true"),
      "P13-52 policy must not grant authority or allow implicit grants.",
    ),
    check(
      "packaged-fail-closed",
      kotlinBlock.includes('put("packagedImplicitHighPrivilegeDenied", true)') &&
        kotlinBlock.includes('put("requiresDisclosureManifest", true)') &&
        runtimeBlock.includes("packagedImplicitHighPrivilegeDenied: true") &&
        runtimeBlock.includes("requiresDisclosureManifest: true") &&
        runtimeBlock.includes('defaultAction: "deny_without_reviewed_disclosure_manifest"'),
      "Packaged high-privilege surfaces must remain denied until reviewed disclosure metadata exists.",
    ),
    check(
      "prompt-and-foreground-policy",
      kotlinBlock.includes('put("foregroundDisclosureRequiredForPromotedSensitiveSurfaces", true)') &&
        kotlinBlock.includes('put("userPromptRequiredForPromotedSensitiveSurfaces", true)') &&
        runtimeBlock.includes("foregroundDisclosureRequiredForPromotedSensitiveSurfaces: true") &&
        runtimeBlock.includes("userPromptRequiredForPromotedSensitiveSurfaces: true"),
      "P13-52 must require user prompts and foreground disclosure before sensitive surface promotion.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-privacy-disclosure-policy-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13PrivacyDisclosurePolicy",
    task: "P13-52",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: surfaceEntry || {
      id: "security.rollback_privacy",
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
      kotlinPolicyReady: scenarioById(scenarios, "kotlin_policy").status !== "missing_evidence",
      runtimeDiagnosticsReady: scenarioById(scenarios, "runtime_and_doctor_diagnostics").status === "ready",
      typescriptSurfaceReady: scenarioById(scenarios, "typescript_surface").status === "ready",
      implicitHighPrivilegeDenied: true,
      packagedImplicitHighPrivilegeDenied: true,
      grantsAuthority: false,
      promptUiReady: false,
      foregroundDisclosureReady: false,
      packagedReviewReady: false,
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

function block(text, startToken, endToken) {
  const start = text.indexOf(startToken);
  if (start < 0) return "";
  const end = text.indexOf(endToken, start);
  if (end < 0) return text.slice(start);
  return text.slice(start, end + endToken.length);
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Privacy Disclosure Policy",
    "",
    `- Task: ${report.task}`,
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Implicit high privilege denied: ${report.decision.implicitHighPrivilegeDenied}`,
    `- Packaged implicit high privilege denied: ${report.decision.packagedImplicitHighPrivilegeDenied}`,
    `- Grants authority: ${report.decision.grantsAuthority}`,
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
      console.log("Usage: node verify-privacy-disclosure-policy.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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
