package org.autojs.autojs.ui.project;

import static org.autojs.autojs.apkbuilder.ApkBuilder.TEMPLATE_APK_NAME;
import static org.autojs.autojs.util.StringUtils.key;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.textfield.TextInputLayout;

import org.autojs.autojs.apkbuilder.ApkBuilder;
import org.autojs.autojs.external.fileprovider.AppFileProvider;
import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.pref.Language;
import org.autojs.autojs.project.ProjectConfig;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs.ui.common.NotAskAgainDialog;
import org.autojs.autojs.ui.filechooser.FileChooserDialogBuilder;
import org.autojs.autojs.ui.shortcut.AppsIconSelectActivity;
import org.autojs.autojs.ui.widget.RoundCheckboxWithText;
import org.autojs.autojs.util.AndroidUtils;
import org.autojs.autojs.util.AndroidUtils.Abi;
import org.autojs.autojs.util.BitmapUtils;
import org.autojs.autojs.util.EnvironmentUtils;
import org.autojs.autojs.util.IntentUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs.util.WorkingDirectoryUtils;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.ActivityBuildBinding;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by Stardust on Oct 22, 2017.
 * Modified by SuperMonster003 as of Dec 1, 2023.
 */
public class BuildActivity extends BaseActivity implements ApkBuilder.ProgressCallback {

    private static final int REQUEST_CODE = 44401;
    public static final String EXTRA_SOURCE = BuildActivity.class.getName() + ".extra_source_file";
    private static final String LOG_TAG = "BuildActivity";
    private static final Pattern REGEX_PACKAGE_NAME = Pattern.compile("^([A-Za-z][A-Za-z\\d_]*\\.)+([A-Za-z][A-Za-z\\d_]*)$");

    private static final LinkedList<String> SUPPORTED_ABIS = new LinkedList<>() {{
        add(Abi.ARM64_V8A);
        add(Abi.X86_64);
        add(Abi.ARMEABI_V7A);
        add(Abi.X86);
        add(Abi.ARMEABI);
    }};

    private static final LinkedList<String> SUPPORTED_LIBS = new LinkedList<>() {{
        add(ApkBuilder.Constants.OPENCV);
        add(ApkBuilder.Constants.MLKIT_GOOGLE_OCR);
        add(ApkBuilder.Constants.PADDLE_LITE);
        add(ApkBuilder.Constants.MLKIT_BARCODE);
        add(ApkBuilder.Constants.OPENCC);
    }};

    EditText mSourcePath;
    View mSourcePathContainer;
    EditText mOutputPath;
    EditText mAppName;
    EditText mPackageName;
    EditText mVersionName;
    EditText mVersionCode;
    ImageView mIcon;
    LinearLayout mAppConfig;

