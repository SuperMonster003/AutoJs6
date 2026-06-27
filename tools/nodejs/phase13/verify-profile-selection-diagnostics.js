#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-profile-selection-diagnostics";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_49_PROFILE_SELECTION_DIAGNOSTICS.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  profileSelectionDiagnostics: "app/src/main/java/org/autojs/autojs/engine/NodeProfileSelectionDiagnostics.kt",
  bridgeManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  doctorGenerator: "app/src/main/java/org/autojs/autojs/ui/settings/NodeDoctorReportGenerator.kt",
  doctorTest: "app/src/androidTest/java/org/autojs/autojs/ui/settings/NodeDoctorReportGeneratorInstrumentationTest.kt",
  p13ProfileSelectionTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeProfileSelectionDiagnosticsPhase13DecisionInstrumentationTest.kt",
  typesAutojsGlobal: "docs/nodejs/types/autojs6-node/autojs_global.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typescriptSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const report = buildReport(files);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 13 profile selection diagnostics: " + report.decision.status);
  console.log("Phase 13 profile selection blockers: " + report.blockers.length);
  console.log("Phase 13 profile selection policy violations: " + report.policyViolations.length);
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
  const surfaceEntry = surface(files.surfaceManifest.text, "profile.promotion_model");
  const kotlinBlock = files.profileSelectionDiagnostics.text;
  const runtimeBlock = block(files.nodeBridgeSources.text, "function __autojs6_profile_selection_report", "  function __autojs6_bridge_limit_defaults()");
  const errorBlock = block(files.nodeBridgeSources.text, "function __autojs6_bridge_capability_not_declared_error", "  function __autojs6_bridge_next_id()");
  const scenarios = [
    scenario("diagnostic_selector", "Kotlin profile selection diagnostics summarize Safe/Pro/Desktop/Debug requests without granting authority", [
      token(files.profileSelectionDiagnostics, "object NodeProfileSelectionDiagnostics"),
      token(files.profileSelectionDiagnostics, 'SCHEMA = "autojs6-node-profile-selection-diagnostics-v1"'),
      token(files.profileSelectionDiagnostics, 'STATUS = "diagnostic_only"'),
      token(files.profileSelectionDiagnostics, 'SAFE_DEFAULT = "safe_default"'),
      token(files.profileSelectionDiagnostics, 'PRO_COMPAT_OPT_IN = "pro_compat_opt_in"'),
      token(files.profileSelectionDiagnostics, 'DESKTOP_COMPAT_OPT_IN = "desktop_compat_opt_in"'),
      token(files.profileSelectionDiagnostics, 'DEBUG_UNSAFE_LAB = "debug_unsafe_lab"'),
      token(files.profileSelectionDiagnostics, "fun fromWorkingDirectory"),
      token(files.profileSelectionDiagnostics, "fun failureHintForMissingCapabilities"),
      token(files.profileSelectionDiagnostics, "fun missingCapabilityMessage"),
      token(files.profileSelectionDiagnostics, "declarationExamplesJson"),
      token(files.profileSelectionDiagnostics, 'put("effectiveProfile", SAFE_DEFAULT)'),
      token(files.profileSelectionDiagnostics, 'put("profilePromotion", false)'),
      token(files.profileSelectionDiagnostics, 'put("userSelectable", false)'),
      token(files.profileSelectionDiagnostics, 'put("highRiskCapabilities", JSONArray(highRiskCapabilitiesJson(declaredPermissions)))'),
      token(files.p13ProfileSelectionTest, "diagnosticsReportProjectPackageProfileSelectionAndFailureHints"),
      token(files.p13ProfileSelectionTest, 'getJSONObject("declarationExamples")'),
      token(files.p13ProfileSelectionTest, "desktop_compat_opt_in"),
      token(files.p13ProfileSelectionTest, "debug_unsafe_lab"),
    ], [
      "Profile selection remains diagnostic-only; project settings and packaged metadata do not yet promote effective Pro/Desktop/Debug profiles.",
    ]),
    scenario("runtime_profile_and_error_hints", "autojs6:profile and capability errors expose profile selection diagnostics to scripts", [
      token(files.nodeBridgeSources, "const profileSelection = __autojs6_profile_selection_report(featureFlags, bridgePermissionsReport);"),
      token(files.nodeBridgeSources, "profileSelection,"),
      token(files.nodeBridgeSources, "function __autojs6_profile_required_profile_for_capability"),
      token(files.nodeBridgeSources, "function __autojs6_profile_failure_hint_for_missing_capabilities"),
      token(files.nodeBridgeSources, "function __autojs6_profile_declaration_examples"),
      token(files.nodeBridgeSources, "declarationExamples: __autojs6_profile_declaration_examples(missing)"),
      token(files.nodeBridgeSources, 'schema: "autojs6-node-profile-selection-diagnostics-v1"'),
      token(files.nodeBridgeSources, 'selectionMode: "safe_default_effective_metadata_only"'),
      token(files.nodeBridgeSources, 'profilePromotion: false'),
      token(files.nodeBridgeSources, 'userSelectable: false'),
      token(files.nodeBridgeSources, 'requiredProfiles,'),
      token(files.nodeBridgeSources, 'requiredNodePermissions: missing'),
      token(files.nodeBridgeSources, 'requiredAndroidPermissions: androidPermissions'),
      token(files.nodeBridgeSources, '__autojs6_define_error_property(error, "requiredProfiles", profileSelectionHint.requiredProfiles);'),
      token(files.nodeBridgeSources, '__autojs6_define_error_property(error, "profileSelectionHint", profileSelectionHint);'),
      token(files.typesAutojsGlobal, "export interface ProfileSelectionDiagnostics"),
      token(files.typesAutojsGlobal, "export interface ProfileSelectionDeclarationExamples"),
      token(files.typesAutojsGlobal, "export interface AutoJsBridgeCapabilityNotDeclaredError"),
      token(files.typesAutojsGlobal, "readonly profileSelection: ProfileSelectionDiagnostics"),
      token(files.typesAutojsGlobal, "readonly declarationExamples: ProfileSelectionDeclarationExamples"),
      token(files.typescriptSmoke, "autojsProfile.profileSelection.highRiskCapabilities"),
      token(files.typescriptSmoke, "profileSelectionFailureHint.declarationExamples"),
      token(files.typescriptSmoke, "AutoJs6Node.AutoJsBridgeCapabilityNotDeclaredError"),
      token(files.p13ProfileSelectionTest, "runtimeProfileSelectionAndCapabilityErrorsExposeActionableHints"),
      token(files.p13ProfileSelectionTest, "declarationExamples.projectJson.node.permissions"),
      token(files.p13ProfileSelectionTest, "p13profile.error="),
    ]),
    scenario("doctor_ux", "Node Doctor JSON and text reports include project/package profile selection and high-risk declarations", [
      token(files.doctorGenerator, "projectRoot: File? = null"),
      token(files.doctorGenerator, 'put("profileSelection", profileSelectionJson(projectRoot))'),
      token(files.doctorGenerator, "NodeProfileSelectionDiagnostics.fromWorkingDirectory(projectRoot.absolutePath)"),
      token(files.doctorGenerator, 'appendLine("Profile Selection:")'),
      token(files.doctorGenerator, 'appendLine("  effective profile: ${profileSelection.getString("effectiveProfile")}")'),
      token(files.doctorGenerator, 'appendLine("  high-risk capabilities: ${profileSelection.getJSONArray("highRiskCapabilities").length()}")'),
      token(files.doctorTest, "generatesProjectProfileSelectionDiagnostics"),
      token(files.doctorTest, "blocked profile promotions:"),
      token(files.doctorTest, "declaredAndroidPermissions"),
    ]),
    scenario("docs_gate_and_surface", "Docs, release matrix, surface manifest, and Gradle gate record P13-49", [
      token(files.phase13Tasks, "P13-49 partial landing"),
      token(files.p13Report, "P13-49 records profile selection UX and diagnostics as partial"),
      token(files.p13Report, "declarationExamples"),
      token(files.phase13GradleGate, "verifyNodePhase13ProfileSelectionDiagnostics"),
      token(files.testingDoc, "verifyNodePhase13ProfileSelectionDiagnostics"),
      token(files.ciGatesDoc, "verifyNodePhase13ProfileSelectionDiagnostics"),
      token(files.compatibilityProfile, "P13-49 records profile selection UX and diagnostics as partial"),
      token(files.securityModel, "P13-49 records profile selection UX and diagnostics as partial"),
      token(files.proComparison, "P13-49 records profile selection UX and diagnostics as partial"),
      token(files.proAutoxNotes, "P13-49 records profile selection UX and diagnostics as partial"),
      token(files.typesReadme, "P13-49 extends the `autojs6:profile` declaration"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.capabilityMatrix, "P13-49 profile selection diagnostics"),
    ], [
      "Project settings UI, packaged builder UI, and actual effective profile switching remain future implementation work.",
    ]),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-49"), "P13-49 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-49 report must exist."),
    check("surface-present", Boolean(surfaceEntry), "`profile.promotion_model` surface must exist."),
    check(
      "surface-evidence",
      surfaceEntry && (surfaceEntry.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`profile.promotion_model` surface must reference P13-49 evidence.",
    ),
    check(
      "diagnostic-only-kotlin",
      kotlinBlock.includes('put("effectiveProfile", SAFE_DEFAULT)') &&
        kotlinBlock.includes('put("profilePromotion", false)') &&
        kotlinBlock.includes('put("userSelectable", false)') &&
        kotlinBlock.includes('put("metadataOnly", manifest.packagedMetadata.metadataOnly)') &&
        kotlinBlock.includes('put("grantsAuthority", manifest.packagedMetadata.grantsAuthority)'),
      "Kotlin diagnostics must keep profile selection diagnostic-only and truthfully expose metadata authority.",
    ),
    check(
      "diagnostic-only-runtime",
      runtimeBlock.includes('effectiveProfile: "safe_default"') &&
        runtimeBlock.includes('profilePromotion: false') &&
        runtimeBlock.includes('userSelectable: false') &&
        runtimeBlock.includes('metadataOnly: metadata.metadataOnly !== false') &&
        runtimeBlock.includes('grantsAuthority: metadata.grantsAuthority === true'),
      "Runtime profileSelection must keep safe_default effective and expose metadata-only/grantsAuthority truth.",
    ),
    check(
      "actionable-error-hints",
      errorBlock.includes('__autojs6_define_error_property(error, "requiredProfiles", profileSelectionHint.requiredProfiles);') &&
        errorBlock.includes('__autojs6_define_error_property(error, "requiredNodePermissions", profileSelectionHint.requiredNodePermissions);') &&
        errorBlock.includes('__autojs6_define_error_property(error, "requiredAndroidPermissions", profileSelectionHint.requiredAndroidPermissions);') &&
        errorBlock.includes('__autojs6_define_error_property(error, "profileSelectionHint", profileSelectionHint);') &&
        files.nodeBridgeSources.text.includes("declare node.permissions") &&
        files.nodeBridgeSources.text.includes("declarationExamples"),
      "Capability-not-declared errors must include profile, node permission, Android permission, declaration hints, and declaration examples.",
    ),
    check(
      "no-profile-promotion",
      !kotlinBlock.includes('put("effectiveProfile", PRO_COMPAT_OPT_IN)') &&
        !kotlinBlock.includes('put("effectiveProfile", DESKTOP_COMPAT_OPT_IN)') &&
        !kotlinBlock.includes('put("profilePromotion", true)') &&
        !kotlinBlock.includes('put("userSelectable", true)') &&
        !runtimeBlock.includes('effectiveProfile: "pro_compat_opt_in"') &&
        !runtimeBlock.includes('effectiveProfile: "desktop_compat_opt_in"') &&
        !runtimeBlock.includes("profilePromotion: true") &&
        !runtimeBlock.includes("userSelectable: true"),
      "P13-49 must not promote Pro/Desktop/Debug profiles or expose user-selectable switching as enabled.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-profile-selection-diagnostics-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13ProfileSelectionDiagnostics",
    task: "P13-49",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: surfaceEntry || {
      id: "profile.promotion_model",
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
      diagnosticSelectorReady: scenarioById(scenarios, "diagnostic_selector").status !== "missing_evidence",
      runtimeProfileSelectionReady: scenarioById(scenarios, "runtime_profile_and_error_hints").status === "ready",
      doctorUxReady: scenarioById(scenarios, "doctor_ux").status === "ready",
      profilePromotionPromoted: false,
      userSelectableProfilePromoted: false,
      effectiveProfile: "safe_default",
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
    "# Phase 13 Profile Selection Diagnostics",
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
      console.log("Usage: node verify-profile-selection-diagnostics.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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
