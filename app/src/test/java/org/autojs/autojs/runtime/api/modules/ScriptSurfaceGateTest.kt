package org.autojs.autojs.runtime.api.modules

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.generated.ScriptModules
import org.autojs.autojs.runtime.api.modules.global.GlobalJavaClasses
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * The script API surface gate of roadmap P6.2. It runs in `:app:testAppDebugUnitTest`, so in the fast checks of
 * `prepush_check.py` and in CI (`android.yml`), without a device.
 *
 * - [runtimeSurface] mounts every top-level module of the layer ([ScriptModuleLayer.TOP_LEVEL_ORDER]) on the JVM,
 *   describes the global object with the functions of `tools/augment/surface_snapshot.js` (the part before
 *   `let snapshot = {`) and flattens the result into the lines of `device_surface_snapshot.py`. The subtrees of the
 *   global object itself (`globalThis`, `global`) and of the `$` aliases that are the same value as their base are not repeated (`@alias.` lines say
 *   which); values that change with every build (versions, resource ids) are masked and failures recorded as `error`.
 *   Names the engine defines when it initializes (`require`, `runtime`, ...) are not part of this surface.
 * - [metadataSurface] projects `autojs6/script-modules.json`: one line per module, function (with the declared arity
 *   and the parameters of each overload, which the runtime surface cannot see), accessor, install and Java class.
 *
 * Each writes its lines to `app/build/script-modules-surface/` and fails when they differ from the file of the same name
 * in `tools/augment/baseline/`, listing the first differences. After an intended change of the surface, run this test,
 * then `py -3.12 -I tools/augment/surface_gate.py --update`, and commit the baseline diff with the change. Without a
 * baseline the test is skipped.
 *
 * zh-CN: ROADMAP P6.2 的脚本 API 表面门禁. 随 :app:testAppDebugUnitTest 运行, 即进入 prepush_check.py 的快速检查与 CI (android.yml), 不需要设备.
 * [runtimeSurface] 在 JVM 上挂载新层的全部顶层模块, 用 surface_snapshot.js 中 `let snapshot = {` 之前的函数描述全局对象, 展平成
 * device_surface_snapshot.py 的行格式; 全局对象自身 (globalThis, global) 与和本名是同一值的 $ 别名不重复展开 (由 @alias. 行记录), 随构建变化的值 (版本, 资源 id) 掩码,
 * 失败一律记为 error; 引擎初始化时定义的名称 (require, runtime 等) 不在此表面内. [metadataSurface] 投影 script-modules.json: 模块, 函数 (含各重载声明的
 * arity 与参数, 运行时表面看不到), accessor, install 与 Java 类各一行. 两者把结果写到 app/build/script-modules-surface/, 与 tools/augment/baseline/ 下的
 * 同名基线不同时失败并列出前若干行差异. 有意改动表面时先跑本测试, 再运行 py -3.12 -I tools/augment/surface_gate.py --update, 把基线差异与改动一起
 * 提交. 没有基线时跳过.
 */
class ScriptSurfaceGateTest {

    @Test
    fun runtimeSurface() {
        val lines = DifferentialRhino().use { rhino ->
            RuntimeModulesFixture.installAppContext()
            val runtime = rhino.ktRuntime
            allocateHost(runtime)
            RuntimeModulesFixture.prepareHost(runtime)
            val mounter = ScriptModuleMounter(ScriptModules, runtime, runtime.topLevelScope, rhino.ktScope.globalThis, order = ScriptModuleLayer.ORDER)
            val failed = ScriptModuleLayer.TOP_LEVEL_ORDER.filterNot { name ->
                runCatching { mounter.install(name) }.onFailure { println("Mounting $name failed: $it") }.getOrDefault(false)
            }
            (failed.map { "@mount.$it\terror" } + snapshot(rhino)).sorted()
        }
        gate(RUNTIME_FILE, "The global object with every top-level module of the layer mounted on the JVM (ScriptSurfaceGateTest.runtimeSurface)", lines)
    }

