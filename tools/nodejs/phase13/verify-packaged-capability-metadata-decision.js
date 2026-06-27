#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-packaged-capability-metadata-decision";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_45_PACKAGED_CAPABILITY_METADATA.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  bridgeManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  projectRuntimeDescriptor: "app/src/main/java/org/autojs/autojs/script/NodeProjectRuntimeCompatibilityDescriptor.kt",
  projectLaunchDescriptor: "app/src/main/java/org/autojs/autojs/script/NodeProjectLaunchDescriptor.kt",
  apkBuilder: "app/src/main/java/org/autojs/autojs/apkbuilder/ApkBuilder.kt",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  p13PackagedTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodePackagedCapabilityMetadataPhase13DecisionInstrumentationTest.kt",
  bridgeManifestTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgePermissionManifestInstrumentationTest.kt",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  workManagerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt",
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

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const report = buildReport(files);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 13 packaged capability metadata decision: " + report.decision.status);
  console.log("Phase 13 packaged capability metadata blockers: " + report.blockers.length);
  console.log("Phase 13 packaged capability metadata policy violations: " + report.policyViolations.length);
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
  const surfaceEntry = surface(files.surfaceManifest.text, "packaging.plugins_distribution");
  const profileBlock = block(files.nodeBridgeSources.text, "packagedCapabilityProfile: Object.freeze({", "      filesystemProfile: Object.freeze({");
  const scenarios = [
    scenario("diagnostic_profile", "packagedCapabilityProfile records metadata-only packaged APK capability boundary", [
      token(files.nodeBridgeSources, "packagedCapabilityProfile: Object.freeze({"),
      token(files.nodeBridgeSources, 'status: "packaged_metadata_partial"'),
      token(files.nodeBridgeSources, 'metadataSchema: "autojs6-packaged-capability-metadata-v1"'),
      token(files.nodeBridgeSources, 'permissionManifest: "node_permissions_enforced"'),
      token(files.nodeBridgeSources, 'androidManifestSynthesis: "bridge_android_permissions_only"'),
      token(files.nodeBridgeSources, 'profileMetadata: "diagnostic_only"'),
      token(files.nodeBridgeSources, 'nodeBuiltins: "declared_metadata_only_no_authority"'),
      token(files.nodeBridgeSources, 'nativeAssets: "declared_metadata_only_denied"'),
      token(files.nodeBridgeSources, 'filesystemRoots: "declared_metadata_only_scoped_default"'),
      token(files.nodeBridgeSources, 'network: "network_capability_maps_android_internet"'),
      token(files.nodeBridgeSources, 'longRunning: "execution_mode_metadata_partial"'),
      token(files.nodeBridgeSources, 'undeclaredCapabilities: "rejected_before_dispatch"'),
      token(files.nodeBridgeSources, "rawProfilePromotion: false"),
      token(files.nodeBridgeSources, "rawBuiltinPromotion: false"),
      token(files.nodeBridgeSources, "nativeAssetLoading: false"),
      token(files.nodeBridgeSources, "expandedFilesystemRoots: false"),
      token(files.nodeBridgeSources, "unreviewedAndroidPermissions: false"),
      token(files.typesAutojsGlobal, "export interface PackagedCapabilityProfile"),
      token(files.typesAutojsGlobal, "export interface PackagedCapabilityMetadata"),
      token(files.typesAutojsGlobal, "readonly packagedCapabilityProfile: PackagedCapabilityProfile"),
      token(files.typescriptSmoke, "autojsProfile.packagedCapabilityProfile.metadataSources"),
      token(files.typescriptSmoke, "autojsProfile.bridgePermissions.packagedMetadata.network.requested"),
    ]),
    scenario("manifest_metadata_and_runtime_denial", "Kotlin/JS manifest metadata is exposed as read-only evidence without granting undeclared authority", [
      token(files.bridgeManifest, "data class PackagedMetadata"),
      token(files.bridgeManifest, "collectPackagedMetadata"),
      token(files.bridgeManifest, "packagedMetadata = packagedMetadata.build(normalizedPermissions)"),
      token(files.bridgeManifest, "warnings = (warnings + packagedMetadata.warnings"),
      token(files.bridgeManifest, 'PACKAGED_METADATA_SCHEMA = "autojs6-packaged-capability-metadata-v1"'),
      token(files.nodeBridgeSources, "__autojs6_bridge_freeze_packaged_metadata"),
      token(files.nodeBridgeSources, "packagedMetadata: manifest.packagedMetadata"),
      token(files.p13PackagedTest, "packagedCapabilityMetadataRecordsProfilesPermissionsAndStableDenials"),
      token(files.p13PackagedTest, "packagedCapabilityMetadataWarningsReachRuntimeReports"),
      token(files.p13PackagedTest, "p13packaged.profile=packaged_metadata_partial"),
      token(files.p13PackagedTest, "p13packaged.metadata=autojs6-packaged-capability-metadata-v1|partial"),
      token(files.p13PackagedTest, "p13packaged.warnings=6|6|true|true|false"),
      token(files.p13PackagedTest, "p13packaged.denial=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|accessibility|0"),
      token(files.p13PackagedTest, 'assertEquals(listOf("network"), manifest.permissions)'),
      token(files.p13PackagedTest, "manifest.packagedMetadata.network.declaredCapability"),
      token(files.bridgeManifestTest, "packagedMetadataWarningsAreMirroredIntoManifestAndMetadata"),
      token(files.bridgeManifestTest, "packagedManifestMismatchUsesCapabilityManifestPermissions"),
      token(files.apkBuilder, "applyNodeBridgeAndroidManifestPermissions"),
      token(files.apkBuilder, "NodeBridgePermissionManifest.androidPermissionsForWorkingDirectory"),
      token(files.projectRuntimeDescriptor, "CAPABILITY_MANIFEST_VERSION"),
      token(files.projectRuntimeDescriptor, "currentJson("),
      token(files.projectLaunchDescriptor, "DESCRIPTOR_FILE_NAME = \".autojs6-node-launch.json\""),
      token(files.workManagerTest, "jsWorkManagerModuleUsesFakeTransportAndSerializesPackagedMetadata"),
    ], [
      "Packaged profile, builtin, native asset, and filesystem-root metadata are diagnostic-only in P13-45.",
      "Full packaged install/run synthesis, disclosure UX, and profile-specific runtime promotion remain future work.",
    ]),
    scenario("docs_gate_and_surface", "Docs, release matrix, surface manifest, and Gradle gate record P13-45", [
      token(files.phase13Tasks, "P13-45 partial landing"),
      token(files.p13Report, "P13-45 records packaged APK capability metadata as partial"),
      token(files.phase13GradleGate, "verifyNodePhase13PackagedCapabilityMetadataDecision"),
      token(files.testingDoc, "verifyNodePhase13PackagedCapabilityMetadataDecision"),
      token(files.ciGatesDoc, "verifyNodePhase13PackagedCapabilityMetadataDecision"),
      token(files.compatibilityProfile, "P13-45 records packaged APK capability metadata as partial"),
      token(files.securityModel, "P13-45 records packaged APK capability metadata as partial"),
      token(files.proComparison, "P13-45 records packaged APK capability metadata as partial"),
      token(files.proAutoxNotes, "P13-45 records packaged APK capability metadata as partial"),
      token(files.typesReadme, "P13-45 extends the `autojs6:profile` declaration"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.capabilityMatrix, "P13-45 packaged APK capability metadata"),
    ], [
      "P13-45 remains partial until profile/builtin/native/fs metadata can drive reviewed packaged synthesis and install-run evidence.",
    ]),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-45"), "P13-45 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-45 report must exist."),
    check("surface-present", Boolean(surfaceEntry), "`packaging.plugins_distribution` surface must exist."),
    check(
      "surface-evidence",
      surfaceEntry && (surfaceEntry.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`packaging.plugins_distribution` surface must reference P13-45 evidence.",
    ),
    check(
      "profile-fail-closed",
      profileBlock.includes("rawProfilePromotion: false") &&
        profileBlock.includes("rawBuiltinPromotion: false") &&
        profileBlock.includes("nativeAssetLoading: false") &&
        profileBlock.includes("expandedFilesystemRoots: false") &&
        profileBlock.includes("unreviewedAndroidPermissions: false"),
      "packagedCapabilityProfile must keep raw profile, builtin, native asset, fs-root, and Android permission promotion fail-closed.",
    ),
    check(
      "metadata-does-not-grant-authority",
      files.bridgeManifest.text.includes("metadataOnly = true") &&
        files.bridgeManifest.text.includes("grantsAuthority = false"),
      "packaged metadata must be diagnostic-only and must not grant authority.",
    ),
    check(
      "no-unsafe-packaged-promotion",
      !files.nodeBridgeSources.text.includes("rawProfilePromotion: true") &&
        !files.nodeBridgeSources.text.includes("rawBuiltinPromotion: true") &&
        !files.nodeBridgeSources.text.includes("nativeAssetLoading: true") &&
        !files.nodeBridgeSources.text.includes("expandedFilesystemRoots: true") &&
        !files.bridgeManifest.text.includes("grantsAuthority = true"),
      "P13-45 must not mark packaged profile, builtin, native asset, fs-root, or authority grants as promoted.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-packaged-capability-metadata-decision-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13PackagedCapabilityMetadataDecision",
    task: "P13-45",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: surfaceEntry || {
      id: "packaging.plugins_distribution",
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
      diagnosticProfileReady: scenarioById(scenarios, "diagnostic_profile").status === "ready",
      manifestMetadataReady: scenarioById(scenarios, "manifest_metadata_and_runtime_denial").status !== "missing_evidence",
      rawPackagedPromotion: false,
      nativeAssetsPromoted: false,
      expandedFilesystemRootsPromoted: false,
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
    "# Phase 13 Packaged Capability Metadata Decision",
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
  lines.push("");
  return lines.join("\n");
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8");
  } catch (_) {
    return "";
  }
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      options.repoRoot = args[index + 1];
      index += 1;
    } else if (arg === "--report-dir") {
      options.reportDir = args[index + 1];
      index += 1;
    } else if (arg === "--fail-on-blocked") {
      options.failOnBlocked = true;
    } else if (arg === "-h" || arg === "--help") {
      console.log("Usage: node verify-packaged-capability-metadata-decision.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
