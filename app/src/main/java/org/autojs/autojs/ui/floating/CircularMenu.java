package org.autojs.autojs.ui.floating;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.annotation.NonNull;

import com.afollestad.materialdialogs.MaterialDialog;
import com.makeramen.roundedimageview.RoundedImageView;

import org.autojs.autojs.AutoJs;
import org.autojs.autojs.app.AppLevelThemeDialogBuilder;
import org.autojs.autojs.app.CircularMenuOperationDialogBuilder;
import org.autojs.autojs.app.DialogUtils;
import org.autojs.autojs.core.accessibility.AccessibilityService;
import org.autojs.autojs.core.accessibility.AccessibilityTool;
import org.autojs.autojs.core.accessibility.LayoutInspector;
import org.autojs.autojs.core.accessibility.NodeInfo;
import org.autojs.autojs.core.activity.ActivityInfoProvider;
import org.autojs.autojs.core.record.GlobalActionRecorder;
import org.autojs.autojs.core.record.Recorder;
import org.autojs.autojs.model.explorer.ExplorerDirPage;
import org.autojs.autojs.model.explorer.Explorers;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.pref.Language;
import org.autojs.autojs.pref.Pref;
import org.autojs.autojs.tool.Func1;
import org.autojs.autojs.ui.enhancedfloaty.FloatyService;
import org.autojs.autojs.ui.enhancedfloaty.FloatyWindow;
import org.autojs.autojs.ui.explorer.ExplorerView;
import org.autojs.autojs.ui.floating.layoutinspector.LayoutBoundsFloatyWindow;
import org.autojs.autojs.ui.floating.layoutinspector.LayoutHierarchyFloatyWindow;
import org.autojs.autojs.ui.main.MainActivity;
import org.autojs.autojs.util.ClipboardUtils;
import org.autojs.autojs.util.RootUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs.util.WorkingDirectoryUtils;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.CircularActionMenuBinding;
import org.greenrobot.eventbus.EventBus;
import org.jdeferred.Deferred;
import org.jdeferred.impl.DeferredObject;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;

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
    private CircularActionMenuBinding binding;
    private MaterialDialog mSettingsDialog;
    private MaterialDialog mLayoutInspectDialog;
    private String mRunningPackage, mRunningActivity;
    private Deferred<NodeInfo, Void, Void> mCaptureDeferred;

    public CircularMenu(Context context) {
        // mContext = new ContextThemeWrapper(context, R.style.AppTheme);
        mContext = context;
        initFloaty();
        setupWindowListeners();
        mRecorder = GlobalActionRecorder.getSingleton(context);
        mRecorder.addOnStateChangedListener(this);
        AutoJs.getInstance().getLayoutInspector().addCaptureAvailableListener(this);
    }

    private void setupWindowListeners() {
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

    private void setupBindingListeners() {
        binding.scriptList.setOnClickListener(v -> {
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
        });
        binding.record.setOnClickListener(v -> {
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
        });
        binding.layoutInspect.setOnClickListener(v -> {
            mWindow.collapse();
            inspectLayout(rootNode -> new LayoutBoundsFloatyWindow(rootNode, mContext));
        });
        binding.layoutInspect.setOnLongClickListener(v -> {
            mWindow.collapse();
            mLayoutInspectDialog = new CircularMenuOperationDialogBuilder(mContext)
                    .item(R.drawable.ic_circular_menu_bounds, R.string.text_inspect_layout_bounds, v1 -> {
                        mWindow.collapse();
                        inspectLayout(rootNode -> new LayoutBoundsFloatyWindow(rootNode, mContext));
                    })
                    .item(R.drawable.ic_circular_menu_hierarchy, R.string.text_inspect_layout_hierarchy, v1 -> {
                        mWindow.collapse();
                        inspectLayout(rootNode -> new LayoutHierarchyFloatyWindow(rootNode, mContext));
                    })
                    .title(R.string.text_inspect_layout)
                    .build();
            DialogUtils.showDialog(mLayoutInspectDialog);
            return true;
        });
        binding.stopAllScripts.setOnClickListener(v -> {
            mWindow.collapse();
            if (AutoJs.getInstance().getScriptEngineService().stopAllAndToast() <= 0) {
                ViewUtils.showToast(mContext, R.string.text_no_scripts_to_stop_running);
            }
        });
        binding.actionMenuMore.setOnClickListener(v -> {
            mWindow.collapse();

            if (mSettingsDialog != null && mSettingsDialog.isShowing()) {
                mSettingsDialog.dismiss();
            }

            ActivityInfoProvider infoProvider = AutoJs.getInstance().getInfoProvider();
            mRunningPackage = infoProvider.getLatestPackageByUsageStatsIfGranted();
            mRunningActivity = infoProvider.getLatestActivity();

            mSettingsDialog = new CircularMenuOperationDialogBuilder(mContext)
                    .item(R.drawable.ic_accessibility_black_48dp, R.string.text_manage_a11y_service, v1 -> {
                        dismissSettingsDialog();
                        getAccessibilityTool().getService().launchSettings();
                    })
                    .item(R.drawable.ic_text_fields_black_48dp, mContext.getString(R.string.text_latest_package) + ":\n" + getRunningPackage(), v1 -> {
                        dismissSettingsDialog();
                        if (!TextUtils.isEmpty(mRunningPackage)) {
                            ClipboardUtils.setClip(mContext, mRunningPackage);
                            ViewUtils.showToast(mContext, getTextAlreadyCopied(R.string.text_latest_package));
                        }
                    })
                    .item(R.drawable.ic_text_fields_black_48dp, mContext.getString(R.string.text_latest_activity) + ":\n" + getRunningActivity(), v1 -> {
                        dismissSettingsDialog();
                        if (!TextUtils.isEmpty(mRunningActivity)) {
                            ClipboardUtils.setClip(mContext, mRunningActivity);
                            ViewUtils.showToast(mContext, getTextAlreadyCopied(R.string.text_latest_activity));
                        }
                    })
                    .item(R.drawable.ic_home_black_48dp, R.string.text_open_main_activity, v1 -> {
                        dismissSettingsDialog();
                        MainActivity.launch(mContext);
                    })
                    .item(R.drawable.ic_control_point_black_48dp, R.string.text_pointer_location, v1 -> {
                        dismissSettingsDialog();
                        if (!RootUtils.togglePointerLocation()) {
                            ViewUtils.showToast(mContext, R.string.text_pointer_location_toggle_failed_with_hint, true);
                        }
                    })
                    .item(R.drawable.ic_close_white_48dp, R.string.text_close_floating_button, v1 -> closeAndSaveState())
                    .title(R.string.text_more)
                    .build();

            DialogUtils.showDialog(mSettingsDialog);
        });
    }

    public boolean isRecording() {
        return mState == STATE_RECORDING;
    }

    private void initFloaty() {
        mWindow = new CircularMenuWindow(new CircularMenuFloaty() {
            @Override
            public CircularActionView inflateActionView(FloatyService service, CircularMenuWindow window) {
                CircularActionView actionView = (CircularActionView) View.inflate(service, R.layout.circular_action_view, null);
                mActionViewIcon = actionView.findViewById(R.id.icon);
                return actionView;
            }

            @Override
            public CircularActionMenu inflateMenuItems(FloatyService service, CircularMenuWindow window) {
                CircularActionMenu menu = (CircularActionMenu) View.inflate(new ContextThemeWrapper(service, R.style.AppTheme), R.layout.circular_action_menu, null);
                binding = CircularActionMenuBinding.bind(menu);
                setupBindingListeners();
                return menu;
            }
        });
        mWindow.setKeepToSideHiddenWidthRadio(0.25f);
        FloatyService.addWindow(mWindow);
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

    private void inspectLayout(Func1<NodeInfo, FloatyWindow> windowCreator) {
        if (mLayoutInspectDialog != null) {
            mLayoutInspectDialog.dismiss();
            mLayoutInspectDialog = null;
        }
        if (AccessibilityService.isNotRunning()) {
            ViewUtils.showToast(mContext, R.string.error_no_accessibility_permission_to_capture);
            getAccessibilityTool().launchSettings();
        } else {
            mCaptureDeferred.promise().then(capture -> {
                mActionViewIcon.post(() -> FloatyService.addWindow(windowCreator.call(capture)));
            });
        }
    }

    public void closeAndSaveState() {
        boolean state = FloatyWindowManger.isCircularMenuShowing();
        Pref.putBooleanSync(R.string.key_floating_menu_shown, state);
        savePosition();
        close();
    }

    public void savePosition() {
        mWindow.savePosition();
    }

    public void savePosition(@NotNull Configuration newConfig) {
        mWindow.savePosition(newConfig);
    }

    private AccessibilityTool getAccessibilityTool() {
        return new AccessibilityTool(mContext);
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
                mContext.getString(R.string.text_null).toLowerCase(Language.getPrefLanguage().getLocale()),
                mContext.getString(R.string.text_a11y_service_may_be_needed).toLowerCase(Language.getPrefLanguage().getLocale()));
    }

    @Override
    public void onCaptureAvailable(NodeInfo capture, @NonNull Context context) {
        if (mCaptureDeferred != null && mCaptureDeferred.isPending())
            mCaptureDeferred.resolve(capture);
    }

    private void dismissSettingsDialog() {
        if (mSettingsDialog != null) {
            mSettingsDialog.dismiss();
            mSettingsDialog = null;
        }
    }

    @NonNull
    private String getTextAlreadyCopied(int actionResId) {
        return MessageFormat.format("{0} ({1})",
                mContext.getString(R.string.text_already_copied_to_clip),
                mContext.getString(actionResId).toLowerCase(Language.getPrefLanguage().getLocale()));
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
