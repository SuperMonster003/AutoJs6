package org.autojs.autojs.runtime.api.augment.util

import android.os.Build
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY
import org.mozilla.javascript.VarScope

object VersionCodesInfo {

    val list = VersionCodesInfoGenerated.list

    init {
        // The generated object is process-wide but takes the prototype and parent scope of whichever script first touches
        // this class, and kept that script's top-level scope and runtime reachable for the lifetime of the process
        // (docs/memory/ROADMAP.md H18). It is no longer used; detach it from that script. Each runtime builds its own object (newObj).
        // zh-CN: 生成的对象是进程级的, 却取得最先访问本类的脚本的原型与父作用域, 曾在进程生命周期内一直钉住该脚本的顶层作用域与运行时
        // (docs/memory/ROADMAP.md H18). 该对象不再使用, 将其与该脚本断开; 每个运行时各自创建自己的对象 (newObj).
        VersionCodesInfoGenerated.obj.apply {
            parentScope = null
            prototype = null
        }
    }

    /**
     * The object of `util.versionCodes` entries for one script, in that script's [scope].
     * zh-CN: 某个脚本的 `util.versionCodes` 条目对象, 位于该脚本的 [scope] 中.
     */
    fun newObj(scope: VarScope): NativeObject = newNativeObject(scope).apply {
        list.forEach { info ->
            defineProperty(info.versionCode, { info.toNativeObject() }, null, READONLY or PERMANENT)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun briefOfVersionInt(versionInt: Int, isCompact: Boolean = false): String {
        return list.find { runCatching { it.apiLevel.toInt() }.getOrNull() == versionInt }?.let { info ->
            var result = "${if (isCompact) "API Lv." else "Android API Level "}${info.apiLevel}"
            info.releaseName.takeIf { it.isNotBlank() }?.let { result += " ($it)" }
            info.internalCodename.takeIf { it.isNotBlank() }?.let { result += " [$it]" }
            result
        } ?: throw WrappedIllegalArgumentException("$versionInt is an invalid or unknown version int")
    }

    @JvmStatic
    @JvmOverloads
    fun briefOfCurrentVersionInt(isCompat: Boolean = false) = briefOfVersionInt(Build.VERSION.SDK_INT, isCompat)

}
