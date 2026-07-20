package org.autojs.autojs.ui.edit.editor.ace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AceOptionalWebResourceRouteTest {

    @Test
    fun `accepts only the exact appassets favicon URL`() {
        assertTrue(
            AceOptionalWebResourceRoute.matches(
                "https://appassets.androidplatform.net/favicon.ico",
            ),
        )

        listOf(
            null,
            "",
            "http://appassets.androidplatform.net/favicon.ico",
            "https://appassets.androidplatform.net/favicon.ico?cache=1",
            "https://appassets.androidplatform.net/favicon.ico/extra",
            "https://appassets.androidplatform.net/manifest.json",
            "https://appassets.androidplatform.net.evil.example/favicon.ico",
            "https://evil.example/favicon.ico",
        ).forEach { url ->
            assertFalse(url.orEmpty(), AceOptionalWebResourceRoute.matches(url))
        }
    }
}
