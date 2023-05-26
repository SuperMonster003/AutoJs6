package org.autojs.autojs.ui.filechooser;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.pio.PFile;
import org.autojs.autojs.ui.explorer.ExplorerView;
import org.autojs.autojs.ui.widget.BindableViewHolder;
import org.autojs.autojs6.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Stardust on 2017/10/19.
 */
public class FileChooseListView extends ExplorerView {

    private int mMaxChoice = 1;
    private final LinkedHashMap<PFile, Integer> mSelectedFiles = new LinkedHashMap<>();
    private boolean mCanChooseDir = false;

    public FileChooseListView(Context context) {
        super(context);
        init();
    }

    public FileChooseListView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void setMaxChoice(int maxChoice) {
        mMaxChoice = maxChoice;
    }

    public void setCanChooseDir(boolean canChooseDir) {
        mCanChooseDir = canChooseDir;
    }

    public boolean getCanChooseDir() {
        return mCanChooseDir;
    }

    public LinkedHashMap<PFile, Integer> getSelectedFiles() {
        return mSelectedFiles;
    }

    public List<PFile> getSelectedFilesList() {
        ArrayList<PFile> list = new ArrayList<>(mSelectedFiles.size());
        for (Map.Entry<PFile, Integer> entry : mSelectedFiles.entrySet()) {
            list.add(entry.getKey());
        }
        return list;
    }

    private void init() {
        SimpleItemAnimator animator = (SimpleItemAnimator) getExplorerItemListView().getItemAnimator();
        if (animator != null) {
            animator.setSupportsChangeAnimations(false);
        }
    }

    @Override
    protected BindableViewHolder<?> onCreateViewHolder(LayoutInflater inflater, ViewGroup parent, int viewType) {
        return switch (viewType) {
            case VIEW_TYPE_ITEM -> new ExplorerItemViewHolder(this, inflater.inflate(R.layout.file_choose_list_file, parent, false));
            case VIEW_TYPE_PAGE -> new ExplorerPageViewHolder(this, inflater.inflate(R.layout.file_choose_list_directory, parent, false));
            default -> super.onCreateViewHolder(inflater, parent, viewType);
        };
    }

    void check(ScriptFile file, int position) {
        if (mSelectedFiles.size() == mMaxChoice) {
            Map.Entry<PFile, Integer> itemToUncheck = mSelectedFiles.entrySet().iterator().next();
            int positionOfItemToUncheck = itemToUncheck.getValue();
            mSelectedFiles.remove(itemToUncheck.getKey());
            RecyclerView.Adapter<?> adapter = getExplorerItemListView().getAdapter();
            if (adapter != null) {
                adapter.notifyItemChanged(positionOfItemToUncheck);
            }
        }
        mSelectedFiles.put(file, position);
    }


}
