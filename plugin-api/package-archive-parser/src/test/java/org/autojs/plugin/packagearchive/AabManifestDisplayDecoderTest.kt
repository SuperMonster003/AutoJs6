package org.autojs.plugin.packagearchive

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

class AabManifestDisplayDecoderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun aabWithManyResourcesDecodesBaseManifest() {
        val file = temporaryFolder.newFile("many-entries.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            repeat(18_184) { index ->
                zip.putNextEntry(ZipEntry("assets/entry_$index"))
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("base/manifest/AndroidManifest.xml"))
            zip.write(manifest("com.example.large"))
            zip.closeEntry()
        }
        assertTrue(AabManifestDisplayDecoder.decode(file).contains("com.example.large"))
    }

    @Test
    fun protobufManifestIsDecodedWithRawAndCompiledValues() {
        val decoded = AabManifestDisplayDecoder.decodeManifest(
            manifest(packageName = "com.example.app", split = "config.en"),
        )

        assertTrue(decoded.startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"))
        assertTrue(decoded.contains("xmlns:android=\"$ANDROID_NAMESPACE\""))
        assertTrue(decoded.contains("package=\"com.example.app\""))
        assertTrue(decoded.contains("split=\"config.en\""))
        assertTrue(decoded.contains("android:versionCode=\"42\""))
        assertTrue(decoded.contains("android:versionName=\"2.1\""))
        assertTrue(decoded.contains("<uses-sdk android:minSdkVersion=\"24\" />"))
        assertTrue(decoded.contains("android:debuggable=\"true\""))
        assertTrue(decoded.contains("android:label=\"Demo &amp; Co\""))
        assertTrue(decoded.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(decoded.endsWith("</manifest>"))
    }

    @Test
    fun aabPrefersBaseManifestWithoutExtractingIt() {
        val aab = createAab(
            "feature/manifest/AndroidManifest.xml" to manifest("com.example.feature"),
            "base/manifest/AndroidManifest.xml" to manifest("com.example.base"),
        )

        val decoded = AabManifestDisplayDecoder.decode(aab)

        assertTrue(decoded.contains("package=\"com.example.base\""))
        assertFalse(decoded.contains("package=\"com.example.feature\""))
    }

    @Test
    fun aabFallsBackToAnotherModuleManifest() {
        val aab = createAab(
            "feature/manifest/AndroidManifest.xml" to manifest("com.example.feature"),
        )

        val decoded = AabManifestDisplayDecoder.decode(aab)

        assertTrue(decoded.contains("package=\"com.example.feature\""))
    }

    @Test
    fun aabEntryBudgetIsEnforcedBeforeManifestReading() {
        val aab = createAab(
            "assets/ignored.txt" to byteArrayOf(1),
            "base/manifest/AndroidManifest.xml" to manifest("com.example.base"),
        )

        captureIOException {
            AabManifestDisplayDecoder.decode(
                aab,
                AabManifestDisplayDecoder.Limits(maxZipEntries = 1),
            )
        }
    }

    @Test
    fun malformedProtobufReportsAnIOException() {
        val failure = captureIOException {
            AabManifestDisplayDecoder.decodeManifest(
                byteArrayOf(
                    fieldTag(fieldNumber = 1, wireType = 2).toByte(),
                    0x80.toByte(),
                ),
            )
        }

        assertTrue(failure.message.orEmpty().contains("AAB manifest protobuf"))
    }

    @Test
    fun nodeAndStringBudgetsAreEnforced() {
        val bytes = manifest("com.example.app")
        val defaults = AabManifestDisplayDecoder.Limits()

        listOf(
            defaults.copy(maxInputBytes = bytes.size - 1),
            defaults.copy(maxFields = 3),
            defaults.copy(maxNodes = 1),
            defaults.copy(maxAttributes = 1),
            defaults.copy(maxAttributesPerElement = 1),
            defaults.copy(maxNamespaces = 0),
            defaults.copy(maxDepth = 1),
            defaults.copy(maxDecodedStringChars = 8),
            defaults.copy(maxSingleStringChars = 4),
            defaults.copy(maxXmlNameChars = 4),
            defaults.copy(maxOutputChars = 64),
        ).forEach { limits ->
            captureIOException {
                AabManifestDisplayDecoder.decodeManifest(bytes, limits)
            }
        }
    }

    @Test
    fun truncatedNestedMessageIsRejected() {
        val malformed = manifest("com.example.app").copyOf().also { bytes ->
            bytes[1] = 0x7F
        }

        captureIOException {
            AabManifestDisplayDecoder.decodeManifest(malformed)
        }
    }

    private fun manifest(
        packageName: String,
        split: String = "",
    ): ByteArray = ProtoWriter().apply {
        message(XML_NODE_ELEMENT_FIELD) {
            message(XML_ELEMENT_NAMESPACE_FIELD) {
                string(XML_NAMESPACE_PREFIX_FIELD, "android")
                string(XML_NAMESPACE_URI_FIELD, ANDROID_NAMESPACE)
            }
            string(XML_ELEMENT_NAME_FIELD, "manifest")
            message(XML_ELEMENT_ATTRIBUTE_FIELD) {
                string(XML_ATTRIBUTE_NAME_FIELD, "package")
                string(XML_ATTRIBUTE_VALUE_FIELD, packageName)
            }
            message(XML_ELEMENT_ATTRIBUTE_FIELD) {
                string(XML_ATTRIBUTE_NAMESPACE_URI_FIELD, ANDROID_NAMESPACE)
                string(XML_ATTRIBUTE_NAME_FIELD, "versionCode")
                varint(XML_ATTRIBUTE_RESOURCE_ID_FIELD, 0x0101021B)
                message(XML_ATTRIBUTE_COMPILED_ITEM_FIELD) {
                    message(ITEM_PRIMITIVE_FIELD) {
                        varint(PRIMITIVE_INT_DECIMAL_FIELD, 42)
                    }
                }
            }
            message(XML_ELEMENT_ATTRIBUTE_FIELD) {
                string(XML_ATTRIBUTE_NAMESPACE_URI_FIELD, ANDROID_NAMESPACE)
                string(XML_ATTRIBUTE_NAME_FIELD, "versionName")
                message(XML_ATTRIBUTE_COMPILED_ITEM_FIELD) {
                    message(ITEM_STRING_FIELD) {
                        string(WRAPPED_STRING_VALUE_FIELD, "2.1")
                    }
                }
            }
            if (split.isNotEmpty()) {
                message(XML_ELEMENT_ATTRIBUTE_FIELD) {
                    string(XML_ATTRIBUTE_NAME_FIELD, "split")
                    string(XML_ATTRIBUTE_VALUE_FIELD, split)
                }
            }
            message(XML_ELEMENT_CHILD_FIELD) {
                message(XML_NODE_ELEMENT_FIELD) {
                    string(XML_ELEMENT_NAME_FIELD, "uses-sdk")
                    message(XML_ELEMENT_ATTRIBUTE_FIELD) {
                        string(XML_ATTRIBUTE_NAMESPACE_URI_FIELD, ANDROID_NAMESPACE)
                        string(XML_ATTRIBUTE_NAME_FIELD, "minSdkVersion")
                        message(XML_ATTRIBUTE_COMPILED_ITEM_FIELD) {
                            message(ITEM_PRIMITIVE_FIELD) {
                                varint(PRIMITIVE_INT_DECIMAL_FIELD, 24)
                            }
                        }
                    }
                }
            }
            message(XML_ELEMENT_CHILD_FIELD) {
                message(XML_NODE_ELEMENT_FIELD) {
                    string(XML_ELEMENT_NAME_FIELD, "application")
                    message(XML_ELEMENT_ATTRIBUTE_FIELD) {
                        string(XML_ATTRIBUTE_NAMESPACE_URI_FIELD, ANDROID_NAMESPACE)
                        string(XML_ATTRIBUTE_NAME_FIELD, "debuggable")
                        message(XML_ATTRIBUTE_COMPILED_ITEM_FIELD) {
                            message(ITEM_PRIMITIVE_FIELD) {
                                varint(PRIMITIVE_BOOLEAN_FIELD, 1)
                            }
                        }
                    }
                    message(XML_ELEMENT_ATTRIBUTE_FIELD) {
                        string(XML_ATTRIBUTE_NAMESPACE_URI_FIELD, ANDROID_NAMESPACE)
                        string(XML_ATTRIBUTE_NAME_FIELD, "label")
                        message(XML_ATTRIBUTE_COMPILED_ITEM_FIELD) {
                            message(ITEM_RAW_STRING_FIELD) {
                                string(WRAPPED_STRING_VALUE_FIELD, "Demo & Co")
                            }
                        }
                    }
                    message(XML_ELEMENT_ATTRIBUTE_FIELD) {
                        string(XML_ATTRIBUTE_NAMESPACE_URI_FIELD, ANDROID_NAMESPACE)
                        string(XML_ATTRIBUTE_NAME_FIELD, "icon")
                        message(XML_ATTRIBUTE_COMPILED_ITEM_FIELD) {
                            message(ITEM_REFERENCE_FIELD) {
                                string(REFERENCE_NAME_FIELD, "mipmap/ic_launcher")
                            }
                        }
                    }
                }
            }
        }
    }.toByteArray()

    private fun createAab(vararg entries: Pair<String, ByteArray>): File {
        val aab = temporaryFolder.newFile("sample-${System.nanoTime()}.aab")
        ZipOutputStream(FileOutputStream(aab)).use { zip ->
            entries.forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents)
                zip.closeEntry()
            }
        }
        return aab
    }

    private fun captureIOException(block: () -> Unit): IOException {
        try {
            block()
            throw AssertionError("Expected IOException")
        } catch (exception: IOException) {
            return exception
        }
    }

    private class ProtoWriter {

        private val output = ByteArrayOutputStream()

        fun message(fieldNumber: Int, block: ProtoWriter.() -> Unit) {
            val nested = ProtoWriter().apply(block).toByteArray()
            tag(fieldNumber, WIRE_LENGTH_DELIMITED)
            rawVarint(nested.size.toLong())
            output.write(nested)
        }

        fun string(fieldNumber: Int, value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            tag(fieldNumber, WIRE_LENGTH_DELIMITED)
            rawVarint(bytes.size.toLong())
            output.write(bytes)
        }

        fun varint(fieldNumber: Int, value: Int) {
            tag(fieldNumber, WIRE_VARINT)
            rawVarint(value.toLong() and 0xFFFFFFFFL)
        }

        private fun tag(fieldNumber: Int, wireType: Int) {
            rawVarint(fieldTag(fieldNumber, wireType).toLong())
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

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val WIRE_VARINT = 0
        const val WIRE_LENGTH_DELIMITED = 2

        const val XML_NODE_ELEMENT_FIELD = 1
        const val XML_ELEMENT_NAMESPACE_FIELD = 1
        const val XML_ELEMENT_NAME_FIELD = 3
        const val XML_ELEMENT_ATTRIBUTE_FIELD = 4
        const val XML_ELEMENT_CHILD_FIELD = 5
        const val XML_NAMESPACE_PREFIX_FIELD = 1
        const val XML_NAMESPACE_URI_FIELD = 2
        const val XML_ATTRIBUTE_NAMESPACE_URI_FIELD = 1
        const val XML_ATTRIBUTE_NAME_FIELD = 2
        const val XML_ATTRIBUTE_VALUE_FIELD = 3
        const val XML_ATTRIBUTE_RESOURCE_ID_FIELD = 5
        const val XML_ATTRIBUTE_COMPILED_ITEM_FIELD = 6
        const val ITEM_REFERENCE_FIELD = 1
        const val ITEM_STRING_FIELD = 2
        const val ITEM_RAW_STRING_FIELD = 3
        const val ITEM_PRIMITIVE_FIELD = 7
        const val WRAPPED_STRING_VALUE_FIELD = 1
        const val REFERENCE_NAME_FIELD = 3
        const val PRIMITIVE_INT_DECIMAL_FIELD = 6
        const val PRIMITIVE_BOOLEAN_FIELD = 8

        fun fieldTag(fieldNumber: Int, wireType: Int): Int =
            (fieldNumber shl 3) or wireType
    }
}
