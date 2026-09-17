package org.autojs.autojs.ui.explorer;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.cardview.widget.CardView;
import androidx.core.widget.ImageViewCompat;
import org.autojs.autojs.AutoJs;
import org.autojs.autojs.model.explorer.ExplorerChangeEvent;
import org.autojs.autojs.model.explorer.ExplorerItem;
import org.autojs.autojs.model.explorer.Explorers;
import org.autojs.autojs.pio.PFile;
import org.autojs.autojs.project.ProjectConfig;
import org.autojs.autojs.project.ProjectLauncher;
import org.autojs.autojs.script.PythonProjectLaunchPolicy;
import org.autojs.autojs.ui.project.BuildActivity;
import org.autojs.autojs.ui.project.ProjectConfigActivity;
import org.autojs.autojs.ui.python.PythonRuntimePluginUi;
import org.autojs.autojs.ui.terminal.TerminalActivity;
import org.autojs.autojs.util.IntentUtils;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.ExplorerProjectToolbarBinding;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.util.List;

import static org.autojs.autojs.util.ViewUtils.showToast;

/**
 * Modified by SuperMonster003 as of May 26, 2022.
 * Transformed by SuperMonster003 on May 12, 2023.
 */
public class ExplorerProjectToolbar extends CardView {

    private ExplorerProjectToolbarBinding binding;

    private PFile mDirectory;

    private boolean mPythonProject;

    private boolean mRunnableOnly;

    private boolean mBuildable;

    TextView mProjectName;

    private OnOperateListener mOnOperateListener;

    // Unify action icon tint to match day/night theme, especially for overlay dialogs.
    // zh-CN: 统一操作图标着色以匹配日/夜主题, 尤其用于 overlay 对话框场景.
    ColorStateList mActionIconTint = ColorStateList.valueOf(getContext().getColor(R.color.project_toolbar_button));

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

        ImageView projectRun = binding.projectRun;
        ImageView projectBuild = binding.projectBuild;
        ImageView projectEdit = binding.projectEdit;
        ImageView projectTerminal = binding.projectTerminal;

        projectRun.setOnClickListener(v -> run());
        projectBuild.setOnClickListener(v -> build());
        projectEdit.setOnClickListener(v -> edit());
        projectTerminal.setOnClickListener(v -> openInTerminal());

        List.of(projectRun, projectBuild, projectEdit, projectTerminal).forEach(imageView -> {
            ImageViewCompat.setImageTintList(imageView, mActionIconTint);
        });

        this.setOnClickListener(v -> edit());
    }

    public void setProject(PFile dir) {
        trySetProject(dir);
    }

    boolean trySetProject(PFile dir) {
        try {
            if (PythonProjectLaunchPolicy.inspect(new File(dir.getPath()), null) != null) {
                activateProject(dir, dir.getName(), true);
                return true;
            }
            ProjectConfig projectConfig = ProjectConfig.fromProjectDir(dir.getPath());
            mBuildable = projectConfig != null;
            if (projectConfig == null) {
                projectConfig = ProjectConfig.fromProjectDirForExecution(dir.getPath());
            }
            if (projectConfig == null) {
                clearProject();
                return false;
            }
            activateProject(dir, projectConfig.getName(), false);
            return true;
        } catch (RuntimeException ignored) {
            clearProject();
            return false;
        }
    }

    private void activateProject(PFile dir, CharSequence projectName, boolean pythonProject) {
        mDirectory = dir;
        mPythonProject = pythonProject;
        if (pythonProject) mBuildable = false;
        mProjectName.setText(projectName);
        updateCommandVisibility();
        setVisibility(VISIBLE);
    }

    void clearProject() {
        mDirectory = null;
        mPythonProject = false;
        mBuildable = false;
        mProjectName.setText(null);
        updateCommandVisibility();
        setVisibility(GONE);
    }

    public void refresh() {
        if (mDirectory != null) {
            setProject(mDirectory);
        }
    }

    void run() {
        PFile directory = mDirectory;
        if (directory == null) {
            clearProject();
            return;
        }
        notifyOperated();
        if (mPythonProject && !PythonRuntimePluginUi.ensureAvailableForInteractiveLaunch(getContext())) {
            return;
        }
        try {
            new ProjectLauncher(directory.getPath())
                    .launchInteractive(AutoJs.getInstance().getScriptEngineService(), getContext());
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
        PFile directory = mDirectory;
        if (directory == null) {
            clearProject();
            return;
        }
        if (mPythonProject || !mBuildable) {
            return;
        }
        BuildActivity.launch(getContext(), directory.getPath());
    }

    void openInTerminal() {
        PFile directory = mDirectory;
        if (directory == null) {
            clearProject();
            return;
        }
        notifyOperated();
        TerminalActivity.launch(getContext(), directory.getPath());
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
        if (event.getAction() == ExplorerChangeEvent.ALL) {
            refresh();
        } else if (item != null && mDirectory.getPath().equals(item.getPath())) {
            refresh();
        }
    }

    void edit() {
        PFile directory = mDirectory;
        if (directory == null) {
            clearProject();
            return;
        }
        if (mPythonProject) {
            return;
        }
        Intent intent = new Intent(getContext(), ProjectConfigActivity.class)
                .putExtra(ProjectConfigActivity.EXTRA_DIRECTORY, directory.getPath());
        IntentUtils.startSafely(intent, getContext());
    }

    public void setRunnableOnly(boolean b) {
        mRunnableOnly = b;
        updateCommandVisibility();
    }

    private void updateCommandVisibility() {
        ViewGroup viewGroup = binding.commands;
        for (int i = 0; i < viewGroup.getChildCount(); i += 1) {
            ImageView view = (ImageView) viewGroup.getChildAt(i);
            boolean runOnly = mRunnableOnly || mPythonProject;
            boolean visible = !runOnly || view.getId() == R.id.project_run;
            if (view.getId() == R.id.project_build && !mBuildable) visible = false;
            view.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    public void setOnOperateListener(@Nullable OnOperateListener onOperateListener) {
        mOnOperateListener = onOperateListener;
    }

    public interface OnOperateListener {
        void onOperated(LinearLayoutCompat toolbar);
    }

}
