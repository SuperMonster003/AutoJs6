package org.autojs.autojs.ui.doc

import android.app.Activity
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.webkit.WebView
import androidx.annotation.Nullable
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.stardust.util.BackPressedHandler
import kotlinx.android.synthetic.main.fragment_online_docs.*
import org.autojs.autojs.Pref
import org.autojs.autojs.R
import org.autojs.autojs.ui.main.QueryEvent
import org.autojs.autojs.ui.main.ViewPagerFragment
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

/**
 * Created by Stardust on 2017/8/22.
 */
class DocsFragment : ViewPagerFragment(), BackPressedHandler {

    override val layout: Int
        get() = R.layout.fragment_online_docs

    override val fabRotation: Int
        get() = ViewPagerFragment.ROTATION_GONE


    private lateinit var mWebView: WebView

    private var mIndexUrl: String? = null
    private var mPreviousQuery: String? = null

    init {
        arguments = Bundle()
    }

    override fun onCreate(@Nullable savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mWebView = eweb_view.webView
        eweb_view.swipeRefreshLayout.setOnRefreshListener {
            if (TextUtils.equals(mWebView.url, mIndexUrl)) {
                loadUrl()
            } else {
                eweb_view.onRefresh()
            }
        }
        val savedWebViewState = arguments?.getBundle("savedWebViewState")
        if (savedWebViewState != null) {
            mWebView.restoreState(savedWebViewState)
        } else {
            loadUrl()
        }
    }

    private fun loadUrl() {
        mIndexUrl = arguments?.getString(ARGUMENT_URL, Pref.getDocumentationUrl() + "index.html")
        mWebView.loadUrl(mIndexUrl)
    }


    override fun onPause() {
        super.onPause()
        val savedWebViewState = Bundle()
        mWebView.saveState(savedWebViewState)
        arguments?.putBundle("savedWebViewState", savedWebViewState)
    }

    override fun onBackPressed(activity: Activity): Boolean {
        if (mWebView.canGoBack()) {
            mWebView.goBack()
            return true
        }
        return false
    }

    override fun onFabClick(fab: FloatingActionButton) {

    }

    @Subscribe
    fun onQuerySummit(event: QueryEvent) {
        if (!isShown) {
            return
        }
        if (event === QueryEvent.CLEAR) {
            mWebView.clearMatches()
            mPreviousQuery = null
            return
        }
        if (event.isFindForward) {
            mWebView.findNext(false)
            return
        }
        if (event.query == mPreviousQuery) {
            mWebView.findNext(true)
            return
        }
        mWebView.findAllAsync(event.query)
        mPreviousQuery = event.query
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    companion object {

        const val ARGUMENT_URL = "url"
    }
}
