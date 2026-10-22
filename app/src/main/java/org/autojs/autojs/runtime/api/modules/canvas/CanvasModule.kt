package org.autojs.autojs.runtime.api.modules.canvas

import android.graphics.Bitmap
import org.autojs.autojs.core.graphics.ScriptCanvas
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptConstruct
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.mozilla.javascript.Wrapper

/**
 * The `Canvas` constructor on the new script module layer (roadmap P3.1), with the four documented overloads of the
 * current `augment/canvas/Canvas`: no argument (a canvas without target), a mutable Bitmap, an ImageWrapper (drawn on a
 * copy) or a width and a height (a transparent ARGB_8888 bitmap). The result is the current `ScriptCanvas` (appendix C
 * Q24); calling `Canvas(...)` without `new` constructs as well, as before.
 *
 * Arguments follow the real signatures (T4, D10): one argument that is neither a Bitmap nor an ImageWrapper, or a size
 * that is not a number, is a TypeError (the current module threw IllegalArgumentException); as documented, a size is
 * truncated toward zero and must be a positive 32-bit integer then, otherwise a RangeError (the current module failed in
 * `Bitmap.createBitmap` with IllegalArgumentException); more than two arguments are a TypeError (appendix C Q7).
 *
 * zh-CN: 新脚本模块层上的 Canvas 构造器 (P3.1), 提供现有 augment/canvas/Canvas 的四个文档重载: 无参数 (无绘制目标的画布), 可变 Bitmap, ImageWrapper (绘制到
 * 副本) 或宽高 (透明 ARGB_8888 位图). 结果为现有 ScriptCanvas (附录 C Q24); 不带 new 调用 Canvas(...) 同样构造, 与原来相同. 参数按真实签名 (T4, D10): 单个参数
 * 既不是 Bitmap 也不是 ImageWrapper, 或尺寸不是数值, 为 TypeError (现有模块为 IllegalArgumentException); 按文档, 尺寸向 0 截断后须为 32 位正整数, 否则为
 * RangeError (现有模块在 Bitmap.createBitmap 中以 IllegalArgumentException 失败); 多于两个参数为 TypeError (附录 C Q7).
 */
@ScriptModule(name = "Canvas", dollarPrefix = false)
class CanvasModule(private val runtime: ScriptRuntime) {

    @ScriptConstruct
    fun construct(first: Any? = ABSENT, second: Any? = ABSENT): ScriptCanvas = when {
        first === ABSENT -> ScriptCanvas(runtime)
        second === ABSENT -> when (val target = if (first is Wrapper) first.unwrap() else first) {
            is Bitmap -> ScriptCanvas(runtime, target)
            is ImageWrapper -> ScriptCanvas(runtime, target)
            else -> throw UtilValues.typeError(CONSTRUCTOR, ScriptMessages.argumentType("bitmap", "Bitmap | ImageWrapper", ScriptArgs.describe(first)))
        }
        else -> ScriptCanvas(runtime, size("width", first), size("height", second))
    }

    private fun size(param: String, value: Any?): Int {
        if (value !is Number) throw GlobalValues.typeError(CONSTRUCTOR, param, "number", value)
        val size = GlobalValues.int(CONSTRUCTOR, param, value)
        if (size <= 0) throw UtilValues.rangeError(CONSTRUCTOR, ScriptMessages.argumentPositive(param, UtilValues.quote(value)))
        return size
    }

    private companion object {

        const val CONSTRUCTOR = "Canvas"

        val ABSENT = Any()

    }

}
