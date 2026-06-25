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
                    "locked_cancellable_atomic|install,update,remove,list,verify,prune|" +
                    "dependency,plugin,project|diagnostic_only|diagnostic_only|not_promoted|denied|" +
                    "false|false|true|true|true|true",
            ),
        )
        assertTrue(
            result.stdout,
            result.stdout.contains("p13pm.boundary=undefined|undefined|undefined|true|false|false|false|false|false|true"),
        )
    }

    @Test
    fun packageManagerDiagnosticFacadeIsVisibleButInstallOperationsRemainDenied() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-42 package_manager diagnostic facade")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                const assert = require("assert");
                const packageManager = require("package_manager");
                const status = packageManager.status();
                const policy = packageManager.policy();

                assert.strictEqual(Object.isFrozen(packageManager), true);
                assert.strictEqual(Object.isFrozen(status), true);
                assert.strictEqual(Object.isFrozen(policy), true);
                assert.strictEqual(Object.isFrozen(status.operations), true);
                assert.strictEqual(Object.isFrozen(policy.operations), true);
                assert.strictEqual(status.status, "diagnostic_only");
                assert.strictEqual(status.jsBridge, "diagnostic_only");
                assert.strictEqual(status.requirePackageManager, "diagnostic_only");
                assert.strictEqual(status.grantsInstallAuthority, false);
                assert.strictEqual(policy.npmCli, "denied");
                assert.strictEqual(policy.registryDownload, "deferred");
                assert.strictEqual(policy.lifecycleScripts, "denied");
                assert.strictEqual(policy.rawRegistryAccess, false);
                assert.deepStrictEqual(status.operations, ["install", "update", "remove", "list", "verify", "prune"]);
                assert.deepStrictEqual(policy.packageKinds, ["dependency", "plugin", "project"]);

                function deniedCode(methodName) {
                  try {
                    packageManager[methodName]({ name: "@autojs6/p13", version: "1.0.0" });
                  } catch (error) {
                    return String(error && (error.autojs6Code || error.code || ""));
                  }
                  throw new Error("package_manager." + methodName + " must remain unpromoted");
                }
                const deniedCodes = ["install", "update", "remove", "list", "verify", "prune"].map(deniedCode);
                deniedCodes.forEach((code) => assert.strictEqual(code, "ERR_AUTOJS6_PACKAGE_MANAGER_NOT_PROMOTED"));

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
                  deniedCodes.join(","),
                  require.resolve("package_manager"),
                  typeof globalThis.package_manager
                ].join("|"));
            """.trimIndent(),
            sourceName = "<node-package-manager-phase13-facade.js>",
            workingDirectory = root,
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("P13-42 package_manager diagnostic facade", result)
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "p13pm.facade=diagnostic_only|diagnostic_only|diagnostic_only|denied|deferred|false|" +
                    "true|true|true|ERR_AUTOJS6_PACKAGE_MANAGER_NOT_PROMOTED,ERR_AUTOJS6_PACKAGE_MANAGER_NOT_PROMOTED," +
                    "ERR_AUTOJS6_PACKAGE_MANAGER_NOT_PROMOTED,ERR_AUTOJS6_PACKAGE_MANAGER_NOT_PROMOTED," +
                    "ERR_AUTOJS6_PACKAGE_MANAGER_NOT_PROMOTED,ERR_AUTOJS6_PACKAGE_MANAGER_NOT_PROMOTED|" +
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
        assertEquals("diagnostic_only", nativeValues["android_package_manager.js_bridge"])

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
