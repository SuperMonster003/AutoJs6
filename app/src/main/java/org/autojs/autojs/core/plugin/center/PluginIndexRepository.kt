package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.util.AndroidUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.math.max

class PluginIndexRepository {

    companion object {
        private const val TAG = "PluginIndexRepository"
        private const val CACHE_FILE_NAME = "autojs6_plugin_index_v2.json"

        private const val OFFICIAL_OWNER = "SuperMonster003"
        private const val OFFICIAL_REPO_PREFIX = "AutoJs6-Plugin-"
        private const val THIRD_PARTY_OWNER = "SuperMonster002"
        private const val THIRD_PARTY_REPO = "Auto.js-SDK-Plugins-Garage"
        private const val THIRD_PARTY_BRANCH = "main"

        @Volatile
        private var apiRateLimitCooldownUntilMs: Long = 0L

        @Volatile
        private var lastNetworkFetchAttemptMs: Long = 0L
    }

    @Volatile
    private var memoryCache: List<PluginIndexEntry>? = null

    suspend fun fetchOnlineIndex(
        context: Context,
        forceRefresh: Boolean,
        enabledSources: Set<PluginIndexSource>,
    ): List<PluginIndexEntry> = withContext(Dispatchers.IO) {
        val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
        val cachedAll = memoryCache ?: readCacheSafely(cacheFile).also { parsed ->
            if (parsed != null) {
                memoryCache = parsed
            }
        }

        if (!forceRefresh) {
            cachedAll?.let { return@withContext filterBySource(it, enabledSources) }
        }

        val now = System.currentTimeMillis()
        if (forceRefresh && cachedAll != null && now - lastNetworkFetchAttemptMs < 15_000L) {
            Log.i(TAG, "Skip too-frequent force refresh (<15s), use cache.")
            return@withContext filterBySource(cachedAll, enabledSources)
        }

        if (enabledSources.isEmpty()) {
            return@withContext emptyList()
        }

        lastNetworkFetchAttemptMs = now

        val cachedBySource = cachedAll.orEmpty().groupBy { it.source }
        val freshBySource = linkedMapOf<PluginIndexSource, List<PluginIndexEntry>>()

        if (PluginIndexSource.OFFICIAL in enabledSources) {
            val entries = runCatching {
                fetchOfficialEntries(context)
            }.onFailure { e ->
                Log.w(TAG, "Failed to fetch official plugin index: ${e.message}")
            }.getOrElse {
                cachedBySource[PluginIndexSource.OFFICIAL].orEmpty()
            }
            freshBySource[PluginIndexSource.OFFICIAL] = entries
        }

        if (PluginIndexSource.THIRD_PARTY in enabledSources) {
            val entries = runCatching {
                fetchThirdPartyEntries(context)
            }.onFailure { e ->
                Log.w(TAG, "Failed to fetch third-party plugin index: ${e.message}")
            }.getOrElse {
                cachedBySource[PluginIndexSource.THIRD_PARTY].orEmpty()
            }
            freshBySource[PluginIndexSource.THIRD_PARTY] = entries
        }

        val resultForSelected = freshBySource.values.flatten()
        val untouched = cachedAll.orEmpty().filterNot { it.source in enabledSources }
        val finalAll = (resultForSelected + untouched).distinctBy { "${it.source}:${it.packageName}" }

        if (finalAll.isNotEmpty()) {
            memoryCache = finalAll
            writeCache(cacheFile, finalAll)
        }

        val fallback = memoryCache ?: cachedAll.orEmpty()
        return@withContext filterBySource(fallback, enabledSources)
    }

    private fun filterBySource(
        source: List<PluginIndexEntry>,
        enabledSources: Set<PluginIndexSource>,
    ): List<PluginIndexEntry> {
        if (enabledSources.isEmpty()) {
            return emptyList()
        }
        return source.filter { it.source in enabledSources }
    }

