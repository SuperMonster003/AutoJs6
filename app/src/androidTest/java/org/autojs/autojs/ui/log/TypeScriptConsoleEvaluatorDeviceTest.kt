package org.autojs.autojs.ui.log

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.autojs.autojs.AutoJs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypeScriptConsoleEvaluatorDeviceTest {

    @Test
    fun evaluatesTypeScriptAndPrintsItsFinalValue() {
        val console = AutoJs.instance.globalConsole
        val completed = CountDownLatch(1)
        console.clear()
        try {
            TypeScriptConsoleEvaluator().evaluate(
                "const answer: number = 40; answer + 2",
                completed::countDown,
            )

            assertTrue(
                "Console TypeScript evaluation did not complete",
                completed.await(90, TimeUnit.SECONDS),
            )
            val messages = synchronized(console.logEntries) {
                console.logEntries.map { entry -> entry.content.toString() }
            }
            assertTrue(
                messages.joinToString("\n"),
                messages.any { message -> "> const answer: number = 40; answer + 2" in message },
            )
            assertTrue(
                messages.joinToString("\n"),
                messages.any { message -> "=> 42" in message },
            )
        } finally {
            console.clear()
        }
    }

    @Test
    fun stopsTheActiveTypeScriptSnippet() {
        val console = AutoJs.instance.globalConsole
        val completed = CountDownLatch(1)
        val evaluator = TypeScriptConsoleEvaluator()
        console.clear()
        try {
            evaluator.evaluate(
                "while (true) { }",
                completed::countDown,
            )

            assertTrue(evaluator.isRunning())
            assertTrue("The active TypeScript snippet was not accepted for stopping", evaluator.stop())
            assertTrue(
                "The stopped TypeScript snippet did not reach a terminal state",
                completed.await(90, TimeUnit.SECONDS),
            )
            assertFalse(evaluator.isRunning())
            assertFalse(evaluator.stop())
        } finally {
            evaluator.stop()
            console.clear()
        }
    }

}
