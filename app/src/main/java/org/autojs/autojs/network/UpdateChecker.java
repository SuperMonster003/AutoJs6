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
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import okhttp3.ResponseBody;
import org.autojs.autojs.annotation.Lazy;
import org.autojs.autojs.core.pref.Language;
import org.autojs.autojs.core.pref.Pref;
import org.autojs.autojs.core.ui.widget.CustomSnackbar;
import org.autojs.autojs.network.api.StreamingUrlApi;
import org.autojs.autojs.network.download.DownloadManager;
import org.autojs.autojs.network.entity.ExtendedVersionInfo;
import org.autojs.autojs.network.entity.VersionInfo;
import org.autojs.autojs.tool.SimpleObserver;
import org.autojs.autojs.ui.settings.DisplayVersionHistoriesActivity;
import org.autojs.autojs.util.AndroidUtils;
import org.autojs.autojs.util.IntentUtils;
import org.autojs.autojs.util.TextUtils;
import org.autojs.autojs.util.UpdateUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.MarkdownMode;
import org.kohsuke.github.PagedIterable;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by Stardust on Sep 20, 2017.
 * Modified by SuperMonster003 as of Feb 26, 2022.
 *
 * @noinspection CallToPrintStackTrace, unused, ResultOfMethodCallIgnored
 */
@SuppressLint("CheckResult")
public class UpdateChecker {

    private static final String TAG = UpdateChecker.class.getSimpleName();

    private MaterialDialog mUpdateDialog;
    private MaterialDialog mPendingDialog;

    public enum PromptMode {DIALOG, SNACKBAR}

    public static final String URL_BASE_GITHUB_RAW = "https://raw.githubusercontent.com/";
    public static final String URL_BASE_GITHUB_HOME = "https://github.com/";

    public static final String URL_VERSION_PROPS_RAW = URL_BASE_GITHUB_RAW + "SuperMonster003/AutoJs6/master/version.properties";
    public static final String URL_VERSION_PROPS_BLOB = URL_BASE_GITHUB_HOME + "SuperMonster003/AutoJs6/blob/master/version.properties";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Context mContext;
    private final View mView;
    private final PromptMode mPromptMode;
    private final SimpleObserver<ResponseBody> mCallback;
    private final Executor mGitHubExecutor = Executors.newSingleThreadExecutor();

    private UpdateChecker(Context context, View view, PromptMode promptMode, SimpleObserver<ResponseBody> callback) {
        mContext = context;
        mView = view;
        mPromptMode = promptMode;
        mCallback = callback;
    }

    public void checkNow() {
        checkNow(false);
    }

