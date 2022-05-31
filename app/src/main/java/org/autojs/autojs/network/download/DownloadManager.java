package org.autojs.autojs.network.download;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.util.Log;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.internal.MDButton;
import com.stardust.app.GlobalAppContext;
import com.stardust.concurrent.VolatileBox;
import com.stardust.pio.PFiles;

import org.autojs.autojs.network.api.DownloadApi;
import org.autojs.autojs.network.entity.VersionInfo;
import org.autojs.autojs.tool.SimpleObserver;
import org.autojs.autojs.tool.UpdateUtils;
import org.autojs.autojs6.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Locale;
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

    private static final String LOG_TAG = "DownloadManager";
    private static DownloadManager sInstance;
    private static final int RETRY_COUNT = 3;

    private final DownloadApi mDownloadApi;
    private final ConcurrentHashMap<String, VolatileBox<Boolean>> mDownloadStatuses = new ConcurrentHashMap<>();

    private Disposable mDisposable;

    public DownloadManager() {
        Retrofit mRetrofit = new Retrofit.Builder()
                .baseUrl(UpdateUtils.BASE_URL)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .client(getOkHttpClient())
                .build();

        mDownloadApi = mRetrofit.create(DownloadApi.class);
    }

    private OkHttpClient getOkHttpClient() {
        Interceptor interceptor = chain -> {
            Request request = chain.request();
            Response response = chain.proceed(request);
            int tryCount = 0;
            while (!response.isSuccessful() && tryCount < RETRY_COUNT) {
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

    public static String parseFileNameLocally(String url) {
        int i = url.lastIndexOf('-');
        if (i < 0) {
            i = url.lastIndexOf('/');
        }
        try {
            return URLDecoder.decode(url.substring(i + 1), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return GlobalAppContext.getString(R.string.text_should_never_happen);
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

    public Observable<File> downloadWithProgress(Context context, String url, String path, VersionInfo versionInfo) {
        return download(context, url, path, createDownloadProgressDialog(context, url, versionInfo, null));
    }

    public Observable<File> downloadWithProgress(Context context, String url, String path, String content) {
        return download(context, url, path, createDownloadProgressDialog(context, url, null, content));
    }

    private MaterialDialog createDownloadProgressDialog(Context context, String url, @Nullable VersionInfo versionInfo, @Nullable String content) {
        MaterialDialog d = new MaterialDialog.Builder(context)
                .title(context.getString(R.string.text_downloading))
                .positiveText(R.string.dialog_button_cancel_download)
                .onPositive((dialog, which) -> {
                    dialog.dismiss();
                    DownloadManager.getInstance().cancelDownload(url);
                })
                .progress(false, 100, true)
                .cancelable(false)
                .autoDismiss(false)
                .show();

        String contentText = versionInfo != null ? versionInfo.toString() : content;
        if (contentText != null) {
            d.setContent(contentText);
        }

        d.setProgressNumberFormat(context.getString(R.string.text_half_ellipsis));

        ProgressBar progressBar = d.getProgressBar();
        progressBar.setProgressTintList(ColorStateList.valueOf(context.getColor(R.color.dialog_progress_download_tint)));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.dialog_progress_download_bg_tint)));

        MDButton positiveButton = d.getActionButton(DialogAction.POSITIVE);
        positiveButton.setTextColor(context.getColor(R.color.dialog_progress_download_act_btn));

        return d;
    }

    private Observable<File> download(Context context, String url, String path, MaterialDialog progressDialog) {
        PublishSubject<File> subject = PublishSubject.create();
        DownloadManager downloadMgr = DownloadManager.getInstance();
        downloadMgr.download(url, path)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(o -> {
                    // 10,000 KB (around but less than 10 MB)
                    int megaThreshold = 10000 * (1 << 10);
                    if (o.getTotalBytes() > megaThreshold) {
                        progressDialog.setProgressNumberFormat(String.format(Locale.getDefault(),
                                context.getString(R.string.format_dialog_progress_number_format_mega_bytes),
                                o.getReadMegaBytes(), o.getTotalMegaBytes()));
                    } else {
                        progressDialog.setProgressNumberFormat(String.format(Locale.getDefault(),
                                context.getString(R.string.format_dialog_progress_number_format_kilo_bytes),
                                o.getReadKiloBytes(), o.getTotalKiloBytes()));
                    }
                    progressDialog.setProgress(o.getProgress());
                })
                .subscribe(new SimpleObserver<>() {
                    @Override
                    public void onSubscribe(Disposable disposable) {
                        mDisposable = disposable;
                    }

                    @Override
                    public void onComplete() {
                        progressDialog.dismiss();
                        subject.onNext(new File(path));
                        subject.onComplete();
                    }

                    @Override
                    public void onError(Throwable error) {
                        Log.e(LOG_TAG, "Download failed", error);
                        progressDialog.dismiss();
                        disposeIfNeeded();
                        getOkHttpClient().dispatcher().cancelAll();
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

    public boolean isCancelled(String url) {
        VolatileBox<Boolean> status = mDownloadStatuses.get(url);
        return status != null && !status.get();
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
                    return;
                }
                int len = mInputStream.read(buffer);
                if (len == -1) {
                    break;
                }
                o.incrementRead(len);
                mFileOutputStream.write(buffer, 0, len);
                if (o.getTotalBytes() > 0) {
                    mProgress.onNext(o);
                }
            }
            mProgress.onComplete();
            recycle();
        }

        public void start(ResponseBody body) throws Exception {
            PFiles.ensureDir(mPath);
            startImpl(body);
        }

        private void onCancel() {
            GlobalAppContext.toast(R.string.text_download_cancelled);
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
            try {
                mInputStream.close();
            } catch (IOException ignored) {
                // Ignored.
            }
            try {
                mFileOutputStream.close();
            } catch (IOException ignored) {
                // Ignored.
            }
        }

        public PublishSubject<ProgressInfo> progress() {
            return mProgress;
        }

    }
}
