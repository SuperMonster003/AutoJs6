package org.autojs.autojs.core.plugin.center

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.util.Locale

/**
 * Resolves the APK Builder build paired with a host version and device ABI.
 *
 * Resolution deliberately mirrors the plugin repository's
 * `scripts/update_compat_matrix.py resolve` command: select the compatible
 * entry with the greatest plugin version code first, then choose an exact ABI
 * artifact with a universal fallback. If that selected entry has no usable
 * artifact, callers must fall back to the legacy release-index channel.
 */
object ApkBuilderCompatibilityMatrixResolver {

    private const val SUPPORTED_SCHEMA_VERSION = 1L
    private const val UNIVERSAL_VARIANT = "inrt-universal"
    private const val VARIANT_PREFIX = "inrt-"
    private const val TRUSTED_RELEASE_PATH_PREFIX =
        "/SuperMonster003/AutoJs6-Plugin-APK-Builder-Template/releases/download/"

    private val supportedAbis = linkedSetOf(
        "arm64-v8a",
        "armeabi-v7a",
        "x86_64",
        "x86",
    )
    private val sha256Pattern = Regex("^[0-9a-fA-F]{64}$")
    private val releaseDatePattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    data class Resolution(
        val release: PluginIndexRelease,
        val supportedAbis: List<String>,
        val artifactVariant: String,
    )

    fun resolve(
        json: String,
        hostVersionCode: Long,
        deviceAbis: Collection<String>,
    ): Resolution? {
        require(hostVersionCode > 0L) { "hostVersionCode must be positive" }

        val root = JsonParser.parseString(json).asJsonObject
        val schemaVersion = root.longValue("schemaVersion")
        require(schemaVersion == SUPPORTED_SCHEMA_VERSION) {
            "Unsupported APK Builder compatibility matrix schemaVersion: $schemaVersion"
        }
        val entriesElement = root.get("entries")
        require(entriesElement != null && entriesElement.isJsonArray) {
            "APK Builder compatibility matrix entries must be an array"
        }

        val selectedEntry = entriesElement.asJsonArray
            .asSequence()
            .mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
            .mapNotNull(::parseEntry)
            .filter { entry -> hostVersionCode in entry.minHostVersionCode..entry.maxHostVersionCode }
            .maxByOrNull { it.pluginVersionCode }
            ?: return null

        val normalizedDeviceAbis = deviceAbis
            .mapNotNull(::normalizeAbi)
            .distinct()
        val artifact = selectArtifact(selectedEntry.artifacts, normalizedDeviceAbis) ?: return null
        val compositeVersionName = buildCompositeVersionName(
            pluginVersionName = selectedEntry.pluginVersionName,
            hostVersionName = selectedEntry.hostVersionName,
        )
        val versionDate = selectedEntry.releasedAt
            ?.substringBefore('T')
            ?.takeIf(releaseDatePattern::matches)

        return Resolution(
            release = PluginIndexRelease(
                versionName = compositeVersionName,
                versionCode = selectedEntry.pluginVersionCode,
                versionDate = versionDate,
                apkUrl = artifact.apkUrl,
                apkSha256 = artifact.apkSha256,
                apkSizeBytes = artifact.apkSizeBytes,
                compatibilityMatrixMatched = true,
            ),
            supportedAbis = artifact.supportedAbis,
            artifactVariant = artifact.variant,
        )
    }

    private fun parseEntry(obj: JsonObject): MatrixEntry? {
        val pluginVersionName = obj.stringValue("pluginVersionName") ?: return null
        val pluginVersionCode = obj.longValue("pluginVersionCode")?.takeIf { it > 0L } ?: return null
        val hostVersionName = obj.stringValue("hostVersionName") ?: return null
        val minHostVersionCode = obj.longValue("minHostVersionCode")?.takeIf { it > 0L } ?: return null
        val maxHostVersionCode = obj.longValue("maxHostVersionCode")?.takeIf { it >= minHostVersionCode } ?: return null
        val builtForHostVersionCode = obj.longValue("hostVersionCode")
            ?: minHostVersionCode.takeIf { minHostVersionCode == maxHostVersionCode }
            ?: return null
        if (builtForHostVersionCode !in minHostVersionCode..maxHostVersionCode) return null
        val allowPatchVersionMismatch = if (obj.has("allowPatchVersionMismatch")) {
            obj.booleanValue("allowPatchVersionMismatch") ?: return null
        } else {
            false
        }
        if (minHostVersionCode != maxHostVersionCode && !allowPatchVersionMismatch) return null
        val artifacts = obj.objectArray("artifacts")
            ?.mapNotNull { artifact -> parseArtifact(artifact, allowImplicitUniversal = false) }
            ?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(parseArtifact(obj, allowImplicitUniversal = true))

        return MatrixEntry(
            pluginVersionName = pluginVersionName,
            pluginVersionCode = pluginVersionCode,
            hostVersionName = hostVersionName,
            minHostVersionCode = minHostVersionCode,
            maxHostVersionCode = maxHostVersionCode,
            allowPatchVersionMismatch = allowPatchVersionMismatch,
            releasedAt = obj.stringValue("releasedAt"),
            artifacts = artifacts,
        )
    }

