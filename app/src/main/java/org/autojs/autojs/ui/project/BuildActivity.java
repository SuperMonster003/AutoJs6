package org.autojs.autojs.ui.project;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.material.textfield.TextInputLayout;

import org.autojs.autojs.apkbuilder.ApkBuilder;
import org.autojs.autojs.apkbuilder.ApkBuilderPluginHelper;
import org.autojs.autojs.external.fileprovider.AppFileProvider;
import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.project.ProjectConfig;
import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs.ui.filechooser.FileChooserDialogBuilder;
import org.autojs.autojs.ui.shortcut.AppsIconSelectActivity;
import org.autojs.autojs.util.BitmapUtils;
import org.autojs.autojs.util.EnvironmentUtils;
import org.autojs.autojs.util.IntentUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs.util.WorkingDirectoryUtils;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.ActivityBuildBinding;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by Stardust on 2017/10/22.
 * Modified by SuperMonster003 as of Dec 1, 2023.
 */
public class BuildActivity extends BaseActivity implements ApkBuilder.ProgressCallback {

    private static final int REQUEST_CODE = 44401;

    public static final String EXTRA_SOURCE = BuildActivity.class.getName() + ".extra_source_file";

    private static final String LOG_TAG = "BuildActivity";
    private static final Pattern REGEX_PACKAGE_NAME = Pattern.compile("^([A-Za-z][A-Za-z\\d_]*\\.)+([A-Za-z][A-Za-z\\d_]*)$");
    EditText mSourcePath;
    View mSourcePathContainer;
    EditText mOutputPath;
    EditText mAppName;
    EditText mPackageName;
    EditText mVersionName;
    EditText mVersionCode;
    ImageView mIcon;
    CardView mAppConfig;

    private ProjectConfig mProjectConfig;
    private MaterialDialog mProgressDialog;
    private String mSource;
    private boolean mIsDefaultIcon = true;
    private boolean mIsApkTemplateInAssets = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityBuildBinding binding = ActivityBuildBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mSourcePath = binding.sourcePath;
        mSourcePathContainer = binding.sourcePathContainer;
        mOutputPath = binding.outputPath;
        mAppName = binding.appName;
        mPackageName = binding.packageName;
        mVersionName = binding.versionName;
        mVersionCode = binding.versionCode;
        mIcon = binding.icon;
        mAppConfig = binding.appConfig;

        binding.fab.setOnClickListener(v -> buildApk());
        binding.selectSource.setOnClickListener(v -> selectSourceFilePath());
        binding.selectOutput.setOnClickListener(v -> selectOutputDirPath());
        mIcon.setOnClickListener(v -> selectIcon());

