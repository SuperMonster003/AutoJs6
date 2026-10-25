package org.autojs.autojs.runtime.api.modules

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.core.automator.pick.PickArguments
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Scale
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.automator.AutomatorArgs
import org.autojs.autojs.runtime.api.modules.flow.FlowArgs
import org.autojs.autojs.runtime.api.modules.toolkit.ToolkitArgs
import org.autojs.autojs.runtime.resource.RuntimeResourceScope
import org.autojs.autojs.util.RhinoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.RhinoException
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Appendix C Q112: an overload whose only script parameter is a vararg it counts itself declares the argument counts of
 * its real signatures (`arity` of the overload in script-modules.json); so does a function an install hook defines
 * (`arity` of the install name, Q127). Each declared arity is compared with the check of the function body. A delegate
 * to the current augment module, and a function of the new layer that compares the count with numbers in its body, is
 * checked in the source: the first `ensureArguments*`, `count(name, args, a..b)`, `args.size in a..b`, `args.size >= n`,
 * `if (args.isEmpty()) throw`, `arityError(name, min, max, …)` or `composeArguments(args, min, max?, …)` of the body, or
 * of the function it hands its arguments to (up to three calls deep); the install functions of mail and compose where
 * the current module defines them ([INSTALL_DEFINITIONS]). The checks of the new layer that parse
 * the arguments by their types (`FlowArgs`, `ToolkitArgs`, `AutomatorArgs`, `Color`, `console`) are sampled on the JVM:
 * calls with the fewest and the most arguments pass the check, a call with one argument less or one more is refused
 * with a TypeError or RangeError. A wait or a flow tool skips nullish arguments and reads an options object beside the
 * numbers, as the current parser; its samples are the positional forms its arity counts (appendix C Q126).
 *
 * zh-CN: 附录 C Q112: 唯一的脚本参数是自行计数的 vararg 的重载, 声明其真实签名的参数个数 (script-modules.json 中重载的 arity); install 钩子定义的函数
 * 同样声明 (install 名称的 arity, Q127). 每个声明的 arity 都与函数体的检查比较. 委托给现有 augment 模块的函数, 以及在函数体中把个数与数字比较的新层函数,
 * 在源码中核对: 函数体中第一个 ensureArguments*, count(name, args, a..b), args.size in a..b, args.size >= n, if (args.isEmpty()) throw,
 * arityError(name, min, max, …) 或 composeArguments(args, min, max?, …), 没有时看它把参数交给的函数 (最多三层); mail 与 compose 的 install 函数在现有
 * 模块定义它们之处核对 (INSTALL_DEFINITIONS). 按类型解析参数的新层检查 (FlowArgs, ToolkitArgs, AutomatorArgs, Color, console) 在 JVM 上抽样: 参数最少与最多的调用通过检查, 少一个或多一个参数的调用以
 * TypeError 或 RangeError 拒绝. 等待与 flow 工具同现有解析器一样跳过 nullish 参数, 并在数字之外接受一个选项对象; 其样例是 arity 所计的位置形式 (附录 C Q126).
 */
class ScriptModuleArityTest {

    /**
     * A declared arity: the script path of the overload (`module.name`, `module.()`, `module.new`) or of the install
     * function (`module.name`, `name` on the global object, `target.name`), its source, its counts, and for an install
     * function the module path of the hook ([install]).
     */
    private class Declared(val key: String, val source: String, val min: Int, val max: Int, val install: String? = null) {
        override fun toString() = "$key ${counts(min, max)}"
    }

    /** Script arguments of sampled calls; [check] is the checker the function calls first, given the arguments of an accepted call. */
    private class Sample(val accepted: List<List<String>>, val refused: List<List<String>>, val check: ((String, Array<Any?>) -> Unit)? = null)

