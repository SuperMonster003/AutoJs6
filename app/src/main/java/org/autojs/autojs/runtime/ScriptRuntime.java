package org.autojs.autojs.runtime;

import static org.autojs.autojs.util.StringUtils.str;

import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.util.Log;

import org.autojs.autojs.annotation.ScriptVariable;
import org.autojs.autojs.concurrent.VolatileDispose;
import org.autojs.autojs.core.accessibility.AccessibilityBridge;
import org.autojs.autojs.core.accessibility.SimpleActionAutomator;
import org.autojs.autojs.core.accessibility.UiSelector;
import org.autojs.autojs.core.activity.ActivityInfoProvider;
import org.autojs.autojs.core.console.GlobalConsole;
import org.autojs.autojs.core.image.Colors;
import org.autojs.autojs.core.image.ImageWrapper;
import org.autojs.autojs.core.image.capture.ScreenCaptureRequester;
import org.autojs.autojs.core.looper.Loopers;
import org.autojs.autojs.engine.ScriptEngineService;
import org.autojs.autojs.lang.ThreadCompat;
import org.autojs.autojs.pio.UncheckedIOException;
import org.autojs.autojs.rhino.AndroidClassLoader;
import org.autojs.autojs.rhino.TopLevelScope;
import org.autojs.autojs.rhino.continuation.Continuation;
import org.autojs.autojs.runtime.api.AbstractShell;
import org.autojs.autojs.runtime.api.AppUtils;
import org.autojs.autojs.runtime.api.Device;
import org.autojs.autojs.runtime.api.Dialogs;
import org.autojs.autojs.runtime.api.Engines;
import org.autojs.autojs.runtime.api.Events;
import org.autojs.autojs.runtime.api.Files;
import org.autojs.autojs.runtime.api.Floaty;
import org.autojs.autojs.runtime.api.Images;
import org.autojs.autojs.runtime.api.Media;
import org.autojs.autojs.runtime.api.MlKitOCR;
import org.autojs.autojs.runtime.api.Plugins;
import org.autojs.autojs.runtime.api.ProcessShell;
import org.autojs.autojs.runtime.api.ScreenMetrics;
import org.autojs.autojs.runtime.api.Sensors;
import org.autojs.autojs.runtime.api.Threads;
import org.autojs.autojs.runtime.api.Timers;
import org.autojs.autojs.runtime.api.UI;
import org.autojs.autojs.runtime.exception.ScriptEnvironmentException;
import org.autojs.autojs.runtime.exception.ScriptException;
import org.autojs.autojs.runtime.exception.ScriptInterruptedException;
import org.autojs.autojs.tool.Supplier;
import org.autojs.autojs.tool.UiHandler;
import org.autojs.autojs.util.ClipboardUtils;
import org.autojs.autojs.util.RootUtils;
import org.autojs.autojs.util.SdkVersionUtils;
import org.autojs.autojs6.R;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.ScriptStackElement;
import org.mozilla.javascript.Scriptable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Stardust on 2017/1/27.
 */
public class ScriptRuntime {

    private static final String TAG = "ScriptRuntime";

    public static class Builder {
        private UiHandler mUiHandler;
        private GlobalConsole mConsole;
        private AccessibilityBridge mAccessibilityBridge;
        private Supplier<AbstractShell> mShellSupplier;
        private ScreenCaptureRequester mScreenCaptureRequester;
        private AppUtils mAppUtils;
        private ScriptEngineService mEngineService;

        public Builder() {

        }

        public Builder setUiHandler(UiHandler uiHandler) {
            mUiHandler = uiHandler;
            return this;
        }

        public UiHandler getUiHandler() {
            return mUiHandler;
        }

        public Builder setConsole(GlobalConsole console) {
            mConsole = console;
            return this;
        }

        public GlobalConsole getConsole() {
            return mConsole;
        }

        public Builder setAccessibilityBridge(AccessibilityBridge accessibilityBridge) {
            mAccessibilityBridge = accessibilityBridge;
            return this;
        }

        public AccessibilityBridge getAccessibilityBridge() {
            return mAccessibilityBridge;
        }

        public Builder setShellSupplier(Supplier<AbstractShell> shellSupplier) {
            mShellSupplier = shellSupplier;
            return this;
        }

        public Supplier<AbstractShell> getShellSupplier() {
            return mShellSupplier;
        }

        public Builder setScreenCaptureRequester(ScreenCaptureRequester requester) {
            mScreenCaptureRequester = requester;
            return this;
        }

        public ScreenCaptureRequester getScreenCaptureRequester() {
            return mScreenCaptureRequester;
        }

        public Builder setAppUtils(AppUtils appUtils) {
            mAppUtils = appUtils;
            return this;
        }

        public AppUtils getAppUtils() {
            return mAppUtils;
        }

        public Builder setEngineService(ScriptEngineService service) {
            mEngineService = service;
            return this;
        }

        public ScriptEngineService getEngineService() {
            return mEngineService;
        }

