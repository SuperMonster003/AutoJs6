package org.autojs.autojs.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public abstract class BaseViewBindingFragment<T extends androidx.viewbinding.ViewBinding> extends Fragment {

    protected T binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = createBinding(inflater, container);
        return getRootView(binding);
    }

    protected abstract T createBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container);

    protected View getRootView(@NonNull T binding) {
        return binding.getRoot();
    }

}
