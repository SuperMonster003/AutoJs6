package org.autojs.autojs.runtime.api.modules

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.compose.ScriptUiDispatcher
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Scale
import org.autojs.autojs.runtime.api.epub.EpubService
import org.autojs.autojs.runtime.api.gallery.GalleryService
import org.autojs.autojs.runtime.api.installer.InstallerService
import org.autojs.autojs.runtime.api.mail.MailService
import org.autojs.autojs.runtime.api.modules.compose.Compose
import org.autojs.autojs.runtime.api.modules.compose.ComposeScriptPlatform
import org.autojs.autojs.runtime.api.modules.compose.ComposeScriptValues
import org.autojs.autojs.runtime.api.modules.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.generated.ScriptModules
import org.autojs.autojs.runtime.api.modules.selector.SelectorGlobals
import org.autojs.autojs.runtime.api.speech.SpeechService
import org.autojs.autojs.runtime.api.terminal.TerminalService
import org.autojs.autojs.runtime.api.tts.TtsService
import org.autojs.autojs.runtime.resource.RuntimeResourceScope
import org.autojs.plugin.compose.api.catalog.ComponentInvocation
import org.autojs.plugin.compose.api.v2.ComposeUiV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.ScriptableObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * Appendix C Q110: the names a `@ScriptInstall` function defines at run time are declared on its annotation and written
 * to the module's `installs` in script-modules.json (with `installsComputed` for names computed from another source),
 * for the d.ts and documentation tools. Every hook that declares names is installed on the JVM, the names it added are
 * enumerated on the module object, on the global object and on the declared target objects, and compared with the
 * metadata: a name more or a name less fails, as do a different kind (function, getter, property) or different
 * attributes; names the hook computes must come from the source the metadata describes. A hook that declares nothing
 * must call nothing that defines a name (checked in its source).
 *
 * zh-CN: 附录 C Q110: @ScriptInstall 函数在运行时定义的名称在其注解上声明, 写入 script-modules.json 中模块的 installs (由其他来源计算的名称写
 * installsComputed), 供 d.ts 与文档工具使用. 每个声明了名称的钩子在 JVM 上安装, 枚举它在模块对象, 全局对象与声明的目标对象上添加的名称, 与元数据比较:
 * 多一个或少一个名称都失败, 种类 (function, getter, property) 或属性特性不同也失败; 钩子计算出的名称须来自元数据说明的来源. 什么都没声明的钩子在源码中
 * 不得调用任何定义名称的函数.
 */
class ScriptModuleInstallsTest {

    /** A hook to install: [fields] of the runtime to allocate, [before] the modules it reads, [computed] its computed names. */
    private class Case(
        val name: String,
        val fields: List<String> = emptyList(),
        val before: List<String> = emptyList(),
        val prepare: (DifferentialRhino) -> Unit = {},
        val computed: () -> Set<String>? = { null },
    )

    @Test
    fun hooksDefineTheNamesTheyDeclare() {
        val metadata = mainModules()
        var names = 0
        val failures = CASES.flatMap { case ->
            val module = metadata.getValue(case.name)
            names += module.getAsJsonArray("installs").size()
            DifferentialRhino().use { rhino -> compare(rhino, case, module) }
        }
        assertTrue("Install hooks differ from their declarations:\n" + failures.joinToString("\n"), failures.isEmpty())
        assertEquals("declared install names", 101 + 584, names)
    }

    @Test
    fun everyHookIsInstalledHereOrDefinesNothing() {
        val metadata = mainModules()
        val hooks = ScriptModules.bindings.filter { it.spec.hasInstall }.map { it.spec }
        assertTrue("a sub-module with an install hook", hooks.all { it.parent == null })
        val declaring = hooks.filter { spec -> metadata.getValue(spec.name).let { it.getAsJsonArray("installs").size() > 0 || !it.get("installsComputed").isJsonNull } }
        assertEquals(declaring.map { it.name }.sorted(), CASES.map { it.name }.sorted())
        val silent = hooks - declaring.toSet()
        assertEquals(listOf("engines", "plugins", "ui"), silent.map { it.name }.sorted())
        silent.forEach { spec ->
            val module = metadata.getValue(spec.name)
            val body = hookBody(File(module.get("source").asString.substringBeforeLast(':')).let { if (it.isAbsolute) it else File("..", it.path) }.readText(), module.get("install").asString)
            DEFINING_CALLS.forEach { call -> assertTrue("${spec.name}: the hook calls $call but declares no names", "$call(" !in body) }
        }
    }

