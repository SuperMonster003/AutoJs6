package org.autojs.autojs.runtime.api.modules.automator

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.runtime.api.augment.automator.RootAutomatorNativeObject
import org.autojs.autojs.runtime.api.modules.ScriptConstruct
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.autojs.autojs.util.RootUtils
import org.mozilla.javascript.Scriptable

/**
 * `RootAutomator` on the new script module layer (roadmap P3.3): the constructor of the touch injector that works through
 * root or Shizuku (`new RootAutomator(waitForReady?)`, no `$` alias), as with the current module. The instances are the
 * current ones (`augment/automator/RootAutomatorNativeObject`, P5.2).
 *
 * `waitForReady` is a boolean (wait for the device to be ready) or a number of milliseconds to wait; another type is a
 * TypeError and NaN or a negative number a RangeError, checked before root or Shizuku is looked for (D10; the current
 * module read any value as a boolean). Without root or Shizuku the constructor fails as before.
 *
 * zh-CN: 新脚本模块层上的 RootAutomator (P3.3): 经 root 或 Shizuku 注入触摸事件的构造器 (new RootAutomator(waitForReady?), 无 $ 别名), 同现有模块; 实例沿用现有实现
 * (augment/automator/RootAutomatorNativeObject, P5.2 迁出). waitForReady 为布尔值 (是否等待设备就绪) 或等待的毫秒数; 其他类型为 TypeError, NaN 或负数为
 * RangeError, 在检查 root 或 Shizuku 之前判断 (D10; 现有模块把任何值读作布尔值). 没有 root 或 Shizuku 时构造照旧失败.
 */
@ScriptModule(name = "RootAutomator", dollarPrefix = false)
class RootAutomatorModule(private val runtime: ScriptRuntime) {

    @ScriptConstruct
    fun construct(waitForReady: Any? = null): Scriptable {
        val ready: Any = when {
            isNullish(waitForReady) -> false
            waitForReady is Boolean -> waitForReady
            waitForReady is Number -> waitForReady.also {
                val d = it.toDouble()
                if (d.isNaN() || d < 0) throw GlobalValues.rangeError(CONSTRUCTOR, "waitForReady", "non-negative number", it)
            }
            else -> throw GlobalValues.typeError(CONSTRUCTOR, "waitForReady", "boolean | number", waitForReady)
        }
        if (!RootUtils.isRootAvailable() && !WrappedShizuku.isOperational()) {
            throw RuntimeException("$CONSTRUCTOR must be instantiated with root access or shizuku access")
        }
        return RootAutomatorNativeObject(runtime, ready)
    }

    private companion object {
        const val CONSTRUCTOR = "RootAutomator"
    }

}
