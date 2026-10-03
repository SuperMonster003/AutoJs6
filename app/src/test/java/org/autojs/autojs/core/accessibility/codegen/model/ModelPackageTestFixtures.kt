package org.autojs.autojs.core.accessibility.codegen.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ModelPackageTestFixtures {
    fun binary(): ByteArray {
        val data = ByteBuffer.allocate(28 + (1 + 6 + 8) + (1 + 12 + 16) + 4).order(ByteOrder.LITTLE_ENDIAN)
        data.put("AJCG".toByteArray(Charsets.US_ASCII)).putShort(1).putShort(FeatureSchema.names.size.toShort()).putInt(2).putDouble(0.25).putDouble(1.5)
        data.put(1).putShort(0).putFloat(0.5f).putFloat(-1f).putFloat(1f)
        data.put(2).putShort(1).putFloat(1.5f).putShort(2).putFloat(2.5f)
        floatArrayOf(0f, 0.25f, 0.5f, 0.75f).forEach(data::putFloat)
        data.putInt(CRC32().apply { update(data.array(), 0, data.position()) }.value.toInt())
        return data.array()
    }

    fun manifest(binary: ByteArray = binary()): JsonObject = JsonObject().apply {
        addProperty("format", 1)
        addProperty("modelId", "autojs6.codegen.tiny-test")
        add("displayName", JsonObject().apply { addProperty("en", "Test model") })
        addProperty("versionCode", 1)
        addProperty("versionName", "1.0.0")
        addProperty("kind", "ranker")
        addProperty("algorithm", "catboost-oblivious")
        addProperty("featureSchemaVersion", FeatureSchema.featureSchemaVersion)
        addProperty("featureNamesHash", FeatureSchema.namesHash)
        add("model", JsonObject().apply {
            addProperty("file", "model.bin")
            addProperty("size", binary.size)
            addProperty("sha256", MessageDigest.getInstance("SHA-256").digest(binary).joinToString("") { "%02x".format(it.toInt() and 0xff) })
            addProperty("trees", 2)
            addProperty("depth", 2)
            addProperty("features", FeatureSchema.names.size)
        })
        add("dataset", JsonObject().apply {
            addProperty("datasetId", "cg-fixture-test-v1")
            addProperty("groups", 3)
            addProperty("createdAt", "2026-10-03T00:00:00Z")
        })
        add("metrics", JsonObject().apply {
            add("test", JsonObject().apply {
                addProperty("top1Survival", 0.9)
                addProperty("ruleTop1Survival", 0.8)
                addProperty("regret", 0.1)
                addProperty("groups", 3)
            })
            add("realTest", JsonObject().apply {
                add("top1Survival", JsonNull.INSTANCE)
                add("ruleTop1Survival", JsonNull.INSTANCE)
                add("regret", JsonNull.INSTANCE)
                addProperty("groups", 0)
            })
        })
        add("training", JsonObject().apply {
            addProperty("catboostVersion", "1.2.8")
            addProperty("pythonVersion", "3.12.12")
            addProperty("hostCommit", "0".repeat(40))
            addProperty("trainedAt", "2026-10-03T00:00:00Z")
        })
        addProperty("minHostVersionCode", 1)
        addProperty("license", "MPL-2.0")
        addProperty("publisher", "AutoJs6")
    }

    fun features(): JsonObject = JsonObject().apply {
        addProperty("format", 1)
        addProperty("featureSchemaVersion", FeatureSchema.featureSchemaVersion)
        add("names", JsonArray().apply { FeatureSchema.names.forEach(::add) })
        addProperty("namesHash", FeatureSchema.namesHash)
    }

    fun archive(
        binary: ByteArray = binary(),
        mutateManifest: (JsonObject) -> Unit = {},
        mutateEntries: (LinkedHashMap<String, ByteArray>) -> Unit = {},
        stored: Boolean = false,
    ): ByteArray {
        val manifest = manifest(binary).also(mutateManifest)
        val entries = linkedMapOf(
            "manifest.json" to GsonBuilder().serializeNulls().create().toJson(manifest).toByteArray(Charsets.UTF_8),
            "model.bin" to binary,
            "features.json" to Gson().toJson(features()).toByteArray(Charsets.UTF_8),
            "MODEL_CARD.md" to "# Test model\nControlled fixture data only.\n".toByteArray(),
            "LICENSE" to "SPDX-License-Identifier: MPL-2.0\n".toByteArray(),
        ).also(mutateEntries)
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((name, bytes) in entries) {
                val entry = ZipEntry(name).apply {
                    time = 315532800000L
                    if (stored) {
                        method = ZipEntry.STORED
                        size = bytes.size.toLong()
                        compressedSize = size
                        crc = CRC32().apply { update(bytes) }.value
                    }
                }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
