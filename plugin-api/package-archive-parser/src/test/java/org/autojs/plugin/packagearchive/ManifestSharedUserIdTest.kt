package org.autojs.plugin.packagearchive

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ManifestSharedUserIdTest {
    private val android = "http://schemas.android.com/apk/res/android"

    @Test fun verifiedRootUsesTheAndroidNamespaceAndIgnoresCommentDecoys() {
        val summary = ManifestSummaryParser.parse("""<!-- <manifest package="fake.package" android:sharedUserId="fake.uid"> -->
            <manifest xmlns:a="$android" xmlns:x="urn:unrelated" package="real.package"
                x:sharedUserId="fake.uid" a:sharedUserId="android.uid.&#115;ystem"><application /></manifest>""")
        assertEquals("real.package", summary.packageName)
        assertEquals("android.uid.system", summary.sharedUserId)
    }

    @Test fun aForeignAttributeCannotMasqueradeAsAnAndroidSharedUidOrPackage() {
        val summary = ManifestSummaryParser.parse("""<manifest xmlns:x="urn:unrelated" x:package="fake.package"
            package="real.package" x:sharedUserId="fake.uid" />""")
        assertEquals("real.package", summary.packageName)
        assertNull(summary.sharedUserId)
        assertNull(ManifestSummaryParser.parse("""<manifest package="real.package" />""").sharedUserId)
    }

    @Test fun duplicateUnboundOrUnresolvedDeclarationsFailInsteadOfReturningAbsence() {
        for (xml in listOf(
            """<manifest xmlns:a="$android" xmlns:b="$android" a:sharedUserId="one.uid" b:sharedUserId="two.uid" />""",
            """<manifest android:sharedUserId="one.uid" />""",
            """<manifest xmlns:a="$android" a:sharedUserId="@string/shared" />""",
            """<manifest xmlns:a="$android" a:sharedUserId="" />""",
            """<manifest xmlns:a="$android" a:sharedUserId="${"a".repeat(1025)}" />""",
            """<!DOCTYPE manifest SYSTEM "https://invalid.example/never"><manifest package="real.package" />""",
            """<application><manifest package="nested.fake" /></application>""",
        )) assertThrows(xml.take(90), IOException::class.java) { ManifestSummaryParser.parse(xml) }
    }

    @Test fun theOriginalConstructorRemainsAvailableAndDefaultsToNoSharedUid() {
        val summary = ManifestSummary("real.package", null, null, false, false, 1, null, null, null, null,
            null, emptyList(), emptyList())
        assertNull(summary.sharedUserId)
        assertTrue(ManifestSummary::class.java.constructors.any { it.parameterCount == 13 })
    }
}
