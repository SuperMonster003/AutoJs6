package org.autojs.autojs.ui.preview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentPreviewRequestPolicyTest {

    @Test
    fun mainFrameDataDocumentsAreNeverDelegatedToWebView() {
        val urls = listOf(
            "data:text/html;charset=utf-8;base64,PGgxPlByZXZpZXc8L2gxPg==",
            "data:image/png;base64,iVBORw0KGgo=",
            "data:video/mp4;base64,AAAA",
        )

        urls.forEach { url ->
            assertFalse(
                url,
                DocumentPreviewRequestPolicy.shouldLetWebViewLoadDataResource(
                    url = url,
                    isForMainFrame = true,
                ),
            )
        }
    }

    @Test
    fun onlyWhitelistedDataSubresourcesAreDelegatedToWebView() {
        val allowed = listOf(
            "data:image/png;base64,iVBORw0KGgo=",
            "DATA:image/svg+xml,%3Csvg%20xmlns='http://www.w3.org/2000/svg'/%3E",
            "data:audio/ogg;base64,T2dnUw==",
            "data:video/mp4;base64,AAAA",
        )
        val blocked = listOf(
            "data:text/html;base64,PGgxPlByZXZpZXc8L2gxPg==",
            "data:text/javascript,alert(1)",
            "data:application/octet-stream;base64,AAAA",
            "https://example.com/image.png",
            "file:///sdcard/image.png",
            "images/relative.png",
        )

        allowed.forEach { url ->
            assertTrue(
                url,
                DocumentPreviewRequestPolicy.shouldLetWebViewLoadDataResource(
                    url = url,
                    isForMainFrame = false,
                ),
            )
        }
        blocked.forEach { url ->
            assertFalse(
                url,
                DocumentPreviewRequestPolicy.shouldLetWebViewLoadDataResource(
                    url = url,
                    isForMainFrame = false,
                ),
            )
        }
    }
}