    private ProjectConfig mProjectConfig;
    private MaterialDialog mProgressDialog;
    private String mSource;
    private boolean mIsDefaultIcon = true;
    private boolean mIsProjectLevelBuilding;
    private FlexboxLayout mFlexboxAbis;
    private FlexboxLayout mFlexboxLibs;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityBuildBinding binding = ActivityBuildBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mSourcePath = binding.sourcePath;
        mSourcePath.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    mOutputPath.requestFocus();
                }
                return true;
            }
            return false;
        });

        mSourcePathContainer = binding.sourcePathContainer;

        mOutputPath = binding.outputPath;
        mOutputPath.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    TextView nextField = (TextView) mOutputPath.focusSearch(View.FOCUS_DOWN);
                    if (nextField != null) {
                        nextField.requestFocus();
                    }
                }
                return true;
            }
            return false;
        });

        mAppName = binding.appName;

        mPackageName = binding.packageName;
        mPackageName.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    mVersionName.requestFocus();
                }
                return true;
            }
            return false;
        });

        mVersionName = binding.versionName;
        mVersionCode = binding.versionCode;

        mIcon = binding.appIcon;
        mIcon.setOnClickListener(v -> selectIcon());

        mAppConfig = binding.appConfig;

        mFlexboxAbis = binding.flexboxAbis;
        initAbisChildren();
        syncAbisWithApp();

        mFlexboxLibs = binding.flexboxLibraries;
        initLibsChildren();

        binding.fab.setOnClickListener(v -> buildApk());
        binding.selectSource.setOnClickListener(v -> selectSourceFilePath());
        binding.selectOutput.setOnClickListener(v -> selectOutputDirPath());
        binding.textAbis.setOnClickListener(v -> toggleAllFlexboxChildren(mFlexboxAbis));
        binding.textAbis.setOnLongClickListener(v -> {
            syncAbisWithApp();
            return true;
        });
        binding.textLibs.setOnClickListener(v -> toggleAllFlexboxChildren(mFlexboxLibs));

        setToolbarAsBack(R.string.text_build_apk);
        mSource = getIntent().getStringExtra(EXTRA_SOURCE);
        if (mSource != null) {
            setupWithSourceFile(new ScriptFile(mSource));
        }
    }

    private void toggleAllFlexboxChildren(FlexboxLayout mFlexboxLibs) {
        boolean isAllChecked = true;
        for (int i = 0; i < mFlexboxLibs.getChildCount(); i += 1) {
            View child = mFlexboxLibs.getChildAt(i);
            if (child instanceof RoundCheckboxWithText) {
                if (!((RoundCheckboxWithText) child).isEnabled()) {
                    continue;
                }
                if (!((RoundCheckboxWithText) child).isChecked()) {
                    isAllChecked = false;
                    break;
                }
            }
        }
        for (int i = 0; i < mFlexboxLibs.getChildCount(); i += 1) {
            View child = mFlexboxLibs.getChildAt(i);
            if (child instanceof RoundCheckboxWithText) {
                if (!((RoundCheckboxWithText) child).isEnabled()) {
                    continue;
                }
                ((RoundCheckboxWithText) child).setChecked(!isAllChecked);
            }
        }
    }

    private void initAbisChildren() {
        SUPPORTED_ABIS.forEach((abiText) -> {
            RoundCheckboxWithText child = new RoundCheckboxWithText(this, null) {
                @Override
                public boolean onInterceptTouchEvent(@Nullable MotionEvent ev) {
                    if (!isEnabled()) {
                        promptForUnavailability();
                    }
                    return super.onInterceptTouchEvent(ev);
                }

                private void promptForUnavailability() {
                    Context context = getContext();
                    String key = key(R.string.key_dialog_selected_abi_is_unavailable);
                    NotAskAgainDialog.Builder builder = new NotAskAgainDialog.Builder(context, key);
                    builder.title(R.string.text_prompt);
                    builder.content(getString(R.string.text_unable_to_build_apk_as_autojs6_does_not_include_selected_abi, abiText) + "\n\n" +
                                    getString(R.string.text_the_following_solutions_can_be_referred_to) + ":\n\n" +
                                    "- " + getString(R.string.text_download_and_install_autojs6_including_above_abi, abiText) + "\n" +
                                    "- " + getString(R.string.text_download_and_install_autojs6_including_all_abis) + " [" + getString(R.string.text_recommended) + "]\n\n" +
                                    getString(R.string.text_download_link_for_autojs6) + ":\n" +
                                    getString(R.string.uri_autojs6_download_link));
                    builder.positiveText(R.string.dialog_button_dismiss);
                    MaterialDialog dialog = builder.show();
                    if (dialog != null) {
                        TextView contentView = dialog.getContentView();
                        if (contentView != null) {
                            Linkify.addLinks(contentView, Pattern.compile("https?://.*"), null);
                        }
                    } else {
                        ViewUtils.showToast(context, getString(R.string.text_unavailable_abi_for, abiText));
                    }
                }
            };
            child.setText(abiText);
            child.setChecked(false);
            child.setEnabled(false);
            mFlexboxAbis.addView(child);
        });
    }

    private void syncAbisWithApp() {
        List<String> appSupportedAbiList = AndroidUtils.getAppSupportedAbiList();
        List<String> deviceFilteredAbiList = AndroidUtils.getDeviceFilteredAbiList();
        if (appSupportedAbiList.isEmpty()) /* Just in case. */ {
            appSupportedAbiList = Collections.singletonList(AndroidUtils.appMainAbi);
        }
        for (int i = 0; i < mFlexboxAbis.getChildCount(); i += 1) {
            View child = mFlexboxAbis.getChildAt(i);
            if (child instanceof RoundCheckboxWithText) {
                CharSequence charSequence = ((RoundCheckboxWithText) child).getText();
                if (charSequence != null) {
                    boolean isAppContains = appSupportedAbiList.contains(charSequence.toString());
                    boolean isDeviceContains = deviceFilteredAbiList.contains(charSequence.toString());
                    ((RoundCheckboxWithText) child).setChecked(isAppContains && isDeviceContains);
                    ((RoundCheckboxWithText) child).setEnabled(isAppContains);
                }
            }
        }
    }

    private void initLibsChildren() {
        SUPPORTED_LIBS.forEach((text) -> {
            RoundCheckboxWithText child = new RoundCheckboxWithText(this, null);
            child.setText(text);
            child.setChecked(false);
            mFlexboxLibs.addView(child);
        });
    }

    private void setupWithSourceFile(ScriptFile file) {
        String dir = file.getParent();
        if (dir != null && dir.startsWith(getFilesDir().getPath())) {
            dir = WorkingDirectoryUtils.getPath();
        }
        mOutputPath.setText(dir);
        mAppName.setText(file.getSimplifiedName());
        mPackageName.setText(getString(R.string.format_default_package_name, getPackageNameSuffix(file)));
        setSource(file);
    }

    private static String getPackageNameSuffix(ScriptFile file) {
        String name = file.getSimplifiedName()
                .replaceAll("[^\\w$]+", "_");
        if (name.matches("^\\d.*")) {
            name = "app_" + name;
        }
        return name.toLowerCase(Language.getPrefLanguage().getLocale());
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
        mIsProjectLevelBuilding = true;
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
        if (!checkInputs()) {
            ViewUtils.showToast(this, getString(R.string.error_input_fields_check_failed));
            return;
        }
        if (!checkAbis()) {
            ViewUtils.showToast(this, getString(R.string.error_at_least_one_abi_needs_to_be_selected));
            return;
        }
        doBuildingApk();
    }

    private boolean checkInputs() {
        if (mIsProjectLevelBuilding) {
            return checkNotEmpty(mOutputPath);
        }
        return checkNotEmpty(mSourcePath)
               & checkNotEmpty(mOutputPath)
               & checkNotEmpty(mAppName)
               & checkNotEmpty(mVersionCode)
               & checkNotEmpty(mVersionName)
               & checkPackageNameValid(mPackageName);
    }

    private boolean checkAbis() {
        for (int i = 0; i < mFlexboxAbis.getChildCount(); i += 1) {
            View child = mFlexboxAbis.getChildAt(i);
            if (child instanceof RoundCheckboxWithText) {
                if (((RoundCheckboxWithText) child).isChecked() && ((RoundCheckboxWithText) child).isEnabled()) {
                    return true;
                }
            }
        }
        return false;
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
        // TODO by Stardust on Dec 8, 2017.
        //  ! More beautiful ways?
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

        ArrayList<String> abis = new ArrayList<>();

        for (int i = 0; i < mFlexboxAbis.getChildCount(); i += 1) {
            View child = mFlexboxAbis.getChildAt(i);
            if (child instanceof RoundCheckboxWithText) {
                if (((RoundCheckboxWithText) child).isChecked()) {
                    CharSequence charSequence = ((RoundCheckboxWithText) child).getText();
                    if (charSequence != null) {
                        abis.add(charSequence.toString());
                    }
                }
            }
        }

        ArrayList<String> libs = new ArrayList<>();

        for (int i = 0; i < mFlexboxLibs.getChildCount(); i += 1) {
            View child = mFlexboxLibs.getChildAt(i);
            if (child instanceof RoundCheckboxWithText) {
                if (((RoundCheckboxWithText) child).isChecked()) {
                    CharSequence charSequence = ((RoundCheckboxWithText) child).getText();
                    if (charSequence != null) {
                        libs.add(charSequence.toString());
                    }
                }
            }
        }

        return new ApkBuilder.AppConfig()
                .setAppName(appName)
                .setSourcePath(jsPath)
                .setPackageName(packageName)
                .setVersionName(versionName)
                .setVersionCode(versionCode)
                .setAbis(abis)
                .setLibs(libs)
                .setIcon(mIsDefaultIcon ? null : () -> BitmapUtils.drawableToBitmap(mIcon.getDrawable()));
    }

    private ApkBuilder callApkBuilder(File tmpDir, File outApk, ApkBuilder.AppConfig appConfig) throws Exception {
        InputStream templateApk = getAssets().open(TEMPLATE_APK_NAME);
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
        ScriptRuntime.popException(message);
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
    public void onPrepare(@NonNull ApkBuilder builder) {
        mProgressDialog.setContent(R.string.apk_builder_prepare);
    }

    @Override
    public void onBuild(@NonNull ApkBuilder builder) {
        mProgressDialog.setContent(R.string.apk_builder_build);
    }

    @Override
    public void onSign(@NonNull ApkBuilder builder) {
        mProgressDialog.setContent(R.string.apk_builder_package);
    }

    @Override
    public void onClean(@NonNull ApkBuilder builder) {
        mProgressDialog.setContent(R.string.apk_builder_clean);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint({"CheckResult", "MissingSuperCall"})
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            AppsIconSelectActivity.getDrawableFromIntent(getApplicationContext(), data)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(drawable -> {
                        mIcon.setImageDrawable(drawable);
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