        public ScriptRuntime build() {
            return new ScriptRuntime(this);
        }

    }

    @ScriptVariable
    public final AppUtils app;

    @ScriptVariable
    public final GlobalConsole console;

    @ScriptVariable
    public final SimpleActionAutomator automator;

    @ScriptVariable
    public final ActivityInfoProvider info;

    @ScriptVariable
    public final UI ui;

    @ScriptVariable
    public final Dialogs dialogs;

    @ScriptVariable
    public Events events;

    @ScriptVariable
    public final ScriptBridges bridges = new ScriptBridges();

    @ScriptVariable
    public Loopers loopers;

    @ScriptVariable
    public Timers timers;

    @ScriptVariable
    public Device device;

    @ScriptVariable
    public final AccessibilityBridge accessibilityBridge;

    @ScriptVariable
    public final Engines engines;

    @ScriptVariable
    public Threads threads;

    @ScriptVariable
    public final Floaty floaty;

    @ScriptVariable
    public UiHandler uiHandler;

    @ScriptVariable
    public final Colors colors = new Colors();

    @ScriptVariable
    public final Files files;

    @ScriptVariable
    public Sensors sensors;

    @ScriptVariable
    public final Media media;

    @ScriptVariable
    public final Plugins plugins;

    @ScriptVariable
    private final Images images;

    @ScriptVariable
    public final MlKitOCR mlKitOCR;

    private static WeakReference<Context> applicationContext;
    private final Map<String, Object> mProperties = new ConcurrentHashMap<>();
    private AbstractShell mRootShell;
    private Supplier<AbstractShell> mShellSupplier;
    private final ScreenMetrics mScreenMetrics = new ScreenMetrics();
    private Thread mThread;
    private TopLevelScope mTopLevelScope;

    protected ScriptRuntime(Builder builder) {
        files = new Files(this);
        dialogs = new Dialogs(this);

        app = builder.getAppUtils();
        console = builder.getConsole();
        mShellSupplier = builder.getShellSupplier();
        engines = new Engines(builder.getEngineService(), this);

        accessibilityBridge = builder.getAccessibilityBridge();

        info = accessibilityBridge.getInfoProvider();
        automator = new SimpleActionAutomator(accessibilityBridge, this);
        automator.setScreenMetrics(mScreenMetrics);

        uiHandler = builder.getUiHandler();

        floaty = new Floaty(uiHandler, this);
        Context context = uiHandler.getContext();

        ui = new UI(context, this);
        images = new Images(context, this, builder.getScreenCaptureRequester());
        device = new Device(context);
        media = new Media(context, this);
        plugins = new Plugins(context, this);

        mlKitOCR = new MlKitOCR();
    }

    public void init() {
        if (loopers != null) {
            throw new IllegalStateException("Already initialized");
        }
        threads = new Threads(this);
        timers = new Timers(this);
        loopers = new Loopers(this);
        events = new Events(uiHandler.getContext(), accessibilityBridge, this);
        mThread = Thread.currentThread();
        sensors = new Sensors(uiHandler.getContext(), this);
    }

    public TopLevelScope getTopLevelScope() {
        return mTopLevelScope;
    }

    public void setTopLevelScope(TopLevelScope topLevelScope) {
        if (mTopLevelScope != null) {
            throw new IllegalStateException("Top level has already exists");
        }
        mTopLevelScope = topLevelScope;
    }

    public static void setApplicationContext(Context context) {
        applicationContext = new WeakReference<>(context);
    }

    public static Context getApplicationContext() {
        if (applicationContext == null || applicationContext.get() == null) {
            throw new ScriptEnvironmentException("No application context");
        }
        return applicationContext.get();
    }

    public UiHandler getUiHandler() {
        return uiHandler;
    }

    public AccessibilityBridge getAccessibilityBridge() {
        return accessibilityBridge;
    }

    public void toast(final String text) {
        uiHandler.toast(text);
    }

