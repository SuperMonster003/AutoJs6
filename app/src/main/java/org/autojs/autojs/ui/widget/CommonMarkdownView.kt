package org.autojs.autojs.ui.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.util.TextUtils.renderMarkdown

/**
 * Created by Stardust on Mar 5, 2017.
 * Transformed by SuperMonster003 on May 25, 2023.
 */
class CommonMarkdownView : WebView {

    interface OnPageFinishedListener {
        fun onPageFinished(view: WebView?, url: String?)
    }

    private var mMarkdownHtml: String? = null
    private var mPadding = "16px"
    private var mOnPageFinishedListener: OnPageFinishedListener? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    fun setPadding(padding: String) {
        mPadding = padding
    }

    init {
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                loadUrl("javascript:document.body.style.margin=\"$mPadding\"; void 0")
                mOnPageFinishedListener?.onPageFinished(view, url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                context.startActivity(Intent(Intent.ACTION_VIEW).setData(request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                context.startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            }
        }
    }

    fun loadMarkdown(markdown: String) {
        mMarkdownHtml = renderMarkdown(markdown).also { loadHtml(it) }
    }

    fun setOnPageFinishedListener(onPageFinishedListener: OnPageFinishedListener?) {
        mOnPageFinishedListener = onPageFinishedListener
    }

    private fun loadHtml(html: String) = loadDataWithBaseURL(null, html, "text/html", "utf-8", null)

    fun setText(resId: Int) = setText(context.getString(resId))

    private fun setText(text: String) = loadDataWithBaseURL(null, text, "text/plain", "utf-8", null)

    override fun goBack() {
        super.goBack()
        if (!canGoBack()) {
            mMarkdownHtml?.let { loadHtml(it) }
        }
    }

    class DialogBuilder(context: Context) : MaterialDialog.Builder(context) {

        private val mMarkdownView: CommonMarkdownView
        private val mContainer: FrameLayout

        init {
            mContainer = FrameLayout(context)
            mMarkdownView = CommonMarkdownView(context)
            mContainer.addView(mMarkdownView)
            mContainer.clipToPadding = true
            customView(mContainer, false)
        }

        fun padding(l: Int, t: Int, r: Int, b: Int) = also { mContainer.setPadding(l, t, r, b) }

        fun markdown(md: String) = also { mMarkdownView.loadMarkdown(md) }

    }
}