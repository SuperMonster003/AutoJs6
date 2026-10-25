package org.autojs.autojs.runtime.api.modules.terminal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.terminal.TerminalLauncher
import org.autojs.autojs.core.plugin.terminal.TerminalPluginHost
import org.autojs.autojs.ui.main.MainActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Host-UID acceptance of signed/R8 plugin APKs. No plugin implementation class is referenced. */
class TerminalReleaseMatrixDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val plugin = "io.github.supermonster003.autojs6.plugin.three.shell.terminal"

    @Test fun drawerDirectoryAndProjectEntryRequestsReachTheSameVisibleSession() {
        bringHostToFront()
        val smoke = TerminalScriptSmokeDeviceTest()
        smoke.prepare()
        try {
            val before = runBlocking { TerminalPluginHost.listSessions(context).map { it.id }.toSet() }
            val environment = runBlocking { TerminalPluginHost.getEnvironment(context) }
            val home = requireNotNull(environment.home)
            val temporary = requireNotNull(environment.prefix) + "/tmp"
            run {
                // The drawer starts a new session; both directory actions use open(context, path).
                assertTrue(TerminalLauncher.openNew(context))
                var id: String? = null
                await("drawer session") {
                    runBlocking { TerminalPluginHost.listSessions(context) }
                        .firstOrNull { it.id !in before && it.alive }?.let { id = it.id } != null
                }
                bringHostToFront()
                assertTrue(TerminalLauncher.open(context, temporary))
                await("directory-menu cwd") {
                    runBlocking { TerminalPluginHost.listSessions(context) }.any { it.id == id && it.cwd.endsWith("/terminal/usr/tmp") }
                }
                bringHostToFront()
                assertTrue(TerminalLauncher.open(context, home))
                await("project-toolbar cwd") {
                    runBlocking { TerminalPluginHost.listSessions(context) }.any { it.id == id && it.cwd.endsWith("/terminal/home") }
                }
                assertEquals(1, runBlocking { TerminalPluginHost.listSessions(context) }.count { it.id !in before })
                assertTrue("terminal entry must resolve to its UI", shell("dumpsys activity activities").lineSequence().any {
                    it.contains("ResumedActivity") && it.contains(plugin) && it.contains("TerminalActivity")
                })
                android.util.Log.i("TerminalReleaseMatrix", "drawer=new directory=tmp project=home sameSession=true visible=true")
            }
        } finally { smoke.restore() }
    }

    @Test fun sharedStorageEchoOptionalNodeCommandsAndTheBundledOpenSampleWork() {
        bringHostToFront()
        val smoke = TerminalScriptSmokeDeviceTest()
        val restore = ArrayList<() -> Unit>()
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val state = shell("cmd appops get $plugin MANAGE_EXTERNAL_STORAGE")
                val mode = Regex("MANAGE_EXTERNAL_STORAGE: (allow|ignore|deny|default|foreground)").find(state)?.groupValues?.get(1) ?: "default"
                if (mode != "allow") {
                    shell("cmd appops set $plugin MANAGE_EXTERNAL_STORAGE allow")
                    restore.add { shell("cmd appops set $plugin MANAGE_EXTERNAL_STORAGE $mode") }
                }
            } else {
                for (permission in listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    if (context.packageManager.checkPermission(permission, plugin) != PackageManager.PERMISSION_GRANTED) {
                        shell("pm grant $plugin $permission")
                        restore.add { shell("pm revoke $plugin $permission") }
                    }
                }
            }
            smoke.prepare()
            try {
                val result = smoke.runScript("""
                    var node = terminal.env().node.available;
                    var command = 'sleep 1; cd /sdcard && echo P6_STORAGE_OK';
                    if (node) command += ' && node --version && npm --version && corepack --version';
                    var output = [];
                    var s = terminal.exec(command, {cwd: testCwd, show: false, keepOpen: false});
                    s.on('output', function(line) { output.push(String(line)); });
                    s.on('exit', function(code) {
                        check(code == 0, 'storage/Node commands failed: ' + output.join(' '));
                        check(output.join(' ').indexOf('P6_STORAGE_OK') >= 0, 'echo missing');
                        report({node: node, exitCode: code, output: output});
                    });
                """, 120000)
                assertEquals(0, result.getInt("exitCode"))
                if (result.getBoolean("node")) {
                    val output = result.getJSONArray("output").toString()
                    assertTrue("all three version commands answer", Regex("v?\\d+\\.\\d+\\.\\d+").findAll(output).count() >= 3)
                }
                val sampleName = context.assets.list("sample/终端")!!.first { it.contains("打开") && it.endsWith(".js") }
                val sample = context.assets.open("sample/终端/$sampleName").bufferedReader().use { it.readText() }
                smoke.runScript("""
                    var messages = [];
                    new Function('terminal', 'toastLog', ${JSONObject.quote(sample)})(terminal, function(value) { messages.push(String(value)); });
                    check(messages.length > 0, 'sample did not report');
                    check(messages.join(' ').indexOf('已打开') >= 0, 'sample failed');
                    report({messages: messages});
                """, workingDirectory = runBlocking { TerminalPluginHost.getEnvironment(context).home!! })
                android.util.Log.i("TerminalReleaseMatrix", "storage=granted cd=/sdcard echo=ok node=${result.getBoolean("node")} bundledOpenSample=ok")
            } finally { smoke.restore() }
        } finally { restore.asReversed().forEach { it() } }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText() }

    private fun bringHostToFront() {
        // The real host has continuous UI observers; its main loop need not become globally idle.
        // Launch through Android instead of ActivityScenario's instrumentation-wide idle wait.
        shell("am start -W -n ${context.packageName}/${MainActivity::class.java.name} -f 0x00020000")
    }

    private fun await(what: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20000
        while (!condition()) { check(SystemClock.elapsedRealtime() < deadline) { "Timed out: $what" }; SystemClock.sleep(80) }
    }
}
