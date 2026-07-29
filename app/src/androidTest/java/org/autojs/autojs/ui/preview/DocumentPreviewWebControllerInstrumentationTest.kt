@file:Suppress("DEPRECATION")

package org.autojs.autojs.ui.preview

import android.view.ContextThemeWrapper
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import org.autojs.autojs6.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class DocumentPreviewWebControllerInstrumentationTest {

    @Test
    fun generatedDocumentLoadsFromVirtualHttpsOrigin() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val resourceRoot = File(
            targetContext.cacheDir,
            "document-preview-load-test-${System.nanoTime()}",
        )
        check(resourceRoot.mkdirs())

        val controller = AtomicReference<DocumentPreviewWebController?>()
        val observedTitle = AtomicReference<String?>()
        val observedUrl = AtomicReference<String?>()
        val pageFinished = CountDownLatch(1)

        try {
            instrumentation.runOnMainSync {
                val context = ContextThemeWrapper(targetContext, R.style.AppTheme)
                val webView = WebView(context)
                controller.set(
                    DocumentPreviewWebController(
                        context = context,
                        webView = webView,
                        resourceRoot = resourceRoot,
                        onExternalLink = {},
                        onPageFinished = {
                            observedTitle.set(webView.title)
                            observedUrl.set(webView.url)
                            pageFinished.countDown()
                        },
                    ).also {
                        val renderedPreview = DocumentPreviewRenderer().renderMarkdown(
                            markdown = """
                                # Document preview loaded

                                <div align="center">
                                  <details><summary>Preview details</summary>Safe content</details>
                                </div>

                                | Name | Value |
                                | --- | --- |
                                | One | Two |
                            """.trimIndent(),
                            stylesheetName = "github-light.css",
                            customCss = null,
                        ).replace(
                            "<head>",
                            "<head><title>$EXPECTED_DOCUMENT_TITLE</title>",
                        )
                        it.show(
                            renderedPreview,
                        )
                    },
                )
            }

            assertTrue(
                "Timed out waiting for the generated preview document",
                pageFinished.await(PAGE_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            instrumentation.waitForIdleSync()
            assertEquals(EXPECTED_DOCUMENT_TITLE, observedTitle.get())
            assertEquals(DocumentPreviewWebOrigin.DOCUMENT_URL, observedUrl.get())
        } finally {
            instrumentation.runOnMainSync {
                controller.getAndSet(null)?.destroy()
            }
            resourceRoot.delete()
        }
    }

    @Test
    fun untrustedDocumentCapabilitiesAreDisabled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val resourceRoot = File(
            targetContext.cacheDir,
            "document-preview-web-test-${System.nanoTime()}",
        )
        check(resourceRoot.mkdirs())

        try {
            instrumentation.runOnMainSync {
                val context = ContextThemeWrapper(targetContext, R.style.AppTheme)
                val webView = WebView(context)
                val controller = DocumentPreviewWebController(
                    context = context,
                    webView = webView,
                    resourceRoot = resourceRoot,
                    onExternalLink = {},
                )
                try {
                    webView.settings.apply {
                        assertFalse(javaScriptEnabled)
                        assertFalse(javaScriptCanOpenWindowsAutomatically)
                        assertFalse(domStorageEnabled)
                        assertFalse(databaseEnabled)
                        assertFalse(allowFileAccess)
                        assertFalse(allowContentAccess)
                        assertFalse(allowFileAccessFromFileURLs)
                        assertFalse(allowUniversalAccessFromFileURLs)
                        assertFalse(blockNetworkImage)
                        assertFalse(blockNetworkLoads)
                        assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, mixedContentMode)
                    }
                } finally {
                    controller.destroy()
                }
            }
        } finally {
            resourceRoot.delete()
        }
    }

    companion object {
        private const val EXPECTED_DOCUMENT_TITLE = "document-preview-load-ok"
        private const val PAGE_LOAD_TIMEOUT_SECONDS = 20L
    }
}
