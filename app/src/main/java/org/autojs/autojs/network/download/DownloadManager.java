package org.autojs.autojs.network.download;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
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
import io.reactivex.subjects.Subject;
import okhttp3.Call;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.autojs.autojs.util.DialogUtils;
import org.autojs.autojs.network.UpdateChecker;
import org.autojs.autojs.network.api.DownloadApi;
import org.autojs.autojs.network.entity.VersionInfo;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.util.IntentUtils;
import org.autojs.autojs.util.IntentUtils.ToastExceptionHolder;
import org.autojs.autojs.util.StreamUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Stardust on Oct 20, 2017.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 2, 2026.
 * Modified by OpenAI ChatGPT (GPT-5.2 Thinking) as of Feb 2, 2026.
 * Modified by SuperMonster003 as of Feb 3, 2026.
 */
public class DownloadManager {

    private static DownloadManager sInstance;

    private final int mRetryCount = 3;
    private final Handler mHandler;
    private final DownloadApi mDownloadApi;
    private final OkHttpClient mOkHttpClient;

    private MaterialDialog mProgressDialog;

    // Track active tasks so cancel can close streams to break blocking reads.
    // zh-CN: 跟踪活动任务, 以便 cancel 时可关闭流从而打断阻塞 read().
    private final ConcurrentHashMap<String, DownloadTask> mActiveTasks = new ConcurrentHashMap<>();

    /**
     * Partial file policy for cancellations/failures.
     * zh-CN: 用于取消/失败场景的未完成文件处理策略.
     */
    public enum PartialFilePolicy {
        DELETE_ALWAYS,
        DELETE_APK_ZIP_ONLY,
        KEEP_ALWAYS
    }

    private volatile PartialFilePolicy mPartialFilePolicy = PartialFilePolicy.DELETE_ALWAYS;

    /**
     * A dedicated exception type for user cancellations.
     * zh-CN: 专用于用户取消的异常类型.
     */
    private static final class DownloadCancelledException extends CancellationException {
        DownloadCancelledException(String message) {
            super(message);
        }
    }

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
                try {
                    response.close();
                } catch (Throwable ignored) {
                    /* Ignored. */
                }
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

    public void setPartialFilePolicy(@NonNull PartialFilePolicy policy) {
        mPartialFilePolicy = policy;
    }

