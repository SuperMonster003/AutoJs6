package org.autojs.autojs.runtime.api.modules.speech

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.plugin.speech.SpeechBundles
import org.autojs.autojs.core.plugin.speech.SpeechModelQuery
import org.autojs.autojs.core.plugin.speech.SpeechPluginHost
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.speech.SpeechScriptArguments
import org.autojs.autojs.runtime.api.speech.SpeechService

/**
 * `speech.models` (3-Macaw Speech roadmap D5, D23, D24, appendix B.1): the model catalog of the plugin and the models
 * installed in its private storage. `list(filter?)`, `install(id, options?)` / `installAsync` (download with
 * `onProgress(done, total)` and `acceptLicense` for non-commercial models, roadmap D11), `importFile(path, options?)`
 * (a local archive the script may read), `remove(id)`, `setDefault(kind, id)`, `getDefault(kind)` and
 * `refreshCatalog()`. The models live in the plugin; the host only relays the calls and reads the script's archive.
 * zh-CN: `speech.models` (3-Macaw Speech 路线图 D5, D23, D24, 附录 B.1): 插件的模型目录及安装在其私有存储中的模型. `list(filter?)`,
 * `install(id, options?)` / `installAsync` (下载, 带 `onProgress(done, total)` 与非商用模型所需的 `acceptLicense`, 路线图 D11),
 * `importFile(path, options?)` (脚本可读的本地归档), `remove(id)`, `setDefault(kind, id)`, `getDefault(kind)` 与 `refreshCatalog()`. 模型保存在
 * 插件中; 宿主只转发调用并读取脚本的归档.
 */
@Suppress("unused", "UNUSED_PARAMETER")
class SpeechModels(
    private val scriptRuntime: ScriptRuntime,
    private val service: SpeechService,
) : ArgumentGuards() {

    @RhinoRuntimeFunctionInterface
    fun list(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsAtMost(args, 1) { a ->
            val query = SpeechScriptArguments.modelQuery(SpeechCalls.json(a.getOrNull(0)))
            SpeechCalls.syncOf(scriptRuntime) { { service.host { context -> SpeechPluginHost.listModels(context, query) } } }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun install(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            SpeechCalls.blockingWithProgress(scriptRuntime, "speech.models.install", SpeechCalls.progressOf(a.getOrNull(1))) { installWork(a[0], a.getOrNull(1)) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun installAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            SpeechCalls.asyncWithProgress(scriptRuntime, SpeechCalls.progressOf(a.getOrNull(1))) { installWork(a[0], a.getOrNull(1)) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun importFile(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            SpeechCalls.blockingWithProgress(scriptRuntime, "speech.models.importFile", SpeechCalls.progressOf(a.getOrNull(1))) {
                val archive = service.resolveFile(SpeechScriptArguments.path(SpeechCalls.json(a[0]), "path"))
                val request = SpeechScriptArguments.importModel(SpeechCalls.json(a.getOrNull(1)))
                service.ensureOpen()
                return@blockingWithProgress { emit -> service.host { context -> SpeechPluginHost.importModel(context, archive, request, emit) } }
            }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun remove(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsOnlyOne(args) { raw ->
            val id = SpeechScriptArguments.modelId(SpeechCalls.json(raw), "id")
            SpeechCalls.syncOf(scriptRuntime) { { service.host { context -> SpeechPluginHost.removeModel(context, id) } } }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun setDefault(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLength(args, 2) { a ->
            val kind = SpeechScriptArguments.kind(SpeechCalls.json(a[0]), "kind")
            val id = SpeechScriptArguments.modelId(SpeechCalls.json(a[1]), "id")
            SpeechCalls.syncOf(scriptRuntime) { { service.host { context -> SpeechPluginHost.setDefaultModel(context, kind, id) } } }
        }
    }

    /** The default model of [kind], or null when none is installed. zh-CN: [kind] 的默认模型, 未安装时为 null. */
    @RhinoRuntimeFunctionInterface
    fun getDefault(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsOnlyOne(args) { raw ->
            val kind = SpeechScriptArguments.kind(SpeechCalls.json(raw), "kind")
            SpeechCalls.syncOf(scriptRuntime) {
                {
                    service.host { context ->
                        SpeechPluginHost.listModels(context, SpeechModelQuery(kind = kind, installed = true)).firstOrNull(SpeechBundles::isDefault)
                    }
                }
            }
        }
    }

    /** `{ version, updated }` after refreshing the signed catalog (roadmap D24). zh-CN: 刷新签名目录后的 `{ version, updated }` (路线图 D24). */
    @RhinoRuntimeFunctionInterface
    fun refreshCatalog(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsIsEmpty(args) {
            SpeechCalls.blockingOf(scriptRuntime, "speech.models.refreshCatalog") { { service.host { context -> SpeechPluginHost.refreshCatalog(context) } } }
        }
    }

    private fun installWork(id: Any?, options: Any?): SpeechProgressWork {
        val request = SpeechScriptArguments.install(SpeechCalls.json(id), SpeechCalls.json(options))
        service.ensureOpen()
        return { emit -> service.host { context -> SpeechPluginHost.installModel(context, request, emit) } }
    }

    private fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        SpeechJsErrors.throwInto(scriptRuntime, t)
    }
}
