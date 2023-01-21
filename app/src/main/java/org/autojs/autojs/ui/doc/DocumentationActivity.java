package org.autojs.autojs.ui.doc;

import android.webkit.WebView;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;
import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs.ui.widget.EWebView;
import org.autojs.autojs.util.DocsUtils;
import org.autojs.autojs6.R;

/**
 * Created by Stardust on 2017/10/24.
 */
@EActivity(R.layout.activity_documentation)
public class DocumentationActivity extends BaseActivity {

    public static final String EXTRA_URL = "url";

    @ViewById(R.id.eweb_view)
    EWebView mEWebView;

    WebView mWebView;

    @AfterViews
    void setUpViews() {
        setToolbarAsBack(R.string.text_tutorial);
        mWebView = mEWebView.getWebView();
        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null) {
            url = DocsUtils.getUrl("index.html");
        }
        mWebView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
