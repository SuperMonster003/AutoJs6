package org.autojs.autojs.ui.edit.toolbar;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.FragmentSearchToolbarBinding;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class SearchToolbarFragment extends ToolbarFragment<FragmentSearchToolbarBinding> {

    public static final String ARGUMENT_SHOW_REPLACE_ITEM = "show_replace_item";

    @Override
    protected FragmentSearchToolbarBinding createBinding(@NonNull @NotNull LayoutInflater inflater, @Nullable @org.jetbrains.annotations.Nullable ViewGroup container) {
        return FragmentSearchToolbarBinding.inflate(inflater, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        boolean showReplaceItem = false;
        if (getArguments() != null) {
            showReplaceItem = getArguments().getBoolean(ARGUMENT_SHOW_REPLACE_ITEM, false);
        }
        view.findViewById(R.id.replace).setVisibility(showReplaceItem ? View.VISIBLE : View.GONE);
    }

    @Override
    public List<Integer> getMenuItemIds() {
        return Arrays.asList(R.id.replace, R.id.find_next, R.id.find_prev, R.id.cancel_search);
    }

}
