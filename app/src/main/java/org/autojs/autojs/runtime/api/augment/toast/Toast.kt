package org.autojs.autojs.runtime.api.augment.toast

import org.autojs.autojs.AutoJs
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.util.RhinoUtils.undefined
import org.mozilla.javascript.Undefined

class Toast(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override val selfAssignmentFunctions = listOf(
        ::dismissAll.name,
    )

    // @Caution by SuperMonster003 on Oct 11, 2022.
    //  ! android.widget.Toast.makeText() doesn't work well on Android API Level 28 (9) [P].
    //  ! There hasn't been a solution for this so far.
    //  ! Tested devices:
    //  ! 1. SONY XPERIA XZ1 Compact (G8441)
    //  ! 2. Android Studio AVD (Android 9.0 x86)
    //  ! zh-CN:
    //  ! android.widget.Toast.makeText() 在安卓 API 级别 28 (9) [P] 上运行状况不佳.
    //  ! 到目前为止, 还没有针对这个问题的解决方案.
    //  ! 进行测试的设备包括:
    //  ! 1. 索尼 XPERIA XZ1 Compact (G8441 型号)
    //  ! 2. Android Studio 自带的安卓虚拟设备 (安卓 9.0 x86 架构)
    override fun invoke(vararg args: Any?): Undefined = call(scriptRuntime, args)

    companion object : FlexibleArray() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun call(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsLengthInRange(args, 0..3) {
            undefined {
                when (it.size) {
                    0 -> ToastParser("").show(scriptRuntime)
                    1 -> ToastParser(args[0]).show(scriptRuntime)
                    2 -> ToastParser(args[0], args[1]).show(scriptRuntime)
                    3 -> ToastParser(args[0], args[1], args[2]).show(scriptRuntime)
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun dismissAll(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            dismissAllRhino(scriptRuntime)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun dismissAllRhino(scriptRuntime: ScriptRuntime) = undefined {
            AutoJs.instance.uiHandler.dismissAllToasts(scriptRuntime)
        }

    }

}
