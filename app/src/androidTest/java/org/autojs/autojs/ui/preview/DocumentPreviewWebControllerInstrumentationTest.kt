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
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DocumentPreviewWebControllerInstrumentationTest {

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
}
