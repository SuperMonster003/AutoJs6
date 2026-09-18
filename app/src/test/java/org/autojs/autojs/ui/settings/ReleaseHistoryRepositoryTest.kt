package org.autojs.autojs.ui.settings

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.ui.settings.ReleaseHistoryRepository.Companion.LocalSampleCandidate
import org.autojs.autojs.ui.settings.ReleaseHistoryRepository.Companion.LocalSampleSource
import org.autojs.autojs.ui.settings.ReleaseHistoryRepository.Companion.chooseLocalSample
import org.autojs.autojs.ui.settings.ReleaseHistoryRepository.Companion.parseFirstVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ReleaseHistoryRepositoryTest {

    private fun md(version: String) = "# v$version\n\n###### 2026/09/18\n\n* `Fix` something"

    private fun cache(tag: String, version: String) = LocalSampleCandidate(LocalSampleSource.CACHE, tag, md(version))
    private fun asset(tag: String, version: String) = LocalSampleCandidate(LocalSampleSource.ASSET, tag, md(version))
    private fun fallback(version: String) = LocalSampleCandidate(LocalSampleSource.FALLBACK_ASSET, "en", md(version))

    @Test
    fun `newest version wins between the cache and the asset of the same language`() {
        val newerAsset = asset("zh-Hans", "6.8.0")
        assertSame(newerAsset, chooseLocalSample(listOf(cache("zh-Hans", "6.7.0"), newerAsset)))
        val newerCache = cache("zh-Hans", "6.8.1")
        assertSame(newerCache, chooseLocalSample(listOf(newerCache, asset("zh-Hans", "6.8.0"))))
    }

    @Test
    fun `same version keeps the cache like before`() {
        val sameCache = cache("en", "6.8.0")
        assertSame(sameCache, chooseLocalSample(listOf(sameCache, asset("en", "6.8.0"))))
    }

    @Test
    fun `pruned language falls back to its cache and then to the en asset`() {
        val cached = cache("zh-Hant-TW", "6.8.0")
        assertSame(cached, chooseLocalSample(listOf(cached, fallback("6.8.0"))))
        val en = fallback("6.8.0")
        assertSame(en, chooseLocalSample(listOf(en)))
        // An outdated cache loses to a newer bundled en asset.
        val newerEn = fallback("6.8.0")
        assertSame(newerEn, chooseLocalSample(listOf(cache("ja", "6.7.0"), newerEn)))
    }

    @Test
    fun `blank candidates are ignored and nothing yields null`() {
        val en = fallback("6.8.0")
        assertSame(en, chooseLocalSample(listOf(LocalSampleCandidate(LocalSampleSource.CACHE, "ru", "   "), en)))
        assertNull(chooseLocalSample(emptyList()))
        assertNull(chooseLocalSample(listOf(LocalSampleCandidate(LocalSampleSource.ASSET, "en", ""))))
    }

    @Test
    fun `first version is parsed from the first version heading`() {
        assertEquals("6.8.0", "intro\n# v6.8.0\n# v6.7.0".parseFirstVersion())
        assertEquals("6.8.0-beta1", "#  v6.8.0-beta1 \n".parseFirstVersion())
        assertEquals(ReleaseHistoryRepository.DEFAULT_VERSION_NAME, "no heading".parseFirstVersion())
        assertEquals(ReleaseHistoryRepository.DEFAULT_VERSION_NAME, (null as String?).parseFirstVersion())
        assertEquals("6.8.0-beta1", fallback("6.8.0-beta1").latestVersion)
    }

    @Test
    fun `maps a GitHub release page to localized and fallback changelog files`() {
        assertEquals(
            listOf(
                "https://raw.githubusercontent.com/example/plugin/HEAD/app/src/main/assets/doc/CHANGELOG-zh-Hans.md",
                "https://raw.githubusercontent.com/example/plugin/HEAD/app/src/main/assets/doc/CHANGELOG.md",
            ),
            ReleaseHistoryRepository.pluginChangelogContentUrls(
                "https://github.com/example/plugin/releases/tag/v1.2.3",
                "zh-Hans",
            ),
        )
    }

    @Test
    fun `maps a GitHub blob URL directly to raw content`() {
        assertEquals(
            listOf("https://raw.githubusercontent.com/example/plugin/main/CHANGELOG.md"),
            ReleaseHistoryRepository.pluginChangelogContentUrls(
                "https://github.com/example/plugin/blob/main/CHANGELOG.md?plain=1",
                "en",
            ),
        )
    }

    @Test
    fun `parses inline plugin release notes as one release`() = runBlocking {
        val items = ReleaseHistoryRepository.parseMarkdownFlow(
            "# v1.2.3\n\n* `Improvement` Keep release history inside the app",
        ).toList()

        assertEquals(1, items.size)
        assertEquals("v1.2.3", items.single().version)
        assertEquals(
            listOf("* `Improvement` Keep release history inside the app"),
            items.single().lines,
        )
    }
}
