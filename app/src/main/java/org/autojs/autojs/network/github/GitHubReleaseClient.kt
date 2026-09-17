package org.autojs.autojs.network.github

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.autojs.autojs.network.GitHubTokenStore
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal GitHub REST client for the update checker: the latest release of a repository and its
 * assets. Replaces the `org.kohsuke.github` SDK (and its Jackson / Commons IO / Commons Lang3
 * dependencies) with one OkHttp request plus explicit JSON parsing.
 *
 * Calls are synchronous and must run off the main thread.
 *
 * zh-CN: 更新检查用的最小 GitHub REST 客户端: 仓库最新 release 及其资产. 以一次 OkHttp 请求加显式 JSON 解析替代
 * `org.kohsuke.github` SDK (及其 Jackson / Commons IO / Commons Lang3 依赖). 调用是同步的, 必须在非主线程执行.
 */
class GitHubReleaseClient(
    private val client: OkHttpClient,
    private val userAgent: String,
    private val token: String?,
    private val apiBaseUrl: String = DEFAULT_API_BASE_URL,
) {

    /**
     * @throws GitHubRepositoryNotFoundException the repository does not exist (or is not visible with the current token)
     * @throws GitHubReleaseNotFoundException the repository exists but has no published release
     * @throws GitHubApiException any other non-2xx answer (rate limit, invalid token, server error)
     * @throws GitHubResponseFormatException the answer is not the expected JSON
     * @throws IOException the request could not be completed (no network, timeout)
     */
    @Throws(IOException::class)
    fun getLatestRelease(owner: String, repo: String): GitHubRelease {
        val url = "${apiBaseUrl.trimEnd('/')}/repos/$owner/$repo/releases/latest"
        client.newCall(buildRequest(url)).execute().use { response ->
            if (response.code == 404) {
                if (repositoryExists(owner, repo)) {
                    throw GitHubReleaseNotFoundException(owner, repo)
                }
                throw GitHubRepositoryNotFoundException(owner, repo)
            }
            val body = response.body?.string()
            if (!response.isSuccessful) {
                throw GitHubApiException(response.code, describeFailure(response, body))
            }
            if (body.isNullOrBlank()) {
                throw GitHubResponseFormatException("Empty response for $url")
            }
            return GitHubReleaseParser.parseRelease(body)
        }
    }

    @Throws(IOException::class)
    private fun repositoryExists(owner: String, repo: String): Boolean {
        val url = "${apiBaseUrl.trimEnd('/')}/repos/$owner/$repo"
        client.newCall(buildRequest(url)).execute().use { response ->
            return response.code != 404
        }
    }

    internal fun buildRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .get()
            .header("Accept", ACCEPT_JSON)
            .header("X-GitHub-Api-Version", API_VERSION)
            .header("User-Agent", userAgent)
            .header("Cache-Control", "no-cache")
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()
    }

    private fun describeFailure(response: Response, body: String?): String {
        val reason = GitHubReleaseParser.parseErrorMessage(body)
            ?: response.message.takeIf { it.isNotBlank() }
            ?: "HTTP ${response.code}"
        return "HTTP ${response.code}: $reason"
    }

    companion object {

        const val DEFAULT_API_BASE_URL = "https://api.github.com"
        private const val ACCEPT_JSON = "application/vnd.github+json"
        private const val API_VERSION = "2022-11-28"

        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }

        /** Builds a client that uses the stored GitHub token (if any) and the host's user agent. */
        @JvmStatic
        fun create(context: Context): GitHubReleaseClient {
            val appContext = context.applicationContext ?: context
            return GitHubReleaseClient(
                client = sharedClient,
                userAgent = "${appContext.getString(R.string.app_name)}/${BuildConfig.VERSION_NAME} (UpdateChecker)",
                token = GitHubTokenStore.getToken(appContext),
            )
        }
    }
}

/** A GitHub answer with a non-2xx status; [code] is the HTTP status, the message includes GitHub's reason. */
open class GitHubApiException(val code: Int, message: String) : IOException(message)

class GitHubRepositoryNotFoundException(owner: String, repo: String) :
    GitHubApiException(404, "GitHub repository $owner/$repo was not found")

class GitHubReleaseNotFoundException(owner: String, repo: String) :
    GitHubApiException(404, "GitHub repository $owner/$repo has no published release")

class GitHubResponseFormatException(message: String, cause: Throwable? = null) : IOException(message, cause)
