package org.autojs.autojs.ui.edit.editor.ace.font

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

class FontCatalogParser(
    private val hostVersionCode: Int,
    private val supportedSchemaVersion: Int = SCHEMA_VERSION,
    private val maxFontBytes: Long = FontFileVerifier.MAX_FONT_BYTES,
    private val reservedRemoteIds: Set<String> = AceFontIds.reservedRemoteIds,
) {

    @Throws(FontCatalogException::class)
    fun parse(bytes: ByteArray): FontCatalog {
        val json = try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: Exception) {
            throw FontCatalogParseException("Font catalog is not valid UTF-8", e)
        }
        return parse(json)
    }

    @Throws(FontCatalogException::class)
    fun parse(json: String): FontCatalog {
        val root = try {
            JsonParser.parseString(json).asObject("catalog")
        } catch (e: FontCatalogException) {
            throw e
        } catch (e: Exception) {
            throw FontCatalogParseException("Malformed font catalog JSON", e)
        }

        val schemaVersion = root.requiredInt("schemaVersion", "catalog")
        if (schemaVersion != supportedSchemaVersion) {
            throw FontCatalogCompatibilityException(
                "Unsupported font catalog schema $schemaVersion (expected $supportedSchemaVersion)",
            )
        }
        val catalogVersion = root.requiredLong("catalogVersion", "catalog")
        if (catalogVersion < 1L) {
            throw FontCatalogParseException("catalog.catalogVersion must be positive")
        }
        val minHostVersionCode = root.requiredInt("minHostVersionCode", "catalog")
        if (minHostVersionCode < 1) {
            throw FontCatalogParseException("catalog.minHostVersionCode must be positive")
        }
        if (minHostVersionCode > hostVersionCode) {
            throw FontCatalogCompatibilityException(
                "Font catalog requires host version $minHostVersionCode; current version is $hostVersionCode",
            )
        }

        val revoked = parseRevocations(root)
        val fontArray = root.requiredArray("fonts", "catalog")
        val seenIds = hashSetOf<String>()
        val fonts = fontArray.mapIndexed { index, element ->
            parseFont(element.asObject("catalog.fonts[$index]"), index, revoked).also { font ->
                if (!seenIds.add(font.id)) {
                    throw FontCatalogParseException("Duplicate font id '${font.id}'")
                }
            }
        }.sortedWith(compareBy<RemoteFont> { it.order }.thenBy { it.displayName }.thenBy { it.id })

        return FontCatalog(
            schemaVersion = schemaVersion,
            catalogVersion = catalogVersion,
            minHostVersionCode = minHostVersionCode,
            releaseTag = root.optionalString("releaseTag", "catalog"),
            generatedAt = root.optionalString("generatedAt", "catalog"),
            revokedSha256 = revoked,
            fonts = fonts,
        )
    }

    private fun parseFont(obj: JsonObject, index: Int, revoked: Set<String>): RemoteFont {
        val path = "catalog.fonts[$index]"
        val id = obj.requiredString("id", path)
        if (!AceFontIds.validId.matches(id)) {
            throw FontCatalogParseException("$path.id is not a valid stable font id")
        }
        if (id in reservedRemoteIds) {
            throw FontCatalogParseException("$path.id '$id' is reserved by the host application")
        }

        val license = parseLicense(obj.get("license"), "$path.license")
        val source = parseSource(obj.get("source"), "$path.source")
        val artifact = parseArtifact(obj.requiredObject("artifact", path), "$path.artifact", revoked)
        return RemoteFont(
            id = id,
            displayName = obj.requiredString("displayName", path),
            family = obj.requiredString("family", path),
            order = obj.optionalInt("order", path) ?: obj.optionalInt("sortOrder", path) ?: index,
            author = obj.requiredString("author", path),
            license = license,
            source = source,
            artifact = artifact,
            features = parseFeatures(obj, path),
        )
    }

    private fun parseFeatures(obj: JsonObject, path: String): Set<FontFeature> {
        val values = obj.optionalArray("features", path) ?: return emptySet()
        val parsed = linkedSetOf<FontFeature>()
        values.forEachIndexed { index, element ->
            val featurePath = "$path.features[$index]"
            if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
                throw FontCatalogParseException("$featurePath must be a string")
            }
            val value = element.asString.nonBlank(featurePath)
            val feature = FontFeature.fromCatalogValue(value)
                ?: throw FontCatalogParseException("$featurePath has unsupported value '$value'")
            if (!parsed.add(feature)) {
                throw FontCatalogParseException("$path.features contains duplicate value '$value'")
            }
        }
        // Keep model iteration deterministic even if a producer emits a different wire order.
        return FontFeature.entries.filterTo(linkedSetOf()) { it in parsed }
    }

    private fun parseLicense(element: JsonElement?, path: String): FontLicense {
        if (element == null || element.isJsonNull) {
            throw FontCatalogParseException("Missing required field $path")
        }
        if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            return FontLicense(element.asString.nonBlank(path), null, emptyList())
        }
        val obj = element.asObject(path)
        val files = obj.optionalArray("files", path)?.mapIndexed { index, file ->
            val filePath = "$path.files[$index]"
            when {
                file.isJsonPrimitive && file.asJsonPrimitive.isString -> {
                    val value = file.asString.nonBlank(filePath)
                    if (value.isHttpsUrl()) {
                        FontLicenseFile(name = null, path = null, url = value)
                    } else {
                        FontLicenseFile(name = value.substringAfterLast('/'), path = value, url = null)
                    }
                }
                file.isJsonObject -> {
                    val fileObj = file.asJsonObject
                    val url = fileObj.optionalString("url", filePath)?.also {
                        if (!it.isHttpsUrl()) throw FontCatalogParseException("$filePath.url must use HTTPS")
                    }
                    val localPath = fileObj.optionalString("path", filePath)
                    if (url == null && localPath == null) {
                        throw FontCatalogParseException("$filePath requires url or path")
                    }
                    FontLicenseFile(
                        name = fileObj.optionalString("name", filePath)
                            ?: fileObj.optionalString("fileName", filePath)
                            ?: fileObj.optionalString("originalName", filePath),
                        path = localPath,
                        url = url,
                    )
                }
                else -> throw FontCatalogParseException("$filePath must be a string or object")
            }
        }.orEmpty()
        val legacyUrl = obj.optionalString("url", path)
        if (legacyUrl != null && !legacyUrl.isHttpsUrl()) {
            throw FontCatalogParseException("$path.url must use HTTPS")
        }
        return FontLicense(
            name = obj.requiredString("name", path),
            spdx = obj.optionalString("spdx", path),
            files = if (legacyUrl == null) files else files + FontLicenseFile(null, null, legacyUrl),
        )
    }

    private fun parseSource(element: JsonElement?, path: String): FontSource {
        val obj = element?.asObject(path)
            ?: throw FontCatalogParseException("Missing required field $path")
        val repository = obj.requiredString("repository", path)
        if (!repository.isHttpsUrl()) {
            throw FontCatalogParseException("$path.repository must use HTTPS")
        }
        val snapshotObj = obj.optionalObject("autoJs6Snapshot", path)
        val snapshot = snapshotObj?.let {
            AutoJs6FontSnapshot(
                versionCode = it.optionalInt("versionCode", "$path.autoJs6Snapshot"),
                commit = it.optionalString("commit", "$path.autoJs6Snapshot"),
                path = it.optionalString("path", "$path.autoJs6Snapshot"),
            )
        }
        return FontSource(
            repository = repository,
            upstreamVersion = obj.optionalFlexibleString("upstreamVersion", path),
            autoJs6Snapshot = snapshot,
        )
    }

    private fun parseArtifact(obj: JsonObject, path: String, revoked: Set<String>): FontArtifact {
        val format = obj.requiredString("format", path).lowercase()
        if (format != FontFileVerifier.FORMAT_WOFF2) {
            throw FontCatalogParseException("$path.format must be '${FontFileVerifier.FORMAT_WOFF2}'")
        }
        val size = obj.optionalLong("size", path) ?: obj.requiredLong("sizeBytes", path)
        if (size < FontFileVerifier.WOFF2_HEADER_SIZE || size > maxFontBytes) {
            throw FontCatalogParseException(
                "$path.size must be between ${FontFileVerifier.WOFF2_HEADER_SIZE} and $maxFontBytes bytes",
            )
        }
        val sha256 = obj.requiredString("sha256", path).lowercase()
        if (!AceFontIds.validSha256.matches(sha256)) {
            throw FontCatalogParseException("$path.sha256 must contain exactly 64 hexadecimal characters")
        }
        val urls = when {
            obj.has("urls") -> obj.requiredArray("urls", path).mapIndexed { index, url ->
                if (!url.isJsonPrimitive || !url.asJsonPrimitive.isString) {
                    throw FontCatalogParseException("$path.urls[$index] must be a string")
                }
                url.asString.nonBlank("$path.urls[$index]")
            }
            obj.has("url") -> listOf(obj.requiredString("url", path))
            else -> throw FontCatalogParseException("Missing required field $path.urls")
        }.distinct()
        if (urls.isEmpty()) throw FontCatalogParseException("$path.urls must not be empty")
        urls.forEachIndexed { index, url ->
            if (!url.isHttpsUrl()) throw FontCatalogParseException("$path.urls[$index] must use HTTPS")
        }
        obj.optionalString("url", path)?.let { legacyUrl ->
            if (legacyUrl != urls.first()) {
                throw FontCatalogParseException("$path.url must equal the first entry in $path.urls")
            }
        }

        val fileName = obj.optionalString("fileName", path) ?: "font-$sha256.woff2"
        if (fileName.contains('/') || fileName.contains('\\') || fileName == "." || fileName == ".." ||
            !fileName.endsWith(".woff2", ignoreCase = true)
        ) {
            throw FontCatalogParseException("$path.fileName must be a plain file name")
        }
        val mimeType = obj.optionalString("mimeType", path) ?: FontFileVerifier.MIME_WOFF2
        if (mimeType != FontFileVerifier.MIME_WOFF2) {
            throw FontCatalogParseException("$path.mimeType must be '${FontFileVerifier.MIME_WOFF2}'")
        }
        return FontArtifact(
            version = obj.requiredFlexibleString("version", path),
            fileName = fileName,
            format = format,
            mimeType = mimeType,
            weight = obj.optionalInt("weight", path) ?: 400,
            style = obj.optionalString("style", path) ?: "normal",
            sizeBytes = size,
            sha256 = sha256,
            urls = urls,
            revoked = sha256 in revoked,
        )
    }

    private fun parseRevocations(root: JsonObject): Set<String> {
        val array = root.optionalArray("revokedSha256", "catalog")
            ?: root.optionalArray("revokedArtifacts", "catalog")
            ?: return emptySet()
        return array.mapIndexedTo(linkedSetOf()) { index, element ->
            val path = "catalog.revokedSha256[$index]"
            val value = when {
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString
                element.isJsonObject -> element.asJsonObject.requiredString("sha256", path)
                else -> throw FontCatalogParseException("$path must be a SHA-256 string or object")
            }.lowercase()
            if (!AceFontIds.validSha256.matches(value)) {
                throw FontCatalogParseException("$path is not a valid SHA-256 digest")
            }
            value
        }
    }

    private fun JsonElement.asObject(path: String): JsonObject =
        if (isJsonObject) asJsonObject else throw FontCatalogParseException("$path must be an object")

    private fun JsonObject.requiredObject(name: String, path: String): JsonObject =
        get(name)?.asObject("$path.$name") ?: throw FontCatalogParseException("Missing required field $path.$name")

    private fun JsonObject.optionalObject(name: String, path: String): JsonObject? =
        get(name)?.takeUnless { it.isJsonNull }?.asObject("$path.$name")

    private fun JsonObject.requiredArray(name: String, path: String): JsonArray =
        optionalArray(name, path) ?: throw FontCatalogParseException("Missing required field $path.$name")

    private fun JsonObject.optionalArray(name: String, path: String): JsonArray? {
        val value = get(name)?.takeUnless { it.isJsonNull } ?: return null
        if (!value.isJsonArray) throw FontCatalogParseException("$path.$name must be an array")
        return value.asJsonArray
    }

    private fun JsonObject.requiredString(name: String, path: String): String =
        optionalString(name, path) ?: throw FontCatalogParseException("Missing required field $path.$name")

    private fun JsonObject.optionalString(name: String, path: String): String? {
        val value = get(name)?.takeUnless { it.isJsonNull } ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
            throw FontCatalogParseException("$path.$name must be a string")
        }
        return value.asString.nonBlank("$path.$name")
    }

    private fun JsonObject.requiredFlexibleString(name: String, path: String): String =
        optionalFlexibleString(name, path)
            ?: throw FontCatalogParseException("Missing required field $path.$name")

    private fun JsonObject.optionalFlexibleString(name: String, path: String): String? {
        val value = get(name)?.takeUnless { it.isJsonNull } ?: return null
        if (!value.isJsonPrimitive || (!value.asJsonPrimitive.isString && !value.asJsonPrimitive.isNumber)) {
            throw FontCatalogParseException("$path.$name must be a string or integer")
        }
        return value.asString.nonBlank("$path.$name")
    }

    private fun JsonObject.requiredInt(name: String, path: String): Int =
        optionalInt(name, path) ?: throw FontCatalogParseException("Missing required field $path.$name")

    private fun JsonObject.optionalInt(name: String, path: String): Int? {
        val long = optionalLong(name, path) ?: return null
        if (long !in Int.MIN_VALUE..Int.MAX_VALUE) {
            throw FontCatalogParseException("$path.$name is outside the integer range")
        }
        return long.toInt()
    }

    private fun JsonObject.requiredLong(name: String, path: String): Long =
        optionalLong(name, path) ?: throw FontCatalogParseException("Missing required field $path.$name")

    private fun JsonObject.optionalLong(name: String, path: String): Long? {
        val value = get(name)?.takeUnless { it.isJsonNull } ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) {
            throw FontCatalogParseException("$path.$name must be an integer")
        }
        val text = value.asJsonPrimitive.toString()
        if (!INTEGER.matches(text)) throw FontCatalogParseException("$path.$name must be an integer")
        return text.toLongOrNull() ?: throw FontCatalogParseException("$path.$name is outside the long range")
    }

    private fun String.nonBlank(path: String): String = trim().also {
        if (it.isEmpty()) throw FontCatalogParseException("$path must not be blank")
    }

    private fun String.isHttpsUrl(): Boolean = startsWith("https://", ignoreCase = true)

    companion object {
        const val SCHEMA_VERSION = 1
        private val INTEGER = Regex("-?(0|[1-9][0-9]*)")
    }
}
