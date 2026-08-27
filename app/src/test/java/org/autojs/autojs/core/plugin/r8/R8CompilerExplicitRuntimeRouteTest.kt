package org.autojs.autojs.core.plugin.r8

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import java.io.File
import java.nio.file.Files

class R8CompilerExplicitRuntimeRouteTest {
    @Test
    fun scriptRuntimePublishesOnlyUsefulExplicitR8Overloads() {
        val stringArray = emptyArray<String>().javaClass
        val shapes = ScriptRuntime::class.java.methods
            .filter { it.name == "loadJarWithR8" }
            .map { it.parameterTypes.toList() }
            .toSet()

        assertEquals(
            setOf(
                listOf(String::class.java, stringArray),
                listOf(String::class.java, stringArray, stringArray),
                listOf(String::class.java, stringArray, String::class.java),
                listOf(String::class.java, stringArray, stringArray, String::class.java),
                listOf(
                    String::class.java,
                    stringArray,
                    stringArray,
                    stringArray,
                    IntArray::class.java,
                ),
                listOf(
                    String::class.java,
                    stringArray,
                    stringArray,
                    stringArray,
                    IntArray::class.java,
                    String::class.java,
                ),
            ),
            shapes,
        )

        assertEquals(
            setOf(listOf(String::class.java, String::class.java, String::class.java)),
            ScriptRuntime::class.java.methods
                .filter { it.name == "retraceR8Stack" }
                .map { it.parameterTypes.toList() }
                .toSet(),
        )
    }

    @Test
    fun rhinoConvertsScriptArraysForSimpleAndOwnedConsumerRuleShapes() {
        val context = Context.enter()
        try {
            val scope = context.initStandardObjects()
            val fixture = ScriptArrayFixture()
            ScriptableObject.putProperty(scope, "fixture", Context.javaToJS(fixture, scope))

            assertEquals(
                "simple:program.jar:keep-0.pro,keep-1.pro",
                Context.jsToJava(
                    context.evaluateString(
                        scope,
                        "fixture.call('program.jar', ['keep-0.pro', 'keep-1.pro'])",
                        "r8-simple-shape",
                        1,
                        null,
                    ),
                    String::class.java,
                ),
            )
            assertEquals(
                "full:program.jar:1:2:2:1,0",
                Context.jsToJava(
                    context.evaluateString(
                        scope,
                        "fixture.call('program.jar', ['keep.pro'], ['api-0.jar', 'api-1.jar'], " +
                            "['consumer-0.pro', 'consumer-1.pro'], [1, 0])",
                        "r8-full-shape",
                        1,
                        null,
                    ),
                    String::class.java,
                ),
            )
        } finally {
            Context.exit()
        }
    }

