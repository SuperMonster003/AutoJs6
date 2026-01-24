package org.autojs.autojs.runtime.api.augment.canvas

import android.graphics.Bitmap
import org.autojs.autojs.core.graphics.ScriptCanvas
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Constructable
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable

@Suppress("unused")
class Canvas(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Constructable {

    init {
        originateKeyName()
    }

    override fun construct(vararg args: Any?): Scriptable = ensureArgumentsAtMost(args, 2) { argList ->
        when (argList.size) {
            0 -> ScriptCanvas(scriptRuntime)
            1 -> when (val o = argList[0]) {
                is Bitmap -> ScriptCanvas(scriptRuntime, o)
                is ImageWrapper -> ScriptCanvas(scriptRuntime, o)
                else -> throw WrappedIllegalArgumentException("Invalid argument ${o.jsBrief()} for $key constructor")
            }
            2 -> {
                val (width, height) = argList
                require(width is Number) {
                    "Argument \"width\" ${width.jsBrief()} for $key constructor must be a number"
                }
                require(height is Number) {
                    "Argument \"height\" ${height.jsBrief()} for $key constructor must be a number"
                }
                ScriptCanvas(scriptRuntime, coerceIntNumber(width), coerceIntNumber(height))
            }
            else -> throw WrappedIllegalArgumentException("Invalid arguments length ${argList.size} for $key constructor")
        }.let { javaCanvas ->
            Context.javaToJS(javaCanvas, scriptRuntime.topLevelScope) as Scriptable
        }
    }

}
