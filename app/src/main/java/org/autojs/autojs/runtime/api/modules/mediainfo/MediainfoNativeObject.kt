package org.autojs.autojs.runtime.api.modules.mediainfo

import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoStandardFunctionInterface
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.mediainfo.MediainfoPluginHost
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.ArgumentGuards.Companion.ensureArgumentsIsEmpty
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.getProp
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.StringReadable
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import java.io.File

@Suppress("unused", "UNUSED_PARAMETER")
class MediainfoNativeObject(
    private val scriptRuntime: ScriptRuntime,
    rawPath: Any?,
) : NativeObject(), StringReadable {

    @JvmField
    val path: String = resolvePath(scriptRuntime, rawPath)

    private var informToPrint: String = ""

    init {
        RhinoUtils.initNativeObjectPrototype(this)
        defineProperty(StringReadable.KEY, newBaseFunction(scriptRuntime.topLevelScope, StringReadable.KEY, { toStringReadable() }, NOT_CONSTRUCTABLE), READONLY or DONTENUM or PERMANENT)
        defineProperty("path", path, READONLY or PERMANENT)
        initProperties()
    }

    private fun initProperties() {
        val file = File(path)
        val inform = runBlocking {
            MediainfoPluginHost.inform(ScriptRuntime.applicationContext, file)
        }
        defineProperty("inform", inform, READONLY or PERMANENT)
        STREAM_KINDS.forEach { streamKind ->
            val funcName = streamKind.lowercase()
            defineProperty(funcName, newBaseFunction(scriptRuntime.topLevelScope, funcName, { argList ->
                val parameter = argList.takeUnless { it.isEmpty() }?.get(0)?.let { coerceString(it) } ?: ""
                val options = Mediainfo.queryOptions(argList.getOrNull(1))
                runBlocking {
                    MediainfoPluginHost.get(ScriptRuntime.applicationContext, file, streamKind, options.streamNumber, parameter, options.infoKind)
                }
            }, NOT_CONSTRUCTABLE), READONLY or PERMANENT)
        }
        applyInform(inform)
    }

    private fun applyInform(inform: String) {
        var currentObj: Scriptable? = null
        var currentSectionName: String?

        inform.lineSequence().forEach { raw ->
            val line = raw.trimEnd()
            if (line.isBlank()) return@forEach
            when {
                !line.contains(':') -> {
                    currentSectionName = line.lowercase()
                    currentObj = this.getProp(currentSectionName) as? Scriptable
                        ?: newNativeObject(scriptRuntime.topLevelScope).also { defineProperty(currentSectionName, it, READONLY or PERMANENT) }
                    if (informToPrint.isNotBlank()) informToPrint += "  },\n"
                    informToPrint += "  $currentSectionName: {\n"
                }
                currentObj != null -> {
                    val idx = line.indexOf(':')
                    val key = line.substring(0, idx).trim().toCamel()
                    val value = line.substring(idx + 1).trim()
                    currentObj.defineProp(key, value, READONLY or PERMANENT)
                    informToPrint += "    $key: \"$value\",\n"
                }
            }
        }
        if (informToPrint.isNotBlank()) informToPrint += "  },"
    }

    private fun String.toCamel(): String = this
        .replace(Regex("\\((s|es|ies)\\)"), "$1")
        .split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotEmpty() }
        .joinToString("") { part ->
            part.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }.replaceFirstChar { it.lowercase() }

    override fun toStringReadable() = toStringRhino(this)

    companion object : ArgumentGuards() {

        internal fun resolvePath(scriptRuntime: ScriptRuntime, rawPath: Any?): String = when {
            rawPath.isJsNullish() -> throw WrappedIllegalArgumentException("Argument path ${rawPath.jsBrief()} is invalid for mediainfo")
            else -> {
                val pathString = coerceString(rawPath)
                require(pathString.isNotBlank()) {
                    "Argument \"path\" ${pathString.jsBrief()} for mediainfo cannot be empty"
                }
                scriptRuntime.files.nonNullPath(pathString)
            }
        }

        private val STREAM_KINDS = listOf(
            "GENERAL",
            "VIDEO",
            "AUDIO",
            "TEXT",
            "OTHER",
            "IMAGE",
            "MENU",
            "MAX",
        )

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun toString(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): String = ensureArgumentsIsEmpty(args) {
            toStringRhino(thisObj as MediainfoNativeObject)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun toStringRhino(thisObj: MediainfoNativeObject): String {
            val objectName = MediainfoNativeObject::class.java.simpleName
            val inform = thisObj.informToPrint
            return when {
                inform.isBlank() -> "$objectName {}"
                else -> "$objectName {\n$inform\n}"
            }
        }

    }

}
