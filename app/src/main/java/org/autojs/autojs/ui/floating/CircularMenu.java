package org.autojs.autojs.ui.floating;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.annotation.NonNull;

import com.afollestad.materialdialogs.MaterialDialog;
import com.makeramen.roundedimageview.RoundedImageView;
import com.stardust.enhancedfloaty.FloatyService;
import com.stardust.enhancedfloaty.FloatyWindow;

import org.autojs.autojs.AutoJs;
import org.autojs.autojs.app.AppLevelThemeDialogBuilder;
import org.autojs.autojs.app.CircularMenuOperationDialogBuilder;
import org.autojs.autojs.app.DialogUtils;
import org.autojs.autojs.core.accessibility.AccessibilityServiceTool;
import org.autojs.autojs.core.accessibility.AccessibilityService;
import org.autojs.autojs.core.accessibility.LayoutInspector;
import org.autojs.autojs.core.accessibility.NodeInfo;
import org.autojs.autojs.core.activity.ActivityInfoProvider;
import org.autojs.autojs.core.record.GlobalActionRecorder;
import org.autojs.autojs.core.record.Recorder;
import org.autojs.autojs.model.explorer.ExplorerDirPage;
import org.autojs.autojs.model.explorer.Explorers;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.pref.Pref;
import org.autojs.autojs.tool.Func1;
import org.autojs.autojs.ui.explorer.ExplorerView;
import org.autojs.autojs.ui.floating.layoutinspector.LayoutBoundsFloatyWindow;
import org.autojs.autojs.ui.floating.layoutinspector.LayoutHierarchyFloatyWindow;
import org.autojs.autojs.ui.main.MainActivity_;
import org.autojs.autojs.util.ClipboardUtils;
import org.autojs.autojs.util.RootUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs.util.WorkingDirectoryUtils;
import org.autojs.autojs6.R;
import org.greenrobot.eventbus.EventBus;
import org.jdeferred.Deferred;
import org.jdeferred.impl.DeferredObject;

import java.text.MessageFormat;

import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnLongClick;
import butterknife.Optional;

/**
 * Created by Stardust on 2017/10/18.
 */
public class CircularMenu implements Recorder.OnStateChangedListener, LayoutInspector.CaptureAvailableListener {

    public static final class StateChangeEvent {

        private final int currentState;
        private final int previousState;

        public StateChangeEvent(int currentState, int previousState) {
            this.currentState = currentState;
            this.previousState = previousState;
        }

        public int getCurrentState() {
            return currentState;
        }

        public int getPreviousState() {
            return previousState;
        }

    }

    public static final int STATE_CLOSED = -1;
    public static final int STATE_NORMAL = 0;
    public static final int STATE_RECORDING = 1;

    CircularMenuWindow mWindow;
    private int mState;
    private RoundedImageView mActionViewIcon;
    private final Context mContext;
    private final GlobalActionRecorder mRecorder;
    private MaterialDialog mSettingsDialog;
    private MaterialDialog mLayoutInspectDialog;
    private String mRunningPackage, mRunningActivity;
    private Deferred<NodeInfo, Void, Void> mCaptureDeferred;

    public CircularMenu(Context context) {
        // mContext = new ContextThemeWrapper(context, R.style.AppTheme);
        mContext = context;
        initFloaty();
        setupListeners();
        mRecorder = GlobalActionRecorder.getSingleton(context);
        mRecorder.addOnStateChangedListener(this);
        AutoJs.getInstance().getLayoutInspector().addCaptureAvailableListener(this);
    }

    private void setupListeners() {
        mWindow.setOnActionViewClickListener(v -> {
            if (isRecording()) {
                stopRecord();
            } else if (mWindow.isExpanded()) {
                mWindow.collapse();
            } else {
                mCaptureDeferred = new DeferredObject<>();
                AutoJs.getInstance().getLayoutInspector().captureCurrentWindow();
                mWindow.expand();
            }
        });
    }

    public boolean isRecording() {
        return mState == STATE_RECORDING;
    }

    private void initFloaty() {
        mWindow = new CircularMenuWindow(mContext, new CircularMenuFloaty() {
            @Override
            public CircularActionView inflateActionView(FloatyService service, CircularMenuWindow window) {
                CircularActionView actionView = (CircularActionView) View.inflate(service, R.layout.circular_action_view, null);
                mActionViewIcon = actionView.findViewById(R.id.icon);
                return actionView;
            }

            @Override
            public CircularActionMenu inflateMenuItems(FloatyService service, CircularMenuWindow window) {
                CircularActionMenu menu = (CircularActionMenu) View.inflate(new ContextThemeWrapper(service, R.style.AppTheme), R.layout.circular_action_menu, null);
                ButterKnife.bind(CircularMenu.this, menu);
                return menu;
            }
        });
        mWindow.setKeepToSideHiddenWidthRadio(0.25f);
        FloatyService.addWindow(mWindow);
    }