    @Test
    fun metadataSurface() {
        val text = javaClass.classLoader!!.getResources("autojs6/script-modules.json").toList().map { it.readText() }
            .filter { "\"class\": \"org.autojs.autojs.runtime.api.modules.base64.Base64Module\"" in it }.distinct().single()
        val lines = ArrayList<String>()
        JsonParser.parseString(text).asJsonObject.getAsJsonArray("modules").map { it.asJsonObject }.forEach { module ->
            val path = listOfNotNull(module.string("parent"), module.string("name")).joinToString(".")
            lines += "module $path\t" + fields(module, "names", "aliases", "attributes", "dollarPrefix", "eager", "emitter", "assign", "proxy", "builtIn", "prototype", "install") +
                    " invoke=${module.present("invoke")} construct=${module.present("construct")} installsComputed=${module.present("installsComputed")}"
            module.objects("functions").forEach { function ->
                val overloads = function.objects("overloads").joinToString(";") { overload ->
                    val arity = overload.get("arity")?.takeIf { it.isJsonObject }?.asJsonObject?.let { "${it.get("min")}-${it.get("max")}" } ?: "-"
                    arity + overload.objects("params").joinToString("", "(", ")") { if (it.flag("vararg")) "v" else if (it.flag("optional")) "o" else "r" }
                }
                lines += "fn $path.${function.string("name")}\t" + fields(function, "attributes", "global", "length", "aliases", "builtIn", "builtInOnly", "toStringRole") + " overloads=$overloads"
            }
            module.objects("accessors").forEach { lines += "accessor $path.${it.string("name")}\t" + fields(it, "kind", "writable", "attributes", "global", "builtIn") }
            module.objects("installs").forEach { lines += "install $path:${it.string("name")}\t" + fields(it, "kind", "attributes", "global", "target", "arity") }
            module.objects("javaClasses").forEach { lines += "javaClass $path.${it.string("name")}\t" + fields(it, "type", "global", "attributes") }
        }
        gate(METADATA_FILE, "autojs6/script-modules.json of the host, one line per module, function, accessor, install and Java class (ScriptSurfaceGateTest.metadataSurface)", lines.sorted())
    }

