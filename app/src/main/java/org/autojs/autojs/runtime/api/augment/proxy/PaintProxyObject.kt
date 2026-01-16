package org.autojs.autojs.runtime.api.augment.proxy

import android.graphics.Paint
import android.os.Build
import org.autojs.autojs.runtime.api.augment.colors.Colors
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined

/**
 * Paint proxy object which preserves identity and only intercepts setColor overload ambiguity.
 * zh-CN: Paint 代理对象, 保持对象身份一致性, 且仅拦截 setColor 重载歧义问题.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Jan 16, 2026.
 * Modified by SuperMonster003 as of Jan 16, 2026.
 */
class PaintProxyObject(
    scope: Scriptable,
    base: Scriptable,
    paint: Paint,
) : JavaProxyObject<Paint>(scope, base, paint) {

    override fun getClassName(): String = "Paint"

    // Rhino JS uses only "number", and Android Q+ has both setColor(int) and setColor(long).
    // In many JS->Java bridges, a JS number is represented as a Java Number and may match the long overload first.
    // A plain ColorInt is 32-bit; when widened to long, it is typically just a sign-extension of low 32 bits.
    // A real ColorLong (from Color.pack) uses high bits for metadata and is not equal to sign-extended int.
    //
    // zh-CN:
    //
    // Rhino JS 只有 number, 而 Android Q+ 同时存在 setColor(int) 与 setColor(long).
    // 在许多 JS->Java 桥接中, JS number 会被表示为 Java Number, 并可能优先命中 long 重载.
    // 普通 ColorInt 是 32-bit 值; 扩展为 long 时通常只是低 32 位的符号扩展.
    // 真正的 ColorLong (由 Color.pack 得到) 高位包含元数据, 通常不等于 int 的符号扩展.
    private fun isLikelyColorIntFromJsNumber(value: Long): Boolean {
        val asInt = value.toInt()
        return value == asInt.toLong()
    }

    private val setColorFn: BaseFunction = newBaseFunction("setColor", { args ->

        // Intercept Paint#setColor overload ambiguity on Android Q+.
        // zh-CN: 在 Android Q+ 上拦截 Paint#setColor 的重载歧义问题.
        val arg = args.getOrNull(0)

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                when (arg) {
                    is Number -> {
                        val l = arg.toLong()
                        when {
                            isLikelyColorIntFromJsNumber(l) -> {
                                // Treat as ColorInt and use safe implementation.
                                // zh-CN: 按 ColorInt 处理并使用安全实现.
                                Colors.setPaintColorRhino(target, l.toInt())
                            }
                            else -> {
                                // Preserve real ColorLong semantics.
                                // zh-CN: 保留真正的 ColorLong 语义.
                                target.setColor(l)
                            }
                        }
                    }
                    else -> {
                        // Non-number inputs: ColorHex/ColorName/etc.
                        // zh-CN: 非数字输入: ColorHex/ColorName 等.
                        Colors.setPaintColorRhino(target, arg)
                    }
                }
            }
            else -> {
                // On pre-Q devices, setColor(int) works normally.
                // zh-CN: 在 Q 之前, setColor(int) 正常工作.
                Colors.setPaintColorRhino(target, arg)
            }
        }

        // Return undefined like Java void.
        // zh-CN: 与 Java void 一致返回 undefined.
        Undefined.instance
    }, NOT_CONSTRUCTABLE)

    override fun getIntercepted(name: String): Any? =
        when (name) {
            "setColor" -> setColorFn
            else -> null
        }

    override fun putIntercepted(name: String, value: Any?): Boolean =
        when (name) {
            // Support property form: paint.color = ...
            // zh-CN: 支持属性形式: paint.color = ...
            "color" -> withRhinoContext { cx ->
                setColorFn.call(cx, this, this, arrayOf(value))
                true
            }
            else -> false
        }

    override fun hasIntercepted(name: String): Boolean =
        name == "setColor" || name == "color"

}