    @Optional
    @OnClick(R.id.script_list)
    void showScriptList() {
        mWindow.collapse();
        ExplorerView explorerView = new ExplorerView(mContext);
        explorerView.setExplorer(Explorers.workspace(), ExplorerDirPage.createRoot(WorkingDirectoryUtils.getPath()));
        explorerView.setDirectorySpanSize(2);
        final MaterialDialog dialog = new AppLevelThemeDialogBuilder(mContext)
                .title(R.string.text_run_script)
                .customView(explorerView, false)
                .positiveText(R.string.text_cancel)
                .cancelable(false)
                .build();
        explorerView.setOnItemOperateListener(item -> dialog.dismiss());
        explorerView.setOnItemClickListener((view, item) -> Scripts.run(mContext, item.toScriptFile()));
        explorerView.setOnProjectToolbarOperateListener(toolbar -> dialog.dismiss());
        explorerView.setOnProjectToolbarClickListener(toolbar -> toolbar.findViewById(R.id.project_run).performClick());
        explorerView.setProjectToolbarRunnableOnly(true);

        DialogUtils.adaptToExplorer(dialog, explorerView);
        DialogUtils.showDialog(dialog);
    }

    @Optional
    @OnClick(R.id.record)
    void startRecord() {
        mWindow.collapse();
        if (!RootUtils.isRootAvailable()) {
            DialogUtils.showDialog(new AppLevelThemeDialogBuilder(mContext)
                    .title(R.string.text_no_root_access)
                    .content(R.string.no_root_access_for_record)
                    .positiveText(R.string.dialog_button_quit)
                    .build());
        } else {
            mRecorder.start();
        }
    }

    private void setState(int state) {
        int previousState = mState;

        mState = state;
        mActionViewIcon.setImageResource(isRecording()
                ? R.drawable.ic_ali_record
                : R.drawable.autojs6_material);
        mActionViewIcon.setBackgroundTintList(ColorStateList.valueOf(mContext.getColor(isRecording()
                ? R.color.circular_menu_icon_red
                : R.color.circular_menu_icon_white)));
        int padding = (int) mContext.getResources().getDimension(isRecording()
                ? R.dimen.padding_circular_menu_recording
                : R.dimen.padding_circular_menu_normal);
        mActionViewIcon.setPadding(padding, padding, padding, padding);

        EventBus.getDefault().post(new StateChangeEvent(mState, previousState));
    }

    public void stopRecord() {
        mRecorder.stop();
    }

    @Optional
    @OnLongClick(R.id.layout_inspect)
    void inspectLayout() {
        mWindow.collapse();
        mLayoutInspectDialog = new CircularMenuOperationDialogBuilder(mContext)
                .item(R.id.layout_bounds, R.drawable.ic_circular_menu_bounds, R.string.text_inspect_layout_bounds)
                .item(R.id.layout_hierarchy, R.drawable.ic_circular_menu_hierarchy, R.string.text_inspect_layout_hierarchy)
                .bindItemClick(this)
                .title(R.string.text_inspect_layout)
                .build();
        DialogUtils.showDialog(mLayoutInspectDialog);
    }

    @Optional
    @OnClick({R.id.layout_bounds, R.id.layout_inspect})
    void showLayoutBounds() {
        mWindow.collapse();
        inspectLayout(rootNode -> new LayoutBoundsFloatyWindow(rootNode, mContext));
    }

    @Optional
    @OnClick(R.id.layout_hierarchy)
    void showLayoutHierarchy() {
        inspectLayout(rootNode -> new LayoutHierarchyFloatyWindow(rootNode, mContext));
    }

    private void inspectLayout(Func1<NodeInfo, FloatyWindow> windowCreator) {
        if (mLayoutInspectDialog != null) {
            mLayoutInspectDialog.dismiss();
            mLayoutInspectDialog = null;
        }
        if (AccessibilityService.isNotRunning()) {
            ViewUtils.showToast(mContext, R.string.text_no_accessibility_permission_to_capture);
            getAccessibilityServiceTool().goToAccessibilitySetting();
        } else {
            mCaptureDeferred.promise().then(capture -> {
                mActionViewIcon.post(() -> FloatyService.addWindow(windowCreator.call(capture)));
            });
        }
    }

    private AccessibilityServiceTool getAccessibilityServiceTool() {
        return new AccessibilityServiceTool(mContext);
    }

    @Optional
    @OnClick(R.id.stop_all_scripts)
    void stopAllScripts() {
        mWindow.collapse();
        if (AutoJs.getInstance().getScriptEngineManager().getEngines().size() > 0) {
            AutoJs.getInstance().getScriptEngineService().stopAllAndToast();
        } else {
            ViewUtils.showToast(mContext, R.string.text_no_scripts_to_stop_running);
        }
    }

