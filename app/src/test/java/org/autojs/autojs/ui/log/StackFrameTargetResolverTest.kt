package org.autojs.autojs.ui.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StackFrameTargetResolverTest {

    @Test
    fun normalizeFileUriSourceName() {
        assertEquals(
            "/sdcard/project/main.mjs",
            StackFrameTargetResolver.normalizeSourceName("file:///sdcard/project/main.mjs"),
        )
    }

    @Test
    fun matchAbsoluteCurrentFile() {
        assertTrue(
            StackFrameTargetResolver.matchesCurrentFile(
                "/sdcard/project/main.js",
                "/sdcard/project/main.js",
            )
        )
    }

    @Test
    fun matchRelativeCurrentFileName() {
        assertTrue(
            StackFrameTargetResolver.matchesCurrentFile(
                "main.js",
                "/sdcard/project/main.js",
            )
        )
    }

    @Test
    fun matchRelativeCurrentFileSuffix() {
        assertTrue(
            StackFrameTargetResolver.matchesCurrentFile(
                "src/main.ts",
                "/sdcard/project/src/main.ts",
            )
        )
    }

    @Test
    fun rejectDifferentFile() {
        assertFalse(
            StackFrameTargetResolver.matchesCurrentFile(
                "/sdcard/project/dep.js",
                "/sdcard/project/main.js",
            )
        )
    }

    @Test
    fun resolveCurrentFileSourceToCurrentPath() {
        assertEquals(
            "/sdcard/project/src/main.ts",
            StackFrameTargetResolver.resolveSourcePath(
                "src/main.ts",
                "/sdcard/project/src/main.ts",
            )
        )
    }

    @Test
    fun resolveRelativeSiblingSourceAgainstCurrentPath() {
        assertEquals(
            "/sdcard/project/dep.mjs",
            StackFrameTargetResolver.resolveSourcePath(
                "dep.mjs",
                "/sdcard/project/main.mjs",
            )
        )
    }

    @Test
    fun resolveAbsoluteSourceWithoutCurrentPath() {
        assertEquals(
            "/sdcard/project/dep.cjs",
            StackFrameTargetResolver.resolveSourcePath(
                "/sdcard/project/dep.cjs",
                null,
            )
        )
    }
}
