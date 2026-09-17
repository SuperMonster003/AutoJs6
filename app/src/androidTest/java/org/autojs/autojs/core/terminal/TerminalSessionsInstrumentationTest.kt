package org.autojs.autojs.core.terminal

import android.content.Intent
import android.os.SystemClock
import jackpal.androidterm.emulatorview.TerminalSelectionSnapshot
import androidx.test.runner.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.ui.terminal.TerminalActivity
import org.autojs.autojs.ui.terminal.TerminalEmulatorView
import org.autojs.autojs6.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class TerminalSessionsInstrumentationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun switchingSessionsPreservesShellStateAndUnsubmittedInput() {
        val paths = TerminalPaths.of(context).ensureLayout()
        val first = onMain { TerminalSessionManager.create(context, paths.home.path) }
        val second = onMain { TerminalSessionManager.create(context, paths.tmp.path) }
        var activity: TerminalActivity? = null
        try {
            activity = instrumentation.startActivitySync(TerminalActivity.intent(context)
                .putExtra("session_id", first.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TerminalActivity
            val terminal = activity
            await { onMain { terminal.findViewById<TerminalEmulatorView>(R.id.terminal)?.termSession === first.pty } }
            onMain { first.pty.write("AJ6_SESSION=first; printf '__AJ6_%s__\\n' \"\$AJ6_SESSION\"\r") }
            await { onMain { first.pty.transcriptText.contains("__AJ6_first__") } }
            // Keep an unfinished command in the shell's own line editor across view replacement.
            onMain { first.pty.write("printf '__DRAFT_%s__\\n' preserved") }
            await { onMain { first.pty.transcriptText.contains("preserved") } }
            onMain { TerminalActivity.launchSession(terminal, second.id) }
            await { onMain { terminal.findViewById<TerminalEmulatorView>(R.id.terminal)?.termSession === second.pty } }
            onMain {
                second.pty.write("AJ6_SESSION=second; printf '__AJ6_%s__\\n' \"\$AJ6_SESSION\"\r")
            }
            await { onMain { second.pty.transcriptText.contains("__AJ6_second__") } }
            onMain { TerminalActivity.launchSession(terminal, first.id) }
            await { onMain { terminal.findViewById<TerminalEmulatorView>(R.id.terminal)?.termSession === first.pty } }
            onMain {
                assertFalse(first.pty.transcriptText.contains("__AJ6_second__"))
                first.pty.write("\r")
            }
            await { onMain { first.pty.transcriptText.contains("__DRAFT_preserved__") } }
            onMain {
                TerminalSessionManager.close(first.id)
                assertFalse(first.pty.isProcessAlive)
                assertTrue(second.pty.isProcessAlive)
            }
            await { first.pty.exitCode != null }
        } finally {
            onMain {
                activity?.finish()
                TerminalSessionManager.close(first.id)
                TerminalSessionManager.close(second.id)
            }
        }
    }

    @Test
    fun selectionSnapshotPreservesLogicalLinesAndSurvivesNewOutput() {
        val paths = TerminalPaths.of(context).ensureLayout()
        val session = onMain { TerminalSessionManager.create(context, paths.home.path).also { it.pty.updateSize(24, 8) } }
        val line = "select-中文-" + "abcdef0123456789".repeat(6)
        try {
            await { onMain { session.pty.transcriptText.contains("$") } }
            onMain { session.pty.write("printf '\\n%s\\n%s\\n' '$line' 'hard-line-END'\r") }
            await { onMain { session.pty.transcriptText.contains("$line\nhard-line-END") } }
            val snapshot = onMain { TerminalSelectionSnapshot.capture(session.pty, 24, 0) }
            val copied = snapshot.selectedText(0, snapshot.text.length)
            assertTrue(copied.contains("$line\nhard-line-END"))
            assertTrue(snapshot.text.contains("\n"))
            onMain { session.pty.write("printf '__FRESH_%s__\\n' output\r") }
            await { onMain { session.pty.transcriptText.contains("__FRESH_output__") } }
            assertEquals(copied, snapshot.selectedText(0, snapshot.text.length))
            assertFalse(snapshot.text.contains("__FRESH_output__"))
            val end = snapshot.text.indexOf("hard-line-END")
            assertEquals("hard-line-END", snapshot.selectedText(end, end + 13))
        } finally {
            onMain { TerminalSessionManager.close(session.id) }
        }
    }

    @Test
    fun closingDuringStartupDoesNotLeaveALiveShell() {
        val paths = TerminalPaths.of(context).ensureLayout()
        // Exercise immediate cancellation, the fork/setsid window, and background manager sessions.
        repeat(30) { iteration ->
            val session = onMain {
                TerminalSessionManager.create(context, paths.home.path).also {
                    if (iteration % 2 == 0) it.pty.updateSize(80, 24)
                    if (iteration % 3 == 0) TerminalSessionManager.close(it.id)
                }
            }
            try {
                if (iteration % 3 != 0) {
                    SystemClock.sleep((iteration % 3).toLong())
                    onMain { TerminalSessionManager.close(session.id) }
                }
                assertFalse(session.pty.isProcessAlive)
                await { session.pty.exitCode != null }
                assertNull(TerminalSessionManager.get(session.id))
                val pid = session.pty.pid
                if (pid > 0) assertFalse("Shell $pid survived cancellation", File("/proc/$pid").exists())
            } finally {
                onMain { TerminalSessionManager.close(session.id) }
            }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { "Terminal state did not settle" }
            SystemClock.sleep(50)
        }
    }

    private fun <T> onMain(block: () -> T): T {
        val value = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { value.set(runCatching(block)) }
        return value.get().getOrThrow()
    }
}
