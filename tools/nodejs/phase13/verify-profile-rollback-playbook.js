#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-profile-rollback-playbook";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_51_PROFILE_ROLLBACK_PLAYBOOK.md",
  rollbackPlaybook: "docs/nodejs/security/PROFILE_ROLLBACK_PLAYBOOK.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  gradleProperties: "gradle.properties",
  appBuildGradle: "app/build.gradle.kts",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  rollbackPolicy: "app/src/main/java/org/autojs/autojs/engine/NodeProfileRollbackPolicy.kt",
  doctorGenerator: "app/src/main/java/org/autojs/autojs/ui/settings/NodeDoctorReportGenerator.kt",
  doctorTest: "app/src/androidTest/java/org/autojs/autojs/ui/settings/NodeDoctorReportGeneratorInstrumentationTest.kt",
  p13RollbackTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeProfileRollbackPlaybookPhase13DecisionInstrumentationTest.kt",
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

const PROFILE_PROPERTIES = [
  "autojs.nodejs.profile.pro.enabled",
  "autojs.nodejs.profile.desktop.enabled",
  "autojs.nodejs.profile.debugUnsafeLab.enabled",
  "autojs.nodejs.relaxedCapabilities.enabled",
];

const RELAXED_CAPABILITIES = [
  "network",
  "raw_network",
  "worker_threads",
  "java_interop",
  "filesystem_relaxation",
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
  console.log("Phase 13 profile rollback playbook: " + report.decision.status);
  console.log("Phase 13 profile rollback blockers: " + report.blockers.length);
  console.log("Phase 13 profile rollback policy violations: " + report.policyViolations.length);
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
  const kotlinBlock = files.rollbackPolicy.text;
  const runtimeBlock = block(files.nodeBridgeSources.text, "function __autojs6_profile_rollback_policy_report", "  function __autojs6_bridge_normalize_capability");
  const gradleBlock = files.appBuildGradle.text;
  const scenarios = [
    scenario("gradle_property_switches", "Gradle and BuildConfig expose default-off profile and relaxed-capability rollback switches", [
      ...PROFILE_PROPERTIES.map((property) => token(files.gradleProperties, `${property}=false`)),
      ...PROFILE_PROPERTIES.map((property) => token(files.appBuildGradle, `providers.gradleProperty("${property}")`)),
      token(files.appBuildGradle, "NODEJS_PROFILE_PRO_ENABLED"),
      token(files.appBuildGradle, "NODEJS_PROFILE_DESKTOP_ENABLED"),
      token(files.appBuildGradle, "NODEJS_PROFILE_DEBUG_UNSAFE_LAB_ENABLED"),
      token(files.appBuildGradle, "NODEJS_RELAXED_CAPABILITIES_ENABLED"),
      token(files.appBuildGradle, "nodeJsReleaseForbiddenExperimentalFlags"),
      token(files.appBuildGradle, "Release Node builds cannot enable experimental feature flag(s)"),
    ]),
    scenario("kotlin_policy", "NodeProfileRollbackPolicy records per-profile and per-capability rollback actions without granting authority", [
      token(files.rollbackPolicy, "object NodeProfileRollbackPolicy"),
      token(files.rollbackPolicy, 'SCHEMA = "autojs6-node-profile-rollback-policy-v1"'),
      token(files.rollbackPolicy, 'STATUS = "playbook_partial"'),
      token(files.rollbackPolicy, 'PRO_PROFILE_PROPERTY = "autojs.nodejs.profile.pro.enabled"'),
      token(files.rollbackPolicy, 'DESKTOP_PROFILE_PROPERTY = "autojs.nodejs.profile.desktop.enabled"'),
      token(files.rollbackPolicy, 'DEBUG_UNSAFE_LAB_PROPERTY = "autojs.nodejs.profile.debugUnsafeLab.enabled"'),
      token(files.rollbackPolicy, 'RELAXED_CAPABILITIES_PROPERTY = "autojs.nodejs.relaxedCapabilities.enabled"'),
      token(files.rollbackPolicy, 'put("profilePromotion", false)'),
      token(files.rollbackPolicy, 'put("grantsAuthority", false)'),
      token(files.rollbackPolicy, 'put("safeDefaultAlwaysAvailable", true)'),
      token(files.rollbackPolicy, 'put("independentProfileDisable", true)'),
      token(files.rollbackPolicy, 'put("crashFallbackProfile", SAFE_DEFAULT)'),
      token(files.rollbackPolicy, 'put("stressFallbackProfile", SAFE_DEFAULT)'),
      token(files.rollbackPolicy, "BuildConfig.NODEJS_PROFILE_PRO_ENABLED"),
      token(files.rollbackPolicy, "BuildConfig.NODEJS_RELAXED_CAPABILITIES_ENABLED"),
      ...RELAXED_CAPABILITIES.map((id) => token(files.rollbackPolicy, `id = "${id}"`)),
      token(files.rollbackPolicy, "publish_safe_profile_patch_or_remove_promoted_metadata"),
      token(files.rollbackPolicy, 'put("remoteKillSwitchPolicy", "rejected_v1_1_publish_safe_profile_patch")'),
    ], []),
    scenario("runtime_and_doctor_diagnostics", "autojs6:profile and Node Doctor expose the rollback playbook to scripts and diagnostics", [
      token(files.nodeBridgeSources, "const profileRollbackPolicy = __autojs6_profile_rollback_policy_report(featureFlags);"),
      token(files.nodeBridgeSources, "profileRollbackPolicy,"),
      token(files.nodeBridgeSources, 'schema: "autojs6-node-profile-rollback-policy-v1"'),
      token(files.nodeBridgeSources, 'status: "playbook_partial"'),
      token(files.nodeBridgeSources, 'rollbackTargetProfile: "safe_default"'),
      token(files.nodeBridgeSources, 'profilePromotion: false'),
      token(files.nodeBridgeSources, 'grantsAuthority: false'),
      token(files.nodeBridgeSources, 'independentProfileDisable: true'),
      token(files.nodeBridgeSources, "profileStates: Object.freeze(["),
      token(files.nodeBridgeSources, "capabilityStates: Object.freeze(["),
      token(files.nodeBridgeSources, "rollbackTriggers: Object.freeze(["),
      token(files.nodeBridgeSources, 'remoteKillSwitchPolicy: "rejected_v1_1_publish_safe_profile_patch"'),
      token(files.doctorGenerator, 'put("profileRollbackPolicy", NodeProfileRollbackPolicy.toJsonObject())'),
      token(files.doctorGenerator, 'appendLine("Profile Rollback Policy:")'),
      token(files.doctorTest, "NodeProfileRollbackPolicy.SCHEMA"),
      token(files.p13RollbackTest, "runtimeProfileExposesFrozenRollbackPolicyToScripts"),
      token(files.p13RollbackTest, "kotlinPolicyRecordsPerProfileAndCapabilityRollbackPlaybook"),
      token(files.p13RollbackTest, "Object.isFrozen(rollback)"),
    ]),
    scenario("typescript_surface", "TypeScript declarations and smoke cover profile rollback policy shapes", [
      token(files.typesAutojsGlobal, "export interface ProfileRollbackPolicy"),
      token(files.typesAutojsGlobal, "export interface ProfileRollbackState"),
      token(files.typesAutojsGlobal, "export interface CapabilityRollbackState"),
      token(files.typesAutojsGlobal, "export interface ProfileRollbackTrigger"),
      token(files.typesAutojsGlobal, "readonly remoteKillSwitchPolicy"),
      token(files.typesAutojsGlobal, "readonly profileRollbackPolicy: ProfileRollbackPolicy"),
      token(files.typesReadme, "P13-51 extends the `autojs6:profile` declaration with `profileRollbackPolicy`"),
      token(files.typescriptSmoke, "autojsProfile.profileRollbackPolicy.status"),
      token(files.typescriptSmoke, "packagedRemoteKillSwitchPolicy"),
      token(files.typescriptSmoke, "AutoJs6Node.ProfileRollbackTrigger"),
    ]),
    scenario("docs_gate_and_surface", "Docs, release matrix, surface manifest, and Gradle gate record P13-51", [
      token(files.phase13Tasks, "P13-51 partial landing"),
      token(files.p13Report, "P13-51 records per-profile rollback playbook as partial"),
      token(files.p13Report, "Remote packaged kill switches are explicitly rejected for v1.1"),
      token(files.rollbackPlaybook, "autojs6-node-profile-rollback-policy-v1"),
      token(files.rollbackPlaybook, "Remote packaged kill switches are explicitly rejected for v1.1"),
      token(files.phase13GradleGate, "verifyNodePhase13ProfileRollbackPlaybook"),
      token(files.testingDoc, "verifyNodePhase13ProfileRollbackPlaybook"),
      token(files.ciGatesDoc, "verifyNodePhase13ProfileRollbackPlaybook"),
      token(files.compatibilityProfile, "P13-51 records per-profile rollback playbook as partial"),
      token(files.securityModel, "P13-51 records per-profile rollback playbook as partial"),
      token(files.proComparison, "P13-51 records per-profile rollback playbook as partial"),
      token(files.proAutoxNotes, "P13-51 records per-profile rollback playbook as partial"),
      token(files.capabilityMatrix, "P13-51 profile rollback playbook"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
    ]),
  ];

  const forbiddenRuntimePromotions = [
    'effectiveProfile: "pro_compat_opt_in"',
    'effectiveProfile: "desktop_compat_opt_in"',
    "profilePromotion: true",
    "userSelectable: true",
    "grantsAuthority: true",
    "rawSockets: true",
    "rawHostPaths: true",
    "rawProcessHandles: true",
    "downloadExec: true",
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-51"), "P13-51 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-51 report must exist."),
    check("rollback-playbook-present", files.rollbackPlaybook.exists, "P13-51 rollback playbook document must exist."),
    check("surface-present", Boolean(surfaceEntry), "`security.rollback_privacy` surface must exist."),
    check(
      "surface-evidence",
      surfaceEntry && (surfaceEntry.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)) && (surfaceEntry.evidence || []).some((entry) => entry.includes("PROFILE_ROLLBACK_PLAYBOOK.md")),
      "`security.rollback_privacy` surface must reference P13-51 rollback playbook evidence.",
    ),
    check(
      "profile-switches-default-off",
      PROFILE_PROPERTIES.every((property) => files.gradleProperties.text.includes(`${property}=false`)),
      "Profile rollback Gradle properties must default to false.",
    ),
    check(
      "buildconfig-switches",
      ["NODEJS_PROFILE_PRO_ENABLED", "NODEJS_PROFILE_DESKTOP_ENABLED", "NODEJS_PROFILE_DEBUG_UNSAFE_LAB_ENABLED", "NODEJS_RELAXED_CAPABILITIES_ENABLED"].every((field) => gradleBlock.includes(field)),
      "BuildConfig must expose P13-51 rollback switch fields.",
    ),
    check(
      "safe-fallback-policy",
      kotlinBlock.includes('put("rollbackTargetProfile", SAFE_DEFAULT)') &&
        kotlinBlock.includes('put("crashFallbackProfile", SAFE_DEFAULT)') &&
        kotlinBlock.includes('put("stressFallbackProfile", SAFE_DEFAULT)') &&
        runtimeBlock.includes('rollbackTargetProfile: "safe_default"') &&
        runtimeBlock.includes('crashFallbackProfile: "safe_default"') &&
        runtimeBlock.includes('stressFallbackProfile: "safe_default"'),
      "Kotlin and runtime rollback policy must fall back to safe_default for rollback/crash/stress.",
    ),
    check(
      "remote-packaged-kill-switch-rejected",
      kotlinBlock.includes('put("remoteKillSwitchAvailable", false)') &&
        kotlinBlock.includes('put("remoteKillSwitchPolicy", "rejected_v1_1_publish_safe_profile_patch")') &&
        runtimeBlock.includes("remoteKillSwitchAvailable: false") &&
        runtimeBlock.includes('remoteKillSwitchPolicy: "rejected_v1_1_publish_safe_profile_patch"') &&
        files.rollbackPlaybook.text.includes("Remote packaged kill switches are explicitly rejected for v1.1"),
      "P13-51 must explicitly reject remote packaged kill switches or provide a policy.",
    ),
    check(
      "no-runtime-profile-promotion",
      forbiddenRuntimePromotions.every((value) => !runtimeBlock.includes(value)) &&
        !kotlinBlock.includes('put("profilePromotion", true)') &&
        !kotlinBlock.includes('put("grantsAuthority", true)'),
      "P13-51 must not promote profiles or grant relaxed authority.",
    ),
    check(
      "runtime-wiring-deferred-until-profile-promotion",
      files.p13Report.text.includes("Runtime profile promotion rollback wiring and automatic crash circuit breaker implementation are deferred until effective profile switching is promoted") &&
        files.rollbackPlaybook.text.includes("Runtime profile promotion rollback wiring and automatic crash circuit breaker implementation are deferred until effective profile switching is promoted") &&
        runtimeBlock.includes("profilePromotion: false") &&
        runtimeBlock.includes('rollbackTargetProfile: "safe_default"') &&
        kotlinBlock.includes('put("profilePromotion", false)') &&
        kotlinBlock.includes('put("rollbackTargetProfile", SAFE_DEFAULT)'),
      "P13-51 must defer runtime rollback wiring and automatic crash circuit breaker implementation until profile promotion exists.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-profile-rollback-playbook-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13ProfileRollbackPlaybook",
    task: "P13-51",
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
      phase13TaskComplete: finalBlockerClosable,
      finalBlockerClosable,
      gradleSwitchesReady: scenarioById(scenarios, "gradle_property_switches").status === "ready",
      kotlinPolicyReady: scenarioById(scenarios, "kotlin_policy").status !== "missing_evidence",
      runtimeDiagnosticsReady: scenarioById(scenarios, "runtime_and_doctor_diagnostics").status === "ready",
      typescriptSurfaceReady: scenarioById(scenarios, "typescript_surface").status === "ready",
      remotePackagedKillSwitchRejected: checkById(checks, "remote-packaged-kill-switch-rejected").passed,
      runtimeWiringDeferredUntilProfilePromotion: checkById(checks, "runtime-wiring-deferred-until-profile-promotion").passed,
      rollbackTargetProfile: "safe_default",
      profilePromotionPromoted: false,
      automaticCrashCircuitBreakerReady: false,
      remotePackagedKillSwitchReady: false,
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

function checkById(checks, id) {
  return checks.find((entry) => entry.id === id) || { passed: false };
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
    "# Phase 13 Profile Rollback Playbook",
    "",
    `- Task: ${report.task}`,
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Rollback target profile: \`${report.decision.rollbackTargetProfile}\``,
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
      console.log("Usage: node verify-profile-rollback-playbook.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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
