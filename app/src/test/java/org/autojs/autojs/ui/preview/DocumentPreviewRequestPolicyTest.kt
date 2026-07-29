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

    @Test
    fun onlyPublicExternalHttpsSubresourcesAreDelegatedToWebView() {
        val allowed = listOf(
            "https://raw.githubusercontent.com/example/project/main/banner.png",
            "https://img.shields.io/github/v/release/example/project",
            "https://img.shields.io/badge/auto.js->= 6.2.0-67a91b",
            "HTTPS://example.com/image.png",
        )
        val blocked = listOf(
            "http://example.com/image.png",
            "https://appassets.androidplatform.net/preview-assets/base.css",
            "https://appassets.androidplatform.net/preview assets/base.css",
            "HTTPS://APPASSETS.ANDROIDPLATFORM.NET/preview-assets/base.css",
            "https://appassets.androidplatform.net./preview-assets/base.css",
            "https://localhost/image.png",
            "https://127.0.0.1/image.png",
            "https://127.1/image.png",
            "https://0177.0.0.1/image.png",
            "https://2130706433/image.png",
            "https://192.168.1.1/image.png",
            "https://[::1]/image.png",
            "https://user:password@example.com/image.png",
            "file:///sdcard/image.png",
            "data:image/png;base64,iVBORw0KGgo=",
        )

        allowed.forEach { url ->
            assertTrue(
                url,
                DocumentPreviewRequestPolicy.shouldLetWebViewLoadHttpsSubresource(
                    url = url,
                    isForMainFrame = false,
                ),
            )
            assertFalse(
                url,
                DocumentPreviewRequestPolicy.shouldLetWebViewLoadHttpsSubresource(
                    url = url,
                    isForMainFrame = true,
                ),
            )
        }
        blocked.forEach { url ->
            assertFalse(
                url,
                DocumentPreviewRequestPolicy.shouldLetWebViewLoadHttpsSubresource(
                    url = url,
                    isForMainFrame = false,
                ),
            )
        }
    }
}
