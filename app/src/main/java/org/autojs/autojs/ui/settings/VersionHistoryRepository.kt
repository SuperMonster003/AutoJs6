package org.autojs.autojs.ui.settings

import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import org.autojs.autojs.util.TextUtils
import org.autojs.autojs6.R
import org.jsoup.Jsoup

class VersionHistoryRepository {

    private val mOkHttpClient by lazy { OkHttpClient() }

    fun loadVersionHistoriesFlow(activity: DisplayVersionHistoriesActivity, urlRaw: String, urlBlob: String): Flow<VersionHistoryItem> = channelFlow {
        var tRaw: Throwable? = null
        var tBlob: Throwable? = null

        val loadingTextView: TextView? = activity.findViewById(R.id.loading_text)
        val loadingSecondTextView: TextView? = activity.findViewById(R.id.loading_second_text)

        runCatching {
            loadingTextView?.visibility = TextView.VISIBLE
            return@channelFlow fetchStreamFlow(urlRaw).collect { send(it) }
        }.onFailure { eRaw ->
            tRaw = eRaw.apply { printStackTrace() }
        }

        runCatching {
            loadingSecondTextView?.visibility = TextView.VISIBLE
            return@channelFlow fetchStreamString(urlBlob).let { html ->
                parseHtmlFlow(html).collect { send(it) }
            }
        }.onFailure { eBlob ->
            tBlob = eBlob.apply { printStackTrace() }
        }

        loadingTextView?.visibility = TextView.GONE
        loadingSecondTextView?.visibility = TextView.GONE

        showErrorDialog(activity, tRaw, tBlob)
    }

    private fun parseHtmlFlow(html: String) = channelFlow {
        val doc = Jsoup.parse(html)
        doc.select("div.markdown-heading:has(> h1.heading-element:first-child)").forEach { h1Container ->
            val h1 = h1Container.selectFirst("> h1.heading-element:first-child") ?: return@forEach
            val version = h1.text()
            val date = h1Container.nextElementSibling()?.selectFirst("> h6.heading-element:first-child")?.text() ?: ""
            val ul = h1Container.nextElementSibling()?.nextElementSibling()

            if (ul != null && ul.tagName() == "ul") {
                val lines = ul.select("> li").map { li ->
                    TextUtils.htmlToMarkdown(li.outerHtml()).trim()
                }
                send(VersionHistoryItem(version, date, lines))
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

    private fun fetchStreamFlow(url: String): Flow<VersionHistoryItem> = channelFlow {
        launch(Dispatchers.IO) {
            mOkHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val src: BufferedSource = ensureSuccessfulResponse(response, url).source()
                val regexVersion = Regex("""^#\s+v[\d.]+\S*""")

                var curTitle = ""
                var curDate = ""
                val bodyLines = mutableListOf<String>()

                fun flush() {
                    if (curTitle.isBlank()) return
                    trySend(VersionHistoryItem(curTitle, curDate, bodyLines.toList()))
                    bodyLines.clear()
                }

                @Suppress("AssignedValueIsNeverRead")
                while (true) {
                    val line = src.readUtf8Line() ?: break
                    when {
                        regexVersion.matches(line.trim()) -> {
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
                flush()
            }
            close()
        }
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

    private fun showErrorDialog(activity: DisplayVersionHistoriesActivity, tRaw: Throwable?, tBlob: Throwable?) {
        val message: String? = buildString {
            if (tRaw != null) append("Error message of raw:\n\n${tRaw.message}\n\n")
            if (tBlob != null) append("Error message of blob:\n\n${tBlob.message}\n\n")
        }.takeIf(String::isNotBlank)?.trim()

        MaterialDialog.Builder(activity)
            .title(R.string.error_failed_to_retrieve_version_histories)
            .apply { message?.let(::content) }
            .positiveText(R.string.dialog_button_dismiss)
            .dismissListener { activity.finish() }
            .show()
    }

}