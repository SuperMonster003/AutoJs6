package org.autojs.autojs.ui.floating;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.RemoteException;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import com.afollestad.materialdialogs.MaterialDialog;
import com.makeramen.roundedimageview.RoundedImageView;
import org.autojs.autojs.AutoJs;
import org.autojs.autojs.app.AppLevelThemeDialogBuilder;
import org.autojs.autojs.app.CircularMenuOperationDialogBuilder;
import org.autojs.autojs.util.DialogUtils;
import org.autojs.autojs.app.tool.PointerLocationTool;
import org.autojs.autojs.core.accessibility.AccessibilityTool;
import org.autojs.autojs.core.accessibility.Capture;
import org.autojs.autojs.core.accessibility.LayoutInspector;
import org.autojs.autojs.core.activity.ActivityInfoProvider;
import org.autojs.autojs.core.pref.Language;
import org.autojs.autojs.core.pref.Pref;
import org.autojs.autojs.core.record.GlobalActionRecorder;
import org.autojs.autojs.core.record.Recorder;
import org.autojs.autojs.core.shizuku.IUserService;
import org.autojs.autojs.event.GlobalKeyObserver;
import org.autojs.autojs.model.explorer.ExplorerDirPage;
import org.autojs.autojs.model.explorer.ExplorerPage;
import org.autojs.autojs.model.explorer.Explorers;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.runtime.api.WrappedShizuku;
import org.autojs.autojs.runtime.api.augment.shell.Shell;
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
import java.util.List;
import java.util.Objects;

/**
 * Created by Stardust on Oct 18, 2017.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Jan 20, 2026.
 * Modified by SuperMonster003 as of Jan 20, 2026.
 */
@SuppressWarnings({"unused", "CodeBlock2Expr"})
public class CircularMenu implements LayoutInspector.CaptureAvailableListener {

    public record StateChangeEvent(int currentState, int previousState) {
        /* Empty record body. */
    }

    public static final int STATE_CLOSED = -1;
    public static final int STATE_NORMAL = 0;
    public static final int STATE_RECORDING = 1;

    CircularMenuWindow mWindow;

    private int mState;
    private RoundedImageView mActionViewIcon;
    private Context mContext;
    private final GlobalActionRecorder mRecorder;
    private final Recorder.OnStateChangedListener mRecorderStateListener;
    private CircularActionMenuBinding binding;
    private MaterialDialog mMenuOperationDialog;
    private MaterialDialog mScriptListDialog;
    private ExplorerView mScriptListDialogExplorerView;
    private MaterialDialog mLayoutInspectDialog;
    private String mCurrentPackage;
    private String mCurrentActivity;
    private Deferred<Capture, Void, Void> mCaptureDeferred;
    private final AccessibilityTool mA11yTool;
    private final PointerLocationTool mPointerLocationTool;
    private final GlobalKeyObserver mGlobalKeyObserver;
    private final GlobalKeyObserver.OnVolumeDownListener mVolumeDownListener;

    private final View.OnClickListener mCollapseWindowAndInspectLayoutBoundsListener = v -> {
        mWindow.collapse();
        inspectLayout(capture -> new LayoutBoundsFloatyWindow(capture, mContext));
    };
    private final View.OnClickListener mCollapseWindowAndInspectLayoutHierarchyListener = v -> {
        mWindow.collapse();
        inspectLayout(capture -> new LayoutHierarchyFloatyWindow(capture, mContext));
    };

