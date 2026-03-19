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

    @Override
    protected FragmentNormalToolbarBinding createBinding(@NonNull @NotNull LayoutInflater inflater, @Nullable @org.jetbrains.annotations.Nullable ViewGroup container) {
        return FragmentNormalToolbarBinding.inflate(inflater, container, false);
    }

    @Override
    public List<Integer> getMenuItemIds() {
        return Arrays.asList(R.id.run, R.id.undo, R.id.redo, R.id.save);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable android.os.Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        applyRunButtonState();
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
