package org.autojs.autojs.ui.edit.toolbar;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.FragmentSearchToolbarBinding;

import java.util.Arrays;
import java.util.List;

public class SearchToolbarFragment extends ToolbarFragment {

    public static final String ARGUMENT_SHOW_REPLACE_ITEM = "show_replace_item";

    private FragmentSearchToolbarBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchToolbarBinding.inflate(inflater, container, false);
        return binding.getRoot();
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
