package org.autojs.autojs.ui.filechooser;

import android.view.View;

import androidx.annotation.NonNull;

import org.autojs.autojs.model.explorer.ExplorerItem;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.ui.explorer.ExplorerViewHelper;
import org.autojs.autojs.ui.widget.BindableViewHolder;
import org.autojs.autojs6.databinding.ExplorerFirstCharIconBinding;
import org.autojs.autojs6.databinding.FileChooseListFileBinding;

class ExplorerItemViewHolder extends BindableViewHolder<ExplorerItem> {

    private final FileChooseListView fileChooseListView;
    @NonNull
    private final ExplorerFirstCharIconBinding firstCharIconBinding;

    @NonNull
    private final FileChooseListFileBinding listFileBinding;

    private ExplorerItem mExplorerItem;

    ExplorerItemViewHolder(FileChooseListView fileChooseListView, View itemView) {
        super(itemView);
        this.fileChooseListView = fileChooseListView;
        firstCharIconBinding = ExplorerFirstCharIconBinding.bind(itemView);
        listFileBinding = FileChooseListFileBinding.bind(itemView);
    }

    @Override
    public void bind(ExplorerItem item, int position) {
        mExplorerItem = item;
        listFileBinding.name.setText(ExplorerViewHelper.getDisplayName(fileChooseListView.getContext(), item));
        listFileBinding.item.setOnClickListener(v -> listFileBinding.checkbox.toggle());
        listFileBinding.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listFileBinding.checkbox.isChecked()) {
                fileChooseListView.check(mExplorerItem.toScriptFile(), getAdapterPosition());
            } else {
                fileChooseListView.getSelectedFiles().remove(mExplorerItem.toScriptFile());
            }
        });
        listFileBinding.scriptFileSize.setText(PFiles.getHumanReadableSize(item.getSize()));

        switch (item.getType()) {
            case JAVASCRIPT, AUTO -> firstCharIconBinding.firstChar
                    .setIconTextColorNightDay()
                    .setStrokeThemeColor()
                    .setFillColorDayNight();
            default -> firstCharIconBinding.firstChar
                    .setIconTextColorDayNight()
                    .setStrokeColorDayNight()
                    .setFillTransparent();
        }
        firstCharIconBinding.firstChar.setIconText(ExplorerViewHelper.getIconText(item));

        listFileBinding.checkbox.setChecked(fileChooseListView.getSelectedFiles().containsKey(mExplorerItem.toScriptFile()), false);
    }

}
