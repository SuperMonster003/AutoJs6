package org.autojs.autojs.engine

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.Os
import org.autojs.autojs.engine.encryption.AdvancedEncryptionStandard
import org.autojs.autojs.engine.encryption.ScriptEncryption
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context as RhinoContext
import java.io.File
import java.nio.charset.StandardCharsets

class NodeEmbeddedEngineRequestPreparationInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("engine request-preparation regression")
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        runCatching { RhinoContext.exit() }
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun r4CorpusUsesOnDemandProviderWithoutProjectWideScans() {
        val fixture = createLargeWorkingDirectory()
        try {
            assertEquals(
                "R4 acceptance corpus must contain at least 4000 unrelated files",
                FILLER_FILE_COUNT,
                fixture.fillerFileCount,
            )

            val businessLogicEntry = File(fixture.root, "business-logic.cjs").apply {
                writeText(
                    """
                    "nodejs";
                    const values = [1, 2, 3];
                    let total = 0;
                    function add(value) { total += value; }
                    values.forEach(add);
                    const expression = total * 2;
                    if (expression !== 12) throw new Error("business logic failed");
                    console.log("r4.business.first=" + expression);
                    console.log("r4.business.second=true");
                    Promise.resolve().then(async () => {
                      await Promise.resolve();
                      if (total !== 6) throw new Error("async business logic failed");
                    }).catch((error) => setImmediate(() => { throw error; }));
                    setTimeout(() => {}, 0);
                    """.trimIndent(),
                    StandardCharsets.UTF_8,
                )
            }
            val businessLogicRun = executeFile(fixture.root, businessLogicEntry, "non-trivial module-free business logic")
            val businessProvider = assertOnDemandProviderWithoutDirectoryScan(
                label = "non-trivial module-free business logic",
                run = businessLogicRun,
            )
            assertFalse(
                "R4 performance must not depend on the old trivial-source allowlist",
                businessLogicRun.diagnostics.commonJs.trivialModuleFreeEntry,
            )
            assertEquals(0L, businessProvider.requestCount)
            assertEquals(0L, businessProvider.resolvedSourceCount)
            assertEquals(0L, businessProvider.decryptedSourceCount)

            File(fixture.root, "plain-static-child.cjs").writeText(
                """module.exports = { value: "plain-static-ok" };""",
                StandardCharsets.UTF_8,
            )
            val plainStaticEntry = File(fixture.root, "plain-static-entry.cjs").apply {
                writeText(
                    """
                    "nodejs";
                    const child = require("./plain-static-child.cjs");
                    if (child.value !== "plain-static-ok") throw new Error("plain static require failed");
                    """.trimIndent(),
                    StandardCharsets.UTF_8,
                )
            }
            val plainStaticRun = executeFile(fixture.root, plainStaticEntry, "plain static CommonJS")
            assertOnDemandProviderWithoutDirectoryScan("plain static CommonJS", plainStaticRun)
            assertEquals(1, plainStaticRun.diagnostics.commonJs.staticRequireCount)
            assertEquals(1, plainStaticRun.diagnostics.commonJs.moduleCount)
            assertTrue(plainStaticRun.diagnostics.commonJs.complete)

            File(fixture.root, "plain-dynamic-child.cjs").writeText(
                """module.exports = { value: "plain-dynamic-ok" };""",
                StandardCharsets.UTF_8,
            )
            val plainDynamicEntry = File(fixture.root, "plain-dynamic-entry.cjs").apply {
                writeText(
                    """
                    "nodejs";
                    const childName = "./plain-dynamic-child.cjs";
                    const child = require(childName);
                    if (child.value !== "plain-dynamic-ok") throw new Error("plain dynamic require failed");
                    """.trimIndent(),
                    StandardCharsets.UTF_8,
                )
            }
            val plainDynamicRun = executeFile(fixture.root, plainDynamicEntry, "plain dynamic CommonJS")
            val plainDynamicProvider = assertOnDemandProviderWithoutDirectoryScan(
                "plain dynamic CommonJS",
                plainDynamicRun,
            )
            assertEquals(1, plainDynamicRun.diagnostics.commonJs.dynamicRequireIgnoredCount)
            assertEquals(0L, plainDynamicProvider.decryptedSourceCount)

            writePackage(
                root = fixture.root,
                packageName = "r4-plain-package",
                source = """module.exports = { value: "plain-package-ok" };""",
                encrypted = false,
            )
            val plainPackageEntry = File(fixture.root, "plain-package-entry.cjs").apply {
                writeText(
                    """
                    "nodejs";
                    const pkg = require("r4-plain-package");
                    if (pkg.value !== "plain-package-ok") throw new Error("plain package resolution failed");
                    """.trimIndent(),
                    StandardCharsets.UTF_8,
                )
            }
            assertOnDemandProviderWithoutDirectoryScan(
                "plain package resolution",
                executeFile(fixture.root, plainPackageEntry, "plain package resolution"),
            )

            val missingEntry = File(fixture.root, "missing-module-entry.cjs").apply {
                writeText(
                    """
                    "nodejs";
                    let missingRejected = false;
                    try {
                      require("./r4-intentionally-missing.cjs");
                    } catch (error) {
                      const code = String(error && (error.autojs6Code || error.code) || "");
                      missingRejected = code === "ERR_AUTOJS6_MODULE_NOT_FOUND" || code === "MODULE_NOT_FOUND";
                    }
                    if (!missingRejected) throw new Error("missing module did not retain stable rejection");
                    """.trimIndent(),
                    StandardCharsets.UTF_8,
                )
            }
            assertOnDemandProviderWithoutDirectoryScan(
                "missing CommonJS module",
                executeFile(fixture.root, missingEntry, "missing CommonJS module"),
            )

            if (BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED) {
                File(fixture.root, "plain-esm-child.mjs").writeText(
                    """export const value = "plain-esm-ok";""",
                    StandardCharsets.UTF_8,
                )
                val plainEsmEntry = File(fixture.root, "plain-esm-entry.mjs").apply {
                    writeText(
                        """
                        import { value } from "./plain-esm-child.mjs";
                        if (value !== "plain-esm-ok") throw new Error("plain ESM resolution failed");
                        """.trimIndent(),
                        StandardCharsets.UTF_8,
                    )
                }
                val plainEsmRun = executeFile(fixture.root, plainEsmEntry, "plain static ESM")
                assertOnDemandProviderWithoutDirectoryScan("plain static ESM", plainEsmRun)
                assertTrue(plainEsmRun.diagnostics.isEsmEntry)
            }

            withTestScriptEncryption {
                writeEncryptedScript(
                    File(fixture.root, "encrypted-static-child.cjs"),
                    """module.exports = { value: "encrypted-static-ok" };""",
                )
                val encryptedStaticEntry = File(fixture.root, "encrypted-static-entry.cjs").apply {
                    writeText(
                        """
                        "nodejs";
                        const child = require("./encrypted-static-child.cjs");
                        if (child.value !== "encrypted-static-ok") throw new Error("encrypted static require failed");
                        """.trimIndent(),
                        StandardCharsets.UTF_8,
                    )
                }
                val encryptedStaticRun = executeFile(
                    fixture.root,
                    encryptedStaticEntry,
                    "encrypted static CommonJS",
                )
                val encryptedStaticProvider = assertOnDemandProviderWithoutDirectoryScan(
                    "encrypted static CommonJS",
                    encryptedStaticRun,
                )
                assertEquals(
                    "statically resolved encrypted CommonJS should use the exact preload closure",
                    1,
                    encryptedStaticRun.diagnostics.commonJs.moduleCount,
                )
                assertEquals(0L, encryptedStaticProvider.requestCount)
                assertEquals(0L, encryptedStaticProvider.resolvedSourceCount)
                assertEquals(0L, encryptedStaticProvider.decryptedSourceCount)

                writeEncryptedScript(
                    File(fixture.root, "encrypted-dynamic-child.cjs"),
                    """module.exports = { value: "encrypted-dynamic-ok" };""",
                )
                val encryptedDynamicEntry = File(fixture.root, "encrypted-dynamic-entry.cjs").apply {
                    writeText(
                        """
                        "nodejs";
                        const childName = ["./encrypted", "dynamic-child.cjs"].join("-");
                        const child = require(childName);
                        if (child.value !== "encrypted-dynamic-ok") throw new Error("encrypted dynamic require failed");
                        """.trimIndent(),
                        StandardCharsets.UTF_8,
                    )
                }
                val encryptedDynamicRun = executeFile(
                    fixture.root,
                    encryptedDynamicEntry,
                    "encrypted dynamic CommonJS",
                )
                assertSingleOnDemandEncryptedCandidate("encrypted dynamic CommonJS", encryptedDynamicRun)
                assertEquals(1, encryptedDynamicRun.diagnostics.commonJs.dynamicRequireIgnoredCount)

                writePackage(
                    root = fixture.root,
                    packageName = "r4-encrypted-package",
                    source = """module.exports = { value: "encrypted-package-ok" };""",
                    encrypted = true,
                )
                val encryptedPackageEntry = File(fixture.root, "encrypted-package-entry.cjs").apply {
                    writeText(
                        """
                        "nodejs";
                        const packageName = ["r4", "encrypted", "package"].join("-");
                        const pkg = require(packageName);
                        if (pkg.value !== "encrypted-package-ok") throw new Error("encrypted package resolution failed");
                        """.trimIndent(),
                        StandardCharsets.UTF_8,
                    )
                }
                val encryptedPackageRun = executeFile(
                    fixture.root,
                    encryptedPackageEntry,
                    "encrypted package resolution",
                )
                val encryptedPackageProvider = assertOnDemandProviderWithoutDirectoryScan(
                    "encrypted package resolution",
                    encryptedPackageRun,
                )
                assertEquals(
                    "computed package resolution should probe package.json and the resolved entry only",
                    2L,
                    encryptedPackageProvider.requestCount,
                )
                assertEquals(2L, encryptedPackageProvider.resolvedSourceCount)
                assertEquals(1L, encryptedPackageProvider.decryptedSourceCount)
                assertTrue(encryptedPackageProvider.sourceBytes > 0L)

                if (BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED) {
                    writeEncryptedScript(
                        File(fixture.root, "encrypted-static-esm-child.mjs"),
                        """export const value = "encrypted-static-esm-ok";""",
                    )
                    val encryptedStaticEsmEntry = File(fixture.root, "encrypted-static-esm-entry.mjs").apply {
                        writeText(
                            """
                            import { value } from "./encrypted-static-esm-child.mjs";
                            if (value !== "encrypted-static-esm-ok") throw new Error("encrypted static ESM failed");
                            """.trimIndent(),
                            StandardCharsets.UTF_8,
                        )
                    }
                    val encryptedStaticEsmRun = executeFile(
                        fixture.root,
                        encryptedStaticEsmEntry,
                        "encrypted static ESM",
                    )
                    assertSingleOnDemandEncryptedCandidate("encrypted static ESM", encryptedStaticEsmRun)
                    assertTrue(encryptedStaticEsmRun.diagnostics.isEsmEntry)
                }

                if (
                    BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED &&
                    BuildConfig.NODEJS_DYNAMIC_IMPORT_LOCAL_ENABLED &&
                    BuildConfig.NODEJS_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED
                ) {
                    writeEncryptedScript(
                        File(fixture.root, "encrypted-dynamic-import-child.mjs"),
                        """export const value = "encrypted-dynamic-import-ok";""",
                    )
                    val encryptedDynamicImportEntry = File(fixture.root, "encrypted-dynamic-import-entry.cjs").apply {
                        writeText(
                            """
                            "nodejs";
                            (async () => {
                              const loaded = await import("./encrypted-dynamic-import-child.mjs");
                              if (loaded.value !== "encrypted-dynamic-import-ok") {
                                throw new Error("encrypted dynamic import failed");
                              }
                            })().catch((error) => setImmediate(() => { throw error; }));
                            """.trimIndent(),
                            StandardCharsets.UTF_8,
                        )
                    }
                    val encryptedDynamicImportRun = executeFile(
                        fixture.root,
                        encryptedDynamicImportEntry,
                        "encrypted dynamic import",
                    )
                    assertSingleOnDemandEncryptedCandidate("encrypted dynamic import", encryptedDynamicImportRun)
                    assertTrue(encryptedDynamicImportRun.diagnostics.commonJs.dynamicImportCount > 0)
                }
            }
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun directiveOnlyEmptyAndCommentEntriesKeepTheSameR4ProviderContract() {
        val root = File(context.cacheDir, "embedded-engine-r4-module-free-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create R4 module-free test dir: $absolutePath" }
        }
        try {
            val sources = linkedMapOf(
                "directive-only.js" to "\"nodejs\";\n/* Nothing to do in this Node.js script. */",
                "empty.cjs" to "",
                "trivia-only.cjs" to "// require('ignored-in-comment')\n/* import('also-ignored') */\n",
            )
            for ((name, source) in sources) {
                val entry = File(root, name).apply { writeText(source, StandardCharsets.UTF_8) }
                val run = executeFile(root, entry, name)
                val provider = assertOnDemandProviderWithoutDirectoryScan(name, run)
                assertTrue("$name should retain its module-free parser diagnostic", run.diagnostics.commonJs.trivialModuleFreeEntry)
                assertEquals(0L, provider.requestCount)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun staticCommonJsPackagePreloadRejectsSameInodeIntermediateSymlinkSwap() {
        val root = File(context.cacheDir, "embedded-engine-r4-preload-root-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create static preload root: $absolutePath" }
        }
        val outsideRoot = File(context.cacheDir, "embedded-engine-r4-preload-outside-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create static preload outside root: $absolutePath" }
        }
        val packageDirectory = File(root, "package-dir").apply {
            check(mkdirs()) { "Failed to create static preload package directory: $absolutePath" }
        }
        val movedPackageDirectory = File(outsideRoot, "moved-package-dir")
        val entrySource = "require(\"./package-dir\");"
        val entryFile = File(root, "entry.cjs").apply {
            writeText(entrySource, StandardCharsets.UTF_8)
        }
        File(packageDirectory, "package.json").writeText(
            """{"main":"./index.cjs"}""",
            StandardCharsets.UTF_8,
        )
        File(packageDirectory, "index.cjs").writeText(
            "module.exports = true;",
            StandardCharsets.UTF_8,
        )

        val engine = NativeNodeEmbeddedJavaScriptEngine(context)
        var swapped = false
        engine.commonJsPreloadAfterSourceAuthorizationForTest = {
            check(packageDirectory.renameTo(movedPackageDirectory)) {
                "Failed to move the authorized package directory outside the preload root"
            }
            Os.symlink(movedPackageDirectory.canonicalPath, packageDirectory.absolutePath)
            swapped = true
        }
        try {
            val diagnostics = engine.prepareCommonJsPreloadDiagnosticsForTest(
                source = entrySource,
                sourceName = entryFile.canonicalPath,
                workingDirectory = root.canonicalPath,
            )

            assertTrue("the controlled intermediate-directory swap must run", swapped)
            assertFalse("a source opened through the escaped directory must not be preloaded", diagnostics.complete)
            assertEquals("source_read_failure", diagnostics.incompleteReason)
            assertEquals(0, diagnostics.moduleCount)
            assertTrue(diagnostics.sourceReadFailureCount > 0)
        } finally {
            engine.commonJsPreloadAfterSourceAuthorizationForTest = null
            if (swapped) {
                runCatching { packageDirectory.delete() }
                runCatching { movedPackageDirectory.renameTo(packageDirectory) }
            }
            root.deleteRecursively()
            outsideRoot.deleteRecursively()
        }
    }

    @Test
    fun directProviderReturnsPlainMissingAndScopedDenialResults() {
        val root = File(context.cacheDir, "embedded-engine-r4-provider-scope-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create direct provider scope dir: $absolutePath" }
        }
        val outside = File(context.cacheDir, "embedded-engine-r4-provider-outside-${System.nanoTime()}.cjs")
        val symlink = File(root, "outside-link.cjs")
        try {
            val plain = File(root, "plain.cjs").apply {
                writeText("module.exports = 'plain';", StandardCharsets.UTF_8)
            }
            outside.writeText("module.exports = 'outside';", StandardCharsets.UTF_8)
            Os.symlink(outside.canonicalPath, symlink.absolutePath)

            withDirectProvider(root) { fixture ->
                val plainResponse = fixture.provider.resolveModuleSource(
                    providerRequest(fixture.executionId, plain.absolutePath, "plain"),
                )
                assertProviderResponse(
                    response = plainResponse,
                    requestId = "plain",
                    status = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_NOT_ENCRYPTED,
                )
                assertEquals(plain.canonicalPath, providerResolvedPath(plainResponse))
                assertEquals(null, providerSourceDescriptor(plainResponse))

                val missingResponse = fixture.provider.resolveModuleSource(
                    providerRequest(
                        fixture.executionId,
                        File(root, "missing.cjs").absolutePath,
                        "missing",
                    ),
                )
                assertProviderResponse(
                    response = missingResponse,
                    requestId = "missing",
                    status = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_NOT_FOUND,
                )
                assertEquals("not_found", providerDenialReason(missingResponse))

                val outsideResponse = fixture.provider.resolveModuleSource(
                    providerRequest(fixture.executionId, outside.absolutePath, "outside"),
                )
                assertProviderDenied(outsideResponse, "outside", "canonical_path_escape")

                val symlinkResponse = fixture.provider.resolveModuleSource(
                    providerRequest(fixture.executionId, symlink.absolutePath, "symlink"),
                )
                assertProviderDenied(symlinkResponse, "symlink", "canonical_path_escape")

                val diagnostics = fixture.provider.nativeDiagnostics
                assertEquals("4", diagnostics.getString(MODULE_PROVIDER_REQUEST_COUNT_KEY))
                assertEquals("1", diagnostics.getString("embedded_script.module_provider.not_encrypted_count"))
                assertEquals("1", diagnostics.getString("embedded_script.module_provider.not_found_count"))
                assertEquals("2", diagnostics.getString("embedded_script.module_provider.denied_count"))
                assertEquals("0", diagnostics.getString("embedded_script.module_provider.decrypted_source_count"))
            }
        } finally {
            symlink.delete()
            root.deleteRecursively()
            outside.delete()
        }
    }

    @Test
    fun directProviderRejectsIntermediateSymlinkSwapAfterCanonicalization() {
        val root = File(context.cacheDir, "embedded-engine-r4-provider-race-${System.nanoTime()}").apply {
            check(mkdirs())
        }
        val insideDirectory = File(root, "branch").apply { check(mkdirs()) }
        val insideBackup = File(root, "branch-safe")
        val outsideDirectory = File(
            context.cacheDir,
            "embedded-engine-r4-provider-race-outside-${System.nanoTime()}",
        ).apply { check(mkdirs()) }
        val candidate = File(insideDirectory, "candidate.cjs").apply {
            writeText("module.exports = 'inside';", StandardCharsets.UTF_8)
        }
        File(outsideDirectory, candidate.name).writeText(
            "module.exports = 'outside';",
            StandardCharsets.UTF_8,
        )
        try {
            withDirectProvider(
                root = root,
                afterCanonicalizationForTest = {
                    check(insideDirectory.renameTo(insideBackup))
                    Os.symlink(outsideDirectory.canonicalPath, insideDirectory.absolutePath)
                },
                afterOpenForTest = {
                    check(insideDirectory.delete())
                    check(insideBackup.renameTo(insideDirectory))
                },
            ) { fixture ->
                val response = fixture.provider.resolveModuleSource(
                    providerRequest(fixture.executionId, candidate.absolutePath, "intermediate-swap"),
                )
                assertProviderDenied(response, "intermediate-swap", "opened_file_path_mismatch")
            }
        } finally {
            if (insideDirectory.isDirectory) {
                insideDirectory.deleteRecursively()
            } else {
                insideDirectory.delete()
            }
            if (insideBackup.exists()) {
                insideBackup.deleteRecursively()
            }
            root.deleteRecursively()
            outsideDirectory.deleteRecursively()
        }
    }

    @Test
    fun directProviderReturnsDecryptedPfdAndValidatesRequestIdentity() {
        val root = File(context.cacheDir, "embedded-engine-r4-provider-pfd-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create direct provider PFD dir: $absolutePath" }
        }
        try {
            withTestScriptEncryption {
                val expectedSource = "module.exports = { value: 'provider-pfd-ok' };"
                val encrypted = File(root, "encrypted.cjs")
                writeEncryptedScript(encrypted, expectedSource)
                withDirectProvider(root) { fixture ->
                    val response = fixture.provider.resolveModuleSource(
                        providerRequest(fixture.executionId, encrypted.absolutePath, "encrypted-pfd"),
                    )
                    assertProviderResponse(
                        response = response,
                        requestId = "encrypted-pfd",
                        status = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_DECRYPTED,
                    )
                    assertEquals(encrypted.canonicalPath, providerResolvedPath(response))
                    assertEquals(
                        expectedSource.toByteArray(StandardCharsets.UTF_8).size.toLong(),
                        response.getLong(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_SOURCE_BYTES),
                    )
                    val descriptor = providerSourceDescriptor(response)
                    assertNotNull("decrypted provider response must use a PFD", descriptor)
                    val actualSource = ParcelFileDescriptor.AutoCloseInputStream(checkNotNull(descriptor)).use { input ->
                        String(input.readBytes(), StandardCharsets.UTF_8)
                    }
                    assertEquals(expectedSource, actualSource)

                    val mismatchedIdentity = fixture.provider.resolveModuleSource(
                        providerRequest("different-execution", encrypted.absolutePath, "identity-mismatch"),
                    )
                    assertProviderDenied(
                        response = mismatchedIdentity,
                        requestId = "identity-mismatch",
                        denialReason = "execution_id_mismatch",
                    )

                    val versionMismatchRequest = providerRequest(
                        fixture.executionId,
                        encrypted.absolutePath,
                        "version-mismatch",
                    ).apply {
                        putInt(
                            NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION,
                            NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_CONTRACT_VERSION + 1,
                        )
                    }
                    val versionMismatch = fixture.provider.resolveModuleSource(versionMismatchRequest)
                    assertProviderError(
                        response = versionMismatch,
                        requestId = "version-mismatch",
                        status = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_FAILED,
                        errorCode = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_INVALID_REQUEST,
                        denialReason = "contract_version_mismatch",
                    )

                    val diagnostics = fixture.provider.nativeDiagnostics
                    assertEquals("3", diagnostics.getString(MODULE_PROVIDER_REQUEST_COUNT_KEY))
                    assertEquals("1", diagnostics.getString(MODULE_PROVIDER_DECRYPTED_SOURCE_COUNT_KEY))
                    assertEquals("1", diagnostics.getString("embedded_script.module_provider.denied_count"))
                    assertEquals("1", diagnostics.getString("embedded_script.module_provider.failed_count"))
                    assertEquals(
                        "0",
                        awaitProviderDiagnostic(
                            fixture.provider,
                            "embedded_script.module_provider.active_pipe_count",
                        ),
                    )
                }
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun directProviderReturnsCancellationTimeoutFailureAndBudgetErrors() {
        val root = File(context.cacheDir, "embedded-engine-r4-provider-errors-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create direct provider error dir: $absolutePath" }
        }
        try {
            withTestScriptEncryption {
                val encrypted = File(root, "encrypted.cjs")
                writeEncryptedScript(encrypted, "module.exports = 'budget';")
                val corrupt = File(root, "corrupt.cjs")
                writeCorruptEncryptedScript(corrupt)

                withDirectProvider(root) { fixture ->
                    fixture.provider.cancel("direct provider cancellation")
                    val cancelled = fixture.provider.resolveModuleSource(
                        providerRequest(fixture.executionId, encrypted.absolutePath, "cancelled"),
                    )
                    assertProviderError(
                        response = cancelled,
                        requestId = "cancelled",
                        status = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_CANCELLED,
                        errorCode = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_CANCELLED,
                        denialReason = "cancelled",
                    )
                }

                withDirectProvider(root, timeoutMs = 1L) { fixture ->
                    SystemClock.sleep(5L)
                    val timedOut = fixture.provider.resolveModuleSource(
                        providerRequest(fixture.executionId, encrypted.absolutePath, "timed-out"),
                    )
                    assertProviderError(
                        response = timedOut,
                        requestId = "timed-out",
                        status = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_TIMED_OUT,
                        errorCode = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_TIMED_OUT,
                        denialReason = "timed_out",
                    )
                }

                withDirectProvider(root) { fixture ->
                    val failed = fixture.provider.resolveModuleSource(
                        providerRequest(fixture.executionId, corrupt.absolutePath, "corrupt"),
                    )
                    assertProviderError(
                        response = failed,
                        requestId = "corrupt",
                        status = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_FAILED,
                        errorCode = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_FAILED,
                        denialReason = "decrypt_failed",
                    )
                    assertEquals(
                        "1",
                        fixture.provider.nativeDiagnostics.getString(
                            "embedded_script.module_provider.failed_count",
                        ),
                    )
                }

                withDirectProvider(
                    root = root,
                    initialSourceCount = NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_MAX_COUNT,
                ) { fixture ->
                    val budget = fixture.provider.resolveModuleSource(
                        providerRequest(fixture.executionId, encrypted.absolutePath, "budget"),
                    )
                    assertProviderError(
                        response = budget,
                        requestId = "budget",
                        status = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_FAILED,
                        errorCode = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_BUDGET_EXCEEDED,
                        denialReason = "budget_exceeded",
                    )
                    assertEquals(
                        "1",
                        fixture.provider.nativeDiagnostics.getString(
                            "embedded_script.module_provider.budget_rejected_count",
                        ),
                    )
                }
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private inline fun <T> withDirectProvider(
        root: File,
        timeoutMs: Long = DIRECT_PROVIDER_TIMEOUT_MS,
        initialSourceCount: Int = 0,
        initialSourceBytes: Long = 0L,
        noinline afterCanonicalizationForTest: (() -> Unit)? = null,
        noinline afterOpenForTest: (() -> Unit)? = null,
        block: (DirectProviderFixture) -> T,
    ): T {
        val executionId = "r4-direct-provider-${System.nanoTime()}"
        val provider = NodeJsEncryptedModuleSourceProvider(
            rootDirectory = root,
            executionId = executionId,
            timeoutMs = timeoutMs,
            initialSourceCount = initialSourceCount,
            initialSourceBytes = initialSourceBytes,
            afterCanonicalizationForTest = afterCanonicalizationForTest,
            afterOpenForTest = afterOpenForTest,
        )
        return try {
            block(DirectProviderFixture(executionId, provider))
        } finally {
            provider.cancel("direct provider test cleanup")
        }
    }

    private fun providerRequest(
        executionId: String,
        path: String,
        requestId: String,
        timeoutMs: Long = DIRECT_PROVIDER_TIMEOUT_MS,
    ): Bundle = Bundle().apply {
        putInt(
            NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION,
            NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_CONTRACT_VERSION,
        )
        putString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_REQUEST_ID, requestId)
        putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, executionId)
        putString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_PATH, path)
        putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, timeoutMs)
    }

    private fun assertProviderResponse(
        response: Bundle,
        requestId: String,
        status: String,
    ) {
        assertEquals(
            NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_CONTRACT_VERSION,
            response.getInt(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION),
        )
        assertEquals(
            requestId,
            response.getString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_REQUEST_ID),
        )
        assertEquals(status, response.getString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_STATUS))
        assertTrue(
            "provider response elapsed time must be non-negative",
            response.getLong(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_ELAPSED_MS) >= 0L,
        )
    }

    private fun assertProviderDenied(
        response: Bundle,
        requestId: String,
        denialReason: String,
    ) {
        assertProviderError(
            response = response,
            requestId = requestId,
            status = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_DENIED,
            errorCode = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_DENIED,
            denialReason = denialReason,
        )
    }

    private fun assertProviderError(
        response: Bundle,
        requestId: String,
        status: String,
        errorCode: String,
        denialReason: String,
    ) {
        assertProviderResponse(response, requestId, status)
        assertEquals(errorCode, response.getString(NodeJsRuntimeContract.KEY_ERROR_CODE))
        assertEquals(denialReason, providerDenialReason(response))
        assertTrue(
            "provider error response must include a message",
            response.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE).orEmpty().isNotBlank(),
        )
        assertEquals(null, providerSourceDescriptor(response))
    }

    private fun providerResolvedPath(response: Bundle): String =
        response.getString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_RESOLVED_PATH).orEmpty()

    private fun providerDenialReason(response: Bundle): String =
        response.getString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_DENIAL_REASON).orEmpty()

    @Suppress("DEPRECATION")
    private fun providerSourceDescriptor(response: Bundle): ParcelFileDescriptor? =
        response.getParcelable(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_SOURCE_FD)
                as? ParcelFileDescriptor

    private fun awaitProviderDiagnostic(
        provider: NodeJsEncryptedModuleSourceProvider,
        key: String,
        expected: String = "0",
    ): String? {
        val deadline = SystemClock.elapsedRealtime() + DIRECT_PROVIDER_DIAGNOSTIC_WAIT_MS
        var actual: String?
        do {
            actual = provider.nativeDiagnostics.getString(key)
            if (actual == expected) {
                return actual
            }
            SystemClock.sleep(DIRECT_PROVIDER_DIAGNOSTIC_POLL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)
        return actual
    }

    private fun executeFile(root: File, sourceFile: File, label: String): EngineRun {
        val engine = NativeNodeEmbeddedJavaScriptEngine(context).apply {
            setTag(ScriptEngine.TAG_WORKING_DIRECTORY, root.canonicalPath)
        }
        assertEquals("$label engine result", 0, engine.doExecution(JavaScriptFileSource(sourceFile)))
        return EngineRun(
            diagnostics = engine.lastRequestPreparationDiagnostics,
            nativeValues = engine.lastEmbeddedScriptResultNativeValues,
        )
    }

    private fun assertOnDemandProviderWithoutDirectoryScan(
        label: String,
        run: EngineRun,
    ): ModuleProviderDiagnostics {
        val diagnostics = run.diagnostics
        val scan = diagnostics.encryptedRuntimeModuleScan
        assertTrue("$label request should be prepared", diagnostics.prepared)
        assertFalse("$label must not require a legacy directory scan", scan.required)
        assertFalse("$label must not perform a legacy directory scan", scan.performed)
        assertEquals("on_demand_provider", scan.decision)
        assertEquals("skipped", scan.status)
        assertEquals(0, scan.examinedPathCount)
        assertEquals(0, scan.walkedFileCount)
        assertEquals(0, scan.candidateFileCount)
        assertEquals(0, scan.headerProbeCount)
        assertEquals(0L, scan.headerBytesRead)
        assertEquals(0, scan.fullFileReadCount)
        assertEquals(0L, scan.fullFileBytesRead)
        assertTrue(
            "$label request preparation regressed to ${diagnostics.elapsedMs}ms",
            diagnostics.elapsedMs <= REQUEST_PREPARATION_MAX_MS,
        )
        assertEquals(
            "on_demand_provider",
            run.nativeValues["embedded_script.engine_encrypted_runtime_scan.decision"],
        )
        assertEquals(
            "0",
            run.nativeValues["embedded_script.engine_encrypted_runtime_scan.walked_file_count"],
        )
        assertEquals(
            "0",
            run.nativeValues["embedded_script.engine_encrypted_runtime_scan.candidate_file_count"],
        )
        assertEquals(
            diagnostics.elapsedMs.toString(),
            run.nativeValues["timing.engine_request_preparation.ms"],
        )
        return assertModuleProviderDiagnostics(label, run.nativeValues)
    }

    private fun assertSingleOnDemandEncryptedCandidate(label: String, run: EngineRun) {
        val provider = assertOnDemandProviderWithoutDirectoryScan(label, run)
        assertEquals("$label should request only the resolved candidate", 1L, provider.requestCount)
        assertEquals("$label should resolve one provider source", 1L, provider.resolvedSourceCount)
        assertEquals("$label should decrypt one provider source", 1L, provider.decryptedSourceCount)
        assertTrue("$label should report decrypted UTF-8 bytes", provider.sourceBytes > 0L)
        assertTrue("$label provider elapsed time must be non-negative", provider.elapsedMs >= 0L)
        assertTrue("$label successful provider lookup must not report denial", provider.denialReason.isEmpty())
    }

    private fun assertModuleProviderDiagnostics(
        label: String,
        nativeValues: Map<String, String>,
    ): ModuleProviderDiagnostics {
        fun requiredLong(key: String): Long {
            val raw = nativeValues[key]
            assertNotNull("$label must export $key", raw)
            return raw?.toLongOrNull()
                ?: throw AssertionError("$label must export numeric $key, actual=$raw")
        }

        val requestCount = requiredLong(MODULE_PROVIDER_REQUEST_COUNT_KEY)
        val resolvedSourceCount = requiredLong(MODULE_PROVIDER_RESOLVED_SOURCE_COUNT_KEY)
        val decryptedSourceCount = requiredLong(MODULE_PROVIDER_DECRYPTED_SOURCE_COUNT_KEY)
        val sourceBytes = requiredLong(MODULE_PROVIDER_SOURCE_BYTES_KEY)
        val elapsedMs = requiredLong(MODULE_PROVIDER_ELAPSED_MS_KEY)
        val denialReason = nativeValues[MODULE_PROVIDER_DENIAL_REASON_KEY]
        assertNotNull("$label must export $MODULE_PROVIDER_DENIAL_REASON_KEY", denialReason)
        assertEquals(
            "$label must keep the legacy directory fallback disabled",
            "false",
            nativeValues[MODULE_PROVIDER_LEGACY_SCAN_KEY],
        )
        assertTrue("$label provider request count must be non-negative", requestCount >= 0L)
        assertTrue("$label provider resolved count must be non-negative", resolvedSourceCount >= 0L)
        assertTrue("$label provider decrypted count must be non-negative", decryptedSourceCount >= 0L)
        assertTrue("$label provider bytes must be non-negative", sourceBytes >= 0L)
        assertTrue("$label provider elapsed time must be non-negative", elapsedMs >= 0L)
        assertTrue(
            "$label cannot decrypt more sources than it resolves",
            decryptedSourceCount <= resolvedSourceCount,
        )
        assertTrue(
            "$label cannot resolve more sources than it requests",
            resolvedSourceCount <= requestCount,
        )
        return ModuleProviderDiagnostics(
            requestCount = requestCount,
            resolvedSourceCount = resolvedSourceCount,
            decryptedSourceCount = decryptedSourceCount,
            sourceBytes = sourceBytes,
            elapsedMs = elapsedMs,
            denialReason = denialReason.orEmpty(),
        )
    }

    private fun writePackage(
        root: File,
        packageName: String,
        source: String,
        encrypted: Boolean,
    ) {
        val packageRoot = File(root, "node_modules/$packageName").apply {
            check(mkdirs() || isDirectory) { "Failed to create package dir: $absolutePath" }
        }
        File(packageRoot, "package.json").writeText(
            """{"name":"$packageName","main":"./lib/index.cjs"}""",
            StandardCharsets.UTF_8,
        )
        val module = File(packageRoot, "lib/index.cjs")
        if (encrypted) {
            writeEncryptedScript(module, source)
        } else {
            module.parentFile?.let { parent -> check(parent.mkdirs() || parent.isDirectory) }
            module.writeText(source, StandardCharsets.UTF_8)
        }
    }

    private fun writeEncryptedScript(file: File, source: String) {
        file.parentFile?.let { parent -> check(parent.mkdirs() || parent.isDirectory) }
        val encrypted = AdvancedEncryptionStandard(
            TEST_ENCRYPTION_KEY.toByteArray(StandardCharsets.UTF_8),
            TEST_ENCRYPTION_VECTOR,
        ).encrypt(source.toByteArray(StandardCharsets.UTF_8))
        file.outputStream().use { output ->
            EncryptedScriptFileHeader.writeHeader(
                output,
                EncryptedScriptFileHeader.FLAG_EXECUTION_MODE_NODEJS,
            )
            output.write(encrypted)
        }
    }

    private fun writeCorruptEncryptedScript(file: File) {
        file.parentFile?.let { parent -> check(parent.mkdirs() || parent.isDirectory) }
        file.outputStream().use { output ->
            EncryptedScriptFileHeader.writeHeader(
                output,
                EncryptedScriptFileHeader.FLAG_EXECUTION_MODE_NODEJS,
            )
            output.write(ByteArray(CORRUPT_CIPHERTEXT_BYTES) { index -> (index + 1).toByte() })
        }
    }

    private inline fun <T> withTestScriptEncryption(block: () -> T): T {
        val keyField = ScriptEncryption::class.java.getDeclaredField("mKey").apply { isAccessible = true }
        val vectorField = ScriptEncryption::class.java.getDeclaredField("mInitVector").apply { isAccessible = true }
        val previousKey = keyField.get(null)
        val previousVector = vectorField.get(null)
        keyField.set(null, TEST_ENCRYPTION_KEY)
        vectorField.set(null, TEST_ENCRYPTION_VECTOR)
        return try {
            block()
        } finally {
            keyField.set(null, previousKey)
            vectorField.set(null, previousVector)
        }
    }

    private fun createLargeWorkingDirectory(): LargeWorkingDirectory {
        val root = File(context.cacheDir, "embedded-engine-r4-large-cwd-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create R4 request-preparation test dir: $absolutePath" }
        }
        repeat(FILLER_FILE_COUNT) { index ->
            val branch = if (index % 2 == 0) {
                "node_modules/unrelated-${index % 64}/lib/shard-${index % 8}"
            } else {
                "workspace/shard-${index % 64}/nested-${index % 8}"
            }
            val extension = FILLER_EXTENSIONS[index % FILLER_EXTENSIONS.size]
            File(root, "$branch/unrelated-$index.$extension").apply {
                parentFile?.let { parent -> check(parent.mkdirs() || parent.isDirectory) }
                writeText("// R4 unrelated module candidate $index\n", StandardCharsets.UTF_8)
            }
        }
        return LargeWorkingDirectory(root = root, fillerFileCount = FILLER_FILE_COUNT)
    }

    private data class EngineRun(
        val diagnostics: NativeNodeEmbeddedJavaScriptEngine.RequestPreparationDiagnostics,
        val nativeValues: Map<String, String>,
    )

    private data class DirectProviderFixture(
        val executionId: String,
        val provider: NodeJsEncryptedModuleSourceProvider,
    )

    private data class ModuleProviderDiagnostics(
        val requestCount: Long,
        val resolvedSourceCount: Long,
        val decryptedSourceCount: Long,
        val sourceBytes: Long,
        val elapsedMs: Long,
        val denialReason: String,
    )

    private data class LargeWorkingDirectory(
        val root: File,
        val fillerFileCount: Int,
    )

    private companion object {
        const val FILLER_FILE_COUNT = 4_000
        const val REQUEST_PREPARATION_MAX_MS = 250L
        const val DIRECT_PROVIDER_TIMEOUT_MS = 5_000L
        const val DIRECT_PROVIDER_DIAGNOSTIC_WAIT_MS = 1_000L
        const val DIRECT_PROVIDER_DIAGNOSTIC_POLL_MS = 5L
        const val CORRUPT_CIPHERTEXT_BYTES = 15
        const val TEST_ENCRYPTION_KEY = "0123456789abcdef0123456789abcdef"
        const val TEST_ENCRYPTION_VECTOR = "0123456789abcdef"

        const val MODULE_PROVIDER_REQUEST_COUNT_KEY = "embedded_script.module_provider.request_count"
        const val MODULE_PROVIDER_RESOLVED_SOURCE_COUNT_KEY =
            "embedded_script.module_provider.resolved_source_count"
        const val MODULE_PROVIDER_DECRYPTED_SOURCE_COUNT_KEY =
            "embedded_script.module_provider.decrypted_source_count"
        const val MODULE_PROVIDER_SOURCE_BYTES_KEY = "embedded_script.module_provider.source_bytes"
        const val MODULE_PROVIDER_ELAPSED_MS_KEY = "embedded_script.module_provider.elapsed_ms"
        const val MODULE_PROVIDER_DENIAL_REASON_KEY = "embedded_script.module_provider.denial_reason"
        const val MODULE_PROVIDER_LEGACY_SCAN_KEY =
            "embedded_script.module_provider.legacy_directory_scan_ran"

        val FILLER_EXTENSIONS = listOf("js", "json", "ts", "mjs", "cjs")
    }
}