    private fun parseArtifact(
        obj: JsonObject,
        allowImplicitUniversal: Boolean,
    ): MatrixArtifact? {
        val apkName = obj.stringValue("apkName")
            ?.takeIf { it.endsWith(".apk", ignoreCase = true) }
            ?: return null
        val apkUrl = obj.stringValue("apkUrl")
            ?.takeIf { isTrustedReleaseUrl(it, apkName) }
            ?: return null
        val apkSha256 = obj.stringValue("apkSha256")
            ?.takeIf(sha256Pattern::matches)
            ?.lowercase(Locale.ROOT)
            ?: return null
        val apkSizeBytes = obj.longValue("apkSizeBytes")?.takeIf { it > 0L } ?: return null
        val variant = obj.stringValue("variant")
            ?: if (allowImplicitUniversal) UNIVERSAL_VARIANT else return null
        val rawSupportedAbis = obj.stringArray("supportedAbis") ?: emptyList()
        val normalizedSupportedAbis = rawSupportedAbis.mapNotNull(::normalizeAbi).distinct()
        if (normalizedSupportedAbis.size != rawSupportedAbis.size) return null

        when {
            variant == UNIVERSAL_VARIANT -> {
                if (normalizedSupportedAbis.isNotEmpty() && normalizedSupportedAbis.toSet() != supportedAbis) {
                    return null
                }
            }
            variant.startsWith(VARIANT_PREFIX) -> {
                val variantAbi = normalizeAbi(variant.removePrefix(VARIANT_PREFIX)) ?: return null
                if (normalizedSupportedAbis != listOf(variantAbi)) return null
            }
            else -> return null
        }

        return MatrixArtifact(
            variant = variant,
            supportedAbis = normalizedSupportedAbis,
            apkUrl = apkUrl,
            apkSha256 = apkSha256,
            apkSizeBytes = apkSizeBytes,
        )
    }

    private fun selectArtifact(
        artifacts: List<MatrixArtifact>,
        deviceAbis: List<String>,
    ): MatrixArtifact? {
        deviceAbis.forEach { deviceAbi ->
            artifacts.firstOrNull { artifact ->
                artifact.variant != UNIVERSAL_VARIANT && deviceAbi in artifact.supportedAbis
            }?.let { return it }
        }
        return artifacts.firstOrNull { it.variant == UNIVERSAL_VARIANT }
    }

    private fun buildCompositeVersionName(
        pluginVersionName: String,
        hostVersionName: String,
    ): String {
        if (pluginVersionName.contains("+autojs6-", ignoreCase = true)) {
            return pluginVersionName
        }
        val hostVersionSlug = hostVersionName
            .trim()
            .replace(Regex("\\s+"), "-")
            .lowercase(Locale.ROOT)
        return "$pluginVersionName+autojs6-$hostVersionSlug"
    }

    private fun isTrustedReleaseUrl(url: String, apkName: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("github.com", ignoreCase = true) &&
                uri.userInfo == null &&
                (uri.port == -1 || uri.port == 443) &&
                uri.query == null &&
                uri.fragment == null &&
                uri.path.startsWith(TRUSTED_RELEASE_PATH_PREFIX) &&
                uri.path.substringAfterLast('/') == apkName
    }

    private fun normalizeAbi(value: String): String? {
        val normalized = value.trim().lowercase(Locale.ROOT)
        return normalized.takeIf { it in supportedAbis }
    }

    private fun JsonObject.stringValue(key: String): String? {
        val primitive = get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return null
        if (!primitive.isString) return null
        return primitive.asString.trim().takeIf { it.isNotBlank() }
    }

    private fun JsonObject.longValue(key: String): Long? {
        val primitive = get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return null
        if (!primitive.isNumber) return null
        return primitive.asString.toLongOrNull()
    }

    private fun JsonObject.booleanValue(key: String): Boolean? {
        val primitive = get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return null
        if (!primitive.isBoolean) return null
        return primitive.asBoolean
    }

    private fun JsonObject.stringArray(key: String): List<String>? {
        val element = get(key) ?: return null
        if (!element.isJsonArray) return null
        return element.asJsonArray.mapNotNull { item ->
            item.takeIf { it.isJsonPrimitive }
                ?.asJsonPrimitive
                ?.takeIf { it.isString }
                ?.asString
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
    }

    private fun JsonObject.objectArray(key: String): List<JsonObject>? {
        val element = get(key) ?: return null
        if (!element.isJsonArray) return null
        return element.asJsonArray.mapNotNull { item -> item.takeIf { it.isJsonObject }?.asJsonObject }
    }

    private data class MatrixEntry(
        val pluginVersionName: String,
        val pluginVersionCode: Long,
        val hostVersionName: String,
        val minHostVersionCode: Long,
        val maxHostVersionCode: Long,
        val allowPatchVersionMismatch: Boolean,
        val releasedAt: String?,
        val artifacts: List<MatrixArtifact>,
    )

    private data class MatrixArtifact(
        val variant: String,
        val supportedAbis: List<String>,
        val apkUrl: String,
        val apkSha256: String,
        val apkSizeBytes: Long,
    )
}
