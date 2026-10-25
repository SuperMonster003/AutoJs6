package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.api.ai.AiService
import org.autojs.autojs.runtime.api.modules.ai.Ai
import org.autojs.autojs.runtime.api.modules.compose.Compose
import org.autojs.autojs.runtime.api.modules.compose.ComposeScriptPlatform
import org.autojs.autojs.runtime.api.modules.compose.ComposeScriptValues
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.epub.Epub
import org.autojs.autojs.runtime.api.modules.gallery.Gallery
import org.autojs.autojs.runtime.api.modules.installer.Installer
import org.autojs.autojs.runtime.api.modules.mail.Mail
import org.autojs.autojs.runtime.api.modules.mediainfo.Mediainfo
import org.autojs.autojs.runtime.api.modules.memory.Memory
import org.autojs.autojs.runtime.api.modules.opencc.OpenCC
import org.autojs.autojs.runtime.api.modules.pinyin.Pinyin
import org.autojs.autojs.runtime.api.modules.pinyin.PinyinOptions
import org.autojs.autojs.runtime.api.modules.pinyin4j.Pinyin4j
import org.autojs.autojs.runtime.api.modules.pinyin4j.Pinyin4jOptions
import org.autojs.autojs.runtime.api.modules.speech.Speech
import org.autojs.autojs.runtime.api.modules.speech.SpeechModels
import org.autojs.autojs.runtime.api.modules.speech.SpeechSpeaker
import org.autojs.autojs.runtime.api.modules.terminal.Terminal
import org.autojs.autojs.runtime.api.epub.EpubService
import org.autojs.autojs.runtime.api.gallery.GalleryService
import org.autojs.autojs.runtime.api.installer.InstallerService
import org.autojs.autojs.runtime.api.mail.MailService
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.autojs.autojs.runtime.api.speech.SpeechService
import org.autojs.autojs.runtime.api.terminal.TerminalService
import org.autojs.autojs.runtime.api.tts.TtsService
import org.autojs.autojs.core.plugin.compose.ScriptUiDispatcher
import org.autojs.plugin.pinyin4j.api.Pinyin4jRomanizationSystems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import java.util.concurrent.Executor

/**
 * Roadmap P4.1: the modules that only change their mounting layer (ai, opencc, pinyin, pinyin4j, mediainfo, mail,
 * installer, epub, gallery, terminal, speech with speech.models, compose, memory). Each one hands its calls to the
 * current augment module, so the JVM compares, per module, the surface on the new layer with the current module mounted
 * in a second Rhino (the runtime services need Android and are allocated without their constructors, compose gets a
 * platform without a plugin) and the results of calls that fail before the plugin or the service is reached; the
 * device repeats those calls with both switch states (`device_eval.py --strict-errors`, the coded errors of the plugin
 * modules have the same name, code and message there).
 *
 * zh-CN: P4.1: 只换挂载层的模块 (ai, opencc, pinyin, pinyin4j, mediainfo, mail, installer, epub, gallery, terminal, speech 及 speech.models, compose,
 * memory). 每个模块把调用交给现有 augment 模块, 因此 JVM 按模块比较新层的表面与在第二个 Rhino 中挂载的现有模块 (运行时服务需要 Android, 以不经构造函数的
 * 方式分配; compose 使用没有插件的平台), 以及在到达插件或服务之前就失败的调用的结果; 真机在两种开关状态下重复这些调用 (device_eval.py --strict-errors,
 * 插件模块的带错误码的错误在真机上名称, 错误码与消息都相同).
 */
class AugmentDelegatingModulesTest {

    /**
     * A module: [calls] give the same results as the current module; [checked] (call to the error name on the new
     * layer) are the argument errors the new layer raises itself (D10), which the current module also rejects;
     * [accepted] are calls the current module accepts although their values look unusual (JSON of toJSON / wrapper
     * objects, concatenated strings, Java objects), which the new layer's checks let through as well.
     */
    private class Case(
        val name: String,
        val mountCurrent: (DifferentialRhino) -> Unit,
        val calls: List<String>,
        val prepare: (DifferentialRhino) -> Unit = {},
        val checked: List<Pair<String, String>> = emptyList(),
        val accepted: List<String> = emptyList(),
    )

