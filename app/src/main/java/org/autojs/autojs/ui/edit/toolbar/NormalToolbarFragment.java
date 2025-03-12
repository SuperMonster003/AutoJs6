package org.autojs.autojs.ui.edit.toolbar;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.FragmentNormalToolbarBinding;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class NormalToolbarFragment extends ToolbarFragment<FragmentNormalToolbarBinding> {

    @Override
    protected FragmentNormalToolbarBinding createBinding(@NonNull @NotNull LayoutInflater inflater, @Nullable @org.jetbrains.annotations.Nullable ViewGroup container) {
        return FragmentNormalToolbarBinding.inflate(inflater, container, false);
    }

    @Override
    public List<Integer> getMenuItemIds() {
        return Arrays.asList(R.id.run, R.id.undo, R.id.redo, R.id.save);
    }

}
