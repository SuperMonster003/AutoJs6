package org.autojs.autojs.core.ui.widget

import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.JavaScriptFileSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * `<list>` item templates evaluate their `{{ expression }}` attributes against the bound data item,
 * and names the item does not define fall back to the script's global scope.
 *
 * zh-CN: `<list>` 项目模板的 `{{ expression }}` 属性以绑定的数据项为求值上下文, 数据项未定义的名称回退到脚本全局作用域.
 *
 * Guards the "列表与网格" example of the UI documentation and the "列表控件.js" sample,
 * which failed with `ReferenceError: "name" is not defined` once Rhino required a VarScope for name resolution.
 */
class JsListViewDataBindingDeviceTest {

    @Test
    fun listItemTemplateBindsDynamicAttributesFromDataSourceItems() {
        val report = runUiScript("""
            var suffix = ' (global)';
            var appName = 'AutoJs6';
            ui.layout(
                <vertical>
                    <text id="heading" text="Hello, {{appName}}"/>
                    <list id="people">
                        <vertical padding="8">
                            <text id="label" text="{{name}}: {{age}}"/>
                            <text id="nameText" text="姓名: {{name}}"/>
                            <text id="ageText" text="年龄: {{age}} 岁"/>
                            <text id="mixed" text="{{name + suffix}}"/>
                        </vertical>
                    </list>
                </vertical>
            );
            var itemViews = [];
            ui.people.on('item_bind', function (itemView, holder) {
                itemViews.push(itemView);
            });
            ui.people.setDataSource([{ name: '小明', age: 18 }, { name: '小红', age: 30 }]);
            function snapshot() {
                return {
                    heading: String(ui.heading.getText()),
                    items: itemViews.map(function (itemView) {
                        return {
                            label: String(itemView.label.getText()),
                            name: String(itemView.nameText.getText()),
                            age: String(itemView.ageText.getText()),
                            mixed: String(itemView.mixed.getText()),
                            nameWidth: itemView.nameText.getWidth(),
                        };
                    }),
                };
            }
            function isBound(s) {
                return s.items.length >= 2 && s.items.every(function (item) {
                    return item.label !== '' && item.name !== '' && item.age !== '' && item.mixed !== '';
                });
            }
            var deadline = Date.now() + 20000;
            function poll() {
                if (isBound(snapshot())) {
                    // Give RecyclerView one more layout pass so the bound item views have a measured width.
                    // zh-CN: 让 RecyclerView 再完成一次布局, 使已绑定的项目视图具有测量宽度.
                    setTimeout(function () { finish(snapshot()); }, 500);
                } else if (Date.now() > deadline) {
                    finish(snapshot());
                } else {
                    setTimeout(poll, 100);
                }
            }
            setTimeout(poll, 100);
        """)

        assertEquals(report.toString(), "Hello, AutoJs6", report.getString("heading"))
        val items = report.getJSONArray("items")
        assertEquals(report.toString(), 2, items.length())
        listOf("小明" to 18, "小红" to 30).forEachIndexed { index, (name, age) ->
            val item = items.getJSONObject(index)
            assertEquals("$name: $age", item.getString("label"))
            assertEquals("姓名: $name", item.getString("name"))
            assertEquals("年龄: $age 岁", item.getString("age"))
            assertEquals("$name (global)", item.getString("mixed"))
            assertTrue("item $index has a zero-width name view: $item", item.getInt("nameWidth") > 0)
        }
    }

    private fun runUiScript(body: String): JSONObject {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "list-binding-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        val report = File(directory, "report.json")
        val script = File(directory, "list-binding.js")
        script.writeText(buildString {
            appendLine("\"ui rhino\";")
            appendLine("function finish(result) { files.write(${JSONObject.quote(report.absolutePath)}, JSON.stringify(result)); ui.finish(); }")
            appendLine(body.trimIndent())
        })
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }
        var execution: ScriptExecution? = null
        try {
            execution = AutoJs.instance.scriptEngineService.execute(
                JavaScriptFileSource(script), listener,
                ExecutionConfig(workingDirectory = directory.absolutePath),
            )
            assertTrue("list binding script timed out", completed.await(60, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("list binding script failed: ${it.message}", it) }
            assertTrue("list binding script did not report its result", report.isFile)
            return JSONObject(report.readText())
        } finally {
            execution?.engine?.let { if (!it.isDestroyed) it.forceStop() }
            directory.deleteRecursively()
        }
    }
}