    @Test
    fun checksInTheSourceAgreeWithTheDeclaredCounts() {
        val declared = declared().filter { it.key !in SAMPLES }
        val failures = declared.mapNotNull { d ->
            val (file, line) = d.source.substringBeforeLast(':') to d.source.substringAfterLast(':').toInt()
            val checked = when (val module = d.install) {
                null -> Source.countsAt(File(ROOT, file), line)
                else -> INSTALL_DEFINITIONS[module]?.let { Source.definitionCounts(File(ROOT, it), d.key.substringAfterLast('.')) }
            }
            when (val counts = checked) {
                null -> "${d.key}: no argument check in the source, and no JVM sample"
                d.min to d.max -> null
                else -> "${d.key}: the source checks ${counts(counts.first, counts.second)}, declared ${counts(d.min, d.max)}"
            }
        }
        assertTrue("Declared arities differ from the checks in the source:\n" + failures.joinToString("\n"), failures.isEmpty())
        assertEquals("arities checked in the source", 166, declared.size)
    }

    @Test(timeout = 300_000)
    fun sampledChecksAgreeWithTheDeclaredCounts() {
        val declared = declared().associateBy { it.key }
        assertEquals("sampled overloads without a declared arity", emptyList<String>(), SAMPLES.keys.filter { it !in declared })
        DifferentialRhino().use { rhino ->
            prepare(rhino)
            val failures = SAMPLES.flatMap { (key, sample) -> sampled(rhino, declared.getValue(key), sample) }
            assertTrue("Sampled argument checks differ from the declared arities:\n" + failures.joinToString("\n"), failures.isEmpty())
        }
        assertEquals("arities sampled on the JVM", 89, SAMPLES.size)
        assertEquals("declared arities (overloads and install functions)", 178 + 77, declared.size)
    }

    private fun sampled(rhino: DifferentialRhino, d: Declared, sample: Sample): List<String> {
        val failures = mutableListOf<String>()
        val accepted = sample.accepted.map { it.size }.toSet()
        val refused = sample.refused.map { it.size }.toSet()
        if (d.min !in accepted || (if (d.max < 0) accepted.none { it > d.min } else d.max !in accepted)) failures += "$d: no accepted sample at both ends"
        if (d.min > 0 && d.min - 1 !in refused || d.max >= 0 && d.max + 1 !in refused) failures += "$d: no refused sample beyond both ends"
        sample.accepted.forEach { args ->
            val outcome = when (val check = sample.check) {
                null -> Context.toString(rhino.evalKt(attempt(call(d.key, args))))
                else -> try {
                    val array = rhino.evalKt("[${args.joinToString()}]") as NativeArray
                    check(d.key, Array(array.size) { array[it] })
                    PASSED
                } catch (e: RhinoException) {
                    e.reason()
                }
            }
            if (outcome != PASSED && !outcome.startsWith(OTHER)) failures += "$d: ${call(d.key, args)} is refused ($outcome)"
        }
        sample.refused.forEach { args ->
            val outcome = Context.toString(rhino.evalKt(attempt(call(d.key, args))))
            if (outcome == PASSED || outcome.startsWith(OTHER)) failures += "$d: ${call(d.key, args)} is not refused ($outcome)"
        }
        return failures
    }

    /** The modules of the samples on the new layer, with the runtime fields they read when called. */
    private fun prepare(rhino: DifferentialRhino) {
        val runtime = rhino.ktRuntime
        RuntimeModulesFixture.prepareHost(runtime)
        RuntimeModulesFixture.setField(runtime, ScriptRuntime::class.java, "scale", Scale(runtime))
        RuntimeModulesFixture.setField(runtime, ScriptRuntime::class.java, "mProperties", ConcurrentHashMap<String, Any>())
        RuntimeModulesFixture.setField(runtime, ScriptRuntime::class.java, "closeableManager", RuntimeResourceScope("script-module-arity-test"))
        HostModuleTestSupport.setField(runtime, "console", DifferentialRhino.allocateWithoutConstructor(ConsoleImpl::class.java))
        HostModuleTestSupport.setField(runtime, "scriptModules", rhino.mountNewModules("global", "util", "auto", "automator", "flow", "colors", "Color", "console", "selector"))
    }