    private fun fetchOfficialEntries(context: Context): List<PluginIndexEntry> {
        val languageTag = Language.getPrefLanguage().getLocalCompatibleLanguageTag()
        val repos = fetchOfficialRepoConfigs(context)
        if (repos.isEmpty()) {
            Log.w(TAG, "No official plugin repository found with prefix: $OFFICIAL_REPO_PREFIX")
            return emptyList()
        }

        return repos.mapNotNull { plugin ->
            runCatching {
                fetchOfficialEntry(context, plugin, languageTag)
            }.onFailure {
                Log.w(TAG, "Failed to fetch official plugin ${plugin.repo}: ${it.message}")
            }.getOrNull()
        }
    }

    private fun fetchOfficialRepoConfigs(context: Context): List<OfficialPluginConfig> {
        val repos = getJsonArray(
            context = context,
            url = "https://api.github.com/users/$OFFICIAL_OWNER/repos?per_page=100&type=public&sort=updated",
        )
        val list = mutableListOf<OfficialPluginConfig>()
        for (i in 0 until repos.length()) {
            val repo = repos.optJSONObject(i) ?: continue
            if (repo.optBoolean("fork")) continue
            if (repo.optBoolean("archived")) continue

            val name = repo.optString("name").trim()
            if (!name.startsWith(OFFICIAL_REPO_PREFIX)) continue

            val branch = repo.optString("default_branch").takeIf { it.isNotBlank() } ?: "main"
            val owner = repo.optJSONObject("owner")?.optString("login")?.takeIf { it.isNotBlank() } ?: OFFICIAL_OWNER
            list += OfficialPluginConfig(
                owner = owner,
                repo = name,
                branch = branch,
            )
        }
        return list.sortedBy { it.repo.lowercase() }
    }

    private fun fetchOfficialEntry(
        context: Context,
        plugin: OfficialPluginConfig,
        languageTag: String,
    ): PluginIndexEntry? {
        val releaseJson = getJsonObject(
            context = context,
            url = "https://api.github.com/repos/${plugin.owner}/${plugin.repo}/releases/latest",
        )
        val releaseAssets = parseReleaseAssets(releaseJson.optJSONArray("assets"))
        val selectedAsset = pickAssetByAbiOrFallback(releaseAssets)
        val releaseBody = releaseJson.optString("body").takeIf { it.isNotBlank() }
        val publishedDate = releaseJson.optString("published_at").takeIf { it.length >= 10 }?.substring(0, 10)
        val releaseAuthor = releaseJson.optJSONObject("author")?.optString("login")?.takeIf { it.isNotBlank() }

        val versionProperties = runCatching {
            getText(
                context = context,
                url = rawGithubUrl(
                    owner = plugin.owner,
                    repo = plugin.repo,
                    branch = plugin.branch,
                    path = "version.properties",
                ),
            )
        }.getOrNull()
        val versionMap = parseProperties(versionProperties.orEmpty())

        val manifestText = runCatching {
            getText(
                context = context,
                url = rawGithubUrl(
                    owner = plugin.owner,
                    repo = plugin.repo,
                    branch = plugin.branch,
                    path = "app/src/main/AndroidManifest.xml",
                ),
            )
        }.getOrNull()

        val buildGradleText = runCatching {
            getText(
                context = context,
                url = rawGithubUrl(
                    owner = plugin.owner,
                    repo = plugin.repo,
                    branch = plugin.branch,
                    path = "app/build.gradle.kts",
                ),
            )
        }.getOrNull()

        val repoTreePaths = emptySet<String>()

        val unknown = context.getString(R.string.text_unknown)
        val packageName = parseManifestPackageName(manifestText)
            ?: parseApplicationIdFromBuildGradle(buildGradleText)
            ?: "unknown.${plugin.repo.lowercase().replace(Regex("[^a-z0-9._-]"), "-")}"

        val title = parseManifestApplicationLabel(manifestText)
            ?: releaseJson.optString("name").substringBefore(" @").takeIf { it.isNotBlank() }
            ?: plugin.repo.removePrefix(OFFICIAL_REPO_PREFIX).takeIf { it.isNotBlank() }
            ?: unknown

        val iconRef = parseManifestApplicationIcon(manifestText)
        val iconPath = resolveIconPath(iconRef, repoTreePaths.orEmpty())
        val iconUrl = iconPath?.let {
            rawGithubUrl(
                owner = plugin.owner,
                repo = plugin.repo,
                branch = plugin.branch,
                path = it,
            ).toUri()
        }

        val description = resolveOfficialDescription(
            context = context,
            owner = plugin.owner,
            repo = plugin.repo,
            branch = plugin.branch,
            languageTag = languageTag,
            treePaths = repoTreePaths.orEmpty(),
        ) ?: unknown

        val author = parseManifestAuthor(manifestText)
            ?: releaseAuthor
            ?: unknown

        val versionName = versionMap["VERSION_NAME"]?.takeIf { it.isNotBlank() }
            ?: releaseJson.optString("tag_name").removePrefix("v").takeIf { it.isNotBlank() }
            ?: context.getString(R.string.text_unknown)

        val versionCode = versionMap["VERSION_BUILD"]?.toLongOrNull()
            ?: 0L

        val changelogPath = resolveOfficialChangelogPath(languageTag, repoTreePaths.orEmpty())
        val changelogUrl = changelogPath?.let {
            rawGithubUrl(
                owner = plugin.owner,
                repo = plugin.repo,
                branch = plugin.branch,
                path = it,
            )
        }

        val release = PluginIndexRelease(
            versionName = versionName,
            versionCode = max(versionCode, 0L),
            versionDate = publishedDate,
            apkUrl = selectedAsset?.downloadUrl,
            apkSha256 = selectedAsset?.sha256,
            apkSizeBytes = selectedAsset?.size,
            changelogUrl = changelogUrl,
            changelogText = releaseBody,
        )

        return PluginIndexEntry(
            packageName = packageName,
            iconUrl = iconUrl,
            title = title,
            description = description,
            author = author,
            collaborators = emptyList(),
            engine = null,
            variant = null,
            engineId = null,
            releases = listOf(release),
            tags = listOf("official"),
            source = PluginIndexSource.OFFICIAL,
        )
    }

