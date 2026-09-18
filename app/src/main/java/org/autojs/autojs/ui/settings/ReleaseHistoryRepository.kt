package org.autojs.autojs.ui.settings

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.autojs.autojs.util.ProcessLogger
import java.io.File
import org.autojs.autojs6.R
import java.util.EnumSet

class ReleaseHistoryRepository {

    private val mOkHttpClient by lazy { OkHttpClient() }

    fun loadReleaseHistoryFlow(
        context: Context,
        languageTag: String,
        urlRaw: String,
        urlBlob: String,
    ): Flow<ReleaseHistoryItem> = channelFlow {
        runCatching {
            ProcessLogger.i(context.getString(R.string.logger_ver_history_start_raw_thread))
            ProcessLogger.i("URL: $urlRaw")
            val markdown = fetchStreamString(urlRaw)
            ProcessLogger.i(context.getString(R.string.logger_ver_history_raw_thread_success))
            writeCacheMarkdown(context, languageTag, markdown)
            return@channelFlow parseMarkdownFlow(markdown).collect { send(it) }
        }.onFailure { eRaw ->
            eRaw.apply {
                printStackTrace()
                ProcessLogger.i("${context.getString(R.string.logger_ver_history_raw_thread_failure)}: $message")
            }
        }
        runCatching {
            ProcessLogger.i(context.getString(R.string.logger_ver_history_start_blob_thread))

            var niceUrlBlob = urlBlob
            val hasQuery = urlBlob.contains('?')
            val queryPrefix = if (hasQuery) "&" else "?"

            if (!urlBlob.contains("plain=")) {
                niceUrlBlob += "${queryPrefix}plain=1"
                if (!urlBlob.contains("raw=")) {
                    niceUrlBlob += "&raw=true"
                }
            } else if (!urlBlob.contains("raw=")) {
                niceUrlBlob += "${queryPrefix}raw=true"
            }

            ProcessLogger.i("URL: $niceUrlBlob")

            val markdown = fetchStreamString(niceUrlBlob)
            ProcessLogger.i(context.getString(R.string.logger_ver_history_blob_thread_success))
            writeCacheMarkdown(context, languageTag, markdown)
            return@channelFlow parseMarkdownFlow(markdown).collect { send(it) }
        }.onFailure { eBlob ->
            eBlob.apply {
                printStackTrace()
                ProcessLogger.i("${context.getString(R.string.logger_ver_history_blob_thread_failure)}: $message")
            }
        }
    }

    fun loadPluginReleaseHistoryFlow(
        context: Context,
        url: String,
        languageTag: String,
    ): Flow<ReleaseHistoryItem> = channelFlow {
        var lastError: Throwable? = null
        for (contentUrl in pluginChangelogContentUrls(url, languageTag)) {
            val result = runCatching {
                ProcessLogger.i(context.getString(R.string.logger_ver_history_start_raw_thread))
                ProcessLogger.i("URL: $contentUrl")
                val markdown = fetchStreamString(contentUrl)
                val items = parseMarkdownFlow(markdown).toList()
                check(items.isNotEmpty()) { "No release history entries at $contentUrl" }
                items.forEach { send(it) }
                ProcessLogger.i(context.getString(R.string.logger_ver_history_raw_thread_success))
            }
            if (result.isSuccess) {
                return@channelFlow
            }
            lastError = result.exceptionOrNull()
        }
        lastError?.let { error ->
            error.printStackTrace()
            ProcessLogger.i(
                "${context.getString(R.string.logger_ver_history_raw_thread_failure)}: ${error.message}",
            )
        }
    }

