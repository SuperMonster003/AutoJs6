package org.autojs.plugin.packagearchive

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ManifestPermissionDeclarationsTest {
    private val android = ManifestRootAttributes.ANDROID_NAMESPACE

    @Test fun previewUsesActualDirectChildDeclarationsAndTheAndroidNamespace() {
        val summary = ManifestSummaryParser.parse("""
            <!-- <uses-permission android:name="fake.before.root" /> -->
            <manifest xmlns:a="$android" xmlns:tools="http://schemas.android.com/tools" package="com.example">
                <!-- <uses-permission a:name="fake.comment" /> -->
                <![CDATA[<uses-permission a:name="fake.cdata" />]]>
                <uses-permission tools:name="fake.shadow" a:name="android.permission.CAMERA" />
                <uses-permission a:name="android.permission.CAMERA" />
                <uses-permission-sdk-23 a:name="android.permission.READ_CONTACTS" />
                <uses-permission-sdk-m a:name="android.permission.READ_SMS" />
                <uses-permission-sdk-99 a:name="fake.unknown.tag" />
                <uses-permission tools:name="fake.foreign.only" />
                <application><uses-permission a:name="fake.nested" /></application>
            </manifest>
        """.trimIndent())
        assertEquals(listOf("android.permission.CAMERA", "android.permission.READ_CONTACTS", "android.permission.READ_SMS"), summary.requestedPermissions)
    }

    @Test fun aliasPrefixesAndNumericEntitiesCannotChangePermissionIdentity() {
        val summary = ManifestSummaryParser.parse("""<manifest package="com.example" xmlns:p="$android">
            <uses-permission p:name="android.permission.CAM&#69;RA" /></manifest>""")
        assertEquals(listOf("android.permission.CAMERA"), summary.requestedPermissions)
    }

    @Test fun ambiguousOrIncompletePermissionDocumentsFailInsteadOfReturningAPartialPreview() {
        for (document in listOf(
            """<manifest xmlns:a="$android" xmlns:b="$android"><uses-permission a:name="one" b:name="two" /></manifest>""",
            """<manifest><uses-permission android:name="one" /></manifest>""",
            """<manifest xmlns:a="$android"><uses-permission a:name="@string/permission" /></manifest>""",
            """<manifest xmlns:a="$android"><uses-permission a:name="" /></manifest>""",
            """<manifest xmlns:a="$android"><uses-permission a:name="${"x".repeat(1025)}" /></manifest>""",
            """<manifest xmlns:a="$android"><uses-permission a:name="android.permission.CAMERA" /><application></manifest>""",
            """<manifest xmlns:a="$android" xmlns:p="urn:foreign"><p:uses-permission a:name="one" /></manifest>""",
        )) assertThrows(document.take(100), IOException::class.java) { ManifestSummaryParser.parse(document) }
    }
}
