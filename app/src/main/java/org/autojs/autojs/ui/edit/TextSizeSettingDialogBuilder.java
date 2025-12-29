package org.autojs.autojs.ui.edit;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.afollestad.materialdialogs.MaterialDialog;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.DialogTextSizeSettingsBinding;

/**
 * Created by Stardust on Feb 24, 2018.
 * Modified by SuperMonster003 as of Dec 29, 2025.
 */
public class TextSizeSettingDialogBuilder extends MaterialDialog.Builder implements SeekBar.OnSeekBarChangeListener {

    public interface PositiveCallback {

        void onPositive(int value);
    }

    private final int mMinTextSize;
    private final int mMaxTextSize;

    private final SeekBar mSeekBar;
    private final TextView mPreviewText;

    private int mTextSize;
    private MaterialDialog mMaterialDialog;

    public TextSizeSettingDialogBuilder(@NonNull Context context) {
        super(context);

        DialogTextSizeSettingsBinding binding = DialogTextSizeSettingsBinding.inflate(LayoutInflater.from(context));

        mSeekBar = binding.seekBar;
        mPreviewText = binding.previewText;

        mMinTextSize = context.getResources().getInteger(R.integer.editor_text_size_min_value);
        mMaxTextSize = context.getResources().getInteger(R.integer.editor_text_size_max_value);

        LinearLayout view = binding.getRoot();
        customView(view, false);
        title(R.string.text_text_size);

        mSeekBar.setOnSeekBarChangeListener(this);
        mSeekBar.setMax(mMaxTextSize - mMinTextSize);

        binding.textSizeMinValue.setText(String.valueOf(mMinTextSize));
        binding.textSizeMaxValue.setText(String.valueOf(mMaxTextSize));

        autoDismiss(false);

        neutralText(R.string.dialog_button_use_default);
        neutralColorRes(R.color.dialog_button_reset);
        onNeutral((dialog, which) -> initialValue(getContext().getResources().getInteger(R.integer.editor_text_size_default_value)));
        negativeText(R.string.text_cancel);
        negativeColorRes(R.color.dialog_button_default);
        onNegative((dialog, which) -> dialog.dismiss());
        positiveText(R.string.dialog_button_confirm);
        positiveColorRes(R.color.dialog_button_attraction);
        onPositive((dialog, which) -> dialog.dismiss());
    }

    private void setTextSize(int textSize) {
        mTextSize = textSize;
        String title = getContext().getString(R.string.text_text_size_current_value, textSize);
        if (mMaterialDialog != null) {
            mMaterialDialog.setTitle(title);
        } else {
            title(title);
        }
        mPreviewText.setTextSize(textSize);
    }

    public TextSizeSettingDialogBuilder initialValue(int value) {
        mSeekBar.setProgress(value - mMinTextSize);
        return this;
    }

    public TextSizeSettingDialogBuilder callback(PositiveCallback callback) {
        onPositive((dialog, which) -> {
            callback.onPositive(mTextSize);
            dialog.dismiss();
        });
        return this;
    }

    @Override
    public MaterialDialog build() {
        mMaterialDialog = super.build();
        return mMaterialDialog;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        setTextSize(progress + mMinTextSize);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        /* Ignored. */
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        /* Ignored. */
    }

}
