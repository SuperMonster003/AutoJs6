package org.autojs.autojs.runtime.api.modules.speech

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.plugin.speech.SpeechPluginHost
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.AugmentableKey
import org.autojs.autojs.runtime.api.speech.SpeechScriptArguments
import org.autojs.autojs.runtime.api.speech.SpeechService

/**
 * `speech.speaker` (contract version 2, 3-Macaw Speech roadmap D125, D132 - D135): voiceprints the plugin keeps in its
 * private storage. `enroll(name, files, options?)` / `enrollAsync` (one path or an array of up to 10, averaged; the
 * samples join the speaker's earlier ones unless `replace: true`) answer `{ name, samples, durationMillis, modelId }`,
 * `verify(name, file, options?)` / `verifyAsync` answer `{ name, score, matched, threshold }`, `identify(file, options?)`
 * / `identifyAsync` answer the same with `candidates: [{ name, score }]` (`name` null below the threshold), `list()`
 * answers `[{ name, samples, updatedAt, modelId }]` and `remove(name)` whether a voiceprint was deleted. No answer carries
 * an embedding. A plugin without contract version 2 (3-Macaw Speech 1.2.0 and earlier) refuses every call with
 * `PLUGIN_INCOMPATIBLE`.
 * zh-CN: `speech.speaker` (契约版本 2, 3-Macaw Speech 路线图 D125, D132 - D135): 插件保存在其私有存储中的声纹. `enroll(name, files, options?)`
 * / `enrollAsync` (一个路径或至多 10 个路径的数组, 取平均; 除非 `replace: true`, 样本并入该说话人原有的样本) 应答
 * `{ name, samples, durationMillis, modelId }`, `verify(name, file, options?)` / `verifyAsync` 应答 `{ name, score, matched, threshold }`,
 * `identify(file, options?)` / `identifyAsync` 应答同样字段加 `candidates: [{ name, score }]` (未过阈值时 `name` 为 null), `list()` 应答
 * `[{ name, samples, updatedAt, modelId }]`, `remove(name)` 应答是否删除了声纹. 应答从不带嵌入向量. 不支持契约版本 2 的插件 (3-Macaw Speech
 * 1.2.0 及更早) 以 `PLUGIN_INCOMPATIBLE` 拒绝每个调用.
 */
@Suppress("unused", "UNUSED_PARAMETER")
class SpeechSpeaker(
    private val scriptRuntime: ScriptRuntime,
    private val service: SpeechService,
) : Augmentable(scriptRuntime) {

    override val keys = listOf(AugmentableKey("speaker", withDollarPrefix = false))

    override val selfAssignmentFunctions = listOf(
        ::enroll.name,
        ::enrollAsync.name,
        ::verify.name,
        ::verifyAsync.name,
        ::identify.name,
        ::identifyAsync.name,
        ::list.name,
        ::remove.name,
    )

    override val scriptErrorPassThroughFunctions: Set<String>
        get() = selfAssignmentFunctions.toSet()

    @RhinoRuntimeFunctionInterface
    fun enroll(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLengthInRange(args, 2..3) { a ->
            SpeechCalls.blockingOf(scriptRuntime, "speech.speaker.enroll") { enrollWork(a[0], a[1], a.getOrNull(2)) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun enrollAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsLengthInRange(args, 2..3) { a ->
            SpeechCalls.asyncOf(scriptRuntime) { enrollWork(a[0], a[1], a.getOrNull(2)) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun verify(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLengthInRange(args, 2..3) { a ->
            SpeechCalls.blockingOf(scriptRuntime, "speech.speaker.verify") { verifyWork(a[0], a[1], a.getOrNull(2)) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun verifyAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsLengthInRange(args, 2..3) { a ->
            SpeechCalls.asyncOf(scriptRuntime) { verifyWork(a[0], a[1], a.getOrNull(2)) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun identify(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            SpeechCalls.blockingOf(scriptRuntime, "speech.speaker.identify") { identifyWork(a[0], a.getOrNull(1)) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun identifyAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            SpeechCalls.asyncOf(scriptRuntime) { identifyWork(a[0], a.getOrNull(1)) }
        }
    }

    /** `[{ name, samples, updatedAt, modelId }]`, sorted by name. zh-CN: 按名字排序的已注册说话人. */
    @RhinoRuntimeFunctionInterface
    fun list(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsIsEmpty(args) {
            SpeechCalls.syncOf(scriptRuntime) { { service.host { context -> SpeechPluginHost.listSpeakers(context) } } }
        }
    }

    /** Whether the voiceprint of the name was deleted. zh-CN: 是否删除了该名字的声纹. */
    @RhinoRuntimeFunctionInterface
    fun remove(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsOnlyOne(args) { raw ->
            val name = SpeechScriptArguments.speakerName(SpeechCalls.json(raw), "name")
            SpeechCalls.syncOf(scriptRuntime) { { service.host { context -> SpeechPluginHost.removeSpeaker(context, name) } } }
        }
    }

    private fun enrollWork(name: Any?, files: Any?, options: Any?): SpeechWork {
        val parsed = SpeechScriptArguments.enroll(SpeechCalls.json(name), SpeechCalls.json(files), SpeechCalls.json(options))
        val inputs = parsed.paths.map(service::resolveFile)
        service.ensureOpen()
        return { service.host { context -> SpeechPluginHost.enrollSpeaker(context, inputs, parsed.request) } }
    }

    private fun verifyWork(name: Any?, file: Any?, options: Any?): SpeechWork {
        val request = SpeechScriptArguments.verify(SpeechCalls.json(name), SpeechCalls.json(options))
        val input = service.resolveFile(SpeechScriptArguments.path(SpeechCalls.json(file), "file"))
        service.ensureOpen()
        return { service.host { context -> SpeechPluginHost.verifySpeaker(context, input, request) } }
    }

    private fun identifyWork(file: Any?, options: Any?): SpeechWork {
        val request = SpeechScriptArguments.identify(SpeechCalls.json(options))
        val input = service.resolveFile(SpeechScriptArguments.path(SpeechCalls.json(file), "file"))
        service.ensureOpen()
        return { service.host { context -> SpeechPluginHost.identifySpeaker(context, input, request) } }
    }

    private fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        SpeechJsErrors.throwInto(scriptRuntime, t)
    }
}