    /** The arities of script-modules.json of the host (the unit test fixtures have their own). */
    private fun declared(): List<Declared> {
        val text = javaClass.classLoader!!.getResources("autojs6/script-modules.json").toList().map { it.readText() }
            .filter { "\"class\": \"org.autojs.autojs.runtime.api.modules.base64.Base64Module\"" in it }.distinct().single()
        val modules = JsonParser.parseString(text).asJsonObject.getAsJsonArray("modules").map { it.asJsonObject }
        val byClass = modules.associateBy { it.get("class").asString }
        fun path(m: JsonObject): String = m.get("parent").takeUnless { it.isJsonNull }?.let { path(byClass.getValue(it.asString)) + "." }.orEmpty() + m.get("name").asString
        return modules.flatMap { m ->
            val overloads = listOfNotNull(m.get("invoke").takeUnless { it.isJsonNull }?.let { "()" to it.asJsonObject }, m.get("construct").takeUnless { it.isJsonNull }?.let { "new" to it.asJsonObject }) +
                m.getAsJsonArray("functions").flatMap { f -> f.asJsonObject.getAsJsonArray("overloads").map { f.asJsonObject.get("name").asString to it.asJsonObject } }
            val installs = m.getAsJsonArray("installs").map { it.asJsonObject }.mapNotNull { i ->
                val name = i.get("name").asString
                val key = i.get("target").takeUnless { it.isJsonNull }?.let { "${it.asString}.$name" } ?: if (i.get("global").asString == "ONLY") name else "${path(m)}.$name"
                i.get("arity").takeUnless { it.isJsonNull }?.asJsonObject?.let { a -> Declared(key, i.get("source").asString, a.min(), a.max(), install = path(m)) }
            }
            overloads.mapNotNull { (name, o) ->
                o.get("arity").takeUnless { it.isJsonNull }?.asJsonObject?.let { a -> Declared("${path(m)}.$name", o.get("source").asString, a.min(), a.max()) }
            } + installs
        }
    }

    private fun JsonObject.min() = get("min").asInt

    private fun JsonObject.max() = get("max").takeUnless { it.isJsonNull }?.asInt ?: -1

    /** The argument checks in the source, as the class documentation lists them. */
    private object Source {

        private val texts = HashMap<File, String>()

        private fun text(file: File) = texts.getOrPut(file) { file.readText().replace("\r\n", "\n") }

        /** The counts the function declared at [line] of [file] checks. */
        fun countsAt(file: File, line: Int): Pair<Int, Int>? {
            val source = text(file)
            val offset = source.split('\n').take(line - 1).sumOf { it.length + 1 }
            return function(file, FUN.find(source, maxOf(0, offset - 1)) ?: return null)?.let { (body, param) -> counts(file, body, param, 0) }
        }

        private fun counts(file: File, body: String, param: String, depth: Int): Pair<Int, Int>? {
            check(body, param)?.let { return it }
            if (depth == 3) return null
            val p = Regex.escape(param)
            // A function of the current augment module the arguments go to (`impl.open(runtime, args)`, `Yolo.load(runtime, args)`).
            Regex("""\b(\w+)\.(\w+)\((?:runtime,\s*)?\*?$p\b""").findAll(body).forEach { m ->
                val target = augmentClass(file, m.groupValues[1]) ?: return@forEach
                return named(target, m.groupValues[2])?.let { (b, a) -> counts(target, b, a, depth + 1) }
            }
            // A function of the same file (`mount(args)`, `prepare(args)`), not the function itself (its declaration).
            val own = Regex("""fun\s+(?:<[^>]*>\s*)?(\w+)\(""").find(body)?.groupValues?.get(1)
            return Regex("""(?<![.\w])(\w+)\([^()]*\b$p\b""").findAll(body).map { it.groupValues[1] }.filter { it != own }
                .firstNotNullOfOrNull { named(file, it) }?.let { (b, a) -> counts(file, b, a, depth + 1) }
        }

