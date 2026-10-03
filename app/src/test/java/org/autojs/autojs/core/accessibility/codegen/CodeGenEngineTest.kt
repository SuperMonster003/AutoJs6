package org.autojs.autojs.core.accessibility.codegen

import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Action
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Mode
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Scope
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Style
import org.autojs.autojs.core.accessibility.codegen.rank.Explanation
import org.autojs.autojs.core.accessibility.codegen.search.SearchBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.CompilerEnvirons
import org.mozilla.javascript.Context
import org.mozilla.javascript.Parser
import java.io.File

class CodeGenEngineTest {
    private fun fixture(name: String) = SnapshotNode.load(File(requireNotNull(javaClass.getResource("/codegen/" + name + ".layout.json")).toURI()))

    @Test
    fun threeFixturesProduceVerifiedRunnableCodeAndScopedExplanations() {
        val login = fixture("login")
        val duplicates = fixture("list-duplicates")
        val crossPackage = fixture("cross-package-id")
        val cases = listOf(
            login to CodeGenRequest(listOf(login.child(2)!!), action = Action.CLICK),
            duplicates to CodeGenRequest(listOf(duplicates.child(3)!!.child(1)!!), action = Action.CLICK),
            crossPackage to CodeGenRequest(listOf(crossPackage.child(1)!!)),
        )
        for ((root, request) in cases) {
            val first = CodeGenEngine().generate(root, request) as CodeGenResult
            val second = CodeGenEngine().generate(root, request) as CodeGenResult
            assertEquals(first.candidates.map { it.code }, second.candidates.map { it.code })
            if (root === duplicates) assertEquals("""
                function locate() {
                    let a = id("list").findOnce();
                    return a && a.compass("c3c1");
                }

                wait(locate, Infinity, 200, { then: w => w.click() });
            """.trimIndent(), first.preferred.code)
            assertTrue(first.candidates.size in 1..10)
            assertEquals(first.candidates.size - 1, first.alternatives.size)
            for (candidate in first.candidates) {
                assertEquals(request.targets, candidate.resolved)
                assertEquals(1, candidate.matchedCount)
                assertTrue(candidate.explanation.size in 1..3)
                assertEquals(Explanation.Key.ACTIVE_WINDOW_ONLY, candidate.explanation.last().messages.last().key)
                Parser(CompilerEnvirons().apply { languageVersion = Context.VERSION_ES6 }).parse(candidate.code, "generated", 1)
                assertFalse(candidate.code.contains("waitFor("))
                assertFalse(candidate.code.contains(".findOne()"))
            }
        }
    }

    @Test
    fun noIdLoginUsesItsStableTextSelector() {
        val root = fixture("login")
        val result = CodeGenEngine().generate(root, CodeGenRequest(listOf(root.child(2)!!), action = Action.CLICK)) as CodeGenResult
        assertEquals("text(\"登录\").untilFindOne().click();", result.preferred.code)
    }

    @Test
    fun crossPackageIdRemainsFullyQualified() {
        val root = fixture("cross-package-id")
        val result = CodeGenEngine().generate(root, CodeGenRequest(listOf(root.child(1)!!))) as CodeGenResult
        assertEquals("let w = id(\"org.autojs.test.codegen.fixture:id/title\").untilFindOne();", result.preferred.code)
    }

    @Test
    fun collectionAndFlowUseTheRequestedScopeAndStyle() {
        val root = fixture("list-duplicates")
        val targets = (0 until 20).map { root.child(it)!!.child(1)!! }
        val request = CodeGenRequest(targets, Scope.COLLECTION, Mode.CHECK, Action.CLICK, style = Style.FLOW)
        val result = CodeGenEngine().generate(root, request) as CodeGenResult
        assertEquals(20, result.preferred.matchedCount)
        assertEquals(targets.toSet(), result.preferred.resolved.toSet())
        assertTrue(result.preferred.code.contains("flow.whenPresent("))
        assertTrue(result.preferred.code.contains("clickEach()"))
    }

    @Test
    fun invalidRequestAndBudgetExhaustionHaveDistinctFailures() {
        val root = fixture("login")
        val invalid = CodeGenEngine().generate(root, CodeGenRequest<SnapshotNode>(emptyList())) as CodeGenFailure
        assertEquals(CodeGenFailure.Reason.INVALID_REQUEST, invalid.reason)
        assertEquals(listOf(CodeGenRequest.Error.EMPTY_TARGETS), invalid.requestErrors)
        val limited = CodeGenEngine(budget = SearchBudget(0)).generate(root, CodeGenRequest(listOf(root.child(2)!!))) as CodeGenFailure
        assertEquals(CodeGenFailure.Reason.BUDGET_EXCEEDED, limited.reason)
    }

    @Test
    fun unsupportedActionIncludesTheClosestRejection() {
        val root = fixture("cross-package-id")
        val failure = CodeGenEngine().generate(root, CodeGenRequest(listOf(root.child(1)!!), action = Action.CLICK)) as CodeGenFailure
        assertEquals(CodeGenFailure.Reason.ALL_REJECTED, failure.reason)
        assertNotNull(failure.closestRejection)
    }

    @Test
    fun cancellationReturnsAFailureAndTimingsAreLoggedOnce() {
        val root = fixture("login")
        val signal = CancellationSignal().apply { cancel() }
        val logged = ArrayList<CodeGenTimings>()
        val failure = CodeGenEngine(logTimings = logged::add).generate(root, CodeGenRequest(listOf(root)), signal) as CodeGenFailure
        assertEquals(CodeGenFailure.Reason.CANCELLED, failure.reason)
        assertEquals(listOf(failure.timings), logged)
    }

    @Test
    fun successfulGenerationRecordsSeparatePhaseDurations() {
        val root = fixture("login")
        val logged = ArrayList<CodeGenTimings>()
        val result = CodeGenEngine(logTimings = logged::add).generate(root, CodeGenRequest(listOf(root.child(2)!!))) as CodeGenResult
        assertEquals(listOf(result.timings), logged)
        assertTrue(result.timings.searchNanos >= 0)
        assertTrue(result.timings.verificationNanos > 0)
        assertTrue(result.timings.rankingNanos > 0)
        assertTrue(result.timings.renderingNanos > 0)
        assertTrue(result.timings.totalNanos >= result.timings.verificationNanos)
    }
}
