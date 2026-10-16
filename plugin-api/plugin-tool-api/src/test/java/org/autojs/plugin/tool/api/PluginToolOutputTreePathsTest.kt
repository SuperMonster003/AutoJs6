package org.autojs.plugin.tool.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PluginToolOutputTreePathsTest {

    @Test
    fun relativePathsWithPlainSegmentsAreAccepted() {
        listOf("a.txt", "docs/readme.md", "deep/a/b/c/", "名字/файл.txt", ".hidden/x", "a..b/c").forEach {
            assertNull(it, PluginToolOutputTreePaths.problemOf(it))
        }
    }

    @Test
    fun escapesAndOddPathsAreRefused() {
        mapOf(
            "" to "empty",
            "/etc/passwd" to "absolute",
            "../up.txt" to "segment",
            "a/../../b" to "segment",
            "a/./b" to "segment",
            "a//b" to "segment",
            "a\\b" to "backslash",
            "a\u0000b" to "control",
            "a\nb" to "control",
        ).forEach { (path, reason) ->
            val problem = PluginToolOutputTreePaths.problemOf(path)
            assertNotNull(path, problem)
            assert(problem!!.contains(reason)) { "$path: $problem" }
        }
    }

    @Test
    fun lengthsAreBounded() {
        assertNull(PluginToolOutputTreePaths.problemOf("x".repeat(255)))
        assertEquals("a segment has more than 255 bytes", PluginToolOutputTreePaths.problemOf("x".repeat(256)))
        val long = (1..20).joinToString("/") { "y".repeat(250) }
        assertEquals("the path has more than 4096 bytes", PluginToolOutputTreePaths.problemOf(long))
    }
}