    /**
     * Gives the runtime the host objects the modules read when they are installed: fields of `java.` types get an
     * instance from their constructor, the others one allocated without it (as [RuntimeModulesFixture.prepareHost]),
     * whose own `java.util` fields then get empty instances (`UI.mProperties`). Types that cannot be allocated on the JVM
     * (`Device`, whose static initializer needs Android) and `Lazy` delegates stay null; what reads them records `error`.
     * `ui` is not mounted (`@mount.ui error`): its install changes the `UI` object, which as a script object allocated
     * without its constructor has no property map.
     */
    private fun allocateHost(runtime: ScriptRuntime) {
        ScriptRuntime::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) && !it.type.isPrimitive }.forEach { field ->
            field.isAccessible = true
            if (field.get(runtime) == null && isConcrete(field.type)) runCatching { field.set(runtime, newHost(field.type)) }
        }
    }

    private fun newHost(type: Class<*>): Any? = when {
        type.name.startsWith("java.") -> type.getDeclaredConstructor().newInstance()
        else -> RuntimeModulesFixture.allocate(type).also { host ->
            generateSequence<Class<*>>(type) { it.superclass }.takeWhile { it.name.startsWith("org.autojs.") }.forEach { owner ->
                owner.declaredFields.filter { !Modifier.isStatic(it.modifiers) && it.type.name.startsWith("java.util.") && isConcrete(it.type) }.forEach { field ->
                    field.isAccessible = true
                    if (field.get(host) == null) runCatching { field.set(host, field.type.getDeclaredConstructor().newInstance()) }
                }
            }
        }
    }

    private fun isConcrete(type: Class<*>) = !type.isInterface && !type.isArray && !Modifier.isAbstract(type.modifiers) && type != String::class.java

    /** The lines of the global object, described by the functions of surface_snapshot.js and [SNAPSHOT_PART]. */
    private fun snapshot(rhino: DifferentialRhino): List<String> {
        val source = File(SNAPSHOT_SCRIPT).readText()
        val functions = source.substringBefore(SNAPSHOT_MARK, "").also { check(it.isNotEmpty()) { "$SNAPSHOT_SCRIPT has no line starting with `$SNAPSHOT_MARK`" } }
            .replace("let LOOKUP_NAMES = [];", GlobalJavaClasses.NAMES.joinToString(", ", "let LOOKUP_NAMES = [", "];") { "\"$it\"" })
        val json = rhino.evalKt("(function () {\nlet global = globalThis;\n$functions\n$SNAPSHOT_PART\n})()").toString()
        val snapshot = JsonParser.parseString(json).asJsonObject
        val lines = ArrayList<String>()
        flatten(snapshot.getAsJsonObject("global"), "", lines)
        snapshot.getAsJsonObject("aliases").entrySet().forEach { (name, value) -> lines += "@alias.$name\t${value.asString.takeUnless { it.startsWith("error") } ?: "error"}" }
        snapshot.getAsJsonObject("lookup").entrySet().forEach { (name, element) ->
            val entry = element.asJsonObject
            val value = if (entry.has("error")) "error" else "bare=${entry.string("bare")} ${entry.string("type")} ${entry.string("text")}"
            lines += "@lookup.$name\tin=${entry.get("inGlobal")} own=${entry.get("own")}\t$value"
        }
        return lines.map { it.replace(RESOURCE_ID, "res:<id>") }
    }

    /** `flatten` of device_surface_snapshot.py, with failures as `error` and the version values masked. */
    private fun flatten(props: JsonObject, prefix: String, lines: MutableList<String>) {
        props.entrySet().forEach { (name, element) ->
            val entry = element.asJsonObject
            val path = prefix + name
            if (entry.has("error") || entry.has("missing")) {
                lines += "$path\terror"
                return@forEach
            }
            val value = entry.get("value")?.takeIf { it.isJsonObject }?.asJsonObject
            val summary = when {
                entry.flag("accessor") -> "accessor"
                VERSION_PATH.containsMatchIn(path) && value?.has("value") == true -> "${value.string("type")} <build>"
                else -> summary(value)
            }
            lines += "$path\t${attributes(entry)}\t$summary"
            value?.get("props")?.takeIf { it.isJsonObject }?.let { flatten(it.asJsonObject, "$path.", lines) }
        }
    }

    /** `attrs` of device_surface_snapshot.py. */
    private fun attributes(entry: JsonObject) = buildString {
        append(if (entry.flag("enumerable")) 'E' else '-')
        if (entry.flag("accessor")) {
            append('A').append(if (entry.flag("hasGetter")) 'G' else '-').append(if (entry.flag("hasSetter")) 'S' else '-')
        } else {
            append(if (entry.flag("writable")) 'W' else '-')
        }
        append(if (entry.flag("configurable")) 'C' else '-')
    }

    /** `value_summary` of device_surface_snapshot.py (Python `repr` for strings), with errors as `error`. */
    private fun summary(value: JsonObject?): String {
        value ?: return "?"
        if (value.has("error")) return "error"
        val type = value.string("type")
        return when {
            type == "function" -> "function name=${repr(value.string("name"))} length=${value.get("length") ?: "None"}"
            type == "object" -> "object ${value.string("tag")} " + listOfNotNull(
                value.string("javaClass")?.takeIf { it.isNotEmpty() },
                value.get("length")?.let { "length=$it" },
                value.getAsJsonArray("proto")?.takeIf { it.size() > 0 }?.joinToString(" > ", "proto=") { it.asString },
            ).joinToString(" ")
            value.has("value") -> "$type ${repr(value.string("value"))}"
            else -> type.toString()
        }
    }

    /** Python's `repr` of a string, so that the lines can be compared with device snapshots (surface_compare.py). */
    private fun repr(text: String?): String {
        text ?: return "None"
        val quote = if ('\'' in text && '"' !in text) '"' else '\''
        return buildString {
            append(quote)
            text.codePoints().forEach { c ->
                when {
                    c == quote.code || c == '\\'.code -> append('\\').appendCodePoint(c)
                    c == '\t'.code -> append("\\t")
                    c == '\n'.code -> append("\\n")
                    c == '\r'.code -> append("\\r")
                    c == ' '.code || c > ' '.code && c != 0x7f && Character.getType(c) !in NON_PRINTABLE -> appendCodePoint(c)
                    c <= 0xff -> append("\\x%02x".format(c))
                    c <= 0xffff -> append("\\u%04x".format(c))
                    else -> append("\\U%08x".format(c))
                }
            }
            append(quote)
        }
    }

    /** Writes [lines] to the build directory and fails with their differences from the baseline [fileName]. */
    private fun gate(fileName: String, description: String, lines: List<String>) {
        val actual = File(OUTPUT_DIR, fileName).apply { parentFile!!.mkdirs() }
        actual.writeText((listOf("# $description", "# $UPDATE_HINT") + lines).joinToString("\n", postfix = "\n"))
        val baseline = File(BASELINE_DIR, fileName)
        assumeTrue("No baseline tools/augment/baseline/$fileName; to create it: $UPDATE_HINT", baseline.isFile)
        val expected = baseline.readLines().filter { it.isNotEmpty() && !it.startsWith("#") }
        if (expected == lines) return
        val removed = expected - lines.toSet()
        val added = lines - expected.toSet()
        val diff = (removed.map { "- $it" } + added.map { "+ $it" }).sortedBy { it.substring(2) }
        fail(buildString {
            appendLine("The script API surface differs from tools/augment/baseline/$fileName: ${removed.size} lines removed, ${added.size} added")
            diff.take(MAX_DIFF_LINES).forEach { appendLine("    $it") }
            if (diff.size > MAX_DIFF_LINES) appendLine("    ... ${diff.size - MAX_DIFF_LINES} more")
            if (diff.isEmpty()) appendLine("    (the same lines, in another order or with duplicates)")
            appendLine("Actual: app/build/script-modules-surface/$fileName")
            append("If the change is intended: $UPDATE_HINT, and commit the baseline with the change")
        })
    }

    private fun JsonObject.string(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString

    private fun JsonObject.flag(name: String) = get(name)?.takeUnless { it.isJsonNull }?.asBoolean == true

    private fun JsonObject.present(name: String) = get(name)?.isJsonNull == false

    private fun JsonObject.objects(name: String): List<JsonObject> = get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asJsonObject }.orEmpty()

    /** `name=<JSON value>` of each of [names] (`null` when absent). */
    private fun fields(entry: JsonObject, vararg names: String) = names.joinToString(" ") { "$it=${entry.get(it) ?: "null"}" }

    private companion object {

        const val RUNTIME_FILE = "surface-jvm.txt"
        const val METADATA_FILE = "script-modules-surface.txt"
        const val OUTPUT_DIR = "build/script-modules-surface"
        const val BASELINE_DIR = "../tools/augment/baseline"
        const val SNAPSHOT_SCRIPT = "../tools/augment/surface_snapshot.js"
        const val SNAPSHOT_MARK = "let snapshot = {"
        const val UPDATE_HINT = "run ScriptSurfaceGateTest, then `py -3.12 -I tools/augment/surface_gate.py --update`"
        const val MAX_DIFF_LINES = 40

        val RESOURCE_ID = Regex("res:[0-9]+")

        /** autojs.versionCode / versionName / versionDate, autojs.version.code / name / date and the app ones. */
        val VERSION_PATH = Regex("(^|[.])version(Code|Name|Date|[.]code|[.]name|[.]date)$")

        /** The general categories Python's `str.isprintable` rejects (besides the ASCII space). */
        val NON_PRINTABLE = setOf(
            Character.CONTROL, Character.FORMAT, Character.SURROGATE, Character.PRIVATE_USE, Character.UNASSIGNED,
            Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR, Character.SPACE_SEPARATOR,
        ).map { it.toInt() }.toSet()

        /**
         * Replaces the rest of surface_snapshot.js: resolves the standard constructors Rhino loads lazily (`JSON`, `Math`,
         * ...; a first use does it on the device), then describes every own property of global, one level only for the
         * global object itself and for the `$` aliases that are the same value as their base. The E4X constructors stay lazy: here
         * no XML library is bound to their scope, and reading `XML.prettyPrinting` throws a Java exception a script cannot
         * catch.
         */
        const val SNAPSHOT_PART = """
            let E4X = { XML: 1, XMLList: 1, Namespace: 1, QName: 1 };
            Object.getOwnPropertyNames(global).forEach(name => {
                if (E4X[name]) return;
                try {
                    let desc = Object.getOwnPropertyDescriptor(global, name);
                    if (desc && javaClassOf(desc.value) === "org.mozilla.javascript.LazilyLoadedCtor") void global[name];
                } catch (e) {
                    // Recorded below: as the error of its descriptor, or as the lazy constructor it still is.
                }
            });
            let aliases = describeAliases();
            let props = {};
            Object.getOwnPropertyNames(global).forEach(name => {
                let one = {};
                let desc;
                try {
                    desc = Object.getOwnPropertyDescriptor(global, name);
                    Object.defineProperty(one, name, desc);
                } catch (e) {
                    props[name] = { error: String(e) };
                    return;
                }
                props[name] = describeOwn(one, desc.value === global || aliases[name] === "same" ? MAX_DEPTH : 0)[name];
            });
            return JSON.stringify({ global: props, aliases: aliases, lookup: describeLookup(LOOKUP_NAMES) });
        """

    }

}
