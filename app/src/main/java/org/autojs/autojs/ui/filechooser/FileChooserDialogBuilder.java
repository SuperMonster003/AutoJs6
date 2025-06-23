package org.autojs.autojs.ui.filechooser;

import android.content.Context;
import android.widget.CheckBox;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import com.afollestad.materialdialogs.MaterialDialog;
import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import org.autojs.autojs.app.DialogUtils;
import org.autojs.autojs.model.explorer.Explorer;
import org.autojs.autojs.model.explorer.ExplorerDirPage;
import org.autojs.autojs.model.explorer.ExplorerFileProvider;
import org.autojs.autojs.model.explorer.Explorers;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.pio.PFile;
import org.autojs.autojs.theme.ThemeColorHelper;
import org.autojs.autojs6.R;

import java.io.File;
import java.io.FileFilter;
import java.util.Collections;
import java.util.List;

/**
 * Created by Stardust on Oct 19, 2017.
 */
public class FileChooserDialogBuilder extends MaterialDialog.Builder {

    public interface SingleChoiceCallback {

        void onSelected(PFile file);
    }

    public interface MultiChoiceCallback {

        void onSelected(List<PFile> files);
    }

    private final FileChooseListView mFileChooseListView;

    private MultiChoiceCallback mCallback;
    private FileFilter mFileFilter;
    private String mRootDir;
    private String mInitialDir;

    public FileChooserDialogBuilder(@NonNull Context context) {
        super(context);
        mFileChooseListView = new FileChooseListView(context);
        mFileChooseListView.isProjectRecognitionEnabled = false;
        if (mFileChooseListView.findViewById(R.id.checkbox) instanceof CheckBox checkbox) {
            ThemeColorHelper.setThemeColorPrimary(checkbox, backgroundColor);
        }
        customView(mFileChooseListView, false);
        negativeText(R.string.dialog_button_cancel);
        negativeColorRes(R.color.dialog_button_default);
        positiveText(R.string.dialog_button_confirm);
        positiveColorRes(R.color.dialog_button_attraction);
        onPositive((dialog, which) -> notifySelected());
        cancelable(false);
    }

    private void notifySelected() {
        if (mCallback == null)
            return;
        List<PFile> selectedFiles = mFileChooseListView.getSelectedFilesList();
        if (selectedFiles.isEmpty()) {
            mCallback.onSelected(Collections.singletonList(mFileChooseListView.getCurrentDirectory()));
        } else {
            mCallback.onSelected(selectedFiles);
        }
    }

    @Override
    public MaterialDialog show() {
        return DialogUtils.adaptToExplorer(super.show(), mFileChooseListView);
    }

    public FileChooserDialogBuilder dir(String rootDir, String initialDir) {
        mRootDir = rootDir;
        mInitialDir = initialDir;
        return this;
    }

    public FileChooserDialogBuilder dir(String dir) {
        mRootDir = dir;
        return this;
    }

    public FileChooserDialogBuilder justScriptFile() {
        mFileFilter = Scripts.FILE_FILTER;
        return this;
    }

    public FileChooserDialogBuilder chooseDir() {
        mFileFilter = File::isDirectory;
        mFileChooseListView.setCanChooseDir(true);
        return this;
    }

    public FileChooserDialogBuilder setProjectRecognition(boolean b) {
        mFileChooseListView.isProjectRecognitionEnabled = b;
        return this;
    }

    public FileChooserDialogBuilder singleChoice(SingleChoiceCallback callback) {
        mFileChooseListView.setMaxChoice(1);
        mCallback = files -> callback.onSelected(files.get(0));
        return this;
    }

    public FileChooserDialogBuilder multiChoice(MultiChoiceCallback callback) {
        return multiChoice(Integer.MAX_VALUE, callback);
    }

    public FileChooserDialogBuilder multiChoice(int maxChoices, MultiChoiceCallback callback) {
        mFileChooseListView.setMaxChoice(maxChoices);
        mCallback = callback;
        return this;
    }

    public Observable<PFile> singleChoice() {
        PublishSubject<PFile> result = PublishSubject.create();
        singleChoice(file -> {
            result.onNext(file);
            result.onComplete();
        });
        show();
        return result;
    }

    @Override
    public FileChooserDialogBuilder title(@NonNull CharSequence title) {
        super.title(title);
        return this;
    }

    @Override
    public FileChooserDialogBuilder title(@StringRes int titleRes) {
        super.title(titleRes);
        return this;
    }

    @Override
    public MaterialDialog build() {
        ExplorerDirPage root = ExplorerDirPage.createRoot(mRootDir);
        Explorer explorer = mFileFilter == null
                ? Explorers.external()
                : new Explorer(new ExplorerFileProvider(mFileFilter), 0);
        if (mInitialDir == null) {
            mFileChooseListView.setExplorer(explorer, root);
        } else {
            mFileChooseListView.setExplorer(explorer, root, new ExplorerDirPage(mInitialDir, root));
        }
        return super.build();
    }
}
