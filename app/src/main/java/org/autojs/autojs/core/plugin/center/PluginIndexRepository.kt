package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.network.GitHubTokenStore
import org.autojs.autojs.util.AndroidUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

@Suppress("SameParameterValue")
data class PluginIndexFetchIssue(
    val source: PluginIndexSource,
    val message: String,
)

class PluginIndexRepository {

    companion object {
        private const val TAG = "PluginIndexRepository"
        private const val CACHE_FILE_NAME = "autojs6_plugin_index_v8.json"

        private const val OFFICIAL_OWNER = "SuperMonster003"
        private const val OFFICIAL_INDEX_REPO = "AutoJs6"
        private const val OFFICIAL_INDEX_BRANCH = "master"
        private const val OFFICIAL_GENERATED_INDEX_PATH = "plugins.official.generated.json"
        private const val THIRD_PARTY_OWNER = "SuperMonster002"
        private const val THIRD_PARTY_REPO = "Auto.js-SDK-Plugins-Garage"
        private const val THIRD_PARTY_BRANCH = "main"
        private const val THIRD_PARTY_GENERATED_INDEX_PATH = "plugins.generated.json"

        private const val OFFICIAL_ABI_GAP_RETRY_INTERVAL_MS = 60_000L
        private const val AUTHENTICATED_OFFICIAL_ABI_GAP_RETRY_INTERVAL_MS = 15_000L
        private const val FORCE_REFRESH_MIN_INTERVAL_MS = 15_000L
        private const val AUTHENTICATED_FORCE_REFRESH_MIN_INTERVAL_MS = 3_000L

        @Volatile
        private var apiRateLimitCooldownUntilMs: Long = 0L

        @Volatile
        private var apiRateLimitCooldownKey: String? = null

        @Volatile
        private var lastNetworkFetchAttemptMs: Long = 0L

        @Volatile
        private var lastNetworkFetchAttemptKey: String? = null

        @Volatile
        private var validatedGitHubApiAuthKey: String? = null

        private fun githubAuthKeyOf(token: String?): String = token?.let { "${it.length}:${it.hashCode()}" } ?: "anonymous"
    }

    @Volatile
    private var memoryCache: List<PluginIndexEntry>? = null
    @Volatile
    private var memoryFetchedSources: Set<PluginIndexSource>? = null
    @Volatile
    private var memoryLanguageTag: String? = null

    suspend fun getCachedIndex(
        context: Context,
        enabledSources: Set<PluginIndexSource>,
    ): List<PluginIndexEntry> = withContext(Dispatchers.IO) {
        if (enabledSources.isEmpty()) {
            return@withContext emptyList()
        }
        val currentLanguageTag = normalizeLanguageTag(Language.getPrefLanguage().getLocalCompatibleLanguageTag()) ?: "en"
        val cacheFile = resolveCacheFile(context)
        val cacheSnapshot = when {
            memoryCache != null && memoryFetchedSources != null -> CacheSnapshot(
                entries = memoryCache.orEmpty(),
                fetchedSources = memoryFetchedSources.orEmpty(),
                languageTag = memoryLanguageTag,
            )
            else -> readCacheSafely(cacheFile)?.also { parsed ->
                memoryCache = parsed.entries
                memoryFetchedSources = parsed.fetchedSources
                memoryLanguageTag = parsed.languageTag
            }
        } ?: return@withContext emptyList()
        if (!isSameLanguageTag(cacheSnapshot.languageTag, currentLanguageTag)) {
            Log.i(TAG, "Plugin index cache language changed: ${cacheSnapshot.languageTag} -> $currentLanguageTag, skip cached index.")
            return@withContext emptyList()
        }
        return@withContext filterBySource(cacheSnapshot.entries, enabledSources)
    }

