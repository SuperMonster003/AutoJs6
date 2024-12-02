package org.autojs.autojs.ui.explorer;

import static org.autojs.autojs.util.ViewUtils.showToast;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.cardview.widget.CardView;

import org.autojs.autojs.AutoJs;
import org.autojs.autojs.model.explorer.ExplorerChangeEvent;
import org.autojs.autojs.model.explorer.ExplorerItem;
import org.autojs.autojs.model.explorer.Explorers;
import org.autojs.autojs.pio.PFile;
import org.autojs.autojs.project.ProjectConfig;
import org.autojs.autojs.project.ProjectLauncher;
import org.autojs.autojs.ui.project.BuildActivity;
import org.autojs.autojs.ui.project.ProjectConfigActivity;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.ExplorerProjectToolbarBinding;
import org.greenrobot.eventbus.Subscribe;

/**
 * Modified by SuperMonster003 as of May 26, 2022.
 * Transformed by SuperMonster003 on May 12, 2023.
 */
public class ExplorerProjectToolbar extends CardView {

    private ExplorerProjectToolbarBinding binding;

    private PFile mDirectory;

    TextView mProjectName;

    private OnOperateListener mOnOperateListener;

    public ExplorerProjectToolbar(Context context) {
        super(context);
        init();
    }

    public ExplorerProjectToolbar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ExplorerProjectToolbar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setVisibility(GONE);
        binding = ExplorerProjectToolbarBinding.inflate(LayoutInflater.from(getContext()), this, true);

        mProjectName = binding.projectName;
        binding.projectRun.setOnClickListener(v -> run());
        binding.projectBuild.setOnClickListener(v -> build());
        binding.projectEdit.setOnClickListener(v -> edit());

        this.setOnClickListener(v -> edit());
    }

    public void setProject(PFile dir) {
        ProjectConfig projectConfig = ProjectConfig.fromProjectDir(dir.getPath());
        if (projectConfig == null) {
            setVisibility(GONE);
        } else {
            setVisibility(VISIBLE);
            mDirectory = dir;
            mProjectName.setText(projectConfig.getName());
        }
    }

    public void refresh() {
        if (mDirectory != null) {
            setProject(mDirectory);
        }
    }

    void run() {
        notifyOperated();
        try {
            new ProjectLauncher(mDirectory.getPath())
                    .launch(AutoJs.getInstance().getScriptEngineService());
        } catch (Exception e) {
            e.printStackTrace();
            showToast(getContext(), e.getMessage(), true);
        }
    }

    private void notifyOperated() {
        if (mOnOperateListener != null) {
            mOnOperateListener.onOperated(binding.commands);
        }
    }

    void build() {
        BuildActivity.launch(getContext(), mDirectory.getPath());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Explorers.workspace().registerChangeListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Explorers.workspace().unregisterChangeListener(this);
    }

    @Subscribe
    public void onExplorerChange(ExplorerChangeEvent event) {
        if (mDirectory == null) {
            return;
        }
        ExplorerItem item = event.getItem();
        if ((event.getAction() == ExplorerChangeEvent.ALL)
                || (item != null && mDirectory.getPath().equals(item.getPath()))) {
            refresh();
        }
    }

    void edit() {
        Intent intent = new Intent(getContext(), ProjectConfigActivity.class)
                .putExtra(ProjectConfigActivity.EXTRA_DIRECTORY, mDirectory.getPath())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
    }

    public void setRunnableOnly(boolean b) {
        ViewGroup viewGroup = binding.commands;
        for (int i = 0; i < viewGroup.getChildCount(); i += 1) {
            ImageView view = (ImageView) viewGroup.getChildAt(i);
            view.setVisibility(!b || view.getId() == R.id.project_run ? VISIBLE : GONE);
        }
    }

    public void setOnOperateListener(@Nullable OnOperateListener onOperateListener) {
        mOnOperateListener = onOperateListener;
    }

    public interface OnOperateListener {
        void onOperated(LinearLayoutCompat toolbar);
    }

}
