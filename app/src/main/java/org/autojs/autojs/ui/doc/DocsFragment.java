package org.autojs.autojs.ui.doc;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.webkit.WebView;

import androidx.annotation.Nullable;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.ViewById;
import org.autojs.autojs.event.BackPressedHandler;
import org.autojs.autojs.ui.main.QueryEvent;
import org.autojs.autojs.ui.main.ViewPagerFragment;
import org.autojs.autojs.ui.widget.EWebView;
import org.autojs.autojs.ui.widget.NestedWebView;
import org.autojs.autojs.util.DocsUtils;
import org.autojs.autojs.util.WebViewUtils;
import org.autojs.autojs6.R;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

/**
 * Created by Stardust on 2017/8/22.
 */
@EFragment(R.layout.fragment_online_docs)
public class DocsFragment extends ViewPagerFragment implements BackPressedHandler {

    public static final String ARGUMENT_URL = "url";

    @ViewById(R.id.eweb_view)
    EWebView mEWebView;
    NestedWebView mWebView;

    private String mIndexUrl;
    private String mPreviousQuery;
    private Context mContext;

    public DocsFragment() {
        super(ROTATION_GONE);
        setArguments(new Bundle());
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
        mContext = requireContext();
    }

    @AfterViews
    void setUpViews() {
        mWebView = mEWebView.getWebView();

        WebViewUtils.adaptDarkMode(mContext, mWebView);

        // mEWebView.getSwipeRefreshLayout().setOnRefreshListener(() -> {
        //     if (TextUtils.equals(mWebView.getUrl(), mIndexUrl)) {
        //         loadMainPage();
        //     } else {
        //         mEWebView.onRefresh();
        //     }
        // });

        Bundle savedWebViewState = getArguments().getBundle("savedWebViewState");
        if (savedWebViewState != null) {
            mWebView.restoreState(savedWebViewState);
        } else {
            loadMainPage();
        }
    }

    public void loadMainPage() {
        mIndexUrl = getArguments().getString(ARGUMENT_URL, DocsUtils.getUrl("index.html"));
        mWebView.loadUrl(mIndexUrl);
    }


    @Override
    public void onPause() {
        super.onPause();
        Bundle savedWebViewState = new Bundle();
        mWebView.saveState(savedWebViewState);
        getArguments().putBundle("savedWebViewState", savedWebViewState);
    }

    @Override
    public boolean onBackPressed(Activity activity) {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
            return true;
        }
        return false;
    }

    @Override
    protected void onFabClick(FloatingActionButton fab) {

    }

    @Subscribe
    public void onQuerySummit(QueryEvent event) {
        if (!isShown()) {
            return;
        }
        if (event == QueryEvent.CLEAR) {
            mWebView.clearMatches();
            mPreviousQuery = null;
            return;
        }
        if (event.isFindForward()) {
            mWebView.findNext(false);
            return;
        }
        if (event.getQuery().equals(mPreviousQuery)) {
            mWebView.findNext(true);
            return;
        }
        mWebView.findAllAsync(event.getQuery());
        mPreviousQuery = event.getQuery();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    // FIXME by SuperMonster003 on Aug 24, 2022.
    //  ! In normal circumstances, getWebView() should not be nullable.
    //  ! Exception occurs after switching night mode (any times over once).
    @Nullable
    public WebView getWebView() {
        return mWebView;
    }

}
