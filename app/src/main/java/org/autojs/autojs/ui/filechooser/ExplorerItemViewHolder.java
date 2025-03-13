package org.autojs.autojs.ui.filechooser;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;

import androidx.annotation.NonNull;

import org.autojs.autojs.core.ui.widget.JsCheckBox;
import org.autojs.autojs.model.explorer.ExplorerItem;
import org.autojs.autojs.pio.PFile;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.theme.ThemeColorManagerCompat;
import org.autojs.autojs.ui.explorer.ExplorerViewHelper;
import org.autojs.autojs.ui.widget.BindableViewHolder;
import org.autojs.autojs.util.ColorUtils;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.ExplorerFirstCharIconBinding;
import org.autojs.autojs6.databinding.FileChooseListFileBinding;

class ExplorerItemViewHolder extends BindableViewHolder<Object> {

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
    public void bind(Object item, int position) {
        if (!(item instanceof ExplorerItem explorerItem)) return;
        mExplorerItem = explorerItem;

        Context context = fileChooseListView.getContext();
        JsCheckBox checkbox = listFileBinding.checkbox;

        int itemBackgroundDarkColor = context.getColor(R.color.item_background_dark);
        int themePrimaryColor = ThemeColorManagerCompat.getColorPrimary();

        int checkBoxCheckedThemeColorForContrast = ColorUtils.adjustColorForContrast(itemBackgroundDarkColor, themePrimaryColor, 1.9);
        int checkBoxUncheckedCheckedThemeColorForContrast = ColorUtils.adjustColorForContrast(itemBackgroundDarkColor, themePrimaryColor, 1.4);
        int itemIconThemeColorForContrast = ColorUtils.adjustColorForContrast(itemBackgroundDarkColor, themePrimaryColor, 1.15);

        checkbox.setButtonTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{checkBoxCheckedThemeColorForContrast, checkBoxUncheckedCheckedThemeColorForContrast}
        ));

        listFileBinding.name.setText(ExplorerViewHelper.getDisplayName(context, explorerItem));
        listFileBinding.item.setOnClickListener(view -> checkbox.toggle());
        checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (checkbox.isChecked()) {
                fileChooseListView.check(mExplorerItem.toScriptFile(), getAdapterPosition());
            } else {
                fileChooseListView.getSelectedFiles().remove(mExplorerItem.toScriptFile());
            }
        });
        listFileBinding.scriptFileSize.setText(PFiles.getHumanReadableSize(explorerItem.getSize()));
        listFileBinding.scriptFileDate.setText(PFile.getFullDateString(explorerItem.lastModified()));

        switch (explorerItem.getType()) {
            case JAVASCRIPT, AUTO -> firstCharIconBinding.firstChar
                    .setIconTextColorByThemeColorLuminance()
                    .setStrokeColor(itemIconThemeColorForContrast)
                    .setFillColor(itemIconThemeColorForContrast);
            default -> firstCharIconBinding.firstChar
                    .setIconTextColorDayNight()
                    .setStrokeColorDayNight()
                    .setFillTransparent();
        }

        firstCharIconBinding.firstChar.setIcon(ExplorerViewHelper.getIcon(explorerItem));

        checkbox.setChecked(fileChooseListView.getSelectedFiles().containsKey(mExplorerItem.toScriptFile()), false);
    }

}