    @Override
    public void onCaptureAvailable(NodeInfo capture, @NonNull Context context) {
        if (mCaptureDeferred != null && mCaptureDeferred.isPending())
            mCaptureDeferred.resolve(capture);
    }

    @Optional
    @OnClick(R.id.action_menu_more)
    void settings() {
        mWindow.collapse();

        if (mSettingsDialog != null && mSettingsDialog.isShowing()) {
            mSettingsDialog.dismiss();
        }

        ActivityInfoProvider infoProvider = AutoJs.getInstance().getInfoProvider();
        mRunningPackage = infoProvider.getLatestPackageByUsageStatsIfGranted();
        mRunningActivity = infoProvider.getLatestActivity();

        mSettingsDialog = new CircularMenuOperationDialogBuilder(mContext)
                .item(R.id.accessibility_service, R.drawable.ic_accessibility_black_48dp, R.string.text_manage_a11y_service)
                .item(R.id.package_name, R.drawable.ic_text_fields_black_48dp, mContext.getString(R.string.text_latest_package) + ":\n" + getRunningPackage())
                .item(R.id.class_name, R.drawable.ic_text_fields_black_48dp, mContext.getString(R.string.text_latest_activity) + ":\n" + getRunningActivity())
                .item(R.id.open_launcher, R.drawable.ic_home_black_48dp, R.string.text_open_main_activity)
                .item(R.id.pointer_location, R.drawable.ic_control_point_black_48dp, R.string.text_pointer_location)
                .item(R.id.exit, R.drawable.ic_close_white_48dp, R.string.text_exit_floating_window)
                .bindItemClick(this)
                .title(R.string.text_more)
                .build();

        DialogUtils.showDialog(mSettingsDialog);
    }

    private String getRunningPackage() {
        if (!TextUtils.isEmpty(mRunningPackage)) {
            return mRunningPackage;
        }
        return getEmptyInfoHint();
    }

    private String getRunningActivity() {
        if (!TextUtils.isEmpty(mRunningActivity)) {
            return mRunningActivity;
        }
        return getEmptyInfoHint();
    }

    private String getEmptyInfoHint() {
        return MessageFormat.format("{0} ({1})",
                mContext.getString(R.string.text_null).toLowerCase(),
                mContext.getString(R.string.text_a11y_service_may_be_needed).toLowerCase());
    }

    @Optional
    @OnClick(R.id.accessibility_service)
    void enableAccessibilityService() {
        dismissSettingsDialog();
        getAccessibilityServiceTool().enableAccessibilityService();
    }

    private void dismissSettingsDialog() {
        if (mSettingsDialog != null) {
            mSettingsDialog.dismiss();
            mSettingsDialog = null;
        }
    }

    @Optional
    @OnClick(R.id.package_name)
    void copyPackageName() {
        dismissSettingsDialog();
        if (!TextUtils.isEmpty(mRunningPackage)) {
            ClipboardUtils.setClip(mContext, mRunningPackage);
            ViewUtils.showToast(mContext, getTextAlreadyCopied(R.string.text_latest_package));
        }
    }

    @Optional
    @OnClick(R.id.class_name)
    void copyActivityName() {
        dismissSettingsDialog();
        if (!TextUtils.isEmpty(mRunningActivity)) {
            ClipboardUtils.setClip(mContext, mRunningActivity);
            ViewUtils.showToast(mContext, getTextAlreadyCopied(R.string.text_latest_activity));
        }
    }

    @NonNull
    private String getTextAlreadyCopied(int actionResId) {
        return MessageFormat.format("{0} ({1})",
                mContext.getString(R.string.text_already_copied_to_clip),
                mContext.getString(actionResId).toLowerCase());
    }

    @Optional
    @OnClick(R.id.open_launcher)
    void openLauncher() {
        dismissSettingsDialog();
        mContext.startActivity(new Intent(mContext, MainActivity_.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @Optional
    @OnClick(R.id.pointer_location)
    void togglePointerLocation() {
        dismissSettingsDialog();
        if (!RootUtils.togglePointerLocation()) {
            ViewUtils.showToast(mContext, R.string.text_pointer_location_toggle_failed_with_hint, true);
        }
    }

    public void close() {
        dismissSettingsDialog();
        try {
            mWindow.close();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } finally {
            EventBus.getDefault().post(new StateChangeEvent(STATE_CLOSED, mState));
            mState = STATE_CLOSED;
        }
        mRecorder.removeOnStateChangedListener(this);
        AutoJs.getInstance().getLayoutInspector().removeCaptureAvailableListener(this);
    }

    @Optional
    @OnClick(R.id.exit)
    public void closeAndSaveState() {
        close();
        Pref.putBoolean(R.string.key_floating_menu_shown, false);
    }

    @Override
    public void onStart() {
        setState(STATE_RECORDING);
    }

    @Override
    public void onStop() {
        setState(STATE_NORMAL);
    }

    @Override
    public void onPause() {
    }

    @Override
    public void onResume() {

    }

}
