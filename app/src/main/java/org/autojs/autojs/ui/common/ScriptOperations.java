package org.autojs.autojs.ui.common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.text.Editable;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.ShortcutManagerCompat;
import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.tencent.bugly.crashreport.BuglyLog;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.internal.functions.ObjectHelper;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import org.autojs.autojs.app.GlobalAppContext;
import org.autojs.autojs.core.pref.Pref;
import org.autojs.autojs.extension.MaterialDialogExtensions;
import org.autojs.autojs.external.ScriptIntents;
import org.autojs.autojs.model.explorer.Explorer;
import org.autojs.autojs.model.explorer.ExplorerDirPage;
import org.autojs.autojs.model.explorer.ExplorerFileItem;
import org.autojs.autojs.model.explorer.ExplorerPage;
import org.autojs.autojs.model.explorer.Explorers;
import org.autojs.autojs.model.sample.SampleFile;
import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.network.download.DownloadManager;
import org.autojs.autojs.pio.PFile;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.pio.UncheckedIOException;
import org.autojs.autojs.storage.file.TmpScriptFiles;
import org.autojs.autojs.ui.filechooser.FileChooserDialogBuilder;
import org.autojs.autojs.ui.shortcut.ShortcutCreateActivity;
import org.autojs.autojs.ui.timing.TimedTaskSettingActivity;
import org.autojs.autojs.util.EnvironmentUtils;
import org.autojs.autojs.util.ShortcutUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs.util.WorkingDirectoryUtils;
import org.autojs.autojs6.R;
import org.reactivestreams.Publisher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.autojs.autojs.app.DialogUtils.fixCheckBoxGravity;
import static org.autojs.autojs.app.DialogUtils.showDialog;
import static org.autojs.autojs.util.FileUtils.TYPE.JAVASCRIPT;
import static org.autojs.autojs.util.RhinoUtils.isMainThread;

/**
 * Created by Stardust on Jul 31, 2017.
 */
@SuppressLint("CheckResult")
public class ScriptOperations {

    private static final String LOG_TAG = "ScriptOperations";
    private final ExplorerPage mExplorerPage;
    private final Context mContext;
    private final View mView;
    private final ScriptFile mCurrentDirectory;
    private final Explorer mExplorer;

    private final List<String> availablePrefixes = List.of("test", "untitled", "unnamed", "script");

    public ScriptOperations(Context context, View view, ScriptFile currentDirectory) {
        mContext = context;
        mView = view;
        mCurrentDirectory = currentDirectory;
        mExplorer = Explorers.workspace();
        mExplorerPage = new ExplorerDirPage(currentDirectory, null);
    }

    public ScriptOperations(Context context, View view, ExplorerPage page) {
        mContext = context;
        mView = view;
        mCurrentDirectory = page.toScriptFile();
        mExplorer = Explorers.workspace();
        mExplorerPage = page;
    }

