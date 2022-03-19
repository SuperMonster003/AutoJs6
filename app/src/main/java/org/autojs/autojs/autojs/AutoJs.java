package org.autojs.autojs.autojs;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.stardust.app.GlobalAppContext;
import com.stardust.autojs.core.accessibility.AccessibilityServiceTool;
import com.stardust.autojs.core.console.GlobalConsole;
import com.stardust.autojs.runtime.ScriptRuntime;
import com.stardust.autojs.runtime.accessibility.AccessibilityConfig;
import com.stardust.autojs.runtime.api.AppUtils;
import com.stardust.autojs.runtime.exception.ScriptException;
import com.stardust.autojs.runtime.exception.ScriptInterruptedException;
import com.stardust.view.accessibility.AccessibilityService;
import com.stardust.view.accessibility.LayoutInspector;
import com.stardust.view.accessibility.NodeInfo;

import org.autojs.autojs.R;
import org.autojs.autojs.external.fileprovider.AppFileProvider;
import org.autojs.autojs.pluginclient.DevPluginService;
import org.autojs.autojs.ui.floating.FloatyWindowManger;
import org.autojs.autojs.ui.floating.FullScreenFloatyWindow;
import org.autojs.autojs.ui.floating.layoutinspector.LayoutBoundsFloatyWindow;
import org.autojs.autojs.ui.floating.layoutinspector.LayoutHierarchyFloatyWindow;
import org.autojs.autojs.ui.log.LogActivity_;
import org.autojs.autojs.ui.settings.SettingsActivity_;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Created by Stardust on 2017/4/2.
 */

public class AutoJs extends com.stardust.autojs.AutoJs {

    private static AutoJs instance;

    private final Application appContext;

    // @Thank to Zen2H
    private final ExecutorService printExecutor = Executors.newSingleThreadExecutor();

    public static AutoJs getInstance() {
        return instance;
    }

    public synchronized static void initInstance(Application application) {
        if (instance != null) {
            return;
        }
        instance = new AutoJs(application);
    }

    private interface LayoutInspectFloatyWindow {
        FullScreenFloatyWindow create(NodeInfo nodeInfo);
    }

    private AutoJs(final Application application) {
        super(application);
        appContext = application;
        getScriptEngineService().registerGlobalScriptExecutionListener(new ScriptExecutionGlobalListener());
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(LayoutBoundsFloatyWindow.class.getName());
        intentFilter.addAction(LayoutHierarchyFloatyWindow.class.getName());
        BroadcastReceiver mLayoutInspectBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    ensureAccessibilityServiceEnabled();
                    String action = intent.getAction();
                    if (LayoutBoundsFloatyWindow.class.getName().equals(action)) {
                        capture(LayoutBoundsFloatyWindow::new);
                    } else if (LayoutHierarchyFloatyWindow.class.getName().equals(action)) {
                        capture(LayoutHierarchyFloatyWindow::new);
                    }
                } catch (Exception e) {
                    if (Looper.myLooper() != Looper.getMainLooper()) {
                        throw e;
                    }
                }
            }
        };
        LocalBroadcastManager.getInstance(application).registerReceiver(mLayoutInspectBroadcastReceiver, intentFilter);
    }

    private void capture(LayoutInspectFloatyWindow window) {
        LayoutInspector inspector = getLayoutInspector();
        LayoutInspector.CaptureAvailableListener listener = new LayoutInspector.CaptureAvailableListener() {
            @Override
            public void onCaptureAvailable(NodeInfo capture) {
                inspector.removeCaptureAvailableListener(this);
                getUiHandler().post(() ->
                        FloatyWindowManger.addWindow(getApplication().getApplicationContext(), window.create(capture))
                );
            }
        };
        inspector.addCaptureAvailableListener(listener);
        if (!inspector.captureCurrentWindow()) {
            inspector.removeCaptureAvailableListener(listener);
        }
    }

    @Override
    protected AppUtils createAppUtils(Context context) {
        return new AppUtils(context, AppFileProvider.AUTHORITY);
    }

    @Override
    protected GlobalConsole createGlobalConsole() {
        DevPluginService devPluginService = DevPluginService.getInstance();
        return new GlobalConsole(getUiHandler()) {
            @Override
            public String println(int level, CharSequence charSequence) {
                String log = super.println(level, charSequence);

                // FIXME by SuperMonster003 as of Feb 2, 2022.
                //  ! When running in 'ui' thread (`ui.run`, `ui.post`),
                //  ! android.os.NetworkOnMainThreadException may happen.
                //  ! Further more, dunno if a thread executor is a good idea.
                printExecutor.submit(() -> devPluginService.print(log));

                return log;
            }
        };
    }

    public void ensureAccessibilityServiceEnabled() {
        if (AccessibilityService.Companion.getInstance() != null) {
            return;
        }
        String errorMessage = tryEnableAccessibilityService();
        if (errorMessage != null) {
            getAccessibilityTool().goToAccessibilitySetting();
            throw new ScriptException(errorMessage);
        }
    }

    @Override
    public void waitForAccessibilityServiceEnabled() {
        if (AccessibilityService.Companion.getInstance() != null) {
            return;
        }
        String errorMessage = tryEnableAccessibilityService();
        if (errorMessage != null) {
            getAccessibilityTool().goToAccessibilitySetting();
            if (!AccessibilityService.Companion.waitForEnabled(-1)) {
                throw new ScriptInterruptedException();
            }
        }
    }

    @Nullable
    private String tryEnableAccessibilityService() {
        if (getAccessibilityTool().isAccessibilityServiceEnabled()) {
            return GlobalAppContext.getString(R.string.text_auto_operate_service_enabled_but_not_working);
        }
        if (!getAccessibilityTool().enableAccessibilityServiceAutomaticallyIfNeededAndWaitFor(2000)) {
            return GlobalAppContext.getString(R.string.text_no_accessibility_permission);
        }
        return null;
    }

    @Override
    protected AccessibilityConfig createAccessibilityConfig() {
        return super.createAccessibilityConfig();
    }

    @Override
    protected ScriptRuntime createRuntime() {
        ScriptRuntime runtime = super.createRuntime();
        runtime.putProperty("class.settings", SettingsActivity_.class);
        runtime.putProperty("class.console", LogActivity_.class);
        runtime.putProperty("broadcast.inspect_layout_bounds", LayoutBoundsFloatyWindow.class.getName());
        runtime.putProperty("broadcast.inspect_layout_hierarchy", LayoutHierarchyFloatyWindow.class.getName());
        return runtime;
    }

    private AccessibilityServiceTool getAccessibilityTool() {
        return new AccessibilityServiceTool(appContext);
    }

}