    @Test
    fun surfacesMatchTheCurrentModules() {
        val failures = CASES.mapNotNull { case ->
            withNew(case) { rhino ->
                runCatching { HostModuleTestSupport.assertSameSurface(rhino, case.name, case.prepare, case.mountCurrent) }.exceptionOrNull()?.message
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun speechModelsSurfaceMatchesTheCurrentModule() {
        val case = CASES.first { it.name == "speech" }
        withNew(case) { rhino -> HostModuleTestSupport.assertSameSurface(rhino, "speech.models", case.prepare, case.mountCurrent) }
    }

    /** 3-Macaw Speech roadmap P8.3.2: `speech.speaker`, mounted like `speech.models`. */
    @Test
    fun speechSpeakerSurfaceMatchesTheCurrentModule() {
        val case = CASES.first { it.name == "speech" }
        withNew(case) { rhino -> HostModuleTestSupport.assertSameSurface(rhino, "speech.speaker", case.prepare, case.mountCurrent) }
    }

    @Test
    fun failingCallsGiveTheSameResultsAsTheCurrentModules() {
        // As HostModuleTestSupport.assertSameResults, for all modules at once; a call whose message needs Android resources
        // on either side (`Context.getString` is not mocked) is left to the device.
        // zh-CN: 同 HostModuleTestSupport.assertSameResults, 一次比较全部模块; 任一侧的消息需要 Android 资源 (Context.getString 未 mock) 的调用留给真机.
        var compared = 0
        val failures = CASES.flatMap { case ->
            val all = case.calls + case.checked.map { it.first }
            val new = withNew(case) { rhino -> all.map { rhino.render(DifferentialRhino.Side.KT, it) } }
            val current = HostModuleTestSupport.currentResults(all, case.prepare, case.mountCurrent)
            val expected = case.checked.toMap()
            all.indices.mapNotNull { i ->
                val (now, next) = current[i] to new[i]
                val call = all[i]
                expected[call]?.let { name ->
                    // The new layer's own check: its error, and the current module rejects the call too.
                    // zh-CN: 新层自己的检查: 抛出该错误, 且现有模块同样拒绝该调用.
                    compared++
                    return@mapNotNull when {
                        next.key != "throws $name" -> "$call (checked)\n    expected throws $name\n    new      $next"
                        !now.key.startsWith("throws") && !now.key.startsWith("unavailable(jvm)") -> "$call (checked)\n    current accepts it: $now"
                        else -> null
                    }
                }
                if (listOf(now, next).any { NOT_MOCKED in it.detail.orEmpty() } || now.key.startsWith("unavailable(jvm)")) return@mapNotNull null
                compared++
                val same = now.key == next.key || now.key.startsWith("throws ") && next.key == "throws JavaException " + now.key.removePrefix("throws ")
                if (same) null else "$call\n    current $now\n    new     $next"
            }
        }
        assertTrue("Results differ from the current modules ($compared compared):\n" + failures.joinToString("\n"), failures.isEmpty())
        assertTrue("$compared calls compared", compared >= 40)
    }

    @Test
    fun acceptedCallsGetPastTheArgumentChecksAsOnTheCurrentModules() {
        // What the current module accepts stays accepted (D10): on the JVM both sides then fail at the same step behind
        // the argument checks (the plugin host or the service cannot run there), or give the same value.
        // zh-CN: 现有模块接受的照旧接受 (D10): 在 JVM 上两侧随后在参数检查之后的同一步失败 (插件宿主或服务在此无法运行), 或得到相同的值.
        var compared = 0
        val failures = CASES.filter { it.accepted.isNotEmpty() }.flatMap { case ->
            val new = withNew(case) { rhino -> case.accepted.map { rhino.render(DifferentialRhino.Side.KT, it) } }
            val current = HostModuleTestSupport.currentResults(case.accepted, case.prepare, case.mountCurrent)
            case.accepted.indices.mapNotNull { i ->
                compared++
                val (now, next) = reached(current[i]) to reached(new[i])
                when {
                    now in ARGUMENT_ERRORS -> "${case.accepted[i]}\n    current rejects it: ${current[i]}"
                    now != next -> "${case.accepted[i]}\n    current ${current[i]}\n    new     ${new[i]}"
                    else -> null
                }
            }
        }
        assertTrue("Accepted calls differ ($compared compared):\n" + failures.joinToString("\n"), failures.isEmpty())
        assertTrue("$compared calls compared", compared >= 25)
    }

    /**
     * The device cases: the error name and code of every call, expected as on the new layer in the JVM where that does
     * not depend on Android (otherwise printed only); with the switch off the argument errors of [Case.checked] differ in
     * their names only (appendix E P4.1).
     */
    @Test
    fun deviceCases() {
        CASES.forEach { case ->
            val calls = case.calls + case.checked.map { it.first }
            val expected = withNew(case) { rhino -> calls.map { rhino.render(DifferentialRhino.Side.KT, caught(it)) } }
            DEVICE.add(calls.mapIndexed { i, call ->
                val rendered = expected[i]
                caught(call) to if (NOT_MOCKED in rendered.detail.orEmpty() || rendered.key.startsWith("throws") || JVM_ONLY.any { it in rendered.key }) DeviceCases.INFO else rendered.key
            })
        }
    }

    /**
     * Calls that reach the official plugins, for a device with them installed (`build/script-modules-device-plugins`, not
     * part of the default device run): printed only, the two switch states must print the same.
     *
     * zh-CN: 到达官方插件的调用, 供装有这些插件的设备使用 (build/script-modules-device-plugins, 不在默认的真机运行中): 只打印, 两种开关状态须打印相同的结果.
     */
    @Test
    fun pluginSmokeCases() {
        DeviceCases("AugmentDelegatingModulesPluginSmoke", "P4.1 smoke with the official plugins installed", "build/script-modules-device-plugins")
            .add(PLUGIN_SMOKE.map { it to DeviceCases.INFO })
    }

    @Test
    fun shapes() {
        val rendered = CASES.associate { case ->
            case.name to withNew(case) { rhino ->
                rhino.render(DifferentialRhino.Side.KT, "[typeof ${case.name}, ${case.name} === \$${case.name}]").key
            }
        }
        val callable = setOf("ai", "opencc", "pinyin", "pinyin4j", "mediainfo", "compose")
        rendered.forEach { (name, key) ->
            assertEquals(name, "[\"${if (name in callable) "function" else "object"}\",true]", key)
        }
        withNew(CASES.first { it.name == "compose" }) { rhino ->
            assertEquals("true", rhino.render(DifferentialRhino.Side.KT, "ComposeError === compose.ComposeError").key)
        }
        withNew(CASES.first { it.name == "speech" }) { rhino ->
            assertEquals("[\"object\",\"undefined\"]", rhino.render(DifferentialRhino.Side.KT, "[typeof speech.models, typeof speech.\$models]").key)
            assertEquals("[\"object\",\"undefined\"]", rhino.render(DifferentialRhino.Side.KT, "[typeof speech.speaker, typeof speech.\$speaker]").key)
        }
        DEVICE.add(SHAPES)
    }

    @Test
    fun optionNamesFollowTheCurrentParsers() {
        // The pinyin / pinyin4j option checks repeat the names the current parsers accept, so that a rejected name costs
        // no Java exception (R2). The parsers are the oracle: the new layer gives a RangeError with the parser's message
        // exactly when the parser rejects the value, and lets every other value through.
        // zh-CN: pinyin / pinyin4j 的选项检查复制了现有解析器接受的名称, 使拒绝的名称不产生 Java 异常 (R2). 以解析器为准: 解析器拒绝某值时, 新层给出带同样
        // 消息的 RangeError, 其余的值一律放行.
        var compared = 0
        val failures = mutableListOf<String>()
        fun compare(rhino: DifferentialRhino, call: String, rejection: String?) {
            compared++
            val result = Context.toString(rhino.evalKt("(function () { try { $call; return 'accepted'; } catch (e) { return e.name + ': ' + e.message; } })()"))
            val ok = if (rejection == null) !result.startsWith("RangeError") else result.startsWith("RangeError") && rejection in result
            if (!ok) failures += "$call\n    current ${rejection ?: "accepted"}\n    new     $result"
        }
        fun js(value: String) = "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"
        fun rejection(parse: () -> Any?) = runCatching { parse() }.exceptionOrNull()?.let { it as IllegalArgumentException; it.message!! }

        withNew(CASES.first { it.name == "pinyin4j" }) { rhino ->
            val words = PINYIN4J_NAMES + listOf("", "  ", "x", "nope", "HANYU-PINYIN", "wade giles", "with tone", "u :", "ü", "Ü ")
            val candidates = (words + words.map { " ${it.lowercase()} " }).distinct()
            candidates.forEach { c ->
                compare(rhino, "pinyin4j.of('a', { tone: ${js(c)} })", rejection { Pinyin4jOptions.normalizeToneType(c, Pinyin4jOptions.DEFAULT_OF_TONE_TYPE) })
                compare(rhino, "pinyin4j.of('a', { case: ${js(c)} })", rejection { Pinyin4jOptions.normalizeCaseType(c) })
                compare(rhino, "pinyin4j.of('a', { v: ${js(c)} })", rejection { Pinyin4jOptions.normalizeVCharType(c, Pinyin4jOptions.DEFAULT_OF_TONE_TYPE) })
                compare(rhino, "pinyin4j.readings('a', { romanization: ${js(c)} })", rejection { Pinyin4jOptions.normalizeRomanization(c) })
                // A romanization other than HANYU with an explicit case (the message of the current module's compatibility check).
                // zh-CN: HANYU 以外的拼音方案加上明确的 case (消息同现有模块的兼容性检查).
                compare(rhino, "pinyin4j.readings('a', { system: ${js(c)}, case: 'up' })", rejection {
                    require(Pinyin4jOptions.normalizeRomanization(c) == Pinyin4jRomanizationSystems.HANYU) { "Pinyin4j case options are only supported with HANYU romanization" }
                })
            }
        }
        withNew(CASES.first { it.name == "pinyin" }) { rhino ->
            val names = PinyinOptions.PinyinMode.entries.map { it.name } + PinyinOptions.PinyinStyle.entries.map { it.name } + listOf("", "x", "tone 2", "place-name")
            val strings = (names + names.map { it.lowercase() } + names.map { " $it" }).distinct()
            val numbers = listOf("-1" to -1.0, "-0" to -0.0, "0" to 0.0, "1" to 1.0, "1.5" to 1.5, "2" to 2.0, "3" to 3.0, "4" to 4.0, "5" to 5.0, "6" to 6.0, "99" to 99.0,
                "NaN" to Double.NaN, "Infinity" to Double.POSITIVE_INFINITY)
            listOf("mode" to PinyinOptions::parseMode, "style" to PinyinOptions::parseStyle).forEach { (key, parse) ->
                strings.forEach { c -> compare(rhino, "pinyin('a', { $key: ${js(c)} })", rejection { parse(c) }) }
                numbers.forEach { (source, n) -> compare(rhino, "pinyin.convert('a', { $key: $source })", rejection { parse(n) }) }
                compare(rhino, "pinyin('a', { $key: true })", rejection { parse(true) })
            }
        }
        assertTrue("Option checks differ from the current parsers ($compared compared):\n" + failures.joinToString("\n"), failures.isEmpty())
        assertTrue("$compared calls compared", compared >= 400)
    }

    private fun <T> withNew(case: Case, block: (DifferentialRhino) -> T): T = DifferentialRhino().use { rhino ->
        case.prepare(rhino)
        rhino.mountNewModules(case.name)
        block(rhino)
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
        override fun createRenderer(sessionId: Int, beforeMount: (Any) -> Unit, onDestroyed: () -> Unit): ComposeScriptPlatform.Opened =
            error("No plugin in the JVM")
        override fun resolveImage(value: Any): ComposeScriptValues.ResolvedImage = error("No images in the JVM")
        override fun post(delayMillis: Long, action: () -> Unit) = Unit
        override fun warn(message: String) = Unit
        override fun report(error: Throwable) = Unit
        override fun close() = Unit
    }

    companion object {

        private const val NOT_MOCKED = "not mocked"

        /**
         * Results that depend on the JVM: Java exceptions of the services allocated without constructors, and the INTERNAL
         * code of a plugin module whose argument message needs Android resources (printed only on the device).
         */
        private val JVM_ONLY = listOf("JavaException", "\"INTERNAL\"")

        private val DEVICE = DeviceCases("AugmentDelegatingModulesTest", "new script module layer, only the mounting layer changes (P4.1)")

        /** The error name and code of what [call] throws (the messages follow the language of the device), or its result type. */
        private fun caught(call: String) =
            "(function () { try { var v = $call; return 'no error: ' + typeof v; } catch (e) { return [String(e.name), e.code === undefined ? null : String(e.code)]; } })()"

        private fun service(type: Class<*>, field: String): (DifferentialRhino) -> Unit = { side ->
            HostModuleTestSupport.setField(side.ktRuntime, field, DifferentialRhino.allocateWithoutConstructor(type))
        }

        private fun services(vararg pairs: Pair<Class<*>, String>): (DifferentialRhino) -> Unit = { side -> pairs.forEach { (type, field) -> service(type, field)(side) } }

        private fun global(side: DifferentialRhino) = side.ktScope.globalThis as ScriptableObject

        private const val T = "TypeError"
        private const val R = "RangeError"

        /** The error names of argument checks: an accepted call must not end in one of them. */
        private val ARGUMENT_ERRORS = setOf("WrappedIllegalArgumentException", "IllegalArgumentException", "InternalError", T, R)

        private val REACHED = Regex("^(?:throws(?:[(]java[)])?|unavailable[(]jvm[)]) (?:JavaException )?([^ ]+)")

        /**
         * Where a call ended: the simple name of the Java exception or the error it failed with, or its value. A plugin
         * host class fails to initialize on the JVM: ExceptionInInitializerError the first time, NoClassDefFoundError after.
         */
        private fun reached(rendered: DifferentialRhino.Rendered) = (REACHED.find(rendered.key)?.groupValues?.get(1) ?: rendered.key)
            .replace("ExceptionInInitializerError", "NoClassDefFoundError")

        /** The names the normalizers of `Pinyin4jOptions` accept (all four, so that each option also meets the names of the others). */
        private val PINYIN4J_NAMES = listOf(
            "HANYU", "HANYU_PINYIN", "PINYIN", "TONGYONG", "TONGYONG_PINYIN", "WADE", "WADE_GILES", "WADEGILES", "WADE_GILES_PINYIN", "MPS2",
            "MANDARIN_PHONETIC_SYMBOLS_2", "YALE", "YALE_PINYIN", "GWOYEU", "GWOYEU_ROMATZYH", "GR",
            "LOWERCASE", "LOW", "L", "0", "UPPERCASE", "UP", "U", "1",
            "WITH_TONE_NUMBER", "WITH_NUMBER", "NUMBER", "NUM", "WITHOUT_TONE", "NO_TONE", "NO", "FALSE", "WITH_TONE_MARK", "WITH_MARK", "MARK", "TRUE",
            "WITH_U_AND_COLON", "U_AND_COLON", "U_COLON", "U:", "WITH_V", "V", "WITH_U_UNICODE", "U_UNICODE", "UNICODE", "Ü",
        )

        private fun typeErrors(vararg calls: String) = calls.map { it to T }

        private fun rangeErrors(vararg calls: String) = calls.map { it to R }

        private val CASES = listOf(
            Case("ai", { Ai(it.ktRuntime, ScriptRuntimeFields.ai(it.ktRuntime)).augment(global(it)) }, emptyList(), service(AiService::class.java, "ai"),
                typeErrors("ai()", "ai.ask()", "ai.ask(1)", "ai.ask({})", "ai.ask({ messages: 1 })", "ai.ask('a', 1)", "ai.ask('a', {}, 1)", "ai.chat()", "ai.stream(1)",
                    "ai.session(1)", "ai.session({ system: 1 })", "ai.session(1, 2)", "ai.catalog(1, 2)", "ai.catalog([])", "ai.ask(null)", "ai.ask(function () {})",
                    "ai.ask(Object.create({ role: 'user', content: 'x' }))", "ai.ask({ messages: null })", "ai.ask('x', { toJSON: function () { return [] } })", "ai.ask('x', new Date(0))",
                    "ai.ask('x', new java.util.HashMap())", "ai.catalog(new String('x'))", "ai.session({ system: null })",
                    "ai.ask(NaN)", "ai.ask(true)", "ai.ask(-Infinity)", "ai.session([])", "ai.ask('x', [])", "ai.catalog(false)",
                    "(function () { Number.prototype.toJSON = function () { return 'x' }; try { return ai.ask(1) } finally { delete Number.prototype.toJSON } })()") +
                    rangeErrors("ai.ask([])", "ai.ask({ messages: [] })", "ai.session({ system: '' })", "ai.session({ system: new java.lang.String('') })"),
                accepted = listOf("ai.ask(new String('x'))", "ai.ask(new Date(0))", "ai.ask({ toJSON: function () { return 'x' } })", "ai.ask({ messages: function () {}, role: 'user', content: 'x' })",
                    "ai.ask([1])", "ai.ask(new java.lang.String('x'))", "ai.chat({ role: 'user', content: 'x' })", "ai.session({ system: function () {} })", "ai.session({ system: new String('s') })",
                    "ai.session(Object.create({ system: 1 }))", "ai.catalog({})",
                    "(function () { Array.prototype.toJSON = function () { return 'x' }; try { return ai.ask([]) } finally { delete Array.prototype.toJSON } })()",
                    "(function () { var a = []; a.toJSON = function () { return {} }; return ai.catalog(a) })()")),
            Case("opencc", { OpenCC.augmentWithRuntime(global(it), it.ktRuntime) }, listOf("typeof opencc.s2t"), checked =
                typeErrors("opencc()", "opencc('a')", "opencc.convert('a')", "opencc.convert('a', 1)", "opencc.convert(null, 's2t')", "opencc.s2t()", "opencc.s2t('a', 'b')", "opencc.s2t(null)") +
                    typeErrors("opencc.convert('a', new String('s2t'))") + rangeErrors("opencc.convert('a', 'nope')", "opencc('a', 'nope')"),
                accepted = listOf("(function () { var t = 's2'; t += 't'; return opencc.convert('a', t) })()", "opencc.convert('a', new java.lang.String('s2t'))", "opencc.convert(1, 's2t')",
                    "opencc.convert({}, 's2t')", "(function () { var t = 'a'; t += 'b'; return opencc.s2t(t) })()")),
            Case("pinyin", { Pinyin.augmentWithRuntime(global(it), it.ktRuntime) },
                listOf("typeof pinyin.STYLE_TONE", "pinyin.compact([['a', 'b'], ['c']])", "pinyin.convert('')", "pinyin('', { mode: 'nope' })", "pinyin.convert(null, 1)"), checked =
                typeErrors("pinyin()", "pinyin.convert()", "pinyin.compare('a')", "pinyin.compact()", "pinyin.compact(1)", "pinyin.compact([1])", "pinyin.compact([[null]])", "pinyin.simple()",
                    "pinyin.fromCodePoint()", "pinyin.fromCodePoint('x')", "pinyin.fromPhrase()", "pinyin.fromPhrase(null)", "pinyin('a', { customDictionary: 1 })", "pinyin('a', { customDictionary: { a: 1 } })") +
                    rangeErrors("pinyin.fromCodePoint(1e20)", "pinyin('a', { mode: 'nope' })", "pinyin.convert('a', { style: 99 })", "pinyin('a', { mode: new java.lang.String('nope') })",
                        "(function () { var m = 'no'; m += 'pe'; return pinyin('a', { style: m }) })()"),
                accepted = listOf("pinyin.fromCodePoint(new java.lang.Integer(65))", "pinyin('a', { mode: undefined })", "pinyin.fromCodePoint('65')")),
            Case("pinyin4j", { Pinyin4j.augmentWithRuntime(global(it), it.ktRuntime) }, listOf("pinyin4j.of('')", "pinyin4j('', { tone: 'nope' })", "pinyin4j.readings(null, { romanization: 'x' })"), checked =
                typeErrors("pinyin4j()", "pinyin4j.of()", "pinyin4j.readings()", "pinyin4j.as()", "pinyin4j.as('a', 'b')") +
                    rangeErrors("pinyin4j.of('a', { tone: 'nope' })", "pinyin4j('a', { case: 'x' })", "pinyin4j.of('a', { v: 'x' })", "pinyin4j.readings('a', { romanization: 'x' })",
                        "pinyin4j.readings('a', { romanization: 'yale', tone: 'num' })", "pinyin4j.readings('a', { tone: 'x' })")),
            Case("mediainfo", { Mediainfo(it.ktRuntime).augment(global(it)) }, listOf("typeof mediainfo.SNAPSHOT_SCHEMA_V1"), checked =
                typeErrors("mediainfo()", "mediainfo(null)", "mediainfo.read()", "mediainfo.get('a')", "mediainfo.get('/a', null, 'x')", "mediainfo.get('/a', 'General', undefined)",
                    "mediainfo.get('/a', 'General', 'x', 1)", "mediainfo.get('/a', 'General', 'x', { streamNumber: 'x' })", "mediainfo.get('/a', 'General', 'x', { infoKind: 1 })",
                    "mediainfo.countGet('a')", "mediainfo.countGet('/a', null)", "mediainfo.snapshot()", "mediainfo.snapshot('/a', 1)", "mediainfo.capabilities(1)") +
                    rangeErrors("mediainfo('')", "mediainfo.read(' ')", "mediainfo.get('/a', 'General', 'x', { streamNumber: -1 })", "mediainfo.get('/a', 'General', 'x', { streamNumber: 1.5 })",
                        "mediainfo.get('/a', 'General', 'x', { infoKind: 'nope' })", "mediainfo.get('/a', 'General', 'x', { streamNumber: new java.lang.Integer(-1) })"),
                accepted = listOf("mediainfo.get('/a', 'General', 'x', { streamNumber: new java.lang.Integer(1) })", "mediainfo.get('/a', 'General', 'x', { infoKind: new java.lang.String('TEXT') })",
                    "mediainfo.get('/a', 'General', 'x', { streamNumber: undefined, infoKind: null })")),
            Case("mail", { Mail(it.ktRuntime, ScriptRuntimeFields.mail(it.ktRuntime)).augment(global(it)) }, listOf("mail.connect()", "mail.connect(1)", "mail.setDefault()", "mail.close(1)", "mail.connectAsync()"), service(MailService::class.java, "mail")),
            Case("installer", { Installer(it.ktRuntime, ScriptRuntimeFields.installer(it.ktRuntime)).augment(global(it)) }, listOf("installer.install()", "installer.session()", "installer.inspect()", "installer.uninstall()"), service(InstallerService::class.java, "installer")),
            Case("epub", { Epub(it.ktRuntime, ScriptRuntimeFields.epub(it.ktRuntime)).augment(global(it)) }, listOf("epub.open()", "epub.metadata()", "epub.toc(1, 2)", "epub.read()"), service(EpubService::class.java, "epub")),
            Case("gallery", { Gallery(it.ktRuntime, ScriptRuntimeFields.gallery(it.ktRuntime)).augment(global(it)) }, listOf("gallery.pick(1, 2)", "gallery.view()", "gallery.edit()", "gallery.convert()"), service(GalleryService::class.java, "gallery")),
            Case("terminal", { Terminal(it.ktRuntime, ScriptRuntimeFields.terminal(it.ktRuntime)).augment(global(it)) }, listOf("terminal.open(1, 2, 3)", "terminal.session()", "terminal.exec()", "terminal.env(1, 2, 3)"), service(TerminalService::class.java, "terminal")),
            Case("speech", {
                Speech(it.ktRuntime, ScriptRuntimeFields.speech(it.ktRuntime), ScriptRuntimeFields.tts(it.ktRuntime)).augment(global(it)).also { speech ->
                    SpeechModels(it.ktRuntime, ScriptRuntimeFields.speech(it.ktRuntime)).augment(speech)
                    SpeechSpeaker(it.ktRuntime, ScriptRuntimeFields.speech(it.ktRuntime)).augment(speech)
                }
            },
                listOf(
                    "speech.isAvailable(1)", "speech.capabilities(1, 2)", "speech.models.list(1, 2)", "speech.models.remove()",
                    "speech.speaker.list(1)", "speech.speaker.remove()", "speech.speaker.enroll('a')", "speech.speaker.identify()",
                ),
                services(SpeechService::class.java to "speech", TtsService::class.java to "tts")),
            Case("compose", { side -> checkNotNull(ScriptRuntimeFields.compose(side.ktRuntime)).augment(global(side)) }, listOf("compose.isAvailable()", "compose.isAvailable(1)", "compose.createElement()", "compose.state()", "typeof compose.version"),
                { side -> ScriptRuntimeFields.setCompose(side.ktRuntime, Compose(side.ktRuntime, NoPluginPlatform())) }),
            Case("memory", { Memory(it.ktRuntime).augment(global(it)) }, emptyList(), checked =
                typeErrors("memory.snapshot(1)", "memory.retained(1)", "memory.collect(1, 2)", "memory.collect('1')", "memory.collect(NaN)", "memory.dumpHeap(1, 2)", "memory.dumpHeap(1)",
                    "memory.watch()", "memory.watch(1)", "memory.watch('a')", "memory.watch(null)", "memory.watch({}, 1)",
                    "memory.watch(new java.lang.String('a'))") +
                    rangeErrors("memory.collect(-1)", "memory.collect(1e9)", "memory.dumpHeap(' ')", "memory.dumpHeap(new java.lang.String(' '))", "memory.collect(new java.lang.Integer(-1))"),
                accepted = listOf("memory.collect(new java.lang.Integer(5))", "memory.watch(new java.lang.StringBuilder('a'))", "memory.watch({}, new java.lang.String('n'))",
                    "memory.dumpHeap(new java.lang.String('a'))")),
        )

        private fun settled(promise: String) = HostModuleTestSupport.settled(promise, "typeof v")

        /** One call per module that reaches its plugin (or the host's diagnostics), with a result that does not change between runs. */
        private val PLUGIN_SMOKE = listOf(
            "typeof ai.agent",
            settled("ai.catalog()"),
            "opencc.s2t('\\u7b80\\u4f53')",
            "opencc('\\u7b80\\u4f53', 's2tw')",
            "JSON.stringify(pinyin('\\u4e2d\\u6587'))",
            "pinyin.simple('\\u4e2d\\u6587')",
            "pinyin4j.of('\\u4e2d\\u6587', ' ')",
            "JSON.stringify(pinyin4j.readings('\\u4e2d'))",
            "typeof mediainfo.capabilities().streamCount",
            "[mail.default === null, typeof mail.providers, typeof mail.accounts, typeof mail.list, typeof mail.listAsync]",
            "typeof installer.isAvailable()",
            "typeof installer.status",
            "typeof epub.isAvailable()",
            "typeof gallery.isAvailable()",
            "typeof terminal.isAvailable()",
            "typeof speech.isAvailable()",
            "typeof speech.capabilities()",
            "Array.isArray(speech.models.list())",
            "[typeof compose.isAvailable(), compose.version === null || typeof compose.version, compose.sessions.length]",
            "Object.keys(memory.snapshot()).join()",
        )

        private val SHAPES = listOf(
            "[typeof ai, typeof opencc, typeof pinyin, typeof pinyin4j, typeof mediainfo, typeof compose]" to "[\"function\",\"function\",\"function\",\"function\",\"function\",\"function\"]",
            "[typeof mail, typeof installer, typeof epub, typeof gallery, typeof terminal, typeof speech, typeof memory]" to "[\"object\",\"object\",\"object\",\"object\",\"object\",\"object\",\"object\"]",
            "[ai === \$ai, mail === \$mail, compose === \$compose, memory === \$memory, typeof speech.models, typeof speech.\$models]" to "[true,true,true,true,\"object\",\"undefined\"]",
            "[ComposeError === compose.ComposeError, typeof MailError, typeof mail.MailError, typeof installer.InstallerError, typeof epub.EpubError, typeof gallery.GalleryError, typeof terminal.TerminalError, typeof speech.SpeechError]" to
                "[true,\"undefined\",\"function\",\"function\",\"function\",\"function\",\"function\",\"function\"]",
        )

    }

}
