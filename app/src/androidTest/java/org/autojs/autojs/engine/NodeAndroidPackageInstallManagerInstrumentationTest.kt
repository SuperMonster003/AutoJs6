package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeAndroidPackageInstallManagerInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext
    private lateinit var root: File
    private lateinit var store: File
    private lateinit var sources: File
    private lateinit var manager: NodeAndroidPackageInstallManager

    @Before
    fun setUp() {
        root = File(context.cacheDir, "node-android-package-install-manager-test").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create package manager test root: $absolutePath" }
        }
        store = File(root, "store")
        sources = File(root, "sources").apply { mkdirs() }
        manager = NodeAndroidPackageInstallManager(store) { 1_700_000_000_000L }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun installUpdateListVerifyRemoveAndPruneAreTransactionalAndIntegrityChecked() {
        val sourceV1 = packageSource(
            dirName = "pkg-v1",
            name = "@autojs6/test-package",
            version = "1.0.0",
            files = mapOf("index.cjs" to "module.exports = 'v1';\n"),
        )
        val integrityV1 = manager.calculateSourceIntegrity(sourceV1)

        val installed = manager.install(
            NodeAndroidPackageInstallManager.PackageRequest(
                name = "@autojs6/test-package",
                version = "1.0.0",
                sourceDir = sourceV1,
                expectedIntegrity = integrityV1,
            ),
        )
        assertEquals(NodeAndroidPackageInstallManager.STATUS_INSTALLED, installed.status)
        assertEquals(integrityV1, installed.record?.integrity)
        assertEquals(1, manager.list().size)
        assertEquals(NodeAndroidPackageInstallManager.STATUS_VERIFIED, manager.verify("@autojs6/test-package", "1.0.0").status)

        val duplicate = expectPackageError(NodeAndroidPackageInstallManager.ERROR_ALREADY_INSTALLED) {
            manager.install(
                NodeAndroidPackageInstallManager.PackageRequest(
                    name = "@autojs6/test-package",
                    version = "1.0.0",
                    sourceDir = sourceV1,
                    expectedIntegrity = integrityV1,
                ),
            )
        }
        assertTrue(duplicate.message.contains("already installed"))

        val sourceV2 = packageSource(
            dirName = "pkg-v2",
            name = "@autojs6/test-package",
            version = "1.0.0",
            files = mapOf("index.cjs" to "module.exports = 'v2';\n"),
        )
        val wrongIntegrity = integrityV1
        expectPackageError(NodeAndroidPackageInstallManager.ERROR_INTEGRITY_MISMATCH) {
            manager.update(
                NodeAndroidPackageInstallManager.PackageRequest(
                    name = "@autojs6/test-package",
                    version = "1.0.0",
                    sourceDir = sourceV2,
                    expectedIntegrity = wrongIntegrity,
                ),
            )
        }
        assertEquals("module.exports = 'v1';\n", File(manager.list().single().packageDir, "index.cjs").readText())

        val integrityV2 = manager.calculateSourceIntegrity(sourceV2)
        val updated = manager.update(
            NodeAndroidPackageInstallManager.PackageRequest(
                name = "@autojs6/test-package",
                version = "1.0.0",
                sourceDir = sourceV2,
                expectedIntegrity = integrityV2,
            ),
        )
        assertEquals(NodeAndroidPackageInstallManager.STATUS_UPDATED, updated.status)
        assertEquals("module.exports = 'v2';\n", File(manager.list().single().packageDir, "index.cjs").readText())
        assertEquals(NodeAndroidPackageInstallManager.STATUS_VERIFIED, manager.verify("@autojs6/test-package", "1.0.0").status)

        assertEquals(
            NodeAndroidPackageInstallManager.STATUS_REMOVED,
            manager.remove("@autojs6/test-package", "1.0.0").status,
        )
        assertTrue(manager.list().isEmpty())
        assertEquals(
            NodeAndroidPackageInstallManager.STATUS_MISSING,
            manager.verify("@autojs6/test-package", "1.0.0").status,
        )
        assertEquals(NodeAndroidPackageInstallManager.STATUS_PRUNED, manager.prune().status)
    }

    @Test
    fun rejectsLifecycleScriptsNativePayloadsBinEntriesAndCancellation() {
        val lifecycle = packageSource(
            dirName = "pkg-lifecycle",
            name = "lifecycle-package",
            version = "1.0.0",
            scriptsJson = """"install":"node install.js"""",
            files = mapOf("index.cjs" to "module.exports = 1;\n"),
        )
        expectPackageError(NodeAndroidPackageInstallManager.ERROR_LIFECYCLE_SCRIPT_DISABLED) {
            manager.install(request("lifecycle-package", "1.0.0", lifecycle))
        }

        val nativePayload = packageSource(
            dirName = "pkg-native",
            name = "native-package",
            version = "1.0.0",
            files = mapOf("build/Release/addon.node" to "native"),
        )
        expectPackageError(NodeAndroidPackageInstallManager.ERROR_NATIVE_PAYLOAD_DISABLED) {
            manager.install(request("native-package", "1.0.0", nativePayload))
        }

        val binPackage = packageSource(
            dirName = "pkg-bin",
            name = "bin-package",
            version = "1.0.0",
            binJson = """"bin-package":"cli.js"""",
            files = mapOf("cli.js" to "#!/usr/bin/env node\n"),
        )
        expectPackageError(NodeAndroidPackageInstallManager.ERROR_BIN_DISABLED) {
            manager.install(request("bin-package", "1.0.0", binPackage))
        }

        val cancellable = packageSource(
            dirName = "pkg-cancel",
            name = "cancel-package",
            version = "1.0.0",
            files = mapOf("index.cjs" to "module.exports = 1;\n"),
        )
        val token = NodeAndroidPackageInstallManager.CancellationToken().also { it.cancel() }
        expectPackageError(NodeAndroidPackageInstallManager.ERROR_CANCELLED) {
            manager.install(request("cancel-package", "1.0.0", cancellable), token)
        }
        assertTrue(manager.list().isEmpty())
    }

    @Test
    fun nativeValuesAdvertiseSafeAndroidPackageManagerBoundary() {
        val nativeValues = manager.nativeValues()
        assertEquals("partial", nativeValues["android_package_manager.status"])
        assertEquals("true", nativeValues["android_package_manager.transactional"])
        assertEquals("true", nativeValues["android_package_manager.cancellable"])
        assertEquals("true", nativeValues["android_package_manager.locked"])
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
        assertEquals("read_maintenance_partial", nativeValues["android_package_manager.js_bridge"])
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