    suspend fun fetchOnlineIndex(
        context: Context,
        forceRefresh: Boolean,
        enabledSources: Set<PluginIndexSource>,
        onIssue: ((PluginIndexFetchIssue) -> Unit)? = null,
    ): List<PluginIndexEntry> = withContext(Dispatchers.IO) {
        val currentLanguageTag = normalizeLanguageTag(Language.getPrefLanguage().getLocalCompatibleLanguageTag()) ?: "en"
        val cacheFile = resolveCacheFile(context)
        val cacheSnapshot = when {
            memoryCache != null && memoryFetchedSources != null -> CacheSnapshot(
                entries = memoryCache.orEmpty(),
                fetchedSources = memoryFetchedSources.orEmpty(),
                languageTag = memoryLanguageTag,
            )
            else -> readCacheSafely(cacheFile)?.also { parsed ->
                memoryCache = parsed.entries
                memoryFetchedSources = parsed.fetchedSources
                memoryLanguageTag = parsed.languageTag
            }
        }
        val cachedAll = cacheSnapshot?.entries
        val cachedFetchedSources = cacheSnapshot?.fetchedSources ?: memoryFetchedSources.orEmpty()
        val cachedLanguageTag = cacheSnapshot?.languageTag ?: memoryLanguageTag
        val hasLanguageMismatch = cachedAll != null && !isSameLanguageTag(cachedLanguageTag, currentLanguageTag)
        val now = System.currentTimeMillis()
        val githubAuthKey = githubAuthKeyOf(GitHubTokenStore.getToken(context))
        val hasValidatedGitHubToken = githubAuthKey != "anonymous" && validatedGitHubApiAuthKey == githubAuthKey
        val officialAbiGapRetryIntervalMs = if (hasValidatedGitHubToken) {
            AUTHENTICATED_OFFICIAL_ABI_GAP_RETRY_INTERVAL_MS
        } else {
            OFFICIAL_ABI_GAP_RETRY_INTERVAL_MS
        }
        val forceRefreshMinIntervalMs = if (hasValidatedGitHubToken) {
            AUTHENTICATED_FORCE_REFRESH_MIN_INTERVAL_MS
        } else {
            FORCE_REFRESH_MIN_INTERVAL_MS
        }

        if (lastNetworkFetchAttemptKey != githubAuthKey) {
            lastNetworkFetchAttemptKey = githubAuthKey
            lastNetworkFetchAttemptMs = 0L
        }

        if (!forceRefresh) {
            cachedAll?.let {
                val missingSources = enabledSources - cachedFetchedSources
                val hasOfficialAbiGaps = hasOfficialAbiGaps(it, enabledSources)
                if (missingSources.isEmpty() && !hasOfficialAbiGaps && !hasLanguageMismatch) {
                    return@withContext filterBySource(it, enabledSources)
                }
                if (missingSources.isEmpty() && hasOfficialAbiGaps && !hasLanguageMismatch && now - lastNetworkFetchAttemptMs < officialAbiGapRetryIntervalMs) {
                    Log.i(TAG, "Cache has official ABI gaps, but refresh attempted recently. Use cache.")
                    return@withContext filterBySource(it, enabledSources)
                }
                Log.i(TAG, "Cache missing sources $missingSources, try online fetch once.")
                if (missingSources.isEmpty() && hasOfficialAbiGaps) {
                    Log.i(TAG, "Cache has official entries with unknown ABI, try online fetch once.")
                }
                if (hasLanguageMismatch) {
                    Log.i(TAG, "Plugin index cache language changed: $cachedLanguageTag -> $currentLanguageTag, try online fetch once.")
                }
            }
        }

        if (forceRefresh && cachedAll != null && now - lastNetworkFetchAttemptMs < forceRefreshMinIntervalMs) {
            Log.i(TAG, "Skip too-frequent force refresh (<${forceRefreshMinIntervalMs / 1000}s), use cache.")
            PluginCenterProcessLogger.i("Skip online plugin index refresh: refresh interval is too short.")
            return@withContext filterBySource(cachedAll, enabledSources)
        }

        if (enabledSources.isEmpty()) {
            PluginCenterProcessLogger.i("Skip online plugin index refresh: no source enabled.")
            return@withContext emptyList()
        }

        lastNetworkFetchAttemptMs = now
        PluginCenterProcessLogger.i("Start online plugin index refresh: sources=${enabledSources.joinToString { it.name }}.")

        val cachedBySource = cachedAll.orEmpty().groupBy { it.source }
        val freshBySource = linkedMapOf<PluginIndexSource, List<PluginIndexEntry>>()
        val fetchedFromNetworkBySource = linkedMapOf<PluginIndexSource, Boolean>()

        if (PluginIndexSource.OFFICIAL in enabledSources) {
            val fetchResult = runCatching {
                fetchOfficialEntries(context, currentLanguageTag)
            }.onFailure { e ->
                val message = e.toIssueMessage()
                Log.w(TAG, "Failed to fetch official plugin index: $message")
                PluginCenterProcessLogger.i("Failed to fetch official plugin index: $message")
                onIssue?.invoke(PluginIndexFetchIssue(PluginIndexSource.OFFICIAL, message))
            }
            fetchedFromNetworkBySource[PluginIndexSource.OFFICIAL] = fetchResult.isSuccess
            val entriesFresh = fetchResult.getOrElse {
                cachedBySource[PluginIndexSource.OFFICIAL].orEmpty()
            }
            val entries = stabilizeEntriesWithCache(
                fresh = entriesFresh,
                cached = cachedBySource[PluginIndexSource.OFFICIAL].orEmpty(),
            )
            freshBySource[PluginIndexSource.OFFICIAL] = entries
        }

        if (PluginIndexSource.THIRD_PARTY in enabledSources) {
            val fetchResult = runCatching {
                fetchThirdPartyEntries(context, currentLanguageTag)
            }.onFailure { e ->
                val message = e.toIssueMessage()
                Log.w(TAG, "Failed to fetch third-party plugin index: $message")
                PluginCenterProcessLogger.i("Failed to fetch third-party plugin index: $message")
                onIssue?.invoke(PluginIndexFetchIssue(PluginIndexSource.THIRD_PARTY, message))
            }
            fetchedFromNetworkBySource[PluginIndexSource.THIRD_PARTY] = fetchResult.isSuccess
            val entriesFresh = fetchResult.getOrElse {
                cachedBySource[PluginIndexSource.THIRD_PARTY].orEmpty()
            }
            val entries = stabilizeEntriesWithCache(
                fresh = entriesFresh,
                cached = cachedBySource[PluginIndexSource.THIRD_PARTY].orEmpty(),
            )
            freshBySource[PluginIndexSource.THIRD_PARTY] = entries
        }

        val resultForSelected = freshBySource.values.flatten()
        val untouched = cachedAll.orEmpty().filterNot { it.source in enabledSources }
        val finalAll = (resultForSelected + untouched).distinctBy { "${it.source}:${it.packageName}" }
        val fetchedThisRound = enabledSources.filterTo(linkedSetOf()) { source ->
            fetchedFromNetworkBySource[source] == true || source in cachedFetchedSources
        }
        val finalFetchedSources = cachedFetchedSources + fetchedThisRound
        val allEnabledSourcesFetchedFromNetwork = enabledSources.all { fetchedFromNetworkBySource[it] == true }
        val finalLanguageTag = when {
            !hasLanguageMismatch -> currentLanguageTag
            allEnabledSourcesFetchedFromNetwork -> currentLanguageTag
            else -> cachedLanguageTag
        }

        memoryCache = finalAll
        memoryFetchedSources = finalFetchedSources
        memoryLanguageTag = finalLanguageTag
        writeCache(cacheFile, finalAll, finalFetchedSources, finalLanguageTag)
        PluginCenterProcessLogger.i("Online plugin index refresh finished: entries=${filterBySource(finalAll, enabledSources).size}.")

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

    private fun hasOfficialAbiGaps(
        entries: List<PluginIndexEntry>,
        enabledSources: Set<PluginIndexSource>,
    ): Boolean {
        if (PluginIndexSource.OFFICIAL !in enabledSources) return false
        return entries.any { it.source == PluginIndexSource.OFFICIAL && it.supportedAbis == null }
    }

    private fun stabilizeEntriesWithCache(
        fresh: List<PluginIndexEntry>,
        cached: List<PluginIndexEntry>,
    ): List<PluginIndexEntry> {
        if (fresh.isEmpty()) return cached
        if (cached.isEmpty()) return fresh
        val cachedByPackage = cached.associateBy { it.packageName }
        val mergedFresh = fresh.map { entry ->
            val cachedEntry = cachedByPackage[entry.packageName] ?: return@map entry
            val stableTitle = when {
                entry.title.isBlank() -> cachedEntry.title
                isLikelyVersionLike(entry.title) && cachedEntry.title.isNotBlank() -> cachedEntry.title
                else -> entry.title
            }
            entry.copy(
                title = stableTitle,
                iconUrl = entry.iconUrl ?: cachedEntry.iconUrl,
                description = entry.description ?: cachedEntry.description,
                author = entry.author ?: cachedEntry.author,
                instructionHardCoded = entry.instructionHardCoded ?: cachedEntry.instructionHardCoded,
                instructionMarkdownUrl = entry.instructionMarkdownUrl ?: cachedEntry.instructionMarkdownUrl,
                collaborators = entry.collaborators.ifEmpty { cachedEntry.collaborators },
                releases = entry.releases.ifEmpty { cachedEntry.releases },
                supportedAbis = entry.supportedAbis ?: cachedEntry.supportedAbis,
                tags = entry.tags.ifEmpty { cachedEntry.tags },
                requiresHostVersion = entry.requiresHostVersion ?: cachedEntry.requiresHostVersion,
                engine = entry.engine ?: cachedEntry.engine,
                variant = entry.variant ?: cachedEntry.variant,
                engineId = entry.engineId ?: cachedEntry.engineId,
            )
        }
        val freshPackages = mergedFresh.mapTo(linkedSetOf()) { it.packageName }
        val keepCachedMissing = cached.filterNot { it.packageName in freshPackages }
        return mergedFresh + keepCachedMissing
    }

    private fun Throwable.toIssueMessage(): String = message
        ?.takeIf { it.isNotBlank() }
        ?: javaClass.simpleName

    private fun fetchOfficialEntries(
        context: Context,
        languageTag: String,
    ): List<PluginIndexEntry> {
        val indexUrl = rawGithubUrl(
            owner = OFFICIAL_OWNER,
            repo = OFFICIAL_INDEX_REPO,
            branch = OFFICIAL_INDEX_BRANCH,
            path = OFFICIAL_GENERATED_INDEX_PATH,
        )
        PluginCenterProcessLogger.i("Fetch official generated plugin index: $indexUrl")
        return fetchGeneratedEntries(
            context = context,
            languageTag = languageTag,
            indexUrl = indexUrl,
            source = PluginIndexSource.OFFICIAL,
            fallbackTag = "official",
        )
    }

    private fun fetchThirdPartyEntries(
        context: Context,
        languageTag: String,
    ): List<PluginIndexEntry> {
        val indexUrl = rawGithubUrl(
            owner = THIRD_PARTY_OWNER,
            repo = THIRD_PARTY_REPO,
            branch = THIRD_PARTY_BRANCH,
            path = THIRD_PARTY_GENERATED_INDEX_PATH,
        )
        PluginCenterProcessLogger.i("Fetch third-party generated plugin index: $indexUrl")
        return fetchGeneratedEntries(
            context = context,
            languageTag = languageTag,
            indexUrl = indexUrl,
            source = PluginIndexSource.THIRD_PARTY,
            fallbackTag = "third-party",
        )
    }

    private fun fetchGeneratedEntries(
        context: Context,
        languageTag: String,
        indexUrl: String,
        source: PluginIndexSource,
        fallbackTag: String,
    ): List<PluginIndexEntry> {
        val content = getText(
            context = context,
            url = indexUrl,
            accept = "application/json",
        )
        return parseIndexEntriesJson(
            json = content,
            defaultSource = source,
            languageTag = languageTag,
        ).map { entry ->
            entry.copy(
                source = source,
                tags = entry.tags.ifEmpty { listOf(fallbackTag) },
            )
        }.also {
            PluginCenterProcessLogger.i("Parsed ${source.name.lowercase()} generated plugin index: entries=${it.size}.")
        }
    }

    private fun parseReleaseAssets(array: JSONArray?): List<ReleaseAsset> {
        if (array == null) return emptyList()
        val list = mutableListOf<ReleaseAsset>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val url = obj.optString("browser_download_url").takeIf { it.isNotBlank() }
                ?: obj.optString("downloadUrl").takeIf { it.isNotBlank() }
                ?: obj.optString("apkUrl").takeIf { it.isNotBlank() }
                ?: continue
            val size = obj.optLong("size", 0L).takeIf { it > 0 } ?: 0L
            val sha256 = obj.optString("digest")
                .takeIf { it.isNotBlank() }
                ?.substringAfter("sha256:", missingDelimiterValue = "")
                ?: obj.optString("sha256").takeIf { it.isNotBlank() }
                ?: obj.optString("apkSha256").takeIf { it.isNotBlank() }
                ?: ""
            val normalizedSha256 = sha256
                .ifBlank { null }
            list += ReleaseAsset(name = name, downloadUrl = url, size = size, sha256 = normalizedSha256)
        }
        return list
    }

