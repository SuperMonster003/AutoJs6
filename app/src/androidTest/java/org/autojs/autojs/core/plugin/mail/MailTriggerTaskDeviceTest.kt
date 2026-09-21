package org.autojs.autojs.core.plugin.mail

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.timing.IntentTask
import org.autojs.autojs.timing.TimedTaskManager
import org.autojs.plugin.mail.api.MailActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Installs (or removes) an "On mail arrived" intent task for the P8 device matrix of the mail
 * plugin repository (`.python/run_trigger_matrix.py`). Meant for `am instrument` directly, so
 * the task survives the run: `mail.trigger.script` is the device path of the script the task
 * launches (`docs/smoke/trigger.js` of the plugin repository), `mail.trigger.watch` the watch
 * id (empty for every watch), `mail.trigger.from` / `mail.trigger.subject` the optional filter
 * entries (comma-separated). The removal test deletes every task of the action.
 */
class MailTriggerTaskDeviceTest {

    private val arguments
        get() = InstrumentationRegistry.getArguments()

    @Test
    fun installsAMailArrivedTask() {
        val scriptPath = arguments.getString("mail.trigger.script")
        assumeTrue("mail.trigger.script not given", !scriptPath.isNullOrBlank())
        assertTrue("the script must exist on the device: $scriptPath", File(scriptPath!!).isFile)
        val extras = MailTriggerTaskExtras(
            triggerId = arguments.getString("mail.trigger.watch").orEmpty().trim(),
            filter = MailTriggerFilter(
                from = MailTriggerFilter.split(arguments.getString("mail.trigger.from")),
                subject = MailTriggerFilter.split(arguments.getString("mail.trigger.subject")),
            ),
        )
        TimedTaskManager.allIntentTasksAsList.filter { it.action == MailActions.HOST_TRIGGER_ACTION && it.scriptPath == scriptPath }.forEach { TimedTaskManager.removeTaskSync(it) }
        val task = IntentTask().apply {
            action = MailActions.HOST_TRIGGER_ACTION
            setScriptPathAndCaptureContext(scriptPath)
            this.extras = extras.toJson()
        }
        TimedTaskManager.addTaskSync(task)
        val stored = TimedTaskManager.allIntentTasksAsList.filter { it.action == MailActions.HOST_TRIGGER_ACTION }
        assertEquals(1, stored.count { it.scriptPath == scriptPath })
        assertEquals(extras, MailTriggerTaskExtras.parse(stored.first { it.scriptPath == scriptPath }.extras))
        Log.i(TAG, "installed task id=${task.id} watch=${extras.triggerId.ifEmpty { "*" }} filter=${!extras.filter.isEmpty} tasks=${stored.size}")
    }

    @Test
    fun removesTheMailArrivedTasks() {
        val tasks = TimedTaskManager.allIntentTasksAsList.filter { it.action == MailActions.HOST_TRIGGER_ACTION }
        tasks.forEach { TimedTaskManager.removeTaskSync(it) }
        Log.i(TAG, "removed ${tasks.size} task(s)")
        assertTrue(TimedTaskManager.allIntentTasksAsList.none { it.action == MailActions.HOST_TRIGGER_ACTION })
    }

    private companion object {
        const val TAG = "MailTriggerTask"
    }
}
