package org.autojs.autojs.network.download;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.internal.MDButton;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.autojs.autojs.app.DialogUtils;
import org.autojs.autojs.concurrent.VolatileBox;
import org.autojs.autojs.network.UpdateChecker;
import org.autojs.autojs.network.api.DownloadApi;
import org.autojs.autojs.network.entity.VersionInfo;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.tool.SimpleObserver;
import org.autojs.autojs.util.IntentUtils;
import org.autojs.autojs.util.IntentUtils.ToastExceptionHolder;
import org.autojs.autojs.util.StreamUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Stardust on Oct 20, 2017.
 */
public class DownloadManager {

    private static DownloadManager sInstance;

    private final int mRetryCount = 3;

    private final Handler mHandler;
    private final DownloadApi mDownloadApi;
    private final ConcurrentHashMap<String, VolatileBox<Boolean>> mDownloadStatuses = new ConcurrentHashMap<>();
    private final OkHttpClient mOkHttpClient;

    private MaterialDialog mProgressDialog;
    private Disposable mDisposable;

    public DownloadManager() {
        mHandler = new Handler(Looper.getMainLooper());
        mOkHttpClient = getOkHttpClient();

        Retrofit mRetrofit = new Retrofit.Builder()
                .baseUrl(UpdateChecker.URL_BASE_GITHUB_RAW)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .client(mOkHttpClient)
                .build();

        mDownloadApi = mRetrofit.create(DownloadApi.class);
    }

    @NonNull
    private OkHttpClient getOkHttpClient() {
        Interceptor interceptor = chain -> {
            Request request = chain.request();
            Response response = chain.proceed(request);
            int tryCount = 0;
            while (!response.isSuccessful() && tryCount < mRetryCount) {
                tryCount++;
                response = chain.proceed(request);
            }
            return response;
        };
        return new OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .build();
    }

    public static DownloadManager getInstance() {
        if (sInstance == null) {
            sInstance = new DownloadManager();
        }
        return sInstance;
    }

    public void disposeIfNeeded() {
        if (mDisposable != null) {
            mDisposable.dispose();
        }
    }

