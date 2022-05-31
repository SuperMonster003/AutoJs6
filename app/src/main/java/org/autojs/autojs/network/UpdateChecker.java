package org.autojs.autojs.network;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.internal.MDButton;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.gson.GsonBuilder;
import com.stardust.app.GlobalAppContext;
import com.stardust.autojs.core.ui.widget.CustomSnackbar;
import com.stardust.util.IntentUtil;

import org.autojs.autojs.Pref;
import org.autojs.autojs.external.fileprovider.AppFileProvider;
import org.autojs.autojs.network.api.UpdateCheckerApi;
import org.autojs.autojs.network.download.DownloadManager;
import org.autojs.autojs.network.entity.VersionInfo;
import org.autojs.autojs.tool.SimpleObserver;
import org.autojs.autojs6.R;
import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.MarkdownMode;
import org.kohsuke.github.PagedIterable;

import java.io.BufferedReader;
import java.io.File;
import java.io.Reader;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by Stardust on 2017/9/20.
 * Modified by SuperMonster003 as of Feb 26, 2022.
 */

public class UpdateChecker {

    private MaterialDialog mUpdateDialog;
    private MaterialDialog mPendingDialog;

    public enum PromptMode {NONE, DIALOG, SNACKBAR}

    private static final String TAG = UpdateChecker.class.getSimpleName();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Context mContext;
    private final View mView;
    private final String mBaseUrl;
    private final String mUrl;
    private final PromptMode mPromptMode;
    private final SimpleObserver<ResponseBody> mCallback;
    private final Executor mGitHubExecutor = Executors.newSingleThreadExecutor();

    private GitHub mGitHubConnection;
    private UpdateCheckerApi checkerApi;

    private UpdateChecker(Context context, View view, String baseUrl, String url, PromptMode promptMode, SimpleObserver<ResponseBody> callback) {
        mContext = context;
        mView = view;
        mBaseUrl = baseUrl;
        mUrl = url;
        mPromptMode = promptMode;
        mCallback = callback;
    }

