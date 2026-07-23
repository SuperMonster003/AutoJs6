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
import org.autojs.autojs.util.DocsUtils.INDEX_URL_SUFFIX
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
    private lateinit var mPageController: DocumentationPageController

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
                webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                webView.settings.setSupportMultipleWindows(true)
                if (ViewUtils.isNightModeYes(requireContext())) {
                    WebViewUtils.adaptDarkMode(webView)
                }
                WebViewUtils.excludeWebViewFromNavigationBar(ewebView, webView) {
                    generateInjectCodeForBottomInsets(it)
                }
                webView.post { webView.invalidate() }
            }
            mPageController = DocumentationPageController(requireContext(), binding.root, ewebView)
        }
        restoreViewStates()
        (activity as? MainActivity)?.apply {
            val tabLayout: TabLayout = findViewById(R.id.tab)
            val docsTab = tabLayout.getTabAt(docsItemIndex)
            docsTab?.view?.let { setTabViewClickListeners(it) }
        }
    }

    private fun loadMainPage() {
        arguments?.let { mIndexUrl = it.getString(ARGUMENT_URL, getUrl(INDEX_URL_SUFFIX)) }
        mIndexUrl?.let { mPageController.loadUrl(it) }
        mPreviousQuery = null
        mWebView.clearMatches()
    }

    override fun onPause() {
        super.onPause()
        saveViewStates()
    }

    override fun onBackPressed(activity: Activity): Boolean {
        if (::mPageController.isInitialized && mPageController.canGoBack()) {
            mPageController.goBack()
            return true
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

    override fun onDestroyView() {
        if (::mPageController.isInitialized) mPageController.dispose()
        super.onDestroyView()
    }

    override fun onPageShow() {
        super.onPageShow()
        mIsCurrentPageDocs = true
        if (::mPageController.isInitialized) mPageController.refreshPluginState()
    }

    override fun onPageHide() {
        super.onPageHide()
        mIsCurrentPageDocs = false
    }

    override fun saveViewStates() {
        if (::mPageController.isInitialized) {
            arguments?.putString(KEY_SAVED_URL, mPageController.currentUrl)
        }
    }

    override fun restoreViewStates() {
        val savedUrl = arguments?.getString(KEY_SAVED_URL)
        val initialUrl = savedUrl ?: arguments?.getString(ARGUMENT_URL, getUrl(INDEX_URL_SUFFIX))
        mIndexUrl = initialUrl
        mPageController.start(initialUrl)
    }

    private fun setTabViewClickListeners(tabView: TabLayout.TabView) {
        tabView.setOnClickListener { if (mIsCurrentPageDocs) mWebView.scrollTo(0, 0) }
        tabView.setOnLongClickListener { if (mIsCurrentPageDocs) true.also { loadMainPage() } else false }
    }

    companion object {

        const val ARGUMENT_URL = "url"
        private const val KEY_SAVED_URL = "savedUrl"

    }

}
