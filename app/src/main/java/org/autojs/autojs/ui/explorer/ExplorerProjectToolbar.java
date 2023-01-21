package org.autojs.autojs.ui.explorer;

import static org.autojs.autojs.util.ViewUtils.showToast;

import android.content.Context;
import android.util.AttributeSet;
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
import org.autojs.autojs.ui.project.ProjectConfigActivity_;
import org.autojs.autojs6.R;
import org.greenrobot.eventbus.Subscribe;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class ExplorerProjectToolbar extends CardView {

    private PFile mDirectory;

    @BindView(R.id.project_name)
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
        inflate(getContext(), R.layout.explorer_project_toolbar, this);
        ButterKnife.bind(this);
        setOnClickListener(view -> edit());
    }

    public void setProject(PFile dir) {
        ProjectConfig projectConfig = ProjectConfig.fromProjectDir(dir.getPath());
        if (projectConfig == null) {
            setVisibility(GONE);
            return;
        }
        mDirectory = dir;
        mProjectName.setText(projectConfig.getName());
    }

    public void refresh() {
        if (mDirectory != null) {
            setProject(mDirectory);
        }
    }

    @OnClick(R.id.project_run)
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
            mOnOperateListener.onOperated(findViewById(R.id.commands));
        }
    }

    @OnClick(R.id.project_build)
    void build() {
        new BuildActivity.IntentBuilder(getContext())
                .extra(mDirectory.getPath())
                .start();
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

    @OnClick(R.id.project_edit)
    void edit() {
        ProjectConfigActivity_.intent(getContext())
                .extra(ProjectConfigActivity.EXTRA_DIRECTORY, mDirectory.getPath())
                .start();
    }

    public void setRunnableOnly(boolean b) {
        ViewGroup viewGroup = (ViewGroup) findViewById(R.id.commands);
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
