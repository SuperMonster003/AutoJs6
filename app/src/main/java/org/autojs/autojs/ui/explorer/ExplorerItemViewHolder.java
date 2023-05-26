package org.autojs.autojs.ui.explorer;

import static org.autojs.autojs.model.explorer.WorkspaceFileProvider.SAMPLE_PATH;

import android.annotation.SuppressLint;
import android.view.Menu;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

import org.autojs.autojs.model.explorer.ExplorerFileItem;
import org.autojs.autojs.model.explorer.ExplorerItem;
import org.autojs.autojs.model.explorer.Explorers;
import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.pio.PFile;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.ui.common.ScriptLoopDialog;
import org.autojs.autojs.ui.common.ScriptOperations;
import org.autojs.autojs.ui.project.BuildActivity;
import org.autojs.autojs.ui.widget.BindableViewHolder;
import org.autojs.autojs.ui.widget.FirstCharView;
import org.autojs.autojs.util.Observers;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.ExplorerFileBinding;
import org.autojs.autojs6.databinding.ExplorerFirstCharIconBinding;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;

class ExplorerItemViewHolder extends BindableViewHolder<ExplorerItem> {

    private final ExplorerView explorerView;

    private final TextView mName;
    private final TextView mFileDate;
    private final TextView mFileSize;
    private final View mOptions;
    private final View mEdit;
    private final View mRun;
    private final FirstCharView mFirstChar;

    private ExplorerItem mExplorerItem;

    ExplorerItemViewHolder(ExplorerView explorerView, View itemView) {
        super(itemView);
        this.explorerView = explorerView;

        ExplorerFileBinding explorerFileBinding = ExplorerFileBinding.bind(itemView);
        ExplorerFirstCharIconBinding firstCharIconBinding = ExplorerFirstCharIconBinding.bind(itemView);

        mName = explorerFileBinding.name;
        mFileDate = explorerFileBinding.scriptFileDate;
        mFileSize = explorerFileBinding.scriptFileSize;
        mFirstChar = firstCharIconBinding.firstChar;

        mRun = explorerFileBinding.run;
        mRun.setOnClickListener(v -> run());

        mEdit = explorerFileBinding.edit;
        mEdit.setOnClickListener(v -> edit());

        mOptions = explorerFileBinding.more;
        mOptions.setOnClickListener(v -> showOptionsMenu());

        explorerFileBinding.item.setOnClickListener(v -> onItemClick());
    }

    @Override
    public void bind(ExplorerItem item, int position) {
        mExplorerItem = item;
        setFirstChar();
        mName.setText(ExplorerViewHelper.getDisplayName(explorerView.getContext(), item));
        mFileDate.setText(PFile.getFullDateString(item.lastModified()));
        mFileSize.setText(PFiles.getHumanReadableSize(item.getSize()));
        mEdit.setVisibility(item.isEditable() ? View.VISIBLE : View.GONE);
        mRun.setVisibility(item.isExecutable() ? View.VISIBLE : View.GONE);
    }

    private void setFirstChar() {
        mFirstChar.setIconText(ExplorerViewHelper.getIconText(mExplorerItem));
        switch (mExplorerItem.getType()) {
            case JAVASCRIPT, AUTO -> mFirstChar
                    .setIconTextColorNightDay()
                    .setStrokeThemeColor()
                    .setFillThemeColor();

            // @Hint by SuperMonster003 on Aug 26, 2022.
            //  ! These default setters could be placed into its class as an initializer.
            //  ! Even though, code below is still necessary to avoid abnormal icon behaviours.
            default -> mFirstChar
                    .setIconTextColorDayNight()
                    .setStrokeColorDayNight()
                    .setFillTransparent();
        }
    }

    private void onItemClick() {
        if (explorerView.onItemClickListener != null) {
            explorerView.onItemClickListener.onItemClick(itemView, mExplorerItem);
        }
        explorerView.notifyItemOperated();
    }

    private void run() {
        Scripts.run(explorerView.getContext(), new ScriptFile(mExplorerItem.getPath()));
        explorerView.notifyItemOperated();
    }

    private void edit() {
        Scripts.edit(explorerView.getContext(), new ScriptFile(mExplorerItem.getPath()));
        explorerView.notifyItemOperated();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    private void showOptionsMenu() {
        explorerView.selectedItem = mExplorerItem;
        PopupMenu popupMenu = new PopupMenu(explorerView.getContext(), mOptions);
        popupMenu.inflate(R.menu.menu_script_options);
        Menu menu = popupMenu.getMenu();
        if (!mExplorerItem.isExecutable()) {
            menu.removeItem(R.id.run_repeatedly);
            menu.removeItem(R.id.more);
        }
        if (!mExplorerItem.canDelete()) {
            menu.removeItem(R.id.delete);
        }
        if (!mExplorerItem.canRename()) {
            menu.removeItem(R.id.rename);
        }
        String samplePath = new PFile(explorerView.getContext().getFilesDir(), SAMPLE_PATH).getPath();
        if (!(mExplorerItem.getPath().startsWith(samplePath))) {
            menu.removeItem(R.id.reset);
        }
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.rename) {
                new ScriptOperations(explorerView.getContext(), explorerView, explorerView.getCurrentPage())
                        .rename((ExplorerFileItem) explorerView.selectedItem)
                        .subscribe(Observers.emptyObserver());
            } else if (itemId == R.id.delete) {
                new ScriptOperations(explorerView.getContext(), explorerView, explorerView.getCurrentPage())
                        .delete(explorerView.selectedItem.toScriptFile());
            } else if (itemId == R.id.run_repeatedly) {
                new ScriptLoopDialog(explorerView.getContext(), explorerView.selectedItem.toScriptFile())
                        .show();
                explorerView.notifyItemOperated();
            } else if (itemId == R.id.create_shortcut) {
                new ScriptOperations(explorerView.getContext(), explorerView, explorerView.getCurrentPage())
                        .createShortcut(explorerView.selectedItem.toScriptFile());
            } else if (itemId == R.id.open_by_other_apps) {
                Scripts.openByOtherApps(explorerView.selectedItem.toScriptFile());
                explorerView.notifyItemOperated();
            } else if (itemId == R.id.send) {
                Scripts.send(explorerView.getContext(), explorerView.selectedItem.toScriptFile());
                explorerView.notifyItemOperated();
            } else if (itemId == R.id.timed_task) {
                new ScriptOperations(explorerView.getContext(), explorerView, explorerView.getCurrentPage())
                        .timedTask(explorerView.selectedItem.toScriptFile());
                explorerView.notifyItemOperated();
            } else if (itemId == R.id.action_build_apk) {
                BuildActivity.launch(explorerView.getContext(), explorerView.selectedItem.getPath());
                explorerView.notifyItemOperated();
            } else if (itemId == R.id.reset) {
                Observable<ScriptFile> o = Explorers.Providers.workspace()
                        .resetSample(explorerView.selectedItem.toScriptFile());
                if (o == null) {
                    explorerView.resetFailed();
                } else {
                    o.observeOn(AndroidSchedulers.mainThread())
                            .subscribe(file -> {
                                if (file.exists()) {
                                    explorerView.resetSucceeded();
                                } else {
                                    explorerView.resetFailed();
                                }
                            }, Observers.toastMessage());
                }
            } else {
                return false;
            }
            return true;
        });
        popupMenu.show();
    }
}
