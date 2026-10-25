package org.autojs.autojs.runtime.api.modules.mediainfo

import kotlinx.coroutines.runBlocking
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.plugin.mediainfo.MediainfoPluginHost
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.js_json_parse
import org.autojs.plugin.mediainfo.api.MediainfoOptionKeys
import org.autojs.plugin.mediainfo.api.MediainfoSnapshotSchemas
import org.autojs.plugin.mediainfo.api.MediainfoQueryOptions
import org.autojs.autojs.rhino.extension.ScriptableExtensions.getProp
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.javascript.NativeObject
import java.io.File

@Suppress("unused", "UNUSED_PARAMETER")
class Mediainfo(private val scriptRuntime: ScriptRuntime) : ArgumentGuards() {

    /**
     * The data properties the `mediainfo` script module defines on its module object, in their order (its install hook
     * defines them, roadmap P5.2).
     * zh-CN: `mediainfo` 脚本模块在模块对象上定义的数据属性, 按顺序 (由其 install 钩子定义, P5.2).
     */
    val scriptProperties = listOf(
        "SNAPSHOT_SCHEMA_V1" to MediainfoSnapshotSchemas.V1,
        "SNAPSHOT_SCHEMA_V2" to MediainfoSnapshotSchemas.V2,
    )

    fun invoke(vararg args: Any?): NativeObject = ensureArgumentsOnlyOne(args) { path ->
        read(scriptRuntime, arrayOf(path))
    }

    companion object : ArgumentGuards() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun read(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsOnlyOne(args) { path ->
            MediainfoNativeObject(scriptRuntime, path)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun get(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 3..4) { values ->
            val file = File(MediainfoNativeObject.resolvePath(scriptRuntime, values[0]))
            val options = queryOptions(values.getOrNull(3))
            runBlocking {
                MediainfoPluginHost.get(ScriptRuntime.applicationContext, file, coerceString(values[1]),
                    options.streamNumber, coerceString(values[2]), options.infoKind)
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun countGet(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Int = ensureArgumentsLength(args, 2) { values ->
            val file = File(MediainfoNativeObject.resolvePath(scriptRuntime, values[0]))
            runBlocking { MediainfoPluginHost.countGet(ScriptRuntime.applicationContext, file, coerceString(values[1])) }
        }

        internal fun queryOptions(raw: Any?): MediainfoQueryOptions {
            if (raw.isJsNullish()) return MediainfoQueryOptions()
            require(raw is NativeObject) { "MediaInfo query options must be a JavaScript Object" }
            return MediainfoQueryOptions.parse(
                raw.getProp("streamNumber").takeUnless { it.isJsNullish() },
                raw.getProp("infoKind").takeUnless { it.isJsNullish() },
            )
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun snapshot(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val path = MediainfoNativeObject.resolvePath(scriptRuntime, argList[0])
            val options = parseSnapshotOptions(argList.getOrNull(1))
            val snapshot = runBlocking {
                MediainfoPluginHost.snapshot(
                    context = ScriptRuntime.applicationContext,
                    file = File(path),
                    includeInform = options.includeInform,
                    includeSections = options.includeSections,
                    schema = options.schema,
                )
            }
            js_json_parse(snapshot)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun capabilities(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsIsEmpty(args) {
            val capabilities = runBlocking {
                MediainfoPluginHost.capabilities(ScriptRuntime.applicationContext)
            }
            val result = JSONObject()
                .put("snapshotSchemas", JSONArray(capabilities.snapshotSchemas))
                .put("defaultSnapshotSchema", capabilities.defaultSnapshotSchema)
                .put("streamCount", capabilities.streamCount)
                .put("streamNumber", true)
                .put("infoKinds", JSONArray(capabilities.infoKinds))
            capabilities.engineVersion?.let { result.put("engineVersion", it) }
            js_json_parse(result.toString())
        }

        private fun parseSnapshotOptions(rawOptions: Any?): SnapshotOptions {
            if (rawOptions.isJsNullish()) return SnapshotOptions()
            if (rawOptions !is NativeObject) {
                throw WrappedIllegalArgumentException(
                    "Argument \"options\" ${rawOptions.jsBrief()} for mediainfo.snapshot must be a JavaScript Object",
                )
            }
            return SnapshotOptions(
                includeInform = rawOptions.inquire<Boolean>(MediainfoOptionKeys.INCLUDE_INFORM, ::coerceBoolean) ?: true,
                includeSections = rawOptions.inquire<Boolean>(MediainfoOptionKeys.INCLUDE_SECTIONS, ::coerceBoolean) ?: true,
                schema = rawOptions.inquire<String>(MediainfoOptionKeys.SCHEMA, ::coerceString),
            )
        }

        private data class SnapshotOptions(
            val includeInform: Boolean = true,
            val includeSections: Boolean = true,
            val schema: String? = null,
        )

    }

}
