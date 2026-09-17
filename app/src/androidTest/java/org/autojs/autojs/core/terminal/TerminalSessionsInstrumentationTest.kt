package org.autojs.autojs.core.terminal

import android.content.Intent
import android.os.SystemClock
import android.widget.EditText
import androidx.test.runner.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.ui.terminal.TerminalActivity
import org.autojs.autojs.ui.terminal.TerminalEmulatorView
import org.autojs.autojs6.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class TerminalSessionsInstrumentationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun switchingSessionsPreservesShellStateAndWrappedDrafts() {
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
            val draft = "npm install cheerio " + "--verbose ".repeat(20)
            onMain { terminal.findViewById<EditText>(R.id.command_input).setText(draft) }
            await { onMain { terminal.findViewById<EditText>(R.id.command_input).layout?.lineCount?.let { it > 1 } == true } }
            onMain { TerminalActivity.launchSession(terminal, second.id) }
            await { onMain { terminal.findViewById<TerminalEmulatorView>(R.id.terminal)?.termSession === second.pty } }
            onMain {
                assertEquals("", terminal.findViewById<EditText>(R.id.command_input).text.toString())
                second.pty.write("AJ6_SESSION=second; printf '__AJ6_%s__\\n' \"\$AJ6_SESSION\"\r")
            }
            await { onMain { second.pty.transcriptText.contains("__AJ6_second__") } }
            onMain { TerminalActivity.launchSession(terminal, first.id) }
            await { onMain { terminal.findViewById<TerminalEmulatorView>(R.id.terminal)?.termSession === first.pty } }
            onMain {
                assertEquals(draft, terminal.findViewById<EditText>(R.id.command_input).text.toString())
                assertFalse(first.pty.transcriptText.contains("__AJ6_second__"))
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
    fun closingDuringStartupDoesNotLeaveALiveShell() {
        val paths = TerminalPaths.of(context).ensureLayout()
        val session = onMain {
            TerminalSessionManager.create(context, paths.home.path).also { TerminalSessionManager.close(it.id) }
        }
        assertFalse(session.pty.isProcessAlive)
        await { session.pty.exitCode != null }
        assertNull(TerminalSessionManager.get(session.id))
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { "Terminal state did not settle" }
            SystemClock.sleep(50)
        }
    }

    private fun <T> onMain(block: () -> T): T {
        val value = AtomicReference<T>()
        instrumentation.runOnMainSync { value.set(block()) }
        return value.get()
    }
}
