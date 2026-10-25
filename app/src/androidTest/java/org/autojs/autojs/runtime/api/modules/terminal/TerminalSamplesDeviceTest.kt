package org.autojs.autojs.runtime.api.modules.terminal

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

/** Runs the actual bundled examples, with an owned npm fixture in the plugin's private HOME. */
class TerminalSamplesDeviceTest {
    private val smoke = TerminalScriptSmokeDeviceTest()
    private val assets get() = InstrumentationRegistry.getInstrumentation().targetContext.assets
    private var directory: String? = null

    @Before fun prepare() { smoke.prepare() }
    @After fun restore() {
        try {
            directory?.let { dir ->
                require(Regex(".*/files/terminal/home/p43-samples-[a-f0-9-]+").matches(dir))
                smoke.runScript("terminal.exec(" + JSONObject.quote("rm -r '$dir'") + ", {cwd: testCwd, show: false, keepOpen: false, wait: true}); report({ok: true});")
            }
        } finally { smoke.restore() }
    }

    @Test fun threeBundledExamplesRunWithRealShellAndNpm() {
        val suffix = "p43-samples-${UUID.randomUUID()}"
        val fixture = """{"name":"terminal-example","version":"1.0.0","scripts":{"build":"node -e \"console.log(123)\""}}"""
        val command = "mkdir '$suffix'; printf '%s' '${fixture.replace("'", "'\\''")}' > '$suffix/package.json'"
        directory = smoke.runScript("""
            check(terminal.env().node.available, 'Node.js Runtime must be installed for the npm sample');
            var s = terminal.exec(${JSONObject.quote(command)}, {cwd: testCwd, show: false, keepOpen: false, wait: true});
            check(s.exitCode == 0, 'fixture creation'); report({dir: testCwd + '/$suffix'});
        """).getString("dir")
        sampleNames.forEachIndexed { index, name ->
            val source = assets.open("sample/终端/$name.js").bufferedReader().use { it.readText() }
            val result = smoke.runScript("""
                var messages = [];
                new Function('terminal', 'toastLog', ${JSONObject.quote(source)})(terminal, function(text) {
                    messages.push(String(text)); report({messages: messages});
                });
                report({messages: messages});
            """, workingDirectory = requireNotNull(directory))
            val messages = result.getJSONArray("messages").toString()
            assertFalse(messages, messages.contains("失败"))
            assertTrue(messages, messages.contains(when (index) { 0 -> "已打开终端会话"; 1 -> "退出码: 0"; else -> "退出码: 3" }))
        }
    }

    @Test fun everyExampleShowsAGuardHintWithoutCallingAnUnavailablePlugin() {
        sampleNames.forEach { name ->
            val source = assets.open("sample/终端/$name.js").bufferedReader().use { it.readText() }
            val result = smoke.runScript("""
                var messages = [];
                // Only isAvailable exists: any accidental call past the guard fails this test.
                new Function('terminal', 'toastLog', ${JSONObject.quote(source)})({isAvailable: function() { return false; }}, function(text) { messages.push(String(text)); });
                report({messages: messages});
            """)
            val messages = result.getJSONArray("messages")
            assertEquals(1, messages.length())
            assertTrue(messages.getString(0).contains("安装并启用 3-Shell Terminal"))
        }
    }

    private val sampleNames = listOf("在终端中打开脚本目录", "运行 npm 脚本并等待完成", "会话驱动与输出监听")
}
