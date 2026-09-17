package org.autojs.autojs.network.github

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Exercises the client against canned answers through an OkHttp interceptor, so no socket is opened.
 */
class GitHubReleaseClientTest {

    private class Canned(private val answers: Map<String, Pair<Int, String>>) : Interceptor {
        val requests = mutableListOf<Request>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += request
            val (code, body) = answers[request.url.encodedPath] ?: (500 to """{"message": "unexpected ${request.url}"}""")
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Error")
                .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }
    }

    private fun client(canned: Canned, token: String? = null) = GitHubReleaseClient(
        client = OkHttpClient.Builder().addInterceptor(canned).build(),
        userAgent = "AutoJs6/6.8.0 (UpdateChecker)",
        token = token,
    )

    private fun fixture(): String =
        GitHubReleaseClientTest::class.java.classLoader!!.getResource("github/release-latest.json")!!.readText()

    @Test
    fun `sends the REST headers and the bearer token only when one is stored`() {
        val withToken = client(Canned(emptyMap()), token = "ghp_secret").buildRequest("https://api.github.com/x")
        assertEquals("application/vnd.github+json", withToken.header("Accept"))
        assertEquals("2022-11-28", withToken.header("X-GitHub-Api-Version"))
        assertEquals("AutoJs6/6.8.0 (UpdateChecker)", withToken.header("User-Agent"))
        assertEquals("Bearer ghp_secret", withToken.header("Authorization"))
        assertEquals("GET", withToken.method)

        val anonymous = client(Canned(emptyMap())).buildRequest("https://api.github.com/x")
        assertNull(anonymous.header("Authorization"))
    }

    @Test
    fun `fetches and parses the latest release with a single request`() {
        val canned = Canned(mapOf("/repos/SuperMonster003/AutoJs6/releases/latest" to (200 to fixture())))
        val release = client(canned).getLatestRelease("SuperMonster003", "AutoJs6")
        assertEquals("v6.7.0", release.tagName)
        assertEquals(6, release.assets.size)
        assertEquals(1, canned.requests.size)
        assertEquals("https://api.github.com/repos/SuperMonster003/AutoJs6/releases/latest", canned.requests[0].url.toString())
    }

    @Test
    fun `distinguishes a missing repository from a repository without releases`() {
        val notFound = 404 to """{"message": "Not Found", "status": "404"}"""
        val missingRepo = Canned(mapOf(
            "/repos/o/missing/releases/latest" to notFound,
            "/repos/o/missing" to notFound,
        ))
        try {
            client(missingRepo).getLatestRelease("o", "missing")
            fail("expected GitHubRepositoryNotFoundException")
        } catch (e: GitHubRepositoryNotFoundException) {
            assertEquals(404, e.code)
        }
        assertEquals(2, missingRepo.requests.size)

        val noRelease = Canned(mapOf(
            "/repos/o/empty/releases/latest" to notFound,
            "/repos/o/empty" to (200 to """{"id": 1, "full_name": "o/empty"}"""),
        ))
        try {
            client(noRelease).getLatestRelease("o", "empty")
            fail("expected GitHubReleaseNotFoundException")
        } catch (e: GitHubReleaseNotFoundException) {
            assertEquals(404, e.code)
        }
    }

    @Test
    fun `reports other failures with the HTTP status and GitHub's message`() {
        val limited = Canned(mapOf(
            "/repos/o/r/releases/latest" to (403 to """{"message": "API rate limit exceeded for 1.2.3.4.", "documentation_url": "x"}"""),
        ))
        try {
            client(limited).getLatestRelease("o", "r")
            fail("expected GitHubApiException")
        } catch (e: GitHubApiException) {
            assertEquals(403, e.code)
            assertEquals("HTTP 403: API rate limit exceeded for 1.2.3.4.", e.message)
        }

        val malformed = Canned(mapOf("/repos/o/r/releases/latest" to (200 to "<html>not json</html>")))
        try {
            client(malformed).getLatestRelease("o", "r")
            fail("expected GitHubResponseFormatException")
        } catch (e: GitHubResponseFormatException) {
            assertTrue(e.message!!.contains("Malformed"))
        }
    }
}
