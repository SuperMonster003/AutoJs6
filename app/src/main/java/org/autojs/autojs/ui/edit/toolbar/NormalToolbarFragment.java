package org.autojs.autojs.ui.edit.toolbar;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.FragmentNormalToolbarBinding;

import java.util.Arrays;
import java.util.List;

public class NormalToolbarFragment extends ToolbarFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return FragmentNormalToolbarBinding.inflate(inflater, container, false).getRoot();
    }

    @Override
    public List<Integer> getMenuItemIds() {
        return Arrays.asList(R.id.run, R.id.undo, R.id.redo, R.id.save);
    }

}
