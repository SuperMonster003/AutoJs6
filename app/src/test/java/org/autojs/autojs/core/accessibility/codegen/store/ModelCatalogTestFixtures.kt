package org.autojs.autojs.core.accessibility.codegen.store

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import org.autojs.autojs.ui.edit.editor.ace.font.FontCatalogSignatureVerifier
import java.util.Base64

/** Structural fixtures use a deterministic injected verifier; the separate interop test uses JCA. */
object ModelCatalogTestFixtures {
    const val MODEL_ID = "autojs6.codegen.ranker"
    const val REPOSITORY = ModelCatalogParser.OFFICIAL_REPOSITORY

    /** The bundled tiny package was trained on feature schema 1; catalog versions describe that package.
     * zh-CN: 内置 tiny 模型包按特征 schema 1 训练, 目录版本条目描述的就是该包. */
    const val PACKAGE_SCHEMA = 1
    const val PACKAGE_NAMES_HASH = "9c6a5d8279ffca0e"

    fun version(code: Int = 1, digest: String = "a".repeat(64), revoked: Boolean = false, deprecated: Boolean = false): JsonObject = JsonObject().apply {
        addProperty("versionCode", code)
        addProperty("versionName", "$code.0.0")
        addProperty("featureSchemaVersion", PACKAGE_SCHEMA)
        addProperty("featureNamesHash", PACKAGE_NAMES_HASH)
        addProperty("minHostVersionCode", 100)
        val filename = "autojs6-codegen-$MODEL_ID-v$code-${digest.take(8)}.ajcgm"
        val url = "https://github.com/$REPOSITORY/releases/download/codegen-models-v1/$filename"
        add("artifact", JsonObject().apply {
            addProperty("fileName", filename)
            addProperty("size", 4096)
            addProperty("sha256", digest)
            addProperty("url", url)
            add("urls", JsonArray().apply { add(url) })
        })
        addProperty("releasedAt", "2026-10-03T00:00:00Z")
        addProperty("datasetId", "test-fixture")
        add("metrics", JsonObject().apply {
            add("test", JsonObject().apply {
                addProperty("groups", 150)
                addProperty("top1Survival", .9)
                addProperty("ruleTop1Survival", .8)
                addProperty("regret", .1)
            })
            add("realTest", JsonObject().apply {
                addProperty("groups", 0)
                add("top1Survival", JsonNull.INSTANCE)
                add("ruleTop1Survival", JsonNull.INSTANCE)
                add("regret", JsonNull.INSTANCE)
            })
        })
        add("license", JsonObject().apply { addProperty("spdx", "MPL-2.0") })
        if (revoked) addProperty("revoked", true)
        if (deprecated) addProperty("deprecated", true)
    }

    fun catalog(catalogVersion: Int = 1, versions: List<JsonObject> = listOf(version())): JsonObject = JsonObject().apply {
        addProperty("schemaVersion", 1)
        addProperty("catalogVersion", catalogVersion)
        addProperty("releaseTag", "codegen-models-v$catalogVersion")
        addProperty("generatedAt", "2026-10-03T01:00:00Z")
        addProperty("repository", REPOSITORY)
        add("models", JsonArray().apply {
            if (versions.isNotEmpty()) add(JsonObject().apply {
                addProperty("modelId", MODEL_ID)
                add("displayName", JsonObject().apply { addProperty("en", "Model"); addProperty("zh-CN", "模型") })
                add("summary", JsonObject().apply { addProperty("en", "Test catalog") })
                val latest = versions.filter { it["revoked"]?.asBoolean != true && it["deprecated"]?.asBoolean != true }.maxOfOrNull { it["versionCode"].asInt }
                if (latest == null) add("recommendedVersionCode", JsonNull.INSTANCE) else addProperty("recommendedVersionCode", latest)
                add("versions", JsonArray().apply { versions.forEach(::add) })
            })
        })
    }

    fun bytes(value: JsonObject): ByteArray = value.toString().toByteArray(Charsets.UTF_8)

    fun signature(raw: ByteArray): ByteArray = bytes(JsonObject().apply {
        addProperty("algorithm", "ECDSA_P256_SHA256")
        addProperty("catalog", "catalog-v1.json")
        addProperty("keyId", "test-only")
        addProperty("signature", ModelCatalogJson.digest(raw))
    })

    val verifier = FontCatalogSignatureVerifier { raw, signature ->
        check(JsonParser.parseString(signature.toString(Charsets.UTF_8)).asJsonObject["signature"].asString == ModelCatalogJson.digest(raw)) { "Fixture signature mismatch" }
    }

    fun bootstrap(raw: ByteArray): ByteArray = bytes(JsonObject().apply {
        addProperty("format", 1)
        addProperty("catalogBase64", Base64.getEncoder().encodeToString(raw))
        add("signature", JsonParser.parseString(signature(raw).toString(Charsets.UTF_8)))
    })
}