    private fun compare(rhino: DifferentialRhino, case: Case, module: JsonObject): List<String> {
        val runtime = rhino.ktRuntime
        RuntimeModulesFixture.prepareHost(runtime, RuntimeModulesFixture.CORE_FIELDS + case.fields)
        RuntimeModulesFixture.setField(runtime, ScriptRuntime::class.java, "scale", Scale(runtime))
        RuntimeModulesFixture.setField(runtime, ScriptRuntime::class.java, "mProperties", ConcurrentHashMap<String, Any>())
        RuntimeModulesFixture.setField(runtime, ScriptRuntime::class.java, "closeableManager", RuntimeResourceScope("script-module-installs-test"))
        case.prepare(rhino)
        val global = rhino.ktScope.globalThis as ScriptableObject
        if (case.before.isNotEmpty()) rhino.mountNewModules(*case.before.toTypedArray())
        val installs = module.getAsJsonArray("installs").map { it.asJsonObject }
        val targets = installs.mapNotNull { i -> i.get("target").takeUnless { it.isJsonNull }?.asString }.distinct()
        val targetsBefore = targets.associateWith { ids(global.get(it, global)) }
        val globalBefore = ids(global)
        rhino.mountNewModules(case.name)
        val spec = ScriptModules.find(case.name)!!.spec
        val moduleObject = global.get(case.name, global)

        // Where a name was defined, as a script path: `module.name`, `name` (global) or `target.name`.
        val added = (ids(global) - globalBefore - globalNames(spec)) +
            (ids(moduleObject) - moduleNames(spec, moduleObject)).map { "${case.name}.$it" } +
            targets.flatMap { t -> (ids(global.get(t, global)) - targetsBefore.getValue(t)).map { "$t.$it" } }
        val declared = installs.flatMap { i ->
            val name = i.get("name").asString
            val shape = i.get("kind").asString + ":" + i.get("attributes").asInt
            val target = i.get("target").takeUnless { it.isJsonNull }?.asString
            when {
                target != null -> listOf("$target.$name" to shape)
                else -> listOfNotNull(
                    ("${case.name}.$name" to shape).takeIf { i.get("global").asString != "ONLY" },
                    (name to shape).takeIf { i.get("global").asString != "NONE" },
                )
            }
        }.toMap()

        val failures = mutableListOf<String>()
        val computed = case.computed()
        val undeclared = added.filter { it !in declared }
        val outside = if (computed == null) undeclared else undeclared.filter { it.substringAfterLast('.') !in computed }
        if (outside.isNotEmpty()) failures += "${case.name}: defined but not declared: $outside"
        if (computed != null && undeclared.isEmpty()) failures += "${case.name}: no name computed from ${module.get("installsComputed").asString}"
        val missing = declared.keys.filter { it !in added }
        if (missing.isNotEmpty()) failures += "${case.name}: declared but not defined: $missing"
        declared.filterKeys { it in added }.forEach { (path, shape) ->
            val holder = if ('.' in path) path.substringBeforeLast('.') else "globalThis"
            val actual = Context.toString(rhino.evalKt("$DESCRIBE($holder, '${path.substringAfterLast('.')}')"))
            if (actual != shape) failures += "${case.name}: $path is $actual, declared $shape"
        }
        return failures
    }

    /** Own string keys of [value], non-enumerable ones included, without building lazy names. */
    private fun ids(value: Any?): Set<String> = (value as ScriptableObject).allIds.filterIsInstance<String>().toSet()

    /** What the annotations of [spec] bind on the global object: the module names and the global members. */
    private fun globalNames(spec: ScriptModuleSpec): Set<String> = (listOf(spec) + ScriptModules.childrenOf(spec.className).map { it.spec }).flatMap { s ->
        s.functions.filter { it.onGlobal }.flatMap { listOf(it.name) + it.aliases } + s.accessors.filter { it.onGlobal }.map { it.name } + s.javaClasses.filter { it.onGlobal }.map { it.name }
    }.toSet() + spec.names

    /** What the annotations of [spec] bind on the module object (with the own names of a callable module object). */
    private fun moduleNames(spec: ScriptModuleSpec, moduleObject: Any?): Set<String> {
        val members = spec.functions.filter { it.onModule }.flatMap { listOf(it.name) + it.aliases } + spec.accessors.filter { it.onModule }.map { it.name } +
            spec.javaClasses.filter { it.onModule }.map { it.name } + ScriptModules.childrenOf(spec.className).flatMap { it.spec.names }
        val marker = if (spec.functions.any { it.functionalToString }) listOf("__augmented_custom_to_string__") else emptyList()
        return (members + marker + if (moduleObject is Function) FUNCTION_OWN_NAMES else emptySet()).toSet()
    }

