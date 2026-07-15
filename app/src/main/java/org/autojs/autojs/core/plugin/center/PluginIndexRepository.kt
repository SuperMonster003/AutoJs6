package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.network.GitHubTokenStore
import org.autojs.autojs.util.AndroidUtils
import org.autojs.autojs.util.StringUtils.truncateForSafeBinder
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.math.max

@Suppress("SameParameterValue")
class PluginIndexRepository {

    companion object {
        private const val TAG = "PluginIndexRepository"
        private const val CACHE_FILE_NAME = "autojs6_plugin_index_v8.json"

        private const val OFFICIAL_OWNER = "SuperMonster003"
        private const val OFFICIAL_REPO_PREFIX = "AutoJs6-Plugin-"
        private const val THIRD_PARTY_OWNER = "SuperMonster002"
        private const val THIRD_PARTY_REPO = "Auto.js-SDK-Plugins-Garage"
        private const val THIRD_PARTY_BRANCH = "main"

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
            return@withContext filterBySource(cachedAll, enabledSources)
        }

        if (enabledSources.isEmpty()) {
            return@withContext emptyList()
        }

        lastNetworkFetchAttemptMs = now

        val cachedBySource = cachedAll.orEmpty().groupBy { it.source }
        val freshBySource = linkedMapOf<PluginIndexSource, List<PluginIndexEntry>>()
        val fetchedFromNetworkBySource = linkedMapOf<PluginIndexSource, Boolean>()

