package org.autojs.autojs.ui.explorer;

import android.view.View;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;

import org.autojs.autojs.ui.viewmodel.ExplorerItemList;
import org.autojs.autojs.ui.widget.BindableViewHolder;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.ExplorerCategoryBinding;

class ExplorerCategoryViewHolder extends BindableViewHolder<Boolean> {

    @NonNull
    public final ExplorerCategoryBinding binding;

    private final ExplorerView explorerView;

    private boolean mIsDir;

    ExplorerCategoryViewHolder(ExplorerView explorerView, View itemView) {
        super(itemView);
        this.explorerView = explorerView;
        binding = ExplorerCategoryBinding.bind(itemView);
        setOnClickListeners();
    }

    private void setOnClickListeners() {
        binding.sortOrder.setOnClickListener(v -> {
            if (mIsDir) {
                explorerView.sort(explorerView.explorerItemList.getDirSortType(), mIsDir, !explorerView.explorerItemList.isDirSortedAscending());
                setDirOrderIconWithCurrentState();
            } else {
                explorerView.sort(explorerView.explorerItemList.getFileSortType(), mIsDir, !explorerView.explorerItemList.isFileSortedAscending());
                setFileOrderIconWithCurrentState();
            }
        });
        binding.sortType.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(explorerView.getContext(), binding.sortType);
            popupMenu.inflate(R.menu.menu_sort_options);

            explorerView.isDirSortMenuShowing = mIsDir;

            int currentSortType = mIsDir ? explorerView.explorerItemList.getDirSortType() : explorerView.explorerItemList.getFileSortType();
            switch (currentSortType) {
                case ExplorerItemList.SORT_TYPE_DATE -> popupMenu.getMenu().findItem(R.id.action_sort_by_date).setChecked(true);
                case ExplorerItemList.SORT_TYPE_SIZE -> popupMenu.getMenu().findItem(R.id.action_sort_by_size).setChecked(true);
                case ExplorerItemList.SORT_TYPE_TYPE -> popupMenu.getMenu().findItem(R.id.action_sort_by_type).setChecked(true);
                default -> popupMenu.getMenu().findItem(R.id.action_sort_by_name).setChecked(true);
            }

            popupMenu.setOnMenuItemClickListener(item -> {
                item.setChecked(true);
                int itemId = item.getItemId();
                if (itemId == R.id.action_sort_by_name) {
                    explorerView.sort(ExplorerItemList.SORT_TYPE_NAME, explorerView.isDirSortMenuShowing, true);
                } else if (itemId == R.id.action_sort_by_date) {
                    explorerView.sort(ExplorerItemList.SORT_TYPE_DATE, explorerView.isDirSortMenuShowing, false);
                } else if (itemId == R.id.action_sort_by_size) {
                    explorerView.sort(ExplorerItemList.SORT_TYPE_SIZE, explorerView.isDirSortMenuShowing, false);
                } else if (itemId == R.id.action_sort_by_type) {
                    explorerView.sort(ExplorerItemList.SORT_TYPE_TYPE, explorerView.isDirSortMenuShowing, true);
                } else {
                    return false;
                }
                return true;
            });
            popupMenu.show();
        });
        binding.goUp.setOnClickListener(v -> {
            if (explorerView.canGoUp()) {
                explorerView.goUp();
            }
        });
        binding.titleContainer.setOnClickListener(v -> {
            if (mIsDir) {
                explorerView.currentPageState.dirsCollapsed = !explorerView.currentPageState.dirsCollapsed;
            } else {
                explorerView.currentPageState.filesCollapsed = !explorerView.currentPageState.filesCollapsed;
            }
            explorerView.notifyDataSetChanged();
        });
    }

    @Override
    public void bind(Boolean isDirCategory, int position) {
        binding.title.setText(isDirCategory ? R.string.text_directory : R.string.text_file);
        mIsDir = isDirCategory;
        if (isDirCategory && explorerView.canGoUp()) {
            binding.goUp.setVisibility(View.VISIBLE);
        } else {
            binding.goUp.setVisibility(View.GONE);
        }
        if (isDirCategory) {
            binding.arrowIcon.setRotation(explorerView.currentPageState.dirsCollapsed ? -90 : 0);
            setDirOrderIconWithCurrentState();
        } else {
            binding.arrowIcon.setRotation(explorerView.currentPageState.filesCollapsed ? -90 : 0);
            setFileOrderIconWithCurrentState();
        }
    }

    private void setFileOrderIconWithCurrentState() {
        binding.sortOrder.setImageResource(explorerView.explorerItemList.isFileSortedAscending() ?
                R.drawable.ic_ascending_order : R.drawable.ic_descending_order);
    }

    private void setDirOrderIconWithCurrentState() {
        binding.sortOrder.setImageResource(explorerView.explorerItemList.isDirSortedAscending() ?
                R.drawable.ic_ascending_order : R.drawable.ic_descending_order);
    }

}
