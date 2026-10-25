package org.autojs.autojs.runtime.api.modules.speech

import com.google.gson.JsonPrimitive
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.plugin.speech.SpeechPluginHost
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.AugmentableKey
import org.autojs.autojs.runtime.api.speech.SpeechScriptArguments
import org.autojs.autojs.runtime.api.speech.SpeechScriptValues
import org.autojs.autojs.runtime.api.speech.SpeechService
import org.autojs.autojs.runtime.api.tts.TtsService
import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.autojs.plugin.speech.api.SpeechPluginIds
import java.io.File

/**
 * The global `speech` object, also `$speech` (3-Macaw Speech roadmap D4, P1.4, appendix B.1): offline speech
 * recognition, voice activity detection, wake words and neural speech synthesis provided by the 3-Macaw Speech plugin.
 * `isAvailable()`, `capabilities()`, `listen(options?)` -> `SpeechSession`, `recognize(source, options?)` /
 * `recognizeAsync`, `detectVoice(source, options?)`, `wake(keywords, options?)` -> `SpeechWakeSession`,
 * `synthesize(text, path, options?)` / `synthesizeAsync`, `speak(text, options?)` (the host `tts` with the plugin's
 * engine), `models` ([SpeechModels]) and the `SpeechError` constructor. Paths follow the script's working-directory
 * rules; `content://` and other URIs are refused. Argument shapes are checked in [SpeechScriptArguments] (pure Kotlin);
 * only the Rhino conversion happens here. The host keeps no recognition or synthesis of its own: without the plugin
 * every call fails with `PLUGIN_UNAVAILABLE` and the localized missing-plugin message (roadmap D27).
 * zh-CN: 全局 `speech` 对象, 亦为 `$speech` (3-Macaw Speech 路线图 D4, P1.4, 附录 B.1): 由 3-Macaw Speech 插件提供的离线语音识别, 语音活动检测,
 * 唤醒词与神经网络语音合成. 路径遵循脚本工作目录规则; 拒绝 `content://` 等 URI. 参数形态在 [SpeechScriptArguments] (纯 Kotlin) 检查; 这里只做
 * Rhino 转换. 宿主不保留任何识别或合成实现: 缺少插件时每个调用都以 `PLUGIN_UNAVAILABLE` 与本地化的缺插件文案失败 (路线图 D27).
 */