    public void checkNow() {
        mPendingDialog = getPendingDialog(mContext, R.string.text_checking_update);

        if (mPromptMode == PromptMode.DIALOG) {
            mPendingDialog.show();
        }

        getCheckerApi()
                .checkForUpdates(mUrl)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SimpleObserver<>() {
                    @Override
                    public void onNext(ResponseBody responseBody) {
                        if (mPendingDialog.isShowing()) {
                            mPendingDialog.dismiss();
                        }
                        Pref.refreshLastUpdatesCheckedTimestamp();

                        if (mCallback != null) {
                            mCallback.onNext(responseBody);
                            return;
                        }

                        try {
                            Properties prop = new Properties();
                            prop.load(responseBody.byteStream());

                            String versionNameKey = mContext.getString(R.string.key_app_property_version_name);
                            String versionName = prop.getProperty(versionNameKey);

                            String versionCodeKey = mContext.getString(R.string.key_app_property_version_code);
                            int versionCode = Integer.parseInt(prop.getProperty(versionCodeKey));

                            VersionInfo versionInfo = new VersionInfo(versionName, versionCode);

                            switch (mPromptMode) {
                                case DIALOG -> {
                                    if (versionInfo.isNewer()) {
                                        showDialog(versionInfo);
                                    } else {
                                        GlobalAppContext.toast(R.string.text_is_latest_version);
                                    }
                                }
                                case SNACKBAR -> {
                                    if (versionInfo.isNewer()) {
                                        showSnackBar(versionInfo);
                                    }
                                }
                                default -> throw new IllegalStateException(mContext.getString(
                                        R.string.error_illegal_argument,
                                        "promptMode", mPromptMode));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            GlobalAppContext.toast(mContext.getString(R.string.error_failed_to_parse_version_info));
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        e.printStackTrace();

                        if (mPendingDialog.isShowing()) {
                            mPendingDialog.dismiss();
                        }

                        if (mCallback != null) {
                            mCallback.onError(e);
                            return;
                        }

                        if (mPromptMode == PromptMode.DIALOG) {
                            new MaterialDialog.Builder(mContext)
                                    .title(R.string.error_check_for_update)
                                    .content(e.toString())
                                    .positiveText(R.string.text_cancel)
                                    .canceledOnTouchOutside(false)
                                    .build()
                                    .show();
                        }
                    }
                });
    }

    private void showDialog(@NonNull VersionInfo versionInfo) {
        showDialog(versionInfo, null);
    }

    private void showDialog(@NonNull VersionInfo versionInfo, Context context) {
        // TODO by SuperMonster003 on May 30, 2022.
        //  ! A. Updates ignorance (and settings).
        //  ! B. VersionInfo: *.properties / *.json.

        Context ctx = context != null ? context : mContext;

        String propVersion = versionInfo.getVersionName();
        if (propVersion == null) {
            new MaterialDialog.Builder(ctx)
                    .title(R.string.error_check_for_update)
                    .content(R.string.error_parse_version_info)
                    .positiveText(R.string.dialog_button_back)
                    .build()
                    .show();
            return;
        }

        mUpdateDialog = new MaterialDialog.Builder(ctx)
                .title(propVersion)
                .content(R.string.text_getting_release_notes)
                .neutralText(R.string.dialog_button_ignore_current_update)
                .neutralColor(ctx.getColor(R.color.dialog_button_warn))
                .negativeText(R.string.dialog_button_back)
                .negativeColor(ctx.getColor(R.color.dialog_button_default))
                .positiveText(R.string.dialog_button_update_now)
                .positiveColor(ctx.getColor(R.color.dialog_button_unavailable))
                .autoDismiss(false)
                .cancelable(false)
                .build();

        mPendingDialog = getPendingDialog(ctx, R.string.text_preparing);

        MDButton neutralButton = mUpdateDialog.getActionButton(DialogAction.NEUTRAL);
        neutralButton.setOnClickListener(v -> {
            // TODO by SuperMonster003 on May 30, 2022.
            //  ! Updates ignorance.

            new MaterialDialog.Builder(ctx)
                    .title(R.string.text_prompt)
                    .content(R.string.text_under_development_content)
                    .positiveText(R.string.dialog_button_back)
                    .build()
                    .show();
        });

        MDButton negativeButton = mUpdateDialog.getActionButton(DialogAction.NEGATIVE);
        negativeButton.setOnClickListener(v -> mUpdateDialog.dismiss());

        MDButton positiveButton = mUpdateDialog.getActionButton(DialogAction.POSITIVE);
        positiveButton.setOnClickListener(null);

        mUpdateDialog.show();

        // TODO by SuperMonster003 on May 31, 2022.
        //  ! Android 7.x (N and N_MR1) compatibility.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            mUpdateDialog.dismiss();
            new MaterialDialog.Builder(ctx)
                    .title(R.string.error_check_for_update)
                    .content(R.string.error_sdk_lower_than_o_not_supported_yet)
                    .positiveText(R.string.text_cancel)
                    .cancelable(false)
                    .build()
                    .show();
            return;
        }

        mGitHubExecutor.execute(() -> {
            GitHub github = connectToGitHubIfNeeded();
            if (github == null) {
                setDialogContent(mUpdateDialog, R.string.error_cannot_connect_to_github);
                return;
            }

            String userName = ctx.getString(R.string.developer_full_name);
            String repoName = ctx.getString(R.string.app_name);
            GHRepository repo = getGitHubRepo(github, userName, repoName);
            if (repo == null) {
                setDialogContent(mUpdateDialog, ctx.getString(R.string.error_invalid_github_repo, repoName));
                return;
            }

            GHRelease release = getGitHubRelease(repo);
            if (release == null) {
                setDialogContent(mUpdateDialog, R.string.error_get_github_latest_release);
                return;
            }

            String releaseTag = release.getTagName();
            if (!checkReleaseTagMatchesProp(releaseTag, propVersion)) {
                setDialogContent(mUpdateDialog, R.string.error_corresponding_github_release_may_not_published);
                return;
            }

            String rawHtmlContent = getRawHtmlFromRelease(repo, release);
            Spanned htmlContent = Html.fromHtml(rawHtmlContent, Html.FROM_HTML_MODE_COMPACT);
            if (htmlContent.toString().isEmpty()) {
                setDialogContent(mUpdateDialog, R.string.text_empty_release_note);
            } else {
                setDialogContent(mUpdateDialog, htmlContent);
            }

            PagedIterable<GHAsset> assets = getGitHubAssets(release);
            if (assets == null) {
                setDialogContent(mUpdateDialog, R.string.error_empty_github_release_assets);
                return;
            }

            setDialogUpdateButton(ctx, assets, versionInfo);
        });
    }

