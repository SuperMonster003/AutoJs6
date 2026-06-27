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
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  p13AndroidManagerReport: "docs/nodejs/reports/phase13/P13_09_ANDROID_PACKAGE_INSTALL_MANAGER.md",
  p13AndroidManagerVerifier: "tools/nodejs/phase13/verify-android-package-install-manager.js",
  phase10PackageManager: "tools/nodejs/phase10/autojs6-pm.js",
  phase10PackageManagerReport: "docs/nodejs/reports/phase10/PACKAGE_MANAGER_BOUNDARY.md",
  designPackageManager: "docs/nodejs/design/PACKAGE_MANAGER.md",
  typesAutojsGlobal: "docs/nodejs/types/autojs6-node/autojs_global.d.ts",
  typesPackageManager: "docs/nodejs/types/autojs6-node/package_manager.d.ts",
  typesNpm: "docs/nodejs/types/autojs6-node/npm.d.ts",
  typesIndex: "docs/nodejs/types/autojs6-node/index.d.ts",
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
    scenario("read_maintenance_profile", "packageManagerProfile records the current Android package manager substrate, guarded bridge, npm facade, and denied desktop npm CLI boundaries", [
      token(files.nodeBridgeSources, "packageManagerProfile: Object.freeze({"),
      token(files.nodeBridgeSources, 'status: "android_substrate_partial"'),
      token(files.nodeBridgeSources, "defaultEnabled: false"),
      token(files.nodeBridgeSources, 'targetProfiles: Object.freeze(["desktop_compat_opt_in"])'),
      token(files.nodeBridgeSources, 'hostPackageManager: "available"'),
      token(files.nodeBridgeSources, 'androidStore: "local_app_private_partial"'),
      token(files.nodeBridgeSources, 'androidRuntimeInstall: "local_unpacked_only"'),
      token(files.nodeBridgeSources, 'npmCli: "denied"'),
      token(files.nodeBridgeSources, 'npmFacade: "guarded_package_manager_alias"'),
      token(files.nodeBridgeSources, 'requireNpm: "guarded_package_manager_alias"'),
      token(files.nodeBridgeSources, 'npmInstallEquivalent: "host_managed_plus_android_store_substrate"'),
      token(files.nodeBridgeSources, 'registryDownload: "deferred"'),
      token(files.nodeBridgeSources, 'lifecycleScripts: "denied"'),
      token(files.nodeBridgeSources, 'binLinks: "denied"'),
      token(files.nodeBridgeSources, 'symlinks: "denied"'),
      token(files.nodeBridgeSources, 'nativeBuild: "denied"'),
      token(files.nodeBridgeSources, 'postinstallDownload: "denied"'),
      token(files.nodeBridgeSources, 'sourcePolicy: "guarded_local_unpacked_mutation"'),
      token(files.nodeBridgeSources, 'sourceAuthority: "local_unpacked_host_managed_only"'),
      token(files.nodeBridgeSources, 'installAuthority: "package_manager_mutate_explicit_local_unpacked"'),
      token(files.nodeBridgeSources, 'allowedSources: Object.freeze(["local_unpacked_host_managed"])'),
      token(files.nodeBridgeSources, 'planOperations: Object.freeze(["planInstall", "planUpdate", "planRemove", "planRegistryInstall", "planTarballInstall"])'),
      token(files.nodeBridgeSources, 'mutationOperations: Object.freeze(["install", "update", "remove"])'),
      token(files.nodeBridgeSources, 'npmFacadeOperations: Object.freeze(["status", "policy", "sourcePolicy", "packageManager", "planInstall", "planUpdate", "planRemove", "planRegistryInstall", "planTarballInstall", "install", "update", "remove", "list", "verify", "prune", "cli", "exec", "runScript"])'),
      token(files.nodeBridgeSources, 'mutationCapability: "package_manager.mutate"'),
      token(files.nodeBridgeSources, '"raw_registry",'),
      token(files.nodeBridgeSources, '"remote_tarball",'),
      token(files.nodeBridgeSources, 'jsBridge: "guarded_mutation_partial"'),
      token(files.nodeBridgeSources, 'requirePackageManager: "guarded_mutation_partial"'),
      token(files.nodeBridgeSources, 'diagnosticFacade: "available"'),
      token(files.nodeBridgeSources, 'rawRegistryAccess: false'),
      token(files.p13PackageManagerTest, "packageManagerProfileRecordsAndroidSubstrateAndDeniedBoundaries"),
      token(files.p13PackageManagerTest, "p13pm.profile=android_substrate_partial|false|desktop_compat_opt_in"),
      token(files.typesAutojsGlobal, "export interface PackageManagerProfile"),
      token(files.typesAutojsGlobal, "readonly packageManagerProfile: PackageManagerProfile"),
      token(files.typesAutojsGlobal, 'readonly npmFacade: "guarded_package_manager_alias" | string'),
      token(files.typescriptSmoke, "autojsProfile.packageManagerProfile.androidRuntimeInstall"),
      token(files.typescriptSmoke, "autojsProfile.packageManagerProfile.npmFacade"),
      token(files.typescriptSmoke, "packageManager.planRegistryInstall"),
      token(files.typescriptSmoke, "npm.planTarballInstall"),
    ], []),
    scenario("android_local_store_substrate", "Android can transact on already-unpacked package directories with integrity and unsafe npm behavior denial", [
      token(files.manager, "class NodeAndroidPackageInstallManager"),
      token(files.manager, "fun install(request: PackageRequest"),
      token(files.manager, "fun update(request: PackageRequest"),
      token(files.manager, "fun remove("),
      token(files.manager, "expectedIntegrity: String? = null"),
      token(files.manager, "fun planInstall(request: PackageRequest"),
      token(files.manager, "fun planUpdate(request: PackageRequest"),
      token(files.manager, "fun planRemove(name: String, version: String"),
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
      token(files.manager, '"android_package_manager.source_policy" to "guarded_local_unpacked_mutation"'),
      token(files.manager, '"android_package_manager.source_authority" to "local_unpacked_host_managed_only"'),
      token(files.manager, '"android_package_manager.allowed_sources" to "local_unpacked_host_managed"'),
      token(files.manager, '"android_package_manager.raw_registry_authority" to "disabled"'),
      token(files.manager, '"android_package_manager.remote_tarball_install" to "disabled"'),
      token(files.manager, '"android_package_manager.install_authority" to INSTALL_AUTHORITY'),
      token(files.manager, '"android_package_manager.mutation_capability" to "package_manager.mutate"'),
      token(files.manager, '"android_package_manager.js_bridge" to "guarded_mutation_partial"'),
      token(files.manager, "DISABLED_LIFECYCLE_SCRIPTS"),
      token(files.manager, "ERROR_BIN_DISABLED"),
      token(files.manager, "ERROR_NATIVE_PAYLOAD_DISABLED"),
      token(files.manager, "Files.isSymbolicLink"),
      token(files.managerTest, "nativeValuesAdvertiseSafeAndroidPackageManagerBoundary"),
      token(files.p13PackageManagerTest, "androidStoreSubstrateInstallsLocalUnpackedPackageAndDeniesUnsafePolicies"),
      token(files.p13PackageManagerTest, "packageManagerGuardedMutationFacadeInstallsUpdatesRemovesAndMaintainsLocalStore"),
      token(files.nodeBridgeSources, "function __autojs6_limited_package_manager()"),
      token(files.nodeBridgeSources, "function __autojs6_limited_npm()"),
      token(files.nodeBridgeSources, "function sourcePolicy()"),
      token(files.nodeBridgeSources, "function planInstall(request)"),
      token(files.nodeBridgeSources, "function planUpdate(request)"),
      token(files.nodeBridgeSources, "function planRemove(request)"),
      token(files.nodeBridgeSources, "function planRegistryInstall(request)"),
      token(files.nodeBridgeSources, "function planTarballInstall(request)"),
      token(files.nodeBridgeSources, "function __autojs6_package_manager_registry_resolution"),
      token(files.nodeBridgeSources, "function __autojs6_package_manager_registry_resolve_range"),
      token(files.nodeBridgeSources, "function __autojs6_package_manager_tarball_url_fields"),
      token(files.nodeBridgeSources, "autojs6-node-package-manager-registry-resolution-v1"),
      token(files.nodeBridgeSources, "autojs6-node-package-manager-tarball-resolution-v1"),
      token(files.nodeBridgeSources, "resolved_from_dist_tag"),
      token(files.nodeBridgeSources, "resolved_from_range"),
      token(files.nodeBridgeSources, "expected_integrity_mismatch"),
      token(files.nodeBridgeSources, "expected_integrity_unsupported_algorithm"),
      token(files.nodeBridgeSources, "inferredVersion"),
      token(files.nodeBridgeSources, "function install(request)"),
      token(files.nodeBridgeSources, "function update(request)"),
      token(files.nodeBridgeSources, "function remove(request)"),
      token(files.nodeBridgeSources, "autojs6-node-package-manager-source-policy-v1"),
      token(files.nodeBridgeSources, "autojs6-node-android-package-plan-v1"),
      token(files.nodeBridgeSources, "autojs6-node-package-manager-remote-plan-v1"),
      token(files.nodeBridgeSources, 'status: "blocked_by_policy"'),
      token(files.nodeBridgeSources, 'sourceAuthority: sourceKind === "raw_registry" ? "raw_registry_denied" : "remote_tarball_denied"'),
      token(files.nodeBridgeSources, "local_unpacked_host_managed"),
      token(files.nodeBridgeSources, "unverified_cache_denied"),
      token(files.nodeBridgeSources, "ERR_AUTOJS6_PACKAGE_MANAGER_MUTATION_NOT_CONFIRMED"),
      token(files.nodeBridgeSources, "ERR_AUTOJS6_NPM_CLI_DENIED"),
      token(files.nodeBridgeSources, "ERR_AUTOJS6_NPM_REGISTRY_DENIED"),
      token(files.typesPackageManager, 'declare module "package_manager"'),
      token(files.typesNpm, 'declare module "npm"'),
      token(files.typesNpm, "export interface NpmFacadeModule"),
      token(files.typesPackageManager, "export interface SourcePolicy"),
      token(files.typesPackageManager, "export interface RemotePackagePlan"),
      token(files.typesPackageManager, "export interface RegistryPackument"),
      token(files.typesPackageManager, "export interface RemotePlanResolution"),
      token(files.typesPackageManager, "export type RemotePlanIntegrityStatus"),
      token(files.typesPackageManager, "export type RemotePlanUrlStatus"),
      token(files.typesPackageManager, "sourcePolicy(): SourcePolicy"),
      token(files.typesPackageManager, "planInstall(request: PlanSourceRequest): Promise<PackagePlan>"),
      token(files.typesPackageManager, "planRegistryInstall(request: PlanRegistryInstallRequest): Promise<RemotePackagePlan>"),
      token(files.typesPackageManager, "planTarballInstall(request: PlanTarballInstallRequest): Promise<RemotePackagePlan>"),
      token(files.typesPackageManager, "install(request: MutationSourceRequest): Promise<OperationResult>"),
      token(files.typesNpm, "planRegistryInstall(request: PlanRegistryInstallRequest): Promise<RemotePackagePlan>"),
      token(files.typesPackageManager, "list(options?: ListOptions): Promise<readonly PackageRecord[]>"),
      token(files.typesPackageManager, "verify(request: VerifyRequest): Promise<OperationResult>"),
      token(files.typesIndex, './package_manager.d.ts'),
      token(files.typesIndex, './npm.d.ts'),
      token(files.typescriptSmoke, "packageManager.RegistryPackument"),
      token(files.typescriptSmoke, "packageManagerRangeRegistryRequest"),
      token(files.typescriptSmoke, "packageManagerTarballRequest"),
      token(files.p13PackageManagerTest, "metadataRegistryPlan.resolution.status"),
      token(files.p13PackageManagerTest, "rangeRegistryPlan.resolution.status"),
      token(files.p13PackageManagerTest, "expected_integrity_mismatch"),
      token(files.p13PackageManagerTest, "tarballMismatchPlan.resolution.integrityStatus"),
      token(files.conformanceTest, "metadataRegistryPlanResult.resolution.status"),
      token(files.conformanceTest, "rangeRegistryPlanResult.resolution.status"),
      token(files.conformanceTest, "npmTarballPlanResult.resolution.inferredName"),
      token(files.p13AndroidManagerReport, "`NodeAndroidPackageInstallManager` supports"),
      token(files.p13AndroidManagerVerifier, "verifyNodePhase13AndroidPackageInstallManager"),
    ], [
      "No Android registry downloader, npm CLI, lifecycle/bin/native execution, or raw node_modules mutation API is promoted yet.",
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
      "P13-42 remains partial until Android-side registry install, unrestricted install authority, lifecycle policy, signed native payloads, and packaged distribution evidence close.",
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
        packageManagerProfile.includes('npmFacade: "guarded_package_manager_alias"') &&
        packageManagerProfile.includes('requireNpm: "guarded_package_manager_alias"') &&
        packageManagerProfile.includes('lifecycleScripts: "denied"') &&
        packageManagerProfile.includes('binLinks: "denied"') &&
        packageManagerProfile.includes('symlinks: "denied"') &&
        packageManagerProfile.includes('nativeBuild: "denied"') &&
        packageManagerProfile.includes('nativePayloads: "denied"') &&
        packageManagerProfile.includes('postinstallDownload: "denied"') &&
        packageManagerProfile.includes('sourcePolicy: "guarded_local_unpacked_mutation"') &&
        packageManagerProfile.includes('sourceAuthority: "local_unpacked_host_managed_only"') &&
        packageManagerProfile.includes('installAuthority: "package_manager_mutate_explicit_local_unpacked"') &&
        packageManagerProfile.includes('allowedSources: Object.freeze(["local_unpacked_host_managed"])') &&
        packageManagerProfile.includes('planOperations: Object.freeze(["planInstall", "planUpdate", "planRemove", "planRegistryInstall", "planTarballInstall"])') &&
        packageManagerProfile.includes('mutationOperations: Object.freeze(["install", "update", "remove"])') &&
        packageManagerProfile.includes('npmFacadeOperations: Object.freeze(["status", "policy", "sourcePolicy", "packageManager", "planInstall", "planUpdate", "planRemove", "planRegistryInstall", "planTarballInstall", "install", "update", "remove", "list", "verify", "prune", "cli", "exec", "runScript"])') &&
        packageManagerProfile.includes('mutationCapability: "package_manager.mutate"') &&
        packageManagerProfile.includes('jsBridge: "guarded_mutation_partial"') &&
        packageManagerProfile.includes('requirePackageManager: "guarded_mutation_partial"') &&
        !packageManagerProfile.includes('jsBridge: "partial"') &&
        !packageManagerProfile.includes('requirePackageManager: "partial"') &&
        packageManagerProfile.includes("rawExecutables: false") &&
        packageManagerProfile.includes("rawRegistryAccess: false"),
      "packageManagerProfile must keep npm CLI, lifecycle, bin/native/symlink, postinstall download, raw registry access, and unrestricted install authority fail-closed.",
    ),
    check(
      "android-store-fail-closed-boundaries",
      files.manager.text.includes("ERROR_LIFECYCLE_SCRIPT_DISABLED") &&
        files.manager.text.includes("ERROR_BIN_DISABLED") &&
        files.manager.text.includes("ERROR_NATIVE_PAYLOAD_DISABLED") &&
        files.manager.text.includes("ERROR_PATH_ESCAPE") &&
        files.manager.text.includes("Files.isSymbolicLink") &&
        files.manager.text.includes('"android_package_manager.network_install" to "disabled"') &&
        files.manager.text.includes('"android_package_manager.source_authority" to "local_unpacked_host_managed_only"') &&
        files.manager.text.includes('"android_package_manager.raw_registry_authority" to "disabled"') &&
        files.manager.text.includes('"android_package_manager.remote_tarball_install" to "disabled"') &&
        files.manager.text.includes('"android_package_manager.js_bridge" to "guarded_mutation_partial"') &&
        files.manager.text.includes('"android_package_manager.mutation_capability" to "package_manager.mutate"'),
      "NodeAndroidPackageInstallManager must keep unsafe npm install behavior denied.",
    ),
    check(
      "guarded-mutation-js-facade-fail-closed",
      files.nodeBridgeSources.text.includes("function __autojs6_limited_package_manager()") &&
        files.nodeBridgeSources.text.includes("function __autojs6_limited_npm()") &&
        files.nodeBridgeSources.text.includes("ERR_AUTOJS6_PACKAGE_MANAGER_MUTATION_NOT_CONFIRMED") &&
        files.nodeBridgeSources.text.includes("ERR_AUTOJS6_NPM_CLI_DENIED") &&
        files.nodeBridgeSources.text.includes("ERR_AUTOJS6_NPM_REGISTRY_DENIED") &&
        files.nodeBridgeSources.text.includes("function sourcePolicy()") &&
        files.nodeBridgeSources.text.includes("function planInstall(request)") &&
        files.nodeBridgeSources.text.includes("function planUpdate(request)") &&
        files.nodeBridgeSources.text.includes("function planRemove(request)") &&
        files.nodeBridgeSources.text.includes("function planRegistryInstall(request)") &&
        files.nodeBridgeSources.text.includes("function planTarballInstall(request)") &&
        files.nodeBridgeSources.text.includes("function __autojs6_package_manager_registry_resolution") &&
        files.nodeBridgeSources.text.includes("function __autojs6_package_manager_registry_resolve_range") &&
        files.nodeBridgeSources.text.includes("function __autojs6_package_manager_tarball_url_fields") &&
        files.nodeBridgeSources.text.includes("autojs6-node-package-manager-registry-resolution-v1") &&
        files.nodeBridgeSources.text.includes("autojs6-node-package-manager-tarball-resolution-v1") &&
        files.nodeBridgeSources.text.includes("resolved_from_dist_tag") &&
        files.nodeBridgeSources.text.includes("resolved_from_range") &&
        files.nodeBridgeSources.text.includes("expected_integrity_mismatch") &&
        files.nodeBridgeSources.text.includes("expected_integrity_unsupported_algorithm") &&
        files.nodeBridgeSources.text.includes("inferredVersion") &&
        files.nodeBridgeSources.text.includes("function install(request)") &&
        files.nodeBridgeSources.text.includes('permissions: ["package_manager", "package_manager.mutate"]') &&
        files.nodeBridgeSources.text.includes("autojs6-node-package-manager-source-policy-v1") &&
        files.nodeBridgeSources.text.includes("autojs6-node-android-package-plan-v1") &&
        files.nodeBridgeSources.text.includes("autojs6-node-package-manager-remote-plan-v1") &&
        files.nodeBridgeSources.text.includes('status: "blocked_by_policy"') &&
        files.nodeBridgeSources.text.includes('npmCli: "denied"') &&
        files.nodeBridgeSources.text.includes('registryDownload: "deferred"') &&
        files.nodeBridgeSources.text.includes('permissions: ["package_manager"]') &&
        files.typesPackageManager.text.includes('declare module "package_manager"') &&
        files.typesPackageManager.text.includes("export interface RegistryPackument") &&
        files.typesPackageManager.text.includes("export interface RemotePlanResolution") &&
        files.typesPackageManager.text.includes("export type RemotePlanIntegrityStatus") &&
        files.typesPackageManager.text.includes("export type RemotePlanUrlStatus") &&
        files.typesPackageManager.text.includes("sourcePolicy(): SourcePolicy") &&
        files.typesPackageManager.text.includes("planInstall(request: PlanSourceRequest): Promise<PackagePlan>") &&
        files.typesPackageManager.text.includes("planRegistryInstall(request: PlanRegistryInstallRequest): Promise<RemotePackagePlan>") &&
        files.typesPackageManager.text.includes("planTarballInstall(request: PlanTarballInstallRequest): Promise<RemotePackagePlan>") &&
        files.typesPackageManager.text.includes("install(request: MutationSourceRequest): Promise<OperationResult>") &&
        files.typesPackageManager.text.includes("list(options?: ListOptions): Promise<readonly PackageRecord[]>") &&
        files.typesPackageManager.text.includes("verify(request: VerifyRequest): Promise<OperationResult>") &&
        files.typesNpm.text.includes('declare module "npm"') &&
        files.typesNpm.text.includes("status(): NpmFacadeStatus") &&
        files.typesNpm.text.includes("export type RegistryPackument = packageManager.RegistryPackument") &&
        files.typesNpm.text.includes("export type RemotePlanIntegrityStatus = packageManager.RemotePlanIntegrityStatus") &&
        files.typesNpm.text.includes("export type RemotePlanUrlStatus = packageManager.RemotePlanUrlStatus") &&
        files.typesNpm.text.includes("planRegistryInstall(request: PlanRegistryInstallRequest): Promise<RemotePackagePlan>") &&
        files.typesNpm.text.includes("cli(...args: readonly unknown[]): never") &&
        files.typescriptSmoke.text.includes("packageManager.RegistryPackument") &&
        files.typescriptSmoke.text.includes("packageManagerRangeRegistryRequest") &&
        files.typescriptSmoke.text.includes("packageManagerTarballRequest") &&
        files.p13PackageManagerTest.text.includes("metadataRegistryPlan.resolution.status") &&
        files.p13PackageManagerTest.text.includes("rangeRegistryPlan.resolution.status") &&
        files.p13PackageManagerTest.text.includes("expected_integrity_mismatch") &&
        files.p13PackageManagerTest.text.includes("tarballMismatchPlan.resolution.integrityStatus") &&
        files.conformanceTest.text.includes("metadataRegistryPlanResult.resolution.status") &&
        files.conformanceTest.text.includes("rangeRegistryPlanResult.resolution.status") &&
        files.conformanceTest.text.includes("npmTarballPlanResult.resolution.inferredName") &&
        files.typesIndex.text.includes('./npm.d.ts') &&
        !packageManagerProfile.includes('jsBridge: "partial"') &&
        !packageManagerProfile.includes('requirePackageManager: "partial"') &&
        !packageManagerProfile.includes("rawRegistryAccess: true"),
      "P13-42 may expose only guarded local package_manager mutation plus a guarded npm facade; npm CLI, registry install, full partial promotion, and raw registry authority must remain absent.",
    ),
    check(
      "host-managed-install-safe-default",
      packageManagerProfile.includes('npmInstallEquivalent: "host_managed_plus_android_store_substrate"') &&
        packageManagerProfile.includes('hostPackageManager: "available"') &&
        packageManagerProfile.includes('androidRuntimeInstall: "local_unpacked_only"') &&
        packageManagerProfile.includes('npmCli: "denied"') &&
        packageManagerProfile.includes('registryDownload: "deferred"') &&
        packageManagerProfile.includes('sourceAuthority: "local_unpacked_host_managed_only"') &&
        files.phase10PackageManager.text.includes("hostManaged: true") &&
        files.phase10PackageManager.text.includes("androidRuntimeInstall: false") &&
        files.phase10PackageManager.text.includes("binLinks: false") &&
        files.p13Report.text.includes("Phase 10 remains the host-managed package manager boundary") &&
        files.p13Report.text.includes("Android runtime installation remains") &&
        files.p13Report.text.includes("constrained to the local store substrate"),
      "P13-42 must keep host-managed install validation as the safe default until mobile install authority is reviewed.",
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
      diagnosticProfileReady: scenarioById(scenarios, "read_maintenance_profile").status === "ready",
      androidLocalStoreReady: scenarioById(scenarios, "android_local_store_substrate").status !== "missing_evidence",
      hostManagedEquivalentReady: scenarioById(scenarios, "host_managed_install_equivalent").status !== "missing_evidence",
      hostManagedInstallSafeDefault: checkById(checks, "host-managed-install-safe-default").passed,
      npmCliPromoted: false,
      npmFacadeReady: files.nodeBridgeSources.text.includes("function __autojs6_limited_npm()") &&
        files.typesNpm.text.includes('declare module "npm"'),
      registryInstallPromoted: false,
      registryTarballPlanReady: files.nodeBridgeSources.text.includes("autojs6-node-package-manager-remote-plan-v1") &&
        files.nodeBridgeSources.text.includes("autojs6-node-package-manager-registry-resolution-v1") &&
        files.nodeBridgeSources.text.includes("autojs6-node-package-manager-tarball-resolution-v1") &&
        files.nodeBridgeSources.text.includes("resolved_from_range") &&
        files.nodeBridgeSources.text.includes("expected_integrity_mismatch") &&
        files.nodeBridgeSources.text.includes("function __autojs6_package_manager_tarball_url_fields") &&
        files.nodeBridgeSources.text.includes("expected_integrity_unsupported_algorithm") &&
        files.nodeBridgeSources.text.includes("function planRegistryInstall(request)") &&
        files.nodeBridgeSources.text.includes("function planTarballInstall(request)"),
      lifecycleScriptsPromoted: false,
      jsPackageManagerDiagnosticFacadeReady: scenarioById(scenarios, "android_local_store_substrate").missingEvidence === 0,
      jsPackageManagerReadMaintenanceReady: scenarioById(scenarios, "android_local_store_substrate").missingEvidence === 0,
      sourceAuthorityPolicyReady: files.nodeBridgeSources.text.includes("autojs6-node-package-manager-source-policy-v1") &&
        files.nodeBridgeSources.text.includes("local_unpacked_host_managed_only") &&
        files.manager.text.includes('"android_package_manager.source_authority" to "local_unpacked_host_managed_only"'),
      jsPackageManagerFacadePromoted: false,
      jsNpmFacadePromoted: files.nodeBridgeSources.text.includes("function __autojs6_limited_npm()"),
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
    missingEvidence: missing.length,
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
