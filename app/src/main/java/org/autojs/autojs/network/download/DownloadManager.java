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

import org.autojs.autojs.concurrent.VolatileBox;
import org.autojs.autojs.network.api.DownloadApi;
import org.autojs.autojs.network.entity.VersionInfo;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.pref.Language;
import org.autojs.autojs.tool.SimpleObserver;
import org.autojs.autojs.util.StreamUtils;
import org.autojs.autojs.util.UpdateUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.concurrent.ConcurrentHashMap;

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
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

/**
 * Created by Stardust on 2017/10/20.
 */
public class DownloadManager {

    private static DownloadManager sInstance;

    private final int mRetryCount = 3;

    // 10,000 KB (around but less than 10 MB)
    private final int mMegaThreshold = 10000 * (1 << 10);
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
                .baseUrl(UpdateUtils.BASE_URL_RAW)
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

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    public Observable<ProgressInfo> download(String url, String path) {
        DownloadTask task = new DownloadTask(url, path);
        mDownloadApi.download(url)
                .subscribeOn(Schedulers.io())
                .subscribe(task::start, error -> task.progress().onError(error));
        return task.progress();
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
        return download(context, url, path);
    }

    public Observable<File> downloadWithProgress(Context context, String url, String path, String content) {
        initProgressDialog(context, url, content);
        return download(context, url, path);
    }

    private void initProgressDialog(Context context, String url, @Nullable VersionInfo versionInfo, @Nullable String content) {
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
                            UpdateUtils.openUrl(context, url);
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

        if (versionInfo != null && versionInfo.getSize() > 0) {
            long size = versionInfo.getSize();
            if (size > mMegaThreshold) {
                mProgressDialog.setProgressNumberFormat(getProgressMegaBytesFormat(context, 0, (float) (size / Math.pow(2, 20))));
            } else {
                mProgressDialog.setProgressNumberFormat(getProgressKiloBytesFormat(context, 0, (float) (size / Math.pow(2, 10))));
            }
        } else {
            mProgressDialog.setProgressNumberFormat(context.getString(R.string.text_half_ellipsis));
        }

        ProgressBar progressBar = mProgressDialog.getProgressBar();
        progressBar.setProgressTintList(ColorStateList.valueOf(context.getColor(R.color.dialog_progress_download_tint)));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.dialog_progress_download_bg_tint)));

    }

    private void initProgressDialog(Context context, String url, String content) {
        initProgressDialog(context, url, null, content);
    }

    private void initProgressDialog(Context context, VersionInfo versionInfo) {
        initProgressDialog(context, versionInfo.getDownloadUrl(), versionInfo, null);
    }

    @NonNull
    private Observable<File> download(Context context, String url, String path) {
        PublishSubject<File> subject = PublishSubject.create();
        DownloadManager downloadMgr = DownloadManager.getInstance();
        downloadMgr.download(url, path)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(o -> {
                    if (o.getTotalBytes() > mMegaThreshold) {
                        mProgressDialog.setProgressNumberFormat(getProgressMegaBytesFormat(context, o.getReadMegaBytes(), o.getTotalMegaBytes()));
                    } else {
                        mProgressDialog.setProgressNumberFormat(getProgressKiloBytesFormat(context, o.getReadKiloBytes(), o.getTotalKiloBytes()));
                    }
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
                        subject.onNext(new File(path));
                        subject.onComplete();
                    }

                    @Override
                    public void onError(Throwable error) {
                        mProgressDialog.dismiss();
                        mProgressDialog = null;
                        disposeIfNeeded();
                        mOkHttpClient.dispatcher().cancelAll();
                        subject.onError(error);
                    }
                });
        return subject;
    }

    public void cancelDownload(String url) {
        VolatileBox<Boolean> status = mDownloadStatuses.get(url);
        if (status != null) {
            status.set(false);
        }
    }

    private String getProgressKiloBytesFormat(Context context, float readKiloBytes, float totalKiloBytes) {
        return String.format(Language.getPrefLanguage().getLocale(),
                context.getString(R.string.format_dialog_progress_number_format_kilo_bytes),
                readKiloBytes, totalKiloBytes);
    }

    private String getProgressMegaBytesFormat(Context context, float readMegaBytes, float totalMegaBytes) {
        return String.format(Language.getPrefLanguage().getLocale(),
                context.getString(R.string.format_dialog_progress_number_format_mega_bytes),
                readMegaBytes, totalMegaBytes);
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
            button.setTextColor(mProgressDialog.getContext().getColor(R.color.dialog_progress_download_act_btn));
            button.setOnClickListener(view -> {
                mProgressDialog.dismiss();
                DownloadManager.getInstance().cancelDownload(mUrl);
                ViewUtils.showToast(mProgressDialog.getContext(), R.string.text_download_cancelled);
            });
        }

        private void onCancel() {
            recycle();
            // TODO: 2017/12/6 notify?
        }

        public void recycle() {
            // FIXME by SuperMonster003 on May 31, 2022.
            //  ! Seems like none of the ways below could stop the downloading process.
            //  ! Even worse, progress may stuck at around 99% and suspend.
            // disposeIfNeeded();
            // getOkHttpClient().dispatcher().cancelAll();
            // body.close();

            mDownloadStatuses.remove(mUrl);
            StreamUtils.closeSilently(mInputStream, mFileOutputStream);
        }

        public PublishSubject<ProgressInfo> progress() {
            return mProgress;
        }

    }

}
