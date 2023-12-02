package org.autojs.autojs.ui.explorer;

import android.view.Menu;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import org.autojs.autojs.model.explorer.ExplorerFileItem;
import org.autojs.autojs.model.explorer.ExplorerPage;
import org.autojs.autojs.model.explorer.ExplorerSamplePage;
import org.autojs.autojs.pio.PFile;
import org.autojs.autojs.ui.common.ScriptOperations;
import org.autojs.autojs.ui.project.BuildActivity;
import org.autojs.autojs.ui.widget.BindableViewHolder;
import org.autojs.autojs.util.Observers;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.ExplorerDirectoryBinding;

class ExplorerPageViewHolder extends BindableViewHolder<ExplorerPage> {

    private final ExplorerView explorerView;

    public TextView mName;
    public TextView mDirDate;
    public View mOptions;
    public ImageView mIcon;

    private ExplorerPage mExplorerPage;

    ExplorerPageViewHolder(ExplorerView explorerView, View itemView) {
        super(itemView);
        this.explorerView = explorerView;

        ExplorerDirectoryBinding binding = ExplorerDirectoryBinding.bind(itemView);

        mName = binding.name;
        mDirDate = binding.scriptDirDate;
        mIcon = binding.icon;

        mOptions = binding.more;
        mOptions.setOnClickListener(v -> showOptionsMenu());

        binding.item.setOnClickListener(v -> onItemClick());
    }

    @Override
    public void bind(ExplorerPage data, int position) {
        mName.setText(ExplorerViewHelper.getDisplayName(explorerView.getContext(), data));
        mDirDate.setText(PFile.getFullDateString(data.lastModified()));
        mIcon.setImageResource(ExplorerViewHelper.getIcon(data));
        mOptions.setVisibility(data instanceof ExplorerSamplePage ? View.GONE : View.VISIBLE);
        mExplorerPage = data;
    }

    private void onItemClick() {
        explorerView.enterDirectChildPage(mExplorerPage);
    }

    private void showOptionsMenu() {
        explorerView.selectedItem = mExplorerPage;
        PopupMenu popupMenu = new PopupMenu(explorerView.getContext(), mOptions);
        Menu menu = popupMenu.getMenu();
        popupMenu.inflate(R.menu.menu_dir_options);
        if (!mExplorerPage.canRename()) {
            menu.removeItem(R.id.action_rename);
        }
        if (!mExplorerPage.canDelete()) {
            menu.removeItem(R.id.action_delete);
        }
        if (!mExplorerPage.canSetAsWorkingDir()) {
            menu.removeItem(R.id.action_set_as_working_dir);
        }
        if (!mExplorerPage.canBuildApk()) {
            menu.removeItem(R.id.action_build_apk);
        }
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_rename) {
                new ScriptOperations(explorerView.getContext(), explorerView, explorerView.getCurrentPage())
                        .rename((ExplorerFileItem) explorerView.selectedItem)
                        .subscribe(Observers.emptyObserver());
            } else if (itemId == R.id.action_delete) {
                new ScriptOperations(explorerView.getContext(), explorerView, explorerView.getCurrentPage())
                        .delete(explorerView.selectedItem.toScriptFile());
            } else if (itemId == R.id.action_set_as_working_dir) {
                new ScriptOperations(explorerView.getContext(), explorerView, explorerView.getCurrentPage())
                        .setAsWorkingDir(explorerView.selectedItem.toScriptFile());
            } else if (itemId == R.id.action_build_apk) {
                BuildActivity.launch(explorerView.getContext(), explorerView.selectedItem.getPath());
            } else {
                return false;
            }
            return true;
        });
        popupMenu.show();
    }

}
