package org.autojs.autojs.ui.main.scripts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LargePackageInspectionTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val device = PackageDeviceSpec(35, listOf("arm64-v8a"), 440, listOf("en-US"))

    @Test
    fun ordinaryAndNestedPackagesAcceptSeventyThousandEntriesWithManifestAtTheEnd() {
        val apk = temporaryFolder.newFile("large.apk")
        ZipOutputStream(apk.outputStream().buffered()).use { zip ->
            repeat(70_000) { index ->
                zip.putNextEntry(ZipEntry("res/raw/resource_$index"))
                zip.closeEntry()
            }
            writeEntry(zip, "lib/arm64-v8a/liblarge.so", byteArrayOf(1, 2, 3))
            writeEntry(zip, "classes.dex", byteArrayOf(1, 2))
            writeEntry(zip, "AndroidManifest.xml", manifest())
        }
        assertEquals("com.example.large", AndroidPackageArchiveInspector.inspect(apk, device).baseManifest?.packageName)
        val xml = apk.inputStream().use(ApkManifestDisplayDecoder::decodeApk)
        assertTrue(xml.contains("com.example.large"))
        val container = temporaryFolder.newFile("large.xapk")
        ZipOutputStream(container.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("base.apk"))
            apk.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            repeat(18_184) { index -> writeEntry(zip, "assets/entry_$index", byteArrayOf()) }
        }
        val inspected = AndroidPackageArchiveInspector.inspect(container, device)
        assertEquals("com.example.large", inspected.baseManifest?.packageName)
        assertEquals(1, inspected.selectedApks.size)
        
    }

    @Test
    fun textManifestBeyondFourMiBCanBeRead() {
        val apk = temporaryFolder.newFile("large-manifest.apk")
        val content = " ".repeat(4 * 1024 * 1024 + 1).toByteArray() + manifest()
        ZipOutputStream(apk.outputStream()).use { writeEntry(it, "AndroidManifest.xml", content) }
        assertTrue(ApkManifestDisplayDecoder.decode(apk).contains("com.example.large"))
    }

    private fun manifest() = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.large" android:versionCode="1"><application /></manifest>""".toByteArray()

    private fun writeEntry(zip: ZipOutputStream, name: String, content: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content)
        zip.closeEntry()
    }
}
