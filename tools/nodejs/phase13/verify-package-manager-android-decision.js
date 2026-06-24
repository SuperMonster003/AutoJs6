#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-package-manager-android-decision";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_42_PACKAGE_MANAGER_ANDROID.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  manager: "app/src/main/java/org/autojs/autojs/engine/NodeAndroidPackageInstallManager.kt",
  managerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeAndroidPackageInstallManagerInstrumentationTest.kt",
  p13PackageManagerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodePackageManagerPhase13DecisionInstrumentationTest.kt",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  p13AndroidManagerReport: "docs/nodejs/reports/phase13/P13_09_ANDROID_PACKAGE_INSTALL_MANAGER.md",
  p13AndroidManagerVerifier: "tools/nodejs/phase13/verify-android-package-install-manager.js",
  phase10PackageManager: "tools/nodejs/phase10/autojs6-pm.js",
  phase10PackageManagerReport: "docs/nodejs/reports/phase10/PACKAGE_MANAGER_BOUNDARY.md",
  designPackageManager: "docs/nodejs/design/PACKAGE_MANAGER.md",
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
  console.log("Phase 13 Android package manager decision: " + report.decision.status);
  console.log("Phase 13 Android package manager blockers: " + report.blockers.length);
  console.log("Phase 13 Android package manager policy violations: " + report.policyViolations.length);
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
  const modulesSurface = surface(files.surfaceManifest.text, "desktop.modules_npm_process");
  const packageManagerProfile = block(files.nodeBridgeSources.text, "packageManagerProfile: Object.freeze({", "      }),");
  const scenarios = [
    scenario("diagnostic_profile", "packageManagerProfile records the current Android package manager substrate and denied desktop npm boundaries", [
      token(files.nodeBridgeSources, "packageManagerProfile: Object.freeze({"),
      token(files.nodeBridgeSources, 'status: "android_substrate_partial"'),
      token(files.nodeBridgeSources, "defaultEnabled: false"),
      token(files.nodeBridgeSources, 'targetProfiles: Object.freeze(["desktop_compat_opt_in"])'),
      token(files.nodeBridgeSources, 'hostPackageManager: "available"'),
      token(files.nodeBridgeSources, 'androidStore: "local_app_private_partial"'),
      token(files.nodeBridgeSources, 'androidRuntimeInstall: "local_unpacked_only"'),
      token(files.nodeBridgeSources, 'npmCli: "denied"'),
      token(files.nodeBridgeSources, 'npmInstallEquivalent: "host_managed_plus_android_store_substrate"'),
      token(files.nodeBridgeSources, 'registryDownload: "deferred"'),
      token(files.nodeBridgeSources, 'lifecycleScripts: "denied"'),
      token(files.nodeBridgeSources, 'binLinks: "denied"'),
      token(files.nodeBridgeSources, 'symlinks: "denied"'),
      token(files.nodeBridgeSources, 'nativeBuild: "denied"'),
      token(files.nodeBridgeSources, 'postinstallDownload: "denied"'),
      token(files.nodeBridgeSources, 'rawRegistryAccess: false'),
      token(files.p13PackageManagerTest, "packageManagerProfileRecordsAndroidSubstrateAndDeniedBoundaries"),
      token(files.p13PackageManagerTest, "p13pm.profile=android_substrate_partial|false|desktop_compat_opt_in"),
      token(files.typesAutojsGlobal, "export interface PackageManagerProfile"),
      token(files.typesAutojsGlobal, "readonly packageManagerProfile: PackageManagerProfile"),
      token(files.typescriptSmoke, "autojsProfile.packageManagerProfile.androidRuntimeInstall"),
    ], []),
    scenario("android_local_store_substrate", "Android can transact on already-unpacked package directories with integrity and unsafe npm behavior denial", [
      token(files.manager, "class NodeAndroidPackageInstallManager"),
      token(files.manager, "fun install(request: PackageRequest"),
      token(files.manager, "fun update(request: PackageRequest"),
      token(files.manager, "fun remove(name: String, version: String"),
      token(files.manager, "fun list(): List<PackageRecord>"),
      token(files.manager, "fun verify(name: String, version: String)"),
      token(files.manager, "fun prune(maxTransactionAgeMs: Long = 0L)"),
      token(files.manager, '"android_package_manager.android_runtime_install" to "local_unpacked_only"'),
      token(files.manager, '"android_package_manager.npm_cli" to "disabled"'),
      token(files.manager, '"android_package_manager.registry_download" to "disabled"'),
      token(files.manager, '"android_package_manager.lockfile" to "autojs6-lock-host-managed"'),
      token(files.manager, '"android_package_manager.bin_links" to "disabled"'),
      token(files.manager, '"android_package_manager.symlinks" to "disabled"'),
      token(files.manager, '"android_package_manager.postinstall_download" to "disabled"'),
      token(files.manager, '"android_package_manager.js_bridge" to "not_exposed"'),
      token(files.manager, "DISABLED_LIFECYCLE_SCRIPTS"),
      token(files.manager, "ERROR_BIN_DISABLED"),
      token(files.manager, "ERROR_NATIVE_PAYLOAD_DISABLED"),
      token(files.manager, "Files.isSymbolicLink"),
      token(files.managerTest, "nativeValuesAdvertiseSafeAndroidPackageManagerBoundary"),
      token(files.p13PackageManagerTest, "androidStoreSubstrateInstallsLocalUnpackedPackageAndDeniesUnsafePolicies"),
      token(files.p13AndroidManagerReport, "`NodeAndroidPackageInstallManager` supports"),
      token(files.p13AndroidManagerVerifier, "verifyNodePhase13AndroidPackageInstallManager"),
    ], [
      "No Android registry downloader, npm CLI, package-manager JS facade, or node_modules mutation API is promoted yet.",
      "Signed packaged native payloads, native build execution, lifecycle debug mode, and bin shim strategy remain future work.",
    ]),
    scenario("host_managed_install_equivalent", "Host-side package manager remains the equivalent npm install path while Android runtime install stays constrained", [
      token(files.phase10PackageManager, 'const REPORT_BASENAME = "phase10-package-manager-boundary"'),
      token(files.phase10PackageManager, 'const LOCKFILE_NAME = "autojs6-lock.json"'),
      token(files.phase10PackageManager, "DISABLED_LIFECYCLE_SCRIPTS"),
      token(files.phase10PackageManager, "safeExtractTgz"),
      token(files.phase10PackageManager, "androidRuntimeInstall: false"),
      token(files.phase10PackageManager, "hostManaged: true"),
      token(files.phase10PackageManager, "binLinks: false"),
      token(files.phase10PackageManagerReport, "`autojs6-lock.json` generation"),
      token(files.phase10PackageManagerReport, "scripts, `.bin` execution, native addons"),
      token(files.designPackageManager, "partial Android local package store through `NodeAndroidPackageInstallManager`"),
    ], [
      "`npm i --no-bin-links` parity is represented by host-managed validation plus Android local-store substrate, not by running the npm CLI on device.",
      "Mobile registry resolution, cache management, tarball extraction, and integrity-lock authority are not Android runtime user APIs yet.",
    ]),
    scenario("docs_gate_and_matrix", "Docs, release matrix, surface manifest, and Gradle gate record P13-42", [
      token(files.phase13Tasks, "P13-42 partial landing"),
      token(files.p13Report, "P13-42 records Android package manager parity as partial"),
      token(files.phase13GradleGate, "verifyNodePhase13PackageManagerAndroidDecision"),
      token(files.testingDoc, "verifyNodePhase13PackageManagerAndroidDecision"),
      token(files.ciGatesDoc, "verifyNodePhase13PackageManagerAndroidDecision"),
      token(files.compatibilityProfile, "P13-42 records Android package manager parity as partial"),
      token(files.securityModel, "P13-42 records Android package manager parity as partial"),
      token(files.proComparison, "P13-42 records Android package manager parity as partial"),
      token(files.proAutoxNotes, "P13-42 records Android package manager parity as partial"),
      token(files.typesReadme, "P13-42 extends the `autojs6:profile` declaration"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.capabilityMatrix, "P13-42 Android package manager"),
    ], [
      "P13-42 remains partial until Android-side registry install, JS facade, lifecycle policy, signed native payloads, and packaged distribution evidence close.",
    ]),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-42"), "P13-42 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-42 report must exist."),
    check("surface-present", Boolean(modulesSurface), "`desktop.modules_npm_process` surface must exist."),
    check(
      "surface-evidence",
      modulesSurface && (modulesSurface.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`desktop.modules_npm_process` surface must reference P13-42 evidence.",
    ),
    check(
      "profile-fail-closed-boundaries",
      packageManagerProfile.includes('npmCli: "denied"') &&
        packageManagerProfile.includes('lifecycleScripts: "denied"') &&
        packageManagerProfile.includes('binLinks: "denied"') &&
        packageManagerProfile.includes('symlinks: "denied"') &&
        packageManagerProfile.includes('nativeBuild: "denied"') &&
        packageManagerProfile.includes('nativePayloads: "denied"') &&
        packageManagerProfile.includes('postinstallDownload: "denied"') &&
        packageManagerProfile.includes('jsBridge: "not_exposed"') &&
        packageManagerProfile.includes("rawExecutables: false") &&
        packageManagerProfile.includes("rawRegistryAccess: false"),
      "packageManagerProfile must keep npm CLI, lifecycle, bin/native/symlink, postinstall download, JS bridge, and raw registry access fail-closed.",
    ),
    check(
      "android-store-fail-closed-boundaries",
      files.manager.text.includes("ERROR_LIFECYCLE_SCRIPT_DISABLED") &&
        files.manager.text.includes("ERROR_BIN_DISABLED") &&
        files.manager.text.includes("ERROR_NATIVE_PAYLOAD_DISABLED") &&
        files.manager.text.includes("ERROR_PATH_ESCAPE") &&
        files.manager.text.includes("Files.isSymbolicLink") &&
        files.manager.text.includes('"android_package_manager.network_install" to "disabled"') &&
        files.manager.text.includes('"android_package_manager.js_bridge" to "not_exposed"'),
      "NodeAndroidPackageInstallManager must keep unsafe npm install behavior denied.",
    ),
    check(
      "no-js-facade-promotion",
      !files.typesAutojsGlobal.text.includes('declare module "package_manager"') &&
        !files.typesAutojsGlobal.text.includes('declare module "npm"') &&
        !files.nodeBridgeSources.text.includes("__autojs6_package_manager") &&
        !packageManagerProfile.includes('jsBridge: "partial"') &&
        !packageManagerProfile.includes('requirePackageManager: "partial"') &&
        !packageManagerProfile.includes("rawRegistryAccess: true"),
      "P13-42 must not introduce a package_manager/npm JS facade or raw registry authority.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-package-manager-android-decision-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13PackageManagerAndroidDecision",
    task: "P13-42",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: modulesSurface || {
      id: "desktop.modules_npm_process",
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
      androidLocalStoreReady: scenarioById(scenarios, "android_local_store_substrate").status !== "missing_evidence",
      hostManagedEquivalentReady: scenarioById(scenarios, "host_managed_install_equivalent").status !== "missing_evidence",
      npmCliPromoted: false,
      registryInstallPromoted: false,
      lifecycleScriptsPromoted: false,
      jsPackageManagerFacadePromoted: false,
      nativeBuildPromoted: false,
      binShimPromoted: false,
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
    "# Phase 13 Android Package Manager Decision",
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
      console.log("Usage: node verify-package-manager-android-decision.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
