package org.autojs.autojs.core.image

import android.graphics.Paint
import android.util.Log
import org.autojs.autojs.runtime.api.augment.colors.Colors
import org.autojs.autojs6.BuildConfig
import java.util.concurrent.ConcurrentHashMap

// @Hint by JetBrains AI Assistant (GPT-5.2) on Jan 16, 2026.
//  ! This class is no longer used (replaced by org.autojs.autojs.runtime.api.augment.proxy.PaintProxyObject).
//  !
//  ! Reason 1: RhinoPaint is a copy-based wrapper (class RhinoPaint(paint) : Paint(paint)).
//  ! Copy-based wrapping breaks identity semantics between Java and JavaScript.
//  ! Specifically, the JS side may see and mutate a copied Paint instance
//  ! while native/Java code still holds the original instance, or vice versa.
//  !
//  ! Reason 2: OEM Canvas implementations may mutate caller-provided Paint objects
//  ! during drawing (e.g. MIUI's MiuiCanvas calling Paint#setColor(long) internally).
//  ! With RhinoPaint, such OEM mutations can be mixed with our overload-fix logic
//  ! and make behavior harder to reason about and debug.
//  !
//  ! Reason 3: The original goal of RhinoPaint was to mitigate Android Q+ Paint#setColor
//  ! overload ambiguity for JS numbers.
//  ! However, a subclass-based approach cannot fully preserve ColorLong semantics
//  ! in all cases without additional heuristics, and still does not solve identity issues.
//  !
//  ! Reason 4: PaintProxyObject uses a composition-based proxy with Wrapper support.
//  ! This preserves the original Paint instance identity, so method overload resolution
//  ! and `instanceof Paint` behave naturally in Rhino.
//  ! The proxy intercepts only the minimal surface area (setColor / color assignment)
//  ! and forwards everything else to the underlying Java wrapper, reducing side effects.
//  ! Note: For OEM paint-mutation issues (e.g. MIUI), the draw-time paint copy workaround
//  ! is implemented in ScriptCanvas.safePaint(...).
//  !
//  ! zh-CN:
//  !
//  ! 当前类不再使用 (由 org.autojs.autojs.runtime.api.augment.proxy.PaintProxyObject 替代).
//  !
//  ! 原因 1: RhinoPaint 属于 "复制式包装" (class RhinoPaint(paint) : Paint(paint)).
//  ! 复制式包装会破坏 Java 与 JavaScript 之间的对象身份一致性语义.
//  ! 具体表现为: JS 侧可能看到并修改的是 Paint 的副本, 而 Java/系统侧仍持有原始实例 (反之亦然).
//  !
//  ! 原因 2: 某些厂商的 Canvas 实现可能在绘制过程中修改调用方传入的 Paint 对象 (例如 MIUI 的 MiuiCanvas 在内部调用 Paint#setColor(long)).
//  ! 使用 RhinoPaint 时, 这类 OEM 的 "写回" 行为会与我们的重载修复逻辑交织, 使行为更难推理/更难调试.
//  !
//  ! 原因 3: RhinoPaint 的初衷是缓解 Android Q+ 上 Paint#setColor 的重载歧义问题 (针对 JS number).
//  ! 但基于子类的方案即使加入判定逻辑, 也难以在所有场景中完整保留 ColorLong 语义, 并且仍无法解决对象身份不一致的问题.
//  !
//  ! 原因 4: PaintProxyObject 使用 "组合式代理" (composition) 并实现 Wrapper.
//  ! 这样可以保留原始 Paint 实例身份, 并使 Rhino 的方法重载匹配与 `instanceof Paint` 表现符合直觉.
//  ! 代理仅拦截最小必要面 (setColor / color 属性赋值), 其它成员全部转发到底层 Java wrapper, 从而减少副作用.
//  ! 注意: 针对 OEM 绘制时修改 Paint 的问题 (例如 MIUI), 已在 ScriptCanvas.safePaint(...) 中实现 "绘制时复制 paint" 的规避策略.
/**
 * Created by SuperMonster003 on Jun 15, 2025.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Jan 16, 2026.
 * Modified by SuperMonster003 as of Jan 16, 2026.
 */
class RhinoPaint(paint: Paint) : Paint(paint) {

    companion object {
        // Deduplicate stack traces to avoid log flooding.
        // zh-CN: 对调用栈进行去重, 避免日志洪泛.
        private val sLogged = ConcurrentHashMap.newKeySet<String>()
    }

    private fun logOnce(tag: String) {
        if (!BuildConfig.DEBUG) return

        val stack = Log.getStackTraceString(Throwable())

        // Use tag + first part of stack as a dedupe key.
        // zh-CN: 使用 tag + 调用栈前半段作为去重键.
        val key = buildString {
            append(tag)
            append("|")
            append(stack.take(600))
        }

        if (!sLogged.add(key)) return
        Log.d("RhinoPaint", "Suspicious Paint mutation: $tag\n$stack")
    }

    // Fix ColorLong overload ambiguity on Android Q+ while preserving real ColorLong.
    // zh-CN: 修复 Android Q+ 上 ColorLong 重载歧义, 同时保留真正的 ColorLong 语义.
    override fun setColor(color: Long) {
        // Detect whether "color" is just a sign-extended 32-bit ColorInt.
        // zh-CN: 检测 color 是否仅为 32-bit ColorInt 的符号扩展.
        val asInt = color.toInt()
        val looksLikeColorInt = (color == asInt.toLong())

        when {
            looksLikeColorInt -> {
                // JS number commonly lands here and should be treated as ColorInt.
                // zh-CN: JS number 常命中该重载, 应按 ColorInt 处理.
                if (color == 0L) {
                    logOnce("setColor(Long=0 as ColorInt)")
                }
                super.setColor(Colors.toIntRhino(asInt))
            }
            else -> {
                // Preserve real ColorLong (contains color space / wide gamut info).
                // zh-CN: 保留真正的 ColorLong (包含颜色空间/广色域信息).
                if (color == 0L) {
                    logOnce("setColor(Long=0 as ColorLong)")
                }
                super.setColor(color)
            }
        }
    }

    override fun setColor(color: Int) {
        // Only log when the incoming value is 0 (transparent).
        // zh-CN: 仅当传入值为 0 (透明) 时记录.
        if (color == 0) {
            logOnce("setColor(Int=0)")
        }
        super.setColor(color)
    }

    fun setColor(color: Any?) {
        val c = Colors.toIntRhino(color)
        // Only log when the computed int value is 0 (transparent).
        // zh-CN: 仅当计算出的 int 值为 0 (透明) 时记录.
        if (c == 0) {
            logOnce("setColor(Any?->Int=0)")
        }
        super.setColor(c)
    }

    override fun setAlpha(a: Int) {
        // Log only when alpha is explicitly cleared to 0.
        // zh-CN: 仅当 alpha 被显式清为 0 时记录.
        if (a == 0) {
            logOnce("setAlpha(0)")
        }
        super.setAlpha(a)
    }

    override fun reset() {
        // reset() wipes out color/alpha and more, always suspicious for user Paint.
        // zh-CN: reset() 会清空 color/alpha 等多项状态, 对用户 Paint 来说总是可疑.
        logOnce("reset()")
        super.reset()
    }

}
