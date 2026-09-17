package org.autojs.autojs.network.github

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

/**
 * The subset of a GitHub release (`GET /repos/{owner}/{repo}/releases/latest`) the host uses.
 *
 * zh-CN: 宿主用到的 GitHub release 字段子集 (`GET /repos/{owner}/{repo}/releases/latest`).
 */
data class GitHubRelease(
    val tagName: String,
    val name: String?,
    val htmlUrl: String?,
    val body: String?,
    val publishedAt: String?,
    val prerelease: Boolean,
    val assets: List<GitHubReleaseAsset>,
)

/**
 * One release asset; only the fields needed to pick and download an APK.
 *
 * zh-CN: 单个 release 资产, 只保留选包与下载需要的字段.
 */
data class GitHubReleaseAsset(
    val name: String,
    val size: Long,
    val browserDownloadUrl: String?,
)

/**
 * Parses GitHub REST JSON without reflection so that the shape is explicit and testable on the JVM.
 *
 * zh-CN: 不用反射解析 GitHub REST JSON, 字段形状显式且可在 JVM 上测试.
 */
object GitHubReleaseParser {

    @Throws(GitHubResponseFormatException::class)
    fun parseRelease(json: String): GitHubRelease {
        val root = parseObject(json)
        val tagName = root.string("tag_name")?.takeIf { it.isNotBlank() }
            ?: throw GitHubResponseFormatException("Release JSON has no tag_name")
        return GitHubRelease(
            tagName = tagName,
            name = root.string("name"),
            htmlUrl = root.string("html_url"),
            body = root.string("body"),
            publishedAt = root.string("published_at"),
            prerelease = root.boolean("prerelease"),
            assets = parseAssets(root.get("assets")),
        )
    }

    /** Extracts `message` from a GitHub error body such as `{"message": "Not Found", ...}`; null if absent. */
    fun parseErrorMessage(json: String?): String? {
        if (json.isNullOrBlank()) return null
        return runCatching { parseObject(json).string("message") }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun parseAssets(element: JsonElement?): List<GitHubReleaseAsset> {
        if (element == null || !element.isJsonArray) return emptyList()
        return element.asJsonArray.mapNotNull { item ->
            if (!item.isJsonObject) return@mapNotNull null
            val obj = item.asJsonObject
            val name = obj.string("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            GitHubReleaseAsset(
                name = name,
                size = obj.long("size"),
                browserDownloadUrl = obj.string("browser_download_url"),
            )
        }
    }

    @Throws(GitHubResponseFormatException::class)
    private fun parseObject(json: String): JsonObject {
        val element = try {
            JsonParser.parseString(json)
        } catch (e: JsonSyntaxException) {
            throw GitHubResponseFormatException("Malformed GitHub JSON", e)
        } catch (e: IllegalStateException) {
            throw GitHubResponseFormatException("Malformed GitHub JSON", e)
        }
        if (!element.isJsonObject) throw GitHubResponseFormatException("GitHub JSON root is not an object")
        return element.asJsonObject
    }

    private fun JsonObject.string(key: String): String? {
        val value = get(key) ?: return null
        return if (value.isJsonPrimitive) value.asString else null
    }

    private fun JsonObject.long(key: String): Long {
        val value = get(key) ?: return -1L
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) value.asLong else -1L
    }

    private fun JsonObject.boolean(key: String): Boolean {
        val value = get(key) ?: return false
        return value.isJsonPrimitive && value.asJsonPrimitive.isBoolean && value.asBoolean
    }
}