    private fun parseSupportedAbisFromReleaseAssets(array: JSONArray?): List<String>? {
        if (array == null) return null
        var hasAnyAssetName = false
        val foundSpecific = linkedSetOf<String>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
            hasAnyAssetName = true
            val parsed = PluginAbiUtils.parseSupportedAbisFromText(name)
            foundSpecific += parsed.filterNot { it == PluginAbiUtils.ABI_UNIVERSAL }
        }
        if (!hasAnyAssetName) return null
        return PluginAbiUtils.normalizeAbiList(foundSpecific).takeIf { it.isNotEmpty() }
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

    private fun isLikelyVersionLike(value: String): Boolean {
        val text = value.trim()
        if (text.isBlank()) return false
        return Regex("""^[vV]?\d+(?:\.\d+){1,4}(?:[-+._][A-Za-z0-9]+)*$""").matches(text)
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

    private fun resolveCacheFile(context: Context): File {
        val persistentDir = File(context.filesDir, "plugin_center").apply { mkdirs() }
        val persistentFile = File(persistentDir, CACHE_FILE_NAME)
        if (!persistentFile.exists()) {
            val legacyCacheFile = File(context.cacheDir, CACHE_FILE_NAME)
            if (legacyCacheFile.exists() && legacyCacheFile.canRead()) {
                runCatching {
                    legacyCacheFile.copyTo(persistentFile, overwrite = true)
                }.onFailure {
                    Log.w(TAG, "Failed to migrate plugin index cache: ${it.message}")
                }
            }
        }
        return persistentFile
    }

    private fun getText(
        context: Context,
        url: String,
        accept: String = "*/*",
    ): String {
        val isGitHubApi = url.startsWith("https://api.github.com/")
        val githubToken = if (isGitHubApi) GitHubTokenStore.getToken(context) else null
        val githubAuthKey = githubAuthKeyOf(githubToken)
        if (isGitHubApi) {
            if (apiRateLimitCooldownKey != githubAuthKey) {
                apiRateLimitCooldownKey = githubAuthKey
                apiRateLimitCooldownUntilMs = 0L
            }
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
                githubToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
        }
        return try {
            conn.connect()
            val code = conn.responseCode
            if (isGitHubApi) {
                updateGitHubApiRateLimitCooldown(conn, code)
                updateGitHubApiTokenValidation(githubToken, githubAuthKey, code)
            }
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }?.trim().orEmpty()
                val shortErr = err.take(220)
                throw HttpStatusException(
                    code = code,
                    url = url,
                    message = if (shortErr.isBlank()) "HTTP $code for $url" else "HTTP $code for $url: $shortErr",
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

    private fun updateGitHubApiTokenValidation(token: String?, authKey: String, code: Int) {
        if (token == null) return
        when (code) {
            in 200..299 -> validatedGitHubApiAuthKey = authKey
            HttpURLConnection.HTTP_UNAUTHORIZED -> {
                if (validatedGitHubApiAuthKey == authKey) {
                    validatedGitHubApiAuthKey = null
                }
            }
        }
    }

    private fun readCacheSafely(file: File): CacheSnapshot? =
        runCatching {
            if (!file.exists() || !file.canRead()) return null
            val text = file.readText()
            parseCacheJson(text)
        }.getOrNull()

    private fun writeCache(
        file: File,
        entries: List<PluginIndexEntry>,
        fetchedSources: Set<PluginIndexSource>,
        languageTag: String?,
    ) {
        runCatching {
            val root = JSONObject()
            root.put("languageTag", languageTag ?: JSONObject.NULL)
            val items = JSONArray()
            entries.forEach { entry ->
                val obj = JSONObject()
                obj.put("packageName", entry.packageName)
                obj.put("iconUrl", entry.iconUrl?.toString())
                obj.put("nightIconUrl", entry.nightIconUrl?.toString())
                obj.put("forceIgnoreLocalIcon", entry.forceIgnoreLocalIcon)
                obj.put("title", entry.title)
                obj.put("description", entry.description)
                obj.put("instructionHardCoded", entry.instructionHardCoded)
                obj.put("instructionMarkdownUrl", entry.instructionMarkdownUrl)
                obj.put("author", entry.author)
                obj.put("collaborators", JSONArray(entry.collaborators))
                obj.put("engine", entry.engine)
                obj.put("variant", entry.variant)
                obj.put("engineId", entry.engineId)
                if (entry.supportedAbis == null) {
                    obj.put("supportedAbis", JSONObject.NULL)
                } else {
                    obj.put("supportedAbis", JSONArray(entry.supportedAbis))
                }
                obj.put("tags", JSONArray(entry.tags))
                obj.put("requiresHostVersion", entry.requiresHostVersion)
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
            root.put(
                "fetchedSources",
                JSONArray(
                    fetchedSources
                        .map { it.name }
                        .sorted(),
                ),
            )
            file.writeText(root.toString())
        }.onFailure {
            Log.w(TAG, "Failed to write plugin index cache: ${it.message}")
        }
    }

    private fun parseCacheJson(json: String): CacheSnapshot {
        val root = JSONObject(json)
        val languageTag = root.optString("languageTag")
            .takeIf { it.isNotBlank() && !it.equals("null", true) }
            ?.let(::normalizeLanguageTag)
        val list = parseIndexEntries(
            root = root,
            defaultSource = PluginIndexSource.OFFICIAL,
            languageTag = null,
        )
        val fetchedSources = root.optJSONArray("fetchedSources")
            ?.let { ja ->
                buildSet {
                    for (i in 0 until ja.length()) {
                        val name = ja.optString(i).takeIf { it.isNotBlank() } ?: continue
                        PluginIndexSource.entries.firstOrNull { it.name == name }?.let(::add)
                    }
                }
            }
            ?.takeIf { it.isNotEmpty() }
            ?: list.mapTo(linkedSetOf()) { it.source }

        return CacheSnapshot(
            entries = list,
            fetchedSources = fetchedSources,
            languageTag = languageTag,
        )
    }

    private fun parseIndexEntriesJson(
        json: String,
        defaultSource: PluginIndexSource,
        languageTag: String? = null,
    ): List<PluginIndexEntry> {
        return parseIndexEntries(
            root = JSONObject(json),
            defaultSource = defaultSource,
            languageTag = languageTag,
        )
    }

    private fun parseIndexEntries(
        root: JSONObject,
        defaultSource: PluginIndexSource,
        languageTag: String?,
    ): List<PluginIndexEntry> {
        val arr = root.optJSONArray("items") ?: root.optJSONArray("plugins") ?: JSONArray()
        val list = mutableListOf<PluginIndexEntry>()

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val packageName = obj.optString("packageName").takeIf { it.isNotBlank() } ?: continue
            val title = obj.optString("title").takeIf { it.isNotBlank() } ?: packageName
            val description = selectLocalizedString(
                obj = obj,
                scalarKey = "description",
                localizedKey = "localizedDescriptions",
                languageTag = languageTag,
                keyCandidates = languageTag?.let(::languageTagToAndroidResourceDirs).orEmpty(),
            )
            val instructionHardCoded = selectLocalizedString(
                obj = obj,
                scalarKey = "instructionHardCoded",
                localizedKey = "localizedInstructionHardCoded",
                languageTag = languageTag,
                keyCandidates = languageTag?.let(::languageTagToAndroidResourceDirs).orEmpty(),
            )
            val instructionMarkdownUrl = selectLocalizedString(
                obj = obj,
                scalarKey = "instructionMarkdownUrl",
                localizedKey = "localizedInstructionMarkdownUrls",
                languageTag = languageTag,
                keyCandidates = languageTag
                    ?.let(::languageTagToAndroidResourceDirs)
                    ?.map { it.replaceFirst("values", "raw") }
                    .orEmpty(),
            )
            val author = obj.optString("author").takeIf { it.isNotBlank() && !it.equals("null", true) }
            val iconUrl = obj.optString("iconUrl").takeIf { it.isNotBlank() }?.toUri()
            val nightIconUrl = obj.optString("nightIconUrl").takeIf { it.isNotBlank() }?.toUri()
            val forceIgnoreLocalIcon = obj.optBoolean("forceIgnoreLocalIcon", false)
            val engine = obj.optString("engine").takeIf { it.isNotBlank() && !it.equals("null", true) }
            val variant = obj.optString("variant").takeIf { it.isNotBlank() && !it.equals("null", true) }
            val engineId = obj.optString("engineId").takeIf { it.isNotBlank() && !it.equals("null", true) }
            val requiresHostVersion = obj.optLong("requiresHostVersion", -1L).takeIf { it > 0 }
            val source = obj.optString("source")
                .takeIf { it.isNotBlank() }
                ?.runCatching { PluginIndexSource.valueOf(this) }
                ?.getOrNull()
                ?: defaultSource

            val collaborators = obj.optJSONArray("collaborators")?.let { ja ->
                List(ja.length()) { idx -> ja.optString(idx) }.filter { it.isNotBlank() }
            } ?: emptyList()
            val releaseArray = obj.optJSONArray("releases")
            val supportedAbisFromAssets = releaseArray?.firstReleaseAssetsOrNull()
                ?.let(::parseSupportedAbisFromReleaseAssets)
            val supportedAbis = when {
                !obj.has("supportedAbis") || obj.isNull("supportedAbis") -> null
                else -> PluginAbiUtils.resolveSupportedAbisFromValue(obj.opt("supportedAbis"))
            } ?: supportedAbisFromAssets
            val tags = obj.optJSONArray("tags")?.let { ja ->
                List(ja.length()) { idx -> ja.optString(idx) }.filter { it.isNotBlank() }
            } ?: emptyList()

            val releases = releaseArray?.let { ja ->
                buildList {
                    for (j in 0 until ja.length()) {
                        val rel = ja.optJSONObject(j) ?: continue
                        val selectedAsset = pickAssetByAbiOrFallback(parseReleaseAssets(rel.optJSONArray("assets")))
                        add(
                            PluginIndexRelease(
                                versionName = rel.optString("versionName", "0.0.0"),
                                versionCode = rel.optLong("versionCode", 0L),
                                versionDate = rel.optString("versionDate").takeIf { it.isNotBlank() },
                                apkUrl = selectedAsset?.downloadUrl ?: rel.optString("apkUrl").takeIf { it.isNotBlank() },
                                apkSha256 = selectedAsset?.sha256 ?: rel.optString("apkSha256").takeIf { it.isNotBlank() },
                                apkSizeBytes = selectedAsset?.size ?: rel.optLong("apkSizeBytes", -1L).takeIf { it > 0 },
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
                nightIconUrl = nightIconUrl,
                forceIgnoreLocalIcon = forceIgnoreLocalIcon,
                title = title,
                description = description,
                instructionHardCoded = instructionHardCoded,
                instructionMarkdownUrl = instructionMarkdownUrl,
                author = author,
                collaborators = collaborators,
                engine = engine,
                variant = variant,
                engineId = engineId,
                releases = releases,
                supportedAbis = supportedAbis,
                tags = tags,
                requiresHostVersion = requiresHostVersion,
                source = source,
            )
        }
        return list
    }

    private fun selectLocalizedString(
        obj: JSONObject,
        scalarKey: String,
        localizedKey: String,
        languageTag: String?,
        keyCandidates: List<String>,
    ): String? {
        val candidates = if (languageTag == null) {
            emptyList()
        } else {
            (keyCandidates + normalizeLanguageTag(languageTag).orEmpty()).filter { it.isNotBlank() }.distinct()
        }

        obj.optJSONObject(localizedKey)
            ?.let { localized -> selectStringFromObject(localized, candidates) }
            ?.let { return it }

        return when (val raw = obj.opt(scalarKey)) {
            is JSONObject -> selectStringFromObject(raw, candidates)
            else -> raw.asJsonStringOrNull()
        }
    }

    private fun selectStringFromObject(
        obj: JSONObject,
        candidates: List<String>,
    ): String? {
        candidates.forEach { key ->
            obj.opt(key).asJsonStringOrNull()?.let { return it }
        }
        return obj.opt("default").asJsonStringOrNull()
            ?: obj.opt("values").asJsonStringOrNull()
            ?: obj.opt("en").asJsonStringOrNull()
    }

    private fun Any?.asJsonStringOrNull(): String? {
        if (this == null || this == JSONObject.NULL) return null
        return toString()
            .trim()
            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }

    private fun JSONArray.firstReleaseAssetsOrNull(): JSONArray? {
        for (i in 0 until length()) {
            val assets = optJSONObject(i)?.optJSONArray("assets") ?: continue
            if (assets.length() > 0) return assets
        }
        return null
    }

    private fun normalizeLanguageTag(tag: String?): String? {
        return tag
            ?.trim()
            ?.replace('_', '-')
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
    }

    private fun isSameLanguageTag(cached: String?, current: String?): Boolean {
        return normalizeLanguageTag(cached) == normalizeLanguageTag(current)
    }

    private data class ReleaseAsset(
        val name: String,
        val downloadUrl: String,
        val size: Long,
        val sha256: String?,
    )

    private class HttpStatusException(
        val code: Int,
        val url: String,
        message: String,
    ) : IllegalStateException(message)

    private data class CacheSnapshot(
        val entries: List<PluginIndexEntry>,
        val fetchedSources: Set<PluginIndexSource>,
        val languageTag: String?,
    )
}
