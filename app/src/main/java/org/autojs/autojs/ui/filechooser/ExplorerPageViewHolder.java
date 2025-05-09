package org.autojs.autojs.ui.filechooser;

import android.content.Context;
import android.view.View;
import androidx.annotation.NonNull;
import com.afollestad.materialdialogs.util.DialogUtils;
import org.autojs.autojs.model.explorer.ExplorerPage;
import org.autojs.autojs.theme.ThemeColorHelper;
import org.autojs.autojs.ui.explorer.ExplorerViewHelper;
import org.autojs.autojs.ui.widget.BindableViewHolder;
import org.autojs.autojs6.databinding.FileChooseListDirectoryBinding;

class ExplorerPageViewHolder extends BindableViewHolder<Object> {

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
    public void bind(Object item, int position) {
        if (!(item instanceof ExplorerPage explorerPage)) return;

        Context context = fileChooseListView.getContext();

        mExplorerPage = explorerPage;
        binding.name.setText(ExplorerViewHelper.getDisplayName(context, explorerPage));
        binding.icon.setImageResource(ExplorerViewHelper.getIconRes(explorerPage));
        if (fileChooseListView.getCanChooseDir()) {
            binding.checkbox.setVisibility(View.VISIBLE);
            binding.checkbox.setChecked(fileChooseListView.getSelectedFiles().containsKey(explorerPage.toScriptFile()), false);
            var defaultMaterialDialogBackgroundColor = DialogUtils.resolveColor(
                    context,
                    com.afollestad.materialdialogs.R.attr.md_background_color,
                    DialogUtils.resolveColor(context, android.R.attr.colorBackgroundFloating));
            ThemeColorHelper.setThemeColorPrimary(binding.checkbox, defaultMaterialDialogBackgroundColor);
        } else {
            binding.checkbox.setVisibility(View.GONE);
        }
        binding.checkbox.setOnCheckedChangeListener((listener, isChecked) -> {
            if (binding.checkbox.isChecked()) {
                fileChooseListView.check(mExplorerPage.toScriptFile(), getAdapterPosition());
            } else {
                fileChooseListView.getSelectedFiles().remove(mExplorerPage.toScriptFile());
            }
        });
        binding.item.setOnClickListener(view -> fileChooseListView.enterDirectChildPage(mExplorerPage));
    }

}