@Suppress("unused", "UNUSED_PARAMETER")
class Speech internal constructor(
    private val scriptRuntime: ScriptRuntime,
    private val service: SpeechService,
    private val tts: TtsService,
) : Augmentable(scriptRuntime) {

    override val keys = listOf(AugmentableKey("speech"))

    override val selfAssignmentFunctions = listOf(
        ::isAvailable.name,
        ::capabilities.name,
        ::listen.name,
        ::recognize.name,
        ::recognizeAsync.name,
        ::detectVoice.name,
        ::wake.name,
        ::synthesize.name,
        ::synthesizeAsync.name,
        ::speak.name,
    )

    /** `SpeechError` instances thrown by these functions reach the script unwrapped. zh-CN: 这些函数抛出的 `SpeechError` 实例不经包装到达脚本. */
    override val scriptErrorPassThroughFunctions: Set<String>
        get() = selfAssignmentFunctions.toSet()

    override val selfAssignmentProperties: List<Pair<Any, Any?>> by lazy {
        listOf<Pair<Any, Any?>>(SpeechJsErrors.NAME to SpeechJsErrors.constructor(scriptRuntime))
    }

    /** Whether a compatible, enabled speech plugin answers right now; never throws. zh-CN: 当前是否有兼容且启用的语音插件应答; 从不抛出. */
    @RhinoRuntimeFunctionInterface
    fun isAvailable(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsIsEmpty(args) {
            SpeechCalls.syncOf(scriptRuntime) { { service.isAvailable() } }
        }
    }

    /** `{ contractVersion, features, catalogVersion, engineName, ... }` of the selected plugin. zh-CN: 选中插件的 `{ contractVersion, features, catalogVersion, engineName, ... }`. */
    @RhinoRuntimeFunctionInterface
    fun capabilities(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsIsEmpty(args) {
            SpeechCalls.syncOf(scriptRuntime) {
                { SpeechScriptValues.capabilities(service.host { context -> SpeechPluginHost.capabilities(context) }) }
            }
        }
    }

    /** `speech.listen(options?)`: a dictation session that keeps the script alive until its `end` event. zh-CN: `speech.listen(options?)`: 让脚本活到 `end` 事件的听写会话. */
    @RhinoRuntimeFunctionInterface
    fun listen(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsAtMost(args, 1) { a ->
            SpeechCalls.syncOf(scriptRuntime) { listenWork(a.getOrNull(0)) }
        }
    }

    /** `speech.recognize(source, options?)`: a WAV path or PCM (`ArrayBuffer`, `Int16Array`, `byte[]`). zh-CN: `speech.recognize(source, options?)`: WAV 路径或 PCM. */
    @RhinoRuntimeFunctionInterface
    fun recognize(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            SpeechCalls.blockingOf(scriptRuntime, "speech.recognize") { recognizeWork(a[0], a.getOrNull(1)) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun recognizeAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            SpeechCalls.asyncOf(scriptRuntime) { recognizeWork(a[0], a.getOrNull(1)) }
        }
    }

    /** `speech.detectVoice(source, options?)`: `{ segments: [{ startMillis, endMillis }], durationMillis }`. zh-CN: `speech.detectVoice(source, options?)`: 语音段. */
    @RhinoRuntimeFunctionInterface
    fun detectVoice(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            SpeechCalls.blockingOf(scriptRuntime, "speech.detectVoice") { detectVoiceWork(a[0], a.getOrNull(1)) }
        }
    }

    /** `speech.wake(keywords, options?)`: a wake session that keeps the script alive until its `end` event. zh-CN: `speech.wake(keywords, options?)`: 让脚本活到 `end` 事件的唤醒会话. */
    @RhinoRuntimeFunctionInterface
    fun wake(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            SpeechCalls.syncOf(scriptRuntime) { wakeWork(a[0], a.getOrNull(1)) }
        }
    }

    /** `speech.synthesize(text, path, options?)`: a WAV file written by the plugin and published by the host. zh-CN: `speech.synthesize(text, path, options?)`: 由插件写出, 宿主发布的 WAV 文件. */
    @RhinoRuntimeFunctionInterface
    fun synthesize(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLengthInRange(args, 2..3) { a ->
            SpeechCalls.blockingOf(scriptRuntime, "speech.synthesize") { synthesizeWork(a[0], a[1], a.getOrNull(2)) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun synthesizeAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsLengthInRange(args, 2..3) { a ->
            SpeechCalls.asyncOf(scriptRuntime) { synthesizeWork(a[0], a[1], a.getOrNull(2)) }
        }
    }

    /**
     * `speech.speak(text, options?)`: the host `tts(text, options)` with the engine fixed to the plugin (roadmap D20);
     * `model` / `speaker` choose the voice `<model>/<speaker>` (roadmap D19; a speaker name is resolved by the engine,
     * a model without `speaker` gets its speaker for the language of the text, no model the engine's voice for that
     * language) and `speed` the rate. Returns what `tts()` returns, a Promise; without the plugin the Promise is
     * rejected with `PLUGIN_UNAVAILABLE` like every other speech call, instead of the `tts` initialization error.
     * zh-CN: `speech.speak(text, options?)`: engine 固定为本插件的宿主 `tts(text, options)` (路线图 D20); `model` / `speaker` 选择语音
     * `<model>/<speaker>` (路线图 D19; 说话人名字由引擎解析, 只给 `model` 时取该模型适合文本语言的说话人, 不给 `model` 时取引擎在该语言下的语音),
     * `speed` 选择语速. 返回值与 `tts()` 相同, 为 Promise; 缺少插件时该 Promise 与其他语音调用一样以 `PLUGIN_UNAVAILABLE` 拒绝, 而不是 `tts` 的
     * 初始化错误.
     */
    @RhinoRuntimeFunctionInterface
    fun speak(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            val text = SpeechCalls.text(a[0], "text")
            val speak = SpeechScriptArguments.speak(SpeechCalls.json(a.getOrNull(1)), text)
            val options = speak.ttsOptions.deepCopy().apply {
                addProperty(SpeechScriptArguments.ENGINE, SpeechPluginIds.OFFICIAL_PACKAGE_NAME)
                speak.speed?.let { addProperty(SpeechScriptArguments.RATE, it) }
            }
            if (!SpeechPluginHost.isTtsEngineInstalled(service.appContext, SpeechPluginIds.OFFICIAL_PACKAGE_NAME)) {
                return@ensureArgumentsLengthInRange SpeechCalls.asyncOf(scriptRuntime) { throw SpeechPluginHost.missingPlugin(service.appContext) }
            }
            tts.speech(text, options, selection = speak.selection).result
        }
    }

    /** Host failures and argument guards leave as `SpeechError`; script values pass unchanged. zh-CN: 宿主失败与参数守卫以 `SpeechError` 离开; 脚本值原样通过. */
    private fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        SpeechJsErrors.throwInto(scriptRuntime, t)
    }

    // ------------------------------------------------------------------ internals

    /** A WAV path or PCM bytes. zh-CN: WAV 路径或 PCM 字节. */
    private sealed interface Source {
        class Path(val file: File) : Source
        class Pcm(val bytes: ByteArray) : Source
    }

    private fun sourceOf(value: Any?): Source {
        if (value is CharSequence) {
            return Source.Path(service.resolveFile(SpeechScriptArguments.path(JsonPrimitive(value.toString()), "source")))
        }
        val bytes = SpeechCalls.pcmBytes(value)
            ?: throw SpeechJsErrors.failure(SpeechErrorCodes.INVALID_OPTIONS, "'source' must be a WAV file path, an ArrayBuffer, an Int16Array or a byte[]")
        return Source.Pcm(bytes)
    }

    /**
     * `speech.listen`: the dispatcher and the emitter are created on the script thread, the plugin session opens in the
     * coroutine, and a failed open releases the emitter silently.
     * zh-CN: `speech.listen`: 分发器与发射器在脚本线程上创建, 插件会话在协程里打开, 打开失败时静默释放发射器.
     */
    private fun listenWork(options: Any?): SpeechWork {
        val request = SpeechScriptArguments.listen(SpeechCalls.json(options))
        service.ensureOpen()
        val controller = SpeechSession.Controller(
            dispatcher = SpeechAsyncDispatcher(scriptRuntime),
            bridges = scriptRuntime.bridges,
            source = request.source,
            scriptRuntime = scriptRuntime,
        )
        val runner = SpeechSessionRunner(
            controller,
            convertError = { error -> SpeechJsErrors.newSpeechError(scriptRuntime, error) },
            onFinished = { finished -> service.untrack(finished) },
        )
        try {
            service.track(runner)
        } catch (t: Throwable) {
            runner.cancelSilently()
            throw t
        }
        return {
            try {
                val proxy = service.host { context -> SpeechPluginHost.openSession(context, request, runner) }
                runner.attach(proxy)
            } catch (t: Throwable) {
                service.untrack(runner)
                runner.cancelSilently()
                throw t
            }
            controller.session
        }
    }

    /** `speech.wake`: like [listenWork]; the rounds of `thenListen` share the wake session's dispatcher. zh-CN: `speech.wake`: 同 [listenWork]; `thenListen` 的各轮次共用唤醒会话的分发器. */
    private fun wakeWork(keywords: Any?, options: Any?): SpeechWork {
        val request = SpeechScriptArguments.wake(SpeechCalls.json(keywords), SpeechCalls.json(options))
        service.ensureOpen()
        val controller = SpeechWakeSession.Controller(
            dispatcher = SpeechAsyncDispatcher(scriptRuntime),
            bridges = scriptRuntime.bridges,
            keywords = request.keywords.map { it.keyword },
            scriptRuntime = scriptRuntime,
            convertValue = { value -> org.autojs.autojs.util.RhinoUtils.toJsValue(scriptRuntime.topLevelScope, value) },
        )
        val convertError: (org.autojs.autojs.core.plugin.speech.SpeechError) -> Any? = { error -> SpeechJsErrors.newSpeechError(scriptRuntime, error) }
        val runner = SpeechWakeRunner(
            controller,
            thenListen = request.thenListen != null,
            newRound = { index ->
                SpeechSession.Controller(
                    dispatcher = controller.dispatcher,
                    bridges = scriptRuntime.bridges,
                    source = SpeechContract.SOURCE_MIC,
                    round = index,
                    ownsDispatcher = false,
                    scriptRuntime = scriptRuntime,
                )
            },
            convertError = convertError,
            onFinished = { finished -> service.untrack(finished) },
        )
        try {
            service.track(runner)
        } catch (t: Throwable) {
            runner.cancelSilently()
            throw t
        }
        return {
            try {
                val proxy = service.host { context -> SpeechPluginHost.openWakeSession(context, request, runner.wakeSink, runner.listenSink) }
                runner.attach(proxy)
            } catch (t: Throwable) {
                service.untrack(runner)
                runner.cancelSilently()
                throw t
            }
            controller.session
        }
    }

    private fun recognizeWork(source: Any?, options: Any?): SpeechWork {
        val parsed = SpeechScriptArguments.recognize(SpeechCalls.json(options))
        val input = sourceOf(source)
        service.ensureOpen()
        return {
            service.host { context ->
                when (input) {
                    is Source.Path -> SpeechPluginHost.recognizeFile(context, input.file, parsed.request)
                    is Source.Pcm -> SpeechPluginHost.recognizePcm(context, input.bytes, parsed.sampleRate, parsed.request)
                }
            }
        }
    }

    private fun detectVoiceWork(source: Any?, options: Any?): SpeechWork {
        val parsed = SpeechScriptArguments.detectVoice(SpeechCalls.json(options))
        val input = sourceOf(source)
        service.ensureOpen()
        return {
            service.host { context ->
                when (input) {
                    is Source.Path -> SpeechPluginHost.detectVoiceFile(context, input.file, parsed.request)
                    is Source.Pcm -> SpeechPluginHost.detectVoicePcm(context, input.bytes, parsed.sampleRate, parsed.request)
                }
            }
        }
    }

    private fun synthesizeWork(text: Any?, path: Any?, options: Any?): SpeechWork {
        val request = SpeechScriptArguments.synthesize(SpeechCalls.json(text?.let { if (it is CharSequence) it.toString() else it }), SpeechCalls.json(options))
        val target = service.resolveFile(SpeechScriptArguments.path(SpeechCalls.json(path), "path"))
        service.ensureOpen()
        return { service.host { context -> SpeechPluginHost.synthesizeToFile(context, request, target) } }
    }
}