        /**
         * The counts the definition of the function [name] in [file] checks (an install function, defined at run time):
         * an entry `"name" to { args -> … }`, `defineFunction(holder, "name") { args -> … }`, `defineOp(holder, "name",
         * a..b)` (which defines `nameAsync` as well), or a helper called with `"name"` whose body calls `defineOp`.
         */
        fun definitionCounts(file: File, name: String): Pair<Int, Int>? {
            val source = text(file)
            val n = Regex.escape(name)
            Regex("""\n([ \t]*)(?:"$n" to \{|defineFunction\(\s*\w+,\s*"$n"\)\s*\{)\s*(\w+)\s*->""").find(source)?.let { m ->
                val next = Regex("""\n${m.groupValues[1]}(?=[^ \t\n])""").find(source, m.range.last)
                return counts(file, source.substring(m.range.first, next?.range?.first ?: source.length), m.groupValues[2], 0)
            }
            val base = Regex.escape(name.removeSuffix("Async"))
            Regex("""defineOp\(\s*\w+,\s*"$base",\s*(\d+)\.\.(\d+)\)""").find(source)?.let { return it.int(1) to it.int(2) }
            val helper = Regex("""\n[ \t]*(\w+)\(\s*"$base"""").find(source) ?: return null
            val body = declaration(file, helper.groupValues[1])?.let { body(file, it) } ?: return null
            return Regex("""defineOp\(\s*\w+,\s*\w+,\s*(\d+)\.\.(\d+)\)""").find(body)?.let { it.int(1) to it.int(2) }
        }

        /** The first check of the count of [param] in [body]. */
        private fun check(body: String, param: String): Pair<Int, Int>? {
            val p = Regex.escape(param)
            val checks = listOf<Pair<Regex, (MatchResult) -> Pair<Int, Int>>>(
                Regex("""ensureArguments(OnlyOne|IsEmpty|NotEmpty|AtLeast|AtMost|Length|LengthInRange)\(\s*$p\b\s*(?:,\s*(\d+)(?:\s*\.\.\s*(\d+))?)?""") to { m ->
                    val (a, b) = m.groupValues[2].toIntOrNull() to m.groupValues[3].toIntOrNull()
                    when (m.groupValues[1]) {
                        "OnlyOne" -> 1 to 1
                        "IsEmpty" -> 0 to 0
                        "NotEmpty" -> 1 to -1
                        "AtLeast" -> a!! to -1
                        "AtMost" -> 0 to a!!
                        "Length" -> a!! to a
                        else -> a!! to b!!
                    }
                },
                Regex("""\.count\(\s*"[^"]*",\s*$p,\s*(\d+)(?:\.\.(\d+))?\)""") to { m -> m.int(1) to (m.groupValues[2].toIntOrNull() ?: m.int(1)) },
                Regex("""\b$p\.size\s*!?in\s*(\d+)\.\.(\d+)""") to { m -> m.int(1) to m.int(2) },
                Regex("""\b$p\.size\s*>=\s*(\d+)""") to { m -> m.int(1) to -1 },
                Regex("""\bif \(\s*$p\.isEmpty\(\)[^)\n]*\)\s*throw""") to { _ -> 1 to -1 },
                Regex("""arityError\(\s*[^,()]+,\s*(\d+),\s*(-?\d+),""") to { m -> m.int(1) to m.int(2) },
                Regex("""composeArguments\(\s*$p,\s*(\d+)(?:,\s*(\d+))?""") to { m -> m.int(1) to (m.groupValues[2].toIntOrNull() ?: m.int(1)) },
            )
            return checks.mapNotNull { (regex, counts) -> regex.find(body)?.let { it.range.first to counts(it) } }.minByOrNull { it.first }?.second
        }

        /** The class file of [receiver] when it is (or is made of) a class of the current augment package. */
        private fun augmentClass(file: File, receiver: String): File? {
            val source = text(file)
            val type = if (receiver[0].isUpperCase()) receiver else Regex("""\bval $receiver\b[^\n]*?\b([A-Z]\w*)\(""").find(source)?.groupValues?.get(1) ?: return null
            val import = Regex("""^import (org\.autojs\.autojs\.runtime\.api\.augment\.[\w.]+)\.$type$""", RegexOption.MULTILINE).find(source) ?: return null
            return File(ROOT, "app/src/main/java/" + import.groupValues[1].replace('.', '/') + "/$type.kt").also { assertTrue("no file of $type", it.isFile) }
        }

        private fun named(file: File, name: String): Pair<String, String>? = declaration(file, name)?.let { function(file, it) }

        private fun declaration(file: File, name: String): MatchResult? =
            Regex("""\n[ \t]*(?:(?:private|internal|public|protected|override|inline|operator)\s+)*fun\s+(?:<[^>]*>\s*)?${Regex.escape(name)}\(""").find(text(file))

        /** The body of the function [start] begins and the name of its argument array (null without one). */
        private fun function(file: File, start: MatchResult): Pair<String, String>? {
            val body = body(file, start)
            val param = Regex("""vararg\s+(\w+)\s*:|(\w+)\s*:\s*Array<out Any\?>""").find(body) ?: return null
            return body to param.groupValues[1].ifEmpty { param.groupValues[2] }
        }

        /** The function [start] begins, up to the next member of its indentation. */
        private fun body(file: File, start: MatchResult): String {
            val source = text(file)
            val indent = source.substring(start.range.first + 1).takeWhile { it == ' ' || it == '\t' }
            val next = Regex("""\n$indent(@|/|(?:(?:private|internal|public|protected|override|inline|operator)\s+)*(?:fun|val|var|class|object)\s|})""").find(source, start.range.last)
            return source.substring(start.range.first, next?.range?.first ?: source.length)
        }

        private fun MatchResult.int(group: Int) = groupValues[group].toInt()

        /** A function declaration, its annotations excluded. */
        private val FUN = Regex("""\n[ \t]*(?:(?:private|internal|public|protected|override|inline|operator)\s+)*fun\s""")

    }

