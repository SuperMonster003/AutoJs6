package org.autojs.autojs.ui.edit.toolbar;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.autojs.autojs.ui.widget.ToolbarMenuItem;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.FragmentNormalToolbarBinding;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class NormalToolbarFragment extends ToolbarFragment<FragmentNormalToolbarBinding> {

    private boolean mRunButtonAsStop = false;
    private boolean mUiPreviewVisible = false;
    private boolean mUiPreviewOn = false;

    @Override
    protected FragmentNormalToolbarBinding createBinding(@NonNull @NotNull LayoutInflater inflater, @Nullable @org.jetbrains.annotations.Nullable ViewGroup container) {
        return FragmentNormalToolbarBinding.inflate(inflater, container, false);
    }

    @Override
    public List<Integer> getMenuItemIds() {
        return Arrays.asList(R.id.action_log, R.id.ui_preview, R.id.run, R.id.undo, R.id.redo, R.id.save);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable android.os.Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        applyRunButtonState();
        applyUiPreviewState();
    }

    /**
     * The layout preview toggle of "ui" scripts (3-Block Compose roadmap P11.10): shown only for them, its icon tells
     * whether the panel is on.
     */
    public void setUiPreviewState(boolean visible, boolean on) {
        mUiPreviewVisible = visible;
        mUiPreviewOn = on;
        applyUiPreviewState();
    }

    private void applyUiPreviewState() {
        View root = getView();
        if (root == null) {
            return;
        }
        ToolbarMenuItem item = root.findViewById(R.id.ui_preview);
        if (item == null) {
            return;
        }
        item.setVisibility(mUiPreviewVisible ? View.VISIBLE : View.GONE);
        item.setIconResource(mUiPreviewOn ? R.drawable.ic_editor_ui_preview_on : R.drawable.ic_editor_ui_preview_off);
        item.setSelected(mUiPreviewOn);
    }

    public void setRunButtonAsStop(boolean runButtonAsStop) {
        mRunButtonAsStop = runButtonAsStop;
        applyRunButtonState();
    }

    private void applyRunButtonState() {
        View root = getView();
        if (root == null) {
            return;
        }
        ToolbarMenuItem runItem = root.findViewById(R.id.run);
        if (runItem == null) {
            return;
        }
        if (mRunButtonAsStop) {
            runItem.setIconResource(R.drawable.ic_stop_button_fill);
            runItem.setTextResource(R.string.text_stop_simplified);
        } else {
            runItem.setIconResource(R.drawable.ic_play_arrow_white_48dp);
            runItem.setTextResource(R.string.text_run_simplified);
        }
    }
}
