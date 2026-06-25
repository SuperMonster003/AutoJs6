package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

class NodePackageManagerPhase13DecisionInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext
    private lateinit var root: File
    private lateinit var store: File
    private lateinit var sources: File
    private lateinit var manager: NodeAndroidPackageInstallManager

    @Before
    fun setUp() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        root = File(context.cacheDir, "node-package-manager-p13-42-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create P13-42 package manager root: $absolutePath" }
        }
        store = File(root, "store")
        sources = File(root, "sources").apply { mkdirs() }
        manager = NodeAndroidPackageInstallManager(store) { 1_700_000_042_000L }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun packageManagerProfileRecordsAndroidSubstrateAndDeniedBoundaries() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-42 Android package manager decision")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                const profile = require("autojs6:profile");
                const packageManagerProfile = profile.packageManagerProfile || {};

                function loads(specifier) {
                  try {
                    require(specifier);
                    return true;
                  } catch (_) {
                    return false;
                  }
                }

                console.log("p13pm.profile=" + [
                  packageManagerProfile.status,
                  packageManagerProfile.defaultEnabled,
                  Array.isArray(packageManagerProfile.targetProfiles) && packageManagerProfile.targetProfiles.join(","),
                  packageManagerProfile.hostPackageManager,
                  packageManagerProfile.androidStore,
                  packageManagerProfile.androidRuntimeInstall,
                  packageManagerProfile.npmCli,
                  packageManagerProfile.npmInstallEquivalent,
                  packageManagerProfile.registryDownload,
                  packageManagerProfile.tarballInstall,
                  packageManagerProfile.lockfile,
                  packageManagerProfile.integrity,
                  packageManagerProfile.lifecycleScripts,
                  packageManagerProfile.debugLifecycleScripts,
                  packageManagerProfile.binLinks,
                  packageManagerProfile.symlinks,
                  packageManagerProfile.nativeBuild,
                  packageManagerProfile.nativePayloads,
                  packageManagerProfile.postinstallDownload,
                  packageManagerProfile.nodeModulesMutation,
                  packageManagerProfile.transaction,
                  packageManagerProfile.sourcePolicy,
                  packageManagerProfile.sourceAuthority,
                  packageManagerProfile.installAuthority,
                  Array.isArray(packageManagerProfile.allowedSources) && packageManagerProfile.allowedSources.join(","),
                  Array.isArray(packageManagerProfile.deniedSources) && packageManagerProfile.deniedSources.join(","),
                  Array.isArray(packageManagerProfile.planOperations) && packageManagerProfile.planOperations.join(","),
                  Array.isArray(packageManagerProfile.mutationOperations) && packageManagerProfile.mutationOperations.join(","),
                  packageManagerProfile.mutationCapability,
                  packageManagerProfile.mutationConfirmation,
                  Array.isArray(packageManagerProfile.operations) && packageManagerProfile.operations.join(","),
                  Array.isArray(packageManagerProfile.packageKinds) && packageManagerProfile.packageKinds.join(","),
                  packageManagerProfile.jsBridge,
                  packageManagerProfile.requirePackageManager,
                  packageManagerProfile.packagedBehavior,
                  packageManagerProfile.androidNetwork,
                  packageManagerProfile.rawExecutables,
                  packageManagerProfile.rawRegistryAccess,
                  Object.isFrozen(packageManagerProfile),
                  Object.isFrozen(packageManagerProfile.targetProfiles),
                  Object.isFrozen(packageManagerProfile.allowedSources),
                  Object.isFrozen(packageManagerProfile.deniedSources),
                  Object.isFrozen(packageManagerProfile.planOperations),
                  Object.isFrozen(packageManagerProfile.mutationOperations),
                  Object.isFrozen(packageManagerProfile.operations),
                  Object.isFrozen(packageManagerProfile.packageKinds)
                ].join("|"));
                console.log("p13pm.boundary=" + [
                  typeof globalThis.package_manager,
                  typeof globalThis.npm,
                  typeof process.npm,
                  loads("package_manager"),
                  loads("npm"),
                  loads("node:npm"),
                  profile.network,
                  profile.nativeAddon,
                  profile.highRiskCapabilities && profile.highRiskCapabilities.nativeAddon,
                  profile.disabledBuiltins.includes("child_process")
                ].join("|"));
            """.trimIndent(),
            sourceName = "<node-package-manager-phase13-p13-42.js>",
            workingDirectory = root,
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("P13-42 package manager profile", result)
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "p13pm.profile=android_substrate_partial|false|desktop_compat_opt_in|" +
                    "available|local_app_private_partial|local_unpacked_only|denied|" +
                    "host_managed_plus_android_store_substrate|deferred|host_side_only|" +
                    "autojs6_lock_host_managed|sha512_required|denied|not_promoted|denied|" +
                    "denied|denied|denied|denied|transactional_store_only|" +
                    "locked_cancellable_atomic|guarded_local_unpacked_mutation|" +
                    "local_unpacked_host_managed_only|package_manager_mutate_explicit_local_unpacked|" +
                    "local_unpacked_host_managed|" +
                    "raw_registry,remote_tarball,git,http_url,file_url,workspace_symlink,unverified_cache|" +
                    "planInstall,planUpdate,planRemove|install,update,remove|package_manager.mutate|allowMutation_required|" +
                    "install,update,remove,list,verify,prune|" +
                    "dependency,plugin,project|guarded_mutation_partial|guarded_mutation_partial|not_promoted|denied|" +
                    "false|false|true|true|true|true|true|true|true|true",
            ),
        )
        assertTrue(
            result.stdout,
            result.stdout.contains("p13pm.boundary=undefined|undefined|undefined|true|false|false|false|false|false|true"),
        )
    }

    @Test
    fun packageManagerGuardedMutationFacadeInstallsUpdatesRemovesAndMaintainsLocalStore() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-42 package_manager guarded mutation facade")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                (async function main() {
                  const assert = require("assert");
                  const fs = require("fs");
                  const path = require("path");
                  const packageManager = require("package_manager");
                  const status = packageManager.status();
                  const policy = packageManager.policy();
                  const sourcePolicy = packageManager.sourcePolicy();

                  assert.strictEqual(Object.isFrozen(packageManager), true);
                  assert.strictEqual(Object.isFrozen(status), true);
                  assert.strictEqual(Object.isFrozen(policy), true);
                  assert.strictEqual(Object.isFrozen(sourcePolicy), true);
                  assert.strictEqual(Object.isFrozen(status.operations), true);
                  assert.strictEqual(Object.isFrozen(policy.operations), true);
                  assert.strictEqual(Object.isFrozen(sourcePolicy.allowedSources), true);
                  assert.strictEqual(Object.isFrozen(sourcePolicy.deniedSources), true);
                  assert.strictEqual(Object.isFrozen(sourcePolicy.planOperations), true);
                  assert.strictEqual(Object.isFrozen(sourcePolicy.localUnpacked), true);
                  assert.strictEqual(Object.isFrozen(sourcePolicy.registry), true);
                  assert.strictEqual(Object.isFrozen(sourcePolicy.tarball), true);
                  assert.strictEqual(Object.isFrozen(sourcePolicy.futurePromotionRequires), true);
                  assert.strictEqual(status.status, "guarded_mutation_partial");
                  assert.strictEqual(status.jsBridge, "guarded_mutation_partial");
                  assert.strictEqual(status.requirePackageManager, "guarded_mutation_partial");
                  assert.strictEqual(status.grantsInstallAuthority, true);
                  assert.strictEqual(status.sourcePolicy, "guarded_local_unpacked_mutation");
                  assert.strictEqual(status.sourceAuthority, "local_unpacked_host_managed_only");
                  assert.strictEqual(status.installAuthority, "package_manager_mutate_explicit_local_unpacked");
                  assert.strictEqual(policy.npmCli, "denied");
                  assert.strictEqual(policy.registryDownload, "deferred");
                  assert.strictEqual(policy.lifecycleScripts, "denied");
                  assert.strictEqual(policy.rawRegistryAccess, false);
                  assert.strictEqual(policy.sourceAuthority, "local_unpacked_host_managed_only");
                  assert.strictEqual(policy.installAuthority, "package_manager_mutate_explicit_local_unpacked");
                  assert.deepStrictEqual(policy.allowedSources, ["local_unpacked_host_managed"]);
                  assert(policy.deniedSources.includes("raw_registry"));
                  assert(policy.deniedSources.includes("remote_tarball"));
                  assert.deepStrictEqual(policy.planOperations, ["planInstall", "planUpdate", "planRemove"]);
                  assert.deepStrictEqual(policy.mutationOperations, ["install", "update", "remove"]);
                  assert.strictEqual(policy.mutationCapability, "package_manager.mutate");
                  assert.strictEqual(policy.mutationConfirmation, "allowMutation_required");
                  assert.strictEqual(policy.requiresExpectedIntegrity, true);
                  assert.strictEqual(sourcePolicy.schema, "autojs6-node-package-manager-source-policy-v1");
                  assert.strictEqual(sourcePolicy.status, "guarded_local_unpacked_mutation");
                  assert.strictEqual(sourcePolicy.grantsInstallAuthority, true);
                  assert.deepStrictEqual(sourcePolicy.allowedSources, ["local_unpacked_host_managed"]);
                  assert(sourcePolicy.deniedSources.includes("raw_registry"));
                  assert(sourcePolicy.deniedSources.includes("remote_tarball"));
                  assert.deepStrictEqual(sourcePolicy.planOperations, ["planInstall", "planUpdate", "planRemove"]);
                  assert.deepStrictEqual(sourcePolicy.mutationOperations, ["install", "update", "remove"]);
                  assert.strictEqual(sourcePolicy.mutationCapability, "package_manager.mutate");
                  assert.strictEqual(sourcePolicy.mutationConfirmation, "allowMutation_required");
                  assert.strictEqual(sourcePolicy.requiresExpectedIntegrity, true);
                  assert.strictEqual(sourcePolicy.localUnpacked.status, "allowed_substrate");
                  assert.strictEqual(sourcePolicy.localUnpacked.requiresSha512, true);
                  assert.strictEqual(sourcePolicy.registry.rawRegistryAccess, false);
                  assert.strictEqual(sourcePolicy.tarball.androidDownload, "denied");
                  assert.strictEqual(sourcePolicy.npmCli.status, "denied");
                  assert.deepStrictEqual(status.operations, ["install", "update", "remove", "list", "verify", "prune"]);
                  assert.deepStrictEqual(policy.packageKinds, ["dependency", "plugin", "project"]);

                  async function rejectionCode(methodName, request) {
                    try {
                      await packageManager[methodName](request);
                    } catch (error) {
                      return String(error && (error.autojs6Code || error.code || ""));
                    }
                    throw new Error("package_manager." + methodName + " must reject");
                  }

                  const planSuffix = Date.now().toString(36);
                  const planName = "@autojs6/p13-plan-" + planSuffix;
                  const planSourceDir = "p13-plan-package-" + planSuffix;
                  fs.mkdirSync(planSourceDir, { recursive: true });
                  fs.writeFileSync(path.join(planSourceDir, "package.json"), JSON.stringify({
                    name: planName,
                    version: "1.0.0"
                  }));
                  fs.writeFileSync(path.join(planSourceDir, "index.cjs"), "module.exports = 13;\n");
                  const mutationNotConfirmed = await rejectionCode("install", {
                    name: planName,
                    version: "1.0.0",
                    sourceDir: planSourceDir,
                    expectedIntegrity: "sha512-placeholder"
                  });
                  assert.strictEqual(mutationNotConfirmed, "ERR_AUTOJS6_PACKAGE_MANAGER_MUTATION_NOT_CONFIRMED");
                  const mutationMissingIntegrity = await rejectionCode("install", {
                    name: planName,
                    version: "1.0.0",
                    sourceDir: planSourceDir,
                    allowMutation: true
                  });
                  assert.strictEqual(mutationMissingIntegrity, "ERR_AUTOJS6_BRIDGE_INVALID_REQUEST");
                  const planInstallResult = await packageManager.planInstall({
                    name: planName,
                    version: "1.0.0",
                    sourceDir: planSourceDir,
                    timeoutMs: 5000
                  });
                  assert.strictEqual(planInstallResult.schema, "autojs6-node-android-package-plan-v1");
                  assert.strictEqual(planInstallResult.operation, "install");
                  assert.strictEqual(planInstallResult.status, "ready");
                  assert.strictEqual(planInstallResult.dryRun, true);
                  assert.strictEqual(planInstallResult.grantsInstallAuthority, false);
                  assert.strictEqual(planInstallResult.record.name, planName);
                  assert.strictEqual(Object.isFrozen(planInstallResult), true);
                  assert.strictEqual(Object.isFrozen(planInstallResult.record), true);
                  const installedResult = await packageManager.install({
                    name: planName,
                    version: "1.0.0",
                    sourceDir: planSourceDir,
                    expectedIntegrity: planInstallResult.record.integrity,
                    allowMutation: true,
                    timeoutMs: 5000
                  });
                  assert.strictEqual(installedResult.operation, "install");
                  assert.strictEqual(installedResult.status, "installed");
                  assert.strictEqual(installedResult.record.integrity, planInstallResult.record.integrity);
                  assert.strictEqual(Object.isFrozen(installedResult), true);
                  assert.strictEqual(Object.isFrozen(installedResult.record), true);
                  const duplicatePlan = await packageManager.planInstall({
                    name: planName,
                    version: "1.0.0",
                    sourceDir: planSourceDir,
                    timeoutMs: 5000
                  });
                  assert.strictEqual(duplicatePlan.status, "already_installed");
                  fs.writeFileSync(path.join(planSourceDir, "index.cjs"), "module.exports = 14;\n");
                  const planUpdateResult = await packageManager.planUpdate({
                    name: planName,
                    version: "1.0.0",
                    sourceDir: planSourceDir,
                    timeoutMs: 5000
                  });
                  assert.strictEqual(planUpdateResult.operation, "update");
                  assert.strictEqual(planUpdateResult.status, "ready");
                  assert.strictEqual(planUpdateResult.grantsInstallAuthority, false);
                  const updatedResult = await packageManager.update({
                    name: planName,
                    version: "1.0.0",
                    sourceDir: planSourceDir,
                    expectedIntegrity: planUpdateResult.record.integrity,
                    allowMutation: true,
                    timeoutMs: 5000
                  });
                  assert.strictEqual(updatedResult.operation, "update");
                  assert.strictEqual(updatedResult.status, "updated");
                  assert.strictEqual(updatedResult.record.integrity, planUpdateResult.record.integrity);
                  const planRemoveResult = await packageManager.planRemove({
                    name: planName,
                    version: "1.0.0",
                    timeoutMs: 5000
                  });
                  assert.strictEqual(planRemoveResult.operation, "remove");
                  assert.strictEqual(planRemoveResult.status, "ready");
                  assert.strictEqual(planRemoveResult.grantsInstallAuthority, false);
                  assert.strictEqual(planRemoveResult.existingRecord.integrity, updatedResult.record.integrity);
                  const removedResult = await packageManager.remove({
                    name: planName,
                    version: "1.0.0",
                    expectedIntegrity: planRemoveResult.existingRecord.integrity,
                    allowMutation: true,
                    timeoutMs: 5000
                  });
                  assert.strictEqual(removedResult.operation, "remove");
                  assert.strictEqual(removedResult.status, "removed");

                  const listResult = await packageManager.list({ timeoutMs: 5000 });
                  assert.strictEqual(Array.isArray(listResult), true);
                  assert.strictEqual(Object.isFrozen(listResult), true);
                  listResult.forEach((record) => {
                    assert.strictEqual(Object.isFrozen(record), true);
                    assert.strictEqual(typeof record.name, "string");
                    assert.strictEqual(typeof record.version, "string");
                  });
                  const verifyResult = await packageManager.verify({
                    name: "@autojs6/p13-missing-runtime-bridge",
                    version: "0.0.0",
                    timeoutMs: 5000
                  });
                  assert.strictEqual(verifyResult.operation, "verify");
                  assert.strictEqual(verifyResult.status, "missing");
                  assert.strictEqual(Object.isFrozen(verifyResult), true);
                  const pruneResult = await packageManager.prune({ maxTransactionAgeMs: 0, timeoutMs: 5000 });
                  assert.strictEqual(pruneResult.operation, "prune");
                  assert.strictEqual(pruneResult.status, "pruned");
                  assert.strictEqual(Object.isFrozen(pruneResult), true);

                  console.log("p13pm.facade=" + [
                    status.status,
                    status.jsBridge,
                    status.requirePackageManager,
                    policy.npmCli,
                    policy.registryDownload,
                    policy.rawRegistryAccess,
                    Object.isFrozen(packageManager),
                    Object.isFrozen(status),
                    Object.isFrozen(policy),
                    Object.isFrozen(sourcePolicy),
                    sourcePolicy.status,
                    sourcePolicy.sourceAuthority,
                    sourcePolicy.allowedSources.join(","),
                    sourcePolicy.deniedSources.includes("raw_registry"),
                    sourcePolicy.grantsInstallAuthority,
                    sourcePolicy.planOperations.join(","),
                    sourcePolicy.mutationOperations.join(","),
                    planInstallResult.status,
                    planInstallResult.grantsInstallAuthority,
                    installedResult.status,
                    duplicatePlan.status,
                    planUpdateResult.status,
                    updatedResult.status,
                    planRemoveResult.status,
                    removedResult.status,
                    mutationNotConfirmed,
                    mutationMissingIntegrity,
                    listResult.length >= 0,
                    verifyResult.status,
                    pruneResult.status,
                    require.resolve("package_manager"),
                    typeof globalThis.package_manager
                  ].join("|"));
                })().catch((error) => {
                  console.log("p13pm.facade.error=" + (error && error.stack ? error.stack : String(error)));
                  process.exitCode = 1;
                });
            """.trimIndent(),
            sourceName = "<node-package-manager-phase13-facade.js>",
            workingDirectory = root,
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("P13-42 package_manager guarded mutation facade", result)
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "p13pm.facade=guarded_mutation_partial|guarded_mutation_partial|guarded_mutation_partial|denied|deferred|false|" +
                    "true|true|true|true|guarded_local_unpacked_mutation|local_unpacked_host_managed_only|" +
                    "local_unpacked_host_managed|true|true|" +
                    "planInstall,planUpdate,planRemove|install,update,remove|ready|false|installed|already_installed|" +
                    "ready|updated|ready|removed|ERR_AUTOJS6_PACKAGE_MANAGER_MUTATION_NOT_CONFIRMED|" +
                    "ERR_AUTOJS6_BRIDGE_INVALID_REQUEST|true|missing|pruned|" +
                    "package_manager|undefined",
            ),
        )
    }

    @Test
    fun androidStoreSubstrateInstallsLocalUnpackedPackageAndDeniesUnsafePolicies() {
        val source = packageSource(
            dirName = "pkg-safe",
            name = "@autojs6/p13-package-manager",
            version = "1.0.0",
            files = mapOf("index.cjs" to "module.exports = 42;\n"),
        )
        val integrity = manager.calculateSourceIntegrity(source)
        val installed = manager.install(
            NodeAndroidPackageInstallManager.PackageRequest(
                name = "@autojs6/p13-package-manager",
                version = "1.0.0",
                sourceDir = source,
                expectedIntegrity = integrity,
            ),
        )
        assertEquals(NodeAndroidPackageInstallManager.STATUS_INSTALLED, installed.status)
        assertEquals(integrity, installed.record?.integrity)
        assertEquals(1, manager.list().size)
        assertEquals(
            NodeAndroidPackageInstallManager.STATUS_VERIFIED,
            manager.verify("@autojs6/p13-package-manager", "1.0.0").status,
        )

        val nativeValues = manager.nativeValues()
        assertEquals("partial", nativeValues["android_package_manager.status"])
        assertEquals("local_unpacked_only", nativeValues["android_package_manager.android_runtime_install"])
        assertEquals("disabled", nativeValues["android_package_manager.npm_cli"])
        assertEquals("disabled", nativeValues["android_package_manager.registry_download"])
        assertEquals("autojs6-lock-host-managed", nativeValues["android_package_manager.lockfile"])
        assertEquals("sha512", nativeValues["android_package_manager.integrity"])
        assertEquals("disabled", nativeValues["android_package_manager.lifecycle_scripts"])
        assertEquals("disabled", nativeValues["android_package_manager.bin_links"])
        assertEquals("disabled", nativeValues["android_package_manager.symlinks"])
        assertEquals("disabled", nativeValues["android_package_manager.native_payload_execution"])
        assertEquals("disabled", nativeValues["android_package_manager.postinstall_download"])
        assertEquals("disabled", nativeValues["android_package_manager.network_install"])
        assertEquals("guarded_local_unpacked_mutation", nativeValues["android_package_manager.source_policy"])
        assertEquals("local_unpacked_host_managed_only", nativeValues["android_package_manager.source_authority"])
        assertEquals("local_unpacked_host_managed", nativeValues["android_package_manager.allowed_sources"])
        assertEquals("disabled", nativeValues["android_package_manager.raw_registry_authority"])
        assertEquals("disabled", nativeValues["android_package_manager.remote_tarball_install"])
        assertEquals("disabled", nativeValues["android_package_manager.unverified_cache_reuse"])
        assertEquals(NodeAndroidPackageInstallManager.INSTALL_AUTHORITY, nativeValues["android_package_manager.install_authority"])
        assertEquals("package_manager.mutate", nativeValues["android_package_manager.mutation_capability"])
        assertEquals("allowMutation_required", nativeValues["android_package_manager.mutation_confirmation"])
        assertEquals("guarded_mutation_partial", nativeValues["android_package_manager.js_bridge"])

        val postinstall = packageSource(
            dirName = "pkg-postinstall",
            name = "postinstall-download-package",
            version = "1.0.0",
            scriptsJson = """"postinstall":"node download.js"""",
            files = mapOf(
                "index.cjs" to "module.exports = 1;\n",
                "download.js" to "require('https').get('https://example.test/artifact.node');\n",
            ),
        )
        expectPackageError(NodeAndroidPackageInstallManager.ERROR_LIFECYCLE_SCRIPT_DISABLED) {
            manager.install(request("postinstall-download-package", "1.0.0", postinstall))
        }

        val binPackage = packageSource(
            dirName = "pkg-bin",
            name = "bin-shim-package",
            version = "1.0.0",
            binJson = """"bin-shim-package":"cli.js"""",
            files = mapOf("cli.js" to "#!/usr/bin/env node\n"),
        )
        expectPackageError(NodeAndroidPackageInstallManager.ERROR_BIN_DISABLED) {
            manager.install(request("bin-shim-package", "1.0.0", binPackage))
        }

        val nativeBuild = packageSource(
            dirName = "pkg-native-build",
            name = "native-build-package",
            version = "1.0.0",
            files = mapOf(
                "binding.gyp" to "{}\n",
                "build/Release/addon.node" to "native",
            ),
        )
        expectPackageError(NodeAndroidPackageInstallManager.ERROR_NATIVE_PAYLOAD_DISABLED) {
            manager.install(request("native-build-package", "1.0.0", nativeBuild))
        }
    }

    private fun request(name: String, version: String, sourceDir: File): NodeAndroidPackageInstallManager.PackageRequest {
        return NodeAndroidPackageInstallManager.PackageRequest(
            name = name,
            version = version,
            sourceDir = sourceDir,
            expectedIntegrity = manager.calculateSourceIntegrity(sourceDir),
        )
    }

    private fun packageSource(
        dirName: String,
        name: String,
        version: String,
        scriptsJson: String? = null,
        binJson: String? = null,
        files: Map<String, String>,
    ): File {
        val dir = File(sources, dirName).apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create package source: $absolutePath" }
        }
        val scripts = scriptsJson?.let { ""","scripts":{$it}""" }.orEmpty()
        val bin = binJson?.let { ""","bin":{$it}""" }.orEmpty()
        File(dir, "package.json").writeText(
            """{"name":"$name","version":"$version"$scripts$bin}""",
        )
        files.forEach { (relative, text) ->
            File(dir, relative).apply {
                parentFile?.mkdirs()
                writeText(text)
            }
        }
        return dir
    }

    private fun expectPackageError(
        code: String,
        block: () -> Unit,
    ): NodeAndroidPackageInstallManager.PackageInstallException {
        try {
            block()
            fail("Expected package install error: $code")
        } catch (error: NodeAndroidPackageInstallManager.PackageInstallException) {
            assertEquals(code, error.code)
            return error
        }
        throw AssertionError("unreachable")
    }
}
