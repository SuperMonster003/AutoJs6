package org.autojs.autojs.ui.filechooser;

import android.view.View;

import androidx.annotation.NonNull;

import org.autojs.autojs.model.explorer.ExplorerPage;
import org.autojs.autojs.ui.explorer.ExplorerViewHelper;
import org.autojs.autojs.ui.widget.BindableViewHolder;
import org.autojs.autojs6.databinding.FileChooseListDirectoryBinding;

class ExplorerPageViewHolder extends BindableViewHolder<ExplorerPage> {

    private final FileChooseListView fileChooseListView;
    @NonNull
    private final FileChooseListDirectoryBinding binding;

    private ExplorerPage mExplorerPage;

    ExplorerPageViewHolder(FileChooseListView fileChooseListView, View itemView) {
        super(itemView);
        this.fileChooseListView = fileChooseListView;
        binding = FileChooseListDirectoryBinding.bind(itemView);
    }

    @Override
    public void bind(ExplorerPage data, int position) {
        mExplorerPage = data;
        binding.name.setText(ExplorerViewHelper.getDisplayName(fileChooseListView.getContext(), data));
        binding.icon.setImageResource(ExplorerViewHelper.getIcon(data));
        if (fileChooseListView.getCanChooseDir()) {
            binding.checkbox.setVisibility(View.VISIBLE);
            binding.checkbox.setChecked(fileChooseListView.getSelectedFiles().containsKey(data.toScriptFile()), false);
        } else {
            binding.checkbox.setVisibility(View.GONE);
        }
        binding.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (binding.checkbox.isChecked()) {
                fileChooseListView.check(mExplorerPage.toScriptFile(), getAdapterPosition());
            } else {
                fileChooseListView.getSelectedFiles().remove(mExplorerPage.toScriptFile());
            }
        });
        binding.item.setOnClickListener(v -> fileChooseListView.enterDirectChildPage(mExplorerPage));
    }

}