        if (PluginIndexSource.OFFICIAL in enabledSources) {
            val fetchResult = runCatching {
                fetchOfficialEntries(context, currentLanguageTag)
            }.onFailure { e ->
                Log.w(TAG, "Failed to fetch official plugin index: ${e.message}")
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
                Log.w(TAG, "Failed to fetch third-party plugin index: ${e.message}")
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

    private fun fetchOfficialEntries(
        context: Context,
        languageTag: String,
    ): List<PluginIndexEntry> {
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
    ): PluginIndexEntry {
        val releaseJson = getJsonObject(
            context = context,
            url = "https://api.github.com/repos/${plugin.owner}/${plugin.repo}/releases/latest",
        )
        val releaseAssetsJson = releaseJson.optJSONArray("assets")
        val releaseAssets = parseReleaseAssets(releaseAssetsJson)
        val supportedAbis = parseSupportedAbisFromReleaseAssets(releaseAssetsJson)
        val selectedAsset = pickAssetByAbiOrFallback(releaseAssets)
        val releaseBody = releaseJson.optString("body").takeIf { it.isNotBlank() }
        val publishedDate = releaseJson.optString("published_at").takeIf { it.length >= 10 }?.substring(0, 10)
        val releaseAuthor = releaseJson.optJSONObject("author")?.optString("login")?.takeIf { it.isNotBlank() }
        val releaseName = releaseJson.optString("name").substringBefore(" @").trim()
        val releaseTag = releaseJson.optString("tag_name").takeIf { it.isNotBlank() }

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

        val repoTreePaths = runCatching {
            fetchRepoTreePaths(
                context = context,
                owner = plugin.owner,
                repo = plugin.repo,
                branch = plugin.branch,
            )
        }.onFailure {
            Log.w(TAG, "Failed to fetch tree for ${plugin.repo}: ${it.message}")
        }.getOrElse { emptySet() }

        val unknown = context.getString(R.string.text_unknown)
        val packageName = parseManifestPackageName(manifestText)
            ?: parseApplicationIdFromBuildGradle(buildGradleText)
            ?: "unknown.${plugin.repo.lowercase().replace(Regex("[^a-z0-9._-]"), "-")}"

        val manifestLabel = parseManifestApplicationLabel(manifestText)
        val title = resolveStringResourceReference(
            context = context,
            owner = plugin.owner,
            repo = plugin.repo,
            branch = plugin.branch,
            languageTag = languageTag,
            treePaths = repoTreePaths,
            value = manifestLabel,
        ) ?: manifestLabel?.takeIf { !it.startsWith("@") }
            ?: plugin.repo.removePrefix(OFFICIAL_REPO_PREFIX).takeIf { it.isNotBlank() }
            ?: releaseName.takeIf { it.isNotBlank() && !isLikelyVersionLike(it) }
            ?: releaseName.takeIf { it.isNotBlank() }
            ?: unknown

        val iconRef = parseManifestApplicationIcon(manifestText)
        val iconPath = resolveIconPath(iconRef, repoTreePaths)
            ?: resolveIconPathWithoutTreeProbe(
                context = context,
                owner = plugin.owner,
                repo = plugin.repo,
                branch = plugin.branch,
                iconRef = iconRef,
            )
            ?: resolveOfficialFallbackIconPath(repoTreePaths)
        val nightIconPath = resolveNightIconPath(iconRef, repoTreePaths)
        val iconUrl = iconPath?.let {
            rawGithubUrl(
                owner = plugin.owner,
                repo = plugin.repo,
                branch = plugin.branch,
                path = it,
            ).toUri()
        }
        val nightIconUrl = nightIconPath?.let {
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
            treePaths = repoTreePaths,
        ) ?: unknown

        val instructionHardCoded = resolveOfficialInstructionHardCoded(
            context = context,
            owner = plugin.owner,
            repo = plugin.repo,
            branch = plugin.branch,
            languageTag = languageTag,
            treePaths = repoTreePaths,
        )?.truncateForSafeBinder()
        val instructionMarkdownPath = resolveOfficialInstructionMarkdownPath(languageTag, repoTreePaths)
        val instructionMarkdownUrl = instructionMarkdownPath?.let {
            rawGithubUrl(
                owner = plugin.owner,
                repo = plugin.repo,
                branch = plugin.branch,
                path = it,
            )
        }

        val manifestAuthor = parseManifestAuthor(manifestText)
        val author = resolveStringResourceReference(
            context = context,
            owner = plugin.owner,
            repo = plugin.repo,
            branch = plugin.branch,
            languageTag = languageTag,
            treePaths = repoTreePaths,
            value = manifestAuthor,
        ) ?: manifestAuthor?.takeIf { !it.startsWith("@") }
            ?: releaseAuthor
            ?: unknown

        val versionName = versionMap["VERSION_NAME"]?.takeIf { it.isNotBlank() }
            ?: releaseTag?.removePrefix("v")?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.text_unknown)

        val versionCode = parseVersionCode(versionMap)
            ?: 0L

        val releaseNotesPath = resolveOfficialReleaseNotesPath(languageTag, repoTreePaths)
        val releaseHistoryPath = resolveOfficialReleaseHistoryPath(languageTag, repoTreePaths) ?: releaseNotesPath
        val changelogUrl = releaseHistoryPath?.let {
            githubBlobUrl(
                owner = plugin.owner,
                repo = plugin.repo,
                branch = plugin.branch,
                path = it,
                anchor = githubMarkdownAnchorForVersion(versionName, releaseTag),
            )
        }
        val changelogText = resolveOfficialReleaseNotesText(
            context = context,
            owner = plugin.owner,
            repo = plugin.repo,
            branch = plugin.branch,
            releaseNotesPath = releaseNotesPath,
            releaseHistoryPath = releaseHistoryPath,
            versionName = versionName,
            releaseTag = releaseTag,
        ) ?: extractReleaseNotesFromMarkdown(
            markdown = releaseBody,
            versionName = versionName,
            releaseTag = releaseTag,
        ) ?: releaseBody

        val release = PluginIndexRelease(
            versionName = versionName,
            versionCode = max(versionCode, 0L),
            versionDate = publishedDate,
            apkUrl = selectedAsset?.downloadUrl,
            apkSha256 = selectedAsset?.sha256,
            apkSizeBytes = selectedAsset?.size,
            changelogUrl = changelogUrl,
            changelogText = changelogText,
        )

        return PluginIndexEntry(
            packageName = packageName,
            iconUrl = iconUrl,
            nightIconUrl = nightIconUrl,
            title = title,
            description = description,
            instructionHardCoded = instructionHardCoded,
            instructionMarkdownUrl = instructionMarkdownUrl,
            author = author,
            collaborators = emptyList(),
            releases = listOf(release),
            supportedAbis = supportedAbis,
            tags = listOf("official"),
            source = PluginIndexSource.OFFICIAL,
        )
    }

    private fun fetchThirdPartyEntries(
        context: Context,
        languageTag: String,
    ): List<PluginIndexEntry> {
        val treeJson = getJsonObject(
            context = context,
            url = "https://api.github.com/repos/$THIRD_PARTY_OWNER/$THIRD_PARTY_REPO/git/trees/$THIRD_PARTY_BRANCH?recursive=1",
        )
        val treeArray = treeJson.optJSONArray("tree") ?: JSONArray()

        val candidates = mutableListOf<GarageApk>()
        val treePathsByPackage = linkedMapOf<String, MutableSet<String>>()
        for (i in 0 until treeArray.length()) {
            val node = treeArray.optJSONObject(i) ?: continue
            if (node.optString("type") != "blob") continue

            val path = node.optString("path")
            if (!path.contains('/')) continue

            val packageName = path.substringBefore('/').trim()
            if (packageName.isBlank()) continue
            treePathsByPackage.getOrPut(packageName) { linkedSetOf() }.add(path)

            if (!path.endsWith(".apk", ignoreCase = true)) continue

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

        return latestByPackage
            .sortedBy { it.packageName.lowercase() }
            .map { apk ->
            val treePaths = treePathsByPackage[apk.packageName].orEmpty()
            val indexConfig = resolveGarageIndexConfig(
                context = context,
                packageName = apk.packageName,
                versionCode = apk.versionCode,
                treePaths = treePaths,
            )
            val iconUrl = resolveGarageDefaultIconPath(apk.packageName, treePaths)?.let { path ->
                rawGithubUrl(
                    owner = THIRD_PARTY_OWNER,
                    repo = THIRD_PARTY_REPO,
                    branch = THIRD_PARTY_BRANCH,
                    path = path,
                ).toUri()
            }
            val nightIconUrl = resolveGarageNightIconPath(apk.packageName, treePaths)?.let { path ->
                rawGithubUrl(
                    owner = THIRD_PARTY_OWNER,
                    repo = THIRD_PARTY_REPO,
                    branch = THIRD_PARTY_BRANCH,
                    path = path,
                ).toUri()
            }
            val description = resolveGarageDescription(
                context = context,
                packageName = apk.packageName,
                languageTag = languageTag,
                treePaths = treePaths,
                fallback = indexConfig.description,
            )
            val instructionMarkdownUrl = resolveGarageInstructionMarkdownUrl(
                packageName = apk.packageName,
                languageTag = languageTag,
                treePaths = treePaths,
            )
            val instructionHardCoded = indexConfig.instruction?.truncateForSafeBinder()

            val effectiveVersionName = indexConfig.versionName
                ?.takeIf { it.isNotBlank() }
                ?: apk.versionName
            val effectiveVersionDate = indexConfig.versionDate
                ?.takeIf { it.isNotBlank() }

            PluginIndexEntry(
                packageName = apk.packageName,
                iconUrl = iconUrl,
                nightIconUrl = nightIconUrl,
                forceIgnoreLocalIcon = indexConfig.forceIgnoreLocalIcon,
                title = indexConfig.name?.takeIf { it.isNotBlank() } ?: apk.originalName,
                description = description,
                instructionHardCoded = instructionHardCoded,
                instructionMarkdownUrl = instructionMarkdownUrl,
                author = indexConfig.author,
                releases = listOf(
                    PluginIndexRelease(
                        versionName = effectiveVersionName,
                        versionCode = apk.versionCode,
                        versionDate = effectiveVersionDate,
                        apkUrl = apk.downloadUrl,
                        apkSha256 = null,
                        apkSizeBytes = apk.size,
                        changelogUrl = null,
                        changelogText = context.getString(R.string.text_no_release_notes),
                    ),
                ),
                supportedAbis = indexConfig.supportedAbis,
                tags = listOf("third-party"),
                requiresHostVersion = indexConfig.requiresHostVersion,
                source = PluginIndexSource.THIRD_PARTY,
            )
        }
    }

    private fun fetchRepoTreePaths(
        context: Context,
        owner: String,
        repo: String,
        branch: String,
    ): Set<String> {
        val treeJson = getJsonObject(
            context = context,
            url = "https://api.github.com/repos/$owner/$repo/git/trees/$branch?recursive=1",
        )
        if (treeJson.optBoolean("truncated", false)) {
            Log.w(TAG, "Git tree is truncated for $owner/$repo@$branch")
        }
        val treeArray = treeJson.optJSONArray("tree") ?: return emptySet()
        val paths = linkedSetOf<String>()
        for (i in 0 until treeArray.length()) {
            val node = treeArray.optJSONObject(i) ?: continue
            if (node.optString("type") != "blob") continue
            val path = node.optString("path").trim()
            if (path.isBlank()) continue
            paths += path
        }
        return paths
    }

    private fun resolveGarageIndexConfig(
        context: Context,
        packageName: String,
        versionCode: Long,
        treePaths: Set<String>,
    ): GarageIndexConfig {
        val indexPath = "$packageName/index.json"
        if (indexPath !in treePaths) {
            return GarageIndexConfig()
        }
        val content = runCatching {
            getText(
                context = context,
                url = rawGithubUrl(
                    owner = THIRD_PARTY_OWNER,
                    repo = THIRD_PARTY_REPO,
                    branch = THIRD_PARTY_BRANCH,
                    path = indexPath,
                ),
                accept = "application/json",
            )
        }.getOrNull() ?: return GarageIndexConfig()
        return parseGarageIndexConfig(content, versionCode)
    }

    private fun parseGarageIndexConfig(json: String, versionCode: Long): GarageIndexConfig {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return GarageIndexConfig()
        val byVersion = root.optJSONObject(versionCode.toString())
        val defaults = root.optJSONObject("0")

        fun selectString(key: String): String? {
            val fromVersion = byVersion?.optString(key)?.trim()?.takeIf { it.isNotBlank() }
            if (fromVersion != null) return fromVersion
            return defaults?.optString(key)?.trim()?.takeIf { it.isNotBlank() }
        }
        fun selectBoolean(key: String): Boolean? {
            parseBooleanValue(byVersion?.opt(key))?.let { return it }
            return parseBooleanValue(defaults?.opt(key))
        }

        val capabilities = byVersion?.optJSONObject("capabilities")
            ?: defaults?.optJSONObject("capabilities")
        val supportedAbis = parseSupportedAbisFromGarageConfig(
            byVersion = byVersion,
            defaults = defaults,
            capabilities = capabilities,
        )
        return GarageIndexConfig(
            name = selectString("name"),
            author = selectString("author"),
            description = selectString("description"),
            instruction = selectString("instruction"),
            versionName = selectString("versionName"),
            versionDate = selectString("versionDate"),
            requiresHostVersion = PluginCapabilityResolver.resolveRequiresHostVersion(capabilities),
            forceIgnoreLocalIcon = selectBoolean("forceIgnoreLocalIcon")
                ?: selectBoolean("preferRemoteIcon")
                ?: false,
            supportedAbis = supportedAbis,
        )
    }

    private fun parseSupportedAbisFromGarageConfig(
        byVersion: JSONObject?,
        defaults: JSONObject?,
        capabilities: JSONObject?,
    ): List<String>? {
        val keys = listOf("supportedAbis", "abis", "abi")
        listOf(byVersion, defaults, capabilities).forEach { source ->
            if (source == null) return@forEach
            keys.forEach { key ->
                if (!source.has(key)) return@forEach
                val resolved = PluginAbiUtils.resolveSupportedAbisFromValue(source.opt(key))
                if (resolved != null) {
                    return resolved
                }
            }
        }
        return null
    }

    private fun parseBooleanValue(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when (value.trim().lowercase()) {
            "1", "true", "yes", "y", "on" -> true
            "0", "false", "no", "n", "off" -> false
            else -> null
        }
        else -> null
    }

    private fun resolveGarageDescription(
        context: Context,
        packageName: String,
        languageTag: String,
        treePaths: Set<String>,
        fallback: String?,
    ): String? {
        val languageDirs = languageTagToAndroidResourceDirs(languageTag)
        val candidatePaths = languageDirs.map { "$packageName/res/$it/strings.xml" }
        candidatePaths.forEach { path ->
            if (path !in treePaths) return@forEach
            val text = runCatching {
                getText(
                    context = context,
                    url = rawGithubUrl(
                        owner = THIRD_PARTY_OWNER,
                        repo = THIRD_PARTY_REPO,
                        branch = THIRD_PARTY_BRANCH,
                        path = path,
                    ),
                )
            }.getOrNull() ?: return@forEach
            parseStringResource(text, "plugin_description")?.let { return it }
        }
        return fallback
    }

    private fun resolveGarageInstructionMarkdownUrl(
        packageName: String,
        languageTag: String,
        treePaths: Set<String>,
    ): String? {
        val rawDirs = languageTagToAndroidResourceDirs(languageTag)
            .map { it.replaceFirst("values", "raw") }
            .toMutableList()
            .apply { add("raw") }
            .distinct()
        val candidatePaths = rawDirs.map { "$packageName/res/$it/plugin_instruction.md" }
        candidatePaths.forEach { path ->
            if (path !in treePaths) return@forEach
            return rawGithubUrl(
                owner = THIRD_PARTY_OWNER,
                repo = THIRD_PARTY_REPO,
                branch = THIRD_PARTY_BRANCH,
                path = path,
            )
        }
        return null
    }

    private fun resolveGarageDefaultIconPath(
        packageName: String,
        treePaths: Set<String>,
    ): String? {
        val names = listOf("ic_launcher", "ic_launcher_round", "plugin_icon")
        val exts = listOf("png", "webp", "jpg", "jpeg")

        val dirs = listOf(
            "$packageName/res/mipmap",
            "$packageName/res/mipmap-anydpi-v26",
            "$packageName/res/mipmap-xxxhdpi",
            "$packageName/res/mipmap-xxhdpi",
            "$packageName/res/mipmap-xhdpi",
            "$packageName/res/mipmap-hdpi",
            "$packageName/res/mipmap-mdpi",
            "$packageName/res/drawable",
            "$packageName/res/drawable-anydpi-v26",
            "$packageName/res/drawable-xxxhdpi",
            "$packageName/res/drawable-xxhdpi",
            "$packageName/res/drawable-xhdpi",
            "$packageName/res/drawable-hdpi",
            "$packageName/res/drawable-mdpi",
        )
        return dirs.firstNotNullOfOrNull { dir ->
            names.firstNotNullOfOrNull { name ->
                exts.firstNotNullOfOrNull { ext ->
                    val candidate = "$dir/$name.$ext"
                    candidate.takeIf { it in treePaths }
                }
            }
        }
    }

    private fun resolveGarageNightIconPath(
        packageName: String,
        treePaths: Set<String>,
    ): String? {
        val names = listOf("ic_launcher", "ic_launcher_round", "plugin_icon")
        val exts = listOf("png", "webp", "jpg", "jpeg")
        val nightDirs = listOf(
            "$packageName/res/mipmap-night",
            "$packageName/res/drawable-night",
        )
        return nightDirs.firstNotNullOfOrNull { dir ->
            names.firstNotNullOfOrNull { name ->
                exts.firstNotNullOfOrNull { ext ->
                    val candidate = "$dir/$name.$ext"
                    candidate.takeIf { it in treePaths }
                }
            }
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

        val byApplicationId = Regex("""\bapplicationId\s*=\s*"([^"]+)"""")
            .find(buildGradle)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (!byApplicationId.isNullOrBlank()) return byApplicationId

        return Regex("""\bnamespace\s*=\s*"([^"]+)"""")
            .find(buildGradle)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
    }

    private fun resolveIconPath(iconRef: String?, treePaths: Set<String>): String? {
        val candidates = buildIconCandidates(iconRef)
        if (candidates.isEmpty() || treePaths.isEmpty()) return null
        return candidates.firstOrNull { it in treePaths }
    }

    private fun resolveNightIconPath(iconRef: String?, treePaths: Set<String>): String? {
        val candidates = buildNightIconCandidates(iconRef)
        if (candidates.isEmpty() || treePaths.isEmpty()) return null
        return candidates.firstOrNull { it in treePaths }
    }

    private fun resolveIconPathWithoutTreeProbe(
        context: Context,
        owner: String,
        repo: String,
        branch: String,
        iconRef: String?,
    ): String? {
        val candidates = buildIconCandidates(iconRef)
        if (candidates.isEmpty()) return null

        return candidates.firstOrNull { candidate ->
            val url = rawGithubUrl(owner = owner, repo = repo, branch = branch, path = candidate)
            isRawFileAvailable(context, url)
        }
    }

    private fun buildIconCandidates(iconRef: String?): List<String> {
        if (iconRef.isNullOrBlank() || !iconRef.startsWith("@")) return emptyList()
        val type = iconRef.substringAfter("@").substringBefore("/")
        val name = iconRef.substringAfter("/").ifBlank { return emptyList() }

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
        return dirs.flatMap { dir -> exts.map { ext -> "$dir/$name.$ext" } }
    }

    private fun buildNightIconCandidates(iconRef: String?): List<String> {
        if (iconRef.isNullOrBlank() || !iconRef.startsWith("@")) return emptyList()
        val type = iconRef.substringAfter("@").substringBefore("/")
        val name = iconRef.substringAfter("/").ifBlank { return emptyList() }

        val dirs = linkedSetOf(
            "app/src/main/res/$type-night",
            "app/src/main/res/drawable-night",
        )
        val exts = listOf("png", "webp", "jpg", "jpeg")
        return dirs.flatMap { dir -> exts.map { ext -> "$dir/$name.$ext" } }
    }

    private fun isRawFileAvailable(context: Context, url: String): Boolean {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("Accept", "*/*")
            setRequestProperty("User-Agent", "${context.getString(R.string.app_name)}/${BuildConfig.VERSION_NAME} (PluginCenter)")
        }
        return try {
            conn.connect()
            conn.responseCode in 200..299
        } catch (_: Throwable) {
            false
        } finally {
            conn.disconnect()
        }
    }

    private fun resolveOfficialFallbackIconPath(treePaths: Set<String>): String? {
        if (treePaths.isEmpty()) return null
        val imagePathRegex = Regex(
            """^app/src/main/res/(?:drawable(?:-[^/]+)?|mipmap(?:-[^/]+)?)/[^/]+\.(?:png|webp|jpg|jpeg)$""",
            RegexOption.IGNORE_CASE,
        )
        return treePaths
            .asSequence()
            .filter { imagePathRegex.matches(it) }
            .sortedWith(
                compareBy<String> { scoreIconPath(it) }
                    .thenBy { it.length }
                    .thenBy { it },
            )
            .firstOrNull()
    }

    private fun scoreIconPath(path: String): Int {
        val lowerPath = path.lowercase()
        val fileName = lowerPath.substringAfterLast('/')
        var score = 100
        if ("/drawable" in lowerPath) score -= 20
        if (fileName.startsWith("ic_plugin")) score -= 70
        else if (fileName.startsWith("ic_launcher")) score -= 60
        else if ("plugin" in fileName) score -= 45
        else if ("logo" in fileName) score -= 35
        else if ("icon" in fileName) score -= 20
        return score
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

    private fun resolveOfficialInstructionHardCoded(
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

            parseStringResource(text, "plugin_instruction")?.let { return it }
        }
        return null
    }

    private fun resolveOfficialInstructionMarkdownPath(
        languageTag: String,
        treePaths: Set<String>,
    ): String? {
        val rawDirs = languageTagToAndroidResourceDirs(languageTag)
            .map { it.replaceFirst("values", "raw") }
            .toMutableList()
            .apply { add("raw") }
            .distinct()
        return rawDirs.firstNotNullOfOrNull { dir ->
            val path = "app/src/main/res/$dir/plugin_instruction.md"
            path.takeIf { it in treePaths }
        }
    }

    private fun resolveStringResourceReference(
        context: Context,
        owner: String,
        repo: String,
        branch: String,
        languageTag: String,
        treePaths: Set<String>,
        value: String?,
    ): String? {
        val rawValue = value
            ?.trim()
            ?.takeIf { it.startsWith("@string/") }
            ?: return null
        val resourceName = rawValue
            .removePrefix("@string/")
            .takeIf { it.isNotBlank() }
            ?: return null

        languageTagToAndroidResourceDirs(languageTag).forEach { dir ->
            val path = "app/src/main/res/$dir/strings.xml"
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
            parseStringResource(text, resourceName)?.let { return it }
        }
        return null
    }

    private fun resolveOfficialReleaseNotesPath(
        languageTag: String,
        treePaths: Set<String>,
    ): String? {
        val candidates = languageTagToAndroidChangelogTags(languageTag)
            .map { "app/src/main/assets/doc/CHANGELOG-$it.md" } +
                "app/src/main/assets/doc/CHANGELOG.md"
        return selectFirstExistingPath(candidates, treePaths)
    }

    private fun resolveOfficialReleaseHistoryPath(
        languageTag: String,
        treePaths: Set<String>,
    ): String? {
        val historyTags = languageTagToRepositoryChangelogTags(languageTag)
        val androidTags = languageTagToAndroidChangelogTags(languageTag)
        val candidates = buildList {
            historyTags.forEach { add(".changelog/CHANGELOG-$it.md") }
            add(".changelog/CHANGELOG.md")
            historyTags.forEach { add("CHANGELOG-$it.md") }
            add("CHANGELOG.md")
            androidTags.forEach { add("app/src/main/assets/doc/CHANGELOG-$it.md") }
            add("app/src/main/assets/doc/CHANGELOG.md")
        }
        return selectFirstExistingPath(candidates, treePaths)
    }

    private fun selectFirstExistingPath(
        candidates: List<String>,
        treePaths: Set<String>,
    ): String? {
        val distinctCandidates = candidates.distinct()
        if (treePaths.isEmpty()) {
            return distinctCandidates.firstOrNull()
        }
        return distinctCandidates.firstOrNull { it in treePaths }
    }

    private fun resolveOfficialReleaseNotesText(
        context: Context,
        owner: String,
        repo: String,
        branch: String,
        releaseNotesPath: String?,
        releaseHistoryPath: String?,
        versionName: String,
        releaseTag: String?,
    ): String? {
        val paths = listOfNotNull(releaseNotesPath, releaseHistoryPath).distinct()
        paths.forEach { path ->
            val markdown = runCatching {
                getText(
                    context = context,
                    url = rawGithubUrl(
                        owner = owner,
                        repo = repo,
                        branch = branch,
                        path = path,
                    ),
                    accept = "text/markdown, text/plain, */*",
                )
            }.getOrNull() ?: return@forEach

            extractReleaseNotesFromMarkdown(
                markdown = markdown,
                versionName = versionName,
                releaseTag = releaseTag,
            )?.let { return it }
        }
        return null
    }

    private fun extractReleaseNotesFromMarkdown(
        markdown: String?,
        versionName: String,
        releaseTag: String?,
    ): String? {
        val text = markdown
            ?.replace("\r\n", "\n")
            ?.replace('\r', '\n')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val targetVersions = releaseVersionCandidates(versionName, releaseTag).toSet()
        val lines = text.lines()
        val sectionLines = mutableListOf<String>()
        var inTargetSection = false
        var foundTargetSection = false
        var foundAnyVersionHeading = false

        for (line in lines) {
            val headingVersion = parseMarkdownVersionHeading(line)
            if (headingVersion != null) {
                foundAnyVersionHeading = true
                if (inTargetSection) {
                    break
                }
                if (headingVersion in targetVersions) {
                    inTargetSection = true
                    foundTargetSection = true
                }
                continue
            }
            if (inTargetSection) {
                sectionLines += line
            }
        }

        val sourceLines = when {
            foundTargetSection -> sectionLines
            !foundAnyVersionHeading -> lines
            else -> emptyList()
        }
        val bulletLines = sourceLines
            .map { it.trim() }
            .filter(::isReleaseNoteBulletLine)
        if (bulletLines.isNotEmpty()) {
            return bulletLines.joinToString("\n")
        }
        if (foundTargetSection) {
            return sectionLines
                .joinToString("\n")
                .trim()
                .takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun parseMarkdownVersionHeading(line: String): String? {
        val rawHeading = Regex("""^#{1,6}\s+([vV]?\d+(?:\.\d+){1,4}(?:[-+._][A-Za-z0-9]+)?)\s*#*\s*$""")
            .find(line.trim())
            ?.groupValues
            ?.getOrNull(1)
            ?: return null
        return normalizeReleaseVersion(rawHeading)
    }

    private fun releaseVersionCandidates(
        versionName: String,
        releaseTag: String?,
    ): List<String> = listOfNotNull(
        versionName,
        versionName.takeIf { it.isNotBlank() }?.let { "v$it" },
        releaseTag,
    )
        .mapNotNull(::normalizeReleaseVersion)
        .distinct()

    private fun normalizeReleaseVersion(value: String?): String? = value
        ?.trim()
        ?.removePrefix("v")
        ?.removePrefix("V")
        ?.lowercase()
        ?.takeIf { it.isNotBlank() }

    private fun isReleaseNoteBulletLine(line: String): Boolean {
        if (line.matches(Regex("""^[*-]{3,}$"""))) return false
        return line.matches(Regex("""^(?:[-*+]\s+|\d+\.\s+).+"""))
    }

    private fun githubMarkdownAnchorForVersion(
        versionName: String,
        releaseTag: String?,
    ): String? {
        val heading = releaseTag
            ?.trim()
            ?.takeIf { it.startsWith("v", ignoreCase = true) }
            ?: versionName
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let { if (it.startsWith("v", ignoreCase = true)) it else "v$it" }
            ?: return null
        return heading
            .lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s_-]"""), "")
            .trim()
            .replace(Regex("""\s+"""), "-")
            .takeIf { it.isNotBlank() }
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

    private fun languageTagToAndroidChangelogTags(languageTag: String): List<String> {
        val normalized = languageTag.trim()
        val primary = normalized.substringBefore('-').lowercase()
        val tags = linkedSetOf<String>()
        when {
            normalized.equals("zh-Hant-HK", ignoreCase = true) -> {
                tags += "zh-rHK"
                tags += "zh-Hant-HK"
                tags += "zh-rTW"
                tags += "zh-Hant-TW"
                tags += "zh"
                tags += "zh-Hans"
            }
            normalized.equals("zh-Hant-TW", ignoreCase = true) -> {
                tags += "zh-rTW"
                tags += "zh-Hant-TW"
                tags += "zh-rHK"
                tags += "zh-Hant-HK"
                tags += "zh"
                tags += "zh-Hans"
            }
            primary == "zh" -> {
                tags += "zh"
                tags += "zh-Hans"
                tags += "zh-rHK"
                tags += "zh-Hant-HK"
                tags += "zh-rTW"
                tags += "zh-Hant-TW"
            }
            else -> {
                tags += primary
            }
        }
        tags += "en"
        tags += "zh-Hans"
        tags += "zh"
        return tags.toList()
    }

    private fun languageTagToRepositoryChangelogTags(languageTag: String): List<String> {
        val normalized = languageTag.trim()
        val primary = normalized.substringBefore('-').lowercase()
        val tags = linkedSetOf<String>()
        when {
            normalized.equals("zh-Hant-HK", ignoreCase = true) -> {
                tags += "zh-Hant-HK"
                tags += "zh-rHK"
                tags += "zh-Hant-TW"
                tags += "zh-Hans"
                tags += "zh"
            }
            normalized.equals("zh-Hant-TW", ignoreCase = true) -> {
                tags += "zh-Hant-TW"
                tags += "zh-rTW"
                tags += "zh-Hant-HK"
                tags += "zh-Hans"
                tags += "zh"
            }
            primary == "zh" -> {
                tags += "zh-Hans"
                tags += "zh"
                tags += "zh-Hant-HK"
                tags += "zh-Hant-TW"
            }
            else -> {
                tags += primary
            }
        }
        tags += "en"
        tags += "zh-Hans"
        tags += "zh"
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

    private fun parseVersionCode(properties: Map<String, String>): Long? {
        return properties["VERSION_CODE"]?.toLongOrNull()
            ?: properties["VERSION_BUILD"]?.toLongOrNull()
    }

    private fun isLikelyVersionLike(value: String): Boolean {
        val text = value.trim()
        if (text.isBlank()) return false
        return Regex("""^[vV]?\d+(?:\.\d+){1,4}(?:[-+._][A-Za-z0-9]+)*$""").matches(text)
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

    private fun githubBlobUrl(
        owner: String,
        repo: String,
        branch: String,
        path: String,
        anchor: String? = null,
    ): String {
        val safePath = path.trimStart('/')
        val baseUrl = "https://github.com/$owner/$repo/blob/$branch/$safePath"
        return anchor
            ?.takeIf { it.isNotBlank() }
            ?.let { "$baseUrl#$it" }
            ?: baseUrl
    }

    private fun getJsonObject(context: Context, url: String): JSONObject =
        JSONObject(getText(context, url, "application/json"))

    private fun getJsonArray(context: Context, url: String): JSONArray =
        JSONArray(getText(context, url, "application/json"))

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
        val arr = root.optJSONArray("items") ?: root.optJSONArray("plugins") ?: JSONArray()
        val list = mutableListOf<PluginIndexEntry>()

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val packageName = obj.optString("packageName").takeIf { it.isNotBlank() } ?: continue
            val title = obj.optString("title").takeIf { it.isNotBlank() } ?: packageName
            val description = obj.optString("description").takeIf { it.isNotBlank() && !it.equals("null", true) }
            val instructionHardCoded = obj.optString("instructionHardCoded").takeIf { it.isNotBlank() && !it.equals("null", true) }
            val instructionMarkdownUrl = obj.optString("instructionMarkdownUrl").takeIf { it.isNotBlank() && !it.equals("null", true) }
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
                ?: PluginIndexSource.OFFICIAL

            val collaborators = obj.optJSONArray("collaborators")?.let { ja ->
                List(ja.length()) { idx -> ja.optString(idx) }.filter { it.isNotBlank() }
            } ?: emptyList()
            val supportedAbis = when {
                !obj.has("supportedAbis") || obj.isNull("supportedAbis") -> null
                else -> PluginAbiUtils.resolveSupportedAbisFromValue(obj.opt("supportedAbis"))
            }
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

    private class HttpStatusException(
        val code: Int,
        val url: String,
        message: String,
    ) : IllegalStateException(message)

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

    private data class GarageIndexConfig(
        val name: String? = null,
        val author: String? = null,
        val description: String? = null,
        val instruction: String? = null,
        val versionName: String? = null,
        val versionDate: String? = null,
        val requiresHostVersion: Long? = null,
        val forceIgnoreLocalIcon: Boolean = false,
        val supportedAbis: List<String>? = null,
    )

    private data class CacheSnapshot(
        val entries: List<PluginIndexEntry>,
        val fetchedSources: Set<PluginIndexSource>,
        val languageTag: String?,
    )
}
