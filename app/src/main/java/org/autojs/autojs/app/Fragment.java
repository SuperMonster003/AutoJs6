package org.autojs.autojs.app;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.autojs.autojs.util.ViewUtils;

/**
 * Created by Stardust on Jan 30, 2017.
 */
public abstract class Fragment extends androidx.fragment.app.Fragment {

    private View mView;

    @NonNull
    public View getView() {
        return mView;
    }

    public <T extends View> T $(int id) {
        return ViewUtils.$(mView, id);
    }

    public View findViewById(int id) {
        return mView.findViewById(id);
    }

    public View getActivityContentView() {
        return getActivity().getWindow().getDecorView();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mView = createView(inflater, container, savedInstanceState);
        return mView;
    }

    @Nullable
    public abstract View createView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState);


}
