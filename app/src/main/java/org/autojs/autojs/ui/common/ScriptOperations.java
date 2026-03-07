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
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.internal.functions.ObjectHelper;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import org.autojs.autojs.app.GlobalAppContext;
import org.autojs.autojs.core.pref.Pref;
import org.autojs.autojs.external.ScriptIntents;
import org.autojs.autojs.model.explorer.Explorer;
import org.autojs.autojs.model.explorer.ExplorerDirPage;
import org.autojs.autojs.model.explorer.ExplorerFileItem;
import org.autojs.autojs.model.explorer.ExplorerPage;
import org.autojs.autojs.model.explorer.ExplorerProjectPage;
import org.autojs.autojs.model.explorer.Explorers;
import org.autojs.autojs.model.sample.SampleFile;
import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.network.download.DownloadManager;
import org.autojs.autojs.pio.PFile;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.pio.UncheckedIOException;
import org.autojs.autojs.project.ProjectConfig;
import org.autojs.autojs.storage.file.TmpScriptFiles;
import org.autojs.autojs.storage.history.HistoryPrefs;
import org.autojs.autojs.storage.history.HistoryRepository;
import org.autojs.autojs.storage.history.TrashRepository;
import org.autojs.autojs.ui.filechooser.FileChooserDialogBuilder;
import org.autojs.autojs.ui.shortcut.ShortcutCreateActivity;
import org.autojs.autojs.ui.timing.TimedTaskSettingActivity;
import org.autojs.autojs.util.DialogUtils;
import org.autojs.autojs.util.DialogUtils.OperationAbortedException;
import org.autojs.autojs.util.DialogUtils.OperationController;
import org.autojs.autojs.util.DialogUtils.ProgressDialogSession;
import org.autojs.autojs.util.EnvironmentUtils;
import org.autojs.autojs.util.FileUtils;
import org.autojs.autojs.util.IntentUtils;
import org.autojs.autojs.util.ShortcutUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs.util.WorkingDirectoryUtils;
import org.autojs.autojs6.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.autojs.autojs.model.explorer.ExplorerFileItem.isInSampleDir;
import static org.autojs.autojs.util.DialogUtils.fixCheckBoxGravity;
import static org.autojs.autojs.util.FileUtils.TYPE.JAVASCRIPT;
import static org.autojs.autojs.util.RhinoUtils.isMainThread;
import static org.autojs.autojs.util.ThreadUtils.runOnMain;

/**
 * Created by Stardust on Jul 31, 2017.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 6, 2026.
 * Modified by SuperMonster003 as of Feb 7, 2026.
 */
@SuppressWarnings({"ResultOfMethodCallIgnored", "CodeBlock2Expr", "unused"})
@SuppressLint("CheckResult")
public class ScriptOperations {

    private static final String LOG_TAG = "ScriptOperations";

    // Suffix for partial copy output file.
    // zh-CN: 复制过程的临时输出文件后缀.
    private static final String PARTIAL_COPY_SUFFIX = ".partial";