    private fun fetchThirdPartyEntries(context: Context): List<PluginIndexEntry> {
        val treeJson = getJsonObject(
            context = context,
            url = "https://api.github.com/repos/$THIRD_PARTY_OWNER/$THIRD_PARTY_REPO/git/trees/$THIRD_PARTY_BRANCH?recursive=1",
        )
        val treeArray = treeJson.optJSONArray("tree") ?: JSONArray()

        val candidates = mutableListOf<GarageApk>()
        for (i in 0 until treeArray.length()) {
            val node = treeArray.optJSONObject(i) ?: continue
            if (node.optString("type") != "blob") continue

            val path = node.optString("path")
            if (!path.endsWith(".apk", ignoreCase = true)) continue
            if (!path.contains('/')) continue

            val packageName = path.substringBefore('/').trim()
            if (packageName.isBlank()) continue

            val fileName = path.substringAfterLast('/')
            val parsed = parseGarageApkFileName(fileName) ?: continue
            val size = node.optLong("size").takeIf { it > 0 }

            candidates += GarageApk(
                packageName = packageName,
                fileName = fileName,
                originalName = parsed.originalName,
                versionName = parsed.versionName,
                versionCode = parsed.versionCode,
                size = size ?: 0L,
                downloadUrl = "https://github.com/$THIRD_PARTY_OWNER/$THIRD_PARTY_REPO/raw/refs/heads/$THIRD_PARTY_BRANCH/$path",
            )
        }

        val latestByPackage = candidates
            .groupBy { it.packageName }
            .mapNotNull { (_, list) ->
                list.maxWithOrNull(
                    compareBy<GarageApk> { it.versionCode }
                        .thenBy { it.size }
                        .thenBy { it.fileName },
                )
            }

        return latestByPackage.map { apk ->
            PluginIndexEntry(
                packageName = apk.packageName,
                title = apk.originalName,
                description = "[ ${context.getString(R.string.text_unknown_description_for_plugin)} ]",
                author = null,
                releases = listOf(
                    PluginIndexRelease(
                        versionName = apk.versionName,
                        versionCode = apk.versionCode,
                        versionDate = null,
                        apkUrl = apk.downloadUrl,
                        apkSha256 = null,
                        apkSizeBytes = apk.size,
                        changelogUrl = null,
                        changelogText = context.getString(R.string.text_no_release_notes),
                    ),
                ),
                tags = listOf("third-party"),
                source = PluginIndexSource.THIRD_PARTY,
            )
        }
    }

