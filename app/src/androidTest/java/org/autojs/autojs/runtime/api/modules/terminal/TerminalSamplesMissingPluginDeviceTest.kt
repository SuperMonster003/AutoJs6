package org.autojs.autojs.runtime.api.modules.terminal

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opts into a device where the plugin is already absent; never uninstalls a user's plugin. */
class TerminalSamplesMissingPluginDeviceTest {
    @Test fun actualMissingPluginShowsTheHintInAllThreeExamples() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("requires a device without the terminal plugin", runCatching {
            context.packageManager.getPackageInfo("io.github.supermonster003.autojs6.plugin.three.shell.terminal", 0)
        }.isFailure)
        val smoke = TerminalScriptSmokeDeviceTest()
        listOf("在终端中打开脚本目录", "运行 npm 脚本并等待完成", "会话驱动与输出监听").forEach { name ->
            val source = context.assets.open("sample/终端/$name.js").bufferedReader().use { it.readText() }
            val result = smoke.runScript("""
                check(terminal.state() == 'not_installed', 'expected absent plugin');
                var messages = [];
                new Function('terminal', 'toastLog', ${JSONObject.quote(source)})(terminal, function(text) { messages.push(String(text)); });
                report({messages: messages});
            """)
            val messages = result.getJSONArray("messages")
            assertEquals(1, messages.length())
            assertTrue(messages.getString(0).contains("安装并启用 3-Shell Terminal"))
        }
    }
}