    public CircularMenu(Context context) {
        // Use application context as base to avoid being treated as Activity context.
        // zh-CN: 使用 application context 作为 base, 避免被识别为 Activity context.
        mContext = new ContextThemeWrapper(context.getApplicationContext(), R.style.AppTheme);
        initFloaty();
        setupWindowListeners();
        mRecorder = GlobalActionRecorder.getSingleton(context);
        mRecorderStateListener = new Recorder.OnStateChangedListener() {
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
                /* Empty body. */
            }

            @Override
            public void onResume() {
                /* Empty body. */
            }
        };
        mRecorder.addOnStateChangedListener(mRecorderStateListener);
        AutoJs.getInstance().getLayoutInspector().addCaptureAvailableListener(this);
        mA11yTool = new AccessibilityTool(mContext);
        mPointerLocationTool = new PointerLocationTool(mContext);
        mGlobalKeyObserver = GlobalKeyObserver.getSingleton(mContext.getApplicationContext());
        mVolumeDownListener = this::onVolumeDownForRecord;
        mGlobalKeyObserver.addVolumeDownListener(mVolumeDownListener);
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

            if (mScriptListDialog == null) {

                mScriptListDialogExplorerView = new ExplorerView(mContext);
                mScriptListDialogExplorerView.setExplorer(Explorers.workspace(), ExplorerDirPage.createRoot(WorkingDirectoryUtils.getPath()));
                mScriptListDialogExplorerView.setDirectorySpanSize(2);

                mScriptListDialog = new MaterialDialog.Builder(mContext)
                        .title(R.string.text_run_script)
                        .titleColorRes(R.color.day_night)
                        .options(List.of(
                                new MaterialDialog.OptionMenuItemSpec(mContext.getString(R.string.dialog_button_homepage), (d) -> {
                                    String homepage = WorkingDirectoryUtils.getPath();
                                    ExplorerPage currentPageStatePage = mScriptListDialogExplorerView.currentPageState.getPage();
                                    if (currentPageStatePage != null) {
                                        if (!Objects.equals(currentPageStatePage.getPath(), homepage)) {
                                            mScriptListDialogExplorerView.saveCurrentPageIntoHistory();
                                        }
                                    }
                                    mScriptListDialogExplorerView.setExplorer(Explorers.workspace(), ExplorerDirPage.createRoot(homepage));
                                })
                        ))
                        .customView(mScriptListDialogExplorerView, false)
                        .backgroundColorRes(R.color.window_background)
                        .neutralText(R.string.dialog_button_minimize)
                        .neutralColorRes(R.color.dialog_button_reset)
                        .onNeutral((dialog, which) -> {
                            mScriptListDialog.hide();
                        })
                        .positiveText(R.string.dialog_button_dismiss)
                        .positiveColorRes(R.color.dialog_button_default)
                        .onPositive((dialog, which) -> {
                            dialog.dismiss();
                        })
                        .cancelable(false)
                        .autoDismiss(false)
                        .dismissListener((di) -> {
                            mScriptListDialog = null;
                            mScriptListDialogExplorerView = null;
                        })
                        .build();

                var scriptListDialog = mScriptListDialog;

                ImageView optionsView = scriptListDialog.getOptionsView();
                if (optionsView != null) {
                    optionsView.setImageTintList(ColorStateList.valueOf(mContext.getColor(R.color.dialog_options_button_tint)));
                }

                // Hide host dialog before launching Activity or showing secondary dialogs.
                // zh-CN: 在启动 Activity 或显示二级对话框之前隐藏宿主对话框.
                mScriptListDialogExplorerView.setRequestHostDialogHide(scriptListDialog::hide);

                // Restore host dialog (overlay) when needed, keeping state.
                // zh-CN: 需要时恢复宿主对话框 (overlay), 并保留状态.
                mScriptListDialogExplorerView.setRequestHostDialogShow(() -> {
                    if (!scriptListDialog.isShowing()) {
                        DialogUtils.showAdaptive(scriptListDialog);
                    }
                });

                // Only dismiss on clicking the item itself.
                // zh-CN: 仅在点击条目本体时关闭对话框.
                mScriptListDialogExplorerView.setOnItemClickListener((view, item) -> {
                    if (item.isExecutable()) {
                        scriptListDialog.hide();
                        Scripts.run(view != null ? view.getContext() : mScriptListDialogExplorerView.getContext(), item.toScriptFile());
                    } else {
                        DialogUtils.showAdaptive(new MaterialDialog.Builder(mContext)
                                .title(mContext.getString(R.string.error_failed_to_run_script))
                                .content(mContext.getString(
                                        R.string.text_file_with_abs_path_is_not_an_executable_script,
                                        item.toScriptFile().getAbsolutePath()
                                ))
                                .positiveText(R.string.dialog_button_dismiss)
                                .positiveColorRes(R.color.dialog_button_failure)
                                .build());
                    }
                });

                mScriptListDialogExplorerView.setOnItemOperateListener(null);

                mScriptListDialogExplorerView.setOnProjectToolbarOperateListener(toolbar -> scriptListDialog.hide());
                mScriptListDialogExplorerView.setOnProjectToolbarClickListener(toolbar -> toolbar.findViewById(R.id.project_run).performClick());
                mScriptListDialogExplorerView.setProjectToolbarRunnableOnly(true);

                DialogUtils.adaptToExplorer(scriptListDialog, mScriptListDialogExplorerView);
            }