    private static final String INTERNAL_STORAGE_ROOT = "/storage/emulated/0";

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
            showMessage(R.string.error_failed_to_create);
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
                    DialogUtils.choiceWidgetThemeColor(builderDefaultPrefix);
                    MaterialDialog dialogDefaultPrefix = builderDefaultPrefix.build();
                    DialogUtils.showAdaptive(dialogDefaultPrefix);
                })
                .autoDismiss(false);
        DialogUtils.widgetThemeColor(builder);
        MaterialDialog dialog = builder.build();
        dialogRef.set(dialog);

        DialogUtils.showAdaptive(fixCheckBoxGravity(dialog));
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
                        showMessage(R.string.error_failed_to_create);
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
        DialogUtils.widgetThemeColor(builder);
        DialogUtils.showAdaptive(builder.build());
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
                        throw new IOException();
                    }
                    notifyFileChanged(item, newItem);
                    return newItem;
                });
    }

    public Observable<ScriptFile> copy(@NonNull ScriptFile source) {
        return copyOrMoveInternal(source, false);
    }

    public Observable<ScriptFile> move(@NonNull ScriptFile source) {
        return copyOrMoveInternal(source, true);
    }

    private Observable<ScriptFile> copyOrMoveInternal(@NonNull ScriptFile source, boolean isMove) {
        int titleRes = isMove ? R.string.text_move_to : R.string.text_copy_to;
        String currentDir = isInSampleDir(source) ? WorkingDirectoryUtils.getPath() : getCurrentDirectoryPath();
        return new FileChooserDialogBuilder(mContext)
                .title(titleRes)
                .dir(currentDir)
                .chooseDir()
                .singleChoice()
                .map(destDir -> new File(destDir.getPath(), source.getName()))
                .flatMap(dest -> resolveSamePathIfNeeded(source, dest, isMove))
                .flatMap(dest -> confirmOverwriteIfNeeded(dest).map(ok -> dest))
                .flatMap(dest -> Observable
                        .fromCallable(() -> {
                            boolean sourceIsProject = ProjectConfig.isProject(source);
                            boolean sourceIsDir = source.isDirectory();

                            OperationController controller = new OperationController();
                            ProgressDialogSession session = new ProgressDialogSession(controller);

                            // For abort handling and refresh.
                            // zh-CN: 用于中止处理与刷新.
                            boolean destTouched = false;

                            try {
                                long totalBytes = computeTotalBytes(source);

                                MaterialDialog.Builder progressBuilder = buildCopyProgressDialogBuilder(totalBytes, isMove, sourceIsDir, controller);

                                session.scheduleShow(progressBuilder);

                                if (isMove) {
                                    destTouched = true;

                                    moveFileOrDirWithProgress(
                                            source,
                                            dest,
                                            totalBytes,
                                            (copied, total) -> {
                                                controller.throwIfCancelled();
                                                session.setProgressThrottled(copied, totalBytes);
                                            },
                                            (currentFile) -> {
                                                controller.throwIfCancelled();
                                                session.setContentThrottled(toContentList(source, dest, currentFile));
                                            },
                                            controller
                                    );

                                    notifyFileRemovedAtItsParent(sourceIsProject, sourceIsDir, source);
                                } else {
                                    destTouched = true;

                                    copyFileOrDirWithProgress(
                                            source,
                                            dest,
                                            totalBytes,
                                            (copied, total) -> {
                                                controller.throwIfCancelled();
                                                session.setProgressThrottled(copied, totalBytes);
                                            },
                                            (currentFile) -> {
                                                controller.throwIfCancelled();
                                                session.setContentThrottled(toContentList(source, dest, currentFile));
                                            },
                                            controller
                                    );
                                }

                                ScriptFile newFile = new ScriptFile(dest.getPath());
                                notifyFileCreatedAtItsParent(ProjectConfig.isProject(newFile), newFile.isDirectory(), newFile);

                                GlobalAppContext.post(() -> ViewUtils.showSnack(mView, R.string.text_operation_completed));
                                return dest.getPath();
                            } catch (OperationAbortedException aborted) {
                                // Cleanup and refresh when aborted.
                                // zh-CN: 中止时清理并刷新.
                                if (destTouched) {
                                    notifyChildrenChangedAtItsParent(new ScriptFile(dest));
                                    if (isMove) {
                                        notifyChildrenChangedAtItsParent(source);
                                    }
                                }

                                GlobalAppContext.post(() -> ViewUtils.showSnack(mView, R.string.text_operation_aborted));
                                return "";
                            } finally {
                                session.dismissSafely();
                            }
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .flatMap(path -> path.isEmpty() ? Observable.empty() : Observable.just(new ScriptFile(path))));
    }

    // Notify Explorer that a directory's children may have changed, using the item's real parent.
    // zh-CN: 通知资源管理器某个目录的子项可能发生变化, 使用真实父目录.
    private void notifyChildrenChangedAtItsParent(@NonNull ScriptFile file) {
        File parent = new File(file.getPath()).getParentFile();
        if (parent == null) {
            return;
        }
        ExplorerDirPage parentPage = new ExplorerDirPage(new ScriptFile(parent.getPath()), null);
        runOnMain(() -> mExplorer.notifyChildrenChanged(parentPage));
    }

    // Build a progress dialog builder for copy/move operations.
    // zh-CN: 为复制/移动操作构建进度对话框 Builder.
    private MaterialDialog.Builder buildCopyProgressDialogBuilder(long totalBytes, boolean isMove, boolean isDir, @NonNull OperationController controller) {
        int titleRes = isDir
                ? isMove ? R.string.text_move_folder : R.string.text_copy_folder
                : isMove ? R.string.text_move_file : R.string.text_copy_file;

        int contentRes = isMove ? R.string.text_moving : R.string.text_copying;

        MaterialDialog.Builder builder = new MaterialDialog.Builder(mContext)
                .title(titleRes)
                .content(contentRes)
                .negativeText(R.string.dialog_button_abort)
                .negativeColorRes(R.color.dialog_button_caution)
                .onNegative((dialog, which) -> {
                    controller.cancel();
                })
                .cancelable(false)
                .canceledOnTouchOutside(false);

        if (totalBytes > 0 && totalBytes <= Integer.MAX_VALUE) {
            // Determinate progress in bytes for small/medium files.
            // zh-CN: 对于中小文件使用字节级确定性进度.
            builder.progress(false, (int) totalBytes, true);
        } else if (totalBytes > 0) {
            // Determinate progress in scaled units for huge files.
            // zh-CN: 对于超大文件使用缩放单位的确定性进度.
            builder.progress(false, DialogUtils.PROGRESS_MAX_LARGE, true);
        } else {
            builder.progress(true, 0);
        }
        return builder;
    }

    // Resolve same-path cases before overwrite confirmation and actual copy/move.
    // zh-CN: 在覆盖确认与实际复制/移动之前处理源路径与目标路径相同的情况.
    private Observable<File> resolveSamePathIfNeeded(@NonNull ScriptFile source, @NonNull File dest, boolean isMove) {
        return Observable.fromCallable(() -> {
                    File srcFile = new File(source.getPath());
                    return FileUtils.areSamePath(srcFile, dest);
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap(isSame -> {
                    if (!isSame) {
                        return Observable.just(dest);
                    }

                    if (isMove) {
                        DialogUtils.showAdaptive(new MaterialDialog.Builder(mContext)
                                .title(R.string.text_prompt)
                                .content(R.string.text_move_aborted_same_path)
                                .positiveText(R.string.dialog_button_dismiss)
                                .build());
                        return Observable.empty();
                    }

                    File parentFile = dest.getParentFile();
                    String newName = generateNextIndexedName(parentFile, source.getName(), source.isDirectory());
                    String message = mContext.getString(R.string.text_copy_same_path_confirm, newName);

                    return confirmWithMessage(message)
                            .flatMap(yes -> yes
                                    ? Observable.just(new File(parentFile, newName))
                                    : Observable.empty());
                });
    }

    private Observable<Boolean> confirmWithMessage(@NonNull CharSequence message) {
        PublishSubject<Boolean> result = PublishSubject.create();
        DialogUtils.showAdaptive(new MaterialDialog.Builder(mContext)
                .title(R.string.text_prompt)
                .content(message)
                .negativeText(R.string.dialog_button_abandon)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative((dialog, which) -> {
                    result.onNext(false);
                    result.onComplete();
                })
                .positiveText(R.string.dialog_button_continue)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive((dialog, which) -> {
                    result.onNext(true);
                    result.onComplete();
                })
                .cancelable(false)
                .build());
        return result;
    }

    // Generate next indexed name by scanning existing "-n" siblings and taking the first missing n from 1.
    // zh-CN: 通过扫描同级目录中已有的 "-n" 名称, 从 1 开始取第一个缺失的 n 作为新后缀.
    private String generateNextIndexedName(@Nullable File parentDir, @NonNull String originalName, boolean isDirectory) {
        String base;
        String extWithDot = "";

        if (!isDirectory) {
            int dot = originalName.lastIndexOf('.');
            if (dot > 0 && dot < originalName.length() - 1) {
                base = originalName.substring(0, dot);
                extWithDot = originalName.substring(dot);
            } else {
                base = originalName;
            }
        } else {
            base = originalName;
        }

        if (parentDir == null) {
            long timestamp = System.currentTimeMillis();
            if (isDirectory) {
                return base + "-" + timestamp;
            }
            return base + "-" + timestamp + extWithDot;
        }

        Set<Integer> existingIndexes = new HashSet<>();
        File[] files = parentDir.listFiles();
        if (files != null) {
            String pattern;
            if (isDirectory) {
                pattern = "^" + Pattern.quote(base) + "-(\\d+)$";
            } else {
                pattern = "^" + Pattern.quote(base) + "-(\\d+)" + Pattern.quote(extWithDot) + "$";
            }
            Pattern p = Pattern.compile(pattern);

            for (File f : files) {
                Matcher m = p.matcher(f.getName());
                if (m.matches()) {
                    try {
                        String foundIndex = m.group(1);
                        if (foundIndex == null) {
                            continue;
                        }
                        existingIndexes.add(Integer.parseInt(foundIndex));
                    } catch (NumberFormatException ignored) {
                        /* Ignored. */
                    }
                }
            }
        }

        int n = 1;
        while (existingIndexes.contains(n)) {
            n++;
        }

        if (isDirectory) {
            return base + "-" + n;
        }
        return base + "-" + n + extWithDot;
    }

    // Compute total bytes of all files under a file or directory.
    // zh-CN: 计算文件或目录下所有文件的总字节数.
    private long computeTotalBytes(@NonNull File src) {
        if (!src.exists()) {
            return 0L;
        }
        if (src.isFile()) {
            return Math.max(0L, src.length());
        }
        long sum = 0L;
        File[] children = src.listFiles();
        if (children == null) {
            return 0L;
        }
        for (File child : children) {
            sum += computeTotalBytes(child);
        }
        return sum;
    }

    // Copy file or directory recursively with progress updates and current path callback.
    // zh-CN: 带进度更新与当前路径回调的递归复制.
    private void copyFileOrDirWithProgress(
            @NonNull File src,
            @NonNull File dst,
            long totalBytes,
            @NonNull ProgressCallback progressCallback,
            @NonNull FileProgressCallback fileCallback,
            @NonNull OperationController controller
    ) throws IOException {
        controller.throwIfCancelled();
        long[] copied = new long[]{0L};
        copyFileOrDirWithProgressInternal(src, dst, totalBytes, copied, progressCallback, fileCallback, controller);
    }

    private void copyFileOrDirWithProgressInternal(
            @NonNull File src,
            @NonNull File dst,
            long totalBytes,
            @NonNull long[] copied,
            @NonNull ProgressCallback progressCallback,
            @NonNull FileProgressCallback fileCallback,
            @NonNull OperationController controller
    ) throws IOException {
        controller.throwIfCancelled();

        fileCallback.onFile(dst);

        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) {
                throw new IOException("mkdirs failed: " + dst);
            }
            File[] children = src.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                copyFileOrDirWithProgressInternal(child, new File(dst, child.getName()), totalBytes, copied, progressCallback, fileCallback, controller);
            }
            return;
        }

        copyFileWithProgressAtomic(src, dst, totalBytes, copied, progressCallback, fileCallback, controller);
    }

    // Copy file with an atomic "partial file then rename" strategy, supports abort cleanup.
    // zh-CN: 使用 "临时文件写入后再 rename" 的原子复制策略, 支持中止清理.
    private void copyFileWithProgressAtomic(
            @NonNull File src,
            @NonNull File dst,
            long totalBytes,
            @NonNull long[] copied,
            @NonNull ProgressCallback progressCallback,
            @NonNull FileProgressCallback fileCallback,
            @NonNull OperationController controller
    ) throws IOException {
        controller.throwIfCancelled();

        File parent = dst.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("mkdirs failed: " + parent);
        }

        File partial = new File(dst.getPath() + PARTIAL_COPY_SUFFIX + "." + UUID.randomUUID());
        boolean committed = false;

        try {
            try (FileInputStream fis = new FileInputStream(src);
                 FileOutputStream fos = new FileOutputStream(partial)) {

                byte[] buffer = new byte[256 * 1024];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    controller.throwIfCancelled();

                    fos.write(buffer, 0, read);
                    copied[0] += read;

                    fileCallback.onFile(dst);
                    progressCallback.onProgress(copied[0], totalBytes);
                }
                fos.getFD().sync();
            }

            controller.throwIfCancelled();

            // Replace destination by rename.
            // zh-CN: 通过 rename 替换目标文件.
            if (dst.exists() && !dst.delete()) {
                throw new IOException("failed to replace destination: " + dst);
            }
            if (!partial.renameTo(dst)) {
                throw new IOException("rename partial to destination failed: " + partial + " -> " + dst);
            }

            committed = true;
        } catch (OperationAbortedException aborted) {
            // Propagate abort to upper layers.
            // zh-CN: 将中止异常继续上抛给上层处理.
            throw aborted;
        } catch (Throwable t) {
            if (t instanceof IOException io) {
                throw io;
            }
            throw new IOException(t);
        } finally {
            if (!committed) {
                // Delete partial file on abort/error.
                // zh-CN: 中止/出错时删除临时文件.
                if (partial.exists() && !partial.delete()) {
                    Log.w(LOG_TAG, "copyFileWithProgressAtomic: failed to delete partial file: " + partial);
                }
            }
        }
    }

    // Move with progress; try rename first, fallback to copy+delete, supports current path callback.
    // zh-CN: 带进度的移动; 优先 rename, 失败则 copy+delete, 支持当前路径回调.
    private void moveFileOrDirWithProgress(
            @NonNull File src,
            @NonNull File dst,
            long totalBytes,
            @NonNull ProgressCallback progressCallback,
            @NonNull FileProgressCallback fileCallback,
            @NonNull OperationController controller
    ) throws IOException {
        controller.throwIfCancelled();

        fileCallback.onFile(dst);

        if (src.renameTo(dst)) {
            return;
        }

        copyFileOrDirWithProgress(src, dst, totalBytes, progressCallback, fileCallback, controller);

        controller.throwIfCancelled();

        boolean deleted = deleteRecursivelyWithProgress(src, null, controller);
        if (!deleted && src.exists()) {
            throw new IOException("Delete after copy failed: " + src);
        }
    }

    // Delete file or directory recursively with progress; supports cancellation and optional current-path callback.
    // zh-CN: 带进度的递归删除; 支持中止, 并支持可选的当前路径回调.
    private boolean deleteRecursivelyWithProgress(@NonNull File file, @Nullable FileProgressCallback fileCallback, @NonNull OperationController controller) {
        controller.throwIfCancelled();

        if (fileCallback != null) {
            fileCallback.onFile(file);
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    controller.throwIfCancelled();
                    if (!deleteRecursivelyWithProgress(child, fileCallback, controller)) {
                        return false;
                    }
                }
            }
        }
        controller.throwIfCancelled();
        return file.delete();
    }

    private void notifyFileRemovedAtItsParent(boolean isProject, boolean isDir, @NonNull ScriptFile scriptFile) {
        ExplorerPage parent = new ExplorerDirPage(new ScriptFile(new File(scriptFile.getPath()).getParent()), null);
        runOnMain(() -> {
            if (isProject) {
                mExplorer.notifyItemRemoved(new ExplorerProjectPage(scriptFile, parent));
            } else if (isDir) {
                mExplorer.notifyItemRemoved(new ExplorerDirPage(scriptFile, parent));
            } else {
                mExplorer.notifyItemRemoved(new ExplorerFileItem(scriptFile, parent));
            }
        });
    }

    private void notifyFileCreatedAtItsParent(boolean isProject, boolean isDir, @NonNull ScriptFile scriptFile) {
        ExplorerPage parent = new ExplorerDirPage(new ScriptFile(new File(scriptFile.getPath()).getParent()), null);
        runOnMain(() -> {
            if (isProject) {
                mExplorer.notifyItemCreated(new ExplorerProjectPage(scriptFile, parent));
            } else if (isDir) {
                mExplorer.notifyItemCreated(new ExplorerDirPage(scriptFile, parent));
            } else {
                mExplorer.notifyItemCreated(new ExplorerFileItem(scriptFile, parent));
            }
        });
    }

    private Observable<Boolean> confirmOverwriteIfNeeded(@NonNull File dest) {
        if (!dest.exists()) {
            return Observable.just(true);
        }
        int confirmOverwriteRes = dest.isDirectory()
                ? R.string.confirm_overwrite_directory
                : R.string.confirm_overwrite_file;
        return RxDialogs.confirm(mContext, confirmOverwriteRes, R.color.dialog_button_caution)
                .flatMap(yes -> {
                    if (!yes) {
                        return Observable.empty();
                    }
                    return Observable.fromCallable(() -> {
                        boolean deleted;
                        if (dest.isDirectory()) {
                            deleted = PFiles.deleteRecursively(dest);
                        } else {
                            deleted = dest.delete();
                        }
                        if (!deleted && dest.exists()) {
                            Log.e(LOG_TAG, "confirmOverwriteIfNeeded: delete failed: " + dest);
                            throw new IOException("Delete failed: " + dest);
                        }
                        return true;
                    }).subscribeOn(Schedulers.io());
                });
    }

    private void notifyFileCreated(ScriptFile scriptFile) {
        runOnMain(() -> {
            if (scriptFile.isDirectory()) {
                mExplorer.notifyItemCreated(new ExplorerDirPage(scriptFile, mExplorerPage));
            } else {
                mExplorer.notifyItemCreated(new ExplorerFileItem(scriptFile, mExplorerPage));
            }
        });
    }

    private void notifyFileChanged(ExplorerFileItem oldItem, ExplorerFileItem newItem) {
        runOnMain(() -> mExplorer.notifyItemChanged(oldItem, newItem));
    }

    private void notifyFileRemoved(boolean isProject, boolean isDir, ScriptFile scriptFile) {
        runOnMain(() -> {
            if (isProject) {
                mExplorer.notifyItemRemoved(new ExplorerProjectPage(scriptFile, mExplorerPage));
            } else if (isDir) {
                mExplorer.notifyItemRemoved(new ExplorerDirPage(scriptFile, mExplorerPage));
            } else {
                mExplorer.notifyItemRemoved(new ExplorerFileItem(scriptFile, mExplorerPage));
            }
        });
    }

    public void createShortcut(ScriptFile file) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(mContext)) {
            ShortcutUtils.showPinShortcutNotSupportedDialog(mContext);
            return;
        }
        Intent intent = new Intent(mContext, ShortcutCreateActivity.class)
                .putExtra(ShortcutCreateActivity.EXTRA_FILE, file);
        IntentUtils.startSafely(intent, mContext);
    }

    public void delete(final ScriptFile scriptFile) {
        DialogUtils.buildAndShowAdaptive(() -> {
            MaterialDialog.Builder builder = new MaterialDialog.Builder(mContext)
                    .title(R.string.text_choose_delete_strategy)
                    .content(scriptFile.getName())
                    .items(
                            mContext.getString(R.string.item_move_to_trash),
                            mContext.getString(R.string.item_delete_permanently)
                    )
                    .itemsCallbackSingleChoice(0, (dialog, itemView, which, text) -> {
                        dialog.dismiss();

                        if (which == 0) {
                            moveToTrashWithProgress(scriptFile);
                        } else if (which == 1) {
                            deleteWithoutConfirm(scriptFile);
                        }

                        return true;
                    })
                    .negativeText(R.string.dialog_button_cancel)
                    .positiveText(R.string.dialog_button_confirm)
                    .positiveColorRes(R.color.dialog_button_caution);
            DialogUtils.choiceWidgetThemeColor(builder);
            return builder.build();
        });
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
        DialogUtils.showAdaptive(new MaterialDialog.Builder(mContext)
                .title(mContext.getString(R.string.text_prompt))
                .content(content)
                .negativeText(R.string.text_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_warn)
                .onPositive((dialog, which) -> setAsWorkingDirNow(scriptFile))
                .build());
    }

    private void moveToTrashWithProgress(final ScriptFile scriptFile) {
        boolean isDir = scriptFile.isDirectory();
        int titleRes = isDir ? R.string.text_move_folder_to_trash : R.string.text_move_file_to_trash;

        Observable.fromCallable(() -> {
                    OperationController controller = new OperationController();
                    ProgressDialogSession session = new ProgressDialogSession(controller);

                    try {
                        MaterialDialog.Builder builder = new MaterialDialog.Builder(mContext)
                                .title(titleRes)
                                .content(R.string.text_moving_to_trash)
                                .negativeText(R.string.dialog_button_abort)
                                .negativeColorRes(R.color.dialog_button_caution)
                                .onNegative((dialog, which) -> {
                                    controller.cancel();
                                })
                                .progress(false, DialogUtils.PROGRESS_MAX_LARGE, true)
                                .cancelable(false)
                                .canceledOnTouchOutside(false);

                        session.scheduleShow(builder);

                        controller.throwIfCancelled();

                        // Move into trash (blob + db) then notify explorer.
                        // zh-CN: 移入回收站 (blob + db), 然后通知资源管理器.
                        new TrashRepository(mContext.getApplicationContext())
                                .moveToTrashWithProgress(
                                        scriptFile.getPath(),
                                        (processed, total) -> {
                                            controller.throwIfCancelled();
                                            if (total > 0) {
                                                session.setProgressThrottled(processed, total);
                                            }
                                        },
                                        (currentFile) -> {
                                            controller.throwIfCancelled();
                                            session.setContentThrottled(toContentList(scriptFile, currentFile));
                                        },
                                        controller
                                );

                        return 1;
                    } catch (OperationAbortedException aborted) {
                        return -1;
                    } finally {
                        session.dismissSafely();
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    switch (result) {
                        case 1 -> {
                            showMessage(R.string.text_moved_to_trash);

                            // Refresh explorer reliably for both file and directory.
                            // zh-CN: 对文件与目录均使用更可靠的刷新方式.
                            notifyChildrenChangedAtItsParent(scriptFile);

                            // Keep existing item removed notification as best-effort.
                            // zh-CN: 保留原有的 itemRemoved 通知, 作为尽力而为的补充.
                            notifyFileRemoved(ProjectConfig.isProject(scriptFile), scriptFile.isDirectory(), scriptFile);
                        }
                        case -1 -> showMessage(R.string.text_operation_aborted);
                        default -> showMessage(R.string.text_failed_to_delete);
                    }
                }, e -> {
                    e.printStackTrace();
                    showMessage(R.string.text_failed_to_delete);

                    String msg = e.getClass().getSimpleName();
                    if (e.getMessage() != null && !e.getMessage().isBlank()) {
                        msg = msg + ": " + e.getMessage();
                    }
                    DialogUtils.buildAndShowAdaptive(new MaterialDialog.Builder(mContext)
                            .title(R.string.text_move_to_trash_failed)
                            .content(msg)
                            .positiveText(R.string.dialog_button_dismiss)
                            .positiveColorRes(R.color.dialog_button_default)
                            .cancelable(false)::build);
                });
    }

    public void deleteWithoutConfirm(final ScriptFile scriptFile) {
        boolean isDir = scriptFile.isDirectory();
        boolean isProject = ProjectConfig.isProject(scriptFile);

        int titleRes = isDir ? R.string.text_delete_folder : R.string.text_delete_file;

        Observable.fromCallable(() -> {
                    OperationController controller = new OperationController();
                    ProgressDialogSession session = new ProgressDialogSession(controller);

                    try {
                        // For delete, indeterminate progress is acceptable and consistent.
                        // zh-CN: 删除操作使用不确定进度条即可, 行为更稳定.
                        MaterialDialog.Builder builder = new MaterialDialog.Builder(mContext)
                                .title(titleRes)
                                .content(mContext.getString(R.string.text_deleting) + mContext.getString(R.string.text_half_ellipsis))
                                .negativeText(R.string.dialog_button_abort)
                                .negativeColorRes(R.color.dialog_button_caution)
                                .onNegative((dialog, which) -> {
                                    controller.cancel();
                                })
                                .progress(true, 0)
                                .progressIndeterminateStyle(true)
                                .cancelable(false)
                                .canceledOnTouchOutside(false);

                        session.scheduleShow(builder);

                        boolean deleted = deleteRecursivelyWithProgress(
                                scriptFile,
                                currentFile -> session.setContentThrottled(toContentList(scriptFile, currentFile)),
                                controller
                        );

                        return deleted ? 1 : 0;
                    } catch (OperationAbortedException aborted) {
                        return -1;
                    } finally {
                        session.dismissSafely();
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(deleted -> {
                    switch (deleted) {
                        case 1 -> {
                            showMessage(R.string.text_already_deleted);
                            notifyFileRemoved(isProject, isDir, scriptFile);

                            if (HistoryPrefs.deletePermanentlyAlsoClearHistory()) {
                                var scriptFilePath = scriptFile.getPath();
                                if (scriptFilePath.startsWith(INTERNAL_STORAGE_ROOT)) {
                                    Schedulers.io().scheduleDirect(() -> {
                                        try {
                                            new HistoryRepository(mContext.getApplicationContext())
                                                    .clearHistoryForPath(scriptFilePath);
                                        } catch (Throwable ignored) {
                                            /* Ignored. */
                                        }
                                    });
                                }
                            }
                        }
                        case 0 -> showMessage(R.string.text_failed_to_delete);
                        case -1 -> showMessage(R.string.text_operation_aborted);
                    }
                });
    }

    public void setAsWorkingDirNow(final ScriptFile scriptFile) {
        WorkingDirectoryUtils.setPath(scriptFile.getPath());
        WorkingDirectoryUtils.addIntoHistory(scriptFile.getPath());
        showMessage(R.string.text_done);
    }

    public Observable<ScriptFile> download(String url) {
        Log.i(LOG_TAG, "dir = " + WorkingDirectoryUtils.getPath() + ", sdcard = " + Environment.getExternalStorageDirectory() + ", url = " + url);
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
                                if (!yes) {
                                    return Observable.empty();
                                }
                                if (!new File(savePath).delete()) {
                                    Log.e(LOG_TAG, "download: delete failed");
                                }
                                return Observable.just(savePath);
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
        DialogUtils.showAdaptive(new FileChooserDialogBuilder(mContext)
                .dir(EnvironmentUtils.getExternalStoragePath())
                .justScriptFile()
                .singleChoice(file -> importFile(file.getPath()).subscribe())
                .title(R.string.text_select_file_to_import)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .build());
    }

    public void timedTask(ScriptFile scriptFile) {
        Intent intent = new Intent(mContext, TimedTaskSettingActivity.class)
                .putExtra(ScriptIntents.EXTRA_KEY_PATH, scriptFile.getPath());
        IntentUtils.startSafely(intent, mContext);
    }

    private List<String> toContentList(File src, File current) {
        return List.of(
                mContext.getString(R.string.text_property_colon_value, mContext.getString(R.string.text_directory), getBriefDirectoryName(src, false)),
                mContext.getString(R.string.text_property_colon_value, mContext.getString(R.string.text_name), current.getName())
        );
    }

    private List<String> toContentList(File src, File dst, File current) {
        return List.of(
                mContext.getString(R.string.text_property_colon_value, mContext.getString(R.string.text_source), getBriefDirectoryName(src, true)),
                mContext.getString(R.string.text_property_colon_value, mContext.getString(R.string.text_destination), getBriefDirectoryName(dst, true)),
                mContext.getString(R.string.text_property_colon_value, mContext.getString(R.string.text_name), current.getName())
        );
    }

    @NonNull
    private String getBriefDirectoryName(@NonNull File file, boolean showDirParent) {
        try {
            if (file.isDirectory()) {
                if (!showDirParent) {
                    String fullPath = file.getAbsolutePath();
                    if (isInternalStorageRoot(fullPath)) {
                        return mContext.getString(R.string.term_internal_strorage);
                    }
                    return file.getName();
                }

                File parent = file.getParentFile();
                if (parent == null) {
                    return "/" + file.getName();
                }

                String parentPath = parent.getAbsolutePath();
                if (isInternalStorageRoot(parentPath)) {
                    return mContext.getString(R.string.term_internal_strorage) + "/" + file.getName();
                }

                return parent.getName() + "/" + file.getName();
            }

            File parent = file.getParentFile();
            if (parent == null) {
                return "/";
            }

            String parentPath = parent.getAbsolutePath();
            if (isInternalStorageRoot(parentPath)) {
                return mContext.getString(R.string.term_internal_strorage);
            }

            return parent.getName();
        } catch (Exception e) {
            return mContext.getString(R.string.text_unknown);
        }
    }

    private boolean isInternalStorageRoot(String absPath) {
        if (absPath == null) return false;
        String p = absPath;
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return INTERNAL_STORAGE_ROOT.equals(p);
    }

    private class InputCallback implements MaterialDialog.InputCallback {

        private final String mExcluded;
        private final String mExtension;
        private boolean mIsFirstTextChanged = true;

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

    public interface FileProgressCallback {
        void onFile(@NonNull File currentFile);
    }

    public interface ProgressCallback {
        void onProgress(long processed, long total);
    }
}