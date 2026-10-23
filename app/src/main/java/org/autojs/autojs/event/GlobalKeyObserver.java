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
import org.autojs.autojs.core.pref.keys.RunningKeys;

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
    private static AutoCloseable sPendingInit;
    private final EventDispatcher<OnVolumeDownListener> mVolumeDownEventDispatcher;
    private final InputEventObserver mInputEventObserver;
    private final ShellKeyObserver mShellKeyObserver;
    private boolean mShellObserving;
    private boolean mVolumeDownFromShell, mVolumeDownFromAccessibility;
    private boolean mVolumeUpFromShell, mVolumeUpFromAccessibility;

    private GlobalKeyObserver(Context applicationContext, long shellObserverDelayMillis) {
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
            listenVolumeControlSettings();
            updateShellObserver();
        }, shellObserverDelayMillis);
    }

    /**
     * The shell fallback (getevent through Shizuku or root) runs only while a volume control setting is on: turning both
     * off ends its process unless another listener (events.observeTouch) still uses it (docs/shizuku-oom/ROADMAP.md P1.5).
     * zh-CN: shell 兜底监听 (经 Shizuku 或 Root 的 getevent) 只在音量键控制设置开启时运行: 两项都关闭后结束其进程, 除非仍有其他监听者
     * (events.observeTouch) 在用 (docs/shizuku-oom/ROADMAP.md P1.5).
     */
    private synchronized void updateShellObserver() {
        if (VolumeControlSettings.isEnabled()) {
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
        // The singleton lives as long as the process, so the subscription is never closed.
        // zh-CN: 单例与进程同生命周期, 订阅无需关闭.
        VolumeControlSettings.listen(this::updateShellObserver);
    }

    /**
     * Creates the singleton when a volume control setting is on; otherwise turning one on later creates it, without
     * restarting the app (docs/shizuku-oom/ROADMAP.md P3.1).
     * zh-CN: 音量键控制设置开启时创建单例; 否则在之后打开某项设置时创建, 无需重启应用 (docs/shizuku-oom/ROADMAP.md P3.1).
     */
    public static synchronized void initIfNeeded(Context applicationContext) {
        if (sSingleton != null || sPendingInit != null) {
            return;
        }
        if (VolumeControlSettings.isEnabled()) {
            makeSureSingletonInitialized(applicationContext, SHELL_OBSERVER_START_DELAY_MILLIS);
            return;
        }
        // The cold start is long over when a setting is turned on, so the shell fallback starts without delay.
        // zh-CN: 设置被打开时冷启动早已结束, shell 兜底监听无需延迟.
        AutoCloseable pendingInit = VolumeControlSettings.whenEnabled(() -> {
            Log.d(LOG_TAG, "volume control turned on, creating the observer");
            makeSureSingletonInitialized(applicationContext, 0L);
        });
        if (sSingleton == null) {
            sPendingInit = pendingInit;
        }
    }

    public static void init(Context applicationContext) {
        makeSureSingletonInitialized(applicationContext, SHELL_OBSERVER_START_DELAY_MILLIS);
    }

    public static GlobalKeyObserver getSingleton(Context applicationContext) {
        return makeSureSingletonInitialized(applicationContext, SHELL_OBSERVER_START_DELAY_MILLIS);
    }

    private static synchronized GlobalKeyObserver makeSureSingletonInitialized(Context applicationContext, long shellObserverDelayMillis) {
        if (sSingleton == null) {
            sSingleton = new GlobalKeyObserver(applicationContext, shellObserverDelayMillis);
        }
        if (sPendingInit != null) {
            try {
                sPendingInit.close();
            } catch (Exception ignored) {
                /* Closing a settings subscription does not throw. */
            }
            sPendingInit = null;
        }
        return sSingleton;
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
}