    @SuppressWarnings("CharsetObjectCanBeUsed")
    public static String parseFileNameLocally(String url) {
        int i = url.lastIndexOf('-');
        if (i < 0) {
            i = url.lastIndexOf('/');
        }
        try {
            return URLDecoder.decode(url.substring(i + 1), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    public Observable<File> downloadWithProgress(Context context, String url, String path) {
        String content = context.getString(R.string.text_file_name) + ": " + DownloadManager.parseFileNameLocally(url);
        return downloadWithProgress(context, url, path, content);
    }

    public Observable<File> downloadWithProgress(Context context, VersionInfo versionInfo) {
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        final String path = new File(downloadDir, versionInfo.getFileName()).getPath();
        String url = versionInfo.getDownloadUrl();
        initProgressDialog(context, versionInfo);
        return download(url, path);
    }

    public Observable<File> downloadWithProgress(Context context, String url, String path, String content) {
        initProgressDialog(context, url, content);
        return download(url, path);
    }

    private void initProgressDialog(Context context, @NonNull String url, @Nullable VersionInfo versionInfo, @Nullable String content) {
        mProgressDialog = new MaterialDialog.Builder(context)
                .title(context.getString(R.string.text_downloading))
                .neutralText(R.string.dialog_button_download_with_browser)
                .neutralColorRes(R.color.dialog_button_hint)
                .onNeutral((dialog, which) -> new MaterialDialog.Builder(context)
                        .title(R.string.text_prompt)
                        .content(R.string.text_download_interruption_warning)
                        .negativeText(R.string.dialog_button_back)
                        .negativeColorRes(R.color.dialog_button_hint)
                        .positiveText(R.string.dialog_button_continue)
                        .positiveColorRes(R.color.dialog_button_caution)
                        .onPositive((d2, which2) -> {
                            dialog.getActionButton(DialogAction.POSITIVE).performClick();
                            IntentUtils.browse(context, url, new ToastExceptionHolder(context));
                        })
                        .cancelable(false)
                        .build()
                        .show())
                .positiveText(R.string.dialog_button_cancel_download)
                .positiveColorRes(R.color.dialog_button_unavailable)
                .progress(false, 100, true)
                .cancelable(false)
                .autoDismiss(false)
                .show();

        String contentText = versionInfo != null ? versionInfo.toString() : content;
        if (contentText != null) {
            mProgressDialog.setContent(contentText);
        }

        if (versionInfo != null) {
            long size = versionInfo.getSize();
            DialogUtils.setProgressNumberFormatByBytes(mProgressDialog, 0, size, context.getString(R.string.text_half_ellipsis));
        }

        DialogUtils.applyProgressThemeColorTintLists(mProgressDialog);
    }

    private void initProgressDialog(Context context, String url, String content) {
        initProgressDialog(context, url, null, content);
    }

    private void initProgressDialog(Context context, VersionInfo versionInfo) {
        initProgressDialog(context, versionInfo.getDownloadUrl(), versionInfo, null);
    }

    @SuppressLint("CheckResult")
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private Observable<File> download(String url, String path) {

        PublishSubject<File> downloadSubject = PublishSubject.create();

        DownloadTask task = new DownloadTask(url, path);
        PublishSubject<ProgressInfo> progressSubject = task.progress();

        mDownloadApi.download(url)
                .subscribeOn(Schedulers.io())
                .subscribe(task::start, progressSubject::onError);

        progressSubject
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(o -> {
                    DialogUtils.setProgressNumberFormatByBytes(mProgressDialog, o.getReadBytes(), o.getTotalBytes());
                    mProgressDialog.setProgress(o.getProgress());
                })
                .subscribe(new SimpleObserver<>() {
                    @Override
                    public void onSubscribe(Disposable disposable) {
                        mDisposable = disposable;
                    }

                    @Override
                    public void onComplete() {
                        mProgressDialog.dismiss();
                        mProgressDialog = null;
                        downloadSubject.onNext(new File(path));
                        downloadSubject.onComplete();
                    }

                    @Override
                    public void onError(Throwable error) {
                        mProgressDialog.dismiss();
                        mProgressDialog = null;
                        disposeIfNeeded();
                        mOkHttpClient.dispatcher().cancelAll();
                        downloadSubject.onError(error);
                    }
                });
        return downloadSubject;
    }

    public void cancelDownload(String url) {
        VolatileBox<Boolean> status = mDownloadStatuses.get(url);
        if (status != null) {
            status.set(false);
        }
    }

    private class DownloadTask {

        private final String mUrl;
        private final String mPath;
        private final VolatileBox<Boolean> mStatus;
        private InputStream mInputStream;
        private FileOutputStream mFileOutputStream;
        private final PublishSubject<ProgressInfo> mProgress;

        public DownloadTask(String url, String path) {
            mUrl = url;
            mPath = path;
            mStatus = new VolatileBox<>(true);
            VolatileBox<Boolean> previous = mDownloadStatuses.put(mUrl, mStatus);
            if (previous != null) {
                previous.set(false);
            }
            mProgress = PublishSubject.create();
        }

        private void startImpl(ResponseBody body) throws Exception {
            byte[] buffer = new byte[4096];
            mFileOutputStream = new FileOutputStream(mPath);
            mInputStream = body.byteStream();
            long total = body.contentLength();

            ProgressInfo o = new ProgressInfo(total);

            while (true) {
                if (!mStatus.get()) {
                    onCancel();
                    break;
                }
                int len = mInputStream.read(buffer);
                if (len == -1) {
                    mProgress.onComplete();
                    recycle();
                    break;
                }
                o.incrementRead(len);
                mFileOutputStream.write(buffer, 0, len);
                if (o.getTotalBytes() > 0) {
                    mProgress.onNext(o);
                }
            }
        }

        public void start(ResponseBody body) throws Exception {
            PFiles.ensureDir(mPath);
            mHandler.post(this::activeProgressDialogButton);
            startImpl(body);
        }

        private void activeProgressDialogButton() {
            MDButton button = mProgressDialog.getActionButton(DialogAction.POSITIVE);
            button.setTextColor(mProgressDialog.getContext().getColor(R.color.dialog_button_caution));
            button.setOnClickListener(view -> {
                mProgressDialog.dismiss();
                DownloadManager.getInstance().cancelDownload(mUrl);
                ViewUtils.showToast(mProgressDialog.getContext(), R.string.text_download_cancelled);
            });
        }

        private void onCancel() {
            recycle();
            // TODO by Stardust on Dec 6, 2017.
            //  ! notify?
        }

        public void recycle() {
            // FIXME by SuperMonster003 on May 31, 2022.
            //  ! Seems like none of the ways below could stop the downloading process.
            //  ! Even worse, progress may stuck at around 99% and suspend.
            //  ! zh-CN:
            //  ! 看起来下面几种方法均无法停止下载进程.
            //  ! 更糟的是, 下载进程可能在 99% 附近卡住.
            //  !
            //  # [1] disposeIfNeeded();
            //  # [2] getOkHttpClient().dispatcher().cancelAll();
            //  # [3] body.close();

            mDownloadStatuses.remove(mUrl);
            StreamUtils.closeSilently(mInputStream, mFileOutputStream);
        }

        public PublishSubject<ProgressInfo> progress() {
            return mProgress;
        }

    }

}
