package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.optArray
import org.autojs.autojs.core.pref.optBoolean
import org.autojs.autojs.core.pref.optLong
import org.autojs.autojs.core.pref.optObject
import org.autojs.autojs.core.pref.optString
import org.autojs.autojs.core.pref.putOrRemove
import org.autojs.autojs.network.GitHubTokenStore
import org.autojs.autojs.util.AndroidUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
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
        // The JSON file store `JsonFileStores.PLUGIN_INDEX`, plain values of `SettingsJson` (settings P5.1 / P5.3, encodeCache / decodeCache).
        private const val CACHE_FILE_NAME = "autojs6_plugin_index_v11.json"

        private const val OFFICIAL_OWNER = "SuperMonster003"
        private const val OFFICIAL_INDEX_REPO = "AutoJs6-Official-Plugins-Index"
        private const val OFFICIAL_INDEX_BRANCH = "main"
        private const val OFFICIAL_GENERATED_INDEX_PATH = "plugins.official.generated.json"
        private const val APK_BUILDER_REPO = "AutoJs6-Plugin-APK-Builder-Template"
        private const val APK_BUILDER_MATRIX_BRANCH = "master"
        private const val APK_BUILDER_MATRIX_PATH = "compat-matrix.json"
        private const val THIRD_PARTY_OWNER = "SuperMonster003"
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

        private fun normalizeLanguageTag(tag: String?): String? {
            return tag
                ?.trim()
                ?.replace('_', '-')
                ?.lowercase()
                ?.takeIf { it.isNotBlank() }
        }

        /**
         * Writes the local cache as plain values of `SettingsJson` (settings P5.3), with the members and the member order
         * the cache has always had: a member whose value is null is left out (org.json's `put` with null), except
         * `languageTag` and `supportedAbis`, which are JSON nulls.
         *
         * zh-CN: 以 `SettingsJson` 普通值写出本地缓存 (设置 P5.3), 成员及其顺序与缓存一直以来的写法相同: 值为 null 的成员省略
         * (即 org.json 的 `put` 传入 null), 只有 `languageTag` 与 `supportedAbis` 写为 JSON null.
         */
        internal fun encodeCache(snapshot: CacheSnapshot): String = SettingsJson.stringify(
            linkedMapOf<String, Any?>(
                "fetchedAtMs" to snapshot.fetchedAtMs.mapKeys { (source, _) -> source.name },
                "languageTag" to snapshot.languageTag,
                "items" to snapshot.entries.map { cacheItem(it) },
                "fetchedSources" to snapshot.fetchedSources.map { it.name }.sorted(),
            ),
        )

        /**
         * Reads a local cache ([encodeCache]) into the entries that the index parser read from it before P5.3, which had
         * no language for the cache (the cache keeps the texts of the language it was fetched in) and no release assets
         * to look at (the cache keeps the selected APK of each release). A malformed cache throws
         * [IllegalArgumentException] like a malformed index does; [uri] turns the icon addresses into [Uri]s.
         *
         * zh-CN: 把本地缓存 ([encodeCache]) 读为 P5.3 之前索引解析器从中读出的条目: 解析缓存时不带语言 (缓存保存的是获取时那种语言的
         * 文本), 也没有发行版资源可看 (缓存保存的是每个发行版选定的 APK). 缓存格式错误时与索引格式错误一样抛
         * [IllegalArgumentException]; [uri] 把图标地址转为 [Uri].
         */
        internal fun decodeCache(text: String, uri: (String) -> Uri? = { it.toUri() }): CacheSnapshot {
            val root = SettingsJson.parseMap(text)
            val items = root.optArray("items")
                ?: throw IllegalArgumentException("Plugin index cache must contain an items array")
            // A malformed cache must not masquerade as an authoritative empty source.
            val objects = items.mapIndexed { i, item ->
                val obj = cacheObject(item) ?: throw IllegalArgumentException("Plugin index cache item $i is not an object")
                obj.also { require(it.optString("packageName").isNotBlank()) { "Plugin index cache item $i has no package name" } }
            }
            val entries = objects.map { cachedEntry(it, uri) }
            val fetchedSources = root.optArray("fetchedSources")
                ?.mapNotNullTo(linkedSetOf<PluginIndexSource>()) { name -> PluginIndexSource.entries.firstOrNull { it.name == name } }
                ?.takeIf { it.isNotEmpty() }
                ?: entries.mapTo(linkedSetOf()) { it.source }
            return CacheSnapshot(
                entries = entries,
                fetchedSources = fetchedSources,
                languageTag = root.optString("languageTag")
                    .takeIf { it.isNotBlank() && !it.equals("null", true) }
                    ?.let { normalizeLanguageTag(it) },
                fetchedAtMs = root.optObject("fetchedAtMs")?.let { times ->
                    PluginIndexSource.entries.mapNotNull { source ->
                        times.optLong(source.name, 0L).takeIf { it > 0 }?.let { source to it }
                    }.toMap()
                }.orEmpty(),
            )
        }

        private fun cacheItem(entry: PluginIndexEntry): Map<String, Any?> = LinkedHashMap<String, Any?>().apply {
            put("packageName", entry.packageName)
            putOrRemove("iconUrl", entry.iconUrl?.toString())
            putOrRemove("nightIconUrl", entry.nightIconUrl?.toString())
            put("forceIgnoreLocalIcon", entry.forceIgnoreLocalIcon)
            putOrRemove("iconBackgroundColor", entry.iconBackgroundColor)
            putOrRemove("nightIconBackgroundColor", entry.nightIconBackgroundColor)
            put("title", entry.title)
            putOrRemove("description", entry.description)
            putOrRemove("instructionHardCoded", entry.instructionHardCoded)
            putOrRemove("instructionMarkdownUrl", entry.instructionMarkdownUrl)
            putOrRemove("author", entry.author)
            put("collaborators", ArrayList(entry.collaborators))
            putOrRemove("engine", entry.engine)
            putOrRemove("variant", entry.variant)
            putOrRemove("engineId", entry.engineId)
            put("featured", entry.isFeatured)
            put("supportedAbis", entry.supportedAbis?.let { ArrayList(it) })
            put("tags", ArrayList(entry.tags))
            putOrRemove("requiresHostVersion", entry.requiresHostVersion)
            putOrRemove("nativePageAlignment", entry.nativePageAlignment)
            putOrRemove("nativePageAlignmentSource", entry.nativePageAlignmentSource)
            put("source", entry.source.name)
            put("releases", entry.releases.map { cacheRelease(it) })
        }

        private fun cacheRelease(release: PluginIndexRelease): Map<String, Any?> = LinkedHashMap<String, Any?>().apply {
            put("versionName", release.versionName)
            put("versionCode", release.versionCode)
            putOrRemove("versionDate", release.versionDate)
            putOrRemove("apkUrl", release.apkUrl)
            putOrRemove("apkSha256", release.apkSha256)
            putOrRemove("apkSizeBytes", release.apkSizeBytes)
            putOrRemove("changelogUrl", release.changelogUrl)
            putOrRemove("changelogText", release.changelogText)
            put("compatibilityMatrixMatched", release.compatibilityMatrixMatched)
            putOrRemove("nativePageAlignment", release.nativePageAlignment)
            putOrRemove("nativePageAlignmentSource", release.nativePageAlignmentSource)
        }

        // Each member is read as `parseIndexEntries` reads it with no language: the same defaults, trimming and checks.
        private fun cachedEntry(item: Map<String, Any?>, uri: (String) -> Uri?): PluginIndexEntry {
            val packageName = item.optString("packageName")
            val releases = item.optArray("releases").orEmpty().mapNotNull { release -> cacheObject(release)?.let { cachedRelease(it) } }
            val nativePageAlignment = PluginNativeAlignmentPolicy.parse(item["nativePageAlignment"])
            val nativePageAlignmentSource = item.optString("nativePageAlignmentSource").takeIf { it in setOf("measured", "declared") }
            // Unknown ABIs are unrestricted only after a measured absence of native code in the latest release (or in the
            // entry when it has no release), like the index parser decides it; the cache has no release assets.
            val latestRelease = releases.maxByOrNull { it.versionCode }
            val hasNoNativeCode = if (latestRelease != null) {
                latestRelease.nativePageAlignment == 0L && latestRelease.nativePageAlignmentSource == "measured"
            } else {
                nativePageAlignment == 0L && nativePageAlignmentSource == "measured"
            }
            return PluginIndexEntry(
                packageName = packageName,
                iconUrl = item.optString("iconUrl").takeIf { it.isNotBlank() }?.let(uri),
                nightIconUrl = item.optString("nightIconUrl").takeIf { it.isNotBlank() }?.let(uri),
                forceIgnoreLocalIcon = item.optBoolean("forceIgnoreLocalIcon", false),
                iconBackgroundColor = PluginIconBackgroundPolicy.normalized(item.optString("iconBackgroundColor")),
                nightIconBackgroundColor = PluginIconBackgroundPolicy.normalized(item.optString("nightIconBackgroundColor")),
                title = item.optString("title").takeIf { it.isNotBlank() } ?: packageName,
                description = cachedText(item, "description"),
                instructionHardCoded = cachedText(item, "instructionHardCoded"),
                instructionMarkdownUrl = cachedText(item, "instructionMarkdownUrl"),
                author = cachedName(item, "author"),
                collaborators = cachedStrings(item.optArray("collaborators")),
                engine = cachedName(item, "engine"),
                variant = cachedName(item, "variant"),
                engineId = cachedName(item, "engineId"),
                isFeatured = item.optBoolean("featured", true),
                releases = releases,
                supportedAbis = item["supportedAbis"]?.let { PluginAbiUtils.resolveSupportedAbisFromValue(it) }
                    ?: emptyList<String>().takeIf { hasNoNativeCode },
                tags = cachedStrings(item.optArray("tags")),
                requiresHostVersion = item.optLong("requiresHostVersion", -1L).takeIf { it > 0 },
                nativePageAlignment = nativePageAlignment,
                nativePageAlignmentSource = nativePageAlignmentSource,
                source = item.optString("source").let { name -> PluginIndexSource.entries.firstOrNull { it.name == name } }
                    ?: PluginIndexSource.OFFICIAL,
            )
        }

        private fun cachedRelease(release: Map<String, Any?>) = PluginIndexRelease(
            versionName = release.optString("versionName", "0.0.0"),
            versionCode = release.optLong("versionCode", 0L),
            versionDate = release.optString("versionDate").takeIf { it.isNotBlank() },
            apkUrl = release.optString("apkUrl").takeIf { it.isNotBlank() },
            apkSha256 = release.optString("apkSha256").takeIf { it.isNotBlank() },
            apkSizeBytes = release.optLong("apkSizeBytes", -1L).takeIf { it > 0 },
            changelogUrl = release.optString("changelogUrl").takeIf { it.isNotBlank() },
            changelogText = release.optString("changelogText").takeIf { it.isNotBlank() },
            compatibilityMatrixMatched = release.optBoolean("compatibilityMatrixMatched", false),
            nativePageAlignment = PluginNativeAlignmentPolicy.parse(release["nativePageAlignment"]),
            nativePageAlignmentSource = release.optString("nativePageAlignmentSource").takeIf { it in setOf("measured", "declared") },
        )

        @Suppress("UNCHECKED_CAST")
        private fun cacheObject(value: Any?): Map<String, Any?>? = value as? Map<String, Any?>

        // A localized text of the parser (`asJsonStringOrNull`): trimmed, and neither blank nor the text "null".
        private fun cachedText(item: Map<String, Any?>, key: String): String? =
            item.optString(key).trim().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

        private fun cachedName(item: Map<String, Any?>, key: String): String? =
            item.optString(key).takeIf { it.isNotBlank() && !it.equals("null", true) }

        // Android's `JSONArray.optString`: a JSON null is the text "null", another value its JSON text; blanks are dropped.
        private fun cachedStrings(array: List<Any?>?): List<String> =
            array.orEmpty().map { it as? String ?: SettingsJson.stringify(it) }.filter { it.isNotBlank() }
    }

    // Entries, source membership, locale and freshness always travel together.
    @Volatile
    private var memorySnapshot: CacheSnapshot? = null

    suspend fun getCachedIndex(
        context: Context,
        enabledSources: Set<PluginIndexSource>,
    ): List<PluginIndexEntry> = withContext(Dispatchers.IO) {
        if (enabledSources.isEmpty()) {
            return@withContext emptyList()
        }
        val currentLanguageTag = normalizeLanguageTag(Language.getPrefLanguage().getLocalCompatibleLanguageTag()) ?: "en"
        val cacheFile = resolveCacheFile(context)
        val cacheSnapshot = memorySnapshot ?: readCacheSafely(cacheFile)?.also { memorySnapshot = it }
            ?: return@withContext emptyList()
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
        val cacheSnapshot = memorySnapshot ?: readCacheSafely(cacheFile)?.also { memorySnapshot = it }
        val cachedAll = cacheSnapshot?.entries
        val cachedFetchedSources = cacheSnapshot?.fetchedSources.orEmpty()
        val cachedLanguageTag = cacheSnapshot?.languageTag
        val cachedFetchedAtMs = cacheSnapshot?.fetchedAtMs.orEmpty()
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
                val sourcesFresh = enabledSources.all { source -> PluginIndexCachePolicy.isFresh(cachedFetchedAtMs[source], now) }
                if (missingSources.isEmpty() && !hasOfficialAbiGaps && !hasLanguageMismatch && sourcesFresh) {
                    return@withContext filterBySource(it, enabledSources)
                }
                if (missingSources.isEmpty() && !hasLanguageMismatch &&
                    PluginIndexCachePolicy.wasAttemptRecent(lastNetworkFetchAttemptMs, now, officialAbiGapRetryIntervalMs)) {
                    Log.i(TAG, "Index cache needs refresh, but a refresh was attempted recently. Use the last good snapshot.")
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

        if (forceRefresh && cachedAll != null &&
            PluginIndexCachePolicy.wasAttemptRecent(lastNetworkFetchAttemptMs, now, forceRefreshMinIntervalMs)) {
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
            fetchResult.onSuccess { entries -> freshBySource[PluginIndexSource.OFFICIAL] = entries }
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
            fetchResult.onSuccess { entries -> freshBySource[PluginIndexSource.THIRD_PARTY] = entries }
        }

        // Only successful responses replace a source. Removed entries must not be
        // appended back from cache; failed/disabled sources retain their snapshot.
        val finalAll = PluginIndexCachePolicy.replaceSuccessfulSources(
            cachedAll.orEmpty(), freshBySource, PluginIndexEntry::source,
        ).distinctBy { "${it.source}:${it.packageName}" }
        val finalFetchedAtMs = cachedFetchedAtMs + freshBySource.keys.associateWith { now }
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

        memorySnapshot = CacheSnapshot(finalAll, finalFetchedSources, finalLanguageTag, finalFetchedAtMs)
        writeCache(cacheFile, finalAll, finalFetchedSources, finalLanguageTag, finalFetchedAtMs)
        PluginCenterProcessLogger.i("Online plugin index refresh finished: entries=${filterBySource(finalAll, enabledSources).size}.")

        return@withContext filterBySource(finalAll, enabledSources)
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
        val entries = fetchGeneratedEntries(
            context = context,
            languageTag = languageTag,
            indexUrl = indexUrl,
            source = PluginIndexSource.OFFICIAL,
            fallbackTag = "official",
        )
        return overlayApkBuilderCompatibilityMatrix(context, entries)
    }

    private fun overlayApkBuilderCompatibilityMatrix(
        context: Context,
        entries: List<PluginIndexEntry>,
    ): List<PluginIndexEntry> {
        val targetIndex = entries.indexOfFirst {
            it.packageName == ApkBuilderTemplatePluginIds.PACKAGE_NAME
        }
        if (targetIndex < 0) return entries

        val matrixUrl = rawGithubUrl(
            owner = OFFICIAL_OWNER,
            repo = APK_BUILDER_REPO,
            branch = APK_BUILDER_MATRIX_BRANCH,
            path = APK_BUILDER_MATRIX_PATH,
        )
        val resolutionResult = runCatching {
            val matrixJson = getText(
                context = context,
                url = matrixUrl,
                accept = "application/json",
                connectTimeoutMs = 6_000,
                readTimeoutMs = 8_000,
            )
            ApkBuilderCompatibilityMatrixResolver.resolve(
                json = matrixJson,
                hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
                deviceAbis = AndroidUtils.deviceFilteredAbiList,
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to resolve APK Builder compatibility matrix; use release index.", error)
            PluginCenterProcessLogger.i(
                "APK Builder compatibility matrix unavailable; use release-index fallback: " +
                        (error.message ?: error.javaClass.simpleName) + ".",
            )
        }
        if (resolutionResult.isFailure) return entries
        val resolution = resolutionResult.getOrNull()

        if (resolution == null) {
            PluginCenterProcessLogger.i(
                "APK Builder compatibility matrix has no usable match for host " +
                        "${BuildConfig.VERSION_CODE}; use release-index fallback.",
            )
            return entries
        }

        val targetEntry = entries[targetIndex]
        val indexedRelease = targetEntry.releases.firstOrNull {
            it.versionCode == resolution.release.versionCode
        }
        val resolvedRelease = resolution.release.copy(
            versionDate = resolution.release.versionDate ?: indexedRelease?.versionDate,
            changelogUrl = indexedRelease?.changelogUrl,
            changelogText = indexedRelease?.changelogText,
        )
        val overlaid = targetEntry.copy(
            releases = listOf(resolvedRelease),
            supportedAbis = resolution.supportedAbis,
        )
        PluginCenterProcessLogger.i(
            "APK Builder compatibility matrix matched host ${BuildConfig.VERSION_CODE}: " +
                    "plugin=${resolvedRelease.versionCode}, variant=${resolution.artifactVariant}.",
        )
        return entries.toMutableList().apply { this[targetIndex] = overlaid }
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
            assets.firstOrNull { PluginAbiUtils.assetMatchesAbi(it.name, abi) }?.let { return it }
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
        connectTimeoutMs: Int = 12_000,
        readTimeoutMs: Int = 15_000,
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
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            // This repository owns freshness and offline fallback. Once it elects
            // to fetch, revalidate mutable branch metadata with HTTP caches too.
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
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
            val text = AtomicFile(file).openRead().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            decodeCache(text)
        }.getOrNull()

    private fun writeCache(
        file: File,
        entries: List<PluginIndexEntry>,
        fetchedSources: Set<PluginIndexSource>,
        languageTag: String?,
        fetchedAtMs: Map<PluginIndexSource, Long>,
    ) {
        runCatching {
            val text = encodeCache(CacheSnapshot(entries, fetchedSources, languageTag, fetchedAtMs))
            val atomic = AtomicFile(file)
            val stream = atomic.startWrite()
            try {
                stream.write(text.toByteArray(StandardCharsets.UTF_8))
                atomic.finishWrite(stream)
            } catch (failure: Throwable) {
                atomic.failWrite(stream)
                throw failure
            }
        }.onFailure {
            Log.w(TAG, "Failed to write plugin index cache: ${it.message}")
        }
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
        val arr = root.optJSONArray("items") ?: root.optJSONArray("plugins")
            ?: throw IllegalArgumentException("Plugin index must contain an items or plugins array")
        // A malformed response must not masquerade as an authoritative empty source.
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i)
                ?: throw IllegalArgumentException("Plugin index item $i is not an object")
            require(item.optString("packageName").isNotBlank()) { "Plugin index item $i has no package name" }
        }
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
            val isFeatured = when {
                obj.has("featured") -> obj.optBoolean("featured", true)
                obj.has("isFeatured") -> obj.optBoolean("isFeatured", true)
                else -> true
            }
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
            val latestRelease = releaseArray?.latestReleaseOrNull()
            val supportedAbisFromAssets = latestRelease?.optJSONArray("assets")
                ?.let(::parseSupportedAbisFromReleaseAssets)
            // Older official indexes use null for ABI-independent plugins. Only a measured
            // absence of native code confirms unrestricted ABIs; a universal filename cannot.
            // zh-CN: 旧官方索引对无 ABI 限制的插件使用 null. 仅实测无原生代码可确认不受限制,
            // 不能仅凭 universal 文件名推断, 也不能沿用旧发行版的结果.
            val alignmentMetadata = latestRelease ?: obj
            val hasNoNativeCode = PluginNativeAlignmentPolicy.parse(alignmentMetadata.opt("nativePageAlignment")) == 0L &&
                    alignmentMetadata.optString("nativePageAlignmentSource") == "measured"
            val supportedAbis = when {
                !obj.has("supportedAbis") || obj.isNull("supportedAbis") -> null
                else -> PluginAbiUtils.resolveSupportedAbisFromValue(obj.opt("supportedAbis"))
            } ?: supportedAbisFromAssets ?: emptyList<String>().takeIf { hasNoNativeCode }
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
                                compatibilityMatrixMatched = rel.optBoolean("compatibilityMatrixMatched", false),
                                nativePageAlignment = PluginNativeAlignmentPolicy.parse(rel.opt("nativePageAlignment")),
                                nativePageAlignmentSource = rel.optString("nativePageAlignmentSource").takeIf { it in setOf("measured", "declared") },
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
                iconBackgroundColor = PluginIconBackgroundPolicy.normalized(obj.optString("iconBackgroundColor")),
                nightIconBackgroundColor = PluginIconBackgroundPolicy.normalized(obj.optString("nightIconBackgroundColor")),
                title = title,
                description = description,
                instructionHardCoded = instructionHardCoded,
                instructionMarkdownUrl = instructionMarkdownUrl,
                author = author,
                collaborators = collaborators,
                engine = engine,
                variant = variant,
                engineId = engineId,
                isFeatured = isFeatured,
                releases = releases,
                supportedAbis = supportedAbis,
                tags = tags,
                requiresHostVersion = requiresHostVersion,
                nativePageAlignment = PluginNativeAlignmentPolicy.parse(obj.opt("nativePageAlignment")),
                nativePageAlignmentSource = obj.optString("nativePageAlignmentSource").takeIf { it in setOf("measured", "declared") },
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

        val raw: Any? = obj.opt(scalarKey)
        return when (raw) {
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

    private fun JSONArray.latestReleaseOrNull(): JSONObject? =
        (0 until length()).mapNotNull { optJSONObject(it) }.maxByOrNull { it.optLong("versionCode", 0L) }

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

    internal data class CacheSnapshot(
        val entries: List<PluginIndexEntry>,
        val fetchedSources: Set<PluginIndexSource>,
        val languageTag: String?,
        val fetchedAtMs: Map<PluginIndexSource, Long>,
    )
}
