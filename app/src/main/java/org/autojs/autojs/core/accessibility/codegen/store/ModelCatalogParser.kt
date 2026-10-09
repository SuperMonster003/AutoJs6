package org.autojs.autojs.core.accessibility.codegen.store

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import org.autojs.autojs.core.accessibility.codegen.model.ModelMetricSummary
import org.autojs.autojs.core.accessibility.codegen.model.ModelMetrics
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogJson.array
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogJson.flag
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogJson.integer
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogJson.obj
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogJson.string
import java.net.URI
import java.time.OffsetDateTime
import java.util.Collections

/** Parses known wire fields; unknown fields are ignored without weakening validation of known ones.
 * zh-CN: 解析并严格校验已知字段; 忽略未知字段, 便于目录向前兼容. */
class ModelCatalogParser(
    private val hostVersionCode: Int,
    private val supportedSchemas: IntRange = FeatureSchema.SUPPORTED_SCHEMA,
    private val expectedNamesHash: (Int) -> String? = FeatureSchema::namesHashOrNull,
    private val expectedRepository: String = OFFICIAL_REPOSITORY,
) {
    private val gson = GsonBuilder().serializeNulls().create()
    fun parse(json: String): ModelCatalog = parse(json.toByteArray(Charsets.UTF_8))

    fun parse(bytes: ByteArray): ModelCatalog = try {
        val root = ModelCatalogJson.parse(bytes, MAX_CATALOG_BYTES)
        val schema = integer(root["schemaVersion"]).toInt()
        require(schema == 1) { "Unsupported model catalog schema" }
        val version = integer(root["catalogVersion"]).toInt()
        val tag = string(root["releaseTag"])
        require(tag == "codegen-models-v$version") { "Release tag differs from catalog version" }
        val generated = string(root["generatedAt"])
        val generatedTime = timestamp(generated)
        val repository = string(root["repository"])
        require(REPOSITORY.matches(repository) && repository == expectedRepository) { "Unexpected signed repository namespace" }
        val ids = HashSet<String>()
        val filenames = HashSet<String>()
        var totalVersions = 0
        val models = array(root["models"]).map { element ->
            val model = obj(element)
            val id = string(model["modelId"], 64)
            require(MODEL_ID.matches(id) && ids.add(id)) { "Invalid or duplicate modelId" }
            require(id != "autojs6.codegen.tiny-test" || !repository.equals(OFFICIAL_REPOSITORY, true)) { "Test model in production catalog" }
            val versions = array(model["versions"], 1).map { raw ->
                totalVersions++
                require(totalVersions <= MAX_HISTORY_ENTRIES) { "Catalog contains too many versions" }
                parseVersion(obj(raw), id, repository, version, generatedTime).also { require(filenames.add(it.artifact.fileName)) { "Duplicate artifact" } }
            }
            require(versions.map { it.versionCode } == versions.map { it.versionCode }.distinct().sorted()) { "Version codes must be unique and ascending" }
            require(model.has("recommendedVersionCode")) { "Missing recommendedVersionCode" }
            val recommended = model["recommendedVersionCode"].let { if (it.isJsonNull) null else integer(it).toInt() }
            require(recommended == versions.filter { !it.revoked && !it.deprecated }.maxOfOrNull { it.versionCode }) { "Recommendation must name the latest active version or null" }
            CatalogModel(id, translations(model["displayName"], 256), translations(model["summary"], 1024),
                recommended, Collections.unmodifiableList(versions))
        }
        require(models.map { it.modelId } == models.map { it.modelId }.sorted()) { "Models must use ascending modelId order" }
        ModelCatalog(schema, version, tag, generated, repository, Collections.unmodifiableList(models))
    } catch (error: ModelCatalogException) {
        throw error
    } catch (error: Exception) {
        throw ModelCatalogParseException("Invalid model catalog: ${error.message}", error)
    }

    private fun parseVersion(raw: JsonObject, modelId: String, repository: String, catalogVersion: Int, generatedAt: OffsetDateTime): CatalogModelVersion {
        val code = integer(raw["versionCode"]).toInt()
        val name = string(raw["versionName"], 256)
        val schema = integer(raw["featureSchemaVersion"]).toInt()
        val namesHash = string(raw["featureNamesHash"])
        require(Regex("[0-9a-f]{16}").matches(namesHash)) { "Invalid feature names hash" }
        val minHost = integer(raw["minHostVersionCode"]).toInt()
        val released = string(raw["releasedAt"])
        require(!timestamp(released).isAfter(generatedAt)) { "Release timestamp is later than catalog timestamp" }
        val dataset = string(raw["datasetId"])
        val license = string(obj(raw["license"])["spdx"], 256)
        require(!license.contains("LicenseRef-LocalEvaluation", ignoreCase = true)) { "Local evaluation packages are not distributable" }
        val metricsObject = obj(raw["metrics"])
        val metrics = ModelMetrics(metricSummary(obj(metricsObject["test"])), metricSummary(obj(metricsObject["realTest"])))
        val artifactObject = obj(raw["artifact"])
        val size = integer(artifactObject["size"], 1, MAX_MODEL_BYTES - 1)
        val digest = string(artifactObject["sha256"])
        require(SHA256.matches(digest)) { "Invalid package hash" }
        val filename = string(artifactObject["fileName"])
        require(filename == "autojs6-codegen-$modelId-v$code-${digest.take(8)}.ajcgm") { "Invalid content-addressed asset filename" }
        val primary = string(artifactObject["url"], 2048)
        val urls = array(artifactObject["urls"], 1, 16).map { string(it, 2048) }
        require(urls.distinct().size == urls.size && urls.first() == primary) { "Artifact URLs must be distinct and begin with url" }
        urls.forEach { validateUrl(it, repository, filename, catalogVersion) }
        val artifact = CatalogModelArtifact(filename, size, digest, primary, Collections.unmodifiableList(urls))
        val immutable = linkedMapOf<String, Any>(
            "versionCode" to code, "versionName" to name, "featureSchemaVersion" to schema, "featureNamesHash" to namesHash,
            "minHostVersionCode" to minHost, "artifact" to artifact, "releasedAt" to released, "datasetId" to dataset,
            "metrics" to metrics, "license" to license,
        )
        val fingerprint = ModelCatalogJson.digest(gson.toJson(immutable).toByteArray(Charsets.UTF_8))
        val compatibility = when {
            minHost > hostVersionCode -> ModelCatalogCompatibility.NEEDS_HOST_UPDATE
            schema !in supportedSchemas || namesHash != expectedNamesHash(schema) -> ModelCatalogCompatibility.INCOMPATIBLE
            else -> ModelCatalogCompatibility.COMPATIBLE
        }
        return CatalogModelVersion(code, name, schema, namesHash, minHost, artifact, released, dataset, metrics, license,
            flag(raw["deprecated"]), flag(raw["revoked"]), compatibility, fingerprint)
    }

    private fun metricSummary(raw: JsonObject): ModelMetricSummary {
        val groups = integer(raw["groups"], 0).toInt()
        fun score(name: String): Double? {
            val value = requireNotNull(raw[name]) { "Missing metric $name" }
            if (groups == 0) {
                require(value.isJsonNull) { "An empty metric bucket must contain null scores" }
                return null
            }
            require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "Metric must be numeric" }
            val score = value.asDouble.also { require(it.isFinite() && it in 0.0..1.0) { "Metric out of range" } }
            // Python's immutable metadata comparison treats +0.0 and -0.0 as equal.
            // Normalize before fingerprinting so signed catalogs keep that cross-platform rule.
            return if (score == 0.0) 0.0 else score
        }
        return ModelMetricSummary(score("top1Survival"), score("ruleTop1Survival"), score("regret"), groups)
    }

    private fun translations(element: JsonElement?, maxLength: Int): Map<String, String> {
        val raw = obj(element)
        require(raw.has("en") && raw.size() in 1..64) { "Translations require an English value" }
        return Collections.unmodifiableMap(raw.entrySet().sortedBy { it.key }.associate { (key, value) ->
            require(LANGUAGE.matches(key)) { "Invalid language tag" }
            key to string(value, maxLength)
        })
    }

    private fun validateUrl(value: String, repository: String, filename: String, catalogVersion: Int) {
        val uri = URI(value)
        require(uri.scheme == "https" && uri.rawAuthority == "github.com" && uri.rawQuery == null && uri.rawFragment == null) { "Artifact URL must use HTTPS GitHub Releases" }
        val prefix = "/$repository/releases/download/"
        require(uri.rawPath.startsWith(prefix)) { "Artifact URL has a different repository namespace" }
        val suffix = uri.rawPath.removePrefix(prefix).split('/')
        require(suffix.size == 2 && suffix[1] == filename) { "Artifact URL names a different file" }
        val tag = requireNotNull(RELEASE_TAG.matchEntire(suffix[0])) { "Invalid artifact release tag" }
        val version = requireNotNull(tag.groupValues[1].toIntOrNull()) { "Artifact release version is too large" }
        require(version in 1..catalogVersion) { "Artifact URL refers to a future release" }
    }

    companion object {
        const val OFFICIAL_REPOSITORY = "SuperMonster003/AutoJs6-Shared-Assets"
        const val MAX_CATALOG_BYTES = 8 * 1024 * 1024
        const val MAX_MODEL_BYTES = 100L * 1024 * 1024
        internal const val MAX_HISTORY_ENTRIES = 65536
        internal val SHA256 = Regex("[0-9a-f]{64}")
        internal val MODEL_ID = Regex("[a-z0-9][a-z0-9._-]{1,63}")
        private val REPOSITORY = Regex("[A-Za-z0-9][A-Za-z0-9_.-]*/[A-Za-z0-9][A-Za-z0-9_.-]*")
        private val RELEASE_TAG = Regex("codegen-models-v([1-9][0-9]*)")
        private val LANGUAGE = Regex("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*")

        internal fun timestamp(value: String): OffsetDateTime = OffsetDateTime.parse(value)
    }
}
