package org.autojs.autojs.core.accessibility.codegen.model

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackageRejectionReason.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.CRC32
import kotlin.random.Random

class ModelPackageReaderTest {
    private val reader = ModelPackageReader()
    private fun read(bytes: ByteArray) = reader.read(ByteArrayInputStream(bytes))
    private fun rejected(bytes: ByteArray, reason: ModelPackageRejectionReason? = null) {
        val result = read(bytes)
        assertTrue("Expected rejection, got $result", result is Rejected)
        if (reason != null) assertEquals((result as Rejected).detail, reason, result.reason)
    }
    private fun manifest(change: (JsonObject) -> Unit) = ModelPackageTestFixtures.archive(mutateManifest = change)
    private fun textEntry(name: String, value: String) = ModelPackageTestFixtures.archive(mutateEntries = { it[name] = value.toByteArray(Charsets.UTF_8) })
    private fun buffer(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun u16(bytes: ByteArray, at: Int) = buffer(bytes).getShort(at).toInt() and 0xffff
    private fun u32(bytes: ByteArray, at: Int) = buffer(bytes).getInt(at).toLong() and 0xffffffffL
    private fun central(bytes: ByteArray): List<Int> {
        val end = bytes.size - 22
        var at = u32(bytes, end + 16).toInt()
        return List(u16(bytes, end + 10)) {
            val offset = at
            at += 46 + u16(bytes, at + 28) + u16(bytes, at + 30) + u16(bytes, at + 32)
            offset
        }
    }
    private fun central(bytes: ByteArray, name: String) = central(bytes).single {
        bytes.copyOfRange(it + 46, it + 46 + u16(bytes, it + 28)).toString(Charsets.US_ASCII) == name
    }
    private fun body(bytes: ByteArray, name: String): Int {
        val local = u32(bytes, central(bytes, name) + 42).toInt()
        return local + 30 + u16(bytes, local + 26) + u16(bytes, local + 28)
    }
    private fun binary(change: (ByteBuffer) -> Unit): ByteArray {
        val bytes = ModelPackageTestFixtures.binary()
        change(buffer(bytes))
        buffer(bytes).putInt(bytes.size - 4, CRC32().apply { update(bytes, 0, bytes.size - 4) }.value.toInt())
        return ModelPackageTestFixtures.archive(binary = bytes)
    }

    @Test
    fun validStoredAndDeflatedPackagesLoadFromFileAndCallerOwnedStream() {
        for (stored in listOf(false, true)) {
            val bytes = ModelPackageTestFixtures.archive(stored = stored)
            val stream = object : ByteArrayInputStream(bytes) {
                var closed = false
                override fun close() { closed = true; super.close() }
            }
            val result = reader.read(stream)
            assertTrue(result.toString(), result is ModelPackage)
            val loaded = result as ModelPackage
            assertEquals("autojs6.codegen.tiny-test", loaded.manifest.modelId)
            assertEquals(FeatureSchema.names, loaded.features.names)
            assertEquals(2, loaded.model.treeCount)
            assertEquals(2, loaded.model.maxDepth)
            assertEquals(FeatureSchema.names.size, loaded.model.featureCount)
            assertEquals(-1.25, loaded.model.score(FloatArray(FeatureSchema.names.size)), 0.0)
            assertFalse(stream.closed)
            val path = Files.createTempFile("codegen-reader-test-", ".ajcgm")
            try {
                Files.write(path, bytes)
                assertTrue(reader.read(path.toFile()) is ModelPackage)
            } finally { Files.deleteIfExists(path) }
        }
    }

    @Test
    fun callerSuppliedTemporaryDirectoryIsUsedAndCleanedOnSuccessOrFailure() {
        val directory = Files.createTempDirectory("codegen-reader-cache-")
        val source = Files.createTempFile("codegen-reader-source-", ".ajcgm")
        try {
            val explicit = ModelPackageReader(temporaryDirectory = directory.toFile())
            val bytes = ModelPackageTestFixtures.archive()
            Files.write(source, bytes)
            assertTrue(explicit.read(source.toFile()) is ModelPackage)
            assertEquals(0, directory.toFile().listFiles()!!.size)
            assertTrue(explicit.read(bytes.inputStream()) is ModelPackage)
            assertEquals(0, directory.toFile().listFiles()!!.size)
            assertTrue(explicit.read(byteArrayOf(1, 2, 3).inputStream()) is Rejected)
            assertEquals(0, directory.toFile().listFiles()!!.size)
            val failed = object : InputStream() { override fun read(): Int = throw IOException("test failure") }
            assertEquals(IO_ERROR, (explicit.read(failed) as Rejected).reason)
            assertEquals(0, directory.toFile().listFiles()!!.size)
            val absent = directory.resolve("not-created").toFile()
            assertEquals(IO_ERROR, (ModelPackageReader(temporaryDirectory = absent).read(bytes.inputStream()) as Rejected).reason)
            assertFalse(absent.exists())
            assertEquals(IO_ERROR, (ModelPackageReader(temporaryDirectory = source.toFile()).read(bytes.inputStream()) as Rejected).reason)
        } finally {
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun everyEntryIsRequiredAndUnknownPathsAreRejected() {
        for (name in listOf("manifest.json", "model.bin", "features.json", "MODEL_CARD.md", "LICENSE")) {
            rejected(ModelPackageTestFixtures.archive(mutateEntries = { it.remove(name) }), MISSING_ENTRY)
        }
        for (name in listOf("../model.bin", "/model.bin", "folder/", "extra.bin", "MODEL_CARD.md/", "C:\\model.bin")) {
            rejected(ModelPackageTestFixtures.archive(mutateEntries = { it[name] = it.remove("MODEL_CARD.md")!! }), INVALID_ENTRY)
        }
    }

    @Test
    fun duplicateNamesAndDirectoriesCannotHideBehindValidNames() {
        val duplicate = ModelPackageTestFixtures.archive()
        val feature = central(duplicate, "features.json")
        val local = u32(duplicate, feature + 42).toInt()
        "manifest.json".toByteArray().copyInto(duplicate, feature + 46)
        "manifest.json".toByteArray().copyInto(duplicate, local + 30)
        rejected(duplicate, DUPLICATE_ENTRY)
        val directory = ModelPackageTestFixtures.archive()
        buffer(directory).putInt(central(directory, "LICENSE") + 38, 0x10)
        rejected(directory, INVALID_ENTRY)
        val symlink = ModelPackageTestFixtures.archive()
        buffer(symlink).putInt(central(symlink, "LICENSE") + 38, 0xa0000000.toInt())
        rejected(symlink, INVALID_ENTRY)
    }

    @Test
    fun encryptedUnsupportedZip64AndMultiDiskArchivesAreRejected() {
        val encrypted = ModelPackageTestFixtures.archive()
        val at = central(encrypted).first()
        buffer(encrypted).putShort(at + 8, (u16(encrypted, at + 8) or 1).toShort())
        rejected(encrypted, UNSUPPORTED_ZIP)
        val method = ModelPackageTestFixtures.archive()
        buffer(method).putShort(central(method).first() + 10, 99)
        rejected(method, UNSUPPORTED_ZIP)
        val zip64 = ModelPackageTestFixtures.archive()
        buffer(zip64).putInt(central(zip64).first() + 20, -1)
        rejected(zip64, UNSUPPORTED_ZIP)
        val split = ModelPackageTestFixtures.archive()
        buffer(split).putShort(split.size - 22 + 4, 1)
        rejected(split, UNSUPPORTED_ZIP)
    }

    @Test
    fun truncatedAppendedPrependedAndInconsistentDirectoryDataAreRejected() {
        val valid = ModelPackageTestFixtures.archive()
        for (length in listOf(0, 1, 22, valid.size / 2, valid.size - 1, valid.size - 22)) rejected(valid.copyOf(length), INVALID_ZIP)
        rejected(valid + byteArrayOf(1, 2, 3), INVALID_ZIP)
        rejected(byteArrayOf(1, 2, 3) + valid, INVALID_ZIP)
        val count = valid.clone()
        buffer(count).putShort(count.size - 22 + 8, 4)
        rejected(count, UNSUPPORTED_ZIP)
        val offset = valid.clone()
        buffer(offset).putInt(offset.size - 22 + 16, u32(offset, offset.size - 22 + 16).toInt() + 1)
        rejected(offset, INVALID_ZIP)
        val local = valid.clone()
        local[30] = 'x'.code.toByte()
        rejected(local, INVALID_ZIP)
        val overlap = valid.clone()
        buffer(overlap).putInt(central(overlap)[1] + 42, 0)
        rejected(overlap, INVALID_ZIP)
    }

    @Test
    fun zipCrcAndDescriptorMustMatchTheActuallyReadBytes() {
        val stored = ModelPackageTestFixtures.archive(stored = true)
        stored[body(stored, "LICENSE")] = 'A'.code.toByte()
        rejected(stored, ZIP_CRC_MISMATCH)
        val descriptor = ModelPackageTestFixtures.archive()
        val entry = central(descriptor, "LICENSE")
        val descriptorOffset = body(descriptor, "LICENSE") + u32(descriptor, entry + 20).toInt()
        descriptor[descriptorOffset + 4] = (descriptor[descriptorOffset + 4].toInt() xor 1).toByte()
        rejected(descriptor, INVALID_ZIP)
    }

    @Test
    fun perEntryLimitsAndCompressionRatioAreCheckedBeforeAllocation() {
        for ((name, limit) in listOf("model.bin" to ModelPackageReader.MAX_MODEL_BYTES, "manifest.json" to 1024 * 1024, "features.json" to 256 * 1024, "MODEL_CARD.md" to 1024 * 1024, "LICENSE" to 1024 * 1024)) {
            val oversized = ModelPackageTestFixtures.archive()
            buffer(oversized).putInt(central(oversized, name) + 24, limit + 1)
            rejected(oversized, ENTRY_TOO_LARGE)
        }
        rejected(textEntry("MODEL_CARD.md", "A".repeat(250_000)), COMPRESSION_RATIO)
    }

    @Test
    fun dishonestDeclaredSizeCannotBypassStreamingEntryLimit() {
        val random = Random(73)
        val oversized = ModelPackageTestFixtures.archive(mutateEntries = {
            it["MODEL_CARD.md"] = ByteArray(1024 * 1024 + 1) { random.nextInt(32, 127).toByte() }
        })
        val entry = central(oversized, "MODEL_CARD.md")
        val descriptor = body(oversized, "MODEL_CARD.md") + u32(oversized, entry + 20).toInt()
        buffer(oversized).putInt(entry + 24, 1)
        buffer(oversized).putInt(descriptor + 12, 1)
        rejected(oversized, ENTRY_TOO_LARGE)
    }

    @Test
    fun packageInputLimitIsEnforcedWithoutAllocatingA100MiBArray() {
        val input = object : InputStream() {
            var remaining = ModelPackageReader.MAX_PACKAGE_BYTES + 1
            override fun read(): Int = if (remaining-- > 0) 0 else -1
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (remaining <= 0) return -1
                val size = minOf(length.toLong(), remaining).toInt()
                bytes.fill(0, offset, offset + size)
                remaining -= size
                return size
            }
        }
        val result = reader.read(input)
        assertTrue(result is Rejected)
        assertEquals(PACKAGE_TOO_LARGE, (result as Rejected).reason)
        // The five tighter entry limits imply a total far below 100 MiB as well.
        assertTrue(ModelPackageReader.MAX_MODEL_BYTES + 3L * 1024 * 1024 + 256 * 1024 < ModelPackageReader.MAX_PACKAGE_BYTES)
    }

    @Test
    fun strictJsonRejectsDuplicateKeysIncludingNestedAndEscapedAliases() {
        val original = ModelPackageTestFixtures.manifest().toString()
        for (json in listOf(
            original.replace("\"format\":1", "\"format\":1,\"format\":1"),
            original.replace("\"format\":1", "\"format\":1,\"\\u0066ormat\":1"),
            original.replace("\"trees\":2", "\"trees\":2,\"trees\":2"),
        )) rejected(textEntry("manifest.json", json), INVALID_JSON)
        val features = ModelPackageTestFixtures.features().toString().replace("\"format\":1", "\"format\":1,\"format\":1")
        rejected(textEntry("features.json", features), INVALID_JSON)
    }

    @Test
    fun strictJsonRejectsInvalidUtf8SurrogatesNestingNonJsonAndTrailingContent() {
        for (json in listOf("[]", "{}{}", "\uFEFF{}", "{\"x\":NaN}", "{\"x\":Infinity}", "{\"x\":01}", "{/*comment*/}", "{\"x\":\"\\ud800\"}", "{\"x\":" + "[".repeat(34) + "0" + "]".repeat(34) + "}", "{\"x\":\"" + "x".repeat(1025) + "\"}")) {
            rejected(textEntry("manifest.json", json), INVALID_JSON)
        }
        rejected(ModelPackageTestFixtures.archive(mutateEntries = { it["manifest.json"] = byteArrayOf(0xc3.toByte(), 0x28) }), INVALID_JSON)
        rejected(ModelPackageTestFixtures.archive(mutateEntries = { it["LICENSE"] = byteArrayOf(0xc3.toByte(), 0x28) }), INVALID_ZIP)
        rejected(textEntry("LICENSE", " \n\t"), INVALID_ENTRY)
    }

    @Test
    fun everyManifestFieldIsRequiredAndUnknownFieldsFailClosed() {
        for (key in ModelPackageTestFixtures.manifest().keySet()) rejected(manifest { it.remove(key) }, INVALID_MANIFEST)
        for ((section, keys) in listOf("model" to listOf("size", "sha256", "trees", "depth", "features"), "dataset" to listOf("datasetId", "groups", "createdAt"), "training" to listOf("catboostVersion", "pythonVersion", "hostCommit", "trainedAt"))) {
            for (key in keys) rejected(manifest { it.getAsJsonObject(section).remove(key) }, INVALID_MANIFEST)
        }
        rejected(manifest { it.addProperty("unknown", true) }, INVALID_MANIFEST)
    }

    @Test
    fun manifestIdsKindsFormatsNamesAndAllNumericBoundsAreValidated() {
        for (id in listOf("A", "a", "../model", "x".repeat(65))) rejected(manifest { it.addProperty("modelId", id) }, INVALID_MANIFEST)
        rejected(manifest { it.addProperty("format", 2) }, INVALID_MANIFEST)
        rejected(manifest { it.addProperty("kind", "generator") }, INVALID_MANIFEST)
        rejected(manifest { it.addProperty("algorithm", "other") }, INVALID_MANIFEST)
        rejected(manifest { it.getAsJsonObject("displayName").remove("en") }, INVALID_MANIFEST)
        rejected(manifest { it.addProperty("publisher", "x".repeat(257)) }, INVALID_MANIFEST)
        for (key in listOf("versionCode", "minHostVersionCode")) {
            for (number in listOf(0L, -1L, Int.MAX_VALUE.toLong() + 1)) rejected(manifest { it.addProperty(key, number) }, INVALID_MANIFEST)
            rejected(manifest { it.addProperty(key, "1") }, INVALID_MANIFEST)
            rejected(manifest { it.addProperty(key, 1.5) }, INVALID_MANIFEST)
        }
        for (number in listOf("1.0", "1e0")) {
            rejected(textEntry("manifest.json", ModelPackageTestFixtures.manifest().toString().replace("\"versionCode\":1", "\"versionCode\":$number")), INVALID_MANIFEST)
        }
        for ((key, number) in listOf("size" to 31, "size" to ModelPackageReader.MAX_MODEL_BYTES + 1, "trees" to 0, "trees" to 4097, "depth" to 0, "depth" to 9, "features" to 0, "features" to 65536)) {
            rejected(manifest { it.getAsJsonObject("model").addProperty(key, number) }, INVALID_MANIFEST)
        }
        rejected(manifest { it.getAsJsonObject("model").addProperty("file", "other.bin") }, INVALID_MANIFEST)
        rejected(manifest { it.getAsJsonObject("model").addProperty("sha256", "ABC") }, INVALID_MANIFEST)
        rejected(manifest { it.getAsJsonObject("dataset").addProperty("groups", 0) }, INVALID_MANIFEST)
        rejected(manifest { it.getAsJsonObject("dataset").addProperty("createdAt", "yesterday") }, INVALID_MANIFEST)
        rejected(manifest { it.getAsJsonObject("training").addProperty("hostCommit", "abc") }, INVALID_MANIFEST)
    }

    @Test
    fun emptyAndNonemptyMetricSplitsHaveUnambiguousFiniteValues() {
        rejected(manifest { it.getAsJsonObject("metrics").getAsJsonObject("test").addProperty("regret", -0.1) }, INVALID_MANIFEST)
        rejected(manifest { it.getAsJsonObject("metrics").getAsJsonObject("test").addProperty("top1Survival", 1.1) }, INVALID_MANIFEST)
        rejected(manifest { it.getAsJsonObject("metrics").getAsJsonObject("test").add("top1Survival", JsonNull.INSTANCE) }, INVALID_MANIFEST)
        rejected(manifest { it.getAsJsonObject("metrics").getAsJsonObject("realTest").addProperty("top1Survival", 0) }, INVALID_MANIFEST)
        rejected(manifest { it.getAsJsonObject("metrics").getAsJsonObject("test").addProperty("groups", -1) }, INVALID_MANIFEST)
        rejected(textEntry("manifest.json", ModelPackageTestFixtures.manifest().toString().replace("\"top1Survival\":0.9", "\"top1Survival\":1e999")), INVALID_MANIFEST)
    }

    @Test
    fun schemaNamesHashFeatureOrderAndHostCompatibilityAreChecked() {
        rejected(manifest { it.addProperty("featureSchemaVersion", 3) }, INCOMPATIBLE_SCHEMA)
        rejected(manifest { it.addProperty("featureNamesHash", "0".repeat(16)) }, FEATURE_MISMATCH)
        rejected(manifest { it.getAsJsonObject("model").addProperty("features", FeatureSchema.names.size - 1) }, FEATURE_MISMATCH)
        val features = ModelPackageTestFixtures.features()
        features.getAsJsonArray("names").set(0, features.getAsJsonArray("names")[1])
        rejected(textEntry("features.json", features.toString()), FEATURE_MISMATCH)
        features.addProperty("featureSchemaVersion", 3)
        rejected(textEntry("features.json", features.toString()), INCOMPATIBLE_SCHEMA)
        val result = ModelPackageReader(1).read(manifest { it.addProperty("minHostVersionCode", 2) }.inputStream())
        assertEquals(HOST_TOO_OLD, (result as Rejected).reason)
    }

    @Test
    fun binaryDigestLengthTreeCountAndDepthMustAgreeWithManifest() {
        rejected(manifest { it.getAsJsonObject("model").addProperty("sha256", "0".repeat(64)) }, MODEL_HASH_MISMATCH)
        rejected(manifest { it.getAsJsonObject("model").addProperty("size", ModelPackageTestFixtures.binary().size + 1) }, MODEL_METADATA_MISMATCH)
        rejected(manifest { it.getAsJsonObject("model").addProperty("trees", 3) }, MODEL_METADATA_MISMATCH)
        rejected(manifest { it.getAsJsonObject("model").addProperty("depth", 3) }, MODEL_METADATA_MISMATCH)
        val badCrc = ModelPackageTestFixtures.binary().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        rejected(ModelPackageTestFixtures.archive(binary = badCrc), MODEL_CRC_MISMATCH)
    }

    @Test
    fun binaryMagicVersionCountsSplitIndicesAndEveryFloatMustBeValid() {
        rejected(binary { it.putInt(0, 0) }, INVALID_MODEL)
        rejected(binary { it.putShort(4, 2) }, INVALID_MODEL)
        rejected(binary { it.putShort(6, 63) }, MODEL_METADATA_MISMATCH)
        rejected(binary { it.putInt(8, 0) }, MODEL_METADATA_MISMATCH)
        rejected(binary { it.putInt(8, 4097) }, MODEL_METADATA_MISMATCH)
        for (depth in listOf(0, 9)) rejected(binary { it.put(28, depth.toByte()) }, INVALID_MODEL)
        rejected(binary { it.putShort(29, FeatureSchema.names.size.toShort()) }, INVALID_MODEL)
        for (number in listOf(Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)) {
            rejected(binary { it.putFloat(31, number) }, INVALID_MODEL)
            rejected(binary { it.putFloat(35, number) }, INVALID_MODEL)
        }
        for (number in listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)) {
            rejected(binary { it.putDouble(12, number) }, INVALID_MODEL)
            rejected(binary { it.putDouble(20, number) }, INVALID_MODEL)
        }
    }

    @Test
    fun binaryBodyLengthMustMatchExactlyEvenWithRecomputedCrc() {
        val original = ModelPackageTestFixtures.binary()
        for (size in listOf(original.size - 1, original.size + 1)) {
            val bytes = original.copyOf(size)
            buffer(bytes).putInt(bytes.size - 4, CRC32().apply { update(bytes, 0, bytes.size - 4) }.value.toInt())
            rejected(ModelPackageTestFixtures.archive(binary = bytes), INVALID_MODEL)
        }
    }

    @Test
    fun ioFailuresAndTwoThousandSeededMutationsNeverEscapeAsExceptions() {
        val failedInput = object : InputStream() { override fun read(): Int = throw IOException("test read failure") }
        assertEquals(IO_ERROR, (reader.read(failedInput) as Rejected).reason)
        assertEquals(IO_ERROR, (reader.read(File("missing-codegen-model-${System.nanoTime()}")) as Rejected).reason)
        val good = ModelPackageTestFixtures.archive()
        val random = Random(1402)
        repeat(2000) { round ->
            val bytes = when (round % 3) {
                0 -> good.clone().also { val at = random.nextInt(it.size); it[at] = (it[at].toInt() xor (1 shl random.nextInt(8))).toByte() }
                1 -> good.copyOf(random.nextInt(good.size))
                else -> good + random.nextBytes(random.nextInt(1, 33))
            }
            val result = read(bytes)
            assertTrue("Mutation $round escaped the result contract", result is ModelPackage || result is Rejected)
        }
    }
}
