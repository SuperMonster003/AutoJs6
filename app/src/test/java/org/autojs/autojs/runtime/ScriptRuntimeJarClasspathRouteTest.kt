package org.autojs.autojs.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class ScriptRuntimeJarClasspathRouteTest {

    @Test
    fun emptyClasspathFailsBeforePathResolutionOrDispatch() {
        var resolutions = 0
        var dispatches = 0

        assertThrows(IllegalArgumentException::class.java) {
            ScriptRuntimeJarClasspathRoute.dispatch(
                programPath = "program.jar",
                orderedClasspathPaths = emptyArray(),
                resolvePath = { path ->
                    resolutions += 1
                    path
                },
            ) { _, _ ->
                dispatches += 1
            }
        }

        assertEquals(0, resolutions)
        assertEquals(0, dispatches)
    }

    @Test
    fun programAndClasspathAreResolvedOnceAndDispatchedInExactCallerOrder() {
        val resolutions = mutableListOf<String>()
        var dispatches = 0

        ScriptRuntimeJarClasspathRoute.dispatch(
            programPath = "program.jar",
            orderedClasspathPaths = arrayOf("second.jar", "first.jar", "second.jar"),
            resolvePath = { path ->
                resolutions += path
                "resolved/$path"
            },
        ) { program, classpath ->
            dispatches += 1
            assertEquals(File("resolved/program.jar"), program)
            assertEquals(
                listOf(
                    File("resolved/second.jar"),
                    File("resolved/first.jar"),
                    File("resolved/second.jar"),
                ),
                classpath,
            )
        }

        assertEquals(listOf("program.jar", "second.jar", "first.jar", "second.jar"), resolutions)
        assertEquals(1, dispatches)
    }
}
