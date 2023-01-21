package org.autojs.autojs.core.ui.widget;

import android.content.Context;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.autojs.autojs.runtime.ScriptRuntime;

/**
 * Created by Stardust on 2018/3/30.
 */
public class JsGridView extends JsListView {

    public JsGridView(Context context, ScriptRuntime scriptRuntime) {
        super(context, scriptRuntime);
    }

    @Override
    protected void init() {
        super.init();
        setLayoutManager(new GridLayoutManager(getContext(), 1){
            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                try {
                    super.onLayoutChildren(recycler, state);
                } catch (IndexOutOfBoundsException e) {
                    getScriptRuntime().console.error(e);
                }
            }
        });
    }
}
