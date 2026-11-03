package org.autojs.autojs.util

import org.junit.Assert.assertEquals
import org.junit.Test

class EnvironmentUtilsLegacySdcardPathTest {

    private val root = "/storage/emulated/0"

    private fun remap(path: String, existing: Set<String>, externalStorageRoot: String = root) =
        EnvironmentUtils.remapLegacySdcardPath(path, externalStorageRoot) { it in existing }

    @Test
    fun `an existing path is kept as it is`() {
        assertEquals("/sdcard/Scripts/a.js", remap("/sdcard/Scripts/a.js", setOf("/sdcard/Scripts/a.js", "$root/Scripts/a.js")))
    }

    @Test
    fun `a missing sdcard path maps to the external storage root`() {
        assertEquals("$root/Scripts/a.js", remap("/sdcard/Scripts/a.js", setOf("$root/Scripts/a.js")))
        assertEquals(root, remap("/sdcard", setOf(root)))
    }

    @Test
    fun `a missing mnt sdcard path maps to the external storage root`() {
        assertEquals("$root/Scripts/a.js", remap("/mnt/sdcard/Scripts/a.js", setOf("$root/Scripts/a.js")))
    }

    @Test
    fun `a mapped location that does not exist either keeps the original path`() {
        assertEquals("/sdcard/Scripts/missing.js", remap("/sdcard/Scripts/missing.js", emptySet()))
    }

    @Test
    fun `paths that only share the prefix text are not mapped`() {
        assertEquals("/sdcard2/Scripts/a.js", remap("/sdcard2/Scripts/a.js", setOf("${root}2/Scripts/a.js", "$root/2/Scripts/a.js")))
        assertEquals("/data/local/tmp/a.js", remap("/data/local/tmp/a.js", setOf("$root/a.js")))
    }

    @Test
    fun `an external storage root of sdcard itself is not mapped again`() {
        assertEquals("/sdcard/Scripts/a.js", remap("/sdcard/Scripts/a.js", setOf("/sdcard/Scripts/a.js/x"), externalStorageRoot = "/sdcard/"))
    }
}
