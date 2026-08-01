package org.autojs.autojs.ui.main.scripts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AndroidPackageArchiveInspectorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val arm64Device = PackageDeviceSpec(
        sdk = 35,
        abis = listOf("arm64-v8a", "armeabi-v7a"),
        densityDpi = 440,
        locales = listOf("en-US"),
    )

    @Test
    fun singleApkIsDetectedFromContentsAndParsed() {
        val apk = temporaryFolder.newFile("renamed.bin")
        writeZip(apk, "AndroidManifest.xml" to manifest())

        val archive = AndroidPackageArchiveInspector.inspect(apk, arm64Device)

        assertEquals(AndroidPackageFormat.APK, archive.format)
        assertEquals("com.example.demo", archive.baseManifest?.packageName)
        assertEquals(42L, archive.baseManifest?.versionCode)
        assertEquals(24, archive.baseManifest?.minSdk)
        assertTrue(archive.canInstall)
    }

    @Test
    fun installationPreparationRejectsSourceChangedAfterInspection() {
        val apk = temporaryFolder.newFile("mutable.apk")
        writeZip(apk, "AndroidManifest.xml" to manifest())
        val archive = AndroidPackageArchiveInspector.inspect(
            file = apk,
            device = arm64Device,
            prepareForInstallation = true,
        )
        writeZip(
            apk,
            "AndroidManifest.xml" to manifest(),
            "classes.dex" to byteArrayOf(1, 2, 3),
        )
        val staging = temporaryFolder.newFolder("staging")

        assertFailsWithIOException {
            archive.stageSelectedApks(staging)
        }
        assertTrue(staging.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun apkmSelectsDeviceAbiDensityAndLocaleSplits() {
        val apkm = temporaryFolder.newFile("demo.apkm")
        writeZip(
            apkm,
            "base.apk" to nestedApk(manifest()),
            "split_config.arm64_v8a.apk" to nestedApk(manifest(split = "config.arm64_v8a")),
            "split_config.x86.apk" to nestedApk(manifest(split = "config.x86")),
            "split_config.hdpi.apk" to nestedApk(manifest(split = "config.hdpi")),
            "split_config.xxhdpi.apk" to nestedApk(manifest(split = "config.xxhdpi")),
            "split_config.en.apk" to nestedApk(manifest(split = "config.en")),
            "split_config.fr.apk" to nestedApk(manifest(split = "config.fr")),
            "Android/obb/com.example.demo/main.42.com.example.demo.obb" to byteArrayOf(1, 2, 3),
        )

        val archive = AndroidPackageArchiveInspector.inspect(apkm, arm64Device)
        val selected = archive.selectedApks.map { it.manifest.splitName }

        assertEquals(AndroidPackageFormat.APKM, archive.format)
        assertEquals(4, archive.selectedApks.size)
        assertTrue(null in selected)
        assertTrue("config.arm64_v8a" in selected)
        assertTrue("config.xxhdpi" in selected)
        assertTrue("config.en" in selected)
        assertFalse("config.x86" in selected)
        assertFalse("config.fr" in selected)
        assertEquals(1, archive.obbEntries.size)
        assertEquals(ArchiveInstallationState.READY_WITH_UNDEPLOYED_OBB, archive.installationState)
    }

    @Test
    fun bundletoolApksInspectsOnlyTocSelectedComponentsBeyondGenericLimit() {
        val apks = temporaryFolder.newFile("large.apks")
        ZipOutputStream(FileOutputStream(apks)).use { zip ->
            zip.putNextEntry(ZipEntry("toc.pb"))
            zip.write(singleBaseToc("splits/base-master.apk"))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("splits/base-master.apk"))
            zip.write(nestedApk(manifest()))
            zip.closeEntry()

            repeat(513) { index ->
                zip.putNextEntry(ZipEntry("unused/variant-$index.apk"))
                zip.write(byteArrayOf(index.toByte()))
                zip.closeEntry()
            }
        }

        val archive = AndroidPackageArchiveInspector.inspect(apks, arm64Device)

        assertEquals(AndroidPackageSubtype.BUNDLETOOL_APKS, archive.subtype)
        assertEquals(514, archive.apkEntryCount)
        assertEquals(listOf("splits/base-master.apk"), archive.selectedApks.map { it.archivePath })
        assertTrue(archive.canInstall)
    }

    @Test
    fun incompatibleAbiBlocksInstallation() {
        val apks = temporaryFolder.newFile("demo.apks")
        writeZip(
            apks,
            "base.apk" to nestedApk(manifest()),
            "split_config.x86.apk" to nestedApk(manifest(split = "config.x86")),
        )

        val archive = AndroidPackageArchiveInspector.inspect(apks, arm64Device)

        assertFalse(archive.canInstall)
        assertTrue(archive.problems.any { it.code == ArchiveProblemCode.INCOMPATIBLE_DEVICE })
    }

    @Test
    fun unsafeArchiveEntryIsRejected() {
        val xapk = temporaryFolder.newFile("unsafe.xapk")
        writeZip(xapk, "../base.apk" to nestedApk(manifest()))

        assertFailsWithIOException {
            AndroidPackageArchiveInspector.inspect(xapk, arm64Device)
        }
    }

    @Test
    fun caseDistinctApkEntriesAreAccepted() {
        val apk = temporaryFolder.newFile("case-distinct.apk")
        writeZip(
            apk,
            "AndroidManifest.xml" to manifest(),
            "assets/Example.txt" to byteArrayOf(1),
            "assets/example.txt" to byteArrayOf(2),
        )

        val archive = AndroidPackageArchiveInspector.inspect(apk, arm64Device)

        assertEquals(AndroidPackageFormat.APK, archive.format)
        assertTrue(archive.canInstall)
    }

    @Test
    fun manifestSummaryParsesSplitMetadataAndPermissions() {
        val summary = ManifestSummaryParser.parse(
            manifest(
                split = "feature.camera",
                configForSplit = "base",
                featureSplit = true,
                usesSplit = "feature.core",
            ).toString(Charsets.UTF_8),
        )

        assertEquals("feature.camera", summary.splitName)
        assertEquals("base", summary.configForSplit)
        assertTrue(summary.featureSplit)
        assertEquals(listOf("feature.core"), summary.usesSplits)
        assertEquals(listOf("android.permission.CAMERA"), summary.requestedPermissions)
    }

    private fun manifest(
        split: String? = null,
        configForSplit: String? = null,
        featureSplit: Boolean = false,
        usesSplit: String? = null,
    ): ByteArray {
        val splitAttributes = buildString {
            split?.let { append(""" split="$it"""") }
            configForSplit?.let { append(""" android:configForSplit="$it"""") }
            if (featureSplit) append(""" android:isFeatureSplit="true"""")
        }
        val usesSplitElement = usesSplit?.let { """<uses-split android:name="$it" />""" }.orEmpty()
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.example.demo"
                android:versionCode="42"
                android:versionName="1.2"$splitAttributes>
                <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="35" />
                <uses-permission android:name="android.permission.CAMERA" />
                $usesSplitElement
                <application android:label="Demo" />
            </manifest>
        """.trimIndent().toByteArray()
    }

    private fun nestedApk(manifest: ByteArray): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zip.write(manifest)
                zip.closeEntry()
            }
            output.toByteArray()
        }

    private fun singleBaseToc(basePath: String): ByteArray = ProtoWriter().apply {
        string(4, "com.example.demo")
        message(1) {
            message(2) {
                message(1) {
                    string(1, "base")
                    varint(6, 1)
                }
                message(2) {
                    string(2, basePath)
                    message(3) {
                        string(1, "base")
                        varint(2, 1)
                    }
                }
            }
        }
    }.toByteArray()

    private fun writeZip(file: File, vararg entries: Pair<String, ByteArray>) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun assertFailsWithIOException(block: () -> Unit) {
        try {
            block()
        } catch (_: IOException) {
            return
        }
        throw AssertionError("Expected IOException")
    }

    private class ProtoWriter {

        private val output = ByteArrayOutputStream()

        fun message(fieldNumber: Int, block: ProtoWriter.() -> Unit) {
            val bytes = ProtoWriter().apply(block).toByteArray()
            tag(fieldNumber, 2)
            rawVarint(bytes.size.toLong())
            output.write(bytes)
        }

        fun string(fieldNumber: Int, value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            tag(fieldNumber, 2)
            rawVarint(bytes.size.toLong())
            output.write(bytes)
        }

        fun varint(fieldNumber: Int, value: Int) {
            tag(fieldNumber, 0)
            rawVarint(value.toLong() and 0xFFFF_FFFFL)
        }

        private fun tag(fieldNumber: Int, wireType: Int) {
            rawVarint(((fieldNumber shl 3) or wireType).toLong())
        }

        private fun rawVarint(value: Long) {
            var remaining = value
            while (remaining and -0x80L != 0L) {
                output.write(((remaining and 0x7F) or 0x80).toInt())
                remaining = remaining ushr 7
            }
            output.write(remaining.toInt())
        }

        fun toByteArray(): ByteArray = output.toByteArray()
    }
}
