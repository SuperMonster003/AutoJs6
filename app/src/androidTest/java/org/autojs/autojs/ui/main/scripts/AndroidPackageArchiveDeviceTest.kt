package org.autojs.autojs.ui.main.scripts

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File
import org.autojs.plugin.packagearchive.AndroidPackageArchiveInspector
import org.autojs.plugin.packagearchive.AndroidPackageFormat
import org.autojs.plugin.packagearchive.PackageDeviceSpec

/** Runs the real Android ZIP/XML stack against an APK supplied on the test device. */
class AndroidPackageArchiveDeviceTest {

    @Test
    fun suppliedApkCanBeInspected() {
        val arguments = InstrumentationRegistry.getArguments()
        val path = arguments.getString("packageArchivePath")
        assumeNotNull(path)
        val source = File(requireNotNull(path))
        assertTrue("APK fixture is not readable: $path", source.isFile && source.canRead())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = AndroidPackageArchiveInspector.inspect(source, PackageDeviceSpec.from(context))

        assertEquals(AndroidPackageFormat.APK, archive.format)
        assertEquals(1, archive.apkEntryCount)
        assertTrue(archive.baseManifest?.packageName?.isNotBlank() == true)
        arguments.getString("expectedPackageName")?.let {
            assertEquals(it, archive.baseManifest?.packageName)
        }
        arguments.getString("expectedVersionName")?.let {
            assertEquals(it, archive.baseManifest?.versionName)
        }
        arguments.getString("expectedVersionCode")?.let {
            assertEquals(it.toLong(), archive.baseManifest?.versionCode)
        }
    }
}