        setToolbarAsBack(R.string.text_build_apk);
        mSource = getIntent().getStringExtra(EXTRA_SOURCE);
        if (mSource != null) {
            setupWithSourceFile(new ScriptFile(mSource));
        }
        mIsApkTemplateInAssets = checkApkTemplateInAssets();
        if (!mIsApkTemplateInAssets) {
            checkApkBuilderPlugin();
        }
    }

    private boolean checkApkTemplateInAssets() {
        try {
            if (Arrays.asList(getAssets().list("")).contains("template.apk")) {
                return true;
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, e.getMessage());
        }
        return false;
    }

    private void checkApkBuilderPlugin() {
        if (!ApkBuilderPluginHelper.isPluginAvailable(this) || ApkBuilderPluginHelper.getPluginVersion(this) < 0) {
            showPluginDownloadDialog();
        }
    }

    private void showPluginDownloadDialog() {
        new MaterialDialog.Builder(this)
                .title(R.string.text_prompt)
                .content(R.string.no_apk_builder_plugin)
                .positiveText(R.string.text_download)
                .negativeText(R.string.text_cancel)
                .onPositive((dialog, which) -> downloadPlugin())
                .dismissListener(dialog -> finish())
                .show();

    }

    private void downloadPlugin() {
        IntentUtils.browse(this, "https://raw.githubusercontent.com/SuperMonster002/Hello-Sockpuppet/master/" +
                "%5B" + "auto.js" + "%5D" +
                "%5B" + "apk_builder_plugin_4.1.1_alpha2" + "%5D" +
                "%5B" + "arm-v7a" + "%5D" +
                "%5B" + "9b150ec3" + "%5D" + ".apk");
    }

    private void setupWithSourceFile(ScriptFile file) {
        String dir = file.getParent();
        if (dir != null && dir.startsWith(getFilesDir().getPath())) {
            dir = WorkingDirectoryUtils.getPath();
        }
        mOutputPath.setText(dir);
        mAppName.setText(file.getSimplifiedName());
        mPackageName.setText(getString(R.string.format_default_package_name, System.currentTimeMillis()));
        setSource(file);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    void selectSourceFilePath() {
        String initialDir = new File(mSourcePath.getText().toString()).getParent();
        new FileChooserDialogBuilder(this)
                .title(R.string.text_source_file_path)
                .dir(EnvironmentUtils.getExternalStoragePath(),
                        initialDir == null ? WorkingDirectoryUtils.getPath() : initialDir)
                .singleChoice(this::setSource)
                .show();
    }

    private void setSource(File file) {
        if (!file.isDirectory()) {
            mSourcePath.setText(file.getPath());
            return;
        }
        mProjectConfig = ProjectConfig.fromProjectDir(file.getPath());
        if (mProjectConfig == null) {
            return;
        }
        mOutputPath.setText(new File(mSource, mProjectConfig.getBuildDir()).getPath());
        mAppConfig.setVisibility(View.GONE);
        mSourcePathContainer.setVisibility(View.GONE);
    }

    void selectOutputDirPath() {
        String initialDir = new File(mOutputPath.getText().toString()).exists()
                ? mOutputPath.getText().toString()
                : WorkingDirectoryUtils.getPath();
        new FileChooserDialogBuilder(this)
                .title(R.string.text_output_apk_path)
                .dir(initialDir)
                .chooseDir()
                .singleChoice(dir -> mOutputPath.setText(dir.getPath()))
                .show();
    }

    void selectIcon() {
        AppsIconSelectActivity.launchForResult(this, REQUEST_CODE);
    }

    void buildApk() {
        if (!mIsApkTemplateInAssets && !ApkBuilderPluginHelper.isPluginAvailable(this)) {
            ViewUtils.showToast(this, R.string.text_apk_builder_plugin_unavailable);
            return;
        }
        if (!checkInputs()) {
            return;
        }
        doBuildingApk();
    }

    private boolean checkInputs() {
        return checkNotEmpty(mSourcePath)
                & checkNotEmpty(mOutputPath)
                & checkNotEmpty(mAppName)
                & checkNotEmpty(mSourcePath)
                & checkNotEmpty(mVersionCode)
                & checkNotEmpty(mVersionName)
                & checkPackageNameValid(mPackageName);
    }

    private boolean checkPackageNameValid(EditText editText) {
        Editable text = editText.getText();
        String hint = Objects.requireNonNull(((TextInputLayout) editText.getParent().getParent()).getHint()).toString();
        if (TextUtils.isEmpty(text)) {
            editText.setError(hint + getString(R.string.text_should_not_be_empty));
            return false;
        }
        if (!REGEX_PACKAGE_NAME.matcher(text).matches()) {
            editText.setError(getString(R.string.text_invalid_package_name));
            return false;
        }
        return true;

    }

    private boolean checkNotEmpty(EditText editText) {
        if (!TextUtils.isEmpty(editText.getText()) || !editText.isShown())
            return true;
        // TODO: 2017/12/8 more beautiful ways?
        String hint = Objects.requireNonNull(((TextInputLayout) editText.getParent().getParent()).getHint()).toString();
        editText.setError(hint + getString(R.string.text_should_not_be_empty));
        return false;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    private void doBuildingApk() {
        ApkBuilder.AppConfig appConfig = createAppConfig();
        File tmpDir = new File(getCacheDir(), "build/");
        File outApk = new File(mOutputPath.getText().toString(),
                String.format("%s_v%s.apk", appConfig.getAppName(), appConfig.getVersionName()));
        showProgressDialog();
        Observable.fromCallable(() -> callApkBuilder(tmpDir, outApk, appConfig))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(apkBuilder -> onBuildSuccessful(outApk),
                        this::onBuildFailed);
    }

    private ApkBuilder.AppConfig createAppConfig() {
        if (mProjectConfig != null) {
            return ApkBuilder.AppConfig.fromProjectConfig(mSource, mProjectConfig);
        }
        String jsPath = mSourcePath.getText().toString();
        String versionName = mVersionName.getText().toString();
        int versionCode = Integer.parseInt(mVersionCode.getText().toString());
        String appName = mAppName.getText().toString();
        String packageName = mPackageName.getText().toString();
        return new ApkBuilder.AppConfig()
                .setAppName(appName)
                .setSourcePath(jsPath)
                .setPackageName(packageName)
                .setVersionCode(versionCode)
                .setVersionName(versionName)
                .setIcon(mIsDefaultIcon ? null : () -> BitmapUtils.drawableToBitmap(mIcon.getDrawable()));
    }

    private ApkBuilder callApkBuilder(File tmpDir, File outApk, ApkBuilder.AppConfig appConfig) throws Exception {
        InputStream templateApk = mIsApkTemplateInAssets ? getAssets().open("template.apk") : ApkBuilderPluginHelper.openTemplateApk(BuildActivity.this);
        return new ApkBuilder(templateApk, outApk, tmpDir.getPath())
                .setProgressCallback(BuildActivity.this)
                .prepare()
                .withConfig(appConfig)
                .build()
                .sign()
                .cleanWorkspace();
    }

    private void showProgressDialog() {
        mProgressDialog = new MaterialDialog.Builder(this)
                .progress(true, 100)
                .content(R.string.text_in_progress)
                .cancelable(false)
                .show();
    }

    private void onBuildFailed(Throwable error) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
        String message = getString(R.string.text_failed_to_build) + "\n" + error.getMessage();
        ViewUtils.showToast(this, message, true);
        Log.e(LOG_TAG, "Failed to build", error);
    }

    private void onBuildSuccessful(File outApk) {
        mProgressDialog.dismiss();
        mProgressDialog = null;
        new MaterialDialog.Builder(this)
                .title(R.string.text_build_succeeded)
                .content(getString(R.string.format_build_succeeded, outApk.getPath()))
                .positiveText(R.string.text_install)
                .negativeText(R.string.text_cancel)
                .onPositive((dialog, which) -> IntentUtils.installApkOrToast(BuildActivity.this, outApk.getPath(), AppFileProvider.AUTHORITY))
                .show();

    }

    @Override
    public void onPrepare(ApkBuilder builder) {
        mProgressDialog.setContent(R.string.apk_builder_prepare);
    }

    @Override
    public void onBuild(ApkBuilder builder) {
        mProgressDialog.setContent(R.string.apk_builder_build);

    }

    @Override
    public void onSign(ApkBuilder builder) {
        mProgressDialog.setContent(R.string.apk_builder_package);

    }

    @Override
    public void onClean(ApkBuilder builder) {
        mProgressDialog.setContent(R.string.apk_builder_clean);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint({"CheckResult", "MissingSuperCall"})
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            AppsIconSelectActivity.getBitmapFromIntent(getApplicationContext(), data)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(bitmap -> {
                        mIcon.setImageBitmap(bitmap);
                        mIsDefaultIcon = false;
                    }, Throwable::printStackTrace);
        }
    }

    public static void launch(Context context, String extraSource) {
        Intent intent = new Intent(context, BuildActivity.class)
                .putExtra(BuildActivity.EXTRA_SOURCE, extraSource);
        context.startActivity(intent);
    }

}
