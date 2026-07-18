package org.autojs.autojs.engine

import android.system.Os
import org.autojs.autojs.engine.encryption.AdvancedEncryptionStandard
import org.autojs.autojs.engine.encryption.ScriptEncryption
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs6.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.mozilla.javascript.Context as RhinoContext
import java.io.File
import java.io.RandomAccessFile
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
    fun realFileEngineSkipsLargeCwdOnlyForStrictTrivialEntry() {
        val fixture = createLargeWorkingDirectory()
        try {
            val simple = File(fixture.root, "simple.js").apply {
                writeText("\"nodejs\";\nconsole.log(1);", StandardCharsets.UTF_8)
            }
            val simpleRun = executeFile(fixture.root, simple, "simple")
            assertSkippedStaticScan(
                label = "simple",
                run = simpleRun,
                expectedStaticRequireCount = 0,
                expectedModuleCount = 0,
            )

            File(fixture.root, "static-child.js").writeText(
                """exports.value = "static-ok";""",
                StandardCharsets.UTF_8,
            )
            val staticEntry = File(fixture.root, "static-entry.js").apply {
                writeText(
                    """
                    "nodejs";
                    const child = require("./static-child");
                    if (child.value !== "static-ok") throw new Error("static require failed");
                    """.trimIndent(),
                    StandardCharsets.UTF_8,
                )
            }
            val staticRun = executeFile(fixture.root, staticEntry, "static require")
            assertRetainedHeaderOnlyScan(
                label = "static require",
                run = staticRun,
                expectedDecision = "conservative_source",
                minimumCandidateCount = fixture.fillerFileCount,
            )
            assertEquals(1, staticRun.diagnostics.commonJs.staticRequireCount)
            assertEquals(1, staticRun.diagnostics.commonJs.moduleCount)
            assertTrue(staticRun.diagnostics.commonJs.complete)

            File(fixture.root, "dynamic-child.js").writeText(
                """exports.value = "dynamic-ok";""",
                StandardCharsets.UTF_8,
            )
            val dynamicEntry = File(fixture.root, "dynamic-entry.js").apply {
                writeText(
                    """
                    "nodejs";
                    const childName = "./dynamic-child";
                    const child = require(childName);
                    if (child.value !== "dynamic-ok") throw new Error("dynamic require failed");
                    """.trimIndent(),
                    StandardCharsets.UTF_8,
                )
            }
            val dynamicRun = executeFile(fixture.root, dynamicEntry, "dynamic require")
            assertRetainedHeaderOnlyScan(
                label = "dynamic require",
                run = dynamicRun,
                expectedDecision = "dynamic_require",
                minimumCandidateCount = fixture.fillerFileCount,
            )
            assertEquals(1, dynamicRun.diagnostics.commonJs.dynamicRequireIgnoredCount)
            assertFalse(dynamicRun.diagnostics.isEsmEntry)

            if (BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED) {
                File(fixture.root, "esm-child.mjs").writeText(
                    """export const value = "esm-ok";""",
                    StandardCharsets.UTF_8,
                )
                val esmEntry = File(fixture.root, "esm-entry.mjs").apply {
                    writeText(
                        """
                        import { value } from "./esm-child.mjs";
                        if (value !== "esm-ok") throw new Error("ESM import failed");
                        """.trimIndent(),
                        StandardCharsets.UTF_8,
                    )
                }
                val esmRun = executeFile(fixture.root, esmEntry, "ESM")
                assertRetainedHeaderOnlyScan(
                    label = "ESM",
                    run = esmRun,
                    expectedDecision = "esm_entry",
                    minimumCandidateCount = fixture.fillerFileCount,
                )
                assertTrue(esmRun.diagnostics.isEsmEntry)
                assertEquals(0, esmRun.diagnostics.commonJs.moduleCount)
            }
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun encryptedRuntimeFallbackPreservesEsmShapes() {
        assumeTrue(
            "Build with Embedded Node ESM enabled for encrypted graph regression.",
            BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED,
        )
        val root = File(context.cacheDir, "embedded-engine-encrypted-graph-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create encrypted graph test dir: $absolutePath" }
        }
        try {
            withTestScriptEncryption {
                writeEncryptedScript(
                    File(root, "source-esm-target.mjs"),
                    """export const value = "source-esm-ok";""",
                )
                writeEncryptedScript(
                    File(root, "unresolved-target.mjs"),
                    """export default "unresolved-ok";""",
                )

                val sourceEsmEntry = File(root, "source-esm-entry.js").apply {
                    writeText(
                        """
                        "nodejs";
                        import { value } from "./source-esm-target.mjs";
                        if (value !== "source-esm-ok") throw new Error("source-level ESM encrypted target failed");
                        """.trimIndent(),
                        StandardCharsets.UTF_8,
                    )
                }
                val sourceEsmRun = executeFile(root, sourceEsmEntry, "source-level ESM encrypted target")
                assertEncryptedFallback("source-level ESM", sourceEsmRun, "esm_entry")
                assertTrue(sourceEsmRun.diagnostics.isEsmEntry)
                assertTrue(sourceEsmRun.diagnostics.commonJs.sourceLevelEsmSyntaxCount > 0)

                val unresolvedEntry = File(root, "unresolved-entry.cjs").apply {
                    writeText(
                        """
                        "nodejs";
                        const target = require("./unresolved-target.mjs");
                        if (target.default !== "unresolved-ok") throw new Error("unresolved encrypted target failed");
                        """.trimIndent(),
                        StandardCharsets.UTF_8,
                    )
                }
                val unresolvedRun = executeFile(root, unresolvedEntry, "unresolved encrypted target")
                assertEncryptedFallback("unresolved static require", unresolvedRun, "unresolved_static_require")
                assertTrue(unresolvedRun.diagnostics.commonJs.unresolvedStaticRequireCount > 0)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun encryptedRuntimeFallbackSupportsCreateRequireWithoutEsmFlags() {
        val root = File(context.cacheDir, "embedded-engine-encrypted-create-require-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create encrypted createRequire test dir: $absolutePath" }
        }
        try {
            withTestScriptEncryption {
                writeEncryptedScript(
                    File(root, "create-require-target.cjs"),
                    """module.exports = { value: "create-require-ok" };""",
                )
                val createRequireEntry = File(root, "create-require-entry.cjs").apply {
                    writeText(
                        """
                        "nodejs";
                        const { createRequire } = require("module");
                        const load = createRequire(__filename);
                        const target = load("./create-require-target.cjs");
                        if (target.value !== "create-require-ok") throw new Error("createRequire encrypted target failed");
                        """.trimIndent(),
                        StandardCharsets.UTF_8,
                    )
                }
                val createRequireRun = executeFile(root, createRequireEntry, "createRequire encrypted target")
                assertEncryptedFallback("createRequire", createRequireRun, "create_require")
                assertTrue(createRequireRun.diagnostics.commonJs.createRequireReferenceCount > 0)
                assertEquals(
                    "builtin module authority must not be counted as unresolved",
                    0,
                    createRequireRun.diagnostics.commonJs.unresolvedStaticRequireCount,
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun encryptedRuntimeFallbackSupportsDynamicImportWhenEnabled() {
        assumeTrue(
            "Build with Embedded Node ESM and local dynamic import enabled for this dynamic-import case.",
            BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED &&
                    BuildConfig.NODEJS_DYNAMIC_IMPORT_LOCAL_ENABLED &&
                    BuildConfig.NODEJS_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED,
        )
        val root = File(context.cacheDir, "embedded-engine-encrypted-dynamic-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create encrypted dynamic-import test dir: $absolutePath" }
        }
        try {
            withTestScriptEncryption {
                writeEncryptedScript(
                    File(root, "dynamic-target.mjs"),
                    """export const value = "dynamic-ok";""",
                )
                val dynamicMarker = File(root, "dynamic-import-ok.txt")
                val dynamicEntry = File(root, "dynamic-import-entry.cjs").apply {
                    writeText(
                        """
                        "nodejs";
                        const fs = require("fs");
                        (async () => {
                          const loaded = await import("./dynamic-target.mjs");
                          if (loaded.value !== "dynamic-ok") throw new Error("dynamic encrypted target failed");
                          fs.writeFileSync("./${dynamicMarker.name}", "ok", "utf8");
                        })().catch((error) => setImmediate(() => { throw error; }));
                        """.trimIndent(),
                        StandardCharsets.UTF_8,
                    )
                }
                val dynamicRun = executeFile(root, dynamicEntry, "dynamic import encrypted target")
                assertEncryptedFallback("dynamic import", dynamicRun, "dynamic_import")
                assertTrue(dynamicRun.diagnostics.commonJs.dynamicImportCount > 0)
                assertEquals("ok", dynamicMarker.readText(StandardCharsets.UTF_8))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun computedAndDivisionLoadersActuallyLoadEncryptedModules() {
        val root = File(context.cacheDir, "embedded-engine-encrypted-computed-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create encrypted computed-loader test dir: $absolutePath" }
        }
        try {
            withTestScriptEncryption {
                writeEncryptedScript(
                    File(root, "computed-target.cjs"),
                    """module.exports = { value: "computed-ok" };""",
                )
                writeEncryptedScript(
                    File(root, "division-target.cjs"),
                    """module.exports = { value: "division-ok" };""",
                )
                val computedEntry = File(root, "computed-entry.cjs").apply {
                    writeText(
                        """
                        "nodejs";
                        if (false) {
                          globalThis["require"]("./computed-target.cjs");
                          Reflect.get(module, "require")("./computed-target.cjs");
                          process.getBuiltinModule("module");
                        }
                        const target = module["require"]("./computed-target.cjs");
                        if (target.value !== "computed-ok") throw new Error("computed encrypted target failed");
                        """.trimIndent(),
                        StandardCharsets.UTF_8,
                    )
                }
                val computedRun = executeFile(root, computedEntry, "computed encrypted loader")
                assertEncryptedFallback("computed encrypted loader", computedRun, "unclassified_loader")

                val divisionEntry = File(root, "division-entry.cjs").apply {
                    writeText(
                        """
                        "nodejs";
                        const targetName = "./division-target.cjs";
                        const quotient = 8 / 2;
                        const target = require(targetName);
                        if (quotient !== 4 || target.value !== "division-ok") {
                          throw new Error("division/dynamic encrypted target failed");
                        }
                        """.trimIndent(),
                        StandardCharsets.UTF_8,
                    )
                }
                val divisionRun = executeFile(root, divisionEntry, "division and dynamic loader")
                assertEncryptedFallback("division and dynamic loader", divisionRun, "dynamic_require")
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun nonTrivialCjsStaysConservativeWithoutMisclassifyingExportMethod() {
        val root = File(context.cacheDir, "embedded-engine-conservative-cjs-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create conservative CommonJS test dir: $absolutePath" }
        }
        try {
            File(root, "child.js").writeText(
                """module.exports = { value: "child-ok" };""",
                StandardCharsets.UTF_8,
            )
            val exportMethodEntry = File(root, "export-method.cjs").apply {
                writeText(
                    """
                    "nodejs";
                    const helper = { export() { return "method-ok"; } };
                    const fs = require("fs");
                    const child = require("./child");
                    if (helper.export() !== "method-ok" || child.value !== "child-ok" || !fs) {
                      throw new Error("CommonJS export method failed");
                    }
                    """.trimIndent(),
                    StandardCharsets.UTF_8,
                )
            }
            val exportMethodRun = executeFile(root, exportMethodEntry, "CommonJS export method")
            val exportDiagnostics = exportMethodRun.diagnostics
            assertFalse("object export() must not classify the entry as ESM", exportDiagnostics.isEsmEntry)
            assertEquals(0, exportDiagnostics.commonJs.sourceLevelEsmSyntaxCount)
            assertEquals(2, exportDiagnostics.commonJs.staticRequireCount)
            assertEquals("builtin require must not be unresolved", 0, exportDiagnostics.commonJs.unresolvedStaticRequireCount)
            assertEquals(1, exportDiagnostics.commonJs.moduleCount)
            assertEquals(
                "conservative_source",
                exportDiagnostics.encryptedRuntimeModuleScan.decision,
            )
            assertTrue(exportDiagnostics.encryptedRuntimeModuleScan.required)

            val dynamicCodeEntry = File(root, "dynamic-code-dead-branch.cjs").apply {
                writeText(
                    """
                    "nodejs";
                    if (false) {
                      const indirectEval = eval;
                      const IndirectFunction = Function;
                      const indirectConstructor = (() => {}).constructor;
                    }
                    console.log(1);
                    """.trimIndent(),
                    StandardCharsets.UTF_8,
                )
            }
            val dynamicCodeRun = executeFile(root, dynamicCodeEntry, "dead-branch dynamic code")
            assertTrue(dynamicCodeRun.diagnostics.commonJs.dynamicCodeReferenceCount >= 3)
            assertTrue(dynamicCodeRun.diagnostics.encryptedRuntimeModuleScan.required)
            assertEquals("dynamic_code", dynamicCodeRun.diagnostics.encryptedRuntimeModuleScan.decision)

            val builtinSubpathEntry = File(root, "builtin-subpaths.cjs").apply {
                writeText(
                    """
                    "nodejs";
                    if (false) {
                      require("assert/strict");
                      require("path/posix");
                      require("timers/promises");
                      require("stream/consumers");
                      require("stream/promises");
                      require("stream/web");
                    }
                    console.log(1);
                    """.trimIndent(),
                    StandardCharsets.UTF_8,
                )
            }
            val builtinSubpathRun = executeFile(root, builtinSubpathEntry, "builtin subpaths")
            assertEquals(6, builtinSubpathRun.diagnostics.commonJs.staticRequireCount)
            assertEquals(0, builtinSubpathRun.diagnostics.commonJs.unresolvedStaticRequireCount)
            assertEquals(0, builtinSubpathRun.diagnostics.commonJs.moduleCount)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun outputBudgetBoundariesHaveExplicitIncompleteStatuses() {
        val engine = NativeNodeEmbeddedJavaScriptEngine(context)
        assertEquals(
            null,
            engine.encryptedRuntimeModuleOutputBudgetStatus(
                NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_MAX_COUNT - 1,
                NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCES_MAX_BYTES.toLong() - 1L,
            ),
        )
        assertEquals(
            "incomplete_output_count_budget",
            engine.encryptedRuntimeModuleOutputBudgetStatus(
                NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_MAX_COUNT,
                0L,
            ),
        )
        assertEquals(
            "incomplete_output_bytes_budget",
            engine.encryptedRuntimeModuleOutputBudgetStatus(
                0,
                NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCES_MAX_BYTES.toLong(),
            ),
        )
        assertEquals(
            null,
            engine.encryptedRuntimeModuleCleartextBudgetStatus(
                NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_MAX_BYTES,
                NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_MAX_BYTES,
            ),
        )
        assertEquals(
            "incomplete_module_size_budget",
            engine.encryptedRuntimeModuleCleartextBudgetStatus(
                NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_MAX_BYTES + 1,
                NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_MAX_BYTES,
            ),
        )
    }

    @Test
    fun encryptedScanBoundsPayloadAndPrunesSymlinkTrees() {
        val root = File(context.cacheDir, "embedded-engine-bounded-scan-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create bounded scan test dir: $absolutePath" }
        }
        val outside = File(context.cacheDir, "embedded-engine-bounded-outside-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create bounded scan outside dir: $absolutePath" }
        }
        val outsideLink = File(root, "outside-link")
        val cycleLink = File(root, "cycle-link")
        try {
            File(outside, "must-not-be-walked.js").writeText("throw new Error('outside tree walked');")
            Os.symlink(outside.canonicalPath, outsideLink.absolutePath)
            Os.symlink(root.canonicalPath, cycleLink.absolutePath)
            File(root, "plain-target.js").writeText("module.exports = { value: 'ok' };")
            val oversized = File(root, "oversized-encrypted.js")
            oversized.outputStream().use { output ->
                EncryptedScriptFileHeader.writeHeader(output)
            }
            RandomAccessFile(oversized, "rw").use { file ->
                file.setLength(
                    EncryptedScriptFileHeader.BLOCK_SIZE.toLong() +
                            NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_MAX_BYTES +
                            AES_CIPHER_BLOCK_BYTES + 1L,
                )
            }
            val entry = File(root, "dynamic-entry.cjs").apply {
                writeText(
                    """
                    "nodejs";
                    const targetName = "./plain-target";
                    const target = require(targetName);
                    if (target.value !== "ok") throw new Error("bounded scan target failed");
                    """.trimIndent(),
                    StandardCharsets.UTF_8,
                )
            }

            val run = executeFile(root, entry, "bounded encrypted scan")
            val scan = run.diagnostics.encryptedRuntimeModuleScan
            assertEquals("dynamic_require", scan.decision)
            assertTrue(scan.performed)
            assertEquals("incomplete_module_size_budget", scan.status)
            assertTrue("outside and cyclic symlink directories should be pruned", scan.prunedDirectoryCount >= 2)
            assertEquals(0, scan.outsidePathRejectedCount)
            assertEquals(1, scan.oversizedCiphertextCount)
            assertEquals(1, scan.encryptedModuleBudgetRejectedCount)
            assertEquals(0, scan.fullFileReadCount)
            assertEquals(0L, scan.fullFileBytesRead)
            assertEquals(0, scan.decryptFailureCount)
        } finally {
            outsideLink.delete()
            cycleLink.delete()
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test
    fun encryptedScanRejectsRootIdentityDriftAndDropsCollectedSources() {
        val root = File(context.cacheDir, "embedded-engine-root-identity-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create root identity test dir: $absolutePath" }
        }
        val movedRoot = File(root.parentFile, root.name + "-moved")
        try {
            withTestScriptEncryption {
                writeEncryptedScript(
                    File(root, "identity-target.cjs"),
                    """module.exports = { value: "must-not-survive-root-drift" };""",
                )
                val entry = File(root, "identity-entry.cjs").apply {
                    writeText(
                        """
                        "nodejs";
                        const targetName = "./identity-target.cjs";
                        require(targetName);
                        """.trimIndent(),
                        StandardCharsets.UTF_8,
                    )
                }
                val engine = NativeNodeEmbeddedJavaScriptEngine(context).apply {
                    setTag(ScriptEngine.TAG_WORKING_DIRECTORY, root.canonicalPath)
                    encryptedRuntimeScanBeforeFinalRootIdentityCheckForTest = {
                        check(root.renameTo(movedRoot)) { "Failed to move scan root" }
                        check(root.mkdirs()) { "Failed to replace scan root" }
                    }
                }
                val failure = try {
                    runCatching { engine.doExecution(JavaScriptFileSource(entry)) }.exceptionOrNull()
                } finally {
                    engine.encryptedRuntimeScanBeforeFinalRootIdentityCheckForTest = null
                }
                assertTrue("root drift must discard collected encrypted sources", failure != null)
                val scan = engine.lastRequestPreparationDiagnostics.encryptedRuntimeModuleScan
                assertEquals("incomplete_identity_failure", scan.status)
                assertTrue(scan.identityMismatchCount > 0)
                assertTrue("fixture must collect an encrypted source before drift", scan.encryptedModuleCount > 0)
                assertEquals(
                    "collected sources must be removed from the engine request after root drift",
                    0,
                    scan.retainedEncryptedModuleCount,
                )
            }
        } finally {
            root.deleteRecursively()
            movedRoot.deleteRecursively()
        }
    }

    @Test
    fun entrySourceReadFailureIsExplicitWhileEmptyFileRemainsValid() {
        val root = File(context.cacheDir, "embedded-engine-source-read-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create source-read test dir: $absolutePath" }
        }
        try {
            val emptyEntry = File(root, "empty.cjs").apply { writeBytes(ByteArray(0)) }
            val emptyEngine = NativeNodeEmbeddedJavaScriptEngine(context).apply {
                setTag(ScriptEngine.TAG_WORKING_DIRECTORY, root.canonicalPath)
            }
            assertEquals(0, emptyEngine.doExecution(JavaScriptFileSource(emptyEntry)))

            val oversizedEntry = File(root, "oversized.cjs")
            RandomAccessFile(oversizedEntry, "rw").use { file ->
                file.setLength(
                    NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_MAX_BYTES.toLong() +
                            EncryptedScriptFileHeader.BLOCK_SIZE +
                            AES_CIPHER_BLOCK_BYTES + 1L,
                )
            }
            val oversizedEngine = NativeNodeEmbeddedJavaScriptEngine(context).apply {
                setTag(ScriptEngine.TAG_WORKING_DIRECTORY, root.canonicalPath)
            }
            val failure = runCatching {
                oversizedEngine.doExecution(JavaScriptFileSource(oversizedEntry))
            }.exceptionOrNull()
            assertTrue("oversized entry must fail instead of running an empty script", failure != null)
            assertTrue(failure?.message.orEmpty(), failure?.message.orEmpty().contains("Unable to read JavaScript source"))
        } finally {
            root.deleteRecursively()
        }
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

    private fun assertSkippedStaticScan(
        label: String,
        run: EngineRun,
        expectedStaticRequireCount: Int,
        expectedModuleCount: Int,
    ) {
        val diagnostics = run.diagnostics
        val scan = diagnostics.encryptedRuntimeModuleScan
        assertTrue("$label request should be prepared", diagnostics.prepared)
        assertFalse("$label should be CommonJS", diagnostics.isEsmEntry)
        assertEquals(expectedStaticRequireCount, diagnostics.commonJs.staticRequireCount)
        assertEquals(0, diagnostics.commonJs.dynamicRequireIgnoredCount)
        assertEquals(0, diagnostics.commonJs.unresolvedStaticRequireCount)
        assertEquals(0, diagnostics.commonJs.sourceLevelEsmSyntaxCount)
        assertEquals(0, diagnostics.commonJs.dynamicImportCount)
        assertEquals(0, diagnostics.commonJs.createRequireReferenceCount)
        assertEquals(0, diagnostics.commonJs.unclassifiedLoaderReferenceCount)
        assertEquals(0, diagnostics.commonJs.dynamicCodeReferenceCount)
        assertTrue("$label should satisfy the strict trivial proof", diagnostics.commonJs.trivialModuleFreeEntry)
        assertTrue(diagnostics.commonJs.complete)
        assertEquals(expectedModuleCount, diagnostics.commonJs.moduleCount)
        assertFalse("$label should not require an encrypted-runtime scan", scan.required)
        assertFalse("$label should not walk the working directory", scan.performed)
        assertEquals("trivial_module_free", scan.decision)
        assertEquals("skipped", scan.status)
        assertEquals(0, scan.walkedFileCount)
        assertEquals(0, scan.candidateFileCount)
        assertEquals(0, scan.headerProbeCount)
        assertEquals(0, scan.fullFileReadCount)
        assertTrue(
            "$label request preparation regressed to ${diagnostics.elapsedMs}ms",
            diagnostics.elapsedMs <= STATIC_REQUEST_PREPARATION_MAX_MS,
        )
        assertNativeDiagnostics(label, run)
    }

    private fun assertRetainedHeaderOnlyScan(
        label: String,
        run: EngineRun,
        expectedDecision: String,
        minimumCandidateCount: Int,
    ) {
        val diagnostics = run.diagnostics
        val scan = diagnostics.encryptedRuntimeModuleScan
        assertTrue("$label request should be prepared", diagnostics.prepared)
        assertTrue("$label should retain encrypted-runtime fallback", scan.required)
        assertTrue("$label should scan the working directory", scan.performed)
        assertEquals(expectedDecision, scan.decision)
        assertEquals("completed", scan.status)
        assertTrue(
            "$label should see the large cwd: candidates=${scan.candidateFileCount}",
            scan.candidateFileCount >= minimumCandidateCount,
        )
        assertTrue(
            "$label should probe the large cwd: probes=${scan.headerProbeCount}",
            scan.headerProbeCount >= minimumCandidateCount,
        )
        assertEquals(0, scan.headerReadFailureCount)
        assertEquals(
            "$label should read only the fixed encrypted header from ordinary candidates",
            scan.headerProbeCount.toLong() * ENCRYPTED_HEADER_SIZE_BYTES,
            scan.headerBytesRead,
        )
        assertEquals("$label must not fully read ordinary candidates", 0, scan.fullFileReadCount)
        assertEquals("$label must not fully read ordinary candidate bytes", 0L, scan.fullFileBytesRead)
        assertEquals(0, scan.encryptedModuleCount)
        assertTrue(
            "$label header-only scan regressed to ${diagnostics.elapsedMs}ms",
            diagnostics.elapsedMs <= FALLBACK_REQUEST_PREPARATION_MAX_MS,
        )
        assertNativeDiagnostics(label, run)
    }

    private fun assertNativeDiagnostics(label: String, run: EngineRun) {
        val diagnostics = run.diagnostics
        val scan = diagnostics.encryptedRuntimeModuleScan
        assertEquals(
            "$label request timing native value",
            diagnostics.elapsedMs.toString(),
            run.nativeValues["timing.engine_request_preparation.ms"],
        )
        assertEquals(
            "$label CommonJS timing native value",
            diagnostics.commonJs.elapsedMs.toString(),
            run.nativeValues["timing.engine_commonjs_preload.ms"],
        )
        assertEquals(
            "$label encrypted scan decision native value",
            scan.decision,
            run.nativeValues["embedded_script.engine_encrypted_runtime_scan.decision"],
        )
        assertEquals(
            "$label encrypted scan timing native value",
            scan.elapsedMs.toString(),
            run.nativeValues["timing.engine_encrypted_runtime_scan.ms"],
        )
        assertEquals(
            "$label encrypted scan full-read native value",
            scan.fullFileReadCount.toString(),
            run.nativeValues["embedded_script.engine_encrypted_runtime_scan.full_file_read_count"],
        )
    }

    private fun assertEncryptedFallback(
        label: String,
        run: EngineRun,
        expectedDecision: String,
    ) {
        val scan = run.diagnostics.encryptedRuntimeModuleScan
        assertTrue("$label should require the encrypted fallback", scan.required)
        assertTrue("$label should perform the encrypted fallback", scan.performed)
        assertEquals(expectedDecision, scan.decision)
        assertEquals("completed", scan.status)
        assertTrue("$label should decrypt at least one module", scan.encryptedModuleCount > 0)
        assertEquals(
            "$label should fully read only valid encrypted candidates",
            scan.encryptedModuleCount,
            scan.fullFileReadCount,
        )
        assertTrue(scan.fullFileBytesRead > scan.fullFileReadCount * ENCRYPTED_HEADER_SIZE_BYTES)
        assertEquals(0, scan.fullFileReadFailureCount)
        assertEquals(0, scan.decryptFailureCount)
        assertEquals(0, scan.oversizedCiphertextCount)
        assertEquals(0, scan.encryptedModuleBudgetRejectedCount)
        assertNativeDiagnostics(label, run)
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
        val root = File(context.cacheDir, "embedded-engine-large-cwd-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create engine request-preparation test dir: $absolutePath" }
        }
        val payload = buildString(FILLER_FILE_BYTES) {
            append("// ordinary filler; encrypted header must not match\n")
            while (length < FILLER_FILE_BYTES) {
                append('x')
            }
        }
        repeat(FILLER_FILE_COUNT) { index ->
            val branch = if (index % 2 == 0) {
                "node_modules/filler-${index % 24}/lib/shard-${index % 8}"
            } else {
                "workspace/shard-${index % 24}/nested-${index % 8}"
            }
            val extension = FILLER_EXTENSIONS[index % FILLER_EXTENSIONS.size]
            File(root, "$branch/filler-$index.$extension").apply {
                parentFile?.let { parent -> check(parent.mkdirs() || parent.isDirectory) }
                writeText(payload, StandardCharsets.UTF_8)
            }
        }
        return LargeWorkingDirectory(root = root, fillerFileCount = FILLER_FILE_COUNT)
    }

    private data class EngineRun(
        val diagnostics: NativeNodeEmbeddedJavaScriptEngine.RequestPreparationDiagnostics,
        val nativeValues: Map<String, String>,
    )

    private data class LargeWorkingDirectory(
        val root: File,
        val fillerFileCount: Int,
    )

    private companion object {
        const val FILLER_FILE_COUNT = 768
        const val FILLER_FILE_BYTES = 8 * 1024
        const val ENCRYPTED_HEADER_SIZE_BYTES = 8L
        const val AES_CIPHER_BLOCK_BYTES = 16L
        const val STATIC_REQUEST_PREPARATION_MAX_MS = 250L
        const val FALLBACK_REQUEST_PREPARATION_MAX_MS = 1_500L
        const val TEST_ENCRYPTION_KEY = "0123456789abcdef0123456789abcdef"
        const val TEST_ENCRYPTION_VECTOR = "0123456789abcdef"
        val FILLER_EXTENSIONS = listOf("js", "json", "ts", "mjs")
    }
}