    public ScriptOperations(Context context, View view) {
        this(context, view, new ScriptFile(WorkingDirectoryUtils.getPath()));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void newScriptFileForScript(final String script) {
        showFileNameInputDialog("", JAVASCRIPT.extension).subscribe(input ->
                createScriptFile(getCurrentDirectoryPath() + input + JAVASCRIPT.extensionWithDot, script, false)
        );
    }

    private String getCurrentDirectoryPath() {
        String path = getCurrentDirectory().getPath();
        return path.endsWith(File.separator) ? path : path + File.separator;
    }

    private ScriptFile getCurrentDirectory() {
        return mCurrentDirectory;
    }

    public void createScriptFile(String path, String script, boolean editable) {
        if (PFiles.createIfNotExists(path)) {
            if (script != null) {
                try {
                    PFiles.write(path, script);
                } catch (UncheckedIOException e) {
                    showMessage(R.string.text_failed_to_write_file);
                    return;
                }
            }
            notifyFileCreated(new ScriptFile(path));
            if (editable) {
                Scripts.edit(mContext, path);
            }
        } else {
            showMessage(R.string.text_failed_to_create);
        }
    }

    private void notifyFileCreated(ScriptFile scriptFile) {
        if (scriptFile.isDirectory()) {
            mExplorer.notifyItemCreated(new ExplorerDirPage(scriptFile, mExplorerPage));
        } else {
            mExplorer.notifyItemCreated(new ExplorerFileItem(scriptFile, mExplorerPage));
        }
    }

    public void newFile() {
        final AtomicReference<MaterialDialog> dialogRef = new AtomicReference<>();
        String presetFileNamePrefix = getNewFileNamePresetPrefill(availablePrefixes.get(Pref.getInt(R.string.key_explorer_file_default_prefix, 0)));
        MaterialDialog.Builder builder = new MaterialDialog.Builder(mContext)
                .title(R.string.text_name)
                .inputType(InputType.TYPE_CLASS_TEXT)
                .alwaysCallInputCallback()
                .input(getString(R.string.text_please_input_name), presetFileNamePrefix, false, (d, input) -> {
                    validateInput(d, d.isPromptCheckBoxChecked() ? JAVASCRIPT.extensionWithDot : null);
                })
                .checkBoxPromptRes(R.string.text_js_file, true, (buttonView, isChecked) -> {
                    MaterialDialog dialogInstance = dialogRef.get();
                    if (dialogInstance != null) {
                        validateInput(dialogInstance, isChecked ? JAVASCRIPT.extensionWithDot : null);
                    }
                })
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive((d, which) -> {
                    assert d.getInputEditText() != null;
                    boolean createJs = d.isPromptCheckBoxChecked() || d.getInputEditText().getText().toString().endsWith(JAVASCRIPT.extensionWithDot);
                    String fileNameSuffix = createJs ? JAVASCRIPT.extensionWithDot : "";
                    createScriptFile(getCurrentDirectoryPath() + d.getInputEditText().getText() + fileNameSuffix, null, createJs);
                    d.dismiss();
                })
                .negativeText(R.string.dialog_button_cancel)
                .onNegative((d, which) -> d.dismiss())
                .neutralText(R.string.dialog_button_default_prefix)
                .neutralColor(mContext.getColor(R.color.dialog_button_hint))
                .onNeutral((d, which) -> {
                    // TODO Activity 或富 Dialog 形式
                    //  ! [Aa] 大小写切换图标支持点击循环切换
                    //  ! [A/文] 语言图标支持点击循环切换
                    //  ! [+] 添加图标支持添加自定义项目 (max 限制)
                    MaterialDialog.Builder builderDefaultPrefix = new MaterialDialog.Builder(mContext)
                            .title(R.string.text_default_prefix)
                            // TODO 多语言支持? 英文+当前
                            // TODO 大小写支持? FirstCharUpperCase/UpperCase/LowerCase
                            // TODO 自定义前缀
                            // TODO 添加 "无/禁用"
                            .items(availablePrefixes)
                            .itemsCallbackSingleChoice(Pref.getInt(R.string.key_explorer_file_default_prefix, 0), (dDefaultPrefix, view, i, text) -> {
                                Pref.putInt(R.string.key_explorer_file_default_prefix, i);
                                EditText editText = dialogRef.get().getInputEditText();
                                if (editText != null) {
                                    editText.setText(getNewFileNamePresetPrefill(text));
                                    editText.setSelection(editText.getText().length());
                                }
                                return true;
                            })
                            .positiveText(R.string.dialog_button_confirm)
                            .positiveColorRes(R.color.dialog_button_attraction)
                            .negativeText(R.string.dialog_button_back);
                    MaterialDialogExtensions.choiceWidgetThemeColor(builderDefaultPrefix);
                    MaterialDialog dialogDefaultPrefix = builderDefaultPrefix.build();
                    showDialog(dialogDefaultPrefix);
                })
                .autoDismiss(false);
        MaterialDialogExtensions.widgetThemeColor(builder);
        MaterialDialog dialog = builder.build();
        dialogRef.set(dialog);

        showDialog(fixCheckBoxGravity(dialog));
    }

    private String getNewFileNamePresetPrefill(CharSequence prefix) {
        var idx = 1;
        PFile[] fileList = getCurrentDirectory().listFiles();
        if (fileList == null) {
            return prefix + "-" + idx;
        }
        var nums = new ArrayList<Integer>();
        for (PFile file : fileList) {
            var fileName = file.getSimplifiedName();
            Pattern pattern = Pattern.compile(prefix + "[\\s-_@#]*(\\d+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(fileName);
            if (!matcher.find()) {
                continue;
            }
            String currentNum = matcher.group(1);
            if (currentNum == null) {
                continue;
            }
            int currentNumInt = Integer.parseInt(currentNum);
            nums.add(currentNumInt);
        }
        if (nums.isEmpty()) {
            return prefix + "-" + idx;
        }
        int max = Collections.max(nums);
        while (idx <= max) {
            if (!nums.contains(idx)) {
                break;
            }
            idx += 1;
        }
        return prefix + "-" + idx;
    }

    private void validateInput(MaterialDialog dialog, String extension) {
        EditText editText = dialog.getInputEditText();
        if (editText == null) {
            return;
        }
        Editable input = editText.getText();
        if (input == null || input.length() == 0) {
            dialog.getActionButton(DialogAction.POSITIVE).setEnabled(false);
            return;
        }
        boolean exists = new File(getCurrentDirectory(), extension == null ? input.toString() : input + extension).exists();
        CharSequence errorMessage = exists ? getString(R.string.text_file_exists) : null;
        editText.setError(errorMessage);
        dialog.getActionButton(DialogAction.POSITIVE).setEnabled(!exists);
    }

    public Observable<String> importFile(final String pathFrom) {
        return showFileNameInputDialog(PFiles.getNameWithoutExtension(pathFrom), PFiles.getExtension(pathFrom))
                .observeOn(Schedulers.io())
                .map(input -> {
                    final String pathTo = getCurrentDirectoryPath() + input + "." + PFiles.getExtension(pathFrom);
                    if (PFiles.copy(pathFrom, pathTo)) {
                        showMessage(R.string.text_import_succeed);
                    } else {
                        showMessage(R.string.text_failed_to_import);
                    }
                    return pathTo;
                })
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(path -> notifyFileCreated(new ScriptFile(path)));
    }

    public Observable<String> importFile(String prefix, final InputStream inputStream, final String ext) {
        return showFileNameInputDialog(PFiles.getNameWithoutExtension(prefix), ext)
                .observeOn(Schedulers.io())
                .map(input -> {
                    final String pathTo = getCurrentDirectoryPath() + input + "." + ext;
                    if (PFiles.copyStream(inputStream, pathTo)) {
                        showMessage(R.string.text_import_succeed);
                    } else {
                        showMessage(R.string.text_failed_to_import);
                    }
                    notifyFileCreated(new ScriptFile(pathTo));
                    return pathTo;
                });
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void newDirectory() {
        showNameInputDialog("", new InputCallback())
                .subscribe(path -> {
                    ScriptFile newDir = new ScriptFile(getCurrentDirectory(), path);
                    if (newDir.mkdirs()) {
                        showMessage(R.string.text_already_created);
                        notifyFileCreated(new ScriptFile(newDir));
                    } else {
                        showMessage(R.string.text_failed_to_create);
                    }
                });
    }

    private void showMessage(final int resId) {
        if (isMainThread()) {
            showMessageWithoutThreadSwitch(resId);
        }
        // switch to ui thread to show message
        GlobalAppContext.post(() -> showMessageWithoutThreadSwitch(resId));
    }

    private void showMessageWithoutThreadSwitch(int resId) {
        if (mView != null) {
            ViewUtils.showSnack(mView, resId);
        } else {
            ViewUtils.showToast(mContext, resId);
        }
    }

    private Observable<String> showFileNameInputDialog(String prefix, String ext) {
        return showNameInputDialog(prefix, new InputCallback(ext));
    }

    private Observable<String> showNameInputDialog(String prefix, MaterialDialog.InputCallback textWatcher) {
        final PublishSubject<String> input = PublishSubject.create();
        MaterialDialog.Builder builder = new MaterialDialog.Builder(mContext)
                .title(R.string.text_name)
                .inputType(InputType.TYPE_CLASS_TEXT)
                .alwaysCallInputCallback()
                .input(getString(R.string.text_please_input_name), prefix, false, textWatcher)
                .negativeText(R.string.dialog_button_cancel)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive((dialog, which) -> {
                    input.onNext(Objects.requireNonNull(dialog.getInputEditText()).getText().toString());
                    input.onComplete();
                })
                .canceledOnTouchOutside(false);
        MaterialDialogExtensions.widgetThemeColor(builder);
        showDialog(builder.build());
        return input;
    }

    private CharSequence getString(int resId) {
        return mContext.getString(resId);
    }

    public Observable<String> importSample(SampleFile sample) {
        try {
            return importFile(sample.getSimplifiedName(), sample.openInputStream(), sample.getExtension());
        } catch (IOException e) {
            e.printStackTrace();
            showMessage(R.string.text_failed_to_import);
            return Observable.error(e);
        }
    }

    public Observable<ExplorerFileItem> rename(final ExplorerFileItem item) {
        String originalName = item.getName();
        return showNameInputDialog(originalName, new InputCallback(null, originalName))
                .map(newName -> {
                    if (newName.equals(originalName)) {
                        return item;
                    }
                    ExplorerFileItem newItem = item.rename(newName);
                    if (ObjectHelper.equals(newItem.toScriptFile(), item.toScriptFile())) {
                        showMessage(R.string.error_cannot_rename);
                        throw new IOException();
                    }
                    notifyFileChanged(item, newItem);
                    return newItem;
                });
    }

    private void notifyFileChanged(ExplorerFileItem oldItem, ExplorerFileItem newItem) {
        mExplorer.notifyItemChanged(oldItem, newItem);
    }

    public void createShortcut(ScriptFile file) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(mContext)) {
            ShortcutUtils.showPinShortcutNotSupportedDialog(mContext);
            return;
        }
        Intent intent = new Intent(mContext, ShortcutCreateActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(ShortcutCreateActivity.EXTRA_FILE, file);
        mContext.startActivity(intent);
    }

    public void delete(final ScriptFile scriptFile) {
        new MaterialDialog.Builder(mContext)
                .title(mContext.getString(R.string.text_confirm_to_delete))
                .content(scriptFile.getName())
                .negativeText(R.string.text_cancel)
                .positiveText(R.string.text_ok)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive((dialog, which) -> deleteWithoutConfirm(scriptFile))
                .build()
                .show();
    }

    public void setAsWorkingDir(final ScriptFile scriptFile) {
        String oldPath = WorkingDirectoryUtils.getRelativePath();
        String newPath = WorkingDirectoryUtils.toRelativePath(scriptFile.getPath());
        if (newPath.equals(oldPath)) {
            showMessage(R.string.text_new_path_is_same_as_old_path);
            return;
        }
        String content = mContext.getString(R.string.text_old_path) + ": " + oldPath + "\n"
                         + mContext.getString(R.string.text_new_path) + ": " + newPath;
        new MaterialDialog.Builder(mContext)
                .title(mContext.getString(R.string.text_prompt))
                .content(content)
                .negativeText(R.string.text_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.text_ok)
                .positiveColorRes(R.color.dialog_button_warn)
                .onPositive((dialog, which) -> setAsWorkingDirNow(scriptFile))
                .build()
                .show();
    }

    public void deleteWithoutConfirm(final ScriptFile scriptFile) {
        boolean isDir = scriptFile.isDirectory();
        Observable.fromPublisher((Publisher<Boolean>) s -> s.onNext(PFiles.deleteRecursively(scriptFile)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(deleted -> {
                    if (deleted) {
                        showMessage(R.string.text_already_deleted);
                        notifyFileRemoved(isDir, scriptFile);
                    } else {
                        showMessage(R.string.text_failed_to_delete);
                    }
                });
    }

    public void setAsWorkingDirNow(final ScriptFile scriptFile) {
        WorkingDirectoryUtils.setPath(scriptFile.getPath());
        WorkingDirectoryUtils.addIntoHistories(scriptFile.getPath());
        showMessage(R.string.text_done);
    }

    private void notifyFileRemoved(boolean isDir, ScriptFile scriptFile) {
        if (isDir) {
            mExplorer.notifyItemRemoved(new ExplorerDirPage(scriptFile, mExplorerPage));
        } else {
            mExplorer.notifyItemRemoved(new ExplorerFileItem(scriptFile, mExplorerPage));
        }
    }

    public Observable<ScriptFile> download(String url) {
        BuglyLog.i(LOG_TAG, "dir = " + WorkingDirectoryUtils.getPath() + ", sdcard = " + Environment.getExternalStorageDirectory() + ", url = " + url);
        String fileName = DownloadManager.parseFileNameLocally(url);
        return new FileChooserDialogBuilder(mContext)
                .title(R.string.text_save_to)
                .dir(WorkingDirectoryUtils.getPath())
                .chooseDir()
                .singleChoice()
                .map(saveDir -> new File(saveDir, fileName).getPath())
                .flatMap(savePath -> {
                    if (!new File(savePath).exists()) {
                        return Observable.just(savePath);
                    }
                    return RxDialogs.confirm(mContext, R.string.confirm_overwrite_file, R.color.dialog_button_caution)
                            .flatMap(yes -> {
                                if (yes) {
                                    if (!new File(savePath).delete()) {
                                        Log.e(LOG_TAG, "download: delete failed");
                                    }
                                    return Observable.just(savePath);
                                } else {
                                    return Observable.empty();
                                }
                            });
                })
                .flatMap(savePath -> DownloadManager.getInstance().downloadWithProgress(mContext, url, savePath))
                .map(ScriptFile::new);
    }

    public Observable<ScriptFile> temporarilyDownload(String url) {
        return Observable.fromCallable(() -> TmpScriptFiles.create(mContext))
                .flatMap(tmpFile ->
                        DownloadManager.getInstance().downloadWithProgress(mContext, url, tmpFile.getPath()))
                .map(ScriptFile::new);
    }

    public void importFile() {
        new FileChooserDialogBuilder(mContext)
                .dir(EnvironmentUtils.getExternalStoragePath())
                .justScriptFile()
                .singleChoice(file -> importFile(file.getPath()).subscribe())
                .title(R.string.text_select_file_to_import)
                .positiveText(R.string.text_ok)
                .positiveColorRes(R.color.dialog_button_attraction)
                .show();
    }

    public void timedTask(ScriptFile scriptFile) {
        Intent intent = new Intent(mContext, TimedTaskSettingActivity.class)
                .putExtra(ScriptIntents.EXTRA_KEY_PATH, scriptFile.getPath());
        mContext.startActivity(intent);
    }

    private class InputCallback implements MaterialDialog.InputCallback {

        private final String mExcluded;
        private boolean mIsFirstTextChanged = true;
        private final String mExtension;

        InputCallback(@Nullable String ext, @Nullable String excluded) {
            mExtension = ext == null ? null : "." + ext;
            mExcluded = excluded;
        }

        InputCallback(String ext) {
            this(ext, null);
        }

        InputCallback() {
            this(null);
        }

        @Override
        public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
            if (mIsFirstTextChanged) {
                mIsFirstTextChanged = false;
                return;
            }
            EditText editText = dialog.getInputEditText();
            if (editText == null) {
                return;
            }
            if (mExcluded != null && mExcluded.contentEquals(input)) {
                editText.setError(null);
                dialog.getActionButton(DialogAction.POSITIVE).setEnabled(true);
                return;
            }
            validateInput(dialog, mExtension);
        }
    }

}