    public void checkNow(boolean isIgnoreLocalVersions) {
        mPendingDialog = new Dialog.Builder.Pending(mContext, R.string.text_checking_update).build();

        if (mPromptMode == PromptMode.DIALOG) {
            mPendingDialog.show();
        }

        AtomicReference<String> errorBlob = new AtomicReference<>();
        AtomicReference<String> errorRaw = new AtomicReference<>();

        Observable<ResponseBody> obsBlobSafe = getStreamingApi()
                .streamingUrl(URL_VERSION_PROPS_BLOB)
                .subscribeOn(Schedulers.io())
                .onErrorResumeNext(e -> {
                    Log.d(TAG, "Error from obsBlobSafe while parsing version.properties");
                    errorBlob.set(e.getMessage());
                    e.printStackTrace();
                    return Observable.empty();
                });

        Observable<ResponseBody> obsRawSafe = getStreamingApi()
                .streamingUrl(URL_VERSION_PROPS_RAW)
                .subscribeOn(Schedulers.io())
                .onErrorResumeNext(e -> {
                    Log.d(TAG, "Error from obsRawSafe while parsing version.properties");
                    errorRaw.set(e.getMessage());
                    e.printStackTrace();
                    return Observable.empty();
                });

        Observable.amb(Arrays.asList(obsBlobSafe, obsRawSafe))
                .observeOn(AndroidSchedulers.mainThread())
                .switchIfEmpty(Observable.error(new ObservableEmptyException()))
                .subscribe(new SimpleObserver<>() {
                    @Override
                    public void onNext(@NotNull ResponseBody responseBody) {
                        Pref.refreshLastUpdatesCheckedTimestamp();

                        if (mPendingDialog.isShowing()) {
                            mPendingDialog.dismiss();
                        }

                        if (mCallback != null) {
                            mCallback.onNext(responseBody);
                            return;
                        }

                        mGitHubExecutor.execute(() -> {
                            VersionInfo versionInfo = getVersionInfo(responseBody);

                            switch (mPromptMode) {
                                case DIALOG -> {
                                    if (versionInfo == null) {
                                        ViewUtils.showToast(mContext, R.string.error_parse_version_info, true);
                                        return;
                                    }
                                    if (!isIgnoreLocalVersions && !(versionInfo.isNewer() && versionInfo.isNotIgnored())) {
                                        ViewUtils.showToast(mContext, R.string.text_is_latest_version, true);
                                        Pref.refreshLastNoNewerUpdatesTimestamp();
                                        return;
                                    }
                                    mHandler.post(() -> showUpdateInfoDialog(versionInfo));
                                }
                                case SNACKBAR -> {
                                    if (versionInfo != null && versionInfo.isNewer() && versionInfo.isNotIgnored()) {
                                        mHandler.post(() -> showSnackBar(versionInfo));
                                    }
                                }
                            }
                        });
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
                            if (!(e instanceof ObservableEmptyException)) {
                                new Dialog.Builder.Prompt(mContext, R.string.error_check_for_update, e.getMessage())
                                        .positiveText(R.string.dialog_button_dismiss)
                                        .positiveColorRes(R.color.dialog_button_default)
                                        .build().show();
                                return;
                            }
                            if (errorBlob.get() != null || errorRaw.get() != null) {
                                StringBuilder message = new StringBuilder();
                                if (errorBlob.get() != null) {
                                    message.append("Error message of blob:\n\n").append(errorBlob.get());
                                }
                                if (errorRaw.get() != null) {
                                    if (message.length() > 0) {
                                        message.append("\n\n");
                                    }
                                    message.append("Error message of raw:\n\n").append(errorRaw.get());
                                }
                                new Dialog.Builder.Prompt(mContext, R.string.error_check_for_update, message.toString())
                                        .positiveText(R.string.dialog_button_dismiss)
                                        .positiveColorRes(R.color.dialog_button_default)
                                        .build().show();
                            } else {
                                new Dialog.Builder.Prompt(mContext, R.string.error_check_for_update,
                                        R.string.error_failed_to_retrieve_version_properties_file_to_parse_version_information)
                                        .positiveText(R.string.dialog_button_dismiss)
                                        .positiveColorRes(R.color.dialog_button_default)
                                        .build().show();
                            }
                        }
                    }
                });
    }

    @Nullable
    private VersionInfo getVersionInfo(ResponseBody responseBody) {
        try {
            return new VersionInfo(responseBody.string());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    private void download(Context context, VersionInfo versionInfo) {
        Consumer<File> onNext = file -> {
            mUpdateDialog = null;
            IntentUtils.installApk(context, file.getPath());
        };

        Consumer<Throwable> onError = e -> {
            e.printStackTrace();

            String msg = e.getMessage();
            msg = msg == null ? context.getString(R.string.error_unknown) : msg;

            MaterialDialog d = new Dialog.Builder.Prompt(context, R.string.text_failed_to_download, msg)
                    .neutralText("")
                    .negativeText(R.string.text_cancel)
                    .negativeColor(context.getColor(R.color.dialog_button_default))
                    .onNegative((dialog, which) -> {
                        dialog.dismiss();
                        mUpdateDialog = null;
                    })
                    .positiveText("")
                    .positiveColor(context.getColor(R.color.dialog_button_failure))
                    .autoDismiss(false)
                    .cancelable(false)
                    .build();

            if (mPendingDialog != null) {
                mPendingDialog.dismiss();
            }

            if (mUpdateDialog != null) {
                d.getActionButton(DialogAction.NEGATIVE).setText(R.string.dialog_button_quit);
                d.getActionButton(DialogAction.NEGATIVE).setTextColor(context.getColor(R.color.dialog_button_caution));
                d.getActionButton(DialogAction.POSITIVE).setText(R.string.dialog_button_retry);
                d.getActionButton(DialogAction.POSITIVE).setOnClickListener(v -> {
                    d.dismiss();
                    mUpdateDialog.show();
                });
            }

            if (versionInfo.getDownloadUrl() != null) {
                d.getActionButton(DialogAction.NEUTRAL).setText(R.string.dialog_button_download_with_browser);
                d.getActionButton(DialogAction.NEUTRAL).setTextColor(context.getColor(R.color.dialog_button_hint));
                d.getActionButton(DialogAction.NEUTRAL).setOnClickListener(v -> {
                    d.dismiss();
                    UpdateUtils.openUrl(context, versionInfo.getDownloadUrl());
                });
            }

            d.show();
        };

        DownloadManager.getInstance().downloadWithProgress(context, versionInfo)
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(onNext, onError);
    }

    @NonNull
    private StreamingUrlApi getStreamingApi() {
        return getStreamingApi(UpdateChecker.URL_BASE_GITHUB_HOME);
    }

    @NonNull
    private StreamingUrlApi getStreamingApi(String baseUrl) {
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build()
                .create(StreamingUrlApi.class);
    }

    private void showSnackBar(@NonNull VersionInfo versionInfo) {
        String ver = versionInfo.getVersionName();
        String content = mContext.getString(R.string.text_new_version_found) + ": " + ver;

        CustomSnackbar.make(mView, content, BaseTransientBottomBar.LENGTH_INDEFINITE)
                .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_SLIDE)
                .setActionOne(R.string.text_updates_snack_bar_act_view, v -> showUpdateInfoDialog(versionInfo, mView.getContext()))
                .setActionTwo(R.string.text_updates_snack_bar_act_later, v -> Pref.refreshLastUpdatesPostponedTimestamp())
                .show();
    }

    private void showUpdateInfoDialog(@NonNull VersionInfo versionInfo) {
        showUpdateInfoDialog(versionInfo, mContext);
    }

    private void showUpdateInfoDialog(@NonNull VersionInfo versionInfo, Context context) {
        String propVersion = versionInfo.getVersionName();
        if (propVersion.isEmpty()) {
            new Dialog.Builder.Prompt(context, R.string.error_check_for_update, R.string.error_parse_version_info)
                    .build().show();
            return;
        }

        mUpdateDialog = new Dialog.Builder.Update(context, versionInfo).build();
        mPendingDialog = new Dialog.Builder.Pending(context, R.string.text_preparing).build();

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
                Dialog.setDialogContent(mUpdateDialog, context.getString(R.string.error_invalid_github_repo, repoName));
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

            fetchLatestReleaseNotes(context, versionInfo, repo, release, releaseTag);
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

    private void fetchLatestReleaseNotes(Context context, VersionInfo versionInfo, GHRepository repo, GHRelease release, String releaseTag) {
        Language language = Objects.requireNonNullElse(Language.getPrefLanguageOrNull(), Language.EN);
        String languageTag = language.getLocalCompatibleLanguageTag();
        String urlSuffix = "app/src/main/assets-app/doc/CHANGELOG-" + languageTag + ".md";
        String urlBlob = "https://github.com/SuperMonster003/AutoJs6/blob/master/" + urlSuffix;
        String urlRaw = "https://raw.githubusercontent.com/SuperMonster003/AutoJs6/master/" + urlSuffix;

        Observable<Spanned> obsBlob = getStreamingApi()
                .streamingUrl(urlBlob)
                .subscribeOn(Schedulers.io())
                .onErrorResumeNext(e -> {
                    Log.d(TAG, "Error from obsBlob while parsing latest release notes from " + urlBlob);
                    e.printStackTrace();
                    return Observable.never();
                })
                .flatMap(responseBody -> {
                    try {
                        String content = responseBody.string().trim();
                        Log.d(TAG, "Respond body string (first 500) got from github: " + content.substring(0, Math.min(content.length(), 500)));
                        String html = parseLatestReleaseNotesFromHtml(content);
                        if (html != null && !html.isBlank()) {
                            String assembledHtml = assembleBlobDependenciesForSingleVersion(html, versionInfo.getVersionName(), urlBlob);
                            return Observable.just(Html.fromHtml(assembledHtml, Html.FROM_HTML_MODE_COMPACT));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return Observable.never();
                });

        Observable<Spanned> obsRaw = getStreamingApi()
                .streamingUrl(urlRaw)
                .subscribeOn(Schedulers.io())
                .onErrorResumeNext(e -> {
                    Log.d(TAG, "Error from obsRawSafe while parsing latest release notes from " + urlRaw);
                    e.printStackTrace();
                    return Observable.never();
                })
                .flatMap(responseBody -> {
                    try {
                        String content = responseBody.string().trim();
                        Log.d(TAG, "Respond body string got from github: " + content);
                        String markdown = parseLatestReleaseNotesFromMarkdown(content, releaseTag);
                        if (markdown != null && !markdown.isBlank()) {
                            String assembledMarkdown = assembleRawDependenciesForSingleVersion(markdown, versionInfo.getVersionName(), urlRaw);
                            String assembledHtml = TextUtils.markdownToHtml(assembledMarkdown);
                            return Observable.just(Html.fromHtml(assembledHtml, Html.FROM_HTML_MODE_COMPACT));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return Observable.never();
                });


        Observable.amb(Arrays.asList(obsBlob, obsRaw))
                .observeOn(AndroidSchedulers.mainThread())
                .timeout(23, TimeUnit.SECONDS)
                .subscribe(
                        releaseNotesSpannedForSingleVersion -> {
                            Dialog.setDialogContent(mUpdateDialog, releaseNotesSpannedForSingleVersion);
                        },
                        e -> {
                            e.printStackTrace();
                            Spanned fallbackLatestReleaseNotes = getFallbackLatestReleaseNotes(repo, release);
                            if (fallbackLatestReleaseNotes == null) {
                                Dialog.setDialogContent(mUpdateDialog, R.string.error_failed_to_retrieve_released_notes);
                                return;
                            }
                            Dialog.setDialogContent(mUpdateDialog, fallbackLatestReleaseNotes);
                            if (language != Language.ZH_HANS) {
                                mHandler.post(() -> new Dialog.Builder
                                        .Prompt(context, R.string.text_prompt, R.string.content_failed_to_retrieve_released_notes_of_current_language_with_zh_hans_fallback)
                                        .build().show());

                            }
                        }
                );
    }

    private @NotNull String assembleBlobDependenciesForSingleVersion(@NotNull String releaseNotes, String versionName, String markdownUrl) {
        String labelImprovement = mContext.getString(R.string.changelog_label_improvement);
        String labelDependency = mContext.getString(R.string.changelog_label_dependency);

        boolean isFiltered = false;

        List<String> filteredReleaseNotes = new ArrayList<>();
        String[] items = releaseNotes.split("\n");
        for (String item : items) {
            Log.d(TAG, "item: " + item);
            if (item.matches(".*\\b" + labelDependency + "\\b.*")) {
                isFiltered = true;
            } else {
                filteredReleaseNotes.add(item);
            }
        }

        if (isFiltered) {
            String anchor = "v" + String.join("", versionName.split("\\."));
            String dependenciesSummary = mContext.getString(R.string.text_changelog_item_dependency);

            for (int i = filteredReleaseNotes.size() - 1; i >= 0; i--) {
                String item = filteredReleaseNotes.get(i);
                if (!item.isBlank()) {
                    String assembledHtml = "<li><code>" +
                                           labelImprovement +
                                           "</code> " +
                                           dependenciesSummary +
                                           " <em><a href=\"" +
                                           markdownUrl + "#" + anchor +
                                           "\" rel=\"nofollow\"><code>" +
                                           "CHANGELOG.md" +
                                           "</code></a></em></li>";
                    filteredReleaseNotes.add(i + 1, assembledHtml);
                    break;
                }
            }
            return String.join("\n", filteredReleaseNotes);
        }
        return releaseNotes;
    }

    private @NotNull String assembleRawDependenciesForSingleVersion(@NotNull String releaseNotes, String versionName, String markdownUrl) {
        String labelImprovement = mContext.getString(R.string.changelog_label_improvement);
        String labelDependency = mContext.getString(R.string.changelog_label_dependency);

        boolean isFiltered = false;

        List<String> filteredReleaseNotes = new ArrayList<>();
        String[] items = releaseNotes.split("\n");
        for (String item : items) {
            if (item.contains("`" + labelDependency + "`")) {
                isFiltered = true;
            } else {
                filteredReleaseNotes.add(item);
            }
        }

        if (isFiltered) {
            String anchor = "v" + String.join("", versionName.split("\\."));
            String dependenciesSummary = mContext.getString(R.string.text_changelog_item_dependency);

            for (int i = filteredReleaseNotes.size() - 1; i >= 0; i--) {
                String item = filteredReleaseNotes.get(i);
                if (!item.isBlank()) {
                    String assembledMarkdown = "* `" + labelImprovement + "` " + dependenciesSummary + " _[`CHANGELOG.md`](" + markdownUrl + "#" + anchor + ")_";
                    filteredReleaseNotes.add(i + 1, assembledMarkdown);
                    break;
                }
            }
            return String.join("\n", filteredReleaseNotes);
        }
        return releaseNotes;
    }

    private String assembleDependenciesInReleaseNotesListMarkdown(String fullReleaseNotes) {
        // TODO by SuperMonster003 on Apr 24. 2025.
        //  ! Remove all dependency items and append a summary item as improvement.
        //  ! Reference to `generate_markdown.py`.
        return fullReleaseNotes;
    }

    @Nullable
    private String parseLatestReleaseNotesFromHtml(String htmlContent) {
        Document document = Jsoup.parse(htmlContent);
        Element releaseDateHeading = document.selectFirst("div.markdown-heading");
        if (releaseDateHeading != null) {
            Element logList = null;
            Element tmp = releaseDateHeading;
            while ((tmp = tmp.nextElementSibling()) != null) {
                if ("ul".equals(tmp.tagName())) {
                    logList = tmp;
                    break;
                }
            }
            if (logList != null) {
                return logList.html();
            }
        }
        return null;
    }

    @Nullable
    private String parseLatestReleaseNotesFromMarkdown(String markdown, String releaseTag) {
        Pattern pattern = Pattern.compile("#+\\s*" + releaseTag + "([\\s\\S]*?)(?=\\n#+\\s*v\\d+\\.\\d+|\\z)");
        Matcher matcher = pattern.matcher(markdown);
        if (matcher.find()) {
            String matchedContent = matcher.group(1);
            if (matchedContent != null) {
                return Pattern.compile("\n").splitAsStream(matchedContent.trim())
                        .filter(line -> line.matches("^[*-]\\s*`.*"))
                        .collect(Collectors.joining("\n"));
            }
        }
        return null;
    }

    @Nullable
    private Spanned getFallbackLatestReleaseNotes(GHRepository repo, GHRelease release) {
        Spanned htmlSpannedContent = getLatestReleaseFromGitHubRelease(repo, release);
        if (htmlSpannedContent == null || htmlSpannedContent.toString().isBlank()) {
            Log.d(TAG, "Release note got nothing from the latest release (fallback)");
            return null;
        }
        Log.d(TAG, "Release note got from latest release (fallback)");
        return htmlSpannedContent;
    }

    private static @Nullable Spanned getLatestReleaseFromGitHubRelease(GHRepository repo, GHRelease release) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            return GHub.getReleaseHtml(repo, release);
        });
        try {
            String rawHtmlContent = future.get(30, TimeUnit.SECONDS);
            return Html.fromHtml(rawHtmlContent, Html.FROM_HTML_MODE_COMPACT);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
                            .positiveText(R.string.dialog_button_dismiss)
                            .positiveColorRes(R.color.dialog_button_default)
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
        private PromptMode mPromptMode;
        private SimpleObserver<ResponseBody> mCallback;

        public Builder(Context context) {
            mContext = context;
        }

        public Builder(@NonNull View view) {
            this(view.getContext());
            mView = view;
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
            return new UpdateChecker(mContext, mView, mPromptMode, mCallback);
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
                    //  ! zh-CN:
                    //  ! 添加一个 "取消" 按钮用于中断操作.
                    //  ! 可能需要用到并发编程.
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
                            .positiveColorRes(R.color.dialog_button_default)
                            .cancelable(false);
                }

            }

            public static class Update extends MaterialDialog.Builder {

                public Update(Context context, VersionInfo versionInfo) {
                    super(context);
                    this
                            .title(versionInfo.getVersionName())
                            .options(List.of(new MaterialDialog.OptionMenuItemSpec(context.getString(R.string.dialog_button_ignore_current_update), parentDialog -> {
                                new MaterialDialog.Builder(context)
                                        .title(R.string.text_prompt)
                                        .content(R.string.prompt_add_ignored_version)
                                        .negativeText(R.string.dialog_button_cancel)
                                        .positiveText(R.string.dialog_button_confirm)
                                        .positiveColorRes(R.color.dialog_button_warn)
                                        .onPositive((tmpDialog, which) -> {
                                            UpdateUtils.addIgnoredVersion(versionInfo);
                                            ViewUtils.showToast(context, R.string.text_done);
                                            parentDialog.dismiss();
                                        })
                                        .show();
                            })))
                            .content(R.string.text_getting_release_notes)
                            .neutralText(R.string.dialog_button_version_histories)
                            .neutralColor(context.getColor(R.color.dialog_button_hint))
                            .onNeutral((dialog, which) -> {
                                DisplayVersionHistoriesActivity.launch(context);
                            })
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
                    //  ! zh-CN:
                    //  ! 鉴于安卓 O (API 26) 才开始引入 java.time 包,
                    //  ! API 24 以及 25 在此处将抛出一个 java.lang.NoClassDefFoundError 异常.
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
                /* Ignored. */
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
                return textBuilder.toString();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
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

    private static class ObservableEmptyException extends RuntimeException {
        /* Empty body. */
    }

}