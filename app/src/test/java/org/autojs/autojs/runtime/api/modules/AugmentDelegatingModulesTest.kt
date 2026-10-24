package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.api.ai.AiService
import org.autojs.autojs.runtime.api.augment.ai.Ai
import org.autojs.autojs.runtime.api.augment.compose.Compose
import org.autojs.autojs.runtime.api.augment.compose.ComposeScriptPlatform
import org.autojs.autojs.runtime.api.augment.compose.ComposeScriptValues
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.epub.Epub
import org.autojs.autojs.runtime.api.augment.gallery.Gallery
import org.autojs.autojs.runtime.api.augment.installer.Installer
import org.autojs.autojs.runtime.api.augment.mail.Mail
import org.autojs.autojs.runtime.api.augment.mediainfo.Mediainfo
import org.autojs.autojs.runtime.api.augment.memory.Memory
import org.autojs.autojs.runtime.api.augment.opencc.OpenCC
import org.autojs.autojs.runtime.api.augment.pinyin.Pinyin
import org.autojs.autojs.runtime.api.augment.pinyin4j.Pinyin4j
import org.autojs.autojs.runtime.api.augment.speech.Speech
import org.autojs.autojs.runtime.api.augment.speech.SpeechModels
import org.autojs.autojs.runtime.api.augment.terminal.Terminal
import org.autojs.autojs.runtime.api.epub.EpubService
import org.autojs.autojs.runtime.api.gallery.GalleryService
import org.autojs.autojs.runtime.api.installer.InstallerService
import org.autojs.autojs.runtime.api.mail.MailService
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.autojs.autojs.runtime.api.speech.SpeechService
import org.autojs.autojs.runtime.api.terminal.TerminalService
import org.autojs.autojs.runtime.api.tts.TtsService
import org.autojs.autojs.core.plugin.compose.ScriptUiDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
     * layer) are the argument errors the new layer raises itself (D10), which the current module also rejects.
     */
    private class Case(
        val name: String,
        val mountCurrent: (DifferentialRhino) -> Unit,
        val calls: List<String>,
        val prepare: (DifferentialRhino) -> Unit = {},
        val checked: List<Pair<String, String>> = emptyList(),
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

    @Test
    fun failingCallsGiveTheSameResultsAsTheCurrentModules() {
        // As HostModuleTestSupport.assertSameResults, for all modules at once; a call whose message needs Android resources
        // on either side (`Context.getString` is not mocked) is left to the device.
        // zh-CN: 同 HostModuleTestSupport.assertSameResults, 一次比较全部模块; 任一侧的消息需要 Android 资源 (Context.getString 未 mock) 的调用留给真机.
        var compared = 0
        val failures = CASES.flatMap { case ->
            val all = case.calls + case.checked.map { it.first }
            val new = withNew(case) { rhino -> all.map { rhino.render(DifferentialRhino.Side.KT, it) } }
            val current = DifferentialRhino().use { side ->
                case.prepare(side)
                case.mountCurrent(side)
                all.map { side.render(DifferentialRhino.Side.KT, it) }
            }
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
        }
        DEVICE.add(SHAPES)
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

        private fun typeErrors(vararg calls: String) = calls.map { it to T }

        private fun rangeErrors(vararg calls: String) = calls.map { it to R }

        private val CASES = listOf(
            Case("ai", { Ai(it.ktRuntime, ScriptRuntimeFields.ai(it.ktRuntime)).augment(global(it)) }, emptyList(), service(AiService::class.java, "ai"),
                typeErrors("ai()", "ai.ask()", "ai.ask(1)", "ai.ask({})", "ai.ask({ messages: 1 })", "ai.ask('a', 1)", "ai.ask('a', {}, 1)", "ai.chat()", "ai.stream(1)",
                    "ai.session(1)", "ai.session({ system: 1 })", "ai.session(1, 2)", "ai.catalog(1, 2)", "ai.catalog([])") +
                    rangeErrors("ai.ask([])", "ai.ask({ messages: [] })", "ai.session({ system: '' })")),
            Case("opencc", { OpenCC.augmentWithRuntime(global(it), it.ktRuntime) }, listOf("typeof opencc.s2t"), checked =
                typeErrors("opencc()", "opencc('a')", "opencc.convert('a')", "opencc.convert('a', 1)", "opencc.convert(null, 's2t')", "opencc.s2t()", "opencc.s2t('a', 'b')", "opencc.s2t(null)") +
                    rangeErrors("opencc.convert('a', 'nope')", "opencc('a', 'nope')")),
            Case("pinyin", { Pinyin.augmentWithRuntime(global(it), it.ktRuntime) },
                listOf("typeof pinyin.STYLE_TONE", "pinyin.compact([['a', 'b'], ['c']])", "pinyin.convert('')", "pinyin('', { mode: 'nope' })", "pinyin.convert(null, 1)"), checked =
                typeErrors("pinyin()", "pinyin.convert()", "pinyin.compare('a')", "pinyin.compact()", "pinyin.compact(1)", "pinyin.compact([1])", "pinyin.compact([[null]])", "pinyin.simple()",
                    "pinyin.fromCodePoint()", "pinyin.fromCodePoint('x')", "pinyin.fromPhrase()", "pinyin.fromPhrase(null)", "pinyin('a', { customDictionary: 1 })", "pinyin('a', { customDictionary: { a: 1 } })") +
                    rangeErrors("pinyin.fromCodePoint(1e20)", "pinyin('a', { mode: 'nope' })", "pinyin.convert('a', { style: 99 })")),
            Case("pinyin4j", { Pinyin4j.augmentWithRuntime(global(it), it.ktRuntime) }, listOf("pinyin4j.of('')", "pinyin4j('', { tone: 'nope' })", "pinyin4j.readings(null, { romanization: 'x' })"), checked =
                typeErrors("pinyin4j()", "pinyin4j.of()", "pinyin4j.readings()", "pinyin4j.as()", "pinyin4j.as('a', 'b')") +
                    rangeErrors("pinyin4j.of('a', { tone: 'nope' })", "pinyin4j('a', { case: 'x' })", "pinyin4j.of('a', { v: 'x' })", "pinyin4j.readings('a', { romanization: 'x' })",
                        "pinyin4j.readings('a', { romanization: 'yale', tone: 'num' })", "pinyin4j.readings('a', { tone: 'x' })")),
            Case("mediainfo", { Mediainfo(it.ktRuntime).augment(global(it)) }, listOf("typeof mediainfo.SNAPSHOT_SCHEMA_V1"), checked =
                typeErrors("mediainfo()", "mediainfo(null)", "mediainfo.read()", "mediainfo.get('a')", "mediainfo.get('/a', null, 'x')", "mediainfo.get('/a', 'General', undefined)",
                    "mediainfo.get('/a', 'General', 'x', 1)", "mediainfo.get('/a', 'General', 'x', { streamNumber: 'x' })", "mediainfo.get('/a', 'General', 'x', { infoKind: 1 })",
                    "mediainfo.countGet('a')", "mediainfo.countGet('/a', null)", "mediainfo.snapshot()", "mediainfo.snapshot('/a', 1)", "mediainfo.capabilities(1)") +
                    rangeErrors("mediainfo('')", "mediainfo.read(' ')", "mediainfo.get('/a', 'General', 'x', { streamNumber: -1 })", "mediainfo.get('/a', 'General', 'x', { streamNumber: 1.5 })",
                        "mediainfo.get('/a', 'General', 'x', { infoKind: 'nope' })")),
            Case("mail", { Mail(it.ktRuntime, ScriptRuntimeFields.mail(it.ktRuntime)).augment(global(it)) }, listOf("mail.connect()", "mail.connect(1)", "mail.setDefault()", "mail.close(1)", "mail.connectAsync()"), service(MailService::class.java, "mail")),
            Case("installer", { Installer(it.ktRuntime, ScriptRuntimeFields.installer(it.ktRuntime)).augment(global(it)) }, listOf("installer.install()", "installer.session()", "installer.inspect()", "installer.uninstall()"), service(InstallerService::class.java, "installer")),
            Case("epub", { Epub(it.ktRuntime, ScriptRuntimeFields.epub(it.ktRuntime)).augment(global(it)) }, listOf("epub.open()", "epub.metadata()", "epub.toc(1, 2)", "epub.read()"), service(EpubService::class.java, "epub")),
            Case("gallery", { Gallery(it.ktRuntime, ScriptRuntimeFields.gallery(it.ktRuntime)).augment(global(it)) }, listOf("gallery.pick(1, 2)", "gallery.view()", "gallery.edit()", "gallery.convert()"), service(GalleryService::class.java, "gallery")),
            Case("terminal", { Terminal(it.ktRuntime, ScriptRuntimeFields.terminal(it.ktRuntime)).augment(global(it)) }, listOf("terminal.open(1, 2, 3)", "terminal.session()", "terminal.exec()", "terminal.env(1, 2, 3)"), service(TerminalService::class.java, "terminal")),
            Case("speech", { Speech(it.ktRuntime, ScriptRuntimeFields.speech(it.ktRuntime), ScriptRuntimeFields.tts(it.ktRuntime)).augment(global(it)).also { speech -> SpeechModels(it.ktRuntime, ScriptRuntimeFields.speech(it.ktRuntime)).augment(speech) } },
                listOf("speech.isAvailable(1)", "speech.capabilities(1, 2)", "speech.models.list(1, 2)", "speech.models.remove()"),
                services(SpeechService::class.java to "speech", TtsService::class.java to "tts")),
            Case("compose", { side -> checkNotNull(ScriptRuntimeFields.compose(side.ktRuntime)).augment(global(side)) }, listOf("compose.isAvailable()", "compose.isAvailable(1)", "compose.createElement()", "compose.state()", "typeof compose.version"),
                { side -> ScriptRuntimeFields.setCompose(side.ktRuntime, Compose(side.ktRuntime, NoPluginPlatform())) }),
            Case("memory", { Memory(it.ktRuntime).augment(global(it)) }, emptyList(), checked =
                typeErrors("memory.snapshot(1)", "memory.retained(1)", "memory.collect(1, 2)", "memory.collect('1')", "memory.collect(NaN)", "memory.dumpHeap(1, 2)", "memory.dumpHeap(1)",
                    "memory.watch()", "memory.watch(1)", "memory.watch('a')", "memory.watch(null)", "memory.watch({}, 1)") +
                    rangeErrors("memory.collect(-1)", "memory.collect(1e9)", "memory.dumpHeap(' ')")),
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