    private suspend fun fetchStreamString(url: String): String {
        return withContext(Dispatchers.IO) {
            mOkHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                ensureSuccessfulResponse(response, url).string()
            }
        }
    }

    companion object {

        const val DEFAULT_VERSION_NAME = "0.0.0"

        private val regexValidMarkdownVersion = Regex("""^#\s+v[\d.]+\S*""")
        private val regexGitHubReleaseUrl = Regex(
            """^https://github\.com/([^/]+)/([^/]+)/releases(?:/.*)?$""",
            RegexOption.IGNORE_CASE,
        )

        internal fun pluginChangelogContentUrls(url: String, languageTag: String): List<String> {
            val cleanUrl = url.substringBefore('#').substringBefore('?')
            regexGitHubReleaseUrl.matchEntire(cleanUrl)?.let { match ->
                val (owner, repository) = match.destructured
                val base = "https://raw.githubusercontent.com/$owner/$repository/HEAD/app/src/main/assets/doc"
                return listOf(
                    "$base/CHANGELOG-$languageTag.md",
                    "$base/CHANGELOG.md",
                ).distinct()
            }
            if (cleanUrl.startsWith("https://github.com/", ignoreCase = true) && cleanUrl.contains("/blob/")) {
                return listOf(
                    cleanUrl
                        .replace("https://github.com/", "https://raw.githubusercontent.com/", ignoreCase = true)
                        .replace("/blob/", "/"),
                )
            }
            return listOf(url)
        }

        enum class Category(val labelRes: Int, val priority: Int) {
            HINT(R.string.changelog_label_hint, -2),
            FEATURE(R.string.changelog_label_feature, 3),
            FIX(R.string.changelog_label_fix, 2),
            IMPROVEMENT(R.string.changelog_label_improvement, 1),
            DEPENDENCY(R.string.changelog_label_dependency, -1);
        }

        val DEFAULT_FILTER: EnumSet<Category> =
            EnumSet.of(Category.FEATURE, Category.FIX, Category.IMPROVEMENT)

        private fun String.toChangelogMarkdownFile(): String {
            return "CHANGELOG-$this.md"
        }

        private fun String.toChangelogMarkdownCacheFile(): String {
            return "CHANGELOG-$this.cache.md"
        }

        /** Language whose CHANGELOG asset is always bundled and serves as the offline fallback (D14). */
        const val FALLBACK_LANGUAGE_TAG = "en"

        enum class LocalSampleSource { ASSET, CACHE, FALLBACK_ASSET }

        /** One offline changelog candidate. zh-CN: 一个离线版本历史候选. */
        data class LocalSampleCandidate(val source: LocalSampleSource, val languageTag: String, val markdown: String) {
            val latestVersion: String get() = markdown.parseFirstVersion()
        }

        /**
         * Reads the offline candidates in chain order: the cache of [languageTag], the bundled asset of [languageTag]
         * (only zh-Hans and en ship, the other languages are pruned at asset merge time, see
         * `appMergedAssetPruneIncludes`), and the bundled en asset when [languageTag] is not en.
         *
         * zh-CN: 按链顺序读取离线候选: [languageTag] 的缓存, [languageTag] 的内置资产 (只内置 zh-Hans 与 en, 其余语言在资产
         * 合并期剪除, 见 `appMergedAssetPruneIncludes`), 以及 [languageTag] 非 en 时的内置 en 资产.
         */
        fun readLocalSampleCandidates(context: Context, languageTag: String): List<LocalSampleCandidate> {
            fun asset(tag: String) = runCatching {
                context.assets.open("doc/${tag.toChangelogMarkdownFile()}").bufferedReader().use { it.readText() }
            }.getOrNull()

            fun cache(tag: String) = runCatching {
                File(context.filesDir, tag.toChangelogMarkdownCacheFile()).readText()
            }.getOrNull()

            return listOfNotNull(
                cache(languageTag)?.let { LocalSampleCandidate(LocalSampleSource.CACHE, languageTag, it) },
                asset(languageTag)?.let { LocalSampleCandidate(LocalSampleSource.ASSET, languageTag, it) },
                if (languageTag != FALLBACK_LANGUAGE_TAG) {
                    asset(FALLBACK_LANGUAGE_TAG)?.let { LocalSampleCandidate(LocalSampleSource.FALLBACK_ASSET, FALLBACK_LANGUAGE_TAG, it) }
                } else null,
            )
        }

        /**
         * Picks the candidate with the newest first version; on a tie the earlier candidate wins, so the cache beats
         * the asset of the same language (unchanged behaviour) and the current language beats the en fallback.
         * Blank candidates are ignored.
         *
         * zh-CN: 选首个版本号最新的候选; 版本相同时靠前者胜出, 即同语言缓存优先于资产 (行为不变), 当前语言优先于 en 回退.
         * 空白候选忽略.
         */
        fun chooseLocalSample(candidates: List<LocalSampleCandidate>): LocalSampleCandidate? {
            var chosen: LocalSampleCandidate? = null
            for (candidate in candidates) {
                if (candidate.markdown.isBlank()) continue
                val current = chosen
                if (current == null || compareVersion(candidate.latestVersion, current.latestVersion) > 0) {
                    chosen = candidate
                }
            }
            return chosen
        }

        suspend fun readBestLocalSample(context: Context, languageTag: String): List<ReleaseHistoryItem> {
            val candidates = readLocalSampleCandidates(context, languageTag)
            val chosen = chooseLocalSample(candidates) ?: return emptyList()

            // `this.languageTag` on purpose: the enclosing parameter of the same name would shadow the candidate's.
            // zh-CN: 此处特意写 `this.languageTag`, 否则同名的外层参数会遮蔽候选自身的语言标签.
            fun LocalSampleCandidate.label(): String = when (source) {
                LocalSampleSource.ASSET -> context.getString(R.string.logger_ver_history_local_asset_file)
                LocalSampleSource.CACHE -> context.getString(R.string.logger_ver_history_offline_cache_file)
                LocalSampleSource.FALLBACK_ASSET -> "${context.getString(R.string.logger_ver_history_local_asset_file)} (${this.languageTag})"
            }

            candidates.forEach { candidate ->
                val what = when (candidate.source) {
                    LocalSampleSource.CACHE -> context.getString(R.string.logger_ver_history_offline_cache_latest)
                    else -> context.getString(R.string.logger_ver_history_local_asset_latest) + " (${candidate.languageTag})"
                }
                ProcessLogger.i("$what: ${candidate.latestVersion}")
            }
            ProcessLogger.i("${context.getString(R.string.logger_ver_history_initial_content_chosen)}: ${chosen.label()}")

            return parseMarkdownFlow(chosen.markdown).toList()
        }

        internal fun String?.parseFirstVersion(): String = this
            ?.lineSequence()
            ?.firstOrNull { regexValidMarkdownVersion.matches(it.trim()) }
            ?.removePrefix("#")?.trim()
            ?.removePrefix("v")?.trim()
            ?: DEFAULT_VERSION_NAME

        private fun ensureSuccessfulResponse(response: Response, url: String): ResponseBody {
            require(response.isSuccessful && response.body != null) {
                buildString {
                    append("URL: $url\nCode: ${response.code}")
                    val content = response.body?.string()
                    if (!content.isNullOrBlank() && content.length <= 200) {
                        append("\nBody: $content")
                    }
                }
            }
            return response.body!!
        }

        private fun writeCacheMarkdown(context: Context, languageTag: String, markdown: String) {
            File(context.filesDir, languageTag.toChangelogMarkdownCacheFile()).writeText(markdown)
        }

        internal fun parseMarkdownFlow(md: String): Flow<ReleaseHistoryItem> = channelFlow {
            var curTitle = ""
            var curDate = ""
            val bodyLines = mutableListOf<String>()

            fun flush(isClose: Boolean = false) {
                if (curTitle.isNotBlank()) {
                    trySend(ReleaseHistoryItem(curTitle, curDate, bodyLines.toList()))
                    bodyLines.clear()
                }
                if (isClose) close()
            }

            @Suppress("AssignedValueIsNeverRead")
            md.lineSequence().forEach { line ->
                when {
                    regexValidMarkdownVersion.matches(line.trim()) -> {
                        flush()
                        curTitle = line.trim().removePrefix("#").trim()
                    }
                    line.trim().startsWith("######") -> {
                        curDate = line.trim().removePrefix("######").trim()
                    }
                    line.trim().startsWith("*") && !line.trim().matches(Regex("\\*+")) -> {
                        bodyLines += line.trim()
                    }
                }
            }
            flush(true)
        }

        fun compareVersion(v1: String, v2: String): Int {
            val a1 = v1.trimStart('v').split(".")
            val a2 = v2.trimStart('v').split(".")
            val max = maxOf(a1.size, a2.size)
            for (i in 0 until max) {
                val n1 = a1.getOrNull(i)?.toIntOrNull() ?: 0
                val n2 = a2.getOrNull(i)?.toIntOrNull() ?: 0
                if (n1 != n2) return n1.compareTo(n2)
            }
            return 0
        }

    }

}
