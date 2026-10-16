package org.autojs.autojs.core.ui.inflater

import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.ui.ViewExtras
import org.autojs.autojs.core.ui.attribute.ViewAttributes
import org.autojs.autojs.core.ui.inflater.inflaters.ViewInflater
import org.autojs.autojs.engine.JavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.script.JavaScriptFileSource
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException
import java.lang.reflect.Modifier
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Host roadmap `docs/dev/android-xml-attributes-roadmap.md` A5: the coverage matrix of the Android XML attributes the
 * metadata table lists. Every attribute a class's own styleables define (and every layout attribute a container's
 * children take) is put in one category, decided the way the inflater decides at runtime:
 *
 * - `handler`: a hand-written handler of the view takes it, or the view creation reads it (theme, style);
 * - `setter`: the metadata fallback finds a public method of the view for it (A2);
 * - `params`: the fallback finds a public field or setter of the container's layout params, or a special rule (A3);
 * - `design`: a design-time attribute (`layout_editor_*`);
 * - `unsupported`: none of the above, so native XML skips it with a warning (A4);
 * - `api`: the attribute is newer than this device.
 *
 * The matrix is written to the app's files directory (`.python/android_attrs_coverage.py` pulls it and, with
 * `--update`, refreshes the snapshot `app/src/androidTest/assets/ui/android-attrs-coverage.json`, made on the newest
 * API). Against the snapshot: on the snapshot's API every supported attribute must stay supported and every class
 * must still be created; on any API a `handler` attribute must stay `handler` unless it is newer than the device.
 *
 * zh-CN: 宿主路线图 A5: 属性表所列 Android XML 属性的覆盖矩阵. 每个类自身 styleable 定义的属性 (及容器子视图的布局属性) 按运行时的判定归入一类:
 * 手写处理器 (含创建期读取的 theme / style), 视图公开 setter 兜底, 布局参数成员或特例, 设计期属性, 不支持, 或高于本机 API.
 * 矩阵写入应用 files 目录, 由 `.python/android_attrs_coverage.py` 拉取并可更新快照 (在最新 API 上生成). 与快照比较: 快照 API 上已支持的属性
 * 不得变为不支持, 类必须仍能创建; 任意 API 上手写处理器的属性除高于本机 API 外必须仍为手写处理器.
 */
class AndroidAttributeCoverageDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun coverageDoesNotFallBelowTheSnapshot() {
        val matrix = withUiRuntime { runtime -> computeMatrix(runtime) }
        File(instrumentation.targetContext.filesDir, OUTPUT).writeText(matrix.toString(1))
        Log.i(TAG, "coverage on API ${Build.VERSION.SDK_INT}: ${matrix.getJSONObject("totals")}")
        val snapshot = try {
            JSONObject(instrumentation.context.assets.open(SNAPSHOT).use { it.readBytes().toString(Charsets.UTF_8) })
        } catch (e: FileNotFoundException) {
            fail("no coverage snapshot; run .python/android_attrs_coverage.py --update on API $SNAPSHOT_API")
            return
        }
        val strict = Build.VERSION.SDK_INT == snapshot.getInt("api")
        val problems = ArrayList<String>()
        for (section in listOf(VIEWS, LAYOUTS)) {
            val expected = snapshot.getJSONObject(section)
            val actual = matrix.getJSONObject(section)
            val actualCategories = categoriesByKey(actual)
            for (className in expected.keys()) {
                if (!actual.has(className)) {
                    if (strict) problems += "$section $className is no longer covered (${matrix.getJSONObject(SKIPPED).optString(className)})"
                    continue
                }
                val categories = expected.getJSONObject(className)
                for (category in categories.keys()) {
                    if (category !in SUPPORTED) continue
                    val keys = categories.getJSONArray(category)
                    for (i in 0 until keys.length()) {
                        val key = keys.getString(i)
                        val now = actualCategories["$className|$key"]
                        when {
                            now == API -> Unit
                            category == HANDLER && now != HANDLER -> problems += "$section $className $key: handler -> $now"
                            strict && now !in SUPPORTED -> problems += "$section $className $key: $category -> $now"
                        }
                    }
                }
            }
        }
        assertTrue("coverage fell below the snapshot (${problems.size}):\n" + problems.take(40).joinToString("\n"), problems.isEmpty())
    }

    private fun categoriesByKey(section: JSONObject): Map<String, String> = HashMap<String, String>().apply {
        for (className in section.keys()) {
            val categories = section.getJSONObject(className)
            for (category in categories.keys()) {
                val keys = categories.getJSONArray(category)
                for (i in 0 until keys.length()) put("$className|${keys.getString(i)}", category)
            }
        }
    }

    // --------------------------------------------------------------------------------------------- the matrix

    private fun computeMatrix(runtime: ScriptRuntime): JSONObject {
        val inflater = runtime.ui.layoutInflater
        val table = AndroidAttributeTable.get(instrumentation.targetContext)
        val fallback = AndroidAttributeFallback(table, inflater.resourceParser)
        val skipped = TreeMap<String, String>()
        val created = TreeMap<String, String>()
        val totals = JSONObject()

        /** A view of [target] for the entry [className], created on the main thread; null (and noted) when it cannot be. */
        fun create(className: String, target: Class<*>): View? = try {
            inflater.createViewForName(target.name, HashMap(), null).also { view ->
                if (target.name == className && view.javaClass.name != className) created[className] = view.javaClass.name
            }
        } catch (e: Throwable) {
            var cause: Throwable = e
            while (cause.cause != null && cause.cause !== cause) cause = cause.cause!!
            if (target.name == className) skipped[className] = "${cause.javaClass.simpleName}: ${cause.message.orEmpty().take(160)}"
            null
        }

        /**
         * The categories of [members] on [className] itself, or on each concrete subclass the table lists when it is
         * abstract (the best one counts). Each view is created and classified in its own main-thread task, so the
         * script's window keeps answering input (a single long task made the system report it not responding).
         */
        fun evaluate(className: String, members: Map<String, AndroidAttributeTable.Member>, classify: (View) -> ((AndroidAttributeTable.Member) -> String)?): Map<String, String>? {
            val targets = concreteClasses(table, className)
            if (targets.isEmpty()) skipped[className] = "no class to create"
            val results = targets.mapNotNull { target ->
                onMain {
                    val view = create(className, target) ?: return@onMain null
                    val category = classify(view) ?: return@onMain null
                    members.mapValues { (_, member) -> category(member) }
                }
            }
            if (results.isEmpty()) return null
            return members.keys.associateWith { key -> best(results.map { it.getValue(key) }) }
        }

        val views = TreeMap<String, Any>()
        for (className in table.classNames()) {
            val own = table.ownMembersOf(className)
            if (own.isEmpty()) continue
            val categories = evaluate(className, own) { view ->
                val attributes = ViewExtras.getViewAttributes(runtime, view, inflater.resourceParser)
                val viewInflater = inflater.getViewInflater(view)
                return@evaluate { member -> viewCategory(table, view, attributes, viewInflater, fallback, member) }
            } ?: continue
            views[className] = categorize(categories, totals, VIEWS)
        }

        val layouts = TreeMap<String, Any>()
        for (className in table.classNames(layout = true)) {
            val own = table.ownMembersOf(className, layout = true)
            if (own.isEmpty()) continue
            val categories = evaluate(className, own) { view ->
                val container = view as? ViewGroup ?: return@evaluate null
                val child = View(container.context)
                val params = runCatching { container.addView(child); child.layoutParams }.getOrNull()
                val attributes = ViewExtras.getViewAttributes(runtime, child, inflater.resourceParser)
                return@evaluate { member -> layoutCategory(attributes, params, fallback, member) }
            } ?: continue
            layouts[className] = categorize(categories, totals, LAYOUTS)
        }

        return JSONObject().apply {
            put("schema", 1)
            put("api", Build.VERSION.SDK_INT)
            put("totals", totals)
            put(VIEWS, JSONObject(views as Map<*, *>))
            put(LAYOUTS, JSONObject(layouts as Map<*, *>))
            put(SKIPPED, JSONObject(skipped as Map<*, *>))
            put(CREATED, JSONObject(created as Map<*, *>))
        }
    }

    /** The most supported of the categories an attribute of an abstract class gets on its concrete subclasses. */
    private fun best(categories: List<String>): String = categories.minByOrNull { RANK.indexOf(it) } ?: UNSUPPORTED

    private fun categorize(categories: Map<String, String>, totals: JSONObject, section: String): JSONObject {
        val byCategory = TreeMap<String, MutableList<String>>()
        for ((key, category) in categories) byCategory.getOrPut(category) { ArrayList() } += key
        val sectionTotals = totals.optJSONObject(section) ?: JSONObject().also { totals.put(section, it) }
        val result = JSONObject()
        for ((name, keys) in byCategory) {
            keys.sort()
            result.put(name, JSONArray(keys))
            sectionTotals.put(name, sectionTotals.optInt(name) + keys.size)
        }
        return result
    }

    /** The order of [DynamicLayoutInflater.applyAttributeValue]: handlers, creation attributes, the metadata fallback. */
    private fun viewCategory(
        table: AndroidAttributeTable,
        view: View,
        attributes: ViewAttributes,
        viewInflater: ViewInflater<View>,
        fallback: AndroidAttributeFallback,
        member: AndroidAttributeTable.Member,
    ): String {
        val attr = member.attr
        if (Build.VERSION.SDK_INT < member.sinceApi) return API
        // `app:srcCompat` and the like are applied as their platform attributes.
        if (attr.namespace == "app") {
            DynamicLayoutInflater.COMPAT_ALIASES[attr.name]?.let { alias ->
                table.attr("android", alias)?.let { return viewCategory(table, view, attributes, viewInflater, fallback, AndroidAttributeTable.Member(it, member.styleable, 0)) }
            }
        }
        if (attr.name in attributes && !attributes.isDeclaredUnsupported(attr.name)) return HANDLER
        if (DynamicLayoutInflater.isCreationAttribute(view, attr.namespace, attr.name)) return HANDLER
        if (attr.name !in attributes && inflaterTakes(viewInflater, view, attr)) return HANDLER
        if (attr.name in AndroidAttributeSetters.SPECIAL || fallback.candidatesOf(view.javaClass, attr).isNotEmpty()) return SETTER
        return UNSUPPORTED
    }

    /**
     * Whether a view inflater takes the attribute itself (TextViewInflater's `drawableTop`, RadioGroupInflater's
     * `checkedButton`, ...). Only asked for names the view's attributes do not register, which the base inflater
     * answers with false without side effects; an inflater that takes the name may reject the empty value.
     */
    private fun inflaterTakes(viewInflater: ViewInflater<View>, view: View, attr: AndroidAttributeTable.Attr): Boolean = try {
        viewInflater.setAttr(view, attr.namespace, attr.name, "", null)
    } catch (e: UnsupportedAttributeException) {
        false
    } catch (e: Exception) {
        true
    }

    private fun layoutCategory(
        attributes: ViewAttributes,
        params: ViewGroup.LayoutParams?,
        fallback: AndroidAttributeFallback,
        member: AndroidAttributeTable.Member,
    ): String {
        val attr = member.attr
        if (Build.VERSION.SDK_INT < member.sinceApi) return API
        if (attr.name in attributes && !attributes.isDeclaredUnsupported(attr.name)) return HANDLER
        if (AndroidLayoutParamRules.isDesignTime(attr.name)) return DESIGN
        if (attr.name in AndroidLayoutParamRules.SPECIAL) return PARAMS
        if (params != null && fallback.layoutMembersOf(params.javaClass, attr).isNotEmpty()) return PARAMS
        return UNSUPPORTED
    }

    /** [className] itself when it can be instantiated, else every concrete subclass of it the table lists. */
    private fun concreteClasses(table: AndroidAttributeTable, className: String): List<Class<*>> {
        val type = runCatching { Class.forName(className) }.getOrNull() ?: return emptyList()
        fun concrete(c: Class<*>) = View::class.java.isAssignableFrom(c) && !Modifier.isAbstract(c.modifiers) && Modifier.isPublic(c.modifiers)
        if (concrete(type)) return listOf(type)
        return (table.classNames() + table.classNames(layout = true)).distinct().sorted()
            .mapNotNull { name -> runCatching { Class.forName(name) }.getOrNull() }
            .filter { concrete(it) && type.isAssignableFrom(it) }
    }

    // --------------------------------------------------------------------------------------------- the runtime

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<T>()
        val error = AtomicReference<Throwable>()
        instrumentation.runOnMainSync {
            try {
                result.set(block())
            } catch (e: Throwable) {
                error.set(e)
            }
        }
        error.get()?.let { throw it }
        return result.get()
    }

    /**
     * Runs [block] with the runtime of a ui script that has set a layout (so the inflater has the script's activity
     * as its context), then stops the script. Work with views goes through [onMain].
     */
    private fun <T> withUiRuntime(block: (ScriptRuntime) -> T): T {
        val context = instrumentation.targetContext
        val directory = File(context.cacheDir, "android-attrs-coverage-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        val ready = File(directory, "ready")
        val script = File(directory, "coverage.js")
        script.writeText(
            """
            "ui rhino";
            ui.layout('<frame/>');
            files.write(${JSONObject.quote(ready.absolutePath)}, 'ready');
            setInterval(function () {}, 1000);
            """.trimIndent(),
        )
        val failure = AtomicReference<Throwable>()
        var execution: ScriptExecution? = null
        try {
            execution = AutoJs.instance.scriptEngineService.execute(
                JavaScriptFileSource(script),
                object : SimpleScriptExecutionListener() {
                    override fun onException(execution: ScriptExecution, error: Throwable) {
                        failure.set(error)
                    }
                },
                ExecutionConfig(workingDirectory = directory.absolutePath),
            )
            val deadline = System.currentTimeMillis() + 60_000
            while (!ready.isFile && failure.get() == null && System.currentTimeMillis() < deadline) Thread.sleep(100)
            failure.get()?.let { throw AssertionError("the script failed: ${it.message}", it) }
            assertTrue("the script did not set its layout", ready.isFile)
            return block((execution!!.engine as JavaScriptEngine).runtime)
        } finally {
            execution?.engine?.let { if (!it.isDestroyed) it.forceStop() }
            directory.deleteRecursively()
        }
    }

    private companion object {
        const val TAG = "AndroidAttrCoverage"
        const val OUTPUT = "android-attrs-coverage.json"
        const val SNAPSHOT = "ui/android-attrs-coverage.json"
        const val SNAPSHOT_API = 37
        const val VIEWS = "views"
        const val LAYOUTS = "layouts"
        const val SKIPPED = "skipped"
        const val CREATED = "created"
        const val HANDLER = "handler"
        const val SETTER = "setter"
        const val PARAMS = "params"
        const val DESIGN = "design"
        const val UNSUPPORTED = "unsupported"
        const val API = "api"
        val SUPPORTED = setOf(HANDLER, SETTER, PARAMS, DESIGN)
        val RANK = listOf(HANDLER, SETTER, PARAMS, DESIGN, API, UNSUPPORTED)
    }
}
