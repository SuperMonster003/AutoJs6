package org.autojs.autojs.ui.settings

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseHistoryRepositoryTest {

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
