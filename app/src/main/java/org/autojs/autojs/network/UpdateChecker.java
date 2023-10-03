package org.autojs.autojs.network;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.internal.MDButton;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.gson.GsonBuilder;

import org.autojs.autojs.annotation.Lazy;
import org.autojs.autojs.core.ui.widget.CustomSnackbar;
import org.autojs.autojs.external.fileprovider.AppFileProvider;
import org.autojs.autojs.network.api.UpdateCheckerApi;
import org.autojs.autojs.network.download.DownloadManager;
import org.autojs.autojs.network.entity.ExtendedVersionInfo;
import org.autojs.autojs.network.entity.VersionInfo;
import org.autojs.autojs.pref.Pref;
import org.autojs.autojs.tool.SimpleObserver;
import org.autojs.autojs.util.AndroidUtils;
import org.autojs.autojs.util.IntentUtils;
import org.autojs.autojs.util.UpdateUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;
import org.jetbrains.annotations.Contract;
import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.MarkdownMode;
import org.kohsuke.github.PagedIterable;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
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

    private static final String TAG = UpdateChecker.class.getSimpleName();
    private MaterialDialog mUpdateDialog;
    private MaterialDialog mPendingDialog;

    public enum PromptMode {DIALOG, SNACKBAR}

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Context mContext;
    private final View mView;
    private final String mBaseUrl;
    private final String mUrl;
    private final PromptMode mPromptMode;
    private final SimpleObserver<ResponseBody> mCallback;
    private final Executor mGitHubExecutor = Executors.newSingleThreadExecutor();

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
        mPendingDialog = new Dialog.Builder.Pending(mContext, R.string.text_checking_update).build();

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
                        Pref.refreshLastUpdatesCheckedTimestamp();

                        if (mPendingDialog.isShowing()) {
                            mPendingDialog.dismiss();
                        }

                        if (mCallback != null) {
                            mCallback.onNext(responseBody);
                            return;
                        }

                        VersionInfo versionInfo = getVersionInfo(responseBody);

                        switch (mPromptMode) {
                            case DIALOG -> {
                                if (versionInfo == null) {
                                    ViewUtils.showToast(mContext, R.string.error_parse_version_info);
                                    return;
                                }
                                if (versionInfo.isNewer() && versionInfo.isNotIgnored()) {
                                    showDialog(versionInfo);
                                } else {
                                    ViewUtils.showToast(mContext, R.string.text_is_latest_version);
                                    Pref.refreshLastNoNewerUpdatesTimestamp();
                                }
                            }
                            case SNACKBAR -> {
                                if (versionInfo != null && versionInfo.isNewer() && versionInfo.isNotIgnored()) {
                                    showSnackBar(versionInfo);
                                }
                            }
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
                            new Dialog.Builder.Prompt(mContext, R.string.error_check_for_update, e.getMessage())
                                    .positiveText(R.string.text_cancel)
                                    .build().show();
                        }
                    }
                });
    }

    @Nullable
    private VersionInfo getVersionInfo(ResponseBody responseBody) {
        VersionInfo versionInfoByBlob = getVersionInfoByBlob(responseBody);
        if (versionInfoByBlob != null) {
            Log.d(TAG, "VersionInfo parsed via blob");
            return versionInfoByBlob;
        }
        // VersionInfo versionInfoByProperties = getVersionInfoByProperties(responseBody);
        // if (versionInfoByProperties != null) {
        //     Log.d(TAG, "VersionInfo parsed via properties");
        //     return versionInfoByProperties;
        // }
        return null;
    }

    @Nullable
    private VersionInfo getVersionInfoByProperties(ResponseBody responseBody) {
        try {
            Properties prop = new Properties();
            prop.load(responseBody.byteStream());

            String versionNameKey = mContext.getString(R.string.property_key_app_version_name);
            String versionName = prop.getProperty(versionNameKey);

            String versionCodeKey = mContext.getString(R.string.property_key_app_version_code);
            int versionCode = Integer.parseInt(prop.getProperty(versionCodeKey));

            return new VersionInfo(versionName, versionCode);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    private static VersionInfo getVersionInfoByBlob(ResponseBody responseBody) {
        StringBuilder textBuilder = new StringBuilder();
        try (Reader r = new BufferedReader(new InputStreamReader(responseBody.byteStream()), 1024)) {
            int c;
            while ((c = r.read()) != -1) {
                textBuilder.append((char) c);
            }
            return new VersionInfo(textBuilder.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    private void download(Context ctx, VersionInfo versionInfo) {
        Consumer<File> onNext = file -> {
            mUpdateDialog = null;
            IntentUtils.installApkOrToast(ctx, file.getPath(), AppFileProvider.AUTHORITY);
        };

        Consumer<Throwable> onError = e -> {
            e.printStackTrace();

            String msg = e.getMessage();
            msg = msg == null ? ctx.getString(R.string.error_unknown) : msg;

            MaterialDialog d = new Dialog.Builder.Prompt(ctx, R.string.text_failed_to_download, msg)
                    .neutralText("")
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

            if (versionInfo.getDownloadUrl() != null) {
                d.getActionButton(DialogAction.NEUTRAL).setText(R.string.dialog_button_download_with_browser);
                d.getActionButton(DialogAction.NEUTRAL).setTextColor(ctx.getColor(R.color.dialog_button_hint));
                d.getActionButton(DialogAction.NEUTRAL).setOnClickListener(v -> {
                    d.dismiss();
                    UpdateUtils.openUrl(ctx, versionInfo.getDownloadUrl());
                });
            }

            d.show();
        };

        DownloadManager.getInstance().downloadWithProgress(ctx, versionInfo)
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(onNext, onError);
    }

    @NonNull
    @Lazy
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

    private void showSnackBar(@NonNull VersionInfo versionInfo) {
        String ver = versionInfo.getVersionName();
        String content = mContext.getString(R.string.text_new_version_found) + ": " + ver;

        CustomSnackbar.make(mView, content, BaseTransientBottomBar.LENGTH_INDEFINITE)
                .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_SLIDE)
                .setActionOne(R.string.text_updates_snack_bar_act_view, v -> showDialog(versionInfo, mView.getContext()))
                .setActionTwo(R.string.text_updates_snack_bar_act_later, v -> Pref.refreshLastUpdatesPostponedTimestamp())
                .show();
    }

    private void showDialog(@NonNull VersionInfo versionInfo) {
        showDialog(versionInfo, mContext);
    }

    private void showDialog(@NonNull VersionInfo versionInfo, Context context) {
        // TODO by SuperMonster003 on Jun 4, 2022.
        //  ! VersionInfo: *.properties / *.json.

        String propVersion = versionInfo.getVersionName();
        if (propVersion.isEmpty()) {
            new Dialog.Builder
                    .Prompt(context, R.string.error_check_for_update, R.string.error_parse_version_info)
                    .build().show();
            return;
        }

        mUpdateDialog = new Dialog.Builder.Update(context, propVersion).build();
        mPendingDialog = new Dialog.Builder.Pending(context, R.string.text_preparing).build();

        MDButton neutralButton = mUpdateDialog.getActionButton(DialogAction.NEUTRAL);
        neutralButton.setOnClickListener(v -> new MaterialDialog.Builder(context)
                .title(R.string.text_prompt)
                .content(R.string.prompt_add_ignored_version)
                .negativeText(R.string.dialog_button_cancel)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_warn)
                .onPositive((dPrompt, which) -> {
                    UpdateUtils.addIgnoredVersion(versionInfo);
                    ViewUtils.showToast(context, R.string.text_done);
                    mUpdateDialog.dismiss();
                })
                .show());

        MDButton negativeButton = mUpdateDialog.getActionButton(DialogAction.NEGATIVE);
        negativeButton.setOnClickListener(v -> mUpdateDialog.dismiss());

        MDButton positiveButton = mUpdateDialog.getActionButton(DialogAction.POSITIVE);
        positiveButton.setOnClickListener(null);

        mUpdateDialog.show();

        mGitHubExecutor.execute(() -> {
            GitHub github = GHub.getConnection();
            if (github == null) {
                Dialog.setDialogContent(mUpdateDialog, R.string.error_cannot_connect_to_github);
                return;
            }

            String userName = context.getString(R.string.developer_full_name);
            String repoName = context.getString(R.string.app_name);
            GHRepository repo = GHub.getRepo(github, userName, repoName);
            if (repo == null) {
                Dialog.setDialogContent(mUpdateDialog, mUpdateDialog.getContext().getString(R.string.error_invalid_github_repo, repoName));
                return;
            }

            GHRelease release = GHub.getRelease(repo);
            if (release == null) {
                Dialog.setDialogContent(mUpdateDialog, R.string.error_get_github_latest_release);
                return;
            }

            String releaseTag = release.getTagName();
            if (!GHub.isTagMatches(releaseTag, propVersion)) {
                Dialog.setDialogContent(mUpdateDialog, R.string.error_corresponding_github_release_may_not_published);
                return;
            }

            String rawHtmlContent = GHub.getReleaseHtml(repo, release);
            Spanned htmlContent = Html.fromHtml(rawHtmlContent, Html.FROM_HTML_MODE_COMPACT);
            if (htmlContent.toString().isEmpty()) {
                Dialog.setDialogContent(mUpdateDialog, R.string.text_empty_release_note);
            } else {
                Dialog.setDialogContent(mUpdateDialog, htmlContent);
            }

            PagedIterable<GHAsset> assets = GHub.getAssets(release);
            if (assets == null) {
                mHandler.post(() -> new Dialog.Builder
                        .Prompt(context, R.string.error_empty_github_release_assets)
                        .build().show());
                return;
            }

            mHandler.post(() -> setDialogUpdateButton(context, assets, versionInfo));
        });
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
                List<String> abiList = AndroidUtils.getDeviceFilteredAbiList();
                abiList.add("universal");
                GHub.Asset targetAsset = GHub.pickAssetIntelligently(ghAssets, abiList);

                mPendingDialog.dismiss();

                if (targetAsset == null) {
                    mHandler.post(() -> new Dialog.Builder
                            .Prompt(ctx, R.string.error_parse_github_release_assets)
                            .positiveText(R.string.text_cancel)
                            .build().show());
                    return;
                }

                if (targetAsset.getAbi() != null) {
                    versionInfo.setAbi(targetAsset.getAbi());
                }
                versionInfo.setFileName(targetAsset.getFileName());
                versionInfo.setSize(targetAsset.getSize());
                versionInfo.setDownloadUrl(targetAsset.getDownloadUrl());

                mHandler.post(() -> download(ctx, versionInfo));
            });
        });
    }

    public static class Builder {
        private final Context mContext;

        private View mView;
        private String mBaseUrl;
        private String mUrl;
        private PromptMode mPromptMode;
        private SimpleObserver<ResponseBody> mCallback;

        public Builder(Context context) {
            mContext = context;
        }

        public Builder(Context context, View view) {
            this(context);
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
            String regexUrl = "^(https?|ftp|file)://[-a-zA-Z\\d+&@#/%?=~_|!:,.;]*[-a-zA-Z\\d+&@#/%=~_|]";
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

    private static class Dialog {

        private static final Handler mHandler = new Handler(Looper.getMainLooper());

        private static void setDialogContent(MaterialDialog dialog, String content) {
            mHandler.post(() -> dialog.setContent(content));
        }

        private static void setDialogContent(MaterialDialog dialog, Spanned content) {
            mHandler.post(() -> dialog.setContent(content));
        }

        private static void setDialogContent(MaterialDialog dialog, int resId) {
            mHandler.post(() -> dialog.setContent(resId));
        }

        private static class Builder {

            public static class Pending extends MaterialDialog.Builder {

                public Pending(Context context, int content) {
                    super(context);

                    // TODO by SuperMonster003 on May 29, 2022.
                    //  ! Add a "CANCEL" button for interruption.
                    //  ! Concurrent programming may be needed.
                    this
                            .content(content)
                            .cancelable(false);
                }

            }

            public static class Prompt extends MaterialDialog.Builder {

                public Prompt(Context context, int content) {
                    this(context, R.string.text_prompt, content);
                }

                public Prompt(Context context, int title, int content) {
                    this(context, title, context.getString(content));
                }

                public Prompt(Context context, int title, String content) {
                    super(context);
                    this
                            .title(title)
                            .content(content)
                            .positiveText(R.string.dialog_button_dismiss)
                            .cancelable(false);
                }

            }

            public static class Update extends MaterialDialog.Builder {

                public Update(Context context, String title) {
                    super(context);
                    this
                            .title(title)
                            .content(R.string.text_getting_release_notes)
                            .neutralText(R.string.dialog_button_ignore_current_update)
                            .neutralColor(context.getColor(R.color.dialog_button_warn))
                            .negativeText(R.string.dialog_button_cancel)
                            .negativeColor(context.getColor(R.color.dialog_button_default))
                            .positiveText(R.string.dialog_button_update_now)
                            .positiveColor(context.getColor(R.color.dialog_button_unavailable))
                            .autoDismiss(false)
                            .cancelable(false);
                }

            }

        }

    }

    private static class GHub {

        private static GitHub mGitHubConnection;

        @Nullable
        @Lazy
        private static GitHub getConnection() {
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

        @Nullable
        private static PagedIterable<GHAsset> getAssets(GHRelease release) {
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

        private static Asset pickAssetIntelligently(PagedIterable<GHAsset> ghAssets, List<String> presetAbiList) {
            Asset targetAsset = null;
            try {
                int pointer = presetAbiList.size();
                for (GHAsset ghAsset : ghAssets) {
                    String assetName = ghAsset.getName();
                    for (int i = 0; i < presetAbiList.size(); i += 1) {
                        String abi = presetAbiList.get(i);
                        String regex = ".*\\b" + abi + "\\b.*";
                        if (assetName.matches(regex)) {
                            targetAsset = new Asset(ghAsset);
                            pointer = Math.min(pointer, i);
                            if (pointer == 0) {
                                targetAsset.setAbi(abi);
                                return targetAsset;
                            }
                        }
                    }
                }
            } catch (Exception ignore) {
                // Ignored.
            }
            return targetAsset;
        }

        @Nullable
        private static String getReleaseHtml(GHRepository repo, GHRelease release) {
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

        @Nullable
        private static GHRelease getRelease(GHRepository repo) {
            try {
                return repo.getLatestRelease();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Nullable
        private static GHRepository getRepo(GitHub github, String userName, String repoName) {
            try {
                GHUser user = github.getUser(userName);
                return user.getRepository(repoName);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Contract(pure = true)
        private static boolean isTagMatches(String releaseTag, String propVersion) {
            return releaseTag.endsWith(propVersion);
        }

        private static class Asset implements ExtendedVersionInfo {

            private final GHAsset mGitHubAsset;

            private String mAbi;

            public Asset(GHAsset gitHubAsset) {
                mGitHubAsset = gitHubAsset;
            }

            public void setAbi(String abi) {
                mAbi = abi;
            }

            @Override
            public String getAbi() {
                return mAbi;
            }

            @Override
            public String getFileName() {
                return mGitHubAsset.getName();
            }

            @Override
            public long getSize() {
                return mGitHubAsset.getSize();
            }

            @Override
            public String getDownloadUrl() {
                return mGitHubAsset.getBrowserDownloadUrl();
            }

        }

    }

}
