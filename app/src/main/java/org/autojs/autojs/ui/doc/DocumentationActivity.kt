package org.autojs.autojs.ui.doc

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.DocsUtils.getUrl
import org.autojs.autojs6.databinding.ActivityDocumentationBinding

/**
 * Created by Stardust on 2017/10/24.
 */
class DocumentationActivity : BaseActivity() {

    private lateinit var binding: ActivityDocumentationBinding

    private lateinit var mWebView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDocumentationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupViews()
    }

    private fun setupViews() {
        mWebView = binding.ewebView.webView.also {
            it.loadUrl(intent.getStringExtra(EXTRA_URL) ?: getUrl("index.html"))
        }
    }

    @SuppressLint("MissingSuperCall")
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack()
        } else {
            // super.onBackPressed()
            onBackPressedDispatcher.onBackPressed()
        }
    }

    companion object {

        const val EXTRA_URL = "url"

    }

}