    public void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new ScriptInterruptedException();
        }
    }

    public void setClip(final String text) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            ClipboardUtils.setClip(uiHandler.getContext(), text);
            return;
        }
        VolatileDispose<Object> dispose = new VolatileDispose<>();
        uiHandler.post(() -> {
            ClipboardUtils.setClip(uiHandler.getContext(), text);
            dispose.setAndNotify(text);
        });
        dispose.blockedGet();
    }

    public String getClip() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return ClipboardUtils.getClipOrEmpty(uiHandler.getContext()).toString();
        }
        final VolatileDispose<String> clip = new VolatileDispose<>();
        uiHandler.post(() -> clip.setAndNotify(ClipboardUtils.getClipOrEmpty(uiHandler.getContext()).toString()));
        return clip.blockedGetOrThrow(ScriptInterruptedException.class);
    }

    public AbstractShell getRootShell() {
        ensureRootShell();
        return mRootShell;
    }

    private void ensureRootShell() {
        if (mRootShell == null) {
            mRootShell = mShellSupplier.get();
            mRootShell.SetScreenMetrics(mScreenMetrics);
            mShellSupplier = null;
        }
    }

    public AbstractShell.Result shell(String cmd, int withRoot) {
        return ProcessShell.execCommand(cmd, withRoot != 0);
    }

    public UiSelector selector() {
        return new UiSelector(accessibilityBridge);
    }

    public boolean isStopped() {
        return mThread.isInterrupted();
    }

    public static void requiresApi(int requiresApi) throws ScriptException {
        int currentApi = Build.VERSION.SDK_INT;
        if (currentApi < requiresApi) {
            throw new ScriptException(str(
                    R.string.text_requires_android_os_version,
                    SdkVersionUtils.sdkIntToString(requiresApi),
                    requiresApi,
                    SdkVersionUtils.sdkIntToString(currentApi),
                    currentApi));
        }
    }

    public void loadJar(String path) {
        path = files.path(path);
        try {
            getClassLoader().loadJar(new File(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void loadDex(String path) {
        path = files.path(path);
        try {
            getClassLoader().loadDex(new File(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static AndroidClassLoader getClassLoader() {
        return (AndroidClassLoader) ContextFactory.getGlobal().getApplicationClassLoader();
    }

    public void exit() {
        mThread.interrupt();
        engines.myEngine().forceStop();
        threads.exit();
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new ScriptInterruptedException();
        }
    }

    public void exit(Throwable e) {
        engines.myEngine().uncaughtException(e);
        exit();
    }

    @Deprecated
    public void stop() {
        exit();
    }

    public void setScreenMetrics(int width, int height) {
        mScreenMetrics.setScreenMetrics(width, height);
    }

    public ScreenMetrics getScreenMetrics() {
        return mScreenMetrics;
    }

    public void ensureAccessibilityServiceEnabled() {
        accessibilityBridge.ensureServiceEnabled();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void onExit() {
        Log.d(TAG, "on exit");

        ignoresException(() -> {
            if (console.getConfigurator().isExitOnClose()) {
                console.hideDelayed();
            }
        });

        ignoresException(RootUtils::resetRuntimeOverriddenRootModeState);
        ignoresException(ImageWrapper::recycleAll);

        // 清除 interrupt 状态
        ignoresException(ThreadCompat::interrupted);

        // 悬浮窗需要第一时间关闭
        // 以免出现恶意脚本全屏悬浮窗屏蔽屏幕并且在 exit 中写死循环的问题
        ignoresException(floaty::closeAll);

        ignoresException(() -> events.emit("exit"), "exception on exit: %s");

        ignoresException(threads::shutDownAll);
        ignoresException(events::recycle);
        ignoresException(media::recycle);
        ignoresException(loopers::recycle);
        ignoresException(this::recycleShell);
        ignoresException(images::releaseScreenCapturer);
        ignoresException(mlKitOCR::release);
        ignoresException(sensors::unregisterAll);
        ignoresException(timers::recycle);
        ignoresException(ui::recycle);
    }

    private void ignoresException(Runnable r) {
        ignoresException(r, null);
    }

    private void ignoresException(Runnable r, String consoleMessage) {
        try {
            r.run();
        } catch (Throwable e) {
            if (consoleMessage != null) {
                console.error(String.format(consoleMessage, e));
            }
            e.printStackTrace();
        }
    }

    private void recycleShell() {
        if (mRootShell != null) {
            mRootShell.exit();
        }
        mRootShell = null;
        mShellSupplier = null;
    }

    public Object getImages() {
        return images;
    }

    public Object getProperty(String key) {
        return mProperties.get(key);
    }

    public Object putProperty(String key, Object value) {
        return mProperties.put(key, value);
    }

    public Object removeProperty(String key) {
        return mProperties.remove(key);
    }

    public Continuation createContinuation() {
        return Continuation.Companion.create(this, mTopLevelScope);
    }

    public Continuation createContinuation(Scriptable scope) {
        return Continuation.Companion.create(this, scope);
    }

    public SimpleActionAutomator getAutomator() {
        return automator;
    }

    public static String getStackTrace(Throwable e, boolean printJavaStackTrace) {
        String message = e.getMessage();
        StringBuilder scriptTrace = new StringBuilder(message == null ? "" : message + "\n");
        if (e instanceof RhinoException rhinoException) {
            scriptTrace.append(rhinoException.details()).append("\n");
            for (ScriptStackElement element : rhinoException.getScriptStack()) {
                element.renderV8Style(scriptTrace);
                scriptTrace.append("\n");
            }
            if (printJavaStackTrace) {
                scriptTrace.append("- - - - - - - - - - -\n");
            } else {
                return scriptTrace.toString();
            }
        }
        try {
            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);
            e.printStackTrace(writer);
            writer.close();
            BufferedReader bufferedReader = new BufferedReader(new StringReader(stringWriter.toString()));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                scriptTrace.append("\n").append(line);
            }
            return scriptTrace.toString();
        } catch (IOException e1) {
            e1.printStackTrace();
            return message;
        }
    }

}
