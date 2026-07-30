package org.autojs.autojs.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileUtilsUriCacheTest {

    @Test
    fun providerFileNamesAreReducedToSafeBasenames() {
        assertEquals("payload.apk", FileUtils.sanitizeUriCacheFileName("../payload.apk"))
        assertEquals("payload.apks", FileUtils.sanitizeUriCacheFileName("..\\payload.apks"))
        assertEquals("bundle.xapk", FileUtils.sanitizeUriCacheFileName("/absolute/bundle.xapk"))
        assertEquals("package.bin", FileUtils.sanitizeUriCacheFileName("..\u0000"))
    }

    @Test
    fun providerFileNamesAreBoundedAndContainNoPathSeparators() {
        val result = FileUtils.sanitizeUriCacheFileName("folder/${"a".repeat(256)}.apk")

        assertTrue(result.length <= 180)
        assertTrue(result.endsWith(".apk"))
        assertFalse('/' in result)
        assertFalse('\\' in result)
    }
}