    @Test
    fun invalidRuleShapeFailsBeforePathResolutionCompilationOrLoading() {
        var resolutions = 0
        var compilations = 0
        var loads = 0

        assertThrows(IllegalArgumentException::class.java) {
            R8CompilerExplicitRuntimeRoute.dispatch(
                programPath = "program.jar",
                keepRulePaths = emptyArray(),
                orderedClasspathPaths = emptyArray(),
                consumerRulePaths = emptyArray(),
                consumerRuleClasspathOrdinals = IntArray(0),
                minApi = 24,
                resolvePath = { path -> resolutions++; path },
                compile = { compilations++; failure() },
                loadPublished = { loads++ },
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            R8CompilerExplicitRuntimeRoute.dispatch(
                programPath = "program.jar",
                keepRulePaths = arrayOf("keep.pro"),
                orderedClasspathPaths = arrayOf("classpath.jar"),
                consumerRulePaths = arrayOf("consumer.pro"),
                consumerRuleClasspathOrdinals = IntArray(0),
                minApi = 24,
                resolvePath = { path -> resolutions++; path },
                compile = { compilations++; failure() },
                loadPublished = { loads++ },
            )
        }

        assertEquals(0, resolutions)
        assertEquals(0, compilations)
        assertEquals(0, loads)
    }

    @Test
    fun pathsResolveOnceInCanonicalOrderAndConsumerOwnersStayExplicit() = withInputFiles { root ->
        val resolutions = mutableListOf<String>()
        var captured: R8CompilerHostCompileSpec? = null

        assertThrows(R8CompilerExplicitLoadException::class.java) {
            R8CompilerExplicitRuntimeRoute.dispatch(
                programPath = "program.jar",
                keepRulePaths = arrayOf("keep-0.pro", "keep-1.pro"),
                orderedClasspathPaths = arrayOf("classpath-1.jar", "classpath-0.jar"),
                consumerRulePaths = arrayOf("consumer-0.pro", "consumer-1.pro"),
                consumerRuleClasspathOrdinals = intArrayOf(1, 0),
                minApi = 24,
                resolvePath = { path ->
                    resolutions += path
                    File(root, path).path
                },
                compile = { spec -> captured = spec; failure() },
                loadPublished = { error("A failed R8 outcome must not load") },
            )
        }

        assertEquals(
            listOf(
                "program.jar",
                "classpath-1.jar",
                "classpath-0.jar",
                "keep-0.pro",
                "keep-1.pro",
                "consumer-0.pro",
                "consumer-1.pro",
            ),
            resolutions,
        )
        val inputs = checkNotNull(captured).inputs
        assertEquals("program", inputs.program.openInputStream().use { it.readBytes().decodeToString() })
        assertEquals(
            listOf("classpath-1", "classpath-0"),
            inputs.classpath.map { it.openInputStream().use { stream -> stream.readBytes().decodeToString() } },
        )
        assertEquals(
            listOf("keep-0", "keep-1"),
            inputs.keepRules.map { it.openInputStream().use { stream -> stream.readBytes().decodeToString() } },
        )
        assertEquals(listOf(1, 0), inputs.consumerRules.map { it.ownerClasspathOrdinal })
        assertEquals(
            listOf("consumer-0", "consumer-1"),
            inputs.consumerRules.map { it.source.openInputStream().use { stream -> stream.readBytes().decodeToString() } },
        )
    }

    @Test
    fun publishedOutcomeHandsOnlyTheVerifiedR8DexZipToTheLoader() = withInputFiles { root ->
        val outcome = publishedOutcome(root)
        var compilations = 0
        var loads = 0

        val loadedBytes = R8CompilerExplicitRuntimeRoute.dispatch(
            programPath = "program.jar",
            keepRulePaths = arrayOf("keep-0.pro"),
            orderedClasspathPaths = emptyArray(),
            consumerRulePaths = emptyArray(),
            consumerRuleClasspathOrdinals = IntArray(0),
            minApi = 24,
            resolvePath = { path -> File(root, path).path },
            compile = { compilations++; outcome },
            loadPublished = { dexZip ->
                loads++
                assertTrue(dexZip.sizeBytes > 0L)
                assertTrue(dexZip.sha256.matches(Regex("[0-9a-f]{64}")))
                dexZip.openRead().use { it.readBytes() }
            },
        )

        assertArrayEquals(R8CompilerTransactionTestFixtures.validDexZip(), loadedBytes)
        assertEquals(1, compilations)
        assertEquals(1, loads)
    }

    @Test
    fun disabledOrMissingSelectionFailsClosedWithoutLoading() = withInputFiles { root ->
        var loads = 0

        val error = assertThrows(R8CompilerExplicitLoadException::class.java) {
            dispatchMinimal(root, R8CompilerHostDispatchOutcome.NotSelected(R8CompilerSelectionDecision.Disabled)) {
                loads++
            }
        }

        assertTrue(error.message.orEmpty().contains("no D8/dx fallback"))
        assertEquals(0, loads)
    }

    @Test
    fun selectedProviderFailurePreservesR8OnlyDirectiveAndNeverLoads() = withInputFiles { root ->
        var loads = 0
        val outcome = failure()

        val error = assertThrows(R8CompilerExplicitLoadException::class.java) {
            dispatchMinimal(root, outcome) { loads++ }
        }

        assertTrue(outcome.directive.propagateAsR8Failure)
        assertFalse(outcome.directive.semanticFallbackAllowed)
        assertTrue(error.message.orEmpty().contains("no D8/dx fallback"))
        assertEquals(0, loads)
    }

    @Test
    fun cancellationIsScriptInterruptionAndNeverLoads() = withInputFiles { root ->
        var loads = 0
        val directive = R8CompilerDispatchFirewall().failed()
        val outcome = R8CompilerHostDispatchOutcome.Cancelled(
            R8CompilerTransactionOutcome.Cancelled(null, directive),
        )

        assertThrows(ScriptInterruptedException::class.java) {
            dispatchMinimal(root, outcome) { loads++ }
        }
        assertFalse(directive.semanticFallbackAllowed)
        assertEquals(0, loads)
    }

    private fun dispatchMinimal(
        root: File,
        outcome: R8CompilerHostDispatchOutcome,
        load: (R8CompilerLoadableDexZip) -> Unit,
    ) {
        R8CompilerExplicitRuntimeRoute.dispatch(
            programPath = "program.jar",
            keepRulePaths = arrayOf("keep-0.pro"),
            orderedClasspathPaths = emptyArray(),
            consumerRulePaths = emptyArray(),
            consumerRuleClasspathOrdinals = IntArray(0),
            minApi = 24,
            resolvePath = { path -> File(root, path).path },
            compile = { outcome },
            loadPublished = load,
        )
    }

    private fun failure() = R8CompilerHostDispatchOutcome.Failed(
        stage = R8CompilerHostFailureStage.TRANSACTION,
        cause = IllegalStateException("synthetic R8 failure"),
        directive = R8CompilerDispatchFirewall().failed(),
    )

    private fun publishedOutcome(root: File): R8CompilerHostDispatchOutcome.Published {
        val transaction = R8CompilerTransactionRunner(
            workspaceRoot = File(root, "published-work").apply { mkdirs() },
            descriptorFactory = R8CompilerTransactionTestFixtures.descriptorFactory(),
            artifactStore = R8CompilerPersistentArtifactStore(
                File(root, "published-cache").apply { mkdirs() },
            ),
            requestIdFactory = R8CompilerTransactionTestFixtures.requestIds(),
        ).run(
            connection = R8CompilerTransactionTestFixtures.FakeConnection(),
            negotiation = R8CompilerTransactionTestFixtures.negotiation(),
            spec = R8CompilerTransactionTestFixtures.spec(),
            firewall = R8CompilerDispatchFirewall().apply { negotiated() },
            abortSignal = R8CompilerAttemptAbortSignal(),
        )
        check(transaction is R8CompilerTransactionOutcome.Published) {
            val failed = transaction as? R8CompilerTransactionOutcome.Failed
            "Unable to construct published R8 fixture: stage=${failed?.stage}, cause=${failed?.cause}"
        }
        return R8CompilerHostDispatchOutcome.Published(transaction)
    }

    private fun <T> withInputFiles(action: (File) -> T): T {
        val root = Files.createTempDirectory("r8-explicit-runtime-").toFile()
        return try {
            mapOf(
                "program.jar" to "program",
                "classpath-0.jar" to "classpath-0",
                "classpath-1.jar" to "classpath-1",
                "keep-0.pro" to "keep-0",
                "keep-1.pro" to "keep-1",
                "consumer-0.pro" to "consumer-0",
                "consumer-1.pro" to "consumer-1",
            ).forEach { (name, content) -> File(root, name).writeText(content) }
            action(root)
        } finally {
            root.deleteRecursively()
        }
    }

    class ScriptArrayFixture {
        fun call(program: String, keepRules: Array<String>): String =
            "simple:$program:${keepRules.joinToString(",")}"

        fun call(
            program: String,
            keepRules: Array<String>,
            orderedClasspath: Array<String>,
            consumerRules: Array<String>,
            consumerRuleClasspathOrdinals: IntArray,
        ): String = "full:$program:${keepRules.size}:${orderedClasspath.size}:${consumerRules.size}:" +
            consumerRuleClasspathOrdinals.joinToString(",")
    }
}
