package org.autojs.autojs.ui.widget;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.AttributeSet;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import org.autojs.autojs.app.OnActivityResultDelegate;
import org.autojs.autojs.tool.ImageSelector;
import org.autojs.autojs.util.ContextUtils;
import org.autojs.autojs.util.IntentUtils;
import org.autojs.autojs6.R;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Created by Stardust on Aug 22, 2017.
 */
public class EWebView extends FrameLayout implements SwipeRefreshLayout.OnRefreshListener, OnActivityResultDelegate {

    private static final List<String> IMAGE_TYPES = Arrays.asList("png", "jpg", "bmp");

    private NestedWebView mWebView;
    private ProgressBar mProgressBar;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private volatile RequestInterceptor mRequestInterceptor;
    private final List<OnPageFinishedListener> mOnPageFinishedListeners = new CopyOnWriteArrayList<>();

    public interface RequestInterceptor {
        WebResourceResponse intercept(WebResourceRequest request);
    }

    public interface OnPageFinishedListener {
        void onPageFinished(WebView view, String url);
    }

    public EWebView(Context context) {
        super(context);
        init();
    }

    public EWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SwipeRefreshLayout getSwipeRefreshLayout() {
        return mSwipeRefreshLayout;
    }

    private void init() {
        inflate(getContext(), R.layout.ewebview, this);

        mWebView = findViewById(R.id.web_view);
        setUpWebView();

        mSwipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(this);

        mProgressBar = findViewById(R.id.progress_bar);
    }

    private void setUpWebView() {
        WebSettings settings = mWebView.getSettings();
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setLoadWithOverviewMode(true);
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setDomStorageEnabled(true);
        settings.setDisplayZoomControls(false);
        mWebView.setWebViewClient(new MyWebViewClient());
        mWebView.setWebChromeClient(new MyWebChromeClient());
    }

    public NestedWebView getWebView() {
        return mWebView;
    }

    /**
     * Installs a request interceptor without replacing the WebViewClient owned by this widget.
     * Returning {@code null} delegates the request to WebView's normal loading path.
     */
    public void setRequestInterceptor(RequestInterceptor interceptor) {
        mRequestInterceptor = interceptor;
    }

    public void addOnPageFinishedListener(OnPageFinishedListener listener) {
        if (listener != null) {
            mOnPageFinishedListeners.add(listener);
        }
    }

    public void removeOnPageFinishedListener(OnPageFinishedListener listener) {
        mOnPageFinishedListeners.remove(listener);
    }

    public void evalJavaScript(String script) {
        mWebView.evaluateJavascript(script, null);
    }

    @SuppressLint("CheckResult")
    @Override
    public void onRefresh() {
        mWebView.reload();
        Observable.timer(2, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(t -> mSwipeRefreshLayout.setRefreshing(false));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        /* Empty body. */
    }

    protected class MyWebChromeClient extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            mProgressBar.setProgress(newProgress);
        }

        // For Android  >= 4.1
        public void openFileChooser(ValueCallback<Uri> valueCallback,
                                    String acceptType, String capture) {
            if (acceptType == null) {
                openFileChooser(valueCallback, null);
            } else {
                openFileChooser(valueCallback, acceptType.split(","));
            }
        }

        public boolean openFileChooser(ValueCallback<Uri> valueCallback,
                                       String[] acceptType) {
            Activity activity = ContextUtils.findActivity(getContext());
            if (activity instanceof OnActivityResultDelegate.DelegateHost &&
                isImageType(acceptType)) {
                chooseImage(activity, valueCallback);
                return true;
            }
            return false;
        }

        @Override
        public boolean onShowFileChooser(WebView webView,
                                         ValueCallback<Uri[]> filePathCallback,
                                         WebChromeClient.FileChooserParams fileChooserParams) {
            return openFileChooser(value -> {
                if (value == null) {
                    filePathCallback.onReceiveValue(null);
                } else {
                    filePathCallback.onReceiveValue(new Uri[]{value});
                }
            }, fileChooserParams.getAcceptTypes());
        }

    }

    private void chooseImage(Activity activity, ValueCallback<Uri> valueCallback) {
        DelegateHost delegateHost = ((OnActivityResultDelegate.DelegateHost) activity);
        Mediator mediator = delegateHost.getOnActivityResultDelegateMediator();
        new ImageSelector(activity, mediator, (selector, uri) -> valueCallback.onReceiveValue(uri))
                .disposable()
                .select();
    }

    private boolean isImageType(String[] acceptTypes) {
        if (acceptTypes == null) {
            return false;
        }
        for (String acceptType : acceptTypes) {
            if (acceptType == null) {
                continue;
            }
            String normalizedType = acceptType.toLowerCase(Locale.ROOT);
            if (normalizedType.startsWith("image/")) {
                return true;
            }
            for (String imageType : IMAGE_TYPES) {
                if (normalizedType.contains(imageType)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected class MyWebViewClient extends WebViewClient {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            mProgressBar.setProgress(0);
            mProgressBar.setVisibility(VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            mProgressBar.setVisibility(GONE);
            mSwipeRefreshLayout.setRefreshing(false);
            for (OnPageFinishedListener listener : mOnPageFinishedListeners) {
                listener.onPageFinished(view, url);
            }
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            RequestInterceptor interceptor = mRequestInterceptor;
            if (interceptor != null) {
                WebResourceResponse response = interceptor.intercept(request);
                if (response != null) {
                    return response;
                }
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return shouldOverrideUrlLoading(view, request.getUrl().toString());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://")) {
                view.loadUrl(url);
            } else {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                List<ResolveInfo> intentActivities = getContext().getPackageManager().queryIntentActivities(intent, 0);
                if (intentActivities.isEmpty()) {
                    return false;
                }
                return IntentUtils.startSafely(Intent.createChooser(intent, getResources().getString(R.string.text_open_with)), getContext());
            }
            return true;
        }

    }

}
