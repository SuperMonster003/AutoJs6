package org.autojs.autojs.runtime.api.modules.epub

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.runtime.api.modules.generated.ScriptModules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `epub` module only exposes the functions [EpubModule] declares; a `@RhinoRuntimeFunctionInterface` method of
 * [Epub] without its function there compiles and passes its own tests but is unreachable from a script ("Cannot find
 * function ..."). `epub.annotations(path)` shipped that way once (plugin roadmap P9.6 device check, then with the
 * `selfAssignmentFunctions` list of the old layer, which P5.2 removed), so the module's functions (its generated binding)
 * are compared with the declared methods here.
 * zh-CN: `epub` 模块只暴露 [EpubModule] 声明的函数; [Epub] 中在那里没有对应函数的 `@RhinoRuntimeFunctionInterface` 方法能编译并通过自身测试,
 * 但脚本里无法访问 ("Cannot find function ..."). `epub.annotations(path)` 曾经这样漏过 (插件路线图 P9.6 设备检查, 当时是旧层的
 * `selfAssignmentFunctions` 列表, P5.2 已删除), 因此这里把模块的函数 (生成的绑定) 与声明的方法逐一对照.
 */
class EpubGlobalFunctionsTest {

    @Test
    fun everyRuntimeFunctionOfTheEpubGlobalIsAssigned() {
        val declared = Epub::class.java.declaredMethods
            .filter { it.isAnnotationPresent(RhinoRuntimeFunctionInterface::class.java) }
            .map { it.name }
            .toSortedSet()
        val exported = ScriptModules.bindings.single { it.spec.name == "epub" }.spec.functions
            .filter { it.onModule }
            .map { it.name }
            .toSortedSet()

        assertEquals(declared, exported)
        assertTrue(exported.containsAll(listOf("annotations", "annotationsAsync")))
    }

}
