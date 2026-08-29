package org.autojs.autojs.ui.log

import androidx.test.runner.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.autojs.autojs.AutoJs
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

}
