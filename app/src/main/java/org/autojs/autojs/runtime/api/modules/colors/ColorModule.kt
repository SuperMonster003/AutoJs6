package org.autojs.autojs.runtime.api.modules.colors

import org.autojs.autojs.core.image.ColorTable
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.colors.ColorNativeObject
import org.autojs.autojs.runtime.api.modules.ScriptConstruct
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptPrototype

/**
 * `Color` on the new script module layer (roadmap P2.3): callable and constructible alike (`Color(...)`, `new Color(...)`,
 * `colors.build(...)`), with the four color tables as the prototype chain of the function object, as in the current
 * module. Arguments: none (black), a color (OmniColor or a Color instance), `(red, green, blue)` or
 * `(red, green, blue, alpha)`, converted by the new `colors` module; other counts are a TypeError.
 *
 * The instances are still `ColorNativeObject` of the current implementation: the modules not migrated yet (images,
 * dialogs, ui, compose, console, s13n, canvas paints) recognize Color objects by that class, so a new class would break
 * passing a Color to them while the switch is on. Its methods therefore keep the current behavior until those modules
 * move (ROADMAP P2.3, pending).
 *
 * zh-CN: 新脚本模块层上的 Color (P2.3): 可调用亦可构造 (Color(...), new Color(...), colors.build(...)), 函数对象的原型链为四个颜色表, 与现有模块相同.
 * 参数: 无 (黑色), 一个颜色 (OmniColor 或 Color 实例), (red, green, blue) 或 (red, green, blue, alpha), 由新 colors 模块转换; 其他个数为 TypeError.
 * 实例仍为现有实现的 ColorNativeObject: 尚未迁移的模块 (images, dialogs, ui, compose, console, s13n, 画笔) 按该类识别 Color 对象, 换成新类会在开关打开时
 * 破坏向它们传入 Color 的用法, 因此实例方法在这些模块迁移前保持现有行为 (ROADMAP P2.3 待决).
 */
@ScriptModule(name = "Color", dollarPrefix = false)
class ColorModule(runtime: ScriptRuntime?) {

    private val colors = ColorsModule(runtime)

    @ScriptInvoke(arity = [0, 4])
    fun invoke(vararg args: Any?): Any = newColor(colors, "Color", args)

    @ScriptConstruct(arity = [0, 4])
    fun construct(vararg args: Any?): Any = newColor(colors, "new Color", args)

    @ScriptPrototype
    fun prototypes(): List<Any> = listOf(ColorTable.Android, ColorTable.Css, ColorTable.Web, ColorTable.Material)

    internal companion object {

        fun newColor(colors: ColorsModule, function: String, args: Array<out Any?>): Any = when (args.size) {
            0 -> ColorNativeObject()
            1 -> ColorNativeObject(colors.int(args[0], function))
            3 -> ColorNativeObject(colors.rgbOf(function, args[0], args[1], args[2]))
            4 -> ColorNativeObject(colors.argbOf(function, args[3], args[0], args[1], args[2]))
            else -> throw ColorValues.typeError(function, ScriptMessages.noOverload(args.joinToString { org.autojs.autojs.runtime.api.modules.ScriptArgs.describe(it) }, SIGNATURES))
        }

        private const val SIGNATURES = "(), (color), (red, green, blue), (red, green, blue, alpha)"

    }

}
