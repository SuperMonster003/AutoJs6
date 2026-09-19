package org.autojs.autojs.core.terminal

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.afollestad.materialdialogs.MaterialDialog
import jackpal.androidterm.emulatorview.TerminalSelectionSnapshot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.ui.terminal.TerminalActivity
import org.autojs.autojs.ui.terminal.TerminalEmulatorView
import org.autojs.autojs.ui.terminal.TerminalManagerDialog
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ViewUtils.subtitleView
import org.autojs.autojs6.databinding.DialogConnectionManagerBinding
import androidx.appcompat.widget.Toolbar
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

    @Test
    fun managerSurvivesNewAndResumedSessionActivitiesAndRestoresTheOriginalTerminal() {
        val paths = TerminalPaths.of(context).ensureLayout()
        val initialIds = TerminalSessionManager.activeSessions.map { it.id }.toSet()
        val first = onMain { TerminalSessionManager.create(context, paths.home.path) }
        val host = instrumentation.startActivitySync(TerminalActivity.intent(context)
            .putExtra("session_id", first.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TerminalActivity
        var manager: MaterialDialog? = null
        var child: TerminalActivity? = null
        try {
            await { onMain { first.pty.transcriptText.contains("$") } }
            manager = onMain { TerminalManagerDialog.show(host) }
            val dialog = manager
            val views = onMain { DialogConnectionManagerBinding.bind(dialog.customView!!) }
            onMain { views.controlContainer.getChildAt(0).performClick() }
            await { onMain { resumedTerminal()?.let { it !== host } == true } }
            val createdTerminal = onMain { resumedTerminal()!! }
            child = createdTerminal
            val second = TerminalSessionManager.activeSessions.single { it.id !in initialIds && it.id != first.id }
            await { onMain { createdTerminal.findViewById<TerminalEmulatorView>(R.id.terminal).termSession === second.pty } }
            onMain {
                assertTrue(dialog.isShowing)
                createdTerminal.onSupportNavigateUp()
            }
            await { onMain { resumedTerminal() === host && dialog.window?.decorView?.hasWindowFocus() == true } }
            onMain {
                assertTrue(dialog.isShowing)
                val row = (0 until views.clientsContainer.childCount).map(views.clientsContainer::getChildAt)
                    .single { it.findViewById<TextView>(R.id.title).text.toString() == host.getString(R.string.text_terminal_session_title, first.id) }
                row.findViewById<View>(R.id.open).performClick()
            }
            await { onMain { resumedTerminal()?.let { it !== host } == true } }
            val resumed = onMain { resumedTerminal()!! }
            child = resumed
            await { onMain { resumed.findViewById<TerminalEmulatorView>(R.id.terminal).termSession === first.pty } }
            onMain {
                assertTrue(dialog.isShowing)
                resumed.onSupportNavigateUp()
            }
            await { onMain { resumedTerminal() === host && dialog.window?.decorView?.hasWindowFocus() == true } }
            onMain {
                dialog.dismiss()
                first.pty.write("printf '__RETURNED_%s__\\n' alive\r")
            }
            await { onMain { first.pty.transcriptText.contains("__RETURNED_alive__") } }
            onMain {
                val toolbar = host.findViewById<Toolbar>(R.id.toolbar)
                assertEquals("~", toolbar.subtitle.toString())
                toolbar.subtitleView!!.performClick()
                assertEquals(paths.home.canonicalPath, ClipboardUtils.getClip(host).toString())
            }
            // Also copy the real current directory after cd, rather than a stale or abbreviated label.
            onMain { first.pty.write("cd '${paths.tmp.path}'\r") }
            await { first.pty.currentDirectory()?.canonicalPath == paths.tmp.canonicalPath }
            onMain {
                host.findViewById<Toolbar>(R.id.toolbar).subtitleView!!.performClick()
                assertEquals(paths.tmp.canonicalPath, ClipboardUtils.getClip(host).toString())
            }
        } finally {
            onMain {
                manager?.dismiss()
                child?.takeUnless { it.isDestroyed }?.finish()
                host.finish()
                TerminalSessionManager.activeSessions.filter { it.id !in initialIds }.forEach { TerminalSessionManager.close(it.id) }
            }
        }
    }

    private fun resumedTerminal() = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<TerminalActivity>().singleOrNull()

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