    private fun parseReleaseAssets(array: JSONArray?): List<ReleaseAsset> {
        if (array == null) return emptyList()
        val list = mutableListOf<ReleaseAsset>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val url = obj.optString("browser_download_url").takeIf { it.isNotBlank() } ?: continue
            val size = obj.optLong("size", 0L).takeIf { it > 0 } ?: 0L
            val sha256 = obj.optString("digest")
                .substringAfter("sha256:", missingDelimiterValue = "")
                .ifBlank { null }
            list += ReleaseAsset(name = name, downloadUrl = url, size = size, sha256 = sha256)
        }
        return list
    }

    private fun pickAssetByAbiOrFallback(assets: List<ReleaseAsset>): ReleaseAsset? {
        if (assets.isEmpty()) return null

        val deviceAbis = AndroidUtils.deviceFilteredAbiList
        deviceAbis.forEach { abi ->
            assets.firstOrNull { it.name.contains(abi, ignoreCase = true) }?.let { return it }
        }

        assets.firstOrNull { it.name.contains("universal", ignoreCase = true) }?.let { return it }

        return assets.maxByOrNull { it.size }
    }

    private fun parseManifestPackageName(manifest: String?): String? =
        manifest?.let { Regex("""<manifest[^>]*\bpackage="([^"]+)"""").find(it)?.groupValues?.getOrNull(1)?.trim() }

    private fun parseManifestApplicationLabel(manifest: String?): String? =
        manifest?.let { Regex("""<application[^>]*\bandroid:label="([^"]+)"""").find(it)?.groupValues?.getOrNull(1)?.trim() }

    private fun parseManifestApplicationIcon(manifest: String?): String? =
        manifest?.let { Regex("""<application[^>]*\bandroid:icon="([^"]+)"""").find(it)?.groupValues?.getOrNull(1)?.trim() }

    private fun parseManifestAuthor(manifest: String?): String? =
        manifest?.let {
            Regex("""<meta-data[^>]*\bandroid:name="org\.autojs\.plugin\.info\.AUTHOR"[^>]*\bandroid:value="([^"]+)"""")
                .find(it)?.groupValues?.getOrNull(1)?.trim()
        }

    private fun parseApplicationIdFromBuildGradle(buildGradle: String?): String? {
        if (buildGradle.isNullOrBlank()) return null
        val byGlobal = Regex("""val\s+globalApplicationId\s*=\s*"([^"]+)"""")
            .find(buildGradle)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (!byGlobal.isNullOrBlank()) return byGlobal

        return Regex("""\bnamespace\s*=\s*"([^"]+)"""")
            .find(buildGradle)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
    }

    private fun resolveIconPath(iconRef: String?, treePaths: Set<String>): String? {
        if (iconRef.isNullOrBlank() || !iconRef.startsWith("@")) return null
        val type = iconRef.substringAfter("@").substringBefore("/")
        val name = iconRef.substringAfter("/").ifBlank { return null }

        val dirs = linkedSetOf(
            "app/src/main/res/$type",
            "app/src/main/res/${type}-xxxhdpi",
            "app/src/main/res/${type}-xxhdpi",
            "app/src/main/res/${type}-xhdpi",
            "app/src/main/res/${type}-hdpi",
            "app/src/main/res/${type}-mdpi",
            "app/src/main/res/${type}-anydpi-v26",
            "app/src/main/res/drawable",
            "app/src/main/res/drawable-xxxhdpi",
            "app/src/main/res/drawable-xxhdpi",
            "app/src/main/res/drawable-xhdpi",
            "app/src/main/res/drawable-hdpi",
            "app/src/main/res/drawable-mdpi",
        )
        val exts = listOf("png", "webp", "jpg", "jpeg")

        if (treePaths.isEmpty()) {
            return dirs.firstNotNullOfOrNull { dir ->
                exts.firstNotNullOfOrNull { ext ->
                    "$dir/$name.$ext"
                }
            }
        }

        return dirs.firstNotNullOfOrNull { dir ->
            exts.firstNotNullOfOrNull { ext ->
                val candidate = "$dir/$name.$ext"
                candidate.takeIf { it in treePaths }
            }
        }
    }

    private fun resolveOfficialDescription(
        context: Context,
        owner: String,
        repo: String,
        branch: String,
        languageTag: String,
        treePaths: Set<String>,
    ): String? {
        val languageDirs = languageTagToAndroidResourceDirs(languageTag)
        val candidatePaths = languageDirs.map { "app/src/main/res/$it/strings.xml" }

        candidatePaths.forEach { path ->
            if (treePaths.isNotEmpty() && path !in treePaths) return@forEach
            val text = runCatching {
                getText(
                    context = context,
                    url = rawGithubUrl(
                        owner = owner,
                        repo = repo,
                        branch = branch,
                        path = path,
                    ),
                )
            }.getOrNull() ?: return@forEach

            parseStringResource(text, "plugin_description")?.let { return it }
        }
        return null
    }

    private fun resolveOfficialChangelogPath(
        languageTag: String,
        treePaths: Set<String>,
    ): String? {
        val tagCandidates = languageTagToChangelogTags(languageTag)
        if (treePaths.isEmpty()) {
            return tagCandidates.firstOrNull()?.let { "app/src/main/assets/doc/CHANGELOG-$it.md" }
        }
        return tagCandidates.firstNotNullOfOrNull { tag ->
            val path = "app/src/main/assets/doc/CHANGELOG-$tag.md"
            path.takeIf { it in treePaths }
        }
    }

    private fun languageTagToAndroidResourceDirs(languageTag: String): List<String> {
        val normalized = languageTag.trim()
        val primary = normalized.substringBefore('-').lowercase()

        val dirs = linkedSetOf<String>()
        when {
            normalized.equals("zh-Hant-HK", ignoreCase = true) -> {
                dirs += "values-zh-rHK"
                dirs += "values-zh-rTW"
                dirs += "values-zh"
            }
            normalized.equals("zh-Hant-TW", ignoreCase = true) -> {
                dirs += "values-zh-rTW"
                dirs += "values-zh-rHK"
                dirs += "values-zh"
            }
            primary == "zh" -> {
                dirs += "values-zh"
                dirs += "values-zh-rHK"
                dirs += "values-zh-rTW"
            }
            else -> {
                dirs += "values-$primary"
            }
        }
        dirs += "values-en"
        dirs += "values"
        return dirs.toList()
    }

    private fun languageTagToChangelogTags(languageTag: String): List<String> {
        val normalized = languageTag.trim()
        val primary = normalized.substringBefore('-').lowercase()
        val tags = linkedSetOf<String>()
        when {
            normalized.equals("zh-Hant-HK", ignoreCase = true) -> {
                tags += "zh-rHK"
                tags += "zh-rTW"
                tags += "zh"
            }
            normalized.equals("zh-Hant-TW", ignoreCase = true) -> {
                tags += "zh-rTW"
                tags += "zh-rHK"
                tags += "zh"
            }
            primary == "zh" -> {
                tags += "zh"
                tags += "zh-rHK"
                tags += "zh-rTW"
            }
            else -> {
                tags += primary
            }
        }
        tags += "en"
        return tags.toList()
    }

    private fun parseStringResource(xml: String, name: String): String? {
        val regex = Regex("""<string\s+name="$name"[^>]*>([\s\S]*?)</string>""")
        val raw = regex.find(xml.removePrefix("\uFEFF"))?.groupValues?.getOrNull(1)?.trim() ?: return null
        return raw
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .ifBlank { null }
    }

    private fun parseProperties(content: String): Map<String, String> {
        if (content.isBlank()) return emptyMap()
        return content
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
            .associate { line ->
                val idx = line.indexOf('=')
                val k = line.substring(0, idx).trim()
                val v = line.substring(idx + 1).trim()
                k to v
            }
    }

    private fun parseGarageApkFileName(fileName: String): GarageParsedName? {
        if (!fileName.endsWith(".apk", ignoreCase = true)) return null
        val base = fileName.removeSuffix(".apk")
        val suffixMatch = Regex("""-(?:build|buid)-(\d+)$""", RegexOption.IGNORE_CASE).find(base) ?: return null
        val versionCode = suffixMatch.groupValues.getOrNull(1)?.toLongOrNull() ?: return null
        val withoutSuffix = base.removeRange(suffixMatch.range)
        val split = withoutSuffix.lastIndexOf('-')
        if (split <= 0 || split >= withoutSuffix.length - 1) return null
        val originalName = withoutSuffix.substring(0, split)
        val versionName = withoutSuffix.substring(split + 1)
        if (originalName.isBlank() || versionName.isBlank()) return null
        return GarageParsedName(
            originalName = originalName,
            versionName = versionName,
            versionCode = versionCode,
        )
    }

    private fun rawGithubUrl(
        owner: String,
        repo: String,
        branch: String,
        path: String,
    ): String {
        val safePath = path.trimStart('/')
        return "https://raw.githubusercontent.com/$owner/$repo/refs/heads/$branch/$safePath"
    }

    private fun getJsonObject(context: Context, url: String): JSONObject =
        JSONObject(getText(context, url, "application/json"))

    private fun getJsonArray(context: Context, url: String): JSONArray =
        JSONArray(getText(context, url, "application/json"))

    private fun getText(
        context: Context,
        url: String,
        accept: String = "*/*",
    ): String {
        val isGitHubApi = url.startsWith("https://api.github.com/")
        if (isGitHubApi) {
            val now = System.currentTimeMillis()
            if (now < apiRateLimitCooldownUntilMs) {
                val remainMs = apiRateLimitCooldownUntilMs - now
                throw IllegalStateException("GitHub API cooldown active (${remainMs / 1000}s remaining)")
            }
        }

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "${context.getString(R.string.app_name)}/${BuildConfig.VERSION_NAME} (PluginCenter)")
            if (isGitHubApi) {
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
        }
        return try {
            conn.connect()
            val code = conn.responseCode
            if (isGitHubApi) {
                updateGitHubApiRateLimitCooldown(conn, code)
            }
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }?.trim().orEmpty()
                val shortErr = err.take(220)
                throw IllegalStateException(
                    if (shortErr.isBlank()) "HTTP $code for $url" else "HTTP $code for $url: $shortErr",
                )
            }
            conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun updateGitHubApiRateLimitCooldown(conn: HttpURLConnection, code: Int) {
        val remain = conn.getHeaderField("X-RateLimit-Remaining")?.toLongOrNull()
        val resetEpoch = conn.getHeaderField("X-RateLimit-Reset")?.toLongOrNull()
        val shouldCooldown = code == HttpURLConnection.HTTP_FORBIDDEN || code == 429 || remain == 0L
        if (!shouldCooldown) return

        val until = ((resetEpoch ?: (System.currentTimeMillis() / 1000L + 60L)) * 1000L) + 3_000L
        if (until > apiRateLimitCooldownUntilMs) {
            apiRateLimitCooldownUntilMs = until
            Log.w(TAG, "GitHub API rate limit cooldown until $apiRateLimitCooldownUntilMs")
        }
    }

    private fun readCacheSafely(file: File): List<PluginIndexEntry>? =
        runCatching {
            if (!file.exists() || !file.canRead()) return null
            val text = file.readText()
            parseCacheJson(text)
        }.getOrNull()

    private fun writeCache(file: File, entries: List<PluginIndexEntry>) {
        runCatching {
            val root = JSONObject()
            val items = JSONArray()
            entries.forEach { entry ->
                val obj = JSONObject()
                obj.put("packageName", entry.packageName)
                obj.put("iconUrl", entry.iconUrl?.toString())
                obj.put("title", entry.title)
                obj.put("description", entry.description)
                obj.put("author", entry.author)
                obj.put("collaborators", JSONArray(entry.collaborators))
                obj.put("engine", entry.engine)
                obj.put("variant", entry.variant)
                obj.put("engineId", entry.engineId)
                obj.put("tags", JSONArray(entry.tags))
                obj.put("source", entry.source.name)

                val releaseArr = JSONArray()
                entry.releases.forEach { release ->
                    val rel = JSONObject()
                    rel.put("versionName", release.versionName)
                    rel.put("versionCode", release.versionCode)
                    rel.put("versionDate", release.versionDate)
                    rel.put("apkUrl", release.apkUrl)
                    rel.put("apkSha256", release.apkSha256)
                    rel.put("apkSizeBytes", release.apkSizeBytes)
                    rel.put("changelogUrl", release.changelogUrl)
                    rel.put("changelogText", release.changelogText)
                    releaseArr.put(rel)
                }
                obj.put("releases", releaseArr)
                items.put(obj)
            }
            root.put("items", items)
            file.writeText(root.toString())
        }.onFailure {
            Log.w(TAG, "Failed to write plugin index cache: ${it.message}")
        }
    }

    private fun parseCacheJson(json: String): List<PluginIndexEntry> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("items") ?: root.optJSONArray("plugins") ?: JSONArray()
        val list = mutableListOf<PluginIndexEntry>()

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val packageName = obj.optString("packageName").takeIf { it.isNotBlank() } ?: continue
            val title = obj.optString("title").takeIf { it.isNotBlank() } ?: packageName
            val description = obj.optString("description")
            val author = obj.optString("author").takeIf { it.isNotBlank() }
            val iconUrl = obj.optString("iconUrl").takeIf { it.isNotBlank() }?.toUri()
            val source = obj.optString("source")
                .takeIf { it.isNotBlank() }
                ?.runCatching { PluginIndexSource.valueOf(this) }
                ?.getOrNull()
                ?: PluginIndexSource.OFFICIAL

            val collaborators = obj.optJSONArray("collaborators")?.let { ja ->
                List(ja.length()) { idx -> ja.optString(idx) }.filter { it.isNotBlank() }
            } ?: emptyList()
            val tags = obj.optJSONArray("tags")?.let { ja ->
                List(ja.length()) { idx -> ja.optString(idx) }.filter { it.isNotBlank() }
            } ?: emptyList()

            val releases = obj.optJSONArray("releases")?.let { ja ->
                buildList {
                    for (j in 0 until ja.length()) {
                        val rel = ja.optJSONObject(j) ?: continue
                        add(
                            PluginIndexRelease(
                                versionName = rel.optString("versionName", "0.0.0"),
                                versionCode = rel.optLong("versionCode", 0L),
                                versionDate = rel.optString("versionDate").takeIf { it.isNotBlank() },
                                apkUrl = rel.optString("apkUrl").takeIf { it.isNotBlank() },
                                apkSha256 = rel.optString("apkSha256").takeIf { it.isNotBlank() },
                                apkSizeBytes = rel.optLong("apkSizeBytes", -1L).takeIf { it > 0 },
                                changelogUrl = rel.optString("changelogUrl").takeIf { it.isNotBlank() },
                                changelogText = rel.optString("changelogText").takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                }
            } ?: emptyList()

            list += PluginIndexEntry(
                packageName = packageName,
                iconUrl = iconUrl,
                title = title,
                description = description,
                author = author,
                collaborators = collaborators,
                engine = obj.optString("engine").takeIf { it.isNotBlank() },
                variant = obj.optString("variant").takeIf { it.isNotBlank() },
                engineId = obj.optString("engineId").takeIf { it.isNotBlank() },
                releases = releases,
                tags = tags,
                source = source,
            )
        }
        return list
    }

    private data class OfficialPluginConfig(
        val owner: String,
        val repo: String,
        val branch: String,
    )

    private data class ReleaseAsset(
        val name: String,
        val downloadUrl: String,
        val size: Long,
        val sha256: String?,
    )

    private data class GarageParsedName(
        val originalName: String,
        val versionName: String,
        val versionCode: Long,
    )

    private data class GarageApk(
        val packageName: String,
        val fileName: String,
        val originalName: String,
        val versionName: String,
        val versionCode: Long,
        val size: Long,
        val downloadUrl: String,
    )
}