    /** script-modules.json of the host (the unit test fixtures have their own), by module name. */
    private fun mainModules(): Map<String, JsonObject> {
        val text = javaClass.classLoader!!.getResources("autojs6/script-modules.json").toList().map { it.readText() }
            .filter { "\"class\": \"org.autojs.autojs.runtime.api.modules.base64.Base64Module\"" in it }.distinct().single()
        return JsonParser.parseString(text).asJsonObject.getAsJsonArray("modules").map { it.asJsonObject }
            .filter { it.get("parent").isJsonNull }.associateBy { it.get("name").asString }
    }

    /** The body of the function [name] in [source], up to the next member declaration. */
    private fun hookBody(source: String, name: String): String {
        val start = Regex("""\n(\s*)fun $name\(""").find(source) ?: throw AssertionError("no function $name")
        val next = Regex("""\n${start.groupValues[1]}(@|(private |internal |override )*(fun|val|var) |})""").find(source, start.range.last)
        return source.substring(start.range.first, next?.range?.first ?: source.length)
    }

    private class NoPluginPlatform : ComposeScriptPlatform {
        override val dispatcher = object : ScriptUiDispatcher {
            private val origin = Thread.currentThread()
            override fun isDispatchThread() = Thread.currentThread() === origin
            override fun post(action: () -> Unit) = Unit
            override fun close() = Unit
        }
        override val mainExecutor = Executor { it.run() }
        override fun version(): Map<String, Any>? = null
        override fun createRenderer(sessionId: Int, beforeMount: (Any) -> Unit, onDestroyed: () -> Unit): ComposeScriptPlatform.Opened = error("No plugin in the JVM")
        override fun resolveImage(value: Any): ComposeScriptValues.ResolvedImage = error("No images in the JVM")
        override fun post(delayMillis: Long, action: () -> Unit) = Unit
        override fun warn(message: String) = Unit
        override fun report(error: Throwable) = Unit
        override fun close() = Unit
    }

    private companion object {

        /** `kind:attributes` of an own property, as script-modules.json writes them (a constructor is a property). */
        const val DESCRIBE = "(function (o, n) { var d = Object.getOwnPropertyDescriptor(o, n), v = d.value;" +
            " var kind = 'get' in d || 'set' in d ? 'getter' : typeof v === 'function' && !(v.prototype && v.prototype.constructor === v) ? 'function' : 'property';" +
            " return kind + ':' + ((d.writable === false ? 1 : 0) | (d.enumerable ? 0 : 2) | (d.configurable ? 0 : 4)); })"

        /** Own names of a function object (a callable module object) that are not members. */
        val FUNCTION_OWN_NAMES = setOf("length", "name", "arity", "prototype", "arguments", "caller")

        /** What a hook calls to define a name (ScriptModuleInstallation, AugmentMembers, Rhino). */
        val DEFINING_CALLS = listOf("defineLazy", "defineProperties", "defineProperty", "onBuilt", "putProperty")

        fun service(vararg pairs: Pair<Class<*>, String>): (DifferentialRhino) -> Unit = { side ->
            pairs.forEach { (type, field) -> HostModuleTestSupport.setField(side.ktRuntime, field, DifferentialRhino.allocateWithoutConstructor(type)) }
        }

        val CASES = listOf(
            Case("app", fields = listOf("app"), prepare = { RuntimeModulesFixture.installAppContext() }),
            Case("compose", prepare = { side -> ScriptRuntimeFields.setCompose(side.ktRuntime, Compose(side.ktRuntime, NoPluginPlatform())) },
                computed = { ComposeUiV2.interopCatalog.components.filter { it.invocation == ComponentInvocation.NODE }.map { it.name }.toSet() }),
            Case("epub", prepare = service(EpubService::class.java to "epub")),
            Case("flow", before = listOf("global")),
            Case("gallery", prepare = service(GalleryService::class.java to "gallery")),
            // The ImGui plugin's API names (ImguiNames): forwarding functions and getters, defined without the plugin.
            // zh-CN: ImGui 插件的 API 名称 (ImguiNames): 转发函数与 getter, 定义时不需要插件.
            Case("imgui"),
            Case("installer", prepare = service(InstallerService::class.java to "installer")),
            Case("mail", prepare = service(MailService::class.java to "mail")),
            Case("mediainfo"),
            Case("pinyin"),
            Case("selector", computed = { SelectorGlobals.names.toSet() }),
            Case("speech", prepare = service(SpeechService::class.java to "speech", TtsService::class.java to "tts")),
            Case("terminal", prepare = service(TerminalService::class.java to "terminal")),
        )

    }

}
