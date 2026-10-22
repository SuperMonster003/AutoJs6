package org.autojs.autojs.event;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import org.autojs.autojs.AutoJs;
import org.autojs.autojs.core.accessibility.AccessibilityService;
import org.autojs.autojs.core.accessibility.OnKeyListener;
import org.autojs.autojs.core.inputevent.InputEventObserver;
import org.autojs.autojs.core.inputevent.ShellKeyObserver;
import org.autojs.autojs.core.pref.keys.RecordKeys;
import org.autojs.autojs.core.pref.keys.RunningKeys;
import org.autojs.autojs.core.pref.store.SettingsStore;

import kotlin.Unit;

/**
 * Created by Stardust on Aug 14, 2017.
 */
public class GlobalKeyObserver implements OnKeyListener, ShellKeyObserver.KeyListener {

    public interface OnVolumeDownListener {
        void onVolumeDown();
    }

    private static final EventDispatcher.Event<OnVolumeDownListener> VOLUME_DOWN_EVENT = OnVolumeDownListener::onVolumeDown;
    private static final String LOG_TAG = "GlobalKeyObserver";
    private static final long SHELL_OBSERVER_START_DELAY_MILLIS = 1_200L;
    private static GlobalKeyObserver sSingleton;
    private final EventDispatcher<OnVolumeDownListener> mVolumeDownEventDispatcher;
    private final InputEventObserver mInputEventObserver;
    private final ShellKeyObserver mShellKeyObserver;
    private boolean mShellObserving;
    private boolean mVolumeDownFromShell, mVolumeDownFromAccessibility;
    private boolean mVolumeUpFromShell, mVolumeUpFromAccessibility;

    private GlobalKeyObserver(Context applicationContext) {
        mVolumeDownEventDispatcher = new EventDispatcher<>();
        AccessibilityService.Companion.getStickOnKeyObserver().addListener(this);
        mShellKeyObserver = new ShellKeyObserver();
        mShellKeyObserver.setKeyListener(this);
        mInputEventObserver = InputEventObserver.getGlobal(applicationContext);
        // Accessibility key events are observed immediately above. Delay only the shell fallback
        // until after the cold Rhino bootstrap so getevent/root probing cannot compete with it.
        // zh-CN: 上方已立即监听无障碍按键事件. 仅将 shell 兜底监听延迟到 Rhino 冷启动之后,
        // 避免 getevent/Root 探测与脚本引擎初始化争抢资源.
        new Handler(applicationContext.getMainLooper()).postDelayed(() -> {
            updateShellObserver();
            listenVolumeControlSettings();
        }, SHELL_OBSERVER_START_DELAY_MILLIS);
    }

    /**
     * The shell fallback (getevent through Shizuku or root) runs only while a volume control setting is on: turning both
     * off ends its process unless another listener (events.observeTouch) still uses it (docs/shizuku-oom/ROADMAP.md P1.5).
     * zh-CN: shell 兜底监听 (经 Shizuku 或 Root 的 getevent) 只在音量键控制设置开启时运行: 两项都关闭后结束其进程, 除非仍有其他监听者
     * (events.observeTouch) 在用 (docs/shizuku-oom/ROADMAP.md P1.5).
     */
    private synchronized void updateShellObserver() {
        if (isVolumeControlEnabled()) {
            if (!mShellObserving) {
                mShellObserving = true;
                mInputEventObserver.addListener(mShellKeyObserver);
            }
            mInputEventObserver.ensureObservedAsync();
        } else if (mShellObserving) {
            mShellObserving = false;
            mInputEventObserver.removeListener(mShellKeyObserver);
            if (!mInputEventObserver.hasListeners()) {
                mInputEventObserver.recycle();
            }
        }
    }

    private void listenVolumeControlSettings() {
        SettingsStore runningStore = RunningKeys.USE_VOLUME_CONTROL_RUNNING.getSettingsStore();
        SettingsStore recordStore = RecordKeys.USE_VOLUME_CONTROL_RECORD.getSettingsStore();
        // The singleton lives as long as the process, so the subscriptions are never closed.
        // zh-CN: 单例与进程同生命周期, 订阅无需关闭.
        runningStore.listen(this::onSettingChanged);
        if (recordStore != runningStore) {
            recordStore.listen(this::onSettingChanged);
        }
    }

    private Unit onSettingChanged(String key) {
        if (key == null || key.equals(RunningKeys.USE_VOLUME_CONTROL_RUNNING.getName()) || key.equals(RecordKeys.USE_VOLUME_CONTROL_RECORD.getName())) {
            updateShellObserver();
        }
        return Unit.INSTANCE;
    }

    public static void initIfNeeded(Context applicationContext) {
        if (isVolumeControlEnabled()) makeSureSingletonInitialized(applicationContext);
    }

    public static void init(Context applicationContext) {
        makeSureSingletonInitialized(applicationContext);
    }

    public static GlobalKeyObserver getSingleton(Context applicationContext) {
        makeSureSingletonInitialized(applicationContext);
        return sSingleton;
    }

    private static void makeSureSingletonInitialized(Context applicationContext) {
        if (sSingleton == null) {
            sSingleton = new GlobalKeyObserver(applicationContext);
        }
    }

    public void onVolumeUp() {
        Log.d(LOG_TAG, "onVolumeUp at " + System.currentTimeMillis());
        if (RunningKeys.USE_VOLUME_CONTROL_RUNNING.get()) {
            AutoJs.getInstance().getScriptEngineService().stopAllAndToast();
        }
    }

    public void onVolumeDown() {
        Log.d(LOG_TAG, "onVolumeDown at " + System.currentTimeMillis());
        mVolumeDownEventDispatcher.dispatchEvent(VOLUME_DOWN_EVENT);
    }

    public void addVolumeDownListener(OnVolumeDownListener listener) {
        mVolumeDownEventDispatcher.addListener(listener);
    }

    public boolean removeVolumeDownListener(OnVolumeDownListener listener) {
        return mVolumeDownEventDispatcher.removeListener(listener);
    }

    @Override
    public void onKeyEvent(int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (mVolumeDownFromShell) {
                mVolumeDownFromShell = false;
                return;
            }
            mVolumeDownFromAccessibility = true;
            onVolumeDown();
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (mVolumeUpFromShell) {
                mVolumeUpFromShell = false;
                return;
            }
            mVolumeUpFromAccessibility = true;
            onVolumeUp();
        }
    }

    @Override
    public void onKeyDown(String keyName) {
        /* Empty body. */
    }

    @Override
    public void onKeyUp(String keyName) {
        if ("KEY_VOLUMEUP".equals(keyName)) {
            if (mVolumeUpFromAccessibility) {
                mVolumeUpFromAccessibility = false;
                return;
            }
            mVolumeUpFromShell = true;
            onVolumeUp();
        } else if ("KEY_VOLUMEDOWN".equals(keyName)) {
            if (mVolumeDownFromAccessibility) {
                mVolumeDownFromAccessibility = false;
                return;
            }
            mVolumeDownFromShell = true;
            onVolumeDown();
        }
    }

    private static boolean isVolumeControlEnabled() {
        return RunningKeys.USE_VOLUME_CONTROL_RUNNING.get() || RecordKeys.USE_VOLUME_CONTROL_RECORD.get();
    }
}
