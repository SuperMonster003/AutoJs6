#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-package-lock-integrity-policy";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_48_PACKAGE_LOCK_INTEGRITY_POLICY.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  integrityPolicy: "app/src/main/java/org/autojs/autojs/engine/NodePackageIntegrityPolicy.kt",
  manager: "app/src/main/java/org/autojs/autojs/engine/NodeAndroidPackageInstallManager.kt",
  managerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeAndroidPackageInstallManagerInstrumentationTest.kt",
  p13IntegrityTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodePackageLockIntegrityPolicyPhase13DecisionInstrumentationTest.kt",
  p13PackageManagerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodePackageManagerPhase13DecisionInstrumentationTest.kt",
  p13NativeAddonReport: "docs/nodejs/reports/phase13/P13_39_NATIVE_ADDON_LOADING.md",
  pluginSystemTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodePluginSystemInstrumentationTest.kt",
  pluginDesign: "docs/nodejs/design/PLUGIN_SYSTEM.md",
  phase9PluginPolicy: "docs/nodejs/reports/phase9/PLUGIN_POLICY.md",
  packageManagerDesign: "docs/nodejs/design/PACKAGE_MANAGER.md",
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
  console.log("Phase 13 package lock/integrity policy: " + report.decision.status);
  console.log("Phase 13 package lock/integrity blockers: " + report.blockers.length);
  console.log("Phase 13 package lock/integrity policy violations: " + report.policyViolations.length);
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
  const profileBlock = block(files.nodeBridgeSources.text, "packageIntegrityProfile: Object.freeze({", "      filesystemProfile: Object.freeze({");
  const policyBlock = files.integrityPolicy.text;
  const scenarios = [
    scenario("unified_policy_descriptor", "Kotlin policy records lockfile, hash, signatures, rollback, cache cleanup, and corruption recovery for all package subjects", [
      token(files.integrityPolicy, "object NodePackageIntegrityPolicy"),
      token(files.integrityPolicy, 'SCHEMA = "autojs6-node-package-lock-integrity-policy-v1"'),
      token(files.integrityPolicy, 'STATUS = "lock_integrity_policy_partial"'),
      token(files.integrityPolicy, 'LOCKFILE = "autojs6-lock.json"'),
      token(files.integrityPolicy, 'ANDROID_RECORD_SCHEMA = "autojs6-node-android-package-record-v1"'),
      token(files.integrityPolicy, 'HASH_ALGORITHM = "sha512"'),
      token(files.integrityPolicy, 'HASH_FORMAT = "ssri_sha512_base64"'),
      token(files.integrityPolicy, 'SIGNATURE_SCHEME = "autojs6-package-signature-v1"'),
      token(files.integrityPolicy, 'id = "android_package_manager"'),
      token(files.integrityPolicy, 'id = "plugin_packages"'),
      token(files.integrityPolicy, 'id = "native_addons"'),
      token(files.integrityPolicy, 'restorePreviousOnFailure'),
      token(files.integrityPolicy, 'staleTransactionsPruned'),
      token(files.integrityPolicy, 'mismatchBlocksLoad'),
      token(files.integrityPolicy, 'reinstallRequired'),
      token(files.integrityPolicy, 'recoveryActions'),
      token(files.integrityPolicy, 'disable_unsigned_plugin_or_native_subject'),
    ]),
    scenario("runtime_profile_and_types", "Runtime diagnostics, TypeScript declarations, and smoke sample expose the same packageIntegrityProfile", [
      token(files.nodeBridgeSources, "packageIntegrityProfile: Object.freeze({"),
      token(files.nodeBridgeSources, 'status: "lock_integrity_policy_partial"'),
      token(files.nodeBridgeSources, 'policySchema: "autojs6-node-package-lock-integrity-policy-v1"'),
      token(files.nodeBridgeSources, 'pluginPackages: "local_checked_files_signed_archives_deferred"'),
      token(files.nodeBridgeSources, 'nativeAddons: "denied_until_signed_packaged_artifacts"'),
      token(files.nodeBridgeSources, 'signaturePolicy: "required_before_plugin_native_promotion"'),
      token(files.nodeBridgeSources, 'rollback: "staging_restore_previous_on_failure"'),
      token(files.nodeBridgeSources, 'cacheCleanup: "staging_prune_and_reinstall_required"'),
      token(files.nodeBridgeSources, 'corruptionRecovery: "verify_mismatch_reinstall_required"'),
      token(files.nodeBridgeSources, "recoveryActions: Object.freeze(["),
      token(files.nodeBridgeSources, "reinstall_from_verified_source"),
      token(files.nodeBridgeSources, "unsafePackageExecution: false"),
      token(files.nodeBridgeSources, "rawRegistryInstall: false"),
      token(files.nodeBridgeSources, "unsignedPluginInstall: false"),
      token(files.nodeBridgeSources, "unsignedNativeAddonLoad: false"),
      token(files.nodeBridgeSources, "unverifiedCacheReuse: false"),
      token(files.typesAutojsGlobal, "export interface PackageIntegrityProfile"),
      token(files.typesAutojsGlobal, "readonly recoveryActions: readonly string[]"),
      token(files.typesAutojsGlobal, "readonly packageIntegrityProfile: PackageIntegrityProfile"),
      token(files.typescriptSmoke, "autojsProfile.packageIntegrityProfile.hashAlgorithm"),
      token(files.typescriptSmoke, "autojsProfile.packageIntegrityProfile.recoveryActions"),
      token(files.typescriptSmoke, "autojsProfile.packageIntegrityProfile.unsignedNativeAddonLoad"),
      token(files.p13IntegrityTest, "packageIntegrityProfileRecordsUnifiedPolicyAndFailClosedBoundaries"),
      token(files.p13IntegrityTest, "p13integrity.profile=lock_integrity_policy_partial"),
    ]),
    scenario("android_store_integrity_recovery", "Android local store uses the policy diagnostics and proves rollback, prune, and corruption recovery behavior", [
      token(files.manager, '"package_integrity_policy.schema" to NodePackageIntegrityPolicy.SCHEMA'),
      token(files.manager, '"package_integrity_policy.status" to NodePackageIntegrityPolicy.STATUS'),
      token(files.manager, '"package_integrity_policy.hash" to NodePackageIntegrityPolicy.HASH_ALGORITHM'),
      token(files.manager, '"package_integrity_policy.signature" to "required_before_plugin_native_promotion"'),
      token(files.manager, '"package_integrity_policy.rollback" to "staging_restore_previous_on_failure"'),
      token(files.manager, '"package_integrity_policy.cache_cleanup" to "staging_prune_and_reinstall_required"'),
      token(files.manager, '"package_integrity_policy.corruption_recovery" to "verify_mismatch_reinstall_required"'),
      token(files.manager, '"package_integrity_policy.recovery_actions" to NodePackageIntegrityPolicy.recoveryActions().joinToString(",")'),
      token(files.manager, '"package_integrity_policy.subjects" to NodePackageIntegrityPolicy.subjectIds().joinToString(",")'),
      token(files.p13IntegrityTest, "androidStoreRollbackPruneAndCorruptionRecoveryMatchPolicy"),
      token(files.p13IntegrityTest, "ERROR_INTEGRITY_MISMATCH"),
      token(files.p13IntegrityTest, "STATUS_PRUNED"),
      token(files.managerTest, "installUpdateListVerifyRemoveAndPruneAreTransactionalAndIntegrityChecked"),
      token(files.p13PackageManagerTest, "androidStoreSubstrateInstallsLocalUnpackedPackageAndDeniesUnsafePolicies"),
    ]),
    scenario("plugin_native_fail_closed", "Plugin and native addon package paths inherit the policy without opening unsigned or native execution", [
      token(files.pluginDesign, "Future Package Format"),
      token(files.pluginDesign, '"signature"'),
      token(files.pluginDesign, "signed archive verifier"),
      token(files.phase9PluginPolicy, "package integrity metadata"),
      token(files.phase9PluginPolicy, "update/signature policy"),
      token(files.pluginSystemTest, "pluginNativeAddonFileIsRejected"),
      token(files.p13NativeAddonReport, "signed packaged native-addon manifest"),
      token(files.p13NativeAddonReport, "runtime download, npm build"),
      token(files.nodeBridgeSources, 'nativePlugins: "denied"'),
      token(files.nodeBridgeSources, 'signedArchives: "deferred"'),
      token(files.nodeBridgeSources, "downloadInstaller: false"),
      token(files.nodeBridgeSources, "nativeAddonLoading: false"),
      token(files.nodeBridgeSources, "marketplaceInstall: false"),
    ], [
      "Implement signed plugin archive extraction and update rollback.",
      "Implement marketplace cache pruning and user-facing corruption recovery.",
    ]),
    scenario("docs_gate_and_surface", "Docs, release matrix, surface manifest, and Gradle gate record P13-48", [
      token(files.phase13Tasks, "P13-48 partial landing"),
      token(files.p13Report, "P13-48 records package lock and integrity policy as partial"),
      token(files.phase13GradleGate, "verifyNodePhase13PackageLockIntegrityPolicy"),
      token(files.testingDoc, "verifyNodePhase13PackageLockIntegrityPolicy"),
      token(files.ciGatesDoc, "verifyNodePhase13PackageLockIntegrityPolicy"),
      token(files.compatibilityProfile, "P13-48 records package lock and integrity policy as partial"),
      token(files.securityModel, "P13-48 records package lock and integrity policy as partial"),
      token(files.proComparison, "P13-48 records package lock and integrity policy as partial"),
      token(files.proAutoxNotes, "P13-48 records package lock and integrity policy as partial"),
      token(files.typesReadme, "P13-48 extends the `autojs6:profile` declaration"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.capabilityMatrix, "P13-48 package lock and integrity policy"),
      token(files.packageManagerDesign, "P13-48 unifies the lock and integrity policy"),
    ]),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-48"), "P13-48 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-48 report must exist."),
    check("surface-present", Boolean(surfaceEntry), "`packaging.plugins_distribution` surface must exist."),
    check(
      "surface-evidence",
      surfaceEntry && (surfaceEntry.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`packaging.plugins_distribution` surface must reference P13-48 evidence.",
    ),
    check(
      "policy-subjects",
      policyBlock.includes('id = "android_package_manager"') &&
        policyBlock.includes('id = "plugin_packages"') &&
        policyBlock.includes('id = "native_addons"'),
      "policy descriptor must cover Android package manager, plugins, and native addons.",
    ),
    check(
      "integrity-policy-unified-across-subjects",
      files.p13Report.text.includes("unified policy descriptor for Android-side package manager") &&
        files.p13Report.text.includes("plugin packages, and native addon artifacts") &&
        files.packageManagerDesign.text.includes("P13-48 unifies the lock and integrity policy across the Android-side package") &&
        files.securityModel.text.includes("shared lockfile, SHA-512 integrity, signature, rollback, cache cleanup, corruption recovery requirements") &&
        policyBlock.includes('id = "android_package_manager"') &&
        policyBlock.includes('id = "plugin_packages"') &&
        policyBlock.includes('id = "native_addons"'),
      "P13-48 must unify integrity policy across package manager, plugins, and native payloads.",
    ),
    check(
      "profile-fail-closed-boundaries",
      profileBlock.includes("unsafePackageExecution: false") &&
        profileBlock.includes("rawRegistryInstall: false") &&
        profileBlock.includes("unsignedPluginInstall: false") &&
        profileBlock.includes("unsignedNativeAddonLoad: false") &&
        profileBlock.includes("lifecycleScripts: false") &&
        profileBlock.includes("postinstallDownloads: false") &&
        profileBlock.includes("unverifiedCacheReuse: false"),
      "packageIntegrityProfile must keep unsafe execution, raw registry install, unsigned plugin/native install, lifecycle scripts, postinstall downloads, and unverified cache reuse fail-closed.",
    ),
    check(
      "no-unsafe-package-integrity-promotion",
      !profileBlock.includes("unsafePackageExecution: true") &&
        !profileBlock.includes("rawRegistryInstall: true") &&
        !profileBlock.includes("unsignedPluginInstall: true") &&
        !profileBlock.includes("unsignedNativeAddonLoad: true") &&
        !profileBlock.includes("lifecycleScripts: true") &&
        !profileBlock.includes("postinstallDownloads: true") &&
        !profileBlock.includes("unverifiedCacheReuse: true") &&
        !files.nodeBridgeSources.text.includes("signedNativeAddonLoader: true"),
      "P13-48 must not promote unsafe package execution, unsigned installs, lifecycle/postinstall execution, unverified cache reuse, or signed native addon loading.",
    ),
    check(
      "native-addon-verification-delegated-to-p13-39",
      files.p13Report.text.includes("Signed native addon artifact verification is delegated to P13-39") &&
        files.p13NativeAddonReport.text.includes("For P13-39 v1.1, native addon artifacts stay packaged-only future inputs") &&
        files.p13NativeAddonReport.text.includes("signed packaged native-addon manifest") &&
        profileBlock.includes('nativeAddons: "denied_until_signed_packaged_artifacts"') &&
        profileBlock.includes("unsignedNativeAddonLoad: false"),
      "P13-48 must delegate signed native addon artifact verification to the P13-39 packaged native path while keeping native addon loading fail-closed.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-package-lock-integrity-policy-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13PackageLockIntegrityPolicy",
    task: "P13-48",
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
      diagnosticProfileReady: scenarioById(scenarios, "runtime_profile_and_types").status === "ready",
      androidStorePolicyReady: scenarioById(scenarios, "android_store_integrity_recovery").status === "ready",
      integrityPolicyUnifiedAcrossSubjects: checkById(checks, "integrity-policy-unified-across-subjects").passed,
      pluginNativePolicyReady: scenarioById(scenarios, "plugin_native_fail_closed").status !== "missing_evidence",
      nativeAddonVerificationDelegatedToP13_39: checkById(checks, "native-addon-verification-delegated-to-p13-39").passed,
      unsignedPluginInstallPromoted: false,
      unsignedNativeAddonLoadPromoted: false,
      rawRegistryInstallPromoted: false,
      unverifiedCacheReusePromoted: false,
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
    "# Phase 13 Package Lock And Integrity Policy",
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
      console.log("Usage: node verify-package-lock-integrity-policy.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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
