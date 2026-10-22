package org.autojs.autojs.memory

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
import org.autojs.autojs.util.RhinoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Appendix C Q11 of docs/memory/ROADMAP.md: `RhinoUtils.standardObjects`, the process-level fallback scope, created for the
 * first time on a script thread (inside the script's Rhino Context, which `util.inspect` and other fallbacks can do) must not
 * break the scripts that run after it. Run it in a process of its own (`am instrument -e class ...`): if anything created the
 * standard objects before the test starts, it is skipped, because the case cannot be produced any more.
 * zh-CN: docs/memory/ROADMAP.md 附录 C Q11: 进程级兜底作用域 `RhinoUtils.standardObjects` 在脚本线程上 (位于脚本的 Rhino Context 中, `util.inspect`
 * 等兜底路径可能如此) 首次创建后, 不得使之后运行的脚本失败. 在单独的进程中运行 (`am instrument -e class ...`): 若测试开始前已有代码创建了标准对象,
 * 该情形已无法再现, 测试跳过.
 */
@RunWith(AndroidJUnit4::class)
class RhinoStandardObjectsDeviceTest {

    private val service get() = AutoJs.instance.scriptEngineService

    @Test
    fun standardObjectsFirstCreatedOnAScriptThreadDoNotBreakLaterScripts() {
        val standardObjects = RhinoUtils::class.java.getDeclaredField("standardObjects\$delegate")
            .apply { isAccessible = true }.get(null) as Lazy<*>
        assumeFalse("RhinoUtils.standardObjects was created before the test (by host start-up or an earlier test)", standardObjects.isInitialized())

        assertEquals(null, run("q11-first", FIRST))
        assertTrue("the first script did not create the standard objects", standardObjects.isInitialized())

        val failures = (1..3).mapNotNull { index -> run("q11-later-$index", LATER)?.let { "run $index: $it" } }
        assertTrue("scripts after the standard objects were created on a script thread failed: $failures", failures.isEmpty())
    }

    /** Runs [source] and returns its error, or null when it succeeded. zh-CN: 运行 [source], 返回其错误, 成功时返回 null. */
    private fun run(name: String, source: String): String? {
        val done = CountDownLatch(1)
        var error: String? = "timed out"
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                error = null
                done.countDown()
            }

            override fun onException(execution: ScriptExecution, e: Throwable) {
                error = (e.javaClass.simpleName + ": " + e.message).take(400)
                done.countDown()
            }
        }
        service.execute(StringScriptSource(name, source.trimIndent()), listener, ExecutionConfig())
        done.await(60, TimeUnit.SECONDS)
        return error
    }

    private companion object {

        // Creates the standard objects inside the script's Context, on its thread.
        // zh-CN: 在脚本的 Context 中, 于其线程上创建标准对象.
        const val FIRST = """
            var scope = Packages.org.autojs.autojs.util.RhinoUtils.getStandardObjects();
            if (!scope) throw new Error('memory Q11: no standard objects');
            util.inspect({ q11: [1, 2, 3] });
        """

        // What the start-up of every script and common scripts touch: the observe polyfills (array-observe needs
        // Object.getNotifier, where Q11 failed), util.inspect, JSON, Promise, require and util.versionCodes.
        // zh-CN: 每个脚本的启动与常见脚本会用到的内容: observe polyfill (array-observe 需要 Object.getNotifier, Q11 即在此失败), util.inspect,
        // JSON, Promise, require 与 util.versionCodes.
        const val LATER = """
            if (typeof Object.getNotifier !== 'function') throw new Error('memory Q11: Object.getNotifier is ' + typeof Object.getNotifier);
            if (typeof Array.observe !== 'function') throw new Error('memory Q11: Array.observe is ' + typeof Array.observe);
            // Only reads: an observer would keep the script running (the polyfill delivers changes on a timer).
            if (!Object.getNotifier({})) throw new Error('memory Q11: no notifier');
            [1].push(2);
            var text = util.inspect({ a: [1, 2], b: { c: 'd' } });
            if (text.indexOf('a') < 0) throw new Error('memory Q11: util.inspect returned ' + text);
            if (JSON.stringify({ a: 1 }) !== '{"a":1}') throw new Error('memory Q11: JSON.stringify');
            new Promise(function (resolve) { resolve(1); });
            if (!util.versionCodes) throw new Error('memory Q11: no util.versionCodes');
            console.log('memory Q11: ' + text.length);
        """
    }
}
