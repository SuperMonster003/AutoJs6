package org.autojs.autojs.network.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GitHubReleaseParserTest {

    private fun fixture(): String {
        val resource = GitHubReleaseParserTest::class.java.classLoader!!.getResource("github/release-latest.json")
        assertNotNull("Missing test resource github/release-latest.json", resource)
        return resource!!.readText()
    }

    @Test
    fun `parses the fields the update checker uses from a real latest-release answer`() {
        val release = GitHubReleaseParser.parseRelease(fixture())

        assertEquals("v6.7.0", release.tagName)
        assertEquals("6.7.0 @ 2026/03/14", release.name)
        assertEquals("https://github.com/SuperMonster003/AutoJs6/releases/tag/v6.7.0", release.htmlUrl)
        assertEquals("2026-03-14T14:45:17Z", release.publishedAt)
        assertFalse(release.prerelease)
        assertTrue(release.body!!.startsWith("* `新增` 插件中心功能"))

        assertEquals(6, release.assets.size)
        val arm64 = release.assets.first()
        assertEquals("autojs6-v6.7.0-arm64-v8a-62db1ff8.apk", arm64.name)
        assertEquals(139_193_973L, arm64.size)
        assertEquals(
            "https://github.com/SuperMonster003/AutoJs6/releases/download/v6.7.0/autojs6-v6.7.0-arm64-v8a-62db1ff8.apk",
            arm64.browserDownloadUrl,
        )
        assertEquals(
            listOf("arm64-v8a-62db1ff8", "armeabi-67502694", "armeabi-v7a-df0b70ee", "universal-047ae62e", "x86-086c273b", "x86_64-3d410022"),
            release.assets.map { it.name.removePrefix("autojs6-v6.7.0-").removeSuffix(".apk") },
        )
    }

    @Test
    fun `tolerates missing optional fields and skips assets without a name`() {
        val release = GitHubReleaseParser.parseRelease(
            """{"tag_name": "v1.2.3", "assets": [{"name": "a.apk", "size": "not-a-number"}, {"size": 3}, "junk"]}""",
        )
        assertEquals("v1.2.3", release.tagName)
        assertNull(release.name)
        assertNull(release.htmlUrl)
        assertNull(release.body)
        assertNull(release.publishedAt)
        assertFalse(release.prerelease)
        assertEquals(1, release.assets.size)
        assertEquals("a.apk", release.assets[0].name)
        assertEquals(-1L, release.assets[0].size)
        assertNull(release.assets[0].browserDownloadUrl)
    }

    @Test
    fun `assets missing or not an array yields an empty list`() {
        assertTrue(GitHubReleaseParser.parseRelease("""{"tag_name": "v1"}""").assets.isEmpty())
        assertTrue(GitHubReleaseParser.parseRelease("""{"tag_name": "v1", "assets": null}""").assets.isEmpty())
        assertTrue(GitHubReleaseParser.parseRelease("""{"tag_name": "v1", "assets": {"name": "x"}}""").assets.isEmpty())
    }

    @Test
    fun `rejects answers without a tag name or that are not JSON objects`() {
        for (json in listOf("""{"name": "x"}""", """{"tag_name": ""}""", "[]", "not json", "")) {
            try {
                GitHubReleaseParser.parseRelease(json)
                fail("Expected GitHubResponseFormatException for $json")
            } catch (_: GitHubResponseFormatException) {
                // expected
            }
        }
    }

    @Test
    fun `extracts the message of a GitHub error body`() {
        assertEquals(
            "Not Found",
            GitHubReleaseParser.parseErrorMessage(
                """{"message": "Not Found", "documentation_url": "https://docs.github.com/rest", "status": "404"}""",
            ),
        )
        assertNull(GitHubReleaseParser.parseErrorMessage(null))
        assertNull(GitHubReleaseParser.parseErrorMessage("  "))
        assertNull(GitHubReleaseParser.parseErrorMessage("<html>rate limited</html>"))
        assertNull(GitHubReleaseParser.parseErrorMessage("""{"message": ""}"""))
    }
}
