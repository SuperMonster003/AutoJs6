package org.autojs.autojs.core.plugin.installer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The routing decision table of plugin roadmap D21: which formats the system installer may take. */
class PackageInstallRouterFormatTest {

    @Test
    fun `formats are recognized by extension regardless of case`() {
        assertEquals(PackageInstallRouter.PackageFormat.APK, PackageInstallRouter.PackageFormat.fromDisplayName("app.apk"))
        assertEquals(PackageInstallRouter.PackageFormat.XAPK, PackageInstallRouter.PackageFormat.fromDisplayName("Game.XAPK"))
        assertEquals(PackageInstallRouter.PackageFormat.APKM, PackageInstallRouter.PackageFormat.fromDisplayName("a.b.apkm"))
        assertEquals(PackageInstallRouter.PackageFormat.AAB, PackageInstallRouter.PackageFormat.fromDisplayName("bundle.aab"))
        assertNull(PackageInstallRouter.PackageFormat.fromDisplayName("notes.txt"))
        assertNull(PackageInstallRouter.PackageFormat.fromDisplayName("noextension"))
        assertNull(PackageInstallRouter.PackageFormat.fromDisplayName(null))
    }

    @Test
    fun `only plain APKs may go to the system installer and AAB is never installable`() {
        assertTrue(PackageInstallRouter.PackageFormat.APK.systemInstallable)
        PackageInstallRouter.PackageFormat.entries.filter { it != PackageInstallRouter.PackageFormat.APK }.forEach { format ->
            assertFalse(format.name, format.systemInstallable)
        }
        assertFalse(PackageInstallRouter.PackageFormat.AAB.installable)
        PackageInstallRouter.PackageFormat.entries.filter { it != PackageInstallRouter.PackageFormat.AAB }.forEach { format ->
            assertTrue(format.name, format.installable)
        }
    }
}