    private companion object {

        /** The host root: unit tests run in `app`. */
        val ROOT = File("..")

        const val PASSED = "passed"
        const val OTHER = "other: "
        const val F = "function () {}"

        fun counts(min: Int, max: Int) = if (max < 0) "[$min, -1]" else if (min == max) "[$min]" else "[$min, $max]"

        /** Functions of a module that are on the global object only, called by their global names. */
        val GLOBAL_ONLY = mapOf("selector.pickup" to "pickup", "selector.detect" to "detect")

        /** The script call of [key] with [args]. */
        fun call(key: String, args: List<String>): String {
            GLOBAL_ONLY[key]?.let { return "$it(${args.joinToString()})" }
            if ('.' !in key) return "$key(${args.joinToString()})"
            val path = key.substringBeforeLast('.')
            return when (val name = key.substringAfterLast('.')) {
                "()" -> "$path(${args.joinToString()})"
                "new" -> "new $path(${args.joinToString()})"
                else -> "$path.$name(${args.joinToString()})"
            }
        }

        /** `passed`, the name and message of a TypeError or RangeError, or [OTHER] and what else [expression] threw. */
        fun attempt(expression: String) = "(function () { try { $expression; } catch (e) { return e instanceof TypeError || e instanceof RangeError" +
            " ? e.name + ': ' + e.message : '$OTHER' + e; } return '$PASSED'; })()"

        fun RhinoException.reason() = (this as? EcmaError)?.let { "${it.name}: ${it.errorMessage}" } ?: details()

        /** The waits of `flow` (`flow(...)` is `flow.wait`): `(cond, timeout?, interval?, onOk?, onErr?)` or `(cond, options, onOk?, onErr?)`. */
        val WAITS = listOf("wait", "waitUntil", "waitWhile", "waitForStable", "waitForVisible", "waitForHidden", "waitThenClick", "waitThenClickBounds",
            "waitThenLongClick", "waitForStableThenClick", "waitForStableThenClickBounds", "()")

        /** The required positionals of each tool (`ToolkitArgs.POSITIONALS`). */
        val TOOLS = mapOf(
            "smartClick" to listOf("'a'"), "smartClickBounds" to listOf("'a'"), "clickIfExists" to listOf("'a'"), "clickBoundsIfExists" to listOf("'a'"),
            "clickAny" to listOf("['a', 'b']"), "clickBoundsAny" to listOf("['a', 'b']"), "findAny" to listOf("['a', 'b']"), "scrollUntil" to listOf("'a'"),
            "typeInto" to listOf("'a'", "'text'"), "dismissPopups" to listOf("['a']"), "collectList" to listOf("'a'", "'b'"), "launchAndWait" to listOf("'a'"),
            "backUntil" to listOf(F), "backToApp" to listOf("'a'"), "toggle" to listOf("'a'", "true"), "retry" to listOf(F),
        )

        fun wait(withDelay: Boolean, native: Boolean = false): Sample {
            val numbers = if (withDelay) listOf("1000", "100", "1", "2") else listOf("1000", "100")
            val fewest = if (withDelay) listOf("'a'", "{ delay: 1 }") else listOf("'a'")
            return Sample(
                accepted = listOf(fewest, listOf("'a'") + numbers + F + F),
                refused = listOf(fewest.dropLast(1), listOf("'a'") + numbers + F + F + F),
                check = { function, args -> if (native) FlowArgs.nativeWaitArgs(function, args) else FlowArgs.waitArgs(function, args, withDelay) },
            )
        }

        /** A tool of a flow root takes two callbacks after the options; the tool of `automator` nothing. */
        fun tool(name: String, callbacks: Boolean): Sample {
            val positionals = TOOLS.getValue(name)
            val most = positionals + "{}" + if (callbacks) listOf(F, F) else emptyList()
            return Sample(
                accepted = listOf(positionals, most),
                refused = listOf(positionals.dropLast(1), most + if (callbacks) F else "1"),
                check = { function, args -> ToolkitArgs.toolArgs(function, name, args, callbacks) },
            )
        }

        fun events(module: String) = mapOf(
            "$module.waitForIdle" to Sample(listOf(emptyList(), listOf("100")), listOf(listOf("100", "1"))) { f, a -> ToolkitArgs.idleArgs(f, a) },
            "$module.waitForEvent" to Sample(listOf(emptyList(), listOf("'viewClicked'", F, "100")), listOf(listOf("'viewClicked'", F, "100", "1"))) { f, a -> ToolkitArgs.eventArgs(f, a) },
            "$module.waitForToast" to Sample(listOf(emptyList(), listOf("'a'", "100")), listOf(listOf("'a'", "100", "1"))) { f, a -> ToolkitArgs.toastArgs(f, a) },
            "$module.waitForNotification" to Sample(listOf(emptyList(), listOf("'a'", "100")), listOf(listOf("'a'", "100", "1"))) { f, a -> ToolkitArgs.notificationArgs(f, a) },
        )

        /** Where the install functions of a hook are defined (the hooks copy the members of the current module's objects). */
        val INSTALL_DEFINITIONS = mapOf(
            // `mail.send(...)` forwards to the method of the default client.
            "mail" to "app/src/main/java/org/autojs/autojs/runtime/api/augment/mail/MailClientNativeObject.kt",
            "compose" to "app/src/main/java/org/autojs/autojs/runtime/api/augment/compose/Compose.kt",
        )

        /** The global forms of flow's install hook (`clickWait` is a `waitThenClick`) and `wait.async`, which call the flow waits. */
        val GLOBAL_WAITS = listOf(
            "waitAsync", "waitThenClick", "clickWait", "waitThenClickBounds", "clickBoundsWait", "waitForStable", "waitForStableThenClick", "clickWhenStable",
            "waitForStableThenClickBounds", "clickBoundsWhenStable", "waitForVisible", "waitForHidden", "waitForGone", "wait.async",
        )

        val SAMPLES: Map<String, Sample> = WAITS.associate { "flow.$it" to wait(withDelay = false) } +
            GLOBAL_WAITS.associateWith { wait(withDelay = false) } +
            listOf("clickWhenStableAfter", "clickBoundsWhenStableAfter").associateWith { wait(withDelay = true) } +
            listOf("waitForActivity", "waitForPackage").associate { "flow.$it" to wait(withDelay = false, native = true) } +
            listOf("clickWhenStableAfter", "clickBoundsWhenStableAfter").associate { "flow.$it" to wait(withDelay = true) } +
            ("auto.wait" to wait(withDelay = false)) +
            (TOOLS.keys - "retry").associate { "flow.$it" to tool(it, callbacks = true) } +
            TOOLS.keys.associate { "automator.$it" to tool(it, callbacks = false) } +
            events("flow") + events("automator") +
            listOf("click", "longClick").associate {
                "automator.$it" to Sample(listOf(listOf("'a'"), listOf("1", "2", "3", "4")), listOf(emptyList(), listOf("1", "2", "3", "4", "5"))) { f, a -> AutomatorArgs.clickArgs(f, a) }
            } +
            listOf("scrollDown", "scrollUp").associate {
                "automator.$it" to Sample(listOf(emptyList(), listOf("1", "2", "3", "4")), listOf(listOf("1", "2", "3", "4", "5"))) { f, a -> AutomatorArgs.scrollArgs(f, a) }
            } +
            ("automator.press" to Sample(listOf(listOf("[[1, 2], 100]"), listOf("1", "2", "3")), listOf(emptyList(), listOf("1", "2", "3", "4"))) { f, a -> AutomatorArgs.pressArgs(f, a) }) +
            ("automator.swipe" to Sample(listOf(listOf("100", "[[1, 2], [3, 4]]"), listOf("1", "2", "3", "4", "5")), listOf(listOf("100"), listOf("1", "2", "3", "4", "5", "6"))) { f, a -> AutomatorArgs.swipeArgs(f, a) }) +
            listOf("gesture" to false, "gestureAsync" to true).associate { (name, callback) ->
                "automator.$name" to Sample(listOf(listOf("100"), listOf("100", "[1, 2]", "[3, 4]", "[5, 6]")), listOf(emptyList())) { f, a -> AutomatorArgs.gestureArgs(f, a, callback) }
            } +
            listOf("gestures" to false, "gesturesAsync" to true).associate { (name, callback) ->
                "automator.$name" to Sample(listOf(listOf("[100, [1, 2]]"), listOf("[100, [1, 2]]", "[0, 100, [3, 4], [5, 6]]")), listOf(emptyList())) { f, a -> AutomatorArgs.gesturesArgs(f, a, callback) }
            } +
            // `Color(...)`, `new Color(...)` and `colors.build(...)`: (), (color), (red, green, blue), (red, green, blue, alpha).
            listOf("Color.()", "Color.new", "colors.build").associateWith { Sample(listOf(emptyList(), listOf("1", "2", "3", "4")), listOf(listOf("1", "2", "3", "4", "5"))) } +
            // One color for each of the six levels.
            listOf("setContentTextColor", "setContentTextColors").associate {
                "console.$it" to Sample(listOf(emptyList(), listOf("1", "2", "3", "4", "5", "6")), listOf(listOf("1", "2", "3", "4", "5", "6", "7")))
            } +
            // `pickup(root?, selector?, compass?, resultType?, callback?)` and `detect(w, compass?, resultType?, callback?)`: the normalizer of
            // the core counts the arguments and tells their positions by type (docs/selector/ROADMAP.md P1.1); accepted calls are only
            // normalized, never searched.
            ("selector.pickup" to Sample(listOf(emptyList(), listOf("null", "'a'", "'p'", "'w'", F)), listOf(listOf("1", "2", "3", "4", "5", "6"))) { _, a ->
                PickArguments.uiObject.pickup(unwrapped(a), PICK_BINDING).requestOrThrow()
            }) +
            ("selector.detect" to Sample(listOf(listOf("null", "'p'"), listOf("null", "'p'", "'w'", F)), listOf(listOf("null"), listOf("null", "'p'", "'w'", F, "1"))) { _, a ->
                PickArguments.uiObject.detect(unwrapped(a)).requestOrThrow()
            })

        /** Selectors without a bridge for the samples of `pickup`. */
        val PICK_BINDING = PickArguments.Binding({ UiSelector(null as AccessibilityBridge?) }, { BooleanFilter.BooleanSupplier { true } })

        fun unwrapped(args: Array<Any?>): Array<Any?> = Array(args.size) { RhinoUtils.unwrap(args[it]) }

    }

}
