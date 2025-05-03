package org.autojs.autojs.ui.settings

import android.content.Context
import androidx.annotation.StringRes
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
import org.autojs.autojs.util.TextUtils
import org.jsoup.Jsoup
import java.io.File
import org.autojs.autojs6.R
import java.util.EnumSet

class VersionHistoryRepository {

    private val mOkHttpClient by lazy { OkHttpClient() }

    fun loadVersionHistoriesFlow(
        context: Context,
        languageTag: String,
        urlRaw: String,
        urlBlob: String,
    ): Flow<VersionHistoryItem> = channelFlow {
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

            // val html = fetchStreamString(niceUrlBlob)
            // return@channelFlow parseHtmlFlow(html).collect { send(it) }

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

        enum class Category(@StringRes val labelRes: Int) {
            HINT(R.string.changelog_label_hint),
            FEATURE(R.string.changelog_label_feature),
            FIX(R.string.changelog_label_fix),
            IMPROVEMENT(R.string.changelog_label_improvement),
            DEPENDENCY(R.string.changelog_label_dependency);
        }

        /* 默认显示: 除 HINT 及 DEPENDENCY 以外的全部类别. */
        val DEFAULT_FILTER: EnumSet<Category> =
            EnumSet.complementOf(EnumSet.of(Category.HINT, Category.DEPENDENCY))

        private fun String.toChangelogMarkdownFile(): String {
            return "CHANGELOG-$this.md"
        }

        private fun String.toChangelogMarkdownCacheFile(): String {
            return "CHANGELOG-$this.cache.md"
        }

        suspend fun readBestLocalSample(context: Context, languageTag: String): List<VersionHistoryItem> {
            val assetStr = runCatching {
                context.assets.open("doc/${languageTag.toChangelogMarkdownFile()}").bufferedReader().use { it.readText() }
            }.getOrNull()

            val cacheStr = runCatching {
                File(context.filesDir, languageTag.toChangelogMarkdownCacheFile()).readText()
            }.getOrNull()

            assetStr ?: cacheStr ?: return emptyList()

            fun String?.parseFirstVersion() = this
                ?.lineSequence()
                ?.firstOrNull { regexValidMarkdownVersion.matches(it.trim()) }
                ?.removePrefix("#")?.trim()
                ?.removePrefix("v")?.trim()
                ?: DEFAULT_VERSION_NAME

            val assetLatestVer = assetStr.parseFirstVersion()
            val cacheLatestVer = cacheStr.parseFirstVersion()

            val useCache = compareVersion(cacheLatestVer, assetLatestVer) >= 0
            val chosenMarkdown = if (useCache && !cacheStr.isNullOrBlank()) cacheStr else assetStr

            ProcessLogger.i("${context.getString(R.string.logger_ver_history_local_asset_latest)}: $assetLatestVer")
            ProcessLogger.i("${context.getString(R.string.logger_ver_history_offline_cache_latest)}: $cacheLatestVer")
            ProcessLogger.i(
                context.getString(R.string.logger_ver_history_initial_content_chosen) + ": ${
                    when (useCache) {
                        true -> context.getString(R.string.logger_ver_history_offline_cache_file)
                        else -> context.getString(R.string.logger_ver_history_local_asset_file)
                    }
                }"
            )

            return parseMarkdownFlow(chosenMarkdown ?: "").toList()
        }

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

        private fun parseMarkdownFlow(md: String): Flow<VersionHistoryItem> = channelFlow {
            var curTitle = ""
            var curDate = ""
            val bodyLines = mutableListOf<String>()

            fun flush(isClose: Boolean = false) {
                if (curTitle.isNotBlank()) {
                    trySend(VersionHistoryItem(curTitle, curDate, bodyLines.toList()))
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

        private fun parseHtmlFlow(html: String) = channelFlow {
            val doc = Jsoup.parse(html)
            doc.select("div.markdown-heading").filter { it.children().first()?.tagName() == "h1" }.forEach { h1Container ->
                val h1 = h1Container.getElementsByTag("h1").first() ?: return@forEach
                val version = h1.text()
                val nextSibling = h1Container.nextElementSibling()
                val date = nextSibling?.getElementsByTag("h6")?.first()?.text() ?: ""
                val ul = nextSibling?.nextElementSibling()

                if (ul?.tagName() == "ul") {
                    val lines = ul.getElementsByTag("li").map { li ->
                        TextUtils.htmlToMarkdown(li.outerHtml()).trim()
                    }
                    send(VersionHistoryItem(version, date, lines))
                }
            }
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