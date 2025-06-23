package org.autojs.autojs.ui.doc

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import org.autojs.autojs.event.BackPressedHandler
import org.autojs.autojs.ui.doc.DocumentationActivity.Companion.generateInjectCodeForBottomInsets
import org.autojs.autojs.ui.fragment.BindingDelegates.viewBinding
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.ui.main.QueryEvent
import org.autojs.autojs.ui.main.ViewPagerFragment
import org.autojs.autojs.ui.main.ViewStatesManageable
import org.autojs.autojs.util.DocsUtils.getUrl
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.WebViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.FragmentOnlineDocsBinding
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

/**
 * Created by Stardust on Aug 22, 2017.
 * Modified by SuperMonster003 as of Mar 26, 2022.
 * Transformed by SuperMonster003 on Mar 31, 2023.
 */
class DocumentationFragment : ViewPagerFragment(ROTATION_GONE), BackPressedHandler, ViewStatesManageable {

    private val binding by viewBinding(FragmentOnlineDocsBinding::bind)

    private var mIndexUrl: String? = null
    private var mPreviousQuery: String? = null
    private var mIsCurrentPageDocs = false

    private lateinit var mWebView: WebView

    init {
        arguments = Bundle()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return FragmentOnlineDocsBinding.inflate(inflater, container, false).root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.ewebView.also { ewebView ->
            ewebView.webView.also { webView ->
                mWebView = webView
                webView.settings.setSupportMultipleWindows(true)
                if (ViewUtils.isNightModeYes(requireContext())) {
                    WebViewUtils.adaptDarkMode(webView)
                }
                WebViewUtils.excludeWebViewFromNavigationBar(ewebView, webView) {
                    generateInjectCodeForBottomInsets(it)
                }
            }
        }
        restoreViewStates()
        (activity as? MainActivity)?.apply {
            val tabLayout: TabLayout = findViewById(R.id.tab)
            val docsTab = tabLayout.getTabAt(docsItemIndex)
            docsTab?.view?.let { setTabViewClickListeners(it) }
        }
    }

    private fun loadMainPage() {
        arguments?.let { mIndexUrl = it.getString(ARGUMENT_URL, getUrl("index.html")) }
        mIndexUrl?.let { mWebView.loadUrl(it) }
    }

    override fun onPause() {
        super.onPause()
        saveViewStates()
    }

    override fun onBackPressed(activity: Activity): Boolean {
        mWebView.let {
            if (it.canGoBack()) {
                it.goBack()
                return true
            }
        }
        return false
    }

    override fun onFabClick(fab: FloatingActionButton) {}

    @Subscribe
    fun onQuerySummit(event: QueryEvent) = when {
        !isShown -> {}
        event === QueryEvent.CLEAR -> {
            mWebView.clearMatches()
            mPreviousQuery = null
        }
        event === QueryEvent.FIND_FORWARD -> mWebView.findNext(true)
        event.isFindBackward -> mWebView.findNext(false)
        event.query == mPreviousQuery -> mWebView.findNext(true)
        else -> {
            mWebView.findAllAsync(event.query)
            mPreviousQuery = event.query
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    override fun onPageShow() {
        super.onPageShow()
        mIsCurrentPageDocs = true
    }

    override fun onPageHide() {
        super.onPageHide()
        mIsCurrentPageDocs = false
    }

    override fun saveViewStates() {
        Bundle().let {
            mWebView.saveState(it)
            arguments?.putBundle("savedWebViewState", it)
        }
    }

    override fun restoreViewStates() {
        arguments?.getBundle("savedWebViewState")?.let { mWebView.restoreState(it) } ?: loadMainPage()
    }

    private fun setTabViewClickListeners(tabView: TabLayout.TabView) {
        tabView.setOnClickListener { if (mIsCurrentPageDocs) mWebView.scrollTo(0, 0) }
        tabView.setOnLongClickListener { if (mIsCurrentPageDocs) true.also { loadMainPage() } else false }
    }

    companion object {

        const val ARGUMENT_URL = "url"

    }

}