    @Nullable
    private PagedIterable<GHAsset> getGitHubAssets(GHRelease release) {
        try {
            PagedIterable<GHAsset> assets = release.listAssets();
            if (assets.toArray().length > 0) {
                return assets;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    private void download(Context ctx, String downloadUrl, String fileName, VersionInfo versionInfo) {
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        final String path = new File(downloadDir, fileName).getPath();

        Consumer<File> onNext = file -> {
            mUpdateDialog = null;
            IntentUtil.installApkOrToast(ctx, file.getPath(), AppFileProvider.AUTHORITY);
        };

        Consumer<Throwable> onError = e -> {
            Log.d(TAG, "onError: printing stack trace");
            e.printStackTrace();

            String msg = e.getMessage();

            MaterialDialog d = new MaterialDialog.Builder(ctx)
                    .title(R.string.text_download_failed)
                    .content(msg == null ? ctx.getString(R.string.error_unknown) : msg)
                    .negativeText(R.string.text_cancel)
                    .negativeColor(ctx.getColor(R.color.dialog_button_default))
                    .onNegative((dialog, which) -> {
                        dialog.dismiss();
                        mUpdateDialog = null;
                    })
                    .positiveText("")
                    .positiveColor(ctx.getColor(R.color.dialog_button_failure))
                    .autoDismiss(false)
                    .cancelable(false)
                    .build();

            if (mPendingDialog != null) {
                mPendingDialog.dismiss();
            }

            if (mUpdateDialog != null) {
                d.getActionButton(DialogAction.NEGATIVE).setText(R.string.dialog_button_quit);
                d.getActionButton(DialogAction.NEGATIVE).setTextColor(ctx.getColor(R.color.dialog_button_caution));
                d.getActionButton(DialogAction.POSITIVE).setText(R.string.dialog_button_retry);
                d.getActionButton(DialogAction.POSITIVE).setOnClickListener(v -> {
                    d.dismiss();
                    mUpdateDialog.show();
                });
            }

            d.show();
        };

        DownloadManager.getInstance().downloadWithProgress(ctx, downloadUrl, path, versionInfo)
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(onNext, onError);
    }

    private void setDialogUpdateButton(@NonNull Context ctx, PagedIterable<GHAsset> ghAssets, VersionInfo versionInfo) {
        MDButton positiveButton = mUpdateDialog.getActionButton(DialogAction.POSITIVE);
        positiveButton.setTextColor(ctx.getColor(R.color.dialog_button_attraction));
        positiveButton.setOnClickListener(v -> {
            if (!mUpdateDialog.isShowing()) {
                return;
            }
            mUpdateDialog.dismiss();
            mPendingDialog.show();

            mGitHubExecutor.execute(() -> {
                String[] abiList = Build.SUPPORTED_ABIS;
                String abiBackup = "universal";

                String url = null;
                String urlBackup = null;
                String fileName = null;
                String fileNameBackup = null;

                try {
                    assets:
                    for (GHAsset ghAsset : ghAssets) {
                        String assetName = ghAsset.getName();
                        String assetUrl = ghAsset.getBrowserDownloadUrl();
                        for (String abi : abiList) {
                            String regex = ".*\\b" + abi + "\\b.*";
                            if (assetName.matches(regex)) {
                                url = assetUrl;
                                fileName = assetName;
                                break assets;
                            }
                        }
                        if (assetName.contains(abiBackup)) {
                            urlBackup = assetUrl;
                            fileNameBackup = assetName;
                        }
                    }
                } catch (Exception ignore) {
                    // Ignored.
                }

                mPendingDialog.dismiss();

                if (url == null && urlBackup == null) {
                    mHandler.post(() -> new MaterialDialog.Builder(ctx)
                            .content(R.string.error_cannot_get_download_url)
                            .positiveText(R.string.text_cancel)
                            .positiveColor(ctx.getColor(R.color.dialog_button_default))
                            .canceledOnTouchOutside(false)
                            .build()
                            .show());
                    return;
                }

                if (url != null) {
                    downloadWithHandler(ctx, url, fileName, versionInfo);
                } else {
                    GlobalAppContext.toast(R.string.text_github_backup_url_used, Toast.LENGTH_LONG);
                    downloadWithHandler(ctx, urlBackup, fileNameBackup, versionInfo);
                }
            });
        });
    }

    private void downloadWithHandler(Context ctx, String url, String fileName, VersionInfo versionInfo) {
        mHandler.post(() -> download(ctx, url, fileName, versionInfo));
    }

    private String getRawHtmlFromRelease(GHRepository repo, GHRelease release) {
        Reader reader;
        try {
            reader = repo.renderMarkdown(release.getBody(), MarkdownMode.MARKDOWN);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        StringBuilder textBuilder = new StringBuilder();
        try (Reader r = new BufferedReader(reader)) {
            int c;
            while ((c = r.read()) != -1) {
                textBuilder.append((char) c);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return textBuilder.toString();
    }

    private GHRelease getGitHubRelease(GHRepository repo) {
        try {
            return repo.getLatestRelease();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private GHRepository getGitHubRepo(GitHub github, String userName, String repoName) {
        try {
            GHUser user = github.getUser(userName);
            return user.getRepository(repoName);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean checkReleaseTagMatchesProp(String releaseTag, String propVersion) {
        return releaseTag.endsWith(propVersion);
    }

    private void setDialogContent(MaterialDialog dialog, String content) {
        mHandler.post(() -> dialog.setContent(content));
    }

    private void setDialogContent(MaterialDialog dialog, Spanned content) {
        mHandler.post(() -> dialog.setContent(content));
    }

    private void setDialogContent(MaterialDialog dialog, int resId) {
        mHandler.post(() -> dialog.setContent(resId));
    }

    private GitHub connectToGitHubIfNeeded() {
        try {
            if (mGitHubConnection == null) {
                // FIXME by SuperMonster003 on May 31, 2022.
                //  ! Given that java.time package was added only in Android O (API 26),
                //  ! API 24 and 25 will throw an java.lang.NoClassDefFoundError here.
                mGitHubConnection = GitHub.connectAnonymously();
            }
            return mGitHubConnection;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void showSnackBar(@NonNull VersionInfo versionInfo) {
        String content = mContext.getString(R.string.text_new_version_found) + ": " + versionInfo.getVersionName();

        CustomSnackbar snackbar = CustomSnackbar.make(mView, content, BaseTransientBottomBar.LENGTH_INDEFINITE);
        snackbar.setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_SLIDE)
                .setActionOne(R.string.text_updates_snack_bar_act_view, v -> showDialog(versionInfo, mView.getContext()))
                .setActionTwo(R.string.text_updates_snack_bar_act_later, null)
                .show();
    }

    private MaterialDialog getPendingDialog(Context context, int content) {
        // TODO by SuperMonster003 on May 29, 2022.
        //  ! Add a "CANCEL" button for interruption.
        //  ! Concurrent programming may be needed.

        return new MaterialDialog.Builder(context)
                .content(content)
                .autoDismiss(false)
                .cancelable(false)
                .build();
    }

    public static class Builder {
        private final Context mContext;

        private View mView;
        private String mBaseUrl;
        private String mUrl;
        private PromptMode mPromptMode = PromptMode.NONE;
        private SimpleObserver<ResponseBody> mCallback;

        public Builder() {
            this(GlobalAppContext.get());
        }

        public Builder(Context context) {
            mContext = context;
        }

        public Builder(View view) {
            this();
            mView = view;
        }

        public Builder setBaseUrl(String baseUrl) {
            this.mBaseUrl = baseUrl;
            return this;
        }

        public Builder setUrl(String url) {
            this.mUrl = url;
            return this;
        }

        public Builder setPromptMode(PromptMode promptMode) {
            this.mPromptMode = promptMode;
            return this;
        }

        public Builder setCallback(SimpleObserver<ResponseBody> callback) {
            this.mCallback = callback;
            return this;
        }

        public UpdateChecker build() {
            ensureUrl();
            return new UpdateChecker(mContext, mView, mBaseUrl, mUrl, mPromptMode, mCallback);
        }

        private void ensureUrl() {
            String regexUrl = "^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
            if (mBaseUrl != null) {
                if (!mBaseUrl.matches(regexUrl)) {
                    System.out.println("Base URL: " + mBaseUrl);
                    throw new IllegalArgumentException(mContext.getString(R.string.error_illegal_url_argument));
                }
            } else {
                if (!mUrl.matches(regexUrl)) {
                    System.out.println("URL: " + mUrl);
                    throw new IllegalArgumentException(mContext.getString(R.string.error_illegal_relative_url_argument_without_base));
                }
            }
        }
    }

    @NonNull
    private UpdateCheckerApi getCheckerApi() {
        if (checkerApi == null) {
            checkerApi = new Retrofit.Builder()
                    .baseUrl(mBaseUrl)
                    .addConverterFactory(GsonConverterFactory.create(new GsonBuilder().setLenient().create()))
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                    .build()
                    .create(UpdateCheckerApi.class);
        }
        return checkerApi;
    }

}
