package org.autojs.autojs.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocsUtilsTest {

    @Test
    fun `builds source URLs from a normalized route`() {
        assertEquals(
            "https://offline-docs.autojs6.local/index.html",
            DocsUtils.getUrl(DocsUtils.Source.OFFLINE, "/index.html"),
        )
        assertEquals(
            "https://docs.autojs6.com/overview.html#usage",
            DocsUtils.getUrl(DocsUtils.Source.ONLINE, "overview.html#usage"),
        )
    }

    @Test
    fun `extracts routes from plugin online and legacy local origins`() {
        assertEquals(
            DocsUtils.INDEX_URL_SUFFIX,
            DocsUtils.extractUrlSuffix("https://offline-docs.autojs6.local/"),
        )
        assertEquals(
            "overview.html?lang=zh#usage",
            DocsUtils.extractUrlSuffix("https://offline-docs.autojs6.local/overview.html?lang=zh#usage"),
        )
        assertEquals(
            "api.html#methods",
            DocsUtils.extractUrlSuffix("file:///android_asset/docs/api.html#methods"),
        )
        assertEquals(
            "events.html#listener",
            DocsUtils.extractUrlSuffix("https://docs.autojs6.com/events.html#listener"),
        )
    }

    @Test
    fun `converts online hash routes to static plugin pages`() {
        assertEquals(
            "overview.html?lang=zh",
            DocsUtils.extractUrlSuffix("https://docs.autojs6.com/#/overview?lang=zh"),
        )
        assertEquals(
            DocsUtils.INDEX_URL_SUFFIX,
            DocsUtils.extractUrlSuffix("https://docs.autojs6.com/#/"),
        )
    }

    @Test
    fun `rejects unrelated origins`() {
        assertNull(DocsUtils.extractUrlSuffix("https://example.com/index.html"))
    }
}