    @NonNull
    public PartialFilePolicy getPartialFilePolicy() {
        return mPartialFilePolicy;
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

    /**
     * Dismiss progress dialog safely to avoid races with async progress updates.
     * zh-CN: 安全关闭进度对话框, 以避免与异步进度更新产生竞态.
     */
    private void dismissProgressDialogSafely() {
        try {
            MaterialDialog dialog = mProgressDialog;
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        } catch (Throwable ignored) {
            /* Ignored. */
        } finally {
            mProgressDialog = null;
        }
    }

    /**
     * Cancel only OkHttp calls matching the given url instead of cancelAll().
     * zh-CN: 仅取消与 url 匹配的 OkHttp 请求, 而不是使用 cancelAll().
     */
    private void cancelOkHttpCallsByUrl(@NonNull String url) {
        try {
            for (Call call : mOkHttpClient.dispatcher().queuedCalls()) {
                if (url.equals(call.request().url().toString())) {
                    call.cancel();
                }
            }
            for (Call call : mOkHttpClient.dispatcher().runningCalls()) {
                if (url.equals(call.request().url().toString())) {
                    call.cancel();
                }
            }
        } catch (Throwable ignored) {
            /* Ignored. */
        }
    }

    private boolean shouldDeletePartialFile(@NonNull String path) {
        PartialFilePolicy policy = getPartialFilePolicy();
        if (policy == PartialFilePolicy.KEEP_ALWAYS) return false;
        if (policy == PartialFilePolicy.DELETE_ALWAYS) return true;

        String name = new File(path).getName().toLowerCase(Locale.US);
        return name.endsWith(".apk") || name.endsWith(".zip");
    }

    private void deleteFileSilentlyAsync(@NonNull String path) {
        Schedulers.io().scheduleDirect(() -> {
            try {
                File f = new File(path);
                if (f.exists()) {
                    // noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            } catch (Throwable ignored) {
                /* Ignored. */
            }
        });
    }

    /**
     * Simplified single-source Observable without PublishSubject<File>.
     * zh-CN: 通过 Observable.create() 精简结构, 不再使用 PublishSubject<File>.
     */
    @SuppressLint("CheckResult")
    private Observable<File> download(String url, String path) {
        return Observable.create(emitter -> {

            DownloadTask task = new DownloadTask(url, path);

            DownloadTask previous = mActiveTasks.put(url, task);
            if (previous != null) {
                previous.cancel();
            }

            // Downstream dispose should behave like user cancellation.
            // zh-CN: 下游 dispose() 应与用户取消行为一致.
            emitter.setCancellable(() -> cancelDownload(url));

            Disposable progressDisposable = task.progress()
                    .sample(50, TimeUnit.MILLISECONDS, true)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            o -> {
                                MaterialDialog dialog = mProgressDialog;
                                if (dialog == null || !dialog.isShowing()) return;
                                DialogUtils.setProgressNumberFormatByBytes(dialog, o.getReadBytes(), o.getTotalBytes());
                                dialog.setProgress(o.getProgress());
                            },
                            error -> {
                                mActiveTasks.remove(url);
                                dismissProgressDialogSafely();

                                if (error instanceof DownloadCancelledException || error instanceof CancellationException) {
                                    // Cancellation is expected; cancel only the matching call.
                                    // zh-CN: 取消属于预期行为, 仅取消匹配的请求.
                                    cancelOkHttpCallsByUrl(url);
                                } else {
                                    cancelOkHttpCallsByUrl(url);
                                    if (shouldDeletePartialFile(path)) {
                                        deleteFileSilentlyAsync(path);
                                    }
                                }

                                if (!emitter.isDisposed()) {
                                    emitter.onError(error);
                                }
                            },
                            () -> {
                                mActiveTasks.remove(url);
                                dismissProgressDialogSafely();
                                if (!emitter.isDisposed()) {
                                    emitter.onNext(new File(path));
                                    emitter.onComplete();
                                }
                            }
                    );

            Disposable networkDisposable = mDownloadApi.download(url)
                    .subscribeOn(Schedulers.io())
                    .subscribe(task::start, task::fail);

            task.setNetworkDisposable(networkDisposable);
            task.setProgressDisposable(progressDisposable);
        });
    }

    /**
     * Cancel by url. Button click and downstream dispose both arrive here.
     * zh-CN: 按 url 取消, 按钮点击与下游 dispose 都会走到这里.
     */
    public void cancelDownload(String url) {
        DownloadTask task = mActiveTasks.get(url);
        if (task != null) {
            task.cancel();
        } else {
            cancelOkHttpCallsByUrl(url);
        }
    }

    private class DownloadTask {

        private final String mUrl;
        private final String mPath;

        private InputStream mInputStream;
        private FileOutputStream mFileOutputStream;
        private BufferedOutputStream mBufferedOutputStream;

        private final Subject<ProgressInfo> mProgress;

        private Disposable mNetworkDisposable;
        private Disposable mProgressDisposable;

        private final AtomicBoolean mCancelled = new AtomicBoolean(false);
        private final AtomicBoolean mTerminated = new AtomicBoolean(false);

        // Larger IO buffer for higher throughput.
        // zh-CN: 更大的 IO 缓冲区, 用于提升吞吐.
        private static final int IO_BUFFER_SIZE = 256 * 1024;

        // Minimum interval between progress emissions to avoid Rx/UI overhead.
        // zh-CN: 进度上报的最小间隔, 用于避免 Rx/UI 开销过大.
        private static final long PROGRESS_EMIT_MIN_INTERVAL_MS = 50L;

        public DownloadTask(String url, String path) {
            mUrl = url;
            mPath = path;
            mProgress = PublishSubject.<ProgressInfo>create().toSerialized();
        }

        public void setNetworkDisposable(@Nullable Disposable disposable) {
            mNetworkDisposable = disposable;
        }

        public void setProgressDisposable(@Nullable Disposable disposable) {
            mProgressDisposable = disposable;
        }

        public Observable<ProgressInfo> progress() {
            return mProgress;
        }

        public void fail(Throwable error) {
            terminate(error);
        }

        public void start(ResponseBody body) {
            PFiles.ensureDir(mPath);
            mHandler.post(this::activeProgressDialogButton);
            startImpl(body);
        }

        private void startImpl(ResponseBody body) {
            byte[] buffer = new byte[IO_BUFFER_SIZE];
            long total = body.contentLength();
            ProgressInfo o = new ProgressInfo(total);
            long lastEmitAt = 0L;

            try (ResponseBody ignoredBody = body) {
                mFileOutputStream = new FileOutputStream(mPath);
                mBufferedOutputStream = new BufferedOutputStream(mFileOutputStream, IO_BUFFER_SIZE);
                mInputStream = body.byteStream();

                while (true) {
                    if (mCancelled.get()) {
                        cancel();
                        break;
                    }
                    int len = mInputStream.read(buffer);
                    if (len == -1) {
                        mBufferedOutputStream.flush();
                        terminate(null);
                        break;
                    }
                    o.incrementRead(len);
                    mBufferedOutputStream.write(buffer, 0, len);

                    if (o.getTotalBytes() > 0) {
                        long now = android.os.SystemClock.uptimeMillis();
                        if (now - lastEmitAt >= PROGRESS_EMIT_MIN_INTERVAL_MS) {
                            lastEmitAt = now;
                            mProgress.onNext(o);
                        }
                    }
                }
            } catch (Throwable t) {
                if (mCancelled.get()) {
                    terminate(new DownloadCancelledException("Download cancelled: " + mUrl));
                } else {
                    terminate(t);
                }
            } finally {
                recycle();
            }
        }

        public void cancel() {
            // Mark as cancelled first so read loop can stop ASAP.
            // zh-CN: 先标记为已取消, 让 read 循环尽快停止.
            if (!mCancelled.compareAndSet(false, true)) {
                return;
            }

            // Close streams off the main thread; close() may trigger network I/O (HTTP/2 RST_STREAM, TLS write).
            // zh-CN: 在非主线程关闭流, close() 可能触发网络 I/O (HTTP/2 RST_STREAM, TLS write).
            final InputStream in = mInputStream;
            final BufferedOutputStream bout = mBufferedOutputStream;
            final FileOutputStream fos = mFileOutputStream;
            Schedulers.io().scheduleDirect(() -> StreamUtils.closeSilently(in, bout, fos));

            Disposable nd = mNetworkDisposable;
            if (nd != null && !nd.isDisposed()) {
                nd.dispose();
            }

            Disposable pd = mProgressDisposable;
            if (pd != null && !pd.isDisposed()) {
                pd.dispose();
            }

            cancelOkHttpCallsByUrl(mUrl);

            onCancel();

            terminate(new DownloadCancelledException("Download cancelled: " + mUrl));
        }

        private void activeProgressDialogButton() {
            MaterialDialog dialog = mProgressDialog;
            if (dialog == null || !dialog.isShowing()) {
                return;
            }
            MDButton button = dialog.getActionButton(DialogAction.POSITIVE);
            button.setTextColor(dialog.getContext().getColor(R.color.dialog_button_caution));
            button.setOnClickListener(view -> {
                dismissProgressDialogSafely();
                DownloadManager.getInstance().cancelDownload(mUrl);
                ViewUtils.showToast(dialog.getContext(), R.string.text_download_cancelled);
            });
        }

        private void onCancel() {
            // TODO by Stardust on Dec 6, 2017.
            //  ! notify?

            if (shouldDeletePartialFile(mPath)) {
                deleteFileSilentlyAsync(mPath);
            }
        }

        private void terminate(@Nullable Throwable error) {
            if (!mTerminated.compareAndSet(false, true)) {
                return;
            }
            if (error == null) {
                mProgress.onComplete();
            } else {
                mProgress.onError(error);
            }
        }

        public void recycle() {
            StreamUtils.closeSilently(mInputStream, mBufferedOutputStream, mFileOutputStream);
        }
    }
}