package org.autojs.autojs.runtime.api.modules.epub

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.RhinoTestRuntime
import org.autojs.autojs.runtime.api.epub.EpubService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import sun.misc.Unsafe

/**
 * The `epub` global only exposes the functions named in `selfAssignmentFunctions`; a
 * `@RhinoRuntimeFunctionInterface` method missing from that list compiles and passes its own tests
 * but is unreachable from a script ("Cannot find function ..."). `epub.annotations(path)` shipped
 * that way once (plugin roadmap P9.6 device check), so the list is compared with the declared
 * methods here.
 * zh-CN: `epub` 全局对象只暴露 `selfAssignmentFunctions` 里列出的函数; 未列入的
 * `@RhinoRuntimeFunctionInterface` 方法能编译并通过自身测试, 但脚本里无法访问 ("Cannot find function ...").
 * `epub.annotations(path)` 曾经这样漏过 (插件路线图 P9.6 设备检查), 因此这里把该列表与声明的方法逐一对照.
 */
class EpubGlobalFunctionsTest {

    private lateinit var context: Context
    private lateinit var scope: TopLevelScope

    @Before
    fun enterContext() {
        context = Context.enter().apply {
            languageVersion = Context.VERSION_ES6
            isInterpretedMode = true
        }
        scope = TopLevelScope().apply { initStandardObjects(context, false) }
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    @Test
    fun everyRuntimeFunctionOfTheEpubGlobalIsAssigned() {
        val epub = Epub(RhinoTestRuntime.create(scope), allocateWithoutConstructor(EpubService::class.java))
        val declared = Epub::class.java.declaredMethods
            .filter { it.isAnnotationPresent(RhinoRuntimeFunctionInterface::class.java) }
            .map { it.name }
            .toSortedSet()
        val assigned = epub.selfAssignmentFunctions.map { it.toString() }.toSortedSet()

        assertEquals(declared, assigned)
        assertTrue(assigned.containsAll(listOf("annotations", "annotationsAsync")))
        assertEquals(assigned, epub.scriptErrorPassThroughFunctions.toSortedSet())
    }

    private fun <T> allocateWithoutConstructor(type: Class<T>): T {
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
            isAccessible = true
            get(null) as Unsafe
        }
        @Suppress("UNCHECKED_CAST")
        return unsafe.allocateInstance(type) as T
    }
}
