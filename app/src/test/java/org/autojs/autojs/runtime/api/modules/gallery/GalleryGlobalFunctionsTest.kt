package org.autojs.autojs.runtime.api.modules.gallery

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.runtime.api.modules.generated.ScriptModules
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The `gallery` module only exposes the functions [GalleryModule] declares; a `@RhinoRuntimeFunctionInterface` method of
 * [Gallery] without its function there compiles but is unreachable from a script, so the module's functions (its
 * generated binding; the `selfAssignmentFunctions` list of the old layer until P5.2) are compared with the declared
 * methods (as for `epub`).
 * zh-CN: `gallery` 模块只暴露 [GalleryModule] 声明的函数; [Gallery] 中在那里没有对应函数的 `@RhinoRuntimeFunctionInterface` 方法能编译但脚本里无法访问,
 * 因此这里把模块的函数 (生成的绑定; P5.2 之前为旧层的 `selfAssignmentFunctions` 列表) 与声明的方法逐一对照 (同 `epub`).
 */
class GalleryGlobalFunctionsTest {

    @Test
    fun everyRuntimeFunctionOfTheGalleryGlobalIsAssigned() {
        val declared = Gallery::class.java.declaredMethods
            .filter { it.isAnnotationPresent(RhinoRuntimeFunctionInterface::class.java) }
            .map { it.name }
            .toSortedSet()
        val exported = ScriptModules.bindings.single { it.spec.name == "gallery" }.spec.functions
            .filter { it.onModule }
            .map { it.name }
            .toSortedSet()

        assertEquals(declared, exported)
        assertEquals(
            sortedSetOf("convert", "convertAsync", "edit", "editAsync", "isAvailable", "isAvailableAsync", "pick", "pickAsync", "view", "viewAsync"),
            exported,
        )
    }

}