            DialogUtils.showAdaptive(mScriptListDialog);
        });
        binding.record.setOnClickListener(v -> {
            mWindow.collapse();
            boolean hasShizukuAccessForRecord = WrappedShizuku.INSTANCE.isOperational();
            if (!hasShizukuAccessForRecord && !RootUtils.isRootAvailable()) {
                DialogUtils.showAdaptive(new AppLevelThemeDialogBuilder(mContext)
                        .title(mContext.getString(R.string.text_prompt))
                        .content(mContext.getString(R.string.error_conditions_not_met_for_record))
                        .positiveText(R.string.dialog_button_abandon)
                        .positiveColorRes(R.color.dialog_button_failure)
                        .build());
            } else {
                mRecorder.start();
            }
        });
        binding.layoutInspect.setOnClickListener(mCollapseWindowAndInspectLayoutBoundsListener);
        binding.layoutInspect.setOnLongClickListener(v -> {
            mWindow.collapse();
            mLayoutInspectDialog = new CircularMenuOperationDialogBuilder(mContext)
                    .item(R.drawable.ic_circular_menu_bounds, mContext.getString(R.string.text_inspect_layout_bounds), mCollapseWindowAndInspectLayoutBoundsListener)
                    .item(R.drawable.ic_circular_menu_hierarchy, mContext.getString(R.string.text_inspect_layout_hierarchy), mCollapseWindowAndInspectLayoutHierarchyListener)
                    .title(mContext.getString(R.string.text_inspect_layout))
                    .build();
            DialogUtils.showAdaptive(mLayoutInspectDialog);
            return true;
        });
        binding.stopAllScripts.setOnClickListener(v -> {
            mWindow.collapse();
            if (AutoJs.getInstance().getScriptEngineService().stopAllAndToast() <= 0) {
                ViewUtils.showToast(mContext, mContext.getString(R.string.text_no_scripts_to_stop_running));
            }
        });
        binding.actionMenuMore.setOnClickListener(v -> {
            mWindow.collapse();

            if (mMenuOperationDialog != null && mMenuOperationDialog.isShowing()) {
                mMenuOperationDialog.dismiss();
            }

            mMenuOperationDialog = new CircularMenuOperationDialogBuilder(mContext)
                    .item(R.drawable.ic_accessibility_black_48dp, mContext.getString(R.string.text_a11y_service), this::getA11yState, onCircularMenuItemClick(itemView -> {
                        mA11yTool.launchSettings(false, true);
                    }))
                    .item(R.drawable.ic_text_fields_black_48dp, mContext.getString(R.string.text_latest_package), this::getCurrentPackage, onCircularMenuItemClick(itemView -> {
                        if (!TextUtils.isEmpty(mCurrentPackage)) {
                            ClipboardUtils.setClip(mContext, mCurrentPackage);
                            ViewUtils.showToast(mContext, getTextAlreadyCopied(R.string.text_latest_package));
                        }
                    }))
                    .item(R.drawable.ic_text_fields_black_48dp, mContext.getString(R.string.text_latest_activity), this::getCurrentActivity, onCircularMenuItemClick(itemView -> {
                        if (!TextUtils.isEmpty(mCurrentActivity)) {
                            ClipboardUtils.setClip(mContext, mCurrentActivity);
                            ViewUtils.showToast(mContext, getTextAlreadyCopied(R.string.text_latest_activity));
                        }
                    }))
                    .item(R.drawable.ic_home_black_48dp, mContext.getString(R.string.text_open_main_activity), onCircularMenuItemClick(itemView -> {
                        MainActivity.launch(mContext);
                    }))
                    .item(R.drawable.ic_control_point_black_48dp, mContext.getString(R.string.text_pointer_location), this::getPointerLocationState, onCircularMenuItemClick(itemView -> {
                        if (PointerLocationTool.togglePointerLocation(mContext)) {
                            // var subtitleView = itemView.findViewById(R.id.subtitle);
                            // if (subtitleView instanceof TextView textView) {
                            //     textView.setText(mPointerLocationTool.isShowing() ? mContext.getString(R.string.text_enabled) : mContext.getString(R.string.text_disabled));
                            // }
                            EventBus.getDefault().post(new PointerLocationTool.Companion.StateChangedEvent());
                            return;
                        }
                        // ViewUtils.showToast(mContext, mContext.getString(R.string.text_pointer_location_toggle_failed_with_hint), true);
                        mPointerLocationTool.config();
                    }))
                    .item(R.drawable.ic_close_white_48dp, mContext.getString(R.string.text_close_floating_button), onCircularMenuItemClick(itemView -> {
                        closeAndSaveState(false);
                    }))
                    .title(mContext.getString(R.string.text_more))
                    .build();

            DialogUtils.showAdaptive(mMenuOperationDialog);
        });
    }

    @NonNull
    private String getA11yState() {
        return mA11yTool.isMalfunctioning() ? mContext.getString(R.string.text_malfunctioning) : mA11yTool.isRunning() ? mContext.getString(R.string.text_enabled) : mContext.getString(R.string.text_disabled);
    }

    @NonNull
    private String getPointerLocationState() {
        return mPointerLocationTool.isShowing() ? mContext.getString(R.string.text_enabled) : mContext.getString(R.string.text_disabled);
    }

    @NonNull
    private View.OnClickListener onCircularMenuItemClick(View.OnClickListener listener) {
        return v -> {
            dismissSettingsDialog();
            dismissScriptListDialog();
            listener.onClick(v);
        };
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

    private void onVolumeDownForRecord() {
        if (!Pref.isUseVolumeControlRecordEnabled() || mState == STATE_CLOSED) {
            return;
        }
        Runnable toggleTask = () -> {
            if (isRecording()) {
                stopRecord();
                return;
            }
            boolean hasShizukuAccessForRecord = WrappedShizuku.INSTANCE.isOperational();
            if (hasShizukuAccessForRecord || RootUtils.isRootAvailable()) {
                mRecorder.start();
            } else {
                ViewUtils.showToast(mContext, mContext.getString(R.string.no_root_access_for_record));
            }
        };
        if (mActionViewIcon != null) {
            mActionViewIcon.post(toggleTask);
        } else {
            toggleTask.run();
        }
    }

    private void inspectLayout(Func1<Capture, FloatyWindow> windowCreator) {
        if (mLayoutInspectDialog != null) {
            mLayoutInspectDialog.dismiss();
            mLayoutInspectDialog = null;
        }
        if (!mA11yTool.isRunning()) {
            if (!mA11yTool.startService(false)) {
                ViewUtils.showToast(mContext, mContext.getString(R.string.error_no_accessibility_permission_to_capture));
                mA11yTool.launchSettings();
            }
        } else {
            mCaptureDeferred.promise().then(capture -> {
                mActionViewIcon.post(() -> FloatyService.addWindow(windowCreator.call(capture)));
            });
        }
    }

    public void closeAndSaveState(boolean state) {
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

    private String getCurrentPackage() {
        if (WrappedShizuku.INSTANCE.isOperational()) {
            try {
                IUserService service = WrappedShizuku.getServiceOrNull();
                if (service != null) {
                    mCurrentPackage = service.currentPackage();
                    if (!TextUtils.isEmpty(mCurrentPackage)) {
                        return mCurrentPackage;
                    }
                }
            } catch (RemoteException ignored) {
                /* Ignored. */
            }
        }
        if (RootUtils.isRootAvailable()) {
            mCurrentPackage = Shell.currentPackageRhino();
            if (!TextUtils.isEmpty(mCurrentPackage)) {
                return mCurrentPackage;
            }
        }
        ActivityInfoProvider infoProvider = AutoJs.getInstance().getInfoProvider();
        mCurrentPackage = infoProvider.getLatestPackageByUsageStatsIfGranted();
        if (!TextUtils.isEmpty(mCurrentPackage)) {
            return mCurrentPackage;
        }
        return getEmptyInfoHint();
    }

    private String getCurrentActivity() {
        if (WrappedShizuku.INSTANCE.isOperational()) {
            try {
                IUserService service = WrappedShizuku.getServiceOrNull();
                if (service != null) {
                    mCurrentActivity = service.currentActivity();
                    if (!TextUtils.isEmpty(mCurrentActivity)) {
                        return mCurrentActivity;
                    }
                }
            } catch (RemoteException ignored) {
                /* Ignored. */
            }
        }
        if (RootUtils.isRootAvailable()) {
            mCurrentActivity = Shell.currentActivityRhino();
            if (!TextUtils.isEmpty(mCurrentActivity)) {
                return mCurrentActivity;
            }
        }
        ActivityInfoProvider infoProvider = AutoJs.getInstance().getInfoProvider();
        mCurrentActivity = infoProvider.getLatestActivity();
        if (!TextUtils.isEmpty(mCurrentActivity)) {
            return mCurrentActivity;
        }
        return getEmptyInfoHint();
    }

    private String getEmptyInfoHint() {
        return MessageFormat.format("{0} ({1})",
                mContext.getString(R.string.text_null).toLowerCase(Language.getPrefLanguage().getLocale()),
                mContext.getString(R.string.text_a11y_service_may_be_needed).toLowerCase(Language.getPrefLanguage().getLocale()));
    }

    @Override
    public void onCaptureAvailable(@NotNull Capture capture, @NonNull Context context) {
        if (mCaptureDeferred != null && mCaptureDeferred.isPending()) {
            mCaptureDeferred.resolve(capture);
        }
    }

    private void dismissSettingsDialog() {
        if (mMenuOperationDialog != null) {
            mMenuOperationDialog.dismiss();
            mMenuOperationDialog = null;
        }
    }

    private void dismissScriptListDialog() {
        if (mScriptListDialog != null) {
            mScriptListDialog.dismiss();
            mScriptListDialog = null;
        }
        if (mScriptListDialogExplorerView != null) {
            mScriptListDialogExplorerView = null;
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
        dismissScriptListDialog();
        try {
            mWindow.close();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } finally {
            EventBus.getDefault().post(new StateChangeEvent(STATE_CLOSED, mState));
            mState = STATE_CLOSED;
        }
        mRecorder.removeOnStateChangedListener(mRecorderStateListener);
        AutoJs.getInstance().getLayoutInspector().removeCaptureAvailableListener(this);
        mGlobalKeyObserver.removeVolumeDownListener(mVolumeDownListener);
    }
}
