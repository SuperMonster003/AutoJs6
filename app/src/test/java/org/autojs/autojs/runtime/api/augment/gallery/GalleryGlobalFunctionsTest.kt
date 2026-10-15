package org.autojs.autojs.runtime.api.augment.gallery

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.RhinoTestRuntime
import org.autojs.autojs.runtime.api.gallery.GalleryService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import sun.misc.Unsafe

/**
 * The `gallery` global only exposes the functions named in `selfAssignmentFunctions`; a
 * `@RhinoRuntimeFunctionInterface` method missing from that list compiles but is unreachable from a
 * script, so the list is compared with the declared methods (as for `epub`).
 * zh-CN: `gallery` 全局对象只暴露 `selfAssignmentFunctions` 里列出的函数; 未列入的 `@RhinoRuntimeFunctionInterface` 方法能编译但脚本里无法访问,
 * 因此这里把该列表与声明的方法逐一对照 (同 `epub`).
 */
class GalleryGlobalFunctionsTest {

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
    fun everyRuntimeFunctionOfTheGalleryGlobalIsAssigned() {
        val gallery = Gallery(RhinoTestRuntime.create(scope), allocateWithoutConstructor(GalleryService::class.java))
        val declared = Gallery::class.java.declaredMethods
            .filter { it.isAnnotationPresent(RhinoRuntimeFunctionInterface::class.java) }
            .map { it.name }
            .toSortedSet()
        val assigned = gallery.selfAssignmentFunctions.map { it.toString() }.toSortedSet()

        assertEquals(declared, assigned)
        assertEquals(
            sortedSetOf("convert", "convertAsync", "edit", "editAsync", "isAvailable", "isAvailableAsync", "pick", "pickAsync", "view", "viewAsync"),
            assigned,
        )
        assertEquals(assigned, gallery.scriptErrorPassThroughFunctions.toSortedSet())
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
